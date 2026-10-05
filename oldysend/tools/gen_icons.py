#!/usr/bin/env python3
"""Generate app/src/main/java/com/disgusty/oldysend/ui/icon/IconData.java.

Downloads Material Icons (filled, "M1") and Material Symbols (Outlined, wght 400,
GRAD 0, opsz 24, FILL 0/1, "M3") SVGs from github.com/google/material-design-icons
(Apache License 2.0), flattens every visible shape of each SVG into a single path,
normalises it to a 24x24 viewport with origin top-left and rewrites it using only
ABSOLUTE commands (M L H V C S Q T A Z), numbers rounded to 3 decimals.

Usage:  python tools/gen_icons.py [--cache DIR] [--out FILE] [--check]
"""
import argparse
import concurrent.futures
import json
import math
import os
import re
import sys
import tempfile
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

RAW = "https://raw.githubusercontent.com/google/material-design-icons/master/"
HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_OUT = os.path.join(HERE, "..", "app", "src", "main", "java", "org", "oldysend", "ui", "icon", "IconData.java")

# (constant name, M1 icon name, M3 icon name, M3 mode)
#   M3 mode: "both" -> M3_X (fill 0) + M3_X_FILLED (fill 1); "outline" -> only M3_X (fill 0)
ICONS = [
    ("send", None, None, "both"), ("wifi",), ("settings",), ("history",), ("info",), ("help",),
    ("close",), ("check",), ("add",), ("remove",), ("delete",), ("edit",), ("content_copy",),
    ("content_paste",), ("folder",), ("folder_open",), ("create_new_folder",),
    ("insert_drive_file", None, "draft"), ("description",), ("image",), ("movie",),
    ("audiotrack", None, "music_note"), ("subject", None, "notes"), ("text_snippet",), ("apps",),
    ("android",), ("attach_file",), ("refresh",), ("search",), ("star",),
    ("star_border", None, "star", "outline"), ("favorite",),
    ("favorite_border", None, "favorite", "outline"), ("more_vert",), ("arrow_back",),
    ("arrow_forward",), ("link",), ("qr_code",), ("devices",), ("smartphone",), ("computer",),
    ("language",), ("terminal",), ("dns",), ("lock",), ("lock_open",), ("password",),
    ("open_in_new",), ("download", "file_download", "download"), ("upload", "file_upload", "upload"),
    ("check_circle",), ("error",), ("cancel",), ("pause",), ("play_arrow",), ("visibility",),
    ("palette",), ("dark_mode",), ("light_mode",), ("brightness_6",), ("translate",),
    ("restart_alt",), ("stop",), ("timer",), ("router",), ("verified_user",),
    ("flash_on", None, "bolt"), ("notifications",), ("photo_library",), ("share",), ("clear_all",),
    ("select_all",), ("chevron_right",), ("expand_more",), ("expand_less",), ("arrow_drop_down",),
    ("tune",), ("phone_android",), ("tablet",), ("laptop",), ("desktop_windows",), ("cloud",),
    ("wifi_off",), ("sync",), ("bug_report",), ("code",), ("archive",), ("picture_as_pdf",),
    ("contact_page",), ("menu",), ("done_all",), ("warning",), ("keyboard",), ("title",),
    ("format_paint",), ("color_lens",), ("save",), ("save_alt",), ("radio_button_checked",),
    ("radio_button_unchecked",), ("check_box",), ("check_box_outline_blank",), ("schedule",),
    ("speed",), ("sd_storage",), ("storage",), ("security",), ("qr_code_scanner",), ("wallpaper",),
    ("notes",), ("inbox",), ("outbox",), ("file_present",),
]

# Fallbacks tried (in order) when the requested name does not exist in a set.
M1_FALLBACK = {}
M3_FALLBACK = {
    "save_alt": ["download"],
    "color_lens": ["palette"],
    "phone_android": ["smartphone"],
    "tablet": ["tablet_android"],
    "desktop_windows": ["computer"],
    "outbox": ["outbox_alt"],
    "brightness_6": ["contrast"],
    "laptop": ["laptop_chromebook", "computer"],  # symbols/laptop is deprecated (not published)
    "sd_storage": ["sd_card"],  # symbols/sd_storage is deprecated (not published)
}

# ---------------------------------------------------------------- download

