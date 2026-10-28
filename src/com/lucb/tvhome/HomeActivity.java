package com.lucb.tvhome;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

    private static final int COLS = 6;
    private static final int DOCK_COUNT = 6;
    private static final long FADE_MS = 1_800;
    private static final long IDLE_MS = 90_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Random rnd = new Random();
    private final List<Glass> glasses = new ArrayList<>();

    private FrameLayout root, dockPanel, menu;
    private ImageView wallFront, wallBack, blurView;
    private View dim;
    private FocusScroll scroll;
    private LinearLayout content, dockRow, themeButton;
    private GridLayout grid;
    private TextView notice;
    private String currentCaption = "";
    private ImageView themeIcon;
    private Walls walls;
    private ObjectAnimator scrollAnim;
    private Bitmap glassBlur;
    private View menuReturnFocus;
    private Runnable menuBack;
    private Glass menuGlass;
    private View shelfScrim;
    private LinearLayout shelfInfo, shelfDots, shelfButtons, btnPlay, btnInfo;
    private TextView shelfApp, shelfTitle, shelfDetail, btnPlayLabel;
    private FrameLayout shelfTrack;
    private View shelfFill;
    private Bitmap currentWall;
    private Map<String, List<WatchNext.Item>> shelf = new HashMap<>();
    private final LruCache<String, Bitmap> artCache = new LruCache<>(6);
    private String shelfPkg;
    private int shelfIndex, shelfToken;
    private boolean shelfActive, shelfFocused;
    private Set<String> visiblePkgs = new HashSet<>();
    private Map<String, AppEntry> appsByPkg = new HashMap<>();
    private final ExecutorService img = Executors.newFixedThreadPool(2);

    private int screenW, screenH, tileW, tileH;
    private String gridSig = "", dockSig = "";
    private boolean fullscreen, swallowUp;

    private final Runnable rotate = () -> walls.next(this::showWall);
    private final Runnable idle = () -> {
        if (!fullscreen && menu == null && hasWindowFocus()) enterFullscreen();
    };
    private final Runnable shelfAdvance = () -> {
        List<WatchNext.Item> items = shelfPkg == null ? null : shelf.get(shelfPkg);
        if (shelfActive && !shelfFocused && items != null && items.size() > 1) {
            shelfIndex = (shelfIndex + 1) % items.size();
            showShelfItem();
        }
    };
    private final Runnable hideNotice = () -> notice.animate().alpha(0f).translationY(-dp(8)).setDuration(300).start();

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
        setContentView(buildUi());

        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> onFocus(newFocus));

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
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

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x4D000000, 0x00000000, 0x00000000, 0x1A000000, 0x8C000000}));
        root.addView(scrim, match());

        dim = new View(this);
        dim.setBackgroundColor(0xFF000000);
        dim.setAlpha(0f);
        root.addView(dim, match());

        shelfScrim = new View(this);
        shelfScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xB3000000, 0x73000000, 0x14000000, 0x00000000}));
        shelfScrim.setAlpha(0f);
        root.addView(shelfScrim, match());

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
        dockLp.gravity = Gravity.START;
        dockLp.leftMargin = dp(40) - dp(14);
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
        root.addView(buildShelfInfo(), shelfInfoLp());

        LinearLayout pill = new LinearLayout(this);
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
        pill.addView(clock);

        themeButton = new LinearLayout(this);
        themeButton.setGravity(Gravity.CENTER);
        themeButton.setFocusable(true);
        themeButton.setClickable(true);
        themeIcon = new ImageView(this);
        themeIcon.setImageResource(R.drawable.ic_toggles);
        themeButton.addView(themeIcon, new LinearLayout.LayoutParams(dp(16), dp(16)));
        GradientDrawable solid = new GradientDrawable();
        solid.setColor(0xF7FFFFFF);
        solid.setShape(GradientDrawable.OVAL);
        themeButton.setOnFocusChangeListener((v, focused) -> {
            themeButton.setBackground(focused ? solid : null);
            themeIcon.setImageTintList(ColorStateList.valueOf(focused ? 0xFF15171A : 0xFFFFFFFF));
            float sc = focused ? 1.18f : 1f;
            themeButton.animate().scaleX(sc).scaleY(sc).translationZ(focused ? dp(10) : 0)
                    .setDuration(200).setInterpolator(new DecelerateInterpolator(1.6f)).start();
        });
        themeButton.setOnClickListener(v -> settingsMenu());
        LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(dp(28), dp(28));
        tbLp.leftMargin = dp(8);
        pill.addView(themeButton, tbLp);

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

    private Glass glass(View owner, float radius, int shade) {
        Glass g = new Glass(owner, radius, Math.max(1, dp(1)), shade);
        glasses.add(g);
        if (glassBlur != null) g.setBlur(glassBlur, screenW, screenH);
        return g;
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
        handler.removeCallbacks(rotate);
        if (wallFront.getDrawable() == null) walls.next(this::showWall);
        else handler.postDelayed(rotate, walls.interval());
        walls.refreshIfNeeded(() -> walls.next(this::showWall));
        if (shelfFocused) exitShelf(true);
        reloadApps();
        resetIdle();
        KeyService.ensureEnabled(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(rotate);
        handler.removeCallbacks(idle);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        bg.shutdownNow();
        img.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (menu != null) closeMenu();
        if (fullscreen) exitFullscreen();
        if (shelfFocused) exitShelf(false);
        focusDock();
    }

    private void reloadApps() {
        bg.execute(() -> {
            List<AppEntry> list = Apps.load(this);
            Set<String> keys = new LinkedHashSet<>();
            for (AppEntry a : list) keys.add(a.pkg);
            List<String> recent = Recents.top(this, keys, DOCK_COUNT);
            handler.post(() -> apply(list, recent));
        });
    }

    private void apply(List<AppEntry> list, List<String> recent) {
        Map<String, AppEntry> byPkg = new HashMap<>();
        Set<String> pkgs = new HashSet<>();
        for (AppEntry a : list) pkgs.add(a.pkg);
        visiblePkgs = pkgs;
        Set<String> inDock = new HashSet<>(recent);
        List<AppEntry> gridList = new ArrayList<>();
        StringBuilder g = new StringBuilder();
        for (AppEntry a : list) {
            byPkg.put(a.pkg, a);
            if (inDock.contains(a.pkg)) continue;
            gridList.add(a);
            g.append(a.pkg).append('|').append(a.label).append(',');
        }
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

    private FrameLayout.LayoutParams shelfInfoLp() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (screenW * 0.56f), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        lp.setMargins(dp(49), dp(64), 0, 0);
        return lp;
    }

    private View buildShelfInfo() {
        shelfInfo = new LinearLayout(this);
        shelfInfo.setOrientation(LinearLayout.VERTICAL);
        shelfInfo.setClipChildren(false);
        shelfInfo.setClipToPadding(false);
        shelfInfo.setAlpha(0f);

        shelfApp = text(12, 0xCCFFFFFF, 700);
        shelfApp.setLetterSpacing(0.1f);
        shelfInfo.addView(shelfApp);

        shelfTitle = text(38, 0xFFFFFFFF, 700);
        shelfTitle.setShadowLayer(14, 0, 2, 0x99000000);
        shelfTitle.setMaxLines(2);
        shelfTitle.setEllipsize(TextUtils.TruncateAt.END);
        shelfTitle.setLineSpacing(0, 0.95f);
        shelfTitle.setPadding(0, dp(6), 0, dp(8));
        shelfInfo.addView(shelfTitle);

        shelfDetail = text(15, 0xF2FFFFFF, 600);
        shelfInfo.addView(shelfDetail);

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
        shelfInfo.addView(shelfTrack, trLp);

        shelfDots = new LinearLayout(this);
        shelfDots.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dotsLp.topMargin = dp(14);
        shelfInfo.addView(shelfDots, dotsLp);

        shelfButtons = new LinearLayout(this);
        shelfButtons.setOrientation(LinearLayout.HORIZONTAL);
        shelfButtons.setClipChildren(false);
        shelfButtons.setAlpha(0f);
        btnPlay = shelfButton(R.drawable.ic_play, "Reprendre");
        btnPlayLabel = (TextView) btnPlay.getChildAt(1);
        btnInfo = shelfButton(0, "Ouvrir l'appli");
        btnPlay.setOnClickListener(v -> {
            WatchNext.Item it = currentShelfItem();
            if (it != null) openWatch(it);
        });
        btnInfo.setOnClickListener(v -> {
            AppEntry app = shelfPkg == null ? null : appsByPkg.get(shelfPkg);
            if (app != null) launch(app);
        });
        shelfButtons.addView(btnPlay, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
        LinearLayout.LayoutParams biLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        biLp.leftMargin = dp(14);
        shelfButtons.addView(btnInfo, biLp);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sbLp.topMargin = dp(22);
        shelfInfo.addView(shelfButtons, sbLp);
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
        if (icon != 0) iv.setImageResource(icon);
        b.addView(iv, new LinearLayout.LayoutParams(icon != 0 ? dp(15) : 0, dp(15)));
        TextView tv = text(15, 0xFFFFFFFF, 600);
        tv.setShadowLayer(0, 0, 0, 0);
        tv.setText(label);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = icon != 0 ? dp(9) : 0;
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
            Map<String, List<WatchNext.Item>> map = WatchNext.grouped(this, allowed, 6);
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
        shelfInfo.animate().alpha(0f).setDuration(300).start();
        shelfScrim.animate().alpha(0f).setDuration(300).start();
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
        shelfScrim.animate().alpha(visible).setDuration(450).start();

        int token = ++shelfToken;
        String key = it.art;
        Bitmap cached = key == null ? null : artCache.get(key);
        if (cached != null) {
            displayBackdrop(cached);
        } else if (key != null) {
            img.execute(() -> {
                Bitmap bmp = loadArt(key, 1280);
                handler.post(() -> {
                    if (bmp != null) artCache.put(key, bmp);
                    if (token == shelfToken && shelfActive && bmp != null) displayBackdrop(bmp);
                });
            });
        }
        handler.removeCallbacks(shelfAdvance);
        if (!shelfFocused) handler.postDelayed(shelfAdvance, 9000);
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
        btnInfo.setFocusable(true);
        shelfButtons.animate().alpha(1f).setDuration(300).start();
        btnPlay.requestFocus();
    }

    private void exitShelf(boolean focusTile) {
        shelfFocused = false;
        btnPlay.setFocusable(false);
        btnInfo.setFocusable(false);
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
        try {
            byte[] data;
            if (uri.startsWith("http")) {
                HttpURLConnection con = (HttpURLConnection) new URL(uri).openConnection();
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
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Exception | OutOfMemoryError e) {
            android.util.Log.w("TVHome", "art " + uri + ": " + e);
            return null;
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
            appMenu(app);
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

    private void appMenu(AppEntry app) {
        if (Apps.SETTINGS_KEY.equals(app.pkg)) {
            int n = Apps.hidden(this).size();
            openMenu(app.label, new String[]{"Réafficher les applis masquées (" + n + ")"}, -1, w -> {
                Apps.showAll(this);
                gridSig = "";
                reloadApps();
                notice("Applis réaffichées");
            });
            return;
        }
        openMenu(app.label, new String[]{"Masquer de l'accueil", "Infos sur l'appli"}, -1, w -> {
            if (w == 0) {
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

    private void settingsMenu() {
        int excluded = WatchNext.excluded(this).size();
        int appsHidden = Apps.hidden(this).size();
        String[] items = {"Catégories", "Durée entre chaque image", "À suivre", "Applications sur l'accueil", "Plus de réglages"};
        String[] details = {Walls.label(walls.theme()), Walls.intervalLabel(walls.interval()),
                excluded == 0 ? "Toutes les applis" : excluded + (excluded > 1 ? " masquées" : " masquée"),
                appsHidden == 0 ? "Toutes affichées" : appsHidden + (appsHidden > 1 ? " masquées" : " masquée"),
                "Tous les paramètres"};
        openMenu("Réglages", currentCaption, items, details, -1, w -> {
            if (w == 0) chooseTheme();
            else if (w == 1) chooseInterval();
            else if (w == 2) chooseUpNext();
            else if (w == 3) chooseApps();
            else openSystemSettings();
        }, null);
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
        if (menu != null) closeMenu();
        menuReturnFocus = getCurrentFocus();
        menuBack = onBack;
        menu = new FrameLayout(this);
        menu.setClickable(true);

        View shade = new View(this);
        shade.setBackgroundColor(0x73000000);
        menu.addView(shade, match());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(20), dp(10), dp(12));
        menuGlass = glass(card, dp(28), 0xA6101216);
        card.setBackground(menuGlass);
        TextView t = text(19, 0xFFFFFFFF, 600);
        t.setText(title);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(dp(18), 0, dp(18), dp(14));
        card.addView(t);
        if (info != null && !info.isEmpty()) card.addView(artwork(info));

        View first = null;
        ScrollView list = new ScrollView(this);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setClipToPadding(false);
        list.setPadding(0, dp(4), 0, dp(4));
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        list.addView(rows);
        for (int i = 0; i < items.length; i++) {
            boolean on = toggles != null ? toggles[i] : i == checked;
            View row = menuRow(items[i], details == null ? null : details[i], on);
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
        btnPlay.setFocusable(false);
        btnInfo.setFocusable(false);
        blurView.animate().alpha(1f).setDuration(250).start();
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
        lp.setMargins(dp(8), 0, dp(8), dp(10));
        box.setLayoutParams(lp);
        return box;
    }

    private View menuRow(String label, String detail, boolean checked) {
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
        row.setOnFocusChangeListener((v, focused) -> {
            bg.setColor(focused ? 0xF7FFFFFF : 0x00FFFFFF);
            int c = focused ? 0xFF15171A : 0xF2FFFFFF;
            tv.setTextColor(c);
            value.setTextColor(focused ? 0x9915171A : 0x99FFFFFF);
            check.setImageTintList(ColorStateList.valueOf(c));
            float s = focused ? 1.03f : 1f;
            row.animate().scaleX(s).scaleY(s).setDuration(140).start();
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
        btnPlay.setFocusable(shelfFocused);
        btnInfo.setFocusable(shelfFocused);
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
        if (v == themeButton || v == btnPlay || v == btnInfo) {
            smoothScroll(0);
            return;
        }
        if (!(v instanceof Tile)) return;
        Tile t = (Tile) v;
        if (t.inDock) {
            smoothScroll(0);
            showShelfFor(t.app.pkg);
            return;
        }
        Rect r = new Rect();
        t.getDrawingRect(r);
        content.offsetDescendantRectToMyCoords(t, r);
        int dockH = dockPanel.getHeight() > 0 ? dockPanel.getHeight() : dp(102);
        int off = dp(28) + dockH + dp(18);
        smoothScroll(Math.max(0, Math.min(scroll.maxScroll(), r.top - off)));
    }

    private void smoothScroll(int y) {
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
            float top = Math.max(0f, 1f - y / (float) dp(120));
            if (shelfActive) {
                shelfInfo.animate().cancel();
                shelfInfo.setAlpha(top);
                shelfScrim.setAlpha(top);
            }
        }
        if (!shelfFocused) {
            int pin = Math.max(0, y - (screenH - dp(150) - dp(28)));
            dockPanel.setTranslationY(pin);
        }
        dockPanel.invalidate();
    }

    private void showWall(Bitmap bmp, String text) {
        currentWall = bmp;
        currentCaption = text == null ? "" : text;
        handler.removeCallbacks(rotate);
        handler.postDelayed(rotate, walls.interval());
        if (!shelfActive) displayBackdrop(bmp);
    }

    private void displayBackdrop(Bitmap bmp) {
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
        ObjectAnimator zoom = ObjectAnimator.ofPropertyValuesHolder(incoming,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f));
        zoom.setDuration(walls.interval() + FADE_MS * 2);
        zoom.setInterpolator(new LinearInterpolator());
        zoom.start();
        wallFront = incoming;
        wallBack = outgoing;

        glassBlur = Blur.of(bmp, 96, 54, 3, 3);
        blurView.setImageBitmap(Blur.of(bmp, 160, 90, 4, 3));
        for (Glass g : glasses) g.setBlur(glassBlur, screenW, screenH);
        root.invalidate();
    }

    private void enterFullscreen() {
        fullscreen = true;
        scroll.animate().alpha(0f).setDuration(500).start();
        blurView.animate().alpha(0f).setDuration(500).start();
        dim.animate().alpha(0f).setDuration(500).start();
        shelfInfo.animate().alpha(0f).setDuration(500).start();
        shelfScrim.animate().alpha(0f).setDuration(500).start();
    }

    private void exitFullscreen() {
        fullscreen = false;
        scroll.animate().alpha(1f).setDuration(350).start();
        float f = Math.min(1f, scroll.getScrollY() / (float) dp(200));
        blurView.animate().alpha(f).setDuration(350).start();
        dim.animate().alpha(f * 0.3f).setDuration(350).start();
        if (shelfActive) {
            float top = Math.max(0f, 1f - scroll.getScrollY() / (float) dp(120));
            shelfInfo.animate().alpha(top).setDuration(350).start();
            shelfScrim.animate().alpha(top).setDuration(350).start();
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
        if (down) resetIdle();
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
        if (fullscreen) {
            if (!down) {
                if (swallowUp) swallowUp = false;
                else exitFullscreen();
            }
            return true;
        }
        View f = getCurrentFocus();
        boolean top = f == themeButton;
        boolean btn = f == btnPlay || f == btnInfo;
        if (code == KeyEvent.KEYCODE_DPAD_UP && down) {
            if (f instanceof Tile && ((Tile) f).inDock) {
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
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && f == btnPlay) {
                stepShelf(-1);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT && f == btnInfo) {
                stepShelf(1);
                return true;
            }
        }
        if (top && down && (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_BACK)) {
            focusTop();
            return true;
        }
        if (top && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
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
