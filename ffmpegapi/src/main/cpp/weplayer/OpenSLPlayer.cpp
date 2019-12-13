//
// Created by WTZ on 2019/11/25.
//

#include "OpenSLPlayer.h"

OpenSLPlayer::OpenSLPlayer(PcmGenerator *pcmGenerator) {
    this->pcmGenerator = pcmGenerator;
}

OpenSLPlayer::~OpenSLPlayer() {
    destroy();
    pcmGenerator = NULL;
}

void pcmBufferCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {
    if (LOG_REPEAT_DEBUG) {
        LOGD("OpenSLPlayer", "call pcmBufferCallback context=%p", context);
    }
    OpenSLPlayer *player = (OpenSLPlayer *) context;
    if (player == NULL) {
        LOGE("OpenSLPlayer", "pcmBufferCallback cast context to OpenSLPlayer result is NULL")
        return;
    }

    player->enqueueFinished = false;
    int size = player->pcmGenerator->getPcmData(&player->enqueueBuffer);
    if (size == 0) {// 已经播放完成或不在播放状态了
        LOGW("OpenSLPlayer", "pcmBufferCallback getPcmData size=%d", size);
        player->enqueueFailed = true;// 用于暂停后可能异步获取失败恢复播放时主动喂一次数据
        player->enqueueFinished = true;
        return;
    }

    SLresult result;
    result = (*player->pcmBufferQueue)->Enqueue(
            player->pcmBufferQueue, player->enqueueBuffer, size);
    if (SL_RESULT_SUCCESS != result) {
        LOGE("OpenSLPlayer", "pcmBufferCallback BufferQueue Enqueue exception!");
        player->enqueueFailed = true;
        if (player->autoEnqueCount < OpenSLPlayer::MAX_AUTO_ENQUE_COUNT) {
            player->autoEnqueCount++;
            pcmBufferCallback(bq, context);
        }
    } else {
        player->enqueueFailed = false;
        player->autoEnqueCount = 0;
    }
    player->enqueueFinished = true;
}

int OpenSLPlayer::init() {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "init");
    }
    int ret;

    // 初始化引擎
    if ((ret = initEngine()) != NO_ERROR) {
        destroy();
        return ret;
    }

    // 使用引擎创建混音器
    if ((ret = initOutputMix()) != NO_ERROR) {
        destroy();
        return ret;
    }
    // 使用混音器设置混音效果
    setEnvironmentalReverb();

    initSuccess = true;
    return NO_ERROR;
}

int OpenSLPlayer::createPlayer() {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "createPlayer");
    }

    if (!initSuccess) {
        LOGE(LOG_TAG, "Can't create player because init not success!");
        return E_CODE_AUD_ILLEGAL_CALL;
    }

    int ret;
    // 使用引擎创建数据源为缓冲队列的播放器
    if ((ret = createBufferQueueAudioPlayer()) != NO_ERROR) {
        destroy();
        return ret;
    }
    // 设置播放器缓冲队列回调函数
    if ((ret = setBufferQueueCallback(pcmBufferCallback, this)) != NO_ERROR) {
        destroy();
        return ret;
    }

    return NO_ERROR;
}

void OpenSLPlayer::destroyPlayer() {
    destroyBufferQueueAudioPlayer();
}

void OpenSLPlayer::startPlay() {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "startPlay");
    }
    // 设置播放状态为正在播放
    setPlayState(SL_PLAYSTATE_PLAYING);

    // 首次启动时需要主动调用缓冲队列回调函数开始播放
    pcmBufferCallback(pcmBufferQueue, this);
}

void OpenSLPlayer::pause() {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "pause");
    }
    setPlayState(SL_PLAYSTATE_PAUSED);
}

void OpenSLPlayer::resumePlay() {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "resumePlay");
    }
    setPlayState(SL_PLAYSTATE_PLAYING);
    if (enqueueFailed) {
        pcmBufferCallback(pcmBufferQueue, this);
    }
}

void OpenSLPlayer::stopPlay() {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "stopPlay");
    }
    setPlayState(SL_PLAYSTATE_STOPPED);
}

