//
// Created by WTZ on 2019/11/19.
//

#include <WeAudio.h>
#include "AndroidLog.h"
#include "WeFFmpeg.h"

WeFFmpeg::WeFFmpeg(JavaListener *javaListener) {
    this->preparedListener = javaListener;
}

WeFFmpeg::~WeFFmpeg() {
    delete dataSource;
    dataSource = NULL;

    delete preparedListener;
    preparedListener = NULL;

    delete pFormatCtx;
    pFormatCtx = NULL;
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
    // 线程创建时入口函数必须是全局函数或者某个类的静态成员函数
    pthread_create(&prepareThread, NULL, prepareThreadCall, this);
}

void WeFFmpeg::_prepareAsync() {
    // 注册解码器并初始化网络
    av_register_all();
    avformat_network_init();

    // 打开文件或网络流
    pFormatCtx = avformat_alloc_context();
    if (avformat_open_input(&pFormatCtx, dataSource, NULL, NULL) != 0) {
        LOGE(LOG_TAG, "Can't open data source: %s", dataSource);
        // 报错的原因可能有：打开网络流时无网络权限，打开本地流时无外部存储访问权限
        return;
    }

    // 查找流信息
    if (avformat_find_stream_info(pFormatCtx, NULL) < 0) {
        LOGE(LOG_TAG, "Can't find stream info from: %s", dataSource);
        return;
    }

    // 从流信息中遍历查找音频流
    for (int i = 0; i < pFormatCtx->nb_streams; i++) {
        if (pFormatCtx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            if (LOG_DEBUG) {
                LOGD(LOG_TAG, "Find audio stream info index: %d", i);
            }
            // 保存音频流信息
            if (weAudio == NULL) {
                weAudio = new WeAudio();
            }
            weAudio->streamIndex = i;
            weAudio->codecParams = pFormatCtx->streams[i]->codecpar;
            break;
        }
    }

    // 根据 AVCodecID 查找解码器
    AVCodecID codecId = weAudio->codecParams->codec_id;
    AVCodec *decoder = avcodec_find_decoder(codecId);
    if (!decoder) {
        LOGE(LOG_TAG, "Can't find decoder for codec id %d", codecId);
        return;
    }

    // 利用解码器创建解码器上下文 AVCodecContext，并初始化默认值
    weAudio->codecContext = avcodec_alloc_context3(decoder);
    if (!weAudio->codecContext) {
        LOGE(LOG_TAG, "Can't allocate an AVCodecContext for codec id %d", codecId);
        return;
    }

    // 把前边获取的音频流编解码参数填充到 AVCodecContext
    if (avcodec_parameters_to_context(weAudio->codecContext, weAudio->codecParams) < 0) {
        LOGE(LOG_TAG, "Can't fill the AVCodecContext by AVCodecParameters for codec id %d",
             codecId);
        return;
    }

    // 使用给定的 AVCodec 初始化 AVCodecContext
    if (avcodec_open2(weAudio->codecContext, decoder, 0) != 0) {
        LOGE(LOG_TAG,
             "Can't initialize the AVCodecContext to use the given AVCodec for codec id %d",
             codecId);
        return;
    }

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
        return;
    }

    int frameCount = 0;
    AVPacket *avPacket = NULL;
    while (1) {
        // Allocate an AVPacket
        avPacket = av_packet_alloc();
        // 读取音频帧到 AVPacket
        if (av_read_frame(pFormatCtx, avPacket) == 0) {
            if (avPacket->stream_index == weAudio->streamIndex) {
                // 模拟解码操作
                frameCount++;
                if (LOG_DEBUG) {
                    LOGD(LOG_TAG, "Audio decode frame %d", frameCount);
                }
            }
            // 释放内存
            av_packet_free(&avPacket);
            av_free(avPacket);
        } else {
            if (LOG_DEBUG) {
                LOGD(LOG_TAG, "Audio decode finished");
            }
            // 释放内存
            av_packet_free(&avPacket);
            av_free(avPacket);
            avPacket = NULL;
            break;
        }
    }
}
