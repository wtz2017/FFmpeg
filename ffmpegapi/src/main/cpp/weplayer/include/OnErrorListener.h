//
// Created by WTZ on 2019/12/4.
//

#ifndef FFMPEG_ONERRORLISTENER_H
#define FFMPEG_ONERRORLISTENER_H

#include "JavaListener.h"

class OnErrorListener : public JavaListener {

private:
    const char *LOG_TAG = "_OnErrorListener";

public:
    OnErrorListener(JavaVM *jvm, JNIEnv *mainEnv, jobject obj)
            : JavaListener(jvm, mainEnv, obj) {
    }
    ~OnErrorListener() {
    };

    const char *getMethodName() {
        return "onNativeError";
    }

    const char *getMethodSignature() {
        return "(ILjava/lang/String;)V";
    }

    void reallyCallback(JNIEnv *env, jobject obj, jmethodID methodId, va_list args) {
        int code = va_arg(args, int);
        char *msg = va_arg(args, char *);

        jstring jStrUtf = env->NewStringUTF(msg);
        env->CallVoidMethod(obj, methodId, code, jStrUtf);
        env->DeleteLocalRef(jStrUtf);
    }

};


#endif //FFMPEG_ONERRORLISTENER_H
