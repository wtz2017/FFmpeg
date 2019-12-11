package com.wtz.ffmpegapi;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.wtz.ffmpegapi.utils.LogUtils;

public class WePlayer {
    private static final String TAG = "WePlayerJava";

    static {
        System.loadLibrary("weplayer");
        System.loadLibrary("avcodec");
        System.loadLibrary("avdevice");
        System.loadLibrary("avfilter");
        System.loadLibrary("avformat");
        System.loadLibrary("avutil");
        System.loadLibrary("postproc");
        System.loadLibrary("swresample");
        System.loadLibrary("swscale");
    }

    private native void nativeSetDataSource(String dataSource);

    private native void nativePrepareAsync();

    private native void nativeSetVolume(float percent);

    private native float nativeGetVolume();

    /**
     * 设置声道
     *
     * @param channel CHANNEL_RIGHT = 0;
     *                CHANNEL_LEFT = 1;
     *                CHANNEL_STEREO = 2;
     */
    private native void nativeSetSoundChannel(int channel);

    private native void nativeStart();

    private native void nativePause();

    private native void nativeSeekTo(int msec);

    private native boolean nativeIsPlaying();

    private native void nativeSetStopFlag();

    private native void nativeStop();

    private native void nativeReset();

    private native void nativeRelease();

    private native int nativeGetDuration();

    private native int nativeGetCurrentPosition();

    private OnPreparedListener mOnPreparedListener;
    private OnPlayLoadingListener mOnPlayLoadingListener;
    private OnErrorListener mOnErrorListener;
    private OnCompletionListener mOnCompletionListener;

    private String mDataSource;
    private float mVolumePercent = -1;
    private boolean isPrepared;

    private Handler mUIHandler;// 用以把回调切换到主线程，不占用工作线程资源
    private Handler mWorkHandler;
    private HandlerThread mWorkThread;
    private static final int HANDLE_SET_DATA_SOURCE = 1;
    private static final int HANDLE_PREPARE_ASYNC = 2;
    private static final int HANDLE_SET_VOLUME = 3;
    private static final int HANDLE_SET_CHANNEL = 4;
    private static final int HANDLE_START = 5;
    private static final int HANDLE_PAUSE = 6;
    private static final int HANDLE_SEEK = 7;
    private static final int HANDLE_STOP = 8;
    private static final int HANDLE_RESET = 9;
    private static final int HANDLE_RELEASE = 10;

    private boolean isReleased;

    public enum SoundChannel {
        RIGHT_CHANNEL(0), LEFT_CHANNEL(1), STERO(2);

        private int nativeValue;

        SoundChannel(int nativeValue) {
            this.nativeValue = nativeValue;
        }

        public int getNativeValue() {
            return nativeValue;
        }
    }

    public interface OnPreparedListener {
        void onPrepared();
    }

    public interface OnPlayLoadingListener {
        void onPlayLoading(boolean isLoading);
    }

    public interface OnErrorListener {
        void onError(int code, String msg);
    }

    public interface OnCompletionListener {
        void onCompletion();
    }

    public WePlayer() {
        mUIHandler = new Handler(Looper.getMainLooper());
        mWorkThread = new HandlerThread("WePlayer-dispatcher");
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
                    case HANDLE_SET_DATA_SOURCE:
                        handleSetDataSource(msg);
                        break;

                    case HANDLE_PREPARE_ASYNC:
                        handlePrepareAsync();
                        break;

                    case HANDLE_SET_VOLUME:
                        handleSetVolume(msg);
                        break;

                    case HANDLE_SET_CHANNEL:
                        handleSetChannel(msg);
                        break;

                    case HANDLE_START:
                        handleStart();
                        break;

                    case HANDLE_PAUSE:
                        handlePause();
                        break;

                    case HANDLE_SEEK:
                        handleSeek(msg);
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
    }

    public void release() {
        if (isReleased) {
            return;
        }
        // 首先置总的标志位，阻止消息队列的正常消费
        isReleased = true;
        nativeSetStopFlag();

        // 然后停止前驱：工作线程
        mWorkHandler.removeCallbacksAndMessages(null);
        Message msg = mWorkHandler.obtainMessage(HANDLE_RELEASE);
        mWorkHandler.sendMessage(msg);

        // 最后停止回调：工作结果
        mUIHandler.removeCallbacksAndMessages(null);
    }

