//
// Created by WTZ on 2019/11/25.
//

#ifndef FFMPEG_OPENSLPLAYER_H
#define FFMPEG_OPENSLPLAYER_H

#include <stddef.h>
#include <stdint.h>
#include "AndroidLog.h"
#include "PcmGenerator.h"

extern "C"
{
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <libavutil/channel_layout.h>
}

// 声道布局定义，用于 FFmpeg 声道布局转换
#define OPENSL_LAYOUT_MONO              (SL_SPEAKER_FRONT_CENTER)
#define OPENSL_LAYOUT_STEREO            (SL_SPEAKER_FRONT_LEFT|SL_SPEAKER_FRONT_RIGHT)
#define OPENSL_LAYOUT_2POINT1           (OPENSL_LAYOUT_STEREO|SL_SPEAKER_LOW_FREQUENCY)
#define OPENSL_LAYOUT_2_1               (OPENSL_LAYOUT_STEREO|SL_SPEAKER_BACK_CENTER)
#define OPENSL_LAYOUT_SURROUND          (OPENSL_LAYOUT_STEREO|SL_SPEAKER_FRONT_CENTER)
#define OPENSL_LAYOUT_3POINT1           (OPENSL_LAYOUT_SURROUND|SL_SPEAKER_LOW_FREQUENCY)
#define OPENSL_LAYOUT_4POINT0           (OPENSL_LAYOUT_SURROUND|SL_SPEAKER_BACK_CENTER)
#define OPENSL_LAYOUT_4POINT1           (OPENSL_LAYOUT_4POINT0|SL_SPEAKER_LOW_FREQUENCY)
#define OPENSL_LAYOUT_2_2               (OPENSL_LAYOUT_STEREO|SL_SPEAKER_SIDE_LEFT|SL_SPEAKER_SIDE_RIGHT)
#define OPENSL_LAYOUT_QUAD              (OPENSL_LAYOUT_STEREO|SL_SPEAKER_BACK_LEFT|SL_SPEAKER_BACK_RIGHT)
#define OPENSL_LAYOUT_5POINT0           (OPENSL_LAYOUT_SURROUND|SL_SPEAKER_SIDE_LEFT|SL_SPEAKER_SIDE_RIGHT)
#define OPENSL_LAYOUT_5POINT1           (OPENSL_LAYOUT_5POINT0|SL_SPEAKER_LOW_FREQUENCY)
#define OPENSL_LAYOUT_5POINT0_BACK      (OPENSL_LAYOUT_SURROUND|SL_SPEAKER_BACK_LEFT|SL_SPEAKER_BACK_RIGHT)
#define OPENSL_LAYOUT_5POINT1_BACK      (OPENSL_LAYOUT_5POINT0_BACK|SL_SPEAKER_LOW_FREQUENCY)
#define OPENSL_LAYOUT_6POINT0           (OPENSL_LAYOUT_5POINT0|SL_SPEAKER_BACK_CENTER)
#define OPENSL_LAYOUT_6POINT0_FRONT     (OPENSL_LAYOUT_2_2|SL_SPEAKER_FRONT_LEFT_OF_CENTER|SL_SPEAKER_FRONT_RIGHT_OF_CENTER)
#define OPENSL_LAYOUT_HEXAGONAL         (OPENSL_LAYOUT_5POINT0_BACK|SL_SPEAKER_BACK_CENTER)
#define OPENSL_LAYOUT_6POINT1           (OPENSL_LAYOUT_5POINT1|SL_SPEAKER_BACK_CENTER)
#define OPENSL_LAYOUT_6POINT1_BACK      (OPENSL_LAYOUT_5POINT1_BACK|SL_SPEAKER_BACK_CENTER)
#define OPENSL_LAYOUT_6POINT1_FRONT     (OPENSL_LAYOUT_6POINT0_FRONT|SL_SPEAKER_LOW_FREQUENCY)
#define OPENSL_LAYOUT_7POINT0           (OPENSL_LAYOUT_5POINT0|SL_SPEAKER_BACK_LEFT|SL_SPEAKER_BACK_RIGHT)
#define OPENSL_LAYOUT_7POINT0_FRONT     (OPENSL_LAYOUT_5POINT0|SL_SPEAKER_FRONT_LEFT_OF_CENTER|SL_SPEAKER_FRONT_RIGHT_OF_CENTER)
#define OPENSL_LAYOUT_7POINT1           (OPENSL_LAYOUT_5POINT1|SL_SPEAKER_BACK_LEFT|SL_SPEAKER_BACK_RIGHT)
#define OPENSL_LAYOUT_7POINT1_WIDE      (OPENSL_LAYOUT_5POINT1|SL_SPEAKER_FRONT_LEFT_OF_CENTER|SL_SPEAKER_FRONT_RIGHT_OF_CENTER)
#define OPENSL_LAYOUT_7POINT1_WIDE_BACK (OPENSL_LAYOUT_5POINT1_BACK|SL_SPEAKER_FRONT_LEFT_OF_CENTER|SL_SPEAKER_FRONT_RIGHT_OF_CENTER)
#define OPENSL_LAYOUT_OCTAGONAL         (OPENSL_LAYOUT_5POINT0|SL_SPEAKER_BACK_LEFT|SL_SPEAKER_BACK_CENTER|SL_SPEAKER_BACK_RIGHT)

