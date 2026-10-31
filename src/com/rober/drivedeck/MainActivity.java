package com.rober.drivedeck;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int REQ_LOCATION = 10;
    private static final int REQ_NOTIFICATIONS = 11;

    private Prefs prefs;
    private MediaHelper media;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView clock;
    private TextView date;
    private TextView speed;
    private TextView heading;
    private TextView headingDeg;
    private TextView battery;
    private TextView banner;
    private TextView track;
    private TextView artist;
    private TextView play;
    private TextView albumPh;
    private TextView hudLabel;
    private ImageView album;

    private LocationManager locationManager;
    private SensorManager sensorManager;
    private long lastBearingMs;

    private final float[] rotationMatrix = new float[9];
    private final float[] orientation = new float[3];
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE d MMM", Locale.forLanguageTag("es-ES"));

    private Runnable tick;
    private BroadcastReceiver batteryReceiver;
    private LocationListener locationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideBars();

        clock = (TextView) findViewById(R.id.clock);
        date = (TextView) findViewById(R.id.date);
        speed = (TextView) findViewById(R.id.speed);
        heading = (TextView) findViewById(R.id.heading);
        headingDeg = (TextView) findViewById(R.id.headingDeg);
        battery = (TextView) findViewById(R.id.battery);
        banner = (TextView) findViewById(R.id.banner);
        track = (TextView) findViewById(R.id.track);
        artist = (TextView) findViewById(R.id.artist);
        play = (TextView) findViewById(R.id.btn_play);
        album = (ImageView) findViewById(R.id.album);
        albumPh = (TextView) findViewById(R.id.album_ph);
        hudLabel = (TextView) findViewById(R.id.hud_label);
        heading.setText("—");
        headingDeg.setText("—");

        media = new MediaHelper(this, this::onMedia);
        initCallbacks();
        wire();
        tickClock();

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
        if (!Perms.location(this)) {
            requestLocation();
        }
    }

    private void initCallbacks() {
        tick = new Runnable() {
            @Override
            public void run() {
                tickClock();
                if (media != null) {
                    media.refresh();
                }
                updateHudLabel();
                handler.postDelayed(this, 1000);
            }
        };
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                boolean charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
                if (level < 0 || scale <= 0) {
                    battery.setText("");
                    return;
                }
                int percent = Math.round(level * 100f / scale);
                battery.setText((charging ? "⚡ " : "") + percent + "%");
            }
        };
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                float kmh = location.hasSpeed() ? location.getSpeed() * 3.6f : 0f;
                speed.setText(Math.round(kmh) + "");
                if (location.hasBearing() && location.getSpeed() > 2.5f) {
                    setHeading(location.getBearing());
                    lastBearingMs = System.currentTimeMillis();
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };
    }

    private void wire() {
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        View navTile = findViewById(R.id.tile_nav);
        navTile.setOnClickListener(v -> openNav(null));
        navTile.setOnLongClickListener(v -> {
            openOtherNav();
            return true;
        });

        findViewById(R.id.tile_home).setOnClickListener(v -> goTo(prefs.home(), "casa"));
        findViewById(R.id.tile_work).setOnClickListener(v -> goTo(prefs.work(), "el trabajo"));

        View musicTile = findViewById(R.id.tile_music);
        musicTile.setOnClickListener(v -> openSpotify());
        musicTile.setOnLongClickListener(v -> {
            if (media != null) {
                media.toggle();
            }
            return true;
        });

        findViewById(R.id.tile_call).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:")));
            } catch (Exception e) {
                toast("No se pudo abrir el teléfono");
            }
        });
        findViewById(R.id.tile_assist).setOnClickListener(v -> assistant());
        findViewById(R.id.tile_hud).setOnClickListener(v -> toggleHud());

        findViewById(R.id.btn_prev).setOnClickListener(v -> {
            if (media != null) {
                media.previous();
            }
        });
        findViewById(R.id.btn_play).setOnClickListener(v -> {
            if (media != null) {
                media.toggle();
            }
        });
        findViewById(R.id.btn_next).setOnClickListener(v -> {
            if (media != null) {
                media.next();
            }
        });

        banner.setOnClickListener(v -> {
            if (!Perms.notifications(this)) {
                Perms.openNotificationSettings(this);
            } else if (!Perms.location(this)) {
                requestLocation();
            } else if (!Perms.overlay(this)) {
                Perms.openOverlaySettings(this);
            } else if (Prefs.WAZE.equals(prefs.navPackage()) && !Perms.accessibility(this)) {
                Perms.openAccessibilitySettings(this);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        OverlayService.ownAppVisible = true;
        setNavLabel();
        HudStyle.applyDashboard(this, findViewById(R.id.tile_nav), findViewById(R.id.btn_play), prefs);
        refreshBanner();
        handler.removeCallbacks(tick);
        handler.post(tick);
        startLocation();
        if (sensorManager == null) {
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        }
        Sensor rotation = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rotation != null) {
            sensorManager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_UI);
        }
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (prefs.hud() && Perms.overlay(this)) {
            try {
                startForegroundService(new Intent(this, OverlayService.class));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onPause() {
        OverlayService.ownAppVisible = false;
        handler.removeCallbacks(tick);
        stopLocation();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        try {
            unregisterReceiver(batteryReceiver);
        } catch (Exception ignored) {
        }
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideBars();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) {
            return;
        }
        if (System.currentTimeMillis() - lastBearingMs < 3000) {
            return;
        }
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
        SensorManager.getOrientation(rotationMatrix, orientation);
        float degrees = (float) Math.toDegrees(orientation[0]);
        if (degrees < 0) {
            degrees += 360;
        }
        int screenRotation = getWindowManager().getDefaultDisplay().getRotation();
        if (screenRotation == Surface.ROTATION_90) {
            degrees += 90;
        } else if (screenRotation == Surface.ROTATION_180) {
            degrees += 180;
        } else if (screenRotation == Surface.ROTATION_270) {
            degrees += 270;
        }
        setHeading(degrees % 360);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && Perms.location(this)) {
            startLocation();
        }
        refreshBanner();
    }

    private void onMedia(String title, String artistName, boolean playing, Bitmap art) {
        if (title.isEmpty()) {
            track.setText("Sin reproducción");
            artist.setText("Abre Spotify o pulsa play");
        } else {
            track.setText(title);
            artist.setText(artistName);
        }
        play.setText(playing ? "❚❚" : "▶︎");
        if (art != null) {
            album.setImageBitmap(art);
            albumPh.setVisibility(View.GONE);
        } else {
            album.setImageBitmap(null);
            albumPh.setVisibility(View.VISIBLE);
        }
    }

    private void tickClock() {
        Date now = new Date();
        clock.setText(timeFormat.format(now));
        String text = dateFormat.format(now);
        if (!text.isEmpty()) {
            text = Character.toUpperCase(text.charAt(0)) + text.substring(1);
        }
        date.setText(text);
    }

    private void setHeading(float degrees) {
        heading.setText(cardinal(degrees));
        headingDeg.setText(Math.round(degrees) + "°");
    }

    private static String cardinal(float degrees) {
        String[] names = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
        return names[Math.round(degrees / 45f) % 8];
    }

    private void setNavLabel() {
        TextView label = (TextView) findViewById(R.id.nav_label);
        label.setText(Prefs.WAZE.equals(prefs.navPackage()) ? "WAZE" : "MAPS");
    }

    private void updateHudLabel() {
        if (hudLabel == null) {
            return;
        }
        boolean on = prefs.hud() && OverlayService.running;
        hudLabel.setText(on ? "HUD ✓" : "HUD");
        hudLabel.setTextColor(on ? 0xFF6EE7B7 : 0xFFF5F7FA);
    }

    private void refreshBanner() {
        if (!Perms.notifications(this)) {
            banner.setText("⚠ Sin acceso a notificaciones · toca para activar Spotify y las indicaciones");
            banner.setVisibility(View.VISIBLE);
        } else if (!Perms.location(this)) {
            banner.setText("⚠ Sin ubicación · toca para activar la velocidad");
            banner.setVisibility(View.VISIBLE);
        } else if (!Perms.overlay(this)) {
            banner.setText("⚠ Activa «Mostrar sobre otras apps» para usar el HUD");
            banner.setVisibility(View.VISIBLE);
        } else if (Prefs.WAZE.equals(prefs.navPackage()) && !Perms.accessibility(this)) {
            banner.setText("⚠ Activa accesibilidad para ver el giro de Waze en el HUD");
            banner.setVisibility(View.VISIBLE);
        } else {
            banner.setVisibility(View.GONE);
        }
    }

    private void requestLocation() {
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
    }

    private void startLocation() {
        if (!Perms.location(this) || locationManager != null) {
            return;
        }
        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager != null) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        1000L, 0f, locationListener, Looper.getMainLooper());
            }
        } catch (Exception ignored) {
            locationManager = null;
        }
    }

    private void stopLocation() {
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Exception ignored) {
            }
            locationManager = null;
        }
    }

    private void openNav(String address) {
        String pkg = prefs.navPackage();
        boolean waze = Prefs.WAZE.equals(pkg);
        String label = waze ? "Waze" : "Maps";
        if (address == null || address.trim().isEmpty()) {
            launchApp(pkg, label);
            return;
        }
        String query = Uri.encode(address.trim());
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(waze
                ? "waze://?q=" + query + "&navigate=yes"
                : "google.navigation:q=" + query + "&mode=d"));
        intent.setPackage(pkg);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(waze
                    ? "https://waze.com/ul?q=" + query + "&navigate=yes"
                    : "geo:0,0?q=" + query));
            fallback.setPackage(pkg);
            try {
                startActivity(fallback);
            } catch (Exception second) {
                toast("No se pudo abrir la navegación");
            }
        }
    }

    private void openOtherNav() {
        String pkg = Prefs.WAZE.equals(prefs.navPackage()) ? Prefs.MAPS : Prefs.WAZE;
        launchApp(pkg, Prefs.WAZE.equals(pkg) ? "Waze" : "Maps");
    }

    private void launchApp(String pkg, String label) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent == null) {
            toast(label + " no está instalado");
            return;
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            toast("No se pudo abrir " + label);
        }
    }

    private void goTo(String address, String name) {
        if (address == null || address.trim().isEmpty()) {
            toast("Configura " + name + " en Ajustes");
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        openNav(address);
    }

    private void openSpotify() {
        launchApp(Prefs.SPOTIFY, "Spotify");
    }

    private void assistant() {
        try {
            startActivity(new Intent(Intent.ACTION_ASSIST));
            return;
        } catch (Exception ignored) {
        }
        try {
            startActivity(new Intent(Intent.ACTION_VOICE_COMMAND));
        } catch (Exception e) {
            toast("No hay asistente de voz");
        }
    }

    private void toggleHud() {
        if (!Perms.overlay(this)) {
            Perms.openOverlaySettings(this);
            toast("Activa «Mostrar sobre otras apps» y vuelve a pulsar HUD");
            return;
        }
        boolean on = !prefs.hud();
        prefs.setHud(on);
        if (on) {
            try {
                startForegroundService(new Intent(this, OverlayService.class));
            } catch (Exception e) {
                prefs.setHud(false);
                toast("No se pudo iniciar el HUD");
            }
        } else {
            stopService(new Intent(this, OverlayService.class));
        }
        updateHudLabel();
    }

    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
