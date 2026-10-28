package com.lucb.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

// Sons de navigation façon Apple TV (fichiers générés par tools/sons.py).
final class Sounds {
    private static final float VOLUME = 0.35f;

    private final SoundPool pool;
    private final SharedPreferences prefs;
    private int move, select;

    Sounds(Context ctx) {
        prefs = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE);
        pool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        try {
            move = pool.load(ctx.getAssets().openFd("sounds/move.wav"), 1);
            select = pool.load(ctx.getAssets().openFd("sounds/select.wav"), 1);
        } catch (Exception e) {
            Log.w("TVHome", "sounds: " + e);
        }
    }

    boolean enabled() {
        return prefs.getBoolean("sounds", true);
    }

    void setEnabled(boolean on) {
        prefs.edit().putBoolean("sounds", on).apply();
    }

    void move() {
        play(move);
    }

    void select() {
        play(select);
    }

    private void play(int id) {
        if (id != 0 && enabled()) pool.play(id, VOLUME, VOLUME, 1, 0, 1f);
    }

    void release() {
        pool.release();
    }
}
