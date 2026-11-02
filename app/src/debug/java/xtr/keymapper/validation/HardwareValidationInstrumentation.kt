// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.validation

import android.app.Activity
import android.app.Instrumentation
import android.content.*
import android.os.*
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import org.json.JSONObject
import org.json.JSONArray
import xtr.keymapper.engine.RustMappingMath
import xtr.keymapper.controller.ControllerAimHandler
import xtr.keymapper.keymap.KeymapProfiles
import xtr.keymapper.mythic.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Built-in instrumentation, debug APK only; no extra test frameworks on the phone. */
class HardwareValidationInstrumentation: Instrumentation() {
    private var suite="jni"
    override fun onCreate(arguments:Bundle?) {suite=arguments?.getString("suite") ?: "jni";start()}
    override fun onStart() {
        val report=JSONObject().put("suite",suite)
        var result=Activity.RESULT_OK
        try {
            when(suite){
                "jni" -> report.put("result",jni())
                "profiles_prepare" -> report.put("result",profiles(false))
                "profiles_verify" -> report.put("result",profiles(true))
                "injection" -> report.put("result",injection())
                "startup" -> report.put("result",startup())
                "overlay", "screen" -> report.put("result",overlay())
                "reader_hold" -> report.put("result",readerHold())
                "runtime" -> report.put("result",runtimeLifecycle())
                "tester" -> report.put("result",tester())
                "heroes" -> report.put("result",tester(true))
                "heroes_prepare" -> report.put("result",prepareHeroes())
                "heroes_touch" -> report.put("result",injection(true))
                "tester_render" -> report.put("result",testerRender())
                else -> error("Unknown suite")
            }
            report.put("status","PASS")
        } catch(error:Throwable) {
            result=Activity.RESULT_CANCELED
            report.put("status","FAIL").put("error",error.javaClass.simpleName+": "+error.message)
        }
        val destination=File(targetContext.filesDir,"validation/$suite.json");destination.parentFile!!.mkdirs();destination.writeText(report.toString(2))
        finish(result,Bundle().apply{putString("validation",report.toString())})
    }
    private fun testerRender():JSONObject {
        val activity=startActivitySync(Intent(targetContext,xtr.keymapper.tester.TesterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as xtr.keymapper.tester.TesterActivity
        try {
            repeat(300){if(activity.state.value.status!="Connected")SystemClock.sleep(20)}
            check(activity.state.value.status=="Connected"){"Supported controller needed for representative rendering"}
            fun command(text:String)=uiAutomation.executeShellCommand(text).use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use{r->r.readText()}}
            val connected=CountDownLatch(1);var remote:xtr.keymapper.IRemoteService?=null
            runOnMainSync{xtr.keymapper.server.RemoteServiceHelper.getInstance(targetContext){remote=it;connected.countDown()}}
            check(connected.await(10,TimeUnit.SECONDS)&&remote!=null)
            fun metrics()=JSONObject(remote!!.diagnostics())
            val submissions=JSONArray(metrics().getString("samples_total")).getLong(2)
            check(metrics().getBoolean("diagnostic_exclusive")&&!remote!!.isActive)
            command("dumpsys gfxinfo ${targetContext.packageName} reset")
            val done=CountDownLatch(1);val cpu=android.os.Process.getElapsedCpuTime();val start=SystemClock.uptimeMillis()
            activity.benchmarkRendering{done.countDown()};check(done.await(15,TimeUnit.SECONDS));SystemClock.sleep(300)
            val elapsed=SystemClock.uptimeMillis()-start;val cpuDelta=android.os.Process.getElapsedCpuTime()-cpu
            val gfx=command("dumpsys gfxinfo ${targetContext.packageName}")
            val lines=gfx.lineSequence().filter{it.startsWith("Total frames rendered:")||it.startsWith("Janky frames:")||it.matches(Regex("(?:50|90|95|99)th percentile:.*"))}.take(6).toList()
            check(JSONArray(metrics().getString("samples_total")).getLong(2)==submissions)
            return JSONObject().put("injection_submission_delta",0).put("source","Synthetic UI-state replay, visibly labeled; no controller event generation or hardware compatibility claim")
                .put("states_submitted",240).put("elapsed_ms",elapsed).put("app_cpu_ms",cpuDelta).put("app_pss_kb",android.os.Debug.getPss()).put("gfx_summary",JSONArray(lines))
        }finally{runOnMainSync{activity.finish()};SystemClock.sleep(500)}
    }
    private fun tester(heroProfiles:Boolean=false):JSONObject {
        val activity=startActivitySync(Intent(targetContext,xtr.keymapper.tester.TesterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as xtr.keymapper.tester.TesterActivity
        val ready=CountDownLatch(1);var remote:xtr.keymapper.IRemoteService?=null
        runOnMainSync{xtr.keymapper.server.RemoteServiceHelper.getInstance(targetContext){remote=it;ready.countDown()}}
        check(ready.await(15,TimeUnit.SECONDS)&&remote!=null)
        val backend=remote!!
        fun diagnostics()=JSONObject(backend.diagnostics())
        fun submissionCount()=JSONArray(diagnostics().getString("samples_total")).getLong(2)
        try {
            repeat(150){if(!diagnostics().optBoolean("diagnostic_exclusive"))SystemClock.sleep(20)}
            check(diagnostics().getBoolean("diagnostic_exclusive"))
            check(!backend.isActive && backend.isMappingPaused)
            val otherOwner=Binder();check(!backend.beginDiagnostics(otherOwner));backend.endDiagnostics(otherOwner)
            SystemClock.sleep(50);check(diagnostics().getBoolean("diagnostic_exclusive"))
            val before=submissionCount()
            // Attempt a real production start/resume while the tester lease is held.
            val profile=xtr.keymapper.keymap.ProfileConfiguration.parse("APPLICATION com.mobile.legends\nSCREENSIZE 1080 2280\nENABLED")
            val config=xtr.keymapper.keymap.KeymapConfig(targetContext).apply{disableAutoProfiling=false}
            val callback=object:xtr.keymapper.IRemoteServiceCallback.Stub(){
                override fun launchEditor(){};override fun alertMouseAimActivated(){};override fun switchProfiles(){}
                override fun enablePointer(){};override fun disablePointer(){};override fun setCursorX(x:Int){};override fun setCursorY(y:Int){}
                override fun requestKeymapProfile()=profile;override fun requestKeymapConfig()=config
            }
            if(heroProfiles){
                for(hero in xtr.keymapper.controller.HeroGameplayConfig.Hero.entries){
                    val draft=HeroTrainingProfiles.draft(hero)+"ENABLED"
                    val failure=runCatching{backend.startServer(KeymapProfiles.getProfile(draft),config,callback,1080,2280,0)}.exceptionOrNull()
                    check(failure is IllegalArgumentException){"Uncalibrated hero crossed Binder activation gate"}
                    val ready=draft.map{if(it.startsWith("HERO_GAMEPLAY "))it.dropLast(1)+"1"else it}.toSet()
                    backend.startServer(KeymapProfiles.getProfile(ready),config,callback,1080,2280,0)
                }
            }else backend.startServer(profile,config,callback,1080,2280,0)
            backend.resumeMouse();SystemClock.sleep(200)
            check(!backend.isActive && backend.isMappingPaused)
            File(targetContext.filesDir,"validation/tester-ready.json").writeText("{\"ready\":true}")
            val original=activity.requestedOrientation
            val beforeEvents=activity.state.value.eventCount
            val oldOrientation=activity.resources.configuration.orientation
            runOnMainSync{activity.requestedOrientation=if(oldOrientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            SystemClock.sleep(1000)
            check(!activity.isDestroyed && activity.resources.configuration.orientation!=oldOrientation)
            check(diagnostics().getBoolean("diagnostic_exclusive"))
            runOnMainSync{activity.requestedOrientation=original};SystemClock.sleep(1000)
            check(!activity.isDestroyed && activity.state.value.eventCount>=beforeEvents)
            val state=activity.state.value
            check(diagnostics().getBoolean("diagnostic_exclusive") && !backend.isActive)
            check(submissionCount()==before){"Injection submission occurred during tester"}
            val report=JSONObject().put("exclusive_root_lease",true).put("production_start_resume_blocked",true).put("injection_submission_delta",submissionCount()-before)
                .put("observed_android_event_history",state.eventCount).put("passed_checks",JSONArray(state.checks.filter{it.passed}.map{it.label})).put("applicable_checks",state.checks.size)
                .put("held_at_end",JSONArray(state.held.toList())).put("queue_overflows",state.dropped).put("source","Automated ownership/lifecycle only; no synthetic controller events generated").put("configuration_retained_activity_and_lease",true)
            val times=state.receiptToProcessNs.toLongArray();if(times.isNotEmpty())report.put("receipt_to_reducer",stats(times))
            val death=CountDownLatch(1);backend.asBinder().linkToDeath({death.countDown()},0)
            runOnMainSync{activity.finish()};check(death.await(5,TimeUnit.SECONDS))
            return report.put("hero_profiles_exclusivity_checked",heroProfiles).put("uncalibrated_heroes_rejected_across_binder",if(heroProfiles)2 else 0).put("root_exit_on_tester_exit",true).put("lease_released_on_exit",true).put("mapping_not_resumed",true)
        }finally{runOnMainSync{activity.finish()};runCatching{backend.stopServer()};File(targetContext.filesDir,"validation/tester-ready.json").delete()}
    }
    private fun prepareHeroes():JSONObject {
        val store=HeroLibraryStore(targetContext);val before=store.read()
        val mappings=KeymapProfiles(targetContext).sharedPref;val original=mappings.all.toMap()
        val journal=File(targetContext.filesDir,"validation/heroes-preparation-journal.json");journal.parentFile!!.mkdirs()
        journal.writeText(JSONObject().put("original_profile_ids",JSONArray(before.profiles.map{it.id})).put("original_mapping_keys",JSONArray(original.keys.toList())).toString(2))
        var created=0;val results=JSONArray()
        for(hero in xtr.keymapper.controller.HeroGameplayConfig.Hero.entries){
            val preset=if(hero==xtr.keymapper.controller.HeroGameplayConfig.Hero.BEATRIX)"s10e-g8-beatrix-training-v1"else"s10e-g8-kimmy-training-v1"
            val old=store.read().profiles.firstOrNull{it.preset==preset}
            val p=old?:store.clone(if(hero==xtr.keymapper.controller.HeroGameplayConfig.Hero.BEATRIX)"Beatrix training draft"else"Kimmy training draft",HeroTrainingProfiles.draft(hero),preset).also{created++}
            val parsed=KeymapProfiles(targetContext).getProfile(p.id,false)
            if(old==null)check(parsed.disabled&&!parsed.heroGameplay.calibrated)
            results.put(JSONObject().put("hero",hero.name).put("created",old==null).put("enabled",!parsed.disabled).put("calibrated",parsed.heroGameplay?.calibrated?:false).put("hero_policy_present",parsed.heroGameplay!=null))
        }
        check(before.favorites==store.read().favorites)
        check(before.profiles.all{it in store.read().profiles})
        check(original.all{(key,value)->mappings.all[key]==value})
        return JSONObject().put("created_profiles",created).put("templates",results).put("original_mapping_entries_preserved",original.size)
            .put("original_library_records_preserved",true).put("favorites_unchanged",true).put("activation_requested",false)
    }
    private fun stats(values:LongArray):JSONObject {
        values.sort();return JSONObject().put("n",values.size).put("p50_ns",values[(values.size*.5).toInt()])
            .put("p95_ns",values[(values.size*.95).toInt()]).put("p99_ns",values[(values.size*.99).toInt()])
    }
    private fun jni():JSONObject {
        check(RustMappingMath.available){"Rust ABI unavailable"}
        var vectors=0;var maxError=0f
        for(xi in -50..50)for(yi in -50..50)for(dz in floatArrayOf(0f,.15f,.9f))for(invert in listOf(false,true)) {
            val x=xi/50f;val y=yi/50f
            val expected=ControllerAimHandler.vector(x,y,dz,1.3f,.7f,invert)
            val actual=RustMappingMath.vectorPacked(x,y,dz,1.3f,.7f,invert)
            maxError=maxOf(maxError,kotlin.math.abs(expected[0]-RustMappingMath.x(actual)),kotlin.math.abs(expected[1]-RustMappingMath.y(actual)))
            check(maxError<=1e-6);vectors++
        }
        check(RustMappingMath.vectorPacked(Float.NaN,0f,.15f,1f,1f,false)==0L)
        var missing=false
        try {System.loadLibrary("mythic_validation_intentionally_missing")}catch(_:UnsatisfiedLinkError){missing=true}
        check(missing)
        var consumed=0.0
        repeat(20000){consumed+=ControllerAimHandler.vector((it%201-100)/100f,.3f,.15f,1f,1f,false)[0];consumed+=RustMappingMath.vectorPacked((it%201-100)/100f,.3f,.15f,1f,1f,false)}
        val java=LongArray(20000);val rust=LongArray(20000)
        repeat(java.size){val x=(it%201-100)/100f;val t=System.nanoTime();consumed+=ControllerAimHandler.vector(x,.3f,.15f,1f,1f,false)[0];java[it]=System.nanoTime()-t}
        repeat(rust.size){val x=(it%201-100)/100f;val t=System.nanoTime();consumed+=RustMappingMath.vectorPacked(x,.3f,.15f,1f,1f,false);rust[it]=System.nanoTime()-t}
        check(!consumed.isNaN())
        return JSONObject().put("abi","arm64-v8a").put("parity_vectors",vectors).put("max_abs_error",maxError.toDouble())
            .put("missing_library_error_caught",missing).put("invalid_input_neutral",true).put("java_reference_math",stats(java)).put("rust_jni_reference_math",stats(rust))
            .put("measurement","Device math microbenchmark; timer/sink overhead included; no physical display latency or hot-path migration")
    }
    private fun profiles(verify:Boolean):JSONObject {
        val store=HeroLibraryStore(targetContext);val marker=File(targetContext.filesDir,"validation/profile-fixtures-private.json")
        if(!verify) {
            check(!marker.exists()){ "Previous validation fixture needs cleanup first" }
            val before=store.read();val created=mutableListOf<String>()
            try {
                val preset=HeroCatalog.presets.first{it.id=="s10e-g8-tuned-v03-single-touch-r1"};val lines=HeroCatalog.lines(targetContext,preset)
                val a=store.clone("VALIDATION_ONLY Alpha",lines,preset.id);created+=a.id
                val b=store.clone("VALIDATION_ONLY Beta",lines,preset.id);created+=b.id
                val c=store.clone("VALIDATION_ONLY Duplicate",KeymapProfiles(targetContext).sharedPref.getStringSet(a.id,null)!!,preset.id);created+=c.id
                var current=store.read();store.update(current,current.assign(0,a.id).assign(1,b.id));current=store.read();store.update(current,current.swap(0,4));
                current=store.read();store.update(current,current.copy(profiles=current.profiles.map{if(it.id==a.id)it.copy(name="VALIDATION_ONLY Renamed")else it}))
                val expected=store.read();check(expected.favorites.size==5);check(expected.favorites[4]==a.id)
                marker.writeText(JSONObject().put("before",HeroLibraryCodec.encode(before)).put("expected",HeroLibraryCodec.encode(expected)).put("created",JSONArray(created)).toString())
                check(HeroCatalog.lines(targetContext,preset)==lines)
                for(bad in listOf("{","{\"version\":99,\"profiles\":[],\"favorites\":[null,null,null,null,null]}")){
                    var rejected=false;try{HeroLibraryCodec.decode(bad)}catch(_:Exception){rejected=true};check(rejected)
                }
                return JSONObject().put("created_disposable_profiles",3).put("favorite_slots",5).put("rename_duplicate_reorder",true).put("future_corrupt_rejected",true).put("preset_immutable",true)
            } catch(error:Throwable){created.forEach{runCatching{store.delete(it)}};val current=store.read();store.update(current,current.copy(favorites=before.favorites));throw error}
        }
        val data=JSONObject(marker.readText());val expected=HeroLibraryCodec.decode(data.getString("expected"));val before=HeroLibraryCodec.decode(data.getString("before"))
        check(store.read()==expected){"Profile persistence differed after process restart"}
        val ids=data.getJSONArray("created");repeat(ids.length()){store.delete(ids.getString(it))}
        val current=store.read();store.update(current,current.copy(favorites=before.favorites))
        check(store.read()==before){"Existing library not restored"};check(marker.delete())
        return JSONObject().put("restart_persistence",true).put("test_profiles_deleted",3).put("prior_library_restored",true)
    }
    private fun startup():JSONObject {
        val activity=startActivitySync(Intent(targetContext,xtr.keymapper.activity.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        check(Shell.getShell().isRoot){"App root consent unavailable"}
        val ready=CountDownLatch(1);var remote:xtr.keymapper.IRemoteService?=null
        runOnMainSync{xtr.keymapper.server.RemoteServiceHelper.getInstance(targetContext){remote=it;ready.countDown()}}
        check(ready.await(15,TimeUnit.SECONDS) && remote!=null){"Root backend not connected"}
        val metrics=JSONObject(remote!!.diagnostics());check(metrics.getInt("backend_uid")==0)
        val observed=CountDownLatch(1)
        val expected=java.util.concurrent.atomic.AtomicReference<String?>(null)
        val changed=java.util.concurrent.atomic.AtomicReference<CountDownLatch?>(null)
        val observer=object:xtr.keymapper.ActivityObserver.Stub(){override fun onForegroundActivitiesChanged(packageName:String?){
            observed.countDown();if(packageName==expected.get())changed.get()?.countDown()
        }}
        remote!!.registerActivityObserver(observer)
        val transition=observed.await(7,TimeUnit.SECONDS)
        check(remote!!.asBinder().isBinderAlive){"Root backend died during foreground observation"}
        check(transition){"Foreground observation unavailable"}
        val away=CountDownLatch(1);expected.set("com.android.settings");changed.set(away)
        val awayStart=System.nanoTime()
        runOnMainSync{activity.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))}
        check(away.await(8,TimeUnit.SECONDS)){"Settings transition not observed"}
        val awayNs=System.nanoTime()-awayStart
        val back=CountDownLatch(1);expected.set(targetContext.packageName);changed.set(back)
        val backStart=System.nanoTime()
        val controlled=startActivitySync(Intent(targetContext,ValidationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        check(back.await(8,TimeUnit.SECONDS)){"Return transition not observed"}
        val backNs=System.nanoTime()-backStart
        val mode=JSONObject(remote!!.diagnostics()).optString("foreground_observer","polling_5000ms")
        remote!!.unregisterActivityObserver(observer)
        runOnMainSync{controlled.finish();activity.finish()}
        return JSONObject().put("root_binder",true).put("root_uid",0).put("foreground_callback",transition).put("mapping_started",false)
            .put("foreground_observer",mode).put("launch_to_away_callback_ns",awayNs).put("launch_to_return_callback_ns",backNs)
    }
    private fun overlay():JSONObject {
        val activity=startActivitySync(Intent(targetContext,ValidationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ValidationActivity
        val store=HeroLibraryStore(targetContext);val before=store.read()
        val profile=store.clone("VALIDATION_ONLY Overlay",HeroCatalog.lines(targetContext,HeroCatalog.presets.first{it.id=="s10e-g8-tuned-v03-single-touch-r1"}),HeroCatalog.presets.first{it.id=="s10e-g8-tuned-v03-single-touch-r1"}.id)
        var current=store.read();store.update(current,current.assign(0,profile.id))
        val settings=targetContext.getSharedPreferences("settings",0)
        val hadAuto=settings.contains("disable_auto_profile");val oldAuto=settings.getBoolean("disable_auto_profile",true)
        settings.edit().putBoolean("disable_auto_profile",false).commit()
        val positions=targetContext.getSharedPreferences("mythic_overlay",0)
        val oldPositions=positions.all.toMap()
        val connected=CountDownLatch(1);var service:xtr.keymapper.TouchPointer?=null
        val connection=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName?,binder:IBinder?){service=(binder as xtr.keymapper.TouchPointer.TouchPointerBinder).service;connected.countDown()}
            override fun onServiceDisconnected(name:ComponentName?){service=null}
        }
        val intent=Intent(targetContext,xtr.keymapper.TouchPointer::class.java).putExtra(xtr.keymapper.editor.EditorActivity.PROFILE_NAME,profile.id)
        val lease=Any()
        try {
            check(Shell.getShell().isRoot)
            runOnMainSync{targetContext.bindService(intent,connection,Context.BIND_AUTO_CREATE);androidx.core.content.ContextCompat.startForegroundService(targetContext,intent)}
            check(connected.await(10,TimeUnit.SECONDS))
            fun field(target:Any,name:String):Any?=target.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(target)
            var floating:Any?=null
            repeat(100){if(floating==null){floating=field(service!!,"favoriteOverlay");SystemClock.sleep(50)}}
            check(floating!=null){"Floating selector unavailable"}
            val root=field(floating!!,"root") as android.widget.LinearLayout
            val header=field(floating!!,"header") as android.widget.Button
            val panel=field(floating!!,"panel") as android.widget.LinearLayout
            val scroll=field(floating!!,"panelScroll") as android.widget.ScrollView
            waitForIdleSync();check(root.isAttachedToWindow)
            runOnMainSync{header.performClick()};waitForIdleSync()
            check(scroll.visibility==android.view.View.VISIBLE)
            val slots=(0 until panel.childCount).map{panel.getChildAt(it)}.filterIsInstance<android.widget.Button>().count{it.text.toString().matches(Regex("[1-5]\\..*"))}
            check(slots==5){"Expected five favorite shortcuts"}
            val params=field(floating!!,"params") as android.view.WindowManager.LayoutParams
            val oldX=params.x
            runOnMainSync {
                val now=SystemClock.uptimeMillis()
                for((action,x,y) in listOf(Triple(0,20f,20f),Triple(2,140f,60f),Triple(1,140f,60f))) {
                    val event=android.view.MotionEvent.obtain(now,now,action,x,y,0);header.dispatchTouchEvent(event);event.recycle()
                }
            }
            check(params.x!=oldX){"Drag did not reposition selector"}
            runOnMainSync{xtr.keymapper.editor.EditorSessionGuard.acquire(lease)};waitForIdleSync()
            check(root.visibility==android.view.View.GONE)
            runOnMainSync{xtr.keymapper.editor.EditorSessionGuard.release(lease)};waitForIdleSync()
            check(root.visibility==android.view.View.VISIBLE)
            runOnMainSync{header.performClick()};check(scroll.visibility==android.view.View.GONE)
            val originalOrientation=activity.requestedOrientation
            runOnMainSync{activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            repeat(100){if(activity.surface.width<=activity.surface.height)SystemClock.sleep(20)}
            check(activity.surface.width>activity.surface.height){"Activity landscape transition failed"}
            SystemClock.sleep(1000)
            val screenWidth=activity.resources.displayMetrics.widthPixels
            val screenHeight=activity.resources.displayMetrics.heightPixels
            check(params.x>=0&&params.y>=0&&params.x<screenWidth&&params.y<screenHeight){"Selector outside rotated screen"}
            runOnMainSync{activity.requestedOrientation=originalOrientation}
            SystemClock.sleep(700)
            val notificationManager=targetContext.getSystemService(android.app.NotificationManager::class.java)
            var notifications=notificationManager.activeNotifications
            repeat(60){if(notifications.none{it.id==2}){SystemClock.sleep(50);notifications=notificationManager.activeNotifications}}
            check(notifications.any{it.id==2}){"Foreground notification missing; own IDs="+notifications.map{it.id}}
            val notificationFlags=notifications.first{it.id==2}.notification.flags
            check(service!!.mService?.isActive!=true){"Test must not activate a gameplay mapping"}
            var screenResult:JSONObject?=null
            if(suite=="screen") {
                fun key(code:Int){uiAutomation.executeShellCommand("input keyevent $code").use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use{stream->stream.readBytes()}}}
                val power=targetContext.getSystemService(android.os.PowerManager::class.java)
                key(223)
                repeat(100){if(power.isInteractive)SystemClock.sleep(20)}
                check(!power.isInteractive){"Screen did not turn off"}
                // Screen power and the asynchronous SCREEN_OFF broadcast complete separately.
                // Always wake again, including a failed teardown assertion.
                try {
                    repeat(150){if(root.isAttachedToWindow)SystemClock.sleep(20)}
                    check(!root.isAttachedToWindow){"Screen-off left selector attached after 3s"}
                } finally { key(224) }
                repeat(100){if(!power.isInteractive)SystemClock.sleep(20)}
                check(power.isInteractive){"Screen did not wake"}
                screenResult=JSONObject().put("screen_off_on",true).put("overlay_removed_on_screen_off",true)
                    .put("keyguard_locked_after_wake",targetContext.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
            }
            return JSONObject().put("screen",screenResult ?: JSONObject.NULL).put("foreground_service_notification",true).put("notification_flags",notificationFlags).put("attached",true).put("favorite_slots",slots)
                .put("expand_collapse",true).put("drag_reposition",true).put("editor_lease_hide_restore",true).put("activity_rotation_reclamp",true).put("gameplay_mapping_inactive",true)
        } finally {
            runOnMainSync{xtr.keymapper.editor.EditorSessionGuard.release(lease);targetContext.stopService(intent);runCatching{targetContext.unbindService(connection)};activity.finish()}
            current=store.read();store.update(current,current.copy(favorites=before.favorites));store.delete(profile.id)
            check(store.read()==before)
            val edit=settings.edit();if(hadAuto)edit.putBoolean("disable_auto_profile",oldAuto)else edit.remove("disable_auto_profile");edit.commit()
            val positionEdit=positions.edit().clear();oldPositions.forEach{(key,value)->if(value is Int)positionEdit.putInt(key,value)};positionEdit.commit()
        }
    }
    private fun readerHold():JSONObject {
        val activity=startActivitySync(Intent(targetContext,ValidationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        check(Shell.getShell().isRoot)
        val ready=CountDownLatch(1);var remote:xtr.keymapper.IRemoteService?=null
        runOnMainSync{xtr.keymapper.server.RemoteServiceHelper.getInstance(targetContext){remote=it;ready.countDown()}}
        check(ready.await(15,TimeUnit.SECONDS)&&remote!=null)
        val connection=remote!!;check(!connection.isActive)
        repeat(100){if(!JSONObject(connection.diagnostics()).getBoolean("reader_running"))SystemClock.sleep(20)}
        check(JSONObject(connection.diagnostics()).getBoolean("reader_running"))
        check(JSONObject(connection.diagnostics()).getInt("logical_active_touches")==0)
        val death=CountDownLatch(1);connection.asBinder().linkToDeath({death.countDown()},0)
        val marker=File(targetContext.filesDir,"validation/reader-ready.json")
        marker.writeText(JSONObject().put("mapping_active",false).put("logical_touches",0).put("reader_running",true).toString())
        return try {
            val killed=death.await(30,TimeUnit.SECONDS)
            if(!killed)connection.stopServer()
            check(killed){"Controlled process termination was not performed"}
            JSONObject().put("root_binder_death_observed",true).put("mapping_inactive_before_termination",true).put("held_touch_kill_tested",false)
        } finally {marker.delete();runOnMainSync{activity.finish()}}
    }
    private fun runtimeLifecycle():JSONObject {
        val activity=startActivitySync(Intent(targetContext,ValidationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ValidationActivity
        check(Shell.getShell().isRoot)
        val ready=CountDownLatch(1);var candidate:xtr.keymapper.IRemoteService?=null
        runOnMainSync{xtr.keymapper.server.RemoteServiceHelper.getInstance(targetContext){candidate=it;ready.countDown()}}
        check(ready.await(15,TimeUnit.SECONDS)&&candidate!=null)
        val remote=candidate!!
        val profile=xtr.keymapper.keymap.ProfileConfiguration.parse("APPLICATION com.mobile.legends\nSCREENSIZE ${activity.surface.width} ${activity.surface.height}\nENABLED")
        check(profile.keys.isEmpty()&&profile.aimKeys.isEmpty())
        val config=xtr.keymapper.keymap.KeymapConfig(null).apply{disableAutoProfiling=false;pointerMode=xtr.keymapper.keymap.KeymapConfig.POINTER_SYSTEM;touchpadInputMode=xtr.keymapper.keymap.KeymapConfig.TOUCHPAD_DISABLED}
        val callback=object:xtr.keymapper.IRemoteServiceCallback.Stub(){
            override fun launchEditor(){};override fun alertMouseAimActivated(){};override fun switchProfiles(){}
            override fun enablePointer(){};override fun disablePointer(){};override fun setCursorX(x:Int){};override fun setCursorY(y:Int){}
            override fun requestKeymapProfile()=profile
            override fun requestKeymapConfig()=config
        }
        fun await(checker:()->Boolean){repeat(100){if(!checker())SystemClock.sleep(20)};check(checker())}
        val death=CountDownLatch(1);remote.asBinder().linkToDeath({death.countDown()},0)
        try {
            check(activity.guard.ready())
            remote.startServer(profile,config,callback,activity.surface.width,activity.surface.height,0)
            await{remote.isActive};remote.pauseMouse();await{remote.isMappingPaused}
            remote.resumeMouse();await{!remote.isMappingPaused};remote.reloadKeymap();SystemClock.sleep(100)
            remote.startServer(profile,config,callback,activity.surface.width,activity.surface.height,0);await{remote.isActive}
            val metrics=JSONObject(remote.diagnostics());check(metrics.getInt("logical_active_touches")==0&&metrics.getLong("injection_errors")==0L)
            remote.stopServer();check(death.await(5,TimeUnit.SECONDS))
            return JSONObject().put("production_binder_start_pause_resume_reload_replace_stop",true).put("empty_mapping_only",true)
                .put("no_game_launch",true).put("root_metrics",metrics).put("root_exit_observed",true)
        } finally {runCatching{remote.stopServer()};runOnMainSync{activity.finish()}}
    }
    private fun injection(heroes:Boolean=false):JSONObject {
        val activity=startActivitySync(Intent(targetContext,ValidationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ValidationActivity
        val connected=CountDownLatch(1);var runner:IValidationRunner?=null
        val connection=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName?,binder:IBinder?){runner=IValidationRunner.Stub.asInterface(binder);connected.countDown()}
            override fun onServiceDisconnected(name:ComponentName?){runner=null}
        }
        try {
            repeat(100){if(!activity.guard.ready())SystemClock.sleep(50)}
            check(activity.guard.ready()){ "Controlled surface not focused" }
            check(Shell.getShell().isRoot){"App root consent unavailable"}
            runOnMainSync{RootService.bind(Intent(targetContext,ValidationRootService::class.java),connection)}
            check(connected.await(15,TimeUnit.SECONDS)){"Validation root Binder unavailable"}
            val root=JSONObject(if(heroes)runner!!.runHeroes(activity.guard)else runner!!.run(activity.guard));check(root.getString("status")=="PASS"){root.toString()}
            SystemClock.sleep(500)
            val observed=activity.surface.result()
            check(observed.getInt("observed_events")>=20 && observed.getInt("max_pointers")== (if(heroes)7 else 3) && observed.getInt("active_pointers")==0 && observed.getInt("sequence_errors")==0){observed.toString()}
            return JSONObject().put("root",root).put("surface",observed)
        } finally {
            runOnMainSync{runCatching{RootService.unbind(connection)};activity.finish()}
        }
    }
}
