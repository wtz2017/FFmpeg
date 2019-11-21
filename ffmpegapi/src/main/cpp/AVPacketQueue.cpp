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
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "putAVpacket current size：%d", queue.size());
    }
    pthread_cond_signal(&condition);

    pthread_mutex_unlock(&mutex);
}

void AVPacketQueue::getAVpacket(AVPacket *packet) {
    pthread_mutex_lock(&mutex);

    while (status != NULL && status->isPlaying()) {
        if (queue.size() > 0) {
            // 获取队首 AVPacket
            AVPacket *avPacket = queue.front();
            // 复制队首 AVPacket 所指向的数据地址到给定的 AVPacket
            if (av_packet_ref(packet, avPacket) == 0) {
                // 弹出队首 AVPacket
                queue.pop();
            }
            // 释放已经出队的 AVPacket
            av_packet_free(&avPacket);
            av_free(avPacket);
            avPacket = NULL;
            if (LOG_DEBUG) {
                LOGD(LOG_TAG, "Pop an avpacket from the queue, %d remaining.", queue.size());
            }
            break;
        } else {
            pthread_cond_wait(&condition, &mutex);
        }
    }

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
