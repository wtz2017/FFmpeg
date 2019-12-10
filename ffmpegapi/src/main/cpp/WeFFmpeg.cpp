//
// Created by WTZ on 2019/11/19.
//

#include "WeFFmpeg.h"

WeFFmpeg::WeFFmpeg(JavaListenerContainer *javaListenerContainer) {
    this->javaListenerContainer = javaListenerContainer;
    init();
}

WeFFmpeg::~WeFFmpeg() {
    release();
}

void WeFFmpeg::init() {
    status = new PlayStatus();
    pthread_mutex_init(&demuxMutex, NULL);

    weAudio = new WeAudio(status, javaListenerContainer);
    int ret;
    if ((ret = weAudio->init()) != NO_ERROR) {
        initSuccess = false;

        pthread_mutex_lock(&status->mutex);
        status->setStatus(PlayStatus::ERROR, LOG_TAG);
        // ！！！注意：这里专门把 java 回调放到锁里，需要 java 层注意不要有其它本地方法调用和耗时操作！！！
        javaListenerContainer->onErrorListener->callback(2, ret, E_NAME_AUDIO_PLAY);
        pthread_mutex_unlock(&status->mutex);
        return;
    }

    // 注册解码器并初始化网络
    av_register_all();
    avformat_network_init();

    // 开启解封装线程
    createDemuxThread();

    initSuccess = true;
}

void demuxThreadHandler(int msgType, void *context) {
    if (LOG_DEBUG) {
        LOGD("WeFFmpeg", "demuxThreadHandler: msgType=%d", msgType);
    }
    WeFFmpeg *weFFmpeg = (WeFFmpeg *) context;
    weFFmpeg->_handleDemuxMessage(msgType);
}

void WeFFmpeg::createDemuxThread() {
    if (demuxThread != NULL) {
        return;
    }
    demuxThread = new LooperThread("DemuxThread", this, demuxThreadHandler);
    demuxThread->create();
}

void WeFFmpeg::_handleDemuxMessage(int msgType) {
    switch (msgType) {
        case MSG_DEMUX_START:
            demux();
            break;
    }
}

void WeFFmpeg::reset() {
    if (!initSuccess) {// 初始化错误不可恢复，或已经释放
        LOGE(LOG_TAG, "Can't reset because initialization failed or released!");
        return;
    }

    stop();

    if (dataSource != NULL) {
        delete[] dataSource;// dataSource 是 JNI 中通过 new char[] 创建的
        dataSource = NULL;
    }

    pthread_mutex_lock(&status->mutex);
    if (status == NULL || status->isReleased()) {
        LOGE(LOG_TAG, "to reset but status is already RELEASED");
        pthread_mutex_unlock(&status->mutex);
        return;
    }
    status->setStatus(PlayStatus::IDLE, LOG_TAG);
    pthread_mutex_unlock(&status->mutex);
}

void WeFFmpeg::setDataSource(char *dataSource) {
    pthread_mutex_lock(&status->mutex);
    if (status == NULL || !status->isIdle()) {
        LOGE(LOG_TAG, "Can't call setDataSource because status is not IDLE!");
        // 调用非法，不是不可恢复的内部工作错误，所以不用设置错误状态
        pthread_mutex_unlock(&status->mutex);
        // 这里不再判空，因为 JNI 层已经做了判空
        delete[] dataSource;// dataSource 是 JNI 中通过 new char[] 创建的
        return;
    }

    if (this->dataSource != NULL) {
        // 先释放旧的 dataSource
        delete[] this->dataSource;// dataSource 是 JNI 中通过 new char[] 创建的
    }
    this->dataSource = dataSource;

    status->setStatus(PlayStatus::INITIALIZED, LOG_TAG);
    pthread_mutex_unlock(&status->mutex);
}

/**
 * AVFormatContext 操作过程中阻塞时中断回调处理函数
 */
int formatCtxInterruptCallback(void *context) {
    if (LOG_REPEAT_DEBUG) {
        LOGD("WeFFmpeg", "formatCtxInterruptCallback...");
    }
    WeFFmpeg *weFFmpeg = (WeFFmpeg *) context;
    if (weFFmpeg->status == NULL || weFFmpeg->status->isStoped() || weFFmpeg->status->isError() ||
        weFFmpeg->status->isReleased()) {
        LOGW("WeFFmpeg", "formatCtxInterruptCallback return AVERROR_EOF");
        return AVERROR_EOF;
    }
    return 0;
}

