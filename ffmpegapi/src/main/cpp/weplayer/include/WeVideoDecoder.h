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
#include "WeAudioDecoder.h"

/* no AV sync correction is done if below the minimum AV sync threshold */
#define AV_SYNC_THRESHOLD_MIN 0.04
/* AV sync correction is done if above the maximum AV sync threshold */
#define AV_SYNC_THRESHOLD_MAX 0.1
/* If a frame duration is longer than this, it will not be duplicated to compensate AV sync */
#define AV_SYNC_FRAMEDUP_THRESHOLD 0.1
/* no AV correction is done if too big error */
#define AV_NOSYNC_THRESHOLD 10.0

#define LOG_TIME_SYNC false // TODO 音视频同步调试日志开关

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

    /* 音视频同步参数 */
    WeAudioDecoder *weAudioDecoder = NULL;
    bool resetFirstFrame = true;// 换源时、恢复播放时、SEEK 时更新为 true
    double pts = 0;// AVFrame 推荐的显示时间，基于 StreamTimeBase 第几刻度
    double frameInterval = 0;// 两帧间隔，单位：秒
    double avgFrameInterval = 0;// 平均两帧间隔，单位：秒
    double lastShowAbsTime = 0;// 上一帧播放时间，绝对时间
    double lastPlayTS = 0;// 上一帧播放时间，第几秒
    double playTS = 0;// 当前帧播放时间，第几秒
    double lastDelay = 0;// 上一次播放视频的两帧间隔时间，单位：秒
    double delay = 0;// 两帧视频间隔时间，单位：秒
    double actualDelay = 0;// 真正需要延迟等待时间，单位：秒

    double currentFrameTime = 0;// 当前帧时间，第几秒
    double clockDiff = 0;// 音频帧与视频帧相差时间
    double syncThreshold = 0;// 判定音视步同步的域值范围
    /* 音视频同步参数 */

public:
    VideoStream *videoStream = NULL;
    long long t1 = 0, t2 = 0, t3 = 0, t4 = 0, t5 = 0;//TODO TEST---Time Consuming---

public:
    WeVideoDecoder(AVPacketQueue *queue);

    ~WeVideoDecoder();

    /**
     *
     * @param videoStream 视频流信息
     * @param decoder 音频解码器，用于音视频同步
     */
    void initStream(VideoStream *videoStream, WeAudioDecoder *decoder);

    void releaseStream();

    void start();

    void stop();

    void setSeekTime(double secs);

    void enableFirstFrame();

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

    double computeFrameDelay_1(AVFrame *avFrame);

    double computeFrameDelay_2(AVFrame *avFrame);

    double computeFrameDelay_3(AVFrame *avFrame);

    double computeFrameDelay_4(AVFrame *avFrame);

    bool initFormatConverter();

    void releaseFormatConverter();

    void releaseAvPacket();

    void releaseAvFrame();

};


#endif //FFMPEG_WEVIDEODECODER_H
