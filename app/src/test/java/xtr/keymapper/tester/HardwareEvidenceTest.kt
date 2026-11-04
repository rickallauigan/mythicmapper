// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.tester

import org.junit.Assert.*
import org.junit.Test

class HardwareEvidenceTest {
    @Test fun retainedEvidenceSurvivesRollingLogEviction(){
        val model=TesterModel();model.connect(TesterDevice(1,"fixture",1,2,"fixture","test",true,setOf("A","B"),listOf(AxisRange(0,"X",-1f,1f),AxisRange(1,"Y",-1f,1f)),0 to 1,null,null,null),0);model.ready=true
        model.key("A",96,true,0,1);model.key("A",96,false,0,2)
        repeat(900){model.axes(mapOf(0 to if(it%2==0)1f else -1f,1 to 0f),it+3L)}
        assertEquals(500,model.snapshot().eventCount)
        val a=model.snapshot().guided.controls.single{it.label=="A"};assertEquals(1,a.pairs);assertEquals(listOf(true,false),a.samples.map{it.down})
        assertFalse(model.snapshot().events.any{it.label=="A"})
    }
    @Test fun overlapRequiresObservedHoldAndActualReleaseAndInterruptCannotPass(){
        val book=HardwareEvidence();val caps=setOf("A","B")
        book.observeHeld(caps,caps,100);book.observeHeld(setOf("A"),caps,180)
        assertFalse(book.snapshot().overlaps.single().released)
        book.interrupt();book.observeHeld(emptySet(),caps,200);assertTrue(book.snapshot().overlaps.isEmpty())
        book.observeHeld(caps,caps,300);book.observeHeld(setOf("B"),caps,400);book.observeHeld(emptySet(),caps,450)
        assertEquals(100L,book.snapshot().overlaps.single().durationMs);assertTrue(book.snapshot().overlaps.single().released)
    }
    @Test fun disconnectIsObservedAndDoesNotInventKeyUp(){
        val book=HardwareEvidence();book.record("A","Android KEY_ACTION",96,0f,true,false,1);book.armDisconnect()
        book.disconnected(setOf("A"),2,2,true);book.reconnected()
        assertEquals(0,book.snapshot().controls.single().ups);assertEquals(0,book.snapshot().controls.single().pairs)
        assertEquals(listOf("A"),book.snapshot().disconnects.single().heldBefore);assertTrue(book.snapshot().disconnects.single().reconnected)
    }
    @Test fun repeatWithoutObservedInitialDownCannotCertifyLifecycle(){
        val model=TesterModel();model.connect(TesterDevice(1,"fixture",1,2,"fixture","test",true,setOf("A"),emptyList(),null,null,null,null),0);model.ready=true
        model.key("A",96,true,1,1);model.key("A",96,false,0,2)
        assertEquals(0,model.snapshot().guided.controls.single().pairs)
        assertFalse(model.snapshot().checks.single{it.id=="A"}.passed)
    }
    @Test fun disconnectClearingIsMeasuredAfterReducerCleanup(){
        val model=TesterModel();model.connect(TesterDevice(1,"fixture",1,2,"fixture","test",true,setOf("A"),listOf(AxisRange(0,"X",-1f,1f)),null,null,null,null),0);model.ready=true
        model.key("A",96,true,0,1);model.axes(mapOf(0 to .8f),2);model.evidence.armDisconnect();model.connect(null,3)
        val state=model.snapshot();assertTrue(state.held.isEmpty());assertTrue(state.raw.isEmpty())
        assertTrue(state.guided.disconnects.single().cleared);assertEquals(listOf("A"),state.guided.disconnects.single().heldBefore)
    }
    @Test fun rearPromptRecordsReportedCodeWithoutNewIdentity(){
        val book=HardwareEvidence();book.startAliases()
        repeat(2){book.record("L3","Android KEY_ACTION",106,0f,true,false,it*2L);book.record("L3","Android KEY_ACTION",106,1f,false,true,it*2L+1)}
        assertTrue(book.snapshot().aliasReports[1].contains("rear L4"));assertTrue(book.snapshot().aliasReports[1].contains("observed L3"))
        assertEquals(setOf("L3"),book.snapshot().controls.map{it.label}.toSet())
    }
}
