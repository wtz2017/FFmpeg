package com.wtz.ffmpeg;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.wtz.ffmpeg.utils.DateTimeUtil;
import com.wtz.ffmpegapi.WePlayer;
import com.wtz.ffmpegapi.CppThreadDemo;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "FFmpegActivity";

    private CppThreadDemo mCppThreadDemo;
    private boolean isProducing;

    private WePlayer mWePlayer;

    private TextView mPlayTimeView;
    private String mDurationText;

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
                mWePlayer.setDataSource("http://mpge.5nd.com/2015/2015-11-26/69708/1.mp3");
//                mWePlayer.setDataSource("http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4");
//                mWePlayer.setDataSource("http://ngcdn004.cnr.cn/live/dszs/index.m3u8");
                mWePlayer.setOnPreparedListener(new WePlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared() {
                        Log.d(TAG, "WePlayer onPrepared");
                        mWePlayer.start();
                        mDurationText = DateTimeUtil.changeRemainTimeToHms(mWePlayer.getDuration());
                        mHandler.sendEmptyMessage(MSG_UPDATE_PLAY_TIME);
                    }
                });
                mWePlayer.setOnPlayLoadingListener(new WePlayer.OnPlayLoadingListener() {
                    @Override
                    public void onPlayLoading(boolean isLoading) {
                        Log.d(TAG, "WePlayer onPlayLoading: " + isLoading);
                    }
                });
                mWePlayer.prepareAsync();
                break;
            case R.id.btn_pause_audio:
                if (mWePlayer == null) {
                    return;
                }
                mWePlayer.pause();
                mHandler.removeMessages(MSG_UPDATE_PLAY_TIME);
                break;
            case R.id.btn_resume_play_audio:
                if (mWePlayer == null) {
                    return;
                }
                mWePlayer.resumePlay();
                mHandler.sendEmptyMessage(MSG_UPDATE_PLAY_TIME);
                break;
            case R.id.btn_stop_play_audio:
                if (mWePlayer == null) {
                    return;
                }
                mWePlayer.stop();
                mHandler.removeMessages(MSG_UPDATE_PLAY_TIME);
                break;
            case R.id.btn_destroy_audio_player:
                if (mWePlayer == null) {
                    return;
                }
                mWePlayer.destroyPlayer();
                mWePlayer = null;
                mHandler.removeMessages(MSG_UPDATE_PLAY_TIME);
                break;
        }
    }

    private void updatePlayTime() {
        String currentPosition = DateTimeUtil.changeRemainTimeToHms(mWePlayer.getCurrentPosition());
        mPlayTimeView.setText(currentPosition + "/" + mDurationText);
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

}
