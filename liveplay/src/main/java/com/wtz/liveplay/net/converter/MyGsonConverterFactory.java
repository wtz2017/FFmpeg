package com.wtz.liveplay.net.converter;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.wtz.liveplay.net.data.BaseData;
import com.wtz.liveplay.net.error.ApiException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.Charset;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.Buffer;
import retrofit2.Converter;
import retrofit2.Retrofit;

public class MyGsonConverterFactory extends Converter.Factory {

    private final Gson gson;

    private MyGsonConverterFactory(Gson gson) {
        if (gson == null) throw new NullPointerException("gson == null");
        this.gson = gson;
    }

    public static MyGsonConverterFactory create() {
        return create(new Gson());
    }

    public static MyGsonConverterFactory create(Gson gson) {
        return new MyGsonConverterFactory(gson);
    }

    @Override
    public Converter<?, RequestBody> requestBodyConverter(Type type, Annotation[] parameterAnnotations, Annotation[] methodAnnotations, Retrofit retrofit) {
        TypeAdapter<?> adapter = gson.getAdapter(TypeToken.get(type));
        return new MyGsonRequestBodyConverter<>(gson, adapter);
    }

    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
        TypeAdapter<?> adapter = gson.getAdapter(TypeToken.get(type));
        return new MyGsonResponseBodyConverter<>(gson, adapter);
    }

    static class MyGsonRequestBodyConverter<T> implements Converter<T, RequestBody> {
        private static final MediaType MEDIA_TYPE = MediaType.parse("application/json; charset=UTF-8");
        private static final Charset UTF_8 = Charset.forName("UTF-8");

        private final Gson gson;
        private final TypeAdapter<T> adapter;

        public MyGsonRequestBodyConverter(Gson gson, TypeAdapter<T> adapter) {
            this.gson = gson;
            this.adapter = adapter;
        }

        @Override
        public RequestBody convert(T value) throws IOException {
            Buffer buffer = new Buffer();
            Writer writer = new OutputStreamWriter(buffer.outputStream(), UTF_8);
            JsonWriter jsonWriter = gson.newJsonWriter(writer);
            adapter.write(jsonWriter, value);
            jsonWriter.close();
            return RequestBody.create(MEDIA_TYPE, buffer.readByteString());
        }
    }

    static class MyGsonResponseBodyConverter<T> implements Converter<ResponseBody, T> {
        private static final Charset UTF_8 = Charset.forName("UTF-8");
        private final Gson mGson;
        private final TypeAdapter<T> adapter;

        public MyGsonResponseBodyConverter(Gson gson, TypeAdapter<T> adapter) {
            mGson = gson;
            this.adapter = adapter;
        }

        @Override
        public T convert(ResponseBody value) throws IOException {
            String response = value.string();
            BaseData ret = mGson.fromJson(response, BaseData.class);
            // 关注的重点，自定义响应码错误的情况，一律抛出 ApiException 异常。
            // 这样，我们就成功的将该异常交给 onError() 去处理了。
            // 但是如果响应码正常，但是 data 为空或有问题呢？
            // 有时就是响应码正常，无数据而已，这就看服务端如何设计接口了
            if (!ret.isStatusOK()) {
                value.close();
                throw new ApiException(ret.getStatus(), ret.getMessage());
            }

            MediaType mediaType = value.contentType();
            Charset charset = mediaType != null ? mediaType.charset(UTF_8) : UTF_8;
            ByteArrayInputStream bis = new ByteArrayInputStream(response.getBytes());
            InputStreamReader reader = new InputStreamReader(bis, charset);
            JsonReader jsonReader = mGson.newJsonReader(reader);
            try {
                return adapter.read(jsonReader);
            } finally {
                value.close();
            }
        }
    }

}
