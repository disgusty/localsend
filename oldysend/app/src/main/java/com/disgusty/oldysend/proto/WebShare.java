package com.disgusty.oldysend.proto;

import org.json.JSONObject;
import com.disgusty.oldysend.files.SendItem;
import com.disgusty.oldysend.i18n.I18n;
import com.disgusty.oldysend.net.HttpRequest;
import com.disgusty.oldysend.net.HttpResponse;
import com.disgusty.oldysend.util.IO;
import com.disgusty.oldysend.util.Log;
import com.disgusty.oldysend.util.Text;

import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Browser-facing pages served at "/": "Share via link" (download page + download API) and
 * "Receive via link" (upload page using the normal v2 upload endpoints). Mirrors server/web.rs.
 */
public final class WebShare {
    public enum Mode {OFF, DOWNLOAD, UPLOAD}

    public enum RequestStatus {PENDING, ACCEPTED, DECLINED}

    public static final class Request {
        public final String ip;
        public final String userAgent;
        public volatile RequestStatus status = RequestStatus.PENDING;
        public final long time = System.currentTimeMillis();

        Request(String ip, String userAgent) {
            this.ip = ip;
            this.userAgent = userAgent;
        }

        /** "Firefox (Windows)" style label like LocalSend's web share page. */
        public String browserLabel() {
            String ua = userAgent == null ? "" : userAgent;
            String browser = ua.contains("Edg/") ? "Edge" : ua.contains("OPR/") ? "Opera" : ua.contains("Firefox/") ? "Firefox"
                    : ua.contains("Chrome/") ? "Chrome" : ua.contains("Safari/") ? "Safari" : "Browser";
            String os = ua.contains("Windows") ? "Windows" : ua.contains("Android") ? "Android" : ua.contains("iPhone") || ua.contains("iPad") ? "iOS"
                    : ua.contains("Mac OS") ? "macOS" : ua.contains("Linux") ? "Linux" : null;
            return os == null ? browser : browser + " (" + os + ")";
        }
    }

    private final Core core;
    private volatile Mode mode = Mode.OFF;
    private volatile String pin;
    private volatile boolean autoAccept;
    private final LinkedHashMap<String, SendItem> files = new LinkedHashMap<String, SendItem>();
    /** Keyed by client IP (= LocalSend's web session id). */
    private final LinkedHashMap<String, Request> requests = new LinkedHashMap<String, Request>();
    private final Map<String, Integer> pinAttempts = new HashMap<String, Integer>();

    WebShare(Core core) {
        this.core = core;
    }

    public Mode mode() {
        return mode;
    }

    public String pin() {
        return pin;
    }

    public boolean autoAccept() {
        return autoAccept;
    }

    public void setPin(String pin) {
        this.pin = Text.isEmpty(pin) ? null : pin;
        synchronized (pinAttempts) {
            pinAttempts.clear();
        }
        core.changed(Core.CHANGED_WEB);
    }

    public void setAutoAccept(boolean v) {
        autoAccept = v;
        core.changed(Core.CHANGED_WEB);
    }

    public void startDownload(List<SendItem> items) {
        synchronized (this) {
            files.clear();
            for (SendItem i : items) files.put(i.id, i);
            requests.clear();
            autoAccept = core.settings.shareViaLinkAutoAccept();
            mode = Mode.DOWNLOAD;
        }
        if (!core.serverRunning()) core.startServer();
        core.discovery.announce();
        core.changed(Core.CHANGED_WEB);
    }

    public void startUpload() {
        synchronized (this) {
            requests.clear();
            files.clear();
            mode = Mode.UPLOAD;
        }
        if (!core.serverRunning()) core.startServer();
        core.changed(Core.CHANGED_WEB);
    }

    public void stop() {
        synchronized (this) {
            mode = Mode.OFF;
            files.clear();
            for (Request r : requests.values()) {
                synchronized (r) {
                    if (r.status == RequestStatus.PENDING) r.status = RequestStatus.DECLINED;
                    r.notifyAll();
                }
            }
            requests.clear();
            pin = null;
        }
        core.changed(Core.CHANGED_WEB);
    }

    public synchronized List<Request> requests() {
        return new ArrayList<Request>(requests.values());
    }

    public synchronized List<SendItem> files() {
        return new ArrayList<SendItem>(files.values());
    }

    public void decide(Request r, boolean accept) {
        synchronized (r) {
            if (r.status != RequestStatus.PENDING) return;
            r.status = accept ? RequestStatus.ACCEPTED : RequestStatus.DECLINED;
            r.notifyAll();
        }
        core.changed(Core.CHANGED_WEB);
    }

    /** URLs to show: one per local address. */
    public List<String> urls() {
        List<String> out = new ArrayList<String>();
        String scheme = core.https() ? "https" : "http";
        for (com.disgusty.oldysend.net.NetUtil.LocalAddress a : core.localAddresses()) {
            String url = scheme + "://" + a.ip + ":" + core.port();
            if (pin != null) {
                try {
                    url += "/?pin=" + URLEncoder.encode(pin, "UTF-8");
                } catch (Exception ignored) {
                }
            }
            out.add(url);
        }
        return out;
    }

    // ---- HTTP ----

    HttpResponse index() {
        switch (mode) {
            case DOWNLOAD:
                return HttpResponse.html(200, asset("web/download.html"));
            case UPLOAD:
                return HttpResponse.html(200, asset("web/upload.html"));
            default:
                return HttpResponse.html(403, asset("web/error-403.html"));
        }
    }

