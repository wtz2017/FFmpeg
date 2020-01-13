// 当我们定义每一个单一的顶点，顶点着色器都会被调用一次，
// 它被调用的时候，会在 a_Position 属性里接受当前顶点的位置
attribute vec4 a_position;
void main(){
    // OpenGL 会把 gl_Position 中存储的值作为当前顶点的最终位置
    gl_Position = a_position;
}
