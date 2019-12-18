//
// Created by WTZ on 2019/11/21.
//

#include "WeAudio.h"

WeAudio::WeAudio(PlayStatus *status, JavaListenerContainer *javaListenerContainer) {
    this->status = status;
    this->javaListenerContainer = javaListenerContainer;

    this->queue = new AVPacketQueue(status);
}

WeAudio::~WeAudio() {
    release();
}

int WeAudio::init() {
    if (openSlPlayer == NULL) {
        openSlPlayer = new OpenSLPlayer(this);
    }
    int ret;
    if ((ret = openSlPlayer->init()) != NO_ERROR) {
        LOGE(LOG_TAG, "OpenSLPlayer init failed!");
        delete openSlPlayer;
        openSlPlayer = NULL;
        return ret;
    }

    createConsumerThread();
    return NO_ERROR;
}

/**
 * 专门用单独线程处理播放行为，因为播放就是在消费数据
 */
void audioConsumerMessageHanler(int msgType, void *context) {
    if (LOG_DEBUG) {
        LOGD("WeAudio", "audioConsumerMessageHanler: msgType=%d", msgType);
    }
    WeAudio *weAudio = (WeAudio *) context;
    switch (msgType) {
        case WeAudio::AUDIO_CONSUMER_START_PLAY:
            weAudio->_handleStartPlay();
            break;
        case WeAudio::AUDIO_CONSUMER_RESUME_PLAY:
            weAudio->_handleResumePlay();
            break;
    }
}

void WeAudio::createConsumerThread() {
    if (audioConsumerThread != NULL) {
        return;
    }
    audioConsumerThread = new LooperThread("AudioConsumer", this, audioConsumerMessageHanler);
    audioConsumerThread->create();
}

int WeAudio::createPlayer() {
    if (openSlPlayer == NULL) {
        LOGE(LOG_TAG, "Invoke createPlayer but openSlPlayer is NULL!");
        return E_CODE_AUD_ILLEGAL_CALL;
    }

    if (!openSlPlayer->isInitSuccess()) {
        LOGE(LOG_TAG, "Invoke createPlayer but openSlPlayer did not initialize successfully!");
        return E_CODE_AUD_ILLEGAL_CALL;
    }

    // 音频流采样率等参数不一样，就需要重新创建 player
    int ret;
    if ((ret = openSlPlayer->createPlayer()) != NO_ERROR) {
        LOGE(LOG_TAG, "OpenSLPlayer createPlayer failed!");
        return ret;
    }

    // 使用 1 秒的采样字节数作为缓冲区大小，音频流采样参数不一样，缓冲区大小也不一样，就需要新建 buffer
    sampleBuffer = static_cast<uint8_t *>(av_malloc(audioStream->sampledSizePerSecond));

    return NO_ERROR;
}

int WeAudio::startPlay() {
    if (audioConsumerThread == NULL) {
        LOGE(LOG_TAG, "Invoke startPlay but audioConsumerThread is NULL!");
        return E_CODE_AUD_ILLEGAL_CALL;
    }

    audioConsumerThread->sendMessage(AUDIO_CONSUMER_START_PLAY);
    return NO_ERROR;
}

void WeAudio::_handleStartPlay() {
    if (openSlPlayer == NULL) {
        LOGE(LOG_TAG, "_handleStartPlay but openSlPlayer is NULL!");
        return;
    }

    if (!openSlPlayer->isInitSuccess()) {
        LOGE(LOG_TAG, "_handleStartPlay but openSlPlayer did not initialize successfully!");
        return;
    }

    openSlPlayer->startPlay();
}

void WeAudio::pause() {
    if (openSlPlayer == NULL) {
        LOGE(LOG_TAG, "Invoke pause but openSlPlayer is NULL!");
        return;
    }

    if (!openSlPlayer->isInitSuccess()) {
        LOGE(LOG_TAG, "Invoke pause but openSlPlayer did not initialize successfully!");
        return;
    }

    openSlPlayer->pause();
}

void WeAudio::resumePlay() {
    if (audioConsumerThread == NULL) {
        LOGE(LOG_TAG, "Invoke resumePlay but audioConsumerThread is NULL!");
        return;
    }

    audioConsumerThread->sendMessage(AUDIO_CONSUMER_RESUME_PLAY);
}

