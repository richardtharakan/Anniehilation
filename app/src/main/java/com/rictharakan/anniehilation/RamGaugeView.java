
package com.rictharakan.anniehilation;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Locale;

public class RamGaugeView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();
    private final Path needlePath = new Path();

    private final int glass = Color.parseColor("#182D4D");
    private final int cyan = Color.parseColor("#42F5FF");
    private final int blue = Color.parseColor("#318BFF");
    private final int violet = Color.parseColor("#A76BFF");
    private final int muted = Color.parseColor("#A9C1E8");
    private final int white = Color.parseColor("#F4FBFF");

    // Warm preview colours.
    private final int warmNeedle = Color.parseColor("#FF5733");
    private final int warmGlow = Color.parseColor("#FFFF5722");

    // Existing cool needle colours.
    private final int coolNeedle = Color.parseColor("#42F5FF");
    private final int coolGlow = Color.parseColor("#DC2DDCFF");

    private float usageFraction = 0f;
    private float colourProgress = 0f;

    private ValueAnimator animator;
    private ValueAnimator colourAnimator;

    private String displayLabel = "RAM LOAD PREVIEW";

    public RamGaugeView(Context context) {
        super(context);
        init();
    }

    public RamGaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RamGaugeView(
            Context context,
            AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    /**
     * Show actual measured RAM usage and return to the cool needle colour.
     */
    public void setUsageFraction(float fraction) {
        displayLabel = "RAM IN USE";
        animateTo(fraction);
        animateNeedleColour(1f);
    }

    /**
     * Show a clearly labelled visual preview and warm needle colour.
     * Actual RAM readings are provided separately by MainActivity.
     */
    public void setStartupPreviewFraction(
            float actualFraction,
            float multiplier
    ) {
        float actual = clamp(actualFraction);
        float target = Math.min(0.99f, actual * multiplier);

        displayLabel = "RAM LOAD PREVIEW";
        animateTo(Math.max(actual, target));
        animateNeedleColour(0f);
    }

    private float clamp(float fraction) {
        return Math.max(0f, Math.min(1f, fraction));
    }

    private void animateTo(float fraction) {
        float target = clamp(fraction);

        if (animator != null) {
            animator.cancel();
        }

        animator = ValueAnimator.ofFloat(usageFraction, target);
        animator.setDuration(1500);
        animator.setInterpolator(new DecelerateInterpolator());

        animator.addUpdateListener(animation -> {
            usageFraction = (float) animation.getAnimatedValue();
            invalidate();
        });

        animator.start();
    }

    private void animateNeedleColour(float target) {
        if (colourAnimator != null) {
            colourAnimator.cancel();
        }

        colourAnimator = ValueAnimator.ofFloat(
                colourProgress,
                clamp(target)
        );

        colourAnimator.setDuration(800);
        colourAnimator.setInterpolator(new DecelerateInterpolator());

        colourAnimator.addUpdateListener(animation -> {
            colourProgress = (float) animation.getAnimatedValue();
            invalidate();
        });

        colourAnimator.start();
    }

    private int blendColour(
            int startColour,
            int endColour,
            float fraction
    ) {
        return Color.rgb(
                Math.round(
                        Color.red(startColour)
                                + (Color.red(endColour)
                                - Color.red(startColour)) * fraction
                ),
                Math.round(
                        Color.green(startColour)
                                + (Color.green(endColour)
                                - Color.green(startColour)) * fraction
                ),
                Math.round(
                        Color.blue(startColour)
                                + (Color.blue(endColour)
                                - Color.blue(startColour)) * fraction
                )
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();

        if (w <= 0f || h <= 0f) {
            return;
        }

        float cx = w / 2f;
        float radius = Math.min(w * 0.36f, h * 0.58f);
        float cy = Math.min(h * 0.72f, radius + h * 0.12f);
        float stroke = Math.max(8f, Math.min(15f, w * 0.025f));

        int needleColour = blendColour(
                warmNeedle, coolNeedle, colourProgress
        );

        int needleGlowColour = blendColour(
                warmGlow, coolGlow, colourProgress
        );

        arcBounds.set(
                cx - radius,
                cy - radius,
                cx + radius,
                cy + radius
        );

        // Subtle blue ambient glow.
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(
                cx, cy, radius * 1.25f,
                new int[]{
                        Color.argb(48, 42, 126, 255),
                        Color.argb(20, 105, 68, 255),
                        Color.TRANSPARENT
                },
                null,
                Shader.TileMode.CLAMP
        ));

        canvas.drawCircle(cx, cy, radius * 1.25f, paint);
        paint.setShader(null);

        // Outer translucent glass ring.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke * 2.2f);
        paint.setColor(Color.argb(30, 125, 190, 255));
        paint.setShadowLayer(
                14f, 0f, 0f,
                Color.argb(70, 67, 155, 255)
        );

        canvas.drawArc(arcBounds, 180f, 180f, false, paint);
        paint.clearShadowLayer();

        // Dark glass base arc.
        paint.setStrokeWidth(stroke * 1.5f);
        paint.setColor(glass);
        canvas.drawArc(arcBounds, 180f, 180f, false, paint);

        // Fine inner glass highlight.
        paint.setStrokeWidth(1.5f);
        paint.setColor(Color.argb(100, 180, 220, 255));
        canvas.drawArc(arcBounds, 183f, 174f, false, paint);

        // Neon gradient arc.
        paint.setStrokeWidth(stroke);
        paint.setShader(new LinearGradient(
                cx - radius, cy,
                cx + radius, cy,
                new int[]{violet, blue, cyan},
                null,
                Shader.TileMode.CLAMP
        ));

        paint.setShadowLayer(
                18f, 0f, 0f,
                Color.argb(150, 62, 170, 255)
        );

        canvas.drawArc(arcBounds, 180f, 180f, false, paint);
        paint.clearShadowLayer();
        paint.setShader(null);

        // Slim highlight along the neon arc.
        paint.setStrokeWidth(Math.max(1.5f, stroke * 0.16f));
        paint.setColor(Color.argb(210, 225, 250, 255));
        canvas.drawArc(arcBounds, 183f, 174f, false, paint);

        // Tick marks.
        float innerRadius = radius - stroke * 1.55f;
        float outerRadius = radius - stroke * 0.45f;

        for (int i = 0; i <= 10; i++) {
            float angle = (float) Math.toRadians(180f + i * 18f);

            float x1 = cx + (float) Math.cos(angle) * innerRadius;
            float y1 = cy + (float) Math.sin(angle) * innerRadius;
            float x2 = cx + (float) Math.cos(angle) * outerRadius;
            float y2 = cy + (float) Math.sin(angle) * outerRadius;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(i % 5 == 0 ? 2.5f : 1.5f);
            paint.setColor(i % 5 == 0 ? cyan : muted);
            paint.setAlpha(i % 5 == 0 ? 230 : 145);

            canvas.drawLine(x1, y1, x2, y2, paint);
            paint.setAlpha(255);
        }

        // Main RAM percentage — unchanged.
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(
                cx - radius * 0.4f, cy - radius * 0.2f,
                cx + radius * 0.4f, cy + radius * 0.2f,
                new int[]{white, cyan, violet},
                null,
                Shader.TileMode.CLAMP
        ));

        paint.setTypeface(android.graphics.Typeface.create(
                "sans-serif",
                android.graphics.Typeface.BOLD
        ));

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Math.min(w * 0.105f, 40f));

        canvas.drawText(
                String.format(
                        Locale.getDefault(),
                        "%.0f%%",
                        usageFraction * 100f
                ),
                cx,
                cy + radius * 0.40f,
                paint
        );

        paint.setShader(null);

        // Gauge label.
        paint.setColor(muted);
        paint.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium",
                android.graphics.Typeface.NORMAL
        ));

        paint.setTextSize(Math.min(w * 0.032f, 12f));

        canvas.drawText(
                displayLabel,
                cx,
                cy + radius * 0.58f,
                paint
        );

        // Needle: left = 0%, right = 100%.
        float angle = (float) Math.toRadians(
                180f + usageFraction * 180f
        );

        float needleLength = radius * 0.72f;
        float needleWidth = Math.max(5f, stroke * 0.48f);

        float nx = cx + (float) Math.cos(angle) * needleLength;
        float ny = cy + (float) Math.sin(angle) * needleLength;

        float px = (float) Math.cos(angle + Math.PI / 2f);
        float py = (float) Math.sin(angle + Math.PI / 2f);

        needlePath.reset();

        needlePath.moveTo(
                cx + px * needleWidth,
                cy + py * needleWidth
        );

        needlePath.lineTo(nx, ny);

        needlePath.lineTo(
                cx - px * needleWidth,
                cy - py * needleWidth
        );

        needlePath.close();

        // Needle glow transitions with the needle colour.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(needleWidth * 1.5f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(needleColour);
        paint.setShadowLayer(15f, 0f, 0f, needleGlowColour);

        canvas.drawLine(cx, cy, nx, ny, paint);
        paint.clearShadowLayer();

        // Glassy filled needle.
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(
                cx, cy, nx, ny,
                new int[]{needleColour, blue, violet},
                null,
                Shader.TileMode.CLAMP
        ));

        canvas.drawPath(needlePath, paint);
        paint.setShader(null);

        // Needle edge highlight.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.3f);
        paint.setColor(Color.argb(210, 235, 252, 255));

        canvas.drawPath(needlePath, paint);

        // Central glass hub.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(70, 75, 185, 255));
        paint.setShadowLayer(
                16f, 0f, 0f,
                Color.argb(210, 54, 150, 255)
        );

        canvas.drawCircle(cx, cy, stroke * 1.25f, paint);
        paint.clearShadowLayer();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke * 0.23f);
        paint.setColor(cyan);

        canvas.drawCircle(cx, cy, stroke * 0.92f, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#142544"));

        canvas.drawCircle(cx, cy, stroke * 0.62f, paint);

        paint.setColor(white);

        canvas.drawCircle(
                cx - stroke * 0.16f,
                cy - stroke * 0.18f,
                stroke * 0.15f,
                paint
        );

        // RAM endpoint labels: enlarged to 24 px.
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(white);

        paint.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium",
                android.graphics.Typeface.BOLD
        ));

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(24f);

        canvas.drawText(
                "0%",
                cx - radius * 0.91f,
                cy + radius * 0.20f,
                paint
        );

        canvas.drawText(
                "100%",
                cx + radius * 0.91f,
                cy + radius * 0.20f,
                paint
        );

        // Reset drawing state.
        paint.setStyle(Paint.Style.STROKE);
        paint.setShader(null);
        paint.clearShadowLayer();
    }
}
