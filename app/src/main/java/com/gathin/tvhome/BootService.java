package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

/**
 * Puts HomeKeyService back after a reboot on firmware that clears the accessibility list at boot.
 *
 * TCL China sets wipe {@code enabled_accessibility_services} and set {@code accessibility_enabled} to 0
 * during boot, so the service that takes over the Home key is never bound and the stock launcher keeps
 * Home. This APK cannot declare CATEGORY_HOME (the same firmware refuses to install those), so the
 * receiver writes both settings back and opens the launcher; needs WRITE_SECURE_SETTINGS, granted once
 * over adb with {@code pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS}.
 *
 * Retried on a schedule: the vendor's clear does not happen at a fixed moment, and binding only follows
 * an enabled 0 → 1 transition after the service list is in place, so one write at BOOT_COMPLETED is not
 * enough. Each attempt re-writes the list and toggles the flag; the chain stops as soon as the system
 * reports the service as enabled.
 */
public class BootService extends BroadcastReceiver {

    private static final String TAG = "TvHome.Boot";
    private static final String SELF = "com.gathin.tvhome/.HomeKeyService";
    private static final String SELF_PACKAGE = "com.gathin.tvhome";
    /** Manual trigger, for checking the repair path without a reboot: adb shell am broadcast -a <this>. */
    static final String DEBUG_REPAIR = "com.gathin.tvhome.DEBUG_REPAIR";
    /** Spread over the first two minutes: the stock launcher and the vendor's clear run inside that window. */
    private static final long[] RETRY_MS = {2_000L, 10_000L, 20_000L, 35_000L, 55_000L, 80_000L, 120_000L};

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        final Context app = context.getApplicationContext();
        note(app, "onReceive " + intent.getAction());
        if (DEBUG_REPAIR.equals(intent.getAction())) {
            repair(app);
            openLauncher(app);
            note(app, "debug repair done, enabled=" + enabledFlag(app));
            return;
        }
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        final Handler handler = new Handler(Looper.getMainLooper());
        for (long delay : RETRY_MS) {
            handler.postDelayed(() -> {
                if (!isEnabled(app)) {
                    repair(app);
                    note(app, "retry@" + delay + " applied, enabled=" + enabledFlag(app) + " bound=" + isEnabled(app));
                } else {
                    note(app, "retry@" + delay + " already bound");
                }
                // Single-task activity: starting it again just brings the launcher back in front, so this
                // also recovers the screen after a late vendor clear.
                openLauncher(app);
            }, delay);
        }
    }

    /** True once the system has our service in its enabled list, so the remaining retries can stop. */
    static boolean isEnabled(Context context) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<AccessibilityServiceInfo> running =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        if (running == null) return false;
        for (AccessibilityServiceInfo info : running) {
            if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null
                    && SELF_PACKAGE.equals(info.getResolveInfo().serviceInfo.packageName)) return true;
        }
        return false;
    }

    /**
     * Writes the list, then flips the flag 0 → 1: the accessibility manager binds on that transition,
     * and setting the flag while it is already 1 changes nothing.
     *
     * Also called from MainActivity: this firmware never delivers BOOT_COMPLETED to a third-party app,
     * so opening the launcher once after a boot is what arms the service again.
     *
     * The steps are spaced out on the main looper. Written back to back the accessibility manager only
     * ever observes the final value and never rebinds; adb's natural delay between commands hid this.
     */
    static void repair(Context context) {
        final Context app = context.getApplicationContext();
        final ContentResolver cr = app.getContentResolver();
        final Handler handler = new Handler(Looper.getMainLooper());
        try {
            // Step 1: flag off, so the manager starts from "no services".
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            String current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            final String merged = (current == null || current.trim().isEmpty()) ? SELF : current + ":" + SELF;
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "");
            handler.postDelayed(() -> {
                try {
                    // Step 2: the list, empty first then filled, so the change is seen even when the value
                    // was already correct before the vendor cleared it.
                    Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, merged);
                    handler.postDelayed(() -> {
                        try {
                            // Step 3: flag on; this transition is what makes the system bind.
                            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                        } catch (SecurityException | IllegalArgumentException denied) {
                            Log.w(TAG, "Cannot switch accessibility on", denied);
                        }
                    }, 400L);
                } catch (SecurityException | IllegalArgumentException denied) {
                    Log.w(TAG, "Cannot restore the accessibility list", denied);
                }
            }, 400L);
        } catch (SecurityException | IllegalArgumentException denied) {
            // No WRITE_SECURE_SETTINGS: nothing can be restored from here, and the TV keeps its own Home.
            Log.w(TAG, "Cannot restore the accessibility service", denied);
        }
    }

    private static void openLauncher(Context context) {
        try {
            context.startActivity(new Intent(context, MainActivity.class)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (RuntimeException unavailable) {
            Log.w(TAG, "Cannot open the launcher", unavailable);
        }
    }

    private static int enabledFlag(Context context) {
        try {
            return Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, -1);
        } catch (RuntimeException unavailable) {
            return -1;
        }
    }

    /** Blind debugging: this firmware's logcat had rotated the boot window away, so attempts land in a file. */
    private static void note(Context context, String line) {
        try (java.io.FileOutputStream out = context.openFileOutput("boot.log", Context.MODE_APPEND)) {
            out.write((System.currentTimeMillis() + " " + line + "\n").getBytes("UTF-8"));
        } catch (Exception ignored) {
            // The log is a debugging aid only; never let it break the repair.
        }
    }
}
