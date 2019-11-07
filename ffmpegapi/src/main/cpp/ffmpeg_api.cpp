#include <jni.h>
#include <AndroidLog.h>
#include <string>

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
    AVCodec *c_temp = av_codec_next(NULL);
    while (c_temp != NULL)
    {
        switch (c_temp->type)
        {
            case AVMEDIA_TYPE_VIDEO:
                LOGI("[Video]:%s", c_temp->name);
                break;
            case AVMEDIA_TYPE_AUDIO:
                LOGI("[Audio]:%s", c_temp->name);
                break;
            default:
                LOGI("[Other]:%s", c_temp->name);
                break;
        }
        c_temp = c_temp->next;
    }
}
