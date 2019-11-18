package com.wtz.ffmpeg;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.wtz.ffmpegapi.API;
import com.wtz.ffmpegapi.CppThreadDemo;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "FFmpegActivity";

    private API api;

    private CppThreadDemo cppThreadDemo;
    private boolean isProducing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        api = new API();
        cppThreadDemo = new CppThreadDemo();

        TextView tv = findViewById(R.id.sample_text);
        tv.setText(api.stringFromJNI());

        findViewById(R.id.btn_simple_pthread).setOnClickListener(this);
        findViewById(R.id.btn_start_product_consumer).setOnClickListener(this);
        findViewById(R.id.btn_stop_product_consumer).setOnClickListener(this);
        findViewById(R.id.btn_c_thread_call_java).setOnClickListener(this);
        findViewById(R.id.btn_java_set_byte_array_to_c).setOnClickListener(this);
        findViewById(R.id.btn_c_set_byte_array_to_java).setOnClickListener(this);

        api.testFFmpeg();
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
        }
    }

}
