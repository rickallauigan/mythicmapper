package xtr.keymapper.server;

import android.os.Handler;
import android.os.Looper;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import xtr.keymapper.IRemoteServiceCallback;
import xtr.keymapper.controller.ControllerDeviceMonitor;
import xtr.keymapper.keymap.*;
import xtr.keymapper.server.event.RepeatKeyHandler;
import static org.junit.Assert.*;

/** Host-only: real dispatch/ledger/MotionEvent code; fake injection endpoint, discovery and clock. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ConcurrentControllerInputTest {
    record Frame(int action, int index, List<Integer> ids, List<Float> xs,
                 List<Float> ys, List<Float> pressures, int mode, int source, long down, long time) {}
    public static class Recorder {
        final List<Frame> frames = new ArrayList<>();
        public boolean inject(InputEvent input, int mode) {
            // Never assert here: reflection failures are caught by production Input.
            MotionEvent e = (MotionEvent) input;
            List<Integer> ids = new ArrayList<>();
            List<Float> xs = new ArrayList<>(), ys = new ArrayList<>(), pressures = new ArrayList<>();
            for (int i=0;i<e.getPointerCount();i++) {
                ids.add(e.getPointerId(i)); xs.add(e.getX(i)); ys.add(e.getY(i));
                pressures.add(e.getPressure(i));
            }
            frames.add(new Frame(e.getActionMasked(),e.getActionIndex(),ids,xs,ys,pressures,
                mode,e.getSource(),e.getDownTime(),e.getEventTime()));
            return true;
        }
        void validLifecycle() {
            assertFalse("Recorder must observe emitted events",frames.isEmpty());
            Set<Integer> live = new HashSet<>(); long down=-1, previous=-1;
            for (Frame f:frames) {
                assertEquals(0,f.mode); assertEquals(android.view.InputDevice.SOURCE_TOUCHSCREEN,f.source);
                for(int i=0;i<f.ids.size();i++) {
                    assertTrue(f.ids.get(i)>=0 && f.ids.get(i)<32);
                    assertTrue(Float.isFinite(f.xs.get(i))); assertTrue(Float.isFinite(f.ys.get(i)));
                    if(f.action!=MotionEvent.ACTION_UP && !(f.action==MotionEvent.ACTION_POINTER_UP && i==f.index))
                        assertTrue("Held pointer must have pressure",f.pressures.get(i)>0);
                }
                assertEquals(f.ids.size(),new HashSet<>(f.ids).size());
                assertTrue(f.index>=0 && f.index<f.ids.size()); assertTrue(f.ids.size()<=10);
                assertTrue(f.time>=previous); previous=f.time;
                int id=f.ids.get(f.index);
                switch(f.action) {
                    case MotionEvent.ACTION_DOWN -> { assertTrue(live.isEmpty()); down=f.down; assertTrue(live.add(id)); }
                    case MotionEvent.ACTION_POINTER_DOWN -> { assertFalse(live.isEmpty()); assertTrue(live.add(id)); }
                    case MotionEvent.ACTION_MOVE -> assertFalse(live.isEmpty());
                    case MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                        assertEquals(live,new HashSet<>(f.ids)); assertTrue(live.remove(id));
                        if(f.action==MotionEvent.ACTION_UP) assertTrue(live.isEmpty());
                        else assertFalse(live.isEmpty());
                    }
                    default -> fail("Unexpected touch action "+f.action);
                }
                if(f.action!=MotionEvent.ACTION_UP && f.action!=MotionEvent.ACTION_POINTER_UP)
                    assertEquals(live,new HashSet<>(f.ids));
                assertEquals(down,f.down); assertTrue(f.time>=f.down);
            }
            assertTrue("No stuck fingers",live.isEmpty());
        }
    }
    final class Harness implements AutoCloseable {
        final Recorder recorder=new Recorder();
        final java.lang.reflect.Method originalMethod=Input.injectInputEventMethod;
        final java.lang.reflect.Method originalDisplay=Input.setDisplayIdMethod;
        final Object originalManager=Input.inputManager;
        final InputService runtime;
        final ControllerDeviceMonitor monitor;
        KeymapProfile reloadProfile;
        final Map<String,long[]> nodes=new HashMap<>(Map.of("pad",new long[]{1,2,0,255,128,0,255,128},"other",new long[]{3,4,0,255,128,0,255,128}));
        Harness() throws Exception {this(Files.readString(Path.of("../profiles/mlbb/samsung-s10e/mlbb-gamesir-g8-v0.3-tuned.txt")));}
        Harness(String text) throws Exception {
            Input.injectInputEventMethod=Recorder.class.getMethod("inject",InputEvent.class,int.class);
            Input.inputManager=recorder; Input.setDisplayIdMethod=null;
            KeymapConfig config=new KeymapConfig(null);config.pointerMode=KeymapConfig.POINTER_OVERLAY;
            reloadProfile=ProfileConfiguration.parse(text);
            IRemoteServiceCallback callback=(IRemoteServiceCallback)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{IRemoteServiceCallback.class},(p,m,a)->switch(m.getName()){
                case "requestKeymapProfile" -> reloadProfile;
                case "requestKeymapConfig" -> config;
                default -> null;
            });
            // Read existing committed fixture; no invented gameplay coordinates.
            runtime=new InputService(reloadProfile,config,callback,2280,1080,null,true,0);
            // Keep production repeat state machine but make its scheduler deterministic on the host.
            ReflectionHelpers.setField(runtime.getKeyEventHandler(),"repeatKeys",new RepeatKeyHandler(new Handler(Looper.getMainLooper()),runtime::injectEvent));
            monitor=new ControllerDeviceMonitor(runtime,2280,1080,()->new HashMap<>(nodes));
            monitor.start();
        }
        void key(String code,boolean down) { assertTrue(monitor.event("pad","EV_KEY "+code+(down?" DOWN":" UP"))); }
        void tick(long ms) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)); }
        void axis(String code,String value) { monitor.event("pad","EV_ABS "+code+" "+value); monitor.event("pad","EV_SYN SYN_REPORT 00000000"); }
        Map<Integer,float[]> live() { return ReflectionHelpers.getField(runtime,"activeTouches"); }
        int attackPointer() {
            var keys=runtime.getKeymapProfile().keys;
            return keys.indexOf(keys.stream().filter(k->k.code.equals("BTN_TL2")).findFirst().orElseThrow());
        }
        @Override public void close() {
            try { monitor.stop(); runtime.stop(); }
            finally {
                Input.injectInputEventMethod=originalMethod; Input.setDisplayIdMethod=originalDisplay;
                Input.inputManager=originalManager;
            }
        }
    }
    @Test public void ltFirstRepeatsWhileEachSkillMovesAndReleasesWithoutLosingOwnership() throws Exception {
        for(String skill:List.of("BTN_TR","BTN_TR2","BTN_TL")) try(Harness h=new Harness()) {
            h.key("BTN_TL2",true); h.key(skill,true);
            int skillPointer=h.live().keySet().stream().filter(p->p>=50&&p<58).findFirst().orElseThrow();
            float originalX=h.live().get(skillPointer)[0];
            int originalFrames=h.recorder.frames.size();
            Frame beforeMove=h.recorder.frames.get(originalFrames-1);
            int skillIndex=beforeMove.xs.indexOf(originalX); assertTrue(skillIndex>=0);
            int skillLocalId=beforeMove.ids.get(skillIndex);
            h.axis("ABS_RX","000000ff");
            assertTrue("Skill pointer actually moved",h.live().get(skillPointer)[0]>originalX);
            Frame moved=h.recorder.frames.get(h.recorder.frames.size()-1);
            assertEquals(MotionEvent.ACTION_MOVE,moved.action);
            assertTrue(moved.ids.contains(skillLocalId));
            assertEquals(h.live().get(skillPointer)[0],moved.xs.get(moved.ids.indexOf(skillLocalId)),0.001f);
            for(int i=0;i<5;i++) {
                h.tick(40);assertFalse("LT pulse releases without releasing skill",h.live().containsKey(h.attackPointer()));
                assertTrue(h.live().containsKey(skillPointer));
                h.tick(110);assertTrue("Next LT pulse starts",h.live().containsKey(h.attackPointer()));
                assertTrue(h.live().containsKey(skillPointer));
                Frame pulse=h.recorder.frames.get(h.recorder.frames.size()-1);
                assertTrue("Skill local ID survives LT pointer reuse",pulse.ids.contains(skillLocalId));
                assertEquals(h.live().get(skillPointer)[0],pulse.xs.get(pulse.ids.indexOf(skillLocalId)),0.001f);
            }
            assertTrue(h.recorder.frames.size()>=originalFrames+11);
            h.axis("ABS_RX","00000080"); // Neutral camera input before testing quiet LT cancellation.
            h.key(skill,false);assertFalse(h.live().containsKey(skillPointer));
            h.key("BTN_TL2",false);int count=h.recorder.frames.size();h.tick(500);assertEquals(count,h.recorder.frames.size());
            assertTrue(h.recorder.frames.stream().anyMatch(f->f.ids.size()==2));h.recorder.validLifecycle();
        }
    }
    @Test public void skillFirstSurvivesBothRepeatPhasesAndLtReleaseThenEmitsUp() throws Exception {
        for(String skill:List.of("BTN_TR","BTN_TR2","BTN_TL")) for(int phase:List.of(20,60)) try(Harness h=new Harness()) {
            h.key(skill,true);h.key("BTN_TL2",true);h.tick(phase);
            h.key("BTN_TL2",false);assertEquals(1,h.live().size());int count=h.recorder.frames.size();h.tick(500);assertEquals(count,h.recorder.frames.size());
            h.axis("ABS_RX","000000ff");assertTrue(h.recorder.frames.size()>count);
            h.key(skill,false);assertTrue(h.live().isEmpty());h.recorder.validLifecycle();
        }
    }
    @Test public void nativeLeftAxesDoNotGenerateTouchOrSuppressMappedSkillsDuringLt() throws Exception {
        try(Harness h=new Harness()) {
            h.key("BTN_TL2",true);
            for(int i=0;i<20;i++) {
                int count=h.recorder.frames.size();
                h.axis("ABS_X",i%2==0?"000000ff":"00000000");h.axis("ABS_Y","00000080");
                assertEquals(count,h.recorder.frames.size()); // Does NOT prove native/game delivery.
                h.tick(20);
            }
            assertTrue("LT continued pulsing during axis traffic",h.recorder.frames.size()>=6);
            h.key("BTN_TR",true);assertTrue(h.live().keySet().stream().anyMatch(p->p>=50&&p<58));
            h.key("BTN_TL2",false);h.key("BTN_TR",false);h.recorder.validLifecycle();
        }
    }
    @Test public void secondControllerCannotStealHeldPointersAndResetCancelsRepeat() throws Exception {
        try(Harness h=new Harness()) {
            h.key("BTN_TL2",true);h.key("BTN_TR",true);int count=h.recorder.frames.size();
            h.monitor.event("other","EV_KEY BTN_TR UP");assertEquals(count,h.recorder.frames.size());assertEquals(2,h.live().size());
            h.monitor.stop();h.runtime.stop();assertTrue(h.live().isEmpty());count=h.recorder.frames.size();h.tick(500);assertEquals(count,h.recorder.frames.size());h.recorder.validLifecycle();
        }
    }
    @Test public void allThreeSkillsStayHeldAcrossLtPulsesAndNonLifoRelease() throws Exception {
        try(Harness h=new Harness()) {
            h.key("BTN_TL2",true);
            for(String skill:List.of("BTN_TR","BTN_TR2","BTN_TL")) h.key(skill,true);
            assertEquals(4,h.live().size());
            Set<Integer> skillPointers=new HashSet<>(h.live().keySet());skillPointers.removeIf(p->p<50||p>=58);
            assertEquals(3,skillPointers.size());h.tick(340);assertTrue(h.live().keySet().containsAll(skillPointers));
            h.key("BTN_TR2",false);assertEquals(2,h.live().keySet().stream().filter(p->p>=50&&p<58).count());
            h.key("BTN_TL2",false);h.key("BTN_TR",false);h.key("BTN_TL",false);
            assertTrue(h.live().isEmpty());h.recorder.validLifecycle();
        }
    }

    private String hero(String name,boolean pulse){
        return String.join("\n",xtr.keymapper.mythic.HeroTrainingProfiles.INSTANCE.draft(xtr.keymapper.controller.HeroGameplayConfig.Hero.valueOf(name)))
            .replace("HOLD_RELEASE 0 0",pulse?"PULSE 150 1":"HOLD_RELEASE 0 1")+"\nENABLED";
    }
    @Test public void beatrixSharedSpellAndSimultaneousSkillsUseIndependentPointers() throws Exception {
        try(var h=new Harness(hero("BEATRIX",false))){
            h.axis("ABS_RX","ff");assertTrue(h.recorder.frames.isEmpty());
            h.key("BTN_TL2",true);h.key("BTN_TR",true);h.key("BTN_TR2",true);h.key("BTN_TL",true);
            h.key("BTN_EAST",true);int before=h.recorder.frames.size();h.key("BTN_THUMBL",true);assertEquals(before,h.recorder.frames.size());
            h.key("BTN_GAMEPAD",true);h.key("BTN_WEST",true);
            assertEquals(7,h.recorder.frames.get(h.recorder.frames.size()-1).ids.size());
            before=h.recorder.frames.size();h.key("BTN_EAST",false);assertEquals(before,h.recorder.frames.size());
            h.key("BTN_THUMBL",false);assertEquals(MotionEvent.ACTION_POINTER_UP,h.recorder.frames.get(h.recorder.frames.size()-1).action);
            h.monitor.stop();h.runtime.stop();h.recorder.validLifecycle();
        }
    }
    @Test public void beatrixPulseSurvivesAxisTrafficAndCancelsOnReleaseAndProfileStop() throws Exception {
        try(var h=new Harness(hero("BEATRIX",true))){
            h.key("BTN_TL2",true);
            for(int i=0;i<40;i++){h.axis("ABS_RX",i%2==0?"ff":"c0");h.tick(10);}
            assertTrue(h.recorder.frames.stream().filter(f->f.action==MotionEvent.ACTION_DOWN).count()>=3);
            h.key("BTN_TL2",false);int end=h.recorder.frames.size();h.tick(500);assertEquals(end,h.recorder.frames.size());
            h.key("BTN_TL2",true);h.monitor.stop();h.runtime.stop();end=h.recorder.frames.size();h.tick(500);assertEquals(end,h.recorder.frames.size());h.recorder.validLifecycle();
        }
        try(var next=new Harness(hero("KIMMY",false))){assertTrue(next.recorder.frames.isEmpty());next.monitor.stop();next.runtime.stop();}
    }
    @Test public void beatrixRepeatedAttackAcquiresAnchorBeforeAimingAtNeighboringControls() throws Exception {
        try(var h=new Harness(hero("BEATRIX",true).replace(
                "STICK_AIM BTN_TL2 1981.08 946.58 80 ","STICK_AIM BTN_TL2 1981.08 946.58 180 "))){
            var attack=h.runtime.getKeymapProfile().aimKeys.stream().filter(a->a.code.equals("BTN_TL2")).findFirst().orElseThrow();
            h.axis("ABS_RX","00");h.axis("ABS_RY","00");h.key("BTN_TL2",true);
            assertEquals("No same-turn drag before anchor acquisition",1,h.recorder.frames.size());
            // Even continuously changing stick samples must not move a newly pressed
            // contact onto neighboring skill hit areas during acquisition.
            for(int i=0;i<50;i++){h.axis("ABS_RX",i%2==0?"00":"ff");h.tick(10);}
            h.key("BTN_TL2",false);
            long down=-1;boolean moved=false;int complete=0;
            for(Frame f:h.recorder.frames){
                if(f.action==MotionEvent.ACTION_DOWN){
                    assertEquals(attack.x,f.xs.get(0),.001f);assertEquals(attack.y,f.ys.get(0),.001f);
                    down=f.time;moved=false;
                }else if(f.action==MotionEvent.ACTION_MOVE){
                    assertTrue("Anchor must persist before moving",f.time-down>=40);
                    assertTrue(Math.hypot(f.xs.get(0)-attack.x,f.ys.get(0)-attack.y)<=180.001);
                    moved=true;
                }else if(f.action==MotionEvent.ACTION_UP&&f.time-down>=80){
                    assertTrue("Complete pulse includes aimed phase",moved);complete++;
                }
            }
            assertTrue(complete>=3);h.recorder.validLifecycle();int end=h.recorder.frames.size();h.tick(500);assertEquals(end,h.recorder.frames.size());
        }
    }
    @Test public void pendingBeatrixAimCannotRunAfterEarlyReleaseSkillPriorityOrDisconnect() throws Exception {
        for(String interrupt:List.of("release","skill","disconnect","stop"))try(var h=new Harness(hero("BEATRIX",true))){
            h.axis("ABS_RX","ff");h.key("BTN_TL2",true);h.tick(20);
            switch(interrupt){
                case "release" -> h.key("BTN_TL2",false);
                case "skill" -> h.key("BTN_TR",true);
                case "disconnect" -> h.monitor.event("pad","EV_SYN SYN_DROPPED 00000000");
                case "stop" -> h.monitor.stop();
            }
            int end=h.recorder.frames.size();h.tick(500);assertEquals("No stale aimed movement after "+interrupt,end,h.recorder.frames.size());
            if(!interrupt.equals("stop"))h.key("BTN_TL2",false);if(interrupt.equals("skill"))h.key("BTN_TR",false);
            h.monitor.stop();h.runtime.stop();h.recorder.validLifecycle();
        }
    }
    @Test public void kimmySkillPriorityCenteringAndDisconnectReleaseEveryPointer() throws Exception {
        try(var h=new Harness(hero("KIMMY",false))){
            h.axis("ABS_X","ff");assertTrue(h.recorder.frames.isEmpty()); // native movement is untouched
            h.axis("ABS_RX","ff");assertEquals(MotionEvent.ACTION_DOWN,h.recorder.frames.get(0).action);
            h.key("BTN_TL2",true);int count=h.recorder.frames.size();h.key("BTN_TL2",false);assertEquals(count,h.recorder.frames.size());
            h.key("BTN_TR",true);assertEquals(MotionEvent.ACTION_UP,h.recorder.frames.get(count).action);
            h.key("BTN_TL",true);h.key("BTN_TR",false);h.axis("ABS_RY","ff");
            h.key("BTN_TL",false);assertEquals(MotionEvent.ACTION_MOVE,h.recorder.frames.get(h.recorder.frames.size()-1).action);
            h.key("BTN_GAMEPAD",true);h.key("BTN_WEST",true);h.nodes.clear();h.tick(250);
            assertEquals(MotionEvent.ACTION_UP,h.recorder.frames.get(h.recorder.frames.size()-1).action);h.recorder.validLifecycle();
            count=h.recorder.frames.size();h.nodes.put("pad",new long[]{5,6,0,255,255,0,255,128});h.tick(250);h.axis("ABS_RX","ff");assertEquals(count,h.recorder.frames.size());
            h.axis("ABS_RX","80");h.axis("ABS_RY","80");h.axis("ABS_RX","ff");assertTrue(h.recorder.frames.size()>count);
            h.monitor.stop();h.runtime.stop();h.recorder.validLifecycle();
        }
    }
    @Test public void kimmyRecallDoesNotResumeShootingUntilNeutralAndSynDroppedCleansUp() throws Exception {
        try(var h=new Harness(hero("KIMMY",false))){
            h.axis("ABS_RX","ff");h.key("BTN_WEST",true);h.key("BTN_WEST",false);int stopped=h.recorder.frames.size();
            h.axis("ABS_RX","c0");assertEquals(stopped,h.recorder.frames.size());
            h.axis("ABS_RX","80");h.axis("ABS_RX","ff");assertTrue(h.recorder.frames.size()>stopped);
            h.monitor.event("pad","EV_SYN SYN_DROPPED 00000000");h.recorder.validLifecycle();
            h.monitor.stop();h.runtime.stop();
        }
    }

    @Test public void actualProfileReloadCancelsOldHeroPulseAndReleasesSharedOwners() throws Exception {
        try(var h=new Harness(hero("BEATRIX",true))){
            ReflectionHelpers.setField(h.runtime,"controllers",h.monitor);
            h.key("BTN_TL2",true);h.key("BTN_EAST",true);h.key("BTN_THUMBL",true);h.key("BTN_GAMEPAD",true);
            assertFalse(h.live().isEmpty());
            h.reloadProfile=ProfileConfiguration.parse(hero("KIMMY",false));h.runtime.reloadKeymap();
            assertTrue(h.live().isEmpty());h.recorder.validLifecycle();int stopped=h.recorder.frames.size();
            h.tick(500);assertEquals(stopped,h.recorder.frames.size());assertFalse(h.monitor.event("pad","EV_KEY BTN_TL2 DOWN"));
            assertEquals(xtr.keymapper.controller.HeroGameplayConfig.Hero.KIMMY,h.runtime.getKeymapProfile().heroGameplay.hero);
            var next=new ControllerDeviceMonitor(h.runtime,2280,1080,()->new HashMap<>(h.nodes));
            ReflectionHelpers.setField(h.runtime,"controllers",next);next.start();
            next.event("pad","EV_ABS ABS_RX ff");next.event("pad","EV_SYN SYN_REPORT 00000000");
            assertFalse(h.live().isEmpty());next.stop();h.runtime.stop();h.recorder.validLifecycle();
        }
    }

}
