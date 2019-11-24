//
// Created by WTZ on 2019/11/21.
//

#include "PlayStatus.h"

PlayStatus::PlayStatus() {
    status = STOPPED;
}

PlayStatus::~PlayStatus() {
}

// TODO 是否加锁同步处理
void PlayStatus::setStatus(PlayStatus::Status status) {
    this->status = status;
}

bool PlayStatus::isPreparing() {
    return status == PREPARING;
}

bool PlayStatus::isPrepared() {
    return status == PREPARED;
}

bool PlayStatus::isPlaying() {
    return status == PLAYING;
}

bool PlayStatus::isPaused() {
    return status == PAUSED;
}

bool PlayStatus::isStoped() {
    return status == STOPPED;
}