"""Screenshot of the incoming-request page per style. usage: python tools/request_shot.py OUT.png styles"""
import json, socket, sys, threading, time
sys.path.insert(0, __import__("os").path.dirname(__file__))
import shots
from PIL import Image

out, styles = sys.argv[1], sys.argv[2].split(",")
files = {f"f{i}": {"id": f"f{i}", "fileName": n, "size": s, "fileType": t} for i, (n, s, t) in enumerate(
    [("Holiday.jpg", 2400000, "image/jpeg"), ("Report.pdf", 340000, "application/pdf"), ("song.mp3", 5100000, "audio/mpeg")])}
body = json.dumps({"info": {"alias": "Mighty Pumpkin", "version": "2.2", "deviceModel": "Windows", "deviceType": "desktop",
                            "fingerprint": "pc", "port": 53317, "protocol": "https"}, "files": files}).encode()
imgs = []
shots.adb("forward", "tcp:53317", "tcp:53317")
for st in styles:
    shots.set_prefs(st, "system")
    shots.adb("shell", "am", "start", "-n", shots.PKG + "/.ui.MainActivity")
    time.sleep(4)
    s = socket.create_connection(("127.0.0.1", 53317))
    if int(shots.adb("shell", "getprop", "ro.build.version.sdk").strip() or 0) >= 21:
        import ssl
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        s = ctx.wrap_socket(s)
    s.sendall(b"POST /api/localsend/v2/prepare-upload HTTP/1.1\r\nHost: x\r\nContent-Length: %d\r\n\r\n" % len(body) + body)
    time.sleep(3)
    imgs.append(shots.shot("req_" + st))
    s.close()
    time.sleep(2)
w, h = imgs[0].size
tw, th = 360, int(h * 360 / w)
sheet = Image.new("RGB", (tw * len(imgs), th), "white")
for i, im in enumerate(imgs):
    sheet.paste(im.convert("RGB").resize((tw, th)), (i * tw, 0))
sheet.save(out)
print(out)
