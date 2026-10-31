package com.lucb.tvhome;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeActivity extends Activity {
    interface OnPick {
        void pick(int index);
    }

    static final String ACTION_INPUTS = BuildConfig.PKG + ".action.INPUTS";

    private static final int COLS = 6;
    private static final int DOCK_COUNT = 6;
    private static final long FADE_MS = 1_800;
    private static final long IDLE_MS = 90_000;
    private static final long ZOOM_PAUSE_MS = 4_000;
    private static final long SHELF_DELAY_MS = 400;
    // Délai entre le retour à l'accueil et l'ouverture d'une entrée : l'appli TV du fabricant doit avoir fini
    // de se fermer, sinon sa fermeture tardive coupe l'image HDMI qu'elle vient de rouvrir (écran noir).
    private static final long INPUT_SETTLE_MS = 2_000;
    private static final int BAND_TOP = 80;
    private static final long ART_CACHE_BYTES = 40L * 1024 * 1024;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Random rnd = new Random();
    private final List<Glass> glasses = new ArrayList<>();

    private FrameLayout root, dockPanel, menu;
    private ImageView wallFront, wallBack, blurView;
    private View dim;
    private FocusScroll scroll;
    private LinearLayout content, dockRow, themeButton, inputsButton, controlButton, pill;
    private GridLayout grid;
    private TextView notice;
    private String currentCaption = "";
    private Walls walls;
    private Sounds sounds;
    private NowPlaying nowPlaying;
    private LinearLayout nowChip;
    private ImageView nowArt;
    private TextView nowTitle;
    private long keyAt;
    private long resumedAt;
    private boolean resumed;
    private Runnable pendingInput;
    private long pendingAt;
    private Tile moving;
    private int moveLimit;
    private boolean reordering;
    private ObjectAnimator scrollAnim, zoom;
    private int scrollTarget;
    private Bitmap glassBlur;
    private View menuReturnFocus;
    private Runnable menuBack;
    private Glass menuGlass;
    private FrameLayout shelfInfo;
    private LinearLayout shelfText, shelfDots, shelfButtons, btnPlay;
    private ImageView shelfThumb;
    private Bitmap blurSrc, blurBg, wallRaw, wallShelfSrc, wallShelf;
    private TextView shelfApp, shelfTitle, shelfDetail, btnPlayLabel;
    private FrameLayout shelfTrack;
    private View shelfFill;
    private Bitmap currentWall;
    private Map<String, List<WatchNext.Item>> shelf = new HashMap<>();
    // Visuels déjà chargés, du plus ancien au plus récent consulté ; limité en octets (un visuel 1080p fait ~8 Mo).
    private final LinkedHashMap<String, Bitmap> artCache = new LinkedHashMap<>(8, 0.75f, true);
    private final Set<String> failedArt = new HashSet<>();
    private Bitmap shownBackdrop;
    private String shelfPkg;
    private int shelfIndex, shelfToken;
    private boolean shelfActive, shelfFocused;
    private Set<String> visiblePkgs = new HashSet<>();
    private Map<String, AppEntry> appsByPkg = new HashMap<>();
    private final ExecutorService img = Executors.newFixedThreadPool(2);

    private int screenW, screenH, tileW, tileH;
    private String gridSig = "", dockSig = "";
    private boolean fullscreen, swallowUp, zoomHeld, shelfPending;

    private final Runnable rotate = () -> walls.next(this::showWall);
    private final Runnable idle = () -> {
        if (!fullscreen && menu == null && moving == null && hasWindowFocus()) enterFullscreen();
    };
    private final Runnable shelfAdvance = () -> {
        List<WatchNext.Item> items = shelfPkg == null ? null : shelf.get(shelfPkg);
        if (shelfActive && !shelfFocused && items != null && items.size() > 1) {
            shelfIndex = (shelfIndex + 1) % items.size();
            showShelfItem();
        }
    };
    // « À suivre » change le fond en fondu plein écran : seulement quand le focus s'arrête sur une tuile du dock.
    private final Runnable shelfFocus = () -> {
        shelfPending = false;
        View f = getCurrentFocus();
        if (f instanceof Tile && ((Tile) f).inDock) showShelfFor(((Tile) f).app.pkg);
    };
    private final Runnable repairKeys = () -> KeyService.repair(this);
    // Le zoom lent du fond redessine tout l'écran à chaque image : en pause pendant la navigation.
    private final Runnable resumeZoom = () -> {
        zoomHeld = false;
        if (zoom != null) zoom.resume();
    };
    private final Runnable hideNotice =() -> notice.animate().alpha(0f).translationY(-dp(8)).setDuration(300).start();

    private final BroadcastReceiver packages = new PackageReceiver(this);

    static final class PackageReceiver extends BroadcastReceiver {
        private final HomeActivity home;

        PackageReceiver(HomeActivity home) {
            this.home = home;
        }

        @Override
        public void onReceive(Context c, Intent i) {
            home.gridSig = "";
            home.reloadApps();
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(size);
        screenW = size.x;
        screenH = size.y;
        tileW = dp(124);
        tileH = dp(74);
        walls = new Walls(this);
        sounds = new Sounds(this);
        nowPlaying = new NowPlaying(this, this::updateNow);
        setContentView(buildUi());

        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> onFocus(newFocus));

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        if (ACTION_INPUTS.equals(getIntent().getAction())) handler.post(this::inputsMenu);
    }

    private View buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.setClipChildren(false);

        wallBack = wallView();
        wallFront = wallView();
        root.addView(wallBack, match());
        root.addView(wallFront, match());

        blurView = new ImageView(this);
        blurView.setScaleType(ImageView.ScaleType.FIT_XY);
        blurView.setAlpha(0f);
        root.addView(blurView, match());

        dim = overlay();
        dim.setBackgroundColor(0xFF000000);
        dim.setAlpha(0f);
        root.addView(dim, match());

        scroll = new FocusScroll(this);
        scroll.setOnScroll(this::onScroll);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(0, 0, 0, dp(200));

        content.addView(new View(this), new LinearLayout.LayoutParams(1, screenH - dp(150)));

        dockPanel = new FrameLayout(this);
        dockPanel.setClipChildren(false);
        dockPanel.setClipToPadding(false);
        dockPanel.setPadding(dp(14), dp(14), dp(14), dp(14));
        dockPanel.setBackground(glass(dockPanel, dp(32), 0x40000000));
        dockPanel.setElevation(dp(16));
        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);
        dockPanel.addView(dockRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dockLp.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(dockPanel, dockLp);

        grid = new GridLayout(this);
        grid.setColumnCount(COLS);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridLp.topMargin = dp(26);
        gridLp.gravity = Gravity.START;
        gridLp.leftMargin = dp(40);
        content.addView(grid, gridLp);

        scroll.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, match());
        root.addView(buildShelfInfo(), match());

        pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setClipChildren(false);
        pill.setClipToPadding(false);
        pill.setPadding(dp(14), 0, dp(4), 0);
        pill.setBackground(glass(pill, dp(18), 0x66000000));
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextColor(0xFFFFFFFF);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        clock.setTypeface(Fonts.get(this, 600));
        clock.setIncludeFontPadding(false);
        // Lecture en cours : pochette et titre, à gauche de l'heure.
        nowChip = new LinearLayout(this);
        nowChip.setOrientation(LinearLayout.HORIZONTAL);
        nowChip.setGravity(Gravity.CENTER_VERTICAL);
        nowChip.setVisibility(View.GONE);
        nowArt = new ImageView(this);
        nowArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        nowArt.setOutlineProvider(new Tile.Rounded(dp(5)));
        nowArt.setClipToOutline(true);
        nowChip.addView(nowArt, new LinearLayout.LayoutParams(dp(22), dp(22)));
        nowTitle = text(13, 0xF2FFFFFF, 600);
        nowTitle.setShadowLayer(0, 0, 0, 0);
        nowTitle.setSingleLine(true);
        nowTitle.setEllipsize(TextUtils.TruncateAt.END);
        nowTitle.setMaxWidth(dp(220));
        LinearLayout.LayoutParams ntLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ntLp.leftMargin = dp(8);
        nowChip.addView(nowTitle, ntLp);
        LinearLayout.LayoutParams ncLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ncLp.rightMargin = dp(12);
        pill.addView(nowChip, ncLp);
        pill.addView(clock);

        inputsButton = pillButton(pill, R.drawable.ic_s_input, this::inputsMenu);
        inputsButton.setVisibility(View.GONE);
        controlButton = pillButton(pill, R.drawable.ic_toggles, this::openControl);
        themeButton = pillButton(pill, R.drawable.ic_gear, this::settingsMenu);

        FrameLayout.LayoutParams clockLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36), Gravity.TOP | Gravity.END);
        clockLp.setMargins(0, dp(26), dp(40), 0);
        root.addView(pill, clockLp);

        notice = text(14, 0xFFFFFFFF, 500);
        notice.setGravity(Gravity.CENTER);
        notice.setPadding(dp(22), 0, dp(22), 0);
        notice.setBackground(glass(notice, dp(20), 0x8C000000));
        notice.setAlpha(0f);
        FrameLayout.LayoutParams noticeLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        noticeLp.topMargin = dp(26);
        root.addView(notice, noticeLp);

        return root;
    }

    private LinearLayout pillButton(LinearLayout pill, int icon, Runnable onClick) {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        b.addView(iv, new LinearLayout.LayoutParams(dp(16), dp(16)));
        GradientDrawable solid = new GradientDrawable();
        solid.setColor(0xF7FFFFFF);
        solid.setShape(GradientDrawable.OVAL);
        b.setOnFocusChangeListener((v, focused) -> {
            b.setBackground(focused ? solid : null);
            iv.setImageTintList(ColorStateList.valueOf(focused ? 0xFF15171A : 0xFFFFFFFF));
            float sc = focused ? 1.18f : 1f;
            b.animate().scaleX(sc).scaleY(sc).translationZ(focused ? dp(10) : 0)
                    .setDuration(200).setInterpolator(new DecelerateInterpolator(1.6f)).start();
        });
        b.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(28), dp(28));
        lp.leftMargin = dp(8);
        pill.addView(b, lp);
        return b;
    }

    private Glass glass(View owner, float radius, int shade) {
        Glass g = new Glass(owner, radius, Math.max(1, dp(1)), shade);
        glasses.add(g);
        if (glassBlur != null) g.setBlur(glassBlur, screenW, screenH);
        return g;
    }

    private View overlay() {
        return new Overlay(this);
    }

    // Calque plein écran dont les enfants ne se chevauchent pas : sa transparence s'applique à chacun,
    // sans tampon hors écran de la taille de l'écran pendant les fondus.
    static final class Layer extends FrameLayout {
        Layer(Context ctx) {
            super(ctx);
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }
    }

    // Voile plein écran : sa transparence s'applique directement, sans tampon hors écran à chaque image.
    static final class Overlay extends View {
        Overlay(Context ctx) {
            super(ctx);
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }
    }

    private ImageView wallView() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return v;
    }

    private TextView text(int sp, int color, int weight) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Fonts.get(this, weight));
        t.setShadowLayer(6, 0, 1, 0xB3000000);
        t.setIncludeFontPadding(false);
        return t;
    }


    private void notice(String text) {
        handler.removeCallbacks(hideNotice);
        notice.setText(text);
        notice.setTranslationY(-dp(8));
        notice.animate().alpha(1f).translationY(0f).setDuration(300).start();
        handler.postDelayed(hideNotice, 3500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        resumedAt = SystemClock.uptimeMillis();
        // Entrée choisie juste avant le retour au premier plan ; une demande plus ancienne est abandonnée.
        if (pendingInput != null && SystemClock.uptimeMillis() - pendingAt < INPUT_SETTLE_MS) handler.postDelayed(pendingInput, INPUT_SETTLE_MS);
        else pendingInput = null;
        nowPlaying.start();
        handler.removeCallbacks(rotate);
        if (wallFront.getDrawable() == null) walls.next(this::showWall);
        else handler.postDelayed(rotate, walls.interval());
        walls.refreshIfNeeded(() -> walls.next(this::showWall));
        if (shelfFocused) exitShelf(true);
        reloadApps();
        resetIdle();
        KeyService.ensureEnabled(this);
        handler.removeCallbacks(repairKeys);
        handler.postDelayed(repairKeys, 4000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        if (pendingInput != null) handler.removeCallbacks(pendingInput);
        pendingInput = null;
        nowPlaying.stop();
        if (moving != null) endMove();
        handler.removeCallbacks(rotate);
        handler.removeCallbacks(idle);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        bg.shutdownNow();
        img.shutdownNow();
        sounds.release();
        walls.close();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (moving != null) endMove();
        if (menu != null) closeMenu();
        if (fullscreen) exitFullscreen();
        if (shelfFocused) exitShelf(false);
        focusDock();
        if (ACTION_INPUTS.equals(intent.getAction())) inputsMenu();
    }

    private void reloadApps() {
        bg.execute(() -> {
            List<AppEntry> list = Apps.load(this);
            Set<String> keys = new LinkedHashSet<>();
            for (AppEntry a : list) keys.add(a.pkg);
            // Dock personnalisé : uniquement les applis choisies (1 à 6). Sinon, les applis récentes.
            List<String> recent = new ArrayList<>();
            for (String pkg : Apps.pinned(this)) if (keys.contains(pkg) && recent.size() < DOCK_COUNT) recent.add(pkg);
            if (recent.isEmpty()) recent.addAll(Recents.top(this, keys, DOCK_COUNT));
            boolean hasInputs = !Inputs.load(this).isEmpty();
            handler.post(() -> {
                apply(list, recent);
                showInputsButton(hasInputs);
            });
        });
    }

    private void updateNow() {
        android.media.session.MediaController c = nowPlaying.current();
        if (c == null) {
            nowChip.setVisibility(View.GONE);
            return;
        }
        Bitmap art = NowPlaying.art(c);
        nowArt.setImageBitmap(art);
        nowArt.setVisibility(art != null ? View.VISIBLE : View.GONE);
        nowTitle.setText(NowPlaying.title(c));
        nowChip.setVisibility(View.VISIBLE);
    }

    private void showInputsButton(boolean show) {
        if (!show && inputsButton.hasFocus()) themeButton.requestFocus();
        inputsButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void apply(List<AppEntry> list, List<String> recent) {
        if (moving != null) return;
        Map<String, AppEntry> byPkg = new HashMap<>();
        Set<String> pkgs = new HashSet<>();
        for (AppEntry a : list) pkgs.add(a.pkg);
        visiblePkgs = pkgs;
        Set<String> inDock = new HashSet<>(recent);
        List<AppEntry> gridList = new ArrayList<>();
        StringBuilder g = new StringBuilder();
        for (AppEntry a : list) {
            byPkg.put(a.pkg, a);
            if (!inDock.contains(a.pkg)) gridList.add(a);
        }
        gridList = ordered(gridList, Apps.order(this));
        for (AppEntry a : gridList) g.append(a.pkg).append('|').append(a.label).append(',');
        String d = TextUtils.join(",", recent);
        View focus = getCurrentFocus();
        boolean focusInDock = !(focus instanceof Tile) || ((Tile) focus).inDock;

        if (!g.toString().equals(gridSig)) {
            gridSig = g.toString();
            grid.removeAllViews();
            for (int i = 0; i < gridList.size(); i++) {
                Tile t = tile(gridList.get(i), false);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.setMargins(0, 0, (i % COLS) < COLS - 1 ? dp(16) : 0, dp(14));
                grid.addView(t, lp);
            }
            dockSig = "";
        }
        if (!d.equals(dockSig)) {
            dockSig = d;
            dockRow.removeAllViews();
            for (String pkg : recent) {
                AppEntry a = byPkg.get(pkg);
                if (a == null) continue;
                Tile t = tile(a, true);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (dockRow.getChildCount() > 0) lp.leftMargin = dp(16);
                dockRow.addView(t, lp);
            }
            if (focusInDock && menu == null) focusDock();
        }
        if (getCurrentFocus() == null && menu == null) focusDock();
        appsByPkg = byPkg;
        loadShelf();
    }

    // Ordre personnalisé de la grille : les applis connues dans l'ordre choisi ; une appli inconnue
    // (nouvelle, ou sortie du dock) se glisse après sa voisine alphabétique, jamais après Réglages.
    private static List<AppEntry> ordered(List<AppEntry> alpha, List<String> order) {
        if (order.isEmpty()) return alpha;
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < order.size(); i++) rank.put(order.get(i), i);
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry a : alpha) if (rank.containsKey(a.pkg)) out.add(a);
        out.sort((a, b) -> Integer.compare(rank.get(a.pkg), rank.get(b.pkg)));
        for (int i = 0; i < alpha.size(); i++) {
            AppEntry a = alpha.get(i);
            if (rank.containsKey(a.pkg)) continue;
            int at = 0;
            for (int k = i - 1; k >= 0; k--) {
                int idx = out.indexOf(alpha.get(k));
                if (idx >= 0) {
                    at = idx + 1;
                    break;
                }
            }
            if (!Apps.SETTINGS_KEY.equals(a.pkg) && at > 0 && Apps.SETTINGS_KEY.equals(out.get(at - 1).pkg)) at--;
            out.add(at, a);
        }
        return out;
    }

    private FrameLayout.LayoutParams shelfInfoLp() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (screenW * 0.56f), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.START);
        lp.leftMargin = dp(49);
        return lp;
    }

    private int bandHeight() {
        return screenH - dp(170) - dp(BAND_TOP);
    }

    private View buildShelfInfo() {
        // Conteneur de l'aperçu : texte à gauche et, pour les petits visuels, vignette nette à droite.
        shelfInfo = new Layer(this);
        shelfInfo.setClipChildren(false);
        shelfInfo.setClipToPadding(false);
        shelfInfo.setAlpha(0f);
        shelfThumb = new ImageView(this);
        shelfThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        shelfThumb.setOutlineProvider(new Tile.Rounded(dp(14)));
        shelfThumb.setClipToOutline(true);
        shelfThumb.setElevation(dp(18));
        shelfThumb.setAlpha(0f);
        shelfApp = text(12, 0xCCFFFFFF, 700);
        shelfApp.setLetterSpacing(0.1f);
        // Même ligne que la pastille de l'heure (en haut à droite) : même hauteur, texte centré.
        shelfApp.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams appLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36), Gravity.TOP | Gravity.START);
        appLp.setMargins(dp(49), dp(26), 0, 0);
        shelfInfo.addView(shelfApp, appLp);
        // Bande entre le nom de l'appli et le dock : texte à gauche et vignette à droite, centrés en hauteur.
        FrameLayout band = new Layer(this);
        band.setClipChildren(false);
        band.setClipToPadding(false);
        FrameLayout.LayoutParams bandLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bandHeight(), Gravity.TOP);
        bandLp.topMargin = dp(BAND_TOP);
        shelfInfo.addView(band, bandLp);
        FrameLayout.LayoutParams thumbLp = new FrameLayout.LayoutParams(0, 0, Gravity.CENTER_VERTICAL | Gravity.END);
        thumbLp.rightMargin = dp(48);
        band.addView(shelfThumb, thumbLp);
        shelfText = new LinearLayout(this);
        shelfText.setOrientation(LinearLayout.VERTICAL);
        shelfText.setClipChildren(false);
        shelfText.setClipToPadding(false);
        band.addView(shelfText, shelfInfoLp());

        shelfTitle = text(38, 0xFFFFFFFF, 700);
        shelfTitle.setShadowLayer(14, 0, 2, 0x99000000);
        shelfTitle.setMaxLines(2);
        shelfTitle.setEllipsize(TextUtils.TruncateAt.END);
        shelfTitle.setLineSpacing(0, 0.95f);
        shelfTitle.setPadding(0, 0, 0, dp(8));
        shelfText.addView(shelfTitle);

        shelfDetail = text(15, 0xF2FFFFFF, 600);
        shelfText.addView(shelfDetail);

        shelfTrack = new FrameLayout(this);
        GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(0x59FFFFFF);
        tbg.setCornerRadius(dp(2));
        shelfTrack.setBackground(tbg);
        shelfFill = new View(this);
        GradientDrawable fbg = new GradientDrawable();
        fbg.setColor(0xFFFFFFFF);
        fbg.setCornerRadius(dp(2));
        shelfFill.setBackground(fbg);
        shelfTrack.addView(shelfFill, new FrameLayout.LayoutParams(0, dp(4)));
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(dp(200), dp(4));
        trLp.topMargin = dp(10);
        shelfText.addView(shelfTrack, trLp);

        shelfDots = new LinearLayout(this);
        shelfDots.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dotsLp.topMargin = dp(14);
        shelfText.addView(shelfDots, dotsLp);

        shelfButtons = new LinearLayout(this);
        shelfButtons.setOrientation(LinearLayout.HORIZONTAL);
        shelfButtons.setClipChildren(false);
        shelfButtons.setAlpha(0f);
        btnPlay = shelfButton(R.drawable.ic_play, "Reprendre");
        btnPlayLabel = (TextView) btnPlay.getChildAt(1);
        btnPlay.setOnClickListener(v -> {
            WatchNext.Item it = currentShelfItem();
            if (it != null) openWatch(it);
        });
        shelfButtons.addView(btnPlay, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sbLp.topMargin = dp(22);
        shelfText.addView(shelfButtons, sbLp);
        return shelfInfo;
    }

    private LinearLayout shelfButton(int icon, String label) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(20), 0, dp(22), 0);
        b.setFocusable(false);
        b.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(21));
        bg.setColor(0x40FFFFFF);
        b.setBackground(bg);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        b.addView(iv, new LinearLayout.LayoutParams(dp(15), dp(15)));
        TextView tv = text(15, 0xFFFFFFFF, 600);
        tv.setShadowLayer(0, 0, 0, 0);
        tv.setText(label);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(9);
        b.addView(tv, tlp);
        b.setOnFocusChangeListener((v, focused) -> {
            bg.setColor(focused ? 0xF7FFFFFF : 0x40FFFFFF);
            int c = focused ? 0xFF15171A : 0xFFFFFFFF;
            tv.setTextColor(c);
            iv.setImageTintList(ColorStateList.valueOf(c));
            float sc = focused ? 1.08f : 1f;
            b.animate().scaleX(sc).scaleY(sc).translationZ(focused ? dp(8) : 0).setDuration(180).start();
        });
        return b;
    }

    private WatchNext.Item currentShelfItem() {
        List<WatchNext.Item> items = shelfPkg == null ? null : shelf.get(shelfPkg);
        if (items == null || items.isEmpty()) return null;
        return items.get(shelfIndex % items.size());
    }

    private void loadShelf() {
        Set<String> allowed = visiblePkgs;
        bg.execute(() -> {
            List<WatchNext.Item> all = WatchNext.all(this, allowed);
            Map<String, List<WatchNext.Item>> map = WatchNext.grouped(all, 6);
            for (Map.Entry<String, List<WatchNext.Item>> e : WatchNext.grouped(WatchNext.previews(this, allowed), 6).entrySet()) {
                if (!map.containsKey(e.getKey())) map.put(e.getKey(), e.getValue());
            }
            handler.post(() -> {
                shelf = map;
                View f = getCurrentFocus();
                if (f instanceof Tile && ((Tile) f).inDock) {
                    String pkg = ((Tile) f).app.pkg;
                    shelfPkg = null;
                    showShelfFor(pkg);
                } else if (shelfActive && !shelf.containsKey(shelfPkg)) {
                    leaveShelf();
                }
            });
        });
    }

    private void showShelfFor(String pkg) {
        List<WatchNext.Item> items = shelf.get(pkg);
        if (items == null || items.isEmpty()) {
            leaveShelf();
            return;
        }
        if (shelfActive && pkg.equals(shelfPkg)) return;
        shelfPkg = pkg;
        shelfIndex = 0;
        shelfActive = true;
        showShelfItem();
    }

    private void leaveShelf() {
        handler.removeCallbacks(shelfAdvance);
        if (!shelfActive) return;
        shelfActive = false;
        shelfPkg = null;
        shelfToken++;
        shelfInfo.animate().alpha(0f).setDuration(300).withEndAction(() -> shelfThumb.setAlpha(0f)).start();
        if (currentWall != null) displayBackdrop(currentWall);
    }

    private void showShelfItem() {
        WatchNext.Item it = currentShelfItem();
        if (it == null) {
            leaveShelf();
            return;
        }
        List<WatchNext.Item> items = shelf.get(shelfPkg);
        AppEntry app = appsByPkg.get(it.pkg);
        shelfApp.setText(app != null ? app.label.toUpperCase() : "");
        shelfTitle.setText(it.title);
        String verb = WatchNext.verb(it);
        shelfDetail.setText(it.detail != null ? verb + "  ·  " + it.detail : verb);
        boolean progress = it.duration > 0 && it.position > 0;
        shelfTrack.setVisibility(progress ? View.VISIBLE : View.GONE);
        if (progress) {
            float p = Math.max(0.03f, Math.min(1f, it.position / (float) it.duration));
            shelfFill.getLayoutParams().width = Math.round(dp(200) * p);
            shelfFill.requestLayout();
        }
        btnPlayLabel.setText(it.position > 0 ? "Reprendre" : "Regarder");
        shelfDots.removeAllViews();
        if (items.size() > 1) {
            for (int i = 0; i < items.size(); i++) {
                View dot = new View(this);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(i == shelfIndex ? 0xFFFFFFFF : 0x66FFFFFF);
                dot.setBackground(g);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(7), dp(7));
                if (i > 0) lp.leftMargin = dp(7);
                shelfDots.addView(dot, lp);
            }
        }
        float visible = fullscreen ? 0f : Math.max(0f, 1f - scroll.getScrollY() / (float) dp(120));
        shelfInfo.setTranslationX(-dp(12));
        shelfInfo.setAlpha(Math.min(shelfInfo.getAlpha(), 0.4f) * visible);
        shelfInfo.animate().alpha(visible).translationX(0f).setDuration(450).setInterpolator(new DecelerateInterpolator(1.6f)).start();

        int token = ++shelfToken;
        String key = it.art;
        Bitmap cached = key == null ? null : artCache.get(key);
        if (cached != null) {
            showArt(cached);
        } else if (key == null || failedArt.contains(key)) {
            showArt(null);
        } else {
            shelfThumb.animate().alpha(0f).setDuration(150).start();
            img.execute(() -> {
                Bitmap raw = loadArt(key, 1920);
                Bitmap bmp = raw != null && isFull(raw) ? Shade.of(raw, ratio(), true) : raw;
                handler.post(() -> {
                    if (bmp != null) cacheArt(key, bmp);
                    else failedArt.add(key);
                    if (token != shelfToken || !shelfActive) return;
                    showArt(bmp);
                });
            });
        }
        handler.removeCallbacks(shelfAdvance);
        if (!shelfFocused) handler.postDelayed(shelfAdvance, 9000);
    }

    // Visuel d'un programme : plein écran s'il est à la résolution de l'écran ; plus petit, vignette nette à droite
    // (jamais agrandie) sur un fond flouté du même visuel. Sans visuel : le fond d'écran.
    private boolean isFull(Bitmap bmp) {
        return bmp.getWidth() >= screenW * 0.9f && bmp.getHeight() >= screenH * 0.9f;
    }

    private void showArt(Bitmap bmp) {
        if (bmp == null || isFull(bmp)) {
            shelfThumb.animate().alpha(0f).setDuration(250).start();
            textWidth((int) (screenW * 0.56f));
            if (bmp != null) displayBackdrop(bmp, true);
            else showWallBehindText();
            return;
        }
        float sc = Math.min(1f, Math.min(screenW * 0.42f / bmp.getWidth(), bandHeight() / (float) bmp.getHeight()));
        int w = Math.round(bmp.getWidth() * sc), h = Math.round(bmp.getHeight() * sc);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) shelfThumb.getLayoutParams();
        lp.width = w;
        lp.height = h;
        shelfThumb.setLayoutParams(lp);
        shelfThumb.setImageBitmap(bmp);
        shelfThumb.setAlpha(0f);
        shelfThumb.animate().alpha(1f).setDuration(450).start();
        textWidth(screenW - dp(48) - w - dp(40) - dp(49));
        if (bmp != blurSrc) {
            blurSrc = bmp;
            Bitmap b = Blur.of(bmp, 192, 108, 6, 3);
            new Canvas(b).drawColor(0x26000000);
            blurBg = Shade.of(b, ratio(), true);
        }
        displayBackdrop(blurBg, true);
    }

    // Aperçu sans visuel : le fond d'écran, assombri à gauche pour la lisibilité du texte.
    private void showWallBehindText() {
        Bitmap raw = wallRaw;
        if (raw == null) {
            if (currentWall != null) displayBackdrop(currentWall);
            return;
        }
        if (raw == wallShelfSrc) {
            displayBackdrop(wallShelf);
            return;
        }
        int token = shelfToken;
        img.execute(() -> {
            Bitmap shaded = Shade.of(raw, ratio(), true);
            handler.post(() -> {
                wallShelfSrc = raw;
                wallShelf = shaded;
                if (token == shelfToken && shelfActive) displayBackdrop(shaded);
            });
        });
    }

    private void textWidth(int w) {
        // Colonne plus étroite à côté d'une vignette : une ligne de titre de plus.
        shelfTitle.setMaxLines(w < screenW * 0.5f ? 3 : 2);
        ViewGroup.LayoutParams lp = shelfText.getLayoutParams();
        if (lp.width == w) return;
        lp.width = w;
        shelfText.setLayoutParams(lp);
    }

    private void stepShelf(int delta) {
        List<WatchNext.Item> items = shelfPkg == null ? null : shelf.get(shelfPkg);
        if (items == null || items.size() < 2) return;
        shelfIndex = (shelfIndex + delta + items.size()) % items.size();
        showShelfItem();
    }

    private void enterShelf() {
        shelfFocused = true;
        handler.removeCallbacks(shelfAdvance);
        DecelerateInterpolator ease = new DecelerateInterpolator(1.8f);
        dockPanel.animate().translationY(dp(70)).alpha(0f).setDuration(320).setInterpolator(ease).start();
        grid.animate().translationY(dp(70)).alpha(0f).setDuration(320).setInterpolator(ease).start();
        btnPlay.setFocusable(true);
        shelfButtons.animate().alpha(1f).setDuration(300).start();
        btnPlay.requestFocus();
    }

    private void exitShelf(boolean focusTile) {
        shelfFocused = false;
        btnPlay.setFocusable(false);
        shelfButtons.animate().alpha(0f).setDuration(200).start();
        DecelerateInterpolator ease = new DecelerateInterpolator(1.8f);
        dockPanel.animate().translationY(0f).alpha(1f).setDuration(320).setInterpolator(ease).start();
        grid.animate().translationY(0f).alpha(1f).setDuration(320).setInterpolator(ease).start();
        if (focusTile) {
            View target = null;
            for (int i = 0; i < dockRow.getChildCount(); i++) {
                View c = dockRow.getChildAt(i);
                if (c instanceof Tile && ((Tile) c).app.pkg.equals(shelfPkg)) target = c;
            }
            if (target != null) target.requestFocus();
            else focusDock();
        }
        handler.removeCallbacks(shelfAdvance);
        handler.postDelayed(shelfAdvance, 9000);
    }

    private Bitmap loadArt(String uri, int maxW) {
        for (String url : hiRes(uri)) {
            Bitmap bmp = loadArtFrom(url, maxW);
            if (bmp != null) return bmp;
        }
        return null;
    }

    // Les applis publient souvent de petites vignettes : on demande d'abord la grande version au même serveur,
    // puis l'image d'origine si elle n'existe pas.
    static List<String> hiRes(String uri) {
        List<String> out = new ArrayList<>();
        String hi = uri;
        if (uri.contains("crunchyroll.com/imgsrv/display/thumbnail/")) {
            hi = uri.replaceFirst("/thumbnail/\\d+x\\d+/", "/thumbnail/1920x1080/");
        } else if (uri.contains("ytimg.com/vi")) {
            hi = uri.replaceFirst("/(default|mqdefault|hqdefault|sddefault)\\.(jpg|webp)", "/maxresdefault.$2");
        } else if (uri.contains("images-amazon.com/images/") || uri.contains("media-amazon.com/images/")) {
            hi = uri.replaceFirst("\\._[^/]*_\\.(jpg|png)$", "._UX1920_.$1");
        }
        if (!hi.equals(uri)) out.add(hi);
        out.add(uri);
        return out;
    }

    private Bitmap loadArtFrom(String uri, int maxW) {
        try {
            byte[] data;
            if (uri.startsWith("http")) {
                // Certaines applis (Prime Video…) donnent des visuels en http : chargés en https, le non-chiffré étant bloqué.
                String url = uri.startsWith("http://") ? "https://" + uri.substring(7) : uri;
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(15000);
                try (InputStream in = con.getInputStream()) {
                    data = readAll(in);
                } finally {
                    con.disconnect();
                }
            } else {
                try (InputStream in = getContentResolver().openInputStream(Uri.parse(uri))) {
                    data = readAll(in);
                }
            }
            BitmapFactory.Options b = new BitmapFactory.Options();
            b.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, b);
            BitmapFactory.Options o = new BitmapFactory.Options();
            int sample = 1;
            while (b.outWidth / (sample * 2) >= maxW) sample *= 2;
            o.inSampleSize = sample;
            // Couleurs 32 bits : en 16 bits, les dégradés des visuels plein écran font des aplats.
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Exception | OutOfMemoryError e) {
            android.util.Log.w("TVHome", "art " + uri + ": " + e);
            return null;
        }
    }

    private void cacheArt(String key, Bitmap bmp) {
        artCache.put(key, bmp);
        long total = 0;
        for (Bitmap b : artCache.values()) total += b.getByteCount();
        Iterator<Map.Entry<String, Bitmap>> it = artCache.entrySet().iterator();
        while (total > ART_CACHE_BYTES && artCache.size() > 1 && it.hasNext()) {
            total -= it.next().getValue().getByteCount();
            it.remove();
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private void openWatch(WatchNext.Item it) {
        try {
            if (it.intentUri == null) throw new IllegalStateException();
            Intent i = Intent.parseUri(it.intentUri, Intent.URI_INTENT_SCHEME);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            Recents.record(this, it.pkg);
        } catch (Exception e) {
            AppEntry app = appsByPkg.get(it.pkg);
            if (app != null) launch(app);
            else notice("Impossible d'ouvrir " + it.title);
        }
    }


    private void focusTop() {
        if (shelfFocused) btnPlay.requestFocus();
        else focusDock();
    }

    private Tile tile(AppEntry app, boolean inDock) {
        Tile t = new Tile(this, app, inDock, tileW, tileH, dp(14));
        t.setOnClickListener(v -> launch(app));
        t.setOnLongClickListener(v -> {
            appMenu(t);
            return true;
        });
        return t;
    }

    private void launch(AppEntry app) {
        try {
            startActivity(app.launchIntent());
            Recents.record(this, app.pkg);
        } catch (Exception e) {
            notice("Impossible d'ouvrir " + app.label);
        }
    }

    private void appMenu(Tile t) {
        AppEntry app = t.app;
        if (Apps.SETTINGS_KEY.equals(app.pkg)) {
            int n = Apps.hidden(this).size();
            openMenu(app.label, new String[]{"Déplacer", "Réafficher les applis masquées (" + n + ")"}, -1, w -> {
                if (w == 0) {
                    startMove(t);
                    return;
                }
                Apps.showAll(this);
                gridSig = "";
                reloadApps();
                notice("Applis réaffichées");
            });
            return;
        }
        // Le dock tel qu'il est affiché : retirer ou ajouter une appli le rend personnalisé.
        List<String> dock = new ArrayList<>();
        for (int i = 0; i < dockRow.getChildCount(); i++) dock.add(((Tile) dockRow.getChildAt(i)).app.pkg);
        List<String> items = new ArrayList<>();
        items.add("Déplacer");
        if (!t.inDock) items.add("Ajouter au dock");
        else if (dock.size() > 1) items.add("Retirer du dock");
        items.add("Masquer de l'accueil");
        items.add("Infos sur l'appli");
        String[] labels = items.toArray(new String[0]);
        openMenu(app.label, labels, -1, w -> {
            String it = labels[w];
            if (it.equals("Déplacer")) {
                startMove(t);
            } else if (it.equals("Retirer du dock")) {
                dock.remove(app.pkg);
                Apps.setPinned(this, dock);
                reloadApps();
                notice(app.label + " est retirée du dock");
            } else if (it.equals("Ajouter au dock")) {
                if (dock.size() >= DOCK_COUNT) {
                    notice("Le dock est plein : retire d'abord une appli");
                    return;
                }
                dock.add(app.pkg);
                Apps.setPinned(this, dock);
                reloadApps();
                notice(app.label + " est ajoutée au dock");
            } else if (it.equals("Masquer de l'accueil")) {
                Apps.setHidden(this, app.pkg, true);
                gridSig = "";
                reloadApps();
                notice(app.label + " est masquée");
            } else {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {
                }
            }
        });
    }

    // Mode « Déplacer » : flèches pour changer de place, OK ou Retour pour terminer.
    private void startMove(Tile t) {
        if (!t.isAttachedToWindow()) return;
        int limit;
        if (t.inDock) {
            // Le dock affiché est épinglé tel quel : la place choisie ne bougera plus avec les applis récentes.
            List<String> dock = new ArrayList<>();
            for (int i = 0; i < dockRow.getChildCount(); i++) dock.add(((Tile) dockRow.getChildAt(i)).app.pkg);
            Apps.setPinned(this, dock);
            limit = dock.size();
        } else {
            limit = grid.getChildCount();
        }
        moving = t;
        moveLimit = limit;
        t.requestFocus();
        t.wiggle(true);
        notice("Flèches pour déplacer, OK pour terminer");
    }

    private void moveBy(int dx, int dy) {
        if (moving.inDock && dy != 0) return;
        ViewGroup parent = (ViewGroup) moving.getParent();
        int i = parent.indexOfChild(moving);
        int j = i + dx + (moving.inDock ? 0 : dy * COLS);
        if (j < 0 || j >= moveLimit || j == i) return;
        reordering = true;
        parent.removeViewAt(i);
        parent.addView(moving, j);
        relayout(parent);
        moving.requestFocus();
        reordering = false;
        List<String> pkgs = new ArrayList<>();
        StringBuilder sig = new StringBuilder();
        for (int k = 0; k < parent.getChildCount(); k++) {
            AppEntry a = ((Tile) parent.getChildAt(k)).app;
            pkgs.add(a.pkg);
            sig.append(a.pkg).append('|').append(a.label).append(',');
        }
        if (moving.inDock) {
            Apps.setPinned(this, pkgs.subList(0, moveLimit));
            dockSig = TextUtils.join(",", pkgs);
        } else {
            Apps.setOrder(this, pkgs);
            gridSig = sig.toString();
        }
    }

    private void endMove() {
        Tile t = moving;
        moving = null;
        t.wiggle(false);
        handler.post(hideNotice);
        reloadApps();
    }

    private void relayout(ViewGroup parent) {
        for (int k = 0; k < parent.getChildCount(); k++) {
            View c = parent.getChildAt(k);
            if (parent == grid) {
                GridLayout.LayoutParams lp = (GridLayout.LayoutParams) c.getLayoutParams();
                lp.setMargins(0, 0, (k % COLS) < COLS - 1 ? dp(16) : 0, dp(14));
                c.setLayoutParams(lp);
            } else {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) c.getLayoutParams();
                lp.leftMargin = k > 0 ? dp(16) : 0;
                c.setLayoutParams(lp);
            }
        }
    }

    private void settingsMenu() {
        int excluded = WatchNext.excluded(this).size();
        int appsHidden = Apps.hidden(this).size();
        String[] items = {"Catégories", "Durée entre chaque image", "À suivre", "Applications sur l'accueil", "Sons de navigation", "Plus de réglages"};
        String[] details = {Walls.label(walls.theme()), Walls.intervalLabel(walls.interval()),
                excluded == 0 ? "Toutes les applis" : excluded + (excluded > 1 ? " masquées" : " masquée"),
                appsHidden == 0 ? "Toutes affichées" : appsHidden + (appsHidden > 1 ? " masquées" : " masquée"),
                sounds.enabled() ? "Activés" : "Désactivés", "Tous les paramètres"};
        openMenu("Réglages", currentCaption, items, details, -1, w -> {
            if (w == 0) chooseTheme();
            else if (w == 1) chooseInterval();
            else if (w == 2) chooseUpNext();
            else if (w == 3) chooseApps();
            else if (w == 4) chooseSounds();
            else openSystemSettings();
        }, null);
    }

    private void chooseSounds() {
        openMenu("Sons de navigation", new String[]{"Activés", "Désactivés"}, null, sounds.enabled() ? 0 : 1, w -> {
            sounds.setEnabled(w == 0);
            notice(w == 0 ? "Sons de navigation activés" : "Sons de navigation désactivés");
        }, this::settingsMenu);
    }

    private void openControl() {
        startActivity(new Intent(this, ControlActivity.class));
        overridePendingTransition(0, 0);
    }

    private void inputsMenu() {
        bg.execute(() -> {
            List<Inputs.Input> inputs = Inputs.load(this);
            handler.post(() -> {
                if (inputs.isEmpty()) {
                    showInputsButton(false);
                    notice("Aucune entrée disponible");
                    return;
                }
                String[] labels = new String[inputs.size()];
                boolean[] idle = new boolean[inputs.size()];
                for (int i = 0; i < labels.length; i++) {
                    labels[i] = inputs.get(i).label;
                    idle[i] = !inputs.get(i).active;
                }
                openMenu("Entrées", null, labels, null, -1, null, idle, w -> openInput(inputs.get(w)), null);
            });
        });
    }

    private void openInput(Inputs.Input in) {
        if (pendingInput != null) handler.removeCallbacks(pendingInput);
        pendingInput = () -> {
            pendingInput = null;
            launchInput(in);
        };
        pendingAt = SystemClock.uptimeMillis();
        // Pas encore revenu au premier plan : on attend onResume, qui relance l'ouverture.
        if (!resumed) return;
        handler.postDelayed(pendingInput, Math.max(0, resumedAt + INPUT_SETTLE_MS - SystemClock.uptimeMillis()));
    }

    private void launchInput(Inputs.Input in) {
        try {
            startActivity(Inputs.intent(in));
        } catch (Exception e) {
            try {
                startActivity(Inputs.tvApp());
            } catch (Exception e2) {
                notice("Impossible d'ouvrir " + in.label);
            }
        }
    }

    private void openSystemSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            notice("Impossible d'ouvrir les réglages");
        }
    }

    private void chooseApps() {
        bg.execute(() -> {
            List<Apps.Manageable> apps = Apps.manageable(this);
            handler.post(() -> {
                if (apps.isEmpty()) {
                    notice("Aucune application à gérer");
                    settingsMenu();
                    return;
                }
                String[] labels = new String[apps.size()];
                boolean[] states = new boolean[apps.size()];
                for (int i = 0; i < apps.size(); i++) {
                    labels[i] = apps.get(i).app.label;
                    states[i] = !apps.get(i).hidden;
                }
                openMenu("Applications sur l'accueil", null, labels, null, -1, states, i -> {
                    Apps.setHidden(this, apps.get(i).app.pkg, !states[i]);
                    gridSig = "";
                    dockSig = "";
                    reloadApps();
                }, this::settingsMenu);
            });
        });
    }

    private void chooseUpNext() {
        Set<String> allowed = visiblePkgs;
        bg.execute(() -> {
            List<String> pkgs = WatchNext.publishers(this, allowed);
            handler.post(() -> {
                if (pkgs.isEmpty()) {
                    notice("Aucune appli ne publie de programme pour l'instant");
                    settingsMenu();
                    return;
                }
                Set<String> excluded = WatchNext.excluded(this);
                String[] labels = new String[pkgs.size()];
                boolean[] states = new boolean[pkgs.size()];
                for (int i = 0; i < labels.length; i++) {
                    AppEntry a = appsByPkg.get(pkgs.get(i));
                    labels[i] = a != null ? a.label : pkgs.get(i);
                    states[i] = !excluded.contains(pkgs.get(i));
                }
                openMenu("À suivre", null, labels, null, -1, states, i -> {
                    WatchNext.setExcluded(this, pkgs.get(i), !states[i]);
                    loadShelf();
                }, this::settingsMenu);
            });
        });
    }

    private void chooseTheme() {
        String current = walls.theme();
        String[] labels = new String[Walls.THEMES.length];
        int checked = 0;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Walls.THEMES[i][1];
            if (Walls.THEMES[i][0].equals(current)) checked = i;
        }
        openMenu("Catégories", labels, null, checked, w -> {
            String key = Walls.THEMES[w][0];
            if (key.equals(current)) return;
            walls.setTheme(key);
            notice(walls.hasPhotos(key) ? Walls.THEMES[w][1] : "Téléchargement : " + Walls.THEMES[w][1] + "…");
            walls.next(this::showWall);
            walls.refreshIfNeeded(() -> walls.next(this::showWall));
        }, this::settingsMenu);
    }

    private void chooseInterval() {
        long current = walls.interval();
        int checked = -1;
        for (int i = 0; i < Walls.INTERVALS.length; i++) if (Walls.INTERVALS[i] == current) checked = i;
        openMenu("Durée entre chaque image", Walls.INTERVAL_LABELS, null, checked, w -> {
            long ms = Walls.INTERVALS[w];
            walls.setInterval(ms);
            handler.removeCallbacks(rotate);
            handler.postDelayed(rotate, ms);
            notice("Une nouvelle image toutes les " + Walls.INTERVAL_LABELS[w]);
        }, this::settingsMenu);
    }

    private void openMenu(String title, String[] items, int checked, OnPick onPick) {
        openMenu(title, null, items, null, checked, onPick, null);
    }

    private void openMenu(String title, String[] items, String[] details, int checked, OnPick onPick, Runnable onBack) {
        openMenu(title, null, items, details, checked, onPick, onBack);
    }

    private void openMenu(String title, String info, String[] items, String[] details, int checked, OnPick onPick, Runnable onBack) {
        openMenu(title, info, items, details, checked, null, onPick, onBack);
    }

    private void openMenu(String title, String info, String[] items, String[] details, int checked, boolean[] toggles, OnPick onPick, Runnable onBack) {
        openMenu(title, info, items, details, checked, toggles, null, onPick, onBack);
    }

    private void openMenu(String title, String info, String[] items, String[] details, int checked, boolean[] toggles, boolean[] dimmed, OnPick onPick, Runnable onBack) {
        if (menu != null) closeMenu();
        menuReturnFocus = getCurrentFocus();
        menuBack = onBack;
        menu = new FrameLayout(this);
        menu.setClickable(true);

        View shade = overlay();
        shade.setBackgroundColor(0x73000000);
        menu.addView(shade, match());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(2), dp(20), dp(2), dp(12));
        menuGlass = glass(card, dp(28), 0xA6101216);
        card.setBackground(menuGlass);
        TextView t = text(19, 0xFFFFFFFF, 600);
        t.setText(title);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(dp(26), 0, dp(26), dp(14));
        card.addView(t);
        if (info != null && !info.isEmpty()) card.addView(artwork(info));

        View first = null;
        ScrollView list = new ScrollView(this);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setClipToPadding(false);
        // Marge dans la liste (et non dans la carte) : la ligne agrandie au focus peut y déborder sans être rognée.
        list.setPadding(dp(8), dp(4), dp(8), dp(4));
        list.setClipChildren(false);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        rows.setClipToPadding(false);
        list.addView(rows);
        for (int i = 0; i < items.length; i++) {
            boolean on = toggles != null ? toggles[i] : i == checked;
            View row = menuRow(items[i], details == null ? null : details[i], on, dimmed != null && dimmed[i]);
            int index = i;
            row.setOnClickListener(v -> {
                if (toggles != null) {
                    toggles[index] = !toggles[index];
                    ((View) row.getTag()).setVisibility(toggles[index] ? View.VISIBLE : View.INVISIBLE);
                    onPick.pick(index);
                    return;
                }
                closeMenu();
                onPick.pick(index);
            });
            rows.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
            if (first == null || (toggles == null && i == checked)) first = row;
        }
        int maxList = screenH - dp(170);
        int listH = Math.min(maxList, items.length * dp(44) + dp(8));
        card.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, listH));
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(dp(420), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        menu.addView(card, cardLp);
        root.addView(menu, match());

        scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        themeButton.setFocusable(false);
        inputsButton.setFocusable(false);
        controlButton.setFocusable(false);
        btnPlay.setFocusable(false);
        blurView.animate().alpha(1f).setDuration(250).withEndAction(this::syncWall).start();
        shade.setAlpha(0f);
        shade.animate().alpha(1f).setDuration(250).start();
        card.setAlpha(0f);
        card.setScaleX(0.92f);
        card.setScaleY(0.92f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(0.8f)).start();
        if (first != null) first.requestFocus();
    }

    private View artwork(String info) {
        String[] parts = info.split("  ·  ");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(12), dp(18), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0x14FFFFFF);
        box.setBackground(bg);
        TextView kicker = text(11, 0x99FFFFFF, 600);
        kicker.setShadowLayer(0, 0, 0, 0);
        kicker.setText("IMAGE ACTUELLE");
        kicker.setLetterSpacing(0.08f);
        box.addView(kicker);
        TextView name = text(16, 0xFFFFFFFF, 600);
        name.setShadowLayer(0, 0, 0, 0);
        name.setText(parts[0]);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0, dp(6), 0, 0);
        box.addView(name);
        StringBuilder rest = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            if (rest.length() > 0) rest.append("  ·  ");
            rest.append(parts[i]);
        }
        TextView credit = text(13, 0xB3FFFFFF, 400);
        credit.setShadowLayer(0, 0, 0, 0);
        credit.setText(rest.length() > 0 ? rest.toString() : "Illustration intégrée");
        credit.setMaxLines(2);
        credit.setEllipsize(TextUtils.TruncateAt.END);
        credit.setPadding(0, dp(4), 0, 0);
        box.addView(credit);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(16), 0, dp(16), dp(10));
        box.setLayoutParams(lp);
        return box;
    }

    private View menuRow(String label, String detail, boolean checked, boolean dim) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(18), 0, dp(16), 0);
        TextView tv = text(16, 0xF2FFFFFF, 500);
        tv.setShadowLayer(0, 0, 0, 0);
        tv.setText(label);
        tv.setSingleLine(true);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = text(15, 0x99FFFFFF, 500);
        value.setShadowLayer(0, 0, 0, 0);
        value.setSingleLine(true);
        if (detail != null) {
            value.setText(detail);
            LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            vLp.rightMargin = dp(8);
            row.addView(value, vLp);
        }
        ImageView check = new ImageView(this);
        check.setImageResource(detail != null ? R.drawable.ic_chevron_right : R.drawable.ic_check);
        check.setAlpha(detail != null ? 0.6f : 1f);
        check.setVisibility(checked || detail != null ? View.VISIBLE : View.INVISIBLE);
        row.addView(check, new LinearLayout.LayoutParams(dp(detail != null ? 15 : 18), dp(detail != null ? 15 : 18)));
        row.setTag(check);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(0x00FFFFFF);
        row.setBackground(bg);
        float rest = dim ? 0.4f : 1f;
        row.setAlpha(rest);
        row.setOnFocusChangeListener((v, focused) -> {
            bg.setColor(focused ? 0xF7FFFFFF : 0x00FFFFFF);
            int c = focused ? 0xFF15171A : 0xF2FFFFFF;
            tv.setTextColor(c);
            value.setTextColor(focused ? 0x9915171A : 0x99FFFFFF);
            check.setImageTintList(ColorStateList.valueOf(c));
            float s = focused ? 1.03f : 1f;
            row.animate().scaleX(s).scaleY(s).alpha(focused ? 1f : rest).setDuration(140).start();
        });
        return row;
    }

    private void closeMenu() {
        if (menu == null) return;
        FrameLayout m = menu;
        menu = null;
        glasses.remove(menuGlass);
        menuBack = null;
        m.animate().alpha(0f).setDuration(200).withEndAction(() -> root.removeView(m)).start();
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        themeButton.setFocusable(true);
        inputsButton.setFocusable(true);
        controlButton.setFocusable(true);
        btnPlay.setFocusable(shelfFocused);
        onScroll(scroll.getScrollY());
        if (menuReturnFocus != null && menuReturnFocus.isAttachedToWindow()) menuReturnFocus.requestFocus();
        else focusDock();
        resetIdle();
    }

    private void focusDock() {
        if (dockRow.getChildCount() > 0) dockRow.getChildAt(0).requestFocus();
        smoothScroll(0);
    }

    private void onFocus(View v) {
        if (reordering) return;
        // Son seulement pour un déplacement fait à la télécommande, pas quand le code replace le focus.
        if (v != null && SystemClock.uptimeMillis() - keyAt < 250) sounds.move();
        if (v == themeButton || v == inputsButton || v == controlButton || v == btnPlay) {
            smoothScroll(0);
            return;
        }
        if (!(v instanceof Tile)) return;
        Tile t = (Tile) v;
        if (t.inDock) {
            smoothScroll(0);
            handler.removeCallbacks(shelfFocus);
            handler.postDelayed(shelfFocus, SHELF_DELAY_MS);
            shelfPending = true;
            return;
        }
        Rect r = new Rect();
        t.getDrawingRect(r);
        content.offsetDescendantRectToMyCoords(t, r);
        int dockH = dockPanel.getHeight() > 0 ? dockPanel.getHeight() : dp(102);
        int off = dp(28) + dockH + dp(18);
        // Dock épinglé en haut, puis on ne défile que si la tuile sortirait de l'écran.
        int first = grid.getTop() - off;
        int above = r.top - off;
        int below = r.bottom + dp(40) - screenH;
        int y = Math.max(first, Math.min(above, Math.max(below, scrollTarget)));
        smoothScroll(Math.max(0, Math.min(scroll.maxScroll(), y)));
    }

    private void smoothScroll(int y) {
        scrollTarget = y;
        if (scrollAnim != null) scrollAnim.cancel();
        if (scroll.getScrollY() == y) return;
        scrollAnim = ObjectAnimator.ofInt(scroll, "scrollY", y);
        scrollAnim.setDuration(420);
        scrollAnim.setInterpolator(new DecelerateInterpolator(2.2f));
        scrollAnim.start();
    }

    private void onScroll(int y) {
        float f = Math.min(1f, y / (float) dp(200));
        if (!fullscreen && menu == null) {
            blurView.setAlpha(f);
            dim.setAlpha(f * 0.3f);
            syncWall();
            float top = Math.max(0f, 1f - y / (float) dp(120));
            if (shelfActive) {
                shelfInfo.animate().cancel();
                shelfInfo.setAlpha(top);
            }
        }
        if (!shelfFocused) {
            int pin = Math.max(0, y - (screenH - dp(150) - dp(28)));
            dockPanel.setTranslationY(pin);
        }
        dockPanel.invalidate();
        pill.setAlpha(Math.max(0f, 1f - y / (float) dp(120)));
    }

    // Fond net masqué quand le flou le recouvre entièrement : son zoom lent ne coûte alors plus rien au GPU.
    private void syncWall() {
        int v = !fullscreen && blurView.getAlpha() >= 1f ? View.INVISIBLE : View.VISIBLE;
        if (wallFront.getVisibility() == v && wallBack.getVisibility() == v) return;
        wallFront.setVisibility(v);
        wallBack.setVisibility(v);
    }

    private void showWall(Bitmap bmp, String text) {
        currentCaption = text == null ? "" : text;
        handler.removeCallbacks(rotate);
        handler.postDelayed(rotate, walls.interval());
        img.execute(() -> {
            Bitmap shaded = Shade.of(bmp, ratio(), false);
            handler.post(() -> {
                currentWall = shaded;
                wallRaw = bmp;
                if (!shelfActive) displayBackdrop(shaded);
            });
        });
    }

    private float ratio() {
        return screenW / (float) screenH;
    }

    private void displayBackdrop(Bitmap bmp) {
        displayBackdrop(bmp, false);
    }

    // still : visuel de programme, fixe comme le « top shelf » de tvOS ; les fonds d'écran gardent leur zoom lent.
    private void displayBackdrop(Bitmap bmp, boolean still) {
        if (bmp == shownBackdrop) return;
        shownBackdrop = bmp;
        ImageView incoming = wallBack;
        ImageView outgoing = wallFront;
        root.removeView(incoming);
        root.addView(incoming, 1, match());
        incoming.animate().cancel();
        outgoing.animate().cancel();
        outgoing.setAlpha(1f);
        incoming.setImageBitmap(bmp);
        incoming.setAlpha(0f);
        incoming.setScaleX(1f);
        incoming.setScaleY(1f);
        incoming.setPivotX(screenW * (0.3f + rnd.nextFloat() * 0.4f));
        incoming.setPivotY(screenH * (0.3f + rnd.nextFloat() * 0.4f));
        incoming.animate().alpha(1f).setDuration(FADE_MS).setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (outgoing == wallBack) outgoing.setImageDrawable(null);
                }).start();
        ObjectAnimator previous = zoom;
        if (previous != null) handler.postDelayed(previous::cancel, FADE_MS);
        zoom = null;
        if (!still) {
            zoom = ObjectAnimator.ofPropertyValuesHolder(incoming,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f));
            zoom.setDuration(walls.interval() + FADE_MS * 2);
            zoom.setInterpolator(new LinearInterpolator());
            zoom.start();
            if (zoomHeld) zoom.pause();
        }
        wallFront = incoming;
        wallBack = outgoing;

        glassBlur = Blur.of(bmp, 96, 54, 3, 3);
        blurView.setImageBitmap(Blur.of(bmp, 160, 90, 4, 3));
        for (Glass g : glasses) g.setBlur(glassBlur, screenW, screenH);
        root.invalidate();
    }

    private void enterFullscreen() {
        fullscreen = true;
        syncWall();
        scroll.animate().alpha(0f).setDuration(500).start();
        blurView.animate().alpha(0f).setDuration(500).start();
        dim.animate().alpha(0f).setDuration(500).start();
        shelfInfo.animate().alpha(0f).setDuration(500).start();
    }

    private void exitFullscreen() {
        fullscreen = false;
        scroll.animate().alpha(1f).setDuration(350).start();
        float f = Math.min(1f, scroll.getScrollY() / (float) dp(200));
        blurView.animate().alpha(f).setDuration(350).withEndAction(this::syncWall).start();
        dim.animate().alpha(f * 0.3f).setDuration(350).start();
        if (shelfActive) {
            float top = Math.max(0f, 1f - scroll.getScrollY() / (float) dp(120));
            shelfInfo.animate().alpha(top).setDuration(350).start();
        }
        resetIdle();
    }

    private void resetIdle() {
        handler.removeCallbacks(idle);
        handler.postDelayed(idle, IDLE_MS);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (down) {
            resetIdle();
            Tile.steer(code);
            keyAt = SystemClock.uptimeMillis();
            if ((code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) && e.getRepeatCount() == 0 && !fullscreen
                    && getCurrentFocus() != null) sounds.select();
            zoomHeld = true;
            if (zoom != null) zoom.pause();
            handler.removeCallbacks(resumeZoom);
            handler.postDelayed(resumeZoom, ZOOM_PAUSE_MS);
        }
        if (menu != null) {
            if (code == KeyEvent.KEYCODE_BACK) {
                if (!down) {
                    Runnable back = menuBack;
                    closeMenu();
                    if (back != null) back.run();
                }
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
        if (moving != null) {
            if (down && code == KeyEvent.KEYCODE_DPAD_LEFT) moveBy(-1, 0);
            else if (down && code == KeyEvent.KEYCODE_DPAD_RIGHT) moveBy(1, 0);
            else if (down && code == KeyEvent.KEYCODE_DPAD_UP) moveBy(0, -1);
            else if (down && code == KeyEvent.KEYCODE_DPAD_DOWN) moveBy(0, 1);
            else if (!down && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_BACK)) endMove();
            return true;
        }
        if (fullscreen) {
            if (!down) {
                if (swallowUp) swallowUp = false;
                else exitFullscreen();
            }
            return true;
        }
        View f = getCurrentFocus();
        // OK maintenu : les télécommandes répètent la touche ; on ouvre le menu de l'appli une seule fois.
        if (down && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) && e.getRepeatCount() > 0 && f instanceof Tile) {
            if (e.getRepeatCount() == 1) {
                f.cancelLongPress();
                f.performLongClick();
            }
            return true;
        }
        boolean top = f == themeButton || f == inputsButton || f == controlButton;
        boolean btn = f == btnPlay;
        if (code == KeyEvent.KEYCODE_DPAD_UP && down) {
            if (f instanceof Tile && ((Tile) f).inDock) {
                if (shelfPending) {
                    handler.removeCallbacks(shelfFocus);
                    shelfFocus.run();
                }
                if (shelfActive) enterShelf();
                else themeButton.requestFocus();
                return true;
            }
            if (btn) {
                themeButton.requestFocus();
                return true;
            }
            if (top) {
                swallowUp = true;
                enterFullscreen();
                return true;
            }
        }
        if (btn && down) {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_BACK) {
                exitShelf(true);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                stepShelf(code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                return true;
            }
        }
        if (top && down && (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_BACK)) {
            focusTop();
            return true;
        }
        if (top && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            if (down) {
                // Boutons de la pastille de gauche à droite, en sautant ceux qui sont masqués.
                View[] pill = {inputsButton, controlButton, themeButton};
                int i = 0;
                while (pill[i] != f) i++;
                int dir = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                for (int j = i + dir; j >= 0 && j < pill.length; j += dir) {
                    if (pill[j].getVisibility() == View.VISIBLE) {
                        pill[j].requestFocus();
                        break;
                    }
                }
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK) {
            if (!down && scroll.getScrollY() > 0) focusDock();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
