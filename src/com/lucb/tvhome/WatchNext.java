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
import java.util.TreeMap;

final class WatchNext {
    static final class Item {
        String pkg, title, detail, art, intentUri, channel;
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
        for (Item it : previews(ctx, allowed)) out.add(it.pkg);
        return new ArrayList<>(out);
    }

    static List<Item> load(Context ctx, Set<String> allowed, int max) {
        return load(ctx, allowed, excluded(ctx), max);
    }

    // Tous les programmes publiés, en une seule requête.
    static List<Item> all(Context ctx, Set<String> allowed) {
        return raw(ctx, allowed, excluded(ctx));
    }

    // Chaînes de recommandations (« Recommandé », « Abonnements »…) : l'aperçu des applis qui n'ont rien
    // dans « À suivre », comme les rangées de l'accueil Android TV. Chaîne par défaut de chaque appli.
    static List<Item> previews(Context ctx, Set<String> allowed) {
        List<Item> out = new ArrayList<>();
        Map<Long, String> channels = new HashMap<>();
        Map<String, Long> first = new HashMap<>();
        Map<String, String> seen = new TreeMap<>();
        Set<String> excluded = excluded(ctx);
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(TvContract.Channels.CONTENT_URI, new String[]{TvContract.Channels._ID,
                    TvContract.Channels.COLUMN_PACKAGE_NAME, TvContract.Channels.COLUMN_DISPLAY_NAME,
                    TvContract.Channels.COLUMN_BROWSABLE, TvContract.Channels.COLUMN_TYPE}, null, null, null);
            while (c != null && c.moveToNext()) {
                // Le marquage « affichable » est posé par l'accueil d'origine (désactivé) : on ne s'y fie pas.
                if (!TvContract.Channels.TYPE_PREVIEW.equals(str(c, TvContract.Channels.COLUMN_TYPE))) continue;
                long id = num(c, TvContract.Channels._ID, -1);
                String pkg = str(c, TvContract.Channels.COLUMN_PACKAGE_NAME);
                channels.put(id, str(c, TvContract.Channels.COLUMN_DISPLAY_NAME));
                // Chaîne par défaut de l'appli : la première qu'elle a créée.
                if (pkg != null && (!first.containsKey(pkg) || id < first.get(pkg))) first.put(pkg, id);
            }
            if (c != null) c.close();
            c = ctx.getContentResolver().query(TvContract.PreviewPrograms.CONTENT_URI, new String[]{
                    TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME, TvContract.PreviewPrograms.COLUMN_CHANNEL_ID,
                    TvContract.PreviewPrograms.COLUMN_BROWSABLE, TvContract.PreviewPrograms.COLUMN_TITLE,
                    TvContract.PreviewPrograms.COLUMN_WEIGHT, TvContract.PreviewPrograms.COLUMN_INTENT_URI,
                    TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI,
                    TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, TvContract.PreviewPrograms.COLUMN_THUMBNAIL_ASPECT_RATIO,
                    TvContract.PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER, TvContract.PreviewPrograms.COLUMN_EPISODE_DISPLAY_NUMBER},
                    null, null, null);
            while (c != null && c.moveToNext()) {
                Item it = new Item();
                it.pkg = str(c, TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME);
                long ch = num(c, TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, -1);
                if (it.pkg != null) seen.put(it.pkg, seen.containsKey(it.pkg) ? String.valueOf(Integer.parseInt(seen.get(it.pkg)) + 1) : "1");
                if (it.pkg == null || !allowed.contains(it.pkg) || excluded.contains(it.pkg)) continue;
                if (first.get(it.pkg) == null || first.get(it.pkg) != ch) continue;
                it.title = str(c, TvContract.PreviewPrograms.COLUMN_TITLE);
                if (it.title == null) continue;
                it.channel = channels.get(ch);
                it.type = -1;
                it.engaged = num(c, TvContract.PreviewPrograms.COLUMN_WEIGHT, 0);
                it.intentUri = str(c, TvContract.PreviewPrograms.COLUMN_INTENT_URI);
                it.art = pickArt(c);
                it.detail = episode(c);
                out.add(it);
            }
            Log.i("TVHome", "previews: channels=" + channels.size() + " programs=" + seen + " kept=" + out.size());
        } catch (Exception e) {
            Log.w("TVHome", "previews: " + e);
        } finally {
            if (c != null) c.close();
        }
        out.sort((a, b) -> Long.compare(b.engaged, a.engaged));
        return out;
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
            // Par appli : programmes publiés et, s'ils sont écartés, pourquoi (journal de diagnostic).
            Map<String, String> seen = new TreeMap<>();
            while (c.moveToNext()) {
                total++;
                Item it = new Item();
                it.pkg = str(c, TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME);
                String why = it.pkg == null ? "sans appli" : !allowed.contains(it.pkg) ? "appli masquée"
                        : excluded.contains(it.pkg) ? "exclue" : num(c, TvContract.WatchNextPrograms.COLUMN_BROWSABLE, 1) == 0 ? "non affichable"
                        : str(c, TvContract.WatchNextPrograms.COLUMN_TITLE) == null ? "sans titre" : "ok";
                String key = it.pkg + (why.equals("ok") ? "" : " (" + why + ")");
                seen.put(key, seen.containsKey(key) ? String.valueOf(Integer.parseInt(seen.get(key)) + 1) : "1");
                if (!why.equals("ok")) continue;
                it.title = str(c, TvContract.WatchNextPrograms.COLUMN_TITLE);
                it.type = (int) num(c, TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, 0);
                it.position = num(c, TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, 0);
                it.duration = num(c, TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS, 0);
                it.engaged = num(c, TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, 0);
                it.intentUri = str(c, TvContract.WatchNextPrograms.COLUMN_INTENT_URI);
                it.art = pickArt(c);
                it.detail = episode(c);
                out.add(it);
            }
            Log.i("TVHome", "watchnext: rows=" + total + " kept=" + out.size() + " " + seen);
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
        if (it.type == -1) return it.channel != null ? it.channel : "Recommandé";
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
