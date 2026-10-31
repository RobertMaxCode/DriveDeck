package com.rober.drivedeck;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

final class Perms {
    static boolean notifications(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(context.getPackageName());
    }

    static boolean overlay(Context context) {
        return Settings.canDrawOverlays(context);
    }

    static boolean location(Context context) {
        return context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean accessibility(Context context) {
        int enabled = Settings.Secure.getInt(context.getContentResolver(), "accessibility_enabled", 0);
        if (enabled != 1) {
            return false;
        }
        String services = Settings.Secure.getString(context.getContentResolver(), "enabled_accessibility_services");
        return services != null && services.contains(
                context.getPackageName() + "/" + WazeAccessibilityService.class.getName());
    }

    static void openAccessibilitySettings(Context context) {
        context.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    static void openNotificationSettings(Context context) {
        context.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    static void openOverlaySettings(Context context) {
        context.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.getPackageName()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
