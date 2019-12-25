//
// Created by WTZ on 2019/12/24.
//

#include "EditStatus.h"

EditStatus::EditStatus() {
    status = IDLE;
    pthread_mutex_init(&mutex, NULL);
}

EditStatus::~EditStatus() {
    pthread_mutex_destroy(&mutex);
}

void EditStatus::setStatus(EditStatus::Status status, const char *setterName) {
    if (LOG_DEBUG) {
        LOGW(LOG_TAG, "%s set status: %s", setterName, getStatusName(status));
    }
    this->status = status;
}

bool EditStatus::isIdle() {
    return status == IDLE;
}

bool EditStatus::isInitialized() {
    return status == INITIALIZED;
}

bool EditStatus::isPreparing() {
    return status == PREPARING;
}

bool EditStatus::isPrepared() {
    return status == PREPARED;
}

bool EditStatus::isEditing() {
    return status == EDITING;
}

bool EditStatus::isCompleted() {
    return status == COMPLETED;
}

bool EditStatus::isStoped() {
    return status == STOPPED;
}

bool EditStatus::isError() {
    return status == ERROR;
}

bool EditStatus::isReleased() {
    return status == RELEASED;
}

const char *EditStatus::getStatusName(EditStatus::Status status) {
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
        case EDITING:
            name = "EDITING";
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

const char *EditStatus::getCurrentStatusName() {
    return getStatusName(status);
}
