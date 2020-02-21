//
// Created by WTZ on 2019/12/24.
//

#include "WeEditor.h"

WeEditor::WeEditor(JavaListenerContainer *javaListenerContainer) {
    this->javaListenerContainer = javaListenerContainer;
    init();
}

WeEditor::~WeEditor() {
    release();
}

void WeEditor::init() {
    status = new EditStatus();
    weDemux = new WeDemux();
    weAudioEditor = new WeAudioEditor(weDemux->getAudioQueue(), status, javaListenerContainer);

    int ret;
    if ((ret = weAudioEditor->init()) != NO_ERROR) {
        initSuccess = false;

        pthread_mutex_lock(&status->mutex);
        status->setStatus(EditStatus::ERROR, LOG_TAG);
        // ！！！注意：这里专门把 java 回调放到锁里，需要 java 层注意不要有其它本地方法调用和耗时操作！！！
        javaListenerContainer->onErrorListener->callback(2, ret, E_NAME_AUDIO_EDIT);
        pthread_mutex_unlock(&status->mutex);
        return;
    }

    createDemuxThread();// 开启解封装线程
    initSuccess = true;
}

void editDemuxThreadHandler(int msgType, void *context) {
    WeEditor *editor = (WeEditor *) context;
    editor->_handleDemuxMessage(msgType);
}

void WeEditor::createDemuxThread() {
    if (demuxThread != NULL) {
        return;
    }
    demuxThread = new LooperThread("EditDemuxThread", this, editDemuxThreadHandler);
    demuxThread->create();
}

void WeEditor::_handleDemuxMessage(int msgType) {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "_handleDemuxMessage: msgType=%d", msgType);
    }
    switch (msgType) {
        case MSG_DEMUX_START:
            demux();
            break;
    }
}

void WeEditor::reset() {
    if (!initSuccess) {// 初始化错误不可恢复，或已经释放
        LOGE(LOG_TAG, "Can't reset because initialization failed or released!");
        return;
    }

    stop();

    if (weDemux != NULL) {
        weDemux->clearDataSource();
    }

    pthread_mutex_lock(&status->mutex);
    if (status == NULL || status->isReleased()) {
        LOGE(LOG_TAG, "to reset but status is already RELEASED");
        pthread_mutex_unlock(&status->mutex);
        return;
    }
    status->setStatus(EditStatus::IDLE, LOG_TAG);
    pthread_mutex_unlock(&status->mutex);
}


void WeEditor::setDataSource(char *dataSource) {
    pthread_mutex_lock(&status->mutex);
    if (status == NULL || !status->isIdle()) {
        LOGE(LOG_TAG, "Can't call setDataSource because status is not IDLE!");
        // 调用非法，不是不可恢复的内部工作错误，所以不用设置错误状态
        pthread_mutex_unlock(&status->mutex);
        // 这里不再判空，因为 JNI 层已经做了判空
        delete[] dataSource;// dataSource 是 JNI 中通过 new char[] 创建的
        return;
    }

    weDemux->setDataSource(dataSource);

    status->setStatus(EditStatus::INITIALIZED, LOG_TAG);
    pthread_mutex_unlock(&status->mutex);
}

