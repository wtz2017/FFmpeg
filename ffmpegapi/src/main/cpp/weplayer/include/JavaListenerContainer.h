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
#include "OnSeekComplete.h"

class JavaListenerContainer {

public:
    OnPreparedListener *onPreparedListener = NULL;
    OnNativeLoading *onPlayLoadingListener = NULL;
    OnSeekComplete *onSeekCompleteListener = NULL;
    OnErrorListener *onErrorListener = NULL;
    OnCompletionListener *onCompletionListener = NULL;
    OnPCMDataCall *onPcmDataCall = NULL;
    OnYUVDataCall *onYuvDataCall = NULL;
    OnCheckVideoHardCodec *onCheckHardCodec = NULL;
    OnInitVideoHardCodec *onInitVideoHardCodec = NULL;
    OnSetVideoHardCodec *onSetVideoHardCodec = NULL;
    OnVideoPacketCall *onVideoPacketCall = NULL;
    OnStopVideoHardCodec *onStopVideoHardCodec = NULL;

public:
    JavaListenerContainer() {
    }

    ~JavaListenerContainer() {
        delete onPreparedListener;
        onPreparedListener = NULL;
        delete onPlayLoadingListener;
        onPlayLoadingListener = NULL;
        delete onSeekCompleteListener;
        onSeekCompleteListener = NULL;
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