void WeAudio::_handleResumePlay() {
    if (openSlPlayer == NULL) {
        LOGE(LOG_TAG, "_handleResumePlay but openSlPlayer is NULL!");
        return;
    }

    if (!openSlPlayer->isInitSuccess()) {
        LOGE(LOG_TAG, "_handleResumePlay but openSlPlayer did not initialize successfully!");
        return;
    }

    openSlPlayer->resumePlay();
}

int WeAudio::getPcmMaxBytesPerCallback() {
    if (audioStream == NULL) {
        LOGE(LOG_TAG, "getPcmMaxBytesPerCallback but audioStream is NULL");
        return 0;
    }
    return audioStream->sampledSizePerSecond;
}

/**
 * 实现 PcmGenerator 声明的虚函数，提供 PCM 数据
 *
 * @param buf 外部调用者用来接收数据的 buffer
 * @return 实际返回的数据字节大小
 */
int WeAudio::getPcmData(void **buf) {
    int ret = 0;
    // 循环是为了本次操作如果失败就再从队列里取下一个操作，也就是理想情况只操作一次
    while (status != NULL && status->isPlaying()) {
        if (queue->getQueueSize() == 0) {
            if (queue->isProductDataComplete()) {
                break;
            }

            // 队列中无数据且生产数据未完成，表示正在加载中
            if (!status->isPlayLoading) {
                status->isPlayLoading = true;
                javaListenerContainer->onPlayLoadingListener->callback(1, true);
            }

            av_usleep(100 * 1000);// 睡眠 100 ms，降低 CPU 使用率
            continue;
        }

        // 队列中有数据
        if (status->isPlayLoading) {
            status->isPlayLoading = false;
            javaListenerContainer->onPlayLoadingListener->callback(1, false);
        }

        // 解 AVPacket 包
        if (!decodeQueuePacket()) {
            // 解码失败直接取下一个包，不用等待
            continue;
        }

        // 对解出来的 AVFrame 重采样
        ret = resample(&sampleBuffer);
        if (ret < 0) {
            // 重采样失败直接取下一个包，不用等待
            continue;
        }

        if (pitch != NORMAL_PITCH || tempo != NORMAL_TEMPO) {
            // 需要变调、变速
            if (soundTouchBuffer == NULL || soundTouch == NULL) {
                initSoundTouch();
            }

            ret = adjustPitchTempo(sampleBuffer, ret, soundTouchBuffer);
            if (ret <= 0) {
                // 调音失败直接取下一个包，不用等待
                continue;
            }

            *buf = soundTouchBuffer;
        } else {
            *buf = sampleBuffer;
        }

        break;
    }

    updatePCM16bitDB(reinterpret_cast<char *>(*buf), ret);

    if (needRecordPCM) {
        javaListenerContainer->onPcmDataCall->callback(2, *buf, ret);
    }

    return ret;
}

bool WeAudio::decodeQueuePacket() {
    // 从队列中取出 packet
    avPacket = av_packet_alloc();
    if (!queue->getAVpacket(avPacket)) {
        LOGE(LOG_TAG, "getAVpacket from queue failed");
        releaseAvPacket();
        return false;
    }

    // 把 packet 发送给解码器解码
    int ret = avcodec_send_packet(audioStream->codecContext, avPacket);
    releaseAvPacket();// 不管解码成功与否，都要先释放内存
    if (ret != 0) {
        LOGE(LOG_TAG, "avcodec_send_packet occurred exception: %d", ret);
        return false;
    }

    // 接收解码后的数据帧 frame
    avFrame = av_frame_alloc();
    ret = avcodec_receive_frame(audioStream->codecContext, avFrame);
    if (ret != 0) {
        LOGE(LOG_TAG, "avcodec_receive_frame occurred exception: %d", ret);
        releaseAvFrame();
        return false;
    }

    return true;
}

