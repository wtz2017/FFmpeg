#include <jni.h>
#include <pthread.h>
#include <unistd.h>
#include <string>
#include <assert.h>
#include "AndroidLog.h"
#include "JavaListener.h"
#include "OnResultListener.h"
#include "queue"

extern "C"
{
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
}

#define LOG_TAG "ThreadDemo"

JavaVM *jvm;

pthread_t simpleThread;

bool stopProduce = false;
pthread_t producerThread;
pthread_t consumerThread;
pthread_mutex_t productMutex;
pthread_cond_t productCond;
std::queue<int> productQueue;

pthread_t callJavaThread;

//------------ OpenSL ES Test Start------------
// 引擎
SLObjectItf engineObject = NULL;
SLEngineItf engine = NULL;

// 混音器
SLObjectItf outputMixObject = NULL;
SLEnvironmentalReverbItf outputMixEnvironmentalReverb = NULL;

// 播放器
SLObjectItf playerObject = NULL;
SLPlayItf playController = NULL;
SLVolumeItf volumeController = NULL;
SLAndroidSimpleBufferQueueItf pcmBufferQueue = NULL;

FILE *pcmFile;
const int BYTES_SAMPLED_PER_SECOND = 44100 * 2 * 2;
uint8_t *readBuffer;// 字节数组指针
void *enqueueBuffer;// 播放数据入队 buffer
//------------ OpenSL ES Test End------------

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    jvm = vm;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE(LOG_TAG, "JNI_OnLoad vm->GetEnv exception!");
        return -1;
    }
    return JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_wtz_ffmpegapi_CppThreadDemo_stringFromJNI(JNIEnv *env, jobject thiz) {
    std::string hello = "Welcome to FFmpeg";
    return env->NewStringUTF(hello.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_CppThreadDemo_stringToJNI(JNIEnv *env, jobject thiz, jstring jstr) {
    const char *chars = env->GetStringUTFChars(jstr, NULL);
    LOGI(LOG_TAG, "stringToJNI GetStringUTFChars content: %s", chars);
    env->ReleaseStringUTFChars(jstr, chars);

    /**
     * GetStringUTFRegion 需要使用对应的 GetStringUTFLength 来获取 UTF-8 字符串所需要的
     * 字节个数（不包括结束的 '\0'），对于 char 数组大小要比它大 1，并在 char 数组最后一位写结束符 '\0'
     */
    int jstrUtf8Len = env->GetStringUTFLength(jstr);
    char *buf = new char[jstrUtf8Len + 1];
    env->GetStringUTFRegion(jstr, 0, jstrUtf8Len, buf);
    buf[jstrUtf8Len] = '\0';
    LOGI(LOG_TAG, "stringToJNI GetStringUTFRegion content: %s", buf);
    delete[] buf;
}

void *simpleThreadCallback(void *data) {
    LOGI(LOG_TAG, "This is a simple thread!(pid=%d tid=%d)", getpid(), gettid());
    pthread_exit(&simpleThread);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_CppThreadDemo_testSimpleThread(JNIEnv *env, jobject thiz) {
    LOGI(LOG_TAG, "Call testSimpleThread from java!(pid=%d tid=%d)", getpid(), gettid());
    pthread_create(&simpleThread, NULL, simpleThreadCallback, NULL);
}

void *producerCallback(void *data) {
    LOGI(LOG_TAG, "This is producer thread!(pid=%d tid=%d)", getpid(), gettid());
    while (!stopProduce) {
        pthread_mutex_lock(&productMutex);

        productQueue.push(1);
        LOGI(LOG_TAG, "生产了一个产品，目前总量为%d，准备通知消费者", productQueue.size());
        pthread_cond_signal(&productCond);

        pthread_mutex_unlock(&productMutex);

        sleep(5);// 睡眠 5 秒
    }
    LOGE(LOG_TAG, "producer exiting...");
    pthread_exit(&producerThread);
}

void *consumerCallback(void *data) {
    LOGI(LOG_TAG, "This is consumer thread!(pid=%d tid=%d)", getpid(), gettid());
    while (!stopProduce) {
        pthread_mutex_lock(&productMutex);

        if (productQueue.size() > 0) {
            productQueue.pop();
            LOGI(LOG_TAG, "消费了一个产品，还剩%d个产品", productQueue.size());
        } else {
            LOGD(LOG_TAG, "没有产品可消费，等待中...");
            pthread_cond_wait(&productCond, &productMutex);
        }

        pthread_mutex_unlock(&productMutex);

        usleep(500 * 1000);// 睡眠 500000 微秒，即 500 毫秒
    }
    LOGE(LOG_TAG, "consumer exiting...");
    pthread_exit(&consumerThread);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_CppThreadDemo_startProduceConsumeThread(JNIEnv *env, jobject thiz) {
    LOGI(LOG_TAG, "Call startProduceConsumeThread from java!(pid=%d tid=%d)", getpid(), gettid());
    stopProduce = false;

    for (int i = 0; i < 10; ++i) {
        productQueue.push(i);
    }

    pthread_mutex_init(&productMutex, NULL);
    pthread_cond_init(&productCond, NULL);

    pthread_create(&producerThread, NULL, producerCallback, NULL);
    pthread_create(&consumerThread, NULL, consumerCallback, NULL);
}

void clearQueue(std::queue<int> &queue) {
    std::queue<int> empty;
    swap(empty, queue);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_CppThreadDemo_stopProduceConsumeThread(JNIEnv *env, jobject thiz) {
    LOGI(LOG_TAG, "Call stopProduceConsumeThread from java!(pid=%d tid=%d)", getpid(), gettid());
    stopProduce = true;

    pthread_cond_signal(&productCond);// 唤醒还在等待的消费者
    pthread_cond_destroy(&productCond);
    pthread_mutex_destroy(&productMutex);

    clearQueue(productQueue);
}

void *callJavaThreadCallback(void *data) {
    JavaListener *listener = (JavaListener *) data;
    listener->callback(2, 2, "call java from c++ in child thread");
    delete listener;
    pthread_exit(&callJavaThread);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_CppThreadDemo_callbackFromC(JNIEnv *env, jobject thiz) {
    // JavaListener 构造方法需要在 C++ 主线程中调用，即直接从 java 层调用下来的线程
    JavaListener *javaListener = new OnResultListener(jvm, env, thiz);

    javaListener->callback(2, 1, "call java from c++ in main thread");

    pthread_create(&callJavaThread, NULL, callJavaThreadCallback, javaListener);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_CppThreadDemo_setByteArray(JNIEnv *env, jobject thiz, jbyteArray jarray) {
    // 1. 获取 java 数组长度
    int arrayLen = env->GetArrayLength(jarray);

    // 2. 获取指向 java 数组或其副本的指针，具体是哪个由底层实现，第二个参数若不空用来反馈实现者是否拷贝的结果
    jbyte *pArray = env->GetByteArrayElements(jarray, NULL);

    // 3. 处理数据，如果底层实现是非拷贝方式，下述代码会直接修改原始 java 数组元素
    for (int i = 0; i < arrayLen; i++) {
        pArray[i] += 2;
    }

    // 4. 释放数组指针，对于 copy 模式，第 3 个参数决定如何管理 copy 内存和是否提交 copy 的值到原始 java 数组
    env->ReleaseByteArrayElements(jarray, pArray, 0);

//    // 2. 申请缓冲区
////    jbyte *copyArray = (jbyte *) malloc(sizeof(jbyte) * arrayLen);
//    jbyte *copyArray = new jbyte[arrayLen];
//
//
//    // 3. 初始化缓冲区
////    memset(copyArray, 0, sizeof(jbyte) * arrayLen);
//    for (int i = 0; i < arrayLen; ++i) {
//        copyArray[i] = 0;
//    }
//
//    // 4. 拷贝 java 数组数据
//    env->GetByteArrayRegion(jarray, 0, arrayLen, copyArray);
//
//    // 5. 处理数据
//    for (int i = 0; i < arrayLen; i++) {
//        copyArray[i] += 2;
//        LOGI(LOG_TAG, "after modify copyArray %d = %d", i, copyArray[i]);
//    }
//
//    // 如果想把处理结果同步到原始 java 数组，可以这么做
////    env->SetByteArrayRegion(jarray, 0, arrayLen, copyArray);
//
//    // 6. 释放缓冲区
////    free(copyArray);
//    delete[] copyArray;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_wtz_ffmpegapi_CppThreadDemo_getByteArray(JNIEnv *env, jobject thiz) {
    int arrayLen = 10;

//    jbyte *pbuf = (jbyte *) malloc(sizeof(jbyte) * arrayLen);
    jbyte *pbuf = new jbyte[arrayLen];
    for (int i = 0; i < arrayLen; i++) {
        pbuf[i] = i + 2;
    }

    jbyteArray jarray = env->NewByteArray(arrayLen);
    env->SetByteArrayRegion(jarray, 0, arrayLen, pbuf);

//    free(pbuf);
    delete[] pbuf;

    return jarray;
}

//------------ OpenSL ES Test Start------------
/**
 * destroy buffer queue audio player object, and invalidate all associated interfaces
 */
void destroyBufferQueueAudioPlayer() {
    if (playerObject != NULL) {
        (*playerObject)->Destroy(playerObject);
        playerObject = NULL;
        playController = NULL;
        volumeController = NULL;
        pcmBufferQueue = NULL;
    }
}

/**
 * destroy output mix object, and invalidate all associated interfaces
 */
void destroyOutputMixer() {
    if (outputMixObject != NULL) {
        (*outputMixObject)->Destroy(outputMixObject);
        outputMixObject = NULL;
        outputMixEnvironmentalReverb = NULL;
    }
}

/**
 * destroy engine object, and invalidate all associated interfaces
 */
void destroyEngine() {
    if (engineObject != NULL) {
        (*engineObject)->Destroy(engineObject);
        engineObject = NULL;
        engine = NULL;
    }
}

/**
 * 确保在退出应用时销毁所有对象。
 * 对象应按照与创建时相反的顺序销毁，因为销毁具有依赖对象的对象并不安全。
 * 例如，请按照以下顺序销毁：音频播放器和录制器、输出混合，最后是引擎。
 */
void shuttdownPlayer() {
    destroyBufferQueueAudioPlayer();
    destroyOutputMixer();
    destroyEngine();
}

bool initEngine(SLObjectItf *engineObject, SLEngineItf *engine) {
    // create engine object
    SLresult result;
    result = slCreateEngine(engineObject, 0, NULL, 0, NULL, NULL);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "slCreateEngine exception!");
        return false;
    }

    // realize the engine object
    (void) result;
    result = (**engineObject)->Realize(*engineObject, SL_BOOLEAN_FALSE);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "engineObject Realize exception!");
        destroyEngine();
        return false;
    }

    // get the engine interface, which is needed in order to create other objects
    (void) result;
    result = (**engineObject)->GetInterface(*engineObject, SL_IID_ENGINE, engine);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "GetInterface SLEngineItf exception!");
        destroyEngine();
        return false;
    }

    return true;
}

bool initOutputMix(SLEngineItf *engine, SLObjectItf *outputMixObject) {
    // create output mix, with environmental reverb specified as a non-required interface
    const SLInterfaceID ids[1] = {SL_IID_ENVIRONMENTALREVERB};
    const SLboolean reqs[1] = {SL_BOOLEAN_FALSE};
    SLresult result;
    result = (**engine)->CreateOutputMix(*engine, outputMixObject, 1, ids, reqs);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "CreateOutputMix exception!");
        return false;
    }

    // realize the output mix
    (void) result;
    result = (**outputMixObject)->Realize(*outputMixObject, SL_BOOLEAN_FALSE);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "outputMixObject Realize exception!");
        destroyOutputMixer();
        return false;
    }

    return true;
}

