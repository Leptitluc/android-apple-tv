package com.lucb.tvhome;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

final class Tile extends FrameLayout {
    private static final float FOCUS_SCALE = 1.1f;
    private static final float TILT = 0.05f;
    private static final long TILT_MS = 500;
    private static final long STEP_MS = 24;

    // Dernière flèche pressée : la tuile qui prend le focus s'incline dans ce sens.
    private static int dirX, dirY;
    private static long dirAt;

    final AppEntry app;
    final boolean inDock;
    private final FrameLayout card;
    private final Face face;
    private final View ring;
    private final ImageView image;
    private final float lift, radius, elev;
    private final int w;
    private final Runnable step = this::step;
    private final Matrix keystone = new Matrix(), band = new Matrix();
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] src = new float[8], dst = new float[8];
    private long start, dur;
    private ObjectAnimator wiggle;
    private int dx, dy;
    private float k, x = Float.NaN;

    Tile(Context ctx, AppEntry app, boolean inDock, int w, int h, float radius) {
        super(ctx);
        this.app = app;
        this.inDock = inDock;
        this.w = w;
        this.radius = radius;
        float d = ctx.getResources().getDisplayMetrics().density;
        int pad = Math.round(6 * d);
        float sw = 3 * d;
        lift = 16 * d;
        elev = 2 * d;
        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);

        ring = new View(ctx);
        GradientDrawable stroke = new GradientDrawable();
        stroke.setCornerRadius(radius + pad - sw / 2f);
        stroke.setStroke(Math.round(sw), 0xFFFFFFFF);
        stroke.setColor(0x00000000);
        ring.setBackground(stroke);
        ring.setAlpha(0f);
        // Inclinaison et reflet sont dessinés dans « face » (rognée à ses bornes) : seule la tuile est redessinée.
        face = new Face(this);
        addView(face, new LayoutParams(w + pad * 2, h + pad * 2));
        face.addView(ring, new LayoutParams(w + pad * 2, h + pad * 2));

        card = new FrameLayout(ctx);
        card.setOutlineProvider(new Rounded(radius));
        card.setClipToOutline(true);
        card.setElevation(elev);
        card.setBackgroundColor(0xFF1C1F24);
        image = new ImageView(ctx);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setScaleX(1.04f);
        image.setScaleY(1.04f);
        Drawable.ConstantState cs = app.banner.getConstantState();
        image.setImageDrawable(cs != null ? cs.newDrawable(ctx.getResources()) : app.banner);
        card.addView(image, new LayoutParams(w, h));
        sheen.setShader(new LinearGradient(0, 0, w / 2f, 0, new int[] {0x00FFFFFF, 0x4CFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        face.addView(card, new LayoutParams(w, h, Gravity.CENTER));
        setLayoutParams(new LayoutParams(w + pad * 2, h + pad * 2));

        setOnFocusChangeListener((v, focused) -> animateFocus(focused));
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (!isFocused()) return;
        float s = isPressed() ? 1.04f : FOCUS_SCALE;
        animate().scaleX(s).scaleY(s).setDuration(isPressed() ? 90 : 160).start();
    }

    private void animateFocus(boolean focused) {
        DecelerateInterpolator ease = new DecelerateInterpolator(1.8f);
        float s = focused ? FOCUS_SCALE : 1f;
        animate().scaleX(s).scaleY(s).translationZ(focused ? lift : 0f).setDuration(200).setInterpolator(ease).start();
        ring.animate().alpha(focused ? 1f : 0f).setDuration(160).start();
        removeCallbacks(step);
        k = 0f;
        x = Float.NaN;
        card.setElevation(elev);
        face.invalidate();
        if (focused) parallax();
    }

    // Mode « Déplacer » : la tuile tremble légèrement, comme sur tvOS.
    void wiggle(boolean on) {
        if (wiggle != null) {
            wiggle.cancel();
            wiggle = null;
            setRotation(0f);
        }
        if (!on) return;
        wiggle = ObjectAnimator.ofFloat(this, View.ROTATION, -1.5f, 1.5f);
        wiggle.setDuration(130);
        wiggle.setRepeatCount(ValueAnimator.INFINITE);
        wiggle.setRepeatMode(ValueAnimator.REVERSE);
        wiggle.start();
    }

    static void steer(int code) {
        dirX = code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 0;
        dirY = code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 0;
        dirAt = SystemClock.uptimeMillis();
    }

    // Inclinaison vers la flèche puis retour à plat, et un reflet qui glisse une fois.
    // Perspective dessinée dans la tuile : rotationX/Y ferait redessiner tout l'écran à chaque image.
    private void parallax() {
        float sc = Settings.Global.getFloat(getContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        if (sc <= 0f) return;
        boolean dir = SystemClock.uptimeMillis() - dirAt < 300;
        dx = dir ? dirX : 0;
        dy = dir ? dirY : 0;
        dur = (long) (TILT_MS * Math.max(1f, sc));
        start = SystemClock.uptimeMillis();
        // L'ombre recalculée en perspective à chaque image coûte cher : retirée le temps de l'inclinaison.
        if (dx != 0 || dy != 0) card.setElevation(0f);
        step();
    }

    // ~30 images/s : sur une puce faible, le GPU suit sans prendre de retard.
    private void step() {
        float t = Math.min(1f, (SystemClock.uptimeMillis() - start) / (float) dur);
        k = t < 0.2f ? (float) Math.sin(t / 0.2f * Math.PI / 2) : 1 - (t - 0.2f) / 0.8f;
        if (t >= 0.2f) k = k * k * (3 - 2 * k);
        float u = Math.min(1f, t / 0.85f);
        x = t < 1f ? w * (-0.75f + 2f * u * u * (3 - 2 * u)) : Float.NaN;
        face.invalidate();
        if (t < 1f) postOnAnimationDelayed(step, STEP_MS);
        else card.setElevation(elev);
    }

    // Trapèze : le bord côté flèche recule (rétrécit), le bord opposé garde sa taille, donc rien ne déborde de la tuile.
    private void tilt(float W, float H) {
        float e = TILT * k, c = TILT * 0.3f * k;
        for (int i = 0; i < 4; i++) {
            int u = i == 1 || i == 2 ? 1 : 0, v = i >= 2 ? 1 : 0;
            float px = u * W, py = v * H;
            src[i * 2] = px;
            src[i * 2 + 1] = py;
            if (dx != 0) {
                px += (u == 0 ? 1 : -1) * c * W / 2;
                if ((u == 1) == (dx > 0)) py += (v == 0 ? 1 : -1) * e * H;
            }
            if (dy != 0) {
                py += (v == 0 ? 1 : -1) * c * H / 2;
                if ((v == 1) == (dy > 0)) px += (u == 0 ? 1 : -1) * e * W;
            }
            dst[i * 2] = px;
            dst[i * 2 + 1] = py;
        }
        keystone.setPolyToPoly(src, 0, dst, 0, 4);
    }

    private void paint(Canvas canvas, Face f) {
        boolean tilted = k > 0f && (dx != 0 || dy != 0);
        if (!tilted && Float.isNaN(x)) {
            f.drawChildren(canvas);
            return;
        }
        int save = canvas.save();
        if (tilted) {
            tilt(f.getWidth(), f.getHeight());
            canvas.concat(keystone);
        }
        f.drawChildren(canvas);
        if (!Float.isNaN(x)) {
            band.setRotate(20f);
            band.postTranslate(card.getLeft() + x, 0);
            sheen.getShader().setLocalMatrix(band);
            canvas.drawRoundRect(card.getLeft(), card.getTop(), card.getRight(), card.getBottom(), radius, radius, sheen);
        }
        canvas.restoreToCount(save);
    }

    static final class Face extends FrameLayout {
        private final Tile tile;

        Face(Tile tile) {
            super(tile.getContext());
            this.tile = tile;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            tile.paint(canvas, this);
        }

        void drawChildren(Canvas canvas) {
            super.dispatchDraw(canvas);
        }
    }

    static final class Rounded extends ViewOutlineProvider {
        private final float radius;

        Rounded(float radius) {
            this.radius = radius;
        }

        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
        }
    }
}
