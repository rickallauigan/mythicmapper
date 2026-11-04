// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import xtr.keymapper.universal.*
import xtr.keymapper.controller.ControllerBindings
import xtr.keymapper.mythic.*
import java.util.UUID
import kotlin.math.hypot

@Composable internal fun UniversalEditorScreen(draft:UniversalDraft?,revision:Int,error:String?,status:String,image:android.graphics.Bitmap?,preview:MappingPreview?,detectedInput:String?,
    onChange:((()->Unit))->Unit,onSelectPreview:(Boolean)->Unit,onSave:()->Unit,onExit:()->Unit,onCreate:(UniversalMapping)->Unit,
    onImport:()->Unit,onImage:()->Unit,onClearError:()->Unit,onExport:()->Unit,onDuplicate:(String)->Unit,
    onFavorite:(Int,Boolean)->Unit,favorites:()->HeroLibrary,onSavedTarget:(String)->CapturedTarget?,savedTargets:()->List<CapturedTarget>,onCapture:()->Unit,
    cloneChoices:()->List<HeroProfile>,onCloneExisting:(HeroProfile,String)->Unit) {
    var selected by rememberSaveable{mutableStateOf<String?>(null)}
    var pending by remember{mutableStateOf(false)}
    var close by remember{mutableStateOf(false)}
    var destructive by remember{mutableStateOf<(() -> Unit)?>(null)}
    var favoritesOpen by remember{mutableStateOf(false)}
    var duplicate by remember{mutableStateOf(false)}
    var rename by remember{mutableStateOf(false)}
    var savedTargetOpen by remember{mutableStateOf(false)}
    var aimLink by remember{mutableStateOf(false)}
    fun exit(){if(draft?.dirty==true||pending)close=true else onExit()}
    BackHandler{exit()}
    Surface(Modifier.fillMaxSize(),color=MythicColors.Ink){
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp)
            .semantics{testTagsAsResourceId=true}.testTag("universal-editor"),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                Text("M · Hero mapping editor",style=MaterialTheme.typography.titleLarge)
                TextButton(onClick={exit()}){Text("Close editor")}
            }
            Text("MLBB · Original GameSir G8 · native left-stick movement",color=MythicColors.Muted)
            error?.let{Text(it,color=MaterialTheme.colorScheme.error);TextButton(onClick=onClearError){Text("Dismiss error")}}
            if(status.isNotEmpty())Text(status,color=MythicColors.Green,modifier=Modifier.testTag("editor-status"))
            if(draft==null){
                var name by rememberSaveable{mutableStateOf("")};var hero by rememberSaveable{mutableStateOf("")}
                Text("Create a personal mapping",style=MaterialTheme.typography.titleMedium)
                OutlinedTextField(name,{name=it},Modifier.fillMaxWidth().testTag("create-name"),label={Text("Profile name")},singleLine=true)
                OutlinedTextField(hero,{hero=it},Modifier.fillMaxWidth().testTag("create-hero"),label={Text("Hero name (optional)")},singleLine=true)
                Button(onClick={onCreate(UniversalMapping(name=name,hero=hero))},enabled=name.isNotBlank(),modifier=Modifier.testTag("create-blank")){Text("Create blank mapping")}
                OutlinedButton(onClick={onCreate(UniversalDraft.starter(name,hero))},enabled=name.isNotBlank()){Text("Create six-control starter")}
                Text("Blank and six-control drafts have no HUD coordinates. Measure targets before enabling.")
                TextButton(onClick=onImport){Text("Import universal JSON as a new copy")}
                Text("Clone existing · new UUID, originals preserved",style=MaterialTheme.typography.titleMedium)
                runCatching{cloneChoices()}.getOrDefault(emptyList()).forEach{p->OutlinedButton(onClick={onCloneExisting(p,name.ifBlank{"${p.name.take(65)} copy"})}){Text("Clone ${p.name}")}}
                Text("Legacy hero copies keep their compatibility editor and policies. Universal copies remain editable here.",color=MythicColors.Muted)
            }else{
                val d=draft.value;val a=d.actions.find{it.id==selected}
                Text("Profile: ${d.name}${if(draft.dirty)" · Unsaved"else""}",style=MaterialTheme.typography.titleMedium)
                TextButton(onClick={rename=true}){Text("Rename profile")}
                OutlinedTextField(d.hero,{text->onChange{draft.change(d.copy(hero=text))}},Modifier.fillMaxWidth(),label={Text("Hero (optional)")},singleLine=true)
                Text("Reference ${d.geometry.width} × ${d.geometry.height} · ${if(d.calibrated)"HUD confirmed"else"UNCALIBRATED"} · ${if(d.enabled)"Enabled"else"Disabled"}",color=MythicColors.Muted)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    OutlinedButton(onClick={onChange{draft.undo();selected=null;pending=false}},enabled=draft.canUndo&&!pending){Text("Undo")}
                    OutlinedButton(onClick={onChange{draft.redo();selected=null;pending=false}},enabled=draft.canRedo&&!pending){Text("Redo")}
                    TextButton(onClick={destructive={onChange{draft.reset();selected=null;pending=false}}}){Text("Revert")}
                }
                UniversalCanvas(d,selected,image,onBegin={if(!pending)onChange{draft.beginGesture()}},onEnd={onChange{draft.endGesture()}},onCancel={onChange{draft.cancelGesture()}},onSelect={if(!pending)selected=it},onMove={id,x,y->if(!pending)onChange{draft.action(draft.value.actions.single{it.id==id}.copy(x=x,y=y))}})
                Text(if(image==null)"NEUTRAL REFERENCE CANVAS · not an MLBB screenshot"else"User-selected HUD image · coordinates remain in saved reference plane",color=MythicColors.Muted)
                if(pending)Text("Apply the selected binding properties before Save, preview or changing controls.",color=MaterialTheme.colorScheme.secondary)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    TextButton(onClick=onImage){Text("Choose HUD image")}
                    TextButton(onClick=onCapture,enabled=!draft.dirty&&!pending){Text("Measure with capture tool")}
                }
                GeometryProperties(d.geometry,onApply={g->onChange{draft.change(d.copy(geometry=g,calibrated=false,enabled=false).validate())}})
                detectedInput?.let{code->OutlinedButton(onClick={if(!pending){val old=draft.value.actions.find{code in it.inputs};if(old!=null)selected=old.id else onChange{val next=UniversalAction(name=ControllerBindings.label(code),inputs=setOf(code));draft.action(next);selected=next.id}}}){Text("Bind observed Android input: ${ControllerBindings.label(code)}")}}
                Text("Controller input palette",style=MaterialTheme.typography.titleMedium)
                UniversalAction.buttons.toList().chunked(4).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    row.forEach{code->OutlinedButton(onClick={
                        val current=draft.value.actions.find{code in it.inputs}
                        if(current!=null)selected=current.id else onChange{val created=UniversalAction(name=ControllerBindings.label(code),inputs=setOf(code));draft.action(created);selected=created.id};pending=false
                    },enabled=!pending,modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("palette-$code")){Text(ControllerBindings.label(code))}}
                }}
                OutlinedButton(onClick={aimLink=true},enabled=!pending){Text("Right Stick · directional aim")}
                Text("Left Stick → native game movement. Rear L4/R4 can report L3/R3; no separate identities are invented.",color=MythicColors.Muted)
                a?.let{selectedAction->BindingProperties(selectedAction,d,revision,onPending={pending=it},onApply={next->onChange{draft.action(next);pending=false}},
                    onDelete={destructive={onChange{draft.remove(selectedAction.id);selected=null;pending=false}}},
                    onDuplicate={val free=UniversalAction.buttons.firstOrNull{c->d.actions.none{c in it.inputs}};if(free!=null)onChange{val copy=selectedAction.copy(id=UUID.randomUUID().toString(),name="${selectedAction.name.take(65)} copy",inputs=setOf(free),x=null,y=null);draft.action(copy);selected=copy.id;pending=false}},
                    onSavedTarget={savedTargetOpen=true})}
                Text("Mappings · ${d.actions.size}/8 targets",style=MaterialTheme.typography.titleMedium)
                d.actions.forEach{binding->TextButton(onClick={selected=binding.id},enabled=!pending,modifier=Modifier.testTag("mapping-${binding.inputs.first()}")){
                    Text("${binding.inputs.joinToString(" / "){if(it==UniversalAction.RIGHT_STICK)"RS"else ControllerBindings.label(it)}} → ${binding.name.ifBlank{binding.kind.name}} · ${binding.kind}${if(binding.aim)" + RS aim"else""} · ${binding.x?.let{"${it.toInt()}, ${binding.y!!.toInt()}"}?:"UNMEASURED"}")}}
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(d.calibrated,{yes->onChange{draft.change(d.copy(calibrated=yes,enabled=if(yes)d.enabled else false).validate())}},modifier=Modifier.testTag("confirm-hud"));Text("I measured the enabled targets for this reference HUD (not gameplay verification)",Modifier.weight(1f))}
                Row(verticalAlignment=Alignment.CenterVertically){Switch(d.enabled,{yes->onChange{check(!yes||d.calibrated){"Confirm HUD first"};draft.change(d.copy(enabled=yes))}},enabled=d.calibrated,modifier=Modifier.testTag("enable-mapping"));Text("Enable for explicit activation from Home")}
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Button(onClick=onSave,enabled=!pending,modifier=Modifier.testTag("save-universal")){Text("Save mapping")}
                    OutlinedButton(onClick={onSelectPreview(preview==null)},enabled=!pending){Text(if(preview==null)"Safe preview"else"STOP / RELEASE ALL")}
                }
                preview?.let{p->PreviewPanel(p,revision)}
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    TextButton(onClick={favoritesOpen=true},enabled=!draft.dirty&&!pending){Text("Assign favorite")}
                    TextButton(onClick={duplicate=true},enabled=!draft.dirty&&!pending){Text("Duplicate profile")}
                    TextButton(onClick=onExport,enabled=!draft.dirty&&!pending){Text("Export JSON")}
                }
                Text("GPLv3 · XtMapper-derived input/aim/repeat and lifecycle ownership. Preview records only; activation requires the real allowed G8 and MLBB foreground.",color=MythicColors.Muted)
            }
        }
    }
    if(close)AlertDialog(onDismissRequest={close=false},title={Text("Discard unsaved changes?")},text={Text("Your last saved mapping and favorites are preserved.")},confirmButton={TextButton(onClick=onExit){Text("Discard changes")}},dismissButton={TextButton(onClick={close=false}){Text("Keep editing")}})
    destructive?.let{go->AlertDialog(onDismissRequest={destructive=null},title={Text("Confirm mapping change")},confirmButton={TextButton(onClick={destructive=null;go()}){Text("Confirm")}},dismissButton={TextButton(onClick={destructive=null}){Text("Cancel")}})}
    if(rename){var name by rememberSaveable{mutableStateOf(draft!!.value.name)};AlertDialog(onDismissRequest={rename=false},title={Text("Rename personal mapping")},text={OutlinedTextField(name,{name=it},Modifier.semantics{testTagsAsResourceId=true}.testTag("profile-name"),label={Text("Profile name")})},confirmButton={TextButton(onClick={onChange{draft!!.change(draft.value.copy(name=name))};rename=false}){Text("Apply name")}},dismissButton={TextButton(onClick={rename=false}){Text("Cancel")}})}
    if(duplicate){var name by rememberSaveable{mutableStateOf("${draft!!.value.name.take(65)} copy")};AlertDialog(onDismissRequest={duplicate=false},title={Text("Duplicate personal mapping")},text={OutlinedTextField(name,{name=it},label={Text("New profile name")})},confirmButton={TextButton(onClick={duplicate=false;onDuplicate(name)}){Text("Duplicate")}},dismissButton={TextButton(onClick={duplicate=false}){Text("Cancel")}})}
    if(favoritesOpen){val library=runCatching(favorites).getOrDefault(HeroLibrary());AlertDialog(onDismissRequest={favoritesOpen=false},title={Text("Five favorite slots")},text={Column{library.favorites.forEachIndexed{slot,id->TextButton(onClick={
        favoritesOpen=false
        if(id==null||id==draft!!.value.id)onFavorite(slot,false)else destructive={onFavorite(slot,true)}
    }){Text("Favorite ${slot+1}: ${library.profiles.find{it.id==id}?.name?:"Empty"}")}}}},confirmButton={TextButton(onClick={favoritesOpen=false}){Text("Close")}})}
    if(savedTargetOpen){val targets=runCatching(savedTargets).getOrDefault(emptyList()).filter{it.width==draft!!.value.geometry.width&&it.height==draft.value.geometry.height&&(it.gamePackage.isNullOrBlank()||it.gamePackage==draft.value.game)}
        AlertDialog(onDismissRequest={savedTargetOpen=false},title={Text("Measured targets · matching reference plane")},text={Column{if(targets.isEmpty())Text("No saved target matches. Save this draft and use the capture tool.");targets.forEach{t->TextButton(onClick={onChange{val selectedAction=draft!!.value.actions.single{it.id==selected};val target=requireNotNull(onSavedTarget(t.id));draft.action(selectedAction.copy(x=target.x,y=target.y));pending=false};savedTargetOpen=false}){Text(t.name)}}}},confirmButton={TextButton(onClick={savedTargetOpen=false}){Text("Close")}})}
    if(aimLink&&draft!=null)AlertDialog(onDismissRequest={aimLink=false},title={Text("Right-stick directional target")},text={Column{
        Text("Link aim to a held/repeated target: the button still owns its contact; the stick changes direction.")
        draft.value.actions.filter{it.kind in setOf(TouchAction.HOLD,TouchAction.REPEAT)}.forEach{a->TextButton(onClick={onChange{draft.action(a.copy(aim=true));selected=a.id};aimLink=false}){Text("Aim ${a.name.ifBlank{a.inputs.first()}}")}}
        TextButton(onClick={onChange{val a=UniversalAction(name="Stick-held shooting",inputs=setOf(UniversalAction.RIGHT_STICK),aim=true);draft.action(a);selected=a.id};aimLink=false}){Text("New stick-displacement hold")}
    }},confirmButton={TextButton(onClick={aimLink=false}){Text("Cancel")}})
}

