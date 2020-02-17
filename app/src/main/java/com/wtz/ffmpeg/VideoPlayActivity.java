package com.wtz.ffmpeg;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Context;
import android.media.MediaCodecList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;

import com.wtz.ffmpeg.utils.DateTimeUtil;
import com.wtz.ffmpeg.utils.ScreenUtils;
import com.wtz.ffmpegapi.WePlayer;
import com.wtz.ffmpegapi.WeSurfaceView;
import com.wtz.ffmpegapi.utils.LogUtils;
import com.wtz.ffmpegapi.utils.VideoUtils;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class VideoPlayActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "VideoPlayActivity";

    private WeSurfaceView mWeSurfaceView;

    private WePlayer mWePlayer;
    private int mDuration;
    private boolean isSeeking;
    private boolean isLoading;

    private ProgressDialog mProgressDialog;
    private TextView mPlayUrl;
    private TextView mError;
    private TextView mPlayTimeView;
    private String mDurationText;
    private SeekBar mPlaySeekBar;

    private TextView mVolume;
    private SeekBar mVolumeSeekBar;
    private static final DecimalFormat VOLUME_FORMAT = new DecimalFormat("0%");

    private TextView mVideoDecoderView;
    private TextView mMachineDecoderView;
    private TextView mReallyUsedDecoderView;

    private static final int UPDATE_PLAY_TIME_INTERVAL = 300;
    private static final int MSG_UPDATE_PLAY_TIME = 1;
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
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

    private static final String[] mSources = {
            // Local File
            "file:///sdcard/0001.优酷网-28 黄石天书-0001-joined.flv",
            "file:///sdcard/BINGO  Super Simple Songs.mp4",
            "file:///sdcard/Open Shut Them  Super Simple Songs.mp4",
            "file:///sdcard/冰河世纪4：大陆漂移.mp4",
            "file:///sdcard/video-h265.mkv",
            "file:///sdcard/掰手腕.wmv",
            "file:///sdcard/机器人总动员.rmvb",

            // HLS(HTTP Live Streaming)
            "http://ivi.bupt.edu.cn/hls/cctv1hd.m3u8",//CCTV1高清
            "http://ivi.bupt.edu.cn/hls/cctv6hd.m3u8",//CCTV6高清

            // 1080P
            "https://www.apple.com/105/media/us/iphone-x/2017/01df5b43-28e4-4848-bf20-490c34a926a7/films/feature/iphone-x-feature-tpl-cc-us-20170912_1920x1080h.mp4",

            // 720P
            "https://www.apple.com/105/media/cn/mac/family/2018/46c4b917_abfd_45a3_9b51_4e3054191797/films/bruce/mac-bruce-tpl-cn-2018_1280x720h.mp4",

            "http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4",// 大熊兔

            // RTMP(Real Time Messaging Protocol)
            "rtmp://live.hkstv.hk.lxdns.com/live/hks1",//香港卫视 打不开
            "rtmp://live.hkstv.hk.lxdns.com/live/hks2",//香港卫视 打不开
            "rtmp://202.69.69.180:443/webcast/bshdlive-pc",// 能打开，很卡
            "rtmp://media3.sinovision.net:1935/live/livestream"// 能打开
    };
    private int mIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtils.d(TAG, "onCreate ");
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_video_play);

        initViews();
        initMachineDecoderInfo();
    }

    private void initMachineDecoderInfo() {
        StringBuilder builder = new StringBuilder("本机视频解码器: ");
        Map<String, String> codecsMap = new HashMap<>();
        String name = "";
        int codecNum = MediaCodecList.getCodecCount();
        for (int i = 0; i < codecNum; i++) {
            String[] types = MediaCodecList.getCodecInfoAt(i).getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].startsWith("video")) {
                    name = types[j].substring(6);
                    if (!codecsMap.containsKey(name)) {
                        codecsMap.put(name, name);
                        builder.append(name);
                        builder.append(";");
                    }
                }
                LogUtils.w("MediaCodecList", "Codec " + i + " type: " + types[j]);
            }
        }
        mMachineDecoderView.setText(builder.toString());
    }

    @Override
    protected void onStart() {
        LogUtils.d(TAG, "onStart");
        super.onStart();
    }

    private void initViews() {
        findViewById(R.id.btn_open_media).setOnClickListener(this);
        findViewById(R.id.btn_next_media).setOnClickListener(this);
        findViewById(R.id.btn_pause_audio).setOnClickListener(this);
        findViewById(R.id.btn_resume_play_audio).setOnClickListener(this);
        findViewById(R.id.btn_stop_play_audio).setOnClickListener(this);
        findViewById(R.id.btn_destroy_audio_player).setOnClickListener(this);

        mWeSurfaceView = findViewById(R.id.we_surface_view);

        mPlayUrl = findViewById(R.id.tv_play_url);
        mPlayUrl.setText(mSources[mIndex]);
        mError = findViewById(R.id.tv_error_info);
        mPlayTimeView = findViewById(R.id.tv_play_time);
        mVolume = findViewById(R.id.tv_volume);
        mVideoDecoderView = findViewById(R.id.tv_video_decoder);
        mMachineDecoderView = findViewById(R.id.tv_machine_decoders);
        mReallyUsedDecoderView = findViewById(R.id.tv_really_use_decoder);

        mPlaySeekBar = findViewById(R.id.seek_bar_play);
        setSeekbarWith(mPlaySeekBar);
        mPlaySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if (isSeeking) {
                    // 因为主动 seek 导致的 seekbar 变化，此时只需要更新时间
                    updatePlayTime();
                } else {
                    // 因为实际播放时间变化而设置 seekbar 导致变化，什么都不用做
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                LogUtils.d(TAG, "mPlaySeekBar onStartTrackingTouch");
                isSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                LogUtils.d(TAG, "mPlaySeekBar onStopTrackingTouch");
                if (mWePlayer != null) {
                    mWePlayer.seekTo(seekBar.getProgress());
                }
                isSeeking = false;
            }
        });

        mVolumeSeekBar = findViewById(R.id.seek_bar_volume);
        mVolumeSeekBar.setMax(100);
        setSeekbarWith(mVolumeSeekBar);
        mVolumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if (mWePlayer != null) {
                    float percent = seekBar.getProgress() / (float) seekBar.getMax();
                    mVolume.setText("音量：" + VOLUME_FORMAT.format(percent));
                    mWePlayer.setVolume(percent);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                LogUtils.d(TAG, "mVolumeSeekBar onStartTrackingTouch");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                LogUtils.d(TAG, "mVolumeSeekBar onStopTrackingTouch");
            }
        });
    }

    private void setSeekbarWith(SeekBar seekbar) {
        int[] wh = ScreenUtils.getScreenPixels(this);
        int seekWith = (int) Math.round(0.75 * wh[0]);

        ViewGroup.LayoutParams lp = seekbar.getLayoutParams();
        lp.width = seekWith;
        seekbar.setLayoutParams(lp);
    }

    @Override
    public void onClick(View view) {
        LogUtils.d(TAG, "onClick " + view);
        switch (view.getId()) {
            case R.id.btn_open_media:
                openMedia(mSources[mIndex]);
                break;
            case R.id.btn_next_media:
                mIndex++;
                if (mIndex >= mSources.length) {
                    mIndex = 0;
                }
                resetUI();
                openMedia(mSources[mIndex]);
                break;
            case R.id.btn_pause_audio:
                if (mWePlayer == null) {
                    return;
                }
                mWePlayer.pause();
                stopUpdateTime();
                break;
            case R.id.btn_resume_play_audio:
                if (mWePlayer == null) {
                    return;
                }
                mWePlayer.start();
                startUpdateTime();
                break;
            case R.id.btn_stop_play_audio:
                if (mWePlayer == null) {
                    return;
                }
                mWePlayer.stop();
                stopUpdateTime();
                resetUI();
                break;
            case R.id.btn_destroy_audio_player:
                if (mWePlayer == null) {
                    return;
                }
                mWePlayer.release();
                stopUpdateTime();
                resetUI();
                mWePlayer = null;
                break;
        }
    }

    private void openMedia(String url) {
        if (mWePlayer == null) {
            mWePlayer = new WePlayer();
            mWePlayer.setSurfaceView(mWeSurfaceView);
            mWePlayer.setOnPreparedListener(new WePlayer.OnPreparedListener() {
                @Override
                public void onPrepared() {
                    LogUtils.d(TAG, "WePlayer onPrepared");
                    mWePlayer.start();

                    float volume = mWePlayer.getVolume();
                    mVolume.setText("音量：" + VOLUME_FORMAT.format(volume));
                    mVolumeSeekBar.setProgress((int) (mVolumeSeekBar.getMax() * volume));

                    mDuration = mWePlayer.getDuration();
                    LogUtils.d(TAG, "mDuration=" + mDuration);
                    mPlaySeekBar.setMax(mDuration);
                    mDurationText = DateTimeUtil.changeRemainTimeToHms(mDuration);
                    startUpdateTime();

                    mVideoDecoderView.setText("视频编码类型：" + mWePlayer.getVideoCodecType());
                    String useDecoder = mWePlayer.isVideoHardCodec() ?
                            VideoUtils.findHardCodecType(mWePlayer.getVideoCodecType()) : "软解";
                    mReallyUsedDecoderView.setText("实际解码类型：" + useDecoder);
                }
            });
            mWePlayer.setOnPlayLoadingListener(new WePlayer.OnPlayLoadingListener() {
                @Override
                public void onPlayLoading(boolean isLoading) {
                    LogUtils.d(TAG, "WePlayer onPlayLoading: " + isLoading);
                    VideoPlayActivity.this.isLoading = isLoading;
                    if (isLoading) {
                        stopUpdateTime();
                        showProgressDialog(VideoPlayActivity.this);
                    } else {
                        startUpdateTime();
                        hideProgressDialog();
                    }
                }
            });
            mWePlayer.setOnErrorListener(new WePlayer.OnErrorListener() {
                @Override
                public void onError(int code, String msg) {
                    LogUtils.e(TAG, "WePlayer onError: " + code + "; " + msg);
                    mError.setText("Error " + code);
                }
            });
            mWePlayer.setOnCompletionListener(new WePlayer.OnCompletionListener() {
                @Override
                public void onCompletion() {
                    LogUtils.d(TAG, "WePlayer onCompletion");
                }
            });
        } else {
            mWePlayer.reset();
        }
        mPlayUrl.setText(url);
        mWePlayer.setDataSource(url);
        mWePlayer.prepareAsync();
    }

    private void startUpdateTime() {
        mHandler.sendEmptyMessage(MSG_UPDATE_PLAY_TIME);
    }

    private void stopUpdateTime() {
        mHandler.removeMessages(MSG_UPDATE_PLAY_TIME);
    }

    private void resetUI() {
        mPlayUrl.setText("");
        mError.setText("");
        mPlayTimeView.setText("00:00:00/" + mDurationText);
        mPlaySeekBar.setProgress(0);
    }

    private void updatePlayTime() {
        if (mWePlayer == null || isLoading) return;

        if (mDuration == 0) {
            // 直播，显示日期时间
            String currentPosition = DateTimeUtil.getCurrentDateTime("HH:mm:ss/yyyy-MM-dd");
            mPlayTimeView.setText(currentPosition);
        } else if (isSeeking) {
            // seek 时 seekbar 会自动更新位置，只需要根据 seek 位置更新时间
            String currentPosition = DateTimeUtil.changeRemainTimeToHms(mPlaySeekBar.getProgress());
            mPlayTimeView.setText(currentPosition + "/" + mDurationText);
        } else if (mWePlayer.isPlaying()) {
            // 没有 seek 时，如果还在播放中，就正常按实际播放时间更新时间和 seekbar
            int position = mWePlayer.getCurrentPosition();
            String currentPosition = DateTimeUtil.changeRemainTimeToHms(position);
            mPlayTimeView.setText(currentPosition + "/" + mDurationText);
            mPlaySeekBar.setProgress(position);
        } else {
            // 既没有 seek，也没有播放，那就不更新
        }
    }

    private void showProgressDialog(Context context) {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(context);
            mProgressDialog.setMessage("正在加载中");
        }
        mProgressDialog.show();
    }

    private void hideProgressDialog() {
        if (mProgressDialog == null) {
            return;
        }
        mProgressDialog.dismiss();
    }

    @Override
    protected void onStop() {
        LogUtils.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        LogUtils.d(TAG, "onDestroy");
        if (mWePlayer != null) {
            mWePlayer.release();
            stopUpdateTime();
            mWePlayer = null;
        }

        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

}
