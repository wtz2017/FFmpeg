//
// Created by WTZ on 2019/12/23.
//

#include "WeAudioDecoder.h"

WeAudioDecoder::WeAudioDecoder(AVPacketQueue *queue) {
    this->queue = queue;
    pthread_mutex_init(&decodeMutex, NULL);
}

WeAudioDecoder::~WeAudioDecoder() {
    stop();
    releaseStream();

    queue = NULL;
    pthread_mutex_destroy(&decodeMutex);
}

void WeAudioDecoder::initStream(AudioStream *audioStream) {
    this->audioStream = audioStream;

    // 使用 1 秒的采样字节数作为缓冲区大小，音频流采样参数不一样，缓冲区大小也不一样，就需要新建 buffer
    sampleBuffer = static_cast<uint8_t *>(av_malloc(audioStream->sampledSizePerSecond));
}

void WeAudioDecoder::releaseStream() {
    if (queue != NULL) {
        queue->clearQueue();
    }

    if (sampleBuffer != NULL) {// 不同数据流使用的采样 buffer 不一样，需要销毁新建
//        av_free(sampledBuffer);
        av_freep(&sampleBuffer);// 使用 av_freep(&buf) 代替 av_free(buf)
        sampleBuffer = NULL;
    }

    if (soundTouchBuffer != NULL) {// 不同数据流使用的采样 buffer 不一样，需要销毁新建
        free(soundTouchBuffer);
        soundTouchBuffer = NULL;
    }
    if (soundTouch != NULL) {// 不同数据流使用的采样 soundTouch 不一样，需要销毁新建
        soundTouch->clear();
        delete soundTouch;
        soundTouch = NULL;
    }

    releaseAvPacket();
    releaseAvFrame();

    audioStream = NULL;// 这里只置空，由真正 new 的地方去 delete

    currentFrameTime = 0;
    sampleTimeSecs = 0;
    amplitudeAvg = 0;
    soundDecibels = 0;
}

void WeAudioDecoder::start() {
    queue->setAllowOperation(true);
}

void WeAudioDecoder::stop() {
    queue->setAllowOperation(false);
}

void WeAudioDecoder::flushCodecBuffers() {
    pthread_mutex_lock(&decodeMutex);
    avcodec_flush_buffers(audioStream->codecContext);
    pthread_mutex_unlock(&decodeMutex);
}

/**
 * 从队列中取 AVPacket 解码生成 PCM 数据
 *
 * @return >0：sampled bytes；-1：数据加载中；-2：已经播放到末尾；-3：取包异常；
 * -4：发送解码失败；-5：接收解码数据帧失败；-6：重采样失败；-7：调音失败；
 */
int WeAudioDecoder::getPcmData(void **buf) {
    int ret = 0;

    if (readAllFramesComplete) {
        // 前一个包解出的帧都读完后，再从队列中取新的 AVPacket
        if ((ret = getPacket()) != 0) {
            return ret;
        }

        // 发送 AVPacket 给解码器解码
        if (!sendPacket()) {
            // 发送失败直接取下一个包，不用等待
            return -4;
        }
    }

    // 取解码后的 AVFrame 帧，可能一个 AVPacket 对应多个 AVFrame，例如 ape 类型的音频
    if (!receiveFrame()) {
        // 取帧失败直接取下一个包解码，不用等待
        readAllFramesComplete = true;
        return -5;
    } else {
        // 取帧成功，可能这个包里还有帧
        readAllFramesComplete = false;
    }

    // 对解出来的 AVFrame 重采样
    ret = resample(&sampleBuffer);
    if (ret < 0) {
        return -6;
    }

    if (pitch != NORMAL_PITCH || tempo != NORMAL_TEMPO) {
        // 需要变调、变速
        if (soundTouchBuffer == NULL || soundTouch == NULL) {
            initSoundTouch();
        }

        ret = adjustPitchTempo(sampleBuffer, ret, soundTouchBuffer);
        if (ret <= 0) {
            // 调音失败直接取下一帧，不用等待
            return -7;
        }

        *buf = soundTouchBuffer;
    } else {
        *buf = sampleBuffer;
    }

    updatePCM16bitDB(reinterpret_cast<char *>(*buf), ret);

    return ret;
}

bool WeAudioDecoder::readAllDataComplete() {
    return readAllPacketComplete && readAllFramesComplete;
}

/**
 * 从队列中取 AVPacket
 *
 * @return 0：取包成功；-1：数据加载中；-2：已经播放到末尾；-3：取包异常
 */
int WeAudioDecoder::getPacket() {
    if (queue->getQueueSize() == 0) {
        // 队列中无数据
        if (queue->isProductDataComplete()) {
            // 已经播放到末尾
            readAllPacketComplete = true;
            LOGW(LOG_TAG, "Get all packet complete from queue");
            return -2;
        }
        readAllPacketComplete = false;

        // 队列中无数据且生产数据未完成，表示正在加载中
        return -1;
    }
    readAllPacketComplete = false;

    // 从队列中取出 packet
    avPacket = av_packet_alloc();
    if (!queue->getAVpacket(avPacket)) {
        LOGE(LOG_TAG, "getAVpacket from queue failed");
        releaseAvPacket();
        return -3;
    }

    return 0;
}

bool WeAudioDecoder::sendPacket() {
    pthread_mutex_lock(&decodeMutex);
    int ret = avcodec_send_packet(audioStream->codecContext, avPacket);
    pthread_mutex_unlock(&decodeMutex);

    releaseAvPacket();// 不管解码成功与否，都要先释放内存
    if (ret != 0) {
        LOGE(LOG_TAG, "avcodec_send_packet occurred exception: %d %s", ret,
             WeUtils::getAVErrorName(ret));
        return false;
    }

    return true;
}

