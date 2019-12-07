package com.wtz.ffmpeg;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.wtz.ffmpeg.utils.DateTimeUtil;
import com.wtz.ffmpeg.utils.ScreenUtils;
import com.wtz.ffmpegapi.WePlayer;
import com.wtz.ffmpegapi.CppThreadDemo;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "FFmpegActivity";

    private CppThreadDemo mCppThreadDemo;
    private boolean isProducing;

    private WePlayer mWePlayer;
    private int mDuration;
    private boolean isSeeking;
    private boolean isLoading;

    private TextView mPlayTimeView;
    private String mDurationText;
    private ProgressDialog mProgressDialog;
    private SeekBar mSeekBar;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCppThreadDemo = new CppThreadDemo();
        mCppThreadDemo.stringToJNI("hello! I'm java!");

        ((TextView) findViewById(R.id.sample_text)).setText(mCppThreadDemo.stringFromJNI());
        findViewById(R.id.btn_simple_pthread).setOnClickListener(this);
        findViewById(R.id.btn_start_product_consumer).setOnClickListener(this);
        findViewById(R.id.btn_stop_product_consumer).setOnClickListener(this);
        findViewById(R.id.btn_c_thread_call_java).setOnClickListener(this);
        findViewById(R.id.btn_java_set_byte_array_to_c).setOnClickListener(this);
        findViewById(R.id.btn_c_set_byte_array_to_java).setOnClickListener(this);
        findViewById(R.id.btn_test_opensl_es).setOnClickListener(this);
        findViewById(R.id.btn_open_audio_play).setOnClickListener(this);
        findViewById(R.id.btn_pause_audio).setOnClickListener(this);
        findViewById(R.id.btn_resume_play_audio).setOnClickListener(this);
        findViewById(R.id.btn_stop_play_audio).setOnClickListener(this);
        findViewById(R.id.btn_destroy_audio_player).setOnClickListener(this);

        mPlayTimeView = findViewById(R.id.tv_play_time);

        mSeekBar = findViewById(R.id.seek_bar);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
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
                Log.d(TAG, "onStartTrackingTouch");
                isSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Log.d(TAG, "onStopTrackingTouch");
                if (mWePlayer != null){
                    mWePlayer.seekTo(seekBar.getProgress());
                }
                isSeeking = false;
            }
        });
        int[] wh = ScreenUtils.getScreenPixels(this);
        int seekWith = (int) Math.round(0.75 * wh[0]);
        ViewGroup.LayoutParams lp = mSeekBar.getLayoutParams();
        lp.width = seekWith;
        mSeekBar.setLayoutParams(lp);
    }

    @Override
    public void onClick(View view) {
        Log.d(TAG, "onClick " + view);
        switch (view.getId()) {
            case R.id.btn_simple_pthread:
                mCppThreadDemo.testSimpleThread();
                break;
            case R.id.btn_start_product_consumer:
                if (!isProducing) {
                    isProducing = true;
                    mCppThreadDemo.startProduceConsumeThread();
                }
                break;
            case R.id.btn_stop_product_consumer:
                if (isProducing) {
                    mCppThreadDemo.stopProduceConsumeThread();
                    isProducing = false;
                }
                break;
            case R.id.btn_c_thread_call_java:
                mCppThreadDemo.setOnResultListener(new CppThreadDemo.OnResultListener() {
                    @Override
                    public void onResult(int code, String msg) {
                        Log.d(TAG, "OnResultListener code: " + code + "; msg: " + msg);
                    }
                });
                mCppThreadDemo.callbackFromC();
                break;
            case R.id.btn_java_set_byte_array_to_c:
                byte[] data = new byte[6];
                for (int i = 0; i < data.length; i++) {
                    data[i] = (byte) i;
                    Log.d(TAG, "before setByteArray data " + i + " = " + data[i]);
                }
                mCppThreadDemo.setByteArray(data);
                for (int i = 0; i < data.length; i++) {
                    Log.d(TAG, "after setByteArray data " + i + " = " + data[i]);
                }
                break;
            case R.id.btn_c_set_byte_array_to_java:
                byte[] array = mCppThreadDemo.getByteArray();
                for (int i = 0; i < array.length; i++) {
                    Log.d(TAG, "getByteArray data " + i + " = " + array[i]);
                }
                break;
            case R.id.btn_test_opensl_es:
                String path = "/sdcard/test.pcm";
                mCppThreadDemo.playPCM(path);
                break;
            case R.id.btn_open_audio_play:
                if (mWePlayer == null) {
                    mWePlayer = new WePlayer();
                }
//                mWePlayer.setDataSource("file:///sdcard/test.mp3");
//                mWePlayer.setDataSource("file:///sdcard/test.ac3");
//                mWePlayer.setDataSource("file:///sdcard/test.mp4");
//                mWePlayer.setDataSource("http://mpge.5nd.com/2015/2015-11-26/69708/1.mp3");
//                mWePlayer.setDataSource("http://music.163.com/song/media/outer/url?id=29750099.mp3");
                mWePlayer.setDataSource("http://music.163.com/song/media/outer/url?id=566435178.mp3");
//                mWePlayer.setDataSource("http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4");
//                mWePlayer.setDataSource("http://ngcdn004.cnr.cn/live/dszs/index.m3u8");
                mWePlayer.setOnPreparedListener(new WePlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared() {
                        Log.d(TAG, "WePlayer onPrepared");
                        mWePlayer.start();

                        mDuration = mWePlayer.getDuration();
                        mSeekBar.setMax(mDuration);
                        mDurationText = DateTimeUtil.changeRemainTimeToHms(mDuration);
                        startUpdateTime();
                    }
                });
                mWePlayer.setOnPlayLoadingListener(new WePlayer.OnPlayLoadingListener() {
                    @Override
                    public void onPlayLoading(boolean isLoading) {
                        Log.d(TAG, "WePlayer onPlayLoading: " + isLoading);
                        MainActivity.this.isLoading = isLoading;
                        if (isLoading) {
                            stopUpdateTime();
                            showProgressDialog(MainActivity.this);
                        } else {
                            startUpdateTime();
                            hideProgressDialog();
                        }
                    }
                });
                mWePlayer.setOnErrorListener(new WePlayer.OnErrorListener() {
                    @Override
                    public void onError(int code, String msg) {
                        Log.e(TAG, "WePlayer onError: " + code + "; " + msg);
                    }
                });
                mWePlayer.setOnCompletionListener(new WePlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion() {
                        Log.d(TAG, "WePlayer onCompletion");
                    }
                });
                mWePlayer.prepareAsync();
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
                resetSeekbarAndTime();
                break;
            case R.id.btn_destroy_audio_player:
                if (mWePlayer == null) {
                    return;
                }
                mWePlayer.destroyPlayer();
                stopUpdateTime();
                resetSeekbarAndTime();
                mWePlayer = null;
                break;
        }
    }

    private void startUpdateTime() {
        mHandler.sendEmptyMessage(MSG_UPDATE_PLAY_TIME);
    }

    private void stopUpdateTime() {
        mHandler.removeMessages(MSG_UPDATE_PLAY_TIME);
    }

    private void resetSeekbarAndTime() {
        mPlayTimeView.setText("00:00:00/" + mDurationText);
        mSeekBar.setProgress(0);
    }

    private void updatePlayTime() {
        if (mWePlayer == null || isLoading) return;

        if (isSeeking) {
            // seek 时 seekbar 会自动更新位置，只需要根据 seek 位置更新时间
            String currentPosition = DateTimeUtil.changeRemainTimeToHms(mSeekBar.getProgress());
            mPlayTimeView.setText(currentPosition + "/" + mDurationText);
        } else if (mWePlayer.isPlaying()){
            // 没有 seek 时，如果还在播放中，就正常按实际播放时间更新时间和 seekbar
            int position = mWePlayer.getCurrentPosition();
            String currentPosition = DateTimeUtil.changeRemainTimeToHms(position);
            mPlayTimeView.setText(currentPosition + "/" + mDurationText);
            mSeekBar.setProgress(position);
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
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

}
