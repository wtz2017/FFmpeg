//
// Created by WTZ on 2019/12/24.
//

#ifndef FFMPEG_WEAUDIOEDITOR_H
#define FFMPEG_WEAUDIOEDITOR_H


#include "EditStatus.h"
#include "JavaListenerContainer.h"
#include "WeAudioDecoder.h"
#include "LooperThread.h"
#include "WeError.h"

class WeAudioEditor {

private:
    EditStatus *status = NULL;
    JavaListenerContainer *javaListenerContainer = NULL;
    bool editFinished = true;

    int endTimeMsec = 0; // 编辑结束时间，单位：毫秒

    WeAudioDecoder *decoder = NULL;

    // 只针对取数据单独用一个线程，其它播放控制走调度线程
    LooperThread *audioEditThread = NULL;

public:
    const char *LOG_TAG = "WeAudioEditor";
    static const int AUDIO_EDIT_START = 1;

public:
    WeAudioEditor(AVPacketQueue *queue, EditStatus *status,
            JavaListenerContainer *javaListenerContainer);

    ~WeAudioEditor();

    WeAudioDecoder *getDecoder();

    int init();

    void _handleAudioEditMessage(int msgType);

    /**
     * 只针对取数据单独用一个线程，其它控制走调度线程
     */
    int startEdit(int endTimeMsec);

    int getPcmMaxBytesPerCallback();

    void stopEdit();

    void clearDataAfterStop();

    bool workFinished();

private:
    void createAudioEditThread();

    void handleStartEdit();

    void setCompletionStatus();

    void release();

    void destroyAudioEditThread();

};


#endif //FFMPEG_WEAUDIOEDITOR_H
