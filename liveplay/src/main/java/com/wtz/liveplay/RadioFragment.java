package com.wtz.liveplay;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wtz.liveplay.adapter.BaseRecyclerViewAdapter;
import com.wtz.liveplay.adapter.RadioChannelsGridAdapter;
import com.wtz.liveplay.adapter.RadioPlacesGridAdapter;
import com.wtz.liveplay.net.Listener.ResultListener;
import com.wtz.liveplay.net.RadioAPI;
import com.wtz.liveplay.net.data.RadioChannels;
import com.wtz.liveplay.net.data.RadioPlaces;
import com.wtz.liveplay.net.data.RadioToken;
import com.wtz.liveplay.net.error.ApiErrorCode;
import com.wtz.liveplay.utils.ScreenUtils;
import com.wtz.liveplay.view.GridItemDecoration;
import com.wtz.liveplay.view.LoadingDialog;

import java.util.ArrayList;
import java.util.List;

public class RadioFragment extends Fragment {

    private static final String TAG = "RadioFragment";

    private LayoutInflater mInflater;
    private FrameLayout mRoot;

    private LoadingDialog mLoadingDialog;
    private Dialog mErrorDialog;
    private TextView mErrorTitle;
    private TextView mErrorButton;

    private RecyclerView mPlacesGridView;
    private RadioPlacesGridAdapter mPlacesAdapter;

    private RecyclerView mChannelsGridView;
    private RadioChannelsGridAdapter mChannelsAdapter;

    private String mToken;
    private List<RadioPlaces.Place> mPlaceList;
    private List<RadioChannels.Channel> mChannelList;
    private ArrayList<AudioService.AudioItem> mPlayList = new ArrayList<>();

    private static final int ERROR_GET_PLACES = 0;
    private static final int ERROR_GET_CHANNELS = 1;
    private int mErrorType = ERROR_GET_PLACES;

    private int mCurrentPlaceIndex;

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
        this.mInflater = inflater;

        mRoot = (FrameLayout) mInflater.inflate(R.layout.fragment_radio_container, container, false);
        configView();

        initData();
        return mRoot;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        Log.d(TAG, "onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
        configView();
    }

