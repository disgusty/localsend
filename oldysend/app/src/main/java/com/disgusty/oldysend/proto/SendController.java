package com.disgusty.oldysend.proto;

import org.json.JSONObject;
import com.disgusty.oldysend.files.SendItem;
import com.disgusty.oldysend.net.HttpClient;
import com.disgusty.oldysend.util.Codec;
import com.disgusty.oldysend.util.IO;
import com.disgusty.oldysend.util.Log;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import javax.net.ssl.SSLContext;

/** Client side of transfers. Several sessions may run at once ("multiple recipients" mode). */
public final class SendController {
    public enum Status {
        PREPARING, WAITING, SENDING, FINISHED, FINISHED_WITH_ERRORS, DECLINED, RECIPIENT_BUSY,
        TOO_MANY_ATTEMPTS, CANCELED_BY_SENDER, CANCELED_BY_RECEIVER, ERROR
    }

    public enum FileStatus {QUEUE, SKIPPED, SENDING, FAILED, FINISHED}

    public static final class SendingFile {
        public final SendItem item;
        public volatile FileStatus status = FileStatus.QUEUE;
        public volatile long sent;
        public String token;
        public String error;

        SendingFile(SendItem item) {
            this.item = item;
        }
    }

    public static final class Session {
        public final String id = Codec.randomId();
        public final Device target;
        public final LinkedHashMap<String, SendingFile> files = new LinkedHashMap<String, SendingFile>();
        public volatile Status status = Status.PREPARING;
        public volatile String errorMessage;
        public volatile String remoteSessionId;
        public volatile int checksumProgress;
        public long startTime;
        public long endTime;
        /** True while the receiver asks for a PIN; the UI answers with {@link SendController#submitPin}. */
        public volatile boolean pinRequested;
        public volatile boolean pinWasWrong;
        private String pin;
        private final Object pinLock = new Object();
        private volatile HttpClient client;
        private volatile boolean cancelled;

        Session(Device target) {
            this.target = target;
        }

        public boolean isActive() {
            return status == Status.PREPARING || status == Status.WAITING || status == Status.SENDING;
        }

        public boolean isMessage() {
            return files.size() == 1 && files.values().iterator().next().item.isText();
        }

        public long totalBytes() {
            long t = 0;
            for (SendingFile f : files.values()) if (f.status != FileStatus.SKIPPED) t += f.item.size;
            return t;
        }

        public long sentBytes() {
            long t = 0;
            for (SendingFile f : files.values()) {
                if (f.status == FileStatus.FINISHED) t += f.item.size;
                else if (f.status == FileStatus.SENDING) t += f.sent;
            }
            return t;
        }

        public int count(FileStatus s) {
            int n = 0;
            for (SendingFile f : files.values()) if (f.status == s) n++;
            return n;
        }

        public int acceptedCount() {
            return files.size() - count(FileStatus.SKIPPED);
        }
    }

    private final Core core;
    private final List<Session> sessions = new ArrayList<Session>();

    SendController(Core core) {
        this.core = core;
    }

    public List<Session> sessions() {
        synchronized (sessions) {
            return new ArrayList<Session>(sessions);
        }
    }

    public Session byId(String id) {
        synchronized (sessions) {
            for (Session s : sessions) if (s.id.equals(id)) return s;
        }
        return null;
    }

    public void remove(Session s) {
        synchronized (sessions) {
            sessions.remove(s);
        }
        core.changed(Core.CHANGED_SEND);
    }

