"""
Generates the Android 12+ splash logo (app/src/main/res/mipmap-*/splash_logo.png) from the
original notes app icon art (the same art as the launcher icon).

Android 12+ draws the splash icon as a 288dp square but only shows the central 192dp circle, so
anything outside that circle is cut off and a square logo ends up looking round. The rounded-square
logo is sized so even its corners stay inside the circle; the rest of the canvas is transparent, so
the same image works on the white (day) and #121212 (night) splash backgrounds.

Usage: python make_splash.py [path/to/1024px_icon_art.jpg]
"""
import math
import os
import sys

from PIL import Image, ImageDraw

DEFAULT_SRC = r"C:\Users\karth\.gemini\antigravity-ide\brain\7e5a5dc2-7047-4072-b7b4-5cd9fc34ffea\.user_uploaded\media_1790837725864.jpg"
RES_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "app", "src", "main", "res")

CANVAS_DP = 288          # whole splash icon area
VISIBLE_RADIUS_DP = 96   # only a 192dp circle of it is visible
SAFETY_DP = 4            # gap between the logo's farthest corner and the circle edge
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

# Rounded-square shape of the 1024px art, fitted from its edges: superellipse corners 324px wide
# with exponent 2.5, straight sides 1px in from the image border
CORNER_PX = 324
CORNER_EXPONENT = 2.5
SIDE_INSET_PX = 2.0      # 1px border + 1px so the cut stays clear of the white anti-aliasing fringe
COLOR_SAMPLE_INSET_PX = 5  # edge pixels take their color from this far inside the art
SUPERSAMPLE = 4


def logo_mask(size):
    """Anti-aliased mask of the logo's rounded-square shape."""
    big = size * SUPERSAMPLE
    e = SIDE_INSET_PX * SUPERSAMPLE
    c = CORNER_PX * SUPERSAMPLE
    steps = 400

    def corner(reverse):
        ts = [i / steps * math.pi / 2 for i in range(steps + 1)]
        pts = []
        for t in (reversed(ts) if reverse else ts):
            u = math.cos(t) ** (2 / CORNER_EXPONENT)
            v = math.sin(t) ** (2 / CORNER_EXPONENT)
            pts.append((e + c * (1 - u), e + c * (1 - v)))
        return pts

    polygon = (
        [(x, y) for x, y in corner(False)]                    # top-left: left side -> top side
        + [(big - x, y) for x, y in corner(True)]             # top-right: top side -> right side
        + [(big - x, big - y) for x, y in corner(False)]      # bottom-right: right side -> bottom
        + [(x, big - y) for x, y in corner(True)]             # bottom-left: bottom -> left side
    )
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).polygon(polygon, fill=255)
    return mask.resize((size, size), Image.Resampling.BOX)


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_SRC
    art = Image.open(src).convert("RGB")
    size = art.width

    # Scale the art up a hair so the pixels under the cut edge come from inside the logo,
    # not from the white-blended edge of the JPG
    grown = round(size * (size / 2) / (size / 2 - COLOR_SAMPLE_INSET_PX))
    crop = (grown - size) // 2
    art = art.resize((grown, grown), Image.Resampling.LANCZOS).crop((crop, crop, crop + size, crop + size))

    mask = logo_mask(size)
    logo = art.convert("RGBA")
    logo.putalpha(mask)

    # Farthest visible logo pixel from the centre, as a fraction of the logo width
    alpha = mask.load()
    reach = max(
        math.hypot(x + 0.5 - size / 2, y + 0.5 - size / 2)
        for y in range(size) for x in range(size) if alpha[x, y] > 0
    ) / size
    logo_dp = math.floor((VISIBLE_RADIUS_DP - SAFETY_DP) / reach)
    print(f"logo reach {reach:.4f} of its width -> logo {logo_dp}dp in a {CANVAS_DP}dp canvas")

    for density, scale in DENSITIES.items():
        canvas_px = round(CANVAS_DP * scale)
        logo_px = round(logo_dp * scale / 2) * 2  # same parity as the canvas, so it centres exactly
        resized = logo.resize((logo_px, logo_px), Image.Resampling.LANCZOS)
        canvas = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
        offset = (canvas_px - logo_px) // 2
        canvas.paste(resized, (offset, offset))  # straight copy: the canvas is fully transparent
        dest = os.path.join(RES_DIR, f"mipmap-{density}", "splash_logo.png")
        canvas.save(dest, optimize=True)
        print(f"Saved {dest} ({canvas_px}px, logo {logo_px}px)")


if __name__ == "__main__":
    main()
