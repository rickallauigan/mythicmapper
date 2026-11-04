// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.validation

import android.app.Activity
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import org.json.JSONObject

/** No game, links, buttons or purchases. All touches are consumed by this dedicated surface. */
class ValidationActivity: Activity() {
    lateinit var surface: TouchSurface
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (android.os.Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        surface=TouchSurface(this)
        setContentView(surface)
        window.decorView.post { if (android.os.Build.VERSION.SDK_INT >= 30) window.insetsController?.hide(WindowInsets.Type.systemBars()) }
    }
    override fun onKeyDown(code:Int,event:android.view.KeyEvent):Boolean = controller(event) || super.onKeyDown(code,event)
    override fun onKeyUp(code:Int,event:android.view.KeyEvent):Boolean = controller(event) || super.onKeyUp(code,event)
    private fun controller(event:android.view.KeyEvent):Boolean {
        val gamepad=event.device?.let {it.supportsSource(android.view.InputDevice.SOURCE_GAMEPAD)||it.supportsSource(android.view.InputDevice.SOURCE_JOYSTICK)} ?: false
        if(gamepad)surface.controllerLabel="${android.view.KeyEvent.keyCodeToString(event.keyCode)} ${if(event.action==0)"DOWN"else"UP"}"
        return gamepad
    }
    override fun onGenericMotionEvent(event:MotionEvent):Boolean {
        if(!event.isFromSource(android.view.InputDevice.SOURCE_JOYSTICK))return super.onGenericMotionEvent(event)
        surface.controllerLabel="LX=${event.getAxisValue(MotionEvent.AXIS_X)} LY=${event.getAxisValue(MotionEvent.AXIS_Y)} RX=${event.getAxisValue(MotionEvent.AXIS_Z)} RY=${event.getAxisValue(MotionEvent.AXIS_RZ)}"
        return true
    }
    val guard=object:IValidationSurface.Stub() {
        override fun ready()=surface.hasWindowFocus() && surface.isShown && !isFinishing
        override fun width()=surface.width
        override fun height()=surface.height
        override fun activeTouches()=surface.result().getInt("active_pointers")
    }
    class TouchSurface(context:android.content.Context):View(context) {
        var controllerLabel="Controller input: waiting"
            set(value){field=value;invalidate()}
        private val active=mutableSetOf<Int>()
        private val paint=Paint().apply {color=Color.WHITE;textSize=38f}
        private var events=0; private var maxPointers=0; private var errors=0; private var cancels=0
        @Synchronized override fun onTouchEvent(event:MotionEvent):Boolean {
            val ids=(0 until event.pointerCount).map{event.getPointerId(it)}.toSet()
            val id=event.getPointerId(event.actionIndex)
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {if(active.isNotEmpty())errors++;if(!active.add(id))errors++}
                MotionEvent.ACTION_POINTER_DOWN -> if(!active.add(id))errors++
            }
            if(event.actionMasked!=MotionEvent.ACTION_CANCEL && ids!=active)errors++
            maxPointers=maxOf(maxPointers,event.pointerCount)
            when(event.actionMasked) {
                MotionEvent.ACTION_UP,MotionEvent.ACTION_POINTER_UP -> if(!active.remove(id))errors++
                MotionEvent.ACTION_CANCEL -> {active.clear();cancels++}
            }
            events++;invalidate();return true
        }
        @Synchronized fun result()=JSONObject().put("observed_events",events).put("max_pointers",maxPointers)
            .put("active_pointers",active.size).put("sequence_errors",errors).put("cancel_events",cancels)
        override fun onDraw(canvas:Canvas) {
            canvas.drawColor(Color.rgb(10,28,36))
            canvas.drawText("MYTHICMAPPER SAFE TEST SURFACE",40f,160f,paint)
            canvas.drawText("No gameplay actions. All touches consumed.",40f,220f,paint)
            canvas.drawText("Touch events: $events  Active: ${active.size}",40f,280f,paint)
            canvas.drawText(controllerLabel.take(65),40f,350f,paint)
            canvas.drawText("Controller buttons are observed, not mapped here.",40f,420f,paint)
        }
    }
}
