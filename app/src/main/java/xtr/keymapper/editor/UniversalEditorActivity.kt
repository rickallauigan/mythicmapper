// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.editor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import xtr.keymapper.keymap.KeymapProfiles
import xtr.keymapper.mythic.*
import xtr.keymapper.universal.*

/** Extends the existing editor entry/lease/store. Preview has no privileged touch backend. */
open class UniversalEditorActivity:ComponentActivity() {
    companion object {const val PROFILE="profile"}
    private var draft by mutableStateOf<UniversalDraft?>(null)
    private var revision by mutableIntStateOf(0)
    private var error by mutableStateOf<String?>(null)
    private var status by mutableStateOf("")
    private var expected:Set<String> = emptySet()
    private var preview:MappingPreview?=null
    private var image by mutableStateOf<android.graphics.Bitmap?>(null)
    private var imageUri:String?=null
    private var exportText:String?=null
    private var detectedInput by mutableStateOf<String?>(null)
    private var observedDevice:Int?=null
    private val deviceListener=object:android.hardware.input.InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(id:Int){}
        override fun onInputDeviceChanged(id:Int){}
        override fun onInputDeviceRemoved(id:Int){if(observedDevice==id){preview?.engine?.cancel("controller disconnected");detectedInput=null;observedDevice=null;revision++}}
    }
    private fun allowed(id:Int)=xtr.keymapper.devices.InputDeviceDiscovery.connected().any{it.runtimeId==id&&MythicScope.supported(it)}
    private fun controllerKey(event:android.view.KeyEvent):Boolean {
        val code=xtr.keymapper.controller.ObservedAndroidG8.keys[event.keyCode]
        if(code!=null&&allowed(event.deviceId)&&(event.action==0||event.action==1)){
            observedDevice=event.deviceId
            if(event.action==0&&event.repeatCount==0)detectedInput=code
            preview?.button(code,event.action==0,"Android key ${event.keyCode}");revision++;return true
        }
        return false
    }
    override fun dispatchGenericMotionEvent(event:android.view.MotionEvent):Boolean {
        if(preview!=null&&allowed(event.deviceId)&&event.action==android.view.MotionEvent.ACTION_MOVE){
            val device=event.device?:return super.dispatchGenericMotionEvent(event)
            val pairs=listOf(android.view.MotionEvent.AXIS_Z to android.view.MotionEvent.AXIS_RZ,android.view.MotionEvent.AXIS_RX to android.view.MotionEvent.AXIS_RY)
            val pair=pairs.firstOrNull{device.getMotionRange(it.first)!=null&&device.getMotionRange(it.second)!=null}?:return super.dispatchGenericMotionEvent(event)
            fun signed(axis:Int):Float{val r=device.getMotionRange(axis);return xtr.keymapper.tester.AxisRange(axis,"Android",r.min,r.max,r.flat).signed(event.getAxisValue(axis))}
            observedDevice=event.deviceId;preview?.axes(signed(pair.first),signed(pair.second),"Android axes ${pair.first}/${pair.second}");revision++;return true
        }
        return super.dispatchGenericMotionEvent(event)
    }
    override fun onStart(){super.onStart();getSystemService(android.hardware.input.InputManager::class.java).registerInputDeviceListener(deviceListener,android.os.Handler(android.os.Looper.getMainLooper()))}
    private val screenshot=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)runCatching{
        contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);loadImage(uri)
    }.onFailure{error=it.message}}
    private val export=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)runCatching{
        contentResolver.openOutputStream(uri,"wt")!!.use{it.write(requireNotNull(exportText).toByteArray(Charsets.UTF_8))};status="Exported universal schema2 JSON"
    }.onFailure{error="Export failed: ${it.message}"}}
    private val importFile=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)runCatching{
        val text=contentResolver.openInputStream(uri)!!.use(xtr.keymapper.structured.ProfileCodec::read)
        val d=UniversalCodec.decode(text);create(d.cloned(d.name))
    }.onFailure{error="Import rejected; no existing mapping changed: ${it.message}"}}
    override fun onCreate(state:Bundle?) {
        super.onCreate(state);EditorSessionGuard.acquire(this)
        val original=requireNotNull(window.callback)
        window.callback=object:android.view.Window.Callback by original {
            override fun dispatchKeyEvent(event:android.view.KeyEvent)=controllerKey(event)||original.dispatchKeyEvent(event)
        }
        runCatching {
            val id=intent.getStringExtra(PROFILE)
            if(state?.containsKey("draft")==true){draft=UniversalDraft.restore(state.getString("draft")!!);expected=state.getStringArrayList("expected")!!.toSet();require(draft!!.value.id==id)}
            else if(id!=null){val d=UniversalStorage.read(this,id);expected=KeymapProfiles(this).sharedPref.getStringSet(id,null)!!.toSet();draft=UniversalDraft(d)}
            state?.getString("image")?.let{loadImage(Uri.parse(it))}
        }.onFailure{error=it.message}
        setContent {MythicTheme {
            revision
            UniversalEditorScreen(draft,revision,error,status,image,preview,detectedInput,
                onChange={block->attempt{stopPreview();block();revision++}},onSelectPreview={on->attempt{stopPreview();if(on)preview=MappingPreview(requireNotNull(draft).value){revision++};revision++}},
                onSave={attempt{save()}},onExit={finish()},onCreate={d->attempt{create(d)}},
                onImport={importFile.launch(arrayOf("application/json","text/plain"))},onImage={screenshot.launch(arrayOf("image/*"))},
                onClearError={error=null},onExport={attempt{check(!requireNotNull(draft).dirty){"Save before export"};exportText=UniversalCodec.encode(draft!!.value);export.launch("${draft!!.value.id}.json")}},
                onDuplicate={name->attempt{check(!draft!!.dirty){"Save before duplicating"};create(draft!!.value.cloned(name))}},
                onFavorite={slot,replace->attempt{check(!draft!!.dirty){"Save before assigning favorites"};val store=HeroLibraryStore(this);val old=store.read();check(old.favorites[slot]==null||old.favorites[slot]==draft!!.value.id||replace){"Confirm replacing the occupied slot"};store.update(old,old.assign(slot,draft!!.value.id));status="Favorite ${slot+1} assigned";revision++}},
                favorites={HeroLibraryStore(this).read()},onSavedTarget={id->CapturedTargetStore(this).read().find{it.id==id}},savedTargets={CapturedTargetStore(this).read()},
                onCapture={attempt{check(!draft!!.dirty){"Save your draft before measuring with the existing capture tool"};startActivity(Intent(this,EditorActivity::class.java).putExtra(EditorActivity.PROFILE_NAME,draft!!.value.id).putExtra(EditorActivity.CAPTURE,true))}},
                cloneChoices={HeroLibraryStore(this).read().profiles},onCloneExisting={profile,name->attempt{
                    val lines=KeymapProfiles(this).sharedPref.getStringSet(profile.id,null)?.toSet()?:error("Mapping missing")
                    val universal=UniversalStorage.document(lines)
                    if(universal!=null)create(universal.cloned(name))else{
                        val p=HeroLibraryStore(this).clone(name,lines,profile.preset)
                        startActivity(Intent(this,EditorActivity::class.java).putExtra(EditorActivity.PROFILE_NAME,p.id).putExtra(EditorActivity.VISUAL,true));finish()
                    }
                }})
        }}
    }
    private fun attempt(block:()->Unit){runCatching(block).onFailure{error=it.message?:it.javaClass.simpleName}}
    private fun create(value:UniversalMapping) {
        stopPreview();val p=HeroLibraryStore(this).clone(value.name,UniversalStorage.pack(value))
        intent=intent.putExtra(PROFILE,p.id);val d=UniversalStorage.read(this,p.id);expected=KeymapProfiles(this).sharedPref.getStringSet(p.id,null)!!.toSet();draft=UniversalDraft(d);revision++;status="Personal draft created · uncalibrated and disabled"
    }
    private fun save(){
        stopPreview();val session=requireNotNull(draft);val next=session.value.copy(updatedAt=maxOf(session.value.createdAt,System.currentTimeMillis()));UniversalStorage.save(this,next.id,next,expected,session.original.name)
        expected=UniversalStorage.pack(next);draft=UniversalDraft(next);revision++;status="Saved · ${if(session.value.enabled&&session.value.calibrated)"ready for explicit activation from Home"else"disabled until HUD confirmation and Enable"}"
    }
    private fun loadImage(uri:Uri){
        val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
        contentResolver.openInputStream(uri)!!.use{android.graphics.BitmapFactory.decodeStream(it,null,bounds)}
        require(bounds.outWidth>0&&bounds.outHeight>0&&bounds.outWidth.toLong()*bounds.outHeight<=16_000_000){"Image exceeds16 megapixels or is unreadable"}
        val geometry=draft?.value?.geometry?:MappingGeometry();require(geometry.fits(bounds.outWidth.toFloat(),bounds.outHeight.toFloat())){"Image aspect differs from the configured landscape plane"}
        val options=android.graphics.BitmapFactory.Options().apply{while(maxOf(bounds.outWidth,bounds.outHeight)/inSampleSize.coerceAtLeast(1)>2048)inSampleSize=inSampleSize.coerceAtLeast(1)*2}
        image=contentResolver.openInputStream(uri)!!.use{android.graphics.BitmapFactory.decodeStream(it,null,options)}?:error("Cannot decode image")
        imageUri=uri.toString();status="User-selected image · not game-state access"
    }
    private fun stopPreview(){preview?.close();preview=null}
    override fun onStop(){getSystemService(android.hardware.input.InputManager::class.java).unregisterInputDeviceListener(deviceListener);stopPreview();revision++;super.onStop()}
    override fun onSaveInstanceState(out:Bundle){draft?.let{out.putString("draft",it.snapshot());out.putStringArrayList("expected",ArrayList(expected))};imageUri?.let{out.putString("image",it)};super.onSaveInstanceState(out)}
    override fun onDestroy(){stopPreview();EditorSessionGuard.release(this);super.onDestroy()}
}
