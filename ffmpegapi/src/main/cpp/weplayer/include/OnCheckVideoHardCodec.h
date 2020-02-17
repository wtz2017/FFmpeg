//
// Created by WTZ on 2020/2/8.
//

#ifndef FFMPEG_ONCHECKVIDEOHARDCODEC_H
#define FFMPEG_ONCHECKVIDEOHARDCODEC_H

#include "JavaListener.h"

class OnCheckVideoHardCodec : public JavaListener {

private:
    const char *LOG_TAG = "_OnCheckHardCodec";

public:
    OnCheckVideoHardCodec(JavaVM *vm, JNIEnv *mainEnv, jobject obj)
            : JavaListener(vm, mainEnv, obj) {
    }

    ~OnCheckVideoHardCodec() {
    }

    const char *getMethodName() {
        return "onNativeCheckVideoHardCodec";
    }

    const char *getMethodSignature() {
        return "(Ljava/lang/String;)Z";
    }

    void reallyCallback(JNIEnv *env, jobject obj, jmethodID methodId, va_list args) {
        const char *codecName = va_arg(args, const char *);
        char *result = va_arg(args, char *);

        jstring jStrUtf = env->NewStringUTF(codecName);
        *result = env->CallBooleanMethod(obj, methodId, jStrUtf);
        env->DeleteLocalRef(jStrUtf);
    }
};


#endif //FFMPEG_ONCHECKVIDEOHARDCODEC_H
