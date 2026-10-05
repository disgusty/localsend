#!/usr/bin/env python3
"""Generate app/src/main/assets/verify_icons.txt.

Exactly 260 lines; line i is the flattened 24x24 absolute-command SVG path of the i-th icon of
LocalSend's fingerprint icon alphabet (app/lib/util/fingerprint_alphabet.dart, `iconAlphabet`).
Flutter's `Icons.xxx` are the filled Material Icons, so the M1 "materialicons" 24px variant is used;
names missing there fall back to Material Symbols (FILL 1, wght 400, opsz 24).

Reuses download/cache/flatten code from gen_icons.py.

Usage:  python tools/gen_verify_icons.py [--cache DIR] [--out FILE] [--dart FILE] [--check]
"""
import argparse
import concurrent.futures
import json
import math
import os
import re
import sys
import tempfile
import urllib.request
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_icons as gi  # noqa: E402

DEFAULT_OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "verify_icons.txt")
DEFAULT_DART = os.path.join(HERE, "..", "..", "app", "lib", "util", "fingerprint_alphabet.dart")
EXPECTED = 260

# Flutter Icons name -> Material Icons name where Flutter renamed the icon.
M1_NAME = {"door_front_door": "door_front"}
# Flutter Icons name -> Material Symbols name override (fallback only).
M3_NAME = {}

FONT_URL = gi.RAW + "font/MaterialIcons-Regular.ttf"
CODEPOINTS_URL = gi.RAW + "font/MaterialIcons-Regular.codepoints"

# ---------------------------------------------------------------- transforms

def parse_transform(t):
    """SVG transform attribute -> affine (a, b, c, d, e, f)."""
    m = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
    for fn, argstr in re.findall(r"(\w+)\s*\(([^)]*)\)", t):
        a = [float(v) for v in gi.NUM_RE.findall(argstr)]
        if fn == "matrix":
            n = tuple(a)
        elif fn == "translate":
            n = (1, 0, 0, 1, a[0], a[1] if len(a) > 1 else 0)
        elif fn == "scale":
            n = (a[0], 0, 0, a[1] if len(a) > 1 else a[0], 0, 0)
        elif fn == "rotate":
            r = math.radians(a[0])
            cs, sn = math.cos(r), math.sin(r)
            n = (cs, sn, -sn, cs, 0, 0)
            if len(a) == 3:
                n = mul(mul((1, 0, 0, 1, a[1], a[2]), n), (1, 0, 0, 1, -a[1], -a[2]))
        else:
            raise ValueError("unsupported transform " + fn)
        m = mul(m, n)
    return m


def mul(m, n):
    a, b, c, d, e, f = m
    A, B, C, D, E, F = n
    return (a * A + c * B, b * A + d * B, a * C + c * D, b * C + d * D, a * E + c * F + e, b * E + d * F + f)


def apply_affine(cmds, m):
    a, b, c, d, e, f = m

    def pt(x, y):
        return a * x + c * y + e, b * x + d * y + f

    det = a * d - b * c
    sc = math.sqrt(abs(det))
    rot = math.degrees(math.atan2(b, a))
    if abs(abs(a * a + b * b) - abs(c * c + d * d)) > 1e-2 or abs(a * c + b * d) > 1e-2:
        has_arc = any(k == "A" for k, _ in cmds)
        if has_arc:
            raise ValueError("non-similarity transform with arcs not supported")
    res = []
    cx = cy = sx = sy = 0.0
    for k, args in cmds:
        if k == "Z":
            res.append(("Z", []))
            cx, cy = sx, sy
            continue
        if k == "H":
            k, args = "L", [args[0], cy]
        elif k == "V":
            k, args = "L", [cx, args[0]]
        if k == "A":
            x, y = pt(args[5], args[6])
            res.append(("A", [args[0] * sc, args[1] * sc, args[2] + rot, args[3],
                              (1.0 - args[4]) if det < 0 else args[4], x, y]))
        else:
            out = []
            for i in range(0, len(args), 2):
                out.extend(pt(args[i], args[i + 1]))
            res.append((k, out))
        cx, cy = args[-2], args[-1]
        if k == "M":
            sx, sy = cx, cy
    return res


