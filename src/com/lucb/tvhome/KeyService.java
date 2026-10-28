package com.lucb.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public final class KeyService extends AccessibilityService {
    static final int KEY_TCL_SETTINGS = 4077;
    static final int KEY_TCL_QUICK_PANEL = 4139;
    static final int KEY_TCL_QUICK_SETTINGS = 4514;

    private static KeyService instance;

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
        if (!isSettingsKey(code)) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            Log.i("TVHome", "keys: " + code + " -> settings");
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
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
