// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.tester

/** Display-only decimal formatting. Input normalization and retained evidence keep full precision. */
object DiagnosticNumbers {
    fun fixed(value:Float?,digits:Int=2):String {
        if(value==null||!value.isFinite())return "—"
        val scale=if(digits==3)1000L else 100L
        val magnitude=kotlin.math.abs(value.toDouble())*scale
        if(magnitude>Long.MAX_VALUE-1)return value.toString()
        val rounded=kotlin.math.floor(magnitude+.5).toLong()
        val sign=if(value.toRawBits()<0)"-"else""
        return "$sign${rounded/scale}.${(rounded%scale).toString().padStart(if(digits==3)3 else 2,'0')}"
    }
}
