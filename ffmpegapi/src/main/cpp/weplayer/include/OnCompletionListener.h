//
// Created by WTZ on 2019/12/5.
//

#ifndef FFMPEG_ONCOMPLETIONLISTENER_H
#define FFMPEG_ONCOMPLETIONLISTENER_H

#include "JavaListener.h"

class OnCompletionListener : public JavaListener {

private:
    const char *LOG_TAG = "_OnCompletionListener";

public:
    OnCompletionListener(JavaVM *vm, JNIEnv *mainEnv, jobject obj)
            : JavaListener(vm, mainEnv, obj) {
    }

    ~OnCompletionListener() {
    }

    const char *getMethodName() {
        return "onNativeCompletion";
    }

    const char *getMethodSignature() {
        return "()V";
    }

    void reallyCallback(JNIEnv *env, jobject obj, jmethodID methodId, va_list args) {
        env->CallVoidMethod(obj, methodId);
    }

};


#endif //FFMPEG_ONCOMPLETIONLISTENER_H
