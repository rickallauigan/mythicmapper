// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.tester

import kotlin.math.*

@androidx.compose.runtime.Immutable
data class AxisRange(val code:Int,val label:String,val min:Float,val max:Float,val flat:Float=0f) {
    fun signed(raw:Float):Float = if(max>min) ((raw-(min+max)/2)/((max-min)/2)).coerceIn(-1f,1f) else 0f
    fun pressure(raw:Float):Float = if(max>min) ((raw-min)/(max-min)).coerceIn(0f,1f) else 0f
}
@androidx.compose.runtime.Immutable
data class TesterDevice(val id:Int,val name:String,val vendor:Int,val product:Int,val descriptor:String,
    val transport:String,val supported:Boolean,val keys:Set<String>,val ranges:List<AxisRange>,
    val left:Pair<Int,Int>?,val right:Pair<Int,Int>?,val lt:Int?,val rt:Int?)
@androidx.compose.runtime.Immutable
data class Thresholds(val deadZone:Float=.15f,val direction:Float=.65f,val travel:Float=.9f,val triggerRest:Float=.1f)
@androidx.compose.runtime.Immutable
data class TesterEvent(val elapsedMs:Long,val type:String,val code:Int,val label:String,val raw:Float,val normalized:Float?,val state:String) {
    fun sanitized()="${elapsedMs}ms Android $type code=$code $label raw=$raw ${normalized?.let{"norm=%.3f".format(java.util.Locale.ROOT,it)} ?: ""} $state"
}
@androidx.compose.runtime.Immutable
data class StickReading(val rawX:Float?,val rawY:Float?,val x:Float,val y:Float,val drift:Float?,val sectors:Int)
@androidx.compose.runtime.Immutable
data class CheckItem(val id:String,val label:String,val passed:Boolean)
@androidx.compose.runtime.Immutable
data class TesterState(val device:TesterDevice?=null,val status:String="Disconnected",val safety:String="Acquiring exclusive diagnostic mode…",
    val held:Set<String> = emptySet(),val raw:Map<Int,Float> = emptyMap(),val checks:List<CheckItem> = emptyList(),
    val events:List<TesterEvent> = emptyList(),val eventCount:Int=0,val paused:Boolean=false,val duplicates:Int=0,val dropped:Int=0,
    val lastConnection:String="Waiting for controller",val thresholds:Thresholds=Thresholds(),val left:StickReading=StickReading(null,null,0f,0f,null,0),
    val right:StickReading=StickReading(null,null,0f,0f,null,0),val receiptToProcessNs:List<Long> = emptyList(),val totalEvents:Long=0)

