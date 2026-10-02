package com.eren.admin;

import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
    private static final LruCache<String, Bitmap> BMP = new LruCache<String, Bitmap>(12);
    private static final String UA = "Mozilla/5.0 (Linux; Android) Eren/1.0";

    /** Admin preview sets this to skip every animation. */
    public static boolean noAnim = false;

    private static Dialog dialog;
    private static boolean polling;
    private static boolean decided;
    private static String lastRaw = "";
    private static String etag = null;

    public interface Actions {
        void onExit();
        void onUpdate(String url);
        void onClose();
    }

    public interface BitmapCb { void done(Bitmap b); }

    public interface MediaCb { void done(String path, String url); }

    /** Used by Eren Admin only. */
    public static void setDb(String u) { DB_URL = u; }

    // =====================================================================
    //  PUBLIC ENTRY
    // =====================================================================

    public static void show(final Activity act) {
        stop();
        polling = true;
        decided = false;
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

    private static String base() {
        String b = DB_URL.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    private static void fetch(final Activity act, final boolean first) {
        new Thread(new Runnable() {
            public void run() {
                String raw = null;
                boolean notModified = false;
                try {
                    URL u = new URL(base() + "/eren/apps/" + Uri.encode(APP_KEY) + ".json");
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
                        if (polling) schedule(act);
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

    private static void hide() {
        try { if (dialog != null && dialog.isShowing()) dialog.dismiss(); } catch (Throwable t) { }
        dialog = null;
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
        if (!on) { hide(); return; }
        JSONObject cfg = app.optJSONObject("dialog");
        if (cfg == null) cfg = new JSONObject();

        int min = cfg.optInt("minVersion", 0);
        if (min > 0) {
            try {
                PackageInfo pi = act.getPackageManager().getPackageInfo(act.getPackageName(), 0);
                if (pi.versionCode >= min) { hide(); return; }
            } catch (Throwable t) { }
        }

        if (!decided) {
            decided = true;
            if (!shouldShow(act, cfg)) { polling = false; return; }
            bump("shown");
        }
        present(act, cfg);
    }

    private static boolean shouldShow(Activity act, JSONObject cfg) {
        String mode = cfg.optString("showMode", "always");
        SharedPreferences sp = act.getSharedPreferences("eren_dialog", 0);
        long rev = cfg.optLong("rev", 0);
        boolean ok = true;
        if ("once".equals(mode)) ok = sp.getLong("rev_" + APP_KEY, -1) != rev;
        else if ("daily".equals(mode)) ok = System.currentTimeMillis() - sp.getLong("last_" + APP_KEY, 0) > 86400000L;
        if (ok) sp.edit().putLong("rev_" + APP_KEY, rev).putLong("last_" + APP_KEY, System.currentTimeMillis()).apply();
        return ok;
    }

    /** Anonymous counters shown in Eren Admin. */
    private static void bump(final String field) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    URL u = new URL(base() + "/eren/stats/" + Uri.encode(APP_KEY) + ".json");
                    HttpURLConnection c = (HttpURLConnection) u.openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    c.setRequestMethod("POST");
                    c.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setDoOutput(true);
                    String body = "{\"" + field + "\":{\".sv\":{\"increment\":1}},\"last\":{\".sv\":\"timestamp\"}}";
                    c.getOutputStream().write(body.getBytes("UTF-8"));
                    c.getResponseCode();
                    c.disconnect();
                } catch (Throwable t) { }
            }
        }).start();
    }

    private static void present(final Activity act, JSONObject cfg) {
        hide();

        final boolean cancel = cfg.optBoolean("cancelable", false);
        final String exitAction = cfg.optString("exitAction", "close");

        final Dialog d = new Dialog(act, android.R.style.Theme_Material_Light_NoActionBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setCancelable(cancel);
        d.setCanceledOnTouchOutside(false);

        final View[] rootRef = new View[1];
        final JSONObject fc = cfg;
        View root = buildDialogView(act, cfg, new Actions() {
            public void onExit() {
                bump("exits");
                final Runnable end = new Runnable() {
                    public void run() {
                        polling = false;
                        try { d.dismiss(); } catch (Throwable t) { }
                        if (!"dismiss".equals(exitAction)) {
                            act.finishAffinity();
                            MAIN.postDelayed(new Runnable() {
                                public void run() { Process.killProcess(Process.myPid()); }
                            }, 250);
                        }
                    }
                };
                playOut(rootRef[0], fc, end);
            }
            public void onUpdate(String url) {
                bump("updates");
                if (url == null || url.trim().length() == 0) return;
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    act.startActivity(i);
                } catch (Throwable t) { }
            }
            public void onClose() {
                polling = false;
                playOut(rootRef[0], fc, new Runnable() {
                    public void run() { try { d.dismiss(); } catch (Throwable t) { } }
                });
            }
        });
        rootRef[0] = root;
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
    //  DIALOG VIEW  (same structure as index.html, scaled)
    //  Also used by Eren Admin for the live preview.
    // =====================================================================

    private static int px(float den, float k, float v) { return Math.round(v * den * k); }

    public static View buildDialogView(final Context ctx, final JSONObject cfg, final Actions actions) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        final float den = dm.density;
        float screenDp = dm.widthPixels / den;

        // size: width % of screen (max 400dp), everything inside scales with it
        float dialogW = Math.min(screenDp * clamp(cfg.optInt("widthPct", 76), 40, 100) / 100f, 400f);
        final float k = dialogW / 330f * clamp(cfg.optInt("uiScale", 100), 50, 150) / 100f;
        final int cardPx = Math.round(dialogW * den);

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
        int exitBorder = col(cfg.optString("exitBorder", cfg.optString("border", "#080808")), border);
        int updBorder = col(cfg.optString("updateBorder", cfg.optString("border", "#080808")), border);

        int radius = cfg.optInt("radius", 31);
        int brandSize = cfg.optInt("brandSize", 56);
        int smallSize = cfg.optInt("smallSize", 16);
        int featSize = cfg.optInt("featureSize", 15);
        int btnSize = cfg.optInt("btnSize", 17);
        int btnH = cfg.optInt("btnH", 55);
        int btnW = clamp(cfg.optInt("btnW", 100), 30, 100);
        int btnRadius = cfg.optInt("btnRadius", 19);
        int btnBorder = cfg.optInt("btnBorder", 3);
        int btnGap = cfg.optInt("btnGap", 12);
        boolean btnCol = "column".equals(cfg.optString("btnLayout", "row"));
        boolean showExit = cfg.optBoolean("showExit", true);

        Typeface fSmall = font(ctx, cfg.optString("fontSmall", "Audiowide-Regular"));
        Typeface fBrand = font(ctx, cfg.optString("fontBrand", "Oswald-Variable"));
        Typeface fFeat = font(ctx, cfg.optString("fontFeature", "Poppins-Bold"));
        Typeface fBtn = font(ctx, cfg.optString("fontButton", "Audiowide-Regular"));

        // page backdrop + scroll
        FrameLayout page = new FrameLayout(ctx);
        int a = Math.round(255f * dim / 100f);
        page.setBackgroundColor((backdrop & 0x00FFFFFF) | (a << 24));
        ScrollView sv = new ScrollView(ctx);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        page.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout holder = new FrameLayout(ctx);
        holder.setClipChildren(false);
        holder.setPadding(px(den, 1, 14), px(den, 1, 24), px(den, 1, 14), px(den, 1, 24));
        sv.addView(holder, new FrameLayout.LayoutParams(-1, -2));

        // card wrapper (animated) + card (rounded, clipped)
        final FrameLayout wrap = new FrameLayout(ctx);
        wrap.setClipChildren(false);
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(cardPx, -2);
        wlp.gravity = Gravity.CENTER;
        holder.addView(wrap, wlp);

        final LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(cardC);
        final float rpx = px(den, k, radius);
        cbg.setCornerRadius(rpx);
        card.setBackground(cbg);
        card.setElevation(px(den, 1, 10));
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rpx); }
        });
        wrap.addView(card, new FrameLayout.LayoutParams(-1, -2));

        // ---- hero (image / video) ----
        String media = cfg.optString("media", "");
        String mtype = cfg.optString("mediaType", "image");
        int heroH = Math.round(cardPx * clamp(cfg.optInt("heroH", 52), 15, 220) / 100f);
        FrameLayout hero = new FrameLayout(ctx);
        hero.setBackgroundColor(0xFFE8E4E6);
        card.addView(hero, new LinearLayout.LayoutParams(-1, heroH));
        if (media.length() == 0) hero.setVisibility(View.GONE);
        else if ("video".equals(mtype)) {
            final Vid vv = new Vid(ctx);
            hero.addView(vv, new FrameLayout.LayoutParams(-1, -1));
            fetchMedia(ctx, media, new MediaCb() {
                public void done(String path, String url) {
                    if (path != null) vv.setSource(path);
                    else if (url != null) vv.setSource(url);
                }
            });
        } else {
            final ImageView iv = new ImageView(ctx);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            hero.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            loadBitmap(ctx, media, 1400, new BitmapCb() {
                public void done(Bitmap b) { if (b != null) iv.setImageBitmap(b); }
            });
        }
        if (media.length() > 0 && cfg.optBoolean("heroFade", true)) {
            View fade = new View(ctx);
            GradientDrawable fg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{(cardC & 0x00FFFFFF), (cardC & 0x00FFFFFF) | (0xDB << 24), cardC});
            fade.setBackground(fg);
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(-1, Math.round(heroH * 0.29f));
            flp.gravity = Gravity.BOTTOM;
            hero.addView(fade, flp);
        }

        // ---- content ----
        final LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        int padTop = media.length() == 0 ? 26 : 14;
        content.setPadding(px(den, k, 17), px(den, k, padTop), px(den, k, 17), px(den, k, 20));
        card.addView(content, new LinearLayout.LayoutParams(-1, -2));

        TextView small = new TextView(ctx);
        small.setText(cfg.optString("small", "UPDATE"));
        small.setTypeface(fSmall);
        small.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(den, k, smallSize));
        small.setTextColor(smallC);
        small.setLetterSpacing(0.04f);
        small.setGravity(Gravity.CENTER);
        small.setIncludeFontPadding(false);
        content.addView(small, new LinearLayout.LayoutParams(-2, -2));

        TextView brand = new TextView(ctx);
        String b1 = cfg.optString("brand1", "MOD");
        String b2 = cfg.optString("brand2", "MASE");
        android.text.SpannableString ss = new android.text.SpannableString(b1 + b2);
        ss.setSpan(new android.text.style.ForegroundColorSpan(textC), 0, b1.length(), 0);
        if (b2.length() > 0)
            ss.setSpan(new android.text.style.ForegroundColorSpan(accent), b1.length(), b1.length() + b2.length(), 0);
        brand.setText(ss);
        brand.setTypeface(fBrand);
        brand.setLetterSpacing(0.012f);
        brand.setGravity(Gravity.CENTER);
        brand.setIncludeFontPadding(false);
        brand.setSingleLine(true);
        if (Build.VERSION.SDK_INT >= 26) {
            brand.setAutoSizeTextTypeUniformWithConfiguration(px(den, k, 14), px(den, k, brandSize), 1, TypedValue.COMPLEX_UNIT_PX);
        } else {
            brand.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(den, k, brandSize));
        }
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = px(den, k, 3);
        content.addView(brand, blp);

        String sub = cfg.optString("subtitle", "");
        if (sub.length() > 0) {
            TextView st = new TextView(ctx);
            st.setText(sub);
            st.setTypeface(fFeat);
            st.setTextColor(col(cfg.optString("subColor", "#7a7d88"), 0xFF7A7D88));
            st.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(den, k, cfg.optInt("subSize", 13)));
            st.setGravity(Gravity.CENTER);
            st.setIncludeFontPadding(false);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
            slp.topMargin = px(den, k, 6);
            content.addView(st, slp);
        }

        // features
        LinearLayout feats = new LinearLayout(ctx);
        feats.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable fbg = new GradientDrawable();
        fbg.setColor(cardC);
        fbg.setCornerRadius(px(den, k, 22));
        feats.setBackground(fbg);
        feats.setElevation(px(den, 1, 4));
        feats.setPadding(px(den, k, 17), px(den, k, 15), px(den, k, 17), px(den, k, 15));
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
        fl.topMargin = px(den, k, 18);
        fl.leftMargin = px(den, 1, 3);
        fl.rightMargin = px(den, 1, 3);
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
            s.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(den, k, 18));
            s.setTypeface(Typeface.DEFAULT_BOLD);
            s.setIncludeFontPadding(false);
            row.addView(s, new LinearLayout.LayoutParams(-2, -2));
            TextView t = new TextView(ctx);
            t.setText(arr.optString(i));
            t.setTypeface(fFeat);
            t.setTextColor(featC);
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(den, k, featSize));
            t.setIncludeFontPadding(false);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
            tl.leftMargin = px(den, k, 9);
            row.addView(t, tl);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.topMargin = px(den, k, 4);
            rl.bottomMargin = px(den, k, 4);
            feats.addView(row, rl);
        }
        if (arr.length() == 0) feats.setVisibility(View.GONE);

        // buttons
        LinearLayout btns = new LinearLayout(ctx);
        btns.setOrientation(btnCol ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        int contentW = cardPx - 2 * px(den, k, 17);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Math.round(contentW * btnW / 100f), -2);
        bl.topMargin = px(den, k, 22);
        content.addView(btns, bl);

        boolean shadow = cfg.optBoolean("btnShadow", false);
        final TextView exit = button(ctx, den, k, cfg.optString("exitText", "EXIT"), fBtn, btnSize, exitFg, exitBg,
                exitBorder, btnBorder, btnRadius, btnH, shadow);
        final TextView upd = button(ctx, den, k, cfg.optString("updateText", "UPDATE"), fBtn, btnSize, btnText, updBg,
                updBorder, btnBorder, btnRadius, btnH, shadow);
        int half = px(den, k, btnGap) / 2;
        if (btnCol) {
            LinearLayout.LayoutParams u1 = new LinearLayout.LayoutParams(-1, -2);
            u1.bottomMargin = showExit ? px(den, k, btnGap) : 0;
            btns.addView(upd, u1);
            if (showExit) btns.addView(exit, new LinearLayout.LayoutParams(-1, -2));
        } else {
            if (showExit) {
                LinearLayout.LayoutParams e1 = new LinearLayout.LayoutParams(0, -2, 1f);
                e1.rightMargin = half;
                btns.addView(exit, e1);
            }
            LinearLayout.LayoutParams e2 = new LinearLayout.LayoutParams(0, -2, showExit ? 1.05f : 1f);
            if (showExit) e2.leftMargin = half;
            btns.addView(upd, e2);
        }

        final String url = cfg.optString("updateUrl", "");
        final Actions act = actions;
        exit.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { if (act != null) act.onExit(); }
        });
        upd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { if (act != null) act.onUpdate(url); }
        });
        setPress(exit, cfg);
        setPress(upd, cfg);

        // close (X)
        if (cfg.optBoolean("showClose", false)) {
            TextView x = new TextView(ctx);
            x.setText("\u2715");
            x.setTextColor(0xFFFFFFFF);
            x.setGravity(Gravity.CENTER);
            x.setIncludeFontPadding(false);
            x.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(den, k, 14));
            GradientDrawable xg = new GradientDrawable();
            xg.setShape(GradientDrawable.OVAL);
            xg.setColor(0x88000000);
            x.setBackground(xg);
            int xs = px(den, k, 32);
            FrameLayout.LayoutParams xl = new FrameLayout.LayoutParams(xs, xs);
            xl.gravity = Gravity.TOP | Gravity.END;
            xl.setMargins(0, px(den, k, 10), px(den, k, 10), 0);
            wrap.addView(x, xl);
            x.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { if (act != null) act.onClose(); }
            });
        }

        page.setTag(wrap);

        // animations
        if (!noAnim && cfg.optBoolean("animOn", true)) {
            wrap.setAlpha(0f);
            final DisplayMetrics fdm = dm;
            wrap.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                boolean done = false;
                public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                    if (done || r - l <= 0) return;
                    done = true;
                    playIn(wrap, content, upd, cfg, fdm, den);
                }
            });
        }
        return page;
    }

    private static TextView button(Context ctx, float den, float k, String text, Typeface tf, int size, int tc, int bg,
                                   int border, int bw, int radius, int h, boolean shadow) {
        TextView b = new TextView(ctx);
        b.setText(text);
        b.setTypeface(tf);
        b.setTextColor(tc);
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(den, k, size));
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setHeight(px(den, k, h));
        b.setClickable(true);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(px(den, k, radius));
        if (bw > 0) g.setStroke(Math.max(1, px(den, k, bw)), border);
        b.setBackground(g);
        if (shadow) b.setElevation(px(den, 1, 5));
        return b;
    }

    // =====================================================================
    //  ANIMATIONS
    // =====================================================================

    public static class Elastic implements Interpolator {
        public float getInterpolation(float t) {
            if (t <= 0f || t >= 1f) return t;
            return (float) (Math.pow(2, -10 * t) * Math.sin((t - 0.075) * (2 * Math.PI) / 0.3) + 1);
        }
    }

    public static Interpolator ease(String n, String type) {
        if (n == null || n.equals("auto")) {
            if (type.equals("pop")) n = "overshoot";
            else if (type.equals("bounce") || type.equals("drop")) n = "bounce";
            else if (type.equals("elastic") || type.equals("jelly") || type.equals("swing")) n = "elastic";
            else n = "decelerate";
        }
        if (n.equals("accelerate")) return new AccelerateInterpolator();
        if (n.equals("accdecel")) return new AccelerateDecelerateInterpolator();
        if (n.equals("overshoot")) return new OvershootInterpolator(1.6f);
        if (n.equals("bounce")) return new BounceInterpolator();
        if (n.equals("anticipate")) return new AnticipateOvershootInterpolator();
        if (n.equals("linear")) return new LinearInterpolator();
        if (n.equals("fastslow")) return new PathInterpolator(0.4f, 0f, 0.2f, 1f);
        if (n.equals("elastic")) return new Elastic();
        return new DecelerateInterpolator();
    }

    private static void playIn(final View card, LinearLayout content, View upd, JSONObject cfg, DisplayMetrics dm, float den) {
        String type = cfg.optString("animIn", "zoom_in");
        int dur = clamp(cfg.optInt("animDur", 420), 50, 4000);
        int delay = clamp(cfg.optInt("animDelay", 0), 0, 5000);
        float it = clamp(cfg.optInt("animPower", 60), 5, 200) / 100f;
        float W = dm.widthPixels, H = dm.heightPixels;
        Interpolator ip = ease(cfg.optString("animEase", "auto"), type);

        card.setPivotX(card.getWidth() / 2f);
        card.setPivotY(card.getHeight() / 2f);
        card.setCameraDistance(8000 * den);

        if (type.equals("slide_up")) card.setTranslationY(H * 0.30f * it);
        else if (type.equals("slide_down")) card.setTranslationY(-H * 0.30f * it);
        else if (type.equals("slide_left")) card.setTranslationX(W * 0.8f * it);
        else if (type.equals("slide_right")) card.setTranslationX(-W * 0.8f * it);
        else if (type.equals("zoom_in")) { card.setScaleX(1f - 0.5f * it); card.setScaleY(1f - 0.5f * it); }
        else if (type.equals("zoom_out")) { card.setScaleX(1f + 0.6f * it); card.setScaleY(1f + 0.6f * it); }
        else if (type.equals("pop")) { card.setScaleX(0f); card.setScaleY(0f); }
        else if (type.equals("bounce")) card.setTranslationY(-H * 0.45f * it);
        else if (type.equals("drop")) { card.setTranslationY(-H * 0.8f * it); card.setRotation(-8f * it); }
        else if (type.equals("flip_x")) card.setRotationX(90f * Math.min(it, 1f));
        else if (type.equals("flip_y")) card.setRotationY(90f * Math.min(it, 1f));
        else if (type.equals("rotate")) { card.setRotation(-90f * it); card.setScaleX(0.5f); card.setScaleY(0.5f); }
        else if (type.equals("spin_zoom")) { card.setRotation(360f * it); card.setScaleX(0f); card.setScaleY(0f); }
        else if (type.equals("swing")) { card.setPivotY(0f); card.setRotation(18f * it); }
        else if (type.equals("jelly")) { card.setScaleX(0.5f); card.setScaleY(1f + 0.5f * it); }
        else if (type.equals("elastic")) { card.setScaleX(0.3f); card.setScaleY(0.3f); }
        else if (type.equals("roll")) { card.setTranslationX(-W * 0.8f * it); card.setRotation(-120f * it); }
        else if (type.equals("unfold")) card.setScaleY(0f);
        else if (type.equals("stretch")) card.setScaleX(0.2f);
        else if (type.equals("tilt")) { card.setRotationX(40f * it); card.setRotationY(-25f * it); card.setScaleX(0.8f); card.setScaleY(0.8f); }
        // "fade" keeps the default state (only alpha)

        ObjectAnimator al = ObjectAnimator.ofFloat(card, View.ALPHA, 0f, 1f);
        al.setStartDelay(delay);
        al.setDuration(Math.min(dur, 320));
        al.start();
        card.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).rotation(0f)
                .rotationX(0f).rotationY(0f).setDuration(dur).setStartDelay(delay).setInterpolator(ip).start();

        if (cfg.optBoolean("animStagger", true)) {
            int gap = clamp(cfg.optInt("animStaggerMs", 70), 0, 600);
            for (int i = 0; i < content.getChildCount(); i++) {
                View c = content.getChildAt(i);
                c.setAlpha(0f);
                c.setTranslationY(14f * den);
                c.animate().alpha(1f).translationY(0f).setDuration(320)
                        .setStartDelay(delay + (long) (dur * 0.35f) + i * (long) gap)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
        }
        attention(upd, cfg, den, delay + dur + 200);
    }

    private static void attention(final View v, JSONObject cfg, float den, long after) {
        String t = cfg.optString("attn", "none");
        if (t.equals("none")) return;
        final int ms = clamp(cfg.optInt("attnMs", 1200), 200, 6000);
        final ObjectAnimator an;
        if (t.equals("pulse")) {
            an = ObjectAnimator.ofPropertyValuesHolder(v, PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.07f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.07f));
            an.setRepeatMode(ObjectAnimator.REVERSE);
        } else if (t.equals("glow")) {
            an = ObjectAnimator.ofFloat(v, View.ALPHA, 1f, 0.6f);
            an.setRepeatMode(ObjectAnimator.REVERSE);
        } else if (t.equals("float")) {
            an = ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 0f, -5f * den);
            an.setRepeatMode(ObjectAnimator.REVERSE);
        } else if (t.equals("wobble")) {
            an = ObjectAnimator.ofFloat(v, View.ROTATION, -2.5f, 2.5f);
            an.setRepeatMode(ObjectAnimator.REVERSE);
        } else {
            // shake
            an = ObjectAnimator.ofPropertyValuesHolder(v, PropertyValuesHolder.ofKeyframe(View.TRANSLATION_X,
                    Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(0.08f, -6f * den), Keyframe.ofFloat(0.16f, 6f * den),
                    Keyframe.ofFloat(0.24f, -6f * den), Keyframe.ofFloat(0.32f, 6f * den), Keyframe.ofFloat(0.4f, 0f),
                    Keyframe.ofFloat(1f, 0f)));
            an.setRepeatMode(ObjectAnimator.RESTART);
        }
        an.setRepeatCount(ObjectAnimator.INFINITE);
        an.setDuration(ms);
        an.setStartDelay(after);
        an.start();
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View x) { }
            public void onViewDetachedFromWindow(View x) { an.cancel(); }
        });
    }

    private static void playOut(View page, JSONObject cfg, final Runnable end) {
        View card = page == null ? null : (View) page.getTag();
        String t = cfg.optString("animOut", "zoom");
        if (card == null || noAnim || !cfg.optBoolean("animOn", true) || t.equals("none")) { end.run(); return; }
        ViewGroupAnim.run(card, t, end);
    }

    /** exit animation helper */
    private static final class ViewGroupAnim {
        static void run(View card, String t, final Runnable end) {
            android.view.ViewPropertyAnimator a = card.animate().alpha(0f).setDuration(220)
                    .setInterpolator(new AccelerateInterpolator());
            if (t.equals("zoom")) a.scaleX(0.8f).scaleY(0.8f);
            else if (t.equals("slide_down")) a.translationY(card.getHeight() * 0.5f);
            else if (t.equals("slide_up")) a.translationY(-card.getHeight() * 0.5f);
            else if (t.equals("spin")) a.rotation(90f).scaleX(0.4f).scaleY(0.4f);
            a.withEndAction(end).start();
        }
    }

    private static void setPress(View b, JSONObject cfg) {
        final String t = cfg.optString("press", "scale");
        if (t.equals("none") || noAnim || !cfg.optBoolean("animOn", true)) return;
        b.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                int a = e.getAction();
                if (a == MotionEvent.ACTION_DOWN) {
                    v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start();
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(t.equals("bounce") ? 320 : 120)
                            .setInterpolator(t.equals("bounce") ? new OvershootInterpolator(3f) : new DecelerateInterpolator()).start();
                }
                return false;
            }
        });
    }

    // =====================================================================
    //  VIDEO (center-crop, muted, looping)
    // =====================================================================

    public static class Vid extends TextureView implements TextureView.SurfaceTextureListener {
        private MediaPlayer mp;
        private String src;
        private SurfaceTexture st;

        public Vid(Context c) {
            super(c);
            setSurfaceTextureListener(this);
        }

        public void setSource(String s) {
            src = s;
            start();
        }

        private void start() {
            if (src == null || st == null) return;
            release();
            try {
                mp = new MediaPlayer();
                mp.setSurface(new Surface(st));
                mp.setDataSource(src);
                mp.setLooping(true);
                mp.setVolume(0f, 0f);
                mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    public void onPrepared(MediaPlayer p) {
                        crop();
                        p.start();
                    }
                });
                mp.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                    public void onVideoSizeChanged(MediaPlayer p, int w, int h) { crop(); }
                });
                mp.prepareAsync();
            } catch (Throwable t) { }
        }

        private void crop() {
            if (mp == null) return;
            int a = mp.getVideoWidth(), b = mp.getVideoHeight(), w = getWidth(), h = getHeight();
            if (a == 0 || b == 0 || w == 0 || h == 0) return;
            float sc = Math.max((float) w / a, (float) h / b);
            Matrix m = new Matrix();
            m.setScale(a * sc / w, b * sc / h, w / 2f, h / 2f);
            setTransform(m);
        }

        private void release() {
            if (mp != null) {
                try { mp.stop(); } catch (Throwable t) { }
                try { mp.release(); } catch (Throwable t) { }
                mp = null;
            }
        }

        public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { st = s; start(); }
        public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) { crop(); }
        public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { release(); st = null; return true; }
        public void onSurfaceTextureUpdated(SurfaceTexture s) { }
    }

    // =====================================================================
    //  MEDIA / HELPERS
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

    private static InputStream open(String u) throws Exception {
        for (int i = 0; i < 6; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", UA);
            c.setInstanceFollowRedirects(false);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) break;
                u = new URL(new URL(u), loc).toString();
                continue;
            }
            if (code >= 400) throw new Exception("HTTP " + code);
            return c.getInputStream();
        }
        throw new Exception("bad redirect");
    }

    private static byte[] readBytes(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toByteArray();
    }

    private static String readAll(InputStream in) throws Exception {
        return new String(readBytes(in), "UTF-8");
    }

    private static String safe(String id) { return id.replaceAll("[^A-Za-z0-9_-]", ""); }

    /** Admin calls this after uploading so the preview does not download again. */
    public static void putCache(Context ctx, String id, byte[] data) {
        try {
            File f = new File(ctx.getCacheDir(), "eren_" + safe(id));
            FileOutputStream o = new FileOutputStream(f);
            o.write(data);
            o.close();
        } catch (Throwable t) { }
    }

    /** Blocking. rtdb:ID / data: -> local file path. http -> null. */
    public static String localPath(Context ctx, String src) throws Exception {
        if (src.startsWith("rtdb:")) {
            String id = src.substring(5);
            File f = new File(ctx.getCacheDir(), "eren_" + safe(id));
            if (!f.exists() || f.length() == 0) {
                String raw = readAll(open(base() + "/eren/media/" + Uri.encode(id) + ".json"));
                JSONObject o = new JSONObject(raw);
                byte[] data = Base64.decode(o.optString("data"), Base64.DEFAULT);
                File tmp = new File(ctx.getCacheDir(), "eren_tmp_" + safe(id));
                FileOutputStream os = new FileOutputStream(tmp);
                os.write(data);
                os.close();
                tmp.renameTo(f);
            }
            return f.getAbsolutePath();
        }
        if (src.startsWith("data:")) {
            File f = new File(ctx.getCacheDir(), "eren_d" + Integer.toHexString(src.hashCode()));
            if (!f.exists() || f.length() == 0) {
                byte[] data = Base64.decode(src.substring(src.indexOf(',') + 1), Base64.DEFAULT);
                FileOutputStream os = new FileOutputStream(f);
                os.write(data);
                os.close();
            }
            return f.getAbsolutePath();
        }
        return null;
    }

    public static void fetchMedia(final Context ctx, final String src, final MediaCb cb) {
        if (src == null || src.length() == 0) { cb.done(null, null); return; }
        if (src.startsWith("http")) { cb.done(null, src); return; }
        new Thread(new Runnable() {
            public void run() {
                String p = null;
                try { p = localPath(ctx, src); } catch (Throwable t) { }
                final String fp = p;
                MAIN.post(new Runnable() { public void run() { cb.done(fp, null); } });
            }
        }).start();
    }

    /** Loads http(s) / rtdb: / data: image off the main thread; result on main thread. */
    public static void loadBitmap(final Context ctx, final String src, final int maxW, final BitmapCb cb) {
        final String key = src.length() > 200 ? src.hashCode() + ":" + maxW : src + ":" + maxW;
        Bitmap hit = BMP.get(key);
        if (hit != null) { cb.done(hit); return; }
        new Thread(new Runnable() {
            public void run() {
                Bitmap bm = null;
                try {
                    byte[] data;
                    String p = src.startsWith("http") ? null : localPath(ctx, src);
                    if (p != null) data = readBytes(new FileInputStream(p));
                    else data = readBytes(open(src));
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
                if (fb != null) BMP.put(key, fb);
                MAIN.post(new Runnable() { public void run() { cb.done(fb); } });
            }
        }).start();
    }

    /** One frame (about 1s in) of a video, for colour matching. */
    public static void videoFrame(final Context ctx, final String src, final BitmapCb cb) {
        new Thread(new Runnable() {
            public void run() {
                Bitmap bm = null;
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                try {
                    String p = src.startsWith("http") ? null : localPath(ctx, src);
                    if (p != null) r.setDataSource(p);
                    else r.setDataSource(src, new HashMap<String, String>());
                    bm = r.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (bm == null) bm = r.getFrameAtTime();
                } catch (Throwable t) { bm = null; }
                try { r.release(); } catch (Throwable t) { }
                final Bitmap fb = bm;
                MAIN.post(new Runnable() { public void run() { cb.done(fb); } });
            }
        }).start();
    }
}
