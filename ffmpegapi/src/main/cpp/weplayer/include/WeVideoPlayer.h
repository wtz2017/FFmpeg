//
// Created by WTZ on 2020/1/9.
//

#ifndef FFMPEG_WEVIDEOPLAYER_H
#define FFMPEG_WEVIDEOPLAYER_H

#include "JavaListenerContainer.h"
#include "WeVideoDecoder.h"
#include "LooperThread.h"
#include "WeError.h"

class WeVideoPlayer {

private:
    PlayStatus *status = NULL;
    JavaListenerContainer *javaListenerContainer = NULL;

    WeVideoDecoder *decoder = NULL;
    bool playFinished = true;

    // 播放器只针对取数据单独用一个线程，其它播放控制走调度线程
    LooperThread *videoPlayerThread = NULL;

public:
    const char *LOG_TAG = "WeVideoPlayer";
    static const int VIDEO_CONSUMER_START_PLAY = 1;
    static const int VIDEO_CONSUMER_RESUME_PLAY = 2;

public:
    WeVideoPlayer(AVPacketQueue *queue, PlayStatus *status,
                  JavaListenerContainer *javaListenerContainer);

    ~WeVideoPlayer();

    WeVideoDecoder *getDecoder();

    int init();

    void _handleVideoPlayMessage(int msgType);

    int createPlayer();

    /**
     * 播放器只针对取数据单独用一个线程，其它播放控制走调度线程，启动播放是取数据
     */
    int startPlay();

    void pause();

    /**
     * 播放器只针对取数据单独用一个线程，其它播放控制走调度线程，恢复播放是取数据
     */
    void resumePlay();

    bool isPlayComplete();

    void stopPlay();

    void destroyPlayer();

    void clearDataAfterStop();

    bool workFinished();

private:
    void createVideoPlayerThread();

    void handleStartPlay();

    void handleResumePlay();

    void play();

    void release();

    void destroyVideoPlayerThread();

};


#endif //FFMPEG_WEVIDEOPLAYER_H
