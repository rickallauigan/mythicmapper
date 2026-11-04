// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.tester

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.*
import java.util.Locale
import kotlin.math.*

private val Ink=Color(0xff080910)
private val CardInk=Color(0xff12121f)
private val Violet=Color(0xffb779ff)
private val Line=Color(0xff38304e)
private val Muted=Color(0xffb8b2ce)
private val Green=Color(0xff64f28a)
private fun accent(name:String)=when(name){"A"->Green;"B"->Color(0xffff5c69);"X"->Color(0xff50c6ff);"Y"->Color(0xffffdf52);else->Violet}
private fun number(v:Float?)=if(TesterWork.baselineFormatting)v?.let{"%.2f".format(Locale.ROOT,it)}?:"—"else DiagnosticNumbers.fixed(v)

@Composable fun TesterScreen(state:TesterState,onBack:()->Unit,onReset:()->Unit,onPause:()->Unit,onClear:()->Unit,
    onCenter:()->Unit,onThresholds:(Thresholds)->Unit,onExport:()->Unit,onPageChanged:(Int)->Unit={},pageRequest:Int=-1,onArmBenchmark:()->Unit={},onAliases:()->Unit={},onArmDisconnect:()->Unit={},liveState:State<TesterState>?=null,baselineRendering:Boolean=false){
    val source=liveState?:rememberUpdatedState(state)
    val chrome by remember(source){derivedStateOf{val s=source.value;TesterChrome(s.device,s.status,s.safety,s.thresholds,s.benchmarkPrompt,s.benchmarkRemaining)}}
    SideEffect{TesterWork.hit(0)}
    val pageScroll=rememberLazyListState()
    val baselineScroll=rememberScrollState()
    val baselineState=if(baselineRendering)source.value else null
    var page by rememberSaveableInt();var settings by remember{mutableStateOf(false)}
    LaunchedEffect(page){pageScroll.scrollToItem(0);baselineScroll.scrollTo(0)}
    LaunchedEffect(pageRequest){if(pageRequest in 0..2)page=pageRequest}
    val colors=remember{darkColorScheme(primary=Violet,background=Ink,surface=CardInk,onSurface=Color(0xfff3efff),outline=Line)}
    MaterialTheme(colorScheme=colors){
        Surface(Modifier.fillMaxSize(),color=Ink){Column(Modifier.safeDrawingPadding()){
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back")}
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){
                    Text(androidx.compose.ui.text.buildAnnotatedString{withStyle(androidx.compose.ui.text.SpanStyle(color=Violet)){append("✦ ")};append("MythicMapper")},fontWeight=FontWeight.Bold,fontSize=21.sp)
                    Text("Controller Tester",color=Muted,fontSize=13.sp)
                }
                IconButton(onClick={settings=true}){Icon(Icons.Outlined.Tune,"Test thresholds")}
            }
            HorizontalDivider(color=Line.copy(alpha=.5f))
            if(baselineState!=null)Column(Modifier.weight(1f).verticalScroll(baselineScroll).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                if(chrome.benchmarkPrompt.isNotBlank())DiagnosticCard{Text(chrome.benchmarkPrompt,color=Violet,fontWeight=FontWeight.Bold);if(chrome.benchmarkRemaining>0)Text("${chrome.benchmarkRemaining} seconds remaining");Text("Physical protocol v1 · no touch injection",fontSize=12.sp,color=Muted)}
                if(page==0){
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Text("GameSir G8 Galileo Tester",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Bold);Badge(chrome.status)
                    }
                    Text("Live input · Diagnostics · No touch injection",color=Muted,fontSize=12.sp)
                    ControllerDrawing(baselineState.held,baselineState.left,baselineState.right)
                    LiveButtons(source)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        StickCard("Left Stick (L)",baselineState.left,baselineState.thresholds.deadZone,Modifier.weight(1f));StickCard("Right Stick (R)",baselineState.right,baselineState.thresholds.deadZone,Modifier.weight(1f))
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        LiveTrigger(source,"LT",Modifier.weight(1f));LiveTrigger(source,"RT",Modifier.weight(1f))
                    }
                    Text("L4 / R4 mirror their programmed buttons. If assigned to L3 / R3, the same indicators light. Identical Android codes cannot be distinguished.",color=Muted,fontSize=11.sp)
                    Text(chrome.safety,color=if(chrome.status=="Connected")Green else Muted,fontSize=12.sp)
                    Text("Mapping stays stopped after testing. Restart it from Home when ready.",color=Muted,fontSize=12.sp)
                }else if(page==2)LiveGuided(source,onReset,onArmBenchmark,onAliases,onArmDisconnect)
                else LiveDiagnostics(source,onReset,onPause,onClear,onExport)
            }else{
            LazyColumn(Modifier.weight(1f),state=pageScroll,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                if(chrome.benchmarkPrompt.isNotBlank())item("benchmark"){DiagnosticCard{Text(chrome.benchmarkPrompt,color=Violet,fontWeight=FontWeight.Bold);if(chrome.benchmarkRemaining>0)Text("${chrome.benchmarkRemaining} seconds remaining");Text("Physical protocol v1 · no touch injection",fontSize=12.sp,color=Muted)}}
                if(page==0){
                    item("title"){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Text("GameSir G8 Galileo Tester",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Bold);Badge(chrome.status)
                    }}
                    item("source"){Text("Live input · Diagnostics · No touch injection",color=Muted,fontSize=12.sp)}
                    item("controller"){LiveController(source)}
                    item("buttons"){LiveButtons(source)}
                    item("sticks"){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        LiveStick(source,true,Modifier.weight(1f));LiveStick(source,false,Modifier.weight(1f))
                    }}
                    item("triggers"){Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        LiveTrigger(source,"LT",Modifier.weight(1f));LiveTrigger(source,"RT",Modifier.weight(1f))
                    }}
                    item("rear"){Text("L4 / R4 mirror their programmed buttons. If assigned to L3 / R3, the same indicators light. Identical Android codes cannot be distinguished.",color=Muted,fontSize=11.sp)}
                    item("safety"){Text(chrome.safety,color=if(chrome.status=="Connected")Green else Muted,fontSize=12.sp)}
                    item("exit"){Text("Mapping stays stopped after testing. Restart it from Home when ready.",color=Muted,fontSize=12.sp)}
                }else if(page==2){
                    item("guide"){LiveGuided(source,onReset,onArmBenchmark,onAliases,onArmDisconnect)}
                }else{
                    item("checklist"){val current by source;Checklist(current,onReset)}
                    item("events"){val current by source;EventLog(current,onPause,onClear,onExport)}
                    item("info"){val current by source;ControllerInfo(current)}
                }
            }
            }
            Surface(Modifier.padding(horizontal=12.dp,vertical=8.dp).fillMaxWidth(),color=CardInk,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Line)){
                Row(Modifier.padding(6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    for((index,label)in listOf("Controller","Diagnostics","Guided").withIndex()){
                        key(label){
                            val selected=page==index
                            Surface(Modifier.weight(1f).clickable{page=index;onPageChanged(index)}.semantics{contentDescription="$label tab";this.selected=selected},
                                color=if(selected)Color(0xff302044)else Color.Transparent,shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,if(selected)Violet else Color.Transparent)){
                                Column(Modifier.padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(if(index==0)Icons.Outlined.SportsEsports else Icons.Outlined.FactCheck,null,tint=if(selected)Violet else Muted);Text(label,fontSize=12.sp)}
                            }
                        }
                    }
                }
            }
        }}
        if(settings)AlertDialog(onDismissRequest={settings=false},title={Text("Diagnostic thresholds")},text={
            Column{
                Text("Change thresholds to reset test progress. Android Y is positive down. Range requires eight outer sectors and all four extents.",fontSize=13.sp)
                Text("Dead zone ${number(chrome.thresholds.deadZone)}")
                Slider(value=chrome.thresholds.deadZone,onValueChange={onThresholds(chrome.thresholds.copy(deadZone=it))},valueRange=.05f.. .3f)
                Text("Direction ${number(chrome.thresholds.direction)}")
                Slider(value=chrome.thresholds.direction,onValueChange={onThresholds(chrome.thresholds.copy(direction=it))},valueRange=.4f.. .8f)
                Text("Full travel ${number(chrome.thresholds.travel)}")
                Slider(value=chrome.thresholds.travel,onValueChange={onThresholds(chrome.thresholds.copy(travel=it))},valueRange=.8f.. .98f)
                TextButton(onClick=onCenter){Text("Measure center offset now")}
                Text("Release both sticks first. This measures observed offset, not an automatic calibration.",fontSize=12.sp,color=Muted)
            }
        },confirmButton={TextButton(onClick={settings=false}){Text("Done")}})
    }
}
@androidx.compose.runtime.Immutable
private data class TesterChrome(val device:TesterDevice?,val status:String,val safety:String,val thresholds:Thresholds,val benchmarkPrompt:String,val benchmarkRemaining:Int)
@androidx.compose.runtime.Immutable
private data class VisualReading(val held:Set<String>,val left:StickReading,val right:StickReading)
@Composable private fun LiveController(source:State<TesterState>){
    val visual by remember(source){derivedStateOf{val s=source.value;VisualReading(s.held,s.left,s.right)}}
    ControllerDrawing(visual.held,visual.left,visual.right)
}
@Composable private fun LiveButtons(source:State<TesterState>){
    val held by remember(source){derivedStateOf{source.value.held}}
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)){
        for(row in listOf(listOf("A","B","X","Y","LB","RB","LT","RT"),listOf("↑","↓","←","→","L3","R3","View","Menu"))){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){for(label in row)ButtonLamp(label,label in held,Modifier.weight(1f))}
        }
    }
}
@Composable private fun LiveStick(source:State<TesterState>,left:Boolean,modifier:Modifier){
    val reading by remember(source,left){derivedStateOf{if(left)source.value.left else source.value.right}}
    val zone by remember(source){derivedStateOf{source.value.thresholds.deadZone}}
    StickCard(if(left)"Left Stick (L)"else"Right Stick (R)",reading,zone,modifier)
}
@androidx.compose.runtime.Immutable
private data class TriggerReading(val range:AxisRange?,val raw:Float?,val digital:Boolean,val pressed:Boolean)
@Composable private fun LiveTrigger(source:State<TesterState>,name:String,modifier:Modifier){
    val reading by remember(source,name){derivedStateOf{val s=source.value;val code=if(name=="LT")s.device?.lt else s.device?.rt
        TriggerReading(s.device?.ranges?.find{it.code==code},s.raw[code],name in (s.device?.keys?:emptySet()),name in s.held)}}
    TriggerCard(name,reading.range,reading.raw,reading.digital,reading.pressed,modifier)
}
@Composable private fun LiveGuided(source:State<TesterState>,reset:()->Unit,benchmark:()->Unit,aliases:()->Unit,disconnect:()->Unit){
    val current by source
    GuidedHardware(current,reset,benchmark,aliases,disconnect)
}
@Composable private fun LiveDiagnostics(source:State<TesterState>,reset:()->Unit,pause:()->Unit,clear:()->Unit,export:()->Unit){
    val current by source
    Checklist(current,reset);EventLog(current,pause,clear,export);ControllerInfo(current)
}
@Composable private fun rememberSaveableInt()=androidx.compose.runtime.saveable.rememberSaveable{mutableIntStateOf(0)}
@Composable private fun Badge(label:String){
    val color=if(label=="Connected")Green else Muted
    Surface(color=color.copy(alpha=.07f),shape=RoundedCornerShape(30.dp),border=BorderStroke(1.dp,color)){
        Row(Modifier.padding(horizontal=10.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.SportsEsports,null,Modifier.size(16.dp),tint=color);Spacer(Modifier.width(5.dp));Text(label,color=color,fontSize=12.sp,fontWeight=FontWeight.SemiBold)}
    }
}
@Composable private fun DiagnosticCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    Surface(modifier,color=CardInk,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Line)){
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}
@Composable private fun ButtonLamp(label:String,pressed:Boolean,modifier:Modifier){
    val color=accent(label)
    Surface(modifier.heightIn(min=32.dp).semantics{contentDescription="$label ${if(pressed)"pressed"else"released"}"},
        color=if(pressed)color.copy(alpha=.25f)else Color(0xff13131f),shape=RoundedCornerShape(20.dp),border=BorderStroke(if(pressed)2.dp else 1.dp,if(label in listOf("A","B","X","Y")||pressed)color else Line)){
        Box(Modifier.padding(vertical=5.dp),contentAlignment=Alignment.Center){Text(label,color=if(pressed||label in listOf("A","B","X","Y"))color else Muted,fontSize=if(label.length>2)9.sp else 12.sp,fontWeight=FontWeight.Bold)}
    }
}
@Composable private fun StickCard(title:String,reading:StickReading,deadZone:Float,modifier:Modifier){
    SideEffect{TesterWork.hit(2)}
    DiagnosticCard(modifier){Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
        Text(title,fontWeight=FontWeight.SemiBold,fontSize=14.sp)
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.2f).semantics{contentDescription="$title X ${number(reading.rawX)} Y ${number(reading.rawY)}"}){
            val radius=size.minDimension*.44f;val middle=center
            drawCircle(Color(0xff0b0c15),radius,middle);drawCircle(Muted,radius,middle,style=Stroke(1.5.dp.toPx()))
            drawLine(Line,Offset(middle.x-radius,middle.y),Offset(middle.x+radius,middle.y),1.dp.toPx());drawLine(Line,Offset(middle.x,middle.y-radius),Offset(middle.x,middle.y+radius),1.dp.toPx())
            drawCircle(Violet.copy(alpha=.35f),radius*deadZone,middle,style=Stroke(1.dp.toPx()))
            drawCircle(Line,radius*.65f,middle,style=Stroke(1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(4f,4f))))
            val thumb=middle+Offset(reading.x*radius,reading.y*radius)
            drawCircle(Violet.copy(alpha=.15f),15.dp.toPx(),thumb);drawCircle(Brush.radialGradient(listOf(Color(0xffe7ccff),Color(0xff8b3cf0)),thumb,12.dp.toPx()),10.dp.toPx(),thumb)
            drawCircle(Color.White,10.dp.toPx(),thumb,style=Stroke(1.5.dp.toPx()))
        }
        Text("X ${number(reading.rawX)}  Y ${number(reading.rawY)}",fontSize=11.sp,color=Muted)
        Text("Norm ${number(reading.x.takeIf{reading.rawX!=null})} / ${number(reading.y.takeIf{reading.rawY!=null})}",fontSize=11.sp)
        Text("Dead zone ${number(deadZone)}",fontSize=11.sp,color=Muted)
        LinearProgressIndicator(progress={deadZone},Modifier.fillMaxWidth().height(5.dp),color=Violet,trackColor=Line)
        Text("Center offset ${number(reading.drift)} · ${reading.sectors}/8 sectors",fontSize=11.sp,color=Muted)
    }}
}
@Composable private fun TriggerCard(name:String,range:AxisRange?,raw:Float?,digital:Boolean,pressed:Boolean,modifier:Modifier){
    SideEffect{TesterWork.hit(3)}
    val code=range?.code;val pressure=raw?.let{range?.pressure(it)}
    DiagnosticCard(modifier){
        Row{Text("$name (Analog)",Modifier.weight(1f),fontSize=13.sp);Text(pressure?.let{"${(it*100).roundToInt()}%"}?:"—",fontWeight=FontWeight.Bold,fontSize=13.sp)}
        LinearProgressIndicator(progress={pressure?:0f},Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp)),color=Violet,trackColor=Line)
        Text(if(code==null)"Analog not available"else"Raw ${number(raw)} · ${range?.label}",color=Muted,fontSize=11.sp)
        Text("Digital: ${if(digital){if(pressed)"Pressed"else"Released"}else"Not available"}",color=if(pressed)Violet else Muted,fontSize=11.sp)
        Text(if(code!=null&&digital)"Axis + button supported"else if(code!=null)"Analog axis supported"else if(digital)"Digital button only"else"Not available",color=Muted,fontSize=10.sp)
    }
}
@Composable private fun Checklist(state:TesterState,onReset:()->Unit){
    val count=state.checks.count{it.passed};val total=state.checks.size
    DiagnosticCard{
        Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.FactCheck,null,tint=Muted);Spacer(Modifier.width(8.dp));Text("Test Checklist",Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=18.sp);Text("$count / $total (${if(total>0)100*count/total else 0}%)",color=Violet,fontSize=12.sp)}
        LinearProgressIndicator(progress={if(total>0)count.toFloat()/total else 0f},Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(8.dp)),color=Violet,trackColor=Line)
        if(total==0)Text("Connect a supported controller to discover applicable checks.",color=Muted,fontSize=12.sp)
        val columns=state.checks.chunked(((total+1)/2).coerceAtLeast(1)).takeIf{total>0}?:emptyList()
        if(total>0)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            for(column in columns)Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                for(item in column)Surface(color=Color(0xff1b1b2a),shape=RoundedCornerShape(6.dp),border=BorderStroke(1.dp,Line)){
                    Row(Modifier.fillMaxWidth().padding(5.dp).semantics{contentDescription="${item.label} ${if(item.passed)"passed"else"pending"}"},verticalAlignment=Alignment.CenterVertically){
                        Icon(if(item.passed)Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,null,Modifier.size(17.dp),tint=if(item.passed)Green else Muted)
                        Spacer(Modifier.width(5.dp));Text(item.label,fontSize=11.sp,lineHeight=14.sp)
                    }
                }
            }
        }
        OutlinedButton(onClick=onReset,Modifier.fillMaxWidth(),border=BorderStroke(1.dp,Violet)){Icon(Icons.Outlined.RestartAlt,null);Spacer(Modifier.width(8.dp));Text("Reset Test")}
        Text("Buttons require press + release. Full range requires 8 outer sectors and ±X/±Y travel. Tests reflect observed input only.",fontSize=11.sp,color=Muted)
    }
}
@Composable private fun EventLog(state:TesterState,onPause:()->Unit,onClear:()->Unit,onExport:()->Unit){
    DiagnosticCard{
        Row(verticalAlignment=Alignment.CenterVertically){Text("☷  Event Log (${if(state.paused)"Paused"else"Live"})",Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=17.sp);Text("${state.eventCount}/500",color=Muted,fontSize=11.sp);IconButton(onClick=onPause){Icon(if(state.paused)Icons.Outlined.PlayArrow else Icons.Outlined.Pause,if(state.paused)"Resume display"else"Pause display")}}
        Column(Modifier.fillMaxWidth().height(220.dp).background(Color(0xff0b0c14),RoundedCornerShape(8.dp)).verticalScroll(rememberScrollState()).padding(8.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
            if(state.events.isEmpty())Text("Awaiting observed Android input…",color=Muted,fontSize=11.sp)
            for(event in state.events.asReversed())Text(event.sanitized(),fontFamily=FontFamily.Monospace,fontSize=10.sp,lineHeight=14.sp,color=if(event.type=="AXIS")Color(0xff81eee2)else if(event.state=="UP")Color(0xffff8b9b)else Green)
        }
        Text("Android stream · no fabricated Linux codes\nRepeated downs ${state.duplicates} · queue overflows ${state.dropped}\nLinux SYN_DROPPED/recovery: unavailable in this stream",color=Muted,fontSize=11.sp)
        val times=state.receiptToProcessNs.sorted()
        if(times.isNotEmpty())Text("UI receipt → reducer p50/p95/p99: ${listOf(.5,.95,.99).joinToString(" / "){"%.2f".format(Locale.ROOT,times[((times.size-1)*it).toInt()]/1e6)}} ms · ${times.size} samples",fontSize=10.sp,color=Muted)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedButton(onClick=onClear,Modifier.weight(1f)){Text("Clear Log",fontSize=12.sp)}
            OutlinedButton(onClick=onExport,Modifier.weight(1f)){Text("Export",fontSize=12.sp)}
        }
        Text("Pause freezes display only. Capture and test progress continue. Export excludes descriptors and hardware identifiers.",fontSize=10.sp,color=Muted)
    }
}
@Composable private fun ControllerInfo(state:TesterState){
    DiagnosticCard{
        Row(verticalAlignment=Alignment.CenterVertically){Text("ⓘ  Controller Info",Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=18.sp);Badge(state.status)}
        var reveal by remember{mutableStateOf(false)}
        val device=state.device
        if(device==null)Text(state.lastConnection,color=Muted)else{
            for((name,value)in listOf("Name" to device.name,"VID / PID" to "0x%04X / 0x%04X".format(device.vendor,device.product),"Android ID" to device.id.toString(),"Transport" to device.transport,"Descriptor" to if(reveal)device.descriptor else "Hidden · tap Reveal descriptor","Mode" to "Unclassified · no mode inferred from name","Keys" to device.keys.joinToString(),"Connection" to state.lastConnection)){
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Text(name,Modifier.width(82.dp),color=Muted,fontSize=11.sp);Text(value,Modifier.weight(1f),fontSize=11.sp)}
                HorizontalDivider(color=Line.copy(alpha=.5f))
            }
            TextButton(onClick={reveal=!reveal}){Text(if(reveal)"Hide descriptor"else"Reveal descriptor",fontSize=11.sp)}
            Text("Observed Android motion ranges",fontWeight=FontWeight.SemiBold,fontSize=12.sp)
            for(range in device.ranges)Text("${range.label} (${range.code}): ${number(range.min)} … ${number(range.max)} · flat ${number(range.flat)}",fontSize=10.sp,color=Muted)
        }
        Text(state.safety,color=Muted,fontSize=11.sp)
    }
}

