// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.universal

import android.os.Handler
import android.os.Looper

/** Production editor preview: bounded recording only. Never constructs Input/InputService/RootService. */
class MappingPreview(d:UniversalMapping,private val changed:()->Unit={}):AutoCloseable {
    data class Event(val action:Int,val pointer:Int,val x:Float,val y:Float,val atMs:Long)
    private val epoch=android.os.SystemClock.uptimeMillis()
    var lastInput="None";private set
    fun button(code:String,down:Boolean,source:String="SIMULATED"){lastInput="$source $code ${if(down)"DOWN"else"UP"}";engine.button(code,down);changed()}
    fun axes(x:Float,y:Float,source:String="SIMULATED"){lastInput="$source right stick $x / $y";engine.axes(x,y);changed()}
    val events=ArrayDeque<Event>();val active=linkedMapOf<Int,Pair<Float,Float>>()
    val engine=UniversalActionHandler(d.copy(enabled=true,calibrated=true).validate(),Handler(Looper.getMainLooper()),{x,y,action,pointer->
        if(action==0)active.remove(pointer)else active[pointer]=x to y
        if(events.size==256)events.removeFirst();events.addLast(Event(action,pointer,x,y,android.os.SystemClock.uptimeMillis()-epoch));changed()
    },d.geometry.width.toFloat(),d.geometry.height.toFloat())
    init{engine.axes(0f,0f)}
    override fun close(){engine.close();changed()}
}
