//
// Created by WTZ on 2019/11/29.
//

#include "WeUtils.h"

void WeUtils::av_dump_format_for_android(AVFormatContext *ic, int index, const char *url,
                                         int is_output) {
    int i;
    uint8_t *printed = (uint8_t *) (ic->nb_streams ? av_mallocz(ic->nb_streams) : NULL);
    if (ic->nb_streams && !printed)
        return;

    LOGI(WE_UTILS_LOG_TAG, "**************** AV_DUMP_FORMAT: START ****************")
    LOGI(WE_UTILS_LOG_TAG, "%s #%d, %s, %s '%s':\n",
         is_output ? "Output" : "Input",
         index,
         is_output ? ic->oformat->name : ic->iformat->name,
         is_output ? "to" : "from", url);
    dump_metadata(NULL, ic->metadata, "  ");

    if (!is_output) {
        LOGI(WE_UTILS_LOG_TAG, "  Duration: ");
        if (ic->duration != AV_NOPTS_VALUE) {
            int hours, mins, secs, us;
            int64_t duration = ic->duration + (ic->duration <= INT64_MAX - 5000 ? 5000 : 0);
            secs = duration / AV_TIME_BASE;
            us = duration % AV_TIME_BASE;
            mins = secs / 60;
            secs %= 60;
            hours = mins / 60;
            mins %= 60;
            LOGI(WE_UTILS_LOG_TAG, "%02d:%02d:%02d.%02d", hours, mins, secs,
                 (100 * us) / AV_TIME_BASE);
        } else {
            LOGI(WE_UTILS_LOG_TAG, "N/A");
        }
        if (ic->start_time != AV_NOPTS_VALUE) {
            int secs, us;
            LOGI(WE_UTILS_LOG_TAG, " start: ");
            secs = llabs(ic->start_time / AV_TIME_BASE);
            us = llabs(ic->start_time % AV_TIME_BASE);
            LOGI(WE_UTILS_LOG_TAG, "%s%d.%06d",
                 ic->start_time >= 0 ? "" : "-",
                 secs,
                 (int) av_rescale(us, 1000000, AV_TIME_BASE));
        }
        LOGI(WE_UTILS_LOG_TAG, " bitrate: ");
        if (ic->bit_rate) {
            LOGI(WE_UTILS_LOG_TAG, "%lld kb/s", ic->bit_rate / 1000);
        } else {
            LOGI(WE_UTILS_LOG_TAG, "N/A");
        }
        LOGI(WE_UTILS_LOG_TAG, "\n");
    }

    for (i = 0; i < ic->nb_chapters; i++) {
        AVChapter *ch = ic->chapters[i];
        LOGI(WE_UTILS_LOG_TAG, "    Chapter #%d:%d: ", index, i);
        LOGI(WE_UTILS_LOG_TAG,
             "start %f, ", ch->start * av_q2d(ch->time_base));
        LOGI(WE_UTILS_LOG_TAG,
             "end %f\n", ch->end * av_q2d(ch->time_base));

        dump_metadata(NULL, ch->metadata, "    ");
    }

    if (ic->nb_programs) {
        int j, k, total = 0;
        for (j = 0; j < ic->nb_programs; j++) {
            AVDictionaryEntry *name = av_dict_get(ic->programs[j]->metadata,
                                                  "name", NULL, 0);
            LOGI(WE_UTILS_LOG_TAG, "  Program %d %s\n", ic->programs[j]->id,
                 name ? name->value : "");
            dump_metadata(NULL, ic->programs[j]->metadata, "    ");
            for (k = 0; k < ic->programs[j]->nb_stream_indexes; k++) {
                dump_stream_format(ic, ic->programs[j]->stream_index[k],
                                   index, is_output);
                printed[ic->programs[j]->stream_index[k]] = 1;
            }
            total += ic->programs[j]->nb_stream_indexes;
        }
        if (total < ic->nb_streams)
            LOGI(WE_UTILS_LOG_TAG, "  No Program\n");
    }

    for (i = 0; i < ic->nb_streams; i++)
        if (!printed[i])
            dump_stream_format(ic, i, index, is_output);

//    av_free(printed);
    av_freep(&printed);// 使用 av_freep(&buf) 代替 av_free(buf)
    LOGI(WE_UTILS_LOG_TAG, "**************** AV_DUMP_FORMAT: END ****************")
}

