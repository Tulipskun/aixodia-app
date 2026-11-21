#!/usr/bin/env python3
"""Render the web's brand SVG into the app's density-bucketed PNG assets.

The web owns the emblem at public/brand/aixodia-mark.svg. This app must not
carry a second, hand-transcribed copy of its paths: generating both drawables
from that one file keeps the bar and the launcher identical to the web.

Requires cairosvg + Pillow (dev tools; the generated PNGs are committed).
Run from anywhere:  python3 scripts/render_brand_icons.py
"""

from pathlib import Path

import cairosvg
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SVG = ROOT / "aixodia-web" / "public" / "brand" / "aixodia-mark.svg"
RES = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"

# Android conventional density buckets, relative to mdpi.
BUCKETS = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

BAR_DP = 22          # the bar's <img width=22 height=22> on the web
FRAME_DP = 108       # adaptive icon foreground canvas
INSET_DP = 16        # keeps the outer ring inside the 66dp safe zone
MARK_DP = FRAME_DP - 2 * INSET_DP


def render_svg(size_px: int) -> Image.Image:
    png = cairosvg.svg2png(url=str(SVG), output_width=size_px, output_height=size_px)
    from io import BytesIO

    return Image.open(BytesIO(png)).convert("RGBA")


def main() -> None:
    assert SVG.is_file(), f"brand SVG missing: {SVG}"
    for bucket, scale in BUCKETS.items():
        out = RES / f"drawable-{bucket}"
        out.mkdir(parents=True, exist_ok=True)

        # Bar mark: the SVG itself, tight, at the web's 22dp.
        bar_px = round(BAR_DP * scale)
        render_svg(bar_px).save(out / "aixodia_mark.png")

        # Foreground: the same SVG inset into the 108dp adaptive frame.
        frame_px = round(FRAME_DP * scale)
        mark_px = round(MARK_DP * scale)
        inset_px = round(INSET_DP * scale)
        canvas = Image.new("RGBA", (frame_px, frame_px), (0, 0, 0, 0))
        canvas.alpha_composite(render_svg(mark_px), (inset_px, inset_px))
        canvas.save(out / "ic_launcher_foreground.png")

        print(f"{bucket}: mark {bar_px}px, foreground {frame_px}px")


if __name__ == "__main__":
    main()
