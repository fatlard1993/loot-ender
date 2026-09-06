#!/usr/bin/env python3
"""Generate Loot Ender's lockpicking art: the lock screen's three sprites, and the pick item.

The three screen sprites share one 64x64 canvas and one centre, because the screen
stacks them in the same bounds and turns them about that point: the face never
moves, the cylinder shows how far the last torque got, and the pick shows where
you put it. Anything drawn off-centre would wobble as it turned.

Metal tones are read out of the vanilla iron textures rather than written down, so
the lock is made of the same iron the pick is crafted from.

Pure stdlib PNG reader and writer (zlib + struct) so it runs without Pillow, the
same script generated art approach as the rest of the suite. Deterministic:
re-running produces identical bytes.

Usage: python3 generate_lock_textures.py [path/to/minecraft.jar]
"""

import glob
import math
import os
import struct
import sys
import zipfile
import zlib
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
GUI = os.path.join(HERE, "src/main/resources/assets/loot-ender/textures/gui")
ITEM = os.path.join(HERE, "src/main/resources/assets/loot-ender/textures/item")

CLEAR = (0, 0, 0, 0)
_JAR = None


def minecraft_version():
    """The version this mod targets, so the sprite is cut from the same jar the
    mod is built against rather than whatever happens to be cached."""
    path = os.path.join(HERE, "gradle.properties")
    if not os.path.exists(path):
        return None
    for line in open(path):
        key, sep, value = line.partition("=")
        if sep and key.strip() == "minecraft_version":
            return value.strip()
    return None


def find_jar():
    """Loom caches the remapped Minecraft jars after a build; that is where the
    vanilla art comes from. Override with an argument or $MINECRAFT_JAR."""
    global _JAR
    if _JAR:
        return _JAR
    if len(sys.argv) > 1:
        _JAR = sys.argv[1]
        return _JAR
    if os.environ.get("MINECRAFT_JAR"):
        _JAR = os.environ["MINECRAFT_JAR"]
        return _JAR
    cache = os.path.expanduser("~/.gradle/caches/fabric-loom")
    names = ("minecraft-merged.jar", "minecraft-client.jar")
    found = []
    version = minecraft_version()
    if version:
        for name in names:
            found += glob.glob(os.path.join(cache, version, name))
    if not found:
        for name in names:
            found += glob.glob(os.path.join(cache, "*", name))
    if not found:
        sys.exit("no cached Minecraft jar found: build the mod once, "
                 "or pass a jar path as the first argument")
    _JAR = max(found, key=os.path.getmtime)
    return _JAR


def vanilla(name):
    """Read assets/minecraft/textures/<name> out of the vanilla jar."""
    with zipfile.ZipFile(find_jar()) as jar:
        return decode_png(jar.read("assets/minecraft/textures/" + name))


