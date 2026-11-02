// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.tester

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class TesterScreenTest {
    @get:Rule val compose=createComposeRule()
    @Test fun disconnectedNavigationNeverInventsApplicableChecks(){
        compose.setContent{TesterScreen(TesterState(),{},{},{},{},{},{},{})}
        compose.onNodeWithText("GameSir G8 Galileo Tester").assertExists()
        compose.onNodeWithContentDescription("Diagnostics tab").performClick()
        compose.onNodeWithText("0 / 0 (0%)").assertExists()
        compose.onNodeWithText("Reset Test").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Controller tab").performClick()
        compose.onNodeWithText("GameSir G8 Galileo Tester").assertExists()
    }
}
