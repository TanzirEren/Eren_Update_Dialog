package com.eren.admin;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

public class MainActivity extends Activity {

    static final String[] ACCENTS = {"#4F6AF5", "#7C4DFF", "#00A68C", "#F5703A", "#E91E63", "#1A1C28"};
    static final String PERM = "<uses-permission android:name=\"android.permission.INTERNET\"/>";
    static final int PICK = 77;

    SharedPreferences sp;
    FrameLayout top, body, busyView;
    LinearLayout nav;
    JSONObject apps = new JSONObject();
    int tab = 0;
    Runnable back;
    String adminHash = "";
    long ping = -1;
    String query = "";
    int sort = 0;
    Editor pendingEditor;

    // =====================================================================
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("eren", 0);
        U.init(this);
        U.anim = sp.getBoolean("anim", true);
        U.PRIMARY = Color.parseColor(sp.getString("accent", ACCENTS[0]));

        top = new FrameLayout(this);
        top.setBackgroundColor(U.BG);
        top.setFitsSystemWindows(true);
        LinearLayout col = U.col(this);
        body = new FrameLayout(this);
        col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        nav = U.row(this);
        nav.setBackgroundColor(U.SURF);
        nav.setElevation(U.dp(10));
        nav.setPadding(U.dp(8), U.dp(8), U.dp(8), U.dp(8));
        col.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        top.addView(col, new FrameLayout.LayoutParams(-1, -1));

