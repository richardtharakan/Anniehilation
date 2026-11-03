package com.rictharakan.anniehilation;

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
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Anniehilation side-scrolling action-platform game.
 *
 * This version generates 56 levels, adds enemies and a forgiving team health
 * system, gives Berthu a large protective health pool, and makes Xingxing the
 * animated end-of-level objective at all 56 level finishes.
 *
 * Save as: app/src/main/java/com/rictharakan/anniehilation/GameView.java
 */
public class GameView extends View {

    private static final int MAX_LEVEL = 56;
    private static final float HERO_W = 42f;
    private static final float HERO_H = 68f;
    private static final float BERTHU_W = 73f;
    private static final float BERTHU_H = 42f;
    private static final float GRAVITY = 1500f;
    private static final float RUN_SPEED = 235f;
    private static final float JUMP_SPEED = 615f;

    private static final float ANNIE_MAX_HP = 150f;
    private static final float BERTHU_MAX_HP = 500f;
    private static final float BERTHU_DAMAGE_SHARE = 0.80f;

    private static final int SKY_TOP = Color.rgb(24, 30, 65);
    private static final int SKY_BOTTOM = Color.rgb(115, 91, 127);
    private static final int STONE = Color.rgb(66, 62, 82);
    private static final int STONE_LIGHT = Color.rgb(113, 103, 125);
    private static final int GOLD = Color.rgb(255, 198, 82);
    private static final int HAIR = Color.rgb(47, 22, 31);
    private static final int OUTFIT = Color.rgb(143, 38, 83);
    private static final int SKIN = Color.rgb(231, 170, 132);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    // Enemy character animation and short attack-only comedy sound effects.
    private final Bitmap[] tigerEnemyFrames = new Bitmap[8];
    private SoundPool enemySoundPool;
    private int enemyFartPunchSoundId;
    private int enemyFartKickSoundId;
    private boolean enemyFartPunchLoaded;
    private boolean enemyFartKickLoaded;
    private final Runnable returnToDashboard;
    private final List<Platform> platforms = new ArrayList<>();
    private final List<Hazard> hazards = new ArrayList<>();
    private final List<Gap> gaps = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();

    private float density;
    private float screenW;
    private float screenH;
    private float groundY;
    private float worldW;
    private float cameraX;

    private float heroX = 75f;
    private float heroY;
    private float velocityY;
    private float berthuX = 35f;
    private float animationTime;
    private float attackTime;
    private float attackCooldown;
    private float hurtCooldown;
    private float berthuRegenDelay;
    private float messageTime;
    private float stageClearTime;
    private float reunionTime;
    private float berthuFlash;
    private float annieFlash;

    private float annieHp = ANNIE_MAX_HP;
    private float berthuHp = BERTHU_MAX_HP;

    private int level = 1;
    private int defeatedEnemies;

    private boolean onGround;
    private boolean facingRight = true;
    private boolean leftHeld;
    private boolean rightHeld;
    private boolean jumpHeld;
    private boolean attackHeld;
    private boolean jumpWasHeld;
    private boolean attackWasHeld;
    private boolean paused;
    private boolean gameOver;
    private boolean levelCleared;
    private boolean reunionActive;
    private boolean won;
    private boolean initialized;

    private long lastFrameNanos;
    private Bitmap xingxingBitmap;
    // Optional transparent animation frames: xingxing_frame_1.png ... _8.png.
    private final Bitmap[] xingxingFrames = new Bitmap[8];

    // Annie sprite frames. Missing files safely fall back to the original drawn character.
    private final Bitmap[] annieIdleFrames = new Bitmap[4];
    private final Bitmap[] annieWalkFrames = new Bitmap[4];
    private final Bitmap[] annieRunFrames = new Bitmap[4];
    private final Bitmap[] annieJumpFrames = new Bitmap[3];
    private final Bitmap[] berthuWalkFrames = new Bitmap[8];
    private Bitmap berthuIdleBitmap;
    private Bitmap annieHurtBitmap;
    private Bitmap annieAttackBitmap;

    public GameView(Context context) {
        this(context, (Runnable) null);
    }

