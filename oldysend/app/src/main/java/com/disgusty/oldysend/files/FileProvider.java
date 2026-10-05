package com.disgusty.oldysend.files;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Read-only provider for received files, needed to open files with other apps on Android 7+
 * (file:// uris are forbidden there). Only files below external storage or the app's own folders are served.
 */
public final class FileProvider extends ContentProvider {
    public static final String AUTHORITY = "com.disgusty.oldysend.files";

    public static Uri uriFor(Context context, File file) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).encodedPath(Uri.encode(file.getAbsolutePath(), "/")).build();
    }

    private File fileOf(Uri uri) throws FileNotFoundException {
        String path = uri.getPath();
        if (path == null) throw new FileNotFoundException();
        File f = new File(path);
        try {
            String canonical = f.getCanonicalPath();
            String ext = android.os.Environment.getExternalStorageDirectory().getCanonicalPath();
            String own = getContext().getFilesDir().getParentFile().getCanonicalPath();
            if (!canonical.startsWith(ext) && !canonical.startsWith(own) && !canonical.startsWith("/storage/") && !canonical.startsWith("/mnt/")) {
                throw new FileNotFoundException("Not allowed");
            }
        } catch (java.io.IOException e) {
            throw new FileNotFoundException(e.toString());
        }
        return f;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File f;
        try {
            f = fileOf(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        String[] cols = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
        }
        c.addRow(row);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        String path = uri.getPath();
        return path == null ? MimeTypes.OCTET : MimeTypes.fromName(path);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(fileOf(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
