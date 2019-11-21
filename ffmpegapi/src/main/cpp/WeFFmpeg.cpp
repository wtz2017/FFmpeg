//
// Created by WTZ on 2019/11/19.
//

#include "AndroidLog.h"
#include "WeFFmpeg.h"
#include "WeAudio.h"

WeFFmpeg::WeFFmpeg(JavaListener *preparedListener) {
    this->preparedListener = preparedListener;
    status = new PlayStatus();
}

WeFFmpeg::~WeFFmpeg() {
    delete dataSource;
    dataSource = NULL;

    delete preparedListener;
    preparedListener = NULL;

    delete pFormatCtx;
    pFormatCtx = NULL;

    delete weAudio;
    weAudio = NULL;

    status->setStatus(PlayStatus::STOPPED);
    delete status;
    status == NULL;
}

void WeFFmpeg::setDataSource(char *dataSource) {
    this->dataSource = dataSource;
}

void *prepareThreadCall(void *data) {
    WeFFmpeg *weFFmpeg = (WeFFmpeg *) data;
    weFFmpeg->_prepareAsync();
    pthread_exit(&weFFmpeg->prepareThread);
}

void WeFFmpeg::prepareAsync() {
    status->setStatus(PlayStatus::PREPARING);
    // 线程创建时入口函数必须是全局函数或者某个类的静态成员函数
    pthread_create(&prepareThread, NULL, prepareThreadCall, this);
}

void WeFFmpeg::_prepareAsync() {
    // 注册解码器并初始化网络
    av_register_all();
    avformat_network_init();

    // 打开文件或网络流
    pFormatCtx = avformat_alloc_context();// TODO pFormatCtx 是否可以复用？？？
    if (avformat_open_input(&pFormatCtx, dataSource, NULL, NULL) != 0) {
        LOGE(LOG_TAG, "Can't open data source: %s", dataSource);
        // 报错的原因可能有：打开网络流时无网络权限，打开本地流时无外部存储访问权限
        status->setStatus(PlayStatus::ERROR);
        return;
    }

    // 查找流信息
    if (avformat_find_stream_info(pFormatCtx, NULL) < 0) {
        LOGE(LOG_TAG, "Can't find stream info from: %s", dataSource);
        status->setStatus(PlayStatus::ERROR);
        return;
    }

    // 清除音频旧数据
    if (weAudio != NULL) {
        weAudio->streamIndex = -1;
        delete weAudio->codecParams;
        weAudio->codecParams = NULL;
    }
    // 从流信息中遍历查找音频流
    for (int i = 0; i < pFormatCtx->nb_streams; i++) {
        if (pFormatCtx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            if (LOG_DEBUG) {
                LOGD(LOG_TAG, "Find audio stream info index: %d", i);
            }
            // 保存音频流信息
            if (weAudio == NULL) {
                weAudio = new WeAudio(status);
            }
            weAudio->streamIndex = i;
            weAudio->codecParams = pFormatCtx->streams[i]->codecpar;
            break;
        }
    }

    if (weAudio == NULL || weAudio->streamIndex == -1) {
        LOGE(LOG_TAG, "Can't find audio stream from: %s", dataSource);
        status->setStatus(PlayStatus::ERROR);
        return;
    }

    // 根据 AVCodecID 查找解码器
    AVCodecID codecId = weAudio->codecParams->codec_id;
    AVCodec *decoder = avcodec_find_decoder(codecId);
    if (!decoder) {
        LOGE(LOG_TAG, "Can't find decoder for codec id %d", codecId);
        status->setStatus(PlayStatus::ERROR);
        return;
    }

    // 利用解码器创建解码器上下文 AVCodecContext，并初始化默认值
    weAudio->codecContext = avcodec_alloc_context3(decoder);
    if (!weAudio->codecContext) {
        LOGE(LOG_TAG, "Can't allocate an AVCodecContext for codec id %d", codecId);
        status->setStatus(PlayStatus::ERROR);
        return;
    }

    // 把前边获取的音频流编解码参数填充到 AVCodecContext
    if (avcodec_parameters_to_context(weAudio->codecContext, weAudio->codecParams) < 0) {
        LOGE(LOG_TAG, "Can't fill the AVCodecContext by AVCodecParameters for codec id %d",
             codecId);
        status->setStatus(PlayStatus::ERROR);
        return;
    }

    // 使用给定的 AVCodec 初始化 AVCodecContext
    if (avcodec_open2(weAudio->codecContext, decoder, 0) != 0) {
        LOGE(LOG_TAG,
             "Can't initialize the AVCodecContext to use the given AVCodec for codec id %d",
             codecId);
        status->setStatus(PlayStatus::ERROR);
        return;
    }

    status->setStatus(PlayStatus::PREPARED);

    // 回调初始化准备完成
    preparedListener->callback(1, dataSource);
}

void *decodeThreadCall(void *data) {
    WeFFmpeg *weFFmpeg = (WeFFmpeg *) data;
    weFFmpeg->_start();
    pthread_exit(&weFFmpeg->decodeThread);
}

void WeFFmpeg::start() {
    // 线程创建时入口函数必须是全局函数或者某个类的静态成员函数
    pthread_create(&decodeThread, NULL, decodeThreadCall, this);
}

void WeFFmpeg::_start() {
    if (weAudio == NULL) {
        LOGE(LOG_TAG, "_start but weAudio is NULL");
        status->setStatus(PlayStatus::ERROR);
        return;
    }

    status->setStatus(PlayStatus::PLAYING);

    int frameCount = 0;
    AVPacket *avPacket = NULL;
    while (1) {
        // Allocate an AVPacket
        avPacket = av_packet_alloc();
        // 读取音频帧到 AVPacket
        if (av_read_frame(pFormatCtx, avPacket) == 0) {
            if (avPacket->stream_index == weAudio->streamIndex) {
                // 音频解码操作
                frameCount++;
                if (LOG_DEBUG) {
                    LOGD(LOG_TAG, "Audio decode frame %d", frameCount);
                }
                weAudio->queue->putAVpacket(avPacket);
            } else {
                // 不是音频就释放内存
                av_packet_free(&avPacket);
                av_free(avPacket);
            }
        } else {
            if (LOG_DEBUG) {
                LOGD(LOG_TAG, "Audio decode finished");
            }
            // 减少 avPacket 对 packet 数据的引用计数
            av_packet_free(&avPacket);
            // 释放 avPacket 结构体本身
            av_free(avPacket);
            avPacket = NULL;
            break;
        }
    }

    // TODO ------回调确认播放完成 status->setStatus(PlayStatus::STOPPED);

    //模拟出队
    while (weAudio->queue->getQueueSize() > 0) {
        AVPacket *packet = av_packet_alloc();
        weAudio->queue->getAVpacket(packet);
        av_packet_free(&packet);
        av_free(packet);
        packet = NULL;
    }
}
