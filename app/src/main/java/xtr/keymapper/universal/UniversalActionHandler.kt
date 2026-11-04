// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.universal

import android.os.Handler
import xtr.keymapper.controller.ControllerAimHandler
import xtr.keymapper.keymap.element.AimKey
import xtr.keymapper.keymap.element.Key
import xtr.keymapper.mouse.HeldAimHandler.TouchSink
import xtr.keymapper.server.event.RepeatKeyHandler
import kotlin.math.hypot

/** Action resolution only: existing aim/repeat producers feed the existing caller's touch ledger. */
class UniversalActionHandler(mapping:UniversalMapping,private val handler:Handler,sink:TouchSink,width:Float,height:Float):AutoCloseable {
    private val actions=mapping.actions.filter{it.enabled}
    private val targets=actions.filter{it.kind!=TouchAction.RESET}
    private val owners=linkedMapOf<String,MutableSet<String>>()
    private val pressed=mutableSetOf<String>()
    private val blocked=mutableSetOf<String>()
    private val applied=mutableSetOf<String>()
    private val tapTasks=mutableMapOf<String,Runnable>()
    private val order=mutableMapOf<String,Long>()
    private var serial=0L
    private var running=true
    private var neutral=false
    private var x=0f;private var y=0f
    private val aim:ControllerAimHandler
    private val repeat:RepeatKeyHandler
    private val keys:Map<String,Key>
    var lastCancellation:String?=null;private set
    val ownerCount get()=owners.values.sumOf{it.size}
    init {
        mapping.ready();require(mapping.geometry.fits(width,height)){"Display aspect/orientation changed; recalibrate before mapping"}
        val sx=width/mapping.geometry.width;val sy=height/mapping.geometry.height
        val configs=targets.map{a->AimKey(arrayOf("STICK_AIM",a.id,(requireNotNull(a.x)*sx).toString(),(requireNotNull(a.y)*sy).toString(),
            (a.radius*minOf(sx,sy)).toString(),(if(a.aim)a.sensitivity else 0f).toString(),(if(a.aim)a.sensitivity else 0f).toString(),a.deadZone.toString(),if(a.invertY)"1"else"0"))}
        aim=ControllerAimHandler(configs,sink,width,height)
        val scheduler=object:ControllerAimHandler.AnchorScheduler{
            override fun after(task:Runnable,delayMs:Int){handler.postDelayed(task,delayMs.toLong())}
            override fun cancel(task:Runnable){handler.removeCallbacks(task)}
        }
        repeat=RepeatKeyHandler(handler,{_,_,action,pointer->
            if(running){val a=targets[pointer-ControllerAimHandler.FIRST_POINTER_ID]
                if(action==1&&a.aim&&a.anchorMs>0)aim.triggerAfterAnchor(a.id,scheduler,a.anchorMs)else aim.trigger(a.id,action)}
        })
        keys=targets.associate{a->a.id to Key().apply{code=a.id;repeatIntervalMs=a.repeatMs}}
    }
    /** Known held inputs carried over from disconnect/reset must release before rearming. */
    fun blockUntilRelease(inputs:Set<String>){blocked+=inputs}
    fun button(code:String,down:Boolean):Boolean {
        if(!running)return true
        if(!down){blocked.remove(code);pressed.remove(code)}else{
            if(code in blocked||!pressed.add(code))return true
        }
        val a=actions.singleOrNull{code in it.inputs}?:return true
        if(a.kind==TouchAction.RESET){if(down)cancel("explicit reset");return true}
        if(down&&a.kind==TouchAction.TAP&&a.id in applied&&owners[a.id].isNullOrEmpty())release(a.id)
        if(down){if(!neutral){blocked+=code;return true};if(a.cancelOthers){cancel("configured action cancellation");blocked.remove(code);pressed.add(code)};owners.getOrPut(a.id){linkedSetOf()}.add(code);order[a.id]=++serial}
        else {owners[a.id]?.remove(code);if(owners[a.id]?.isEmpty()==true)owners.remove(a.id)}
        reconcile();return true
    }
    fun axes(nextX:Float,nextY:Float) {
        if(!running)return
        require(nextX.isFinite()&&nextY.isFinite()&&nextX in -1f..1f&&nextY in -1f..1f)
        x=nextX;y=nextY
        if(hypot(x,y)<=.05f)neutral=true
        actions.filter{UniversalAction.RIGHT_STICK in it.inputs}.forEach{a->
            val active=neutral&&hypot(x,y)>a.deadZone
            if(active){if(owners.put(a.id,linkedSetOf(UniversalAction.RIGHT_STICK))==null)order[a.id]=++serial}
            else owners.remove(a.id)
        }
        reconcile();aim.axes(x,y)
    }
    private fun pointer(id:String)=ControllerAimHandler.FIRST_POINTER_ID+targets.indexOfFirst{it.id==id}
    private fun reconcile() {
        if(!running)return
        val wanted=targets.filter{owners[it.id]?.isNotEmpty()==true}
        val focused=wanted.filter{it.aim}.maxWithOrNull(compareBy<UniversalAction>{it.priority}.thenBy{order[it.id]?:0})
        aim.focus(focused?.id)
        val permitted=wanted.filter{!it.aim||focused==null||!focused.exclusiveAim||it.id==focused.id||it.priority==focused.priority}.map{it.id}.toSet()
        (applied-permitted).toList().forEach{id->
            // A tap is owned by its bounded timer after a short hardware release.
            if(targets.single{it.id==id}.kind!=TouchAction.TAP||id !in tapTasks)release(id)
        }
        wanted.filter{it.id in permitted&&it.id !in applied}.forEach{a->
            applied+=a.id
            when(a.kind){
                TouchAction.REPEAT->repeat.event(keys.getValue(a.id),pointer(a.id),1)
                TouchAction.TAP->{aim.trigger(a.id,1);val task=Runnable{tapTasks.remove(a.id);if(running){aim.trigger(a.id,0);if(owners[a.id].isNullOrEmpty())applied.remove(a.id)}};tapTasks[a.id]=task;handler.postDelayed(task,40)}
                TouchAction.HOLD->aim.trigger(a.id,1)
                TouchAction.RESET->Unit
            }
        }
        aim.axes(x,y)
    }
    private fun release(id:String){
        tapTasks.remove(id)?.let(handler::removeCallbacks)
        repeat.event(keys.getValue(id),pointer(id),0);aim.trigger(id,0);applied.remove(id)
    }
    fun cancel(reason:String) {
        if(!running)return
        lastCancellation=reason;blocked+=pressed;pressed.clear();neutral=false;x=0f;y=0f
        tapTasks.values.forEach(handler::removeCallbacks);tapTasks.clear()
        repeat.reset();aim.reset();applied.clear();owners.clear();order.clear()
    }
    override fun close(){if(!running)return;cancel("session stopped");running=false}
}
