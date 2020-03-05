package com.wtz.liveplay;

import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;
import com.wtz.liveplay.adapter.MyFragmentPagerAdapter;
import com.wtz.liveplay.view.TabItemView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends FragmentActivity implements TabLayout.OnTabSelectedListener {
    private static final String TAG = "liveplay.MainActivity";

    private List<TabItemView> mTabItemList;
    private List<Fragment> mFragmentList;

    private TabLayout mTabLayout;
    private ViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTabLayout = findViewById(R.id.tab_layout);
        mViewPager = findViewById(R.id.view_pager);

        mTabItemList = new ArrayList<>();
        mTabItemList.add(new TabItemView(this)
                .create(R.string.radio, R.drawable.ic_radio_selector));
        mTabItemList.add(new TabItemView(this)
                .create(R.string.tv, R.drawable.ic_tv_selector));

        mFragmentList = new ArrayList<>();
        mFragmentList.add(new RadioFragment());
        mFragmentList.add(new TVFragment());

        mViewPager.setAdapter(
                new MyFragmentPagerAdapter(getSupportFragmentManager(), mFragmentList, mTabItemList));
        mViewPager.setOffscreenPageLimit(3);
        mViewPager.setCurrentItem(0);

        mTabLayout.setupWithViewPager(mViewPager);// 将 ViewPager 和 TabLayout 绑定
        mTabLayout.addOnTabSelectedListener(this);
        for (int i = 0; i < mTabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = mTabLayout.getTabAt(i);
            if (tab != null) {
                tab.setCustomView(mTabItemList.get(i));
            }
        }
        mTabItemList.get(0).setSecletedEffect(true);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        Log.d(TAG, "onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        Log.d(TAG, "onTabSelected: " + tab);
        ((TabItemView) tab.getCustomView()).setSecletedEffect(true);
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
        Log.d(TAG, "onTabUnselected: " + tab);
        ((TabItemView) tab.getCustomView()).setSecletedEffect(false);
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
        Log.d(TAG, "onTabReselected: " + tab);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        mTabLayout.removeOnTabSelectedListener(this);
        super.onDestroy();
    }

}
