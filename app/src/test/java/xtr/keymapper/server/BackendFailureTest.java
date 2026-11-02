// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.server;

import android.view.InputEvent;
import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import xtr.keymapper.keymap.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE)
public class BackendFailureTest {
    public static class Backend {
        boolean reject=true; int calls;
        public boolean inject(InputEvent event,int mode){calls++;return !reject;}
    }
    private InputService service() throws Exception {
        var config=new KeymapConfig(null);config.pointerMode=KeymapConfig.POINTER_OVERLAY;
        var callback=(xtr.keymapper.IRemoteServiceCallback)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{xtr.keymapper.IRemoteServiceCallback.class},(p,m,a)->null);
        return new InputService(ProfileConfiguration.parse("APPLICATION com.mobile.legends\nBTN_TL2 100 100 0"),config,callback,2280,1080,null,true,0);
    }
    @Test public void rejectionLatchesStopsInputAndNewRuntimeCanRecover() throws Exception {
        var backend=new Backend();Input.inputManager=backend;
        Input.injectInputEventMethod=Backend.class.getMethod("inject",InputEvent.class,int.class);Input.setDisplayIdMethod=null;
        var old=service();old.injectEvent(100,100,1,0);
        assertTrue(old.stopEvents);
        Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(old.isPausedAndReleased());
        int before=backend.calls;old.injectEvent(120,100,1,1);assertEquals(before,backend.calls);
        old.pauseResumeKeymap();assertTrue(old.stopEvents);
        backend.reject=false;
        var next=service();next.injectEvent(100,100,1,0);assertFalse(next.stopEvents);assertTrue(backend.calls>before);
        next.stop();old.stop();
    }
    @Test public void nearestRankPercentilesAndEmptyWindow() {
        assertEquals(0,RuntimeMetrics.percentile(new long[0],.99));
        assertEquals(3,RuntimeMetrics.percentile(new long[]{1,2,3,4,5},.5));
        assertEquals(5,RuntimeMetrics.percentile(new long[]{1,2,3,4,5},.99));
    }
}
