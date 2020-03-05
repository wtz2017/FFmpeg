package com.wtz.liveplay.net;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wtz.liveplay.net.converter.MyGsonConverterFactory;
import com.wtz.liveplay.net.converter.MyStringConverterFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

public class HttpMethod {

    private static final String BASE_URL = "http://pacc.radio.cn/";
    private static final boolean IGNORE_SSL_VERIFY = false;

    private static HttpMethod mInstance;
    private Retrofit mRetrofit;
    private Gson mGson;

    public static HttpMethod getInstance() {
        if (mInstance == null) {
            synchronized (HttpMethod.class) {
                if (mInstance == null) {
                    mInstance = new HttpMethod();
                }
            }
        }
        return mInstance;
    }

    private HttpMethod() {
        mRetrofit = new Retrofit.Builder()
                .client(createHttpClient())
                // baseUrl 中的路径(path)必须以 / 结束
                .baseUrl(BASE_URL)
                // 这里先添加了一个 StringConverter，否则后边获取 string 结果时会报错：IllegalStateException: Expected a string but was BEGIN_OBJECT
                // 如果有多个 ConverterFactory 都支持同一种类型，那么就是只有第一个才会被使用；
                // 如果有 GsonConverter，那么 StringConverter 一定要放在它前面，因为 GsonConverterFactory是不判断是否支持的
                .addConverterFactory(MyStringConverterFactory.create())
                .addConverterFactory(MyGsonConverterFactory.create(getGson()))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();
    }

    public Gson getGson() {
        if (mGson == null) {
            mGson = new GsonBuilder()
                    .setDateFormat("yyyy-MM-dd hh:mm:ss")
                    // 更多 Gson 配置
                    .create();
        }
        return mGson;
    }

    private OkHttpClient createHttpClient() {
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .addInterceptor(new BaseInterceptor())
                .addInterceptor(new HttpLoggingInterceptor())
                .followRedirects(true);

        if (IGNORE_SSL_VERIFY) {// 测试是否要忽略校验https的域名和证书
            httpClientBuilder.hostnameVerifier(SSLUtil.getIgnoredHostnameVerifier());
            SSLContext ignoredSSLContext = SSLUtil.getIgnoredSSLContext();
            if (ignoredSSLContext != null) {
                httpClientBuilder.sslSocketFactory(ignoredSSLContext.getSocketFactory());
            }
        }

        return httpClientBuilder.build();
    }

    static class BaseInterceptor implements Interceptor {
        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            Request originRequest = chain.request();
            HttpUrl url = originRequest.url();
            // 可以对 url 做一些处理，或者其它参数处理
            Request newRequest = originRequest.newBuilder()
                    .method(originRequest.method(), originRequest.body())
                    .url(url)
                    .build();
            return chain.proceed(newRequest);
        }
    }

    static class HttpLoggingInterceptor implements Interceptor {

        String tag = "HttpLoggingInterceptor";

        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            Request request = chain.request();

            long t1 = System.nanoTime();
            Log.d(tag, String.format("Sending request %s %s %n%s",
                    request.method(), request.url(), request.headers()));

            okhttp3.Response response = chain.proceed(request);

            long t2 = System.nanoTime();
            Log.d(tag, String.format("Received response for %s %s in %.1fms%n%s",
                    response.request().method(), response.request().url(), (t2 - t1) / 1e6d
                    , response.headers()));
            // 注意，不要在这里使用response.body().string()，因为response.body().string()只能请求一次，
            // 请求过后就会关闭，再次调用response.body().string()就会报异常：IllegalStateException: closed at okio.RealBufferedSource.read

            return response;
        }
    }

    public <T> T createAPI(Class<T> clazz) {
        return mRetrofit.create(clazz);
    }

}