bool setEnvironmentalReverb(SLObjectItf *outputMixObject,
                            SLEnvironmentalReverbItf *outputMixEnvironmentalReverb) {
    // get the environmental reverb interface
    // this could fail if the environmental reverb effect is not available,
    // either because the feature is not present, excessive CPU load, or
    // the required MODIFY_AUDIO_SETTINGS permission was not requested and granted
    SLresult result;
    result = (**outputMixObject)->GetInterface(*outputMixObject, SL_IID_ENVIRONMENTALREVERB,
                                               outputMixEnvironmentalReverb);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "GetInterface SLEnvironmentalReverbItf exception!");
        return false;
    }

    // aux effect on the output mix, used by the buffer queue player
    const SLEnvironmentalReverbSettings reverbSettings = SL_I3DL2_ENVIRONMENT_PRESET_STONECORRIDOR;
    result = (**outputMixEnvironmentalReverb)->SetEnvironmentalReverbProperties(
            *outputMixEnvironmentalReverb, &reverbSettings);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "SetEnvironmentalReverbProperties exception!");
        return false;
    }

    return true;
}

bool createBufferQueueAudioPlayer(SLEngineItf *engine, SLObjectItf *playerObject,
                                  SLPlayItf *playController, SLVolumeItf *volumeController) {
    // configure audio source
    SLDataLocator_AndroidSimpleBufferQueue bufferQueueLocator = {
            SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2
    };
    SLDataFormat_PCM pcmFormat = {
            SL_DATAFORMAT_PCM,// 数据格式：pcm
            2,// 声道数：2个（立体声）
            SL_SAMPLINGRATE_44_1,// 采样率：44100hz
            SL_PCMSAMPLEFORMAT_FIXED_16,// bitsPerSample：16位
            SL_PCMSAMPLEFORMAT_FIXED_16,// containerSize：和采样位数一致就行
            SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT,// 立体声（前左前右）
            SL_BYTEORDER_LITTLEENDIAN// 字节排列顺序：小端 little-endian，将低序字节存储在起始地址
    };
    SLDataSource audioSrc = {&bufferQueueLocator, &pcmFormat};

    // configure audio sink
    SLDataLocator_OutputMix outputMixLocator = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
    SLDataSink audioSnk = {&outputMixLocator, NULL};

    /*
     * create audio player:
     *     fast audio does not support when SL_IID_EFFECTSEND is required, skip it
     *     for fast audio case
     */
    const SLInterfaceID ids[3] = {SL_IID_BUFFERQUEUE, SL_IID_VOLUME, SL_IID_EFFECTSEND,
            /*SL_IID_MUTESOLO,*/};
    const SLboolean reqs[3] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE,
            /*SL_BOOLEAN_TRUE,*/ };
    SLresult result;
    result = (**engine)->CreateAudioPlayer(
            *engine, playerObject, &audioSrc, &audioSnk, 3, ids, reqs);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "CreateAudioPlayer exception!");
        return false;
    }

    // realize the player
    (void) result;
    result = (**playerObject)->Realize(*playerObject, SL_BOOLEAN_FALSE);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "playerObject Realize exception!");
        destroyBufferQueueAudioPlayer();
        return false;
    }

    result = (**playerObject)->GetInterface(*playerObject, SL_IID_PLAY, playController);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "GetInterface SLPlayItf exception!");
        destroyBufferQueueAudioPlayer();
        return false;
    }

    result = (**playerObject)->GetInterface(*playerObject, SL_IID_VOLUME, volumeController);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "GetInterface SLVolumeItf exception!");
        destroyBufferQueueAudioPlayer();
        return false;
    }

    return true;
}

