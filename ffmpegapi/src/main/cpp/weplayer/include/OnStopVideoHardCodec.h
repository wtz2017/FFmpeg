//
// Created by WTZ on 2020/2/10.
//

#ifndef FFMPEG_ONSTOPVIDEOHARDCODEC_H
#define FFMPEG_ONSTOPVIDEOHARDCODEC_H


#include "JavaListener.h"

class OnStopVideoHardCodec : public JavaListener {

private:
    const char *LOG_TAG = "_OnStopVideoHardCodec";

public:
    OnStopVideoHardCodec(JavaVM *vm, JNIEnv *mainEnv, jobject obj)
            : JavaListener(vm, mainEnv, obj) {
    }

    ~OnStopVideoHardCodec() {
    }

    const char *getMethodName() {
        return "onNativeStopVideoHardCodec";
    }

    const char *getMethodSignature() {
        return "()V";
    }

    void reallyCallback(JNIEnv *env, jobject obj, jmethodID methodId, va_list args) {
        env->CallVoidMethod(obj, methodId);
    }

};


#endif //FFMPEG_ONSTOPVIDEOHARDCODEC_H
