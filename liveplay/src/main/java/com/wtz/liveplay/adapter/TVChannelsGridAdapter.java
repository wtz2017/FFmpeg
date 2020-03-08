package com.wtz.liveplay.adapter;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wtz.liveplay.R;

import java.util.ArrayList;
import java.util.List;

public class TVChannelsGridAdapter extends BaseRecyclerViewAdapter<TVChannelsGridAdapter.Holder> {

    private List<TVItem> mData = new ArrayList<>();

    public TVChannelsGridAdapter(List<TVItem> data) {
        if (data != null && data.size() > 0) {
            this.mData.addAll(data);
        }
    }

    public void update(List<TVItem> data) {
        mData.clear();
        if (data != null && data.size() > 0) {
            this.mData.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tv_channels, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, int position) {
        TVItem channel = mData.get(position);
        if (channel.isCCTV) {
            holder.icon.setImageResource(R.drawable.icon_cctv_bg);
            holder.num.setText("" + channel.num);
            holder.num.setVisibility(View.VISIBLE);
        } else {
            holder.icon.setImageResource(R.drawable.icon_other_tv_bg);
            holder.num.setVisibility(View.GONE);
        }
        holder.name.setText(channel.name);
        bindItemClickListener(holder);
    }

    @Override
    public int getItemCount() {
        return mData == null ? 0 : mData.size();
    }

    public class Holder extends RecyclerView.ViewHolder {
        private ImageView icon;
        private TextView num;
        private TextView name;

        public Holder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.iv_icon);
            num = itemView.findViewById(R.id.tv_num);
            name = itemView.findViewById(R.id.tv_name);
        }
    }

    public static class TVItem implements Parcelable {
        public boolean isCCTV;
        public int num;
        public String name;
        public String url;

        public TVItem(boolean isCCTV, int num, String name, String url) {
            this.isCCTV = isCCTV;
            this.num = num;
            this.name = name;
            this.url = url;
        }

        protected TVItem(Parcel in) {
            isCCTV = in.readByte() != 0;
            num = in.readInt();
            name = in.readString();
            url = in.readString();
        }

        public static final Creator<TVItem> CREATOR = new Creator<TVItem>() {
            @Override
            public TVItem createFromParcel(Parcel in) {
                return new TVItem(in);
            }

            @Override
            public TVItem[] newArray(int size) {
                return new TVItem[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeByte((byte) (isCCTV ? 1 : 0));
            dest.writeInt(num);
            dest.writeString(name);
            dest.writeString(url);
        }
    }

}
