// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.virtual

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.*
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import xtr.keymapper.editor.EditorSessionGuard
import java.io.File

/** Explicit shell instrumentation only. No listener, network endpoint or privileged injection. */
class VirtualG8Instrumentation:Instrumentation() {
    private lateinit var arguments:Bundle
    override fun onCreate(arguments:Bundle?){this.arguments=arguments?:Bundle();start()}
    override fun onStart() {
        var code=Activity.RESULT_OK
        val report=try{if(arguments.getString("smoke")=="true")smoke()else replay(JSONObject(String(Base64.decode(requireNotNull(arguments.getString("scenario")),Base64.DEFAULT))))}
        catch(e:Throwable){code=Activity.RESULT_CANCELED;JSONObject().put("status","FAIL").put("error",e.javaClass.simpleName+": "+e.message).put("stack",e.stackTraceToString().take(4000))}
        if(arguments.containsKey("scenario"))report.put("request_id",runCatching{JSONObject(String(Base64.decode(arguments.getString("scenario"),Base64.DEFAULT))).optString("request_id")}.getOrDefault(""))
        val file=File(targetContext.filesDir,"virtual-g8/result.json");file.parentFile!!.mkdirs();file.writeText(report.toString(2))
        finish(code,Bundle().apply{putString("virtual_g8",report.toString())})
    }
    private fun onMain(action:()->Unit) {
        var failure:Throwable?=null
        runOnMainSync{try{action()}catch(e:Throwable){failure=e}}
        failure?.let{throw it}
    }
    private fun replay(scenario:JSONObject):JSONObject {
        require(scenario.getInt("schema")==1);require(scenario.getString("source")=="synthetic")
        val steps=scenario.getJSONArray("steps");require(steps.length() in 1..2000)
        var previous=0L
        for(i in 0 until steps.length()){val t=steps.getJSONObject(i).getLong("at_ms");require(t in previous..60000);previous=t}
        val activity=startActivitySync(Intent(targetContext,VirtualG8Activity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as VirtualG8Activity
        var session:VirtualG8Session?=null;val observations=JSONArray()
        try {
            val focusDeadline=SystemClock.elapsedRealtime()+10000
            while(!activity.hasWindowFocus()&&SystemClock.elapsedRealtime()<focusDeadline)SystemClock.sleep(20)
            check(activity.hasWindowFocus()){"Virtual surface never acquired focus"}
            onMain{activity.enable(false);session=activity.session}
            var previousTime=0L
            for(i in 0 until steps.length()) {
                val step=steps.getJSONObject(i);val remaining=step.getLong("at_ms")-previousTime
                previousTime=step.getLong("at_ms")
                if(remaining>0)SystemClock.sleep(remaining)
                onMain{
                    check(activity.hasWindowFocus()&&!activity.isFinishing){"Virtual surface lost foreground; aborting"}
                    if(step.has("action"))session!!.command(step)
                    step.optJSONObject("expect")?.let{session!!.assertState(it)}
                    observations.put(session!!.snapshot().put("step",i))
                }
            }
            var result:JSONObject?=null
            onMain{session!!.close();result=session!!.report()}
            check(result!!.getInt("final_pointers")==0&&result!!.getInt("trace_dropped")==0)
            return result!!.put("request_id",scenario.optString("request_id")).put("schema",1).put("id",scenario.getString("id")).put("status","PASS")
                .put("provenance",scenario.getString("source")).put("observations",observations)
        }catch(e:Throwable){
            onMain{session?.close()}
            return (session?.report()?:JSONObject()).put("status","FAIL").put("id",scenario.optString("id"))
                .put("request_id",scenario.optString("request_id")).put("error",e.javaClass.simpleName+": "+e.message).put("observations",observations)
        }finally{onMain{activity.disable();activity.finish()};waitForIdleSync();check(!EditorSessionGuard.active){"Virtual UI lease leaked"}}
    }
    private fun smoke():JSONObject {
        val checks=JSONArray()
        fun launch(type:Class<out Activity>,extras:(Intent)->Unit={}) {
            val intent=Intent(targetContext,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);extras(intent)
            val activity=startActivitySync(intent)
            try{waitForIdleSync();check(!activity.isFinishing){"Activity finished during smoke: ${type.simpleName}"};checks.put(type.simpleName)}finally{onMain{activity.finish()};waitForIdleSync()}
        }
        launch(xtr.keymapper.activity.MainActivity::class.java)
        val store=xtr.keymapper.mythic.HeroLibraryStore(targetContext);val before=store.read()
        check(before.favorites.size==5);check(xtr.keymapper.mythic.HeroCatalog.presets.isNotEmpty());checks.put("offline-library-and-five-slots")
        launch(xtr.keymapper.tester.TesterActivity::class.java)
        // Own disposable profile only; no rewrite or favorite assignment.
        val profile=store.clone("SIMULATED smoke only",xtr.keymapper.mythic.HeroTrainingProfiles.draft(xtr.keymapper.controller.HeroGameplayConfig.Hero.KIMMY),null)
        try{launch(xtr.keymapper.editor.EditorActivity::class.java){it.putExtra(xtr.keymapper.editor.EditorActivity.PROFILE_NAME,profile.id);it.putExtra(xtr.keymapper.editor.EditorActivity.VISUAL,true)}}finally{store.delete(profile.id)}
        val activity=startActivitySync(Intent(targetContext,VirtualG8Activity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as VirtualG8Activity
        try {
            onMain{activity.enable();activity.session!!.command(JSONObject().put("action","axis").put("code","ABS_RX").put("value",255))}
            waitForIdleSync();check(activity.session!!.snapshot().getInt("active_pointers")==1){"Virtual UI did not observe shooting contact"}
            onMain{activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT};SystemClock.sleep(600)
            check(!activity.isDestroyed){"Virtual Activity recreated on orientation"}
            onMain{activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE};SystemClock.sleep(600)
            check(!activity.isDestroyed){"Virtual Activity recreated on orientation"};checks.put("virtual-ui-orientation")
            onMain{activity.session!!.close()};check(activity.session!!.report().getInt("final_pointers")==0)
        }finally{onMain{activity.disable();activity.finish()};waitForIdleSync()}
        repeat(100){if(EditorSessionGuard.active)SystemClock.sleep(20)}
        check(!EditorSessionGuard.active);check(store.read()==before){"Smoke mutated library/favorites"}
        return JSONObject().put("status","PASS").put("checks",checks).put("source","AVD UI and recording sink; not physical gameplay")
            .put("platform_touch_submissions",0).put("root_required",false)
    }
}
