package com.wethaq.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

public final class PremiumActivity extends Activity {
    private static final int GOLD = 0xFFD4AF37;
    private static final int BLACK = 0xFF050507;
    private static final int PANEL = 0xFF151519;
    private final ArrayList<String> contacts = new ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BLACK);
        getWindow().setNavigationBarColor(BLACK);
        // Always render a valid screen before requesting any runtime permission.
        showHome();
        if (Build.VERSION.SDK_INT >= 33) {
            getWindow().getDecorView().postDelayed(() -> {
                try {
                    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 90);
                    }
                } catch (Throwable ignored) { }
            }, 350);
        }
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        d.setStroke(1, 0x665C481B);
        return d;
    }

    private TextView title(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(22);
        t.setTextColor(GOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(18, 20, 18, 20);
        t.setBackground(bg(PANEL, 28));
        return t;
    }

    private Button menuButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(17);
        b.setAllCaps(false);
        b.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        b.setBackground(bg(0xFF1B1B20, 28));
        b.setPadding(20, 10, 20, 10);
        return b;
    }

    private LinearLayout.LayoutParams lp(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, top, 0, 0);
        return p;
    }

    private void showHome() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 10, 12, 12);
        root.setBackgroundColor(BLACK);

        root.addView(title("وَثاق\nالمؤسس حاتم حسين الحاج رمضان"));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        Button search = menuButton("🔎  البحث");
        Button contacts = menuButton("👥  جهات الاتصال");
        Button settings = menuButton("⚙️  الإعدادات");
        Button personal = menuButton("🪪  المعلومات الشخصية");
        Button archive = menuButton("🖼️  المحفوظات");

        list.addView(search, lp(12));
        list.addView(contacts, lp(10));
        list.addView(settings, lp(10));
        list.addView(personal, lp(10));
        list.addView(archive, lp(10));

        TextView trust = new TextView(this);
        trust.setText("🛡️ مركز الثقة\nهوية رقمية • خصوصية • مراسلة آمنة");
        trust.setTextColor(Color.WHITE);
        trust.setTextSize(15);
        trust.setGravity(Gravity.CENTER);
        trust.setPadding(18, 18, 18, 18);
        trust.setBackground(bg(0xFF101014, 28));
        list.addView(trust, lp(18));

        search.setOnClickListener(v -> safeMessage("البحث عن مستخدم"));
        contacts.setOnClickListener(v -> safeMessage("جهات الاتصال"));
        settings.setOnClickListener(v -> safeMessage("الإعدادات"));
        personal.setOnClickListener(v -> safeMessage("المعلومات الشخصية"));
        archive.setOnClickListener(v -> safeMessage("المحفوظات"));

        try {
            setContentView(root);
        } catch (Throwable e) {
            TextView fallback = new TextView(this);
            fallback.setText("وَثاق\n\nحدث خطأ في الواجهة، يرجى إعادة فتح التطبيق.");
            fallback.setTextColor(GOLD);
            fallback.setTextSize(20);
            fallback.setGravity(Gravity.CENTER);
            fallback.setBackgroundColor(BLACK);
            setContentView(fallback);
        }
    }

    private void safeMessage(String text) {
        try { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) { }
    }
}
