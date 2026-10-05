package com.disgusty.oldysend.service;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.disgusty.oldysend.R;
import com.disgusty.oldysend.i18n.I18n;
import com.disgusty.oldysend.proto.ReceiveController;
import com.disgusty.oldysend.ui.MainActivity;
import com.disgusty.oldysend.ui.ReceiveActivity;
import com.disgusty.oldysend.util.Log;
import com.disgusty.oldysend.util.Sdk;

import java.lang.reflect.Method;

/** Notifications for every API level: Notification.Builder from 3.0, the 1.x/2.x constructor before. */
public final class Notifs {
    public static final int ID_SERVICE = 1;
    public static final int ID_REQUEST = 2;
    static final String CHANNEL_SERVICE = "service";
    static final String CHANNEL_REQUESTS = "requests";

    private Notifs() {
    }

    public static void init(Context c) {
        if (Sdk.atLeast(26)) Api26.channels(c);
    }

    static NotificationManager manager(Context c) {
        return (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @android.annotation.SuppressLint({"WrongConstant", "InlinedApi"})
    static PendingIntent activity(Context c, Class<?> cls, int requestCode) {
        Intent i = new Intent(c, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int flags = Sdk.atLeast(23) ? 0x04000000 /* FLAG_IMMUTABLE */ : 0;
        return PendingIntent.getActivity(c, requestCode, i, flags | (Sdk.atLeast(3) ? PendingIntent.FLAG_UPDATE_CURRENT : 0));
    }

    @SuppressWarnings("deprecation")
    public static Notification build(Context c, String channel, String title, String text, PendingIntent pi, boolean ongoing, boolean alert) {
        if (Sdk.atLeast(11)) return Api11.build(c, channel, title, text, pi, ongoing, alert);
        Notification n = new Notification(R.drawable.ic_stat_oldysend, alert ? title : null, System.currentTimeMillis());
        try {
            // Removed from the SDK in API 23 but present on the old platforms this branch runs on.
            Method m = Notification.class.getMethod("setLatestEventInfo", Context.class, CharSequence.class, CharSequence.class, PendingIntent.class);
            m.invoke(n, c, title, text, pi);
        } catch (Exception e) {
            Log.w("setLatestEventInfo failed", e);
        }
        if (ongoing) n.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
        else n.flags |= Notification.FLAG_AUTO_CANCEL;
        if (alert) n.defaults |= Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE;
        return n;
    }

    public static Notification service(Context c, boolean transfer) {
        I18n t = I18n.get();
        String title = transfer ? t.text("oldy.notifTransfer") : t.text("oldy.notifServer");
        return build(c, CHANNEL_SERVICE, title, t.text("appName"), activity(c, MainActivity.class, 1), true, false);
    }

    public static void showRequest(Context c, ReceiveController.Session s) {
        I18n t = I18n.get();
        String title = s.sender.alias;
        String text = s.message != null ? t.text("receivePage.subTitleMessage") : t.plural("receivePage.subTitle", s.files.size());
        try {
            manager(c).notify(ID_REQUEST, build(c, CHANNEL_REQUESTS, title, text, activity(c, ReceiveActivity.class, 2), false, true));
        } catch (Exception e) {
            Log.w("Could not post notification", e);
        }
    }

    public static void cancelRequest(Context c) {
        try {
            manager(c).cancel(ID_REQUEST);
        } catch (Exception ignored) {
        }
    }

    @TargetApi(11)
    private static final class Api11 {
        @SuppressWarnings("deprecation")
        static Notification build(Context c, String channel, String title, String text, PendingIntent pi, boolean ongoing, boolean alert) {
            Notification.Builder b = Sdk.atLeast(26) ? Api26.builder(c, channel) : new Notification.Builder(c);
            b.setSmallIcon(R.drawable.ic_stat_oldysend)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(pi)
                    .setOngoing(ongoing)
                    .setAutoCancel(!ongoing)
                    .setWhen(System.currentTimeMillis());
            if (alert) {
                b.setTicker(title);
                b.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
            }
            if (Sdk.atLeast(16)) return Api16.build(b, alert);
            return b.getNotification();
        }
    }

    @TargetApi(16)
    private static final class Api16 {
        static Notification build(Notification.Builder b, boolean alert) {
            if (alert) b.setPriority(Notification.PRIORITY_HIGH);
            return b.build();
        }
    }

    @TargetApi(26)
    private static final class Api26 {
        static Notification.Builder builder(Context c, String channel) {
            return new Notification.Builder(c, channel);
        }

        static void channels(Context c) {
            I18n t = I18n.get();
            NotificationManager nm = manager(c);
            NotificationChannel service = new NotificationChannel(CHANNEL_SERVICE, t.text("oldy.channelService"), NotificationManager.IMPORTANCE_LOW);
            NotificationChannel requests = new NotificationChannel(CHANNEL_REQUESTS, t.text("oldy.channelRequests"), NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(service);
            nm.createNotificationChannel(requests);
        }
    }
}