void WeUtils::dump_metadata(void *ctx, AVDictionary *m, const char *indent) {
    if (m && !(av_dict_count(m) == 1 && av_dict_get(m, "language", NULL, 0))) {
        AVDictionaryEntry *tag = NULL;

        LOGI(WE_UTILS_LOG_TAG, "%sMetadata:\n", indent);
        while ((tag = av_dict_get(m, "", tag, AV_DICT_IGNORE_SUFFIX)))
            if (strcmp("language", tag->key)) {
                const char *p = tag->value;
                LOGI(WE_UTILS_LOG_TAG,
                     "%s  %-16s: ", indent, tag->key);
                while (*p) {
                    char tmp[256];
                    size_t len = strcspn(p, "\x8\xa\xb\xc\xd");
                    av_strlcpy(tmp, p, FFMIN(sizeof(tmp), len + 1));
                    LOGI(WE_UTILS_LOG_TAG, "%s", tmp);
                    p += len;
                    if (*p == 0xd) LOGI(WE_UTILS_LOG_TAG, " ");
                    if (*p == 0xa) LOGI(WE_UTILS_LOG_TAG, "\n%s  %-16s: ", indent, "");
                    if (*p) p++;
                }
                LOGI(WE_UTILS_LOG_TAG, "\n");
            }
    }
}

void WeUtils::dump_stream_format(AVFormatContext *ic, int i, int index, int is_output) {
    char buf[256];
    int flags = (is_output ? ic->oformat->flags : ic->iformat->flags);
    AVStream *st = ic->streams[i];
    AVDictionaryEntry *lang = av_dict_get(st->metadata, "language", NULL, 0);
    char *separator = (char *) (ic->dump_separator);
    AVCodecContext *avctx;
    int ret;

    avctx = avcodec_alloc_context3(NULL);
    if (!avctx)
        return;

    ret = avcodec_parameters_to_context(avctx, st->codecpar);
    if (ret < 0) {
        avcodec_free_context(&avctx);
        return;
    }

    // Fields which are missing from AVCodecParameters need to be taken from the AVCodecContext
    avctx->properties = st->codec->properties;
    avctx->codec = st->codec->codec;
    avctx->qmin = st->codec->qmin;
    avctx->qmax = st->codec->qmax;
    avctx->coded_width = st->codec->coded_width;
    avctx->coded_height = st->codec->coded_height;

    if (separator)
        av_opt_set(avctx, "dump_separator", separator, 0);
    avcodec_string(buf, sizeof(buf), avctx, is_output);
    avcodec_free_context(&avctx);

    LOGI(WE_UTILS_LOG_TAG, "    Stream #%d:%d", index, i);

    /* the pid is an important information, so we display it */
    /* XXX: add a generic system */
    if (flags & AVFMT_SHOW_IDS)
        LOGI(WE_UTILS_LOG_TAG, "[0x%x]", st->id);
    if (lang)
        LOGI(WE_UTILS_LOG_TAG, "(%s)", lang->value);
    LOGD(WE_UTILS_LOG_TAG, "nb_frames: %d, time_base: %d/%d", st->codec_info_nb_frames,
         st->time_base.num, st->time_base.den);
    LOGI(WE_UTILS_LOG_TAG, ": %s", buf);

    if (st->sample_aspect_ratio.num &&
        av_cmp_q(st->sample_aspect_ratio, st->codecpar->sample_aspect_ratio)) {
        AVRational display_aspect_ratio;
        av_reduce(&display_aspect_ratio.num, &display_aspect_ratio.den,
                  st->codecpar->width * (int64_t) st->sample_aspect_ratio.num,
                  st->codecpar->height * (int64_t) st->sample_aspect_ratio.den,
                  1024 * 1024);
        LOGI(WE_UTILS_LOG_TAG, ", SAR %d:%d DAR %d:%d",
             st->sample_aspect_ratio.num, st->sample_aspect_ratio.den,
             display_aspect_ratio.num, display_aspect_ratio.den);
    }

    if (st->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
        int fps = st->avg_frame_rate.den && st->avg_frame_rate.num;
        int tbr = st->r_frame_rate.den && st->r_frame_rate.num;
        int tbn = st->time_base.den && st->time_base.num;
        int tbc = st->codec->time_base.den && st->codec->time_base.num;

        if (fps || tbr || tbn || tbc)
            LOGI(WE_UTILS_LOG_TAG, "%s", separator);

        if (fps)
            print_fps(av_q2d(st->avg_frame_rate), tbr || tbn || tbc ? "fps, " : "fps");
        if (tbr)
            print_fps(av_q2d(st->r_frame_rate), tbn || tbc ? "tbr, " : "tbr");
        if (tbn)
            print_fps(1 / av_q2d(st->time_base), tbc ? "tbn, " : "tbn");
        if (tbc)
            print_fps(1 / av_q2d(st->codec->time_base), "tbc");
    }

    if (st->disposition & AV_DISPOSITION_DEFAULT)
        LOGI(WE_UTILS_LOG_TAG, " (default)");
    if (st->disposition & AV_DISPOSITION_DUB)
        LOGI(WE_UTILS_LOG_TAG, " (dub)");
    if (st->disposition & AV_DISPOSITION_ORIGINAL)
        LOGI(WE_UTILS_LOG_TAG, " (original)");
    if (st->disposition & AV_DISPOSITION_COMMENT)
        LOGI(WE_UTILS_LOG_TAG, " (comment)");
    if (st->disposition & AV_DISPOSITION_LYRICS)
        LOGI(WE_UTILS_LOG_TAG, " (lyrics)");
    if (st->disposition & AV_DISPOSITION_KARAOKE)
        LOGI(WE_UTILS_LOG_TAG, " (karaoke)");
    if (st->disposition & AV_DISPOSITION_FORCED)
        LOGI(WE_UTILS_LOG_TAG, " (forced)");
    if (st->disposition & AV_DISPOSITION_HEARING_IMPAIRED)
        LOGI(WE_UTILS_LOG_TAG, " (hearing impaired)");
    if (st->disposition & AV_DISPOSITION_VISUAL_IMPAIRED)
        LOGI(WE_UTILS_LOG_TAG, " (visual impaired)");
    if (st->disposition & AV_DISPOSITION_CLEAN_EFFECTS)
        LOGI(WE_UTILS_LOG_TAG, " (clean effects)");
    LOGI(WE_UTILS_LOG_TAG, "\n");

    dump_metadata(NULL, st->metadata, "    ");

    dump_sidedata(NULL, st, "    ");
}