void OpenSLPlayer::destroy() {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "destroy");
    }
    initSuccess = false;
    destroyBufferQueueAudioPlayer();
    destroyOutputMixer();
    destroyEngine();
}

int OpenSLPlayer::initEngine() {
    // create engine object
    SLresult result;
    result = slCreateEngine(&engineObject, 0, NULL, 0, NULL, NULL);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "slCreateEngine exception!");
        return E_CODE_AUD_CREATE_ENGINE;
    }

    // realize the engine object
    (void) result;
    result = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "engineObject Realize exception!");
        destroyEngine();
        return E_CODE_AUD_REALIZE_ENGINE;
    }

    // get the engine interface, which is needed in order to create other objects
    (void) result;
    result = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engine);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "GetInterface SLEngineItf exception!");
        destroyEngine();
        return E_CODE_AUD_GETITF_ENGINE;
    }

    return NO_ERROR;
}

int OpenSLPlayer::initOutputMix() {
    // create output mix, with environmental reverb specified as a non-required interface
    const SLInterfaceID ids[1] = {SL_IID_ENVIRONMENTALREVERB};
    const SLboolean reqs[1] = {SL_BOOLEAN_FALSE};
    SLresult result;
    result = (*engine)->CreateOutputMix(engine, &outputMixObject, 1, ids, reqs);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "CreateOutputMix exception!");
        return E_CODE_AUD_CREATE_OUTMIX;
    }

    // realize the output mix
    (void) result;
    result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "outputMixObject Realize exception!");
        destroyOutputMixer();
        return E_CODE_AUD_REALIZE_OUTMIX;
    }

    return NO_ERROR;
}

int OpenSLPlayer::setEnvironmentalReverb() {
    // get the environmental reverb interface
    // this could fail if the environmental reverb effect is not available,
    // either because the feature is not present, excessive CPU load, or
    // the required MODIFY_AUDIO_SETTINGS permission was not requested and granted
    SLresult result;
    result = (*outputMixObject)->GetInterface(outputMixObject, SL_IID_ENVIRONMENTALREVERB,
                                              &outputMixEnvironmentalReverb);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "GetInterface SLEnvironmentalReverbItf exception!");
        return E_CODE_AUD_GETITF_ENVRVB;
    }

    // aux effect on the output mix, used by the buffer queue player
    const SLEnvironmentalReverbSettings reverbSettings = SL_I3DL2_ENVIRONMENT_PRESET_STONECORRIDOR;
    result = (*outputMixEnvironmentalReverb)->SetEnvironmentalReverbProperties(
            outputMixEnvironmentalReverb, &reverbSettings);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "SetEnvironmentalReverbProperties exception!");
        return E_CODE_AUD_SETPROP_ENVRVB;
    }

    return NO_ERROR;
}

