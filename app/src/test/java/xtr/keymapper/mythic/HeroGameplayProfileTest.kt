// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.mythic

import android.os.Parcel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import xtr.keymapper.controller.HeroGameplayConfig
import xtr.keymapper.editor.VisualMappingSession
import xtr.keymapper.keymap.*
import xtr.keymapper.structured.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],manifest=Config.NONE)
class HeroGameplayProfileTest {
    private fun ready(hero:HeroGameplayConfig.Hero)=HeroTrainingProfiles.draft(hero).map{if(it.startsWith("HERO_GAMEPLAY "))it.dropLast(1)+"1"else it}.toSet()+"ENABLED"
    @Test fun bothTemplatesAreDisabledAndActivationFailsUntilExplicitConfirmation(){
        for(hero in HeroGameplayConfig.Hero.entries){
            val draft=KeymapProfiles.getProfile(HeroTrainingProfiles.draft(hero));assertTrue(draft.disabled);assertFalse(draft.heroGameplay.calibrated)
            assertTrue(KeymapProfiles.getProfile(HeroTrainingProfiles.draft(hero)+"ENABLED").disabled)
            MythicScope.validateProfile(draft)
            assertThrows(IllegalArgumentException::class.java){MythicScope.validateActivation(draft)}
            val active=KeymapProfiles.getProfile(ready(hero));MythicScope.validateActivation(active)
            assertEquals(setOf("BTN_GAMEPAD","BTN_WEST"),active.keys.map{it.code}.toSet())
            assertFalse(active.keys.any{it.code=="BTN_THUMBL"});assertEquals(5,active.aimKeys.size)
        }
    }
    @Test fun blockedStructuredHeroCannotLoseItsActivationGate(){
        val ready=ready(HeroGameplayConfig.Hero.KIMMY)
        val packed=StructuredProfiles.pack(ProfileCodec.fromLegacy("Kimmy",ready),true)
        val profile=KeymapProfiles.getProfile(packed)
        assertTrue(profile.disabled);assertEquals(5,profile.aimKeys.size);assertNotNull(profile.heroGameplay)
        assertThrows(IllegalArgumentException::class.java){MythicScope.validateActivation(profile)}
    }
    @Test fun parcelAndJsonYamlRetainHeroPolicyAcrossBinderAndExports(){
        val lines=ready(HeroGameplayConfig.Hero.BEATRIX).map{if(it.startsWith("HERO_GAMEPLAY"))"HERO_GAMEPLAY BEATRIX RENNER PULSE 300 1"else it}.toSet()
        val p=KeymapProfiles.getProfile(lines);val parcel=Parcel.obtain()
        try{p.writeToParcel(parcel,0);parcel.setDataPosition(0);assertEquals(p.heroGameplay.data,KeymapProfile.CREATOR.createFromParcel(parcel).heroGameplay.data)}finally{parcel.recycle()}
        val document=ProfileCodec.fromLegacy("Training",lines)
        val canonical=ProfileCodec.decode(ProfileCodec.encode(document))
        for(yaml in listOf(false,true)){
            val decoded=ProfileCodec.decode(ProfileCodec.encode(document,yaml));assertEquals(canonical,decoded)
            val restored=KeymapProfiles.getProfile(decoded.runtimeLines());assertEquals(p.heroGameplay.data,restored.heroGameplay.data)
            for(aim in p.aimKeys){val saved=restored.aimKeys.single{it.code==aim.code};assertEquals(aim.x,saved.x,0f);assertEquals(aim.y,saved.y,0f);assertEquals(aim.radius,saved.radius,0f)}
        }
    }
    @Test fun editingCoordinatesRevokesReadinessAndKeepsHeroPolicy(){
        val lines=ready(HeroGameplayConfig.Hero.KIMMY)
        val session=VisualMappingSession("test",lines);session.replace("BTN_TL2","STICK_AIM BTN_TL2 1900 900 100 1 1 .15 0")
        assertFalse(session.profile.heroGameplay.calibrated);assertTrue(session.profile.disabled)
        assertThrows(IllegalArgumentException::class.java){session.replace("BTN_THUMBL","BTN_THUMBL 20 20 0")}
        val structured=StructuredProfiles.pack(ProfileCodec.fromLegacy("Training",lines))
        val draft=StructuredProfiles.reconcile(structured,StructuredProfiles.runtime(structured).map{if(it.startsWith("STICK_AIM BTN_TL2"))"STICK_AIM BTN_TL2 1900 900 100 1 1 .15 0"else it}.toSet())
        val document=StructuredProfiles.document(draft)!!;assertFalse(document.profile.enabled)
        assertTrue(document.bindings.single{it.action=="hero_gameplay"}.nativeLine.endsWith(" 0"))
    }
    @Test fun scopeRejectsConflictsAndUnboundedOrKimmyRepeatPolicies(){
        for(line in listOf("HERO_GAMEPLAY KIMMY NONE PULSE 150 1","HERO_GAMEPLAY BEATRIX NONE HOLD_RELEASE 0 1","HERO_GAMEPLAY BEATRIX RENNER PULSE 0 1","HERO_GAMEPLAY BEATRIX RENNER PULSE 60001 1"))
            assertThrows(IllegalArgumentException::class.java){ProfileConfiguration.parse(line)}
        val p=KeymapProfiles.getProfile(ready(HeroGameplayConfig.Hero.BEATRIX)+"BTN_THUMBL 10 20 0")
        assertThrows(IllegalArgumentException::class.java){MythicScope.validateActivation(p)}
    }
    @Test fun weaponPolicyAndCadenceRemainIndependentOfCoordinateScaling(){
        for(weapon in HeroGameplayConfig.Weapon.entries.filter{it!=HeroGameplayConfig.Weapon.NONE}){
            val lines=ready(HeroGameplayConfig.Hero.BEATRIX).map{if(it.startsWith("HERO_GAMEPLAY"))"HERO_GAMEPLAY BEATRIX $weapon PULSE 300 1"else it}.toSet()
            val p=KeymapProfiles.getProfile(lines);val x=p.aimKeys.single{it.code=="BTN_TL2"}.x
            p.scale(1140f,540f)
            assertEquals(x/2,p.aimKeys.single{it.code=="BTN_TL2"}.x,0f)
            assertEquals(weapon,p.heroGameplay.weapon);assertEquals(300,p.heroGameplay.repeatMs)
            MythicScope.validateActivation(p)
        }
    }
    @Test fun installationAndOptInFavoritesNeverReplaceExistingData(){
        val context=RuntimeEnvironment.getApplication();val store=HeroLibraryStore(context)
        val original=store.clone("Existing personal",HeroCatalog.lines(context,HeroCatalog.presets.first()))
        store.update(store.read(),store.read().assign(0,original.id))
        val mappings=KeymapProfiles(context).sharedPref;val originalLines=mappings.getStringSet(original.id,null)!!.toSet()
        val beatrix=store.clone("Beatrix training",HeroTrainingProfiles.draft(HeroGameplayConfig.Hero.BEATRIX),"s10e-g8-beatrix-training-v1")
        assertEquals(original.id,store.read().favorites[0]);assertEquals(originalLines,mappings.getStringSet(original.id,null))
        assertThrows(IllegalArgumentException::class.java){HeroTrainingProfiles.assignRecommended(context,beatrix.id)}
        val kimmy=store.clone("Kimmy training",HeroTrainingProfiles.draft(HeroGameplayConfig.Hero.KIMMY),"s10e-g8-kimmy-training-v1")
        assertNull(store.read().favorites[1]);HeroTrainingProfiles.assignRecommended(context,kimmy.id)
        assertEquals(kimmy.id,store.read().favorites[1]);assertEquals(original.id,store.read().favorites[0])
    }
}
