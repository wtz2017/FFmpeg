//
// Created by WTZ on 2020/1/9.
//

#include "WeVideoDecoder.h"

WeVideoDecoder::WeVideoDecoder(AVPacketQueue *queue) {
    this->queue = queue;
    pthread_mutex_init(&decodeMutex, NULL);
}

WeVideoDecoder::~WeVideoDecoder() {
    stop();
    releaseStream();

    queue = NULL;
    pthread_mutex_destroy(&decodeMutex);
}

void WeVideoDecoder::initStream(VideoStream *videoStream) {
    if (videoStream == NULL) {
        return;
    }
    this->videoStream = videoStream;

    //TODO FOR VIDEO BUFFER
    // 使用 1 秒的采样字节数作为缓冲区大小，音频流采样参数不一样，缓冲区大小也不一样，就需要新建 buffer
//    sampleBuffer = static_cast<uint8_t *>(av_malloc(audioStream->sampledSizePerSecond));
}

void WeVideoDecoder::releaseStream() {
    if (queue != NULL) {
        queue->clearQueue();
    }

    //TODO FOR VIDEO BUFFER
//    if (sampleBuffer != NULL) {// 不同数据流使用的采样 buffer 不一样，需要销毁新建
////        av_free(sampledBuffer);
//        av_freep(&sampleBuffer);// 使用 av_freep(&buf) 代替 av_free(buf)
//        sampleBuffer = NULL;
//    }

    releaseAvPacket();
    releaseAvFrame();

    videoStream = NULL;// 这里只置空，由真正 new 的地方去 delete
}

void WeVideoDecoder::start() {
    queue->setAllowOperation(true);
}

void WeVideoDecoder::stop() {
    queue->setAllowOperation(false);
}

void WeVideoDecoder::flushCodecBuffers() {
    pthread_mutex_lock(&decodeMutex);
    avcodec_flush_buffers(videoStream->codecContext);
    pthread_mutex_unlock(&decodeMutex);
}

int WeVideoDecoder::getData(void **buf) {
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

    // TODO VIDEO 处理
    // 对解出来的 AVFrame 重采样
//    ret = resample(&sampleBuffer);
//    if (ret < 0) {
//        return -6;
//    }

    // TODO VIDEO BUFFER 赋值
//    *buf = sampleBuffer;

    return ret;
}

bool WeVideoDecoder::readAllDataComplete() {
    return readAllPacketComplete && readAllFramesComplete;
}

/**
 * 从队列中取 AVPacket
 *
 * @return 0：取包成功；-1：数据加载中；-2：已经播放到末尾；-3：取包异常
 */
int WeVideoDecoder::getPacket() {
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

bool WeVideoDecoder::sendPacket() {
    pthread_mutex_lock(&decodeMutex);
    int ret = avcodec_send_packet(videoStream->codecContext, avPacket);
    pthread_mutex_unlock(&decodeMutex);

    releaseAvPacket();// 不管解码成功与否，都要先释放内存
    if (ret != 0) {
        LOGE(LOG_TAG, "avcodec_send_packet occurred exception: %d %s", ret,
             WeUtils::getAVErrorName(ret));
        return false;
    }

    return true;
}

bool WeVideoDecoder::receiveFrame() {
    avFrame = av_frame_alloc();
    pthread_mutex_lock(&decodeMutex);
    int ret = avcodec_receive_frame(videoStream->codecContext, avFrame);
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

void WeVideoDecoder::releaseAvPacket() {
    if (avPacket == NULL) {
        return;
    }
    av_packet_free(&avPacket);
//    av_free(avPacket);
    av_freep(&avPacket);// 使用 av_freep(&buf) 代替 av_free(buf)
    avPacket = NULL;
}

void WeVideoDecoder::releaseAvFrame() {
    if (avFrame == NULL) {
        return;
    }
    av_frame_free(&avFrame);
//    av_free(avFrame);
    av_freep(&avFrame);// 使用 av_freep(&buf) 代替 av_free(buf)
    avFrame = NULL;
}