bool WeAudioDecoder::receiveFrame() {
    avFrame = av_frame_alloc();
    pthread_mutex_lock(&decodeMutex);
    int ret = avcodec_receive_frame(audioStream->codecContext, avFrame);
    pthread_mutex_unlock(&decodeMutex);

    if (ret != 0) {
        if (ret != AVERROR(EAGAIN)) {
            // 之所以屏蔽打印 EAGAIN 是因为考虑一包多帧时尝试多读一次来判断是否还有帧，避免频繁打印 EAGAIN
            LOGE(LOG_TAG, "avcodec_receive_frame: %d %s", ret, WeUtils::getAVErrorName(ret));
        }
        releaseAvFrame();
        return false;
    }

    return true;
}

int WeAudioDecoder::resample(uint8_t **out) {
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
    int ret = 0;
    if ((ret = swr_init(swrContext)) < 0) {
        LOGE(LOG_TAG, "swr_init occurred exception: %d %s", ret, WeUtils::getAVErrorName(ret));
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
    updateTime(avFrame->pts, sampleDataBytes);
    if (LOG_REPEAT_DEBUG) {
        LOGD(LOG_TAG, "resample data size bytes: %d", sampleDataBytes);
    }

    releaseAvFrame();
    swr_free(&swrContext);
    swrContext = NULL;

    return sampleDataBytes;
}

void WeAudioDecoder::updateTime(int64_t pts, int sampleDataBytes) {
    currentFrameTime = pts * av_q2d(audioStream->streamTimeBase);
    if (currentFrameTime < sampleTimeSecs) {
        // avFrame->pts maybe 0
        currentFrameTime = sampleTimeSecs;
    }
    // 实际采样时间 = 当前帧时间 + 本帧实际采样字节数占 1 秒理论采样总字节数的比例
    sampleTimeSecs =
            currentFrameTime + (sampleDataBytes / (double) audioStream->sampledSizePerSecond);
    if (audioStream->duration > 0 && sampleTimeSecs > audioStream->duration) {
        // 测试发现 ape 音频文件 seek 到末尾后再播放时计算会大于总时长 1s 左右
        sampleTimeSecs = audioStream->duration;
    }
}

void WeAudioDecoder::initSoundTouch() {
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

/**
 * 调整音调、音速
 *
 * @param in 需要调整的数据 buffer
 * @param inSize 需要调整的数据大小
 * @param out 接收调整后的数据 buffer
 * @return <= 0 if failed, or adjusted bytes
 */
int WeAudioDecoder::adjustPitchTempo(uint8_t *in, int inSize, SAMPLETYPE *out) {
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

// soundTouch->flush();
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
void WeAudioDecoder::updatePCM16bitDB(char *data, int dataBytes) {
    if (dataBytes <= 0) {
        amplitudeAvg = 0;
        soundDecibels = 0;
        return;
    }
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

void WeAudioDecoder::releaseAvPacket() {
    if (avPacket == NULL) {
        return;
    }
    av_packet_free(&avPacket);
//    av_free(avPacket);
    av_freep(&avPacket);// 使用 av_freep(&buf) 代替 av_free(buf)
    avPacket = NULL;
}

void WeAudioDecoder::releaseAvFrame() {
    if (avFrame == NULL) {
        return;
    }
    av_frame_free(&avFrame);
//    av_free(avFrame);
    av_freep(&avFrame);// 使用 av_freep(&buf) 代替 av_free(buf)
    avFrame = NULL;
}

int WeAudioDecoder::getChannelNums() {
    if (audioStream == NULL) {
        LOGE(LOG_TAG, "getChannelNums but audioStream is NULL");
        return 0;
    }
    return audioStream->channelNums;
}

int WeAudioDecoder::getSampleRate() {
    if (audioStream == NULL) {
        LOGE(LOG_TAG, "getSampleRate but audioStream is NULL");
        return 0;
    }
    return audioStream->sampleRate;
}

int WeAudioDecoder::getBitsPerSample() {
    if (audioStream == NULL) {
        LOGE(LOG_TAG, "getBitsPerSample but audioStream is NULL");
        return 0;
    }
    return audioStream->bytesPerSample * 8;
}

int WeAudioDecoder::getSampledSizePerSecond() {
    if (audioStream == NULL) {
        LOGE(LOG_TAG, "getSampledSizePerSecond but audioStream is NULL");
        return 0;
    }
    return audioStream->sampledSizePerSecond;
}

double WeAudioDecoder::getCurrentTimeSecs() {
    return sampleTimeSecs;
}

void WeAudioDecoder::setSeekTime(double secs) {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "setSeekTime %lf", secs);
    }
    currentFrameTime = secs;
    sampleTimeSecs = secs;
}

void WeAudioDecoder::setPitch(float pitch) {
    this->pitch = pitch;
    if (soundTouch != NULL) {
        soundTouch->setPitch(pitch);
    }
}

float WeAudioDecoder::getPitch() {
    return pitch;
}

void WeAudioDecoder::setTempo(float tempo) {
    this->tempo = tempo;
    if (soundTouch != NULL) {
        soundTouch->setTempo(tempo);
    }
}

float WeAudioDecoder::getTempo() {
    return tempo;
}

double WeAudioDecoder::getSoundDecibels() {
    return soundDecibels;
}
