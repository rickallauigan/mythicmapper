// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.universal

/** Existing-editor style immutable snapshots; bounded undo/redo and no storage writes on edit. */
class UniversalDraft(val original:UniversalMapping) {
    var value=original;private set
    private val back=ArrayDeque<UniversalMapping>();private val forward=ArrayDeque<UniversalMapping>()
    private var gesture:UniversalMapping?=null
    fun beginGesture(){if(gesture==null)gesture=value}
    fun endGesture(){val before=gesture?:return;val next=value;gesture=null;value=before;change(next)}
    fun cancelGesture(){gesture?.let{value=it};gesture=null}
    val dirty get()=value!=original
    val canUndo get()=back.isNotEmpty();val canRedo get()=forward.isNotEmpty()
    fun change(next:UniversalMapping){next.validate();if(next==value)return;if(gesture!=null){value=next;return};if(back.size==32)back.removeFirst();back.addLast(value);forward.clear();value=next}
    fun action(next:UniversalAction){change(value.edited(value.actions.filterNot{it.id==next.id}+next))}
    fun remove(id:String){change(value.edited(value.actions.filterNot{it.id==id}))}
    fun undo(){if(back.isNotEmpty()){forward.addLast(value);value=back.removeLast()}}
    fun redo(){if(forward.isNotEmpty()){back.addLast(value);value=forward.removeLast()}}
    fun reset(){change(original)}
    fun snapshot():String {endGesture();return org.json.JSONObject().put("original",UniversalCodec.encode(original)).put("value",UniversalCodec.encode(value))
        .put("undo",org.json.JSONArray(back.map(UniversalCodec::encode))).put("redo",org.json.JSONArray(forward.map(UniversalCodec::encode))).toString()}
    companion object {
        fun restore(text:String):UniversalDraft {
            require(text.toByteArray().size<=262144);val r=org.json.JSONObject(text);val d=UniversalDraft(UniversalCodec.decode(r.getString("original")));d.value=UniversalCodec.decode(r.getString("value"))
            require(d.value.id==d.original.id)
            for((key,list)in listOf("undo" to d.back,"redo" to d.forward)){
                val a=r.getJSONArray(key);require(a.length()<=32);for(i in 0 until a.length()){val v=UniversalCodec.decode(a.getString(i));require(v.id==d.original.id);list.addLast(v)}
            };return d
        }
        fun starter(name:String,hero:String="")=UniversalMapping(name=name,hero=hero,origin="starter:unmeasured-six-controls",actions=listOf(
            "BTN_GAMEPAD" to "Regen","BTN_EAST" to "Battle Spell","BTN_TR" to "Skill 1","BTN_TR2" to "Skill 2","BTN_TL" to "Ultimate","BTN_TL2" to "Attack"
        ).map{(input,label)->UniversalAction(name=label,inputs=setOf(input),kind=if(input in setOf("BTN_GAMEPAD","BTN_EAST"))TouchAction.TAP else TouchAction.HOLD,aim=input=="BTN_TL2")})
    }
}

/** Letterboxed canonical plane with explicit pan/zoom; outside taps never invent anchors. */
data class UniversalViewport(val viewportWidth:Float,val viewportHeight:Float,val geometry:MappingGeometry,val zoom:Float=1f,val panX:Float=0f,val panY:Float=0f) {
    init{require(viewportWidth.isFinite()&&viewportWidth>0&&viewportHeight.isFinite()&&viewportHeight>0&&zoom.isFinite()&&zoom in 1f..4f&&panX.isFinite()&&panY.isFinite());geometry.validate()}
    val scale=minOf(viewportWidth/geometry.width,viewportHeight/geometry.height)*zoom
    val left get()=(viewportWidth-geometry.width*scale)/2+panX
    val top get()=(viewportHeight-geometry.height*scale)/2+panY
    fun screen(x:Float,y:Float)=androidx.compose.ui.geometry.Offset(left+x*scale,top+y*scale)
    fun canonical(x:Float,y:Float):Pair<Float,Float>? {
        if(!x.isFinite()||!y.isFinite())return null
        val px=(x-left)/scale;val py=(y-top)/scale
        return if(px>=0&&px<geometry.width&&py>=0&&py<geometry.height)px to py else null
    }
}
