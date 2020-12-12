package com.wtz.ffmpeg;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.wtz.ffmpeg.utils.DateTimeUtil;
import com.wtz.ffmpeg.utils.FileChooser;
import com.wtz.ffmpeg.utils.ScreenUtils;
import com.wtz.ffmpegapi.AACEncoder;
import com.wtz.ffmpegapi.MP3Encoder;
import com.wtz.ffmpegapi.PCMRecorder;
import com.wtz.ffmpegapi.WAVSaver;
import com.wtz.ffmpegapi.WePlayer;
import com.wtz.ffmpegapi.utils.LogUtils;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;

public class AudioPlayActivity extends AppCompatActivity implements View.OnClickListener, RadioGroup.OnCheckedChangeListener {
    private static final String TAG = "AudioPlayActivity";

    private WePlayer mWePlayer;
    private int mDuration;
    private int mSeekPosition;
    private boolean isPrepared;
    private boolean isSeeking;
    private boolean isLoading;

    private PCMRecorder.Encoder mPCMEncoder;
    private File mRecordAudioFile;

    private ProgressDialog mProgressDialog;
    private TextView mPlayUrlView;
    private TextView mErrorView;
    private TextView mPlayTimeView;
    private String mDurationText;
    private SeekBar mPlaySeekBar;
    private TextView mRecordTimeView;

    private TextView mDecibels;
    private TextView mVolume;
    private SeekBar mVolumeSeekBar;
    private TextView mPitch;
    private SeekBar mPitchSeekBar;
    private TextView mTempo;
    private SeekBar mTempoSeekBar;
    private static final DecimalFormat DECIBELS_FORMAT = new DecimalFormat("0.00dB");
    private static final DecimalFormat VOLUME_FORMAT = new DecimalFormat("0%");
    private static final DecimalFormat PITCH_FORMAT = new DecimalFormat("0.0");
    private static final float MAX_PITCH = 3.0f;
    private static final float PITCH_ACCURACY = 0.1f;
    private static final DecimalFormat TEMPO_FORMAT = new DecimalFormat("0.0");
    private static final float MAX_TEMPO = 3.0f;
    private static final float TEMPO_ACCURACY = 0.1f;

