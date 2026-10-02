package com.eren.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;
import android.media.MediaPlayer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Eren - update dialog.
 *
 * Hook:  Eren.show(this);
 *
 * MT Manager / smali:  replace the two strings below (DB_URL, APP_KEY) inside Eren.smali.
 * They are NOT final on purpose, so each one stays a plain const-string in the smali.
 */
public class Eren {

    // ---- REPLACE THESE TWO VALUES (Eren.smali -> <clinit>) ----
    private static String DB_URL = "https://YOUR-PROJECT-default-rtdb.firebaseio.com";
    private static String APP_KEY = "MM-XXX-XXX-XXX-ST";
    // -----------------------------------------------------------

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Typeface> FONTS = new HashMap<String, Typeface>();

    /** Admin preview sets this to skip the entrance animation. */
    public static boolean noAnim = false;

    private static Dialog dialog;
    private static boolean polling;
    private static String lastRaw = "";
    private static String etag = null;

    public interface Actions {
        void onExit();
        void onUpdate(String url);
    }

    // =====================================================================
    //  PUBLIC ENTRY
    // =====================================================================

    public static void show(final Activity act) {
        stop();
        polling = true;
        etag = null;
        lastRaw = "";
        fetch(act, true);
    }

    public static void stop() {
        polling = false;
        try { if (dialog != null && dialog.isShowing()) dialog.dismiss(); } catch (Throwable t) { }
        dialog = null;
    }

    // =====================================================================
    //  NETWORK (Firebase RTDB REST)
    // =====================================================================

    private static void fetch(final Activity act, final boolean first) {
        new Thread(new Runnable() {
            public void run() {
                String raw = null;
                boolean notModified = false;
                try {
                    String base = DB_URL.trim();
                    while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                    URL u = new URL(base + "/eren/apps/" + Uri.encode(APP_KEY) + ".json");
                    HttpURLConnection c = (HttpURLConnection) u.openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(15000);
                    c.setRequestProperty("X-Firebase-ETag", "true");
                    if (etag != null && !first) c.setRequestProperty("If-None-Match", etag);
                    int code = c.getResponseCode();
                    if (code == 304) {
                        notModified = true;
                    } else if (code == 200) {
                        String e = c.getHeaderField("ETag");
                        if (e != null) etag = e;
                        raw = readAll(c.getInputStream());
                    }
                    c.disconnect();
                } catch (Throwable t) {
                    raw = null;
                }
                final String fRaw = raw;
                final boolean nm = notModified;
                MAIN.post(new Runnable() {
                    public void run() {
                        if (!polling || act.isFinishing()) return;
                        if (!nm) apply(act, fRaw);
                        schedule(act);
                    }
                });
            }
        }).start();
    }

    private static void schedule(final Activity act) {
        MAIN.postDelayed(new Runnable() {
            public void run() {
                if (polling && !act.isFinishing()) fetch(act, false);
            }
        }, 5000);
    }

    private static void apply(Activity act, String raw) {
        if (raw == null) return;
        if (raw.equals(lastRaw)) return;
        lastRaw = raw;
        JSONObject app = null;
        try {
            if (!raw.trim().equals("null")) app = new JSONObject(raw);
        } catch (Throwable t) { }
        boolean on = app != null && app.optBoolean("enabled", false);
        if (!on) {
            try { if (dialog != null && dialog.isShowing()) dialog.dismiss(); } catch (Throwable t) { }
            dialog = null;
            return;
        }
        JSONObject cfg = app.optJSONObject("dialog");
        if (cfg == null) cfg = new JSONObject();

        int min = cfg.optInt("minVersion", 0);
        if (min > 0) {
            try {
                PackageInfo pi = act.getPackageManager().getPackageInfo(act.getPackageName(), 0);
                if (pi.versionCode >= min) {
                    try { if (dialog != null && dialog.isShowing()) dialog.dismiss(); } catch (Throwable t) { }
                    dialog = null;
                    return;
                }
            } catch (Throwable t) { }
        }
        present(act, cfg);
    }

