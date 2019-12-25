//
// Created by WTZ on 2019/12/24.
//

#ifndef FFMPEG_EDITSTATUS_H
#define FFMPEG_EDITSTATUS_H

#include <pthread.h>
#include "AndroidLog.h"

class EditStatus {

public:
    enum Status {
        IDLE, INITIALIZED, PREPARING, PREPARED, EDITING, COMPLETED, STOPPED, ERROR, RELEASED
    };

    bool isLoading = false;
    bool isSeeking = false;

    pthread_mutex_t mutex;

private:
    const char *LOG_TAG = "EditStatus";

    Status status;

public:
    EditStatus();

    ~EditStatus();

    void setStatus(Status status, const char *setterName);

    bool isIdle();

    bool isInitialized();

    bool isPreparing();

    bool isPrepared();

    bool isEditing();

    bool isCompleted();

    bool isStoped();

    bool isError();

    bool isReleased();

    const char *getStatusName(Status status);

    const char *getCurrentStatusName();
};


#endif //FFMPEG_EDITSTATUS_H
