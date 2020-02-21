//
// Created by WTZ on 2019/11/21.
//

#include "WeAudioPlayer.h"

WeAudioPlayer::WeAudioPlayer(AVPacketQueue *queue, PlayStatus *status,
                             JavaListenerContainer *javaListenerContainer) {
    this->decoder = new WeAudioDecoder(queue);
    this->status = status;
    this->javaListenerContainer = javaListenerContainer;
}

WeAudioPlayer::~WeAudioPlayer() {
    release();
}

WeAudioDecoder *WeAudioPlayer::getDecoder() {
    return decoder;
}

int WeAudioPlayer::init() {
    if (openSlPlayer == NULL) {
        openSlPlayer = new OpenSLPlayer(this, AudioStream::SAMPLE_OUT_CHANNEL_LAYOUT);
    }
    int ret;
    if ((ret = openSlPlayer->init()) != NO_ERROR) {
        LOGE(LOG_TAG, "OpenSLPlayer init failed!");
        delete openSlPlayer;
        openSlPlayer = NULL;
        return ret;
    }

    createAudioPlayerThread();
    return NO_ERROR;
}

/**
 * 专门用单独线程处理播放行为，因为播放就是在消费数据
 */
void audioPlayMessageHanler(int msgType, void *context) {
    WeAudioPlayer *weAudio = (WeAudioPlayer *) context;
    weAudio->_handleAudioPlayMessage(msgType);
}

void WeAudioPlayer::createAudioPlayerThread() {
    if (audioPlayerThread != NULL) {
        return;
    }
    audioPlayerThread = new LooperThread("AudioPlayerThread", this, audioPlayMessageHanler);
    audioPlayerThread->create();
}

void WeAudioPlayer::_handleAudioPlayMessage(int msgType) {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "_handleAudioPlayMessage: msgType=%d", msgType);
    }
    switch (msgType) {
        case WeAudioPlayer::AUDIO_CONSUMER_START_PLAY:
            handleStartPlay();
            break;
        case WeAudioPlayer::AUDIO_CONSUMER_RESUME_PLAY:
            handleResumePlay();
            break;
    }
}

int WeAudioPlayer::createPlayer() {
    if (openSlPlayer == NULL) {
        LOGE(LOG_TAG, "Invoke createPlayer but openSlPlayer is NULL!");
        return E_CODE_AUD_ILLEGAL_CALL;
    }

    if (!openSlPlayer->isInitSuccess()) {
        LOGE(LOG_TAG, "Invoke createPlayer but openSlPlayer did not initialize successfully!");
        return E_CODE_AUD_ILLEGAL_CALL;
    }

    // 音频流采样率等参数不一样，就需要重新创建 player
    int ret;
    if ((ret = openSlPlayer->createPlayer()) != NO_ERROR) {
        LOGE(LOG_TAG, "OpenSLPlayer createPlayer failed!");
        return ret;
    }

    return NO_ERROR;
}

int WeAudioPlayer::startPlay() {
    if (audioPlayerThread == NULL) {
        LOGE(LOG_TAG, "Invoke startPlay but audioConsumerThread is NULL!");
        return E_CODE_AUD_ILLEGAL_CALL;
    }

    decoder->start();
    audioPlayerThread->sendMessage(AUDIO_CONSUMER_START_PLAY);
    return NO_ERROR;
}

void WeAudioPlayer::handleStartPlay() {
    if (openSlPlayer == NULL) {
        LOGE(LOG_TAG, "handleStartPlay but openSlPlayer is NULL!");
        return;
    }

    if (!openSlPlayer->isInitSuccess()) {
        LOGE(LOG_TAG, "handleStartPlay but openSlPlayer did not initialize successfully!");
        return;
    }

    openSlPlayer->startPlay();
}

void WeAudioPlayer::pause() {
    if (openSlPlayer == NULL) {
        LOGE(LOG_TAG, "Invoke pause but openSlPlayer is NULL!");
        return;
    }

    if (!openSlPlayer->isInitSuccess()) {
        LOGE(LOG_TAG, "Invoke pause but openSlPlayer did not initialize successfully!");
        return;
    }

    openSlPlayer->pause();
}

void WeAudioPlayer::resumePlay() {
    if (audioPlayerThread == NULL) {
        LOGE(LOG_TAG, "Invoke resumePlay but audioConsumerThread is NULL!");
        return;
    }

    audioPlayerThread->sendMessage(AUDIO_CONSUMER_RESUME_PLAY);
}

void WeAudioPlayer::handleResumePlay() {
    if (openSlPlayer == NULL) {
        LOGE(LOG_TAG, "handleResumePlay but openSlPlayer is NULL!");
        return;
    }

    if (!openSlPlayer->isInitSuccess()) {
        LOGE(LOG_TAG, "handleResumePlay but openSlPlayer did not initialize successfully!");
        return;
    }

    openSlPlayer->resumePlay();
}

