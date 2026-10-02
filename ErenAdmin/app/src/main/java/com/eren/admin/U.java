package com.eren.admin;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/** Tiny UI toolkit - light, rounded, Material-You / Stitch-like. */
final class U {
    static float den = 3f;
    static boolean anim = true;
    static int PRIMARY = 0xFF4F6AF5;
    static final int BG = 0xFFF4F6FC, SURF = 0xFFFFFFFF, TEXT = 0xFF1A1C28, SUB = 0xFF6A7084,
            LINE = 0xFFE4E7F2, GOOD = 0xFF1FA463, BAD = 0xFFE5484D;
    static Typeface head;
    static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface S { void on(String s); }

    static void init(Context c) {
        den = c.getResources().getDisplayMetrics().density;
        head = Eren.font(c, "Poppins-SemiBold");
    }

    static int dp(float v) { return Math.round(v * den); }

    static int tint(int c, float a) { return (c & 0xFFFFFF) | ((int) (a * 255) << 24); }

    static int tonal() { return tint(PRIMARY, 0.12f); }

    static GradientDrawable rr(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    static GradientDrawable rrStroke(int color, float r, int stroke, float w) {
        GradientDrawable g = rr(color, r);
        g.setStroke(dp(w), stroke);
        return g;
    }

    static Drawable ripple(int fill, float r) {
        return new RippleDrawable(ColorStateList.valueOf(0x22000000), rr(fill, r), rr(0xFFFFFFFF, r));
    }

    static void put(JSONObject o, String k, Object v) {
        try { o.put(k, v); } catch (Throwable t) { }
    }

    static TextView tv(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(head);
        return t;
    }

    static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    static LinearLayout.LayoutParams wrapLp(float l, float t, float r, float b) { return lp(-1, -2, l, t, r, b); }

    static LinearLayout card(Context c) {
        LinearLayout l = col(c);
        l.setBackground(rrStroke(SURF, 24, LINE, 1));
        l.setPadding(dp(18), dp(16), dp(18), dp(16));
        l.setElevation(dp(1));
        return l;
    }

    static void press(final View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View x, MotionEvent e) {
                int a = e.getAction();
                if (a == MotionEvent.ACTION_DOWN) x.animate().scaleX(.97f).scaleY(.97f).setDuration(80).start();
                else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL)
                    x.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                return false;
            }
        });
    }

    static TextView btn(Context c, String s, boolean filled, View.OnClickListener l) {
        TextView t = tv(c, s, 15, filled ? 0xFFFFFFFF : PRIMARY, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(20), dp(14), dp(20), dp(14));
        t.setBackground(filled ? ripple(PRIMARY, 18) : ripple(tonal(), 18));
        t.setOnClickListener(l);
        press(t);
        return t;
    }

    static TextView dangerBtn(Context c, String s, View.OnClickListener l) {
        TextView t = tv(c, s, 15, BAD, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(20), dp(14), dp(20), dp(14));
        t.setBackground(ripple(tint(BAD, .1f), 18));
        t.setOnClickListener(l);
        press(t);
        return t;
    }

    static EditText input(Context c, String hint, String val) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setText(val == null ? "" : val);
        e.setTextSize(15);
        e.setTextColor(TEXT);
        e.setHintTextColor(0xFFA3A8B8);
        e.setSingleLine(true);
        e.setBackground(rrStroke(0xFFF7F8FD, 16, LINE, 1));
        e.setPadding(dp(16), dp(13), dp(16), dp(13));
        return e;
    }

    static View labeled(Context c, String label, View field) {
        LinearLayout l = col(c);
        TextView t = tv(c, label, 12.5f, SUB, true);
        l.addView(t, lp(-2, -2, 4, 0, 0, 6));
        l.addView(field, new LinearLayout.LayoutParams(-1, -2));
        return l;
    }

    /** Password field with eye icon. out[0] = EditText. */
    static View pass(Context c, String hint, final EditText[] out) {
        FrameLayout f = new FrameLayout(c);
        final EditText e = input(c, hint, "");
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setPadding(dp(16), dp(14), dp(52), dp(14));
        f.addView(e, new FrameLayout.LayoutParams(-1, -2));
        final TextView eye = tv(c, "\uD83D\uDC41", 20, SUB, false);
        eye.setPadding(dp(12), dp(12), dp(14), dp(12));
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(-2, -2);
        ep.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        f.addView(eye, ep);
        final boolean[] shown = {false};
        eye.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                shown[0] = !shown[0];
                int sel = e.getSelectionEnd();
                e.setInputType(InputType.TYPE_CLASS_TEXT | (shown[0]
                        ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
                eye.setText(shown[0] ? "\uD83D\uDE48" : "\uD83D\uDC41");
                e.setSelection(Math.max(0, sel));
            }
        });
        out[0] = e;
        return f;
    }

    static TextWatcher watch(final S s) {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence a, int b, int c, int d) { }
            public void onTextChanged(CharSequence a, int b, int c, int d) { }
            public void afterTextChanged(Editable e) { s.on(e.toString()); }
        };
    }

    static TextView chip(Context c, String s, boolean sel) {
        TextView t = tv(c, s, 13, sel ? 0xFFFFFFFF : TEXT, true);
        t.setPadding(dp(16), dp(9), dp(16), dp(9));
        t.setBackground(sel ? rr(PRIMARY, 50) : rrStroke(0xFFFFFFFF, 50, LINE, 1));
        press(t);
        return t;
    }

    static void toast(Context c, String s) { Toast.makeText(c, s, Toast.LENGTH_SHORT).show(); }

    static void copy(Context c, String s, String what) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Eren", s));
        toast(c, what + " copied");
    }

    static void pop(View v, long delay) {
        if (!anim) return;
        v.setAlpha(0f);
        v.setTranslationY(dp(18));
        v.animate().alpha(1f).translationY(0).setStartDelay(delay).setDuration(320)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    static void stagger(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) pop(g.getChildAt(i), Math.min(i, 8) * 45L);
    }

    static void shake(View v) {
        v.animate().translationX(dp(10)).setDuration(60).withEndAction(new Runnable() {
            public void run() { v.animate().translationX(-dp(8)).setDuration(60).withEndAction(new Runnable() {
                public void run() { v.animate().translationX(0).setDuration(60).start(); }
            }).start(); }
        }).start();
    }

    static Drawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    /** Animated pill toggle. */
    static class Toggle extends View {
        boolean on;
        float pos;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        S cb;

        Toggle(Context c, boolean v, S cb) {
            super(c);
            on = v;
            pos = v ? 1f : 0f;
            this.cb = cb;
            setLayoutParams(new ViewGroup.LayoutParams(dp(54), dp(32)));
            setOnClickListener(new OnClickListener() {
                public void onClick(View x) { set(!on, true); }
            });
        }

        void set(boolean v, boolean fire) {
            on = v;
            ValueAnimator a = ValueAnimator.ofFloat(pos, v ? 1f : 0f);
            a.setDuration(anim ? 180 : 0);
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator va) { pos = (Float) va.getAnimatedValue(); invalidate(); }
            });
            a.start();
            if (fire && cb != null) cb.on(v ? "1" : "0");
        }

        @Override
        protected void onMeasure(int w, int h) { setMeasuredDimension(dp(54), dp(32)); }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            int off = 0xFFD5D9E6;
            int col = blend(off, PRIMARY, pos);
            p.setColor(col);
            c.drawRoundRect(new RectF(0, 0, w, h), h / 2, h / 2, p);
            p.setColor(0xFFFFFFFF);
            float r = h / 2 - dp(4) + pos * dp(1.5f);
            float cx = h / 2 + pos * (w - h);
            c.drawCircle(cx, h / 2, r, p);
        }

        static int blend(int a, int b, float t) {
            int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
            int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
            int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
            return 0xFF000000 | (r << 16) | (g << 8) | bl;
        }
    }

    static View switchRow(Context c, String title, String sub, boolean val, S cb) {
        LinearLayout r = row(c);
        LinearLayout t = col(c);
        t.addView(tv(c, title, 15, TEXT, true));
        if (sub != null) t.addView(tv(c, sub, 12.5f, SUB, false), lp(-2, -2, 0, 2, 0, 0));
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(new Toggle(c, val, cb));
        r.setPadding(0, dp(6), 0, dp(6));
        return r;
    }

    static View hr(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(lp(-1, 1, 0, 10, 0, 10));
        return v;
    }
}
