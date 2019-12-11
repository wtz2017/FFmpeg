//
// Created by WTZ on 2019/12/4.
//

#ifndef FFMPEG_WEERROR_H
#define FFMPEG_WEERROR_H

#define NO_ERROR                    0

#define E_NAME_PREPARE              "Initialization preparation error"
#define E_CODE_PRP_OPEN_SOURCE      10000    /* Can't open data source */
#define E_CODE_PRP_FIND_STREAM      10001    /* Can't find stream info */
#define E_CODE_PRP_FIND_AUDIO       10002    /* Can't find audio stream */
#define E_CODE_PRP_FIND_DECODER     10003    /* Can't find decoder */
#define E_CODE_PRP_ALC_CODEC_CTX    10004    /* Can't allocate an AVCodecContext */
#define E_CODE_PRP_PRM_CODEC_CTX    10005    /* Can't fill the AVCodecContext by AVCodecParameters */
#define E_CODE_PRP_CODEC_OPEN       10006    /* Can't initialize the AVCodecContext to use the given AVCodec */

#define E_NAME_AUDIO_PLAY           "Audio playback error"
#define E_CODE_AUD_CREATE_ENGINE    10100    /* slCreateEngine exception */
#define E_CODE_AUD_REALIZE_ENGINE   10101    /* engineObject Realize exception */
#define E_CODE_AUD_GETITF_ENGINE    10102    /* GetInterface SLEngineItf exception */
#define E_CODE_AUD_CREATE_OUTMIX    10103    /* CreateOutputMix exception */
#define E_CODE_AUD_REALIZE_OUTMIX   10104    /* outputMixObject Realize exception */
#define E_CODE_AUD_CREATE_AUDIOPL   10105    /* CreateAudioPlayer exception */
#define E_CODE_AUD_REALIZ_AUDIOPL   10106    /* playerObject Realize exception */
#define E_CODE_AUD_GETITF_PLAY      10107    /* GetInterface SLPlayItf exception */
#define E_CODE_AUD_GETITF_VOLUME    10108    /* GetInterface SLVolumeItf exception */
#define E_CODE_AUD_GETITF_MUSOLO    10109    /* GetInterface SLMuteSoloItf exception */
#define E_CODE_AUD_GETITF_BUFQUE    10110    /* GetInterface SLAndroidSimpleBufferQueueItf exception */
#define E_CODE_AUD_REGCALL_BUFQUE   10111    /* pcmBufferQueue RegisterCallback exception */
#define E_CODE_AUD_SET_PLAYSTATE    10112    /* SLPlayItf SetPlayState exception */
#define E_CODE_AUD_GETITF_ENVRVB    10113    /* GetInterface SLEnvironmentalReverbItf exception */
#define E_CODE_AUD_SETPROP_ENVRVB   10114    /* SetEnvironmentalReverbProperties exception */
#define E_CODE_AUD_ILLEGAL_CALL     10115    /* Illegal call */

#endif //FFMPEG_WEERROR_H