int WeAudio::resample(uint8_t **out) {
    // 处理可能缺少的声道个数和声道布局参数
    if (avFrame->channels == 0 && avFrame->channel_layout == 0) {
        LOGE(LOG_TAG, "Both avFrame channels and channel_layout are 0");
    } else if (avFrame->channels > 0 && avFrame->channel_layout == 0) {
        avFrame->channel_layout = av_get_default_channel_layout(avFrame->channels);
    } else if (avFrame->channels == 0 && avFrame->channel_layout > 0) {
        avFrame->channels = av_get_channel_layout_nb_channels(avFrame->channel_layout);
    }

    // 设置重采样输入和输出音频的信息
    SwrContext *swrContext = NULL;
    swrContext = swr_alloc_set_opts(
            NULL,
            /* 转换输出配置选项 */
            AudioStream::SAMPLE_OUT_CHANNEL_LAYOUT,/* 声道布局 */
            AudioStream::SAMPLE_OUT_FORMAT,/* 音频采样格式 */
            avFrame->sample_rate,/* 采样率，与输入保持一致 */
            /* 转换输入配置选项 */
            avFrame->channel_layout,
            static_cast<AVSampleFormat>(avFrame->format),
            avFrame->sample_rate,
            /* 日志配置 */
            NULL, NULL
    );
    if (!swrContext) {
        LOGE(LOG_TAG, "swr_alloc_set_opts occurred exception");
        releaseAvFrame();
        return -1;
    }

    // 重采样参数设置或修改参数之后必须调用 swr_init() 对 SwrContext 进行初始化
    if (swr_init(swrContext) < 0) {
        LOGE(LOG_TAG, "swr_init occurred exception");
        releaseAvFrame();
        swr_free(&swrContext);
        swrContext = NULL;
        return -1;
    }

    // 重采样转换，返回值为每个声道采样个数
    int sampleNumsPerChannel = swr_convert(
            swrContext,
            out,/* 转换后接收数据的 buffer */
            avFrame->nb_samples,/* 输出此帧每个通道包含的采样个数 */
            (const uint8_t **) avFrame->data,/* 需要重采样的原始数据 */
            avFrame->nb_samples);/* 输入此帧每个通道包含的采样个数 */
    if (sampleNumsPerChannel < 0) {
        LOGE(LOG_TAG, "swr_convert occurred exception");
        releaseAvFrame();
        swr_free(&swrContext);
        swrContext = NULL;
        return -1;
    }

    int sampleDataBytes =
            audioStream->channelNums * sampleNumsPerChannel * audioStream->bytesPerSample;
    updatePlayTime(avFrame->pts, sampleDataBytes);
    if (LOG_REPEAT_DEBUG) {
        LOGD(LOG_TAG, "resample data size bytes: %d", sampleDataBytes);
    }

    releaseAvFrame();
    swr_free(&swrContext);
    swrContext = NULL;

    return sampleDataBytes;
}

void WeAudio::updatePlayTime(int64_t pts, int sampleDataBytes) {
    currentFrameTime = pts * av_q2d(audioStream->streamTimeBase);
    if (currentFrameTime < playTimeSecs) {
        // avFrame->pts maybe 0
        currentFrameTime = playTimeSecs;
    }
    // 实际播放时间 = 当前帧时间 + 本帧实际采样字节数占 1 秒理论采样总字节数的比例
    playTimeSecs =
            currentFrameTime + (sampleDataBytes / (double) audioStream->sampledSizePerSecond);
}

void WeAudio::initSoundTouch() {
    if (soundTouchBuffer == NULL) {
        // 创建所需的 buffer，大小要与重采样 buffer 一样大
        soundTouchBuffer = static_cast<SAMPLETYPE *>(malloc(
                audioStream->sampledSizePerSecond));
    }

    if (soundTouch == NULL) {
        soundTouch = new SoundTouch();
        soundTouch->setSampleRate(audioStream->sampleRate);
        soundTouch->setChannels(audioStream->channelNums);
        soundTouch->setPitch(pitch);
        soundTouch->setTempo(tempo);
    }
}

int WeAudio::adjustPitchTempo(uint8_t *in, int inSize, SAMPLETYPE *out) {
    // 因为 FFmpeg 重采样出来的 PCM 数据是 uint8 的，
    // 而 SoundTouch 中最低是 SAMPLETYPE(16bit integer sample type)，
    // 所以我们需要将 8bit 的数据转换成 16bit 后再给 SoundTouch 处理。
    for (int i = 0; i < inSize / 2 + 1; i++) {
        out[i] = (in[i * 2] | ((in[i * 2 + 1]) << 8));
    }

    // 开始调整音调和音速
    int sampleNumsPerChannel = inSize / audioStream->channelNums / audioStream->bytesPerSample;
    soundTouch->putSamples(out, sampleNumsPerChannel);
    int adjustSampleNumsPerChannel = soundTouch->receiveSamples(out, sampleNumsPerChannel);

// soundTouch->flush();// TODO 如何处理 flush
// Flushes the last samples from the processing pipeline to the output.
// Clears also the internal processing buffers.
//
// Note: This function is meant for extracting the last samples of a sound
// stream. This function may introduce additional blank samples in the end
// of the sound stream, and thus it's not recommended to call this function
// in the middle of a sound stream.

    return audioStream->channelNums * adjustSampleNumsPerChannel * 2;// SoundTouch 是 16bit 编码
}

