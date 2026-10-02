package com.eren.admin;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** Dialog editor: live preview on top, many options below. */
final class Editor {
    static final String[] FONT_FILES = {"Audiowide-Regular", "Oswald-Variable", "Poppins-Bold", "Poppins-SemiBold",
            "Righteous-Regular", "Bungee-Regular", "Orbitron-Variable"};
    static final String[] FONT_NAMES = {"Audiowide", "Oswald", "Poppins Bold", "Poppins Semi", "Righteous", "Bungee",
            "Orbitron"};

    static final String[] PRESET_NAMES = {"Default", "Ocean", "Forest", "Sunset", "Purple", "Gold", "Rose", "Dark", "Mono"};
    // backdrop, card, text, accent, exitBg, exitFg, btnText, border, featureText, smallColor
    static final String[][] PRESETS = {
            {"#ffd0c8", "#fffafd", "#353539", "#70a0df", "#ffffff", "#050505", "#050505", "#080808", "#37383c", "#292a2d"},
            {"#cfe8ff", "#f8fcff", "#12304a", "#1e88e5", "#ffffff", "#0b1d2d", "#ffffff", "#0b1d2d", "#1b3a55", "#12304a"},
            {"#d4ecd6", "#fbfffb", "#1d3a28", "#2e9e5b", "#ffffff", "#10241a", "#ffffff", "#10241a", "#274a35", "#1d3a28"},
            {"#ffd9b8", "#fffaf5", "#4a2a1a", "#ff7043", "#ffffff", "#2a1209", "#ffffff", "#2a1209", "#5a3522", "#4a2a1a"},
            {"#e4d4ff", "#fdfaff", "#2e1a4a", "#8e5cf0", "#ffffff", "#1a0d2e", "#ffffff", "#1a0d2e", "#3f2a5e", "#2e1a4a"},
            {"#fff0c2", "#fffdf5", "#3a2e10", "#e0a800", "#ffffff", "#241b05", "#241b05", "#241b05", "#4a3c18", "#3a2e10"},
            {"#ffd1e0", "#fffafc", "#4a1a2c", "#e91e63", "#ffffff", "#2a0d18", "#ffffff", "#2a0d18", "#5e2a3f", "#4a1a2c"},
            {"#0d0f16", "#1b1e2a", "#f2f4ff", "#7c9cff", "#2a2e3d", "#f2f4ff", "#0b0b10", "#3b4157", "#dfe3f5", "#cfd4ea"},
            {"#e6e6e6", "#ffffff", "#111111", "#111111", "#ffffff", "#111111", "#ffffff", "#111111", "#222222", "#111111"}
    };
    static final String[] PKEYS = {"backdrop", "card", "text", "accent", "exitBg", "exitFg", "btnText", "border",
            "featureText", "smallColor"};

    static final String[] SWATCH = {"#ffd0c8", "#fffafd", "#ffffff", "#f2f4ff", "#e6e6e6", "#9aa0b4", "#353539", "#111111",
            "#70a0df", "#1e88e5", "#4F6AF5", "#8e5cf0", "#e91e63", "#ff7043", "#e0a800", "#2e9e5b", "#00A68C", "#e5484d",
            "#cfe8ff", "#d4ecd6", "#ffd9b8", "#e4d4ff", "#fff0c2", "#ffd1e0", "#0d0f16", "#1b1e2a", "#12304a", "#1d3a28",
            "#4a2a1a", "#2e1a4a"};

    static final String[] ANIM_IN = {"fade", "slide_up", "slide_down", "slide_left", "slide_right", "zoom_in", "zoom_out",
            "pop", "bounce", "drop", "flip_x", "flip_y", "rotate", "spin_zoom", "swing", "jelly", "elastic", "roll",
            "unfold", "stretch", "tilt"};
    static final String[] ANIM_IN_N = {"Fade", "Slide up", "Slide down", "Slide left", "Slide right", "Zoom in", "Zoom out",
            "Pop", "Bounce", "Drop", "Flip X", "Flip Y", "Rotate", "Spin zoom", "Swing", "Jelly", "Elastic", "Roll",
            "Unfold", "Stretch", "Tilt"};

