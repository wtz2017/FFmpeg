#include <jni.h>
#include <pthread.h>
#include <unistd.h>
#include "AndroidLog.h"
#include "JavaListener.h"
#include "OnResultListener.h"
#include "queue"

JavaVM *jvm;

pthread_t simpleThread;

bool stopProduce = false;
pthread_t producerThread;
pthread_t consumerThread;
pthread_mutex_t productMutex;
pthread_cond_t productCond;
std::queue<int> productQueue;

pthread_t callJavaThread;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    jvm = vm;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    return JNI_VERSION_1_6;
}

void *simpleThreadCallback(void *data) {
    LOGI("This is a simple thread!(pid=%d tid=%d)", getpid(), gettid());
    pthread_exit(&simpleThread);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_CppThreadDemo_testSimpleThread(JNIEnv *env, jobject thiz) {
    LOGI("Call testSimpleThread from java!(pid=%d tid=%d)", getpid(), gettid());
    pthread_create(&simpleThread, NULL, simpleThreadCallback, NULL);
}

void *producerCallback(void *data) {
    LOGI("This is producer thread!(pid=%d tid=%d)", getpid(), gettid());
    while (!stopProduce) {
        pthread_mutex_lock(&productMutex);

        productQueue.push(1);
        LOGI("生产了一个产品，目前总量为%d，准备通知消费者", productQueue.size());
        pthread_cond_signal(&productCond);

        pthread_mutex_unlock(&productMutex);

        sleep(5);// 睡眠 5 秒
    }
    LOGE("producer exiting...");
    pthread_exit(&producerThread);
}

void *consumerCallback(void *data) {
    LOGI("This is consumer thread!(pid=%d tid=%d)", getpid(), gettid());
    while (!stopProduce) {
        pthread_mutex_lock(&productMutex);

        if (productQueue.size() > 0) {
            productQueue.pop();
            LOGI("消费了一个产品，还剩%d个产品", productQueue.size());
        } else {
            LOGD("没有产品可消费，等待中...");
            pthread_cond_wait(&productCond, &productMutex);
        }

        pthread_mutex_unlock(&productMutex);

        usleep(500 * 1000);// 睡眠 500000 微秒，即 500 毫秒
    }
    LOGE("consumer exiting...");
    pthread_exit(&consumerThread);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_CppThreadDemo_startProduceConsumeThread(JNIEnv *env, jobject thiz) {
    LOGI("Call startProduceConsumeThread from java!(pid=%d tid=%d)", getpid(), gettid());
    stopProduce = false;

    for (int i = 0; i < 10; ++i) {
        productQueue.push(i);
    }

    pthread_mutex_init(&productMutex, NULL);
    pthread_cond_init(&productCond, NULL);

    pthread_create(&producerThread, NULL, producerCallback, NULL);
    pthread_create(&consumerThread, NULL, consumerCallback, NULL);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_CppThreadDemo_stopProduceConsumeThread(JNIEnv *env, jobject thiz) {
    LOGI("Call stopProduceConsumeThread from java!(pid=%d tid=%d)", getpid(), gettid());
    stopProduce = true;
    pthread_cond_signal(&productCond);// 唤醒还在等待的消费者
}

void *callJavaThreadCallback(void *data) {
    JavaListener *listener = (JavaListener *) data;
    listener->callback(2, 2, "call java from c++ in child thread");
    pthread_exit(&callJavaThread);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_CppThreadDemo_callbackFromC(JNIEnv *env, jobject thiz) {
    // Fix: JNI ERROR (app bug): accessed stale local reference
    jobject globalObj = env->NewGlobalRef(thiz);

    // JavaListener 构造方法需要在 C++ 主线程中调用，即直接从 java 层调用下来的线程
    JavaListener *javaListener = new OnResultListener(jvm, env, globalObj);

    javaListener->callback(2, 1, "call java from c++ in main thread");

    pthread_create(&callJavaThread, NULL, callJavaThreadCallback, javaListener);
}