void WeUtils::dump_sidedata(void *ctx, AVStream *st, const char *indent) {
    int i;

    if (st->nb_side_data)
        LOGI(WE_UTILS_LOG_TAG, "%sSide data:\n", indent);

    for (i = 0; i < st->nb_side_data; i++) {
        AVPacketSideData sd = st->side_data[i];
        LOGI(WE_UTILS_LOG_TAG, "%s  ", indent);

        switch (sd.type) {
            case AV_PKT_DATA_PALETTE:
                LOGI(WE_UTILS_LOG_TAG, "palette");
                break;
            case AV_PKT_DATA_NEW_EXTRADATA:
                LOGI(WE_UTILS_LOG_TAG, "new extradata");
                break;
            case AV_PKT_DATA_PARAM_CHANGE:
                LOGI(WE_UTILS_LOG_TAG, "paramchange: ");
                dump_paramchange(ctx, &sd);
                break;
            case AV_PKT_DATA_H263_MB_INFO:
                LOGI(WE_UTILS_LOG_TAG, "H.263 macroblock info");
                break;
            case AV_PKT_DATA_REPLAYGAIN:
                LOGI(WE_UTILS_LOG_TAG, "replaygain: ");
                dump_replaygain(ctx, &sd);
                break;
            case AV_PKT_DATA_DISPLAYMATRIX:
                LOGI(WE_UTILS_LOG_TAG, "displaymatrix: rotation of %.2f degrees",
                     av_display_rotation_get((int32_t *) sd.data));
                break;
            case AV_PKT_DATA_STEREO3D:
                LOGI(WE_UTILS_LOG_TAG, "stereo3d: ");
                dump_stereo3d(ctx, &sd);
                break;
            case AV_PKT_DATA_AUDIO_SERVICE_TYPE:
                LOGI(WE_UTILS_LOG_TAG, "audio service type: ");
                dump_audioservicetype(ctx, &sd);
                break;
            case AV_PKT_DATA_QUALITY_STATS:
                LOGI(WE_UTILS_LOG_TAG, "quality factor: %ld, pict_type: %c",
                     AV_RL32(sd.data), av_get_picture_type_char(
                        (AVPictureType) (sd.data[4])));
                break;
            case AV_PKT_DATA_CPB_PROPERTIES:
                LOGI(WE_UTILS_LOG_TAG, "cpb: ");
                dump_cpb(ctx, &sd);
                break;
            case AV_PKT_DATA_MASTERING_DISPLAY_METADATA:
                dump_mastering_display_metadata(ctx, &sd);
                break;
            case AV_PKT_DATA_SPHERICAL:
                LOGI(WE_UTILS_LOG_TAG, "spherical: ");
                dump_spherical(ctx, st->codecpar, &sd);
                break;
            case AV_PKT_DATA_CONTENT_LIGHT_LEVEL:
                dump_content_light_metadata(ctx, &sd);
                break;
            default:
                LOGI(WE_UTILS_LOG_TAG,
                     "unknown side data type %d (%d bytes)", sd.type, sd.size);
                break;
        }

        LOGI(WE_UTILS_LOG_TAG, "\n");
    }
}

