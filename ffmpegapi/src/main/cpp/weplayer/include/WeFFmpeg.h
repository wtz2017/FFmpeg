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
 * 解包：单独开启线程；
 * 播放：单独开启线程；
 * 其它调度（如 setDataSource、prepare、pause、resumePlay、release）直接使用 java 层开启的调度线程；
 * java 层的调度线程使用 HandlerThread 实现串行调度，保证调度方法之间不会有线程并发问题；
 * java 层的调度线程使用停止标志位加等待超时方式实现停止 decode 和 play 线程。
 * 其中 setStopFlag 不走 java 调度线程消息队列，直接执行，避免无法立即通知结束工作
 */
class WeFFmpeg {

private:
    JavaListenerContainer *javaListenerContainer = NULL;

    bool initSuccess = false;
    bool prepareFinished = true;
    bool demuxFinished = true;
    bool seekToBegin = false;

    char *dataSource = NULL;
    AVFormatContext *pFormatCtx = NULL;
    WeAudio *weAudio = NULL;
    double duration = 0;// Duration of the stream in seconds

    // 只针对解封装包数据单独用一个线程，其它走调度线程
    LooperThread *demuxThread = NULL;
    pthread_mutex_t demuxMutex;
    static const int MSG_DEMUX_START = 1;

public:
    const char *LOG_TAG = "WeFFmpeg";

    PlayStatus *status = NULL;

public:
    WeFFmpeg(JavaListenerContainer *javaListenerContainer);

    ~WeFFmpeg();

    void reset();

    void setDataSource(char *dataSource);

    /**
     * 为新数据源做准备，或者调用过 stop 后再重新做准备
     */
    void prepareAsync();

    /**
     * 开始解包和播放
     */
    void start();

    void _handleDemuxMessage(int msgType);

    void pause();

    /**
     * Seeks to specified time position
     *
     * @param msec the offset in milliseconds from the start to seek to
     */
    void seekTo(int msec);

    /**
     * 设置音量
     * @param percent 范围是：0 ~ 1.0
     */
    void setVolume(float percent);

    float getVolume();

    /**
     * 设置声道
     *
     * @param channel
     *      CHANNEL_RIGHT = 0;
     *      CHANNEL_LEFT = 1;
     *      CHANNEL_STEREO = 2;
     */
    void setSoundChannel(int channel);

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

    bool isPlaying();

    /**
     * setStopFlag 不走 java 调度线程消息队列，直接执行，避免无法立即通知结束工作
     */
    void setStopFlag();

    /**
     * 具体停止工作，例如：停止播放、关闭打开的文件流
     */
    void stop();

    void release();

private:
    /**
     * 初始化公共资源，例如：libavformat、音频播放器
     */
    void init();

    void handleErrorOnPreparing(int errorCode);

    /**
     * 开启解封装线程
     */
    void createDemuxThread();

    /**
     * 真正解封装的函数
     */
    void demux();

    void destroyDemuxThread();

};


#endif //FFMPEG_WEFFMPEG_H
