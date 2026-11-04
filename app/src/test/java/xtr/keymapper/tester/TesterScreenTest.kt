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
    @Test fun guidedNavigationRetainsObservedEvidenceAndOnlyArmsPhysicalDisconnect(){
        val model=TesterModel();model.connect(TesterDevice(1,"fixture",1,2,"fixture","test",true,setOf("A","B"),emptyList(),null,null,null,null),0);model.ready=true
        model.key("A",96,true,0,1);model.key("A",96,false,0,2)
        var armed=0
        compose.setContent{TesterScreen(model.snapshot(),{},{},{},{},{},{},{},onArmDisconnect={armed++})}
        compose.onNodeWithContentDescription("Guided tab").performClick()
        compose.onNodeWithText("✓ A · ↓1 ↑1 paired 1").assertExists()
        compose.onNodeWithText("○ B").assertExists()
        compose.onNodeWithText("Arm disconnect test").performScrollTo().performClick()
        compose.runOnIdle{org.junit.Assert.assertEquals(1,armed)}
        // Panel changes must retain all tab labels and usable navigation targets.
        compose.onNodeWithContentDescription("Diagnostics tab").performClick()
        compose.onNodeWithText("Test Checklist").assertExists()
        compose.onNodeWithContentDescription("Guided tab").performClick()
        for(label in listOf("Controller","Diagnostics","Guided")){
            compose.onNodeWithText(label,useUnmergedTree=true).assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Controller tab").performClick()
        compose.onNodeWithText("GameSir G8 Galileo Tester").assertExists()
    }

}
