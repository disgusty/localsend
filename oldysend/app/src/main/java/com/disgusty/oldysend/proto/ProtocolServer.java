package com.disgusty.oldysend.proto;

import org.json.JSONObject;
import com.disgusty.oldysend.net.HttpRequest;
import com.disgusty.oldysend.net.HttpResponse;
import com.disgusty.oldysend.net.HttpServer;
import com.disgusty.oldysend.util.Log;
import com.disgusty.oldysend.util.Text;

/** Routes of LocalSend protocol v2.2 plus the web share pages, matching packages/core/src/http/server/mod.rs. */
final class ProtocolServer implements HttpServer.Handler {
    private final Core core;

    ProtocolServer(Core core) {
        this.core = core;
    }

    @Override
    public HttpResponse handle(HttpRequest req) throws Exception {
        String m = req.method;
        String p = req.path;
        if ("GET".equals(m) && "/".equals(p)) return core.web.index();
        if ("GET".equals(m) && "/i18n.json".equals(p)) return core.web.i18n();
        if ("POST".equals(m) && Protocol.PREPARE_DOWNLOAD.equals(p)) return core.web.prepareDownload(req);
        if ("GET".equals(m) && Protocol.DOWNLOAD.equals(p)) return core.web.download(req);
        if ("POST".equals(m) && Protocol.REGISTER.equals(p)) return register(req);
        if ("GET".equals(m) && (Protocol.INFO.equals(p) || Protocol.INFO_V1.equals(p))) {
            return HttpResponse.json(200, core.info(false));
        }
        if ("POST".equals(m) && Protocol.PREPARE_UPLOAD.equals(p)) return core.receive.prepareUpload(req);
        if ("POST".equals(m) && Protocol.UPLOAD.equals(p)) return core.receive.upload(req);
        if ("POST".equals(m) && Protocol.CANCEL.equals(p)) return cancel(req);
        return HttpResponse.empty(404);
    }

    private HttpResponse register(HttpRequest req) throws Exception {
        JSONObject body;
        try {
            body = new JSONObject(Text.utf8(req.readBody(64 * 1024)));
        } catch (Exception e) {
            return HttpResponse.error(400, "Invalid body");
        }
        Device d = Device.fromInfo(body, req.clientIp, 53317, false);
        // On TLS, only trust registrations whose claimed fingerprint is proven by the client certificate.
        boolean valid = req.certFingerprint == null || req.certFingerprint.equalsIgnoreCase(d.fingerprint);
        if (valid) {
            d.discoveredVia = "http";
            d.verified = true;
            core.registerDevice(d);
        } else {
            Log.w("Ignoring register from " + req.clientIp + ": fingerprint does not match certificate");
        }
        return HttpResponse.json(200, core.info(false));
    }

    private HttpResponse cancel(HttpRequest req) {
        String sessionId = req.param("sessionId");
        if (core.receive.cancelFromSender(req.clientIp, sessionId)) return HttpResponse.empty(200);
        // Not one of our receive sessions: the peer may cancel a transfer we are sending to it.
        if (sessionId != null) core.send.cancelFromReceiver(req.clientIp, sessionId);
        return HttpResponse.empty(200);
    }
}
