package com.eren.admin;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.json.JSONObject;

import java.util.ArrayList;

/** Picks dominant colours from an image/video frame and builds a matching dialog colour scheme. */
final class Pal {

    static String hex(int c) { return String.format("#%06x", c & 0xFFFFFF); }

    static int hsv(float h, float s, float v) { return Color.HSVToColor(new float[]{h, Math.max(0, Math.min(1, s)), Math.max(0, Math.min(1, v))}); }

    /** Up to 6 vibrant, distinct colours (best first). */
    static int[] extract(Bitmap src) {
        int w = 56, h = Math.max(1, Math.round(56f * src.getHeight() / Math.max(1, src.getWidth())));
        Bitmap b = Bitmap.createScaledBitmap(src, w, h, true);
        float[][] acc = new float[36][4];
        float[] hv = new float[3];
        long ar = 0, ag = 0, ab = 0;
        int n = w * h;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = b.getPixel(x, y);
                ar += Color.red(p);
                ag += Color.green(p);
                ab += Color.blue(p);
                Color.colorToHSV(p, hv);
                if (hv[1] < 0.22f || hv[2] < 0.22f) continue;
                float wt = hv[1] * hv[2] + 0.04f;
                int bk = ((int) (hv[0] / 10f)) % 36;
                acc[bk][0] += wt;
                acc[bk][1] += wt * Color.red(p);
                acc[bk][2] += wt * Color.green(p);
                acc[bk][3] += wt * Color.blue(p);
            }
        }
        ArrayList<Integer> order = new ArrayList<Integer>();
        for (int i = 0; i < 36; i++) if (acc[i][0] > 0.4f) order.add(i);
        final float[][] fa = acc;
        java.util.Collections.sort(order, new java.util.Comparator<Integer>() {
            public int compare(Integer x, Integer y) { return Float.compare(fa[y][0], fa[x][0]); }
        });
        ArrayList<Integer> out = new ArrayList<Integer>();
        ArrayList<Integer> used = new ArrayList<Integer>();
        for (int bk : order) {
            boolean close = false;
            for (int u : used) {
                int d = Math.abs(u - bk);
                if (Math.min(d, 36 - d) < 3) { close = true; break; }
            }
            if (close) continue;
            used.add(bk);
            float wt = acc[bk][0];
            out.add(Color.rgb(Math.round(acc[bk][1] / wt), Math.round(acc[bk][2] / wt), Math.round(acc[bk][3] / wt)));
            if (out.size() >= 6) break;
        }
        if (out.isEmpty()) out.add(Color.rgb((int) (ar / n), (int) (ag / n), (int) (ab / n)));
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    static int onColor(int c) {
        double y = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000.0;
        return y > 150 ? 0xFF111111 : 0xFFFFFFFF;
    }

    /** Fills the colour keys of cfg from one accent colour. */
    static void scheme(JSONObject cfg, int raw) {
        float[] v = new float[3];
        Color.colorToHSV(raw, v);
        float h = v[0];
        float s = Math.max(0.5f, Math.min(0.95f, v[1]));
        float val = Math.max(0.6f, Math.min(0.95f, v[2]));
        int accent = hsv(h, s, val);
        int dark = hsv(h, 0.55f, 0.22f);
        int border = hsv(h, 0.6f, 0.12f);
        U.put(cfg, "accent", hex(accent));
        U.put(cfg, "updateBg", hex(accent));
        U.put(cfg, "backdrop", hex(hsv(h, 0.2f, 1f)));
        U.put(cfg, "card", hex(hsv(h, 0.03f, 1f)));
        U.put(cfg, "text", hex(hsv(h, 0.6f, 0.30f)));
        U.put(cfg, "smallColor", hex(dark));
        U.put(cfg, "featureText", hex(hsv(h, 0.5f, 0.32f)));
        U.put(cfg, "subColor", hex(hsv(h, 0.25f, 0.5f)));
        U.put(cfg, "exitBg", "#ffffff");
        U.put(cfg, "exitFg", hex(border));
        U.put(cfg, "btnText", hex(onColor(accent)));
        U.put(cfg, "border", hex(border));
        U.put(cfg, "exitBorder", hex(border));
        U.put(cfg, "updateBorder", hex(border));
        U.put(cfg, "category", "Auto");
    }
}
