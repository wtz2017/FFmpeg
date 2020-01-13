package com.wtz.ffmpeg;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.wtz.ffmpeg.gl_test.TextureGLSurfaceView;
import com.wtz.ffmpeg.gl_test.TriangleGLSurfaceView;
import com.wtz.ffmpegapi.utils.LogUtils;

public class OpenGLTestActivity extends AppCompatActivity {

    private static final String TAG = "BaseTestActivity";

    private TextView tvOpenGLInfo;
    private FrameLayout flOpenGLContainer1;
    private FrameLayout flOpenGLContainer2;
    private TriangleGLSurfaceView mTriangleGLSurfaceView;
    private TextureGLSurfaceView mTextureGLSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_opengl_test);

        flOpenGLContainer1 = findViewById(R.id.fl_opengl_container1);
        flOpenGLContainer2 = findViewById(R.id.fl_opengl_container2);
        tvOpenGLInfo = findViewById(R.id.tv_opengl_info);

        // 检测系统是否支持 OpenGL ES 2.0
        final ActivityManager activityManager =
                (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        final ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
        tvOpenGLInfo.setText("reqGlEsVersion: " + configurationInfo.reqGlEsVersion);

        final boolean supportsEs2 = configurationInfo.reqGlEsVersion >= 0x20000;
        LogUtils.d(TAG, "supportsEs2=" + supportsEs2);
        if (supportsEs2) {
            mTriangleGLSurfaceView = new TriangleGLSurfaceView(this);
            flOpenGLContainer1.addView(mTriangleGLSurfaceView);

            mTextureGLSurfaceView = new TextureGLSurfaceView(this);
            flOpenGLContainer2.addView(mTextureGLSurfaceView);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mTriangleGLSurfaceView != null) {
            mTriangleGLSurfaceView.onResume();
        }
        if (mTextureGLSurfaceView != null) {
            mTextureGLSurfaceView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mTriangleGLSurfaceView != null) {
            mTriangleGLSurfaceView.onPause();
        }
        if (mTextureGLSurfaceView != null) {
            mTextureGLSurfaceView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}
