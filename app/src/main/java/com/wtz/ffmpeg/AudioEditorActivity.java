package com.wtz.ffmpeg;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.wtz.ffmpeg.utils.DateTimeUtil;
import com.wtz.ffmpeg.utils.FileChooser;
import com.wtz.ffmpeg.utils.ScreenUtils;
import com.wtz.ffmpeg.view.ProgressRound;
import com.wtz.ffmpegapi.AACEncoder;
import com.wtz.ffmpegapi.MP3Encoder;
import com.wtz.ffmpegapi.PCMRecorder;
import com.wtz.ffmpegapi.WAVSaver;
import com.wtz.ffmpegapi.WeEditor;
import com.wtz.ffmpegapi.WePlayer;
import com.wtz.ffmpegapi.utils.LogUtils;

import java.io.File;
import java.io.IOException;

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

    private int mSelectLocalAudioRequestCode;
    private String mLocalAudioPath;
    private static final String CUT_NAME_TAG = "_cut_";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtils.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_editor);

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
        findViewById(R.id.btn_pause_audio).setOnClickListener(this);
        findViewById(R.id.btn_resume_play_audio).setOnClickListener(this);
        findViewById(R.id.btn_stop_play_audio).setOnClickListener(this);
        findViewById(R.id.btn_set_start_time).setOnClickListener(this);
        findViewById(R.id.btn_set_end_time).setOnClickListener(this);
        findViewById(R.id.btn_start_cut).setOnClickListener(this);
        findViewById(R.id.btn_stop_cut).setOnClickListener(this);
        ((RadioGroup) findViewById(R.id.rg_record_type)).setOnCheckedChangeListener(this);

        mPlayUrl = findViewById(R.id.tv_play_url);
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
                        resetPlayUI();
                        mRecordAudioFile = null;
                        mLocalAudioPath = url;
                        openAudio(mLocalAudioPath);
                    }
                }
            }
        }
    };

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onClick(View view) {
        LogUtils.d(TAG, "onClick " + view);
        switch (view.getId()) {
            case R.id.btn_select_local_audio:
                mSelectLocalAudioRequestCode = FileChooser.chooseAudio(AudioEditorActivity.this);
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
                startCut(mLocalAudioPath);
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
            case R.id.rb_mp3:
                mPCMEncoder = new MP3Encoder();
                mRecordAudioFile = getCutFile(".mp3");
                break;
            case R.id.rb_wav:
                mPCMEncoder = new WAVSaver();
                mRecordAudioFile = getCutFile(".wav");
                break;
            case R.id.rb_aac:
                mPCMEncoder = new AACEncoder();
                mRecordAudioFile = getCutFile(".aac");
                break;
        }
    }

    private File getCutFile(String suffix) {
        if (TextUtils.isEmpty(mLocalAudioPath)) {
            return null;
        }
        String cutPath;
        int lastDotIndex = mLocalAudioPath.lastIndexOf(".");
        if (lastDotIndex != -1) {
            cutPath = mLocalAudioPath.substring(0, lastDotIndex);
        } else {
            cutPath = mLocalAudioPath;
        }
        String timeStr = DateTimeUtil.getCurrentDateTime("yyyyMMdd_HHmmss");
        File file = new File(cutPath + CUT_NAME_TAG + timeStr + suffix);
        boolean createSuccess = false;
        try {
            createSuccess = file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            toast(e.toString());
        }
        if (!createSuccess) {
            toast("没有权限保存在原文件同目录下！将会保存到/sdcard/下");
            file = new File("/sdcard/", file.getName());
        }
        return file;
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

        mPlayUrl.setText(url);
        mWePlayer.setDataSource(url);
        mWePlayer.prepareAsync();
    }

    private void startCut(String url) {
        String startTimeStr = mStartTimeEditor.getText().toString();
        String endTimeStr = mEndTimeEditor.getText().toString();
        if (TextUtils.isEmpty(startTimeStr) || TextUtils.isEmpty(endTimeStr)) {
            toast("请先设置时间范围！");
            return;
        }

        mStartEditTimeMsec = Integer.valueOf(startTimeStr);
        mEndEditTimeMsec = Integer.valueOf(endTimeStr);
        if (mStartEditTimeMsec < 0 || mEndEditTimeMsec > mDurationMsec
                || mStartEditTimeMsec >= mEndEditTimeMsec) {
            toast("时间范围设置无效！");
            return;
        }

        if (mRecordAudioFile == null) {
            mPCMEncoder = new MP3Encoder();
            mRecordAudioFile = getCutFile(".mp3");
        }
        toast("裁减文件将保存到" + mRecordAudioFile.getAbsolutePath());
        if (mWeEditor == null) {
            mWeEditor = new WeEditor();
            mWeEditor.setOnPreparedListener(new WeEditor.OnPreparedListener() {
                @Override
                public void onPrepared() {
                    LogUtils.d(TAG, "WeEditor onPrepared");
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
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
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
