package com.rictharakan.anniehilation.annienvasion;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/**
 * Annienvasion v1.1 visual rebuild.
 * The runway is now a live scene: two opposing runners physically approach
 * the gate pair, squads run behind them, gates pulse, and new miniatures visibly
 * burst into the formation when an addition/multiplier resolves.
 */
public final class AnnienvasionView extends View {
    public interface Listener {
        void onStageWon(int completedStage);
        void onExitRequested();
    }

    private static final float BASE_W = 400f;
    private static final float BASE_H = 800f;
    private static final float GATE_Y = 348f;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int[] sky = new int[] {
            Color.rgb(12, 25, 51), Color.rgb(31, 73, 91), Color.rgb(42, 111, 83)
    };
    private final Bitmap[] annieRunFrames = new Bitmap[8];
    private final Bitmap[] annieWalkFrames = new Bitmap[8];
    private final Bitmap[] berthuFrames = new Bitmap[7];
    private final Bitmap[] xingxingFrames = new Bitmap[8];

    private AnnienvasionEngine engine;
    private Listener listener;
    private int stage;
    private long lastFrameNanos;
    private boolean running = true;
    private boolean winReported;
    private float scale = 1f;
    private float offsetX;
    private float offsetY;
    private float sceneClock;
    private float gateFlash;
    private float cloneBurst;
    private float playerLaneX = 200f;
    private float enemyLaneX = 200f;
    private int lastGateIndex;
    private int lastPlayerArmy = 1;
    private int lastEnemyArmy = 1;
    private String growthText = "";

    public AnnienvasionView(Context context, int initialStage) {
        super(context);
        setFocusable(true);
        stage = Math.max(1, Math.min(50, initialStage));
        engine = new AnnienvasionEngine(stage);
        for (int i = 0; i < annieRunFrames.length; i++)
            annieRunFrames[i] = loadBitmap("annie_run_" + (i + 1));
        for (int i = 0; i < annieWalkFrames.length; i++)
            annieWalkFrames[i] = loadBitmap("annie_walk_" + (i + 1));
        for (int i = 0; i < berthuFrames.length; i++)
            berthuFrames[i] = loadBitmap("berthu_walk_" + (i + 1));
        for (int i = 0; i < xingxingFrames.length; i++)
            xingxingFrames[i] = loadBitmap("xingxing_frame_" + (i + 1));
        if (xingxingFrames[0] == null) xingxingFrames[0] = loadBitmap("xingxing");
    }

    public void setListener(Listener listener) { this.listener = listener; }