bool
setBufferQueueCallback(SLObjectItf *playerObject, SLAndroidSimpleBufferQueueItf *pcmBufferQueue,
                       slAndroidSimpleBufferQueueCallback callback) {
    // get the buffer queue interface
    SLresult result;
    result = (**playerObject)->GetInterface(*playerObject, SL_IID_BUFFERQUEUE, pcmBufferQueue);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "GetInterface SLAndroidSimpleBufferQueueItf exception!");
        destroyBufferQueueAudioPlayer();
        return false;
    }

    // register callback on the buffer queue
    (void) result;
    result = (**pcmBufferQueue)->RegisterCallback(*pcmBufferQueue, callback, NULL);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "pcmBufferQueue RegisterCallback exception!");
        destroyBufferQueueAudioPlayer();
        return false;
    }

    return true;
}

bool setPlayState(SLPlayItf *playController, SLuint32 state) {
    SLresult result;
    result = (**playController)->SetPlayState(*playController, state);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "SetPlayState %d exception!", state);
        return false;
    }

    return true;
}

int getPcmData(void **buf) {
    int size = 0;
    if (!feof(pcmFile)) {
        size = fread(readBuffer, 1, BYTES_SAMPLED_PER_SECOND, pcmFile);
        if (size < BYTES_SAMPLED_PER_SECOND) {
            LOGI(LOG_TAG, "read last data!");
        } else {
            LOGI(LOG_TAG, "reading...");
        }
        *buf = readBuffer;
    } else {
        LOGI(LOG_TAG, "file end");
        *buf = NULL;
    }
    return size;
}

void pcmBufferCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {
    int size = getPcmData(&enqueueBuffer);
    if (NULL != enqueueBuffer) {
        SLresult result;
        result = (*pcmBufferQueue)->Enqueue(pcmBufferQueue, enqueueBuffer, size);
        if (SL_RESULT_SUCCESS != result) {
            LOGE(LOG_TAG, "BufferQueue Enqueue exception!");
        }
    }
}

// https://github.com/android/ndk-samples/blob/master/native-audio/app/src/main/cpp/native-audio-jni.c
extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_ffmpegapi_CppThreadDemo_playPCM(JNIEnv *env, jobject thiz, jstring jpath) {
    // 读取 pcm 文件
    const char *path = env->GetStringUTFChars(jpath, 0);
    pcmFile = fopen(path, "r");
    env->ReleaseStringUTFChars(jpath, path);
    if (pcmFile == NULL) {
        LOGE(LOG_TAG, "fopen file error: %s", path);
        return;
    }

    // 初始化引擎
    if (!initEngine(&engineObject, &engine)) {
        shuttdownPlayer();
        return;
    }

    // 使用引擎创建混音器
    if (!initOutputMix(&engine, &outputMixObject)) {
        shuttdownPlayer();
        return;
    }
    // 使用混音器设置混音效果
    setEnvironmentalReverb(&outputMixObject, &outputMixEnvironmentalReverb);

    // 使用引擎创建数据源为缓冲队列的播放器
    if (!createBufferQueueAudioPlayer(&engine, &playerObject, &playController, &volumeController)) {
        shuttdownPlayer();
        return;
    }
    // 设置播放器缓冲队列回调函数
    if (!setBufferQueueCallback(&playerObject, &pcmBufferQueue, pcmBufferCallback)) {
        shuttdownPlayer();
        return;
    }
    // 设置播放状态为正在播放
    setPlayState(&playController, SL_PLAYSTATE_PLAYING);

    // 初始化文件读缓冲器
    readBuffer = (uint8_t *) malloc(BYTES_SAMPLED_PER_SECOND);
    // 主动调用缓冲队列回调函数开始工作
    pcmBufferCallback(pcmBufferQueue, NULL);
}
//------------ OpenSL ES Test End------------