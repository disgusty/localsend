package com.disgusty.oldysend.util;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;

public final class IO {
    public static final int BUFFER = 64 * 1024;

    private IO() {
    }

    public static void close(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Throwable ignored) {
        }
    }

    // Socket/ServerSocket/DatagramSocket only implement Closeable since Java 7 (Android API 19).
    public static void close(Socket s) {
        if (s == null) return;
        try {
            s.close();
        } catch (Throwable ignored) {
        }
    }

    public static void close(ServerSocket s) {
        if (s == null) return;
        try {
            s.close();
        } catch (Throwable ignored) {
        }
    }

    public static void close(DatagramSocket s) {
        if (s == null) return;
        try {
            s.close();
        } catch (Throwable ignored) {
        }
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    public static byte[] readLimited(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            if (out.size() + n > limit) throw new IOException("Body too large");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    public static String readText(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        try {
            return Text.utf8(readAll(in));
        } finally {
            close(in);
        }
    }

    /** Writes atomically through a temp file so a crash never leaves a truncated settings/history file. */
    public static void writeText(File file, String text) throws IOException {
        File tmp = new File(file.getPath() + ".tmp");
        FileOutputStream out = new FileOutputStream(tmp);
        try {
            out.write(Text.utf8(text));
            out.getFD().sync();
        } finally {
            close(out);
        }
        if (!tmp.renameTo(file)) {
            file.delete();
            if (!tmp.renameTo(file)) throw new IOException("rename failed: " + file);
        }
    }

    public interface Progress {
        /** @return false to abort */
        boolean onProgress(long transferred);
    }

    public static long copy(InputStream in, OutputStream out, Progress progress) throws IOException {
        byte[] buf = new byte[BUFFER];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            total += n;
            if (progress != null && !progress.onProgress(total)) throw new IOException("Cancelled");
        }
        return total;
    }

    public static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }
}
