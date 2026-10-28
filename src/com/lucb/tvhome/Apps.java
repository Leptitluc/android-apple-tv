package com.lucb.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.provider.Settings;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class Apps {
    static final String SETTINGS_KEY = "__settings__";

    static final Set<String> HIDDEN = new HashSet<>(Arrays.asList(
            BuildConfig.PKG,
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
            "com.android.tv.settings",
            "com.android.settings"
    ));

    static final Set<String> DEFAULT_HIDDEN_LABELS = new HashSet<>(Arrays.asList(
            "quick panel", "t-solo", "tcl home", "tcl home passive", "safety guard"
    ));

    private Apps() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("apps", Context.MODE_PRIVATE);
    }

    static Set<String> hidden(Context ctx) {
        return new HashSet<>(prefs(ctx).getStringSet("hidden", new HashSet<>()));
    }

    static void setHidden(Context ctx, String pkg, boolean hide) {
        Set<String> set = hidden(ctx);
        if (hide) set.add(pkg);
        else set.remove(pkg);
        prefs(ctx).edit().putStringSet("hidden", set).apply();
    }

    static void showAll(Context ctx) {
        prefs(ctx).edit().putStringSet("hidden", new HashSet<>()).apply();
    }

    static List<AppEntry> load(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Map<String, AppEntry> map = new LinkedHashMap<>();
        collect(ctx, pm, Intent.CATEGORY_LEANBACK_LAUNCHER, true, map);
        collect(ctx, pm, Intent.CATEGORY_LAUNCHER, false, map);
        Set<String> hidden = hidden(ctx);
        if (!prefs(ctx).getBoolean("hiddenInit", false)) {
            for (AppEntry a : map.values()) {
                if (DEFAULT_HIDDEN_LABELS.contains(a.label.toLowerCase(Locale.ROOT))) hidden.add(a.pkg);
            }
            prefs(ctx).edit().putStringSet("hidden", hidden).putBoolean("hiddenInit", true).apply();
        }
        map.keySet().removeAll(hidden);
        map.values().removeIf(a -> isTcl(pm, a) || isSettingsDuplicate(a));
        List<AppEntry> list = new ArrayList<>(map.values());
        Collator collator = Collator.getInstance(Locale.FRANCE);
        collator.setStrength(Collator.PRIMARY);
        list.sort((a, b) -> collator.compare(a.label, b.label));
        list.add(settingsEntry(ctx));
        return list;
    }

    static final class Manageable {
        final AppEntry app;
        final boolean hidden;

        Manageable(AppEntry app, boolean hidden) {
            this.app = app;
            this.hidden = hidden;
        }
    }

    static List<Manageable> manageable(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Map<String, AppEntry> map = new LinkedHashMap<>();
        collect(ctx, pm, Intent.CATEGORY_LEANBACK_LAUNCHER, true, map);
        collect(ctx, pm, Intent.CATEGORY_LAUNCHER, false, map);
        map.values().removeIf(a -> isTcl(pm, a) || isSettingsDuplicate(a));
        Set<String> hidden = hidden(ctx);
        List<AppEntry> all = new ArrayList<>(map.values());
        Collator collator = Collator.getInstance(Locale.FRANCE);
        collator.setStrength(Collator.PRIMARY);
        all.sort((a, b) -> collator.compare(a.label, b.label));
        List<Manageable> out = new ArrayList<>();
        for (AppEntry a : all) out.add(new Manageable(a, hidden.contains(a.pkg)));
        return out;
    }

    static final Set<String> SETTINGS_LABELS = new HashSet<>(Arrays.asList(
            "réglages", "reglages", "paramètres", "parametres", "settings", "tv settings"
    ));

    private static boolean isTcl(PackageManager pm, AppEntry a) {
        if (!a.pkg.startsWith("com.tcl.") && !a.label.toLowerCase(Locale.ROOT).contains("tcl")) return false;
        try {
            return (pm.getApplicationInfo(a.pkg, 0).flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    private static boolean isSettingsDuplicate(AppEntry a) {
        return SETTINGS_LABELS.contains(a.label.toLowerCase(Locale.ROOT).trim());
    }

    private static void collect(Context ctx, PackageManager pm, String category, boolean leanback, Map<String, AppEntry> map) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            ActivityInfo ai = ri.activityInfo;
            String pkg = ai.packageName;
            if (HIDDEN.contains(pkg) || map.containsKey(pkg)) continue;
            if (!ai.enabled || !ai.applicationInfo.enabled) continue;
            String label = String.valueOf(ri.loadLabel(pm)).trim();
            Drawable banner = leanback ? ai.loadBanner(pm) : null;
            if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
            if (banner == null) banner = generated(ctx, ri.loadIcon(pm));
            map.put(pkg, new AppEntry(pkg, new ComponentName(pkg, ai.name), label, banner, leanback, null));
        }
    }

    private static AppEntry settingsEntry(Context ctx) {
        return new AppEntry(SETTINGS_KEY, null, "Réglages", settingsBanner(ctx), false, new Intent(Settings.ACTION_SETTINGS));
    }

    // Tuile Réglages façon tvOS : engrenage gris foncé dans un engrenage, sur fond argenté.
    private static Drawable settingsBanner(Context ctx) {
        int w = 320, h = 180;
        float cx = w / 2f, cy = h / 2f;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, 0xFFF2F2F4, 0xFFB9BCC1, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);

        Shader dark = new LinearGradient(0, cy - 68, 0, cy + 68, 0xFF74777D, 0xFF3C3F44, Shader.TileMode.CLAMP);
        p.setShader(dark);
        p.setShadowLayer(5, 0, 2, 0x47000000);
        c.drawPath(gear(cx, cy, 68, 60, 32, 0.42f), p);
        p.clearShadowLayer();
        p.setShader(new LinearGradient(0, cy - 50, 0, cy + 50, 0xFF9DA0A6, 0xFFDADCDF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, 50, p);

        p.setShader(dark);
        for (int i = 0; i < 6; i++) {
            c.save();
            c.rotate(i * 60f, cx, cy);
            c.drawRect(cx - 3.5f, cy - 51, cx + 3.5f, cy, p);
            c.restore();
        }
        p.setShadowLayer(3, 0, 1.5f, 0x40000000);
        c.drawPath(gear(cx, cy, 31, 25, 12, 0.45f), p);
        p.clearShadowLayer();
        p.setShader(new LinearGradient(0, cy - 10, 0, cy + 10, 0xFFA7AAAF, 0xFFE2E3E6, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, 10, p);
        return new BitmapDrawable(ctx.getResources(), bmp);
    }

    private static Path gear(float cx, float cy, float outer, float root, int teeth, float duty) {
        Path path = new Path();
        double step = 2 * Math.PI / teeth;
        double flank = step * (1 - duty) / 4;
        for (int i = 0; i < teeth; i++) {
            double a = i * step - Math.PI / 2;
            double[][] pts = {{root, a}, {outer, a + flank}, {outer, a + flank + step * duty}, {root, a + 2 * flank + step * duty}};
            for (int k = 0; k < pts.length; k++) {
                float x = cx + (float) (pts[k][0] * Math.cos(pts[k][1]));
                float y = cy + (float) (pts[k][0] * Math.sin(pts[k][1]));
                if (i == 0 && k == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
        }
        path.close();
        return path;
    }

    static Drawable generated(Context ctx, Drawable icon) {
        int w = 320, h = 180;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        int base = dominant(icon);
        float[] hsv = new float[3];
        Color.colorToHSV(base, hsv);
        hsv[1] = Math.min(hsv[1], 0.55f);
        hsv[2] = 0.42f;
        int top = Color.HSVToColor(hsv);
        hsv[2] = 0.24f;
        int bottom = Color.HSVToColor(hsv);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        int s = 104;
        icon.setBounds((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2);
        icon.draw(c);
        return new BitmapDrawable(ctx.getResources(), bmp);
    }

    private static int dominant(Drawable icon) {
        Bitmap small = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(small);
        icon.setBounds(0, 0, 24, 24);
        icon.draw(c);
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                int px = small.getPixel(x, y);
                if (Color.alpha(px) < 200) continue;
                float[] hsv = new float[3];
                Color.colorToHSV(px, hsv);
                int weight = hsv[1] > 0.25f ? 4 : 1;
                r += Color.red(px) * weight;
                g += Color.green(px) * weight;
                b += Color.blue(px) * weight;
                n += weight;
            }
        }
        small.recycle();
        if (n == 0) return 0xFF34495E;
        return Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
    }
}
