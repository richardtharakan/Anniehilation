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
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * First cinematic room-based Anniehilation prototype.
 * Four connected rooms, room transitions, momentum jumps, ledge hanging,
 * pull-up, ladder climbing, pressure plate/gate, enemy combat, companion
 * following, Xingxing finish objective, and the existing Tiger SFX.
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

    private static final int MODE_PLAYING = 0;
    private static final int MODE_PAUSED = 1;
    private static final int MODE_GAME_OVER = 2;
    private static final int MODE_COMPLETE = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final CinematicRoomManager roomManager = new CinematicRoomManager();
    private final Runnable returnToDashboard;
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<MediaPlayer> fallbackPlayers = new ArrayList<>();

    private final Bitmap[] annieIdle = new Bitmap[4];
    private final Bitmap[] annieWalk = new Bitmap[4];
    private final Bitmap[] annieRun = new Bitmap[4];
    private final Bitmap[] annieJump = new Bitmap[3];
    private final Bitmap[] berthuWalk = new Bitmap[8];
    private final Bitmap[] xingxingFrames = new Bitmap[8];
    private final Bitmap[] tigerFrames = new Bitmap[8];
    private Bitmap berthuIdle;
    private Bitmap annieAttack;
    private Bitmap annieHurt;
    private Bitmap xingxingBase;

    private SoundPool soundPool;
    private int fartPunchId;
    private int fartKickId;
    private int xingxingLaughId;
    private boolean fartPunchReady;
    private boolean fartKickReady;
    private boolean xingxingLaughReady;
    private boolean sfxLoadFailed;
    private boolean reunionLaughPlayed;

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
    private boolean runMode = false;
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
        xingxingBase = bitmap("xingxing");
        if (xingxingFrames[0] == null) xingxingFrames[0] = xingxingBase;
    }

    private void loadTigerAssets() {
        String[] names = {"tiger_hero_guard", "tiger_hero_run", "tiger_hero_block",
                "tiger_hero_punch", "tiger_hero_kick", "tiger_hero_spin_kick",
                "tiger_hero_hit", "tiger_hero_down"};
        for (int i = 0; i < names.length; i++) tigerFrames[i] = bitmap(names[i]);
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
            soundPool = new SoundPool.Builder().setAudioAttributes(attributes).setMaxStreams(6).build();
            soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
                if (sampleId == fartPunchId) fartPunchReady = status == 0;
                if (sampleId == fartKickId) fartKickReady = status == 0;
                if (sampleId == xingxingLaughId) xingxingLaughReady = status == 0;
                if (status != 0) sfxLoadFailed = true;
            });
            int punch = getResources().getIdentifier("tiger_fart_punch", "raw", getContext().getPackageName());
            int kick = getResources().getIdentifier("tiger_fart_kick", "raw", getContext().getPackageName());
            int laugh = getResources().getIdentifier("xingxing_laugh", "raw", getContext().getPackageName());
            if (punch != 0) fartPunchId = soundPool.load(getContext(), punch, 1);
            if (kick != 0) fartKickId = soundPool.load(getContext(), kick, 1);
            if (laugh != 0) xingxingLaughId = soundPool.load(getContext(), laugh, 1);
            if (punch == 0 || kick == 0 || laugh == 0) sfxLoadFailed = true;
        } catch (RuntimeException e) {
            sfxLoadFailed = true;
            if (soundPool != null) {
                soundPool.release();
                soundPool = null;
            }
        }
    }

    private void playAttackSound(boolean kick) {
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

    private void playXingxingLaugh() {
        int resource = getResources().getIdentifier("xingxing_laugh", "raw", getContext().getPackageName());
        if (soundPool != null && xingxingLaughId != 0 && xingxingLaughReady) {
            int stream = soundPool.play(xingxingLaughId, 1f, 1f, 3, 0, 1f);
            if (stream != 0) return;
        }
        if (resource != 0) playFallbackSound(resource);
    }

    private void playFallbackSound(int resourceId) {
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

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewW = w / density;
        viewH = h / density;
        floorY = viewH * .785f;
        loadRoom(CinematicRoomManager.SCHOOL_GATE, false, CinematicRoomManager.LEFT);
    }

    private CinematicRoomManager.RoomDef room() { return roomManager.getRoom(roomId); }

    private void loadRoom(int id, boolean fromTransition, int entryDirection) {
        roomId = id;
        CinematicRoomManager.RoomDef def = room();
        ladderX = def.ladderX < 0 ? -1f : def.ladderX * viewW;
        ladderTopY = def.ladderTop < 0 ? -1f : def.ladderTop * viewH;
        enemies.clear();
        for (CinematicRoomManager.EnemyDef spawn : def.enemies) {
            float x = spawn.x * viewW;
            enemies.add(new Enemy(x, spawn.hp, spawn.boss,
                    Math.max(26f, spawn.patrolRadius * viewW)));
        }
        gateOpen = def.gateX < 0f;
        roomWasVisited = false;
        reunionLaughPlayed = false;
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
        animationTime += frameDt;

        // Fixed-step simulation keeps movement and collisions consistent on 30/60/90/120 Hz screens.
        if (mode == MODE_PLAYING) {
            physicsAccumulator = Math.min(physicsAccumulator + frameDt, FIXED_STEP * 8f);
            int steps = 0;
            while (physicsAccumulator >= FIXED_STEP && steps < 8) {
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
        drawXingxing(canvas);
        if (transitionAlpha > 0f) {
            paint.setColor(Color.argb((int) (transitionAlpha * 95f), 10, 12, 22));
            canvas.drawRect(0f, 0f, viewW, viewH, paint);
        }
        canvas.restore();

        drawHud(canvas);
        drawControls(canvas);
        if (mode == MODE_PAUSED) drawOverlay(canvas, "PAUSED", "Tap anywhere to continue", "Your progress in this prototype is retained");
        if (mode == MODE_GAME_OVER) drawOverlay(canvas, "ANNIE NEEDS A REST", "Tap anywhere to retry this room", "Berthu is catching his breath");
        if (mode == MODE_COMPLETE) {
            boolean finalStage = roomId >= CinematicRoomManager.ROOM_COUNT - 1;
            drawOverlay(canvas, finalStage ? "ALL 50 LEVELS COMPLETE!" : "YOU FOUND XINGXING!",
                    finalStage ? "The whole adventure is complete" : "LEVEL " + (roomId + 1) + " / 50 COMPLETE",
                    finalStage ? "Tap anywhere to replay from Level 1" : "Xingxing is laughing! Tap anywhere for the next level");
        }
        canvas.restore();
        postInvalidateOnAnimation();
    }

    private void updateWorld(float dt) {
        if (attackCooldown > 0f) attackCooldown = Math.max(0f, attackCooldown - dt);
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

        handlePressedEdges();
        updatePlayer(dt);
        updateObstacleInteractions();
        updateEnemies(dt);
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
        if (pendingAction || (actionHeld && !actionWasHeld)) {
            if (playerState == STATE_HANG) pullUpFromLedge();
            else if (room().gateX >= 0f && !gateOpen) messageTimer = 1.2f;
        }
        pendingJump = pendingAttack = pendingAction = false;
        jumpWasHeld = jumpHeld;
        attackWasHeld = attackHeld;
        actionWasHeld = actionHeld;
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
        float speed = runMode ? RUN_SPEED : WALK_SPEED;
        float targetVx = direction * speed;
        float acceleration;
        if (direction == 0f) {
            acceleration = onGround ? GROUND_BRAKE : AIR_ACCEL * .58f;
        } else if (heroVx != 0f && Math.signum(heroVx) != Math.signum(targetVx)) {
            acceleration = onGround ? GROUND_ACCEL * 1.8f : AIR_ACCEL;
        } else {
            acceleration = onGround ? GROUND_ACCEL : AIR_ACCEL;
        }
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

        if (attackTimer > 0f) playerState = STATE_ATTACK;
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
            if (distance < (enemy.boss ? 198f : 176f) && enemy.attackCooldown <= 0f) {
                enemy.moving = false;
                enemy.vx = 0f;
                enemy.attackTimer = enemy.boss ? .68f : .53f;
                enemy.attackPose = enemy.attackCount % 2;
                enemy.attackCount++;
                enemy.attackCooldown = enemy.boss ? 1.48f : 1.16f;
                playAttackSound(enemy.attackPose == 1 || enemy.boss);
                float verticalDifference = Math.abs((heroY + HERO_H) - floorY);
                if (distance < (enemy.boss ? 86f : 74f) && verticalDifference < 31f && onGround) {
                    damageTeam(enemy.boss ? 19f : 12f);
                }
            }
            if (enemy.hp <= 0f) {
                defeatedEnemies++;
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

    private void damageTeam(float amount) {
        if (hurtCooldown > 0f || mode != MODE_PLAYING) return;
        hurtCooldown = .76f;
        berthuRegenDelay = 5f;
        if (berthuHp > 0f) {
            berthuHp = Math.max(0f, berthuHp - amount * BERTHU_DAMAGE_SHARE);
            annieHp = Math.max(0f, annieHp - amount * (1f - BERTHU_DAMAGE_SHARE));
            berthuFlash = .32f;
        } else {
            annieHp = Math.max(0f, annieHp - amount);
        }
        annieFlash = .24f;
        if (annieHp <= 0f) mode = MODE_GAME_OVER;
    }

    private void updateBerthu(float dt) {
        float target = Math.max(8f, heroX - (facingRight ? 60f : -60f));
        float difference = target - berthuX;
        // Spring-damped following avoids the old robotic constant-speed chase.
        float springAcceleration = difference * 22f - berthuVx * 9.5f;
        berthuVx += springAcceleration * dt;
        berthuVx = Math.max(-245f, Math.min(245f, berthuVx));
        berthuX += berthuVx * dt;
        if (Math.abs(difference) < 1.5f && Math.abs(berthuVx) < 5f) {
            berthuX = target;
            berthuVx = 0f;
        }
        float targetY = onGround ? floorY - BERTHU_H : Math.min(floorY - BERTHU_H, heroY + HERO_H - BERTHU_H);
        berthuY += (targetY - berthuY) * (1f - (float) Math.exp(-12f * dt));
        float catSpeed = Math.min(1f, Math.abs(berthuVx) / 190f);
        berthuAnimationPhase += dt * (2f + catSpeed * 10f);
    }

    private void checkRoomTransitions() {
        if (playerState == STATE_HANG || playerState == STATE_CLIMB) return;
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
        if (mode != MODE_PLAYING || room().monkeyX < 0f || reunionLaughPlayed) return;
        float goalX = room().monkeyX * viewW;
        if (onGround && Math.abs((heroX + HERO_W / 2f) - goalX) < 78f) {
            reunionLaughPlayed = true;
            playXingxingLaugh();
            mode = MODE_COMPLETE;
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
                float bladeX = anchorX + (float) Math.sin(swing) * length;
                float bladeY = anchorY + (float) Math.cos(swing) * length;
                paint.setColor(0xFF705D4F);
                paint.setStrokeWidth(4f);
                canvas.drawLine(anchorX, anchorY, bladeX, bladeY, paint);
                paint.setColor(0xFFB8C0C9);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(4f);
                canvas.drawCircle(bladeX, bladeY, 17f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF5F6875);
                canvas.drawCircle(bladeX, bladeY, 10f, paint);
                for (int k = 0; k < 4; k++) {
                    double a = k * Math.PI / 2.0 + animationTime * 1.1;
                    canvas.drawLine(bladeX, bladeY,
                            bladeX + (float) Math.cos(a) * 22f, bladeY + (float) Math.sin(a) * 22f, paint);
                }
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
                paint.setColor(0xFF77716C); canvas.drawCircle(bx, floorY - radius, radius, paint);
                paint.setColor(0xFFAAA39A); canvas.drawCircle(bx - radius * .28f, floorY - radius * 1.32f, radius * .20f, paint);
                paint.setColor(0xFF48434A); paint.setStrokeWidth(2f);
                canvas.drawLine(bx - radius * .50f, floorY - radius * .85f, bx + radius * .26f, floorY - radius * 1.38f, paint);
                canvas.drawLine(bx + radius * .05f, floorY - radius * .55f, bx + radius * .55f, floorY - radius * .98f, paint);
            } else if (o.type == CinematicRoomManager.ObstacleDef.FALLING_ROCK) {
                float cycle = (animationTime + o.phase) % 3.2f;
                float drop = cycle < .95f ? cycle / .95f : 1f;
                float rockY = -22f + drop * (floorY - 44f);
                float rx = x + (float) Math.sin(o.phase) * o.range * viewW;
                float radius = Math.max(15f, o.width * viewW * .45f);
                paint.setColor(0xFF8B817A); canvas.drawCircle(rx, rockY, radius, paint);
                paint.setColor(0xFFB5A99C); canvas.drawCircle(rx - radius * .25f, rockY - radius * .28f, radius * .25f, paint);
                paint.setColor(Color.argb(80, 10, 10, 15));
                canvas.drawOval(rx - radius, floorY - 2f, rx + radius, floorY + 4f, paint);
            } else if (o.type == CinematicRoomManager.ObstacleDef.WATER_POOL) {
                float w = o.width * viewW;
                paint.setColor(0xFF244D69);
                canvas.drawRect(x - w * .5f, floorY - 4f, x + w * .5f, floorY + 12f, paint);
                paint.setColor(0xFF78C8D7); paint.setStrokeWidth(2f);
                for (int k = 0; k < 4; k++) {
                    float waveX = x - w * .42f + ((animationTime * 38f + k * 31f) % Math.max(30f, w * .84f));
                    float waveY = floorY + 1f + (float) Math.sin(animationTime * 3f + k) * 2f;
                    canvas.drawLine(waveX, waveY, waveX + 14f, waveY, paint);
                }
            }
        }
    }

    private void updateObstacleInteractions() {
        float heroCenter = heroX + HERO_W * .5f;
        float heroFeet = heroY + HERO_H;
        for (CinematicRoomManager.ObstacleDef o : room().obstacles) {
            if (o.type == CinematicRoomManager.ObstacleDef.SWING_BLADE) {
                float swing = (float) Math.sin(animationTime * 2.45f + o.phase) * .42f;
                float length = viewH * .37f;
                float bladeX = o.x * viewW + (float) Math.sin(swing) * length;
                float bladeY = viewH * .20f + (float) Math.cos(swing) * length;
                if (Math.abs(heroCenter - bladeX) < 30f && heroY < bladeY + 22f && heroFeet > bladeY - 22f) damageTeam(13f);
            } else if (o.type == CinematicRoomManager.ObstacleDef.FIRE_JET) {
                float cycle = (animationTime + o.phase) % 2.65f;
                boolean active = cycle < 1.15f;
                if (active && Math.abs(heroCenter - o.x * viewW) < Math.max(24f, o.width * viewW * .42f)
                        && onGround && heroFeet >= floorY - 49f) damageTeam(11f);
            } else if (o.type == CinematicRoomManager.ObstacleDef.ROLLING_BOULDER) {
                float bx = (o.x + (float) Math.sin(animationTime * 1.5f + o.phase) * o.range) * viewW;
                float radius = Math.max(17f, o.width * viewW * .48f);
                if (Math.abs(heroCenter - bx) < radius + HERO_W * .30f && heroFeet > floorY - radius * 1.9f) damageTeam(14f);
            } else if (o.type == CinematicRoomManager.ObstacleDef.FALLING_ROCK) {
                float cycle = (animationTime + o.phase) % 3.2f;
                if (cycle < .95f) {
                    float rockY = -22f + (cycle / .95f) * (floorY - 44f);
                    float rx = o.x * viewW + (float) Math.sin(o.phase) * o.range * viewW;
                    float radius = Math.max(15f, o.width * viewW * .45f);
                    if (Math.abs(heroCenter - rx) < radius + HERO_W * .30f
                            && heroY < rockY + radius && heroFeet > rockY - radius) damageTeam(14f);
                }
            } else if (o.type == CinematicRoomManager.ObstacleDef.WATER_POOL) {
                float half = o.width * viewW * .5f;
                if (heroCenter > o.x * viewW - half && heroCenter < o.x * viewW + half
                        && heroFeet >= floorY - 15f) {
                    heroVx *= .992f;
                }
            }
        }
    }

    private void drawPlayer(Canvas canvas) {
        if (annieFlash > 0f && ((int) (animationTime * 22f) % 2 == 0)) return;
        Bitmap frame = null;
        Bitmap[] animatedFrames = null;
        float phase = heroAnimationPhase;
        if (playerState == STATE_ATTACK && annieAttack != null) frame = annieAttack;
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
        if (moving) drawBlendedFrames(canvas, berthuWalk, berthuAnimationPhase, dst, heroX >= berthuX);
        else if (frame != null) drawFacingBitmap(canvas, frame, dst, heroX >= berthuX);
        else if (berthuIdle == null) drawCatFallback(canvas, dst);
        if (berthuFlash > 0f) {
            paint.setColor(Color.argb(65, 255, 219, 116));
            canvas.drawCircle(berthuX + 23f, berthuY + 18f, 31f, paint);
        }
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

    private void drawXingxing(Canvas canvas) {
        if (room().monkeyX < 0f) return;
        float monkeyPhase = animationTime * 7.2f;
        Bitmap frame = choose(xingxingFrames, (int) monkeyPhase);
        float x = room().monkeyX * viewW;
        float w = 82f;
        float h = frame != null && frame.getHeight() > 0 ? w * frame.getHeight() / (float) frame.getWidth() : 88f;
        float bob = (float) Math.abs(Math.sin(animationTime * (mode == MODE_COMPLETE ? 6.8f : 3.2f)))
                * (mode == MODE_COMPLETE ? 8f : 4f);
        RectF dst = new RectF(x - w * .5f, floorY - h - bob, x + w * .5f, floorY - bob);
        paint.setColor(Color.argb(38, 255, 198, 82));
        canvas.drawOval(x - 45f, floorY - 9f, x + 45f, floorY + 3f, paint);
        if (frame != null) drawBlendedFrames(canvas, xingxingFrames,
                mode == MODE_COMPLETE ? animationTime * 10.5f : monkeyPhase, dst, true);
        else drawMonkeyFallback(canvas, dst);
        paint.setColor(Color.argb(220, 32, 26, 43));
        canvas.drawRoundRect(x - 39f, dst.top - 22f, x + 39f, dst.top - 5f, 7f, 7f, paint);
        paint.setColor(0xFFFFD47D);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(10f);
        canvas.drawText("XINGXING", x, dst.top - 10f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
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
        Bitmap frame;
        if (enemy.backflipTimer > 0f) frame = tigerFrames[5] != null ? tigerFrames[5] : tigerFrames[1];
        else if (enemy.hitFlash > 0f) frame = tigerFrames[6] != null ? tigerFrames[6] : tigerFrames[0];
        else if (enemy.attackTimer > 0f) frame = enemy.attackPose == 1
                ? (enemy.boss ? tigerFrames[5] : tigerFrames[4]) : tigerFrames[3];
        else if (enemy.moving) frame = tigerFrames[1] != null ? tigerFrames[1] : tigerFrames[0];
        else frame = tigerFrames[0];

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
            // Rotate the existing high-resolution fighter sprite through a complete aerial backflip.
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
            paint.setColor(enemy.boss ? 0xFF6D273E : 0xFF493746);
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
        paint.setColor(Color.argb(198, 20, 23, 39));
        canvas.drawRoundRect(12f, 10f, viewW - 12f, 70f, 14f, 14f, paint);
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextSize(15f);
        canvas.drawText("ANNIEHILATION · CINEMATIC PROTOTYPE", 25f, 30f, paint);
        paint.setColor(0xFFE1D3E7);
        paint.setTextSize(11f);
        canvas.drawText(room().title + "   ·   LEVEL " + (roomId + 1) + "/50", 25f, 47f, paint);
        drawBar(canvas, 25f, 55f, 115f, 7f, annieHp / ANNIE_MAX_HP, 0xFFE96A75);
        drawBar(canvas, 150f, 55f, 115f, 7f, berthuHp / BERTHU_MAX_HP, 0xFFF0B05D);
        paint.setColor(0xFFEDE7F2);
        paint.setTextSize(8f);
        canvas.drawText("ANNIE", 25f, 53f, paint);
        canvas.drawText("BERTHU", 150f, 53f, paint);
        if (messageTimer > 0f) {
            paint.setColor(Color.argb(190, 24, 23, 40));
            canvas.drawRoundRect(viewW * .28f, 78f, viewW * .72f, 108f, 9f, 9f, paint);
            paint.setColor(0xFFFFE0A4);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(11f);
            canvas.drawText(room().subtitle, viewW * .5f, 97f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
        if (sfxFeedbackTimer > 0f) {
            paint.setColor(0xFFFFD47D);
            paint.setTextSize(10f);
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("SFX TEST", viewW - 82f, 91f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawBar(Canvas canvas, float x, float y, float w, float h, float value, int color) {
        paint.setColor(0xFF4A3D4C);
        canvas.drawRoundRect(x, y, x + w, y + h, h, h, paint);
        paint.setColor(color);
        canvas.drawRoundRect(x, y, x + w * Math.max(0f, Math.min(1f, value)), y + h, h, h, paint);
    }

    private void drawControls(Canvas canvas) {
        drawControl(canvas, 62f, viewH - 73f, "◀", leftHeld);
        drawControl(canvas, 137f, viewH - 73f, "▶", rightHeld);
        drawControl(canvas, 99f, viewH - 143f, "UP", upHeld);
        drawControl(canvas, 99f, viewH - 25f, "DN", downHeld);
        drawControl(canvas, viewW - 238f, viewH - 72f, "ACT", actionHeld);
        drawControl(canvas, viewW - 161f, viewH - 72f, "JUMP", jumpHeld);
        drawControl(canvas, viewW - 82f, viewH - 72f, "HIT", attackHeld);
        drawControl(canvas, viewW - 82f, viewH - 145f, runMode ? "RUN" : "WALK", runButtonTouch);
        drawControl(canvas, viewW - 190f, 34f, "EXIT", exitButtonTouch);
        drawControl(canvas, viewW - 112f, 34f, "SFX", sfxButtonTouch);
        drawControl(canvas, viewW - 42f, 34f, "Ⅱ", pauseButtonTouch);
    }

    private void drawControl(Canvas canvas, float cx, float cy, String label, boolean pressed) {
        paint.setColor(pressed ? Color.argb(215, 255, 202, 112) : Color.argb(105, 19, 23, 38));
        canvas.drawCircle(cx, cy, 29f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(pressed ? 0xFFFFF0C4 : Color.argb(155, 230, 226, 239));
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
        jumpWasHeld = attackWasHeld = actionWasHeld = false;
        runButtonWasHeld = pauseWasHeld = sfxWasHeld = false;
    }

    private boolean isInside(float x, float y, float cx, float cy, float r) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (mode == MODE_PAUSED || mode == MODE_GAME_OVER || mode == MODE_COMPLETE) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                if (mode == MODE_PAUSED) {
                    if (pauseOpeningTap) pauseOpeningTap = false;
                    else mode = MODE_PLAYING;
                } else if (mode == MODE_GAME_OVER) {
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
            if (isInside(tx, ty, viewW - 42f, 34f, 34f)) {
                mode = MODE_PAUSED;
                pauseOpeningTap = true;
                releaseControls();
                invalidate();
                return true;
            }
            if (isInside(tx, ty, viewW - 190f, 34f, 34f)) {
                if (returnToDashboard != null) returnToDashboard.run();
                return true;
            }
            if (isInside(tx, ty, viewW - 112f, 34f, 34f)) {
                playAttackSound(true);
                sfxFeedbackTimer = 1.0f;
                invalidate();
                return true;
            }
            if (isInside(tx, ty, viewW - 82f, viewH - 145f, 34f)) {
                runMode = !runMode;
                runButtonTouch = true;
                invalidate();
                return true;
            }
            if (isInside(tx, ty, viewW - 161f, viewH - 72f, 34f)) pendingJump = true;
            if (isInside(tx, ty, viewW - 82f, viewH - 72f, 34f)) pendingAttack = true;
            if (isInside(tx, ty, viewW - 238f, viewH - 72f, 34f)) pendingAction = true;
        }

        boolean l = false, r = false, u = false, d = false;
        boolean j = false, a = false, act = false, run = false, pause = false, sfx = false, exit = false;
        int count = event.getPointerCount();
        for (int i = 0; i < count; i++) {
            if ((action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP)
                    && i == event.getActionIndex()) continue;
            float x = event.getX(i) / density;
            float y = event.getY(i) / density;
            l |= isInside(x, y, 62f, viewH - 73f, 34f);
            r |= isInside(x, y, 137f, viewH - 73f, 34f);
            u |= isInside(x, y, 99f, viewH - 143f, 34f);
            d |= isInside(x, y, 99f, viewH - 25f, 34f);
            act |= isInside(x, y, viewW - 238f, viewH - 72f, 34f);
            j |= isInside(x, y, viewW - 161f, viewH - 72f, 34f);
            a |= isInside(x, y, viewW - 82f, viewH - 72f, 34f);
            run |= isInside(x, y, viewW - 82f, viewH - 145f, 34f);
            sfx |= isInside(x, y, viewW - 112f, 34f, 34f);
            pause |= isInside(x, y, viewW - 42f, 34f, 34f);
            exit |= isInside(x, y, viewW - 190f, 34f, 34f);
        }
        leftHeld = l; rightHeld = r; upHeld = u; downHeld = d;
        jumpHeld = j; attackHeld = a; actionHeld = act;
        runButtonTouch = run; sfxButtonTouch = sfx; pauseButtonTouch = pause; exitButtonTouch = exit;
        invalidate();
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        for (MediaPlayer player : new ArrayList<>(fallbackPlayers)) {
            try { player.release(); } catch (RuntimeException ignored) { }
        }
        fallbackPlayers.clear();
    }

    private static final class Enemy {
        float x, hp, maxHp, patrolMin, patrolMax, attackCooldown, attackTimer, hitFlash, vx;
        float width = 58f;
        float direction = 1f;
        float backflipTimer, backflipDuration = .82f, backflipCooldown = 2f;
        float backflipStartX, backflipDirection = -1f;
        boolean boss, facingRight = false, moving;
        int attackPose, attackCount;
        Enemy(float x, float hp, boolean boss, float patrolRadius) {
            this.x = x; this.hp = this.maxHp = hp; this.boss = boss;
            this.patrolMin = x - patrolRadius; this.patrolMax = x + patrolRadius;
            this.attackCooldown = .4f;
            this.width = boss ? 76f : 59f;
            this.backflipCooldown = boss ? 2.1f : 9999f;
        }
    }
}