@Composable private fun GeometryProperties(g:MappingGeometry,onApply:(MappingGeometry)->Unit){
    var open by remember{mutableStateOf(false)}
    TextButton(onClick={open=true}){Text("Reference geometry")}
    if(open){var w by rememberSaveable{mutableStateOf(g.width.toString())};var h by rememberSaveable{mutableStateOf(g.height.toString())}
        AlertDialog(onDismissRequest={open=false},title={Text("Landscape reference geometry")},text={Column{
            Text("Changing geometry revokes calibration. Existing anchors are not silently rescaled.")
            OutlinedTextField(w,{w=it},label={Text("Width")});OutlinedTextField(h,{h=it},label={Text("Height")})
        }},confirmButton={TextButton(onClick={onApply(MappingGeometry(w.toIntOrNull()?:0,h.toIntOrNull()?:0));open=false}){Text("Apply geometry")}},dismissButton={TextButton(onClick={open=false}){Text("Cancel")}})}
}

@Composable private fun UniversalCanvas(d:UniversalMapping,selected:String?,image:android.graphics.Bitmap?,onBegin:()->Unit,onEnd:()->Unit,onCancel:()->Unit,onSelect:(String)->Unit,onMove:(String,Float,Float)->Unit){
    var size by remember{mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)}
    var zoom by rememberSaveable{mutableFloatStateOf(1f)};var px by rememberSaveable{mutableFloatStateOf(0f)};var py by rememberSaveable{mutableFloatStateOf(0f)};var pan by rememberSaveable{mutableStateOf(false)}
    Row{TextButton(onClick={pan=!pan}){Text(if(pan)"Pan/zoom mode"else"Place/drag mode")};TextButton(onClick={zoom=1f;px=0f;py=0f}){Text("Reset view")}}
    val plane=if(size.width>0&&size.height>0)UniversalViewport(size.width.toFloat(),size.height.toFloat(),d.geometry,zoom,px,py)else null
    val paint=remember{android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.WHITE;textSize=24f}}
    val latest by rememberUpdatedState(d)
    Canvas(Modifier.fillMaxWidth().height(240.dp).clipToBounds().background(MythicColors.Surface).border(1.dp,MythicColors.Line,RoundedCornerShape(8.dp))
        .onSizeChanged{size=it}.testTag("mapping-canvas").semantics{contentDescription="Reference mapping plane; numeric coordinate controls are below"}
        .pointerInput(plane,pan,selected){if(plane!=null&&!pan)detectTapGestures{point->
            val nearest=latest.actions.filter{it.x!=null}.minByOrNull{a->val p=plane.screen(a.x!!,a.y!!);hypot((p.x-point.x).toDouble(),(p.y-point.y).toDouble())}
            if(nearest!=null&&hypot((plane.screen(nearest.x!!,nearest.y!!).x-point.x).toDouble(),(plane.screen(nearest.x,nearest.y).y-point.y).toDouble())<32.dp.toPx())onSelect(nearest.id)
            else if(selected!=null)plane.canonical(point.x,point.y)?.let{onMove(selected,it.first,it.second)}
        }}.pointerInput(plane,pan,selected){if(plane!=null){if(pan)detectTransformGestures{_,move,z,_->zoom=(zoom*z).coerceIn(1f,4f);px=(px+move.x).coerceIn(-size.width.toFloat(),size.width.toFloat());py=(py+move.y).coerceIn(-size.height.toFloat(),size.height.toFloat())}
            else detectDragGestures(onDragStart={onBegin()},onDragEnd=onEnd,onDragCancel=onCancel){change,_->change.consume();selected?.let{id->plane.canonical(change.position.x,change.position.y)?.let{onMove(id,it.first,it.second)}}}
        }}){
        paint.textSize=12.sp.toPx()
        plane?.let{p->
            val corner=p.screen(0f,0f);val dims=Size(d.geometry.width*p.scale,d.geometry.height*p.scale)
            image?.let{drawImage(it.asImageBitmap(),dstOffset=androidx.compose.ui.unit.IntOffset(corner.x.toInt(),corner.y.toInt()),dstSize=androidx.compose.ui.unit.IntSize(dims.width.toInt().coerceAtLeast(1),dims.height.toInt().coerceAtLeast(1)))}
            drawRect(MythicColors.Muted,corner,dims,style=Stroke(1.dp.toPx()))
            for(i in 1..3){val x=corner.x+dims.width*i/4;drawLine(MythicColors.Line,Offset(x,corner.y),Offset(x,corner.y+dims.height))}
            d.actions.filter{it.x!=null}.forEach{a->val point=p.screen(a.x!!,a.y!!);drawCircle(if(a.id==selected)MythicColors.Violet else MythicColors.Green,10.dp.toPx(),point)
                if(a.aim&&a.id==selected)drawCircle(MythicColors.Violet,a.radius*p.scale,point,style=Stroke(2.dp.toPx()))
                drawContext.canvas.nativeCanvas.drawText(a.inputs.joinToString("/"){if(it==UniversalAction.RIGHT_STICK)"RS"else ControllerBindings.label(it)},point.x+12.dp.toPx(),point.y-10.dp.toPx(),paint)
            }
        }
    }
}

