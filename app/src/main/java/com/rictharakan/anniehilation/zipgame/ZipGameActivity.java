package com.rictharakan.anniehilation.zipgame;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Standalone, visually polished native Activity for Anniehilation's Zip Trail. */
public class ZipGameActivity extends Activity implements ZipGameView.Listener {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!manualPaused && !lifecyclePaused && !roundFinished) {
                secondsRemaining--;
                updateHeader();
                if (secondsRemaining <= 0) {
                    onTimeExpired();
                    return;
                }
            }
            if (!roundFinished && !manualPaused && !lifecyclePaused) {
                handler.postDelayed(this, 1000L);
            }
        }
    };

    private ZipGameView gameView;
    private TextView levelText;
    private TextView scoreText;
    private TextView timerText;
    private TextView statusText;
    private Button pauseButton;

    private int level;
    private int score;
    private int secondsRemaining;
    private long boardSeed;
    private boolean manualPaused;
    private boolean lifecyclePaused;
    private boolean roundFinished;
    private int nextLevelToSave;

    private static final int NAVY = Color.rgb(11, 20, 36);
    private static final int PANEL = Color.rgb(24, 42, 64);
    private static final int AQUA = Color.rgb(72, 224, 215);
    private static final int GOLD = Color.rgb(255, 205, 112);
    private static final int TEXT_MUTED = Color.rgb(174, 198, 219);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        level = ZipProgressStore.loadLevel(this);
        score = ZipProgressStore.loadScore(this);
        boardSeed = ZipProgressStore.loadSeed(this);
        if (boardSeed == 0L) boardSeed = System.currentTimeMillis();
        buildScreen();
        startLevel(false);
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable rootBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] { 0xFF091322, 0xFF13263E, 0xFF0C1A2D });
        root.setBackground(rootBackground);

        // Game title block.
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(dp(3), 0, dp(3), 0);

        ImageView titleIcon = new ImageView(this);
        int iconId = getResources().getIdentifier("ic_launcher_round", "mipmap", getPackageName());
        if (iconId != 0) titleIcon.setImageResource(iconId);
        titleIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        titleRow.addView(titleIcon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);
        titleStack.setPadding(dp(10), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText("ZIP TRAIL");
        title.setTextColor(Color.WHITE);
        title.setTextSize(23f);
        title.setLetterSpacing(0.08f);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
        titleStack.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("BERTHU'S SEARCH FOR ANNIE");
        subtitle.setTextColor(AQUA);
        subtitle.setTextSize(10f);
        subtitle.setLetterSpacing(0.11f);
        subtitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        titleStack.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        titleRow.addView(titleStack, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(titleRow, new LinearLayout.LayoutParams(-1, dp(48)));

        // Three compact metric cards.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, dp(8), 0, dp(7));
        levelText = metricCard("LEVEL 1", AQUA);
        scoreText = metricCard("SCORE 0", Color.WHITE);
        timerText = metricCard("90s", GOLD);
        addMetric(header, levelText);
        addMetric(header, scoreText);
        addMetric(header, timerText);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(53)));

        // Instruction/status panel.
        statusText = new TextView(this);
        statusText.setText("Start at Berthu, then trace every cell to reach Annie.");
        statusText.setTextColor(TEXT_MUTED);
        statusText.setTextSize(12.5f);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(dp(10), dp(5), dp(10), dp(5));
        statusText.setMaxLines(2);
        statusText.setBackground(rounded(PANEL, 0xFF36516E, 1, 13));
        root.addView(statusText, new LinearLayout.LayoutParams(-1, dp(48)));

        gameView = new ZipGameView(this);
        gameView.setListener(this);
        gameView.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams boardParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        boardParams.topMargin = dp(4);
        boardParams.bottomMargin = dp(3);
        root.addView(gameView, boardParams);

        LinearLayout firstRow = buttonRow(root);
        addButton(firstRow, "Undo", new View.OnClickListener() {
            @Override public void onClick(View view) { gameView.undo(); }
        }, 0xFF243D59, 0xFF49647E);
        addButton(firstRow, "Hint", new View.OnClickListener() {
            @Override public void onClick(View view) {
                gameView.applyHint();
                statusText.setText("A step has been added to guide Berthu along a valid route.");
            }
        }, 0xFF126F75, 0xFF47D8D0);
        addButton(firstRow, "Clear", new View.OnClickListener() {
            @Override public void onClick(View view) {
                gameView.clearPath();
                statusText.setText("Path cleared. Berthu is ready to search for Annie again.");
            }
        }, 0xFF243D59, 0xFF49647E);

        LinearLayout secondRow = buttonRow(root);
        pauseButton = addButton(secondRow, "Pause", new View.OnClickListener() {
            @Override public void onClick(View view) { togglePause(); }
        }, 0xFF805526, 0xFFFFCF78);
        addButton(secondRow, "Restart", new View.OnClickListener() {
            @Override public void onClick(View view) { confirmRestart(); }
        }, 0xFF243D59, 0xFF49647E);
        addButton(secondRow, "Back", new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        }, 0xFF4A2C3A, 0xFF8E5264);

        setContentView(root);
    }

    private TextView metricCard(String value, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(12f);
        text.setGravity(Gravity.CENTER);
        text.setTypeface(android.graphics.Typeface.create("sans-serif-bold", android.graphics.Typeface.BOLD));
        text.setBackground(rounded(0xCC1A2D45, 0xFF324B66, 1, 12));
        return text;
    }

    private void addMetric(LinearLayout row, TextView text) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        row.addView(text, params);
    }

    private LinearLayout buttonRow(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        root.addView(row, new LinearLayout.LayoutParams(-1, dp(50)));
        return row;
    }

    private Button addButton(LinearLayout row, String label, View.OnClickListener listener,
                             int fill, int stroke) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13f);
        button.setTextColor(Color.WHITE);
        button.setTypeface(android.graphics.Typeface.create("sans-serif-bold", android.graphics.Typeface.BOLD));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(3), 0, dp(3), 0);
        button.setMinHeight(dp(42));
        button.setMinimumHeight(dp(42));
        button.setBackground(rounded(fill, stroke, 1, 13));
        button.setElevation(dp(2));
        button.setOnClickListener(listener);
        button.setContentDescription(label);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
        params.setMargins(dp(3), dp(2), dp(3), dp(2));
        row.addView(button, params);
        return button;
    }

    private GradientDrawable rounded(int fill, int stroke, int strokeWidthDp, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeWidthDp > 0) drawable.setStroke(dp(strokeWidthDp), stroke);
        return drawable;
    }

    private void startLevel(boolean makeNewSeed) {
        handler.removeCallbacks(ticker);
        if (makeNewSeed) boardSeed = System.nanoTime() ^ System.currentTimeMillis();
        manualPaused = false;
        lifecyclePaused = false;
        roundFinished = false;
        nextLevelToSave = 0;
        secondsRemaining = timeLimitFor(level);
        gameView.setInputEnabled(true);
        gameView.setPuzzle(ZipPuzzleGenerator.generate(level, boardSeed));
        pauseButton.setText("Pause");
        statusText.setText("Start at Berthu. Follow each number, cross no walls, and finish at Annie.");
        updateHeader();
        saveProgress();
        startTicker();
    }

    private int timeLimitFor(int currentLevel) {
        return 90 + Math.max(0, (currentLevel - 1) / 20) * 10;
    }

    private void startTicker() {
        handler.removeCallbacks(ticker);
        if (!roundFinished && !manualPaused && !lifecyclePaused) {
            handler.postDelayed(ticker, 1000L);
        }
    }

    private void togglePause() {
        if (roundFinished) return;
        manualPaused = !manualPaused;
        pauseButton.setText(manualPaused ? "Resume" : "Pause");
        statusText.setText(manualPaused ? "Paused. Your current path is preserved." : "Back in the puzzle. Find Annie!");
        if (manualPaused) handler.removeCallbacks(ticker);
        else startTicker();
    }

    private void confirmRestart() {
        new AlertDialog.Builder(this)
                .setTitle("Restart this puzzle?")
                .setMessage("Your current path will be cleared. Your level and score will remain.")
                .setNegativeButton("Keep playing", null)
                .setPositiveButton("Restart", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        startLevel(false);
                    }
                })
                .show();
    }

    @Override
    public void onMoveResult(ZipPuzzle.MoveResult result) {
        switch (result) {
            case STARTED:
                statusText.setText("Berthu is on the trail. Keep following the checkpoints!");
                break;
            case RETRACTED:
                statusText.setText("Path rewound. Choose another route.");
                break;
            case WRONG_START:
                statusText.setText("Begin by touching Berthu at checkpoint 1.");
                break;
            case NON_ADJACENT:
                statusText.setText("Follow neighbouring cells horizontally or vertically.");
                break;
            case WALL_BLOCKED:
                statusText.setText("A barrier blocks this edge. Find a way around it.");
                break;
            case WRONG_NUMBER:
                statusText.setText("The numbered checkpoints must be visited in order.");
                break;
            case PREMATURE_END:
                statusText.setText("Annie is the final stop. Visit every cell before reaching her.");
                break;
            case ALREADY_VISITED:
                statusText.setText("That cell is already part of your path.");
                break;
            default:
                break;
        }
    }

    @Override
    public void onPuzzleSolved() {
        if (roundFinished) return;
        roundFinished = true;
        handler.removeCallbacks(ticker);
        gameView.setInputEnabled(false);
        final int completedLevel = level;
        int gained = completedLevel * 10 + Math.max(0, secondsRemaining);
        score += gained;
        nextLevelToSave = completedLevel + 1;
        boardSeed = System.nanoTime() ^ System.currentTimeMillis();
        statusText.setText("Berthu found Annie! Every cell was visited in the right order.");
        updateHeader();
        saveProgress();
        new AlertDialog.Builder(this)
                .setTitle("Level " + completedLevel + " complete!")
                .setMessage("You earned " + gained + " points. Berthu and Annie are ready for another puzzle!")
                .setCancelable(false)
                .setPositiveButton("Next level", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        level = nextLevelToSave;
                        nextLevelToSave = 0;
                        startLevel(true);
                    }
                })
                .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
    }

    private void onTimeExpired() {
        if (roundFinished) return;
        roundFinished = true;
        handler.removeCallbacks(ticker);
        gameView.setInputEnabled(false);
        statusText.setText("Time is up. Restart the board and try another route.");
        new AlertDialog.Builder(this)
                .setTitle("Time's up")
                .setMessage("Your level and score are safe. Try this puzzle again?")
                .setCancelable(false)
                .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        startLevel(false);
                    }
                })
                .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
    }

    private void updateHeader() {
        if (levelText != null) levelText.setText("LEVEL  " + level);
        if (scoreText != null) scoreText.setText("SCORE  " + score);
        if (timerText != null) timerText.setText(secondsRemaining + "s");
    }

    private void saveProgress() {
        int progressLevel = nextLevelToSave > 0 ? nextLevelToSave : level;
        ZipProgressStore.save(this, progressLevel, score, boardSeed);
    }

    @Override
    protected void onPause() {
        lifecyclePaused = true;
        handler.removeCallbacks(ticker);
        saveProgress();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        lifecyclePaused = false;
        startTicker();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
