/*
 * Copyright 2026 Anniehilation contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rictharakan.anniehilation.chess;

import android.content.Context;
import android.content.SharedPreferences;

/** Saves the current local game as a replayable UCI move list. */
final class ChessProgressStore {
    private static final String PREFS = "anniehilation_chess_v1";
    private static final String KEY_MOVES = "uci_history";
    private static final String KEY_TERMINAL_RESULT = "terminal_result";

    private ChessProgressStore() { }

    static String load(Context context) {
        return prefs(context).getString(KEY_MOVES, "");
    }

    static String loadTerminalResult(Context context) {
        return prefs(context).getString(KEY_TERMINAL_RESULT, "");
    }

    static void save(Context context, ChessGameEngine game) {
        prefs(context).edit()
                .putString(KEY_MOVES, game.getUciHistory())
                .putString(KEY_TERMINAL_RESULT, game.getPersistentResultToken())
                .apply();
    }

    static void clear(Context context) {
        prefs(context).edit()
                .remove(KEY_MOVES)
                .remove(KEY_TERMINAL_RESULT)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
