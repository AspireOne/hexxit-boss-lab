"""Paints the Hexbound Sovereign textures from the box layout in SovereignModel.java.

Writes sovereign.png (base), the additive glow masks sovereign_glow.png (hex runes, tinted by its
state in game) and sovereign_heart.png (core, eyes, gems and orb), and sovereign_fissures.png,
whose alpha orders when each fissure opens as it loses health.

Usage from the repository root: python scripts/art/sovereign_paint.py [SovereignModel.java] [texture dir]
Needs Pillow. The fixed random seed makes the output reproducible.
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image

from uvboxes import faces, read_boxes

ROOT = Path(__file__).resolve().parents[2]
MODEL = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'src/main/java/dev/hexxitlab/sovereign/SovereignModel.java'
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / 'src/main/resources/mods/hexxitbosslab/textures/entity'
SIZE = 128

# Materials by owning part and box size, so repacking offsets never changes the art.
MATERIAL = {
    ('body', 12, 8, 8): 'chest', ('body', 14, 3, 9): 'yoke', ('body', 9, 2, 7): 'waist',
    ('body', 10, 2, 8): 'belt', ('body', 6, 2, 6): 'gorget', ('body', 4, 5, 1): 'core',
    ('head', 7, 8, 7): 'void', ('head', 9, 9, 7): 'cowl',
    ('sceptreSpin', 1, 17, 1): 'rod', ('sceptreSpin', 2, 2, 2): 'gold',
    ('sceptreSpin', 2, 1, 2): 'gold', ('sceptreSpin', 3, 4, 3): 'orb',
    ('sceptreSpin', 6, 2, 1): 'gold', ('sceptreSpin', 1, 2, 6): 'gold',
    ('sceptreSpin', 1, 3, 1): 'tip',
    ('robe1', 11, 4, 8): 'mantle', ('robe2', 9, 4, 7): 'robe', ('robe3', 7, 4, 5): 'robe',
    ('robe4', 4, 4, 3): 'hem',
    ('crownSegment', 9, 3, 1): 'band', ('crownSegment', 1, 3, 1): 'prong',
    ('crownSegment', 2, 5, 1): 'spire', ('crownSegment', 1, 2, 1): 'prong',
    ('crownSegment', 2, 2, 1): 'gem',
    ('haloPlate', 4, 2, 1): 'tablet', ('haloSpike', 1, 4, 1): 'ray',
    ('shard', 2, 6, 2): 'crystal', ('shard', 1, 4, 1): 'crystal',
    ('plate', 4, 8, 1): 'plate',
    ('pauldron', 7, 4, 10): 'shell', ('pauldron', 5, 2, 7): 'scale', ('pauldron', 2, 5, 2): 'horn',
    ('pauldron', 8, 1, 11): 'gold',
    ('mask', 3, 8, 1): 'mask',
    ('hand', 5, 4, 5): 'cuff', ('hand', 6, 1, 6): 'gold', ('hand', 4, 3, 5): 'palm',
    ('hand', 1, 4, 1): 'claw',
}

OBSIDIAN = (36, 28, 50)
OBSIDIAN_HI = (74, 62, 98)
OBSIDIAN_LO = (18, 13, 27)
GOLD = (192, 144, 58)
GOLD_HI = (246, 212, 118)
GOLD_LO = (116, 78, 28)
VELVET = (82, 28, 102)
VELVET_HI = (116, 48, 138)
VELVET_LO = (44, 12, 58)
IVORY = (240, 234, 221)
IVORY_HI = (252, 249, 242)
IVORY_LO = (196, 186, 168)
VOID = (8, 4, 14)
CRYSTAL = (54, 26, 86)
CRYSTAL_HI = (132, 82, 186)
GEM = (156, 46, 196)
DUST = (96, 88, 112)

# Glow masks are grey: the game tints them.
RUNE = (255, 255, 255)
RUNE_DIM = (120, 120, 120)
HEART = (255, 236, 255)
HEART_DIM = (150, 96, 190)

rng = random.Random(23)
base = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
glow = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 255))
heart = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 255))
fissures = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))


def jitter(color, amount):
    j = rng.randint(-amount, amount)
    return tuple(max(0, min(255, c + j)) for c in color[:3])


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def put(img, x, y, color):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        img.putpixel((x, y), color if len(color) == 4 else color + (255,))


def fill(img, rect, fn):
    x0, y0, fw, fh = rect
    for y in range(fh):
        for x in range(fw):
            c = fn(x, y, fw, fh)
            if c is not None:
                put(img, x0 + x, y0 + y, c)


def plated(lo, mid, hi, speck=0.05):
    """Bevelled plate: light top and left edges, dark bottom and right, with dust specks."""
    def paint(x, y, fw, fh):
        c = jitter(mid, 4)
        if fh > 1 and y == 0:
            c = jitter(hi, 4)
        elif fw > 1 and x == 0:
            c = mix(c, hi, 0.4)
        elif fh > 2 and y == fh - 1:
            c = jitter(lo, 3)
        elif fw > 2 and x == fw - 1:
            c = mix(c, lo, 0.5)
        if rng.random() < speck:
            c = mix(c, DUST, 0.5)
        return c
    return paint


def gilded(lo, mid, hi):
    """Gold: a polished highlight band near the top, darker towards the bottom."""
    def paint(x, y, fw, fh):
        t = y / max(1, fh - 1)
        c = mix(hi, mid, min(1, t * 2)) if t < 0.5 else mix(mid, lo, (t - 0.5) * 1.4)
        if fw > 2 and x == fw - 1:
            c = mix(c, lo, 0.4)
        return jitter(c, 5)
    return paint


def velvet(x, y, fw, fh):
    """Heavy cloth hanging in folds."""
    fold = math.sin(x * 1.3) * 0.5 + 0.5
    return jitter(mix(VELVET_LO, VELVET_HI, 0.25 + 0.55 * fold), 4)


def crystal(x, y, fw, fh):
    """Dark crystal with a lighter facet running down it."""
    facet = (x + y) % 3 == 0
    return jitter(CRYSTAL_HI if facet else CRYSTAL, 6)


def border(rect, color, sides='tblr'):
    x0, y0, fw, fh = rect
    for x in range(fw):
        if 't' in sides:
            put(base, x0 + x, y0, jitter(color, 4))
        if 'b' in sides:
            put(base, x0 + x, y0 + fh - 1, jitter(color, 4))
    for y in range(fh):
        if 'l' in sides:
            put(base, x0, y0 + y, jitter(color, 4))
        if 'r' in sides:
            put(base, x0 + fw - 1, y0 + y, jitter(color, 4))


def shade(rect, factor):
    x0, y0, fw, fh = rect
    for y in range(fh):
        for x in range(fw):
            r, g, b, a = base.getpixel((x0 + x, y0 + y))
            put(base, x0 + x, y0 + y, (min(255, int(r * factor)), min(255, int(g * factor)), min(255, int(b * factor)), a))


def paint_box(material, u, v, w, h, d):
    for side, rect in faces(u, v, w, h, d).items():
        if rect[2] <= 0 or rect[3] <= 0:
            continue
        paint_face(material, side, rect)
        factor = {'top': 1.12, 'bottom': 0.7}.get(side)
        if factor:
            shade(rect, factor)


def paint_face(material, side, rect):
    x0, y0, fw, fh = rect
    if material in ('chest', 'yoke', 'shell', 'cuff', 'plate', 'waist'):
        fill(base, rect, plated(OBSIDIAN_LO, OBSIDIAN, OBSIDIAN_HI))
        if material in ('chest', 'yoke', 'plate') and side != 'bottom':
            border(rect, GOLD_HI if side == 'top' else GOLD, 't' if side != 'front' else 'tb')
        if material == 'shell' and side in ('front', 'back', 'right', 'left'):
            border(rect, GOLD, 'b')
        if material == 'cuff' and side not in ('top', 'bottom'):
            border(rect, GOLD_LO, 'b')
    elif material in ('gold', 'belt', 'gorget', 'band', 'prong', 'spire', 'horn', 'tip', 'ray'):
        fill(base, rect, gilded(GOLD_LO, GOLD, GOLD_HI))
        if material == 'horn' and side not in ('top', 'bottom'):
            # Horns blacken towards their roots.
            for y in range(fh):
                for x in range(fw):
                    if y > fh // 2:
                        put(base, x0 + x, y0 + y, mix(base.getpixel((x0 + x, y0 + y)), OBSIDIAN, 0.6))
    elif material == 'scale':
        def scales(x, y, fw, fh):
            return jitter(OBSIDIAN_HI if (x + (y // 2) * 2) % 4 == 0 or y % 2 == 0 and x % 4 == 2 else OBSIDIAN, 4)
        fill(base, rect, scales)
    elif material == 'rod':
        fill(base, rect, lambda x, y, fw, fh: jitter(OBSIDIAN if y % 5 == 2 else (GOLD_HI if x == 0 else GOLD), 4))
    elif material in ('orb', 'core', 'gem'):
        def gemstone(x, y, fw, fh):
            cx, cy = (fw - 1) / 2, (fh - 1) / 2
            r = math.hypot(x - cx, y - cy) / max(1, math.hypot(cx, cy))
            return jitter(mix((236, 180, 255), GEM, min(1, r * 1.2)), 4)
        fill(base, rect, gemstone)
        if material == 'orb':
            # The cage around the orb: gold bars on every edge.
            border(rect, GOLD, 'tblr')
    elif material == 'void':
        fill(base, rect, lambda x, y, fw, fh: jitter(VOID, 3))
    elif material == 'cowl':
        fill(base, rect, velvet)
        if side == 'front':
            # The hood's dark inside, framing the face.
            fill(base, rect, lambda x, y, fw, fh: jitter(VELVET_LO, 3))
        elif side in ('right', 'left'):
            border(rect, GOLD, 'b')
    elif material in ('mantle', 'robe', 'hem'):
        fill(base, rect, velvet)
        if material == 'mantle' and side in ('front', 'back', 'right', 'left'):
            border(rect, GOLD_HI, 't')
            # Embroidered gold diamonds along the mantle's top.
            for x in range(1, fw - 1, 3):
                put(base, x0 + x, y0 + 1, jitter(GOLD, 4))
        if material in ('robe', 'mantle') and side in ('front', 'back', 'right', 'left'):
            border(rect, GOLD_LO, 'b')
        if material == 'hem' and side in ('front', 'back', 'right', 'left'):
            # A tattered hem: holes eat upwards from the bottom edge.
            for x in range(fw):
                for y in range(fh):
                    edge = fh - 1 - y
                    if edge < 2 and rng.random() < 0.6 - edge * 0.3:
                        put(base, x0 + x, y0 + y, (0, 0, 0, 0))
    elif material == 'mask':
        fill(base, rect, plated(IVORY_LO, IVORY, IVORY_HI, speck=0.02))
        if side == 'front':
            paint_mask(rect)
        elif side == 'back':
            fill(base, rect, lambda x, y, fw, fh: jitter(VOID, 3))
    elif material == 'tablet':
        fill(base, rect, plated(OBSIDIAN_LO, OBSIDIAN, OBSIDIAN_HI, speck=0))
        if side in ('front', 'back'):
            border(rect, GOLD, 'tblr')
    elif material == 'palm':
        fill(base, rect, plated(OBSIDIAN_LO, OBSIDIAN, OBSIDIAN_HI))
        if side == 'front':
            border(rect, GOLD_LO, 'tb')
    elif material == 'claw':
        def claw(x, y, fw, fh):
            return jitter(GOLD_HI if y == fh - 1 else GOLD if y == fh - 2 else OBSIDIAN, 4)
        fill(base, rect, claw)
    elif material == 'crystal':
        fill(base, rect, crystal)
    else:
        raise SystemExit('Unpainted material ' + material)


# One half of the porcelain face, outer edge on the left; the other half is its mirror.
# G gold brow, I porcelain, S shadow, E burning eye, T gold tear, . cut away.
MASK = [
    'GGG',
    'III',
    'IIS',
    'IEE',
    'III',
    'ITI',
    'ITI',
    '.II',
]
MASK_PAINT = {'G': GOLD_HI, 'S': IVORY_LO, 'E': VOID, 'T': GOLD}


def paint_mask(rect):
    x0, y0, fw, fh = rect
    for y, row in enumerate(MASK):
        for x, key in enumerate(row):
            if key == '.':
                put(base, x0 + x, y0 + y, (0, 0, 0, 0))
            elif key in MASK_PAINT:
                put(base, x0 + x, y0 + y, jitter(MASK_PAINT[key], 3))


# One chest plate's front, outer edge on the left: half a diamond rune that the closed pair
# completes. G gold, R glowing rune, O obsidian.
PLATE = [
    'GGGG',
    'GOOR',
    'GORO',
    'GROO',
    'GROO',
    'GORO',
    'GOOR',
    'GGGG',
]


boxes = read_boxes(MODEL.read_text(), MATERIAL)
seen = set()
for part, u, v, w, h, d in boxes:
    if (u, v) in seen:
        continue
    seen.add((u, v))
    paint_box(MATERIAL[(part, w, h, d)], u, v, w, h, d)


def lookup(material):
    return [(u, v, w, h, d) for part, u, v, w, h, d in boxes if MATERIAL[(part, w, h, d)] == material]


def region(box, side):
    return faces(*box)[side]


# Hex runes: tinted in game by what the Sovereign is doing.
for plate in lookup('plate'):
    x0, y0, fw, fh = region(plate, 'front')
    for y, row in enumerate(PLATE):
        for x, key in enumerate(row):
            if key == 'G':
                put(base, x0 + x, y0 + y, jitter(GOLD, 4))
            elif key == 'R':
                put(base, x0 + x, y0 + y, jitter(GOLD_LO, 4))
                put(glow, x0 + x, y0 + y, RUNE)
for chest in lookup('chest'):
    x0, y0, fw, fh = region(chest, 'back')
    cx, cy = (fw - 1) / 2, (fh - 1) / 2
    for y in range(fh):
        for x in range(fw):
            r = math.hypot(x - cx, (y - cy) * 1.2)
            if abs(r - 3) < 0.5 or (x - cx) == 0 and r < 3:
                put(glow, x0 + x, y0 + y, RUNE_DIM)
                put(base, x0 + x, y0 + y, jitter(GOLD_LO, 4))
for shell in lookup('shell'):
    for side in ('right', 'left', 'front', 'back'):
        x0, y0, fw, fh = region(shell, side)
        for x in range(1, fw - 1):
            if x % 3 != 0:
                put(glow, x0 + x, y0 + fh - 2, RUNE_DIM)
for palm in lookup('palm'):
    x0, y0, fw, fh = region(palm, 'front')
    for x, y in ((1, 0), (2, 0), (0, 1), (3, 1), (1, 2), (2, 2)):
        put(glow, x0 + x, y0 + y, RUNE)
    put(heart, x0 + 1, y0 + 1, HEART_DIM)
    put(heart, x0 + 2, y0 + 1, HEART_DIM)
for tablet in lookup('tablet'):
    for side in ('front', 'back'):
        x0, y0, fw, fh = region(tablet, side)
        put(glow, x0 + 1, y0, RUNE)
        put(glow, x0 + 2, y0 + 1, RUNE)
        put(glow, x0 + 1, y0 + 1, RUNE_DIM)
for box in lookup('crystal'):
    for side in ('front', 'back', 'right', 'left'):
        x0, y0, fw, fh = region(box, side)
        for y in range(fh):
            put(glow, x0 + (y % fw), y0 + y, RUNE if fw == 1 else RUNE_DIM)
for hem in lookup('hem') + lookup('robe'):
    for side in ('front', 'back', 'right', 'left'):
        x0, y0, fw, fh = region(hem, side)
        row = fh - 2 if hem in lookup('robe') else 0
        for x in range(fw):
            if (x + row) % 2 == 0 and base.getpixel((x0 + x, y0 + row))[3] > 0:
                put(glow, x0 + x, y0 + row, RUNE_DIM if hem in lookup('robe') else RUNE)
for void in lookup('void'):
    # Behind the mask, seen once it splits: two burning eyes.
    x0, y0, fw, fh = region(void, 'front')
    for x in (1, 2, fw - 3, fw - 2):
        put(heart, x0 + x, y0 + 3, HEART)
        put(heart, x0 + x, y0 + 4, HEART_DIM)
for mask in lookup('mask'):
    # The eyes burn through the porcelain.
    x0, y0, fw, fh = region(mask, 'front')
    for y, row in enumerate(MASK):
        for x, key in enumerate(row):
            if key == 'E':
                put(heart, x0 + x, y0 + y, HEART)
                put(glow, x0 + x, y0 + y, RUNE)
for rod in lookup('rod'):
    for side in ('front', 'back', 'right', 'left'):
        x0, y0, fw, fh = region(rod, side)
        for y in range(2, fh, 5):
            put(glow, x0, y0 + y, RUNE_DIM)

# The heart: the core, the sceptre's orb and the crown's gems burn with their own light.
for material in ('core', 'orb', 'gem'):
    for box in lookup(material):
        for side, rect in faces(*box).items():
            x0, y0, fw, fh = rect
            cx, cy = (fw - 1) / 2, (fh - 1) / 2
            for y in range(fh):
                for x in range(fw):
                    r = math.hypot(x - cx, y - cy) / max(1, math.hypot(cx, cy))
                    if material == 'orb' and (x in (0, fw - 1) or y in (0, fh - 1)):
                        continue
                    put(heart, x0 + x, y0 + y, mix(HEART, HEART_DIM, min(1, r)))

# Fissures: random walks across armour and porcelain. Alpha stores the order: high-alpha fissures
# open first, as the reveal threshold falls with lost health.
order = [('plate', 0.0), ('chest', 0.08), ('mask', 0.2), ('yoke', 0.3), ('shell', 0.4),
         ('cuff', 0.5), ('band', 0.55), ('tablet', 0.6), ('scale', 0.7), ('waist', 0.75)]
crack_rng = random.Random(29)
for material, start in order:
    for box in lookup(material):
        for side in ('front', 'right', 'left', 'back'):
            x0, y0, w, h = region(box, side)
            if w <= 0 or h <= 0:
                continue
            walks = max(1, (w * h) // 40)
            for walk in range(walks):
                cx, cy = crack_rng.randrange(w), crack_rng.randrange(h)
                for step in range(crack_rng.randint(2, 5)):
                    appear = start + (walk / walks) * 0.15 + step * 0.025 + (0.1 if side != 'front' else 0)
                    alpha = max(8, int(255 * (1 - min(0.97, appear))))
                    old = fissures.getpixel((x0 + cx, y0 + cy))
                    if old[3] < alpha:
                        fissures.putpixel((x0 + cx, y0 + cy), (255, 255, 255, alpha))
                    cx = max(0, min(w - 1, cx + crack_rng.choice((-1, 0, 1))))
                    cy = max(0, min(h - 1, cy + crack_rng.choice((-1, 1))))

OUT.mkdir(parents=True, exist_ok=True)
base.save(OUT / 'sovereign.png')
glow.save(OUT / 'sovereign_glow.png')
heart.save(OUT / 'sovereign_heart.png')
fissures.save(OUT / 'sovereign_fissures.png')
print('painted', len(seen), 'boxes')
