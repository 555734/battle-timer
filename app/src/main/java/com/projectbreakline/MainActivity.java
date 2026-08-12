package com.projectbreakline;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public final class MainActivity extends Activity {
    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        gameView = new GameView(this);
        setContentView(gameView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null) gameView.resumeFromSystem();
    }

    @Override
    protected void onPause() {
        if (gameView != null) gameView.pauseFromSystem();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (gameView != null) gameView.release();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (gameView == null || !gameView.handleBack()) super.onBackPressed();
    }
}
