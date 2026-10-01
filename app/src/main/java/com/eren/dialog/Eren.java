package com.eren.dialog;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ============================================================================
 *  EREN  -  Remote Update Dialog  (single file / drop-in)
 * ============================================================================
 *
 *  Host app usage:
 *
 *      Eren.show(this);                       // uses CONNECT_KEY below
 *      Eren.show(this, "MM-XXX-XXX-XXX-ST");  // explicit key
 *
 *  Everything (UI, network, cache, realtime sync) lives inside this one class,
 *  so the compiled smali can be dropped into any modded APK. No resources and
 *  no third-party libraries are required - only android.* and org.json.
 *
 *  Realtime: Firebase RTDB REST + Server-Sent-Events stream, with polling
 *  fallback, plus a local cache so the dialog paints instantly on cold start.
 * ============================================================================
 */
public final class Eren {

    private Eren() {
    }

    /* ========================================================================
     *  MT MANAGER EDIT ZONE  --  edit these strings inside Eren.smali
     * ------------------------------------------------------------------------
     *  These fields are intentionally NOT "final". A non-final static String
     *  is stored exactly once, inside the static constructor <clinit> of
     *  Eren.smali, and is read back with sget-object everywhere else. That
     *  means you only have to change ONE string in MT Manager.
     * ====================================================================== */

    /** App Connect Key - must match the key created in Eren Admin panel. */
    public static String CONNECT_KEY = "MM-XXX-XXX-XXX-ST";

    /** Firebase Realtime Database URL. No trailing slash needed. */
    public static String DATABASE_URL = "https://your-project-default-rtdb.firebaseio.com";

    /** Root node written by the Eren Admin panel. */
    public static String REMOTE_ROOT = "apps";

    /** Manifest line shown in Eren Admin so the user can copy it. */
    public static String INTERNET_PERMISSION = "<uses-permission android:name=\"android.permission.INTERNET\"/>";

    /** Show the built-in dialog when the database cannot be reached. */
    public static boolean OFFLINE_FALLBACK = true;

    /** Close the whole process when the user taps EXIT (false = just close activity). */
    public static boolean KILL_PROCESS_ON_EXIT = false;

    /** Log to logcat with tag "Eren". */
    public static boolean DEBUG_LOG = true;

    /** How often to re-read the database when the live stream is unavailable. */
    public static int POLL_INTERVAL_MS = 15000;

    /** Network timeouts. */
    public static int CONNECT_TIMEOUT_MS = 8000;
    public static int READ_TIMEOUT_MS = 12000;

    /* ------------------------------------------------------------------------
     *  Offline / first-run look (same layout as the original index.html)
     * ---------------------------------------------------------------------- */

    public static String LOCAL_SMALL_TITLE = "UPDATE";
    public static String LOCAL_BRAND = "MODMASE";
    public static String LOCAL_BRAND_ACCENT = "MASE";
    public static String LOCAL_IMAGE_URL = "";
    public static String LOCAL_UPDATE_URL = "https://example.com";
    public static String LOCAL_FEATURE_ONE = "New Features Available";
    public static String LOCAL_FEATURE_TWO = "Previous Bug Fixed";
    public static String LOCAL_EXIT_LABEL = "EXIT";
    public static String LOCAL_UPDATE_LABEL = "UPDATE";

    /* Built-in palette (mirrors index.html) */
    public static String LOCAL_BG_COLOR = "#FFD0C8";
    public static String LOCAL_CARD_COLOR = "#FFFAFD";
    public static String LOCAL_TEXT_COLOR = "#353539";
    public static String LOCAL_FEATURE_COLOR = "#37383C";
    public static String LOCAL_ACCENT_COLOR = "#70A0DF";
    public static String LOCAL_BORDER_COLOR = "#080808";
    public static String LOCAL_BUTTON_TEXT_COLOR = "#050505";
    public static String LOCAL_EXIT_BUTTON_COLOR = "#FFFFFF";
    public static String LOCAL_SCRIM_COLOR = "#B3000000";

