//
// Created by WTZ on 2019/11/27.
//

#ifndef FFMPEG_JAVALISTENERCONTAINER_H
#define FFMPEG_JAVALISTENERCONTAINER_H


#include "OnPreparedListener.h"
#include "OnPlayLoadingListener.h"

class JavaListenerContainer {

public:
    OnPreparedListener *onPreparedListener;
    OnPlayLoadingListener *onPlayLoadingListener;

public:
    JavaListenerContainer() {
    }

    ~JavaListenerContainer() {
        delete onPreparedListener;
        onPreparedListener = NULL;
        delete onPlayLoadingListener;
        onPlayLoadingListener = NULL;
    }

};


#endif //FFMPEG_JAVALISTENERCONTAINER_H
