package com.wtz.ffmpeg;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_base_test).setOnClickListener(this);
        findViewById(R.id.btn_audio_play).setOnClickListener(this);
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_base_test:
                startActivity(new Intent(MainActivity.this, BaseTestActivity.class));
                break;
            case R.id.btn_audio_play:
                startActivity(new Intent(MainActivity.this, AudioPlayActivity.class));
                break;
        }
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

}
