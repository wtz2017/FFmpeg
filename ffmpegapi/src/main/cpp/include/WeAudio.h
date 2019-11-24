//
// Created by WTZ on 2019/11/20.
//

#ifndef FFMPEG_WEAUDIO_H
#define FFMPEG_WEAUDIO_H

#include "AVPacketQueue.h"

extern "C"
{
#include "libavcodec/avcodec.h"
#include "libswresample/swresample.h"
};

class WeAudio {

private:
    const char *LOG_TAG = "WeAudio";

    AVPacket *avPacket = NULL;
    AVFrame *avFrame = NULL;

    const int SAMPLE_BUFFER_SIZE_BYTES = 44100 * 2 * 2;// 44.1KHZ、立体声道、16 位采样下的 1 秒字节数
    uint8_t *sampledBuffer = NULL;
    const int64_t SAMPLE_OUT_CHANNEL_LAYOUT = AV_CH_LAYOUT_STEREO;// 声道布局：立体声
    const AVSampleFormat SAMPLE_OUT_FORMAT = AV_SAMPLE_FMT_S16;// 音频采样格式：有符号 16 位

public:
    PlayStatus *status = NULL;

    int streamIndex = -1;
    AVCodecContext *codecContext = NULL;
    AVCodecParameters *codecParams = NULL;

    AVPacketQueue *queue = NULL;
    pthread_t playThread;

    const bool TEST_SAMPLE = true;// TODO 纯测试采样开关
    const char *TEST_SAVE_FILE_PATH = "/sdcard/test_sample_data.pcm";
    FILE *testSaveFile = NULL;// 纯测试采样数据保存文件指针

public:
    WeAudio(PlayStatus *status);

    ~WeAudio();

    void play();

    int _play();

private:
    bool decodeQueuePacket();

    /**
     * 重采样
     *
     * @return -1 if failed, or sampled bytes
     */
    int resample();

    void releaseAvPacket();

    void releaseAvFrame();
};


#endif //FFMPEG_WEAUDIO_H
