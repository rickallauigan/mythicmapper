// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.universal

import org.json.JSONObject
import xtr.keymapper.controller.ControllerBindings
import xtr.keymapper.mythic.MythicScope
import xtr.keymapper.structured.ProfileCodec
import java.util.UUID
import kotlin.math.abs

/** Canonical pixels in an explicitly saved landscape reference plane; never device/HUD guesses. */
data class MappingGeometry(val width:Int=2280,val height:Int=1080) {
    fun validate(){require(width in 2..32768&&height in 2 until width){"Choose a valid landscape reference geometry"}}
    fun fits(width:Float,height:Float):Boolean = width.isFinite()&&height.isFinite()&&width>height&&height>0&&abs(width/height-this.width.toFloat()/this.height)/(this.width.toFloat()/this.height)<.01f
}
enum class TouchAction { TAP, HOLD, REPEAT, RESET }
/** Multiple inputs in one action share first-DOWN/last-UP ownership of exactly one target. */
data class UniversalAction(val id:String=UUID.randomUUID().toString(),val name:String="",val inputs:Set<String>,
    val kind:TouchAction=TouchAction.HOLD,val x:Float?=null,val y:Float?=null,val enabled:Boolean=true,
    val aim:Boolean=false,val radius:Float=80f,val deadZone:Float=.15f,val sensitivity:Float=1f,
    val invertY:Boolean=false,val repeatMs:Int=400,val priority:Int=0,val exclusiveAim:Boolean=true,val anchorMs:Int=0,val cancelOthers:Boolean=false) {
    fun validate(g:MappingGeometry) {
        require(UUID.fromString(id).toString()==id){"Invalid action UUID"};text(name,80,true)
        require(inputs.isNotEmpty()&&inputs.size<=16&&inputs.all{it in buttons||it==RIGHT_STICK}){"Unsupported input"}
        require((x==null)==(y==null)){"Both coordinates are required"}
        x?.let{require(it.isFinite()&&it>=0&&it<g.width&&y!!.isFinite()&&y>=0&&y<g.height){"Anchor outside reference geometry"}}
        require(radius.isFinite()&&radius>0&&radius<=minOf(g.width,g.height)/2f){"Invalid aim radius"}
        require(deadZone.isFinite()&&deadZone in 0f..0.9f&&sensitivity.isFinite()&&sensitivity in .1f..4f){"Invalid stick tuning"}
        require(repeatMs in 60..60000&&anchorMs in 0..250&&anchorMs*2<repeatMs){"Invalid repeat/acquisition timing"}
        require(priority in 0..100){"Invalid priority"}
        require(RIGHT_STICK !in inputs||(aim&&kind==TouchAction.HOLD&&inputs.size==1)){"Stick activation supports continuous directional hold only"}
        require(!cancelOthers||RIGHT_STICK !in inputs){"Stick activation uses priority rather than global cancellation"}
        require(!aim||kind in setOf(TouchAction.HOLD,TouchAction.REPEAT)){"Only held/repeated contacts accept directional aim"}
        require(kind!=TouchAction.RESET||(!aim&&x==null)){"Reset has no touch target"}
    }
    companion object {
        const val RIGHT_STICK="RIGHT_STICK"
        val buttons:Set<String> = ControllerBindings.codes().filter{it!="BTN_MODE"}.sortedBy{ControllerBindings.label(it)}.toCollection(linkedSetOf())
        internal fun text(s:String,max:Int,empty:Boolean=false){require(s.length<=max&&(empty||s.isNotBlank())&&s.none{it.isISOControl()}){"Invalid name/metadata"}}
    }
}

data class UniversalMapping(val id:String=UUID.randomUUID().toString(),val name:String,val hero:String="",
    val origin:String="blank",val geometry:MappingGeometry=MappingGeometry(),val calibrated:Boolean=false,
    val enabled:Boolean=false,val actions:List<UniversalAction> = emptyList(),val createdAt:Long=System.currentTimeMillis(),
    val updatedAt:Long=createdAt,val game:String=MythicScope.GAME,val controller:String="ORIGINAL_G8",val schema:Int=2) {
    fun validate():UniversalMapping {
        require(schema==2){"Unsupported universal schema; nothing overwritten"};require(UUID.fromString(id).toString()==id){"Invalid personal UUID"}
        UniversalAction.text(name,80);UniversalAction.text(hero,80,true);UniversalAction.text(origin,120)
        require(game==MythicScope.GAME&&controller=="ORIGINAL_G8"){"Unsupported game/controller"};geometry.validate()
        require(createdAt>=0&&updatedAt>=createdAt){"Invalid timestamps"}
        require(actions.size<=8&&actions.map{it.id}.distinct().size==actions.size){"At most eight uniquely owned targets"}
        actions.forEach{it.validate(geometry)}
        val points=actions.filter{it.x!=null&&it.kind!=TouchAction.RESET}.map{it.x to it.y}
        require(points.distinct().size==points.size){"Duplicate anchors need shared input ownership on one target"}
        val inputs=actions.flatMap{it.inputs};require(inputs.distinct().size==inputs.size){"An input cannot own conflicting actions; add aliases to one target"}
        if(calibrated)require(actions.any{it.enabled&&it.kind!=TouchAction.RESET}&&actions.filter{it.enabled&&it.kind!=TouchAction.RESET}.all{it.x!=null}){"Measure every enabled touch target before confirming HUD calibration"}
        return this
    }
    fun ready(){validate();require(enabled&&calibrated){"Enable and explicitly confirm this HUD before activation"}}
    fun edited(next:List<UniversalAction>)=copy(actions=next,calibrated=false,enabled=false,updatedAt=maxOf(createdAt,System.currentTimeMillis())).validate()
    fun cloned(name:String)=copy(id=UUID.randomUUID().toString(),name=name,origin="clone:$id",calibrated=false,enabled=false,createdAt=System.currentTimeMillis(),updatedAt=System.currentTimeMillis()).validate()
}

