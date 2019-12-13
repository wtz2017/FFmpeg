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
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.wtz.ffmpeg.utils.DateTimeUtil;
import com.wtz.ffmpeg.utils.ScreenUtils;
import com.wtz.ffmpegapi.WePlayer;
import com.wtz.ffmpegapi.CppThreadDemo;

import java.text.DecimalFormat;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "FFmpegActivity";

    private CppThreadDemo mCppThreadDemo;
    private boolean isProducing;

    private WePlayer mWePlayer;
    private int mDuration;
    private boolean isSeeking;
    private boolean isLoading;

    private View mBaseTestRoot;

    private ProgressDialog mProgressDialog;
    private TextView mPlayUrl;
    private TextView mError;
    private TextView mPlayTimeView;
    private String mDurationText;
    private SeekBar mPlaySeekBar;

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
    private static final int MSG_UPDATE_PLAY_TIME = 1;
    private static final int MSG_UPDATE_SOUND_DECIBELS = 2;
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
            }
        }
    };

    private static final String[] mSources = {
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

            // TODO 打不开 应该是当前库不支持 https
            "https://lhttp.qingting.fm/live/20462/64k.mp3",//经典调频北京FM969

            // Local File
            "file:///sdcard/test.mp3",
            "file:///sdcard/test.ac3",
            "file:///sdcard/test.mp4",

            "http://mpge.5nd.com/2015/2015-11-26/69708/1.mp3",
            "http://music.163.com/song/media/outer/url?id=29750099.mp3",
            "http://music.163.com/song/media/outer/url?id=566435178.mp3",

            // 1080P // TODO 打不开 应该是当前库不支持 https
            "https://www.apple.com/105/media/us/iphone-x/2017/01df5b43-28e4-4848-bf20-490c34a926a7/films/feature/iphone-x-feature-tpl-cc-us-20170912_1920x1080h.mp4",

            // 720P // TODO 打不开 应该是当前库不支持 https
            "https://www.apple.com/105/media/cn/mac/family/2018/46c4b917_abfd_45a3_9b51_4e3054191797/films/bruce/mac-bruce-tpl-cn-2018_1280x720h.mp4",

            // RTSP(Real Time Streaming Protocol)
            "rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mov",// 大熊兔 // TODO 打不开
            "http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4",// 大熊兔

            // RTMP(Real Time Messaging Protocol)
            "rtmp://live.hkstv.hk.lxdns.com/live/hks1",//香港卫视 // TODO 打不开
            "rtmp://live.hkstv.hk.lxdns.com/live/hks2",//香港卫视 // TODO 打不开
            "rtmp://202.69.69.180:443/webcast/bshdlive-pc",
            "rtmp://media3.sinovision.net:1935/live/livestream"
    };
    private int mIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCppThreadDemo = new CppThreadDemo();
        mCppThreadDemo.stringToJNI("hello! I'm java!");

        mBaseTestRoot = findViewById(R.id.base_test_root);
        findViewById(R.id.btn_base_test_control).setOnClickListener(this);
        ((TextView) findViewById(R.id.sample_text)).setText(mCppThreadDemo.stringFromJNI());
        findViewById(R.id.btn_simple_pthread).setOnClickListener(this);
        findViewById(R.id.btn_start_product_consumer).setOnClickListener(this);
        findViewById(R.id.btn_stop_product_consumer).setOnClickListener(this);
        findViewById(R.id.btn_c_thread_call_java).setOnClickListener(this);
        findViewById(R.id.btn_java_set_byte_array_to_c).setOnClickListener(this);
        findViewById(R.id.btn_c_set_byte_array_to_java).setOnClickListener(this);
        findViewById(R.id.btn_test_opensl_es).setOnClickListener(this);
        findViewById(R.id.btn_open_media).setOnClickListener(this);
        findViewById(R.id.btn_next_media).setOnClickListener(this);
        findViewById(R.id.btn_pause_audio).setOnClickListener(this);
        findViewById(R.id.btn_resume_play_audio).setOnClickListener(this);
        findViewById(R.id.btn_stop_play_audio).setOnClickListener(this);
        findViewById(R.id.btn_destroy_audio_player).setOnClickListener(this);
        findViewById(R.id.btn_left_channel).setOnClickListener(this);
        findViewById(R.id.btn_right_channel).setOnClickListener(this);
        findViewById(R.id.btn_stero).setOnClickListener(this);

        mPlayUrl = findViewById(R.id.tv_play_url);
        mPlayUrl.setText(mSources[mIndex]);
        mError = findViewById(R.id.tv_error_info);
        mPlayTimeView = findViewById(R.id.tv_play_time);
        mDecibels = findViewById(R.id.tv_decibels);
        mVolume = findViewById(R.id.tv_volume);
        mPitch = findViewById(R.id.tv_pitch);
        mTempo = findViewById(R.id.tv_tempo);

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
                Log.d(TAG, "mPlaySeekBar onStartTrackingTouch");
                isSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Log.d(TAG, "mPlaySeekBar onStopTrackingTouch");
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
                Log.d(TAG, "mVolumeSeekBar onStartTrackingTouch");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Log.d(TAG, "mVolumeSeekBar onStopTrackingTouch");
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
                Log.d(TAG, "mPitchSeekBar onStartTrackingTouch");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Log.d(TAG, "mPitchSeekBar onStopTrackingTouch");
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
                Log.d(TAG, "mTempoSeekBar onStartTrackingTouch");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Log.d(TAG, "mTempoSeekBar onStopTrackingTouch");
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
        Log.d(TAG, "onClick " + view);
        switch (view.getId()) {
            case R.id.btn_base_test_control:
                if (mBaseTestRoot.getVisibility() == View.VISIBLE) {
                    mBaseTestRoot.setVisibility(View.GONE);
                    ((Button) view).setText("显示基本测试");
                } else {
                    mBaseTestRoot.setVisibility(View.VISIBLE);
                    ((Button) view).setText("隐藏基本测试");
                }
                break;
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
            case R.id.btn_open_media:
                openAudio(mSources[mIndex]);
                break;
            case R.id.btn_next_media:
                mIndex++;
                if (mIndex >= mSources.length) {
                    mIndex = 0;
                }
                resetUI();
                openAudio(mSources[mIndex]);
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
        }
    }

    private void openAudio(String url) {
        if (mWePlayer == null) {
            mWePlayer = new WePlayer();
            mWePlayer.setOnPreparedListener(new WePlayer.OnPreparedListener() {
                @Override
                public void onPrepared() {
                    Log.d(TAG, "WePlayer onPrepared");
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
                    mPlaySeekBar.setMax(mDuration);
                    mDurationText = DateTimeUtil.changeRemainTimeToHms(mDuration);
                    startUpdateTime();
                    startUpdateDecibels();
                }
            });
            mWePlayer.setOnPlayLoadingListener(new WePlayer.OnPlayLoadingListener() {
                @Override
                public void onPlayLoading(boolean isLoading) {
                    Log.d(TAG, "WePlayer onPlayLoading: " + isLoading);
                    MainActivity.this.isLoading = isLoading;
                    if (isLoading) {
                        stopUpdateTime();
                        stopUpdateDecibels();
                        showProgressDialog(MainActivity.this);
                    } else {
                        startUpdateTime();
                        startUpdateDecibels();
                        hideProgressDialog();
                    }
                }
            });
            mWePlayer.setOnErrorListener(new WePlayer.OnErrorListener() {
                @Override
                public void onError(int code, String msg) {
                    Log.e(TAG, "WePlayer onError: " + code + "; " + msg);
                    mError.setText("Error " + code);
                }
            });
            mWePlayer.setOnCompletionListener(new WePlayer.OnCompletionListener() {
                @Override
                public void onCompletion() {
                    Log.d(TAG, "WePlayer onCompletion");
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

    private void startUpdateDecibels() {
        mHandler.sendEmptyMessage(MSG_UPDATE_SOUND_DECIBELS);
    }

    private void stopUpdateDecibels() {
        mHandler.removeMessages(MSG_UPDATE_SOUND_DECIBELS);
    }

    private void resetUI() {
        mPlayUrl.setText("");
        mError.setText("");
        mPlayTimeView.setText("00:00:00/" + mDurationText);
        mPlaySeekBar.setProgress(0);
    }

    private void updatePlayTime() {
        if (mWePlayer == null || isLoading) return;

        if (isSeeking) {
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