    public Session start(Device target, List<SendItem> items) {
        final Session s = new Session(target);
        for (SendItem item : items) s.files.put(item.id, new SendingFile(item));
        synchronized (sessions) {
            sessions.add(s);
        }
        core.changed(Core.CHANGED_SEND);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    run0(s);
                } catch (Throwable e) {
                    Log.e("Send session failed", e);
                    if (s.isActive()) {
                        s.status = s.cancelled ? Status.CANCELED_BY_SENDER : Status.ERROR;
                        s.errorMessage = String.valueOf(e.getMessage());
                        s.endTime = System.currentTimeMillis();
                    }
                } finally {
                    if (core.ui != null) core.ui.transferActive(anyActive());
                    core.changed(Core.CHANGED_SEND);
                }
            }
        }, "send-" + target.ip).start();
        return s;
    }

    private boolean anyActive() {
        synchronized (sessions) {
            for (Session s : sessions) if (s.isActive()) return true;
        }
        return false;
    }

    private SSLContext tlsFor(Device d) throws Exception {
        return d.https ? core.clientTls() : null;
    }

    private void run0(Session s) throws Exception {
        Device t = s.target;
        if (core.settings.createChecksums()) {
            int i = 0;
            for (SendingFile f : s.files.values()) {
                if (s.cancelled) return;
                s.checksumProgress = ++i;
                core.changed(Core.CHANGED_SEND);
                if (!f.item.isText()) f.item.computeSha256(core.context);
            }
        }
        s.status = Status.WAITING;
        core.changed(Core.CHANGED_SEND);

        JSONObject files = new JSONObject();
        boolean message = s.isMessage();
        for (SendingFile f : s.files.values()) {
            FileDto dto = new FileDto();
            dto.id = f.item.id;
            dto.fileName = f.item.name;
            dto.size = f.item.size;
            dto.fileType = f.item.mime == null ? "application/octet-stream" : f.item.mime;
            dto.sha256 = f.item.sha256;
            if (message) dto.preview = f.item.text;
            if (f.item.modified > 0) dto.modified = SendItem.iso(f.item.modified);
            files.put(dto.id, dto.toJson());
        }
        JSONObject body = new JSONObject();
        body.put("info", core.info(true));
        body.put("files", files);

        HttpClient.Response res;
        while (true) {
            res = prepareUpload(s, com.disgusty.oldysend.util.Text.utf8(body.toString()));
            if (res == null) return;
            if (res.status == 401) {
                // PIN required or wrong: ask the user and retry.
                s.pinWasWrong = s.pin != null;
                String pin = askPin(s);
                if (pin == null) {
                    finish(s, Status.CANCELED_BY_SENDER);
                    return;
                }
                s.pin = pin;
                continue;
            }
            break;
        }
        switch (res.status) {
            case 200:
                break;
            case 204:
                finish(s, Status.FINISHED);
                for (SendingFile f : s.files.values()) f.status = FileStatus.FINISHED;
                return;
            case 403:
                finish(s, s.cancelled ? Status.CANCELED_BY_SENDER : Status.DECLINED);
                return;
            case 409:
                finish(s, Status.RECIPIENT_BUSY);
                return;
            case 429:
                finish(s, Status.TOO_MANY_ATTEMPTS);
                return;
            default:
                s.errorMessage = res.message();
                finish(s, Status.ERROR);
                return;
        }

        JSONObject json = res.json();
        s.remoteSessionId = json.optString("sessionId");
        JSONObject tokens = json.optJSONObject("files");
        for (SendingFile f : s.files.values()) {
            String token = tokens == null ? null : tokens.optString(f.item.id, null);
            if (token == null || token.length() == 0) f.status = FileStatus.SKIPPED;
            else f.token = token;
        }
        s.status = Status.SENDING;
        s.startTime = System.currentTimeMillis();
        if (core.ui != null) core.ui.transferActive(true);
        core.changed(Core.CHANGED_SEND);

        for (final SendingFile f : s.files.values()) {
            if (f.status != FileStatus.QUEUE) continue;
            if (s.cancelled || s.status != Status.SENDING) break;
            int attempt = 0;
            while (true) {
                attempt++;
                f.status = FileStatus.SENDING;
                f.sent = 0;
                core.changed(Core.CHANGED_SEND);
                InputStream in = null;
                try {
                    HttpClient c = new HttpClient(tlsFor(t), t.https ? t.fingerprint : null);
                    c.readTimeoutMs = 120000;
                    s.client = c;
                    in = f.item.open(core.context);
                    HttpClient.Response r = c.upload(t.ip, t.port, HttpClient.query(Protocol.UPLOAD, "sessionId", s.remoteSessionId,
                            "fileId", f.item.id, "token", f.token), in, f.item.size, new IO.Progress() {
                        @Override
                        public boolean onProgress(long transferred) {
                            f.sent = transferred;
                            core.changed(Core.CHANGED_SEND);
                            return true;
                        }
                    });
                    if (r.status == 200) {
                        f.status = FileStatus.FINISHED;
                    } else if (r.status == 422 && attempt < Protocol.MAX_UPLOAD_ATTEMPTS) {
                        continue;
                    } else {
                        f.status = FileStatus.FAILED;
                        f.error = r.message();
                    }
                } catch (Exception e) {
                    f.status = FileStatus.FAILED;
                    f.error = e.getMessage();
                    Log.w("Upload of " + f.item.name + " failed", e);
                } finally {
                    IO.close(in);
                }
                break;
            }
            core.changed(Core.CHANGED_SEND);
        }
        if (s.status == Status.SENDING) {
            finish(s, s.count(FileStatus.FAILED) > 0 ? Status.FINISHED_WITH_ERRORS : Status.FINISHED);
        }
    }

    /**
     * POST /prepare-upload, trying every address the device was seen at and each one twice: a phone in Wi-Fi power
     * saving often drops the first SYN, and a PC announces from adapters we cannot reach. Null when cancelled.
     */
    private HttpClient.Response prepareUpload(Session s, byte[] body) throws Exception {
        Device t = s.target;
        HttpClient.ConnectException first = null;
        List<String> ips = t.candidateIps();
        for (int round = 0; round < 2; round++) {
            for (String ip : ips) {
                if (s.cancelled) return null;
                HttpClient c = new HttpClient(tlsFor(t), t.https ? t.fingerprint : null);
                c.connectTimeoutMs = round == 0 ? 4000 : 8000;
                c.readTimeoutMs = 0; // the receiver may take long to decide
                s.client = c;
                try {
                    HttpClient.Response r = c.request("POST", ip, t.port, HttpClient.query(Protocol.PREPARE_UPLOAD, "pin", s.pin),
                            "application/json", body);
                    if (!ip.equals(t.ip)) Log.i("prepare-upload: " + t.ip + " unreachable, switched to " + ip);
                    if (!ip.equals(t.ip) || !t.verified) core.confirmAddress(t, ip);
                    return r;
                } catch (HttpClient.ConnectException e) {
                    Log.w("prepare-upload: " + e.getMessage());
                    if (first == null) first = e;
                }
            }
        }
        if (s.cancelled) return null;
        String text = com.disgusty.oldysend.i18n.I18n.get() == null ? first.getMessage()
                : com.disgusty.oldysend.i18n.I18n.t("oldy.connectFailed", "address", t.ip + ":" + t.port);
        throw new java.io.IOException(text);
    }

    private void finish(Session s, Status status) {
        if (!s.isActive()) return;
        s.status = status;
        s.endTime = System.currentTimeMillis();
        core.changed(Core.CHANGED_SEND);
    }

    private String askPin(Session s) throws InterruptedException {
        synchronized (s.pinLock) {
            s.pin = null;
            s.pinRequested = true;
            core.changed(Core.CHANGED_SEND);
            if (core.ui != null) core.ui.requestSendPin(s);
            while (s.pinRequested && !s.cancelled) s.pinLock.wait(500);
            s.pinRequested = false;
            return s.cancelled ? null : s.pin;
        }
    }

    /** Answer to a PIN request; null cancels the session. */
    public void submitPin(Session s, String pin) {
        synchronized (s.pinLock) {
            if (pin == null) s.cancelled = true;
            s.pin = pin;
            s.pinRequested = false;
            s.pinLock.notifyAll();
        }
    }

    /** The sending user cancels: abort the connection and tell the receiver. */
    public void cancel(final Session s) {
        if (!s.isActive()) return;
        s.cancelled = true;
        final boolean notify = s.status == Status.SENDING || s.status == Status.WAITING;
        s.status = Status.CANCELED_BY_SENDER;
        s.endTime = System.currentTimeMillis();
        synchronized (s.pinLock) {
            s.pinLock.notifyAll();
        }
        final HttpClient c = s.client;
        if (c != null) c.cancel();
        core.changed(Core.CHANGED_SEND);
        if (!notify) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpClient cc = new HttpClient(tlsFor(s.target), s.target.https ? s.target.fingerprint : null);
                    cc.request("POST", s.target.ip, s.target.port, HttpClient.query(Protocol.CANCEL, "sessionId", s.remoteSessionId), null, new byte[0]);
                } catch (Exception e) {
                    Log.d("Cancel request failed: " + e);
                }
            }
        }, "send-cancel").start();
    }

    /** POST /cancel received from the device we are sending to. */
    void cancelFromReceiver(String ip, String remoteSessionId) {
        for (Session s : sessions()) {
            if (s.isActive() && s.target.ip.equals(ip) && remoteSessionId.equals(s.remoteSessionId)) {
                s.cancelled = true;
                s.status = Status.CANCELED_BY_RECEIVER;
                s.endTime = System.currentTimeMillis();
                HttpClient c = s.client;
                if (c != null) c.cancel();
                core.changed(Core.CHANGED_SEND);
            }
        }
    }
}
