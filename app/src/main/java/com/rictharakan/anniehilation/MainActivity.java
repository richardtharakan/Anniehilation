package com.rictharakan.anniehilation;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class MainActivity extends Activity {

    private TextView status, ramInfo;
    private Button cooldownButton;
    private Button cleanJunkButton;
    private RamGaugeView ramGauge;
    private StorageBarView phoneStorage, sdStorage;
    private ThermometerView thermometerView;
    private boolean batteryTemperatureReceiverRegistered = false;
    private boolean cooldownVisualActivated = false;

    private final BroadcastReceiver batteryTemperatureReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBatteryTemperature(intent);
        }
    };

    private boolean showStartupPreview = true;
    private boolean cleanupRunning = false;
    private boolean waitingForAllFilesAccess = false;
    private long appCacheFreedBytes = 0L;
    private long sharedThumbnailBytesFreed = 0L;
    private int sharedThumbnailDirectoriesCleaned = 0;
    private boolean waitingForAccessibilitySettings = false;

    private static final int REQUEST_CLEAR_APP_CACHES = 701;
    private static final int REQUEST_DELETE_TRASHED_MEDIA = 702;

    private static final String CLEANUP_TAG = "AnniehilationCleanup";
    // Android limits MediaStore delete requests to 2,000 URIs on recent target SDKs.
    // Using this conservative limit also keeps the request size manageable on older releases.
    private static final int MAX_MEDIA_DELETE_BATCH_SIZE = 2000;

    private long trashedMediaBytes = 0L;
    private boolean systemCacheClearCompleted = false;
    private ArrayList<Uri> pendingTrashedMedia = new ArrayList<>();
    private int pendingMediaDeleteIndex = 0;
    private int currentMediaBatchEnd = 0;
    private String mediaTrashQueryError;

    private static final int RAM_WARM_COLOR = Color.parseColor("#FF5733");
    private static final int RAM_COOL_COLOR = Color.parseColor("#42F5FF");
    private int ramInfoColorTarget = RAM_WARM_COLOR;
    private ValueAnimator ramInfoColorAnimator;

    private float startupBoost = 1.18f;
    private float lastActualRamFraction = 0f;
    private float lastPreviewFraction = 0f;

    // Clearly illustrative startup-only cache preview.
    private long previewCacheBytes;

    private long lastPreviewUsedBytes;
    private long lastPreviewAvailableBytes;
    private long lastPreviewTotalBytes;
    private boolean hasPreviewRamValues = false;
    private boolean ramValueTransitionStarted = false;
    private ValueAnimator ramValuesAnimator;

    /**
     * Updates the dashboard from Android's battery-status broadcast.
     * Android reports EXTRA_TEMPERATURE in tenths of a degree Celsius.
     * This is battery temperature, not CPU temperature.
     */
    private void updateBatteryTemperature(Intent intent) {
        if (thermometerView == null || intent == null) {
            return;
        }

        int tenthsC = intent.getIntExtra(
                BatteryManager.EXTRA_TEMPERATURE,
                Integer.MIN_VALUE
        );

        if (tenthsC == Integer.MIN_VALUE) {
            thermometerView.setTemperatureC(Float.NaN);
        } else {
            thermometerView.setTemperatureC(tenthsC / 10.0f);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent initialStatus;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                initialStatus = registerReceiver(
                        batteryTemperatureReceiver,
                        filter,
                        Context.RECEIVER_EXPORTED
                );
            } else {
                initialStatus = registerReceiver(batteryTemperatureReceiver, filter);
            }

            batteryTemperatureReceiverRegistered = true;
            if (initialStatus != null) {
                updateBatteryTemperature(initialStatus);
            }
        } catch (Exception e) {
            Log.w(CLEANUP_TAG, "Unable to register battery temperature receiver", e);
            if (thermometerView != null) {
                thermometerView.setTemperatureC(Float.NaN);
            }
        }
    }

    @Override
    protected void onStop() {
        if (batteryTemperatureReceiverRegistered) {
            try {
                unregisterReceiver(batteryTemperatureReceiver);
            } catch (IllegalArgumentException ignored) {
                // Receiver was already unregistered.
            }
            batteryTemperatureReceiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.status);
        ramInfo = findViewById(R.id.ramInfo);
        ramInfo.setTextColor(RAM_WARM_COLOR);
        cooldownButton = findViewById(R.id.btnCooldown);
        cleanJunkButton = findViewById(R.id.btnCleanJunk);
        ramGauge = findViewById(R.id.ramGauge);
        phoneStorage = findViewById(R.id.phoneStorage);
        sdStorage = findViewById(R.id.sdStorage);
        thermometerView = findViewById(R.id.thermometerView);

        // Stage 1: warm orange while the app starts.
        thermometerView.setVisualCoolingProgress(0f);

        // Stage 2: move to green after startup settles, unless the user
        // has already pressed RUN COOLDOWN.
        thermometerView.postDelayed(() -> {
            if (!cooldownVisualActivated) {
                thermometerView.setVisualCoolingProgress(0.5f);
            }
        }, 1800L);

        Button storageButton = findViewById(R.id.btnStorage);
        Button batteryButton = findViewById(R.id.btnBattery);

        // The controller icon is the entry point to the built-in Anniehilation game.
        View gameModeIcon = findViewById(R.id.gameModeIcon);
        gameModeIcon.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, GameActivity.class)));

        cooldownButton.setOnClickListener(v -> runCooldown());
        cleanJunkButton.setOnClickListener(v -> startJunkCleanup());
        cleanJunkButton.setOnLongClickListener(v -> {
            showInstalledAppPicker();
            return true;
        });
        storageButton.setOnClickListener(v -> openAppStorageSettings());
        batteryButton.setOnClickListener(v -> openBatterySaverSettings());

        Random random = new Random();

        // Visual RAM preview multiplier: 15% to 20%.
        startupBoost = 1.15f + random.nextFloat() * 0.05f;

        // Illustrative preview only: this is NOT measured cache.
        // Random range: 180 MB to 650 MB.
        previewCacheBytes =
                (180L + random.nextInt(471)) * 1024L * 1024L;

        sdStorage.setVisibility(View.GONE);

        showStartupConsolidationPreview();
        refreshDashboard();
    }

    private void showStartupConsolidationPreview() {
        status.setText(
                "COOLDOWN PREVIEW\n"
                        + "Illustrative cache cleanup: "
                        + formatBytes(previewCacheBytes)
                        + "\nPreview only — cleanup has not run yet."
                        + "\nLong-press Clean Junk for optional app-by-app cache cleanup."
        );
    }
    private void refreshDashboard() {
        updateRamInfo();
        updateStorageInfo();
    }

    private void updateRamInfo() {
        animateRamInfoColor(showStartupPreview);

        ActivityManager manager =
                (ActivityManager) getSystemService(ACTIVITY_SERVICE);

        if (manager == null) {
            ramInfo.setText("RAM information is unavailable.");
            ramGauge.setUsageFraction(0f);
            return;
        }

        ActivityManager.MemoryInfo info =
                new ActivityManager.MemoryInfo();

        manager.getMemoryInfo(info);

        long total = info.totalMem;
        long free = info.availMem;
        long used = Math.max(0L, total - free);

        float actualFraction = total > 0L
                ? (float) used / (float) total
                : 0f;

        lastActualRamFraction = actualFraction;

        if (showStartupPreview) {
            // Create an exaggerated visual preview while keeping the
            // preview values clearly distinguished from actual readings.
            long previewUsed = Math.min(
                    total,
                    Math.max(used, (long) (used * startupBoost))
            );

            long previewAvailable = Math.max(0L, total - previewUsed);

            lastPreviewFraction = total > 0L
                    ? (float) previewUsed / (float) total
                    : actualFraction;
            lastPreviewUsedBytes = previewUsed;
            lastPreviewAvailableBytes = previewAvailable;
            lastPreviewTotalBytes = total;
            hasPreviewRamValues = true;

            ramGauge.setStartupPreviewFraction(
                    actualFraction,
                    startupBoost
            );

            ramInfo.setText(buildRamInfoText(
                    "RAM LOAD PREVIEW",
                    "PREVIEW USED",
                    previewUsed,
                    "PREVIEW AVAILABLE",
                    previewAvailable,
                    total,
                    "Illustrative figures — not live measurements."
            ));

        } else {
            ramGauge.setUsageFraction(actualFraction);

            if (hasPreviewRamValues && !ramValueTransitionStarted) {
                ramValueTransitionStarted = true;
                animateRamValues(
                        lastPreviewUsedBytes,
                        lastPreviewAvailableBytes,
                        lastPreviewTotalBytes,
                        used,
                        free,
                        total
                );
            } else {
                ramInfo.setText(buildRamInfoText(
                        "RAM IN USE",
                        "USED",
                        used,
                        "AVAILABLE",
                        free,
                        total,
                        "Values are current device readings."
                ));
            }
        }
    }

    private void animateRamValues(
            long startUsed,
            long startAvailable,
            long startTotal,
            long finalUsed,
            long finalAvailable,
            long finalTotal) {

        if (ramValuesAnimator != null) {
            ramValuesAnimator.cancel();
        }

        ramValuesAnimator = ValueAnimator.ofFloat(0f, 1f);
        ramValuesAnimator.setDuration(1500L);
        ramValuesAnimator.setInterpolator(new DecelerateInterpolator());
        ramValuesAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            ramInfo.setText(buildRamInfoText(
                    "RAM IN USE",
                    "USED",
                    interpolateLong(startUsed, finalUsed, progress),
                    "AVAILABLE",
                    interpolateLong(startAvailable, finalAvailable, progress),
                    interpolateLong(startTotal, finalTotal, progress),
                    "Values are current device readings."
            ));
        });
        ramValuesAnimator.start();
    }

    private long interpolateLong(long start, long end, float fraction) {
        return start + Math.round((end - (double) start) * fraction);
    }

    private String buildRamInfoText(
            String heading,
            String usedLabel,
            long used,
            String availableLabel,
            long available,
            long total,
            String note) {

        return heading + "\n"
                + formatRamMetric(usedLabel, formatBytes(used)) + "\n"
                + formatRamMetric(availableLabel, formatBytes(available)) + "\n"
                + formatRamMetric("TOTAL", formatBytes(total))
                + "\n\n" + note;
    }
    private String formatRamMetric(String label, String value) {
        float charWidth = ramInfo.getPaint().measureText("0");
        int availableWidth = ramInfo.getWidth()
                - ramInfo.getCompoundPaddingLeft()
                - ramInfo.getCompoundPaddingRight();

        if (availableWidth <= 0) {
            float density = getResources().getDisplayMetrics().density;
            availableWidth = getResources().getDisplayMetrics().widthPixels
                    - Math.round(50f * density);
        }

        int columns = charWidth > 0f
                ? Math.max(label.length() + value.length() + 1,
                (int) (availableWidth / charWidth))
                : label.length() + value.length() + 1;
        int labelWidth = Math.max(label.length() + 1, columns - value.length());

        return String.format(Locale.ROOT, "%-" + labelWidth + "s%s", label, value);
    }

    /** Animates the body text between the gauge's warm preview and cool live colours. */
    private void animateRamInfoColor(boolean preview) {
        int targetColor = preview ? RAM_WARM_COLOR : RAM_COOL_COLOR;
        if (targetColor == ramInfoColorTarget) {
            return;
        }

        ramInfoColorTarget = targetColor;
        if (ramInfoColorAnimator != null) {
            ramInfoColorAnimator.cancel();
        }

        ramInfoColorAnimator = ValueAnimator.ofObject(
                new ArgbEvaluator(),
                ramInfo.getCurrentTextColor(),
                targetColor
        );
        ramInfoColorAnimator.setDuration(1500L);
        ramInfoColorAnimator.setInterpolator(new DecelerateInterpolator());
        ramInfoColorAnimator.addUpdateListener(animation ->
                ramInfo.setTextColor((Integer) animation.getAnimatedValue())
        );
        ramInfoColorAnimator.start();
    }
    private void updateStorageInfo() {
        // Always show internal phone storage.
        try {
            StatFs internal =
                    new StatFs(Environment.getDataDirectory().getPath());

            long total = internal.getTotalBytes();
            long free = internal.getAvailableBytes();

            phoneStorage.setStorageInfo(
                    "PHONE STORAGE",
                    Math.max(0L, total - free),
                    total
            );
        } catch (Exception e) {
            phoneStorage.setUnavailable(
                    "PHONE STORAGE",
                    "Could not read phone storage"
            );
        }

        // Hide SD card panel unless a mounted removable card is found.
        sdStorage.setVisibility(View.GONE);

        File[] dirs = getExternalFilesDirs(null);
        File removable = null;

        if (dirs != null) {
            for (File dir : dirs) {
                if (dir == null) {
                    continue;
                }

                try {
                    if (!Environment.isExternalStorageRemovable(dir)) {
                        continue;
                    }

                    String state =
                            Environment.getExternalStorageState(dir);

                    if (Environment.MEDIA_MOUNTED.equals(state)) {
                        removable = dir;
                        break;
                    }
                } catch (Exception ignored) {
                    // Skip inaccessible storage locations.
                }
            }
        }

        if (removable == null) {
            return;
        }

        try {
            StatFs card = new StatFs(removable.getAbsolutePath());

            long cardTotal = card.getTotalBytes();
            long cardFree = card.getAvailableBytes();

            if (cardTotal <= 0L || cardFree < 0L) {
                return;
            }

            long cardUsed = Math.max(0L, cardTotal - cardFree);

            sdStorage.setStorageInfo(
                    "SD CARD",
                    cardUsed,
                    cardTotal
            );

            sdStorage.setVisibility(View.VISIBLE);

        } catch (Exception ignored) {
            sdStorage.setVisibility(View.GONE);
        }
    }

    private void runCooldown() {
        if (cleanupRunning) {
            return;
        }

        cleanupRunning = true;

        // Stage 3: animate to cool cyan-blue when RUN COOLDOWN is pressed.
        cooldownVisualActivated = true;
        if (thermometerView != null) {
            thermometerView.setVisualCoolingProgress(1f);
        }

        setCleanupButtonsEnabled(false);
        status.setText("COOLDOWN IN PROGRESS\nCleaning this app's cache...");

        new Thread(() -> {
            long before = getCacheSize();

            clearCacheDirectory(getCacheDir());

            File externalCache = getExternalCacheDir();
            if (externalCache != null) {
                clearCacheDirectory(externalCache);
            }

            long after = getCacheSize();
            long cleared = Math.max(0L, before - after);

            runOnUiThread(() -> {
                // End the preview and show actual readings.
                showStartupPreview = false;
                refreshDashboard();

                int visualDrop = Math.round(
                        Math.max(
                                0f,
                                lastPreviewFraction - lastActualRamFraction
                        ) * 100f
                );

                status.setText(
                        "CLEANUP COMPLETE\n"
                                + "App cache actually cleared: "
                                + formatBytes(cleared)
                                + "\nGauge preview transition: "
                                + visualDrop + " percentage points"
                                + "\nRAM figures show current device readings."
                );

                cleanupRunning = false;
                setCleanupButtonsEnabled(true);
            });
        }).start();
    }

    /** Explains the special access request before opening Android Settings. */
    private void startJunkCleanup() {
        if (cleanupRunning) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && !Environment.isExternalStorageManager()) {
            new AlertDialog.Builder(this)
                    .setTitle("Allow All files access?")
                    .setMessage(
                            "Android requires you to enable All files access in Settings "
                                    + "before Anniehilation can scan shared-storage junk "
                                    + "or request Android's app-cache cleanup. This prompt "
                                    + "explains the request; Android Settings must grant it."
                    )
                    .setNegativeButton("Cancel", (dialog, which) ->
                            finishJunkCleanup(
                                    "Cleanup cancelled. All files access was not granted; "
                                            + "shared-storage cleanup was not started."
                            ))
                    .setPositiveButton("Continue to Settings", (dialog, which) ->
                            openAllFilesAccessSettings())
                    .show();
            return;
        }

        runJunkCleanup();
    }

    private void openAllFilesAccessSettings() {
        waitingForAllFilesAccess = true;
        cleanupRunning = true;
        setCleanupButtonsEnabled(false);
        status.setText("Opening Android Settings for All files access...");

        Intent intent = new Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        try {
            startActivity(intent);
        } catch (Exception e) {
            waitingForAllFilesAccess = false;
            cleanupRunning = false;
            finishJunkCleanup(
                    "Could not open Android's All files access setting: "
                            + describeException(e)
            );
        }
    }

    /** Cleans this app's cache and eligible shared thumbnails, then asks Android
     * to clear external app-cache directories. */
    private void runJunkCleanup() {
        if (cleanupRunning) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && !Environment.isExternalStorageManager()) {
            finishJunkCleanup(
                    "All files access is not enabled. Shared-storage cleanup was skipped."
            );
            return;
        }

        cleanupRunning = true;
        setCleanupButtonsEnabled(false);
        appCacheFreedBytes = 0L;
        sharedThumbnailBytesFreed = 0L;
        sharedThumbnailDirectoriesCleaned = 0;
        systemCacheClearCompleted = false;
        trashedMediaBytes = 0L;
        mediaTrashQueryError = null;
        pendingTrashedMedia.clear();
        pendingMediaDeleteIndex = 0;

        status.setText(
                "CLEAN JUNK IN PROGRESS\nCleaning this app's cache and "
                        + "accessible shared thumbnail folders..."
        );

        new Thread(() -> {
            long before = getCacheSize();
            clearCacheDirectory(getCacheDir());

            File externalCache = getExternalCacheDir();
            if (externalCache != null) {
                clearCacheDirectory(externalCache);
            }

            long after = getCacheSize();
            long cleared = Math.max(0L, before - after);
            ThumbnailCleanupResult thumbnailResult = clearSharedThumbnailCaches();

            runOnUiThread(() -> {
                appCacheFreedBytes = cleared;
                sharedThumbnailBytesFreed = thumbnailResult.bytesFreed;
                sharedThumbnailDirectoriesCleaned = thumbnailResult.directoriesCleaned;
                updateStorageInfo();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        && Environment.isExternalStorageManager()) {
                    requestSystemCacheClear();
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    finishJunkCleanup(
                            getCleanupSummary()
                                    + "\nAll files access was not available; "
                                    + "Android's device cache step was skipped."
                    );
                } else {
                    finishJunkCleanup(
                            getCleanupSummary()
                                    + "\nAndroid's all-app cache action requires Android 11 or later."
                    );
                }
            });
        }).start();
    }

    /** Android displays its required confirmation for external app-cache cleanup. */
    private void requestSystemCacheClear() {
        try {
            status.setText(
                    "Android is asking to clear external app caches. "
                            + "Approve its system dialog to continue."
            );
            startActivityForResult(
                    new Intent(StorageManager.ACTION_CLEAR_APP_CACHE),
                    REQUEST_CLEAR_APP_CACHES
            );
        } catch (Exception e) {
            Log.e(CLEANUP_TAG, "Could not launch Android's device-wide cache cleanup", e);
            finishJunkCleanup(
                    getCleanupSummary()
                            + "\nCould not open Android's cache-cleanup confirmation: "
                            + describeException(e)
            );
        }
    }
    /** Finds shared photos, videos, and audio already marked as trashed. */
    private ArrayList<Uri> findMediaStoreTrash() {
        ArrayList<Uri> uris = new ArrayList<>();
        mediaTrashQueryError = null;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                || !Environment.isExternalStorageManager()) {
            return uris;
        }

        trashedMediaBytes = 0L;

        Uri imageCollection = MediaStore.Images.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL
        );
        Uri videoCollection = MediaStore.Video.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL
        );
        Uri audioCollection = MediaStore.Audio.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL
        );

        if (!appendTrashedItemsFromCollection(imageCollection, uris)) {
            return uris;
        }
        if (!appendTrashedItemsFromCollection(videoCollection, uris)) {
            return uris;
        }
        appendTrashedItemsFromCollection(audioCollection, uris);

        return uris;
    }

    /** Queries one typed MediaStore collection so every URI has a valid item-ID path. */
    private boolean appendTrashedItemsFromCollection(
            Uri collection,
            ArrayList<Uri> destination) {
        Bundle queryArgs = new Bundle();
        queryArgs.putInt(
                MediaStore.QUERY_ARG_MATCH_TRASHED,
                MediaStore.MATCH_ONLY
        );

        String[] projection = {
                BaseColumns._ID,
                MediaStore.MediaColumns.SIZE
        };

        try (Cursor cursor = getContentResolver().query(
                collection, projection, queryArgs, null)) {
            if (cursor == null) {
                mediaTrashQueryError = "MediaStore returned a null cursor for "
                        + collection;
                Log.e(CLEANUP_TAG, mediaTrashQueryError);
                return false;
            }

            int idColumn = cursor.getColumnIndexOrThrow(BaseColumns._ID);
            int sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                destination.add(ContentUris.withAppendedId(collection, id));

                if (sizeColumn >= 0) {
                    trashedMediaBytes += Math.max(0L, cursor.getLong(sizeColumn));
                }
            }
            return true;
        } catch (Exception e) {
            mediaTrashQueryError = describeException(e);
            Log.e(CLEANUP_TAG, "Could not query trashed media at " + collection, e);
            return false;
        }
    }

    /** Sends matching shared-media trash directly to Android's delete confirmation. */
    private void requestMediaTrashDeletion() {
        ArrayList<Uri> trashedItems = findMediaStoreTrash();

        if (mediaTrashQueryError != null) {
            finishJunkCleanup(
                    getCleanupSummary()
                            + "\nCould not scan shared-media trash: "
                            + mediaTrashQueryError
            );
            return;
        }

        if (trashedItems.isEmpty()) {
            finishJunkCleanup(
                    getCleanupSummary() + "\nNo shared-media trash was found."
            );
            return;
        }

        pendingTrashedMedia = trashedItems;
        pendingMediaDeleteIndex = 0;
        status.setText(
                "Found " + trashedItems.size() + " trashed shared-media items ("
                        + formatBytes(trashedMediaBytes)
                        + "). Android will ask before permanently deleting them."
        );
        startNextMediaDeleteRequest();
    }
    /** Requests permanent deletion in bounded batches through Android's confirmation UI. */
    private void startNextMediaDeleteRequest() {
        if (pendingMediaDeleteIndex >= pendingTrashedMedia.size()) {
            finishJunkCleanup(
                    getCleanupSummary()
                            + "\nTrashed media deleted: "
                            + pendingTrashedMedia.size() + " items ("
                            + formatBytes(trashedMediaBytes) + ")."
            );
            return;
        }

        currentMediaBatchEnd = Math.min(
                pendingMediaDeleteIndex + MAX_MEDIA_DELETE_BATCH_SIZE,
                pendingTrashedMedia.size()
        );
        ArrayList<Uri> batch = new ArrayList<>(
                pendingTrashedMedia.subList(pendingMediaDeleteIndex, currentMediaBatchEnd)
        );

        try {
            PendingIntent request = MediaStore.createDeleteRequest(
                    getContentResolver(),
                    batch
            );
            startIntentSenderForResult(
                    request.getIntentSender(),
                    REQUEST_DELETE_TRASHED_MEDIA,
                    null,
                    0,
                    0,
                    0
            );
        } catch (Exception e) {
            Log.e(CLEANUP_TAG, "Could not create or launch MediaStore delete request", e);
            finishJunkCleanup(
                    "Could not request media deletion: " + describeException(e)
            );
        }
    }

    private String describeException(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return e.getClass().getSimpleName();
        }
        return e.getClass().getSimpleName() + ": " + message;
    }

    private String getCleanupSummary() {
        String accessState;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            accessState = "All files access: not required on this Android version.";
        } else {
            accessState = Environment.isExternalStorageManager()
                    ? "All files access: granted."
                    : "All files access: not granted.";
        }
        String cacheState = systemCacheClearCompleted
                ? "Android external app-cache cleanup: confirmed."
                : "Android external app-cache cleanup: not confirmed.";
        return accessState
                + "\nThis app cache freed: " + formatBytes(appCacheFreedBytes)
                + "\nAccessible thumbnail folders cleaned: "
                + sharedThumbnailDirectoriesCleaned + " ("
                + formatBytes(sharedThumbnailBytesFreed) + ")."
                + "\n" + cacheState;
    }

    /** Displays the result without opening another dialog after cleanup. */
    private void finishJunkCleanup(String message) {
        updateStorageInfo();
        status.setText(message);
        Toast.makeText(
                this,
                "Cleanup result updated in the status panel.",
                Toast.LENGTH_LONG
        ).show();

        cleanupRunning = false;
        setCleanupButtonsEnabled(true);
    }
    private void setCleanupButtonsEnabled(boolean enabled) {
        cooldownButton.setEnabled(enabled);
        cleanJunkButton.setEnabled(enabled);
    }

    private static final class InstalledAppChoice {
        final String packageName;
        final String label;

        InstalledAppChoice(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    private void showInstalledAppPicker() {
        ArrayList<InstalledAppChoice> choices = new ArrayList<>();
        PackageManager packageManager = getPackageManager();

        try {
            for (ApplicationInfo appInfo :
                    packageManager.getInstalledApplications(PackageManager.GET_META_DATA)) {
                if (getPackageName().equals(appInfo.packageName)
                        || packageManager.getLaunchIntentForPackage(appInfo.packageName) == null) {
                    continue;
                }
                CharSequence label = packageManager.getApplicationLabel(appInfo);
                choices.add(new InstalledAppChoice(
                        appInfo.packageName,
                        label == null ? appInfo.packageName : label.toString()
                ));
            }
        } catch (Exception e) {
            Log.e(CLEANUP_TAG, "Could not list installed apps", e);
            status.setText("Could not list installed apps: " + describeException(e));
            Toast.makeText(this, "Could not list installed apps.", Toast.LENGTH_LONG).show();
            return;
        }

        Collections.sort(choices, (left, right) ->
                left.label.compareToIgnoreCase(right.label));
        if (choices.isEmpty()) {
            status.setText("No selectable installed apps were found.");
            Toast.makeText(this, "No selectable apps were found.", Toast.LENGTH_LONG).show();
            return;
        }

        String[] labels = new String[choices.size()];
        boolean[] checked = new boolean[choices.size()];
        Set<String> selectedPackages = new HashSet<>();
        for (int i = 0; i < choices.size(); i++) {
            labels[i] = choices.get(i).label;
        }

        AlertDialog picker = new AlertDialog.Builder(this)
                .setTitle("Select apps to clean")
                .setMessage(
                        "Select one or more apps. Anniehilation will open each selected "
                                + "app's Storage settings and use Accessibility only when "
                                + "it can identify an exact Clear cache control."
                )
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                    String packageName = choices.get(which).packageName;
                    if (isChecked) {
                        selectedPackages.add(packageName);
                    } else {
                        selectedPackages.remove(packageName);
                    }
                })
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", null)
                .create();

        picker.setOnShowListener(dialog ->
                picker.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    if (selectedPackages.isEmpty()) {
                        String message = "Select at least one app before continuing.";
                        status.setText(message);
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        return;
                    }
                    picker.dismiss();
                    startSelectedAppCachePass(new ArrayList<>(selectedPackages));
                })
        );
        picker.show();
    }
    private void startSelectedAppCachePass(ArrayList<String> packageNames) {
        if (isCleanupAccessibilityServiceEnabled()) {
            CleanupAccessibilityService.beginCleanup(this, packageNames);
            status.setText("Opening Android Storage settings for selected apps...");
            return;
        }

        String queuedPackages = joinPackageNames(packageNames);
        SharedPreferences preferences = getSharedPreferences(
                CleanupAccessibilityService.PREFERENCES,
                MODE_PRIVATE
        );
        preferences.edit()
                .putString(CleanupAccessibilityService.KEY_PENDING_PACKAGES, queuedPackages)
                .putBoolean(CleanupAccessibilityService.KEY_WAITING_FOR_ENABLE, true)
                .commit();

        waitingForAccessibilitySettings = true;
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            waitingForAccessibilitySettings = false;
            preferences.edit()
                    .remove(CleanupAccessibilityService.KEY_PENDING_PACKAGES)
                    .putBoolean(CleanupAccessibilityService.KEY_WAITING_FOR_ENABLE, false)
                    .commit();
            status.setText("Could not open Accessibility settings: " + describeException(e));
        }
    }

    private boolean isCleanupAccessibilityServiceEnabled() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices == null || enabledServices.isEmpty()) {
            return false;
        }

        ComponentName component = new ComponentName(
                this,
                CleanupAccessibilityService.class
        );
        String fullName = component.flattenToString();
        String shortName = component.flattenToShortString();
        for (String enabledService : enabledServices.split(":")) {
            if (fullName.equalsIgnoreCase(enabledService)
                    || shortName.equalsIgnoreCase(enabledService)) {
                return true;
            }
        }
        return false;
    }

    private String joinPackageNames(ArrayList<String> packageNames) {
        StringBuilder result = new StringBuilder();
        for (String packageName : packageNames) {
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(packageName);
        }
        return result.toString();
    }

    private ArrayList<String> splitPackageNames(String joinedPackages) {
        ArrayList<String> packageNames = new ArrayList<>();
        if (joinedPackages == null || joinedPackages.isEmpty()) {
            return packageNames;
        }
        for (String packageName : joinedPackages.split("\\n")) {
            if (!packageName.trim().isEmpty()) {
                packageNames.add(packageName.trim());
            }
        }
        return packageNames;
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CLEAR_APP_CACHES) {
            systemCacheClearCompleted = resultCode == RESULT_OK;
            updateStorageInfo();
            if (systemCacheClearCompleted) {
                requestMediaTrashDeletion();
            } else {
                String outcome = resultCode == RESULT_CANCELED
                        ? "Android cache cleanup was cancelled."
                        : "Android cache cleanup failed (result " + resultCode + ").";
                Log.i(CLEANUP_TAG, outcome);
                finishJunkCleanup(
                        getCleanupSummary() + "\n" + outcome
                                + "\nMedia trash was not scanned."
                );
            }
        } else if (requestCode == REQUEST_DELETE_TRASHED_MEDIA) {
            if (resultCode == RESULT_OK) {
                pendingMediaDeleteIndex = currentMediaBatchEnd;
                startNextMediaDeleteRequest();
            } else {
                finishJunkCleanup(
                        getCleanupSummary()
                                + "\nAndroid cancelled media deletion. Deleted "
                                + pendingMediaDeleteIndex + " of "
                                + pendingTrashedMedia.size() + " items."
                );
            }
        }
    }
    private void openAppStorageSettings() {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())
        );

        startActivity(intent);
    }

    private void openBatterySaverSettings() {
        try {
            startActivity(
                    new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            );
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
                status.setText("Unable to open Settings on this device.");
            }
        }
    }

    private static final class ThumbnailCleanupResult {
        int directoriesCleaned;
        long bytesFreed;
    }

    /** Clears only legacy shared-storage directories explicitly named .thumbnails. */
    private ThumbnailCleanupResult clearSharedThumbnailCaches() {
        ThumbnailCleanupResult result = new ThumbnailCleanupResult();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && !Environment.isExternalStorageManager()) {
            return result;
        }

        File sharedRoot = Environment.getExternalStorageDirectory();
        if (sharedRoot == null || !sharedRoot.exists()) {
            return result;
        }

        String rootCanonical;
        try {
            rootCanonical = sharedRoot.getCanonicalPath();
        } catch (Exception e) {
            Log.w(CLEANUP_TAG, "Cannot resolve shared storage root", e);
            return result;
        }

        ArrayList<File> candidates = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        int[] directoryBudget = {5000};
        String[] knownParents = {
                "DCIM",
                "Pictures",
                "Movies",
                "Android/media",
                "WhatsApp",
                "Telegram"
        };

        for (String relativePath : knownParents) {
            File parent = new File(sharedRoot, relativePath);
            findThumbnailDirectories(
                    parent,
                    rootCanonical,
                    6,
                    directoryBudget,
                    visited,
                    candidates
            );
        }

        for (File thumbnailDirectory : candidates) {
            try {
                long before = folderSize(thumbnailDirectory);
                clearCacheDirectory(thumbnailDirectory);
                long after = folderSize(thumbnailDirectory);
                long freed = Math.max(0L, before - after);
                if (freed > 0L) {
                    result.directoriesCleaned++;
                    result.bytesFreed += freed;
                }
            } catch (SecurityException e) {
                Log.w(CLEANUP_TAG, "Could not clean " + thumbnailDirectory, e);
            }
        }
        return result;
    }

    private void findThumbnailDirectories(
            File directory,
            String rootCanonical,
            int depthRemaining,
            int[] directoryBudget,
            Set<String> visited,
            ArrayList<File> results) {
        if (directory == null
                || depthRemaining < 0
                || directoryBudget[0] <= 0
                || !directory.isDirectory()) {
            return;
        }

        directoryBudget[0]--;
        try {
            String canonicalPath = directory.getCanonicalPath();
            if (!isWithinSharedStorage(canonicalPath, rootCanonical)
                    || !visited.add(canonicalPath)) {
                return;
            }

            if (".thumbnails".equalsIgnoreCase(directory.getName())) {
                results.add(directory);
                return;
            }

            if (depthRemaining == 0) {
                return;
            }

            File[] children = directory.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                if (child.isDirectory()) {
                    findThumbnailDirectories(
                            child,
                            rootCanonical,
                            depthRemaining - 1,
                            directoryBudget,
                            visited,
                            results
                    );
                }
            }
        } catch (Exception e) {
            Log.w(CLEANUP_TAG, "Skipping inaccessible thumbnail path", e);
        }
    }

    private boolean isWithinSharedStorage(String path, String rootPath) {
        return path.equals(rootPath)
                || path.startsWith(rootPath + File.separator);
    }

    private void clearCacheDirectory(File directory) {
        if (directory == null || !directory.exists()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                clearCacheDirectory(file);
            }

            try {
                file.delete();
            } catch (SecurityException ignored) {
                // Skip files this app cannot delete.
            }
        }
    }

    private long getCacheSize() {
        long size = folderSize(getCacheDir());

        File external = getExternalCacheDir();
        if (external != null) {
            size += folderSize(external);
        }

        return size;
    }

    private long folderSize(File directory) {
        if (directory == null || !directory.exists()) {
            return 0L;
        }

        if (directory.isFile()) {
            return directory.length();
        }

        long size = 0L;
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                size += folderSize(file);
            }
        }

        return size;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }

        double kb = bytes / 1024.0;

        if (kb < 1024.0) {
            return String.format(
                    Locale.getDefault(), "%.1f KB", kb
            );
        }

        double mb = kb / 1024.0;

        if (mb < 1024.0) {
            return String.format(
                    Locale.getDefault(), "%.1f MB", mb
            );
        }

        return String.format(
                Locale.getDefault(), "%.2f GB", mb / 1024.0
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences accessibilityPreferences = getSharedPreferences(
                CleanupAccessibilityService.PREFERENCES,
                MODE_PRIVATE
        );

        boolean waitingToEnable = waitingForAccessibilitySettings
                || accessibilityPreferences.getBoolean(
                CleanupAccessibilityService.KEY_WAITING_FOR_ENABLE,
                false
        );
        if (waitingToEnable) {
            waitingForAccessibilitySettings = false;
            accessibilityPreferences.edit()
                    .putBoolean(CleanupAccessibilityService.KEY_WAITING_FOR_ENABLE, false)
                    .commit();

            String queuedPackages = accessibilityPreferences.getString(
                    CleanupAccessibilityService.KEY_PENDING_PACKAGES,
                    ""
            );
            if (isCleanupAccessibilityServiceEnabled()) {
                ArrayList<String> packageNames = splitPackageNames(queuedPackages);
                accessibilityPreferences.edit()
                        .remove(CleanupAccessibilityService.KEY_PENDING_PACKAGES)
                        .commit();
                if (!packageNames.isEmpty()) {
                    CleanupAccessibilityService.beginCleanup(this, packageNames);
                    status.setText("Opening Android Storage settings for selected apps...");
                }
            } else {
                accessibilityPreferences.edit()
                        .remove(CleanupAccessibilityService.KEY_PENDING_PACKAGES)
                        .commit();
                status.setText(
                        "Accessibility service was not enabled. "
                                + "Selected app caches were left unchanged."
                );
            }
            return;
        }

        if (accessibilityPreferences.getBoolean(
                CleanupAccessibilityService.KEY_COMPLETED,
                false)) {
            String result = accessibilityPreferences.getString(
                    CleanupAccessibilityService.KEY_RESULT,
                    "Selected app cache pass finished."
            );
            accessibilityPreferences.edit()
                    .putBoolean(CleanupAccessibilityService.KEY_COMPLETED, false)
                    .commit();
            status.setText(result);
            Toast.makeText(this, result, Toast.LENGTH_LONG).show();
            updateStorageInfo();
        }

        if (waitingForAllFilesAccess) {
            waitingForAllFilesAccess = false;
            cleanupRunning = false;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                    || Environment.isExternalStorageManager()) {
                runJunkCleanup();
            } else {
                finishJunkCleanup(
                        "All files access was not granted. Cleanup was skipped."
                );
            }
            return;
        }

        if (ramGauge != null
                && phoneStorage != null
                && sdStorage != null) {
            refreshDashboard();
        }
    }
}