    private static void present(final Activity act, JSONObject cfg) {
        try { if (dialog != null && dialog.isShowing()) dialog.dismiss(); } catch (Throwable t) { }

        final boolean cancel = cfg.optBoolean("cancelable", false);
        final String exitAction = cfg.optString("exitAction", "close");

        Dialog d = new Dialog(act, android.R.style.Theme_Material_Light_NoActionBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setCancelable(cancel);
        d.setCanceledOnTouchOutside(false);
        final Dialog fd = d;

        View root = buildDialogView(act, cfg, new Actions() {
            public void onExit() {
                if ("dismiss".equals(exitAction)) {
                    polling = false;
                    try { fd.dismiss(); } catch (Throwable t) { }
                    return;
                }
                polling = false;
                try { fd.dismiss(); } catch (Throwable t) { }
                act.finishAffinity();
                MAIN.postDelayed(new Runnable() {
                    public void run() { Process.killProcess(Process.myPid()); }
                }, 250);
            }
            public void onUpdate(String url) {
                if (url == null || url.trim().length() == 0) return;
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    act.startActivity(i);
                } catch (Throwable t) { }
            }
        });
        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog = d;
        d.show();
    }

    // =====================================================================
    //  DIALOG VIEW  (same structure as index.html)
    //  Also used by Eren Admin for the live preview.
    // =====================================================================

    public static View buildDialogView(final Context ctx, JSONObject cfg, final Actions actions) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        final float den = dm.density;
        float screenDp = dm.widthPixels / den;

        // colours
        int backdrop = col(cfg.optString("backdrop", "#ffd0c8"), 0xFFFFD0C8);
        int dim = clamp(cfg.optInt("dim", 100), 0, 100);
        int cardC = col(cfg.optString("card", "#fffafd"), 0xFFFFFAFD);
        int textC = col(cfg.optString("text", "#353539"), 0xFF353539);
        int accent = col(cfg.optString("accent", "#70a0df"), 0xFF70A0DF);
        int smallC = col(cfg.optString("smallColor", "#292a2d"), 0xFF292A2D);
        int featC = col(cfg.optString("featureText", "#37383c"), 0xFF37383C);
        int exitBg = col(cfg.optString("exitBg", "#ffffff"), 0xFFFFFFFF);
        int updBg = col(cfg.optString("updateBg", cfg.optString("accent", "#70a0df")), accent);
        int btnText = col(cfg.optString("btnText", "#050505"), 0xFF050505);
        int exitFg = col(cfg.optString("exitFg", cfg.optString("btnText", "#050505")), btnText);
        int border = col(cfg.optString("border", "#080808"), 0xFF080808);

        boolean big = screenDp >= 600;
        float dialogW = big ? 680f : (screenDp - 30f);
        if (dialogW > 680f) dialogW = 680f;
        float scale = big ? 1f : 1f;
        int radius = cfg.optInt("radius", big ? 36 : 31);
        int brandSize = cfg.optInt("brandSize", big ? 67 : (int) Math.max(48, Math.min(61, screenDp * 0.14f)));
        int smallSize = cfg.optInt("smallSize", big ? 19 : 16);
        int featSize = cfg.optInt("featureSize", big ? 20 : 15);
        int btnSize = cfg.optInt("btnSize", big ? 25 : 17);
        int btnH = big ? 63 : 55;
        int btnBorder = big ? 4 : 3;

        // fonts
        Typeface fSmall = font(ctx, cfg.optString("fontSmall", "Audiowide-Regular"));
        Typeface fBrand = font(ctx, cfg.optString("fontBrand", "Oswald-Variable"));
        Typeface fFeat = font(ctx, cfg.optString("fontFeature", "Poppins-Bold"));
        Typeface fBtn = font(ctx, cfg.optString("fontButton", "Audiowide-Regular"));

        // root = page backdrop
        FrameLayout page = new FrameLayout(ctx);
        int a = Math.round(255f * dim / 100f);
        page.setBackgroundColor((backdrop & 0x00FFFFFF) | (a << 24));

        android.widget.ScrollView sv = new android.widget.ScrollView(ctx);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        page.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout holder = new FrameLayout(ctx);
        holder.setPadding(dp(den, 12), dp(den, 24), dp(den, 12), dp(den, 24));
        sv.addView(holder, new FrameLayout.LayoutParams(-1, -2));

