package com.eren.admin;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** Firebase Realtime Database over plain REST (no SDK, no google-services.json). */
final class Db {
    static String base = "";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface CB { void done(boolean ok, String body); }

    static String normalize(String u) {
        u = u.trim();
        if (u.length() == 0) return "";
        if (!u.startsWith("http")) u = "https://" + u;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (u.endsWith(".json")) u = u.substring(0, u.length() - 5);
        return u;
    }

    static void get(String path, CB cb) { req("GET", path, null, cb); }
    static void put(String path, String json, CB cb) { req("PUT", path, json, cb); }
    static void patch(String path, String json, CB cb) { req("PATCH", path, json, cb); }
    static void del(String path, CB cb) { req("DELETE", path, null, cb); }

    private static void req(final String method, final String path, final String body, final CB cb) {
        new Thread(new Runnable() {
            public void run() {
                boolean ok = false;
                String res = "";
                try {
                    URL u = new URL(base + path + ".json");
                    HttpURLConnection c = (HttpURLConnection) u.openConnection();
                    c.setConnectTimeout(12000);
                    c.setReadTimeout(20000);
                    if (method.equals("PATCH")) {
                        c.setRequestMethod("POST");
                        c.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                    } else {
                        c.setRequestMethod(method);
                    }
                    if (body != null) {
                        c.setDoOutput(true);
                        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                        OutputStream os = c.getOutputStream();
                        os.write(body.getBytes("UTF-8"));
                        os.close();
                    }
                    int code = c.getResponseCode();
                    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    if (in != null) {
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        byte[] b = new byte[8192];
                        int n;
                        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                        in.close();
                        res = bo.toString("UTF-8");
                    }
                    ok = code >= 200 && code < 300;
                    if (!ok && res.length() == 0) res = "HTTP " + code;
                    c.disconnect();
                } catch (Throwable t) {
                    res = String.valueOf(t.getMessage());
                }
                final boolean fo = ok;
                final String fr = res;
                MAIN.post(new Runnable() { public void run() { cb.done(fo, fr); } });
            }
        }).start();
    }

    static JSONObject obj(String s) {
        try {
            if (s == null || s.trim().equals("null") || s.trim().length() == 0) return null;
            return new JSONObject(s);
        } catch (Throwable t) { return null; }
    }

    static String sha(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(("eren:" + s).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Throwable t) { return s; }
    }

    static String genKey() {
        String cs = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder("MM");
        for (int g = 0; g < 3; g++) {
            sb.append('-');
            for (int i = 0; i < 3; i++) sb.append(cs.charAt(r.nextInt(cs.length())));
        }
        return sb.append("-ST").toString();
    }
}
