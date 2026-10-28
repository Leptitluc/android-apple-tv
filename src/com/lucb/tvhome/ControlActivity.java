package com.lucb.tvhome;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Centre de contrôle façon tvOS : panneau à droite, par-dessus n'importe quelle appli.
public final class ControlActivity extends Activity {
    static final String ACTION = BuildConfig.PKG + ".action.CONTROL";
    private static final int[] TIMERS = {0, 15, 30, 60, 90};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private FrameLayout root;
    private LinearLayout panel, rows;
    private View scrim, volumeRow, slot;
    private TextView volumeValue, timerValue;
    private AudioManager audio;
    private SharedPreferences prefs;
    private Sounds sounds;
    private NowPlaying nowPlaying;
    private LinearLayout nowCard;
    private ImageView nowArt, nowToggleIcon;
    private TextView nowTitle, nowSub;
    private long keyAt;
    // Pas de session : on masque la carte, sauf si le focus y est (changement de piste en cours) ; on revérifie plus tard.
    private final Runnable hideNow = this::hideNow;
    private boolean closing;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        prefs = getSharedPreferences("ui", Context.MODE_PRIVATE);
        sounds = new Sounds(this);
        nowPlaying = new NowPlaying(this, this::updateNow);
        setContentView(build());
        nowPlaying.start();
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((o, n) -> {
            if (n != null && SystemClock.uptimeMillis() - keyAt < 250) sounds.move();
        });
        bg.execute(() -> {
            boolean inputs = !Inputs.load(this).isEmpty();
            handler.post(() -> {
                if (inputs && !isFinishing()) addInputs();
            });
        });
    }

    private View build() {
        root = new FrameLayout(this);
        scrim = new HomeActivity.Overlay(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0x00000000, 0x33000000, 0x99000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, dp(20), 0, dp(12));
        GradientDrawable bgd = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xF22A2D33, 0xF2181A1E});
        bgd.setCornerRadius(dp(28));
        bgd.setStroke(Math.max(1, dp(1)), 0x33FFFFFF);
        panel.setBackground(bgd);
        panel.setElevation(dp(24));

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        style(clock, 32, 0xFFFFFFFF, 600);
        clock.setPadding(dp(16), 0, dp(16), 0);
        // Contenu défilant : avec la carte « Lecture en cours », tout ne tient pas toujours en hauteur.
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(14), 0, dp(14), dp(4));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipToPadding(false);
        sv.addView(inner);
        panel.addView(sv);
        inner.addView(clock);
        TextClock date = new TextClock(this);
        String day = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
        date.setFormat24Hour(day);
        date.setFormat12Hour(day);
        style(date, 14, 0x99FFFFFF, 500);
        date.setPadding(dp(16), dp(2), dp(16), dp(12));
        inner.addView(date);

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        // La ligne agrandie au focus déborde dans la marge intérieure : elle ne doit pas y être rognée.
        rows.setClipChildren(false);
        inner.setClipChildren(false);
        inner.setClipToPadding(false);
        inner.addView(rows);

        // Emplacement réservé à la carte « Lecture en cours ».
        slot = new FrameLayout(this);
        slot.setVisibility(View.GONE);
        rows.addView(slot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        volumeRow = row(R.drawable.ic_s_volume, "Volume", "", v -> toggleMute());
        volumeValue = (TextView) volumeRow.getTag();
        rows.addView(volumeRow, rowLp());
        updateVolume();
        if (KeyService.canSleep(this)) {
            View timer = row(R.drawable.ic_s_sleep, "Minuterie de veille", "", v -> nextTimer());
            timerValue = (TextView) timer.getTag();
            rows.addView(timer, rowLp());
            updateTimer();
            rows.addView(row(R.drawable.ic_s_power, "Mettre en veille", null, v -> sleepNow()), rowLp());
        }
        rows.addView(row(R.drawable.ic_s_wifi, "Réseau", network(), v -> open(Settings.ACTION_WIFI_SETTINGS)), rowLp());
        rows.addView(row(R.drawable.ic_gear, "Réglages", null, v -> open(Settings.ACTION_SETTINGS)), rowLp());

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(380), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        lp.setMargins(0, dp(26), dp(26), dp(26));
        root.addView(panel, lp);

        panel.setTranslationX(dp(420));
        panel.animate().translationX(0f).setDuration(280).setInterpolator(new DecelerateInterpolator(2f)).start();
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(280).start();
        volumeRow.requestFocus();
        return root;
    }

    // Carte « Lecture en cours » : construite une fois, mise à jour en place pour ne pas perdre le focus.
    private void updateNow() {
        MediaController c = nowPlaying.current();
        handler.removeCallbacks(hideNow);
        if (c == null) {
            // Changement de piste : la session disparaît un instant ; on attend avant de masquer la carte (et de perdre le focus).
            if (slot.getVisibility() == View.VISIBLE) handler.postDelayed(hideNow, 1500);
            return;
        }
        if (nowCard == null) buildNowCard();
        Bitmap art = NowPlaying.art(c);
        nowArt.setImageBitmap(art);
        nowArt.setVisibility(art != null ? View.VISIBLE : View.GONE);
        nowTitle.setText(NowPlaying.title(c));
        nowSub.setText(nowPlaying.subtitle(c));
        nowToggleIcon.setImageResource(NowPlaying.playing(c) ? R.drawable.ic_pause : R.drawable.ic_play);
        slot.setVisibility(View.VISIBLE);
    }

    private void hideNow() {
        if (nowPlaying.current() != null) return;
        if (nowCard != null && inside(getCurrentFocus(), nowCard)) handler.postDelayed(hideNow, 1500);
        else slot.setVisibility(View.GONE);
    }

    private void buildNowCard() {
        nowCard = new LinearLayout(this);
        nowCard.setOrientation(LinearLayout.VERTICAL);
        nowCard.setPadding(dp(14), dp(14), dp(14), dp(10));
        GradientDrawable bgd = new GradientDrawable();
        bgd.setCornerRadius(dp(18));
        bgd.setColor(0x14FFFFFF);
        nowCard.setBackground(bgd);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        nowArt = new ImageView(this);
        nowArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        nowArt.setOutlineProvider(new Tile.Rounded(dp(10)));
        nowArt.setClipToOutline(true);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(dp(54), dp(54));
        al.rightMargin = dp(12);
        top.addView(nowArt, al);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        nowTitle = new TextView(this);
        style(nowTitle, 16, 0xFFFFFFFF, 600);
        nowTitle.setMaxLines(2);
        nowTitle.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(nowTitle);
        nowSub = new TextView(this);
        style(nowSub, 13, 0x99FFFFFF, 500);
        nowSub.setSingleLine(true);
        nowSub.setEllipsize(TextUtils.TruncateAt.END);
        nowSub.setPadding(0, dp(4), 0, 0);
        texts.addView(nowSub);
        top.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        nowCard.addView(top);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        // Les boutons grossissent au focus : ni la rangée ni la carte ne doivent les rogner.
        buttons.setPadding(0, dp(4), 0, dp(4));
        buttons.setClipChildren(false);
        buttons.setClipToPadding(false);
        nowCard.setClipChildren(false);
        nowCard.setClipToPadding(false);
        ((FrameLayout) slot).setClipChildren(false);
        buttons.addView(round(R.drawable.ic_prev, v -> transport(-1)), roundLp());
        View toggle = round(R.drawable.ic_play, v -> transport(0));
        nowToggleIcon = (ImageView) toggle.getTag();
        buttons.addView(toggle, roundLp());
        buttons.addView(round(R.drawable.ic_next, v -> transport(1)), roundLp());
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = dp(10);
        nowCard.addView(buttons, bl);
        ((FrameLayout) slot).addView(nowCard);
        LinearLayout.LayoutParams sl = (LinearLayout.LayoutParams) slot.getLayoutParams();
        sl.bottomMargin = dp(10);
        slot.setLayoutParams(sl);
    }

    private View round(int icon, View.OnClickListener click) {
        FrameLayout b = new FrameLayout(this);
        b.setFocusable(true);
        b.setClickable(true);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        b.addView(iv, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        b.setTag(iv);
        GradientDrawable bgd = new GradientDrawable();
        bgd.setShape(GradientDrawable.OVAL);
        bgd.setColor(0x1FFFFFFF);
        b.setBackground(bgd);
        b.setOnFocusChangeListener((v, focused) -> {
            bgd.setColor(focused ? 0xF7FFFFFF : 0x1FFFFFFF);
            iv.setImageTintList(ColorStateList.valueOf(focused ? 0xFF15171A : 0xFFFFFFFF));
            float s = focused ? 1.1f : 1f;
            b.animate().scaleX(s).scaleY(s).setDuration(140).start();
        });
        b.setOnClickListener(click);
        return b;
    }

    private LinearLayout.LayoutParams roundLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
        lp.setMargins(dp(10), 0, dp(10), 0);
        return lp;
    }

    private void transport(int dir) {
        MediaController c = nowPlaying.current();
        if (c == null) return;
        MediaController.TransportControls t = c.getTransportControls();
        if (dir < 0) t.skipToPrevious();
        else if (dir > 0) t.skipToNext();
        else if (NowPlaying.playing(c)) t.pause();
        else t.play();
    }

    private void addInputs() {
        View r = row(R.drawable.ic_s_input, "Entrées", null, v -> {
            startActivity(new Intent(this, HomeActivity.class).setAction(HomeActivity.ACTION_INPUTS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
            overridePendingTransition(0, 0);
        });
        rows.addView(r, rows.indexOfChild(volumeRow) + 1, rowLp());
    }

    // Ligne de menu : icône, libellé, valeur ; au focus, fond blanc et texte sombre comme sur tvOS.
    private View row(int icon, String label, String value, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setPadding(dp(16), 0, dp(16), 0);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        r.addView(iv, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView tv = new TextView(this);
        style(tv, 16, 0xF2FFFFFF, 500);
        tv.setText(label);
        tv.setSingleLine(true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = dp(14);
        r.addView(tv, tl);
        TextView val = new TextView(this);
        style(val, 14, 0x99FFFFFF, 500);
        val.setSingleLine(true);
        if (value != null) val.setText(value);
        r.addView(val);
        r.setTag(val);
        GradientDrawable bgd = new GradientDrawable();
        bgd.setCornerRadius(dp(14));
        bgd.setColor(0x00FFFFFF);
        r.setBackground(bgd);
        r.setOnFocusChangeListener((v, focused) -> {
            bgd.setColor(focused ? 0xF7FFFFFF : 0x00FFFFFF);
            int c = focused ? 0xFF15171A : 0xF2FFFFFF;
            tv.setTextColor(c);
            val.setTextColor(focused ? 0x9915171A : 0x99FFFFFF);
            iv.setImageTintList(ColorStateList.valueOf(c));
            float s = focused ? 1.03f : 1f;
            r.animate().scaleX(s).scaleY(s).setDuration(140).start();
        });
        r.setOnClickListener(click);
        return r;
    }

    private LinearLayout.LayoutParams rowLp() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
    }

    private void style(TextView t, int sp, int color, int weight) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Fonts.get(this, weight));
        t.setIncludeFontPadding(false);
    }

    private void adjustVolume(int dir) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI);
        handler.postDelayed(this::updateVolume, 120);
    }

    private void toggleMute() {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
        handler.postDelayed(this::updateVolume, 120);
    }

    private void updateVolume() {
        int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        volumeValue.setText(audio.isStreamMute(AudioManager.STREAM_MUSIC) ? "Muet" : "◀  " + Math.round(cur * 100f / max) + " %  ▶");
    }

    private String network() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (nc == null) return "Hors ligne";
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi";
        return "Connecté";
    }

    private void nextTimer() {
        long at = prefs.getLong("sleepAt", 0);
        int cur = at > System.currentTimeMillis() ? minutesLeft(at) : 0;
        int next = 0;
        for (int m : TIMERS) {
            if (m > cur) {
                next = m;
                break;
            }
        }
        if (next == TIMERS[TIMERS.length - 1] && cur >= next) next = 0;
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(this, SleepReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        if (next == 0) {
            prefs.edit().remove("sleepAt").apply();
        } else {
            long when = System.currentTimeMillis() + next * 60_000L;
            prefs.edit().putLong("sleepAt", when).apply();
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
        updateTimer();
    }

    private void updateTimer() {
        long at = prefs.getLong("sleepAt", 0);
        if (at > System.currentTimeMillis() && PendingIntent.getBroadcast(this, 0, new Intent(this, SleepReceiver.class),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE) == null) {
            prefs.edit().remove("sleepAt").apply();
            at = 0;
        }
        timerValue.setText(at > System.currentTimeMillis() ? minutesLeft(at) + " min" : "Désactivée");
    }

    private static int minutesLeft(long at) {
        return (int) Math.ceil((at - System.currentTimeMillis()) / 60_000.0);
    }

    private void sleepNow() {
        finish();
        overridePendingTransition(0, 0);
        // File à part : la fermeture du panneau vide la file de l'activité.
        Context app = getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(() -> KeyService.sleep(app), 300);
    }

    private void open(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish();
        overridePendingTransition(0, 0);
    }

    private void close() {
        if (closing) return;
        closing = true;
        scrim.animate().alpha(0f).setDuration(200).start();
        panel.animate().translationX(dp(420)).setDuration(220).setInterpolator(new DecelerateInterpolator(1.5f))
                .withEndAction(() -> {
                    finish();
                    overridePendingTransition(0, 0);
                }).start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (down) keyAt = SystemClock.uptimeMillis();
        if (down && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) && e.getRepeatCount() == 0) sounds.select();
        View f = getCurrentFocus();
        if (code == KeyEvent.KEYCODE_BACK) {
            if (!down) close();
            return true;
        }
        if (f == volumeRow && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            if (down) adjustVolume(code == KeyEvent.KEYCODE_DPAD_LEFT ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_RAISE);
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            View next = f == null ? null : f.focusSearch(code == KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT : View.FOCUS_RIGHT);
            if (nowCard != null && inside(f, nowCard) && inside(next, nowCard)) return super.dispatchKeyEvent(e);
            if (down && code == KeyEvent.KEYCODE_DPAD_LEFT) close();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private static boolean inside(View v, View group) {
        while (v != null) {
            if (v == group) return true;
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isFinishing()) finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        bg.shutdownNow();
        sounds.release();
        nowPlaying.stop();
        super.onDestroy();
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
