package com.wtz.ffmpegapi;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import com.wtz.ffmpegapi.utils.LogUtils;

import java.io.File;

public class PCMRecorder {

    /**
     * Encode pcm data to another mime type data
     */
    public interface Encoder {
        /**
         * 对 PCM 编码前的准备工作，不可以耗时阻塞
         *
         * @param sampleRate          采样率
         * @param channelNums         通道个数
         * @param bitsPerSample       每个采样编码保存位数
         * @param maxBytesPerCallback 每次回调 PCM 数据最大字节数
         * @param saveFile            要保存的文件
         * @return true:启动准备成功
         */
        boolean start(int sampleRate, int channelNums, int bitsPerSample, int maxBytesPerCallback, File saveFile);

        /**
         * 处理 PCM 数据并保存到文件
         *
         * @param pcmData
         * @param size
         */
        void encode(byte[] pcmData, int size);

        /**
         * 停止录制并释放资源，不可以耗时阻塞
         */
        void stop();
    }

    public interface OnStartResultListener {
        /**
         * @param success true:启动成功
         */
        void onResult(boolean success);
    }

    static class StartParams {
        public Encoder encoder;
        public int sampleRate;
        public int channelNums;
        public int bitsPerSample;
        public int maxBytesPerCallback;
        public File saveFile;
        public OnStartResultListener listener;
    }

    private static final String TAG = "PCMRecorder";

    private Encoder mEncoder;
    private static final int RECORD_IDLE = 0;
    private static final int RECORD_STARTING = 1;
    private static final int RECORD_STARTED = 2;
    private static final int RECORD_STOPPING = 3;
    private static final int RECORD_STOPPED = 4;
    private int mRecordState = RECORD_IDLE;

    private boolean isReleased;
    private Handler mWorkHandler;
    private HandlerThread mWorkThread;
    private static final int HANDLE_START = 1;
    private static final int HANDLE_ENCODE = 2;
    private static final int HANDLE_STOP = 3;
    private static final int HANDLE_RELEASE = 4;

    private int mSampledSizePerSecond;// 1秒最大采样字节大小
    private double mRecordTimeSecs;// 当前已录制时间，单位：秒

    public PCMRecorder() {
        mWorkThread = new HandlerThread("PCMRecorder");
        mWorkThread.start();
        mWorkHandler = new Handler(mWorkThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                int msgType = msg.what;
                if (msgType != HANDLE_ENCODE) {
                    LogUtils.d(TAG, "mWorkHandler handleMessage: " + msgType);
                }
                if (isReleased && msgType != HANDLE_RELEASE) {
                    Log.e(TAG, "mWorkHandler handleMessage " + msgType + " but PCMRecorder is already destroyed!");
                    return;
                }
                switch (msgType) {
                    case HANDLE_START:
                        handleStart(msg);
                        break;

                    case HANDLE_ENCODE:
                        handleEncode(msg);
                        break;

                    case HANDLE_STOP:
                        handleStop();
                        break;

                    case HANDLE_RELEASE:
                        handleRelease();
                        break;
                }
            }
        };
    }

    public void start(Encoder encoder, int sampleRate, int channelNums, int bitsPerSample, int maxBytesPerCallback, File saveFile,
                      OnStartResultListener listener) {
        if (isReleased) {
            LogUtils.e(TAG, "Call start but is already released");
            // 已经 released 就不用再回调 listener
            return;
        }

        StartParams startParams = new StartParams();
        startParams.encoder = encoder;
        startParams.sampleRate = sampleRate;
        startParams.channelNums = channelNums;
        startParams.bitsPerSample = bitsPerSample;
        startParams.maxBytesPerCallback = maxBytesPerCallback;
        startParams.saveFile = saveFile;
        startParams.listener = listener;

        Message msg = mWorkHandler.obtainMessage(HANDLE_START);
        msg.obj = startParams;
        mWorkHandler.sendMessage(msg);
    }

    private void handleStart(Message msg) {
        StartParams startParams = (StartParams) msg.obj;
        if (mRecordState != RECORD_IDLE && mRecordState != RECORD_STOPPED) {
            LogUtils.e(TAG, "handleStart but mRecordState is illegal");
            if (startParams.listener != null) {
                startParams.listener.onResult(false);
            }
            return;
        }

        if (startParams.encoder == null) {
            LogUtils.e(TAG, "handleStart but encoder is null");
            if (startParams.listener != null) {
                startParams.listener.onResult(false);
            }
            return;
        }

        mRecordState = RECORD_STARTING;
        mEncoder = startParams.encoder;
        mRecordTimeSecs = 0;
        mSampledSizePerSecond = startParams.channelNums * startParams.sampleRate * startParams.bitsPerSample / 8;
        if (!mEncoder.start(startParams.sampleRate, startParams.channelNums,
                startParams.bitsPerSample, startParams.maxBytesPerCallback, startParams.saveFile)) {
            mEncoder.stop();
            mRecordState = RECORD_STOPPED;
            if (startParams.listener != null) {
                startParams.listener.onResult(false);
            }
            return;
        }

        mRecordState = RECORD_STARTED;
        if (startParams.listener != null) {
            startParams.listener.onResult(true);
        }
    }

    public void encode(byte[] pcmData, int size) {
        if (isReleased) {
            LogUtils.e(TAG, "Call encode but is already released");
            return;
        }

        Message msg = mWorkHandler.obtainMessage(HANDLE_ENCODE);
        msg.obj = pcmData;
        msg.arg1 = size;
        mWorkHandler.sendMessage(msg);
    }

    private void handleEncode(Message msg) {
        if (mEncoder == null || mRecordState != RECORD_STARTED) {
            LogUtils.e(TAG, "handleEncode but mEncoder is null or state is not started");
            return;
        }

        mRecordTimeSecs += msg.arg1 * 1.0 / mSampledSizePerSecond;
        mEncoder.encode((byte[]) msg.obj, msg.arg1);
    }

    public double getRecordTimeSecs() {
        return mRecordTimeSecs;
    }

    public void stop() {
        if (isReleased) {
            LogUtils.e(TAG, "Call stop but is already released");
            return;
        }
        Message msg = mWorkHandler.obtainMessage(HANDLE_STOP);
        mWorkHandler.sendMessage(msg);
    }

    private void handleStop() {
        mRecordState = RECORD_STOPPING;
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder = null;
        }
        mRecordState = RECORD_STOPPED;
    }

    public void release() {
        LogUtils.d(TAG, "Call release...");
        if (isReleased) {
            return;
        }
        // 首先置总的标志位，阻止消息队列的正常消费
        isReleased = true;

        // 然后停止前驱：工作线程
        mWorkHandler.removeCallbacksAndMessages(null);
        Message msg = mWorkHandler.obtainMessage(HANDLE_RELEASE);
        mWorkHandler.sendMessage(msg);
    }

    private void handleRelease() {
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder = null;
        }

        mWorkHandler.removeCallbacksAndMessages(null);
        mWorkHandler = null;

        try {
            mWorkThread.quit();
        } catch (Exception e) {
            e.printStackTrace();
        }
        mWorkThread = null;
        LogUtils.d(TAG, "release complete");
    }

}
