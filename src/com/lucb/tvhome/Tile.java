package com.lucb.tvhome;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

final class Tile extends FrameLayout {
    private static final float FOCUS_SCALE = 1.1f;

    final AppEntry app;
    final boolean inDock;
    private final FrameLayout card;
    private final View ring;
    private final float lift;

    Tile(Context ctx, AppEntry app, boolean inDock, int w, int h, float radius) {
        super(ctx);
        this.app = app;
        this.inDock = inDock;
        float d = ctx.getResources().getDisplayMetrics().density;
        int pad = Math.round(6 * d);
        float sw = 3 * d;
        lift = 16 * d;
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
        addView(ring, new LayoutParams(w + pad * 2, h + pad * 2));

        card = new FrameLayout(ctx);
        card.setOutlineProvider(new Rounded(radius));
        card.setClipToOutline(true);
        card.setElevation(2 * d);
        card.setBackgroundColor(0xFF1C1F24);
        ImageView image = new ImageView(ctx);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setScaleX(1.04f);
        image.setScaleY(1.04f);
        Drawable.ConstantState cs = app.banner.getConstantState();
        image.setImageDrawable(cs != null ? cs.newDrawable(ctx.getResources()) : app.banner);
        card.addView(image, new LayoutParams(w, h));
        LayoutParams clp = new LayoutParams(w, h, Gravity.CENTER);
        addView(card, clp);
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
