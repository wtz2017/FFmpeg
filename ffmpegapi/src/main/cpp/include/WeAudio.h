//
// Created by WTZ on 2019/11/20.
//

#ifndef FFMPEG_WEAUDIO_H
#define FFMPEG_WEAUDIO_H

#include "AVPacketQueue.h"
#include "OpenSLPlayer.h"
#include "OnPlayLoadingListener.h"
#include "JavaListenerContainer.h"

extern "C"
{
#include "libavcodec/avcodec.h"
#include "libswresample/swresample.h"
};

class WeAudio : public PcmGenerator {

private:
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

public:
    const char *LOG_TAG = "WeAudio";

    PlayStatus *status = NULL;
    JavaListenerContainer *javaListenerContainer = NULL;;

    int streamIndex = -1;
    AVCodecContext *codecContext = NULL;
    AVCodecParameters *codecParams = NULL;

    int duration = 0;
    AVRational streamTimeBase;
    double currentFrameTime = 0;
    double currentPlayTime = 0;// 当前播放时间，单位：秒

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

    void pause();

    void resumePlay();

    void stopPlay();

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

    void release();

    void releaseAvPacket();

    void releaseAvFrame();
};


#endif //FFMPEG_WEAUDIO_H
