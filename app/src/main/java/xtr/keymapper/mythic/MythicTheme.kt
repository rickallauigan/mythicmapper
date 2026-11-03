// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.mythic

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Shared with the Controller Tester. Static surfaces: no animated glow/gradient layers. */
object MythicColors {
    val Ink=Color(0xff080910)
    val Surface=Color(0xff12121f)
    val Violet=Color(0xffb779ff)
    val Line=Color(0xff38304e)
    val Muted=Color(0xffb8b2ce)
    val Green=Color(0xff64f28a)
}
@Composable fun MythicTheme(content:@Composable ()->Unit) {
    MaterialTheme(colorScheme=darkColorScheme(primary=MythicColors.Violet,onPrimary=Color(0xff221134),
        background=MythicColors.Ink,surface=MythicColors.Surface,onSurface=Color(0xfff3efff),
        onSurfaceVariant=MythicColors.Muted,outline=MythicColors.Line),content=content)
}
