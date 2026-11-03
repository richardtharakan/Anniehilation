package com.rictharakan.anniehilation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Data-driven 50-stage cinematic room campaign.
 * Each stage is one authored-style room blueprint with generated variations,
 * a destination/reunion point, different trap combinations and a forward link.
 * The geometry is intentionally deterministic so every visit to a stage is
 * consistent and can later be replaced by hand-authored room data.
 */
final class CinematicRoomManager {
    static final int SCHOOL_GATE = 0;
    static final int COURTYARD = 1;
    static final int FORT_BALCONY = 2;
    static final int HIDDEN_HALL = 3;
    static final int ROOM_COUNT = 50;

    static final int LEFT = 0;
    static final int RIGHT = 1;
    static final int UP = 2;
    static final int DOWN = 3;

    static final class PlatformDef {
        final float x, width, height;
        PlatformDef(float x, float width, float height) {
            this.x = x; this.width = width; this.height = height;
        }
    }

    static final class GapDef {
        final float x, width;
        GapDef(float x, float width) { this.x = x; this.width = width; }
    }

    static final class HazardDef {
        final float x, width;
        final boolean heavy;
        HazardDef(float x, float width, boolean heavy) {
            this.x = x; this.width = width; this.heavy = heavy;
        }
    }

    static final class ObstacleDef {
        static final int SWING_BLADE = 1;
        static final int FIRE_JET = 2;
        static final int ROLLING_BOULDER = 3;
        static final int FALLING_ROCK = 4;
        static final int WATER_POOL = 5;
        static final int MOVING_PLATFORM = 6;

        final int type;
        final float x, width, range, phase;
        ObstacleDef(int type, float x, float width, float range, float phase) {
            this.type = type; this.x = x; this.width = width;
            this.range = range; this.phase = phase;
        }
    }

    static final class EnemyDef {
        final float x, patrolRadius, hp;
        final boolean boss;
        EnemyDef(float x, float patrolRadius, float hp, boolean boss) {
            this.x = x; this.patrolRadius = patrolRadius; this.hp = hp; this.boss = boss;
        }
    }

    static final class RoomDef {
        final int id;
        final String title, subtitle;
        final int theme;
        final int skyTop, skyBottom, farColor, middleColor, floorColor, floorHighlight;
        final int[] links;
        final List<PlatformDef> platforms;
        final List<GapDef> gaps;
        final List<HazardDef> hazards;
        final List<ObstacleDef> obstacles;
        final List<EnemyDef> enemies;
        final float ladderX, ladderTop, pressurePlateX, gateX, monkeyX;

        RoomDef(int id, String title, String subtitle, int theme,
                int skyTop, int skyBottom, int farColor, int middleColor,
                int floorColor, int floorHighlight, int[] links,
                List<PlatformDef> platforms, List<GapDef> gaps,
                List<HazardDef> hazards, List<ObstacleDef> obstacles,
                List<EnemyDef> enemies, float ladderX, float ladderTop,
                float pressurePlateX, float gateX, float monkeyX) {
            this.id = id; this.title = title; this.subtitle = subtitle; this.theme = theme;
            this.skyTop = skyTop; this.skyBottom = skyBottom; this.farColor = farColor;
            this.middleColor = middleColor; this.floorColor = floorColor;
            this.floorHighlight = floorHighlight; this.links = links;
            this.platforms = Collections.unmodifiableList(platforms);
            this.gaps = Collections.unmodifiableList(gaps);
            this.hazards = Collections.unmodifiableList(hazards);
            this.obstacles = Collections.unmodifiableList(obstacles);
            this.enemies = Collections.unmodifiableList(enemies);
            this.ladderX = ladderX; this.ladderTop = ladderTop;
            this.pressurePlateX = pressurePlateX; this.gateX = gateX; this.monkeyX = monkeyX;
        }
        int link(int direction) { return links[direction]; }
    }

    private final RoomDef[] roomCache = new RoomDef[ROOM_COUNT];

    private static final String[] WORLD_NAMES = {
            "THE SCHOOL GROUNDS", "THE OLD CITY", "THE FORGOTTEN FORT",
            "THE WILD JUNGLE", "THE UNDERGROUND KINGDOM", "THE MOUNTAIN PASSAGE",
            "THE NIGHT CITADEL", "XINGXING'S KINGDOM"
    };
    private static final String[] TITLES = {
            "SCHOOL GATE", "MARKET ROOFTOPS", "FORTRESS WALKWAY", "JUNGLE CROSSING",
            "CRYSTAL CAVERN", "MOUNTAIN BRIDGE", "NIGHT CITADEL", "MONKEY KINGDOM"
    };

