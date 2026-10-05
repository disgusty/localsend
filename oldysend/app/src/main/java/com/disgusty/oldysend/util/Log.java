package com.disgusty.oldysend.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Logcat wrapper that also keeps a small in-memory ring buffer for the debug page. */
public final class Log {
    private static final String TAG = "OldySend";
    private static final int MAX = 400;
    private static final List<String> BUFFER = new ArrayList<String>();
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private Log() {
    }

    public static void d(String msg) {
        android.util.Log.d(TAG, msg);
        remember("D", msg);
    }

    public static void i(String msg) {
        android.util.Log.i(TAG, msg);
        remember("I", msg);
    }

    public static void w(String msg) {
        android.util.Log.w(TAG, msg);
        remember("W", msg);
    }

    public static void w(String msg, Throwable t) {
        android.util.Log.w(TAG, msg, t);
        remember("W", msg + ": " + t);
    }

    public static void e(String msg, Throwable t) {
        android.util.Log.e(TAG, msg, t);
        remember("E", msg + ": " + t);
    }

    private static void remember(String level, String msg) {
        synchronized (BUFFER) {
            String time;
            synchronized (TIME) {
                time = TIME.format(new Date());
            }
            BUFFER.add(time + " " + level + " " + msg);
            if (BUFFER.size() > MAX) BUFFER.remove(0);
        }
    }

    public static List<String> snapshot() {
        synchronized (BUFFER) {
            return new ArrayList<String>(BUFFER);
        }
    }
}
