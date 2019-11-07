#ifndef FFMPEG_ANDROIDLOG_H
#define FFMPEG_ANDROIDLOG_H

#include <android/log.h>

#define LOG_TAG           "ffmpeg_api"
#define LOGI(FORMAT, ...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, FORMAT, ##__VA_ARGS__);
#define LOGD(FORMAT, ...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, FORMAT, ##__VA_ARGS__);
#define LOGE(FORMAT, ...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, FORMAT, ##__VA_ARGS__);

#endif //FFMPEG_ANDROIDLOG_H
