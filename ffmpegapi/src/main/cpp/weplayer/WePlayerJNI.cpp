#include <jni.h>
#include "JavaListenerContainer.h"
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
    if (dataSource == NULL || env->GetStringUTFLength(dataSource) == 0) {
        jclass exceptionClass = env->FindClass("java/lang/Exception");
        env->ThrowNew(exceptionClass,
                      "Can't set a 'null' string to data source!");
        env->DeleteLocalRef(exceptionClass);
        return;
    }

    if (weFFmpeg == NULL) {
        JavaListenerContainer *javaListenerContainer = new JavaListenerContainer();
        javaListenerContainer->onPreparedListener = new OnPreparedListener(jvm, env, thiz);
        javaListenerContainer->onPlayLoadingListener = new OnPlayLoadingListener(jvm, env, thiz);
        javaListenerContainer->onErrorListener = new OnErrorListener(jvm, env, thiz);
        javaListenerContainer->onCompletionListener = new OnCompletionListener(jvm, env, thiz);
        weFFmpeg = new WeFFmpeg(javaListenerContainer);
    }

    /**
     * GetStringUTFRegion 需要使用对应的 GetStringUTFLength 来获取 UTF-8 字符串所需要的
     * 字节个数（不包括结束的 '\0'），对于 char 数组大小要比它大 1，并在 char 数组最后一位写结束符 '\0'
     */
    int jstrUtf8Len = env->GetStringUTFLength(dataSource);
    char *source = new char[jstrUtf8Len + 1];
    env->GetStringUTFRegion(dataSource, 0, jstrUtf8Len, source);
    source[jstrUtf8Len] = '\0';

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
Java_com_wtz_ffmpegapi_WePlayer_nativeSetVolume(JNIEnv *env, jobject thiz, jfloat percent) {
    if (weFFmpeg == NULL) {
        LOGE(LOG_TAG, "nativeSetVolume...but weFFmpeg is NULL");
        return;
    }

    weFFmpeg->setVolume(percent);
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeGetVolume(JNIEnv *env, jobject thiz) {
    if (weFFmpeg == NULL) {
        LOGE(LOG_TAG, "nativeGetVolume...but weFFmpeg is NULL");
        return 0;
    }

    return weFFmpeg->getVolume();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeSetSoundChannel(JNIEnv *env, jobject thiz, jint channel) {
    if (weFFmpeg == NULL) {
        LOGE(LOG_TAG, "nativeSetSoundChannel...but weFFmpeg is NULL");
        return;
    }

    weFFmpeg->setSoundChannel(channel);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeSetPitch(JNIEnv *env, jobject thiz, jfloat pitch) {
    if (weFFmpeg == NULL) {
        LOGE(LOG_TAG, "nativeSetPitch...but weFFmpeg is NULL");
        return;
    }

    weFFmpeg->setPitch(pitch);
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeGetPitch(JNIEnv *env, jobject thiz) {
    if (weFFmpeg == NULL) {
        LOGE(LOG_TAG, "nativeGetPitch...but weFFmpeg is NULL");
        return 1.0;
    }

    return weFFmpeg->getPitch();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeSetTempo(JNIEnv *env, jobject thiz, jfloat tempo) {
    if (weFFmpeg == NULL) {
        LOGE(LOG_TAG, "nativeSetTempo...but weFFmpeg is NULL");
        return;
    }

    weFFmpeg->setTempo(tempo);
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeGetTempo(JNIEnv *env, jobject thiz) {
    if (weFFmpeg == NULL) {
        LOGE(LOG_TAG, "nativeGetTempo...but weFFmpeg is NULL");
        return 1.0;
    }

    return weFFmpeg->getTempo();
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
Java_com_wtz_ffmpegapi_WePlayer_nativeSeekTo(JNIEnv *env, jobject thiz, jint msec) {
    if (weFFmpeg == NULL) {
        jclass exceptionClass = env->FindClass("java/lang/Exception");
        env->ThrowNew(exceptionClass,
                      "Have you called prepare before calling the seekTo function?");
        env->DeleteLocalRef(exceptionClass);
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeSeekTo %d ms...", msec);
    }

    weFFmpeg->seekTo(msec);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeIsPlaying(JNIEnv *env, jobject thiz) {
    if (weFFmpeg == NULL) {
        // 不涉及到控制状态，不抛异常
        LOGE(LOG_TAG, "nativeIsPlaying...but weFFmpeg is NULL");
        return false;
    }

    return weFFmpeg->isPlaying();
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
Java_com_wtz_ffmpegapi_WePlayer_nativeStop(JNIEnv *env, jobject thiz) {
    if (weFFmpeg == NULL) {
        LOGE(LOG_TAG, "nativeSetStopFlag...but weFFmpeg is NULL");
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeStop...");
    }

    weFFmpeg->stop();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeReset(JNIEnv *env, jobject thiz) {
    if (weFFmpeg == NULL) {
        LOGE(LOG_TAG, "nativeReset...but weFFmpeg is NULL");
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeReset...");
    }

    weFFmpeg->reset();
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