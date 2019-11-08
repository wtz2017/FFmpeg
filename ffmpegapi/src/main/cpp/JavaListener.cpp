/**
 * Created by WTZ on 2019/11/8.
 */

#include "JavaListener.h"
#include "AndroidLog.h"

JavaListener::JavaListener(JavaVM *jvm, JNIEnv *mainEnv, jobject obj) {
    _mainTid = gettid();
    _jvm = jvm;
    _mainEnv = mainEnv;
    _obj = obj;
}

JNIEnv *JavaListener::initCallbackEnv() {
    pid_t currentTid = gettid();
    if (currentTid == _mainTid) {
        // 在 C++ 主线程中直接使用主线程 env
        return _mainEnv;
    }

    // 在 C++ 子线程中要使用子线程 env
    JNIEnv *env;
    _jvm->AttachCurrentThread(&env, 0);
    return env;
}

void JavaListener::releaseCallbackEnv() {
    pid_t currentTid = gettid();
    if (currentTid == _mainTid) {
        return;
    }

    _jvm->DetachCurrentThread();
}

void JavaListener::callback(int argCount, ...) {
    JNIEnv *env = initCallbackEnv();

    if (_methodID == NULL) {
        _methodID = env->GetMethodID(env->GetObjectClass(_obj), getMethodName(), getMethodSignature());
    }

    va_list args;
    va_start(args, argCount);

    reallyCallback(env, _obj, _methodID, args);

    va_end(args);

    releaseCallbackEnv();
}
