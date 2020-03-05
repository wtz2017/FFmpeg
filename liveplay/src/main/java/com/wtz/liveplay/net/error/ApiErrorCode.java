package com.wtz.liveplay.net.error;

public interface ApiErrorCode {

    /**
     * 未知错误
     */
    int ERROR_UNKNOWN = 0;

    /**
     * 本地网络连接失败
     */
    int ERROR_NET_CONNECT_FAILED = 1;

    /**
     * 服务接口访问失败
     */
    int ERROR_SERVER_ACCESS_FAILED = 2;

    /**
     * 返回数据异常
     */
    int ERROR_DATA_EXCEPTION = 3;

}
