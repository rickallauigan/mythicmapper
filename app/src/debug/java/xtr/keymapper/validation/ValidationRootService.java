// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.validation;

import android.app.ActivityManager;
import android.app.IActivityManager;
import android.content.Intent;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.view.MotionEvent;
import com.topjohnwu.superuser.ipc.RootService;
import org.json.JSONObject;
import java.lang.reflect.Proxy;
import java.util.*;
import xtr.keymapper.IRemoteServiceCallback;
import xtr.keymapper.controller.ControllerDeviceMonitor;
import xtr.keymapper.keymap.*;
import xtr.keymapper.server.*;

/** Debug-only fixed protocol. Never reads or modifies controller events or game data. */
public class ValidationRootService extends RootService {
    private final IValidationRunner.Stub binder=new IValidationRunner.Stub() {
        @Override public String run(IValidationSurface surface) {
            try { return exercise(surface).toString(); }
            catch(Exception error) {
                try { return new JSONObject().put("status","FAIL").put("error",error.getClass().getSimpleName()+": "+error.getMessage()).toString(); }
                catch(Exception ignored){return "{}";}
            }
        }
        @Override public String runHeroes(IValidationSurface surface) {
            try{return exerciseHeroes(surface).toString();}
            catch(Exception error){return "{\"status\":\"FAIL\",\"error\":\"Hero safe-surface replay failed: "+error.getClass().getSimpleName()+"\"}";}
        }
    };
    @Override public IBinder onBind(Intent intent){return binder;}
    private void requireSurface(IValidationSurface surface)throws Exception {
        var tasks=getSystemService(ActivityManager.class).getRunningTasks(1);
        if(!surface.ready() || tasks.isEmpty() || tasks.get(0).topActivity==null ||
            !tasks.get(0).topActivity.getClassName().equals(ValidationActivity.class.getName()) ||
            !tasks.get(0).topActivity.getPackageName().equals(getPackageName()))
            throw new SecurityException("Controlled validation surface is not focused");
    }
    private int scrollThreads(){int count=0;for(Thread thread:Thread.getAllStackTraces().keySet())if(thread.isAlive()&&thread.getName().equals("scroll"))count++;return count;}
    private JSONObject resources()throws Exception {
        JSONObject value=new JSONObject().put("cpu_ms",android.os.Process.getElapsedCpuTime()).put("elapsed_ns",System.nanoTime())
            .put("pss_kb",android.os.Debug.getPss()).put("managed_heap_bytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
        for(String key:new String[]{"art.gc.gc-count","art.gc.bytes-allocated","art.gc.gc-time"}) {
            String raw=android.os.Debug.getRuntimeStat(key);
            value.put(key,raw!=null&&raw.matches("[0-9]+")?Long.parseLong(raw):JSONObject.NULL);
        }
        return value;
    }
    private JSONObject resourceDelta(JSONObject before)throws Exception {
        JSONObject after=resources(),out=new JSONObject();
        for(String key:new String[]{"cpu_ms","elapsed_ns","art.gc.gc-count","art.gc.bytes-allocated","art.gc.gc-time"})
            out.put(key,before.isNull(key)||after.isNull(key)?JSONObject.NULL:after.getLong(key)-before.getLong(key));
        return out.put("pss_kb_after",after.getLong("pss_kb")).put("managed_heap_bytes_after",after.getLong("managed_heap_bytes"));
    }
    private interface MainStep { void run() throws Exception; }
    private void onMain(MainStep step)throws Exception {
        var done=new java.util.concurrent.CountDownLatch(1);var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{try{step.run();}catch(Throwable e){failure.set(e);}finally{done.countDown();}});
        if(!done.await(8,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Hero replay timed out");
        if(failure.get()!=null)throw new IllegalStateException("Hero replay failed",failure.get());
    }
    /** Fixed synthetic controller replay into the same focused, purchase-free blue surface. */
    private JSONObject exerciseHeroes(IValidationSurface surface)throws Exception {
        requireSurface(surface);int width=surface.width(),height=surface.height();
        if(width<300||height<300)throw new IllegalStateException("Surface not laid out");
        var config=new KeymapConfig(null);config.pointerMode=KeymapConfig.POINTER_OVERLAY;
        int actors=0;
        for(String variant:List.of("BEATRIX_HOLD","BEATRIX_PULSE","KIMMY")){
            var hero=xtr.keymapper.controller.HeroGameplayConfig.Hero.valueOf(variant.startsWith("BEATRIX")?"BEATRIX":"KIMMY");
            String lines=String.join("\n",xtr.keymapper.mythic.HeroTrainingProfiles.INSTANCE.draft(hero))
                .replace("HOLD_RELEASE 0 0",variant.equals("BEATRIX_PULSE")?"PULSE 150 1":"HOLD_RELEASE 0 1")+"\nENABLED";
            var callback=(IRemoteServiceCallback)Proxy.newProxyInstance(getClassLoader(),new Class[]{IRemoteServiceCallback.class},
                (p,m,a)->switch(m.getName()){case "requestKeymapProfile"->ProfileConfiguration.parse(lines);case "requestKeymapConfig"->config;default->null;});
            TouchBackend guarded=new TouchBackend(){
                final Input delegate=new Input(0);
                public void injectTouch(int action,int pointer,float pressure,float x,float y){
                    if(action!=MotionEvent.ACTION_UP)try{requireSurface(surface);}catch(Exception e){throw new SecurityException("Safe surface lost",e);}
                    delegate.injectTouch(action,pointer,pressure,x,y);
                }
                public boolean hasSubmissionFailure(){return delegate.hasSubmissionFailure();}
            };
            final InputService[] runtime={null};final ControllerDeviceMonitor[] monitor={null};
            Map<String,long[]> nodes=new HashMap<>();nodes.put("hero-replay",new long[]{1,2,0,255,128,0,255,128});
            try{
                onMain(()->{
                    requireSurface(surface);runtime[0]=new InputService(ProfileConfiguration.parse(lines),config,callback,width,height,null,true,0,guarded,true);
                    monitor[0]=new ControllerDeviceMonitor(runtime[0],width,height,()->new HashMap<>(nodes));
                    var field=InputService.class.getDeclaredField("controllers");field.setAccessible(true);field.set(runtime[0],monitor[0]);monitor[0].start();
                });
                String[] packets=variant.equals("BEATRIX_HOLD")?new String[]{"EV_KEY BTN_TL2 DOWN","EV_ABS ABS_RX ff","EV_SYN SYN_REPORT 0","EV_KEY BTN_TR DOWN","EV_KEY BTN_TR2 DOWN","EV_KEY BTN_TL DOWN","EV_KEY BTN_EAST DOWN","EV_KEY BTN_THUMBL DOWN","EV_KEY BTN_GAMEPAD DOWN","EV_KEY BTN_WEST DOWN","EV_KEY BTN_EAST UP","EV_KEY BTN_THUMBL UP"}
                    :variant.equals("BEATRIX_PULSE")?new String[]{"EV_KEY BTN_TL2 DOWN","EV_ABS ABS_RX ff","EV_SYN SYN_REPORT 0"}
                    :new String[]{"EV_ABS ABS_RX ff","EV_SYN SYN_REPORT 0","EV_KEY BTN_TR DOWN","EV_KEY BTN_TL DOWN","EV_KEY BTN_TR UP","EV_KEY BTN_TL UP","EV_KEY BTN_EAST DOWN","EV_KEY BTN_THUMBL DOWN","EV_KEY BTN_EAST UP","EV_KEY BTN_THUMBL UP"};
                for(String packet:packets){onMain(()->{requireSurface(surface);monitor[0].event("hero-replay",packet);});SystemClock.sleep(16);}
                SystemClock.sleep(180);
                onMain(()->{requireSurface(surface);runtime[0].reloadKeymap();});
                SystemClock.sleep(200);
                onMain(()->{runtime[0].pauseResumeKeymap();if(!runtime[0].isDiagnosticSafe())throw new IllegalStateException("Hero runtime not released/healthy");});
                if(guarded.hasSubmissionFailure())throw new IllegalStateException("Platform rejected hero gesture");
                actors++;
            }finally{onMain(()->{if(monitor[0]!=null)monitor[0].stop();if(runtime[0]!=null)runtime[0].stop();});}
        }
        return new JSONObject().put("status","PASS").put("uid",android.os.Process.myUid()).put("hero_touch_policies",actors)
            .put("source","Deterministic hero engine replay into guarded blue surface; not physical controller input or MLBB gameplay")
            .put("reload_and_pulse_cleanup",true).put("runtime_metrics",new JSONObject(RuntimeMetrics.snapshot()));
    }
    private JSONObject exercise(IValidationSurface surface)throws Exception {
        requireSurface(surface);
        final int width=surface.width(),height=surface.height();
        if(width<300||height<300)throw new IllegalStateException("Surface not laid out");
        var config=new KeymapConfig(null);config.pointerMode=KeymapConfig.POINTER_OVERLAY;
        String lines="APPLICATION com.mobile.legends\nSCREENSIZE "+width+" "+height+"\nENABLED\nBTN_TL2 "+width*.4f+" "+height*.4f+" 0\n"
            +"STICK_AIM BTN_TR "+width*.6f+" "+height*.4f+" 40 1 1 .15 0\n"
            +"STICK_AIM BTN_TR2 "+width*.5f+" "+height*.6f+" 40 1 1 .15 0\n";
        var callback=(IRemoteServiceCallback)Proxy.newProxyInstance(getClassLoader(),new Class[]{IRemoteServiceCallback.class},
            (p,m,a)->switch(m.getName()){case "requestKeymapProfile"->ProfileConfiguration.parse(lines);case "requestKeymapConfig"->config;default->null;});
        int scrollBefore=scrollThreads();
        System.loadLibrary("controller_device");
        var discovery=new ControllerDeviceMonitor.NodeDiscovery();
        var actual=discovery.get();
        JSONObject controller=new JSONObject().put("accepted_controller_interfaces",actual.size());
        if(actual.size()==1) {
            long[] info=actual.values().iterator().next();
            controller.put("rx_min",info[2]).put("rx_max",info[3]).put("rx_center_observed",info[4])
                .put("ry_min",info[5]).put("ry_max",info[6]).put("ry_center_observed",info[7]);
        }
        long scanStart=System.nanoTime();for(int i=0;i<20;i++)discovery.get();
        controller.put("cached_scan_average_ns",(System.nanoTime()-scanStart)/20);
        TouchBackend guarded=new TouchBackend() {
            private final Input delegate=new Input(0);
            @Override public void injectTouch(int action,int pointer,float pressure,float x,float y) {
                if(action!=MotionEvent.ACTION_UP) try {requireSurface(surface);}catch(Exception error){throw new SecurityException("Surface lost focus",error);}
                delegate.injectTouch(action,pointer,pressure,x,y);
            }
            @Override public boolean hasSubmissionFailure(){return delegate.hasSubmissionFailure();}
        };
        JSONObject idleStart=resources();SystemClock.sleep(500);JSONObject idle=resourceDelta(idleStart);
        JSONObject workloadStart=resources();
        InputService runtime=null;int operations=0;
        long[] costs=new long[600];int samples=0;
        try {
            runtime=new InputService(ProfileConfiguration.parse(lines),config,callback,width,height,null,true,0,guarded,true);
            JSONObject normalStart=resources();
            for(int fingers=1;fingers<=3;fingers++) {
                requireSurface(surface);
                for(int i=0;i<fingers;i++){long t=System.nanoTime();runtime.injectEvent(width*.4f+i*60,height*.4f,1,i);costs[samples++]=System.nanoTime()-t;operations++;SystemClock.sleep(8);}
                for(int i=0;i<fingers;i++){runtime.injectEvent(width*.4f+i*60+10,height*.4f+10,2,i);operations++;SystemClock.sleep(8);}
                for(int i=fingers-1;i>=0;i--){runtime.injectEvent(width*.4f+i*60+10,height*.4f+10,0,i);operations++;SystemClock.sleep(8);}
            }
            JSONObject normal=resourceDelta(normalStart),rapidStart=resources();
            for(int i=0;i<200;i++) {
                requireSurface(surface);
                long t=System.nanoTime();runtime.injectEvent(width*.5f,height*.5f,1,0);runtime.injectEvent(width*.5f,height*.5f,0,0);
                costs[samples++]=System.nanoTime()-t;operations+=2;SystemClock.sleep(4);
            }
            JSONObject rapid=resourceDelta(rapidStart);
            Map<String,long[]> nodes=new HashMap<>();nodes.put("replay",new long[]{1,2,0,255,128,0,255,128});
            var monitor=new ControllerDeviceMonitor(runtime,width,height,()->new HashMap<>(nodes));
            monitor.start();
            requireSurface(surface);
            long start=System.nanoTime();
            var main=new android.os.Handler(android.os.Looper.getMainLooper());
            try {
                for(int round=0;round<20;round++) {
                    requireSurface(surface);
                    String[] packets={"EV_KEY BTN_TL2 DOWN","EV_KEY BTN_TR DOWN","EV_KEY BTN_TR2 DOWN",
                        "EV_ABS ABS_RX 000000ff","EV_SYN SYN_REPORT 00000000","EV_SYN SYN_DROPPED 00000000"};
                    var done=new java.util.concurrent.CountDownLatch(packets.length);
                    var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
                    for(String packet:packets) {
                        long receipt=System.nanoTime();
                        main.post(()->{
                            long dispatch=System.nanoTime();RuntimeMetrics.record(0,dispatch-receipt);
                            try{monitor.event("replay",packet);}catch(Throwable error){failure.set(error);}
                            finally{RuntimeMetrics.record(1,System.nanoTime()-dispatch);done.countDown();}
                        });
                    }
                    if(!done.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Replay dispatch timed out");
                    if(failure.get()!=null)throw new IllegalStateException("Replay failed",failure.get());
                    SystemClock.sleep(8);
                }
            } finally {monitor.stop();}
            long replay=System.nanoTime()-start;
            requireSurface(surface);
            runtime.injectEvent(width*.5f,height*.5f,1,0);runtime.reloadKeymap();SystemClock.sleep(20);
            runtime.injectEvent(width*.5f,height*.5f,1,0);runtime.stop();SystemClock.sleep(20);
            runtime=new InputService(ProfileConfiguration.parse(lines),config,callback,width,height,null,true,0,guarded,true);
            runtime.injectEvent(width*.5f,height*.5f,1,0);runtime.injectEvent(width*.5f,height*.5f,0,0);SystemClock.sleep(20);
            runtime.stop();
            TouchBackend rejected=new TouchBackend() {
                private volatile boolean failed;
                @Override public void injectTouch(int action,int id,float pressure,float x,float y){if(action==MotionEvent.ACTION_DOWN)failed=true;}
                @Override public boolean hasSubmissionFailure(){return failed;}
            };
            runtime=new InputService(ProfileConfiguration.parse(lines),config,callback,width,height,null,true,0,rejected,true);
            runtime.injectEvent(width*.5f,height*.5f,1,0);
            for(int i=0;i<100&&!runtime.isPausedAndReleased();i++)SystemClock.sleep(5);
            if(!runtime.isPausedAndReleased())throw new IllegalStateException("Rejected backend did not stop/release ledger");
            runtime.stop();runtime=new InputService(ProfileConfiguration.parse(lines),config,callback,width,height,null,true,0,guarded,true);
            runtime.injectEvent(width*.5f,height*.5f,1,0);runtime.injectEvent(width*.5f,height*.5f,0,0);
            Arrays.sort(costs,0,samples);
            return new JSONObject().put("status","PASS").put("uid",android.os.Process.myUid()).put("protocol_operations",operations)
                .put("pair_or_down_samples",samples).put("pair_or_down_p50_ns",costs[(int)(samples*.5)])
                .put("pair_or_down_p95_ns",costs[(int)(samples*.95)]).put("pair_or_down_p99_ns",costs[(int)(samples*.99)])
                .put("simulated_backend_rejection_and_fresh_runtime_recovery",true).put("controller_packet_source","deterministic replay; physical probe reported separately")
                .put("resources",new JSONObject().put("idle_500ms",idle).put("normal_gestures",normal).put("rapid_pairs",rapid).put("entire_workload",resourceDelta(workloadStart)))
                .put("scroll_threads_before",scrollBefore).put("scroll_threads_after",scrollThreads()).put("physical_controller_probe",controller)
                .put("replay_with_waits_ns",replay).put("runtime_metrics",new JSONObject(RuntimeMetrics.snapshot()));
        } finally {if(runtime!=null)runtime.stop();}
    }
}