/**
 * 关于“分贝与负分贝数”
 * 一开始，用声强 Pa 表示声音的强弱，但发现与我们耳朵的感觉相差太大，后来换成了分贝来表示声音的强弱，叫做响度。
 * 汽车声音 0.2Pa，对应为 80dB，发令枪声 7000Pa，对应为 171dB，此时看发令枪的声音就大约是汽车声的两倍了，
 * 这比较符合人的听觉感受。
 *
 * 帕转换为分贝要用到数学中的对数知识，公式：20 * log10{ Pa / [2 * 10^(-5)] }
 *
 * 人耳能感觉到的最低声压为 2×10E-5Pa，把这一声压级定为 0dB，当声压超过 130dB 时人耳将无法忍受，
 * 故人耳听觉的动态范围为 0～130dB。
 * 声强小于 2×10E-5Pa 的声音响度的都为负分贝数了。就像开尔文温标转化为摄氏温标一样，开尔文温标没有负数，
 * 摄氏温标就有负数了。例如，冬天哈尔滨室外温度 -37℃，这个负数温度也是有温度的，只是温度低而已。
 */
void WeAudio::updatePCM16bitDB(char *data, int dataBytes) {
    // 虽然我们在重采样设置统一转换为 16bit 采样，但是 FFmpeg 重采样转换接收 buffer 是 8 位的，
    // 因此这里要把两个 8 位转成 16 位的数据再计算振幅
    short int amplitude = 0;// 16bit 采样值
    double amplitudeSum = 0;// 16bit 采样值加和
    for (int i = 0; i < dataBytes; i += 2) {
        memcpy(&amplitude, data + i, 2);// 把 char（8位） 转为 short int（16位）
        amplitudeSum += abs(amplitude);// 把这段时间的所有采样值加和
    }
    // 更新振幅平均值
    amplitudeAvg = amplitudeSum / (dataBytes / 2);// 除数是 16 位采样点个数

    // 更新分贝值：分贝 = 20 * log10(振幅)
    if (amplitudeAvg > 0) {
        soundDecibels = 20 * log10(amplitudeAvg);
    } else {
        soundDecibels = 0;
    }
    if (LOG_REPEAT_DEBUG) {
        LOGD(LOG_TAG, "amplitudeAvg %lf, soundDecibels %lf", amplitudeAvg, soundDecibels);
    }
}

void WeAudio::releaseAvPacket() {
    if (avPacket == NULL) {
        return;
    }
    av_packet_free(&avPacket);
//    av_free(avPacket);
    av_freep(&avPacket);// 使用 av_freep(&buf) 代替 av_free(buf)
    avPacket = NULL;
}

void WeAudio::releaseAvFrame() {
    if (avFrame == NULL) {
        return;
    }
    av_frame_free(&avFrame);
//    av_free(avFrame);
    av_freep(&avFrame);// 使用 av_freep(&buf) 代替 av_free(buf)
    avFrame = NULL;
}

int WeAudio::getChannelNums() {
    return audioStream->channelNums;
}

SLuint32 WeAudio::getOpenSLSampleRate() {
    return OpenSLPlayer::convertToOpenSLSampleRate(audioStream->sampleRate);
}

int WeAudio::getBitsPerSample() {
    return audioStream->bytesPerSample * 8;
}

SLuint32 WeAudio::getOpenSLChannelLayout() {
    return OpenSLPlayer::ffmpegToOpenSLChannelLayout(AudioStream::SAMPLE_OUT_CHANNEL_LAYOUT);
}

