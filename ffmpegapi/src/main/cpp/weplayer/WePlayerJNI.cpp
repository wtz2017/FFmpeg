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
        javaListenerContainer->onPcmDataCall = new OnPCMDataCall(jvm, env, thiz);
        weFFmpeg = new WeFFmpeg(javaListenerContainer);
    }

    /**
     * java 内部是使用16bit的unicode编码（UTF-16）来表示字符串的，无论中文英文都是2字节；
     * jni 内部是使用UTF-8编码来表示字符串的，UTF-8是变长编码的unicode，一般ascii字符是1字节，中文是3字节；
     * c/c++ 使用的是原始数据，ascii就是一个字节了，中文一般是GB2312编码，用两个字节来表示一个汉字。
     *
     * GetStringLength 用来获取 java 原始字符串 UTF-16 字节个数
     * GetStringUTFLength 用来获取 UTF-8 字符串所需要的字节个数（不包括结束的 '\0'），
     * 对于用来存储字符串的 char 数组大小要比字符串长度大 1，并在 char 数组最后一位写结束符 '\0'
     *
     * GetStringUTFRegion 用来获取转成 UTF-8 后的字符串，第2、3两个参数指原始字串（UTF-16）的长度范围
     */
    int jstrUtf16Len = env->GetStringLength(dataSource);
    int jstrUtf8Len = env->GetStringUTFLength(dataSource);
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "GetString UTF-16 Length: %d; UTF-8 Length: %d", jstrUtf16Len, jstrUtf8Len);
    }

    char *source = new char[jstrUtf8Len + 1];// 回收放在 WeFFmpeg 中
    env->GetStringUTFRegion(dataSource, 0, jstrUtf16Len, source);
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
JNIEXPORT jdouble JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeGetSoundDecibels(JNIEnv *env, jobject thiz) {
    if (weFFmpeg == NULL) {
        LOGE(LOG_TAG, "nativeGetSoundDecibels...but weFFmpeg is NULL");
        return 0;
    }

    return weFFmpeg->getSoundDecibels();
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
JNIEXPORT jint JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeGetAudioSampleRate(JNIEnv *env, jobject thiz) {
    if (weFFmpeg == NULL) {
        LOGE(LOG_TAG, "nativeGetAudioSampleRate...but weFFmpeg is NULL");
        return 0;
    }

    return weFFmpeg->getAudioSampleRate();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeGetAudioChannelNums(JNIEnv *env, jobject thiz) {
    if (weFFmpeg == NULL) {
        LOGE(LOG_TAG, "nativeGetAudioChannelNums...but weFFmpeg is NULL");
        return 0;
    }

    return weFFmpeg->getAudioChannelNums();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeGetAudioBitsPerSample(JNIEnv *env, jobject thiz) {
    if (weFFmpeg == NULL) {
        LOGE(LOG_TAG, "natvieGetAudioBitsPerSample...but weFFmpeg is NULL");
        return 0;
    }

    return weFFmpeg->getAudioBitsPerSample();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeGetPcmMaxBytesPerCallback(JNIEnv *env, jobject thiz) {
    if (weFFmpeg == NULL) {
        LOGE(LOG_TAG, "nativeGetPcmMaxBytesPerCallback...but weFFmpeg is NULL");
        return 0;
    }

    return weFFmpeg->getPcmMaxBytesPerCallback();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeSetRecordPCMFlag(JNIEnv *env, jobject thiz, jboolean record) {
    if (weFFmpeg == NULL) {
        LOGE(LOG_TAG, "nativeSetRecordPCMFlag...but weFFmpeg is NULL");
        return;
    }

    weFFmpeg->setRecordPCMFlag(record);
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