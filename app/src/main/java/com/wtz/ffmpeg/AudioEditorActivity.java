package com.wtz.ffmpeg;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.wtz.ffmpeg.utils.DateTimeUtil;
import com.wtz.ffmpeg.utils.ScreenUtils;
import com.wtz.ffmpeg.view.ProgressRound;
import com.wtz.ffmpegapi.AACEncoder;
import com.wtz.ffmpegapi.PCMRecorder;
import com.wtz.ffmpegapi.WAVSaver;
import com.wtz.ffmpegapi.WeEditor;
import com.wtz.ffmpegapi.WePlayer;
import com.wtz.ffmpegapi.utils.LogUtils;

import java.io.File;

public class AudioEditorActivity extends AppCompatActivity implements View.OnClickListener, RadioGroup.OnCheckedChangeListener {
    private static final String TAG = "AudioEditorActivity";

    private WePlayer mWePlayer;
    private int mDurationMsec;
    private int mPlayPositionMsec;
    private boolean isSeeking;
    private boolean isLoading;

    private WeEditor mWeEditor;
    private int mStartEditTimeMsec;
    private int mEndEditTimeMsec;

    private PCMRecorder.Encoder mPCMEncoder;
    private File mRecordAudioFile;
    private File mAACFile;
    private File mWAVFile;

    private TextView mPlayUrl;
    private ProgressDialog mPlayProgressDialog;

    private TextView mPlayTimeView;
    private String mDurationText;
    private SeekBar mPlaySeekBar;

    private EditText mStartTimeEditor;
    private EditText mEndTimeEditor;
    private ProgressRound mProgressRound;

