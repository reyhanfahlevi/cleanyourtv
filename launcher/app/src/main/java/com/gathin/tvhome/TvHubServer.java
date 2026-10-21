package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Embedded REST API and mDNS discovery server for tvhub management.
 * Runs on port 8080 and announces itself via _tvhub._tcp. mDNS.
 */
public class TvHubServer extends NanoHTTPD {

    private static final String TAG = "TvHubServer";
    public static final int PORT = 8080;
    public static final String SERVICE_TYPE = "_tvhub._tcp.";

    private volatile MainActivity activity;
    private NsdManager nsdManager;
    private NsdManager.RegistrationListener nsdListener;
    private boolean isRegistered = false;

    public TvHubServer(MainActivity activity) {
        super("0.0.0.0", PORT);
        this.activity = activity;
    }

    public void updateActivity(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void start() throws IOException {
        super.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        Log.i(TAG, "TvHubServer HTTP listening on port " + PORT);
        registerMdns();
    }

    @Override
    public void stop() {
        unregisterMdns();
        super.stop();
        Log.i(TAG, "TvHubServer stopped");
    }

    private void registerMdns() {
        try {
            nsdManager = (NsdManager) activity.getSystemService(Context.NSD_SERVICE);
            if (nsdManager == null) return;

            String serviceName = "tvhub-" + Build.MODEL.replaceAll("[^a-zA-Z0-9_-]", "-");
            NsdServiceInfo info = new NsdServiceInfo();
            info.setServiceName(serviceName);
            info.setServiceType(SERVICE_TYPE);
            info.setPort(PORT);

            nsdListener = new NsdManager.RegistrationListener() {
                @Override
                public void onServiceRegistered(NsdServiceInfo nsdServiceInfo) {
                    isRegistered = true;
                    Log.i(TAG, "mDNS Service registered: " + nsdServiceInfo.getServiceName());
                }

                @Override
                public void onRegistrationFailed(NsdServiceInfo nsdServiceInfo, int errorCode) {
                    Log.w(TAG, "mDNS Registration failed: " + errorCode);
                }

                @Override
                public void onServiceUnregistered(NsdServiceInfo nsdServiceInfo) {
                    isRegistered = false;
                    Log.i(TAG, "mDNS Service unregistered");
                }

                @Override
                public void onUnregistrationFailed(NsdServiceInfo nsdServiceInfo, int errorCode) {
                    Log.w(TAG, "mDNS Unregistration failed: " + errorCode);
                }
            };

            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, nsdListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register mDNS service", e);
        }
    }

    private void unregisterMdns() {
        if (nsdManager != null && nsdListener != null && isRegistered) {
            try {
                nsdManager.unregisterService(nsdListener);
            } catch (Exception e) {
                Log.w(TAG, "Error unregistering mDNS", e);
            }
            isRegistered = false;
        }
    }

    private Response addCors(Response res) {
        res.addHeader("Access-Control-Allow-Origin", "*");
        res.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        res.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        return res;
    }

    private Response jsonResponse(Response.Status status, Object jsonObject) {
        Response res = newFixedLengthResponse(status, "application/json", jsonObject.toString());
        return addCors(res);
    }

    private Response errorResponse(Response.Status status, String message) {
        try {
            JSONObject err = new JSONObject();
            err.put("error", message);
            return jsonResponse(status, err);
        } catch (Exception e) {
            return addCors(newFixedLengthResponse(status, "text/plain", message));
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri();

        if (Method.OPTIONS.equals(method)) {
            Response res = newFixedLengthResponse(Response.Status.OK, "text/plain", null);
            return addCors(res);
        }

        try {
            if ("/api/status".equals(uri) && Method.GET.equals(method)) {
                return handleStatus();
            } else if ("/api/config".equals(uri) && Method.GET.equals(method)) {
                return handleGetConfig();
            } else if ("/api/config".equals(uri) && Method.POST.equals(method)) {
                return handlePostConfig(session);
            } else if ("/api/wallpapers".equals(uri) && Method.GET.equals(method)) {
                return handleGetWallpapers();
            } else if ("/api/wallpaper/file".equals(uri) && Method.GET.equals(method)) {
                return handleGetWallpaperFile(session);
            } else if ("/api/wallpaper/active".equals(uri) && (Method.POST.equals(method) || Method.PUT.equals(method))) {
                return handleActiveWallpaper(session);
            } else if ("/api/wallpaper".equals(uri) && Method.POST.equals(method)) {
                return handlePostWallpaper(session);
            } else if ("/api/wallpaper".equals(uri) && Method.DELETE.equals(method)) {
                return handleDeleteWallpaper(session);
            } else if ("/api/apps".equals(uri) && Method.GET.equals(method)) {
                return handleGetApps();
            } else if ("/api/action/launch".equals(uri) && Method.POST.equals(method)) {
                return handleActionLaunch(session);
            } else if ("/api/action/key".equals(uri) && Method.POST.equals(method)) {
                return handleActionKey(session);
            } else {
                return errorResponse(Response.Status.NOT_FOUND, "Endpoint not found: " + uri);
            }
        } catch (Exception e) {
            Log.e(TAG, "Request failed: " + uri, e);
            return errorResponse(Response.Status.INTERNAL_ERROR, e.getMessage());
        }
    }

    private Response handleStatus() throws Exception {
        JSONObject status = new JSONObject();
        status.put("id", Build.MODEL.toLowerCase().replaceAll("[^a-z0-9]", "-") + "-" + Build.DEVICE);
        status.put("model", Build.MODEL);
        status.put("device", Build.DEVICE);
        status.put("manufacturer", Build.MANUFACTURER);
        status.put("android_version", Build.VERSION.RELEASE);
        status.put("sdk", Build.VERSION.SDK_INT);
        status.put("uptime_ms", SystemClock.uptimeMillis());

        // Memory
        ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            JSONObject mem = new JSONObject();
            mem.put("total_mb", mi.totalMem / (1024 * 1024));
            mem.put("available_mb", mi.availMem / (1024 * 1024));
            mem.put("low_memory", mi.lowMemory);
            status.put("memory", mem);
        }

        // Storage
        File dataDir = Environment.getDataDirectory();
        StatFs stat = new StatFs(dataDir.getPath());
        long freeBytes = stat.getAvailableBytes();
        long totalBytes = stat.getTotalBytes();
        JSONObject storage = new JSONObject();
        storage.put("total_mb", totalBytes / (1024 * 1024));
        storage.put("free_mb", freeBytes / (1024 * 1024));
        status.put("storage", storage);

        // Wallpaper info
        File themeDir = Config.dir(activity);
        File wallpapersDir = themeDir != null ? new File(themeDir, "wallpapers") : null;
        int wallpaperCount = 0;
        if (wallpapersDir != null && wallpapersDir.exists() && wallpapersDir.isDirectory()) {
            File[] files = wallpapersDir.listFiles();
            if (files != null) wallpaperCount = files.length;
        }
        status.put("wallpapers_count", wallpaperCount);

        return jsonResponse(Response.Status.OK, status);
    }

    private Response handleGetConfig() throws Exception {
        File themeDir = Config.dir(activity);
        File configFile = themeDir != null ? new File(themeDir, Config.FILE_NAME) : null;
        JSONObject json = new JSONObject();
        if (configFile != null && configFile.exists()) {
            try (FileInputStream in = new FileInputStream(configFile)) {
                byte[] b = new byte[(int) configFile.length()];
                int read = in.read(b);
                String str = new String(b, 0, read, StandardCharsets.UTF_8);
                json = new JSONObject(str);
            }
        }
        android.content.SharedPreferences choices = activity.getSharedPreferences(Config.SETTINGS_NAME, android.content.Context.MODE_PRIVATE);
        String currentWp = choices.getString(Config.WALLPAPER_CHOICE_KEY, "auto");
        json.put("wallpaper_choice", currentWp == null ? "auto" : currentWp);
        return jsonResponse(Response.Status.OK, json);
    }

    private Response handlePostConfig(IHTTPSession session) throws Exception {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        String postData = files.get("postData");
        if (postData == null || postData.trim().isEmpty()) {
            return errorResponse(Response.Status.BAD_REQUEST, "Missing JSON payload");
        }

        JSONObject json = new JSONObject(postData);

        if (json.has("wallpaper_choice")) {
            String wc = json.optString("wallpaper_choice", null);
            android.content.SharedPreferences choices = activity.getSharedPreferences(Config.SETTINGS_NAME, android.content.Context.MODE_PRIVATE);
            if (wc == null || "auto".equalsIgnoreCase(wc) || wc.isEmpty()) {
                choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
            } else {
                choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, wc).apply();
            }
        }

        File themeDir = Config.dir(activity);
        if (themeDir == null) {
            return errorResponse(Response.Status.INTERNAL_ERROR, "External storage unavailable");
        }
        if (!themeDir.exists()) themeDir.mkdirs();

        File configFile = new File(themeDir, Config.FILE_NAME);
        JSONObject merged = new JSONObject();
        if (configFile.exists()) {
            try (FileInputStream in = new FileInputStream(configFile)) {
                byte[] b = new byte[(int) configFile.length()];
                int read = in.read(b);
                merged = new JSONObject(new String(b, 0, read, StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        }

        // Merge incoming keys so wallpaper, rotation, etc. are preserved
        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            merged.put(k, json.get(k));
        }

        try (FileOutputStream out = new FileOutputStream(configFile)) {
            out.write(merged.toString(2).getBytes(StandardCharsets.UTF_8));
        }

        // Live restart launcher cleanly on UI thread
        activity.runOnUiThread(() -> {
            try {
                activity.recreate();
            } catch (Exception e) {
                try {
                    Intent intent = activity.getIntent();
                    activity.finish();
                    activity.startActivity(intent);
                } catch (Exception ignored) {}
            }
        });

        JSONObject res = new JSONObject();
        res.put("status", "ok");
        res.put("message", "Config merged and launcher reloaded");
        return jsonResponse(Response.Status.OK, res);
    }

    private Response handleGetWallpapers() throws Exception {
        File themeDir = Config.dir(activity);
        File dir = themeDir != null ? new File(themeDir, "wallpapers") : null;
        JSONArray array = new JSONArray();
        if (dir != null && dir.exists()) {
            File[] list = dir.listFiles();
            if (list != null) {
                for (File f : list) {
                    if (f.isFile() && !f.getName().startsWith(".")) {
                        JSONObject item = new JSONObject();
                        item.put("name", f.getName());
                        item.put("size_bytes", f.length());
                        item.put("url", "/api/wallpaper/file?name=" + java.net.URLEncoder.encode(f.getName(), "UTF-8"));
                        array.put(item);
                    }
                }
            }
        }
        android.content.SharedPreferences choices = activity.getSharedPreferences(Config.SETTINGS_NAME, android.content.Context.MODE_PRIVATE);
        String currentChoice = choices.getString(Config.WALLPAPER_CHOICE_KEY, "auto");
        JSONObject res = new JSONObject();
        res.put("wallpapers", array);
        res.put("active_wallpaper", currentChoice == null ? "auto" : currentChoice);
        return jsonResponse(Response.Status.OK, res);
    }

    private Response handleActiveWallpaper(IHTTPSession session) throws Exception {
        Map<String, List<String>> params = session.getParameters();
        String name = null;
        if (params.containsKey("name") && !params.get("name").isEmpty()) {
            name = params.get("name").get(0);
        } else {
            Map<String, String> body = new HashMap<>();
            session.parseBody(body);
            String postData = body.get("postData");
            if (postData != null && !postData.trim().isEmpty()) {
                JSONObject json = new JSONObject(postData);
                name = json.optString("name", null);
            }
        }

        android.content.SharedPreferences choices = activity.getSharedPreferences(Config.SETTINGS_NAME, android.content.Context.MODE_PRIVATE);
        if (name == null || name.isEmpty() || "auto".equalsIgnoreCase(name) || "default".equalsIgnoreCase(name)) {
            choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
            name = "auto";
        } else {
            choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, name).apply();
        }

        activity.runOnUiThread(() -> {
            try {
                activity.recreate();
            } catch (Exception ignored) {}
        });

        JSONObject res = new JSONObject();
        res.put("status", "ok");
        res.put("active_wallpaper", name);
        return jsonResponse(Response.Status.OK, res);
    }

    private Response handleGetWallpaperFile(IHTTPSession session) throws Exception {
        Map<String, List<String>> params = session.getParameters();
        if (!params.containsKey("name") || params.get("name").isEmpty()) {
            return errorResponse(Response.Status.BAD_REQUEST, "Parameter 'name' required");
        }
        String name = params.get("name").get(0);

        File themeDir = Config.dir(activity);
        File target = themeDir != null ? new File(new File(themeDir, "wallpapers"), name) : null;
        if (target != null && target.exists() && target.isFile()) {
            String mime = "image/jpeg";
            String lower = name.toLowerCase();
            if (lower.endsWith(".png")) mime = "image/png";
            else if (lower.endsWith(".webp")) mime = "image/webp";

            FileInputStream fis = new FileInputStream(target);
            return newFixedLengthResponse(Response.Status.OK, mime, fis, target.length());
        }
        return errorResponse(Response.Status.NOT_FOUND, "Wallpaper not found: " + name);
    }

    private Response handlePostWallpaper(IHTTPSession session) throws Exception {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);

        String filename = null;
        Map<String, List<String>> params = session.getParameters();
        if (params.containsKey("filename") && !params.get("filename").isEmpty()) {
            filename = params.get("filename").get(0);
        }

        if (filename == null || filename.trim().isEmpty()) {
            filename = "wallpaper_" + System.currentTimeMillis() + ".jpg";
        }
        filename = filename.replaceAll("[^a-zA-Z0-9_.-]", "_");

        String tmpFilePath = null;
        for (Map.Entry<String, String> entry : files.entrySet()) {
            if (!"postData".equals(entry.getKey())) {
                tmpFilePath = entry.getValue();
                break;
            }
        }

        if (tmpFilePath == null) {
            return errorResponse(Response.Status.BAD_REQUEST, "No file uploaded");
        }

        File themeDir = Config.dir(activity);
        if (themeDir == null) {
            return errorResponse(Response.Status.INTERNAL_ERROR, "Storage unavailable");
        }
        File wallpapersDir = new File(themeDir, "wallpapers");
        if (!wallpapersDir.exists()) wallpapersDir.mkdirs();

        File target = new File(wallpapersDir, filename);
        File tmpFile = new File(tmpFilePath);
        if (!tmpFile.renameTo(target)) {
            // copy if rename fails
            try (FileInputStream in = new FileInputStream(tmpFile);
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
            tmpFile.delete();
        }

        activity.runOnUiThread(activity::recreate);

        JSONObject res = new JSONObject();
        res.put("status", "ok");
        res.put("saved_file", filename);
        return jsonResponse(Response.Status.OK, res);
    }

    private Response handleDeleteWallpaper(IHTTPSession session) throws Exception {
        Map<String, List<String>> params = session.getParameters();
        if (!params.containsKey("name") || params.get("name").isEmpty()) {
            return errorResponse(Response.Status.BAD_REQUEST, "Parameter 'name' required");
        }
        String name = params.get("name").get(0);

        File themeDir = Config.dir(activity);
        File target = themeDir != null ? new File(new File(themeDir, "wallpapers"), name) : null;
        if (target != null && target.exists()) {
            target.delete();
            activity.runOnUiThread(activity::recreate);
            JSONObject res = new JSONObject();
            res.put("status", "ok");
            res.put("deleted", name);
            return jsonResponse(Response.Status.OK, res);
        }
        return errorResponse(Response.Status.NOT_FOUND, "Wallpaper not found: " + name);
    }

    private Response handleGetApps() throws Exception {
        PackageManager pm = activity.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);

        JSONArray array = new JSONArray();
        for (ResolveInfo ri : list) {
            if (ri.activityInfo == null) continue;
            JSONObject app = new JSONObject();
            app.put("package", ri.activityInfo.packageName);
            app.put("activity", ri.activityInfo.name);
            app.put("label", ri.loadLabel(pm).toString());
            array.put(app);
        }

        JSONObject res = new JSONObject();
        res.put("apps", array);
        return jsonResponse(Response.Status.OK, res);
    }

