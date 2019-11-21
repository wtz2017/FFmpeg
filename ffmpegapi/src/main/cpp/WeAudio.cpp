//
// Created by WTZ on 2019/11/21.
//

#include "WeAudio.h"

WeAudio::WeAudio(PlayStatus *status) {
    queue = new AVPacketQueue(status);
}

WeAudio::~WeAudio() {
    delete codecContext;
    codecContext = NULL;

    delete codecParams;
    codecParams = NULL;

    delete queue;
    queue = NULL;
}