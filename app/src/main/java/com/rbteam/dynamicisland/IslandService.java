package com.rbteam.dynamicisland;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
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
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
public class IslandService extends Service {
    private static IslandService instance;
    private WindowManager wm;
    private LinearLayout pill;
    private TextView main, sub;
    private boolean expanded = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() { updateText(); handler.postDelayed(this, 15000); }
    };
    public static void refresh() { if (instance != null) instance.handler.post(instance::updateText); }
    @Override public void onCreate() {
        super.onCreate(); instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        buildPill(); handler.post(ticker);
    }
    private GradientDrawable background() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.rgb(5, 7, 12)); d.setCornerRadius(100);
        d.setStroke(2, Color.rgb(70, 76, 100)); return d;
    }
    private void buildPill() {
        pill = new LinearLayout(this); pill.setOrientation(LinearLayout.VERTICAL);
        pill.setGravity(Gravity.CENTER); pill.setPadding(22, 8, 22, 8); pill.setBackground(background());
        main = new TextView(this); main.setTextColor(Color.WHITE); main.setTextSize(12);
        main.setTypeface(Typeface.DEFAULT, Typeface.BOLD); main.setGravity(Gravity.CENTER);
        sub = new TextView(this); sub.setTextColor(Color.LTGRAY); sub.setTextSize(10); sub.setGravity(Gravity.CENTER);
        sub.setVisibility(View.GONE); pill.addView(main); pill.addView(sub);
        pill.setOnClickListener(v -> { expanded = !expanded; sub.setVisibility(expanded ? View.VISIBLE : View.GONE); applySize(); updateText(); });
        try { wm.addView(pill, params()); }
        catch (Exception e) { Toast.makeText(this, "Allow display over other apps first", Toast.LENGTH_LONG).show(); stopSelf(); return; }
        updateText();
    }
    private WindowManager.LayoutParams params() {
        int mode = getSharedPreferences("island", MODE_PRIVATE).getInt("position", 0);
        int width = expanded ? 250 : (mode == 0 ? 174 : 150);
        int height = expanded ? 68 : 36;
        int gravity = mode == 0 ? (Gravity.TOP | Gravity.CENTER_HORIZONTAL) : (Gravity.TOP | Gravity.RIGHT);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        p.gravity = gravity; p.y = mode == 0 ? 3 : 10; if (mode == 1) p.x = 10; return p;
    }
    private void applySize() { if (pill != null && pill.getParent() != null) wm.updateViewLayout(pill, params()); }
    private void updateText() {
        if (main == null) return;
        String time = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());
        int level = 0; Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b != null) { int l = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); int s = b.getIntExtra(BatteryManager.EXTRA_SCALE, -1); if (l >= 0 && s > 0) level = Math.round(l * 100f / s); }
        String track = getSharedPreferences("island", MODE_PRIVATE).getString("track", "");
        String artist = getSharedPreferences("island", MODE_PRIVATE).getString("artist", "");
        main.setText(track.isEmpty() ? "●  " + time + "   ·   " + level + "%" : "♫  " + track);
        sub.setText(track.isEmpty() ? "Tap to expand  •  Battery " + level + "%" : (artist.isEmpty() ? time : artist + "  •  " + time) + "  •  " + level + "%");
    }
    @Override public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }
    @Override public void onDestroy() {
        handler.removeCallbacks(ticker); if (pill != null && pill.getParent() != null) wm.removeView(pill);
        instance = null; super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
