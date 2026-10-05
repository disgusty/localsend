package com.disgusty.oldysend.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import com.disgusty.oldysend.files.MimeTypes;
import com.disgusty.oldysend.files.SendItem;
import com.disgusty.oldysend.i18n.I18n;
import com.disgusty.oldysend.proto.Device;
import com.disgusty.oldysend.ui.kit.Ic;
import com.disgusty.oldysend.util.IO;
import com.disgusty.oldysend.util.Text;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class UiUtil {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService THUMBS = new ThreadPoolExecutor(1, 2, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    private static final Map<String, Bitmap> CACHE = new LinkedHashMap<String, Bitmap>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            return size() > 40;
        }
    };

    private UiUtil() {
    }

    public static Ic deviceIcon(String type) {
        if (Device.TYPE_MOBILE.equals(type)) return Ic.SMARTPHONE;
        if (Device.TYPE_WEB.equals(type)) return Ic.LANGUAGE;
        if (Device.TYPE_HEADLESS.equals(type)) return Ic.TERMINAL;
        if (Device.TYPE_SERVER.equals(type)) return Ic.DNS;
        return Ic.COMPUTER;
    }

    public static Ic fileIcon(String mime, String name) {
        switch (MimeTypes.kind(mime, name)) {
            case IMAGE:
                return Ic.IMAGE;
            case VIDEO:
                return Ic.MOVIE;
            case AUDIO:
                return Ic.AUDIOTRACK;
            case PDF:
                return Ic.PICTURE_AS_PDF;
            case TEXT:
                return Ic.SUBJECT;
            case APK:
                return Ic.ANDROID;
            case ARCHIVE:
                return Ic.ARCHIVE;
            default:
                return Ic.INSERT_DRIVE_FILE;
        }
    }

    /** "#105" style visual id of an IPv4 address (LocalSend shows the last octet). */
    public static String visualId(String ip) {
        int dot = ip.lastIndexOf('.');
        return "#" + (dot >= 0 ? ip.substring(dot + 1) : ip);
    }

    /** Remaining time like LocalSend: "m:ss" or "1h 5m". */
    public static String remaining(long seconds) {
        I18n t = I18n.get();
        if (seconds < 0) seconds = 0;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) {
            return t.text("progressPage.remainingTime.hours", "h", h, "m", m);
        }
        return t.text("progressPage.remainingTime.minutes", "m", m, "ss", Text.twoDigits(s));
    }

    /** Loads a downscaled image thumbnail asynchronously (images only). */
    public static void thumbnail(final Context context, final SendItem item, final ImageView target, final int sizePx) {
        if (item.mime == null || !item.mime.startsWith("image/")) return;
        final String key = item.id + "@" + sizePx;
        Bitmap cached;
        synchronized (CACHE) {
            cached = CACHE.get(key);
        }
        if (cached != null) {
            target.setImageDrawable(new BitmapDrawable(cached));
            return;
        }
        target.setTag(key);
        THUMBS.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap b = decode(context, item.file, item.uri, sizePx);
                if (b == null) return;
                synchronized (CACHE) {
                    CACHE.put(key, b);
                }
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (key.equals(target.getTag())) target.setImageDrawable(new BitmapDrawable(b));
                    }
                });
            }
        });
    }

    @SuppressWarnings("deprecation")
    public static Drawable bitmapDrawable(Bitmap b) {
        return new BitmapDrawable(b);
    }

    static Bitmap decode(Context context, File file, Uri uri, int size) {
        InputStream in = null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            in = file != null ? new FileInputStream(file) : context.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, o);
            IO.close(in);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= size && o.outHeight / (sample * 2) >= size) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            in = file != null ? new FileInputStream(file) : context.getContentResolver().openInputStream(uri);
            Bitmap raw = BitmapFactory.decodeStream(in, null, o2);
            if (raw == null) return null;
            int s = Math.min(raw.getWidth(), raw.getHeight());
            Bitmap square = Bitmap.createBitmap(raw, (raw.getWidth() - s) / 2, (raw.getHeight() - s) / 2, s, s);
            return Bitmap.createScaledBitmap(square, size, size, true);
        } catch (Throwable e) {
            return null;
        } finally {
            IO.close(in);
        }
    }
}