void WeUtils::dump_paramchange(void *ctx, AVPacketSideData *sd) {
    int size = sd->size;
    const uint8_t *data = sd->data;
    uint32_t flags, channels, sample_rate, width, height;
    uint64_t layout;

    if (!data || sd->size < 4)
        goto fail;

    flags = AV_RL32(data);
    data += 4;
    size -= 4;

    if (flags & AV_SIDE_DATA_PARAM_CHANGE_CHANNEL_COUNT) {
        if (size < 4)
            goto fail;
        channels = AV_RL32(data);
        data += 4;
        size -= 4;
        LOGI(WE_UTILS_LOG_TAG, "channel count %u, ", channels);
    }
    if (flags & AV_SIDE_DATA_PARAM_CHANGE_CHANNEL_LAYOUT) {
        if (size < 8)
            goto fail;
        layout = AV_RL64(data);
        data += 8;
        size -= 8;
        LOGI(WE_UTILS_LOG_TAG,
             "channel layout: %s, ", av_get_channel_name(layout));
    }
    if (flags & AV_SIDE_DATA_PARAM_CHANGE_SAMPLE_RATE) {
        if (size < 4)
            goto fail;
        sample_rate = AV_RL32(data);
        data += 4;
        size -= 4;
        LOGI(WE_UTILS_LOG_TAG, "sample_rate %u, ", sample_rate);
    }
    if (flags & AV_SIDE_DATA_PARAM_CHANGE_DIMENSIONS) {
        if (size < 8)
            goto fail;
        width = AV_RL32(data);
        data += 4;
        size -= 4;
        height = AV_RL32(data);
        data += 4;
        size -= 4;
        LOGI(WE_UTILS_LOG_TAG, "width %u height %u", width, height);
    }

    return;
    fail:
    LOGI(WE_UTILS_LOG_TAG, "unknown param");
}

void WeUtils::dump_replaygain(void *ctx, AVPacketSideData *sd) {
    AVReplayGain *rg;

    if (sd->size < sizeof(*rg)) {
        LOGI(WE_UTILS_LOG_TAG, "invalid data");
        return;
    }
    rg = (AVReplayGain *) sd->data;

    print_gain(ctx, "track gain", rg->track_gain);
    print_peak(ctx, "track peak", rg->track_peak);
    print_gain(ctx, "album gain", rg->album_gain);
    print_peak(ctx, "album peak", rg->album_peak);
}

