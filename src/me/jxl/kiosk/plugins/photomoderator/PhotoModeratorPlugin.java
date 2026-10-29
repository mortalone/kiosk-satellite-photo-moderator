// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.photomoderator;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.PluginHost;

public final class PhotoModeratorPlugin implements KioskPlugin {
    private PluginHost host;
    private Context context;
    private WindowManager windowManager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService ioPool;

    private static final String FOTOO_PACKAGE = "com.bo.fotoo";
    private BroadcastReceiver dreamReceiver;
    private Application.ActivityLifecycleCallbacks lifecycleCallbacks;
    private Activity currentActivity;

    // Settings
    private String curatorUrl = "http://192.168.0.96:8095";
    private String overlayTarget = "Screensaver only";
    private String buttonPosition = "Top right";
    private String buttonSize = "Medium (48 dp)";
    private String buttonIcon = "Shield (🛡️)";
    private int buttonOpacity = 70;
    private int buttonOffsetX = 16;
    private int buttonOffsetY = 16;
    private int autoDismissSeconds = 45;

    // State
    private boolean kioskScreensaverActive = false;
    private boolean fotooActive = false;
    private boolean dreaming = false;
    private boolean dialogOpen = false;

    // Overlays
    private FloatingButtonView floatingButton;
    private ModeratorDialogView dialogView;
    private final LruCache<String, Bitmap> thumbCache = new LruCache<>(24);

    @Override
    public void start(PluginHost host, Map<String, Object> settings) throws Exception {
        this.host = host;
        this.context = applicationContext(host);
        if (this.context != null) {
            this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        }
        this.ioPool = Executors.newCachedThreadPool();

        applySettings(settings);

        if (this.context != null) {
            registerDreamReceiver();
            registerActivityLifecycle();
        }

        if (this.host != null) {
            this.host.subscribe("screensaver.state");
            this.host.subscribe("screensaver.view");

            // Query initial screensaver state
            try {
                this.host.executeCommand("isScreensaverActive", Collections.emptyMap(), (ok, data, err) -> {
                    if (ok && data instanceof Boolean) {
                        kioskScreensaverActive = (Boolean) data;
                        main.post(this::updateOverlayState);
                    }
                });
            } catch (Throwable ignored) {}

            try {
                this.host.status("Ready. Photo Moderator active.", false);
            } catch (Throwable ignored) {}
        }

        main.post(this::updateOverlayState);
    }

    @Override
    public void configure(Map<String, Object> settings) throws Exception {
        applySettings(settings);
        main.post(this::updateOverlayState);
    }

    @Override
    public void execute(String command, Map<String, Object> arguments) throws Exception {
        if ("showModeratorDialog".equals(command)) {
            main.post(this::openModeratorDialog);
        } else if ("hideModeratorDialog".equals(command)) {
            main.post(this::closeModeratorDialog);
        } else if ("reportCurrentNsfw".equals(command)) {
            reportCurrentPhotoAction("nsfw");
        } else if ("reportCurrentStranger".equals(command)) {
            reportCurrentPhotoAction("stranger");
        }
    }

    @Override
    public void onEvent(String event, Map<String, Object> payload) throws Exception {
        if ("ks.screensaver.state".equals(event) || "screensaver.state".equals(event)) {
            if (payload != null && payload.containsKey("active")) {
                kioskScreensaverActive = Boolean.TRUE.equals(payload.get("active"));
                main.post(this::updateOverlayState);
            }
        }
    }

    @Override
    public void stop() throws Exception {
        main.removeCallbacksAndMessages(null);
        if (host != null) {
            try { host.unsubscribe("screensaver.state"); } catch (Throwable ignored) {}
            try { host.unsubscribe("screensaver.view"); } catch (Throwable ignored) {}
        }
        if (dreamReceiver != null && context != null) {
            try { context.unregisterReceiver(dreamReceiver); } catch (Throwable ignored) {}
            dreamReceiver = null;
        }
        if (lifecycleCallbacks != null && context instanceof Application) {
            try { ((Application) context).unregisterActivityLifecycleCallbacks(lifecycleCallbacks); } catch (Throwable ignored) {}
            lifecycleCallbacks = null;
        }
        main.post(() -> {
            closeModeratorDialog();
            removeFloatingButton();
        });
        if (ioPool != null) {
            ioPool.shutdownNow();
            ioPool = null;
        }
        thumbCache.evictAll();
        host = null;
        context = null;
        windowManager = null;
    }

