package com.disgusty.oldysend.service;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;

import com.disgusty.oldysend.App;
import com.disgusty.oldysend.proto.Core;
import com.disgusty.oldysend.util.Log;
import com.disgusty.oldysend.util.Sdk;

import java.lang.reflect.Method;

/**
 * Keeps the process (and the HTTP server) alive while a transfer runs or when "keep running" is on,
 * with wake and Wi-Fi locks so old phones do not drop the connection when the screen turns off.
 */
public final class ServerService extends Service {
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private boolean foreground;

    /** Starts, updates or stops the service according to the current state. */
    public static void update(Context c) {
        boolean needed = App.get().transferActive() || Core.get().settings.keepRunning();
        Intent i = new Intent(c, ServerService.class);
        try {
            if (needed) {
                if (Sdk.atLeast(26)) Api26.startForegroundService(c, i);
                else c.startService(i);
            } else {
                c.stopService(i);
            }
        } catch (Exception e) {
            // Android 12+ refuses foreground services started from the background.
            Log.w("Could not start service", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OldySend:transfer");
            wakeLock.setReferenceCounted(false);
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null && Sdk.atLeast(3)) {
                wifiLock = Api3.wifiLock(wm);
                wifiLock.setReferenceCounted(false);
            }
        } catch (Exception e) {
            Log.w("No locks", e);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onStart(Intent intent, int startId) {
        // onStartCommand only exists since API 5; onStart is called on every version.
        refresh();
    }

    private void refresh() {
        boolean transfer = App.get().transferActive();
        Notification n = Notifs.service(this, transfer);
        // startForegroundService() obliges us to call startForeground() even if the transfer already ended.
        goForeground(n);
        if (!transfer && !Core.get().settings.keepRunning()) {
            stopSelfCompat();
            return;
        }
        try {
            if (transfer) {
                if (wakeLock != null) wakeLock.acquire();
                if (wifiLock != null) wifiLock.acquire();
            } else {
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
            }
        } catch (Exception e) {
            Log.w("Lock error", e);
        }
        if (!Core.get().serverRunning() && !Core.get().serverStarting()) Core.get().startServer();
    }

    private void goForeground(Notification n) {
        try {
            if (Sdk.atLeast(29)) Api29.startForeground(this, n);
            else if (Sdk.atLeast(5)) Api5.startForeground(this, n);
            else {
                legacySetForeground(true);
                Notifs.manager(this).notify(Notifs.ID_SERVICE, n);
            }
            foreground = true;
        } catch (Exception e) {
            Log.w("startForeground failed", e);
        }
    }

    private void legacySetForeground(boolean value) {
        try {
            Method m = Service.class.getMethod("setForeground", boolean.class);
            m.invoke(this, value);
        } catch (Exception ignored) {
        }
    }

    private void stopSelfCompat() {
        if (foreground) {
            if (Sdk.atLeast(5)) Api5.stopForeground(this);
            else {
                legacySetForeground(false);
                Notifs.manager(this).cancel(Notifs.ID_SERVICE);
            }
            foreground = false;
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Exception ignored) {
        }
        if (!Sdk.atLeast(5)) Notifs.manager(this).cancel(Notifs.ID_SERVICE);
        super.onDestroy();
    }

    @TargetApi(5)
    private static final class Api5 {
        static void startForeground(Service s, Notification n) {
            s.startForeground(Notifs.ID_SERVICE, n);
        }

        @SuppressWarnings("deprecation")
        static void stopForeground(Service s) {
            s.stopForeground(true);
        }
    }

    @TargetApi(26)
    private static final class Api26 {
        static void startForegroundService(Context c, Intent i) {
            c.startForegroundService(i);
        }
    }

    @TargetApi(29)
    private static final class Api29 {
        static void startForeground(Service s, Notification n) {
            s.startForeground(Notifs.ID_SERVICE, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        }
    }

    @TargetApi(3)
    private static final class Api3 {
        @SuppressWarnings("deprecation")
        static WifiManager.WifiLock wifiLock(WifiManager wm) {
            return wm.createWifiLock(Sdk.atLeast(12) ? 3 /* WIFI_MODE_FULL_HIGH_PERF */ : WifiManager.WIFI_MODE_FULL, "OldySend");
        }
    }
}