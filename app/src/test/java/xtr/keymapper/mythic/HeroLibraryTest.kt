// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.mythic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import xtr.keymapper.keymap.KeymapProfiles

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class HeroLibraryTest {
    private fun profile(name: String)=HeroProfile(UUID.randomUUID().toString(),name,null)
    @Test fun slotsReplaceRemoveMoveAndRenameByIdentity() {
        val a=profile("Hero A");val b=profile("Hero B")
        var s=HeroLibrary(listOf(a,b)).assign(0,a.id).assign(1,b.id)
        s=s.swap(0,4);assertEquals(a.id,s.favorites[4])
        s=s.assign(4,b.id);assertNull(s.favorites[1])
        s=s.copy(profiles=listOf(a,b.copy(name="Renamed"))).validate()
        assertEquals(b.id,s.favorites[4]);assertEquals(5,s.favorites.size)
        s=s.remove(b.id);assertTrue(s.favorites.all{it==null})
    }
    @Test fun persistenceReopenAndImmutableCatalogClone() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val preset=HeroCatalog.presets.single();val original=HeroCatalog.lines(context,preset)
        val store=HeroLibraryStore(context);val p=store.clone("My hero",original,preset.id)
        val s=store.read();store.update(s,s.assign(2,p.id))
        assertEquals(p.id,HeroLibraryStore(context).read().favorites[2])
        val prefs=KeymapProfiles(context).sharedPref
        prefs.edit().putStringSet(p.id,original+"BTN_NORTH 200 200 0").commit()
        assertEquals(original,HeroCatalog.lines(context,preset))
        assertTrue(KeymapProfiles(context).sharedPref.getStringSet(p.id,null)!!.contains("BTN_NORTH 200 200 0"))
        store.delete(p.id);assertNull(store.read().favorites[2]);assertFalse(prefs.contains(p.id))
    }
    @Test fun migrationPadsSlotsButFutureAndCorruptSchemasFail() {
        val a=profile("Hero");val encoded=HeroLibraryCodec.encode(HeroLibrary(listOf(a)).assign(0,a.id))
        assertEquals(HeroLibrary(listOf(a)).assign(0,a.id),HeroLibraryCodec.decode(encoded))
        val v0=org.json.JSONObject(encoded).put("version",0).put("favorites",org.json.JSONArray(listOf(a.id))).toString()
        assertEquals(listOf(a.id,null,null,null,null),HeroLibraryCodec.decode(v0).favorites)
        for(bad in listOf("{",encoded.replace("\"version\":1","\"version\":999"),
            org.json.JSONObject(encoded).put("favorites",org.json.JSONArray(listOf("missing",null,null,null,null))).toString())) {
            assertThrows(Exception::class.java){HeroLibraryCodec.decode(bad)}
        }
    }
    @Test fun staleWriterDoesNotOverwrite() {
        val context=ApplicationProvider.getApplicationContext<Context>();val store=HeroLibraryStore(context)
        val old=store.read();val p=store.clone("Hero",HeroCatalog.lines(context,HeroCatalog.presets.single()))
        assertThrows(IllegalArgumentException::class.java){store.update(old,HeroLibrary())}
        assertTrue(store.read().profiles.any{it.id==p.id})
    }
    @Test fun rejectsOtherGamesAndAutomation() {
        val context=ApplicationProvider.getApplicationContext<Context>();val original=HeroCatalog.lines(context,HeroCatalog.presets.single())
        for(lines in listOf(original.map{it.replace(MythicScope.GAME,"other.game")}.toSet(),
            original.map{if(it.startsWith("BTN_TL2 ")) "$it 150" else it}.toSet())) {
            assertThrows(IllegalArgumentException::class.java){HeroLibraryStore(context).clone("bad",lines)}
        }
    }
    @Test fun failedCommitRestoresPriorMetadataAndFutureDataIsNeverOverwritten() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val actual=context.getSharedPreferences("failure_metadata",0)
        var reject=true
        val failing=object:android.content.SharedPreferences by actual {
            override fun edit():android.content.SharedPreferences.Editor {
                val edit=actual.edit()
                return object:android.content.SharedPreferences.Editor by edit {
                    override fun putString(key:String?,value:String?):android.content.SharedPreferences.Editor {edit.putString(key,value);return this}
                    override fun remove(key:String?):android.content.SharedPreferences.Editor {edit.remove(key);return this}
                    override fun commit():Boolean {val result=edit.commit();if(reject){reject=false;return false};return result}
                }
            }
        }
        val store=HeroLibraryStore(failing,KeymapProfiles(context).sharedPref)
        val before=store.read();val p=profile("Test")
        assertThrows(IllegalStateException::class.java){store.update(before,before.copy(profiles=listOf(p)))}
        assertEquals(before,store.read());assertFalse(actual.contains("library"))
        val future="{\"version\":99,\"profiles\":[],\"favorites\":[null,null,null,null,null]}"
        actual.edit().putString("library",future).commit()
        assertThrows(IllegalArgumentException::class.java){store.read()}
        assertEquals(future,actual.getString("library",null))
    }

    @Test fun deviceEvidenceRequiresObservedNameIdentityAndControllerSource() {
        val g8=xtr.keymapper.devices.HardwareDevice("Sony Interactive Entertainment GameSir-G8",xtr.keymapper.devices.DeviceType.GAMEPAD,
            vendorId=0x054c,productId=0x0ce6,sources=android.view.InputDevice.SOURCE_GAMEPAD)
        assertTrue(MythicScope.supported(g8))
        for(other in listOf(g8.copy(name="Sony Controller"),g8.copy(name="GameSir G8+"),g8.copy(productId=1),g8.copy(sources=android.view.InputDevice.SOURCE_KEYBOARD),g8.copy(isConnected=false)))
            assertFalse(MythicScope.supported(other))
    }

}
