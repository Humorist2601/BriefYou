"""Drives the app through the screens. Stage 2: gets past the tour, then looks at each screen
(screenshots and texts) so the real scenarios can be written from them."""
import re, subprocess, sys, time
pkg, out = sys.argv[1], sys.argv[2]
mode = sys.argv[3] if len(sys.argv) > 3 else "scenarios"   # "scenarios" or "readme"

def adb(*a):
    return subprocess.run(["adb", *a], capture_output=True, text=True).stdout

def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return adb("shell", "cat", "/sdcard/ui.xml")

def nodes(xml):
    for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?clickable="(\w+)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        yield m.group(1), m.group(2), m.group(3) == "true", tuple(int(x) for x in m.groups()[3:])

def find(xml, needle):
    """Exact label match first (so 'Skip' is the button, not a sentence containing 'skip')."""
    n = needle.lower()
    for text, desc, _, b in nodes(xml):
        if n == text.lower() or n == desc.lower():
            return b
    for text, desc, _, b in nodes(xml):
        if n in text.lower() or n in desc.lower():
            return b
    return None

def tap(b):
    adb("shell", "input", "tap", str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))

def tap_text(needle, wait=8):
    end = time.time() + wait
    while time.time() < end:
        b = find(dump(), needle)
        if b:
            tap(b)
            return True
        time.sleep(1)
    return False

def look(name):
    """Screenshot and a list of the texts on screen."""
    with open(f"{out}/{name}.png", "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)
    xml = dump()
    with open(f"{out}/{name}.txt", "w") as f:
        for text, desc, clickable, b in nodes(xml):
            if text or desc:
                f.write(f"{b} text={text!r} desc={desc!r} clickable={clickable}\n")
    return xml

look("10_start")
def clear_blockers():
    for label in ("Wait", "Allow", "Got it", "Close"):
        if tap_text(label, wait=1):
            time.sleep(1)
log = open(f"{out}/drive.log", "w")
def say(*a):
    log.write(" ".join(str(x) for x in a) + "\n"); log.flush()
for i in range(10):   # system dialogs, notification prompt, then the tour pages
    clear_blockers()
    xml = dump()
    texts = [t for t, d, c, b in nodes(xml) if t]
    say("round", i, texts[:6])
    target = find(xml, "Skip") or find(xml, "Next")
    if not target:
        break
    tap(target); say("tapped", target)
    time.sleep(2)
    look(f"10_round{i}")
look("11_after_tour")
time.sleep(4)
xml = look("12_home")
for label in ("Games", "News", "Technology"):
    b = find(xml, label)
    if b:
        tap(b); time.sleep(3)
        xml = look(f"13_after_tap_{label}")
        break
adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(2)
look("14_after_back")


if mode == "readme":
    # Clean light and dark screenshots of the main screens for the README (out/readme/).
    import os
    os.makedirs(f"{out}/readme", exist_ok=True)
    def shot(name):
        with open(f"{out}/readme/{name}.png", "wb") as f:
            f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)
    for night in ("no", "yes"):
        adb("shell", "cmd", "uimode", "night", night); time.sleep(3)
        tag = "dark" if night == "yes" else "light"
        adb("shell", "am", "start", "-n", pkg + "/me.ash.reader.infrastructure.android.MainActivity"); time.sleep(3)
        shot(f"home-{tag}")
        xml = dump()
        for label in ("Games", "News", "Technology"):
            b = find(xml, label)
            if b:
                tap(b); time.sleep(3); shot(f"list-{tag}")
                break
        adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(2)
