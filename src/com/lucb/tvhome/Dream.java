package com.lucb.tvhome;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;

// Économiseur d'écran : les fonds d'Accueil TV en fondu et zoom lent, l'heure, et le lieu au début de chaque image.
public final class Dream extends DreamService {
    private static final long FADE_MS = 2_000;
    private static final long CAPTION_MS = 6_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable next = this::next;
    private Walls walls;
    private FrameLayout root;
    private ImageView front, back;
    private TextView caption;
    private ObjectAnimator zoom;
    private boolean stopped;
    private final Runnable hideCaption = () -> caption.animate().alpha(0f).setDuration(800).start();

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        walls = new Walls(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        back = image();
        front = image();
        root.addView(back, match());
        root.addView(front, match());
        View shade = new HomeActivity.Overlay(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x59000000, 0x00000000, 0x00000000, 0x73000000}));
        root.addView(shade, match());

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        style(clock, 54, 700);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        cl.setMargins(0, dp(40), dp(56), 0);
        root.addView(clock, cl);

        caption = new TextView(this);
        style(caption, 22, 600);
        caption.setMaxLines(2);
        caption.setAlpha(0f);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams((int) (getResources().getDisplayMetrics().widthPixels * 0.6f),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        tl.setMargins(dp(56), 0, 0, dp(48));
        root.addView(caption, tl);
        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        stopped = false;
        next();
        walls.refreshIfNeeded(null);
    }

    @Override
    public void onDreamingStopped() {
        stopped = true;
        handler.removeCallbacksAndMessages(null);
        if (zoom != null) zoom.cancel();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        stopped = true;
        handler.removeCallbacksAndMessages(null);
        walls.close();
        super.onDetachedFromWindow();
    }

    private void next() {
        walls.next(this::show);
    }

    private void show(Bitmap bmp, String text) {
        if (stopped) return;
        ImageView incoming = back, outgoing = front;
        root.removeView(incoming);
        root.addView(incoming, 1, match());
        incoming.setImageBitmap(bmp);
        incoming.setAlpha(0f);
        incoming.setScaleX(1f);
        incoming.setScaleY(1f);
        incoming.animate().alpha(1f).setDuration(FADE_MS).setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> outgoing.setImageDrawable(null)).start();
        if (zoom != null) zoom.cancel();
        zoom = ObjectAnimator.ofPropertyValuesHolder(incoming,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f));
        zoom.setDuration(walls.interval() + FADE_MS * 2);
        zoom.setInterpolator(new LinearInterpolator());
        zoom.start();
        front = incoming;
        back = outgoing;

        // Le lieu seul, sans les crédits techniques.
        String place = text == null ? "" : text.split("  ·  ")[0].trim();
        handler.removeCallbacks(hideCaption);
        caption.setText(place);
        caption.setAlpha(0f);
        if (!place.isEmpty()) {
            caption.animate().alpha(1f).setStartDelay(FADE_MS / 2).setDuration(800).start();
            handler.postDelayed(hideCaption, CAPTION_MS + FADE_MS);
        }
        handler.removeCallbacks(next);
        handler.postDelayed(next, walls.interval());
    }

    private ImageView image() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return v;
    }

    private void style(TextView t, int sp, int weight) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(0xFFFFFFFF);
        t.setTypeface(Fonts.get(this, weight));
        t.setShadowLayer(12, 0, 2, 0x80000000);
        t.setIncludeFontPadding(false);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
