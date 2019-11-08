#include <jni.h>
#include <string>
#include "AndroidLog.h"

extern "C"
{
#include <libavformat/avformat.h>
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_wtz_ffmpegapi_API_stringFromJNI(JNIEnv *env, jobject thiz) {
    std::string hello = "Welcome to FFmpeg";
    return env->NewStringUTF(hello.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_API_testFFmpeg(JNIEnv *env, jobject thiz) {
    av_register_all();
    AVCodec *codec = av_codec_next(NULL);
    while (codec != NULL)
    {
        switch (codec->type)
        {
            case AVMEDIA_TYPE_VIDEO:
                LOGI("[Video]:%s", codec->name);
                break;
            case AVMEDIA_TYPE_AUDIO:
                LOGI("[Audio]:%s", codec->name);
                break;
            default:
                LOGI("[Other]:%s", codec->name);
                break;
        }
        codec = codec->next;
    }
}
