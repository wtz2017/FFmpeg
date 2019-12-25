//
// Created by WTZ on 2019/11/20.
//

#ifndef FFMPEG_WEAUDIOPLAYER_H
#define FFMPEG_WEAUDIOPLAYER_H

#include "JavaListenerContainer.h"
#include "WeAudioDecoder.h"
#include "OpenSLPlayer.h"
#include "LooperThread.h"

class WeAudioPlayer : public PcmGenerator {

private:
    PlayStatus *status = NULL;
    JavaListenerContainer *javaListenerContainer = NULL;

    WeAudioDecoder *decoder = NULL;
    OpenSLPlayer *openSlPlayer = NULL;

    bool needRecordPCM = false;// 是否录制 PCM

    // 播放器只针对取数据单独用一个线程，其它播放控制走调度线程
    LooperThread *audioPlayerThread = NULL;

public:
    const char *LOG_TAG = "WeAudioPlayer";
    static const int AUDIO_CONSUMER_START_PLAY = 1;
    static const int AUDIO_CONSUMER_RESUME_PLAY = 2;

public:
    WeAudioPlayer(AVPacketQueue *queue, PlayStatus *status,
                  JavaListenerContainer *javaListenerContainer);

    ~WeAudioPlayer();

    WeAudioDecoder *getDecoder();

    int init();

    void _handleAudioPlayMessage(int msgType);

    int createPlayer();

    /**
     * 播放器只针对取数据单独用一个线程，其它播放控制走调度线程，启动播放是取数据
     */
    int startPlay();

    void pause();

    /**
     * 播放器只针对取数据单独用一个线程，其它播放控制走调度线程，恢复播放是取数据
     */
    void resumePlay();

    bool isPlayComplete();

    void stopPlay();

    void destroyPlayer();

    void clearDataAfterStop();

    bool workFinished();

    /**
     * @param record true:录制 PCM
     */
    void setRecordPCMFlag(bool record);

    /**
     * 设置音量
     * @param percent 范围是：0 ~ 1.0
     */
    void setVolume(float percent);

    float getVolume();

    /**
     * 设置声道
     *
     * @param channel
     *      CHANNEL_RIGHT = 0;
     *      CHANNEL_LEFT = 1;
     *      CHANNEL_STEREO = 2;
     */
    void setSoundChannel(int channel);

    int getPcmMaxBytesPerCallback();

    // 以下是继承 PcmGenerator 要实现的方法
    int getPcmData(void **buf);

    int getChannelNums();

    int getSampleRate();

    int getBitsPerSample();


private:
    void createAudioPlayerThread();

    void handleStartPlay();

    void handleResumePlay();

    void release();

    void destroyAudioPlayerThread();

};


#endif //FFMPEG_WEAUDIOPLAYER_H
