//
// Created by WTZ on 2019/12/24.
//

#ifndef FFMPEG_WEEDITOR_H
#define FFMPEG_WEEDITOR_H


#include "JavaListenerContainer.h"
#include "WeDemux.h"
#include "WeAudioEditor.h"
#include "LooperThread.h"
#include "EditStatus.h"

class WeEditor {

private:
    JavaListenerContainer *javaListenerContainer = NULL;

    bool initSuccess = false;
    bool prepareFinished = true;
    bool demuxFinished = true;

    WeDemux *weDemux = NULL;
    WeAudioEditor *weAudioEditor = NULL;
    AVPacket *avPacket = NULL;

    int startTimeMsec; // 编辑起始时间，单位：毫秒
    int endTimeMsec; // 编辑结束时间，单位：毫秒

    // 只针对解封装包数据单独用一个线程，其它走调度线程
    LooperThread *demuxThread = NULL;
    static const int MSG_DEMUX_START = 1;

public:
    const char *LOG_TAG = "WeEditor";

    EditStatus *status = NULL;

public:
    WeEditor(JavaListenerContainer *javaListenerContainer);

    ~WeEditor();

    void _handleDemuxMessage(int msgType);

    void reset();

    void setDataSource(char *dataSource);

    /**
     * 为新数据源做准备，或者调用过 stop 后再重新做准备
     */
    void prepareAsync();

    /**
      * 开始编辑
      *
      * @param startTimeMsec 编辑起始时间，单位：毫秒
      * @param endTimeMsec  编辑结束时间，单位：毫秒
      */
    void start(int startTimeMsec, int endTimeMsec);

    /**
     * Gets the duration of the file.
     *
     * @return the duration in milliseconds
     */
    int getDuration();

    /**
     * Gets the current position.
     *
     * @return the current position in milliseconds
     */
    int getCurrentPosition();

    int getAudioSampleRate();

    int getAudioChannelNums();

    int getAudioBitsPerSample();

    int getPcmMaxBytesPerCallback();

    /**
     * setStopFlag 不走 java 调度线程消息队列，直接执行，避免无法立即通知结束工作
     */
    void setStopFlag();

    /**
     * 具体停止工作，例如：停止编辑、关闭打开的文件流
     */
    void stop();

    void release();

private:
    /**
     * 初始化公共资源，例如：libavformat、音频播放器
     */
    void init();

    /**
     * 开启解封装线程
     */
    void createDemuxThread();

    void handleErrorOnPreparing(int errorCode);

    /**
     * 真正解封装的函数
     */
    void demux();

    /**
     * 在解封装完成或失败后，等待播放器播放完成处理工作
     *
     * @return 0：真正播放完成；-1：等待播放完成过程中 seek 了，需要重新解封装；-2：因停止或释放提前退出；
     */
    int waitPlayComplete();

    void releaseAvPacket();

    /**
     * Seeks to specified time position
     *
     * @param msec the offset in milliseconds from the start to seek to
     */
    void seekTo(int msec);

};


#endif //FFMPEG_WEEDITOR_H
