"""Screenshots of every style x tab on the connected device, combined into one sheet.

usage: python tools/shots.py OUT.png [styles] [brightness] [extra-activity]
"""
import os
import subprocess
import sys
import tempfile
import time

from PIL import Image

ADB = os.path.join(os.environ["LOCALAPPDATA"], "Android", "Sdk", "platform-tools", "adb.exe")
PKG = "com.disgusty.oldysend"


def adb(*args, binary=False):
    return subprocess.run([ADB] + list(args), capture_output=True, check=False).stdout


def stop():
    adb("shell", "am", "force-stop", PKG)
    # Android 2.x has no "am force-stop": kill the process as the app user.
    for line in adb("shell", "ps").decode(errors="ignore").splitlines():
        if line.strip().rstrip("\r").endswith(PKG):
            pid = line.split()[1]
            adb("shell", f"run-as {PKG} kill -9 {pid}")
    time.sleep(0.5)


def set_prefs(style, brightness, extra=""):
    xml = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map>"
           f"<string name=\"style\">{style}</string><string name=\"brightness\">{brightness}</string>"
           "<string name=\"alias\">Retro Banana</string><string name=\"httpFingerprint\">test-fp</string>"
           "<boolean name=\"advancedSettings\" value=\"true\" />" + extra + "</map>")
    stop()
    tmp = os.path.join(tempfile.gettempdir(), "settings.xml")
    open(tmp, "w").write(xml)
    adb("push", tmp, "/data/local/tmp/settings.xml")
    adb("shell", f"run-as {PKG} sh -c 'cd /data/data/{PKG} ; mkdir shared_prefs; cat /data/local/tmp/settings.xml > shared_prefs/settings.xml'")


def shot(name):
    # The emulator console works on every Android version (screencap only exists since 4.0).
    out = os.path.join(tempfile.gettempdir(), name + ".png")
    if os.path.exists(out):
        os.remove(out)
    adb("emu", "screenrecord", "screenshot", out)
    return Image.open(out)


def main():
    out = sys.argv[1]
    styles = sys.argv[2].split(",") if len(sys.argv) > 2 else ["classic", "holo", "md1", "md3"]
    brightness = sys.argv[3] if len(sys.argv) > 3 else "system"
    extra = sys.argv[4] if len(sys.argv) > 4 else None
    if int(adb("shell", "getprop", "ro.build.version.sdk").strip() or 0) >= 14:
        adb("shell", "input", "keyevent", "82")
    rows = []
    for st in styles:
        set_prefs(st, brightness)
        imgs = []
        targets = [("tab", t) for t in (0, 1, 2)] + ([("act", extra)] if extra else [])
        for kind, t in targets:
            stop()
            if kind == "tab":
                adb("shell", "am", "start", "-n", PKG + "/.ui.MainActivity", "--ei", "tab", str(t))
            else:
                adb("shell", "am", "start", "-n", PKG + "/.ui." + t)
            time.sleep(3.5)
            imgs.append(shot(f"{st}_{t}"))
        rows.append(imgs)
    w, h = rows[0][0].size
    scale = 360 / w
    tw, th = int(w * scale), int(h * scale)
    cols = max(len(r) for r in rows)
    sheet = Image.new("RGB", (tw * cols, th * len(rows)), "white")
    for y, r in enumerate(rows):
        for x, im in enumerate(r):
            sheet.paste(im.convert("RGB").resize((tw, th), Image.LANCZOS), (x * tw, y * th))
    sheet.save(out)
    print(out, sheet.size)


if __name__ == "__main__":
    main()
