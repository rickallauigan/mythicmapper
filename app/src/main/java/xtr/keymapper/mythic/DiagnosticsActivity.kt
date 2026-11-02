// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.mythic

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.hardware.input.InputManager
import android.view.InputDevice
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import xtr.keymapper.IRemoteService
import xtr.keymapper.OnKeyEventListener
import xtr.keymapper.devices.InputDeviceDiscovery
import xtr.keymapper.server.RemoteServiceHelper

class DiagnosticsActivity: ComponentActivity() {
    private var remote: IRemoteService?=null
    private var closed=false
    private var lastEvent by mutableStateOf("No event received")
    private val handler=Handler(Looper.getMainLooper())
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        RemoteServiceHelper.getInstance(this){service -> handler.post {
            if(!closed) remote=service
        }}
        setContent {
            var report by remember{mutableStateOf("Root backend unavailable")}
            var devices by remember{mutableStateOf("")}
            LaunchedEffect(Unit){while(true){
                devices=InputDeviceDiscovery.connected().joinToString("\n\n") {d ->
                    "${d.name}\nVID/PID ${d.vendorId.toString(16)}:${d.productId.toString(16)} · ${if(MythicScope.supported(d))"supported observed G8 mode" else "unsupported"}"
                }.ifBlank{"No external input devices"}
                report=runCatching{remote?.diagnostics() ?: "Root backend unavailable"}.getOrElse{"Root backend disconnected: ${it.javaClass.simpleName}"}
                delay(1000)
            }}
            MaterialTheme(colorScheme=darkColorScheme()) {Surface(Modifier.fillMaxSize()){
                Column(Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Input diagnostics",style=MaterialTheme.typography.headlineMedium)
                    Text(devices)
                    Text("Latest controller event: $lastEvent")
                    Text("Software timings only, in nanoseconds. Processing includes synchronous submissions; asynchronous submission is not delivery or display latency. Percentiles cover the latest 2048 samples. Jitter is the last inter-receipt interval change, not USB polling jitter.")
                    Text(report)
                    Text("Rust reference library: ${if(RustStatus.available())"ABI 1 available" else "not available"}. Java mapping remains active.")
                    Button(onClick={finish()}){Text("Back")}
                }
            }}
        }
    }
    private fun controllerKey(event: android.view.KeyEvent): Boolean {
        val device=event.device
        if(device!=null && (device.supportsSource(InputDevice.SOURCE_GAMEPAD) || device.supportsSource(InputDevice.SOURCE_JOYSTICK))) {
            lastEvent="${android.view.KeyEvent.keyCodeToString(event.keyCode)} ${if(event.action==0) "DOWN" else "UP"}"
            return true
        }
        return false
    }
    override fun onKeyDown(keyCode:Int,event:android.view.KeyEvent):Boolean = controllerKey(event) || super.onKeyDown(keyCode,event)
    override fun onKeyUp(keyCode:Int,event:android.view.KeyEvent):Boolean = controllerKey(event) || super.onKeyUp(keyCode,event)
    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if(event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            lastEvent="LX=${event.getAxisValue(android.view.MotionEvent.AXIS_X)} LY=${event.getAxisValue(android.view.MotionEvent.AXIS_Y)} RX=${event.getAxisValue(android.view.MotionEvent.AXIS_Z)} RY=${event.getAxisValue(android.view.MotionEvent.AXIS_RZ)} LT=${event.getAxisValue(android.view.MotionEvent.AXIS_LTRIGGER)} RT=${event.getAxisValue(android.view.MotionEvent.AXIS_RTRIGGER)}"
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }
    override fun onDestroy(){closed=true;remote=null;handler.removeCallbacksAndMessages(null);super.onDestroy()}
}
private object RustStatus {fun available()=xtr.keymapper.engine.RustMappingMath.available}
