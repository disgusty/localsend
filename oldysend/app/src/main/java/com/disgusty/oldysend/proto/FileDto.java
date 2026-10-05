package com.disgusty.oldysend.proto;

import org.json.JSONException;
import org.json.JSONObject;

/** File metadata exchanged in prepare-upload / prepare-download. */
public final class FileDto {
    public String id;
    public String fileName;
    public long size;
    public String fileType = "application/octet-stream";
    public String sha256;
    public String preview;
    public String modified;
    public String accessed;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("fileName", fileName);
        o.put("size", size);
        o.put("fileType", fileType);
        if (sha256 != null) o.put("sha256", sha256);
        if (preview != null) o.put("preview", preview);
        if (modified != null || accessed != null) {
            JSONObject meta = new JSONObject();
            if (modified != null) meta.put("modified", modified);
            if (accessed != null) meta.put("accessed", accessed);
            o.put("metadata", meta);
        }
        return o;
    }

    public static FileDto fromJson(JSONObject o) {
        FileDto f = new FileDto();
        f.id = o.optString("id", null);
        f.fileName = o.optString("fileName", "file");
        f.size = o.optLong("size", 0);
        f.fileType = o.optString("fileType", "application/octet-stream");
        f.sha256 = o.isNull("sha256") ? null : o.optString("sha256", null);
        f.preview = o.isNull("preview") ? null : o.optString("preview", null);
        JSONObject meta = o.optJSONObject("metadata");
        if (meta != null) {
            f.modified = meta.isNull("modified") ? null : meta.optString("modified", null);
            f.accessed = meta.isNull("accessed") ? null : meta.optString("accessed", null);
        }
        return f;
    }

    /** LocalSend sends text messages as a single text/plain "file" whose content is in the preview. */
    public boolean isMessage() {
        return preview != null && fileType != null && fileType.startsWith("text/");
    }
}
