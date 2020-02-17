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
#include "OnYUVDataCall.h"
#include "OnCheckVideoHardCodec.h"
#include "OnInitVideoHardCodec.h"
#include "OnVideoPacketCall.h"
#include "OnSetVideoHardCodec.h"
#include "OnStopVideoHardCodec.h"

class JavaListenerContainer {

public:
    OnPreparedListener *onPreparedListener;
    OnNativeLoading *onPlayLoadingListener;
    OnErrorListener *onErrorListener;
    OnCompletionListener *onCompletionListener;
    OnPCMDataCall *onPcmDataCall;
    OnYUVDataCall *onYuvDataCall;
    OnCheckVideoHardCodec *onCheckHardCodec;
    OnInitVideoHardCodec *onInitVideoHardCodec;
    OnSetVideoHardCodec *onSetVideoHardCodec;
    OnVideoPacketCall *onVideoPacketCall;
    OnStopVideoHardCodec *onStopVideoHardCodec;

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
        delete onPcmDataCall;
        onPcmDataCall = NULL;
        delete onYuvDataCall;
        onYuvDataCall = NULL;
        delete onCheckHardCodec;
        onCheckHardCodec = NULL;
        delete onInitVideoHardCodec;
        onInitVideoHardCodec = NULL;
        delete onSetVideoHardCodec;
        onSetVideoHardCodec = NULL;
        delete onVideoPacketCall;
        onVideoPacketCall = NULL;
        delete onStopVideoHardCodec;
        onStopVideoHardCodec = NULL;
    }

};


#endif //FFMPEG_JAVALISTENERCONTAINER_H
