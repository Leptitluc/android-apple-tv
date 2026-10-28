package com.lucb.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

final class Glass extends Drawable {
    private final View owner;
    private final float radius;
    private final float strokeWidth;
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final int[] loc = new int[2];
    private Bitmap blur;
    private int screenW = 1, screenH = 1;
    Glass(View owner, float radius, float strokeWidth, int shadeColor) {
        this.owner = owner;
        this.radius = radius;
        this.strokeWidth = strokeWidth;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(strokeWidth);
        shade.setColor(shadeColor);
    }

    void setBlur(Bitmap blur, int screenW, int screenH) {
        this.blur = blur;
        this.screenW = screenW;
        this.screenH = screenH;
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        rect.set(bounds);
        float r = Math.min(radius, rect.height() / 2f);
        path.reset();
        path.addRoundRect(rect, r, r, Path.Direction.CW);
        fill.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0x24FFFFFF, 0x05FFFFFF, Shader.TileMode.CLAMP));
        stroke.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                new int[]{0x99FFFFFF, 0x1FFFFFFF, 0x14FFFFFF, 0x4DFFFFFF},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas canvas) {
        float r = Math.min(radius, rect.height() / 2f);
        canvas.save();
        canvas.clipPath(path);
        if (blur != null) {
            owner.getLocationInWindow(loc);
            matrix.setScale(screenW / (float) blur.getWidth(), screenH / (float) blur.getHeight());
            matrix.postTranslate(-loc[0], -loc[1]);
            canvas.drawBitmap(blur, matrix, bitmapPaint);
            canvas.drawRect(rect, shade);
        } else {
            canvas.drawColor(0x99202428);
        }
        canvas.drawRect(rect, fill);
        canvas.restore();
        float h = strokeWidth / 2f;
        canvas.drawRoundRect(rect.left + h, rect.top + h, rect.right - h, rect.bottom - h, r, r, stroke);
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
