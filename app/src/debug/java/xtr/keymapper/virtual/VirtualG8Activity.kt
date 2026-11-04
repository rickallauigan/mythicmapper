// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.virtual

import android.app.ActivityManager
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import xtr.keymapper.editor.EditorSessionGuard
import xtr.keymapper.tester.ControllerDrawing
import xtr.keymapper.tester.StickReading

/** An explicit debug entry point. Output is ALWAYS a recording sink, even on rooted devices. */
class VirtualG8Activity:ComponentActivity() {
    var session:VirtualG8Session?=null;private set
    private var revision by mutableIntStateOf(0)
    private val main=Handler(Looper.getMainLooper())
    private var pending=false
    private var message by mutableStateOf("Disabled. Start explicitly; no hardware is connected by this screen.")
    private val publish=Runnable{pending=false;revision++}
    private fun changed(){if(!pending){pending=true;main.postDelayed(publish,16)}}
    fun enable(renderEvents:Boolean=true) {
        check(xtr.keymapper.BuildConfig.DEBUG)
        val running=getSystemService(ActivityManager::class.java).getRunningServices(100)
        check(running.none{it.service.className=="xtr.keymapper.TouchPointer"&&it.started}){"Stop real mapping before virtual testing"}
        session?.close();EditorSessionGuard.acquire(this)
        session=VirtualG8Session(if(renderEvents)::changed else ({}));message="SIMULATED • recording only • 60-second safety timeout";changed()
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=android.graphics.Color.rgb(8,12,23)
        window.navigationBarColor=android.graphics.Color.rgb(8,12,23)
        androidx.core.view.WindowInsetsControllerCompat(window,window.decorView).isAppearanceLightStatusBars=false
        setContent{revision;VirtualScreen()}
        if(intent.getBooleanExtra("enable_virtual_g8",false))runCatching{enable()}.onFailure{message=it.message?:"Virtual activation unavailable"}
    }
    fun disable(){session?.close();session=null;EditorSessionGuard.release(this)}
    override fun onStop(){disable();super.onStop()}
    override fun onDestroy(){main.removeCallbacksAndMessages(null);super.onDestroy()}
    @Composable private fun VirtualScreen() {
        MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xffb290ff),background=Color(0xff080c17),surface=Color(0xff14182a))) {
            Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
                Column(Modifier.safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("MythicMapper · Virtual G8",style=MaterialTheme.typography.headlineMedium)
                    Text("SIMULATED — DEV ONLY",color=Color(0xffb290ff))
                    Text(if(session?.closed==true)"Stopped automatically · Start to begin a new recording"else message)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(onClick={runCatching{enable()}.onFailure{message=it.message?:"Unavailable"}}){Text("Start virtual controller")}
                        Button(onClick={session?.close();session=null;EditorSessionGuard.release(this);message="Stopped · all recording pointers released";changed()}){Text("STOP / RELEASE ALL")}
                    }
                    session?.takeUnless{it.closed}?.let{s->
                        val state=s.snapshot()
                        Text("Virtual G8: ${if(s.connected)"Connected (logical only)"else"Disconnected"} · ${s.profile} · Recording contacts: ${state.getInt("active_pointers")}")
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            for(p in listOf("BASIC","BENNETT","KIMMY"))OutlinedButton(onClick={s.command(JSONObject().put("action","profile").put("profile",p))}){Text(p)}
                            OutlinedButton(onClick={s.command(JSONObject().put("action",if(s.connected)"disconnect"else"connect"))}){Text(if(s.connected)"Disconnect"else"Connect")}
                        }
                        Text("Saved custom mappings · recording only")
                        val profiles=remember(s){xtr.keymapper.mythic.HeroLibraryStore(this@VirtualG8Activity).read().profiles}
                        for(profile in profiles) {
                            val document=remember(profile.id){runCatching{xtr.keymapper.universal.UniversalStorage.read(this@VirtualG8Activity,profile.id)}.getOrNull()}
                            if(document!=null)OutlinedButton(onClick={runCatching{s.loadUniversal(document)}.onFailure{message=it.message?:"Mapping unavailable"};changed()}){Text("Preview ${profile.name}")}
                        }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f)) {
                                val l=reading(s,"ABS_X","ABS_Y");val r=reading(s,"ABS_RX","ABS_RY")
                                ControllerDrawing(s.held.map{ xtr.keymapper.controller.ControllerBindings.label(it) }.toSet(),l,r)
                                Text("Original controller artwork reused · no physical G8 attestation")
                            }
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                for(row in listOf(listOf("BTN_TL","BTN_TR","BTN_TL2","BTN_TR2"),listOf("BTN_GAMEPAD","BTN_EAST","BTN_WEST","BTN_NORTH"),listOf("BTN_THUMBL","BTN_THUMBR","BTN_SELECT","BTN_START"),listOf("DPAD_UP","DPAD_DOWN","DPAD_LEFT","DPAD_RIGHT"))) {
                                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){for(code in row)PressControl(s,code)}
                                }
                                Text("Hold controls to press; release/cancel lifts. L4/R4 have no invented identity.")
                            }
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            StickPad(s,"Left stick · native/pass-through","ABS_X","ABS_Y",Modifier.weight(1f))
                            StickPad(s,"Right stick · mapping input","ABS_RX","ABS_RY",Modifier.weight(1f))
                        }
                        for((label,code)in listOf("LT analog" to "ABS_Z","RT analog" to "ABS_RZ")) {
                            Text("$label: ${s.axes[code]} / 255 · separate digital button; production ignores duplicate analog triggers")
                            Slider(value=s.axes.getValue(code).toFloat(),onValueChange={s.axis(code,it.toInt())},valueRange=0f..255f)
                        }
                        Text("Touch timeline · source SIMULATED_RECORDING_SINK",style=MaterialTheme.typography.titleMedium)
                        for(event in s.touches.takeLast(12).asReversed())Text(event.toString(),style=MaterialTheme.typography.bodySmall)
                        Text("No Android touch injection; no game installation required. Left-stick native game movement and issue #20 game effects remain unverified.")
                    }
                }
            }
        }
    }
    private fun reading(s:VirtualG8Session,x:String,y:String):StickReading {
        fun n(code:String)=(s.axes.getValue(code)-128)/128f
        return StickReading(s.axes.getValue(x).toFloat(),s.axes.getValue(y).toFloat(),n(x),n(y),null,0)
    }
    @Composable private fun PressControl(s:VirtualG8Session,code:String) {
        Box(Modifier.background(if(code in s.held)Color(0xff6d41ba)else Color(0xff252b43),RoundedCornerShape(10.dp))
            .pointerInput(s,code){detectTapGestures(onPress={s.key(code,true);try{tryAwaitRelease()}finally{if(session===s)s.key(code,false)}})}.padding(12.dp)) {
            Text(xtr.keymapper.controller.ControllerBindings.label(code))
        }
    }
    @Composable private fun StickPad(s:VirtualG8Session,title:String,x:String,y:String,modifier:Modifier) {
        Column(modifier){Text(title);Text("Raw ${s.axes[x]}, ${s.axes[y]} · center 128 · range 0…255")
            Canvas(Modifier.fillMaxWidth().height(160.dp).pointerInput(s,x,y){
                fun move(p:Offset){val radius=minOf(size.width,size.height)/2f
                    s.axis(x,(128+(p.x-size.width/2f)/radius*127).toInt().coerceIn(0,255));s.axis(y,(128+(p.y-size.height/2f)/radius*127).toInt().coerceIn(0,255))}
                detectDragGestures(onDragStart={move(it)},onDragEnd={s.axis(x,128);s.axis(y,128)},onDragCancel={s.axis(x,128);s.axis(y,128)}){c,_->c.consume();move(c.position)}
            }){val radius=size.minDimension/2f;drawCircle(Color(0xff252b43),radius);drawCircle(Color(0xff8159c9),radius*.15f,style=androidx.compose.ui.graphics.drawscope.Stroke(2f));drawLine(Color.Gray,Offset(center.x-radius,center.y),Offset(center.x+radius,center.y));drawLine(Color.Gray,Offset(center.x,center.y-radius),Offset(center.x,center.y+radius));drawCircle(Color(0xffbe9aff),10f,Offset(center.x+(s.axes.getValue(x)-128)/128f*radius,center.y+(s.axes.getValue(y)-128)/128f*radius))}
        }
    }
}
