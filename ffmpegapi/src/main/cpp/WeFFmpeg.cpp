//
// Created by WTZ on 2019/11/19.
//

#include "AndroidLog.h"
#include "WeFFmpeg.h"
#include "WeAudio.h"

WeFFmpeg::WeFFmpeg(JavaListenerContainer *javaListenerContainer) {
    this->javaListenerContainer = javaListenerContainer;
    status = new PlayStatus();
}

WeFFmpeg::~WeFFmpeg() {
    delete dataSource;
    dataSource = NULL;

    // 最顶层负责回收 javaListenerContainer
    delete javaListenerContainer;
    javaListenerContainer = NULL;

    delete pFormatCtx;
    pFormatCtx = NULL;

    delete weAudio;
    weAudio = NULL;

    status->setStatus(PlayStatus::STOPPED);
    // 最顶层负责回收 status
    delete status;
    status == NULL;
}

void WeFFmpeg::setDataSource(char *dataSource) {
    if (dataSource == NULL || strlen(dataSource) == 0) {
        LOGE(LOG_TAG, "SetDataSource can't be NULL");
        return;
    }
    // strcmp() 函数不能接受为 NULL 的指针
    if (this->dataSource != NULL && strcmp(dataSource, this->dataSource) == 0) {
        LOGW(LOG_TAG, "SetDataSource is the same with old source");
        return;
    }
    delete this->dataSource;
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

// TODO 如何处理连续换歌调用和资源释放？
void WeFFmpeg::_prepareAsync() {
    // 注册解码器并初始化网络
    av_register_all();
    avformat_network_init();

    if (pFormatCtx != NULL) {// TODO
        avformat_free_context(pFormatCtx);
//        av_free(pFormatCtx);// A/libc: invalid address or address of corrupt block 0x77c2b930 passed to dlfree
//        delete pFormatCtx;
        pFormatCtx = NULL;
    }
    pFormatCtx = avformat_alloc_context();
    // 打开文件或网络流 TODO 何时关闭流
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
    if (weAudio != NULL) {// TODO 是否有必要复用 weAudio ？在连续切换源地址时，应该先stop掉再从头再来
        delete weAudio;
        weAudio = NULL;
    }
    // 从流信息中遍历查找音频流
    for (int i = 0; i < pFormatCtx->nb_streams; i++) {
        if (pFormatCtx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            if (LOG_DEBUG) {
                LOGD(LOG_TAG, "Find audio stream info index: %d", i);
            }
            // 保存音频流信息
            weAudio = new WeAudio(status, pFormatCtx->streams[i]->codecpar->sample_rate,
                                  javaListenerContainer);
            weAudio->streamIndex = i;
            weAudio->codecParams = pFormatCtx->streams[i]->codecpar;
            break;
        }
    }

    if (weAudio == NULL) {
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
    javaListenerContainer->onPreparedListener->callback(1, dataSource);
}

void *decodeThreadCall(void *data) {
    WeFFmpeg *weFFmpeg = (WeFFmpeg *) data;
    weFFmpeg->_start();
    pthread_exit(&weFFmpeg->decodeThread);
}

void WeFFmpeg::start() {
    if (status != NULL && !status->isPrepared()) {
        LOGE(LOG_TAG, "Invoke start but status is not prepared");
        return;
    }
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

    // WeAudio 模块开启新的线程从 AVPacket 队列里取包、解码、重采样、播放，没有就阻塞等待
    weAudio->startPlayer();

    // 本线程开始读 AVPacket 包并缓存入队
    int packetCount = 0;
    AVPacket *avPacket = NULL;
    while (status != NULL && !status->isStoped()) {
        // Allocate an AVPacket
        avPacket = av_packet_alloc();
        // 读取数据包到 AVPacket
        if (av_read_frame(pFormatCtx, avPacket) == 0) {
            if (avPacket->stream_index == weAudio->streamIndex) {
                // 当前包为音频包
                packetCount++;
                if (LOG_REPEAT_DEBUG) {
                    LOGD(LOG_TAG, "Read Audio packet, current count is %d", packetCount);
                }
                // 缓存音频包到队列
                weAudio->queue->putAVpacket(avPacket);
            } else {
                // 不是音频就释放内存
                av_packet_free(&avPacket);
                av_free(avPacket);
                avPacket = NULL;
            }
        } else {
            // 文件读取出错或已经结束
            if (LOG_DEBUG) {
                LOGD(LOG_TAG, "AVPacket read finished");
            }
            // 减少 avPacket 对 packet 数据的引用计数
            av_packet_free(&avPacket);
            // 释放 avPacket 结构体本身
            av_free(avPacket);
            avPacket = NULL;

            // 等待队列数据取完后退出，否则造成播放不完整
            while (status != NULL && !status->isStoped()) {
                if (weAudio->queue->getQueueSize() > 0) {
                    continue;
                }

                status->setStatus(PlayStatus::STOPPED);
                // 当队列中数据都取完后，再通知可能正在阻塞等待的消费者线程
                weAudio->queue->informPutFinished();
                // TODO ------回调应用层确认播放完成
                break;
            }
            break;
        }
    }
}

void WeFFmpeg::pause() {
    if (weAudio == NULL) {
        LOGE(LOG_TAG, "pause but weAudio is NULL");
        status->setStatus(PlayStatus::ERROR);
        return;
    }

    weAudio->pause();
}

void WeFFmpeg::resumePlay() {
    if (weAudio == NULL) {
        LOGE(LOG_TAG, "resumePlay but weAudio is NULL");
        status->setStatus(PlayStatus::ERROR);
        return;
    }

    weAudio->resumePlay();
}
