//
// Created by WTZ on 2019/12/7.
//

#ifndef FFMPEG_AUDIOSTREAM_H
#define FFMPEG_AUDIOSTREAM_H

#include <stdint.h>

extern "C"
{
#include "libavcodec/avcodec.h"
#include "libavutil/channel_layout.h"
#include "libavutil/samplefmt.h"
};

class AudioStream {

public:
    int streamIndex = -1;
    AVCodecParameters *codecParams = NULL;
    AVCodecContext *codecContext = NULL;
    AVRational streamTimeBase;

    int sampledSizePerSecond;// 1 秒的采样字节数
    int sampleRate;// 采样率（Hz）与原数据保持一致，从外部传参
    static const int64_t SAMPLE_OUT_CHANNEL_LAYOUT = AV_CH_LAYOUT_STEREO;// 声道布局：立体声
    int channelNums;// 由声道布局计算得出声道数
    static const AVSampleFormat SAMPLE_OUT_FORMAT = AV_SAMPLE_FMT_S16;// 音频采样格式：有符号 16 位
    int bytesPerSample;// 由采样格式计算得出每个声道每次采样字节数

public:
    AudioStream(int streamIndex, AVCodecParameters *codecParams, AVRational streamTimeBase);

    ~AudioStream();

};


#endif //FFMPEG_AUDIOSTREAM_H
