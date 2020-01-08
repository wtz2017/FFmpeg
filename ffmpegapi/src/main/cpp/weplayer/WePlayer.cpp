//
// Created by WTZ on 2019/11/19.
//

#include "WePlayer.h"

WePlayer::WePlayer(JavaListenerContainer *javaListenerContainer) {
    this->javaListenerContainer = javaListenerContainer;
    init();
}

WePlayer::~WePlayer() {
    release();
}

void WePlayer::init() {
    status = new PlayStatus();
    weDemux = new WeDemux();
    weAudioPlayer = new WeAudioPlayer(weDemux->getAudioQueue(), status, javaListenerContainer);

    int ret;
    if ((ret = weAudioPlayer->init()) != NO_ERROR) {
        initSuccess = false;

        pthread_mutex_lock(&status->mutex);
        status->setStatus(PlayStatus::ERROR, LOG_TAG);
        // ！！！注意：这里专门把 java 回调放到锁里，需要 java 层注意不要有其它本地方法调用和耗时操作！！！
        javaListenerContainer->onErrorListener->callback(2, ret, E_NAME_AUDIO_PLAY);
        pthread_mutex_unlock(&status->mutex);
        return;
    }

    createDemuxThread();// 开启解封装线程
    initSuccess = true;
}

void demuxThreadHandler(int msgType, void *context) {
    WePlayer *wePlayer = (WePlayer *) context;
    wePlayer->_handleDemuxMessage(msgType);
}

void WePlayer::createDemuxThread() {
    if (demuxThread != NULL) {
        return;
    }
    demuxThread = new LooperThread("PlayDemuxThread", this, demuxThreadHandler);
    demuxThread->create();
}

void WePlayer::_handleDemuxMessage(int msgType) {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "_handleDemuxMessage: msgType=%d", msgType);
    }
    switch (msgType) {
        case MSG_DEMUX_START:
            demux();
            break;
    }
}

void WePlayer::reset() {
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
    status->setStatus(PlayStatus::IDLE, LOG_TAG);
    pthread_mutex_unlock(&status->mutex);
}

void WePlayer::setDataSource(char *dataSource) {
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

    status->setStatus(PlayStatus::INITIALIZED, LOG_TAG);
    pthread_mutex_unlock(&status->mutex);
}

/**
 * 为新数据源做准备，或者调用过 stop 后再重新做准备
 */
void WePlayer::prepareAsync() {
    prepareFinished = false;

    pthread_mutex_lock(&status->mutex);
    if (status == NULL || (!status->isInitialized() && !status->isStoped())) {
        LOGE(LOG_TAG, "Can't call prepare before Initialized or stoped!");
        // 调用非法，不是不可恢复的内部工作错误，所以不用设置错误状态
        pthread_mutex_unlock(&status->mutex);
        prepareFinished = true;
        return;
    }
    status->setStatus(PlayStatus::PREPARING, LOG_TAG);
    pthread_mutex_unlock(&status->mutex);

    int ret;
    if ((ret = weDemux->prepare()) != NO_ERROR) {
        handleErrorOnPreparing(ret);
        prepareFinished = true;
        return;
    }

    weAudioPlayer->getDecoder()->initStream(weDemux->getAudioStream());

    // 为新数据流创建音频播放器
    if ((ret = weAudioPlayer->createPlayer()) != NO_ERROR) {
        LOGE(LOG_TAG, "createAudioPlayer failed!");
        handleErrorOnPreparing(ret);
        prepareFinished = true;
        return;
    }

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
    status->setStatus(PlayStatus::PREPARED, LOG_TAG);

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "prepare finished to callback java...");
    }
    // 回调初始化准备完成，注意要在 java API 层把回调切换到主线程
    // ！！！注意：这里专门把 java 回调放到锁里，需要 java 层注意不要有其它本地方法调用和耗时操作！！！
    javaListenerContainer->onPreparedListener->callback(1, weDemux->getDataSource());

    pthread_mutex_unlock(&status->mutex);
    prepareFinished = true;
}

