#!/usr/bin/env python3
"""Generate Loot Ender's mod menu icon: a chest front wearing the gold clasp.

The clasp is the mod. Everything Loot Ender does that a player can see comes down
to which clasp a chest is wearing - gold for one nobody has been into, dark for
one they have emptied - so the icon is simply the gold one, on the chest it
belongs to.

Nothing here is drawn by hand. The chest front is vanilla's own two faces, the
lid's and the base's, stacked the way the model stacks them; the clasp is lifted
out of this mod's sealed.png, which is itself generated from vanilla's clasp.

Pure stdlib PNG reader and writer (zlib + struct), no Pillow, deterministic:
re-running produces identical bytes. Nearest neighbour throughout, never smoothed.

Usage: python3 generate_icon.py
"""

import glob
import os
import struct
import sys
import zipfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "src/main/resources/assets/loot-ender/icon.png")
SEALED = os.path.join(HERE, "src/main/resources/assets/loot-ender/textures/entity/chest/sealed.png")

# Vanilla's chest atlas, and the two faces that make up the front of a closed one.
LID_FRONT = (14, 14, 14, 5)      # x, y, w, h
BASE_FRONT = (14, 33, 14, 10)
# Of the clasp's 6x5 corner, this is the face you actually see head-on.
CLASP_FACE = (1, 1, 2, 4)

CANVAS = 16
SCALE = 8                        # 16 -> 128, the size the rest of the suite uses


def client_jar():
    jars = sorted(glob.glob(os.path.expanduser(
        "~/.gradle/caches/fabric-loom/*/minecraft-client*.jar")), key=os.path.getmtime)
    if not jars:
        sys.exit("no Loom client jar cached - run a build first")
    return jars[-1]


def read_png(data):
    """Decode a non-interlaced 8-bit PNG to rows of RGBA tuples."""
    pos, idat, palette, trns = 8, b"", None, None
    width = height = depth = colour = 0
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        if tag == b"IHDR":
            width, height, depth, colour = struct.unpack(">IIBB", body[:10])
        elif tag == b"PLTE":
            palette = body
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
        pos += 12 + length

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
    raw = zlib.decompress(idat)
    stride = width * channels
    out, prev = [], bytearray(stride)

    for y in range(height):
        head = y * (stride + 1)
        filt = raw[head]
        line = bytearray(raw[head + 1:head + 1 + stride])
        for i in range(stride):
            a = line[i - channels] if i >= channels else 0
            b = prev[i]
            c = prev[i - channels] if i >= channels else 0
            if filt == 1: line[i] = (line[i] + a) & 0xFF
            elif filt == 2: line[i] = (line[i] + b) & 0xFF
            elif filt == 3: line[i] = (line[i] + (a + b) // 2) & 0xFF
            elif filt == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 0xFF
        prev = line

        row = []
        for x in range(width):
            px = line[x * channels:(x + 1) * channels]
            if colour == 6: row.append(tuple(px))
            elif colour == 2: row.append((px[0], px[1], px[2], 255))
            elif colour == 3:
                i = px[0]
                alpha = trns[i] if trns and i < len(trns) else 255
                row.append((palette[i * 3], palette[i * 3 + 1], palette[i * 3 + 2], alpha))
            elif colour == 0: row.append((px[0], px[0], px[0], 255))
            else: row.append((px[0], px[0], px[0], px[1]))
        out.append(row)
    return out


def write_png(path, rows):
    height, width = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in rows)

    def chunk(tag, body):
        c = tag + body
        return struct.pack(">I", len(body)) + c + struct.pack(">I", zlib.crc32(c))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)
    print("wrote %s (%dx%d)" % (path, width, height))


def paste(canvas, source, region, at):
    x0, y0, w, h = region
    ax, ay = at
    for y in range(h):
        for x in range(w):
            px = source[y0 + y][x0 + x]
            if px[3] == 0:
                continue
            canvas[ay + y][ax + x] = px


def build():
    with zipfile.ZipFile(client_jar()) as jar:
        chest = read_png(jar.read("assets/minecraft/textures/entity/chest/normal.png"))
    sealed = read_png(open(SEALED, "rb").read())

    canvas = [[(0, 0, 0, 0)] * CANVAS for _ in range(CANVAS)]

    # One pixel in from the left, and sitting on the bottom edge: fifteen rows of
    # chest in a sixteen row frame leaves the gap at the top, where a lid would open.
    paste(canvas, chest, LID_FRONT, (1, 1))
    paste(canvas, chest, BASE_FRONT, (1, 6))

    # The clasp straddles the seam between the two, centred across the front.
    paste(canvas, sealed, CLASP_FACE, (1 + (14 - CLASP_FACE[2]) // 2, 4))

    return [[canvas[y // SCALE][x // SCALE] for x in range(CANVAS * SCALE)]
            for y in range(CANVAS * SCALE)]


if __name__ == "__main__":
    write_png(OUT, build())
