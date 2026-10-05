package com.disgusty.oldysend.proto;

import org.json.JSONObject;
import com.disgusty.oldysend.data.History;
import com.disgusty.oldysend.data.Settings;
import com.disgusty.oldysend.files.SendItem;
import com.disgusty.oldysend.files.Storage;
import com.disgusty.oldysend.net.HttpClient;
import com.disgusty.oldysend.net.HttpRequest;
import com.disgusty.oldysend.net.HttpResponse;
import com.disgusty.oldysend.util.Codec;
import com.disgusty.oldysend.util.IO;
import com.disgusty.oldysend.util.Log;
import com.disgusty.oldysend.util.Text;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server side of a transfer: one session at a time, like LocalSend. */
public final class ReceiveController {
    public enum Status {WAITING, SENDING, FINISHED, FINISHED_WITH_ERRORS, CANCELED_BY_SENDER, CANCELED_BY_RECEIVER, DECLINED}

    public enum FileStatus {QUEUE, SKIPPED, SENDING, FAILED, FINISHED}

    public static final class ReceivingFile {
        public final FileDto dto;
        public String token;
        public volatile FileStatus status = FileStatus.QUEUE;
        /** Name chosen in the options page (may differ from dto.fileName). */
        public String desiredName;
        public String location;
        public boolean savedToGallery;
        public volatile long received;
        public String error;
        int attempts;

        ReceivingFile(FileDto dto) {
            this.dto = dto;
            this.desiredName = dto.fileName;
        }
    }

    public static final class Session {
        public final String id = Codec.randomId();
        public final Device sender;
        public final String senderIp;
        public final boolean fromBrowser;
        public final LinkedHashMap<String, ReceivingFile> files = new LinkedHashMap<String, ReceivingFile>();
        public volatile Status status = Status.WAITING;
        public final String message;
        public long startTime;
        public long endTime;
        public String destination;
        public boolean saveToGallery;
        public boolean autoAccepted;
        // decision
        private final Object lock = new Object();
        private boolean decided;
        private boolean accepted;

        Session(Device sender, String ip, boolean fromBrowser, String message) {
            this.sender = sender;
            this.senderIp = ip;
            this.fromBrowser = fromBrowser;
            this.message = message;
        }

        public boolean isActive() {
            return status == Status.WAITING || status == Status.SENDING;
        }

        public long totalBytes() {
            long t = 0;
            for (ReceivingFile f : files.values()) if (f.status != FileStatus.SKIPPED) t += f.dto.size;
            return t;
        }

        public long receivedBytes() {
            long t = 0;
            for (ReceivingFile f : files.values()) {
                if (f.status == FileStatus.FINISHED) t += f.dto.size;
                else if (f.status == FileStatus.SENDING) t += f.received;
            }
            return t;
        }

        public int count(FileStatus s) {
            int n = 0;
            for (ReceivingFile f : files.values()) if (f.status == s) n++;
            return n;
        }

        public int acceptedCount() {
            return files.size() - count(FileStatus.SKIPPED);
        }

        public boolean hasFolders() {
            for (ReceivingFile f : files.values()) if (f.dto.fileName.indexOf('/') >= 0) return true;
            return false;
        }
    }

    private final Core core;
    private volatile Session session;
    private final Map<String, Integer> pinAttempts = new HashMap<String, Integer>();

    ReceiveController(Core core) {
        this.core = core;
    }

    public Session session() {
        return session;
    }

    /** Dismisses a finished session (after the user closed the progress page). */
    public void clearFinished() {
        Session s = session;
        if (s != null && !s.isActive()) {
            session = null;
            core.changed(Core.CHANGED_RECEIVE);
        }
    }

    // ---- HTTP endpoints ----

    HttpResponse checkPin(HttpRequest req, String pin) {
        if (pin == null) return null;
        synchronized (pinAttempts) {
            Integer a = pinAttempts.get(req.clientIp);
            int attempts = a == null ? 0 : a;
            if (attempts >= Protocol.MAX_PIN_ATTEMPTS) return HttpResponse.error(429, "Too many requests");
            String given = req.param("pin");
            if (given == null) return HttpResponse.error(401, "PIN required");
            if (!given.equals(pin)) {
                pinAttempts.put(req.clientIp, attempts + 1);
                return HttpResponse.error(401, "Invalid PIN");
            }
            pinAttempts.remove(req.clientIp);
            return null;
        }
    }

