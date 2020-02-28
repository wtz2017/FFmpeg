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

public class WeVideoView extends GLSurfaceView implements GLSurfaceView.Renderer,
        SurfaceTexture.OnFrameAvailableListener, WePlayer.OnYUVDataListener {
    private static final String TAG = "WeVideoView";

    private WePlayer mWePlayer;
    private OnSurfaceCreatedListener mOnSurfaceCreatedListener;
    private OnSurfaceDestroyedListener mOnSurfaceDestroyedListener;
    private WePlayer.OnPreparedListener mOnPreparedListener;
    private WePlayer.OnPlayLoadingListener mOnPlayLoadingListener;
    private WePlayer.OnSeekCompleteListener mOnSeekCompleteListener;
    private WePlayer.OnErrorListener mOnErrorListener;
    private WePlayer.OnCompletionListener mOnCompletionListener;

    private String mDataSource;
    private int mVideoWidth;
    private int mVideoHeight;
    private float mPauseVolume;
    private int mDestroyedPosition;

    private boolean beHardCodec;
    private boolean drawOneFrameThenPause = false;
    private boolean canRenderYUV;
    private boolean canRenderMedia;
    private boolean isSurfaceDestroyed;
    private boolean isGLReleased;
    private boolean justClearScreen;
    private static final int DELAY_CLEAR_SCREEN_TIME = 400;

    private static final int BYTES_PER_FLOAT = 4;

    /* ---------- 顶点坐标配置：start ---------- */
    // java 层顶点坐标
    private float[] mVertexCoordinatesData;

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
    private float[] mTextureCoordinatesData;

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
    private static final int TEXTURE_YUV_DATA_ID_NUM = 3;
    private int[] mTextureYUVDataIds;

    // 用来传入纹理内容到片元着色器的句柄
    private int mTextureUniformYHandle;
    private int mTextureUniformUHandle;
    private int mTextureUniformVHandle;
    /* ---------- 软解 YUV 纹理内容配置：end ---------- */

    /* ---------- 硬解 MediaCodec 纹理内容配置：start ---------- */
    // 纹理内容句柄
    private static final int TEXTURE_MEDIACODEC_DATA_ID_NUM = 1;
    private int[] mTextureMediaCodecDataIds;

    // 用来传入纹理内容到片元着色器的句柄
    private int mTextureUnifMediaCodecHandle;

    private SurfaceTexture mMediaCodecSurfaceTexture;
    private Surface mMediaCodecSurface;
    /* ---------- 硬解 MediaCodec 纹理内容配置：end ---------- */

    private int mVertexShaderYUVHandle;
    private int mFragmentShaderYUVHandle;
    private int mProgramYUVHandle;

    private int mVertexShaderMediaCodecHandle;
    private int mFragmentShaderMediaCodecHandle;
    private int mProgramMediaCodecHandle;

    private Handler mUIHandler = new Handler(Looper.getMainLooper());

    public interface OnSurfaceCreatedListener {
        void onSurfaceCreated();
    }

    public interface OnSurfaceDestroyedListener {
        void onSurfaceDestroyed(String lastDataSource, int lastPosition);
    }

    public void setOnSurfaceCreatedListener(OnSurfaceCreatedListener listener) {
        this.mOnSurfaceCreatedListener = listener;
    }

    public void setOnSurfaceDestroyedListener(OnSurfaceDestroyedListener listener) {
        this.mOnSurfaceDestroyedListener = listener;
    }

    public void setOnPreparedListener(WePlayer.OnPreparedListener listener) {
        this.mOnPreparedListener = listener;
    }

    public void setOnPlayLoadingListener(WePlayer.OnPlayLoadingListener listener) {
        this.mOnPlayLoadingListener = listener;
    }

    public void setOnSeekCompleteListener(WePlayer.OnSeekCompleteListener listener) {
        this.mOnSeekCompleteListener = listener;
    }

    public void setOnErrorListener(WePlayer.OnErrorListener listener) {
        this.mOnErrorListener = listener;
    }

    public void setOnCompletionListener(WePlayer.OnCompletionListener listener) {
        this.mOnCompletionListener = listener;
    }

    public WeVideoView(Context context) {
        this(context, null);
    }

    public WeVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setEGLContextClientVersion(2);
        setRenderer(this);// 此条语句会创建启动 GLThread
        isGLReleased = false;
        // 设置为脏模式：外部调用一次 requestRender() 就渲染一次，否则不重复渲染
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public void onActivityStart() {
        LogUtils.w(TAG, "onActivityStart");
        super.onResume();
    }

    @Override
    public void onResume() {
        // do nothing 防止外层应用调用 super.onResume();
    }

    /**
     * 此回调在主线程
     */
    @Override
    protected void onAttachedToWindow() {
        LogUtils.w(TAG, "onAttachedToWindow");
        super.onAttachedToWindow();
    }

    /**
     * 此回调在主线程
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        LogUtils.w(TAG, "surfaceCreated");
        super.surfaceCreated(holder);
    }

    /**
     * 此回调在 GLThread
     * <p>
     * onSurfaceCreated 并不一定与 surfaceCreated 及 surfaceDestroyed 保持一样的调用次数
     * onSurfaceCreated will be called when the rendering thread
     * starts and whenever the EGL context is lost. The EGL context will typically
     * be lost when the Android device awakes after going to sleep.
     */
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        LogUtils.w(TAG, "onSurfaceCreated isSurfaceDestroyed=" + isSurfaceDestroyed);
        isGLReleased = false;
        isSurfaceDestroyed = false;
        // 初始化 shader 工作需要放在 onSurfaceCreated 之后的 GLThread 线程中
        // onDrawFrame() onSurfaceChanged() onSurfaceCreated() 都是在 GLThread 线程中
        initCoordinatesData();
        initYUVProgram();
        initMediaCodecProgram();
        initPlayer();
    }

    private void initCoordinatesData() {
        mVertexCoordinatesData = new float[]{
                -1f, -1f,
                1f, -1f,
                -1f, 1f,
                1f, 1f
        };
        mVertexCoordinatesBuffer = ByteBuffer
                .allocateDirect(mVertexCoordinatesData.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(mVertexCoordinatesData);
        mVertexCoordinatesBuffer.position(0);
        mVertexCoordinatesData = null;

        mTextureCoordinatesData = new float[]{
                0f, 1f,
                1f, 1f,
                0f, 0f,
                1f, 0f
        };
        mTextureCoordinatesBuffer = ByteBuffer
                .allocateDirect(mTextureCoordinatesData.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(mTextureCoordinatesData);
        mTextureCoordinatesBuffer.position(0);
        mTextureCoordinatesData = null;
    }

    private void initYUVProgram() {
        String vertexSource = ShaderUtil.readRawText(getContext(), R.raw.vertex_shader);
        String fragmentSource = ShaderUtil.readRawText(getContext(), R.raw.yuv_fragment_shader);
        int[] shaderIDs = ShaderUtil.createAndLinkProgram(vertexSource, fragmentSource);
        mVertexShaderYUVHandle = shaderIDs[0];
        mFragmentShaderYUVHandle = shaderIDs[1];
        mProgramYUVHandle = shaderIDs[2];
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
        mTextureYUVDataIds = new int[TEXTURE_YUV_DATA_ID_NUM];
        GLES20.glGenTextures(TEXTURE_YUV_DATA_ID_NUM, mTextureYUVDataIds, 0);// 创建 3 个纹理
        if (mTextureYUVDataIds[0] == 0) {
            throw new RuntimeException("initYUVProgram Error: glGenTextures generate texture 0 failed.");
        }
        if (mTextureYUVDataIds[1] == 0) {
            throw new RuntimeException("initYUVProgram Error: glGenTextures generate texture 1 failed.");
        }
        if (mTextureYUVDataIds[2] == 0) {
            throw new RuntimeException("initYUVProgram Error: glGenTextures generate texture 2 failed.");
        }
        for (int i = 0; i < TEXTURE_YUV_DATA_ID_NUM; i++) {
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
        int[] shaderIDs = ShaderUtil.createAndLinkProgram(vertexSource, fragmentSource);
        mVertexShaderMediaCodecHandle = shaderIDs[0];
        mFragmentShaderMediaCodecHandle = shaderIDs[1];
        mProgramMediaCodecHandle = shaderIDs[2];
        if (mProgramMediaCodecHandle <= 0) {
            throw new RuntimeException("initMediaCodecProgram Error: createAndLinkProgram failed.");
        }

        // 获取顶点着色器和片元着色器中的变量句柄
        mVertexCoordiMediaCodecHandle = GLES20.glGetAttribLocation(mProgramMediaCodecHandle, "a_Position");
        mTextureCoordiMediaCodecHandle = GLES20.glGetAttribLocation(mProgramMediaCodecHandle, "a_TexCoordinate");
        mTextureUnifMediaCodecHandle = GLES20.glGetUniformLocation(mProgramMediaCodecHandle, "u_Texture_MediaCodec");

        // 绑定纹理数据内容
        mTextureMediaCodecDataIds = new int[1];
        GLES20.glGenTextures(TEXTURE_MEDIACODEC_DATA_ID_NUM, mTextureMediaCodecDataIds, 0);// 创建 1 个纹理
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

    private void initPlayer() {
        mWePlayer = new WePlayer(false);
        mWePlayer.setSurface(mMediaCodecSurface);
        mWePlayer.setOnPreparedListener(new WePlayer.OnPreparedListener() {
            @Override
            public void onPrepared() {
                LogUtils.d(TAG, "WePlayer onPrepared");
                beHardCodec = mWePlayer.isVideoHardCodec();
                mVideoWidth = mWePlayer.getVideoWidthOnPrepared();
                mVideoHeight = mWePlayer.getVideoHeightOnPrepared();

                if (mVideoWidth > 0 && mVideoHeight > 0) {
                    // 新的资源准备好时清除之前可能的延时清屏，尽量避免切换闪屏
                    mUIHandler.removeCallbacks(mClearScreenRunnable);
                } else {
                    // 是纯音乐，需要清屏
                }

                if (mOnPreparedListener != null) {
                    mOnPreparedListener.onPrepared();
                }
            }
        });
        mWePlayer.setOnYUVDataListener(this);
        mWePlayer.setOnPlayLoadingListener(new WePlayer.OnPlayLoadingListener() {
            @Override
            public void onPlayLoading(boolean isLoading) {
                if (mOnPlayLoadingListener != null) {
                    mOnPlayLoadingListener.onPlayLoading(isLoading);
                }
            }
        });
        mWePlayer.setOnSeekCompleteListener(new WePlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete() {
                if (mOnSeekCompleteListener != null) {
                    mOnSeekCompleteListener.onSeekComplete();
                }
            }
        });
        mWePlayer.setOnErrorListener(new WePlayer.OnErrorListener() {
            @Override
            public void onError(int code, String msg) {
                LogUtils.e(TAG, "WePlayer onError: " + code + "; " + msg);
                if (mOnErrorListener != null) {
                    mOnErrorListener.onError(code, msg);
                }
            }
        });
        mWePlayer.setOnCompletionListener(new WePlayer.OnCompletionListener() {
            @Override
            public void onCompletion() {
                LogUtils.d(TAG, "WePlayer onCompletion");
                if (mOnCompletionListener != null) {
                    mOnCompletionListener.onCompletion();
                }
            }
        });
        mWePlayer.setOnStoppedListener(new WePlayer.OnStoppedListener() {
            @Override
            public void onStopped() {
                LogUtils.w(TAG, "WePlayer onStopped");
                clearScreen(false);
                flushTexImage();
                canRenderMedia = false;
                canRenderYUV = false;
            }
        });
        mWePlayer.setOnResetListener(new WePlayer.OnResetListener() {
            @Override
            public void onReset() {
                LogUtils.d(TAG, "WePlayer onReset");
                clearScreen(false);
                flushTexImage();
                canRenderMedia = false;
                canRenderYUV = false;
            }
        });
        mWePlayer.setOnReleasedListener(new WePlayer.OnReleasedListener() {
            @Override
            public void onReleased() {
                LogUtils.w(TAG, "WePlayer onReleased");
                clearScreen(true);
                releaseMediaCodecSurface();
                mUIHandler.removeCallbacksAndMessages(null);
                mWePlayer = null;
            }
        });
        if (mOnSurfaceCreatedListener != null) {
            mOnSurfaceCreatedListener.onSurfaceCreated();
        }
    }

    private void flushTexImage() {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                if (!isSurfaceDestroyed && mMediaCodecSurfaceTexture != null) {
                    // 解决切换媒体资源时不会回调 onFrameAvailable 的问题
                    mMediaCodecSurfaceTexture.updateTexImage();
                }
            }
        });
    }

    /**
     * 此回调在 GLThread
     */
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        LogUtils.d(TAG, "onSurfaceChanged: " + width + "x" + height);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onYUVData(int width, int height, byte[] y, byte[] u, byte[] v) {
        mVideoWidth = width;
        mVideoHeight = height;
        yBuffer = ByteBuffer.wrap(y);
        uBuffer = ByteBuffer.wrap(u);
        vBuffer = ByteBuffer.wrap(v);

        canRenderYUV = true;
        requestRender();// 触发 onDrawFrame
        pauseWhenDrawOneFrame();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        canRenderMedia = true;
        requestRender();// 触发 onDrawFrame
        pauseWhenDrawOneFrame();
    }

    /**
     * 清屏为黑屏
     *
     * @param immediately true 表示立即清屏，false 表示延时清屏
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

    /**
     * 此回调在 GLThread
     */
    @Override
    public void onDrawFrame(GL10 gl) {
        if (isSurfaceDestroyed) {
            return;
        }

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
        if (!canRenderYUV) {
            LogUtils.e(TAG, "CAN'T renderYUV");
            return;
        }
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
        if (!canRenderMedia) {
            LogUtils.e(TAG, "CAN'T renderMedia");
            return;
        }
        if (mMediaCodecSurfaceTexture == null) {
            LogUtils.e(TAG, "mMediaCodecSurfaceTexture is null");
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

    /**
     * 此回调在主线程
     * 必须在此方法 return 之前，保证释放 GLES20 动作在 GLThread 中完成
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        LogUtils.w(TAG, "surfaceDestroyed");
        releaseOnSurfaceDestroyed();

        // queueEvent: Queue a runnable to be run on the GL rendering thread.
        // 实际执行动作必须在 super.surfaceDestroyed(holder) 和本方法 return 之前有效
        queueEvent(mReleaseGLES20Runnable);
        // TODO 在 android.opengl.GLSurfaceView.Renderer.onSurfaceCreated 中注释写到：
        // * onSurfaceCreated will be called when the rendering thread
        // * starts and whenever the EGL context is lost. The EGL context will typically
        // * be lost when the Android device awakes after going to sleep.
        // * Note that when the EGL context is lost, all OpenGL resources associated
        // * with that context will be automatically deleted. You do not need to call
        // * the corresponding "glDelete" methods such as glDeleteTextures to
        // * manually delete these lost resources.

        while (!isGLReleased) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        LogUtils.w(TAG, "to call super.surfaceDestroyed");
        super.surfaceDestroyed(holder);
    }

    @Override
    public void onPause() {
        // do nothing 防止外层应用调用 super.onPause()
    }

    public void onActivityStop() {
        LogUtils.w(TAG, "onActivityStop isSurfaceDestroyed=" + isSurfaceDestroyed);
        super.onPause();
        // onSurfaceCreated 并不一定与 surfaceCreated 及 surfaceDestroyed 保持一样的调用次数
        // * onSurfaceCreated will be called when the rendering thread
        // * starts and whenever the EGL context is lost. The EGL context will typically
        // * be lost when the Android device awakes after going to sleep.
        releaseOnSurfaceDestroyed();// 在 Activity stop 不可见时主动释放资源
    }

    private void releaseOnSurfaceDestroyed() {
        if (!isSurfaceDestroyed) {
            LogUtils.w(TAG, "releaseOnSurfaceDestroyed...");
            isSurfaceDestroyed = true;

            mDestroyedPosition = getCurrentPosition() - 1000;
            if (mDestroyedPosition < 0) {
                mDestroyedPosition = 0;
            }
            mWePlayer.release();

            if (mOnSurfaceDestroyedListener != null) {
                mOnSurfaceDestroyedListener.onSurfaceDestroyed(mDataSource, mDestroyedPosition);
            }
        }
    }

    /**
     * 绘制动作在 GLThread 中
     * 把释放绘制所用的资源动作也放入 GLThread 中，就可以避免并必导致异常
     */
    private Runnable mReleaseGLES20Runnable = new Runnable() {
        @Override
        public void run() {
            LogUtils.w(TAG, "queueEvent run release GLES20...");
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

            if (mProgramMediaCodecHandle > 0) {
                GLES20.glDetachShader(mProgramMediaCodecHandle, mVertexShaderMediaCodecHandle);
                GLES20.glDeleteShader(mVertexShaderMediaCodecHandle);
                mVertexShaderMediaCodecHandle = 0;

                GLES20.glDetachShader(mProgramMediaCodecHandle, mFragmentShaderMediaCodecHandle);
                GLES20.glDeleteShader(mFragmentShaderMediaCodecHandle);
                mFragmentShaderMediaCodecHandle = 0;

                GLES20.glDeleteProgram(mProgramMediaCodecHandle);
                mProgramMediaCodecHandle = 0;
            }

            if (mProgramYUVHandle > 0) {
                GLES20.glDetachShader(mProgramYUVHandle, mVertexShaderYUVHandle);
                GLES20.glDeleteShader(mVertexShaderYUVHandle);
                mVertexShaderYUVHandle = 0;

                GLES20.glDetachShader(mProgramYUVHandle, mFragmentShaderYUVHandle);
                GLES20.glDeleteShader(mFragmentShaderYUVHandle);
                mFragmentShaderYUVHandle = 0;

                GLES20.glDeleteProgram(mProgramYUVHandle);
                mProgramYUVHandle = 0;
            }

            if (mTextureYUVDataIds != null) {
                GLES20.glDeleteTextures(TEXTURE_YUV_DATA_ID_NUM, mTextureYUVDataIds, 0);
                mTextureYUVDataIds = null;
            }
            if (mTextureMediaCodecDataIds != null) {
                GLES20.glDeleteTextures(TEXTURE_MEDIACODEC_DATA_ID_NUM, mTextureMediaCodecDataIds, 0);
                mTextureMediaCodecDataIds = null;
            }

            isGLReleased = true;
        }
    };

    /**
     * 1. 在 SurfaceDestroyed 后才有必要释放 MediaCodecSurface
     * 2. 在 PlayerReleased 后释放 MediaCodecSurface 可避免多线程并发异常
     */
    private void releaseMediaCodecSurface() {
        LogUtils.w(TAG, "releaseMediaCodecSurface");
        if (mMediaCodecSurfaceTexture == null) return;
        mMediaCodecSurface.release();
        mMediaCodecSurface = null;
        mMediaCodecSurfaceTexture.release();
        mMediaCodecSurfaceTexture = null;
    }

    /**
     * 此回调在主线程
     */
    @Override
    protected void onDetachedFromWindow() {
        LogUtils.w(TAG, "onDetachedFromWindow");
        super.onDetachedFromWindow();
        resetData();
    }

    private void resetData() {
        mDataSource = null;
        mVideoWidth = 0;
        mVideoHeight = 0;
        mDestroyedPosition = 0;
        beHardCodec = false;
    }

    /* ============================== Player Function ============================== */
    public void setDataSource(String dataSource) {
        mDataSource = dataSource;
        if (mWePlayer != null) {
            mWePlayer.setDataSource(dataSource);
        }
    }

    public void prepareAsync() {
        if (mWePlayer != null) {
            mWePlayer.prepareAsync();
        }
    }

    public void start() {
        if (mWePlayer != null) {
            mWePlayer.start();
        }
    }

    public void pause() {
        if (mWePlayer != null) {
            mWePlayer.pause();
        }
    }

    public void drawOneFrameThenPause() {
        LogUtils.w(TAG, "drawOneFrameThenPause...");
        drawOneFrameThenPause = true;
        if (mWePlayer != null) {
            mPauseVolume = mWePlayer.getVolume();
            mWePlayer.setVolume(0);
        }
        start();
    }

    private void pauseWhenDrawOneFrame() {
        if (drawOneFrameThenPause) {
            LogUtils.w(TAG, "pauseWhenDrawOneFrame...");
            drawOneFrameThenPause = false;
            pause();
            setVolume(mPauseVolume);
        }
    }

    public void reset() {
        resetData();
        if (mWePlayer != null) {
            mWePlayer.reset();
        }
    }

    public boolean isPlaying() {
        return mWePlayer != null ? mWePlayer.isPlaying() : false;
    }

    /**
     * Gets the duration of the file.
     *
     * @return the duration in milliseconds
     */
    public int getDuration() {
        return mWePlayer != null ? mWePlayer.getDuration() : 0;
    }

    /**
     * Gets the current playback position.
     *
     * @return the current position in milliseconds
     */
    public int getCurrentPosition() {
        return mWePlayer != null ? mWePlayer.getCurrentPosition() : 0;
    }

    /**
     * Seeks to specified time position
     *
     * @param msec the offset in milliseconds from the start to seek to
     */
    public void seekTo(int msec) {
        if (mWePlayer != null) {
            mWePlayer.seekTo(msec);
        }
    }

    public int getVideoWidthOnPrepared() {
        return mWePlayer != null ? mWePlayer.getVideoWidthOnPrepared() : 0;
    }

    public int getVideoHeightOnPrepared() {
        return mWePlayer != null ? mWePlayer.getVideoHeightOnPrepared() : 0;
    }

    /**
     * 设置音量
     *
     * @param percent 范围是：0 ~ 1.0
     */
    public void setVolume(float percent) {
        if (mWePlayer != null) {
            mWePlayer.setVolume(percent);
        }
    }

    /**
     * 获取当前音量百分比
     *
     * @return 范围是：0 ~ 1.0
     */
    public float getVolume() {
        if (mWePlayer != null) {
            if (drawOneFrameThenPause) {
                return mPauseVolume;
            } else {
                return mWePlayer.getVolume();
            }
        }

        return 0;
    }

    public boolean isVideoHardCodec() {
        return mWePlayer != null ? mWePlayer.isVideoHardCodec() : beHardCodec;
    }

    public String getVideoCodecType() {
        return mWePlayer != null ? mWePlayer.getVideoCodecType() : "";
    }

}
