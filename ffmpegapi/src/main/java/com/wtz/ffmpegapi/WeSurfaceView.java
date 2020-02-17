package com.wtz.ffmpegapi;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.wtz.ffmpegapi.utils.ShaderUtil;
import com.wtz.ffmpegapi.utils.LogUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class WeSurfaceView extends GLSurfaceView implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "WeSurfaceView";

    private int mVideoWidth;
    private int mVideoHeight;

    private boolean isSurfaceDestroyed;
    private boolean beHardCodec;
    private boolean justClearScreen;
    private static final int DELAY_CLEAR_SCREEN_TIME = 400;

    private static final int BYTES_PER_FLOAT = 4;

    /* ---------- 顶点坐标配置：start ---------- */
    // java 层顶点坐标
    private float[] mVertexCoordinatesData = {
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f
    };

    // 每个顶点坐标大小
    private static final int VERTEX_COORDINATE_DATA_SIZE = 2;

    // Native 层存放顶点坐标缓冲区
    private FloatBuffer mVertexCoordinatesBuffer;

    // 用来传入顶点坐标的句柄
    private int mVertexCoordinateYUVHandle;
    private int mVertexCoordiMediaCodecHandle;
    /* ---------- 顶点坐标配置：end ---------- */

    /* ---------- 纹理坐标配置：start ---------- */
    // java 层纹理坐标
    private float[] mTextureCoordinatesData = {
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f
    };

    // 每个纹理坐标大小
    private static final int TEXTURE_COORDINATE_DATA_SIZE = 2;

    // Native 层存放纹理坐标缓冲区
    private FloatBuffer mTextureCoordinatesBuffer;

    // 用来传入纹理坐标的句柄
    private int mTextureCoordinateYUVHandle;
    private int mTextureCoordiMediaCodecHandle;
    /* ---------- 纹理坐标配置：end ---------- */

    /* ---------- 纹理内容配置 ---------- */
    /* ---------- 软解 YUV 纹理内容配置：start ---------- */
    // 纹理内容：yuv 原始数据
    private ByteBuffer yBuffer;
    private ByteBuffer uBuffer;
    private ByteBuffer vBuffer;

    // 纹理内容句柄
    private int[] mTextureYUVDataIds = new int[3];

    // 用来传入纹理内容到片元着色器的句柄
    private int mTextureUniformYHandle;
    private int mTextureUniformUHandle;
    private int mTextureUniformVHandle;
    /* ---------- 软解 YUV 纹理内容配置：end ---------- */

    /* ---------- 硬解 MediaCodec 纹理内容配置：start ---------- */
    // 纹理内容句柄
    private int[] mTextureMediaCodecDataIds = new int[1];

    // 用来传入纹理内容到片元着色器的句柄
    private int mTextureUnifMediaCodecHandle;

    private SurfaceTexture mMediaCodecSurfaceTexture;
    private Surface mMediaCodecSurface;
    /* ---------- 硬解 MediaCodec 纹理内容配置：end ---------- */

    private int mProgramYUVHandle;
    private int mProgramMediaCodecHandle;

    private Handler mUIHandler = new Handler(Looper.getMainLooper());

    public WeSurfaceView(Context context) {
        this(context, null);
    }

    public WeSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setEGLContextClientVersion(2);
        setRenderer(this);
        // 设置为脏模式：外部调用一次 requestRender() 就渲染一次，否则不重复渲染
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        mVertexCoordinatesBuffer = ByteBuffer
                .allocateDirect(mVertexCoordinatesData.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(mVertexCoordinatesData);
        mVertexCoordinatesBuffer.position(0);
        mVertexCoordinatesData = null;

        mTextureCoordinatesBuffer = ByteBuffer
                .allocateDirect(mTextureCoordinatesData.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(mTextureCoordinatesData);
        mTextureCoordinatesBuffer.position(0);
        mTextureCoordinatesData = null;
    }

    @Override
    protected void onAttachedToWindow() {
        LogUtils.w(TAG, "onAttachedToWindow");
        super.onAttachedToWindow();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        LogUtils.w(TAG, "onSurfaceCreated");
        isSurfaceDestroyed = false;
        // 初始化 shader 工作需要放在 onSurfaceCreated 之后的 UI 线程中
        initYUVProgram();
        initMediaCodecProgram();
    }

    private void initYUVProgram() {
        String vertexSource = ShaderUtil.readRawText(getContext(), R.raw.vertex_shader);
        String fragmentSource = ShaderUtil.readRawText(getContext(), R.raw.yuv_fragment_shader);
        mProgramYUVHandle = ShaderUtil.createAndLinkProgram(vertexSource, fragmentSource);
        if (mProgramYUVHandle <= 0) {
            throw new RuntimeException("initYUVProgram Error: createAndLinkProgram for yuv failed.");
        }

        // 获取顶点着色器和片元着色器中的变量句柄
        mVertexCoordinateYUVHandle = GLES20.glGetAttribLocation(mProgramYUVHandle, "a_Position");
        mTextureCoordinateYUVHandle = GLES20.glGetAttribLocation(mProgramYUVHandle, "a_TexCoordinate");
        mTextureUniformYHandle = GLES20.glGetUniformLocation(mProgramYUVHandle, "u_TextureY");
        mTextureUniformUHandle = GLES20.glGetUniformLocation(mProgramYUVHandle, "u_TextureU");
        mTextureUniformVHandle = GLES20.glGetUniformLocation(mProgramYUVHandle, "u_TextureV");

        // 绑定纹理数据内容
        GLES20.glGenTextures(3, mTextureYUVDataIds, 0);// 创建 3 个纹理
        if (mTextureYUVDataIds[0] == 0) {
            throw new RuntimeException("initYUVProgram Error: glGenTextures generate texture 0 failed.");
        }
        if (mTextureYUVDataIds[1] == 0) {
            throw new RuntimeException("initYUVProgram Error: glGenTextures generate texture 1 failed.");
        }
        if (mTextureYUVDataIds[2] == 0) {
            throw new RuntimeException("initYUVProgram Error: glGenTextures generate texture 2 failed.");
        }
        for (int i = 0; i < 3; i++) {
            // 在 OpenGL 中绑定这个创建的纹理
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureYUVDataIds[i]);

            // 在使用纹理的时候，有时候会出现超过纹理边界的问题，
            // GL_TEXTURE_WRAP 系列参数用来设置超出边界时应该怎样处理。
            // GL_REPEAT 犹如字面意思那样会重复，当几何纹理坐标大于1.0的时候，
            // 所取的纹理坐标的值位于纹理坐标减去1.0的位置，例如：纹理坐标是 1.1 的时候，所取的颜色的值是 0.1。
            // 所以 1.0 和 2.0 一样，1.1 和 0.1一样，所以就向字面意思那样会重复。
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);

            // GL_TEXTURE_MIN_FILTER 告诉 OpenGL 在绘制小于原始大小（以像素为单位）的纹理时要应用哪种类型的过滤，
            // GL_TEXTURE_MAG_FILTER 告诉 OpenGL 在放大纹理超过原始大小时要应用哪种类型的过滤。
            // GL_NEAREST 是最快也是最粗糙的过滤形式，所做的就是在屏幕的每个点选择最近的像素，这可能导致图像伪像和锯齿。
            // GL_LINEAR 使用纹理中坐标最接近的若干个颜色，通过加权平均算法得到需要绘制的像素颜色。
//            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
//            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        }
    }

    private void initMediaCodecProgram() {
        String vertexSource = ShaderUtil.readRawText(getContext(), R.raw.vertex_shader);
        String fragmentSource = ShaderUtil.readRawText(getContext(), R.raw.mediacodec_fragment_shader);
        mProgramMediaCodecHandle = ShaderUtil.createAndLinkProgram(vertexSource, fragmentSource);
        if (mProgramMediaCodecHandle <= 0) {
            throw new RuntimeException("initMediaCodecProgram Error: createAndLinkProgram failed.");
        }

        // 获取顶点着色器和片元着色器中的变量句柄
        mVertexCoordiMediaCodecHandle = GLES20.glGetAttribLocation(mProgramMediaCodecHandle, "a_Position");
        mTextureCoordiMediaCodecHandle = GLES20.glGetAttribLocation(mProgramMediaCodecHandle, "a_TexCoordinate");
        mTextureUnifMediaCodecHandle = GLES20.glGetUniformLocation(mProgramMediaCodecHandle, "u_Texture_MediaCodec");

        // 绑定纹理数据内容
        GLES20.glGenTextures(1, mTextureMediaCodecDataIds, 0);// 创建 1 个纹理
        if (mTextureMediaCodecDataIds[0] == 0) {
            throw new RuntimeException("initMediaCodecProgram Error: glGenTextures generate texture 0 failed.");
        }

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureMediaCodecDataIds[0]);

        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);

        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        // MediaCodec 向 mMediaCodecSurface 发送数据，mMediaCodecSurface 通过 mMediaCodecSurfaceTexture 回调 OnFrameAvailableListener
        mMediaCodecSurfaceTexture = new SurfaceTexture(mTextureMediaCodecDataIds[0]);
        mMediaCodecSurfaceTexture.setOnFrameAvailableListener(this);
        mMediaCodecSurface = new Surface(mMediaCodecSurfaceTexture);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        LogUtils.d(TAG, "onSurfaceChanged: " + width + "x" + height);
        GLES20.glViewport(0, 0, width, height);
    }

    protected void onPlayerPrepared() {
        mUIHandler.removeCallbacks(mClearScreenRunnable);
    }

    protected void setHardCodec(boolean hardCodec) {
        this.beHardCodec = hardCodec;
    }

    protected Surface getMediaCodecSurface() {
        return mMediaCodecSurface;
    }

    protected void setYUVData(int width, int height, byte[] y, byte[] u, byte[] v) {
        mVideoWidth = width;
        mVideoHeight = height;
        yBuffer = ByteBuffer.wrap(y);
        uBuffer = ByteBuffer.wrap(u);
        vBuffer = ByteBuffer.wrap(v);

        requestRender();// 触发 onDrawFrame
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        requestRender();// 触发 onDrawFrame
    }

    /**
     * 在彻底停止数据回调后调用一次清屏
     */
    private void clearScreen(boolean immediately) {
        if (immediately) {
            justClearScreen = true;
            requestRender();// 触发 onDrawFrame
        } else {
            mUIHandler.removeCallbacks(mClearScreenRunnable);
            mUIHandler.postDelayed(mClearScreenRunnable, DELAY_CLEAR_SCREEN_TIME);
        }
    }

    private Runnable mClearScreenRunnable = new Runnable() {
        @Override
        public void run() {
            justClearScreen = true;
            requestRender();// 触发 onDrawFrame
        }
    };

    @Override
    public void onDrawFrame(GL10 gl) {
        // 清屏
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glClearColor(0f, 0f, 0f, 1.0f);

        if (justClearScreen) {
            justClearScreen = false;
            return;
        }

        // 渲染
        if (beHardCodec) {
            renderMediaCodecData();
        } else {
            renderYUV();
        }
    }

    private void renderYUV() {
        if (mVideoWidth <= 0 || mVideoHeight <= 0 || yBuffer == null || uBuffer == null || vBuffer == null) {
            // 播放中可能部分画面数据不满足条件时走到这里
            // 返回函数前再画一次矩形，是为了解决之前已经清屏而这里又不绘制导致闪屏的问题
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            return;
        }

        // 使用程序对象 mProgramYUVHandle 作为当前渲染状态的一部分
        GLES20.glUseProgram(mProgramYUVHandle);

        /* ---------- 设置要绘制的图形区域顶点 ---------- */
        // 指定索引 index 处的通用顶点属性数组的位置和数据格式，以便在渲染时使用
        // index 指定要修改的通用顶点属性的索引。
        // size 指定每个通用顶点属性的组件数，必须为 1、2、3 或 4，初始值为 4。
        // type 指定数组中每个组件的数据类型。 接受符号常量 GL_BYTE，GL_UNSIGNED_BYTE，GL_SHORT，
        // GL_UNSIGNED_SHORT，GL_FIXED 或 GL_FLOAT。 初始值为 GL_FLOAT。
        // normalized 指定在访问定点数据值时是应将其标准化。
        // 如果标准化，那么以整数格式存储的值将被映射到范围[-1,1]（对于有符号值）或[0,1]（对于无符号值）
        // stride 指定连续通用顶点属性之间的字节偏移量。这里一个点两个 float 横纵坐标，所以是 2x4=8 字节
        // pointer 指定指向数组中第一个通用顶点属性的第一个组件的指针。初始值为0。
        GLES20.glVertexAttribPointer(mVertexCoordinateYUVHandle, VERTEX_COORDINATE_DATA_SIZE,
                GLES20.GL_FLOAT, false, 8, mVertexCoordinatesBuffer);
        // 启用 index 指定的通用顶点属性数组
        GLES20.glEnableVertexAttribArray(mVertexCoordinateYUVHandle);

        /* ---------- 设置要绘制的图形区域颜色，这里是纹理 ---------- */
        // 设置纹理坐标
        GLES20.glVertexAttribPointer(mTextureCoordinateYUVHandle, TEXTURE_COORDINATE_DATA_SIZE,
                GLES20.GL_FLOAT, false, 8, mTextureCoordinatesBuffer);
        GLES20.glEnableVertexAttribArray(mTextureCoordinateYUVHandle);

        // 将 3 个纹理单元分别激活，并绑定到 YUV 数据
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureYUVDataIds[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE,
                mVideoWidth, mVideoHeight, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, yBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureYUVDataIds[1]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE,
                mVideoWidth / 2, mVideoHeight / 2, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, uBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureYUVDataIds[2]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE,
                mVideoWidth / 2, mVideoHeight / 2, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, vBuffer);

        // 将 YUV 数据传入到片元着色器 Uniform 变量中
        // glUniform 用于更改 uniform 变量或数组的值，要更改的 uniform 变量的位置由 location 指定，
        // location 的值应该由 `glGetUniformLocation` 函数返回
        GLES20.glUniform1i(mTextureUniformYHandle, 0);// 诉纹理标准采样器在着色器中使用纹理单元 0
        GLES20.glUniform1i(mTextureUniformUHandle, 1);// 诉纹理标准采样器在着色器中使用纹理单元 1
        GLES20.glUniform1i(mTextureUniformVHandle, 2);// 诉纹理标准采样器在着色器中使用纹理单元 2

        // 清除本地 YUV 数据
        yBuffer.clear();
        yBuffer = null;
        uBuffer.clear();
        uBuffer = null;
        vBuffer.clear();
        vBuffer = null;

        // 开始渲染图形
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    private void renderMediaCodecData() {
        if (mMediaCodecSurfaceTexture == null) {
            return;
        }
        // 使用程序对象 mProgramMediaCodecHandle 作为当前渲染状态的一部分
        GLES20.glUseProgram(mProgramMediaCodecHandle);

        // 设置要绘制的图形区域顶点
        GLES20.glVertexAttribPointer(mVertexCoordiMediaCodecHandle, VERTEX_COORDINATE_DATA_SIZE,
                GLES20.GL_FLOAT, false, 8, mVertexCoordinatesBuffer);
        // 启用 index 指定的通用顶点属性数组
        GLES20.glEnableVertexAttribArray(mVertexCoordiMediaCodecHandle);

        // 设置纹理坐标
        GLES20.glVertexAttribPointer(mTextureCoordiMediaCodecHandle, TEXTURE_COORDINATE_DATA_SIZE,
                GLES20.GL_FLOAT, false, 8, mTextureCoordinatesBuffer);
        GLES20.glEnableVertexAttribArray(mTextureCoordiMediaCodecHandle);

        // 激活纹理单元，并绑定到 MediaCodec 数据
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureMediaCodecDataIds[0]);
        // 初始化时通过 new SurfaceTexture(mTextureMediaCodecDataIds[0]) 绑定了 mMediaCodecSurfaceTexture
        // 调用 SurfaceTexture.updateTexImage() 即可更新数据
        // update the texture image to the most recent frame from the image stream
        mMediaCodecSurfaceTexture.updateTexImage();

        // 将 MediaCodec 数据传入到片元着色器 Uniform 变量中
        GLES20.glUniform1i(mTextureUnifMediaCodecHandle, 0);// 纹理标准采样器在着色器中使用纹理单元 0

        // 开始渲染图形
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    protected void onPlayerStopped() {
        clearScreen(false);
    }

    protected void onPlayerReset() {
        clearScreen(false);
    }

    protected void onPlayerReleased() {
        LogUtils.w(TAG, "onPlayerReleased...");
        clearScreen(true);
        mUIHandler.removeCallbacksAndMessages(null);

        // 在 player 真正释放后再释放 Surface 资源，以免造成多线程并发异常
        if (isSurfaceDestroyed) {
            if (mMediaCodecSurface != null) {
                mMediaCodecSurface.release();
                mMediaCodecSurface = null;
                mMediaCodecSurfaceTexture.release();
                mMediaCodecSurfaceTexture = null;
            }
            if (yBuffer != null) {
                yBuffer.clear();
                yBuffer = null;
            }
            if (uBuffer != null) {
                uBuffer.clear();
                uBuffer = null;
            }
            if (vBuffer != null) {
                vBuffer.clear();
                vBuffer = null;
            }
            if (mVertexCoordinatesBuffer != null) {
                mVertexCoordinatesBuffer.clear();
                mVertexCoordinatesBuffer = null;
            }
            if (mTextureCoordinatesBuffer != null) {
                mTextureCoordinatesBuffer.clear();
                mTextureCoordinatesBuffer = null;
            }
            mTextureYUVDataIds = null;
            mTextureMediaCodecDataIds = null;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        LogUtils.w(TAG, "surfaceDestroyed");
        super.surfaceDestroyed(holder);
        isSurfaceDestroyed = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        LogUtils.w(TAG, "onDetachedFromWindow");
        super.onDetachedFromWindow();
    }

}
