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
#include "SoundTouch.h"

extern "C"
{
#include "libavcodec/avcodec.h"
#include "libswresample/swresample.h"
};

using namespace soundtouch;

class WeAudio : public PcmGenerator {

private:
    PlayStatus *status = NULL;
    JavaListenerContainer *javaListenerContainer = NULL;

    AVPacket *avPacket = NULL;
    AVFrame *avFrame = NULL;

    uint8_t *sampleBuffer = NULL;

    // 1.需要在 soundtouch/include/STTypes.h 中把 “#define SOUNDTOUCH_INTEGER_SAMPLES 1 ”放开，
    // 同时注释掉 “#define SOUNDTOUCH_FLOAT_SAMPLES 1”
    // 2.需要把 “#define SOUNDTOUCH_ALLOW_MMX 1” 注释掉，否则编译不过
    // 3.为了能在播放中随时交叉调整音调和音速，还需要放开如下宏定义
    // “#define SOUNDTOUCH_PREVENT_CLICK_AT_RATE_CROSSOVER 1”
    SoundTouch *soundTouch = NULL;
    SAMPLETYPE *soundTouchBuffer = NULL;
    static const float NORMAL_PITCH = 1.0f;
    static const float NORMAL_TEMPO = 1.0f;
    float pitch = NORMAL_PITCH;// 音调
    float tempo = NORMAL_TEMPO;// 音速

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

    bool workFinished();

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
     * @param out 接收采样后的数据 buffer
     * @return -1 if failed, or sampled bytes
     */
    int resample(uint8_t **out);

    void updatePlayTime(int64_t pts, int sampleDataBytes);

    void initSoundTouch();

    /**
     * 调整音调、音速
     *
     * @param in 需要调整的数据 buffer
     * @param inSize 需要调整的数据大小
     * @param out 接收调整后的数据 buffer
     * @return <= 0 if failed, or adjusted bytes
     */
    int adjustPitchTempo(uint8_t *in, int inSize, SAMPLETYPE *out);

    void release();

    void destroyConsumerThread();

    void releaseAvPacket();

    void releaseAvFrame();
};


#endif //FFMPEG_WEAUDIO_H