    /* Assets fonts. Drop the .ttf files in assets/fonts/ of the HOST app.
     * Missing files are ignored and a system font is used instead. */
    public static String FONT_BRAND = "fonts/Oswald-Medium.ttf";
    public static String FONT_TITLE = "fonts/Audiowide-Regular.ttf";
    public static String FONT_BUTTON = "fonts/Audiowide-Regular.ttf";
    public static String FONT_BODY = "fonts/Poppins-SemiBold.ttf";

    public static final String TAG = "Eren";
    private static final String PREFS = "eren_dialog_prefs";

    /* ========================================================================
     *  Public API
     * ====================================================================== */

    /** Callback so the host app can react to the dialog buttons. */
    public interface Callback {
        void onConfigLoaded(Cfg cfg);

        void onExit();

        void onUpdate(String url);

        void onDismiss();

        void onError(String message);
    }

    /** Convenience: show the dialog for the compiled-in CONNECT_KEY. */
    public static void show(Activity activity) {
        show(activity, CONNECT_KEY, null);
    }

    /** Show the dialog for a specific app connect key. */
    public static void show(Activity activity, String connectKey) {
        show(activity, connectKey, null);
    }

    /** Full entry point. */
    public static void show(final Activity activity, String connectKey, final Callback callback) {
        if (activity == null) {
            log("show() called with a null Activity");
            return;
        }
        if (isShowing()) {
            log("show() ignored - dialog is already visible");
            return;
        }
        if (connectKey == null || connectKey.trim().length() == 0) {
            connectKey = CONNECT_KEY;
        }
        final String key = connectKey.trim();

        final ErenDialog dialog = new ErenDialog(activity, key, callback);
        dialog.present();
    }

    /** True while an Eren dialog is on screen. */
    public static boolean isShowing() {
        return ErenDialog.CURRENT != null;
    }

    /** Programmatically close the dialog. */
    public static void dismiss() {
        final ErenDialog d = ErenDialog.CURRENT;
        if (d == null) {
            return;
        }
        d.handler.post(new Runnable() {
            @Override
            public void run() {
                d.close(false);
            }
        });
    }

