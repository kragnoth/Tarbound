"""Pixel-art textures for the blocky haunted radio (16x16 each, Minecraft density).
Run from the repo root:  python3 dev/make_radio_textures.py src/main/resources/assets/tarbound/textures/block

Same low-noise style as make_wood_textures.py: fixed palettes, no per-pixel jitter; the bakelite case is long runs
of one tone with a lit top edge, the plinth wood has grain runs."""
import sys, random
from PIL import Image

out = sys.argv[1]
rnd = random.Random(1937)

def hexc(h):
    h = h.lstrip('#'); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4)) + (255,)

# palette
TAR, TAR_HI = hexc('0b0a09'), hexc('2a2622')
C_DK, C_MD, C_LT, C_HI = hexc('1d211a'), hexc('2b3027'), hexc('3a4134'), hexc('525c48')
F_DK, F_MD, F_LT = hexc('31372c'), hexc('454d3e'), hexc('5d6752')
SLAT_DK, SLAT_LT = hexc('0f110d'), hexc('353b2f')
W_DK, W_MD, W_LT = hexc('241910'), hexc('36271a'), hexc('4b3624')
DIAL_BG, DIAL_MID, DIAL_TICK, NEEDLE = hexc('c4f5d0'), hexc('92dcab'), hexc('3c6a4c'), hexc('c4362a')
EYE_C, EYE_R = hexc('7dffaa'), hexc('2fbf6a')
K_HI, K_MD, K_DK, K_NOTCH = hexc('7a7e74'), hexc('50544b'), hexc('2a2d27'), hexc('cfc8aa')
A_HI, A_MD, A_DK = hexc('c9ced3'), hexc('8d9399'), hexc('55595e')
BEZEL = hexc('6b7360')

def jitter(c, amt=6):
    """Kept for the layout code below; no longer adds noise (the palette does the work)."""
    return c


def runs(length, tones, lo=3, hi=7):
    """A row of tones in runs of lo..hi pixels, wrapping."""
    out = []
    while len(out) < length:
        out += [rnd.choice(tones)] * rnd.randint(lo, hi)
    shift = rnd.randint(0, length - 1)
    out = out[:length]
    return out[shift:] + out[:shift]

# ---------------- case: bakelite body, tar seeping over the top edge ----------------
case = Image.new('RGBA', (16, 16)); p = case.load()
for y in range(16):  # smooth moulded bakelite: one tone, a soft sheen under the top edge, a few scuffs
    row = runs(16, (C_LT, C_MD), 3, 7) if y == 2 else [C_MD] * 16
    for x in range(16):
        p[x, y] = row[x]
for (x, y, n) in ((3, 7, 3), (10, 10, 2), (6, 12, 4)):
    for k in range(n):
        p[(x + k) % 16, y] = C_DK
for x in range(16):                      # rounded top edge catches the light
    p[x, 0] = jitter(C_HI, 5)
    p[x, 1] = jitter(C_LT, 4)
for x in range(16):                      # tar lip along the very top
    if rnd.random() < 0.4:
        p[x, 0] = TAR
# a few short drips from the lip (kept above row 4 so tops stay clean)
for x, length in ((2, 2), (6, 1), (11, 2), (13, 1)):
    for y in range(1, 1 + length):
        p[x, y] = TAR
    p[x, 1 + length] = TAR_HI
# grime at the bottom
for y in (14, 15):
    row = runs(16, (C_DK, C_DK, C_MD) if y == 14 else (C_DK,), 3, 6)
    for x in range(16):
        p[x, y] = row[x]
case.save(f'{out}/scrying_font_case.png')

# ---------------- front: 14x7 panel at texels (0,0)-(14,7) ----------------
front = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); f = front.load()
W, H = 14, 7
for y in range(H):
    for x in range(W):
        f[x, y] = jitter(F_MD, 4)
for x in range(W):                       # frame: light top, dark bottom
    f[x, 0] = jitter(F_LT, 4)
    f[x, H - 1] = jitter(F_DK, 3)
for y in range(H):
    f[0, y] = jitter(F_LT, 4)
    f[W - 1, y] = jitter(F_DK, 3)
# recess behind the dial glass (dial element covers x3..11, rows 1..2)
for x in range(2, 12):
    for y in range(1, 4):
        f[x, y] = jitter(C_DK, 3)
# speaker grille between the knobs: x5..9, rows 4..5 -> slats
for x in range(5, 9):
    f[x, 4] = SLAT_LT
    f[x, 5] = SLAT_DK
f[4, 4] = F_DK; f[9, 4] = F_DK; f[4, 5] = F_DK; f[9, 5] = F_DK
# screws in the corners
for (x, y) in ((1, 1), (12, 1), (1, 5), (12, 5)):
    f[x, y] = K_HI
# tar has got into the grille
f[6, 5] = TAR; f[7, 4] = TAR
front.save(f'{out}/scrying_font_front.png')

# ---------------- parts sheet ----------------
parts = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); q = parts.load()
# dial glass 8x2 at (0,0): two rows of graduations and the needle
for x in range(8):
    q[x, 0] = DIAL_BG if x % 2 == 0 else DIAL_MID
    q[x, 1] = DIAL_MID if x % 2 == 0 else DIAL_TICK
q[5, 0] = NEEDLE; q[5, 1] = NEEDLE
# magic-eye tuning tube 1x1 at (8,0)
q[8, 0] = EYE_C; q[9, 0] = EYE_R
# glow edge for the glass sides (10,0)
q[10, 0] = DIAL_MID
# knob face 2x2 at (0,4), knob side 2x1 at (0,6), knob edge 1x2 at (2,4)
q[0, 4] = K_HI; q[1, 4] = K_MD; q[0, 5] = K_MD; q[1, 5] = K_DK
q[1, 4] = K_NOTCH  # pointer notch
q[0, 6] = K_MD; q[1, 6] = K_DK
q[2, 4] = K_MD; q[2, 5] = K_DK
# antenna 1x6 at (12,0)
for y in range(6):
    q[12, y] = A_HI if y % 3 == 0 else A_MD
q[13, 0] = A_DK
# bezel / small edge pixel at (15,15)
q[15, 15] = BEZEL
# wood plinth strip 16x2 at rows 10-11, top/bottom at rows 12-15
top_row = runs(16, (W_LT, W_LT, W_MD), 3, 6)
for x in range(16):
    q[x, 10] = top_row[x]
    q[x, 11] = W_DK
for y in range(12, 15):
    row = runs(16, (W_MD, W_MD, W_DK, W_LT), 3, 7)  # plinth top and bottom: grain runs
    for x in range(16):
        q[x, y] = row[x]
parts.save(f'{out}/scrying_font_parts.png')
print('radio textures written')
