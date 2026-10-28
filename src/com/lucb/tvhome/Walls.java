package com.lucb.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Walls {
    interface Listener {
        void onWall(Bitmap bitmap, String caption);
    }

    private static final String UA = "TVHomeLauncher/1.0 (personal Android TV launcher)";
    static final String[][] THEMES = {
            {"all", "Tout, en mélange"},
            {"nature", "Nature et paysages"},
            {"mountains", "Montagnes"},
            {"water", "Lacs et rivières"},
            {"waterfalls", "Cascades"},
            {"coasts", "Côtes et îles"},
            {"art", "Œuvres d'art"},
            {"architecture", "Architecture"},
            {"night", "Nuit et étoiles"},
            {"birds", "Oiseaux"},
            {"illustrations", "Illustrations (hors ligne)"}
    };
    private static final List<String> PHOTO_THEMES = Arrays.asList(
            "mountains", "water", "waterfalls", "coasts", "art", "architecture", "night", "birds");
    private static final String[][] ASSETS = {
            {"walls/nature-crepuscule.jpg", "Crépuscule sur les crêtes"},
            {"walls/nature-lac-brume.jpg", "Lac dans la brume"},
            {"walls/nature-aurore.jpg", "Aurore boréale"}
    };
    private static final int MAX_FILES = 60;
    private static final long REFRESH_MS = 6L * 3600 * 1000;

    private final Context ctx;
    private final File dir;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final List<String> queue = new ArrayList<>();
    private String current = "";

    Walls(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.dir = new File(this.ctx.getFilesDir(), "walls");
        dir.mkdirs();
    }

    void next(Listener listener) {
        io.execute(() -> {
            String key = pick();
            Bitmap bmp = decode(key);
            if (bmp == null) {
                key = ASSETS[0][0];
                bmp = decode(key);
            }
            if (bmp == null) return;
            current = key;
            String caption = caption(key);
            Bitmap result = bmp;
            main.post(() -> listener.onWall(result, caption));
        });
    }

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences("walls", Context.MODE_PRIVATE);
    }

    static final long[] INTERVALS = {15_000, 30_000, 45_000, 60_000, 120_000, 300_000, 600_000, 1_800_000, 3_600_000};
    static final String[] INTERVAL_LABELS = {"15 secondes", "30 secondes", "45 secondes", "1 minute", "2 minutes",
            "5 minutes", "10 minutes", "30 minutes", "1 heure"};

    long interval() {
        return prefs().getLong("interval", 45_000);
    }

    void setInterval(long ms) {
        prefs().edit().putLong("interval", ms).apply();
    }

    static String intervalLabel(long ms) {
        for (int i = 0; i < INTERVALS.length; i++) if (INTERVALS[i] == ms) return INTERVAL_LABELS[i];
        return (ms / 1000) + " secondes";
    }

    String theme() {
        return prefs().getString("theme", "all");
    }

    static String label(String theme) {
        for (String[] t : THEMES) if (t[0].equals(theme)) return t[1];
        return THEMES[0][1];
    }

    void setTheme(String theme) {
        prefs().edit().putString("theme", theme).apply();
        io.execute(queue::clear);
    }

    boolean hasPhotos(String theme) {
        return "illustrations".equals(theme) || !photos(theme).isEmpty();
    }

    void refreshIfNeeded(Runnable onFirstPhotos) {
        io.execute(() -> {
            String theme = theme();
            if ("illustrations".equals(theme)) return;
            String key = "lastFetch_" + theme;
            long last = prefs().getLong(key, 0);
            int before = photos(theme).size();
            if (before >= 12 && System.currentTimeMillis() - last < REFRESH_MS) return;
            int added = 0;
            for (int attempt = 0; attempt < 4 && added < 6; attempt++) {
                String t = "all".equals(theme) ? PHOTO_THEMES.get(rnd.nextInt(PHOTO_THEMES.size())) : theme;
                try {
                    added += fetchBatch(t, 6 - added);
                } catch (Exception e) {
                    Log.w("TVHome", "fetch walls " + t + ": " + e);
                }
            }
            Log.i("TVHome", "walls theme=" + theme + " fetched=" + added + " cached=" + photos(theme).size());
            prefs().edit().putLong(key, System.currentTimeMillis()).apply();
            prune();
            if (before == 0 && added > 0 && onFirstPhotos != null) {
                queue.clear();
                main.post(onFirstPhotos);
            }
        });
    }

    private List<File> photos() {
        return photos("all");
    }

    private List<File> photos(String theme) {
        List<String> prefixes = "nature".equals(theme)
                ? Arrays.asList("nature_", "mountains_", "water_", "waterfalls_", "coasts_")
                : Arrays.asList(theme + "_");
        File[] files = dir.listFiles((d, n) -> {
            if (!n.endsWith(".jpg")) return false;
            if ("all".equals(theme)) return true;
            for (String p : prefixes) if (n.startsWith(p)) return true;
            return false;
        });
        return files == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
    }

    private String pick() {
        if (queue.isEmpty()) {
            String theme = theme();
            List<File> files = "illustrations".equals(theme) ? new ArrayList<>() : photos(theme);
            if (files.size() >= 3) {
                for (File f : files) queue.add(f.getAbsolutePath());
            } else {
                for (File f : files) queue.add(f.getAbsolutePath());
                for (String[] a : ASSETS) queue.add(a[0]);
            }
            Collections.shuffle(queue, rnd);
            if (queue.size() > 1 && queue.get(0).equals(current)) queue.add(queue.remove(0));
        }
        return queue.remove(0);
    }

    private Bitmap decode(String key) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = open(key)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= 1920) sample *= 2;
            opts.inSampleSize = sample;
            try (InputStream in = open(key)) {
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception | OutOfMemoryError e) {
            if (key.startsWith("/")) new File(key).delete();
            return null;
        }
    }

    private InputStream open(String key) throws Exception {
        return key.startsWith("/") ? new FileInputStream(key) : ctx.getAssets().open(key);
    }

    private String caption(String key) {
        for (String[] a : ASSETS) if (a[0].equals(key)) return a[1];
        File txt = new File(key.replaceAll("\\.jpg$", ".txt"));
        try (InputStream in = new FileInputStream(txt)) {
            return new String(readAll(in), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static List<String> categories(String theme) {
        switch (theme) {
            case "nature":
                return Arrays.asList("Featured pictures of landscapes", "Featured pictures of mountains", "Featured pictures of lakes",
                        "Featured pictures of waterfalls", "Featured pictures of coasts");
            case "water":
                return Arrays.asList("Featured pictures of lakes", "Featured pictures of watercourses", "Featured pictures of bodies of water");
            case "waterfalls":
                return Arrays.asList("Featured pictures of waterfalls");
            case "coasts":
                return Arrays.asList("Featured pictures of coasts", "Featured pictures of islands");
            case "art":
                return Arrays.asList("Featured pictures of paintings");
            case "architecture":
                return Arrays.asList("Featured pictures of architecture", "Featured pictures of buildings");
            case "night":
                return Arrays.asList("Featured night photography", "Featured pictures of the Milky Way");
            case "birds":
                return Arrays.asList("Featured pictures of birds in flight", "Featured pictures of birds");
            default:
                return Arrays.asList("Featured pictures of mountains", "Featured pictures of landscapes");
        }
    }

    private JSONArray search(String query) throws Exception {
        String url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2"
                + "&generator=search&gsrnamespace=6&gsrlimit=40&gsrsort=random"
                + "&gsrsearch=" + URLEncoder.encode(query + " filemime:image/jpeg", "UTF-8")
                + "&prop=imageinfo&iiprop=url%7Csize%7Cmime%7Cextmetadata&iiurlwidth=1920"
                + "&iiextmetadatafilter=ObjectName%7CArtist%7CLicenseShortName";
        JSONObject json = new JSONObject(new String(get(url), StandardCharsets.UTF_8));
        JSONObject q = json.optJSONObject("query");
        return q == null ? null : q.optJSONArray("pages");
    }

    private int fetchBatch(String theme, int wanted) throws Exception {
        List<String> cats = categories(theme);
        String category = cats.get(rnd.nextInt(cats.size()));
        JSONArray pages = search("deepcat:\"" + category + "\"");
        if (pages == null || pages.length() == 0) pages = search("incategory:\"" + category.replace(' ', '_') + "\"");
        if (pages == null) return 0;
        double minRatio = "art".equals(theme) ? 1.2 : 1.3;
        List<JSONObject> candidates = new ArrayList<>();
        for (int i = 0; i < pages.length(); i++) candidates.add(pages.getJSONObject(i));
        Collections.shuffle(candidates, rnd);
        int added = 0;
        for (JSONObject page : candidates) {
            if (added >= wanted) break;
            JSONArray infos = page.optJSONArray("imageinfo");
            if (infos == null || infos.length() == 0) continue;
            JSONObject info = infos.getJSONObject(0);
            if (!"image/jpeg".equals(info.optString("mime"))) continue;
            double w = info.optDouble("width", 0), h = info.optDouble("height", 0);
            if (h <= 0 || w / h < minRatio || w / h > 2.1 || w < 1600) continue;
            String thumb = info.optString("thumburl", info.optString("url"));
            if (thumb.isEmpty()) continue;
            String title = page.optString("title");
            String name = theme + "_" + sha1(title);
            File jpg = new File(dir, name + ".jpg");
            if (jpg.exists()) continue;
            byte[] data = get(thumb);
            if (data.length < 50_000) continue;
            File tmp = new File(dir, name + ".part");
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(data);
            }
            JSONObject meta = info.optJSONObject("extmetadata");
            String caption = buildCaption(title, meta);
            try (OutputStream out = new FileOutputStream(new File(dir, name + ".txt"))) {
                out.write(caption.getBytes(StandardCharsets.UTF_8));
            }
            if (tmp.renameTo(jpg)) added++;
        }
        return added;
    }

    private static String buildCaption(String title, JSONObject meta) {
        String name = meta(meta, "ObjectName");
        if (name.isEmpty()) {
            name = title.replaceFirst("^File:", "").replaceAll("\\.[A-Za-z]+$", "").replace('_', ' ');
        }
        String artist = meta(meta, "Artist");
        String license = meta(meta, "LicenseShortName");
        StringBuilder sb = new StringBuilder(shorten(name, 70));
        if (!artist.isEmpty()) sb.append("  ·  ").append(shorten(artist, 40));
        if (!license.isEmpty()) sb.append("  ·  ").append(license);
        sb.append("  ·  Wikimedia Commons");
        return sb.toString();
    }

    private static String meta(JSONObject meta, String key) {
        if (meta == null) return "";
        JSONObject o = meta.optJSONObject(key);
        if (o == null) return "";
        String v = Html.fromHtml(o.optString("value"), Html.FROM_HTML_MODE_LEGACY).toString();
        return v.replaceAll("\\s+", " ").trim();
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1).trim() + "…";
    }

    private void prune() {
        List<File> files = photos();
        if (files.size() <= MAX_FILES) return;
        files.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < files.size() - MAX_FILES; i++) {
            File f = files.get(i);
            f.delete();
            new File(f.getAbsolutePath().replaceAll("\\.jpg$", ".txt")).delete();
        }
    }

    private static byte[] get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestProperty("User-Agent", UA);
        con.setConnectTimeout(15000);
        con.setReadTimeout(30000);
        try {
            if (con.getResponseCode() != 200) throw new IllegalStateException("HTTP " + con.getResponseCode());
            try (InputStream in = con.getInputStream()) {
                return readAll(in);
            }
        } finally {
            con.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static String sha1(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(String.format("%02x", d[i]));
        return sb.toString();
    }
}