    private Bitmap loadBitmap(String name) {
        try {
            int id = getResources().getIdentifier(name, "drawable", getContext().getPackageName());
            return id == 0 ? null : BitmapFactory.decodeResource(getResources(), id);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public void pauseGame() {
        running = false;
        lastFrameNanos = 0L;
    }

    public void resumeGame() {
        running = true;
        lastFrameNanos = 0L;
        postInvalidate();
    }

    private void startStage(int nextStage) {
        stage = Math.max(1, Math.min(50, nextStage));
        engine = new AnnienvasionEngine(stage);
        winReported = false;
        lastFrameNanos = 0L;
        lastGateIndex = 0;
        lastPlayerArmy = 1;
        lastEnemyArmy = 1;
        gateFlash = 0f;
        cloneBurst = 0f;
        playerLaneX = 200f;
        enemyLaneX = 200f;
        growthText = "";
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        scale = Math.min(width / BASE_W, height / BASE_H);
        offsetX = (width - BASE_W * scale) / 2f;
        offsetY = (height - BASE_H * scale) / 2f;
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        long now = System.nanoTime();
        float dt = 0f;
        if (running) {
            dt = lastFrameNanos == 0L ? 0f : Math.min(0.06f, (now - lastFrameNanos) / 1_000_000_000f);
            lastFrameNanos = now;
            sceneClock += dt;
            engine.update(dt);
            gateFlash = Math.max(0f, gateFlash - dt * 1.9f);
            cloneBurst = Math.max(0f, cloneBurst - dt * 1.4f);
            if (engine.getGateIndex() > lastGateIndex) {
                gateFlash = 1f;
                cloneBurst = 1f;
                lastGateIndex = engine.getGateIndex();
                growthText = "ANNIE " + lastPlayerArmy + " → " + engine.getPlayerArmy() + "   |   RIVAL " + lastEnemyArmy + " → " + engine.getEnemyArmy();
                lastPlayerArmy = engine.getPlayerArmy();
                lastEnemyArmy = engine.getEnemyArmy();
            }
        }

        drawBackdrop(canvas);
        drawHeader(canvas);
        if (engine.getPhase() == AnnienvasionEngine.PHASE_RUNWAY) drawRunway(canvas);
        else drawBattle(canvas);
        drawFooter(canvas);

        if (engine.isWon() && !winReported) {
            winReported = true;
            if (listener != null) listener.onStageWon(stage);
        }
        canvas.restore();
        if (running && (!engine.isFinished() || (engine.isWon() && engine.getVictoryClock() < 4f)))
            postInvalidateDelayed(16L);
    }

    private int worldAccent() {
        switch (Math.min(8, ((stage - 1) / 6) + 1)) {
            case 2: return Color.rgb(255, 176, 105);
            case 3: return Color.rgb(222, 190, 130);
            case 4: return Color.rgb(109, 213, 139);
            case 5: return Color.rgb(159, 132, 230);
            case 6: return Color.rgb(112, 205, 234);
            case 7: return Color.rgb(179, 140, 255);
            case 8: return Color.rgb(255, 213, 112);
            default: return Color.rgb(113, 225, 211);
        }
    }

    private void drawBackdrop(Canvas c) {
        p.setShader(new LinearGradient(0, 0, 0, BASE_H, sky, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, BASE_W, BASE_H, p);
        p.setShader(null);
        // Distant glowing sky lights.
        for (int i = 0; i < 7; i++) {
            float x = 24 + i * 62;
            float y = 104 + (float) Math.sin(sceneClock * .25f + i) * 4f;
            p.setColor(Color.argb(30, 220, 255, 244));
            c.drawCircle(x, y, 15 + (i % 3) * 4, p);
            p.setColor(Color.argb(85, 255, 255, 255));
            c.drawCircle(x, y, 1.4f, p);
        }
        p.setColor(Color.argb(48, 2, 10, 19));
        c.drawRect(0, 775, BASE_W, BASE_H, p);
    }

    private void drawHeader(Canvas c) {
        glass(c, 12, 8, 388, 79, 17, Color.argb(218, 7, 17, 35), worldAccent());
        text(c, "ANNIENVASION", 25, 37, 22, Color.rgb(255, 220, 132), true, Paint.Align.LEFT);
        text(c, "RUN • MULTIPLY • LEAD THE CHARGE", 26, 57, 8.6f, Color.rgb(210, 230, 235), true, Paint.Align.LEFT);
        text(c, "WORLD " + worldForStage(stage) + "  /  " + worldNameForStage(stage), 26, 71, 8f, Color.WHITE, false, Paint.Align.LEFT);
        glass(c, 315, 19, 379, 62, 12, Color.argb(180, 65, 78, 100), Color.argb(120, 255, 255, 255));
        text(c, "STAGE " + stage + "/50", 347, 45, 10.5f, Color.WHITE, true, Paint.Align.CENTER);
        text(c, "✕", 370, 34, 13, Color.WHITE, true, Paint.Align.CENTER);
    }

    private int worldForStage(int s) { return Math.min(8, ((s - 1) / 6) + 1); }

    private String worldNameForStage(int s) {
        if (s <= 6) return "SCHOOL GROUNDS";
        if (s <= 12) return "OLD CITY";
        if (s <= 18) return "FORGOTTEN FORT";
        if (s <= 24) return "WILD JUNGLE";
        if (s <= 30) return "UNDERGROUND KINGDOM";
        if (s <= 36) return "MOUNTAIN PASSAGE";
        if (s <= 42) return "NIGHT CITADEL";
        return "XINGXING'S KINGDOM";
    }

    private void drawRunway(Canvas c) {
        drawRunwayScenery(c);
        drawTrack(c);
        drawRunwayHud(c);

        AnnienvasionEngine.GatePair pair = engine.getCurrentGate();
        int playerLane = engine.getDisplayPlayerChoice();
        int enemyLane = engine.getDisplayEnemyChoice();
        float progress = engine.getGateProgress();
        float runBob = (float) Math.sin(sceneClock * 17f) * 2.7f;
        float playerY = 500f - progress * 150f + runBob;
        float enemyY = 220f + progress * 130f - runBob;
        float targetPlayerX = playerLane == 0 ? 123f : 277f;
        float targetEnemyX = enemyLane == 0 ? 123f : 277f;
        playerLaneX += (targetPlayerX - playerLaneX) * .19f;
        enemyLaneX += (targetEnemyX - enemyLaneX) * .19f;
        float playerX = playerLaneX;
        float enemyX = enemyLaneX;

        // Two gates are physical 3D arches on the track, not just text counters.
        drawMultiplierGate(c, 123, GATE_Y, pair.left.label(), 0, engine.getSelectedGate() == 0);
        drawMultiplierGate(c, 277, GATE_Y, pair.right.label(), 1, engine.getSelectedGate() == 1);

        // The trailing crowds grow with the actual number produced by each gate.
        drawRunningCrowd(c, true, engine.getPlayerArmy(), playerX, playerY + 26, 1f);
        drawRunningCrowd(c, false, engine.getEnemyArmy(), enemyX, enemyY - 20, -1f);
        drawAnnieRunner(c, playerX, playerY, 1f);
        drawRivalRunner(c, enemyX, enemyY, -1f);
        drawBerthuRunner(c, playerX + (playerLane == 0 ? 37 : -37), playerY + 20);

        // Gate flash and clone burst make each + / x decision feel physical.
        if (gateFlash > 0f) {
            drawGateBurst(c, playerX, GATE_Y, gateFlash);
            drawGateBurst(c, enemyX, GATE_Y, gateFlash * .72f);
        }
        if (cloneBurst > 0f) {
            text(c, "SQUAD MULTIPLIED!", 200, 413, 14 + cloneBurst * 4, Color.rgb(255, 238, 163), true, Paint.Align.CENTER);
            text(c, growthText, 200, 431, 8.1f, Color.WHITE, true, Paint.Align.CENTER);
            drawBurstStars(c, 200, 445, cloneBurst);
        }
        drawGateTimer(c);
    }

    private void drawRunwayScenery(Canvas c) {
        // Distant school / world silhouette and garden line.
        p.setColor(Color.argb(155, 9, 36, 48));
        c.drawOval(-35, 105, 155, 206, p);
        c.drawOval(244, 95, 445, 218, p);
        p.setColor(Color.argb(190, 16, 59, 57));
        c.drawRect(0, 126, 400, 605, p);
        p.setColor(Color.argb(110, 145, 208, 154));
        for (int i = 0; i < 11; i++) {
            float x = i * 42 - 12;
            float y = 137 + (i % 3) * 11;
            c.drawCircle(x, y, 16 + (i % 4) * 3, p);
        }
        // Distant banners, lamps and arena side stands.
        for (int i = 0; i < 4; i++) {
            float x = 18 + i * 122;
            p.setColor(Color.argb(170, 218, 231, 210));
            c.drawRect(x, 156, x + 2, 255, p);
            p.setColor(worldAccent());
            Path flag = new Path();
            flag.moveTo(x + 2, 160); flag.lineTo(x + 28, 168); flag.lineTo(x + 2, 179); flag.close();
            c.drawPath(flag, p);
        }
    }

    private void drawTrack(Canvas c) {
        Path road = new Path();
        road.moveTo(115, 120); road.lineTo(285, 120); road.lineTo(382, 618); road.lineTo(18, 618); road.close();
        p.setShader(new LinearGradient(0, 120, 0, 618, Color.rgb(79, 102, 111), Color.rgb(28, 47, 61), Shader.TileMode.CLAMP));
        c.drawPath(road, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.4f);
        p.setColor(Color.argb(185, 221, 245, 221));
        c.drawLine(115, 120, 18, 618, p); c.drawLine(285, 120, 382, 618, p);
        p.setStyle(Paint.Style.FILL);
        // Scrolling lane dashes sell forward movement.
        for (int i = 0; i < 11; i++) {
            float y = (i * 54 + (sceneClock * 150f) % 54f) + 120;
            if (y > 615) continue;
            float half = 7 + (y - 120) * .035f;
            p.setColor(Color.argb(75, 230, 246, 239));
            c.drawRoundRect(200 - half, y, 200 + half, y + 16, 4, 4, p);
        }
        // Parallel edge lights.
        for (int i = 0; i < 13; i++) {
            float y = 145 + i * 37;
            float spread = (y - 120) * .19f;
            p.setColor(Color.argb(155, 92, 225, 202));
            c.drawCircle(200 - 70 - spread, y, 2.1f + i * .08f, p);
            p.setColor(Color.argb(155, 255, 201, 105));
            c.drawCircle(200 + 70 + spread, y, 2.1f + i * .08f, p);
        }
        // Track labels.
        text(c, "RIVAL CHARGE", 57, 133, 7.4f, Color.rgb(255, 194, 183), true, Paint.Align.CENTER);
        text(c, "ANNIE'S CHARGE", 343, 603, 7.4f, Color.rgb(176, 232, 255), true, Paint.Align.CENTER);
    }

    private void drawRunwayHud(Canvas c) {
        glass(c, 22, 91, 378, 125, 11, Color.argb(193, 8, 20, 37), Color.argb(100, 255, 255, 255));
        text(c, "GATE " + Math.min(AnnienvasionEngine.GATES_PER_RUN, engine.getGateIndex() + 1) + " / " + AnnienvasionEngine.GATES_PER_RUN,
                200, 106, 9.5f, Color.WHITE, true, Paint.Align.CENTER);
        text(c, "RIVAL  " + engine.getEnemyArmy(), 57, 106, 9, Color.rgb(255, 187, 174), true, Paint.Align.CENTER);
        text(c, "ANNIE  " + engine.getPlayerArmy(), 343, 106, 9, Color.rgb(166, 228, 255), true, Paint.Align.CENTER);
        p.setColor(Color.argb(85, 255, 255, 255)); c.drawRoundRect(48, 114, 352, 120, 3, 3, p);
        float journey = Math.min(1f, (engine.getGateIndex() + engine.getGateProgress()) / AnnienvasionEngine.GATES_PER_RUN);
        p.setColor(worldAccent()); c.drawRoundRect(48, 114, 48 + 304 * journey, 120, 3, 3, p);
    }

    private void drawMultiplierGate(Canvas c, float cx, float cy, String label, int side, boolean selected) {
        float w = selected ? 69 : 63;
        float h = selected ? 92 : 86;
        int glow = side == 0 ? Color.rgb(48, 216, 232) : Color.rgb(194, 118, 255);
        // Shadow + raised plinth.
        p.setColor(Color.argb(100, 0, 0, 0));
        c.drawOval(cx - 50, cy + 29, cx + 50, cy + 44, p);
        roundRect(c, cx - 48, cy + 24, cx + 48, cy + 38, 7, Color.rgb(27, 39, 54), Color.argb(140, 255, 255, 255), 1);
        // Outer arch has a luminous edge and inner dark opening.
        roundRect(c, cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, 18,
                Color.argb(220, 10, 21, 40), glow, selected ? 4 : 2.5f);
        roundRect(c, cx - w / 2f + 8, cy - h / 2f + 8, cx + w / 2f - 8, cy + h / 2f - 4, 13,
                Color.argb(180, side == 0 ? 20 : 35, 49, side == 0 ? 67 : 69), Color.argb(130, 255, 255, 255), 1);
        // Portal glow.
        p.setShader(new LinearGradient(cx, cy - h / 2f, cx, cy + h / 2f,
                Color.argb(selected ? 180 : 105, Color.red(glow), Color.green(glow), Color.blue(glow)),
                Color.argb(8, Color.red(glow), Color.green(glow), Color.blue(glow)), Shader.TileMode.CLAMP));
        c.drawRoundRect(cx - w / 2f + 12, cy - h / 2f + 12, cx + w / 2f - 12, cy + h / 2f - 4, 10, 10, p);
        p.setShader(null);
        // Top-mounted sign.
        roundRect(c, cx - 43, cy - h / 2f - 21, cx + 43, cy - h / 2f + 8, 9,
                selected ? Color.rgb(255, 207, 104) : glow, Color.argb(190, 255, 255, 255), 1);
        text(c, label, cx, cy - h / 2f + 1, 18, selected ? Color.rgb(30, 38, 48) : Color.WHITE, true, Paint.Align.CENTER);
        text(c, side == 0 ? "GATE A" : "GATE B", cx, cy + h / 2f + 18, 8.2f, Color.WHITE, true, Paint.Align.CENTER);
        if (selected) {
            p.setColor(Color.argb(150, 255, 238, 158));
            c.drawCircle(cx, cy - h / 2f - 6, 3, p);
        }
    }

    private void drawRunningCrowd(Canvas c, boolean friendly, int count, float leaderX, float leaderY, float direction) {
        int visible = Math.min(20, Math.max(0, count - 1));
        for (int i = visible - 1; i >= 0; i--) {
            int row = i / 5;
            int col = (i % 5) - 2;
            float perspective = 1f - row * .105f;
            float x = leaderX + col * (12f + row * 1.2f);
            float y = leaderY + direction * (18f + row * 18f) + (float) Math.sin(sceneClock * 14f + i) * 1.8f;
            drawMiniRunner(c, x, y, friendly, .70f * perspective, sceneClock * 13f + i * .7f, i);
        }
        // The troop counter grows with a quick pop after crossing a gate.
        float pulse = cloneBurst > 0f ? 1f + cloneBurst * .12f : 1f;
        drawCountBadge(c, leaderX + 37, leaderY - 31, count, friendly, pulse);
    }

    private void drawAnnieRunner(Canvas c, float x, float y, float direction) {
        Bitmap frame = animatedFrame(annieRunFrames, 88L);
        if (frame == null) frame = animatedFrame(annieWalkFrames, 105L);
        float bob = (float) Math.sin(sceneClock * 17f) * 2.6f;
        if (frame != null) {
            drawBitmapFit(c, frame, x - 24, y - 55 + bob, 48, 58);
        } else {
            drawSchoolGirl(c, x, y + bob, 1f, sceneClock * 17f);
        }
        // Tiny backpack silhouette and little speed trail.
        p.setColor(Color.argb(135, 185, 226, 255));
        c.drawLine(x - 18, y - 32, x - 29, y - 39, p);
        c.drawLine(x - 18, y - 23, x - 33, y - 25, p);
    }

    private void drawSchoolGirl(Canvas c, float x, float feetY, float size, float runPhase) {
        float bob = (float) Math.sin(runPhase * 2f) * 1.5f;
        float legA = (float) Math.sin(runPhase) * 8f * size;
        float legB = (float) Math.sin(runPhase + Math.PI) * 8f * size;
        float armA = (float) Math.sin(runPhase + Math.PI) * 7f * size;
        float armB = (float) Math.sin(runPhase) * 7f * size;
        // Shadow, shoes and moving legs.
        p.setColor(Color.argb(105, 0, 0, 0)); c.drawOval(x - 17 * size, feetY - 2, x + 18 * size, feetY + 5, p);
        p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(4.6f * size); p.setColor(Color.rgb(246, 220, 193));
        c.drawLine(x - 5 * size, feetY - 18 * size, x - 7 * size + legA, feetY - 2, p);
        c.drawLine(x + 5 * size, feetY - 18 * size, x + 7 * size + legB, feetY - 2, p);
        p.setStrokeWidth(5 * size); p.setColor(Color.rgb(37, 39, 53));
        c.drawLine(x - 7 * size + legA, feetY - 2, x - 10 * size + legA, feetY, p);
        c.drawLine(x + 7 * size + legB, feetY - 2, x + 10 * size + legB, feetY, p);
        // Swinging arms and white blouse.
        p.setStrokeWidth(4.4f * size); p.setColor(Color.rgb(245, 230, 214));
        c.drawLine(x - 9 * size, feetY - 43 * size + bob, x - 15 * size + armA, feetY - 30 * size + bob, p);
        c.drawLine(x + 9 * size, feetY - 43 * size + bob, x + 15 * size + armB, feetY - 31 * size + bob, p);
        p.setColor(Color.rgb(49, 86, 142));
        Path skirt = new Path();
        skirt.moveTo(x - 10 * size, feetY - 35 * size + bob);
        skirt.lineTo(x + 10 * size, feetY - 35 * size + bob);
        skirt.lineTo(x + 15 * size, feetY - 18 * size);
        skirt.lineTo(x - 15 * size, feetY - 18 * size);
        skirt.close(); c.drawPath(skirt, p);
        roundRect(c, x - 10 * size, feetY - 47 * size + bob, x + 10 * size, feetY - 32 * size + bob,
                4 * size, Color.rgb(238, 234, 221), Color.argb(130, 255, 255, 255), .8f * size);
        // Tie and collar.
        p.setColor(Color.rgb(171, 51, 68));
        Path tie = new Path(); tie.moveTo(x, feetY - 42 * size + bob); tie.lineTo(x - 2.5f * size, feetY - 34 * size + bob);
        tie.lineTo(x + 2.5f * size, feetY - 34 * size + bob); tie.close(); c.drawPath(tie, p);
        // Face, hair and swinging ponytail.
        p.setColor(Color.rgb(238, 190, 154)); c.drawCircle(x, feetY - 54 * size + bob, 8.5f * size, p);
        p.setColor(Color.rgb(53, 35, 35));
        c.drawArc(x - 9 * size, feetY - 64 * size + bob, x + 9 * size, feetY - 48 * size + bob, 180, 180, true, p);
        c.drawCircle(x - 7 * size, feetY - 55 * size + bob, 3.1f * size, p);
        p.setColor(Color.rgb(239, 190, 158)); c.drawCircle(x - 8 * size, feetY - 53 * size + bob, 3.2f * size, p);
        p.setColor(Color.rgb(35, 30, 31)); c.drawCircle(x - 3 * size, feetY - 54 * size + bob, .8f * size, p);
        c.drawCircle(x + 3 * size, feetY - 54 * size + bob, .8f * size, p);
        p.setStrokeWidth(2 * size); p.setColor(Color.rgb(53, 35, 35));
        c.drawLine(x + 7 * size, feetY - 58 * size + bob, x + 13 * size, feetY - 54 * size + bob, p);
    }

    private void drawRivalRunner(Canvas c, float x, float y, float direction) {
        float bob = (float) Math.sin(sceneClock * 16f + 1.4f) * 2.5f;
        drawRivalCaptain(c, x, y + bob, 1.05f, sceneClock * 15f);
        // Red battle standard trails behind the rival captain.
        p.setColor(Color.argb(180, 236, 100, 93));
        c.drawLine(x + 16, y - 33, x + 24, y - 5, p);
        Path flag = new Path(); flag.moveTo(x + 23, y - 32); flag.lineTo(x + 43, y - 24);
        flag.lineTo(x + 23, y - 14); flag.close(); c.drawPath(flag, p);
    }

    private void drawRivalCaptain(Canvas c, float x, float feetY, float scale, float phase) {
        float leg = (float) Math.sin(phase) * 7f * scale;
        float arm = (float) Math.sin(phase + Math.PI) * 6f * scale;
        p.setColor(Color.argb(100, 0, 0, 0)); c.drawOval(x - 17 * scale, feetY - 2, x + 18 * scale, feetY + 5, p);
        p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(5 * scale); p.setColor(Color.rgb(221, 181, 143));
        c.drawLine(x - 5 * scale, feetY - 18 * scale, x - 9 * scale + leg, feetY - 1, p);
        c.drawLine(x + 5 * scale, feetY - 18 * scale, x + 9 * scale - leg, feetY - 1, p);
        p.setColor(Color.rgb(38, 40, 54)); c.drawLine(x - 9 * scale + leg, feetY - 1, x - 14 * scale + leg, feetY, p);
        c.drawLine(x + 9 * scale - leg, feetY - 1, x + 14 * scale - leg, feetY, p);
        p.setColor(Color.rgb(122, 55, 61));
        Path cape = new Path(); cape.moveTo(x - 9 * scale, feetY - 44 * scale); cape.lineTo(x + 11 * scale, feetY - 42 * scale);
        cape.lineTo(x + 19 * scale, feetY - 17 * scale); cape.lineTo(x - 17 * scale, feetY - 17 * scale); cape.close(); c.drawPath(cape, p);
        roundRect(c, x - 10 * scale, feetY - 45 * scale, x + 10 * scale, feetY - 19 * scale,
                5 * scale, Color.rgb(88, 76, 101), Color.rgb(206, 181, 144), 1f);
        p.setStrokeWidth(4.4f * scale); p.setColor(Color.rgb(218, 174, 135));
        c.drawLine(x - 8 * scale, feetY - 40 * scale, x - 15 * scale - arm, feetY - 28 * scale, p);
        c.drawLine(x + 8 * scale, feetY - 40 * scale, x + 15 * scale + arm, feetY - 29 * scale, p);
        p.setColor(Color.rgb(220, 174, 139)); c.drawCircle(x, feetY - 53 * scale, 8 * scale, p);
        p.setColor(Color.rgb(40, 40, 55));
        Path helm = new Path(); helm.moveTo(x - 9 * scale, feetY - 53 * scale); helm.lineTo(x - 7 * scale, feetY - 66 * scale);
        helm.lineTo(x, feetY - 72 * scale); helm.lineTo(x + 8 * scale, feetY - 65 * scale);
        helm.lineTo(x + 9 * scale, feetY - 53 * scale); helm.close(); c.drawPath(helm, p);
        p.setColor(Color.rgb(247, 188, 87)); c.drawRect(x - 1 * scale, feetY - 66 * scale, x + 1 * scale, feetY - 54 * scale, p);
    }

    private void drawMiniRunner(Canvas c, float x, float feetY, boolean friendly, float size, float phase, int variant) {
        float swing = (float) Math.sin(phase) * 4.4f * size;
        int tunic = friendly ? (variant % 3 == 0 ? Color.rgb(49, 106, 165) : Color.rgb(71, 134, 184))
                : (variant % 3 == 0 ? Color.rgb(139, 55, 63) : Color.rgb(158, 82, 74));
        p.setColor(Color.argb(72, 0, 0, 0)); c.drawOval(x - 6 * size, feetY - 1, x + 7 * size, feetY + 2, p);
        p.setStrokeWidth(2.8f * size); p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(Color.rgb(235, 205, 176));
        c.drawLine(x - 2 * size, feetY - 8 * size, x - 3 * size + swing, feetY, p);
        c.drawLine(x + 2 * size, feetY - 8 * size, x + 3 * size - swing, feetY, p);
        p.setColor(Color.rgb(30, 34, 48));
        c.drawLine(x - 3 * size + swing, feetY, x - 5 * size + swing, feetY, p);
        c.drawLine(x + 3 * size - swing, feetY, x + 5 * size - swing, feetY, p);
        p.setColor(tunic); roundRect(c, x - 4.5f * size, feetY - 18 * size, x + 4.5f * size, feetY - 7 * size,
                2.5f * size, tunic, Color.argb(130, 255, 255, 255), .6f * size);
        p.setStrokeWidth(2.5f * size); p.setColor(Color.rgb(232, 195, 162));
        c.drawLine(x - 4 * size, feetY - 16 * size, x - 7 * size - swing, feetY - 9 * size, p);
        c.drawLine(x + 4 * size, feetY - 16 * size, x + 7 * size + swing, feetY - 9 * size, p);
        p.setColor(Color.rgb(224, 183, 146)); c.drawCircle(x, feetY - 21 * size, 4 * size, p);
        p.setColor(friendly ? Color.rgb(56, 42, 40) : Color.rgb(66, 47, 43));
        c.drawArc(x - 4.5f * size, feetY - 25 * size, x + 4.5f * size, feetY - 17 * size, 180, 180, true, p);
        if (!friendly) {
            p.setColor(Color.rgb(235, 184, 80));
            c.drawRect(x - 1.2f * size, feetY - 23 * size, x + 1.2f * size, feetY - 12 * size, p);
        }
    }

    private void drawBerthuRunner(Canvas c, float x, float feetY) {
        Bitmap frame = animatedFrame(berthuFrames, 105L);
        if (frame != null) {
            float bob = (float) Math.sin(sceneClock * 16f) * 2.2f;
            drawBitmapFit(c, frame, x - 22, feetY - 38 + bob, 44, 39);
            return;
        }
        float bob = (float) Math.sin(sceneClock * 15f) * 2f;
        p.setColor(Color.argb(85, 0, 0, 0)); c.drawOval(x - 17, feetY - 2, x + 18, feetY + 4, p);
        // Orange tabby body and paws.
        p.setColor(Color.rgb(218, 119, 39)); c.drawOval(x - 16, feetY - 22 + bob, x + 16, feetY - 7 + bob, p);
        p.setStrokeWidth(4); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(Color.rgb(218, 119, 39));
        float leg = (float) Math.sin(sceneClock * 17f) * 4f;
        c.drawLine(x - 9, feetY - 10 + bob, x - 12 + leg, feetY - 1, p);
        c.drawLine(x + 8, feetY - 10 + bob, x + 11 - leg, feetY - 1, p);
        // Tail curls as he runs.
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4); p.setColor(Color.rgb(233, 143, 56));
        c.drawArc(x - 2, feetY - 33 + bob, x + 26, feetY - 12 + bob, 180, 185, false, p); p.setStyle(Paint.Style.FILL);
        c.drawCircle(x, feetY - 25 + bob, 11, p);
        Path ears = new Path(); ears.moveTo(x - 9, feetY - 29 + bob); ears.lineTo(x - 12, feetY - 42 + bob); ears.lineTo(x - 2, feetY - 34 + bob);
        ears.moveTo(x + 9, feetY - 29 + bob); ears.lineTo(x + 12, feetY - 42 + bob); ears.lineTo(x + 2, feetY - 34 + bob);
        ears.close(); c.drawPath(ears, p);
        p.setColor(Color.rgb(248, 218, 181)); c.drawOval(x - 7, feetY - 27 + bob, x + 7, feetY - 19 + bob, p);
        p.setColor(Color.rgb(48, 34, 27)); c.drawCircle(x - 4, feetY - 27 + bob, 1.3f, p); c.drawCircle(x + 4, feetY - 27 + bob, 1.3f, p);
        p.setStrokeWidth(2); p.setStyle(Paint.Style.STROKE); p.setColor(Color.rgb(111, 61, 31));
        c.drawLine(x - 3, feetY - 34 + bob, x - 6, feetY - 29 + bob, p); c.drawLine(x + 3, feetY - 34 + bob, x + 6, feetY - 29 + bob, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawCountBadge(Canvas c, float x, float y, int count, boolean friendly, float pulse) {
        float radius = 13 * pulse;
        p.setColor(friendly ? Color.rgb(40, 145, 194) : Color.rgb(182, 68, 74));
        c.drawCircle(x, y, radius, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f); p.setColor(Color.argb(225, 255, 255, 255));
        c.drawCircle(x, y, radius, p); p.setStyle(Paint.Style.FILL);
        text(c, String.valueOf(count), x, y + 4, count > 99 ? 8 : 10, Color.WHITE, true, Paint.Align.CENTER);
    }

    private void drawGateBurst(Canvas c, float x, float y, float power) {
        float radius = 22 + (1f - power) * 52;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f * power);
        p.setColor(Color.argb((int) (180 * power), 255, 234, 139)); c.drawCircle(x, y, radius, p);
        p.setStyle(Paint.Style.FILL);
        drawBurstStars(c, x, y, power);
    }

    private void drawBurstStars(Canvas c, float x, float y, float power) {
        for (int i = 0; i < 8; i++) {
            double a = (Math.PI * 2 * i / 8.0) + sceneClock * .5;
            float d = 18 + (1f - power) * 35;
            float sx = x + (float) Math.cos(a) * d;
            float sy = y + (float) Math.sin(a) * d;
            p.setColor(Color.argb((int) (190 * power), 255, 232, 143));
            c.drawCircle(sx, sy, 2.2f * power + .5f, p);
        }
    }

    private void drawGateTimer(Canvas c) {
        text(c, "TAP A GATE TO CHOOSE  •  BOTH ARMIES ARE MOVING", 200, 626, 7.6f,
                Color.rgb(231, 244, 241), true, Paint.Align.CENTER);
    }

    private void drawBattle(Canvas c) {
        drawBattleScenery(c);
        drawBattleHud(c);
        drawIsoArena(c);
        drawBattleTroops(c, false, engine.getEnemyArmy());
        drawBattleTroops(c, true, engine.getPlayerArmy());
        drawClashEffects(c);
        drawRivalCaptain(c, 200, 280, 1.1f, sceneClock * 12f);
        drawAnnieRunner(c, 200, 532, 1f);
        drawBerthuRunner(c, 115, 547);
        if (engine.getPhase() == AnnienvasionEngine.PHASE_WON) drawVictory(c);
        else if (engine.getPhase() == AnnienvasionEngine.PHASE_LOST) drawDefeat(c);
    }

    private void drawBattleScenery(Canvas c) {
        p.setColor(Color.argb(210, 15, 49, 51)); c.drawRect(0, 92, 400, 660, p);
        // miniature fortress walls, turrets, flags and trees around the war field
        for (int i = 0; i < 5; i++) {
            float x = i * 92 - 14;
            p.setColor(Color.argb(160, 68, 91, 92)); c.drawRect(x, 130, x + 37, 198, p);
            p.setColor(Color.argb(180, 100, 128, 119)); c.drawRect(x - 3, 124, x + 40, 139, p);
            p.setColor(worldAccent()); c.drawRect(x + 17, 109, x + 19, 127, p);
            Path flag = new Path(); flag.moveTo(x + 19, 109); flag.lineTo(x + 37, 115); flag.lineTo(x + 19, 122); flag.close(); c.drawPath(flag, p);
        }
        for (int i = 0; i < 8; i++) {
            float x = i * 58 - 5;
            p.setColor(Color.argb(170, 18, 68, 51)); c.drawCircle(x, 618 + (i % 2) * 8, 19, p);
            p.setColor(Color.argb(140, 31, 104, 70)); c.drawCircle(x - 5, 610 + (i % 2) * 8, 13, p);
        }
    }

    private void drawBattleHud(Canvas c) {
        glass(c, 18, 91, 191, 139, 12, Color.argb(222, 113, 44, 52), Color.argb(160, 255, 190, 171));
        glass(c, 209, 91, 382, 139, 12, Color.argb(222, 29, 85, 125), Color.argb(160, 174, 226, 255));
        text(c, "RIVAL ARMY", 104, 108, 8, Color.rgb(255, 218, 208), true, Paint.Align.CENTER);
        text(c, String.valueOf(engine.getEnemyArmy()), 104, 130, 17, Color.WHITE, true, Paint.Align.CENTER);
        text(c, "ANNIE'S SQUAD", 296, 108, 8, Color.rgb(211, 240, 255), true, Paint.Align.CENTER);
        text(c, String.valueOf(engine.getPlayerArmy()), 296, 130, 17, Color.WHITE, true, Paint.Align.CENTER);
        text(c, "MINIATURES IN THE FIELD", 200, 157, 8, Color.rgb(225, 243, 232), true, Paint.Align.CENTER);
    }

    private void drawIsoArena(Canvas c) {
        // Layered isometric turf tiles with raised edges and a central battle lane.
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 9; col++) {
                float cx = 200 + (col - 4) * 29 + (row - 3.5f) * 12;
                float cy = 388 + (row - 3.5f) * 27 - (col - 4) * 10;
                Path tile = new Path(); tile.moveTo(cx, cy - 15); tile.lineTo(cx + 30, cy);
                tile.lineTo(cx, cy + 15); tile.lineTo(cx - 30, cy); tile.close();
                p.setColor(((row + col) & 1) == 0 ? Color.rgb(70, 133, 91) : Color.rgb(51, 112, 77));
                c.drawPath(tile, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.75f);
                p.setColor(Color.argb(80, 222, 255, 211)); c.drawPath(tile, p); p.setStyle(Paint.Style.FILL);
            }
        }
        // Centre strip and little obstacles.
        p.setColor(Color.argb(80, 255, 227, 150)); c.drawLine(78, 390, 322, 390, p);
        drawRock(c, 63, 370, 10); drawRock(c, 332, 420, 12);
        drawTree(c, 47, 293, .9f); drawTree(c, 353, 480, .85f);
    }

    private void drawRock(Canvas c, float x, float y, float size) {
        p.setColor(Color.argb(80, 0, 0, 0)); c.drawOval(x - size, y, x + size, y + 5, p);
        p.setColor(Color.rgb(101, 119, 107)); c.drawOval(x - size, y - size, x + size, y + size * .45f, p);
        p.setColor(Color.argb(100, 220, 237, 214)); c.drawArc(x - size, y - size, x + size, y + size * .45f, 190, 100, false, p);
    }

    private void drawTree(Canvas c, float x, float y, float size) {
        p.setColor(Color.rgb(92, 65, 43)); c.drawRect(x - 3 * size, y - 20 * size, x + 3 * size, y, p);
        p.setColor(Color.rgb(30, 84, 55)); c.drawCircle(x, y - 23 * size, 14 * size, p);
        p.setColor(Color.rgb(48, 120, 70)); c.drawCircle(x - 7 * size, y - 29 * size, 9 * size, p);
        p.setColor(Color.rgb(73, 143, 82)); c.drawCircle(x + 6 * size, y - 31 * size, 8 * size, p);
    }

    private void drawBattleTroops(Canvas c, boolean friendly, int armySize) {
        int shown = Math.min(27, Math.max(0, armySize));
        float t = Math.min(1f, engine.getBattleClock() / 3.2f);
        float wave = (float) Math.sin(sceneClock * 8f);
        for (int i = 0; i < shown; i++) {
            int row = i / 9;
            int col = i % 9;
            float spread = (col - 4) * (17 + row * 2.2f);
            float x = 200 + spread + (float) Math.sin(sceneClock * 5f + i) * 2.2f;
            float spawnY = friendly ? 492 + row * 20 : 291 - row * 20;
            float meetY = friendly ? 407 + row * 13 : 373 - row * 13;
            float clash = Math.max(0f, engine.getBattleClock() - 3.2f);
            float shove = (float) Math.sin(sceneClock * 5.2f + i * .8f) * Math.min(9f, 3f + clash * .22f);
            float y = spawnY + (meetY - spawnY) * t + (friendly ? -shove : shove) + wave * 1.5f;
            x += (float) Math.sin(sceneClock * 8.5f + i * 1.3f) * Math.min(3.5f, 1.5f + clash * .05f);
            float bob = (float) Math.sin(sceneClock * 13f + i * .7f) * 1.4f;
            drawBattleSoldier(c, x, y + bob, friendly, sceneClock * 11f + i * .55f, .92f - row * .07f, i);
        }
    }

    private void drawBattleSoldier(Canvas c, float x, float feetY, boolean friendly, float phase, float size, int variant) {
        float swing = (float) Math.sin(phase) * 4.5f * size;
        int body = friendly ? (variant % 3 == 0 ? Color.rgb(45, 119, 173) : Color.rgb(58, 145, 190))
                : (variant % 3 == 0 ? Color.rgb(151, 59, 67) : Color.rgb(175, 83, 72));
        p.setColor(Color.argb(95, 0, 0, 0)); c.drawOval(x - 8 * size, feetY - 1, x + 9 * size, feetY + 4, p);
        p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(3.7f * size); p.setColor(Color.rgb(218, 184, 150));
        c.drawLine(x - 3 * size, feetY - 13 * size, x - 5 * size + swing, feetY, p);
        c.drawLine(x + 3 * size, feetY - 13 * size, x + 5 * size - swing, feetY, p);
        p.setStrokeWidth(4 * size); p.setColor(Color.rgb(35, 38, 49));
        c.drawLine(x - 5 * size + swing, feetY, x - 8 * size + swing, feetY, p);
        c.drawLine(x + 5 * size - swing, feetY, x + 8 * size - swing, feetY, p);
        // Armour torso, shoulder pieces and shield/weapon.
        p.setColor(body); roundRect(c, x - 6.5f * size, feetY - 26 * size, x + 6.5f * size, feetY - 11 * size,
                4 * size, body, Color.argb(180, 255, 255, 255), .8f * size);
        p.setColor(friendly ? Color.rgb(228, 207, 144) : Color.rgb(228, 178, 91));
        c.drawCircle(x - 6 * size, feetY - 23 * size, 3 * size, p);
        c.drawCircle(x + 6 * size, feetY - 23 * size, 3 * size, p);
        p.setStrokeWidth(3.1f * size); p.setColor(Color.rgb(222, 184, 148));
        c.drawLine(x - 5 * size, feetY - 23 * size, x - 10 * size - swing, feetY - 14 * size, p);
        c.drawLine(x + 5 * size, feetY - 23 * size, x + 10 * size + swing, feetY - 14 * size, p);
        // Tiny sword glint.
        p.setStrokeWidth(1.7f * size); p.setColor(Color.rgb(232, 239, 230));
        c.drawLine(x + 10 * size + swing, feetY - 14 * size, x + 14 * size + swing, feetY - 28 * size, p);
        p.setColor(Color.rgb(226, 183, 147)); c.drawCircle(x, feetY - 31 * size, 6 * size, p);
        p.setColor(friendly ? Color.rgb(42, 45, 59) : Color.rgb(53, 43, 48));
        c.drawArc(x - 6.5f * size, feetY - 38 * size, x + 6.5f * size, feetY - 25 * size, 180, 180, true, p);
        p.setColor(Color.rgb(242, 205, 111)); c.drawRect(x - 1.2f * size, feetY - 26 * size, x + 1.2f * size, feetY - 15 * size, p);
    }

    private void drawClashEffects(Canvas c) {
        float pulse = (float) Math.sin(sceneClock * 13f);
        for (int i = 0; i < 6; i++) {
            float x = 118 + i * 32;
            float y = 390 + (float) Math.sin(sceneClock * 5f + i) * 13f;
            p.setColor(Color.argb(120, 255, 213, 120));
            c.drawCircle(x, y, 2.3f + Math.max(0, pulse) * 2.4f, p);
        }
        if (engine.getBattleClock() > 1f) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f);
            p.setColor(Color.argb(115, 255, 238, 166));
            c.drawArc(172, 364, 228, 421, sceneClock * 88f, 62, false, p);
            c.drawArc(181, 371, 219, 409, -sceneClock * 94f, 55, false, p);
            p.setStyle(Paint.Style.FILL);
        }
        text(c, "CLASH!", 200, 183, 11, Color.rgb(255, 226, 143), true, Paint.Align.CENTER);
    }

    private void drawVictory(Canvas c) {
        // Xingxing and Annie move together before the reunion banner settles in.
        p.setColor(Color.argb(165, 5, 17, 33)); c.drawRoundRect(25, 252, 375, 593, 24, 24, p);
        glass(c, 68, 269, 332, 316, 15, Color.argb(225, 14, 38, 54), Color.rgb(255, 220, 127));
        text(c, "VICTORY!", 200, 299, 20, Color.rgb(255, 225, 143), true, Paint.Align.CENTER);
        float reunion = Math.min(1f, engine.getVictoryClock() / 1.4f);
        float ease = reunion * reunion * (3f - 2f * reunion);
        float annieX = 158 + ease * 24f;
        float monkeyX = 244 - ease * 24f;
        drawAnnieRunner(c, annieX, 493, 1f);
        drawXingxing(c, monkeyX, 435, 80, 90);
        if (reunion > .72f) {
            p.setStrokeWidth(5); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(Color.rgb(115, 73, 45));
            c.drawLine(monkeyX - 14, 438, annieX + 12, 450, p);
            c.drawLine(monkeyX + 14, 438, annieX + 17, 450, p);
        }
        glass(c, 48, 535, 352, 578, 13, Color.argb(225, 12, 31, 48), Color.argb(170, 255, 220, 127));
        text(c, "XINGXING REUNION!", 200, 553, 13, Color.rgb(255, 226, 145), true, Paint.Align.CENTER);
        text(c, "The happy troublemaker made it back to Annie.", 200, 568, 8.3f, Color.WHITE, false, Paint.Align.CENTER);
    }

    private void drawDefeat(Canvas c) {
        p.setColor(Color.argb(165, 5, 15, 28)); c.drawRoundRect(40, 290, 360, 480, 22, 22, p);
        text(c, "THE RIVAL HELD THE FIELD", 200, 350, 16, Color.rgb(255, 202, 185), true, Paint.Align.CENTER);
        text(c, "Change your gate choices and regroup.", 200, 378, 10, Color.WHITE, false, Paint.Align.CENTER);
    }

    private void drawXingxing(Canvas c, float x, float y, float w, float h) {
        Bitmap frame = animatedFrame(xingxingFrames, 120L);
        float bob = (float) Math.sin(sceneClock * 8f) * 4f;
        if (frame != null) { drawBitmapFit(c, frame, x - w / 2f, y - h / 2f + bob, w, h); return; }
        p.setColor(Color.rgb(115, 73, 45)); c.drawOval(x - w * .28f, y - h * .12f + bob, x + w * .28f, y + h * .44f + bob, p);
        c.drawCircle(x, y - h * .18f + bob, w * .31f, p);
        p.setColor(Color.rgb(158, 109, 68)); c.drawCircle(x - w * .29f, y - h * .20f + bob, w * .15f, p); c.drawCircle(x + w * .29f, y - h * .20f + bob, w * .15f, p);
        p.setColor(Color.rgb(243, 197, 149)); c.drawOval(x - w * .18f, y - h * .17f + bob, x + w * .18f, y + h * .04f + bob, p);
        p.setColor(Color.rgb(42, 30, 25)); c.drawCircle(x - 7, y - h * .22f + bob, 2.5f, p); c.drawCircle(x + 7, y - h * .22f + bob, 2.5f, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f); p.setColor(Color.rgb(92, 48, 32));
        c.drawArc(x - 10, y - h * .13f + bob, x + 10, y + h * .08f + bob, 8, 165, false, p); p.setStyle(Paint.Style.FILL);
        p.setStrokeWidth(7); p.setColor(Color.rgb(115, 73, 45));
        c.drawLine(x - w * .19f, y + h * .15f + bob, x - w * .47f, y - h * .12f + bob, p);
        c.drawLine(x + w * .19f, y + h * .15f + bob, x + w * .47f, y - h * .12f + bob, p);
    }

    private void drawFooter(Canvas c) {
        int phase = engine.getPhase();
        if (phase == AnnienvasionEngine.PHASE_RUNWAY) {
            AnnienvasionEngine.GatePair pair = engine.getCurrentGate();
            drawChoiceCard(c, 18, 636, 192, 754, "GATE A", pair.left.label(), "ADDITION / MULTIPLIER", Color.rgb(35, 143, 164), engine.getSelectedGate() == 0);
            drawChoiceCard(c, 208, 636, 382, 754, "GATE B", pair.right.label(), "ADDITION / MULTIPLIER", Color.rgb(125, 81, 171), engine.getSelectedGate() == 1);
            text(c, "TAP THE GATE YOUR RUNNER SHOULD TAKE", 200, 777, 8.2f, Color.rgb(255, 223, 158), true, Paint.Align.CENTER);
        } else if (phase == AnnienvasionEngine.PHASE_BATTLE) {
            drawAbility(c, 18, 682, 192, 770, "BERTHU POUNCE", engine.getPounceCooldown(), Color.rgb(179, 91, 49));
            drawAbility(c, 208, 682, 382, 770, "ANNIE RALLY", engine.getRallyCooldown(), Color.rgb(40, 127, 171));
            text(c, "YOUR COMMANDERS ARE IN THE FIGHT", 200, 665, 8, Color.WHITE, true, Paint.Align.CENTER);
        } else if (phase == AnnienvasionEngine.PHASE_WON) {
            actionCard(c, stage >= 50 ? "CAMPAIGN COMPLETE" : "NEXT STAGE  →", Color.rgb(45, 154, 111));
        } else {
            actionCard(c, "RETRY STAGE", Color.rgb(163, 77, 75));
        }
    }

    private void drawChoiceCard(Canvas c, float l, float t, float r, float b, String title, String value,
                                String subtitle, int color, boolean selected) {
        float alpha = selected ? 244 : 218;
        glass(c, l, t, r, b, 17, Color.argb((int) alpha, Color.red(color), Color.green(color), Color.blue(color)),
                selected ? Color.rgb(255, 222, 126) : Color.argb(160, 255, 255, 255));
        text(c, title, (l + r) / 2f, t + 22, 9, Color.rgb(232, 243, 251), true, Paint.Align.CENTER);
        text(c, value, (l + r) / 2f, t + 67, 30, Color.WHITE, true, Paint.Align.CENTER);
        text(c, subtitle, (l + r) / 2f, b - 13, 7.2f, Color.rgb(232, 241, 246), true, Paint.Align.CENTER);
        if (selected) {
            p.setColor(Color.rgb(255, 221, 127)); c.drawCircle((l + r) / 2f, t + 8, 3, p);
        }
    }

    private void drawAbility(Canvas c, float l, float t, float r, float b, String label, float cooldown, int color) {
        glass(c, l, t, r, b, 17, color, Color.argb(150, 255, 255, 255));
        text(c, label, (l + r) / 2f, t + 31, 10.5f, Color.WHITE, true, Paint.Align.CENTER);
        String status = cooldown <= 0f ? "READY  •  TAP" : String.format(java.util.Locale.US, "%.1fs", cooldown);
        text(c, status, (l + r) / 2f, t + 62, 10, Color.rgb(255, 232, 180), true, Paint.Align.CENTER);
    }

    private void actionCard(Canvas c, String label, int color) {
        glass(c, 20, 690, 380, 773, 18, color, Color.rgb(255, 224, 145));
        text(c, label, 200, 741, 19, Color.WHITE, true, Paint.Align.CENTER);
    }

    private Bitmap animatedFrame(Bitmap[] frames, long frameMillis) {
        int available = 0;
        for (Bitmap frame : frames) if (frame != null && !frame.isRecycled()) available++;
        if (available == 0) return null;
        int target = (int) ((SystemClock.uptimeMillis() / Math.max(1L, frameMillis)) % available);
        for (Bitmap frame : frames) {
            if (frame == null || frame.isRecycled()) continue;
            if (target-- == 0) return frame;
        }
        return null;
    }

    private void drawBitmapFit(Canvas c, Bitmap bitmap, float left, float top, float width, float height) {
        if (bitmap == null || bitmap.isRecycled()) return;
        float ratio = Math.min(width / bitmap.getWidth(), height / bitmap.getHeight());
        float w = bitmap.getWidth() * ratio;
        float h = bitmap.getHeight() * ratio;
        rect.set(left + (width - w) / 2f, top + (height - h) / 2f,
                left + (width + w) / 2f, top + (height + h) / 2f);
        p.setAlpha(255); p.setFilterBitmap(true); c.drawBitmap(bitmap, null, rect, p);
    }

    private void glass(Canvas c, float l, float t, float r, float b, float radius, int fill, int stroke) {
        rect.set(l, t, r, b); p.setShader(new LinearGradient(l, t, r, b,
                lighten(fill, 18), fill, Shader.TileMode.CLAMP));
        p.setStyle(Paint.Style.FILL); c.drawRoundRect(rect, radius, radius, p); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.15f); p.setColor(stroke); c.drawRoundRect(rect, radius, radius, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(24, 255, 255, 255)); c.drawLine(l + radius, t + 1, r - radius, t + 1, p);
    }

    private int lighten(int color, int amount) {
        return Color.argb(Color.alpha(color), Math.min(255, Color.red(color) + amount), Math.min(255, Color.green(color) + amount), Math.min(255, Color.blue(color) + amount));
    }

    private void roundRect(Canvas c, float l, float t, float r, float b, float radius, int fill, int stroke, float strokeWidth) {
        rect.set(l, t, r, b); p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(fill); c.drawRoundRect(rect, radius, radius, p);
        if (strokeWidth > 0) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(strokeWidth); p.setColor(stroke); c.drawRoundRect(rect, radius, radius, p); p.setStyle(Paint.Style.FILL); }
    }

    private void text(Canvas c, String value, float x, float y, float size, int color, boolean bold, Paint.Align align) {
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTextSize(size);
        p.setTypeface(android.graphics.Typeface.create("sans-serif", bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        p.setTextAlign(align); c.drawText(value, x, y, p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        float x = (event.getX() - offsetX) / scale;
        float y = (event.getY() - offsetY) / scale;
        if (x >= 310 && y >= 10 && y <= 80) {
            if (listener != null) listener.onExitRequested();
            return true;
        }
        int phase = engine.getPhase();
        if (phase == AnnienvasionEngine.PHASE_RUNWAY) {
            if (y >= 625 && y <= 765) engine.chooseGate(x < 200 ? 0 : 1);
            else if (y >= 250 && y <= 450) engine.chooseGate(x < 200 ? 0 : 1);
        } else if (phase == AnnienvasionEngine.PHASE_BATTLE) {
            if (y >= 675 && y <= 790) {
                if (x < 200) engine.usePounce(); else engine.useRally();
            }
        } else if (phase == AnnienvasionEngine.PHASE_WON) {
            if (y >= 675 && y <= 795 && stage < 50) startStage(stage + 1);
            else if (y >= 675 && y <= 795) startStage(1);
        } else if (phase == AnnienvasionEngine.PHASE_LOST) {
            if (y >= 675 && y <= 795) startStage(stage);
        }
        invalidate();
        return true;
    }
}