void WeEditor::prepareAsync() {
    prepareFinished = false;

    pthread_mutex_lock(&status->mutex);
    if (status == NULL || (!status->isInitialized() && !status->isStoped())) {
        LOGE(LOG_TAG, "Can't call prepare before Initialized or stoped!");
        // 调用非法，不是不可恢复的内部工作错误，所以不用设置错误状态
        pthread_mutex_unlock(&status->mutex);
        prepareFinished = true;
        return;
    }
    status->setStatus(EditStatus::PREPARING, LOG_TAG);
    pthread_mutex_unlock(&status->mutex);

    int ret;
    if ((ret = weDemux->prepare()) != NO_ERROR) {
        handleErrorOnPreparing(ret);
        prepareFinished = true;
        return;
    }

    weAudioEditor->getDecoder()->initStream(weDemux->getAudioStream());

    // 设置数据生产未完成
    weDemux->getAudioQueue()->setProductDataComplete(false);

    // 状态确认需要加锁同步，判断在准备期间是否已经被停止
    pthread_mutex_lock(&status->mutex);
    if (status == NULL || !status->isPreparing()) {
        // 只有“准备中”状态可以切换到“准备好”
        LOGE(LOG_TAG, "prepare finished but status isn't PREPARING");
        pthread_mutex_unlock(&status->mutex);
        prepareFinished = true;
        return;
    }
    status->setStatus(EditStatus::PREPARED, LOG_TAG);

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "prepare finished to callback java...");
    }
    // 回调初始化准备完成，注意要在 java API 层把回调切换到主线程
    // ！！！注意：这里专门把 java 回调放到锁里，需要 java 层注意不要有其它本地方法调用和耗时操作！！！
    javaListenerContainer->onPreparedListener->callback(3, weDemux->getDataSource(),
                                                        getVideoWidth(), getVideoHeight());

    pthread_mutex_unlock(&status->mutex);
    prepareFinished = true;
}

void WeEditor::handleErrorOnPreparing(int errorCode) {
    // 出错先释放资源
    if (weAudioEditor != NULL) {
        weAudioEditor->getDecoder()->releaseStream();
    }
    if (weDemux != NULL) {
        weDemux->releaseStream();
    }

    // 再设置出错状态
    pthread_mutex_lock(&status->mutex);
    if (status == NULL || !status->isPreparing()) {
        pthread_mutex_unlock(&status->mutex);
        return;
    }
    status->setStatus(EditStatus::ERROR, LOG_TAG);
    // ！！！注意：这里专门把 java 回调放到锁里，需要 java 层注意不要有其它本地方法调用和耗时操作！！！
    javaListenerContainer->onErrorListener->callback(2, errorCode, E_NAME_PREPARE);
    pthread_mutex_unlock(&status->mutex);
}

/**
  * 开始编辑
  *
  * @param startTimeMsec 编辑起始时间，单位：毫秒
  * @param endTimeMsec  编辑结束时间，单位：毫秒
  */
void WeEditor::start(int startTimeMsec, int endTimeMsec) {
    pthread_mutex_lock(&status->mutex);
    if (status == NULL ||
        (!status->isPrepared() && !status->isCompleted())) {
        LOGE(LOG_TAG, "Invoke start but status is %s", status->getCurrentStatusName());
        // 调用非法，不是不可恢复的内部工作错误，所以不用设置错误状态
        pthread_mutex_unlock(&status->mutex);
        return;
    }

    if (startTimeMsec < 0 || endTimeMsec > weDemux->getDurationSecs() * 1000 ||
        startTimeMsec >= endTimeMsec) {
        LOGE(LOG_TAG, "Invoke start but time range is invalid: %d ~ %d", startTimeMsec,
             endTimeMsec);
        // 调用非法，不是不可恢复的内部工作错误，所以不用设置错误状态
        pthread_mutex_unlock(&status->mutex);
        return;
    }

    status->setStatus(EditStatus::EDITING, LOG_TAG);

    this->startTimeMsec = startTimeMsec;
    this->endTimeMsec = endTimeMsec;

    if (weDemux->getAudioQueue()->isProductDataComplete()) {
        weDemux->getAudioQueue()->setProductDataComplete(false);
    }

    int ret;
    if ((ret = weAudioEditor->startEdit(endTimeMsec)) != NO_ERROR) {
        LOGE(LOG_TAG, "startEdit failed!");
        status->setStatus(EditStatus::ERROR, LOG_TAG);
        // ！！！注意：这里专门把 java 回调放到锁里，需要 java 层注意不要有其它本地方法调用和耗时操作！！！
        javaListenerContainer->onErrorListener->callback(2, ret, E_NAME_AUDIO_EDIT);
        pthread_mutex_unlock(&status->mutex);
        return;
    }
    demuxThread->sendMessage(MSG_DEMUX_START);

    pthread_mutex_unlock(&status->mutex);
}

