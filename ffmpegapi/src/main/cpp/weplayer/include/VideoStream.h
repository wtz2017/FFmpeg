//
// Created by WTZ on 2020/1/9.
//

#ifndef FFMPEG_VIDEOSTREAM_H
#define FFMPEG_VIDEOSTREAM_H


#include <stdint.h>

extern "C"
{
#include "libavcodec/avcodec.h"
};

class VideoStream {

public:
    int streamIndex = -1;
    AVCodecParameters *codecParams = NULL;
    AVCodecContext *codecContext = NULL;
    int width = 0;
    int height = 0;
    AVRational streamTimeBase;
    AVRational avgFrameRate;

public:
    VideoStream(int streamIndex, AVCodecParameters *codecParams, AVRational streamTimeBase,
                AVRational avgFrameRate);

    ~VideoStream();

};


#endif //FFMPEG_VIDEOSTREAM_H
