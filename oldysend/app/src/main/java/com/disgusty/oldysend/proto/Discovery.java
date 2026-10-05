package com.disgusty.oldysend.proto;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.wifi.WifiManager;

import org.json.JSONObject;
import com.disgusty.oldysend.data.Favorites;
import com.disgusty.oldysend.net.HttpClient;
import com.disgusty.oldysend.net.NetUtil;
import com.disgusty.oldysend.util.IO;
import com.disgusty.oldysend.util.Log;
import com.disgusty.oldysend.util.Sdk;
import com.disgusty.oldysend.util.Text;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UDP multicast announcements (protocol v2.2, answered over HTTP register) plus the HTTP subnet scan
 * used when multicast is blocked — which is common on Android 1.x, where MulticastLock does not exist.
 */
public final class Discovery {
    private static final int[] ANNOUNCE_DELAYS = {100, 500, 2000};

    private final Core core;
    private volatile MulticastSocket socket;
    private Thread receiver;
    private Object multicastLock;
    private final ExecutorService responder = new ThreadPoolExecutor(0, 8, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    private final AtomicInteger scansRunning = new AtomicInteger();

    Discovery(Core core) {
        this.core = core;
    }

    public synchronized void start() {
        if (socket != null) return;
        acquireLock();
        try {
            MulticastSocket s = new MulticastSocket(core.settings.port());
            s.setTimeToLive(8);
            s.setLoopbackMode(false);
            s.joinGroup(InetAddress.getByName(core.settings.multicastGroup()));
            socket = s;
        } catch (Exception e) {
            Log.w("Multicast unavailable", e);
            releaseLock();
            return;
        }
        final MulticastSocket s = socket;
        receiver = new Thread(new Runnable() {
            @Override
            public void run() {
                receiveLoop(s);
            }
        }, "multicast-rx");
        receiver.start();
    }

    public synchronized void stop() {
        MulticastSocket s = socket;
        socket = null;
        if (s != null) {
            try {
                s.leaveGroup(InetAddress.getByName(core.settings.multicastGroup()));
            } catch (Exception ignored) {
            }
            IO.close(s);
        }
        releaseLock();
    }

    public boolean scanning() {
        return scansRunning.get() > 0;
    }

    private void receiveLoop(MulticastSocket s) {
        byte[] buf = new byte[65536];
        int errors = 0;
        while (socket == s) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                s.receive(p);
                errors = 0;
                handle(p.getAddress().getHostAddress(), new String(p.getData(), 0, p.getLength(), "UTF-8"));
            } catch (Exception e) {
                if (socket != s) return;
                if (++errors > 10) {
                    Log.w("Multicast receive keeps failing", e);
                    return;
                }
            }
        }
    }

    private void handle(final String ip, String payload) {
        final JSONObject msg;
        try {
            msg = new JSONObject(payload);
        } catch (Exception e) {
            return;
        }
        String fp = msg.optString("fingerprint", "");
        if (fp.equals(core.fingerprint())) return;
        final Device d = Device.fromInfo(msg, ip, core.settings.port(), false);
        d.discoveredVia = "multicast";
        core.registerDevice(d);
        // v2.2 messages have no "announce" flag (always announcements); v2.0 answers carry announce=false.
        if (!msg.optBoolean("announce", true)) return;
        responder.execute(new Runnable() {
            @Override
            public void run() {
                if (!answer(d)) sendUdp(false);
            }
        });
    }

    /** Answers an announcement with an HTTP register request to the announcer. */
    private boolean answer(Device d) {
        try {
            HttpClient c = new HttpClient(d.https ? core.clientTls() : null, null);
            c.connectTimeoutMs = 3000;
            c.readTimeoutMs = 5000;
            HttpClient.Response r = c.postJson(d.ip, d.port, Protocol.REGISTER, core.info(true));
            if (r.status != 200) return false;
            JSONObject body = r.json();
            Device updated = Device.fromInfo(body, d.ip, d.port, d.https);
            updated.port = d.port;
            updated.https = d.https;
            if (r.peerFingerprint != null) updated.fingerprint = r.peerFingerprint;
            updated.discoveredVia = "multicast";
            updated.hasEndpoint = true;
            updated.verified = true;
            core.registerDevice(updated);
            return true;
        } catch (Exception e) {
            Log.d("Register answer to " + d.ip + " failed: " + e);
            return false;
        }
    }

    /** Sends the announcement burst (100 ms, 500 ms, 2 s). */
    public void announce() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int delay : ANNOUNCE_DELAYS) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        return;
                    }
                    sendUdp(true);
                }
            }
        }, "multicast-tx").start();
    }

    private void sendUdp(boolean announce) {
        MulticastSocket s = socket;
        if (s == null) return;
        try {
            JSONObject msg = core.info(true);
            msg.put("announce", announce);
            byte[] data = Text.utf8(msg.toString());
            s.send(new DatagramPacket(data, data.length, InetAddress.getByName(core.settings.multicastGroup()), core.settings.port()));
        } catch (Exception e) {
            Log.d("Multicast send failed: " + e);
        }
    }

    /** Manual refresh: announce, contact favorites, then scan every local /24 subnet over HTTP. */
    public void scan() {
        if (socket == null && core.serverRunning()) start();
        announce();
        scansRunning.incrementAndGet();
        core.changed(Core.CHANGED_SCAN);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    scan0();
                } finally {
                    scansRunning.decrementAndGet();
                    core.changed(Core.CHANGED_SCAN);
                }
            }
        }, "http-scan").start();
    }

    private void scan0() {
        final boolean https = core.https();
        // Old phones cannot afford 254 threads; 24 parallel probes keep a /24 scan under ~10 s.
        ThreadPoolExecutor pool = new ThreadPoolExecutor(24, 24, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        for (final Favorites.Entry f : core.favorites.all()) {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    probe(f.ip, f.port, f.https, 3000);
                }
            });
        }
        List<NetUtil.LocalAddress> addrs = core.localAddresses();
        final int port = core.settings.port();
        final int timeout = Math.max(200, core.settings.discoveryTimeout());
        for (NetUtil.LocalAddress a : addrs) {
            String prefix = a.subnetPrefix();
            for (int i = 1; i < 255; i++) {
                final String ip = prefix + i;
                if (ip.equals(a.ip)) continue;
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        probe(ip, port, https, timeout);
                    }
                });
            }
        }
        pool.shutdown();
        try {
            pool.awaitTermination(120, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
    }

    /** POST /register to one address; registers the device when it answers. */
    public Device probe(String ip, int port, boolean https, int connectTimeout) {
        try {
            HttpClient c = new HttpClient(https ? core.clientTls() : null, null);
            c.connectTimeoutMs = connectTimeout;
            c.readTimeoutMs = 4000;
            HttpClient.Response r = c.postJson(ip, port, Protocol.REGISTER, core.info(true));
            if (r.status != 200) return null;
            Device d = Device.fromInfo(r.json(), ip, port, https);
            d.port = port;
            d.https = https;
            if (r.peerFingerprint != null) d.fingerprint = r.peerFingerprint;
            d.discoveredVia = "http";
            d.hasEndpoint = true;
            d.verified = true;
            core.registerDevice(d);
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- WifiManager.MulticastLock (API 4) ----

    private void acquireLock() {
        if (!Sdk.atLeast(4) || multicastLock != null) return;
        multicastLock = Api4.acquire(core.context);
    }

    private void releaseLock() {
        if (multicastLock != null && Sdk.atLeast(4)) Api4.release(multicastLock);
        multicastLock = null;
    }

    @TargetApi(4)
    private static final class Api4 {
        static Object acquire(Context context) {
            try {
                WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiManager.MulticastLock lock = wm.createMulticastLock("oldysend");
                lock.setReferenceCounted(false);
                lock.acquire();
                return lock;
            } catch (Exception e) {
                Log.w("No multicast lock", e);
                return null;
            }
        }

        static void release(Object lock) {
            try {
                ((WifiManager.MulticastLock) lock).release();
            } catch (Exception ignored) {
            }
        }
    }
}
