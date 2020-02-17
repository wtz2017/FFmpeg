//
// Created by WTZ on 2020/2/9.
//

#ifndef FFMPEG_ONINITVIDEOHARDCODEC_H
#define FFMPEG_ONINITVIDEOHARDCODEC_H

#include "JavaListener.h"

class OnInitVideoHardCodec : public JavaListener {

private:
    const char *LOG_TAG = "_OnInitHardCodec";

public:
    OnInitVideoHardCodec(JavaVM *vm, JNIEnv *mainEnv, jobject obj)
            : JavaListener(vm, mainEnv, obj) {
    }

    ~OnInitVideoHardCodec() {
    }

    const char *getMethodName() {
        return "onNativeInitVideoHardCodec";
    }

    const char *getMethodSignature() {
        return "(Ljava/lang/String;II[B[B)Z";
    }

    void reallyCallback(JNIEnv *env, jobject obj, jmethodID methodId, va_list args) {
        const char *codecName = va_arg(args, const char *);
        int width = va_arg(args, int);
        int height = va_arg(args, int);
        int csd0Size = va_arg(args, int);
        int csd1Size = va_arg(args, int);
        uint8_t *csd0 = va_arg(args, uint8_t *);
        uint8_t *csd1 = va_arg(args, uint8_t *);
        char *result = va_arg(args, char *);

        jstring jStrUtf = env->NewStringUTF(codecName);
        jbyteArray csd0Byte = env->NewByteArray(csd0Size);
        env->SetByteArrayRegion(csd0Byte, 0, csd0Size, (jbyte *)csd0);
        jbyteArray csd1Byte = env->NewByteArray(csd1Size);
        env->SetByteArrayRegion(csd1Byte, 0, csd1Size, (jbyte *)csd1);

        *result = env->CallBooleanMethod(obj, methodId, jStrUtf, width, height, csd0Byte, csd1Byte);

        env->DeleteLocalRef(jStrUtf);
        env->DeleteLocalRef(csd0Byte);
        env->DeleteLocalRef(csd1Byte);
    }

};


#endif //FFMPEG_ONINITVIDEOHARDCODEC_H
