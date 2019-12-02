#include <jni.h>
#include <JavaListenerContainer.h>
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
        JavaListenerContainer *javaListenerContainer = new JavaListenerContainer();
        javaListenerContainer->onPreparedListener = new OnPreparedListener(jvm, env, thiz);
        javaListenerContainer->onPlayLoadingListener = new OnPlayLoadingListener(jvm, env, thiz);
        weFFmpeg = new WeFFmpeg(javaListenerContainer);
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
                      "Have you called setDataSource before calling the prepare function?");
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

    weFFmpeg->startDemuxThread();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativePause(JNIEnv *env, jobject thiz) {
    if (weFFmpeg == NULL) {
        jclass exceptionClass = env->FindClass("java/lang/Exception");
        env->ThrowNew(exceptionClass, "Have you called start before calling the pause function?");
        env->DeleteLocalRef(exceptionClass);
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativePause...");
    }

    weFFmpeg->pause();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeResumePlay(JNIEnv *env, jobject thiz) {
    if (weFFmpeg == NULL) {
        jclass exceptionClass = env->FindClass("java/lang/Exception");
        env->ThrowNew(exceptionClass, "Have you called start before calling the resumePlay function?");
        env->DeleteLocalRef(exceptionClass);
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeResumePlay...");
    }

    weFFmpeg->resumePlay();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeGetDuration(JNIEnv *env, jobject thiz) {
    if (weFFmpeg == NULL) {
        // 不涉及到控制状态，不抛异常
        LOGE(LOG_TAG, "nativeGetDuration...but weFFmpeg is NULL");
        return 0;
    }

    return weFFmpeg->getDuration();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeGetCurrentPosition(JNIEnv *env, jobject thiz) {
    if (weFFmpeg == NULL) {
        // 不涉及到控制状态，不抛异常
        LOGE(LOG_TAG, "nativeGetCurrentPosition...but weFFmpeg is NULL");
        return 0;
    }

    return weFFmpeg->getCurrentPosition();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeSetStopFlag(JNIEnv *env, jobject thiz) {
    if (weFFmpeg == NULL) {
        // 允许直接停止，不抛异常
        LOGE(LOG_TAG, "nativeSetStopFlag...but weFFmpeg is NULL");
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeSetStopFlag...");
    }

    weFFmpeg->setStopFlag();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeRelease(JNIEnv *env, jobject thiz) {
    if (weFFmpeg == NULL) {
        // 允许直接释放，不抛异常
        LOGE(LOG_TAG, "nativeRelease...but weFFmpeg is NULL");
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeRelease...");
    }

    delete weFFmpeg;
    weFFmpeg = NULL;
}