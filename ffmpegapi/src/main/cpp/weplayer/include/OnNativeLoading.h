//
// Created by WTZ on 2019/11/27.
//

#ifndef FFMPEG_ONNATIVELOADING_H
#define FFMPEG_ONNATIVELOADING_H


#include "JavaListener.h"

class OnNativeLoading : public JavaListener {

private:
    const char *LOG_TAG = "_OnNativeLoading";

public:
    OnNativeLoading(JavaVM *jvm, JNIEnv *mainEnv, jobject obj)
            : JavaListener(jvm, mainEnv, obj) {
    }

    ~OnNativeLoading() {
    }

    const char *getMethodName() {
        return "onNativeLoading";
    }

    const char *getMethodSignature() {
        return "(Z)V";
    }

    void reallyCallback(JNIEnv *env, jobject obj, jmethodID methodId, va_list args) {
        bool isLoading = va_arg(args, bool);

        env->CallVoidMethod(obj, methodId, isLoading);
    }

};


#endif //FFMPEG_ONNATIVELOADING_H