void WePlayer::handleErrorOnPreparing(int errorCode) {
    // 出错先释放资源
    if (weAudioPlayer != NULL) {
        weAudioPlayer->getDecoder()->releaseStream();
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
    status->setStatus(PlayStatus::ERROR, LOG_TAG);
    // ！！！注意：这里专门把 java 回调放到锁里，需要 java 层注意不要有其它本地方法调用和耗时操作！！！
    javaListenerContainer->onErrorListener->callback(2, errorCode, E_NAME_PREPARE);
    pthread_mutex_unlock(&status->mutex);
}

/**
 * 开始解包和播放
 */
void WePlayer::start() {
    pthread_mutex_lock(&status->mutex);
    if (status == NULL ||
        (!status->isPrepared() && !status->isPaused() && !status->isCompleted())) {
        LOGE(LOG_TAG, "Invoke start but status is %s", status->getCurrentStatusName());
        // 调用非法，不是不可恢复的内部工作错误，所以不用设置错误状态
        pthread_mutex_unlock(&status->mutex);
        return;
    }

    bool needStartDemux = status->isPrepared() || status->isCompleted();
    status->setStatus(PlayStatus::PLAYING, LOG_TAG);
    if (needStartDemux) {
        if (weDemux->getAudioQueue()->isProductDataComplete()) {
            seekToBegin = true;// 在播放完成后，如果用户没有 seek，就从头开始播放
            weDemux->getAudioQueue()->setProductDataComplete(false);
        }
        int ret;
        if ((ret = weAudioPlayer->startPlay()) != NO_ERROR) {
            LOGE(LOG_TAG, "startAudioPlayer failed!");
            status->setStatus(PlayStatus::ERROR, LOG_TAG);
            // ！！！注意：这里专门把 java 回调放到锁里，需要 java 层注意不要有其它本地方法调用和耗时操作！！！
            javaListenerContainer->onErrorListener->callback(2, ret, E_NAME_AUDIO_PLAY);
            pthread_mutex_unlock(&status->mutex);
            return;
        }
        demuxThread->sendMessage(MSG_DEMUX_START);
    } else {
        weAudioPlayer->resumePlay();// 要先设置播放状态，才能恢复播放
    }
    pthread_mutex_unlock(&status->mutex);
}

void WePlayer::demux() {
    demuxFinished = false;

    if (seekToBegin) {
        seekToBegin = false;
        seekTo(0);
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "Start read AVPacket...");
    }
    // 本线程开始读 AVPacket 包并缓存入队
    int readRet = -1;
    while (status != NULL && !status->isStoped() && !status->isError() && !status->isReleased()) {
        if (status->isSeeking || weDemux->getAudioQueue()->getQueueSize() >= AVPacketQueue::MAX_CACHE_NUM) {
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

            // 等待播放完成后退出，否则造成播放不完整
            int ret = waitPlayComplete();
            if (ret == 0 || ret == -2) {
                // 真正播放完成，或因停止或释放提前退出
                demuxFinished = true;
                return;// 结束 demux 工作
            } else if (ret == -1) {
                LOGW(LOG_TAG, "Break to seek point while waiting play complete");
                weAudioPlayer->resumePlay();// 可能播放器已经因取不到数据停止播放，需要重启播放
                continue;// 回到读包大循环继续读数据
            }
        } // 读完分支
    } // 读包大循环

    weDemux->getAudioQueue()->setProductDataComplete(true);// 提前中断解包
    demuxFinished = true;// 提前中断退出
}

/**
 * 在解封装完成或失败后，等待播放器播放完成处理工作
 *
 * @return 0：真正播放完成；-1：等待播放完成过程中 seek 了，需要重新解封装；-2：因停止或释放提前退出；
 */
int WePlayer::waitPlayComplete() {
    while (status != NULL && !status->isStoped() && !status->isError() && !status->isReleased()) {
        if (!weDemux->getAudioQueue()->isProductDataComplete()) {
            // 等待播放完成过程中 seek 了
            return -1;
        }

        if (!weAudioPlayer->isPlayComplete()) {// 还没有播放完成
            av_usleep(100 * 1000);// 睡眠 100 ms，降低 CPU 使用率
            continue;
        }

        LOGW(LOG_TAG, "Go to call java play complete");
        // 真正播放完成
        pthread_mutex_lock(&status->mutex);
        if (status == NULL || !status->isPlaying()) {
            // 只有“播放中”状态才可以切换到“播放完成”状态
            pthread_mutex_unlock(&status->mutex);
            return 0;
        }
        status->setStatus(PlayStatus::COMPLETED, LOG_TAG);

        // ！！！注意：这里专门把 java 回调放到锁里，需要 java 层注意不要有其它本地方法调用和耗时操作！！！
        javaListenerContainer->onCompletionListener->callback(0);

        pthread_mutex_unlock(&status->mutex);
        return 0;
    }
    return -2;
}

