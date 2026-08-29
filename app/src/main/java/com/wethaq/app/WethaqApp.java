package com.wethaq.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

public final class WethaqApp extends Application implements Application.ActivityLifecycleCallbacks {
    @Override public void onCreate(){super.onCreate();registerActivityLifecycleCallbacks(this);}
    private void refresh(Activity a){a.getWindow().setStatusBarColor(android.graphics.Color.rgb(8,8,10));a.getWindow().setNavigationBarColor(android.graphics.Color.rgb(8,8,10));a.getWindow().getDecorView().postDelayed(()->WethaqUi.apply(this,a),120);a.getWindow().getDecorView().postDelayed(()->WethaqUi.apply(this,a),500);}
    @Override public void onActivityCreated(Activity a,Bundle b){refresh(a);}
    @Override public void onActivityResumed(Activity a){refresh(a);}
    @Override public void onActivityStarted(Activity a){}
    @Override public void onActivityPaused(Activity a){}
    @Override public void onActivityStopped(Activity a){}
    @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}
    @Override public void onActivityDestroyed(Activity a){}
}