/**
 * 为新数据源做准备，或者调用过 stop 后再重新做准备
 */
void WeFFmpeg::prepareAsync() {
    prepareFinished = false;

    pthread_mutex_lock(&status->mutex);
    if (status == NULL || (!status->isInitialized() && !status->isStoped())) {
        LOGE(LOG_TAG, "Can't call prepare before Initialized or stoped!");
        // 调用非法，不是不可恢复的内部工作错误，所以不用设置错误状态
        pthread_mutex_unlock(&status->mutex);
        prepareFinished = true;
        return;
    }
    status->setStatus(PlayStatus::PREPARING, LOG_TAG);
    pthread_mutex_unlock(&status->mutex);

    pFormatCtx = avformat_alloc_context();
    pFormatCtx->interrupt_callback.callback = formatCtxInterruptCallback;
    pFormatCtx->interrupt_callback.opaque = this;

    int ret;
    // 打开文件或网络流
    if ((ret = avformat_open_input(&pFormatCtx, dataSource, NULL, NULL)) != 0) {
        LOGE(LOG_TAG, "Can't open data source: %s. \n AVERROR=%d %s", dataSource,
             ret, WeUtils::getAVErrorName(ret));
        // 报错的原因可能有：打开网络流时无网络权限，打开本地流时无外部存储访问权限
        handleErrorOnPreparing(E_CODE_PRP_OPEN_SOURCE);
        prepareFinished = true;
        return;
    }

    // 查找流信息
    if ((ret = avformat_find_stream_info(pFormatCtx, NULL)) < 0) {
        LOGE(LOG_TAG, "Can't find stream info from: %s. \n AVERROR=%d %s", dataSource,
             ret, WeUtils::getAVErrorName(ret));
        handleErrorOnPreparing(E_CODE_PRP_FIND_STREAM);
        prepareFinished = true;
        return;
    }

    if (LOG_DEBUG) {
        WeUtils::av_dump_format_for_android(pFormatCtx, 0, dataSource, 0);
    }

    // 从流信息中遍历查找音频流
    for (int i = 0; i < pFormatCtx->nb_streams; i++) {
        if (pFormatCtx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            if (LOG_DEBUG) {
                LOGD(LOG_TAG, "Find audio stream info index=%d, pFormatCtx->duration=%lld", i,
                     pFormatCtx->duration);
            }
            // 保存音频流信息
            weAudio->audioStream = new AudioStream(
                    i, pFormatCtx->streams[i]->codecpar, pFormatCtx->streams[i]->time_base);
            duration = pFormatCtx->duration * av_q2d(AV_TIME_BASE_Q);
            if (duration < 0) {
                duration = 0;
            }
            break;
        }
    }
    if (weAudio == NULL || weAudio->audioStream == NULL) {
        LOGE(LOG_TAG, "Can't find audio stream from: %s", dataSource);
        handleErrorOnPreparing(E_CODE_PRP_FIND_AUDIO);
        prepareFinished = true;
        return;
    }

    // 根据 AVCodecID 查找解码器
    AVCodecID codecId = weAudio->audioStream->codecParams->codec_id;
    AVCodec *decoder = avcodec_find_decoder(codecId);
    if (!decoder) {
        LOGE(LOG_TAG, "Can't find decoder for codec id %d", codecId);
        handleErrorOnPreparing(E_CODE_PRP_FIND_DECODER);
        prepareFinished = true;
        return;
    }

    // 利用解码器创建解码器上下文 AVCodecContext，并初始化默认值
    weAudio->audioStream->codecContext = avcodec_alloc_context3(decoder);
    if (!weAudio->audioStream->codecContext) {
        LOGE(LOG_TAG, "Can't allocate an AVCodecContext for codec id %d", codecId);
        handleErrorOnPreparing(E_CODE_PRP_ALC_CODEC_CTX);
        prepareFinished = true;
        return;
    }

    // 把前边获取的音频流编解码参数填充到 AVCodecContext
    if ((ret = avcodec_parameters_to_context(
            weAudio->audioStream->codecContext, weAudio->audioStream->codecParams)) < 0) {
        LOGE(LOG_TAG,
             "Can't fill the AVCodecContext by AVCodecParameters for codec id %d. \n AVERROR=%d %s",
             codecId, ret, WeUtils::getAVErrorName(ret));
        handleErrorOnPreparing(E_CODE_PRP_PRM_CODEC_CTX);
        prepareFinished = true;
        return;
    }

    // 使用给定的 AVCodec 初始化 AVCodecContext
    if ((ret = avcodec_open2(weAudio->audioStream->codecContext, decoder, 0)) != 0) {
        LOGE(LOG_TAG,
             "Can't initialize the AVCodecContext to use the given AVCodec for codec id %d. \n AVERROR=%d %s",
             codecId, ret, WeUtils::getAVErrorName(ret));
        handleErrorOnPreparing(E_CODE_PRP_CODEC_OPEN);
        prepareFinished = true;
        return;
    }

    // 状态确认需要加锁同步，判断在准备期间是否已经被停止
    pthread_mutex_lock(&status->mutex);
    if (status == NULL || !status->isPreparing()) {
        // 只有“准备中”状态可以切换到“准备好”
        LOGE(LOG_TAG, "prepare finished but status isn't PREPARING");
        pthread_mutex_unlock(&status->mutex);
        prepareFinished = true;
        return;
    }
    status->setStatus(PlayStatus::PREPARED, LOG_TAG);

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "prepare finished to callback java...");
    }
    // 回调初始化准备完成，注意要在 java API 层把回调切换到主线程
    // ！！！注意：这里专门把 java 回调放到锁里，需要 java 层注意不要有其它本地方法调用和耗时操作！！！
    javaListenerContainer->onPreparedListener->callback(1, dataSource);

    pthread_mutex_unlock(&status->mutex);
    prepareFinished = true;
}

