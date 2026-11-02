// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper;

import android.app.Application;
import android.content.Context;
import com.topjohnwu.superuser.Shell;

/** Configure libsu before any Activity (including direct diagnostic entry) can create its shell. */
public class MythicMapperApplication extends Application {
    private static boolean configured;
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        configureShell();
    }
    static synchronized void configureShell() {
        if (configured) return;
        Shell.enableVerboseLogging = BuildConfig.DEBUG;
        if (Shell.getCachedShell() == null) Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR).setTimeout(10));
        configured = true;
    }
}
