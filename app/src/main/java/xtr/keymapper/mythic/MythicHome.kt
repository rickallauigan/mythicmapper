// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.mythic

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.hardware.input.InputManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import xtr.keymapper.activity.MainActivity
import xtr.keymapper.devices.InputDeviceDiscovery
import xtr.keymapper.editor.EditorActivity
import xtr.keymapper.keymap.KeymapConfig
import xtr.keymapper.keymap.KeymapProfiles
import xtr.keymapper.structured.StructuredProfiles

private data class NameRequest(val initial: String, val save: (String)->Unit)

@Composable
fun MythicHome(activity: MainActivity) {
    val store=remember {HeroLibraryStore(activity)}
    var library by remember {mutableStateOf(HeroLibrary())}
    var devices by remember {mutableStateOf(InputDeviceDiscovery.connected())}
    var error by remember {mutableStateOf<String?>(null)}
    var nameRequest by remember {mutableStateOf<NameRequest?>(null)}
    var restore by remember {mutableStateOf<HeroProfile?>(null)}
    var active by remember {mutableStateOf(false)}
    var selected by remember {mutableStateOf<String?>(null)}
    fun refresh() {runCatching {library=store.read()}.onFailure{error=it.message}}
    fun action(block: ()->Unit) {runCatching{block();refresh()}.onFailure{error=it.message}}
    fun edit(profile: HeroProfile, capture: Boolean) {
        activity.startActivity(Intent(activity,EditorActivity::class.java).putExtra(EditorActivity.PROFILE_NAME,profile.id)
            .putExtra(if(capture)EditorActivity.CAPTURE else EditorActivity.VISUAL,true))
    }
    DisposableEffect(activity) {
        val manager=activity.getSystemService(InputManager::class.java)
        val listener=object:InputManager.InputDeviceListener {
            private fun update(){devices=InputDeviceDiscovery.connected()}
            override fun onInputDeviceAdded(id:Int)=update()
            override fun onInputDeviceRemoved(id:Int)=update()
            override fun onInputDeviceChanged(id:Int)=update()
        }
        manager.registerInputDeviceListener(listener,Handler(Looper.getMainLooper()))
        onDispose {manager.unregisterInputDeviceListener(listener)}
    }
    LaunchedEffect(activity) {
        while(true){refresh();active=activity.isComposeServiceActive();selected=activity.pointerOverlay?.selectedProfile ?: activity.selectedProfileNameForCompose();delay(750)}
    }
    MythicHomeScreen(library,devices.any(MythicScope::supported),active,selected,
        onActivate={p -> action {
            check(devices.any(MythicScope::supported)){"Connect original G8 in the observed USB mode; see diagnostics for identity"}
            MythicScope.validateActivation(KeymapProfiles(activity).getProfile(p.id,false))
            KeymapConfig(activity).apply {disableAutoProfiling=false;useShizuku=false;touchpadInputMode=KeymapConfig.TOUCHPAD_DISABLED;pointerMode=KeymapConfig.POINTER_SYSTEM}.applySharedPrefs()
            xtr.keymapper.server.RemoteServiceHelper.useShizuku=false
            activity.selectProfileFromCompose(p.id);activity.launchAppFromCompose()
            activity.packageManager.getLaunchIntentForPackage(MythicScope.GAME)?.let {activity.startActivity(it)}
        }},
        onStop={activity.stopPointerFromCompose();active=false},
        onClone={preset -> nameRequest=NameRequest(if(preset.id.contains("beatrix"))"Beatrix training"else if(preset.id.contains("kimmy"))"Kimmy training"else"My hero") {name -> action{store.clone(name,HeroCatalog.lines(activity,preset),preset.id)}}},
        onRename={p -> nameRequest=NameRequest(p.name){name -> action{val s=store.read();store.update(s,s.copy(profiles=s.profiles.map{if(it.id==p.id)it.copy(name=name)else it}))}}},
        onDuplicate={p -> nameRequest=NameRequest("${p.name.take(70)} copy"){name -> action{val lines=KeymapProfiles(activity).sharedPref.getStringSet(p.id,null)?.toSet() ?: error("Mapping missing");store.clone(name,lines,p.preset)}}},
        onDelete={p -> action{check(p.id!=selected || !active){"Stop mapping before deleting the selected profile"};store.delete(p.id)}},
        onRestore={restore=it},
        onAssign={slot,id -> action{val s=store.read();store.update(s,s.assign(slot,id))}},
        onSwap={a,b -> action{val s=store.read();store.update(s,s.swap(a,b))}},
        onEditor={edit(it,false)},onCalibration={edit(it,true)},
        onDiagnostics={activity.startActivity(Intent(activity,xtr.keymapper.tester.TesterActivity::class.java))},
        onRuntimeDiagnostics={activity.startActivity(Intent(activity,DiagnosticsActivity::class.java))},
        onHeroSetup={p->activity.startActivity(Intent(activity,HeroTrainingActivity::class.java).putExtra("profile",p.id))},
        onPermission={activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:${activity.packageName}")))})
    nameRequest?.let {request ->
        var name by remember(request){mutableStateOf(request.initial)}
        AlertDialog(onDismissRequest={nameRequest=null},title={Text("Personal hero profile")},
            text={OutlinedTextField(name,{name=it},label={Text("Name")},singleLine=true)},
            confirmButton={TextButton(onClick={request.save(name);nameRequest=null}){Text("Save")}},
            dismissButton={TextButton(onClick={nameRequest=null}){Text("Cancel")}})
    }
    restore?.let {p -> AlertDialog(onDismissRequest={restore=null},title={Text("Restore starter mapping?")},
        text={Text("This replaces ${p.name}'s personal mapping with its unverified starter template.")},
        confirmButton={TextButton(onClick={action {
            check(!active || selected!=p.id){"Stop mapping before restoring the selected profile"}
            val preset=HeroCatalog.presets.find{it.id==p.preset} ?: error("Starter revision unavailable")
            StructuredProfiles.writeSet(KeymapProfiles(activity).sharedPref,p.id,HeroCatalog.lines(activity,preset))
        };restore=null}){Text("Restore")}},dismissButton={TextButton(onClick={restore=null}){Text("Cancel")}}) }
    error?.let {message -> AlertDialog(onDismissRequest={error=null},title={Text("Action needs attention")},text={Text(message)},confirmButton={TextButton(onClick={error=null}){Text("Close")}})}
}

@Composable
fun MythicHomeScreen(library: HeroLibrary, connected: Boolean, active: Boolean, selected: String?,
    onActivate:(HeroProfile)->Unit, onStop:()->Unit, onClone:(HeroPreset)->Unit,
    onRename:(HeroProfile)->Unit, onDuplicate:(HeroProfile)->Unit, onDelete:(HeroProfile)->Unit,
    onRestore:(HeroProfile)->Unit, onAssign:(Int,String?)->Unit, onSwap:(Int,Int)->Unit,
    onEditor:(HeroProfile)->Unit, onCalibration:(HeroProfile)->Unit, onDiagnostics:()->Unit, onPermission:()->Unit, onRuntimeDiagnostics:()->Unit={},onHeroSetup:(HeroProfile)->Unit={}) {
    var search by remember {mutableStateOf("")}
    var assignSlot by remember {mutableStateOf<Int?>(null)}
    MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xff6de0c1),surface=Color(0xff14212b),background=Color(0xff0b141d))) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("mythic-home"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                item {
                    Text("MythicMapper",style=MaterialTheme.typography.headlineLarge)
                    Text("MLBB · original GameSir Galileo G8",style=MaterialTheme.typography.bodyMedium)
                    ControllerSchematic()
                    Text(if(connected) "G8 connected · observed USB mode" else "G8 not detected · wake controller or check USB mode",color=if(connected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text("Selected: ${library.profiles.find{it.id==selected}?.name ?: "None"}")
                    Text(if(active)"Mapper service running" else "Mapper stopped")
                    Row {OutlinedButton(onClick=onDiagnostics){Text("Controller Tester")};Spacer(Modifier.width(8.dp));OutlinedButton(onClick=onPermission){Text("Overlay permission")}}
                    TextButton(onClick=onRuntimeDiagnostics){Text("Runtime metrics")}
                    if(active) Button(onClick=onStop){Text("Stop mapping")}
                }
                item {Text("Five favorites",style=MaterialTheme.typography.titleLarge)}
                items((0..4).toList()) {slot ->
                    val id=library.favorites[slot];val p=library.profiles.find{it.id==id}
                    Card(Modifier.fillMaxWidth().testTag("favorite-$slot")) {Column(Modifier.padding(12.dp)) {
                        Text("${slot+1}. ${p?.name ?: "Empty slot"}")
                        Row {
                            TextButton(onClick={p?.let(onActivate)},enabled=p!=null&&connected){Text("Use")}
                            TextButton(onClick={assignSlot=slot}){Text("Assign")}
                            TextButton(onClick={onAssign(slot,null)},enabled=p!=null){Text("Clear")}
                            TextButton(onClick={onSwap(slot,(slot+1)%5)}){Text("Move →")}
                        }
                    }}
                }
                item {Text("Offline hero library",style=MaterialTheme.typography.titleLarge)
                    OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),label={Text("Search profiles and starters")},singleLine=true)
                    Text("Starter templates require your HUD calibration. No new hero is marked verified.")}
                items(HeroCatalog.presets.filter{it.name.contains(search,true)}) {preset ->
                    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)) {Text(preset.name);Text(preset.status);Button(onClick={onClone(preset)},modifier=Modifier.testTag("clone-${preset.id}")){Text("Clone to personal profile")}}}
                }
                item {Text("Personal mappings",style=MaterialTheme.typography.titleLarge)}
                items(library.profiles.filter{it.name.contains(search,true)},key={it.id}) {p ->
                    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp)) {
                        Text(p.name,style=MaterialTheme.typography.titleMedium)
                        Row {TextButton(onClick={onActivate(p)},enabled=connected){Text("Use")};TextButton(onClick={onEditor(p)}){Text("Visual editor")};TextButton(onClick={onCalibration(p)}){Text("Calibrate")}}
                        Row {TextButton(onClick={onRename(p)}){Text("Rename")};TextButton(onClick={onDuplicate(p)}){Text("Duplicate")};TextButton(onClick={onDelete(p)}){Text("Delete")}}
                        if(p.preset in setOf("s10e-g8-beatrix-training-v1","s10e-g8-kimmy-training-v1"))TextButton(onClick={onHeroSetup(p)}){Text("Hero training setup")}
                        if(p.preset!=null)TextButton(onClick={onRestore(p)}){Text("Restore starter")}
                    }}
                }
                item {Text("GPLv3 · based on XtMapper by Xtr126 and contributors. Native movement is handled by MLBB. Never run two injecting mappers.",style=MaterialTheme.typography.bodySmall)}
            }
        }
        assignSlot?.let {slot -> AlertDialog(onDismissRequest={assignSlot=null},title={Text("Assign favorite ${slot+1}")},
            text={Column(Modifier.heightIn(max=320.dp).then(Modifier.background(MaterialTheme.colorScheme.surface))) {
                androidx.compose.foundation.lazy.LazyColumn {items(library.profiles,key={it.id}){p -> TextButton(onClick={onAssign(slot,p.id);assignSlot=null}){Text(p.name)}}}
            }},confirmButton={TextButton(onClick={assignSlot=null}){Text("Close")}})}
    }
}

/** Original code-drawn schematic, not vendor artwork or a product photograph. */
@Composable
private fun ControllerSchematic() {
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val w=size.width;val h=size.height
        drawRoundRect(Color(0xff2d4350),Offset(w*.12f,h*.2f),Size(w*.76f,h*.6f),CornerRadius(h*.22f))
        drawRoundRect(Color(0xff0b141d),Offset(w*.39f,h*.25f),Size(w*.22f,h*.5f),CornerRadius(8f))
        drawCircle(Color(0xff6de0c1),h*.12f,Offset(w*.25f,h*.45f))
        drawCircle(Color(0xff6de0c1),h*.10f,Offset(w*.70f,h*.62f))
        for((x,y) in listOf(.78f to .36f,.82f to .45f,.78f to .54f,.74f to .45f))drawCircle(Color(0xffc8d7df),h*.035f,Offset(w*x,h*y))
    }
}