void WeFFmpeg::handleErrorOnPreparing(int errorCode) {
    // 出错先释放资源
    if (weAudio != NULL) {
        weAudio->releaseStream();
    }
    if (pFormatCtx != NULL) {
        avformat_close_input(&pFormatCtx);
        avformat_free_context(pFormatCtx);
        pFormatCtx = NULL;
    }

    // 再设置出错状态
    pthread_mutex_lock(&status->mutex);
    if (status == NULL || !status->isPreparing()) {
        pthread_mutex_unlock(&status->mutex);
        return;
    }
    status->setStatus(PlayStatus::ERROR, LOG_TAG);
    // ！！！注意：这里专门把 java 回调放到锁里，需要 java 层注意不要有其它本地方法调用和耗时操作！！！
    javaListenerContainer->onErrorListener->callback(2, errorCode, E_NAME_PREPARE);
    pthread_mutex_unlock(&status->mutex);
}

/**
 * 开始解包和播放
 */
void WeFFmpeg::start() {
    pthread_mutex_lock(&status->mutex);
    if (status == NULL ||
        (!status->isPrepared() && !status->isPaused() && !status->isCompleted())) {
        LOGE(LOG_TAG, "Invoke start but status is %s", status->getCurrentStatusName());
        // 调用非法，不是不可恢复的内部工作错误，所以不用设置错误状态
        pthread_mutex_unlock(&status->mutex);
        return;
    }
    if (status->isPrepared()) {
        status->setStatus(PlayStatus::PLAYING, LOG_TAG);
        if (createAudioPlayer() != NO_ERROR) {
            return;
        }
        if (weAudio->queue->isProductDataComplete()) {
            weAudio->queue->setProductDataComplete(false);
        }
        if (startAudioPlayer() != NO_ERROR) {
            return;
        }
        demuxThread->sendMessage(MSG_DEMUX_START);
    } else if (status->isCompleted()) {
        status->setStatus(PlayStatus::PLAYING, LOG_TAG);
        if (weAudio->queue->isProductDataComplete()) {
            seekToBegin = true;// 在播放完成后，如果用户没有 seek，就从头开始播放
            weAudio->queue->setProductDataComplete(false);
        }
        if (startAudioPlayer() != NO_ERROR) {
            return;
        }
        demuxThread->sendMessage(MSG_DEMUX_START);
    } else {
        status->setStatus(PlayStatus::PLAYING, LOG_TAG);
        weAudio->resumePlay();// 要先设置播放状态，才能恢复播放
    }
    pthread_mutex_unlock(&status->mutex);
}

