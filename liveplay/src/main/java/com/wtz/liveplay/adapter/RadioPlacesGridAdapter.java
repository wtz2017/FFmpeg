package com.wtz.liveplay.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wtz.liveplay.R;
import com.wtz.liveplay.net.data.RadioPlaces;

import java.util.ArrayList;
import java.util.List;

public class RadioPlacesGridAdapter extends BaseRecyclerViewAdapter<RadioPlacesGridAdapter.Holder> {
    private static final String TAG = "RadioPlacesGridAdapter";

    private List<RadioPlaces.Place> mData = new ArrayList<>();
    private OnRecyclerViewItemClickListener mOnItemClickListener;

    private static final int UNSELECTED_TEXTCOLOR = Color.parseColor("#FF000000");
    private static final int SELECTED_TEXTCOLOR = Color.parseColor("#FF7F00");
    private int mLastSelectedPosition;
    private View mLastSelectedView;

    public RadioPlacesGridAdapter(List<RadioPlaces.Place> data, int selectedPosition) {
        this.mLastSelectedPosition = selectedPosition;
        if (data != null && data.size() > 0) {
            this.mData.addAll(data);
        }
        super.setOnItemClickListener(mInnerItemClickListener);
    }

    @Override
    public void setOnItemClickListener(OnRecyclerViewItemClickListener listener) {
        this.mOnItemClickListener = listener;
    }

    public void update(List<RadioPlaces.Place> data) {
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
                .inflate(R.layout.item_radio_places, parent, false);
        Holder holder = new Holder(view);
        bindItemClickListener(holder);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, int position) {
        holder.name.setText(mData.get(position).getName());
        if (position == mLastSelectedPosition) {
            setSelectEffect(holder.itemView, true);
            mLastSelectedView = holder.itemView;
        } else {
            setSelectEffect(holder.itemView, false);
        }
    }

    private OnRecyclerViewItemClickListener mInnerItemClickListener = new OnRecyclerViewItemClickListener() {
        @Override
        public void onItemClick(View view, int position) {
            setSelectEffect(mLastSelectedView, false);
            setSelectEffect(view, true);
            mLastSelectedPosition = position;
            mLastSelectedView = view;

            if (mOnItemClickListener != null) {
                mOnItemClickListener.onItemClick(view, position);
            }
        }

        @Override
        public boolean onItemLongClick(View view, int position) {
            if (mOnItemClickListener != null) {
                return mOnItemClickListener.onItemLongClick(view, position);
            }
            return false;
        }
    };

    private void setSelectEffect(View view, boolean selected) {
        if (view == null) return;

        TextView name = (TextView) view.findViewById(R.id.tv_name);
        if (selected) {
            name.setTextColor(SELECTED_TEXTCOLOR);
        } else {
            name.setTextColor(UNSELECTED_TEXTCOLOR);
        }
    }

    @Override
    public int getItemCount() {
        return mData == null ? 0 : mData.size();
    }

    public class Holder extends RecyclerView.ViewHolder {
        private TextView name;

        public Holder(View itemView) {
            super(itemView);
            name = (TextView) itemView.findViewById(R.id.tv_name);
        }
    }

}
