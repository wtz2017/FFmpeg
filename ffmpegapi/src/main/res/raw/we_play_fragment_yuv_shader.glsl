// 精度 precision 可以选择 lowp、mediump和 highp
// 顶点着色器由于位置的精确度，一般默认为高精度，所以不需要再去怎么修改；
// 而片段着色器则采用中等精度，主要是考虑到性能和兼容性。
precision mediump float;
varying vec2 v_TexCoordinate; // 从顶点着色器插入的纹理坐标
uniform sampler2D u_TextureY;  // 用来传入 Y 纹理内容的句柄
uniform sampler2D u_TextureU;  // 用来传入 U 纹理内容的句柄
uniform sampler2D u_TextureV;  // 用来传入 V 纹理内容的句柄
void main(){
    float y, u, v;
    // 使用 texture2D 得到指定纹理坐标的纹理值
    // 下边使用 texture2D 结果的 r 分量，使用 g 或 b 分量一样
    y = texture2D(u_TextureY, v_TexCoordinate).r;
    u = texture2D(u_TextureU, v_TexCoordinate).r;
    v = texture2D(u_TextureV, v_TexCoordinate).r;

    vec3 rgb;
    // 未量化公式（ Y,U,V 范围是 [0, 255]，公式基于 BT.601-6）：
    // 归一化前， 256 级别公式中实际分量分别是 Y、(U - 128)、(V - 128)
//    rgb.r = y + 1.402 * (v - 0.5);
//    rgb.g = y - 0.3441 * (u - 0.5) - 0.7141 * (v - 0.5);
//    rgb.b = y + 1.772 * (u - 0.5);

    // 根据日志分析发现 YUV 的值是量化的
    // 量化公式（Y 范围是 [16, 235]；U,V 范围是 [16, 240]，公式基于 BT.601-6）：
    // 归一化前， 256 级别公式中实际分量分别是 (Y - 16)、(U - 128)、(V - 128)
    rgb.r = 1.164 * (y - 0.06) + 1.596 * (v - 0.5);
    rgb.g = 1.164 * (y - 0.06) - 0.392 * (u - 0.5) - 0.812 * (v - 0.5);
    rgb.b = 1.164 * (y - 0.06) + 2.016 * (u - 0.5);

    // OpenGL 会使用 gl_FragColor 的值作为当前片段的最终颜色
    gl_FragColor = vec4(rgb, 1);
}
