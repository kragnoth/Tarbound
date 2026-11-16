"""Builds the Steward's textures (64x64 body, 64x64 glow layer), its item icon (16x16), and a labelled UV guide.
Run from the repo root: python3 dev/make_steward_texture.py
Writes:
  src/main/resources/assets/tarbound/textures/entity/steward.png        (the model's texture)
  src/main/resources/assets/tarbound/textures/entity/steward_glow.png   (drawn full-bright on top: eyes, lamp, soul light)
  src/main/resources/assets/tarbound/textures/item/steward.png          (inventory icon)
  dev/steward_uv_guide.png                                              (8x, boxes outlined and labelled)

The Steward is a spirit bound into a husk of the same tar the radios sit in: enderman-built, four arms, the shell
cooled to a dull crust that has cracked open, so wet tar beads in the cracks and runs down. Its head is a cast-iron
observer (the same iron as the Tar Antennas) with the Reliquary's red eyes peering out of the dark behind its grille.
Deep in the widest cracks there's a little soul light, the spirit inside.

Layout must match StewardModel.createBodyLayer(); each PART is one cube (texOffs u, v; size w, h, d). To restyle,
repaint the PNGs keeping the layout; re-running this overwrites hand-painted changes. Small palettes and long runs,
no per-pixel noise (like the rest of the mod's art)."""
import random
from PIL import Image, ImageDraw

OUT = "src/main/resources/assets/tarbound/textures/entity/steward.png"
GLOW_OUT = "src/main/resources/assets/tarbound/textures/entity/steward_glow.png"
ITEM_OUT = "src/main/resources/assets/tarbound/textures/item/steward.png"
GUIDE = "dev/steward_uv_guide.png"
W, H = 64, 64
rnd = random.Random(2610)

# --- palette (shared with the rest of Tarbound) ---
TAR = [(8, 6, 5), (13, 10, 8), (19, 15, 12), (27, 21, 17)]        # wet tar, darkest first (tar_still)
CRUST = [(34, 27, 22), (45, 35, 28), (58, 45, 35)]                 # the cooled shell (tar-soaked wood browns)
GLOSS = [(66, 58, 54), (104, 96, 90)]                               # wet sheen on tar beads
IRON = [(41, 38, 37), (53, 49, 46), (64, 59, 55), (76, 70, 64), (90, 83, 75)]   # antenna cast iron
WORN = (112, 104, 93)
RUST = [(78, 46, 30), (104, 62, 36)]
VOID = (6, 4, 8)                                                     # behind the Reliquary's eyes
EYE = [(70, 6, 8), (255, 40, 30), (255, 150, 120)]
SOUL = [(30, 120, 140), (80, 200, 220)]                              # spirit light, kept dim

# name: (u, v, w, h, d)
PARTS = {
    "head":   (0, 0, 8, 8, 8),
    "body":   (32, 0, 8, 12, 4),
    "arm":    (0, 16, 2, 28, 2),
    "lowarm": (8, 16, 2, 20, 2),
    "leg":    (16, 16, 2, 28, 2),
    "socket": (24, 16, 3, 3, 3),
    "drip":   (36, 16, 1, 2, 1),
}

img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()
gx = glow.load()


def faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "west": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "east": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def put(x, y, c, target=None):
    t = target if target is not None else px
    if 0 <= x < W and 0 <= y < H:
        t[x, y] = (*c, 255)


def fill(rect, c):
    x0, y0, fw, fh = rect
    for y in range(fh):
        for x in range(fw):
            put(x0 + x, y0 + y, c)


# ------------------------------------------------------------------ tar shell

