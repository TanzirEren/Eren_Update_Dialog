package com.eren.dialog;

import android.content.Context;
import android.os.Handler;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * ============================================================================
 *  EREN  -  data layer for the update dialog
 * ============================================================================
 *
 *  Reads  <DATABASE_URL>/<REMOTE_ROOT>/<CONNECT_KEY>.json  from Firebase RTDB.
 *
 *   - loadSync()   : one shot HTTP GET
 *   - Live         : Server-Sent-Events stream (realtime push)
 *   - Poller       : interval based refresh (used when SSE is not available)
 *   - cache        : last good JSON on disk, painted instantly at cold start
 * ============================================================================
 */
final class ErenData {

    private ErenData() {
    }

    interface Listener {
        void onConfig(Eren.Cfg cfg);

        void onFailure(String message);
    }

    /* ========================================================================
     *  URLs
     * ====================================================================== */

    static String nodeUrl(String connectKey) {
        String base = Eren.DATABASE_URL == null ? "" : Eren.DATABASE_URL.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String root = Eren.REMOTE_ROOT == null || Eren.REMOTE_ROOT.length() == 0
                ? "apps" : Eren.REMOTE_ROOT.trim();
        return base + "/" + root + "/" + Eren.encodeKey(connectKey) + ".json";
    }

    static String streamUrl(String connectKey) {
        return nodeUrl(connectKey) + "?stream=true";
    }

    /* ========================================================================
     *  One shot load
     * ====================================================================== */

    static Eren.Cfg loadSync(String connectKey) {
        try {
            String body = get(nodeUrl(connectKey), false);
            if (body == null || body.trim().length() == 0 || "null".equals(body.trim())) {
                Eren.log("no data for key " + connectKey);
                return null;
            }
            JSONObject o = new JSONObject(body);
            return Eren.Cfg.fromJson(o, connectKey);
        } catch (Throwable t) {
            Eren.log("loadSync failed: " + t);
            return null;
        }
    }

    static String get(String url, boolean stream) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(Eren.CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(stream ? 0 : Eren.READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", stream ? "text/event-stream" : "application/json");
            conn.setRequestProperty("Cache-Control", "no-cache");
            conn.setRequestProperty("User-Agent", "Eren/1.0 (Android)");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                Eren.log("HTTP " + code + " for " + url);
                return null;
            }
            return readAll(conn.getInputStream());
        } finally {
            if (conn != null && !stream) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) {
            return null;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        bos.flush();
        String out = new String(bos.toByteArray(), "UTF-8");
        bos.close();
        return out;
    }

    /* ========================================================================
     *  Disk cache  (instant paint + offline resilience)
     * ====================================================================== */

    private static File cacheFile(Context c, String connectKey) {
        File dir = new File(c.getCacheDir(), "eren");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String safe = connectKey.replaceAll("[^A-Za-z0-9._-]", "_");
        return new File(dir, "cfg_" + safe + ".json");
    }

    static void saveCache(Context c, String connectKey, String rawJson) {
        if (c == null || rawJson == null) {
            return;
        }
        try {
            FileOutputStream fos = new FileOutputStream(cacheFile(c, connectKey));
            fos.write(rawJson.getBytes("UTF-8"));
            fos.flush();
            fos.close();
        } catch (Throwable t) {
            Eren.log("cache write failed: " + t);
        }
    }

    static Eren.Cfg loadCache(Context c, String connectKey) {
        if (c == null) {
            return null;
        }
        File f = cacheFile(c, connectKey);
        if (!f.exists()) {
            return null;
        }
        try {
            FileInputStream fis = new FileInputStream(f);
            String body = readAll(fis);
            fis.close();
            if (body == null || body.trim().length() == 0) {
                return null;
            }
            return Eren.Cfg.fromJson(new JSONObject(body), connectKey);
        } catch (Throwable t) {
            Eren.log("cache read failed: " + t);
            return null;
        }
    }

    static long cacheAge(Context c, String connectKey) {
        if (c == null) {
            return -1L;
        }
        File f = cacheFile(c, connectKey);
        return f.exists() ? (System.currentTimeMillis() - f.lastModified()) : -1L;
    }

    /* ========================================================================
     *  Load + cache in one call
     * ====================================================================== */

