"""Builds the Weakened Blaze entity texture (64x32) and a labelled UV guide for repainting it.
Run from the repo root: python3 dev/make_weakened_blaze_texture.py
Writes:
  src/main/resources/assets/tarbound/textures/entity/weakened_blaze.png   (the texture the model uses)
  dev/weakened_blaze_uv_guide.png                                         (8x, boxes outlined and labelled)

The layout must match WeakenedBlazeModel.createBodyLayer(); each PART below is one cube with the same
texOffs and size as in the model. To restyle it, repaint the PNG in Blockbench or any pixel editor (keep
the layout) - no code changes needed. Re-running this script overwrites hand-painted changes."""
import os
import numpy as np
from PIL import Image, ImageDraw

OUT = "src/main/resources/assets/tarbound/textures/entity/weakened_blaze.png"
GUIDE = "dev/weakened_blaze_uv_guide.png"
W, H = 64, 32
rng = np.random.default_rng(1013)

# Palette: ash-blue "burnt out" body, soul-fire cyan for everything still burning.
ASH = [(176, 206, 216), (140, 172, 186), (104, 134, 150), (68, 92, 108), (40, 58, 72)]
DARK = (24, 36, 48)
GLOW = [(40, 160, 205), (95, 220, 245), (170, 245, 255), (235, 255, 255)]

# name: (kind, u, v, w, h, d)  -- box sizes in model pixels, texOffs(u, v)
PARTS = {
    "head":  ("head", 0, 0, 7, 7, 7),
    "chip":  ("chip", 28, 0, 3, 3, 3),
    "core":  ("core", 40, 0, 4, 4, 4),
    "rod":   ("rod", 0, 16, 2, 8, 2),
    "stub":  ("stub", 8, 16, 2, 5, 2),
    "wisp":  ("wisp", 16, 16, 2, 4, 2),
    "shard": ("shard", 24, 16, 2, 2, 2),
    "tip":   ("tip", 32, 16, 2, 3, 2),
}

img = np.zeros((H, W, 4), np.uint8)


def faces(u, v, w, h, d):
    """Face rectangles (x, y, width, height) of a cube's UV footprint, same as ModelPart.Cube."""
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "west": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "east": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def put(x, y, col):
    if 0 <= x < W and 0 <= y < H:
        img[y, x] = (*col, 255)


def shade(palette, t):
    """Pick from a palette by t in 0..1 (0 = first entry)."""
    return palette[int(np.clip(t, 0, 0.999) * len(palette))]


def paint_ash(rect, face, grain="none", lit=0.0):
    x0, y0, fw, fh = rect
    base = {"top": 0.15, "bottom": 0.75}.get(face, 0.4) - lit
    for y in range(fh):
        for x in range(fw):
            t = base + rng.normal(0, 0.14)
            if grain == "vertical":
                t += 0.12 * (1 if (x + (3 if face in ("east", "back") else 0)) % 2 else -1)
            edge = (x in (0, fw - 1)) + (y in (0, fh - 1))
            t += 0.07 * edge
            put(x0 + x, y0 + y, shade(ASH, t))


def paint_glow(rect, vertical=None, flicker=0.12):
    """Soul-fire fill. vertical=(t_top, t_bottom) gives a gradient down the face."""
    x0, y0, fw, fh = rect
    for y in range(fh):
        for x in range(fw):
            t = 0.5 if vertical is None else vertical[0] + (vertical[1] - vertical[0]) * (y / max(1, fh - 1))
            t += rng.normal(0, flicker)
            put(x0 + x, y0 + y, shade(GLOW, t))