int OpenSLPlayer::createBufferQueueAudioPlayer() {
    // configure audio source
    SLDataLocator_AndroidSimpleBufferQueue bufferQueueLocator = {
            SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2
    };
    SLDataFormat_PCM pcmFormat = {
            SL_DATAFORMAT_PCM,// 数据格式：pcm
            pcmGenerator->getChannelNums(),// 声道数
            pcmGenerator->getOpenSLSampleRate(),// 采样率
            pcmGenerator->getBitsPerSample(),// bitsPerSample
            pcmGenerator->getBitsPerSample(),// containerSize：和采样位数一致就行
            pcmGenerator->getOpenSLChannelLayout(),// 声道布局
            SL_BYTEORDER_LITTLEENDIAN// 字节排列顺序：小端 little-endian，将低序字节存储在起始地址
    };
    SLDataSource audioSrc = {&bufferQueueLocator, &pcmFormat};

    // configure audio sink
    SLDataLocator_OutputMix outputMixLocator = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
    SLDataSink audioSnk = {&outputMixLocator, NULL};

    /*
     * create audio player:
     *     fast audio does not support when SL_IID_EFFECTSEND is required, skip it
     *     for fast audio case
     */
    const int ID_COUNT = 3;
    // 如果某个功能接口没注册 id 和写为 SL_BOOLEAN_TRUE，后边通过 GetInterface 就获取不到这个接口
    const SLInterfaceID ids[ID_COUNT] = {SL_IID_BUFFERQUEUE, SL_IID_VOLUME, SL_IID_MUTESOLO};
    const SLboolean reqs[ID_COUNT] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
    SLresult result;
    result = (*engine)->CreateAudioPlayer(
            engine, &playerObject, &audioSrc, &audioSnk, ID_COUNT, ids, reqs);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "CreateAudioPlayer exception!");
        return E_CODE_AUD_CREATE_AUDIOPL;
    }

    // realize the player
    (void) result;
    result = (*playerObject)->Realize(playerObject, SL_BOOLEAN_FALSE);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "playerObject Realize exception!");
        destroyBufferQueueAudioPlayer();
        return E_CODE_AUD_REALIZ_AUDIOPL;
    }

    // get play controller
    result = (*playerObject)->GetInterface(playerObject, SL_IID_PLAY, &playController);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "GetInterface SLPlayItf exception!");
        destroyBufferQueueAudioPlayer();
        return E_CODE_AUD_GETITF_PLAY;
    }

    // get volume controller
    result = (*playerObject)->GetInterface(playerObject, SL_IID_VOLUME, &volumeController);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "GetInterface SLVolumeItf exception!");
        destroyBufferQueueAudioPlayer();
        return E_CODE_AUD_GETITF_VOLUME;
    }
    setVolume(volumePercent);

    // get muteSolo controller
    result = (*playerObject)->GetInterface(playerObject, SL_IID_MUTESOLO, &muteSoloController);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "GetInterface SLMuteSoloItf exception!");
        destroyBufferQueueAudioPlayer();
        return E_CODE_AUD_GETITF_MUSOLO;
    }
    setSoundChannel(soundChannel);

    return NO_ERROR;
}

int
OpenSLPlayer::setBufferQueueCallback(slAndroidSimpleBufferQueueCallback callback, void *pContext) {
    // get the buffer queue interface
    SLresult result;
    result = (*playerObject)->GetInterface(playerObject, SL_IID_BUFFERQUEUE, &pcmBufferQueue);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "GetInterface SLAndroidSimpleBufferQueueItf exception!");
        destroyBufferQueueAudioPlayer();
        return E_CODE_AUD_GETITF_BUFQUE;
    }

    // register callback on the buffer queue
    (void) result;
    result = (*pcmBufferQueue)->RegisterCallback(pcmBufferQueue, callback, pContext);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "pcmBufferQueue RegisterCallback exception!");
        destroyBufferQueueAudioPlayer();
        return E_CODE_AUD_REGCALL_BUFQUE;
    }

    return NO_ERROR;
}

int OpenSLPlayer::setPlayState(SLuint32 state) {
    if (playController == NULL) {
        LOGE(LOG_TAG, "SetPlayState %d failed because playController is NULL !", state);
        return E_CODE_AUD_SET_PLAYSTATE;
    }

    SLresult result;
    result = (*playController)->SetPlayState(playController, state);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "SetPlayState %d exception!", state);
        return E_CODE_AUD_SET_PLAYSTATE;
    }

    return NO_ERROR;
}

void OpenSLPlayer::destroyBufferQueueAudioPlayer() {
    if (playerObject != NULL) {
        (*playerObject)->Destroy(playerObject);
        playerObject = NULL;
        playController = NULL;
        volumeController = NULL;
        muteSoloController = NULL;
        pcmBufferQueue = NULL;
        enqueueBuffer = NULL;
    }
}

void OpenSLPlayer::destroyOutputMixer() {
    if (outputMixObject != NULL) {
        (*outputMixObject)->Destroy(outputMixObject);
        outputMixObject = NULL;
        outputMixEnvironmentalReverb = NULL;
    }
}

void OpenSLPlayer::destroyEngine() {
    if (engineObject != NULL) {
        (*engineObject)->Destroy(engineObject);
        engineObject = NULL;
        engine = NULL;
    }
}

bool OpenSLPlayer::isInitSuccess() {
    return initSuccess;
}

/**
 * 设置音量
 * @param percent 范围是：0 ~ 1.0
 */