void WeUtils::dump_stereo3d(void *ctx, AVPacketSideData *sd) {
    AVStereo3D *stereo;

    if (sd->size < sizeof(*stereo)) {
        LOGI(WE_UTILS_LOG_TAG, "invalid data");
        return;
    }

    stereo = (AVStereo3D *) sd->data;

    LOGI(WE_UTILS_LOG_TAG, "%s", av_stereo3d_type_name(stereo->type));

    if (stereo->flags & AV_STEREO3D_FLAG_INVERT)
        LOGI(WE_UTILS_LOG_TAG, " (inverted)");
}

void WeUtils::dump_audioservicetype(void *ctx, AVPacketSideData *sd) {
    enum AVAudioServiceType *ast = (enum AVAudioServiceType *) sd->data;

    if (sd->size < sizeof(*ast)) {
        LOGI(WE_UTILS_LOG_TAG, "invalid data");
        return;
    }

    switch (*ast) {
        case AV_AUDIO_SERVICE_TYPE_MAIN:
            LOGI(WE_UTILS_LOG_TAG, "main");
            break;
        case AV_AUDIO_SERVICE_TYPE_EFFECTS:
            LOGI(WE_UTILS_LOG_TAG, "effects");
            break;
        case AV_AUDIO_SERVICE_TYPE_VISUALLY_IMPAIRED:
            LOGI(WE_UTILS_LOG_TAG, "visually impaired");
            break;
        case AV_AUDIO_SERVICE_TYPE_HEARING_IMPAIRED:
            LOGI(WE_UTILS_LOG_TAG, "hearing impaired");
            break;
        case AV_AUDIO_SERVICE_TYPE_DIALOGUE:
            LOGI(WE_UTILS_LOG_TAG, "dialogue");
            break;
        case AV_AUDIO_SERVICE_TYPE_COMMENTARY:
            LOGI(WE_UTILS_LOG_TAG, "comentary");
            break;
        case AV_AUDIO_SERVICE_TYPE_EMERGENCY:
            LOGI(WE_UTILS_LOG_TAG, "emergency");
            break;
        case AV_AUDIO_SERVICE_TYPE_VOICE_OVER:
            LOGI(WE_UTILS_LOG_TAG, "voice over");
            break;
        case AV_AUDIO_SERVICE_TYPE_KARAOKE:
            LOGI(WE_UTILS_LOG_TAG, "karaoke");
            break;
        default:
            LOGW(WE_UTILS_LOG_TAG, "unknown");
            break;
    }
}

void WeUtils::dump_cpb(void *ctx, AVPacketSideData *sd) {
    AVCPBProperties *cpb = (AVCPBProperties *) sd->data;

    if (sd->size < sizeof(*cpb)) {
        LOGI(WE_UTILS_LOG_TAG, "invalid data");
        return;
    }

    LOGI(WE_UTILS_LOG_TAG,
         "bitrate max/min/avg: %d/%d/%d buffer size: %d vbv_delay: %lld",
         cpb->max_bitrate, cpb->min_bitrate, cpb->avg_bitrate,
         cpb->buffer_size,
         cpb->vbv_delay);
}

void WeUtils::dump_mastering_display_metadata(void *ctx, AVPacketSideData *sd) {
    AVMasteringDisplayMetadata *metadata = (AVMasteringDisplayMetadata *) sd->data;
    LOGI(WE_UTILS_LOG_TAG, "Mastering Display Metadata, "
                           "has_primaries:%d has_luminance:%d "
                           "r(%5.4f,%5.4f) g(%5.4f,%5.4f) b(%5.4f %5.4f) wp(%5.4f, %5.4f) "
                           "min_luminance=%f, max_luminance=%f",
         metadata->has_primaries, metadata->has_luminance,
         av_q2d(metadata->display_primaries[0][0]),
         av_q2d(metadata->display_primaries[0][1]),
         av_q2d(metadata->display_primaries[1][0]),
         av_q2d(metadata->display_primaries[1][1]),
         av_q2d(metadata->display_primaries[2][0]),
         av_q2d(metadata->display_primaries[2][1]),
         av_q2d(metadata->white_point[0]), av_q2d(metadata->white_point[1]),
         av_q2d(metadata->min_luminance), av_q2d(metadata->max_luminance));
}

