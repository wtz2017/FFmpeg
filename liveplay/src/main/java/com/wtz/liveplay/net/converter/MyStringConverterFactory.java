package com.wtz.liveplay.net.converter;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;

public class MyStringConverterFactory extends Converter.Factory {

    private static final MyStringConverterFactory INSTANCE = new MyStringConverterFactory();

    public static MyStringConverterFactory create() {
        return INSTANCE;
    }

    // 我们只关实现从 ResponseBody 到 String 的转换，所以其它方法可不覆盖
    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
        if (type == String.class) {
            return MyStringResponseBodyConverter.INSTANCE;
        }
        //其 它类型我们不处理，返回null就行
        return null;
    }

    /**
     * 自定义 Converter 实现 RequestBody 到 String 的转换
     */
    static class MyStringResponseBodyConverter implements Converter<ResponseBody, String> {

        public static final MyStringResponseBodyConverter INSTANCE = new MyStringResponseBodyConverter();

        @Override
        public String convert(ResponseBody value) throws IOException {
            return value.string();
        }
    }

}
