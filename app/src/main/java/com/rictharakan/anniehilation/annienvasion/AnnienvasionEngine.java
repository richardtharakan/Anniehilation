package com.rictharakan.anniehilation.annienvasion;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java rules engine for Annienvasion. No Android classes are used here,
 * so the core rules can be compiled and tested independently.
 */
public final class AnnienvasionEngine {
    public static final int PHASE_RUNWAY = 0;
    public static final int PHASE_BATTLE = 1;
    public static final int PHASE_WON = 2;
    public static final int PHASE_LOST = 3;

    public static final int MAX_ARMY = 120;
    public static final int GATES_PER_RUN = 5;

    public static final class GateOption {
        public final boolean multiply;
        public final int amount;

        GateOption(boolean multiply, int amount) {
            this.multiply = multiply;
            this.amount = amount;
        }

        public int apply(int current) {
            long result = multiply ? (long) current * amount : (long) current + amount;
            return (int) Math.max(1L, Math.min(MAX_ARMY, result));
        }

        public String label() {
            return (multiply ? "x" : "+") + amount;
        }
    }

    public static final class GatePair {
        public final GateOption left;
        public final GateOption right;

        GatePair(GateOption left, GateOption right) {
            this.left = left;
            this.right = right;
        }
    }

    private final int stage;
    private final List<GatePair> gates = new ArrayList<GatePair>();
    private int phase = PHASE_RUNWAY;
    private int gateIndex;
    private int playerArmy = 1;
    private int enemyArmy = 1;
    private int selectedGate = -1;
    private int lastPlayerChoice = 0;
    private int lastEnemyChoice = 0;
    private float gateClock;
    private float battleClock;
    private float playerCasualtyClock;
    private float enemyCasualtyClock;
    private float pounceCooldown;
    private float rallyCooldown;
    private float victoryClock;
    private int pounceUses;
    private int rallyUses;
    private String message = "Pick the stronger gate. Build Annie's squad!";

    public AnnienvasionEngine(int stage) {
        this.stage = Math.max(1, Math.min(50, stage));
        buildGates();
    }

    private void buildGates() {
        int bonus = Math.min(12, (stage - 1) / 5);
        gates.add(new GatePair(new GateOption(false, 4 + bonus), new GateOption(true, 2)));
        gates.add(new GatePair(new GateOption(false, 6 + bonus), new GateOption(true, 2)));
        gates.add(new GatePair(new GateOption(true, 2), new GateOption(false, 10 + bonus)));
        gates.add(new GatePair(new GateOption(true, 2), new GateOption(false, 16 + bonus)));
        gates.add(new GatePair(new GateOption(false, 10 + bonus), new GateOption(true, stage >= 8 ? 3 : 2)));
    }

    public void chooseGate(int side) {
        if (phase != PHASE_RUNWAY || gateIndex >= GATES_PER_RUN) return;
        selectedGate = side == 1 ? 1 : 0;
        message = selectedGate == 1 ? "Right lane selected. Hold your nerve!" : "Left lane selected. Keep moving!";
    }

    public void update(float deltaSeconds) {
        float dt = Math.max(0f, Math.min(0.08f, deltaSeconds));
        if (phase == PHASE_RUNWAY) {
            gateClock += dt;
            if (gateClock >= 2.85f) advanceGate();
        } else if (phase == PHASE_BATTLE) {
            updateBattle(dt);
        } else if (phase == PHASE_WON) {
            victoryClock += dt;
        }
    }

    private void advanceGate() {
        if (gateIndex >= gates.size()) return;
        GatePair pair = gates.get(gateIndex);
        int playerChoice = selectedGate < 0 ? bestChoice(playerArmy, pair) : selectedGate;
        lastPlayerChoice = playerChoice;
        GateOption playerOption = playerChoice == 1 ? pair.right : pair.left;
        playerArmy = playerOption.apply(playerArmy);

        int enemyChoice = bestChoice(enemyArmy, pair);
        // Rival captains are not perfect calculators: some stages expose a different
        // weakness, giving the player a genuine strategic edge for reading the gates.
        int rivalMistakeGate = (stage % 2 == 0) ? 2 : 1;
        if (gateIndex == rivalMistakeGate) enemyChoice = 1 - enemyChoice;
        lastEnemyChoice = enemyChoice;
        GateOption enemyOption = enemyChoice == 1 ? pair.right : pair.left;
        enemyArmy = enemyOption.apply(enemyArmy);

        gateIndex++;
        selectedGate = -1;
        gateClock = 0f;
        message = "Annie's squad: " + playerArmy + "   •   Rival squad: " + enemyArmy;
        if (gateIndex >= GATES_PER_RUN) {
            // A gentle stage-based rival adjustment keeps later rounds challenging
            // without turning the opening gate race into an automatic loss.
            int rivalBonus = Math.min(12, (stage - 1) / 4);
            enemyArmy = Math.min(MAX_ARMY, enemyArmy + rivalBonus);
            phase = PHASE_BATTLE;
            message = "ARMIES COLLIDE! Tap a command when it is ready.";
        }
    }

