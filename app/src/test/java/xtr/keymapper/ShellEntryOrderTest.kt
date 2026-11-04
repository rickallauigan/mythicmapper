// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper

import com.topjohnwu.superuser.Shell
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],manifest=Config.NONE)
class ShellEntryOrderTest {
    @Test fun loadingHomeAfterShellInitializationDoesNotReconfigureShell(){
        // Simulate libsu's initialization barrier without creating any host shell/root process.
        val type=Class.forName("com.topjohnwu.superuser.internal.MainShell")
        val field=type.getDeclaredField("isInitMain").apply{isAccessible=true};val old=field.getBoolean(null)
        try {
            field.setBoolean(null,true)
            assertThrows(IllegalStateException::class.java){Shell.setDefaultBuilder(Shell.Builder.create())}
            Class.forName("xtr.keymapper.activity.MainActivity",true,javaClass.classLoader)
        }finally{field.setBoolean(null,old)}
    }
}
