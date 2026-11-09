package com.rictharakan.anniehilation.annienvasion;

import android.app.Activity;
import android.os.Bundle;
import android.content.pm.ActivityInfo;

/** Standalone Annienvasion game screen. */
public final class AnnienvasionActivity extends Activity implements AnnienvasionView.Listener {
    private AnnienvasionView gameView;
    private int stage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        stage = AnnienvasionProgress.loadUnlockedStage(this);
        gameView = new AnnienvasionView(this, stage);
        gameView.setListener(this);
        setContentView(gameView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null) gameView.resumeGame();
    }

    @Override
    protected void onPause() {
        if (gameView != null) gameView.pauseGame();
        super.onPause();
    }

    @Override
    public void onStageWon(int completedStage) {
        AnnienvasionProgress.saveUnlockedStage(this, Math.min(50, completedStage + 1));
    }

    @Override
    public void onExitRequested() {
        finish();
    }
}