    HttpResponse prepareUpload(HttpRequest req) throws Exception {
        boolean webReceive = core.web.mode() == WebShare.Mode.UPLOAD;
        String pin = core.settings.receivePin();
        if (webReceive && core.web.pin() != null) pin = core.web.pin();
        HttpResponse pinError = checkPin(req, pin);
        if (pinError != null) return pinError;

        JSONObject body;
        try {
            body = new JSONObject(Text.utf8(req.readBody(Protocol.MAX_JSON)));
        } catch (Exception e) {
            return HttpResponse.error(400, "Invalid body");
        }
        JSONObject info = body.optJSONObject("info");
        JSONObject files = body.optJSONObject("files");
        if (info == null || files == null || files.length() == 0) return HttpResponse.error(400, "No files provided");

        Device sender = Device.fromInfo(info, req.clientIp, 53317, false);
        if (req.certFingerprint != null) sender.fingerprint = req.certFingerprint;
        sender.verified = true;
        boolean fromBrowser = Device.TYPE_WEB.equals(sender.deviceType) || req.certFingerprint == null && core.https();

        List<FileDto> dtos = new ArrayList<FileDto>();
        Iterator<?> keys = files.keys();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            JSONObject f = files.optJSONObject(key);
            if (f == null) continue;
            FileDto dto = FileDto.fromJson(f);
            if (dto.id == null) dto.id = key;
            dtos.add(dto);
        }
        // org.json before Android 4.x keeps keys in a HashMap: show files in a stable, readable order.
        java.util.Collections.sort(dtos, new java.util.Comparator<FileDto>() {
            @Override
            public int compare(FileDto a, FileDto b) {
                return a.fileName.compareToIgnoreCase(b.fileName);
            }
        });
        String message = dtos.size() == 1 && dtos.get(0).isMessage() ? dtos.get(0).preview : null;

        Session s;
        synchronized (this) {
            Session cur = session;
            if (cur != null && cur.isActive()) return HttpResponse.error(409, "Blocked by another session");
            s = new Session(sender, req.clientIp, fromBrowser, message);
            for (FileDto dto : dtos) s.files.put(dto.id, new ReceivingFile(dto));
            s.destination = core.settings.destination();
            s.saveToGallery = core.settings.saveToGallery() && !s.hasFolders();
            session = s;
        }
        if (!fromBrowser) core.registerDevice(sender);

        boolean auto = false;
        if (message == null) {
            Settings st = core.settings;
            if (st.quickSaveOn()) auto = true;
            else if (st.quickSaveFromFavorites() && core.favorites.isFavorite(sender.fingerprint)) auto = true;
            else if (webReceive && core.web.autoAccept()) auto = true;
        }

        if (auto) {
            s.autoAccepted = true;
            decide(s, true, null);
        } else {
            core.changed(Core.CHANGED_RECEIVE);
            if (core.ui != null) core.ui.showReceiveRequest();
            synchronized (s.lock) {
                long deadline = System.currentTimeMillis() + 15 * 60 * 1000;
                while (!s.decided && s.status == Status.WAITING) {
                    long left = deadline - System.currentTimeMillis();
                    if (left <= 0) break;
                    s.lock.wait(Math.min(left, 1000));
                    // A sender that gave up closes the connection: free the slot like LocalSend's drop guard.
                    if (!s.decided && s.status == Status.WAITING && req.peerClosed()) {
                        s.status = Status.CANCELED_BY_SENDER;
                        s.endTime = System.currentTimeMillis();
                    }
                }
            }
        }

        if (s.status == Status.CANCELED_BY_SENDER) return HttpResponse.error(403, "Cancelled by sender");
        if (!s.decided) {
            s.status = Status.CANCELED_BY_RECEIVER;
            core.changed(Core.CHANGED_RECEIVE);
            return HttpResponse.error(403, "Rejected");
        }
        if (!s.accepted) return HttpResponse.error(403, "Rejected");

