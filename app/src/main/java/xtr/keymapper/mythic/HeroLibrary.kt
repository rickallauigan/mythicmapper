// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.mythic

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import xtr.keymapper.keymap.KeymapProfiles
import xtr.keymapper.keymap.ProfileConfiguration
import xtr.keymapper.structured.StructuredProfiles
import java.util.UUID

/** UUID is also the legacy profile storage key, so existing editors retain stable identity. */
data class HeroProfile(val id: String, val name: String, val preset: String?)
data class HeroLibrary(val profiles: List<HeroProfile> = emptyList(),
                       val favorites: List<String?> = List(5) { null }) {
    fun validate(): HeroLibrary {
        require(favorites.size == 5) { "Exactly five favorite slots required" }
        require(profiles.map { it.id }.distinct().size == profiles.size) { "Duplicate profile ID" }
        profiles.forEach {
            require(UUID.fromString(it.id).toString() == it.id) { "Invalid profile UUID" }
            require(it.name.isNotBlank() && it.name.length <= 80 && it.name.none(Char::isISOControl)) { "Invalid profile name" }
        }
        require(favorites.filterNotNull().all { id -> profiles.any { it.id == id } }) { "Favorite references missing profile" }
        require(favorites.filterNotNull().distinct().size == favorites.filterNotNull().size) { "Duplicate favorite" }
        return this
    }
    fun assign(slot: Int, id: String?): HeroLibrary {
        require(slot in 0..4)
        require(id == null || profiles.any { it.id == id })
        return copy(favorites = favorites.mapIndexed { index, old -> when {
            index == slot -> id
            id != null && old == id -> null
            else -> old
        } }).validate()
    }
    fun swap(a: Int, b: Int): HeroLibrary {
        require(a in 0..4 && b in 0..4)
        val next=favorites.toMutableList(); val old=next[a]; next[a]=next[b]; next[b]=old
        return copy(favorites=next).validate()
    }
    fun remove(id: String) = copy(profiles=profiles.filterNot { it.id == id },
        favorites=favorites.map { if (it == id) null else it }).validate()
}

object HeroLibraryCodec {
    fun encode(value: HeroLibrary): String {
        value.validate()
        return JSONObject().put("version",1).put("profiles",JSONArray(value.profiles.map {
            JSONObject().put("id",it.id).put("name",it.name).put("preset",it.preset ?: JSONObject.NULL)
        })).put("favorites",JSONArray(value.favorites.map { it ?: JSONObject.NULL })).toString()
    }
    fun decode(text: String): HeroLibrary {
        require(text.length <= 4 * 1024 * 1024) { "Library metadata exceeds storage limit" }
        val root=JSONObject(text)
        require(root.keys().asSequence().toSet() == setOf("version","profiles","favorites")) { "Unexpected library fields" }
        val versionValue=root.get("version"); require(versionValue is Int) { "Invalid library version" }
        val version=versionValue
        require(version in 0..1) { "Unsupported library version; nothing overwritten" }
        val profiles=root.getJSONArray("profiles").let { a -> (0 until a.length()).map { i ->
            val p=a.getJSONObject(i)
            require(p.keys().asSequence().toSet() == setOf("id","name","preset")) { "Unexpected profile fields" }
            require(p.get("id") is String && p.get("name") is String && (p.isNull("preset") || p.get("preset") is String))
            HeroProfile(p.getString("id"),p.getString("name"),if(p.isNull("preset")) null else p.getString("preset"))
        } }
        // Version 0 was the development compact favorites array. Migration pads empty slots.
        val a=root.getJSONArray("favorites"); require(a.length() <= 5 && (version == 0 || a.length() == 5))
        val favorites=List(5) { i -> if(i>=a.length() || a.isNull(i)) null else a.getString(i) }
        return HeroLibrary(profiles,favorites).validate()
    }
}

