// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.controller;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/** Manual input policy over the existing held-aim engine, on its owning runtime looper. */
public final class ControllerHeroHandler {
    private static final String ATTACK="BTN_TL2", SPELL="BTN_EAST";
    private final HeroGameplayConfig config;
    private final ControllerAimHandler aim;
    private final BiConsumer<String,Integer> dispatch;
    private final xtr.keymapper.server.event.RepeatKeyHandler pulses;
    private final xtr.keymapper.keymap.element.Key pulseKey=new xtr.keymapper.keymap.element.Key();
    private final float deadZone;
    private final ControllerAimHandler.AnchorScheduler aimScheduler;
    private final int anchorMs;
    private final Set<String> spellOwners=new LinkedHashSet<>(), skills=new LinkedHashSet<>();
    private float x,y;
    private boolean lt, attack, running=true, waitForCenter=true;
    private boolean kimmyResumeAfterSkill;
    private Runnable pendingKimmyResume;
    // One-shot touch handoff phases, not an attack cadence or game cooldown.
    private static final int KIMMY_RELEASE_GAP_MS=40, KIMMY_RESUME_ANCHOR_MS=40;
    public ControllerHeroHandler(HeroGameplayConfig config,ControllerAimHandler aim,BiConsumer<String,Integer> dispatch,android.os.Handler handler,float deadZone){
        if(!config.calibrated)throw new IllegalArgumentException("Confirm hero HUD calibration first");
        this.config=config;this.aim=aim;this.dispatch=dispatch;this.deadZone=deadZone;
        aimScheduler=new ControllerAimHandler.AnchorScheduler(){
            public void after(Runnable task,int delayMs){handler.postDelayed(task,delayMs);}
            public void cancel(Runnable task){handler.removeCallbacks(task);}
        };
        // Provisional acquisition/aim phases, scoped to manually held Beatrix repeat.
        // At the 150 ms training cadence: anchor 40 ms, aimed 40 ms, released 70 ms.
        anchorMs=config.hero==HeroGameplayConfig.Hero.BEATRIX&&config.fire==HeroGameplayConfig.Fire.PULSE
            ?Math.min(40,config.repeatMs/3):0;
        pulseKey.repeatIntervalMs=config.repeatMs;
        // Reuse the proven cancelable pulse scheduler. Aim retains the actual
        // pointer and current stick position; dummy fixed coordinates never reach injection.
        pulses=new xtr.keymapper.server.event.RepeatKeyHandler(handler,(px,py,action,pointer)->setAttack(action==1),anchorMs*2);
    }
    public void axes(float x,float y){if(!running)return;this.x=x;this.y=y;
        if(Math.hypot(x,y)<=deadZone)waitForCenter=false;
        reconcile();aim.axes(x,y);
    }
    public boolean button(String code,int action){
        if(!running)return false;
        if(code.equals(ATTACK)){
            if(config.hero==HeroGameplayConfig.Hero.KIMMY)return true; // LT is deliberately unnecessary.
            lt=action==1;reconcile();return true;
        }
        if(code.equals(SPELL)||code.equals("BTN_THUMBL")){
            boolean before=!spellOwners.isEmpty();if(action==1)spellOwners.add(code);else spellOwners.remove(code);
            boolean after=!spellOwners.isEmpty();if(before!=after)skill(SPELL,after?1:0);return true;
        }
        if(code.equals("BTN_TR")||code.equals("BTN_TR2")||code.equals("BTN_TL")){skill(code,action);return true;}
        if(code.equals("BTN_WEST")&&action==1&&config.hero==HeroGameplayConfig.Hero.KIMMY){waitForCenter=true;cancelKimmyResume();kimmyResumeAfterSkill=false;setAttack(false);}
        return false;
    }
    private void skill(String code,int action){
        if(action==1)skills.add(code);else skills.remove(code);
        if(config.hero==HeroGameplayConfig.Hero.KIMMY&&action==1){cancelKimmyResume();kimmyResumeAfterSkill=true;setAttack(false);}
        focus();dispatch.accept(code,action);reconcile();aim.axes(x,y);
    }
    private void focus(){String last=null;for(String code:skills)last=code;aim.focus(last==null?ATTACK:last);}
    private void reconcile(){
        focus();
        if(config.hero==HeroGameplayConfig.Hero.KIMMY){
            boolean shoot=!waitForCenter&&skills.isEmpty()&&Math.hypot(x,y)>deadZone;
            if(!shoot){
                cancelKimmyResume();setAttack(false);
                if(skills.isEmpty())kimmyResumeAfterSkill=false;
            }else if(!attack&&pendingKimmyResume==null){
                if(kimmyResumeAfterSkill)scheduleKimmyResume();else setAttack(true);
            }
            return;
        }
        if(config.fire==HeroGameplayConfig.Fire.HOLD_RELEASE){setAttack(lt);return;}
        pulses.event(pulseKey,0,lt&&skills.isEmpty()?1:0);
    }
    private void setAttack(boolean down){if(attack==down)return;attack=down;
        if(down&&anchorMs>0)aim.triggerAfterAnchor(ATTACK,aimScheduler,anchorMs);else aim.trigger(ATTACK,down?1:0);
    }
    private void scheduleKimmyResume(){
        pendingKimmyResume=new Runnable(){public void run(){
            if(pendingKimmyResume!=this)return;
            pendingKimmyResume=null;
            if(!running||waitForCenter||!skills.isEmpty()||Math.hypot(x,y)<=deadZone)return;
            kimmyResumeAfterSkill=false;focus();attack=true;
            aim.triggerAfterAnchor(ATTACK,aimScheduler,KIMMY_RESUME_ANCHOR_MS);
            aim.axes(x,y);
        }};
        aimScheduler.after(pendingKimmyResume,KIMMY_RELEASE_GAP_MS);
    }
    private void cancelKimmyResume(){
        if(pendingKimmyResume!=null)aimScheduler.cancel(pendingKimmyResume);
        pendingKimmyResume=null;
    }
    public void stop(){if(!running)return;running=false;cancelKimmyResume();kimmyResumeAfterSkill=false;lt=false;pulses.reset();setAttack(false);
        for(String code:skills)dispatch.accept(code,0);skills.clear();spellOwners.clear();aim.focus(null);x=y=0;
    }
}
