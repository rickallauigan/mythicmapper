// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.universal

import android.os.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import xtr.keymapper.keymap.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],manifest=Config.NONE)
class UniversalMappingTest {
    private fun action(vararg inputs:String)=UniversalAction(name="Measured synthetic target",inputs=inputs.toSet(),x=500f,y=400f)
    private fun mapping(vararg a:UniversalAction)=UniversalMapping(name="UI TEST Custom Hero",actions=a.toList(),enabled=true,calibrated=true)
    private class Trace {
        data class E(val x:Float,val y:Float,val state:Int,val pointer:Int)
        val events=mutableListOf<E>();val active=mutableSetOf<Int>()
        val sink=xtr.keymapper.mouse.HeldAimHandler.TouchSink{x,y,s,p->
            if(s==1)check(active.add(p));if(s==0)check(active.remove(p));if(s==2)check(p in active)
            events+=E(x,y,s,p)
        }
    }
    @Test fun roundTripAndParcelKeepStableIdentityAndAliases(){
        val d=mapping(action("BTN_EAST","BTN_THUMBL").copy(aim=true))
        assertEquals(d,UniversalCodec.decode(UniversalCodec.encode(d)))
        val lines=UniversalStorage.pack(d);assertEquals(lines,ProfileConfiguration.validate(lines.joinToString("\n")))
        val p=ProfileConfiguration.parse(lines.joinToString("\n"));assertTrue(p.hasControllerBindings())
        val parcel=Parcel.obtain();try{p.writeToParcel(parcel,0);parcel.setDataPosition(0);assertEquals(p.universalMapping,KeymapProfile.CREATOR.createFromParcel(parcel).universalMapping)}finally{parcel.recycle()}
    }
    @Test fun rejectsUnknownFutureDuplicateAndNonfiniteDefinitions(){
        val d=mapping(action("BTN_GAMEPAD"));val text=UniversalCodec.encode(d)
        for(bad in listOf(text.replace("\"schema_version\":2","\"schema_version\":99"),text.replace("ORIGINAL_G8","OTHER"),text.replace("\"HOLD\"","\"MACRO\""),"{\"id\":1,\"id\":2}"))
            assertThrows(Exception::class.java){UniversalCodec.decode(bad)}
        for(a in listOf(action("BTN_GAMEPAD").copy(x=Float.NaN),action("BTN_GAMEPAD").copy(y=-1f),action("BTN_GAMEPAD").copy(x=2280f),action("BTN_GAMEPAD").copy(radius=0f),action("BTN_GAMEPAD").copy(repeatMs=1),action("BTN_GAMEPAD").copy(inputs=setOf("KEY_FAKE"))))
            assertThrows(Exception::class.java){mapping(a).validate()}
        assertThrows(Exception::class.java){mapping(action("BTN_GAMEPAD"),action("BTN_GAMEPAD").copy(x=700f)).validate()}
        assertThrows(Exception::class.java){UniversalStorage.document(UniversalStorage.pack(d)+"BTN_TR 10 20 0")}
    }
    @Test fun blankHasNoInventedAnchorsAndCloneCannotActivate(){
        val blank=UniversalMapping(name="Blank").validate();assertTrue(blank.actions.isEmpty());assertThrows(Exception::class.java){blank.ready()}
        val d=mapping(action("BTN_GAMEPAD"));val copy=d.cloned("Copy");assertNotEquals(d.id,copy.id);assertFalse(copy.enabled);assertFalse(copy.calibrated);assertEquals(d.actions,copy.actions)
        assertFalse(d.edited(d.actions).calibrated)
    }
    @Test fun aliasOwnersJoinAndFinalReleaseEndsTheContact(){
        val t=Trace();val h=UniversalActionHandler(mapping(action("BTN_EAST","BTN_THUMBL")),Handler(Looper.getMainLooper()),t.sink,2280f,1080f)
        h.axes(0f,0f);h.button("BTN_EAST",true);h.button("BTN_THUMBL",true);assertEquals(1,t.active.size);assertEquals(2,h.ownerCount)
        h.button("BTN_EAST",false);assertEquals(1,t.active.size);h.button("BTN_THUMBL",false);assertTrue(t.active.isEmpty());assertEquals(listOf(1,0),t.events.map{it.state});h.close()
    }
    @Test fun tapReleasesWithoutARepeatAndHoldCanOverlap(){
        val t=Trace();val h=UniversalActionHandler(mapping(action("BTN_GAMEPAD").copy(kind=TouchAction.TAP),action("BTN_TR").copy(x=700f)),Handler(Looper.getMainLooper()),t.sink,2280f,1080f)
        h.axes(0f,0f);h.button("BTN_GAMEPAD",true);h.button("BTN_TR",true);assertEquals(2,t.active.size)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));assertEquals(1,t.active.size);assertEquals(2,t.events.count{it.state==1})
        h.close();assertTrue(t.active.isEmpty());shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));assertEquals(2,t.events.count{it.state==1})
    }
    @Test fun repeatIsExplicitCancelableAndUsesCurrentDirectionalAim(){
        val t=Trace();val a=action("BTN_TL2").copy(kind=TouchAction.REPEAT,aim=true,radius=180f,repeatMs=400)
        val h=UniversalActionHandler(mapping(a),Handler(Looper.getMainLooper()),t.sink,2280f,1080f);h.axes(0f,0f);h.axes(1f,0f);h.button("BTN_TL2",true)
        assertEquals(680f,t.events.last().x,0.01f);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(850));assertEquals(3,t.events.count{it.state==1})
        h.close();val count=t.events.size;shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));assertEquals(count,t.events.size);assertTrue(t.active.isEmpty())
    }
    @Test fun skillPrioritySuspendsThenResumesHeldAttack(){
        val t=Trace();val attack=action("BTN_TL2").copy(aim=true);val skill=action("BTN_TR2").copy(aim=true,priority=10,x=800f)
        val h=UniversalActionHandler(mapping(attack,skill),Handler(Looper.getMainLooper()),t.sink,2280f,1080f);h.axes(0f,0f);h.button("BTN_TL2",true);h.axes(1f,0f)
        h.button("BTN_TR2",true);assertEquals(1,t.active.size);assertEquals(880f,t.events.last().x,0.01f)
        h.button("BTN_TR2",false);assertEquals(1,t.active.size);assertEquals(580f,t.events.last().x,0.01f);h.close();assertTrue(t.active.isEmpty())
    }
    @Test fun stickNeutralAndCancellationRequireFreshRearm(){
        val t=Trace();val a=action(UniversalAction.RIGHT_STICK).copy(aim=true)
        val h=UniversalActionHandler(mapping(a),Handler(Looper.getMainLooper()),t.sink,2280f,1080f)
        h.axes(1f,0f);assertTrue(t.active.isEmpty());h.axes(0f,0f);h.axes(1f,0f);assertEquals(1,t.active.size)
        h.cancel("disconnect");assertEquals(0,h.ownerCount);assertTrue(t.active.isEmpty());h.axes(1f,0f);assertTrue(t.active.isEmpty())
        h.axes(0f,0f);h.axes(-1f,0f);assertEquals(420f,t.events.last().x,0.01f);h.axes(0f,0f);assertTrue(t.active.isEmpty());h.close()
    }
    @Test fun changedAspectOrUnconfirmedHudCannotRun(){
        val d=mapping(action("BTN_GAMEPAD"));assertThrows(Exception::class.java){UniversalActionHandler(d,Handler(Looper.getMainLooper()),Trace().sink,1080f,2280f)}
        assertThrows(Exception::class.java){UniversalActionHandler(d.copy(calibrated=false),Handler(Looper.getMainLooper()),Trace().sink,2280f,1080f)}
        val t=Trace();val h=UniversalActionHandler(d,Handler(Looper.getMainLooper()),t.sink,1140f,540f);h.axes(0f,0f);h.button("BTN_GAMEPAD",true);assertEquals(250f,t.events.single().x,0f);h.close()
    }
    @Test fun shortHardwareTapRetainsBoundedContactButCancelReleasesImmediately(){
        val t=Trace();val h=UniversalActionHandler(mapping(action("BTN_GAMEPAD").copy(kind=TouchAction.TAP)),Handler(Looper.getMainLooper()),t.sink,2280f,1080f)
        h.axes(0f,0f);h.button("BTN_GAMEPAD",true);h.button("BTN_GAMEPAD",false)
        assertEquals(1,t.active.size);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(39));assertEquals(1,t.active.size)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));assertTrue(t.active.isEmpty())
        h.button("BTN_GAMEPAD",true);h.cancel("focus lost");assertTrue(t.active.isEmpty());h.close()
    }
    @Test fun recallLikeActionCancelsShootingUntilFreshNeutral(){
        val t=Trace();val attack=action(UniversalAction.RIGHT_STICK).copy(aim=true)
        val recall=action("BTN_WEST").copy(x=900f,kind=TouchAction.TAP,cancelOthers=true)
        val h=UniversalActionHandler(mapping(attack,recall),Handler(Looper.getMainLooper()),t.sink,2280f,1080f)
        h.axes(0f,0f);h.axes(1f,0f);h.button("BTN_WEST",true);h.button("BTN_WEST",false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));assertTrue(t.active.isEmpty())
        h.axes(1f,0f);assertTrue(t.active.isEmpty());h.axes(0f,0f);h.axes(1f,0f);assertEquals(1,t.active.size);h.close()
    }
    @Test fun dragIsOneUndoAndDraftSurvivesRecreation(){
        val original=mapping(action("BTN_GAMEPAD"));val d=UniversalDraft(original)
        d.beginGesture();repeat(20){d.action(d.value.actions.single().copy(x=600f+it))};d.endGesture()
        assertEquals(619f,d.value.actions.single().x!!,0f);d.undo();assertEquals(original,d.value);d.redo()
        val restored=UniversalDraft.restore(d.snapshot());assertEquals(d.value,restored.value);restored.undo();assertEquals(original,restored.value)
        val view=UniversalViewport(1140f,600f,MappingGeometry(),2f,30f,-20f);val point=view.screen(500f,400f);val canonical=view.canonical(point.x,point.y)!!
        assertEquals(500f,canonical.first,.001f);assertEquals(400f,canonical.second,.001f)
    }

    @Test fun storageRejectsStaleUnknownAndRenamedOwnersWithoutMutatingPriorData(){
        val context=org.robolectric.RuntimeEnvironment.getApplication()
        val store=xtr.keymapper.mythic.HeroLibraryStore(context)
        val p=store.clone("Owned universal",UniversalStorage.pack(UniversalMapping(name="Owned universal")))
        try{
            val d=UniversalStorage.read(context,p.id);val prefs=KeymapProfiles(context).sharedPref
            val before=prefs.getStringSet(p.id,null)!!.toSet()
            assertThrows(Exception::class.java){UniversalStorage.save(context,p.id,d.copy(name="Stale"),emptySet(),p.name)}
            assertThrows(Exception::class.java){UniversalStorage.save(context,p.id,d.copy(id=java.util.UUID.randomUUID().toString()),before,p.name)}
            assertEquals(before,prefs.getStringSet(p.id,null))
            val library=store.read();store.update(library,library.copy(profiles=library.profiles.map{if(it.id==p.id)it.copy(name="External rename")else it}))
            assertThrows(Exception::class.java){UniversalStorage.save(context,p.id,d,before,p.name)}
            assertEquals("External rename",UniversalStorage.read(context,p.id).name);assertEquals(before,prefs.getStringSet(p.id,null))
        }finally{store.delete(p.id)}
    }

}