    private static final int UPDATE_PLAY_TIME_INTERVAL = 300;
    private static final int UPDATE_CUT_TIME_INTERVAL = 100;
    private static final int MSG_UPDATE_PLAY_TIME = 1;
    private static final int MSG_UPDATE_CUT_TIME = 2;
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_PLAY_TIME:
                    updatePlayTime();
                    removeMessages(MSG_UPDATE_PLAY_TIME);
                    sendEmptyMessageDelayed(MSG_UPDATE_PLAY_TIME, UPDATE_PLAY_TIME_INTERVAL);
                    break;
                case MSG_UPDATE_CUT_TIME:
                    updateCutTime();
                    removeMessages(MSG_UPDATE_CUT_TIME);
                    sendEmptyMessageDelayed(MSG_UPDATE_CUT_TIME, UPDATE_CUT_TIME_INTERVAL);
                    break;
            }
        }
    };

    private static final String[] mSources = {
            // Local File
            "file:///sdcard/但愿人长久 - 王菲.mp3",
            "file:///sdcard/一千年以后 - 林俊杰.mp3",
            "file:///sdcard/卡农 钢琴曲.wma",
            "file:///sdcard/test.ac3",
            "file:///sdcard/test.mp4",
            "file:///sdcard/王铮亮-真爱你的云.ape",
            "file:///sdcard/邓紫棋-爱你.flac",

            // HLS(HTTP Live Streaming)
            "http://ivi.bupt.edu.cn/hls/cctv1hd.m3u8",//CCTV1高清
            "http://ivi.bupt.edu.cn/hls/cctv6hd.m3u8",//CCTV6高清
            "http://rtmpcnr001.cnr.cn/live/zgzs/playlist.m3u8",//中国之声
            "http://rtmpcnr003.cnr.cn/live/yyzs/playlist.m3u8",//音乐之声
            "http://rtmpcnr004.cnr.cn/live/dszs/playlist.m3u8",//经典音乐广播
            "http://ngcdn004.cnr.cn/live/dszs/index.m3u8",//中央广播电台音乐频道
            "http://123.56.16.201:1935/live/fm1006/96K/tzwj_video.m3u8",//北京新闻广播
            "http://123.56.16.201:1935/live/fm994/96K/tzwj_video.m3u8",//北京教学广播
            "http://123.56.16.201:1935/live/am603/96K/tzwj_video.m3u8",//北京故事广播
            "http://audiolive.rbc.cn:1935/live/fm1043/96K/tzwj_video.m3u8",//北京长书广播
            "http://mpge.5nd.com/2015/2015-11-26/69708/1.mp3",
            "http://music.163.com/song/media/outer/url?id=29750099.mp3",
            "http://music.163.com/song/media/outer/url?id=566435178.mp3",
    };
    private int mIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtils.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_editor);

        initViews();
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
        findViewById(R.id.btn_set_start_time).setOnClickListener(this);
        findViewById(R.id.btn_set_end_time).setOnClickListener(this);
        findViewById(R.id.btn_start_cut).setOnClickListener(this);
        findViewById(R.id.btn_stop_cut).setOnClickListener(this);
        ((RadioGroup) findViewById(R.id.rg_record_type)).setOnCheckedChangeListener(this);

        mPlayUrl = findViewById(R.id.tv_play_url);
        mPlayUrl.setText(mSources[mIndex]);
        mPlayTimeView = findViewById(R.id.tv_play_time);

        mStartTimeEditor = findViewById(R.id.et_start_time);
        mEndTimeEditor = findViewById(R.id.et_end_time);
        mProgressRound = findViewById(R.id.cut_progress_round);

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
                resetPlayUI();
                openAudio(mSources[mIndex]);
                break;
            case R.id.btn_next_media:
                mIndex++;
                if (mIndex >= mSources.length) {
                    mIndex = 0;
                }
                resetPlayUI();
                openAudio(mSources[mIndex]);
                break;
            case R.id.btn_pause_audio:
                if (mWePlayer == null) {
                    return;
                }
                mWePlayer.pause();
                stopUpdatePlayTime();
                break;
            case R.id.btn_resume_play_audio:
                if (mWePlayer == null) {
                    return;
                }
                mWePlayer.start();
                startUpdatePlayTime();
                break;
            case R.id.btn_stop_play_audio:
                if (mWePlayer == null) {
                    return;
                }
                mWePlayer.stop();
                stopUpdatePlayTime();
                resetPlayUI();
                break;
            case R.id.btn_set_start_time:
                mStartTimeEditor.setText("" + mPlayPositionMsec);
                break;
            case R.id.btn_set_end_time:
                mEndTimeEditor.setText("" + mPlayPositionMsec);
                break;
            case R.id.btn_start_cut:
                startCut(mSources[mIndex]);
                startUpdateCutTime();
                break;
            case R.id.btn_stop_cut:
                if (mWeEditor == null) {
                    return;
                }
                mWeEditor.stop();
                stopUpdateCutTime();
                break;
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
        switch (checkedId) {
            case R.id.rb_wav:
                mPCMEncoder = new WAVSaver();
                if (mWAVFile == null) {
                    mWAVFile = new File("/sdcard/pcm_cut.wav");
                }
                mRecordAudioFile = mWAVFile;
                break;
            case R.id.rb_aac:
                mPCMEncoder = new AACEncoder();
                if (mAACFile == null) {
                    mAACFile = new File("/sdcard/pcm_cut.aac");
                }
                mRecordAudioFile = mAACFile;
                break;
        }
    }

    private void openAudio(String url) {
        if (mWePlayer == null) {
            mWePlayer = new WePlayer(true);
            mWePlayer.setOnPreparedListener(new WePlayer.OnPreparedListener() {
                @Override
                public void onPrepared() {
                    LogUtils.d(TAG, "WePlayer onPrepared");
                    mWePlayer.start();

                    mDurationMsec = mWePlayer.getDuration();
                    LogUtils.d(TAG, "mDurationMsec=" + mDurationMsec);
                    mPlaySeekBar.setMax(mDurationMsec);
                    mDurationText = DateTimeUtil.changeRemainTimeToHms(mDurationMsec);
                    startUpdatePlayTime();
                }
            });
            mWePlayer.setOnPlayLoadingListener(new WePlayer.OnPlayLoadingListener() {
                @Override
                public void onPlayLoading(boolean isLoading) {
                    LogUtils.d(TAG, "WePlayer onPlayLoading: " + isLoading);
                    AudioEditorActivity.this.isLoading = isLoading;
                    if (isLoading) {
                        stopUpdatePlayTime();
                        showPlayProgressDialog(AudioEditorActivity.this);
                    } else {
                        startUpdatePlayTime();
                        hidePlayProgressDialog();
                    }
                }
            });
            mWePlayer.setOnErrorListener(new WePlayer.OnErrorListener() {
                @Override
                public void onError(int code, String msg) {
                    LogUtils.e(TAG, "WePlayer onError: " + code + "; " + msg);
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

    private void startCut(String url) {
        String startTimeStr = mStartTimeEditor.getText().toString();
        String endTimeStr = mEndTimeEditor.getText().toString();
        if (TextUtils.isEmpty(startTimeStr) || TextUtils.isEmpty(endTimeStr)) {
            Toast.makeText(AudioEditorActivity.this, "请先设置时间范围！",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        mStartEditTimeMsec = Integer.valueOf(startTimeStr);
        mEndEditTimeMsec = Integer.valueOf(endTimeStr);
        if (mStartEditTimeMsec < 0 || mEndEditTimeMsec > mDurationMsec
                || mStartEditTimeMsec >= mEndEditTimeMsec) {
            Toast.makeText(AudioEditorActivity.this, "时间范围设置无效！",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (mWeEditor == null) {
            mWeEditor = new WeEditor();
            mWeEditor.setOnPreparedListener(new WeEditor.OnPreparedListener() {
                @Override
                public void onPrepared() {
                    LogUtils.d(TAG, "WeEditor onPrepared");
                    if (mRecordAudioFile == null) {
                        mPCMEncoder = new WAVSaver();
                        mRecordAudioFile = new File("/sdcard/pcm_cut.wav");
                    }
                    mWeEditor.start(mStartEditTimeMsec, mEndEditTimeMsec, mPCMEncoder, mRecordAudioFile);
                }
            });
            mWeEditor.setOnLoadingDataListener(new WeEditor.OnLoadingDataListener() {
                @Override
                public void onLoading(boolean isLoading) {
                    LogUtils.d(TAG, "WeEditor onLoading " + isLoading);
                }
            });
            mWeEditor.setOnErrorListener(new WeEditor.OnErrorListener() {
                @Override
                public void onError(int code, String msg) {
                    LogUtils.d(TAG, "WeEditor onError " + code + ";" + msg);
                }
            });
            mWeEditor.setOnCompletionListener(new WeEditor.OnCompletionListener() {
                @Override
                public void onCompletion() {
                    LogUtils.d(TAG, "WeEditor onCompletion");
                    AudioEditorActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // 解决实际裁减会有一定微小误差的问题
                            stopUpdateCutTime();
                            mProgressRound.setProgress(1);
                        }
                    });
                }
            });
        } else {
            mWeEditor.reset();
        }

        mWeEditor.setDataSource(url);
        mWeEditor.prepareAsync();
    }

    private void startUpdatePlayTime() {
        mHandler.sendEmptyMessage(MSG_UPDATE_PLAY_TIME);
    }

    private void stopUpdatePlayTime() {
        mHandler.removeMessages(MSG_UPDATE_PLAY_TIME);
    }

    private void updatePlayTime() {
        if (mWePlayer == null || isLoading) return;

        if (mDurationMsec == 0) {
            // 直播，显示日期时间
            String currentPosition = DateTimeUtil.getCurrentDateTime("HH:mm:ss/yyyy-MM-dd");
            mPlayTimeView.setText(currentPosition);
        } else if (isSeeking) {
            // seek 时 seekbar 会自动更新位置，只需要根据 seek 位置更新时间
            mPlayPositionMsec = mPlaySeekBar.getProgress();
            String currentPosition = DateTimeUtil.changeRemainTimeToHms(mPlayPositionMsec);
            mPlayTimeView.setText(currentPosition + "/" + mDurationText);
        } else if (mWePlayer.isPlaying()) {
            // 没有 seek 时，如果还在播放中，就正常按实际播放时间更新时间和 seekbar
            mPlayPositionMsec = mWePlayer.getCurrentPosition();
            String currentPosition = DateTimeUtil.changeRemainTimeToHms(mPlayPositionMsec);
            mPlayTimeView.setText(currentPosition + "/" + mDurationText);
            mPlaySeekBar.setProgress(mPlayPositionMsec);
        } else {
            // 既没有 seek，也没有播放，那就不更新
        }
    }

    private void startUpdateCutTime() {
        mHandler.sendEmptyMessage(MSG_UPDATE_CUT_TIME);
    }

    private void stopUpdateCutTime() {
        mHandler.removeMessages(MSG_UPDATE_CUT_TIME);
    }

    private void updateCutTime() {
        if (mWeEditor == null) {
            return;
        }
        mProgressRound.setProgress(mWeEditor.getCurrentRecordTimeRatio());
    }

    private void resetPlayUI() {
        mPlayUrl.setText("");
        mPlayTimeView.setText("00:00:00/" + mDurationText);
        mPlaySeekBar.setProgress(0);
        hidePlayProgressDialog();
    }

    private void showPlayProgressDialog(Context context) {
        if (mPlayProgressDialog == null) {
            mPlayProgressDialog = new ProgressDialog(context);
            mPlayProgressDialog.setMessage("正在播放加载中...");
        }
        mPlayProgressDialog.show();
    }

    private void hidePlayProgressDialog() {
        if (mPlayProgressDialog == null) {
            return;
        }
        mPlayProgressDialog.dismiss();
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
            stopUpdatePlayTime();
            hidePlayProgressDialog();
            mWePlayer = null;
        }

        if (mWeEditor != null) {
            mWeEditor.release();
            stopUpdateCutTime();
            mWeEditor = null;
        }

        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

}
