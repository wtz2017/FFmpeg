#include <jni.h>
#include "WeFFmpeg.h"
#include "OnPreparedListener.h"
#include "AndroidLog.h"


#define LOG_TAG "WePlayerJNI"

JavaVM *jvm;
WeFFmpeg *weFFmpeg;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    jvm = vm;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE(LOG_TAG, "JNI_OnLoad vm->GetEnv exception!");
        return -1;
    }
    return JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeSetDataSource(JNIEnv *env, jobject thiz, jstring dataSource) {
    if (weFFmpeg == NULL) {
        JavaListener *javaListener = new OnPreparedListener(jvm, env, thiz);
        weFFmpeg = new WeFFmpeg(javaListener);
    }

    int strLen = env->GetStringLength(dataSource);
    char *source = new char[strLen];
    env->GetStringUTFRegion(dataSource, 0, strLen, source);
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeSetDataSource: %s", source);
    }

    weFFmpeg->setDataSource(source);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativePrepareAsync(JNIEnv *env, jobject thiz) {
    if (weFFmpeg == NULL) {
        jclass exceptionClass = env->FindClass("java/lang/Exception");
        env->ThrowNew(exceptionClass,
                      "Have you called setDataSource before calling the prepareAsync function?");
        env->DeleteLocalRef(exceptionClass);
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativePrepareAsync...");
    }

    weFFmpeg->prepareAsync();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeStart(JNIEnv *env, jobject thiz) {
    if (weFFmpeg == NULL) {
        jclass exceptionClass = env->FindClass("java/lang/Exception");
        env->ThrowNew(exceptionClass, "Have you called prepare before calling the start function?");
        env->DeleteLocalRef(exceptionClass);
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeStart...");
    }

    weFFmpeg->start();
}
