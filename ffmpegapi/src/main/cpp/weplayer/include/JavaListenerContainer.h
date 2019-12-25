//
// Created by WTZ on 2019/11/27.
//

#ifndef FFMPEG_JAVALISTENERCONTAINER_H
#define FFMPEG_JAVALISTENERCONTAINER_H


#include "OnPreparedListener.h"
#include "OnNativeLoading.h"
#include "OnErrorListener.h"
#include "OnCompletionListener.h"
#include "OnPCMDataCall.h"

class JavaListenerContainer {

public:
    OnPreparedListener *onPreparedListener;
    OnNativeLoading *onPlayLoadingListener;
    OnErrorListener *onErrorListener;
    OnCompletionListener *onCompletionListener;
    OnPCMDataCall *onPcmDataCall;

public:
    JavaListenerContainer() {
    }

    ~JavaListenerContainer() {
        delete onPreparedListener;
        onPreparedListener = NULL;
        delete onPlayLoadingListener;
        onPlayLoadingListener = NULL;
        delete onErrorListener;
        onErrorListener = NULL;
        delete onCompletionListener;
        onCompletionListener = NULL;
    }

};


#endif //FFMPEG_JAVALISTENERCONTAINER_H
