package com.wtz.ffmpeg;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.wtz.ffmpegapi.WePlayer;
import com.wtz.ffmpegapi.CppThreadDemo;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "FFmpegActivity";

    private WePlayer wePlayer;

    private CppThreadDemo cppThreadDemo;
    private boolean isProducing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cppThreadDemo = new CppThreadDemo();
        cppThreadDemo.stringToJNI("hello! I'm java!");

        ((TextView) findViewById(R.id.sample_text)).setText(cppThreadDemo.stringFromJNI());
        findViewById(R.id.btn_simple_pthread).setOnClickListener(this);
        findViewById(R.id.btn_start_product_consumer).setOnClickListener(this);
        findViewById(R.id.btn_stop_product_consumer).setOnClickListener(this);
        findViewById(R.id.btn_c_thread_call_java).setOnClickListener(this);
        findViewById(R.id.btn_java_set_byte_array_to_c).setOnClickListener(this);
        findViewById(R.id.btn_c_set_byte_array_to_java).setOnClickListener(this);
        findViewById(R.id.btn_test_ffmpeg).setOnClickListener(this);

        wePlayer = new WePlayer();
    }

    @Override
    public void onClick(View view) {
        Log.d(TAG, "onClick " + view);
        switch (view.getId()) {
            case R.id.btn_simple_pthread:
                cppThreadDemo.testSimpleThread();
                break;
            case R.id.btn_start_product_consumer:
                if (!isProducing) {
                    isProducing = true;
                    cppThreadDemo.startProduceConsumeThread();
                }
                break;
            case R.id.btn_stop_product_consumer:
                if (isProducing) {
                    cppThreadDemo.stopProduceConsumeThread();
                    isProducing = false;
                }
                break;
            case R.id.btn_c_thread_call_java:
                cppThreadDemo.setOnResultListener(new CppThreadDemo.OnResultListener() {
                    @Override
                    public void onResult(int code, String msg) {
                        Log.d(TAG, "OnResultListener code: " + code + "; msg: " + msg);
                    }
                });
                cppThreadDemo.callbackFromC();
                break;
            case R.id.btn_java_set_byte_array_to_c:
                byte[] data = new byte[6];
                for (int i = 0; i < data.length; i++) {
                    data[i] = (byte) i;
                    Log.d(TAG, "before setByteArray data " + i + " = " + data[i]);
                }
                cppThreadDemo.setByteArray(data);
                for (int i = 0; i < data.length; i++) {
                    Log.d(TAG, "after setByteArray data " + i + " = " + data[i]);
                }
                break;
            case R.id.btn_c_set_byte_array_to_java:
                byte[] array = cppThreadDemo.getByteArray();
                for (int i = 0; i < array.length; i++) {
                    Log.d(TAG, "getByteArray data " + i + " = " + array[i]);
                }
                break;
            case R.id.btn_test_ffmpeg:
                wePlayer.setDataSource("http://mpge.5nd.com/2015/2015-11-26/69708/1.mp3");
                wePlayer.setOnPreparedListener(new WePlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared() {
                        Log.d(TAG, "WePlayer onPrepared");
                        wePlayer.start();
                    }
                });
                wePlayer.prepareAsync();
                break;
        }
    }

}
