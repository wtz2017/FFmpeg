package com.wtz.liveplay.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;
import com.wtz.liveplay.R;
import com.wtz.liveplay.net.data.RadioChannels;

import java.util.ArrayList;
import java.util.List;

public class RadioChannelsGridAdapter extends BaseRecyclerViewAdapter<RadioChannelsGridAdapter.Holder> {

    private Context mContext;
    private List<RadioChannels.Channel> mData = new ArrayList<>();
    private int mIconWidth;
    private int mIconHeight;

    public RadioChannelsGridAdapter(Context mContext, List<RadioChannels.Channel> data) {
        this.mContext = mContext;
        if (data != null && data.size() > 0) {
            this.mData.addAll(data);
        }
    }

    public void update(List<RadioChannels.Channel> data) {
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
                .inflate(R.layout.item_radio_channels, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, int position) {
        RadioChannels.Channel channel = mData.get(position);
        holder.name.setText(channel.getName());
        if (mIconWidth == 0) {
            ViewGroup.LayoutParams lp = holder.icon.getLayoutParams();
            mIconWidth = lp.width;
            mIconHeight = lp.height;
        }
        Picasso.get()
                .load(channel.getImg())
                .resize(mIconWidth, mIconHeight)// 解决 OOM 问题
                .centerCrop()// 需要先调用fit或resize设置目标大小，否则会报错：Center crop requires calling resize with positive width and height
                .placeholder(R.drawable.icon_radio_default)
                .into(holder.icon);

        bindItemClickListener(holder);
    }

    @Override
    public int getItemCount() {
        return mData == null ? 0 : mData.size();
    }

    public class Holder extends RecyclerView.ViewHolder {
        private ImageView icon;
        private TextView name;

        public Holder(View itemView) {
            super(itemView);
            name = (TextView) itemView.findViewById(R.id.tv_name);
            icon = (ImageView) itemView.findViewById(R.id.iv_icon);
        }
    }

}