int WeFFmpeg::createAudioPlayer() {
    int ret;
    if ((ret = weAudio->createPlayer()) != NO_ERROR) {
        LOGE(LOG_TAG, "weAudio createPlayer failed!");
        pthread_mutex_lock(&status->mutex);
        if (status != NULL && !status->isReleased()) {
            status->setStatus(PlayStatus::ERROR, LOG_TAG);
            // ！！！注意：这里专门把 java 回调放到锁里，需要 java 层注意不要有其它本地方法调用和耗时操作！！！
            javaListenerContainer->onErrorListener->callback(2, ret, E_NAME_AUDIO_PLAY);
        }
        pthread_mutex_unlock(&status->mutex);
        return ret;
    }
    return NO_ERROR;
}

int WeFFmpeg::startAudioPlayer() {
    int ret;
    if ((ret = weAudio->startPlay()) != NO_ERROR) {
        LOGE(LOG_TAG, "weAudio startPlay failed!");
        pthread_mutex_lock(&status->mutex);
        if (status != NULL && !status->isReleased()) {
            status->setStatus(PlayStatus::ERROR, LOG_TAG);
            // ！！！注意：这里专门把 java 回调放到锁里，需要 java 层注意不要有其它本地方法调用和耗时操作！！！
            javaListenerContainer->onErrorListener->callback(2, ret, E_NAME_AUDIO_PLAY);
        }
        pthread_mutex_unlock(&status->mutex);
        return ret;
    }
    return NO_ERROR;
}

void WeFFmpeg::demux() {
    demuxFinished = false;

    if (seekToBegin) {
        seekToBegin = false;
        seekTo(0);
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "Start read AVPacket...");
    }
    // 本线程开始读 AVPacket 包并缓存入队
    int packetCount = 0;
    int readRet = -1;
    AVPacket *avPacket = NULL;
    while (status != NULL && !status->isStoped() && !status->isError() && !status->isReleased()) {
        if (status->isSeeking) {
            continue;
        }
        if (weAudio->queue->getQueueSize() >= AVPacketQueue::MAX_CACHE_NUM) {
            continue;
        }

        // Allocate an AVPacket
        avPacket = av_packet_alloc();

        // 读取数据包到 AVPacket
        pthread_mutex_lock(&demuxMutex);// seek 会操作 AVFormatContext 修改解封装起始位置，所以要加锁
        readRet = av_read_frame(pFormatCtx, avPacket);
        pthread_mutex_unlock(&demuxMutex);

        if (readRet == 0) {
            // 读包成功
            if (avPacket->stream_index == weAudio->audioStream->streamIndex) {
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
            if (readRet == AVERROR_EOF) {
                // 数据读取已经到末尾
                LOGW(LOG_TAG, "AVPacket read finished");
            } else {
                // 出错了
                LOGE(LOG_TAG, "AVPacket read failed! AVERROR=%d %s", readRet,
                     WeUtils::getAVErrorName(readRet));
            }
            weAudio->queue->setProductDataComplete(true);

            // 减少 avPacket 对 packet 数据的引用计数
            av_packet_free(&avPacket);
            // 释放 avPacket 结构体本身
//            av_free(avPacket);
            av_freep(&avPacket);// 使用 av_freep(&buf) 代替 av_free(buf)
            avPacket = NULL;

            // 等待队列数据取完后退出，否则造成播放不完整
            while (status != NULL && !status->isStoped() && !status->isError() &&
                   !status->isReleased()) {
                if (weAudio->queue->getQueueSize() > 0) {// 还没有播放完成
                    av_usleep(10 * 1000);// 每次睡眠 10 ms
                    continue;
                }

                // 到这里说明队列已经没有数据了，原因有两个：1.播放完成；2. seek 主动清空了数据
                if (!weAudio->queue->isProductDataComplete()) {
                    // seek 主动清空了数据
                    break;// 跳出本循环，回到大循环继续读数据
                } else {
                    // 播放完成
                    av_usleep(200 * 1000);// 再睡眠一定时间等待真正播放完成，解决上层依据状态更新时间时不能更新最后一秒的问题
                    pthread_mutex_lock(&status->mutex);
                    if (status == NULL || !status->isPlaying()) {
                        // 只有“播放中”状态才可以切换到“播放完成”状态
                        pthread_mutex_unlock(&status->mutex);
                        demuxFinished = true;
                        return;// 结束 demux 线程
                    }
                    status->setStatus(PlayStatus::COMPLETED, LOG_TAG);

                    // ！！！注意：这里专门把 java 回调放到锁里，需要 java 层注意不要有其它本地方法调用和耗时操作！！！
                    javaListenerContainer->onCompletionListener->callback(0);

                    pthread_mutex_unlock(&status->mutex);
                    demuxFinished = true;
                    return;// 结束 demux 线程
                }
            } // 读完等待循环
        } // 读完分支
    } // 读包大循环
    weAudio->queue->setProductDataComplete(true);// 提前中断解包
    demuxFinished = true;// 提前中断退出
}

