package com.wtz.ffmpegapi.utils;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

public class TextureUtils {

    public static int[] genTexture2D(int num) {
        return genTexture(GLES20.GL_TEXTURE_2D, num);
    }

    public static int[] genTextureOES(int num) {
        return genTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, num);
    }

    private static int[] genTexture(int type, int num) {
        // 创建 Texture 对象并初始化配置
        int[] ids = new int[num];
        GLES20.glGenTextures(num, ids, 0);

        for (int i = 0; i < num; i++) {
            if (ids[i] == 0) {
                throw new RuntimeException("genTexture texture " + i + " failed!");
            }
            GLES20.glBindTexture(type, ids[i]);

            // 在使用纹理的时候，有时候会出现超过纹理边界的问题，
            // GL_TEXTURE_WRAP 系列参数用来设置超出边界时应该怎样处理。
            // GL_REPEAT 犹如字面意思那样会重复，当几何纹理坐标大于1.0的时候，
            // 所取的纹理坐标的值位于纹理坐标减去1.0的位置，例如：纹理坐标是 1.1 的时候，所取的颜色的值是 0.1。
            // 所以 1.0 和 2.0 一样，1.1 和 0.1一样，所以就向字面意思那样会重复。
            GLES20.glTexParameteri(type, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
            GLES20.glTexParameteri(type, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);

            // GL_TEXTURE_MIN_FILTER 告诉 OpenGL 在绘制小于原始大小（以像素为单位）的纹理时要应用哪种类型的过滤，
            // GL_TEXTURE_MAG_FILTER 告诉 OpenGL 在放大纹理超过原始大小时要应用哪种类型的过滤。
            // GL_NEAREST 是最快也是最粗糙的过滤形式，所做的就是在屏幕的每个点选择最近的像素，这可能导致图像伪像和锯齿。
            // GL_LINEAR 使用纹理中坐标最接近的若干个颜色，通过加权平均算法得到需要绘制的像素颜色。
            GLES20.glTexParameteri(type, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(type, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        }

        // 解绑 Texture
        GLES20.glBindTexture(type, 0);
        return ids;
    }

}
