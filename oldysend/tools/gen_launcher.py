"""Renders the OldySend launcher icons (one per interface style) and the notification icon into app/src/main/res (Pillow).

The disc, ring, paper plane and gloss repeat LogoDrawable with the colors each Kit passes to it, so the launcher icon
looks like the logo inside the app. Shadows follow the launcher icon guidelines of each era:
  classic  Android 2.0 icon guidelines: drop shadow #000 75 %, angle 90 deg, distance 2 px, size 5 px (at 48 px)
  holo     Android 4.x iconography: subtle bottom shadow
  md1      Material Design 2014 product icons: 1 dp tinted edge + soft shadow under the shape
  md3      flat (the adaptive icon in drawable-anydpi-v26 is used from Android 8.0)
"""
import math
import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter

RES = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
# Material "send" glyph (24x24): M2.01 21L23 12 2.01 3 2 10l15 2-15 2z
PLANE = [(2.01, 21), (23, 12), (2.01, 3), (2, 10), (17, 12), (2, 14)]

DENSITIES = (("drawable", 1), ("drawable-hdpi", 1.5), ("drawable-xhdpi", 2), ("drawable-xxhdpi", 3), ("drawable-xxxhdpi", 4))


def rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, 255)


STYLES = {
    # ring, fill (or gradient), glyph — the values of ClassicKit/HoloKit/MaterialKit.logo()
    "classic": dict(ring=0x5A5A5A, radial=(0xFFC94D, 0xE07B00), glyph=0xFFFFFF, gloss=True),
    "holo": dict(ring=0x0099CC, vertical=(0x5CC6EC, 0x1C9FD0), glyph=0xFFFFFF),
    "md1": dict(ring=0x00796B, fill=0x009688, glyph=0xFFFFFF),
    "md3": dict(ring=0x006A60, fill=0x9EF2E4, glyph=0x005048),
}


def plane(cx, cy, scale, angle):
    pts = []
    a = math.radians(angle)
    for x, y in PLANE:
        x -= 12.5
        y -= 12
        rx = x * math.cos(a) - y * math.sin(a)
        ry = x * math.sin(a) + y * math.cos(a)
        pts.append((cx + rx * scale, cy + ry * scale))
    return pts


def circle_mask(s, box):
    m = Image.new("L", (s, s), 0)
    ImageDraw.Draw(m).ellipse(box, fill=255)
    return m


def logo(size, st):
    """LogoDrawable.draw() at size x size."""
    s = size
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    r = s / 2
    d.ellipse([0, 0, s, s], fill=rgb(st["ring"]))
    inner = [r - r * 0.86, r - r * 0.86, r + r * 0.86, r + r * 0.86]
    if "radial" in st:
        c0, c1 = st["radial"]
        grad = Image.new("RGBA", (s, s), rgb(c1))
        gd = ImageDraw.Draw(grad)
        cx, cy, rad = s * 0.5, s * 0.3, s * 0.7
        steps = 96
        for i in range(steps):
            t = i / steps  # 0 = edge, 1 = center
            col = tuple(int(a + (b - a) * t) for a, b in zip(rgb(c1), rgb(c0)))
            k = rad * (1 - t)
            gd.ellipse([cx - k, cy - k, cx + k, cy + k], fill=col)
        img.paste(grad, (0, 0), circle_mask(s, inner))
    elif "vertical" in st:
        c0, c1 = st["vertical"]
        grad = Image.new("RGBA", (s, s))
        gd = ImageDraw.Draw(grad)
        for y in range(s):
            t = y / max(1, s - 1)
            gd.line([(0, y), (s, y)], fill=tuple(int(a + (b - a) * t) for a, b in zip(rgb(c0), rgb(c1))))
        img.paste(grad, (0, 0), circle_mask(s, inner))
    else:
        d.ellipse(inner, fill=rgb(st["fill"]))
    d = ImageDraw.Draw(img)
    d.polygon(plane(r + s * 0.02, r, s * 0.5 / 24, -25), fill=rgb(st["glyph"]))
    if st.get("gloss"):
        gloss = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        ImageDraw.Draw(gloss).ellipse([r - r * 0.66, r - r * 0.82, r + r * 0.66, r + r * 0.02], fill=(255, 255, 255, 0x40))
        img = Image.alpha_composite(img, gloss)
    return img


def shadow(shape, dy, blur, alpha):
    a = shape.split()[3].point(lambda v: v * alpha // 255)
    sh = Image.new("RGBA", shape.size, (0, 0, 0, 0))
    sh.putalpha(a)
    sh = sh.transform(shape.size, Image.AFFINE, (1, 0, 0, 0, 1, -dy))
    return sh.filter(ImageFilter.GaussianBlur(blur)) if blur > 0 else sh


def launcher(px, style):
    k = 4  # supersampling
    s = px * k
    unit = s / 48.0  # 1 px at mdpi
    pad = round(unit * (3 if style != "md3" else 2))
    disc = logo(s - 2 * pad, STYLES[style])
    shape = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    shape.paste(disc, (pad, pad))
    out = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    if style == "classic":
        out = Image.alpha_composite(out, shadow(shape, 2 * unit, 2.5 * unit, 191))
    elif style == "holo":
        out = Image.alpha_composite(out, shadow(shape, 1.5 * unit, 1.5 * unit, 110))
    elif style == "md1":
        out = Image.alpha_composite(out, shadow(shape, 1 * unit, 1.5 * unit, 70))
    out = Image.alpha_composite(out, shape)
    if style == "md1":
        # 1 dp tinted edge: lighter top, darker bottom of the shape (Material product icon "finish").
        a = shape.split()[3]
        top = ImageChops.subtract(a, a.transform(a.size, Image.AFFINE, (1, 0, 0, 0, 1, -unit)))
        bottom = ImageChops.subtract(a, a.transform(a.size, Image.AFFINE, (1, 0, 0, 0, 1, unit)))
        hi = Image.new("RGBA", (s, s), (255, 255, 255, 0))
        hi.putalpha(top.point(lambda v: v * 50 // 255))
        lo = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        lo.putalpha(bottom.point(lambda v: v * 50 // 255))
        out = Image.alpha_composite(Image.alpha_composite(out, hi), lo)
    return out.resize((px, px), Image.LANCZOS)


def status(px):
    s = px * 4
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(img).polygon(plane(s / 2, s / 2, s / 24 * 0.9, -25), fill=(255, 255, 255, 255))
    return img.resize((px, px), Image.LANCZOS)


for folder, mult in DENSITIES:
    path = os.path.join(RES, folder)
    os.makedirs(path, exist_ok=True)
    old = os.path.join(path, "ic_launcher.png")
    if os.path.exists(old):
        os.remove(old)  # ic_launcher is now an alias per Android version (values*/drawables.xml)
    for style in STYLES:
        launcher(int(48 * mult), style).save(os.path.join(path, "ic_launcher_%s.png" % style), optimize=True)
    status(int(24 * mult)).save(os.path.join(path, "ic_stat_oldysend.png"), optimize=True)
print("ok")
