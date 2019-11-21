//
// Created by WTZ on 2019/11/21.
//

#ifndef FFMPEG_PLAYSTATUS_H
#define FFMPEG_PLAYSTATUS_H


class PlayStatus {

public:
    enum Status {
        PREPARING, PREPARED, PLAYING, PAUSED, STOPPED, ERROR
    };

private:
    Status status;

public:
    PlayStatus();

    ~PlayStatus();

    void setStatus(Status status);

    bool isPreparing();

    bool isPrepared();

    bool isPlaying();

    bool isPaused();

    bool isStoped();

};


#endif //FFMPEG_PLAYSTATUS_H
