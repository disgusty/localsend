package com.disgusty.oldysend.proto;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;
import com.disgusty.oldysend.data.Favorites;
import com.disgusty.oldysend.data.History;
import com.disgusty.oldysend.data.Settings;
import com.disgusty.oldysend.i18n.I18n;
import com.disgusty.oldysend.net.HttpServer;
import com.disgusty.oldysend.net.NetUtil;
import com.disgusty.oldysend.net.Tls;
import com.disgusty.oldysend.util.Codec;
import com.disgusty.oldysend.util.Log;
import com.disgusty.oldysend.util.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;

/**
 * Application-wide protocol state: identity, HTTP server, discovered devices and the transfer controllers.
 * Listeners are always called on the main thread.
 */
public final class Core {
    public static final int CHANGED_DEVICES = 1;
    public static final int CHANGED_SERVER = 2;
    public static final int CHANGED_RECEIVE = 4;
    public static final int CHANGED_SEND = 8;
    public static final int CHANGED_WEB = 16;
    public static final int CHANGED_HISTORY = 32;
    public static final int CHANGED_SCAN = 64;

    public interface Listener {
        void onCoreChanged(int what);
    }

    /** Implemented by the application to bring up screens for events that need the user. */
    public interface Ui {
        void showReceiveRequest();

        void showReceiveProgress();

        void transferActive(boolean active);

        void requestSendPin(SendController.Session session);
    }

    private static Core instance;

    public final Context context;
    public final Handler main = new Handler(Looper.getMainLooper());
    public final Settings settings;
    public final Favorites favorites;
    public final History history;
    public final ReceiveController receive;
    public final SendController send;
    public final WebShare web;
    public final Discovery discovery;
    public Ui ui;

    private final List<Listener> listeners = new ArrayList<Listener>();
    private final Map<String, Device> devices = new LinkedHashMap<String, Device>();
    private int pendingChanges;
    private boolean dispatchScheduled;

    private volatile Tls tls;
    private volatile HttpServer server;
    private volatile boolean serverStarting;
    private volatile String serverError;
    private int runningPort;
    private boolean runningHttps;

    private Core(Context context) {
        this.context = context.getApplicationContext();
        settings = new Settings(this.context);
        favorites = new Favorites(this.context);
        history = new History(this.context);
        receive = new ReceiveController(this);
        send = new SendController(this);
        web = new WebShare(this);
        discovery = new Discovery(this);
    }

    public static synchronized Core init(Context context) {
        if (instance == null) instance = new Core(context);
        return instance;
    }

    public static Core get() {
        return instance;
    }

    // ---- Change notification ----

    public void addListener(Listener l) {
        synchronized (listeners) {
            if (!listeners.contains(l)) listeners.add(l);
        }
    }