/** Original vector geometry, 500×300 units. No vendor image/logo or raster asset. */
@Composable fun ControllerDrawing(held:Set<String>,left:StickReading,right:StickReading){
    SideEffect{TesterWork.hit(1)}
    val labelPaint=remember{android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{textAlign=android.graphics.Paint.Align.CENTER;isFakeBoldText=true}}
    Canvas(Modifier.fillMaxWidth().aspectRatio(500f/300).semantics{contentDescription="Original G8 controller illustration. Pressed: ${held.joinToString().ifEmpty{"none"}}"}){
        TesterWork.section(4,"Tester.draw.controller"){scale(size.width/500,size.height/300,pivot=Offset.Zero){
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xff454551),Color(0xff22232e))),Offset(119f,64f),Size(262f,185f),CornerRadius(10f),style=Fill)
            drawRoundRect(Color(0xff181923),Offset(146f,71f),Size(208f,171f),CornerRadius(4f))
            for(x in 158..340 step 10)drawLine(Color(0xff242631),Offset(x.toFloat(),78f),Offset(x.toFloat(),235f),1f)
            val l=Path().apply{moveTo(138f,60f);cubicTo(83f,50f,37f,65f,27f,112f);cubicTo(14f,165f,4f,261f,39f,285f);cubicTo(69f,299f,106f,260f,126f,243f);lineTo(143f,242f);close()}
            val r=Path().apply{moveTo(362f,60f);cubicTo(417f,50f,463f,65f,473f,112f);cubicTo(486f,165f,496f,261f,461f,285f);cubicTo(431f,299f,394f,260f,374f,243f);lineTo(357f,242f);close()}
            for(path in listOf(l,r)){drawPath(path,Brush.linearGradient(listOf(Color(0xffb9bac8),Color(0xff696b7c),Color(0xffa2a4b3)),start=Offset(20f,60f),end=Offset(500f,300f)));drawPath(path,Color(0xffd4d5df),style=Stroke(1.2f))}
            fun text(label:String,x:Float,y:Float,color:Color=Color.White,font:Float=14f){drawContext.canvas.nativeCanvas.drawText(label,x,y,labelPaint.apply{this.color=color.toArgb();textSize=font})}
            fun button(label:String,x:Float,y:Float,radius:Float=15f){val active=label in held;val color=accent(label)
                if(active)drawCircle(color.copy(alpha=.2f),radius+6,Offset(x,y))
                drawCircle(if(active)color.copy(alpha=.5f)else Color(0xff14151e),radius,Offset(x,y));drawCircle(color,radius,Offset(x,y),style=Stroke(if(active)3f else 1.5f));text(when(label){"View"->"◧";"Menu"->"≡";"Home"->"○";else->label},x,y+5,color,if(label.length>2)12f else 15f)
            }
            for((label,x,y)in listOf(Triple("LT",80f,20f),Triple("LB",79f,47f),Triple("RT",420f,20f),Triple("RB",421f,47f))){
                val active=label in held;drawRoundRect(if(active)Violet.copy(alpha=.65f)else Color(0xff242131),Offset(x-32,y-13),Size(64f,25f),CornerRadius(11f));drawRoundRect(Violet,Offset(x-32,y-13),Size(64f,25f),CornerRadius(11f),style=Stroke(1.5f));text(label,x,y+5)
            }
            fun stick(x:Float,y:Float,reading:StickReading,click:String){drawCircle(Violet.copy(alpha=.25f),34f,Offset(x,y));drawCircle(Violet,29f,Offset(x,y),style=Stroke(2.5f));drawCircle(Color(0xff1b1b25),26f,Offset(x,y));val p=Offset(x+reading.x*7,y+reading.y*7);drawCircle(if(click in held)Violet else Color(0xff353644),21f,p);drawCircle(Color(0xff737181),21f,p,style=Stroke(1.5f));drawCircle(Color(0xff242631),16f,p,style=Stroke(1f))}
            stick(77f,118f,left,"L3");stick(413f,207f,right,"R3")
            for((label,x,y)in listOf(Triple("Y",434f,101f),Triple("X",405f,129f),Triple("B",463f,129f),Triple("A",434f,157f)))button(label,x,y)
            drawRoundRect(Color(0xff1b1c27),Offset(59f,175f),Size(37f,68f),CornerRadius(6f));drawRoundRect(Color(0xff1b1c27),Offset(43f,191f),Size(69f,36f),CornerRadius(6f))
            for((label,x,y)in listOf(Triple("↑",77f,185f),Triple("↓",77f,233f),Triple("←",52f,209f),Triple("→",102f,209f))){if(label in held)drawCircle(Violet.copy(alpha=.7f),12f,Offset(x,y));text(label,x,y+4,if(label in held)Color.White else Muted,13f)}
            button("View",119f,85f,10f);button("Menu",381f,85f,10f);button("Home",84f,260f,10f)
            text("MYTHICMAPPER",250f,163f,Color(0xff69677c),12f)
        }
    }}
}