void OpenSLPlayer::setVolume(float percent) {
    if (percent < 0) {
        percent = 0;
    } else if (percent > 1.0) {
        percent = 1.0;
    }
    volumePercent = percent;

    if (volumeController == NULL || !initSuccess) {
        LOGW(LOG_TAG, "setVolume %f but volumeController is %d and init %d", volumePercent,
             volumeController, initSuccess);
        return;
    }
    // 第 2 个参数有效范围是：0 ~ -5000，其中 0 表示最大音量，-5000 表示静音
    if (volumePercent > 0.30) {
        (*volumeController)->SetVolumeLevel(volumeController, 100 * (1 - volumePercent) * -20);
    } else if (volumePercent > 0.25) {
        (*volumeController)->SetVolumeLevel(volumeController, 100 * (1 - volumePercent) * -22);
    } else if (volumePercent > 0.20) {
        (*volumeController)->SetVolumeLevel(volumeController, 100 * (1 - volumePercent) * -25);
    } else if (volumePercent > 0.15) {
        (*volumeController)->SetVolumeLevel(volumeController, 100 * (1 - volumePercent) * -28);
    } else if (volumePercent > 0.10) {
        (*volumeController)->SetVolumeLevel(volumeController, 100 * (1 - volumePercent) * -30);
    } else if (volumePercent > 0.05) {
        (*volumeController)->SetVolumeLevel(volumeController, 100 * (1 - volumePercent) * -34);
    } else if (volumePercent > 0.03) {
        (*volumeController)->SetVolumeLevel(volumeController, 100 * (1 - volumePercent) * -37);
    } else if (volumePercent > 0.01) {
        (*volumeController)->SetVolumeLevel(volumeController, 100 * (1 - volumePercent) * -40);
    } else {
        (*volumeController)->SetVolumeLevel(volumeController, 100 * (1 - volumePercent) * -100);
    }
}

float OpenSLPlayer::getVolume() {
    return volumePercent;
}

void OpenSLPlayer::setSoundChannel(int channel) {
    if (muteSoloController == NULL || !initSuccess) {
        LOGW(LOG_TAG, "setSoundChannel %d but muteSoloController is %d and init %d", channel,
             muteSoloController, initSuccess);
        return;
    }

    if (channel != CHANNEL_LEFT && channel != CHANNEL_RIGHT && channel != CHANNEL_STEREO) {
        LOGE(LOG_TAG, "setSoundChannel argument %d is invalid!", channel);
        return;
    }
    soundChannel = channel;

    switch (channel) {
        case CHANNEL_RIGHT:
            openRightChannel(true);
            openLeftChannel(false);
            break;
        case CHANNEL_LEFT:
            openRightChannel(false);
            openLeftChannel(true);
            break;
        case CHANNEL_STEREO:
            openRightChannel(false);
            openLeftChannel(false);
            break;
    }
}

void OpenSLPlayer::openRightChannel(bool open) {
    // 0 是右声道
    (*muteSoloController)->SetChannelMute(muteSoloController, 0, open);
}

void OpenSLPlayer::openLeftChannel(bool open) {
    // 1 是左声道
    (*muteSoloController)->SetChannelMute(muteSoloController, 1, open);
}

SLuint32 OpenSLPlayer::convertToOpenSLSampleRate(int sampleRate) {
    SLuint32 rate = 0;
    switch (sampleRate) {
        case 8000:
            rate = SL_SAMPLINGRATE_8;
            break;
        case 11025:
            rate = SL_SAMPLINGRATE_11_025;
            break;
        case 12000:
            rate = SL_SAMPLINGRATE_12;
            break;
        case 16000:
            rate = SL_SAMPLINGRATE_16;
            break;
        case 22050:
            rate = SL_SAMPLINGRATE_22_05;
            break;
        case 24000:
            rate = SL_SAMPLINGRATE_24;
            break;
        case 32000:
            rate = SL_SAMPLINGRATE_32;
            break;
        case 44100:
            rate = SL_SAMPLINGRATE_44_1;
            break;
        case 48000:
            rate = SL_SAMPLINGRATE_48;
            break;
        case 64000:
            rate = SL_SAMPLINGRATE_64;
            break;
        case 88200:
            rate = SL_SAMPLINGRATE_88_2;
            break;
        case 96000:
            rate = SL_SAMPLINGRATE_96;
            break;
        case 192000:
            rate = SL_SAMPLINGRATE_192;
            break;
        default:
            rate = SL_SAMPLINGRATE_44_1;
    }
    return rate;
}

