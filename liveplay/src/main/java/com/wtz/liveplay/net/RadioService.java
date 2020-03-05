package com.wtz.liveplay.net;

import com.wtz.liveplay.net.data.RadioChannels;
import com.wtz.liveplay.net.data.RadioPlaces;
import com.wtz.liveplay.net.data.RadioToken;

import io.reactivex.Observable;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface RadioService {

    /**
     * 获取 token
     */
    @POST("gettoken")
    Observable<RadioToken> getToken();

    /**
     * 获取地区编号
     */
    @GET("channels/getliveplace")
    Observable<RadioPlaces> getPlaces(@Query("token") String token);

    /**
     * 根据地区编号获取频道列表
     */
    @GET("channels/getlivebyparam")
    Observable<RadioChannels> getChannelsByPlace(@Query("token") String token, @Query("channelPlaceId") String channelPlaceId, @Query("limit") int limit, @Query("offset") int offset);

    /**
     * 根据地区编号获取频道列表
     */
    @GET("channels/getlivebyparam")
    Observable<RadioChannels> getChannelsByPlace(@Query("token") String token, @Query("channelPlaceId") String channelPlaceId);

}
