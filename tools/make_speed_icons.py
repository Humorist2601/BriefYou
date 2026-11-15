"""Makes the speed number icons (ic_brief_speed_*.xml) for the media notifications. Brief You.

Run from the repo root: python3 tools/make_speed_icons.py app/src/main/res/drawable
Needs fontTools and Poppins Bold (Google Fonts; FONT below). Add a speed to SPEEDS and to
SpeedIcons.kt together.
"""
import sys
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

FONT = "/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf"
OUT = sys.argv[1]
SPEEDS = ["0.8", "1", "1.2", "1.4", "1.6", "1.8", "2", "2.5", "3"]

font = TTFont(FONT)
cmap = font.getBestCmap()
glyphs = font.getGlyphSet()
hmtx = font["hmtx"]
units = font["head"].unitsPerEm
cap = font["OS/2"].sCapHeight or int(units * 0.7)


def name_for(ch):
    return cmap[ord(ch)]


# One size for all (the widest decides), so 1× isn't bigger than 1.2×.
X = "\u00d7" if 0xD7 in cmap else "x"
SCALE = min(min(22 / sum(hmtx[name_for(c)][0] for c in s + X) for s in SPEEDS), 11 / cap)

for speed in SPEEDS:
    text = speed + ("×" if 0xD7 in cmap else "x")
    # Width of the whole string in font units (with a little tightening of the dot).
    names = [name_for(c) for c in text]
    width = sum(hmtx[n][0] for n in names)
    # Fit in a 24 x 24 box with 1 unit of margin each side; at most cap height 11.
    scale = SCALE
    x0 = (24 - width * scale) / 2
    baseline = 12 + cap * scale / 2
    pen = SVGPathPen(glyphs)
    x = 0
    for n in names:
        # Flip y (fonts go up, Android goes down), place and scale.
        t = TransformPen(pen, (scale, 0, 0, -scale, x0 + x * scale, baseline))
        glyphs[n].draw(t)
        x += hmtx[n][0]
    d = pen.getCommands()
    file = f"{OUT}/ic_brief_speed_{speed.replace('.', '_')}.xml"
    with open(file, "w") as f:
        f.write(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<!-- Brief You: a speed for the media notifications (made from Poppins Bold). -->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n    android:height="24dp"\n'
            '    android:viewportWidth="24"\n    android:viewportHeight="24">\n'
            f'    <path\n        android:fillColor="#FFFFFFFF"\n        android:pathData="{d}" />\n'
            "</vector>\n"
        )
    print(file, round(scale, 4))
