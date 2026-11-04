// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.mythic

import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import org.robolectric.util.ReflectionHelpers
import xtr.keymapper.TouchPointer
import xtr.keymapper.editor.EditorSessionGuard

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class FavoriteOverlayTest {
    @Test fun openingDoesNotSelectAndEditorLeaseHidesSelectorUntilClosed() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller=Robolectric.buildService(TouchPointer::class.java).create()
        val overlay=FavoriteOverlay(controller.get()){fail("Opening overlay must not select a profile")}
        val owner=Any()
        try {
            overlay.show()
            val root=ReflectionHelpers.getField<LinearLayout>(overlay,"root")
            val header=ReflectionHelpers.getField<Button>(overlay,"header")
            header.performClick()
            val scroll=ReflectionHelpers.getField<ScrollView>(overlay,"panelScroll")
            assertEquals(View.VISIBLE,scroll.visibility)
            EditorSessionGuard.acquire(owner);shadowOf(Looper.getMainLooper()).idle()
            assertEquals(View.GONE,root.visibility)
            EditorSessionGuard.release(owner);shadowOf(Looper.getMainLooper()).idle()
            assertEquals(View.VISIBLE,root.visibility)
            header.performClick();assertEquals(View.GONE,scroll.visibility)
            overlay.close();shadowOf(Looper.getMainLooper()).idle();assertFalse(root.isAttachedToWindow)
        } finally {EditorSessionGuard.release(owner);overlay.close();controller.destroy();ShadowSettings.setCanDrawOverlays(false)}
    }
    @Test fun closeAndShowRestoresOneCollapsedOverlayWithoutDuplicateChildren() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller=Robolectric.buildService(TouchPointer::class.java).create()
        val overlay=FavoriteOverlay(controller.get()){}
        try {
            overlay.show();overlay.show();shadowOf(Looper.getMainLooper()).idle()
            val root=ReflectionHelpers.getField<LinearLayout>(overlay,"root")
            assertEquals(2,root.childCount);assertTrue(root.isAttachedToWindow)
            overlay.close();shadowOf(Looper.getMainLooper()).idle();assertFalse(root.isAttachedToWindow)
            overlay.show();shadowOf(Looper.getMainLooper()).idle();assertEquals(2,root.childCount);assertTrue(root.isAttachedToWindow)
        } finally {overlay.close();controller.destroy();ShadowSettings.setCanDrawOverlays(false)}
    }
    @Test fun missingPermissionDoesNotAttachAWindow() {
        ShadowSettings.setCanDrawOverlays(false)
        val controller=Robolectric.buildService(TouchPointer::class.java).create()
        val overlay=FavoriteOverlay(controller.get()){}
        try {overlay.show();assertFalse(ReflectionHelpers.getField<LinearLayout>(overlay,"root").isAttachedToWindow)}
        finally {overlay.close();controller.destroy()}
    }
}
