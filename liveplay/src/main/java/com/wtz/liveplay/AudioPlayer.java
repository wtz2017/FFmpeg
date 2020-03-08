package com.wtz.liveplay;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.squareup.picasso.Picasso;
import com.wtz.ffmpegapi.WePlayer;
import com.wtz.liveplay.utils.DateTimeUtil;
import com.wtz.liveplay.utils.ScreenUtils;
import com.wtz.liveplay.view.LoadingDialog;

import java.util.ArrayList;
import java.util.List;

public class AudioPlayer extends AppCompatActivity implements View.OnClickListener,
        View.OnTouchListener, WePlayer.OnPreparedListener, WePlayer.OnPlayLoadingListener,
        AudioService.OnPlayItemChangedListener, AudioService.OnPlayStatusChangedListener,
        WePlayer.OnErrorListener, AudioService.OnUserExitListener {
    private static final String TAG = "AudioPlayer";

    public static final String KEY_AUDIO_LIST = "key_audio_list";
    public static final String KEY_AUDIO_INDEX = "key_audio_index";
    private List<AudioService.AudioItem> mAudioList = new ArrayList<>();
    private int mSize;
    private int mIndex;
    private AudioService.AudioItem mCurrentItem;

    private ImageView ivAlbum;
    private int mAlbumWidth;
    private int mAlbumHeight;
    private Drawable mAlbumDrawable;

    private TextView tvTime;

    private TextView tvName;

    private ImageView ivPre;
    private ImageView ivPlay;
    private ImageView ivNext;

    private LoadingDialog mLoadingDialog;
    private Toast mErrorToast;

    private boolean isPrepared;

    private static final int UPDATE_PLAY_TIME_INTERVAL = 300;
    private static final int MSG_UPDATE_PLAY_TIME = 1;
    private Handler mHandler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_PLAY_TIME:
                    updatePlayTime();
                    removeMessages(MSG_UPDATE_PLAY_TIME);
                    sendEmptyMessageDelayed(MSG_UPDATE_PLAY_TIME, UPDATE_PLAY_TIME_INTERVAL);
                    break;
            }
        }
    };

    private AudioService mService;
    private boolean mBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        if (!initData(getIntent())) return;

        configView();

        Intent playService = new Intent(this, AudioService.class);
        startService(playService);// 启动服务，保证本界面退出时服务不会终止
        bindService(playService, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // 由于设置了 singleInstance 模式，按了 Home 键隐藏后再次进入时会调用此方法
        Log.d(TAG, "onNewIntent " + intent + ";getExtras:" + intent.getExtras());
        super.onNewIntent(intent);

        initData(intent);// 可能从列表页来，数据不空，也可能从服务通知来，数据为空

        if (!isServiceOK()) return;

        mService.setAudioList(mAudioList);

        if (!mCurrentItem.playPath.equals(mService.getCurrentSource())) {
            startNewAudio();
        } else if (!isPlaying()) {
            play();
        }
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        startUpdateTime();
        super.onResume();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        Log.w(TAG, "onConfigurationChanged newConfig:" + newConfig);
        super.onConfigurationChanged(newConfig);

        if (ivAlbum != null) {
            mAlbumDrawable = ivAlbum.getDrawable();
        }
        configView();
    }

    private void configView() {
        if (ScreenUtils.isPortrait(this)) {
            // 竖屏
            setContentView(R.layout.activity_audio_player_portrait);
        } else {
            // 横屏
            setContentView(R.layout.activity_audio_player_landscape);
        }
        initViews();
    }

    /**
     * 可能从列表页来，也可能从服务通知点击来；
     * intent 数据可以为空，但是当前的 mAudioList 不能为空
     */
    private boolean initData(Intent intent) {
        List<AudioService.AudioItem> list = intent.getParcelableArrayListExtra(KEY_AUDIO_LIST);
        if (list != null && !list.isEmpty()) {
            mAudioList.clear();
            mAudioList.addAll(list);
            mSize = mAudioList.size();
            mIndex = intent.getIntExtra(KEY_AUDIO_INDEX, 0);
            if (mIndex < 0 || mIndex >= mSize) {
                mIndex = 0;
            }
            mCurrentItem = mAudioList.get(mIndex);
        } else if (mAudioList == null || mAudioList.isEmpty()) {
            Log.e(TAG, "initData failed! mAudioList is null or empty!");
            finish();
            return false;
        }

        Log.d(TAG, "initData mCurrentItem: " + mCurrentItem);
        return true;
    }

    private void initViews() {
        ivAlbum = findViewById(R.id.iv_album);
        setAlbumLayout(ivAlbum);
        if (mAlbumDrawable != null) {
            ivAlbum.setImageDrawable(mAlbumDrawable);
        } else {
            ivAlbum.setImageResource(R.drawable.icon_radio_default);
        }

        tvTime = findViewById(R.id.tv_time);
        startUpdateTime();

        tvName = findViewById(R.id.tv_name);
        tvName.setText(mAudioList.get(mIndex).name);

        ivPre = (ImageView) this.findViewById(R.id.iv_pre);
        ivPre.setOnClickListener(this);
        ivPre.setOnTouchListener(this);

        ivPlay = (ImageView) this.findViewById(R.id.iv_play);
        ivPlay.setOnClickListener(this);
        ivPlay.setOnTouchListener(this);
        if (!isPlaying()) {
            ivPlay.setImageResource(R.drawable.play_image_selector);
        } else {
            ivPlay.setImageResource(R.drawable.pause_image_selector);
        }

        ivNext = (ImageView) this.findViewById(R.id.iv_next);
        ivNext.setOnClickListener(this);
        ivNext.setOnTouchListener(this);

        mLoadingDialog = new LoadingDialog(this);
    }

    private void startUpdateTime() {
        mHandler.sendEmptyMessage(MSG_UPDATE_PLAY_TIME);
    }

    private void stopUpdateTime() {
        mHandler.removeMessages(MSG_UPDATE_PLAY_TIME);
    }

    private void updatePlayTime() {
        tvTime.setText(DateTimeUtil.getCurrentDateTime("HH:mm:ss"));
    }

    private void setAlbumLayout(ImageView album) {
        int[] wh = ScreenUtils.getScreenPixels(this);
        int albumWidth;
        if (ScreenUtils.isPortrait(this)) {
            albumWidth = (int) Math.round(wh[0] * 0.85);
        } else {
            albumWidth = (int) Math.round(wh[0] * 0.5 * 0.85);
        }
        if (albumWidth > wh[1]) {
            albumWidth = (int) Math.round(wh[1] * 0.85);
        }

        mAlbumHeight = mAlbumWidth = albumWidth;
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) album.getLayoutParams();
        lp.width = albumWidth;
        lp.height = albumWidth;
        album.setLayoutParams(lp);
    }

    @Override
    public void onClick(View view) {
        Log.d(TAG, "onClick " + view);
        switch (view.getId()) {
            case R.id.iv_pre:
                pre();
                break;
            case R.id.iv_next:
                next();
                break;
            case R.id.iv_play:
                if (isPlaying()) {
                    pause();
                } else {
                    if (isPrepared) {
                        play();
                    } else {
                        startNewAudio();
                    }
                }
                break;
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        switch (view.getId()) {
            case R.id.iv_pre:
                ivPre.requestFocus();
                break;
            case R.id.iv_next:
                ivNext.requestFocus();
                break;
            case R.id.iv_play:
                ivPlay.requestFocus();
                break;
        }
        return false;
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            Log.d(TAG, "onServiceConnected");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            AudioService.LocalBinder binder = (AudioService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;

            initPlayService();

            if (!mCurrentItem.playPath.equals(mService.getCurrentSource())) {
                startNewAudio();
            } else {
                tvName.setText(mCurrentItem.name);
                updateAlbum();
                if (!isPlaying()) {
                    play();
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.e(TAG, "onServiceDisconnected");
            mBound = false;
        }
    };

    private boolean isServiceOK() {
        return mService != null && mBound;
    }

    private boolean isPlaying() {
        if (isServiceOK()) {
            return mService.isPlaying();
        }
        return false;
    }

    private void initPlayService() {
        if (isServiceOK()) {
            mService.setAudioList(mAudioList);
            mService.setOnPreparedListener(this);
            mService.setOnPlayLoadingListener(this);
            mService.setOnErrorListener(this);
            mService.setOnPlayItemChangedListener(this);
            mService.setOnPlayStatusChangedListener(this);
            mService.setOnUserExitListener(this);
        }
    }

    @Override
    public void onPrepared() {
        isPrepared = true;
        play();
    }

    @Override
    public void onPlayLoading(boolean isLoading) {
        if (isLoading) {
            showLoading();
        } else {
            hideLoading();
        }
    }

    private void showLoading() {
        if (mLoadingDialog != null) {
            mLoadingDialog.show();
        }
    }

    private void hideLoading() {
        if (mLoadingDialog != null) {
            mLoadingDialog.cancel();
        }
    }

    @Override
    public void onError(int code, String msg) {
        Log.e(TAG, "onError " + code + ":" + msg);
        ivPlay.setImageResource(R.drawable.play_image_selector);
        if (mErrorToast == null) {
            mErrorToast = Toast.makeText(AudioPlayer.this, R.string.sorry_load_failed,
                    Toast.LENGTH_SHORT);
            mErrorToast.setGravity(Gravity.CENTER, 0, 0);
        }
        mErrorToast.show();
    }

    private void openAudio() {
        isPrepared = false;
        if (isServiceOK()) {
            mService.openAudio(mIndex);
        }
    }

    private void pre() {
        if (mIndex == 0) {
            mIndex = mAudioList.size() - 1;
        } else {
            mIndex--;
        }
        mCurrentItem = mAudioList.get(mIndex);

        startNewAudio();
    }

    private void next() {
        if (mIndex == mAudioList.size() - 1) {
            mIndex = 0;
        } else {
            mIndex++;
        }
        mCurrentItem = mAudioList.get(mIndex);

        startNewAudio();
    }

    @Override
    public void onPlayItemChangedFromService(int index) {
        Log.d(TAG, "onPlayItemChangedFromService index=" + index);
        mIndex = index;
        mCurrentItem = mAudioList.get(mIndex);
        tvName.setText(mCurrentItem.name);
        updateAlbum();
    }

    private void startNewAudio() {
        resetPlayUI();
        stopPlay();

        tvName.setText(mCurrentItem.name);
        updateAlbum();

        openAudio();
    }

    private void play() {
        if (isServiceOK()) {
            mService.start();
            ivPlay.setImageResource(R.drawable.pause_image_selector);
        }
    }

    private void pause() {
        if (isServiceOK()) {
            mService.pause();
            ivPlay.setImageResource(R.drawable.play_image_selector);
        }
    }

    @Override
    public void onPlayStatusChangedFromService(boolean isPlaying) {
        Log.d(TAG, "onPlayStatusChangedFromService isPlaying=" + isPlaying);
        if (isPlaying) {
            ivPlay.setImageResource(R.drawable.pause_image_selector);
        } else {
            ivPlay.setImageResource(R.drawable.play_image_selector);
        }
    }

    @Override
    public void onUserExitFromService() {
        Log.w(TAG, "onUserExitFromService");
        finish();
    }

    private void stopPlay() {
        if (isServiceOK()) {
            mService.stop();
        }
        isPrepared = false;
    }

    private void releasePlayService() {
        if (isServiceOK()) {
            mService.releasePlayer();
        }
    }

    private void resetPlayUI() {
        Picasso.get().cancelRequest(ivAlbum);
        ivAlbum.setImageResource(R.drawable.icon_radio_default);
    }

    private void updateAlbum() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                // Picasso call should happen from the main thread
                Picasso.get()
                        .load(mCurrentItem.iconPath)
                        // 解决 OOM 问题
                        .resize(mAlbumWidth, mAlbumHeight)
                        .centerCrop()// 需要先调用fit或resize设置目标大小，否则会报错：Center crop requires calling resize with positive width and height
                        .placeholder(R.drawable.icon_radio_default)
                        .noFade()
                        .into(ivAlbum);
            }
        });
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        stopUpdateTime();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (ivAlbum != null) {
            Picasso.get().cancelRequest(ivAlbum);
        }
        mAlbumDrawable = null;

        hideLoading();
        mLoadingDialog = null;

        if (mErrorToast != null) {
            mErrorToast.cancel();
        }

        if (mBound) {
            mService.clearAllListener();
            unbindService(mConnection);
            mService = null;
            mBound = false;
        }

        if (mAudioList != null) {
            mAudioList.clear();
            mAudioList = null;
        }

        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

}
