package com.wtz.liveplay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.squareup.picasso.Picasso;
import com.wtz.ffmpegapi.WePlayer;

import java.util.ArrayList;
import java.util.List;

public class AudioService extends Service implements WePlayer.OnPreparedListener,
        WePlayer.OnPlayLoadingListener, WePlayer.OnSeekCompleteListener,
        WePlayer.OnCompletionListener, WePlayer.OnStoppedListener, WePlayer.OnErrorListener {

    private static final String TAG = AudioService.class.getSimpleName();

    // NOTIFICATION_ID must not be 0.
    // startForeground 只要传的 id 相同，不管是不是一个进程，不管是不是同一个 notification，
    // 都会用最新的 notification 覆盖旧的，只显示一个。
    private static final int NOTIFICATION_ID = 1;
    private static final String NOTIFY_CHANNEL_ID = "channel_we_audioService";
    private static final String NOTIFY_CHANNEL_NAME = "We AudioService is playing.";
    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder mNotificationBuilder;
    private Intent mAudioPlayerIntent;
    private RemoteViews mRemoteViews;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private static final int ICON_WIDTH_DIMEN_ID = R.dimen.dp_60;
    private static final int ICON_HEIGHT_DIMEN_ID = R.dimen.dp_60;
    private int mAlbumWidth;
    private int mAlbumHeight;

    private static final int PENDING_REQUEST_ENTER_CONTENT = 100;
    private static final int PENDING_REQUEST_DELETE_CONTENT = 101;
    private static final int PENDING_REQUEST_PLAY_PAUSE = 200;
    private static final int PENDING_REQUEST_LAST_SONG = 201;
    private static final int PENDING_REQUEST_NEXT_SONG = 202;
    private static final int PENDING_REQUEST_STOP_PLAY = 203;

    private BroadcastReceiver mPlayerReceiver;
    private static final String PLAY_PAUSE_ACTION = "com.wtz.liveplay.PLAY_PAUSE";
    private static final String LAST_SONG_ACTION = "com.wtz.liveplay.LAST_SONG";
    private static final String NEXT_SONG_ACTION = "com.wtz.liveplay.NEXT_SONG";
    private static final String STOP_PLAY_ACTION = "com.wtz.liveplay.STOP_PLAY";

    private ArrayList<AudioItem> mAudioList = new ArrayList<>();
    private int mListSize;
    private int mIndex;
    private AudioItem mCurrentItem;
    private String mCurrentSource;
    private boolean isPrepared = false;
    private boolean isUserPlaying = false;// 用户当前是否启动了播放
    private boolean isPlayingEnd = true;// 当前播放是否结束（完成、停止、错误）

    private float mVolumePercent = 1.0f;
    private WePlayer mPlayer;
    private WePlayer.OnPreparedListener mOnPreparedListener;
    private WePlayer.OnPlayLoadingListener mOnPlayLoadingListener;
    private WePlayer.OnSeekCompleteListener mOnSeekCompleteListener;
    private WePlayer.OnCompletionListener mOnCompletionListener;
    private WePlayer.OnErrorListener mOnErrorListener;
    // 通过服务通知反馈的几个监听
    private OnPlayItemChangedListener mOnPlayItemChangedListener;
    private OnPlayStatusChangedListener mOnPlayStatusChangedListener;
    private OnUserExitListener mOnUserExitListener;

    private final IBinder mBinder = new LocalBinder();

    public AudioService() {
    }

    public class LocalBinder extends Binder {
        AudioService getService() {
            return AudioService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();

        registerPlayerReceiver();
        startNotification();
    }

    private void registerPlayerReceiver() {
        mPlayerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "mPlayerReceiver onReceive: " + intent.getAction());
                switch (intent.getAction()) {
                    case LAST_SONG_ACTION:
                        pre();
                        break;

                    case NEXT_SONG_ACTION:
                        next();
                        break;

                    case PLAY_PAUSE_ACTION:
                        if (isPlaying()) {
                            pause();
                        } else {
                            start();
                        }
                        break;

                    case STOP_PLAY_ACTION:
                        if (mOnUserExitListener != null) {
                            mOnUserExitListener.onUserExitFromService();
                        }
                        stopSelf();
                        break;
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(PLAY_PAUSE_ACTION);
        intentFilter.addAction(LAST_SONG_ACTION);
        intentFilter.addAction(NEXT_SONG_ACTION);
        intentFilter.addAction(STOP_PLAY_ACTION);
        registerReceiver(mPlayerReceiver, intentFilter);
    }

    private void startNotification() {
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // 设置点击通知结果
        mAudioPlayerIntent = new Intent(this, AudioPlayer.class);
        mAudioPlayerIntent.putParcelableArrayListExtra(AudioPlayer.KEY_AUDIO_LIST, mAudioList);
        mAudioPlayerIntent.putExtra(AudioPlayer.KEY_AUDIO_INDEX, mIndex);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, PENDING_REQUEST_ENTER_CONTENT, mAudioPlayerIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // 自定义布局
        mRemoteViews = new RemoteViews(getPackageName(), R.layout.notify_audio_player);
        // 暂停/播放
        mRemoteViews.setOnClickPendingIntent(R.id.iv_pause_play,
                getPendingIntent(PLAY_PAUSE_ACTION, PENDING_REQUEST_PLAY_PAUSE));
        // 上一首
        mRemoteViews.setOnClickPendingIntent(R.id.iv_last_song,
                getPendingIntent(LAST_SONG_ACTION, PENDING_REQUEST_LAST_SONG));
        // 下一首
        mRemoteViews.setOnClickPendingIntent(R.id.iv_next_song,
                getPendingIntent(NEXT_SONG_ACTION, PENDING_REQUEST_NEXT_SONG));
        // 停止播放
        mRemoteViews.setOnClickPendingIntent(R.id.iv_stop_play,
                getPendingIntent(STOP_PLAY_ACTION, PENDING_REQUEST_STOP_PLAY));
        mAlbumWidth = (int) (getResources().getDimension(ICON_WIDTH_DIMEN_ID) + 0.5f);
        mAlbumHeight = (int) (getResources().getDimension(ICON_HEIGHT_DIMEN_ID) + 0.5f);

        mNotificationBuilder = new NotificationCompat.Builder(this, NOTIFY_CHANNEL_ID);
        mNotificationBuilder.setSmallIcon(R.mipmap.ic_launcher)
                .setTicker("电台直播")/* 通知被显示在状态栏时的信息  */
                .setOngoing(true)
                .setContentTitle("电台直播")
                .setContentText("正在直播电台")
                .setAutoCancel(false)
                // 当通知条目被点击，就执行这个被设置的 ContentIntent
                .setContentIntent(contentIntent)
                // 当用户点击"清除所有通知"按钮的时候，就执行这个被设置的 DeleteIntent
                //.setDeleteIntent(delPendingIntent)
                // 自定义普通大小的视图
                .setContent(mRemoteViews)
                // 自定义大视图
                // .setCustomBigContentView(bigView)
                .setPriority(NotificationCompat.PRIORITY_MAX);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFY_CHANNEL_ID,
                    NOTIFY_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW);
            mNotificationManager.createNotificationChannel(channel);
        }

        startForeground(NOTIFICATION_ID, mNotificationBuilder.build());
    }

    private PendingIntent getPendingIntent(String action, int requestCode) {
        return PendingIntent.getBroadcast(
                this, requestCode, new Intent(action), PendingIntent.FLAG_CANCEL_CURRENT);
    }

    private void updateNotification(boolean onlyUpdatePlayPause) {
        if (mNotificationManager == null || mNotificationBuilder == null
                || mRemoteViews == null || mCurrentItem == null) {
            return;
        }

        if (onlyUpdatePlayPause) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                mHandler.removeCallbacks(mUpdateNotifyPlayPauseRunnable);
                mHandler.post(mUpdateNotifyPlayPauseRunnable);
            } else {
                mUpdateNotifyPlayPauseRunnable.run();
            }
        } else {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                mHandler.removeCallbacks(mUpdateNotifyAllRunnable);
                mHandler.post(mUpdateNotifyAllRunnable);
            } else {
                mUpdateNotifyAllRunnable.run();
            }
        }
    }

    private Runnable mUpdateNotifyAllRunnable = new Runnable() {
        @Override
        public void run() {
            // 更新当前通知展现的 UI 内容
            mRemoteViews.setTextViewText(R.id.tv_title, mCurrentItem.name);
            if (isUserPlaying && !isPlayingEnd) {
                mRemoteViews.setImageViewResource(R.id.iv_pause_play, R.drawable.pause_image_selector);
            } else {
                mRemoteViews.setImageViewResource(R.id.iv_pause_play, R.drawable.play_image_selector);
            }

            // 更新点击通知时进入的 UI 内容
            mAudioPlayerIntent.putParcelableArrayListExtra(AudioPlayer.KEY_AUDIO_LIST, mAudioList);
            mAudioPlayerIntent.putExtra(AudioPlayer.KEY_AUDIO_INDEX, mIndex);
            PendingIntent contentIntent = PendingIntent.getActivity(
                    AudioService.this, PENDING_REQUEST_ENTER_CONTENT, mAudioPlayerIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            mNotificationBuilder.setContentIntent(contentIntent);

            // 提交通知
            Notification notification = mNotificationBuilder.build();
            mNotificationManager.notify(NOTIFICATION_ID, notification);

            // 由于用到 notification，更新图标放在最后边
            // Picasso call should happen from the main thread
            Picasso.get()
                    .load(mCurrentItem.iconPath)
                    // 解决 OOM 问题
                    .resize(mAlbumWidth, mAlbumHeight)
//                    .centerCrop()// 需要先调用fit或resize设置目标大小，否则会报错：Center crop requires calling resize with positive width and height
//                    .placeholder(R.drawable.icon_radio_default)// Cannot use placeholder or error drawables with remote views.
                    .noFade()
                    .into(mRemoteViews, R.id.iv_album, NOTIFICATION_ID, notification);
        }
    };

    private Runnable mUpdateNotifyPlayPauseRunnable = new Runnable() {
        @Override
        public void run() {
            // 更新通知展现的播放/暂停图标
            if (isUserPlaying && !isPlayingEnd) {
                mRemoteViews.setImageViewResource(R.id.iv_pause_play, R.drawable.pause_image_selector);
            } else {
                mRemoteViews.setImageViewResource(R.id.iv_pause_play, R.drawable.play_image_selector);
            }
            mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        mHandler.removeCallbacksAndMessages(null);
        releasePlayer();
        stopNotification();
        stopForeground(true);
        try {
            unregisterReceiver(mPlayerReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    private void stopNotification() {
        if (mNotificationManager != null) {
            mNotificationManager.cancel(NOTIFICATION_ID);
            mNotificationManager = null;
        }
        mRemoteViews = null;
        mNotificationBuilder = null;
    }

    /* ============================== 以下是播放器接口 ============================== */

    public void setOnPreparedListener(WePlayer.OnPreparedListener listener) {
        this.mOnPreparedListener = listener;
    }

    public void setOnPlayLoadingListener(WePlayer.OnPlayLoadingListener listener) {
        this.mOnPlayLoadingListener = listener;
    }

    public void setOnSeekCompleteListener(WePlayer.OnSeekCompleteListener listener) {
        this.mOnSeekCompleteListener = listener;
    }

    public void setOnErrorListener(WePlayer.OnErrorListener listener) {
        this.mOnErrorListener = listener;
    }

    public void setOnCompletionListener(WePlayer.OnCompletionListener listener) {
        this.mOnCompletionListener = listener;
    }

    public void setOnPlayItemChangedListener(OnPlayItemChangedListener listener) {
        this.mOnPlayItemChangedListener = listener;
    }

    public void setOnPlayStatusChangedListener(OnPlayStatusChangedListener listener) {
        this.mOnPlayStatusChangedListener = listener;
    }

    public void setOnUserExitListener(OnUserExitListener listener) {
        this.mOnUserExitListener = listener;
    }

    public void clearAllListener() {
        mOnPreparedListener = null;
        mOnPlayLoadingListener = null;
        mOnSeekCompleteListener = null;
        mOnErrorListener = null;
        mOnCompletionListener = null;
        mOnPlayItemChangedListener = null;
        mOnPlayStatusChangedListener = null;
        mOnUserExitListener = null;
    }

    public void setAudioList(List<AudioItem> list) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("Audio List can't be null");
        }

        mAudioList.clear();
        mAudioList.addAll(list);
        mListSize = mAudioList.size();
    }

    private void pre() {
        if (mIndex == 0) {
            mIndex = mAudioList.size() - 1;
        } else {
            mIndex--;
        }
        openAudio(mIndex);
        if (mOnPlayItemChangedListener != null) {
            mOnPlayItemChangedListener.onPlayItemChangedFromService(mIndex);
        }
    }

    private void next() {
        if (mIndex == mAudioList.size() - 1) {
            mIndex = 0;
        } else {
            mIndex++;
        }
        openAudio(mIndex);
        if (mOnPlayItemChangedListener != null) {
            mOnPlayItemChangedListener.onPlayItemChangedFromService(mIndex);
        }
    }

    public void openAudio(int index) {
        if (mAudioList == null || mAudioList.isEmpty()) {
            throw new IllegalArgumentException("Audio List can't be null");
        }
        if (index < 0 || index >= mListSize) {
            throw new IllegalArgumentException("Audio index is out of bound");
        }

        mIndex = index;
        mCurrentItem = mAudioList.get(index);
        updateNotification(false);
        openAudio(mCurrentItem.playPath);
    }

    public void openAudio(String path) {
        Log.d(TAG, "openAudio...path = " + path);
        mCurrentSource = path;

        if (mPlayer == null) {
            mPlayer = new WePlayer(true);
            mPlayer.setOnPreparedListener(this);
            mPlayer.setOnPlayLoadingListener(this);
            mPlayer.setOnSeekCompleteListener(this);
            mPlayer.setOnCompletionListener(this);
            mPlayer.setOnErrorListener(this);
            mPlayer.setOnStoppedListener(this);
            mPlayer.setVolume(mVolumePercent);
        }

        isPrepared = false;
        mPlayer.reset();
        mPlayer.setDataSource(path);
        mPlayer.prepareAsync();
    }

    public String getCurrentSource() {
        return mCurrentSource;
    }

    @Override
    public void onPrepared() {
        Log.d(TAG, "onPrepared");
        isPrepared = true;
        if (mOnPreparedListener != null) {
            mOnPreparedListener.onPrepared();
        } else {
            start();
        }
    }

    public void start() {
        Log.d(TAG, "start...isPrepared = " + isPrepared);
        if (!isPrepared) {
            return;
        }
        isUserPlaying = true;
        isPlayingEnd = false;
        if (mPlayer != null) {
            mPlayer.start();
        }
        updateNotification(true);
        if (mOnPlayStatusChangedListener != null) {
            mOnPlayStatusChangedListener.onPlayStatusChangedFromService(true);
        }
    }

    @Override
    public void onPlayLoading(boolean isLoading) {
        if (mOnPlayLoadingListener != null) {
            mOnPlayLoadingListener.onPlayLoading(isLoading);
        }
    }

    public void pause() {
        Log.d(TAG, "pause...isPrepared = " + isPrepared);
        if (!isPrepared) {
            return;
        }
        isUserPlaying = false;
        if (mPlayer != null) {
            mPlayer.pause();
        }
        updateNotification(true);
        if (mOnPlayStatusChangedListener != null) {
            mOnPlayStatusChangedListener.onPlayStatusChangedFromService(false);
        }
    }

    public void seekTo(int msec) {
        Log.d(TAG, "seekTo...msec = " + msec + ", isPrepared = " + isPrepared);
        if (!isPrepared) {
            return;
        }
        if (mPlayer != null) {
            mPlayer.seekTo(msec);
        }
    }

    @Override
    public void onSeekComplete() {
        if (mOnSeekCompleteListener != null) {
            mOnSeekCompleteListener.onSeekComplete();
        }
    }

    public int getDuration() {
        int duration = 0;
        if (mPlayer != null) {
            duration = mPlayer.getDuration();
        }
        return duration;
    }

    public int getCurrentPosition() {
        int currentPosition = 0;
        if (mPlayer != null) {
            currentPosition = mPlayer.getCurrentPosition();
        }
        return currentPosition;
    }

    public boolean isPlaying() {
        boolean isPlaying = false;
        try {
            isPlaying = mPlayer.isPlaying();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return isPlaying;
    }

    /**
     * 设置音量
     *
     * @param percent 范围是：0 ~ 1.0
     */
    public void setVolume(float percent) {
        mVolumePercent = percent;
        if (mPlayer != null) {
            mPlayer.setVolume(percent);
        }
    }

    @Override
    public void onCompletion() {
        Log.d(TAG, "onCompletion");
        isPlayingEnd = true;
        updateNotification(true);
        if (mOnCompletionListener != null) {
            mOnCompletionListener.onCompletion();
        }
        if (mOnPlayStatusChangedListener != null) {
            mOnPlayStatusChangedListener.onPlayStatusChangedFromService(false);
        }
    }

    @Override
    public void onError(int code, String msg) {
        Log.e(TAG, "onError " + code + ":" + msg);
        isPlayingEnd = true;
        updateNotification(true);
        if (mOnErrorListener != null) {
            mOnErrorListener.onError(code, msg);
        }
        if (mOnPlayStatusChangedListener != null) {
            mOnPlayStatusChangedListener.onPlayStatusChangedFromService(false);
        }
    }

    public void stop() {
        Log.d(TAG, "stop...");
        isUserPlaying = false;
        if (mPlayer != null) {
            mPlayer.stop();
        }
    }

    @Override
    public void onStopped() {
        Log.d(TAG, "onStopped");
        isPlayingEnd = true;
        updateNotification(true);
        if (mOnPlayStatusChangedListener != null) {
            mOnPlayStatusChangedListener.onPlayStatusChangedFromService(false);
        }
    }

    public void releasePlayer() {
        Log.d(TAG, "invoke releasePlayer...");
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
            Log.d(TAG, "released");
        }
    }

    public interface OnPlayItemChangedListener {
        void onPlayItemChangedFromService(int index);
    }

    public interface OnPlayStatusChangedListener {
        void onPlayStatusChangedFromService(boolean isPlaying);
    }

    public interface OnUserExitListener {
        void onUserExitFromService();
    }

    static class AudioItem implements Parcelable {
        public String name;
        public String iconPath;
        public String playPath;

        public AudioItem(String name, String iconPath, String playPath) {
            this.name = name;
            this.iconPath = iconPath;
            this.playPath = playPath;
        }

        protected AudioItem(Parcel in) {
            name = in.readString();
            iconPath = in.readString();
            playPath = in.readString();
        }

        public static final Creator<AudioItem> CREATOR = new Creator<AudioItem>() {
            @Override
            public AudioItem createFromParcel(Parcel in) {
                return new AudioItem(in);
            }

            @Override
            public AudioItem[] newArray(int size) {
                return new AudioItem[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(name);
            dest.writeString(iconPath);
            dest.writeString(playPath);
        }

        @NonNull
        @Override
        public String toString() {
            return "AudioItem(" + name + ", " + playPath + ")";
        }
    }

}
