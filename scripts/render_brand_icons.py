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

# Every colour the web's mark uses, apart from the disc. The launcher's icon
# draws the whole mark in one ink on a pale disc; seeing them listed here and
# asserted one at a time means a recolor in the web SVG either updates this
# script too or fails it loudly instead of shipping a two-tone mark by accident.
MARK_SWAPS = {
    'stroke="#7fa6d3" stroke-opacity="0.55"': 'stroke="{ink}" stroke-opacity="1"',
    'stroke="#7fa6d3" stroke-opacity="0.5"': 'stroke="{ink}" stroke-opacity="1"',
    '<stop offset="0%" stop-color="#7fa6d3" />': '<stop offset="0%" stop-color="{ink}" />',
    '<stop offset="100%" stop-color="#5c7fae" />': '<stop offset="100%" stop-color="{ink}" />',
    '<g fill="#7fa6d3" fill-opacity="0.9">': '<g fill="{ink}" fill-opacity="1">',
    'fill="#f2f2f3"': 'fill="{ink}"',
}

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


def icon_tokens() -> dict[str, str]:
    """The icon's two colours, read from the resource file both are declared in
    so there is still exactly one place to change them."""
    text = BACKGROUND_XML.read_text()
    tokens = {}
    for name in ("ic_launcher_background", "ic_launcher_disc"):
        found = re.search(r'name="' + name + r'">\s*(#[0-9A-Fa-f]{6})\s*<', text)
        assert found, f"no {name} colour in {BACKGROUND_XML}"
        tokens[name] = found.group(1)
    return tokens


def render_svg(size_px: int, disc: str | None = None, ink: str | None = None) -> Image.Image:
    """The mark at `size_px`. With `disc` it is recoloured for the launcher:
    the disc in `disc`, and every other colour in `ink` — one colour on a pale
    disc, because a mark whose colours match the plate disappears into it.
    Without either the SVG is rendered as the web owns it, for the bar."""
    if disc is None:
        assert ink is None
        png = cairosvg.svg2png(url=str(SVG), output_width=size_px, output_height=size_px)
    else:
        source = SVG.read_text()
        # Exactly one, so an SVG edit that moves these stops this script
        # instead of quietly shipping a recolored-by-accident mark.
        assert source.count(DISC_FILL) == 1, f"expected one {DISC_FILL} in {SVG}"
        source = source.replace(DISC_FILL, f'fill="{disc}"')
        for original, replacement in MARK_SWAPS.items():
            assert source.count(original) == 1, f"expected one {original!r} in {SVG}"
            source = source.replace(original, replacement.format(ink=ink))
        png = cairosvg.svg2png(bytestring=source.encode(), output_width=size_px, output_height=size_px)
    return Image.open(BytesIO(png)).convert("RGBA")


def main() -> None:
    assert SVG.is_file(), f"brand SVG missing: {SVG}"
    tokens = icon_tokens()
    disc = tokens["ic_launcher_disc"]
    ink = tokens["ic_launcher_background"]
    for bucket, scale in BUCKETS.items():
        out = RES / f"drawable-{bucket}"
        out.mkdir(parents=True, exist_ok=True)

        # Bar mark: the SVG itself, tight, at the web's 22dp.
        bar_px = round(BAR_DP * scale)
        render_svg(bar_px).save(out / "aixodia_mark.png")

        # Foreground: the same SVG inset into the 108dp adaptive frame, drawn in
        # one ink on the pale disc.
        frame_px = round(FRAME_DP * scale)
        mark_px = round(MARK_DP * scale)
        inset_px = round(INSET_DP * scale)
        canvas = Image.new("RGBA", (frame_px, frame_px), (0, 0, 0, 0))
        canvas.alpha_composite(render_svg(mark_px, disc, ink), (inset_px, inset_px))
        canvas.save(out / "ic_launcher_foreground.png")

        # Monochrome: the ring and the star alone, in white, with the disc left
        # out. The system tints this layer's alpha, so filling the disc here would
        # turn a themed icon into a plain filled circle — the emblem has to be the
        # thing that is drawn, not the field behind it.
        mono = Image.new("RGBA", (frame_px, frame_px), (0, 0, 0, 0))
        mono.alpha_composite(render_svg(mark_px, "none", "#FFFFFF"), (inset_px, inset_px))
        mono.save(out / "ic_launcher_monochrome.png")

        print(f"{bucket}: mark {bar_px}px, foreground {frame_px}px, disc {disc}, ink {ink}")


if __name__ == "__main__":
    main()
