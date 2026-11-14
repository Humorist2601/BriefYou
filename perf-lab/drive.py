"""Stage 1: only looks around (the screens' texts), so the real scenarios can be written from it."""
import subprocess, sys, re, time
pkg, out = sys.argv[1], sys.argv[2]

def adb(*a, check=False):
    return subprocess.run(["adb", *a], capture_output=True, text=True).stdout

def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return adb("shell", "cat", "/sdcard/ui.xml")

def nodes(xml):
    for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?clickable="(\w+)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        yield m.group(1), m.group(2), m.group(3) == "true", tuple(int(x) for x in m.groups()[3:])

xml = dump()
with open(f"{out}/screen_texts.txt", "w") as f:
    for text, desc, clickable, b in nodes(xml):
        if text or desc:
            f.write(f"{b} text={text!r} desc={desc!r} clickable={clickable}\n")
print(open(f"{out}/screen_texts.txt").read()[:3000])
