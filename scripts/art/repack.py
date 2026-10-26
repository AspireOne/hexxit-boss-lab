"""Repacks the texture offsets of every box(...) call in WardenModel.java, in place.

Boxes that shared an offset keep sharing one. Run it after adding or resizing a box, then
repaint with paint.py.

Usage from the repository root: python scripts/art/repack.py [WardenModel.java]
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODEL = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'src/main/java/dev/hexxitlab/warden/WardenModel.java'
SIZE = 128
BOX = re.compile(r'(box\((\w+),\s*)(\d+),\s*(\d+)(,[^;]*?,\s*(\d+),\s*(\d+),\s*(\d+)\);)')

source = MODEL.read_text(newline='')
groups = {}
for match in BOX.finditer(source):
    key = (int(match.group(3)), int(match.group(4)))
    width, height, depth = int(match.group(6)), int(match.group(7)), int(match.group(8))
    groups.setdefault(key, (2 * (width + depth), depth + height))

# Shelf packing, tallest regions first.
items = sorted(groups.items(), key=lambda item: (-item[1][1], -item[1][0]))
x = y = row = 0
placed = {}
for key, (region_width, region_height) in items:
    if x + region_width > SIZE:
        x = 0
        y += row
        row = 0
    placed[key] = (x, y)
    x += region_width
    row = max(row, region_height)
if y + row > SIZE:
    raise SystemExit('Boxes need %d rows; the sheet has %d' % (y + row, SIZE))

source = BOX.sub(
    lambda match: '%s%d, %d%s' % (
        match.group(1), *placed[(int(match.group(3)), int(match.group(4)))], match.group(5)),
    source)
MODEL.write_text(source, newline='')
print('packed height', y + row)
