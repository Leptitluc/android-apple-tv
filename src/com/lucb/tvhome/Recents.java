package com.lucb.tvhome;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Recents {
    static final List<String> DEFAULTS = Arrays.asList(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom",
            "com.apple.atve.androidtv.appletv",
            "com.crunchyroll.crunchyroid",
            "com.plexapp.android",
            "net.oqee.androidtv.store",
            "com.spotify.tv.android"
    );

    private static final String PREFS = "recents";
    private static final String KEY = "history";
    private static final long WINDOW = 30L * 24 * 3600 * 1000;

    private Recents() {}

    static boolean hasUsageAccess(Context ctx) {
        AppOpsManager aom = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
        int mode = aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    static List<String> top(Context ctx, Set<String> launchable, int count) {
        Set<String> out = new LinkedHashSet<>();
        for (String p : fromUsage(ctx)) if (launchable.contains(p)) out.add(p);
        for (String p : history(ctx)) if (launchable.contains(p)) out.add(p);
        for (String p : DEFAULTS) if (launchable.contains(p)) out.add(p);
        for (String p : launchable) if (!Apps.SETTINGS_KEY.equals(p)) out.add(p);
        List<String> list = new ArrayList<>(out);
        list.remove(Apps.SETTINGS_KEY);
        return list.size() > count ? list.subList(0, count) : list;
    }

    private static List<String> fromUsage(Context ctx) {
        List<String> out = new ArrayList<>();
        if (!hasUsageAccess(ctx)) return out;
        UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - WINDOW, now);
        if (stats == null) return out;
        Map<String, Long> last = new HashMap<>();
        for (UsageStats s : stats) {
            if (s.getTotalTimeInForeground() < 5000) continue;
            String p = s.getPackageName();
            if (Apps.HIDDEN.contains(p)) continue;
            Long prev = last.get(p);
            if (prev == null || s.getLastTimeUsed() > prev) last.put(p, s.getLastTimeUsed());
        }
        out.addAll(last.keySet());
        out.sort((a, b) -> Long.compare(last.get(b), last.get(a)));
        return out;
    }

    private static List<String> history(Context ctx) {
        String raw = prefs(ctx).getString(KEY, "");
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) return out;
        out.addAll(Arrays.asList(raw.split(",")));
        return out;
    }

    static void record(Context ctx, String pkg) {
        if (Apps.SETTINGS_KEY.equals(pkg)) return;
        List<String> list = history(ctx);
        list.remove(pkg);
        list.add(0, pkg);
        while (list.size() > 20) list.remove(list.size() - 1);
        prefs(ctx).edit().putString(KEY, TextUtils.join(",", list)).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
