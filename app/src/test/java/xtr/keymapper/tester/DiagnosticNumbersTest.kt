// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.tester

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class DiagnosticNumbersTest {
    @Test fun displayFormattingMatchesPreviousFormatterForControllerValues(){
        for(digits in listOf(2,3))for(i in -10000..10000){val v=i/32767f
            assertEquals(String.format(Locale.ROOT,"%.${digits}f",v),DiagnosticNumbers.fixed(v,digits))
        }
        for(v in listOf(-32768f,0f,-0f,127.5f,255f,32767f))assertEquals(String.format(Locale.ROOT,"%.2f",v),DiagnosticNumbers.fixed(v))
        assertEquals("—",DiagnosticNumbers.fixed(null));assertEquals("—",DiagnosticNumbers.fixed(Float.NaN))
    }
}