    /** Forget the cached copy of an app config. */
    public static void clearCache(Context context, String connectKey) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove("cfg_" + connectKey)
                .apply();
    }

    /* ========================================================================
     *  Config model - the exact JSON contract shared with Eren Admin
     * ====================================================================== */

    public static final class Cfg {

        public boolean enabled = true;
        public boolean dismissible = true;
        public boolean showOnLaunch = true;

        public String key = "";
        public String appName = "";
        public String smallTitle = LOCAL_SMALL_TITLE;
        public String brand = LOCAL_BRAND;
        public String brandAccent = LOCAL_BRAND_ACCENT;
        public String shortDetail = "";

        public String mediaType = "image";     // image | video
        public String imageUrl = "";
        public String videoUrl = "";
        public String videoThumbUrl = "";
        public String videoPlayMode = "inline"; // inline | external
        public float imageRatio = 1200f / 675f;

        public String updateUrl = "";
        public String exitLabel = LOCAL_EXIT_LABEL;
        public String updateLabel = LOCAL_UPDATE_LABEL;
        public boolean exitEnabled = true;
        public boolean updateEnabled = true;
        public boolean featuresCardVisible = true;

        public final List<String> features = new ArrayList<String>();

        public String scrimColor = LOCAL_SCRIM_COLOR;
        public String cardColor = LOCAL_CARD_COLOR;
        public String textColor = LOCAL_TEXT_COLOR;
        public String featureColor = LOCAL_FEATURE_COLOR;
        public String accentColor = LOCAL_ACCENT_COLOR;
        public String borderColor = LOCAL_BORDER_COLOR;
        public String buttonTextColor = LOCAL_BUTTON_TEXT_COLOR;
        public String exitButtonColor = LOCAL_EXIT_BUTTON_COLOR;
        public String titleColor = LOCAL_TEXT_COLOR;

        public int cornerRadiusDp = 36;
        public int buttonRadiusDp = 22;
        public int buttonHeightDp = 63;
        public int borderWidthDp = 4;

        public String fontBrand = FONT_BRAND;
        public String fontTitle = FONT_TITLE;
        public String fontButton = FONT_BUTTON;
        public String fontBody = FONT_BODY;

        public int latestVersionCode = 0;
        public long updatedAt = 0L;

        public Cfg copy() {
            Cfg c = new Cfg();
            c.enabled = enabled;
            c.dismissible = dismissible;
            c.showOnLaunch = showOnLaunch;
            c.key = key;
            c.appName = appName;
            c.smallTitle = smallTitle;
            c.brand = brand;
            c.brandAccent = brandAccent;
            c.shortDetail = shortDetail;
            c.mediaType = mediaType;
            c.imageUrl = imageUrl;
            c.videoUrl = videoUrl;
            c.videoThumbUrl = videoThumbUrl;
            c.videoPlayMode = videoPlayMode;
            c.imageRatio = imageRatio;
            c.updateUrl = updateUrl;
            c.exitLabel = exitLabel;
            c.updateLabel = updateLabel;
            c.exitEnabled = exitEnabled;
            c.updateEnabled = updateEnabled;
            c.featuresCardVisible = featuresCardVisible;
            c.features.clear();
            c.features.addAll(features);
            c.scrimColor = scrimColor;
            c.cardColor = cardColor;
            c.textColor = textColor;
            c.featureColor = featureColor;
            c.accentColor = accentColor;
            c.borderColor = borderColor;
            c.buttonTextColor = buttonTextColor;
            c.exitButtonColor = exitButtonColor;
            c.titleColor = titleColor;
            c.cornerRadiusDp = cornerRadiusDp;
            c.buttonRadiusDp = buttonRadiusDp;
            c.buttonHeightDp = buttonHeightDp;
            c.borderWidthDp = borderWidthDp;
            c.fontBrand = fontBrand;
            c.fontTitle = fontTitle;
            c.fontButton = fontButton;
            c.fontBody = fontBody;
            c.latestVersionCode = latestVersionCode;
            c.updatedAt = updatedAt;
            return c;
        }

        /** Built-in default that reproduces the original index.html dialog. */
        public static Cfg localDefault(String connectKey) {
            Cfg c = new Cfg();
            c.key = connectKey;
            c.appName = LOCAL_BRAND;
            c.smallTitle = LOCAL_SMALL_TITLE;
            c.brand = LOCAL_BRAND;
            c.brandAccent = LOCAL_BRAND_ACCENT;
            c.imageUrl = LOCAL_IMAGE_URL;
            c.updateUrl = LOCAL_UPDATE_URL;
            c.features.add(LOCAL_FEATURE_ONE);
            c.features.add(LOCAL_FEATURE_TWO);
            c.exitLabel = LOCAL_EXIT_LABEL;
            c.updateLabel = LOCAL_UPDATE_LABEL;
            return c;
        }

        public static Cfg fromJson(JSONObject o, String connectKey) {
            Cfg c = localDefault(connectKey);
            if (o == null) {
                return c;
            }
            c.key = connectKey;
            c.enabled = o.optBoolean("enabled", true);
            c.dismissible = o.optBoolean("dismissible", true);
            c.showOnLaunch = o.optBoolean("showOnLaunch", true);
            c.appName = o.optString("appName", c.appName);
            c.shortDetail = o.optString("shortDetail", c.shortDetail);
            c.smallTitle = o.optString("smallTitle", c.smallTitle);
            c.brand = o.optString("brand", o.optString("name", c.brand));
            c.brandAccent = o.optString("brandAccent", c.brandAccent);
            c.mediaType = o.optString("mediaType", c.mediaType);
            c.imageUrl = o.optString("imageUrl", c.imageUrl);
            c.videoUrl = o.optString("videoUrl", c.videoUrl);
            c.videoThumbUrl = o.optString("videoThumbUrl", c.videoThumbUrl);
            c.videoPlayMode = o.optString("videoPlayMode", c.videoPlayMode);
            double ratio = o.optDouble("imageRatio", c.imageRatio);
            if (ratio > 0.2d && ratio < 6.0d) {
                c.imageRatio = (float) ratio;
            }
            c.updateUrl = o.optString("updateUrl", c.updateUrl);
            c.exitLabel = o.optString("exitLabel", c.exitLabel);
            c.updateLabel = o.optString("updateLabel", c.updateLabel);
            c.exitEnabled = o.optBoolean("exitEnabled", true);
            c.updateEnabled = o.optBoolean("updateEnabled", true);
            c.featuresCardVisible = o.optBoolean("featuresCardVisible", true);
            c.cornerRadiusDp = o.optInt("cornerRadius", c.cornerRadiusDp);
            c.buttonRadiusDp = o.optInt("buttonRadius", c.buttonRadiusDp);
            c.buttonHeightDp = o.optInt("buttonHeight", c.buttonHeightDp);
            c.borderWidthDp = o.optInt("borderWidth", c.borderWidthDp);
            c.latestVersionCode = o.optInt("latestVersionCode", 0);
            c.updatedAt = o.optLong("updatedAt", 0L);

            JSONArray arr = o.optJSONArray("features");
            if (arr != null) {
                c.features.clear();
                for (int i = 0; i < arr.length(); i++) {
                    String f = arr.optString(i, "");
                    if (f != null && f.trim().length() > 0) {
                        c.features.add(f);
                    }
                }
            }

            JSONObject col = o.optJSONObject("colors");
            if (col != null) {
                c.scrimColor = col.optString("scrim", c.scrimColor);
                c.cardColor = col.optString("card", c.cardColor);
                c.textColor = col.optString("text", c.textColor);
                c.titleColor = col.optString("title", c.textColor);
                c.featureColor = col.optString("featureText", c.featureColor);
                c.accentColor = col.optString("accent", c.accentColor);
                c.borderColor = col.optString("buttonBorder", c.borderColor);
                c.buttonTextColor = col.optString("buttonText", c.buttonTextColor);
                c.exitButtonColor = col.optString("exitButton", c.exitButtonColor);
            }

            JSONObject fon = o.optJSONObject("fonts");
            if (fon != null) {
                c.fontBrand = fon.optString("brand", c.fontBrand);
                c.fontTitle = fon.optString("title", c.fontTitle);
                c.fontButton = fon.optString("button", c.fontButton);
                c.fontBody = fon.optString("body", c.fontBody);
            }

            if (c.mediaType == null || c.mediaType.length() == 0) {
                c.mediaType = "image";
            }
            c.mediaType = c.mediaType.toLowerCase(Locale.US);
            if ("video".equals(c.mediaType)) {
                if (c.videoUrl == null || c.videoUrl.length() == 0) {
                    c.videoUrl = c.imageUrl;
                }
            } else {
                if (c.imageUrl == null || c.imageUrl.length() == 0) {
                    c.imageUrl = c.videoUrl;
                }
            }
            return c;
        }
    }

    /* ========================================================================
     *  Small helpers
     * ====================================================================== */

    static void log(String message) {
        if (DEBUG_LOG) {
            Log.d(TAG, message);
        }
    }

    static int dp(Context c, float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                c.getResources().getDisplayMetrics());
    }

    static int sp(Context c, float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                c.getResources().getDisplayMetrics());
    }

    static int color(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String v = value.trim();
        if (v.length() == 0) {
            return fallback;
        }
        try {
            if (v.charAt(0) != '#') {
                v = "#" + v;
            }
            if (v.length() == 7) {
                v = "#FF" + v.substring(1);
            }
            return Color.parseColor(v);
        } catch (Throwable t) {
            return fallback;
        }
    }

    static Typeface font(Context context, String asset, int style) {
        if (asset != null && asset.trim().length() > 0) {
            try {
                return Typeface.createFromAsset(context.getAssets(), asset.trim());
            } catch (Throwable ignored) {
                log("font not found in assets: " + asset + " (system font used)");
            }
        }
        return Typeface.create("sans-serif", style);
    }

    static GradientDrawable rounded(int fill, float radiusPx, int strokeColor, int strokePx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(radiusPx);
        if (strokePx > 0) {
            d.setStroke(strokePx, strokeColor);
        }
        return d;
    }

    static String encodeKey(String key) {
        try {
            return URLEncoder.encode(key, "UTF-8");
        } catch (Throwable t) {
            return key;
        }
    }
}
