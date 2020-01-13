package com.wtz.ffmpeg.gl_test;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.AttributeSet;

import com.wtz.ffmpeg.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class TextureGLSurfaceView extends GLSurfaceView implements GLSurfaceView.Renderer {

    private static final int BYTES_PER_FLOAT = 4;

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

    // java 层纹理坐标
    private final float[] mTextureCoordinatesData = {
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f

            // 下边是倒置
//            1f,0f,
//            0f,0f,
//            1f, 1f,
//            0f, 1f
    };

    // 每个纹理坐标大小
    private static final int TEXTURE_COORDINATE_DATA_SIZE = 2;

    // Native 层存放纹理坐标缓冲区
    private FloatBuffer mTextureCoordinatesBuffer;

    // 用来传入纹理坐标的句柄
    private int mTextureCoordinateHandle;

    // 纹理内容句柄
    private int mTextureDataHandle;

    // 用来传入纹理内容到片元着色器的句柄
    private int mTextureUniformHandle;

    private int mProgramHandle;

    public TextureGLSurfaceView(Context context) {
        this(context, null);
    }

    public TextureGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setEGLContextClientVersion(2);
        setRenderer(this);

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
        String vertexSource = ShaderUtil.readRawText(getContext(), R.raw.texture_vertex_shader);
        String fragmentSource = ShaderUtil.readRawText(getContext(), R.raw.texture_fragment_shader);
        mProgramHandle = ShaderUtil.createAndLinkProgram(vertexSource, fragmentSource);
        if (mProgramHandle > 0) {
            mVertexCoordinateHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Position");
            mTextureCoordinateHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_TexCoordinate");
            mTextureUniformHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_Texture");
        }

        // 加载纹理
        mTextureDataHandle = loadTexture(getContext(), R.drawable.test_opengl);
    }

    private static int loadTexture(final Context context, final int resourceId) {
        final int[] textureHandle = new int[1];

        // 这个 glGenTextures 方法可以用来同时生成多个 handle，这里我们仅生成一个
        GLES20.glGenTextures(1, textureHandle, 0);

        if (textureHandle[0] != 0) {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            // 默认情况下，Android 会根据设备的分辨率和你放置图片的资源文件目录而预先缩放位图。
            // 我们不希望 Android 根据我们的情况对位图进行缩放，因此我们将 inScaled 设置为 false
            options.inScaled = false;

            // 得到图片资源
            final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resourceId, options);

            // 在 OpenGL 中绑定这个创建的纹理
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);

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

            // 将位图加载到已绑定的纹理中
            // void texImage2D (int target, int level, Bitmap bitmap, int border)
            // 我们想要一个常规的 2D 位图，因此我们传入 GL_TEXTURE_2D 作为第一个参数；
            // 第二个参数用于 MIP 映射级别，我们这里没有使用 MIP-映射，因此我们将传入 0 设置为默认级别；
            // 第四个参数表示边框，由于我们没有使用边框，所以我们传入0；
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

            // 回收位图，因为它的数据已加载到 OpenGL 中
            bitmap.recycle();
        }

        if (textureHandle[0] == 0) {
            throw new RuntimeException("Error: glGenTextures generate texture failed.");
        }
        return textureHandle[0];
    }


    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // 清屏
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);

        // 使用程序对象 mProgramHandle 作为当前渲染状态的一部分
        GLES20.glUseProgram(mProgramHandle);

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
        // 将纹理单元设置为纹理单元 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        // 将纹理绑定到这个单元 0
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureDataHandle);
        // 通过绑定到纹理单元 0，告诉纹理标准采样器在着色器中使用此纹理
        // glUniform 用于更改一个 uniform 变量或数组的值，要更改的 uniform 变量的位置由 location 指定，
        // location 的值应该由 `glGetUniformLocation` 函数返回
        GLES20.glUniform1i(mTextureUniformHandle, 0);

        // 开始渲染图形
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

}
