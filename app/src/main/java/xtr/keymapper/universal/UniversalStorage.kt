// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.universal

import android.content.Context
import android.util.Base64
import xtr.keymapper.keymap.KeymapProfiles
import xtr.keymapper.mythic.HeroLibraryStore
import xtr.keymapper.structured.StructuredProfiles

/** Uses existing string-set transactions. Neither legacy nor starter data is rewritten. */
object UniversalStorage {
    const val TAG="UNIVERSAL_MAPPING_V2"
    /** Validate before invoking a document provider that may truncate its destination on open. */
    fun exportSnapshot(snapshot:String?,open:()->java.io.OutputStream){
        val text=requireNotNull(snapshot){"Export session expired; choose Export again"}
        val bytes=UniversalCodec.encode(UniversalCodec.decode(text)).toByteArray(Charsets.UTF_8)
        open().use{it.write(bytes)}
    }
    @JvmStatic fun contains(text:String)=text.lineSequence().any{it.trim().startsWith("$TAG ")}
    fun pack(d:UniversalMapping):Set<String> = setOf("APPLICATION ${d.game}","SCREENSIZE ${d.geometry.width} ${d.geometry.height}",
        "$TAG ${Base64.encodeToString(UniversalCodec.encode(d).toByteArray(Charsets.UTF_8),Base64.NO_WRAP)}")+(if(d.enabled&&d.calibrated)setOf("ENABLED")else emptySet())
    @JvmStatic fun document(lines:Set<String>):UniversalMapping? {
        val tags=lines.filter{it.startsWith("$TAG ")};if(tags.isEmpty())return null
        require(tags.size==1){"Duplicate universal mapping"};val encoded=tags.single().substringAfter(' ')
        require(encoded.length<=2*xtr.keymapper.structured.ProfileCodec.MAX_BYTES)
        val d=UniversalCodec.decode(xtr.keymapper.structured.ProfileCodec.read(java.io.ByteArrayInputStream(Base64.decode(encoded,Base64.DEFAULT))))
        require(lines==pack(d)){"Universal metadata/projection conflict; nothing overwritten"};return d
    }
    @JvmStatic fun validateContainer(text:String):Set<String> {
        val lines=text.lineSequence().filter{it.isNotBlank()}.map{it.trim()}.toList();require(lines.distinct().size==lines.size){"Duplicate container lines"}
        val set=lines.toSet();require(document(set)!=null);return set
    }
    fun read(context:Context,id:String):UniversalMapping {
        val owner=HeroLibraryStore(context).read().profiles.singleOrNull{it.id==id}?:error("Unknown personal profile ownership")
        val d=document(KeymapProfiles(context).sharedPref.getStringSet(id,null)?.toSet()?:error("Mapping missing"))?:error("This profile uses the legacy editor")
        require(d.id==id){"Mapping UUID does not match its personal owner"};return d.copy(name=owner.name)
    }
    fun save(context:Context,id:String,d:UniversalMapping,expected:Set<String>,expectedName:String) = synchronized(StructuredProfiles.SAVE_LOCK) {
        val store=HeroLibraryStore(context);val library=store.read();val owner=library.profiles.singleOrNull{it.id==id}?:error("Profile deleted")
        require(owner.name==expectedName){"Profile renamed elsewhere; reopen before saving"}
        require(d.id==id){"Unknown profile owner"};d.validate()
        val prefs=KeymapProfiles(context).sharedPref;require(prefs.getStringSet(id,null)?.toSet()==expected){"Profile changed elsewhere; nothing overwritten"}
        StructuredProfiles.writeSet(prefs,id,pack(d))
        if(owner.name!=d.name)try{store.update(library,library.copy(profiles=library.profiles.map{if(it.id==id)it.copy(name=d.name)else it}))}
        catch(t:Exception){StructuredProfiles.writeSet(prefs,id,expected);throw t}
    }
}
