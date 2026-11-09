package com.rictharakan.anniehilation;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.rictharakan.anniehilation.chess.ChessActivity;
import com.rictharakan.anniehilation.zipgame.ZipGameActivity;
import com.rictharakan.anniehilation.annienvasion.AnnienvasionActivity;

/** Six-slot launcher for the Anniehilation game collection. */
public final class GameHubActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();
        getWindow().setStatusBarColor(0xFF071421);
        getWindow().setNavigationBarColor(0xFF091725);
        setContentView(R.layout.activity_game_hub);

        findViewById(R.id.btnHubBack).setOnClickListener(v -> finish());

        // Slot 1: the original Anniehilation platform-adventure and original logo.
        findViewById(R.id.tileAnniehilation).setOnClickListener(v ->
                startActivity(new Intent(GameHubActivity.this, GameActivity.class)));

        // Slot 2: Royal Chess v1.1, with Berthu as the computer opponent.
        findViewById(R.id.tileChess).setOnClickListener(v ->
                startActivity(new Intent(GameHubActivity.this, ChessActivity.class)));

        // Slot 3: Zip Trail v1.2 visual/touch upgrade.
        findViewById(R.id.tileZip).setOnClickListener(v ->
                startActivity(new Intent(GameHubActivity.this, ZipGameActivity.class)));

        // Slot 4: Annienvasion. Its gameplay module remains unchanged by this release.
        findViewById(R.id.tileGame04).setOnClickListener(v ->
                startActivity(new Intent(GameHubActivity.this, AnnienvasionActivity.class)));

        findViewById(R.id.tileGame05).setOnClickListener(v -> showComingSoon("Game 05"));
        findViewById(R.id.tileGame06).setOnClickListener(v -> showComingSoon("Game 06"));

        animateTiles();
    }

    private void animateTiles() {
        int[] ids = {
                R.id.tileAnniehilation, R.id.tileChess, R.id.tileZip,
                R.id.tileGame04, R.id.tileGame05, R.id.tileGame06
        };
        for (int i = 0; i < ids.length; i++) {
            View tile = findViewById(ids[i]);
            if (tile == null) continue;
            tile.setAlpha(0f);
            tile.setTranslationY(dp(10));
            tile.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * 45L)
                    .setDuration(330L)
                    .setInterpolator(new DecelerateInterpolator(1.2f))
                    .start();
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showComingSoon(String name) {
        android.widget.Toast.makeText(this, name + " is coming soon!",
                android.widget.Toast.LENGTH_SHORT).show();
    }
}
