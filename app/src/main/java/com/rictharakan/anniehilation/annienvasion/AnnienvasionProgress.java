package com.rictharakan.anniehilation.annienvasion;

import android.content.Context;
import android.content.SharedPreferences;

/** Tiny local progress store. No account, network, or storage permission required. */
final class AnnienvasionProgress {
    private static final String PREFS = "annienvasion_progress_v1";
    private static final String KEY_UNLOCKED = "unlocked_stage";

    private AnnienvasionProgress() { }

    static int loadUnlockedStage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return Math.max(1, Math.min(50, prefs.getInt(KEY_UNLOCKED, 1)));
    }

    static void saveUnlockedStage(Context context, int stage) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int safeStage = Math.max(1, Math.min(50, stage));
        int current = prefs.getInt(KEY_UNLOCKED, 1);
        if (safeStage > current) prefs.edit().putInt(KEY_UNLOCKED, safeStage).apply();
    }
}
