#define _GNU_SOURCE
#define ioctl test_ioctl
#include "../../main/cpp/controller_device.c"
#include <assert.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>

static int kind;
static int g8_mode;
static jlong captured[8];
static int array_length;
static const char *chars(JNIEnv *env,jstring text,jboolean *copy) { (void)env;(void)copy;return (const char*)text; }
static void release(JNIEnv *env,jstring text,const char *data) { (void)env;(void)text;(void)data; }
static jlongArray array(JNIEnv *env,jsize size) { (void)env;assert(size==8||size==2);array_length=size;return (jlongArray)captured; }
static void region(JNIEnv *env,jlongArray arr,jsize start,jsize count,const jlong *data) {
    (void)env;(void)arr;assert(start==0&&count==array_length);memcpy(captured,data,count*sizeof(jlong));
}
int test_ioctl(int fd,unsigned long request,...) {
    (void)fd;va_list args;va_start(args,request);void *out=va_arg(args,void*);va_end(args);
    if(request==EVIOCGID) {
        struct input_id *id=out; id->bustype=g8_mode==2?BUS_BLUETOOTH:BUS_USB;
        id->vendor=0x054c;id->product=g8_mode==3?0:0x0ce6;return 0;
    }
    if(request==EVIOCGNAME(255)) {
        if(g8_mode==4) return -1;
        strcpy(out,g8_mode==1?"Sony Controller":"Sony Interactive Entertainment GameSir-G8");return 0;
    }
    unsigned long *bits=out;
    if(request==EVIOCGBIT(EV_KEY,((KEY_MAX/WORD_BITS)+1)*sizeof(unsigned long))) {
        if(kind!=1) bits[BTN_GAMEPAD/WORD_BITS]|=1UL<<(BTN_GAMEPAD%WORD_BITS);
        if(kind==2) bits[BTN_TOUCH/WORD_BITS]|=1UL<<(BTN_TOUCH%WORD_BITS);
    } else if(request==EVIOCGBIT(EV_ABS,((ABS_MAX/WORD_BITS)+1)*sizeof(unsigned long))) {
        for(int c=0;c<=ABS_MAX;c++) if(c==ABS_X||c==ABS_Y||c==ABS_RX||c==ABS_RY)
            bits[c/WORD_BITS]|=1UL<<(c%WORD_BITS);
    } else if(request==EVIOCGPROP(((INPUT_PROP_MAX/WORD_BITS)+1)*sizeof(unsigned long))) {
        if(kind==3) bits[INPUT_PROP_ACCELEROMETER/WORD_BITS]|=1UL<<(INPUT_PROP_ACCELEROMETER%WORD_BITS);
    } else if(request==EVIOCGABS(ABS_RX)||request==EVIOCGABS(ABS_RY)) {
        struct input_absinfo *info=out;memset(info,0,sizeof(*info));info->maximum=kind==4?0:255;info->value=128;
    } else return -1;
    return 0;
}
int main(void) {
    const struct JNINativeInterface_ table={.GetStringUTFChars=chars,.ReleaseStringUTFChars=release,
        .NewLongArray=array,.SetLongArrayRegion=region};JNIEnv env=&table;
    char path[]="/tmp/xtmapper-controller-probe-XXXXXX";int fd=mkstemp(path);assert(fd>=0);close(fd);
    for(kind=1;kind<=4;kind++) assert(!Java_xtr_keymapper_controller_ControllerDeviceMonitor_probe(&env,NULL,(jstring)path));
    for(g8_mode=1;g8_mode<=4;g8_mode++)
        assert(!Java_xtr_keymapper_controller_ControllerDeviceMonitor_isOriginalG8(&env,NULL,(jstring)path));
    g8_mode=0;assert(Java_xtr_keymapper_controller_ControllerDeviceMonitor_isOriginalG8(&env,NULL,(jstring)path));
    kind=0;assert(Java_xtr_keymapper_controller_ControllerDeviceMonitor_probe(&env,NULL,(jstring)path));
    assert(captured[2]==0&&captured[3]==255&&captured[4]==128&&captured[7]==128);
    assert(Java_xtr_keymapper_controller_ControllerDeviceMonitor_identity(&env,NULL,(jstring)path));
    assert(array_length==2);jlong old_inode=captured[1];
    /* Keep both files alive at once so POSIX cannot legally reuse the inode.
       GitHub-hosted filesystems may immediately recycle an inode after unlink(). */
    char replacement[]="/tmp/xtmapper-controller-probe-replacement-XXXXXX";
    fd=mkstemp(replacement);assert(fd>=0);close(fd);
    assert(Java_xtr_keymapper_controller_ControllerDeviceMonitor_identity(&env,NULL,(jstring)replacement));
    jlong replacement_inode=captured[1];assert(replacement_inode!=old_inode);
    assert(rename(replacement,path)==0);
    assert(Java_xtr_keymapper_controller_ControllerDeviceMonitor_identity(&env,NULL,(jstring)path));
    assert(captured[1]==replacement_inode&&captured[1]!=old_inode);unlink(path);
    assert(!Java_xtr_keymapper_controller_ControllerDeviceMonitor_identity(&env,NULL,(jstring)path));
    assert(!Java_xtr_keymapper_controller_ControllerDeviceMonitor_probe(&env,NULL,(jstring)path));
    puts("controller capability probe tests passed");
}
