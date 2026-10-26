"""Builds the README's boss strips from a gallery run: three cropped shots side by side.

    python scripts/art/readme_strips.py .lab/runs/<gallery run> sovereign sovereign-attacks

Each panel is a crop of an 854 x 480 gallery shot, scaled to the panel size, so pick shots
without HUD overlays. The Warden's strips predate this script.
"""
import sys
from pathlib import Path

from PIL import Image

PANEL = (420, 300)
GAP = 6
GAP_COLOUR = (13, 17, 23)
OUTPUT = Path(__file__).resolve().parents[2] / 'docs' / 'images'

# Strip name -> panels of (gallery shot, crop left, crop top, crop width); crops keep the panel's
# aspect ratio.
STRIPS = {
    'sovereign': [
        ('sov-p1-three-quarter', 142, 18, 560),
        ('sov-charge', 207, 13, 560),
        ('sov-p3-front', 150, 31, 560),
    ],
    'sovereign-attacks': [
        ('sov-lance-release', 120, 20, 560),
        ('sov-rush-late', 110, 0, 560),
        ('sov-blast-wide-night', 88, 0, 672),
    ],
}


def panel(run, shot, left, top, width):
    matches = list(run.glob('*-GALLERY-*-%s.png' % shot))
    if len(matches) != 1:
        raise SystemExit('Expected one %s shot in %s, found %d' % (shot, run, len(matches)))
    image = Image.open(matches[0]).convert('RGB')
    height = width * PANEL[1] // PANEL[0]
    if left + width > image.width or top + height > image.height:
        raise SystemExit('Crop of %s falls outside the %dx%d shot' % ((shot,) + image.size))
    return image.crop((left, top, left + width, top + height)).resize(PANEL, Image.LANCZOS)


def main():
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    run = Path(sys.argv[1])
    for name in sys.argv[2:]:
        panels = [panel(run, *spec) for spec in STRIPS[name]]
        strip = Image.new('RGB', (PANEL[0] * 3 + GAP * 2, PANEL[1]), GAP_COLOUR)
        for i, image in enumerate(panels):
            strip.paste(image, (i * (PANEL[0] + GAP), 0))
        path = OUTPUT / (name + '.png')
        strip.save(path, optimize=True)
        print('Wrote', path)


if __name__ == '__main__':
    main()