/** Worker-confined reducer. Android codes are retained verbatim; no evdev translation. */
class TesterModel {
    var device:TesterDevice?=null;private set
    var thresholds=Thresholds();private set
    private var lastDescriptor:String?=null
    private val held=mutableSetOf<String>();private val pressed=mutableSetOf<String>();private val passed=mutableSetOf<String>()
    private val raw=mutableMapOf<Int,Float>();private val sectors=mutableMapOf<String,MutableSet<Int>>()
    private val directions=mutableMapOf<String,MutableSet<String>>();private val extents=mutableMapOf<String,FloatArray>()
    private val drift=mutableMapOf<String,Float>();private val triggerMax=mutableMapOf<String,Float>();private val triggerMin=mutableMapOf<String,Float>()
    private var received=0L
    private val history=ArrayDeque<TesterEvent>();private var frozen:List<TesterEvent>?=null
    private val timing=ArrayDeque<Long>();private var connection="Waiting for controller"
    var safety="Acquiring exclusive diagnostic mode…";var ready=false
    var duplicates=0;private set
    var dropped=0;private set
    fun connect(next:TesterDevice?,at:Long) {
        if(next==device)return
        val connectionChanged=next?.id!=device?.id||next?.descriptor!=device?.descriptor
        val controlsChanged=next?.copy(transport="")!=device?.copy(transport="")
        val identityChanged=next!=null&&next.descriptor!=lastDescriptor
        if(next!=null)lastDescriptor=next.descriptor
        device=next
        if(controlsChanged){held.clear();raw.clear();pressed.clear()}
        if(identityChanged) reset()
        if(connectionChanged)connection="${at}ms · ${if(next==null)"Disconnected"else"Connected"} (observed)"
    }
    fun reset(){passed.clear();pressed.clear();sectors.clear();directions.clear();extents.clear();triggerMin.clear();triggerMax.clear();drift.clear()}
    fun configure(next:Thresholds){thresholds=next;reset()}
    fun measureCenter(){for((name,pair) in listOf("L" to device?.left,"R" to device?.right))if(pair!=null){
        val x=normalized(pair.first);val y=normalized(pair.second)
        if(raw.containsKey(pair.first)&&raw.containsKey(pair.second))drift[name]=hypot(x,y)
    }}
    fun clearLog(){history.clear();if(frozen!=null)frozen=emptyList()}
    fun pauseLog(){frozen=if(frozen==null)history.takeLast(60)else null}
    fun overflow(){dropped++;held.clear();pressed.clear();raw.clear()}
    fun clearHeld(){held.clear();pressed.clear()}
    fun clearTransient(){clearHeld();raw.clear()}
    fun recordTiming(ns:Long){if(timing.size==512)timing.removeFirst();timing.addLast(ns.coerceAtLeast(0))}
    fun key(label:String,code:Int,down:Boolean,repeat:Int,at:Long){
        if(!ready || device?.supported!=true || label !in device!!.keys)return
        if(down){if(!held.add(label)&&repeat==0)duplicates++;pressed.add(label)}
        else {held.remove(label);if(pressed.remove(label))passed.add(label)}
        log(TesterEvent(at,"KEY_ACTION",code,label,if(down)0f else 1f,null,if(down)"DOWN${if(repeat>0)" repeat=$repeat"else""}"else"UP"))
    }
    fun axes(values:Map<Int,Float>,at:Long){
        if(!ready || device?.supported!=true)return
        for((code,value)in values){if(!value.isFinite())continue;val range=device!!.ranges.find{it.code==code} ?: continue
            if(raw[code]==value)continue
            raw[code]=value
            log(TesterEvent(at,"AXIS",code,range.label,value,if(range.label in listOf("LTRIGGER","RTRIGGER","BRAKE","GAS"))range.pressure(value)else range.signed(value),"MOVE"))
        }
        for((name,pair)in listOf("L" to device!!.left,"R" to device!!.right))if(pair!=null&&raw.containsKey(pair.first)&&raw.containsKey(pair.second)){
            val x=normalized(pair.first);val y=normalized(pair.second);val radius=hypot(x,y)
            val dirs=directions.getOrPut(name){mutableSetOf()}
            if(x>=thresholds.direction)dirs.add("E");if(x<=-thresholds.direction)dirs.add("W")
            if(y>=thresholds.direction)dirs.add("S");if(y<=-thresholds.direction)dirs.add("N")
            if(dirs.size==4)passed.add("$name-dir")
            val bounds=extents.getOrPut(name){floatArrayOf(0f,0f,0f,0f)}
            bounds[0]=min(bounds[0],x);bounds[1]=max(bounds[1],x);bounds[2]=min(bounds[2],y);bounds[3]=max(bounds[3],y)
            if(radius>=thresholds.travel)sectors.getOrPut(name){mutableSetOf()}.add(((atan2(y,x)+2*PI+PI/8)/(PI/4)).toInt()%8)
            if(sectors[name]?.size==8&&bounds[0]<=-thresholds.travel&&bounds[1]>=thresholds.travel&&bounds[2]<=-thresholds.travel&&bounds[3]>=thresholds.travel)passed.add("$name-range")
        }
        for((name,code)in listOf("LT" to device!!.lt,"RT" to device!!.rt))if(code!=null&&raw.containsKey(code)){
            val v=pressure(code)!!;triggerMax[name]=max(triggerMax[name]?:0f,v);triggerMin[name]=min(triggerMin[name]?:1f,v)
            if(triggerMax[name]!!>=thresholds.travel&&triggerMin[name]!!<=thresholds.triggerRest)passed.add("$name-range")
            if(name !in device!!.keys){if(v>=.5f){held.add(name);pressed.add(name)}else if(v<=thresholds.triggerRest){held.remove(name);if(pressed.remove(name))passed.add(name)}}
        }
        for(range in device!!.ranges.filter{it.label=="HAT_X"||it.label=="HAT_Y"})if(raw.containsKey(range.code)){
            val negative=if(range.label=="HAT_X")"←"else"↑";val positive=if(range.label=="HAT_X")"→"else"↓"
            for((name,active)in listOf(negative to (range.signed(raw[range.code]!!)<-.5f),positive to (range.signed(raw[range.code]!!)>.5f)))if(name !in device!!.keys){
                if(active){held.add(name);pressed.add(name)}else {held.remove(name);if(pressed.remove(name))passed.add(name)}
            }
        }
    }
    private fun log(event:TesterEvent){received++;if(history.size==500)history.removeFirst();history.addLast(event)}
    private fun normalized(code:Int)=raw[code]?.let{v->device?.ranges?.find{it.code==code}?.signed(v)}?:0f
    fun pressure(code:Int?)=code?.let{c->raw[c]?.let{v->device?.ranges?.find{it.code==c}?.pressure(v)}}
    private fun stick(name:String,pair:Pair<Int,Int>?):StickReading=if(pair==null)StickReading(null,null,0f,0f,null,0)else
        StickReading(raw[pair.first],raw[pair.second],normalized(pair.first),normalized(pair.second),drift[name],sectors[name]?.size?:0)
    fun export()=history.joinToString("\n"){it.sanitized()}
    fun snapshot():TesterState {
        val d=device;val checks=mutableListOf<CheckItem>()
        if(d?.supported==true){
            val buttons=d.keys.toMutableSet()
            if(d.lt!=null)buttons.add("LT");if(d.rt!=null)buttons.add("RT")
            if(d.ranges.any{it.label=="HAT_X"})buttons.addAll(listOf("←","→"))
            if(d.ranges.any{it.label=="HAT_Y"})buttons.addAll(listOf("↑","↓"))
            for(b in listOf("A","B","X","Y","LB","RB","LT","RT","L3","R3","↑","↓","←","→","View","Menu","Home"))if(b in buttons)checks.add(CheckItem(b,b,b in passed))
            for((name,pair)in listOf("L" to d.left,"R" to d.right))if(pair!=null){checks.add(CheckItem("$name-dir","$name stick · all directions","$name-dir" in passed));checks.add(CheckItem("$name-range","$name stick · full range","$name-range" in passed))}
            for((name,code)in listOf("LT" to d.lt,"RT" to d.rt))if(code!=null)checks.add(CheckItem("$name-range","$name analog range","$name-range" in passed))
        }
        return TesterState(d,when{d==null->"Disconnected";!d.supported->"Unsupported";!ready->"Input unavailable";else->"Connected"},safety,
            held.toSet(),raw.toMap(),checks,frozen?:history.takeLast(60),history.size,frozen!=null,duplicates,dropped,connection,thresholds,
            stick("L",d?.left),stick("R",d?.right),timing.toList(),received)
    }
}
