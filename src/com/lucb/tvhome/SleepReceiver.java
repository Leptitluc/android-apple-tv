package com.lucb.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// Fin de la minuterie de veille du centre de contrôle.
public final class SleepReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent i) {
        ctx.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().remove("sleepAt").apply();
        KeyService.sleep(ctx);
    }
}
