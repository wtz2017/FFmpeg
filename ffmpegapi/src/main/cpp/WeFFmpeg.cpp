//
// Created by WTZ on 2019/11/19.
//

#include "WeFFmpeg.h"

WeFFmpeg::WeFFmpeg(JavaListenerContainer *javaListenerContainer) {
    this->javaListenerContainer = javaListenerContainer;
    status = new PlayStatus();
}

WeFFmpeg::~WeFFmpeg() {
    release();
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

/**
 * AVFormatContext 操作过程中阻塞时中断回调处理函数
 */
int formatCtxInterruptCallback(void *context) {
    if (LOG_REPEAT_DEBUG) {
        LOGD("WeFFmpeg", "formatCtxInterruptCallback...");
    }
    WeFFmpeg *weFFmpeg = (WeFFmpeg *)context;
    if (weFFmpeg->status == NULL || weFFmpeg->status->isStoped() || weFFmpeg->status->isError()) {
        LOGW("WeFFmpeg", "formatCtxInterruptCallback return AVERROR_EOF");
        return AVERROR_EOF;
    }
    return 0;
}

void WeFFmpeg::prepareAsync() {
    // 判断只有 stoped 状态才可以往下走，避免之前启动未回收内存
    if (status == NULL || !status->isStoped()) {
        LOGE(LOG_TAG, "Can't call prepare before stop!");
        workFinished = true;
        status->setStatus(PlayStatus::ERROR, LOG_TAG);
        return;
    }

    status->setStatus(PlayStatus::PREPARING, LOG_TAG);

    // 注册解码器并初始化网络
    av_register_all();
    avformat_network_init();

    pFormatCtx = avformat_alloc_context();
    pFormatCtx->interrupt_callback.callback = formatCtxInterruptCallback;
    pFormatCtx->interrupt_callback.opaque = this;

    // 打开文件或网络流
    if (avformat_open_input(&pFormatCtx, dataSource, NULL, NULL) != 0) {
        LOGE(LOG_TAG, "Can't open data source: %s", dataSource);
        // 报错的原因可能有：打开网络流时无网络权限，打开本地流时无外部存储访问权限
        workFinished = true;
        status->setStatus(PlayStatus::ERROR, LOG_TAG);
        return;
    }

    // 查找流信息
    if (avformat_find_stream_info(pFormatCtx, NULL) < 0) {
        LOGE(LOG_TAG, "Can't find stream info from: %s", dataSource);
        workFinished = true;
        status->setStatus(PlayStatus::ERROR, LOG_TAG);
        return;
    }

    // 从流信息中遍历查找音频流
    for (int i = 0; i < pFormatCtx->nb_streams; i++) {
        if (LOG_DEBUG) {
            WeUtils::av_dump_format_for_android(pFormatCtx, i, NULL, 0);
        }
        if (pFormatCtx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            if (LOG_DEBUG) {
                LOGD(LOG_TAG, "Find audio stream info index: %d", i);
            }
            // 保存音频流信息
            weAudio = new WeAudio(status, pFormatCtx->streams[i]->codecpar->sample_rate,
                                  javaListenerContainer);
            weAudio->streamIndex = i;
            weAudio->codecParams = pFormatCtx->streams[i]->codecpar;
            weAudio->streamTimeBase = pFormatCtx->streams[i]->time_base;
            weAudio->duration = pFormatCtx->duration * av_q2d(AV_TIME_BASE_Q);
            break;
        }
    }
    if (weAudio == NULL) {
        LOGE(LOG_TAG, "Can't find audio stream from: %s", dataSource);
        workFinished = true;
        status->setStatus(PlayStatus::ERROR, LOG_TAG);
        return;
    }

    // 根据 AVCodecID 查找解码器
    AVCodecID codecId = weAudio->codecParams->codec_id;
    AVCodec *decoder = avcodec_find_decoder(codecId);
    if (!decoder) {
        LOGE(LOG_TAG, "Can't find decoder for codec id %d", codecId);
        workFinished = true;
        status->setStatus(PlayStatus::ERROR, LOG_TAG);
        return;
    }

    // 利用解码器创建解码器上下文 AVCodecContext，并初始化默认值
    weAudio->codecContext = avcodec_alloc_context3(decoder);
    if (!weAudio->codecContext) {
        LOGE(LOG_TAG, "Can't allocate an AVCodecContext for codec id %d", codecId);
        workFinished = true;
        status->setStatus(PlayStatus::ERROR, LOG_TAG);
        return;
    }

    // 把前边获取的音频流编解码参数填充到 AVCodecContext
    if (avcodec_parameters_to_context(weAudio->codecContext, weAudio->codecParams) < 0) {
        LOGE(LOG_TAG, "Can't fill the AVCodecContext by AVCodecParameters for codec id %d",
             codecId);
        workFinished = true;
        status->setStatus(PlayStatus::ERROR, LOG_TAG);
        return;
    }

    // 使用给定的 AVCodec 初始化 AVCodecContext
    if (avcodec_open2(weAudio->codecContext, decoder, 0) != 0) {
        LOGE(LOG_TAG,
             "Can't initialize the AVCodecContext to use the given AVCodec for codec id %d",
             codecId);
        workFinished = true;
        status->setStatus(PlayStatus::ERROR, LOG_TAG);
        return;
    }

    if (status == NULL || !status->isPreparing()) {
        LOGE(LOG_TAG, "prepare finished but status isn't PREPARING");
        return;
    }
    status->setStatus(PlayStatus::PREPARED, LOG_TAG);

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "prepare finished to callback java...");
    }
    // 回调初始化准备完成，注意要在 java API 层把回调切换到主线程
    javaListenerContainer->onPreparedListener->callback(1, dataSource);
}