void WeUtils::dump_spherical(void *ctx, AVCodecParameters *par, AVPacketSideData *sd) {
    AVSphericalMapping *spherical = (AVSphericalMapping *) sd->data;
    double yaw, pitch, roll;

    if (sd->size < sizeof(*spherical)) {
        LOGI(WE_UTILS_LOG_TAG, "invalid data");
        return;
    }

    LOGI(WE_UTILS_LOG_TAG, "%s ", av_spherical_projection_name(spherical->projection));

    yaw = ((double) spherical->yaw) / (1 << 16);
    pitch = ((double) spherical->pitch) / (1 << 16);
    roll = ((double) spherical->roll) / (1 << 16);
    LOGI(WE_UTILS_LOG_TAG, "(%f/%f/%f) ", yaw, pitch, roll);

    if (spherical->projection == AV_SPHERICAL_EQUIRECTANGULAR_TILE) {
        size_t l, t, r, b;
        av_spherical_tile_bounds(spherical, par->width, par->height,
                                 &l, &t, &r, &b);
        LOGI(WE_UTILS_LOG_TAG, "[%zu, %zu, %zu, %zu] ", l, t, r, b);
    } else if (spherical->projection == AV_SPHERICAL_CUBEMAP) {
        LOGI(WE_UTILS_LOG_TAG, "[pad %u] ", spherical->padding);
    }
}

void WeUtils::dump_content_light_metadata(void *ctx, AVPacketSideData *sd) {
    AVContentLightMetadata *metadata = (AVContentLightMetadata *) sd->data;
    LOGI(WE_UTILS_LOG_TAG, "Content Light Level Metadata, "
                           "MaxCLL=%d, MaxFALL=%d",
         metadata->MaxCLL, metadata->MaxFALL);
}

void WeUtils::print_fps(double d, const char *postfix) {
    uint64_t v = lrintf(d * 100);
    if (!v) {
        LOGI(WE_UTILS_LOG_TAG, "%1.4f %s", d, postfix);
    } else if (v % 100) {
        LOGI(WE_UTILS_LOG_TAG, "%3.2f %s", d, postfix);
    } else if (v % (100 * 1000)) {
        LOGI(WE_UTILS_LOG_TAG, "%1.0f %s", d, postfix);
    } else {
        LOGI(WE_UTILS_LOG_TAG, "%1.0fk %s", d / 1000, postfix);
    }
}

void WeUtils::print_gain(void *ctx, const char *str, int32_t gain) {
    LOGI(WE_UTILS_LOG_TAG, "%s - ", str);
    if (gain == INT32_MIN) {
        LOGI(WE_UTILS_LOG_TAG, "unknown");
    } else {
        LOGI(WE_UTILS_LOG_TAG, "%f", gain / 100000.0f);
    }
    LOGI(WE_UTILS_LOG_TAG, ", ");
}

void WeUtils::print_peak(void *ctx, const char *str, uint32_t peak) {
    LOGI(WE_UTILS_LOG_TAG, "%s - ", str);
    if (!peak) {
        LOGI(WE_UTILS_LOG_TAG, "unknown");
    } else {
        LOGI(WE_UTILS_LOG_TAG, "%f", (float) peak / UINT32_MAX);
    }
    LOGI(WE_UTILS_LOG_TAG, ", ");
}

long long WeUtils::getCurrentTimeMill() {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return tv.tv_sec * 1000 + tv.tv_usec / 1000;
}