def fetch(url, cache):
    fn = os.path.join(cache, re.sub(r"[^A-Za-z0-9_.-]", "_", url[len(RAW):]))
    if os.path.exists(fn):
        with open(fn, "rb") as f:
            data = f.read()
        return None if data == b"404" else data.decode("utf-8")
    for attempt in range(4):
        try:
            with urllib.request.urlopen(url, timeout=30) as r:
                data = r.read()
            break
        except urllib.error.HTTPError as e:
            if e.code == 404:
                data = b"404"
                break
            if attempt == 3:
                raise
        except Exception:
            if attempt == 3:
                raise
    with open(fn, "wb") as f:
        f.write(data)
    return None if data == b"404" else data.decode("utf-8")


def m1_url(cats, name):
    c = cats.get(name)
    return None if c is None else RAW + "src/%s/%s/materialicons/24px.svg" % (c, name)


def m3_url(name, fill):
    return RAW + "symbols/web/%s/materialsymbolsoutlined/%s%s_24px.svg" % (name, name, "_fill1" if fill else "")

# ---------------------------------------------------------------- path parsing

NUM_RE = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
ARGC = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}


class PathReader:
    def __init__(self, d):
        self.d = d
        self.i = 0

    def skip(self):
        d = self.d
        while self.i < len(d) and d[self.i] in " \t\r\n,":
            self.i += 1

    def at_number(self):
        self.skip()
        return self.i < len(self.d) and (self.d[self.i] in "+-." or self.d[self.i].isdigit())

    def number(self):
        self.skip()
        m = NUM_RE.match(self.d, self.i)
        if not m:
            raise ValueError("number expected at %d in %r" % (self.i, self.d[self.i:self.i + 20]))
        self.i = m.end()
        return float(m.group(0))

    def flag(self):
        self.skip()
        c = self.d[self.i]
        if c not in "01":
            raise ValueError("arc flag expected at %d" % self.i)
        self.i += 1
        return 1.0 if c == "1" else 0.0


def parse_path(d):
    """Return list of (CMD, [args]) with absolute coordinates; command letters upper case."""
    r = PathReader(d)
    out = []
    cx = cy = sx = sy = 0.0
    cmd = None
    while True:
        r.skip()
        if r.i >= len(d):
            break
        ch = d[r.i]
        if ch.isalpha():
            cmd = ch
            r.i += 1
        elif cmd is None:
            raise ValueError("path must start with a command: %r" % d[:20])
        elif cmd in "Zz":
            raise ValueError("numbers after Z")
        # else: implicit repetition of previous command
        up = cmd.upper()
        rel = cmd != up
        if up == "Z":
            out.append(("Z", []))
            cx, cy = sx, sy
            continue
        if up == "A":
            a = [r.number(), r.number(), r.number(), r.flag(), r.flag(), r.number(), r.number()]
        else:
            a = [r.number() for _ in range(ARGC[up])]
        if up == "H":
            x = a[0] + (cx if rel else 0)
            out.append(("H", [x]))
            cx = x
        elif up == "V":
            y = a[0] + (cy if rel else 0)
            out.append(("V", [y]))
            cy = y
        elif up == "A":
            if rel:
                a[5] += cx
                a[6] += cy
            out.append(("A", a))
            cx, cy = a[5], a[6]
        else:
            if rel:
                a = [v + (cx if k % 2 == 0 else cy) for k, v in enumerate(a)]
            out.append((up, a))
            cx, cy = a[-2], a[-1]
            if up == "M":
                sx, sy = cx, cy
                cmd = "l" if rel else "L"  # subsequent pairs are lineto
    return out


def transform_cmds(cmds, s, tx, ty):
    res = []
    for c, a in cmds:
        if c == "H":
            res.append((c, [a[0] * s + tx]))
        elif c == "V":
            res.append((c, [a[0] * s + ty]))
        elif c == "A":
            res.append((c, [a[0] * s, a[1] * s, a[2], a[3], a[4], a[5] * s + tx, a[6] * s + ty]))
        elif c == "Z":
            res.append((c, []))
        else:
            res.append((c, [v * s + (tx if k % 2 == 0 else ty) for k, v in enumerate(a)]))
    return res


