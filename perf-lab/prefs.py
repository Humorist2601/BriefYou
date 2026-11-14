"""Sets app settings in the DataStore file (Preferences protobuf) without touching the rest.
Usage: prefs.py <file> key=int ... ; rewrites the file in place."""
import sys

def rv(b, i):
    n = s = 0
    while True:
        c = b[i]; i += 1; n |= (c & 0x7f) << s; s += 7
        if not c & 0x80: return n, i

def wv(n):
    o = bytearray()
    while True:
        c = n & 0x7f; n >>= 7
        o.append(c | (0x80 if n else 0))
        if not n: return bytes(o)

def ld(field, data): return wv(field << 3 | 2) + wv(len(data)) + data

def parse(b):
    entries, i = {}, 0
    while i < len(b):
        tag, i = rv(b, i); ln, i = rv(b, i); e = b[i:i + ln]; i += ln
        k, j = rv(e, 0); kl, j = rv(e, j); key = e[j:j + kl].decode(); entries[key] = e[j + kl:]
    return entries

def encode(entries):
    return b"".join(ld(1, ld(1, k.encode()) + v) for k, v in entries.items())

path = sys.argv[1]
try: data = open(path, "rb").read()
except FileNotFoundError: data = b""
entries = parse(data)
for kv in sys.argv[2:]:
    k, v = kv.split("=")
    entries[k] = ld(2, wv(3 << 3 | 0) + wv(int(v)))  # Value{ integer = 3 }
open(path, "wb").write(encode(entries))
