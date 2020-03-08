package com.wtz.liveplay;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.wtz.ffmpegapi.WePlayer;
import com.wtz.ffmpegapi.WeVideoView;
import com.wtz.ffmpegapi.utils.LogUtils;
import com.wtz.liveplay.adapter.TVChannelsGridAdapter;
import com.wtz.liveplay.utils.DateTimeUtil;
import com.wtz.liveplay.view.LoadingDialog;

import java.util.ArrayList;
import java.util.List;

public class VideoPlayer extends AppCompatActivity implements View.OnClickListener,
        View.OnTouchListener, WePlayer.OnPreparedListener, WePlayer.OnPlayLoadingListener,
        WePlayer.OnErrorListener, WeVideoView.OnSurfaceCreatedListener,
        WeVideoView.OnSurfaceDestroyedListener {
    private static final String TAG = "VideoPlayer";

    public static final String KEY_VIDEO_LIST = "key_video_list";
    public static final String KEY_VIDEO_INDEX = "key_video_index";
    private ArrayList<TVChannelsGridAdapter.TVItem> mChannelList = new ArrayList<>();
    private int mSize;
    private int mIndex;
    private TVChannelsGridAdapter.TVItem mCurrentItem;

    private View layoutVideoControl;
    private ImageView ivBack;
    private TextView tvName;
    private TextView tvTime;
    private ImageView ivPre;
    private ImageView ivPlay;
    private ImageView ivNext;

    private LoadingDialog mLoadingDialog;
    private Toast mErrorToast;

    private WeVideoView mWeVideoView;

    private boolean isPrepared;
    private boolean isLoading;
    private boolean isUserPlaying;
    private String mDestroyedDataSource;
    private int mDestroyedPosition;

    private static final int UPDATE_TIME_INTERVAL = 300;
    private static final int HIDE_CONTROL_INTERVAL = 5000;
    private static final int MSG_UPDATE_TIME = 1;
    private static final int MSG_HIDE_CONTROL = 2;
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_TIME:
                    updatePlayTime();
                    removeMessages(MSG_UPDATE_TIME);
                    sendEmptyMessageDelayed(MSG_UPDATE_TIME, UPDATE_TIME_INTERVAL);
                    break;

                case MSG_HIDE_CONTROL:
                    hideControl();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtils.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // 用来控制屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (!initData(getIntent())) return;

        setContentView(R.layout.activity_video_player);

        initViews();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // 由于设置了 singleInstance 模式，按了 Home 键隐藏后再次进入时会调用此方法
        LogUtils.d(TAG, "onNewIntent " + intent + ";getExtras:" + intent.getExtras());
        super.onNewIntent(intent);

        if (!initData(getIntent())) return;

        if (!mCurrentItem.url.equals(mWeVideoView.getCurrentSource())) {
            startNewVideo();
        } else if (!mWeVideoView.isPlaying()) {
            play();
        }
    }

    @Override
    protected void onStart() {
        LogUtils.d(TAG, "onStart");
        mWeVideoView.onActivityStart();// 不要忘了在此调用
        super.onStart();
    }

    @Override
    protected void onResume() {
        LogUtils.d(TAG, "onResume");
        startUpdateTime();
        super.onResume();
    }

    private boolean initData(Intent intent) {
        List<TVChannelsGridAdapter.TVItem> list = intent.getParcelableArrayListExtra(KEY_VIDEO_LIST);
        if (list != null && !list.isEmpty()) {
            mChannelList.clear();
            mChannelList.addAll(list);
            mSize = mChannelList.size();
            mIndex = intent.getIntExtra(KEY_VIDEO_INDEX, 0);
            if (mIndex < 0 || mIndex >= mSize) {
                mIndex = 0;
            }
            mCurrentItem = mChannelList.get(mIndex);
        } else if (mChannelList == null || mChannelList.isEmpty()) {
            LogUtils.e(TAG, "initData failed! mChannelList is null or empty!");
            finish();
            return false;
        }

        LogUtils.d(TAG, "initData mCurrentItem: " + mCurrentItem);
        return true;
    }

    private void initViews() {
        mWeVideoView = findViewById(R.id.we_surface_view);
        mWeVideoView.setOnTouchListener(this);
        mWeVideoView.setOnClickListener(this);
        mWeVideoView.setOnSurfaceCreatedListener(this);
        mWeVideoView.setOnSurfaceDestroyedListener(this);
        mWeVideoView.setOnPreparedListener(this);
        mWeVideoView.setOnPlayLoadingListener(this);
        mWeVideoView.setOnErrorListener(this);
        mWeVideoView.setVolume(1.0f);

        layoutVideoControl = findViewById(R.id.rl_video_control_layer);
        layoutVideoControl.setOnTouchListener(this);
        layoutVideoControl.setOnClickListener(this);
        startDelayHideControl();

        ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(this);
        ivBack.setOnTouchListener(this);

        tvName = findViewById(R.id.tv_name);
        tvName.setText(mChannelList.get(mIndex).name);

        tvTime = findViewById(R.id.tv_time);
        startUpdateTime();

        ivPre = (ImageView) this.findViewById(R.id.iv_pre);
        ivPre.setOnClickListener(this);
        ivPre.setOnTouchListener(this);

        ivPlay = (ImageView) this.findViewById(R.id.iv_play);
        ivPlay.setOnClickListener(this);
        ivPlay.setOnTouchListener(this);

        ivNext = (ImageView) this.findViewById(R.id.iv_next);
        ivNext.setOnClickListener(this);
        ivNext.setOnTouchListener(this);

        mLoadingDialog = new LoadingDialog(this);
    }

    @Override
    public void onSurfaceCreated() {
        LogUtils.d(TAG, "mWeVideoView onSurfaceCreated mDestroyedDataSource: " + mDestroyedDataSource);
        if (!TextUtils.isEmpty(mDestroyedDataSource)) {
            mWeVideoView.setDataSource(mDestroyedDataSource);
            mWeVideoView.prepareAsync();
        } else {
            startNewVideo();
        }
    }

    @Override
    public void onSurfaceDestroyed(String lastDataSource, int lastPosition) {
        LogUtils.d(TAG, "mWeVideoView onSurfaceDestroyed: " + lastDataSource + ":" + lastPosition);
        mDestroyedDataSource = lastDataSource;
        mDestroyedPosition = lastPosition;
        stopUpdateTime();
    }

    private void startUpdateTime() {
        mHandler.removeMessages(MSG_UPDATE_TIME);
        mHandler.sendEmptyMessage(MSG_UPDATE_TIME);
    }

    private void stopUpdateTime() {
        mHandler.removeMessages(MSG_UPDATE_TIME);
    }

    private void updatePlayTime() {
        tvTime.setText(DateTimeUtil.getCurrentDateTime("HH:mm:ss"));
    }

    private void startDelayHideControl() {
        mHandler.removeMessages(MSG_HIDE_CONTROL);
        mHandler.sendEmptyMessageDelayed(MSG_HIDE_CONTROL, HIDE_CONTROL_INTERVAL);
    }

    private void stopHideControl() {
        mHandler.removeMessages(MSG_HIDE_CONTROL);
    }

    private void hideControl() {
        LogUtils.d(TAG, "hideControl");
        layoutVideoControl.setVisibility(View.GONE);
        stopUpdateTime();
    }

    private void showControl() {
        LogUtils.d(TAG, "showControl");
        layoutVideoControl.setVisibility(View.VISIBLE);
        startUpdateTime();
    }

    @Override
    public void onClick(View view) {
        LogUtils.d(TAG, "onClick " + view);
        if (layoutVideoControl.getVisibility() != View.VISIBLE) {
            showControl();
            startDelayHideControl();
        } else {
            boolean needNowHide = false;
            switch (view.getId()) {
                case R.id.iv_back:
                    finish();
                    break;

                case R.id.iv_pre:
                    pre();
                    break;

                case R.id.iv_next:
                    next();
                    break;

                case R.id.iv_play:
                    if (mWeVideoView.isPlaying()) {
                        pause();
                    } else {
                        if (isPrepared) {
                            play();
                        } else {
                            startNewVideo();
                        }
                    }
                    break;
                default:
                    needNowHide = true;
                    break;
            }
            if (needNowHide) {
                hideControl();
                stopHideControl();
            } else {
                startDelayHideControl();
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        LogUtils.d(TAG, "onTouchEvent " + event);
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        LogUtils.d(TAG, "onTouch view:" + view + "; event:" + event);
        switch (view.getId()) {
            case R.id.iv_back:
                ivBack.requestFocus();
                break;

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

    @Override
    public void onPrepared() {
        LogUtils.d(TAG, "mWeVideoView onPrepared mDestroyedPosition=" + mDestroyedPosition);
        isPrepared = true;
        if (mDestroyedPosition > 0) {
            mWeVideoView.seekTo(mDestroyedPosition);
            mDestroyedPosition = 0;
        }

        if (isUserPlaying) {
            mWeVideoView.start();
            ivPlay.setImageResource(R.drawable.pause_image_selector);
        } else {
            mWeVideoView.drawOneFrameThenPause();
            ivPlay.setImageResource(R.drawable.play_image_selector);
        }

        startUpdateTime();
    }

    @Override
    public void onPlayLoading(boolean isLoading) {
        this.isLoading = isLoading;
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
        LogUtils.e(TAG, "onError " + code + ":" + msg);
        ivPlay.setImageResource(R.drawable.play_image_selector);
        if (mErrorToast == null) {
            mErrorToast = Toast.makeText(VideoPlayer.this, R.string.sorry_load_failed,
                    Toast.LENGTH_SHORT);
            mErrorToast.setGravity(Gravity.CENTER, 0, 0);
        }
        mErrorToast.show();
    }

    private void pre() {
        if (mIndex == 0) {
            mIndex = mChannelList.size() - 1;
        } else {
            mIndex--;
        }
        mCurrentItem = mChannelList.get(mIndex);

        startNewVideo();
    }

    private void next() {
        if (mIndex == mChannelList.size() - 1) {
            mIndex = 0;
        } else {
            mIndex++;
        }
        mCurrentItem = mChannelList.get(mIndex);

        startNewVideo();
    }

    private void startNewVideo() {
        tvName.setText(mCurrentItem.name);
        mDestroyedDataSource = null;
        mDestroyedPosition = 0;

        isPrepared = false;
        isUserPlaying = true;
        mWeVideoView.reset();
        mWeVideoView.setDataSource(mCurrentItem.url);
        mWeVideoView.prepareAsync();
    }

    private void play() {
        isUserPlaying = true;
        ivPlay.setImageResource(R.drawable.pause_image_selector);
        mWeVideoView.start();
    }

    private void pause() {
        isUserPlaying = false;
        ivPlay.setImageResource(R.drawable.play_image_selector);
        mWeVideoView.pause();
    }

    @Override
    protected void onPause() {
        LogUtils.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        LogUtils.d(TAG, "onStop");
        mWeVideoView.onActivityStop();// 不要忘了在此调用
        stopUpdateTime();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        LogUtils.d(TAG, "onDestroy");
        hideLoading();
        mLoadingDialog = null;

        if (mErrorToast != null) {
            mErrorToast.cancel();
        }

        if (mChannelList != null) {
            mChannelList.clear();
            mChannelList = null;
        }

        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

}
