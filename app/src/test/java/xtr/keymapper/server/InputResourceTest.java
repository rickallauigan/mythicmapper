// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.server;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE)
public class InputResourceTest {
    private long workers() {
        return Thread.getAllStackTraces().keySet().stream().filter(t->t.isAlive()&&t.getName().equals("scroll")).count();
    }
    @Test public void controllerProfileChurnDoesNotStartUnusedScrollWorkers() {
        long before=workers();
        for(int i=0;i<20;i++)new Input(0);
        assertEquals("Controller-only Input construction leaked workers",before,workers());
    }
}
