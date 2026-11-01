// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.engine

/** Explicit diagnostics/reference API. Never loaded by the active Java mapping runtime. */
object RustMappingMath {
    val available: Boolean by lazy {
        try {
            System.loadLibrary("mythicmapper_core_jni")
            abiVersion() == 1
        } catch (_: LinkageError) { false }
    }
    @JvmStatic private external fun abiVersion(): Int
    @JvmStatic external fun vectorPacked(x: Float, y: Float, deadZone: Float,
                                        sx: Float, sy: Float, invertY: Boolean): Long
    fun x(packed: Long): Float = Float.fromBits((packed ushr 32).toInt())
    fun y(packed: Long): Float = Float.fromBits(packed.toInt())
}