/** Single checked metadata transaction. Mapping text remains in the proven profile store. */
class HeroLibraryStore internal constructor(private val prefs: android.content.SharedPreferences,
                                            private val mappings: android.content.SharedPreferences) {
    constructor(context: Context): this(context.getSharedPreferences("mythic_hero_library",Context.MODE_PRIVATE),KeymapProfiles(context).sharedPref)
    fun read(): HeroLibrary = synchronized(StructuredProfiles.SAVE_LOCK) {
        prefs.getString("library",null)?.let(HeroLibraryCodec::decode) ?: HeroLibrary()
    }
    fun update(expected: HeroLibrary, next: HeroLibrary) = synchronized(StructuredProfiles.SAVE_LOCK) {
        require(read() == expected) { "Library changed; refresh and retry" }
        val encoded=HeroLibraryCodec.encode(next)
        require(encoded.length <= 4 * 1024 * 1024) { "Library metadata exceeds storage limit" }
        val before=prefs.getString("library",null)
        if(!prefs.edit().putString("library",encoded).commit()) {
            val rollback=prefs.edit(); if(before==null) rollback.remove("library") else rollback.putString("library",before)
            check(rollback.commit()) { "Library save and rollback failed; verify storage" }
            error("Library save failed; prior state restored")
        }
    }
    fun clone(name: String, lines: Set<String>, preset: String? = null): HeroProfile = synchronized(StructuredProfiles.SAVE_LOCK) {
        val previous=read(); val profile=HeroProfile(UUID.randomUUID().toString(),name,preset)
        val next=previous.copy(profiles=previous.profiles+profile).validate()
        val universal=xtr.keymapper.universal.UniversalStorage.document(lines)
        val checked=if(universal==null)ProfileConfiguration.validate(lines.joinToString("\n"))else xtr.keymapper.universal.UniversalStorage.pack((if(previous.profiles.any{it.id==universal.id})universal.cloned(name)else universal.copy(name=name,calibrated=false,enabled=false)).copy(id=profile.id))
        MythicScope.validateProfile(KeymapProfiles.getProfile(checked))
        StructuredProfiles.writeSet(mappings,profile.id,checked)
        // A metadata failure may leave an unreferenced mapping, never an invalid favorite.
        update(previous,next)
        profile
    }
    fun delete(id: String) = synchronized(StructuredProfiles.SAVE_LOCK) {
        val previous=read(); update(previous,previous.remove(id))
        // Remove references before data. A crash can leave an orphan, not a dangling favorite.
        check(mappings.edit().remove(id).commit()) { "Unreferenced mapping cleanup failed" }
    }
}

data class HeroPreset(val id: String, val name: String, val status: String, val asset: String)
object HeroCatalog {
    val presets=listOf(HeroPreset("s10e-g8-tuned-v03-single-touch-r1", "S10e G8 starter · assign your hero",
        "Unverified adaptation of historically tested tuned v0.3; recalibrate for your HUD", "mythic/s10e-g8-starter.txt"),
        HeroPreset("s10e-g8-beatrix-training-v1","Beatrix · training draft","Unverified hero/weapon HUD; disabled until setup. Suggested favorite 1, opt-in only.","generated:BEATRIX"),
        HeroPreset("s10e-g8-kimmy-training-v1","Kimmy · training draft","Unverified current hero/HUD; disabled until setup. Suggested favorite 2, opt-in only.","generated:KIMMY"))
    fun lines(context: Context, preset: HeroPreset): Set<String> {
        if(preset.id in setOf("s10e-g8-beatrix-training-v1","s10e-g8-kimmy-training-v1"))
            return HeroTrainingProfiles.draft(if(preset.id.contains("beatrix"))xtr.keymapper.controller.HeroGameplayConfig.Hero.BEATRIX else xtr.keymapper.controller.HeroGameplayConfig.Hero.KIMMY)
        return context.assets.open(preset.asset).bufferedReader().use { ProfileConfiguration.validate(it.readText()) }
    }
}
