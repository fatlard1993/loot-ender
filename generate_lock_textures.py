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


# Art pixels. The screen draws these sprites at twice this, so every pixel here lands as a
# chunky two-by-two, the way a block's sixteen do when you stand near it. Drawing at the size
# shown gave a smooth dial that could have come out of any game; this one is made of the same
# stuff as the chest behind it.
SIZE = 64
CENTRE = SIZE / 2.0


def darker(px, factor):
    """Iron block is pale stock. The pick is the same metal worked thin and oiled, which is the
    honest reason it is drawn darker: it also happens to be the only way it reads against the
    plate it lies on."""
    return tuple(min(255, int(c * factor)) for c in px[:3]) + (px[3],)

VOID = (16, 16, 16, 255)
STEEL_LIT = (226, 226, 230, 255)
STEEL_DARK = (96, 96, 104, 255)


def blank():
    return [[CLEAR] * SIZE for _ in range(SIZE)]


def paint(sprite, colour_at):
    """Every pixel, by its offset from the centre: the axis everything turns about."""
    for y in range(SIZE):
        for x in range(SIZE):
            colour = colour_at(x + 0.5 - CENTRE, y + 0.5 - CENTRE)
            if colour is not None:
                sprite[y][x] = colour


def latch_tones():
    """The chest's own latch, read off the sheet the block is painted with: its front face is
    two pixels by four, dark at the top and light at the bottom, and its side a column lighter
    still. Darkest first, so the plate here is a bigger piece of the same metal."""
    sheet = vanilla("entity/chest/normal.png")
    tones = sorted({sheet[y][x][:3] for y in range(1, 5) for x in range(1, 3)})
    side = sorted({sheet[y][2][:3] for y in range(1, 5)})
    return [t + (255,) for t in tones], [t + (255,) for t in side]


# The lock plate, as the latch is on the block: two wide by four tall, here in art pixels
LATCH_HALF_W = 10.0
LATCH_HALF_H = 20.0
PLUG_RADIUS = 9.0


def band(dy, tones):
    """Which of the latch's top-to-bottom bands a row falls in."""
    index = int((dy + LATCH_HALF_H) / (2 * LATCH_HALF_H) * 4)
    return tones[max(0, min(index, len(tones) - 1))]


def build_face():
    """The plate: the chest's latch, grown. The same four bands of grey top to bottom, the
    lighter side-column along its right edge, and a round recess cut for the plug."""
    front, side = latch_tones()
    sprite = blank()

    def plate(dx, dy):
        if abs(dx) > LATCH_HALF_W or abs(dy) > LATCH_HALF_H:
            return None
        d = math.hypot(dx, dy)
        if d <= PLUG_RADIUS:
            return VOID
        if d <= PLUG_RADIUS + 1.0:
            return front[0]
        return band(dy, side if dx > LATCH_HALF_W - 3.0 else front)
    paint(sprite, plate)
    return sprite