def fmt(v):
    v = round(v, 3)
    if v == 0:
        return "0"
    s = ("%.3f" % v).rstrip("0").rstrip(".")
    if s.startswith("0."):
        s = s[1:]
    elif s.startswith("-0."):
        s = "-" + s[2:]
    return s


def serialize(cmds):
    out = []
    for c, a in cmds:
        if c == "A":
            parts = [fmt(a[0]), fmt(a[1]), fmt(a[2]), "1" if a[3] else "0", "1" if a[4] else "0", fmt(a[5]), fmt(a[6])]
        else:
            parts = [fmt(v) for v in a]
        s = c
        for k, p in enumerate(parts):
            if k == 0 or p.startswith("-"):
                s += p
            elif p.startswith(".") and "." in parts[k - 1] and not (c == "A" and k in (3, 4, 5)):
                s += p
            else:
                s += " " + p
        out.append(s)
    return "".join(out)

# ---------------------------------------------------------------- SVG shapes

def f(el, k, default=0.0):
    v = el.get(k)
    if v is None:
        return default
    return float(re.sub(r"px$", "", v.strip()))


def shape_to_d(el, tag):
    if tag == "path":
        return el.get("d", "")
    if tag == "circle":
        cx, cy, r = f(el, "cx"), f(el, "cy"), f(el, "r")
        return "M%r %rA%r %r 0 1 0 %r %rA%r %r 0 1 0 %r %rZ" % (cx - r, cy, r, r, cx + r, cy, r, r, cx - r, cy)
    if tag == "ellipse":
        cx, cy, rx, ry = f(el, "cx"), f(el, "cy"), f(el, "rx"), f(el, "ry")
        return "M%r %rA%r %r 0 1 0 %r %rA%r %r 0 1 0 %r %rZ" % (cx - rx, cy, rx, ry, cx + rx, cy, rx, ry, cx - rx, cy)
    if tag == "rect":
        x, y, w, h = f(el, "x"), f(el, "y"), f(el, "width"), f(el, "height")
        rx = el.get("rx")
        ry = el.get("ry")
        rx = float(rx) if rx is not None else (float(ry) if ry is not None else 0.0)
        ry = float(ry) if ry is not None else rx
        rx, ry = min(rx, w / 2), min(ry, h / 2)
        if rx <= 0 or ry <= 0:
            return "M%r %rH%rV%rH%rZ" % (x, y, x + w, y + h, x)
        return ("M%r %rH%rA%r %r 0 0 1 %r %rV%rA%r %r 0 0 1 %r %rH%rA%r %r 0 0 1 %r %rV%rA%r %r 0 0 1 %r %rZ"
                % (x + rx, y, x + w - rx, rx, ry, x + w, y + ry, y + h - ry, rx, ry, x + w - rx, y + h,
                   x + rx, rx, ry, x, y + h - ry, y + ry, rx, ry, x + rx, y))
    if tag in ("polygon", "polyline"):
        nums = [float(n) for n in NUM_RE.findall(el.get("points", ""))]
        pts = ["%r %r" % (nums[k], nums[k + 1]) for k in range(0, len(nums) - 1, 2)]
        return "M" + "L".join(pts) + "Z"
    return None


def svg_to_path(svg, issues):
    """Return (absolute normalised path string, has_opacity)."""
    root = ET.fromstring(svg)
    vb = [float(v) for v in root.get("viewBox", "0 0 24 24").replace(",", " ").split()]
    s = 24.0 / vb[2]
    if abs(vb[3] - vb[2]) > 1e-6:
        issues.append("non-square viewBox %r" % vb)
    tx, ty = -vb[0] * s, -vb[1] * s
    cmds = []
    opac = [False]

    def walk(el, fill, opacity):
        tag = el.tag.split("}")[-1]
        if tag in ("defs", "clipPath", "mask", "title", "desc", "style"):
            if tag in ("clipPath", "mask", "style"):
                issues.append("ignored <%s>" % tag)
            return
        if el.get("transform"):
            issues.append("transform=%s on <%s> (NOT applied)" % (el.get("transform"), tag))
        if el.get("clip-path") or el.get("mask"):
            issues.append("clip-path/mask on <%s> ignored" % tag)
        st = el.get("style") or ""
        m = re.search(r"fill\s*:\s*([^;]+)", st)
        fill = (m.group(1).strip() if m else el.get("fill", fill))
        o = opacity
        for k in ("opacity", "fill-opacity"):
            if el.get(k) is not None:
                o *= float(el.get(k))
            m2 = re.search(k + r"\s*:\s*([^;]+)", st)
            if m2:
                o *= float(m2.group(1))
        d = shape_to_d(el, tag)
        if d is not None:
            if fill == "none":
                if el.get("stroke") not in (None, "none"):
                    issues.append("stroke-only <%s> skipped" % tag)
                return
            if o < 1:
                opac[0] = True
            if d.strip():
                cmds.extend(transform_cmds(parse_path(d), s, tx, ty))
        elif tag not in ("svg", "g"):
            issues.append("unknown element <%s>" % tag)
        for ch in el:
            walk(ch, fill, o)

    walk(root, root.get("fill", "black"), 1.0)
    return serialize(cmds), opac[0]

