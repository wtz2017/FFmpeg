package com.wtz.ffmpegapi;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.wtz.ffmpegapi.utils.LogUtils;

import java.io.File;

public class WeEditor {
    private static final String TAG = "WeEditorJava";

    static {
        System.loadLibrary("weplayer");
        System.loadLibrary("avcodec");
        System.loadLibrary("avfilter");
        System.loadLibrary("avformat");
        System.loadLibrary("avutil");
        System.loadLibrary("swresample");
        System.loadLibrary("swscale");
    }

    private native void nativeCreateEditor();

    private native void nativeSetEditDataSource(String dataSource);

    private native void nativePrepareEdit();

    private native void nativeStartEdit(int startTimeMsec, int endTimeMsec);

    private native void nativeSetStopEditFlag();

    private native void nativeStopEdit();

    private native void nativeResetEdit();

    private native void nativeReleaseEdit();

    private native int nativeGetEditDuration();

    private native int nativeGetEditPosition();

    private native int nativeGetEditAudioSampleRate();

    private native int nativeGetEditAudioChannelNums();

    private native int nativeGetEditAudioBitsPerSample();

    private native int nativeGetEditPcmMaxBytesPerCallback();

    private OnPreparedListener mOnPreparedListener;
    private OnLoadingDataListener mOnLoadingDataListener;
    private OnErrorListener mOnErrorListener;
    private OnCompletionListener mOnCompletionListener;

    private String mDataSource;
    private boolean isPrepared;
    private boolean isCompleted;
    private boolean isReleased;

    private PCMRecorder mPCMRecorder;
    private int mEditRangeMsec;

    private Handler mUIHandler;// 用以把回调切换到主线程，不占用工作线程资源
    private Handler mWorkHandler;
    private HandlerThread mWorkThread;
    private static final int HANDLE_CREATE_EDITOR = 0;
    private static final int HANDLE_SET_DATA_SOURCE = 1;
    private static final int HANDLE_PREPARE_ASYNC = 2;
    private static final int HANDLE_START_EDIT = 3;
    private static final int HANDLE_STOP = 4;
    private static final int HANDLE_RESET = 5;
    private static final int HANDLE_RELEASE = 6;

    public interface OnPreparedListener {
        void onPrepared();
    }

    public interface OnLoadingDataListener {
        void onLoading(boolean isLoading);
    }

    public interface OnErrorListener {
        void onError(int code, String msg);
    }

    public interface OnCompletionListener {
        void onCompletion();
    }

