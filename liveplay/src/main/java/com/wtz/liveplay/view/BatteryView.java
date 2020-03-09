package com.wtz.liveplay.view;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.BatteryManager;
import android.util.AttributeSet;
import android.view.View;

import com.wtz.liveplay.R;

import java.text.DecimalFormat;


public class BatteryView extends View {

    private static final int DEFAULT_BODY_HEIGHT_DIMEN_ID = R.dimen.dp_16;

    private float mHeadWidth;// 电池头宽度
    private float mHeadHeight;// 电池头高度

    private float mBodyWidth;// 电池体宽度
    private float mBodyHeight;// 电池体高度
    private float mBodyBorder;// 电池外框的宽度
    private float mBodyPadding;// 电池内芯与边框的距离
    private float mBodyRadius;// 外框圆角
    private float mBodyCenterX;
    private float mBodyCenterY;

    private float mFullPowerWidth;// 电池内芯最大宽度

    private float mChargeTriangleWidth;// 充电标志单个三角形宽度
    private float mChargeTriangleHeight;// 充电标志单个三角形高度

    private float mPowerTextSize;// 电池百分比字体大小
    private float mPowerTextMarginBottom;// 电池百分比离底部距离
    private float mPowerTextWidth;
    private float mPowerTextX;
    private float mPowerTextX1;
    private float mPowerTextX2;
    private float mPowerTextY;

    private RectF mHeadRect;
    private RectF mBodyRect;
    private RectF mPowerRect;
    private Path mChargePath1;
    private Path mChargePath2;

    private Paint mHeadPaint;
    private Paint mBodyPaint;
    private Paint mPowerPaint;
    private Paint mChargePaint;

    private Paint mPowerTextPaint;

    private static final int COLOR_LEVEL_1 = Color.RED;
    private static final int COLOR_LEVEL_2 = Color.parseColor("#FFD700");
    private static final int COLOR_LEVEL_3 = Color.parseColor("#BFFF16");
    private static final int COLOR_LEVEL_4 = Color.GREEN;
    private static final int COLOR_CHARGE = Color.parseColor("#FFA500");

    private boolean showPowerPercent = true;
    private boolean mIsCharging;// 是否在充电
    private float mPower;// 当前电量
    private static final DecimalFormat POWER_FORMAT = new DecimalFormat("0%");

    public BatteryView(Context context) {
        this(context, null);
    }

