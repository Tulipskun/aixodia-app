#!/usr/bin/env python3
"""Render the web's brand SVG into the app's density-bucketed PNG assets.

The web owns the emblem at public/brand/aixodia-mark.svg. This app must not
carry a second, hand-transcribed copy of its paths: generating both drawables
from that one file keeps the bar and the launcher identical to the web.

The launcher is the one place allowed a colour of its own. Its plate is a solid
square of the icon background, and the mark's own disc is filled with that same
colour so the centre is not a near-black hole in the middle of a blue plate; the
colour is read from res/values/ic_launcher_background.xml, which is also what the
adaptive icon's background layer paints, so there is still one number to change.
The bar's mark keeps the SVG's own colours, because it sits on the app's chrome
rather than on the plate.

Requires cairosvg + Pillow (dev tools; the generated PNGs are committed).
Run from anywhere:  python3 scripts/render_brand_icons.py
"""

import re
from io import BytesIO
from pathlib import Path

import cairosvg
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SVG = ROOT / "aixodia-web" / "public" / "brand" / "aixodia-mark.svg"
RES = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"
BACKGROUND_XML = RES / "values" / "ic_launcher_background.xml"

# The mark's outermost disc, as the SVG paints it: the web's surface2 token.
DISC_FILL = 'fill="#141417"'

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


def plate_colour() -> str:
    """The icon's background colour, as the resource file holds it."""
    text = BACKGROUND_XML.read_text()
    found = re.search(r'name="ic_launcher_background">\s*(#[0-9A-Fa-f]{6})\s*<', text)
    assert found, f"no ic_launcher_background colour in {BACKGROUND_XML}"
    return found.group(1)


def render_svg(size_px: int, disc: str | None = None) -> Image.Image:
    """The mark at `size_px`, optionally with its disc filled with `disc`."""
    if disc is None:
        png = cairosvg.svg2png(url=str(SVG), output_width=size_px, output_height=size_px)
    else:
        source = SVG.read_text()
        # Exactly one, so an SVG edit that moves the disc's colour stops this
        # script instead of quietly leaving a dark centre behind.
        assert source.count(DISC_FILL) == 1, f"expected one {DISC_FILL} in {SVG}"
        png = cairosvg.svg2png(
            bytestring=source.replace(DISC_FILL, f'fill="{disc}"').encode(),
            output_width=size_px,
            output_height=size_px,
        )
    return Image.open(BytesIO(png)).convert("RGBA")


def main() -> None:
    assert SVG.is_file(), f"brand SVG missing: {SVG}"
    disc = plate_colour()
    for bucket, scale in BUCKETS.items():
        out = RES / f"drawable-{bucket}"
        out.mkdir(parents=True, exist_ok=True)

        # Bar mark: the SVG itself, tight, at the web's 22dp.
        bar_px = round(BAR_DP * scale)
        render_svg(bar_px).save(out / "aixodia_mark.png")

        # Foreground: the same SVG inset into the 108dp adaptive frame, its disc
        # in the plate's colour.
        frame_px = round(FRAME_DP * scale)
        mark_px = round(MARK_DP * scale)
        inset_px = round(INSET_DP * scale)
        canvas = Image.new("RGBA", (frame_px, frame_px), (0, 0, 0, 0))
        canvas.alpha_composite(render_svg(mark_px, disc), (inset_px, inset_px))
        canvas.save(out / "ic_launcher_foreground.png")

        print(f"{bucket}: mark {bar_px}px, foreground {frame_px}px, disc {disc}")


if __name__ == "__main__":
    main()