    private static final int UPDATE_PLAY_TIME_INTERVAL = 300;
    private static final int UPDATE_SOUND_DECIBELS_INTERVAL = 200;
    private static final int UPDATE_RECORD_TIME_INTERVAL = 300;
    private static final int MSG_UPDATE_PLAY_TIME = 1;
    private static final int MSG_UPDATE_SOUND_DECIBELS = 2;
    private static final int MSG_UPDATE_RECORD_TIME = 3;
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_PLAY_TIME:
                    updatePlayTime();
                    removeMessages(MSG_UPDATE_PLAY_TIME);
                    sendEmptyMessageDelayed(MSG_UPDATE_PLAY_TIME, UPDATE_PLAY_TIME_INTERVAL);
                    break;
                case MSG_UPDATE_SOUND_DECIBELS:
                    updateSoundDecibels();
                    removeMessages(MSG_UPDATE_SOUND_DECIBELS);
                    sendEmptyMessageDelayed(MSG_UPDATE_SOUND_DECIBELS, UPDATE_SOUND_DECIBELS_INTERVAL);
                    break;
                case MSG_UPDATE_RECORD_TIME:
                    updateRecordTime();
                    removeMessages(MSG_UPDATE_RECORD_TIME);
                    sendEmptyMessageDelayed(MSG_UPDATE_RECORD_TIME, UPDATE_RECORD_TIME_INTERVAL);
                    break;
            }
        }
    };

    private int mSelectLocalAudioRequestCode;
    private String mLocalAudioPath;

    private int mNetSourceIndex = 0;
    private int mTempSelectNetSourceIndex = 0;
    private static final String[] mNetAudioSources = {
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

    private String mCurrentUrl;
    private static final String RECORD_NAME_TAG = "_record_";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtils.d(TAG, "onCreate ");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_play);

        initViews();

        LocalBroadcastManager.getInstance(this).registerReceiver(
                mBroadcastReceiver, new IntentFilter(FileChooser.ACTION_FILE_CHOOSE_RESULT));
    }

    @Override
    protected void onStart() {
        LogUtils.d(TAG, "onStart");
        super.onStart();
    }

    private void initViews() {
        findViewById(R.id.btn_select_local_audio).setOnClickListener(this);
        findViewById(R.id.btn_select_net_audio).setOnClickListener(this);
        findViewById(R.id.btn_pause_audio).setOnClickListener(this);
        findViewById(R.id.btn_resume_play_audio).setOnClickListener(this);
        findViewById(R.id.btn_stop_play_audio).setOnClickListener(this);
        findViewById(R.id.btn_destroy_audio_player).setOnClickListener(this);
        findViewById(R.id.btn_left_channel).setOnClickListener(this);
        findViewById(R.id.btn_right_channel).setOnClickListener(this);
        findViewById(R.id.btn_stero).setOnClickListener(this);

        findViewById(R.id.btn_start_record_audio).setOnClickListener(this);
        findViewById(R.id.btn_pause_record_audio).setOnClickListener(this);
        findViewById(R.id.btn_resume_record_audio).setOnClickListener(this);
        findViewById(R.id.btn_stop_record_audio).setOnClickListener(this);
        ((RadioGroup) findViewById(R.id.rg_record_type)).setOnCheckedChangeListener(this);

        mPlayUrlView = findViewById(R.id.tv_play_url);
        mErrorView = findViewById(R.id.tv_error_info);
        mPlayTimeView = findViewById(R.id.tv_play_time);
        mDecibels = findViewById(R.id.tv_decibels);
        mVolume = findViewById(R.id.tv_volume);
        mPitch = findViewById(R.id.tv_pitch);
        mTempo = findViewById(R.id.tv_tempo);
        mRecordTimeView = findViewById(R.id.tv_record_time);

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
                if (mWePlayer != null && isPrepared) {
                    mWePlayer.seekTo(seekBar.getProgress());
                } else {
                    mSeekPosition = seekBar.getProgress();
                    isSeeking = false;
                }
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

        mPitchSeekBar = findViewById(R.id.seek_bar_pitch);
        mPitchSeekBar.setMax((int) (MAX_PITCH / PITCH_ACCURACY));
        setSeekbarWith(mPitchSeekBar);
        mPitchSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if (mWePlayer != null) {
                    float pitch = seekBar.getProgress() / (float) seekBar.getMax() * MAX_PITCH;
                    mPitch.setText("音调：" + PITCH_FORMAT.format(pitch));
                    mWePlayer.setPitch(pitch);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                LogUtils.d(TAG, "mPitchSeekBar onStartTrackingTouch");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                LogUtils.d(TAG, "mPitchSeekBar onStopTrackingTouch");
            }
        });

        mTempoSeekBar = findViewById(R.id.seek_bar_tempo);
        mTempoSeekBar.setMax((int) (MAX_TEMPO / TEMPO_ACCURACY));
        setSeekbarWith(mTempoSeekBar);
        mTempoSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if (mWePlayer != null) {
                    float tempo = seekBar.getProgress() / (float) seekBar.getMax() * MAX_TEMPO;
                    mTempo.setText("音速：" + TEMPO_FORMAT.format(tempo));
                    mWePlayer.setTempo(tempo);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                LogUtils.d(TAG, "mTempoSeekBar onStartTrackingTouch");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                LogUtils.d(TAG, "mTempoSeekBar onStopTrackingTouch");
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

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            LogUtils.d(TAG, "mBroadcastReceiver onReceive: " + action);
            if (FileChooser.ACTION_FILE_CHOOSE_RESULT.equals(action)) {
                int code = intent.getIntExtra(FileChooser.RESULT_REQUEST_CODE, -1);
                if (code == mSelectLocalAudioRequestCode) {
                    String url = intent.getStringExtra(FileChooser.RESULT_FILE_PATH);
                    LogUtils.d(TAG, "select local music path: " + url);
                    toast("已选择：" + url);
                    if (!TextUtils.isEmpty(url)) {
                        resetUI();
                        mLocalAudioPath = url;
                        mRecordAudioFile = null;
                        openAudio(mLocalAudioPath);
                    }
                }
            }
        }
    };

    private void showNetAudioListDialog(final Context context) {
        mTempSelectNetSourceIndex = 0;
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setIcon(R.mipmap.ic_launcher_round)
                .setTitle("网络音频选择")
                .setSingleChoiceItems(mNetAudioSources, mTempSelectNetSourceIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mTempSelectNetSourceIndex = which;
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        resetUI();
                        mNetSourceIndex = mTempSelectNetSourceIndex;
                        mRecordAudioFile = null;
                        openAudio(mNetAudioSources[mNetSourceIndex]);
                    }
                }).create();
        dialog.show();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onClick(View view) {
        LogUtils.d(TAG, "onClick " + view);
        switch (view.getId()) {
            case R.id.btn_select_local_audio:
                mSelectLocalAudioRequestCode = FileChooser.chooseAudio(AudioPlayActivity.this);
                break;
            case R.id.btn_select_net_audio:
                showNetAudioListDialog(AudioPlayActivity.this);
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
                startUpdateDecibels();
                break;
            case R.id.btn_stop_play_audio:
                if (mWePlayer == null) {
                    return;
                }
                mWePlayer.stop();
                isPrepared = false;
                mSeekPosition = 0;
                stopUpdateTime();
                stopUpdateDecibels();
                resetUI();
                break;
            case R.id.btn_destroy_audio_player:
                if (mWePlayer == null) {
                    return;
                }
                mWePlayer.release();
                stopUpdateTime();
                stopUpdateDecibels();
                resetUI();
                mWePlayer = null;
                break;
            case R.id.btn_left_channel:
                if (mWePlayer == null) {
                    return;
                }
                mWePlayer.setSoundChannel(WePlayer.SoundChannel.LEFT_CHANNEL);
                break;
            case R.id.btn_right_channel:
                if (mWePlayer == null) {
                    return;
                }
                mWePlayer.setSoundChannel(WePlayer.SoundChannel.RIGHT_CHANNEL);
                break;
            case R.id.btn_stero:
                if (mWePlayer == null) {
                    return;
                }
                mWePlayer.setSoundChannel(WePlayer.SoundChannel.STERO);
                break;
            case R.id.btn_start_record_audio:
                if (mWePlayer == null) {
                    return;
                }
                if (mRecordAudioFile == null) {
                    mPCMEncoder = new MP3Encoder();
                    mRecordAudioFile = getRecordSaveFile(".mp3");
                }
                toast("录音文件保存在：" + mRecordAudioFile.getAbsolutePath());
                mWePlayer.startRecord(mPCMEncoder, mRecordAudioFile);
                startUpdateRecordTime();
                break;
            case R.id.btn_pause_record_audio:
                if (mWePlayer == null) {
                    return;
                }
                mWePlayer.pauseRecord();
                stopUpdateRecordTime();
                break;
            case R.id.btn_resume_record_audio:
                if (mWePlayer == null) {
                    return;
                }
                mWePlayer.resumeRecord();
                startUpdateRecordTime();
                break;
            case R.id.btn_stop_record_audio:
                stopUpdateRecordTime();
                if (mWePlayer == null) {
                    return;
                }
                mWePlayer.stopRecord();
                break;
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
        switch (checkedId) {
            case R.id.rb_mp3:
                mPCMEncoder = new MP3Encoder();
                mRecordAudioFile = getRecordSaveFile(".mp3");
                break;
            case R.id.rb_wav:
                mPCMEncoder = new WAVSaver();
                mRecordAudioFile = getRecordSaveFile(".wav");
                break;
            case R.id.rb_aac:
                mPCMEncoder = new AACEncoder();
                mRecordAudioFile = getRecordSaveFile(".aac");
                break;
        }
    }

    private File getRecordSaveFile(String suffix) {
        if (TextUtils.isEmpty(mCurrentUrl)) {
            return null;
        }
        String baseName = mCurrentUrl.replaceAll("/", "_")
                .replaceAll(":", "_");
        String timeStr = DateTimeUtil.getCurrentDateTime("yyyyMMdd_HHmmss");
        String finalName = baseName + RECORD_NAME_TAG + timeStr + suffix;
        return new File("/sdcard/", finalName);
    }

    private void openAudio(String url) {
        isPrepared = false;
        isLoading = false;
        if (mWePlayer == null) {
            mWePlayer = new WePlayer(true);
            mWePlayer.setOnPreparedListener(new WePlayer.OnPreparedListener() {
                @Override
                public void onPrepared() {
                    LogUtils.d(TAG, "WePlayer onPrepared");
                    isPrepared = true;
                    if (mSeekPosition > 0) {
                        mWePlayer.seekTo(mSeekPosition);
                        mSeekPosition = 0;
                    }
                    mWePlayer.start();

                    float volume = mWePlayer.getVolume();
                    mVolume.setText("音量：" + VOLUME_FORMAT.format(volume));
                    mVolumeSeekBar.setProgress((int) (mVolumeSeekBar.getMax() * volume));

                    float pitch = mWePlayer.getPitch();
                    mPitch.setText("音调：" + PITCH_FORMAT.format(pitch));
                    mPitchSeekBar.setProgress((int) (pitch / MAX_PITCH * mPitchSeekBar.getMax()));

                    float tempo = mWePlayer.getTempo();
                    mTempo.setText("音速：" + TEMPO_FORMAT.format(tempo));
                    mTempoSeekBar.setProgress((int) (tempo / MAX_TEMPO * mTempoSeekBar.getMax()));

                    mDuration = mWePlayer.getDuration();
                    LogUtils.d(TAG, "mDuration=" + mDuration);
                    mPlaySeekBar.setMax(mDuration);
                    mDurationText = DateTimeUtil.changeRemainTimeToHms(mDuration);
                    startUpdateTime();
                    startUpdateDecibels();
                }
            });
            mWePlayer.setOnPlayLoadingListener(new WePlayer.OnPlayLoadingListener() {
                @Override
                public void onPlayLoading(boolean isLoading) {
                    LogUtils.d(TAG, "WePlayer onPlayLoading: " + isLoading);
                    AudioPlayActivity.this.isLoading = isLoading;
                    if (isLoading) {
                        stopUpdateTime();
                        stopUpdateDecibels();
                        showProgressDialog(AudioPlayActivity.this);
                    } else {
                        startUpdateTime();
                        startUpdateDecibels();
                        hideProgressDialog();
                    }
                }
            });
            mWePlayer.setOnSeekCompleteListener(new WePlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete() {
                    LogUtils.d(TAG, "mWePlayer onSeekComplete");
                    isSeeking = false;
                }
            });
            mWePlayer.setOnErrorListener(new WePlayer.OnErrorListener() {
                @Override
                public void onError(int code, String msg) {
                    LogUtils.e(TAG, "WePlayer onError: " + code + "; " + msg);
                    mErrorView.setText("Error " + code);
                    toast("Error:" + msg);
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
        mCurrentUrl = url;
        mPlayUrlView.setText(url);
        mWePlayer.setDataSource(url);
        mWePlayer.prepareAsync();
    }

    private void startUpdateTime() {
        mHandler.sendEmptyMessage(MSG_UPDATE_PLAY_TIME);
    }

    private void stopUpdateTime() {
        mHandler.removeMessages(MSG_UPDATE_PLAY_TIME);
    }

    private void startUpdateDecibels() {
        mHandler.sendEmptyMessage(MSG_UPDATE_SOUND_DECIBELS);
    }

    private void stopUpdateDecibels() {
        mHandler.removeMessages(MSG_UPDATE_SOUND_DECIBELS);
    }

    private void startUpdateRecordTime() {
        mHandler.sendEmptyMessage(MSG_UPDATE_RECORD_TIME);
    }

    private void stopUpdateRecordTime() {
        mHandler.removeMessages(MSG_UPDATE_RECORD_TIME);
    }

    private void resetUI() {
        mPlayUrlView.setText("");
        mErrorView.setText("");
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

    private void updateSoundDecibels() {
        if (mWePlayer == null || isLoading || !mWePlayer.isPlaying()) return;

        mDecibels.setText("分贝：" + DECIBELS_FORMAT.format(mWePlayer.getSoundDecibels()));
    }

    private void updateRecordTime() {
        if (mWePlayer == null) return;
        long recordTime = Math.round(mWePlayer.getRecordTimeSecs() * 1000);
        String recordTimeStr = DateTimeUtil.changeRemainTimeToHms(recordTime);
        mRecordTimeView.setText(recordTimeStr);
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
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        if (mWePlayer != null) {
            mWePlayer.release();
            stopUpdateTime();
            stopUpdateDecibels();
            mWePlayer = null;
        }

        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

}
