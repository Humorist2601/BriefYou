"""Brief You README: the Podcasts and videos header and feature table, and the Listen and watch
tile, in the same style as the other README pictures (make_assets.py)."""
import sys
src = open("make_assets.py").read().split("app_icon = ")[0]
exec(src)  # helpers, THEMES, OUT/ICONS/FONT from argv

ROWS = [
    ("Player in the article", "Episodes and videos play under the title, in one compact player."),
    ("Speed and sleep timer", "0.8× to 3×, kept for next time; a timer that fades out."),
    ("Carries on", "Picks up where you stopped; marked read once mostly played."),
    ("Notification", "Speed, back 30, progress, forward 30 and stop; resume after a restart."),
    ("Videos", "In their own shape, with picture-in-picture and full screen."),
    ("Now playing", "A pill above the bottom bar; tap it for the full controls."),
    ("YouTube", "Videos inside articles open in the YouTube app."),
    ("Feed pictures", "A podcast's cover as its feed's icon, or the website's icon."),
]

def table(rows, mode):
    c = THEMES[mode]
    W, RH, GAP = 880, 52, 3
    H = len(rows) * RH + (len(rows) - 1) * GAP
    b = ""
    for i, (title, desc) in enumerate(rows):
        y = i * (RH + GAP)
        top = 22 if i == 0 else 6
        bot = 22 if i == len(rows) - 1 else 6
        b += (f'<path fill="{c["surfaceHigh"]}" d="M{top},{y} H{W-top} Q{W},{y} {W},{y+top} V{y+RH-bot} '
              f'Q{W},{y+RH} {W-bot},{y+RH} H{bot} Q0,{y+RH} 0,{y+RH-bot} V{y+top} Q0,{y} {top},{y} Z"/>')
        t, _ = text(title, 22, y + 32, 16, c["on"], 620)
        b += t
        t, w = text(desc, 280, y + 31, 16, c["onVariant"], 420)
        assert 280 + w < W - 16, (desc, w)
        b += t
    label = " ".join(f"{t}: {d}" for t, d in rows)
    return svg(W, H, b, label), label

for mode, c in THEMES.items():
    s, label = table(ROWS, mode)
    write(f"t-media-{mode}.svg", s)
    # Header
    W, H = 880, 72
    accent, cont = c["primary"], c["pc"]
    b = shape("flower8", 30, 36, 28, cont, 10)
    b += icon("podcasts", 30, 36, 26, accent)
    t, w = text("Podcasts and videos", 74, 46, 28, c["on"], 650, 112)
    b += t
    b += wave(74 + w + 24, W - 8, 37, 4, 22, 3.5, c["outline"])
    write(f"h-media-{mode}.svg", svg(W, H, b, "Podcasts and videos"))
    # Tile
    key, title, sub, ic, sh, tone = ("listen", "Listen and watch", "Read aloud, podcasts and videos, with speed and a sleep timer", "graphic_eq", "sunny", "s")
    W, H = 288, 176
    bg, fg, acc = c["sc"], c["onSc"], c["secondary"]
    b = f'<rect width="{W}" height="{H}" rx="32" fill="{bg}"/>'
    b += shape(sh, 52, 52, 30, acc, 12)
    b += icon(ic, 52, 52, 26, bg)
    t, _ = text(title, 24, 116, 21, fg, 650, 110)
    b += t
    lines = wrap(sub, 14.5, W - 48)
    assert len(lines) <= 2, lines
    for i, line in enumerate(lines):
        t, _ = text(line, 24, 142 + i * 20, 14.5, fg, 420)
        b += t.replace(f'fill="{fg}"', f'fill="{fg}" fill-opacity="0.8"')
    write(f"card-{key}-{mode}.svg", svg(W, H, b, f"{title}: {sub}"))
print(label)
