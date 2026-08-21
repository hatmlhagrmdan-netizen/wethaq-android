package com.wethaq.finalapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class LauncherActivity extends Activity {
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView text(String s, float size) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(Color.WHITE); v.setGravity(Gravity.CENTER); return v;
    }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER); root.setPadding(dp(24),dp(24),dp(24),dp(24)); root.setBackgroundColor(Color.rgb(8,27,35));
        TextView logo = text("وَثاق", 42); logo.setTypeface(null, 1); root.addView(logo, new LinearLayout.LayoutParams(-1,dp(80)));
        TextView sub = text("WETHAQ\nهوية رقمية • تواصل • ثقة", 16); root.addView(sub, new LinearLayout.LayoutParams(-1,dp(80)));
        Button enter = new Button(this); enter.setText("الدخول إلى وَثاق"); enter.setAllCaps(false); enter.setOnClickListener(v -> { startActivity(new Intent(this, MainActivity.class)); finish(); });
        root.addView(enter, new LinearLayout.LayoutParams(-1,dp(56)));
        setContentView(root);
    }
}
