package com.rober.drivedeck;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

final class HudStyle {
    static final int TEXT = 0xFFF5F7FA;
    static final int MUTED = 0xFF8A94A6;

    private HudStyle() {
    }

    static void apply(Context context, View root, Prefs prefs) {
        int accent = prefs.accent();
        float factor = prefs.hudSize() == 0 ? 0.84f : prefs.hudSize() == 2 ? 1.2f : 1.0f;
        int play = Math.round(64 * factor);
        int side = Math.round(56 * factor);
        int playText = Math.round(28 * factor);
        int sideText = Math.round(22 * factor);
        int navText = Math.round(14 * factor);
        int trackText = Math.round(12 * factor);

        TextView nav = (TextView) root.findViewById(R.id.ov_nav);
        TextView track = (TextView) root.findViewById(R.id.ov_track);
        TextView playView = (TextView) root.findViewById(R.id.ov_play);
        TextView prev = (TextView) root.findViewById(R.id.ov_prev);
        TextView next = (TextView) root.findViewById(R.id.ov_next);
        TextView close = (TextView) root.findViewById(R.id.ov_close);
        TextView grip = (TextView) root.findViewById(R.id.ov_grip);

        size(playView, dp(context, play), dp(context, play));
        size(prev, dp(context, side), dp(context, side));
        size(next, dp(context, side), dp(context, side));
        textSize(playView, playText);
        textSize(prev, sideText);
        textSize(next, sideText);
        textSize(nav, navText);
        textSize(track, trackText);

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(context, 22));
        background.setColor(withAlpha(prefs.hudBg(), prefs.hudOpacity()));
        background.setStroke(dp(context, 1), 0x663B4A5C);
        root.setBackground(background);

        if (nav != null) {
            nav.setTextColor(accent);
        }
        if (playView != null) {
            playView.setTextColor(accent);
        }
        if (track != null) {
            track.setTextColor(TEXT);
        }
        if (prev != null) {
            prev.setTextColor(TEXT);
        }
        if (next != null) {
            next.setTextColor(TEXT);
        }
        if (close != null) {
            close.setTextColor(MUTED);
            close.setVisibility(prefs.hudShowClose() ? View.VISIBLE : View.GONE);
        }
        if (grip != null) {
            grip.setTextColor(MUTED);
            grip.setVisibility(prefs.hudShowGrip() ? View.VISIBLE : View.GONE);
        }
    }

    static int playSizeDp(Prefs prefs) {
        float factor = prefs.hudSize() == 0 ? 0.84f : prefs.hudSize() == 2 ? 1.2f : 1.0f;
        return Math.round(64 * factor);
    }

    static void applyDashboard(Context context, View navTile, View play, Prefs prefs) {
        if (navTile != null) {
            navTile.setBackground(rounded(context, prefs.accent(), 16));
        }
        if (play != null) {
            play.setBackground(circle(context, prefs.accent()));
        }
    }

    static Drawable rounded(Context context, int color, float radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(context, radiusDp));
        shape.setColor(color);
        return new RippleDrawable(ColorStateList.valueOf(0x33000000), shape, null);
    }

    static Drawable circle(Context context, int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        return new RippleDrawable(ColorStateList.valueOf(0x33000000), shape, null);
    }

    static Drawable box(Context context, int color, float radiusDp, int strokeColor) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(context, radiusDp));
        shape.setColor(color);
        shape.setStroke(dp(context, 1), strokeColor);
        return shape;
    }

    static int withAlpha(int color, int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        int alpha = Math.round(255 * clamped / 100f);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void size(View view, int width, int height) {
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = width;
        params.height = height;
        view.setLayoutParams(params);
    }

    private static void textSize(TextView view, int sp) {
        if (view != null) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        }
    }
}