@Composable private fun GuidedHardware(state:TesterState,onReset:()->Unit,onBenchmark:()->Unit,onAliases:()->Unit,onDisconnect:()->Unit){
    DiagnosticCard{
        Text("Guided hardware acceptance",fontWeight=FontWeight.Bold,fontSize=20.sp)
        Text("Evidence is retained separately from the rolling log. Press and release each pending control. Counts below come only from observed Android input.",color=Muted,fontSize=12.sp)
        for(check in state.checks){
            val proof=state.guided.controls.find{it.label==check.id}
            Text("${if(check.passed||proof?.pairs?.let{it>0}==true)"✓"else"○"} ${check.label}"+(proof?.let{" · ↓${it.downs} ↑${it.ups} paired ${it.pairs}"}?:""),color=if(check.passed)Green else Muted,fontSize=13.sp)
        }
        Text("Simultaneous buttons · hold together ≥50 ms, then release both",fontWeight=FontWeight.SemiBold)
        for(pair in listOf("A + B","LB + RB")){
            val supported=pair.split(" + ").all{it in (state.device?.keys?:emptySet())}
            if(supported){val proof=state.guided.overlaps.find{it.labels==pair};Text("${if(proof?.released==true)"✓"else"○"} $pair · ${proof?.durationMs?:0} ms observed",color=if(proof?.released==true)Green else Muted)}
        }
        OutlinedButton(onClick=onReset,Modifier.fillMaxWidth()){Text("Archive evidence & start new test")}
    }
    DiagnosticCard{
        Text("Rear L4 / R4 verification",fontWeight=FontWeight.Bold)
        Text("Follow the prompt one actuator at a time. L4/R4 have no invented separate Android identity. Identical reported codes cannot distinguish rear buttons from stick clicks.",color=Muted,fontSize=12.sp)
        if(state.guided.aliasPrompt.isNotBlank())Text(state.guided.aliasPrompt,color=Violet,fontWeight=FontWeight.Bold)
        for(report in state.guided.aliasReports)Text(report,fontSize=12.sp)
        OutlinedButton(onClick=onAliases){Text("Begin four-step alias check")}
    }
    DiagnosticCard{
        Text("Safe physical disconnect",fontWeight=FontWeight.Bold)
        Text("Arm first. Hold A and move a stick, physically unplug the G8 while held, then reconnect and release everything. The app never disconnects USB for you. Mapping stays stopped.",color=Muted,fontSize=12.sp)
        if(state.guided.armedDisconnect)Text("Armed · waiting for your physical unplug",color=Violet)
        for(test in state.guided.disconnects)Text("Held before: ${test.heldBefore.joinToString().ifEmpty{"none"}} · axis values ${test.axesBefore} · cleared ${test.cleared} · reconnected ${test.reconnected}",fontSize=12.sp)
        OutlinedButton(onClick=onDisconnect,enabled=state.status=="Connected"){Text("Arm disconnect test")}
    }
    DiagnosticCard{
        Text("Repeatable physical benchmark",fontWeight=FontWeight.Bold)
        Text("Warm both sticks for five seconds, press A to start. 10s both-stick circles; 10s alternating full LT/RT; 5s A+B plus circles; 5s all released. Repeat this exact protocol for before/after builds.",fontSize=12.sp,color=Muted)
        OutlinedButton(onClick=onBenchmark,enabled=state.status=="Connected"){Text("Arm 30-second benchmark")}
    }
}
