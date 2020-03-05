package com.wtz.liveplay.net.error;

public class ApiException extends RuntimeException {

    private int errorCode;

    public ApiException(int code, String msg) {
        super(msg);
        this.errorCode = code;
    }

    public int getErrorCode() {
        return errorCode;
    }

}