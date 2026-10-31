package com.rober.drivedeck;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NavListenerService extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification sbn : active) {
                    handle(sbn, true);
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onListenerDisconnected() {
        DriveState.clearAll();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        handle(sbn, true);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        handle(sbn, false);
    }

    private static void handle(StatusBarNotification sbn, boolean posted) {
        if (sbn == null) {
            return;
        }
        String pkg = sbn.getPackageName();
        if (!Prefs.MAPS.equals(pkg) && !Prefs.WAZE.equals(pkg)) {
            return;
        }
        if (!posted && Prefs.WAZE.equals(pkg)) {
            DriveState.clearWaze();
        }
        Notification n = sbn.getNotification();
        if (n == null) {
            return;
        }
        boolean navigation = Notification.CATEGORY_NAVIGATION.equals(n.category)
                || (Prefs.WAZE.equals(pkg)
                    && (n.flags & Notification.FLAG_ONGOING_EVENT) != 0
                    && !"CLOSE_WAZE_CHANNEL".equals(n.getChannelId()));
        if (!navigation) {
            return;
        }
        if (posted) {
            String line = extract(n);
            if (!line.isEmpty()) {
                DriveState.setNav(pkg, line);
            }
        } else {
            DriveState.clearNav(pkg);
        }
    }

    private static String extract(Notification n) {
        Bundle e = n.extras;
        if (e == null) {
            return "";
        }
        String title = clean(e.getCharSequence(Notification.EXTRA_TITLE));
        String text = clean(e.getCharSequence(Notification.EXTRA_TEXT));
        String subText = clean(e.getCharSequence(Notification.EXTRA_SUB_TEXT));
        if (text.isEmpty()) {
            text = clean(e.getCharSequence(Notification.EXTRA_BIG_TEXT));
        }
        if (text.isEmpty()) {
            text = subText;
        }
        if (text.isEmpty()) {
            CharSequence[] lines = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            StringBuilder sb = new StringBuilder();
            if (lines != null) {
                for (CharSequence line : lines) {
                    String value = clean(line);
                    if (!value.isEmpty()) {
                        if (sb.length() > 0) {
                            sb.append(" · ");
                        }
                        sb.append(value);
                    }
                }
            }
            text = sb.toString();
        }
        if (title.equalsIgnoreCase("Maps") || title.equalsIgnoreCase("Waze")
                || title.equalsIgnoreCase("Google Maps") || title.equalsIgnoreCase("Waze Navigation")) {
            title = "";
        }
        if (title.isEmpty()) {
            return appendSub(text, subText);
        }
        if (text.isEmpty() || title.contains(text)) {
            return appendSub(title, subText);
        }
        if (text.contains(title)) {
            return appendSub(text, subText);
        }
        return appendSub(title + " · " + text, subText);
    }

    private static String appendSub(String line, String subText) {
        if (subText.isEmpty() || line.contains(subText)) {
            return line;
        }
        return line.isEmpty() ? subText : line + " · " + subText;
    }

    private static String clean(CharSequence value) {
        if (value == null) {
            return "";
        }
        return value.toString().replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }
}
