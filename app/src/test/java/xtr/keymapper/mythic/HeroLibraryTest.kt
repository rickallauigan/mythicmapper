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
}
