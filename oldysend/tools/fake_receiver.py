"""A minimal LocalSend v2.2 receiver for testing OldySend as a sender.

usage: python tools/fake_receiver.py [http|https] [port] [seconds]
Accepts every request, stores files in a temp dir, verifies sha256, prints a summary.
"""
import hashlib
import json
import os
import ssl
import sys
import tempfile
import threading
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

scheme = sys.argv[1] if len(sys.argv) > 1 else "http"
port = int(sys.argv[2]) if len(sys.argv) > 2 else 53317
seconds = int(sys.argv[3]) if len(sys.argv) > 3 else 60
OUT = tempfile.mkdtemp(prefix="fake_ls_")
INFO = {"alias": "Fake Desktop", "version": "2.2", "deviceModel": "Linux", "deviceType": "desktop",
        "fingerprint": "fake-" + uuid.uuid4().hex[:6], "port": port, "protocol": scheme, "download": False}
session = {}
log = []


class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def body(self):
        if "chunked" in self.headers.get("Transfer-Encoding", ""):
            data = b""
            while True:
                size = int(self.rfile.readline().strip(), 16)
                if size == 0:
                    self.rfile.readline()
                    return data
                data += self.rfile.read(size)
                self.rfile.readline()
        return self.rfile.read(int(self.headers.get("Content-Length", 0)))

    def reply(self, code, obj=None):
        data = json.dumps(obj).encode() if obj is not None else b""
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *a):
        pass

    def do_POST(self):
        u = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        data = self.body()
        if u.path == "/api/localsend/v2/register":
            peer = json.loads(data)
            log.append(("register", peer.get("alias"), peer.get("protocol")))
            return self.reply(200, INFO)
        if u.path == "/api/localsend/v2/prepare-upload":
            req = json.loads(data)
            files = req["files"]
            log.append(("prepare", req["info"]["alias"], [f["fileName"] for f in files.values()]))
            if len(files) == 1 and list(files.values())[0].get("preview") and list(files.values())[0]["fileType"].startswith("text/"):
                log.append(("message", list(files.values())[0]["preview"]))
                return self.reply(204)
            session["id"] = str(uuid.uuid4())
            session["files"] = files
            session["tokens"] = {fid: str(uuid.uuid4()) for fid in files}
            return self.reply(200, {"sessionId": session["id"], "files": session["tokens"]})
        if u.path == "/api/localsend/v2/upload":
            fid = q.get("fileId")
            if q.get("sessionId") != session.get("id") or session["tokens"].get(fid) != q.get("token"):
                return self.reply(403, {"message": "Invalid token"})
            meta = session["files"][fid]
            path = os.path.join(OUT, meta["fileName"].replace("/", "_"))
            open(path, "wb").write(data)
            ok = len(data) == meta["size"] and (not meta.get("sha256") or hashlib.sha256(data).hexdigest() == meta["sha256"])
            log.append(("upload", meta["fileName"], len(data), "ok" if ok else "BAD"))
            return self.reply(200 if ok else 422)
        if u.path == "/api/localsend/v2/cancel":
            log.append(("cancel",))
            return self.reply(200)
        self.reply(404)

    def do_GET(self):
        self.reply(200, INFO)


srv = ThreadingHTTPServer(("0.0.0.0", port), H)
if scheme == "https":
    from cryptography import x509
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import rsa
    from cryptography.x509.oid import NameOID
    import datetime
    k = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "LocalSend User")])
    cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(k.public_key()).serial_number(7)
            .not_valid_before(datetime.datetime(2000, 1, 1)).not_valid_after(datetime.datetime(2100, 1, 1)).sign(k, hashes.SHA256()))
    d = tempfile.mkdtemp()
    open(os.path.join(d, "k"), "wb").write(k.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    open(os.path.join(d, "c"), "wb").write(cert.public_bytes(serialization.Encoding.PEM))
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.minimum_version = ssl.TLSVersion.TLSv1_2
    ctx.load_cert_chain(os.path.join(d, "c"), os.path.join(d, "k"))
    srv.socket = ctx.wrap_socket(srv.socket, server_side=True)
threading.Timer(seconds, srv.shutdown).start()
print("listening", scheme, port, "->", OUT, flush=True)
srv.serve_forever()
for entry in log:
    print(*entry)
