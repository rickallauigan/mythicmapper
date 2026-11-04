// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.engine;
import xtr.keymapper.controller.ControllerAimHandler;
import java.util.Arrays;

/** Linux host parity/JNI harness. Timings do not represent Android or gameplay latency. */
class RustMappingMath {
    static native int abiVersion();
    static native long vectorPacked(float x,float y,float dz,float sx,float sy,boolean invert);
    static volatile double consumed;
    public static void main(String[] args) {
        System.loadLibrary("mythicmapper_core_jni");
        if (abiVersion()!=1) throw new AssertionError("ABI mismatch");
        int count=0;
        for(int xi=-100;xi<=100;xi++) for(int yi=-100;yi<=100;yi++)
            for(float dz: new float[]{0,.15f,.9f}) for(boolean invert:new boolean[]{false,true}) {
                float x=xi/100f,y=yi/100f;
                float[] expected=ControllerAimHandler.vector(x,y,dz,1.3f,.7f,invert);
                long packed=vectorPacked(x,y,dz,1.3f,.7f,invert);
                float rx=Float.intBitsToFloat((int)(packed>>>32)), ry=Float.intBitsToFloat((int)packed);
                if(Math.abs(expected[0]-rx)>0.000001f || Math.abs(expected[1]-ry)>0.000001f)
                    throw new AssertionError("parity at "+x+","+y+" dz="+dz);
                count++;
            }
        if(vectorPacked(Float.NaN,0,.15f,1,1,false)!=0) throw new AssertionError("invalid input");
        System.out.println("JNI parity PASS: "+count+" vectors + ABI/invalid input");
        // Warm both paths, use a volatile sink so calculations cannot simply be eliminated.
        for(int i=0;i<100000;i++) { consumed=vectorPacked((i%201-100)/100f,.3f,.15f,1,1,false); consumed=ControllerAimHandler.vector((i%201-100)/100f,.3f,.15f,1,1,false)[0]; }
        for(boolean rust:new boolean[]{false,true}) {
            long[] timings=new long[20000];
            for(int i=0;i<timings.length;i++) {
                float x=(i%201-100)/100f;
                long start=System.nanoTime();
                if(rust) consumed=vectorPacked(x,.3f,.15f,1,1,false);
                else consumed=ControllerAimHandler.vector(x,.3f,.15f,1,1,false)[0];
                timings[i]=System.nanoTime()-start;
            }
            Arrays.sort(timings);
            System.out.printf("HOST %s math ns (timer+sink included) n=%d p50=%d p95=%d p99=%d%n",rust?"Rust JNI":"original Java",timings.length,timings[10000],timings[19000],timings[19800]);
        }
    }
}
