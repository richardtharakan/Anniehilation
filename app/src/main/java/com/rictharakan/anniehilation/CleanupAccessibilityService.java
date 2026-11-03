package com.rictharakan.anniehilation;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * User-started helper for navigating Android Settings to selected apps' cache controls.
 * It only clicks a control whose accessible label exactly matches "Clear cache".
 */
public class CleanupAccessibilityService extends AccessibilityService {

    public static final String PREFERENCES = "annie_cleanup_accessibility";
    public static final String KEY_PENDING_PACKAGES = "pending_packages";
    public static final String KEY_WAITING_FOR_ENABLE = "waiting_for_enable";
    public static final String KEY_QUEUE = "queue";
    public static final String KEY_INDEX = "index";
    public static final String KEY_STAGE = "stage";
    public static final String KEY_ACTIVE = "active";
    public static final String KEY_CLEARED = "cleared";
    public static final String KEY_NO_CACHE = "no_cache";
    public static final String KEY_SKIPPED = "skipped";
    public static final String KEY_COMPLETED = "completed";
    public static final String KEY_RESULT = "result";

    private static final String STAGE_APP_INFO = "app_info";
    private static final String STAGE_STORAGE = "storage";
    private static final String STAGE_AFTER_CLEAR = "after_clear";
    private static final String TAG = "AnniehilationCleanup";
    private static final long SCREEN_TIMEOUT_MS = 9000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable screenTimeout;

    public static void beginCleanup(Context context, ArrayList<String> packageNames) {
        StringBuilder queue = new StringBuilder();
        for (String packageName : packageNames) {
            if (packageName == null || packageName.trim().isEmpty()) {
                continue;
            }
            if (queue.length() > 0) {
                queue.append('\n');
            }
            queue.append(packageName.trim());
        }

        if (queue.length() == 0) {
            return;
        }

        SharedPreferences preferences = context.getSharedPreferences(
                PREFERENCES,
                Context.MODE_PRIVATE
        );
        preferences.edit()
                .putString(KEY_QUEUE, queue.toString())
                .putInt(KEY_INDEX, 0)
                .putString(KEY_STAGE, STAGE_APP_INFO)
                .putInt(KEY_CLEARED, 0)
                .putInt(KEY_NO_CACHE, 0)
                .putInt(KEY_SKIPPED, 0)
                .putBoolean(KEY_ACTIVE, true)
                .putBoolean(KEY_COMPLETED, false)
                .commit();

        launchCurrentApp(context);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }

        SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        if (!preferences.getBoolean(KEY_ACTIVE, false)) {
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !looksLikeSettingsPackage(root.getPackageName())) {
            return;
        }

        int index = preferences.getInt(KEY_INDEX, 0);
        String stage = preferences.getString(KEY_STAGE, STAGE_APP_INFO);

        if (STAGE_APP_INFO.equals(stage)) {
            if (!isCurrentAppNameVisible(root, preferences)) {
                return;
            }
            AccessibilityNodeInfo storageNode = findExactLabel(root, new String[] {
                    "Storage",
                    "Storage & cache",
                    "Storage usage",
                    "Storage space"
            });
            if (storageNode != null) {
                preferences.edit().putString(KEY_STAGE, STAGE_STORAGE).commit();
                boolean clicked = clickMatchingControl(storageNode);
                scheduleScreenTimeout(index, STAGE_STORAGE);
                if (!clicked) {
                    Log.i(TAG, "Storage entry could not be opened; waiting for Settings state change");
                }
            }
            return;
        }

