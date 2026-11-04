// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.virtual

import android.os.*
import android.view.MotionEvent
import org.json.JSONArray
import org.json.JSONObject
import xtr.keymapper.controller.*
import xtr.keymapper.keymap.*
import xtr.keymapper.server.*
import xtr.keymapper.mythic.HeroTrainingProfiles
import java.util.LinkedHashMap

/** Debug-only logical source. No InputManager injection, native discovery, USB spoofing or storage writes. */
class VirtualG8Session(private val changed:()->Unit={}) : AutoCloseable {
    companion object {const val ID="SIMULATED:Virtual-G8:0";const val MAX_TRACE=4096}
    private val handler=Handler(Looper.getMainLooper())
    private val gate=RuntimeRequestGate<InputService>()
    private var runtime:InputService?=null
    private var monitor:ControllerDeviceMonitor?=null
    private val live=LinkedHashMap<Int,Pair<Float,Float>>()
    val touches=ArrayDeque<JSONObject>()
    val inputs=ArrayDeque<JSONObject>()
    val held=mutableSetOf<String>()
    val axes=mutableMapOf("ABS_X" to 128,"ABS_Y" to 128,"ABS_RX" to 128,"ABS_RY" to 128,"ABS_Z" to 0,"ABS_RZ" to 0)
    var profile="KIMMY";private set
    var connected=true;private set
    var paused=false;private set
    var diagnostics=false;private set
    var dropped=0;private set
    var inputDropped=0;private set
    var maxPointers=0;private set
    var blocked=0;private set
    var closed=false;private set
    private var unlocked=true
    private var gameForeground=true
    private var checkpointTouches=0
    private var lastTouchTime=0L
    var totalTouches=0;private set
    private val epoch=SystemClock.uptimeMillis()
    private val nodes=mutableMapOf<String,LongArray>()
    private val watchdog=Runnable{close();changed()}
    private val sink=object:TouchBackend {
        override fun injectTouch(action:Int,pointer:Int,pressure:Float,x:Float,y:Float) {
            check(Looper.myLooper()==Looper.getMainLooper()){"Touch off lifecycle looper"}
            check(x.isFinite()&&y.isFinite()&&x in 0f..2280f&&y in 0f..1080f){"Invalid touch coordinate"}
            when(action){
                MotionEvent.ACTION_DOWN->{check(!live.containsKey(pointer)){"Duplicate DOWN"};live[pointer]=x to y}
                MotionEvent.ACTION_MOVE->{check(live.containsKey(pointer)){"Orphan MOVE"};live[pointer]=x to y}
                MotionEvent.ACTION_UP->{check(live.remove(pointer)!=null){"Orphan UP"}}
                else->error("Unexpected sink action")
            }
            record(action,pointer,x,y);changed()
        }
        override fun cancelOwnedGesture():Boolean {
            if(live.isNotEmpty()){record(MotionEvent.ACTION_CANCEL,-1,0f,0f);live.clear();changed()}
            return true
        }
        override fun hasSubmissionFailure()=false
    }
    init {check(xtr.keymapper.BuildConfig.DEBUG);start();handler.postDelayed(watchdog,60000)}
    private fun input(event:JSONObject) {
        if(inputs.size==2000){inputs.removeFirst();inputDropped++}
        inputs.add(event)
    }
    private fun record(action:Int,pointer:Int,x:Float,y:Float) {
        val time=SystemClock.uptimeMillis();check(time>=lastTouchTime);lastTouchTime=time
        if(touches.size==MAX_TRACE){touches.removeFirst();dropped++}
        totalTouches++;maxPointers=maxOf(maxPointers,live.size)
        touches.add(JSONObject().put("t_ms",time-epoch).put("source","SIMULATED_RECORDING_SINK")
            .put("action",action).put("pointer",pointer).put("x",x).put("y",y).put("active",if(action==MotionEvent.ACTION_CANCEL)0 else live.size))
    }
    private fun profileText():String {
        if(profile=="BASIC")return "APPLICATION com.mobile.legends\nSCREENSIZE 2280 1080\nENABLED\n"+
            listOf("BTN_GAMEPAD","BTN_EAST","BTN_WEST","BTN_NORTH","BTN_TL","BTN_TR","BTN_TL2","BTN_TR2","BTN_THUMBL","BTN_THUMBR","BTN_START","BTN_SELECT","DPAD_UP","DPAD_DOWN","DPAD_LEFT","DPAD_RIGHT").mapIndexed{i,c->"$c ${100+i*60} 400 0"}.joinToString("\n")
        val hero=if(profile=="BENNETT")HeroGameplayConfig.Hero.BEATRIX else HeroGameplayConfig.Hero.KIMMY
        var text=HeroTrainingProfiles.draft(hero).joinToString("\n")
        text=text.replace("HOLD_RELEASE 0 0","HOLD_RELEASE 0 1")
        if(profile=="BENNETT")text=text.replace("NIBIRU HOLD_RELEASE 0 1","BENNETT PULSE 400 1").replace("1981.08 946.58 80","1981.08 946.58 180")
        return text+"\nENABLED" // disposable in-memory fixture, never a claim of gameplay calibration
    }
    private fun start() {
        if(runtime!=null||closed||paused||diagnostics||!unlocked||!gameForeground)return
        val config=KeymapConfig(null).apply{pointerMode=KeymapConfig.POINTER_OVERLAY}
        val callback=java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,arrayOf(xtr.keymapper.IRemoteServiceCallback::class.java)){_,method,_->
            when(method.name){"asBinder"->Binder();"requestKeymapProfile"->ProfileConfiguration.parse(profileText());"requestKeymapConfig"->config;else->null}
        } as xtr.keymapper.IRemoteServiceCallback
        held.clear()
        val r=InputService(ProfileConfiguration.parse(profileText()),config,callback,2280,1080,null,true,0,sink,true)
        runtime=r;gate.publishOwned(gate.next(),r)
        if(connected)nodes[ID]=longArrayOf(1,2,0,255,axes.getValue("ABS_RX").toLong(),0,255,axes.getValue("ABS_RY").toLong())
        monitor=ControllerDeviceMonitor(r,2280f,1080f){nodes.toMap()}.also{it.start()}
    }
    private fun halt() {
        gate.next();monitor?.stop();monitor=null
        runtime?.let{r->gate.discardOwned(r){owner,_->owner.stop()}};runtime=null
        held.clear();check(live.isEmpty()){"Cleanup leaked contacts"}
    }
    private fun packet(line:String) {
        input(JSONObject().put("packet",line).put("source",ID).put("observed_ms",SystemClock.uptimeMillis()-epoch))
        val frame=gate.captureFrame()
        if(!connected||paused||diagnostics||!unlocked||!gameForeground||!gate.accepts(frame)||frame.owner==null){blocked++;return}
        check(monitor?.event(ID,line)==true){"Unhandled virtual packet: $line"}
    }
    fun key(code:String,down:Boolean) {
        if(closed)return
        val canonical=ControllerBindings.canonical(code);require(ControllerBindings.isController(canonical)){"Unknown controller button"}
        if(down)held.add(canonical)else held.remove(canonical)
        packet("EV_KEY $canonical ${if(down)"DOWN"else"UP"}");changed()
    }
    fun axis(code:String,value:Int) {
        if(closed)return
        require(code in axes.keys&&value in 0..255){"Unknown/out-of-range axis"};axes[code]=value
        packet("EV_ABS $code ${value.toString(16)}");packet("EV_SYN SYN_REPORT 0");changed()
    }
    fun command(step:JSONObject) {
        check(!closed){"Virtual session closed"}
        input(JSONObject(step.toString()).put("source",ID).put("observed_ms",SystemClock.uptimeMillis()-epoch))
        when(step.getString("action")) {
            "button"->key(step.getString("code"),step.getBoolean("down"))
            "axis"->axis(step.getString("code"),step.getInt("value"))
            "profile"->{halt();profile=step.getString("profile");require(profile in listOf("BASIC","BENNETT","KIMMY"));start()}
            "disconnect"->{halt();connected=false;nodes.clear();neutralState()}
            "connect"->{halt();connected=true;neutralState()
                for((code,key)in listOf("ABS_RX" to "right_x","ABS_RY" to "right_y"))if(step.has(key)){val v=step.getInt(key);require(v in 0..255);axes[code]=v}
                start()}
            "syn_dropped"->{packet("EV_SYN SYN_DROPPED 0");held.clear();neutralState()}
            "screen_off"->{halt();paused=true;unlocked=false}
            "screen_on"->{unlocked=step.optBoolean("unlocked",false);if(unlocked){paused=false;start()}else{halt();paused=true}}
            "foreground"->{halt();gameForeground=step.getBoolean("game");start()}
            "diagnostics_on"->{halt();diagnostics=true}
            "diagnostics_off"->{diagnostics=false;start()}
            "checkpoint"->{checkpointTouches=totalTouches}
            "release_all","reset"->{halt();neutralState();start()}
            else->error("Unknown action")
        };changed()
    }
    private fun neutralState(){axes.keys.toList().forEach{axes[it]=if(it=="ABS_Z"||it=="ABS_RZ")0 else 128}}
    fun snapshot()=JSONObject().put("source",ID).put("profile",profile).put("connected",connected).put("paused",paused)
        .put("diagnostics",diagnostics).put("unlocked",unlocked).put("game_foreground",gameForeground)
        .put("active_pointers",live.size).put("total_touches",totalTouches).put("max_pointers",maxPointers).put("blocked",blocked)
        .put("touches_since_checkpoint",totalTouches-checkpointTouches).put("held",JSONArray(held.toList())).put("axes",JSONObject(axes.toMap())).put("trace_dropped",dropped).put("input_dropped",inputDropped)
    fun assertState(expected:JSONObject) {
        val state=snapshot()
        expected.keys().forEach{key->
            when(key){
                "min_downs"->check(touches.count{it.getInt("action")==MotionEvent.ACTION_DOWN}>=expected.getInt(key)){"Missing attack phases"}
                "last_x_min"->check(touches.last().getDouble("x")>=expected.getDouble(key)){"Aim did not follow X"}
                "last_x_max"->check(touches.last().getDouble("x")<=expected.getDouble(key)){"Aim did not reverse X"}
                "min_touches"->check(totalTouches>=expected.getInt(key)){"Expected $key: $expected; actual $state"}
                "min_max_pointers"->check(maxPointers>=expected.getInt(key)){"Overlap absent"}
                "min_blocked"->check(blocked>=expected.getInt(key)){"Blocked input not observed"}
                else->check(state.has(key)&&state.get(key).toString()==expected.get(key).toString()){"Expected $key: $expected; actual $state"}
            }
        }
    }
    override fun close(){if(closed)return;halt();closed=true;handler.removeCallbacksAndMessages(null);neutralState();changed()}
    fun report()=snapshot().put("final_pointers",live.size).put("touch_trace",JSONArray(touches.toList()))
        .put("input_trace",JSONArray(inputs.toList())).put("platform_touch_submissions",0)
        .put("movement_note","ABS_X/Y remain native/pass-through in production; no fake movement touch")
        .put("physical_gameplay","UNVERIFIED; issue #20 deferred")
}
