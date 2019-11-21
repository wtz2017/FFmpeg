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

public:
    std::queue<AVPacket *> queue;
    pthread_mutex_t mutex;
    pthread_cond_t condition;
    PlayStatus *status = NULL;

public:
    AVPacketQueue(PlayStatus *status);

    ~AVPacketQueue();

    void putAVpacket(AVPacket *packet);

    void getAVpacket(AVPacket *packet);

    int getQueueSize();

    void clearQueue(std::queue<AVPacket *> &queue);

};


#endif //FFMPEG_AVPACKETQUEUE_H
