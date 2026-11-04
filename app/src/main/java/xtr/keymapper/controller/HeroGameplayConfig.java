// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.controller;

import android.os.Parcel;
import android.os.Parcelable;
import xtr.keymapper.keymap.KeymapProfile;

/** Explicit, manually selected touch policy. Never detects weapons or targets from the game. */
public final class HeroGameplayConfig implements Parcelable {
    public enum Hero { BEATRIX, KIMMY }
    public enum Weapon { NIBIRU, BENNETT, WESKER, RENNER, NONE }
    public enum Fire { HOLD_RELEASE, PULSE }
    public final Hero hero;
    public final Weapon weapon;
    public final Fire fire;
    public final int repeatMs;
    public final boolean calibrated;
    public HeroGameplayConfig(String[] data) {
        if(data.length!=6) throw new IllegalArgumentException("HERO_GAMEPLAY requires hero, weapon, fire policy, repeat ms and calibration flag");
        hero=Hero.valueOf(data[1]);weapon=Weapon.valueOf(data[2]);fire=Fire.valueOf(data[3]);repeatMs=Integer.parseInt(data[4]);
        if(!data[5].equals("0")&&!data[5].equals("1"))throw new IllegalArgumentException("Calibration flag must be 0 or 1");
        calibrated=data[5].equals("1");
        if(hero==Hero.KIMMY&&(weapon!=Weapon.NONE||fire!=Fire.HOLD_RELEASE||repeatMs!=0))throw new IllegalArgumentException("Kimmy uses stick-held shooting only");
        if(hero==Hero.BEATRIX&&weapon==Weapon.NONE)throw new IllegalArgumentException("Select the Beatrix weapon manually");
        if(fire==Fire.PULSE ? repeatMs<60||repeatMs>60000 : repeatMs!=0)throw new IllegalArgumentException("Pulse cadence must be 60..60000 ms; held mode uses zero");
    }
    public String getData(){return "HERO_GAMEPLAY "+hero+" "+weapon+" "+fire+" "+repeatMs+" "+(calibrated?1:0);}
    public void validate(KeymapProfile p) {
        if(p.camera!=null||!p.chords.isEmpty())throw new IllegalArgumentException("Hero shooting exclusively owns the right stick");
        var codes=p.aimKeys.stream().map(a->a.code).collect(java.util.stream.Collectors.toSet());
        if(p.aimKeys.size()!=5||!codes.equals(java.util.Set.of("BTN_TL2","BTN_TR","BTN_TR2","BTN_TL","BTN_EAST")))throw new IllegalArgumentException("Hero profile needs attack, three skills and one shared spell aim binding");
        if(p.keys.size()!=2||!p.keys.stream().map(k->k.code).collect(java.util.stream.Collectors.toSet()).equals(java.util.Set.of("BTN_GAMEPAD","BTN_WEST")))throw new IllegalArgumentException("Hero fixed controls must be A Regen and X Recall; B/L3 share spell ownership");
        if(p.xRes<=0||p.yRes<=0)throw new IllegalArgumentException("Hero profile needs a calibrated screen plane");
        for(var a:p.aimKeys)if(a.x<0||a.y<0||a.x>=p.xRes||a.y>=p.yRes||a.radius<=0)throw new IllegalArgumentException("Hero aim center/radius outside saved plane");
        for(var k:p.keys)if(k.x+k.offset<0||k.y+k.offset<0||k.x+k.offset>=p.xRes||k.y+k.offset>=p.yRes)throw new IllegalArgumentException("Hero fixed anchor outside saved plane");
    }
    private HeroGameplayConfig(Parcel p){this(p.readString().split(" "));}
    public int describeContents(){return 0;}
    public void writeToParcel(Parcel p,int flags){p.writeString(getData());}
    public static final Creator<HeroGameplayConfig> CREATOR=new Creator<>(){
        public HeroGameplayConfig createFromParcel(Parcel p){return new HeroGameplayConfig(p);}
        public HeroGameplayConfig[] newArray(int size){return new HeroGameplayConfig[size];}
    };
}
