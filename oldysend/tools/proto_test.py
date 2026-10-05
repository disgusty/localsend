"""Protocol conformance test against a running OldySend (adb forward tcp:53317 tcp:53317).

Acts like LocalSend: register, info, prepare-upload (+PIN), upload (with sha256), message, cancel.
usage: python tools/proto_test.py [http|https] [host] [port]
"""
import hashlib
import json
import os
import socket
import ssl
import sys
import tempfile
import uuid

scheme = sys.argv[1] if len(sys.argv) > 1 else "http"
host = sys.argv[2] if len(sys.argv) > 2 else "127.0.0.1"
port = int(sys.argv[3]) if len(sys.argv) > 3 else 53317

ctx = None
fingerprint = "py-test-" + uuid.uuid4().hex[:8]
if scheme == "https":
    # LocalSend uses mutual TLS: present a self-signed client certificate.
    import subprocess
    d = tempfile.mkdtemp()
    key, crt = os.path.join(d, "k.pem"), os.path.join(d, "c.pem")
    try:
        from cryptography import x509
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import rsa
        from cryptography.x509.oid import NameOID
        import datetime
        k = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "LocalSend User")])
        cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(k.public_key())
                .serial_number(1).not_valid_before(datetime.datetime(2000, 1, 1)).not_valid_after(datetime.datetime(2100, 1, 1))
                .sign(k, hashes.SHA256()))
        open(key, "wb").write(k.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        open(crt, "wb").write(cert.public_bytes(serialization.Encoding.PEM))
        fingerprint = hashlib.sha256(cert.public_bytes(serialization.Encoding.DER)).hexdigest().upper()
    except ImportError:
        print("pip install cryptography")
        sys.exit(1)
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    ctx.minimum_version = ssl.TLSVersion.TLSv1_2
    ctx.load_cert_chain(crt, key)

INFO = {"alias": "Python Tester", "version": "2.2", "deviceModel": "CPython", "deviceType": "desktop",
        "fingerprint": fingerprint, "port": 53317, "protocol": scheme, "download": False}


def request(method, path, body=b"", headers=None, chunked=False):
    s = socket.create_connection((host, port), timeout=30)
    if ctx:
        s = ctx.wrap_socket(s, server_hostname=None)
    head = f"{method} {path} HTTP/1.1\r\nHost: {host}:{port}\r\nConnection: close\r\n"
    for k, v in (headers or {}).items():
        head += f"{k}: {v}\r\n"
    if chunked:
        head += "Transfer-Encoding: chunked\r\n\r\n"
        s.sendall(head.encode())
        for i in range(0, len(body), 7000):
            part = body[i:i + 7000]
            s.sendall(b"%x\r\n" % len(part) + part + b"\r\n")
        s.sendall(b"0\r\n\r\n")
    else:
        head += f"Content-Length: {len(body)}\r\n\r\n"
        s.sendall(head.encode() + body)
    data = b""
    while True:
        chunk = s.recv(65536)
        if not chunk:
            break
        data += chunk
    s.close()
    head, _, payload = data.partition(b"\r\n\r\n")
    status = int(head.split(b" ")[1])
    return status, payload


def check(name, cond, extra=""):
    print(("PASS " if cond else "FAIL ") + name + (" " + str(extra) if extra else ""))
    if not cond:
        global failures
        failures += 1


failures = 0
import subprocess as _sp
_sp.run([os.path.join(os.environ.get('LOCALAPPDATA', ''), 'Android', 'Sdk', 'platform-tools', 'adb.exe'), 'shell', 'input', 'keyevent', '82'])
st, body = request("POST", "/api/localsend/v2/register", json.dumps(INFO).encode(), {"Content-Type": "application/json"})
reg = json.loads(body)
check("register", st == 200 and "alias" in reg and reg.get("version") == "2.2", reg)
st, body = request("GET", "/api/localsend/v1/info")
check("v1 info", st == 200 and json.loads(body).get("alias") == reg["alias"])
st, body = request("GET", "/")
check("index without web share = 403", st == 403)

# Text message (single text/plain file with preview) -> 204
msg_id = str(uuid.uuid4())
payload = {"info": INFO, "files": {msg_id: {"id": msg_id, "fileName": msg_id + ".txt", "size": 5, "fileType": "text/plain", "preview": "Hello"}}}
import subprocess
import threading
import time
ADB = os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk", "platform-tools", "adb.exe")
# The receive page shows the message; "back" closes it (= accept nothing -> 204).
threading.Thread(target=lambda: (time.sleep(4), subprocess.run([ADB, "shell", "input", "keyevent", "4"]))).start()
st, body = request("POST", "/api/localsend/v2/prepare-upload", json.dumps(payload).encode(), {"Content-Type": "application/json"})
check("message -> 204 (needs quick save off: user closes dialog) or 403", st in (204, 403), st)

# File upload (quick save must be ON on the device for an unattended test)
data = os.urandom(300_000) + "Ünïcødé".encode()
fid = "f1"
files = {fid: {"id": fid, "fileName": "folder/test ü.bin", "size": len(data), "fileType": "application/octet-stream",
               "sha256": hashlib.sha256(data).hexdigest(), "metadata": {"modified": "2021-01-01T12:34:56.123456789Z"}},
         "f2": {"id": "f2", "fileName": "small.txt", "size": 3, "fileType": "text/plain"}}
st, body = request("POST", "/api/localsend/v2/prepare-upload", json.dumps({"info": INFO, "files": files}).encode(), {"Content-Type": "application/json"})
check("prepare-upload accepted", st == 200, (st, body[:200]))
if st == 200:
    res = json.loads(body)
    sid = res["sessionId"]
    st2, _ = request("POST", "/api/localsend/v2/prepare-upload", json.dumps({"info": INFO, "files": files}).encode())
    check("second session blocked (409)", st2 == 409, st2)
    st3, _ = request("POST", f"/api/localsend/v2/upload?sessionId={sid}&fileId={fid}&token=wrong", data)
    check("wrong token -> 403", st3 == 403, st3)
    st4, _ = request("POST", f"/api/localsend/v2/upload?sessionId={sid}&fileId={fid}&token={res['files'][fid]}", data, chunked=True)
    check("chunked upload with sha256", st4 == 200, st4)
    st5, _ = request("POST", f"/api/localsend/v2/upload?sessionId={sid}&fileId=f2&token={res['files']['f2']}", b"abc")
    check("fixed-length upload", st5 == 200, st5)
    st6, b6 = request("POST", "/api/localsend/v2/prepare-upload", json.dumps({"info": INFO, "files": files}).encode(), {"Content-Type": "application/json"})
    check("after finish a new session is possible", st6 in (200, 403), st6)
    if st6 == 200:
        sid2 = json.loads(b6)["sessionId"]
        st7, _ = request("POST", f"/api/localsend/v2/cancel?sessionId={sid2}")
        check("cancel", st7 == 200, st7)
print("failures:", failures)
