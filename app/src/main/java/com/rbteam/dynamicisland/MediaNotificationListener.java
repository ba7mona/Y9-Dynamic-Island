package com.rbteam.dynamicisland;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.app.Notification;
public class MediaNotificationListener extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        Notification n = sbn.getNotification(); if (n == null || n.extras == null) return;
        CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
        if (title == null && text == null) return;
        String pkg = sbn.getPackageName().toLowerCase();
        if (pkg.contains("music") || pkg.contains("spotify") || pkg.contains("youtube") || pkg.contains("anghami") || pkg.contains("vlc") || pkg.contains("media")) {
            getSharedPreferences("island", MODE_PRIVATE).edit().putString("track", title == null ? "Now playing" : title.toString()).putString("artist", text == null ? "" : text.toString()).apply();
            IslandService.refresh();
        }
    }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { }
}
