package com.disgusty.oldysend.util;

import android.os.Build;

import androidx.annotation.ChecksSdkIntAtLeast;

/**
 * Platform version checks that work on every Android release.
 *
 * {@code Build.VERSION.SDK_INT} only exists since Android 1.6 (API 4): reading it on 1.0–1.5 throws
 * NoSuchFieldError, so the string field {@code Build.VERSION.SDK} (API 1) is parsed instead.
 */
public final class Sdk {
    public static final int INT = readSdkInt();

    private Sdk() {
    }

    @SuppressWarnings("deprecation")
    private static int readSdkInt() {
        try {
            return Integer.parseInt(Build.VERSION.SDK.trim());
        } catch (Throwable e) {
            return 1;
        }
    }

    @ChecksSdkIntAtLeast(parameter = 0)
    public static boolean atLeast(int api) {
        return INT >= api;
    }

    /** Human-readable release name, e.g. "2.3.7". */
    public static String release() {
        String release = Build.VERSION.RELEASE;
        return release == null ? String.valueOf(INT) : release;
    }
}
