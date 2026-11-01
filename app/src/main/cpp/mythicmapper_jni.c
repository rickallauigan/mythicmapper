/* SPDX-License-Identifier: GPL-3.0-or-later */
#include <jni.h>
#include <stdint.h>

extern uint64_t mythicmapper_vector_v1(float, float, float, float, float, int32_t);
extern int32_t mythicmapper_abi_version(void);

JNIEXPORT jint JNICALL Java_xtr_keymapper_engine_RustMappingMath_abiVersion(JNIEnv *env, jclass type) {
    (void)env; (void)type;
    return mythicmapper_abi_version();
}
JNIEXPORT jlong JNICALL Java_xtr_keymapper_engine_RustMappingMath_vectorPacked(
        JNIEnv *env, jclass type, jfloat x, jfloat y, jfloat dz, jfloat sx, jfloat sy, jboolean invert) {
    (void)env; (void)type;
    return (jlong)mythicmapper_vector_v1(x, y, dz, sx, sy, invert == JNI_TRUE);
}
