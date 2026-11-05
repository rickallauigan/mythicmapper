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
    private var suite="flow"
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);suite=arguments?.getString("suite")?:"flow";start()}
    private fun nodes(n:Node?):List<Node> = if(n==null) emptyList() else listOf(n)+(0 until n.childCount).flatMap{nodes(n.getChild(it))}
    private fun all():List<Node>{if(Build.VERSION.SDK_INT>=34)uiAutomation.clearCache();return (nodes(uiAutomation.rootInActiveWindow)+uiAutomation.windows.flatMap{nodes(it.root)}).filter{it.packageName==targetContext.packageName}}
    private fun settle(){waitForIdleSync();uiAutomation.waitForIdle(150,5000)}
    private fun resetUiGesture(){
        val end=SystemClock.uptimeMillis()+10000
        while(uiAutomation.rootInActiveWindow?.packageName?.toString()!=targetContext.packageName&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50)
        val root=uiAutomation.rootInActiveWindow;check(root?.packageName?.toString()==targetContext.packageName){"UI gesture reset requires an app-owned surface"}
        val bounds=android.graphics.Rect();root.getBoundsInScreen(bounds)
        val now=SystemClock.uptimeMillis();val event=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_CANCEL,bounds.exactCenterX(),bounds.exactCenterY(),0)
        event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
        try{uiAutomation.injectInputEvent(event,true)}finally{event.recycle()}
    }
    private fun scroll(root:Node,forward:Boolean){
        val r=android.graphics.Rect();root.getBoundsInScreen(r)
        // The padded left margin avoids consuming drags in the mapping canvas/fields.
        val x=if(root.viewIdResourceName=="mythic-home")r.centerX().toFloat()else r.left+8*targetContext.resources.displayMetrics.density
        val y=r.top+r.height()*(if(forward).8f else .3f);val end=r.top+r.height()*(if(forward).3f else .8f)
        val start=SystemClock.uptimeMillis()
        fun event(action:Int,p:Float){val e=android.view.MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,x,p,0);e.source=android.view.InputDevice.SOURCE_TOUCHSCREEN;check(uiAutomation.injectInputEvent(e,true));e.recycle()}
        var released=false
        try{event(0,y);repeat(8){SystemClock.sleep(20);event(2,y+(end-y)*(it+1)/8)};event(1,end);released=true}
        finally{if(!released)runCatching{event(android.view.MotionEvent.ACTION_CANCEL,end)}}
        settle()
    }
    private fun find(value:String,contains:Boolean=false):Node {
        fun matches(n:Node)=n.viewIdResourceName==value||n.text?.toString()?.let{if(contains)it.contains(value)else it==value}==true
        // Navigation/Compose idle precedes publication of a new dialog accessibility window.
        val deadline=SystemClock.uptimeMillis()+10000
        while(SystemClock.uptimeMillis()<deadline){
            val current=all();current.firstOrNull{matches(it)&&it.isVisibleToUser}?.let{return it}
            if(current.any{it.viewIdResourceName in setOf("universal-editor","mythic-home")})break
            SystemClock.sleep(50)
        }
        for(forward in listOf(false,true))for(step in 0 until 45){
            all().firstOrNull{matches(it)&&it.isVisibleToUser}?.let{return it}
            val root=all().firstOrNull{it.viewIdResourceName in setOf("universal-editor","mythic-home")}?:break
            val direction=if(forward)Node.ACTION_SCROLL_FORWARD else Node.ACTION_SCROLL_BACKWARD
            if(root.actionList.none{it.id==direction})break
            scroll(root,forward)
        }
        repeat(20){all().firstOrNull{matches(it)&&it.isVisibleToUser}?.let{return it};SystemClock.sleep(50)}
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
        var released=false
        try{event(0,0f);repeat(8){SystemClock.sleep(25);event(2,(it+1)*10f)};event(1,80f);released=true}
        finally{if(!released)runCatching{event(android.view.MotionEvent.ACTION_CANCEL,80f)}}
        settle()
        fun number(id:String):Float{val n=find(id);return (nodes(n).firstOrNull{it.isEditable}?:n).text.toString().toFloat()}
        check(kotlin.math.abs(number("binding-x")-(target.x-80f/plane.scale))<1f){"Drag X coordinate mismatch"}
        check(kotlin.math.abs(number("binding-y")-(target.y-80f/plane.scale))<1f){"Drag Y coordinate mismatch"}
        find("Profile: UI TEST Custom Hero · Unsaved");click("Undo");find("Profile: UI TEST Custom Hero")
    }
    private fun main(f:()->Unit){var error:Throwable?=null;runOnMainSync{try{f()}catch(t:Throwable){error=t}};error?.let{throw it}}
    override fun onStart(){
        check(Build.HARDWARE in setOf("ranchu","goldfish"))
        uiAutomation.serviceInfo=uiAutomation.serviceInfo.apply{flags=flags or 0x10 or 0x40 or 0x100}
        if(suite=="crud"){crud();return}
        if(suite=="io"){documentPicker();return}
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
            resetUiGesture()
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
            fun rotate(orientation:Int){
                val desired=if(orientation==android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)android.content.res.Configuration.ORIENTATION_PORTRAIT else android.content.res.Configuration.ORIENTATION_LANDSCAPE
                if(activity!!.resources.configuration.orientation==desired)return
                val rotation=addMonitor(UniversalEditorTestActivity::class.java.name,null,false)
                try{main{activity!!.requestedOrientation=orientation};activity=waitForMonitorWithTimeout(rotation,10000) as? UniversalEditorTestActivity?:error("Editor orientation recreation failed");settle();check(activity!!.resources.configuration.orientation==desired)}finally{removeMonitor(rotation)}
            }
            rotate(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            find("mapping-canvas");settle();File(dir,"canvas.png").outputStream().use{check(uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
            check(UniversalStorage.read(context,saved.id)==document)
            rotate(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
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
            JSONObject().put("status","PASS").put("favorite_assigned_through_ui",true).put("home_create_entry",true).put("created_through_ui",true).put("bindings",6).put("right_stick_aim",true).put("reopened",true).put("marker_drag_single_undo",true).put("undo_redo",true).put("draft_recreation",true).put("portrait_landscape_recreation",true).put("overlap_disconnect_diagnostics",true).put("final_pointers",0).put("platform_touch_submissions",0)
        }catch(t:Throwable){File(dir,"failure-nodes.txt").writeText(all().joinToString("\n"){"${it.viewIdResourceName}: ${it.text} visible=${it.isVisibleToUser}"});JSONObject().put("status","FAIL").put("error",t.stackTraceToString())}
        finally{main{session?.close();activity?.finish();home?.finish()};settle();store.read().profiles.filter{p->p.id !in before.profiles.map{it.id}}.forEach{store.delete(it.id)}}
        check(store.read()==before){"Isolated library/favorites changed"}
        check(xtr.keymapper.mythic.HeroLibraryStore(targetContext).read()==originalLibrary){"Original library changed"}
        check(xtr.keymapper.keymap.KeymapProfiles(targetContext).sharedPref.all==originalMappings){"Original mappings changed"}
        result.put("original_library_and_mappings_unchanged",true)
        File(dir,"result.json").writeText(result.toString(2));finish(if(result.getString("status")=="PASS")Activity.RESULT_OK else Activity.RESULT_CANCELED,Bundle().apply{putString("universal_editor",result.toString())})
    }
    private fun crud(){
        val dir=File(targetContext.filesDir,"universal-editor").apply{mkdirs()}
        val context=object:android.content.ContextWrapper(targetContext){override fun getSharedPreferences(n:String,m:Int)=super.getSharedPreferences("ux_home_$n",m)}
        val store=xtr.keymapper.mythic.HeroLibraryStore(context);val before=store.read()
        val original=xtr.keymapper.mythic.HeroLibraryStore(targetContext).read()
        val mappings=xtr.keymapper.keymap.KeymapProfiles(targetContext).sharedPref.all.toMap()
        val activities=mutableListOf<Activity>()
        fun editor(id:String?=null):UniversalEditorTestActivity=(startActivitySync(Intent(targetContext,UniversalEditorTestActivity::class.java).putExtra("profile",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as UniversalEditorTestActivity).also{activities+=it;settle();resetUiGesture()}
        fun home():Activity {
            val existing=activities.lastOrNull{it is xtr.keymapper.virtual.VirtualHomeActivity&&!it.isDestroyed}
            if(existing!=null){
                if(!existing.hasWindowFocus())main{existing.startActivity(Intent(existing,xtr.keymapper.virtual.VirtualHomeActivity::class.java).putExtra("ux_test",true).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))}
                val end=SystemClock.uptimeMillis()+10000;while(!existing.hasWindowFocus()&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50)
                check(existing.hasWindowFocus()){ "Existing Home did not resume" };settle();return existing
            }
            return startActivitySync(Intent(targetContext,xtr.keymapper.virtual.VirtualHomeActivity::class.java).putExtra("ux_test",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).also{activities+=it;settle();resetUiGesture()}
        }
        fun fresh(name:String)=store.read().profiles.single{it.name==name&&it.id !in before.profiles.map{p->p.id}}
        fun closed(a:Activity){val end=SystemClock.uptimeMillis()+10000;while(!a.isDestroyed&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50);check(a.isDestroyed){"Editor did not close"}}
        val result=try{
            var a=editor();text("create-name","UI TEST Editor CRUD Starter");click("Create six-control starter")
            val starter=fresh("UI TEST Editor CRUD Starter");val source=UniversalStorage.read(context,starter.id)
            check(source.actions.size==6&&source.actions.all{it.x==null&&it.y==null}&&!source.enabled&&!source.calibrated)
            val slot=before.favorites.indexOfFirst{it==null};check(slot>=0)
            click("Assign favorite");click("Favorite ${slot+1}: Empty");check(store.read().favorites[slot]==starter.id)
            click("Duplicate profile");click("Duplicate")
            val copy=fresh("UI TEST Editor CRUD Starter copy");check(copy.id!=starter.id)
            check(UniversalStorage.read(context,copy.id).actions==source.actions&&store.read().favorites[slot]==starter.id)
            click("mapping-BTN_TR");click("Remove binding");click("Confirm");find("Mappings · 5/8 targets")
            click("Undo");find("Mappings · 6/8 targets");click("Redo");find("Mappings · 5/8 targets");click("save-universal")
            check(UniversalStorage.read(context,copy.id).actions.none{"BTN_TR" in it.inputs})
            click("Rename profile");text("profile-name","UI TEST Editor CRUD Renamed");click("Apply name");click("save-universal")
            check(UniversalStorage.read(context,copy.id).name=="UI TEST Editor CRUD Renamed")
            click("Close");closed(a);home();text("profile-search","UI TEST Editor CRUD Renamed")
            val navigation=addMonitor(UniversalEditorTestActivity::class.java.name,null,false)
            try{click("Visual editor");a=waitForMonitorWithTimeout(navigation,10000) as? UniversalEditorTestActivity?:error("Universal Home Edit route missing");activities+=a;settle()}finally{removeMonitor(navigation)}
            click("palette-BTN_WEST");find("Profile: UI TEST Editor CRUD Renamed · Unsaved")
            click("Close");click("Keep editing");check(!a.isFinishing)
            click("Close");click("Discard changes");closed(a)
            check(UniversalStorage.read(context,copy.id).actions.none{"BTN_WEST" in it.inputs})
            a=editor();text("create-name","UI TEST Editor CRUD Clone");click("Clone UI TEST Editor CRUD Starter")
            val clone=fresh("UI TEST Editor CRUD Clone");check(clone.id!=starter.id&&clone.id!=copy.id)
            val cloned=UniversalStorage.read(context,clone.id);check(cloned.actions==source.actions&&!cloned.enabled&&!cloned.calibrated)
            check(UniversalStorage.read(context,starter.id)==source)
            click("Close");closed(a);home()
            for(profile in listOf(starter,copy,clone)){
                val name=store.read().profiles.single{it.id==profile.id}.name
                text("profile-search",name);click("Delete");find("Delete personal mapping?");click("Cancel");check(store.read().profiles.any{it.id==profile.id})
                click("Delete");find("Delete personal mapping?");click("Delete");check(store.read().profiles.none{it.id==profile.id})
            }
            check(store.read()==before)
            JSONObject().put("status","PASS").put("starter_unmeasured",true).put("duplicate_uuid",true).put("binding_remove_undo_redo",true)
                .put("rename_saved",true).put("home_edit_route",true).put("discard_cancel_and_confirm",true).put("clone_existing",true)
                .put("delete_cancel_and_confirm",true).put("favorite_cleared_on_delete",true).put("platform_touch_submissions",0)
        }catch(t:Throwable){File(dir,"crud-failure-nodes.txt").writeText(all().joinToString("\n"){"${it.viewIdResourceName}: ${it.text} visible=${it.isVisibleToUser}"});JSONObject().put("status","FAIL").put("error",t.stackTraceToString())}
        finally{main{activities.asReversed().forEach{it.finish()}};settle();store.read().profiles.filter{p->p.id !in before.profiles.map{it.id}}.forEach{store.delete(it.id)}}
        check(store.read()==before&&xtr.keymapper.mythic.HeroLibraryStore(targetContext).read()==original&&xtr.keymapper.keymap.KeymapProfiles(targetContext).sharedPref.all==mappings){"CRUD changed original data"}
        val end=SystemClock.uptimeMillis()+10000;while(xtr.keymapper.editor.EditorSessionGuard.active&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50)
        check(!xtr.keymapper.editor.EditorSessionGuard.active){"Editor ownership leaked"}
        result.put("original_data_unchanged",true).put("editor_lease_released",true)
        File(dir,"crud-result.json").writeText(result.toString(2));finish(if(result.getString("status")=="PASS")Activity.RESULT_OK else Activity.RESULT_CANCELED,Bundle().apply{putString("universal_editor",result.toString())})
    }

    private fun pickerNode(value:String,requiredId:String?=null):Node {
        val end=SystemClock.uptimeMillis()+10000
        while(SystemClock.uptimeMillis()<end){
            if(Build.VERSION.SDK_INT>=34)uiAutomation.clearCache()
            val root=uiAutomation.rootInActiveWindow
            if(root?.packageName?.toString()=="com.android.documentsui")nodes(root).firstOrNull{
                it.isVisibleToUser&&(requiredId==null||it.viewIdResourceName==requiredId)&&(it.viewIdResourceName==value||it.text?.toString()?.equals(value,true)==true||it.contentDescription?.toString()?.equals(value,true)==true)
            }?.let{return it}
            SystemClock.sleep(50)
        };error("Expected DocumentsUI control unavailable: $value")
    }
    private fun pickerClick(value:String,requiredId:String?=null){
        val end=SystemClock.uptimeMillis()+10000
        do{var n=pickerNode(value,requiredId);while(!n.isClickable&&n.parent!=null)n=n.parent
            if(n.performAction(Node.ACTION_CLICK)){settle();return};SystemClock.sleep(50)
        }while(SystemClock.uptimeMillis()<end)
        error("DocumentsUI click failed: $value")
    }
    private fun chooseExportedFile(name:String){
        pickerClick("Show roots");pickerClick("Downloads","android:id/title")
        if(nodes(uiAutomation.rootInActiveWindow).any{it.contentDescription?.toString()=="List view"&&it.isVisibleToUser})pickerClick("List view")
        pickerClick(name)
    }
    private fun documentPicker(){
        val dir=File(targetContext.filesDir,"universal-editor").apply{mkdirs()}
        val context=object:android.content.ContextWrapper(targetContext){override fun getSharedPreferences(n:String,m:Int)=super.getSharedPreferences("ux_home_$n",m)}
        val store=xtr.keymapper.mythic.HeroLibraryStore(context);val before=store.read()
        val original=xtr.keymapper.mythic.HeroLibraryStore(targetContext).read();val mappings=xtr.keymapper.keymap.KeymapProfiles(targetContext).sharedPref.all.toMap()
        val activities=mutableListOf<Activity>();var exportedUri:android.net.Uri?=null;var deleted=false
        fun editor():UniversalEditorTestActivity=(startActivitySync(Intent(targetContext,UniversalEditorTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as UniversalEditorTestActivity).also{activities+=it;settle();resetUiGesture()}
        fun closed(a:Activity){val end=SystemClock.uptimeMillis()+10000;while(!a.isDestroyed&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50);check(a.isDestroyed)}
        fun resultUri(a:UniversalEditorTestActivity):android.net.Uri {val end=SystemClock.uptimeMillis()+10000;while(a.lastPickerUri==null&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50);return requireNotNull(a.lastPickerUri){"Picker result missing"}}
        val result=try{
            var a=editor();text("create-name","UI TEST Editor JSON");click("create-blank")
            click("palette-BTN_GAMEPAD");click("Tap");text("binding-x","300");text("binding-y","800");click("apply-binding")
            click("confirm-hud");click("enable-mapping");click("save-universal")
            val source=store.read().profiles.single{it.id !in before.profiles.map{p->p.id}};val document=UniversalStorage.read(context,source.id)
            val slot=before.favorites.indexOfFirst{it==null};check(slot>=0);click("Assign favorite");click("Favorite ${slot+1}: Empty")
            click("Safe preview");find("SIMULATED · RECORDING ONLY · no gameplay injection")
            click("SIM A DOWN");find("Active pointers 0 · owners 1");click("SIM A UP")
            find("DOWN pointer 50",contains=true);find("UP pointer 50",contains=true);find("Active pointers 0 · owners 0")
            click("STOP / RELEASE ALL");check(UniversalStorage.read(context,source.id)==document)
            click("Export JSON")
            val name="MM_UI_TEST_Editor_"+java.util.UUID.randomUUID().toString()+".json"
            check(pickerNode("android:id/title").performAction(Node.ACTION_SET_TEXT,Bundle().apply{putCharSequence(Node.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,name)}));settle()
            // Recreate the stopped editor while the real external document picker owns foreground.
            val recreation=addMonitor(UniversalEditorTestActivity::class.java.name,null,false)
            try{main{a.recreate()};pickerClick("android:id/button1");a=waitForMonitorWithTimeout(recreation,10000) as? UniversalEditorTestActivity?:error("Stopped editor recreation failed");activities+=a}finally{removeMonitor(recreation)}
            exportedUri=resultUri(a);settle();resetUiGesture();find("Exported universal schema2 JSON")
            val exported=context.contentResolver.openInputStream(exportedUri!!)!!.use(xtr.keymapper.structured.ProfileCodec::read)
            check(UniversalCodec.decode(exported)==document){"Picker export did not preserve the saved snapshot"}
            val cursor=context.contentResolver.query(exportedUri!!,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)!!
            val filename=cursor.use{check(it.moveToFirst());it.getString(0)};check(filename.startsWith("MM_UI_TEST_Editor_"))
            context.contentResolver.takePersistableUriPermission(exportedUri!!,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            click("Close");closed(a)
            a=editor();click("Import universal JSON as a new copy");chooseExportedFile(filename);resultUri(a);settle()
            val copy=store.read().profiles.single{it.id!=source.id&&it.id !in before.profiles.map{p->p.id}}
            val cloned=UniversalStorage.read(context,copy.id);check(cloned.id!=document.id&&cloned.actions==document.actions&&!cloned.enabled&&!cloned.calibrated)
            check(store.read().favorites[slot]==source.id&&UniversalStorage.read(context,source.id)==document)
            click("Close");closed(a)
            val library=store.read();val future=JSONObject(exported).put("schema_version",99).toString()
            context.contentResolver.openOutputStream(exportedUri!!,"wt")!!.use{it.write(future.toByteArray(Charsets.UTF_8))}
            a=editor();click("Import universal JSON as a new copy");chooseExportedFile(filename);resultUri(a)
            val end=SystemClock.uptimeMillis()+10000;var rejected=false
            while(SystemClock.uptimeMillis()<end){if(all().any{it.text?.toString()?.startsWith("Import rejected; no existing mapping changed:")==true}){rejected=true;break};SystemClock.sleep(50)}
            check(rejected){"Rejected import error was not visible"}
            check(store.read()==library){"Future import changed the library"}
            check(UniversalStorage.read(context,source.id)==document){"Future import changed the source mapping"}
            JSONObject().put("status","PASS").put("safe_preview_down_up",true).put("real_documents_ui",true).put("export_parent_recreation",true).put("export_round_trip",true)
                .put("import_new_disabled_uuid",true).put("favorites_preserved",true).put("future_import_rejected",true).put("platform_touch_submissions",0)
        }catch(t:Throwable){File(dir,"io-failure-nodes.txt").writeText(all().joinToString("\n"){"${it.viewIdResourceName}: ${it.text} visible=${it.isVisibleToUser}"});JSONObject().put("status","FAIL").put("error",t.stackTraceToString())}
        finally{
            exportedUri?.let{uri->
                deleted=runCatching{android.provider.DocumentsContract.deleteDocument(context.contentResolver,uri)}.getOrDefault(false)
                runCatching{context.contentResolver.releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)}
            }
            main{activities.asReversed().forEach{it.finish()}};settle();store.read().profiles.filter{p->p.id !in before.profiles.map{it.id}}.forEach{store.delete(it.id)}
        }
        check(store.read()==before&&xtr.keymapper.mythic.HeroLibraryStore(targetContext).read()==original&&xtr.keymapper.keymap.KeymapProfiles(targetContext).sharedPref.all==mappings){"Picker suite changed original data"}
        result.put("temporary_document_created",exportedUri!=null).put("temporary_document_deleted",deleted).put("original_data_unchanged",true)
        if(result.optString("status")=="PASS")check(deleted){"Test document cleanup failed"}
        File(dir,"io-result.json").writeText(result.toString(2));finish(if(result.getString("status")=="PASS")Activity.RESULT_OK else Activity.RESULT_CANCELED,Bundle().apply{putString("universal_editor",result.toString())})
    }

}
