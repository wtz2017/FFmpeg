//
// Created by WTZ on 2019/11/21.
//

#include "AVPacketQueue.h"

AVPacketQueue::AVPacketQueue(char *queueName) {
    this->queueName = queueName;
    allowOperation = true;
    pthread_mutex_init(&mutex, NULL);
    pthread_cond_init(&condition, NULL);
}

AVPacketQueue::~AVPacketQueue() {
    allowOperation = false;
    releaseQueue();
    queueName = NULL;
    pthread_cond_destroy(&condition);
    pthread_mutex_destroy(&mutex);
}

void AVPacketQueue::setAllowOperation(bool allow) {
    this->allowOperation = allow;
}

void AVPacketQueue::setProductDataComplete(bool complete) {
    pthread_mutex_lock(&mutex);

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "%s setProductDataComplete: %d", queueName, complete);
    }
    productDataComplete = complete;// 作用：当完成标志位设置早于消费者读数据时，消费者不会再 wait
    if (complete) {// 作用：当完成标志位设置晚于消费者 wait 时，可以通知消费者退出
        pthread_cond_signal(&condition);
    }

    pthread_mutex_unlock(&mutex);
}

bool AVPacketQueue::isProductDataComplete() {
    bool ret;
    pthread_mutex_lock(&mutex);
    ret = productDataComplete;
    pthread_mutex_unlock(&mutex);
    return ret;
}

void AVPacketQueue::putAVpacket(AVPacket *packet) {
    pthread_mutex_lock(&mutex);

    queue.push(packet);
    if (LOG_REPEAT_DEBUG) {
        LOGD(LOG_TAG, "%s putAVpacket current size：%d", queueName, queue.size());
    }
    pthread_cond_signal(&condition);

    pthread_mutex_unlock(&mutex);
}

bool AVPacketQueue::getAVpacket(AVPacket *packet) {
    bool ret = false;
    pthread_mutex_lock(&mutex);

    // 循环是为了在队列为空导致阻塞等待后被唤醒时继续取下一个
    while (allowOperation) {
        if (queue.size() > 0) {
            // 获取队首 AVPacket
            AVPacket *avPacket = queue.front();
            // 复制队首 AVPacket 所指向的数据地址到给定的 AVPacket
            if (av_packet_ref(packet, avPacket) == 0) {
                ret = true;
                // 弹出队首 AVPacket
                queue.pop();
                if (LOG_REPEAT_DEBUG) {
                    LOGD(LOG_TAG, "%s pop an avpacket from the queue, %d remaining.", queueName,
                         queue.size());
                }
            }
            // 释放已经出队的 AVPacket
            av_packet_free(&avPacket);
//            av_free(avPacket);
            av_freep(&avPacket);// 使用 av_freep(&buf) 代替 av_free(buf)
            avPacket = NULL;
            break;
        } else if (!productDataComplete) {
            pthread_cond_wait(&condition, &mutex);
        } else {
            // 队列为空，也不再生产数据，那就退出
            break;
        }
    }

    pthread_mutex_unlock(&mutex);
    return ret;
}

int AVPacketQueue::getQueueSize() {
    int size = 0;
    pthread_mutex_lock(&mutex);
    size = queue.size();
    pthread_mutex_unlock(&mutex);
    return size;
}

void AVPacketQueue::clearQueue() {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "%s clearQueue...", queueName);
    }
    pthread_cond_signal(&condition);
    pthread_mutex_lock(&mutex);

    AVPacket *packet = NULL;
    while (!queue.empty()) {
        packet = queue.front();
        queue.pop();
        av_packet_free(&packet);
//        av_free(packet);
        av_freep(&packet);// 使用 av_freep(&buf) 代替 av_free(buf)
    }
    packet = NULL;

    pthread_mutex_unlock(&mutex);
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "%s clearQueue finished", queueName);
    }
}

void AVPacketQueue::releaseQueue() {
    clearQueue();
    std::queue<AVPacket *> empty;
    swap(empty, queue);
}