void WeFFmpeg::pause() {
    pthread_mutex_lock(&status->mutex);
    if (status == NULL || !status->isPlaying()) {
        LOGE(LOG_TAG, "pause but status is not PLAYING");
        // 调用非法，不是不可恢复的内部工作错误，所以不用设置错误状态
        pthread_mutex_unlock(&status->mutex);
        return;
    }
    status->setStatus(PlayStatus::PAUSED, LOG_TAG);
    pthread_mutex_unlock(&status->mutex);

    weAudio->pause();
}

/**
 * seek 会操作 AVFormatContext 修改解封装起始位置，
 * 所以与同时解封装动作的 int av_read_frame(AVFormatContext *s, AVPacket *pkt) 冲突，
 * 多线程之间要加锁同步操作！
 *
 * @param msec the offset in milliseconds from the start to seek to
 */
void WeFFmpeg::seekTo(int msec) {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "seekTo %d ms", msec);
    }

    if (status == NULL || (!status->isPrepared() && !status->isPlaying()
                           && !status->isPaused() && !status->isCompleted())) {
        LOGE(LOG_TAG, "Can't seek because status is %s", status->getCurrentStatusName());
        return;
    }

    if (duration <= 0) {
        LOGE(LOG_TAG, "Can't seek because duration <= 0");
        return;
    }
    int targetSeconds = roundf(((float) msec) / 1000);
    if (targetSeconds < 0) {
        targetSeconds = 0;
    } else if (targetSeconds > duration) {
        targetSeconds = duration;
    }

    if (weAudio == NULL) {
        LOGE(LOG_TAG, "Can't seek because weAudio == NULL");
        return;
    }

    status->isSeeking = true;

    // reset
    weAudio->queue->setProductDataComplete(false);// 通知解包者和播放者还有数据，尤其对于解包线已经解完进入等待状态有用
    weAudio->queue->clearQueue();
    weAudio->setSeekTime(targetSeconds);

    long long seekStart, seekEnd;
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "seek --> start...");
        seekStart = WeUtils::getCurrentTimeMill();
    }
    // seek
    pthread_mutex_lock(&demuxMutex);
    // 由于这里 seek 设置流索引为 -1，所以 seek 会以 AV_TIME_BASE 为单位，而 AV_TIME_BASE 定义为 1000000，
    // 因此把秒数转换成 AV_TIME_BASE 所代表的时间刻度数后使用 int 就不够了，需要用 int64_t 代替。
    int64_t seekPosition = targetSeconds * AV_TIME_BASE;
    int ret = avformat_seek_file(pFormatCtx, -1, INT64_MIN, seekPosition, INT64_MAX, 0);
    if (ret < 0) {
        LOGE(LOG_TAG, "seek failed! AVERROR=%d %s", ret, WeUtils::getAVErrorName(ret));
    }
    pthread_mutex_unlock(&demuxMutex);
    if (LOG_DEBUG) {
        seekEnd = WeUtils::getCurrentTimeMill();
        LOGD(LOG_TAG, "seek --> end. use %lld ms!", seekEnd - seekStart);
    }

    status->isSeeking = false;
}

/**
 * Gets the duration of the file.
 *
 * @return the duration in milliseconds
 */
int WeFFmpeg::getDuration() {
    return duration * 1000;
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
    int ret = weAudio->getPlayTimeSecs() * 1000;
    if (LOG_REPEAT_DEBUG) {
        LOGD(LOG_TAG, "getCurrentPosition: %d", ret);
    }
    return ret;
}

bool WeFFmpeg::isPlaying() {
    return status != NULL && status->isPlaying();
}

