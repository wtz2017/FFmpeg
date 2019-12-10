//
// Created by WTZ on 2019/12/7.
//

#include "include/AudioStream.h"

AudioStream::AudioStream(int streamIndex, AVCodecParameters *codecParams,
                         AVRational streamTimeBase) {
    this->streamIndex = streamIndex;
    this->codecParams = codecParams;
    this->streamTimeBase = streamTimeBase;

    // 初始化采样参数
    this->sampleRate = codecParams->sample_rate;
    this->channelNums = av_get_channel_layout_nb_channels(SAMPLE_OUT_CHANNEL_LAYOUT);
    this->bytesPerSample = av_get_bytes_per_sample(SAMPLE_OUT_FORMAT);
    this->sampledSizePerSecond = sampleRate * channelNums * bytesPerSample;
}

AudioStream::~AudioStream() {
    // codecContext 是调用 avcodec_alloc_context3 创建的，需要使用对应函数关闭释放
    if (codecContext != NULL) {
        avcodec_close(codecContext);
        avcodec_free_context(&codecContext);
        codecContext = NULL;
    }

    // 是直接通过 pFormatCtx->streams[i]->codecpar 赋值的，只需把本指针清空即可
    if (codecParams != NULL) {
        codecParams = NULL;
    }
}