    private void applySettings(Map<String, Object> values) {
        if (values == null) return;
        curatorUrl = stringSetting(values, "curatorUrl", "http://192.168.0.96:8095");
        while (curatorUrl.endsWith("/")) {
            curatorUrl = curatorUrl.substring(0, curatorUrl.length() - 1);
        }
        overlayTarget = stringSetting(values, "overlayTarget", "Screensaver only");
        buttonPosition = stringSetting(values, "buttonPosition", "Top right");
        buttonSize = stringSetting(values, "buttonSize", "Medium (48 dp)");
        buttonIcon = stringSetting(values, "buttonIcon", "Shield (🛡️)");
        buttonOpacity = intSetting(values, "buttonOpacity", 70);
        buttonOffsetX = intSetting(values, "buttonOffsetX", 16);
        buttonOffsetY = intSetting(values, "buttonOffsetY", 16);
        autoDismissSeconds = intSetting(values, "autoDismissSeconds", 45);
    }

    private static String stringSetting(Map<String, Object> m, String k, String def) {
        Object v = m.get(k);
        return v != null ? String.valueOf(v).trim() : def;
    }

    private static int intSetting(Map<String, Object> m, String k, int def) {
        Object v = m.get(k);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v != null) {
            try { return Integer.parseInt(String.valueOf(v).trim()); } catch (Throwable ignored) {}
        }
        return def;
    }

    private int dp(float value) {
        if (context == null) return Math.round(value * 2.0f);
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private boolean isOverlayAllowed() {
        if ("Always (Screensaver + Dashboards)".equals(overlayTarget)) {
            return true;
        }
        if ("Screensaver + Fotoo".equals(overlayTarget)) {
            return kioskScreensaverActive || fotooActive || dreaming;
        }
        // Default: "Screensaver only"
        return kioskScreensaverActive;
    }

    private void updateOverlayState() {
        if (isOverlayAllowed()) {
            ensureFloatingButton();
        } else {
            removeFloatingButton();
            closeModeratorDialog();
        }
    }

    private int parseButtonSizeDp() {
        if (buttonSize.contains("Small")) return 36;
        if (buttonSize.contains("Large")) return 60;
        return 48; // Medium
    }

    private int parseGravity() {
        int g = 0;
        if (buttonPosition.contains("Top")) g |= Gravity.TOP;
        else g |= Gravity.BOTTOM;

        if (buttonPosition.contains("Left")) g |= Gravity.START;
        else g |= Gravity.END;

        return g;
    }

    private void ensureFloatingButton() {
        if (context == null) return;
        int sizePx = dp(parseButtonSizeDp());
        int grav = parseGravity();
        int offX = dp(buttonOffsetX);
        int offY = dp(buttonOffsetY);

        if (floatingButton != null) {
            floatingButton.configure(buttonIcon, buttonOpacity);
            return;
        }

        // A previous plugin instance (reload, update, KS restart) may have left its
        // button mounted in the activity or as an overlay window. Remove it first,
        // otherwise the user sees two shields.
        removeStaleButton();

        floatingButton = new FloatingButtonView(context);
        floatingButton.configure(buttonIcon, buttonOpacity);
        floatingButton.setListener(this::openModeratorDialog);

        if (addOverlayView(floatingButton, sizePx, sizePx, grav, offX, offY, true)) {
            lastMountedButton = floatingButton;
        }
    }

    /** The button most recently mounted by any instance of this plugin in this process. */
    private static volatile View lastMountedButton;

    private void removeStaleButton() {
        View stale = lastMountedButton;
        lastMountedButton = null;
        if (stale != null && stale != floatingButton) removeOverlayView(stale);
        // Also sweep the activity content for orphaned buttons of earlier instances.
        try {
            Activity activity = activeKioskActivity();
            if (activity == null) return;
            View content = activity.findViewById(android.R.id.content);
            if (!(content instanceof ViewGroup)) return;
            ViewGroup root = (ViewGroup) content;
            for (int i = root.getChildCount() - 1; i >= 0; i--) {
                View child = root.getChildAt(i);
                if (child != floatingButton && child.getClass().getName().equals(FloatingButtonView.class.getName())) {
                    root.removeViewAt(i);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void removeFloatingButton() {
        if (floatingButton != null) {
            removeOverlayView(floatingButton);
            if (lastMountedButton == floatingButton) lastMountedButton = null;
            floatingButton = null;
        }
    }

    public void openModeratorDialog() {
        if (dialogOpen || context == null) return;
        postponeScreensaver();
        dialogOpen = true;

        dialogView = new ModeratorDialogView(context, autoDismissSeconds, new ModeratorDialogView.Listener() {
            @Override
            public void onCloseRequested() {
                closeModeratorDialog();
            }

            @Override
            public void onReportNsfw(PhotoItem photo) {
                executeModerationAction(photo, "report_nsfw", null);
            }

            @Override
            public void onReportStranger(PhotoItem photo) {
                executeModerationAction(photo, "remove_subject", "stranger");
            }

            @Override
            public void onBlacklist(PhotoItem photo) {
                executeModerationAction(photo, "status", "blacklist");
            }

            @Override
            public void onPostponeScreensaver() {
                postponeScreensaver();
            }

            @Override
            public void onPhotoChanged(PhotoItem photo) {
                loadCurrentThumbnail();
            }
        });

        addOverlayView(dialogView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER, 0, 0, false);

        fetchRecentlyShown();
    }

    public void closeModeratorDialog() {
        if (!dialogOpen) return;
        dialogOpen = false;
        if (dialogView != null) {
            removeOverlayView(dialogView);
            dialogView = null;
        }
    }

    private void postponeScreensaver() {
        if (host != null) {
            try {
                host.executeCommand("postponeScreensaver", Collections.emptyMap(), (ok, data, error) -> {});
            } catch (Throwable ignored) {}
        }
    }

    // --- Backend API Integration ---

    private void fetchRecentlyShown() {
        if (ioPool == null) return;
        final String endpoint = curatorUrl + "/api/photos/recently_shown";
        ioPool.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    InputStream in = conn.getInputStream();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int r;
                    while ((r = in.read(buf)) != -1) baos.write(buf, 0, r);
                    String resp = baos.toString("UTF-8");

                    List<PhotoItem> list = new ArrayList<>();
                    JSONArray arr = new JSONArray(resp);
                    for (int i = 0; i < arr.length(); i++) {
                        PhotoItem item = PhotoItem.fromJson(arr.getJSONObject(i));
                        if (item != null) list.add(item);
                    }

                    main.post(() -> {
                        if (dialogView != null && dialogOpen) {
                            dialogView.setPhotos(list);
                            loadCurrentThumbnail();
                        }
                    });
                } else {
                    main.post(() -> {
                        if (dialogView != null) dialogView.showStatusBanner("Fejl: Server svarede med kode " + code, 0xFFEF4444);
                    });
                }
            } catch (Throwable t) {
                main.post(() -> {
                    if (dialogView != null) dialogView.showStatusBanner("Kunne ikke forbinde til " + curatorUrl, 0xFFEF4444);
                });
            }
        });
    }

    private void loadCurrentThumbnail() {
        if (dialogView == null || !dialogOpen) return;
        PhotoItem photo = dialogView.getCurrentPhoto();
        if (photo == null || photo.thumbUrl.isEmpty()) return;

        Bitmap cached = thumbCache.get(photo.thumbUrl);
        if (cached != null) {
            dialogView.setThumbnailBitmap(cached);
            return;
        }

        String fullThumbUrl = photo.thumbUrl.startsWith("http")
                ? photo.thumbUrl
                : curatorUrl + (photo.thumbUrl.startsWith("/") ? "" : "/") + photo.thumbUrl;

        if (ioPool == null) return;
        ioPool.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(fullThumbUrl).openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                if (conn.getResponseCode() == 200) {
                    InputStream is = conn.getInputStream();
                    Bitmap bmp = BitmapFactory.decodeStream(is);
                    if (bmp != null) {
                        thumbCache.put(photo.thumbUrl, bmp);
                        main.post(() -> {
                            if (dialogView != null && dialogOpen) {
                                PhotoItem cur = dialogView.getCurrentPhoto();
                                if (cur != null && cur.id == photo.id) {
                                    dialogView.setThumbnailBitmap(bmp);
                                }
                            }
                        });
                    }
                }
            } catch (Throwable ignored) {}
        });
    }

    private void executeModerationAction(PhotoItem photo, String actionType, String subAction) {
        if (photo == null || ioPool == null) return;
        postponeScreensaver();

        String urlStr;
        String jsonPayload = null;
        if ("report_nsfw".equals(actionType)) {
            urlStr = curatorUrl + "/api/photos/" + photo.id + "/report_nsfw";
        } else if ("remove_subject".equals(actionType)) {
            urlStr = curatorUrl + "/api/photos/" + photo.id + "/remove_subject";
            jsonPayload = "{\"action\":\"" + subAction + "\"}";
        } else { // "status"
            urlStr = curatorUrl + "/api/photos/" + photo.id + "/status";
            jsonPayload = "{\"action\":\"" + subAction + "\"}";
        }

        final String targetUrl = urlStr;
        final String body = jsonPayload;

        ioPool.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(targetUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                if (body != null) {
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    OutputStream os = conn.getOutputStream();
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    os.close();
                }

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    main.post(() -> {
                        if (dialogView != null && dialogOpen) {
                            if ("report_nsfw".equals(actionType)) {
                                dialogView.onActionSuccess("Markeret som nøgenhed og fjernet.");
                            } else if ("remove_subject".equals(actionType)) {
                                dialogView.onActionSuccess("Markeret som fremmed og fjernet.");
                            } else {
                                dialogView.onActionSuccess("Permanent udelukket (blacklistet).");
                            }
                        }
                    });
                } else {
                    main.post(() -> {
                        if (dialogView != null) dialogView.onActionError("Serverfejl: " + code);
                    });
                }
            } catch (Throwable t) {
                main.post(() -> {
                    if (dialogView != null) dialogView.onActionError("Netværksfejl: " + t.getMessage());
                });
            }
        });
    }

    private void reportCurrentPhotoAction(String action) {
        if (ioPool == null) return;
        ioPool.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(curatorUrl + "/api/photos/recently_shown").openConnection();
                conn.setConnectTimeout(5000);
                if (conn.getResponseCode() == 200) {
                    InputStream in = conn.getInputStream();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[2048];
                    int r;
                    while ((r = in.read(buf)) != -1) baos.write(buf, 0, r);
                    JSONArray arr = new JSONArray(baos.toString("UTF-8"));
                    if (arr.length() > 0) {
                        PhotoItem latest = PhotoItem.fromJson(arr.getJSONObject(0));
                        if (latest != null) {
                            if ("nsfw".equals(action)) {
                                executeModerationAction(latest, "report_nsfw", null);
                            } else {
                                executeModerationAction(latest, "remove_subject", "stranger");
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        });
    }

    // --- Overlay Mounting and Window Management ---

    private boolean preferInAppOverlay() {
        return !fotooActive && !dreaming;
    }

    private Activity activeKioskActivity() {
        if (currentActivity != null && !currentActivity.isFinishing() &&
                (Build.VERSION.SDK_INT < 17 || !currentActivity.isDestroyed())) {
            return currentActivity;
        }
        Activity activity = findResumedActivity();
        if (activity != null) currentActivity = activity;
        return activity;
    }

    private boolean addOverlayView(View view, int width, int height, int gravity, int xOffset, int yOffset, boolean button) {
        if (view == null) return false;

        if (preferInAppOverlay()) {
            Activity activity = activeKioskActivity();
            if (activity != null) {
                View content = activity.findViewById(android.R.id.content);
                if (content instanceof FrameLayout) {
                    FrameLayout root = (FrameLayout) content;
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height, gravity);
                    if ((gravity & Gravity.TOP) == Gravity.TOP) {
                        params.topMargin = yOffset;
                    } else if ((gravity & Gravity.BOTTOM) == Gravity.BOTTOM) {
                        params.bottomMargin = yOffset;
                    }
                    if ((gravity & Gravity.START) == Gravity.START) {
                        params.leftMargin = xOffset;
                    } else if ((gravity & Gravity.END) == Gravity.END) {
                        params.rightMargin = xOffset;
                    }
                    root.addView(view, params);
                    if (Build.VERSION.SDK_INT >= 21) view.setZ(100000f);
                    view.bringToFront();
                    root.invalidate();
                    return true;
                }
            }
        }

        if (windowManager == null) return false;
        try {
            WindowManager.LayoutParams params = overlayParams(width, height);
            params.gravity = gravity;
            params.x = xOffset;
            params.y = yOffset;
            windowManager.addView(view, params);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void removeOverlayView(View view) {
        if (view == null) return;
        try {
            ViewParent parent = view.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(view);
                return;
            }
        } catch (Throwable ignored) {}
        if (windowManager != null) {
            try { windowManager.removeViewImmediate(view); } catch (Throwable ignored) {}
        }
    }

    private WindowManager.LayoutParams overlayParams(int width, int height) {
        return new WindowManager.LayoutParams(
                width,
                height,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
    }

    // --- Fotoo and Lifecycle Hooks ---

    private void registerDreamReceiver() {
        dreamReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String act = intent.getAction();
                if ("android.intent.action.DREAMING_STARTED".equals(act)) {
                    dreaming = true;
                    updateOverlayState();
                } else if ("android.intent.action.DREAMING_STOPPED".equals(act)) {
                    dreaming = false;
                    updateOverlayState();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.DREAMING_STARTED");
        filter.addAction("android.intent.action.DREAMING_STOPPED");
        try { context.registerReceiver(dreamReceiver, filter); } catch (Throwable ignored) {}
    }

    private void registerActivityLifecycle() {
        if (!(context instanceof Application)) return;
        lifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) {
                currentActivity = activity;
                updateOverlayState();
            }
            @Override public void onActivityPaused(Activity activity) {
                if (currentActivity == activity) currentActivity = null;
            }
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
            @Override public void onActivityDestroyed(Activity activity) {
                if (currentActivity == activity) currentActivity = null;
            }
        };
        ((Application) context).registerActivityLifecycleCallbacks(lifecycleCallbacks);
    }

    private Activity findResumedActivity() {
        try {
            Class<?> threadClass = Class.forName("android.app.ActivityThread");
            Method currentThread = threadClass.getDeclaredMethod("currentActivityThread");
            currentThread.setAccessible(true);
            Object thread = currentThread.invoke(null);
            if (thread == null) return null;

            Field activitiesField = threadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            Object activitiesObject = activitiesField.get(thread);
            if (!(activitiesObject instanceof Map)) return null;

            Activity fallback = null;
            for (Object record : ((Map<?, ?>) activitiesObject).values()) {
                if (record == null) continue;
                Class<?> recordClass = record.getClass();

                Field activityField = recordClass.getDeclaredField("activity");
                activityField.setAccessible(true);
                Object activityValue = activityField.get(record);
                if (!(activityValue instanceof Activity)) continue;
                Activity activity = (Activity) activityValue;
                if (!activity.getPackageName().equals(context.getPackageName()) ||
                        activity.isFinishing() ||
                        (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
                    continue;
                }

                if (activity.hasWindowFocus()) return activity;

                boolean paused = false;
                boolean stopped = false;
                try {
                    Field pausedField = recordClass.getDeclaredField("paused");
                    pausedField.setAccessible(true);
                    paused = pausedField.getBoolean(record);
                } catch (Throwable ignored) {}
                try {
                    Field stoppedField = recordClass.getDeclaredField("stopped");
                    stoppedField.setAccessible(true);
                    stopped = stoppedField.getBoolean(record);
                } catch (Throwable ignored) {}

                if (!paused && !stopped) fallback = activity;
            }
            return fallback;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Context applicationContext(PluginHost host) {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method currentApplication = activityThread.getDeclaredMethod("currentApplication");
            currentApplication.setAccessible(true);
            Object value = currentApplication.invoke(null);
            if (value instanceof Application) return ((Application) value).getApplicationContext();
            if (value instanceof Context) return ((Context) value).getApplicationContext();
        } catch (Throwable ignored) {}

        Object current = host;
        for (int depth = 0; current != null && depth < 5; depth++) {
            Class<?> type = current.getClass();
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                try {
                    for (Field field : c.getDeclaredFields()) {
                        field.setAccessible(true);
                        Object value = field.get(current);
                        if (value instanceof Context) return ((Context) value).getApplicationContext();
                    }
                } catch (Throwable ignored) {}
            }

            Object enclosing = null;
            for (Class<?> c = type; c != null && enclosing == null; c = c.getSuperclass()) {
                try {
                    for (Field field : c.getDeclaredFields()) {
                        if (!field.getName().startsWith("this$")) continue;
                        field.setAccessible(true);
                        Object value = field.get(current);
                        if (value != null && value.getClass().getName().startsWith("me.jxl.")) {
                            enclosing = value;
                            break;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            current = enclosing;
        }
        return null;
    }
}