SLuint32 OpenSLPlayer::ffmpegToOpenSLChannelLayout(int64_t ffmpegChannelLayout) {
    SLuint32 ret = 0;
    switch (ffmpegChannelLayout) {
        case AV_CH_LAYOUT_MONO:
            ret = OPENSL_LAYOUT_MONO;
            break;
        case AV_CH_LAYOUT_STEREO:
            ret = OPENSL_LAYOUT_STEREO;
            break;
        case AV_CH_LAYOUT_2POINT1:
            ret = OPENSL_LAYOUT_2POINT1;
            break;
        case AV_CH_LAYOUT_2_1:
            ret = OPENSL_LAYOUT_2_1;
            break;
        case AV_CH_LAYOUT_SURROUND:
            ret = OPENSL_LAYOUT_SURROUND;
            break;
        case AV_CH_LAYOUT_3POINT1:
            ret = OPENSL_LAYOUT_3POINT1;
            break;
        case AV_CH_LAYOUT_4POINT0:
            ret = OPENSL_LAYOUT_4POINT0;
            break;
        case AV_CH_LAYOUT_4POINT1:
            ret = OPENSL_LAYOUT_4POINT1;
            break;
        case AV_CH_LAYOUT_2_2:
            ret = OPENSL_LAYOUT_2_2;
            break;
        case AV_CH_LAYOUT_QUAD:
            ret = OPENSL_LAYOUT_QUAD;
            break;
        case AV_CH_LAYOUT_5POINT0:
            ret = OPENSL_LAYOUT_5POINT0;
            break;
        case AV_CH_LAYOUT_5POINT1:
            ret = OPENSL_LAYOUT_5POINT1;
            break;
        case AV_CH_LAYOUT_5POINT0_BACK:
            ret = OPENSL_LAYOUT_5POINT0_BACK;
            break;
        case AV_CH_LAYOUT_5POINT1_BACK:
            ret = OPENSL_LAYOUT_5POINT1_BACK;
            break;
        case AV_CH_LAYOUT_6POINT0:
            ret = OPENSL_LAYOUT_6POINT0;
            break;
        case AV_CH_LAYOUT_6POINT0_FRONT:
            ret = OPENSL_LAYOUT_6POINT0_FRONT;
            break;
        case AV_CH_LAYOUT_HEXAGONAL:
            ret = OPENSL_LAYOUT_HEXAGONAL;
            break;
        case AV_CH_LAYOUT_6POINT1:
            ret = OPENSL_LAYOUT_6POINT1;
            break;
        case AV_CH_LAYOUT_6POINT1_BACK:
            ret = OPENSL_LAYOUT_6POINT1_BACK;
            break;
        case AV_CH_LAYOUT_6POINT1_FRONT:
            ret = OPENSL_LAYOUT_6POINT1_FRONT;
            break;
        case AV_CH_LAYOUT_7POINT0:
            ret = OPENSL_LAYOUT_7POINT0;
            break;
        case AV_CH_LAYOUT_7POINT0_FRONT:
            ret = OPENSL_LAYOUT_7POINT0_FRONT;
            break;
        case AV_CH_LAYOUT_7POINT1:
            ret = OPENSL_LAYOUT_7POINT1;
            break;
        case AV_CH_LAYOUT_7POINT1_WIDE:
            ret = OPENSL_LAYOUT_7POINT1_WIDE;
            break;
        case AV_CH_LAYOUT_7POINT1_WIDE_BACK:
            ret = OPENSL_LAYOUT_7POINT1_WIDE_BACK;
            break;
        case AV_CH_LAYOUT_OCTAGONAL:
            ret = OPENSL_LAYOUT_OCTAGONAL;
            break;
        default:
            ret = OPENSL_LAYOUT_STEREO;
    }
    return ret;
}
