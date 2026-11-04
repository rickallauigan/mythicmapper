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
        send(0,x,y);if(dx!=0f||dy!=0f){SystemClock.sleep(40);send(2,x+dx,y+dy)};SystemClock.sleep(50);send(1,x+dx,y+dy);settle()
    }
    override fun onStart(){
        uiAutomation.serviceInfo=uiAutomation.serviceInfo.apply{flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS}
        val report=try{if(args.getString("suite")=="home_physical"){check(xtr.keymapper.BuildConfig.DEBUG&&Build.MODEL=="SM-G970F");physicalHome()}else{check(BuildConfigEmulator());if(args.getString("suite")=="home")home()else bubble()}}catch(t:Throwable){File(targetContext.filesDir,"ux-failure.txt").writeText(nodes(uiAutomation.rootInActiveWindow).joinToString("\n"){"${it.viewIdResourceName} text=${it.text} desc=${it.contentDescription} editable=${it.isEditable} visible=${it.isVisibleToUser} actions=${it.actionList}"});JSONObject().put("status","FAIL").put("error",t.stackTraceToString().take(6000))}
        finish(if(report.optString("status")=="PASS")Activity.RESULT_OK else Activity.RESULT_CANCELED,Bundle().apply{putString("ux",report.toString())})
    }
    private fun BuildConfigEmulator()=xtr.keymapper.BuildConfig.DEBUG&&Build.HARDWARE in setOf("ranchu","goldfish")
    private fun nodes(root:android.view.accessibility.AccessibilityNodeInfo?):List<android.view.accessibility.AccessibilityNodeInfo> {
        if(root==null)return emptyList()
        return listOf(root)+(0 until root.childCount).flatMap{nodes(root.getChild(it))}
    }
    private fun appNodes():List<android.view.accessibility.AccessibilityNodeInfo> {
        if(Build.VERSION.SDK_INT>=34)uiAutomation.clearCache()
        return (nodes(uiAutomation.rootInActiveWindow)+uiAutomation.windows.flatMap{nodes(it.root)}).filter{it.packageName?.toString()==targetContext.packageName}
    }
    private fun settle(){waitForIdleSync();uiAutomation.waitForIdle(150,5000)}
    private fun byId(id:String):android.view.accessibility.AccessibilityNodeInfo? {
        return appNodes().firstOrNull{it.viewIdResourceName==id}
    }
    private fun homeRoot():android.view.accessibility.AccessibilityNodeInfo {
        repeat(30){byId("mythic-home")?.let{return it};SystemClock.sleep(100)}
        error("Home scroll surface unavailable")
    }
    private fun text(value:String)=appNodes().firstOrNull{it.text?.toString()==value||it.contentDescription?.toString()==value}
    private fun click(value:String){
        val deadline=SystemClock.uptimeMillis()+5000
        var node=text(value)
        while(node==null&&SystemClock.uptimeMillis()<deadline){SystemClock.sleep(100);node=text(value)}
        var target=requireNotNull(node){"Missing UI control: $value"}
        while(!target.isClickable&&target.parent!=null)target=target.parent
        check(target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){"Cannot click $value"}
        settle()
    }
    /** Navigation idle does not imply Compose accessibility is published after rotation. */
    private fun awaitVisibleText(value:String) {
        val end=SystemClock.uptimeMillis()+10000
        while(SystemClock.uptimeMillis()<end){
            if(text(value)?.isVisibleToUser==true)return
            SystemClock.sleep(50)
        }
        error("Destination label not visible after navigation: $value")
    }
    private fun verifyVirtualNavigation() {
        val monitor=addMonitor(xtr.keymapper.virtual.VirtualG8Activity::class.java.name,null,false)
        try {
            click("Virtual G8 · SIMULATED")
            val destination=waitForMonitorWithTimeout(monitor,10000)?:error("Virtual G8 Activity not launched")
            awaitVisibleText("SIMULATED — DEV ONLY")
            check(!destination.isFinishing&&!destination.isDestroyed)
            shot("home-virtual-g8")
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            val end=SystemClock.uptimeMillis()+10000
            while(!destination.isDestroyed&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50)
            check(destination.isDestroyed){"Virtual G8 did not finish on Back"}
            awaitVisibleText("Virtual G8 · SIMULATED")
            check(!EditorSessionGuard.active)
        }finally{removeMonitor(monitor)}
    }
    private fun scrollHome(forward:Boolean) {
        val r=android.graphics.Rect();homeRoot().getBoundsInScreen(r)
        val x=r.centerX().toFloat();val startY=r.top+r.height()*(if(forward).75f else .35f)
        val endY=r.top+r.height()*(if(forward).35f else .75f);val start=SystemClock.uptimeMillis()
        fun event(action:Int,y:Float){val e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,x,y,0);e.source=android.view.InputDevice.SOURCE_TOUCHSCREEN;check(uiAutomation.injectInputEvent(e,true));e.recycle()}
        event(0,startY);for(i in 1..8){SystemClock.sleep(20);event(2,startY+(endY-startY)*i/8)};event(1,endY);settle()
    }
    private fun scrollFavorites(forward:Boolean) {
        val r=android.graphics.Rect();requireNotNull(byId("favorite-strip")).getBoundsInScreen(r)
        val x=r.left+r.width()*(if(forward).8f else .2f);val end=r.left+r.width()*(if(forward).2f else .8f);val y=r.centerY().toFloat();val start=SystemClock.uptimeMillis()
        fun event(action:Int,ex:Float){val e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,ex,y,0);e.source=android.view.InputDevice.SOURCE_TOUCHSCREEN;check(uiAutomation.injectInputEvent(e,true));e.recycle()}
        event(0,x);for(i in 1..8){SystemClock.sleep(20);event(2,x+(end-x)*i/8)};event(1,end);settle()
    }
    private fun scrollTo(id:String){
        repeat(40){
            val node=byId(id)
            if(node!=null&&node.isVisibleToUser){node.performAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id);settle();return}
            scrollHome(true)
        };error("Not found: $id")
    }
    private fun setText(value:String){
        val editable=appNodes().firstOrNull{it.isEditable}?:text("Search profiles and starters")?:error("Text field missing")
        editable.performAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        editable.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS)
        editable.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        settle()
        val fresh=appNodes().firstOrNull{it.isEditable||it.actionList.any{a->a.id==android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT}}?:error("Focused field disappeared")
        check(fresh.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)}))
        settle()
    }
    private fun home():JSONObject {
        val isolated=object:android.content.ContextWrapper(targetContext){override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("ux_home_$name",mode)}
        val store=xtr.keymapper.mythic.HeroLibraryStore(isolated)
        // Only the explicit debug UI namespace is used; never regular profile preferences.
        store.read().profiles.forEach{store.delete(it.id)}
        val alpha=store.clone("UI TEST Alpha",xtr.keymapper.mythic.HeroTrainingProfiles.draft(xtr.keymapper.controller.HeroGameplayConfig.Hero.KIMMY),null)
        val beta=store.clone("UI TEST Beta",xtr.keymapper.mythic.HeroTrainingProfiles.draft(xtr.keymapper.controller.HeroGameplayConfig.Hero.KIMMY),null)
        store.read().let{store.update(it,it.assign(0,alpha.id).assign(1,beta.id))}
        val a=startActivitySync(Intent(targetContext,xtr.keymapper.virtual.VirtualHomeActivity::class.java).putExtra("ux_test",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try{
            main{a.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT};waitForIdleSync();SystemClock.sleep(1200)
            check(text("Physical G8 not detected")!=null);check(text("Virtual G8 · SIMULATED")!=null);shot("home-overview")
            scrollTo("favorite-strip");shot("home-favorites")
            repeat(4){if(byId("favorite-4")?.isVisibleToUser!=true){scrollFavorites(true)}}
            check(byId("favorite-4")?.isVisibleToUser==true)
            repeat(4){scrollFavorites(false)}
            click("Manage favorite 1");click("Assign");click("UI TEST Beta")
            check(store.read().favorites[0]==alpha.id);click("Replace");check(store.read().favorites[0]==beta.id)
            click("Manage favorite 1");click("Clear");check(store.read().favorites[0]==null)
            scrollTo("profile-search");setText("UI TEST Alpha");scrollTo("profile-${alpha.id}");shot("home-library")
            click("Rename");setText("UI TEST Renamed");click("Save");check(store.read().profiles.any{it.id==alpha.id&&it.name=="UI TEST Renamed"})
            // Search is intentionally persisted; change it to the renamed value.
            scrollToSearch();setText("UI TEST Renamed");scrollTo("profile-${alpha.id}")
            click("Duplicate");setText("UI TEST Copy");click("Save")
            val copy=store.read().profiles.single{it.name=="UI TEST Copy"};check(copy.id!=alpha.id)
            scrollToSearch();setText("UI TEST Copy");scrollTo("profile-${copy.id}")
            click("Delete");check(store.read().profiles.any{it.id==copy.id});click("Cancel");check(store.read().profiles.any{it.id==copy.id})
            click("Delete");click("Delete");check(store.read().profiles.none{it.id==copy.id})
            scrollToSearch();setText("UI TEST Renamed");scrollTo("profile-${alpha.id}")
            val mappings=xtr.keymapper.keymap.KeymapProfiles(targetContext).sharedPref
            check(!mappings.contains(alpha.id))
            mappings.edit().putStringSet(alpha.id,xtr.keymapper.mythic.HeroTrainingProfiles.draft(xtr.keymapper.controller.HeroGameplayConfig.Hero.KIMMY)).commit()
            val monitor=addMonitor(xtr.keymapper.editor.EditorActivity::class.java.name,null,false)
            try{click("Visual editor");val editor=waitForMonitorWithTimeout(monitor,10000)?:error("Editor did not open");check(editor.intent.getStringExtra(xtr.keymapper.editor.EditorActivity.PROFILE_NAME)==alpha.id);main{editor.finish()};settle()}
            finally{removeMonitor(monitor);mappings.edit().remove(alpha.id).commit()}
            // Return to the overview, then rotate the same Activity (no global settings).
            repeat(15){byId("mythic-home")?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);waitForIdleSync()}
            click("Controller Tester");check(text("GameSir G8 Galileo Tester")!=null);sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle()
            verifyVirtualNavigation()
            check(!EditorSessionGuard.active)
            main{a.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE};waitForIdleSync();SystemClock.sleep(1000);shot("home-landscape")
            verifyVirtualNavigation()
            return JSONObject().put("status","PASS").put("checks","physical/simulated labels; favorites replace/clear; search; rename; duplicate UUID; delete confirm/cancel; all five slots; editor/Tester/Virtual navigation; ownership cleanup; portrait/landscape").put("source","actual AVD accessibility; isolated disposable preferences").put("mapper_submissions",0)
        }finally{main{a.finish()};waitForIdleSync();store.read().profiles.forEach{store.delete(it.id)}}
    }
    private fun scrollToSearch(){
        repeat(40){val node=byId("profile-search");if(node!=null&&node.isVisibleToUser){node.performAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id);settle();return};scrollHome(false)}
        error("Search not reachable")
    }
    /** Actual personal library is read, searched and opened; never renamed/assigned/saved. */
    private fun physicalHome():JSONObject {
        val store=xtr.keymapper.mythic.HeroLibraryStore(targetContext);val before=store.read()
        val mappings=xtr.keymapper.keymap.KeymapProfiles(targetContext).sharedPref
        val protected=before.profiles.associate{it.id to mappings.getStringSet(it.id,null)?.toSet()}
        check(targetContext.getSystemService(android.app.ActivityManager::class.java).getRunningServices(100).none{it.service.className=="xtr.keymapper.TouchPointer"&&it.started})
        val connected=xtr.keymapper.devices.InputDeviceDiscovery.connected().any(xtr.keymapper.mythic.MythicScope::supported)
        val a=startActivitySync(Intent(targetContext,xtr.keymapper.activity.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            settle();check(text(if(connected)"GameSir G8 connected"else"Physical G8 not detected")!=null)
            check(text("Virtual G8 · SIMULATED")!=null);shot("physical-home-overview")
            scrollTo("favorite-strip");shot("physical-home-favorites")
            repeat(4){if(byId("favorite-4")?.isVisibleToUser!=true)scrollFavorites(true)}
            check(byId("favorite-4")?.isVisibleToUser==true);check(before.favorites.size==5)
            val profile=before.profiles.firstOrNull()?:error("No personal profile to navigate safely")
            scrollTo("profile-search");setText(profile.name)
            // Dismiss only the IME; its window can cover the matching card on the S10e.
            if(uiAutomation.windows.any{it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD}){sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle()}
            scrollTo("profile-${profile.id}");shot("physical-home-library")
            for((label,capture)in listOf("Visual editor" to false,"Calibrate" to true)) {
                val monitor=addMonitor(xtr.keymapper.editor.EditorActivity::class.java.name,null,false)
                try {
                    click(label);val editor=waitForMonitorWithTimeout(monitor,10000)?:error("Editor route missing")
                    waitForIdleSync();check(!editor.isFinishing)
                    check(editor.intent.getStringExtra(xtr.keymapper.editor.EditorActivity.PROFILE_NAME)==profile.id)
                    check(editor.intent.getBooleanExtra(xtr.keymapper.editor.EditorActivity.CAPTURE,false)==capture)
                    main{editor.finish()};val end=SystemClock.uptimeMillis()+6000
                    while((!editor.isDestroyed||EditorSessionGuard.active)&&SystemClock.uptimeMillis()<end)SystemClock.sleep(25)
                    check(editor.isDestroyed&&!EditorSessionGuard.active);settle()
                }finally{removeMonitor(monitor)}
            }
            repeat(12){homeRoot().performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);waitForIdleSync()}
            main{a.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE};waitForIdleSync();SystemClock.sleep(900);shot("physical-home-landscape")
        }finally{main{a.finish()};waitForIdleSync()}
        val large=startActivitySync(Intent(targetContext,PhysicalHomeLargeTextActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            main{large.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT};waitForIdleSync();SystemClock.sleep(900)
            check(large.resources.configuration.fontScale>=1.5f);check(text("Create mapping")!=null);check(text("Controller Tester")!=null)
            shot("physical-home-large-text")
        }finally{main{large.finish()};waitForIdleSync()}
        check(store.read()==before&&protected.all{(id,lines)->mappings.getStringSet(id,null)?.toSet()==lines})
        return JSONObject().put("status","PASS").put("physical_g8_observed",connected).put("five_slots",true).put("search",true)
            .put("personal_editor_and_calibration_navigation",true).put("portrait_landscape",true).put("large_text_scale",1.5)
            .put("simulated_action_labeled",true).put("personal_data_unchanged",true)
    }
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
