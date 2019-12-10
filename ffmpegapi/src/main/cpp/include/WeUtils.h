//
// Created by WTZ on 2019/11/29.
//

#ifndef FFMPEG_WEUTILS_H
#define FFMPEG_WEUTILS_H

#include <stddef.h>
#include <sys/time.h>
#include "AndroidLog.h"

extern "C"
{
#include "libavformat/avformat.h"
#include "libavutil/avstring.h"
#include "libavutil/opt.h"
#include "libavutil/intreadwrite.h"
#include "libavutil/replaygain.h"
#include "libavutil/display.h"
#include "libavutil/stereo3d.h"
#include "libavutil/mastering_display_metadata.h"
#include "libavutil/spherical.h"
#include "libavutil/error.h"
};

#define WE_UTILS_LOG_TAG "WeUtils"

/**
 * 很多函数都是 FFmpeg 的源码，只是把打印函数换成了 Android NDK 支持的打印函数，因为 NDK 不支持 printf
 */
class WeUtils {

public:
    static void
    av_dump_format_for_android(AVFormatContext *ic, int index, const char *url, int is_output);

    static long long getCurrentTimeMill();

    static char *getAVErrorName(int avError);

private:
    static void dump_metadata(void *ctx, AVDictionary *m, const char *indent);

    static void dump_stream_format(AVFormatContext *ic, int i, int index, int is_output);

    static void dump_sidedata(void *ctx, AVStream *st, const char *indent);

    static void dump_paramchange(void *ctx, AVPacketSideData *sd);

    static void dump_replaygain(void *ctx, AVPacketSideData *sd);

    static void dump_stereo3d(void *ctx, AVPacketSideData *sd);

    static void dump_audioservicetype(void *ctx, AVPacketSideData *sd);

    static void dump_cpb(void *ctx, AVPacketSideData *sd);

    static void dump_mastering_display_metadata(void *ctx, AVPacketSideData *sd);

    static void dump_spherical(void *ctx, AVCodecParameters *par, AVPacketSideData *sd);

    static void dump_content_light_metadata(void *ctx, AVPacketSideData *sd);

    static void print_fps(double d, const char *postfix);

    static void print_gain(void *ctx, const char *str, int32_t gain);

    static void print_peak(void *ctx, const char *str, uint32_t peak);

};


#endif //FFMPEG_WEUTILS_H
