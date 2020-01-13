package com.wtz.ffmpeg.gl_test;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

import com.wtz.ffmpeg.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class TriangleGLSurfaceView extends GLSurfaceView implements GLSurfaceView.Renderer {

    private final float[] mVertexData = {
//            -1f, 0f,
//            0f, -1f,
//            0f, 1f,
//
//            0f, -1f,
//            1f, 0f,
//            0f, 1f

            -1f, 0f,
            0f, -1f,
            0f, 1f,
            1f, 0f

    };
    private FloatBuffer mVertexBuffer;
    private int mProgramHandle;
    private int mVertexPosition;
    private int mFragmentColor;

    public TriangleGLSurfaceView(Context context) {
        this(context, null);
    }

    public TriangleGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setEGLContextClientVersion(2);
        setRenderer(this);

        mVertexBuffer = ByteBuffer.allocateDirect(mVertexData.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(mVertexData);
        mVertexBuffer.position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        String vertexSource = ShaderUtil.readRawText(getContext(), R.raw.triangle_vertex_shader);
        String fragmentSource = ShaderUtil.readRawText(getContext(), R.raw.triangle_fragment_shader);
        mProgramHandle = ShaderUtil.createAndLinkProgram(vertexSource, fragmentSource);
        if (mProgramHandle > 0) {
            mVertexPosition = GLES20.glGetAttribLocation(mProgramHandle, "a_position");
            mFragmentColor = GLES20.glGetUniformLocation(mProgramHandle, "u_color");
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);

        // 使用程序对象 mProgramHandle 作为当前渲染状态的一部分
        GLES20.glUseProgram(mProgramHandle);

        // glUniform 用于更改一个 uniform 变量或数组的值，要更改的 uniform 变量的位置由 location 指定，
        // location 的值应该由 `glGetUniformLocation` 函数返回，v0,v1,v2,v3 指明在指定的 uniform 变量中要使用的新值
        GLES20.glUniform4f(mFragmentColor, 1f, 1f, 0f, 1f);

        // 指定索引 index 处的通用顶点属性数组的位置和数据格式，以便在渲染时使用
        // index 指定要修改的通用顶点属性的索引。
        // size 指定每个通用顶点属性的组件数，必须为 1、2、3 或 4，初始值为 4。
        // type 指定数组中每个组件的数据类型。 接受符号常量 GL_BYTE，GL_UNSIGNED_BYTE，GL_SHORT，
        // GL_UNSIGNED_SHORT，GL_FIXED 或 GL_FLOAT。 初始值为 GL_FLOAT。
        // normalized 指定在访问定点数据值时是应将其标准化。
        // 如果标准化，那么以整数格式存储的值将被映射到范围[-1,1]（对于有符号值）或[0,1]（对于无符号值）
        // stride 指定连续通用顶点属性之间的字节偏移量。这里一个点两个 float 横纵坐标，所以是 2x4=8 字节
        // pointer 指定指向数组中第一个通用顶点属性的第一个组件的指针。初始值为0。
        GLES20.glVertexAttribPointer(
                mVertexPosition, 2, GLES20.GL_FLOAT, false, 8, mVertexBuffer);
        // 启用 index 指定的通用顶点属性数组
        GLES20.glEnableVertexAttribArray(mVertexPosition);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

}
