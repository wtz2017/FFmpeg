package com.wtz.ffmpegapi;

import android.text.TextUtils;

import com.wtz.ffmpegapi.utils.LogUtils;

public class WePlayer {
    private static final String TAG = "WePlayer";

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

    private OnPreparedListener mOnPreparedListener;
    private OnPlayLoadingListener mOnPlayLoadingListener;
    private String mDataSource;
    private boolean isPrepared;

    public interface OnPreparedListener {
        void onPrepared();
    }

    public interface OnPlayLoadingListener {
        void onPlayLoading(boolean isLoading);
    }

    public void setOnPreparedListener(OnPreparedListener onPreparedListener) {
        this.mOnPreparedListener = onPreparedListener;
    }

    public void setOnPlayLoadingListener(OnPlayLoadingListener onPlayLoadingListener) {
        this.mOnPlayLoadingListener = onPlayLoadingListener;
    }

    public void setDataSource(String dataSource) {
        if (TextUtils.equals(dataSource, mDataSource)) {
            return;
        }
        isPrepared = false;
        this.mDataSource = dataSource;
        nativeSetDataSource(mDataSource);
    }

    public void prepareAsync() {
        if (TextUtils.isEmpty(mDataSource)) {
            throw new IllegalStateException("Can't call prepareAsync method before set valid data source");
        }

        nativePrepareAsync();
    }

    /**
     * called from native
     */
    public void onNativePrepared(String dataSource) {
        LogUtils.d(TAG, "onNativePrepared dataSource: " + dataSource);
        if (!TextUtils.equals(dataSource, mDataSource)) {
            LogUtils.w(TAG, "onNativePrepared data source changed! So the preparation is invalid!");
            return;
        }
        isPrepared = true;
        if (mOnPreparedListener != null) {
            mOnPreparedListener.onPrepared();
        }
    }

    public void start() {
        if (!isPrepared) {
            throw new IllegalStateException("Can't call start method before prepare finished");
        }

        nativeStart();
    }

    /**
     * called from native
     */
    public void onNativePlayLoading(boolean isLoading) {
        LogUtils.d(TAG, "onNativePlayLoading isLoading: " + isLoading);
        if (mOnPlayLoadingListener != null) {
            mOnPlayLoadingListener.onPlayLoading(isLoading);
        }
    }

    public void pause() {
        if (!isPrepared) {
            throw new IllegalStateException("Can't call pause method before prepare finished");
        }

        nativePause();
    }

    public void resumePlay() {
        if (!isPrepared) {
            throw new IllegalStateException("Can't call resumePlay method before prepare finished");
        }

        nativeResumePlay();
    }

}