/**
 * setStopFlag 不走 java 调度线程消息队列，直接执行，避免无法立即通知结束工作
 * 这样就会与使用调度线程的方法并发，所以要对部分函数步骤做锁同步
 */
void WeFFmpeg::setStopFlag() {
    pthread_mutex_lock(&status->mutex);
    if (status == NULL || status->isReleased() || status->isStoped()) {
        LOGE(LOG_TAG, "Call setStopFlag but status is already NULL or stopped: %d", status);
        pthread_mutex_unlock(&status->mutex);
        return;
    }
    status->setStatus(PlayStatus::STOPPED, LOG_TAG);
    pthread_mutex_unlock(&status->mutex);
}

/**
 * 具体停止工作，例如：停止播放、关闭打开的文件流
 */
void WeFFmpeg::stop() {
    pthread_mutex_lock(&status->mutex);
    if (status == NULL || status->isReleased()) {
        LOGE(LOG_TAG, "to stop but status is already RELEASED");
        pthread_mutex_unlock(&status->mutex);
        return;
    }
    // 进一步检查外界是否已经调用了 setStopFlag，否则这里直接设置停止标志
    if (!status->isStoped()) {
        status->setStatus(PlayStatus::STOPPED, LOG_TAG);
    }
    pthread_mutex_unlock(&status->mutex);

    if (weAudio != NULL) {
        weAudio->stopPlay();
        weAudio->destroyPlayer();// 不同采样参数数据流使用的 openSlPlayer 不一样，需要销毁新建
    }

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "close stream wait other thread finished...");
    }
    // 等待工作线程结束
    int sleepCount = 0;
    while (!prepareFinished || !demuxFinished) {
        if (sleepCount > 300) {
            break;
        }
        sleepCount++;
        av_usleep(10 * 1000);// 每次睡眠 10 ms
    }
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "close stream wait end after sleep %d ms, start close...", sleepCount * 10);
    }

    if (weAudio != NULL) {
        weAudio->clearDataAfterStop();
    }

    // 释放打开的数据流
    if (pFormatCtx != NULL) {
        avformat_close_input(&pFormatCtx);
        avformat_free_context(pFormatCtx);
        pFormatCtx = NULL;
    }

    duration = 0;
}

/**
 * 调用 release 之前，先异步调用 setStopFlag；
 * 因为 release 要与 prepare 保持串行，而 prepare 可能会一直阻塞，先异步调用 setStopFlag 结束 prepare
 */
void WeFFmpeg::release() {
    initSuccess = false;

    pthread_mutex_lock(&status->mutex);
    if (status == NULL || status->isReleased()) {
        LOGE(LOG_TAG, "to release but status is already NULL or RELEASED");
        pthread_mutex_unlock(&status->mutex);
        return;
    }
    status->setStatus(PlayStatus::RELEASED, LOG_TAG);
    pthread_mutex_unlock(&status->mutex);

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "release wait other thread finished...");
    }
    // 等待工作线程结束
    int sleepCount = 0;
    while (!prepareFinished || !demuxFinished) {
        if (sleepCount > 300) {
            break;
        }
        sleepCount++;
        av_usleep(10 * 1000);// 每次睡眠 10 ms
    }
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "release wait end after sleep %d ms, start release...", sleepCount * 10);
    }

    // 开始释放所有资源
    if (weAudio != NULL) {
        delete weAudio;
        weAudio = NULL;
    }

    if (pFormatCtx != NULL) {
        avformat_close_input(&pFormatCtx);
        avformat_free_context(pFormatCtx);
        pFormatCtx = NULL;
    }

    destroyDemuxThread();// 要在 demuxFinished = true 后再执行

    if (dataSource != NULL) {
        delete[] dataSource;// dataSource 是 JNI 中通过 new char[] 创建的
        dataSource = NULL;
    }

    // 最顶层负责回收 javaListenerContainer
    delete javaListenerContainer;
    javaListenerContainer = NULL;

    // 最顶层负责回收 status
    delete status;
    status = NULL;

    pthread_mutex_destroy(&demuxMutex);

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "release finished");
    }
}

void WeFFmpeg::destroyDemuxThread() {
    if (demuxThread != NULL) {
        demuxThread->shutdown();
        delete demuxThread;
        demuxThread = NULL;
    }
}