def crack(rect, start=None, steps=7, glow=True):
    """A jagged crack across one face: dark line with soul-fire glowing through it."""
    x0, y0, fw, fh = rect
    x, y = start if start else (int(rng.integers(0, fw)), int(rng.integers(0, max(1, fh // 2))))
    for _ in range(steps):
        if not (0 <= x < fw and 0 <= y < fh):
            break
        put(x0 + x, y0 + y, GLOW[1] if glow and rng.random() < 0.7 else DARK)
        for dx in (-1, 1):  # dark rim either side, so the glow reads as light from inside
            if 0 <= x + dx < fw and rng.random() < 0.55:
                put(x0 + x + dx, y0 + y, DARK)
        y += 1
        x += int(rng.choice([-1, 0, 0, 1]))


def paint_part(name):
    kind, u, v, w, h, d = PARTS[name]
    f = faces(u, v, w, h, d)
    if kind == "head":
        for face, r in f.items():
            paint_ash(r, face)
        # Lopsided face: a small dim eye, a big bright one, and a crooked mouth.
        fx, fy, _, _ = f["front"]
        for ex, ey, ew, eh, t in ((1, 2, 2, 2, 0.15), (4, 2, 2, 3, 0.85)):
            for yy in range(eh):
                for xx in range(ew):
                    put(fx + ex + xx, fy + ey + yy, shade(GLOW, t + rng.normal(0, 0.08)))
        for mx, my in ((1, 5), (2, 5), (3, 6), (4, 6), (5, 5)):
            put(fx + mx, fy + my, DARK)
        crack(f["front"], (3, 0), 3)
        crack(f["top"], (2, 0), 6)
        crack(f["east"], (4, 1), 5)
        crack(f["back"], (2, 0), 6)
        crack(f["west"], (1, 2), 4)
    elif kind == "chip":
        for face, r in f.items():
            paint_ash(r, face)
        paint_glow(f["bottom"], flicker=0.1)  # the broken-off face still smoulders
    elif kind == "core":
        for face, r in f.items():
            paint_glow(r, vertical=(0.85, 0.55), flicker=0.14)
        for r in f.values():  # a few darker "dying" pixels so the flicker has something to show
            x0, y0, fw, fh = r
            for _ in range(2):
                put(x0 + int(rng.integers(0, fw)), y0 + int(rng.integers(0, fh)), GLOW[0])
    elif kind in ("rod", "stub"):
        for face, r in f.items():
            paint_ash(r, face, grain="vertical")
        for face in ("west", "front", "east", "back"):
            x0, y0, fw, fh = f[face]
            for yy in range(fh - 2, fh):  # charred, frayed ends
                for xx in range(fw):
                    put(x0 + xx, y0 + yy, shade(ASH, 0.8 + rng.normal(0, 0.1)))
            ry = int(rng.integers(2, max(3, fh - 2)))  # one horizontal fracture with glow
            for xx in range(fw):
                put(x0 + xx, y0 + ry, GLOW[1] if rng.random() < 0.65 else DARK)
        if kind == "stub":
            paint_glow(f["bottom"], flicker=0.1)  # snapped end
    elif kind == "tip":
        for face, r in f.items():
            paint_ash(r, face, grain="vertical", lit=-0.1)
        paint_glow(f["top"], flicker=0.1)  # the break, facing back up at the stub
    elif kind == "wisp":
        # Flame tongue: pale at the base (bottom of the face), fading to teal at the tip (top).
        for face, r in f.items():
            if face in ("top", "bottom"):
                paint_glow(r, flicker=0.1)
            else:
                paint_glow(r, vertical=(0.1, 0.95), flicker=0.1)
    elif kind == "shard":
        for face, r in f.items():
            paint_ash(r, face, lit=-0.25)
            x0, y0, fw, fh = r
            if rng.random() < 0.6:
                put(x0 + int(rng.integers(0, fw)), y0 + int(rng.integers(0, fh)), GLOW[1])


for part in PARTS:
    paint_part(part)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
Image.fromarray(img, "RGBA").save(OUT)
print("wrote", OUT)

# ---- UV guide: 8x scale, checkerboard behind the texture, each cube outlined and labelled ----
S = 8
guide = Image.new("RGBA", (W * S, H * S), (30, 30, 36, 255))
cb = ImageDraw.Draw(guide)
for cy in range(H):
    for cx in range(W):
        if (cx + cy) % 2:
            cb.rectangle([cx * S, cy * S, cx * S + S - 1, cy * S + S - 1], fill=(40, 40, 48, 255))
tex = Image.fromarray(img, "RGBA").resize((W * S, H * S), Image.NEAREST)
guide.alpha_composite(tex)
dr = ImageDraw.Draw(guide)
for name, (kind, u, v, w, h, d) in PARTS.items():
    f = faces(u, v, w, h, d)
    for face, (x, y, fw, fh) in f.items():
        dr.rectangle([x * S, y * S, (x + fw) * S - 1, (y + fh) * S - 1], outline=(255, 200, 60, 255))
        if fw * S >= 40 and fh * S >= 24:
            dr.text((x * S + 3, y * S + 2), face, fill=(255, 255, 255, 255))
    dr.text((u * S + 2, (v + d + h) * S + 4), f"{name} {w}x{h}x{d} @({u},{v})", fill=(255, 200, 60, 255))
guide.save(GUIDE)
print("wrote", GUIDE)