    // Eight coordinated palettes: school, old city, fort, jungle, cave, mountain, citadel, kingdom.
    private static final int[][] PALETTES = {
            {0xFF6F9FC9,0xFFE7CDA7,0xFF718D9C,0xFF526D68,0xFF586052,0xFFB7A27A},
            {0xFF587F9E,0xFFE7B98D,0xFF6B7885,0xFF795C50,0xFF5C514C,0xFFC09A70},
            {0xFF77758F,0xFFE5A97C,0xFF5C5366,0xFF453E51,0xFF51484A,0xFFB18B69},
            {0xFF397C75,0xFFB4C98F,0xFF315C4B,0xFF326C45,0xFF455A42,0xFF9EAA68},
            {0xFF252B49,0xFF6B6386,0xFF34354C,0xFF403B4B,0xFF49404A,0xFF9F816D},
            {0xFF668FAE,0xFFD4D9DE,0xFF526B83,0xFF63727F,0xFF626C75,0xFFB7C6D0},
            {0xFF25233F,0xFF79516B,0xFF343047,0xFF45364B,0xFF403B49,0xFFAD796C},
            {0xFF365C78,0xFFB7A4D1,0xFF3C5267,0xFF4A5364,0xFF504C5E,0xFFE1B77A}
    };

    RoomDef getRoom(int id) {
        int safeId = Math.max(0, Math.min(ROOM_COUNT - 1, id));
        if (roomCache[safeId] == null) roomCache[safeId] = makeRoom(safeId);
        return roomCache[safeId];
    }

