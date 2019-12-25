//
// Created by WTZ on 2019/12/24.
//

#include "WeAudioEditor.h"

WeAudioEditor::WeAudioEditor(AVPacketQueue *queue, EditStatus *status,
                             JavaListenerContainer *javaListenerContainer) {
    this->decoder = new WeAudioDecoder(queue);
    this->status = status;
    this->javaListenerContainer = javaListenerContainer;
}

WeAudioEditor::~WeAudioEditor() {
    release();
}

WeAudioDecoder *WeAudioEditor::getDecoder() {
    return decoder;
}

int WeAudioEditor::init() {
    createAudioEditThread();
    return NO_ERROR;
}

void audioEditMessageHanler(int msgType, void *context) {
    WeAudioEditor *editor = (WeAudioEditor *) context;
    editor->_handleAudioEditMessage(msgType);
}

void WeAudioEditor::createAudioEditThread() {
    if (audioEditThread != NULL) {
        return;
    }
    audioEditThread = new LooperThread("AudioEditThread", this, audioEditMessageHanler);
    audioEditThread->create();
}

void WeAudioEditor::_handleAudioEditMessage(int msgType) {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "_handleAudioEditMessage: msgType=%d", msgType);
    }
    switch (msgType) {
        case AUDIO_EDIT_START:
            handleStartEdit();
            break;
    }
}

int WeAudioEditor::startEdit(int endTimeMsec) {
    if (audioEditThread == NULL) {
        LOGE(LOG_TAG, "Invoke startEdit but audioEditThread is NULL!");
        return E_CODE_AUDE_ILLEGAL_CALL;
    }

    this->endTimeMsec = endTimeMsec;
    decoder->start();
    audioEditThread->sendMessage(AUDIO_EDIT_START);
    return NO_ERROR;
}

void WeAudioEditor::handleStartEdit() {
    editFinished = false;
    int ret = 0;
    void *buf;
    while (status != NULL && status->isEditing()) {
        if (status->isSeeking) {
            av_usleep(100 * 1000);// 睡眠 100 ms，降低 CPU 使用率
            continue;
        }

        if (decoder->getCurrentTimeSecs() * 1000 >= endTimeMsec) {
            // 已到达指定结束时间
            setCompletionStatus();
            break;
        }

        ret = decoder->getPcmData(&buf);
        if (ret > 0) {
            // 成功获取数据
            javaListenerContainer->onPcmDataCall->callback(2, buf, ret);
            continue;
        } else if (ret == -2) {
            // 已经到末尾，退出循环
            setCompletionStatus();
            break;
        } else if (ret == -1) {
            // 数据加载中，等一会儿再来取
            if (!status->isLoading) {
                status->isLoading = true;
                javaListenerContainer->onPlayLoadingListener->callback(1, true);
            }
            av_usleep(100 * 1000);// 睡眠 100 ms，降低 CPU 使用率
            continue;
        } else {
            // 各种原因失败，直接取下一个包，不用等待
            if (status->isLoading) {
                status->isLoading = false;
                javaListenerContainer->onPlayLoadingListener->callback(1, false);
            }
            continue;
        }
    }

    // 这里加一条判断 loading 是为了补充非播放状态时退出 while 循环的场景，同时适用于正常取到数据场景
    if (status->isLoading) {
        status->isLoading = false;
        javaListenerContainer->onPlayLoadingListener->callback(1, false);
    }

    editFinished = true;
}

void WeAudioEditor::setCompletionStatus() {
    pthread_mutex_lock(&status->mutex);
    if (status == NULL || !status->isEditing()) {
        // 只有“编辑中”状态才可以切换到“编辑完成”状态
        pthread_mutex_unlock(&status->mutex);
        return;
    }
    status->setStatus(EditStatus::COMPLETED, LOG_TAG);

    // ！！！注意：这里专门把 java 回调放到锁里，需要 java 层注意不要有其它本地方法调用和耗时操作！！！
    javaListenerContainer->onCompletionListener->callback(0);

    pthread_mutex_unlock(&status->mutex);
}

int WeAudioEditor::getPcmMaxBytesPerCallback() {
    if (decoder == NULL) {
        LOGE(LOG_TAG, "getPcmMaxBytesPerCallback but decoder is NULL");
        return 0;
    }
    return decoder->getSampledSizePerSecond();
}

void WeAudioEditor::stopEdit() {
    decoder->stop();

    if (audioEditThread != NULL) {
        audioEditThread->clearMessage();// 清除还未执行的消息
    }
}

void WeAudioEditor::clearDataAfterStop() {
    decoder->releaseStream();
}

bool WeAudioEditor::workFinished() {
    return editFinished;
}

void WeAudioEditor::release() {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "release...");
    }
    stopEdit();// 首先停止播放，也就停止了消费者从队列里取数据

    delete decoder;
    decoder = NULL;

    destroyAudioEditThread();

    // 最顶层 负责回收 javaListenerContainer，这里只把本指针置空
    javaListenerContainer = NULL;

    // 最顶层 负责回收 status，这里只把本指针置空
    status == NULL;
}

void WeAudioEditor::destroyAudioEditThread() {
    if (audioEditThread != NULL) {
        audioEditThread->shutdown();
        delete audioEditThread;
        audioEditThread = NULL;
    }
}
