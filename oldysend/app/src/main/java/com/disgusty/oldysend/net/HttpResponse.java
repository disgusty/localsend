package com.disgusty.oldysend.net;

import org.json.JSONObject;
import com.disgusty.oldysend.util.IO;
import com.disgusty.oldysend.util.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** Response produced by a route handler; the body is either bytes or a stream with a known length. */
public final class HttpResponse {
    public int status = 200;
    public final Map<String, String> headers = new LinkedHashMap<String, String>();
    public byte[] body;
    public InputStream stream;
    public long streamLength = -1;
    /** Invoked with the number of streamed bytes; return false to abort the transfer. */
    public IO.Progress streamProgress;
    /** Called after the streamed body finished (true) or failed (false). */
    public StreamListener streamListener;

    public interface StreamListener {
        void onFinished(boolean success);
    }

    public static HttpResponse empty(int status) {
        HttpResponse r = new HttpResponse();
        r.status = status;
        r.body = new byte[0];
        return r;
    }

    public static HttpResponse json(int status, JSONObject json) {
        HttpResponse r = new HttpResponse();
        r.status = status;
        r.headers.put("Content-Type", "application/json");
        r.body = Text.utf8(json.toString());
        return r;
    }

    /** LocalSend's error body: {"message": "..."} */
    public static HttpResponse error(int status, String message) {
        JSONObject o = new JSONObject();
        try {
            o.put("message", message);
        } catch (Exception ignored) {
        }
        return json(status, o);
    }

    public static HttpResponse html(int status, String html) {
        HttpResponse r = new HttpResponse();
        r.status = status;
        r.headers.put("Content-Type", "text/html; charset=utf-8");
        r.body = Text.utf8(html);
        return r;
    }

    static String reason(int status) {
        switch (status) {
            case 200: return "OK";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 409: return "Conflict";
            case 422: return "Unprocessable Entity";
            case 429: return "Too Many Requests";
            case 500: return "Internal Server Error";
            default: return "Status";
        }
    }

    void write(OutputStream out, boolean keepAlive) throws IOException {
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n");
        long length = body != null ? body.length : streamLength;
        for (Map.Entry<String, String> h : headers.entrySet()) {
            head.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
        }
        if (status != 204) head.append("Content-Length: ").append(Math.max(0, length)).append("\r\n");
        head.append("Connection: ").append(keepAlive ? "keep-alive" : "close").append("\r\n\r\n");
        out.write(Text.utf8(head.toString()));
        if (body != null) {
            if (status != 204) out.write(body);
            out.flush();
            return;
        }
        boolean ok = false;
        try {
            byte[] buf = new byte[IO.BUFFER];
            long sent = 0;
            int n;
            while (sent < streamLength && (n = stream.read(buf, 0, (int) Math.min(buf.length, streamLength - sent))) != -1) {
                out.write(buf, 0, n);
                sent += n;
                if (streamProgress != null && !streamProgress.onProgress(sent)) throw new IOException("Cancelled");
            }
            out.flush();
            if (sent != streamLength) throw new IOException("Source ended early");
            ok = true;
        } finally {
            IO.close(stream);
            if (streamListener != null) streamListener.onFinished(ok);
        }
    }
}