    private void configView() {
        View content;
        boolean isPortrait = ScreenUtils.isPortrait(getActivity());
        if (isPortrait) {
            // 竖屏
            content = mInflater.inflate(R.layout.fragment_radio_portrait, mRoot, false);
        } else {
            // 横屏
            content = mInflater.inflate(R.layout.fragment_radio_landscape, mRoot, false);
        }
        mRoot.removeAllViews();
        mRoot.addView(content);

        initPlacesGridView(mRoot, isPortrait);
        initChannelsGridView(mRoot);
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

    private void initPlacesGridView(View root, boolean isPortrait) {
        mPlacesGridView = root.findViewById(R.id.recycler_view_places);
        GridLayoutManager placesLayoutManager;
        if (isPortrait) {
            placesLayoutManager = new GridLayoutManager(getActivity(), 2);
            placesLayoutManager.setOrientation(RecyclerView.HORIZONTAL);
        } else {
            placesLayoutManager = new GridLayoutManager(getActivity(), 2);
            placesLayoutManager.setOrientation(RecyclerView.VERTICAL);
        }
        mPlacesGridView.setLayoutManager(placesLayoutManager);
        mPlacesGridView.addItemDecoration(
                new GridItemDecoration(getActivity(), R.drawable.grid_divider_line_shape, true));
        mPlacesAdapter = new RadioPlacesGridAdapter(mPlaceList, mCurrentPlaceIndex);
        mPlacesAdapter.setOnItemClickListener(new BaseRecyclerViewAdapter.OnRecyclerViewItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                Log.d(TAG, "mPlacesAdapter onItemClick position=" + position);
                mCurrentPlaceIndex = position;
                showLoading();
                getChannelsData(mPlaceList.get(position));
            }

            @Override
            public boolean onItemLongClick(View view, int position) {
                Log.d(TAG, "mPlacesAdapter onItemLongClick position=" + position);
                return true;
            }
        });
        mPlacesGridView.setAdapter(mPlacesAdapter);
    }

    private void initChannelsGridView(View root) {
        mChannelsGridView = root.findViewById(R.id.recycler_view_channels);
        int[] wh = ScreenUtils.getScreenPixels(getActivity());
        int spanCount = wh[0] / 480;
        GridLayoutManager channelsLayoutManager = new GridLayoutManager(getActivity(), spanCount);
        channelsLayoutManager.setOrientation(RecyclerView.VERTICAL);
        mChannelsGridView.setLayoutManager(channelsLayoutManager);
        mChannelsGridView.addItemDecoration(
                new GridItemDecoration(getActivity(), R.drawable.grid_divider_line_shape, true));
        mChannelsAdapter = new RadioChannelsGridAdapter(getActivity(), mChannelList);
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
        showLoading();
        if (!TextUtils.isEmpty(mToken)) {
            getPlacesData();
        } else {
            RadioAPI.getInstance().getToken(new ResultListener<RadioToken>() {
                @Override
                public void onSuccess(RadioToken data) {
                    Log.d(TAG, "getToken onSuccess isDataOK=" + data.isDataOK());
                    mToken = data.getToken();
                    getPlacesData();
                }

                @Override
                public void onFailed(int code, String error) {
                    Log.e(TAG, "getToken onFailed " + code + ":" + error);
                    hideLoading();
                    onGetPlacesDataFailed(code);
                }
            });
        }
    }

    private void getPlacesData() {
        RadioAPI.getInstance().getPlaces(mToken, new ResultListener<RadioPlaces>() {
            @Override
            public void onSuccess(RadioPlaces data) {
                Log.d(TAG, "getPlaces onSuccess isDataOK=" + data.isDataOK());
                mPlaceList = data.getPlaceList();
                mPlacesAdapter.update(mPlaceList);
                RadioPlaces.Place place = mPlaceList.get(mCurrentPlaceIndex);
                getChannelsData(place);
            }

            @Override
            public void onFailed(int code, String error) {
                Log.e(TAG, "getPlaces onFailed " + code + ":" + error);
                hideLoading();
                onGetPlacesDataFailed(code);
            }
        });
    }

    private void onGetPlacesDataFailed(int errorCode) {
        mErrorType = ERROR_GET_PLACES;
        showError(errorCode);
    }

    private void getChannelsData(RadioPlaces.Place place) {
        RadioAPI.getInstance().getChannelsByPlace(mToken, place.getId(), new ResultListener<RadioChannels>() {
            @Override
            public void onSuccess(RadioChannels data) {
                Log.d(TAG, "getChannelsData onSuccess isDataOK=" + data.isDataOK());
                mChannelList = data.getChannelList();
                mChannelsAdapter.update(mChannelList);
                mPlayList.clear();
                mChannelsGridView.smoothScrollToPosition(0);
                hideLoading();
            }

            @Override
            public void onFailed(int code, String error) {
                Log.e(TAG, "getChannelsData onFailed " + code + ":" + error);
                hideLoading();
                onGetChannelsDataFailed(code);
            }
        });
    }

    private void createPlayList() {
        for (RadioChannels.Channel channel : mChannelList) {
            try {
                mPlayList.add(new AudioService.AudioItem(
                        channel.getName(),
                        channel.getImg(),
                        channel.getStreamList().get(0).getUrl())
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void onGetChannelsDataFailed(int errorCode) {
        mErrorType = ERROR_GET_CHANNELS;
        showError(errorCode);
    }

    private void showError(int errorCode) {
        if (mErrorDialog == null) {
            mErrorDialog = new Dialog(getActivity(), R.style.NormalDialogStyle);
            View content = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_error, null);
            mErrorTitle = content.findViewById(R.id.tv_name);
            mErrorButton = content.findViewById(R.id.btn_retry);
            mErrorButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "mErrorDialog onRetry");
                    mErrorDialog.dismiss();
                    onRetry();
                }
            });
            mErrorDialog.setContentView(content);
            mErrorDialog.setCanceledOnTouchOutside(true);
            mErrorDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    Log.d(TAG, "mErrorDialog onCancel");
                }
            });
            mErrorDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    Log.d(TAG, "mErrorDialog onDismiss");
                }
            });
        }
        switch (errorCode) {
            case ApiErrorCode.ERROR_NET_CONNECT_FAILED:
                mErrorTitle.setText("本地网络连接失败");
                mErrorButton.setText("连接后重试");
                break;

            case ApiErrorCode.ERROR_SERVER_ACCESS_FAILED:
                mErrorTitle.setText("远程服务访问失败");
                mErrorButton.setText("点击重试");
                break;

            case ApiErrorCode.ERROR_DATA_EXCEPTION:
                mErrorTitle.setText("远程服务数据异常");
                mErrorButton.setText("点击重试");
                break;

            default:
                mErrorTitle.setText("数据获取未知错误");
                mErrorButton.setText("点击重试");
                break;
        }
        mErrorDialog.show();
    }

    private void hideError() {
        if (mErrorDialog != null) {
            mErrorDialog.cancel();
        }
    }

    private void onRetry() {
        switch (mErrorType) {
            case ERROR_GET_PLACES:
                getPlacesData();
                break;

            case ERROR_GET_CHANNELS:
                getChannelsData(mPlaceList.get(mCurrentPlaceIndex));
                break;
        }
    }

    private void showLoading() {
        if (mLoadingDialog == null) {
            mLoadingDialog = new LoadingDialog(getActivity());
            mLoadingDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    Log.d(TAG, "mLoadingDialog onCancel");
                }
            });
            mLoadingDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    Log.d(TAG, "mLoadingDialog onDismiss");
                }
            });
        }
        mLoadingDialog.show();
    }

    private void hideLoading() {
        if (mLoadingDialog != null) {
            mLoadingDialog.cancel();
        }
    }

    private void play(int index) {
        if (mPlayList.isEmpty()) {
            createPlayList();
        }
        Intent i = new Intent(getActivity(), AudioPlayer.class);
        i.putParcelableArrayListExtra(AudioPlayer.KEY_AUDIO_LIST, mPlayList);
        i.putExtra(AudioPlayer.KEY_AUDIO_INDEX, index);
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
