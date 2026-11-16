"""Tar Antenna models, drawn pointing UP (the blockstate rotates them to the other five faces).
Run from the repo root:
  python3 dev/make_antenna_models.py src/main/resources/assets/tarbound/models/block src/main/resources/assets/tarbound/blockstates/tar_antenna.json

Six stage models, like dripstone thickness:
  single              - rabbit-ear TV antenna                          (reach 3)
  base2, top2         - rooftop mast with a Yagi aerial                (reach 6)
  base3, mid3, top3   - broadcast mast: a bigger roof mast on a braced
                        footing, a side aerial, and a stacked Yagi pair (reach 9)

The metal is Tarbound's own cast iron (dev/make_antenna_textures.py, 0.61); the base and mode lamps are vanilla
references, so resource packs restyle those. Hitboxes are in TarAntennaBlock.BOXES_UP.
"""
import json, math, sys, os

models, blockstates = sys.argv[1], sys.argv[2]

TEX = {
    "base": "minecraft:block/polished_blackstone",
    "rod": "tarbound:block/antenna_iron",
    "steel": "tarbound:block/antenna_iron",
    "dark": "tarbound:block/antenna_iron_dark",
    "particle": "tarbound:block/antenna_iron",
}


def uv_for(face, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    clamp = lambda v: max(0.0, min(16.0, v))
    r = {
        "up": [x0, z0, x1, z1],
        "down": [x0, 16 - z1, x1, 16 - z0],
        "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0],
        "south": [x0, 16 - y1, x1, 16 - y0],
        "west": [z0, 16 - y1, z1, 16 - y0],
        "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
    }[face]
    return [round(clamp(v), 3) for v in r]


def box(name, f, t, tex, rot=None, faces=None, glow=False, cull_down=False):
    faces = faces or ("north", "east", "south", "west", "up", "down")
    el = {"name": name, "from": [round(v, 3) for v in f], "to": [round(v, 3) for v in t], "faces": {}}
    for fc in faces:
        d = {"uv": uv_for(fc, f, t), "texture": "#" + tex}
        if glow:
            d["neoforge_data"] = {"block_light": 15, "sky_light": 15}
        if cull_down and fc == "down":
            d["cullface"] = "down"
        el["faces"][fc] = d
    if rot:
        el["rotation"] = {"angle": rot[0], "axis": rot[1], "origin": rot[2]}
    return el


def rod_y(name, cx, cz, y0, y1, w, tex, rot=None):
    h = w / 2
    return box(name, (cx - h, y0, cz - h), (cx + h, y1, cz + h), tex, rot)


# ---------------------------------------------------------------- single: rabbit ears
def single():
    e = [
        box("base", (5, 0, 5), (11, 2, 11), "base", cull_down=True),
        box("base_top", (6, 2, 6), (10, 3, 10), "base"),
        box("dial", (7, 1, 4.75), (9, 2, 5), "rod", faces=("north",)),
        box("hinge", (6.5, 3, 7.25), (9.5, 4, 8.75), "dark"),
    ]
    for side, ang in (("l", 22.5), ("r", -22.5)):
        rot = (ang, "z", [8, 3.5, 8])
        e.append(rod_y(f"rod_{side}_low", 8, 8, 3.5, 9, 1.0, "rod", rot))
        e.append(rod_y(f"rod_{side}_high", 8, 8, 9, 15, 0.5, "rod", rot))
        e.append(box(f"tip_{side}", (7.25, 14.75, 7.25), (8.75, 16, 8.75), "tip", rot, glow=TIP[MODE][1]))
    return e


# ---------------------------------------------------------------- 2 tall: rooftop mast + Yagi
def base2():
    e = [
        box("plate", (4, 0, 4), (12, 1, 12), "base", cull_down=True),
        box("bracket", (6, 1, 6), (10, 3, 10), "dark"),
        rod_y("pole", 8, 8, 1, 16, 2, "rod"),
    ]
    for i, y in enumerate((7, 13)):
        e.append(box(f"clamp{i}", (6.5, y, 6.5), (9.5, y + 1, 9.5), "dark"))
    # guy-wire lugs
    for i, (dx, dz) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
        cx, cz = 8 + dx * 2.5, 8 + dz * 2.5
        e.append(box(f"lug{i}", (cx - 0.5, 12, cz - 0.5), (cx + 0.5, 13, cz + 0.5), "dark"))
    return e


def top2():
    e = [
        rod_y("pole", 8, 8, 0, 10, 2, "rod"),
        box("mount", (6.5, 9, 6.5), (9.5, 11, 9.5), "base"),
        box("boom", (7.5, 10, 0.5), (8.5, 11, 15.5), "dark"),
    ]
    # Yagi elements: reflector at the back is longest, directors shorten towards the front.
    for i, (z, half) in enumerate(((1, 7.5), (4, 6.5), (7, 5.5), (10, 4.5), (13, 3.5), (15, 2.5))):
        e.append(box(f"element{i}", (8 - half, 11, z - 0.5 if z < 15 else 14.5), (8 + half, 12, z + 0.5 if z < 15 else 15.5), "rod"))
    # driven element: folded dipole with a balun box
    e.append(box("balun", (7, 11, 6), (9, 13, 8), "base"))
    e.append(box("lamp", (7, 13, 6), (9, 14.5, 8), "tip", glow=TIP[MODE][1]))
    e.append(box("dipole_top", (2.5, 12.5, 6.5), (13.5, 13, 7.5), "rod"))
    e.append(box("dipole_l", (2.5, 12, 6.5), (3, 12.5, 7.5), "rod"))
    e.append(box("dipole_r", (13, 12, 6.5), (13.5, 12.5, 7.5), "rod"))
    return e


# ---------------------------------------------------------------- 3 tall: broadcast mast
# The rooftop mast grown up: a heavier pole on a braced footing, a small side aerial halfway up, and a stacked
# pair of long Yagis on top with the mode lamp at the tip.
def yagi(prefix, y, z0, z1, halves, along="z"):
    """A boom from z0 to z1 at height y with elements (one per half-length) spread along it, longest at the back."""
    e = []
    if along == "z":
        e.append(box(prefix + "boom", (7.5, y, z0), (8.5, y + 1, z1), "dark"))
    else:
        e.append(box(prefix + "boom", (z0, y, 7.5), (z1, y + 1, 8.5), "dark"))
    n = len(halves)
    for i, half in enumerate(halves):
        p = z0 + 0.5 + i * (z1 - z0 - 1) / (n - 1)
        if along == "z":
            e.append(box(f"{prefix}el{i}", (8 - half, y + 1, p - 0.5), (8 + half, y + 1.5, p + 0.5), "rod"))
        else:
            e.append(box(f"{prefix}el{i}", (p - 0.5, y + 1, 8 - half), (p + 0.5, y + 1.5, 8 + half), "rod"))
    return e


def base3():
    e = [
        box("plate", (3, 0, 3), (13, 1, 13), "base", cull_down=True),
        box("bracket", (5.5, 1, 5.5), (10.5, 4, 10.5), "dark"),
        rod_y("pole", 8, 8, 1, 16, 2.5, "rod"),
        box("collar", (6.25, 6, 6.25), (9.75, 7, 9.75), "dark"),
    ]
    # four braces from the plate edges up to the collar, leaning in (22.5 degrees, meeting the pole at the collar)
    for i, (dx, dz) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
        axis = "z" if dz == 0 else "x"
        ang = (22.5 if dx > 0 else -22.5) if dz == 0 else (-22.5 if dz > 0 else 22.5)
        cx, cz = 8 + dx * 3.5, 8 + dz * 3.5
        e.append(box(f"brace{i}", (cx - 0.5, 1, cz - 0.5), (cx + 0.5, 7, cz + 0.5), "dark", (ang, axis, [cx, 1, cz]),
                     faces=("north", "east", "south", "west")))
    return e


def mid3():
    e = [rod_y("pole", 8, 8, 0, 16, 2.5, "rod")]
    for i, y in enumerate((2, 14)):
        e.append(box(f"clamp{i}", (6.25, y, 6.25), (9.75, y + 1, 9.75), "dark"))
    # a small side aerial, boom across the mast, pointing the other way from the top pair
    e += yagi("side_", 7, 0.5, 15.5, (5.5, 4.5, 3.5, 3, 2.5), along="x")
    for i, (dx, dz) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):  # guy-wire lugs
        cx, cz = 8 + dx * 2.75, 8 + dz * 2.75
        e.append(box(f"lug{i}", (cx - 0.5, 12, cz - 0.5), (cx + 0.5, 13, cz + 0.5), "dark"))
    return e


