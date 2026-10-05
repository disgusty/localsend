package com.disgusty.oldysend.net;

import org.json.JSONObject;
import com.disgusty.oldysend.util.IO;
import com.disgusty.oldysend.util.Text;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

/**
 * One-shot HTTP/1.1 requests over a raw socket ("Connection: close").
 * A request can be aborted from another thread with {@link #cancel()}, which closes the socket.
 */
public final class HttpClient {
    public static final class Response {
        public int status;
        public final Map<String, String> headers = new HashMap<String, String>();
        public byte[] body;
        /** Set for streamed responses (download); the caller must close it. */
        public InputStream stream;
        public long contentLength = -1;
        /** Fingerprint of the server certificate (HTTPS only). */
        public String peerFingerprint;

        public JSONObject json() throws Exception {
            return new JSONObject(Text.utf8(body));
        }

        /** LocalSend error message from {"message": "..."} or the raw body. */
        public String message() {
            if (body == null || body.length == 0) return null;
            try {
                return json().optString("message", null);
            } catch (Exception e) {
                return Text.utf8(body);
            }
        }
    }

    public static final class StatusException extends IOException {
        public final int status;
        public final String serverMessage;

        public StatusException(int status, String serverMessage) {
            super("HTTP " + status + (serverMessage != null ? ": " + serverMessage : ""));
            this.status = status;
            this.serverMessage = serverMessage;
        }
    }

    /** The TCP connection could not be opened (refused, unreachable, timeout) — nothing was sent. */
    public static final class ConnectException extends IOException {
        public final String address;

        public ConnectException(String address, IOException cause) {
            super("Failed to connect to " + address + ": " + cause.getMessage());
            this.address = address;
            initCause(cause);
        }
    }

    private final SSLContext tls;
    private final String expectedFingerprint;
    private volatile Socket socket;
    private volatile boolean cancelled;
    public int connectTimeoutMs = 5000;
    /** 0 = wait forever (prepare-upload waits for the receiver's decision). */
    public int readTimeoutMs = 30000;

    /**
     * @param tls                 null for plain HTTP
     * @param expectedFingerprint pins the server certificate in HTTPS mode; null only for discovery
     */
    public HttpClient(SSLContext tls, String expectedFingerprint) {
        this.tls = tls;
        this.expectedFingerprint = expectedFingerprint;
    }

