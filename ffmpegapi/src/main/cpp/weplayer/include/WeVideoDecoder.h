//
// Created by WTZ on 2020/1/9.
//

#ifndef FFMPEG_WEVIDEODECODER_H
#define FFMPEG_WEVIDEODECODER_H

extern "C"
{
#include "libavcodec/avcodec.h"
#include "libswscale/swscale.h"
#include "libavutil/imgutils.h"
#include "libavutil/time.h"
};

#include "VideoStream.h"
#include "AVPacketQueue.h"
#include "WeUtils.h"
#include "OnYUVDataCall.h"

class WeVideoDecoder {

private:
    const char *LOG_TAG = "WeVideoDecoder";

    pthread_mutex_t decodeMutex;

    AVPacketQueue *queue = NULL;

    AVPacket *avPacket = NULL;
    AVFrame *avFrame = NULL;
    bool readAllPacketComplete = true;// 是否读完了所有 AVPacket
    bool readAllFramesComplete = true;// 是否读完了一个 AVPacket 里的所有 AVFrame
    bool parseYUVComplete = true;

    const AVPixelFormat TARGET_AV_PIXEL_FORMAT = AV_PIX_FMT_YUV420P;

    // 转换相关参数
    const int LINE_SIZE_ALIGN = 1;
    AVFrame *convertFrame = NULL;// 声明一个新的 AVFrame 用来接收转换成 YUV420P 的数据
    uint8_t *convertBuffer = NULL;
    SwsContext *swsContext = NULL;

public:
    VideoStream *videoStream = NULL;

public:
    WeVideoDecoder(AVPacketQueue *queue);

    ~WeVideoDecoder();

    void initStream(VideoStream *videoStream);

    void releaseStream();

    void start();

    void stop();

    /**
     * Reset the internal decoder state / flush internal buffers. Should be called
     * e.g. when seeking or when switching to a different stream.
     */
    void flushCodecBuffers();

    /**
     * 从队列中取 AVPacket 解码生成 YUV 数据
     *
     * @return >0：sampled bytes；-1：数据加载中；-2：已经播放到末尾；-3：取包异常；
     * -4：发送解码失败；-5：接收解码数据帧失败；-6：解析 YUV 失败；
     */
    int getYUVData(OnYUVDataCall *onYuvDataCall);

    bool readAllDataComplete();

private:
    /**
     * 从队列中取 AVPacket
     *
     * @return 0：取包成功；-1：数据加载中；-2：已经播放到末尾；-3：取包异常
     */
    int getPacket();

    /**
     * 把 packet 发送给解码器解码
     *
     * @return true:发送解码成功
     */
    bool sendPacket();

    /**
     * 接收解码后的数据帧 frame
     *
     * @return true:接收成功
     */
    bool receiveFrame();

    bool parseYUV(OnYUVDataCall *onYuvDataCall);

    bool initFormatConverter();

    void releaseFormatConverter();

    void releaseAvPacket();

    void releaseAvFrame();

};


#endif //FFMPEG_WEVIDEODECODER_H
