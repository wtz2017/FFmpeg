//
// Created by WTZ on 2019/11/21.
//

#ifndef FFMPEG_AVPACKETQUEUE_H
#define FFMPEG_AVPACKETQUEUE_H

#include "queue"
#include <pthread.h>
#include "PlayStatus.h"
#include "AndroidLog.h"

extern "C"
{
#include "libavcodec/avcodec.h"
};

class AVPacketQueue {

private:
    const char *LOG_TAG = "AVPacketQueue";

    PlayStatus *status = NULL;
    bool productDataComplete = false;

    std::queue<AVPacket *> queue;
    pthread_mutex_t mutex;
    pthread_cond_t condition;

public:
    static const int MAX_CACHE_NUM = 40;

public:
    AVPacketQueue(PlayStatus *status);

    ~AVPacketQueue();

    /**
     * 生产者线程通知是否还有数据可以入队，防止最后消费者线程一直阻塞等待
     */
    void setProductDataComplete(bool complete);

    bool isProductDataComplete();

    void putAVpacket(AVPacket *packet);

    bool getAVpacket(AVPacket *packet);

    int getQueueSize();

    void clearQueue();

private:
    void releaseQueue();

};


#endif //FFMPEG_AVPACKETQUEUE_H
