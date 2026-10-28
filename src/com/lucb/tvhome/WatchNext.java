package com.lucb.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class WatchNext {
    static final class Item {
        String pkg, title, detail, art, intentUri;
        long position, duration, engaged;
        int type;

        String key() {
            return pkg + "|" + title + "|" + detail;
        }
    }

    private static final String[] COLUMNS = {
            TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
            TvContract.WatchNextPrograms.COLUMN_BROWSABLE,
            TvContract.WatchNextPrograms.COLUMN_TITLE,
            TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
            TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
            TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
            TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI,
            TvContract.WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
            TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_ASPECT_RATIO,
            TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
            TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER
    };

    private WatchNext() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("upnext", Context.MODE_PRIVATE);
    }

    static Set<String> excluded(Context ctx) {
        return new HashSet<>(prefs(ctx).getStringSet("excluded", new HashSet<>()));
    }

    static void setExcluded(Context ctx, String pkg, boolean exclude) {
        Set<String> set = excluded(ctx);
        if (exclude) set.add(pkg);
        else set.remove(pkg);
        prefs(ctx).edit().putStringSet("excluded", set).apply();
    }

    static List<String> publishers(Context ctx, Set<String> allowed) {
        Set<String> out = new LinkedHashSet<>();
        for (Item it : load(ctx, allowed, new HashSet<>(), 100)) out.add(it.pkg);
        return new ArrayList<>(out);
    }

    static List<Item> load(Context ctx, Set<String> allowed, int max) {
        return load(ctx, allowed, excluded(ctx), max);
    }

    // Tous les programmes publiés, en une seule requête.
    static List<Item> all(Context ctx, Set<String> allowed) {
        return raw(ctx, allowed, excluded(ctx));
    }

    static Map<String, List<Item>> grouped(List<Item> all, int perApp) {
        Map<String, List<Item>> out = new LinkedHashMap<>();
        Map<String, Set<String>> titles = new HashMap<>();
        for (Item it : all) {
            List<Item> list = out.get(it.pkg);
            if (list == null) {
                list = new ArrayList<>();
                out.put(it.pkg, list);
                titles.put(it.pkg, new HashSet<>());
            }
            if (list.size() < perApp && titles.get(it.pkg).add(it.title)) list.add(it);
        }
        return out;
    }

    static List<Item> load(Context ctx, Set<String> allowed, Set<String> excluded, int max) {
        List<Item> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Item it : raw(ctx, allowed, excluded)) {
            if (seen.add(it.pkg)) unique.add(it);
            if (unique.size() >= max) break;
        }
        return unique;
    }

    private static List<Item> raw(Context ctx, Set<String> allowed, Set<String> excluded) {
        List<Item> out = new ArrayList<>();
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, COLUMNS, null, null, null);
            if (c == null) {
                Log.i("TVHome", "watchnext: null cursor");
                return out;
            }
            int total = 0;
            while (c.moveToNext()) {
                total++;
                Item it = new Item();
                it.pkg = str(c, TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME);
                if (it.pkg == null || !allowed.contains(it.pkg) || excluded.contains(it.pkg)) continue;
                if (num(c, TvContract.WatchNextPrograms.COLUMN_BROWSABLE, 1) == 0) continue;
                it.title = str(c, TvContract.WatchNextPrograms.COLUMN_TITLE);
                if (it.title == null || it.title.isEmpty()) continue;
                it.type = (int) num(c, TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, 0);
                it.position = num(c, TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, 0);
                it.duration = num(c, TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS, 0);
                it.engaged = num(c, TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, 0);
                it.intentUri = str(c, TvContract.WatchNextPrograms.COLUMN_INTENT_URI);
                it.art = pickArt(c);
                it.detail = episode(c);
                out.add(it);
            }
            Log.i("TVHome", "watchnext: rows=" + total + " kept=" + out.size());
        } catch (Exception e) {
            Log.w("TVHome", "watchnext: " + e);
        } finally {
            if (c != null) c.close();
        }
        out.sort((a, b) -> Long.compare(b.engaged, a.engaged));
        return out;
    }

    private static String pickArt(Cursor c) {
        String poster = str(c, TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI);
        String thumb = str(c, TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI);
        long posterRatio = num(c, TvContract.WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, -1);
        long thumbRatio = num(c, TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_ASPECT_RATIO, -1);
        boolean posterWide = posterRatio >= 0 && posterRatio <= 2;
        boolean thumbWide = thumbRatio >= 0 && thumbRatio <= 2;
        if (thumb != null && thumbWide && !posterWide) return thumb;
        if (poster != null) return poster;
        return thumb;
    }

    private static String episode(Cursor c) {
        String season = str(c, TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER);
        String ep = str(c, TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER);
        if (season != null && ep != null) return "S" + season + ", É" + ep;
        if (ep != null) return "Épisode " + ep;
        return null;
    }

    static String verb(Item it) {
        switch (it.type) {
            case TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT:
                return "À suivre";
            case TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEW:
                return "Nouveau";
            case TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_WATCHLIST:
                return "Ma liste";
            default:
                return "Reprendre";
        }
    }

    private static String str(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) return null;
        String v = c.getString(i);
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }

    private static long num(Cursor c, String col, long def) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) return def;
        return c.getLong(i);
    }
}
