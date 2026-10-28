package com.lucb.tvhome;

import android.graphics.Bitmap;

final class Blur {
    private Blur() {}

    static Bitmap of(Bitmap src, int w, int h, int radius, int passes) {
        Bitmap mid = Bitmap.createScaledBitmap(src, w * 2, h * 2, true);
        Bitmap small = Bitmap.createScaledBitmap(mid, w, h, true);
        mid.recycle();
        Bitmap out = small.copy(Bitmap.Config.ARGB_8888, true);
        small.recycle();
        int[] px = new int[w * h];
        int[] tmp = new int[w * h];
        out.getPixels(px, 0, w, 0, 0, w, h);
        for (int p = 0; p < passes; p++) {
            box(px, tmp, w, h, radius, true);
            box(tmp, px, w, h, radius, false);
        }
        out.setPixels(px, 0, w, 0, 0, w, h);
        return out;
    }

    private static void box(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w;
        int len = horizontal ? w : h;
        int div = r * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int rs = 0, gs = 0, bs = 0;
            for (int i = -r; i <= r; i++) {
                int c = in[idx(line, Math.max(0, Math.min(len - 1, i)), w, horizontal)];
                rs += (c >> 16) & 0xFF;
                gs += (c >> 8) & 0xFF;
                bs += c & 0xFF;
            }
            for (int i = 0; i < len; i++) {
                out[idx(line, i, w, horizontal)] = 0xFF000000 | ((rs / div) << 16) | ((gs / div) << 8) | (bs / div);
                int add = in[idx(line, Math.min(len - 1, i + r + 1), w, horizontal)];
                int sub = in[idx(line, Math.max(0, i - r), w, horizontal)];
                rs += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
                gs += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
                bs += (add & 0xFF) - (sub & 0xFF);
            }
        }
    }

    private static int idx(int line, int i, int w, boolean horizontal) {
        return horizontal ? line * w + i : i * w + line;
    }
}
