package com.rictharakan.anniehilation;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Locale;

public class ThermometerView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Real battery-temperature reading supplied by Android, in Celsius.
    private float temperatureC = Float.NaN;

    // Visual cooling stage only: 0 = orange, 0.5 = green, 1 = cyan-blue.
    // This is separate from the real temperature reading.
    private float visualCoolingProgress = 0f;
    private ValueAnimator colorAnimator;

    private final int warmColor = Color.parseColor("#FF5733");
    private final int greenColor = Color.parseColor("#55EFA3");
    private final int coolColor = Color.parseColor("#42F5FF");
    private final int glassColor = Color.parseColor("#263C55");

    public ThermometerView(Context context) {
        super(context);
        init();
    }

    public ThermometerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThermometerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    /**
     * Sets the battery temperature received from Android.
     * Pass Float.NaN when Android does not provide a reading.
     */
    public void setTemperatureC(float temperatureC) {
        this.temperatureC = temperatureC;
        invalidate();
    }

    /**
     * Animates the thermometer's visual colour stage.
     * 0.0 = warm orange; 0.5 = green; 1.0 = cool cyan-blue.
     * This does not change or predict the physical battery temperature.
     */
    public void setVisualCoolingProgress(float progress) {
        float target = Math.max(0f, Math.min(1f, progress));

        if (colorAnimator != null) {
            colorAnimator.cancel();
        }

        colorAnimator = ValueAnimator.ofFloat(visualCoolingProgress, target);
        colorAnimator.setDuration(1500L);
        colorAnimator.setInterpolator(new DecelerateInterpolator());
        colorAnimator.addUpdateListener(animation -> {
            visualCoolingProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        colorAnimator.start();
    }

    private int getVisualThermometerColor() {
        ArgbEvaluator evaluator = new ArgbEvaluator();

        if (visualCoolingProgress <= 0.5f) {
            float fraction = visualCoolingProgress * 2f;
            return (Integer) evaluator.evaluate(
                    fraction,
                    warmColor,
                    greenColor
            );
        }

        float fraction = (visualCoolingProgress - 0.5f) * 2f;
        return (Integer) evaluator.evaluate(
                fraction,
                greenColor,
                coolColor
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

        canvas.save();

        float cx = w * 0.50f;
        float bulbRadius = Math.min(w * 0.22f, h * 0.105f);
        float bulbCy = h * 0.81f;

        float tubeWidth = Math.min(
                w * 0.30f,
                13f * getResources().getDisplayMetrics().density
        );
        float tubeTop = h * 0.10f;
        float tubeBottom = bulbCy;
        float tubeLeft = cx - tubeWidth / 2f;
        float tubeRight = cx + tubeWidth / 2f;

        RectF tube = new RectF(tubeLeft, tubeTop, tubeRight, tubeBottom);
        int visualColor = getVisualThermometerColor();

        // Outer glass tube.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#183047"));
        canvas.drawRoundRect(tube, tubeWidth / 2f, tubeWidth / 2f, paint);

        // Tube outline follows the same warm-to-cool animation as the fill.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.4f, w * 0.035f));
        paint.setColor(visualColor);
        canvas.drawRoundRect(tube, tubeWidth / 2f, tubeWidth / 2f, paint);

        // Glass bulb.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(glassColor);
        canvas.drawCircle(cx, bulbCy, bulbRadius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.4f, w * 0.035f));
        paint.setColor(visualColor);
        canvas.drawCircle(cx, bulbCy, bulbRadius, paint);

        // Keep the mercury height based on the actual battery reading;
        // only its colour follows the illustrative cooling animation.
        if (Float.isFinite(temperatureC)) {
            float fraction = Math.max(
                    0f,
                    Math.min(1f, (temperatureC - 15f) / 45f)
            );

            float mercuryTop = tubeBottom
                    - fraction * (tubeBottom - tubeTop - tubeWidth * 0.35f);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(visualColor);

            RectF mercury = new RectF(
                    tubeLeft + tubeWidth * 0.24f,
                    mercuryTop,
                    tubeRight - tubeWidth * 0.24f,
                    tubeBottom - tubeWidth * 0.12f
            );

            canvas.save();
            canvas.clipRect(
                    tubeLeft + 1f,
                    tubeTop + 1f,
                    tubeRight - 1f,
                    tubeBottom
            );
            canvas.drawRoundRect(
                    mercury,
                    tubeWidth * 0.20f,
                    tubeWidth * 0.20f,
                    paint
            );
            canvas.restore();

            canvas.drawCircle(cx, bulbCy, bulbRadius * 0.72f, paint);
        }

        // Numeric reading remains the real reported battery temperature.
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Math.max(8f, w * 0.23f));
        paint.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium",
                android.graphics.Typeface.NORMAL
        ));
        paint.setColor(visualColor);

        String label = Float.isFinite(temperatureC)
                ? String.format(Locale.getDefault(), "%.1f°", temperatureC)
                : "--";
        canvas.drawText(label, cx, h * 0.99f, paint);

        canvas.restore();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (colorAnimator != null) {
            colorAnimator.cancel();
            colorAnimator = null;
        }
        super.onDetachedFromWindow();
    }
}
