// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.ux

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.*
import android.view.*
import android.widget.*
import org.json.JSONObject
import xtr.keymapper.editor.EditorSessionGuard
import xtr.keymapper.mythic.FavoriteOverlay
import java.io.File

/** Owned emulator UI interactions, never mapper input injection. */
class UxInstrumentation:Instrumentation() {
    private lateinit var args:Bundle
    override fun onCreate(arguments:Bundle?){args=arguments?:Bundle();start()}
    private fun main(action:()->Unit){var failure:Throwable?=null;runOnMainSync{try{action()}catch(t:Throwable){failure=t}};failure?.let{throw it}}
    private fun <T> field(owner:Any,name:String):T {val f=owner.javaClass.getDeclaredField(name);f.isAccessible=true;@Suppress("UNCHECKED_CAST") return f.get(owner)as T}
    private fun shot(name:String){val dir=File(targetContext.filesDir,"ux");dir.mkdirs();File(dir,"$name.png").outputStream().use{check(uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}}
    private fun gesture(view:View,dx:Float=0f,dy:Float=0f) {
        val loc=IntArray(2);main{view.getLocationOnScreen(loc)}
        val x=loc[0]+view.width/2f;val y=loc[1]+view.height/2f;val start=SystemClock.uptimeMillis()
        fun send(action:Int,ex:Float,ey:Float){val e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,ex,ey,0);e.source=android.view.InputDevice.SOURCE_TOUCHSCREEN;check(uiAutomation.injectInputEvent(e,true));e.recycle()}
        send(0,x,y);if(dx!=0f||dy!=0f){SystemClock.sleep(40);send(2,x+dx,y+dy)};SystemClock.sleep(50);send(1,x+dx,y+dy);waitForIdleSync();SystemClock.sleep(150)
    }
    override fun onStart(){
        val report=try{check(BuildConfigEmulator());bubble()}catch(t:Throwable){JSONObject().put("status","FAIL").put("error",t.stackTraceToString().take(6000))}
        finish(if(report.optString("status")=="PASS")Activity.RESULT_OK else Activity.RESULT_CANCELED,Bundle().apply{putString("ux",report.toString())})
    }
    private fun BuildConfigEmulator()=xtr.keymapper.BuildConfig.DEBUG&&Build.HARDWARE in setOf("ranchu","goldfish")
    private fun bubble():JSONObject {
        val a=startActivitySync(Intent(targetContext,BubblePreviewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as BubblePreviewActivity
        try{
            waitForIdleSync();SystemClock.sleep(500)
            val o=a.overlay;val header=field<Button>(o,"header");val root=field<LinearLayout>(o,"root");val panel=field<ScrollView>(o,"panelScroll")
            check(o.isAttached()&&root.isAttachedToWindow);check(header.width==(48*a.resources.displayMetrics.density+.5f).toInt());shot("bubble-collapsed")
            gesture(header);check(panel.visibility==View.VISIBLE);shot("bubble-expanded")
            gesture(header);check(panel.visibility==View.GONE)
            gesture(header,120f,100f);check(panel.visibility==View.GONE&&a.selections==0)
            val before=field<WindowManager.LayoutParams>(o,"params").let{it.x to it.y}
            main{o.close();o.show();o.show()};waitForIdleSync();check(root.childCount==2&&o.isAttached())
            check(field<WindowManager.LayoutParams>(o,"params").let{it.x to it.y}==before)
            gesture(header);val list=field<LinearLayout>(o,"panel");val favorite=list.getChildAt(1)
            gesture(favorite);check(a.selections==1&&panel.visibility==View.GONE)
            // Exercise the actual overlay editor tap and return, with one disposable UUID mirror.
            val profile=field<xtr.keymapper.mythic.HeroProfile>(a,"profile")
            val mappings=xtr.keymapper.keymap.KeymapProfiles(targetContext).sharedPref
            check(!mappings.contains(profile.id))
            mappings.edit().putStringSet(profile.id,xtr.keymapper.mythic.HeroTrainingProfiles.draft(xtr.keymapper.controller.HeroGameplayConfig.Hero.KIMMY)).commit()
            val editorMonitor=addMonitor(xtr.keymapper.editor.EditorActivity::class.java.name,null,false)
            try {
                gesture(header);main{panel.fullScroll(View.FOCUS_DOWN)};waitForIdleSync();SystemClock.sleep(300)
                gesture(field<LinearLayout>(o,"panel").getChildAt(6))
                val editor=waitForMonitorWithTimeout(editorMonitor,10000)?:error("Overlay editor did not open")
                check(panel.visibility==View.GONE)
                check((root.background as android.graphics.drawable.ColorDrawable).color==android.graphics.Color.TRANSPARENT)
                main{editor.finish()};waitForIdleSync()
                val deadline=SystemClock.uptimeMillis()+5000
                while((!editor.isDestroyed||EditorSessionGuard.active)&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(25)
                check(editor.isDestroyed&&!EditorSessionGuard.active){"Editor lifecycle/lease did not finish"}
            }finally{removeMonitor(editorMonitor);mappings.edit().remove(profile.id).commit()}
            val owner=Any();try{main{EditorSessionGuard.acquire(owner)};waitForIdleSync();check(root.visibility==View.GONE)}finally{main{EditorSessionGuard.release(owner)}}
            waitForIdleSync();check(root.visibility==View.VISIBLE)
            main{a.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT};SystemClock.sleep(1100)
            check(o.isAttached());shot("bubble-portrait")
            main{o.close()};waitForIdleSync();check(!root.isAttachedToWindow)
            return JSONObject().put("status","PASS").put("checks","48dp target; real tap/drag; expand/collapse; no drag select; selection; recreation; position; overlay editor return/backdrop; ownership hide; rotation; cleanup").put("mapper_submissions",0).put("source","owned AVD UI; isolated preview data; no root")
        }finally{main{a.finish()};waitForIdleSync()}
    }
}
