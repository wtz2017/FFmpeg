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

    private native void nativeStart();

    private native void nativePause();

    private native void nativeResumePlay();

    private native void nativeSetStopFlag();

    private native void nativeRelease();

    private native int nativeGetDuration();

    private native int nativeGetCurrentPosition();

    private OnPreparedListener mOnPreparedListener;
    private OnPlayLoadingListener mOnPlayLoadingListener;
    private String mDataSource;
    private boolean isPrepared;

    private Handler mUIHandler;// 用以把回调切换到主线程，不占用工作线程资源
    private Handler mWorkHandler;
    private HandlerThread mWorkThread;
    private static final int HANDLE_SET_DATA_SOURCE = 1;
    private static final int HANDLE_PREPARE_ASYNC = 2;
    private static final int HANDLE_START = 3;
    private static final int HANDLE_PAUSE = 4;
    private static final int HANDLE_RESUME_PLAY = 5;
    private static final int HANDLE_RELEASE = 6;
    private static final int HANDLE_DESTROY = 7;

    private boolean isDestroyed;

    public interface OnPreparedListener {
        void onPrepared();
    }

    public interface OnPlayLoadingListener {
        void onPlayLoading(boolean isLoading);
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
                if (isDestroyed && msgType != HANDLE_DESTROY) {
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

                    case HANDLE_START:
                        handleStart();
                        break;

                    case HANDLE_PAUSE:
                        handlePause();
                        break;

                    case HANDLE_RESUME_PLAY:
                        handleResumePlay();
                        break;

                    case HANDLE_RELEASE:
                        handleRelease();
                        break;

                    case HANDLE_DESTROY:
                        handleDestroy();
                        break;
                }
            }
        };
    }

    public void destroyPlayer() {
        if (isDestroyed) {
            return;
        }
        // 首先置总的标志位，阻止消息队列的正常消费
        isDestroyed = true;

        // 然后停止前驱：工作线程
        mWorkHandler.removeCallbacksAndMessages(null);
        Message msg = mWorkHandler.obtainMessage(HANDLE_DESTROY);
        mWorkHandler.sendMessage(msg);

        // 最后停止回调：工作结果
        mUIHandler.removeCallbacksAndMessages(null);
    }

    private void handleDestroy() {
        nativeSetStopFlag();
        nativeRelease();

        mWorkHandler.removeCallbacksAndMessages(null);
        try {
            mWorkThread.quit();
        } catch (Exception e) {
            e.printStackTrace();
        }

        mUIHandler.removeCallbacksAndMessages(null);
    }

    public void setOnPreparedListener(OnPreparedListener onPreparedListener) {
        this.mOnPreparedListener = onPreparedListener;
    }

    public void setOnPlayLoadingListener(OnPlayLoadingListener onPlayLoadingListener) {
        this.mOnPlayLoadingListener = onPlayLoadingListener;
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
     */
    public void onNativePrepared(String dataSource) {
        LogUtils.d(TAG, "onNativePrepared isDestroyed: " + isDestroyed + ", dataSource: " + dataSource);
        if (!TextUtils.equals(dataSource, mDataSource)) {
            LogUtils.w(TAG, "onNativePrepared data source changed! So the preparation is invalid!");
            return;
        }
        isPrepared = true;
        if (mOnPreparedListener != null && !isDestroyed) {
            mUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    mOnPreparedListener.onPrepared();
                }
            });
        }
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
        LogUtils.d(TAG, "onNativePlayLoading isLoading: " + isLoading + ", isDestroyed:" + isDestroyed);
        if (mOnPlayLoadingListener != null && !isDestroyed) {
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

    public void resumePlay() {
        Message msg = mWorkHandler.obtainMessage(HANDLE_RESUME_PLAY);
        mWorkHandler.sendMessage(msg);
    }

    private void handleResumePlay() {
        if (!isPrepared) {
            LogUtils.e(TAG, "Can't call resumePlay method before prepare finished");
            return;
        }

        nativeResumePlay();
    }

    public void stop() {
        isPrepared = false;
        nativeSetStopFlag();// 设置停止标志位立即执行，不进消息队列

        // 先清除其它所有未执行消息，再执行具体释放动作
        mWorkHandler.removeCallbacksAndMessages(null);
        Message msg = mWorkHandler.obtainMessage(HANDLE_RELEASE);
        mWorkHandler.sendMessage(msg);
    }

    private void handleRelease() {
        nativeRelease();
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

}
