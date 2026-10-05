package com.disgusty.oldysend.util;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Locale;

/** String helpers limited to the Java 5 library level of Android 1.0. */
public final class Text {
    private Text() {
    }

    public static boolean isEmpty(String s) {
        return s == null || s.length() == 0;
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().length() == 0;
    }

    public static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    public static boolean equals(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    public static byte[] utf8(String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String utf8(byte[] bytes, int offset, int length) {
        try {
            return new String(bytes, offset, length, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String utf8(byte[] bytes) {
        return utf8(bytes, 0, bytes.length);
    }

    public static String latin1(byte[] bytes, int offset, int length) {
        try {
            return new String(bytes, offset, length, "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String join(String separator, List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(separator);
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    public static String lower(String s) {
        return s.toLowerCase(Locale.ENGLISH);
    }

    public static String upper(String s) {
        return s.toUpperCase(Locale.ENGLISH);
    }

    /** Replaces every literal occurrence (String.replace(CharSequence,...) is Java 5 but behaves oddly on early Harmony). */
    public static String replace(String s, String target, String replacement) {
        if (target.length() == 0) return s;
        int idx = s.indexOf(target);
        if (idx < 0) return s;
        StringBuilder sb = new StringBuilder(s.length() + 16);
        int from = 0;
        while (idx >= 0) {
            sb.append(s, from, idx).append(replacement);
            from = idx + target.length();
            idx = s.indexOf(target, from);
        }
        sb.append(s, from, s.length());
        return sb.toString();
    }

    private static final String[] SIZE_UNITS = {"B", "KB", "MB", "GB", "TB"};

    /** Formats bytes like LocalSend does (1024 based, one decimal). */
    public static String fileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < SIZE_UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        long tenths = Math.round(value * 10);
        String number = (tenths % 10 == 0) ? String.valueOf(tenths / 10) : (tenths / 10) + "." + (tenths % 10);
        return number + " " + SIZE_UNITS[unit];
    }

    public static String twoDigits(long n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    public static boolean isHttpUrl(String s) {
        if (s == null) return false;
        String t = lower(s.trim());
        return (t.startsWith("http://") || t.startsWith("https://")) && t.indexOf(' ') < 0 && t.indexOf('\n') < 0;
    }
}
