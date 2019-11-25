//
// Created by WTZ on 2019/11/21.
//

#include "WeAudio.h"

WeAudio::WeAudio(PlayStatus *status, int sampleRate) {
    this->status = status;

    // 初始化采样参数
    this->sampleRate = sampleRate;
    this->channelNums = av_get_channel_layout_nb_channels(SAMPLE_OUT_CHANNEL_LAYOUT);
    this->bytesPerSample = av_get_bytes_per_sample(SAMPLE_OUT_FORMAT);

    this->queue = new AVPacketQueue(status);
}

WeAudio::~WeAudio() {
    if (openSlPlayer != NULL) {
        openSlPlayer->destroy();
        delete openSlPlayer;
        openSlPlayer = NULL;
    }

    if (sampledBuffer != NULL) {
        av_free(sampledBuffer);
        sampledBuffer = NULL;
    }

    releaseAvPacket();
    releaseAvFrame();

    delete codecContext;
    codecContext = NULL;

    delete codecParams;
    codecParams = NULL;

    delete queue;
    queue = NULL;

    delete testSaveFile;
    testSaveFile = NULL;
}

void *playThreadCall(void *data) {
    WeAudio *weAudio = static_cast<WeAudio *>(data);

    if (weAudio->TEST_SAMPLE) {
        weAudio->testSaveFile = fopen(weAudio->TEST_SAVE_FILE_PATH, "w");
    }

    weAudio->_play();

    if (weAudio->TEST_SAMPLE) {
        fclose(weAudio->testSaveFile);
    }

    pthread_exit(&weAudio->playThread);
}

void WeAudio::play() {
    pthread_create(&playThread, NULL, playThreadCall, this);
}

void WeAudio::_play() {
    if (sampledBuffer == NULL) {
        // 使用 1 秒的采样字节数作为缓冲区大小
        int bufferSize = sampleRate * channelNums * bytesPerSample;
        sampledBuffer = static_cast<uint8_t *>(av_malloc(bufferSize));
    }

    if (openSlPlayer == NULL) {
        openSlPlayer = new OpenSLPlayer(this);
    }

    if (!openSlPlayer->init()) {
        LOGE(LOG_TAG, "OpenSLPlayer init failed!");
        return;
    }

    openSlPlayer->start();
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
        // 解 AVPacket 包
        if (!decodeQueuePacket()) {
            continue;
        }

        // 对解出来的 AVFrame 重采样
        ret = resample();
        if (ret < 0) {
            continue;
        }

        break;
    }

    if (ret > 0) {
        *buf = sampledBuffer;
    } else {
        *buf = NULL;
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
    int ret = avcodec_send_packet(codecContext, avPacket);
    releaseAvPacket();// 不管解码成功与否，都要先释放内存
    if (ret != 0) {
        LOGE(LOG_TAG, "avcodec_send_packet occurred exception: %d", ret);
        return false;
    }

    // 接收解码后的数据帧 frame
    avFrame = av_frame_alloc();
    ret = avcodec_receive_frame(codecContext, avFrame);
    if (ret != 0) {
        LOGE(LOG_TAG, "avcodec_receive_frame occurred exception: %d", ret);
        releaseAvFrame();
        return false;
    }

    return true;
}

int WeAudio::resample() {
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
            SAMPLE_OUT_CHANNEL_LAYOUT,/* 声道布局 */
            SAMPLE_OUT_FORMAT,/* 音频采样格式 */
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
        LOGE(LOG_TAG, "swr_alloc_set_opts occurred exception");
        releaseAvFrame();
        swr_free(&swrContext);
        swrContext = NULL;
        return -1;
    }

    // 重采样转换，返回值为每个声道采样个数
    int sampleNumsPerChannel = swr_convert(
            swrContext,
            &sampledBuffer,/* 转换后接收数据的 buffer */
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

    int sampleDataBytes = channelNums * sampleNumsPerChannel * bytesPerSample;
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "resample data size bytes: %d", sampleDataBytes);
    }
    if (TEST_SAMPLE) {
        fwrite(sampledBuffer, 1, sampleDataBytes, testSaveFile);
    }

    releaseAvFrame();
    swr_free(&swrContext);
    swrContext = NULL;

    return sampleDataBytes;
}

void WeAudio::releaseAvPacket() {
    if (avPacket == NULL) {
        return;
    }
    av_packet_free(&avPacket);
    av_free(avPacket);
    avPacket = NULL;
}

void WeAudio::releaseAvFrame() {
    if (avFrame == NULL) {
        return;
    }
    av_frame_free(&avFrame);
    av_free(avFrame);
    avFrame = NULL;
}

int WeAudio::getChannelNums() {
    return channelNums;
}

SLuint32 WeAudio::getOpenSLSampleRate() {
    return OpenSLPlayer::convertToOpenSLSampleRate(sampleRate);
}

int WeAudio::getBitsPerSample() {
    return bytesPerSample * 8;
}

SLuint32 WeAudio::getOpenSLChannelLayout() {
    return OpenSLPlayer::ffmpegToOpenSLChannelLayout(SAMPLE_OUT_CHANNEL_LAYOUT);
}
