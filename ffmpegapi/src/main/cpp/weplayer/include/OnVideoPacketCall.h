//
// Created by WTZ on 2020/2/9.
//

#ifndef FFMPEG_ONVIDEOPACKETCALL_H
#define FFMPEG_ONVIDEOPACKETCALL_H


#include "JavaListener.h"

class OnVideoPacketCall : public JavaListener {

private:
    const char *LOG_TAG = "_OnVideoPacketCall";

public:
    OnVideoPacketCall(JavaVM *vm, JNIEnv *mainEnv, jobject obj)
            : JavaListener(vm, mainEnv, obj) {
    }

    ~OnVideoPacketCall() {
    }

    const char *getMethodName() {
        return "onNativeVideoPacketCall";
    }

    const char *getMethodSignature() {
        return "(I[B)V";
    }

    void reallyCallback(JNIEnv *env, jobject obj, jmethodID methodId, va_list args) {
        int packetSize = va_arg(args, int);
        uint8_t *packet = va_arg(args, uint8_t *);

        jbyteArray dataBytes = env->NewByteArray(packetSize);
        env->SetByteArrayRegion(dataBytes, 0, packetSize, (jbyte *) packet);

        env->CallVoidMethod(obj, methodId, packetSize, dataBytes);

        env->DeleteLocalRef(dataBytes);
    }

};


#endif //FFMPEG_ONVIDEOPACKETCALL_H
