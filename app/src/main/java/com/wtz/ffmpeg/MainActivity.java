package com.wtz.ffmpeg;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.wtz.ffmpeg.utils.PermissionChecker;
import com.wtz.ffmpeg.utils.PermissionHandler;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, PermissionHandler.PermissionHandleListener {
    private static final String TAG = "MainActivity";

    private PermissionHandler mPermissionHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_base_test).setOnClickListener(this);
        findViewById(R.id.btn_opengl_test).setOnClickListener(this);
        findViewById(R.id.btn_audio_play).setOnClickListener(this);
        findViewById(R.id.btn_audio_edit).setOnClickListener(this);
        findViewById(R.id.btn_video_play).setOnClickListener(this);

        mPermissionHandler = new PermissionHandler(this, this);
        mPermissionHandler.handleCommonPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        // 另外对于android-10需要在Application中设置：android:requestLegacyExternalStorage="true"
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
            case R.id.btn_opengl_test:
                startActivity(new Intent(MainActivity.this, OpenGLTestActivity.class));
                break;
            case R.id.btn_audio_play:
                startActivity(new Intent(MainActivity.this, AudioPlayActivity.class));
                break;
            case R.id.btn_audio_edit:
                startActivity(new Intent(MainActivity.this, AudioEditorActivity.class));
                break;
            case R.id.btn_video_play:
                startActivity(new Intent(MainActivity.this, VideoPlayActivity.class));
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
        if (mPermissionHandler != null) {
            mPermissionHandler.destroy();
            mPermissionHandler = null;
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult requestCode=" + requestCode);
        mPermissionHandler.handleActivityRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult requestCode=" + requestCode + ", resultCode=" + resultCode
                + ", data=" + data);
        mPermissionHandler.handleActivityResult(requestCode);
    }

    @Override
    public void onPermissionResult(String permission, PermissionChecker.PermissionState state) {
        Log.w(TAG, "onPermissionResult " + permission + " state is " + state);
        if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
            if (state == PermissionChecker.PermissionState.USER_NOT_GRANTED) {
                Log.e(TAG, "onPermissionResult " + permission + " state is USER_NOT_GRANTED!");
                finish();
            }
        }
    }

}
