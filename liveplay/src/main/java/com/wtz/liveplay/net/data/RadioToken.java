package com.wtz.liveplay.net.data;

import android.text.TextUtils;

import com.google.gson.annotations.SerializedName;

public class RadioToken extends BaseData {

    @Override
    public boolean isDataOK() {
        return !TextUtils.isEmpty(token);
    }

    @SerializedName("token")
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

}
