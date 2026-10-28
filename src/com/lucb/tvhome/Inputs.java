package com.lucb.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Inputs {
    static final class Input {
        final String id, label;
        final boolean passthrough, active;
        final int rank;

        Input(String id, String label, boolean passthrough, boolean active, int rank) {
            this.id = id;
            this.label = label;
            this.passthrough = passthrough;
            this.active = active;
            this.rank = rank;
        }
    }

    private Inputs() {}

    // Entrées physiques (HDMI, AV, péritel…) et tuner intégré, via l'API standard d'Android TV.
    // Les tuners des applis tierces (chaînes en streaming) sont ignorés.
    static List<Input> load(Context ctx) {
        List<Input> out = new ArrayList<>();
        try {
            TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return out;
            List<TvInputInfo> all = tim.getTvInputList();
            Map<String, String> devices = new HashMap<>();
            for (TvInputInfo i : all) {
                if (i.getParentId() == null || devices.containsKey(i.getParentId())) continue;
                String name = text(i.loadCustomLabel(ctx), i.loadLabel(ctx));
                if (name != null) devices.put(i.getParentId(), name);
            }
            PackageManager pm = ctx.getPackageManager();
            for (TvInputInfo i : all) {
                if (i.getParentId() != null || i.isHidden(ctx)) continue;
                if (!i.isPassthroughInput() && !isSystem(pm, i)) continue;
                String label = text(i.loadCustomLabel(ctx), i.loadLabel(ctx));
                if (label == null) label = typeLabel(i.getType());
                String device = devices.get(i.getId());
                if (device != null && !device.equals(label)) label += "  ·  " + device;
                // Actif = un appareil branché et allumé ; en veille ou débranché = inactif. Le tuner est toujours actif.
                boolean active = !i.isPassthroughInput() || tim.getInputState(i.getId()) == TvInputManager.INPUT_STATE_CONNECTED;
                out.add(new Input(i.getId(), label, i.isPassthroughInput(), active, rank(i.getType())));
            }
        } catch (Exception e) {
            Log.w("TVHome", "inputs: " + e);
        }
        out.sort((a, b) -> a.rank != b.rank ? Integer.compare(a.rank, b.rank) : a.label.compareToIgnoreCase(b.label));
        return numbered(out);
    }

    // « HDMI », « HDMI » -> « HDMI 1 », « HDMI 2 » quand la télé ne nomme pas ses entrées.
    private static List<Input> numbered(List<Input> list) {
        Map<String, Integer> count = new HashMap<>();
        for (Input in : list) count.put(in.label, count.containsKey(in.label) ? count.get(in.label) + 1 : 1);
        Map<String, Integer> seen = new HashMap<>();
        List<Input> out = new ArrayList<>();
        for (Input in : list) {
            if (count.get(in.label) < 2) {
                out.add(in);
                continue;
            }
            int n = seen.containsKey(in.label) ? seen.get(in.label) + 1 : 1;
            seen.put(in.label, n);
            out.add(new Input(in.id, in.label + " " + n, in.passthrough, in.active, in.rank));
        }
        return out;
    }

    static Intent intent(Input in) {
        return new Intent(Intent.ACTION_VIEW, in.passthrough
                ? TvContract.buildChannelUriForPassthroughInput(in.id)
                : TvContract.buildChannelsUriForInput(in.id))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    // Repli : l'appli TV du fabricant, si elle ne sait pas ouvrir une entrée précise.
    static Intent tvApp() {
        return new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private static boolean isSystem(PackageManager pm, TvInputInfo i) {
        try {
            String pkg = i.getServiceInfo().packageName;
            return (pm.getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String text(CharSequence custom, CharSequence label) {
        if (!TextUtils.isEmpty(custom) && !TextUtils.isEmpty(custom.toString().trim())) return custom.toString().trim();
        if (!TextUtils.isEmpty(label) && !TextUtils.isEmpty(label.toString().trim())) return label.toString().trim();
        return null;
    }

    private static int rank(int type) {
        if (type == TvInputInfo.TYPE_TUNER) return 0;
        if (type == TvInputInfo.TYPE_HDMI) return 1;
        return 2;
    }

    private static String typeLabel(int type) {
        switch (type) {
            case TvInputInfo.TYPE_TUNER:
                return "TV";
            case TvInputInfo.TYPE_HDMI:
                return "HDMI";
            case TvInputInfo.TYPE_COMPOSITE:
                return "AV";
            case TvInputInfo.TYPE_SVIDEO:
                return "S-Vidéo";
            case TvInputInfo.TYPE_SCART:
                return "Péritel";
            case TvInputInfo.TYPE_COMPONENT:
                return "Composante";
            case TvInputInfo.TYPE_VGA:
                return "VGA";
            case TvInputInfo.TYPE_DVI:
                return "DVI";
            case TvInputInfo.TYPE_DISPLAY_PORT:
                return "DisplayPort";
            default:
                return "Entrée";
        }
    }
}
