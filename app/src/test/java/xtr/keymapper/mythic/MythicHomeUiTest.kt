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
        compose.onNodeWithTag("mythic-home").performScrollToNode(hasTestTag("favorite-4"))
        compose.onNodeWithText("5. Empty slot").assertIsDisplayed()
        compose.onAllNodesWithText("Use").fetchSemanticsNodes().forEach {node ->
            compose.onNode(hasText("Use") and hasAnyAncestor(hasTestTag("favorite-4"))).assertIsNotEnabled()
        }
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
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun portraitHomeRendersPreview() {
        render()
        val output=java.io.File("build/ui-previews/mythic-home.png");output.parentFile.mkdirs()
        output.outputStream().use {Assert.assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
        Assert.assertTrue(output.length()>10000)
    }
}
