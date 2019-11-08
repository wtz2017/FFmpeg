/**
 * Created by WTZ on 2019/11/8.
 */

#ifndef FFMPEG_ONRESULTLISTENER_H
#define FFMPEG_ONRESULTLISTENER_H

#include "JavaListener.h"
#include "AndroidLog.h"

class OnResultListener : public JavaListener {
public:
    OnResultListener(JavaVM *vm, JNIEnv *mainEnv, jobject obj)
            : JavaListener(vm, mainEnv, obj) {
    }

    ~OnResultListener();

    const char *getMethodName() {
        return "onResult";
    }

    const char *getMethodSignature() {
        return "(ILjava/lang/String;)V";
    }

    void reallyCallback(JNIEnv *env, jobject obj, jmethodID methodId, va_list args) {
        int code = va_arg(args, int);
        char *msg = va_arg(args, char *);
        LOGD("reallyCallback[%s] code=%d, msg=%s", getMethodName(), code, msg);

        jstring stringUtf = env->NewStringUTF(msg);
        env->CallVoidMethod(obj, methodId, code, stringUtf);
        env->DeleteLocalRef(stringUtf);
    }
};

#endif //FFMPEG_ONRESULTLISTENER_H