# ---------------------------------------------------------------- bbox check

def arc_points(x1, y1, rx, ry, phi, fa, fs, x2, y2, n=16):
    if rx == 0 or ry == 0 or (x1 == x2 and y1 == y2):
        return [(x2, y2)]
    rx, ry = abs(rx), abs(ry)
    p = math.radians(phi)
    cp, sp = math.cos(p), math.sin(p)
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p, y1p = cp * dx + sp * dy, -sp * dx + cp * dy
    lam = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)
    if lam > 1:
        rx, ry = rx * math.sqrt(lam), ry * math.sqrt(lam)
    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    co = math.sqrt(max(0.0, num / den)) if den else 0.0
    if fa == fs:
        co = -co
    cxp, cyp = co * rx * y1p / ry, -co * ry * x1p / rx
    cx = cp * cxp - sp * cyp + (x1 + x2) / 2
    cy = sp * cxp + cp * cyp + (y1 + y2) / 2
    t1 = math.atan2((y1p - cyp) / ry, (x1p - cxp) / rx)
    t2 = math.atan2((-y1p - cyp) / ry, (-x1p - cxp) / rx)
    dt = t2 - t1
    if fs and dt < 0:
        dt += 2 * math.pi
    elif not fs and dt > 0:
        dt -= 2 * math.pi
    pts = []
    for k in range(1, n + 1):
        t = t1 + dt * k / n
        ex, ey = rx * math.cos(t), ry * math.sin(t)
        pts.append((cp * ex - sp * ey + cx, sp * ex + cp * ey + cy))
    return pts


def bbox(d):
    cmds = parse_path(d)
    xs, ys = [], []
    cx = cy = sx = sy = 0.0
    for c, a in cmds:
        if c == "Z":
            cx, cy = sx, sy
            continue
        if c == "H":
            cx = a[0]
        elif c == "V":
            cy = a[0]
        elif c == "A":
            for x, y in arc_points(cx, cy, a[0], a[1], a[2], a[3], a[4], a[5], a[6]):
                xs.append(x)
                ys.append(y)
            cx, cy = a[5], a[6]
        else:
            for k in range(0, len(a), 2):
                xs.append(a[k])
                ys.append(a[k + 1])
            cx, cy = a[-2], a[-1]
            if c == "M":
                sx, sy = cx, cy
        xs.append(cx)
        ys.append(cy)
    return min(xs), min(ys), max(xs), max(ys)

# ---------------------------------------------------------------- main

