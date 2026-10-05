package com.disgusty.oldysend.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import com.disgusty.oldysend.util.Codec;
import com.disgusty.oldysend.util.IO;
import com.disgusty.oldysend.util.Log;
import com.disgusty.oldysend.util.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Favorite devices (fingerprint + last known address), like LocalSend's favorites list. */
public final class Favorites {
    public static final class Entry {
        public String id;
        public String fingerprint;
        public String ip;
        public int port;
        public String alias;
        /** True when the user typed the name; otherwise it follows the device's alias. */
        public boolean customAlias;
        public boolean https;

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("fingerprint", fingerprint);
            o.put("ip", ip);
            o.put("port", port);
            o.put("alias", alias);
            o.put("customAlias", customAlias);
            o.put("https", https);
            return o;
        }

        static Entry fromJson(JSONObject o) {
            Entry e = new Entry();
            e.id = o.optString("id", Codec.randomId());
            e.fingerprint = o.optString("fingerprint", "");
            e.ip = o.optString("ip", "");
            e.port = o.optInt("port", Settings.DEFAULT_PORT);
            e.alias = o.optString("alias", "");
            e.customAlias = o.optBoolean("customAlias", false);
            e.https = o.optBoolean("https", true);
            return e;
        }
    }

    private final File file;
    private final List<Entry> entries = new ArrayList<Entry>();

    public Favorites(Context context) {
        file = new File(context.getFilesDir(), "favorites.json");
        try {
            if (file.exists()) {
                JSONArray arr = new JSONArray(IO.readText(file));
                for (int i = 0; i < arr.length(); i++) entries.add(Entry.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            Log.w("Could not read favorites", e);
        }
    }

    public synchronized List<Entry> all() {
        return new ArrayList<Entry>(entries);
    }

    public synchronized Entry byFingerprint(String fingerprint) {
        if (Text.isEmpty(fingerprint)) return null;
        for (Entry e : entries) if (fingerprint.equalsIgnoreCase(e.fingerprint)) return e;
        return null;
    }

    public synchronized boolean isFavorite(String fingerprint) {
        return byFingerprint(fingerprint) != null;
    }

    public synchronized void put(Entry entry) {
        if (entry.id == null) entry.id = Codec.randomId();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id.equals(entry.id)) {
                entries.set(i, entry);
                save();
                return;
            }
        }
        entries.add(entry);
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

    /** Keeps address and (non-custom) alias in sync when a favorite is discovered. */
    public synchronized void updateFromDiscovery(String fingerprint, String ip, int port, String alias, boolean https) {
        Entry e = byFingerprint(fingerprint);
        if (e == null) return;
        boolean changed = !ip.equals(e.ip) || port != e.port || e.https != https || (!e.customAlias && !alias.equals(e.alias));
        if (!changed) return;
        e.ip = ip;
        e.port = port;
        e.https = https;
        if (!e.customAlias) e.alias = alias;
        save();
    }

    private void save() {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : entries) arr.put(e.toJson());
            IO.writeText(file, arr.toString());
        } catch (Exception e) {
            Log.w("Could not save favorites", e);
        }
    }
}
