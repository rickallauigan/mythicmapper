// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.ux

/** Same user-facing editor and runtime; only storage is isolated with the existing Home UX suite. */
class UniversalEditorTestActivity:xtr.keymapper.editor.UniversalEditorActivity() {
    @Volatile var lastPickerUri:android.net.Uri?=null;private set
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:android.content.Intent?){
        if(resultCode==android.app.Activity.RESULT_OK)lastPickerUri=data?.data
        super.onActivityResult(requestCode,resultCode,data)
    }
    override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("ux_home_$name",mode)
}
