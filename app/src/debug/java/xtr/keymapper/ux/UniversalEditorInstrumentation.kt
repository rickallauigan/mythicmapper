// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.ux

import android.app.*
import android.content.Intent
import android.os.*
import android.view.accessibility.AccessibilityNodeInfo as Node
import org.json.JSONObject
import java.io.File
import xtr.keymapper.universal.*
import xtr.keymapper.virtual.VirtualG8Session

/** Real accessibility operations against the production editor, isolated test preference namespace. */
class UniversalEditorInstrumentation:Instrumentation() {
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    private fun nodes(n:Node?):List<Node> = if(n==null) emptyList() else listOf(n)+(0 until n.childCount).flatMap{nodes(n.getChild(it))}
    private fun all():List<Node>{if(Build.VERSION.SDK_INT>=34)uiAutomation.clearCache();return uiAutomation.windows.flatMap{nodes(it.root)}.filter{it.packageName==targetContext.packageName}}
    private fun settle(){waitForIdleSync();SystemClock.sleep(100)}
    private fun find(value:String):Node {
        for(forward in listOf(false,true))for(step in 0 until 45){
            val found=all().firstOrNull{it.viewIdResourceName==value||it.text?.toString()==value}
            if(found!=null){if(found.isVisibleToUser)return found;found.performAction(Node.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id);settle();all().firstOrNull{(it.viewIdResourceName==value||it.text?.toString()==value)&&it.isVisibleToUser}?.let{return it}}
            val root=all().firstOrNull{it.viewIdResourceName=="universal-editor"}
            val direction=if(forward)Node.ACTION_SCROLL_FORWARD else Node.ACTION_SCROLL_BACKWARD
            if(root?.actionList?.none{it.id==direction}!=false)break
            root.performAction(direction);settle()
        };error("Unreachable control $value")
    }
    private fun click(value:String){var n=find(value);while(!n.isClickable&&n.parent!=null)n=n.parent;check(n.performAction(Node.ACTION_CLICK)){"Click failed $value"};settle()}
    private fun text(id:String,value:String){val n=find(id);val editable=nodes(n).firstOrNull{it.isEditable}?:n;check(editable.performAction(Node.ACTION_SET_TEXT,Bundle().apply{putCharSequence(Node.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)})){"Edit failed $id"};settle()}
    private fun main(f:()->Unit){var error:Throwable?=null;runOnMainSync{try{f()}catch(t:Throwable){error=t}};error?.let{throw it}}
    override fun onStart(){
        check(Build.HARDWARE in setOf("ranchu","goldfish"))
        uiAutomation.serviceInfo=uiAutomation.serviceInfo.apply{flags=flags or 0x10 or 0x40 or 0x100}
        val dir=File(targetContext.filesDir,"universal-editor").apply{mkdirs()}
        var activity:UniversalEditorTestActivity?=null
        var session:VirtualG8Session?=null
        val context=object:android.content.ContextWrapper(targetContext){override fun getSharedPreferences(n:String,m:Int)=super.getSharedPreferences("ux_home_$n",m)}
        val store=xtr.keymapper.mythic.HeroLibraryStore(context)
        val before=store.read()
        val result=try{
            activity=startActivitySync(Intent(targetContext,UniversalEditorTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as UniversalEditorTestActivity
            settle();text("create-name","UI TEST Custom Hero");click("create-blank")
            for((code,x,y)in listOf(Triple("BTN_GAMEPAD",300,800),Triple("BTN_EAST",600,800),Triple("BTN_TR",1000,800),Triple("BTN_TR2",1300,600),Triple("BTN_TL",1700,400),Triple("BTN_TL2",1900,800))){
                click("palette-$code");text("binding-name",code);if(code in setOf("BTN_GAMEPAD","BTN_EAST"))click("Tap")
                text("binding-x",x.toString());text("binding-y",y.toString());if(code=="BTN_TL2")click("binding-aim");click("apply-binding")
            }
            click("confirm-hud");click("enable-mapping");click("save-universal")
            val saved=store.read().profiles.single{it.name=="UI TEST Custom Hero"&&it.id !in before.profiles.map{p->p.id}}
            val document=UniversalStorage.read(context,saved.id);document.ready();check(document.actions.size==6&&document.actions.last().aim)
            val free=before.favorites.indexOfFirst{it==null};check(free>=0){"Isolated acceptance needs a disposable free slot"}
            click("Assign favorite");click("Favorite ${free+1}: Empty");check(store.read().favorites[free]==saved.id)
            check(store.read().favorites.filterIndexed{i,_->i!=free}==before.favorites.filterIndexed{i,_->i!=free})
            File(dir,"saved-profile.json").writeText(UniversalCodec.encode(document))
            File(dir,"editor.png").outputStream().use{check(uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
            main{activity!!.finish()};settle()
            activity=startActivitySync(Intent(targetContext,UniversalEditorTestActivity::class.java).putExtra(xtr.keymapper.editor.EditorActivity.PROFILE_NAME,saved.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as UniversalEditorTestActivity
            settle();check(UniversalStorage.read(context,saved.id)==document);find("mapping-BTN_TL2")
            click("Rename profile");text("profile-name","UI TEST Unsaved rename");click("Apply name");find("Profile: UI TEST Unsaved rename · Unsaved")
            click("Undo");find("Profile: UI TEST Custom Hero");click("Redo");find("Profile: UI TEST Unsaved rename · Unsaved")
            val monitor=addMonitor(UniversalEditorTestActivity::class.java.name,null,false)
            try{main{activity!!.recreate()};activity=waitForMonitorWithTimeout(monitor,10000) as? UniversalEditorTestActivity?:error("Editor recreation failed");settle();find("Profile: UI TEST Unsaved rename · Unsaved")}finally{removeMonitor(monitor)}
            click("Revert");click("Confirm");find("Profile: UI TEST Custom Hero");check(UniversalStorage.read(context,saved.id)==document)
            find("mapping-canvas");File(dir,"canvas.png").outputStream().use{check(uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
            main{session=VirtualG8Session();session!!.loadUniversal(document);session!!.axis("ABS_RX",128);session!!.axis("ABS_RY",128)}
            for(code in listOf("BTN_GAMEPAD","BTN_EAST","BTN_TR","BTN_TR2","BTN_TL","BTN_TL2")){
                main{session!!.key(code,true)};SystemClock.sleep(70);main{session!!.key(code,false)}
            }
            main{session!!.close()};val trace=session!!.report();check(trace.getInt("final_pointers")==0&&trace.getInt("total_touches")>=12)
            File(dir,"replay.json").writeText(trace.toString(2))
            JSONObject().put("status","PASS").put("favorite_assigned_through_ui",true).put("created_through_ui",true).put("bindings",6).put("right_stick_aim",true).put("reopened",true).put("undo_redo",true).put("draft_recreation",true).put("final_pointers",0).put("platform_touch_submissions",0)
        }catch(t:Throwable){File(dir,"failure-nodes.txt").writeText(all().joinToString("\n"){"${it.viewIdResourceName}: ${it.text} visible=${it.isVisibleToUser}"});JSONObject().put("status","FAIL").put("error",t.stackTraceToString())}
        finally{main{session?.close();activity?.finish()};settle();store.read().profiles.filter{p->p.id !in before.profiles.map{it.id}}.forEach{store.delete(it.id)}}
        check(store.read()==before){"Isolated library/favorites changed"}
        File(dir,"result.json").writeText(result.toString(2));finish(if(result.getString("status")=="PASS")Activity.RESULT_OK else Activity.RESULT_CANCELED,Bundle().apply{putString("universal_editor",result.toString())})
    }
}
