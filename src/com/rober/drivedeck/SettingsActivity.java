package com.rober.drivedeck;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {
    private static final int REQ_LOCATION = 20;

    private static final int DARK = 0xFF0B0F14;
    private static final int CARD = 0xFF1A222C;
    private static final int CARD_STROKE = 0xFF263241;

    private static final int[] ACCENTS = {
            0xFFFFB300, 0xFF4DD0E1, 0xFF6EE7B7, 0xFFFF6B6B, 0xFFC4B5FD,
            0xFFF5F7FA, 0xFFFF8A65, 0xFF64B5F6, 0xFFF48FB1, 0xFFC6FF00
    };
    private static final String[] ACCENT_NAMES = {
            "Ámbar", "Cian", "Verde", "Rojo", "Violeta",
            "Blanco", "Naranja", "Azul", "Rosa", "Lima"
    };
    private static final int[] BACKGROUNDS = {
            0xFF0B0F14, 0xFF161C24, 0xFF0E1A2B, 0xFF1A1026, 0xFF0E1F1A
    };
    private static final String[] BACKGROUND_NAMES = {
            "Negro", "Carbón", "Azul noche", "Morado noche", "Verde noche"
    };
    private static final String[] SIZE_NAMES = {"Pequeño", "Mediano", "Grande"};
    private static final String[] POSITION_NAMES = {"Abajo", "Centro", "Arriba"};

    private interface BoolSetter {
        void set(boolean value);
    }

    private Prefs prefs;
    private RadioButton maps;
    private RadioButton waze;
    private EditText home;
    private EditText work;
    private TextView notifStatus;
    private TextView overlayStatus;
    private TextView locationStatus;
    private TextView accessibilityStatus;

    private LinearLayout hudCustom;
    private View hudPreview;
    private SeekBar opacityBar;
    private TextView opacityLabel;
    private TextView positionNote;
    private CheckBox showRoute;
    private CheckBox showTrack;
    private CheckBox showGrip;
    private CheckBox showClose;
    private final List<TextView> accentSwatches = new ArrayList<>();
    private final List<TextView> backgroundSwatches = new ArrayList<>();
    private final List<TextView> sizeButtons = new ArrayList<>();
    private final List<TextView> positionButtons = new ArrayList<>();
    private boolean syncing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = new Prefs(this);

        maps = (RadioButton) findViewById(R.id.nav_maps);
        waze = (RadioButton) findViewById(R.id.nav_waze);
        home = (EditText) findViewById(R.id.input_home);
        work = (EditText) findViewById(R.id.input_work);
        notifStatus = (TextView) findViewById(R.id.status_notif);
        overlayStatus = (TextView) findViewById(R.id.status_overlay);
        locationStatus = (TextView) findViewById(R.id.status_location);
        accessibilityStatus = (TextView) findViewById(R.id.status_accessibility);
        hudCustom = (LinearLayout) findViewById(R.id.hud_custom);

        boolean isWaze = Prefs.WAZE.equals(prefs.navPackage());
        waze.setChecked(isWaze);
        maps.setChecked(!isWaze);
        home.setText(prefs.home());
        work.setText(prefs.work());

        findViewById(R.id.btn_save).setOnClickListener(v -> {
            persist();
            toast("Ajustes guardados");
            finish();
        });
        findViewById(R.id.btn_notif).setOnClickListener(v -> Perms.openNotificationSettings(this));
        findViewById(R.id.btn_overlay).setOnClickListener(v -> Perms.openOverlaySettings(this));
        findViewById(R.id.btn_accessibility).setOnClickListener(v -> Perms.openAccessibilitySettings(this));
        findViewById(R.id.btn_location).setOnClickListener(v -> {
            if (Perms.location(this)) {
                toast("Ubicación ya concedida");
            } else {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            }
        });

        buildHudOptions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        OverlayService.ownAppVisible = true;
        refreshStatus();
        syncHudOptions();
    }

    @Override
    protected void onPause() {
        OverlayService.ownAppVisible = false;
        persist();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshStatus();
    }

    private void persist() {
        prefs.setWaze(waze.isChecked());
        prefs.setDest(home.getText().toString().trim(), work.getText().toString().trim());
    }

    private void refreshStatus() {
        setStatus(notifStatus, Perms.notifications(this));
        setStatus(overlayStatus, Perms.overlay(this));
        setStatus(locationStatus, Perms.location(this));
        setStatus(accessibilityStatus, Perms.accessibility(this));
    }

    private void setStatus(TextView view, boolean granted) {
        view.setText(granted ? "Concedido" : "Sin permiso");
        view.setTextColor(granted ? 0xFF6EE7B7 : 0xFFFFC46B);
    }

    private void buildHudOptions() {
        if (hudCustom == null) {
            return;
        }
        hudCustom.removeAllViews();

        addLabel("Vista previa");
        hudPreview = getLayoutInflater().inflate(R.layout.overlay, hudCustom, false);
        hudCustom.addView(hudPreview);
        int[] previewIds = {
                R.id.ov_nav, R.id.ov_track, R.id.ov_play,
                R.id.ov_prev, R.id.ov_next, R.id.ov_close, R.id.ov_grip
        };
        for (int id : previewIds) {
            View view = hudPreview.findViewById(id);
            if (view != null) {
                view.setClickable(false);
                view.setFocusable(false);
            }
        }

        addLabel("Color de acento");
        addSwatchRow(ACCENTS, 0, 5, true);
        addSwatchRow(ACCENTS, 5, 5, true);

        addLabel("Fondo del HUD");
        addSwatchRow(BACKGROUNDS, 0, BACKGROUNDS.length, false);

        addLabel("Tamaño");
        LinearLayout sizeRow = row();
        for (int i = 0; i < SIZE_NAMES.length; i++) {
            sizeRow.addView(segment(SIZE_NAMES[i], i, true));
        }
        hudCustom.addView(sizeRow);

        addLabel("Opacidad");
        opacityLabel = new TextView(this);
        opacityLabel.setTextColor(HudStyle.MUTED);
        opacityLabel.setTextSize(12);
        hudCustom.addView(opacityLabel);

        opacityBar = new SeekBar(this);
        opacityBar.setMax(60);
        opacityBar.setProgressTintList(ColorStateList.valueOf(prefs.accent()));
        opacityBar.setThumbTintList(ColorStateList.valueOf(prefs.accent()));
        opacityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (syncing || !fromUser) {
                    return;
                }
                int value = progress + 40;
                prefs.setHudOpacity(value);
                opacityLabel.setText(value + "%");
                syncHudOptions();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                restartHud();
            }
        });
        hudCustom.addView(opacityBar);

        addLabel("Posición");
        LinearLayout positionRow = row();
        for (int i = 0; i < POSITION_NAMES.length; i++) {
            positionRow.addView(segment(POSITION_NAMES[i], i, false));
        }
        hudCustom.addView(positionRow);

        positionNote = new TextView(this);
        positionNote.setText("Posición personalizada (arrastrada)");
        positionNote.setTextColor(HudStyle.MUTED);
        positionNote.setTextSize(11);
        positionNote.setVisibility(View.GONE);
        hudCustom.addView(positionNote);

        TextView resetPosition = secondary("Restablecer posición");
        resetPosition.setOnClickListener(v -> {
            prefs.setHudPos(Prefs.HUD_POS_BOTTOM);
            prefs.clearHudPositions();
            onHudChanged();
        });
        hudCustom.addView(resetPosition);

        addLabel("Elementos visibles");
        showRoute = check("Indicación de ruta", prefs.hudShowRoute(), prefs::setHudShowRoute);
        showTrack = check("Título de la canción", prefs.hudShowTrack(), prefs::setHudShowTrack);
        showGrip = check("Asa para arrastrar", prefs.hudShowGrip(), prefs::setHudShowGrip);
        showClose = check("Botón de cerrar", prefs.hudShowClose(), prefs::setHudShowClose);
        hudCustom.addView(showRoute);
        hudCustom.addView(showTrack);
        hudCustom.addView(showGrip);
        hudCustom.addView(showClose);

        TextView reset = secondary("RESTABLECER HUD");
        reset.setOnClickListener(v -> {
            prefs.resetHud();
            syncHudOptions();
            restartHud();
        });
        hudCustom.addView(reset);

        TextView hint = new TextView(this);
        hint.setText("Si el asa está visible, puedes arrastrar el HUD; en horizontal se mueve por toda la pantalla. Los cambios se aplican al instante.");
        hint.setTextColor(HudStyle.MUTED);
        hint.setTextSize(11);
        hint.setPadding(0, dp(8), 0, 0);
        hudCustom.addView(hint);

        syncHudOptions();
    }

    private void addLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(HudStyle.MUTED);
        label.setTextSize(11);
        label.setLetterSpacing(0.12f);
        label.setPadding(0, dp(14), 0, dp(4));
        hudCustom.addView(label);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(2), 0, dp(2));
        return row;
    }

    private void addSwatchRow(int[] colors, int start, int count, boolean accent) {
        LinearLayout row = row();
        for (int i = 0; i < count; i++) {
            int color = colors[start + i];
            TextView swatch = new TextView(this);
            int size = dp(accent ? 42 : 48);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            swatch.setLayoutParams(params);
            swatch.setTag(color);
            swatch.setContentDescription(accent
                    ? ACCENT_NAMES[start + i] : BACKGROUND_NAMES[start + i]);
            swatch.setOnClickListener(v -> {
                if (accent) {
                    prefs.setAccent(color);
                } else {
                    prefs.setHudBg(color);
                }
                onHudChanged();
            });
            if (accent) {
                accentSwatches.add(swatch);
            } else {
                backgroundSwatches.add(swatch);
            }
            row.addView(swatch);
        }
        hudCustom.addView(row);
    }

    private TextView segment(String text, int value, boolean size) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(13);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(6), dp(10), dp(6), dp(10));
        button.setTag(value);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        button.setLayoutParams(params);
        button.setOnClickListener(v -> {
            if (size) {
                prefs.setHudSize(value);
            } else {
                prefs.setHudPos(value);
                prefs.clearHudPositions();
            }
            onHudChanged();
        });
        (size ? sizeButtons : positionButtons).add(button);
        return button;
    }

    private CheckBox check(String text, boolean checked, BoolSetter setter) {
        CheckBox box = new CheckBox(this);
        box.setText(text);
        box.setTextColor(HudStyle.TEXT);
        box.setTextSize(14);
        box.setChecked(checked);
        box.setPadding(0, dp(6), 0, dp(6));
        box.setButtonTintList(ColorStateList.valueOf(prefs.accent()));
        box.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (syncing) {
                return;
            }
            setter.set(isChecked);
            onHudChanged();
        });
        return box;
    }

    private TextView secondary(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(13);
        button.setTextColor(HudStyle.MUTED);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), dp(12), dp(14), dp(12));
        button.setBackground(HudStyle.box(this, CARD, 12, CARD_STROKE));
        button.setClickable(true);
        button.setFocusable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(2));
        button.setLayoutParams(params);
        return button;
    }

    private void syncHudOptions() {
        if (hudPreview == null) {
            return;
        }
        syncing = true;

        HudStyle.apply(this, hudPreview, prefs);
        TextView nav = (TextView) hudPreview.findViewById(R.id.ov_nav);
        TextView track = (TextView) hudPreview.findViewById(R.id.ov_track);
        TextView play = (TextView) hudPreview.findViewById(R.id.ov_play);
        nav.setText("490 m · Calle Mayor");
        nav.setVisibility(prefs.hudShowRoute() ? View.VISIBLE : View.GONE);
        track.setText("Canción de ejemplo");
        track.setVisibility(prefs.hudShowTrack() ? View.VISIBLE : View.INVISIBLE);
        play.setText("▶︎");

        for (TextView swatch : accentSwatches) {
            paintSwatch(swatch, (Integer) swatch.getTag(), true, (Integer) swatch.getTag() == prefs.accent());
        }
        for (TextView swatch : backgroundSwatches) {
            paintSwatch(swatch, (Integer) swatch.getTag(), false, (Integer) swatch.getTag() == prefs.hudBg());
        }
        for (TextView button : sizeButtons) {
            paintSegment(button, (Integer) button.getTag() == prefs.hudSize());
        }
        for (TextView button : positionButtons) {
            paintSegment(button, (Integer) button.getTag() == prefs.hudPos());
        }

        if (showRoute != null) {
            showRoute.setChecked(prefs.hudShowRoute());
            showTrack.setChecked(prefs.hudShowTrack());
            showGrip.setChecked(prefs.hudShowGrip());
            showClose.setChecked(prefs.hudShowClose());
            ColorStateList tint = ColorStateList.valueOf(prefs.accent());
            showRoute.setButtonTintList(tint);
            showTrack.setButtonTintList(tint);
            showGrip.setButtonTintList(tint);
            showClose.setButtonTintList(tint);
        }

        if (opacityBar != null) {
            opacityBar.setProgressTintList(ColorStateList.valueOf(prefs.accent()));
            opacityBar.setThumbTintList(ColorStateList.valueOf(prefs.accent()));
            opacityBar.setProgress(prefs.hudOpacity() - 40);
        }
        if (opacityLabel != null) {
            opacityLabel.setText(prefs.hudOpacity() + "%");
        }
        if (positionNote != null) {
            positionNote.setVisibility(prefs.hudPos() == Prefs.HUD_POS_CUSTOM
                    ? View.VISIBLE : View.GONE);
        }
        findViewById(R.id.btn_save).setBackground(
                HudStyle.rounded(this, prefs.accent(), 16));

        syncing = false;
    }

    private void paintSwatch(TextView view, int color, boolean accent, boolean selected) {
        GradientDrawable shape = new GradientDrawable();
        if (accent) {
            shape.setShape(GradientDrawable.OVAL);
        } else {
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setCornerRadius(dp(10));
        }
        shape.setColor(color);
        if (selected) {
            shape.setStroke(dp(3), 0xFFFFFFFF);
        }
        view.setBackground(shape);
        view.setText(selected ? "✓" : "");
        view.setGravity(Gravity.CENTER);
        view.setTextSize(16);
        view.setTextColor(isLight(color) ? DARK : 0xFFFFFFFF);
    }

    private void paintSegment(TextView view, boolean selected) {
        view.setTextColor(selected ? DARK : HudStyle.TEXT);
        view.setBackground(selected
                ? HudStyle.rounded(this, prefs.accent(), 10)
                : HudStyle.box(this, CARD, 10, CARD_STROKE));
    }

    private void onHudChanged() {
        syncHudOptions();
        restartHud();
    }

    private void restartHud() {
        if (!prefs.hud()) {
            return;
        }
        stopService(new Intent(this, OverlayService.class));
        try {
            startForegroundService(new Intent(this, OverlayService.class));
        } catch (Exception ignored) {
        }
    }

    private static boolean isLight(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (0.299 * r + 0.587 * g + 0.114 * b) > 165;
    }

    private int dp(int value) {
        return HudStyle.dp(this, value);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
