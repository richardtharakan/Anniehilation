package com.rictharakan.anniehilation.chess;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Royal, slightly silly Chess arcade screen. White is the player; Berthu controls Black. */
public final class ChessActivity extends Activity implements ChessBoardView.Listener {

    private static final int NAVY = 0xFF081522;
    private static final int PANEL = 0xFF15283B;
    private static final int PANEL_LIGHT = 0xFF20384E;
    private static final int AQUA = 0xFF6CE4D8;
    private static final int GOLD = 0xFFFFD27A;
    private static final int CREAM = 0xFFF6E9D1;
    private static final int MUTED = 0xFFB7C9D8;

    private final ExecutorService berthuExecutor = Executors.newSingleThreadExecutor();
    private final Random banterRandom = new Random();

    private ChessEngine engine;
    private ChessBoardView boardView;
    private TextView statusText;
    private TextView moveText;
    private TextView berthuMood;
    private TextView berthuTitle;
    private TextView berthuDetailText;
    private TextView modeBadge;
    private Button vsBerthuButton;
    private Button passPlayButton;
    private Button difficultyButton;
    private Button undoButton;

    private boolean vsBerthu;
    private boolean berthuThinking;
    private boolean resultDialogShown;
    private boolean destroyed;
    private int difficulty = BerthuAI.MISCHIEF;
    private int aiRequestToken;

