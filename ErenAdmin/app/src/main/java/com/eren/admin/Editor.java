package com.eren.admin;

import android.app.Dialog;
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

    static JSONObject defaults() {
        JSONObject o = new JSONObject();
        U.put(o, "media", "");
        U.put(o, "mediaType", "image");
        U.put(o, "heroRatio", "1200:675");
        U.put(o, "small", "UPDATE");
        U.put(o, "brand1", "MOD");
        U.put(o, "brand2", "MASE");
        U.put(o, "symbol", "\u2756");
        JSONArray f = new JSONArray();
        f.put("New Features Available");
        f.put("Previous Bug Fixed");
        U.put(o, "features", f);
        U.put(o, "exitText", "EXIT");
        U.put(o, "updateText", "UPDATE");
        U.put(o, "updateUrl", "");
        U.put(o, "exitAction", "close");
        U.put(o, "cancelable", false);
        U.put(o, "dim", 100);
        U.put(o, "radius", 31);
        U.put(o, "brandSize", 56);
        U.put(o, "minVersion", 0);
        U.put(o, "fontSmall", "Audiowide-Regular");
        U.put(o, "fontBrand", "Oswald-Variable");
        U.put(o, "fontFeature", "Poppins-Bold");
        U.put(o, "fontButton", "Audiowide-Regular");
        for (int i = 0; i < PKEYS.length; i++) U.put(o, PKEYS[i], PRESETS[0][i]);
        U.put(o, "updateBg", PRESETS[0][3]);
        U.put(o, "category", "Default");
        return o;
    }

    final MainActivity m;
    final String key;
    final JSONObject cfg;
    boolean dirty = false;
    FrameLayout preview;
    Runnable pending;
    LinearLayout featBox;
    final String original;
    final java.util.HashMap<String, EditText> hexFields = new java.util.HashMap<String, EditText>();
    final java.util.HashMap<String, View> swatches = new java.util.HashMap<String, View>();
    EditText mediaField;
    boolean mediaInternal = false;
    LinearLayout ratioHint;
    TextView ratioText;

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
        original = cfg.toString();
    }

    void set(String k, Object v) {
        U.put(cfg, k, v);
        dirty = true;
        refreshSoon();
    }

    void refreshSoon() {
        if (pending != null) U.MAIN.removeCallbacks(pending);
        pending = new Runnable() { public void run() { refresh(); } };
        U.MAIN.postDelayed(pending, 140);
    }

    void refresh() {
        if (preview == null) return;
        preview.removeAllViews();
        Eren.noAnim = true;
        View v = Eren.buildDialogView(m, cfg, null);
        Eren.noAnim = false;
        int sw = m.getResources().getDisplayMetrics().widthPixels;
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(sw, U.dp(640));
        v.setPivotX(0);
        v.setPivotY(0);
        float s = scale();
        v.setScaleX(s);
        v.setScaleY(s);
        preview.addView(v, p);
    }

    float scale() { return 0.5f; }

    // =====================================================================
    View build() {
        final android.content.Context c = m;
        LinearLayout page = U.col(c);

        // top bar
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
        TextView full = U.btn(c, "Test", false, new View.OnClickListener() {
            public void onClick(View v) { fullTest(); }
        });
        full.setPadding(U.dp(16), U.dp(8), U.dp(16), U.dp(8));
        bar.addView(full);
        page.addView(bar);

        // preview
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

        // controls
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout list = U.col(c);
        list.setPadding(U.dp(16), U.dp(4), U.dp(16), U.dp(24));
        sv.addView(list);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        buildMedia(list);
        buildText(list);
        buildFeatures(list);
        buildButtons(list);
        buildColors(list);
        buildStyle(list);
        buildTarget(list);
        buildTools(list);

        // bottom save bar
        LinearLayout sb = U.row(c);
        sb.setBackgroundColor(U.SURF);
        sb.setElevation(U.dp(8));
        sb.setPadding(U.dp(16), U.dp(10), U.dp(16), U.dp(10));
        TextView reset = U.btn(c, "Reset", false, new View.OnClickListener() {
            public void onClick(View v) {
                m.confirm("Reset dialog?", "All fields go back to the default MODMASE look.", "Reset", new Runnable() {
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

        refresh();
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
            public void onExit() { }
            public void onUpdate(String url) { }
        });
        d.setContentView(v);
        d.setCancelable(true);
        d.setOnCancelListener(null);
        v.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View x) { d.dismiss(); return true; }
        });
        d.show();
        U.toast(m, "Test mode - press Back to close");
    }

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
                    set(k, vals[idx]);
                    for (int j = 0; j < r.getChildCount(); j++) {
                        TextView t = (TextView) r.getChildAt(j);
                        boolean sel = j == idx;
                        t.setTextColor(sel ? 0xFFFFFFFF : U.TEXT);
                        t.setBackground(sel ? U.rr(U.PRIMARY, 50) : U.rrStroke(0xFFFFFFFF, 50, U.LINE, 1));
                    }
                    if (k.equals("heroRatio")) updateRatioHint();
                }
            });
            r.addView(ch, U.lp(-2, -2, 0, 0, 8, 0));
        }
        c.addView(U.labeled(m, label, hs), U.wrapLp(0, 12, 0, 0));
    }

    void slider(LinearLayout c, final String label, final String k, final int min, final int max, int def) {
        final TextView t = U.tv(m, label + ": " + cfg.optInt(k, def), 12.5f, U.SUB, true);
        SeekBar sb = new SeekBar(m);
        sb.setMax(max - min);
        sb.setProgress(cfg.optInt(k, def) - min);
        sb.getProgressDrawable().setTint(U.PRIMARY);
        sb.getThumb().setTint(U.PRIMARY);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                t.setText(label + ": " + (p + min));
                if (user) set(k, p + min);
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        c.addView(t, U.lp(-2, -2, 4, 14, 0, 2));
        c.addView(sb, new LinearLayout.LayoutParams(-1, -2));
    }

    // ---- media ----
    void buildMedia(LinearLayout list) {
        LinearLayout c = sec(list, "Image / Video", "Top of the dialog. Paste a URL or pick from gallery.");
        String cur = cfg.optString("media", "");
        mediaField = U.input(m, "https://.../image.png or .mp4", cur.startsWith("data:") ? galleryLabel(cur) : cur);
        mediaField.addTextChangedListener(U.watch(new U.S() {
            public void on(String s) {
                if (mediaInternal) return;
                if (s.startsWith("[gallery")) return;
                set("media", s.trim());
            }
        }));
        c.addView(U.labeled(m, "Media URL", mediaField), U.wrapLp(0, 12, 0, 0));
        chips(c, "Type", "mediaType", new String[]{"Image", "Video (URL)"}, new String[]{"image", "video"}, false);
        chips(c, "Shape", "heroRatio", new String[]{"16:9", "1:1", "4:3", "4:5", "9:16 portrait"},
                new String[]{"1200:675", "1000:1000", "1200:900", "1080:1350", "1080:1920"}, false);
        ratioText = U.tv(m, "", 12.5f, U.PRIMARY, true);
        c.addView(ratioText, U.lp(-2, -2, 4, 10, 0, 0));
        updateRatioHint();
        LinearLayout r = U.row(m);
        r.addView(U.btn(m, "\uD83D\uDDBC  Gallery image", false, new View.OnClickListener() {
            public void onClick(View v) { m.pickImage(Editor.this); }
        }), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(U.dangerBtn(m, "Clear", new View.OnClickListener() {
            public void onClick(View v) { setMedia(""); }
        }), U.lp(-2, -2, 8, 0, 0, 0));
        c.addView(r, U.wrapLp(0, 12, 0, 0));
        c.addView(U.tv(m, "Gallery images are compressed and stored inside your database. Video must be a direct link (.mp4).",
                11.5f, U.SUB, false), U.lp(-2, -2, 2, 8, 0, 0));
    }

    String galleryLabel(String d) { return "[gallery image \u2022 " + (d.length() / 1365) + " KB]"; }

    void updateRatioHint() {
        String[] p = cfg.optString("heroRatio", "1200:675").split(":");
        ratioText.setText("Recommended size: " + p[0] + " \u00D7 " + p[1] + " px  (width \u00D7 height)");
    }

    void setMedia(String v) {
        mediaInternal = true;
        mediaField.setText(v.startsWith("data:") ? galleryLabel(v) : v);
        mediaInternal = false;
        set("media", v);
        if (v.startsWith("data:")) set("mediaType", "image");
    }

    // ---- text ----
    void buildText(LinearLayout list) {
        LinearLayout c = sec(list, "Title & name", "Small title and the big colored name.");
        field(c, "Small title", "small", "UPDATE");
        field(c, "Name - first part", "brand1", "MOD");
        field(c, "Name - colored part", "brand2", "MASE");
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

    // ---- buttons ----
    void buildButtons(LinearLayout list) {
        LinearLayout c = sec(list, "Buttons", "Texts, link and behaviour.");
        field(c, "Exit button text", "exitText", "EXIT");
        field(c, "Update button text", "updateText", "UPDATE");
        field(c, "Update URL (opens on UPDATE)", "updateUrl", "https://.../app.apk");
        chips(c, "Exit does", "exitAction", new String[]{"Close app", "Just dismiss"}, new String[]{"close", "dismiss"}, false);
        c.addView(U.switchRow(m, "Back button closes dialog", "Off = user must tap a button", cfg.optBoolean("cancelable", false),
                new U.S() { public void on(String s) { set("cancelable", s.equals("1")); } }), U.wrapLp(0, 10, 0, 0));
    }

    // ---- colors ----
    void buildColors(LinearLayout list) {
        LinearLayout c = sec(list, "Colors", "Pick a category preset, then fine-tune with HEX or palette.");
        chips(c, "Category preset", "category", PRESET_NAMES, PRESET_NAMES, false);
        // chips() only stores category; hook preset apply by wrapping row clicks
        HorizontalScrollView hs = (HorizontalScrollView) ((LinearLayout) c.getChildAt(c.getChildCount() - 1)).getChildAt(1);
        final LinearLayout row = (LinearLayout) hs.getChildAt(0);
        for (int i = 0; i < row.getChildCount(); i++) {
            final int idx = i;
            final View.OnClickListener base = null;
            final View ch = row.getChildAt(i);
            ch.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    applyPreset(idx);
                    for (int j = 0; j < row.getChildCount(); j++) {
                        TextView t = (TextView) row.getChildAt(j);
                        boolean sel = j == idx;
                        t.setTextColor(sel ? 0xFFFFFFFF : U.TEXT);
                        t.setBackground(sel ? U.rr(U.PRIMARY, 50) : U.rrStroke(0xFFFFFFFF, 50, U.LINE, 1));
                    }
                }
            });
        }
        colorRow(c, "Page backdrop", "backdrop");
        colorRow(c, "Dialog card", "card");
        colorRow(c, "Name - first part", "text");
        colorRow(c, "Accent (name 2nd part)", "accent");
        colorRow(c, "Update button", "updateBg");
        colorRow(c, "Update button text", "btnText");
        colorRow(c, "Exit button", "exitBg");
        colorRow(c, "Exit button text", "exitFg");
        colorRow(c, "Button border", "border");
        colorRow(c, "Feature text", "featureText");
        colorRow(c, "Small title", "smallColor");
        slider(c, "Backdrop opacity %", "dim", 0, 100, 100);
    }

    void applyPreset(int i) {
        String[] p = PRESETS[i];
        for (int k = 0; k < PKEYS.length; k++) U.put(cfg, PKEYS[k], p[k]);
        U.put(cfg, "updateBg", p[3]);
        U.put(cfg, "category", PRESET_NAMES[i]);
        dirty = true;
        String[] all = {"backdrop", "card", "text", "accent", "updateBg", "btnText", "exitBg", "exitFg", "border", "featureText", "smallColor"};
        for (String k : all) syncColor(k);
        refreshSoon();
    }

    void syncColor(String k) {
        EditText e = hexFields.get(k);
        View sw = swatches.get(k);
        if (e != null) { e.setText(cfg.optString(k)); }
        if (sw != null) sw.setBackground(U.circle(Eren.col(cfg.optString(k), Color.GRAY)));
    }

    void colorRow(LinearLayout c, String label, final String k) {
        LinearLayout r = U.row(m);
        final View sw = new View(m);
        sw.setBackground(U.circle(Eren.col(cfg.optString(k, "#888888"), Color.GRAY)));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(U.dp(38), U.dp(38));
        r.addView(sw, sp);
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
        sw.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { palette(k); }
        });
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

    // ---- style ----
    void buildStyle(LinearLayout list) {
        LinearLayout c = sec(list, "Fonts & shape", "Fonts are bundled inside the app (assets/fonts).");
        chips(c, "Small title font", "fontSmall", FONT_NAMES, FONT_FILES, true);
        chips(c, "Name font", "fontBrand", FONT_NAMES, FONT_FILES, true);
        chips(c, "Feature font", "fontFeature", FONT_NAMES, FONT_FILES, true);
        chips(c, "Button font", "fontButton", FONT_NAMES, FONT_FILES, true);
        slider(c, "Name size", "brandSize", 28, 80, 56);
        slider(c, "Corner radius", "radius", 0, 48, 31);
    }

    void buildTarget(LinearLayout list) {
        LinearLayout c = sec(list, "Targeting", "Show only to older versions of the app.");
        EditText e = U.input(m, "0 = show to everyone", String.valueOf(cfg.optInt("minVersion", 0)));
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        e.addTextChangedListener(U.watch(new U.S() {
            public void on(String s) { int v = 0; try { v = Integer.parseInt(s.trim()); } catch (Throwable t) { } set("minVersion", v); }
        }));
        c.addView(U.labeled(m, "Show if app versionCode is below", e), U.wrapLp(0, 12, 0, 0));
    }

    void buildTools(LinearLayout list) {
        LinearLayout c = sec(list, "Backup", "Copy this dialog as JSON or paste one.");
        LinearLayout r = U.row(m);
        r.addView(U.btn(m, "Copy JSON", false, new View.OnClickListener() {
            public void onClick(View v) { U.copy(m, cfg.toString(), "JSON"); }
        }), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(U.btn(m, "Paste JSON", false, new View.OnClickListener() {
            public void onClick(View v) { pasteJson(); }
        }), U.lp(0, -2, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) r.getChildAt(1).getLayoutParams()).weight = 1f;
        c.addView(r, U.wrapLp(0, 12, 0, 0));
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
