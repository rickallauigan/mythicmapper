// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import xtr.keymapper.universal.*
import xtr.keymapper.controller.ControllerBindings

@Composable internal fun BindingProperties(a:UniversalAction,d:UniversalMapping,revision:Int,onPending:(Boolean)->Unit,onApply:(UniversalAction)->Unit,
    onDelete:()->Unit,onDuplicate:()->Unit,onSavedTarget:()->Unit) {
    var name by rememberSaveable(a){mutableStateOf(a.name)}
    var kind by rememberSaveable(a){mutableStateOf(a.kind.name)}
    var x by rememberSaveable(a){mutableStateOf(a.x?.toString()?:"")};var y by rememberSaveable(a){mutableStateOf(a.y?.toString()?:"")}
    var aim by rememberSaveable(a){mutableStateOf(a.aim)};var enabled by rememberSaveable(a){mutableStateOf(a.enabled)}
    var radius by rememberSaveable(a){mutableStateOf(a.radius.toString())};var dead by rememberSaveable(a){mutableStateOf(a.deadZone.toString())}
    var sensitivity by rememberSaveable(a){mutableStateOf(a.sensitivity.toString())};var invert by rememberSaveable(a){mutableStateOf(a.invertY)}
    var interval by rememberSaveable(a){mutableStateOf(a.repeatMs.toString())};var anchor by rememberSaveable(a){mutableStateOf(a.anchorMs.toString())}
    var priority by rememberSaveable(a){mutableStateOf(a.priority.toString())};var exclusive by rememberSaveable(a){mutableStateOf(a.exclusiveAim)}
    var cancel by rememberSaveable(a){mutableStateOf(a.cancelOthers)}
    var aliases by rememberSaveable(a){mutableStateOf(a.inputs.sorted().joinToString(","))}
    var aliasDialog by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    fun value():UniversalAction {
        val type=TouchAction.valueOf(kind)
        fun coordinate(v:String):Float?=if(v.isBlank())null else v.toFloat()
        return a.copy(name=name,kind=type,inputs=aliases.split(',').filter{it.isNotEmpty()}.toSet(),
            x=if(type==TouchAction.RESET)null else coordinate(x),y=if(type==TouchAction.RESET)null else coordinate(y),
            enabled=enabled,aim=aim&&type in setOf(TouchAction.HOLD,TouchAction.REPEAT),radius=radius.toFloat(),deadZone=dead.toFloat(),sensitivity=sensitivity.toFloat(),
            invertY=invert,repeatMs=interval.toInt(),priority=priority.toInt(),exclusiveAim=exclusive,anchorMs=anchor.toInt(),cancelOthers=cancel&&type!=TouchAction.RESET)
    }
    val differs=runCatching{value()!=a}.getOrDefault(true)
    LaunchedEffect(differs){onPending(differs)}
    HorizontalDivider();Text("Selected binding · ${a.inputs.joinToString(" / ")}",style=MaterialTheme.typography.titleMedium)
    OutlinedTextField(name,{name=it},Modifier.fillMaxWidth().testTag("binding-name"),label={Text("Action label")},singleLine=true)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
        TouchAction.entries.forEach{mode->FilterChip(selected=kind==mode.name,onClick={kind=mode.name;if(mode in setOf(TouchAction.TAP,TouchAction.RESET))aim=false},
            enabled=UniversalAction.RIGHT_STICK !in a.inputs||mode==TouchAction.HOLD,label={Text(when(mode){TouchAction.TAP->"Tap";TouchAction.HOLD->"Hold / press-release";TouchAction.REPEAT->"Held repeat";TouchAction.RESET->"Reset / release"})})}
    }
    if(kind!=TouchAction.RESET.name){
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(x,{x=it},Modifier.weight(1f).testTag("binding-x"),label={Text("Anchor X")},singleLine=true)
            OutlinedTextField(y,{y=it},Modifier.weight(1f).testTag("binding-y"),label={Text("Anchor Y")},singleLine=true)
        }
        Text("Canonical pixels · tap/drag the reference canvas or enter measured coordinates. No default anchor.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick=onSavedTarget){Text("Use saved measured target")}
    }
    if(kind in setOf(TouchAction.HOLD.name,TouchAction.REPEAT.name)){
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(aim,{aim=it},enabled=UniversalAction.RIGHT_STICK !in a.inputs,modifier=Modifier.testTag("binding-aim"));Text("Right-stick directional aim")}
    }
    if(aim&&kind in setOf(TouchAction.HOLD.name,TouchAction.REPEAT.name)){
        OutlinedTextField(radius,{radius=it},Modifier.fillMaxWidth().testTag("binding-radius"),label={Text("Aim radius")},singleLine=true)
        OutlinedTextField(dead,{dead=it},Modifier.fillMaxWidth().testTag("binding-dead-zone"),label={Text("Dead zone (0–0.9)")},singleLine=true)
        OutlinedTextField(sensitivity,{sensitivity=it},Modifier.fillMaxWidth().testTag("binding-sensitivity"),label={Text("Sensitivity (0.1–4)")},singleLine=true)
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(invert,{invert=it});Text("Invert Y")}
        OutlinedTextField(priority,{priority=it},Modifier.fillMaxWidth().testTag("binding-priority"),label={Text("Touch aim priority (0–100)")},singleLine=true)
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(exclusive,{exclusive=it});Text("Suspend lower-priority aimed contacts; resume held owners on release",Modifier.weight(1f))}
    }
    if(kind==TouchAction.REPEAT.name){
        OutlinedTextField(interval,{interval=it},Modifier.fillMaxWidth().testTag("binding-repeat"),label={Text("Repeat interval (60–60000 ms)")},singleLine=true)
        if(aim)OutlinedTextField(anchor,{anchor=it},Modifier.fillMaxWidth().testTag("binding-acquisition"),label={Text("Anchor acquisition (0–250 ms)")},singleLine=true)
        Text("Repeats only while the configured input is physically held. Release, interruption or STOP cancels it.")
    }
    if(kind!=TouchAction.RESET.name&&UniversalAction.RIGHT_STICK !in a.inputs)Row(verticalAlignment=Alignment.CenterVertically){Checkbox(cancel,{cancel=it});Text("Cancel other contacts first; require neutral before stick shooting resumes",Modifier.weight(1f))}
    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(enabled,{enabled=it});Text("Binding enabled")}
    if(UniversalAction.RIGHT_STICK !in a.inputs)TextButton(onClick={aliasDialog=true}){Text("Share target with another button")}
    error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    Button(onClick={runCatching{value().also{it.validate(d.geometry)};onApply(value());error=null}.onFailure{error=it.message}},modifier=Modifier.testTag("apply-binding")){Text("Apply binding")}
    Row{TextButton(onClick=onDelete){Text("Remove binding")};TextButton(onClick=onDuplicate,enabled=UniversalAction.buttons.any{code->d.actions.none{code in it.inputs}}&&d.actions.size<8){Text("Duplicate to a free input (unmeasured)")}}
    if(aliasDialog)AlertDialog(onDismissRequest={aliasDialog=false},title={Text("Shared first-DOWN / last-UP owners")},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())){
        UniversalAction.buttons.forEach{code->val list=aliases.split(',').toMutableSet();val used=d.actions.any{it.id!=a.id&&code in it.inputs}
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(code in list,{yes->if(yes)list.add(code)else list.remove(code);aliases=list.filter{it.isNotEmpty()}.sorted().joinToString(",")},enabled=!used);Text(ControllerBindings.label(code))}
        }
    }},confirmButton={TextButton(onClick={aliasDialog=false}){Text("Review aliases")}})
}
