package com.rbteam.dynamicisland;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
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
import java.util.List;
import java.util.Locale;

public class IslandService extends Service {
    private static IslandService instance;
    private WindowManager wm;
    private LinearLayout pill, controls;
    private TextView main, sub;
    private boolean expanded = false;
    private boolean charging = false;
    private int batteryLevel = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() { updateText(); handler.postDelayed(this, 5000); }
    };
    private final Runnable collapseAfterCharge = new Runnable() {
        @Override public void run() {
            expanded = false;
            if (sub != null) sub.setVisibility(View.GONE);
            if (controls != null) controls.setVisibility(View.GONE);
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
                showExpanded();
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
        d.setCornerRadius(dp(28));
        d.setStroke(dp(1), Color.rgb(38, 42, 54));
        return d;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void buildPill() {
        pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(12), dp(5), dp(12), dp(5));
        pill.setBackground(background());

        main = text("Y9 Island", 11, Color.WHITE);
        main.setSingleLine(true);
        main.setMaxEms(18);
        main.setEllipsize(android.text.TextUtils.TruncateAt.END);
        main.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        sub = text("", 9, Color.LTGRAY);
        sub.setSingleLine(true);
        sub.setVisibility(View.GONE);

        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setVisibility(View.GONE);
        addControl("⏮", () -> sendMediaCommand(0));
        addControl("▶/Ⅱ", () -> sendMediaCommand(1));
        addControl("⏭", () -> sendMediaCommand(2));

        pill.addView(main);
        pill.addView(sub);
        pill.addView(controls);
        pill.setOnClickListener(v -> {
            expanded = !expanded;
            handler.removeCallbacks(collapseAfterCharge);
            showExpanded();
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

    private void addControl(String label, Runnable action) {
        TextView button = text(label, 18, Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setPadding(dp(15), dp(5), dp(15), dp(5));
        button.setClickable(true);
        button.setOnClickListener(v -> action.run());
        controls.addView(button);
    }

    private void showExpanded() {
        if (sub != null) sub.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (controls != null) controls.setVisibility(expanded ? View.VISIBLE : View.GONE);
        applySize();
    }

    private MediaController activeMediaController() {
        try {
            MediaSessionManager manager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> sessions = manager.getActiveSessions(
                new ComponentName(this, MediaNotificationListener.class));
            if (sessions != null) {
                for (MediaController controller : sessions) {
                    if (controller.getPlaybackState() != null) return controller;
                }
                if (!sessions.isEmpty()) return sessions.get(0);
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "Enable notification access for Y9 Island", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) { }
        return null;
    }

    private void sendMediaCommand(int command) {
        MediaController controller = activeMediaController();
        if (controller == null) {
            Toast.makeText(this, "Start music and enable notification access", Toast.LENGTH_SHORT).show();
            return;
        }
        MediaController.TransportControls transport = controller.getTransportControls();
        try {
            if (command == 0) transport.skipToPrevious();
            else if (command == 2) transport.skipToNext();
            else {
                android.media.session.PlaybackState state = controller.getPlaybackState();
                if (state != null && state.getState() == android.media.session.PlaybackState.STATE_PLAYING)
                    transport.pause();
                else transport.play();
            }
        } catch (Exception e) {
            Toast.makeText(this, "This music app does not support that control", Toast.LENGTH_SHORT).show();
        }
        handler.postDelayed(this::updateText, 350);
    }

    private WindowManager.LayoutParams params() {
        int mode = getSharedPreferences("island", MODE_PRIVATE).getInt("position", 1);
        int width = expanded ? dp(218) : dp(128);
        int height = expanded ? dp(91) : dp(31);
        int gravity = mode == 0
            ? (Gravity.TOP | Gravity.CENTER_HORIZONTAL)
            : (Gravity.TOP | Gravity.RIGHT);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        p.gravity = gravity;
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
        MediaController controller = activeMediaController();
        boolean playing = false;
        if (controller != null && controller.getPlaybackState() != null)
            playing = controller.getPlaybackState().getState() == android.media.session.PlaybackState.STATE_PLAYING;

        if (!track.isEmpty()) {
            main.setText((playing ? "♫  " : "Ⅱ  ") + track);
            String details = artist.isEmpty() ? time : artist;
            if (charging) details = "⚡ " + batteryLevel + "%  •  " + details;
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