    public void cancel() {
        cancelled = true;
        IO.close(socket);
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public static String query(String path, String... params) {
        StringBuilder sb = new StringBuilder(path);
        boolean first = true;
        for (int i = 0; i + 1 < params.length; i += 2) {
            if (params[i + 1] == null) continue;
            sb.append(first ? '?' : '&');
            first = false;
            try {
                sb.append(params[i]).append('=').append(URLEncoder.encode(params[i + 1], "UTF-8"));
            } catch (Exception e) {
                sb.append(params[i]).append('=').append(params[i + 1]);
            }
        }
        return sb.toString();
    }

    public Response request(String method, String host, int port, String pathAndQuery, String contentType, byte[] body) throws IOException {
        return execute(method, host, port, pathAndQuery, contentType, body, null, -1, null, false);
    }

    public Response postJson(String host, int port, String path, JSONObject json) throws IOException {
        return request("POST", host, port, path, "application/json", Text.utf8(json.toString()));
    }

    /** Streams {@code length} bytes from {@code source} as the request body. */
    public Response upload(String host, int port, String pathAndQuery, InputStream source, long length, IO.Progress progress) throws IOException {
        return execute("POST", host, port, pathAndQuery, "application/octet-stream", null, source, length, progress, false);
    }

    /** GET whose body is returned as an open stream. */
    public Response openStream(String host, int port, String pathAndQuery) throws IOException {
        return execute("GET", host, port, pathAndQuery, null, null, null, -1, null, true);
    }

    private Response execute(String method, String host, int port, String pathAndQuery, String contentType, byte[] body,
                             InputStream source, long sourceLength, IO.Progress progress, boolean streamResponse) throws IOException {
        if (cancelled) throw new IOException("Cancelled");
        Socket s = new Socket();
        socket = s;
        boolean keepOpen = false;
        try {
            try {
                s.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            } catch (IOException e) {
                if (cancelled) throw e;
                throw new ConnectException(host + ":" + port, e);
            }
            s.setSoTimeout(readTimeoutMs);
            s.setTcpNoDelay(true);
            String peerFingerprint = null;
            if (tls != null) {
                SSLSocket ssl;
                try {
                    ssl = (SSLSocket) tls.getSocketFactory().createSocket(s, host, port, true);
                    Tls.restrictProtocols(ssl);
                    ssl.startHandshake();
                } catch (Exception e) {
                    throw new IOException("TLS handshake failed: " + e);
                }
                peerFingerprint = Tls.verifyPeer(ssl, expectedFingerprint);
                s = ssl;
                socket = ssl;
            }
            if (cancelled) throw new IOException("Cancelled");
            OutputStream out = new BufferedOutputStream(s.getOutputStream(), IO.BUFFER);
            StringBuilder head = new StringBuilder();
            head.append(method).append(' ').append(pathAndQuery).append(" HTTP/1.1\r\n");
            head.append("Host: ").append(host.indexOf(':') >= 0 ? "[" + host + "]" : host).append(':').append(port).append("\r\n");
            head.append("User-Agent: OldySend\r\n");
            head.append("Connection: close\r\n");
            long length = body != null ? body.length : (source != null ? sourceLength : 0);
            if (contentType != null) head.append("Content-Type: ").append(contentType).append("\r\n");
            if (body != null || source != null || "POST".equals(method)) head.append("Content-Length: ").append(length).append("\r\n");
            head.append("\r\n");
            out.write(Text.utf8(head.toString()));
            if (body != null) {
                out.write(body);
            } else if (source != null) {
                byte[] buf = new byte[IO.BUFFER];
                long sent = 0;
                int n;
                while (sent < sourceLength && (n = source.read(buf, 0, (int) Math.min(buf.length, sourceLength - sent))) != -1) {
                    out.write(buf, 0, n);
                    sent += n;
                    if (cancelled) throw new IOException("Cancelled");
                    if (progress != null && !progress.onProgress(sent)) throw new IOException("Cancelled");
                }
                if (sent != sourceLength) throw new IOException("File changed while sending (" + sent + "/" + sourceLength + ")");
            }
            out.flush();

            InputStream in = new BufferedInputStream(s.getInputStream(), IO.BUFFER);
            Response res = new Response();
            res.peerFingerprint = peerFingerprint;
            String status = HttpRequest.readLine(in);
            if (status == null) throw new IOException("Empty response");
            String[] parts = status.split(" ");
            if (parts.length < 2) throw new IOException("Bad status line");
            try {
                res.status = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new IOException("Bad status line");
            }
            String line;
            while ((line = HttpRequest.readLine(in)) != null && line.length() > 0) {
                int c = line.indexOf(':');
                if (c > 0) res.headers.put(Text.lower(line.substring(0, c).trim()), line.substring(c + 1).trim());
            }
            InputStream bodyStream;
            String te = res.headers.get("transfer-encoding");
            String cl = res.headers.get("content-length");
            if (te != null && Text.lower(te).contains("chunked")) {
                bodyStream = new HttpRequest.ChunkedInputStream(in);
            } else if (cl != null) {
                res.contentLength = Long.parseLong(cl.trim());
                bodyStream = new HttpRequest.FixedLengthInputStream(in, res.contentLength);
            } else if (res.status == 204 || "HEAD".equals(method)) {
                bodyStream = new HttpRequest.FixedLengthInputStream(in, 0);
            } else {
                bodyStream = in; // until close
            }
            if (streamResponse && res.status == 200) {
                res.stream = new SocketClosingStream(bodyStream, s);
                keepOpen = true;
            } else {
                res.body = IO.readLimited(bodyStream, 16 * 1024 * 1024);
            }
            return res;
        } catch (IOException e) {
            if (cancelled) throw new IOException("Cancelled");
            throw e;
        } finally {
            if (!keepOpen) IO.close(s);
        }
    }

    private static final class SocketClosingStream extends InputStream {
        private final InputStream in;
        private final Socket socket;

        SocketClosingStream(InputStream in, Socket socket) {
            this.in = in;
            this.socket = socket;
        }

        @Override
        public int read() throws IOException {
            return in.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return in.read(b, off, len);
        }

        @Override
        public void close() {
            IO.close(socket);
        }
    }
}