def svg_to_path(svg, issues):
    """Like gen_icons.svg_to_path, but applies `transform` attributes."""
    root = ET.fromstring(svg)
    vb = [float(v) for v in root.get("viewBox", "0 0 24 24").replace(",", " ").split()]
    s = 24.0 / vb[2]
    if abs(vb[3] - vb[2]) > 1e-6:
        issues.append("non-square viewBox %r" % vb)
    tx, ty = -vb[0] * s, -vb[1] * s
    cmds = []

    def walk(el, fill, ctm):
        tag = el.tag.split("}")[-1]
        if tag in ("defs", "clipPath", "mask", "title", "desc", "style"):
            if tag in ("clipPath", "mask", "style"):
                issues.append("ignored <%s>" % tag)
            return
        if el.get("transform"):
            ctm = mul(ctm, parse_transform(el.get("transform")))
        if el.get("clip-path") or el.get("mask"):
            issues.append("clip-path/mask on <%s> ignored" % tag)
        st = el.get("style") or ""
        m = re.search(r"fill\s*:\s*([^;]+)", st)
        fill = (m.group(1).strip() if m else el.get("fill", fill))
        d = gi.shape_to_d(el, tag)
        if d is not None:
            if fill == "none":
                if el.get("stroke") not in (None, "none"):
                    issues.append("stroke-only <%s> skipped" % tag)
                return
            for k in ("opacity", "fill-opacity"):
                if el.get(k) is not None or re.search(k + r"\s*:", st):
                    issues.append("%s on <%s> (merged as opaque)" % (k, tag))
            if d.strip():
                pc = gi.parse_path(d)
                if ctm != (1.0, 0.0, 0.0, 1.0, 0.0, 0.0):
                    pc = apply_affine(pc, ctm)
                cmds.extend(gi.transform_cmds(pc, s, tx, ty))
        elif tag not in ("svg", "g"):
            issues.append("unknown element <%s>" % tag)
        for ch in el:
            walk(ch, fill, ctm)

    walk(root, root.get("fill", "black"), (1.0, 0.0, 0.0, 1.0, 0.0, 0.0))
    return gi.serialize(cmds)

# ---------------------------------------------------------------- font glyph fallback

_font = {}


def font_glyph_path(name, cache):
    """Glyph of the Material Icons font (what Flutter renders) as a 24x24 path, or None."""
    if not _font:
        cps = {}
        for ln in gi.fetch(CODEPOINTS_URL, cache).splitlines():
            p = ln.split()
            if len(p) == 2:
                cps[p[0]] = int(p[1], 16)
        fn = os.path.join(cache, "font_MaterialIcons-Regular.ttf")
        if not os.path.exists(fn):
            with urllib.request.urlopen(FONT_URL, timeout=60) as r:
                data = r.read()
            with open(fn, "wb") as fo:
                fo.write(data)
        from fontTools.ttLib import TTFont  # pip install fonttools
        _font["cps"] = cps
        _font["font"] = TTFont(fn)
    cp = _font["cps"].get(name)
    if cp is None:
        return None
    font = _font["font"]
    g = font.getBestCmap().get(cp)
    if g is None:
        return None
    from fontTools.pens.svgPathPen import SVGPathPen
    from fontTools.pens.transformPen import TransformPen
    upm = font["head"].unitsPerEm
    asc = font["hhea"].ascent
    k = 24.0 / upm
    gs = font.getGlyphSet()
    pen = SVGPathPen(gs)
    gs[g].draw(TransformPen(pen, (k, 0, 0, -k, 0, asc * k)))
    d = pen.getCommands()
    return gi.serialize(gi.parse_path(d)) if d else None

# Embedded copy of the alphabet (order matters!). Cross-checked against the dart file when present.
ALPHABET = """
ac_unit accessibility agriculture alarm album anchor android apartment apple architecture attach_file
attach_money audiotrack back_hand backpack badge bakery_dining balance bathtub battery_full bento biotech
blender bluetooth bolt bookmark brush bug_report build cable cake calculate calendar_today campaign casino
cast castle celebration cell_tower chair change_history chat checkroom church circle cloud coffee_maker
colorize computer confirmation_number content_cut conveyor_belt cookie coronavirus cottage credit_card
cruelty_free cyclone delete diamond directions_bike directions_boat directions_bus directions_car
door_front_door earbuds eco edit egg elevator email emoji_events emoji_nature engineering explore extension
face factory fastfood favorite fence festival fingerprint fire_extinguisher fireplace fitness_center flag
flashlight_on flight flutter_dash forklift format_paint gavel grass groups handshake hardware headphones
healing hearing hexagon history_edu hive home hourglass_empty houseboat hub ice_skating icecream
inventory_2 iron kebab_dining key keyboard king_bed kitchen landscape layers light lightbulb link liquor
local_bar local_cafe local_drink local_florist local_gas_station local_laundry_service local_mall
local_pizza local_shipping local_taxi location_city lock luggage map markunread_mailbox masks medication
memory menu menu_book mic microwave military_tech monitor_heart mood mosque motorcycle mouse movie museum
newspaper nightlight notifications oil_barrel outdoor_grill outlet palette park pentagon person pets
phishing phone photo_camera piano pie_chart place pool power precision_manufacturing print propane_tank
psychology public push_pin qr_code radar radio ramen_dining receipt recycling redeem refresh restaurant
rocket room_service route router sailing satellite_alt savings school science search settings shelves
shield shopping_basket shopping_cart shower signpost sim_card smart_toy smartphone smoking_rooms soap
solar_power speaker sports_esports sports_football sports_golf sports_hockey sports_motorsports
sports_soccer sports_tennis square stadium stairs star store straighten stroller table_restaurant tag
temple_buddhist theater_comedy theaters thermostat thumb_up toggle_on toll tornado toys traffic train tune
umbrella usb vaccines videocam view_in_ar visibility voicemail volcano wallet warehouse warning watch
water_drop waves wb_sunny weekend whatshot wifi wind_power window wine_bar work
""".split()


