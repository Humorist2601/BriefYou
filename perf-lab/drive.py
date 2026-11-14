"""Drives the app through the screens. Stage 2: gets past the tour, then looks at each screen
(screenshots and texts) so the real scenarios can be written from them."""
import re, subprocess, sys, time
pkg, out = sys.argv[1], sys.argv[2]

def adb(*a):
    return subprocess.run(["adb", *a], capture_output=True, text=True).stdout

def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return adb("shell", "cat", "/sdcard/ui.xml")

def nodes(xml):
    for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?clickable="(\w+)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        yield m.group(1), m.group(2), m.group(3) == "true", tuple(int(x) for x in m.groups()[3:])

def find(xml, needle):
    for text, desc, _, b in nodes(xml):
        if needle.lower() in text.lower() or needle.lower() in desc.lower():
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
for i in range(8):   # system dialogs, notification prompt, then the tour pages
    clear_blockers()
    xml = dump()
    if find(xml, "Skip"):
        tap(find(xml, "Skip")); time.sleep(2)
    elif not find(xml, "Next"):
        break
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
