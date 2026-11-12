"""Draws the README's Material 3 Expressive pictures (hero, feature cards, section headers) as SVG,
in light and dark versions. Text is turned into outlines with Google Sans Flex (the app's own
font), so GitHub shows it in that font. Icons are Material Symbols (rounded, filled).

Run from the repository root (pictures go to .github/assets/readme):

    pip install fonttools
    python3 .github/readme/make_assets.py

Icons are copied into .github/readme/icons from the @material-symbols/svg-400 npm package (Apache
2.0); add the "-fill" file of any new icon there.
"""
import base64
import html
import math
import os
import re
import sys

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

OUT = ".github/assets/readme"
ICONS = ".github/readme/icons"
FONT = "app/src/main/res/font/google_sans_flex.ttf"
APP_ICON = ".github/assets/icon-round.png"
os.makedirs(OUT, exist_ok=True)

# Material 3 tonal colours (baseline purple, like the app's default scheme).
THEMES = {
    "light": dict(
        surface="#F7F2FA", surfaceHigh="#ECE6F0", on="#1D1B20", onVariant="#49454F", outline="#CAC4D0",
        primary="#6750A4", pc="#EADDFF", onPc="#21005D",
        secondary="#625B71", sc="#E8DEF8", onSc="#1D192B",
        tertiary="#7D5260", tc="#FFD8E4", onTc="#31111D",
    ),
    "dark": dict(
        surface="#1D1B20", surfaceHigh="#2B2930", on="#E6E0E9", onVariant="#CAC4D0", outline="#49454F",
        primary="#D0BCFF", pc="#4F378B", onPc="#EADDFF",
        secondary="#CCC2DC", sc="#4A4458", onSc="#E8DEF8",
        tertiary="#EFB8C8", tc="#633B48", onTc="#FFD8E4",
    ),
}

_fonts = {}


def font(wght, wdth=100):
    key = (wght, wdth)
    if key not in _fonts:
        f = TTFont(FONT)
        _fonts[key] = instantiateVariableFont(f, {"wght": wght, "wdth": wdth, "opsz": 18, "ROND": 100, "GRAD": 0, "slnt": 0})
    return _fonts[key]


# Glyph outlines used by the picture being drawn, kept once each in its <defs> and placed with <use>.
_glyphs = {}


def text(s, x, y, size, fill, wght=400, wdth=100, anchor="start"):
    """Text as outlined glyphs (each outline stored once per picture); returns (svg, width)."""
    f = font(wght, wdth)
    gs = f.getGlyphSet()
    cmap = f.getBestCmap()
    upm = f["head"].unitsPerEm
    scale = size / upm
    width = sum(f["hmtx"][cmap.get(ord(c), ".notdef")][0] for c in s) * scale
    x0 = x - width if anchor == "end" else x - width / 2 if anchor == "middle" else x
    out = []
    cx = 0
    for c in s:
        g = cmap.get(ord(c), ".notdef")
        gid = f"g{wght}_{wdth}_{g}".replace(".", "_")
        if gid not in _glyphs:
            pen = SVGPathPen(gs)
            gs[g].draw(pen)
            d = re.sub(r"-?\d+\.\d+", lambda m: str(round(float(m.group()))), pen.getCommands())
            _glyphs[gid] = d
        if _glyphs[gid]:
            out.append(f'<use href="#{gid}" xlink:href="#{gid}" transform="translate({x0 + cx * scale:.1f},{y:.1f}) scale({scale:.5f},{-scale:.5f})"/>')
        cx += f["hmtx"][g][0]
    return f'<g fill="{fill}">{"".join(out)}</g>', width


def wrap(s, size, maxw, wght=400):
    f = font(wght)
    cmap = f.getBestCmap()
    scale = size / f["head"].unitsPerEm
    w = lambda t: sum(f["hmtx"][cmap.get(ord(c), ".notdef")][0] for c in t) * scale
    lines, cur = [], ""
    for word in s.split():
        t = (cur + " " + word).strip()
        if w(t) > maxw and cur:
            lines.append(cur)
            cur = word
        else:
            cur = t
    lines.append(cur)
    return lines