    static Eren.Cfg loadAndCache(Context c, String connectKey) {
        String raw = null;
        try {
            raw = get(nodeUrl(connectKey), false);
        } catch (Throwable t) {
            Eren.log("network read failed: " + t);
        }
        if (raw == null || raw.trim().length() == 0 || "null".equals(raw.trim())) {
            return null;
        }
        saveCache(c, connectKey, raw);
        try {
            return Eren.Cfg.fromJson(new JSONObject(raw), connectKey);
        } catch (Throwable t) {
            Eren.log("parse failed: " + t);
            return null;
        }
    }

    /* ========================================================================
     *  Realtime stream  (Firebase RTDB REST streaming)
     * ====================================================================== */

    static final class Live {

        private final Context ctx;
        private final String key;
        private final Listener listener;
        private final Handler handler;

        private volatile boolean running;
        private Thread thread;
        private HttpURLConnection conn;
        private long lastDeliver;

        Live(Context ctx, String key, Listener listener, Handler handler) {
            this.ctx = ctx;
            this.key = key;
            this.listener = listener;
            this.handler = handler;
        }

        void start() {
            if (running) {
                return;
            }
            running = true;
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    loop();
                }
            }, "eren-live");
            thread.setDaemon(true);
            thread.start();
        }

        void stop() {
            running = false;
            final HttpURLConnection c = conn;
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Throwable ignored) {
                }
            }
            if (thread != null) {
                thread.interrupt();
            }
        }

        private void loop() {
            int failures = 0;
            while (running) {
                try {
                    conn = (HttpURLConnection) new URL(streamUrl(key)).openConnection();
                    conn.setConnectTimeout(Eren.CONNECT_TIMEOUT_MS);
                    conn.setReadTimeout(0);
                    conn.setRequestProperty("Accept", "text/event-stream");
                    conn.setRequestProperty("Cache-Control", "no-cache");
                    conn.setRequestProperty("User-Agent", "Eren/1.0 (Android)");
                    int code = conn.getResponseCode();
                    if (code < 200 || code >= 300) {
                        throw new IllegalStateException("HTTP " + code);
                    }
                    failures = 0;
                    Eren.log("realtime stream connected");
                    BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    String line;
                    boolean touched = false;
                    while (running && (line = r.readLine()) != null) {
                        if (line.length() == 0) {
                            if (touched) {
                                touched = false;
                                deliver();
                            }
                            continue;
                        }
                        if (line.startsWith("data:")) {
                            touched = true;
                        }
                    }
                    try {
                        r.close();
                    } catch (Throwable ignored) {
                    }
                    if (running) {
                        deliver();
                    }
                } catch (Throwable t) {
                    if (!running) {
                        break;
                    }
                    failures++;
                    Eren.log("stream dropped (" + t + ") retry #" + failures);
                    try {
                        Thread.sleep(Math.min(30000L, 1500L * failures));
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
            Eren.log("realtime stream stopped");
        }

        private void deliver() {
            long now = System.currentTimeMillis();
            if (now - lastDeliver < 120L) {
                return;
            }
            lastDeliver = now;
            final Eren.Cfg cfg = loadAndCache(ctx, key);
            if (cfg == null) {
                return;
            }
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (listener != null) {
                        listener.onConfig(cfg);
                    }
                }
            });
        }
    }

    /* ========================================================================
     *  Poller fallback
     * ====================================================================== */

    static final class Poller {

        private final Context ctx;
        private final String key;
        private final Listener listener;
        private final Handler handler;
        private final Runnable task = new Runnable() {
            @Override
            public void run() {
                if (!running) {
                    return;
                }
                final Eren.Cfg cfg = loadAndCache(ctx, key);
                if (cfg != null && listener != null) {
                    listener.onConfig(cfg);
                }
                if (running) {
                    handler.postDelayed(this, Math.max(5000, Eren.POLL_INTERVAL_MS));
                }
            }
        };
        private volatile boolean running;

        Poller(Context ctx, String key, Listener listener, Handler handler) {
            this.ctx = ctx;
            this.key = key;
            this.listener = listener;
            this.handler = handler;
        }

        void start() {
            running = true;
            handler.post(task);
        }

        void stop() {
            running = false;
            handler.removeCallbacks(task);
        }
    }
}
