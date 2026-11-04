// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.ux

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.widget.TextView
import xtr.keymapper.mythic.*
import xtr.keymapper.controller.HeroGameplayConfig

/** Explicit debug preview: real overlay, isolated preferences, no mapper or root backend. */
class BubblePreviewActivity:Activity() {
    lateinit var overlay:FavoriteOverlay
    var selections=0;private set
    private lateinit var store:HeroLibraryStore
    private lateinit var profile:HeroProfile
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        check(xtr.keymapper.BuildConfig.DEBUG)
        setContentView(TextView(this).apply{text="SIMULATED · Overlay preview\nNo mapper or gameplay touches\nTap M, drag to move, expand favorites";setTextColor(-1);textSize=20f;setPadding(36,160,36,36);setBackgroundColor(0xff080910.toInt())})
        val isolated=object:ContextWrapper(this){override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("ux_preview_$name",mode)}
        store=HeroLibraryStore(isolated)
        profile=store.clone("UI TEST · Kimmy",HeroTrainingProfiles.draft(HeroGameplayConfig.Hero.KIMMY),null)
        val before=store.read();store.update(before,before.assign(0,profile.id))
        overlay=FavoriteOverlay(isolated,{profile.id},{check(it==profile.id);selections++},{true})
        overlay.show()
    }
    override fun onDestroy(){if(::overlay.isInitialized)overlay.close();if(::profile.isInitialized)store.delete(profile.id);super.onDestroy()}
}
