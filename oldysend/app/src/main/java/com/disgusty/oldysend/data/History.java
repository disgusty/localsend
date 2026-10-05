package com.disgusty.oldysend.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import com.disgusty.oldysend.util.Codec;
import com.disgusty.oldysend.util.IO;
import com.disgusty.oldysend.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Receive history, newest first. */
public final class History {
    public static final int MAX = 200;

    public static final class Entry {
        public String id = Codec.randomId();
        public String fileName;
        public String fileType;
        /** Path or content uri; null for messages. */
        public String location;
        public boolean savedToGallery;
        public boolean isMessage;
        public long fileSize;
        public String senderAlias;
        public long timestamp = System.currentTimeMillis();

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("fileName", fileName);
            o.put("fileType", fileType);
            if (location != null) o.put("location", location);
            o.put("savedToGallery", savedToGallery);
            o.put("isMessage", isMessage);
            o.put("fileSize", fileSize);
            o.put("senderAlias", senderAlias);
            o.put("timestamp", timestamp);
            return o;
        }

        static Entry fromJson(JSONObject o) {
            Entry e = new Entry();
            e.id = o.optString("id", e.id);
            e.fileName = o.optString("fileName", "");
            e.fileType = o.optString("fileType", "");
            e.location = o.isNull("location") ? null : o.optString("location", null);
            e.savedToGallery = o.optBoolean("savedToGallery", false);
            e.isMessage = o.optBoolean("isMessage", false);
            e.fileSize = o.optLong("fileSize", 0);
            e.senderAlias = o.optString("senderAlias", "");
            e.timestamp = o.optLong("timestamp", 0);
            return e;
        }
    }

    private final File file;
    private final List<Entry> entries = new ArrayList<Entry>();

    public History(Context context) {
        file = new File(context.getFilesDir(), "history.json");
        try {
            if (file.exists()) {
                JSONArray arr = new JSONArray(IO.readText(file));
                for (int i = 0; i < arr.length(); i++) entries.add(Entry.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            Log.w("Could not read history", e);
        }
    }

    public synchronized List<Entry> all() {
        return new ArrayList<Entry>(entries);
    }

    public synchronized void add(Entry entry) {
        entries.add(0, entry);
        while (entries.size() > MAX) entries.remove(entries.size() - 1);
        save();
    }

    public synchronized void remove(String id) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id.equals(id)) {
                entries.remove(i);
                break;
            }
        }
        save();
    }

    public synchronized void clear() {
        entries.clear();
        save();
    }

    private void save() {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : entries) arr.put(e.toJson());
            IO.writeText(file, arr.toString());
        } catch (Exception e) {
            Log.w("Could not save history", e);
        }
    }
}
