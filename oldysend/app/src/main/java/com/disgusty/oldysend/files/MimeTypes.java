package com.disgusty.oldysend.files;

import android.webkit.MimeTypeMap;

import com.disgusty.oldysend.util.Text;

/** MIME helpers. MimeTypeMap exists since API 1 but its table was tiny on early releases, hence the fallback. */
public final class MimeTypes {
    public static final String OCTET = "application/octet-stream";
    public static final String APK = "application/vnd.android.package-archive";

    public enum Kind {IMAGE, VIDEO, AUDIO, PDF, TEXT, APK, ARCHIVE, OTHER}

    private static final String[][] FALLBACK = {
            {"jpg", "image/jpeg"}, {"jpeg", "image/jpeg"}, {"png", "image/png"}, {"gif", "image/gif"},
            {"webp", "image/webp"}, {"bmp", "image/bmp"}, {"heic", "image/heic"}, {"heif", "image/heif"},
            {"svg", "image/svg+xml"}, {"mp4", "video/mp4"}, {"3gp", "video/3gpp"}, {"mkv", "video/x-matroska"},
            {"webm", "video/webm"}, {"mov", "video/quicktime"}, {"avi", "video/x-msvideo"}, {"mp3", "audio/mpeg"},
            {"ogg", "audio/ogg"}, {"m4a", "audio/mp4"}, {"wav", "audio/wav"}, {"flac", "audio/flac"},
            {"aac", "audio/aac"}, {"opus", "audio/opus"}, {"pdf", "application/pdf"}, {"txt", "text/plain"},
            {"html", "text/html"}, {"htm", "text/html"}, {"json", "application/json"}, {"xml", "text/xml"},
            {"csv", "text/csv"}, {"md", "text/markdown"}, {"zip", "application/zip"}, {"rar", "application/vnd.rar"},
            {"7z", "application/x-7z-compressed"}, {"tar", "application/x-tar"}, {"gz", "application/gzip"},
            {"apk", APK}, {"doc", "application/msword"},
            {"docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
            {"xls", "application/vnd.ms-excel"},
            {"xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"},
            {"ppt", "application/vnd.ms-powerpoint"},
            {"pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"},
            {"odt", "application/vnd.oasis.opendocument.text"}, {"epub", "application/epub+zip"},
    };

    private MimeTypes() {
    }

    public static String extension(String name) {
        int slash = name.lastIndexOf('/');
        int dot = name.lastIndexOf('.');
        if (dot <= slash + 1 || dot == name.length() - 1) return "";
        return Text.lower(name.substring(dot + 1));
    }

    public static String fromName(String name) {
        String ext = extension(name);
        if (ext.length() == 0) return OCTET;
        for (String[] f : FALLBACK) if (f[0].equals(ext)) return f[1];
        try {
            String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (m != null) return m;
        } catch (Throwable ignored) {
        }
        return OCTET;
    }

    public static String extensionFor(String mime) {
        if (mime == null) return null;
        for (String[] f : FALLBACK) if (f[1].equals(mime)) return f[0];
        try {
            return MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        } catch (Throwable e) {
            return null;
        }
    }

    public static Kind kind(String mime, String name) {
        String m = mime == null || OCTET.equals(mime) ? fromName(name == null ? "" : name) : mime;
        if (m.startsWith("image/")) return Kind.IMAGE;
        if (m.startsWith("video/")) return Kind.VIDEO;
        if (m.startsWith("audio/")) return Kind.AUDIO;
        if (m.equals("application/pdf")) return Kind.PDF;
        if (m.startsWith("text/")) return Kind.TEXT;
        if (m.equals(APK)) return Kind.APK;
        if (m.contains("zip") || m.contains("rar") || m.contains("7z") || m.contains("tar") || m.contains("gzip")) return Kind.ARCHIVE;
        return Kind.OTHER;
    }

    public static boolean isMedia(String mime) {
        return mime != null && (mime.startsWith("image/") || mime.startsWith("video/"));
    }
}