    private static final String[] BERTHU_WAITING = {
            "Berthu is plotting with both paws…",
            "A suspicious amount of purring. Think carefully.",
            "The whiskered grandmaster is calculating snacks.",
            "Berthu says: no take-backsies. (He is lying.)"
    };
    private static final String[] BERTHU_AFTER_MOVE = {
            "Mrow. Your move, human.",
            "A paw-sition of absolute power.",
            "Berthu has moved. Please admire the whiskers.",
            "That was a highly meow-tivated decision.",
            "The cat has left the opening. The cat has no regrets.",
            "Check? More like… check-mew."
    };
    private static final String[] BERTHU_WIN = {
            "Berthu wins. He would like this recorded as a historic purrformance.",
            "Checkmate! The cat has claimed the kingdom and the warm chair.",
            "Berthu wins. He is accepting treats instead of a trophy."
    };
    private static final String[] ANNIE_WIN = {
            "Annie wins! Berthu demands a rematch and a snack.",
            "Checkmate! The royal crown stays with Annie. Berthu is mildly offended.",
            "Victory! Berthu says the board was slippery."
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        engine = new ChessEngine();
        buildScreen();
        updateScreen();
        boardView.post(this::showModePicker);
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(6), dp(10), dp(8));
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF07111E, 0xFF14283B, 0xFF101D30, 0xFF07111D});
        root.setBackground(background);

        // Ornate header with the actual Anniehilation portrait.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView portrait = new ImageView(this);
        int icon = getResources().getIdentifier("ic_launcher_round", "mipmap", getPackageName());
        if (icon == 0) icon = getResources().getIdentifier("ic_launcher", "mipmap", getPackageName());
        if (icon != 0) portrait.setImageResource(icon);
        portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);
        portrait.setBackground(circleDrawable(0xFF17283B, GOLD, dp(2)));
        portrait.setClipToOutline(true);
        header.addView(portrait, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(10), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText("ANNIE'S ROYAL CHESS");
        title.setTextColor(CREAM);
        title.setTextSize(19f);
        title.setLetterSpacing(0.045f);
        title.setTypeface(Typeface.create("serif", Typeface.BOLD));
        heading.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView subtitle = new TextView(this);
        subtitle.setText("A LITTLE STRATEGY. A LOT OF ATTITUDE.");
        subtitle.setTextColor(AQUA);
        subtitle.setTextSize(9f);
        subtitle.setLetterSpacing(0.075f);
        subtitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        heading.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView pawBadge = new TextView(this);
        pawBadge.setText("♟  ♡");
        pawBadge.setTextSize(19f);
        pawBadge.setTextColor(GOLD);
        pawBadge.setGravity(Gravity.CENTER);
        pawBadge.setBackground(pillDrawable(0xFF28384A, 0xFF6A5636));
        header.addView(pawBadge, new LinearLayout.LayoutParams(dp(54), dp(36)));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(54)));

        // Mode selector: player can switch between Berthu and local pass-and-play.
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setGravity(Gravity.CENTER);
        modeRow.setPadding(0, dp(4), 0, dp(4));
        vsBerthuButton = modeButton("🐾  Play Berthu", v -> confirmModeChange(true));
        passPlayButton = modeButton("♟  Two Players", v -> confirmModeChange(false));
        modeRow.addView(vsBerthuButton, new LinearLayout.LayoutParams(0, dp(39), 1f));
        LinearLayout.LayoutParams pvpParams = new LinearLayout.LayoutParams(0, dp(39), 1f);
        pvpParams.leftMargin = dp(7);
        modeRow.addView(passPlayButton, pvpParams);
        root.addView(modeRow, new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        modeBadge = new TextView(this);
        modeBadge.setTextColor(GOLD);
        modeBadge.setTextSize(9.5f);
        modeBadge.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        modeBadge.setLetterSpacing(0.07f);
        modeBadge.setPadding(dp(10), dp(4), dp(10), dp(4));
        modeBadge.setBackground(pillDrawable(0xFF25384B, 0xFF6F5934));
        infoRow.addView(modeBadge, new LinearLayout.LayoutParams(0, dp(25), 1f));
        difficultyButton = new Button(this);
        difficultyButton.setAllCaps(false);
        difficultyButton.setTextSize(10f);
        difficultyButton.setTextColor(CREAM);
        difficultyButton.setMinHeight(dp(30));
        difficultyButton.setMinimumHeight(dp(30));
        difficultyButton.setPadding(dp(7), 0, dp(7), 0);
        difficultyButton.setBackground(pillDrawable(0xFF273A4D, 0xFF668096));
        difficultyButton.setOnClickListener(v -> showDifficultyPicker());
        infoRow.addView(difficultyButton, new LinearLayout.LayoutParams(-2, dp(30)));
        root.addView(infoRow, new LinearLayout.LayoutParams(-1, dp(32)));

        // Berthu's top-of-board player panel.
        LinearLayout berthuCard = playerCard(
                "BERTHU  ·  BLACK",
                "The whiskered challenger",
                "berthu_face_logo", true);
        root.addView(berthuCard, new LinearLayout.LayoutParams(-1, dp(52)));

        statusText = new TextView(this);
        statusText.setTextColor(GOLD);
        statusText.setTextSize(12f);
        statusText.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        statusText.setGravity(Gravity.CENTER);
        statusText.setBackground(pillDrawable(0xB91B2B3D, 0xFF66553B));
        statusText.setPadding(dp(8), dp(3), dp(8), dp(3));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, dp(31));
        statusParams.topMargin = dp(5);
        statusParams.bottomMargin = dp(3);
        root.addView(statusText, statusParams);

        boardView = new ChessBoardView(this);
        boardView.setEngine(engine);
        boardView.setListener(this);
        LinearLayout.LayoutParams boardParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        boardParams.topMargin = dp(1);
        boardParams.bottomMargin = dp(2);
        root.addView(boardView, boardParams);

        // Annie's panel mirrors Berthu's panel under the White side of the board.
        LinearLayout annieCard = playerCard(
                "ANNIE  ·  WHITE",
                "Your move, Your Majesty",
                "royal_chess_annie_badge", false);
        root.addView(annieCard, new LinearLayout.LayoutParams(-1, dp(52)));

        moveText = new TextView(this);
        moveText.setTextColor(MUTED);
        moveText.setTextSize(10f);
        moveText.setGravity(Gravity.CENTER);
        moveText.setPadding(0, dp(2), 0, dp(2));
        root.addView(moveText, new LinearLayout.LayoutParams(-1, dp(22)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        undoButton = addAction(actions, "Undo turn", v -> undoMove(), 0xFF263D55, 0xFF6886A1);
        addAction(actions, "New match", v -> confirmNewGame(), 0xFF745226, GOLD);
        addAction(actions, "Back", v -> finish(), 0xFF493044, 0xFF9E7890);
        root.addView(actions, new LinearLayout.LayoutParams(-1, dp(45)));

        setContentView(root);
    }

    private LinearLayout playerCard(String name, String detail, String resourceName, boolean berthu) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(8), dp(3), dp(10), dp(3));
        card.setBackground(roundDrawable(0xD91A2D42, berthu ? 0xFF805A35 : 0xFF78613A, dp(13), dp(1)));

        ImageView avatar = new ImageView(this);
        int res;
        if ("ic_launcher_round".equals(resourceName)) {
            res = getResources().getIdentifier(resourceName, "mipmap", getPackageName());
        } else {
            res = getResources().getIdentifier(resourceName, "drawable", getPackageName());
        }
        if (res != 0) avatar.setImageResource(res);
        else avatar.setImageResource(android.R.drawable.sym_def_app_icon);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(circleDrawable(0xFF26394C, berthu ? 0xFFFFB65D : GOLD, dp(1.7f)));
        avatar.setClipToOutline(true);
        card.addView(avatar, new LinearLayout.LayoutParams(dp(43), dp(43)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(9), 0, 0, 0);
        TextView playerName = new TextView(this);
        playerName.setText(name);
        playerName.setTextColor(berthu ? 0xFFFFC17D : 0xFFFFE3A8);
        playerName.setTextSize(11f);
        playerName.setLetterSpacing(0.075f);
        playerName.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        labels.addView(playerName, new LinearLayout.LayoutParams(-1, -2));
        TextView playerDetail = new TextView(this);
        playerDetail.setText(detail);
        playerDetail.setTextColor(MUTED);
        playerDetail.setTextSize(9.5f);
        labels.addView(playerDetail, new LinearLayout.LayoutParams(-1, -2));
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView mood = new TextView(this);
        mood.setText(berthu ? "🐾 PURR-PLAYER" : "♛ ROYAL SIDE");
        mood.setTextColor(berthu ? 0xFFFFC17D : AQUA);
        mood.setTextSize(8f);
        mood.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
        mood.setGravity(Gravity.CENTER);
        mood.setPadding(dp(7), dp(5), dp(7), dp(5));
        mood.setBackground(pillDrawable(0xFF26394A, berthu ? 0xFF906A45 : 0xFF397C78));
        card.addView(mood, new LinearLayout.LayoutParams(-2, dp(27)));
        if (berthu) {
            berthuMood = mood;
            berthuTitle = playerName;
            berthuDetailText = playerDetail;
        }
        return card;
    }

    private Button modeButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(11f);
        button.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
        button.setTextColor(CREAM);
        button.setMinHeight(dp(35));
        button.setMinimumHeight(dp(35));
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setOnClickListener(listener);
        return button;
    }

    private Button addAction(LinearLayout row, String label, View.OnClickListener listener,
                             int fill, int stroke) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(CREAM);
        button.setTextSize(10.5f);
        button.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
        button.setBackground(roundDrawable(fill, stroke, dp(12), dp(1)));
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setMinHeight(dp(40));
        button.setMinimumHeight(dp(40));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(41), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        row.addView(button, params);
        return button;
    }

    private GradientDrawable roundDrawable(int fill, int stroke, float radius, float strokeWidth) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(radius);
        shape.setStroke(Math.max(1, Math.round(strokeWidth)), stroke);
        return shape;
    }

    private GradientDrawable circleDrawable(int fill, int stroke, int strokeWidth) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(fill);
        shape.setStroke(Math.max(1, strokeWidth), stroke);
        return shape;
    }

    private GradientDrawable pillDrawable(int fill, int stroke) {
        return roundDrawable(fill, stroke, dp(30), dp(1));
    }

    private void confirmModeChange(boolean chooseBerthu) {
        if (chooseBerthu == vsBerthu) {
            if (chooseBerthu) showDifficultyPicker();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Change the chess table?")
                .setMessage("Starting the other mode will reset this match.")
                .setNegativeButton("Stay here", null)
                .setPositiveButton("Switch mode", (dialog, which) -> {
                    if (chooseBerthu) showDifficultyPicker();
                    else startMatch(false, difficulty);
                })
                .show();
    }

    /** Minimal glass-style table selector; only the match mode changes here. */
    private void showModePicker() {
        Dialog dialog = new Dialog(this);
        LinearLayout shell = glassDialogShell();
        addGlassDialogHeading(shell, "ROYAL CHESS", "Choose your table", "Same board. A different kind of trouble.");
        addDialogGap(shell, 16);
        shell.addView(glassChoice(dialog, "🐾", "Play against Berthu",
                "A clever cat. Three difficulty moods.", GOLD,
                this::showDifficultyPicker), new LinearLayout.LayoutParams(-1, dp(68)));
        addDialogGap(shell, 9);
        shell.addView(glassChoice(dialog, "♟", "Two players",
                "Pass and play on one phone.", AQUA,
                () -> startMatch(false, difficulty)), new LinearLayout.LayoutParams(-1, dp(68)));
        addDialogGap(shell, 8);
        addDialogCancel(shell, dialog, "NOT NOW");
        showGlassDialog(dialog, shell);
    }

    /** Three compact difficulty choices, styled to match the glass mode picker. */
    private void showDifficultyPicker() {
        Dialog dialog = new Dialog(this);
        LinearLayout shell = glassDialogShell();
        addGlassDialogHeading(shell, "BERTHU'S MOOD", "Set the challenge", "How fiercely should the royal cat play?");
        addDialogGap(shell, 15);
        shell.addView(glassChoice(dialog, "☁", "Chill",
                "Gentle paws · a relaxed warm-up", difficulty == BerthuAI.CHILL ? GOLD : 0xFF6C9BA7,
                () -> startMatch(true, BerthuAI.CHILL)), new LinearLayout.LayoutParams(-1, dp(62)));
        addDialogGap(shell, 8);
        shell.addView(glassChoice(dialog, "🐾", "Mischief",
                "Thinks ahead · balanced play", difficulty == BerthuAI.MISCHIEF ? GOLD : 0xFF6C9BA7,
                () -> startMatch(true, BerthuAI.MISCHIEF)), new LinearLayout.LayoutParams(-1, dp(62)));
        addDialogGap(shell, 8);
        shell.addView(glassChoice(dialog, "♛", "Royal Menace",
                "Sharp claws · the toughest opponent", difficulty == BerthuAI.ROYAL_MENACE ? GOLD : 0xFF6C9BA7,
                () -> startMatch(true, BerthuAI.ROYAL_MENACE)), new LinearLayout.LayoutParams(-1, dp(62)));
        addDialogGap(shell, 8);
        addDialogCancel(shell, dialog, "CANCEL");
        showGlassDialog(dialog, shell);
    }

    private LinearLayout glassDialogShell() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(18), dp(18), dp(14));
        GradientDrawable glass = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xE51B3047, 0xD814263A, 0xE30B192A});
        glass.setCornerRadius(dp(24));
        glass.setStroke(dp(1), 0xAA6CE4D8);
        shell.setBackground(glass);
        shell.setElevation(dp(18));
        return shell;
    }

    private void addGlassDialogHeading(LinearLayout shell, String eyebrow, String title, String subtitle) {
        TextView label = new TextView(this);
        label.setText(eyebrow);
        label.setTextColor(AQUA);
        label.setTextSize(9f);
        label.setLetterSpacing(0.16f);
        label.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
        shell.addView(label, new LinearLayout.LayoutParams(-1, -2));

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(CREAM);
        heading.setTextSize(21f);
        heading.setTypeface(Typeface.create("serif", Typeface.BOLD));
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.topMargin = dp(4);
        shell.addView(heading, headingParams);

        TextView hint = new TextView(this);
        hint.setText(subtitle);
        hint.setTextColor(MUTED);
        hint.setTextSize(11f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(3);
        shell.addView(hint, hintParams);
    }

    private View glassChoice(Dialog dialog, String symbol, String title, String detail,
                             int accent, Runnable action) {
        LinearLayout choice = new LinearLayout(this);
        choice.setOrientation(LinearLayout.HORIZONTAL);
        choice.setGravity(Gravity.CENTER_VERTICAL);
        choice.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xD32B4055, 0xB51A2B40});
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), accent == GOLD ? 0xCCFFD27A : 0x806CE4D8);
        choice.setBackground(background);
        choice.setClickable(true);
        choice.setFocusable(true);

        TextView icon = new TextView(this);
        icon.setText(symbol);
        icon.setTextColor(accent);
        icon.setTextSize(23f);
        icon.setGravity(Gravity.CENTER);
        choice.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(42)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, 0, 0);
        TextView name = new TextView(this);
        name.setText(title);
        name.setTextColor(CREAM);
        name.setTextSize(13f);
        name.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
        copy.addView(name, new LinearLayout.LayoutParams(-1, -2));
        TextView description = new TextView(this);
        description.setText(detail);
        description.setTextColor(MUTED);
        description.setTextSize(10f);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(2);
        copy.addView(description, detailParams);
        choice.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(accent);
        arrow.setTextSize(25f);
        arrow.setGravity(Gravity.CENTER);
        choice.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(38)));
        choice.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
        return choice;
    }

    private void addDialogGap(LinearLayout shell, int heightDp) {
        shell.addView(new View(this), new LinearLayout.LayoutParams(-1, dp(heightDp)));
    }

    private void addDialogCancel(LinearLayout shell, Dialog dialog, String label) {
        TextView cancel = new TextView(this);
        cancel.setText(label);
        cancel.setTextColor(MUTED);
        cancel.setTextSize(9f);
        cancel.setLetterSpacing(0.14f);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, dp(7), 0, dp(3));
        cancel.setClickable(true);
        cancel.setFocusable(true);
        cancel.setOnClickListener(v -> dialog.dismiss());
        shell.addView(cancel, new LinearLayout.LayoutParams(-1, dp(30)));
    }

    private void showGlassDialog(Dialog dialog, View content) {
        dialog.setContentView(content);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = Math.min(dp(420), getResources().getDisplayMetrics().widthPixels - dp(32));
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.dimAmount = 0.72f;
            window.setAttributes(params);
        }
    }

    private void startMatch(boolean againstBerthu, int selectedDifficulty) {
        aiRequestToken++;
        berthuThinking = false;
        vsBerthu = againstBerthu;
        difficulty = selectedDifficulty;
        engine.reset();
        resultDialogShown = false;
        boardView.setEngine(engine);
        boardView.setInputEnabled(true);
        updateModeControls();
        updateScreen();
        if (berthuMood != null) {
            berthuMood.setText(vsBerthu ? "🐾 READY TO POUNCE" : "♟ PLAYER TWO");
        }
    }

    private void updateModeControls() {
        if (berthuTitle != null) {
            berthuTitle.setText(vsBerthu ? "BERTHU  ·  BLACK" : "BLACK  ·  PLAYER 2");
            berthuDetailText.setText(vsBerthu
                    ? "The whiskered computer challenger"
                    : "Pass the phone after White's move");
            if (!vsBerthu) berthuMood.setText("♟ PLAYER TWO");
        }
        if (modeBadge != null) {
            modeBadge.setText(vsBerthu ? "WHITE: YOU  ·  BLACK: BERTHU" : "PASS & PLAY  ·  LOCAL MATCH");
        }
        if (difficultyButton != null) {
            difficultyButton.setVisibility(vsBerthu ? View.VISIBLE : View.GONE);
            difficultyButton.setText("🐾 " + difficultyName());
        }
        if (vsBerthuButton != null) {
            vsBerthuButton.setBackground(roundDrawable(
                    vsBerthu ? 0xFF6B4A22 : 0xFF26394D,
                    vsBerthu ? GOLD : 0xFF536E85, dp(12), dp(vsBerthu ? 2 : 1)));
        }
        if (passPlayButton != null) {
            passPlayButton.setBackground(roundDrawable(
                    !vsBerthu ? 0xFF23605D : 0xFF26394D,
                    !vsBerthu ? AQUA : 0xFF536E85, dp(12), dp(!vsBerthu ? 2 : 1)));
        }
    }

    private String difficultyName() {
        if (difficulty == BerthuAI.CHILL) return "Chill";
        if (difficulty == BerthuAI.ROYAL_MENACE) return "Royal Menace";
        return "Mischief";
    }

    private void confirmNewGame() {
        new AlertDialog.Builder(this)
                .setTitle("A fresh royal rumble?")
                .setMessage("Berthu promises to behave. He is probably lying.")
                .setNegativeButton("Keep playing", null)
                .setPositiveButton("Choose match", (dialog, which) -> showModePicker())
                .show();
    }

    @Override
    public void onMoveSelected(ChessEngine.Move move) {
        if (berthuThinking || isTerminalPosition() || (vsBerthu && !engine.isWhiteToMove())) return;
        if (!engine.makeMove(move)) {
            boardView.clearSelection();
            return;
        }
        resultDialogShown = false;
        boardView.refreshPosition(true);
        updateScreen();
        if (finishIfGameOver()) return;
        if (vsBerthu && !engine.isWhiteToMove()) requestBerthuMove();
    }

    @Override
    public void onPromotionRequired(List<ChessEngine.Move> choices) {
        final ChessEngine.Move[] options = new ChessEngine.Move[4];
        for (ChessEngine.Move move : choices) {
            char promotion = Character.toLowerCase(move.promotion);
            if (promotion == 'q') options[0] = move;
            else if (promotion == 'r') options[1] = move;
            else if (promotion == 'b') options[2] = move;
            else if (promotion == 'n') options[3] = move;
        }
        String[] labels = {"Queen · Annie", "Rook", "Bishop", "Knight"};
        new AlertDialog.Builder(this)
                .setTitle("A royal promotion!")
                .setItems(labels, (dialog, which) -> {
                    if (options[which] != null) onMoveSelected(options[which]);
                })
                .setNegativeButton("Cancel", (dialog, which) -> boardView.clearSelection())
                .show();
    }

    @Override
    public void onInvalidMove() {
        moveText.setText(vsBerthu
                ? "Mrow? That square is not legal, human."
                : "That move is not legal. Try a highlighted square.");
    }

    private void requestBerthuMove() {
        if (!vsBerthu || engine.isWhiteToMove() || berthuThinking || destroyed) return;
        final int request = ++aiRequestToken;
        final ChessEngine searchPosition = new ChessEngine(engine);
        berthuThinking = true;
        boardView.setInputEnabled(false);
        boardView.clearSelection();
        if (berthuMood != null) berthuMood.setText("🐾 THINKING…");
        moveText.setText(BERTHU_WAITING[banterRandom.nextInt(BERTHU_WAITING.length)]);
        updateScreen();

        berthuExecutor.execute(() -> {
            ChessEngine.Move chosen = BerthuAI.chooseMove(searchPosition, difficulty);
            runOnUiThread(() -> {
                if (destroyed || isFinishing() || request != aiRequestToken) return;
                berthuThinking = false;
                if (chosen != null && engine.makeMove(chosen)) {
                    boardView.refreshPosition(true);
                    updateScreen();
                    if (!finishIfGameOver()) {
                        if (berthuMood != null) berthuMood.setText("🐾 VERY PLEASED");
                        moveText.setText(BERTHU_AFTER_MOVE[banterRandom.nextInt(BERTHU_AFTER_MOVE.length)]);
                    }
                } else {
                    boardView.refreshPosition(false);
                    updateScreen();
                    if (berthuMood != null) berthuMood.setText("🐾 CONFUSED MEOW");
                    moveText.setText("Berthu lost his place in the whisker calculations. Try again.");
                }
                boardView.setInputEnabled(vsBerthu && engine.isWhiteToMove()
                        && !isTerminalPosition());
                maybeShowResultDialog();
            });
        });
    }

    private void undoMove() {
        if (berthuThinking) return;
        if (!engine.canUndo()) {
            moveText.setText("No moves to undo yet. The board is pristine!");
            return;
        }
        int plies = 1;
        if (vsBerthu && engine.isWhiteToMove() && engine.getMoveCount() >= 2) {
            plies = 2; // Undo the player's move and Berthu's reply as one turn.
        }
        for (int i = 0; i < plies && engine.canUndo(); i++) engine.undo();
        resultDialogShown = false;
        boardView.refreshPosition(false);
        boardView.setInputEnabled(!vsBerthu || engine.isWhiteToMove());
        if (berthuMood != null) berthuMood.setText("🐾 I SAW THAT");
        updateScreen();
    }

    private void updateScreen() {
        if (statusText == null) return;
        String status = engine.getStatusText();
        if (berthuThinking) status = "BERTHU IS THINKING  ·  HOLD YOUR HORSES";
        else if (vsBerthu && engine.isWhiteToMove() && !isTerminalPosition()) status = "ANNIE'S TURN  ·  WHITE TO MOVE";
        else if (vsBerthu && !engine.isWhiteToMove() && !isTerminalPosition()) status = "BERTHU'S TURN  ·  BLACK TO MOVE";
        statusText.setText(status);
        if (engine.getMoveCount() == 0) {
            moveText.setText(vsBerthu ? "You are White. Berthu is Black. Make the first move!"
                    : "Two-player royal rumble · White starts at the bottom");
        } else if (!berthuThinking) {
            ChessEngine.Move last = engine.getLastMove();
            moveText.setText("MOVE " + engine.getFullmoveNumber() + "   ·   LAST: "
                    + (last == null ? "—" : last.toString()));
        }
        if (undoButton != null) undoButton.setEnabled(engine.canUndo() && !berthuThinking);
        updateModeControls();
    }

    private boolean finishIfGameOver() {
        if (isTerminalPosition()) {
            boardView.setInputEnabled(false);
            maybeShowResultDialog();
            return true;
        }
        return false;
    }

    private boolean isTerminalPosition() {
        return engine.isCheckmate() || engine.isStalemate()
                || engine.isInsufficientMaterial() || engine.isDrawByFiftyMoveRule();
    }

    private void maybeShowResultDialog() {
        if (resultDialogShown || !isTerminalPosition()) return;
        resultDialogShown = true;
        String message = engine.getStatusText();
        if (engine.isCheckmate() && vsBerthu) {
            message = engine.isWhiteToMove()
                    ? BERTHU_WIN[banterRandom.nextInt(BERTHU_WIN.length)]
                    : ANNIE_WIN[banterRandom.nextInt(ANNIE_WIN.length)];
        }
        String title = engine.isCheckmate() ? "Checkmate!" : "A royal draw";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message + "\n\nFancy another round?")
                .setNegativeButton("Admire the board", null)
                .setPositiveButton("New match", (dialog, which) -> showModePicker())
                .show();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        aiRequestToken++;
        berthuExecutor.shutdownNow();
        super.onDestroy();
    }
}
