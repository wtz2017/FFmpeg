#include <jni.h>
#include <AndroidLog.h>
#include "pthread.h"
#include "queue"
#include "unistd.h"

pthread_t simple_thread;

bool stop_produce = false;
pthread_t producer_thread;
pthread_t consumer_thread;
pthread_mutex_t product_mutex;
pthread_cond_t product_cond;
std::queue<int> product_queue;

void *simpleThreadCallback(void *data) {
    LOGI("This is a simple thread!")
    pthread_exit(&simple_thread);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_CppThreadDemo_testSimpleThread(JNIEnv *env, jobject thiz) {
    pthread_create(&simple_thread, NULL, simpleThreadCallback, NULL);
}

void *producerCallback(void *data) {
    while (!stop_produce) {
        pthread_mutex_lock(&product_mutex);

        product_queue.push(1);
        LOGI("生产了一个产品，目前总量为%d，准备通知消费者", product_queue.size());
        pthread_cond_signal(&product_cond);

        pthread_mutex_unlock(&product_mutex);

        sleep(5);// 睡眠 5 秒
    }
    LOGE("producer exiting...");
    pthread_exit(&producer_thread);
}

void *consumerCallback(void *data) {
    while (!stop_produce) {
        pthread_mutex_lock(&product_mutex);

        if (product_queue.size() > 0) {
            product_queue.pop();
            LOGI("消费了一个产品，还剩%d个产品", product_queue.size());
        } else {
            LOGD("没有产品可消费，等待中...");
            pthread_cond_wait(&product_cond, &product_mutex);
        }

        pthread_mutex_unlock(&product_mutex);

        usleep(500 * 1000);// 睡眠 500000 微秒，即 500 毫秒
    }
    LOGE("consumer exiting...");
    pthread_exit(&consumer_thread);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_CppThreadDemo_startProduceConsumeThread(JNIEnv *env, jobject thiz) {
    stop_produce = false;

    for (int i = 0; i < 10; ++i) {
        product_queue.push(i);
    }

    pthread_mutex_init(&product_mutex, NULL);
    pthread_cond_init(&product_cond, NULL);

    pthread_create(&producer_thread, NULL, producerCallback, NULL);
    pthread_create(&consumer_thread, NULL, consumerCallback, NULL);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_CppThreadDemo_stopProduceConsumeThread(JNIEnv *env, jobject thiz) {
    stop_produce = true;
    pthread_cond_signal(&product_cond);// 唤醒还在等待的消费者
}