void WePlayer::releaseAvPacket() {
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

void WePlayer::pause() {
    pthread_mutex_lock(&status->mutex);
    if (status == NULL || !status->isPlaying()) {
        LOGE(LOG_TAG, "pause but status is not PLAYING");
        // 调用非法，不是不可恢复的内部工作错误，所以不用设置错误状态
        pthread_mutex_unlock(&status->mutex);
        return;
    }
    status->setStatus(PlayStatus::PAUSED, LOG_TAG);
    pthread_mutex_unlock(&status->mutex);

    weAudioPlayer->pause();
}

/**
 * @param msec the offset in milliseconds from the start to seek to
 */
void WePlayer::seekTo(int msec) {
    if (status == NULL || (!status->isPrepared() && !status->isPlaying()
                           && !status->isPaused() && !status->isCompleted())) {
        LOGE(LOG_TAG, "Can't seek because status is %s", status->getCurrentStatusName());
        return;
    }

    double duration = weDemux->getDurationSecs();
    if (duration <= 0) {
        LOGE(LOG_TAG, "Can't seek because duration <= 0");
        return;
    }

    if (weAudioPlayer == NULL) {
        LOGE(LOG_TAG, "Can't seek because weAudioPlayer == NULL");
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
    weAudioPlayer->getDecoder()->flushCodecBuffers();
    weAudioPlayer->getDecoder()->setSeekTime(targetSeconds);

    // seek
    weDemux->seekTo(targetSeconds);

    status->isSeeking = false;
}

void WePlayer::setVolume(float percent) {
    if (weAudioPlayer == NULL) {
        LOGE(LOG_TAG, "setVolume but weAudio is NULL");
        return;
    }

    weAudioPlayer->setVolume(percent);
}

float WePlayer::getVolume() {
    if (weAudioPlayer == NULL) {
        LOGE(LOG_TAG, "getVolume but weAudio is NULL");
        return 0;
    }

    return weAudioPlayer->getVolume();
}

void WePlayer::setSoundChannel(int channel) {
    if (weAudioPlayer == NULL) {
        LOGE(LOG_TAG, "setSoundChannel but weAudio is NULL");
        return;
    }

    weAudioPlayer->setSoundChannel(channel);
}

void WePlayer::setPitch(float pitch) {
    if (weAudioPlayer == NULL) {
        LOGE(LOG_TAG, "setPitch but weAudio is NULL");
        return;
    }

    weAudioPlayer->getDecoder()->setPitch(pitch);
}

float WePlayer::getPitch() {
    if (weAudioPlayer == NULL) {
        LOGE(LOG_TAG, "getPitch but weAudio is NULL");
        return 0;
    }

    return weAudioPlayer->getDecoder()->getPitch();
}

void WePlayer::setTempo(float tempo) {
    if (weAudioPlayer == NULL) {
        LOGE(LOG_TAG, "setTempo but weAudio is NULL");
        return;
    }

    weAudioPlayer->getDecoder()->setTempo(tempo);
}

float WePlayer::getTempo() {
    if (weAudioPlayer == NULL) {
        LOGE(LOG_TAG, "getTempo but weAudio is NULL");
        return 0;
    }

    return weAudioPlayer->getDecoder()->getTempo();
}

double WePlayer::getSoundDecibels() {
    if (weAudioPlayer == NULL) {
        LOGE(LOG_TAG, "getSoundDecibels but weAudio is NULL");
        return 0;
    }

    return weAudioPlayer->getDecoder()->getSoundDecibels();
}

/**
 * Gets the duration of the file.
 *
 * @return the duration in milliseconds
 */
int WePlayer::getDuration() {
    if (weDemux == NULL) {
        LOGE(LOG_TAG, "getDuration but weDemux is NULL");
        return 0;
    }
    return round(weDemux->getDurationSecs() * 1000);
}

/**
 * Gets the current playback position.
 *
 * @return the current position in milliseconds
 */
int WePlayer::getCurrentPosition() {
    if (weAudioPlayer == NULL) {
        LOGE(LOG_TAG, "getCurrentPosition but weAudio is NULL");
        // 不涉及到控制，不设置错误状态
        return 0;
    }
    return round(weAudioPlayer->getDecoder()->getCurrentTimeSecs() * 1000);
}

int WePlayer::getAudioSampleRate() {
    if (weDemux == NULL || weDemux->getAudioStream() == NULL) {
        LOGE(LOG_TAG, "getAudioSampleRate but weDemux or audioStream is NULL");
        return 0;
    }

    return weDemux->getAudioStream()->sampleRate;
}

int WePlayer::getAudioChannelNums() {
    if (weDemux == NULL || weDemux->getAudioStream() == NULL) {
        LOGE(LOG_TAG, "getAudioChannelNums but weDemux or audioStream is NULL");
        return 0;
    }

    return weDemux->getAudioStream()->channelNums;
}

int WePlayer::getAudioBitsPerSample() {
    if (weDemux == NULL || weDemux->getAudioStream() == NULL) {
        LOGE(LOG_TAG, "getAudioBitsPerSample but weDemux or audioStream is NULL");
        return 0;
    }

    return weDemux->getAudioStream()->bytesPerSample * 8;
}

int WePlayer::getPcmMaxBytesPerCallback() {
    if (weAudioPlayer == NULL) {
        LOGE(LOG_TAG, "getPcmMaxBytesPerCallback but weAudio is NULL");
        return 0;
    }
    return weAudioPlayer->getPcmMaxBytesPerCallback();
}

bool WePlayer::isPlaying() {
    return status != NULL && status->isPlaying();
}

void WePlayer::setRecordPCMFlag(bool record) {
    if (weAudioPlayer == NULL) {
        LOGE(LOG_TAG, "setRecordPCMFlag but weAudio is NULL");
        return;
    }

    weAudioPlayer->setRecordPCMFlag(record);
}

/**
 * setStopFlag 不走 java 调度线程消息队列，直接执行，避免无法立即通知结束工作
 * 这样就会与使用调度线程的方法并发，所以要对部分函数步骤做锁同步
 */
void WePlayer::setStopFlag() {
    pthread_mutex_lock(&status->mutex);
    if (status == NULL || status->isReleased() || status->isStoped()) {
        LOGE(LOG_TAG, "Call setStopFlag but status is already NULL or stopped: %d", status);
        pthread_mutex_unlock(&status->mutex);
        return;
    }
    status->setStatus(PlayStatus::STOPPED, LOG_TAG);
    if (weDemux != NULL) {
        weDemux->setStopFlag();
    }
    pthread_mutex_unlock(&status->mutex);
}

/**
 * 具体停止工作，例如：停止播放、关闭打开的文件流
 */
void WePlayer::stop() {
    pthread_mutex_lock(&status->mutex);
    if (status == NULL || status->isReleased()) {
        LOGE(LOG_TAG, "to allowOperation but status is already RELEASED");
        pthread_mutex_unlock(&status->mutex);
        return;
    }
    // 进一步检查外界是否已经调用了 setStopFlag，否则这里直接设置停止标志
    if (!status->isStoped()) {
        status->setStatus(PlayStatus::STOPPED, LOG_TAG);
    }
    pthread_mutex_unlock(&status->mutex);

    if (weAudioPlayer != NULL) {
        weAudioPlayer->stopPlay();
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "close stream wait other thread finished...");
    }
    // 等待工作线程结束
    int sleepCount = 0;
    while (!prepareFinished || !demuxFinished || (weAudioPlayer != NULL && !weAudioPlayer->workFinished())) {
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
    if (weAudioPlayer != NULL) {
        weAudioPlayer->destroyPlayer();// 不同采样参数数据流使用的 openSlPlayer 不一样，需要销毁新建
        weAudioPlayer->clearDataAfterStop();
    }
}

/**
 * 调用 release 之前，先异步调用 setStopFlag；
 * 因为 release 要与 prepare 保持串行，而 prepare 可能会一直阻塞，先异步调用 setStopFlag 结束 prepare
 */
void WePlayer::release() {
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
    status->setStatus(PlayStatus::RELEASED, LOG_TAG);
    pthread_mutex_unlock(&status->mutex);

    // 开始释放所有资源
    delete weAudioPlayer;
    weAudioPlayer = NULL;

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