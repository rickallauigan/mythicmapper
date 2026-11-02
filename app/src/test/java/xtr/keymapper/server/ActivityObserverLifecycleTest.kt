// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.server

import android.app.ActivityManager
import android.content.ComponentName
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import xtr.keymapper.ActivityObserver
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class ActivityObserverLifecycleTest {
    @Test fun fallbackDeduplicatesAndStopCancelsFutureCallbacks() {
        val context=RuntimeEnvironment.getApplication()
        val manager=context.getSystemService(ActivityManager::class.java)
        val task=ActivityManager.RunningTaskInfo().apply{topActivity=ComponentName("com.mobile.legends","Game")}
        shadowOf(manager).setTasks(listOf(task))
        val seen=mutableListOf<String>()
        val observer=ActivityObserverService(context,object:ActivityObserver.Stub(){override fun onForegroundActivitiesChanged(name:String){seen+=name}})
        val thread=ReflectionHelpers.getField<android.os.HandlerThread>(observer,"thread")
        shadowOf(thread.looper).idle()
        assertEquals(listOf("com.mobile.legends"),seen)
        shadowOf(thread.looper).idleFor(Duration.ofSeconds(5));assertEquals(1,seen.size)
        observer.stop();task.topActivity=ComponentName("other.app","Other")
        assertFalse(android.os.Handler(thread.looper).post{fail("Stopped observer ran")})
        observer.run();assertEquals(1,seen.size)
    }
}
