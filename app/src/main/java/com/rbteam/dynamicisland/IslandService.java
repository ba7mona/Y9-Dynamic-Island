package com.rbteam.dynamicisland;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class IslandService extends Service {
    private static IslandService instance;
    private WindowManager wm;
    private LinearLayout pill;
    private TextView main, sub;
    private boolean expanded = false;
    private boolean charging = false;
    private int batteryLevel = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() { updateText(); handler.postDelayed(this, 10000); }
    };
    private final Runnable collapseAfterCharge = new Runnable() {
        @Override public void run() {
            expanded = false;
            if (sub != null) sub.setVisibility(View.GONE);
            applySize();
            updateText();
        }
    };
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) batteryLevel = Math.round(level * 100f / scale);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                       status == BatteryManager.BATTERY_STATUS_FULL;
            updateText();
        }
    };
    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                charging = true;
                expanded = true;
                if (sub != null) sub.setVisibility(View.VISIBLE);
                applySize();
                handler.removeCallbacks(collapseAfterCharge);
                handler.postDelayed(collapseAfterCharge, 4500);
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                charging = false;
                updateText();
            }
        }
    };

    public static void refresh() {
        if (instance != null) instance.handler.post(instance::updateText);
    }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        IntentFilter power = new IntentFilter();
        power.addAction(Intent.ACTION_POWER_CONNECTED);
        power.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(powerReceiver, power);
        buildPill();
        handler.post(ticker);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable background() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.rgb(3, 4, 8));
        d.setCornerRadius(dp(30));
        d.setStroke(dp(1), Color.rgb(35, 38, 48));
        return d;
    }

    private void buildPill() {
        pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(13), dp(5), dp(13), dp(5));
        pill.setBackground(background());

        main = new TextView(this);
        main.setTextColor(Color.WHITE);
        main.setTextSize(11);
        main.setSingleLine(true);
        main.setMaxEms(19);
        main.setEllipsize(android.text.TextUtils.TruncateAt.END);
        main.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        main.setGravity(Gravity.CENTER);

        sub = new TextView(this);
        sub.setTextColor(Color.LTGRAY);
        sub.setTextSize(9);
        sub.setSingleLine(true);
        sub.setGravity(Gravity.CENTER);
        sub.setVisibility(View.GONE);

        pill.addView(main);
        pill.addView(sub);
        pill.setOnClickListener(v -> {
            expanded = !expanded;
            handler.removeCallbacks(collapseAfterCharge);
            sub.setVisibility(expanded ? View.VISIBLE : View.GONE);
            applySize();
            updateText();
        });

        try {
            wm.addView(pill, params());
        } catch (Exception e) {
            Toast.makeText(this, "Allow display over other apps first", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        updateText();
    }

    private WindowManager.LayoutParams params() {
        int mode = getSharedPreferences("island", MODE_PRIVATE).getInt("position", 1);
        int width = expanded ? dp(205) : dp(132);
        int height = expanded ? dp(54) : dp(32);
        int gravity = mode == 0
            ? (Gravity.TOP | Gravity.CENTER_HORIZONTAL)
            : (Gravity.TOP | Gravity.RIGHT);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        p.gravity = gravity;
        // Keep Side Island below the status bar and inset from the right edge.
        p.y = mode == 0 ? dp(2) : dp(34);
        if (mode == 1) p.x = dp(12);
        return p;
    }

    private void applySize() {
        if (pill != null && pill.getParent() != null) {
            try { wm.updateViewLayout(pill, params()); } catch (Exception ignored) { }
        }
    }

    private void updateText() {
        if (main == null) return;
        String time = new SimpleDateFormat("h:mm", Locale.getDefault()).format(new Date());
        String track = getSharedPreferences("island", MODE_PRIVATE).getString("track", "");
        String artist = getSharedPreferences("island", MODE_PRIVATE).getString("artist", "");

        if (!track.isEmpty()) {
            main.setText("♫  " + track);
            String details = artist.isEmpty() ? time : artist;
            if (charging) details = "⚡ Charging " + batteryLevel + "%  •  " + details;
            else details = details + "  •  " + batteryLevel + "%";
            sub.setText(details);
        } else if (charging) {
            main.setText("⚡ Charging  " + batteryLevel + "%");
            sub.setText("Battery connected  •  " + time);
        } else {
            main.setText("●  " + time + "  ·  " + batteryLevel + "%");
            sub.setText("Battery " + batteryLevel + "%  •  Tap to expand");
        }
    }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(collapseAfterCharge);
        try { unregisterReceiver(batteryReceiver); } catch (Exception ignored) { }
        try { unregisterReceiver(powerReceiver); } catch (Exception ignored) { }
        if (pill != null && pill.getParent() != null) wm.removeView(pill);
        instance = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
