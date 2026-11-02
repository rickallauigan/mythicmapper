// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.tester

import android.os.*
import android.hardware.input.InputManager
import android.view.*
import android.content.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import xtr.keymapper.IRemoteService
import xtr.keymapper.devices.InputDeviceDiscovery
import xtr.keymapper.mythic.MythicScope
import xtr.keymapper.editor.EditorSessionGuard
import xtr.keymapper.server.RemoteServiceHelper

/** Foreground Android input only. Never creates Input/TouchBackend or another evdev reader. */
class TesterActivity:ComponentActivity(),InputManager.InputDeviceListener {
    private val thread=HandlerThread("g8-tester-reducer").apply{start()}
    private val worker=Handler(thread.looper)
    private val main=Handler(Looper.getMainLooper())
    private val model=TesterModel()
    val state=MutableStateFlow(TesterState())
    private val token=Binder()
    private var remote:IRemoteService?=null
    private var binding:RemoteServiceHelper.RemoteServiceConnection?=null
    @Volatile private var started=false
    @Volatile private var generation=0
    private var selectedId:Int?=null
    private var selectedAxes=emptyList<Int>()
    private val packets=ArrayDeque<()->Unit>()
    private var scheduled=false
    private var publishing=false
    private val captureTick=object:Runnable{override fun run(){if(started&&captureEnabled){captureState();worker.postDelayed(this,1000)}}}
    private var activeCaptured=false
    private var screenshotPending=false
    private var requestedPage by mutableIntStateOf(-1)
    private val captureEnabled get()=xtr.keymapper.BuildConfig.DEBUG&&intent.getBooleanExtra("capture_diagnostics",false)
    private val epoch=SystemClock.uptimeMillis()
    private val input by lazy{getSystemService(InputManager::class.java)}
    private val death=IBinder.DeathRecipient{worker.post{model.ready=false;model.clearTransient();model.safety="Root backend disconnected · input disabled";publish()}}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        val original=requireNotNull(window.callback)
        window.callback=object:Window.Callback by original {
            override fun dispatchKeyEvent(event:KeyEvent)=controller(event)||original.dispatchKeyEvent(event)
        }
        setContent{val snapshot by state.collectAsState();TesterScreen(snapshot,
            onBack={finish()},onReset={submit{model.reset()}},onPause={submit{model.pauseLog()}},
            onClear={submit{model.clearLog()}},onCenter={submit{model.measureCenter()}},onThresholds={t->submit{model.configure(t)}},
            onExport={worker.post{val text=model.export();main.post{startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,text)},"Export sanitized Android events"))}}},onPageChanged={page->captureWindow(if(page==0)"controller"else"diagnostics")},pageRequest=requestedPage)}
    }
    override fun onNewIntent(intent:Intent){
        super.onNewIntent(intent)
        if(captureEnabled){val page=intent.getIntExtra("tester_page",-1);if(page in 0..1){requestedPage=page;captureWindow(if(page==0)"controller"else"diagnostics")}}
    }
    override fun onStart(){
        super.onStart();started=true;val ownGeneration=++generation
        EditorSessionGuard.acquire(this)
        input.registerInputDeviceListener(this,main);discover()
        worker.post {
            val granted=runCatching{com.topjohnwu.superuser.Shell.getShell().isRoot}.getOrDefault(false)
            main.post bind@{
                if(!started||ownGeneration!=generation)return@bind
                if(!granted){worker.post{model.ready=false;model.safety="Input unavailable · app-specific root consent required";publish()};return@bind}
        binding=RemoteServiceHelper.getInstance(this){service->worker.post{
            if(ownGeneration!=generation||!started)return@post
            try {
                if(service==null)error("Root backend unavailable")
                if(!service.beginDiagnostics(token)){service.endDiagnostics(token);error("Exclusive root lease unavailable or cleanup failed")}
                if(ownGeneration!=generation||!started){service.endDiagnostics(token);return@post}
                remote=service;service.asBinder().linkToDeath(death,0)
                model.ready=true;model.safety="Exclusive tester · mapping stopped · zero touch injection"
                main.postDelayed({captureWindow("controller")},500)
                if(captureEnabled)worker.postDelayed(captureTick,32)
            }catch(error:Exception){model.ready=false;model.safety="Input unavailable · ${error.javaClass.simpleName}. Mapping remains paused."}
            publish()
        }}
            }
        }
    }
    override fun onStop(){
        val changing=isChangingConfigurations
        val ownedBinding=binding;binding=null
        started=false;generation++;input.unregisterInputDeviceListener(this)
        selectedId=null;selectedAxes=emptyList()
        synchronized(packets){packets.clear()}
        worker.removeCallbacks(captureTick)
        worker.post{
            model.ready=false;model.clearTransient()
            runCatching{remote?.endDiagnostics(token);remote?.asBinder()?.unlinkToDeath(death,0);if(!changing)remote?.stopServer()};remote=null
            publish();main.post{
                if(ownedBinding!=null&&!RemoteServiceHelper.useShizuku)runCatching{com.topjohnwu.superuser.ipc.RootService.unbind(ownedBinding)}
                if(!started)EditorSessionGuard.release(this)
            }
        }
        super.onStop()
    }
    override fun onDestroy(){worker.post{thread.quitSafely()};super.onDestroy()}
    override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(!hasFocus)submit{model.clearTransient()}}
    private fun now()=SystemClock.uptimeMillis()-epoch
    override fun onInputDeviceAdded(id:Int)=discover()
    override fun onInputDeviceChanged(id:Int)=discover()
    override fun onInputDeviceRemoved(id:Int)=discover()
    private fun discover(){
        val all=InputDeviceDiscovery.connected()
        val hardware=all.firstOrNull{MythicScope.supported(it)} ?: all.firstOrNull{xtr.keymapper.devices.InterfaceEvidence.controller(it.sources)}
        val device=hardware?.let{InputDevice.getDevice(it.runtimeId)}
        // runtime ID from the actual InputDevice; no descriptor, VID/PID or ranges from artwork.
        val info=device?.let{d->
            val ranges=d.motionRanges.filter{it.isFromSource(InputDevice.SOURCE_JOYSTICK)}.map{AxisRange(it.axis,MotionEvent.axisToString(it.axis).removePrefix("AXIS_"),it.min,it.max,it.flat)}.distinctBy{it.code}
            val codes=ranges.map{it.code}.toSet()
            fun pair(a:Int,b:Int)=if(a in codes&&b in codes)a to b else null
            val left=pair(MotionEvent.AXIS_X,MotionEvent.AXIS_Y)
            val right=pair(MotionEvent.AXIS_Z,MotionEvent.AXIS_RZ)?:pair(MotionEvent.AXIS_RX,MotionEvent.AXIS_RY)
            fun trigger(vararg candidates:Int)=candidates.firstOrNull{it in codes&&it!=right?.first&&it!=right?.second}
            val available=d.hasKeys(*keys.keys.toIntArray());val supported=keys.values.filterIndexed{i,_->available[i]}.toSet()
            TesterDevice(d.id,d.name,d.vendorId,d.productId,d.descriptor,"Unknown (Android InputDevice does not expose bus)",hardware.let(MythicScope::supported),supported,ranges,left,right,
                trigger(MotionEvent.AXIS_LTRIGGER,MotionEvent.AXIS_BRAKE),trigger(MotionEvent.AXIS_RTRIGGER,MotionEvent.AXIS_GAS))
        }
        selectedId=info?.id;selectedAxes=info?.ranges?.map{it.code}?:emptyList()
        submit{model.connect(info,now())}
    }
    private fun controller(event:KeyEvent):Boolean {
        if(event.deviceId!=selectedId)return false
        val label=keys[event.keyCode]?:return event.device?.supportsSource(InputDevice.SOURCE_GAMEPAD)==true
        val code=event.keyCode;val down=event.action==KeyEvent.ACTION_DOWN;val repeat=event.repeatCount;val at=now()
        if(event.action==KeyEvent.ACTION_DOWN||event.action==KeyEvent.ACTION_UP)submit{model.key(label,code,down,repeat,at)}
        return true
    }
    override fun dispatchGenericMotionEvent(event:MotionEvent):Boolean {
        if(event.deviceId!=selectedId||!event.isFromSource(InputDevice.SOURCE_JOYSTICK))return super.dispatchGenericMotionEvent(event)
        // Copy pooled MotionEvent data only; decode, log and reduce off the UI thread.
        for(history in 0 until event.historySize){val values=selectedAxes.associateWith{event.getHistoricalAxisValue(it,history)};val at=event.getHistoricalEventTime(history)-epoch;submit{model.axes(values,at)}}
        val values=selectedAxes.associateWith{event.getAxisValue(it)};val at=now();submit{model.axes(values,at)}
        return true
    }
    private fun submit(action:()->Unit){
        val receipt=System.nanoTime()
        synchronized(packets){
            if(packets.size>=256){packets.clear();packets.add{model.overflow()}}
            packets.add{model.recordTiming(System.nanoTime()-receipt);action()}
            if(scheduled)return;scheduled=true
        }
        worker.post(::drain)
    }
    private fun drain(){
        repeat(64){val next=synchronized(packets){if(packets.isEmpty())null else packets.removeFirst()};next?.invoke()}
        publish()
        synchronized(packets){if(packets.isEmpty())scheduled=false else worker.post(::drain)}
    }
    private fun publish(){if(!publishing){publishing=true;worker.postDelayed({
        publishing=false;state.value=model.snapshot()
        if(captureEnabled){
            if(!activeCaptured&&state.value.held.isNotEmpty()){activeCaptured=true;main.postDelayed({captureWindow("active")},32)}
        }
    },16)}}
    /** Explicit debug capture, opt-in via own Activity intent; no identifiers/profile contents. */
    private fun captureState(){
        val s=state.value;val t=s.receiptToProcessNs.sorted()
        val report=org.json.JSONObject().put("source","Observed Android InputDevice events")
            .put("ready",model.ready).put("status",s.status).put("event_history_count",s.eventCount).put("events_observed_total",s.totalEvents)
            .put("passed_checks",org.json.JSONArray(s.checks.filter{it.passed}.map{it.label})).put("applicable_checks",s.checks.size)
            .put("held",org.json.JSONArray(s.held.toList())).put("queue_overflows",s.dropped).put("repeated_downs",s.duplicates)
            .put("events",org.json.JSONArray(s.events.map{it.sanitized()}))
        if(t.isNotEmpty())report.put("receipt_to_reducer_ns",org.json.JSONObject().put("samples",t.size).put("p50",t[((t.size-1)*.5).toInt()]).put("p95",t[((t.size-1)*.95).toInt()]).put("p99",t[((t.size-1)*.99).toInt()]))
        report.put("app_cpu_ms",android.os.Process.getElapsedCpuTime()).put("uptime_ms",SystemClock.uptimeMillis()).put("app_pss_kb",Debug.getPss())
        runCatching{remote?.let{
            val raw=org.json.JSONObject(it.diagnostics());val safe=org.json.JSONObject()
            for(key in listOf("backend_uid","logical_active_touches","diagnostic_exclusive","reader_running","injection_errors"))safe.put(key,raw.get(key))
            safe.put("injection_submission_samples",org.json.JSONArray(raw.getString("samples_total")).getLong(2));report.put("root",safe)
        }}
        val file=java.io.File(filesDir,"validation/tester-live.json");file.parentFile!!.mkdirs();val atomic=android.util.AtomicFile(file);val stream=atomic.startWrite();try{stream.write(report.toString(2).toByteArray());atomic.finishWrite(stream)}catch(error:Exception){atomic.failWrite(stream)}
    }
    /** PixelCopy captures only our actual app window, excluding private status-bar overlays. */
    private fun captureWindow(name:String){
        if(!captureEnabled||!started||screenshotPending||Build.VERSION.SDK_INT<26)return
        main.postDelayed({
            if(!started||!window.decorView.hasWindowFocus())return@postDelayed
            screenshotPending=true
            val bitmap=android.graphics.Bitmap.createBitmap(window.decorView.width,window.decorView.height,android.graphics.Bitmap.Config.ARGB_8888)
            PixelCopy.request(window,bitmap,{result->
                screenshotPending=false
                worker.post{
                    if(result==PixelCopy.SUCCESS)java.io.File(filesDir,"validation/tester-$name.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                    bitmap.recycle()
                }
            },main)
        },if(name=="active")0 else 200)
    }
    /** Debug rendering benchmark only: explicit synthetic badge, no reducer events/checks or injection. */
    fun benchmarkRendering(done:()->Unit){
        check(xtr.keymapper.BuildConfig.DEBUG&&!captureEnabled)
        worker.post{
            val before=model.snapshot();var frame=0
            val replay=object:Runnable{override fun run(){
                if(!started){done();return}
                val phase=frame/12.0;val x=kotlin.math.sin(phase).toFloat();val y=kotlin.math.cos(phase).toFloat()
                val pressure=(x+1)/2
                val raw=before.raw.toMutableMap()
                for(code in listOfNotNull(before.device?.lt,before.device?.rt))before.device?.ranges?.find{it.code==code}?.let{raw[code]=it.min+pressure*(it.max-it.min)}
                state.value=before.copy(status="Synthetic replay",safety="Synthetic rendering benchmark · not physical input",raw=raw.toMap(),
                    held=if(frame%12<6)setOf("A","LB")else emptySet(),left=StickReading(null,null,x,y,null,0),right=StickReading(null,null,-x,-y,null,0))
                frame++
                if(frame<240)worker.postDelayed(this,16)else{state.value=model.snapshot();done()}
            }}
            worker.post(replay)
        }
    }
    companion object {
        val keys=linkedMapOf(KeyEvent.KEYCODE_BUTTON_A to "A",KeyEvent.KEYCODE_BUTTON_B to "B",KeyEvent.KEYCODE_BUTTON_X to "X",KeyEvent.KEYCODE_BUTTON_Y to "Y",
            KeyEvent.KEYCODE_BUTTON_L1 to "LB",KeyEvent.KEYCODE_BUTTON_R1 to "RB",KeyEvent.KEYCODE_BUTTON_L2 to "LT",KeyEvent.KEYCODE_BUTTON_R2 to "RT",
            KeyEvent.KEYCODE_BUTTON_THUMBL to "L3",KeyEvent.KEYCODE_BUTTON_THUMBR to "R3",KeyEvent.KEYCODE_DPAD_UP to "↑",KeyEvent.KEYCODE_DPAD_DOWN to "↓",
            KeyEvent.KEYCODE_DPAD_LEFT to "←",KeyEvent.KEYCODE_DPAD_RIGHT to "→",KeyEvent.KEYCODE_BUTTON_SELECT to "View",KeyEvent.KEYCODE_BUTTON_START to "Menu",KeyEvent.KEYCODE_BUTTON_MODE to "Home")
    }
}
