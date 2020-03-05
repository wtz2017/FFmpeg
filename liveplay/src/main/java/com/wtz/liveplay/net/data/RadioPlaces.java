package com.wtz.liveplay.net.data;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

public class RadioPlaces extends BaseData {

    @Override
    public boolean isDataOK() {
        return placeList != null && placeList.size() > 0;
    }

    @SerializedName("liveChannelPlace")
    private List<Place> placeList;

    public List<Place> getPlaceList() {
        return placeList;
    }

    public void setPlaceList(List<Place> placeList) {
        this.placeList = placeList;
    }

    public class Place implements Serializable {

        @SerializedName("id")
        private String id;

        @SerializedName("name")
        private String name;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @NonNull
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("Place(");
            builder.append(id);
            builder.append(",");
            builder.append(name);
            builder.append(")");
            return builder.toString();
        }
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("PlaceList[");
        if (placeList != null && placeList.size() > 0) {
            for (Place place : placeList) {
                builder.append(place);
                builder.append(",");
            }
            builder.deleteCharAt(builder.length() - 1);
        }
        builder.append("]");
        return super.toString();
    }

}
