//
// Created by WTZ on 2020/1/9.
//

#include "WeVideoPlayer.h"

WeVideoPlayer::WeVideoPlayer(AVPacketQueue *queue, PlayStatus *status,
                             JavaListenerContainer *javaListenerContainer) {
    this->decoder = new WeVideoDecoder(queue, javaListenerContainer);
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
    // do nothing
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
    play();
}

void WeVideoPlayer::pause() {
    // do nothing, 直接靠设置 PlayStatus 即可
}

void WeVideoPlayer::resumePlay() {
    if (videoPlayerThread == NULL) {
        LOGE(LOG_TAG, "Invoke resumePlay but videoConsumerThread is NULL!");
        return;
    }

    videoPlayerThread->sendMessage(VIDEO_CONSUMER_RESUME_PLAY);
}

void WeVideoPlayer::handleResumePlay() {
    decoder->enableFirstFrame();
    play();
}

void WeVideoPlayer::play() {
    playFinished = false;
    int ret;
    while (status != NULL && status->isPlaying()) {
        if (status->isSeeking) {
            av_usleep(100 * 1000);// 睡眠 100 ms，降低 CPU 使用率
            decoder->enableFirstFrame();
            continue;
        }

        if (decoder->isSupportHardCodec()) {
            // 硬解码
            ret = decoder->getVideoPacket();
        } else {
            // 软解码
            if (LOG_TIME_SYNC) {
                decoder->t1 = decoder->t2 = decoder->t3 = decoder->t4 = decoder->t5 = 0;
            }
            ret = decoder->getYUVData();
            if (LOG_TIME_SYNC) {
                LOGE(LOG_TAG,
                     "getYUVData ret=%d; getPacket=%d; sendPacket=%d; receiveFrame=%d; parseYUV=%d",
                     ret, decoder->t2 - decoder->t1, decoder->t3 - decoder->t2,
                     decoder->t4 - decoder->t3,
                     decoder->t5 - decoder->t4);
            }
        }

        if (ret == 0) {
            // 成功获取数据
            if (status->isPlayLoading) {
                status->setLoading(false);
            }
            continue;
        } else if (ret == -1) {
            // 数据加载中，等一会儿再来取
            if (!status->isPlayLoading) {
                status->setLoading(true);
            }
            av_usleep(100 * 1000);// 睡眠 100 ms，降低 CPU 使用率
            decoder->enableFirstFrame();
            continue;
        } else if (ret == -2) {
            // 已经播放到末尾，直接退出循环
            break;
        } else {
            // 各种原因失败，直接取下一个包，不用等待
            if (status->isPlayLoading) {
                status->setLoading(false);
            }
            continue;
        }
    }
    // 这里加一条判断 loading 是为了补充非播放状态时退出 while 循环的场景，同时适用于正常取到数据场景
    if (status->isPlayLoading) {
        status->setLoading(false);
    }
    playFinished = true;
}

bool WeVideoPlayer::isPlayComplete() {
    return (decoder == NULL || decoder->readAllDataComplete()) && playFinished;
}

void WeVideoPlayer::stopPlay() {
    decoder->stop();

    if (videoPlayerThread != NULL) {
        videoPlayerThread->clearMessage();// 清除还未执行的播放请求消息
    }
}

void WeVideoPlayer::destroyPlayer() {
    // do nothing
}

void WeVideoPlayer::clearDataAfterStop() {
    decoder->releaseStream();
}

bool WeVideoPlayer::workFinished() {
    return playFinished;
}

void WeVideoPlayer::release() {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "release...");
    }
    stopPlay();// 首先停止播放，也就停止了消费者从队列里取数据

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
