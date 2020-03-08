package com.wtz.liveplay;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wtz.liveplay.adapter.BaseRecyclerViewAdapter;
import com.wtz.liveplay.adapter.TVChannelsGridAdapter;
import com.wtz.liveplay.utils.ScreenUtils;
import com.wtz.liveplay.view.GridItemDecoration;

import java.util.ArrayList;

public class TVFragment extends Fragment {

    private static final String TAG = "TVFragment";

    private RecyclerView mChannelsGridView;
    private TVChannelsGridAdapter mChannelsAdapter;
    private ArrayList<TVChannelsGridAdapter.TVItem> mChannelList = new ArrayList<>();

    @Override
    public void onAttach(@NonNull Context context) {
        Log.d(TAG, "onAttach");
        super.onAttach(context);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        View root = inflater.inflate(R.layout.fragment_tv, container, false);
        configView(root);

        initData();
        return root;
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
    }

    private void configView(View root) {
        mChannelsGridView = root.findViewById(R.id.recycler_view_channels);
        int[] wh = ScreenUtils.getScreenPixels(getActivity());
        int spanCount = wh[0] / 360;
        GridLayoutManager channelsLayoutManager = new GridLayoutManager(getActivity(), spanCount);
        channelsLayoutManager.setOrientation(RecyclerView.VERTICAL);
        mChannelsGridView.setLayoutManager(channelsLayoutManager);
        mChannelsGridView.addItemDecoration(
                new GridItemDecoration(getActivity(), R.drawable.grid_divider_line_shape, true));
        mChannelsAdapter = new TVChannelsGridAdapter(mChannelList);
        mChannelsAdapter.setOnItemClickListener(new BaseRecyclerViewAdapter.OnRecyclerViewItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                Log.d(TAG, "mChannelsAdapter onItemClick position=" + position);
                play(position);
            }

            @Override
            public boolean onItemLongClick(View view, int position) {
                Log.d(TAG, "mChannelsAdapter onItemLongClick position=" + position);
                return true;
            }
        });
        mChannelsGridView.setAdapter(mChannelsAdapter);
    }

    private void initData() {
        // IVI直播测试 http://ivi.bupt.edu.cn/
        mChannelList.clear();
        // 央视
        mChannelList.add(new TVChannelsGridAdapter.TVItem(true, 1, "综合",
                "http://ivi.bupt.edu.cn/hls/cctv1hd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(true, 2, "财经",
                "http://ivi.bupt.edu.cn/hls/cctv2hd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(true, 3, "综艺",
                "http://ivi.bupt.edu.cn/hls/cctv3hd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(true, 4, "中文国际",
                "http://ivi.bupt.edu.cn/hls/cctv4hd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(true, 5, "体育",
                "http://ivi.bupt.edu.cn/hls/cctv5phd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(true, 6, "电影",
                "http://ivi.bupt.edu.cn/hls/cctv6hd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(true, 7, "国防军事",
                "http://ivi.bupt.edu.cn/hls/cctv7hd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(true, 8, "电视剧",
                "http://ivi.bupt.edu.cn/hls/cctv8hd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(true, 9, "纪录",
                "http://ivi.bupt.edu.cn/hls/cctv9hd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(true, 10, "科教",
                "http://ivi.bupt.edu.cn/hls/cctv10hd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(true, 12, "社会与法",
                "http://ivi.bupt.edu.cn/hls/cctv12hd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(true, 14, "少儿",
                "http://ivi.bupt.edu.cn/hls/cctv14hd.m3u8"
        ));
        // 卫视
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "北京卫视",
                "http://ivi.bupt.edu.cn/hls/btv1hd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "湖南卫视",
                "http://ivi.bupt.edu.cn/hls/hunanhd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "浙江卫视",
                "http://ivi.bupt.edu.cn/hls/zjhd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "江苏卫视",
                "http://ivi.bupt.edu.cn/hls/jshd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "东方卫视",
                "http://ivi.bupt.edu.cn/hls/dfhd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "安徽卫视",
                "http://ivi.bupt.edu.cn/hls/ahhd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "黑龙江卫视",
                "http://ivi.bupt.edu.cn/hls/hljhd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "辽宁卫视",
                "http://ivi.bupt.edu.cn/hls/lnhd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "深圳卫视",
                "http://ivi.bupt.edu.cn/hls/szhd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "广东卫视",
                "http://ivi.bupt.edu.cn/hls/gdhd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "天津卫视",
                "http://ivi.bupt.edu.cn/hls/tjhd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "湖北卫视",
                "http://ivi.bupt.edu.cn/hls/hbhd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "山东卫视",
                "http://ivi.bupt.edu.cn/hls/sdhd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "重庆卫视",
                "http://ivi.bupt.edu.cn/hls/cqhd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "福建东南卫视",
                "http://ivi.bupt.edu.cn/hls/dnhd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "四川卫视",
                "http://ivi.bupt.edu.cn/hls/schd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "河北卫视",
                "http://ivi.bupt.edu.cn/hls/hebhd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "河南卫视",
                "http://ivi.bupt.edu.cn/hls/hnhd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "江西卫视",
                "http://ivi.bupt.edu.cn/hls/jxhd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "广西卫视",
                "http://ivi.bupt.edu.cn/hls/gxhd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "吉林卫视",
                "http://ivi.bupt.edu.cn/hls/jlhd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "海南卫视",
                "http://ivi.bupt.edu.cn/hls/lyhd.m3u8"
        ));
        mChannelList.add(new TVChannelsGridAdapter.TVItem(false, 0, "贵州卫视",
                "http://ivi.bupt.edu.cn/hls/gzhd.m3u8"
        ));

        mChannelsAdapter.update(mChannelList);
        mChannelsGridView.smoothScrollToPosition(0);
    }

    private void play(int index) {
        // 当要打开视频时，需要先结束电台音频服务
        Intent stopAudioIntent = new Intent(AudioService.STOP_PLAY_ACTION);
        stopAudioIntent.setPackage(getActivity().getPackageName());
        getActivity().sendBroadcast(stopAudioIntent);

        // 然后再打开视频
        Intent i = new Intent(getActivity(), VideoPlayer.class);
        i.putParcelableArrayListExtra(VideoPlayer.KEY_VIDEO_LIST, mChannelList);
        i.putExtra(VideoPlayer.KEY_VIDEO_INDEX, index);
        startActivity(i);
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "onDestroyView");
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        Log.d(TAG, "onDetach");
        super.onDetach();
    }

}
