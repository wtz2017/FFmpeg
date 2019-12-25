package com.wtz.ffmpeg.view;

import android.animation.FloatEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.wtz.ffmpeg.R;


/**
 * @author: SvenHe(https://github.com/heshiweij/RoundProgress)
 * @des 圆形转圈的 View
 */
public class ProgressRound extends View {
    private static final String TAG = "ProgressRound";

    private int mWidth;
    private int mHeight;

    /**
     * 自定义属性：
     * <p/>
     * 1. 外层圆的颜色 roundColor
     * <p/>
     * 2. 弧形进度圈的颜色 rouncProgressColor
     * <p/>
     * 3. 中间百分比文字的颜色 textColor
     * <p/>
     * 4. 中间百分比文字的大小 textSize
     */
    int roundColor;
    int roundProgressColor;
    int textColor;
    float textSize;

    private float mProgressStrokeWidth = 40f;// 圆环画笔的默认粗细（在 onLayout 会修改）
    private RectF mProgressRect;
    private float mProgress = 0f;
    private final float maxProgress = 1f; // 不可以修改的最大进度值
    private float mLastProgress = -1;
    private ValueAnimator mAnimator;

    private Paint mCirclePaint;
    private Paint mTextPaint;

    public ProgressRound(Context context) {
        this(context, null);
    }

    public ProgressRound(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ProgressRound(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // 初始化属性
        initAttrs(context, attrs, defStyleAttr);

        // 初始化点击事件
        initClickListener();
    }

    /**
     * 初始化属性
     *
     * @param context
     * @param attrs
     * @param defStyleAttr
     */
    private void initAttrs(Context context, AttributeSet attrs, int defStyleAttr) {
        TypedArray a = null;
        try {
            a = context.obtainStyledAttributes(attrs, R.styleable.ProgressRound);
            roundColor = a.getColor(R.styleable.ProgressRound_roundColor, getResources().getColor(android.R.color.holo_orange_dark));
            roundProgressColor = a.getColor(R.styleable.ProgressRound_roundProgressColor, getResources().getColor(android.R.color.holo_red_dark));
            textColor = a.getColor(R.styleable.ProgressRound_textColor, getResources().getColor(android.R.color.holo_blue_dark));
            textSize = a.getDimension(R.styleable.ProgressRound_textSize, 27f);
        } finally {
            a.recycle();
        }
    }

    /**
     * 初始化点击事件
     */
    private void initClickListener() {
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                restartAnimate();
            }
        });
    }

    /**
     * 当开始布局时候调用
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        // 获取总的宽高
        mWidth = getMeasuredWidth();
        mHeight = getMeasuredHeight();

        // 初始化各种值
        initValue();

        // 设置圆环画笔
        setupPaint();

        // 设置文字画笔
        setupTextPaint();
    }

    /**
     * 初始化各种值
     */
    private void initValue() {
        // 画笔的粗细为总宽度的 1 / 15
        mProgressStrokeWidth = mWidth / 15f;
        mProgressRect = new RectF(0 + mProgressStrokeWidth / 2, 0 + mProgressStrokeWidth / 2,
                mWidth - mProgressStrokeWidth / 2, mHeight - mProgressStrokeWidth / 2);
    }

    /**
     * 设置圆环画笔
     */
    private void setupPaint() {
        // 创建圆环画笔
        mCirclePaint = new Paint();
        mCirclePaint.setAntiAlias(true);
        mCirclePaint.setColor(roundColor);
        mCirclePaint.setStyle(Paint.Style.STROKE); // 边框风格
        mCirclePaint.setStrokeWidth(mProgressStrokeWidth);
    }

    /**
     * 设置文字画笔
     */
    private void setupTextPaint() {
        mTextPaint = new Paint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setColor(textColor);
        mTextPaint.setTextSize(textSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 第一步：绘制一个圆环
        mCirclePaint.setStrokeWidth(mProgressStrokeWidth / 3);
        mCirclePaint.setColor(roundColor);
        float cx = mWidth / 2.0f;
        float cy = mHeight / 2.0f;
        float radius = mWidth / 2.0f - mProgressStrokeWidth / 2.0f;
        canvas.drawCircle(cx, cy, radius, mCirclePaint);

        // 第二步：绘制文字
        String text = ((int) (mProgress / maxProgress * 100)) + "%";
        Rect bounds = new Rect();
        mTextPaint.getTextBounds(text, 0, text.length(), bounds);
        // 文字的坐标系是左下角，所以 y 的坐标是 mWidth / 2 + bounds.height() / 2
        canvas.drawText(text, mWidth / 2 - bounds.width() / 2, mHeight / 2 + bounds.height() / 2, mTextPaint);

        // 第三步：绘制动态进度圆环
        mCirclePaint.setDither(true);
        mCirclePaint.setStrokeJoin(Paint.Join.BEVEL);
        mCirclePaint.setStrokeCap(Paint.Cap.ROUND); //  设置笔触为圆形
        mCirclePaint.setStrokeWidth(mProgressStrokeWidth);
        mCirclePaint.setColor(roundProgressColor);
        canvas.drawArc(mProgressRect, -90f, mProgress / maxProgress * 360, false, mCirclePaint);
    }

    /**
     * 重新开启动画
     */
    private void restartAnimate() {
        if (mLastProgress > 0) {
            // 取消动画
            cancelAnimate();
            // 重置进度
            setProgress(0f);
            // 重新开启动画
            runAnimate(mLastProgress);
        }
    }

    /**
     * 设置当前显示的进度条
     *
     * @param progress
     */
    public void setProgress(float progress) {
        mProgress = progress;
        // 使用 postInvalidate 比 Invalidate() 好，线程安全
        postInvalidate();
    }


    /**
     * 开始执行动画
     *
     * @param targetProgress 最终到达的进度
     */
    public void runAnimate(float targetProgress) {
        // 运行之前，先取消上一次动画
        cancelAnimate();

        mLastProgress = targetProgress;

        mAnimator = ValueAnimator.ofObject(new FloatEvaluator(), 0, targetProgress);
        // 设置差值器
        mAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (float) animation.getAnimatedValue();
                setProgress(value);
            }
        });

        mAnimator.setDuration((long) (targetProgress * 100 * 33));
        mAnimator.start();
    }

    /**
     * 取消动画
     */
    public void cancelAnimate() {
        if (mAnimator != null && mAnimator.isRunning()) {
            mAnimator.cancel();
        }
    }

}
