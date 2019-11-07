package com.wtz.ffmpegapi;

public class CppThreadDemo {

    static {
        System.loadLibrary("thread_demo");
    }

    public native void testSimpleThread();
    public native void startProduceConsumeThread();
    public native void stopProduceConsumeThread();

}
