package com.rictharakan.anniehilation;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** First Bengaluru-inspired side-scrolling game level. */
public class GameLevelOne {

    private final List<RectF> platforms = new ArrayList<>();
    /** World-space spike strips used by GameView for rendering and collision checks. */
    private final List<RectF> hazards = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float worldWidth;
    private float viewportWidth;
    private float viewportHeight;
    private float groundY;

    public void configure(float width, float height) {
        viewportWidth = width;
        viewportHeight = height;
        worldWidth = width * 5.4f;
        groundY = height * 0.82f;

        platforms.clear();
        hazards.clear();

        // Ground sections, with gaps between them.
        addPlatform(0f, groundY, width * 1.02f, height * 1.22f);
        addPlatform(width * 1.18f, groundY, width * 2.10f, height * 1.22f);
        addPlatform(width * 2.28f, groundY, width * 3.28f, height * 1.22f);
        addPlatform(width * 3.46f, groundY, worldWidth, height * 1.22f);

        // Raised platforms.
        addPlatform(width * 0.64f, height * 0.66f, width * 1.08f, height * 0.70f);
        addPlatform(width * 1.43f, height * 0.55f, width * 1.83f, height * 0.59f);
        addPlatform(width * 2.02f, height * 0.68f, width * 2.36f, height * 0.72f);
        addPlatform(width * 2.80f, height * 0.58f, width * 3.16f, height * 0.62f);
        addPlatform(width * 3.70f, height * 0.65f, width * 4.12f, height * 0.69f);
        addPlatform(width * 4.48f, height * 0.54f, width * 4.85f, height * 0.58f);

        // Spike strips are placed on solid ground so they can be jumped over.
        // Keep them clear of the opening combat encounter (about 2.40W–3.02W).
        addHazard(width * 0.78f, width * 0.91f);
        addHazard(width * 1.55f, width * 1.68f);
        addHazard(width * 3.62f, width * 3.75f);
        addHazard(width * 4.40f, width * 4.55f);
    }

    private void addHazard(float left, float right) {
        float hazardTop = groundY - viewportHeight * 0.018f;
        float hazardBottom = groundY + viewportHeight * 0.010f;
        hazards.add(new RectF(left, hazardTop, right, hazardBottom));
    }

    private void addPlatform(float left, float top, float right, float bottom) {
        platforms.add(new RectF(left, top, right, bottom));
    }

    public List<RectF> getPlatforms() {
        return Collections.unmodifiableList(platforms);
    }

    /**
     * Returns the spike-strip rectangles in the same world coordinate system
     * as {@link #getPlatforms()}. GameView uses these for drawing, damage, and
     * avoiding checkpoint placement directly beside a trap.
     */
    public List<RectF> getHazards() {
        return Collections.unmodifiableList(hazards);
    }

    public float getWorldWidth() {
        return worldWidth;
    }

    public float getStartingGroundY() {
        return groundY;
    }