def shape(kind, cx, cy, r, fill, rot=0):
    """Material shapes: wavy-edged circles (cookie, flower, clover, sunny), a pill or a soft square."""
    if kind == "pill":
        return f'<rect x="{cx-r}" y="{cy-r*0.72}" width="{2*r}" height="{1.44*r}" rx="{0.72*r}" fill="{fill}"/>'
    if kind == "square":
        return f'<rect x="{cx-r*0.9}" y="{cy-r*0.9}" width="{1.8*r}" height="{1.8*r}" rx="{0.55*r}" fill="{fill}"/>'
    n, a, sharp = {
        "cookie9": (9, 0.07, 1), "cookie7": (7, 0.09, 1), "cookie4": (4, 0.12, 1),
        "clover4": (4, 0.22, 1), "flower8": (8, 0.12, 1), "sunny": (8, 0.07, 2.2), "burst": (12, 0.06, 1),
    }[kind]
    pts = []
    for i in range(361):
        t = math.radians(i) + math.radians(rot)
        wave = math.cos(n * t)
        if sharp != 1:
            wave = math.copysign(abs(wave) ** (1 / sharp), wave)
        rr = r * (1 - a + a * wave)
        pts.append(f"{cx + rr*math.cos(t):.1f},{cy + rr*math.sin(t):.1f}")
    return f'<path fill="{fill}" d="M{" L".join(pts)}Z"/>'


SPARKLE = "M12 2l2.2 7.8L22 12l-7.8 2.2L12 22l-2.2-7.8L2 12l7.8-2.2z"


def icon(name, cx, cy, size, fill):
    if name == "sparkle":
        s = size / 24
        return f'<path transform="translate({cx-size/2:.1f},{cy-size/2:.1f}) scale({s:.3f})" d="{SPARKLE}" fill="{fill}"/>'
    svg = open(os.path.join(ICONS, f"{name}-fill.svg")).read()
    d = " ".join(re.findall(r' d="([^"]+)"', svg))
    s = size / 960
    return f'<path transform="translate({cx-size/2:.1f},{cy+size/2:.1f}) scale({s:.4f})" d="{d}" fill="{fill}"/>'


def wave(x0, x1, y, amp, length, stroke, color):
    pts = []
    x = x0
    while x <= x1:
        pts.append(f"{x:.1f},{y + amp*math.sin((x-x0)/length*2*math.pi):.1f}")
        x += 2
    return f'<polyline points="{" ".join(pts)}" fill="none" stroke="{color}" stroke-width="{stroke}" stroke-linecap="round" stroke-linejoin="round"/>'


def svg(w, h, body, label):
    label = html.escape(label, quote=True)
    used = {gid: d for gid, d in _glyphs.items() if f'"#{gid}"' in body}
    defs = "<defs>" + "".join(f'<path id="{gid}" d="{d}"/>' for gid, d in used.items()) + "</defs>"
    _glyphs.clear()
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="{w}" height="{h}" '
        f'viewBox="0 0 {w} {h}" role="img" aria-label="{label}"><title>{label}</title>{defs}{body}</svg>'
    )


def write(name, content):
    open(os.path.join(OUT, name), "w").write(content)


app_icon = "data:image/png;base64," + base64.b64encode(open(APP_ICON, "rb").read()).decode()

# Hero ------------------------------------------------------------------------------------------
for mode, c in THEMES.items():
    W, H = 880, 280
    b = f'<rect width="{W}" height="{H}" rx="40" fill="{c["surface"]}"/>'
    b += f'<clipPath id="h"><rect width="{W}" height="{H}" rx="40"/></clipPath><g clip-path="url(#h)">'
    b += shape("cookie9", 815, 60, 125, c["pc"], 10)
    b += shape("flower8", 735, 268, 72, c["tc"], 5)
    b += shape("clover4", 860, 238, 58, c["sc"], 20)
    b += wave(690, 880, 168, 8, 40, 6, c["primary"])
    b += "</g>"
    b += f'<image x="56" y="56" width="120" height="120" xlink:href="{app_icon}" href="{app_icon}"/>'
    t, _ = text("Brief You", 200, 128, 64, c["on"], 650, 125)
    b += t
    for i, line in enumerate(["A calm, private RSS reader for Android,", "with AI that runs on your phone."]):
        t, _ = text(line, 202, 172 + i * 28, 21, c["onVariant"], 450)
        b += t
    t, _ = text("A fork of Read You", 58, 236, 15, c["primary"], 600)
    b += t
    write(f"hero-{mode}.svg", svg(W, H, b, "Brief You: a calm, private RSS reader for Android, with AI that runs on your phone"))

