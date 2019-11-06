package com.wtz.ffmpegapi;

public class API {

    static {
        System.loadLibrary("ffmpeg_api");
        System.loadLibrary("avcodec");
        System.loadLibrary("avdevice");
        System.loadLibrary("avfilter");
        System.loadLibrary("avformat");
        System.loadLibrary("avutil");
        System.loadLibrary("postproc");
        System.loadLibrary("swresample");
        System.loadLibrary("swscale");
    }

    public native String stringFromJNI();
    public native void testFFmpeg();
}
