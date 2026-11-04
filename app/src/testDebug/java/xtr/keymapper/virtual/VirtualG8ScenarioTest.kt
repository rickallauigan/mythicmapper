// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.virtual

import android.os.Looper
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration

/** Same debug source/runtime/sink and JSON cases as AVD; host clock is virtual. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class VirtualG8ScenarioTest {
    private fun scenario(name:String) {
        val prefs=RuntimeEnvironment.getApplication().getSharedPreferences("profiles",0)
        val before=prefs.all.toMap()
        val data=JSONObject(File("../tools/scenarios/$name.json").readText())
        assertEquals(1,data.getInt("schema"));assertEquals("synthetic",data.getString("source"))
        val session=VirtualG8Session()
        try {
            val steps=data.getJSONArray("steps");var time=0L
            for(i in 0 until steps.length()) {
                val step=steps.getJSONObject(i);val t=step.getLong("at_ms")
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(t-time));time=t
                if(step.has("action"))session.command(step)
                step.optJSONObject("expect")?.let{session.assertState(it)}
            }
        }finally{session.close()}
        assertEquals(0,session.report().getInt("final_pointers"));assertEquals(0,session.dropped)
        assertEquals(0,session.report().getInt("platform_touch_submissions"));assertEquals(before,prefs.all)
    }
    @Test fun basic()=scenario("basic-input")
    @Test fun bennett()=scenario("beatrix-bennett")
    @Test fun kimmy()=scenario("kimmy-skill-resume")
    @Test fun lockUnlock()=scenario("lock-unlock")
    @Test fun disconnect()=scenario("disconnect-reconnect")
    @Test fun switching()=scenario("profile-switch")
    @Test fun diagnosticExclusivity()=scenario("diagnostics-exclusive")
    @Test fun invalidInputsAndClosedSessionFailWithoutTouchLeaks() {
        val session=VirtualG8Session()
        try {
            for(step in listOf("{\"action\":\"axis\",\"code\":\"ABS_RX\",\"value\":256}","{\"action\":\"button\",\"code\":\"KEY_POWER\",\"down\":true}","{\"action\":\"unknown\"}")) {
                try{session.command(JSONObject(step));fail("Invalid virtual command accepted")}catch(_:IllegalArgumentException){}catch(_:IllegalStateException){}
            }
        }finally{session.close()}
        assertEquals(0,session.snapshot().getInt("active_pointers"))
        try{session.command(JSONObject("{\"action\":\"reset\"}"));fail("Closed session accepted input")}catch(_:IllegalStateException){}
    }
    @Test fun watchdogReleasesMissingButtonUp() {
        val session=VirtualG8Session();session.key("BTN_GAMEPAD",true)
        assertEquals(1,session.snapshot().getInt("active_pointers"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61))
        assertEquals(0,session.report().getInt("final_pointers"));session.close()
    }
    @Test fun sourceHistoryIsBoundedAndOverflowIsReported() {
        val session=VirtualG8Session()
        try{repeat(1200){session.axis("ABS_X",it%256)}}finally{session.close()}
        assertTrue(session.inputs.size<=2000);assertTrue(session.inputDropped>0)
        assertEquals(0,session.report().getInt("final_pointers"))
    }

    @Test fun intentActivationRefusesRunningMapperWithoutCrashingOrAcquiringLease() {
        val app=RuntimeEnvironment.getApplication()
        val manager=app.getSystemService(android.app.ActivityManager::class.java)
        val service=android.app.ActivityManager.RunningServiceInfo().apply{
            this.service=android.content.ComponentName(app.packageName,"xtr.keymapper.TouchPointer");started=true
        }
        shadowOf(manager).setServices(listOf(service))
        val controller=org.robolectric.Robolectric.buildActivity(VirtualG8Activity::class.java,
            android.content.Intent(app,VirtualG8Activity::class.java).putExtra("enable_virtual_g8",true))
        try{val activity=controller.create().start().resume().get();assertNull(activity.session);assertFalse(activity.isFinishing);assertFalse(xtr.keymapper.editor.EditorSessionGuard.active)}
        finally{controller.pause().stop().destroy();shadowOf(manager).setServices(emptyList())}
    }

    @Test fun duplicateResumeNotificationsDoNotReplaceTheLiveOwner() {
        val session=VirtualG8Session()
        session.command(JSONObject("""{"action":"profile","profile":"BENNETT"}"""))
        session.axis("ABS_RX",255);session.key("BTN_TL2",true)
        val before=session.totalTouches
        try {
            session.command(JSONObject("""{"action":"screen_on","unlocked":true}"""))
            session.command(JSONObject("""{"action":"diagnostics_off"}"""))
            assertEquals(before,session.totalTouches)
            session.command(JSONObject("""{"action":"screen_on","unlocked":false}"""))
            assertEquals(0,session.snapshot().getInt("active_pointers"))
        } finally {session.close()}
        assertEquals(0,session.report().getInt("final_pointers"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(0,session.report().getInt("final_pointers"))
    }

}
