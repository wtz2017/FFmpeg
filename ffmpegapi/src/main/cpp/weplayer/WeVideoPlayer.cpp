//
// Created by WTZ on 2020/1/9.
//

#include "WeVideoPlayer.h"

WeVideoPlayer::WeVideoPlayer(AVPacketQueue *queue, PlayStatus *status,
                             JavaListenerContainer *javaListenerContainer) {
    this->decoder = new WeVideoDecoder(queue);
    this->status = status;
    this->javaListenerContainer = javaListenerContainer;
}

WeVideoPlayer::~WeVideoPlayer() {
    release();
}

WeVideoDecoder *WeVideoPlayer::getDecoder() {
    return decoder;
}

int WeVideoPlayer::init() {
    //TODO OpenGL player
//    if (openSlPlayer == NULL) {
//        openSlPlayer = new OpenSLPlayer(this, AudioStream::SAMPLE_OUT_CHANNEL_LAYOUT);
//    }
//    int ret;
//    if ((ret = openSlPlayer->init()) != NO_ERROR) {
//        LOGE(LOG_TAG, "OpenSLPlayer init failed!");
//        delete openSlPlayer;
//        openSlPlayer = NULL;
//        return ret;
//    }

    createVideoPlayerThread();
    return NO_ERROR;
}

/**
 * 专门用单独线程处理播放行为，因为播放就是在消费数据
 */
void videoPlayMessageHanler(int msgType, void *context) {
    WeVideoPlayer *pPlayer = (WeVideoPlayer *) context;
    pPlayer->_handleVideoPlayMessage(msgType);
}

void WeVideoPlayer::createVideoPlayerThread() {
    if (videoPlayerThread != NULL) {
        return;
    }
    videoPlayerThread = new LooperThread("VideoPlayerThread", this, videoPlayMessageHanler);
    videoPlayerThread->create();
}

void WeVideoPlayer::_handleVideoPlayMessage(int msgType) {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "_handleVideoPlayMessage: msgType=%d", msgType);
    }
    switch (msgType) {
        case VIDEO_CONSUMER_START_PLAY:
            handleStartPlay();
            break;
        case VIDEO_CONSUMER_RESUME_PLAY:
            handleResumePlay();
            break;
    }
}

int WeVideoPlayer::createPlayer() {
    //TODO OpenGL player
//    if (openSlPlayer == NULL) {
//        LOGE(LOG_TAG, "Invoke createPlayer but openSlPlayer is NULL!");
//        return E_CODE_AUD_ILLEGAL_CALL;
//    }
//
//    if (!openSlPlayer->isInitSuccess()) {
//        LOGE(LOG_TAG, "Invoke createPlayer but openSlPlayer did not initialize successfully!");
//        return E_CODE_AUD_ILLEGAL_CALL;
//    }
//
//    // 音频流采样率等参数不一样，就需要重新创建 player
//    int ret;
//    if ((ret = openSlPlayer->createPlayer()) != NO_ERROR) {
//        LOGE(LOG_TAG, "OpenSLPlayer createPlayer failed!");
//        return ret;
//    }

    return NO_ERROR;
}

int WeVideoPlayer::startPlay() {
    if (videoPlayerThread == NULL) {
        LOGE(LOG_TAG, "Invoke startPlay but videoConsumerThread is NULL!");
        return E_CODE_AUD_ILLEGAL_CALL;
    }

    decoder->start();
    videoPlayerThread->sendMessage(VIDEO_CONSUMER_START_PLAY);
    return NO_ERROR;
}

void WeVideoPlayer::handleStartPlay() {
    //TODO OpenGL player
//    if (openSlPlayer == NULL) {
//        LOGE(LOG_TAG, "handleStartPlay but openSlPlayer is NULL!");
//        return;
//    }
//
//    if (!openSlPlayer->isInitSuccess()) {
//        LOGE(LOG_TAG, "handleStartPlay but openSlPlayer did not initialize successfully!");
//        return;
//    }
//
//    openSlPlayer->startPlay();
}

void WeVideoPlayer::pause() {
    //TODO OpenGL player
//    if (openSlPlayer == NULL) {
//        LOGE(LOG_TAG, "Invoke pause but openSlPlayer is NULL!");
//        return;
//    }
//
//    if (!openSlPlayer->isInitSuccess()) {
//        LOGE(LOG_TAG, "Invoke pause but openSlPlayer did not initialize successfully!");
//        return;
//    }
//
//    openSlPlayer->pause();
}

void WeVideoPlayer::resumePlay() {
    if (videoPlayerThread == NULL) {
        LOGE(LOG_TAG, "Invoke resumePlay but videoConsumerThread is NULL!");
        return;
    }

    videoPlayerThread->sendMessage(VIDEO_CONSUMER_RESUME_PLAY);
}

void WeVideoPlayer::handleResumePlay() {
    //TODO OpenGL player
//    if (openSlPlayer == NULL) {
//        LOGE(LOG_TAG, "handleResumePlay but openSlPlayer is NULL!");
//        return;
//    }
//
//    if (!openSlPlayer->isInitSuccess()) {
//        LOGE(LOG_TAG, "handleResumePlay but openSlPlayer did not initialize successfully!");
//        return;
//    }
//
//    openSlPlayer->resumePlay();
}

bool WeVideoPlayer::isPlayComplete() {
    //TODO OpenGL player
//    return (decoder == NULL || decoder->readAllDataComplete()) &&
//           (openSlPlayer == NULL || openSlPlayer->enqueueFinished);
    return (decoder == NULL || decoder->readAllDataComplete());
}

void WeVideoPlayer::stopPlay() {
    //TODO OpenGL player
//    if (openSlPlayer != NULL) {
//        openSlPlayer->stopPlay();// 停止播放回调
//    } else {
//        LOGE(LOG_TAG, "Invoke stopPlay but openSlPlayer is NULL!");
//    }

    decoder->stop();

    if (videoPlayerThread != NULL) {
        videoPlayerThread->clearMessage();// 清除还未执行的播放请求消息
    }
}

void WeVideoPlayer::destroyPlayer() {
    //TODO OpenGL player
//    if (openSlPlayer != NULL) {
//        openSlPlayer->destroyPlayer();
//    } else {
//        LOGE(LOG_TAG, "Invoke destroyPlayer but openSlPlayer is NULL!");
//    }
}

void WeVideoPlayer::clearDataAfterStop() {
    decoder->releaseStream();
}

bool WeVideoPlayer::workFinished() {
    //TODO OpenGL player
//    if (openSlPlayer != NULL) {
//        return openSlPlayer->enqueueFinished;
//    }
    return true;
}

void WeVideoPlayer::release() {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "release...");
    }
    stopPlay();// 首先停止播放，也就停止了消费者从队列里取数据

    //TODO OpenGL player
//    if (openSlPlayer != NULL) {
//        delete openSlPlayer;
//        openSlPlayer = NULL;
//    }

    delete decoder;
    decoder = NULL;

    destroyVideoPlayerThread();

    // 最顶层 WePlayer 负责回收 javaListenerContainer，这里只把本指针置空
    javaListenerContainer = NULL;

    // 最顶层 WePlayer 负责回收 status，这里只把本指针置空
    status == NULL;
}

void WeVideoPlayer::destroyVideoPlayerThread() {
    if (videoPlayerThread != NULL) {
        videoPlayerThread->shutdown();
        delete videoPlayerThread;
        videoPlayerThread = NULL;
    }
}
