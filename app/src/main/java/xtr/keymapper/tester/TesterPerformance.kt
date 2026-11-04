// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.tester

import android.os.*
import android.view.*
import org.json.*
import java.util.concurrent.atomic.AtomicLongArray

/** Same instrumentation in both physical trials. Frame metrics are app-window metrics, not glass latency. */
object TesterWork {
    @Volatile var active=false
    @Volatile var baselineFormatting=false
    private val counts=AtomicLongArray(8)
    private val nanos=AtomicLongArray(8)
    private val queueSamples=LongArray(20000);private var queueCount=0
    @Synchronized fun queue(ns:Long){if(active){queueSamples[queueCount%queueSamples.size]=ns;queueCount++}}
    fun reset(){queueCount=0;for(i in 0..7){counts.set(i,0);nanos.set(i,0)}}
    fun hit(i:Int){if(active)counts.incrementAndGet(i)}
    fun <T> section(i:Int,label:String,action:()->T):T {
        if(!active)return action()
        val start=System.nanoTime();android.os.Trace.beginSection(label)
        return try{action()}finally{nanos.addAndGet(i,System.nanoTime()-start);counts.incrementAndGet(i);android.os.Trace.endSection()}
    }
    @Synchronized fun queueReport()=TesterPerformance.quantiles(queueSamples.copyOf(minOf(queueCount,queueSamples.size)).sorted()).put("total_samples",queueCount).put("window_truncated",queueCount>queueSamples.size)
    fun report()=JSONObject().apply{for((i,name)in listOf("screen_compositions","controller_compositions","stick_compositions","trigger_compositions","controller_draw_recording","reducer","snapshot","log_append").withIndex())put(name,JSONObject().put("count",counts.get(i)).put("total_ns",nanos.get(i)))}
}

class TesterPerformance(private val window:Window) {
    private val thread=HandlerThread("tester-frame-metrics").apply{start()}
    private val handler=Handler(thread.looper)
    private val samples=Array(6){LongArray(6000)}
    private var count=0;private var jank=0;private var callbackDrops=0L
    @Volatile var active=false
    private var cpu=0L;private var start=0L;private var pss=0L
    private var runtime=emptyMap<String,String>()
    private val listener=Window.OnFrameMetricsAvailableListener{_,metrics,dropped->
        if(active)synchronized(this){
            val slot=count%6000
            val fields=intArrayOf(FrameMetrics.TOTAL_DURATION,FrameMetrics.LAYOUT_MEASURE_DURATION,FrameMetrics.DRAW_DURATION,FrameMetrics.SYNC_DURATION,FrameMetrics.COMMAND_ISSUE_DURATION,FrameMetrics.INPUT_HANDLING_DURATION)
            for(i in fields.indices)samples[i][slot]=metrics.getMetric(fields[i])
            val deadline=if(Build.VERSION.SDK_INT>=31)metrics.getMetric(FrameMetrics.DEADLINE)else 16_666_667L
            if(samples[0][slot]>deadline&&deadline>0)jank++
            count++;callbackDrops+=dropped
        }
    }
    init{window.addOnFrameMetricsAvailableListener(listener,handler)}
    fun start(){synchronized(this){count=0;jank=0;callbackDrops=0L};pss=Debug.getPss();runtime=Debug.getRuntimeStats();cpu=android.os.Process.getElapsedCpuTime();start=SystemClock.elapsedRealtime();TesterWork.reset();TesterWork.active=true;active=true}
    fun finish():JSONObject {
        active=false;TesterWork.active=false
        val elapsed=SystemClock.elapsedRealtime()-start;val deltaCpu=android.os.Process.getElapsedCpuTime()-cpu
        val after=Debug.getRuntimeStats()
        return JSONObject().put("elapsed_ms",elapsed).put("app_cpu_ms",deltaCpu).put("app_cpu_one_core_percent",if(elapsed>0)100.0*deltaCpu/elapsed else 0.0)
            .put("pss_before_kb",pss).put("pss_after_kb",Debug.getPss()).put("frames",count).put("janky_frames",jank)
            .put("janky_percent",if(count>0)100.0*jank/count else 0.0).put("frame_metric_callback_drops",callbackDrops)
            .put("work",TesterWork.report()).apply{
                for(key in listOf("art.gc.bytes-allocated","art.gc.gc-count","art.gc.gc-time","art.gc.blocking-gc-count"))put(key,after[key]?.toLongOrNull()?.let{v->runtime[key]?.toLongOrNull()?.let{v-it}}?:JSONObject.NULL)
                synchronized(this@TesterPerformance){for((i,name)in listOf("frame_total","layout_measure","draw","sync","command_issue","input_handling").withIndex()){
                    val data=samples[i].copyOf(minOf(count,6000)).sorted();put(name,quantiles(data))
                }}
            }
    }
    fun close(){active=false;TesterWork.active=false;window.removeOnFrameMetricsAvailableListener(listener);thread.quitSafely()}
    companion object{fun quantiles(data:List<Long>)=JSONObject().put("samples",data.size).apply{for((p,q)in listOf("p50_ns" to .5,"p95_ns" to .95,"p99_ns" to .99))put(p,if(data.isEmpty())0 else data[((data.size-1)*q).toInt()])}}
}