void *demuxThreadCall(void *data) {
    if (LOG_DEBUG) {
        LOGD("WeFFmpeg", "demuxThread run");
    }
    WeFFmpeg *weFFmpeg = (WeFFmpeg *) data;
    weFFmpeg->_demux();
    if (LOG_DEBUG) {
        LOGD(weFFmpeg->LOG_TAG, "demuxThread exit");
    }
    pthread_exit(&weFFmpeg->demuxThread);
}

void WeFFmpeg::startDemuxThread() {
    if (status == NULL || !status->isPrepared()) {
        LOGE(LOG_TAG, "Invoke startDemuxThread but status is not prepared");
        return;
    }
    status->setStatus(PlayStatus::PLAYING, LOG_TAG);
    // 线程创建时入口函数必须是全局函数或者某个类的静态成员函数
    pthread_create(&demuxThread, NULL, demuxThreadCall, this);
}

void WeFFmpeg::_demux() {
    if (weAudio == NULL) {
        LOGE(LOG_TAG, "_demux but weAudio is NULL");
        workFinished = true;
        status->setStatus(PlayStatus::ERROR, LOG_TAG);
        return;
    }

    // WeAudio 模块开启新的线程从 AVPacket 队列里取包、解码、重采样、播放，没有就阻塞等待
    weAudio->startPlayer();

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "Start read AVPacket...");
    }
    // 本线程开始读 AVPacket 包并缓存入队
    int packetCount = 0;
    AVPacket *avPacket = NULL;
    while (status != NULL && !status->isStoped() && !status->isError()) {
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
//                av_free(avPacket);
                av_freep(&avPacket);// 使用 av_freep(&buf) 代替 av_free(buf)
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
//            av_free(avPacket);
            av_freep(&avPacket);// 使用 av_freep(&buf) 代替 av_free(buf)
            avPacket = NULL;

            // 等待队列数据取完后退出，否则造成播放不完整
            while (status != NULL && !status->isStoped() && !status->isError()) {
                if (weAudio->queue->getQueueSize() > 0) {
                    av_usleep(10 * 1000);// 每次睡眠 10 ms
                    continue;
                }

                status->setStatus(PlayStatus::COMPLETED, LOG_TAG);
                // 当队列中数据都取完后，再通知可能正在阻塞等待的消费者线程
                weAudio->queue->informPutFinished();
                // TODO ------回调应用层确认播放完成
                break;
            }
            break;
        }
    }

    workFinished = true;
}

