package com.wtz.liveplay.net;

import com.wtz.liveplay.net.Listener.BaseObserver;
import com.wtz.liveplay.net.Listener.ResultListener;
import com.wtz.liveplay.net.data.RadioChannels;
import com.wtz.liveplay.net.data.RadioPlaces;
import com.wtz.liveplay.net.data.RadioToken;

public class RadioAPI {

    private static RadioAPI mInstance;
    private RadioService mRadioService;

    public static RadioAPI getInstance() {
        if (mInstance == null) {
            synchronized (HttpMethod.class) {
                if (mInstance == null) {
                    mInstance = new RadioAPI();
                }
            }
        }
        return mInstance;
    }

    private RadioAPI() {
        mRadioService = HttpMethod.getInstance().createAPI(RadioService.class);
    }

    public void getToken(ResultListener<RadioToken> listener) {
        new BaseObserver<RadioToken>("getToken", listener)
                .subscribe(mRadioService.getToken());
    }

    public void getPlaces(String token, ResultListener<RadioPlaces> listener) {
        new BaseObserver<RadioPlaces>("getPlaces", listener)
                .subscribe(mRadioService.getPlaces(token));
    }

    public void getChannelsByPlace(String token, String channelPlaceId, ResultListener<RadioChannels> listener) {
        new BaseObserver<RadioChannels>("getChannelsByPlace", listener)
                .subscribe(mRadioService.getChannelsByPlace(token, channelPlaceId));
    }

}