char *WeUtils::getAVErrorName(int avError) {
    char *ret = "ERROR_NAME_UNKONOW";

    switch (avError) {
        // ------ include/libavutil/error.h ------
        case AVERROR_BSF_NOT_FOUND:
            ret = "AVERROR_BSF_NOT_FOUND";
            break;
        case AVERROR_BUG:
            ret = "AVERROR_BUG";
            break;
        case AVERROR_BUFFER_TOO_SMALL:
            ret = "AVERROR_BUFFER_TOO_SMALL";
            break;
        case AVERROR_DECODER_NOT_FOUND:
            ret = "AVERROR_DECODER_NOT_FOUND";
            break;
        case AVERROR_DEMUXER_NOT_FOUND:
            ret = "AVERROR_DEMUXER_NOT_FOUND";
            break;
        case AVERROR_ENCODER_NOT_FOUND:
            ret = "AVERROR_ENCODER_NOT_FOUND";
            break;
        case AVERROR_EXIT:
            ret = "AVERROR_EXIT";
            break;
        case AVERROR_EXTERNAL:
            ret = "AVERROR_EXTERNAL";
            break;
        case AVERROR_FILTER_NOT_FOUND:
            ret = "AVERROR_FILTER_NOT_FOUND";
            break;
        case AVERROR_INVALIDDATA:
            ret = "AVERROR_INVALIDDATA";
            break;
        case AVERROR_MUXER_NOT_FOUND:
            ret = "AVERROR_MUXER_NOT_FOUND";
            break;
        case AVERROR_OPTION_NOT_FOUND:
            ret = "AVERROR_OPTION_NOT_FOUND";
            break;
        case AVERROR_PATCHWELCOME:
            ret = "AVERROR_PATCHWELCOME";
            break;
        case AVERROR_PROTOCOL_NOT_FOUND:
            ret = "AVERROR_PROTOCOL_NOT_FOUND";
            break;
        case AVERROR_STREAM_NOT_FOUND:
            ret = "AVERROR_STREAM_NOT_FOUND";
            break;
        case AVERROR_BUG2:
            ret = "AVERROR_BUG2";
            break;
        case AVERROR_UNKNOWN:
            ret = "AVERROR_UNKNOWN";
            break;
        case AVERROR_EXPERIMENTAL:
            ret = "AVERROR_EXPERIMENTAL";
            break;
        case AVERROR_INPUT_CHANGED:
            ret = "AVERROR_INPUT_CHANGED";
            break;
        case AVERROR_OUTPUT_CHANGED:
            ret = "AVERROR_OUTPUT_CHANGED";
            break;
        case AVERROR_HTTP_BAD_REQUEST:
            ret = "AVERROR_HTTP_BAD_REQUEST";
            break;
        case AVERROR_HTTP_UNAUTHORIZED:
            ret = "AVERROR_HTTP_UNAUTHORIZED";
            break;
        case AVERROR_HTTP_FORBIDDEN:
            ret = "AVERROR_HTTP_FORBIDDEN";
            break;
        case AVERROR_HTTP_NOT_FOUND:
            ret = "AVERROR_HTTP_NOT_FOUND";
            break;
        case AVERROR_HTTP_OTHER_4XX:
            ret = "AVERROR_HTTP_OTHER_4XX";
            break;
        case AVERROR_HTTP_SERVER_ERROR:
            ret = "AVERROR_HTTP_SERVER_ERROR";
            break;

        // ------ include/asm-generic/errno-base.h ------
        case AVERROR(EPERM):
            ret = "EPERM:Operation not permitted";
            break;
        case AVERROR(ENOENT):
            ret = "ENOENT:No such file or directory";
            break;
        case AVERROR(ESRCH):
            ret = "ESRCH:No such process";
            break;
        case AVERROR(EINTR):
            ret = "EINTR:Interrupted system call";
            break;
        case AVERROR(EIO):
            ret = "EIO:I/O error";
            break;
        case AVERROR(ENXIO):
            ret = "ENXIO:No such device or address";
            break;
        case AVERROR(E2BIG):
            ret = "E2BIG:Arg list too long";
            break;
        case AVERROR(ENOEXEC):
            ret = "ENOEXEC:Exec format error";
            break;
        case AVERROR(EBADF):
            ret = "EBADF:Bad file number";
            break;
        case AVERROR(ECHILD):
            ret = "ECHILD:No child processes";
            break;
        case AVERROR(EAGAIN):
            ret = "EAGAIN:Try again";
            break;
        case AVERROR(ENOMEM):
            ret = "ENOMEM:Out of memory";
            break;
        case AVERROR(EACCES):
            ret = "EACCES:Permission denied";
            break;
        case AVERROR(EFAULT):
            ret = "EFAULT:Bad address";
            break;
        case AVERROR(ENOTBLK):
            ret = "ENOTBLK:Block device required";
            break;
        case AVERROR(EBUSY):
            ret = "EBUSY:Device or resource busy";
            break;
        case AVERROR(EEXIST):
            ret = "EEXIST:File exists";
            break;
        case AVERROR(EXDEV):
            ret = "EXDEV:Cross-device link";
            break;
        case AVERROR(ENODEV):
            ret = "ENODEV:No such device";
            break;
        case AVERROR(ENOTDIR):
            ret = "ENOTDIR:Not a directory";
            break;
        case AVERROR(EISDIR):
            ret = "EISDIR:Is a directory";
            break;
        case AVERROR(EINVAL):
            ret = "EINVAL:Invalid argument";
            break;
        case AVERROR(ENFILE):
            ret = "ENFILE:File table overflow";
            break;
        case AVERROR(EMFILE):
            ret = "EMFILE:Too many open files";
            break;
        case AVERROR(ENOTTY):
            ret = "ENOTTY:Not a typewriter";
            break;
        case AVERROR(ETXTBSY):
            ret = "ETXTBSY:Text file busy";
            break;
        case AVERROR(EFBIG):
            ret = "EFBIG:File too large";
            break;
        case AVERROR(ENOSPC):
            ret = "ENOSPC:No space left on device";
            break;
        case AVERROR(ESPIPE):
            ret = "ESPIPE:Illegal seek";
            break;
        case AVERROR(EROFS):
            ret = "EROFS:Read-only file system";
            break;
        case AVERROR(EMLINK):
            ret = "EMLINK:Too many links";
            break;
        case AVERROR(EPIPE):
            ret = "EPIPE:Broken pipe";
            break;
        case AVERROR(EDOM):
            ret = "EDOM:Math argument out of domain of func";
            break;
        case AVERROR(ERANGE):
            ret = "ERANGE:Math result out of range";
            break;

        // ------ include/asm-generic/errno.h ------（部分）
        case AVERROR(ETIMEDOUT):
            ret = "ETIMEDOUT:Connection timed out";
            break;
        case AVERROR(ENAMETOOLONG):
            ret = "ENAMETOOLONG:File name too long";
            break;
        case AVERROR(ENOTEMPTY):
            ret = "ENOTEMPTY:Directory not empty";
            break;
        case AVERROR(ECHRNG):
            ret = "ECHRNG:Channel number out of range";
            break;
        case AVERROR(ENODATA):
            ret = "ENODATA:No data available";
            break;
        case AVERROR(ENOSR):
            ret = "ENOSR:Out of streams resources";
            break;
        case AVERROR(ECOMM):
            ret = "ECOMM:Communication error on send";
            break;
        case AVERROR(EPROTO):
            ret = "EPROTO:Protocol error";
            break;
        case AVERROR(EOVERFLOW):
            ret = "EOVERFLOW:Value too large for defined data type";
            break;
        case AVERROR(EREMCHG):
            ret = "EREMCHG:Remote address changed";
            break;
        case AVERROR(ESTRPIPE):
            ret = "ESTRPIPE:Streams pipe error";
            break;
        case AVERROR(EMSGSIZE):
            ret = "EMSGSIZE:Message too long";
            break;
        case AVERROR(EPROTOTYPE):
            ret = "EPROTOTYPE:Protocol wrong type for socket";
            break;
        case AVERROR(ENOPROTOOPT):
            ret = "ENOPROTOOPT:Protocol not available";
            break;
        case AVERROR(EPROTONOSUPPORT):
            ret = "EPROTONOSUPPORT:Protocol not supported";
            break;
        case AVERROR(ENETDOWN):
            ret = "ENETDOWN:Network is down";
            break;
        case AVERROR(ENETUNREACH):
            ret = "ENETUNREACH:Network is unreachable";
            break;
        case AVERROR(ENETRESET):
            ret = "ENETRESET:Network dropped connection because of reset";
            break;
        case AVERROR(ECONNRESET):
            ret = "ECONNRESET:Connection reset by peer";
            break;
        case AVERROR(ENOBUFS):
            ret = "ENOBUFS:No buffer space available";
            break;
        case AVERROR(ECONNREFUSED):
            ret = "ECONNREFUSED:Connection refused";
            break;
    }
    return ret;
}

