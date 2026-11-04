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
    private fun settle(){waitForIdleSync();uiAutomation.waitForIdle(150,5000)}
    private fun scroll(root:Node,forward:Boolean){
        val r=android.graphics.Rect();root.getBoundsInScreen(r)
        // The padded left margin avoids consuming drags in the mapping canvas/fields.
        val x=r.left+8*targetContext.resources.displayMetrics.density
        val y=r.top+r.height()*(if(forward).8f else .3f);val end=r.top+r.height()*(if(forward).3f else .8f)
        val start=SystemClock.uptimeMillis()
        fun event(action:Int,p:Float){val e=android.view.MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,x,p,0);e.source=android.view.InputDevice.SOURCE_TOUCHSCREEN;check(uiAutomation.injectInputEvent(e,true));e.recycle()}
        event(0,y);repeat(8){SystemClock.sleep(20);event(2,y+(end-y)*(it+1)/8)};event(1,end);settle()
    }
    private fun find(value:String):Node {
        // Navigation/Compose idle precedes publication of a new dialog accessibility window.
        val deadline=SystemClock.uptimeMillis()+10000
        while(SystemClock.uptimeMillis()<deadline){
            val current=all();current.firstOrNull{(it.viewIdResourceName==value||it.text?.toString()==value)&&it.isVisibleToUser}?.let{return it}
            if(current.any{it.viewIdResourceName in setOf("universal-editor","mythic-home")})break
            SystemClock.sleep(50)
        }
        for(forward in listOf(false,true))for(step in 0 until 45){
            all().firstOrNull{(it.viewIdResourceName==value||it.text?.toString()==value)&&it.isVisibleToUser}?.let{return it}
            val root=all().firstOrNull{it.viewIdResourceName in setOf("universal-editor","mythic-home")}?:break
            val direction=if(forward)Node.ACTION_SCROLL_FORWARD else Node.ACTION_SCROLL_BACKWARD
            if(root.actionList.none{it.id==direction})break
            scroll(root,forward)
        }
        repeat(20){all().firstOrNull{(it.viewIdResourceName==value||it.text?.toString()==value)&&it.isVisibleToUser}?.let{return it};SystemClock.sleep(50)}
        error("Unreachable control $value")
    }
    private fun click(value:String){
        val deadline=SystemClock.uptimeMillis()+10000
        do{var n=find(value);while(!n.isClickable&&n.parent!=null)n=n.parent
            if(n.isEnabled&&n.performAction(Node.ACTION_CLICK)){settle();return};SystemClock.sleep(50)
        }while(SystemClock.uptimeMillis()<deadline)
        error("Click failed $value")
    }
    private fun text(id:String,value:String){
        val deadline=SystemClock.uptimeMillis()+10000
        do{
            val fresh=find(id);val editable=nodes(fresh).firstOrNull{it.isEditable}?:fresh
            if(editable.performAction(Node.ACTION_SET_TEXT,Bundle().apply{putCharSequence(Node.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)})){
                settle();val updated=find(id);val field=nodes(updated).firstOrNull{it.isEditable}?:updated
                if(field.text?.toString()==value)return
            }
            SystemClock.sleep(50)
        }while(SystemClock.uptimeMillis()<deadline)
        error("Edit failed or value not retained: $id")
    }
    private fun dragTarget(document:UniversalMapping){
        click("mapping-BTN_TL2")
        find("mapping-canvas").performAction(Node.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id);settle()
        val bounds=android.graphics.Rect();var previous:android.graphics.Rect?=null;var stable=0
        val deadline=SystemClock.uptimeMillis()+5000
        while(stable<3&&SystemClock.uptimeMillis()<deadline){find("mapping-canvas").getBoundsInScreen(bounds);stable=if(bounds==previous)stable+1 else 0;previous=android.graphics.Rect(bounds);SystemClock.sleep(50)}
        check(stable>=3&&bounds.width()>0&&bounds.height()>0){"Canvas geometry never stabilized"}
        val plane=UniversalViewport(bounds.width().toFloat(),bounds.height().toFloat(),document.geometry)
        val target=document.actions.last();val p=plane.screen(target.x!!,target.y!!)
        val start=SystemClock.uptimeMillis()
        fun event(action:Int,delta:Float){val e=android.view.MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,bounds.left+p.x-delta,bounds.top+p.y-delta,0);e.source=android.view.InputDevice.SOURCE_TOUCHSCREEN;check(uiAutomation.injectInputEvent(e,true));e.recycle()}
        event(0,0f);repeat(8){SystemClock.sleep(25);event(2,(it+1)*10f)};event(1,80f);settle()
        fun number(id:String):Float{val n=find(id);return (nodes(n).firstOrNull{it.isEditable}?:n).text.toString().toFloat()}
        check(kotlin.math.abs(number("binding-x")-(target.x-80f/plane.scale))<1f){"Drag X coordinate mismatch"}
        check(kotlin.math.abs(number("binding-y")-(target.y-80f/plane.scale))<1f){"Drag Y coordinate mismatch"}
        find("Profile: UI TEST Custom Hero · Unsaved");click("Undo");find("Profile: UI TEST Custom Hero")
    }
    private fun main(f:()->Unit){var error:Throwable?=null;runOnMainSync{try{f()}catch(t:Throwable){error=t}};error?.let{throw it}}
    override fun onStart(){
        check(Build.HARDWARE in setOf("ranchu","goldfish"))
        uiAutomation.serviceInfo=uiAutomation.serviceInfo.apply{flags=flags or 0x10 or 0x40 or 0x100}
        val dir=File(targetContext.filesDir,"universal-editor").apply{mkdirs()}
        var activity:UniversalEditorTestActivity?=null
        var home:Activity?=null
        var session:VirtualG8Session?=null
        val context=object:android.content.ContextWrapper(targetContext){override fun getSharedPreferences(n:String,m:Int)=super.getSharedPreferences("ux_home_$n",m)}
        val store=xtr.keymapper.mythic.HeroLibraryStore(context)
        val before=store.read()
        val originalLibrary=xtr.keymapper.mythic.HeroLibraryStore(targetContext).read()
        val originalMappings=xtr.keymapper.keymap.KeymapProfiles(targetContext).sharedPref.all.toMap()
        val result=try{
            home=startActivitySync(Intent(targetContext,xtr.keymapper.virtual.VirtualHomeActivity::class.java).putExtra("ux_test",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val entry=addMonitor(UniversalEditorTestActivity::class.java.name,null,false)
            try{settle();click("Create mapping");activity=waitForMonitorWithTimeout(entry,10000) as? UniversalEditorTestActivity?:error("Home Create mapping route failed")}finally{removeMonitor(entry)}
            settle();text("create-name","UI TEST Custom Hero");click("create-blank")
            for((code,x,y)in listOf(Triple("BTN_GAMEPAD",300,800),Triple("BTN_EAST",600,800),Triple("BTN_TR",1000,800),Triple("BTN_TR2",1300,600),Triple("BTN_TL",1700,400),Triple("BTN_TL2",1900,800))){
                sendStatus(0,Bundle().apply{putString("editor_step","configure $code")});click("palette-$code");text("binding-name",code);if(code in setOf("BTN_GAMEPAD","BTN_EAST"))click("Tap")
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
            settle();check(UniversalStorage.read(context,saved.id)==document);find("mapping-BTN_TL2");dragTarget(document)
            sendStatus(0,Bundle().apply{putString("editor_step","rename/undo/recreation")});click("Rename profile");text("profile-name","UI TEST Unsaved rename");click("Apply name");find("Profile: UI TEST Unsaved rename · Unsaved")
            click("Undo");find("Profile: UI TEST Custom Hero");click("Redo");find("Profile: UI TEST Unsaved rename · Unsaved")
            val monitor=addMonitor(UniversalEditorTestActivity::class.java.name,null,false)
            try{main{activity!!.recreate()};activity=waitForMonitorWithTimeout(monitor,10000) as? UniversalEditorTestActivity?:error("Editor recreation failed");settle();find("Profile: UI TEST Unsaved rename · Unsaved")}finally{removeMonitor(monitor)}
            click("Revert");click("Confirm");find("Profile: UI TEST Custom Hero");check(UniversalStorage.read(context,saved.id)==document)
            find("mapping-canvas").performAction(Node.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id);settle();File(dir,"canvas.png").outputStream().use{check(uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
            sendStatus(0,Bundle().apply{putString("editor_step","saved custom profile replay")});main{session=VirtualG8Session();session!!.loadUniversal(document);session!!.axis("ABS_RX",128);session!!.axis("ABS_RY",128)}
            for(code in listOf("BTN_GAMEPAD","BTN_EAST","BTN_TR","BTN_TR2","BTN_TL","BTN_TL2")){
                main{session!!.key(code,true)};SystemClock.sleep(70);main{session!!.key(code,false)}
            }
            main{
                session!!.axis("ABS_RX",255);session!!.key("BTN_TL2",true);session!!.key("BTN_TR",true)
                session!!.assertState(JSONObject().put("active_pointers",2))
                session!!.command(JSONObject().put("action","disconnect"));session!!.assertState(JSONObject().put("active_pointers",0))
                session!!.command(JSONObject().put("action","connect"));session!!.key("BTN_TL2",true)
                session!!.command(JSONObject().put("action","diagnostics_on"));session!!.key("BTN_TR2",true)
                session!!.assertState(JSONObject().put("active_pointers",0).put("diagnostics",true))
                session!!.command(JSONObject().put("action","diagnostics_off"));session!!.close()
            };val trace=session!!.report();check(trace.getInt("final_pointers")==0&&trace.getInt("total_touches")>=12)
            File(dir,"replay.json").writeText(trace.toString(2))
            JSONObject().put("status","PASS").put("favorite_assigned_through_ui",true).put("home_create_entry",true).put("created_through_ui",true).put("bindings",6).put("right_stick_aim",true).put("reopened",true).put("marker_drag_single_undo",true).put("undo_redo",true).put("draft_recreation",true).put("overlap_disconnect_diagnostics",true).put("final_pointers",0).put("platform_touch_submissions",0)
        }catch(t:Throwable){File(dir,"failure-nodes.txt").writeText(all().joinToString("\n"){"${it.viewIdResourceName}: ${it.text} visible=${it.isVisibleToUser}"});JSONObject().put("status","FAIL").put("error",t.stackTraceToString())}
        finally{main{session?.close();activity?.finish();home?.finish()};settle();store.read().profiles.filter{p->p.id !in before.profiles.map{it.id}}.forEach{store.delete(it.id)}}
        check(store.read()==before){"Isolated library/favorites changed"}
        check(xtr.keymapper.mythic.HeroLibraryStore(targetContext).read()==originalLibrary){"Original library changed"}
        check(xtr.keymapper.keymap.KeymapProfiles(targetContext).sharedPref.all==originalMappings){"Original mappings changed"}
        result.put("original_library_and_mappings_unchanged",true)
        File(dir,"result.json").writeText(result.toString(2));finish(if(result.getString("status")=="PASS")Activity.RESULT_OK else Activity.RESULT_CANCELED,Bundle().apply{putString("universal_editor",result.toString())})
    }
}