    static JSONObject defaults() {
        JSONObject o = new JSONObject();
        U.put(o, "media", "");
        U.put(o, "mediaType", "image");
        U.put(o, "heroH", 52);
        U.put(o, "heroFade", true);
        U.put(o, "widthPct", 76);
        U.put(o, "uiScale", 100);
        U.put(o, "radius", 31);
        U.put(o, "small", "UPDATE");
        U.put(o, "brand1", "MOD");
        U.put(o, "brand2", "MASE");
        U.put(o, "subtitle", "");
        U.put(o, "symbol", "\u2756");
        JSONArray f = new JSONArray();
        f.put("New Features Available");
        f.put("Previous Bug Fixed");
        U.put(o, "features", f);
        U.put(o, "exitText", "EXIT");
        U.put(o, "updateText", "UPDATE");
        U.put(o, "updateUrl", "");
        U.put(o, "exitAction", "close");
        U.put(o, "showExit", true);
        U.put(o, "showClose", false);
        U.put(o, "cancelable", false);
        U.put(o, "showMode", "always");
        U.put(o, "minVersion", 0);
        U.put(o, "dim", 100);
        U.put(o, "brandSize", 56);
        U.put(o, "btnH", 55);
        U.put(o, "btnW", 100);
        U.put(o, "btnRadius", 19);
        U.put(o, "btnBorder", 3);
        U.put(o, "btnGap", 12);
        U.put(o, "btnSize", 17);
        U.put(o, "btnLayout", "row");
        U.put(o, "btnShadow", false);
        U.put(o, "fontSmall", "Audiowide-Regular");
        U.put(o, "fontBrand", "Oswald-Variable");
        U.put(o, "fontFeature", "Poppins-Bold");
        U.put(o, "fontButton", "Audiowide-Regular");
        U.put(o, "animOn", true);
        U.put(o, "animIn", "zoom_in");
        U.put(o, "animDur", 420);
        U.put(o, "animDelay", 0);
        U.put(o, "animPower", 60);
        U.put(o, "animEase", "auto");
        U.put(o, "animStagger", true);
        U.put(o, "animStaggerMs", 70);
        U.put(o, "animOut", "zoom");
        U.put(o, "press", "scale");
        U.put(o, "attn", "none");
        U.put(o, "attnMs", 1200);
        U.put(o, "autoColor", false);
        for (int i = 0; i < PKEYS.length; i++) U.put(o, PKEYS[i], PRESETS[0][i]);
        U.put(o, "updateBg", PRESETS[0][3]);
        U.put(o, "exitBorder", PRESETS[0][7]);
        U.put(o, "updateBorder", PRESETS[0][7]);
        U.put(o, "subColor", "#7a7d88");
        U.put(o, "category", "Default");
        return o;
    }

    final MainActivity m;
    final String key;
    final JSONObject cfg;
    boolean dirty = false;
    FrameLayout preview;
    Runnable pending;
    LinearLayout featBox, swatchRow;
    final java.util.HashMap<String, EditText> hexFields = new java.util.HashMap<String, EditText>();
    final java.util.HashMap<String, View> swatches = new java.util.HashMap<String, View>();
    final java.util.HashMap<String, SeekBar> seeks = new java.util.HashMap<String, SeekBar>();
    EditText mediaField;
    boolean mediaInternal = false;
    TextView ratioText;
    static final String[] COLOR_KEYS = {"backdrop", "card", "text", "accent", "updateBg", "btnText", "exitBg", "exitFg",
            "border", "exitBorder", "updateBorder", "featureText", "smallColor", "subColor"};

    Editor(MainActivity m, String key, JSONObject saved) {
        this.m = m;
        this.key = key;
        JSONObject d = defaults();
        try {
            if (saved != null) {
                java.util.Iterator<String> it = saved.keys();
                while (it.hasNext()) { String k = it.next(); d.put(k, saved.get(k)); }
            }
        } catch (Throwable t) { }
        cfg = d;
    }

    void set(String k, Object v) {
        U.put(cfg, k, v);
        dirty = true;
        refreshSoon();
    }

    void refreshSoon() {
        if (pending != null) U.MAIN.removeCallbacks(pending);
        pending = new Runnable() { public void run() { refresh(false); } };
        U.MAIN.postDelayed(pending, 140);
    }

    void refresh(boolean anim) {
        if (preview == null) return;
        preview.removeAllViews();
        Eren.noAnim = !anim;
        View v = Eren.buildDialogView(m, cfg, null);
        Eren.noAnim = false;
        int sw = m.getResources().getDisplayMetrics().widthPixels;
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(sw, U.dp(640));
        v.setPivotX(0);
        v.setPivotY(0);
        v.setScaleX(scale());
        v.setScaleY(scale());
        preview.addView(v, p);
    }

    float scale() { return 0.5f; }

