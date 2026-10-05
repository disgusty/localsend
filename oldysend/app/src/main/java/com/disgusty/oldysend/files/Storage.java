package com.disgusty.oldysend.files;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import com.disgusty.oldysend.util.IO;
import com.disgusty.oldysend.util.Log;
import com.disgusty.oldysend.util.Sdk;
import com.disgusty.oldysend.util.Text;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Where received files go:
 * - default: the public Downloads folder (File API before Android 10, MediaStore.Downloads afterwards),
 * - a custom folder: a path (any version) or a SAF tree uri (Android 5+),
 * - images/videos with "save to gallery": Pictures|Movies/OldySend.
 */
public final class Storage {
    public static final String APP_FOLDER = "OldySend";

    /** An open destination for one incoming file. */
    public static final class Target {
        public OutputStream out;
        /** Absolute path or content uri, stored in the history. */
        public String location;
        public String displayName;
        File file;
        Uri pendingUri;
        boolean gallery;

        public boolean savedToGallery() {
            return gallery;
        }
    }

    private Storage() {
    }

    @SuppressWarnings("deprecation")
    public static File defaultDownloadDir() {
        if (Sdk.atLeast(8)) return Api8.downloads();
        return new File(Environment.getExternalStorageDirectory(), "download");
    }

    /** Human readable name of the destination setting. */
    public static String describe(Context context, String destination) {
        if (destination == null) return defaultDownloadDir().getAbsolutePath();
        if (destination.startsWith("content://")) {
            Uri uri = Uri.parse(destination);
            String last = uri.getLastPathSegment();
            if (last != null) {
                int colon = last.lastIndexOf(':');
                return colon >= 0 ? "/" + last.substring(colon + 1) : last;
            }
        }
        return destination;
    }

    /**
     * Opens a target for {@code relativeName} (may contain folders from a folder transfer).
     */
    public static Target open(Context context, String destination, String relativeName, String mime, boolean toGallery) throws IOException {
        String clean = sanitizeRelative(relativeName);
        if (toGallery && MimeTypes.isMedia(mime) && clean.indexOf('/') < 0) {
            if (Sdk.atLeast(29)) return Api29.openMediaStore(context, galleryCollection(mime), galleryFolder(mime), clean, mime, true);
            File dir = new File(Environment.getExternalStorageDirectory(), galleryFolder(mime));
            Target t = openFile(dir, clean);
            t.gallery = true;
            return t;
        }
        if (destination != null && destination.startsWith("content://") && Sdk.atLeast(21)) {
            return Api21.openTree(context, Uri.parse(destination), clean, mime);
        }
        if (destination == null && Sdk.atLeast(29)) {
            return Api29.openMediaStore(context, Api29.downloadsCollection(), "Download", clean, mime, false);
        }
        File base = destination == null ? defaultDownloadDir() : new File(destination);
        return openFile(base, clean);
    }

    /** Makes the finished file visible (timestamps, MediaStore pending flag, media scanner). */
    public static void finish(Context context, Target t, long modified) {
        IO.close(t.out);
        if (t.file != null) {
            if (modified > 0) t.file.setLastModified(modified);
            scan(context, t.file);
        }
        if (t.pendingUri != null && Sdk.atLeast(29)) Api29.publish(context, t.pendingUri);
    }

    /** Removes a partially written file after a failed transfer. */
    public static void abort(Context context, Target t) {
        IO.close(t.out);
        try {
            if (t.file != null) t.file.delete();
            else if (t.location != null && t.location.startsWith("content://")) {
                context.getContentResolver().delete(Uri.parse(t.location), null, null);
            }
        } catch (Exception e) {
            Log.w("Could not delete partial file", e);
        }
    }

