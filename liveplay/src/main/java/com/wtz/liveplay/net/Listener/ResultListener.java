package com.wtz.liveplay.net.Listener;

public interface ResultListener<T> {

    void onSuccess(T data);

    void onFailed(int code, String error);

}