class OpenSLPlayer {

public:
    // PCM 数据生成器
    PcmGenerator *pcmGenerator;

    // 播放数据入队 buffer
    void *enqueueBuffer;

    // 播放缓冲队列
    SLAndroidSimpleBufferQueueItf pcmBufferQueue = NULL;

private:
    const char *LOG_TAG = "OpenSLPlayer";

    bool initSuccess = false;

    // 引擎
    SLObjectItf engineObject = NULL;
    SLEngineItf engine = NULL;

    // 混音器
    SLObjectItf outputMixObject = NULL;
    SLEnvironmentalReverbItf outputMixEnvironmentalReverb = NULL;

    // 播放器
    SLObjectItf playerObject = NULL;
    SLPlayItf playController = NULL;
    SLVolumeItf volumeController = NULL;

    // PCM 数据参数
    int channelNums;// 声道数量
    SLuint32 openSLSampleRate;// OpenSL ES 定义的采样率
    int bitsPerSample;// 每个声道每次采样比特位数
    SLuint32 openSLChannelLayout;// OpenSL ES 定义的声道布局


public:
    OpenSLPlayer(PcmGenerator *pcmGenerator);

    ~OpenSLPlayer();

    bool init();

    void startPlay();

    void pause();

    void resumePlay();

    /**
     * 确保在退出应用时销毁所有对象。
     * 对象应按照与创建时相反的顺序销毁，因为销毁具有依赖对象的对象并不安全。
     * 例如，请按照以下顺序销毁：音频播放器和录制器、输出混合，最后是引擎。
     */
    void destroy();

    bool isInitSuccess();

    /**
     * 转换采样率（Hz）为 OpenSL ES 定义的采样率
     * @param sampleRate 待转换的样率（Hz）
     * @return OpenSL ES 定义的采样率
     */
    static SLuint32 convertToOpenSLSampleRate(int sampleRate);

    /**
     * 转换 FFmpeg 定义的声道布局为 OpenSL ES 定义的声道布局
     * @param ffmpegChannelLayout FFmpeg 定义的声道布局
     * @return OpenSL ES 定义的声道布局
     */
    static SLuint32 ffmpegToOpenSLChannelLayout(int64_t ffmpegChannelLayout);

private:
    bool initEngine();

    bool initOutputMix();

    bool setEnvironmentalReverb();

    bool createBufferQueueAudioPlayer();

    bool setBufferQueueCallback(slAndroidSimpleBufferQueueCallback callback, void* pContext);

    bool setPlayState(SLuint32 state);

    /**
     * destroy buffer queue audio player object, and invalidate all associated interfaces
     */
    void destroyBufferQueueAudioPlayer();

    /**
     * destroy output mix object, and invalidate all associated interfaces
     */
    void destroyOutputMixer();

    /**
     * destroy engine object, and invalidate all associated interfaces
     */
    void destroyEngine();

};


#endif //FFMPEG_OPENSLPLAYER_H