void WeAudio::stopPlay() {
    // 停止播放回调
    if (openSlPlayer != NULL) {
        openSlPlayer->stopPlay();
    } else {
        LOGE(LOG_TAG, "Invoke stopPlay but openSlPlayer is NULL!");
    }

    if (audioConsumerThread != NULL) {
        audioConsumerThread->clearMessage();// 清除还未执行的播放请求消息
    }
}

void WeAudio::setRecordPCMFlag(bool record) {
    needRecordPCM = record;
}

void WeAudio::destroyPlayer() {
    if (openSlPlayer != NULL) {
        openSlPlayer->destroyPlayer();
    } else {
        LOGE(LOG_TAG, "Invoke destroyPlayer but openSlPlayer is NULL!");
    }
}

bool WeAudio::workFinished() {
    if (openSlPlayer != NULL) {
        return openSlPlayer->enqueueFinished;
    }
    return true;
}

void WeAudio::clearDataAfterStop() {
    if (queue != NULL) {
        queue->clearQueue();// 消除音频数据缓存
    }

    if (sampleBuffer != NULL) {// 不同数据流使用的采样 buffer 不一样，需要销毁新建
//        av_free(sampledBuffer);
        av_freep(&sampleBuffer);// 使用 av_freep(&buf) 代替 av_free(buf)
        sampleBuffer = NULL;
    }

    if (soundTouchBuffer != NULL) {
        free(soundTouchBuffer);
        soundTouchBuffer = NULL;
    }

    if (soundTouch != NULL) {
        soundTouch->clear();
    }

    // 后释放音频流
    releaseStream();

    releaseAvPacket();
    releaseAvFrame();

    currentFrameTime = 0;
    playTimeSecs = 0;
}

void WeAudio::releaseStream() {
    if (audioStream != NULL) {
        delete audioStream;
        audioStream = NULL;
    }
}

double WeAudio::getPlayTimeSecs() {
    return playTimeSecs;
}

void WeAudio::setSeekTime(int secs) {
    currentFrameTime = secs;
    playTimeSecs = secs;
}

void WeAudio::setVolume(float percent) {
    if (openSlPlayer != NULL) {
        openSlPlayer->setVolume(percent);
    }
}

float WeAudio::getVolume() {
    if (openSlPlayer != NULL) {
        return openSlPlayer->getVolume();
    }
    return 0;
}

void WeAudio::setSoundChannel(int channel) {
    if (openSlPlayer != NULL) {
        openSlPlayer->setSoundChannel(channel);
    }
}

void WeAudio::setPitch(float pitch) {
    this->pitch = pitch;
    if (soundTouch != NULL) {
        soundTouch->setPitch(pitch);
    }
}

float WeAudio::getPitch() {
    return pitch;
}

void WeAudio::setTempo(float tempo) {
    this->tempo = tempo;
    if (soundTouch != NULL) {
        soundTouch->setTempo(tempo);
    }
}

float WeAudio::getTempo() {
    return tempo;
}

double WeAudio::getSoundDecibels() {
    return soundDecibels;
}

void WeAudio::release() {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "release...");
    }
    stopPlay();// 首先停止播放，也就停止了消费者从队列里取数据

    if (openSlPlayer != NULL) {
        delete openSlPlayer;// 启动其析构函数回收内部资源
        openSlPlayer = NULL;
    }

    destroyConsumerThread();

    if (queue != NULL) {
        delete queue;// 启动其析构函数回收内部资源
        queue = NULL;
    }

    if (sampleBuffer != NULL) {
//        av_free(sampledBuffer);
        av_freep(&sampleBuffer);// 使用 av_freep(&buf) 代替 av_free(buf)
        sampleBuffer = NULL;
    }

    if (soundTouchBuffer != NULL) {
        free(soundTouchBuffer);
        soundTouchBuffer = NULL;
    }

    if (soundTouch != NULL) {
        soundTouch->clear();
        delete soundTouch;
        soundTouch = NULL;
    }

    releaseAvPacket();
    releaseAvFrame();

    // 最顶层 WeFFmpeg 负责回收 javaListenerContainer，这里只把本指针置空
    javaListenerContainer = NULL;

    // 最顶层 WeFFmpeg 负责回收 status，这里只把本指针置空
    status == NULL;
}

void WeAudio::destroyConsumerThread() {
    if (audioConsumerThread != NULL) {
        audioConsumerThread->shutdown();
        delete audioConsumerThread;
        audioConsumerThread = NULL;
    }
}