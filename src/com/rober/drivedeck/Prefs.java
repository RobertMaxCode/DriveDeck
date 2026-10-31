package com.rober.drivedeck;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String MAPS = "com.google.android.apps.maps";
    static final String WAZE = "com.waze";
    static final String SPOTIFY = "com.spotify.music";

    static final int ACCENT_DEFAULT = 0xFFFFB300;
    static final int HUD_BG_DEFAULT = 0xFF161C24;
    static final int HUD_OPACITY_DEFAULT = 92;
    static final int HUD_SIZE_DEFAULT = 1;
    static final int HUD_POS_BOTTOM = 0;
    static final int HUD_POS_CENTER = 1;
    static final int HUD_POS_TOP = 2;
    static final int HUD_POS_CUSTOM = -1;

    private final SharedPreferences sp;

    Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences("drivedeck", Context.MODE_PRIVATE);
    }

    String navPackage() {
        return sp.getBoolean("waze", false) ? WAZE : MAPS;
    }

    void setWaze(boolean waze) {
        sp.edit().putBoolean("waze", waze).apply();
    }

    String home() {
        return sp.getString("home", "");
    }

    String work() {
        return sp.getString("work", "");
    }

    void setDest(String home, String work) {
        sp.edit().putString("home", home).putString("work", work).apply();
    }

    boolean hud() {
        return sp.getBoolean("hud", false);
    }

    void setHud(boolean on) {
        sp.edit().putBoolean("hud", on).apply();
    }

    int hudY() {
        return sp.getInt("hud_y", -1);
    }

    void setHudY(int y) {
        sp.edit().putInt("hud_y", y).apply();
    }

    int hudX() {
        return sp.getInt("hud_x", -1);
    }

    void setHudX(int x) {
        sp.edit().putInt("hud_x", x).apply();
    }

    int hudLandY() {
        return sp.getInt("hud_land_y", -1);
    }

    void setHudLandY(int y) {
        sp.edit().putInt("hud_land_y", y).apply();
    }

    void clearHudPositions() {
        sp.edit().remove("hud_y").remove("hud_x").remove("hud_land_y").apply();
    }

    int accent() {
        return sp.getInt("accent", ACCENT_DEFAULT);
    }

    void setAccent(int color) {
        sp.edit().putInt("accent", color).apply();
    }

    int hudSize() {
        return sp.getInt("hud_size", HUD_SIZE_DEFAULT);
    }

    void setHudSize(int size) {
        sp.edit().putInt("hud_size", size).apply();
    }

    int hudBg() {
        return sp.getInt("hud_bg", HUD_BG_DEFAULT);
    }

    void setHudBg(int color) {
        sp.edit().putInt("hud_bg", color).apply();
    }

    int hudOpacity() {
        return sp.getInt("hud_opacity", HUD_OPACITY_DEFAULT);
    }

    void setHudOpacity(int percent) {
        sp.edit().putInt("hud_opacity", percent).apply();
    }

    boolean hudShowRoute() {
        return sp.getBoolean("hud_route", true);
    }

    void setHudShowRoute(boolean show) {
        sp.edit().putBoolean("hud_route", show).apply();
    }

    boolean hudShowTrack() {
        return sp.getBoolean("hud_track", true);
    }

    void setHudShowTrack(boolean show) {
        sp.edit().putBoolean("hud_track", show).apply();
    }

    boolean hudShowGrip() {
        return sp.getBoolean("hud_grip", true);
    }

    void setHudShowGrip(boolean show) {
        sp.edit().putBoolean("hud_grip", show).apply();
    }

    boolean hudShowClose() {
        return sp.getBoolean("hud_close", true);
    }

    void setHudShowClose(boolean show) {
        sp.edit().putBoolean("hud_close", show).apply();
    }

    int hudPos() {
        return sp.getInt("hud_pos", HUD_POS_BOTTOM);
    }

    void setHudPos(int pos) {
        sp.edit().putInt("hud_pos", pos).apply();
    }

    void resetHud() {
        sp.edit()
                .putInt("accent", ACCENT_DEFAULT)
                .putInt("hud_size", HUD_SIZE_DEFAULT)
                .putInt("hud_bg", HUD_BG_DEFAULT)
                .putInt("hud_opacity", HUD_OPACITY_DEFAULT)
                .putBoolean("hud_route", true)
                .putBoolean("hud_track", true)
                .putBoolean("hud_grip", true)
                .putBoolean("hud_close", true)
                .putInt("hud_pos", HUD_POS_BOTTOM)
                .remove("hud_y")
                .remove("hud_x")
                .remove("hud_land_y")
                .apply();
    }
}
