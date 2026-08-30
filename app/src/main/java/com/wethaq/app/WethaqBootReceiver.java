package com.wethaq.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class WethaqBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (context.getSharedPreferences("wethaq", Context.MODE_PRIVATE).getString("token", "").length() < 10) return;
        Intent service = new Intent(context, WethaqMessageService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service); else context.startService(service);
    }
}
