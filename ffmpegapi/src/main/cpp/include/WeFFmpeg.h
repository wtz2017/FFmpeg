//
// Created by WTZ on 2019/11/19.
//

#ifndef FFMPEG_WEFFMPEG_H
#define FFMPEG_WEFFMPEG_H

#include <pthread.h>
#include "JavaListener.h"
#include "AndroidLog.h"
#include "WeUtils.h"

extern "C"
{
#include "libavformat/avformat.h"
#include "libavutil/time.h"
};

#include "WeAudio.h"
#include "JavaListenerContainer.h"

/**
 * 解包：开启 decode 线程；
 * 播放：开启 play 线程；
 * 其它调度（如 setDataSource、prepare、pause、resumePlay、release）直接使用 java 层开启的调度线程；
 * java 层的调度线程使用 HandlerThread 实现串行调度，保证调度方法之间不会有线程并发问题；
 * java 层的调度线程使用停止标志位加等待超时方式实现停止 decode 和 play 线程。
 * 其中 setStopFlag 不走 java 调度线程消息队列，直接执行，避免无法立即通知结束工作
 */
class WeFFmpeg {

private:
    JavaListenerContainer *javaListenerContainer = NULL;
    bool workFinished = false;

    char *dataSource = NULL;
    AVFormatContext *pFormatCtx = NULL;
    WeAudio *weAudio = NULL;

    pthread_mutex_t statusMutex;

public:
    const char *LOG_TAG = "WeFFmpeg";

    PlayStatus *status = NULL;

    pthread_t demuxThread;

public:
    WeFFmpeg(JavaListenerContainer *javaListenerContainer);

    ~WeFFmpeg();

    void setDataSource(char *dataSource);

    void prepareAsync();

    /**
     * 开启解封装线程
     */
    void startDemuxThread();

    /**
     * 真正解封装的函数
     */
    void _demux();

    void pause();

    void resumePlay();

    /**
     * Gets the duration of the file.
     *
     * @return the duration in milliseconds
     */
    int getDuration();

    /**
     * Gets the current playback position.
     *
     * @return the current position in milliseconds
     */
    int getCurrentPosition();

    /**
     * setStopFlag 不走 java 调度线程消息队列，直接执行，避免无法立即通知结束工作
     */
    void setStopFlag();

    void release();

private:
    void setErrorOnPreparing();

};


#endif //FFMPEG_WEFFMPEG_H
