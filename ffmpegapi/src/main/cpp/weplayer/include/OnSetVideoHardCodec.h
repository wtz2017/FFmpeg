//
// Created by WTZ on 2020/2/10.
//

#ifndef FFMPEG_ONSETVIDEOHARDCODEC_H
#define FFMPEG_ONSETVIDEOHARDCODEC_H


#include "JavaListener.h"

class OnSetVideoHardCodec : public JavaListener {

private:
    const char *LOG_TAG = "_OnSetVideoHardCodec";

public:
    OnSetVideoHardCodec(JavaVM *vm, JNIEnv *mainEnv, jobject obj)
            : JavaListener(vm, mainEnv, obj) {
    }

    ~OnSetVideoHardCodec() {
    }

    const char *getMethodName() {
        return "onNativeSetVideoHardCodec";
    }

    const char *getMethodSignature() {
        return "(Z)V";
    }

    void reallyCallback(JNIEnv *env, jobject obj, jmethodID methodId, va_list args) {
        bool hardCodec = va_arg(args, bool);
        env->CallVoidMethod(obj, methodId, hardCodec);
    }

};


#endif //FFMPEG_ONSETVIDEOHARDCODEC_H
