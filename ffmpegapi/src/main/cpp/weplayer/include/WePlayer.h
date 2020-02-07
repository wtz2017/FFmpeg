//
// Created by WTZ on 2019/11/19.
//

#ifndef FFMPEG_WEPLAYER_H
#define FFMPEG_WEPLAYER_H

#include "WeDemux.h"
#include "WeAudioPlayer.h"
#include "LooperThread.h"
#include "JavaListenerContainer.h"
#include "AndroidLog.h"
#include "WeVideoPlayer.h"

/**
 * 解包：单独开启线程；
 * 播放：单独开启线程；
 * 其它调度（如 setDataSource、prepare、pause、resumePlay、release）直接使用 java 层开启的调度线程；
 * java 层的调度线程使用 HandlerThread 实现串行调度，保证调度方法之间不会有线程并发问题；
 * java 层的调度线程使用停止标志位加等待超时方式实现停止 decode 和 play 线程。
 * 其中 setStopFlag 不走 java 调度线程消息队列，直接执行，避免无法立即通知结束工作
 */
class WePlayer {

private:
    JavaListenerContainer *javaListenerContainer = NULL;

    bool initSuccess = false;
    bool prepareFinished = true;
    bool demuxFinished = true;
    bool seekToBegin = false;

    WeDemux *weDemux = NULL;
    WeAudioPlayer *weAudioPlayer = NULL;
    WeVideoPlayer *weVideoPlayer = NULL;
    AVPacket *avPacket = NULL;

    // 只针对解封装包数据单独用一个线程，其它走调度线程
    LooperThread *demuxThread = NULL;
    static const int MSG_DEMUX_START = 1;

public:
    const char *LOG_TAG = "WePlayer";

    PlayStatus *status = NULL;

public:
    WePlayer(JavaListenerContainer *javaListenerContainer);

    ~WePlayer();

    void _handleDemuxMessage(int msgType);

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
     * 设置音调
     *
     * @param pitch
     */
    void setPitch(float pitch);

    float getPitch();

    /**
     * 设置音速
     *
     * @param tempo
     */
    void setTempo(float tempo);

    float getTempo();

    /**
     * 获取当前播放声音分贝值，单位：dB
     */
    double getSoundDecibels();

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

    int getAudioSampleRate();

    int getAudioChannelNums();

    int getAudioBitsPerSample();

    int getPcmMaxBytesPerCallback();

    bool isPlaying();

    /**
     * @param record true:录制 PCM
     */
    void setRecordPCMFlag(bool record);

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

    void handleErrorOnInit(int errorCode, char *errorName);

    /**
     * 开启解封装线程
     */
    void createDemuxThread();

    void handleErrorOnPreparing(int errorCode);

    /**
     * 真正解封装的函数
     */
    void demux();

    /**
     * 在解封装完成或失败后，等待播放器播放完成处理工作
     *
     * @return 0：真正播放完成；-1：等待播放完成过程中 seek 了，需要重新解封装；-2：因停止或释放提前退出；
     */
    int waitPlayComplete();

    void releaseAvPacket();

    /**
     * 清除队列和解码器中的缓存数据
     */
    void clearCache();

};


#endif //FFMPEG_WEPLAYER_H
