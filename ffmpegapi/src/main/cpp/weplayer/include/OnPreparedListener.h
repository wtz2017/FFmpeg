//
// Created by WTZ on 2019/11/20.
//

#ifndef FFMPEG_ONPREPAREDLISTENER_H
#define FFMPEG_ONPREPAREDLISTENER_H

#include "JavaListener.h"
#include "AndroidLog.h"

class OnPreparedListener : public JavaListener {
private:
    const char *LOG_TAG = "_OnPreparedListener";

public:
    OnPreparedListener(JavaVM *vm, JNIEnv *mainEnv, jobject obj)
            : JavaListener(vm, mainEnv, obj) {
    }

    ~OnPreparedListener() {
    }

    const char *getMethodName() {
        return "onNativePrepared";
    }

    const char *getMethodSignature() {
        return "(Ljava/lang/String;)V";
    }

    void reallyCallback(JNIEnv *env, jobject obj, jmethodID methodId, va_list args) {
        char *msg = va_arg(args, char *);
        if (LOG_DEBUG) {
            LOGD(LOG_TAG, "%s args=%s", getMethodName(), msg);
        }

        jstring stringUtf = env->NewStringUTF(msg);
        env->CallVoidMethod(obj, methodId, stringUtf);
        env->DeleteLocalRef(stringUtf);
    }
};

#endif //FFMPEG_ONPREPAREDLISTENER_H
