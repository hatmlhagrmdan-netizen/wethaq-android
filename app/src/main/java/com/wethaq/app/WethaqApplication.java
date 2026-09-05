package com.wethaq.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.provider.Settings;

/**
 * Application-level bootstrap for shared Wethaq services.
 * Keeps process-wide initialization out of individual screens.
 */
public final class WethaqApplication extends Application {
    public static final String MESSAGE_CHANNEL_ID = "wethaq_messages";

    @Override
    public void onCreate() {
        super.onCreate();
        createMessageNotificationChannel();
    }

    private void createMessageNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "رسائل وَثاق",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("تنبيهات الرسائل الواردة في وَثاق");
        channel.enableVibration(true);
        channel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, null);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }
}
