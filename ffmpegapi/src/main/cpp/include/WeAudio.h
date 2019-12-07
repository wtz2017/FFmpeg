//
// Created by WTZ on 2019/11/20.
//

#ifndef FFMPEG_WEAUDIO_H
#define FFMPEG_WEAUDIO_H

#include "AVPacketQueue.h"
#include "OpenSLPlayer.h"
#include "OnPlayLoadingListener.h"
#include "JavaListenerContainer.h"
#include "WeError.h"

extern "C"
{
#include "libavcodec/avcodec.h"
#include "libswresample/swresample.h"
};

class WeAudio : public PcmGenerator {

private:
    PlayStatus *status = NULL;
    JavaListenerContainer *javaListenerContainer = NULL;
    bool startFinished = false;// 启动播放器线程工作是否完成

    AVPacket *avPacket = NULL;
    AVFrame *avFrame = NULL;

    uint8_t *sampledBuffer = NULL;
    int sampledSizePerSecond;
    int sampleRate;// 采样率（Hz）与原数据保持一致，从外部传参
    const int64_t SAMPLE_OUT_CHANNEL_LAYOUT = AV_CH_LAYOUT_STEREO;// 声道布局：立体声
    int channelNums;// 由声道布局计算得出声道数
    const AVSampleFormat SAMPLE_OUT_FORMAT = AV_SAMPLE_FMT_S16;// 音频采样格式：有符号 16 位
    int bytesPerSample;// 由采样格式计算得出每个声道每次采样字节数

    OpenSLPlayer *openSlPlayer = NULL;

    double currentFrameTime = 0;// 当前帧时间，单位：秒
    double playTimeSecs = 0;// 当前播放时间，单位：秒

public:
    const char *LOG_TAG = "WeAudio";

    int streamIndex = -1;
    AVCodecParameters *codecParams = NULL;
    AVCodecContext *codecContext = NULL;
    AVRational streamTimeBase;

    AVPacketQueue *queue = NULL;
    pthread_t startPlayThread;

    // 测试用
    const bool TEST_SAMPLE = false;// TODO 纯测试采样开关
    const char *TEST_SAVE_FILE_PATH = "/sdcard/test_sample_data.pcm";
    FILE *testSaveFile = NULL;// 纯测试采样数据保存文件指针

public:
    WeAudio(PlayStatus *status, int sampleRate, JavaListenerContainer *javaListenerContainer);

    ~WeAudio();

    void startPlayer();

    void _startPlayer();

    bool startThreadFinished();

    void pause();

    void resumePlay();

    void stopPlay();

    /**
     * @return 当前播放时间，单位：秒
     */
    double getPlayTimeSecs();

    /**
     * 设置 seek 时间
     * @param secs 目标位置秒数
     */
    void setSeekTime(int secs);

    // 以下是继承 PcmGenerator 要实现的方法
    int getPcmData(void **buf);

    int getChannelNums();

    SLuint32 getOpenSLSampleRate();

    int getBitsPerSample();

    SLuint32 getOpenSLChannelLayout();


private:
    bool decodeQueuePacket();

    /**
     * 重采样
     *
     * @return -1 if failed, or sampled bytes
     */
    int resample();

    void updatePlayTime(int64_t pts, int sampleDataBytes);

    void release();

    void releaseAvPacket();

    void releaseAvFrame();
};


#endif //FFMPEG_WEAUDIO_H