void WeEditor::demux() {
    demuxFinished = false;

    seekTo(startTimeMsec);

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "Start read AVPacket...");
    }
    // 本线程开始读 AVPacket 包并缓存入队
    int readRet = -1;
    while (status != NULL && !status->isCompleted() && !status->isStoped() && !status->isError() &&
           !status->isReleased()) {
        if (weDemux->getAudioQueue()->getQueueSize() >= AVPacketQueue::MAX_CACHE_NUM) {
            av_usleep(100 * 1000);// 睡眠 100 ms，降低 CPU 使用率
            continue;
        }

        avPacket = av_packet_alloc();// Allocate an AVPacket
        readRet = weDemux->readPacket(avPacket);// 读取数据包到 AVPacket
        if (readRet == 0) {
            // 读包成功
            if (avPacket->stream_index == weDemux->getAudioStream()->streamIndex) {
                // 当前包为音频包，缓存音频包到队列
                weDemux->getAudioQueue()->putAVpacket(avPacket);
            } else {
                // 不是音频就释放内存
                releaseAvPacket();
            }
        } else {
            // 数据读取已经到末尾 或 出错了
            weDemux->getAudioQueue()->setProductDataComplete(true);
            releaseAvPacket();
            demuxFinished = true;
            return;// 结束 demux 工作
        } // 读完分支
    } // 读包大循环

    weDemux->getAudioQueue()->setProductDataComplete(true);// 提前中断解包
    demuxFinished = true;// 提前中断退出
}

void WeEditor::releaseAvPacket() {
    if (avPacket == NULL) {
        return;
    }
    // 减少 avPacket 对 packet 数据的引用计数
    av_packet_free(&avPacket);
    // 释放 avPacket 结构体本身
    // av_free(avPacket);
    av_freep(&avPacket);// 使用 av_freep(&buf) 代替 av_free(buf)
    avPacket = NULL;
}

void WeEditor::seekTo(int msec) {
    if (status == NULL ||
        (!status->isPrepared() && !status->isEditing() && !status->isCompleted())) {
        LOGE(LOG_TAG, "Can't seek because status is %s", status->getCurrentStatusName());
        return;
    }

    double duration = weDemux->getDurationSecs();
    if (duration <= 0) {
        LOGE(LOG_TAG, "Can't seek because duration <= 0");
        return;
    }

    if (weAudioEditor == NULL) {
        LOGE(LOG_TAG, "Can't seek because weAudioEditor == NULL");
        return;
    }

    double targetSeconds = round(((double) msec) / 1000);
    if (targetSeconds < 0) {
        targetSeconds = 0;
    } else if (targetSeconds > duration) {
        targetSeconds = duration;
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "seekTo %d ms i.e. %lf seconds", msec, targetSeconds);
    }

    status->isSeeking = true;

    // reset
    weDemux->getAudioQueue()->setProductDataComplete(false);
    weDemux->getAudioQueue()->clearQueue();
    weAudioEditor->getDecoder()->flushCodecBuffers();
    weAudioEditor->getDecoder()->setSeekTime(targetSeconds);

    // seek
    weDemux->seekTo(targetSeconds);

    status->isSeeking = false;
}

int WeEditor::getDuration() {
    if (weDemux == NULL) {
        LOGE(LOG_TAG, "getDuration but weDemux is NULL");
        return 0;
    }
    return round(weDemux->getDurationSecs() * 1000);
}

int WeEditor::getCurrentPosition() {
    if (weAudioEditor == NULL) {
        LOGE(LOG_TAG, "getCurrentPosition but weAudioEditor is NULL");
        // 不涉及到控制，不设置错误状态
        return 0;
    }
    return round(weAudioEditor->getDecoder()->getCurrentTimeSecs() * 1000);
}

int WeEditor::getAudioSampleRate() {
    if (weDemux == NULL || weDemux->getAudioStream() == NULL) {
        LOGE(LOG_TAG, "getAudioSampleRate but weDemux or audioStream is NULL");
        return 0;
    }

    return weDemux->getAudioStream()->sampleRate;
}