        // card
        final LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(cardC);
        final float rpx = dp(den, radius);
        cbg.setCornerRadius(rpx);
        card.setBackground(cbg);
        card.setElevation(dp(den, 10));
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rpx); }
        });
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(den, Math.round(dialogW)), -2);
        clp.gravity = Gravity.CENTER;
        holder.addView(card, clp);

        // ---- hero ----
        String ratio = cfg.optString("heroRatio", "1200:675");
        float rw = 1200f, rh = 675f;
        try {
            String[] p = ratio.split(":");
            rw = Float.parseFloat(p[0]);
            rh = Float.parseFloat(p[1]);
        } catch (Throwable t) { }
        int cardPx = dp(den, Math.round(dialogW));
        int heroH = Math.round(cardPx * (rh / rw));

        FrameLayout hero = new FrameLayout(ctx);
        hero.setBackgroundColor(0xFFE8E4E6);
        card.addView(hero, new LinearLayout.LayoutParams(-1, heroH));

        String media = cfg.optString("media", "");
        String mtype = cfg.optString("mediaType", "image");
        if (media.length() > 0) {
            if ("video".equals(mtype)) {
                final VideoView vv = new VideoView(ctx);
                FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(-1, -1);
                vlp.gravity = Gravity.CENTER;
                hero.addView(vv, vlp);
                vv.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    public void onPrepared(MediaPlayer mp) {
                        mp.setLooping(true);
                        mp.setVolume(0f, 0f);
                        vv.start();
                    }
                });
                try { vv.setVideoURI(Uri.parse(media)); } catch (Throwable t) { }
            } else {
                final ImageView iv = new ImageView(ctx);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                hero.addView(iv, new FrameLayout.LayoutParams(-1, -1));
                loadBitmap(media, 1400, new BitmapCb() {
                    public void done(Bitmap b) { if (b != null) iv.setImageBitmap(b); }
                });
            }
        }

        // bottom fade (29%)
        View fade = new View(ctx);
        GradientDrawable fg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{ (cardC & 0x00FFFFFF), (cardC & 0x00FFFFFF) | (0xDB << 24), cardC });
        fade.setBackground(fg);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(-1, Math.round(heroH * 0.29f));
        flp.gravity = Gravity.BOTTOM;
        hero.addView(fade, flp);

        // ---- content ----
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        int padH = big ? 25 : 17;
        content.setPadding(dp(den, padH), dp(den, big ? 17 : 16), dp(den, padH), dp(den, big ? 23 : 20));
        card.addView(content, new LinearLayout.LayoutParams(-1, -2));

        // small title
        TextView small = new TextView(ctx);
        small.setText(cfg.optString("small", "UPDATE"));
        small.setTypeface(fSmall);
        small.setTextSize(TypedValue.COMPLEX_UNIT_DIP, smallSize);
        small.setTextColor(smallC);
        small.setLetterSpacing(0.04f);
        small.setGravity(Gravity.CENTER);
        small.setIncludeFontPadding(false);
        content.addView(small, new LinearLayout.LayoutParams(-2, -2));

        // brand
        TextView brand = new TextView(ctx);
        String b1 = cfg.optString("brand1", "MOD");
        String b2 = cfg.optString("brand2", "MASE");
        android.text.SpannableString ss = new android.text.SpannableString(b1 + b2);
        ss.setSpan(new android.text.style.ForegroundColorSpan(textC), 0, b1.length(), 0);
        if (b2.length() > 0)
            ss.setSpan(new android.text.style.ForegroundColorSpan(accent), b1.length(), b1.length() + b2.length(), 0);
        brand.setText(ss);
        brand.setTypeface(fBrand);
        brand.setTextSize(TypedValue.COMPLEX_UNIT_DIP, brandSize);
        brand.setLetterSpacing(0.012f);
        brand.setGravity(Gravity.CENTER);
        brand.setIncludeFontPadding(false);
        brand.setMaxLines(1);
        brand.setSingleLine(true);
        brand.setEllipsize(null);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = dp(den, 4);
        content.addView(brand, blp);

        // features card
        LinearLayout feats = new LinearLayout(ctx);
        feats.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable fbg = new GradientDrawable();
        fbg.setColor(cardC);
        fbg.setCornerRadius(dp(den, big ? 25 : 22));
        feats.setBackground(fbg);
        feats.setElevation(dp(den, 4));
        int fp = big ? 25 : 17;
        feats.setPadding(dp(den, fp), dp(den, big ? 20 : 17), dp(den, fp), dp(den, big ? 20 : 17));
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
        fl.topMargin = dp(den, big ? 24 : 21);
        fl.leftMargin = dp(den, 3);
        fl.rightMargin = dp(den, 3);
        content.addView(feats, fl);

        String sym = cfg.optString("symbol", "\u2756");
        JSONArray arr = cfg.optJSONArray("features");
        if (arr == null) {
            arr = new JSONArray();
            arr.put("New Features Available");
            arr.put("Previous Bug Fixed");
        }
        for (int i = 0; i < arr.length(); i++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView s = new TextView(ctx);
            s.setText(sym);
            s.setTextColor(0xFF424347);
            s.setTextSize(TypedValue.COMPLEX_UNIT_DIP, big ? 20 : 18);
            s.setTypeface(Typeface.DEFAULT_BOLD);
            s.setIncludeFontPadding(false);
            row.addView(s, new LinearLayout.LayoutParams(-2, -2));
            TextView t = new TextView(ctx);
            t.setText(arr.optString(i));
            t.setTypeface(fFeat);
            t.setTextColor(featC);
            t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, featSize);
            t.setIncludeFontPadding(false);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
            tl.leftMargin = dp(den, 9);
            row.addView(t, tl);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.topMargin = dp(den, 4);
            rl.bottomMargin = dp(den, 4);
            feats.addView(row, rl);
        }
        if (arr.length() == 0) feats.setVisibility(View.GONE);

        // buttons
        LinearLayout btns = new LinearLayout(ctx);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = dp(den, big ? 30 : 25);
        content.addView(btns, bl);

        TextView exit = button(ctx, den, cfg.optString("exitText", "EXIT"), fBtn, btnSize, exitFg, exitBg,
                border, btnBorder, big ? 22 : 19, btnH);
        TextView upd = button(ctx, den, cfg.optString("updateText", "UPDATE"), fBtn, btnSize, btnText, updBg,
                border, btnBorder, big ? 22 : 19, btnH);
        LinearLayout.LayoutParams e1 = new LinearLayout.LayoutParams(0, -2, 1f);
        LinearLayout.LayoutParams e2 = new LinearLayout.LayoutParams(0, -2, 1.05f);
        e1.rightMargin = dp(den, big ? 12 : 6);
        e2.leftMargin = dp(den, big ? 12 : 6);
        btns.addView(exit, e1);
        btns.addView(upd, e2);

        final String url = cfg.optString("updateUrl", "");
        final Actions act = actions;
        exit.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { if (act != null) act.onExit(); }
        });
        upd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { if (act != null) act.onUpdate(url); }
        });

        // entrance animation (dialogIn)
        if (noAnim) return page;
        card.setAlpha(0f);
        card.setTranslationY(dp(den, 16));
        card.setScaleX(0.98f);
        card.setScaleY(0.98f);
        card.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(400)
                .setInterpolator(new DecelerateInterpolator()).start();
        return page;
    }

    private static TextView button(Context ctx, float den, String text, Typeface tf, int size, int tc, int bg,
                                   int border, int bw, int radius, int h) {
        final TextView b = new TextView(ctx);
        b.setText(text);
        b.setTypeface(tf);
        b.setTextColor(tc);
        b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setMinHeight(dp(den, h));
        b.setHeight(dp(den, h));
        b.setClickable(true);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(den, radius));
        g.setStroke(dp(den, bw), border);
        b.setBackground(g);
        b.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                int a = e.getAction();
                if (a == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
                else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                return false;
            }
        });
        return b;
    }

    // =====================================================================
    //  HELPERS
    // =====================================================================

    public static int dp(float den, float v) { return Math.round(v * den); }

    public static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    public static int col(String s, int def) {
        try { return Color.parseColor(s.trim()); } catch (Throwable t) { return def; }
    }

    public static Typeface font(Context ctx, String name) {
        Typeface t = FONTS.get(name);
        if (t != null) return t;
        try {
            t = Typeface.createFromAsset(ctx.getAssets(), "fonts/" + name + ".ttf");
        } catch (Throwable e) {
            t = Typeface.DEFAULT_BOLD;
        }
        FONTS.put(name, t);
        return t;
    }

    public interface BitmapCb { void done(Bitmap b); }

    /** Loads http(s) URL or data: URI off the main thread, result delivered on main thread. */
    public static void loadBitmap(final String src, final int maxW, final BitmapCb cb) {
        new Thread(new Runnable() {
            public void run() {
                Bitmap bm = null;
                try {
                    byte[] data;
                    if (src.startsWith("data:")) {
                        int i = src.indexOf(',');
                        data = Base64.decode(src.substring(i + 1), Base64.DEFAULT);
                    } else {
                        HttpURLConnection c = (HttpURLConnection) new URL(src).openConnection();
                        c.setConnectTimeout(10000);
                        c.setReadTimeout(20000);
                        c.setInstanceFollowRedirects(true);
                        InputStream in = c.getInputStream();
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                        in.close();
                        data = bo.toByteArray();
                    }
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, o);
                    int s = 1;
                    while (o.outWidth / (s * 2) >= maxW) s *= 2;
                    o = new BitmapFactory.Options();
                    o.inSampleSize = s;
                    bm = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                } catch (Throwable t) { bm = null; }
                final Bitmap fb = bm;
                MAIN.post(new Runnable() { public void run() { cb.done(fb); } });
            }
        }).start();
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }
}
