package com.wtz.ffmpegapi.opengl;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

import com.wtz.ffmpegapi.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class WeSurfaceView extends GLSurfaceView implements GLSurfaceView.Renderer {

    private static final int BYTES_PER_FLOAT = 4;

    /* ---------- 顶点坐标配置：start ---------- */
    // java 层顶点坐标
    private final float[] mVertexCoordinatesData = {
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
    private int mVertexCoordinateHandle;
    /* ---------- 顶点坐标配置：end ---------- */

    /* ---------- 纹理坐标配置：start ---------- */
    // java 层纹理坐标
    private final float[] mTextureCoordinatesData = {
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
    private int mTextureCoordinateHandle;
    /* ---------- 纹理坐标配置：end ---------- */

    /* ---------- 纹理内容配置：start ---------- */
    // 纹理内容：yuv 原始数据
    private int yuvWidth;
    private int yuvHeight;
    private ByteBuffer yBuffer;
    private ByteBuffer uBuffer;
    private ByteBuffer vBuffer;

    // 纹理内容句柄
    private int[] mTextureYUVDataIds = new int[3];

    // 用来传入纹理内容到片元着色器的句柄
    private int mTextureUniformYHandle;
    private int mTextureUniformUHandle;
    private int mTextureUniformVHandle;
    /* ---------- 纹理内容配置：end ---------- */

    private int mProgramYUVHandle;

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

        mTextureCoordinatesBuffer = ByteBuffer
                .allocateDirect(mTextureCoordinatesData.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(mTextureCoordinatesData);
        mTextureCoordinatesBuffer.position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        initYUVProgram();
    }

    private void initYUVProgram() {
        String vertexSource = ShaderUtil.readRawText(getContext(), R.raw.yuv_vertex_shader);
        String fragmentSource = ShaderUtil.readRawText(getContext(), R.raw.yuv_fragment_shader);
        mProgramYUVHandle = ShaderUtil.createAndLinkProgram(vertexSource, fragmentSource);
        if (mProgramYUVHandle <= 0) {
            throw new RuntimeException("Error: createAndLinkProgram for yuv failed.");
        }

        // 获取顶点着色器和片元着色器中的变量句柄
        mVertexCoordinateHandle = GLES20.glGetAttribLocation(mProgramYUVHandle, "a_Position");
        mTextureCoordinateHandle = GLES20.glGetAttribLocation(mProgramYUVHandle, "a_TexCoordinate");
        mTextureUniformYHandle = GLES20.glGetUniformLocation(mProgramYUVHandle, "u_TextureY");
        mTextureUniformUHandle = GLES20.glGetUniformLocation(mProgramYUVHandle, "u_TextureU");
        mTextureUniformVHandle = GLES20.glGetUniformLocation(mProgramYUVHandle, "u_TextureV");

        // 绑定纹理数据内容
        GLES20.glGenTextures(3, mTextureYUVDataIds, 0);// 创建 3 个纹理
        if (mTextureYUVDataIds[0] == 0) {
            throw new RuntimeException("Error: glGenTextures generate texture 0 failed.");
        }
        if (mTextureYUVDataIds[1] == 0) {
            throw new RuntimeException("Error: glGenTextures generate texture 1 failed.");
        }
        if (mTextureYUVDataIds[2] == 0) {
            throw new RuntimeException("Error: glGenTextures generate texture 2 failed.");
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

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // 清屏
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glClearColor(0f, 0f, 0f, 1.0f);

        // 渲染
        renderYUV();
    }

    public void setYUVData(int width, int height, byte[] y, byte[] u, byte[] v) {
        yuvWidth = width;
        yuvHeight = height;
        yBuffer = ByteBuffer.wrap(y);
        uBuffer = ByteBuffer.wrap(u);
        vBuffer = ByteBuffer.wrap(v);

        requestRender();// 触发 onDrawFrame
    }

    private void renderYUV() {
        if (yuvWidth <= 0 || yuvHeight <= 0 || yBuffer == null || uBuffer == null || vBuffer == null) {
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
        GLES20.glVertexAttribPointer(mVertexCoordinateHandle, VERTEX_COORDINATE_DATA_SIZE,
                GLES20.GL_FLOAT, false, 8, mVertexCoordinatesBuffer);
        // 启用 index 指定的通用顶点属性数组
        GLES20.glEnableVertexAttribArray(mVertexCoordinateHandle);

        /* ---------- 设置要绘制的图形区域颜色，这里是纹理 ---------- */
        // 设置纹理坐标
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle, TEXTURE_COORDINATE_DATA_SIZE,
                GLES20.GL_FLOAT, false, 8, mTextureCoordinatesBuffer);
        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);

        // 将 3 个纹理单元分别激活，并绑定到 YUV 数据
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureYUVDataIds[0]);
        // TODO 函数解释？
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE,
                yuvWidth, yuvHeight, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, yBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureYUVDataIds[1]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE,
                yuvWidth / 2, yuvHeight / 2, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, uBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureYUVDataIds[2]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE,
                yuvWidth / 2, yuvHeight / 2, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, vBuffer);

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

}
