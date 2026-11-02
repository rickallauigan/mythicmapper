// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.mythic

import xtr.keymapper.devices.HardwareDevice
import xtr.keymapper.devices.InterfaceEvidence
import xtr.keymapper.keymap.KeymapProfile

object MythicScope {
    const val GAME = "com.mobile.legends"
    /** Fail closed to the one historically observed USB mode. Native probe also checks USB bus. */
    fun supported(device: HardwareDevice): Boolean = device.isConnected &&
        device.vendorId == 0x054c && device.productId == 0x0ce6 &&
        device.name == "Sony Interactive Entertainment GameSir-G8" &&
        InterfaceEvidence.controller(device.sources)

    @JvmStatic fun validateActivation(profile: KeymapProfile) {
        profile.heroGameplay?.let { require(it.calibrated && !profile.disabled) { "Confirm hero HUD, weapon policy and calibration in Hero training setup before activation" } }
        validateProfile(profile)
    }

    @JvmStatic fun validateProfile(profile: KeymapProfile) {
        if(!profile.disabled || profile.aimKeys.isNotEmpty() || profile.keys.isNotEmpty()) profile.heroGameplay?.validate(profile)
        require(profile.packageName == GAME) { "MythicMapper supports MLBB only" }
        require(profile.macroIdMap.isEmpty() && profile.swipeKeys.isEmpty()) { "Scripted input sequences are outside MythicMapper scope" }
        require(profile.keys.all { it.repeatIntervalMs == 0 && xtr.keymapper.controller.ControllerBindings.isController(it.code) }) { "Only direct controller bindings are supported" }
        require(profile.aimKeys.all { it.stick } && (profile.camera == null || profile.camera.stick)) { "Only controller stick aiming is supported" }
        require(profile.leftClick == null && profile.rightClick == null && profile.mouseAimConfig == null && profile.mouseWalk == null) { "Mouse mappings are outside MythicMapper scope" }
        require(profile.dpadArray.all { it == null }) { "Movement remains MLBB native in this MVP" }
    }
}
