package com.lucb.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;

// Dégradés sombres incrustés dans l'image de fond, recadrée au format de l'écran : un seul calque à afficher
// au lieu de voiles plein écran recalculés à chaque image (deux fois plus rapide sur une puce faible).
final class Shade {
    private Shade() {}

    // shelf : en plus, l'assombrissement à gauche derrière le texte de l'aperçu.
    static Bitmap of(Bitmap src, float ratio, boolean shelf) {
        int w = src.getWidth(), h = src.getHeight();
        int cw = w, ch = h;
        if (w / (float) h > ratio) cw = Math.round(h * ratio);
        else ch = Math.round(w / ratio);
        Bitmap out = Bitmap.createBitmap(cw, ch, src.getConfig() == Bitmap.Config.RGB_565 ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(src, new Rect((w - cw) / 2, (h - ch) / 2, (w + cw) / 2, (h + ch) / 2), new Rect(0, 0, cw, ch), p);
        Paint g = new Paint();
        g.setShader(new LinearGradient(0, 0, 0, ch, new int[]{0x4D000000, 0x00000000, 0x00000000, 0x1A000000, 0x8C000000}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, cw, ch, g);
        if (shelf) {
            g.setShader(new LinearGradient(0, 0, cw, 0, new int[]{0xB3000000, 0x73000000, 0x14000000, 0x00000000}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, cw, ch, g);
        }
        return out;
    }
}
