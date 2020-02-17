//
// Created by WTZ on 2019/11/21.
//

#include "PlayStatus.h"

PlayStatus::PlayStatus(OnNativeLoading *onPlayLoadingListener) {
    status = IDLE;
    this->onPlayLoadingListener = onPlayLoadingListener;
    pthread_mutex_init(&mutex, NULL);
    pthread_mutex_init(&loadingMutex, NULL);
}

PlayStatus::~PlayStatus() {
    pthread_mutex_destroy(&mutex);
    pthread_mutex_destroy(&loadingMutex);
    onPlayLoadingListener = NULL;
}

void PlayStatus::setStatus(PlayStatus::Status status, const char *setterName) {
    if (LOG_DEBUG) {
        LOGW(LOG_TAG, "%s set status: %s", setterName, getStatusName(status));
    }
    this->status = status;
}

void PlayStatus::setLoading(bool isLoading) {
    pthread_mutex_lock(&loadingMutex);
    if (isLoading != this->isPlayLoading) {
        this->isPlayLoading = isLoading;
        onPlayLoadingListener->callback(1, isLoading);
    }
    pthread_mutex_unlock(&loadingMutex);
}

bool PlayStatus::isIdle() {
    return status == IDLE;
}

bool PlayStatus::isInitialized() {
    return status == INITIALIZED;
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

bool PlayStatus::isCompleted() {
    return status == COMPLETED;
}

bool PlayStatus::isStoped() {
    return status == STOPPED;
}

bool PlayStatus::isError() {
    return status == ERROR;
}

bool PlayStatus::isReleased() {
    return status == RELEASED;
}

const char *PlayStatus::getStatusName(PlayStatus::Status status) {
    const char *name = NULL;
    switch (status) {
        case IDLE:
            name = "IDLE";
            break;
        case INITIALIZED:
            name = "INITIALIZED";
            break;
        case PREPARING:
            name = "PREPARING";
            break;
        case PREPARED:
            name = "PREPARED";
            break;
        case PLAYING:
            name = "PLAYING";
            break;
        case PAUSED:
            name = "PAUSED";
            break;
        case COMPLETED:
            name = "COMPLETED";
            break;
        case STOPPED:
            name = "STOPPED";
            break;
        case ERROR:
            name = "ERROR";
            break;
        case RELEASED:
            name = "RELEASED";
            break;
        default:
            name = "unknown";
            break;
    }
    return name;
}

const char *PlayStatus::getCurrentStatusName() {
    return getStatusName(status);
}
