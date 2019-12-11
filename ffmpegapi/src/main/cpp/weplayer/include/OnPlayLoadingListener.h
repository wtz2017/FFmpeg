//
// Created by WTZ on 2019/11/27.
//

#ifndef FFMPEG_ONPLAYLOADINGLISTENER_H
#define FFMPEG_ONPLAYLOADINGLISTENER_H


#include "JavaListener.h"
#include "AndroidLog.h"

class OnPlayLoadingListener : public JavaListener {

private:
    const char *LOG_TAG = "_OnPlayLoadingListener";

public:
    OnPlayLoadingListener(JavaVM *jvm, JNIEnv *mainEnv, jobject obj)
            : JavaListener(jvm, mainEnv, obj) {
    }

    ~OnPlayLoadingListener() {
    }

    const char *getMethodName() {
        return "onNativePlayLoading";
    }

    const char *getMethodSignature() {
        return "(Z)V";
    }

    void reallyCallback(JNIEnv *env, jobject obj, jmethodID methodId, va_list args) {
        bool isLoading = va_arg(args, bool);
        if (LOG_DEBUG) {
            LOGD(LOG_TAG, "%s args=%d", getMethodName(), isLoading);
        }

        env->CallVoidMethod(obj, methodId, isLoading);
    }

};


#endif //FFMPEG_ONPLAYLOADINGLISTENER_H