void WeFFmpeg::pause() {
    if (weAudio == NULL) {
        LOGE(LOG_TAG, "pause but weAudio is NULL");
        status->setStatus(PlayStatus::ERROR, LOG_TAG);
        return;
    }

    status->setStatus(PlayStatus::PAUSED, LOG_TAG);
    weAudio->pause();
}

void WeFFmpeg::resumePlay() {
    if (weAudio == NULL) {
        LOGE(LOG_TAG, "resumePlay but weAudio is NULL");
        status->setStatus(PlayStatus::ERROR, LOG_TAG);
        return;
    }

    status->setStatus(PlayStatus::PLAYING, LOG_TAG);
    weAudio->resumePlay();// 要先设置播放状态，才能恢复播放
}

/**
 * Gets the duration of the file.
 *
 * @return the duration in milliseconds
 */
int WeFFmpeg::getDuration() {
    if (weAudio == NULL) {
        LOGE(LOG_TAG, "getDuration but weAudio is NULL");
        // 不涉及到控制，不设置错误状态
        return 0;
    }
    return weAudio->duration * 1000;
}

/**
 * Gets the current playback position.
 *
 * @return the current position in milliseconds
 */
int WeFFmpeg::getCurrentPosition() {
    if (weAudio == NULL) {
        LOGE(LOG_TAG, "getCurrentPosition but weAudio is NULL");
        // 不涉及到控制，不设置错误状态
        return 0;
    }
    int ret = weAudio->currentPlayTime * 1000;
    if (LOG_REPEAT_DEBUG) {
        LOGD(LOG_TAG, "getCurrentPosition: %d", ret);
    }
    return ret;
}

/**
 * setStopFlag 不走 java 调度线程消息队列，直接执行，避免无法立即通知结束工作
 */
void WeFFmpeg::setStopFlag() {
    if (status == NULL || status->isStoped()) {
        LOGE(LOG_TAG, "Call setStopFlag but status is already NULL or stopped: %d", status);
        return;
    }
    status->setStatus(PlayStatus::STOPPED, LOG_TAG);
}

/**
 * 调用 release 之前，先异步调用 setStopFlag；
 * 因为 release 要与 prepare 保持串行，而 prepare 可能会一直阻塞，先异步调用 setStopFlag 结束 prepare
 */
void WeFFmpeg::release() {
    if (status == NULL) {
        LOGE(LOG_TAG, "to release but status is already NULL");
        return;
    }
    // 进一步检查外界是否已经调用了 setStopFlag，否则这里直接设置停止标志
    if (!status->isStoped()) {
        status->setStatus(PlayStatus::STOPPED, LOG_TAG);
    }

    // 等待工作线程结束
    int sleepCount = 0;
    while (!workFinished) {
        if (sleepCount > 300) {
            break;
        }
        if (LOG_DEBUG) {
            LOGD(LOG_TAG, "release wait other thread finished, sleepCount: %d", sleepCount);
        }
        sleepCount++;
        av_usleep(10 * 1000);// 每次睡眠 10 ms
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "start release");
    }
    // 开始释放资源
    if (weAudio != NULL) {
        delete weAudio;
        weAudio = NULL;
    }

    if (pFormatCtx != NULL) {
        avformat_close_input(&pFormatCtx);
        avformat_free_context(pFormatCtx);
        pFormatCtx = NULL;
    }

    delete dataSource;
    dataSource = NULL;

    // 最顶层负责回收 status
    delete status;
    status = NULL;

    // 最顶层负责回收 javaListenerContainer
    delete javaListenerContainer;
    javaListenerContainer = NULL;

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "release finished");
    }
}

