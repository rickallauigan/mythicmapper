// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.mythic

import android.content.Intent
import android.os.*
import android.app.KeyguardManager
import android.view.View
import android.widget.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import org.robolectric.util.ReflectionHelpers
import xtr.keymapper.TouchPointer
import xtr.keymapper.IRemoteService
import xtr.keymapper.editor.EditorSessionGuard
import xtr.keymapper.server.RemoteServiceHelper
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class OverlayUnlockPresentationTest {
    @Test fun unlockRestoresSelectorOutsideGameWithoutStartingMappingAndHonorsLease() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller=Robolectric.buildService(TouchPointer::class.java).create();val service=controller.get()
        val old=ReflectionHelpers.getStaticField<IRemoteService?>(RemoteServiceHelper::class.java,"service")
        var starts=0
        val remote=Proxy.newProxyInstance(javaClass.classLoader,arrayOf(IRemoteService::class.java)){_,method,_->
            when(method.name){"asBinder"->Binder();"startServer"->{starts++;null};"isActive"->false;"isMappingPaused"->true;else->null}
        }as IRemoteService
        ReflectionHelpers.setStaticField(RemoteServiceHelper::class.java,"service",remote)
        val profile="00000000-0000-0000-0000-000000000123"
        service.getSharedPreferences("profiles",0).edit().putStringSet(profile,setOf("APPLICATION com.mobile.legends")).commit()
        ReflectionHelpers.setField(service,"mythicProfileChoice",profile)
        assertNull(service.selectedProfile);assertEquals(profile,service.selectedProfileForUi())
        val overlay=FavoriteOverlay(service){fail("Presentation must never select")}
        ReflectionHelpers.setField(service,"favoriteOverlay",overlay)
        val receiver=ReflectionHelpers.getField<android.content.BroadcastReceiver>(service,"screenOff")
        val power=service.getSystemService(PowerManager::class.java);val keyguard=service.getSystemService(KeyguardManager::class.java)
        val owner=Any()
        try {
            overlay.show();assertTrue(overlay.isAttached())
            Shadows.shadowOf(power).setIsInteractive(false);receiver.onReceive(service,Intent(Intent.ACTION_SCREEN_OFF))
            assertFalse(overlay.isAttached())
            Shadows.shadowOf(power).setIsInteractive(true);Shadows.shadowOf(keyguard).setIsKeyguardLocked(true)
            receiver.onReceive(service,Intent(Intent.ACTION_SCREEN_ON));Shadows.shadowOf(Looper.getMainLooper()).idle();assertFalse(overlay.isAttached())
            EditorSessionGuard.acquire(owner);Shadows.shadowOf(keyguard).setIsKeyguardLocked(false)
            receiver.onReceive(service,Intent(Intent.ACTION_USER_PRESENT));Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue(overlay.isAttached())
            val root=ReflectionHelpers.getField<LinearLayout>(overlay,"root");assertEquals(View.GONE,root.visibility)
            EditorSessionGuard.release(owner);Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(View.VISIBLE,root.visibility)
            ReflectionHelpers.getField<Button>(overlay,"header").performClick()
            val panel=ReflectionHelpers.getField<LinearLayout>(overlay,"panel")
            assertTrue("Paused selection still supports editor access",panel.getChildAt(6).isEnabled)
            assertNull("Presentation must not alter runtime selection",service.selectedProfile);assertEquals(0,starts)
        }finally{EditorSessionGuard.release(owner);overlay.close();controller.destroy();ReflectionHelpers.setStaticField(RemoteServiceHelper::class.java,"service",old);ShadowSettings.setCanDrawOverlays(false)}
    }
}
