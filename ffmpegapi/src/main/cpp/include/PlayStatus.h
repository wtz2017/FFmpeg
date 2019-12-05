//
// Created by WTZ on 2019/11/21.
//

#ifndef FFMPEG_PLAYSTATUS_H
#define FFMPEG_PLAYSTATUS_H

#include <stddef.h>
#include <pthread.h>
#include "AndroidLog.h"

class PlayStatus {

public:
    enum Status {
        PREPARING, PREPARED, PLAYING, PAUSED, COMPLETED, STOPPED, ERROR
    };

    /**
     * 播放过程中可能因资源获取不顺利导致加载缓慢，如果缓冲队列数据为空，就说明正在播放加载中；
     * 此变量作为大的播放状态的一个补充子状态，只有在 OpenSL 播放中才会回调取数据，从而判断是否在加载中。
     */
    bool isPlayLoading = false;

    bool isSeeking = false;// 不像java一样所有类型的值都有默认的初始值，C/C++一定要手动给变量默认值！

    pthread_mutex_t mutex;

private:
    const char *LOG_TAG = "PlayStatus";

    Status status;

public:
    PlayStatus();

    ~PlayStatus();

    void setStatus(Status status, const char *setterName);

    bool isPreparing();

    bool isPrepared();

    bool isPlaying();

    bool isPaused();

    bool isCompleted();

    bool isStoped();

    bool isError();

    const char *getStatusName(Status status);

    const char *getCurrentStatusName();

};


#endif //FFMPEG_PLAYSTATUS_H