# Feature cards ---------------------------------------------------------------------------------
CARDS = [
    ("ai", "On-device AI", "Summaries, group gists and Ask, all private", "sparkle", "cookie9", "p"),
    ("triage", "Triage", "Swipe through a backlog, card by card", "style", "clover4", "t"),
    ("listen", "Read aloud", "Like a music app, with speed and progress", "graphic_eq", "sunny", "s"),
    ("calm", "Calmer feeds", "One digest, muted words, no duplicates", "notifications_off", "flower8", "t"),
    ("stats", "Reading stats", "Your week, streaks and a monthly recap", "analytics", "burst", "p"),
    ("backup", "Full backup", "Everything in one file", "backup", "cookie4", "s"),
]
for key, title, sub, ic, sh, tone in CARDS:
    for mode, c in THEMES.items():
        W, H = 288, 176
        bg = {"p": c["pc"], "s": c["sc"], "t": c["tc"]}[tone]
        fg = {"p": c["onPc"], "s": c["onSc"], "t": c["onTc"]}[tone]
        accent = {"p": c["primary"], "s": c["secondary"], "t": c["tertiary"]}[tone]
        b = f'<rect width="{W}" height="{H}" rx="32" fill="{bg}"/>'
        b += shape(sh, 52, 52, 30, accent, 12)
        b += icon(ic, 52, 52, 26, bg)
        t, _ = text(title, 24, 116, 21, fg, 650, 110)
        b += t
        for i, line in enumerate(wrap(sub, 14.5, W - 48)[:2]):
            t, _ = text(line, 24, 142 + i * 20, 14.5, fg, 420)
            b += t.replace(f'fill="{fg}"', f'fill="{fg}" fill-opacity="0.8"')
        write(f"card-{key}-{mode}.svg", svg(W, H, b, f"{title}: {sub}"))

# Section headers -------------------------------------------------------------------------------
HEADERS = [
    ("ai", "On-device AI", "sparkle", "cookie9", "p"),
    ("reading", "Reading", "menu_book", "cookie7", "s"),
    ("listen", "Read aloud", "graphic_eq", "sunny", "t"),
    ("triage", "Triage", "style", "clover4", "p"),
    ("calm", "Calmer feeds", "notifications_off", "flower8", "s"),
    ("look", "Look and feel", "palette", "burst", "t"),
    ("backup", "Backup and updates", "backup", "cookie4", "p"),
]
for key, title, ic, sh, tone in HEADERS:
    for mode, c in THEMES.items():
        W, H = 880, 72
        accent = {"p": c["primary"], "s": c["secondary"], "t": c["tertiary"]}[tone]
        cont = {"p": c["pc"], "s": c["sc"], "t": c["tc"]}[tone]
        b = shape(sh, 30, 36, 28, cont, 10)
        b += icon(ic, 30, 36, 26, accent)
        t, w = text(title, 74, 46, 28, c["on"], 650, 112)
        b += t
        b += wave(74 + w + 24, W - 8, 37, 4, 22, 3.5, c["outline"])
        write(f"h-{key}-{mode}.svg", svg(W, H, b, title))