    private int bestChoice(int current, GatePair pair) {
        int left = pair.left.apply(current);
        int right = pair.right.apply(current);
        return right > left ? 1 : 0;
    }

    private void updateBattle(float dt) {
        battleClock += dt;
        pounceCooldown = Math.max(0f, pounceCooldown - dt);
        rallyCooldown = Math.max(0f, rallyCooldown - dt);

        // Automatic troop combat. A larger formation gains a modest attack-rate edge.
        float playerRate = 0.52f + Math.min(0.55f, playerArmy / 100f);
        float enemyRate = 0.52f + Math.min(0.55f, enemyArmy / 100f);
        playerCasualtyClock += dt * playerRate;
        enemyCasualtyClock += dt * enemyRate;

        while (enemyArmy > 0 && playerCasualtyClock >= 1f) {
            enemyArmy--;
            playerCasualtyClock -= 1f;
        }
        while (playerArmy > 0 && enemyCasualtyClock >= 1f) {
            playerArmy--;
            enemyCasualtyClock -= 1f;
        }

        if (enemyArmy <= 0) {
            enemyArmy = 0;
            phase = PHASE_WON;
            victoryClock = 0f;
            message = "THE FIELD IS OURS! Xingxing is on his way.";
        } else if (playerArmy <= 0) {
            playerArmy = 0;
            phase = PHASE_LOST;
            message = "The rival held the line. Try a different gate strategy.";
        } else if (battleClock > 95f) {
            if (playerArmy >= enemyArmy) {
                phase = PHASE_WON;
                message = "TIME! Annie's squad held the field!";
            } else {
                phase = PHASE_LOST;
                message = "TIME! The rival army held the field.";
            }
        } else {
            message = "Annie's squad " + playerArmy + "  vs  rival " + enemyArmy;
        }
    }

    /** Berthu's Pounce removes a small group of opposing miniatures. */
    public boolean usePounce() {
        if (phase != PHASE_BATTLE || pounceCooldown > 0f || enemyArmy <= 0) return false;
        int damage = Math.min(enemyArmy, 4 + Math.min(3, stage / 12));
        enemyArmy -= damage;
        pounceUses++;
        pounceCooldown = 10f;
        message = "BERTHU POUNCE! " + damage + " rival troops scattered.";
        if (enemyArmy <= 0) finishVictory();
        return true;
    }

    /** Annie's Rally restores a few tired troops, up to the army cap. */
    public boolean useRally() {
        if (phase != PHASE_BATTLE || rallyCooldown > 0f || playerArmy <= 0) return false;
        int before = playerArmy;
        playerArmy = Math.min(MAX_ARMY, playerArmy + 3 + Math.min(2, stage / 15));
        int restored = playerArmy - before;
        rallyUses++;
        rallyCooldown = 14f;
        message = "ANNIE RALLY! " + restored + " miniatures rejoined the squad.";
        return true;
    }

    private void finishVictory() {
        enemyArmy = 0;
        phase = PHASE_WON;
        victoryClock = 0f;
        message = "THE FIELD IS OURS! Xingxing is on his way.";
    }

    public int getStage() { return stage; }
    public int getPhase() { return phase; }
    public int getGateIndex() { return gateIndex; }
    public int getSelectedGate() { return selectedGate; }
    public int getLastPlayerChoice() { return lastPlayerChoice; }
    public int getLastEnemyChoice() { return lastEnemyChoice; }
    public float getGateProgress() { return Math.max(0f, Math.min(1f, gateClock / 2.85f)); }
    public int getDisplayPlayerChoice() {
        if (selectedGate >= 0) return selectedGate;
        return bestChoice(playerArmy, getCurrentGate());
    }
    public int getDisplayEnemyChoice() {
        int choice = bestChoice(enemyArmy, getCurrentGate());
        int rivalMistakeGate = (stage % 2 == 0) ? 2 : 1;
        return gateIndex == rivalMistakeGate ? 1 - choice : choice;
    }
    public int getPlayerArmy() { return playerArmy; }
    public int getEnemyArmy() { return enemyArmy; }
    public int getPounceUses() { return pounceUses; }
    public int getRallyUses() { return rallyUses; }
    public float getPounceCooldown() { return pounceCooldown; }
    public float getRallyCooldown() { return rallyCooldown; }
    public float getBattleClock() { return battleClock; }
    public float getVictoryClock() { return victoryClock; }
    public String getMessage() { return message; }
    public GatePair getCurrentGate() { return gateIndex < gates.size() ? gates.get(gateIndex) : gates.get(gates.size() - 1); }
    public float getRunwayProgress() { return Math.min(1f, gateIndex / (float) GATES_PER_RUN); }

    public boolean isFinished() { return phase == PHASE_WON || phase == PHASE_LOST; }
    public boolean isWon() { return phase == PHASE_WON; }
}
