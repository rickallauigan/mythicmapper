// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.mythic

import android.content.Context
import xtr.keymapper.controller.HeroGameplayConfig
import xtr.keymapper.keymap.KeymapProfiles
import xtr.keymapper.keymap.ProfileConfiguration
import xtr.keymapper.structured.*

/** Templates reuse measured Dyrroth HUD anchors, never claim hero-specific calibration. */
object HeroTrainingProfiles {
    fun draft(hero:HeroGameplayConfig.Hero):Set<String> = ProfileConfiguration.validate("""
        APPLICATION com.mobile.legends
        SCREENSIZE 2280 1080
        HERO_GAMEPLAY $hero ${if(hero==HeroGameplayConfig.Hero.BEATRIX)"NIBIRU" else "NONE"} HOLD_RELEASE 0 0
        BTN_WEST 1172.29 970.84 0
        BTN_GAMEPAD 1341.5 955.02 0
        STICK_AIM BTN_EAST 1477.88 982.97 80 1 1 0.15 0
        STICK_AIM BTN_TL2 1981.08 946.58 80 1 1 0.15 0
        STICK_AIM BTN_TR 1650.44 965.04 80 1 1 0.15 0
        STICK_AIM BTN_TR2 1775.13 753.31 80 1 1 0.15 0
        STICK_AIM BTN_TL 1986.09 648.63 80 1 1 0.15 0
    """.trimIndent())
    fun save(context:Context,id:String,expected:Set<String>,next:Set<String>) = synchronized(StructuredProfiles.SAVE_LOCK){
        val prefs=KeymapProfiles(context).sharedPref
        require(prefs.getStringSet(id,null)?.toSet()==expected){"Profile changed elsewhere; reopen setup"}
        val checked=ProfileConfiguration.validate(next.joinToString("\n"));val p=KeymapProfiles.getProfile(checked)
        require(p.heroGameplay!=null);MythicScope.validateProfile(p)
        if(!p.disabled)MythicScope.validateActivation(p)
        val old=StructuredProfiles.document(expected)
        val saved=if(old==null)checked else {
            val fresh=ProfileCodec.fromLegacy(old.profile.name,checked)
            val active=fresh.bindings.map{b->old.bindings.firstOrNull{it.identity==b.identity}?.let{b.copy(name=it.name,description=it.description)}?:b}
            StructuredProfiles.pack(fresh.copy(profile=fresh.profile.copy(description=old.profile.description),bindings=active+old.bindings.filterNot{it.enabled}))
        }
        StructuredProfiles.writeSet(prefs,id,saved)
    }
    fun assignRecommended(context:Context,id:String){
        val p=KeymapProfiles(context).getProfile(id,false);val hero=p.heroGameplay?:error("Not a hero profile")
        val slot=if(hero.hero==HeroGameplayConfig.Hero.BEATRIX)0 else 1
        val store=HeroLibraryStore(context);val old=store.read()
        require(old.favorites[slot]==null||old.favorites[slot]==id){"Favorite ${slot+1} is occupied; use Home's explicit Assign action to replace it"}
        store.update(old,old.assign(slot,id))
    }
}