# Feature tables ---------------------------------------------------------------------------------
# One picture per README section: rows connected into one group (rounded at its ends, small gaps
# between), the feature's name on the left and what it does on the right (wrapping if needed).
TABLES = {
    "ai": [
        ('Summaries', 'Tap the sparkle in an article: 3 key points and why it matters.'),
        ('Group gists', 'A briefing of your newest unread, grouped by story.'),
        ('Ask', 'Ask questions about an article or a gist.'),
        ('Read aloud', 'Any summary or gist, read aloud.'),
        ('Your instructions', 'Edit what the model is told, in Settings, AI model.'),
        ('Download in the app', 'Gemma downloads in the background, after a quick check of your phone.'),
    ],
    "reading": [
        ('Colours from the picture', 'The page takes its colours from the article’s picture.'),
        ('Floating toolbar', 'Star, AI, next, read aloud and full content; hides as you scroll.'),
        ('Quick text sheet', 'Size, spacing, margins and font, changed live.'),
        ('Steady pictures', 'Pictures load in place, so the text never jumps.'),
        ('Next article card', 'At the end of an article, or "You’re all caught up".'),
        ('Reading time', '4 min in the list and on the page.'),
        ('Read later', 'Share a link from any app to save the full page, offline.'),
        ('Newspaper', 'Hold Share to send an article as a newspaper front page.'),
        ('Tidier articles', 'No share buttons, "Read more" links or tracking pixels.'),
    ],
    "listen": [
        ('Media controls', 'In the notification, lock screen and Quick Settings.'),
        ('Progress', 'A wavy ring follows the words being spoken.'),
        ('Speed', '0.8× to 2×; the last speed is kept.'),
        ('Pause and stop', 'Tap to pause; long press while paused to stop.'),
    ],
    "triage": [
        ('Card stack', 'Swipe right: read · left: save · up: open · down: skip.'),
        ('Summary on the card', 'Long-press a card for 3 key points from the on-device AI.'),
        ('Auto-advance', 'Skips each card after a few seconds, with a wavy countdown ring.'),
        ('Next group', 'When a group is done, the next one follows; a bar shows the whole run.'),
        ('Jump between groups', 'Tap the group name under "Triage" to pick another, or open a group to Triage just one feed.'),
        ('Share', 'Tap Share for the link; hold it to share the card as a picture.'),
        ('Inbox zero', 'A little celebration when you’re all caught up.'),
    ],
    "calm": [
        ('One digest notification', 'One per sync instead of one per article, with Mark all read and Triage.'),
        ('Muted keywords', 'Articles mentioning them are marked read after each sync.'),
        ('Watched words', 'Words you care about stand out and come first.'),
        ('Duplicate stories', 'When a story is published again, only the first stays unread.'),
        ('Recently filtered', 'What was hidden and why, with Undo.'),
        ('Sort feeds and groups', 'By name, unread count or the order you added them.'),
    ],
    "look": [
        ('Material 3 Expressive', 'Shape-morphing buttons, shaped icons, wavy progress.'),
        ('Google Sans Flex', 'Rounded throughout, with wide headings.'),
        ('Material You, the current way', 'The system’s wallpaper colours and Google’s latest colour spec.'),
        ('Feed colours and icons', 'Names coloured from their icon; pick a Material icon, an emoji or an image.'),
        ('App shortcuts', 'Long-press the icon for Triage, AI gist, Unread or Sync now.'),
        ('Reading stats', 'Your week, streak, top feeds and this month vs last.'),
        ('Monthly recap', 'Last month as calendar and highlights; last year each January.'),
        ('Swipe between lists', 'Starred, Unread and All, each with its own scroll position.'),
        ('Predictive back', 'Android’s own back animation, in screens and dialogs.'),
        ('New icon and animated splash', 'A fanned stack of cards that grows into the app.'),
        ('First-run tour', 'Four pages on what’s different, and a hint the first time you meet a feature.'),
    ],
    "backup": [
        ('Full backup and restore', 'Feeds, starred articles, filters and settings in one file.'),
        ('Own updates', 'The app checks Brief You’s releases, never Read You’s.'),
        ('Signed releases', 'Every push to main is built and published as Brief You 0.16.2.N.'),
        ('Brief You Nightly', 'Test builds as their own app, next to Brief You.'),
        ('Reviewed Read You updates', 'Read You’s changes arrive as a pull request to review.'),
    ],
}

# One name column for every table, so the descriptions line up from section to section.
NAME_W = 22 + max(text(n, 0, 0, 16, "#000", 620)[1] for rows in TABLES.values() for n, _ in rows) + 28

for key, rows in TABLES.items():
    rows = [(n, re.sub(r'"([^"]*)"', "\u201c\\1\u201d", d)) for n, d in rows]  # curly quotes
    for mode, c in THEMES.items():
        W = 880
        size = 16
        nameW = NAME_W
        _glyphs.clear()
        lines = [wrap(desc, size, W - nameW - 40, 420) for _, desc in rows]
        heights = [max(52, 22 + 24 * len(l)) for l in lines]
        gap = 3
        H = sum(heights) + gap * (len(rows) - 1)
        b = ""
        y = 0
        for i, ((name, desc), h, ls) in enumerate(zip(rows, heights, lines)):
            top = 22 if i == 0 else 6
            bottom = 22 if i == len(rows) - 1 else 6
            b += (
                f'<path fill="{c["surfaceHigh"]}" d="M{top},{y} H{W-top} Q{W},{y} {W},{y+top} V{y+h-bottom} '
                f'Q{W},{y+h} {W-bottom},{y+h} H{bottom} Q0,{y+h} 0,{y+h-bottom} V{y+top} Q0,{y} {top},{y} Z"/>'
            )
            t, _ = text(name, 22, y + h / 2 + 6, size, c["on"], 620)
            b += t
            ty = y + (h - 24 * len(ls)) / 2 + 17
            for j, line in enumerate(ls):
                t, _ = text(line, nameW, ty + j * 24, size, c["onVariant"], 420)
                b += t
            y += h + gap
        label = " ".join(f"{n}: {d}" for n, d in rows)
        write(f"t-{key}-{mode}.svg", svg(W, H, b, label))
print("ok")
