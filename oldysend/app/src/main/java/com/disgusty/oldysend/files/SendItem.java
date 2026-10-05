package com.disgusty.oldysend.files;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.disgusty.oldysend.util.Codec;
import com.disgusty.oldysend.util.IO;
import com.disgusty.oldysend.util.Text;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** One entry of the send selection: a file path, a content uri, or an in-memory text message. */
public final class SendItem {
    public final String id = Codec.randomId();
    /** File name, possibly with a relative folder path ("Photos/a.jpg") when a folder was added. */
    public String name;
    public long size;
    public String mime;
    public File file;
    public Uri uri;
    public byte[] bytes;
    /** Message text (sent in the preview field) for text items. */
    public String text;
    public long modified;
    public String sha256;

    public static SendItem ofFile(File f, String relativeName) {
        SendItem item = new SendItem();
        item.file = f;
        item.name = relativeName != null ? relativeName : f.getName();
        item.size = f.length();
        item.mime = MimeTypes.fromName(f.getName());
        item.modified = f.lastModified();
        return item;
    }

    public static SendItem ofText(String text) {
        SendItem item = new SendItem();
        item.text = text;
        item.bytes = Text.utf8(text);
        item.size = item.bytes.length;
        item.mime = "text/plain";
        item.name = Codec.randomId() + ".txt";
        return item;
    }

    public static SendItem ofApk(File apk, String label, String version) {
        SendItem item = ofFile(apk, null);
        String base = label.replace('/', '_');
        item.name = Text.isEmpty(version) ? base + ".apk" : base + " - v" + version + ".apk";
        item.mime = MimeTypes.APK;
        return item;
    }

    /** Resolves name and size through the content provider (OpenableColumns exists since API 1). */
    public static SendItem ofUri(Context context, Uri uri, String relativeName) {
        if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
            return ofFile(new File(uri.getPath()), relativeName);
        }
        SendItem item = new SendItem();
        item.uri = uri;
        String name = null;
        long size = -1;
        Cursor c = null;
        try {
            c = context.getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int si = c.getColumnIndex(OpenableColumns.SIZE);
                if (ni >= 0) name = c.getString(ni);
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si);
                if (name == null) {
                    int di = c.getColumnIndex("_data");
                    if (di >= 0) {
                        String data = c.getString(di);
                        if (data != null) {
                            name = new File(data).getName();
                            if (size < 0) size = new File(data).length();
                        }
                    }
                }
                int mi = c.getColumnIndex("last_modified");
                if (mi < 0) mi = c.getColumnIndex("date_modified");
                if (mi >= 0 && !c.isNull(mi)) {
                    long m = c.getLong(mi);
                    item.modified = m < 100000000000L ? m * 1000 : m;
                }
            }
        } catch (Exception ignored) {
            // Some old providers reject null projections.
        } finally {
            if (c != null) c.close();
        }
        item.mime = context.getContentResolver().getType(uri);
        if (name == null) {
            name = uri.getLastPathSegment();
            if (name == null) name = "file";
            String ext = MimeTypes.extensionFor(item.mime);
            if (ext != null && MimeTypes.extension(name).length() == 0) name = name + "." + ext;
        }
        if (size < 0) size = measure(context, uri);
        item.name = relativeName != null ? relativeName : name;
        item.size = Math.max(0, size);
        if (item.mime == null || MimeTypes.OCTET.equals(item.mime)) item.mime = MimeTypes.fromName(name);
        return item;
    }

    private static long measure(Context context, Uri uri) {
        InputStream in = null;
        try {
            in = context.getContentResolver().openInputStream(uri);
            byte[] buf = new byte[IO.BUFFER];
            long total = 0;
            int n;
            while ((n = in.read(buf)) != -1) total += n;
            return total;
        } catch (Exception e) {
            return 0;
        } finally {
            IO.close(in);
        }
    }

    public InputStream open(Context context) throws IOException {
        if (bytes != null) return new ByteArrayInputStream(bytes);
        if (file != null) return new FileInputStream(file);
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Cannot open " + uri);
        return in;
    }

    public boolean isText() {
        return text != null;
    }

    public String computeSha256(Context context) throws IOException {
        MessageDigest md = Codec.sha256Digest();
        InputStream in = open(context);
        try {
            byte[] buf = new byte[IO.BUFFER];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        } finally {
            IO.close(in);
        }
        sha256 = Codec.hexLower(md.digest());
        return sha256;
    }

    /** RFC 3339 UTC timestamp for the metadata field. */
    public static String iso(long millis) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(millis));
    }

    public static long parseIso(String s) {
        if (s == null) return 0;
        String[] patterns = {"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'"};
        String v = s;
        // Trim fractional seconds beyond milliseconds (Rust sends nanoseconds).
        int dot = v.indexOf('.');
        if (dot > 0 && v.endsWith("Z") && v.length() - dot > 5) v = v.substring(0, dot + 4) + "Z";
        for (String p : patterns) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                return f.parse(v).getTime();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }
}
