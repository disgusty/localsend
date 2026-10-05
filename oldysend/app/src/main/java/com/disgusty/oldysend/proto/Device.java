package com.disgusty.oldysend.proto;

import org.json.JSONObject;
import com.disgusty.oldysend.util.Text;

/** A LocalSend peer as seen by discovery (protocol v2). */
public final class Device {
    public static final String TYPE_MOBILE = "mobile";
    public static final String TYPE_DESKTOP = "desktop";
    public static final String TYPE_WEB = "web";
    public static final String TYPE_HEADLESS = "headless";
    public static final String TYPE_SERVER = "server";

    public String ip;
    public int port;
    public boolean https;
    public String alias;
    public String version = Protocol.VERSION;
    public String deviceModel;
    public String deviceType = TYPE_DESKTOP;
    public String fingerprint;
    public boolean download;
    /** "multicast", "http" or "manual", for the device details page. */
    public String discoveredVia = "http";
    public long lastSeen;
    /** False when the payload had no port/protocol (prepare-upload "info"), so a known endpoint must not be overwritten. */
    public boolean hasEndpoint;
    /** True when an HTTP exchange with {@link #ip} happened; multicast source addresses are only candidates. */
    public boolean verified;
    /** Other addresses this device was seen at (multi-homed PCs announce from every interface). */
    public final java.util.List<String> otherIps = new java.util.ArrayList<String>();

    public static String normalizeType(String type) {
        if (type == null) return TYPE_DESKTOP;
        String t = Text.lower(type);
        if (TYPE_MOBILE.equals(t) || TYPE_DESKTOP.equals(t) || TYPE_WEB.equals(t) || TYPE_HEADLESS.equals(t) || TYPE_SERVER.equals(t)) {
            return t;
        }
        return TYPE_DESKTOP;
    }

    /** Fills alias/version/model/type/fingerprint/download from a register, info or multicast payload. */
    public static Device fromInfo(JSONObject o, String ip, int defaultPort, boolean defaultHttps) {
        Device d = new Device();
        d.ip = ip;
        d.alias = o.optString("alias", "Unknown");
        d.version = o.optString("version", "1.0");
        d.deviceModel = o.isNull("deviceModel") ? null : o.optString("deviceModel", null);
        d.deviceType = o.isNull("deviceType") ? TYPE_DESKTOP : normalizeType(o.optString("deviceType", null));
        d.fingerprint = o.optString("fingerprint", "");
        d.port = o.has("port") ? o.optInt("port", defaultPort) : defaultPort;
        d.hasEndpoint = o.has("port") && o.has("protocol");
        if (o.has("protocol")) {
            d.https = "https".equals(o.optString("protocol"));
        } else {
            d.https = defaultHttps;
        }
        d.download = o.optBoolean("download", false);
        d.lastSeen = System.currentTimeMillis();
        return d;
    }

    /** The address to try first followed by the alternatives. */
    public java.util.List<String> candidateIps() {
        java.util.List<String> out = new java.util.ArrayList<String>();
        out.add(ip);
        synchronized (otherIps) {
            for (String o : otherIps) if (!out.contains(o)) out.add(o);
        }
        return out;
    }

    public String protocol() {
        return https ? "https" : "http";
    }

    public String baseUrl() {
        return protocol() + "://" + ip + ":" + port;
    }

    /** Peers identify by fingerprint; manual entries without one fall back to the address. */
    public String key() {
        return Text.isEmpty(fingerprint) ? ip + ":" + port : fingerprint;
    }

    public String versionLabel() {
        return version;
    }
}
