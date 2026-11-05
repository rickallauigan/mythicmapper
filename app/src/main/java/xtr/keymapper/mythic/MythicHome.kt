// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.mythic

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.hardware.input.InputManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xtr.keymapper.activity.MainActivity
import xtr.keymapper.devices.InputDeviceDiscovery
import xtr.keymapper.editor.EditorActivity
import xtr.keymapper.keymap.KeymapConfig
import xtr.keymapper.keymap.KeymapProfiles
import xtr.keymapper.structured.StructuredProfiles

private data class NameRequest(val initial: String, val save: (String)->Unit)

@Composable
fun MythicHome(activity: MainActivity) {MythicTheme{MythicHomeContent(activity)}}

@Composable
private fun MythicHomeContent(activity:MainActivity) {
    val store=remember {HeroLibraryStore(activity)}
    var library by remember {mutableStateOf(HeroLibrary())}
    var devices by remember {mutableStateOf(InputDeviceDiscovery.connected())}
    var error by remember {mutableStateOf<String?>(null)}
    var nameRequest by remember {mutableStateOf<NameRequest?>(null)}
    var restore by remember {mutableStateOf<HeroProfile?>(null)}
    var active by remember {mutableStateOf(false)}
    var selected by remember {mutableStateOf<String?>(null)}
    var permission by remember {mutableStateOf(Settings.canDrawOverlays(activity))}
    var readiness by remember {mutableStateOf<Map<String,String>>(emptyMap())}
    fun refresh() {runCatching {library=store.read()}.onFailure{error=it.message}}
    LaunchedEffect(library) {
        val profiles=library.profiles
        readiness=withContext(Dispatchers.IO){profiles.associate { p -> p.id to runCatching {
            val mapping=KeymapProfiles(activity).getProfile(p.id,false)
            when {mapping.disabled->"Disabled · setup required";mapping.heroGameplay?.calibrated==true->"Calibration confirmed";else->"Review HUD calibration"}
        }.getOrDefault("Mapping needs attention") }}
    }
    fun action(block: ()->Unit) {runCatching{block();refresh()}.onFailure{refresh();error=it.message}}
    fun edit(profile: HeroProfile, capture: Boolean) {
        if(!capture&&xtr.keymapper.universal.UniversalStorage.contains(KeymapProfiles(activity).sharedPref.getStringSet(profile.id,null).orEmpty().joinToString("\n"))){activity.openUniversalEditorFromCompose(profile.id);return}
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
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME){refresh();permission=Settings.canDrawOverlays(activity);devices=InputDeviceDiscovery.connected()}}
        activity.lifecycle.addObserver(observer);refresh()
        onDispose {manager.unregisterInputDeviceListener(listener);activity.lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(activity) {
        activity.repeatOnLifecycle(Lifecycle.State.STARTED){while(true){active=activity.isComposeServiceActive();selected=activity.pointerOverlay?.selectedProfileForUi() ?: activity.selectedProfileNameForCompose();delay(750)}}
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
        onAssign={slot,id -> action{val s=store.read();check(s==library){"Library changed; refresh and retry"};store.update(s,s.assign(slot,id))}},
        onSwap={a,b -> action{val s=store.read();store.update(s,s.swap(a,b))}},
        onEditor={edit(it,false)},onCalibration={edit(it,true)},
        onDiagnostics={activity.startActivity(Intent(activity,xtr.keymapper.tester.TesterActivity::class.java))},
        onRuntimeDiagnostics={activity.startActivity(Intent(activity,DiagnosticsActivity::class.java))},
        onHeroSetup={p->activity.openHeroTrainingFromCompose(p.id)},
        controllerName=devices.firstOrNull(MythicScope::supported)?.name ?: "Original GameSir Galileo G8",
        readiness=readiness,permissionGranted=permission,
        onCreateMapping={activity.openUniversalEditorFromCompose(null)},
        onVirtual=if(xtr.keymapper.BuildConfig.DEBUG)({activity.startActivity(Intent().setClassName(activity,"xtr.keymapper.virtual.VirtualG8Activity"))})else null,
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
    onEditor:(HeroProfile)->Unit, onCalibration:(HeroProfile)->Unit, onDiagnostics:()->Unit, onPermission:()->Unit,
    onRuntimeDiagnostics:()->Unit={},onHeroSetup:(HeroProfile)->Unit={},controllerName:String="Original GameSir Galileo G8",
    readiness:Map<String,String> = emptyMap(),permissionGranted:Boolean=true,onVirtual:(()->Unit)?=null,onCreateMapping:(()->Unit)?=null) {
    var search by rememberSaveable {mutableStateOf("")}
    var filter by rememberSaveable {mutableStateOf("All")}
    var assignSlot by remember {mutableStateOf<Int?>(null)}
    var replacement by remember {mutableStateOf<Pair<Int,HeroProfile>?>(null)}
    var deletion by remember {mutableStateOf<HeroProfile?>(null)}
    var creating by remember {mutableStateOf(false)}
    val chosen=library.profiles.find{it.id==selected}
    MythicTheme {
        Surface(Modifier.fillMaxSize(),color=MythicColors.Ink) {
            LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().semantics{testTagsAsResourceId=true}.testTag("mythic-home"),
                contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item("brand") {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Surface(shape=RoundedCornerShape(12.dp),color=Color(0xff241735),border=BorderStroke(1.dp,MythicColors.Violet)) {
                            Text("M",Modifier.padding(horizontal=12.dp,vertical=5.dp),fontSize=30.sp,fontWeight=FontWeight.Black,color=MythicColors.Violet)
                        }
                        Column(Modifier.weight(1f)){Text("MythicMapper",fontSize=22.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Text("MLBB · local controller mapping",style=MaterialTheme.typography.bodySmall,color=MythicColors.Muted)}
                        IconButton(onClick=onRuntimeDiagnostics){Icon(Icons.Outlined.Tune,"Diagnostics and settings")}
                    }
                }
                item("connection") {HomeCard {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.SportsEsports,null,tint=MythicColors.Violet,modifier=Modifier.size(32.dp))
                        Column(Modifier.weight(1f)){Text(if(connected)"GameSir G8 connected"else"Physical G8 not detected",fontWeight=FontWeight.SemiBold);Text(controllerName,style=MaterialTheme.typography.bodySmall,color=MythicColors.Muted)}
                        Text(if(connected)"USB"else"OFFLINE",color=if(connected)MythicColors.Green else MythicColors.Muted,fontSize=12.sp)
                    }
                    HorizontalDivider(color=MythicColors.Line)
                    Text(if(active)"● Mapping session running"else"○ Mapping stopped",color=if(active)MythicColors.Green else MythicColors.Muted)
                    Text("Selected: ${chosen?.name ?: "Choose a personal mapping"}")
                    Text("Root backend permissions are checked when starting mapping.",style=MaterialTheme.typography.bodySmall,color=MythicColors.Muted)
                    if(!permissionGranted)TextButton(onClick=onPermission){Text("Overlay permission required →")}
                }}
                item("actions") {
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Button(onClick={if(active)onStop()else chosen?.let(onActivate)},enabled=active||(chosen!=null&&connected),modifier=Modifier.weight(1f).heightIn(min=56.dp),shape=RoundedCornerShape(14.dp)) {Text(if(active)"Stop mapping"else"Start mapping")}
                        OutlinedButton(onClick={if(onCreateMapping!=null)onCreateMapping()else creating=true},modifier=Modifier.weight(1f).heightIn(min=56.dp),shape=RoundedCornerShape(14.dp)){Text("Create mapping")}
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick=onDiagnostics,modifier=Modifier.weight(1f).heightIn(min=52.dp),shape=RoundedCornerShape(14.dp)){Text("Controller Tester")}
                        onVirtual?.let {open->OutlinedButton(onClick=open,modifier=Modifier.weight(1f).heightIn(min=52.dp),shape=RoundedCornerShape(14.dp)){Text("Virtual G8 · SIMULATED")}}
                    }
                    if(onVirtual!=null)Text("DEV ONLY · Virtual G8 records touches; it is not physical hardware.",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodySmall,color=MythicColors.Muted)
                }
                item("favorites") {Text("Five favorites",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
                item("favorite-strip") {
                    LazyRow(Modifier.fillMaxWidth().testTag("favorite-strip"),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        items((0..4).toList(),key={it}){slot->
                            val id=library.favorites[slot];val p=library.profiles.find{it.id==id}
                            var menu by remember {mutableStateOf(false)}
                            Card(Modifier.width(148.dp).testTag("favorite-$slot"),shape=RoundedCornerShape(18.dp),
                                colors=CardDefaults.cardColors(containerColor=if(p!=null&&id==selected)Color(0xff2b1c40)else MythicColors.Surface),
                                border=BorderStroke(1.dp,if(p!=null&&id==selected)MythicColors.Violet else MythicColors.Line)) {
                                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                    ProfileMark(p?.name ?: "+")
                                    Text("${slot+1}. ${p?.name ?: "Empty slot"}",fontWeight=FontWeight.SemiBold,minLines=2,maxLines=3,overflow=TextOverflow.Ellipsis)
                                    Text(if(p!=null&&id==selected)"Selected"else if(p==null)"Assign a profile"else"Personal",fontSize=12.sp,color=MythicColors.Muted)
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        TextButton(onClick={p?.let(onActivate)},enabled=p!=null&&connected,modifier=Modifier.testTag("favorite-use-$slot")){Text("Use")}
                                        Box{IconButton(onClick={menu=true}){Icon(Icons.Outlined.MoreVert,"Manage favorite ${slot+1}")}
                                            DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                                                DropdownMenuItem(text={Text("Assign")},onClick={menu=false;assignSlot=slot})
                                                DropdownMenuItem(text={Text("Clear")},enabled=p!=null,onClick={menu=false;onAssign(slot,null)})
                                                DropdownMenuItem(text={Text("Move →")},onClick={menu=false;onSwap(slot,(slot+1)%5)})
                                                DropdownMenuItem(text={Text("Visual editor")},enabled=p!=null,onClick={menu=false;p?.let(onEditor)})
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item("library-search") {HomeCard(Modifier.testTag("library-search")) {
                    Text("Mapping library",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    OutlinedTextField(search,{search=it},Modifier.fillMaxWidth().testTag("profile-search"),label={Text("Search profiles and starters")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,shape=RoundedCornerShape(12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("All","Personal","Starters").forEach{label->FilterChip(selected=filter==label,onClick={filter=label},label={Text(label)})}
                    }
                    Text("Starter templates need your HUD calibration. No new hero is marked verified.",style=MaterialTheme.typography.bodySmall,color=MythicColors.Muted)
                }}
                if(filter!="Starters") {
                    items(library.profiles.filter{it.name.contains(search,true)},key={it.id}) {p->HomeCard(Modifier.testTag("profile-${p.id}")) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            ProfileMark(p.name)
                            Column(Modifier.weight(1f)){Text(p.name,fontWeight=FontWeight.SemiBold);Text("Personal · ${readiness[p.id] ?: "Review HUD calibration"}",style=MaterialTheme.typography.bodySmall,color=MythicColors.Muted)}
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            TextButton(onClick={onActivate(p)},enabled=connected){Text("Use")}
                            TextButton(onClick={onEditor(p)}){Text("Visual editor")}
                            TextButton(onClick={onCalibration(p)}){Text("Calibrate")}
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            TextButton(onClick={onRename(p)}){Text("Rename")};TextButton(onClick={onDuplicate(p)}){Text("Duplicate")}
                            TextButton(onClick={deletion=p}){Text("Delete",color=MaterialTheme.colorScheme.error)}
                        }
                        if(p.preset in setOf("s10e-g8-beatrix-training-v1","s10e-g8-kimmy-training-v1"))TextButton(onClick={onHeroSetup(p)}){Text("Hero training setup")}
                        if(p.preset!=null)TextButton(onClick={onRestore(p)}){Text("Restore starter")}
                    }}
                }
                if(filter!="Personal") {
                    items(HeroCatalog.presets.filter{it.name.contains(search,true)},key={"starter-${it.id}"}) {preset->HomeCard {
                        Text(preset.name,fontWeight=FontWeight.SemiBold);Text("STARTER · ${preset.status}",style=MaterialTheme.typography.bodySmall,color=MythicColors.Muted)
                        OutlinedButton(onClick={onClone(preset)},modifier=Modifier.testTag("clone-${preset.id}")){Text("Clone to personal profile")}
                    }}
                }
                if(library.profiles.none{it.name.contains(search,true)}&&HeroCatalog.presets.none{it.name.contains(search,true)})item("no-results"){Text("No matching mappings",color=MythicColors.Muted)}
                item("settings") {HomeCard {
                    Text("Diagnostics & settings",style=MaterialTheme.typography.titleMedium)
                    TextButton(onClick=onRuntimeDiagnostics){Text("Runtime metrics")};TextButton(onClick=onPermission){Text("Overlay permission")}
                    Text("Local mapping folders · planned in issue #17",color=MythicColors.Muted,style=MaterialTheme.typography.bodySmall)
                }}
                item("attribution") {Text("GPLv3 · based on XtMapper by Xtr126 and contributors. Never run two injecting mappers.",style=MaterialTheme.typography.bodySmall,color=MythicColors.Muted)}
            }
        }
        if(creating)AlertDialog(onDismissRequest={creating=false},title={Text("Create from a starter")},text={Column {
            Text("Clone a template, then edit and calibrate your personal mapping. Use Create mapping from Home for a blank universal profile.")
            HeroCatalog.presets.forEach{preset->TextButton(onClick={creating=false;onClone(preset)}){Text(preset.name)}}
        }},confirmButton={TextButton(onClick={creating=false}){Text("Cancel")}})
        assignSlot?.let{slot->AlertDialog(onDismissRequest={assignSlot=null},title={Text("Assign favorite ${slot+1}")},text={LazyColumn(Modifier.heightIn(max=320.dp)) {
            if(library.profiles.isEmpty())item{Text("Create a personal mapping first.")}
            items(library.profiles,key={it.id}){p->TextButton(onClick={assignSlot=null;if(library.favorites[slot]!=null&&library.favorites[slot]!=p.id)replacement=slot to p else onAssign(slot,p.id)}){Text(p.name)}}
        }},confirmButton={TextButton(onClick={assignSlot=null}){Text("Close")}})}
        replacement?.let{(slot,p)->AlertDialog(onDismissRequest={replacement=null},title={Text("Replace favorite ${slot+1}?")},text={Text("Assign ${p.name} to this slot? Your previous profile remains in the library.")},confirmButton={TextButton(onClick={onAssign(slot,p.id);replacement=null}){Text("Replace")}},dismissButton={TextButton(onClick={replacement=null}){Text("Cancel")}})}
        deletion?.let{p->AlertDialog(onDismissRequest={deletion=null},title={Text("Delete personal mapping?")},text={Text("Delete ${p.name} and clear its favorite references? This cannot be undone.")},confirmButton={TextButton(onClick={onDelete(p);deletion=null}){Text("Delete")}},dismissButton={TextButton(onClick={deletion=null}){Text("Cancel")}})}
    }
}

@Composable private fun HomeCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Card(modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,MythicColors.Line),colors=CardDefaults.cardColors(containerColor=MythicColors.Surface)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}
/** License-safe monogram, never a copied hero portrait. */
@Composable private fun ProfileMark(name:String) {
    Surface(shape=RoundedCornerShape(12.dp),color=Color(0xff2d2040),border=BorderStroke(1.dp,MythicColors.Line)) {
        Text(name.trim().take(1).uppercase(),Modifier.padding(horizontal=16.dp,vertical=9.dp),fontSize=22.sp,fontWeight=FontWeight.Bold,color=MythicColors.Violet)
    }
}
