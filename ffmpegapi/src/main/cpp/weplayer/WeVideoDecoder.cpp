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
}

void WeVideoDecoder::releaseStream() {
    if (queue != NULL) {
        queue->clearQueue();
    }

    releaseFormatConverter();
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

/**
 * 从队列中取 AVPacket 解码生成 YUV 数据
 *
 * @return >0：成功；-1：数据加载中；-2：已经播放到末尾；-3：取包异常；
 * -4：发送解码失败；-5：接收解码数据帧失败；-6：解析 YUV 失败；
 */
int WeVideoDecoder::getYUVData(OnYUVDataCall *onYuvDataCall) {
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

    if (!parseYUV(onYuvDataCall)) {
        ret = -6;
    }

    return ret;
}

bool WeVideoDecoder::readAllDataComplete() {
    return readAllPacketComplete && readAllFramesComplete && parseYUVComplete;
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

bool WeVideoDecoder::parseYUV(OnYUVDataCall *onYuvDataCall) {
    int ret = true;
    parseYUVComplete = false;
    if (avFrame->format == TARGET_AV_PIXEL_FORMAT) {
        onYuvDataCall->callback(5, videoStream->width, videoStream->height,
                                avFrame->data[0], avFrame->data[1], avFrame->data[2]);
    } else {
        // need convert
        if (swsContext == NULL) {
            initFormatConverter();
        }
        if (swsContext != NULL) {
            sws_scale(
                    swsContext,
                    avFrame->data,// srcSlice[]
                    avFrame->linesize,// srcStride[]
                    0,// srcSliceY 从 slice 第几行开始处理
                    avFrame->height,// srcSliceH 即 slice 的总行数
                    convertFrame->data,// dst[]
                    convertFrame->linesize// dstStride[]
            );
            onYuvDataCall->callback(5,
                                    videoStream->width,
                                    videoStream->height,
                                    convertFrame->data[0],
                                    convertFrame->data[1],
                                    convertFrame->data[2]);
        } else {
            ret = false;
        }
    }

    releaseAvFrame();
    parseYUVComplete = true;
    return ret;
}

bool WeVideoDecoder::initFormatConverter() {
    convertFrame = av_frame_alloc();
    int pixelBufSize = av_image_get_buffer_size(
            TARGET_AV_PIXEL_FORMAT,
            videoStream->width,
            videoStream->height,
            LINE_SIZE_ALIGN);
    convertBuffer = static_cast<uint8_t *>(av_malloc(pixelBufSize * sizeof(uint8_t)));
    // Setup the data pointers and linesizes based on the specified image parameters
    // and the provided array.
    int ret = av_image_fill_arrays(
            convertFrame->data,
            convertFrame->linesize,
            convertBuffer,
            TARGET_AV_PIXEL_FORMAT,
            videoStream->width,
            videoStream->height,
            LINE_SIZE_ALIGN);
    if (ret < 0) {
        LOGE(LOG_TAG, "av_image_fill_arrays failed return code %d", ret);
        releaseFormatConverter();
        return false;
    }

    swsContext = sws_getContext(
            videoStream->width,
            videoStream->height,
            videoStream->codecContext->pix_fmt,// 视频原始格式
            videoStream->width,
            videoStream->height,
            TARGET_AV_PIXEL_FORMAT,// 视频目标转换格式
            SWS_BICUBIC,// 双三次插值算法
            NULL, NULL, NULL);
    if (swsContext == NULL) {
        LOGE(LOG_TAG, "sws_getContext failed", ret);
        releaseFormatConverter();
        return false;
    }

    return true;
}

void WeVideoDecoder::releaseFormatConverter() {
    if (convertFrame != NULL) {
        av_frame_free(&convertFrame);
        av_freep(&convertFrame);
        convertFrame = NULL;
    }
    if (convertBuffer != NULL) {
        av_freep(&convertBuffer);
        convertBuffer = NULL;
    }
    if (swsContext != NULL) {
        sws_freeContext(swsContext);
        swsContext = NULL;
    }
}

void WeVideoDecoder::releaseAvPacket() {
    if (avPacket == NULL) {
        return;
    }
    av_packet_free(&avPacket);
    av_freep(&avPacket);
    avPacket = NULL;
}

void WeVideoDecoder::releaseAvFrame() {
    if (avFrame == NULL) {
        return;
    }
    av_frame_free(&avFrame);
    av_freep(&avFrame);
    avFrame = NULL;
}
