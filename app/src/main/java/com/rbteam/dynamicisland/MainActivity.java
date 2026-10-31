package com.rbteam.dynamicisland;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.view.Gravity;

public class MainActivity extends Activity {
    private int selectedPosition = 1;
    private Button notch, side;

    private TextView label(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private Button action(String value) {
        Button b = new Button(this);
        b.setText(value);
        return b;
    }

    private void choose(int position) {
        selectedPosition = position;
        getSharedPreferences("island", MODE_PRIVATE).edit().putInt("position", position).apply();
        if (notch != null) notch.setText(position == 0 ? "Notch Island (camera)" : "Notch Island (camera)");
        if (side != null) side.setText(position == 1 ? "✓ Side Island (top right)" : "Side Island (top right)");
        stopService(new Intent(this, IslandService.class));
        if (Settings.canDrawOverlays(this)) startService(new Intent(this, IslandService.class));
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        selectedPosition = getSharedPreferences("island", MODE_PRIVATE).getInt("position", 1);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 30, 24, 24);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(12, 16, 24));

        root.addView(label("Y9 DYNAMIC ISLAND", 24, Color.WHITE));
        TextView desc = label("Floating island for Huawei Y9 2019", 14, Color.LTGRAY);
        desc.setPadding(0, 16, 0, 22);
        root.addView(desc);

        notch = action("Notch Island (camera)");
        notch.setOnClickListener(v -> choose(0));
        root.addView(notch);

        side = action("✓ Side Island (top right)");
        side.setOnClickListener(v -> choose(1));
        root.addView(side);

        choose(selectedPosition);

        Button overlay = action("1. Allow display over other apps");
        overlay.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this))
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        });
        root.addView(overlay);

        Button notifications = action("2. Enable notification access (music)");
        notifications.setOnClickListener(v -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        root.addView(notifications);

        Button start = action("3. Start Dynamic Island");
        start.setOnClickListener(v -> {
            if (Settings.canDrawOverlays(this)) startService(new Intent(this, IslandService.class));
            else overlay.performClick();
        });
        root.addView(start);

        Button stop = action("Stop Island");
        stop.setOnClickListener(v -> stopService(new Intent(this, IslandService.class)));
        root.addView(stop);

        root.addView(label("Tap the island to expand or collapse it.", 12, Color.LTGRAY));
        setContentView(root);
    }
}