    @SuppressWarnings("deprecation")
    private static void scan(Context context, File f) {
        try {
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(f)));
        } catch (Exception ignored) {
        }
    }

    private static String galleryFolder(String mime) {
        return (mime.startsWith("video/") ? "Movies" : "Pictures") + "/" + APP_FOLDER;
    }

    private static Uri galleryCollection(String mime) {
        return mime.startsWith("video/") ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    }

    private static Target openFile(File base, String relative) throws IOException {
        File f = new File(base, relative);
        File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create folder " + parent);
        f = unique(f);
        Target t = new Target();
        t.file = f;
        t.out = new FileOutputStream(f);
        t.location = f.getAbsolutePath();
        t.displayName = f.getName();
        return t;
    }

    /** "name.ext" → "name (1).ext", like LocalSend. */
    static File unique(File f) {
        if (!f.exists()) return f;
        String name = f.getName();
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            ext = name.substring(dot);
            name = name.substring(0, dot);
        }
        for (int i = 1; i < 10000; i++) {
            File c = new File(f.getParentFile(), name + " (" + i + ")" + ext);
            if (!c.exists()) return c;
        }
        return f;
    }

    /** Strips path traversal and characters that file systems reject. */
    public static String sanitizeRelative(String name) {
        String n = name == null ? "file" : name.replace('\\', '/');
        StringBuilder out = new StringBuilder();
        for (String part : n.split("/")) {
            if (part.length() == 0 || ".".equals(part) || "..".equals(part)) continue;
            StringBuilder p = new StringBuilder();
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c < 32 || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|') p.append('_');
                else p.append(c);
            }
            if (out.length() > 0) out.append('/');
            out.append(p);
        }
        return out.length() == 0 ? "file" : out.toString();
    }

    public static boolean isValidFileName(String name) {
        if (Text.isBlank(name)) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32 || "\\/:*?\"<>|".indexOf(c) >= 0) return false;
        }
        return true;
    }

    /** Uri for opening a received file with another app. */
    public static Uri uriFor(Context context, String location) {
        if (location.startsWith("content://")) return Uri.parse(location);
        File f = new File(location);
        if (Sdk.atLeast(24)) return FileProvider.uriFor(context, f);
        return Uri.fromFile(f);
    }

    public static boolean exists(Context context, String location) {
        if (location == null) return false;
        if (!location.startsWith("content://")) return new File(location).exists();
        Cursor c = null;
        try {
            c = context.getContentResolver().query(Uri.parse(location), null, null, null, null);
            return c != null && c.moveToFirst();
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.close();
        }
    }

    public static void delete(Context context, String location) {
        try {
            if (location.startsWith("content://")) context.getContentResolver().delete(Uri.parse(location), null, null);
            else new File(location).delete();
        } catch (Exception e) {
            Log.w("Could not delete " + location, e);
        }
    }

    @TargetApi(8)
    private static final class Api8 {
        static File downloads() {
            return android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
        }
    }

    @TargetApi(21)
    private static final class Api21 {
        static Target openTree(Context context, Uri tree, String relative, String mime) throws IOException {
            ContentResolver cr = context.getContentResolver();
            try {
                Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
                String[] parts = relative.split("/");
                for (int i = 0; i < parts.length - 1; i++) {
                    Uri existing = findChild(cr, tree, parent, parts[i]);
                    parent = existing != null ? existing
                            : DocumentsContract.createDocument(cr, parent, DocumentsContract.Document.MIME_TYPE_DIR, parts[i]);
                    if (parent == null) throw new IOException("Cannot create folder " + parts[i]);
                }
                String name = parts[parts.length - 1];
                Uri doc = DocumentsContract.createDocument(cr, parent, mime == null ? MimeTypes.OCTET : mime, name);
                if (doc == null) throw new IOException("Cannot create " + name);
                Target t = new Target();
                t.out = cr.openOutputStream(doc);
                if (t.out == null) throw new IOException("Cannot write " + name);
                t.location = doc.toString();
                t.displayName = name;
                return t;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Storage error: " + e);
            }
        }

        private static Uri findChild(ContentResolver cr, Uri tree, Uri parent, String name) {
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent));
            Cursor c = null;
            try {
                c = cr.query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
                while (c != null && c.moveToNext()) {
                    if (name.equals(c.getString(1)) && DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2))) {
                        return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0));
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.close();
            }
            return null;
        }
    }

    @TargetApi(29)
    static final class Api29 {
        static Uri downloadsCollection() {
            return MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        }

        static Target openMediaStore(Context context, Uri collection, String folder, String relative, String mime, boolean gallery) throws IOException {
            String name = relative;
            String sub = "";
            int slash = relative.lastIndexOf('/');
            if (slash >= 0) {
                sub = "/" + relative.substring(0, slash);
                name = relative.substring(slash + 1);
            }
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            if (mime != null) v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, folder + sub + "/");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            ContentResolver cr = context.getContentResolver();
            Uri uri;
            try {
                uri = cr.insert(collection, v);
            } catch (Exception e) {
                throw new IOException("MediaStore insert failed: " + e);
            }
            if (uri == null) throw new IOException("MediaStore insert failed");
            Target t = new Target();
            t.out = cr.openOutputStream(uri);
            if (t.out == null) throw new IOException("Cannot write " + name);
            t.location = uri.toString();
            t.displayName = name;
            t.pendingUri = uri;
            t.gallery = gallery;
            return t;
        }

        static void publish(Context context, Uri uri) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.IS_PENDING, 0);
                context.getContentResolver().update(uri, v, null, null);
            } catch (Exception e) {
                Log.w("Could not publish " + uri, e);
            }
        }
    }
}
