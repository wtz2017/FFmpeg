//
// Created by WTZ on 2020/1/9.
//

#include "VideoStream.h"

VideoStream::VideoStream(int streamIndex, AVCodecParameters *codecParams, AVRational streamTimeBase,
                         AVRational avgFrameRate) {
    this->streamIndex = streamIndex;
    this->codecParams = codecParams;
    this->streamTimeBase = streamTimeBase;
    this->avgFrameRate = avgFrameRate;
}

VideoStream::~VideoStream() {
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
