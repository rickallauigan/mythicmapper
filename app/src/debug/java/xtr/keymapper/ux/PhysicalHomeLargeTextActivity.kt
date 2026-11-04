// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.ux

/** Per-Activity resource override; never writes Android's global font setting. */
class PhysicalHomeLargeTextActivity:xtr.keymapper.activity.MainActivity() {
    override fun attachBaseContext(base:android.content.Context) {
        val config=android.content.res.Configuration(base.resources.configuration)
        config.fontScale=maxOf(config.fontScale,1.5f)
        super.attachBaseContext(base.createConfigurationContext(config))
    }
}
