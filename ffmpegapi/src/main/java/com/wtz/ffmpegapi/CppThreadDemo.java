package com.wtz.ffmpegapi;

public class CppThreadDemo {

    static {
        System.loadLibrary("thread_demo");
    }

    public native void testSimpleThread();

    public native void startProduceConsumeThread();

    public native void stopProduceConsumeThread();

    public interface OnResultListener {
        void onResult(int code, String msg);
    }

    private OnResultListener onResultListener;

    public void setOnResultListener(OnResultListener onResultListener) {
        this.onResultListener = onResultListener;
    }

    /**
     * C++ 层调用
     */
    public void onResult(int code, String msg) {
        if (onResultListener != null) {
            onResultListener.onResult(code, msg);
        }
    }

    public native void callbackFromC();

}
