// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.server;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONObject;

/** Bounded software-only measurements. Sorting/formatting occurs only on diagnostics requests. */
public final class RuntimeMetrics {
    private RuntimeMetrics() {}
    private static final int CAPACITY = 2048;
    private static final long[][] samples = new long[3][CAPACITY];
    private static final long[] counts = new long[3];
    private static final Set<String> buttons = new HashSet<>();
    private static long drops, errors, lastReceipt, lastInterval, jitter;
    private static int active, controllers, rx, ry;
    private static String lastEvent = "No controller event", lastError = "None";
    public static synchronized void record(int stage, long ns) {
        if (stage < 0 || stage >= 3 || ns < 0) return;
        samples[stage][(int)(counts[stage]++ % CAPACITY)] = ns;
    }
    public static synchronized void controller(String line) {
        lastEvent = line.length() > 160 ? line.substring(0,160) : line;
        long now=System.nanoTime();
        if(lastReceipt!=0) {
            long interval=now-lastReceipt;
            if(lastInterval!=0) jitter=Math.abs(interval-lastInterval);
            lastInterval=interval;
        }
        lastReceipt=now;
    }
    public static synchronized void axes(int x,int y) {rx=x;ry=y;}
    public static synchronized void button(String code,int action) {if(action==1) buttons.add(code);else buttons.remove(code);}
    public static synchronized void controllers(int count) {controllers=count;if(count==0){buttons.clear();rx=ry=0;lastReceipt=lastInterval=jitter=0;}}
    public static synchronized void touches(int count) {active=count;}
    public static synchronized void dropped() {drops++;}
    public static synchronized void error(String detail) {errors++;lastError=detail.length()>200?detail.substring(0,200):detail;}
    public static String snapshot() {
        long[][] copied = new long[3][];
        JSONObject result = new JSONObject();
        try {
            synchronized(RuntimeMetrics.class) {
                for(int i=0;i<3;i++)copied[i]=Arrays.copyOf(samples[i],(int)Math.min(counts[i],CAPACITY));
                result.put("backend_uid",android.os.Process.myUid()).put("samples_total",Arrays.toString(counts)).put("logical_active_touches",active)
                    .put("controller_nodes",controllers).put("buttons",buttons.toString())
                    .put("right_stick_raw",rx+","+ry).put("syn_dropped",drops).put("injection_errors",errors)
                    .put("last_error",lastError).put("last_event",lastEvent).put("last_receipt_jitter_ns",jitter);
            }
            String[] names={"queue","processing_inclusive","submission"};
            for(int i=0;i<3;i++) {
                Arrays.sort(copied[i]);
                result.put(names[i],new JSONObject().put("window_samples",copied[i].length)
                    .put("p50_ns",percentile(copied[i],.50)).put("p95_ns",percentile(copied[i],.95)).put("p99_ns",percentile(copied[i],.99)));
            }
            return result.toString(2);
        } catch(org.json.JSONException failure) {return "Diagnostics serialization failed";}
    }
    static long percentile(long[] sorted,double p) {return sorted.length==0?0:sorted[(int)Math.ceil(sorted.length*p)-1];}
}
