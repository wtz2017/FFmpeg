//
// Created by WTZ on 2019/12/22.
//

#ifndef FFMPEG_WEDEMUX_H
#define FFMPEG_WEDEMUX_H

#include "WeError.h"
#include "WeUtils.h"
#include "AudioStream.h"
#include "AVPacketQueue.h"

extern "C"
{
#include "libavformat/avformat.h"
#include "libavutil/time.h"
};

class WeDemux {

private:
    pthread_mutex_t demuxMutex;

    bool stopWork = true;

    char *dataSource = NULL;
    AVFormatContext *pFormatCtx = NULL;
    double duration = 0;// Duration of the stream in seconds

    AVPacketQueue *audioQueue = NULL;
    AudioStream *audioStream = NULL;

public:
    const char *LOG_TAG = "WeDemux";

public:
    WeDemux();

    ~WeDemux();

    AVPacketQueue *getAudioQueue();

    AudioStream *getAudioStream();

    void setDataSource(char *dataSource);

    char *getDataSource();

    void clearDataSource();

    /**
     * 为新数据源做准备，或者调用过 stop 后再重新做准备
     */
    int prepare();

    /**
      * 读取数据包到 AVPacket
      *
      * @return 0 if OK, < 0 on error or end of file
      */
    int readPacket(AVPacket *pkt);

    /**
     * Seeks to specified time position
     *
     * @param targetSeconds the offset in seconds from the start to seek to
     */
    void seekTo(double targetSeconds);

    /**
     * Gets the duration of the file.
     *
     * @return the duration in milliseconds
     */
    double getDurationSecs();

    void setStopFlag();

    bool isStopped();

    void releaseStream();

private:
    void init();

    void release();

};


#endif //FFMPEG_WEDEMUX_H
