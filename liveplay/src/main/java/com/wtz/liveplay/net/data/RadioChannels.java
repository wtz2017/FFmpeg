package com.wtz.liveplay.net.data;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

public class RadioChannels extends BaseData {

    @Override
    public boolean isDataOK() {
        return channelList != null && channelList.size() > 0;
    }

    @SerializedName("liveChannel")
    private List<Channel> channelList;

    public List<Channel> getChannelList() {
        return channelList;
    }

    public void setChannelList(List<Channel> channelList) {
        this.channelList = channelList;
    }

    public class Channel implements Serializable {

        @SerializedName("id")
        private String id;

        @SerializedName("name")
        private String name;

        @SerializedName("radio_id")
        private String radioId;

        @SerializedName("radio_name")
        private String radioName;

        @SerializedName("channelPage")
        private String channelPage;

        @SerializedName("img")
        private String img;

        @SerializedName("streams")
        private List<Stream> streamList;

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

        public String getRadioId() {
            return radioId;
        }

        public void setRadioId(String radioId) {
            this.radioId = radioId;
        }

        public String getRadioName() {
            return radioName;
        }

        public void setRadioName(String radioName) {
            this.radioName = radioName;
        }

        public String getChannelPage() {
            return channelPage;
        }

        public void setChannelPage(String channelPage) {
            this.channelPage = channelPage;
        }

        public String getImg() {
            return img;
        }

        public void setImg(String img) {
            this.img = img;
        }

        public List<Stream> getStreamList() {
            return streamList;
        }

        public void setStreamList(List<Stream> streamList) {
            this.streamList = streamList;
        }

        @NonNull
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("Channel(");
            builder.append(id);
            builder.append(",");
            builder.append(name);
            builder.append(",");
            builder.append(radioName);
            builder.append(",");
            builder.append("StreamList:[");
            if (streamList != null && streamList.size() > 0) {
                for (Stream stream : streamList) {
                    builder.append(stream);
                    builder.append(",");
                }
                builder.deleteCharAt(builder.length() - 1);
            }
            builder.append("]");
            builder.append(")");
            return builder.toString();
        }

    }

    public class Stream implements Serializable {

        /**
         * e.g. "H" "M" "L"
         */
        @SerializedName("resolution")
        private String resolution;

        @SerializedName("url")
        private String url;

        public String getResolution() {
            return resolution;
        }

        public void setResolution(String resolution) {
            this.resolution = resolution;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        @NonNull
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("Stream(");
            builder.append(resolution);
            builder.append(",");
            builder.append(url);
            builder.append(")");
            return builder.toString();
        }

    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("ChannelList[");
        if (channelList != null && channelList.size() > 0) {
            for (Channel channel : channelList) {
                builder.append(channel);
                builder.append(",");
            }
            builder.deleteCharAt(builder.length() - 1);
        }
        builder.append("]");
        return super.toString();
    }

}
