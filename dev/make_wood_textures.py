"""Tarbound's wood textures: tar-soaked planks, log and log top, and the Pitch Cask.

Drawn in a painterly low-noise style (in the spirit of packs like Excalibur, nothing copied): each texture uses a
small fixed palette (6-9 colours), boards and staves carry long runs of one tone for the grain instead of
per-pixel noise, seams are a single dark line, and the tar shows as a few cool glossy streaks along the grain.
Everything wraps, so faces tile. Run from the repo root:  python3 dev/make_wood_textures.py"""
import random
from PIL import Image

OUT = 'src/main/resources/assets/tarbound/textures/block/'

# tar-soaked wood: dark oak gone near-black, warm browns underneath, and a cool sheen where the tar sits wet
TAR_WOOD = [(20, 15, 13), (31, 23, 19), (42, 31, 24), (54, 40, 30), (66, 50, 37), (80, 61, 45)]
SHEEN = [(58, 56, 60), (78, 76, 82)]
# pitch cask: oak, sealed but not soaked
OAK = [(46, 33, 22), (70, 51, 33), (88, 65, 42), (104, 78, 50), (120, 91, 59), (136, 104, 68)]
PITCH = (19, 14, 12)
IRON = [(38, 38, 41), (56, 55, 58), (74, 72, 73), (94, 91, 89), (112, 107, 101)]
RUST = [(92, 60, 40), (116, 76, 48)]


def img():
    return Image.new('RGBA', (16, 16))


def put(im, x, y, c):
    im.putpixel((x % 16, y % 16), c + (255,))


def runs(r, length, tones, lo=3, hi=7):
    """A row (or column) of grain: tones in runs of lo..hi pixels, wrapping."""
    out = []
    while len(out) < length:
        out += [r.choice(tones)] * r.randint(lo, hi)
    shift = r.randint(0, length - 1)
    out = out[:length]
    return out[shift:] + out[:shift]


def planks(name, ramp, seed, joints=(3, 11, 6, 14), sheen=True, rim=False):
    """Four boards of 4 px: a lighter top row, two rows of grain, a dark seam; one butt joint per board."""
    r = random.Random(seed)
    im = img()
    for b in range(4):
        y0 = b * 4
        base = r.choice((2, 3, 3, 4))  # each board its own tone, mostly middling
        top = runs(r, 16, (base, base, base + 1))
        mid1 = runs(r, 16, (base, base, base - 1, base + 1))
        mid2 = runs(r, 16, (base - 1, base, base, base - 1))
        for x in range(16):
            put(im, x, y0, ramp[min(top[x], len(ramp) - 1)])
            put(im, x, y0 + 1, ramp[mid1[x]])
            put(im, x, y0 + 2, ramp[mid2[x]])
            put(im, x, y0 + 3, ramp[0] if not rim else PITCH)
        j = joints[b]
        for y in range(y0, y0 + 3):  # butt joint: a soft dark line
            put(im, j, y, ramp[1])
        if sheen and r.random() < 0.75:  # a wet tar streak along the grain
            sx, sy, n = r.randint(0, 15), y0 + r.choice((1, 2)), r.randint(2, 4)
            for k in range(n):
                if (sx + k) % 16 != j:
                    put(im, sx + k, sy, SHEEN[0] if k else SHEEN[1])
    if rim:
        for i in range(16):
            for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
                put(im, x, y, PITCH)
            for (x, y) in ((i, 1), (i, 14), (1, i), (14, i)):
                if 0 < i < 15:
                    c = im.getpixel((x, y))[:3]
                    put(im, x, y, tuple(int(v * 0.7) for v in c))
    im.save(OUT + name + '.png')
    return im