        busyView = new FrameLayout(this);
        busyView.setBackgroundColor(0x66000000);
        busyView.setClickable(true);
        TextView bt = U.tv(this, "Working\u2026", 15, U.TEXT, true);
        bt.setBackground(U.rr(U.SURF, 20));
        bt.setPadding(U.dp(26), U.dp(16), U.dp(26), U.dp(16));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2);
        bp.gravity = Gravity.CENTER;
        busyView.addView(bt, bp);
        busyView.setVisibility(View.GONE);
        top.addView(busyView, new FrameLayout.LayoutParams(-1, -1));

        setContentView(top);
        getWindow().setStatusBarColor(U.BG);
        getWindow().setNavigationBarColor(U.SURF);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        String db = sp.getString("db", "");
        if (db.length() == 0) connectScreen();
        else {
            Db.base = db;
            String stay = sp.getString("stay", "");
            if (stay.length() > 0) { adminHash = stay; enter(); }
            else loginScreen();
        }
    }

    @Override
    public void onBackPressed() {
        if (back != null) { Runnable r = back; r.run(); return; }
        if (nav.getVisibility() == View.VISIBLE && tab != 0) { tab(0); return; }
        finish();
    }

    void busy(boolean b) { busyView.setVisibility(b ? View.VISIBLE : View.GONE); }

    void setScreen(View v, boolean showNav, Runnable backAction) {
        back = backAction;
        body.removeAllViews();
        body.addView(v, new FrameLayout.LayoutParams(-1, -1));
        nav.setVisibility(showNav ? View.VISIBLE : View.GONE);
        if (U.anim) {
            v.setAlpha(0f);
            v.animate().alpha(1f).setDuration(220).start();
        }
    }

    ScrollView scroll(View content) {
        ScrollView s = new ScrollView(this);
        s.setVerticalScrollBarEnabled(false);
        s.addView(content, new FrameLayout.LayoutParams(-1, -2));
        return s;
    }

    // =====================================================================
    //  AUTH FLOW
    // =====================================================================
    View logo(int size) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(getResources().getIdentifier("ic_launcher", "mipmap", getPackageName()));
        iv.setLayoutParams(new LinearLayout.LayoutParams(U.dp(size), U.dp(size)));
        return iv;
    }

    LinearLayout authCard(String title, String sub) {
        LinearLayout c = U.col(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(U.dp(24), U.dp(48), U.dp(24), U.dp(24));
        c.addView(logo(84));
        c.addView(U.tv(this, title, 26, U.TEXT, true), U.lp(-2, -2, 0, 18, 0, 4));
        TextView s = U.tv(this, sub, 14, U.SUB, false);
        s.setGravity(Gravity.CENTER);
        c.addView(s, U.lp(-2, -2, 0, 0, 0, 22));
        return c;
    }

    void connectScreen() {
        LinearLayout c = authCard("Eren Admin", "Connect your Firebase Realtime Database");
        LinearLayout card = U.card(this);
        final EditText url = U.input(this, "https://xxxx-default-rtdb.firebaseio.com", sp.getString("db", ""));
        url.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        card.addView(U.labeled(this, "databaseURL", url));
        final TextView err = U.tv(this, "", 12.5f, U.BAD, false);
        card.addView(err, U.lp(-2, -2, 4, 8, 0, 0));
        card.addView(U.btn(this, "Connect", true, new View.OnClickListener() {
            public void onClick(View v) {
                String u = Db.normalize(url.getText().toString());
                if (u.length() == 0) { err.setText("Enter your databaseURL"); U.shake(url); return; }
                Db.base = u;
                busy(true);
                Db.get("/eren/admin", new Db.CB() {
                    public void done(boolean ok, String body) {
                        busy(false);
                        if (!ok) { err.setText("Cannot connect: " + body + "\nCheck URL and database rules (see Guide)."); return; }
                        sp.edit().putString("db", Db.base).apply();
                        JSONObject a = Db.obj(body);
                        if (a == null || a.optString("keyHash").length() == 0) createKeyScreen();
                        else loginScreen();
                    }
                });
            }
        }), U.wrapLp(0, 14, 0, 0));
        c.addView(card, U.wrapLp(0, 0, 0, 0));
        TextView hint = U.tv(this, "Firebase Console \u2192 Realtime Database \u2192 copy the URL shown at the top.", 12.5f, U.SUB, false);
        hint.setGravity(Gravity.CENTER);
        c.addView(hint, U.lp(-2, -2, 8, 16, 8, 0));
        setScreen(scroll(c), false, null);
        U.stagger(c);
    }

    void createKeyScreen() {
        LinearLayout c = authCard("Create access key", "This is your private key for the admin panel.\nKeep it safe - it cannot be recovered.");
        LinearLayout card = U.card(this);
        final EditText[] a = new EditText[1], b2 = new EditText[1];
        card.addView(U.labeled(this, "New access key", U.pass(this, "At least 6 characters", a)));
        card.addView(U.labeled(this, "Confirm key", U.pass(this, "Repeat key", b2)), U.wrapLp(0, 12, 0, 0));
        final TextView err = U.tv(this, "", 12.5f, U.BAD, false);
        card.addView(err, U.lp(-2, -2, 4, 8, 0, 0));
        card.addView(U.btn(this, "Create & continue", true, new View.OnClickListener() {
            public void onClick(View v) {
                String k = a[0].getText().toString();
                if (k.length() < 6) { err.setText("Key must be at least 6 characters"); U.shake(card0(v)); return; }
                if (!k.equals(b2[0].getText().toString())) { err.setText("Keys do not match"); return; }
                final String h = Db.sha(k);
                JSONObject o = new JSONObject();
                U.put(o, "keyHash", h);
                U.put(o, "created", System.currentTimeMillis());
                busy(true);
                Db.put("/eren/admin", o.toString(), new Db.CB() {
                    public void done(boolean ok, String body) {
                        busy(false);
                        if (!ok) { err.setText("Failed: " + body); return; }
                        adminHash = h;
                        enter();
                    }
                });
            }
        }), U.wrapLp(0, 14, 0, 0));
        c.addView(card);
        setScreen(scroll(c), false, new Runnable() { public void run() { connectScreen(); } });
        U.stagger(c);
    }

    View card0(View v) { return (View) v.getParent(); }

    void loginScreen() {
        LinearLayout c = authCard("Welcome back", "Enter your admin access key");
        LinearLayout card = U.card(this);
        final EditText[] a = new EditText[1];
        card.addView(U.labeled(this, "Access key", U.pass(this, "Your access key", a)));
        final TextView err = U.tv(this, "", 12.5f, U.BAD, false);
        card.addView(err, U.lp(-2, -2, 4, 8, 0, 0));
        card.addView(U.btn(this, "Unlock", true, new View.OnClickListener() {
            public void onClick(final View v) {
                final String h = Db.sha(a[0].getText().toString());
                busy(true);
                Db.get("/eren/admin/keyHash", new Db.CB() {
                    public void done(boolean ok, String body) {
                        busy(false);
                        if (!ok) { err.setText("Cannot reach database: " + body); return; }
                        String real = body.replace("\"", "").trim();
                        if (real.equals("null")) { createKeyScreen(); return; }
                        if (!real.equals(h)) { err.setText("Wrong access key"); U.shake(card0(v)); return; }
                        adminHash = h;
                        if (sp.getBoolean("stayOn", false)) sp.edit().putString("stay", h).apply();
                        enter();
                    }
                });
            }
        }), U.wrapLp(0, 14, 0, 0));
        c.addView(card);
        TextView other = U.tv(this, "Use a different database", 13, U.PRIMARY, true);
        other.setPadding(U.dp(12), U.dp(12), U.dp(12), U.dp(12));
        other.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { connectScreen(); } });
        c.addView(other, U.lp(-2, -2, 0, 10, 0, 0));
        setScreen(scroll(c), false, null);
        U.stagger(c);
    }

    void enter() {
        buildNav();
        busy(true);
        loadApps(new Runnable() { public void run() { busy(false); tab(0); } });
    }

    void loadApps(final Runnable after) {
        final long t0 = System.currentTimeMillis();
        Db.get("/eren/apps", new Db.CB() {
            public void done(boolean ok, String body) {
                ping = ok ? System.currentTimeMillis() - t0 : -1;
                if (ok) { JSONObject o = Db.obj(body); apps = o == null ? new JSONObject() : o; }
                else U.toast(MainActivity.this, "Offline: " + body);
                if (after != null) after.run();
            }
        });
    }

    // =====================================================================
    //  NAV + TABS
    // =====================================================================
    void buildNav() {
        nav.removeAllViews();
        String[] icons = {"\u2302", "\u25A6", "\u2754", "\u2699"};
        String[] names = {"Home", "Apps", "Guide", "Settings"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout it = U.col(this);
            it.setGravity(Gravity.CENTER);
            boolean sel = i == tab;
            TextView ic = U.tv(this, icons[i], 20, sel ? U.PRIMARY : U.SUB, false);
            ic.setGravity(Gravity.CENTER);
            ic.setBackground(sel ? U.rr(U.tonal(), 50) : null);
            ic.setPadding(U.dp(22), U.dp(4), U.dp(22), U.dp(4));
            it.addView(ic);
            TextView tx = U.tv(this, names[i], 11.5f, sel ? U.PRIMARY : U.SUB, true);
            it.addView(tx, U.lp(-2, -2, 0, 2, 0, 0));
            it.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { tab(idx); } });
            nav.addView(it, new LinearLayout.LayoutParams(0, -2, 1f));
        }
    }

    void tab(int i) {
        tab = i;
        buildNav();
        View v;
        if (i == 0) v = dashboard();
        else if (i == 1) v = appsTab();
        else if (i == 2) v = guide();
        else v = settings();
        setScreen(v, true, null);
        if (v instanceof ScrollView) U.stagger((ViewGroup) ((ScrollView) v).getChildAt(0));
    }

    // ---- common bits ----
    LinearLayout page() {
        LinearLayout c = U.col(this);
        c.setPadding(U.dp(20), U.dp(18), U.dp(20), U.dp(28));
        return c;
    }

    View titleRow(String t, String sub) {
        LinearLayout r = U.row(this);
        LinearLayout tt = U.col(this);
        tt.addView(U.tv(this, t, 26, U.TEXT, true));
        if (sub != null) tt.addView(U.tv(this, sub, 13.5f, U.SUB, false));
        r.addView(tt, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(logo(44));
        return r;
    }

    View sectionTitle(String s) {
        TextView t = U.tv(this, s, 17, U.TEXT, true);
        t.setPadding(U.dp(2), U.dp(22), 0, U.dp(10));
        return t;
    }

    int count(boolean live) {
        int n = 0;
        Iterator<String> it = apps.keys();
        while (it.hasNext()) {
            JSONObject a = apps.optJSONObject(it.next());
            if (a != null && (!live || a.optBoolean("enabled", false))) n++;
        }
        return n;
    }

    ArrayList<String> keys() {
        ArrayList<String> l = new ArrayList<String>();
        Iterator<String> it = apps.keys();
        while (it.hasNext()) l.add(it.next());
        return l;
    }

    // =====================================================================
    //  DASHBOARD
    // =====================================================================
    View dashboard() {
        LinearLayout c = page();
        c.addView(titleRow("Eren Admin", "Dashboard"));

        // hero
        LinearLayout hero = U.col(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{U.PRIMARY, U.Toggle.blend(U.PRIMARY, 0xFF9B7BFF, .6f)});
        g.setCornerRadius(U.dp(28));
        hero.setBackground(g);
        hero.setPadding(U.dp(22), U.dp(22), U.dp(22), U.dp(22));
        hero.addView(U.tv(this, ping >= 0 ? "\u25CF Connected \u2022 " + ping + " ms" : "\u25CF Offline", 12.5f, 0xCCFFFFFF, true));
        hero.addView(U.tv(this, "Update dialogs,\nmanaged live.", 24, 0xFFFFFFFF, true), U.lp(-2, -2, 0, 8, 0, 0));
        hero.addView(U.tv(this, "Edit a dialog here and every connected app sees it within seconds.", 13, 0xDDFFFFFF, false),
                U.lp(-2, -2, 0, 6, 0, 14));
        TextView add = U.tv(this, "\uFF0B  Add app", 14, U.PRIMARY, true);
        add.setBackground(U.ripple(0xFFFFFFFF, 50));
        add.setPadding(U.dp(20), U.dp(11), U.dp(20), U.dp(11));
        add.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { appForm(null); } });
        U.press(add);
        hero.addView(add, new LinearLayout.LayoutParams(-2, -2));
        c.addView(hero, U.wrapLp(0, 18, 0, 0));

        // stats
        LinearLayout st = U.row(this);
        int total = count(false), live = count(true);
        st.addView(stat("Apps", total, U.PRIMARY), new LinearLayout.LayoutParams(0, -2, 1f));
        st.addView(stat("Live", live, U.GOOD), U.lp(0, -2, 10, 0, 0, 0));
        st.addView(stat("Off", total - live, U.SUB), U.lp(0, -2, 10, 0, 0, 0));
        for (int i = 0; i < 3; i++) ((LinearLayout.LayoutParams) st.getChildAt(i).getLayoutParams()).weight = 1f;
        c.addView(st, U.wrapLp(0, 14, 0, 0));

        // live
        ArrayList<String> ks = keys();
        sortKeys(ks, 0);
        c.addView(sectionTitle("Dialogs running now"));
        int shown = 0;
        for (String k : ks) {
            JSONObject a = apps.optJSONObject(k);
            if (a != null && a.optBoolean("enabled", false) && shown < 4) { c.addView(appCard(k, a), U.wrapLp(0, 0, 0, 10)); shown++; }
        }
        if (shown == 0) c.addView(empty("No dialog is live. Open an app and switch Dialog ON."));

        c.addView(sectionTitle("Recent apps"));
        shown = 0;
        for (String k : ks) {
            JSONObject a = apps.optJSONObject(k);
            if (a != null && shown < 3) { c.addView(appCard(k, a), U.wrapLp(0, 0, 0, 10)); shown++; }
        }
        if (shown == 0) c.addView(empty("No apps yet. Tap \u201CAdd app\u201D to start."));

        // quick tips
        LinearLayout tip = U.card(this);
        tip.addView(U.tv(this, "\uD83D\uDCA1 Quick tip", 14, U.TEXT, true));
        tip.addView(U.tv(this, "Use \u201CTest\u201D inside the editor to see the exact full-screen dialog before you save.", 13, U.SUB, false),
                U.lp(-2, -2, 0, 4, 0, 0));
        c.addView(tip, U.wrapLp(0, 14, 0, 0));
        return scroll(c);
    }

    View stat(String label, int n, int color) {
        LinearLayout c = U.card(this);
        c.setPadding(U.dp(16), U.dp(14), U.dp(16), U.dp(14));
        final TextView num = U.tv(this, "0", 28, color, true);
        c.addView(num);
        c.addView(U.tv(this, label, 12.5f, U.SUB, true));
        ValueAnimator a = ValueAnimator.ofInt(0, n);
        a.setDuration(U.anim ? 700 : 0);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator v) { num.setText(String.valueOf(v.getAnimatedValue())); }
        });
        a.start();
        return c;
    }

    View empty(String s) {
        TextView t = U.tv(this, s, 13.5f, U.SUB, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(U.dp(16), U.dp(24), U.dp(16), U.dp(24));
        t.setBackground(U.rrStroke(0xFFFAFBFF, 22, U.LINE, 1));
        return t;
    }

    void sortKeys(ArrayList<String> ks, final int mode) {
        Collections.sort(ks, new Comparator<String>() {
            public int compare(String x, String y) {
                JSONObject a = apps.optJSONObject(x), b = apps.optJSONObject(y);
                if (mode == 1) return a.optString("name").compareToIgnoreCase(b.optString("name"));
                if (mode == 2) {
                    int r = (b.optBoolean("enabled") ? 1 : 0) - (a.optBoolean("enabled") ? 1 : 0);
                    if (r != 0) return r;
                }
                return Long.compare(b.optLong("created"), a.optLong("created"));
            }
        });
    }

    // =====================================================================
    //  APPS TAB
    // =====================================================================
    View appsTab() {
        FrameLayout f = new FrameLayout(this);
        final LinearLayout c = page();
        c.addView(titleRow("My apps", count(false) + " connected"));
        EditText s = U.input(this, "\uD83D\uDD0D  Search apps", query);
        c.addView(s, U.wrapLp(0, 16, 0, 0));
        final LinearLayout chips = U.row(this);
        final String[] sn = {"Newest", "A-Z", "Live first"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView ch = U.chip(this, sn[i], sort == i);
            ch.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    sort = idx;
                    for (int j = 0; j < 3; j++) {
                        TextView t = (TextView) chips.getChildAt(j);
                        t.setTextColor(j == idx ? 0xFFFFFFFF : U.TEXT);
                        t.setBackground(j == idx ? U.rr(U.PRIMARY, 50) : U.rrStroke(0xFFFFFFFF, 50, U.LINE, 1));
                    }
                    fill(listBox0[0]);
                }
            });
            chips.addView(ch, U.lp(-2, -2, 0, 0, 8, 0));
        }
        c.addView(chips, U.wrapLp(0, 12, 0, 12));
        final LinearLayout listBox = U.col(this);
        listBox0[0] = listBox;
        c.addView(listBox);
        c.setPadding(U.dp(20), U.dp(18), U.dp(20), U.dp(100));
        s.addTextChangedListener(U.watch(new U.S() { public void on(String q) { query = q.trim().toLowerCase(Locale.ROOT); fill(listBox); } }));
        fill(listBox);
        f.addView(scroll(c), new FrameLayout.LayoutParams(-1, -1));

        TextView fab = U.tv(this, "\uFF0B", 30, 0xFFFFFFFF, false);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(U.ripple(U.PRIMARY, 22));
        fab.setElevation(U.dp(8));
        fab.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { appForm(null); } });
        U.press(fab);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(U.dp(62), U.dp(62));
        fp.gravity = Gravity.END | Gravity.BOTTOM;
        fp.setMargins(0, 0, U.dp(20), U.dp(20));
        f.addView(fab, fp);
        if (U.anim) {
            fab.setScaleX(0f);
            fab.setScaleY(0f);
            fab.animate().scaleX(1f).scaleY(1f).setStartDelay(250).setDuration(300)
                    .setInterpolator(new android.view.animation.OvershootInterpolator()).start();
        }
        return f;
    }

    final LinearLayout[] listBox0 = new LinearLayout[1];

    void fill(LinearLayout box) {
        box.removeAllViews();
        ArrayList<String> ks = keys();
        sortKeys(ks, sort);
        int n = 0;
        for (String k : ks) {
            JSONObject a = apps.optJSONObject(k);
            if (a == null) continue;
            if (query.length() > 0 && !(a.optString("name").toLowerCase(Locale.ROOT).contains(query)
                    || a.optString("detail").toLowerCase(Locale.ROOT).contains(query) || k.toLowerCase(Locale.ROOT).contains(query))) continue;
            View v = appCard(k, a);
            box.addView(v, U.wrapLp(0, 0, 0, 12));
            U.pop(v, Math.min(n, 8) * 40L);
            n++;
        }
        if (n == 0) box.addView(empty(apps.length() == 0 ? "No apps yet.\nTap \uFF0B to add your first app." : "No match."));
    }

    View iconView(String name, String url, int size) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(U.rr(U.tonal(), 18));
        TextView l = U.tv(this, name.length() > 0 ? name.substring(0, 1).toUpperCase(Locale.ROOT) : "?", size / 2.4f, U.PRIMARY, true);
        l.setGravity(Gravity.CENTER);
        f.addView(l, new FrameLayout.LayoutParams(-1, -1));
        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        final int r = U.dp(size / 3.4f);
        f.setClipToOutline(true);
        f.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        if (url != null && url.length() > 0) Eren.loadBitmap(url, 256, new Eren.BitmapCb() {
            public void done(Bitmap b) { if (b != null) iv.setImageBitmap(b); }
        });
        f.setLayoutParams(new LinearLayout.LayoutParams(U.dp(size), U.dp(size)));
        return f;
    }

    View appCard(final String key, JSONObject a) {
        LinearLayout c = U.card(this);
        c.setPadding(U.dp(16), U.dp(16), U.dp(16), U.dp(16));
        LinearLayout r = U.row(this);
        r.addView(iconView(a.optString("name"), a.optString("icon"), 60));
        LinearLayout t = U.col(this);
        t.addView(U.tv(this, a.optString("name", "Unnamed"), 16.5f, U.TEXT, true));
        TextView d = U.tv(this, a.optString("detail"), 13, U.SUB, false);
        d.setMaxLines(2);
        d.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(d, U.lp(-2, -2, 0, 2, 0, 0));
        t.addView(U.tv(this, key, 11.5f, 0xFF8A90A6, false), U.lp(-2, -2, 0, 5, 0, 0));
        r.addView(t, U.lp(0, -2, 14, 0, 8, 0));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 1f;
        boolean on = a.optBoolean("enabled", false);
        TextView pill = U.tv(this, on ? "LIVE" : "OFF", 11, on ? U.GOOD : U.SUB, true);
        pill.setPadding(U.dp(10), U.dp(4), U.dp(10), U.dp(4));
        pill.setBackground(U.rr(on ? U.tint(U.GOOD, .12f) : 0xFFEEF0F7, 50));
        r.addView(pill);
        c.addView(r);
        c.setForeground(null);
        c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { detail(key); } });
        U.press(c);
        return c;
    }

    // =====================================================================
    //  ADD / EDIT APP INFO
    // =====================================================================
    void appForm(final String editKey) {
        JSONObject cur = editKey == null ? new JSONObject() : apps.optJSONObject(editKey);
        final LinearLayout c = U.col(this);
        final EditText name = U.input(this, "e.g. MODMASE", cur.optString("name"));
        final EditText detail = U.input(this, "One short line", cur.optString("detail"));
        final EditText icon = U.input(this, "https://.../icon.png", cur.optString("icon"));
        final String date = editKey == null ? new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date()) : cur.optString("date");
        c.addView(U.labeled(this, "App name", name));
        c.addView(U.labeled(this, "Short detail", detail), U.wrapLp(0, 12, 0, 0));
        c.addView(U.labeled(this, "App icon URL", icon), U.wrapLp(0, 12, 0, 0));
        c.addView(U.tv(this, "\uD83D\uDCC5 Date: " + date + " (auto)", 12.5f, U.SUB, true), U.lp(-2, -2, 4, 12, 0, 0));
        sheet(editKey == null ? "Add app" : "Edit app", c, editKey == null ? "Add" : "Save", new Runnable() {
            public void run() {
                String n = name.getText().toString().trim();
                if (n.length() == 0) { U.toast(MainActivity.this, "App name is required"); return; }
                JSONObject o = new JSONObject();
                U.put(o, "name", n);
                U.put(o, "detail", detail.getText().toString().trim());
                U.put(o, "icon", icon.getText().toString().trim());
                if (editKey == null) {
                    final String k = uniqueKey();
                    U.put(o, "date", date);
                    U.put(o, "created", System.currentTimeMillis());
                    U.put(o, "enabled", false);
                    JSONObject dlg = Editor.defaults();
                    U.put(dlg, "brand1", n.length() > 3 ? n.substring(0, n.length() / 2).toUpperCase(Locale.ROOT) : n.toUpperCase(Locale.ROOT));
                    U.put(dlg, "brand2", n.length() > 3 ? n.substring(n.length() / 2).toUpperCase(Locale.ROOT) : "");
                    U.put(o, "dialog", dlg);
                    busy(true);
                    final JSONObject fo = o;
                    Db.put("/eren/apps/" + k, o.toString(), new Db.CB() {
                        public void done(boolean ok, String body) {
                            busy(false);
                            if (!ok) { U.toast(MainActivity.this, "Failed: " + body); return; }
                            U.put(apps, k, fo);
                            if (tab == 1) tab(1); else tab(tab);
                            detail(k);
                        }
                    });
                } else {
                    busy(true);
                    Db.patch("/eren/apps/" + editKey, o.toString(), new Db.CB() {
                        public void done(boolean ok, String body) {
                            busy(false);
                            if (!ok) { U.toast(MainActivity.this, "Failed: " + body); return; }
                            JSONObject a = apps.optJSONObject(editKey);
                            try {
                                a.put("name", name.getText().toString().trim());
                                a.put("detail", detail.getText().toString().trim());
                                a.put("icon", icon.getText().toString().trim());
                            } catch (Throwable t) { }
                            detail(editKey);
                        }
                    });
                }
            }
        });
    }

    String uniqueKey() {
        String k = Db.genKey();
        while (apps.has(k)) k = Db.genKey();
        return k;
    }

    // =====================================================================
    //  APP DETAIL
    // =====================================================================
    void detail(final String key) {
        final JSONObject a = apps.optJSONObject(key);
        if (a == null) { tab(1); return; }
        LinearLayout c = U.col(this);
        c.setPadding(U.dp(12), U.dp(10), U.dp(20), U.dp(28));

        LinearLayout bar = U.row(this);
        TextView bk = U.tv(this, "\u2190", 24, U.TEXT, true);
        bk.setPadding(U.dp(12), U.dp(4), U.dp(12), U.dp(4));
        bk.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { onBackPressed(); } });
        bar.addView(bk);
        bar.addView(U.tv(this, "App", 18, U.TEXT, true), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView ed = U.tv(this, "\u270E  Edit", 14, U.PRIMARY, true);
        ed.setBackground(U.ripple(U.tonal(), 50));
        ed.setPadding(U.dp(18), U.dp(9), U.dp(18), U.dp(9));
        ed.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openEditor(key, a.optJSONObject("dialog"), false); } });
        U.press(ed);
        bar.addView(ed);
        c.addView(bar);

        LinearLayout in = U.col(this);
        in.setPadding(U.dp(8), U.dp(8), 0, 0);
        c.addView(in);

        // header
        LinearLayout h = U.card(this);
        LinearLayout hr = U.row(this);
        hr.addView(iconView(a.optString("name"), a.optString("icon"), 76));
        LinearLayout ht = U.col(this);
        ht.addView(U.tv(this, a.optString("name"), 21, U.TEXT, true));
        ht.addView(U.tv(this, a.optString("detail"), 13.5f, U.SUB, false), U.lp(-2, -2, 0, 3, 0, 0));
        ht.addView(U.tv(this, "\uD83D\uDCC5 " + a.optString("date"), 12, 0xFF8A90A6, true), U.lp(-2, -2, 0, 6, 0, 0));
        hr.addView(ht, U.lp(0, -2, 16, 0, 0, 0));
        ((LinearLayout.LayoutParams) ht.getLayoutParams()).weight = 1f;
        h.addView(hr);
        in.addView(h, U.wrapLp(0, 6, 0, 0));

        // connection info
        in.addView(sectionTitle("Connect this app"));
        in.addView(copyCard("App Connect Key", key, true), U.wrapLp(0, 0, 0, 10));
        in.addView(copyCard("Firebase databaseURL", Db.base, false), U.wrapLp(0, 0, 0, 10));
        in.addView(copyCard("INTERNET permission", PERM, false), U.wrapLp(0, 0, 0, 10));

        // controls
        in.addView(sectionTitle("Controls"));
        LinearLayout ctl = U.card(this);
        ctl.addView(U.switchRow(this, "Dialog", "Show the update dialog in this app", a.optBoolean("enabled", false), new U.S() {
            public void on(String s) {
                final boolean en = s.equals("1");
                JSONObject o = new JSONObject();
                U.put(o, "enabled", en);
                Db.patch("/eren/apps/" + key, o.toString(), new Db.CB() {
                    public void done(boolean ok, String body) {
                        if (ok) { U.put(a, "enabled", en); U.toast(MainActivity.this, en ? "Dialog enabled" : "Dialog disabled"); }
                        else U.toast(MainActivity.this, "Failed: " + body);
                    }
                });
            }
        }));
        ctl.addView(U.hr(this));
        ctl.addView(row2("Edit dialog", "\u270E", new Runnable() { public void run() { openEditor(key, a.optJSONObject("dialog"), false); } }));
        ctl.addView(row2("Edit app info", "\u2139", new Runnable() { public void run() { appForm(key); } }));
        ctl.addView(row2("Duplicate app (new key)", "\u29C9", new Runnable() { public void run() { duplicate(key); } }));
        ctl.addView(row2("Copy all setup info", "\u2398", new Runnable() {
            public void run() {
                U.copy(MainActivity.this, "APP_KEY = " + key + "\nDB_URL = " + Db.base + "\n" + PERM, "Setup info");
            }
        }));
        in.addView(ctl);

        TextView del = U.dangerBtn(this, "Delete app", new View.OnClickListener() {
            public void onClick(View v) {
                confirm("Delete this app?", "The key stops working and its dialog disappears everywhere.", "Delete", new Runnable() {
                    public void run() {
                        busy(true);
                        Db.del("/eren/apps/" + key, new Db.CB() {
                            public void done(boolean ok, String body) {
                                busy(false);
                                if (ok) { apps.remove(key); tab(1); } else U.toast(MainActivity.this, "Failed: " + body);
                            }
                        });
                    }
                });
            }
        });
        in.addView(del, U.wrapLp(0, 14, 0, 0));

        setScreen(scroll(c), false, new Runnable() { public void run() { tab(1); } });
        U.stagger(in);
    }

    View row2(String label, String icon, final Runnable r) {
        LinearLayout l = U.row(this);
        l.setPadding(0, U.dp(12), 0, U.dp(12));
        l.addView(U.tv(this, icon, 18, U.PRIMARY, true), U.lp(U.dp(28), -2, 0, 0, 10, 0));
        l.addView(U.tv(this, label, 15, U.TEXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
        l.addView(U.tv(this, "\u203A", 22, U.SUB, false));
        l.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        return l;
    }

    View copyCard(String label, final String value, boolean big) {
        LinearLayout c = U.card(this);
        c.setPadding(U.dp(16), U.dp(14), U.dp(16), U.dp(14));
        LinearLayout r = U.row(this);
        LinearLayout t = U.col(this);
        t.addView(U.tv(this, label, 12, U.SUB, true));
        TextView v = U.tv(this, value, big ? 19 : 12.5f, U.TEXT, big);
        if (!big) v.setTypeface(Typeface.MONOSPACE);
        v.setTextIsSelectable(false);
        t.addView(v, U.lp(-2, -2, 0, 4, 0, 0));
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        final String lab = label;
        TextView cp = U.tv(this, "Copy", 13, U.PRIMARY, true);
        cp.setBackground(U.ripple(U.tonal(), 50));
        cp.setPadding(U.dp(16), U.dp(8), U.dp(16), U.dp(8));
        cp.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { U.copy(MainActivity.this, value, lab); } });
        U.press(cp);
        r.addView(cp, U.lp(-2, -2, 10, 0, 0, 0));
        c.addView(r);
        return c;
    }

    void duplicate(String key) {
        JSONObject src = apps.optJSONObject(key);
        try {
            final String k = uniqueKey();
            final JSONObject o = new JSONObject(src.toString());
            o.put("name", src.optString("name") + " copy");
            o.put("enabled", false);
            o.put("created", System.currentTimeMillis());
            busy(true);
            Db.put("/eren/apps/" + k, o.toString(), new Db.CB() {
                public void done(boolean ok, String body) {
                    busy(false);
                    if (ok) { U.put(apps, k, o); detail(k); U.toast(MainActivity.this, "Duplicated"); }
                    else U.toast(MainActivity.this, "Failed: " + body);
                }
            });
        } catch (Throwable t) { }
    }

    void openEditor(String key, JSONObject saved, boolean dirtyStart) {
        Editor e = new Editor(this, key, saved);
        e.dirty = dirtyStart;
        View v = e.build();
        setScreen(v, false, back);
        back = e.m.back;
        pendingEditor = e;
    }

    // =====================================================================
    //  GALLERY PICK (stored as compressed base64 data URI)
    // =====================================================================
    void pickImage(Editor e) {
        pendingEditor = e;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "Choose image"), PICK);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != PICK || res != RESULT_OK || data == null || pendingEditor == null) return;
        try {
            Uri u = data.getData();
            InputStream in = getContentResolver().openInputStream(u);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int s = 1;
            while (o.outWidth / (s * 2) >= 1200) s *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = s;
            in = getContentResolver().openInputStream(u);
            Bitmap bm = BitmapFactory.decodeStream(in, null, o);
            in.close();
            if (bm == null) throw new RuntimeException("decode");
            int q = 85;
            byte[] out;
            do {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                bm.compress(Bitmap.CompressFormat.JPEG, q, bo);
                out = bo.toByteArray();
                q -= 10;
            } while (out.length > 380 * 1024 && q > 25);
            pendingEditor.setMedia("data:image/jpeg;base64," + Base64.encodeToString(out, Base64.NO_WRAP));
            U.toast(this, "Image added (" + out.length / 1024 + " KB)");
        } catch (Throwable t) { U.toast(this, "Could not read image"); }
    }

    // =====================================================================
    //  GUIDE
    // =====================================================================
    View guide() {
        LinearLayout c = page();
        c.addView(titleRow("Guide", "From zero to a live dialog"));
        String[][] steps = {
                {"Create the database", "Firebase Console \u2192 Realtime Database \u2192 Create. Copy the databaseURL, then open Rules and paste the rules below."},
                {"Connect & create your key", "Open Eren Admin, paste the databaseURL, then create your private access key. You need this key every time you open the panel."},
                {"Add an app", "Apps tab \u2192 \uFF0B. Give it a name, a short detail and an icon URL. A unique App Connect Key is generated for it."},
                {"Build Eren with your values", "Open the Eren APK with MT Manager \u2192 Eren.smali (com/eren/dialog). Replace the DB_URL string with your databaseURL and the APP_KEY string with the App Connect Key. Make sure the target app has the INTERNET permission line shown on the app page."},
                {"Hook it", "In your target app call  Eren.show(this);  from an Activity (see MainActivity of Eren)."},
                {"Design & enable", "Edit dialog \u2192 style it with the live preview \u2192 Save. Switch Dialog ON. Users see it within seconds, and edits sync live."},
                {"Switch it off", "When everyone updated, turn Dialog OFF - or use \u201Cshow only below versionCode\u201D in Targeting."}
        };
        for (int i = 0; i < steps.length; i++) {
            LinearLayout card = U.card(this);
            LinearLayout r = U.row(this);
            TextView n = U.tv(this, String.valueOf(i + 1), 14, 0xFFFFFFFF, true);
            n.setGravity(Gravity.CENTER);
            n.setBackground(U.circle(U.PRIMARY));
            r.addView(n, new LinearLayout.LayoutParams(U.dp(30), U.dp(30)));
            r.addView(U.tv(this, steps[i][0], 15.5f, U.TEXT, true), U.lp(-2, -2, 12, 0, 0, 0));
            card.addView(r);
            card.addView(U.tv(this, steps[i][1], 13.5f, U.SUB, false), U.lp(-2, -2, 0, 8, 0, 0));
            c.addView(card, U.wrapLp(0, i == 0 ? 18 : 0, 0, 10));
        }
        final String rules = "{\n  \"rules\": {\n    \"eren\": { \".read\": true, \".write\": true }\n  }\n}";
        c.addView(copyCard("Database rules (paste in Firebase Rules tab)", rules, false), U.wrapLp(0, 4, 0, 10));
        LinearLayout warn = U.card(this);
        warn.addView(U.tv(this, "\uD83D\uDD12 About security", 14, U.TEXT, true));
        warn.addView(U.tv(this, "The access key is checked inside this app. Anyone who knows your databaseURL can read/write /eren. "
                + "Use a long random App Connect Key and keep the URL private.", 13, U.SUB, false), U.lp(-2, -2, 0, 4, 0, 0));
        c.addView(warn);
        return scroll(c);
    }

    // =====================================================================
    //  SETTINGS
    // =====================================================================
    View settings() {
        LinearLayout c = page();
        c.addView(titleRow("Settings", "Make it yours"));

        LinearLayout ap = U.card(this);
        ap.addView(U.tv(this, "Appearance", 16, U.TEXT, true));
        ap.addView(U.tv(this, "Accent color", 12.5f, U.SUB, true), U.lp(-2, -2, 0, 12, 0, 8));
        LinearLayout sw = U.row(this);
        for (int i = 0; i < ACCENTS.length; i++) {
            final String hex = ACCENTS[i];
            View v = new View(this);
            boolean sel = Color.parseColor(hex) == U.PRIMARY;
            v.setBackground(sel ? U.circle(Color.parseColor(hex)) : U.circle(Color.parseColor(hex)));
            v.setAlpha(sel ? 1f : .55f);
            v.setScaleX(sel ? 1.1f : .9f);
            v.setScaleY(sel ? 1.1f : .9f);
            v.setOnClickListener(new View.OnClickListener() {
                public void onClick(View x) {
                    sp.edit().putString("accent", hex).apply();
                    U.PRIMARY = Color.parseColor(hex);
                    tab(3);
                }
            });
            sw.addView(v, U.lp(U.dp(38), U.dp(38), 0, 0, 12, 0));
        }
        ap.addView(sw);
        ap.addView(U.hr(this));
        ap.addView(U.switchRow(this, "Animations", "Smooth transitions and effects", U.anim, new U.S() {
            public void on(String s) { U.anim = s.equals("1"); sp.edit().putBoolean("anim", U.anim).apply(); }
        }));
        c.addView(ap, U.wrapLp(0, 18, 0, 10));

        LinearLayout se = U.card(this);
        se.addView(U.tv(this, "Security", 16, U.TEXT, true));
        se.addView(U.switchRow(this, "Stay signed in", "Skip the access key on this device", sp.getBoolean("stayOn", false), new U.S() {
            public void on(String s) {
                boolean on = s.equals("1");
                sp.edit().putBoolean("stayOn", on).putString("stay", on ? adminHash : "").apply();
            }
        }), U.wrapLp(0, 8, 0, 0));
        se.addView(U.hr(this));
        se.addView(row2("Change access key", "\uD83D\uDD11", new Runnable() { public void run() { changeKey(); } }));
        c.addView(se, U.wrapLp(0, 0, 0, 10));

        LinearLayout co = U.card(this);
        co.addView(U.tv(this, "Connection", 16, U.TEXT, true));
        TextView url = U.tv(this, Db.base, 12.5f, U.SUB, false);
        url.setTypeface(Typeface.MONOSPACE);
        co.addView(url, U.lp(-2, -2, 0, 6, 0, 4));
        co.addView(row2("Test connection / refresh", "\u21BB", new Runnable() {
            public void run() {
                busy(true);
                loadApps(new Runnable() { public void run() { busy(false); U.toast(MainActivity.this, ping >= 0 ? "Connected \u2022 " + ping + " ms" : "Offline"); } });
            }
        }));
        co.addView(row2("Export all apps (copy JSON)", "\u21E9", new Runnable() {
            public void run() { U.copy(MainActivity.this, apps.toString(), "All apps JSON"); }
        }));
        co.addView(row2("Disconnect database", "\u23FB", new Runnable() {
            public void run() {
                confirm("Disconnect?", "You will need the databaseURL and access key again.", "Disconnect", new Runnable() {
                    public void run() { sp.edit().remove("db").remove("stay").apply(); adminHash = ""; apps = new JSONObject(); connectScreen(); }
                });
            }
        }));
        c.addView(co, U.wrapLp(0, 0, 0, 10));

        LinearLayout ab = U.card(this);
        ab.addView(U.tv(this, "About", 16, U.TEXT, true));
        ab.addView(U.tv(this, "Eren Admin 1.0  \u2022  by TENIx\nControls the Eren update dialog through Firebase Realtime Database.", 13, U.SUB, false),
                U.lp(-2, -2, 0, 6, 0, 0));
        c.addView(ab);
        return scroll(c);
    }

    void changeKey() {
        LinearLayout c = U.col(this);
        final EditText[] o = new EditText[1], n = new EditText[1];
        c.addView(U.labeled(this, "Current key", U.pass(this, "Current key", o)));
        c.addView(U.labeled(this, "New key", U.pass(this, "At least 6 characters", n)), U.wrapLp(0, 12, 0, 0));
        sheet("Change access key", c, "Update", new Runnable() {
            public void run() {
                if (!Db.sha(o[0].getText().toString()).equals(adminHash)) { U.toast(MainActivity.this, "Current key is wrong"); return; }
                String nk = n[0].getText().toString();
                if (nk.length() < 6) { U.toast(MainActivity.this, "New key too short"); return; }
                final String h = Db.sha(nk);
                JSONObject j = new JSONObject();
                U.put(j, "keyHash", h);
                busy(true);
                Db.patch("/eren/admin", j.toString(), new Db.CB() {
                    public void done(boolean ok, String body) {
                        busy(false);
                        if (ok) {
                            adminHash = h;
                            if (sp.getBoolean("stayOn", false)) sp.edit().putString("stay", h).apply();
                            U.toast(MainActivity.this, "Access key updated");
                        } else U.toast(MainActivity.this, "Failed: " + body);
                    }
                });
            }
        });
    }

    // =====================================================================
    //  DIALOG HELPERS
    // =====================================================================
    void sheet(String title, View content, String ok, final Runnable onOk) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = U.col(this);
        box.setBackground(U.rr(U.SURF, 28));
        box.setPadding(U.dp(22), U.dp(22), U.dp(22), U.dp(18));
        box.addView(U.tv(this, title, 19, U.TEXT, true), U.lp(-2, -2, 0, 0, 0, 14));
        box.addView(content, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout r = U.row(this);
        r.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = U.btn(this, "Cancel", false, new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } });
        TextView okb = U.btn(this, ok, true, new View.OnClickListener() {
            public void onClick(View v) { d.dismiss(); onOk.run(); }
        });
        r.addView(cancel);
        r.addView(okb, U.lp(-2, -2, 8, 0, 0, 0));
        box.addView(r, U.wrapLp(0, 18, 0, 0));
        d.setContentView(box);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setLayout(getResources().getDisplayMetrics().widthPixels - U.dp(32), ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        d.show();
        if (U.anim) {
            box.setScaleX(.92f);
            box.setScaleY(.92f);
            box.setAlpha(0f);
            box.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    void confirm(String title, String msg, String ok, Runnable onOk) {
        TextView t = U.tv(this, msg, 14, U.SUB, false);
        sheet(title, t, ok, onOk);
    }
}
