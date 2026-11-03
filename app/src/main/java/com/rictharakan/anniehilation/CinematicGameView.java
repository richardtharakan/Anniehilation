package com.rictharakan.anniehilation;

import android.content.Context;
import android.content.SharedPreferences;
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
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Cinematic 50-stage Anniehilation campaign view.
 * Includes connected room transitions, platform movement, ledge hanging,
 * pull-up, ladder climbing, pressure plates, boss combat, companions,
 * world-finale Xingxing reunions, projectiles, HUD and pause/SFX controls.
 *
 * This class is deliberately separate from the proven GameView so the previous
 * horizontal build remains available as a rollback option.
 */
public class CinematicGameView extends View {
    private static final float HERO_W = 49f;
    private static final float HERO_H = 79f;
    private static final float BERTHU_H = 39f;
    // Movement is velocity-driven rather than snapping to a fixed speed each frame.
    private static final float GRAVITY = 1320f;
    private static final float WALK_SPEED = 116f;
    private static final float RUN_SPEED = 205f;
    private static final float JUMP_SPEED = 600f;
    private static final float GROUND_ACCEL = 920f;
    private static final float GROUND_BRAKE = 1320f;
    private static final float AIR_ACCEL = 520f;
    private static final float FIXED_STEP = 1f / 120f;
    private static final float PULL_UP_DURATION = .34f;
    private static final float ANNIE_MAX_HP = 150f;
    private static final float BERTHU_MAX_HP = 500f;
    private static final float BERTHU_DAMAGE_SHARE = .80f;
    private static final float FIREBALL_SPEED = 520f;
    private static final float FIREBALL_COOLDOWN = .58f;
    private static final float FIREBALL_DAMAGE = 54f;
    private static final float FIRE_CAST_DURATION = .34f;
    private static final float LOW_HEALTH_SPEED_THRESHOLD = .70f;
    private static final float LOW_HEALTH_MIN_SPEED = .30f;
    private static final float BOSS_BLUE_FIREBALL_SPEED = 355f;
    private static final float BOSS_BLUE_FIREBALL_DAMAGE = 38f;
    private static final boolean APPLY_BOSS_PALETTE_RECOLOR = true;

    // Lightweight campaign save: remembers the most recently reached stage and settings.
    private static final String SAVE_PREFS = "anniehilation_campaign_save_v2";
    private static final String KEY_LAST_STAGE = "last_reached_stage";
    private static final String KEY_BEST_STAGE = "best_reached_stage";
    private static final String KEY_ANNIE_HP = "annie_health";
    private static final String KEY_BERTHU_HP = "berthu_health";
    private static final String KEY_SFX_ENABLED = "sfx_enabled";

    private static final float ACTION_BUTTON_RADIUS = 22f;
    private static final float ACTION_BUTTON_HIT_RADIUS = 28f;

    private static final int STATE_IDLE = 0;
    private static final int STATE_WALK = 1;
    private static final int STATE_RUN = 2;
    private static final int STATE_JUMP = 3;
    private static final int STATE_FALL = 4;
    private static final int STATE_HANG = 5;
    private static final int STATE_CLIMB = 6;
    private static final int STATE_CROUCH = 7;
    private static final int STATE_ATTACK = 8;
    private static final int STATE_HURT = 9;
    private static final int STATE_PULL_UP = 10;
    private static final int STATE_CAST = 11;

    private static final int MODE_PLAYING = 0;
    private static final int MODE_PAUSED = 1;
    private static final int MODE_GAME_OVER = 2;
    private static final int MODE_COMPLETE = 3;
    private static final int MODE_REUNION = 4;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final CinematicRoomManager roomManager = new CinematicRoomManager();
    private final Runnable returnToDashboard;
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Fireball> fireballs = new ArrayList<>();
    private final List<MediaPlayer> fallbackPlayers = new ArrayList<>();

    private final Bitmap[] annieIdle = new Bitmap[4];
    private final Bitmap[] annieWalk = new Bitmap[4];
    private final Bitmap[] annieRun = new Bitmap[4];
    private final Bitmap[] annieJump = new Bitmap[3];
    private final Bitmap[] berthuWalk = new Bitmap[8];
    private final Bitmap[] xingxingFrames = new Bitmap[8];
    private final Bitmap[] tigerFrames = new Bitmap[8]; // Original black-clad normal villains.
    private final Bitmap[] bossTigerFrames = new Bitmap[8]; // Boss-only white palette variant.
    private Bitmap berthuIdle;
    private Bitmap annieAttack;
    private Bitmap annieHurt;
    private Bitmap annieFireCastPose;
    private Bitmap xingxingBase;

    private SoundPool soundPool;
    private int fartPunchId;
    private int fartKickId;
    private int xingxingGiggleId;
    private int xingxingLaughId; // Older laugh retained as a fallback.
    private int noNoNoId;
    private int meowId;
    private int fireballWhooshId;
    private int fireballWhooshRawId;
    private int rockFallId;
    private int waterSplashId;
    private int waterLoopId;
    private int waterLoopStreamId;
    private boolean fartPunchReady;
    private boolean fartKickReady;
    private boolean xingxingGiggleReady;
    private boolean xingxingLaughReady;
    private boolean noNoNoReady;
    private boolean meowReady;
    private boolean fireballWhooshReady;
    private boolean rockFallReady;
    private boolean waterSplashReady;
    private boolean waterLoopReady;
    private boolean playerInWater;
    private int[] rockSoundCycleSeen = new int[0];
    private boolean sfxLoadFailed;
    private boolean reunionLaughPlayed;
    private boolean xingxingCageBroken;
    private boolean xingxingReunionPending;
    private float xingxingCageBreakTimer;
    private float reunionSceneTimer;
    private boolean sfxEnabled = true;
    private boolean initialRoomLoaded;
    private long lastRockFallSoundAtMs = -1000L;
    private long lastBerthuTapTimeMs = -1000L;
    private float lastBerthuTapX;
    private float lastBerthuTapY;
    private boolean berthuPlateCommandActive;
    private float berthuPlateTargetX = -1f;
    private boolean bossIntroPlayedThisRoom;
    private boolean roomHasBossThisRoom;
    private boolean bossDefeatedThisRoom = true;
    private int berthuHitsThisRoom;

    private float density = 1f;
    private float viewW;
    private float viewH;
    private float floorY;
    private float animationTime;
    private float physicsAccumulator;
    private float heroAnimationPhase;
    private float berthuAnimationPhase;
    private float transitionAlpha;
    private float roomTransitionTimer;
    private float roomTransitionStartX;
    private float roomTransitionStartY;
    private float roomTransitionOffsetX;
    private float roomTransitionOffsetY;
    private float landingSquashTimer;
    private float takeoffStretchTimer;
    private float pullUpTimer;
    private float pullUpStartX;
    private float pullUpStartY;
    private float pullUpTargetX;
    private float pullUpTargetY;
    private float messageTimer;
    private float attackTimer;
    private float attackCooldown;
    private float fireballCooldown;
    private float fireCastTimer;
    private float hurtCooldown;
    private float berthuFlash;
    private float annieFlash;
    private float berthuRegenDelay;
    private float sfxFeedbackTimer;
    private float climbProgress;

    private int roomId = CinematicRoomManager.SCHOOL_GATE;
    private int mode = MODE_PLAYING;
    private int defeatedEnemies;
    private int roomVisitCount;
    private int playerState = STATE_IDLE;
    private int attackFacing;
    private float annieHp = ANNIE_MAX_HP;
    private float berthuHp = BERTHU_MAX_HP;

    private float heroX = 65f;
    private float heroY;
    private float heroVx;
    private float heroVy;
    private float berthuX = 20f;
    private float berthuY;
    private float berthuVx;
    private float berthuVy;
    private float berthuJumpCooldown;
    private float berthuLastSafeX = 20f;
    private boolean berthuOnGround = true;
    private boolean berthuFacingRight = true;
    private float enemySpawnTimer;
    private float checkpointX = 65f;
    private float checkpointY;
    private float ladderX = -1f;
    private float ladderTopY = -1f;
    private float hangingPlatformX;
    private float hangingPlatformY;
    private float hangingPlatformW;

    private boolean onGround = true;
    private boolean facingRight = true;
    private boolean runMode = true;
    private boolean gateOpen;
    private boolean roomWasVisited;
    private boolean jumpHeld;
    private boolean jumpWasHeld;
    private boolean attackHeld;
    private boolean attackWasHeld;
    private boolean actionHeld;
    private boolean actionWasHeld;
    private boolean leftHeld;
    private boolean rightHeld;
    private boolean upHeld;
    private boolean upWasHeld;
    private boolean downHeld;
    private boolean runButtonWasHeld;
    private boolean pauseWasHeld;
    private boolean sfxWasHeld;
    private boolean pendingJump;
    private boolean pendingAttack;
    private boolean pendingAction;
    private boolean pauseOpeningTap;
    private boolean exitButtonTouch;
    private long lastFrameNanos = System.nanoTime();

    public CinematicGameView(Context context, Runnable returnToDashboard) {
        super(context);
        this.returnToDashboard = returnToDashboard;
        initialize();
    }

    private void initialize() {
        density = getResources().getDisplayMetrics().density;
        setFocusable(true);
        setFocusableInTouchMode(true);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        loadCharacterAssets();
        loadTigerAssets();
        loadEnemySounds();
    }

    private void loadCharacterAssets() {
        loadFrames(annieIdle, "annie_idle_");
        loadFrames(annieWalk, "annie_walk_");
        loadFrames(annieRun, "annie_run_");
        loadFrames(annieJump, "annie_jump_");
        loadFrames(berthuWalk, "berthu_walk_");
        loadFrames(xingxingFrames, "xingxing_frame_");
        berthuIdle = bitmap("berthu_idle");
        annieAttack = bitmap("annie_attack");
        annieHurt = bitmap("annie_hurt");
        annieFireCastPose = bitmap("annie_fire_cast_pose");
        xingxingBase = bitmap("xingxing");
        if (xingxingFrames[0] == null) xingxingFrames[0] = xingxingBase;
    }
    private void loadTigerAssets() {
        String[] names = {"tiger_hero_guard", "tiger_hero_run", "tiger_hero_block",
                "tiger_hero_punch", "tiger_hero_kick", "tiger_hero_spin_kick",
                "tiger_hero_hit", "tiger_hero_down"};
        for (int i = 0; i < names.length; i++) {
            Bitmap source = bitmap(names[i]);
            tigerFrames[i] = source;
            bossTigerFrames[i] = source != null ? recolorBossWhitePalette(source) : null;
        }
    }

    /**
     * Boss-only pixel palette treatment. It changes dark neutral hair and outfit pixels
     * to white/silver while preserving transparent pixels, skin, facial features and pose.
     * Normal enemies always use tigerFrames and keep their original black costume/hair.
     */
    private Bitmap recolorBossWhitePalette(Bitmap source) {
        try {
            int width = source.getWidth();
            int height = source.getHeight();
            int[] pixels = new int[width * height];
            source.getPixels(pixels, 0, width, 0, 0, width, height);

            for (int y = 0; y < height; y++) {
                float ny = y / (float) Math.max(1, height - 1);
                for (int x = 0; x < width; x++) {
                    int index = y * width + x;
                    int color = pixels[index];
                    int alpha = Color.alpha(color);
                    if (alpha < 24) continue;

                    int red = Color.red(color);
                    int green = Color.green(color);
                    int blue = Color.blue(color);
                    int max = Math.max(red, Math.max(green, blue));
                    int min = Math.min(red, Math.min(green, blue));
                    int spread = max - min;
                    int luminance = (red * 30 + green * 59 + blue * 11) / 100;
                    float nx = x / (float) Math.max(1, width - 1);

                    // Skin is warm and chromatic; protect it while recolouring neutral clothing.
                    boolean warmSkin = red > green * 1.08f
                            && green > blue * 1.08f && red > 58;

                    // All dark neutral costume regions (vest, trousers, gloves and shoes)
                    // become white. This wider mask also catches the glove at the top of punch frames.
                    boolean costume = ny >= .16f && ny < .995f
                            && luminance < 205 && spread < 104 && !warmSkin;

                    // Hair moves around slightly between the supplied animation poses.
                    // Restrict this mask to the crown; transparent pixels keep the background untouched.
                    boolean hairRegion = ny < .205f && nx > .10f && nx < .96f;
                    boolean hair = hairRegion && luminance < 148 && spread < 126;

                    if (costume || hair) {
                        // A white/silver gradient preserves folds and highlights without leaving black areas.
                        int whiteShade = Math.max(232, Math.min(252,
                                230 + (int) (luminance * .13f)));
                        pixels[index] = Color.argb(alpha, whiteShade,
                                Math.min(255, whiteShade + 2), Math.min(255, whiteShade + 5));
                    }
                }
            }

            Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            result.setPixels(pixels, 0, width, 0, 0, width, height);
            return result;
        } catch (RuntimeException ignored) {
            return source;
        }
    }

    /**
     * Palette-only costume adjustment for the existing villain bitmaps.
     * It preserves the source poses: dark lower clothing becomes off-white
     * and dark hair becomes salt-and-pepper grey. The original shirt/vest and
     * body silhouette remain unchanged; this is a palette treatment, not a new mesh.
     */


    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private void loadFrames(Bitmap[] target, String prefix) {
        for (int i = 0; i < target.length; i++) target[i] = bitmap(prefix + (i + 1));
    }

    private Bitmap bitmap(String name) {
        int id = getResources().getIdentifier(name, "drawable", getContext().getPackageName());
        return id == 0 ? null : BitmapFactory.decodeResource(getResources(), id);
    }

    private void loadEnemySounds() {
        try {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            soundPool = new SoundPool.Builder().setAudioAttributes(attributes).setMaxStreams(12).build();
            soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
                if (sampleId == fartPunchId) fartPunchReady = status == 0;
                if (sampleId == fartKickId) fartKickReady = status == 0;
                if (sampleId == xingxingGiggleId) xingxingGiggleReady = status == 0;
                if (sampleId == xingxingLaughId) xingxingLaughReady = status == 0;
                if (sampleId == noNoNoId) noNoNoReady = status == 0;
                if (sampleId == meowId) meowReady = status == 0;
                if (sampleId == fireballWhooshId) fireballWhooshReady = status == 0;
                if (sampleId == rockFallId) rockFallReady = status == 0;
                if (sampleId == waterSplashId) waterSplashReady = status == 0;
                if (sampleId == waterLoopId) waterLoopReady = status == 0;
                if (status != 0) sfxLoadFailed = true;
            });

            int punch = getResources().getIdentifier("tiger_fart_punch", "raw", getContext().getPackageName());
            int kick = getResources().getIdentifier("tiger_fart_kick", "raw", getContext().getPackageName());
            int giggle = getResources().getIdentifier("xingxing_giggle", "raw", getContext().getPackageName());
            int laugh = getResources().getIdentifier("xingxing_laugh", "raw", getContext().getPackageName());
            int noNoNo = getResources().getIdentifier("nonono", "raw", getContext().getPackageName());
            int meow = getResources().getIdentifier("meow", "raw", getContext().getPackageName());
            int rockFall = getResources().getIdentifier("rock_fall", "raw", getContext().getPackageName());
            int waterSplash = getResources().getIdentifier("water_splash", "raw", getContext().getPackageName());
            int waterLoop = getResources().getIdentifier("water_loop", "raw", getContext().getPackageName());
            // Reuse the same long whoosh as the dashboard cooldown for every fireball cast.
            fireballWhooshRawId = getResources().getIdentifier(
                    "cooldown_whoosh", "raw", getContext().getPackageName());
            if (fireballWhooshRawId == 0) {
                fireballWhooshRawId = getResources().getIdentifier(
                        "woosh", "raw", getContext().getPackageName());
            }

            if (punch != 0) fartPunchId = soundPool.load(getContext(), punch, 1);
            if (kick != 0) fartKickId = soundPool.load(getContext(), kick, 1);
            if (giggle != 0) xingxingGiggleId = soundPool.load(getContext(), giggle, 1);
            if (laugh != 0) xingxingLaughId = soundPool.load(getContext(), laugh, 1);
            if (noNoNo != 0) noNoNoId = soundPool.load(getContext(), noNoNo, 1);
            if (meow != 0) meowId = soundPool.load(getContext(), meow, 1);
            if (rockFall != 0) rockFallId = soundPool.load(getContext(), rockFall, 1);
            if (waterSplash != 0) waterSplashId = soundPool.load(getContext(), waterSplash, 1);
            if (waterLoop != 0) waterLoopId = soundPool.load(getContext(), waterLoop, 1);
            if (fireballWhooshRawId != 0) {
                fireballWhooshId = soundPool.load(getContext(), fireballWhooshRawId, 1);
            }

