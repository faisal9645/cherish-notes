"""
The app logo: a white note card, a light blue band at its top and three blue lines, with a sky
blue pencil writing on it, on the app's blue gradient (cyan, royal blue, violet blue).

Writes, from one set of shapes:
- res/drawable/ic_launcher_background.xml   adaptive icon background (the gradient)
- res/drawable/ic_launcher_foreground.xml   adaptive icon foreground (card and pencil)
- res/drawable/ic_launcher_monochrome.xml   themed icon (Android 13+): one colour
- res/drawable/splash_icon.xml              Android 12+ splash: the logo kept inside the visible
                                            192dp circle, transparent around it, so it suits the
                                            white day splash and the dark night splash alike
- res/mipmap-anydpi-v26/ic_launcher*.xml    the adaptive icon using the drawables above
- res/mipmap-*/ic_launcher.png, ic_launcher_round.png   Android 7 (before adaptive icons)

Usage: python scripts/make_logo.py
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

RES = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res"))

# The app's blues, top left to bottom right
GRADIENT = ["#1FC8FF", "#2563FF", "#3B1FF5"]
CARD_COLOR = "#FFFFFF"
STRIP_COLOR = "#D6E4FF"
LINE_COLOR = "#2F5BFF"
SHADOW_COLOR = "#0B1A6B"
SHADOW_ALPHA = 0.22

# Shapes in the adaptive icon's 108 x 108 space (visible 18..90; round masks keep a circle of
# radius 33 around 54,54, and everything stays inside it)
CARD = (33.0, 29.0, 69.0, 73.0)   # left, top, right, bottom
CARD_RADIUS = 6.0
STRIP_BOTTOM = 37.0
LINES = [(39.0, 43.0, 63.0), (39.0, 51.0, 59.0), (39.0, 59.0, 52.0)]  # left, top, right
LINE_HEIGHT = 3.5
CARD_SHADOW_OFFSET = (1.0, 2.0)

# The pencil, along its own axis (u: 0 is the tip, v: across) and placed by its tip
PENCIL_TIP = (55.0, 67.0)
PENCIL_ANGLE = -45.0  # up and to the right
PENCIL_SHADOW_OFFSET = 1.3  # across the pencil
HALF = 3.5


def _eraser():
    """The eraser with a rounded end (a half circle around u = 30)."""
    points = [(28.0, -HALF), (30.0, -HALF)]
    for i in range(1, 12):
        a = -math.pi / 2 + math.pi * i / 12
        points.append((30.0 + HALF * math.cos(a), HALF * math.sin(a)))
    points += [(30.0, HALF), (28.0, HALF)]
    return points


PENCIL_PARTS = [
    # colour, outline (u, v)
    ("#DDE9FF", [(0.0, 0.0), (8.0, -HALF), (8.0, HALF)]),                     # wood
    ("#1B2A8F", [(0.0, 0.0), (3.2, -1.15), (3.2, 1.15)]),                     # graphite
    ("#7CC4FF", [(8.0, -HALF), (25.0, -HALF), (25.0, HALF), (8.0, HALF)]),    # body
    ("#A8DCFF", [(8.0, -HALF), (25.0, -HALF), (25.0, -1.3), (8.0, -1.3)]),    # its light side
    ("#4F9DFF", [(8.0, 1.3), (25.0, 1.3), (25.0, HALF), (8.0, HALF)]),        # its shaded side
    ("#C9D6EE", [(25.0, -HALF), (28.0, -HALF), (28.0, HALF), (25.0, HALF)]),  # metal band
    ("#FFFFFF", _eraser()),                                                    # eraser
]
PENCIL_OUTLINE = [(0.0, 0.0), (8.0, -HALF)] + _eraser()[1:-1] + [(8.0, HALF)]


def place(u, v, shift=0.0):
    """A point on the pencil in icon space ([shift] moves it across the pencil, for the shadow)."""
    a = math.radians(PENCIL_ANGLE)
    v += shift
    return (PENCIL_TIP[0] + u * math.cos(a) - v * math.sin(a),
            PENCIL_TIP[1] + u * math.sin(a) + v * math.cos(a))


def n(value):
    return f"{value:.2f}".rstrip("0").rstrip(".")


def rounded_rect_path(left, top, right, bottom, r):
    return (f"M{n(left + r)},{n(top)} H{n(right - r)} A{n(r)},{n(r)} 0 0 1 {n(right)},{n(top + r)} "
            f"V{n(bottom - r)} A{n(r)},{n(r)} 0 0 1 {n(right - r)},{n(bottom)} H{n(left + r)} "
            f"A{n(r)},{n(r)} 0 0 1 {n(left)},{n(bottom - r)} V{n(top + r)} A{n(r)},{n(r)} 0 0 1 {n(left + r)},{n(top)} Z")


def polygon_path(points):
    first, *rest = points
    return f"M{n(first[0])},{n(first[1])} " + " ".join(f"L{n(x)},{n(y)}" for x, y in rest) + " Z"


def card_parts():
    """(colour, alpha, path) for the card, back to front."""
    l, t, r, b = CARD
    dx, dy = CARD_SHADOW_OFFSET
    strip = (f"M{n(l)},{n(STRIP_BOTTOM)} V{n(t + CARD_RADIUS)} A{n(CARD_RADIUS)},{n(CARD_RADIUS)} 0 0 1 "
             f"{n(l + CARD_RADIUS)},{n(t)} H{n(r - CARD_RADIUS)} A{n(CARD_RADIUS)},{n(CARD_RADIUS)} 0 0 1 "
             f"{n(r)},{n(t + CARD_RADIUS)} V{n(STRIP_BOTTOM)} Z")
    lines = " ".join(rounded_rect_path(x0, y0, x1, y0 + LINE_HEIGHT, LINE_HEIGHT / 2) for x0, y0, x1 in LINES)
    return [
        (SHADOW_COLOR, SHADOW_ALPHA, rounded_rect_path(l + dx, t + dy, r + dx, b + dy, CARD_RADIUS)),
        (CARD_COLOR, 1.0, rounded_rect_path(l, t, r, b, CARD_RADIUS)),
        (STRIP_COLOR, 1.0, strip),
        (LINE_COLOR, 1.0, lines),
    ]


def pencil_parts():
    shadow = polygon_path([place(u, v, PENCIL_SHADOW_OFFSET) for u, v in PENCIL_OUTLINE])
    parts = [(SHADOW_COLOR, SHADOW_ALPHA, shadow)]
    for color, outline in PENCIL_PARTS:
        parts.append((color, 1.0, polygon_path([place(u, v) for u, v in outline])))
    return parts


def path_xml(color, alpha, data, indent="    "):
    alpha_attr = f'\n{indent}    android:fillAlpha="{n(alpha)}"' if alpha < 1.0 else ""
    return (f'{indent}<path\n{indent}    android:fillColor="{color}"{alpha_attr}\n'
            f'{indent}    android:pathData="{data}" />\n')


def gradient_xml(path_data, x0, y0, x1, y1, indent="    "):
    return (f'{indent}<path android:pathData="{path_data}">\n'
            f'{indent}    <aapt:attr name="android:fillColor">\n'
            f'{indent}        <gradient\n'
            f'{indent}            android:type="linear"\n'
            f'{indent}            android:startX="{n(x0)}"\n'
            f'{indent}            android:startY="{n(y0)}"\n'
            f'{indent}            android:endX="{n(x1)}"\n'
            f'{indent}            android:endY="{n(y1)}"\n'
            f'{indent}            android:startColor="#FF{GRADIENT[0][1:]}"\n'
            f'{indent}            android:centerColor="#FF{GRADIENT[1][1:]}"\n'
            f'{indent}            android:endColor="#FF{GRADIENT[2][1:]}" />\n'
            f'{indent}    </aapt:attr>\n'
            f'{indent}</path>\n')


def vector(size_dp, viewport, body, comment, aapt=False):
    ns = '\n    xmlns:aapt="http://schemas.android.com/aapt"' if aapt else ""
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<!-- {comment} Made by scripts/make_logo.py. -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"{ns}\n'
            f'    android:width="{size_dp}dp"\n    android:height="{size_dp}dp"\n'
            f'    android:viewportWidth="{viewport}"\n    android:viewportHeight="{viewport}">\n'
            f'{body}</vector>\n')


def write(rel, text):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = text.encode("utf-8")
    with open(path, "wb") as f:
        f.write(data)
    print("wrote", rel)


def write_vectors():
    glyph = "".join(path_xml(c, a, d) for c, a, d in card_parts() + pencil_parts())
    write("drawable/ic_launcher_background.xml", vector(
        108, 108, gradient_xml("M0,0h108v108h-108z", 18, 18, 90, 90),
        "App icon background: the app's blue gradient.", aapt=True))
    write("drawable/ic_launcher_foreground.xml", vector(
        108, 108, glyph, "App icon: a note card with a pencil."))

    # Themed icon: the card with its lines cut out, and the pencil cut out of the card where it
    # lies on it (even-odd), all in one colour
    l, t, r, b = CARD
    mono = " ".join([rounded_rect_path(l, t, r, b, CARD_RADIUS)] +
                    [rounded_rect_path(x0, y0, x1, y0 + LINE_HEIGHT, LINE_HEIGHT / 2) for x0, y0, x1 in LINES] +
                    [polygon_path([place(u, v) for u, v in PENCIL_OUTLINE])])
    write("drawable/ic_launcher_monochrome.xml", vector(
        108, 108,
        f'    <path\n        android:fillColor="#FFFFFFFF"\n        android:fillType="evenOdd"\n'
        f'        android:pathData="{mono}" />\n',
        "Themed app icon (Android 13+): one colour."))

    # Splash: the logo as a rounded square, 148 wide on the 288 canvas, so even its corners stay
    # inside the 192dp circle Android 12+ shows; the glyph scaled from the icon's visible 72
    side, corner = 148.0, 38.0
    lo = (288 - side) / 2
    hi = lo + side
    scale = side / 72.0
    shift = lo - 18 * scale
    glyph_scaled = "".join(path_xml(c, a, d, indent="        ") for c, a, d in card_parts() + pencil_parts())
    splash = (gradient_xml(rounded_rect_path(lo, lo, hi, hi, corner), lo, lo, hi, hi) +
              f'    <group\n        android:scaleX="{n(scale)}"\n        android:scaleY="{n(scale)}"\n'
              f'        android:translateX="{n(shift)}"\n        android:translateY="{n(shift)}">\n'
              f'{glyph_scaled}    </group>\n')
    write("drawable/splash_icon.xml", vector(
        288, 288, splash,
        "Android 12+ splash: the logo inside the visible circle, transparent around it (day and night).",
        aapt=True))

    adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@drawable/ic_launcher_background" />\n'
                '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
                '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
                '</adaptive-icon>\n')
    write("mipmap-anydpi-v26/ic_launcher.xml", adaptive)
    write("mipmap-anydpi-v26/ic_launcher_round.xml", adaptive)


# ---------- PNG icons for Android 7 ----------

def hex_rgb(value):
    return tuple(int(value[i:i + 2], 16) for i in (1, 3, 5))


def render_png(size, round_icon):
    ss = 4
    canvas = size * ss
    pad = canvas * 0.04
    inner = canvas - 2 * pad
    k = inner / 72.0

    def X(x):
        return pad + (x - 18) * k

    def Y(y):
        return pad + (y - 18) * k

    # Diagonal three-colour gradient over the visible square
    small = Image.new("L", (256, 256))
    small.putdata([min(255, (i + j) // 2) for j in range(256) for i in range(256)])
    ramp = small.resize((int(inner), int(inner)), Image.Resampling.BILINEAR)
    stops = [hex_rgb(c) for c in GRADIENT]

    def channel(ch):
        lut = []
        for v in range(256):
            t = v / 255
            a, b, f = (stops[0], stops[1], t / 0.5) if t < 0.5 else (stops[1], stops[2], (t - 0.5) / 0.5)
            lut.append(round(a[ch] + (b[ch] - a[ch]) * f))
        return ramp.point(lut)

    gradient = Image.merge("RGB", [channel(0), channel(1), channel(2)])
    img = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    mask = Image.new("L", (canvas, canvas), 0)
    md = ImageDraw.Draw(mask)
    box = (pad, pad, canvas - pad, canvas - pad)
    if round_icon:
        md.ellipse(box, fill=255)
    else:
        md.rounded_rectangle(box, radius=inner * 0.225, fill=255)
    layer = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    layer.paste(gradient, (int(pad), int(pad)))
    img.paste(layer, (0, 0), mask)

    def shadow(draw_fn):
        sh = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
        draw_fn(ImageDraw.Draw(sh), hex_rgb(SHADOW_COLOR) + (round(255 * SHADOW_ALPHA),))
        return sh.filter(ImageFilter.GaussianBlur(k * 0.8))

    l, t, r, b = CARD
    dx, dy = CARD_SHADOW_OFFSET
    img.alpha_composite(shadow(lambda d, c: d.rounded_rectangle(
        (X(l + dx), Y(t + dy), X(r + dx), Y(b + dy)), radius=CARD_RADIUS * k, fill=c)))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((X(l), Y(t), X(r), Y(b)), radius=CARD_RADIUS * k, fill=CARD_COLOR)
    d.rounded_rectangle((X(l), Y(t), X(r), Y(STRIP_BOTTOM + CARD_RADIUS)), radius=CARD_RADIUS * k, fill=STRIP_COLOR)
    d.rectangle((X(l), Y(STRIP_BOTTOM), X(r), Y(STRIP_BOTTOM + CARD_RADIUS)), fill=CARD_COLOR)
    for x0, y0, x1 in LINES:
        d.rounded_rectangle((X(x0), Y(y0), X(x1), Y(y0 + LINE_HEIGHT)), radius=LINE_HEIGHT / 2 * k, fill=LINE_COLOR)

    img.alpha_composite(shadow(lambda dd, c: dd.polygon(
        [(X(px), Y(py)) for px, py in (place(u, v, PENCIL_SHADOW_OFFSET) for u, v in PENCIL_OUTLINE)], fill=c)))
    d = ImageDraw.Draw(img)
    for color, outline in PENCIL_PARTS:
        d.polygon([(X(px), Y(py)) for px, py in (place(u, v) for u, v in outline)], fill=color)

    # Clip everything to the icon shape
    clipped = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    clipped.paste(img, (0, 0), mask)
    return clipped.resize((size, size), Image.Resampling.LANCZOS)


def write_pngs():
    for density, size in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        folder = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        render_png(size, round_icon=False).save(os.path.join(folder, "ic_launcher.png"))
        render_png(size, round_icon=True).save(os.path.join(folder, "ic_launcher_round.png"))
        print("wrote", f"mipmap-{density}/ic_launcher.png, ic_launcher_round.png")


if __name__ == "__main__":
    write_vectors()
    write_pngs()
    # A large preview to look at (not part of the app)
    preview = os.environ.get("LOGO_PREVIEW")
    if preview:
        render_png(512, round_icon=False).save(preview)
        print("preview", preview)