    public BatteryView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BatteryView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BatteryViewAttrs);
        showPowerPercent = a.getBoolean(R.styleable.BatteryViewAttrs_show_percent, true);
        float defaultHeight = getContext().getResources().getDimension(DEFAULT_BODY_HEIGHT_DIMEN_ID) + 0.5f;
        mBodyHeight = a.getDimension(R.styleable.BatteryViewAttrs_battery_height, defaultHeight);
        a.recycle();

        initView();
    }

    private void initView() {
        // 初始化各个参数大小
        mHeadHeight = mBodyHeight * 0.1875f;
        mHeadWidth = mHeadHeight * 0.6f;

        mBodyWidth = mBodyHeight * 1.8125f;
        mBodyBorder = mBodyHeight * 0.125f;
        mBodyPadding = mBodyBorder * 0.5f;
        mBodyRadius = mBodyBorder;

        mChargeTriangleWidth = mBodyWidth * 0.275f;
        mChargeTriangleHeight = mBodyHeight * 0.25f;

        mPowerTextSize = mBodyHeight * 0.9375f;
        mPowerTextMarginBottom = mPowerTextSize * 0.1333f;

        // 电池头区域
        mHeadRect = new RectF(0, (mBodyHeight - mHeadHeight) / 2,
                mHeadWidth, (mBodyHeight + mHeadHeight) / 2);

        // 电池体区域
        float bodyLeft = mHeadRect.width() + mBodyBorder / 2;
        float bodyTop = mBodyBorder / 2;
        float bodyRight = mHeadRect.width() + mBodyWidth - mBodyBorder / 2;
        float bodyBottom = mBodyHeight - mBodyBorder / 2;
        mBodyRect = new RectF(bodyLeft, bodyTop, bodyRight, bodyBottom);
        mBodyCenterX = mBodyRect.left + mBodyRect.width() / 2;
        mBodyCenterY = mBodyRect.top + mBodyRect.height() / 2;

        // 电池内芯区域
        mFullPowerWidth = mBodyWidth - mBodyBorder * 2 - mBodyPadding * 2;
        float powerRight = mBodyRect.right - mBodyBorder / 2 - mBodyPadding;
        float powerLeft = powerRight - mFullPowerWidth;
        float powerTop = mBodyRect.top + mBodyBorder / 2 + mBodyPadding;
        float powerBottom = mBodyRect.bottom - mBodyBorder / 2 - mBodyPadding;
        mPowerRect = new RectF(powerLeft, powerTop, powerRight, powerBottom);

        // 充电标志区域
        mChargePath1 = new Path();
        mChargePath1.moveTo(
                mBodyCenterX - mChargeTriangleWidth,
                mBodyCenterY - mChargeTriangleHeight / 4);
        mChargePath1.lineTo(
                mBodyCenterX,
                mBodyCenterY - mChargeTriangleHeight / 4);
        mChargePath1.lineTo(
                mBodyCenterX,
                mBodyCenterY + 3.0f * mChargeTriangleHeight / 4);
        mChargePath1.close();

        mChargePath2 = new Path();
        mChargePath2.moveTo(
                mBodyCenterX + mChargeTriangleWidth,
                mBodyCenterY + mChargeTriangleHeight / 4);
        mChargePath2.lineTo(
                mBodyCenterX,
                mBodyCenterY + mChargeTriangleHeight / 4);
        mChargePath2.lineTo(
                mBodyCenterX,
                mBodyCenterY - 3.0f * mChargeTriangleHeight / 4);
        mChargePath2.close();

        // 电池头画笔
        mHeadPaint = new Paint();
        mHeadPaint.setStyle(Paint.Style.FILL);
        mHeadPaint.setColor(Color.GRAY);

        // 电池体画笔
        mBodyPaint = new Paint();
        mBodyPaint.setStyle(Paint.Style.STROKE);// 空心矩形
        mBodyPaint.setStrokeWidth(mBodyBorder);// 边框宽度
        mBodyPaint.setColor(Color.GRAY);

        // 电池内芯画笔
        mPowerPaint = new Paint();
        mPowerPaint.setStyle(Paint.Style.FILL);

        // 充电标志画笔
        mChargePaint = new Paint();
        mChargePaint.setStyle(Paint.Style.FILL);
        mChargePaint.setColor(COLOR_CHARGE);

        // 电池百分比画笔
        mPowerTextPaint = new Paint();
        mPowerTextPaint.setTextSize(mPowerTextSize);
        mPowerTextPaint.setColor(Color.GRAY);
        mPowerTextPaint.setAntiAlias(true);

        // 电池百分比区域，用到 mPowerTextPaint 放在最后边
        mPowerTextX1 = mBodyRect.right + mBodyBorder / 2;
        mPowerTextX2 = mPowerTextX1 + mPowerTextPaint.measureText("1");
        mPowerTextX = mPowerTextX1;
        mPowerTextY = mBodyRect.bottom - mPowerTextMarginBottom;
        mPowerTextWidth = mPowerTextPaint.measureText("100%");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 画电池头
        canvas.drawRect(mHeadRect, mHeadPaint);

        // 画电池外框
        canvas.drawRoundRect(mBodyRect, mBodyRadius, mBodyRadius, mBodyPaint);

        // 画电池芯
        if (mPower <= 0.15) {
            mPowerPaint.setColor(COLOR_LEVEL_1);
        } else if (mPower <= 0.35) {
            mPowerPaint.setColor(COLOR_LEVEL_2);
        } else if (mPower <= 0.65) {
            mPowerPaint.setColor(COLOR_LEVEL_3);
        } else {
            mPowerPaint.setColor(COLOR_LEVEL_4);
        }
        mPowerRect.left = mPowerRect.right - mPower * mFullPowerWidth;
        canvas.drawRect(mPowerRect, mPowerPaint);

        // 画充电标志
        if (mIsCharging) {
            canvas.drawPath(mChargePath1, mChargePaint);
            canvas.drawPath(mChargePath2, mChargePaint);
        }

        if (showPowerPercent) {
            if (mPower >= 1) {
                mPowerTextX = mPowerTextX1;
            } else {
                mPowerTextX = mPowerTextX2;
            }
            canvas.drawText(POWER_FORMAT.format(mPower), mPowerTextX, mPowerTextY, mPowerTextPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width;
        if (showPowerPercent) {
            width = (int) (mHeadWidth + mBodyWidth + mPowerTextWidth);
        } else {
            width = (int) (mHeadWidth + mBodyWidth);
        }
        setMeasuredDimension(width, (int) mBodyHeight);
    }

    public void showPowerPercent(boolean show) {
        this.showPowerPercent = show;
        requestLayout();
        invalidate();
    }

    private void setPower(float power) {
        mPower = power;
        invalidate();
    }

    private BroadcastReceiver mPowerConnectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            mIsCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;

            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            setPower(((float) level) / scale);
        }
    };

    @Override
    protected void onAttachedToWindow() {
        getContext().registerReceiver(mPowerConnectionReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        getContext().unregisterReceiver(mPowerConnectionReceiver);
        super.onDetachedFromWindow();
    }

}