    HttpResponse i18n() throws Exception {
        I18n t = I18n.get();
        JSONObject o = new JSONObject();
        o.put("waiting", t.text("web.waiting"));
        o.put("enterPin", t.text("web.enterPin"));
        o.put("invalidPin", t.text("web.invalidPin"));
        o.put("tooManyAttempts", t.text("web.tooManyAttempts"));
        o.put("rejected", t.text("web.rejected"));
        o.put("uploadRejected", t.text("sendPage.rejected"));
        o.put("busy", t.text("sendPage.busy"));
        o.put("files", t.text("web.files"));
        o.put("fileName", t.text("web.fileName"));
        o.put("size", t.text("web.size"));
        o.put("dropHint", t.text("sendTab.placeItems"));
        return HttpResponse.json(200, o);
    }

    HttpResponse prepareDownload(HttpRequest req) throws Exception {
        if (mode != Mode.DOWNLOAD) return HttpResponse.error(403, "Web download not initialized.");
        String sessionId = req.param("sessionId");
        Request existing;
        synchronized (this) {
            existing = requests.get(req.clientIp);
        }
        if (sessionId != null && existing != null && sessionId.equals(existing.ip) && existing.status == RequestStatus.ACCEPTED) {
            return fileList(req.clientIp);
        }
        String p = pin;
        if (p != null) {
            synchronized (pinAttempts) {
                Integer a = pinAttempts.get(req.clientIp);
                int attempts = a == null ? 0 : a;
                if (attempts >= Protocol.MAX_PIN_ATTEMPTS) return HttpResponse.error(429, "Too many requests");
                String given = req.param("pin");
                if (given == null) return HttpResponse.error(401, "PIN required");
                if (!given.equals(p)) {
                    pinAttempts.put(req.clientIp, attempts + 1);
                    return HttpResponse.error(401, "Invalid PIN");
                }
                pinAttempts.remove(req.clientIp);
            }
        }
        Request r = new Request(req.clientIp, req.header("user-agent"));
        synchronized (this) {
            requests.put(req.clientIp, r);
        }
        if (autoAccept) r.status = RequestStatus.ACCEPTED;
        core.changed(Core.CHANGED_WEB);
        synchronized (r) {
            long deadline = System.currentTimeMillis() + 10 * 60 * 1000;
            while (r.status == RequestStatus.PENDING && mode == Mode.DOWNLOAD && System.currentTimeMillis() < deadline) {
                r.wait(1000);
                if (r.status == RequestStatus.PENDING && req.peerClosed()) break;
            }
        }
        if (r.status != RequestStatus.ACCEPTED) {
            synchronized (this) {
                if (requests.get(req.clientIp) == r && r.status == RequestStatus.PENDING) requests.remove(req.clientIp);
            }
            return HttpResponse.error(403, "File transfer rejected.");
        }
        return fileList(req.clientIp);
    }

    private HttpResponse fileList(String sessionId) throws Exception {
        JSONObject info = core.info(false);
        info.put("download", true);
        JSONObject fs = new JSONObject();
        for (SendItem i : files()) {
            FileDto dto = new FileDto();
            dto.id = i.id;
            dto.fileName = i.name;
            dto.size = i.size;
            dto.fileType = i.mime == null ? "application/octet-stream" : i.mime;
            if (i.isText()) dto.preview = i.text;
            fs.put(dto.id, dto.toJson());
        }
        JSONObject o = new JSONObject();
        o.put("info", info);
        o.put("sessionId", sessionId);
        o.put("files", fs);
        return HttpResponse.json(200, o);
    }

    HttpResponse download(HttpRequest req) {
        if (mode != Mode.DOWNLOAD) return HttpResponse.error(403, "Web download not initialized.");
        String sessionId = req.param("sessionId");
        if (sessionId == null) return HttpResponse.error(400, "Missing sessionId.");
        Request r;
        SendItem item;
        synchronized (this) {
            r = requests.get(req.clientIp);
            String fileId = req.param("fileId");
            if (fileId == null) return HttpResponse.error(400, "Missing fileId.");
            item = files.get(fileId);
        }
        if (r == null || !sessionId.equals(r.ip) || r.status != RequestStatus.ACCEPTED) return HttpResponse.error(403, "Invalid sessionId.");
        if (item == null) return HttpResponse.error(403, "Invalid fileId.");
        try {
            InputStream in = item.open(core.context);
            HttpResponse res = new HttpResponse();
            res.headers.put("Content-Type", "application/octet-stream");
            String name = item.name.replace('/', '-');
            String encoded;
            try {
                encoded = URLEncoder.encode(name, "UTF-8").replace("+", "%20");
            } catch (Exception e) {
                encoded = "file";
            }
            res.headers.put("Content-Disposition", "attachment; filename=\"" + encoded + "\"");
            res.stream = in;
            res.streamLength = item.size;
            return res;
        } catch (Exception e) {
            Log.w("Web download failed", e);
            return HttpResponse.error(500, "Could not read file");
        }
    }

    private final Map<String, String> assetCache = new HashMap<String, String>();

    private String asset(String path) {
        synchronized (assetCache) {
            String c = assetCache.get(path);
            if (c != null) return c;
            InputStream in = null;
            try {
                in = core.context.getAssets().open(path);
                c = Text.utf8(IO.readAll(in));
            } catch (Exception e) {
                c = "<html><body>OldySend</body></html>";
            } finally {
                IO.close(in);
            }
            assetCache.put(path, c);
            return c;
        }
    }
}