    public void removeListener(Listener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    /** Coalesces change events; progress updates can arrive thousands of times per second. */
    public void changed(int what) {
        synchronized (listeners) {
            pendingChanges |= what;
            if (dispatchScheduled) return;
            dispatchScheduled = true;
        }
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                int what;
                List<Listener> copy;
                synchronized (listeners) {
                    what = pendingChanges;
                    pendingChanges = 0;
                    dispatchScheduled = false;
                    copy = new ArrayList<Listener>(listeners);
                }
                for (Listener l : copy) {
                    try {
                        l.onCoreChanged(what);
                    } catch (Throwable t) {
                        Log.e("Listener failed", t);
                    }
                }
            }
        }, 60);
    }

    public void runOnMain(Runnable r) {
        main.post(r);
    }

    // ---- Identity ----

    public String alias() {
        String a = settings.alias();
        if (Text.isBlank(a)) {
            a = randomAlias();
            settings.setAlias(a);
        }
        return a;
    }

    public static String randomAlias() {
        I18n t = I18n.get();
        String[] adjectives = t.array("aliasGenerator.adjectives");
        String[] fruits = t.array("aliasGenerator.fruits");
        if (adjectives.length == 0 || fruits.length == 0) return "Android";
        return t.text("aliasGenerator.combination", "adjective", adjectives[Codec.randomInt(adjectives.length)],
                "fruit", fruits[Codec.randomInt(fruits.length)]);
    }

    public boolean https() {
        return server != null ? runningHttps : settings.encryption();
    }

    public String fingerprint() {
        Tls t = tls;
        if (https() && t != null) return t.fingerprint;
        return settings.httpFingerprint();
    }

    public int port() {
        return server != null ? runningPort : settings.port();
    }

    /** SSL context for outgoing HTTPS requests (client certificate), or null in HTTP mode. */
    public SSLContext clientTls() throws Exception {
        Tls t = tls;
        if (t == null) t = ensureTls();
        return t.context();
    }

    private synchronized Tls ensureTls() throws Exception {
        if (tls == null) tls = Tls.loadOrCreate(new File(context.getFilesDir(), "security"));
        return tls;
    }

    /** Our device info as used in register, prepare-upload "info" and multicast announcements. */
    public JSONObject info(boolean withPortAndProtocol) {
        JSONObject o = new JSONObject();
        try {
            o.put("alias", alias());
            o.put("version", Protocol.VERSION);
            o.put("deviceModel", settings.deviceModel());
            o.put("deviceType", settings.deviceType());
            o.put("fingerprint", fingerprint());
            if (withPortAndProtocol) {
                o.put("port", port());
                o.put("protocol", https() ? "https" : "http");
            }
            o.put("download", web.mode() == WebShare.Mode.DOWNLOAD);
        } catch (JSONException ignored) {
        }
        return o;
    }

    // ---- Server ----

    public boolean serverRunning() {
        return server != null && server.isRunning();
    }

    public boolean serverStarting() {
        return serverStarting;
    }

    public String serverError() {
        return serverError;
    }

    public void startServer() {
        if (serverStarting || serverRunning()) return;
        serverStarting = true;
        changed(CHANGED_SERVER);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean https = settings.encryption();
                    SSLContext ctx = null;
                    if (https) ctx = ensureTls().context();
                    HttpServer s = new HttpServer(new ProtocolServer(Core.this), ctx);
                    int port = settings.port();
                    s.start(port);
                    synchronized (Core.this) {
                        runningPort = port;
                        runningHttps = https;
                        server = s;
                    }
                    serverError = null;
                    discovery.start();
                    discovery.announce();
                } catch (Throwable e) {
                    Log.e("Server start failed", e);
                    serverError = e.toString();
                } finally {
                    serverStarting = false;
                    changed(CHANGED_SERVER);
                }
            }
        }, "server-start").start();
    }

    public void stopServer() {
        HttpServer s;
        synchronized (this) {
            s = server;
            server = null;
        }
        discovery.stop();
        if (s != null) s.stop();
        changed(CHANGED_SERVER);
    }

    public void restartServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                stopServer();
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                }
                startServer();
            }
        }, "server-restart").start();
    }

    /** Creates a new device identity (new fingerprint), like LocalSend's "reset security context". */
    public void resetIdentity() {
        synchronized (this) {
            Tls.deleteIdentity(new File(context.getFilesDir(), "security"));
            tls = null;
        }
        restartServer();
    }

    public List<NetUtil.LocalAddress> localAddresses() {
        return NetUtil.localAddresses(settings.networkWhitelist(), settings.networkBlacklist());
    }

    // ---- Devices ----

    public void registerDevice(Device d) {
        if (d.fingerprint != null && d.fingerprint.equals(fingerprint())) return;
        synchronized (devices) {
            Device old = devices.get(d.key());
            if (old != null) merge(old, d);
            devices.put(d.key(), d);
        }
        if (d.hasEndpoint || d.verified) favorites.updateFromDiscovery(d.fingerprint, d.ip, d.port, d.alias, d.https);
        changed(CHANGED_DEVICES);
    }

    /**
     * Carries knowledge of {@code old} over to its update {@code d}. A PC with several network adapters (VPN, Hyper-V,
     * VirtualBox) announces itself from each of them, and only some of those addresses are reachable from here; a
     * prepare-upload "info" has no port/protocol. Neither must replace a working endpoint.
     */
    private void merge(Device old, Device d) {
        if (!d.hasEndpoint && old.hasEndpoint) {
            d.port = old.port;
            d.https = old.https;
            d.hasEndpoint = true;
        }
        List<String> ips = old.candidateIps();
        if (!d.ip.equals(old.ip) && score(old) > score(d)) {
            // Keep the better address first, remember the new one as an alternative.
            ips.add(d.ip);
            d.ip = old.ip;
            d.verified = old.verified;
        } else if (d.ip.equals(old.ip)) {
            d.verified |= old.verified;
        }
        synchronized (d.otherIps) {
            for (String ip : ips) if (!ip.equals(d.ip) && !d.otherIps.contains(ip)) d.otherIps.add(ip);
        }
    }

    /** Proven by an HTTP exchange beats being in one of our subnets beats a bare multicast source address. */
    private int score(Device d) {
        int s = d.verified ? 2 : 0;
        for (NetUtil.LocalAddress a : localAddresses()) {
            if (d.ip.startsWith(a.subnetPrefix())) {
                s++;
                break;
            }
        }
        return s;
    }

    /** After a successful connection to an alternative address, makes it the device's primary one. */
    public void confirmAddress(Device d, String ip) {
        synchronized (devices) {
            if (!ip.equals(d.ip)) {
                synchronized (d.otherIps) {
                    d.otherIps.remove(ip);
                    d.otherIps.add(0, d.ip);
                }
                d.ip = ip;
            }
            d.verified = true;
        }
        changed(CHANGED_DEVICES);
    }

    public List<Device> devices() {
        synchronized (devices) {
            return new ArrayList<Device>(devices.values());
        }
    }

    public Device deviceByIp(String ip) {
        synchronized (devices) {
            for (Device d : devices.values()) if (d.ip.equals(ip)) return d;
        }
        return null;
    }

    public void clearDevices() {
        synchronized (devices) {
            devices.clear();
        }
        changed(CHANGED_DEVICES);
    }
}