        if (message != null) {
            // Messages are shown, not stored: accept nothing (204), add to history.
            s.status = Status.FINISHED;
            s.endTime = System.currentTimeMillis();
            if (core.settings.saveToHistory()) {
                History.Entry e = new History.Entry();
                e.fileName = message;
                e.fileType = "text/plain";
                e.isMessage = true;
                e.fileSize = Text.utf8(message).length;
                e.senderAlias = sender.alias;
                core.history.add(e);
                core.changed(Core.CHANGED_HISTORY);
            }
            core.changed(Core.CHANGED_RECEIVE);
            return HttpResponse.empty(204);
        }

        JSONObject tokens = new JSONObject();
        for (ReceivingFile f : s.files.values()) {
            if (f.status == FileStatus.SKIPPED) continue;
            f.token = Codec.randomId();
            tokens.put(f.dto.id, f.token);
        }
        if (tokens.length() == 0) {
            s.status = Status.FINISHED;
            s.endTime = System.currentTimeMillis();
            core.changed(Core.CHANGED_RECEIVE);
            return HttpResponse.empty(204);
        }
        s.status = Status.SENDING;
        s.startTime = System.currentTimeMillis();
        core.changed(Core.CHANGED_RECEIVE);
        if (core.ui != null) {
            core.ui.transferActive(true);
            core.ui.showReceiveProgress();
        }
        JSONObject res = new JSONObject();
        res.put("sessionId", s.id);
        res.put("files", tokens);
        return HttpResponse.json(200, res);
    }

    /**
     * The user's decision from the receive page.
     *
     * @param names fileId → desired name for accepted files; null accepts all with original names
     */
    public void decide(Session s, boolean accept, Map<String, String> names) {
        synchronized (s.lock) {
            if (s.decided || s.status != Status.WAITING) return;
            if (accept && names != null) {
                for (ReceivingFile f : s.files.values()) {
                    String n = names.get(f.dto.id);
                    if (n == null) f.status = FileStatus.SKIPPED;
                    else f.desiredName = n;
                }
            }
            s.accepted = accept;
            s.decided = true;
            if (!accept) {
                s.status = Status.DECLINED;
                s.endTime = System.currentTimeMillis();
            }
            s.lock.notifyAll();
        }
        core.changed(Core.CHANGED_RECEIVE);
    }

    HttpResponse upload(HttpRequest req) {
        String sessionId = req.param("sessionId");
        String fileId = req.param("fileId");
        String token = req.param("token");
        if (sessionId == null || fileId == null || token == null) return HttpResponse.error(400, "Missing parameters");
        final Session s = session;
        final ReceivingFile f;
        synchronized (this) {
            if (s == null || s.status != Status.SENDING || !s.id.equals(sessionId) || !s.senderIp.equals(req.clientIp)) {
                return HttpResponse.error(403, "Invalid token or IP address");
            }
            f = s.files.get(fileId);
            if (f == null || !token.equals(f.token) || f.status != FileStatus.QUEUE) {
                return HttpResponse.error(403, "Invalid token or IP address");
            }
            f.status = FileStatus.SENDING;
            f.received = 0;
            f.attempts++;
        }
        core.changed(Core.CHANGED_RECEIVE);

        boolean verify = core.settings.verifyChecksums() && f.dto.sha256 != null;
        MessageDigest md = verify ? Codec.sha256Digest() : null;
        Storage.Target target = null;
        int result = 500;
        try {
            target = Storage.open(core.context, s.destination, f.desiredName, f.dto.fileType,
                    s.saveToGallery && f.dto.fileName.indexOf('/') < 0);
            InputStream in = req.body;
            byte[] buf = new byte[IO.BUFFER];
            long total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                if (s.status != Status.SENDING) throw new IOException("Session cancelled");
                target.out.write(buf, 0, n);
                if (md != null) md.update(buf, 0, n);
                total += n;
                f.received = total;
                core.changed(Core.CHANGED_RECEIVE);
            }
            target.out.flush();
            if (md != null && !Codec.hexLower(md.digest()).equalsIgnoreCase(f.dto.sha256)) {
                Storage.abort(core.context, target);
                target = null;
                result = 422;
                throw new IOException("Checksum mismatch");
            }
            Storage.finish(core.context, target, SendItem.parseIso(f.dto.modified));
            f.location = target.location;
            f.savedToGallery = target.savedToGallery();
            f.status = FileStatus.FINISHED;
            result = 200;
            if (core.settings.saveToHistory()) {
                History.Entry e = new History.Entry();
                e.fileName = target.displayName != null ? target.displayName : f.desiredName;
                e.fileType = f.dto.fileType;
                e.location = target.location;
                e.savedToGallery = f.savedToGallery;
                e.fileSize = total;
                e.senderAlias = s.sender.alias;
                core.history.add(e);
                core.changed(Core.CHANGED_HISTORY);
            }
        } catch (Exception e) {
            Log.w("Receiving " + f.dto.fileName + " failed", e);
            if (target != null) Storage.abort(core.context, target);
            f.error = e.getMessage();
            if (result == 422 && f.attempts < Protocol.MAX_UPLOAD_ATTEMPTS && s.status == Status.SENDING) {
                f.status = FileStatus.QUEUE;
            } else {
                f.status = FileStatus.FAILED;
            }
        }
        finishIfComplete(s);
        core.changed(Core.CHANGED_RECEIVE);
        if (result == 200) return HttpResponse.empty(200);
        if (result == 422) return HttpResponse.error(422, "Checksum mismatch");
        return HttpResponse.error(500, "Failed to save file");
    }

    private void finishIfComplete(Session s) {
        synchronized (this) {
            if (s.status != Status.SENDING) return;
            for (ReceivingFile f : s.files.values()) {
                if (f.status == FileStatus.QUEUE || f.status == FileStatus.SENDING) return;
            }
            s.status = s.count(FileStatus.FAILED) > 0 ? Status.FINISHED_WITH_ERRORS : Status.FINISHED;
            s.endTime = System.currentTimeMillis();
        }
        Log.i("Receive session finished: " + s.status);
        if (core.ui != null) core.ui.transferActive(false);
        core.changed(Core.CHANGED_RECEIVE);
    }

    /** POST /cancel from the sender. Returns false when the request concerns one of our send sessions. */
    boolean cancelFromSender(String ip, String sessionId) {
        Session s = session;
        if (s == null || !s.senderIp.equals(ip)) return false;
        if (s.status == Status.WAITING && (sessionId == null || sessionId.equals(s.id))) {
            synchronized (s.lock) {
                s.status = Status.CANCELED_BY_SENDER;
                s.endTime = System.currentTimeMillis();
                s.lock.notifyAll();
            }
            core.changed(Core.CHANGED_RECEIVE);
            return true;
        }
        if (s.status == Status.SENDING && s.id.equals(sessionId)) {
            s.status = Status.CANCELED_BY_SENDER;
            s.endTime = System.currentTimeMillis();
            if (core.ui != null) core.ui.transferActive(false);
            core.changed(Core.CHANGED_RECEIVE);
            return true;
        }
        return false;
    }

    /** The receiving user cancels: stop writing and tell the sender's server (LocalSend does the same). */
    public void cancelByReceiver() {
        final Session s = session;
        if (s == null || !s.isActive()) return;
        boolean wasSending = s.status == Status.SENDING;
        synchronized (s.lock) {
            s.status = Status.CANCELED_BY_RECEIVER;
            s.endTime = System.currentTimeMillis();
            s.lock.notifyAll();
        }
        if (core.ui != null) core.ui.transferActive(false);
        core.changed(Core.CHANGED_RECEIVE);
        if (wasSending && !s.fromBrowser) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        HttpClient c = new HttpClient(s.sender.https ? core.clientTls() : null, null);
                        c.request("POST", s.senderIp, s.sender.port, HttpClient.query(Protocol.CANCEL, "sessionId", s.id), null, new byte[0]);
                    } catch (Exception e) {
                        Log.d("Cancel notification to sender failed: " + e);
                    }
                }
            }, "receive-cancel").start();
        }
    }

    /** Used by the receive page when rendering the file list. */
    public static List<ReceivingFile> list(Session s) {
        return new ArrayList<ReceivingFile>(s.files.values());
    }

}
