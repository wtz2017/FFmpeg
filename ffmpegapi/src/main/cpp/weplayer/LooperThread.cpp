//
// Created by WTZ on 2019/12/8.
//

#include "LooperThread.h"

LooperThread::LooperThread(const char *threadName, void *context,
                           void (*messageHanler)(int msgType, void *data)) {
    this->threadName = threadName;
    this->context = context;
    this->messageHanler = messageHanler;
    pthread_mutex_init(&mutex, NULL);
    pthread_cond_init(&condition, NULL);
}

LooperThread::~LooperThread() {
    if (!exit) {
        shutdown();
    }
    releaseQueue();
    pthread_cond_destroy(&condition);
    pthread_mutex_destroy(&mutex);
    messageHanler = NULL;
    threadName = NULL;
    context = NULL;
}

void *threadCallback(void *context) {
    LooperThread *looperThread = (LooperThread *) context;
    if (LOG_DEBUG) {
        LOGD(looperThread->LOG_TAG, "%s run...", looperThread->threadName);
    }

    looperThread->loop();

    if (LOG_DEBUG) {
        LOGD(looperThread->LOG_TAG, "%s exit", looperThread->threadName);
    }
    pthread_exit(&looperThread->thread);
}

void LooperThread::create() {
    pthread_create(&thread, NULL, threadCallback, this);
}

void LooperThread::loop() {
    int msgType;
    while (!exit) {
        pthread_mutex_lock(&mutex);
        while (queue.size() == 0) {
            if (LOG_DEBUG) {
                LOGD(LOG_TAG, "%s loop waiting...", threadName);
            }
            pthread_cond_wait(&condition, &mutex);
            if (LOG_DEBUG) {
                LOGD(LOG_TAG, "%s loop wait finish!", threadName);
            }
            if (exit) {
                LOGW(LOG_TAG, "%s exit after loop wait finish!", threadName);
                pthread_mutex_unlock(&mutex);
                threadTerminated = true;
                return;
            }
        }

        msgType = queue.front();
        queue.pop();
        pthread_mutex_unlock(&mutex);

        if (LOG_DEBUG) {
            LOGD(LOG_TAG, "%s call messageHanler: msgType=%d", threadName, msgType);
        }
        messageHanler(msgType, context);
    }
    threadTerminated = true;
}

/**
 * 一定要在所有消息处理函数完成后才能调用
 */
void LooperThread::shutdown() {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "%s shutdown...", threadName);
    }
    exit = true;
    pthread_cond_signal(&condition);

    int sleepCount = 0;
    while (!threadTerminated) {
        if (sleepCount > 10) {
            break;
        }
        sleepCount++;
        usleep(10 * 1000);
    }
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "Thread %s Terminated after sleep %d ms",
                threadName, sleepCount * 10);
    }
}

void LooperThread::sendMessage(int msgType) {
    pthread_mutex_lock(&mutex);

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "%s sendMessage：%d", threadName, msgType);
    }
    queue.push(msgType);
    pthread_cond_signal(&condition);

    pthread_mutex_unlock(&mutex);
}

void LooperThread::clearMessage() {
    pthread_mutex_lock(&mutex);
    while (!queue.empty()) {
        queue.pop();
    }
    pthread_mutex_unlock(&mutex);
}

void LooperThread::releaseQueue() {
    clearMessage();
    std::queue<int> empty;
    swap(empty, queue);
}
