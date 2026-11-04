// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.controller

import android.view.KeyEvent as K

/** Android key identities mapped to existing logical bindings; these are not raw Linux event codes. */
object ObservedAndroidG8 {
    val keys=mapOf(K.KEYCODE_BUTTON_A to "BTN_GAMEPAD",K.KEYCODE_BUTTON_B to "BTN_EAST",K.KEYCODE_BUTTON_X to "BTN_WEST",K.KEYCODE_BUTTON_Y to "BTN_NORTH",
        K.KEYCODE_BUTTON_L1 to "BTN_TL",K.KEYCODE_BUTTON_R1 to "BTN_TR",K.KEYCODE_BUTTON_L2 to "BTN_TL2",K.KEYCODE_BUTTON_R2 to "BTN_TR2",
        K.KEYCODE_BUTTON_THUMBL to "BTN_THUMBL",K.KEYCODE_BUTTON_THUMBR to "BTN_THUMBR",K.KEYCODE_BUTTON_SELECT to "BTN_SELECT",K.KEYCODE_BUTTON_START to "BTN_START",
        K.KEYCODE_DPAD_UP to "DPAD_UP",K.KEYCODE_DPAD_DOWN to "DPAD_DOWN",K.KEYCODE_DPAD_LEFT to "DPAD_LEFT",K.KEYCODE_DPAD_RIGHT to "DPAD_RIGHT")
}
