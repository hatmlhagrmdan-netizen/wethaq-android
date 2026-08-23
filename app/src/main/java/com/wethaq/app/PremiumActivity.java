package com.wethaq.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Stable launcher for the production Wethaq experience.
 * The previous PremiumActivity implemented a disconnected demo UI with
 * local-only buttons. The real application logic lives in MainActivity
 * (authentication, public search, contacts, conversations and backend I/O).
 * Keep the launcher thin so the installed APK always enters the real app.
 */
public final class PremiumActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
