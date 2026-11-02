// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.tester

import org.json.JSONArray
import org.json.JSONObject

@androidx.compose.runtime.Immutable
data class InputProof(val at:Long,val source:String,val code:Int,val raw:Float,val down:Boolean)
@androidx.compose.runtime.Immutable
data class ControlProof(val label:String,val downs:Int,val ups:Int,val pairs:Int,val samples:List<InputProof>)
@androidx.compose.runtime.Immutable
data class OverlapProof(val labels:String,val durationMs:Long,val released:Boolean)
@androidx.compose.runtime.Immutable
data class DisconnectProof(val at:Long,val heldBefore:List<String>,val axesBefore:Int,val cleared:Boolean,val reconnected:Boolean=false)
@androidx.compose.runtime.Immutable
data class GuidedEvidence(val controls:List<ControlProof> = emptyList(),val overlaps:List<OverlapProof> = emptyList(),
    val disconnects:List<DisconnectProof> = emptyList(),val armedDisconnect:Boolean=false,val aliasPrompt:String="",val aliasReports:List<String> = emptyList())

/** Separate from the evicting display log. Only observed transitions may complete evidence. */
class HardwareEvidence {
    private val controls=linkedMapOf<String,ControlProof>()
    private val overlaps=linkedMapOf<String,OverlapProof>()
    private val active=mutableMapOf<String,Long>()
    private val disconnects=ArrayDeque<DisconnectProof>()
    private var disconnectArmed=false
    private var awaitingReconnect=false
    private var aliasIndex=-1
    private val aliases=mutableListOf<String>()
    var revision=0L;private set
    private val aliasSteps=listOf("Press and release L3 (stick click)","Press and release rear L4","Press and release R3 (stick click)","Press and release rear R4")
    fun record(label:String,source:String,code:Int,raw:Float,down:Boolean,paired:Boolean,at:Long){
        val old=controls[label]?:ControlProof(label,0,0,0,emptyList())
        controls[label]=old.copy(downs=old.downs+if(down)1 else 0,ups=old.ups+if(down)0 else 1,
            pairs=old.pairs+if(paired)1 else 0,samples=(old.samples+InputProof(at,source,code,raw,down)).takeLast(8))
        if(!down&&paired&&aliasIndex in aliasSteps.indices){
            aliases.add("${aliasSteps[aliasIndex]} → observed $label ($source code $code); physical actuator attributed by prompt")
            aliasIndex++;if(aliasIndex==aliasSteps.size)aliasIndex=-1
        }
        revision++
    }
    fun observeHeld(held:Set<String>,supported:Set<String>,at:Long){
        for(pair in listOf(listOf("A","B"),listOf("LB","RB"))){
            if(!supported.containsAll(pair))continue
            val name=pair.joinToString(" + ")
            if(held.containsAll(pair))active.putIfAbsent(name,at)
            else active.remove(name)?.let{start->
                val elapsed=(at-start).coerceAtLeast(0)
                if(elapsed>=50){overlaps[name]=OverlapProof(name,elapsed,false);revision++}
            }
            if(held.none{it in pair})overlaps[name]?.let{if(!it.released){overlaps[name]=it.copy(released=true);revision++}}
        }
    }
    fun interrupt(){active.clear();if(overlaps.entries.removeAll{!it.value.released})revision++}
    fun startAliases(){aliasIndex=0;aliases.clear();revision++}
    fun armDisconnect(){disconnectArmed=true;awaitingReconnect=false;revision++}
    fun disconnected(held:Set<String>,axes:Int,at:Long,cleared:Boolean){
        interrupt()
        if(!disconnectArmed)return
        if(disconnects.size==16)disconnects.removeFirst()
        disconnects.addLast(DisconnectProof(at,held.sorted(),axes,cleared));disconnectArmed=false;awaitingReconnect=true;revision++
    }
    fun reconnected(){if(awaitingReconnect&&disconnects.isNotEmpty()){val last=disconnects.removeLast();disconnects.addLast(last.copy(reconnected=true));awaitingReconnect=false;revision++}}
    fun clear(){controls.clear();overlaps.clear();active.clear();disconnects.clear();disconnectArmed=false;awaitingReconnect=false;aliases.clear();aliasIndex=-1;revision++}
    fun snapshot()=GuidedEvidence(controls.values.toList(),overlaps.values.toList(),disconnects.toList(),disconnectArmed,aliasSteps.getOrNull(aliasIndex)?:"",aliases.toList())
    fun encode():JSONObject {
        val s=snapshot()
        return JSONObject().put("schema",1).put("source","Observed Android events; no independent rear identities")
            .put("controls",JSONArray(s.controls.map{c->JSONObject().put("label",c.label).put("downs",c.downs).put("ups",c.ups).put("pairs",c.pairs)
                .put("samples",JSONArray(c.samples.map{p->JSONObject().put("uptime_ms",p.at).put("source",p.source).put("code",p.code).put("raw",p.raw).put("down",p.down)}))}))
            .put("overlaps",JSONArray(s.overlaps.map{JSONObject().put("labels",it.labels).put("duration_ms",it.durationMs).put("released",it.released)}))
            .put("disconnects",JSONArray(s.disconnects.map{JSONObject().put("uptime_ms",it.at).put("held_before",JSONArray(it.heldBefore)).put("axis_values_before",it.axesBefore).put("cleared",it.cleared).put("reconnected",it.reconnected)}))
            .put("aliases",JSONArray(s.aliasReports))
    }
    fun restore(json:JSONObject){
        require(json.getInt("schema")==1)
        val restored=HardwareEvidence()
        val rows=json.getJSONArray("controls");require(rows.length()<=24)
        repeat(rows.length()){i->val c=rows.getJSONObject(i);val label=c.getString("label");require(label.length<=16)
            val samples=c.getJSONArray("samples");require(samples.length()<=8)
            val proofs=(0 until samples.length()).map{j->val p=samples.getJSONObject(j);InputProof(p.getLong("uptime_ms"),p.getString("source"),p.getInt("code"),p.getDouble("raw").toFloat(),p.getBoolean("down"))}
            val down=c.getInt("downs");val up=c.getInt("ups");val pairs=c.getInt("pairs");require(down>=0&&up>=0&&pairs in 0..minOf(down,up))
            restored.controls[label]=ControlProof(label,down,up,pairs,proofs)
        }
        val combo=json.getJSONArray("overlaps");require(combo.length()<=2)
        repeat(combo.length()){val p=combo.getJSONObject(it);val key=p.getString("labels");require(key in listOf("A + B","LB + RB"));restored.overlaps[key]=OverlapProof(key,p.getLong("duration_ms"),p.getBoolean("released"))}
        val loss=json.getJSONArray("disconnects");require(loss.length()<=16)
        repeat(loss.length()){val p=loss.getJSONObject(it);val held=p.getJSONArray("held_before");require(held.length()<=24);restored.disconnects.addLast(DisconnectProof(p.getLong("uptime_ms"),(0 until held.length()).map{j->held.getString(j)},p.getInt("axis_values_before"),p.getBoolean("cleared"),p.getBoolean("reconnected")))}
        val a=json.getJSONArray("aliases");require(a.length()<=4);repeat(a.length()){restored.aliases.add(a.getString(it))}
        clear();controls.putAll(restored.controls);overlaps.putAll(restored.overlaps);disconnects.addAll(restored.disconnects);aliases.addAll(restored.aliases);revision++
    }
}
