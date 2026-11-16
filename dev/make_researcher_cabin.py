"""Builds data/tarbound/structure/researcher_cabin.nbt (an abandoned researcher's cabin).
Run from the repo root:  python3 dev/make_researcher_cabin.py   (needs: pip install nbtlib)"""
import nbtlib
from nbtlib.tag import Compound, List, Int, String

SX, SY, SZ = 11, 11, 10
blocks = {}  # (x,y,z) -> (name, props, nbt)

def put(x, y, z, name, props=None, nbt=None):
    blocks[(x, y, z)] = ("minecraft:" + name if ":" not in name else name, props or {}, nbt)

X0, X1, Z0, Z1 = 1, 9, 1, 8          # cabin walls
LOOT = {"LootTable": "tarbound:chests/researcher_cabin"}

# floor and foundation
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        edge = x in (X0, X1) or z in (Z0, Z1)
        put(x, 0, z, "cobblestone" if edge else "spruce_planks")
for (x, z) in [(3, 5), (4, 5), (4, 6), (8, 2), (3, 6)]:
    put(x, 0, z, "moss_block")
put(2, 0, 2, "coarse_dirt"); put(7, 0, 3, "rooted_dirt")
put(6, 0, 4, "tarbound:tar", {"level": "0"})          # tar seeping up through the boards

# walls
for y in range(1, 4):
    for x in range(X0, X1 + 1):
        for z in (Z0, Z1):
            put(x, y, z, "spruce_planks")
    for z in range(Z0 + 1, Z1):
        for x in (X0, X1):
            put(x, y, z, "spruce_planks")
    for (x, z) in [(X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)]:
        put(x, y, z, "spruce_log", {"axis": "y"})
# doorway (the door is long gone), windows, a rotted-through hole
for y in (1, 2):
    blocks.pop((5, y, Z0))
for p in [(3, 2, Z0), (7, 2, Z0), (X1, 2, 4), (X1, 2, 5), (3, 2, Z1), (X0, 2, 2), (X1, 3, 3)]:
    blocks.pop(p)
put(7, 2, Z0, "cobweb"); put(X1, 2, 5, "cobweb")

# gable roof along x
rows = [(4, Z0, "south"), (4, Z1, "north"), (5, Z0 + 1, "south"), (5, Z1 - 1, "north"), (6, Z0 + 2, "south"), (6, Z1 - 2, "north")]
for (y, z, f) in rows:
    for x in range(X0, X1 + 1):
        put(x, y, z, "spruce_stairs", {"facing": f, "half": "bottom", "shape": "straight", "waterlogged": "false"})
for x in range(X0, X1 + 1):
    for z in (Z0 + 3, Z0 + 4):
        put(x, 7, z, "spruce_log", {"axis": "x"})
for (y, zs) in [(4, range(Z0 + 1, Z1)), (5, range(Z0 + 2, Z1 - 1)), (6, range(Z0 + 3, Z1 - 2))]:
    for z in zs:
        put(X0, y, z, "spruce_planks"); put(X1, y, z, "spruce_planks")
for p in [(4, 6, 3), (5, 6, 3), (3, 4, Z1)]:            # caved-in roof
    blocks.pop(p)
leaves = {"persistent": "true", "distance": "7", "waterlogged": "false"}
for p in [(2, 5, 2), (3, 5, 2), (8, 6, 6), (9, 7, 5), (8, 5, 7)]:
    put(*p, "oak_leaves", leaves)

# the old two-stage antenna on the ridge
put(7, 8, 4, "tarbound:tar_antenna", {"facing": "up", "stage": "base2", "mode": "commune"})
put(7, 9, 4, "tarbound:tar_antenna", {"facing": "up", "stage": "top2", "mode": "commune"})

# inside: bed, nightstand barrel with a little antenna, the researcher's desk
put(7, 1, 7, "brown_bed", {"facing": "east", "part": "foot", "occupied": "false"})
put(8, 1, 7, "brown_bed", {"facing": "east", "part": "head", "occupied": "false"})
put(8, 1, 6, "barrel", {"facing": "up", "open": "false"}, LOOT)
put(8, 2, 6, "tarbound:tar_antenna", {"facing": "up", "stage": "single", "mode": "scry"})
put(2, 1, 3, "crafting_table")
put(2, 2, 3, "candle", {"candles": "3", "lit": "false", "waterlogged": "false"})
put(2, 1, 4, "lectern", {"facing": "east", "has_book": "false", "powered": "false"})
put(2, 1, 5, "chest", {"facing": "east", "type": "single", "waterlogged": "false"}, LOOT)
put(4, 1, 7, "bookshelf"); put(5, 1, 7, "bookshelf")
put(4, 2, 7, "potted_dead_bush")
put(5, 2, 7, "candle", {"candles": "1", "lit": "false", "waterlogged": "false"})
put(3, 1, 7, "cauldron")
put(6, 1, 2, "composter", {"level": "4"})
# nature moving in
put(3, 1, 5, "moss_carpet"); put(4, 1, 6, "moss_carpet")
put(4, 1, 5, "short_grass"); put(8, 1, 2, "fern")
for p in [(2, 3, 2), (8, 3, 2), (2, 3, 7), (5, 5, 4), (6, 4, 5)]:
    put(*p, "cobweb")
vine = lambda **f: {"north": "false", "east": "false", "south": "false", "west": "false", "up": "false", **{k: "true" for k in f}}
for y in (1, 2, 3):
    for z in (3, 4, 6):
        if (X0, y, z) in blocks:
            put(0, y, z, "vine", vine(east=1))
    for x in (2, 3, 8):
        if (x, y, Z0) in blocks:
            put(x, y, 0, "vine", vine(south=1))
    for x in (6, 7):
        if (x, y, Z1) in blocks:
            put(x, y, 9, "vine", vine(north=1))
for z in (5, 6):
    if (X1, 1, z) in blocks:
        put(10, 1, z, "vine", vine(west=1)); put(10, 2, z, "vine", vine(west=1))

# ---- write
palette, index = [], {}
def state_id(name, props):
    key = (name, tuple(sorted(props.items())))
    if key not in index:
        index[key] = len(palette)
        c = Compound({"Name": String(name)})
        if props:
            c["Properties"] = Compound({k: String(v) for k, v in props.items()})
        palette.append(c)
    return index[key]

air = state_id("minecraft:air", {})
out = []
for x in range(SX):
    for y in range(SY):
        for z in range(SZ):
            if (x, y, z) not in blocks and not (X0 <= x <= X1 and Z0 <= z <= Z1):
                continue  # outside the cabin: leave the world as it is (structure void)
            name, props, nbt = blocks.get((x, y, z), ("minecraft:air", {}, None))
            b = Compound({"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(state_id(name, props))})
            if nbt:
                b["nbt"] = Compound({k: String(v) for k, v in nbt.items()})
            out.append(b)

root = Compound({
    "DataVersion": Int(3955),
    "size": List[Int]([Int(SX), Int(SY), Int(SZ)]),
    "palette": List[Compound](palette),
    "blocks": List[Compound](out),
    "entities": List[Compound]([]),
})
nbtlib.File(root).save("src/main/resources/data/tarbound/structure/researcher_cabin.nbt", gzipped=True)
print(len(blocks), "blocks,", len(palette), "states")
