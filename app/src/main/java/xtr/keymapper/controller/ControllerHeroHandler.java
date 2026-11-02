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
    private final Set<String> spellOwners=new LinkedHashSet<>(), skills=new LinkedHashSet<>();
    private float x,y;
    private boolean lt, attack, running=true, waitForCenter=true;
    public ControllerHeroHandler(HeroGameplayConfig config,ControllerAimHandler aim,BiConsumer<String,Integer> dispatch,android.os.Handler handler,float deadZone){
        if(!config.calibrated)throw new IllegalArgumentException("Confirm hero HUD calibration first");
        this.config=config;this.aim=aim;this.dispatch=dispatch;this.deadZone=deadZone;
        pulseKey.repeatIntervalMs=config.repeatMs;
        // Reuse the proven cancelable pulse scheduler. Aim retains the actual
        // pointer and current stick position; dummy fixed coordinates never reach injection.
        pulses=new xtr.keymapper.server.event.RepeatKeyHandler(handler,(px,py,action,pointer)->setAttack(action==1));
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
        if(code.equals("BTN_WEST")&&action==1&&config.hero==HeroGameplayConfig.Hero.KIMMY){waitForCenter=true;setAttack(false);}
        return false;
    }
    private void skill(String code,int action){
        if(action==1)skills.add(code);else skills.remove(code);
        if(config.hero==HeroGameplayConfig.Hero.KIMMY&&action==1)setAttack(false);
        focus();dispatch.accept(code,action);reconcile();aim.axes(x,y);
    }
    private void focus(){String last=null;for(String code:skills)last=code;aim.focus(last==null?ATTACK:last);}
    private void reconcile(){
        focus();
        if(config.hero==HeroGameplayConfig.Hero.KIMMY){setAttack(!waitForCenter&&skills.isEmpty()&&Math.hypot(x,y)>deadZone);return;}
        if(config.fire==HeroGameplayConfig.Fire.HOLD_RELEASE){setAttack(lt);return;}
        pulses.event(pulseKey,0,lt&&skills.isEmpty()?1:0);
    }
    private void setAttack(boolean down){if(attack==down)return;attack=down;aim.trigger(ATTACK,down?1:0);}
    public void stop(){if(!running)return;running=false;lt=false;pulses.reset();setAttack(false);
        for(String code:skills)dispatch.accept(code,0);skills.clear();spellOwners.clear();aim.focus(null);x=y=0;
    }
}
