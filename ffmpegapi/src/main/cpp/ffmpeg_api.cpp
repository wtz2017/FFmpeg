#include <jni.h>
#include <string>
#include <android/log.h>
#include <AndroidLog.h>
#include "queue"
#include "unistd.h"

extern "C"
{
#include <libavformat/avformat.h>
}

pthread_t simpleThread;

bool stopProduce = false;
pthread_t producerThread;
pthread_t consumerThread;
pthread_mutex_t product_mutex;
pthread_cond_t product_cond;
std::queue<int> product_queue;


extern "C"
JNIEXPORT jstring JNICALL
Java_com_wtz_ffmpegapi_API_stringFromJNI(JNIEnv *env, jobject thiz) {
    std::string hello = "Welcome to FFmpeg";
    return env->NewStringUTF(hello.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_API_testFFmpeg(JNIEnv *env, jobject thiz) {
    av_register_all();
    AVCodec *c_temp = av_codec_next(NULL);
    while (c_temp != NULL)
    {
        switch (c_temp->type)
        {
            case AVMEDIA_TYPE_VIDEO:
                LOGI("[Video]:%s", c_temp->name);
                break;
            case AVMEDIA_TYPE_AUDIO:
                LOGI("[Audio]:%s", c_temp->name);
                break;
            default:
                LOGI("[Other]:%s", c_temp->name);
                break;
        }
        c_temp = c_temp->next;
    }
}

void *simpleThreadCall(void *data) {
    LOGI("This is a simple thread!")
    pthread_exit(&simpleThread);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_API_testSimpleThread(JNIEnv *env, jobject thiz) {
    pthread_create(&simpleThread, NULL, simpleThreadCall, NULL);
}

void *producerCall(void *data) {
    while (!stopProduce) {
        pthread_mutex_lock(&product_mutex);

        product_queue.push(1);
        LOGI("生产了一个产品，目前总量为%d，准备通知消费者", product_queue.size());
        pthread_cond_signal(&product_cond);

        pthread_mutex_unlock(&product_mutex);

        sleep(5);// 睡眠 5 秒
    }
    LOGE("producer exiting...");
    pthread_exit(&producerThread);
}

void *consumerCall(void *data) {
    while (!stopProduce) {
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
    pthread_exit(&consumerThread);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_API_startProduceConsumeThread(JNIEnv *env, jobject thiz) {
    stopProduce = false;

    for (int i = 0; i < 10; ++i) {
        product_queue.push(i);
    }

    pthread_mutex_init(&product_mutex, NULL);
    pthread_cond_init(&product_cond, NULL);

    pthread_create(&producerThread, NULL, producerCall, NULL);
    pthread_create(&consumerThread, NULL, consumerCall, NULL);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_API_stopProduceConsumeThread(JNIEnv *env, jobject thiz) {
    stopProduce = true;
    pthread_cond_signal(&product_cond);// 唤醒还在等待的消费者
}