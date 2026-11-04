// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.mythic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w400dp-h820dp-port")
class MythicHomeUiTest {
    @get:Rule val compose=createComposeRule()
    private fun render(onClone:(HeroPreset)->Unit={}) {
        compose.setContent {MythicHomeScreen(HeroLibrary(),false,false,null,{}, {},onClone,{}, {}, {}, {}, {_,_->}, {_,_->}, {}, {}, {}, {})}
    }
    @Test fun fiveSlotsAreReachableAndDisconnectedUseIsDisabled() {
        render()
        compose.onNodeWithTag("mythic-home").performScrollToNode(hasTestTag("favorite-strip"))
        compose.onNodeWithTag("favorite-strip").performScrollToNode(hasTestTag("favorite-4"))
        compose.onNodeWithText("5. Empty slot").assertIsDisplayed()
        compose.onNodeWithTag("favorite-use-4").assertIsNotEnabled()
    }
    @Test fun cloneIsExplicitAndSearchFiltersCatalog() {
        var clones=0;render{clones++}
        compose.onNodeWithTag("mythic-home").performScrollToNode(hasTestTag("clone-s10e-g8-tuned-v03-single-touch-r1"))
        Assert.assertEquals(0,clones)
        compose.onNodeWithTag("clone-s10e-g8-tuned-v03-single-touch-r1").performClick();Assert.assertEquals(1,clones)
        compose.onNodeWithTag("mythic-home").performScrollToNode(hasText("Search profiles and starters"))
        compose.onNodeWithText("Search profiles and starters").performTextInput("no-match")
        compose.onNodeWithText("Clone to personal profile").assertDoesNotExist()
    }
    @Test fun deletingAndReplacingRequireExplicitConfirmation() {
        val first=HeroProfile("00000000-0000-0000-0000-000000000001","UI Alpha",null)
        val second=HeroProfile("00000000-0000-0000-0000-000000000002","UI Beta",null)
        val library=HeroLibrary(listOf(first,second),listOf(first.id,null,null,null,null))
        var assigned:String?=null;var deleted=0
        compose.setContent{MythicHomeScreen(library,false,false,null,{}, {},{}, {}, {},{deleted++}, {}, {_,id->assigned=id},{_,_->},{},{},{},{})}
        compose.onNodeWithTag("mythic-home").performScrollToNode(hasTestTag("favorite-strip"))
        compose.onNodeWithContentDescription("Manage favorite 1").performClick()
        compose.onNodeWithText("Assign").performClick();compose.onNodeWithText("UI Beta").performClick()
        Assert.assertNull(assigned);compose.onNodeWithText("Replace favorite 1?").assertIsDisplayed()
        compose.onNodeWithText("Replace",useUnmergedTree=true).performClick();Assert.assertEquals(second.id,assigned)
        compose.onNodeWithTag("mythic-home").performScrollToNode(hasTestTag("profile-${first.id}"))
        compose.onNode(hasText("Delete")and hasAnyAncestor(hasTestTag("profile-${first.id}"))).performClick()
        Assert.assertEquals(0,deleted);compose.onNodeWithText("Delete personal mapping?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick();Assert.assertEquals(0,deleted)
    }
    @Test fun creationUsesExistingStartersAndVirtualActionIsExplicit() {
        var clones=0;var virtual=0
        compose.setContent{MythicHomeScreen(HeroLibrary(),false,false,null,{}, {},{clones++},{},{},{},{},{_,_->},{_,_->},{},{},{},{},onVirtual={virtual++})}
        compose.onNodeWithText("Virtual G8 · SIMULATED").performClick();Assert.assertEquals(1,virtual)
        compose.onNodeWithText("Create mapping").performClick()
        compose.onNodeWithText("Create from a starter").assertIsDisplayed();Assert.assertEquals(0,clones)
        compose.onNodeWithText(HeroCatalog.presets.first().name).performClick();Assert.assertEquals(1,clones)
    }
    @Test @Config(qualifiers="w320dp-h640dp-port")
    fun largeFontKeepsActionsReachableAndSimulatorAbsentByDefault() {
        compose.setContent{androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f,1.6f)){
            MythicHomeScreen(HeroLibrary(),false,false,null,{},{},{},{},{},{},{},{_,_->},{_,_->},{},{},{},{})
        }}
        compose.onNodeWithTag("mythic-home").performScrollToNode(hasText("Controller Tester"))
        compose.onNodeWithText("Controller Tester").assertIsDisplayed()
        compose.onNodeWithText("Virtual G8 · SIMULATED").assertDoesNotExist()
        compose.onNodeWithTag("mythic-home").performScrollToNode(hasText("Create mapping"))
        compose.onNodeWithText("Create mapping").performClick();compose.onNodeWithText("Create from a starter").assertIsDisplayed()
    }
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun portraitHomeRendersPreview() {
        render()
        val output=java.io.File("build/ui-previews/mythic-home.png");output.parentFile.mkdirs()
        output.outputStream().use {Assert.assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
        Assert.assertTrue(output.length()>10000)
    }
}