/** Shares the existing bounded strict JSON parser; no executable actions or raw-line escape hatch. */
object UniversalCodec {
    fun encode(d:UniversalMapping):String {
        d.validate()
        return JSONObject(mapOf("schema_version" to d.schema,"id" to d.id,"name" to d.name,"hero" to d.hero,"origin" to d.origin,
            "game" to d.game,"controller" to d.controller,"created_at" to d.createdAt,"updated_at" to d.updatedAt,
            "geometry" to mapOf("width" to d.geometry.width,"height" to d.geometry.height,"orientation" to "LANDSCAPE","coordinates" to "CANONICAL_PIXELS"),
            "calibrated" to d.calibrated,"enabled" to d.enabled,"movement" to "NATIVE_PASSTHROUGH",
            "actions" to d.actions.map{a->mapOf("id" to a.id,"name" to a.name,"inputs" to a.inputs.sorted(),"kind" to a.kind.name,
                "x" to (a.x?:JSONObject.NULL),"y" to (a.y?:JSONObject.NULL),"enabled" to a.enabled,"aim" to a.aim,
                "radius" to a.radius,"dead_zone" to a.deadZone,"sensitivity" to a.sensitivity,"invert_y" to a.invertY,
                "repeat_ms" to a.repeatMs,"priority" to a.priority,"exclusive_aim" to a.exclusiveAim,"anchor_ms" to a.anchorMs,"cancel_others" to a.cancelOthers)})).toString()
    }
    fun decode(text:String):UniversalMapping {
        @Suppress("UNCHECKED_CAST") fun obj(v:Any?):Map<String,Any?> {require(v is Map<*,*>);return v as Map<String,Any?>}
        fun exact(m:Map<String,Any?>,keys:String){require(m.keys==keys.split(' ').toSet()){"Unknown/missing universal fields"}}
        fun s(m:Map<String,Any?>,k:String)=m[k] as? String?:error("$k must be text")
        fun n(m:Map<String,Any?>,k:String)=(m[k]as? Number)?.toDouble()?.also{require(it.isFinite())}?:error("$k must be finite")
        fun i(m:Map<String,Any?>,k:String)=n(m,k).let{require(it==it.toInt().toDouble());it.toInt()}
        fun l(m:Map<String,Any?>,k:String)=n(m,k).let{require(it>=0&&it<=9_007_199_254_740_991.0&&it==it.toLong().toDouble());it.toLong()}
        fun b(m:Map<String,Any?>,k:String)=m[k] as? Boolean?:error("$k must be boolean")
        val r=obj(ProfileCodec.strictJsonValue(text));exact(r,"schema_version id name hero origin game controller created_at updated_at geometry calibrated enabled movement actions")
        require(i(r,"schema_version")==2);require(s(r,"movement")=="NATIVE_PASSTHROUGH")
        val g=obj(r["geometry"]);exact(g,"width height orientation coordinates");require(s(g,"orientation")=="LANDSCAPE"&&s(g,"coordinates")=="CANONICAL_PIXELS")
        val list=r["actions"]as?List<*>?:error("Actions must be an array");require(list.size<=8)
        val actions=list.map{v->val a=obj(v);exact(a,"id name inputs kind x y enabled aim radius dead_zone sensitivity invert_y repeat_ms priority exclusive_aim anchor_ms cancel_others")
            val input=a["inputs"]as?List<*>?:error("Inputs must be an array");require(input.all{it is String}&&input.distinct().size==input.size)
            UniversalAction(s(a,"id"),s(a,"name"),input.map{it as String}.toSet(),TouchAction.valueOf(s(a,"kind")),
                if(a["x"]==null)null else n(a,"x").toFloat(),if(a["y"]==null)null else n(a,"y").toFloat(),b(a,"enabled"),b(a,"aim"),n(a,"radius").toFloat(),n(a,"dead_zone").toFloat(),n(a,"sensitivity").toFloat(),b(a,"invert_y"),i(a,"repeat_ms"),i(a,"priority"),b(a,"exclusive_aim"),i(a,"anchor_ms"),b(a,"cancel_others"))}
        return UniversalMapping(s(r,"id"),s(r,"name"),s(r,"hero"),s(r,"origin"),MappingGeometry(i(g,"width"),i(g,"height")),b(r,"calibrated"),b(r,"enabled"),actions,l(r,"created_at"),l(r,"updated_at"),s(r,"game"),s(r,"controller"),i(r,"schema_version")).validate()
    }
}
