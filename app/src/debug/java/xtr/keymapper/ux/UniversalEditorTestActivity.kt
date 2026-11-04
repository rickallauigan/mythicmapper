// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.ux

/** Same user-facing editor and runtime; only storage is isolated with the existing Home UX suite. */
class UniversalEditorTestActivity:xtr.keymapper.editor.UniversalEditorActivity() {
    override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("ux_home_$name",mode)
}
