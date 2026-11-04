#include <jni.h>
#include <linux/input.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <string.h>

#define WORD_BITS (8 * sizeof(unsigned long))
#define HAS(bits, code) ((bits[(code) / WORD_BITS] >> ((code) % WORD_BITS)) & 1UL)

/* Read-only capability probe: never EVIOCGRAB, never consumes native game input. */
JNIEXPORT jlongArray JNICALL
Java_xtr_keymapper_controller_ControllerDeviceMonitor_probe(JNIEnv *env, jclass cls, jstring path) {
    (void)cls;
    const char *name = (*env)->GetStringUTFChars(env, path, NULL);
    if (!name) return NULL;
    int fd = open(name, O_RDONLY | O_NONBLOCK | O_CLOEXEC);
    (*env)->ReleaseStringUTFChars(env, path, name);
    if (fd < 0) return NULL;
    unsigned long keys[(KEY_MAX / WORD_BITS) + 1] = {0};
    unsigned long axes[(ABS_MAX / WORD_BITS) + 1] = {0};
    unsigned long props[(INPUT_PROP_MAX / WORD_BITS) + 1] = {0};
    struct input_absinfo rx, ry;
    struct stat st;
    int valid = ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keys)), keys) >= 0
        && ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(axes)), axes) >= 0
        && HAS(keys, BTN_GAMEPAD) && !HAS(keys, BTN_TOUCH)
        && HAS(axes, ABS_X) && HAS(axes, ABS_Y) && HAS(axes, ABS_RX) && HAS(axes, ABS_RY)
        && ioctl(fd, EVIOCGABS(ABS_RX), &rx) >= 0
        && ioctl(fd, EVIOCGABS(ABS_RY), &ry) >= 0
        && rx.minimum < rx.maximum && ry.minimum < ry.maximum && fstat(fd, &st) == 0;
    if (ioctl(fd, EVIOCGPROP(sizeof(props)), props) >= 0 && HAS(props, INPUT_PROP_ACCELEROMETER)) valid = 0;
    close(fd);
    if (!valid) return NULL;
    jlong data[] = {(jlong)st.st_rdev, (jlong)st.st_ino,
        rx.minimum, rx.maximum, rx.value, ry.minimum, ry.maximum, ry.value};
    jlongArray result = (*env)->NewLongArray(env, 8);
    if (result) (*env)->SetLongArrayRegion(env, result, 0, 8, data);
    return result;
}

/* Hotplug liveness requires only stat(), not repeatedly opening sensor/touchscreen drivers. */
JNIEXPORT jlongArray JNICALL
Java_xtr_keymapper_controller_ControllerDeviceMonitor_identity(JNIEnv *env, jclass cls, jstring path) {
    (void)cls;
    const char *name = (*env)->GetStringUTFChars(env, path, NULL);
    if (!name) return NULL;
    struct stat st;
    int valid = stat(name, &st) == 0;
    (*env)->ReleaseStringUTFChars(env, path, name);
    if (!valid) return NULL;
    jlong data[] = {(jlong)st.st_rdev, (jlong)st.st_ino};
    jlongArray result = (*env)->NewLongArray(env, 2);
    if (result) (*env)->SetLongArrayRegion(env, result, 0, 2, data);
    return result;
}

/* Restrict MythicMapper to the historically observed original G8 USB mode.
 * VID/PID alone is Sony's identity and is insufficient evidence. No device grab. */
JNIEXPORT jboolean JNICALL
Java_xtr_keymapper_controller_ControllerDeviceMonitor_isOriginalG8(JNIEnv *env, jclass cls, jstring path) {
    (void)cls;
    const char *name = (*env)->GetStringUTFChars(env, path, NULL);
    if (!name) return JNI_FALSE;
    int fd = open(name, O_RDONLY | O_NONBLOCK | O_CLOEXEC);
    (*env)->ReleaseStringUTFChars(env, path, name);
    if (fd < 0) return JNI_FALSE;
    struct input_id id = {0};
    char device_name[256] = {0};
    int valid = ioctl(fd, EVIOCGID, &id) >= 0
        && ioctl(fd, EVIOCGNAME(sizeof(device_name) - 1), device_name) >= 0
        && id.bustype == BUS_USB && id.vendor == 0x054c && id.product == 0x0ce6
        && strcmp(device_name, "Sony Interactive Entertainment GameSir-G8") == 0;
    close(fd);
    return valid ? JNI_TRUE : JNI_FALSE;
}
