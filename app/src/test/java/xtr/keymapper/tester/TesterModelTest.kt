// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.tester

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class TesterModelTest {
    private fun model(analog:Boolean=true):TesterModel=TesterModel().apply{
        connect(TesterDevice(7,"Test fixture",1,2,"local-test","Unknown",true,setOf("A","B","LT"),
            listOf(AxisRange(0,"X",0f,255f),AxisRange(1,"Y",0f,255f))+(if(analog)listOf(AxisRange(2,"LTRIGGER",-1f,1f))else emptyList()),0 to 1,null,if(analog)2 else null,null),0)
        ready=true
    }
    @Test fun buttonRequiresObservedPressAndReleaseAndSimultaneousStatesSurvive(){val m=model();m.key("A",96,false,0,1);assertFalse(m.snapshot().checks.first{it.id=="A"}.passed)
        m.key("A",96,true,0,2);m.key("B",97,true,0,3);assertEquals(setOf("A","B"),m.snapshot().held)
        m.key("A",96,false,0,4);assertEquals(setOf("B"),m.snapshot().held);assertTrue(m.snapshot().checks.first{it.id=="A"}.passed)
        m.key("B",97,true,0,5);assertEquals(1,m.duplicates)
    }
    @Test fun androidActionsRemainAndroidActionsAndNonfiniteAxesCannotPass(){val m=model();m.key("A",96,true,0,1);assertEquals(0f,m.snapshot().events.last().raw,0f)
        m.key("A",96,false,0,2);assertEquals(1f,m.snapshot().events.last().raw,0f)
        m.axes(mapOf(0 to Float.NaN,1 to Float.POSITIVE_INFINITY),3);assertTrue(m.snapshot().raw.isEmpty())
        val before=m.snapshot().totalEvents;m.clearLog();assertEquals(before,m.snapshot().totalEvents)
    }
    @Test fun reconnectRetainsSameDeviceChecklistButClearsTransientState(){val m=model();val d=m.device!!;m.key("A",96,true,0,1);m.key("A",96,false,0,2)
        m.connect(null,3);m.connect(d.copy(id=8),4);assertTrue(m.snapshot().checks.first{it.id=="A"}.passed);assertTrue(m.snapshot().held.isEmpty())
        m.connect(d.copy(descriptor="different-test-device"),5);assertFalse(m.snapshot().checks.any{it.passed})
    }
    @Test fun transportMetadataDoesNotInventAReconnectOrReleaseAHeldButton(){val m=model();val initial=m.snapshot().lastConnection
        m.key("A",96,true,0,1);m.connect(m.device!!.copy(transport="USB observed"),2)
        assertEquals(initial,m.snapshot().lastConnection);assertEquals(setOf("A"),m.snapshot().held)
        m.key("A",96,false,0,3);assertTrue(m.snapshot().checks.first{it.id=="A"}.passed)
    }
    @Test fun focusLossReleasesButtonsButRetainsLastAxisForExplicitCenterMeasurement(){val m=model();m.axes(mapOf(0 to 127.5f,1 to 127.5f),1);m.key("A",96,true,0,2)
        m.clearHeld();assertTrue(m.snapshot().held.isEmpty());assertFalse(m.snapshot().raw.isEmpty())
        m.measureCenter();assertEquals(0f,m.snapshot().left.drift!!,0f)
        m.clearTransient();assertTrue(m.snapshot().raw.isEmpty())
    }
    @Test fun asymmetricNormalizationAndCenter(){val a=AxisRange(0,"X",0f,255f);assertEquals(-1f,a.signed(0f),0f);assertEquals(1f,a.signed(255f),0f);assertEquals(0f,a.signed(127.5f),0f)
        assertEquals(0f,AxisRange(0,"invalid",1f,1f).signed(9f),0f);assertEquals(.5f,AxisRange(0,"T",-1f,1f).pressure(0f),0f)
    }
    @Test fun smallMovementDoesNotCertifyRangeAndFullCircleDoes(){val m=model();m.axes(mapOf(0 to 130f,1 to 128f),1)
        assertFalse(m.snapshot().checks.any{it.passed});m.measureCenter();assertTrue(m.snapshot().left.drift!!<m.thresholds.deadZone)
        for(i in 0..8){val angle=i*PI/4;m.axes(mapOf(0 to (127.5+127.5*cos(angle)).toFloat(),1 to (127.5+127.5*sin(angle)).toFloat()),i+2L)}
        assertTrue(m.snapshot().checks.first{it.id=="L-dir"}.passed);assertTrue(m.snapshot().checks.first{it.id=="L-range"}.passed)
    }
    @Test fun triggerTravelRequiresRestAndFullTravel(){val m=model();m.axes(mapOf(2 to .9f),1);assertFalse(m.snapshot().checks.first{it.id=="LT-range"}.passed)
        m.axes(mapOf(2 to -1f),2);assertTrue(m.snapshot().checks.first{it.id=="LT-range"}.passed)
        assertFalse(model(false).snapshot().checks.any{it.id=="LT-range"})
    }
    @Test fun disconnectClearsHeldAndRawAndUnsupportedOrUnsafeInputCannotPass(){val m=model();m.key("A",96,true,0,1);m.connect(null,2);assertTrue(m.snapshot().held.isEmpty());assertTrue(m.snapshot().raw.isEmpty());assertEquals("Disconnected",m.snapshot().status)
        val d=model().device!!;m.connect(d.copy(supported=false),3);m.key("A",96,true,0,4);assertTrue(m.snapshot().held.isEmpty());assertTrue(m.snapshot().checks.isEmpty())
        m.connect(d,5);m.ready=false;m.key("A",96,true,0,6);assertTrue(m.snapshot().held.isEmpty());assertEquals("Input unavailable",m.snapshot().status)
    }
    @Test fun boundedLogPauseClearResetAndOverflow(){val m=model();repeat(700){m.key("A",96,it%2==0,0,it.toLong())};assertEquals(500,m.snapshot().eventCount)
        m.pauseLog();val frozen=m.snapshot().events;m.key("B",97,true,0,701);assertEquals(frozen,m.snapshot().events)
        m.reset();assertFalse(m.snapshot().checks.any{it.passed});assertEquals(500,m.snapshot().eventCount)
        m.clearLog();assertEquals(0,m.snapshot().eventCount);assertTrue(m.snapshot().events.isEmpty());m.overflow();assertTrue(m.snapshot().held.isEmpty());assertEquals(1,m.dropped)
        assertFalse(m.export().contains("local-test"))
    }
    @Test fun triggerAliasAxesUseUnipolarNormalizationEvenWhenPrimaryAxisExists(){val m=model();val d=m.device!!
        m.connect(d.copy(ranges=d.ranges+AxisRange(23,"BRAKE",0f,1f)),0);m.axes(mapOf(23 to 0f),1)
        assertEquals(0f,m.snapshot().events.last().normalized!!,0f)
        m.axes(mapOf(23 to .5f),2);assertEquals(.5f,m.snapshot().events.last().normalized!!,0f)
    }
    @Test fun denominatorAndThresholdChangesReflectCapabilities(){val m=model();assertEquals(6,m.snapshot().checks.size)
        m.key("A",96,true,0,1);m.key("A",96,false,0,2);m.configure(Thresholds(travel=.95f));assertFalse(m.snapshot().checks.any{it.passed})
        assertEquals(5,model(false).snapshot().checks.size)
    }
    @Test fun analogOnlyAndHatControlsAreObservedWithoutInventedDigitalCapabilities(){val m=model();val d=m.device!!
        m.connect(d.copy(keys=emptySet(),ranges=d.ranges+AxisRange(15,"HAT_X",-1f,1f)),0)
        m.axes(mapOf(2 to 1f,15 to -1f),1);assertEquals(setOf("LT","←"),m.snapshot().held)
        m.axes(mapOf(2 to -1f,15 to 0f),2);assertTrue(m.snapshot().checks.first{it.id=="LT"}.passed);assertTrue(m.snapshot().checks.first{it.id=="←"}.passed)
    }
}
