//
// Created by WTZ on 2019/11/19.
//

#ifndef FFMPEG_WEFFMPEG_H
#define FFMPEG_WEFFMPEG_H

#include <pthread.h>
#include "JavaListener.h"

extern "C"
{
#include "libavformat/avformat.h"
};

#include "WeAudio.h"

class WeFFmpeg {

private:
    const char *LOG_TAG = "WeFFmpeg";
    char *dataSource = NULL;
    JavaListener *preparedListener = NULL;
    AVFormatContext *pFormatCtx = NULL;
    WeAudio *weAudio = NULL;

public:
    pthread_t prepareThread;
    pthread_t decodeThread;

public:
    WeFFmpeg(JavaListener *javaListener);

    ~WeFFmpeg();

    void setDataSource(char *dataSource);

    void prepareAsync();

    void _prepareAsync();

    void start();

    void _start();
};


#endif //FFMPEG_WEFFMPEG_H
