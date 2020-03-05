package com.wtz.liveplay.view;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.wtz.liveplay.R;

public class TabItemView extends LinearLayout {

    private static final int UNSELECTED_TEXTCOLOR = Color.parseColor("#FF000000");
    private static final int SELECTED_TEXTCOLOR = Color.parseColor("#FF7F00");

    // private static final int TEXTSIZE_UNIT = TypedValue.COMPLEX_UNIT_SP;
    // ScreenMather 适配的是使用哪个 R.dimen.xxx，而 getDimension 返回的是像素，因此最终 UNIT 是 PX
    private static final int TEXTSIZE_UNIT = TypedValue.COMPLEX_UNIT_PX;
    private static final int TEXTSIZE_DIMEN_ID = R.dimen.sp_18;
    private static final int TITLE_LEFT_MARGIN_DIMEN_ID = R.dimen.dp_6;

    private static final int PADDING_HORIZONTAL_DIMEN_ID = R.dimen.dp_15;
    private static final int PADDING_VERTICAL_DIMEN_ID = R.dimen.dp_5;
    private static final int ICON_WIDTH_DIMEN_ID = R.dimen.dp_30;
    private static final int ICON_HEIGHT_DIMEN_ID = R.dimen.dp_30;

    private ImageView iconView;
    private TextView titleView;

    public TabItemView(Context context) {
        this(context, null);
    }

    public TabItemView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TabItemView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.CENTER);
//        int paddingHorizontal = dp2px(getContext(), PADDING_HORIZONTAL_DP);
//        int paddingVertical = dp2px(getContext(), PADDING_VERTICAL_DP);
        int paddingHorizontal = (int) (getContext().getResources()
                .getDimension(PADDING_HORIZONTAL_DIMEN_ID) + 0.5f);
        int paddingVertical = (int) (getContext().getResources()
                .getDimension(PADDING_VERTICAL_DIMEN_ID) + 0.5f);
        setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
    }

    public TabItemView create(@StringRes int titleResId, @DrawableRes int iconResId) {
        removeAllViews();
        addIcon(iconResId);
        addTitle(titleResId);
        return this;
    }

    public String getTitle() {
        return titleView != null ? titleView.getText().toString() : "";
    }

    private void addIcon(@DrawableRes int iconResId) {
        iconView = new ImageView(getContext());
        iconView.setImageResource(iconResId);
        iconView.setScaleType(ImageView.ScaleType.FIT_XY);
//        int width = dp2px(getContext(), ICON_WIDTH_DP);
//        int height = dp2px(getContext(), ICON_HEIGHT_DP);
        int width = (int) (getContext().getResources().getDimension(ICON_WIDTH_DIMEN_ID) + 0.5f);
        int height = (int) (getContext().getResources().getDimension(ICON_HEIGHT_DIMEN_ID) + 0.5f);
        LayoutParams layoutParams = new LayoutParams(width, height);
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
        addView(iconView, layoutParams);
    }

    private void addTitle(@StringRes int titleResId) {
        titleView = new TextView(getContext());
        titleView.setText(titleResId);
        titleView.setTextColor(UNSELECTED_TEXTCOLOR);
        int textSize = (int) (getContext().getResources()
                .getDimension(TEXTSIZE_DIMEN_ID) + 0.5f);
        titleView.setTextSize(TEXTSIZE_UNIT, textSize);
        titleView.setGravity(Gravity.CENTER);
        LayoutParams layoutParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
//        layoutParams.leftMargin = dp2px(getContext(), 6);
        layoutParams.leftMargin = (int) (getContext().getResources()
                .getDimension(TITLE_LEFT_MARGIN_DIMEN_ID) + 0.5f);
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
        addView(titleView, layoutParams);
    }

    public static int dp2px(Context context, float dp) {
        float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

    public void setSecletedEffect(boolean secleted) {
        if (secleted) {
            titleView.setTextColor(SELECTED_TEXTCOLOR);
            scaleUp(this);
        } else {
            titleView.setTextColor(UNSELECTED_TEXTCOLOR);
            scaleDown(this);
        }
    }

    private void scaleUp(View view) {
        if (view == null) return;
        AnimatorSet animSet = new AnimatorSet();
        float[] values = new float[]{1.0f, 1.2f};
        animSet.playTogether(ObjectAnimator.ofFloat(view, "scaleX", values),
                ObjectAnimator.ofFloat(view, "scaleY", values));
        animSet.setDuration(10).start();
    }

    private void scaleDown(View view) {
        if (view == null) return;
        AnimatorSet animSet = new AnimatorSet();
        float[] values = new float[]{1.2f, 1.0f};
        animSet.playTogether(ObjectAnimator.ofFloat(view, "scaleX", values),
                ObjectAnimator.ofFloat(view, "scaleY", values));
        animSet.setDuration(10).start();
    }

}
