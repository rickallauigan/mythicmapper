// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.virtual

import android.os.Bundle
import android.widget.Toast
import xtr.keymapper.activity.MainActivity

/** Reuse the real Home/profile UI without a modal root prompt on the root-free dev target.
 * Backend permission/enforcement and production MainActivity are unchanged. */
class VirtualHomeActivity:MainActivity() {
    override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences(if(intent?.getBooleanExtra("ux_test",false)==true)"ux_home_$name"else name,mode)
    override fun onCreate(savedInstanceState:Bundle?) {
        check(xtr.keymapper.BuildConfig.DEBUG)
        super.onCreate(savedInstanceState)
        title="Development Home · virtual recording only"
    }
    override fun openUniversalEditorFromCompose(profileId:String?) {
        if(intent?.getBooleanExtra("ux_test",false)!=true){super.openUniversalEditorFromCompose(profileId);return}
        startActivity(android.content.Intent(this,xtr.keymapper.ux.UniversalEditorTestActivity::class.java).putExtra("profile",profileId))
    }
    override fun alertRootAccessNotFound(){rootUnavailable()}
    override fun alertRootAccessAndExit(){rootUnavailable()}
    private fun rootUnavailable(){if(intent?.getBooleanExtra("ux_test",false)==true)return;runOnUiThread{if(!isFinishing)Toast.makeText(this,"Root backend unavailable · Virtual G8 recording works without root",Toast.LENGTH_LONG).show()}}
}
