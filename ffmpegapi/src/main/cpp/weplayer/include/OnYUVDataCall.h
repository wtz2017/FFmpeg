//
// Created by WTZ on 2020/1/20.
//

#ifndef FFMPEG_ONYUVDATACALL_H
#define FFMPEG_ONYUVDATACALL_H


#include "JavaListener.h"

class OnYUVDataCall : public JavaListener {

private:
    const char *LOG_TAG = "_OnYUVDataCall";

public:
    OnYUVDataCall(JavaVM *jvm, JNIEnv *mainEnv, jobject obj)
            : JavaListener(jvm, mainEnv, obj) {
    }

    ~OnYUVDataCall() {
    };

    const char *getMethodName() {
        return "onNativeYUVDataCall";
    }

    const char *getMethodSignature() {
        return "(II[B[B[B)V";
    }

    void reallyCallback(JNIEnv *env, jobject obj, jmethodID methodId, va_list args) {
        int width = va_arg(args, int);
        int height = va_arg(args, int);
        uint8_t *y = va_arg(args, uint8_t *);
        uint8_t *u = va_arg(args, uint8_t *);
        uint8_t *v = va_arg(args, uint8_t *);

        int ySize = width * height;
        int uvSize = ySize / 4;

        jbyteArray yByte = env->NewByteArray(ySize);
        env->SetByteArrayRegion(yByte, 0, ySize, (jbyte *)y);
        jbyteArray uByte = env->NewByteArray(uvSize);
        env->SetByteArrayRegion(uByte, 0, uvSize, (jbyte *)u);
        jbyteArray vByte = env->NewByteArray(uvSize);
        env->SetByteArrayRegion(vByte, 0, uvSize, (jbyte *)v);

        env->CallVoidMethod(obj, methodId, width, height, yByte, uByte, vByte);

        env->DeleteLocalRef(yByte);
        env->DeleteLocalRef(uByte);
        env->DeleteLocalRef(vByte);
    }

};


#endif //FFMPEG_ONYUVDATACALL_H
