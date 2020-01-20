#include <jni.h>
#include "JavaListenerContainer.h"
#include "AndroidLog.h"
#include "WePlayer.h"
#include "WeEditor.h"


#define LOG_TAG "WeJNI"

JavaVM *jvm;
WePlayer *pWePlayer;
WeEditor *pWeEditor;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    jvm = vm;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE(LOG_TAG, "JNI_OnLoad vm->GetEnv exception!");
        return -1;
    }
    return JNI_VERSION_1_6;
}

// ------------------------------ WePlayer Start ------------------------------
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

    if (pWePlayer == NULL) {
        JavaListenerContainer *javaListenerContainer = new JavaListenerContainer();
        javaListenerContainer->onPreparedListener = new OnPreparedListener(jvm, env, thiz);
        javaListenerContainer->onPlayLoadingListener = new OnNativeLoading(jvm, env, thiz);
        javaListenerContainer->onErrorListener = new OnErrorListener(jvm, env, thiz);
        javaListenerContainer->onCompletionListener = new OnCompletionListener(jvm, env, thiz);
        javaListenerContainer->onPcmDataCall = new OnPCMDataCall(jvm, env, thiz);
        javaListenerContainer->onYuvDataCall = new OnYUVDataCall(jvm, env, thiz);
        pWePlayer = new WePlayer(javaListenerContainer);
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

    char *source = new char[jstrUtf8Len + 1];// 回收放在 WePlayer 中
    env->GetStringUTFRegion(dataSource, 0, jstrUtf16Len, source);
    source[jstrUtf8Len] = '\0';

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeSetDataSource: %s", source);
    }
    pWePlayer->setDataSource(source);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativePrepareAsync(JNIEnv *env, jobject thiz) {
    if (pWePlayer == NULL) {
        jclass exceptionClass = env->FindClass("java/lang/Exception");
        env->ThrowNew(exceptionClass,
                      "Have you called setDataSource before calling the prepare function?");
        env->DeleteLocalRef(exceptionClass);
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativePrepareAsync...");
    }

    pWePlayer->prepareAsync();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeSetVolume(JNIEnv *env, jobject thiz, jfloat percent) {
    if (pWePlayer == NULL) {
        LOGE(LOG_TAG, "nativeSetVolume...but pWePlayer is NULL");
        return;
    }

    pWePlayer->setVolume(percent);
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeGetVolume(JNIEnv *env, jobject thiz) {
    if (pWePlayer == NULL) {
        LOGE(LOG_TAG, "nativeGetVolume...but pWePlayer is NULL");
        return 0;
    }

    return pWePlayer->getVolume();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeSetSoundChannel(JNIEnv *env, jobject thiz, jint channel) {
    if (pWePlayer == NULL) {
        LOGE(LOG_TAG, "nativeSetSoundChannel...but pWePlayer is NULL");
        return;
    }

    pWePlayer->setSoundChannel(channel);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeSetPitch(JNIEnv *env, jobject thiz, jfloat pitch) {
    if (pWePlayer == NULL) {
        LOGE(LOG_TAG, "nativeSetPitch...but pWePlayer is NULL");
        return;
    }

    pWePlayer->setPitch(pitch);
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeGetPitch(JNIEnv *env, jobject thiz) {
    if (pWePlayer == NULL) {
        LOGE(LOG_TAG, "nativeGetPitch...but pWePlayer is NULL");
        return 1.0;
    }

    return pWePlayer->getPitch();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeSetTempo(JNIEnv *env, jobject thiz, jfloat tempo) {
    if (pWePlayer == NULL) {
        LOGE(LOG_TAG, "nativeSetTempo...but pWePlayer is NULL");
        return;
    }

    pWePlayer->setTempo(tempo);
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeGetTempo(JNIEnv *env, jobject thiz) {
    if (pWePlayer == NULL) {
        LOGE(LOG_TAG, "nativeGetTempo...but pWePlayer is NULL");
        return 1.0;
    }

    return pWePlayer->getTempo();
}

extern "C"
JNIEXPORT jdouble JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeGetSoundDecibels(JNIEnv *env, jobject thiz) {
    if (pWePlayer == NULL) {
        LOGE(LOG_TAG, "nativeGetSoundDecibels...but pWePlayer is NULL");
        return 0;
    }

    return pWePlayer->getSoundDecibels();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeStart(JNIEnv *env, jobject thiz) {
    if (pWePlayer == NULL) {
        jclass exceptionClass = env->FindClass("java/lang/Exception");
        env->ThrowNew(exceptionClass, "Have you called prepare before calling the start function?");
        env->DeleteLocalRef(exceptionClass);
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeStart...");
    }

    pWePlayer->start();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativePause(JNIEnv *env, jobject thiz) {
    if (pWePlayer == NULL) {
        jclass exceptionClass = env->FindClass("java/lang/Exception");
        env->ThrowNew(exceptionClass, "Have you called start before calling the pause function?");
        env->DeleteLocalRef(exceptionClass);
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativePause...");
    }

    pWePlayer->pause();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeSeekTo(JNIEnv *env, jobject thiz, jint msec) {
    if (pWePlayer == NULL) {
        jclass exceptionClass = env->FindClass("java/lang/Exception");
        env->ThrowNew(exceptionClass,
                      "Have you called prepare before calling the seekTo function?");
        env->DeleteLocalRef(exceptionClass);
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeSeekTo %d ms...", msec);
    }

    pWePlayer->seekTo(msec);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeIsPlaying(JNIEnv *env, jobject thiz) {
    if (pWePlayer == NULL) {
        // 不涉及到控制状态，不抛异常
        LOGE(LOG_TAG, "nativeIsPlaying...but pWePlayer is NULL");
        return false;
    }

    return pWePlayer->isPlaying();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeGetDuration(JNIEnv *env, jobject thiz) {
    if (pWePlayer == NULL) {
        // 不涉及到控制状态，不抛异常
        LOGE(LOG_TAG, "nativeGetDuration...but pWePlayer is NULL");
        return 0;
    }

    return pWePlayer->getDuration();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeGetCurrentPosition(JNIEnv *env, jobject thiz) {
    if (pWePlayer == NULL) {
        // 不涉及到控制状态，不抛异常
        LOGE(LOG_TAG, "nativeGetCurrentPosition...but pWePlayer is NULL");
        return 0;
    }

    return pWePlayer->getCurrentPosition();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeGetAudioSampleRate(JNIEnv *env, jobject thiz) {
    if (pWePlayer == NULL) {
        LOGE(LOG_TAG, "nativeGetAudioSampleRate...but pWePlayer is NULL");
        return 0;
    }

    return pWePlayer->getAudioSampleRate();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeGetAudioChannelNums(JNIEnv *env, jobject thiz) {
    if (pWePlayer == NULL) {
        LOGE(LOG_TAG, "nativeGetAudioChannelNums...but pWePlayer is NULL");
        return 0;
    }

    return pWePlayer->getAudioChannelNums();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeGetAudioBitsPerSample(JNIEnv *env, jobject thiz) {
    if (pWePlayer == NULL) {
        LOGE(LOG_TAG, "natvieGetAudioBitsPerSample...but pWePlayer is NULL");
        return 0;
    }

    return pWePlayer->getAudioBitsPerSample();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeGetPcmMaxBytesPerCallback(JNIEnv *env, jobject thiz) {
    if (pWePlayer == NULL) {
        LOGE(LOG_TAG, "nativeGetPcmMaxBytesPerCallback...but pWePlayer is NULL");
        return 0;
    }

    return pWePlayer->getPcmMaxBytesPerCallback();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeSetRecordPCMFlag(JNIEnv *env, jobject thiz, jboolean record) {
    if (pWePlayer == NULL) {
        LOGE(LOG_TAG, "nativeSetRecordPCMFlag...but pWePlayer is NULL");
        return;
    }

    pWePlayer->setRecordPCMFlag(record);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeSetStopFlag(JNIEnv *env, jobject thiz) {
    if (pWePlayer == NULL) {
        // 允许直接停止，不抛异常
        LOGE(LOG_TAG, "nativeSetStopFlag...but pWePlayer is NULL");
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeSetStopFlag...");
    }

    pWePlayer->setStopFlag();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeStop(JNIEnv *env, jobject thiz) {
    if (pWePlayer == NULL) {
        LOGE(LOG_TAG, "nativeSetStopFlag...but pWePlayer is NULL");
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeStop...");
    }

    pWePlayer->stop();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeReset(JNIEnv *env, jobject thiz) {
    if (pWePlayer == NULL) {
        LOGE(LOG_TAG, "nativeReset...but pWePlayer is NULL");
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeReset...");
    }

    pWePlayer->reset();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WePlayer_nativeRelease(JNIEnv *env, jobject thiz) {
    if (pWePlayer == NULL) {
        // 允许直接释放，不抛异常
        LOGE(LOG_TAG, "nativeRelease...but pWePlayer is NULL");
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeRelease...");
    }

    delete pWePlayer;
    pWePlayer = NULL;
}
// ------------------------------ WePlayer End ------------------------------

// ------------------------------ WeEditor Start ------------------------------
extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WeEditor_nativeSetEditDataSource(JNIEnv *env, jobject thiz,
                                                        jstring dataSource) {
    if (dataSource == NULL || env->GetStringUTFLength(dataSource) == 0) {
        jclass exceptionClass = env->FindClass("java/lang/Exception");
        env->ThrowNew(exceptionClass,
                      "Can't set a 'null' string to data source!");
        env->DeleteLocalRef(exceptionClass);
        return;
    }

    if (pWeEditor == NULL) {
        JavaListenerContainer *javaListenerContainer = new JavaListenerContainer();
        javaListenerContainer->onPreparedListener = new OnPreparedListener(jvm, env, thiz);
        javaListenerContainer->onPlayLoadingListener = new OnNativeLoading(jvm, env, thiz);
        javaListenerContainer->onErrorListener = new OnErrorListener(jvm, env, thiz);
        javaListenerContainer->onCompletionListener = new OnCompletionListener(jvm, env, thiz);
        javaListenerContainer->onPcmDataCall = new OnPCMDataCall(jvm, env, thiz);
        pWeEditor = new WeEditor(javaListenerContainer);
    }

    int jstrUtf16Len = env->GetStringLength(dataSource);
    int jstrUtf8Len = env->GetStringUTFLength(dataSource);
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "GetString UTF-16 Length: %d; UTF-8 Length: %d", jstrUtf16Len, jstrUtf8Len);
    }

    char *source = new char[jstrUtf8Len + 1];// 回收放在 WeEditor 中
    env->GetStringUTFRegion(dataSource, 0, jstrUtf16Len, source);
    source[jstrUtf8Len] = '\0';

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeSetEditDataSource: %s", source);
    }
    pWeEditor->setDataSource(source);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WeEditor_nativePrepareEdit(JNIEnv *env, jobject thiz) {
    if (pWeEditor == NULL) {
        jclass exceptionClass = env->FindClass("java/lang/Exception");
        env->ThrowNew(exceptionClass,
                      "Have you called setDataSource before calling the prepare function?");
        env->DeleteLocalRef(exceptionClass);
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativePrepareEdit...");
    }

    pWeEditor->prepareAsync();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WeEditor_nativeStartEdit(JNIEnv *env, jobject thiz, jint startTimeMsec,
                                                jint endTimeMsec) {
    if (pWeEditor == NULL) {
        jclass exceptionClass = env->FindClass("java/lang/Exception");
        env->ThrowNew(exceptionClass, "Have you called prepare before calling the start function?");
        env->DeleteLocalRef(exceptionClass);
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeStartEdit...range: %d ms ~ %d ms", startTimeMsec, endTimeMsec);
    }

    pWeEditor->start(startTimeMsec, endTimeMsec);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WeEditor_nativeSetStopEditFlag(JNIEnv *env, jobject thiz) {
    if (pWeEditor == NULL) {
        // 允许直接停止，不抛异常
        LOGE(LOG_TAG, "nativeSetStopEditFlag...but pWeEditor is NULL");
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeSetStopEditFlag...");
    }

    pWeEditor->setStopFlag();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WeEditor_nativeStopEdit(JNIEnv *env, jobject thiz) {
    if (pWeEditor == NULL) {
        LOGE(LOG_TAG, "nativeStopEdit...but pWeEditor is NULL");
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeStopEdit...");
    }

    pWeEditor->stop();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WeEditor_nativeResetEdit(JNIEnv *env, jobject thiz) {
    if (pWeEditor == NULL) {
        LOGE(LOG_TAG, "nativeResetEdit...but pWeEditor is NULL");
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeResetEdit...");
    }

    pWeEditor->reset();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_WeEditor_nativeReleaseEdit(JNIEnv *env, jobject thiz) {
    if (pWeEditor == NULL) {
        // 允许直接释放，不抛异常
        LOGE(LOG_TAG, "nativeReleaseEdit...but pWeEditor is NULL");
        return;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeReleaseEdit...");
    }

    delete pWeEditor;
    pWeEditor = NULL;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wtz_ffmpegapi_WeEditor_nativeGetEditDuration(JNIEnv *env, jobject thiz) {
    if (pWeEditor == NULL) {
        // 不涉及到控制状态，不抛异常
        LOGE(LOG_TAG, "nativeGetEditDuration...but pWeEditor is NULL");
        return 0;
    }

    return pWeEditor->getDuration();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wtz_ffmpegapi_WeEditor_nativeGetEditPosition(JNIEnv *env, jobject thiz) {
    if (pWeEditor == NULL) {
        // 不涉及到控制状态，不抛异常
        LOGE(LOG_TAG, "nativeGetEditPosition...but pWeEditor is NULL");
        return 0;
    }

    return pWeEditor->getCurrentPosition();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wtz_ffmpegapi_WeEditor_nativeGetEditAudioSampleRate(JNIEnv *env, jobject thiz) {
    if (pWeEditor == NULL) {
        LOGE(LOG_TAG, "nativeGetEditAudioSampleRate...but pWeEditor is NULL");
        return 0;
    }

    return pWeEditor->getAudioSampleRate();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wtz_ffmpegapi_WeEditor_nativeGetEditAudioChannelNums(JNIEnv *env, jobject thiz) {
    if (pWeEditor == NULL) {
        LOGE(LOG_TAG, "nativeGetEditAudioChannelNums...but pWeEditor is NULL");
        return 0;
    }

    return pWeEditor->getAudioChannelNums();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wtz_ffmpegapi_WeEditor_nativeGetEditAudioBitsPerSample(JNIEnv *env, jobject thiz) {
    if (pWeEditor == NULL) {
        LOGE(LOG_TAG, "nativeGetEditAudioBitsPerSample...but pWeEditor is NULL");
        return 0;
    }

    return pWeEditor->getAudioBitsPerSample();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wtz_ffmpegapi_WeEditor_nativeGetEditPcmMaxBytesPerCallback(JNIEnv *env, jobject thiz) {
    if (pWeEditor == NULL) {
        LOGE(LOG_TAG, "nativeGetEditPcmMaxBytesPerCallback...but pWeEditor is NULL");
        return 0;
    }

    return pWeEditor->getPcmMaxBytesPerCallback();
}
// ------------------------------ WeEditor End ------------------------------
