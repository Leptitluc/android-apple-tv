package com.lucb.tvhome;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;

final class AppEntry {
    final String pkg;
    final ComponentName component;
    final String label;
    final Drawable banner;
    final boolean leanback;
    final Intent override;

    AppEntry(String pkg, ComponentName component, String label, Drawable banner, boolean leanback, Intent override) {
        this.pkg = pkg;
        this.component = component;
        this.label = label;
        this.banner = banner;
        this.leanback = leanback;
        this.override = override;
    }

    Intent launchIntent() {
        if (override != null) {
            return new Intent(override).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }
}
