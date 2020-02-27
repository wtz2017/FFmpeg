//
// Created by WTZ on 2020/2/27.
//

#ifndef FFMPEG_ONSEEKCOMPLETE_H
#define FFMPEG_ONSEEKCOMPLETE_H


#include "JavaListener.h"

class OnSeekComplete : public JavaListener {

private:
    const char *LOG_TAG = "_OnSeekComplete";

public:
    OnSeekComplete(JavaVM *jvm, JNIEnv *mainEnv, jobject obj)
    : JavaListener(jvm, mainEnv, obj) {
    }

    ~OnSeekComplete() {
    }

    const char *getMethodName() {
        return "onNativeSeekComplete";
    }

    const char *getMethodSignature() {
        return "()V";
    }

    void reallyCallback(JNIEnv *env, jobject obj, jmethodID methodId, va_list args) {
        env->CallVoidMethod(obj, methodId);
    }

};


#endif //FFMPEG_ONSEEKCOMPLETE_H
