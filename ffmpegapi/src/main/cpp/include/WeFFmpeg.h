//
// Created by WTZ on 2019/11/19.
//

#ifndef FFMPEG_WEFFMPEG_H
#define FFMPEG_WEFFMPEG_H

#include <pthread.h>
#include "JavaListener.h"
#include "AndroidLog.h"
#include "WeUtils.h"

extern "C"
{
#include "libavformat/avformat.h"
};

#include "WeAudio.h"
#include "JavaListenerContainer.h"

class WeFFmpeg {

private:
    const char *LOG_TAG = "WeFFmpeg";

    char *dataSource = NULL;
    JavaListenerContainer *javaListenerContainer = NULL;

    AVFormatContext *pFormatCtx = NULL;
    WeAudio *weAudio = NULL;
    PlayStatus *status = NULL;

public:
    pthread_t prepareThread;
    pthread_t decodeThread;

public:
    WeFFmpeg(JavaListenerContainer *javaListenerContainer);

    ~WeFFmpeg();

    void setDataSource(char *dataSource);

    void prepareAsync();

    void _prepareAsync();

    void start();

    void _start();

    void pause();

    void resumePlay();

    /**
     * Gets the duration of the file.
     *
     * @return the duration in milliseconds
     */
    int getDuration();

    /**
     * Gets the current playback position.
     *
     * @return the current position in milliseconds
     */
    int getCurrentPosition();

};


#endif //FFMPEG_WEFFMPEG_H
