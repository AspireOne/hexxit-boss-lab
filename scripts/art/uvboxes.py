"""The box texture layout shared by the texture painters.

Models declare their boxes as box(part, u, v, x, y, z, w, h, d) calls; each box unwraps onto the
sheet in Minecraft's standard layout from its (u, v) offset.
"""
import re

BOX = re.compile(r'box\((\w+),\s*(\d+),\s*(\d+),[^;]*?,\s*(\d+),\s*(\d+),\s*(\d+)\);')


def read_boxes(source, material):
    """Every box in a model source as (part, u, v, w, h, d), checked against a material table."""
    boxes = []
    for match in BOX.finditer(source):
        part, u, v, w, h, d = match.group(1), *map(int, match.groups()[1:])
        if (part, w, h, d) not in material:
            raise SystemExit('No material for %s %dx%dx%d' % (part, w, h, d))
        boxes.append((part, u, v, w, h, d))
    return boxes


def faces(u, v, w, h, d):
    """Face rectangles (x, y, width, height) in the standard box unwrap."""
    return {
        'top': (u + d, v, w, d),
        'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h),       # model -X: the entity's right
        'front': (u + d, v + d, w, h),   # model -Z
        'left': (u + d + w, v + d, d, h),
        'back': (u + d + w + d, v + d, w, h),
    }
