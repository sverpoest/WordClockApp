package com.sebastiaan.wordclock;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.AppCompatButton;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

/*
https://android.jlelse.eu/make-your-app-shine-how-to-make-a-button-morph-into-a-loading-spinner-9efee6e39711
 */

public class ConnectionButton extends AppCompatButton
{
    private enum State {
        PROGRESS, IDLE
    }

    private State mState = State.IDLE;
    private Boolean mIsMorphingInProgress = false;
    private GradientDrawable mGradientDrawable;
    private CircularAnimatedDrawable mAnimatedDrawable;
    private AnimatorSet mAnimatorSet;
    private int mInitialWidth;


    public ConnectionButton(Context context)
    {
        super(context);
        init(context);
    }

    public ConnectionButton(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init(context);
    }

    public ConnectionButton(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context)
    {
        mGradientDrawable = (GradientDrawable)
                ContextCompat.getDrawable(context, R.drawable.button_shape);

        setBackground(mGradientDrawable);
    }

    public void startAnimation()
    {
        if(mState  != State.IDLE)
            return;

        mInitialWidth = getWidth();
        int initialHeight = getHeight();

        int initialCornerRadius = 0;
        int finalCornerRadius = 1000;

        mState = State.PROGRESS;
        mIsMorphingInProgress = true;

        this.setText(null);
        setClickable(false);

        int toWidth = initialHeight;

        ObjectAnimator cornerAnimation = ObjectAnimator.ofFloat(mGradientDrawable,
                "cornerRadius", initialCornerRadius, finalCornerRadius);

        ValueAnimator widthAnimator = ValueAnimator.ofInt(mInitialWidth, toWidth);
        widthAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener()
        {
            @Override
            public void onAnimationUpdate(ValueAnimator animation)
            {
                int val = (Integer) animation.getAnimatedValue();
                ViewGroup.LayoutParams layoutParams = getLayoutParams();
                layoutParams.width = val;
                setLayoutParams(layoutParams);
            }
        });

        mAnimatorSet = new AnimatorSet();
        mAnimatorSet.setDuration(300);
        mAnimatorSet.playTogether(widthAnimator);

        mAnimatorSet.addListener(new AnimatorListenerAdapter()
        {
            @Override
            public void onAnimationEnd(Animator animation)
            {
                super.onAnimationEnd(animation);
                mIsMorphingInProgress = false;
            }
        });

