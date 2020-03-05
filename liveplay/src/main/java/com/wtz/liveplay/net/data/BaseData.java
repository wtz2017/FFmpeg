package com.wtz.liveplay.net.data;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Locale;

public class BaseData implements Serializable {

    @SerializedName("status")
    private int status;

    @SerializedName("message")
    private String message;

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isStatusOK() {
        return status == 200;
    }

    /**
     * 具体业务数据是否正常，子类根据实际情况重写此方法判断
     */
    public boolean isDataOK() {
        return true;
    }

    @NonNull
    @Override
    public String toString() {
        return obj2String(this);
    }

    public static String obj2String(Object obj) {
        StringBuilder builder = new StringBuilder();
        builder.append(obj.getClass().getSimpleName());
        builder.append(":{");
        try {
            Class<?> objClass = obj.getClass();
            Method[] methods = objClass.getDeclaredMethods();
            for (Method method : methods) {
                String methodName = method.getName();
                if (methodName.startsWith("get") && !methodName.contains("getClass")) {
                    Object methodRet = method.invoke(obj);
                    builder.append(methodName.substring(3).toLowerCase(Locale.CHINA));
                    builder.append(":");
                    builder.append(methodRet);
                    builder.append(",");
                } else if (method.getReturnType().getName().equals("boolean")) {
                    Object methodRet = method.invoke(obj);
                    builder.append(methodName);
                    builder.append(":");
                    builder.append(methodRet);
                    builder.append(",");
                }
            }
            int lastIndex = builder.lastIndexOf(",");
            if (lastIndex != -1) {
                builder.deleteCharAt(lastIndex);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        builder.append("}");

        return builder.toString();
    }

}
