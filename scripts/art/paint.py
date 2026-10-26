"""Paints the Ashen Warden textures from the box layout in WardenModel.java.

Writes warden.png (base), and the additive glow masks warden_embers.png, warden_rage.png,
warden_runes.png and warden_cracks.png (whose alpha orders when each crack appears).

Usage from the repository root: python scripts/art/paint.py [WardenModel.java] [texture dir]
Needs Pillow. The fixed random seed makes the output reproducible.
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image

from uvboxes import faces, read_boxes

ROOT = Path(__file__).resolve().parents[2]
MODEL = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'src/main/java/dev/hexxitlab/warden/WardenModel.java'
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / 'src/main/resources/mods/hexxitbosslab/textures/entity'
SIZE = 128

# Materials by owning part and box size, so repacking offsets never changes the art.
MATERIAL = {
    ('hips', 11, 2, 7): 'belt', ('hips', 1, 6, 6): 'tasset',
    ('torso', 13, 8, 8): 'chest', ('torso', 9, 6, 1): 'ridge', ('torso', 9, 5, 6): 'abdomen',
    ('torso', 8, 2, 7): 'gorget', ('torso', 3, 3, 1): 'core',
    ('head', 7, 7, 7): 'helm', ('head', 6, 4, 1): 'face', ('head', 8, 1, 2): 'brow',
    ('head', 1, 2, 7): 'crest', ('cape', 12, 20, 1): 'cape',
    ('sword', 1, 1, 5): 'grip', ('sword', 2, 2, 2): 'pommel', ('sword', 2, 8, 2): 'guard',
    ('sword', 1, 3, 16): 'blade', ('sword', 1, 2, 2): 'steel', ('sword', 1, 1, 2): 'steel',
    ('shield', 12, 20, 1): 'shield', ('shield', 4, 5, 1): 'boss', ('shield', 13, 1, 2): 'rim',
    ('shield', 1, 20, 2): 'rim',
    ('upper', 4, 6, 4): 'mail', ('upper', 7, 4, 8): 'pauldron', ('upper', 6, 2, 6): 'plate',
    ('forearm', 4, 5, 4): 'vambrace', ('forearm', 5, 2, 5): 'trim', ('forearm', 3, 3, 3): 'fist',
    ('thigh', 5, 7, 5): 'mail', ('thigh', 5, 7, 1): 'tasset', ('shin', 5, 6, 5): 'greave',
    ('shin', 3, 3, 2): 'trim', ('shin', 5, 2, 7): 'sabaton',
}

IRON = (54, 50, 48)
IRON_HI = (92, 84, 78)
IRON_LO = (30, 27, 26)
ASH = (112, 106, 100)
BRONZE = (126, 86, 44)
BRONZE_HI = (186, 136, 72)
BRONZE_LO = (74, 48, 24)
MAIL = (44, 42, 42)
MAIL_HI = (78, 74, 70)
CLOTH = (42, 33, 31)
CLOTH_LO = (26, 20, 19)
STEEL = (146, 150, 156)
STEEL_HI = (206, 210, 216)
STEEL_LO = (96, 100, 106)
LEATHER = (66, 44, 30)
SLIT = (10, 8, 6)

EMBER = (255, 120, 30)
EMBER_HOT = (255, 200, 110)
RAGE = (255, 60, 20)
RAGE_HOT = (255, 160, 100)
RUNE = (255, 170, 70)

rng = random.Random(7)
base = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
embers = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 255))
rage = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 255))
runes = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 255))
cracks = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))


def jitter(color, amount):
    j = rng.randint(-amount, amount)
    return tuple(max(0, min(255, c + j)) for c in color[:3])


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def fill(img, rect, fn):
    x0, y0, fw, fh = rect
    for y in range(fh):
        for x in range(fw):
            c = fn(x, y, fw, fh)
            if c is not None:
                img.putpixel((x0 + x, y0 + y), c if len(c) == 4 else c + (255,))


def plated(lo, mid, hi, speck=True, bevel=True):
    def paint(x, y, fw, fh):
        c = jitter(mid, 5)
        if bevel and fh > 1 and y == 0:
            c = jitter(hi, 4)
        elif bevel and fw > 1 and x == 0:
            c = mix(c, hi, 0.45)
        elif bevel and fh > 2 and y == fh - 1:
            c = jitter(lo, 3)
        elif bevel and fw > 2 and x == fw - 1:
            c = mix(c, lo, 0.5)
        if speck and rng.random() < 0.06:
            c = mix(c, ASH, 0.6)
        return c
    return paint


def paint_box(material, u, v, w, h, d):
    rects = faces(u, v, w, h, d)
    for side, rect in rects.items():
        x0, y0, fw, fh = rect
        if fw <= 0 or fh <= 0:
            continue
        shade = {'top': 1.12, 'bottom': 0.7}.get(side, 1.0)
        paint_face(material, side, rect, w, h, d)
        if shade != 1.0:
            for y in range(fh):
                for x in range(fw):
                    r, g, b, a = base.getpixel((x0 + x, y0 + y))
                    base.putpixel((x0 + x, y0 + y), (min(255, int(r * shade)), min(255, int(g * shade)), min(255, int(b * shade)), a))


def glow(img, x, y, color):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        img.putpixel((x, y), color + (255,))


def paint_face(material, side, rect, w, h, d):
    x0, y0, fw, fh = rect
    if material in ('chest', 'pauldron', 'plate', 'vambrace', 'greave', 'sabaton', 'tasset', 'helm', 'face', 'ridge'):
        fill(base, rect, plated(IRON_LO, IRON, IRON_HI))
    elif material in ('belt', 'gorget', 'brow', 'trim', 'guard', 'pommel', 'rim', 'boss', 'crest'):
        fill(base, rect, plated(BRONZE_LO, BRONZE, BRONZE_HI, speck=False))
    elif material in ('mail', 'abdomen'):
        def mail(x, y, fw, fh):
            ring = (x + (y % 2)) % 2 == 0
            return jitter(MAIL_HI if ring else MAIL, 4)
        fill(base, rect, mail)
    elif material == 'fist':
        def fist(x, y, fw, fh):
            return jitter(IRON_HI if y % 2 == 0 else IRON, 4)
        fill(base, rect, fist)
    elif material == 'grip':
        fill(base, rect, lambda x, y, fw, fh: jitter(LEATHER if (x + y) % 2 else mix(LEATHER, (20, 12, 8), 0.4), 3))
    elif material in ('steel', 'blade'):
        def steel(x, y, fw, fh):
            if side in ('right', 'left') and material == 'blade':
                # The flat of the blade: bright edges, a dark fuller down the middle.
                if y == 0 or y == fh - 1:
                    return jitter(STEEL_HI, 3)
                return jitter(STEEL_LO if y == fh // 2 and 2 < x < fw - 1 else STEEL, 3)
            return jitter(STEEL_HI if side in ('top', 'bottom') else STEEL, 3)
        fill(base, rect, steel)
    elif material == 'core':
        fill(base, rect, lambda x, y, fw, fh: mix(EMBER_HOT, EMBER, abs(x - fw / 2 + 0.5) / max(1, fw / 2)))
    elif material == 'cape':
        def cloth(x, y, fw, fh):
            if side in ('front', 'back'):
                # Tattered, burnt hem: holes eat upwards from the bottom edge.
                hem = fh - 1 - y
                if hem < 3 and rng.random() < (0.55 - hem * 0.18):
                    return (0, 0, 0, 0)
                c = CLOTH_LO if x % 4 == 0 else CLOTH
                return jitter(c, 4)
            return jitter(CLOTH_LO, 3)
        fill(base, rect, cloth)
    elif material == 'shield':
        def shield(x, y, fw, fh):
            if side == 'front':
                return shield_face(x, y, fw, fh)
            if side == 'back':
                # Planks and a leather strap across the back.
                if 8 <= y <= 9 or 12 <= y <= 13:
                    return jitter(LEATHER, 3)
                return jitter((58, 44, 34) if x % 3 else (44, 33, 25), 3)
            return jitter(BRONZE_LO, 3)
        fill(base, rect, shield)
    else:
        fill(base, rect, plated(IRON_LO, IRON, IRON_HI))


def shield_face(x, y, fw, fh):
    """Dark iron face with a bronze sunburst sigil around a central eye."""
    cx, cy = (fw - 1) / 2, (fh - 1) / 2 + 0.5
    dx, dy = x - cx, (y - cy) * 0.8
    r = math.hypot(dx, dy)
    c = jitter(IRON, 5)
    if 3.2 < r < 4.4:
        c = jitter(BRONZE_HI, 4)
    if r < 1.6:
        c = jitter(BRONZE, 4)
    angle = math.atan2(dy, dx)
    if 4.4 <= r < 7.5 and abs(math.sin(angle * 4)) > 0.92:
        c = jitter(BRONZE, 3)
    if x in (0, fw - 1) or y in (0, fh - 1):
        c = jitter(IRON_LO, 3)
    if rng.random() < 0.05:
        c = mix(c, ASH, 0.5)
    return c


boxes = read_boxes(MODEL.read_text(), MATERIAL)

seen = set()
for part, u, v, w, h, d in boxes:
    if (u, v) in seen:
        continue
    seen.add((u, v))
    paint_box(MATERIAL[(part, w, h, d)], u, v, w, h, d)


def region(u, v, w, h, d, side):
    return faces(u, v, w, h, d)[side]


def lookup(material):
    return [(u, v, w, h, d) for part, u, v, w, h, d in boxes if MATERIAL[(part, w, h, d)] == material][0]


# Details on the base: rivets, visor slit, breathing holes, buckle.
def px(x, y, c):
    base.putpixel((x, y), c + (255,))


helm = lookup('helm')
hx, hy, hw, hh = region(*helm, 'front')
for x in range(hw):
    px(hx + x, hy + 2, SLIT)
face = lookup('face')
fx, fy, fw, fh = region(*face, 'front')
for x in (1, 4):
    px(fx + x, fy + 2, SLIT)
    px(fx + x, fy + 3, (20, 16, 14))
for material in ('pauldron', 'chest'):
    box = lookup(material)
    x, y, w, h = region(*box, 'front')
    for rx, ry in ((1, 1), (w - 2, 1)):
        px(x + rx, y + ry, BRONZE_HI)
belt = lookup('belt')
bx, by, bw, bh = region(*belt, 'front')
for x in range(bw // 2 - 1, bw // 2 + 2):
    for y in range(bh):
        px(bx + x, by + y, BRONZE_HI if y == 0 else (150, 110, 60))

# Ember glows: the visor, the core, seams through the breastplate, the blade edge and the cape hem.
def mark(img, x, y, color):
    glow(img, x, y, color)


for img, eye, hot, veins in ((embers, EMBER, EMBER_HOT, 6), (rage, RAGE, RAGE_HOT, 16)):
    for x in (1, 2, 4, 5):
        mark(img, hx + x, hy + 2, hot if img is rage else eye)
    if img is rage:
        # Burning tears below the eyes, and a slit blazing end to end.
        for x in (0, 3, 6):
            mark(img, hx + x, hy + 2, eye)
        for x in (1, 4):
            mark(img, fx + x, fy + 0, eye)
            mark(img, fx + x, fy + 1, RAGE)
    core = lookup('core')
    for side in ('front', 'top', 'bottom', 'right', 'left'):
        x, y, w, h = region(*core, side)
        for yy in range(h):
            for xx in range(w):
                mark(img, x + xx, y + yy, hot)
    ridge = lookup('ridge')
    x, y, w, h = region(*ridge, 'front')
    vein_rng = random.Random(11 if img is embers else 13)
    for _ in range(veins):
        cx, cy = vein_rng.randrange(w), vein_rng.randrange(h)
        for _ in range(vein_rng.randint(2, 4)):
            mark(img, x + cx, y + cy, eye)
            cx = max(0, min(w - 1, cx + vein_rng.choice((-1, 0, 1))))
            cy = max(0, min(h - 1, cy + vein_rng.choice((-1, 1))))
    # Seams where plates meet.
    for material in ('pauldron', 'plate', 'greave', 'chest'):
        box = lookup(material)
        for side in ('front', 'right', 'left'):
            x, y, w, h = region(*box, side)
            if img is embers and material != 'plate':
                continue
            for xx in range(w):
                if vein_rng.random() < (0.5 if img is rage else 0.35):
                    mark(img, x + xx, y + h - 1, eye)
    blade = lookup('blade')
    for side in ('top', 'bottom'):
        x, y, w, h = region(*blade, side)
        for yy in range(h):
            for xx in range(w):
                mark(img, x + xx, y + yy, mix(eye, (60, 20, 5), yy / max(1, h)) if img is embers else eye)
    cape = lookup('cape')
    for side in ('front', 'back'):
        x, y, w, h = region(*cape, side)
        for xx in range(w):
            for yy in range(h - 4, h):
                if base.getpixel((x + xx, y + yy))[3] > 0 and (
                    yy == h - 1 or base.getpixel((x + xx, y + yy + 1))[3] == 0):
                    mark(img, x + xx, y + yy, eye)

# Shield runes: a ring of glyphs around the rim and the central eye.
rim_boxes = [(u, v, w, h, d) for part, u, v, w, h, d in boxes if MATERIAL[(part, w, h, d)] == 'rim']
for u, v, w, h, d in set(rim_boxes):
    x, y, fw, fh = region(u, v, w, h, d, 'front')
    for yy in range(fh):
        for xx in range(fw):
            k = xx + yy
            if k % 3 != 2:
                mark(runes, x + xx, y + yy, RUNE if k % 3 == 0 else mix(RUNE, (120, 50, 10), 0.5))
boss = lookup('boss')
x, y, w, h = region(*boss, 'front')
for yy in range(h):
    for xx in range(w):
        if (xx in (1, 2)) and yy in (1, 2, 3):
            mark(runes, x + xx, y + yy, (255, 220, 150) if yy == 2 else RUNE)
shield = lookup('shield')
x, y, w, h = region(*shield, 'front')
for yy in range(h):
    for xx in range(w):
        r, g, b, a = base.getpixel((x + xx, y + yy))
        if (r, g, b) != (0, 0, 0) and abs(r - BRONZE[0]) < 12 and abs(g - BRONZE[1]) < 12:
            mark(runes, x + xx, y + yy, mix(RUNE, (90, 40, 10), 0.55))

# Posture cracks: random walks that start near the core and spread outward. Alpha stores the
# order: high alpha cracks appear first, as the reveal threshold falls with rising posture.
order = [('ridge', 0.0), ('chest', 0.1), ('abdomen', 0.35), ('pauldron', 0.45), ('helm', 0.6),
         ('plate', 0.55), ('vambrace', 0.7), ('tasset', 0.65), ('greave', 0.8), ('face', 0.75)]
crack_rng = random.Random(5)
for material, start in order:
    box = lookup(material)
    for side in ('front', 'right', 'left', 'back', 'top'):
        x, y, w, h = region(*box, side)
        if w <= 0 or h <= 0:
            continue
        walks = max(1, (w * h) // 18)
        for walk in range(walks):
            cx, cy = crack_rng.randrange(w), crack_rng.randrange(h)
            steps = crack_rng.randint(3, 7)
            for step in range(steps):
                appear = start + (walk / walks) * 0.15 + step * 0.025 + (0.1 if side != 'front' else 0)
                alpha = max(8, int(255 * (1 - min(0.97, appear))))
                old = cracks.getpixel((x + cx, y + cy))
                if old[3] < alpha:
                    hot = step < 2
                    cracks.putpixel((x + cx, y + cy), (255, 230 if hot else 170, 120 if hot else 60, alpha))
                cx = max(0, min(w - 1, cx + crack_rng.choice((-1, 0, 1))))
                cy = max(0, min(h - 1, cy + crack_rng.choice((-1, 1))))

OUT.mkdir(parents=True, exist_ok=True)
base.save(OUT / 'warden.png')
embers.save(OUT / 'warden_embers.png')
rage.save(OUT / 'warden_rage.png')
runes.save(OUT / 'warden_runes.png')
cracks.save(OUT / 'warden_cracks.png')
print('painted', len(seen), 'boxes')
