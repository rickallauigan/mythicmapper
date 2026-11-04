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
        // HandlerThread.getLooper() returns null once the thread has terminated.
        // Keep the actual Handler/Looper while alive; CI can finish shutdown before
        // the next assertion, whereas a faster local test previously hid that race.
        val looper=requireNotNull(thread.looper)
        val handler=android.os.Handler(looper)
        try {
            shadowOf(looper).idle()
            assertEquals(listOf("com.mobile.legends"),seen)
            shadowOf(looper).idleFor(Duration.ofSeconds(5));assertEquals(1,seen.size)
            observer.refresh();shadowOf(looper).idle();assertEquals(2,seen.size)
            observer.stop();thread.join(2000)
            assertFalse("Observer thread must terminate",thread.isAlive)
            assertNull("A terminated HandlerThread no longer exposes a looper",thread.looper)
            task.topActivity=ComponentName("other.app","Other")
            assertFalse(handler.post{fail("Stopped observer ran")})
            // Late refresh and repeated shutdown must neither notify nor restart polling.
            observer.run();observer.stop();observer.refresh();observer.run()
            assertEquals(listOf("com.mobile.legends","com.mobile.legends"),seen)
        } finally {
            observer.stop();thread.join(2000)
        }
    }
}
