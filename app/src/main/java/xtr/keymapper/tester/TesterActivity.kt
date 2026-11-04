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
    private lateinit var performance:TesterPerformance
    private var profileTag:String?=null
    private var profileArmed=false
    private var profileStarted=0L
    private var profileWarmup=0L
    private var profileStartEvents=0L
    private var profileStartDrops=0
    private var profileOrientation=0
    private var disconnectRootBefore:org.json.JSONObject?=null
    private var profileRootBefore:org.json.JSONObject?=null
    private var evidenceWritable=true
    private var evidenceRevision=-1L
    private var savedProgress=emptySet<String>()
    private var savedRanges:org.json.JSONObject?=null
    private var savedThresholds:org.json.JSONObject?=null
    private val baselineRenderer get()=intent.getBooleanExtra("render_baseline",false)
    private val acceptanceTick=object:Runnable{override fun run(){if(started){
        if(profileArmed){publish();val seconds=((SystemClock.elapsedRealtime()-profileWarmup)/1000).toInt();model.benchmarkPrompt=if(seconds<5)"Warm up: circle both sticks · ${5-seconds}s"else"Ready · press A to start the 30-second physical protocol"}
        if(profileStarted>0){val elapsed=SystemClock.elapsedRealtime()-profileStarted
            model.benchmarkRemaining=((30000-elapsed).coerceAtLeast(0)/1000).toInt()
            model.benchmarkPrompt=when{elapsed<10000->"1/3 · Circle BOTH sticks fully, about one circle/second";elapsed<20000->"2/3 · Alternate full LT and RT squeezes, about one/second";elapsed<25000->"3/3 · Hold A+B while circling both sticks";else->"3/3 · Release ALL controls; leave them at rest"}
            if(elapsed>=30000)finishPhysicalProfile(null)
            publish()
        }
        persistEvidence()
        worker.postDelayed(this,250)
    }}}

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
        performance=TesterPerformance(window)
        TesterWork.baselineFormatting=baselineRenderer
        worker.post{model.evidenceClockOffset=epoch;restoreEvidence()}
        val original=requireNotNull(window.callback)
        window.callback=object:Window.Callback by original {
            override fun dispatchKeyEvent(event:KeyEvent)=controller(event)||original.dispatchKeyEvent(event)
        }
        setContent{val snapshots=state.collectAsState();TesterScreen(TesterState(),liveState=snapshots,baselineRendering=baselineRenderer,
            onBack={finish()},onReset={submit{newEvidenceSession()}},onPause={submit{model.pauseLog()}},
            onClear={submit{model.clearLog()}},onCenter={submit{model.measureCenter()}},onThresholds={t->submit{model.configure(t)}},
            onExport={worker.post{val text=model.export();main.post{startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,text)},"Export sanitized Android events"))}}},onPageChanged={page->requestedPage=page;captureWindow(when(page){0->"controller";1->"diagnostics";else->"guided"})},pageRequest=requestedPage,onArmBenchmark={armPhysicalProfile("physical-${xtr.keymapper.BuildConfig.BUILD_TYPE}-${SystemClock.uptimeMillis()}")},
            onAliases={submit{archiveEvidence();model.evidence.startAliases()}},onArmDisconnect={submit{model.evidence.armDisconnect();disconnectRootBefore=safeRoot();atomicReport("g8-disconnect",org.json.JSONObject().put("before",disconnectRootBefore).put("requested_action","User physically unplugs while holding A and a stick"));persistEvidence(true)}})}
    }
    override fun onNewIntent(intent:Intent){
        super.onNewIntent(intent)
        intent.getStringExtra("physical_benchmark")?.let{armPhysicalProfile(it)}
        if(captureEnabled){val page=intent.getIntExtra("tester_page",-1);if(page in 0..1){requestedPage=page;captureWindow(if(page==0)"controller"else"diagnostics")}}
    }
    override fun onStart(){
        super.onStart();started=true;val ownGeneration=++generation
        EditorSessionGuard.acquire(this)
        input.registerInputDeviceListener(this,main);discover()
        worker.post(acceptanceTick)
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
                model.ready=true;model.device?.let{model.connect(reportedTransport(it),now())};model.safety=if(evidenceWritable)"Exclusive tester · mapping stopped · zero touch injection"else"Saved evidence rejected; original preserved · mapping stopped"
                main.postDelayed({captureWindow("controller")},500)
                if(captureEnabled)worker.postDelayed(captureTick,32)
                intent.getStringExtra("physical_benchmark")?.let{tag->armPhysicalProfile(tag)}
            }catch(error:Exception){model.ready=false;model.safety="Input unavailable · ${error.javaClass.simpleName}. Mapping remains paused."}
            publish()
        }}
            }
        }
    }
    override fun onConfigurationChanged(configuration:android.content.res.Configuration){
        super.onConfigurationChanged(configuration)
        if(profileOrientation!=0&&configuration.orientation!=profileOrientation)worker.post{if(profileStarted>0)finishPhysicalProfile("Orientation changed")}
    }
    override fun onStop(){
        val changing=isChangingConfigurations
        val ownedBinding=binding;binding=null
        started=false;generation++;input.unregisterInputDeviceListener(this)
        selectedId=null;selectedAxes=emptyList()
        synchronized(packets){packets.clear()}
        worker.removeCallbacks(captureTick);worker.removeCallbacks(acceptanceTick)
        worker.post{
            if(profileStarted>0)finishPhysicalProfile("Activity stopped")
            persistEvidence(true)
            model.ready=false;model.clearTransient()
            runCatching{remote?.endDiagnostics(token);remote?.asBinder()?.unlinkToDeath(death,0);if(!changing)remote?.stopServer()};remote=null
            publish();main.post{
                if(ownedBinding!=null&&!RemoteServiceHelper.useShizuku)runCatching{com.topjohnwu.superuser.ipc.RootService.unbind(ownedBinding)}
                if(!started)EditorSessionGuard.release(this)
            }
        }
        super.onStop()
    }
    override fun onDestroy(){performance.close();worker.post{thread.quitSafely()};super.onDestroy()}
    override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(!hasFocus)submit{model.clearHeld()}}
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
        submit{
            val current=model.device
            val next=if(info!=null&&current?.id==info.id&&current.descriptor==info.descriptor)info.copy(transport=current.transport)
                else if(model.ready&&info!=null)reportedTransport(info)else info
            model.connect(next,now())
            if(disconnectRootBefore!=null&&((current!=null&&next==null)||(current==null&&next!=null))){
                val file=java.io.File(filesDir,"validation/g8-disconnect.json")
                val report=if(file.exists())org.json.JSONObject(file.readText())else org.json.JSONObject().put("before",disconnectRootBefore)
                report.put(if(next==null)"after_removal"else"after_reconnect",runCatching{safeRoot()}.getOrElse{org.json.JSONObject().put("error",it.javaClass.simpleName)})
                    .put("held_after",org.json.JSONArray(model.snapshot().held.toList())).put("axis_values_after",model.snapshot().raw.size).put("evidence",model.evidence.encode())
                atomicReport("g8-disconnect",report)
            }
            if(next!=null&&model.ready&&profileTag==null)intent.getStringExtra("physical_benchmark")?.let{armPhysicalProfile(it)}
            if(next?.supported==true&&(savedProgress.isNotEmpty()||savedRanges!=null||savedThresholds!=null)){
                savedThresholds?.let{model.configure(Thresholds(it.getDouble("dead_zone").toFloat(),it.getDouble("direction").toFloat(),it.getDouble("travel").toFloat(),it.getDouble("trigger_rest").toFloat()))}
                savedRanges?.let{model.restoreRanges(it)}
                model.restoreProgress(savedProgress);savedProgress=emptySet();savedRanges=null;savedThresholds=null
            }
            if(next==null&&profileStarted>0)finishPhysicalProfile("Controller disconnected")
        }
    }
    /** Once per connection, off the UI thread. Only numeric identity is interpolated;
     * no input node is opened, grabbed or probed, and no sysfs value is written. */
    private fun reportedTransport(info:TesterDevice):TesterDevice {
        if(!info.supported)return info
        return runCatching{
            val vendor="%04x".format(java.util.Locale.ROOT,info.vendor)
            val product="%04x".format(java.util.Locale.ROOT,info.product)
            val command="""for d in /sys/bus/usb/devices/*; do if test -f "${'$'}d/idVendor" && test "${'$'}(cat "${'$'}d/idVendor")" = "$vendor" && test "${'$'}(cat "${'$'}d/idProduct")" = "$product"; then echo USB; fi; done"""
            val result=com.topjohnwu.superuser.Shell.cmd(command).exec()
            if(result.isSuccess&&result.out.count{it=="USB"}==1)info.copy(transport="USB · unique sysfs identity observed")else info
        }.getOrDefault(info)
    }
    private fun controller(event:KeyEvent):Boolean {
        if(event.deviceId!=selectedId)return false
        val label=keys[event.keyCode]?:return event.device?.supportsSource(InputDevice.SOURCE_GAMEPAD)==true
        val code=event.keyCode;val down=event.action==KeyEvent.ACTION_DOWN;val repeat=event.repeatCount;val at=now()
        if(event.action==KeyEvent.ACTION_DOWN||event.action==KeyEvent.ACTION_UP)submit{
            if(profileArmed&&label=="A"&&down&&repeat==0&&SystemClock.elapsedRealtime()-profileWarmup>=5000)startPhysicalProfile()
            model.key(label,code,down,repeat,at)
        }
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
            packets.add{val queue=System.nanoTime()-receipt;model.recordTiming(queue);TesterWork.queue(queue);TesterWork.section(5,"Tester.reduce",action)}
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
        publishing=false;state.value=TesterWork.section(6,"Tester.snapshot"){
            model.snapshot().also{if(requestedPage==1)it.events.forEach{event->event.sanitized()}}
        }
        if(captureEnabled){
            if(!activeCaptured&&state.value.held.isNotEmpty()){activeCaptured=true;main.postDelayed({captureWindow("active")},32)}
        }
    },16)}}
    private fun atomicReport(name:String,data:org.json.JSONObject){
        val file=java.io.File(filesDir,"validation/$name.json");file.parentFile!!.mkdirs()
        val atomic=android.util.AtomicFile(file);val stream=atomic.startWrite()
        try{stream.write(data.toString(2).toByteArray());atomic.finishWrite(stream)}catch(error:Exception){atomic.failWrite(stream);throw error}
    }
    private fun restoreEvidence(){
        val file=java.io.File(filesDir,"validation/g8-acceptance.json")
        if(!file.exists())return
        try{val report=org.json.JSONObject(file.readText());require(report.getInt("schema")==1)
            // Validate the whole saved report before mutating the live reducer. A corrupt
            // range/threshold must not escape later from device discovery on the worker.
            val proof=report.getJSONObject("evidence");HardwareEvidence().restore(proof)
            val checks=report.getJSONArray("passed_checks");require(checks.length()<=32)
            val progress=(0 until checks.length()).map{checks.getString(it).also{label->require(label.length<=32)}}.toSet()
            val ranges=report.optJSONObject("ranges");ranges?.let{TesterModel().restoreRanges(it)}
            val thresholds=report.optJSONObject("thresholds")
            thresholds?.let{
                require(it.getDouble("dead_zone").toFloat() in .05f.. .3f)
                require(it.getDouble("direction").toFloat() in .4f.. .8f)
                require(it.getDouble("travel").toFloat() in .8f.. .98f)
                require(it.getDouble("trigger_rest").toFloat() in 0f.. .4f)
            }
            model.evidence.restore(proof);savedProgress=progress;savedRanges=ranges;savedThresholds=thresholds
        }catch(error:Exception){evidenceWritable=false;model.safety="Saved tester evidence rejected; original file preserved"}
    }
    private fun persistEvidence(force:Boolean=false){
        if(!evidenceWritable)return
        val snapshot=model.snapshot();val revision=model.evidence.revision*31+snapshot.checks.count{it.passed}
        if(!force&&revision==evidenceRevision)return
        evidenceRevision=revision
        atomicReport("g8-acceptance",org.json.JSONObject().put("schema",1).put("ranges",model.rangeEvidence()).put("evidence",model.evidence.encode())
            .put("thresholds",org.json.JSONObject().put("dead_zone",model.thresholds.deadZone).put("direction",model.thresholds.direction).put("travel",model.thresholds.travel).put("trigger_rest",model.thresholds.triggerRest)).put("passed_checks",org.json.JSONArray(snapshot.checks.filter{it.passed}.map{it.id})))
    }
    private fun archiveEvidence(){
        val previous=java.io.File(filesDir,"validation/g8-acceptance.json")
        if(previous.exists())previous.copyTo(java.io.File(filesDir,"validation/g8-acceptance-archive-${System.currentTimeMillis()}.json"))
    }
    private fun newEvidenceSession(){
        archiveEvidence()
        model.reset();model.evidence.clear();evidenceWritable=true;evidenceRevision=-1;savedProgress=emptySet();savedRanges=null;savedThresholds=null;persistEvidence(true)
    }
    private fun safeRoot():org.json.JSONObject {
        val service=remote?:error("Root backend missing")
        val raw=org.json.JSONObject(service.diagnostics())
        return org.json.JSONObject().put("mapping_active",service.isActive).put("exclusive",raw.getBoolean("diagnostic_exclusive"))
            .put("logical_touches",raw.getInt("logical_active_touches")).put("submission_samples",org.json.JSONArray(raw.getString("samples_total")).getLong(2))
    }
    private fun armPhysicalProfile(tag:String){
        if(!tag.matches(Regex("[A-Za-z0-9_-]{1,64}")))return
        worker.post{
            if(!model.ready||model.device?.supported!=true||performance.active)return@post
            if(java.io.File(filesDir,"validation/physical-$tag.json").exists()){model.benchmarkPrompt="Benchmark already saved · $tag";publish();return@post}
            profileTag=tag;profileArmed=true;profileWarmup=SystemClock.elapsedRealtime();requestedPage=0
            model.benchmarkPrompt="Warm up: circle both sticks for five seconds, then press A";publish()
        }
    }
    private fun startPhysicalProfile(){
        val root=safeRoot();if(root.getBoolean("mapping_active")||!root.getBoolean("exclusive")||root.getInt("logical_touches")!=0)return
        profileRootBefore=root;profileArmed=false;profileStartEvents=model.snapshot().totalEvents;profileStartDrops=model.dropped;profileOrientation=resources.configuration.orientation
        performance.start();profileStarted=SystemClock.elapsedRealtime()
    }
    private fun finishPhysicalProfile(reason:String?){
        if(profileStarted==0L)return
        val report=performance.finish().put("protocol","g8-physical-v1-30s").put("build_type",xtr.keymapper.BuildConfig.BUILD_TYPE)
            .put("render_revision",if(baselineRenderer)"controlled-eager-full-state-formatter-v1"else"scoped-state-lazy-and-format-v2").put("baseline_renderer",baselineRenderer).put("source","Live physical Android input; identical timed prompts required")
            .put("aborted",reason?:org.json.JSONObject.NULL).put("root_before",profileRootBefore)
        runCatching{report.put("root_after",safeRoot())}.onFailure{report.put("root_error",it.javaClass.simpleName)}
        val snapshot=model.snapshot();report.put("observed_events",snapshot.totalEvents-profileStartEvents).put("queue_overflows",snapshot.dropped-profileStartDrops).put("orientation",profileOrientation)
            .put("held_at_end",org.json.JSONArray(snapshot.held.toList())).put("input_queue",TesterWork.queueReport())
            .put("ranges",model.rangeEvidence()).put("evidence",model.evidence.encode())
        atomicReport("physical-${profileTag}",report);profileStarted=0;model.benchmarkRemaining=0
        model.benchmarkPrompt=if(reason==null)"Physical benchmark saved · ${profileTag}"else"Benchmark interrupted · $reason"
        persistEvidence(true);publish()
    }
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