def decode_png(data):
    """Minimal PNG reader: no interlacing, every colour type and bit depth
    vanilla actually ships. Returns rows of RGBA tuples."""
    pos = 8
    idat = b""
    width = height = depth = ctype = None
    palette = trns = None
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
            assert interlace == 0, "interlaced PNG not supported"
        elif tag == b"PLTE":
            palette = body
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
        elif tag == b"IEND":
            break

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    stride = (width * channels * depth + 7) // 8
    step = max(1, (channels * depth) // 8)
    raw = zlib.decompress(idat)
    out = bytearray(stride * height)
    prev = bytearray(stride)
    p = 0
    for y in range(height):
        filt = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if filt == 1:
            for i in range(step, stride):
                line[i] = (line[i] + line[i - step]) & 0xFF
        elif filt == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif filt == 3:
            for i in range(stride):
                a = line[i - step] if i >= step else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif filt == 4:
            for i in range(stride):
                a = line[i - step] if i >= step else 0
                b = prev[i]
                c = prev[i - step] if i >= step else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = line
        prev = line

    pixels = []
    if depth < 8:
        per = 8 // depth
        mask = (1 << depth) - 1
        for y in range(height):
            base = y * stride
            row = []
            for x in range(width):
                i = x * channels
                value = (out[base + i // per] >> (8 - depth * (i % per + 1))) & mask
                if ctype == 3:
                    r, g, b = palette[value * 3:value * 3 + 3]
                    a = trns[value] if trns and value < len(trns) else 255
                    row.append((r, g, b, a))
                else:
                    v = value * 255 // mask
                    row.append((v, v, v, 255))
            pixels.append(row)
        return pixels

    for y in range(height):
        base = y * stride
        row = []
        for x in range(width):
            i = base + x * channels
            if ctype == 6:
                row.append(tuple(out[i:i + 4]))
            elif ctype == 2:
                row.append((out[i], out[i + 1], out[i + 2], 255))
            elif ctype == 4:
                row.append((out[i], out[i], out[i], out[i + 1]))
            elif ctype == 0:
                row.append((out[i], out[i], out[i], 255))
            else:
                r, g, b = palette[out[i] * 3:out[i] * 3 + 3]
                a = trns[out[i]] if trns and out[i] < len(trns) else 255
                row.append((r, g, b, a))
        pixels.append(row)
    return pixels


def write_png(path, pixels):
    """pixels: rows of RGBA tuples."""
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in pixels)

    def chunk(tag, body):
        c = tag + body
        return struct.pack(">I", len(body)) + c + struct.pack(">I", zlib.crc32(c))

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("wrote %s (%dx%d)" % (path, width, height))




def iron_palette():
    """Vanilla's own iron, darkest first: shadow, body, highlight."""
    counts = Counter(px for row in vanilla("block/iron_block.png") for px in row if px[3])
    tones = sorted((px for px, _ in counts.most_common(6)),
                   key=lambda p: 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2])
    return tones[0], tones[len(tones) // 2], tones[-1]


SIZE = 64
CENTRE = SIZE / 2.0


def darker(px, factor):
    """Iron block is pale stock. The pick is the same metal worked thin and oiled, which is the
    honest reason it is drawn darker: it also happens to be the only way it reads against the
    plate it lies on."""
    return tuple(min(255, int(c * factor)) for c in px[:3]) + (px[3],)

RECESS = (18, 16, 22, 255)
VOID = (0, 0, 0, 255)


def blank():
    return [[CLEAR] * SIZE for _ in range(SIZE)]


def disc(sprite, radius, colour, inner=0.0):
    """A filled circle, or a ring when an inner radius is given."""
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x + 0.5 - CENTRE, y + 0.5 - CENTRE
            d = math.hypot(dx, dy)
            if inner <= d <= radius:
                sprite[y][x] = colour


def lit_edge(sprite, radius, light, dark, width=2.0):
    """A bevel: the rim catches light from the top left and falls into shadow opposite."""
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x + 0.5 - CENTRE, y + 0.5 - CENTRE
            d = math.hypot(dx, dy)
            if not (radius - width <= d <= radius):
                continue
            sprite[y][x] = light if dx + dy < 0 else dark


def bar(sprite, half_width, near, far, colour):
    """A vertical bar running up from the centre, which is the axis everything turns about."""
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x + 0.5 - CENTRE, y + 0.5 - CENTRE
            if abs(dx) <= half_width and -far <= dy <= -near:
                sprite[y][x] = colour


def build_face():
    """The plate: an iron ring around a dark recess the cylinder sits in."""
    shadow, body, light = iron_palette()
    sprite = blank()
    disc(sprite, 30.0, body)
    lit_edge(sprite, 30.0, light, shadow, width=3.0)
    disc(sprite, 21.0, shadow)
    disc(sprite, 19.5, RECESS)
    return sprite


def build_cylinder():
    """The plug, with the keyway cut across it. This is the part that visibly turns."""
    shadow, body, light = iron_palette()
    sprite = blank()
    disc(sprite, 18.0, body)
    lit_edge(sprite, 18.0, light, shadow, width=2.0)
    bar(sprite, 3.0, 0.0, 15.0, VOID)
    bar(sprite, 3.0, 0.0, 15.0, VOID)
    # The keyway runs right through, so it reads as a slot rather than a notch
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x + 0.5 - CENTRE, y + 0.5 - CENTRE
            if abs(dx) <= 3.0 and abs(dy) <= 15.0 and math.hypot(dx, dy) <= 16.0:
                sprite[y][x] = VOID
    return sprite


def build_pick():
    """The pick: a shaft up from the keyway with a hooked tip, so which end is which is obvious.

    Drawn in the plate's shadow tone rather than its highlight. A pick the same brightness as
    the plate it lies on is a pick nobody can see, and where it is pointing is the one thing
    this screen has to say."""
    shadow, body, light = iron_palette()
    edge, core = darker(shadow, 0.35), darker(body, 0.62)
    sprite = blank()
    bar(sprite, 1.5, 2.0, 26.0, edge)
    bar(sprite, 0.5, 2.0, 26.0, core)
    # Hook: two pixels stepping off the tip, the tell that this end does the work
    for step in range(3):
        x = int(CENTRE + 1.5 + step)
        y = int(CENTRE - 26.0 + step)
        for w in range(2):
            sprite[y + w][x] = edge
            sprite[y + w][x - 1] = core
    return sprite


# The pick, drawn rather than plotted: a shape this small is read as a silhouette, and a
# silhouette is easier to get right by looking at it than by describing it in arithmetic.
#
#   .  nothing        s  steel shaft, lit side      S  steel shaft, shadow side
#   h  hook tip       g  grip, lit                  G  grip, shadow      b  bolster
PICK = [
    "................",
    "..........sss...",
    "..........h..S..",
    ".........s..S...",
    "........s..S....",
    ".......s..S.....",
    "......s..S......",
    ".....s..S.......",
    "....s..S........",
    "...bs.S.........",
    "..bggS..........",
    ".bgGg...........",
    ".bgGg...........",
    "..bgG...........",
    "...bb...........",
    "................",
]


def build_item():
    """The pick as you carry it.

    Three things separate a pick from a stick at sixteen pixels, and the first version of this had
    none of them: a shaft thin enough to look like wire rather than a branch, a grip in a different
    material so the eye finds two parts instead of one uniform bar, and a hook at the working end
    big enough to survive being drawn at this size. The hook is deliberately out of scale - a
    true-to-life one would be two pixels nobody would ever notice.
    """
    shadow, body, light = iron_palette()
    steel_lit = light
    steel_dark = darker(body, 0.72)
    hook = light
    grip_lit = (116, 84, 58, 255)
    grip_dark = (78, 54, 36, 255)
    bolster = darker(shadow, 0.55)

    paint = {"s": steel_lit, "S": steel_dark, "h": hook,
             "g": grip_lit, "G": grip_dark, "b": bolster}

    sprite = [[CLEAR] * 16 for _ in range(16)]
    for y, row in enumerate(PICK):
        for x, key in enumerate(row):
            if key != ".":
                sprite[y][x] = paint[key]
    return sprite


if __name__ == "__main__":
    write_png(os.path.join(GUI, "lock_face.png"), build_face())
    write_png(os.path.join(GUI, "lock_cylinder.png"), build_cylinder())
    write_png(os.path.join(GUI, "lock_pick.png"), build_pick())
    write_png(os.path.join(ITEM, "lockpick.png"), build_item())
