// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.mythic;

import android.content.Intent;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import xtr.keymapper.activity.MainActivity;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, manifest=Config.NONE)
public class HeroSetupEntryTest {
    public static class Home extends MainActivity {
        final List<String> events=new ArrayList<>();
        Intent opened;
        @Override public void stopPointer(){events.add("stop bound and started mapper");}
        @Override public void startActivity(Intent intent){events.add("open setup");opened=intent;}
    }
    @Test public void actualHomeEntryStopsMappingBeforeOpeningCalibration(){
        var home=Robolectric.buildActivity(Home.class).get();
        home.openHeroTrainingFromCompose("new-training-copy");
        assertEquals(List.of("stop bound and started mapper","open setup"),home.events);
        assertEquals(HeroTrainingActivity.class.getName(),home.opened.getComponent().getClassName());
        assertEquals("new-training-copy",home.opened.getStringExtra("profile"));
    }
}