def shell(rect, seed, cracks=1, crust_rows=None, wet_bottom=0):
    """Crust in horizontal bands (long runs), a few cracks of wet tar with beads and runs below them."""
    x0, y0, fw, fh = rect
    r = random.Random(seed)
    # bands of crust: each row one shade, changing every few rows, with an occasional notch
    band = 1
    y = 0
    while y < fh:
        run = r.randint(2, 4)
        for yy in range(y, min(fh, y + run)):
            for x in range(fw):
                put(x0 + x, y0 + yy, CRUST[band])
            # a darker seam at the top of each band
            if yy == y and y > 0:
                for x in range(fw):
                    put(x0 + x, y0 + yy, CRUST[0])
        band = (band + r.choice([1, 2])) % 3
        y += run
    # edges a step darker (the shell curls in at the corners)
    for yy in range(fh):
        put(x0, y0 + yy, darker(px[x0, y0 + yy]))
        if fw > 2:
            put(x0 + fw - 1, y0 + yy, darker(px[x0 + fw - 1, y0 + yy]))
    # cracks: a short zig-zag of tar, a bead of gloss in it, and tar running down from it
    for _ in range(cracks):
        cx = r.randrange(0, fw)
        cy = r.randrange(1, max(2, fh - 3))
        length = r.randint(2, max(2, min(5, fw + 1)))
        x, y = cx, cy
        pts = []
        for _ in range(length):
            pts.append((x, y))
            x += r.choice([-1, 1]) if fw > 2 else 0
            x = max(0, min(fw - 1, x))
            y += r.choice([0, 1])
            if y >= fh:
                break
        for (x, y) in pts:
            put(x0 + x, y0 + y, TAR[0])
        bx, by = pts[len(pts) // 2]
        put(x0 + bx, y0 + by, TAR[2])
        if fw > 2:
            put(x0 + bx, y0 + by, GLOSS[0])
        # the run of tar below the crack's lowest point
        lx, ly = max(pts, key=lambda p: p[1])
        drop = r.randint(2, 5)
        for k in range(1, drop + 1):
            if ly + k < fh:
                put(x0 + lx, y0 + ly + k, TAR[1] if k < drop else TAR[2])
        if ly + drop < fh:
            put(x0 + lx, y0 + ly + drop, GLOSS[1] if fw > 2 else GLOSS[0])
        if fw > 2 and r.random() < 0.6:
            # soul light deep in the widest cracks
            sx, sy = pts[0]
            put(x0 + sx, y0 + sy, SOUL[0], gx)
    # hands/feet: the tar pools at the ends of limbs
    for k in range(wet_bottom):
        for x in range(fw):
            put(x0 + x, y0 + fh - 1 - k, TAR[min(3, k)])
    if wet_bottom:
        put(x0 + (fw - 1) // 2, y0 + fh - wet_bottom, GLOSS[0])


def darker(c):
    c = c[:3]
    for pal in (CRUST, TAR):
        if c in pal:
            i = pal.index(c)
            return pal[i - 1] if i > 0 else (TAR[3] if pal is CRUST else TAR[0])
    return c


def limb(name, seed, cracks, wet_bottom):
    u, v, w, h, d = PARTS[name]
    f = faces(u, v, w, h, d)
    for i, face in enumerate(("west", "front", "east", "back")):
        shell(f[face], seed * 10 + i, cracks=cracks, wet_bottom=wet_bottom)
    fill(f["top"], CRUST[0])
    fill(f["bottom"], TAR[0])


# body
u, v, w, h, d = PARTS["body"]
bf = faces(u, v, w, h, d)
shell(bf["front"], 11, cracks=3)
shell(bf["back"], 12, cracks=2)
shell(bf["west"], 13, cracks=1)
shell(bf["east"], 14, cracks=1)
fill(bf["top"], CRUST[0])
for x in range(2, 6):  # the head sits in a ring of tar
    put(bf["top"][0] + x, bf["top"][1] + 1, TAR[1])
    put(bf["top"][0] + x, bf["top"][1] + 2, TAR[1])
fill(bf["bottom"], TAR[1])

# One big crack down the chest, the way an enderman's ribs would sit: the widest, with soul light in it.
fx, fy = bf["front"][0], bf["front"][1]
chest = [(3, 1), (4, 2), (4, 3), (3, 4), (3, 5), (4, 6), (4, 7)]
for (x, y) in chest:
    put(fx + x, fy + y, TAR[0])
    put(fx + x + 1, fy + y, TAR[1])
for (x, y) in chest[2:4]:
    put(fx + x, fy + y, SOUL[0], gx)
put(fx + 4, fy + 3, SOUL[1], gx)
# tar that ran from the shoulders and dried in streaks
for (sx, top, n) in ((1, 0, 6), (6, 1, 4)):
    for k in range(n):
        put(fx + sx, fy + top + k, TAR[1])
    put(fx + sx, fy + top + n, GLOSS[0])
put(fx + 5, fy + 2, GLOSS[1])
for k in range(8, 12):
    put(fx + 4, fy + k, TAR[1])
put(fx + 4, fy + 11, GLOSS[0])

limb("arm", 2, cracks=2, wet_bottom=3)
limb("lowarm", 3, cracks=1, wet_bottom=3)
limb("leg", 4, cracks=2, wet_bottom=2)

# sockets: where the lower arms broke out of the shell, a lump of wet tar
u, v, w, h, d = PARTS["socket"]
for face, rect in faces(u, v, w, h, d).items():
    fill(rect, TAR[1])
    x0, y0, fw, fh = rect
    put(x0 + 1, y0, GLOSS[0])
    put(x0 + fw - 1, y0 + fh - 1, TAR[0])
# drips
u, v, w, h, d = PARTS["drip"]
for face, rect in faces(u, v, w, h, d).items():
    fill(rect, TAR[1])
df = faces(u, v, w, h, d)
put(df["front"][0], df["front"][1] + 1, GLOSS[0])
put(df["back"][0], df["back"][1] + 1, GLOSS[0])
fill(df["bottom"], TAR[2])

# ------------------------------------------------------------------ the observer head

u, v, w, h, d = PARTS["head"]
hf = faces(u, v, w, h, d)


def plate(rect, base=2):
    """Cast-iron plate: one tone, a darker rim, worn top-left corner."""
    x0, y0, fw, fh = rect
    fill(rect, IRON[base])
    for x in range(fw):
        put(x0 + x, y0, IRON[base + 1])
        put(x0 + x, y0 + fh - 1, IRON[base - 1])
    for y in range(fh):
        put(x0, y0 + y, IRON[base - 1] if y else IRON[base + 1])
        put(x0 + fw - 1, y0 + y, IRON[base - 1])
    put(x0, y0, WORN)


# front: the observer's face. A grille frame round a dark window; in the window, the Reliquary's eyes.
x0, y0, _, _ = hf["front"]
plate(hf["front"], 2)
for x in range(1, 7):
    put(x0 + x, y0 + 2, IRON[1])        # top lip of the window
    put(x0 + x, y0 + 6, IRON[3])        # bottom lip
for y in range(3, 6):
    for x in range(1, 7):
        put(x0 + x, y0 + y, VOID)
for x in (1, 6):
    for y in range(3, 6):
        put(x0 + x, y0 + y, IRON[0])    # the window's sides, in shadow
# eyes (also on the glow layer, so they shine in the dark)
for (x, y, c) in ((2, 4, 1), (5, 4, 1), (3, 4, 0), (4, 4, 0)):
    put(x0 + x, y0 + y, EYE[c])
    if c:
        put(x0 + x, y0 + y, EYE[1], gx)
put(x0 + 2, y0 + 3, EYE[0])
put(x0 + 5, y0 + 3, EYE[0])
put(x0 + 2, y0 + 3, EYE[0], gx)
put(x0 + 5, y0 + 3, EYE[0], gx)
# grille slots under the window, like the observer's mouth
for x in (2, 4, 6):
    put(x0 + x - 1, y0 + 7, IRON[0])
# rivets
put(x0 + 1, y0 + 1, WORN)
put(x0 + 6, y0 + 1, WORN)

# top: the observer's arrow, raised in the casting, pointing back (the way its signal goes)
x0, y0, _, _ = hf["top"]
plate(hf["top"], 2)
arrow = [(3, 1), (4, 1), (2, 2), (3, 2), (4, 2), (5, 2), (3, 3), (4, 3), (3, 4), (4, 4), (3, 5), (4, 5)]
for (x, y) in arrow:
    put(x0 + x, y0 + y, IRON[3])
for (x, y) in ((1, 2), (6, 2)):
    put(x0 + x, y0 + y, IRON[1])
put(x0 + 3, y0 + 6, IRON[1])
put(x0 + 4, y0 + 6, IRON[1])

# sides: ridged like an observer's, rust weeping from the rivets, tar crept up from the neck
for side in ("west", "east"):
    x0, y0, fw, fh = hf[side]
    plate(hf[side], 2)
    for x in range(1, 7):
        put(x0 + x, y0 + 2, IRON[3])
        put(x0 + x, y0 + 3, IRON[1])
        put(x0 + x, y0 + 5, IRON[3])
    rx = 2 if side == "west" else 5
    put(x0 + rx, y0 + 1, WORN)
    put(x0 + rx, y0 + 4, RUST[1])
    put(x0 + rx, y0 + 5, RUST[0])
    put(x0 + rx, y0 + 6, RUST[0])
    for x in range(fw):
        put(x0 + x, y0 + 7, TAR[1])
    for x in (1, 2, 5) if side == "west" else (2, 5, 6):
        put(x0 + x, y0 + 6, TAR[2])

# back: the output face, a round lamp in a dark socket
x0, y0, _, _ = hf["back"]
plate(hf["back"], 2)
for (x, y) in ((3, 2), (4, 2), (2, 3), (5, 3), (2, 4), (5, 4), (3, 5), (4, 5)):
    put(x0 + x, y0 + y, IRON[0])
for (x, y) in ((3, 3), (4, 3), (3, 4), (4, 4)):
    put(x0 + x, y0 + y, EYE[0])
    put(x0 + x, y0 + y, EYE[0], gx)
put(x0 + 3, y0 + 3, (150, 30, 24))
put(x0 + 3, y0 + 3, (150, 30, 24), gx)
for x in range(8):
    put(x0 + x, y0 + 7, TAR[1])

# bottom: iron, mostly hidden in the tar ring
x0, y0, _, _ = hf["bottom"]
fill(hf["bottom"], IRON[1])
for x in range(2, 6):
    for y in range(2, 6):
        put(x0 + x, y0 + y, TAR[1])

img.save(OUT)
glow.save(GLOW_OUT)

# ------------------------------------------------------------------ item icon: the head, set on a lump of tar

icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
ip = icon.load()


def ip_put(x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        ip[x, y] = (*c, 255)


# shoulders of tar
for y in range(11, 16):
    half = 3 + (y - 11)
    for x in range(8 - half, 8 + half):
        ip_put(x, y, CRUST[1] if y < 13 else CRUST[0])
for x in range(3, 13):
    ip_put(x, 11, CRUST[2])
for (x, y) in ((5, 12), (5, 13), (5, 14), (10, 13), (10, 14), (10, 15)):
    ip_put(x, y, TAR[0])
ip_put(5, 15, GLOSS[1])
ip_put(10, 12, GLOSS[0])
for (x, y) in ((7, 12), (8, 13), (8, 14)):
    ip_put(x, y, TAR[0])
ip_put(8, 13, SOUL[0])
# the iron head, 10 wide, in three-quarter-ish front view
for y in range(1, 11):
    for x in range(3, 13):
        ip_put(x, y, IRON[2])
for x in range(3, 13):
    ip_put(x, 1, IRON[3])
    ip_put(x, 10, IRON[0])
for y in range(1, 11):
    ip_put(3, y, IRON[3])
    ip_put(12, y, IRON[0])
ip_put(3, 1, WORN)
for x in range(4, 12):
    ip_put(x, 3, IRON[1])
    ip_put(x, 8, IRON[3])
for y in range(4, 8):
    for x in range(4, 12):
        ip_put(x, y, VOID)
for y in range(4, 8):
    ip_put(4, y, IRON[0])
    ip_put(11, y, IRON[0])
for (x, y, c) in ((6, 5, 2), (9, 5, 2), (6, 6, 1), (9, 6, 1)):
    ip_put(x, y, EYE[c])
for x in (5, 7, 9):
    ip_put(x, 9, IRON[0])
ip_put(4, 2, WORN)
ip_put(11, 2, WORN)
for x in (3, 4, 11, 12):
    ip_put(x, 10, TAR[1])
icon.save(ITEM_OUT)

# ------------------------------------------------------------------ UV guide

S = 8
guide = img.resize((W * S, H * S), Image.NEAREST).convert("RGBA")
bg = Image.new("RGBA", guide.size, (70, 70, 70, 255))
bg.alpha_composite(guide)
dr = ImageDraw.Draw(bg)
for name, (u, v, w, h, d) in PARTS.items():
    for face, (x, y, fw, fh) in faces(u, v, w, h, d).items():
        dr.rectangle([x * S, y * S, (x + fw) * S - 1, (y + fh) * S - 1], outline=(255, 210, 60))
    dr.text((u * S + 2, v * S + 2), name, fill=(255, 255, 255))
bg.save(GUIDE)
print("wrote", OUT, GLOW_OUT, ITEM_OUT, GUIDE)
