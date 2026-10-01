package com.eren.dialog;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ============================================================================
 *  EREN  -  the update dialog itself
 * ============================================================================
 *
 *  Pure programmatic UI. No XML, no R.* resources, no third-party libraries -
 *  so the compiled smali can be dropped into any modded APK and it just works.
 *  The overlay is attached straight onto the host activity's content view:
 *
 *      +--------------------------------------------+
 *      |  hero image / video  (1200 x 675, 16:9)    |   + bottom fade
 *      +--------------------------------------------+
 *      |                 UPDATE                     |   Audiowide, small
 *      |  MOD MASE                                  |   Oswald, huge
 *      |  +--------------------------------------+  |
 *      |  | (diamond) New Features Available     |  |   features card
 *      |  | (diamond) Previous Bug Fixed         |  |
 *      |  +--------------------------------------+  |
 *      |   [   EXIT   ]      [   UPDATE   ]         |   black outlined
 *      +--------------------------------------------+
 *
 *  Every colour, label, radius, font and the media itself come from the remote
 *  config written by the Eren Admin panel, and can change live while the
 *  overlay is on screen.
 * ============================================================================
 */
final class ErenDialog {

    /** Currently visible dialog, or null. */
    static ErenDialog CURRENT;

    /* ------------------------------------------------------------------ core */

    private final Activity activity;
    private final String key;
    private final Eren.Callback callback;
    final Handler handler = new Handler(Looper.getMainLooper());

    private Eren.Cfg cfg;
    private ErenData.Live live;
    private ErenData.Poller poller;
    private boolean closed;
    private int attempts;
    private boolean attached;
    private boolean revealed;

    /* ------------------------------------------------------------------- UI */

    private Overlay root;
    private View scrim;
    private LinearLayout card;
    private FrameLayout hero;
    private View heroFade;
    private TextView heroChip;
    private View playBadge;
    private TextView smallTitleView;
    private TextView brandView;
    private LinearLayout featuresCard;
    private LinearLayout featuresBox;
    private TextView exitBtn;
    private TextView updateBtn;
    private LinearLayout buttonRow;
    private ImageView heroImage;
    private VideoView heroVideo;

    private Typeface tfBrand;
    private Typeface tfTitle;
    private Typeface tfButton;
    private Typeface tfBody;

    private static final ExecutorService NET = Executors.newFixedThreadPool(3);

    /* ==================================================================== */

    ErenDialog(Activity activity, String key, Eren.Callback callback) {
        this.activity = activity;
        this.key = key;
        this.callback = callback;
    }

    /* ====================================================================
     *  Present  -  attach the overlay to the host activity
     * ================================================================== */

