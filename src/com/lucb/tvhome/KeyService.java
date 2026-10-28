package com.lucb.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public final class KeyService extends AccessibilityService {
    static final int KEY_TCL_SETTINGS = 4077;
    static final int KEY_TCL_QUICK_PANEL = 4139;
    static final int KEY_TCL_QUICK_SETTINGS = 4514;

    private static KeyService instance;
    private volatile boolean hasInputs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean longFired;
    private final Runnable longPress = () -> {
        longFired = true;
        try {
            startActivity(new Intent(this, ControlActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Log.w("TVHome", "keys: " + e);
        }
    };

    // Mise en veille via l'accessibilité (Android 9+). Si l'écran reste allumé, la télé ne la gère pas : on masque l'option.
    static boolean canSleep(Context ctx) {
        return Build.VERSION.SDK_INT >= 28 && instance != null
                && !ctx.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("noSleep", false);
    }

    static void sleep(Context ctx) {
        KeyService s = instance;
        if (s == null || Build.VERSION.SDK_INT < 28) return;
        // L'écran s'est-il vraiment éteint ? (le délai ci-dessous se fige pendant la veille et ne court qu'au réveil)
        Context app = ctx.getApplicationContext();
        ScreenOff off = new ScreenOff();
        app.registerReceiver(off, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        boolean ok = s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        Log.i("TVHome", "keys: sleep -> " + ok);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                app.unregisterReceiver(off);
            } catch (Exception ignored) {
            }
            PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            if (!off.seen && pm != null && pm.isInteractive()) {
                app.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putBoolean("noSleep", true).apply();
                Log.i("TVHome", "keys: sleep not supported, option hidden");
            }
        }, 10000);
    }

    static void repair(Context ctx) {
        if (instance != null || ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        try {
            android.content.ContentResolver cr = ctx.getContentResolver();
            String me = ctx.getPackageName() + "/" + KeyService.class.getName();
            String cur = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur == null || !cur.contains(me)) return;
            String without = cur.replace(":" + me, "").replace(me + ":", "").replace(me, "");
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, without);
            new Handler(Looper.getMainLooper()).postDelayed(() -> ensureEnabled(ctx), 800);
            Log.i("TVHome", "keys: service restarted");
        } catch (Exception e) {
            Log.w("TVHome", "keys: " + e);
        }
    }

    static final class ScreenOff extends BroadcastReceiver {
        volatile boolean seen;

        @Override
        public void onReceive(Context c, Intent i) {
            seen = true;
        }
    }

    static boolean running() {
        return instance != null;
    }

    static void ensureEnabled(Context ctx) {
        if (ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        try {
            android.content.ContentResolver cr = ctx.getContentResolver();
            String me = ctx.getPackageName() + "/" + KeyService.class.getName();
            String cur = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur != null && cur.contains(me)) return;
            String next = cur == null || cur.isEmpty() || "null".equals(cur) ? me : cur + ":" + me;
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next);
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            Log.i("TVHome", "keys: service re-enabled");
        } catch (Exception e) {
            Log.w("TVHome", "keys: " + e);
        }
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
        new Thread(() -> hasInputs = !Inputs.load(this).isEmpty()).start();
        Log.i("TVHome", "keys: service connected");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        Intent target;
        if (isSettingsKey(code)) {
            // Appui court : paramètres de la télé ; appui long : centre de contrôle.
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    longFired = false;
                    handler.postDelayed(longPress, 500);
                }
                return true;
            }
            handler.removeCallbacks(longPress);
            if (longFired || event.isCanceled()) return true;
            target = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        } else if (code == KeyEvent.KEYCODE_TV_INPUT && hasInputs) {
            // Touche Entrées/Source standard : notre menu des entrées. Sans entrée (box), le système la garde.
            target = new Intent(this, HomeActivity.class).setAction(HomeActivity.ACTION_INPUTS);
        } else {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            Log.i("TVHome", "keys: " + code + " -> " + target.getAction());
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                Log.w("TVHome", "keys: " + e);
                return false;
            }
        }
        return true;
    }

    private static boolean isSettingsKey(int code) {
        return code == KeyEvent.KEYCODE_SETTINGS || code == KEY_TCL_SETTINGS
                || code == KEY_TCL_QUICK_PANEL || code == KEY_TCL_QUICK_SETTINGS;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }
}