        if (STAGE_STORAGE.equals(stage)) {
            // Requiring the usual app-storage data control helps avoid acting on
            // an unrelated Settings page that happens to contain "Clear cache".
            if (!isCurrentAppNameVisible(root, preferences)
                    || !hasExactLabel(root, new String[] {"Clear data", "Clear storage"})) {
                return;
            }
            AccessibilityNodeInfo clearCacheNode = findExactLabel(
                    root,
                    new String[] {"Clear cache"}
            );
            if (clearCacheNode != null) {
                boolean enabled = clearCacheNode.isEnabled();
                boolean clicked = enabled && clickMatchingControl(clearCacheNode);
                if (clicked) {
                    preferences.edit()
                            .putString(KEY_STAGE, STAGE_AFTER_CLEAR)
                            .putInt(KEY_CLEARED, preferences.getInt(KEY_CLEARED, 0) + 1)
                            .commit();
                    scheduleAdvance(index);
                } else if (!enabled) {
                    // Disabled Clear cache usually means the app has no cache to remove.
                    preferences.edit()
                            .putString(KEY_STAGE, STAGE_AFTER_CLEAR)
                            .putInt(KEY_NO_CACHE, preferences.getInt(KEY_NO_CACHE, 0) + 1)
                            .commit();
                    scheduleAdvance(index);
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
        // Keep the user-started queue intact. The next Settings event can resume it.
    }

    private boolean looksLikeSettingsPackage(CharSequence packageName) {
        return packageName != null
                && packageName.toString().toLowerCase(Locale.ROOT).contains("settings");
    }

    private AccessibilityNodeInfo findExactLabel(
            AccessibilityNodeInfo root,
            String[] labels) {
        for (String label : labels) {
            List<AccessibilityNodeInfo> matches =
                    root.findAccessibilityNodeInfosByText(label);
            if (matches == null) {
                continue;
            }
            for (AccessibilityNodeInfo node : matches) {
                if (node.isVisibleToUser()
                        && (label.equalsIgnoreCase(normalize(node.getText()))
                        || label.equalsIgnoreCase(normalize(node.getContentDescription())))) {
                    return node;
                }
            }
        }
        return null;
    }

    private boolean hasExactLabel(AccessibilityNodeInfo root, String[] labels) {
        return findExactLabel(root, labels) != null;
    }

    private boolean isCurrentAppNameVisible(
            AccessibilityNodeInfo root,
            SharedPreferences preferences) {
        ArrayList<String> queue = readQueue(preferences.getString(KEY_QUEUE, ""));
        int index = preferences.getInt(KEY_INDEX, -1);
        if (index < 0 || index >= queue.size()) {
            return false;
        }
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(
                    queue.get(index),
                    0
            );
            CharSequence label = getPackageManager().getApplicationLabel(info);
            return label != null && hasExactLabel(root, new String[] {label.toString()});
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String normalize(CharSequence value) {
        return value == null
                ? ""
                : value.toString().replace('\u00a0', ' ').trim();
    }

    private boolean clickMatchingControl(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int depth = 0; current != null && depth < 4; depth++) {
            if (current.isEnabled() && current.isClickable()) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            current = current.getParent();
        }
        return false;
    }

    private void scheduleScreenTimeout(int expectedIndex, String expectedStage) {
        cancelPendingCallback();
        screenTimeout = () -> {
            SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
            if (preferences.getBoolean(KEY_ACTIVE, false)
                    && preferences.getInt(KEY_INDEX, -1) == expectedIndex
                    && expectedStage.equals(preferences.getString(KEY_STAGE, ""))) {
                skipCurrentApp("Settings page was not recognized");
            }
        };
        handler.postDelayed(screenTimeout, SCREEN_TIMEOUT_MS);
    }

    private void scheduleAdvance(int expectedIndex) {
        cancelPendingCallback();
        screenTimeout = () -> {
            SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
            if (preferences.getBoolean(KEY_ACTIVE, false)
                    && preferences.getInt(KEY_INDEX, -1) == expectedIndex
                    && STAGE_AFTER_CLEAR.equals(preferences.getString(KEY_STAGE, ""))) {
                advanceToNextApp();
            }
        };
        handler.postDelayed(screenTimeout, 700L);
    }

    private void cancelPendingCallback() {
        if (screenTimeout != null) {
            handler.removeCallbacks(screenTimeout);
            screenTimeout = null;
        }
    }

    private void skipCurrentApp(String reason) {
        Log.i(TAG, reason);
        SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        preferences.edit()
                .putInt(KEY_SKIPPED, preferences.getInt(KEY_SKIPPED, 0) + 1)
                .putString(KEY_STAGE, STAGE_AFTER_CLEAR)
                .commit();
        advanceToNextApp();
    }

    private void advanceToNextApp() {
        cancelPendingCallback();
        SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        int nextIndex = preferences.getInt(KEY_INDEX, 0) + 1;
        ArrayList<String> queue = readQueue(preferences.getString(KEY_QUEUE, ""));
        if (nextIndex >= queue.size()) {
            completeCleanup(preferences);
            return;
        }

        preferences.edit()
                .putInt(KEY_INDEX, nextIndex)
                .putString(KEY_STAGE, STAGE_APP_INFO)
                .commit();
        launchCurrentApp(this);
    }

    private void completeCleanup(SharedPreferences preferences) {
        int cleared = preferences.getInt(KEY_CLEARED, 0);
        int noCache = preferences.getInt(KEY_NO_CACHE, 0);
        int skipped = preferences.getInt(KEY_SKIPPED, 0);
        String result = "Selected app cache pass finished: " + cleared
                + " cache controls tapped, " + noCache + " already empty, "
                + skipped + " apps skipped. "
                + "Settings may recreate cache files as apps run.";

        preferences.edit()
                .putBoolean(KEY_ACTIVE, false)
                .putBoolean(KEY_COMPLETED, true)
                .putString(KEY_RESULT, result)
                .commit();

        Intent returnToApp = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(returnToApp);
        } catch (Exception e) {
            Log.w(TAG, "Could not return to Anniehilation after the cache pass", e);
        }
    }

    private static void launchCurrentApp(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(
                PREFERENCES,
                Context.MODE_PRIVATE
        );
        ArrayList<String> queue = readQueue(preferences.getString(KEY_QUEUE, ""));
        int index = preferences.getInt(KEY_INDEX, 0);
        if (!preferences.getBoolean(KEY_ACTIVE, false)
                || index < 0
                || index >= queue.size()) {
            return;
        }

        String packageName = queue.get(index);
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + packageName)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        preferences.edit().putString(KEY_STAGE, STAGE_APP_INFO).commit();
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "Could not open app info for " + packageName, e);
            preferences.edit()
                    .putInt(KEY_SKIPPED, preferences.getInt(KEY_SKIPPED, 0) + 1)
                    .putString(KEY_STAGE, STAGE_AFTER_CLEAR)
                    .commit();
            if (context instanceof CleanupAccessibilityService) {
                ((CleanupAccessibilityService) context).advanceToNextApp();
            } else {
                preferences.edit().putInt(KEY_INDEX, index + 1).commit();
                completeFromContext(context, preferences);
            }
        }
    }

    private static void completeFromContext(
            Context context,
            SharedPreferences preferences) {
        ArrayList<String> queue = readQueue(preferences.getString(KEY_QUEUE, ""));
        if (preferences.getInt(KEY_INDEX, 0) >= queue.size()) {
            int cleared = preferences.getInt(KEY_CLEARED, 0);
            int noCache = preferences.getInt(KEY_NO_CACHE, 0);
            int skipped = preferences.getInt(KEY_SKIPPED, 0);
            String result = "Selected app cache pass finished: " + cleared
                    + " cache controls tapped, " + noCache + " already empty, "
                    + skipped + " apps skipped.";
            preferences.edit()
                    .putBoolean(KEY_ACTIVE, false)
                    .putBoolean(KEY_COMPLETED, true)
                    .putString(KEY_RESULT, result)
                    .commit();
        } else {
            launchCurrentApp(context);
        }
    }

    private static ArrayList<String> readQueue(String value) {
        ArrayList<String> queue = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return queue;
        }
        for (String packageName : value.split("\\n")) {
            if (!packageName.trim().isEmpty()) {
                queue.add(packageName.trim());
            }
        }
        return queue;
    }
}
