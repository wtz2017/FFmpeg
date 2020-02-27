package com.wtz.ffmpeg;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.media.MediaCodecList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.wtz.ffmpeg.utils.DateTimeUtil;
import com.wtz.ffmpeg.utils.ScreenUtils;
import com.wtz.ffmpegapi.WePlayer;
import com.wtz.ffmpegapi.WeVideoView;
import com.wtz.ffmpegapi.utils.LogUtils;
import com.wtz.ffmpegapi.utils.VideoUtils;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class VideoPlayActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "VideoPlayActivity";

    private int mDuration;
    private boolean isSeeking;
    private boolean isLoading;
    private boolean isPlaying;
    private String mDestroyedDataSource;
    private int mDestroyedPosition;

    private View mVideoLayout;
    private WeVideoView mWeVideoView;
    private ProgressBar mProgressBar;

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

            "http://mpge.5nd.com/2015/2015-11-26/69708/1.mp3",// 测试非视频，带封面
            "http://music.163.com/song/media/outer/url?id=29750099.mp3",// 测试非视频，不带封面

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

        // 用来控制屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

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
        mWeVideoView.onActivityStart();// 不要忘了在此调用
    }

    @Override
    protected void onResume() {
        LogUtils.d(TAG, "onResume");
        super.onResume();
    }

    private void initViews() {
        findViewById(R.id.btn_open_media).setOnClickListener(this);
        findViewById(R.id.btn_next_media).setOnClickListener(this);
        findViewById(R.id.btn_pause_audio).setOnClickListener(this);
        findViewById(R.id.btn_resume_play_audio).setOnClickListener(this);

        mVideoLayout = findViewById(R.id.fl_video_container);
        mProgressBar = findViewById(R.id.pb_normal);

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
                mWeVideoView.seekTo(seekBar.getProgress());
                resumePlay();//视频 seek 后直接播放以获取对应画面
            }
        });

        mVolumeSeekBar = findViewById(R.id.seek_bar_volume);
        mVolumeSeekBar.setMax(100);
        setSeekbarWith(mVolumeSeekBar);
        mVolumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                float percent = seekBar.getProgress() / (float) seekBar.getMax();
                mVolume.setText("音量：" + VOLUME_FORMAT.format(percent));
                mWeVideoView.setVolume(percent);
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

        initVideoView();
    }

    private void initVideoView() {
        mWeVideoView = findViewById(R.id.we_surface_view);
        mWeVideoView.setOnSurfaceCreatedListener(new WeVideoView.OnSurfaceCreatedListener() {
            @Override
            public void onSurfaceCreated() {
                LogUtils.d(TAG, "mWeVideoView onSurfaceCreated mDestroyedDataSource: " + mDestroyedDataSource);
                if (!TextUtils.isEmpty(mDestroyedDataSource)) {
                    mWeVideoView.setDataSource(mDestroyedDataSource);
                    mWeVideoView.prepareAsync();
                }
            }
        });
        mWeVideoView.setOnSurfaceDestroyedListener(new WeVideoView.OnSurfaceDestroyedListener() {
            @Override
            public void onSurfaceDestroyed(String lastDataSource, int lastPosition) {
                LogUtils.d(TAG, "mWeVideoView onSurfaceDestroyed: " + lastDataSource + ":" + lastPosition);
                mDestroyedDataSource = lastDataSource;
                mDestroyedPosition = lastPosition;
                stopUpdateTime();
            }
        });
        mWeVideoView.setOnPreparedListener(new WePlayer.OnPreparedListener() {
            @Override
            public void onPrepared() {
                LogUtils.d(TAG, "mWeVideoView onPrepared mDestroyedPosition=" + mDestroyedPosition);
                if (mDestroyedPosition > 0) {
                    mWeVideoView.seekTo(mDestroyedPosition);
                    mDestroyedPosition = 0;
                }

                if (isPlaying) {
                    mWeVideoView.start();
                } else {
                    mWeVideoView.drawOneFrameThenPause();
                }

                setSufaceLayoutOnPrepared();

                float volume = mWeVideoView.getVolume();
                mVolume.setText("音量：" + VOLUME_FORMAT.format(volume));
                mVolumeSeekBar.setProgress((int) (mVolumeSeekBar.getMax() * volume));

                mDuration = mWeVideoView.getDuration();
                LogUtils.d(TAG, "mDuration=" + mDuration);
                mPlaySeekBar.setMax(mDuration);
                mDurationText = DateTimeUtil.changeRemainTimeToHms(mDuration);
                startUpdateTime();

                mVideoDecoderView.setText("视频编码类型：" + mWeVideoView.getVideoCodecType());
                String useDecoder = mWeVideoView.isVideoHardCodec() ?
                        VideoUtils.findHardCodecType(mWeVideoView.getVideoCodecType()) : "软解";
                mReallyUsedDecoderView.setText("实际解码类型：" + useDecoder);
            }
        });
        mWeVideoView.setOnPlayLoadingListener(new WePlayer.OnPlayLoadingListener() {
            @Override
            public void onPlayLoading(boolean isLoading) {
                LogUtils.d(TAG, "mWeVideoView onPlayLoading: " + isLoading);
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
        mWeVideoView.setOnSeekCompleteListener(new WePlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete() {
                LogUtils.d(TAG, "mWeVideoView onSeekComplete");
                isSeeking = false;
            }
        });
        mWeVideoView.setOnErrorListener(new WePlayer.OnErrorListener() {
            @Override
            public void onError(int code, String msg) {
                LogUtils.e(TAG, "mWeVideoView onError: " + code + "; " + msg);
                mError.setText("Error " + code);
            }
        });
        mWeVideoView.setOnCompletionListener(new WePlayer.OnCompletionListener() {
            @Override
            public void onCompletion() {
                LogUtils.d(TAG, "mWeVideoView onCompletion");
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
                pause();
                break;
            case R.id.btn_resume_play_audio:
                resumePlay();
                break;
        }
    }

    private void openMedia(String url) {
        isPlaying = true;
        mDestroyedDataSource = null;
        mDestroyedPosition = 0;

        mPlayUrl.setText(url);
        mWeVideoView.reset();
        mWeVideoView.setDataSource(url);
        mWeVideoView.prepareAsync();
    }

    private void pause() {
        isPlaying = false;
        mWeVideoView.pause();
        stopUpdateTime();
    }

    private void resumePlay() {
        isPlaying = true;
        mWeVideoView.start();
        startUpdateTime();
    }

    private void setSufaceLayoutOnPrepared() {
        int videoWidth = mWeVideoView.getVideoWidthOnPrepared();
        int videoHeight = mWeVideoView.getVideoHeightOnPrepared();
        if (videoWidth == 0 || videoHeight == 0) {
            LogUtils.w(TAG, "video: " + videoWidth + "x" + videoHeight);
            return;
        }

        float videoRatio = videoWidth * 1.0f / videoHeight;
        float containerRatio = mVideoLayout.getWidth() * 1.0f / mVideoLayout.getHeight();
        ViewGroup.LayoutParams lp = mWeVideoView.getLayoutParams();
        if (containerRatio > videoRatio) {
            // 视频属于瘦高类型
            lp.height = mVideoLayout.getHeight();
            lp.width = (int) (lp.height * videoRatio);
        } else if (containerRatio < videoRatio) {
            // 视频属于矮胖类型
            lp.width = mVideoLayout.getWidth();
            lp.height = (int) (lp.width / videoRatio);
        } else {
            lp.width = mVideoLayout.getWidth();
            lp.height = mVideoLayout.getHeight();
        }
        mWeVideoView.setLayoutParams(lp);
        LogUtils.d(TAG, "video container: " + mVideoLayout.getWidth() + "x" + mVideoLayout.getHeight()
                + ", video: " + videoWidth + "x" + videoHeight + ", video layout: " + lp.width + "x" + lp.height);
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
        if (isLoading) return;

        if (mDuration == 0) {
            // 直播，显示日期时间
            String currentPosition = DateTimeUtil.getCurrentDateTime("HH:mm:ss/yyyy-MM-dd");
            mPlayTimeView.setText(currentPosition);
        } else if (isSeeking) {
            // seek 时 seekbar 会自动更新位置，只需要根据 seek 位置更新时间
            String currentPosition = DateTimeUtil.changeRemainTimeToHms(mPlaySeekBar.getProgress());
            mPlayTimeView.setText(currentPosition + "/" + mDurationText);
        } else if (mWeVideoView.isPlaying()) {
            // 没有 seek 时，如果还在播放中，就正常按实际播放时间更新时间和 seekbar
            int position = mWeVideoView.getCurrentPosition();
            String currentPosition = DateTimeUtil.changeRemainTimeToHms(position);
            mPlayTimeView.setText(currentPosition + "/" + mDurationText);
            mPlaySeekBar.setProgress(position);
        } else {
            // 既没有 seek，也没有播放，那就不更新
        }
    }

    private void showProgressDialog(Context context) {
        if (mProgressBar == null) return;
        mProgressBar.setVisibility(View.VISIBLE);
    }

    private void hideProgressDialog() {
        if (mProgressBar == null) return;
        mProgressBar.setVisibility(View.GONE);
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
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        LogUtils.d(TAG, "onDestroy");
        stopUpdateTime();

        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

}
