//
// Created by WTZ on 2019/11/21.
//

#include "AVPacketQueue.h"

AVPacketQueue::AVPacketQueue(PlayStatus *status) {
    this->status = status;
    pthread_mutex_init(&mutex, NULL);
    pthread_cond_init(&condition, NULL);
}

AVPacketQueue::~AVPacketQueue() {
    pthread_mutex_destroy(&mutex);
    pthread_cond_destroy(&condition);
    clearQueue(queue);
}

void AVPacketQueue::putAVpacket(AVPacket *packet) {
    pthread_mutex_lock(&mutex);

    queue.push(packet);
    if (LOG_REPEAT_DEBUG) {
        LOGD(LOG_TAG, "putAVpacket current size：%d", queue.size());
    }
    pthread_cond_signal(&condition);

    pthread_mutex_unlock(&mutex);
}

bool AVPacketQueue::getAVpacket(AVPacket *packet) {
    bool ret = false;
    pthread_mutex_lock(&mutex);

    // 循环是为了在队列为空导致阻塞等待后被唤醒时继续取下一个
    while (status != NULL && status->isPlaying()) {
        if (queue.size() > 0) {
            // 获取队首 AVPacket
            AVPacket *avPacket = queue.front();
            // 复制队首 AVPacket 所指向的数据地址到给定的 AVPacket
            if (av_packet_ref(packet, avPacket) == 0) {
                ret = true;
                // 弹出队首 AVPacket
                queue.pop();
                if (LOG_REPEAT_DEBUG) {
                    LOGD(LOG_TAG, "Pop an avpacket from the queue, %d remaining.", queue.size());
                }
            }
            // 释放已经出队的 AVPacket
            av_packet_free(&avPacket);
            av_free(avPacket);
            avPacket = NULL;
            break;
        } else {
            pthread_cond_wait(&condition, &mutex);
        }
    }

    pthread_mutex_unlock(&mutex);
    return ret;
}

void AVPacketQueue::informPutFinished() {
    pthread_mutex_lock(&mutex);

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "informPutFinished");
    }
    pthread_cond_signal(&condition);

    pthread_mutex_unlock(&mutex);
}

int AVPacketQueue::getQueueSize() {
    int size = 0;
    pthread_mutex_lock(&mutex);
    size = queue.size();
    pthread_mutex_unlock(&mutex);
    return size;
}

void AVPacketQueue::clearQueue(std::queue<AVPacket *> &queue) {
    std::queue<AVPacket *> empty;
    swap(empty, queue);
}
