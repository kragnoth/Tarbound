"""Tarbound's antenna metal: worn, warm-dark cast iron, so the antennas stop borrowing vanilla's bright iron block.

  textures/block/antenna_iron.png       rods, mast, frame (and the Commune lamp)
  textures/block/antenna_iron_dark.png  fittings and braces (was vanilla's anvil)

Same approach as make_wood_textures.py: a small fixed palette, long vertical runs of one tone (antenna parts are
mostly thin upright rods, which sample a 1-2 pixel column), a few pits, a few worn-bright streaks where hands and
weather polish the metal, and a little rust bloom. Everything wraps, so faces tile.
Run from the repo root:  python3 dev/make_antenna_textures.py"""
import random
from PIL import Image

OUT = 'src/main/resources/assets/tarbound/textures/block/'

IRON = [(41, 38, 37), (53, 49, 46), (64, 59, 55), (76, 70, 64), (90, 83, 75)]
IRON_DARK = [(29, 27, 26), (37, 34, 32), (46, 42, 39), (55, 51, 47), (66, 61, 55)]
WORN = (112, 104, 93)
RUST = [(80, 56, 42), (98, 66, 47)]


def runs(r, length, tones, lo=3, hi=7):
    """A column of tones in runs of lo..hi pixels, wrapping."""
    out = []
    while len(out) < length:
        out += [r.choice(tones)] * r.randint(lo, hi)
    shift = r.randint(0, length - 1)
    out = out[:length]
    return out[shift:] + out[:shift]


def cast_iron(name, ramp, seed, rust=2, worn=3, pits=7):
    r = random.Random(seed)
    im = Image.new('RGBA', (16, 16))
    px = lambda x, y, c: im.putpixel((x % 16, y % 16), c + (255,))
    body = ramp[1:4]
    for x in range(16):
        for y, c in enumerate(runs(r, 16, body)):
            px(x, y, c)
    for _ in range(worn):                       # polished streaks: short bright runs down a column
        x, y = r.randrange(16), r.randrange(16)
        for dy in range(r.randint(2, 4)):
            px(x, y + dy, ramp[4] if dy else WORN)
    for _ in range(pits):                       # casting pits
        px(r.randrange(16), r.randrange(16), ramp[0])
    for _ in range(rust):                       # rust bloom: a small blob, darker at its edge
        x, y = r.randrange(16), r.randrange(16)
        px(x, y, RUST[1]); px(x + 1, y, RUST[0]); px(x, y + 1, RUST[0])
    im.save(OUT + name + '.png')
    print(name)


cast_iron('antenna_iron', IRON, 41, rust=1)
cast_iron('antenna_iron_dark', IRON_DARK, 43, rust=1, worn=2, pits=5)
