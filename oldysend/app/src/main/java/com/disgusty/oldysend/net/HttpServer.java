package com.disgusty.oldysend.net;

import com.disgusty.oldysend.util.Codec;
import com.disgusty.oldysend.util.IO;
import com.disgusty.oldysend.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

/**
 * Minimal HTTP/1.1 server (keep-alive, chunked request bodies) on plain or TLS sockets.
 * Mirrors LocalSend's limits: a bounded number of connections in total and per peer.
 */
public final class HttpServer {
    public interface Handler {
        HttpResponse handle(HttpRequest request) throws Exception;
    }

    private static final int MAX_CONNECTIONS = 64;
    private static final int MAX_CONNECTIONS_PER_IP = 16;
    private static final int IDLE_TIMEOUT_MS = 30000;

    private final Handler handler;
    private final SSLContext tls;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ThreadPoolExecutor workers;
    private volatile boolean running;
    private final Set<Socket> open = new HashSet<Socket>();
    private final Map<String, Integer> perIp = new HashMap<String, Integer>();

    public HttpServer(Handler handler, SSLContext tls) {
        this.handler = handler;
        this.tls = tls;
    }

    public synchronized void start(int port) throws IOException {
        if (running) return;
        ServerSocket socket;
        if (tls != null) {
            SSLServerSocket ssl = (SSLServerSocket) tls.getServerSocketFactory().createServerSocket();
            // Client certificates identify LocalSend peers; browsers on the web pages send none.
            ssl.setWantClientAuth(true);
            Tls.restrictProtocols(ssl);
            socket = ssl;
        } else {
            socket = new ServerSocket();
        }
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port), 50);
        serverSocket = socket;
        running = true;
        workers = new ThreadPoolExecutor(0, MAX_CONNECTIONS, 30, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        }, "http-accept");
        acceptThread.start();
        Log.i("HTTP server listening on " + port + (tls != null ? " (TLS)" : ""));
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        IO.close(serverSocket);
        synchronized (open) {
            for (Socket s : open) IO.close(s);
            open.clear();
        }
        if (workers != null) workers.shutdownNow();
        Log.i("HTTP server stopped");
    }

    public boolean isRunning() {
        return running;
    }

    private void acceptLoop() {
        int failures = 0;
        while (running) {
            final Socket s;
            try {
                s = serverSocket.accept();
                failures = 0;
            } catch (IOException e) {
                if (!running) return;
                if (++failures > 10) {
                    Log.e("Accept failed repeatedly, server stops", e);
                    running = false;
                    return;
                }
                continue;
            }
            final String ip = s.getInetAddress().getHostAddress();
            if (!acquire(ip)) {
                IO.close(s);
                continue;
            }
            try {
                workers.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            serve(s, ip);
                        } finally {
                            release(ip);
                        }
                    }
                });
            } catch (Exception e) {
                release(ip);
                IO.close(s);
            }
        }
    }

    private boolean acquire(String ip) {
        synchronized (perIp) {
            int total = 0;
            for (Integer v : perIp.values()) total += v;
            Integer cur = perIp.get(ip);
            int c = cur == null ? 0 : cur;
            if (total >= MAX_CONNECTIONS || c >= MAX_CONNECTIONS_PER_IP) return false;
            perIp.put(ip, c + 1);
            return true;
        }
    }

    private void release(String ip) {
        synchronized (perIp) {
            Integer cur = perIp.get(ip);
            if (cur == null || cur <= 1) perIp.remove(ip);
            else perIp.put(ip, cur - 1);
        }
    }

    private void serve(Socket socket, String ip) {
        synchronized (open) {
            open.add(socket);
        }
        try {
            socket.setSoTimeout(IDLE_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            String certFingerprint = null;
            if (socket instanceof SSLSocket) {
                SSLSocket ssl = (SSLSocket) socket;
                ssl.startHandshake();
                try {
                    Certificate[] chain = ssl.getSession().getPeerCertificates();
                    if (chain != null && chain.length > 0) {
                        certFingerprint = Codec.hexUpper(Codec.sha256(chain[0].getEncoded()));
                    }
                } catch (Exception noClientCert) {
                    // Browser or peer without certificate.
                }
            }
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream(), 16 * 1024);
            OutputStream out = new BufferedOutputStream(socket.getOutputStream(), 16 * 1024);
            while (running) {
                HttpRequest req = HttpRequest.read(in);
                if (req == null) break;
                req.clientIp = ip;
                req.certFingerprint = certFingerprint;
                req.socket = socket;
                req.raw = in;
                HttpResponse res;
                try {
                    res = handler.handle(req);
                } catch (Exception e) {
                    Log.w("Handler failed for " + req.path, e);
                    res = HttpResponse.error(500, "Internal server error");
                }
                if (res == null) res = HttpResponse.empty(404);
                boolean keepAlive = req.keepAlive;
                try {
                    req.drain();
                } catch (IOException e) {
                    keepAlive = false;
                }
                res.write(out, keepAlive);
                if (!keepAlive) break;
            }
        } catch (SocketException ignored) {
            // closed by peer or by stop()
        } catch (java.net.SocketTimeoutException ignored) {
            // idle keep-alive connection
        } catch (IOException e) {
            Log.d("Connection from " + ip + " ended: " + e);
        } finally {
            IO.close(socket);
            synchronized (open) {
                open.remove(socket);
            }
        }
    }
}