    private RoomDef makeRoom(int id) {
        int world = Math.min(7, id / 7);
        int stageInWorld = id % 7;
        int pattern = (id * 7 + stageInWorld * 3) % 6;
        int[] palette = PALETTES[world];

        List<PlatformDef> platforms = new ArrayList<>();
        List<GapDef> gaps = new ArrayList<>();
        List<HazardDef> hazards = new ArrayList<>();
        List<ObstacleDef> obstacles = new ArrayList<>();
        List<EnemyDef> enemies = new ArrayList<>();

        // Six layout families cycle through the campaign; widths and elevations vary by stage.
        switch (pattern) {
            case 0:
                platforms.add(new PlatformDef(.15f,.18f,.17f));
                platforms.add(new PlatformDef(.41f,.19f,.30f));
                platforms.add(new PlatformDef(.70f,.16f,.20f));
                gaps.add(new GapDef(.575f,.075f));
                break;
            case 1:
                platforms.add(new PlatformDef(.10f,.22f,.23f));
                platforms.add(new PlatformDef(.40f,.15f,.36f));
                platforms.add(new PlatformDef(.68f,.21f,.18f));
                gaps.add(new GapDef(.315f,.065f));
                gaps.add(new GapDef(.59f,.055f));
                break;
            case 2:
                platforms.add(new PlatformDef(.20f,.20f,.20f));
                platforms.add(new PlatformDef(.49f,.18f,.40f));
                platforms.add(new PlatformDef(.77f,.12f,.25f));
                gaps.add(new GapDef(.405f,.075f));
                break;
            case 3:
                platforms.add(new PlatformDef(.12f,.16f,.30f));
                platforms.add(new PlatformDef(.36f,.21f,.17f));
                platforms.add(new PlatformDef(.66f,.18f,.34f));
                gaps.add(new GapDef(.255f,.07f));
                gaps.add(new GapDef(.57f,.07f));
                break;
            case 4:
                platforms.add(new PlatformDef(.08f,.22f,.18f));
                platforms.add(new PlatformDef(.39f,.18f,.29f));
                platforms.add(new PlatformDef(.70f,.19f,.38f));
                gaps.add(new GapDef(.31f,.065f));
                break;
            default:
                platforms.add(new PlatformDef(.16f,.18f,.24f));
                platforms.add(new PlatformDef(.43f,.15f,.33f));
                platforms.add(new PlatformDef(.68f,.22f,.19f));
                gaps.add(new GapDef(.59f,.06f));
                break;
        }

        // Every gap gets visible spikes; the wider/deeper pattern is used in later worlds.
        for (GapDef gap : gaps) hazards.add(new HazardDef(gap.x, gap.width, world >= 4));

        // New obstacle roster is deliberately mixed and spaced so each stage asks for a different response.
        int obstacleA = (id + world) % 5;
        float trapX = .34f + (stageInWorld % 3) * .105f;
        if (obstacleA == 0) {
            obstacles.add(new ObstacleDef(ObstacleDef.SWING_BLADE, trapX, .12f, .055f, id * .43f));
        } else if (obstacleA == 1) {
            obstacles.add(new ObstacleDef(ObstacleDef.FIRE_JET, trapX, .105f, 0f, id * .37f));
        } else if (obstacleA == 2) {
            obstacles.add(new ObstacleDef(ObstacleDef.ROLLING_BOULDER, trapX, .09f, .13f, id * .29f));
        } else if (obstacleA == 3) {
            obstacles.add(new ObstacleDef(ObstacleDef.FALLING_ROCK, trapX, .095f, .07f, id * .51f));
        } else {
            obstacles.add(new ObstacleDef(ObstacleDef.WATER_POOL, trapX, .19f, 0f, id * .19f));
        }
        // A second hazard appears in every third room, with a different type from the first.
        if (id % 3 == 1) {
            int secondType = (obstacleA + 2) % 5;
            int type = secondType == 0 ? ObstacleDef.SWING_BLADE
                    : secondType == 1 ? ObstacleDef.FIRE_JET
                    : secondType == 2 ? ObstacleDef.ROLLING_BOULDER
                    : secondType == 3 ? ObstacleDef.FALLING_ROCK : ObstacleDef.WATER_POOL;
            obstacles.add(new ObstacleDef(type, .70f, type == ObstacleDef.WATER_POOL ? .16f : .09f,
                    type == ObstacleDef.ROLLING_BOULDER ? .10f : .045f, id * .67f));
        }
        if (id % 6 == 4) {
            obstacles.add(new ObstacleDef(ObstacleDef.MOVING_PLATFORM, .45f, .19f, .12f, id * .23f));
        }

        // One to three ordinary enemies; every seventh stage ends with the boss version.
        boolean bossStage = (stageInWorld == 6) || id == ROOM_COUNT - 1;
        int enemyCount = 1 + ((id + 1) % 3);
        float[] spawnX = {.40f, .61f, .75f};
        for (int i = 0; i < enemyCount; i++) {
            boolean boss = bossStage && i == enemyCount - 1;
            float hp = boss ? 205f + world * 14f : 74f + world * 8f + i * 7f;
            enemies.add(new EnemyDef(spawnX[i], .055f + .012f * ((id + i) % 3), hp, boss));
        }

        boolean hasGate = id % 5 == 2 || id % 5 == 4;
        float plateX = hasGate ? .22f : -1f;
        float gateX = hasGate ? .80f : -1f;
        boolean hasLadder = id % 4 == 1 || id % 4 == 3;
        float ladderX = hasLadder ? .885f : -1f;
        float ladderTop = hasLadder ? .50f : -1f;
        if (hasLadder) platforms.add(new PlatformDef(.81f, .16f, .118f));

        int previous = id > 0 ? id - 1 : -1;
        int next = id < ROOM_COUNT - 1 ? id + 1 : -1;
        int[] links = {previous, next, -1, -1};

        String title = String.format("%s · STAGE %02d", TITLES[world], id + 1);
        String subtitle = subtitleFor(id, obstacles, hasGate, hasLadder, bossStage);
        // Every level ends at Xingxing; completing the reunion advances to the next stage.
        float monkeyX = .91f;

        return new RoomDef(id, title, subtitle, world,
                palette[0], palette[1], palette[2], palette[3], palette[4], palette[5],
                links, platforms, gaps, hazards, obstacles, enemies,
                ladderX, ladderTop, plateX, gateX, monkeyX);
    }

    private String subtitleFor(int id, List<ObstacleDef> obstacles,
                               boolean gate, boolean ladder, boolean boss) {
        StringBuilder s = new StringBuilder("Stage ").append(id + 1).append(" / 50 · ");
        if (boss) s.append("Watch the boss's acrobatics. ");
        if (gate) s.append("Find the pressure plate. ");
        if (ladder) s.append("A ladder offers a higher route. ");
        if (!obstacles.isEmpty()) {
            int type = obstacles.get(0).type;
            if (type == ObstacleDef.SWING_BLADE) s.append("Time the swinging blade. ");
            else if (type == ObstacleDef.FIRE_JET) s.append("Wait for the fire jet to cool. ");
            else if (type == ObstacleDef.ROLLING_BOULDER) s.append("Avoid the rolling boulder. ");
            else if (type == ObstacleDef.FALLING_ROCK) s.append("Watch the ceiling. ");
            else if (type == ObstacleDef.WATER_POOL) s.append("Cross the water carefully. ");
        }
        s.append("Reach Xingxing!");
        return s.toString();
    }
}