def java_str(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", default=os.path.join(tempfile.gettempdir(), "oldysend_icon_cache"))
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--check", action="store_true", help="only re-check the generated file")
    args = ap.parse_args()
    if args.check:
        return check(args.out)
    os.makedirs(args.cache, exist_ok=True)

    cv = json.loads(fetch(RAW + "update/current_versions.json", args.cache))
    cats = {}
    for key in cv:
        cat, name = key.split("::", 1)
        if cat != "symbols" and name not in cats:
            cats[name] = cat

    entries = []
    for e in ICONS:
        name = e[0]
        m1 = e[1] if len(e) > 1 and e[1] else name
        m3 = e[2] if len(e) > 2 and e[2] else name
        mode = e[3] if len(e) > 3 else "both"
        entries.append((name, m1, m3, mode))

    # Resolve + download everything in parallel.
    jobs = {}
    for name, m1, m3, mode in entries:
        for cand in [m1] + M1_FALLBACK.get(m1, []):
            u = m1_url(cats, cand)
            if u:
                jobs[u] = None
        for cand in [m3] + M3_FALLBACK.get(m3, []):
            jobs[m3_url(cand, False)] = None
            if mode == "both":
                jobs[m3_url(cand, True)] = None
    with concurrent.futures.ThreadPoolExecutor(16) as ex:
        futs = {ex.submit(fetch, u, args.cache): u for u in jobs}
        for fu in concurrent.futures.as_completed(futs):
            jobs[futs[fu]] = fu.result()

    consts = []  # (const name, value or ("ref", other))
    subs, missing, opacity_icons, issues_all = [], [], [], []

    def convert(label, svg):
        iss = []
        d, op = svg_to_path(svg, iss)
        if op:
            opacity_icons.append(label)
        for i in iss:
            issues_all.append("%s: %s" % (label, i))
        return d

    for name, m1, m3, mode in entries:
        N = name.upper()
        # M1
        got = None
        for cand in [m1] + M1_FALLBACK.get(m1, []):
            u = m1_url(cats, cand)
            if u and jobs.get(u):
                got = cand
                consts.append(("M1_" + N, convert("M1_" + N, jobs[u])))
                break
        if got is None:
            missing.append("M1_" + N)
        elif got != name:
            subs.append("M1_%s <- materialicons/%s" % (N, got))
        # M3
        got = None
        for cand in [m3] + M3_FALLBACK.get(m3, []):
            u0 = m3_url(cand, False)
            if jobs.get(u0) and (mode != "both" or jobs.get(m3_url(cand, True))):
                got = cand
                d0 = convert("M3_" + N, jobs[u0])
                consts.append(("M3_" + N, d0))
                if mode == "both":
                    d1 = convert("M3_%s_FILLED" % N, jobs[m3_url(cand, True)])
                    consts.append(("M3_%s_FILLED" % N, ("ref", "M3_" + N) if d1 == d0 else d1))
                break
        if got is None:
            missing.append("M3_" + N)
        elif got != name:
            subs.append("M3_%s <- symbols/%s%s" % (N, got, " (fill0 only)" if mode == "outline" else ""))

    lines = [
        "package com.disgusty.oldysend.ui.icon;",
        "",
        "/**",
        " * Material icon path data, all normalised to a 24x24 viewport (origin top-left) and written",
        " * with absolute commands only (M L H V C S Q T A Z). Parse with {@link SvgPath#parse(String)}.",
        " *",
        " * M1_*         Material Icons, filled style (src/<category>/<icon>/materialicons/24px.svg)",
        " * M3_*         Material Symbols Outlined, wght 400, GRAD 0, opsz 24, FILL 0",
        " * M3_*_FILLED  Material Symbols Outlined, wght 400, GRAD 0, opsz 24, FILL 1",
        " *",
        " * Source: https://github.com/google/material-design-icons (branch master).",
        " * License: Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0).",
        " *",
        " * GENERATED by tools/gen_icons.py - do not edit by hand.",
        " */",
        "public final class IconData {",
        "    private IconData() {",
        "    }",
        "",
    ]
    for cname, val in consts:
        if isinstance(val, tuple):
            lines.append("    public static final String %s = %s;" % (cname, val[1]))
        else:
            lines.append("    public static final String %s =" % cname)
            lines.append("            %s;" % java_str(val))
    lines.append("}")
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as fo:
        fo.write("\n".join(lines) + "\n")

    report = {
        "constants": [c for c, _ in consts],
        "substitutions": subs,
        "missing": missing,
        "opacity_lt_1": opacity_icons,
        "issues": issues_all,
    }
    print(json.dumps(report, indent=1))
    return check(args.out)


def check(path):
    src = open(path, encoding="utf-8").read()
    bad = 0
    n = 0
    for name, d in re.findall(r'public static final String (\w+) =\s*"([^"]*)";', src):
        n += 1
        try:
            x0, y0, x1, y1 = bbox(d)
        except Exception as e:  # noqa
            print("PARSE FAIL", name, e)
            bad += 1
            continue
        if x0 < -0.5 or y0 < -0.5 or x1 > 24.5 or y1 > 24.5:
            print("BBOX OUT OF RANGE", name, (x0, y0, x1, y1))
            bad += 1
    print("checked %d paths, %d problems" % (n, bad))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
