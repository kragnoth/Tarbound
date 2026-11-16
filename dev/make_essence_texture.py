"""Builds the animated Essence of the Void textures (a seamless, looping starfield with drifting
nebula and swirling motes). Run from the repo root: python3 dev/make_essence_texture.py
Writes essence_of_the_void_still.png (16x16 frames) and _flow.png (32x32 frames) plus .mcmeta files."""
import math, json, os, sys
import numpy as np
from PIL import Image

OUT = "src/main/resources/assets/tarbound/textures/block"
FRAMES = 32
rng = np.random.default_rng(1337)

# Palette stops: void black -> deep indigo -> violet -> magenta haze, with a cold teal accent.
STOPS = [(0.00, (4, 3, 14)), (0.35, (16, 10, 46)), (0.60, (48, 20, 92)), (0.80, (98, 36, 132)), (1.00, (150, 70, 170))]

def palette(v):
    v = np.clip(v, 0, 1)
    out = np.zeros(v.shape + (3,))
    for (a, ca), (b, cb) in zip(STOPS, STOPS[1:]):
        m = (v >= a) & (v <= b)
        t = ((v - a) / (b - a))[m][:, None]
        out[m] = np.array(ca) * (1 - t) + np.array(cb) * t
    return out

def frames(size, seed, drift):
    r = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:size, 0:size] / size  # 0..1, tiles at 1
    waves = [(r.integers(-2, 3), r.integers(-2, 3), r.uniform(0, 2 * math.pi), r.integers(-1, 2), r.uniform(0.4, 1.0)) for _ in range(9)]
    waves = [w for w in waves if (w[0], w[1]) != (0, 0)]
    stars = [(r.uniform(0, size), r.uniform(0, size), r.uniform(0.5, 1.0), r.integers(1, 4), r.uniform(0, 1)) for _ in range(size * size // 36)]
    motes = [(r.uniform(0.15, 0.4) * size, r.uniform(0, 1), r.choice([-1, 1]) * r.integers(1, 3), r.uniform(0, size), r.uniform(0, size)) for _ in range(max(2, size // 7))]
    imgs = []
    for f in range(FRAMES):
        t = f / FRAMES
        # Swirl: warp the coordinates with a looping vortex-ish offset (integers keep it seamless).
        wx = xx + 0.06 * np.sin(2 * math.pi * (yy + t))
        wy = yy + 0.06 * np.sin(2 * math.pi * (xx - t)) + drift * t
        n = np.zeros_like(xx)
        total = 0
        for kx, ky, ph, m, a in waves:
            n += a * np.sin(2 * math.pi * (kx * wx + ky * wy + m * t) + ph)
            total += a
        n = 0.5 + 0.5 * n / total
        n = n ** 1.9  # mostly dark void, with brighter wisps
        col = palette(n)
        # Teal glints where the wisps are thickest.
        col += np.clip(n - 0.6, 0, 1)[..., None] * np.array([10, 80, 90])
        # Stars, twinkling on a loop.
        for sx, sy, b, k, ph in stars:
            tw = 0.55 + 0.45 * math.sin(2 * math.pi * (k * t + ph))
            x = int(sx) % size
            y = int(sy + drift * t * size) % size
            c = np.array([235, 230, 255]) * b * tw
            col[y, x] = np.maximum(col[y, x], c)
            if b > 0.9 and tw > 0.8:  # the brightest flare a little
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    col[(y + dy) % size, (x + dx) % size] = np.maximum(col[(y + dy) % size, (x + dx) % size], c * 0.35)
        # Motes circling, each with a short trail.
        for rad, ph, spin, cx, cy in motes:
            for trail, fade in ((0, 1.0), (1, 0.45), (2, 0.2)):
                a = 2 * math.pi * (spin * (t - trail / FRAMES) + ph)
                x = int(cx + rad * math.cos(a)) % size
                y = int(cy + rad * math.sin(a) + drift * t * size) % size
                col[y, x] = np.maximum(col[y, x], np.array([200, 160, 255]) * fade)
        imgs.append(np.clip(col, 0, 255).astype(np.uint8))
    return imgs

def write(name, size, seed, drift):
    imgs = frames(size, seed, drift)
    sheet = Image.new("RGBA", (size, size * FRAMES))
    for i, im in enumerate(imgs):
        sheet.paste(Image.fromarray(im, "RGB").convert("RGBA"), (0, i * size))
    sheet.save(os.path.join(OUT, name + ".png"))
    with open(os.path.join(OUT, name + ".png.mcmeta"), "w") as f:
        json.dump({"animation": {"frametime": 3, "interpolate": True}}, f, indent=2)
    return imgs

if __name__ == "__main__":
    still = write("essence_of_the_void_still", 16, 7, 0)
    flow = write("essence_of_the_void_flow", 32, 11, 1)
    if len(sys.argv) > 1:  # preview: a few frames, tiled 2x2 and scaled up
        prev = Image.new("RGB", (16 * 2 * 4 * 12, 16 * 2 * 12))
        for i, fi in enumerate((0, 8, 16, 24)):
            tile = Image.fromarray(np.tile(still[fi], (2, 2, 1)), "RGB").resize((16 * 2 * 12, 16 * 2 * 12), Image.NEAREST)
            prev.paste(tile, (i * 16 * 2 * 12, 0))
        prev.save(sys.argv[1])
