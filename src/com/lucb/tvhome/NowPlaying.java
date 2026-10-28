package com.lucb.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

// Lecture en cours : suit les sessions média actives des autres applis (autorisation d'écoute des notifications requise).
final class NowPlaying {
    private final Context ctx;
    private final ComponentName comp;
    private final MediaSessionManager msm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<MediaController> watched = new ArrayList<>();
    private final Watch watch;
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::rewatch;
    private boolean started;

    NowPlaying(Context ctx, Runnable changed) {
        this.ctx = ctx;
        comp = new ComponentName(ctx, MediaListener.class);
        msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
        watch = new Watch(changed);
    }

    void start() {
        if (started || msm == null) return;
        try {
            msm.addOnActiveSessionsChangedListener(sessions, comp, handler);
            started = true;
            rewatch(msm.getActiveSessions(comp));
        } catch (SecurityException e) {
            started = false;
        }
    }

    void stop() {
        if (!started) return;
        started = false;
        msm.removeOnActiveSessionsChangedListener(sessions);
        rewatch(null);
    }

    private void rewatch(List<MediaController> list) {
        for (MediaController c : watched) c.unregisterCallback(watch);
        watched.clear();
        if (list != null) {
            for (MediaController c : list) {
                c.registerCallback(watch, handler);
                watched.add(c);
            }
        }
        watch.changed.run();
    }

    // La session qui joue, sinon la plus récente en pause ; null s'il n'y a rien à montrer.
    MediaController current() {
        MediaController paused = null;
        for (MediaController c : watched) {
            if (TextUtils.isEmpty(title(c))) continue;
            PlaybackState s = c.getPlaybackState();
            int st = s == null ? PlaybackState.STATE_NONE : s.getState();
            if (st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING) return c;
            boolean recent = s != null && SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime() < 30 * 60_000L;
            if (paused == null && st == PlaybackState.STATE_PAUSED && recent) paused = c;
        }
        return paused;
    }

    static boolean playing(MediaController c) {
        PlaybackState s = c.getPlaybackState();
        return s != null && (s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_BUFFERING);
    }

    static String title(MediaController c) {
        MediaMetadata m = c.getMetadata();
        if (m == null) return null;
        CharSequence t = m.getText(MediaMetadata.METADATA_KEY_TITLE);
        if (TextUtils.isEmpty(t)) t = m.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        return t == null ? null : t.toString().trim();
    }

    String subtitle(MediaController c) {
        MediaMetadata m = c.getMetadata();
        CharSequence a = m == null ? null : m.getText(MediaMetadata.METADATA_KEY_ARTIST);
        if (TextUtils.isEmpty(a) && m != null) a = m.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        String app;
        try {
            PackageManager pm = ctx.getPackageManager();
            app = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(c.getPackageName(), 0)));
        } catch (Exception e) {
            app = "";
        }
        if (TextUtils.isEmpty(a)) return app;
        return app.isEmpty() ? a.toString() : a + "  ·  " + app;
    }

    static Bitmap art(MediaController c) {
        MediaMetadata m = c.getMetadata();
        if (m == null) return null;
        Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        return b;
    }

    static final class Watch extends MediaController.Callback {
        final Runnable changed;

        Watch(Runnable changed) {
            this.changed = changed;
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            changed.run();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            changed.run();
        }

        @Override
        public void onSessionDestroyed() {
            changed.run();
        }
    }
}