    private void handleRelease() {
        nativeRelease();

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

    public void setOnPlayLoadingListener(OnPlayLoadingListener listener) {
        this.mOnPlayLoadingListener = listener;
    }

    public void setOnErrorListener(OnErrorListener listener) {
        this.mOnErrorListener = listener;
    }

    public void setOnCompletionListener(OnCompletionListener listener) {
        this.mOnCompletionListener = listener;
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
        nativeSetDataSource(mDataSource);
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
        nativePrepareAsync();
    }

    /**
     * called from native
     * ！！！注意：此回调处于 native 的锁中，不可以有其它过多操作，不可以调用 native 方法，以防死锁！！！
     */
    public void onNativePrepared(String dataSource) {
        LogUtils.d(TAG, "onNativePrepared isReleased: " + isReleased + ", dataSource: " + dataSource);
        if (!TextUtils.equals(dataSource, mDataSource)) {
            LogUtils.w(TAG, "onNativePrepared data source changed! So the preparation is invalid!");
            return;
        }

        isPrepared = true;
        setCacheVolume();
        if (mOnPreparedListener != null && !isReleased) {
            mUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    mOnPreparedListener.onPrepared();
                }
            });
        }
    }

    /**
     * 设置音量
     *
     * @param percent 范围是：0 ~ 1.0
     */
    public void setVolume(float percent) {
        // 1. 范围判断底层会处理；2. 准备前设置的先缓存，准备好后会自动设置缓存的值
        mVolumePercent = percent;

        Message msg = mWorkHandler.obtainMessage(HANDLE_SET_VOLUME);
        msg.obj = percent;
        mWorkHandler.sendMessage(msg);
    }

    private void setCacheVolume() {
        if (mVolumePercent < 0) {
            return;
        }
        Message msg = mWorkHandler.obtainMessage(HANDLE_SET_VOLUME);
        msg.obj = mVolumePercent;
        mWorkHandler.sendMessage(msg);
    }

    private void handleSetVolume(Message msg) {
        if (!isPrepared) {
            return;
        }

        float percent = (float) msg.obj;
        nativeSetVolume(percent);
    }

    /**
     * 获取当前音量百分比
     *
     * @return 范围是：0 ~ 1.0
     */
    public float getVolume() {
        return nativeGetVolume();
    }

    public void setSoundChannel(SoundChannel channel) {
        Message msg = mWorkHandler.obtainMessage(HANDLE_SET_CHANNEL);
        msg.obj = channel;
        mWorkHandler.sendMessage(msg);
    }

    private void handleSetChannel(Message msg) {
        if (!isPrepared) {
            return;
        }

        SoundChannel channel = (SoundChannel) msg.obj;
        nativeSetSoundChannel(channel.getNativeValue());
    }

    public void start() {
        Message msg = mWorkHandler.obtainMessage(HANDLE_START);
        mWorkHandler.sendMessage(msg);
    }

    private void handleStart() {
        if (!isPrepared) {
            LogUtils.e(TAG, "Can't call start method before prepare finished");
            return;
        }

        nativeStart();
    }

    /**
     * called from native
     */
    public void onNativePlayLoading(final boolean isLoading) {
        LogUtils.d(TAG, "onNativePlayLoading isLoading: " + isLoading + ", isReleased:" + isReleased);
        if (mOnPlayLoadingListener != null && !isReleased) {
            mUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    mOnPlayLoadingListener.onPlayLoading(isLoading);
                }
            });
        }
    }

    public void pause() {
        Message msg = mWorkHandler.obtainMessage(HANDLE_PAUSE);
        mWorkHandler.sendMessage(msg);
    }

    private void handlePause() {
        if (!isPrepared) {
            LogUtils.e(TAG, "Can't call pause method before prepare finished");
            return;
        }

        nativePause();
    }

    /**
     * Seeks to specified time position
     *
     * @param msec the offset in milliseconds from the start to seek to
     */
    public void seekTo(int msec) {
        Message msg = mWorkHandler.obtainMessage(HANDLE_SEEK);
        msg.arg1 = msec;
        mWorkHandler.sendMessage(msg);
    }

    private void handleSeek(Message msg) {
        if (!isPrepared) {
            LogUtils.e(TAG, "Can't call seekTo method before prepare finished");
            return;
        }

        int msec = msg.arg1;
        nativeSeekTo(msec);
    }

    public void stop() {
        isPrepared = false;
        nativeSetStopFlag();// 设置停止标志位立即执行，不进消息队列

        // 先清除其它所有未执行消息，再执行具体释放动作
        mWorkHandler.removeCallbacksAndMessages(null);
        Message msg = mWorkHandler.obtainMessage(HANDLE_STOP);
        mWorkHandler.sendMessage(msg);
    }

    private void handleStop() {
        nativeStop();
    }

    /**
     * Resets the MediaPlayer to its uninitialized state. After calling
     * this method, you will have to initialize it again by setting the
     * data source and calling prepare().
     */
    public void reset() {
        isPrepared = false;
        nativeSetStopFlag();// 设置停止标志位立即执行，不进消息队列

        // 先清除其它所有未执行消息，再执行具体重置动作
        mWorkHandler.removeCallbacksAndMessages(null);
        Message msg = mWorkHandler.obtainMessage(HANDLE_RESET);
        mWorkHandler.sendMessage(msg);
    }

    private void handleReset() {
        nativeReset();
    }

    public boolean isPlaying() {
        return nativeIsPlaying();
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
        return nativeGetDuration();
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
        return nativeGetCurrentPosition();
    }

    /**
     * called from native
     * ！！！注意：此回调处于 native 的锁中，不可以有其它过多操作，不可以调用 native 方法，以防死锁！！！
     */
    public void onNativeError(final int code, final String msg) {
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
    public void onNativeCompletion() {
        LogUtils.d(TAG, "onNativeCompletion");
        if (mOnCompletionListener != null && !isReleased) {
            mUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    mOnCompletionListener.onCompletion();
                }
            });
        }
    }

}
