//
// Created by WTZ on 2019/11/21.
//

#include "PlayStatus.h"

PlayStatus::PlayStatus() {
    status = STOPPED;
    pthread_mutex_init(&mutex, NULL);
}

PlayStatus::~PlayStatus() {
    pthread_mutex_destroy(&mutex);
}

void PlayStatus::setStatus(PlayStatus::Status status, const char *setterName) {
    if (LOG_DEBUG) {
        LOGW(LOG_TAG, "%s set status: %s", setterName, getStatusName(status));
    }
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

bool PlayStatus::isCompleted() {
    return status == COMPLETED;
}

bool PlayStatus::isStoped() {
    return status == STOPPED;
}

bool PlayStatus::isError() {
    return status == ERROR;
}

const char *PlayStatus::getStatusName(PlayStatus::Status status) {
    const char *name = NULL;
    switch (status) {
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
        default:
            name = "unknown";
            break;
    }
    return name;
}