    public WeEditor() {
        mUIHandler = new Handler(Looper.getMainLooper());
        mWorkThread = new HandlerThread("WeEditor-dispatcher");
        mWorkThread.start();
        mWorkHandler = new Handler(mWorkThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                int msgType = msg.what;
                LogUtils.d(TAG, "mWorkHandler handleMessage: " + msgType);
                if (isReleased && msgType != HANDLE_RELEASE) {
                    Log.e(TAG, "mWorkHandler handleMessage but Player is already destroyed!");
                    return;
                }
                switch (msgType) {
                    case HANDLE_CREATE_EDITOR:
                        handleCreateEditor();
                        break;

                    case HANDLE_SET_DATA_SOURCE:
                        handleSetDataSource(msg);
                        break;

                    case HANDLE_PREPARE_ASYNC:
                        handlePrepareAsync();
                        break;

                    case HANDLE_START_EDIT:
                        handleStartEdit(msg);
                        break;

                    case HANDLE_STOP:
                        handleStop();
                        break;

                    case HANDLE_RESET:
                        handleReset();
                        break;

                    case HANDLE_RELEASE:
                        handleRelease();
                        break;
                }
            }
        };
        createEditor();
    }

    public void release() {
        if (isReleased) {
            return;
        }
        // 首先置总的标志位，阻止消息队列的正常消费
        isReleased = true;
        isPrepared = false;
        nativeSetStopEditFlag();

        if (mPCMRecorder != null) {
            mPCMRecorder.release();
            mPCMRecorder = null;
        }

        // 然后停止前驱：工作线程
        mWorkHandler.removeCallbacksAndMessages(null);
        Message msg = mWorkHandler.obtainMessage(HANDLE_RELEASE);
        mWorkHandler.sendMessage(msg);

        // 最后停止回调：工作结果
        mUIHandler.removeCallbacksAndMessages(null);
    }

    private void handleRelease() {
        nativeReleaseEdit();
        stopPCMRecorder();

        mWorkHandler.removeCallbacksAndMessages(null);
        try {
            mWorkThread.quit();
        } catch (Exception e) {
            e.printStackTrace();
        }

        mUIHandler.removeCallbacksAndMessages(null);
    }

    public void setOnPreparedListener(OnPreparedListener listener) {
        this.mOnPreparedListener = listener;
    }

    public void setOnLoadingDataListener(OnLoadingDataListener listener) {
        this.mOnLoadingDataListener = listener;
    }

    public void setOnErrorListener(OnErrorListener listener) {
        this.mOnErrorListener = listener;
    }

    public void setOnCompletionListener(OnCompletionListener listener) {
        this.mOnCompletionListener = listener;
    }

    private void createEditor() {
        mWorkHandler.removeMessages(HANDLE_CREATE_EDITOR);
        Message msg = mWorkHandler.obtainMessage(HANDLE_CREATE_EDITOR);
        mWorkHandler.sendMessage(msg);
    }

    private void handleCreateEditor() {
        nativeCreateEditor();
    }

    public void setDataSource(String dataSource) {
        mWorkHandler.removeMessages(HANDLE_SET_DATA_SOURCE);// 以最新设置的源为准
        Message msg = mWorkHandler.obtainMessage(HANDLE_SET_DATA_SOURCE);
        msg.obj = dataSource;
        mWorkHandler.sendMessage(msg);
    }

    private void handleSetDataSource(Message msg) {
        String dataSource = (String) msg.obj;
        this.mDataSource = dataSource;
        nativeSetEditDataSource(mDataSource);
    }

    public void prepareAsync() {
        mWorkHandler.removeMessages(HANDLE_PREPARE_ASYNC);
        Message msg = mWorkHandler.obtainMessage(HANDLE_PREPARE_ASYNC);
        mWorkHandler.sendMessage(msg);
    }

    private void handlePrepareAsync() {
        if (TextUtils.isEmpty(mDataSource)) {
            LogUtils.e(TAG, "Can't call prepareAsync method before set valid data source");
            return;
        }

        isPrepared = false;
        nativePrepareEdit();
    }

    /**
     * called from native
     * ！！！注意：此回调处于 native 的锁中，不可以有其它过多操作，不可以调用 native 方法，以防死锁！！！
     */
    private void onNativePrepared(String dataSource, int videoWidth, int videoHeight) {
        LogUtils.d(TAG, "onNativePrepared isReleased: " + isReleased + ", dataSource: " + dataSource);
        if (!TextUtils.equals(dataSource, mDataSource)) {
            LogUtils.w(TAG, "onNativePrepared data source changed! So the preparation is invalid!");
            return;
        }

        isPrepared = true;
        if (mOnPreparedListener != null && !isReleased) {
            mUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    mOnPreparedListener.onPrepared();
                }
            });
        }
    }

    public void start(final int startTimeMsec, final int endTimeMsec, PCMRecorder.Encoder encoder, File saveFile) {
        if (!isPrepared) {
            LogUtils.e(TAG, "start but audio is not prepared");
            return;
        }

        if (startTimeMsec < 0 || endTimeMsec > getDuration() || startTimeMsec >= endTimeMsec) {
            LogUtils.e(TAG, "start but time range is invalid");
            return;
        }

        if (encoder == null || saveFile == null) {
            LogUtils.e(TAG, "start but encoder or saveFile is null");
            return;
        }

        int sampleRate = getAudioSampleRate();
        int channelNums = getAudioChannelNums();
        int bitsPerSample = getAudioBitsPerSample();
        if (sampleRate <= 0 || channelNums <= 0 || bitsPerSample <= 0) {
            LogUtils.e(TAG, "start but sampleRate or channelNums or bitsPerSample <= 0");
            return;
        }

        int maxBytesPerCallback = nativeGetEditPcmMaxBytesPerCallback();
        LogUtils.d(TAG, "startEdit range=[" + startTimeMsec + "," + endTimeMsec + "]ms;"
                + "sampleRate=" + sampleRate + ";channelNums=" + channelNums
                + ";bitsPerSample=" + bitsPerSample + ";maxBytesPerCall=" + maxBytesPerCallback);

        if (mPCMRecorder == null) {
            mPCMRecorder = new PCMRecorder();
        }
        mPCMRecorder.start(encoder, sampleRate, channelNums, bitsPerSample, maxBytesPerCallback, saveFile,
                new PCMRecorder.OnStartResultListener() {
                    @Override
                    public void onResult(boolean success) {
                        if (success) {
                            mEditRangeMsec = endTimeMsec - startTimeMsec;
                            startEdit(startTimeMsec, endTimeMsec);
                        } else {
                            LogUtils.e(TAG, "PCMRecorder start failed！");
                        }
                    }
                });
    }

    private void startEdit(int startTimeMsec, int endTimeMsec) {
        Message msg = mWorkHandler.obtainMessage(HANDLE_START_EDIT);
        msg.arg1 = startTimeMsec;
        msg.arg2 = endTimeMsec;
        mWorkHandler.sendMessage(msg);
    }

    private void handleStartEdit(Message msg) {
        if (!isPrepared) {
            LogUtils.e(TAG, "Can't call startEdit method before prepare finished");
            return;
        }

        nativeStartEdit(msg.arg1, msg.arg2);
    }

    /**
     * called from native
     */
    private void onNativeLoading(final boolean isLoading) {
        LogUtils.d(TAG, "onNativeLoading isLoading: " + isLoading + ", isReleased:" + isReleased);
        if (mOnLoadingDataListener != null && !isReleased) {
            mUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    mOnLoadingDataListener.onLoading(isLoading);
                }
            });
        }
    }

    public void stop() {
        isPrepared = false;
        nativeSetStopEditFlag();// 设置停止标志位立即执行，不进消息队列

        // 先清除其它所有未执行消息，再执行具体释放动作
        mWorkHandler.removeCallbacksAndMessages(null);
        Message msg = mWorkHandler.obtainMessage(HANDLE_STOP);
        mWorkHandler.sendMessage(msg);
    }

    private void handleStop() {
        nativeStopEdit();
        stopPCMRecorder();
    }

    private void stopPCMRecorder() {
        if (mPCMRecorder != null) {
            mPCMRecorder.stop();
        }
    }

    /**
     * Resets to its uninitialized state. After calling
     * this method, you will have to initialize it again by setting the
     * data source and calling prepare().
     */
    public void reset() {
        isPrepared = false;
        nativeSetStopEditFlag();// 设置停止标志位立即执行，不进消息队列

        // 先清除其它所有未执行消息，再执行具体重置动作
        mWorkHandler.removeCallbacksAndMessages(null);
        Message msg = mWorkHandler.obtainMessage(HANDLE_RESET);
        mWorkHandler.sendMessage(msg);
    }

    private void handleReset() {
        nativeResetEdit();
        stopPCMRecorder();
    }

    /**
     * Gets the duration of the file.
     *
     * @return the duration in milliseconds
     */
    public int getDuration() {
        if (!isPrepared) {
            LogUtils.e(TAG, "Can't call getDuration method before prepare finished");
            return 0;
        }
        return nativeGetEditDuration();
    }

    /**
     * Gets the current playback position.
     *
     * @return the current position in milliseconds
     */
    public int getCurrentPosition() {
        if (!isPrepared) {
            LogUtils.e(TAG, "Can't call getCurrentPosition method before prepare finished");
            return 0;
        }
        return nativeGetEditPosition();
    }

    public int getAudioSampleRate() {
        if (!isPrepared) {
            LogUtils.e(TAG, "Can't call getAudioSampleRate method before prepare finished");
            return 0;
        }

        return nativeGetEditAudioSampleRate();
    }

    public int getAudioChannelNums() {
        if (!isPrepared) {
            LogUtils.e(TAG, "Can't call getAudioChannelNums method before prepare finished");
            return 0;
        }

        return nativeGetEditAudioChannelNums();
    }

    public int getAudioBitsPerSample() {
        if (!isPrepared) {
            LogUtils.e(TAG, "Can't call getAudioBitsPerSample method before prepare finished");
            return 0;
        }

        return nativeGetEditAudioBitsPerSample();
    }

    /**
     * called from native
     * ！！！注意：此回调处于 native 的锁中，不可以有其它过多操作，不可以调用 native 方法，以防死锁！！！
     */
    private void onNativeError(final int code, final String msg) {
        LogUtils.e(TAG, "onNativeError code=" + code + "; msg=" + msg);
        if (mOnErrorListener != null && !isReleased) {
            mUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    mOnErrorListener.onError(code, msg);
                }
            });
        }
    }

    /**
     * called from native
     * ！！！注意：此回调处于 native 的锁中，不可以有其它过多操作，不可以调用 native 方法，以防死锁！！！
     */
    private void onNativeCompletion() {
        LogUtils.d(TAG, "onNativeCompletion");
        stopPCMRecorder();// 会抛到另一个线程操作，不会阻塞
        if (mOnCompletionListener != null && !isReleased) {
            mUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    mOnCompletionListener.onCompletion();
                }
            });
        }
    }

    /**
     * @return 当前已录制时长，单位：毫秒
     */
    public int getCurrentRecordTimeMsecs() {
        return mPCMRecorder != null ? (int) Math.round(mPCMRecorder.getRecordTimeSecs() * 1000) : 0;
    }

    /**
     * @return 当前已录制时长占总时长比例
     */
    public float getCurrentRecordTimeRatio() {
        return getCurrentRecordTimeMsecs() * 1.0f / mEditRangeMsec;
    }

    /**
     * Called by native
     *
     * @param pcmData
     * @param size
     */
    private void onNativePCMDataCall(byte[] pcmData, int size) {
        if (pcmData == null || size <= 0 || pcmData.length < size) {
            LogUtils.e(TAG, "onNativePCMDataCall but pcmData=" + pcmData + ";size=" + size);
            return;
        }

        if (mPCMRecorder == null) {
            LogUtils.e(TAG, "onNativePCMDataCall but mPCMRecorder is null");
            return;
        }

        mPCMRecorder.encode(pcmData, size);
    }

}