def dart_names(path):
    src = open(path, encoding="utf-8").read()
    m = re.search(r"iconAlphabet\s*=\s*<IconData>\[(.*?)\];", src, re.S)
    if not m:
        raise SystemExit("iconAlphabet not found in " + path)
    return re.findall(r"Icons\.(\w+)", m.group(1))


def check(path):
    data = open(path, "rb").read()
    bad = []
    if b"\r" in data:
        bad.append("CR found")
    text = data.decode("utf-8")
    if not text.endswith("\n"):
        bad.append("no trailing LF")
    lines = text[:-1].split("\n") if text.endswith("\n") else text.split("\n")
    if len(lines) != EXPECTED:
        bad.append("line count %d != %d" % (len(lines), EXPECTED))
    for i, d in enumerate(lines):
        if not d.strip():
            bad.append("line %d empty" % (i + 1))
            continue
        if re.search(r"[^MLHVCSQTAZ0-9.\- ]", d):
            bad.append("line %d has unexpected chars" % (i + 1))
        try:
            cmds = gi.parse_path(d)
            x0, y0, x1, y1 = gi.bbox(d)
        except Exception as e:  # noqa
            bad.append("line %d parse fail: %s" % (i + 1, e))
            continue
        if not cmds or cmds[0][0] != "M":
            bad.append("line %d does not start with M" % (i + 1))
        if x0 < -0.5 or y0 < -0.5 or x1 > 24.5 or y1 > 24.5:
            bad.append("line %d bbox out of range %r" % (i + 1, (x0, y0, x1, y1)))
    print("checked %d lines, %d bytes, %d problems" % (len(lines), len(data), len(bad)))
    for b in bad:
        print("  PROBLEM:", b)
    return 1 if bad else 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", default=os.path.join(tempfile.gettempdir(), "oldysend_icon_cache"))
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--dart", default=DEFAULT_DART)
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    if args.check:
        return check(args.out)

    names = ALPHABET
    if os.path.exists(args.dart):
        dn = dart_names(args.dart)
        if dn != names:
            raise SystemExit("embedded ALPHABET differs from %s" % args.dart)
        print("alphabet matches", os.path.abspath(args.dart))
    else:
        print("WARNING: dart file not found, using embedded alphabet")
    if len(names) != EXPECTED or len(set(names)) != EXPECTED:
        raise SystemExit("alphabet must have %d unique names, has %d" % (EXPECTED, len(names)))

    os.makedirs(args.cache, exist_ok=True)
    cv = json.loads(gi.fetch(gi.RAW + "update/current_versions.json", args.cache))
    cats = {}
    for key in cv:
        cat, name = key.split("::", 1)
        if cat != "symbols" and name not in cats:
            cats[name] = cat

    jobs = {}
    for n in names:
        u = gi.m1_url(cats, M1_NAME.get(n, n))
        if u:
            jobs[u] = None
    with concurrent.futures.ThreadPoolExecutor(16) as ex:
        futs = {ex.submit(gi.fetch, u, args.cache): u for u in jobs}
        for fu in concurrent.futures.as_completed(futs):
            jobs[futs[fu]] = fu.result()

    # Order: 1) Material Icons SVG (materialicons/24px), 2) glyph of the Material Icons font
    # (the exact font Flutter's Icons use), 3) Material Symbols FILL 1 SVG.
    out, fallbacks, missing, issues = [], [], [], []
    for n in names:
        m1 = M1_NAME.get(n, n)
        u = gi.m1_url(cats, m1)
        svg = jobs.get(u) if u else None
        d = None
        if svg:
            iss = []
            d = svg_to_path(svg, iss)
            issues.extend("%s: %s" % (n, i) for i in iss)
            if m1 != n:
                fallbacks.append("%s <- materialicons/%s (renamed in Flutter)" % (n, m1))
        if not d:
            d = font_glyph_path(m1, args.cache)
            if d:
                fallbacks.append("%s <- MaterialIcons-Regular.ttf glyph '%s'" % (n, m1))
        if not d:
            m3 = M3_NAME.get(n, n)
            s3 = gi.fetch(gi.m3_url(m3, True), args.cache)
            if s3:
                iss = []
                d = svg_to_path(s3, iss)
                issues.extend("%s: %s" % (n, i) for i in iss)
                fallbacks.append("%s <- symbols/%s (fill1)" % (n, m3))
        if not d or not d.startswith("M"):
            missing.append(n)
            d = ""
        out.append(d)

    print("fallbacks (%d):" % len(fallbacks))
    for f_ in fallbacks:
        print("  " + f_)
    for i in issues:
        print("issue:", i)
    if missing:
        raise SystemExit("FAILED, missing icons: " + ", ".join(missing))

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as fo:
        fo.write("\n".join(out) + "\n")
    print("first 5:", names[:5])
    print("last 5:", names[-5:])
    return check(args.out)


if __name__ == "__main__":
    sys.exit(main())
