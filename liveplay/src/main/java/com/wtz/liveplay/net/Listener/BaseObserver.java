package com.wtz.liveplay.net.Listener;

import android.util.Log;

import com.wtz.liveplay.net.data.BaseData;
import com.wtz.liveplay.net.error.ApiErrorCode;
import com.wtz.liveplay.net.error.ApiException;

import java.io.IOException;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.HttpException;

public class BaseObserver<T> implements Observer<T> {

    private String tag;
    private ResultListener<T> listener;
    private Disposable disposable;

    public BaseObserver(String tag, ResultListener<T> listener) {
        this.tag = tag;
        this.listener = listener;
    }

    public void subscribe(Observable<T> o) {
        o.subscribeOn(Schedulers.io())
                .unsubscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this);
    }

    @Override
    public void onSubscribe(Disposable d) {
        Log.d(tag, "onSubscribe...");
        this.disposable = d;
    }

    @Override
    public void onNext(T data) {
        Log.d(tag, "onNext: " + data);
        BaseData ret = (BaseData) data;
        if (listener != null) {
            if (ret.isDataOK()) {
                listener.onSuccess(data);
            } else {
                listener.onFailed(ApiErrorCode.ERROR_DATA_EXCEPTION, "server data exception");
            }
        }
    }

    @Override
    public void onError(Throwable e) {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            disposable = null;
        }

        int errorCode = ApiErrorCode.ERROR_UNKNOWN;
        if (e instanceof IOException) {
            Log.e(tag, "连接失败!");
            errorCode = ApiErrorCode.ERROR_NET_CONNECT_FAILED;
        } else if (e instanceof HttpException) {
            Log.e(tag, "服务暂不可用!");
            errorCode = ApiErrorCode.ERROR_SERVER_ACCESS_FAILED;
        } else if (e instanceof ApiException) {
            errorCode = ((ApiException) e).getErrorCode();
            Log.e(tag, "Api 异常：" + errorCode + ":" + e.getMessage());
        } else {
            Log.e(tag, "未知错误!");
        }

        e.printStackTrace();

        if (listener != null) {
            String msg = e.getMessage();
            if (msg == null) {
                msg = e.toString();
            }
            listener.onFailed(errorCode, msg);
        }
    }

    @Override
    public void onComplete() {
        Log.d(tag, "onComplete!");
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            disposable = null;
        }
    }

}
