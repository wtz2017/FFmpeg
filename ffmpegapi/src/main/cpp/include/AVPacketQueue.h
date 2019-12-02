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

    std::queue<AVPacket *> queue;
    pthread_mutex_t mutex;
    pthread_cond_t condition;

public:
    AVPacketQueue(PlayStatus *status);

    ~AVPacketQueue();

    void putAVpacket(AVPacket *packet);

    bool getAVpacket(AVPacket *packet);

    /**
     * 生产者线程通知已经没有数据可以入队了，防止最后消费者线程一直阻塞等待
     */
    void informPutFinished();

    int getQueueSize();

    void clearQueue();

};


#endif //FFMPEG_AVPACKETQUEUE_H
