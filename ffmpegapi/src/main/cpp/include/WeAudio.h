//
// Created by WTZ on 2019/11/20.
//

#ifndef FFMPEG_WEAUDIO_H
#define FFMPEG_WEAUDIO_H

#include "AudioStream.h"
#include "AVPacketQueue.h"
#include "OpenSLPlayer.h"
#include "OnPlayLoadingListener.h"
#include "JavaListenerContainer.h"
#include "WeError.h"
#include "LooperThread.h"

extern "C"
{
#include "libavcodec/avcodec.h"
#include "libswresample/swresample.h"
};

class WeAudio : public PcmGenerator {

private:
    PlayStatus *status = NULL;
    JavaListenerContainer *javaListenerContainer = NULL;

    AVPacket *avPacket = NULL;
    AVFrame *avFrame = NULL;

    uint8_t *sampledBuffer = NULL;

    OpenSLPlayer *openSlPlayer = NULL;

    double currentFrameTime = 0;// 当前帧时间，单位：秒
    double playTimeSecs = 0;// 当前播放时间，单位：秒

    // 播放器只针对取数据单独用一个线程，其它播放控制走调度线程
    LooperThread *audioConsumerThread = NULL;

public:
    const char *LOG_TAG = "WeAudio";

    static const int AUDIO_CONSUMER_START_PLAY = 1;
    static const int AUDIO_CONSUMER_RESUME_PLAY = 2;

    AudioStream *audioStream = NULL;
    AVPacketQueue *queue = NULL;

public:
    WeAudio(PlayStatus *status, JavaListenerContainer *javaListenerContainer);

    ~WeAudio();

    int init();

    int createPlayer();

    /**
     * 播放器只针对取数据单独用一个线程，其它播放控制走调度线程，启动播放是取数据
     */
    int startPlay();

    void _handleStartPlay();

    void pause();

    /**
     * 播放器只针对取数据单独用一个线程，其它播放控制走调度线程，恢复播放是取数据
     */
    void resumePlay();

    void _handleResumePlay();

    void stopPlay();

    void destroyPlayer();

    void clearDataAfterStop();

    void releaseStream();

    /**
     * @return 当前播放时间，单位：秒
     */
    double getPlayTimeSecs();

    /**
     * 设置 seek 时间
     * @param secs 目标位置秒数
     */
    void setSeekTime(int secs);

    /**
     * 设置音量
     * @param percent 范围是：0 ~ 1.0
     */
    void setVolume(float percent);

    float getVolume();

    // 以下是继承 PcmGenerator 要实现的方法
    int getPcmData(void **buf);

    int getChannelNums();

    SLuint32 getOpenSLSampleRate();

    int getBitsPerSample();

    SLuint32 getOpenSLChannelLayout();


private:
    void createConsumerThread();

    bool decodeQueuePacket();

    /**
     * 重采样
     *
     * @return -1 if failed, or sampled bytes
     */
    int resample();

    void updatePlayTime(int64_t pts, int sampleDataBytes);

    void release();

    void destroyConsumerThread();

    void releaseAvPacket();

    void releaseAvFrame();
};


#endif //FFMPEG_WEAUDIO_H
