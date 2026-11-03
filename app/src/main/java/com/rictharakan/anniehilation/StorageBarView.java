package com.rictharakan.anniehilation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

public class StorageBarView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private String heading = "PHONE STORAGE";
    private String message = "Reading storage...";
    private long usedBytes, totalBytes;
    private boolean available;

    private final int cyan = Color.rgb(72, 235, 255);
    private final int violet = Color.rgb(154, 103, 255);
    private final int bright = Color.rgb(238, 247, 255);
    private final int muted = Color.rgb(172, 199, 228);

    public StorageBarView(Context c) { super(c); density = c.getResources().getDisplayMetrics().density; init(); }
    public StorageBarView(Context c, AttributeSet a) { super(c, a); density = c.getResources().getDisplayMetrics().density; init(); }
    public StorageBarView(Context c, AttributeSet a, int s) { super(c, a, s); density = c.getResources().getDisplayMetrics().density; init(); }

    private void init() { setLayerType(View.LAYER_TYPE_SOFTWARE, null); }

    public void setStorageInfo(String title, long used, long total) {
        heading = title;
        usedBytes = Math.max(0L, used);
        totalBytes = Math.max(0L, total);
        available = totalBytes > 0L;
        message = available ? "" : "Storage information unavailable";
        invalidate();
    }

    public void setUnavailable(String title, String reason) {
        heading = title;
        message = reason;
        usedBytes = totalBytes = 0L;
        available = false;
        invalidate();
    }

    private void drawText(Canvas c, String s, float x, float y, float size,
                          int color, boolean bold, Paint.Align align) {
        p.reset();
        p.setAntiAlias(true);
        p.setTextSize(size);
        p.setColor(color);
        p.setTextAlign(align);
        p.setTypeface(Typeface.create("sans-serif",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        c.drawText(s, x, y, p);
    }

    private String bytes(long n) {
        if (n < 1024L) return n + " B";
        double v = n / 1024.0;
        if (v < 1024.0) return String.format(Locale.getDefault(), "%.1f KB", v);
        v /= 1024.0;
        if (v < 1024.0) return String.format(Locale.getDefault(), "%.1f MB", v);
        v /= 1024.0;
        return String.format(Locale.getDefault(), "%.2f GB", v);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();
        canvas.scale(density, density);
        float w = getWidth() / density;
        float h = getHeight() / density;
        if (w <= 0 || h <= 0) {
            canvas.restore();
            return;
        }

        float left = 5f, right = w - 5f, pad = 18f;

        p.reset();
        p.setAntiAlias(true);
        p.setColor(Color.argb(118, 15, 25, 57));
        p.setStyle(Paint.Style.FILL);
        p.setShadowLayer(13f, 0f, 4f, Color.argb(65, 77, 142, 255));
        canvas.drawRoundRect(new RectF(left, 4f, right, h - 4f), 22f, 22f, p);
        p.clearShadowLayer();

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f);
        p.setColor(Color.argb(125, 145, 199, 255));
        canvas.drawRoundRect(new RectF(left, 4f, right, h - 4f), 22f, 22f, p);

        p.setShader(new LinearGradient(left, 0, right, 0,
                new int[]{Color.TRANSPARENT, Color.argb(75, 220, 248, 255),
                        Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
        canvas.drawLine(left + 20f, 7f, right - 20f, 7f, p);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);

        drawText(canvas, heading, pad + 2f, 29f, 15f, cyan, true, Paint.Align.LEFT);

        if (!available) {
            drawText(canvas, message, pad + 2f, 58f, 14f, muted, false, Paint.Align.LEFT);
            canvas.restore();
            return;
        }

        long free = Math.max(0L, totalBytes - usedBytes);
        float fraction = Math.max(0f, Math.min(1f,
                (float) usedBytes / (float) totalBytes));

        drawText(canvas, String.format(Locale.getDefault(), "%.0f%% USED", fraction * 100f),
                right - pad, 29f, 14f, bright, true, Paint.Align.RIGHT);

        float barLeft = pad, barRight = right - pad, barTop = 43f, barBottom = 55f;
        p.setColor(Color.argb(130, 71, 94, 132));
        canvas.drawRoundRect(new RectF(barLeft, barTop, barRight, barBottom), 7f, 7f, p);

        if (fraction > 0f) {
            p.setShader(new LinearGradient(barLeft, barTop, barRight, barBottom,
                    cyan, violet, Shader.TileMode.CLAMP));
            p.setShadowLayer(8f, 0f, 0f, Color.argb(120, 67, 207, 255));
            canvas.drawRoundRect(new RectF(barLeft, barTop,
                    barLeft + (barRight - barLeft) * fraction, barBottom), 7f, 7f, p);
            p.clearShadowLayer();
            p.setShader(null);
        }

        float third = (barRight - barLeft) / 3f;
        drawText(canvas, "USED", barLeft, 77f, 11f, muted, true, Paint.Align.LEFT);
        drawText(canvas, "FREE", barLeft + third, 77f, 11f, muted, true, Paint.Align.LEFT);
        drawText(canvas, "TOTAL", barRight, 77f, 11f, muted, true, Paint.Align.RIGHT);

        drawText(canvas, bytes(usedBytes), barLeft, 96f, 13f, bright, true, Paint.Align.LEFT);
        drawText(canvas, bytes(free), barLeft + third, 96f, 13f, bright, true, Paint.Align.LEFT);
        drawText(canvas, bytes(totalBytes), barRight, 96f, 13f, bright, true, Paint.Align.RIGHT);

        canvas.restore();
    }
}
