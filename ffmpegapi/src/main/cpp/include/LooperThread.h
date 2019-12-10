//
// Created by WTZ on 2019/12/8.
//

#ifndef FFMPEG_LOOPERTHREAD_H
#define FFMPEG_LOOPERTHREAD_H

#include "queue"
#include <pthread.h>
#include <unistd.h>
#include "AndroidLog.h"

class LooperThread {

private:
    void *context = NULL;

    void (*messageHanler)(int msgType, void *context) = NULL;

    std::queue<int> queue;
    pthread_mutex_t mutex;
    pthread_cond_t condition;

    bool exit = false;
    bool threadTerminated = false;

public:
    const char *LOG_TAG = "LooperThread";
    const char *threadName = NULL;
    pthread_t thread;

public:
    LooperThread(const char *threadName, void *context,
                 void(*messageHanler)(int msgType, void *data));

    ~LooperThread();

    void create();

    void loop();

    void sendMessage(int msgType);

    void clearMessage();

    /**
     * 一定要在所有消息处理函数完成后才能调用
     */
    void shutdown();

private:
    void releaseQueue();

};


#endif //FFMPEG_LOOPERTHREAD_H
