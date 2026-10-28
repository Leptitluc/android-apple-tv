package com.lucb.tvhome;

import android.content.Context;
import android.graphics.Typeface;

import java.util.HashMap;
import java.util.Map;

final class Fonts {
    private static final Map<Integer, Typeface> CACHE = new HashMap<>();

    private Fonts() {}

    static synchronized Typeface get(Context ctx, int weight) {
        Typeface t = CACHE.get(weight);
        if (t != null) return t;
        try {
            t = Typeface.createFromAsset(ctx.getAssets(), "fonts/inter-" + weight + ".ttf");
        } catch (Exception e) {
            t = Typeface.create(weight >= 500 ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL);
        }
        CACHE.put(weight, t);
        return t;
    }
}