    // =====================================================================
    View build() {
        final android.content.Context c = m;
        LinearLayout page = U.col(c);

        LinearLayout bar = U.row(c);
        bar.setPadding(U.dp(8), U.dp(8), U.dp(16), U.dp(8));
        TextView back = U.tv(c, "\u2190", 24, U.TEXT, true);
        back.setPadding(U.dp(12), U.dp(4), U.dp(12), U.dp(4));
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { m.onBackPressed(); } });
        bar.addView(back);
        LinearLayout tt = U.col(c);
        tt.addView(U.tv(c, "Edit dialog", 18, U.TEXT, true));
        tt.addView(U.tv(c, "Live preview", 12, U.SUB, false));
        bar.addView(tt, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView play = U.btn(c, "\u25B6 Play", false, new View.OnClickListener() {
            public void onClick(View v) { refresh(true); }
        });
        play.setPadding(U.dp(14), U.dp(8), U.dp(14), U.dp(8));
        bar.addView(play, U.lp(-2, -2, 0, 0, 8, 0));
        TextView full = U.btn(c, "Test", false, new View.OnClickListener() {
            public void onClick(View v) { fullTest(); }
        });
        full.setPadding(U.dp(14), U.dp(8), U.dp(14), U.dp(8));
        bar.addView(full);
        page.addView(bar);

        int sw = m.getResources().getDisplayMetrics().widthPixels;
        FrameLayout wrap = new FrameLayout(c);
        wrap.setBackground(U.rr(0xFFE9ECF7, 20));
        preview = new FrameLayout(c);
        preview.setClipChildren(true);
        preview.setClipToOutline(true);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(Math.round(sw * scale()), Math.round(U.dp(640) * scale()));
        pp.gravity = Gravity.CENTER;
        wrap.addView(preview, pp);
        wrap.setPadding(0, U.dp(8), 0, U.dp(8));
        page.addView(wrap, U.lp(-1, -2, 16, 0, 16, 8));

        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout list = U.col(c);
        list.setPadding(U.dp(16), U.dp(4), U.dp(16), U.dp(24));
        sv.addView(list);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        buildMedia(list);
        buildAutoColor(list);
        buildSize(list);
        buildText(list);
        buildFeatures(list);
        buildButtons(list);
        buildColors(list);
        buildStyle(list);
        buildAnim(list);
        buildBehavior(list);
        buildTools(list);

        LinearLayout sb = U.row(c);
        sb.setBackgroundColor(U.SURF);
        sb.setElevation(U.dp(8));
        sb.setPadding(U.dp(16), U.dp(10), U.dp(16), U.dp(10));
        TextView reset = U.btn(c, "Reset", false, new View.OnClickListener() {
            public void onClick(View v) {
                m.confirm("Reset dialog?", "All fields go back to the default look.", "Reset", new Runnable() {
                    public void run() { m.openEditor(key, defaults(), true); }
                });
            }
        });
        TextView save = U.btn(c, "Save changes", true, new View.OnClickListener() {
            public void onClick(final View v) { save(); }
        });
        sb.addView(reset, U.lp(-2, -2, 0, 0, 10, 0));
        sb.addView(save, new LinearLayout.LayoutParams(0, -2, 1f));
        page.addView(sb, new LinearLayout.LayoutParams(-1, -2));

        refresh(false);
        m.back = new Runnable() {
            public void run() {
                if (!dirty) { m.detail(key); return; }
                m.confirm("Discard changes?", "You have unsaved changes.", "Discard", new Runnable() {
                    public void run() { m.detail(key); }
                });
            }
        };
        return page;
    }

    void save() {
        U.put(cfg, "rev", System.currentTimeMillis());
        m.busy(true);
        Db.put("/eren/apps/" + key + "/dialog", cfg.toString(), new Db.CB() {
            public void done(boolean ok, String body) {
                m.busy(false);
                if (ok) {
                    try { JSONObject a = m.apps.optJSONObject(key); if (a != null) a.put("dialog", new JSONObject(cfg.toString())); } catch (Throwable t) { }
                    dirty = false;
                    U.toast(m, "Saved \u2714  Dialog updates live");
                } else U.toast(m, "Save failed: " + body);
            }
        });
    }

    void fullTest() {
        final Dialog d = new Dialog(m, android.R.style.Theme_Material_Light_NoActionBar);
        View v = Eren.buildDialogView(m, cfg, new Eren.Actions() {
            public void onExit() { d.dismiss(); }
            public void onUpdate(String url) { }
            public void onClose() { d.dismiss(); }
        });
        d.setContentView(v);
        d.setCancelable(true);
        d.show();
        U.toast(m, "Test mode - Exit or Back closes it");
    }

    // =====================================================================
    //  small UI builders
    // =====================================================================
    LinearLayout sec(LinearLayout list, String title, String sub) {
        LinearLayout c = U.card(m);
        c.addView(U.tv(m, title, 16, U.TEXT, true));
        if (sub != null) c.addView(U.tv(m, sub, 12.5f, U.SUB, false), U.lp(-2, -2, 0, 2, 0, 0));
        list.addView(c, U.lp(-1, -2, 0, 8, 0, 4));
        return c;
    }

    void field(LinearLayout c, String label, final String k, String hint) {
        EditText e = U.input(m, hint, cfg.optString(k, ""));
        e.addTextChangedListener(U.watch(new U.S() { public void on(String s) { set(k, s); } }));
        c.addView(U.labeled(m, label, e), U.wrapLp(0, 12, 0, 0));
    }

    void toggle(LinearLayout c, String title, String sub, final String k, boolean def) {
        c.addView(U.switchRow(m, title, sub, cfg.optBoolean(k, def), new U.S() {
            public void on(String s) { set(k, s.equals("1")); }
        }), U.wrapLp(0, 8, 0, 0));
    }

    void chips(LinearLayout c, String label, final String k, final String[] names, final String[] vals, final boolean typeface) {
        HorizontalScrollView hs = new HorizontalScrollView(m);
        hs.setHorizontalScrollBarEnabled(false);
        final LinearLayout r = U.row(m);
        hs.addView(r);
        final String cur = cfg.optString(k, vals[0]);
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            final TextView ch = U.chip(m, names[i], vals[i].equals(cur));
            if (typeface) ch.setTypeface(Eren.font(m, vals[i]));
            ch.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    for (int j = 0; j < r.getChildCount(); j++) {
                        TextView t = (TextView) r.getChildAt(j);
                        boolean sel = j == idx;
                        t.setTextColor(sel ? 0xFFFFFFFF : U.TEXT);
                        t.setBackground(sel ? U.rr(U.PRIMARY, 50) : U.rrStroke(0xFFFFFFFF, 50, U.LINE, 1));
                    }
                    chosen(k, vals[idx], idx);
                }
            });
            r.addView(ch, U.lp(-2, -2, 0, 0, 8, 0));
        }
        c.addView(U.labeled(m, label, hs), U.wrapLp(0, 12, 0, 0));
    }

    /** Called after a chip is tapped. */
    void chosen(String k, String v, int idx) {
        if (k.equals("category")) { applyPreset(idx); return; }
        if (k.equals("heroRatio")) {
            String[] p = v.split(":");
            int pct = Math.round(100f * Float.parseFloat(p[1]) / Float.parseFloat(p[0]));
            U.put(cfg, "heroRatio", v);
            setSeek("heroH", pct);
            set("heroH", pct);
            ratioText();
            return;
        }
        set(k, v);
        if (k.startsWith("anim") || k.equals("press") || k.equals("attn")) {
            if (pending != null) U.MAIN.removeCallbacks(pending);
            U.MAIN.postDelayed(new Runnable() { public void run() { refresh(true); } }, 160);
        }
    }

    void setSeek(String k, int v) {
        SeekBar sb = seeks.get(k);
        if (sb == null) return;
        TextView t = (TextView) sb.getTag();
        int min = Integer.parseInt(t.getContentDescription().toString());
        sb.setProgress(v - min);
        t.setText(t.getHint() + ": " + v);
    }

    void slider(LinearLayout c, final String label, final String k, final int min, final int max, int def) {
        final TextView t = U.tv(m, label + ": " + cfg.optInt(k, def), 12.5f, U.SUB, true);
        t.setHint(label);
        t.setContentDescription(String.valueOf(min));
        SeekBar sb = new SeekBar(m);
        sb.setMax(max - min);
        sb.setProgress(cfg.optInt(k, def) - min);
        sb.getProgressDrawable().setTint(U.PRIMARY);
        sb.getThumb().setTint(U.PRIMARY);
        sb.setTag(t);
        seeks.put(k, sb);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                t.setText(label + ": " + (p + min));
                if (user) {
                    set(k, p + min);
                    if (k.equals("heroH")) ratioText();
                }
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        c.addView(t, U.lp(-2, -2, 4, 14, 0, 2));
        c.addView(sb, new LinearLayout.LayoutParams(-1, -2));
    }

    // =====================================================================
    //  MEDIA
    // =====================================================================
    void buildMedia(LinearLayout list) {
        LinearLayout c = sec(list, "Image / Video", "Top of the dialog. Paste a URL or pick from your gallery.");
        mediaField = U.input(m, "https://.../image.png or .mp4", mediaLabel(cfg.optString("media", "")));
        mediaField.addTextChangedListener(U.watch(new U.S() {
            public void on(String s) {
                if (mediaInternal || s.startsWith("[")) return;
                set("media", s.trim());
            }
        }));
        c.addView(U.labeled(m, "Media URL", mediaField), U.wrapLp(0, 12, 0, 0));
        chips(c, "Type", "mediaType", new String[]{"Image", "Video"}, new String[]{"image", "video"}, false);
        LinearLayout r = U.row(m);
        r.addView(U.btn(m, "\uD83D\uDDBC Gallery image", false, new View.OnClickListener() {
            public void onClick(View v) { m.pickMedia(Editor.this, 0); }
        }), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(U.btn(m, "\uD83C\uDFAC Gallery video", false, new View.OnClickListener() {
            public void onClick(View v) { m.pickMedia(Editor.this, 1); }
        }), U.lp(0, -2, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) r.getChildAt(1).getLayoutParams()).weight = 1f;
        c.addView(r, U.wrapLp(0, 12, 0, 0));
        c.addView(U.dangerBtn(m, "Remove media", new View.OnClickListener() {
            public void onClick(View v) { setMedia("", false); }
        }), U.wrapLp(0, 8, 0, 0));
        c.addView(U.tv(m, "Gallery files are uploaded to your database (image \u2264 ~400 KB after compression, video up to 6 MB). "
                + "Bigger videos: use a direct .mp4 link.", 11.5f, U.SUB, false), U.lp(-2, -2, 2, 8, 0, 0));
        chips(c, "Shape (sets height)", "heroRatio", new String[]{"16:9", "3:2", "1:1", "4:5", "9:16 portrait"},
                new String[]{"16:9", "3:2", "1:1", "4:5", "9:16"}, false);
        ratioText = U.tv(m, "", 12.5f, U.PRIMARY, true);
        c.addView(ratioText, U.lp(-2, -2, 4, 10, 0, 0));
        ratioText();
        toggle(c, "Soft fade at the bottom", "Blends the media into the card", "heroFade", true);
    }

    String mediaLabel(String v) {
        if (v.startsWith("rtdb:")) return "[uploaded to database]";
        if (v.startsWith("data:")) return "[gallery image \u2022 " + (v.length() / 1365) + " KB]";
        return v;
    }

    void ratioText() {
        if (ratioText == null) return;
        int pct = cfg.optInt("heroH", 52);
        int w = 1080;
        ratioText.setText("Recommended size: " + w + " \u00D7 " + Math.round(w * pct / 100f) + " px");
    }

    void setMedia(String v, boolean video) {
        mediaInternal = true;
        mediaField.setText(mediaLabel(v));
        mediaInternal = false;
        set("media", v);
        if (v.length() > 0) set("mediaType", video ? "video" : "image");
        if (v.length() > 0 && cfg.optBoolean("autoColor", false)) autoColors();
    }

    // ---- auto colours ----
    void buildAutoColor(LinearLayout list) {
        LinearLayout c = sec(list, "Auto colors", "Take colors from your image or video and style the whole dialog.");
        c.addView(U.btn(m, "\uD83C\uDFA8  Match colors to media", true, new View.OnClickListener() {
            public void onClick(View v) { autoColors(); }
        }), U.wrapLp(0, 12, 0, 0));
        HorizontalScrollView hs = new HorizontalScrollView(m);
        hs.setHorizontalScrollBarEnabled(false);
        swatchRow = U.row(m);
        hs.addView(swatchRow);
        c.addView(hs, U.wrapLp(0, 12, 0, 0));
        c.addView(U.tv(m, "Tap a swatch to try another main color.", 11.5f, U.SUB, false), U.lp(-2, -2, 2, 6, 0, 0));
        toggle(c, "Auto-match when media changes", "Runs after you pick a new image/video", "autoColor", false);
    }

    void autoColors() {
        String media = cfg.optString("media", "");
        if (media.length() == 0) { U.toast(m, "Add an image or video first"); return; }
        U.toast(m, "Reading colors\u2026");
        Eren.BitmapCb cb = new Eren.BitmapCb() {
            public void done(Bitmap b) {
                if (b == null) { U.toast(m, "Could not read the media"); return; }
                int[] cols = Pal.extract(b);
                showSwatches(cols);
                applyScheme(cols[0]);
            }
        };
        if ("video".equals(cfg.optString("mediaType"))) Eren.videoFrame(m, media, cb);
        else Eren.loadBitmap(m, media, 300, cb);
    }

    void showSwatches(final int[] cols) {
        swatchRow.removeAllViews();
        for (int i = 0; i < cols.length; i++) {
            final int c = cols[i];
            View v = new View(m);
            v.setBackground(U.rrStroke(c, 50, U.LINE, 1));
            v.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { applyScheme(c); } });
            swatchRow.addView(v, U.lp(U.dp(46), U.dp(46), 0, 0, 10, 0));
        }
    }

    void applyScheme(int c) {
        Pal.scheme(cfg, c);
        dirty = true;
        for (String k : COLOR_KEYS) syncColor(k);
        refreshSoon();
    }

    // =====================================================================
    //  SIZE / TEXT / FEATURES
    // =====================================================================
    void buildSize(LinearLayout list) {
        LinearLayout c = sec(list, "Dialog size", "Width, media height and overall scale.");
        slider(c, "Dialog width % of screen", "widthPct", 45, 100, 76);
        slider(c, "Media height % of width", "heroH", 15, 200, 52);
        slider(c, "Overall scale %", "uiScale", 60, 140, 100);
        slider(c, "Corner radius", "radius", 0, 60, 31);
    }

    void buildText(LinearLayout list) {
        LinearLayout c = sec(list, "Title & name", "Small title, big colored name and optional sub line.");
        field(c, "Small title", "small", "UPDATE");
        field(c, "Name - first part", "brand1", "MOD");
        field(c, "Name - colored part", "brand2", "MASE");
        field(c, "Sub line (optional)", "subtitle", "Version 2.0 \u2022 12 MB");
        slider(c, "Name size", "brandSize", 24, 90, 56);
    }

    void buildFeatures(LinearLayout list) {
        LinearLayout c = sec(list, "Feature lines", "Add, edit or remove the bullet lines.");
        featBox = U.col(m);
        c.addView(featBox);
        drawFeatures();
        c.addView(U.btn(m, "\uFF0B  Add line", false, new View.OnClickListener() {
            public void onClick(View v) {
                JSONArray a = cfg.optJSONArray("features");
                if (a == null) a = new JSONArray();
                a.put("New line");
                set("features", a);
                drawFeatures();
            }
        }), U.wrapLp(0, 10, 0, 0));
        chips(c, "Bullet symbol", "symbol", new String[]{"\u2756", "\u2726", "\u2714", "\u2605", "\u27A4", "\u2022", "\u25C6"},
                new String[]{"\u2756", "\u2726", "\u2714", "\u2605", "\u27A4", "\u2022", "\u25C6"}, false);
    }

    void drawFeatures() {
        featBox.removeAllViews();
        final JSONArray a = cfg.optJSONArray("features") == null ? new JSONArray() : cfg.optJSONArray("features");
        for (int i = 0; i < a.length(); i++) {
            final int idx = i;
            LinearLayout r = U.row(m);
            EditText e = U.input(m, "Feature", a.optString(i));
            e.addTextChangedListener(U.watch(new U.S() {
                public void on(String s) { try { a.put(idx, s); } catch (Throwable t) { } set("features", a); }
            }));
            r.addView(e, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView x = U.tv(m, "\u2715", 18, U.BAD, true);
            x.setPadding(U.dp(14), U.dp(8), U.dp(8), U.dp(8));
            x.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    JSONArray n = new JSONArray();
                    for (int j = 0; j < a.length(); j++) if (j != idx) n.put(a.optString(j));
                    set("features", n);
                    drawFeatures();
                }
            });
            r.addView(x);
            featBox.addView(r, U.lp(-1, -2, 0, 10, 0, 0));
        }
    }

    // =====================================================================
    //  BUTTONS
    // =====================================================================
    void buildButtons(LinearLayout list) {
        LinearLayout c = sec(list, "Buttons", "Texts, link, size and shape.");
        field(c, "Exit button text", "exitText", "EXIT");
        field(c, "Update button text", "updateText", "UPDATE");
        field(c, "Update URL (opens on UPDATE)", "updateUrl", "https://.../app.apk");
        toggle(c, "Show EXIT button", "Off = force update (only UPDATE)", "showExit", true);
        chips(c, "Layout", "btnLayout", new String[]{"Side by side", "Stacked"}, new String[]{"row", "column"}, false);
        slider(c, "Height", "btnH", 32, 90, 55);
        slider(c, "Width % of card", "btnW", 40, 100, 100);
        slider(c, "Corner radius", "btnRadius", 0, 45, 19);
        slider(c, "Border thickness", "btnBorder", 0, 9, 3);
        slider(c, "Gap between buttons", "btnGap", 0, 40, 12);
        slider(c, "Text size", "btnSize", 10, 30, 17);
        toggle(c, "Button shadow", null, "btnShadow", false);
    }

    // =====================================================================
    //  COLORS
    // =====================================================================
    void buildColors(LinearLayout list) {
        LinearLayout c = sec(list, "Colors", "Preset by category, then fine-tune with HEX or palette.");
        chips(c, "Category preset", "category", PRESET_NAMES, PRESET_NAMES, false);
        colorRow(c, "Page backdrop", "backdrop");
        colorRow(c, "Dialog card", "card");
        colorRow(c, "Name - first part", "text");
        colorRow(c, "Accent (name 2nd part)", "accent");
        colorRow(c, "Update button", "updateBg");
        colorRow(c, "Update button text", "btnText");
        colorRow(c, "Exit button", "exitBg");
        colorRow(c, "Exit button text", "exitFg");
        colorRow(c, "Border (both)", "border");
        colorRow(c, "Exit border", "exitBorder");
        colorRow(c, "Update border", "updateBorder");
        colorRow(c, "Feature text", "featureText");
        colorRow(c, "Small title", "smallColor");
        colorRow(c, "Sub line", "subColor");
        slider(c, "Backdrop opacity %", "dim", 0, 100, 100);
    }

    void applyPreset(int i) {
        String[] p = PRESETS[i];
        for (int k = 0; k < PKEYS.length; k++) U.put(cfg, PKEYS[k], p[k]);
        U.put(cfg, "updateBg", p[3]);
        U.put(cfg, "exitBorder", p[7]);
        U.put(cfg, "updateBorder", p[7]);
        U.put(cfg, "category", PRESET_NAMES[i]);
        dirty = true;
        for (String k : COLOR_KEYS) syncColor(k);
        refreshSoon();
    }

    void syncColor(String k) {
        EditText e = hexFields.get(k);
        View sw = swatches.get(k);
        if (e != null) e.setText(cfg.optString(k));
        if (sw != null) sw.setBackground(U.circle(Eren.col(cfg.optString(k), Color.GRAY)));
    }

    void colorRow(LinearLayout c, String label, final String k) {
        LinearLayout r = U.row(m);
        final View sw = new View(m);
        sw.setBackground(U.circle(Eren.col(cfg.optString(k, "#888888"), Color.GRAY)));
        r.addView(sw, new LinearLayout.LayoutParams(U.dp(38), U.dp(38)));
        r.addView(U.tv(m, label, 14, U.TEXT, false), U.lp(0, -2, 12, 0, 8, 0));
        ((LinearLayout.LayoutParams) r.getChildAt(1).getLayoutParams()).weight = 1f;
        final EditText e = U.input(m, "#RRGGBB", cfg.optString(k, ""));
        e.setTextSize(13);
        e.setPadding(U.dp(12), U.dp(9), U.dp(12), U.dp(9));
        e.addTextChangedListener(U.watch(new U.S() {
            public void on(String s) {
                String t = s.trim();
                if (!t.startsWith("#")) t = "#" + t;
                try {
                    int col = Color.parseColor(t);
                    U.put(cfg, k, t);
                    dirty = true;
                    sw.setBackground(U.circle(col));
                    refreshSoon();
                } catch (Throwable x) { }
            }
        }));
        r.addView(e, new LinearLayout.LayoutParams(U.dp(112), -2));
        hexFields.put(k, e);
        swatches.put(k, sw);
        sw.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { palette(k); } });
        c.addView(r, U.wrapLp(0, 10, 0, 0));
    }

    void palette(final String k) {
        final Dialog d = new Dialog(m);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = U.col(m);
        box.setBackground(U.rr(U.SURF, 24));
        box.setPadding(U.dp(18), U.dp(16), U.dp(18), U.dp(16));
        box.addView(U.tv(m, "Choose color", 16, U.TEXT, true));
        LinearLayout row = null;
        for (int i = 0; i < SWATCH.length; i++) {
            if (i % 6 == 0) {
                row = U.row(m);
                box.addView(row, U.wrapLp(0, 10, 0, 0));
            }
            final String col = SWATCH[i];
            View v = new View(m);
            v.setBackground(U.rrStroke(Eren.col(col, Color.GRAY), 50, U.LINE, 1));
            v.setOnClickListener(new View.OnClickListener() {
                public void onClick(View x) { U.put(cfg, k, col); dirty = true; syncColor(k); refreshSoon(); d.dismiss(); }
            });
            row.addView(v, U.lp(U.dp(42), U.dp(42), 0, 0, 8, 0));
        }
        d.setContentView(box);
        d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        d.show();
    }

    // =====================================================================
    //  FONTS
    // =====================================================================
    void buildStyle(LinearLayout list) {
        LinearLayout c = sec(list, "Fonts", "Fonts are bundled inside the app (assets/fonts).");
        chips(c, "Small title font", "fontSmall", FONT_NAMES, FONT_FILES, true);
        chips(c, "Name font", "fontBrand", FONT_NAMES, FONT_FILES, true);
        chips(c, "Feature font", "fontFeature", FONT_NAMES, FONT_FILES, true);
        chips(c, "Button font", "fontButton", FONT_NAMES, FONT_FILES, true);
        slider(c, "Small title size", "smallSize", 8, 30, 16);
        slider(c, "Feature text size", "featureSize", 9, 28, 15);
    }

    // =====================================================================
    //  ANIMATION
    // =====================================================================
    void buildAnim(LinearLayout list) {
        LinearLayout c = sec(list, "Animation", "21 entrance effects with full control. Tap \u25B6 Play to preview.");
        c.addView(U.switchRow(m, "Animations", "Master switch for everything below", cfg.optBoolean("animOn", true), new U.S() {
            public void on(String s) { set("animOn", s.equals("1")); }
        }), U.wrapLp(0, 8, 0, 0));
        chips(c, "Entrance effect", "animIn", ANIM_IN_N, ANIM_IN, false);
        slider(c, "Duration (ms)", "animDur", 100, 2500, 420);
        slider(c, "Start delay (ms)", "animDelay", 0, 2000, 0);
        slider(c, "Intensity %", "animPower", 10, 200, 60);
        chips(c, "Easing", "animEase", new String[]{"Auto", "Smooth", "Fast-slow", "Overshoot", "Bounce", "Elastic", "Anticipate", "Linear", "Speed up"},
                new String[]{"auto", "decelerate", "fastslow", "overshoot", "bounce", "elastic", "anticipate", "linear", "accelerate"}, false);
        toggle(c, "Stagger content", "Title, name, list, buttons appear one by one", "animStagger", true);
        slider(c, "Stagger gap (ms)", "animStaggerMs", 0, 400, 70);
        chips(c, "Exit effect", "animOut", new String[]{"None", "Fade + zoom", "Slide down", "Slide up", "Spin"},
                new String[]{"none", "zoom", "slide_down", "slide_up", "spin"}, false);
        chips(c, "Button press", "press", new String[]{"Scale", "Bounce", "None"}, new String[]{"scale", "bounce", "none"}, false);
        chips(c, "Update button attention", "attn", new String[]{"None", "Pulse", "Shake", "Wobble", "Float", "Glow"},
                new String[]{"none", "pulse", "shake", "wobble", "float", "glow"}, false);
        slider(c, "Attention speed (ms)", "attnMs", 300, 4000, 1200);
    }

    // =====================================================================
    //  BEHAVIOUR / TOOLS
    // =====================================================================
    void buildBehavior(LinearLayout list) {
        LinearLayout c = sec(list, "Behavior", "When and how the dialog appears.");
        chips(c, "Show", "showMode", new String[]{"Every launch", "Once per day", "Once per save"}, new String[]{"always", "daily", "once"}, false);
        chips(c, "Exit does", "exitAction", new String[]{"Close app", "Just dismiss"}, new String[]{"close", "dismiss"}, false);
        toggle(c, "Close (\u2715) button on top", "Lets users dismiss without exiting", "showClose", false);
        toggle(c, "Back button closes dialog", "Off = user must tap a button", "cancelable", false);
        EditText e = U.input(m, "0 = show to everyone", String.valueOf(cfg.optInt("minVersion", 0)));
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        e.addTextChangedListener(U.watch(new U.S() {
            public void on(String s) { int v = 0; try { v = Integer.parseInt(s.trim()); } catch (Throwable t) { } set("minVersion", v); }
        }));
        c.addView(U.labeled(m, "Only if app versionCode is below", e), U.wrapLp(0, 12, 0, 0));
    }

    void buildTools(LinearLayout list) {
        LinearLayout c = sec(list, "Tools", "Backup and reuse.");
        LinearLayout r = U.row(m);
        r.addView(U.btn(m, "Copy JSON", false, new View.OnClickListener() {
            public void onClick(View v) { U.copy(m, cfg.toString(), "JSON"); }
        }), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(U.btn(m, "Paste JSON", false, new View.OnClickListener() {
            public void onClick(View v) { pasteJson(); }
        }), U.lp(0, -2, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) r.getChildAt(1).getLayoutParams()).weight = 1f;
        c.addView(r, U.wrapLp(0, 12, 0, 0));
        c.addView(U.btn(m, "Copy dialog from another app", false, new View.OnClickListener() {
            public void onClick(View v) { copyFrom(); }
        }), U.wrapLp(0, 8, 0, 0));
    }

    void copyFrom() {
        final java.util.ArrayList<String> ks = new java.util.ArrayList<String>();
        java.util.ArrayList<String> names = new java.util.ArrayList<String>();
        java.util.Iterator<String> it = m.apps.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (k.equals(key)) continue;
            JSONObject a = m.apps.optJSONObject(k);
            if (a == null) continue;
            ks.add(k);
            names.add(a.optString("name", k));
        }
        if (ks.isEmpty()) { U.toast(m, "No other apps"); return; }
        m.chooser("Copy dialog from\u2026", names.toArray(new String[0]), new U.S() {
            public void on(String s) {
                int i = Integer.parseInt(s);
                JSONObject a = m.apps.optJSONObject(ks.get(i));
                m.openEditor(key, a == null ? null : a.optJSONObject("dialog"), true);
            }
        });
    }

    void pasteJson() {
        final EditText e = U.input(m, "{ ... }", "");
        e.setSingleLine(false);
        e.setMinLines(5);
        e.setGravity(Gravity.TOP);
        m.sheet("Paste dialog JSON", e, "Import", new Runnable() {
            public void run() {
                try {
                    JSONObject o = new JSONObject(e.getText().toString());
                    m.openEditor(key, o, true);
                } catch (Throwable t) { U.toast(m, "Invalid JSON"); }
            }
        });
    }
}
