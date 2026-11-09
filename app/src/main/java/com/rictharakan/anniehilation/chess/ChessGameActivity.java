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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.WindowCompat;

import java.util.List;

/** Standalone local two-player chess screen for Anniehilation. */
public final class ChessGameActivity extends Activity implements ChessGameView.Listener {
    private ChessGameEngine game;
    private ChessGameView boardView;
    private TextView statusText;
    private TextView moveText;
    private Button undoButton;
    private Button drawButton;
    private boolean gameOverDialogShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        game = new ChessGameEngine();
        String saved = ChessProgressStore.load(this);
        String savedResult = ChessProgressStore.loadTerminalResult(this);
        boolean restored = game.restoreUciHistory(saved);
        if (!restored || !game.restorePersistentResultToken(savedResult)) {
            ChessProgressStore.clear(this);
            game.restart();
        }
        buildScreen();
        updateScreen();
    }

    private void buildScreen() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(Color.rgb(12, 18, 30));
        getWindow().setNavigationBarColor(Color.rgb(12, 18, 30));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(16, 24, 39));
        root.setPadding(dp(10), dp(8), dp(10), dp(8));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("ANNIEHILATION CHESS", 19f, Color.WHITE, true);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(38), 1f));
        addButton(top, "Back", new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        }, 0.35f, dp(38));
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(42)));

        statusText = text("White to move", 15f, Color.rgb(230, 238, 248), true);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(dp(4), 0, dp(4), 0);
        root.addView(statusText, new LinearLayout.LayoutParams(-1, dp(34)));

        boardView = new ChessGameView(this);
        boardView.setGame(game);
        boardView.setListener(this);
        root.addView(boardView, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView historyLabel = text("MOVE HISTORY", 10f, Color.rgb(156, 177, 201), true);
        historyLabel.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        root.addView(historyLabel, new LinearLayout.LayoutParams(-1, dp(18)));

        ScrollView historyScroll = new ScrollView(this);
        historyScroll.setFillViewport(false);
        moveText = text("No moves yet", 13f, Color.rgb(222, 230, 239), false);
        moveText.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        moveText.setPadding(dp(9), dp(5), dp(9), dp(5));
        historyScroll.addView(moveText, new ScrollView.LayoutParams(-1, -2));
        root.addView(historyScroll, new LinearLayout.LayoutParams(-1, dp(58)));

        LinearLayout rowOne = buttonRow(root);
        undoButton = addButton(rowOne, "Undo", new View.OnClickListener() {
            @Override public void onClick(View view) { undoMove(); }
        }, 1f, dp(44));
        addButton(rowOne, "New game", new View.OnClickListener() {
            @Override public void onClick(View view) { confirmNewGame(); }
        }, 1.15f, dp(44));
        addButton(rowOne, "Flip board", new View.OnClickListener() {
            @Override public void onClick(View view) {
                boardView.flipBoard();
            }
        }, 1.15f, dp(44));

        LinearLayout rowTwo = buttonRow(root);
        drawButton = addButton(rowTwo, "Claim draw", new View.OnClickListener() {
            @Override public void onClick(View view) { claimDraw(); }
        }, 1.2f, dp(44));
        addButton(rowTwo, "Resign", new View.OnClickListener() {
            @Override public void onClick(View view) { confirmResign(); }
        }, 0.9f, dp(44));
        addButton(rowTwo, "Copy PGN", new View.OnClickListener() {
            @Override public void onClick(View view) { copyPgn(); }
        }, 1f, dp(44));

        setContentView(root);
    }

    private LinearLayout buttonRow(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        root.addView(row, new LinearLayout.LayoutParams(-1, dp(50)));
        return row;
    }

    private Button addButton(LinearLayout row, String label, View.OnClickListener listener,
                             float weight, int height) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12f);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(3), 0, dp(3), 0);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, height, weight);
        params.setMargins(dp(3), dp(2), dp(3), dp(2));
        row.addView(button, params);
        return button;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(size);
        result.setTextColor(color);
        if (bold) result.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return result;
    }

    @Override
    public void onMoveChosen(ChessGameEngine.Move move) {
        commitMove(move);
    }

    @Override
    public void onPromotionRequired(final List<ChessGameEngine.Move> choices) {
        if (choices == null || choices.isEmpty()) return;
        final int from = choices.get(0).from;
        final int to = choices.get(0).to;
        final String[] labels = {"Queen", "Rook", "Bishop", "Knight"};
        final char[] pieces = {'q', 'r', 'b', 'n'};
        new AlertDialog.Builder(this)
                .setTitle("Promote pawn to")
                .setItems(labels, (dialog, which) -> {
                    if (which >= 0 && which < pieces.length && game.play(from, to, pieces[which])) {
                        afterMove();
                    }
                })
                .setOnCancelListener(dialog -> boardView.refreshBoard())
                .show();
    }

    private void commitMove(ChessGameEngine.Move move) {
        if (move != null && game.play(move.from, move.to, move.promotion)) afterMove();
    }

    private void afterMove() {
        ChessProgressStore.save(this, game);
        gameOverDialogShown = false;
        updateScreen();
        maybeShowGameOver();
    }

    private void updateScreen() {
        statusText.setText(game.getStatusText());
        moveText.setText(game.getMoveHistoryText());
        undoButton.setEnabled(game.canUndo());
        drawButton.setVisibility(game.canClaimDraw() ? View.VISIBLE : View.GONE);
        boardView.refreshBoard();
    }

    private void undoMove() {
        if (game.undo()) {
            ChessProgressStore.save(this, game);
            gameOverDialogShown = false;
            updateScreen();
        }
    }

    private void confirmNewGame() {
        new AlertDialog.Builder(this)
                .setTitle("Start a new chess game?")
                .setMessage("The current move history will be cleared.")
                .setNegativeButton("Keep playing", null)
                .setPositiveButton("New game", (dialog, which) -> {
                    game.restart();
                    ChessProgressStore.clear(this);
                    gameOverDialogShown = false;
                    updateScreen();
                })
                .show();
    }

    private void claimDraw() {
        if (!game.claimDraw()) {
            Toast.makeText(this, "No draw claim is available in this position.", Toast.LENGTH_SHORT).show();
            updateScreen();
            return;
        }
        ChessProgressStore.save(this, game);
        updateScreen();
        maybeShowGameOver();
    }

    private void confirmResign() {
        if (game.isGameOver()) return;
        new AlertDialog.Builder(this)
                .setTitle("Resign this game?")
                .setMessage("The other side will be awarded the win.")
                .setNegativeButton("Continue playing", null)
                .setPositiveButton("Resign", (dialog, which) -> {
                    game.resign();
                    ChessProgressStore.save(this, game);
                    updateScreen();
                    maybeShowGameOver();
                })
                .show();
    }

    private void copyPgn() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("Anniehilation Chess PGN", game.getPgn()));
        Toast.makeText(this, "PGN copied to clipboard.", Toast.LENGTH_SHORT).show();
    }

    private void maybeShowGameOver() {
        if (!game.isGameOver() || gameOverDialogShown) return;
        gameOverDialogShown = true;
        new AlertDialog.Builder(this)
                .setTitle("Game finished")
                .setMessage(game.getStatusText())
                .setPositiveButton("New game", (dialog, which) -> {
                    game.restart();
                    ChessProgressStore.clear(this);
                    gameOverDialogShown = false;
                    updateScreen();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        ChessProgressStore.save(this, game);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
