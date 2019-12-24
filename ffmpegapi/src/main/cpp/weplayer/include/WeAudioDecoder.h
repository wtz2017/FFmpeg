//
// Created by WTZ on 2019/12/23.
//

#ifndef FFMPEG_WEAUDIODECODER_H
#define FFMPEG_WEAUDIODECODER_H

extern "C"
{
#include "libavcodec/avcodec.h"
#include "libswresample/swresample.h"
#include "libavutil/time.h"
};

#include "AudioStream.h"
#include "AVPacketQueue.h"
#include "SoundTouch.h"
#include "WeUtils.h"

using namespace soundtouch;

class WeAudioDecoder {

private:
    const char *LOG_TAG = "WeAudioDecoder";

    pthread_mutex_t decodeMutex;

    AVPacketQueue *queue = NULL;

    AVPacket *avPacket = NULL;
    AVFrame *avFrame = NULL;
    bool readAllPacketComplete = true;// 是否读完了所有 AVPacket
    bool readAllFramesComplete = true;// 是否读完了一个 AVPacket 里的所有 AVFrame

    uint8_t *sampleBuffer = NULL;
    double currentFrameTime = 0;// 当前帧时间，单位：秒
    double sampleTimeSecs = 0;// 当前采样时间，单位：秒

    // 1.需要在 soundtouch/include/STTypes.h 中把 “#define SOUNDTOUCH_INTEGER_SAMPLES 1 ”放开，
    // 同时注释掉 “#define SOUNDTOUCH_FLOAT_SAMPLES 1”
    // 2.需要把 “#define SOUNDTOUCH_ALLOW_MMX 1” 注释掉，否则编译不过
    // 3.为了能在播放中随时交叉调整音调和音速，还需要放开如下宏定义
    // “#define SOUNDTOUCH_PREVENT_CLICK_AT_RATE_CROSSOVER 1”
    SoundTouch *soundTouch = NULL;
    SAMPLETYPE *soundTouchBuffer = NULL;
    static const float NORMAL_PITCH = 1.0f;
    static const float NORMAL_TEMPO = 1.0f;
    float pitch = NORMAL_PITCH;// 音调
    float tempo = NORMAL_TEMPO;// 音速

    double amplitudeAvg = 0;// 当前播放声音振幅平均值，即当前所有 16bit 采样值大小平均值
    double soundDecibels = 0;// 当前播放声音分贝值，单位：dB

public:
    AudioStream *audioStream = NULL;

public:
    WeAudioDecoder(AVPacketQueue *queue);

    ~WeAudioDecoder();

    void initStream(AudioStream *audioStream);

    void releaseStream();

    void start();

    void stop();

    /**
     * Reset the internal decoder state / flush internal buffers. Should be called
     * e.g. when seeking or when switching to a different stream.
     */
    void flushCodecBuffers();

    /**
     * 从队列中取 AVPacket 解码生成 PCM 数据
     *
     * @return >0：sampled bytes；-1：数据加载中；-2：已经播放到末尾；-3：取包异常；
     * -4：发送解码失败；-5：接收解码数据帧失败；-6：重采样失败；-7：调音失败；
     */
    int getPcmData(void **buf);

    bool readAllDataComplete();

    int getChannelNums();

    int getSampleRate();

    int getBitsPerSample();

    int getSampledSizePerSecond();

    /**
     * @return 当前解码时间，单位：秒
     */
    double getCurrentTimeSecs();

    /**
     * 设置 seek 时间
     * @param secs 目标位置秒数
     */
    void setSeekTime(double secs);

    /**
     * 设置音调
     *
     * @param pitch
     */
    void setPitch(float pitch);

    float getPitch();

    /**
     * 设置音速
     *
     * @param tempo
     */
    void setTempo(float tempo);

    float getTempo();

    /**
     * 获取当前播放声音分贝值，单位：dB
     */
    double getSoundDecibels();

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

    /**
     * 重采样
     *
     * @param out 接收采样后的数据 buffer
     * @return -1 if failed, or sampled bytes
     */
    int resample(uint8_t **out);

    void updateTime(int64_t pts, int sampleDataBytes);

    void initSoundTouch();

    /**
     * 调整音调、音速
     *
     * @param in 需要调整的数据 buffer
     * @param inSize 需要调整的数据大小
     * @param out 接收调整后的数据 buffer
     * @return <= 0 if failed, or adjusted bytes
     */
    int adjustPitchTempo(uint8_t *in, int inSize, SAMPLETYPE *out);

    void updatePCM16bitDB(char *data, int dataBytes);

    void releaseAvPacket();

    void releaseAvFrame();

};


#endif //FFMPEG_WEAUDIODECODER_H
