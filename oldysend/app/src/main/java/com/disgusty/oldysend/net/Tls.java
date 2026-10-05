package com.disgusty.oldysend.net;

import com.disgusty.oldysend.util.Codec;
import com.disgusty.oldysend.util.IO;
import com.disgusty.oldysend.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

/**
 * Device identity for HTTPS mode, compatible with LocalSend: an RSA-2048 key and a self-signed certificate
 * (CN=LocalSend User, valid 1975–4096) whose SHA-256 is the device fingerprint. The same certificate is used
 * as server certificate and as client certificate (LocalSend requires mutual TLS).
 */
public final class Tls {
    private static final String ALIAS = "oldysend";

    public final PrivateKey privateKey;
    public final X509Certificate certificate;
    public final String fingerprint;
    private SSLContext context;

    private Tls(PrivateKey key, X509Certificate cert) throws Exception {
        this.privateKey = key;
        this.certificate = cert;
        this.fingerprint = Codec.hexUpper(Codec.sha256(cert.getEncoded()));
    }

    /** Loads the stored identity or generates and stores a new one. Slow on first call (key generation). */
    public static Tls loadOrCreate(File dir) throws Exception {
        File keyFile = new File(dir, "identity.key");
        File certFile = new File(dir, "identity.crt");
        if (keyFile.exists() && certFile.exists()) {
            try {
                PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(readFile(keyFile)));
                X509Certificate cert = parseCert(readFile(certFile));
                return new Tls(key, cert);
            } catch (Exception e) {
                Log.w("Stored identity unreadable, generating a new one", e);
            }
        }
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048, new SecureRandom());
        KeyPair pair = gen.generateKeyPair();
        byte[] der = selfSigned(pair);
        X509Certificate cert = parseCert(der);
        dir.mkdirs();
        writeFile(keyFile, pair.getPrivate().getEncoded());
        writeFile(certFile, der);
        return new Tls(pair.getPrivate(), cert);
    }

    public static void deleteIdentity(File dir) {
        new File(dir, "identity.key").delete();
        new File(dir, "identity.crt").delete();
    }

    private static X509Certificate parseCert(byte[] der) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
    }

    public synchronized SSLContext context() throws Exception {
        if (context == null) {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(new KeyManager[]{new FixedKeyManager()}, new TrustManager[]{new AcceptAllTrustManager()}, new SecureRandom());
            context = ctx;
        }
        return context;
    }

    /** TLS 1.2+ only, like rustls. */
    static void restrictProtocols(SSLServerSocket socket) {
        socket.setEnabledProtocols(filterProtocols(socket.getSupportedProtocols()));
    }

    static void restrictProtocols(SSLSocket socket) {
        socket.setEnabledProtocols(filterProtocols(socket.getSupportedProtocols()));
    }

    private static String[] filterProtocols(String[] supported) {
        List<String> out = new ArrayList<String>();
        for (String p : supported) if ("TLSv1.2".equals(p) || "TLSv1.3".equals(p)) out.add(p);
        if (out.isEmpty()) return supported;
        return out.toArray(new String[out.size()]);
    }

    /**
     * Checks the peer certificate like LocalSend's PinnedServerCertVerifier: valid signature and time,
     * and, when known, the expected fingerprint. Returns the peer fingerprint.
     */
    public static String verifyPeer(SSLSocket socket, String expectedFingerprint) throws IOException {
        try {
            java.security.cert.Certificate[] chain = socket.getSession().getPeerCertificates();
            X509Certificate cert = (X509Certificate) chain[0];
            cert.checkValidity();
            cert.verify(cert.getPublicKey());
            String actual = Codec.hexUpper(Codec.sha256(cert.getEncoded()));
            if (expectedFingerprint != null && expectedFingerprint.length() > 0 && !actual.equalsIgnoreCase(expectedFingerprint)) {
                throw new IOException("Server certificate fingerprint mismatch");
            }
            return actual;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Server certificate is not valid: " + e);
        }
    }

    private final class FixedKeyManager extends X509ExtendedKeyManager {
        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return new String[]{ALIAS};
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            // Always present our certificate, whatever CA hints the peer sends.
            return ALIAS;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return new String[]{ALIAS};
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return "RSA".equalsIgnoreCase(keyType) ? ALIAS : null;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return new X509Certificate[]{certificate};
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return privateKey;
        }
    }

    /** Peers are identified by certificate fingerprint, so any self-signed certificate is accepted here. */
    static final class AcceptAllTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    // ---- Minimal DER encoding of an X.509 v3 certificate ----

    private static final byte[] OID_SHA256_RSA = {0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x0B};
    private static final byte[] OID_CN = {0x55, 0x04, 0x03};

    static byte[] selfSigned(KeyPair pair) throws Exception {
        byte[] algorithm = seq(cat(tlv(0x06, OID_SHA256_RSA), tlv(0x05, new byte[0])));
        byte[] name = seq(tlv(0x31, seq(cat(tlv(0x06, OID_CN), tlv(0x0C, "LocalSend User".getBytes("UTF-8"))))));
        byte[] validity = seq(cat(tlv(0x17, "750101000000Z".getBytes("US-ASCII")), tlv(0x18, "40960101000000Z".getBytes("US-ASCII"))));
        byte[] serial = new BigInteger(1, Codec.sha256(pair.getPublic().getEncoded())).shiftRight(256 - 159).toByteArray();
        byte[] tbs = seq(cat(
                tlv(0xA0, tlv(0x02, new byte[]{2})),
                tlv(0x02, serial),
                algorithm,
                name,
                validity,
                name,
                pair.getPublic().getEncoded()));
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(pair.getPrivate());
        sig.update(tbs);
        byte[] signature = sig.sign();
        byte[] bitString = new byte[signature.length + 1];
        System.arraycopy(signature, 0, bitString, 1, signature.length);
        return seq(cat(tbs, algorithm, tlv(0x03, bitString)));
    }

    private static byte[] seq(byte[] content) {
        return tlv(0x30, content);
    }

    private static byte[] tlv(int tag, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(content.length + 6);
        out.write(tag);
        int len = content.length;
        if (len < 0x80) {
            out.write(len);
        } else if (len < 0x100) {
            out.write(0x81);
            out.write(len);
        } else if (len < 0x10000) {
            out.write(0x82);
            out.write(len >> 8);
            out.write(len);
        } else {
            out.write(0x83);
            out.write(len >> 16);
            out.write(len >> 8);
            out.write(len);
        }
        out.write(content, 0, content.length);
        return out.toByteArray();
    }

    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.write(p, 0, p.length);
        return out.toByteArray();
    }

    private static byte[] readFile(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            return IO.readAll(in);
        } finally {
            IO.close(in);
        }
    }

    private static void writeFile(File f, byte[] data) throws IOException {
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(data);
        } finally {
            IO.close(out);
        }
    }
}