    private Response handleActionLaunch(IHTTPSession session) throws Exception {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        String postData = files.get("postData");
        if (postData == null || postData.trim().isEmpty()) {
            return errorResponse(Response.Status.BAD_REQUEST, "Payload required");
        }
        JSONObject json = new JSONObject(postData);
        String pkg = json.optString("package");
        if (pkg.isEmpty()) {
            return errorResponse(Response.Status.BAD_REQUEST, "'package' required");
        }

        PackageManager pm = activity.getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);

        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            final Intent launchIntent = launch;
            activity.runOnUiThread(() -> {
                try {
                    activity.wakeUpScreen();
                    activity.startActivity(launchIntent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch app: " + pkg, e);
                }
            });
            JSONObject res = new JSONObject();
            res.put("status", "ok");
            res.put("launched", pkg);
            return jsonResponse(Response.Status.OK, res);
        }
        return errorResponse(Response.Status.NOT_FOUND, "App cannot be launched: " + pkg);
    }

    private Response handleActionKey(IHTTPSession session) throws Exception {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        String postData = files.get("postData");
        if (postData == null || postData.trim().isEmpty()) {
            return errorResponse(Response.Status.BAD_REQUEST, "Payload required");
        }
        JSONObject json = new JSONObject(postData);
        String key = json.optString("key", "").toLowerCase();
        int keycode = json.optInt("keycode", -1);

        if (key.isEmpty() && keycode != -1) {
            switch (keycode) {
                case KeyEvent.KEYCODE_DPAD_UP: key = "up"; break;
                case KeyEvent.KEYCODE_DPAD_DOWN: key = "down"; break;
                case KeyEvent.KEYCODE_DPAD_LEFT: key = "left"; break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: key = "right"; break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER: key = "ok"; break;
                case KeyEvent.KEYCODE_BACK: key = "back"; break;
                case KeyEvent.KEYCODE_HOME: key = "home"; break;
            }
        }

        final String finalKey = key;
        activity.runOnUiThread(() -> {
            activity.handleRemoteNavigation(finalKey);
        });

        JSONObject res = new JSONObject();
        res.put("status", "ok");
        res.put("key", key);
        return jsonResponse(Response.Status.OK, res);
    }
}
