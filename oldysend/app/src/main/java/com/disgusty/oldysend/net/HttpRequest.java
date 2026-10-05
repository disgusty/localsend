package com.disgusty.oldysend.net;

import com.disgusty.oldysend.util.IO;
import com.disgusty.oldysend.util.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/** A parsed HTTP/1.1 request whose body is exposed as a stream (fixed length or chunked). */
public final class HttpRequest {
    public String method;
    public String path;
    public String rawQuery;
    public final Map<String, String> query = new HashMap<String, String>();
    /** Header names are lower-cased. */
    public final Map<String, String> headers = new HashMap<String, String>();
    public String clientIp;
    /** SHA-256 (uppercase hex) of the TLS client certificate, or null. */
    public String certFingerprint;
    public InputStream body;
    public boolean keepAlive = true;
    /** Set by the server: tells whether the peer has closed the connection (used while waiting for decisions). */
    java.net.Socket socket;
    java.io.BufferedInputStream raw;

    /**
     * True when the client hung up. Probes the socket with a 1 ms read: end-of-stream means closed,
     * a timeout means the client is still waiting. Only valid once the request body was fully read.
     */
    public boolean peerClosed() {
        if (socket == null || raw == null) return false;
        int old = 30000;
        try {
            old = socket.getSoTimeout();
            socket.setSoTimeout(1);
            raw.mark(1);
            int b = raw.read();
            if (b == -1) return true;
            raw.reset();
            return false;
        } catch (java.net.SocketTimeoutException e) {
            return false;
        } catch (IOException e) {
            return true;
        } finally {
            try {
                socket.setSoTimeout(old);
            } catch (Exception ignored) {
            }
        }
    }

    private static final int MAX_LINE = 16 * 1024;

    /** Reads the request head. Returns null on a clean end of stream before any byte. */
    static HttpRequest read(InputStream in) throws IOException {
        String line = readLine(in);
        if (line == null) return null;
        while (line.length() == 0) {
            line = readLine(in);
            if (line == null) return null;
        }
        String[] parts = line.split(" ");
        if (parts.length < 2) throw new IOException("Bad request line");
        HttpRequest req = new HttpRequest();
        req.method = Text.upper(parts[0]);
        String target = parts[1];
        int q = target.indexOf('?');
        req.path = q < 0 ? target : target.substring(0, q);
        req.rawQuery = q < 0 ? null : target.substring(q + 1);
        if (req.rawQuery != null) parseQuery(req.rawQuery, req.query);
        boolean http10 = parts.length > 2 && "HTTP/1.0".equals(parts[2]);
        int count = 0;
        while (true) {
            String h = readLine(in);
            if (h == null) throw new IOException("Unexpected end of headers");
            if (h.length() == 0) break;
            if (++count > 100) throw new IOException("Too many headers");
            int c = h.indexOf(':');
            if (c <= 0) continue;
            req.headers.put(Text.lower(h.substring(0, c).trim()), h.substring(c + 1).trim());
        }
        String connection = req.header("connection");
        if (connection != null) {
            String lc = Text.lower(connection);
            if (lc.contains("close")) req.keepAlive = false;
            else if (lc.contains("keep-alive")) req.keepAlive = true;
        } else if (http10) {
            req.keepAlive = false;
        }
        String te = req.header("transfer-encoding");
        if (te != null && Text.lower(te).contains("chunked")) {
            req.body = new ChunkedInputStream(in);
        } else {
            long len = 0;
            String cl = req.header("content-length");
            if (cl != null) {
                try {
                    len = Long.parseLong(cl.trim());
                } catch (NumberFormatException e) {
                    throw new IOException("Bad content-length");
                }
            }
            req.body = new FixedLengthInputStream(in, len);
        }
        return req;
    }

    public String header(String name) {
        return headers.get(name);
    }

    public String param(String name) {
        return query.get(name);
    }

    public byte[] readBody(int limit) throws IOException {
        return IO.readLimited(body, limit);
    }

    /** Consumes whatever is left of the body so the connection can be reused. */
    void drain() throws IOException {
        byte[] buf = new byte[8192];
        while (body.read(buf) != -1) {
            // discard
        }
    }

    static void parseQuery(String raw, Map<String, String> out) {
        for (String pair : raw.split("&")) {
            if (pair.length() == 0) continue;
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            out.put(decode(k), decode(v));
        }
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    static String readLine(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream(128);
        int b;
        boolean any = false;
        while ((b = in.read()) != -1) {
            any = true;
            if (b == '\n') break;
            if (b == '\r') continue;
            raw.write(b);
            if (raw.size() > MAX_LINE) throw new IOException("Line too long");
        }
        if (!any) return null;
        return Text.utf8(raw.toByteArray());
    }

    /** Body with a known length. */
    static final class FixedLengthInputStream extends InputStream {
        private final InputStream in;
        private long remaining;

        FixedLengthInputStream(InputStream in, long length) {
            this.in = in;
            this.remaining = length;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int b = in.read();
            if (b == -1) throw new IOException("Connection closed early");
            remaining--;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int n = in.read(b, off, (int) Math.min(len, remaining));
            if (n == -1) throw new IOException("Connection closed early");
            remaining -= n;
            return n;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(in.available(), remaining);
        }
    }

    /** Transfer-Encoding: chunked body. */
    static final class ChunkedInputStream extends InputStream {
        private final InputStream in;
        private long chunkRemaining;
        private boolean done;

        ChunkedInputStream(InputStream in) {
            this.in = in;
        }

        private boolean nextChunk() throws IOException {
            if (done) return false;
            if (chunkRemaining == 0) {
                String line = readLine(in);
                if (line == null) throw new IOException("Connection closed in chunk header");
                if (line.length() == 0) {
                    // CRLF after the previous chunk's data
                    line = readLine(in);
                    if (line == null) throw new IOException("Connection closed in chunk header");
                }
                int semi = line.indexOf(';');
                String hex = (semi < 0 ? line : line.substring(0, semi)).trim();
                try {
                    chunkRemaining = Long.parseLong(hex, 16);
                } catch (NumberFormatException e) {
                    throw new IOException("Bad chunk size: " + hex);
                }
                if (chunkRemaining == 0) {
                    // trailers until an empty line
                    String t;
                    while ((t = readLine(in)) != null && t.length() > 0) {
                        // ignore trailer
                    }
                    done = true;
                    return false;
                }
            }
            return true;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n <= 0 ? -1 : (one[0] & 0xFF);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (!nextChunk()) return -1;
            int n = in.read(b, off, (int) Math.min(len, chunkRemaining));
            if (n == -1) throw new IOException("Connection closed in chunk");
            chunkRemaining -= n;
            return n;
        }
    }
}
