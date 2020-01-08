//
// Created by WTZ on 2019/12/22.
//

#include "WeDemux.h"

WeDemux::WeDemux() {
    init();
}

WeDemux::~WeDemux() {
    release();
}

void WeDemux::init() {
    audioQueue = new AVPacketQueue();
    pthread_mutex_init(&demuxMutex, NULL);

    // 注册解码器并初始化网络
//    av_register_all();// ffmpeg 新版弃用了此函数
    avformat_network_init();
}

AVPacketQueue *WeDemux::getAudioQueue() {
    return audioQueue;
}

AudioStream *WeDemux::getAudioStream() {
    return audioStream;
}

void WeDemux::setDataSource(char *dataSource) {
    if (this->dataSource != NULL) {
        // 先释放旧的 dataSource
        delete[] this->dataSource;// dataSource 是 JNI 中通过 new char[] 创建的
    }
    this->dataSource = dataSource;
}

char *WeDemux::getDataSource() {
    return dataSource;
}

void WeDemux::clearDataSource() {
    if (dataSource != NULL) {
        delete[] dataSource;// dataSource 是 JNI 中通过 new char[] 创建的
        dataSource = NULL;
    }
}

/**
 * AVFormatContext 操作过程中阻塞时中断回调处理函数
 */
int formatCtxInterruptCallback(void *context) {
    if (LOG_REPEAT_DEBUG) {
        LOGD("WeDemux", "formatCtxInterruptCallback...");
    }
    WeDemux *pWeDemux = (WeDemux *) context;
    if (pWeDemux->isStopped()) {
        LOGW("WeDemux", "formatCtxInterruptCallback return AVERROR_EOF");
        return AVERROR_EOF;
    }
    return 0;
}

/**
 * 为新数据源做准备，或者调用过 stop 后再重新做准备
 */
int WeDemux::prepare() {
    stopWork = false;

    pFormatCtx = avformat_alloc_context();
    pFormatCtx->interrupt_callback.callback = formatCtxInterruptCallback;
    pFormatCtx->interrupt_callback.opaque = this;

    int ret;
    // 打开文件或网络流
    if ((ret = avformat_open_input(&pFormatCtx, dataSource, NULL, NULL)) != 0) {
        LOGE(LOG_TAG, "Can't open data source: %s. \n AVERROR=%d %s", dataSource,
             ret, WeUtils::getAVErrorName(ret));
        // 报错的原因可能有：打开网络流时无网络权限，打开本地流时无外部存储访问权限
        return E_CODE_PRP_OPEN_SOURCE;
    }

    // 查找流信息
    if ((ret = avformat_find_stream_info(pFormatCtx, NULL)) < 0) {
        LOGE(LOG_TAG, "Can't find stream info from: %s. \n AVERROR=%d %s", dataSource,
             ret, WeUtils::getAVErrorName(ret));
        return E_CODE_PRP_FIND_STREAM;
    }

    if (LOG_DEBUG) {
        WeUtils::av_dump_format_for_android(pFormatCtx, 0, dataSource, 0);
    }

    // 从流信息中遍历查找音频流
    for (int i = 0; i < pFormatCtx->nb_streams; i++) {
        if (pFormatCtx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            duration = pFormatCtx->duration * av_q2d(AV_TIME_BASE_Q);
            if (duration < 0) {
                duration = 0;
            }
            if (LOG_DEBUG) {
                LOGD(LOG_TAG, "Find audio stream info index=%d, duration=%lf", i, duration);
            }
            // 保存音频流信息
            audioStream = new AudioStream(i, pFormatCtx->streams[i]->codecpar,
                                          pFormatCtx->streams[i]->time_base, duration);
            break;
        }
    }
    if (audioStream == NULL) {
        LOGE(LOG_TAG, "Can't find audio stream from: %s", dataSource);
        return E_CODE_PRP_FIND_AUDIO;
    }

    // 根据 AVCodecID 查找解码器
    AVCodecID codecId = audioStream->codecParams->codec_id;
    AVCodec *decoder = avcodec_find_decoder(codecId);
    if (!decoder) {
        LOGE(LOG_TAG, "Can't find decoder for codec id %d", codecId);
        return E_CODE_PRP_FIND_DECODER;
    }

    // 利用解码器创建解码器上下文 AVCodecContext，并初始化默认值
    audioStream->codecContext = avcodec_alloc_context3(decoder);
    if (!audioStream->codecContext) {
        LOGE(LOG_TAG, "Can't allocate an AVCodecContext for codec id %d", codecId);
        return E_CODE_PRP_ALC_CODEC_CTX;
    }

    // 把前边获取的音频流编解码参数填充到 AVCodecContext
    if ((ret = avcodec_parameters_to_context(
            audioStream->codecContext, audioStream->codecParams)) < 0) {
        LOGE(LOG_TAG,
             "Can't fill the AVCodecContext by AVCodecParameters for codec id %d. \n AVERROR=%d %s",
             codecId, ret, WeUtils::getAVErrorName(ret));
        return E_CODE_PRP_PRM_CODEC_CTX;
    }

    // 使用给定的 AVCodec 初始化 AVCodecContext
    if ((ret = avcodec_open2(audioStream->codecContext, decoder, 0)) != 0) {
        LOGE(LOG_TAG,
             "Can't initialize the AVCodecContext to use the given AVCodec for codec id %d. \n AVERROR=%d %s",
             codecId, ret, WeUtils::getAVErrorName(ret));
        return E_CODE_PRP_CODEC_OPEN;
    }

    return NO_ERROR;
}

/**
  * 读取数据包到 AVPacket，因为 seek 会操作 AVFormatContext 修改解封装起始位置，所以要加锁
  *
  * @return 0 if OK, < 0 on error or end of file
  */
int WeDemux::readPacket(AVPacket *pkt) {
    pthread_mutex_lock(&demuxMutex);
    int ret = av_read_frame(pFormatCtx, pkt);
    pthread_mutex_unlock(&demuxMutex);
    if (ret < 0) {
        if (ret == AVERROR_EOF) {
            // 数据读取已经到末尾
            LOGW(LOG_TAG, "AVPacket read finished");
        } else {
            // 出错了
            LOGE(LOG_TAG, "AVPacket read failed! AVERROR=%d %s", ret, WeUtils::getAVErrorName(ret));
        }
    }
    return ret;
}

/**
 * seek 会操作 AVFormatContext 修改解封装起始位置，
 * 所以与同时解封装动作的 int av_read_frame(AVFormatContext *s, AVPacket *pkt) 冲突，
 * 多线程之间要加锁同步操作！
 *
 * @param targetSeconds the offset in seconds from the start to seek to
 */
void WeDemux::seekTo(double targetSeconds) {
    long long seekStart, seekEnd;
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "seek --> start...");
        seekStart = WeUtils::getCurrentTimeMill();
    }

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
}

/**
 * Gets the duration of the file.
 *
 * @return the duration in seconds
 */
double WeDemux::getDurationSecs() {
    return duration;
}

void WeDemux::setStopFlag() {
    stopWork = true;
}

bool WeDemux::isStopped() {
    return stopWork;
}

void WeDemux::releaseStream() {
    delete audioStream;
    audioStream = NULL;

    if (pFormatCtx != NULL) {
        avformat_close_input(&pFormatCtx);
        avformat_free_context(pFormatCtx);
        pFormatCtx = NULL;
    }

    duration = 0;
}

void WeDemux::release() {
    releaseStream();
    clearDataSource();

    delete audioQueue;
    audioQueue = NULL;

    pthread_mutex_destroy(&demuxMutex);
}