    void present() {
        final ViewGroup host;
        try {
            /* android.R.id.content = the framework's content view, so the dialog
             * app never references its own R class -> safe to drop into any APK */
            host = (ViewGroup) activity.getWindow().getDecorView()
                    .findViewById(android.R.id.content);
        } catch (Throwable t) {
            Eren.log("host content view not found");
            return;
        }
        if (host == null) {
            Eren.log("host content view not found");
            return;
        }

        /* If the admin switched this app off, nothing is drawn at all - the host
         * app simply runs without the update dialog. */
        final Eren.Cfg cached = ErenData.loadCache(activity, key);
        if (cached != null && !cached.enabled) {
            Eren.log("dialog disabled by admin - nothing shown");
            return;
        }

        /* post one frame so the overlay always sits on top of the activity's own
         * layout, whether show() runs before or after setContentView() */
        host.post(new Runnable() {
            @Override
            public void run() {
                if (closed || activity.isFinishing()) {
                    return;
                }
                try {
                    build();
                } catch (Throwable t) {
                    Eren.log("build failed: " + t);
                    return;
                }
                try {
                    host.addView(root, new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
                    attached = true;
                    CURRENT = ErenDialog.this;
                    /* invisible until the first config arrives, so the built-in
                     * fallback look is never flashed before the real one */
                    root.setAlpha(0f);
                    root.setVisibility(View.INVISIBLE);
                } catch (Throwable t) {
                    Eren.log("attach failed: " + t);
                    return;
                }

                /* 1) paint from the disk cache instantly */
                if (cached != null) {
                    apply(cached);
                    Eren.log("painted from cache");
                }

                /* 2) hit the database, then go live */
                refresh(true);
            }
        });
    }

    /** First real paint: fade + lift the overlay in. */
    private void reveal() {
        if (revealed || root == null) {
            return;
        }
        revealed = true;
        root.setVisibility(View.VISIBLE);
        root.setAlpha(0f);
        root.setTranslationY(Eren.dp(activity, 14));
        root.setScaleX(0.97f);
        root.setScaleY(0.97f);
        root.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(280).start();
    }

    /* ====================================================================
     *  UI construction
     * ================================================================== */

    private void build() {
        tfBrand = Eren.font(activity, Eren.FONT_BRAND, Typeface.NORMAL);
        tfTitle = Eren.font(activity, Eren.FONT_TITLE, Typeface.NORMAL);
        tfButton = Eren.font(activity, Eren.FONT_BUTTON, Typeface.NORMAL);
        tfBody = Eren.font(activity, Eren.FONT_BODY, Typeface.BOLD);

        cfg = Eren.Cfg.localDefault(key);

        root = new Overlay(activity);
        root.setClickable(true);
        root.setFocusableInTouchMode(true);

        scrim = new View(activity);
        scrim.setBackgroundColor(Eren.color(cfg.scrimColor, 0xB3000000));
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cfg == null || cfg.dismissible) {
                    close(false);
                }
            }
        });
        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        int outer = Eren.dp(activity, 16);
        scroll.setPadding(outer, Eren.dp(activity, 22), outer, Eren.dp(activity, 22));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        /* ------------------------------------------------------------ card */

        card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        column.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        /* ------------------------------------------------------------ hero */

        hero = new FrameLayout(activity);
        card.addView(hero, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Eren.dp(activity, 200)));

        heroImage = new ImageView(activity);
        heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(heroImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        heroFade = new View(activity);
        FrameLayout.LayoutParams fadeLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Eren.dp(activity, 74));
        fadeLp.gravity = Gravity.BOTTOM;
        hero.addView(heroFade, fadeLp);

        heroChip = new TextView(activity);
        heroChip.setTextSize(11f);
        heroChip.setTypeface(tfBody);
        heroChip.setTextColor(Color.WHITE);
        heroChip.setVisibility(View.GONE);
        FrameLayout.LayoutParams chipLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipLp.gravity = Gravity.TOP | Gravity.START;
        chipLp.setMargins(Eren.dp(activity, 14), Eren.dp(activity, 14), 0, 0);
        hero.addView(heroChip, chipLp);

        playBadge = new View(activity);
        playBadge.setVisibility(View.GONE);
        int pb = Eren.dp(activity, 62);
        FrameLayout.LayoutParams pbLp = new FrameLayout.LayoutParams(pb, pb);
        pbLp.gravity = Gravity.CENTER;
        hero.addView(playBadge, pbLp);

        /* ------------------------------------------------------- text block */

        LinearLayout textWrap = new LinearLayout(activity);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        textWrap.setGravity(Gravity.CENTER_HORIZONTAL);
        int padH = Eren.dp(activity, 25);
        textWrap.setPadding(padH, Eren.dp(activity, 4), padH, Eren.dp(activity, 23));
        card.addView(textWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        smallTitleView = new TextView(activity);
        smallTitleView.setTypeface(tfTitle);
        smallTitleView.setTextSize(19f);
        smallTitleView.setLetterSpacing(0.07f);
        smallTitleView.setGravity(Gravity.CENTER);
        textWrap.addView(smallTitleView);

        brandView = new TextView(activity);
        brandView.setTypeface(tfBrand);
        brandView.setTextSize(58f);
        brandView.setGravity(Gravity.CENTER);
        brandView.setIncludeFontPadding(false);
        brandView.setLetterSpacing(0.03f);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brandLp.topMargin = Eren.dp(activity, 2);
        textWrap.addView(brandView, brandLp);

        /* --------------------------------------------------- features card */

        featuresCard = new LinearLayout(activity);
        featuresCard.setOrientation(LinearLayout.VERTICAL);
        featuresCard.setPadding(Eren.dp(activity, 22), Eren.dp(activity, 18),
                Eren.dp(activity, 22), Eren.dp(activity, 18));
        LinearLayout.LayoutParams fcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fcLp.topMargin = Eren.dp(activity, 22);
        textWrap.addView(featuresCard, fcLp);

        featuresBox = new LinearLayout(activity);
        featuresBox.setOrientation(LinearLayout.VERTICAL);
        featuresCard.addView(featuresBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        /* -------------------------------------------------------- buttons */

        buttonRow = new LinearLayout(activity);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brLp.topMargin = Eren.dp(activity, 28);
        textWrap.addView(buttonRow, brLp);

        exitBtn = makeButton();
        updateBtn = makeButton();

        LinearLayout.LayoutParams lpExit = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams lpUpd = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.05f);
        int gap = Eren.dp(activity, 14);
        lpExit.rightMargin = gap / 2;
        lpUpd.leftMargin = gap / 2;
        buttonRow.addView(exitBtn, lpExit);
        buttonRow.addView(updateBtn, lpUpd);

        exitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (callback != null) {
                    callback.onExit();
                }
                close(true);
            }
        });
        updateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUpdateUrl();
            }
        });

        root.setOnBackListener(new Overlay.OnBack() {
            @Override
            public void onBack() {
                if (cfg != null && cfg.dismissible) {
                    close(false);
                }
            }
        });
    }

    private TextView makeButton() {
        TextView b = new TextView(activity);
        b.setTypeface(tfButton);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setFocusable(true);
        b.setTextSize(22f);
        b.setPadding(Eren.dp(activity, 6), 0, Eren.dp(activity, 6), 0);
        b.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
                } else if (e.getAction() == MotionEvent.ACTION_UP
                        || e.getAction() == MotionEvent.ACTION_CANCEL) {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                }
                return false;
            }
        });
        return b;
    }

    /* ====================================================================
     *  Apply a config to the live views
     * ================================================================== */

    void apply(Eren.Cfg next) {
        if (next == null || closed || root == null) {
            return;
        }
        cfg = next;

        /* the admin can switch the dialog off at any time - hide it immediately */
        if (!cfg.enabled) {
            Eren.log("config says disabled - closing the dialog");
            close(false);
            return;
        }

        if (callback != null) {
            callback.onConfigLoaded(cfg.copy());
        }

        final int cardColor = Eren.color(cfg.cardColor, 0xFFFFFAFD);
        final int textColor = Eren.color(cfg.textColor, 0xFF353539);
        final int titleColor = Eren.color(cfg.titleColor, textColor);
        final int accent = Eren.color(cfg.accentColor, 0xFF70A0DF);
        final int featureColor = Eren.color(cfg.featureColor, textColor);
        final int borderColor = Eren.color(cfg.borderColor, 0xFF080808);
        final int buttonText = Eren.color(cfg.buttonTextColor, 0xFF050505);
        final int exitFill = Eren.color(cfg.exitButtonColor, 0xFFFFFFFF);

        /* scrim */
        scrim.setBackgroundColor(Eren.color(cfg.scrimColor, 0xB3000000));

        /* card */
        final float cardRadius = Eren.dp(activity, clamp(cfg.cornerRadiusDp, 0, 60));
        card.setBackground(Eren.rounded(cardColor, cardRadius, 0, 0));
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), cardRadius);
            }
        });

        /* hero height follows the configured ratio (landscape or portrait) */
        float ratio = cfg.imageRatio <= 0.2f ? (1200f / 675f) : cfg.imageRatio;
        int screenW = screenWidth();
        int cardW = screenW - Eren.dp(activity, 32);
        int heroH = (int) (cardW / ratio);
        int maxH = (int) (screenHeight() * 0.52f);
        heroH = Math.max(Eren.dp(activity, 140), Math.min(heroH, maxH));
        ViewGroup.LayoutParams hl = hero.getLayoutParams();
        if (hl != null && hl.height != heroH) {
            hl.height = heroH;
            hero.setLayoutParams(hl);
        }

        /* fade colour = card colour, so it blends with any theme */
        heroFade.setBackground(new LinearGradient(0, 0, 0, heroFade.getLayoutParams().height,
                new int[]{withAlpha(cardColor, 0), withAlpha(cardColor, 220), cardColor},
                new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));

        /* play badge */
        GradientDrawable badge = new GradientDrawable();
        badge.setShape(GradientDrawable.OVAL);
        badge.setColor(0x8A000000);
        badge.setStroke(Eren.dp(activity, 2), 0x66FFFFFF);
        playBadge.setBackground(badge);

        /* small title */
        smallTitleView.setText(cfg.smallTitle == null ? "" : cfg.smallTitle);
        smallTitleView.setTextColor(titleColor);
        smallTitleView.setTypeface(tfTitle);
        smallTitleView.setVisibility(cfg.smallTitle == null || cfg.smallTitle.length() == 0
                ? View.GONE : View.VISIBLE);

        /* brand: base text + accent tail in the accent colour */
        String flat = cfg.brand == null ? "" : cfg.brand;
        brandView.setTextSize(flat.length() > 9 ? 44f : (flat.length() > 7 ? 50f : 58f));
        brandView.setText(brandSpannable(titleColor, accent));
        brandView.setVisibility(flat.trim().length() == 0 ? View.GONE : View.VISIBLE);

        /* features */
        renderFeatures(featureColor);
        GradientDrawable feats = Eren.rounded(cardColor, Eren.dp(activity, 25), 0, 0);
        featuresCard.setBackground(feats);
        featuresCard.setElevation(Eren.dp(activity, 3));
        featuresCard.setVisibility(cfg.featuresCardVisible && cfg.features.size() > 0
                ? View.VISIBLE : View.GONE);

        /* buttons */
        int bh = Eren.dp(activity, clamp(cfg.buttonHeightDp, 40, 90));
        int bw = Eren.dp(activity, clamp(cfg.borderWidthDp, 0, 10));
        int br = Eren.dp(activity, clamp(cfg.buttonRadiusDp, 0, 40));

        exitBtn.setText(cfg.exitLabel);
        exitBtn.setTypeface(tfButton);
        exitBtn.setTextColor(buttonText);
        exitBtn.setBackground(ripple(Eren.rounded(exitFill, br, borderColor, bw), 0x33000000));
        exitBtn.setVisibility(cfg.exitEnabled ? View.VISIBLE : View.GONE);
        ViewGroup.LayoutParams elp = exitBtn.getLayoutParams();
        if (elp != null) {
            elp.height = bh;
            exitBtn.setLayoutParams(elp);
        }

        updateBtn.setText(cfg.updateLabel);
        updateBtn.setTypeface(tfButton);
        updateBtn.setTextColor(buttonText);
        updateBtn.setBackground(ripple(Eren.rounded(accent, br, borderColor, bw), 0x33FFFFFF));
        updateBtn.setVisibility(cfg.updateEnabled ? View.VISIBLE : View.GONE);
        ViewGroup.LayoutParams ulp = updateBtn.getLayoutParams();
        if (ulp != null) {
            ulp.height = bh;
            updateBtn.setLayoutParams(ulp);
        }

        buttonRow.setVisibility(cfg.exitEnabled || cfg.updateEnabled ? View.VISIBLE : View.GONE);
        if (exitBtn.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lpExit = (LinearLayout.LayoutParams) exitBtn.getLayoutParams();
            LinearLayout.LayoutParams lpUpd = (LinearLayout.LayoutParams) updateBtn.getLayoutParams();
            if (!cfg.exitEnabled) {
                lpUpd.weight = 1f;
            } else if (!cfg.updateEnabled) {
                lpExit.weight = 1f;
            }
        }

        /* media last (it needs the hero height to be final) */
        renderMedia();

        /* and finally fade the overlay in */
        reveal();
    }

    /** Brand text with the trailing accent part coloured (index.html .brand .blue). */
    private CharSequence brandSpannable(int base, int accent) {
        String brand = cfg.brand == null ? "" : cfg.brand;
        String acc = cfg.brandAccent == null ? "" : cfg.brandAccent;
        if (acc.length() > 0 && brand.length() > acc.length() && brand.endsWith(acc)) {
            android.text.SpannableString sp = new android.text.SpannableString(brand);
            sp.setSpan(new android.text.style.ForegroundColorSpan(accent),
                    brand.length() - acc.length(), brand.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new android.text.style.ForegroundColorSpan(base), 0,
                    brand.length() - acc.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return sp;
        }
        return brand;
    }

    private void renderFeatures(int featureColor) {
        featuresBox.removeAllViews();
        if (cfg.features.isEmpty()) {
            return;
        }
        for (int i = 0; i < cfg.features.size(); i++) {
            final String text = cfg.features.get(i);

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView sym = new TextView(activity);
            sym.setText("\u2756");                       /* diamond from index.html */
            sym.setTextSize(19f);
            sym.setTypeface(tfBody);
            sym.setTextColor(featureColor);
            sym.setIncludeFontPadding(false);
            row.addView(sym, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView body = new TextView(activity);
            body.setText(text);
            body.setTextSize(17f);
            body.setTypeface(tfBody);
            body.setTextColor(featureColor);
            body.setLineSpacing(Eren.dp(activity, 2), 1f);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            blp.leftMargin = Eren.dp(activity, 10);
            row.addView(body, blp);

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = i == 0 ? 0 : Eren.dp(activity, 10);
            featuresBox.addView(row, rlp);
        }
    }

    /** Wrap a shape in a ripple so taps feel alive (falls back to the shape). */
    private static android.graphics.drawable.Drawable ripple(GradientDrawable base, int rippleColor) {
        try {
            return new RippleDrawable(ColorStateList.valueOf(rippleColor), base, null);
        } catch (Throwable t) {
            return base;
        }
    }

    /* ====================================================================
     *  Media (image / video, portrait or landscape, URL or data URI)
     * ================================================================== */

    private void renderMedia() {
        if (heroImage == null) {
            return;
        }
        if ("video".equals(cfg.mediaType) && cfg.videoUrl != null && cfg.videoUrl.length() > 0) {
            renderVideo();
            return;
        }

        heroImage.setVisibility(View.VISIBLE);
        playBadge.setVisibility(View.GONE);
        heroChip.setVisibility(View.GONE);
        if (heroVideo != null) {
            try {
                heroVideo.stopPlayback();
            } catch (Throwable ignored) {
            }
            hero.removeView(heroVideo);
            heroVideo = null;
        }

        final String url = cfg.imageUrl;
        heroImage.setImageDrawable(null);
        heroImage.setBackground(heroPlaceholder());
        heroImage.setTag(url);
        if (url == null || url.trim().length() == 0) {
            return;
        }

        NET.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bmp = decode(url);
                if (bmp == null) {
                    return;
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (closed || heroImage == null) {
                            return;
                        }
                        if (url.equals(heroImage.getTag())) {
                            heroImage.setBackground(null);
                            heroImage.setImageBitmap(bmp);
                        }
                    }
                });
            }
        });
    }

    private void renderVideo() {
        heroChip.setText("VIDEO");
        GradientDrawable chip = Eren.rounded(0x8A000000, Eren.dp(activity, 14), 0, 0);
        heroChip.setBackground(chip);
        heroChip.setPadding(Eren.dp(activity, 10), Eren.dp(activity, 4),
                Eren.dp(activity, 10), Eren.dp(activity, 4));
        heroChip.setVisibility(View.VISIBLE);
        playBadge.setVisibility(View.VISIBLE);

        heroImage.setVisibility(View.VISIBLE);
        final String poster = cfg.videoThumbUrl == null ? "" : cfg.videoThumbUrl;
        heroImage.setTag(poster);
        heroImage.setImageDrawable(null);
        heroImage.setBackground(heroPlaceholder());
        if (poster.length() > 0) {
            NET.execute(new Runnable() {
                @Override
                public void run() {
                    final Bitmap bmp = decode(poster);
                    if (bmp == null) {
                        return;
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!closed && heroImage != null && poster.equals(heroImage.getTag())) {
                                heroImage.setBackground(null);
                                heroImage.setImageBitmap(bmp);
                            }
                        }
                    });
                }
            });
        }

        playBadge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playVideo();
            }
        });
    }

    private GradientDrawable heroPlaceholder() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1B1030, 0xFF3A2B6B, 0xFF8FB8F0});
    }

    private void playVideo() {
        if (heroVideo != null) {
            heroVideo.start();
            return;
        }
        if ("external".equalsIgnoreCase(cfg.videoPlayMode)) {
            openUrl(cfg.videoUrl);
            return;
        }
        try {
            heroVideo = new VideoView(activity);
            MediaController mc = new MediaController(activity);
            mc.setAnchorView(heroVideo);
            heroVideo.setMediaController(mc);
            heroVideo.setVideoURI(Uri.parse(cfg.videoUrl));
            hero.addView(heroVideo, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            heroVideo.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(android.media.MediaPlayer mp) {
                    playBadge.setVisibility(View.GONE);
                    heroImage.setVisibility(View.GONE);
                    heroVideo.start();
                }
            });
            heroVideo.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(android.media.MediaPlayer mp, int what, int extra) {
                    toast("Video cannot be played here - opening externally");
                    openUrl(cfg.videoUrl);
                    return true;
                }
            });
            heroVideo.requestFocus();
            heroVideo.start();
        } catch (Throwable t) {
            Eren.log("video failed: " + t);
            openUrl(cfg.videoUrl);
        }
    }

    /** Download + decode an image. Supports http(s), file paths and data URIs. */
    private Bitmap decode(String url) {
        try {
            byte[] raw;
            if (url.startsWith("data:")) {
                int comma = url.indexOf(',');
                if (comma < 0) {
                    return null;
                }
                String meta = url.substring(0, comma);
                String payload = url.substring(comma + 1);
                raw = meta.contains("base64")
                        ? Base64.decode(payload, Base64.DEFAULT)
                        : Uri.decode(payload).getBytes("UTF-8");
            } else if (url.startsWith("file://")) {
                raw = readStream(new FileInputStream(new File(Uri.parse(url).getPath())));
            } else if (url.startsWith("/")) {
                raw = readStream(new FileInputStream(new File(url)));
            } else {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(Eren.CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(Eren.READ_TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", "Eren/1.0 (Android)");
                conn.setInstanceFollowRedirects(true);
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    return null;
                }
                raw = readStream(conn.getInputStream());
                conn.disconnect();
            }
            if (raw == null || raw.length == 0) {
                return null;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap b = BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
            if (b != null && Math.max(b.getWidth(), b.getHeight()) > 2200) {
                float s = 2200f / Math.max(b.getWidth(), b.getHeight());
                b = Bitmap.createScaledBitmap(b, (int) (b.getWidth() * s),
                        (int) (b.getHeight() * s), true);
            }
            return b;
        } catch (Throwable t) {
            Eren.log("image decode failed: " + t);
            return null;
        }
    }

    private static byte[] readStream(InputStream in) throws Exception {
        if (in == null) {
            return null;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        in.close();
        bos.flush();
        byte[] out = bos.toByteArray();
        bos.close();
        return out;
    }

    /* ====================================================================
     *  Network / realtime
     * ================================================================== */

    private void refresh(final boolean first) {
        NET.execute(new Runnable() {
            @Override
            public void run() {
                final Eren.Cfg remote = ErenData.loadAndCache(activity, key);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (closed) {
                            return;
                        }
                        if (remote != null) {
                            attempts = 0;
                            apply(remote);
                            Eren.log("config loaded from database");
                            if (first) {
                                startRealtime();
                            }
                        } else {
                            attempts++;
                            if (callback != null && attempts == 1) {
                                callback.onError("App connect key not found in the database");
                            }
                            Eren.log("no config yet (attempt " + attempts + ")");
                            if (Eren.OFFLINE_FALLBACK && !revealed) {
                                Eren.log("database unreachable - showing the built-in dialog");
                                apply(Eren.Cfg.localDefault(key));
                            }
                            if (first) {
                                startRealtime();
                            }
                            /* the key may be created later - keep checking */
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (!closed) {
                                        refresh(false);
                                    }
                                }
                            }, Math.max(4000, Eren.POLL_INTERVAL_MS));
                        }
                    }
                });
            }
        });
    }

    private void startRealtime() {
        if (live == null) {
            live = new ErenData.Live(activity, key, new ErenData.Listener() {
                @Override
                public void onConfig(Eren.Cfg c) {
                    if (!closed) {
                        apply(c);
                        Eren.log("live update applied");
                    }
                }

                @Override
                public void onFailure(String m) {
                    /* Live reconnects on its own */
                }
            }, handler);
            live.start();
        }
        if (poller == null) {
            poller = new ErenData.Poller(activity, key, new ErenData.Listener() {
                @Override
                public void onConfig(Eren.Cfg c) {
                    if (!closed) {
                        apply(c);
                    }
                }

                @Override
                public void onFailure(String m) {
                }
            }, handler);
            poller.start();
        }
    }

    /* ====================================================================
     *  Actions
     * ================================================================== */

    private void openUpdateUrl() {
        String url = cfg == null ? null : cfg.updateUrl;
        if (url == null || url.trim().length() == 0 || "https://example.com".equals(url.trim())) {
            toast("Update link is not configured");
            return;
        }
        if (callback != null) {
            callback.onUpdate(url);
        }
        openUrl(url);
    }

    private void openUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(i);
        } catch (Throwable t) {
            toast("No app can open this link");
        }
    }

    private void toast(String message) {
        try {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    /* ====================================================================
     *  Close
     * ================================================================== */

    void close(final boolean userExit) {
        if (closed) {
            return;
        }
        closed = true;
        if (live != null) {
            live.stop();
            live = null;
        }
        if (poller != null) {
            poller.stop();
            poller = null;
        }
        if (heroVideo != null) {
            try {
                heroVideo.stopPlayback();
            } catch (Throwable ignored) {
            }
        }
        if (root != null && root.getParent() instanceof ViewGroup) {
            final ViewGroup parent = (ViewGroup) root.getParent();
            if (userExit) {
                try {
                    parent.removeView(root);
                } catch (Throwable ignored) {
                }
            } else {
                root.animate().alpha(0f).setDuration(160).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            parent.removeView(root);
                        } catch (Throwable ignored) {
                        }
                    }
                }).start();
            }
        }
        if (CURRENT == this) {
            CURRENT = null;
        }
        if (callback != null) {
            callback.onDismiss();
        }
        if (userExit) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (Eren.KILL_PROCESS_ON_EXIT) {
                            android.os.Process.killProcess(android.os.Process.myPid());
                            System.exit(0);
                        } else {
                            activity.finishAffinity();
                        }
                    } catch (Throwable t) {
                        try {
                            activity.finish();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }, 180);
        }
    }

    /* ====================================================================
     *  Tiny helpers
     * ================================================================== */

    private int screenWidth() {
        DisplayMetrics m = activity.getResources().getDisplayMetrics();
        return m.widthPixels;
    }

    private int screenHeight() {
        DisplayMetrics m = activity.getResources().getDisplayMetrics();
        return m.heightPixels;
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /* ====================================================================
     *  Overlay container  (catches the BACK key, no window tricks needed)
     * ================================================================== */

    private static final class Overlay extends FrameLayout {

        interface OnBack {
            void onBack();
        }

        private OnBack onBack;

        Overlay(android.content.Context c) {
            super(c);
        }

        void setOnBackListener(OnBack b) {
            onBack = b;
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (e.getAction() == KeyEvent.ACTION_UP && onBack != null) {
                    onBack.onBack();
                }
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
    }
}
