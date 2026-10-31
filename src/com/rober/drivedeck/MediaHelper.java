package com.rober.drivedeck;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;

import java.util.List;

final class MediaHelper {
    interface Listener {
        void onMedia(String title, String artist, boolean playing, Bitmap art);
    }

    private final Context context;
    private final Listener listener;
    private MediaController controller;
    private Bitmap lastArt;
    private String artKey = "\u0000";

    MediaHelper(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void refresh() {
        MediaController c = pick();
        controller = c;
        if (c == null) {
            listener.onMedia("", "", false, null);
            return;
        }
        MediaMetadata md = c.getMetadata();
        PlaybackState ps = c.getPlaybackState();
        String title = md == null ? "" : text(md.getText(MediaMetadata.METADATA_KEY_TITLE));
        String artist = md == null ? "" : text(md.getText(MediaMetadata.METADATA_KEY_ARTIST));
        boolean playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
        if (md != null) {
            String key = title + "\u0000" + artist;
            if (!key.equals(artKey)) {
                artKey = key;
                lastArt = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (lastArt == null) {
                    lastArt = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
                }
                if (lastArt != null && (lastArt.getWidth() > 512 || lastArt.getHeight() > 512)) {
                    lastArt = Bitmap.createScaledBitmap(lastArt, 512, 512, true);
                }
            }
        }
        listener.onMedia(title, artist, playing, md == null ? null : lastArt);
    }

    void toggle() {
        MediaController c = current();
        if (c == null) {
            return;
        }
        try {
            PlaybackState ps = c.getPlaybackState();
            if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) {
                c.getTransportControls().pause();
            } else {
                c.getTransportControls().play();
            }
        } catch (Exception ignored) {
        }
    }

    void next() {
        MediaController c = current();
        if (c != null) {
            try {
                c.getTransportControls().skipToNext();
            } catch (Exception ignored) {
            }
        }
    }

    void previous() {
        MediaController c = current();
        if (c != null) {
            try {
                c.getTransportControls().skipToPrevious();
            } catch (Exception ignored) {
            }
        }
    }

    private MediaController current() {
        if (controller == null) {
            controller = pick();
        }
        return controller;
    }

    private MediaController pick() {
        try {
            MediaSessionManager manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) {
                return null;
            }
            List<MediaController> sessions = manager.getActiveSessions(
                    new ComponentName(context, NavListenerService.class));
            MediaController first = null;
            for (MediaController mc : sessions) {
                if (first == null) {
                    first = mc;
                }
                if (Prefs.SPOTIFY.equals(mc.getPackageName())) {
                    return mc;
                }
            }
            return first;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