    public GameView(Context context, Runnable returnToDashboard) {
        super(context);
        this.returnToDashboard = returnToDashboard;
        initializeView();
    }

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.returnToDashboard = null;
        initializeView();
    }

    public GameView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.returnToDashboard = null;
        initializeView();
    }

    private void initializeView() {
        density = getResources().getDisplayMetrics().density;
        setFocusable(true);
        setFocusableInTouchMode(true);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        lastFrameNanos = System.nanoTime();
        loadXingxingIfAvailable();
        loadAnnieFramesIfAvailable();
        loadBerthuFramesIfAvailable();
        loadTigerEnemyFramesIfAvailable();
        loadEnemySoundEffectsIfAvailable();
    }

    /**
     * Loads optional animation frames without requiring them at compile time.
     * If the frames are absent, the original xingxing drawable remains the fallback.
     */
    private void loadXingxingIfAvailable() {
        try {
            for (int i = 0; i < xingxingFrames.length; i++) {
                int frameId = getResources().getIdentifier(
                        "xingxing_frame_" + (i + 1),
                        "drawable",
                        getContext().getPackageName());
                if (frameId != 0) {
                    xingxingFrames[i] = BitmapFactory.decodeResource(getResources(), frameId);
                }
            }

            int resourceId = getResources().getIdentifier(
                    "xingxing", "drawable", getContext().getPackageName());
            if (resourceId != 0) {
                xingxingBitmap = BitmapFactory.decodeResource(getResources(), resourceId);
            }

            if (xingxingFrames[0] == null) {
                xingxingFrames[0] = xingxingBitmap;
            }
        } catch (RuntimeException ignored) {
            xingxingBitmap = null;
        }
    }

    /** Load transparent Annie frames from res/drawable without hard-coded generated R ids. */
    private void loadAnnieFramesIfAvailable() {
        try {
            loadFrameGroup(annieIdleFrames, "annie_idle_");
            loadFrameGroup(annieWalkFrames, "annie_walk_");
            loadFrameGroup(annieRunFrames, "annie_run_");
            loadFrameGroup(annieJumpFrames, "annie_jump_");
            annieHurtBitmap = loadDrawableBitmap("annie_hurt");
            annieAttackBitmap = loadDrawableBitmap("annie_attack");
        } catch (RuntimeException ignored) {
            // Missing assets must never prevent the game from launching.
        }
    }

    private void loadBerthuFramesIfAvailable() {
        try {
            loadFrameGroup(berthuWalkFrames, "berthu_walk_");
            berthuIdleBitmap = loadDrawableBitmap("berthu_idle");
        } catch (RuntimeException ignored) {
            // Missing optional cat art must never stop the game from opening.
        }
    }

    /** Load the eight cut-out poses used by the recurring action-hero enemy. */
    private void loadTigerEnemyFramesIfAvailable() {
        String[] names = {
                "tiger_hero_guard",
                "tiger_hero_run",
                "tiger_hero_block",
                "tiger_hero_punch",
                "tiger_hero_kick",
                "tiger_hero_spin_kick",
                "tiger_hero_hit",
                "tiger_hero_down"
        };
        try {
            for (int i = 0; i < names.length; i++) {
                tigerEnemyFrames[i] = loadDrawableBitmap(names[i]);
            }
        } catch (RuntimeException ignored) {
            // The original drawn enemy remains available if an image is missing.
        }
    }

    /** Load low-latency attack sounds from res/raw. Missing audio never blocks gameplay. */
    private void loadEnemySoundEffectsIfAvailable() {
        try {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            enemySoundPool = new SoundPool.Builder()
                    .setAudioAttributes(attributes)
                    .setMaxStreams(4)
                    .build();

            enemySoundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
                if (status != 0) return;
                if (sampleId == enemyFartPunchSoundId) enemyFartPunchLoaded = true;
                if (sampleId == enemyFartKickSoundId) enemyFartKickLoaded = true;
            });

            int punchId = getResources().getIdentifier(
                    "tiger_fart_punch", "raw", getContext().getPackageName());
            int kickId = getResources().getIdentifier(
                    "tiger_fart_kick", "raw", getContext().getPackageName());

            if (punchId != 0) enemyFartPunchSoundId = enemySoundPool.load(getContext(), punchId, 1);
            if (kickId != 0) enemyFartKickSoundId = enemySoundPool.load(getContext(), kickId, 1);
        } catch (RuntimeException ignored) {
            if (enemySoundPool != null) {
                enemySoundPool.release();
                enemySoundPool = null;
            }
        }
    }

    /** Each enemy strike gets one of two goofy sounds; the kick has its own lower rasp. */
    private void playEnemyAttackSound(boolean kick) {
        if (enemySoundPool == null) return;

        int preferredId = kick ? enemyFartKickSoundId : enemyFartPunchSoundId;
        boolean preferredLoaded = kick ? enemyFartKickLoaded : enemyFartPunchLoaded;
        int fallbackId = kick ? enemyFartPunchSoundId : enemyFartKickSoundId;
        boolean fallbackLoaded = kick ? enemyFartPunchLoaded : enemyFartKickLoaded;

        if (preferredId != 0 && preferredLoaded) {
            enemySoundPool.play(preferredId, 0.92f, 0.92f, 1, 0, kick ? 0.94f : 1.08f);
        } else if (fallbackId != 0 && fallbackLoaded) {
            enemySoundPool.play(fallbackId, 0.92f, 0.92f, 1, 0, 1.0f);
        }
    }

    private void loadFrameGroup(Bitmap[] target, String prefix) {
        for (int i = 0; i < target.length; i++) {
            target[i] = loadDrawableBitmap(prefix + (i + 1));
        }
    }

    private Bitmap loadDrawableBitmap(String name) {
        int id = getResources().getIdentifier(name, "drawable", getContext().getPackageName());
        return id == 0 ? null : BitmapFactory.decodeResource(getResources(), id);
    }

    private Bitmap chooseAnimationFrame(Bitmap[] frames, int index) {
        if (frames == null || frames.length == 0) return null;
        for (int offset = 0; offset < frames.length; offset++) {
            Bitmap frame = frames[(index + offset) % frames.length];
            if (frame != null && !frame.isRecycled()) return frame;
        }
        return null;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        screenW = w / density;
        screenH = h / density;
        groundY = screenH * 0.79f;

        if (!initialized) {
            initialized = true;
            loadLevel(1);
        } else {
            heroY = Math.min(heroY, groundY - HERO_H);
        }
    }

    private void loadLevel(int newLevel) {
        level = Math.max(1, Math.min(MAX_LEVEL, newLevel));
        platforms.clear();
        hazards.clear();
        gaps.clear();
        enemies.clear();

        cameraX = 0f;
        heroX = 75f;
        heroY = groundY - HERO_H;
        berthuX = 35f;
        velocityY = 0f;
        onGround = true;
        facingRight = true;

        paused = false;
        gameOver = false;
        levelCleared = false;
        reunionActive = false;
        reunionTime = 0f;
        won = false;
        attackTime = 0f;
        attackCooldown = 0f;
        hurtCooldown = 0f;
        berthuRegenDelay = 0f;
        stageClearTime = 0f;
        berthuFlash = 0f;
        annieFlash = 0f;
        defeatedEnemies = 0;
        releaseControls();

        // Level length and obstacle density gradually increase through all 56 levels.
        worldW = 1850f + level * 34f;
        generateLevelLayout();
        lastFrameNanos = System.nanoTime();
        invalidate();
    }

    private void generateLevelLayout() {
        int chunks = (int) ((worldW - 400f) / 220f);
        int gapInterval = Math.max(2, 5 - (level - 1) / 15);
        float gapWidth = 44f + Math.min(27f, level * 0.45f);

        // Each level has a deterministic pattern, with difficulty increasing over time.
        int pattern = level % 7;
        for (int i = 0; i < chunks; i++) {
            float x = 205f + i * 220f;
            boolean milestoneBossSlot = level % 7 == 0 && i == chunks / 2;
            boolean gapChunk = i > 0 && (i + pattern) % gapInterval == 0
                    && !milestoneBossSlot;

            if (gapChunk) {
                float gapStart = x + 91f;
                float gapEnd = gapStart + gapWidth;
                gaps.add(new Gap(gapStart, gapEnd));

                // Some gaps have a stepping stone above them.
                if ((i + level) % 2 == 0) {
                    platforms.add(new Platform(gapStart - 10f,
                            gapWidth + 24f, 52f + (level % 4) * 8f));
                }
            }

            // Raised ledges with reachable heights.
            if ((i + level) % 3 != 1) {
                float platformX = x + 45f + ((i + pattern) % 2) * 26f;
                float platformHeight = 48f + ((i + level) % 5) * 11f;
                platforms.add(new Platform(platformX, 74f + (level % 3) * 7f,
                        platformHeight));
            }

            // Floor spikes. Level 1 teaches the basics; later levels add more.
            int hazardFrequency = Math.max(2, 5 - level / 18);
            if ((i + pattern) % hazardFrequency == 1 && !gapChunk) {
                float hazardX = x + 16f;
                hazards.add(new Hazard(hazardX, 27f + (level % 3) * 4f,
                        (level % 9 == 0)));
            }

            // Enemies patrol a section of the floor and attack if Annie gets close.
            int enemyFrequency = Math.max(2, 4 - level / 20);
            if (((i + pattern) % enemyFrequency == 0 || milestoneBossSlot)
                    && !gapChunk) {
                boolean boss = milestoneBossSlot;
                float enemyX = x + 112f;
                float hp = boss ? 145f + level * 0.8f : 38f + level * 0.65f;
                enemies.add(new Enemy(enemyX, hp, boss,
                        Math.max(35f, 54f - level * 0.15f)));
            }
        }

        // Ensure each level ends with a safe approach to its exit.
        platforms.add(new Platform(worldW - 310f, 95f, 48f));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!initialized || screenW <= 0f || screenH <= 0f) {
            canvas.drawColor(Color.BLACK);
            postInvalidateOnAnimation();
            return;
        }

        long now = System.nanoTime();
        float dt = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        dt = Math.max(0f, Math.min(dt, 0.032f));
        animationTime += dt;

        if (!paused && !gameOver && !won) {
            updateGame(dt);
        }

        canvas.save();
        canvas.scale(density, density);
        drawBackground(canvas);
        canvas.save();
        canvas.translate(-cameraX, 0f);
        drawWorld(canvas);
        drawBerthu(canvas);
        drawAnnie(canvas);
        if (reunionActive) drawReunionEffects(canvas);
        canvas.restore();
        drawHud(canvas);

        if (paused) {
            drawOverlay(canvas, "PAUSED", "Tap to resume", "Use EXIT to return to dashboard");
        } else if (gameOver) {
            drawOverlay(canvas, "ANNIE NEEDS A REST", "Tap to retry this level", "Berthu is catching his breath!");
        } else if (reunionActive) {
            drawOverlay(canvas, "XINGXING REUNION!", "Annie and Xingxing are reunited", "The next adventure is coming...");
        } else if (levelCleared) {
            drawOverlay(canvas, "YOU REACHED XINGXING!", "Level " + level + " complete — next adventure loading...", "");
        } else if (won) {
            drawOverlay(canvas, "XINGXING'S KINGDOM!", "All 56 levels completed", "Tap to play again");
        }
        canvas.restore();
        postInvalidateOnAnimation();
    }

    private void updateGame(float dt) {
        if (hurtCooldown > 0f) hurtCooldown -= dt;
        if (attackCooldown > 0f) attackCooldown -= dt;
        if (attackTime > 0f) attackTime -= dt;
        if (berthuFlash > 0f) berthuFlash -= dt;
        if (annieFlash > 0f) annieFlash -= dt;
        if (messageTime > 0f) messageTime -= dt;

        if (berthuRegenDelay > 0f) {
            berthuRegenDelay -= dt;
        } else if (berthuHp > 0f && berthuHp < BERTHU_MAX_HP) {
            // Berthu slowly recovers after surviving a few seconds without a hit.
            berthuHp = Math.min(BERTHU_MAX_HP, berthuHp + 1.8f * dt);
        }

        if (levelCleared) {
            stageClearTime -= dt;
            if (stageClearTime <= 0f) {
                if (level < MAX_LEVEL) {
                    loadLevel(level + 1);
                } else {
                    levelCleared = false;
                    won = true;
                    releaseControls();
                }
            }
            return;
        }

        // Freeze the gameplay during the reunion so the hug can be seen clearly.
        if (reunionActive) {
            reunionTime += dt;
            heroX = worldW - 128f - HERO_W + 8f;
            heroY = groundY - HERO_H;
            velocityY = 0f;
            onGround = true;
            facingRight = true;
            releaseControls();
            cameraX = Math.max(0f, Math.min(worldW - screenW,
                    heroX - screenW * 0.34f));
            if (reunionTime >= 2.35f) {
                reunionActive = false;
                levelCleared = true;
                stageClearTime = 1.25f;
            }
            return;
        }

        if (jumpHeld && !jumpWasHeld && onGround) {
            velocityY = -JUMP_SPEED;
            onGround = false;
        }

        if (attackHeld && !attackWasHeld && attackCooldown <= 0f) {
            attackTime = 0.24f;
            attackCooldown = 0.28f;
            attackNearbyTargets();
        }
        jumpWasHeld = jumpHeld;
        attackWasHeld = attackHeld;

        float movement = 0f;
        if (leftHeld) movement -= 1f;
        if (rightHeld) movement += 1f;
        if (movement != 0f) {
            facingRight = movement > 0f;
            heroX += movement * RUN_SPEED * dt;
        }
        heroX = Math.max(0f, Math.min(worldW - HERO_W, heroX));

        float previousBottom = heroY + HERO_H;
        velocityY += GRAVITY * dt;
        heroY += velocityY * dt;
        onGround = false;

        if (velocityY >= 0f) {
            for (Platform platform : platforms) {
                float platformY = groundY - platform.heightAboveGround;
                boolean overlapsX = heroX + HERO_W - 5f > platform.x
                        && heroX + 5f < platform.x + platform.width;
                if (overlapsX && previousBottom <= platformY + 4f
                        && heroY + HERO_H >= platformY) {
                    heroY = platformY - HERO_H;
                    velocityY = 0f;
                    onGround = true;
                }
            }
        }

        float footX = heroX + HERO_W * 0.5f;
        if (velocityY >= 0f && previousBottom <= groundY + 2f
                && heroY + HERO_H >= groundY && isGroundAt(footX)) {
            heroY = groundY - HERO_H;
            velocityY = 0f;
            onGround = true;
        }
        if (heroY > groundY - HERO_H && isGroundAt(footX)) {
            heroY = groundY - HERO_H;
            velocityY = 0f;
            onGround = true;
        }

        updateEnemies(dt);
        checkHazards();

        if (heroY > screenH + 125f) {
            takeDamage(38f);
            respawnNearCurrentPosition();
        }

        float targetBerthuX = facingRight
                ? heroX - BERTHU_W - 7f : heroX + HERO_W + 7f;
        targetBerthuX = Math.max(0f, Math.min(worldW - BERTHU_W, targetBerthuX));
        berthuX += (targetBerthuX - berthuX) * Math.min(1f, dt * 9f);

        // Reaching Xingxing starts a dedicated reunion animation instead of
        // skipping straight to the level-complete overlay.
        if (heroX >= worldW - 125f && onGround) {
            reunionActive = true;
            reunionTime = 0f;
            heroX = worldW - 128f - HERO_W + 8f;
            heroY = groundY - HERO_H;
            velocityY = 0f;
            facingRight = true;
            releaseControls();
        }

        cameraX = Math.max(0f, Math.min(worldW - screenW,
                heroX - screenW * 0.34f));
    }

    private void updateEnemies(float dt) {
        Iterator<Enemy> iterator = enemies.iterator();
        while (iterator.hasNext()) {
            Enemy enemy = iterator.next();
            enemy.attackCooldown -= dt;
            enemy.attackFlash = Math.max(0f, enemy.attackFlash - dt);
            enemy.hitFlash = Math.max(0f, enemy.hitFlash - dt);

            float distance = heroX + HERO_W / 2f - (enemy.x + enemy.width / 2f);
            enemy.isMoving = false;
            enemy.playerNearby = Math.abs(distance) < 120f;
            if (Math.abs(distance) < 155f) {
                enemy.facingRight = distance > 0f;
                if (Math.abs(distance) > 38f) {
                    enemy.isMoving = true;
                    enemy.x += Math.signum(distance) * enemy.speed * dt;
                    enemy.x = Math.max(enemy.patrolMin,
                            Math.min(enemy.patrolMax, enemy.x));
                }
            } else {
                enemy.isMoving = true;
                enemy.x += enemy.direction * enemy.speed * 0.35f * dt;
                if (enemy.x < enemy.patrolMin || enemy.x > enemy.patrolMax) {
                    enemy.direction *= -1f;
                    enemy.x = Math.max(enemy.patrolMin,
                            Math.min(enemy.patrolMax, enemy.x));
                }
            }

            boolean closeEnough = Math.abs(distance) < (enemy.boss ? 78f : 58f);
            boolean sameFloor = Math.abs((heroY + HERO_H) - groundY) < 24f;
            if (closeEnough && sameFloor && enemy.attackCooldown <= 0f
                    && hurtCooldown <= 0f) {
                // Alternate a punch and a kick so the matching sound follows the visible pose.
                enemy.attackPose = enemy.attackCount % 2;
                enemy.attackCount++;
                enemy.attackFlash = 0.38f;
                enemy.attackCooldown = enemy.boss ? 1.35f : 1.55f;
                playEnemyAttackSound(enemy.attackPose == 1);
                takeDamage(enemy.boss ? 20f : 12f);
            }
        }
    }

    private boolean isGroundAt(float x) {
        for (Gap gap : gaps) {
            if (x > gap.start && x < gap.end) return false;
        }
        return true;
    }

    private void checkHazards() {
        if (hurtCooldown > 0f) return;
        float left = heroX + 4f;
        float right = heroX + HERO_W - 4f;
        float feet = heroY + HERO_H;
        for (Hazard hazard : hazards) {
            boolean overlapX = right > hazard.x && left < hazard.x + hazard.width;
            boolean nearFloor = feet > groundY - 24f && feet < groundY + 6f;
            if (overlapX && nearFloor) {
                takeDamage(hazard.heavy ? 22f : 14f);
                return;
            }
        }
    }

    private void attackNearbyTargets() {
        float attackStart = facingRight ? heroX + HERO_W : heroX - 62f;
        float attackEnd = facingRight ? heroX + HERO_W + 62f : heroX;

        Iterator<Enemy> enemyIterator = enemies.iterator();
        while (enemyIterator.hasNext()) {
            Enemy enemy = enemyIterator.next();
            boolean inRange = enemy.x + enemy.width >= attackStart
                    && enemy.x <= attackEnd;
            boolean closeVertically = heroY + HERO_H > groundY - 115f;
            if (inRange && closeVertically) {
                enemy.hp -= 42f;
                enemy.hitFlash = 0.22f;
                if (enemy.hp <= 0f) {
                    enemyIterator.remove();
                    defeatedEnemies++;
                }
            }
        }

        // Sword strikes can disable nearby spikes as well as hit enemies.
        Iterator<Hazard> hazardIterator = hazards.iterator();
        while (hazardIterator.hasNext()) {
            Hazard hazard = hazardIterator.next();
            if (hazard.x + hazard.width >= attackStart && hazard.x <= attackEnd
                    && Math.abs((heroY + HERO_H) - groundY) < 100f) {
                hazardIterator.remove();
            }
        }
    }

    /** Team damage: Berthu absorbs 80% while he still has health. */
    private void takeDamage(float amount) {
        if (hurtCooldown > 0f || gameOver || won || levelCleared) return;
        hurtCooldown = 0.72f;
        berthuRegenDelay = 5f;

        if (berthuHp > 0f) {
            float berthuDamage = amount * BERTHU_DAMAGE_SHARE;
            float annieDamage = amount - berthuDamage;
            berthuHp = Math.max(0f, berthuHp - berthuDamage);
            annieHp = Math.max(0f, annieHp - annieDamage);
            berthuFlash = 0.30f;
            annieFlash = 0.18f;
        } else {
            annieHp = Math.max(0f, annieHp - amount);
            annieFlash = 0.25f;
        }

        if (annieHp <= 0f) {
            gameOver = true;
            releaseControls();
        }
    }

    private void respawnNearCurrentPosition() {
        heroX = Math.max(65f, heroX - 100f);
        heroY = groundY - HERO_H;
        velocityY = 0f;
        onGround = true;
        cameraX = Math.max(0f, Math.min(worldW - screenW,
                heroX - screenW * 0.34f));
    }

    private void drawBackground(Canvas canvas) {
        paint.setShader(new LinearGradient(0f, 0f, 0f, screenH,
                SKY_TOP, SKY_BOTTOM, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0f, 0f, screenW, screenH, paint);
        paint.setShader(null);

        // Moon and distant silhouette.
        paint.setColor(Color.rgb(255, 222, 176));
        canvas.drawCircle(screenW * 0.78f, screenH * 0.20f, 24f, paint);
        Path hills = new Path();
        hills.moveTo(0f, groundY);
        float offset = cameraX * 0.13f;
        for (int i = -1; i < 8; i++) {
            float x = i * 190f - (offset % 190f);
            hills.lineTo(x, groundY - 95f - (i % 3) * 25f);
            hills.lineTo(x + 95f, groundY - 28f);
        }
        hills.lineTo(screenW, groundY);
        hills.close();
        paint.setColor(Color.rgb(56, 54, 87));
        canvas.drawPath(hills, paint);
        paint.setColor(Color.rgb(43, 43, 69));
        canvas.drawRect(0f, groundY, screenW, screenH, paint);
    }

    private void drawWorld(Canvas canvas) {
        float segmentStart = 0f;
        for (Gap gap : gaps) {
            drawGroundSegment(canvas, segmentStart, gap.start);
            segmentStart = gap.end;
        }
        drawGroundSegment(canvas, segmentStart, worldW);

        for (Platform platform : platforms) {
            float y = groundY - platform.heightAboveGround;
            paint.setColor(STONE);
            canvas.drawRoundRect(platform.x, y, platform.x + platform.width,
                    y + 18f, 3f, 3f, paint);
            paint.setColor(STONE_LIGHT);
            canvas.drawRect(platform.x, y, platform.x + platform.width,
                    y + 4f, paint);
            paint.setColor(Color.argb(75, 19, 19, 35));
            for (float x = platform.x + 16f; x < platform.x + platform.width; x += 34f) {
                canvas.drawRect(x, y + 8f, x + 2f, y + 16f, paint);
            }
        }

        for (Hazard hazard : hazards) drawSpikes(canvas, hazard);
        for (Enemy enemy : enemies) drawEnemy(canvas, enemy);
        drawLevelGoal(canvas);
    }

    private void drawGroundSegment(Canvas canvas, float start, float end) {
        if (end <= start) return;
        paint.setColor(STONE);
        canvas.drawRect(start, groundY, end, screenH + 35f, paint);
        paint.setColor(Color.rgb(165, 132, 101));
        canvas.drawRect(start, groundY, end, groundY + 7f, paint);
        paint.setColor(Color.argb(65, 25, 21, 37));
        for (float x = start + 15f; x < end; x += 65f) {
            canvas.drawRect(x, groundY + 22f, x + 23f, groundY + 25f, paint);
            canvas.drawRect(x + 30f, groundY + 55f, x + 46f, groundY + 58f, paint);
        }
    }

    private void drawSpikes(Canvas canvas, Hazard hazard) {
        float spikeWidth = 15f;
        paint.setColor(hazard.heavy ? Color.rgb(255, 104, 88) : Color.rgb(193, 195, 207));
        for (float sx = hazard.x; sx < hazard.x + hazard.width; sx += spikeWidth) {
            Path spike = new Path();
            spike.moveTo(sx, groundY);
            spike.lineTo(sx + spikeWidth / 2f, groundY - (hazard.heavy ? 34f : 25f));
            spike.lineTo(sx + spikeWidth, groundY);
            spike.close();
            canvas.drawPath(spike, paint);
        }
    }

    private void drawEnemy(Canvas canvas, Enemy enemy) {
        float drawHeight = enemy.boss ? 104f : 88f;
        float centerX = enemy.x + enemy.width / 2f;
        Bitmap frame = null;

        if (enemy.hitFlash > 0f) {
            frame = enemy.hitFlash > 0.11f ? tigerEnemyFrames[6] : tigerEnemyFrames[7];
        } else if (enemy.attackFlash > 0f) {
            if (enemy.attackPose == 1) {
                frame = enemy.boss ? tigerEnemyFrames[5] : tigerEnemyFrames[4];
            } else {
                frame = tigerEnemyFrames[3];
            }
        } else if (enemy.isMoving) {
            frame = tigerEnemyFrames[1];
        } else if (enemy.playerNearby) {
            frame = tigerEnemyFrames[2];
        } else {
            frame = tigerEnemyFrames[0];
        }

        float barY;
        if (frame != null && !frame.isRecycled()) {
            float drawWidth = drawHeight * frame.getWidth() / (float) frame.getHeight();
            float x = centerX - drawWidth / 2f;
            float y = groundY - drawHeight;
            // A soft contact shadow keeps the character planted on the platform.
            paint.setColor(Color.argb(74, 8, 7, 15));
            canvas.drawOval(centerX - drawWidth * 0.34f, groundY - 5f,
                    centerX + drawWidth * 0.34f, groundY + 4f, paint);

            RectF destination = new RectF(x, y, x + drawWidth, groundY);
            canvas.save();
            if (!enemy.facingRight) {
                canvas.scale(-1f, 1f, centerX, groundY - drawHeight / 2f);
            }
            paint.setAlpha(enemy.hitFlash > 0f ? 220 : 255);
            paint.setFilterBitmap(true);
            canvas.drawBitmap(frame, null, destination, paint);
            paint.setFilterBitmap(false);
            paint.setAlpha(255);
            canvas.restore();
            barY = y - 9f;
        } else {
            // Safe fallback if the optional fighter artwork is missing.
            float h = enemy.boss ? 58f : 43f;
            float y = groundY - h;
            float w = enemy.width;
            paint.setColor(enemy.hitFlash > 0f ? Color.WHITE
                    : (enemy.boss ? Color.rgb(105, 28, 55) : Color.rgb(64, 39, 54)));
            canvas.drawRoundRect(enemy.x + 3f, y + 12f, enemy.x + w - 3f,
                    groundY - 3f, 7f, 7f, paint);
            paint.setColor(enemy.boss ? Color.rgb(174, 54, 76) : Color.rgb(117, 71, 85));
            canvas.drawCircle(centerX, y + 10f, enemy.boss ? 14f : 10f, paint);
            paint.setColor(Color.rgb(255, 211, 111));
            float eyeX = enemy.facingRight ? enemy.x + w * 0.62f : enemy.x + w * 0.38f;
            canvas.drawCircle(eyeX, y + 9f, 2.3f, paint);
            if (enemy.attackFlash > 0f) {
                paint.setColor(Color.argb(190, 255, 100, 84));
                canvas.drawCircle(enemy.facingRight ? enemy.x + w + 12f : enemy.x - 12f,
                        y + 24f, 12f, paint);
            }
            barY = y - 8f;
        }

        // Enemy health bar, kept visible above the sprite in every animation pose.
        float barW = enemy.width + (enemy.boss ? 22f : 16f);
        float barX = centerX - barW / 2f;
        paint.setColor(Color.rgb(50, 28, 39));
        canvas.drawRoundRect(barX, barY, barX + barW, barY + 5f, 2f, 2f, paint);
        paint.setColor(Color.rgb(228, 76, 87));
        canvas.drawRoundRect(barX, barY,
                barX + barW * Math.max(0f, enemy.hp / enemy.maxHp), barY + 5f,
                2f, 2f, paint);
        if (enemy.boss) {
            paint.setColor(GOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(10f);
            paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            canvas.drawText("ACTION BOSS", centerX, barY - 4f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
    }

    /**
     * Xingxing is the end-of-level objective in EVERY level. There is no exit door:
     * Annie's goal is to run through the stage and reach him. His supplied PNG
     * gently bobs and sways so he feels alive; a drawn fallback keeps the game
     * playable if the image resource has not been added yet.
     */
    private void drawLevelGoal(Canvas canvas) {
        // Loop through the celebration frames at about 7 frames per second.
        int frameIndex = ((int) (animationTime * (reunionActive ? 5.5f : 7.5f))) % xingxingFrames.length;
        Bitmap activeFrame = xingxingFrames[frameIndex];
        if (activeFrame == null) {
            activeFrame = xingxingBitmap;
        }

        float monkeyW = 82f;
        float monkeyH = activeFrame != null && activeFrame.getWidth() > 0
                ? monkeyW * activeFrame.getHeight() / (float) activeFrame.getWidth()
                : 96f;

        // At level end Xingxing visibly rushes the last few steps toward Annie, then stays close
        // for the reunion. Outside the reunion he remains the animated end-of-level objective.
        float approachProgress = reunionActive
                ? Math.max(0f, Math.min(1f, reunionTime / 0.62f)) : 1f;
        float approachOffset = (1f - approachProgress) * 42f;
        float x = worldW - 128f + approachOffset;
        float bounce = (float) Math.abs(Math.sin(animationTime * 3.1f)) * 5f;
        float sway = (float) Math.sin(animationTime * 1.7f) * 3f;
        float y = groundY - monkeyH - bounce;

        // Warm, pulsing glow marks him as the objective without drawing a door.
        int glowAlpha = 28 + (int) (18f * (0.5f + 0.5f * Math.sin(animationTime * 2.4f)));
        paint.setColor(Color.argb(glowAlpha, 255, 198, 82));
        canvas.drawOval(x - 8f, groundY - 10f, x + monkeyW + 8f, groundY + 5f, paint);

        // A small floating nameplate makes the objective clear in every level.
        float tagY = Math.max(26f, y - 20f);
        paint.setColor(Color.argb(220, 34, 25, 48));
        canvas.drawRoundRect(x + 5f, tagY - 13f, x + monkeyW - 5f, tagY + 5f,
                7f, 7f, paint);
        paint.setColor(GOLD);
        paint.setTextSize(11f);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("XINGXING", x + monkeyW / 2f, tagY, paint);

        RectF destination = new RectF(x + sway, y, x + sway + monkeyW, groundY - bounce);
        canvas.save();
        float tilt = (float) Math.sin(animationTime * 2.1f) * 3f;
        canvas.rotate(tilt, destination.centerX(), destination.bottom - monkeyH * 0.42f);
        if (activeFrame != null) {
            paint.setAlpha(255);
            paint.setFilterBitmap(true);
            canvas.drawBitmap(activeFrame, null, destination, paint);
            paint.setFilterBitmap(false);
        } else {
            drawFallbackMonkey(canvas, destination);
        }
        canvas.restore();
        paint.setAlpha(255);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** Draws a visible embrace cue while Annie and Xingxing meet. */
    private void drawReunionEffects(Canvas canvas) {
        float progress = Math.max(0f, Math.min(1f, reunionTime / 0.62f));
        float pulse = 1f + 0.12f * (float) Math.sin(animationTime * 8f);
        // Keep the effects attached to Xingxing while he moves in, then center them over the hug.
        float hugX = worldW - 105f + (1f - progress) * 42f;
        float hugY = groundY - 78f;

        // Animated curved arms bridge the small gap between Annie and Xingxing.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(5.5f);
        paint.setColor(Color.rgb(255, 205, 151));
        Path embrace = new Path();
        embrace.moveTo(heroX + HERO_W - 5f, heroY + 28f);
        embrace.cubicTo(heroX + HERO_W + 13f, heroY + 14f,
                hugX + 7f, heroY + 20f, hugX + 10f, heroY + 39f);
        canvas.drawPath(embrace, paint);
        paint.setStyle(Paint.Style.FILL);

        // A little heart rises once Xingxing has begun closing the gap.
        if (reunionTime > 0.18f) {
            float heartScale = pulse * Math.min(1f, (reunionTime - 0.18f) / 0.35f);
            float hx = hugX + 5f;
            float hy = hugY - Math.min(12f, reunionTime * 5f);
            canvas.save();
            canvas.scale(heartScale, heartScale, hx, hy);
            paint.setColor(Color.rgb(255, 105, 145));
            Path heart = new Path();
            heart.moveTo(hx, hy + 8f);
            heart.cubicTo(hx - 20f, hy - 3f, hx - 11f, hy - 13f, hx, hy - 5f);
            heart.cubicTo(hx + 11f, hy - 13f, hx + 20f, hy - 3f, hx, hy + 8f);
            heart.close();
            canvas.drawPath(heart, paint);
            canvas.restore();
        }

        paint.setColor(Color.argb(185, 255, 215, 117));
        for (int i = 0; i < 4; i++) {
            float angle = animationTime * 2.5f + i * (float) Math.PI / 2f;
            float sx = hugX + (float) Math.cos(angle) * (27f + 3f * pulse);
            float sy = hugY - 4f + (float) Math.sin(angle) * 19f;
            canvas.drawCircle(sx, sy, 2.2f, paint);
        }
    }

    private void drawFallbackMonkey(Canvas canvas, RectF bounds) {
        float cx = bounds.centerX();
        float cy = bounds.centerY();
        paint.setColor(Color.rgb(110, 84, 72));
        canvas.drawOval(bounds, paint);
        paint.setColor(Color.rgb(190, 157, 129));
        canvas.drawOval(cx - 17f, cy - 5f, cx + 17f, cy + 20f, paint);
        paint.setColor(Color.rgb(49, 34, 31));
        canvas.drawCircle(cx - 8f, cy - 3f, 2.5f, paint);
        canvas.drawCircle(cx + 8f, cy - 3f, 2.5f, paint);
        paint.setColor(Color.rgb(235, 200, 170));
        canvas.drawOval(cx - 10f, cy + 5f, cx + 10f, cy + 14f, paint);
    }

    private void drawAnnie(Canvas canvas) {
        if (annieFlash > 0f && ((int) (animationTime * 22f)) % 2 == 0) return;

        boolean moving = leftHeld || rightHeld;
        int frameIndex = Math.abs((int) (animationTime * (moving ? 9f : 3.5f)));
        Bitmap frame = null;

        if (reunionActive) {
            // Return to the original clean anime Annie sprite during the reunion.
            // The saved concept sheet contains no dedicated embrace pose; the
            // in-world curved-arm, heart, and sparkle effects provide the hug cue.
            frame = chooseAnimationFrame(annieIdleFrames,
                    (int) (animationTime * 2.4f));
        } else if (annieFlash > 0f && annieHurtBitmap != null) {
            frame = annieHurtBitmap;
        } else if (attackTime > 0f && annieAttackBitmap != null) {
            frame = annieAttackBitmap;
        } else if (!onGround) {
            frame = chooseAnimationFrame(annieJumpFrames, frameIndex);
        } else if (moving && Math.abs(rightHeld ? 1f : -1f) > 0f) {
            // Use running frames for active movement; the slower walk cycle remains the fallback.
            frame = chooseAnimationFrame(annieRunFrames, frameIndex);
            if (frame == null) frame = chooseAnimationFrame(annieWalkFrames, frameIndex);
        } else {
            frame = chooseAnimationFrame(annieIdleFrames, frameIndex);
        }

        // The generated character artwork is portrait-oriented; preserve aspect ratio so Annie
        // stays recognizable rather than stretching her face, hair, or uniform.
        if (frame != null && !frame.isRecycled()) {
            float drawH = HERO_H + 7f;
            float drawW = drawH * frame.getWidth() / (float) frame.getHeight();
            // Running poses are wider; cap width without distorting the animation.
            float maxDrawW = HERO_W * 1.75f;
            if (drawW > maxDrawW) {
                drawW = maxDrawW;
                drawH = drawW * frame.getHeight() / (float) frame.getWidth();
            }
            float centerX = heroX + HERO_W * 0.5f;
            float bottom = heroY + HERO_H + 2f;
            float top = bottom - drawH;
            RectF destination = new RectF(centerX - drawW * 0.5f, top,
                    centerX + drawW * 0.5f, bottom);
            canvas.save();
            if (!facingRight) canvas.scale(-1f, 1f, centerX, heroY + HERO_H * 0.5f);
            paint.setAlpha(255);
            paint.setFilterBitmap(true);
            canvas.drawBitmap(frame, null, destination, paint);
            paint.setFilterBitmap(false);
            canvas.restore();
            return;
        }

        // Procedural fallback preserves a playable character if the PNG assets were not copied.
        float x = heroX;
        float y = heroY;
        float walk = moving ? (float) Math.sin(animationTime * 12f) * 5f : 0f;
        canvas.save();
        if (!facingRight) canvas.scale(-1f, 1f, x + HERO_W / 2f, y);

        paint.setColor(HAIR);
        Path ponytail = new Path();
        ponytail.moveTo(x + 15f, y + 8f);
        ponytail.cubicTo(x - 8f, y + 4f, x + 2f, y + 29f + walk, x - 5f, y + 36f + walk);
        ponytail.cubicTo(x + 19f, y + 31f + walk, x + 25f, y + 17f, x + 21f, y + 9f);
        ponytail.close();
        canvas.drawPath(ponytail, paint);

        paint.setStrokeWidth(7f);
        paint.setColor(SKIN);
        canvas.drawLine(x + 13f, y + 45f, x + 11f + walk, y + 59f, paint);
        canvas.drawLine(x + 28f, y + 45f, x + 31f - walk, y + 59f, paint);
        paint.setStrokeWidth(8f);
        paint.setColor(Color.rgb(25, 25, 31));
        canvas.drawLine(x + 11f + walk, y + 59f, x + 19f + walk, y + 64f, paint);
        canvas.drawLine(x + 31f - walk, y + 59f, x + 39f - walk, y + 64f, paint);

        // Reference-inspired white school shirt and pleated skirt.
        paint.setColor(Color.rgb(245, 242, 236));
        Path skirt = new Path();
        skirt.moveTo(x + 11f, y + 35f);
        skirt.lineTo(x + 33f, y + 35f);
        skirt.lineTo(x + 39f, y + 51f);
        skirt.lineTo(x + 5f, y + 51f);
        skirt.close();
        canvas.drawPath(skirt, paint);
        paint.setColor(Color.rgb(35, 50, 78));
        canvas.drawRoundRect(x + 12f, y + 17f, x + 32f, y + 39f, 5f, 5f, paint);
        paint.setColor(Color.rgb(244, 242, 234));
        canvas.drawRoundRect(x + 13f, y + 15f, x + 31f, y + 36f, 4f, 4f, paint);
        paint.setColor(Color.rgb(28, 48, 78));
        Path tie = new Path();
        tie.moveTo(x + 21f, y + 18f); tie.lineTo(x + 26f, y + 18f);
        tie.lineTo(x + 25f, y + 32f); tie.lineTo(x + 23f, y + 36f);
        tie.lineTo(x + 21f, y + 32f); tie.close();
        canvas.drawPath(tie, paint);
        paint.setColor(GOLD);
        canvas.drawLine(x + 22f, y + 22f, x + 25f, y + 23f, paint);
        canvas.drawLine(x + 22f, y + 27f, x + 25f, y + 28f, paint);

        paint.setColor(SKIN);
        paint.setStrokeWidth(5f);
        canvas.drawLine(x + 12f, y + 22f, x + 5f, y + 35f + walk, paint);
        canvas.drawLine(x + 31f, y + 22f, x + 37f, y + 34f - walk, paint);
        paint.setColor(Color.rgb(35, 50, 78));
        canvas.drawRect(x + 10f + walk, y + 54f, x + 19f + walk, y + 57f, paint);
        canvas.drawRect(x + 30f - walk, y + 54f, x + 39f - walk, y + 57f, paint);
        paint.setColor(SKIN);
        canvas.drawRect(x + 17f, y + 9f, x + 26f, y + 20f, paint);
        canvas.drawOval(x + 10f, y - 1f, x + 31f, y + 19f, paint);
        paint.setColor(HAIR);
        canvas.drawArc(x + 7f, y - 5f, x + 34f, y + 16f, 180f, 205f, true, paint);
        // Simple fringe, glasses and a gold highlight keep the fallback closer to the reference.
        Path fringe = new Path();
        fringe.moveTo(x + 9f, y + 7f); fringe.quadTo(x + 20f, y + 0f, x + 31f, y + 8f);
        fringe.lineTo(x + 26f, y + 13f); fringe.lineTo(x + 21f, y + 9f);
        fringe.lineTo(x + 16f, y + 13f); fringe.close();
        canvas.drawPath(fringe, paint);
        paint.setColor(Color.rgb(20, 20, 24));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.4f);
        canvas.drawRoundRect(x + 13f, y + 7f, x + 21f, y + 13f, 2f, 2f, paint);
        canvas.drawRoundRect(x + 23f, y + 7f, x + 30f, y + 13f, 2f, 2f, paint);
        canvas.drawLine(x + 21f, y + 9f, x + 23f, y + 9f, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }

    /** Draws Berthu with the actual orange-tabby sprite and a cat-shaped fallback. */
    private void drawBerthu(Canvas canvas) {
        float x = berthuX;
        float floor = heroY + HERO_H;
        boolean moving = leftHeld || rightHeld || reunionActive;
        int frameIndex = ((int) (animationTime * (moving ? 9f : 2.5f)) % berthuWalkFrames.length);
        Bitmap frame = moving ? chooseAnimationFrame(berthuWalkFrames, frameIndex) : berthuIdleBitmap;
        if (frame == null) frame = chooseAnimationFrame(berthuWalkFrames, frameIndex);
        if (frame != null && !frame.isRecycled()) {
            float drawH = BERTHU_H;
            float drawW = drawH * frame.getWidth() / (float) frame.getHeight();
            // Anchor the sprite to the floor so all animation frames keep their paws grounded.
            RectF dst = new RectF(x, floor - drawH, x + drawW, floor);
            if (berthuHp <= 0f) paint.setAlpha(175);
            else if (berthuFlash > 0f) paint.setAlpha(210);
            else paint.setAlpha(255);
            paint.setFilterBitmap(true);
            canvas.save();
            if (leftHeld || (!rightHeld && !facingRight)) {
                canvas.scale(-1f, 1f, dst.centerX(), dst.centerY());
            }
            canvas.drawBitmap(frame, null, dst, paint);
            canvas.restore();
            paint.setFilterBitmap(false);
            paint.setAlpha(255);
        } else {
            // Last-resort illustration: a recognisable orange tabby with a long striped tail,
            // pointed ears, four legs, cream muzzle and whiskers. The supplied photo-style
            // sprite frames are preferred whenever available.
            Path tail = new Path();
            tail.moveTo(x + 13f, floor - 21f);
            tail.cubicTo(x - 2f, floor - 28f, x - 1f, floor - 44f, x + 13f, floor - 41f);
            paint.setColor(Color.rgb(220, 133, 45));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5f);
            canvas.drawPath(tail, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(245, 175, 76));
            canvas.drawOval(x + 9f, floor - 29f, x + 48f, floor - 9f, paint);
            paint.setColor(Color.rgb(255, 224, 177));
            canvas.drawOval(x + 23f, floor - 20f, x + 43f, floor - 10f, paint);
            // Four slim paws/legs keep the body from reading as a toy robot.
            paint.setColor(Color.rgb(218, 137, 54));
            paint.setStrokeWidth(3.5f);
            canvas.drawLine(x + 16f, floor - 14f, x + 15f, floor - 3f, paint);
            canvas.drawLine(x + 24f, floor - 14f, x + 25f, floor - 3f, paint);
            canvas.drawLine(x + 38f, floor - 14f, x + 40f, floor - 3f, paint);
            canvas.drawLine(x + 44f, floor - 14f, x + 47f, floor - 3f, paint);
            // Tabby stripes along the flank and tail.
            paint.setStrokeWidth(2.2f);
            paint.setColor(Color.rgb(174, 91, 34));
            canvas.drawLine(x + 20f, floor - 25f, x + 23f, floor - 17f, paint);
            canvas.drawLine(x + 28f, floor - 27f, x + 31f, floor - 18f, paint);
            canvas.drawLine(x + 36f, floor - 26f, x + 39f, floor - 18f, paint);
            canvas.drawLine(x + 4f, floor - 39f, x + 8f, floor - 37f, paint);
            canvas.drawLine(x + 1f, floor - 34f, x + 6f, floor - 32f, paint);
            // Head and pointed ears.
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(240, 164, 62));
            canvas.drawOval(x + 35f, floor - 39f, x + 55f, floor - 20f, paint);
            Path ears = new Path();
            ears.moveTo(x + 37f, floor - 34f); ears.lineTo(x + 38f, floor - 46f); ears.lineTo(x + 45f, floor - 35f);
            ears.moveTo(x + 46f, floor - 35f); ears.lineTo(x + 53f, floor - 44f); ears.lineTo(x + 53f, floor - 31f);
            ears.close();
            canvas.drawPath(ears, paint);
            paint.setColor(Color.rgb(249, 191, 174));
            canvas.drawCircle(x + 45f, floor - 27f, 5.5f, paint);
            paint.setColor(Color.rgb(255, 232, 196));
            canvas.drawOval(x + 42f, floor - 27f, x + 54f, floor - 21f, paint);
            paint.setColor(Color.rgb(50, 34, 24));
            canvas.drawCircle(x + 43f, floor - 30f, 1.5f, paint);
            canvas.drawCircle(x + 50f, floor - 30f, 1.5f, paint);
            paint.setColor(Color.rgb(188, 92, 80));
            canvas.drawCircle(x + 48f, floor - 25f, 1.2f, paint);
            paint.setColor(Color.rgb(248, 223, 193));
            paint.setStrokeWidth(1f);
            canvas.drawLine(x + 51f, floor - 25f, x + 59f, floor - 27f, paint);
            canvas.drawLine(x + 51f, floor - 24f, x + 59f, floor - 23f, paint);
        }
        if (berthuFlash > 0f && berthuHp > 0f) {
            paint.setColor(Color.argb(160, 255, 221, 112));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.8f);
            canvas.drawCircle(x + 25f, floor - 20f, 25f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawHud(Canvas canvas) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(208, 21, 20, 43));
        canvas.drawRoundRect(10f, 10f, screenW - 10f, 79f, 12f, 12f, paint);

        // Exit/dashboard button.
        paint.setColor(Color.rgb(79, 65, 94));
        canvas.drawRoundRect(15f, 18f, 65f, 47f, 8f, 8f, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(17f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("←", 40f, 38f, paint);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(15f);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setColor(Color.WHITE);
        canvas.drawText("ANNIEHILATION", 75f, 29f, paint);
        paint.setTextSize(11f);
        paint.setColor(Color.rgb(222, 204, 236));
        canvas.drawText("LEVEL " + level + " / " + MAX_LEVEL
                + "    ENEMIES DEFEATED " + defeatedEnemies, 75f, 44f, paint);

        float barX = 75f;
        float barW = Math.max(72f, Math.min(135f, screenW * 0.28f));
        float barH = 7f;
        drawHealthBar(canvas, barX, 51f, barW, barH, annieHp / ANNIE_MAX_HP,
                Color.rgb(231, 83, 111));
        paint.setColor(Color.WHITE);
        paint.setTextSize(9f);
        canvas.drawText("ANNIE", barX + barW + 5f, 58f, paint);

        float berthuBarX = barX;
        float berthuBarW = barW;
        drawHealthBar(canvas, berthuBarX, 64f, berthuBarW, barH,
                berthuHp / BERTHU_MAX_HP, Color.rgb(91, 194, 111));
        paint.setColor(Color.WHITE);
        paint.setTextSize(9f);
        canvas.drawText("BERTHU", berthuBarX + berthuBarW + 5f, 71f, paint);

        paint.setColor(Color.rgb(79, 65, 94));
        canvas.drawRoundRect(screenW - 52f, 18f, screenW - 17f, 48f,
                8f, 8f, paint);
        paint.setColor(Color.WHITE);
        canvas.drawRect(screenW - 41f, 26f, screenW - 37f, 40f, paint);
        canvas.drawRect(screenW - 32f, 26f, screenW - 28f, 40f, paint);

        drawControls(canvas);
    }

    private void drawHealthBar(Canvas canvas, float x, float y, float width,
                              float height, float fraction, int color) {
        paint.setColor(Color.rgb(52, 42, 61));
        canvas.drawRoundRect(x, y, x + width, y + height, 4f, 4f, paint);
        float current = Math.max(0f, Math.min(1f, fraction));
        if (current > 0f) {
            paint.setColor(color);
            canvas.drawRoundRect(x, y, x + width * current, y + height,
                    4f, 4f, paint);
        }
    }

    private void drawControls(Canvas canvas) {
        float y = screenH - 46f;
        drawButton(canvas, 48f, y, "◀", leftHeld);
        drawButton(canvas, 112f, y, "▶", rightHeld);
        drawButton(canvas, screenW - 135f, y, "⚔", attackHeld);
        drawButton(canvas, screenW - 55f, y, "↑", jumpHeld);
    }

    private void drawButton(Canvas canvas, float cx, float cy, String label,
                            boolean active) {
        paint.setColor(active ? Color.argb(230, 189, 117, 71)
                : Color.argb(155, 22, 22, 43));
        canvas.drawRoundRect(cx - 25f, cy - 25f, cx + 25f, cy + 25f,
                12f, 12f, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(21f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText(label, cx, cy + 8f, paint);
    }

    private void drawOverlay(Canvas canvas, String title, String subtitle,
                             String footer) {
        paint.setColor(Color.argb(208, 11, 10, 27));
        canvas.drawRect(0f, 0f, screenW, screenH, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setColor(GOLD);
        paint.setTextSize(27f);
        canvas.drawText(title, screenW / 2f, screenH / 2f - 22f, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(15f);
        canvas.drawText(subtitle, screenW / 2f, screenH / 2f + 12f, paint);
        if (!footer.isEmpty()) {
            paint.setColor(Color.rgb(206, 194, 220));
            paint.setTextSize(12f);
            canvas.drawText(footer, screenW / 2f, screenH / 2f + 39f, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        float x = event.getX() / density;
        float y = event.getY() / density;

        if (action == MotionEvent.ACTION_DOWN) {
            if (y < 58f && x < 70f) {
                if (returnToDashboard != null) returnToDashboard.run();
                return true;
            }
            if (y < 58f && x > screenW - 60f && !gameOver && !won) {
                paused = !paused;
                releaseControls();
                invalidate();
                return true;
            }
            if (paused) {
                paused = false;
                invalidate();
                return true;
            }
            if (gameOver) {
                annieHp = ANNIE_MAX_HP;
                berthuHp = BERTHU_MAX_HP;
                loadLevel(level);
                return true;
            }
            if (won) {
                annieHp = ANNIE_MAX_HP;
                berthuHp = BERTHU_MAX_HP;
                loadLevel(1);
                return true;
            }
            if (levelCleared || reunionActive) return true;
        }

        if (action == MotionEvent.ACTION_DOWN
                || action == MotionEvent.ACTION_POINTER_DOWN
                || action == MotionEvent.ACTION_MOVE
                || action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_POINTER_UP
                || action == MotionEvent.ACTION_CANCEL) {
            refreshControls(event);
            return true;
        }
        return true;
    }

    private void refreshControls(MotionEvent event) {
        boolean left = false;
        boolean right = false;
        boolean jump = false;
        boolean attack = false;
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        for (int i = 0; i < event.getPointerCount(); i++) {
            if (action == MotionEvent.ACTION_CANCEL) break;
            if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
                    && i == actionIndex) continue;

            float x = event.getX(i) / density;
            float y = event.getY(i) / density;
            if (y < screenH - 105f) continue;

            if (x < 81f) left = true;
            else if (x < 151f) right = true;
            else if (x > screenW - 94f) jump = true;
            else if (x > screenW - 190f) attack = true;
        }
        leftHeld = left;
        rightHeld = right;
        jumpHeld = jump;
        attackHeld = attack;
        if (action == MotionEvent.ACTION_CANCEL) releaseControls();
        invalidate();
    }

    private void releaseControls() {
        leftHeld = false;
        rightHeld = false;
        jumpHeld = false;
        attackHeld = false;
        jumpWasHeld = false;
        attackWasHeld = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseControls();
        if (enemySoundPool != null) {
            enemySoundPool.release();
            enemySoundPool = null;
            enemyFartPunchLoaded = false;
            enemyFartKickLoaded = false;
        }
        super.onDetachedFromWindow();
    }

    private static final class Platform {
        final float x;
        final float width;
        final float heightAboveGround;
        Platform(float x, float width, float heightAboveGround) {
            this.x = x;
            this.width = width;
            this.heightAboveGround = heightAboveGround;
        }
    }

    private static final class Hazard {
        final float x;
        final float width;
        final boolean heavy;
        Hazard(float x, float width, boolean heavy) {
            this.x = x;
            this.width = width;
            this.heavy = heavy;
        }
    }

    private static final class Gap {
        final float start;
        final float end;
        Gap(float start, float end) {
            this.start = start;
            this.end = end;
        }
    }

    private static final class Enemy {
        float x;
        float hp;
        final float maxHp;
        final boolean boss;
        final float width;
        final float speed;
        final float patrolMin;
        final float patrolMax;
        float direction = 1f;
        float attackCooldown = 0.8f;
        float attackFlash;
        float hitFlash;
        int attackCount;
        int attackPose;
        boolean isMoving;
        boolean playerNearby;
        boolean facingRight = true;

        Enemy(float x, float hp, boolean boss, float patrolRadius) {
            this.x = x;
            this.hp = hp;
            this.maxHp = hp;
            this.boss = boss;
            this.width = boss ? 68f : 56f;
            this.speed = boss ? 48f : 57f;
            this.patrolMin = Math.max(0f, x - patrolRadius);
            this.patrolMax = x + patrolRadius;
        }
    }
}