int WeEditor::getAudioChannelNums() {
    if (weDemux == NULL || weDemux->getAudioStream() == NULL) {
        LOGE(LOG_TAG, "getAudioChannelNums but weDemux or audioStream is NULL");
        return 0;
    }

    return weDemux->getAudioStream()->channelNums;
}

int WeEditor::getAudioBitsPerSample() {
    if (weDemux == NULL || weDemux->getAudioStream() == NULL) {
        LOGE(LOG_TAG, "getAudioBitsPerSample but weDemux or audioStream is NULL");
        return 0;
    }

    return weDemux->getAudioStream()->bytesPerSample * 8;
}

int WeEditor::getPcmMaxBytesPerCallback() {
    if (weAudioEditor == NULL) {
        LOGE(LOG_TAG, "getPcmMaxBytesPerCallback but weAudioEditor is NULL");
        return 0;
    }
    return weAudioEditor->getPcmMaxBytesPerCallback();
}

int WeEditor::getVideoWidth() {
    if (weDemux == NULL || weDemux->getVideoStream() == NULL) {
        return 0;
    }
    return weDemux->getVideoStream()->width;
}

int WeEditor::getVideoHeight() {
    if (weDemux == NULL || weDemux->getVideoStream() == NULL) {
        return 0;
    }
    return weDemux->getVideoStream()->height;
}

void WeEditor::setStopFlag() {
    pthread_mutex_lock(&status->mutex);
    if (status == NULL || status->isReleased() || status->isStoped()) {
        LOGE(LOG_TAG, "Call setStopFlag but status is already NULL or stopped: %d", status);
        pthread_mutex_unlock(&status->mutex);
        return;
    }
    status->setStatus(EditStatus::STOPPED, LOG_TAG);
    if (weDemux != NULL) {
        weDemux->setStopFlag();
    }
    pthread_mutex_unlock(&status->mutex);
}

void WeEditor::stop() {
    pthread_mutex_lock(&status->mutex);
    if (status == NULL || status->isReleased()) {
        LOGE(LOG_TAG, "to allowOperation but status is already RELEASED");
        pthread_mutex_unlock(&status->mutex);
        return;
    }
    // 进一步检查外界是否已经调用了 setStopFlag，否则这里直接设置停止标志
    if (!status->isStoped()) {
        status->setStatus(EditStatus::STOPPED, LOG_TAG);
    }
    pthread_mutex_unlock(&status->mutex);

    if (weAudioEditor != NULL) {
        weAudioEditor->stopEdit();
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "close stream wait other thread finished...");
    }
    // 等待工作线程结束
    int sleepCount = 0;
    while (!prepareFinished || !demuxFinished ||
           (weAudioEditor != NULL && !weAudioEditor->workFinished())) {
        if (sleepCount > 300) {
            break;
        }
        sleepCount++;
        av_usleep(10 * 1000);// 每次睡眠 10 ms
    }
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "close stream wait end after sleep %d ms, start close...", sleepCount * 10);
    }

    if (weDemux != NULL) {
        weDemux->releaseStream();
    }
    if (weAudioEditor != NULL) {
        weAudioEditor->clearDataAfterStop();
    }
}

void WeEditor::release() {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "Start release...");
    }
    stop();

    initSuccess = false;

    pthread_mutex_lock(&status->mutex);
    if (status == NULL || status->isReleased()) {
        LOGE(LOG_TAG, "to release but status is already NULL or RELEASED");
        pthread_mutex_unlock(&status->mutex);
        return;
    }
    status->setStatus(EditStatus::RELEASED, LOG_TAG);
    pthread_mutex_unlock(&status->mutex);

    // 开始释放所有资源
    delete weAudioEditor;
    weAudioEditor = NULL;

    delete weDemux;
    weDemux = NULL;

    if (demuxThread != NULL) {
        demuxThread->shutdown();
        delete demuxThread;
        demuxThread = NULL;
    }

    // 最顶层负责回收 javaListenerContainer
    delete javaListenerContainer;
    javaListenerContainer = NULL;

    // 最顶层负责回收 status
    delete status;
    status = NULL;

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "release finished");
    }
}
