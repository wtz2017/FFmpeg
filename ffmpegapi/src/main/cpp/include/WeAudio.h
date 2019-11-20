//
// Created by WTZ on 2019/11/20.
//

#ifndef FFMPEG_WEAUDIO_H
#define FFMPEG_WEAUDIO_H

extern "C"
{
#include "libavcodec/avcodec.h"
};

class WeAudio {

public:
    int streamIndex = -1;
    AVCodecContext *codecContext = NULL;
    AVCodecParameters *codecParams = NULL;

public:
    WeAudio() {};

    ~WeAudio() {};

};


#endif //FFMPEG_WEAUDIO_H