@Composable private fun PreviewPanel(p:MappingPreview,revision:Int){
    revision
    Text("SIMULATED · RECORDING ONLY · no gameplay injection",color=MythicColors.Violet)
    Text("Input: ${p.lastInput}")
    p.engine.lastCancellation?.let{Text("CANCEL: $it")}
    Text("Active pointers ${p.active.size} · owners ${p.engine.ownerCount}",Modifier.testTag("preview-pointers"))
    Row(Modifier.horizontalScroll(rememberScrollState())){UniversalAction.buttons.forEach{code->
        val held=remember(p,code){mutableStateOf(false)}
        OutlinedButton(onClick={held.value=!held.value;p.button(code,held.value)}){Text("SIM ${ControllerBindings.label(code)} ${if(held.value)"UP"else"DOWN"}")}
    }}
    var x by remember(p){mutableFloatStateOf(0f)};var y by remember(p){mutableFloatStateOf(0f)}
    Text("Right stick · X ${"%.2f".format(x)} / Y ${"%.2f".format(y)}")
    Slider(x,{x=it;p.axes(x,y)},valueRange=-1f..1f,modifier=Modifier.testTag("preview-x"));Slider(y,{y=it;p.axes(x,y)},valueRange=-1f..1f,modifier=Modifier.testTag("preview-y"))
    Row{TextButton(onClick={x=0f;y=0f;p.axes(0f,0f)}){Text("Neutral")};TextButton(onClick={p.engine.cancel("preview reset");x=0f;y=0f;p.axes(0f,0f)}){Text("Cancel contacts")}}
    p.events.takeLast(10).forEach{e->Text("${e.atMs}ms · ${when(e.action){1->"DOWN";2->"MOVE";else->"UP"}} pointer ${e.pointer} → ${e.x.toInt()}, ${e.y.toInt()}",style=MaterialTheme.typography.bodySmall)}
}
