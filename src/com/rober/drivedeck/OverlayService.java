package com.rober.drivedeck;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class OverlayService extends Service implements MediaHelper.Listener {
    static final String ACTION_STOP = "com.rober.drivedeck.action.STOP";
    static volatile boolean running = false;
    static volatile boolean ownAppVisible = false;

    private WindowManager wm;
    private View root;
    private WindowManager.LayoutParams params;
    private TextView nav;
    private TextView track;
    private TextView play;
    private MediaHelper media;
    private Prefs prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Runnable tick;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        prefs = new Prefs(this);
        startForeground(1, notification());
        tick = new Runnable() {
            @Override
            public void run() {
                if (media != null) {
                    media.refresh();
                }
                renderNav();
                handler.postDelayed(this, 1000);
            }
        };
        media = new MediaHelper(this, this);
        setup();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            new Prefs(this).setHud(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        handler.removeCallbacks(tick);
        handler.post(tick);
        return START_STICKY;
    }

    private void setup() {
        if (!Perms.overlay(this)) {
            stopSelf();
            return;
        }
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        root = LayoutInflater.from(this).inflate(R.layout.overlay, null);
        nav = (TextView) root.findViewById(R.id.ov_nav);
        track = (TextView) root.findViewById(R.id.ov_track);
        play = (TextView) root.findViewById(R.id.ov_play);
        root.findViewById(R.id.ov_prev).setOnClickListener(v -> media.previous());
        root.findViewById(R.id.ov_next).setOnClickListener(v -> media.next());
        play.setOnClickListener(v -> media.toggle());
        root.findViewById(R.id.ov_close).setOnClickListener(v -> {
            prefs.setHud(false);
            stopSelf();
        });
        HudStyle.apply(this, root, prefs);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        configureWindow(false);
        try {
            wm.addView(root, params);
        } catch (Exception e) {
            stopSelf();
            return;
        }
        root.post(() -> configureWindow(true));
        dragHandle(root.findViewById(R.id.ov_grip));
        renderNav();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (root != null) {
            configureWindow(true);
        }
    }

    private boolean landscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void configureWindow(boolean update) {
        if (params == null || root == null) {
            return;
        }
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int barH = root.getHeight() > 0
                ? root.getHeight()
                : HudStyle.dp(this, HudStyle.playSizeDp(prefs) + 12);
        if (landscape()) {
            int widthDp = prefs.hudSize() == 0 ? 340 : prefs.hudSize() == 2 ? 560 : 460;
            int maxWidth = Math.max(0, screenW - HudStyle.dp(this, 16));
            params.width = Math.min(maxWidth, HudStyle.dp(this, widthDp));
            params.horizontalMargin = 0f;
            params.gravity = Gravity.TOP | Gravity.LEFT;
            int x;
            int y;
            if (prefs.hudPos() == Prefs.HUD_POS_CUSTOM) {
                x = prefs.hudX() >= 0 ? prefs.hudX() : (screenW - params.width) / 2;
                y = prefs.hudLandY() >= 0 ? prefs.hudLandY() : (screenH - barH) / 2;
            } else {
                x = (screenW - params.width) / 2;
                if (prefs.hudPos() == Prefs.HUD_POS_TOP) {
                    y = HudStyle.dp(this, 40);
                } else if (prefs.hudPos() == Prefs.HUD_POS_CENTER) {
                    y = (screenH - barH) / 2;
                } else {
                    y = screenH - barH - HudStyle.dp(this, 80);
                }
            }
            params.x = Math.max(0, Math.min(Math.max(0, screenW - params.width), x));
            params.y = Math.max(0, Math.min(Math.max(0, screenH - barH), y));
        } else {
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.horizontalMargin = 0.02f;
            params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            int pos = prefs.hudPos();
            int y;
            if (pos == Prefs.HUD_POS_CUSTOM && prefs.hudY() >= 0) {
                y = prefs.hudY();
            } else if (pos == Prefs.HUD_POS_CENTER) {
                y = Math.max(0, (screenH - barH) / 2);
            } else if (pos == Prefs.HUD_POS_TOP) {
                y = Math.max(0, screenH - barH - HudStyle.dp(this, 40));
            } else {
                y = HudStyle.dp(this, 80);
            }
            params.x = 0;
            params.y = y;
        }
        if (update) {
            try {
                wm.updateViewLayout(root, params);
            } catch (Exception ignored) {
            }
        }
    }

    private void dragHandle(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float startX;
            float startY;
            int startParamX;
            int startParamY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        startParamX = params.x;
                        startParamY = params.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int screenW = getResources().getDisplayMetrics().widthPixels;
                        int screenH = getResources().getDisplayMetrics().heightPixels;
                        if (landscape()) {
                            int maxX = Math.max(0, screenW - params.width);
                            int maxY = Math.max(0, screenH - root.getHeight());
                            params.x = Math.max(0, Math.min(maxX,
                                    startParamX + (int) (event.getRawX() - startX)));
                            params.y = Math.max(0, Math.min(maxY,
                                    startParamY + (int) (event.getRawY() - startY)));
                        } else {
                            int maxY = Math.max(0, screenH - root.getHeight() - 32);
                            int y = startParamY - (int) (event.getRawY() - startY);
                            params.y = Math.max(0, Math.min(maxY, y));
                        }
                        wm.updateViewLayout(root, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (landscape()) {
                            prefs.setHudX(params.x);
                            prefs.setHudLandY(params.y);
                        } else {
                            prefs.setHudY(params.y);
                        }
                        prefs.setHudPos(Prefs.HUD_POS_CUSTOM);
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void renderNav() {
        if (root == null) {
            return;
        }
        root.setVisibility(ownAppVisible ? View.GONE : View.VISIBLE);
        if (ownAppVisible) {
            return;
        }
        String line = prefs.hudShowRoute() ? DriveState.hudLine() : "";
        nav.setText(line);
        nav.setVisibility(line.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onMedia(String title, String artist, boolean playing, Bitmap art) {
        if (track == null) {
            return;
        }
        if (!prefs.hudShowTrack()) {
            track.setVisibility(View.INVISIBLE);
            play.setText(playing ? "❚❚" : "▶︎");
            return;
        }
        track.setVisibility(View.VISIBLE);
        if (title.isEmpty()) {
            track.setText("Sin música");
            track.setTextColor(HudStyle.MUTED);
        } else {
            track.setText(title);
            track.setTextColor(HudStyle.TEXT);
        }
        play.setText(playing ? "❚❚" : "▶︎");
    }

    private Notification notification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel("hud", "HUD flotante",
                NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        nm.createNotificationChannel(channel);

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, OverlayService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, "hud")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("DriveDeck HUD")
                .setContentText("Controles sobre Maps y Waze")
                .setOngoing(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_stat), "Cerrar", stop).build())
                .build();
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(tick);
        if (root != null && wm != null) {
            try {
                wm.removeView(root);
            } catch (Exception ignored) {
            }
            root = null;
        }
        super.onDestroy();
    }
}