bool WeAudioPlayer::isPlayComplete() {
    return (decoder == NULL || decoder->readAllDataComplete()) &&
           (openSlPlayer == NULL || openSlPlayer->enqueueFinished);
}

void WeAudioPlayer::stopPlay() {
    if (openSlPlayer != NULL) {
        openSlPlayer->stopPlay();// 停止播放回调
    } else {
        LOGE(LOG_TAG, "Invoke stopPlay but openSlPlayer is NULL!");
    }

    decoder->stop();

    if (audioPlayerThread != NULL) {
        audioPlayerThread->clearMessage();// 清除还未执行的播放请求消息
    }
}

void WeAudioPlayer::destroyPlayer() {
    if (openSlPlayer != NULL) {
        openSlPlayer->destroyPlayer();
    } else {
        LOGE(LOG_TAG, "Invoke destroyPlayer but openSlPlayer is NULL!");
    }
}

void WeAudioPlayer::clearDataAfterStop() {
    decoder->releaseStream();
}

bool WeAudioPlayer::workFinished() {
    if (openSlPlayer != NULL) {
        return openSlPlayer->enqueueFinished;
    }
    return true;
}

void WeAudioPlayer::setRecordPCMFlag(bool record) {
    needRecordPCM = record;
}

void WeAudioPlayer::setVolume(float percent) {
    if (openSlPlayer != NULL) {
        openSlPlayer->setVolume(percent);
    }
}

float WeAudioPlayer::getVolume() {
    if (openSlPlayer != NULL) {
        return openSlPlayer->getVolume();
    }
    return 0;
}

void WeAudioPlayer::setSoundChannel(int channel) {
    if (openSlPlayer != NULL) {
        openSlPlayer->setSoundChannel(channel);
    }
}

int WeAudioPlayer::getPcmMaxBytesPerCallback() {
    if (decoder == NULL) {
        LOGE(LOG_TAG, "getPcmMaxBytesPerCallback but decoder is NULL");
        return 0;
    }
    return decoder->getSampledSizePerSecond();
}

/**
 * 实现 PcmGenerator 声明的虚函数，提供 PCM 数据
 *
 * @param buf 外部调用者用来接收数据的 buffer
 * @return 实际返回的数据字节大小
 */
int WeAudioPlayer::getPcmData(void **buf) {
    int ret = 0;
    // 循环是为了本次操作如果失败就再从队列里取下一个操作，也就是理想情况只操作一次
    while (status != NULL && status->isPlaying()) {
        if (status->isSeeking) {
            av_usleep(100 * 1000);// 睡眠 100 ms，降低 CPU 使用率
            continue;
        }

        ret = decoder->getPcmData(buf);
        if (ret > 0 || ret == -2) {
            // 成功获取数据，或已经播放到末尾，直接退出循环
            break;
        } else if (ret == -1) {
            // 数据加载中，等一会儿再来取
            if (!status->isPlayLoading) {
                status->setLoading(true);
            }
            av_usleep(100 * 1000);// 睡眠 100 ms，降低 CPU 使用率
            continue;
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

    if (ret < 0) {
        ret = 0;
    }
    if (ret > 0 && needRecordPCM) {
        javaListenerContainer->onPcmDataCall->callback(2, *buf, ret);
    }

    return ret;
}

int WeAudioPlayer::getChannelNums() {
    return decoder->getChannelNums();
}

int WeAudioPlayer::getSampleRate() {
    return decoder->getSampleRate();
}

int WeAudioPlayer::getBitsPerSample() {
    return decoder->getBitsPerSample();
}

void WeAudioPlayer::release() {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "release...");
    }
    stopPlay();// 首先停止播放，也就停止了消费者从队列里取数据

    if (openSlPlayer != NULL) {
        delete openSlPlayer;
        openSlPlayer = NULL;
    }

    delete decoder;
    decoder = NULL;

    destroyAudioPlayerThread();// 阻塞等待子线程结束

    // 最顶层 WePlayer 负责回收 javaListenerContainer，这里只把本指针置空
    javaListenerContainer = NULL;

    // 最顶层 WePlayer 负责回收 status，这里只把本指针置空
    status == NULL;
}

void WeAudioPlayer::destroyAudioPlayerThread() {
    if (audioPlayerThread != NULL) {
        audioPlayerThread->shutdown();
        pthread_join(audioPlayerThread->thread, NULL);// 阻塞等待子线程结束
        delete audioPlayerThread;
        audioPlayerThread = NULL;
    }
}