def top3():
    e = [
        rod_y("pole", 8, 8, 0, 15, 2.5, "rod"),
        box("mount_lo", (6.5, 8, 6.5), (9.5, 10, 9.5), "base"),
        box("mount_hi", (6.5, 12, 6.5), (9.5, 14, 9.5), "base"),
    ]
    halves = (7.5, 6.5, 5.5, 4.75, 4, 3.25, 2.5)
    e += yagi("lo_", 9, 0.5, 15.5, halves)
    e += yagi("hi_", 13, 0.5, 15.5, halves)
    # stacking frame: the two booms tied together fore and aft
    for i, z in enumerate((1, 14)):
        e.append(box(f"tie{i}", (7.5, 10, z), (8.5, 13, z + 1), "dark"))
    e.append(box("lamp", (7, 15, 7), (9, 16.5, 9), "tip", glow=TIP[MODE][1]))
    return e


MODE = "commune"
# per mode: (texture for the indicator, glows?) for rabbit-ear tips / aerial lamp, and for the tower light
TIP = {"commune": ("tarbound:block/antenna_iron", False), "scry": ("minecraft:block/gold_block", True), "tithe": ("minecraft:block/amethyst_block", True)}

STAGES = {"single": single, "base2": base2, "top2": top2, "base3": base3, "mid3": mid3, "top3": top3}

os.makedirs(models, exist_ok=True)
MODED = ("single", "top2", "top3")  # pieces carrying the mode lamp
jobs = []
for name, fn in STAGES.items():
    if name in MODED:
        for mode in TIP:
            jobs.append((name, fn, mode, f"tar_antenna_{name}_{mode}"))
    else:
        jobs.append((name, fn, "commune", f"tar_antenna_{name}"))
for name, fn, mode, fname in jobs:
    MODE = mode
    tex = dict(TEX, tip=TIP[mode][0])
    m = {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": tex, "elements": fn()}
    with open(f"{models}/{fname}.json", "w") as fh:
        json.dump(m, fh, indent=1)

ROT = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180},
       "west": {"x": 90, "y": 270}, "east": {"x": 90, "y": 90}}
variants = {}
for facing, r in ROT.items():
    for stage in STAGES:
        for mode in TIP:
            model = f"tar_antenna_{stage}_{mode}" if stage in MODED else f"tar_antenna_{stage}"
            variants[f"facing={facing},mode={mode},stage={stage}"] = {"model": f"tarbound:block/{model}", **r}
with open(blockstates, "w") as fh:
    json.dump({"variants": variants}, fh, indent=1)
print(len(variants), "variants")