            if (punch == 0 || kick == 0 || (giggle == 0 && laugh == 0)
                    || noNoNo == 0 || meow == 0 || rockFall == 0
                    || waterSplash == 0 || waterLoop == 0) {
                sfxLoadFailed = true;
            }
        } catch (RuntimeException e) {
            sfxLoadFailed = true;
            if (soundPool != null) {
                soundPool.release();
                soundPool = null;
            }
        }
    }

    private void playAttackSound(boolean kick) {
        if (!sfxEnabled) return;
        int sample = kick ? fartKickId : fartPunchId;
        boolean ready = kick ? fartKickReady : fartPunchReady;
        if (soundPool != null && sample != 0 && ready) {
            int stream = soundPool.play(sample, 1f, 1f, 2, 0, kick ? .94f : 1.05f);
            if (stream != 0) return;
        }
        int resource = getResources().getIdentifier(kick ? "tiger_fart_kick" : "tiger_fart_punch",
                "raw", getContext().getPackageName());
        if (resource != 0) playFallbackSound(resource);
    }

    private void playXingxingGiggle() {
        if (!sfxEnabled) return;
        int giggleResource = getResources().getIdentifier(
                "xingxing_giggle", "raw", getContext().getPackageName());
        if (soundPool != null && xingxingGiggleId != 0 && xingxingGiggleReady) {
            int stream = soundPool.play(xingxingGiggleId, 1f, 1f, 3, 0, 1f);
            if (stream != 0) return;
        }
        if (giggleResource != 0) {
            playFallbackSound(giggleResource);
            return;
        }

        // The older laugh is retained as a compatibility fallback if the giggle is absent.
        int oldLaughResource = getResources().getIdentifier(
                "xingxing_laugh", "raw", getContext().getPackageName());
        if (soundPool != null && xingxingLaughId != 0 && xingxingLaughReady) {
            int stream = soundPool.play(xingxingLaughId, 1f, 1f, 3, 0, 1f);
            if (stream != 0) return;
        }
        if (oldLaughResource != 0) playFallbackSound(oldLaughResource);
    }

    /** Plays the villain's short reaction when a room containing the boss appears. */
    private void playBossIntroNoNoNo() {
        if (!sfxEnabled) return;
        int resource = getResources().getIdentifier(
                "nonono", "raw", getContext().getPackageName());
        if (soundPool != null && noNoNoId != 0 && noNoNoReady) {
            int stream = soundPool.play(noNoNoId, 1f, 1f, 4, 0, 1f);
            if (stream != 0) return;
        }
        if (resource != 0) playFallbackSound(resource);
    }

    /** Plays Berthu's meow every fifth hit that he absorbs in the current room. */
    private void playBerthuMeow() {
        if (!sfxEnabled) return;
        int resource = getResources().getIdentifier(
                "meow", "raw", getContext().getPackageName());
        if (soundPool != null && meowId != 0 && meowReady) {
            int stream = soundPool.play(meowId, 1f, 1f, 4, 0, 1f);
            if (stream != 0) return;
        }
        if (resource != 0) playFallbackSound(resource);
    }

    /** Reuses the dashboard cooldown whoosh when Annie launches a fireball. */
    private void playFireballWhoosh() {
        if (!sfxEnabled) return;
        if (soundPool != null && fireballWhooshId != 0 && fireballWhooshReady) {
            int stream = soundPool.play(fireballWhooshId, .92f, .92f, 5, 0, 1f);
            if (stream != 0) return;
        }
        if (fireballWhooshRawId != 0) playFallbackSound(fireballWhooshRawId);
    }

    private void playRockFallSound() {
        if (!sfxEnabled) return;
        long now = System.currentTimeMillis();
        if (now - lastRockFallSoundAtMs < 260L) return;
        lastRockFallSoundAtMs = now;

        if (soundPool != null && rockFallId != 0 && rockFallReady) {
            int stream = soundPool.play(rockFallId, 1f, 1f, 5, 0, 1f);
            if (stream != 0) return;
        }
        // MediaPlayer fallback also covers devices where the SoundPool sample is still loading.
        int resource = getResources().getIdentifier("rock_fall", "raw", getContext().getPackageName());
        if (resource != 0) playFallbackSound(resource);
    }

    private void playWaterSplash() {
        if (!sfxEnabled) return;
        if (soundPool != null && waterSplashId != 0 && waterSplashReady) {
            if (soundPool.play(waterSplashId, .72f, .72f, 4, 0, 1f) != 0) return;
        }
        int resource = getResources().getIdentifier("water_splash", "raw", getContext().getPackageName());
        if (resource != 0) playFallbackSound(resource);
    }

    private void updateWaterLoop(boolean shouldPlay) {
        if (!sfxEnabled || !shouldPlay || soundPool == null
                || waterLoopId == 0 || !waterLoopReady) {
            stopWaterLoop();
            return;
        }
        if (waterLoopStreamId == 0) {
            waterLoopStreamId = soundPool.play(waterLoopId, .34f, .34f, 2, -1, 1f);
        }
    }

    private void stopWaterLoop() {
        if (soundPool != null && waterLoopStreamId != 0) {
            try { soundPool.stop(waterLoopStreamId); } catch (RuntimeException ignored) { }
        }
        waterLoopStreamId = 0;
    }

    private void playFallbackSound(int resourceId) {
        if (!sfxEnabled || resourceId == 0) return;
        MediaPlayer player = null;
        try {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            player = MediaPlayer.create(getContext().getApplicationContext(), resourceId, attributes, 0);
            if (player == null) return;
            final MediaPlayer active = player;
            fallbackPlayers.add(active);
            active.setOnCompletionListener(mp -> {
                fallbackPlayers.remove(active);
                active.release();
            });
            active.setOnErrorListener((mp, what, extra) -> {
                fallbackPlayers.remove(active);
                active.release();
                return true;
            });
            active.start();
        } catch (RuntimeException ignored) {
            if (player != null) {
                fallbackPlayers.remove(player);
                player.release();
            }
        }
    }

    private void setSfxEnabled(boolean enabled) {
        sfxEnabled = enabled;
        if (soundPool != null) {
            if (enabled) soundPool.autoResume();
            else soundPool.autoPause();
        }
        if (!enabled) {
            stopWaterLoop();
            for (MediaPlayer player : new ArrayList<>(fallbackPlayers)) {
                try {
                    if (player.isPlaying()) player.stop();
                } catch (IllegalStateException ignored) { }
                try { player.release(); } catch (RuntimeException ignored) { }
                fallbackPlayers.remove(player);
            }
        }
        saveCampaignProgress();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewW = w / density;
        viewH = h / density;
        floorY = viewH * .785f;
        if (!initialRoomLoaded) {
            initialRoomLoaded = true;
            SharedPreferences prefs = getContext().getSharedPreferences(SAVE_PREFS, Context.MODE_PRIVATE);
            int savedStage = Math.max(0, Math.min(CinematicRoomManager.ROOM_COUNT - 1,
                    prefs.getInt(KEY_LAST_STAGE, CinematicRoomManager.SCHOOL_GATE)));
            annieHp = clampHealth(prefs.getFloat(KEY_ANNIE_HP, ANNIE_MAX_HP), ANNIE_MAX_HP);
            berthuHp = clampHealth(prefs.getFloat(KEY_BERTHU_HP, BERTHU_MAX_HP), BERTHU_MAX_HP);
            sfxEnabled = prefs.getBoolean(KEY_SFX_ENABLED, true);
            loadRoom(savedStage, false, CinematicRoomManager.LEFT);
        } else {
            loadRoom(roomId, false, CinematicRoomManager.LEFT);
        }
    }

    private float clampHealth(float value, float maximum) {
        return Math.max(1f, Math.min(maximum, value));
    }

    /** Saves the level the player last reached, health and the SFX preference. */
    private void saveCampaignProgress() {
        if (!initialRoomLoaded) return;
        try {
            SharedPreferences prefs = getContext().getSharedPreferences(SAVE_PREFS, Context.MODE_PRIVATE);
            int bestStage = Math.max(roomId, prefs.getInt(KEY_BEST_STAGE, 0));
            float saveAnnieHp = mode == MODE_GAME_OVER ? ANNIE_MAX_HP : clampHealth(annieHp, ANNIE_MAX_HP);
            float saveBerthuHp = mode == MODE_GAME_OVER ? BERTHU_MAX_HP : clampHealth(berthuHp, BERTHU_MAX_HP);
            prefs.edit()
                    .putInt(KEY_LAST_STAGE, Math.max(0, Math.min(CinematicRoomManager.ROOM_COUNT - 1, roomId)))
                    .putInt(KEY_BEST_STAGE, bestStage)
                    .putFloat(KEY_ANNIE_HP, saveAnnieHp)
                    .putFloat(KEY_BERTHU_HP, saveBerthuHp)
                    .putBoolean(KEY_SFX_ENABLED, sfxEnabled)
                    .apply();
        } catch (RuntimeException ignored) {
            // The current run remains playable even if Android storage is unavailable.
        }
    }

    private void startNewGame() {
        try {
            SharedPreferences prefs = getContext().getSharedPreferences(SAVE_PREFS, Context.MODE_PRIVATE);
            // Preserve the player's SFX preference while clearing campaign progress.
            prefs.edit().remove(KEY_LAST_STAGE).remove(KEY_BEST_STAGE)
                    .remove(KEY_ANNIE_HP).remove(KEY_BERTHU_HP).apply();
        } catch (RuntimeException ignored) { }
        annieHp = ANNIE_MAX_HP;
        berthuHp = BERTHU_MAX_HP;
        defeatedEnemies = 0;
        roomVisitCount = 0;
        mode = MODE_PLAYING;
        pauseOpeningTap = false;
        loadRoom(CinematicRoomManager.SCHOOL_GATE, false, CinematicRoomManager.LEFT);
        saveCampaignProgress();
        invalidate();
    }

    private void pauseMenuLayout(float[] out) {
        float w = Math.min(330f, viewW - 56f);
        float h = 250f;
        float left = (viewW - w) * .5f;
        float top = (viewH - h) * .5f;
        out[0] = left; out[1] = top; out[2] = w; out[3] = h;
    }

    private CinematicRoomManager.RoomDef room() { return roomManager.getRoom(roomId); }

    private void loadRoom(int id, boolean fromTransition, int entryDirection) {
        roomId = id;
        CinematicRoomManager.RoomDef def = room();
        stopWaterLoop();
        playerInWater = false;
        rockSoundCycleSeen = new int[def.obstacles.size()];
        for (int i = 0; i < rockSoundCycleSeen.length; i++) rockSoundCycleSeen[i] = Integer.MIN_VALUE;
        ladderX = def.ladderX < 0 ? -1f : def.ladderX * viewW;
        ladderTopY = def.ladderTop < 0 ? -1f : def.ladderTop * viewH;
        enemies.clear();
        fireballs.clear();
        berthuHitsThisRoom = 0;
        fireballCooldown = 0f;
        fireCastTimer = 0f;
        bossIntroPlayedThisRoom = false;
        roomHasBossThisRoom = false;
        bossDefeatedThisRoom = true;
        for (CinematicRoomManager.EnemyDef spawn : def.enemies) {
            float x = spawn.x * viewW;
            enemies.add(new Enemy(x, spawn.hp, spawn.boss,
                    Math.max(26f, spawn.patrolRadius * viewW)));
            if (spawn.boss) {
                roomHasBossThisRoom = true;
                bossDefeatedThisRoom = false;
            }
        }
        gateOpen = def.gateX < 0f;
        roomWasVisited = false;
        reunionLaughPlayed = false;
        xingxingCageBroken = false;
        xingxingReunionPending = false;
        xingxingCageBreakTimer = 0f;
        reunionSceneTimer = 0f;
        berthuPlateCommandActive = false;
        berthuPlateTargetX = -1f;
        lastBerthuTapTimeMs = -1000L;
        if (roomHasBossThisRoom && !bossIntroPlayedThisRoom) {
            bossIntroPlayedThisRoom = true;
            playBossIntroNoNoNo();
        }
        if (entryDirection == CinematicRoomManager.RIGHT) {
            heroX = viewW - HERO_W - 5f;
        } else if (entryDirection == CinematicRoomManager.LEFT) {
            heroX = 5f;
        } else if (entryDirection == CinematicRoomManager.UP) {
            heroX = viewW * .84f;
        } else if (entryDirection == CinematicRoomManager.DOWN) {
            heroX = viewW * .84f;
        }
        heroY = floorY - HERO_H;
        heroVx = heroVy = 0f;
        onGround = true;
        playerState = STATE_IDLE;
        berthuX = Math.max(5f, heroX - 52f);
        berthuY = floorY - BERTHU_H;
        berthuLastSafeX = berthuX;
        berthuOnGround = true;
        berthuFacingRight = true;
        berthuJumpCooldown = 0f;
        checkpointX = heroX;
        checkpointY = heroY;
        attackTimer = attackCooldown = hurtCooldown = 0f;
        releaseControls();
        roomVisitCount++;
        if (fromTransition) {
            transitionAlpha = .22f;
            roomTransitionTimer = .38f;
            roomTransitionStartX = 0f;
            roomTransitionStartY = 0f;
            if (entryDirection == CinematicRoomManager.LEFT) roomTransitionStartX = viewW * .78f;
            else if (entryDirection == CinematicRoomManager.RIGHT) roomTransitionStartX = -viewW * .78f;
            else if (entryDirection == CinematicRoomManager.DOWN) roomTransitionStartY = viewH * .58f;
            else if (entryDirection == CinematicRoomManager.UP) roomTransitionStartY = -viewH * .58f;
            roomTransitionOffsetX = roomTransitionStartX;
            roomTransitionOffsetY = roomTransitionStartY;
        } else {
            transitionAlpha = 0f;
            roomTransitionTimer = 0f;
            roomTransitionOffsetX = roomTransitionOffsetY = 0f;
        }
        landingSquashTimer = takeoffStretchTimer = pullUpTimer = 0f;
        heroAnimationPhase = 0f;
        berthuAnimationPhase = 0f;
        berthuVx = berthuVy = 0f;
        messageTimer = 2.3f;
        lastFrameNanos = System.nanoTime();
        saveCampaignProgress();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (viewW <= 0f || viewH <= 0f) {
            canvas.drawColor(Color.BLACK);
            postInvalidateOnAnimation();
            return;
        }

        long now = System.nanoTime();
        float frameDt = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        frameDt = Math.max(0f, Math.min(frameDt, .05f));
        // Freeze world animation while paused; keep the reunion animation running.
        if (mode != MODE_PAUSED && mode != MODE_GAME_OVER && mode != MODE_COMPLETE) {
            animationTime += frameDt;
        }
        if (mode == MODE_REUNION) {
            reunionSceneTimer = Math.max(0f, reunionSceneTimer - frameDt);
            if (reunionSceneTimer <= 0f) mode = MODE_COMPLETE;
        }

        // Fixed-step simulation keeps movement and collisions consistent on 30/60/90/120 Hz screens.
        if (mode == MODE_PLAYING) {
            physicsAccumulator = Math.min(physicsAccumulator + frameDt, FIXED_STEP * 8f);
            int steps = 0;
            while (mode == MODE_PLAYING && physicsAccumulator >= FIXED_STEP && steps < 8) {
                updateWorld(FIXED_STEP);
                physicsAccumulator -= FIXED_STEP;
                steps++;
            }
        } else {
            physicsAccumulator = 0f;
        }

        canvas.save();
        canvas.scale(density, density);

        // The room artwork and actors move together; the HUD remains screen-locked.
        canvas.save();
        canvas.translate(roomTransitionOffsetX, roomTransitionOffsetY);
        drawRoomBackground(canvas);
        drawRoomGeometry(canvas);
        drawPressurePlateAndGate(canvas);
        drawHazards(canvas);
        drawObstacles(canvas);
        for (Enemy enemy : enemies) drawEnemy(canvas, enemy);
        drawCompanion(canvas);
        drawPlayer(canvas);
        drawFireballs(canvas);
        drawXingxing(canvas);
        if (transitionAlpha > 0f) {
            paint.setColor(Color.argb((int) (transitionAlpha * 95f), 10, 12, 22));
            canvas.drawRect(0f, 0f, viewW, viewH, paint);
        }
        canvas.restore();

        drawHud(canvas);
        if (mode == MODE_PLAYING || mode == MODE_PAUSED) drawControls(canvas);
        if (mode == MODE_PAUSED) drawPauseMenu(canvas);
        if (mode == MODE_GAME_OVER) drawOverlay(canvas, "ANNIE NEEDS A REST", "Tap anywhere to retry this room", "Berthu is catching his breath");
        if (mode == MODE_COMPLETE) {
            boolean finalStage = roomId >= CinematicRoomManager.ROOM_COUNT - 1;
            String finaleTitle = finalStage ? "ALL 50 LEVELS COMPLETE!"
                    : "WORLD " + (room().theme + 1) + " COMPLETE!";
            String finaleSubtitle = finalStage ? "The whole adventure is complete"
                    : "Annie defeated the boss and found Xingxing";
            String finaleFooter = finalStage ? "Tap anywhere to replay from Level 1"
                    : "Tap anywhere to continue to the next world";
            drawOverlay(canvas, finaleTitle, finaleSubtitle, finaleFooter);
        }
        canvas.restore();
        postInvalidateOnAnimation();
    }

    private void updateWorld(float dt) {
        if (attackCooldown > 0f) attackCooldown = Math.max(0f, attackCooldown - dt);
        if (fireballCooldown > 0f) fireballCooldown = Math.max(0f, fireballCooldown - dt);
        if (fireCastTimer > 0f) fireCastTimer = Math.max(0f, fireCastTimer - dt);
        if (attackTimer > 0f) attackTimer = Math.max(0f, attackTimer - dt);
        if (hurtCooldown > 0f) hurtCooldown = Math.max(0f, hurtCooldown - dt);
        if (annieFlash > 0f) annieFlash = Math.max(0f, annieFlash - dt);
        if (berthuFlash > 0f) berthuFlash = Math.max(0f, berthuFlash - dt);
        if (berthuRegenDelay > 0f) berthuRegenDelay = Math.max(0f, berthuRegenDelay - dt);
        else if (berthuHp > 0f && berthuHp < BERTHU_MAX_HP) berthuHp = Math.min(BERTHU_MAX_HP, berthuHp + 5f * dt);
        if (messageTimer > 0f) messageTimer -= dt;
        if (transitionAlpha > 0f) transitionAlpha = Math.max(0f, transitionAlpha - dt * 1.65f);
        if (roomTransitionTimer > 0f) {
            roomTransitionTimer = Math.max(0f, roomTransitionTimer - dt);
            float progress = 1f - roomTransitionTimer / .38f;
            // Cubic ease-out gives the room a soft, cinematic settle instead of a hard cut.
            float eased = 1f - (float) Math.pow(1f - progress, 3.0);
            roomTransitionOffsetX = roomTransitionStartX * (1f - eased);
            roomTransitionOffsetY = roomTransitionStartY * (1f - eased);
            if (roomTransitionTimer <= 0f) roomTransitionOffsetX = roomTransitionOffsetY = 0f;
        }
        if (sfxFeedbackTimer > 0f) sfxFeedbackTimer -= dt;
        if (landingSquashTimer > 0f) landingSquashTimer = Math.max(0f, landingSquashTimer - dt);
        if (takeoffStretchTimer > 0f) takeoffStretchTimer = Math.max(0f, takeoffStretchTimer - dt);
        if (xingxingReunionPending) {
            xingxingCageBreakTimer = Math.max(0f, xingxingCageBreakTimer - dt);
            if (xingxingCageBreakTimer <= 0f) {
                xingxingReunionPending = false;
                reunionLaughPlayed = true;
                playXingxingGiggle();
                mode = MODE_REUNION;
                reunionSceneTimer = 1.75f;
                releaseControls();
                return;
            }
        }

        handlePressedEdges();
        updatePlayer(dt);
        updateObstacleInteractions();
        updateEnemies(dt);
        updateFireballs(dt);
        updateBerthu(dt);
        checkGoal();
        if (mode == MODE_PLAYING) checkRoomTransitions();
    }

    private void handlePressedEdges() {
        if ((pendingJump || (jumpHeld && !jumpWasHeld)) && onGround && playerState != STATE_HANG) {
            heroVy = -JUMP_SPEED;
            onGround = false;
            takeoffStretchTimer = .15f;
            landingSquashTimer = 0f;
            playerState = STATE_JUMP;
        }
        if ((pendingAttack || (attackHeld && !attackWasHeld))
                && attackCooldown <= 0f && playerState != STATE_HANG) {
            attackTimer = .34f;
            attackCooldown = .48f;
            playerState = STATE_ATTACK;
            attackFacing = facingRight ? 1 : -1;
            strikeEnemies();
        }
        // UP pulls Annie onto a ledge; ACT/FIRE is now reserved for her fireball.
        if (playerState == STATE_HANG && upHeld && !upWasHeld) {
            pullUpFromLedge();
        }
        if (pendingAction || (actionHeld && !actionWasHeld)) {
            launchFireball();
        }
        pendingJump = pendingAttack = pendingAction = false;
        jumpWasHeld = jumpHeld;
        attackWasHeld = attackHeld;
        actionWasHeld = actionHeld;
        upWasHeld = upHeld;
        runButtonWasHeld = runButtonHeld();
        pauseWasHeld = pauseButtonHeld();
        sfxWasHeld = sfxButtonHeld();
    }

    private boolean runButtonHeld() { return runButtonTouch; }
    private boolean pauseButtonHeld() { return pauseButtonTouch; }
    private boolean sfxButtonHeld() { return sfxButtonTouch; }
    private boolean runButtonTouch;
    private boolean pauseButtonTouch;
    private boolean sfxButtonTouch;

    private void updatePlayer(float dt) {
        if (playerState == STATE_HANG) {
            heroVx = heroVy = 0f;
            if (downHeld) {
                playerState = STATE_FALL;
                onGround = false;
                heroVy = 80f;
            }
            return;
        }

        if (playerState == STATE_PULL_UP) {
            pullUpTimer = Math.max(0f, pullUpTimer - dt);
            float progress = 1f - pullUpTimer / PULL_UP_DURATION;
            float eased = progress * progress * (3f - 2f * progress);
            heroX = lerp(pullUpStartX, pullUpTargetX, eased);
            heroY = lerp(pullUpStartY, pullUpTargetY, eased);
            heroVx = heroVy = 0f;
            if (pullUpTimer <= 0f) {
                heroX = pullUpTargetX;
                heroY = pullUpTargetY;
                onGround = true;
                playerState = STATE_IDLE;
                landingSquashTimer = .10f;
            }
            heroAnimationPhase += dt * 7f;
            return;
        }

        if (playerState == STATE_CLIMB) {
            heroVx = 0f;
            climbProgress = Math.min(1f, climbProgress + dt * 3.8f);
            float climbSpeed = 150f + 65f * climbProgress;
            heroY -= climbSpeed * dt;
            if (heroY <= ladderTopY - HERO_H * .15f) {
                int next = room().link(CinematicRoomManager.UP);
                if (next >= 0) {
                    loadRoom(next, true, CinematicRoomManager.DOWN);
                } else {
                    // Settle precisely onto the authored landing platform at the ladder top.
                    float landingY = floorY - HERO_H;
                    float ladderNorm = ladderX / Math.max(1f, viewW);
                    for (CinematicRoomManager.PlatformDef platform : room().platforms) {
                        if (ladderNorm >= platform.x && ladderNorm <= platform.x + platform.width) {
                            landingY = floorY - platform.height * viewH - HERO_H;
                            break;
                        }
                    }
                    heroY = landingY;
                    heroVx = heroVy = 0f;
                    onGround = true;
                    playerState = STATE_IDLE;
                    landingSquashTimer = .10f;
                }
            }
            heroAnimationPhase += dt * 8f;
            return;
        }

        float direction = (rightHeld ? 1f : 0f) - (leftHeld ? 1f : 0f);
        if (direction != 0f) facingRight = direction > 0f;
        float healthSpeedMultiplier = getHealthSpeedMultiplier();
        float speed = (runMode ? RUN_SPEED : WALK_SPEED) * healthSpeedMultiplier;
        float targetVx = direction * speed;
        float acceleration;
        if (direction == 0f) {
            acceleration = onGround ? GROUND_BRAKE : AIR_ACCEL * .58f;
        } else if (heroVx != 0f && Math.signum(heroVx) != Math.signum(targetVx)) {
            acceleration = onGround ? GROUND_ACCEL * 1.8f : AIR_ACCEL;
        } else {
            acceleration = onGround ? GROUND_ACCEL : AIR_ACCEL;
        }
        acceleration *= healthSpeedMultiplier;
        heroVx = moveTowards(heroVx, targetVx, acceleration * dt);
        if (Math.abs(heroVx) < 1.2f && direction == 0f) heroVx = 0f;
        if (heroVx > 2f) facingRight = true;
        else if (heroVx < -2f) facingRight = false;

        float previousX = heroX;
        heroX += heroVx * dt;
        heroX = Math.max(-HERO_W, Math.min(viewW, heroX));

        // If Annie walks off the edge of a supported surface, gravity must resume.
        if (onGround && !hasSupportUnderPlayer()) {
            onGround = false;
            heroVy = 8f;
        }

        // Gates in any of the 50 stages block Annie until their pressure plate is activated.
        if (!gateOpen && room().gateX >= 0f) {
            float gate = room().gateX * viewW;
            if (heroX + HERO_W > gate - 8f && heroX < gate && heroVx > 0f) {
                heroX = gate - HERO_W - 8f;
                heroVx = 0f;
            }
        }

        if (upHeld && ladderX >= 0f && Math.abs((heroX + HERO_W / 2f) - ladderX) < 47f
                && (onGround || Math.abs(heroY + HERO_H - floorY) < 18f)) {
            playerState = STATE_CLIMB;
            climbProgress = 0f;
            onGround = false;
            heroVx = heroVy = 0f;
            return;
        }

        if (playerState == STATE_CROUCH && !downHeld) playerState = STATE_IDLE;
        if (downHeld && onGround && Math.abs(heroVx) < 9f) playerState = STATE_CROUCH;
        float previousBottom = heroY + HERO_H;
        boolean wasAirborne = !onGround;
        if (!onGround) {
            heroVy += GRAVITY * dt;
            heroY += heroVy * dt;
        }
        collideWithPlatforms(previousBottom);
        collideWithFloor(previousBottom);
        if (wasAirborne && onGround) {
            landingSquashTimer = .17f;
            takeoffStretchTimer = 0f;
            // Keep a little run momentum after landing, but absorb the impact.
            heroVx *= .88f;
        }

        if (!onGround && heroVy > 0f && upHeld) tryGrabLedge();
        if (heroY > viewH + 40f) respawnAtCheckpoint();

        if (fireCastTimer > 0f) playerState = STATE_CAST;
        else if (attackTimer > 0f) playerState = STATE_ATTACK;
        else if (annieFlash > 0f) playerState = STATE_HURT;
        else if (!onGround) playerState = heroVy < 0f ? STATE_JUMP : STATE_FALL;
        else if (downHeld && Math.abs(heroVx) < 9f) playerState = STATE_CROUCH;
        else if (Math.abs(heroVx) > 16f) playerState = runMode && Math.abs(heroVx) > WALK_SPEED * 1.22f
                ? STATE_RUN : STATE_WALK;
        else playerState = STATE_IDLE;

        float speedRatio = Math.min(1f, Math.abs(heroVx) / RUN_SPEED);
        if (playerState == STATE_RUN) heroAnimationPhase += dt * (9f + speedRatio * 3f);
        else if (playerState == STATE_WALK) heroAnimationPhase += dt * (5.5f + speedRatio * 2f);
        else if (playerState == STATE_JUMP || playerState == STATE_FALL) heroAnimationPhase += dt * 8f;
        else if (playerState == STATE_IDLE) heroAnimationPhase += dt * 2f;
    }

    /** Full normal run/walk speed above 70% HP; declines smoothly to 30% at zero HP. */
    private float getHealthSpeedMultiplier() {
        float health = Math.max(0f, Math.min(1f, annieHp / ANNIE_MAX_HP));
        if (health >= LOW_HEALTH_SPEED_THRESHOLD) return 1f;
        float progress = health / LOW_HEALTH_SPEED_THRESHOLD;
        return LOW_HEALTH_MIN_SPEED + (1f - LOW_HEALTH_MIN_SPEED) * progress;
    }

    private static float moveTowards(float current, float target, float maxDelta) {
        if (Math.abs(target - current) <= maxDelta) return target;
        return current + Math.signum(target - current) * maxDelta;
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * Math.max(0f, Math.min(1f, amount));
    }

    private boolean hasSupportUnderPlayer() {
        float footY = heroY + HERO_H;
        for (CinematicRoomManager.PlatformDef p : room().platforms) {
            float px = p.x * viewW;
            float py = floorY - p.height * viewH;
            float pw = p.width * viewW;
            boolean overlaps = heroX + HERO_W - 7f > px && heroX + 7f < px + pw;
            if (overlaps && Math.abs(footY - py) < 6f) return true;
        }
        for (CinematicRoomManager.ObstacleDef o : room().obstacles) {
            if (o.type == CinematicRoomManager.ObstacleDef.MOVING_PLATFORM) {
                float px = movingPlatformX(o);
                float py = floorY - viewH * .29f;
                if (heroX + HERO_W - 7f > px && heroX + 7f < px + o.width * viewW
                        && Math.abs(footY - py) < 7f) return true;
            }
        }
        boolean inGap = false;
        for (CinematicRoomManager.GapDef gap : room().gaps) {
            float gx = gap.x * viewW;
            float gw = gap.width * viewW;
            if (heroX + HERO_W * .68f > gx && heroX + HERO_W * .32f < gx + gw) {
                inGap = true;
                break;
            }
        }
        return !inGap && Math.abs(footY - floorY) < 6f;
    }

    private void collideWithPlatforms(float previousBottom) {
        if (heroVy < 0f) return;
        for (CinematicRoomManager.PlatformDef p : room().platforms) {
            float px = p.x * viewW;
            float py = floorY - p.height * viewH;
            float pw = p.width * viewW;
            boolean overlaps = heroX + HERO_W - 7f > px && heroX + 7f < px + pw;
            if (overlaps && previousBottom <= py + 7f && heroY + HERO_H >= py) {
                heroY = py - HERO_H;
                heroVy = 0f;
                onGround = true;
                return;
            }
        }
        for (CinematicRoomManager.ObstacleDef o : room().obstacles) {
            if (o.type == CinematicRoomManager.ObstacleDef.MOVING_PLATFORM) {
                float px = movingPlatformX(o);
                float py = floorY - viewH * .29f;
                float pw = o.width * viewW;
                if (heroX + HERO_W - 7f > px && heroX + 7f < px + pw
                        && previousBottom <= py + 9f && heroY + HERO_H >= py) {
                    heroY = py - HERO_H;
                    heroVy = 0f;
                    onGround = true;
                    return;
                }
            }
        }
    }

    private void collideWithFloor(float previousBottom) {
        if (heroVy < 0f) return;
        boolean overGap = false;
        for (CinematicRoomManager.GapDef gap : room().gaps) {
            float gx = gap.x * viewW;
            float gw = gap.width * viewW;
            if (heroX + HERO_W * .68f > gx && heroX + HERO_W * .32f < gx + gw) {
                overGap = true;
                break;
            }
        }
        if (!overGap && previousBottom <= floorY + 5f && heroY + HERO_H >= floorY) {
            heroY = floorY - HERO_H;
            heroVy = 0f;
            onGround = true;
        } else if (overGap && heroY + HERO_H > floorY + 12f) {
            onGround = false;
        }
    }

    private void tryGrabLedge() {
        for (CinematicRoomManager.PlatformDef p : room().platforms) {
            float px = p.x * viewW;
            float py = floorY - p.height * viewH;
            float pw = p.width * viewW;
            float handY = heroY + 23f;
            boolean verticalReach = handY < py + 24f && handY > py - 25f;
            boolean edgeReach = Math.abs((heroX + HERO_W) - px) < 22f
                    || Math.abs(heroX - (px + pw)) < 22f;
            if (verticalReach && edgeReach) {
                hangingPlatformX = px;
                hangingPlatformY = py;
                hangingPlatformW = pw;
                if (Math.abs((heroX + HERO_W) - px) < 22f) {
                    heroX = px - HERO_W + 8f;
                    facingRight = true;
                } else {
                    heroX = px + pw - 8f;
                    facingRight = false;
                }
                heroY = py - HERO_H + 19f;
                heroVy = heroVx = 0f;
                playerState = STATE_HANG;
                onGround = false;
                messageTimer = 1.1f;
                return;
            }
        }
        for (CinematicRoomManager.ObstacleDef o : room().obstacles) {
            if (o.type != CinematicRoomManager.ObstacleDef.MOVING_PLATFORM) continue;
            float px = movingPlatformX(o);
            float py = floorY - viewH * .29f;
            float pw = o.width * viewW;
            float handY = heroY + 23f;
            boolean verticalReach = handY < py + 24f && handY > py - 25f;
            boolean edgeReach = Math.abs((heroX + HERO_W) - px) < 22f
                    || Math.abs(heroX - (px + pw)) < 22f;
            if (verticalReach && edgeReach) {
                hangingPlatformX = px;
                hangingPlatformY = py;
                hangingPlatformW = pw;
                if (Math.abs((heroX + HERO_W) - px) < 22f) {
                    heroX = px - HERO_W + 8f;
                    facingRight = true;
                } else {
                    heroX = px + pw - 8f;
                    facingRight = false;
                }
                heroY = py - HERO_H + 19f;
                heroVy = heroVx = 0f;
                playerState = STATE_HANG;
                onGround = false;
                messageTimer = 1.1f;
                return;
            }
        }
    }

    private void pullUpFromLedge() {
        if (playerState != STATE_HANG) return;
        float leftSide = hangingPlatformX;
        float targetX;
        if (heroX < leftSide) targetX = leftSide + 8f;
        else targetX = Math.max(leftSide + 8f, hangingPlatformX + hangingPlatformW - HERO_W - 8f);
        pullUpStartX = heroX;
        pullUpStartY = heroY;
        pullUpTargetX = targetX;
        pullUpTargetY = hangingPlatformY - HERO_H;
        pullUpTimer = PULL_UP_DURATION;
        heroVx = heroVy = 0f;
        onGround = false;
        playerState = STATE_PULL_UP;
        heroAnimationPhase = 0f;
    }

    private void updateEnemies(float dt) {
        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy enemy = it.next();
            enemy.attackCooldown = Math.max(0f, enemy.attackCooldown - dt);
            enemy.attackTimer = Math.max(0f, enemy.attackTimer - dt);
            enemy.hitFlash = Math.max(0f, enemy.hitFlash - dt);
            enemy.backflipCooldown = Math.max(0f, enemy.backflipCooldown - dt);
            enemy.rangedCooldown = Math.max(0f, enemy.rangedCooldown - dt);

            if (roomHasBossThisRoom && bossDefeatedThisRoom && !enemy.boss) {
                enemy.fleeing = true;
                enemy.attackTimer = 0f;
                enemy.backflipTimer = 0f;
                if (enemy.fleeDirection == 0f) {
                    enemy.fleeDirection = enemy.x + enemy.width * .5f >= viewW * .5f ? 1f : -1f;
                }
                enemy.vx = moveTowards(enemy.vx, enemy.fleeDirection * 255f, 920f * dt);
                enemy.x += enemy.vx * dt;
                enemy.moving = true;
                if (enemy.x < -enemy.width - 24f || enemy.x > viewW + 24f) it.remove();
                continue;
            }

            if (enemy.backflipTimer > 0f) {
                enemy.backflipTimer = Math.max(0f, enemy.backflipTimer - dt);
                float progress = 1f - enemy.backflipTimer / enemy.backflipDuration;
                float eased = progress * progress * (3f - 2f * progress);
                enemy.x = Math.max(enemy.patrolMin, Math.min(enemy.patrolMax,
                        enemy.backflipStartX + enemy.backflipDirection * 48f * eased));
                enemy.vx = 0f;
                enemy.moving = false;
                if (enemy.backflipTimer <= 0f) {
                    enemy.x = Math.max(enemy.patrolMin, Math.min(enemy.patrolMax,
                            enemy.backflipStartX + enemy.backflipDirection * 48f));
                    enemy.attackCooldown = Math.max(enemy.attackCooldown, .35f);
                }
                continue;
            }

            float center = enemy.x + enemy.width / 2f;
            float delta = (heroX + HERO_W / 2f) - center;
            float distance = Math.abs(delta);
            enemy.facingRight = delta > 0f;
            enemy.moving = false;

            // The named boss performs a readable, full-body backflip when Annie closes in.
            if (enemy.boss && enemy.backflipCooldown <= 0f && distance < 225f && distance > 105f
                    && enemy.hitFlash <= 0f && mode == MODE_PLAYING) {
                enemy.backflipDuration = .82f;
                enemy.backflipTimer = enemy.backflipDuration;
                enemy.backflipStartX = enemy.x;
                enemy.backflipDirection = enemy.facingRight ? -1f : 1f;
                enemy.backflipCooldown = 4.8f;
                enemy.attackTimer = 0f;
                enemy.vx = 0f;
                continue;
            }

            float desiredVx = 0f;
            if (distance < 255f && distance > 88f && enemy.attackTimer <= 0f) {
                desiredVx = Math.signum(delta) * (enemy.boss ? 66f : 82f);
            } else if (distance > 255f) {
                desiredVx = enemy.direction * 30f;
            }
            float enemyAcceleration = Math.abs(desiredVx) > 0f ? 360f : 560f;
            enemy.vx = moveTowards(enemy.vx, desiredVx, enemyAcceleration * dt);
            enemy.x += enemy.vx * dt;
            if (enemy.x < enemy.patrolMin || enemy.x > enemy.patrolMax) {
                enemy.x = Math.max(enemy.patrolMin, Math.min(enemy.patrolMax, enemy.x));
                enemy.direction *= -1f;
                if (distance > 255f) enemy.vx = 0f;
            }
            enemy.moving = Math.abs(enemy.vx) > 9f;

            delta = (heroX + HERO_W / 2f) - (enemy.x + enemy.width / 2f);
            distance = Math.abs(delta);
            enemy.facingRight = delta > 0f;

            // The world boss also launches a blue ranged fireball at intervals.
            if (enemy.boss && distance > 135f && distance < 520f
                    && enemy.rangedCooldown <= 0f && mode == MODE_PLAYING) {
                launchBossBlueFireball(enemy);
                enemy.rangedCooldown = 3.15f;
            }

            if (distance < (enemy.boss ? 198f : 176f) && enemy.attackCooldown <= 0f
                    && enemy.attackTimer <= .06f) {
                enemy.moving = false;
                enemy.vx = 0f;
                enemy.attackTimer = enemy.boss ? .68f : .53f;
                enemy.attackPose = enemy.attackCount % 2;
                enemy.attackCount++;
                enemy.attackCooldown = enemy.boss ? 1.48f : 1.16f;
                playAttackSound(enemy.attackPose == 1 || enemy.boss);
                float verticalDifference = Math.abs((heroY + HERO_H) - floorY);
                if (distance < (enemy.boss ? 86f : 74f) && verticalDifference < 31f && onGround) {
                    damageTeam(enemy.boss ? 42f : 12f);
                }
            }
            if (enemy.hp <= 0f) {
                defeatedEnemies++;
                if (enemy.boss) {
                    bossDefeatedThisRoom = true;
                    for (Enemy remaining : enemies) {
                        if (!remaining.boss) {
                            remaining.fleeing = true;
                            remaining.fleeDirection = remaining.x + remaining.width * .5f >= viewW * .5f ? 1f : -1f;
                        }
                    }
                    messageTimer = 2.4f;
                }
                it.remove();
            }
        }
    }

    private void strikeEnemies() {
        float start = facingRight ? heroX + HERO_W - 4f : heroX - 55f;
        float end = facingRight ? heroX + HERO_W + 66f : heroX + 55f;
        for (Enemy enemy : enemies) {
            boolean horizontalHit = enemy.x + enemy.width > start && enemy.x < end;
            boolean sameLevel = Math.abs((heroY + HERO_H) - floorY) < 38f;
            if (horizontalHit && sameLevel) {
                enemy.hp -= enemy.boss ? 28f : 42f;
                enemy.hitFlash = .25f;
                enemy.attackTimer = 0f;
            }
        }
    }

    /** Fires a bright projectile from Annie's palm and plays the shared cooldown whoosh. */
    private void launchFireball() {
        if (mode != MODE_PLAYING || fireballCooldown > 0f
                || playerState == STATE_HANG || playerState == STATE_CLIMB
                || playerState == STATE_PULL_UP) return;

        float direction = facingRight ? 1f : -1f;
        float handX = heroX + HERO_W * .5f + direction * (HERO_W * .46f);
        float handY = heroY + HERO_H * .305f; // upper chest / breast level rather than waist level
        fireballs.add(new Fireball(handX + direction * 4f, handY,
                direction * FIREBALL_SPEED, 0f, false, 0f));
        fireballCooldown = FIREBALL_COOLDOWN;
        fireCastTimer = FIRE_CAST_DURATION;
        attackFacing = facingRight ? 1 : -1;
        playerState = STATE_CAST;
        playFireballWhoosh();
    }

    /** Launches a blue fireball aimed from the boss toward Annie's current position. */
    private void launchBossBlueFireball(Enemy enemy) {
        // Hold the existing forward punch image briefly while the boss releases the shot.
        enemy.attackPose = 0;
        enemy.attackTimer = Math.max(enemy.attackTimer, .48f);
        enemy.attackCooldown = Math.max(enemy.attackCooldown, .62f);
        enemy.moving = false;
        enemy.vx = 0f;

        float direction = enemy.facingRight ? 1f : -1f;
        float startX = enemy.x + enemy.width * .5f + direction * enemy.width * .28f;
        float startY = floorY - 72f;
        float targetX = heroX + HERO_W * .5f;
        float targetY = heroY + HERO_H * .42f;
        float dx = targetX - startX;
        float dy = targetY - startY;
        float length = Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
        float vx = dx / length * BOSS_BLUE_FIREBALL_SPEED;
        float vy = dy / length * BOSS_BLUE_FIREBALL_SPEED;
        fireballs.add(new Fireball(startX, startY, vx, vy, true, BOSS_BLUE_FIREBALL_DAMAGE));
    }

    /** Advances Annie's orange fireballs and the boss's hostile blue fireballs. */
    private void updateFireballs(float dt) {
        Iterator<Fireball> shots = fireballs.iterator();
        while (shots.hasNext()) {
            Fireball shot = shots.next();
            shot.x += shot.vx * dt;
            shot.y += shot.vy * dt;
            shot.life -= dt;
            shot.animationPhase += dt * 18f;

            if (shot.life <= 0f || shot.x < -36f || shot.x > viewW + 36f) {
                shots.remove();
                continue;
            }

            if (shot.hostile) {
                RectF heroBounds = new RectF(heroX + 5f, heroY + 12f,
                        heroX + HERO_W - 5f, heroY + HERO_H - 7f);
                if (shot.x + 15f >= heroBounds.left && shot.x - 15f <= heroBounds.right
                        && shot.y + 13f >= heroBounds.top && shot.y - 13f <= heroBounds.bottom) {
                    damageTeam(shot.damage);
                    shots.remove();
                }
                continue;
            }

            boolean consumed = false;
            for (Enemy enemy : enemies) {
                float enemyHeight = enemy.boss ? 108f : 91f;
                float enemyTop = floorY - enemyHeight;
                boolean horizontalHit = shot.x + 15f >= enemy.x
                        && shot.x - 15f <= enemy.x + enemy.width;
                boolean verticalHit = shot.y >= enemyTop + 12f && shot.y <= floorY - 10f;
                if (horizontalHit && verticalHit) {
                    enemy.hp -= FIREBALL_DAMAGE;
                    enemy.hitFlash = .24f;
                    enemy.attackTimer = 0f;
                    consumed = true;
                    break;
                }
            }
            if (consumed) shots.remove();
        }
    }

    private void damageTeam(float amount) {
        if (hurtCooldown > 0f || mode != MODE_PLAYING) return;
        hurtCooldown = .76f;
        berthuRegenDelay = 5f;
        if (berthuHp > 0f) {
            berthuHitsThisRoom++;
            berthuHp = Math.max(0f, berthuHp - amount * BERTHU_DAMAGE_SHARE);
            annieHp = Math.max(0f, annieHp - amount * (1f - BERTHU_DAMAGE_SHARE));
            berthuFlash = .32f;
            if (berthuHitsThisRoom % 5 == 0) {
                playBerthuMeow();
            }
        } else {
            annieHp = Math.max(0f, annieHp - amount);
        }
        annieFlash = .24f;
        if (annieHp <= 0f) mode = MODE_GAME_OVER;
    }
    private void updateBerthu(float dt) {
        if (berthuJumpCooldown > 0f) berthuJumpCooldown = Math.max(0f, berthuJumpCooldown - dt);

        boolean plateRun = berthuPlateCommandActive && !gateOpen && room().pressurePlateX >= 0f;
        float target;
        float desiredVelocity;
        if (plateRun) {
            target = berthuPlateTargetX - 24f;
            float difference = target - berthuX;
            desiredVelocity = Math.abs(difference) < 18f ? 0f : Math.signum(difference) * 335f;
            berthuVx = moveTowards(berthuVx, desiredVelocity, 1500f * dt);
        } else {
            target = Math.max(8f, heroX - (facingRight ? 60f : -60f));
            float difference = target - berthuX;
            float springAcceleration = difference * 22f - berthuVx * 9.5f;
            berthuVx += springAcceleration * dt;
            berthuVx = Math.max(-245f, Math.min(245f, berthuVx));
        }

        if (Math.abs(berthuVx) > 3f) berthuFacingRight = berthuVx > 0f;
        float direction = Math.abs(berthuVx) > 3f ? Math.signum(berthuVx) : Math.signum(target - berthuX);

        // Berthu anticipates every authored spike pit and automatically hops across it.
        if (berthuOnGround && berthuJumpCooldown <= 0f && direction != 0f
                && berthuGapAhead(direction)) {
            // Jump early enough to clear the complete pit/spike strip rather than stepping through it.
            berthuVy = -455f;
            berthuOnGround = false;
            berthuJumpCooldown = .78f;
        }

        berthuX += berthuVx * dt;
        berthuX = Math.max(0f, Math.min(viewW - 24f, berthuX));

        if (!berthuOnGround) {
            berthuVy += GRAVITY * .82f * dt;
            berthuY += berthuVy * dt;
            if (berthuVy >= 0f && berthuY + BERTHU_H >= floorY
                    && !berthuBodyOverGap(berthuX)) {
                berthuY = floorY - BERTHU_H;
                berthuVy = 0f;
                berthuOnGround = true;
                berthuLastSafeX = berthuX;
            }
            if (berthuY > viewH + 36f) {
                berthuX = berthuLastSafeX;
                berthuY = floorY - BERTHU_H;
                berthuVy = 0f;
                berthuVx = 0f;
                berthuOnGround = true;
            }
        } else if (!berthuBodyOverGap(berthuX)) {
            berthuY = floorY - BERTHU_H;
            berthuLastSafeX = berthuX;
        } else {
            // If the cat was pushed to an edge unexpectedly, let gravity take over.
            berthuOnGround = false;
            berthuVy = 35f;
        }

        if (plateRun && Math.abs(target - berthuX) < 16f && berthuOnGround
                && Math.abs(berthuVx) < 36f) {
            berthuX = target;
            berthuVx = 0f;
            berthuPlateCommandActive = false;
            gateOpen = true;
            roomWasVisited = true;
            checkpointX = heroX;
            checkpointY = heroY;
            playBerthuMeow();
            messageTimer = 1.7f;
        }

        float catSpeed = Math.min(1f, Math.abs(berthuVx) / 260f);
        berthuAnimationPhase += dt * (2f + catSpeed * 12f);
    }

    private boolean berthuGapAhead(float direction) {
        float leadingEdge = direction > 0f ? berthuX + 53f : berthuX + 5f;
        for (CinematicRoomManager.GapDef gap : room().gaps) {
            float start = gap.x * viewW;
            float end = start + gap.width * viewW;
            float clearance = direction > 0f ? start - leadingEdge : leadingEdge - end;
            if (clearance >= -58f && clearance <= 112f) return true;
        }
        // Some spike strips are explicitly authored hazards even when the floor is continuous.
        for (CinematicRoomManager.HazardDef hazard : room().hazards) {
            float start = hazard.x * viewW;
            float end = start + hazard.width * viewW;
            float clearance = direction > 0f ? start - leadingEdge : leadingEdge - end;
            if (clearance >= -42f && clearance <= 104f) return true;
        }
        return false;
    }

    private boolean berthuBodyOverGap(float x) {
        float left = x + 5f;
        float right = x + 51f;
        for (CinematicRoomManager.GapDef gap : room().gaps) {
            float gx = gap.x * viewW;
            float gr = gx + gap.width * viewW;
            if (right > gx && left < gr) return true;
        }
        return false;
    }


    private boolean startBerthuPressurePlateRun() {
        if (gateOpen || room().pressurePlateX < 0f) return false;
        berthuPlateTargetX = room().pressurePlateX * viewW;
        berthuPlateCommandActive = true;
        berthuVx = 0f;
        messageTimer = 1.6f;
        return true;
    }

    private boolean handleBerthuDoubleTap(float x, float y, long eventTimeMs) {
        if (room().pressurePlateX < 0f || gateOpen) return false;
        float bx = berthuX + roomTransitionOffsetX;
        float by = berthuY + roomTransitionOffsetY;
        float bw = 63f;
        float bh = BERTHU_H + 13f;
        boolean onCat = x >= bx - 10f && x <= bx + bw + 10f
                && y >= by - 12f && y <= by + bh;
        if (!onCat) return false;

        boolean secondTap = eventTimeMs - lastBerthuTapTimeMs <= 420L
                && Math.abs(x - lastBerthuTapX) <= 48f
                && Math.abs(y - lastBerthuTapY) <= 42f;
        lastBerthuTapTimeMs = eventTimeMs;
        lastBerthuTapX = x;
        lastBerthuTapY = y;
        if (secondTap) {
            lastBerthuTapTimeMs = -1000L;
            return startBerthuPressurePlateRun();
        }
        messageTimer = 0.6f;
        return true;
    }

    private void checkRoomTransitions() {
        if (playerState == STATE_HANG || playerState == STATE_CLIMB) return;
        // Keep world finales contained until the boss and Xingxing reunion are complete.
        if (CinematicRoomManager.isWorldFinale(roomId)) {
            // Contain Annie inside the arena without cancelling her run velocity.
            // The previous unconditional heroVx=0 here made boss-stage movement feel like a snail.
            if (heroX < 0f) {
                heroX = 0f;
                if (heroVx < 0f) heroVx = 0f;
            } else if (heroX > viewW - HERO_W) {
                heroX = viewW - HERO_W;
                if (heroVx > 0f) heroVx = 0f;
            }
            return;
        }
        if (heroX > viewW - HERO_W * .25f) {
            int next = room().link(CinematicRoomManager.RIGHT);
            if (next >= 0) loadRoom(next, true, CinematicRoomManager.LEFT);
            else heroX = viewW - HERO_W;
        } else if (heroX < -HERO_W * .25f) {
            int next = room().link(CinematicRoomManager.LEFT);
            if (next >= 0) loadRoom(next, true, CinematicRoomManager.RIGHT);
            else heroX = 0f;
        }
    }

    private void checkGoal() {
        if (mode != MODE_PLAYING || room().monkeyX < 0f || reunionLaughPlayed
                || xingxingReunionPending || xingxingCageBroken) return;
        if (roomHasBossThisRoom && !bossDefeatedThisRoom) return;
        float goalX = room().monkeyX * viewW;
        if (onGround && Math.abs((heroX + HERO_W / 2f) - goalX) < 78f) {
            // Reaching Xingxing breaks the little prison first; his giggle follows after the break.
            xingxingCageBroken = true;
            xingxingCageBreakTimer = .58f;
            xingxingReunionPending = true;
            heroVx = 0f;
            messageTimer = 1.2f;
        }
    }

    private void respawnAtCheckpoint() {
        heroX = checkpointX;
        heroY = checkpointY;
        heroVy = 0f;
        onGround = true;
        damageTeam(9f);
        messageTimer = 1.3f;
    }

    private void drawRoomBackground(Canvas canvas) {
        CinematicRoomManager.RoomDef r = room();
        paint.setShader(new LinearGradient(0f, 0f, 0f, viewH, r.skyTop, r.skyBottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, viewW, viewH, paint);
        paint.setShader(null);
        drawSunAndAtmosphere(canvas, r);
        canvas.save();
        canvas.translate((float) Math.sin(animationTime * .11f) * 3.5f, 0f);
        drawFarSilhouette(canvas, r);
        canvas.restore();
        canvas.save();
        canvas.translate((float) Math.sin(animationTime * .17f + .8f) * 5.5f, 0f);
        drawMidground(canvas, r);
        canvas.restore();
        canvas.save();
        canvas.translate((float) Math.sin(animationTime * .25f + 1.7f) * 1.8f, 0f);
        drawForegroundDecor(canvas, r);
        canvas.restore();
        drawAmbientMotes(canvas, r);
    }

    private void drawAmbientMotes(Canvas canvas, CinematicRoomManager.RoomDef r) {
        // Deterministic, low-cost dust/pollen motes add depth without allocating particles each frame.
        for (int i = 0; i < 13; i++) {
            float speed = 5f + (i % 4) * 3.2f;
            float x = (i * 97f + animationTime * speed) % (viewW + 24f) - 12f;
            float baseY = viewH * (.22f + (i % 6) * .095f);
            float y = baseY + (float) Math.sin(animationTime * (1.1f + i * .07f) + i) * 7f;
            int alpha = 24 + (int) (16f * (.5f + .5f * Math.sin(animationTime * 1.5f + i)));
            paint.setColor(Color.argb(alpha, r.theme == 3 ? 255 : 245,
                    r.theme == 3 ? 190 : 220, r.theme == 3 ? 145 : 170));
            float radius = 1f + (i % 3) * .45f;
            canvas.drawCircle(x, y, radius, paint);
        }
    }

    private void drawSunAndAtmosphere(Canvas canvas, CinematicRoomManager.RoomDef r) {
        if (r.theme == 3) {
            paint.setColor(Color.argb(32, 185, 154, 255));
            canvas.drawCircle(viewW * .52f, viewH * .34f, viewH * .28f, paint);
            paint.setColor(Color.argb(115, 255, 177, 99));
            canvas.drawCircle(viewW * .83f, viewH * .19f, 19f, paint);
        } else {
            paint.setColor(Color.argb(180, 255, 227, 167));
            canvas.drawCircle(viewW * .79f, viewH * .17f, 25f, paint);
            paint.setColor(Color.argb(24, 255, 255, 255));
            canvas.drawCircle(viewW * .79f, viewH * .17f, 42f, paint);
        }
        for (int i = 0; i < 13; i++) {
            float x = (i * 93f + 27f) % viewW;
            float y = 55f + (i * 47f) % Math.max(90f, viewH * .55f);
            int alpha = 18 + (int) (12f * (0.5f + 0.5f * Math.sin(animationTime * .7f + i)));
            paint.setColor(Color.argb(alpha, 255, 240, 205));
            canvas.drawCircle(x, y, 1.8f + (i % 3), paint);
        }
    }

    private void drawFarSilhouette(Canvas canvas, CinematicRoomManager.RoomDef r) {
        Path ridge = new Path();
        ridge.moveTo(0f, floorY);
        ridge.lineTo(0f, viewH * .54f);
        for (int i = 0; i < 8; i++) {
            float x = i * viewW / 7f;
            float y = viewH * (.43f + .07f * (float) Math.sin(i * 1.7f + r.theme));
            ridge.lineTo(x, y);
        }
        ridge.lineTo(viewW, floorY);
        ridge.close();
        paint.setColor(r.farColor);
        canvas.drawPath(ridge, paint);

        if (r.theme == 0) {
            // Distant school block, gables and classroom windows.
            paint.setColor(Color.argb(165, 61, 76, 91));
            canvas.drawRect(viewW * .08f, viewH * .28f, viewW * .49f, floorY, paint);
            canvas.drawRect(viewW * .12f, viewH * .22f, viewW * .45f, viewH * .29f, paint);
            paint.setColor(Color.argb(95, 240, 218, 174));
            for (int i = 0; i < 5; i++) canvas.drawRect(viewW * (.13f + i * .064f), viewH * .34f,
                    viewW * (.16f + i * .064f), viewH * .49f, paint);
        } else if (r.theme == 1 || r.theme == 3) {
            // Old-city and jungle canopy silhouettes.
            for (int i = 0; i < 5; i++) {
                float x = viewW * (.08f + i * .21f);
                paint.setColor(Color.argb(110, 28, 67, 53));
                canvas.drawCircle(x, viewH * .39f, viewH * .12f, paint);
                canvas.drawCircle(x + 23f, viewH * .43f, viewH * .09f, paint);
            }
        } else if (r.theme == 2 || r.theme == 5) {
            paint.setColor(Color.argb(180, 47, 43, 59));
            for (int i = 0; i < 7; i++) {
                float x = i * viewW / 6f;
                canvas.drawRect(x, viewH * .34f, x + viewW * .075f, floorY, paint);
                canvas.drawRect(x - 4f, viewH * .30f, x + viewW * .085f, viewH * .345f, paint);
            }
            paint.setColor(Color.argb(65, 244, 184, 115));
            canvas.drawRect(0f, viewH * .30f, viewW, viewH * .315f, paint);
        } else if (r.theme == 4) {
            // Underground kingdom: deep cave vaults and luminous crystal teeth.
            paint.setColor(Color.argb(190, 24, 25, 48));
            for (int i = 0; i < 7; i++) {
                float x = i * viewW / 6f;
                paint.setColor(0xFF292A47);
                canvas.drawRect(x, viewH * .30f, x + viewW * .06f, floorY, paint);
                paint.setColor(i % 2 == 0 ? 0xFF78C6D6 : 0xFF9E8BE0);
                Path crystal = new Path();
                crystal.moveTo(x + 10f, viewH * .48f);
                crystal.lineTo(x + 24f, viewH * .31f);
                crystal.lineTo(x + 37f, viewH * .48f);
                crystal.close(); canvas.drawPath(crystal, paint);
            }
        } else if (r.theme == 5) {
            // Mountain passage: layered peaks and narrow cliff silhouettes.
            Path peaks = new Path();
            peaks.moveTo(0f, floorY);
            for (int i = 0; i <= 8; i++) {
                float x = i * viewW / 8f;
                float y = viewH * (.30f + .11f * (i % 2));
                peaks.lineTo(x, y);
                peaks.lineTo(x + viewW / 16f, viewH * .54f);
            }
            peaks.lineTo(viewW, floorY); peaks.close();
            paint.setColor(0xFF40566D); canvas.drawPath(peaks, paint);
            paint.setColor(0xFFBECBD3);
            for (int i = 0; i < 6; i++) {
                float x = viewW * (.07f + i * .17f);
                canvas.drawLine(x, viewH * .34f, x + 18f, viewH * .38f, paint);
            }
        } else if (r.theme == 6) {
            // Night citadel: tall towers, narrow arrow slits and moonlit battlements.
            paint.setColor(0xFF25243E);
            for (int i = 0; i < 6; i++) {
                float x = viewW * (.04f + i * .18f);
                canvas.drawRect(x, viewH * .24f, x + viewW * .10f, floorY, paint);
                Path spire = new Path();
                spire.moveTo(x - 3f, viewH * .25f);
                spire.lineTo(x + viewW * .05f, viewH * .12f);
                spire.lineTo(x + viewW * .10f + 3f, viewH * .25f);
                spire.close(); canvas.drawPath(spire, paint);
                paint.setColor(0xFFCF9A72);
                canvas.drawRect(x + viewW * .04f, viewH * .34f, x + viewW * .055f, viewH * .43f, paint);
                paint.setColor(0xFF25243E);
            }
        } else {
            // Xingxing's kingdom: soft magical tree arches and glowing lantern trees.
            for (int i = 0; i < 6; i++) {
                float x = viewW * (.04f + i * .19f);
                paint.setColor(0xFF35546A);
                canvas.drawRect(x, viewH * .33f, x + 13f, floorY, paint);
                paint.setColor(i % 2 == 0 ? 0xFF587A73 : 0xFF766E94);
                canvas.drawCircle(x + 8f, viewH * .31f, 36f + (i % 3) * 7f, paint);
                paint.setColor(0xFFFFD77E);
                canvas.drawCircle(x + 18f, viewH * .42f, 4f, paint);
            }
        }
    }

    private void drawMidground(Canvas canvas, CinematicRoomManager.RoomDef r) {
        if (r.theme == 0) {
            drawTree(canvas, viewW * .67f, floorY - 5f, 1.1f, 0xFF385D4B);
            drawTree(canvas, viewW * .91f, floorY - 4f, .78f, 0xFF42684F);
            drawArch(canvas, viewW * .49f, viewH * .35f, viewW * .16f, viewH * .31f, 0xFF697E82);
        } else if (r.theme == 1 || r.theme == 3) {
            drawTree(canvas, viewW * .12f, floorY, .85f, 0xFF31583E);
            drawTree(canvas, viewW * .57f, floorY, 1.15f, 0xFF315C45);
            drawArch(canvas, viewW * .78f, viewH * .34f, viewW * .15f, viewH * .30f, 0xFF6C7969);
            drawPillar(canvas, viewW * .34f, viewH * .36f, viewH * .42f, 0xFF7D806E);
        } else if (r.theme == 2 || r.theme == 5) {
            for (int i = 0; i < 4; i++) drawPillar(canvas, viewW * (.12f + i * .26f), viewH * .28f, viewH * .49f,
                    0xFF787078);
            drawArch(canvas, viewW * .69f, viewH * .29f, viewW * .20f, viewH * .33f, 0xFF776C72);
        } else if (r.theme == 4) {
            for (int i = 0; i < 7; i++) {
                float x = viewW * (.06f + i * .15f);
                drawPillar(canvas, x, viewH * .24f, viewH * .52f, 0xFF55516C);
                paint.setColor(i % 2 == 0 ? 0xFF82D5DD : 0xFF9D8DE8);
                Path crystal = new Path();
                crystal.moveTo(x + 12f, floorY - 44f);
                crystal.lineTo(x + 23f, floorY - 115f - (i % 3) * 12f);
                crystal.lineTo(x + 37f, floorY - 44f);
                crystal.close(); canvas.drawPath(crystal, paint);
            }
        } else if (r.theme == 5) {
            drawPillar(canvas, viewW * .18f, viewH * .30f, viewH * .48f, 0xFF718596);
            drawPillar(canvas, viewW * .76f, viewH * .27f, viewH * .51f, 0xFF687D91);
            drawArch(canvas, viewW * .40f, viewH * .38f, viewW * .22f, viewH * .28f, 0xFF7D8D98);
        } else if (r.theme == 6) {
            for (int i = 0; i < 5; i++) {
                float x = viewW * (.10f + i * .20f);
                drawPillar(canvas, x, viewH * .21f, viewH * .57f, 0xFF555063);
                paint.setColor(Color.argb(90, 255, 152, 83));
                canvas.drawCircle(x + 18f, viewH * .57f, 9f + 2f * (float) Math.sin(animationTime * 4f + i), paint);
                paint.setColor(Color.argb(35, 255, 178, 106));
                canvas.drawCircle(x + 18f, viewH * .57f, 20f, paint);
            }
        } else {
            drawTree(canvas, viewW * .17f, floorY, 1.15f, 0xFF557D6D);
            drawTree(canvas, viewW * .82f, floorY, 1.25f, 0xFF6F668E);
            drawArch(canvas, viewW * .39f, viewH * .30f, viewW * .22f, viewH * .36f, 0xFF7D8E91);
        }
    }

    private void drawForegroundDecor(Canvas canvas, CinematicRoomManager.RoomDef r) {
        paint.setColor(Color.argb(55, 13, 19, 23));
        canvas.drawRect(0f, floorY - 6f, viewW, floorY + 15f, paint);
        if (r.theme == 0 || r.theme == 1 || r.theme == 3) {
            for (int i = 0; i < 24; i++) {
                float x = (i * 61f + 12f) % viewW;
                paint.setColor(Color.argb(85, 37 + (i % 3) * 8, 77 + (i % 4) * 6, 47));
                Path grass = new Path();
                grass.moveTo(x, floorY + 8f);
                grass.quadTo(x - 3f, floorY - 4f - (i % 4) * 2f, x + 2f, floorY - 1f);
                grass.quadTo(x + 7f, floorY - 8f, x + 5f, floorY + 8f);
                canvas.drawPath(grass, paint);
            }
        } else {
            paint.setColor(Color.argb(35, 231, 188, 141));
            for (int i = 0; i < 14; i++) {
                float x = (i * 83f) % viewW;
                canvas.drawCircle(x, floorY + 26f + (i % 3) * 13f, 1.5f + (i % 2), paint);
            }
        }
    }

    private void drawTree(Canvas canvas, float x, float base, float scale, int foliage) {
        paint.setColor(0xFF51483B);
        canvas.drawRoundRect(x - 7f * scale, base - 105f * scale, x + 8f * scale, base,
                5f, 5f, paint);
        paint.setColor(foliage);
        canvas.drawCircle(x, base - 115f * scale, 41f * scale, paint);
        canvas.drawCircle(x - 28f * scale, base - 95f * scale, 28f * scale, paint);
        canvas.drawCircle(x + 27f * scale, base - 93f * scale, 31f * scale, paint);
        paint.setColor(Color.argb(45, 224, 235, 171));
        canvas.drawCircle(x - 11f * scale, base - 130f * scale, 15f * scale, paint);
    }

    private void drawArch(Canvas canvas, float x, float y, float w, float h, int color) {
        paint.setColor(color);
        canvas.drawRect(x, y + h * .28f, x + w, y + h, paint);
        RectF cap = new RectF(x, y, x + w, y + h * .56f);
        canvas.drawArc(cap, 180f, 180f, false, paint);
        paint.setColor(Color.argb(150, 28, 36, 43));
        canvas.drawRect(x + w * .25f, y + h * .48f, x + w * .75f, y + h, paint);
        paint.setColor(Color.argb(80, 238, 212, 174));
        canvas.drawRect(x - 4f, y + h * .26f, x + 5f, y + h, paint);
    }

    private void drawPillar(Canvas canvas, float x, float y, float h, int color) {
        paint.setColor(color);
        canvas.drawRect(x, y + 12f, x + 28f, y + h, paint);
        canvas.drawRect(x - 7f, y + 6f, x + 35f, y + 17f, paint);
        canvas.drawRect(x - 10f, y + h - 5f, x + 38f, y + h + 6f, paint);
        paint.setColor(Color.argb(55, 255, 255, 255));
        canvas.drawRect(x + 5f, y + 18f, x + 9f, y + h - 8f, paint);
    }

    private void drawRoomGeometry(Canvas canvas) {
        CinematicRoomManager.RoomDef r = room();
        // Room floor is split around authored gaps.
        float cursor = 0f;
        for (CinematicRoomManager.GapDef gap : r.gaps) {
            float gx = gap.x * viewW;
            float gw = gap.width * viewW;
            drawFloorSegment(canvas, cursor, gx, r);
            cursor = gx + gw;
        }
        drawFloorSegment(canvas, cursor, viewW, r);

        for (CinematicRoomManager.PlatformDef p : r.platforms) {
            float x = p.x * viewW;
            float y = floorY - p.height * viewH;
            float w = p.width * viewW;
            paint.setShader(new LinearGradient(x, y, x, y + 21f,
                    r.floorHighlight, r.floorColor, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(x, y, x + w, y + 21f, 5f, 5f, paint);
            paint.setShader(null);
            paint.setColor(Color.argb(120, 24, 29, 32));
            canvas.drawRect(x + 4f, y + 21f, x + w - 4f, y + 28f, paint);
            paint.setColor(Color.argb(70, 255, 239, 205));
            canvas.drawRect(x + 7f, y + 3f, x + w - 7f, y + 5f, paint);
        }
        for (CinematicRoomManager.ObstacleDef o : r.obstacles) {
            if (o.type == CinematicRoomManager.ObstacleDef.MOVING_PLATFORM) {
                float x = movingPlatformX(o);
                float y = floorY - viewH * .29f;
                paint.setShader(new LinearGradient(x, y, x, y + 19f, 0xFFFFD27A, 0xFF8B684C, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(x, y, x + o.width * viewW, y + 19f, 5f, 5f, paint);
                paint.setShader(null);
                paint.setColor(0xFF3F3945);
                canvas.drawRect(x + 8f, y + 19f, x + o.width * viewW - 8f, y + 25f, paint);
            }
        }
        if (ladderX >= 0f) drawLadder(canvas, ladderX, ladderTopY, floorY);
    }

    private void drawFloorSegment(Canvas canvas, float x, float end, CinematicRoomManager.RoomDef r) {
        if (end <= x) return;
        paint.setShader(new LinearGradient(0f, floorY, 0f, viewH, r.floorHighlight,
                r.floorColor, Shader.TileMode.CLAMP));
        canvas.drawRect(x, floorY, end, viewH + 5f, paint);
        paint.setShader(null);
        paint.setColor(Color.argb(100, 28, 26, 31));
        canvas.drawRect(x, floorY + 9f, end, floorY + 12f, paint);
        paint.setColor(Color.argb(48, 16, 21, 23));
        for (float tx = x + 18f; tx < end; tx += 54f) {
            canvas.drawLine(tx, floorY + 22f, tx + 23f, floorY + 22f, paint);
            canvas.drawLine(tx + 14f, floorY + 41f, tx + 42f, floorY + 41f, paint);
        }
    }

    private void drawLadder(Canvas canvas, float x, float top, float bottom) {
        paint.setColor(0xFF6C503A);
        paint.setStrokeWidth(5f);
        canvas.drawLine(x - 13f, top, x - 13f, bottom, paint);
        canvas.drawLine(x + 13f, top, x + 13f, bottom, paint);
        paint.setStrokeWidth(3f);
        for (float y = top + 9f; y < bottom; y += 17f) canvas.drawLine(x - 13f, y, x + 13f, y, paint);
        paint.setStrokeWidth(1f);
    }

    private void drawPressurePlateAndGate(Canvas canvas) {
        CinematicRoomManager.RoomDef r = room();
        if (r.pressurePlateX >= 0f) {
            float x = r.pressurePlateX * viewW;
            paint.setColor(0xFF9A7542);
            canvas.drawRoundRect(x - 20f, floorY - 6f, x + 20f, floorY + 4f, 4f, 4f, paint);
            paint.setColor(0xFFFFD47D);
            canvas.drawRoundRect(x - 14f, floorY - 4f, x + 14f, floorY, 3f, 3f, paint);
            if (Math.abs((heroX + HERO_W / 2f) - x) < 23f && onGround) {
                gateOpen = true;
                roomWasVisited = true;
                messageTimer = 1.5f;
                checkpointX = heroX;
                checkpointY = floorY - HERO_H;
            }
        }
        if (r.gateX >= 0f) {
            float x = r.gateX * viewW;
            if (!gateOpen) {
                paint.setColor(0xFF594A42);
                canvas.drawRoundRect(x - 8f, floorY - 140f, x + 8f, floorY + 2f, 5f, 5f, paint);
                paint.setColor(0xFFD1A96B);
                for (int i = 0; i < 5; i++) canvas.drawRoundRect(x - 7f, floorY - 130f + i * 25f,
                        x + 7f, floorY - 126f + i * 25f, 2f, 2f, paint);
            } else {
                paint.setColor(Color.argb(90, 255, 211, 129));
                canvas.drawCircle(x, floorY - 68f, 30f, paint);
                paint.setColor(0xFFFFD47D);
                canvas.drawText("GATE OPEN", x - 30f, floorY - 154f, paint);
            }
        }
    }

    private void drawHazards(Canvas canvas) {
        for (CinematicRoomManager.HazardDef h : room().hazards) {
            float x = h.x * viewW;
            float w = h.width * viewW;
            paint.setColor(0xFF342C35);
            canvas.drawRect(x, floorY - 1f, x + w, floorY + 8f, paint);
            paint.setColor(h.heavy ? 0xFFCC9B75 : 0xFFB9B7B0);
            Path spikes = new Path();
            int count = Math.max(3, (int) (w / 14f));
            spikes.moveTo(x, floorY + 1f);
            for (int i = 0; i < count; i++) {
                float sx = x + i * (w / count);
                spikes.lineTo(sx + w / count * .5f, floorY - (h.heavy ? 29f : 19f));
                spikes.lineTo(sx + w / count, floorY + 1f);
            }
            spikes.close();
            canvas.drawPath(spikes, paint);
        }
    }

    private float movingPlatformX(CinematicRoomManager.ObstacleDef o) {
        return (o.x + (float) Math.sin(animationTime * 1.35f + o.phase) * o.range) * viewW;
    }
    private void drawObstacles(Canvas canvas) {
        for (CinematicRoomManager.ObstacleDef o : room().obstacles) {
            float x = o.x * viewW;
            if (o.type == CinematicRoomManager.ObstacleDef.MOVING_PLATFORM) continue;
            if (o.type == CinematicRoomManager.ObstacleDef.SWING_BLADE) {
                float anchorX = x;
                float anchorY = viewH * .20f;
                float swing = (float) Math.sin(animationTime * 2.45f + o.phase) * .42f;
                float length = viewH * .37f;
                float ballX = anchorX + (float) Math.sin(swing) * length;
                float ballY = anchorY + (float) Math.cos(swing) * length;
                drawWreckingBall(canvas, anchorX, anchorY, ballX, ballY, 23f,
                        animationTime * 24f + o.phase * 33f);
            } else if (o.type == CinematicRoomManager.ObstacleDef.FIRE_JET) {
                float cycle = (animationTime + o.phase) % 2.65f;
                boolean active = cycle < 1.15f;
                paint.setColor(0xFF51413C);
                canvas.drawRoundRect(x - 14f, floorY - 12f, x + 14f, floorY + 5f, 5f, 5f, paint);
                if (active) {
                    float flicker = 1f + .10f * (float) Math.sin(animationTime * 18f + o.phase);
                    for (int k = -1; k <= 1; k++) {
                        float fx = x + k * 8f;
                        paint.setColor(0xDFFF6C32);
                        Path flame = new Path();
                        flame.moveTo(fx - 8f, floorY - 5f);
                        flame.quadTo(fx - 15f, floorY - 31f * flicker, fx, floorY - 56f * flicker);
                        flame.quadTo(fx + 13f, floorY - 27f * flicker, fx + 8f, floorY - 5f);
                        flame.close(); canvas.drawPath(flame, paint);
                        paint.setColor(0xFFFFD36A);
                        canvas.drawOval(fx - 4f, floorY - 24f * flicker, fx + 4f, floorY - 6f, paint);
                    }
                }
            } else if (o.type == CinematicRoomManager.ObstacleDef.ROLLING_BOULDER) {
                float bx = (o.x + (float) Math.sin(animationTime * 1.5f + o.phase) * o.range) * viewW;
                float radius = Math.max(17f, o.width * viewW * .48f);
                paint.setColor(Color.argb(70, 14, 15, 23));
                canvas.drawOval(bx - radius, floorY - 5f, bx + radius, floorY + 5f, paint);
                drawTexturedRock(canvas, bx, floorY - radius, radius,
                        animationTime * 42f + o.phase * 35f);
            } else if (o.type == CinematicRoomManager.ObstacleDef.FALLING_ROCK) {
                float cycle = (animationTime + o.phase) % 3.2f;
                float drop = cycle < .95f ? cycle / .95f : 1f;
                float rockY = -22f + drop * (floorY - 44f);
                float rx = x + (float) Math.sin(o.phase) * o.range * viewW;
                float radius = Math.max(15f, o.width * viewW * .45f);
                drawTexturedRock(canvas, rx, rockY, radius, animationTime * 115f + o.phase * 50f);
                if (cycle > .90f && cycle < 1.10f) {
                    paint.setColor(Color.argb(55, 220, 214, 201));
                    canvas.drawOval(rx - radius * 1.45f, floorY - 4f,
                            rx + radius * 1.45f, floorY + 5f, paint);
                }
            } else if (o.type == CinematicRoomManager.ObstacleDef.WATER_POOL) {
                float w = o.width * viewW;
                paint.setShader(new LinearGradient(x, floorY - 4f, x, floorY + 14f,
                        new int[]{0xFF67B9D0, 0xFF24506E, 0xFF122E4A}, null, Shader.TileMode.CLAMP));
                canvas.drawRect(x - w * .5f, floorY - 4f, x + w * .5f, floorY + 12f, paint);
                paint.setShader(null);
                paint.setColor(0xFFB5F0F4); paint.setStrokeWidth(2f);
                for (int k = 0; k < 5; k++) {
                    float waveX = x - w * .42f + ((animationTime * 38f + k * 31f) % Math.max(30f, w * .84f));
                    float waveY = floorY + 1f + (float) Math.sin(animationTime * 3f + k) * 2f;
                    canvas.drawLine(waveX, waveY, waveX + 15f, waveY, paint);
                }
            }
        }
    }

    private void drawTexturedRock(Canvas canvas, float cx, float cy, float radius, float rotation) {
        float[] shape = {1.00f,.83f,1.07f,.82f,1.02f,.87f,1.08f,.80f,.98f,.88f};
        Path rock = new Path();
        for (int i = 0; i < shape.length; i++) {
            double angle = Math.PI * 2.0 * i / shape.length + Math.toRadians(rotation);
            float rr = radius * shape[i];
            float px = cx + (float) Math.cos(angle) * rr;
            float py = cy + (float) Math.sin(angle) * rr;
            if (i == 0) rock.moveTo(px, py); else rock.lineTo(px, py);
        }
        rock.close();
        paint.setShader(new LinearGradient(cx - radius, cy - radius, cx + radius, cy + radius,
                new int[]{0xFFC9BBA8,0xFF897B6B,0xFF514A46,0xFF302D31},
                new float[]{0f,.38f,.76f,1f}, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(rock, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.2f, radius * .07f));
        paint.setColor(0xCC29272A);
        canvas.drawPath(rock, paint);
        paint.setStyle(Paint.Style.FILL);

        Path facet = new Path();
        facet.moveTo(cx - radius * .72f, cy - radius * .12f);
        facet.lineTo(cx - radius * .31f, cy - radius * .72f);
        facet.lineTo(cx + radius * .12f, cy - radius * .53f);
        facet.lineTo(cx - radius * .02f, cy - radius * .12f);
        facet.close();
        paint.setColor(0x668F877C);
        canvas.drawPath(facet, paint);
        paint.setColor(0x77817A72);
        canvas.drawLine(cx - radius * .45f, cy + radius * .25f,
                cx + radius * .40f, cy + radius * .58f, paint);
        paint.setColor(0x88706A63);
        canvas.drawCircle(cx + radius * .34f, cy - radius * .19f, radius * .11f, paint);
        canvas.drawCircle(cx - radius * .24f, cy + radius * .33f, radius * .08f, paint);
        paint.setColor(0x66E0D2BE);
        canvas.drawCircle(cx - radius * .30f, cy - radius * .42f, radius * .09f, paint);
    }

    private void drawWreckingBall(Canvas canvas, float anchorX, float anchorY,
                                  float ballX, float ballY, float radius, float rotationDegrees) {
        // Heavy chain with alternating iron links.
        paint.setColor(0xFF343C48);
        paint.setStrokeWidth(4.2f);
        canvas.drawLine(anchorX, anchorY, ballX, ballY, paint);
        float dx = ballX - anchorX, dy = ballY - anchorY;
        for (int i = 1; i <= 7; i++) {
            float t = i / 8f;
            float lx = anchorX + dx * t;
            float ly = anchorY + dy * t;
            paint.setColor(i % 2 == 0 ? 0xFFB5C0CA : 0xFF566271);
            canvas.drawCircle(lx, ly, 3.2f, paint);
        }

        canvas.save();
        canvas.rotate(rotationDegrees, ballX, ballY);
        float[] shape = {1.00f, .91f, 1.04f, .93f, 1.00f, .90f,
                1.05f, .92f, 1.00f, .91f, 1.04f, .92f};
        Path ironBall = new Path();
        for (int i = 0; i < shape.length; i++) {
            double angle = Math.PI * 2.0 * i / shape.length - Math.PI / 2.0;
            float rr = radius * shape[i];
            float px = ballX + (float) Math.cos(angle) * rr;
            float py = ballY + (float) Math.sin(angle) * rr;
            if (i == 0) ironBall.moveTo(px, py); else ironBall.lineTo(px, py);
        }
        ironBall.close();
        paint.setShader(new LinearGradient(ballX - radius, ballY - radius,
                ballX + radius * .9f, ballY + radius,
                new int[]{0xFFF0F3F5, 0xFF9AA7B3, 0xFF596776, 0xFF27323F},
                new float[]{0f, .34f, .72f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(ironBall, paint);
        paint.setShader(null);

        // Forged facets and dents give the ball a rough industrial surface.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.35f);
        paint.setColor(0x997F8B98);
        canvas.drawLine(ballX - radius * .66f, ballY - radius * .08f,
                ballX - radius * .18f, ballY - radius * .71f, paint);
        canvas.drawLine(ballX - radius * .18f, ballY - radius * .71f,
                ballX + radius * .57f, ballY - radius * .37f, paint);
        canvas.drawLine(ballX - radius * .53f, ballY + radius * .35f,
                ballX + radius * .28f, ballY + radius * .65f, paint);
        paint.setStrokeWidth(2.2f);
        paint.setColor(0xFF202A35);
        canvas.drawPath(ironBall, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF3D4A58);
        canvas.drawCircle(ballX - radius * .34f, ballY + radius * .17f, radius * .085f, paint);
        canvas.drawCircle(ballX + radius * .35f, ballY - radius * .29f, radius * .085f, paint);
        paint.setColor(0xFFDDE7ED);
        canvas.drawCircle(ballX - radius * .34f, ballY + radius * .17f, radius * .035f, paint);
        canvas.drawCircle(ballX + radius * .35f, ballY - radius * .29f, radius * .035f, paint);
        paint.setColor(0x66FFFFFF);
        canvas.drawArc(ballX - radius * .67f, ballY - radius * .76f,
                ballX + radius * .40f, ballY + radius * .33f, 202f, 94f, false, paint);
        canvas.restore();
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(1f);
    }

    private void updateObstacleInteractions() {
        float heroCenter = heroX + HERO_W * .5f;
        float heroFeet = heroY + HERO_H;
        boolean inWaterNow = false;

        for (int obstacleIndex = 0; obstacleIndex < room().obstacles.size(); obstacleIndex++) {
            CinematicRoomManager.ObstacleDef o = room().obstacles.get(obstacleIndex);
            if (o.type == CinematicRoomManager.ObstacleDef.SWING_BLADE) {
                float swing = (float) Math.sin(animationTime * 2.45f + o.phase) * .42f;
                float length = viewH * .37f;
                float ballX = o.x * viewW + (float) Math.sin(swing) * length;
                float ballY = viewH * .20f + (float) Math.cos(swing) * length;
                if (Math.abs(heroCenter - ballX) < 34f && heroY < ballY + 25f
                        && heroFeet > ballY - 25f) damageTeam(13f);
            } else if (o.type == CinematicRoomManager.ObstacleDef.FIRE_JET) {
                float cycle = (animationTime + o.phase) % 2.65f;
                boolean active = cycle < 1.15f;
                if (active && Math.abs(heroCenter - o.x * viewW)
                        < Math.max(24f, o.width * viewW * .42f)
                        && onGround && heroFeet >= floorY - 49f) damageTeam(11f);
            } else if (o.type == CinematicRoomManager.ObstacleDef.ROLLING_BOULDER) {
                float bx = (o.x + (float) Math.sin(animationTime * 1.5f + o.phase) * o.range) * viewW;
                float radius = Math.max(17f, o.width * viewW * .48f);
                if (Math.abs(heroCenter - bx) < radius + HERO_W * .30f
                        && heroFeet > floorY - radius * 1.9f) damageTeam(14f);
            } else if (o.type == CinematicRoomManager.ObstacleDef.FALLING_ROCK) {
                float absolute = animationTime + o.phase;
                int cycleNumber = (int) Math.floor(absolute / 3.2f);
                float cycle = absolute - cycleNumber * 3.2f;
                if (cycle < .34f && obstacleIndex < rockSoundCycleSeen.length
                        && rockSoundCycleSeen[obstacleIndex] != cycleNumber) {
                    rockSoundCycleSeen[obstacleIndex] = cycleNumber;
                    playRockFallSound();
                }
                if (cycle < .95f) {
                    float rockY = -22f + (cycle / .95f) * (floorY - 44f);
                    float rx = o.x * viewW + (float) Math.sin(o.phase) * o.range * viewW;
                    float radius = Math.max(15f, o.width * viewW * .45f);
                    if (Math.abs(heroCenter - rx) < radius + HERO_W * .30f
                            && heroY < rockY + radius && heroFeet > rockY - radius) damageTeam(14f);
                }
            } else if (o.type == CinematicRoomManager.ObstacleDef.WATER_POOL) {
                float half = o.width * viewW * .5f;
                boolean horizontalOverlap = heroCenter > o.x * viewW - half
                        && heroCenter < o.x * viewW + half;
                if (horizontalOverlap && onGround && heroFeet >= floorY - 15f) {
                    inWaterNow = true;
                    heroVx *= .992f;
                }
            }
        }

        if (inWaterNow) {
            // Water is a slow recovery zone: restore both companions little by little.
            annieHp = Math.min(ANNIE_MAX_HP, annieHp + 3.2f * (1f / 120f));
            berthuHp = Math.min(BERTHU_MAX_HP, berthuHp + 4.2f * (1f / 120f));
            berthuRegenDelay = 0f;
            if (!playerInWater) playWaterSplash();
        }
        updateWaterLoop(inWaterNow);
        playerInWater = inWaterNow;
    }


    private void drawPlayer(Canvas canvas) {
        if (annieFlash > 0f && ((int) (animationTime * 22f) % 2 == 0)) return;
        Bitmap frame = null;
        Bitmap[] animatedFrames = null;
        float phase = heroAnimationPhase;
        // Reuse Annie's existing punch/hit pose during the short fire-cast window.
        // This creates a clear forward arm action without drawing or adding another limb.
        if (playerState == STATE_CAST && annieAttack != null) frame = annieAttack;
        else if (playerState == STATE_CAST && annieFireCastPose != null) frame = annieFireCastPose;
        else if (playerState == STATE_CAST) animatedFrames = annieIdle;
        else if (playerState == STATE_ATTACK && annieAttack != null) frame = annieAttack;
        else if (playerState == STATE_HURT && annieHurt != null) frame = annieHurt;
        else if (playerState == STATE_JUMP || playerState == STATE_FALL || playerState == STATE_PULL_UP) animatedFrames = annieJump;
        else if (playerState == STATE_RUN) animatedFrames = annieRun;
        else if (playerState == STATE_WALK) animatedFrames = annieWalk;
        else animatedFrames = annieIdle;
        boolean blendAnimation = frame == null && animatedFrames != null;
        if (frame == null && animatedFrames != null) frame = choose(animatedFrames, (int) phase);

        float h = HERO_H;
        float w = HERO_W;
        if (frame != null && frame.getHeight() > 0) w = h * frame.getWidth() / (float) frame.getHeight();
        float squash = 0f;
        if (landingSquashTimer > 0f) {
            float t = landingSquashTimer / .17f;
            squash = (float) Math.sin((1f - t) * Math.PI) * .105f;
        }
        float stretch = 0f;
        if (takeoffStretchTimer > 0f) {
            float t = takeoffStretchTimer / .15f;
            stretch = (float) Math.sin(t * Math.PI) * .075f;
        }
        float runBob = (playerState == STATE_RUN || playerState == STATE_WALK)
                ? (float) Math.sin(heroAnimationPhase * Math.PI * .5f) * (playerState == STATE_RUN ? 1.8f : 1f) : 0f;
        float scaleX = 1f + squash * .55f - stretch * .20f;
        float scaleY = 1f - squash + stretch;
        float drawW = w * scaleX;
        float drawH = h * scaleY;
        float centerX = heroX + HERO_W * .5f;
        float top = heroY + runBob + (h - drawH) * .5f;
        RectF dst = new RectF(centerX - drawW * .5f, top, centerX + drawW * .5f, top + drawH);

        // A soft contact shadow grounds Annie; it lifts and narrows during jumps.
        float heightAboveFloor = Math.max(0f, floorY - (heroY + HERO_H));
        float shadowScale = Math.max(.35f, 1f - heightAboveFloor / 230f);
        float shadowSurfaceY = onGround ? heroY + HERO_H : floorY;
        paint.setColor(Color.argb((int) (66f * shadowScale), 12, 16, 22));
        canvas.drawOval(centerX - 24f * shadowScale, shadowSurfaceY - 3f,
                centerX + 24f * shadowScale, shadowSurfaceY + 4f, paint);

        if (blendAnimation && animatedFrames != null && choose(animatedFrames, (int) phase) != null) {
            drawBlendedFrames(canvas, animatedFrames, phase, dst, facingRight);
        } else if (frame != null) {
            drawFacingBitmap(canvas, frame, dst, facingRight);
        } else {
            drawAnnieFallback(canvas, dst);
        }
        drawInjuryMarks(canvas, dst, annieHp / ANNIE_MAX_HP, false);
        // Fireball casting reuses Annie's existing idle/attack animation; no extra drawn arm.
        if (playerState == STATE_HANG) {
            paint.setColor(0xFFFFD47D);
            paint.setTextSize(11f);
            canvas.drawText("ACTION: PULL UP", Math.max(8f, heroX - 14f), heroY - 12f, paint);
        }
    }

    private void drawBlendedFrames(Canvas canvas, Bitmap[] frames, float phase, RectF dst, boolean faceRight) {
        if (frames == null || frames.length == 0) return;
        int baseIndex = ((int) Math.floor(phase)) % frames.length;
        float blend = phase - (float) Math.floor(phase);
        Bitmap first = choose(frames, baseIndex);
        Bitmap second = choose(frames, (baseIndex + 1) % frames.length);
        if (first == null && second == null) return;
        if (first == null) { drawFacingBitmap(canvas, second, dst, faceRight); return; }
        if (second == null || first == second || blend < .04f) {
            drawFacingBitmap(canvas, first, dst, faceRight);
            return;
        }
        drawFacingBitmapAlpha(canvas, first, dst, faceRight, Math.round(255f * (1f - blend)));
        drawFacingBitmapAlpha(canvas, second, dst, faceRight, Math.round(255f * blend));
    }

    private void drawAnnieFallback(Canvas canvas, RectF dst) {
        float x = dst.left, y = dst.top;
        paint.setColor(0xFF28405C);
        canvas.drawRoundRect(x + 8f, y + 29f, x + 37f, y + 66f, 7f, 7f, paint);
        paint.setColor(0xFFE8B891);
        canvas.drawCircle(x + 22f, y + 18f, 13f, paint);
        paint.setColor(0xFF33212A);
        canvas.drawArc(x + 7f, y + 3f, x + 39f, y + 32f, 180f, 190f, true, paint);
        paint.setColor(0xFF263348);
        canvas.drawLine(x + 13f, y + 17f, x + 21f, y + 17f, paint);
        canvas.drawLine(x + 23f, y + 17f, x + 31f, y + 17f, paint);
        paint.setColor(0xFFFFD47D);
        canvas.drawLine(x + 20f, y + 31f, x + 24f, y + 31f, paint);
        paint.setColor(0xFF202D40);
        canvas.drawLine(x + 14f, y + 66f, x + 10f, y + 78f, paint);
        canvas.drawLine(x + 32f, y + 66f, x + 37f, y + 78f, paint);
    }

    private void drawCompanion(Canvas canvas) {
        boolean moving = Math.abs(berthuVx) > 12f || Math.abs(heroX - berthuX) > 24f;
        Bitmap frame = moving ? choose(berthuWalk, (int) berthuAnimationPhase) : berthuIdle;
        float h = BERTHU_H;
        float w = frame != null && frame.getHeight() > 0 ? h * frame.getWidth() / (float) frame.getHeight() : 57f;
        float bob = moving ? (float) Math.abs(Math.sin(berthuAnimationPhase * Math.PI * .5f)) * 1.8f
                : (float) Math.sin(animationTime * 2f) * 1.0f;
        RectF dst = new RectF(berthuX, berthuY + bob, berthuX + w, berthuY + h + bob);
        if (moving) drawBlendedFrames(canvas, berthuWalk, berthuAnimationPhase, dst, berthuFacingRight);
        else if (frame != null) drawFacingBitmap(canvas, frame, dst, berthuFacingRight);
        else if (berthuIdle == null) drawCatFallback(canvas, dst);
        drawInjuryMarks(canvas, dst, berthuHp / BERTHU_MAX_HP, true);
        if (berthuFlash > 0f) {
            paint.setColor(Color.argb(65, 255, 219, 116));
            canvas.drawCircle(berthuX + 23f, berthuY + 18f, 31f, paint);
        }
    }

    private void drawInjuryMarks(Canvas canvas, RectF d, float healthRatio, boolean cat) {
        float health = Math.max(0f, Math.min(1f, healthRatio));
        if (health >= .88f) return;
        int count = Math.min(6, 1 + (int) ((.88f - health) / .88f * 5f));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(cat ? 1.5f : 2f);
        paint.setColor(cat ? 0xB66B2025 : 0xB66F1B2B);
        float[][] marks = cat
                ? new float[][]{{.34f,.50f},{.52f,.60f},{.68f,.48f},{.43f,.72f},{.75f,.67f},{.58f,.36f}}
                : new float[][]{{.39f,.43f},{.58f,.48f},{.47f,.58f},{.63f,.62f},{.34f,.68f},{.70f,.38f}};
        for (int i = 0; i < count; i++) {
            float x = d.left + d.width() * marks[i][0];
            float y = d.top + d.height() * marks[i][1];
            canvas.drawLine(x - 2.4f, y - 1.8f, x + 2.8f, y + 1.9f, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(x + 4f, y + 2f, cat ? 1.6f : 2f, paint);
            paint.setStyle(Paint.Style.STROKE);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawCatFallback(Canvas canvas, RectF d) {
        paint.setColor(0xFFE39A4E);
        canvas.drawOval(d.left, d.top + 12f, d.right - 5f, d.bottom - 4f, paint);
        canvas.drawCircle(d.right - 10f, d.top + 13f, 12f, paint);
        Path ear = new Path();
        ear.moveTo(d.right - 19f, d.top + 8f); ear.lineTo(d.right - 21f, d.top - 1f);
        ear.lineTo(d.right - 12f, d.top + 6f); ear.close();
        canvas.drawPath(ear, paint);
    }

    /** Draws Annie's orange fireballs and the boss's blue fireballs. */
    private void drawFireballs(Canvas canvas) {
        for (Fireball shot : fireballs) {
            float pulse = 1f + .12f * (float) Math.sin(shot.animationPhase);
            float radius = shot.hostile ? 12f * pulse : 10.5f * pulse;
            int glow = shot.hostile ? Color.argb(72, 35, 132, 255) : Color.argb(62, 255, 82, 20);
            int outer = shot.hostile ? 0xFF247BFF : 0xFFFF5A16;
            int middle = shot.hostile ? 0xFF52C7FF : 0xFFFFB52D;
            int core = shot.hostile ? 0xFFE2F8FF : 0xFFFFF1A1;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(glow);
            canvas.drawCircle(shot.x, shot.y, radius * 2.05f, paint);
            Path flame = new Path();
            float tipX = shot.x + shot.direction * radius * 1.85f;
            float tailX = shot.x - shot.direction * radius * 1.15f;
            flame.moveTo(tipX, shot.y);
            flame.quadTo(shot.x + shot.direction * radius * .65f,
                    shot.y - radius * 1.05f, tailX, shot.y - radius * .56f);
            flame.quadTo(shot.x - shot.direction * radius * .15f,
                    shot.y, tailX, shot.y + radius * .56f);
            flame.quadTo(shot.x + shot.direction * radius * .65f,
                    shot.y + radius * 1.05f, tipX, shot.y);
            flame.close();
            paint.setColor(outer);
            canvas.drawPath(flame, paint);
            paint.setColor(middle);
            canvas.drawCircle(shot.x, shot.y, radius * .72f, paint);
            paint.setColor(core);
            canvas.drawCircle(shot.x + shot.direction * 1.5f, shot.y, radius * .34f, paint);
            paint.setColor(shot.hostile ? 0xAA78DFFF : 0xAAFF7D24);
            canvas.drawCircle(shot.x - shot.direction * 16f, shot.y - 3f, 2.2f, paint);
            canvas.drawCircle(shot.x - shot.direction * 22f, shot.y + 4f, 1.5f, paint);
        }
    }

    private void drawXingxing(Canvas canvas) {
        if (room().monkeyX < 0f) return;
        float x = room().monkeyX * viewW;
        boolean bossWon = !roomHasBossThisRoom || bossDefeatedThisRoom;
        boolean dancing = bossWon;
        float monkeyPhase = dancing ? animationTime * 7.2f : 0f;
        Bitmap frame = dancing ? choose(xingxingFrames, (int) monkeyPhase)
                : (xingxingFrames[0] != null ? xingxingFrames[0] : xingxingBase);
        float w = 82f;
        float h = frame != null && frame.getHeight() > 0
                ? w * frame.getHeight() / (float) frame.getWidth() : 88f;
        float bob = dancing ? (float) Math.abs(Math.sin(animationTime * 7f)) * 4f : 0f;
        if (mode == MODE_REUNION || mode == MODE_COMPLETE) bob *= 1.8f;
        RectF dst = new RectF(x - w * .5f, floorY - h - bob, x + w * .5f, floorY - bob);
        paint.setColor(Color.argb(38, 255, 198, 82));
        canvas.drawOval(x - 45f, floorY - 9f, x + 45f, floorY + 3f, paint);
        if (frame != null) {
            if (dancing) drawBlendedFrames(canvas, xingxingFrames, monkeyPhase, dst, true);
            else drawFacingBitmap(canvas, frame, dst, true);
        } else {
            drawMonkeyFallback(canvas, dst);
        }

        if (!bossWon) {
            // Quiet, sad expression while he waits for Annie to defeat the boss.
            paint.setColor(Color.argb(185, 105, 192, 231));
            canvas.drawCircle(x - 7f, dst.top + 34f, 2.1f, paint);
            canvas.drawCircle(x + 9f, dst.top + 39f, 1.8f, paint);
            paint.setColor(0xFF362638);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.8f);
            canvas.drawArc(x - 8f, dst.top + 43f, x + 8f, dst.top + 53f,
                    205f, 130f, false, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        if (!xingxingCageBroken) {
            drawXingxingPrison(canvas, x, dst.top, bossWon);
        } else if (xingxingCageBreakTimer > 0f) {
            drawBrokenPrisonPieces(canvas, x, dst.top);
        }

        paint.setColor(Color.argb(225, 25, 24, 39));
        canvas.drawRoundRect(x - 39f, dst.top - 22f, x + 39f, dst.top - 5f, 7f, 7f, paint);
        paint.setColor(0xFFFFD47D);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(10f);
        canvas.drawText("XINGXING", x, dst.top - 10f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawXingxingPrison(Canvas canvas, float centerX, float monkeyTop, boolean bossWon) {
        float left = centerX - 59f;
        float right = centerX + 59f;
        float top = Math.max(32f, monkeyTop - 7f);
        float bottom = floorY + 2f;
        paint.setColor(Color.argb(38, 0, 0, 0));
        canvas.drawRoundRect(left - 4f, top - 4f, right + 4f, bottom + 4f, 8f, 8f, paint);
        paint.setColor(0xFF8F9AA9);
        paint.setStrokeWidth(4f);
        canvas.drawLine(left, top, right, top, paint);
        canvas.drawLine(left, bottom, right, bottom, paint);
        for (int i = 0; i <= 6; i++) {
            float x = left + (right - left) * i / 6f;
            canvas.drawLine(x, top, x, bottom, paint);
        }
        paint.setStrokeWidth(2.5f);
        paint.setColor(bossWon ? 0xFFFFD47D : 0xFF677383);
        canvas.drawLine(left - 3f, top + 29f, right + 3f, top + 29f, paint);
        paint.setStrokeWidth(1f);
    }

    private void drawBrokenPrisonPieces(Canvas canvas, float centerX, float monkeyTop) {
        float progress = Math.max(0f, Math.min(1f, 1f - xingxingCageBreakTimer / .58f));
        float left = centerX - 59f - progress * 26f;
        float right = centerX + 59f + progress * 26f;
        float top = Math.max(32f, monkeyTop - 7f);
        paint.setColor(Color.argb((int) (210f * (1f - progress)), 143, 154, 169));
        paint.setStrokeWidth(3f);
        canvas.drawLine(left, top, left - 7f, floorY - progress * 20f, paint);
        canvas.drawLine(right, top, right + 7f, floorY - progress * 20f, paint);
        paint.setStrokeWidth(1f);
    }

    private void drawMonkeyFallback(Canvas canvas, RectF d) {
        paint.setColor(0xFF73604E);
        canvas.drawOval(d.left + 10f, d.top + 17f, d.right - 8f, d.bottom, paint);
        paint.setColor(0xFFD1B69B);
        canvas.drawOval(d.left + 26f, d.top + 31f, d.right - 24f, d.bottom - 9f, paint);
        paint.setColor(0xFF8F7964);
        canvas.drawCircle(d.centerX(), d.top + 17f, 20f, paint);
        paint.setColor(0xFFCAA98A);
        canvas.drawOval(d.centerX() - 11f, d.top + 12f, d.centerX() + 11f, d.top + 30f, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(d.centerX() - 7f, d.top + 15f, 3f, paint);
        canvas.drawCircle(d.centerX() + 7f, d.top + 15f, 3f, paint);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(d.centerX() - 7f, d.top + 15f, 1.3f, paint);
        canvas.drawCircle(d.centerX() + 7f, d.top + 15f, 1.3f, paint);
    }
    private void drawEnemy(Canvas canvas, Enemy enemy) {
        Bitmap[] frames = enemy.boss ? bossTigerFrames : tigerFrames;
        Bitmap frame;
        if (enemy.fleeing) frame = frames[1] != null ? frames[1] : frames[0];
        else if (enemy.backflipTimer > 0f) frame = frames[5] != null ? frames[5] : frames[1];
        else if (enemy.hitFlash > 0f) frame = frames[6] != null ? frames[6] : frames[0];
        else if (enemy.attackTimer > 0f) frame = enemy.attackPose == 1
                ? (frames[5] != null ? frames[5] : frames[4])
                : (frames[3] != null ? frames[3] : frames[0]);
        else if (enemy.moving) frame = frames[1] != null ? frames[1] : frames[0];
        else frame = frames[0];

        float h = enemy.boss ? 108f : 91f;
        float w = frame != null && frame.getHeight() > 0
                ? h * frame.getWidth() / (float) frame.getHeight() : 68f;
        float enemyBob = enemy.moving ? (float) Math.sin(animationTime * 11f + enemy.x * .02f) * 1.8f
                : (float) Math.sin(animationTime * 1.8f + enemy.x * .01f) * .7f;
        float flipProgress = enemy.backflipTimer > 0f
                ? 1f - enemy.backflipTimer / enemy.backflipDuration : 0f;
        float flipLift = enemy.backflipTimer > 0f
                ? (float) Math.sin(flipProgress * Math.PI) * 72f : 0f;
        RectF dst = new RectF(enemy.x + (enemy.width - w) * .5f,
                floorY - h + enemyBob - flipLift,
                enemy.x + (enemy.width + w) * .5f, floorY + enemyBob - flipLift);

        if (enemy.backflipTimer > 0f) {
            canvas.save();
            float rotation = 360f * flipProgress * (enemy.backflipDirection < 0f ? -1f : 1f);
            canvas.rotate(rotation, dst.centerX(), dst.centerY());
            drawFacingBitmap(canvas, frame, dst, enemy.facingRight);
            canvas.restore();
            paint.setColor(Color.argb(75, 255, 214, 142));
            canvas.drawOval(dst.centerX() - 25f, floorY - 5f, dst.centerX() + 25f, floorY + 3f, paint);
        } else {
            drawFacingBitmap(canvas, frame, dst, enemy.facingRight);
        }
        if (frame == null) {
            paint.setColor(enemy.boss ? 0xFFF1F1EC : 0xFF17191D);
            canvas.drawRoundRect(dst.left + 8f, dst.top + 22f, dst.right - 8f, dst.bottom, 15f, 15f, paint);
            paint.setColor(0xFFE0AA88);
            canvas.drawCircle(dst.centerX(), dst.top + 19f, 16f, paint);
        }
        float barW = enemy.boss ? 88f : 58f;
        paint.setColor(0xFF33222C);
        canvas.drawRoundRect(dst.centerX() - barW / 2f, dst.top - 10f,
                dst.centerX() + barW / 2f, dst.top - 4f, 3f, 3f, paint);
        paint.setColor(enemy.boss ? 0xFFE45C6C : 0xFFFFA75F);
        canvas.drawRoundRect(dst.centerX() - barW / 2f, dst.top - 10f,
                dst.centerX() - barW / 2f + barW * Math.max(0f, enemy.hp / enemy.maxHp),
                dst.top - 4f, 3f, 3f, paint);
        paint.setColor(0xFFFFD47D);
        paint.setTextSize(10f);
        paint.setTextAlign(Paint.Align.CENTER);
        if (enemy.boss) canvas.drawText(enemy.backflipTimer > 0f ? "JACKIE SHROFF · BACKFLIP" : "JACKIE SHROFF",
                dst.centerX(), dst.top - 17f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }


    private void drawFacingBitmap(Canvas canvas, Bitmap bitmap, RectF dst, boolean faceRight) {
        drawFacingBitmapAlpha(canvas, bitmap, dst, faceRight, 255);
    }

    private void drawFacingBitmapAlpha(Canvas canvas, Bitmap bitmap, RectF dst, boolean faceRight, int alpha) {
        if (bitmap == null || alpha <= 0) return;
        canvas.save();
        if (!faceRight) canvas.scale(-1f, 1f, dst.centerX(), dst.centerY());
        paint.setAlpha(Math.max(0, Math.min(255, alpha)));
        paint.setFilterBitmap(true);
        canvas.drawBitmap(bitmap, null, dst, paint);
        paint.setAlpha(255);
        canvas.restore();
    }

    private Bitmap choose(Bitmap[] frames, int index) {
        if (frames == null || frames.length == 0) return null;
        for (int i = 0; i < frames.length; i++) {
            Bitmap b = frames[(index + i) % frames.length];
            if (b != null && !b.isRecycled()) return b;
        }
        return null;
    }
    private void drawHud(Canvas canvas) {
        float left = 10f;
        float top = 8f;
        float scale = Math.min(1f, Math.max(.72f, (viewW - 98f) / 535f));

        // Measure the room title first so the pale banner ends close to the final text.
        String location = room().title == null ? "" : room().title.toUpperCase(java.util.Locale.US);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextSize(11.5f * scale);
        float titleAvailable = Math.max(42f * scale,
                Math.min(176f * scale, viewW - 98f - 304f * scale));
        while (location.length() > 3 && paint.measureText(location) > titleAvailable) {
            location = location.substring(0, location.length() - 2).trim() + "…";
        }
        float titleWidth = Math.min(titleAvailable, paint.measureText(location));

        String levelText = String.format(java.util.Locale.US, "%d/50", roomId + 1);
        paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        paint.setTextSize(11f * scale);
        float levelWidth = paint.measureText(levelText);

        // The health section retains its compact width; only the title/level tail varies.
        float healthSectionWidth = 254f * scale;
        float panelW = healthSectionWidth + 12f * scale + titleWidth
                + 10f * scale + levelWidth + 12f * scale;
        panelW = Math.min(panelW, Math.max(300f, viewW - 98f));
        panelW = Math.max(Math.min(320f, viewW - 98f), panelW);

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        // Short, very light banner with no outline; no unused long tail.
        paint.setColor(Color.argb(148, 246, 249, 253));
        canvas.drawRoundRect(left, top, left + panelW, top + 38f * scale,
                9f * scale, 9f * scale, paint);

        float baseline = top + 23f * scale;
        float x = left + 10f * scale;
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(8.5f * scale);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(0xFF111820);
        canvas.drawText("ANNIE", x, baseline, paint);
        float barH = 7f * scale;
        float barY = top + 15f * scale;
        float barA = 78f * scale;
        drawBar(canvas, x + 31f * scale, barY, barA, barH,
                annieHp / ANNIE_MAX_HP, healthColorAnnie(annieHp / ANNIE_MAX_HP));

        x += 121f * scale;
        paint.setColor(0xFF111820);
        canvas.drawText("BERTHU", x, baseline, paint);
        float barB = 77f * scale;
        drawBar(canvas, x + 43f * scale, barY, barB, barH,
                berthuHp / BERTHU_MAX_HP, healthColorBerthu(berthuHp / BERTHU_MAX_HP));

        float locationX = left + healthSectionWidth + 12f * scale;
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextSize(11.5f * scale);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(0xFF254A68); // deep blue-teal location title
        canvas.drawText(location, locationX, baseline, paint);

        float levelX = left + panelW - 9f * scale;
        paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        paint.setTextSize(11f * scale);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setColor(0xFFB65D22); // warm amber level indicator
        canvas.drawText(levelText, levelX, baseline, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        drawHamburger(canvas);
    }


    private int healthColorAnnie(float value) {
        float v = Math.max(0f, Math.min(1f, value));
        if (v >= .70f) return blendColor(0xFFE8D64E, 0xFF54C978, (v - .70f) / .30f);
        if (v >= .40f) return blendColor(0xFFF18D37, 0xFFE8D64E, (v - .40f) / .30f);
        return blendColor(0xFFE34349, 0xFFF18D37, v / .40f);
    }

    private int healthColorBerthu(float value) {
        float v = Math.max(0f, Math.min(1f, value));
        if (v >= .50f) return blendColor(0xFFF18D37, 0xFFE8D64E, (v - .50f) / .50f);
        return blendColor(0xFFE34349, 0xFFF18D37, v / .50f);
    }

    private int blendColor(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return Color.argb(255,
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
    }

    private void drawBar(Canvas canvas, float x, float y, float w, float h, float value, int color) {
        paint.setColor(0xFF3B3543);
        canvas.drawRoundRect(x, y, x + w, y + h, h, h, paint);
        float fill = w * Math.max(0f, Math.min(1f, value));
        paint.setColor(color);
        canvas.drawRoundRect(x, y, x + fill, y + h, h, h, paint);
        paint.setColor(Color.argb(60, 255, 255, 255));
        canvas.drawRoundRect(x + 1f, y + 1f, x + Math.max(1f, fill - 1f), y + h * .42f,
                h * .35f, h * .35f, paint);
    }

    private void drawHamburger(Canvas canvas) {
        float cx = viewW - 37f;
        float cy = 39f;
        paint.setColor(Color.argb(220, 12, 16, 28));
        canvas.drawRoundRect(cx - 25f, cy - 25f, cx + 25f, cy + 25f, 12f, 12f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(3f);
        paint.setColor(Color.WHITE);
        for (int i = -1; i <= 1; i++) canvas.drawLine(cx - 9f, cy + i * 7f, cx + 9f, cy + i * 7f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(1f);
    }

    private void drawPauseMenu(Canvas canvas) {
        paint.setColor(Color.argb(188, 5, 8, 17));
        canvas.drawRect(0f, 0f, viewW, viewH, paint);
        float[] layout = new float[4];
        pauseMenuLayout(layout);
        float left = layout[0], top = layout[1], w = layout[2], h = layout[3];
        paint.setColor(Color.argb(245, 25, 29, 46));
        canvas.drawRoundRect(left, top, left + w, top + h, 18f, 18f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(0xFFB9A6CE);
        canvas.drawRoundRect(left, top, left + w, top + h, 18f, 18f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(22f);
        paint.setColor(0xFFFFD47D);
        canvas.drawText("PAUSED", viewW * .5f, top + 37f, paint);
        drawMenuButton(canvas, left + 22f, top + 51f, w - 44f, 38f,
                "RESUME", 0xFF3C765D);
        drawMenuButton(canvas, left + 22f, top + 97f, w - 44f, 38f,
                "SOUND EFFECTS: " + (sfxEnabled ? "ON" : "OFF"), sfxEnabled ? 0xFF465C7C : 0xFF654C52);
        drawMenuButton(canvas, left + 22f, top + 143f, w - 44f, 38f,
                "START NEW GAME", 0xFF74613F);
        drawMenuButton(canvas, left + 22f, top + 189f, w - 44f, 38f,
                "EXIT TO DASHBOARD", 0xFF79434B);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawMenuButton(Canvas canvas, float x, float y, float w, float h,
                                String label, int color) {
        paint.setColor(color);
        canvas.drawRoundRect(x, y, x + w, y + h, 9f, 9f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(Color.argb(150, 255, 255, 255));
        canvas.drawRoundRect(x, y, x + w, y + h, 9f, 9f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(12f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        canvas.drawText(label, x + w * .5f, y + h * .5f + 4f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }
    private float joystickCenterX() { return 100f; }
    private float joystickCenterY() { return viewH - 82f; }

    // Four compact action controls, all on one baseline. Slots are ordered left-to-right:
    // 0 Run/Walk, 1 Fireball, 2 Hit, 3 Jump.
    private float actionButtonY() { return viewH - 55f; }
    private float actionButtonX(int slot) { return viewW - 34f - (3 - slot) * 51f; }

    private int actionButtonAt(float x, float y) {
        if (Math.abs(y - actionButtonY()) > ACTION_BUTTON_HIT_RADIUS + 5f) return -1;
        for (int slot = 0; slot < 4; slot++) {
            if (isInside(x, y, actionButtonX(slot), actionButtonY(), ACTION_BUTTON_HIT_RADIUS)) {
                return slot;
            }
        }
        return -1;
    }

    private void drawControls(Canvas canvas) {
        drawJoystick(canvas);
        float cy = actionButtonY();
        drawActionButton(canvas, actionButtonX(0), cy, 2, runMode);    // Run
        drawActionButton(canvas, actionButtonX(1), cy, 3, actionHeld); // Fireball
        drawActionButton(canvas, actionButtonX(2), cy, 1, attackHeld); // Hit
        drawActionButton(canvas, actionButtonX(3), cy, 0, jumpHeld);   // Jump
    }

    private void drawJoystick(Canvas canvas) {
        float cx = joystickCenterX();
        float cy = joystickCenterY();
        float radius = 53f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(54, 235, 242, 250));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.6f);
        paint.setColor(Color.argb(112, 244, 247, 252));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        float dx = (rightHeld ? 1f : 0f) - (leftHeld ? 1f : 0f);
        float dy = (downHeld ? 1f : 0f) - (upHeld ? 1f : 0f);
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > 1f) { dx /= len; dy /= len; }
        float knobX = cx + dx * 25f;
        float knobY = cy + dy * 25f;
        paint.setColor(Color.argb(110, 237, 243, 250));
        canvas.drawCircle(knobX, knobY, 20f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.1f);
        paint.setColor(Color.argb(118, 255, 255, 255));
        canvas.drawCircle(knobX, knobY, 20f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawActionButton(Canvas canvas, float cx, float cy, int icon, boolean pressed) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? Color.argb(112, 255, 219, 153)
                : Color.argb(54, 232, 239, 248));
        canvas.drawCircle(cx, cy, ACTION_BUTTON_RADIUS, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        paint.setColor(pressed ? Color.argb(190, 255, 248, 222)
                : Color.argb(118, 246, 249, 255));
        canvas.drawCircle(cx, cy, ACTION_BUTTON_RADIUS, paint);
        paint.setStyle(Paint.Style.FILL);
        switch (icon) {
            case 0: drawJumpPersonIcon(canvas, cx, cy); break;
            case 1: drawBoxingGloveIcon(canvas, cx, cy); break;
            case 2: drawRunnerIcon(canvas, cx, cy); break;
            default: drawLightningFireIcon(canvas, cx, cy); break;
        }
    }

    /** A small airborne figure with bent legs and air streaks below the feet. */
    private void drawJumpPersonIcon(Canvas canvas, float cx, float cy) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(2.8f);
        paint.setColor(0xFFF4F8FF);
        canvas.drawLine(cx - 9f, cy + 15f, cx - 3f, cy + 15f, paint);
        canvas.drawLine(cx + 5f, cy + 15f, cx + 12f, cy + 15f, paint);
        paint.setColor(0xFFBFEAFF);
        canvas.drawLine(cx - 15f, cy + 9f, cx - 11f, cy + 5f, paint);
        canvas.drawLine(cx + 12f, cy + 8f, cx + 16f, cy + 3f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFF8FBFF);
        canvas.drawCircle(cx + 2f, cy - 12f, 3.5f, paint);
        Path torso = new Path();
        torso.moveTo(cx - 1f, cy - 8f);
        torso.quadTo(cx + 7f, cy - 5f, cx + 5f, cy + 2f);
        torso.lineTo(cx - 1f, cy + 5f);
        torso.quadTo(cx - 6f, cy - 1f, cx - 1f, cy - 8f);
        torso.close();
        canvas.drawPath(torso, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        canvas.drawLine(cx + 1f, cy - 5f, cx + 11f, cy - 13f, paint); // raised arm
        canvas.drawLine(cx - 1f, cy - 3f, cx - 10f, cy - 9f, paint); // trailing arm
        canvas.drawLine(cx + 1f, cy + 3f, cx + 11f, cy + 9f, paint); // tucked leg
        canvas.drawLine(cx, cy + 3f, cx - 9f, cy + 10f, paint); // bent leg
        paint.setStyle(Paint.Style.FILL);
    }

    /** Red rounded boxing glove with a clearly visible black wrist wrap. */
    private void drawBoxingGloveIcon(Canvas canvas, float cx, float cy) {
        canvas.save();
        canvas.rotate(-18f, cx, cy);
        paint.setColor(0xFF15171D);
        canvas.drawRoundRect(cx - 6f, cy + 5f, cx + 8f, cy + 17f, 3f, 3f, paint);
        paint.setColor(0xFF4A4D55);
        canvas.drawLine(cx - 4f, cy + 8f, cx + 6f, cy + 8f, paint);
        canvas.drawLine(cx - 4f, cy + 12f, cx + 6f, cy + 12f, paint);
        Path glove = new Path();
        glove.moveTo(cx - 8f, cy + 7f);
        glove.cubicTo(cx - 13f, cy + 4f, cx - 14f, cy - 3f, cx - 10f, cy - 8f);
        glove.cubicTo(cx - 8f, cy - 11f, cx - 5f, cy - 13f, cx - 2f, cy - 12f);
        glove.cubicTo(cx + 2f, cy - 17f, cx + 8f, cy - 14f, cx + 9f, cy - 9f);
        glove.cubicTo(cx + 15f, cy - 8f, cx + 16f, cy - 2f, cx + 12f, cy + 3f);
        glove.cubicTo(cx + 9f, cy + 8f, cx + 1f, cy + 10f, cx - 8f, cy + 7f);
        glove.close();
        paint.setColor(0xFFD62832);
        canvas.drawPath(glove, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.3f);
        paint.setColor(0xFF76121A);
        canvas.drawPath(glove, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFF7375);
        canvas.drawArc(cx - 9f, cy - 12f, cx + 8f, cy + 1f, 194f, 112f, false, paint);
        canvas.restore();
        paint.setStyle(Paint.Style.FILL);
    }

    /** Bold side-running silhouette with speed trails; a clean action-game mark. */
    private void drawRunnerIcon(Canvas canvas, float cx, float cy) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(2.2f);
        paint.setColor(0xFFB8E7FF);
        canvas.drawLine(cx - 17f, cy - 6f, cx - 10f, cy - 6f, paint);
        canvas.drawLine(cx - 18f, cy, cx - 12f, cy, paint);
        canvas.drawLine(cx - 14f, cy + 6f, cx - 9f, cy + 6f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFF7FAFF);
        canvas.drawCircle(cx + 5f, cy - 13f, 3.4f, paint);
        Path torso = new Path();
        torso.moveTo(cx + 1f, cy - 8f);
        torso.lineTo(cx + 10f, cy - 5f);
        torso.lineTo(cx + 5f, cy + 2f);
        torso.lineTo(cx - 3f, cy + 1f);
        torso.close();
        canvas.drawPath(torso, paint);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(4f);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(cx + 2f, cy - 6f, cx - 7f, cy - 12f, paint); // rear arm
        canvas.drawLine(cx + 7f, cy - 5f, cx + 15f, cy - 1f, paint); // forward arm
        canvas.drawLine(cx + 1f, cy + 1f, cx + 12f, cy + 11f, paint); // rear stride
        canvas.drawLine(cx + 1f, cy + 1f, cx - 11f, cy + 8f, paint); // forward stride
        paint.setStyle(Paint.Style.FILL);
    }

    /** Flash-yellow lightning bolt surrounded by orange/red flame tongues. */
    private void drawLightningFireIcon(Canvas canvas, float cx, float cy) {
        Path flames = new Path();
        flames.moveTo(cx - 2f, cy + 16f);
        flames.cubicTo(cx - 16f, cy + 12f, cx - 15f, cy + 2f, cx - 8f, cy - 3f);
        flames.cubicTo(cx - 10f, cy - 10f, cx - 4f, cy - 17f, cx - 2f, cy - 18f);
        flames.cubicTo(cx - 2f, cy - 9f, cx + 2f, cy - 8f, cx + 3f, cy - 2f);
        flames.cubicTo(cx + 7f, cy - 13f, cx + 14f, cy - 11f, cx + 14f, cy - 3f);
        flames.cubicTo(cx + 20f, cy + 6f, cx + 11f, cy + 16f, cx - 2f, cy + 16f);
        flames.close();
        paint.setColor(0xFFE64B24);
        canvas.drawPath(flames, paint);
        paint.setColor(0xFFFF9D24);
        canvas.drawOval(cx - 8f, cy - 2f, cx + 10f, cy + 14f, paint);
        Path bolt = new Path();
        bolt.moveTo(cx + 3f, cy - 15f);
        bolt.lineTo(cx - 9f, cy + 1f);
        bolt.lineTo(cx - 1f, cy + 1f);
        bolt.lineTo(cx - 5f, cy + 15f);
        bolt.lineTo(cx + 10f, cy - 4f);
        bolt.lineTo(cx + 2f, cy - 4f);
        bolt.close();
        paint.setColor(0xFF7A3D12);
        canvas.drawPath(bolt, paint);
        canvas.save();
        canvas.translate(-1f, -1f);
        paint.setColor(0xFFFFE326);
        canvas.drawPath(bolt, paint);
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(cx - 3f, cy - 1f, 1.4f, paint);
        canvas.restore();
    }

    private void drawControl(Canvas canvas, float cx, float cy, String label, boolean pressed) {
        paint.setColor(pressed ? Color.argb(220, 255, 204, 116)
                : Color.argb(126, 18, 22, 36));
        canvas.drawCircle(cx, cy, 29f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(pressed ? 0xFFFFF0C4 : Color.argb(180, 235, 231, 242));
        canvas.drawCircle(cx, cy, 29f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(label.length() > 3 ? 9f : 13f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(pressed ? 0xFF30283B : Color.WHITE);
        canvas.drawText(label, cx, cy + 4f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }
    private void drawOverlay(Canvas canvas, String title, String subtitle, String footer) {
        paint.setColor(Color.argb(190, 13, 15, 29));
        canvas.drawRect(0f, 0f, viewW, viewH, paint);
        float x = viewW * .18f, y = viewH * .29f, w = viewW * .64f, h = viewH * .43f;
        paint.setColor(Color.argb(235, 37, 34, 54));
        canvas.drawRoundRect(x, y, x + w, y + h, 20f, 20f, paint);
        paint.setColor(0xFFFFD47D);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextSize(25f);
        canvas.drawText(title, viewW * .5f, y + 54f, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(15f);
        canvas.drawText(subtitle, viewW * .5f, y + 91f, paint);
        paint.setColor(0xFFD6C8E2);
        paint.setTextSize(12f);
        canvas.drawText(footer, viewW * .5f, y + 121f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void releaseControls() {
        leftHeld = rightHeld = upHeld = downHeld = false;
        jumpHeld = attackHeld = actionHeld = false;
        runButtonTouch = pauseButtonTouch = sfxButtonTouch = exitButtonTouch = false;
        pendingJump = pendingAttack = pendingAction = false;
        jumpWasHeld = attackWasHeld = actionWasHeld = upWasHeld = false;
        runButtonWasHeld = pauseWasHeld = sfxWasHeld = false;
    }

    private boolean isInside(float x, float y, float cx, float cy, float r) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (mode == MODE_REUNION) return true;

        if (mode == MODE_PAUSED) {
            if (pauseOpeningTap) {
                // Ignore the release belonging to the hamburger tap itself.
                if (action == MotionEvent.ACTION_UP) pauseOpeningTap = false;
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                float tx = event.getX(event.getActionIndex()) / density;
                float ty = event.getY(event.getActionIndex()) / density;
                float[] layout = new float[4];
                pauseMenuLayout(layout);
                float left = layout[0], top = layout[1], w = layout[2];
                float buttonLeft = left + 22f;
                float buttonRight = left + w - 22f;
                if (tx >= buttonLeft && tx <= buttonRight
                        && ty >= top + 51f && ty <= top + 89f) {
                    mode = MODE_PLAYING;
                } else if (tx >= buttonLeft && tx <= buttonRight
                        && ty >= top + 97f && ty <= top + 135f) {
                    setSfxEnabled(!sfxEnabled);
                } else if (tx >= buttonLeft && tx <= buttonRight
                        && ty >= top + 143f && ty <= top + 181f) {
                    startNewGame();
                } else if (tx >= buttonLeft && tx <= buttonRight
                        && ty >= top + 189f && ty <= top + 227f) {
                    saveCampaignProgress();
                    if (returnToDashboard != null) returnToDashboard.run();
                }
                invalidate();
            }
            return true;
        }

        if (mode == MODE_GAME_OVER || mode == MODE_COMPLETE) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                if (mode == MODE_GAME_OVER) {
                    annieHp = ANNIE_MAX_HP;
                    berthuHp = BERTHU_MAX_HP;
                    loadRoom(roomId, false, CinematicRoomManager.LEFT);
                    mode = MODE_PLAYING;
                } else {
                    annieHp = ANNIE_MAX_HP;
                    berthuHp = BERTHU_MAX_HP;
                    defeatedEnemies = 0;
                    int nextStage = roomId + 1;
                    if (nextStage >= CinematicRoomManager.ROOM_COUNT) nextStage = 0;
                    loadRoom(nextStage, false, CinematicRoomManager.LEFT);
                    mode = MODE_PLAYING;
                }
                invalidate();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            releaseControls();
            return true;
        }

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int index = event.getActionIndex();
            float tx = event.getX(index) / density;
            float ty = event.getY(index) / density;
            if (handleBerthuDoubleTap(tx, ty, event.getEventTime())) {
                invalidate();
                return true;
            }
            if (isInside(tx, ty, viewW - 37f, 39f, 29f)) {
                mode = MODE_PAUSED;
                pauseOpeningTap = true;
                releaseControls();
                invalidate();
                return true;
            }
            // Four action buttons share one horizontal row: Run, Fire, Hit, Jump.
            int slot = actionButtonAt(tx, ty);
            if (slot == 0) {
                runMode = !runMode;
                runButtonTouch = true;
            } else if (slot == 1) pendingAction = true;
            else if (slot == 2) pendingAttack = true;
            else if (slot == 3) pendingJump = true;
        }

        boolean l = false, r = false, u = false, d = false;
        boolean j = false, a = false, act = false, run = false;
        int count = event.getPointerCount();
        for (int i = 0; i < count; i++) {
            if ((action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP)
                    && i == event.getActionIndex()) continue;
            float x = event.getX(i) / density;
            float y = event.getY(i) / density;

            // Analogue-style left joystick: one translucent pad replaces four directional buttons.
            if (x < 190f && y > viewH - 170f) {
                float dx = x - joystickCenterX();
                float dy = y - joystickCenterY();
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (distance <= 82f && distance > 9f) {
                    l |= dx < -10f && Math.abs(dx) > Math.abs(dy) * .52f;
                    r |= dx > 10f && Math.abs(dx) > Math.abs(dy) * .52f;
                    u |= dy < -10f && Math.abs(dy) > Math.abs(dx) * .52f;
                    d |= dy > 10f && Math.abs(dy) > Math.abs(dx) * .52f;
                }
            }
            run |= isInside(x, y, actionButtonX(0), actionButtonY(), ACTION_BUTTON_HIT_RADIUS);
            act |= isInside(x, y, actionButtonX(1), actionButtonY(), ACTION_BUTTON_HIT_RADIUS);
            a |= isInside(x, y, actionButtonX(2), actionButtonY(), ACTION_BUTTON_HIT_RADIUS);
            j |= isInside(x, y, actionButtonX(3), actionButtonY(), ACTION_BUTTON_HIT_RADIUS);
        }
        leftHeld = l; rightHeld = r; upHeld = u; downHeld = d;
        jumpHeld = j; attackHeld = a; actionHeld = act;
        runButtonTouch = run;
        sfxButtonTouch = false;
        pauseButtonTouch = false;
        exitButtonTouch = false;
        invalidate();
        return true;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) saveCampaignProgress();
    }

    @Override
    protected void onDetachedFromWindow() {
        saveCampaignProgress();
        super.onDetachedFromWindow();
        stopWaterLoop();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        for (MediaPlayer player : new ArrayList<>(fallbackPlayers)) {
            try { player.release(); } catch (RuntimeException ignored) { }
        }
        fallbackPlayers.clear();
    }

    private static final class Fireball {
        float x;
        float y;
        final float vx;
        final float vy;
        final float direction;
        final boolean hostile;
        final float damage;
        float life = 1.35f;
        float animationPhase;

        Fireball(float x, float y, float vx) {
            this(x, y, vx, 0f, false, 0f);
        }

        Fireball(float x, float y, float vx, float vy, boolean hostile, float damage) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.direction = vx >= 0f ? 1f : -1f;
            this.hostile = hostile;
            this.damage = damage;
        }
    }

    private static final class Enemy {
        float x, hp, maxHp, patrolMin, patrolMax, attackCooldown, attackTimer, hitFlash, vx;
        float rangedCooldown;
        float width = 58f;
        float direction = 1f;
        float backflipTimer, backflipDuration = .82f, backflipCooldown = 2f;
        float backflipStartX, backflipDirection = -1f;
        boolean boss, facingRight = false, moving;
        boolean fleeing;
        float fleeDirection;
        int attackPose, attackCount;
        Enemy(float x, float hp, boolean boss, float patrolRadius) {
            this.x = x; this.hp = this.maxHp = hp; this.boss = boss;
            this.patrolMin = x - patrolRadius; this.patrolMax = x + patrolRadius;
            this.attackCooldown = .4f;
            this.rangedCooldown = boss ? 2.5f : 9999f;
            this.width = boss ? 76f : 59f;
            this.backflipCooldown = boss ? 2.1f : 9999f;
        }
    }
}
