package com.wethaq.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);
        root.setBackgroundColor(Color.rgb(10, 18, 30));

        TextView title = new TextView(this);
        title.setText("وَثاق");
        title.setTextColor(Color.WHITE);
        title.setTextSize(34);
        title.setGravity(Gravity.CENTER);

        TextView status = new TextView(this);
        status.setText("النواة الأساسية تعمل بنجاح");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 24, 0, 0);

        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }
}
