package com.rictharakan.anniehilation;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class CooldownShimmerView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF buttonRect = new RectF();
    private final RectF lightRect = new RectF();
    private final Path clipPath = new Path();

    private ValueAnimator animator;
    private float progress = 0f;

    public CooldownShimmerView(Context context) {
        super(context);
        init();
    }

    public CooldownShimmerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CooldownShimmerView(
            Context context,
            AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (animator != null) {
            animator.cancel();
        }

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1800);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());

        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });

        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }

        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();

        if (width <= 0 || height <= 0) {
            return;
        }

        // Match the rounded shape of the button background.
        float radius = 20f * getResources()
                .getDisplayMetrics().density;

        buttonRect.set(0f, 0f, width, height);
        clipPath.reset();
        clipPath.addRoundRect(
                buttonRect,
                radius,
                radius,
                Path.Direction.CW
        );

        canvas.save();
        canvas.clipPath(clipPath);

        // Position the light streak as it glides from left to right.
        float bandWidth = width * 0.24f;
        float centerX = -bandWidth
                + (width + bandWidth * 2f) * progress;

        canvas.save();
        canvas.rotate(-12f, centerX, height / 2f);

        lightRect.set(
                centerX - bandWidth / 2f,
                -height,
                centerX + bandWidth / 2f,
                height * 2f
        );

        paint.setShader(new LinearGradient(
                lightRect.left,
                0f,
                lightRect.right,
                0f,
                new int[]{
                        0x0048EBFF,
                        0x3348EBFF,
                        0xAAFFFFFF,
                        0x6648EBFF,
                        0x0048EBFF
                },
                new float[]{0f, 0.28f, 0.5f, 0.72f, 1f},
                Shader.TileMode.CLAMP
        ));

        canvas.drawRoundRect(
                lightRect,
                radius,
                radius,
                paint
        );

        paint.setShader(null);

        canvas.restore();
        canvas.restore();
    }
}
