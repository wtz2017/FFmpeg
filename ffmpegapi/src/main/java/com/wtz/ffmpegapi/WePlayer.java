package com.wtz.ffmpegapi;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import com.wtz.ffmpegapi.utils.LogUtils;
import com.wtz.ffmpegapi.utils.VideoUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class WePlayer {
    private static final String TAG = "WePlayerJava";

    static {
        System.loadLibrary("weplayer");
        System.loadLibrary("avcodec");
        System.loadLibrary("avfilter");
        System.loadLibrary("avformat");
        System.loadLibrary("avutil");
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

    private native void nativeSetPitch(float pitch);

    private native float nativeGetPitch();

    private native void nativeSetTempo(float tempo);

    private native float nativeGetTempo();

    private native double nativeGetSoundDecibels();

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

    private native int nativeGetAudioSampleRate();

    private native int nativeGetAudioChannelNums();

    private native int nativeGetAudioBitsPerSample();

    private native int nativeGetPcmMaxBytesPerCallback();

    private native void nativeSetRecordPCMFlag(boolean record);

    private OnPreparedListener mOnPreparedListener;
    private OnYUVDataListener mOnYUVDataListener;
    private OnPlayLoadingListener mOnPlayLoadingListener;
    private OnErrorListener mOnErrorListener;
    private OnCompletionListener mOnCompletionListener;
    private OnStoppedListener mOnStoppedListener;
    private OnResetListener mOnResetListener;
    private OnReleasedListener mOnReleasedListener;

    private String mDataSource;
    private float mVolumePercent = -1;
    private boolean isPrepared;
    private boolean isReleased;

    private PCMRecorder mPCMRecorder;

    // for video
    private int mVideoWidth;
    private int mVideoHeight;
    private String mFFmpegVideoCodecType = "";
    private boolean beVideoHardCodec;
    private Surface mSurface;
    private MediaCodec mVideoDecoder;
    private MediaFormat mVideoFormat;
    private MediaCodec.BufferInfo mVideoBufferInfo;

    private Handler mUIHandler;// 用以把回调切换到主线程，不占用工作线程资源
    private Handler mWorkHandler;
    private HandlerThread mWorkThread;
    private static final int HANDLE_SET_DATA_SOURCE = 1;
    private static final int HANDLE_PREPARE_ASYNC = 2;
    private static final int HANDLE_SET_VOLUME = 3;
    private static final int HANDLE_SET_CHANNEL = 4;
    private static final int HANDLE_SET_PITCH = 5;
    private static final int HANDLE_SET_TEMPO = 6;
    private static final int HANDLE_START = 7;
    private static final int HANDLE_PAUSE = 8;
    private static final int HANDLE_SEEK = 9;
    private static final int HANDLE_STOP = 10;
    private static final int HANDLE_RESET = 11;
    private static final int HANDLE_RELEASE = 12;

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

    public interface OnYUVDataListener {
        void onYUVData(int width, int height, byte[] y, byte[] u, byte[] v);
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

    public interface OnStoppedListener {
        void onStopped();
    }

    public interface OnResetListener {
        void onReset();
    }

    public interface OnReleasedListener {
        void onReleased();
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

                    case HANDLE_SET_PITCH:
                        handleSetPitch(msg);
                        break;

                    case HANDLE_SET_TEMPO:
                        handleSetTempo(msg);
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
        isPrepared = false;
        nativeSetStopFlag();

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
        nativeRelease();

        mWorkHandler.removeCallbacksAndMessages(null);
        try {
            mWorkThread.quit();
        } catch (Exception e) {
            e.printStackTrace();
        }

        mUIHandler.removeCallbacksAndMessages(null);

        if (mOnReleasedListener != null) {
            mUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    mOnReleasedListener.onReleased();
                }
            });
        }
    }

    public void setOnPreparedListener(OnPreparedListener listener) {
        this.mOnPreparedListener = listener;
    }

    public void setOnYUVDataListener(OnYUVDataListener listener) {
        this.mOnYUVDataListener = listener;
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

    public void setOnStoppedListener(OnStoppedListener listener) {
        this.mOnStoppedListener = listener;
    }

    public void setOnResetListener(OnResetListener listener) {
        this.mOnResetListener = listener;
    }

    public void setOnReleasedListener(OnReleasedListener listener) {
        this.mOnReleasedListener = listener;
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
    private void onNativePrepared(String dataSource, int videoWidth, int videoHeight) {
        LogUtils.d(TAG, "onNativePrepared isReleased: " + isReleased + ", dataSource: " + dataSource);
        if (!TextUtils.equals(dataSource, mDataSource)) {
            LogUtils.w(TAG, "onNativePrepared data source changed! So the preparation is invalid!");
            return;
        }

        isPrepared = true;
        mVideoWidth = videoWidth;
        mVideoHeight = videoHeight;
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

    /**
     * 设置音调
     *
     * @param pitch
     */
    public void setPitch(float pitch) {
        Message msg = mWorkHandler.obtainMessage(HANDLE_SET_PITCH);
        msg.obj = pitch;
        mWorkHandler.sendMessage(msg);
    }

    private void handleSetPitch(Message msg) {
        if (!isPrepared) {
            return;
        }

        float pitch = (float) msg.obj;
        nativeSetPitch(pitch);
    }

    public float getPitch() {
        return nativeGetPitch();
    }

    /**
     * 设置音速
     *
     * @param tempo
     */
    public void setTempo(float tempo) {
        Message msg = mWorkHandler.obtainMessage(HANDLE_SET_TEMPO);
        msg.obj = tempo;
        mWorkHandler.sendMessage(msg);
    }

    private void handleSetTempo(Message msg) {
        if (!isPrepared) {
            return;
        }

        float tempo = (float) msg.obj;
        nativeSetTempo(tempo);
    }

    public float getTempo() {
        return nativeGetTempo();
    }

    /**
     * 获取当前播放声音分贝值，单位：dB
     */
    public double getSoundDecibels() {
        return nativeGetSoundDecibels();
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
    private void onNativeLoading(final boolean isLoading) {
        LogUtils.d(TAG, "onNativeLoading isLoading: " + isLoading + ", isReleased:" + isReleased);
        if (mOnPlayLoadingListener != null && !isReleased) {
            // post 到 UI 线程：1.是保持原有顺序；2.是不占用 Native 工作线程
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
        beVideoHardCodec = false;
        nativeSetStopFlag();// 设置停止标志位立即执行，不进消息队列
        stopRecord();

        // 先清除其它所有未执行消息，再执行具体释放动作
        mWorkHandler.removeCallbacksAndMessages(null);
        Message msg = mWorkHandler.obtainMessage(HANDLE_STOP);
        mWorkHandler.sendMessage(msg);
    }

    private void handleStop() {
        nativeStop();
        if (mOnStoppedListener != null) {
            mUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    mOnStoppedListener.onStopped();
                }
            });
        }
    }

    /**
     * Resets the MediaPlayer to its uninitialized state. After calling
     * this method, you will have to initialize it again by setting the
     * data source and calling prepare().
     */
    public void reset() {
        isPrepared = false;
        nativeSetStopFlag();// 设置停止标志位立即执行，不进消息队列
        stopRecord();

        // 先清除其它所有未执行消息，再执行具体重置动作
        mWorkHandler.removeCallbacksAndMessages(null);
        Message msg = mWorkHandler.obtainMessage(HANDLE_RESET);
        mWorkHandler.sendMessage(msg);
    }

    private void handleReset() {
        nativeReset();
        // 在彻底停止数据回调后调用一次清屏
        if (mOnResetListener != null) {
            mUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    mOnResetListener.onReset();
                }
            });
        }
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

    public int getAudioSampleRate() {
        if (!isPrepared) {
            LogUtils.e(TAG, "Can't call getAudioSampleRate method before prepare finished");
            return 0;
        }

        return nativeGetAudioSampleRate();
    }

    public int getAudioChannelNums() {
        if (!isPrepared) {
            LogUtils.e(TAG, "Can't call getAudioChannelNums method before prepare finished");
            return 0;
        }

        return nativeGetAudioChannelNums();
    }

    public int getAudioBitsPerSample() {
        if (!isPrepared) {
            LogUtils.e(TAG, "Can't call getAudioBitsPerSample method before prepare finished");
            return 0;
        }

        return nativeGetAudioBitsPerSample();
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
        if (mOnCompletionListener != null && !isReleased) {
            mUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    mOnCompletionListener.onCompletion();
                }
            });
        }
    }

    public void startRecord(PCMRecorder.Encoder encoder, File saveFile) {
        LogUtils.d(TAG, "PCM start...");
        if (!isPrepared) {
            LogUtils.e(TAG, "start but audio is not prepared");
            return;
        }

        if (saveFile == null) {
            LogUtils.e(TAG, "start but saveFile is null");
            return;
        }

        int sampleRate = getAudioSampleRate();
        int channelNums = getAudioChannelNums();
        int bitsPerSample = getAudioBitsPerSample();
        LogUtils.d(TAG, "start sampleRate=" + sampleRate + ";channelNums=" + channelNums);
        if (sampleRate <= 0 || channelNums <= 0 || bitsPerSample <= 0) {
            LogUtils.e(TAG, "start but sampleRate <= 0 or channelNums <= 0 or bitsPerSample <= 0");
            return;
        }

        int maxBytesPerCallback = nativeGetPcmMaxBytesPerCallback();
        LogUtils.d(TAG, "PCM maxBytesPerCallback: " + maxBytesPerCallback);
        if (mPCMRecorder == null) {
            mPCMRecorder = new PCMRecorder();
        }
        mPCMRecorder.start(encoder, sampleRate, channelNums, bitsPerSample, maxBytesPerCallback, saveFile,
                new PCMRecorder.OnStartResultListener() {
                    @Override
                    public void onResult(boolean success) {
                        LogUtils.d(TAG, "PCMRecorder start onResult: " + success);
                        if (success) {
                            nativeSetRecordPCMFlag(true);
                        }
                    }
                });
    }

    public void pauseRecord() {
        nativeSetRecordPCMFlag(false);
    }

    public void resumeRecord() {
        nativeSetRecordPCMFlag(true);
    }

    public void stopRecord() {
        LogUtils.d(TAG, "PCM stopRecord...");
        nativeSetRecordPCMFlag(false);
        if (mPCMRecorder != null) {
            mPCMRecorder.stop();
        }
    }

    public double getRecordTimeSecs() {
        return mPCMRecorder != null ? mPCMRecorder.getRecordTimeSecs() : 0;
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

    public void setSurface(Surface surface) {
        if (surface == null) throw new NullPointerException("surface can't be null");
        this.mSurface = surface;
    }

    /**
     * 用于在视频资源 OnPrepared 之后获取视频宽度
     */
    public int getVideoWidthOnPrepared() {
        if (!isPrepared) {
            LogUtils.e(TAG, "Can't call getVideoWidthOnPrepared method before prepare finished");
            return 0;
        }
        return mVideoWidth;
    }

    /**
     * 用于在视频资源 OnPrepared 之后获取视频高度
     */
    public int getVideoHeightOnPrepared() {
        if (!isPrepared) {
            LogUtils.e(TAG, "Can't call getVideoHeightOnPrepared method before prepare finished");
            return 0;
        }
        return mVideoHeight;
    }

    private void onNativeYUVDataCall(int width, int height, byte[] y, byte[] u, byte[] v) {
        // 直接在本线程中回调，之后 native 内部数据会回收
        if (mOnYUVDataListener != null) {
            mOnYUVDataListener.onYUVData(width, height, y, u, v);
        }
    }

    /**
     * 供 Native 层判断是否支持硬解码
     *
     * @param ffmpegCodecType ffmpeg 层的解码器类型
     * @return true 支持硬解
     */
    private boolean onNativeCheckVideoHardCodec(String ffmpegCodecType) {
        this.mFFmpegVideoCodecType = ffmpegCodecType;
        return VideoUtils.isSupportHardCodec(ffmpegCodecType);
    }

    /**
     * Native 通知 Java 层初始化硬解码器
     *
     * @param ffmpegCodecType ffmpeg 层的解码器类型
     * @param width           视频宽度
     * @param height          视频高度
     * @param csd0
     * @param csd1
     * @return true 初始化成功
     */
    private boolean onNativeInitVideoHardCodec(String ffmpegCodecType, int width, int height,
                                               byte[] csd0, byte[] csd1) {
        if (mSurface == null) {
            LogUtils.e(TAG, "onNativeInitVideoHardCodec mSurface is null!");
            return false;
        }

        String mime = VideoUtils.findHardCodecType(ffmpegCodecType);
        try {
            mVideoDecoder = MediaCodec.createDecoderByType(mime);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        if (mVideoDecoder == null) {
            LogUtils.e(TAG, "MediaCodec create VideoDecoder failed!");
            return false;
        }

        mVideoFormat = MediaFormat.createVideoFormat(mime, width, height);
        mVideoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height);
        mVideoFormat.setByteBuffer("csd-0", ByteBuffer.wrap(csd0));
        mVideoFormat.setByteBuffer("csd-1", ByteBuffer.wrap(csd1));

        try {
            mVideoDecoder.configure(mVideoFormat, mSurface, null, 0);
        } catch (Exception e) {
            e.printStackTrace();
            mVideoDecoder.release();
            mVideoDecoder = null;
            mVideoFormat = null;
            return false;
        }

        mVideoBufferInfo = new MediaCodec.BufferInfo();
        mVideoDecoder.start();
        return true;
    }

    /**
     * 最终由 Native 层判定是否可以硬解码
     *
     * @param hardCodec true 可以硬解码
     */
    private void onNativeSetVideoHardCodec(boolean hardCodec) {
        this.beVideoHardCodec = hardCodec;
    }

    public boolean isVideoHardCodec() {
        return beVideoHardCodec;
    }

    public String getVideoCodecType() {
        return mFFmpegVideoCodecType;
    }

    private void onNativeVideoPacketCall(int packetSize, byte[] packet) {
        if (mVideoDecoder == null) {
            LogUtils.e(TAG, "onNativeVideoPacketCall mVideoDecoder == null");
            return;
        }
        if (packetSize <= 0 || packet == null || packet.length < packetSize) {
            LogUtils.e(TAG, "onNativeVideoPacketCall but params is invalid: packetSize="
                    + packetSize + ", packet=" + packet);
            return;
        }

        // 获取输入 buffer，超时等待
        int inputBufferIndex = mVideoDecoder.dequeueInputBuffer(10);
        if (inputBufferIndex < 0) {
            LogUtils.e(TAG, "mVideoDecoder dequeueInputBuffer failed inputBufferIndex=" + inputBufferIndex);
            return;
        }

        // 成功获取输入 buffer后，填入要处理的数据
        ByteBuffer inputBuffer = mVideoDecoder.getInputBuffers()[inputBufferIndex];
        inputBuffer.clear();
        inputBuffer.put(packet);
        // 填完输入数据后，释放输入 buffer
        mVideoDecoder.queueInputBuffer(
                inputBufferIndex, 0, packetSize, 0, 0);

        // 获取输出 buffer，超时等待
        int ouputBufferIndex = mVideoDecoder.dequeueOutputBuffer(mVideoBufferInfo, 10);
        while (ouputBufferIndex >= 0) {// 可能一次获取不完，需要多次
//            ByteBuffer outputBuffer = mVideoDecoder.getOutputBuffers()[ouputBufferIndex];
            // do nothing?
            // releaseOutputBuffer
            // * @param render If a valid surface was specified when configuring the codec,
            // *               passing true renders this output buffer to the surface.
            mVideoDecoder.releaseOutputBuffer(ouputBufferIndex, true);
            ouputBufferIndex = mVideoDecoder.dequeueOutputBuffer(mVideoBufferInfo, 10);
        }
    }

    /**
     * 涉及多线程操作，由 Native 层统一调用停止时的资源释放
     */
    private void onNativeStopVideoHardCodec() {
        LogUtils.w(TAG, "onNativeStopVideoHardCodec...");
        if (mVideoDecoder != null) {
            mVideoDecoder.flush();
            mVideoDecoder.stop();
            mVideoDecoder.release();
            mVideoDecoder = null;
        }
        mVideoFormat = null;
        mVideoBufferInfo = null;
    }

}