    public void drawBackground(Canvas canvas, float cameraX) {
        // Night-sky gradient.
        paint.setShader(new LinearGradient(
                0f, 0f, 0f, viewportHeight,
                GameAssets.SKY_TOP, GameAssets.SKY_BOTTOM,
                Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, viewportWidth, viewportHeight, paint);
        paint.setShader(null);

        // Moon.
        paint.setColor(0xFFE5D8C2);
        canvas.drawCircle(viewportWidth * 0.78f,
                viewportHeight * 0.17f,
                viewportHeight * 0.035f, paint);

        // Distant skyline with parallax scrolling.
        canvas.save();
        canvas.translate(-cameraX * 0.22f, 0f);
        float buildingWidth = viewportWidth * 0.22f;
        int count = (int) (worldWidth / buildingWidth) + 3;

        for (int i = 0; i < count; i++) {
            float left = i * buildingWidth;
            float variation = ((i * 37) % 9) / 20f;
            float top = viewportHeight * (0.30f + variation);

            paint.setColor(i % 2 == 0
                    ? GameAssets.BUILDING_FAR : 0xFF30304C);
            canvas.drawRect(left, top, left + buildingWidth * 0.84f,
                    viewportHeight * 0.84f, paint);

            paint.setColor(GameAssets.WINDOW);
            float windowWidth = Math.max(3f, viewportWidth * 0.008f);
            float windowHeight = Math.max(4f, viewportHeight * 0.018f);

            for (float wx = left + buildingWidth * 0.12f;
                 wx < left + buildingWidth * 0.72f;
                 wx += buildingWidth * 0.22f) {
                for (float wy = top + viewportHeight * 0.07f;
                     wy < viewportHeight * 0.72f;
                     wy += viewportHeight * 0.075f) {
                    if (((int) (wx + wy)) % 3 != 0) {
                        canvas.drawRect(wx, wy,
                                wx + windowWidth, wy + windowHeight, paint);
                    }
                }
            }
        }
        canvas.restore();

        // Elevated Metro-style track and supports.
        canvas.save();
        canvas.translate(-cameraX * 0.45f, 0f);
        paint.setColor(0xFF343B4A);
        canvas.drawRect(0f, viewportHeight * 0.40f,
                worldWidth, viewportHeight * 0.425f, paint);
        paint.setColor(0xFF747A83);
        canvas.drawRect(0f, viewportHeight * 0.425f,
                worldWidth, viewportHeight * 0.435f, paint);
        paint.setColor(0xFF343B4A);
        for (float x = viewportWidth * 0.1f;
             x < worldWidth; x += viewportWidth * 0.72f) {
            canvas.drawRect(x, viewportHeight * 0.435f,
                    x + viewportWidth * 0.045f, viewportHeight * 0.82f, paint);
        }
        canvas.restore();
    }

    public void drawWorld(Canvas canvas) {
        // Fictional shopfronts.
        for (float baseX = viewportWidth * 0.10f;
             baseX < worldWidth; baseX += viewportWidth * 0.95f) {
            float shopTop = viewportHeight * 0.56f;

            paint.setColor(0xFF282B3A);
            canvas.drawRect(baseX, shopTop,
                    baseX + viewportWidth * 0.40f, groundY, paint);

            paint.setColor(0xFF4C3B43);
            canvas.drawRect(baseX - viewportWidth * 0.02f, shopTop,
                    baseX + viewportWidth * 0.42f,
                    shopTop + viewportHeight * 0.07f, paint);

            paint.setColor(0xFF91B5B0);
            canvas.drawRect(baseX + viewportWidth * 0.045f,
                    shopTop + viewportHeight * 0.12f,
                    baseX + viewportWidth * 0.16f,
                    groundY - viewportHeight * 0.04f, paint);
            canvas.drawRect(baseX + viewportWidth * 0.22f,
                    shopTop + viewportHeight * 0.12f,
                    baseX + viewportWidth * 0.34f,
                    groundY - viewportHeight * 0.04f, paint);

            paint.setColor(GameAssets.GOLD);
            paint.setTextSize(viewportHeight * 0.026f);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            canvas.drawText("COFFEE / BOOKS",
                    baseX + viewportWidth * 0.015f,
                    shopTop + viewportHeight * 0.05f, paint);
        }

        // Ground and raised platform surfaces.
        for (RectF platform : platforms) {
            if (platform.top >= groundY - 1f) {
                paint.setColor(GameAssets.GROUND);
                canvas.drawRect(platform, paint);
                paint.setColor(0xFF414954);
                canvas.drawRect(platform.left, platform.top,
                        platform.right,
                        platform.top + viewportHeight * 0.012f, paint);

                paint.setColor(0xFFCCB77D);
                for (float x = platform.left + viewportWidth * 0.18f;
                     x < platform.right; x += viewportWidth * 0.34f) {
                    canvas.drawRect(x, groundY + viewportHeight * 0.09f,
                            x + viewportWidth * 0.12f,
                            groundY + viewportHeight * 0.10f, paint);
                }
            } else {
                paint.setColor(0xFF3D4352);
                canvas.drawRect(platform, paint);
                paint.setColor(GameAssets.CONCRETE);
                canvas.drawRect(platform.left, platform.top, platform.right,
                        platform.top + viewportHeight * 0.012f, paint);
            }
        }

        // Auto-rickshaw is drawn here, where the Canvas parameter exists.
        // Keeping this drawing inline avoids a missing-canvas variable error.
        float autoX = viewportWidth * 2.62f;
        float autoY = groundY - viewportHeight * 0.12f;

        paint.setColor(0xFF25834E);
        canvas.drawRoundRect(autoX, autoY,
                autoX + viewportWidth * 0.17f,
                groundY - viewportHeight * 0.015f,
                viewportHeight * 0.018f,
                viewportHeight * 0.018f, paint);

        paint.setColor(0xFFF3C94F);
        canvas.drawRect(autoX + viewportWidth * 0.025f,
                autoY + viewportHeight * 0.02f,
                autoX + viewportWidth * 0.14f,
                autoY + viewportHeight * 0.055f, paint);

        paint.setColor(0xFF15212B);
        canvas.drawCircle(autoX + viewportWidth * 0.035f,
                groundY - viewportHeight * 0.014f,
                viewportHeight * 0.025f, paint);
        canvas.drawCircle(autoX + viewportWidth * 0.135f,
                groundY - viewportHeight * 0.014f,
                viewportHeight * 0.025f, paint);
    }
}
