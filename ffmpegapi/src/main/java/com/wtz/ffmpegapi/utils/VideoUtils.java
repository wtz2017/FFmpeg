package com.wtz.ffmpegapi.utils;

import android.media.MediaCodecList;

import java.util.HashMap;
import java.util.Map;

public class VideoUtils {

    private static final Map<String, String> HARD_CODEC_MAP = new HashMap<>();

    static {
        // 要与 Native 层 weplayer/WeVideoDecoder.cpp 中的 avBitStreamFilter 对应起来
//        HARD_CODEC_MAP.put("h263", "video/3gpp");// Native 层未支持
        HARD_CODEC_MAP.put("h264", "video/avc");
        HARD_CODEC_MAP.put("h265", "video/hevc");
        HARD_CODEC_MAP.put("hevc", "video/hevc");
//        HARD_CODEC_MAP.put("mpeg2", "video/mpeg2");// Native 层未支持
        HARD_CODEC_MAP.put("mpeg4", "video/mp4v-es");
//        HARD_CODEC_MAP.put("wmv", "video/x-ms-wmv");// Native 层未支持
//        HARD_CODEC_MAP.put("wmv3", "video/wmv3");// wmv3：Windows Media Video 9，// Native 层未支持
//        HARD_CODEC_MAP.put("vc1", "video/vc1");// vc1：Video Codec 1，早期基于 WMV9，多为 .wmv后缀
//        HARD_CODEC_MAP.put("vp8", "video/x-vnd.on2.vp8");// video in .webm，// Native 层未支持
        HARD_CODEC_MAP.put("vp9", "video/x-vnd.on2.vp9");// video in .webm，由Google开发的开放格式、无使用授权费的视频压缩标准
    }

    public static String findHardCodecType(String ffmpegCodecType) {
        if (HARD_CODEC_MAP.containsKey(ffmpegCodecType)) {
            return HARD_CODEC_MAP.get(ffmpegCodecType);
        }
        return "";
    }

    public static boolean isSupportHardCodec(String ffmpegCodecType) {
        boolean support = false;

        int codecNum = MediaCodecList.getCodecCount();
        for (int i = 0; i < codecNum; i++) {
            String[] types = MediaCodecList.getCodecInfoAt(i).getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equals(findHardCodecType(ffmpegCodecType))) {
                    support = true;
                    break;
                }
            }
            if (support) break;
        }

        return support;
    }

}
