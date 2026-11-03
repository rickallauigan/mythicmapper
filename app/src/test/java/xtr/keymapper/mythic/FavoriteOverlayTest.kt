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
    @Test fun circleHas48dpTargetAndDragPersistsWithoutClicking() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller=Robolectric.buildService(TouchPointer::class.java).create()
        val overlay=FavoriteOverlay(controller.get()){fail("Drag must never select")}
        try {
            overlay.show()
            val header=ReflectionHelpers.getField<Button>(overlay,"header")
            val density=controller.get().resources.displayMetrics.density
            assertEquals((48*density+.5f).toInt(),header.layoutParams.width)
            assertNull(header.background)
            fun event(action:Int,x:Float,y:Float){val e=android.view.MotionEvent.obtain(0,10,action,x,y,0);header.dispatchTouchEvent(e);e.recycle()}
            event(0,10f,10f);event(2,180f,220f);event(1,180f,220f)
            assertEquals(View.GONE,ReflectionHelpers.getField<ScrollView>(overlay,"panelScroll").visibility)
            val params=ReflectionHelpers.getField<android.view.WindowManager.LayoutParams>(overlay,"params")
            val prefs=controller.get().getSharedPreferences("mythic_overlay",0)
            assertEquals(params.x,prefs.getInt("x",-1));assertEquals(params.y,prefs.getInt("y",-1))
            assertTrue(params.x>=0&&params.y>=0)
        }finally{overlay.close();controller.destroy();ShadowSettings.setCanDrawOverlays(false)}
    }
    @Test fun stalePositionClampsAndPermissionRevocationDetaches() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller=Robolectric.buildService(TouchPointer::class.java).create()
        controller.get().getSharedPreferences("mythic_overlay",0).edit().putInt("x",Int.MAX_VALUE).putInt("y",Int.MAX_VALUE).commit()
        val overlay=FavoriteOverlay(controller.get()){}
        val owner=Any()
        try {
            EditorSessionGuard.acquire(owner);overlay.show()
            assertEquals(View.GONE,ReflectionHelpers.getField<LinearLayout>(overlay,"root").visibility)
            val params=ReflectionHelpers.getField<android.view.WindowManager.LayoutParams>(overlay,"params")
            assertTrue(params.x in 0 until controller.get().resources.displayMetrics.widthPixels)
            assertTrue(params.y in 0 until controller.get().resources.displayMetrics.heightPixels)
            ShadowSettings.setCanDrawOverlays(false)
            shadowOf(Looper.getMainLooper()).idleFor(1,java.util.concurrent.TimeUnit.SECONDS)
            assertFalse(overlay.isAttached())
        }finally{EditorSessionGuard.release(owner);overlay.close();controller.destroy();ShadowSettings.setCanDrawOverlays(false)}
    }
    @Test fun missingPermissionDoesNotAttachAWindow() {
        ShadowSettings.setCanDrawOverlays(false)
        val controller=Robolectric.buildService(TouchPointer::class.java).create()
        val overlay=FavoriteOverlay(controller.get()){}
        try {overlay.show();assertFalse(ReflectionHelpers.getField<LinearLayout>(overlay,"root").isAttachedToWindow)}
        finally {overlay.close();controller.destroy()}
    }
}