def log_side(name, seed):
    """Bark: vertical runs of tone, a dark fissure every few columns, tar weeping down a couple of them."""
    r = random.Random(seed)
    im = img()
    x = 0
    while x < 16:
        width = r.randint(2, 4)
        tones = (1, 2, 2, 3) if r.random() < 0.5 else (2, 3, 3, 4)
        for dx in range(width):
            col = runs(r, 16, tones, 3, 8)
            for y in range(16):
                put(im, x + dx, y, TAR_WOOD[col[y]])
        fis = runs(r, 16, (0, 0, 0, 1), 4, 9)  # the fissure after the ridge
        for y in range(16):
            put(im, x + width, y, TAR_WOOD[fis[y]])
        x += width + 1
    for _ in range(2):  # tar weeping down a fissure
        fx, fy, n = r.randint(0, 15), r.randint(0, 15), r.randint(3, 6)
        for k in range(n):
            put(im, fx, fy + k, SHEEN[0] if k < n - 1 else SHEEN[1])
    im.save(OUT + name + '.png')


def log_top(name, seed):
    """End grain: soft squared rings in two alternating tones, dark pith, a bark rim darkened by tar."""
    r = random.Random(seed)
    im = img()
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - 7.5), abs(y - 7.5)
            d = max(dx, dy) * 0.8 + (dx * dx + dy * dy) ** 0.5 * 0.2  # squared, corners softened
            ring = int(d)
            if ring >= 7:
                c = TAR_WOOD[1] if (x + y) % 3 else TAR_WOOD[0]  # bark
            elif ring <= 0:
                c = TAR_WOOD[1]  # pith
            else:
                c = TAR_WOOD[5] if ring <= 2 and ring % 2 else TAR_WOOD[4] if ring % 2 else TAR_WOOD[3]
                if ring >= 5:
                    c = TAR_WOOD[3] if ring % 2 else TAR_WOOD[2]  # tar soaked in from the outside
            put(im, x, y, c)
    for _ in range(2):  # a check (crack) from the rim inward
        side = r.randint(0, 3)
        p = r.randint(4, 11)
        for k in range(r.randint(2, 4)):
            x, y = [(p, k + 1), (14 - k, p), (p, 14 - k), (k + 1, p)][side]
            put(im, x, y, TAR_WOOD[1])
    im.save(OUT + name + '.png')


def cask_side(name, seed):
    """Staves: 3 px of oak with long vertical grain, a pitch seam between, tar wicked up the foot in two steps."""
    r = random.Random(seed)
    im = img()
    for s in range(4):
        x0 = s * 4
        base = r.choice((2, 3, 3, 4))
        for dx in range(3):
            col = runs(r, 16, (base, base, base + 1 if dx == 0 else base - 1), 4, 9)
            for y in range(16):
                t = col[y]
                if y >= 14:
                    t = max(0, t - 2)
                elif y >= 12:
                    t = max(0, t - 1)
                put(im, x0 + dx, y, OAK[t])
        for y in range(16):
            put(im, x0 + 3, y, PITCH)
    im.save(OUT + name + '.png')


def hoop(name, seed):
    """Iron band stock: a lit top edge, a shaded bottom edge (every 4 rows, so any 1 px slice reads), a little rust."""
    r = random.Random(seed)
    im = img()
    for y in range(16):
        row = (3, 2, 2, 1)[y % 4]
        line = runs(r, 16, (row, row, max(0, row - 1)), 4, 8)
        for x in range(16):
            put(im, x, y, IRON[line[x]])
    for _ in range(3):
        x, y = r.randint(0, 15), r.randint(0, 15)
        put(im, x, y, RUST[0])
        put(im, x + 1, y, RUST[1])
    im.save(OUT + name + '.png')


planks('tar_soaked_planks', TAR_WOOD, 101)
log_side('tar_soaked_log', 102)
log_top('tar_soaked_log_top', 103)
cask_side('pitch_cask_side', 104)
lid = planks('pitch_cask_top', OAK, 105, joints=(5, 12, 2, 9), sheen=False, rim=True)
im = lid  # bung
for (x, y, c) in ((5, 5, OAK[1]), (6, 5, OAK[1]), (5, 6, OAK[1]), (6, 6, OAK[0]), (5, 5, OAK[3])):
    put(im, x, y, c)
im.save(OUT + 'pitch_cask_top.png')
hoop('pitch_cask_hoop', 106)
print('wood textures written')