def build_cylinder():
    """The plug, with the keyway cut into it. This is the part that visibly turns, so it is
    banded top and bottom in the latch's greys: a plain disc turned about its centre is a disc
    that has not moved."""
    front, side = latch_tones()
    dark, mid, light = front[0], front[len(front) // 2], front[-1]
    sprite = blank()

    def plug(dx, dy):
        d = math.hypot(dx, dy)
        if d > PLUG_RADIUS:
            return None
        if d > PLUG_RADIUS - 1.5:
            return side[-1] if dx + dy < 0 else dark
        return mid if dy < 0 else light
    paint(sprite, plug)

    # A throat at the centre and a slot up out of it: the shape a key is, so the pick reads as
    # sitting in a keyhole rather than lying on a plate. Outlined in the dark grey, then cut.
    def keyway(dx, dy, throat, half):
        return math.hypot(dx, dy) <= throat or (abs(dx) <= half and -6.5 <= dy <= 0.0)
    paint(sprite, lambda dx, dy: dark if keyway(dx, dy, 4.0, 2.5) else None)
    paint(sprite, lambda dx, dy: VOID if keyway(dx, dy, 3.0, 1.5) else None)
    return sprite


OUTLINE = (40, 40, 46, 255)


def draw_wire(sprite, segments, offset=(0.0, 0.0), radius=0.6, rim=1.35):
    """A run of iron wire: a dark outline a pixel outside the metal, and two tones on the
    metal with the light on the upper-left side. The same drawing as the item in the hotbar,
    only larger, so the pick on the chest is the pick in the hand."""
    ox, oy = offset

    def nearest(px, py):
        best = None
        for (sx, sy, ex, ey) in segments:
            sx, sy, ex, ey = sx + ox, sy + oy, ex + ox, ey + oy
            vx, vy = ex - sx, ey - sy
            t = max(0.0, min(1.0, ((px - sx) * vx + (py - sy) * vy) / (vx * vx + vy * vy)))
            qx, qy = sx + t * vx, sy + t * vy
            d = math.hypot(px - qx, py - qy)
            if best is None or d < best[0]:
                best = (d, px - qx, py - qy)
        return best

    for pass_ in ("outline", "fill"):
        for y in range(SIZE):
            for x in range(SIZE):
                px, py = x + 0.5 - CENTRE, y + 0.5 - CENTRE
                d, ax, ay = nearest(px, py)
                if pass_ == "outline" and d <= rim:
                    sprite[y][x] = OUTLINE
                elif pass_ == "fill" and d <= radius:
                    sprite[y][x] = STEEL_LIT if ax + ay < 0 else STEEL_DARK


# The pick, as runs of wire about the centre it turns on, in the item icon's own proportions
# and line weight: a shaft the icon's length, the near end turned over the icon's two pixels
# and change, the hook the icon's three and a bit, swung off to the side. The shaft runs up
# the boundary between two pixel columns so it lands two pixels wide, one lit and one shaded,
# as the icon's does. The plate is drawn at the same pixel scale, so this is the pick in the
# hotbar, turned upright and put in a lock.
HOOK = (0.0, -1.0, 2.6, 1.2)
SHAFT = (0.0, -1.0, 0.0, -14.5)
BEND = (0.0, -14.5, 2.6, -14.5)


def build_pick():
    """The pick, in the same hand as the one in the hotbar: one bent iron wire, outlined and
    two-toned the way the game draws its tools."""
    sprite = blank()
    draw_wire(sprite, [HOOK, SHAFT, BEND])
    return sprite


def build_pick_broken():
    """The pick a moment after it snapped: the tip still in the keyhole, the rest come away
    in the hand, sheared through and sprung to one side. Drawn in the pick's place so the
    break is seen where the pick was, not read off a number going down."""
    sprite = blank()
    draw_wire(sprite, [HOOK, (0.0, -1.0, 0.0, -5.5)])
    draw_wire(sprite, [(0.0, -8.0, 0.0, -14.5), BEND], offset=(3.0, -2.0))
    return sprite


def build_item():
    """The pick as you carry it, drawn the way the game draws its own tools: a dark outline
    round everything and two tones on the metal with the light on the upper edge. It is one
    piece of iron wire, a nugget's worth: an eye bent at the near end to hold it by and the
    hook at the far end big enough to read at sixteen pixels. No grip, no second material.

    Plotted along one diagonal rather than pixelled by hand: at this size the outline has to
    sit exactly one pixel outside the metal on every row, and arithmetic keeps that promise
    where a hand drawing drifts.
    """
    size = 16
    sprite = [[CLEAR] * size for _ in range(size)]
    outline = (40, 40, 46, 255)
    steel_lit = (222, 222, 230, 255)
    steel = (150, 150, 162, 255)

    # Off the pixel centres by a third, so the shaft lands two pixels wide: one lit, one shaded
    ax, ay = 2.85, 11.85
    bx, by = 12.85, 1.85
    length = math.hypot(bx - ax, by - ay)
    ux, uy = (bx - ax) / length, (by - ay) / length
    nx, ny = -uy, ux

    def along(px, py):
        return ((px - ax) * ux + (py - ay) * uy) / length, (px - ax) * nx + (py - ay) * ny

    # The hook: off the far end, turning down and to the right
    hx, hy = bx + 2.6, by + 2.2

    def segment_dist(px, py, sx, sy, ex, ey):
        vx, vy = ex - sx, ey - sy
        t = max(0.0, min(1.0, ((px - sx) * vx + (py - sy) * vy) / (vx * vx + vy * vy)))
        return math.hypot(px - (sx + t * vx), py - (sy + t * vy))

    def hook_dist(px, py):
        return segment_dist(px, py, bx, by, hx, hy)

    # The bend: off the near end, square to the shaft, down and to the right
    def bend_dist(px, py):
        return segment_dist(px, py, ax, ay, ax + 2.4, ay + 2.4)

    for pass_ in ("outline", "fill"):
        for y in range(size):
            for x in range(size):
                px, py = x + 0.5, y + 0.5
                t, d = along(px, py)
                hd = hook_dist(px, py)
                bd = bend_dist(px, py)
                colour = None
                if pass_ == "outline":
                    if bd <= 1.35 or (-0.03 <= t <= 1.03 and abs(d) <= 1.35) or hd <= 1.35:
                        colour = outline
                else:
                    if bd <= 0.6:
                        colour = steel_lit if (px - ax) - (py - ay) > 0 else steel
                    elif 0.0 <= t <= 1.0 and abs(d) <= 0.6:
                        colour = steel_lit if d < 0 else steel
                    elif hd <= 0.6:
                        colour = steel_lit if py < by + (px - bx) * 0.7 else steel
                if colour is not None:
                    sprite[y][x] = colour
    return sprite


if __name__ == "__main__":
    write_png(os.path.join(GUI, "lock_face.png"), build_face())
    write_png(os.path.join(GUI, "lock_cylinder.png"), build_cylinder())
    write_png(os.path.join(GUI, "lock_pick.png"), build_pick())
    write_png(os.path.join(GUI, "lock_pick_broken.png"), build_pick_broken())
    write_png(os.path.join(ITEM, "lockpick.png"), build_item())
