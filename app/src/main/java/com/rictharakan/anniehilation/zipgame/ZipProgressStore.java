package com.rictharakan.anniehilation.zipgame;

import android.content.Context;
import android.content.SharedPreferences;

/** Small persistence helper; stores progression and the active board seed. */
final class ZipProgressStore {
    private static final String PREFS = "anniehilation_zip_trail";
    private static final String KEY_LEVEL = "level";
    private static final String KEY_SCORE = "score";
    private static final String KEY_SEED = "seed";

    private ZipProgressStore() { }

    static int loadLevel(Context context) {
        return prefs(context).getInt(KEY_LEVEL, 1);
    }

    static int loadScore(Context context) {
        return prefs(context).getInt(KEY_SCORE, 0);
    }

    static long loadSeed(Context context) {
        return prefs(context).getLong(KEY_SEED, 0L);
    }

    static void save(Context context, int level, int score, long seed) {
        prefs(context).edit()
                .putInt(KEY_LEVEL, Math.max(1, level))
                .putInt(KEY_SCORE, Math.max(0, score))
                .putLong(KEY_SEED, seed)
                .apply();
    }

    static void reset(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
