package com.wethaq.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import java.net.HttpURLConnection;
import java.net.URL;

public final class WethaqApp extends Application implements Application.ActivityLifecycleCallbacks {
    private static final String API_HEALTH = "https://wethaq-backend-production.up.railway.app/health";

    @Override public void onCreate(){
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
        warmBackendConnection();
    }

    private void warmBackendConnection(){
        Thread t = new Thread(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(API_HEALTH).openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(5000);
                c.setReadTimeout(7000);
                c.setUseCaches(false);
                c.getResponseCode();
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
        }, "wethaq-backend-warmup");
        t.setDaemon(true);
        t.start();
    }

    private void refresh(Activity a){
        a.getWindow().setStatusBarColor(android.graphics.Color.rgb(8,8,10));
        a.getWindow().setNavigationBarColor(android.graphics.Color.rgb(8,8,10));
        a.getWindow().getDecorView().post(() -> WethaqUi.apply(this,a));
    }
    @Override public void onActivityCreated(Activity a,Bundle b){refresh(a);}
    @Override public void onActivityResumed(Activity a){refresh(a);}
    @Override public void onActivityStarted(Activity a){}
    @Override public void onActivityPaused(Activity a){}
    @Override public void onActivityStopped(Activity a){}
    @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}
    @Override public void onActivityDestroyed(Activity a){}
}