        mAnimatorSet.start();
    }

    public void startReverseAnimation(final String finalText)
    {
        if(mState == State.IDLE)
        {
            setText(finalText);
            return;
        }
        int initialWidth = getWidth();

        int initialCornerRadius = 1000;
        int finalCornerRadius = 0;
        ObjectAnimator cornerAnimation = ObjectAnimator.ofFloat(mGradientDrawable,
                "cornerRadius", initialCornerRadius, finalCornerRadius);

        ValueAnimator widthAnimator = ValueAnimator.ofInt(initialWidth, mInitialWidth);
        widthAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener()
        {
            @Override
            public void onAnimationUpdate(ValueAnimator animation)
            {
                int val = (Integer) animation.getAnimatedValue();
                ViewGroup.LayoutParams layoutParams = getLayoutParams();
                layoutParams.width = val;
                setLayoutParams(layoutParams);
            }
        });

        if(mAnimatorSet != null && mAnimatorSet.isRunning())
            mAnimatorSet.cancel();

        mIsMorphingInProgress = true;
        mAnimatorSet = new AnimatorSet();
        mAnimatorSet.setDuration(300);
        mAnimatorSet.playTogether( widthAnimator);

        mAnimatorSet.addListener(new AnimatorListenerAdapter()
        {
            @Override
            public void onAnimationEnd(Animator animation)
            {
                super.onAnimationEnd(animation);
                setText(finalText);
                mState = State.IDLE;
                setClickable(true);
            }
        });

        mAnimatorSet.start();
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);

        if(mState == State.PROGRESS && !mIsMorphingInProgress)
            drawIndeterminateProgress(canvas);
    }

    private void drawIndeterminateProgress(Canvas canvas)
    {
        if(mAnimatedDrawable == null || !mAnimatedDrawable.isRunning())
        {
            int arcWidth = 5;
            mAnimatedDrawable = new CircularAnimatedDrawable(this, arcWidth, Color.WHITE);

            int offset = 15;

            int left = offset;
            int right = getWidth() - offset;
            int bottom = getHeight() - offset;
            int top = offset;

            mAnimatedDrawable.setBounds(left, top, right, bottom);
            mAnimatedDrawable.setCallback(this);
            mAnimatedDrawable.start();
        }
        else
            mAnimatedDrawable.draw(canvas);
    }

    public class CircularAnimatedDrawable extends Drawable implements Animatable
    {
        private ValueAnimator mValueAnimatorAngle;
        private ValueAnimator mValueAnimatorSweep;
        private final Interpolator ANGLE_INTERPOLATOR = new LinearInterpolator();
        private final Interpolator SWEEP_INTERPOLATOR = new DecelerateInterpolator();
        private static final int ANGLE_ANIMATOR_DURATION = 2000;
        private static final int SWEEP_ANIMATOR_DURATION = 900;
        private final Float MIN_SWEEP_ANGLE = 30f;

        private final RectF fBounds = new RectF();
        private Paint mPaint;
        private View mAnimatedView;

        private float mBorderWidth;
        private float mCurrentGlobalAngle;
        private float mCurrentSweepAngle;
        private float mCurrentGlobalAngleOffset;

        private boolean mModeAppearing;
        private boolean mRunning;

        public CircularAnimatedDrawable(View view, float borderWidth, int arcColor)
        {
            mAnimatedView = view;

            mBorderWidth = borderWidth;

            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(borderWidth);
            mPaint.setColor(arcColor);

            setupAnimations();
        }
        @Override
        public void start()
        {
            if(mRunning)
                return;

            mRunning = true;
            mValueAnimatorAngle.start();
            mValueAnimatorSweep.start();
        }

        @Override
        public void stop()
        {
            if(!mRunning)
                return;

            mRunning = false;
            mValueAnimatorSweep.cancel();
            mValueAnimatorAngle.cancel();
        }

        @Override
        public boolean isRunning()
        {
            return mRunning;
        }

        @Override
        public void draw(@NonNull Canvas canvas)
        {
            float startAngle = mCurrentGlobalAngle - mCurrentGlobalAngleOffset;
            float sweepAngle = mCurrentSweepAngle;
            if (!mModeAppearing) {
                startAngle = startAngle + sweepAngle;
                sweepAngle = 360 - sweepAngle - MIN_SWEEP_ANGLE;
            } else {
                sweepAngle += MIN_SWEEP_ANGLE;
            }

            canvas.drawArc(fBounds, startAngle, sweepAngle, false, mPaint);
        }

        @Override
        public void setAlpha(int alpha)
        {
            mPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter)
        {
            mPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity()
        {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        protected void onBoundsChange(Rect bounds)
        {
            super.onBoundsChange(bounds);
            fBounds.left = bounds.left + mBorderWidth / 2f + .5f;
            fBounds.right = bounds.right - mBorderWidth / 2f - .5f;
            fBounds.top = bounds.top + mBorderWidth / 2f + .5f;
            fBounds.bottom = bounds.bottom - mBorderWidth / 2f - .5f;
        }

        private void setupAnimations()
        {
            mValueAnimatorAngle = ValueAnimator.ofFloat(0, 360f);
            mValueAnimatorAngle.setInterpolator(ANGLE_INTERPOLATOR);
            mValueAnimatorAngle.setDuration(ANGLE_ANIMATOR_DURATION);
            mValueAnimatorAngle.setRepeatCount(ValueAnimator.INFINITE);
            mValueAnimatorAngle.addUpdateListener(new ValueAnimator.AnimatorUpdateListener()
            {
                @Override
                public void onAnimationUpdate(ValueAnimator animation)
                {
                    mCurrentGlobalAngle = (Float) animation.getAnimatedValue();
                    mAnimatedView.invalidate();
                }
            });

            mValueAnimatorSweep = ValueAnimator.ofFloat(0, 360f - 2 * MIN_SWEEP_ANGLE);
            mValueAnimatorSweep.setInterpolator(SWEEP_INTERPOLATOR);
            mValueAnimatorSweep.setDuration(SWEEP_ANIMATOR_DURATION);
            mValueAnimatorSweep.setRepeatCount(ValueAnimator.INFINITE);
            mValueAnimatorSweep.addUpdateListener(new ValueAnimator.AnimatorUpdateListener()
            {
                @Override
                public void onAnimationUpdate(ValueAnimator animation)
                {
                    mCurrentSweepAngle = (Float) animation.getAnimatedValue();
                    invalidateSelf();
                }
            });
            mValueAnimatorSweep.addListener(new AnimatorListenerAdapter()
            {
                @Override
                public void onAnimationRepeat(Animator animation)
                {
                    super.onAnimationRepeat(animation);
                    toggleAppearingMode();
                }
            });
        }

        private void toggleAppearingMode()
        {
            mModeAppearing = !mModeAppearing;
        }
    }
}
