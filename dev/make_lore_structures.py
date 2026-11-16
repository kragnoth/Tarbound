"""Builds Tarbound's lore structures (0.51, 0.60):
  data/tarbound/structure/trapper_cabin.nbt          a trapper's cabin, R.'s neighbour (forest, with the researcher cabin)
  data/tarbound/structure/burned_cabin.nbt           a cabin someone tried to burn the seep out of
  data/tarbound/structure/wayside_shrine.nbt         a roadside candle shrine, a Reliquary still minding a dead furnace
  data/tarbound/structure/drowned_radio.nbt          a radio set sunk in a swamp tar seep, its mast sticking out (placed 2 down)
  data/tarbound/structure/charnel_crypt.nbt          a buried bone chapel under a ladder hatch (placed 10 down)
Run from the repo root:  python3 dev/make_lore_structures.py   (needs: pip install nbtlib)
Positions outside a building's footprint are left out (structure void), so the world shows through."""
import math
import random
import nbtlib
from nbtlib.tag import Compound, List, Int, String

OUT = "src/main/resources/data/tarbound/structure/"


class Template:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.blocks = {}      # (x,y,z) -> (name, props, nbt)
        self.solid_air = set()  # positions written as air (cleared), not void

    def put(self, x, y, z, name, props=None, nbt=None):
        if not (0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]):
            return
        name = name if ":" in name else "minecraft:" + name
        self.blocks[(x, y, z)] = (name, dict(props or {}), nbt)
        self.solid_air.discard((x, y, z))

    def air(self, x, y, z):
        self.blocks.pop((x, y, z), None)
        self.solid_air.add((x, y, z))

    def get(self, x, y, z):
        b = self.blocks.get((x, y, z))
        return b[0] if b else None

    def save(self, path, remap=None):
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

        out = []
        positions = sorted(set(self.blocks) | self.solid_air)
        for p in positions:
            if p in self.blocks:
                name, props, nbt = self.blocks[p]
                if remap:
                    name, props = remap(name, props)
            else:
                name, props, nbt = "minecraft:air", {}, None
            b = Compound({"pos": List[Int]([Int(c) for c in p]), "state": Int(state_id(name, props))})
            if nbt:
                vals = {}
                for k, v in nbt.items():
                    if isinstance(v, int):
                        vals[k] = Int(v)
                    else:
                        v = str(v)
                        vals[k] = String(v)
                b["nbt"] = Compound(vals)
            out.append(b)
        root = Compound({
            "DataVersion": Int(3955),
            "size": List[Int]([Int(c) for c in self.size]),
            "palette": List[Compound](palette),
            "blocks": List[Compound](out),
            "entities": List[Compound]([]),
        })
        nbtlib.File(root).save(OUT + path, gzipped=True)
        print(path, len(out), "positions,", len(palette), "states")


def vine(**faces):
    return {"north": "false", "east": "false", "south": "false", "west": "false", "up": "false", **{k: "true" for k in faces}}


LEAVES = {"persistent": "true", "distance": "7", "waterlogged": "false"}
TAR = ("tarbound:tar", {"level": "0"})


def clear_box(t, x0, x1, y0, y1, z0, z1):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                t.air(x, y, z)


# ----------------------------------------------------------------------------------------------- trapper cabin
def trapper_cabin():
    t = Template(11, 8, 10)
    LOOT = {"LootTable": "tarbound:chests/trapper_cabin"}
    X0, X1, Z0, Z1 = 1, 7, 1, 6
    clear_box(t, X0, X1, 1, 6, Z0, Z1)
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            t.put(x, 0, z, "oak_planks" if (x not in (X0, X1) and z not in (Z0, Z1)) else "cobblestone")
    for y in range(1, 4):
        for x in range(X0, X1 + 1):
            for z in range(Z0, Z1 + 1):
                if x in (X0, X1) or z in (Z0, Z1):
                    corner = x in (X0, X1) and z in (Z0, Z1)
                    t.put(x, y, z, "dark_oak_log" if corner else "oak_log", {"axis": "y"} if corner else {"axis": "x" if z in (Z0, Z1) else "z"})
    for y in (1, 2):
        t.air(4, y, Z0)                     # doorway
    for p in [(2, 2, Z0), (6, 2, Z0), (X1, 2, 3), (X0, 2, 4)]:
        t.put(*p, "glass_pane" if p[0] == 6 else "air")
        if p[0] != 6:
            t.air(*p)
    # flat sloped roof (stairs facing south), one plank overhang
    for x in range(X0 - 1, X1 + 2):
        for z in range(Z0, Z1 + 1):
            y = 4 if z <= 3 else 5
            t.put(x, y, z, "spruce_slab", {"type": "bottom", "waterlogged": "false"})
    t.air(5, 4, 2); t.air(5, 4, 3)   # roof sagged in
    # lean-to drying rack on the east side
    for z in (2, 5):
        t.put(9, 1, z, "spruce_fence"); t.put(9, 2, z, "spruce_fence")
    for z in range(2, 6):
        t.put(9, 3, z, "spruce_slab", {"type": "bottom", "waterlogged": "false"})
    t.put(9, 1, 3, "hay_block", {"axis": "y"})
    t.put(9, 2, 4, "tripwire_hook", {"facing": "south", "attached": "false", "powered": "false"})
    # inside
    t.put(2, 1, 5, "red_bed", {"facing": "east", "part": "foot", "occupied": "false"})
    t.put(3, 1, 5, "red_bed", {"facing": "east", "part": "head", "occupied": "false"})
    t.put(6, 1, 5, "chest", {"facing": "west", "type": "single", "waterlogged": "false"}, LOOT)
    t.put(6, 1, 4, "smoker", {"facing": "west", "lit": "false"})
    t.put(2, 1, 2, "barrel", {"facing": "up", "open": "false"}, LOOT)
    t.put(2, 2, 2, "candle", {"candles": "4", "lit": "false", "waterlogged": "false"})
    t.put(6, 1, 2, "fletching_table")
    t.put(6, 2, 2, "lantern", {"hanging": "false", "waterlogged": "false"})
    t.put(4, 1, 3, "white_carpet")
    for p in [(2, 3, 3), (6, 3, 5), (3, 3, 2)]:
        t.put(*p, "cobweb")
    # a snare line out to a small seep
    t.put(4, 0, 8, *TAR); t.put(5, 0, 8, *TAR)
    t.put(3, 1, 8, "tripwire_hook", {"facing": "east", "attached": "false", "powered": "false"})
    t.put(6, 1, 8, "tripwire_hook", {"facing": "west", "attached": "false", "powered": "false"})
    t.put(4, 1, 9, "bone_block", {"axis": "y"})
    for y in (1, 2):
        if t.get(X0, y, 3):
            t.put(0, y, 3, "vine", vine(east=1))
    t.put(8, 5, 4, "oak_leaves", LEAVES); t.put(0, 4, 5, "oak_leaves", LEAVES)
    t.save("trapper_cabin.nbt")


# ----------------------------------------------------------------------------------------------- burned cabin
def burned_cabin():
    t = Template(11, 7, 10)
    LOOT = {"LootTable": "tarbound:chests/burned_cabin"}
    rnd = random.Random(31)
    X0, X1, Z0, Z1 = 1, 9, 1, 8
    clear_box(t, X0, X1, 1, 5, Z0, Z1)
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            edge = x in (X0, X1) or z in (Z0, Z1)
            t.put(x, 0, z, "cobblestone" if edge else rnd.choice(["coal_block", "blackstone", "spruce_planks", "coarse_dirt"]))
    # the crater the fire left: a tar pool where the floor was
    for (x, z) in [(4, 4), (5, 4), (4, 5), (5, 5), (6, 5), (5, 6)]:
        t.put(x, 0, z, *TAR)
    for (x, z) in [(3, 4), (6, 4), (3, 5), (4, 6), (6, 6)]:
        t.put(x, 0, z, "magma_block")
    # charred wall stubs, higher at the corners
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if not (x in (X0, X1) or z in (Z0, Z1)):
                continue
            corner = x in (X0, X1) and z in (Z0, Z1)
            h = 4 if corner else rnd.choice([0, 1, 1, 2, 2, 3])
            for y in range(1, h + 1):
                block = "stripped_dark_oak_log" if corner else rnd.choice(["stripped_dark_oak_wood", "dark_oak_planks", "blackstone"])
                t.put(x, y, z, block, {"axis": "y"} if block.startswith("stripped") else None)
    for y in (1, 2, 3):
        t.air(5, y, Z0)
    # a fallen ridge beam
    for x in range(2, 9):
        if x != 5:
            t.put(x, 1 if x < 5 else 2, 3, "stripped_dark_oak_log", {"axis": "x"})
    t.put(8, 1, 7, "chest", {"facing": "west", "type": "single", "waterlogged": "false"}, LOOT)
    t.put(2, 1, 7, "lantern", {"hanging": "false", "waterlogged": "false"})
    t.put(2, 1, 2, "campfire", {"facing": "south", "lit": "false", "signal_fire": "false", "waterlogged": "false"})
    t.put(7, 1, 2, "tarbound:tar_antenna", {"facing": "up", "stage": "single", "mode": "commune"})
    for (x, z) in [(3, 2), (7, 6), (2, 5)]:
        t.put(x, 1, z, "dead_bush" if t.get(x, 0, z) == "minecraft:coarse_dirt" else "air")
        if t.get(x, 1, z) == "minecraft:air":
            t.air(x, 1, z)
    t.put(6, 1, 7, "candle", {"candles": "2", "lit": "false", "waterlogged": "false"})
    t.save("burned_cabin.nbt")


# ----------------------------------------------------------------------------------------------- wayside shrine
def wayside_shrine():
    """7x7. Layer 0 replaces the ground. A little roofed niche by the road: a dead furnace with a Reliquary still on
    it, a Votive Skull and a Votive Rack either side, an offering barrel. Faces south (the jigsaw rotates it)."""
    t = Template(7, 6, 7)
    LOOT = {"LootTable": "tarbound:chests/wayside_shrine"}
    rnd = random.Random(61)
    X0, X1, Z0, Z1 = 1, 5, 1, 4
    clear_box(t, X0, X1, 1, 4, Z0, Z1)
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            t.put(x, 0, z, "stone_bricks" if z == Z0 or x in (X0, X1) else rnd.choice(["cobblestone", "cobblestone", "gravel"]))
    for z in (5, 6):                                  # the path it stands beside
        t.put(3, 0, z, "gravel"); t.put(2, 0, z, "coarse_dirt"); t.put(4, 0, z, "coarse_dirt")
    for x in range(X0, X1 + 1):                       # back wall
        for y in range(1, 4):
            t.put(x, y, Z0, rnd.choice(["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks"]))
    t.put(3, 3, Z0, "chiseled_stone_bricks")
    for x in (X0, X1):                                # front posts
        for y in range(1, 4):
            t.put(x, y, 3, "spruce_fence")
    for x in range(X0, X1 + 1):                       # slab roof, one sagging gap
        for z in range(Z0, 4):
            t.put(x, 4, z, "spruce_slab", {"type": "bottom", "waterlogged": "false"})
    t.air(4, 4, 3)
    t.put(3, 1, 2, "furnace", {"facing": "south", "lit": "false"})
    t.put(3, 2, 2, "tarbound:reliquary", {"facing": "south", "book": "1", "cover": "2", "attach": "down"})
    t.put(2, 1, 2, "tarbound:votive_skull", {"rotation": "0", "powered": "false", "lit": "false"})
    t.put(4, 1, 2, "tarbound:votive_rack", {"facing": "south", "lit": "false", "skull": "false"})
    t.put(1, 1, 2, "barrel", {"facing": "up", "open": "false"}, LOOT)
    t.put(5, 1, 2, "potted_wither_rose")
    t.put(2, 1, 4, "candle", {"candles": "3", "lit": "false", "waterlogged": "false"})
    t.put(4, 1, 4, "candle", {"candles": "2", "lit": "false", "waterlogged": "false"})
    t.put(X1, 3, 2, "cobweb")
    for y in (1, 2):
        t.put(0, y, Z0, "vine", vine(east=1))
    t.save("wayside_shrine.nbt")


# ----------------------------------------------------------------------------------------------- drowned radio
def drowned_radio():
    """9x9, placed 2 blocks down (start_height -2): layer 0 is the pool's mud floor, layers 1-2 tar (layer 2 is ground
    level), layer 3 up is above ground. A radio set sunk to its dials in a seep, a broadcast mast rising out of it, a
    half-drowned boardwalk and the operator's chest under a collapsed tarp on the bank."""
    t = Template(9, 9, 9)
    LOOT = {"LootTable": "tarbound:chests/drowned_radio"}
    rnd = random.Random(73)
    cx, cz = 4, 4
    pool = {(x, z) for x in range(9) for z in range(9) if (x - cx) ** 2 + (z - cz) ** 2 <= 9.5}
    rim = {(x, z) for x in range(9) for z in range(9) if (x, z) not in pool and (x - cx) ** 2 + (z - cz) ** 2 <= 16.5}
    for (x, z) in pool:
        t.put(x, 0, z, "mud")
        t.put(x, 1, z, *TAR); t.put(x, 2, z, *TAR)
        for y in (3, 4):
            t.air(x, y, z)                            # nothing grows on the tar
    for (x, z) in rim:                                # a mud bank holds the seep in
        for y in (0, 1, 2):
            t.put(x, y, z, "mud" if y < 2 else rnd.choice(["mud", "mud", "coarse_dirt", "packed_mud"]))
    # the set: a two-wide cabinet sunk to its dials, faceplate on the north side
    for x in (4, 5):
        t.put(x, 1, 4, "dark_oak_planks"); t.put(x, 2, 4, "dark_oak_planks")
    t.put(4, 3, 4, "note_block"); t.put(5, 3, 4, "jukebox")
    for x in (4, 5):
        t.put(x, 3, 3, "dark_oak_trapdoor", {"facing": "north", "half": "top", "open": "true", "powered": "false", "waterlogged": "false"})
    t.put(4, 4, 4, "tarbound:tar_antenna", {"facing": "up", "stage": "base3", "mode": "commune"})
    t.put(4, 5, 4, "tarbound:tar_antenna", {"facing": "up", "stage": "mid3", "mode": "commune"})
    t.put(4, 6, 4, "tarbound:tar_antenna", {"facing": "up", "stage": "top3", "mode": "commune"})
    t.put(5, 4, 4, "chain", {"axis": "y", "waterlogged": "false"})
    # a boardwalk out from the west bank, half gone under
    for x in (1, 2, 3):
        if x != 2:
            t.put(x, 2, 4, "dark_oak_slab", {"type": "top", "waterlogged": "false"})
    t.put(1, 3, 3, "dark_oak_fence"); t.put(1, 3, 5, "dark_oak_fence")
    # the operator's corner on the south-east bank: a fallen tarp over the chest, a soul lantern on a post
    t.put(7, 3, 7, "chest", {"facing": "west", "type": "single", "waterlogged": "false"}, LOOT)
    t.put(8, 3, 6, "spruce_fence"); t.put(8, 4, 6, "spruce_fence")
    t.put(7, 4, 7, "black_carpet"); t.put(8, 5, 6, "black_carpet")
    t.put(6, 3, 8, "spruce_fence"); t.put(6, 4, 8, "soul_lantern", {"hanging": "false", "waterlogged": "false"})
    for (x, z) in [(1, 7), (7, 1), (2, 1)]:
        if (x, z) in rim:
            t.put(x, 3, z, "dead_bush")
    if (0, 4) in rim:
        t.put(0, 3, 4, "candle", {"candles": "1", "lit": "false", "waterlogged": "false"})
    t.save("drowned_radio.nbt")


# ----------------------------------------------------------------------------------------------- charnel crypt
def charnel_crypt():
    """9x9, placed 10 blocks down (start_height -10): the chapel is layers 0-5, a ladder shaft rises through the centre
    to a trapdoor hatch at layer 10 (ground level), and four candle posts mark it on the surface. The shaft is at the
    footprint's centre, where the jigsaw measures the ground, so the hatch lands flush."""
    t = Template(9, 13, 9)
    LOOT = {"LootTable": "tarbound:chests/charnel_crypt"}
    rnd = random.Random(89)
    # shell: deepslate tile floor and ceiling, bone walls, blackstone corner pillars
    for x in range(9):
        for z in range(9):
            edge = x in (0, 8) or z in (0, 8)
            t.put(x, 0, z, "bone_block" if edge else rnd.choice(["deepslate_tiles", "deepslate_tiles", "polished_deepslate"]),
                  {"axis": "y"} if edge else None)
            t.put(x, 5, z, "deepslate_tiles" if not edge else "polished_blackstone_bricks")
            for y in range(1, 5):
                if edge:
                    corner = x in (0, 8) and z in (0, 8)
                    t.put(x, y, z, "polished_blackstone_bricks" if corner else "bone_block",
                          None if corner else {"axis": "y"})
                else:
                    t.air(x, y, z)
    # the centre pillar the ladder clings to, and the shaft above it
    for y in range(1, 5):
        t.put(4, y, 3, "polished_blackstone_bricks")
    t.air(4, 5, 4)
    for y in range(6, 10):
        for x in (3, 4, 5):
            for z in (3, 4, 5):
                if (x, z) != (4, 4):
                    t.put(x, y, z, rnd.choice(["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks"]))
        t.air(4, y, 4)
    for y in range(1, 10):
        t.put(4, y, 4, "ladder", {"facing": "south", "waterlogged": "false"})
    for x in (3, 4, 5):                               # a stone collar flush with the ground, and the hatch
        for z in (3, 4, 5):
            if (x, z) != (4, 4):
                t.put(x, 10, z, "mossy_stone_bricks")
    t.put(4, 10, 4, "spruce_trapdoor", {"facing": "south", "half": "top", "open": "false", "powered": "false", "waterlogged": "false"})
    for (x, z) in [(3, 3), (5, 3), (3, 5), (5, 5)]:  # candle posts round the hatch
        t.put(x, 11, z, "mossy_cobblestone_wall", {"up": "true", "north": "none", "south": "none", "east": "none", "west": "none", "waterlogged": "false"})
        t.put(x, 12, z, "candle", {"candles": "1", "lit": "false", "waterlogged": "false"})
    # the charnel: an Ossuary keeping count on the south wall, racks of candles down the sides
    t.put(4, 1, 7, "tarbound:ossuary", {"facing": "north"})
    t.put(1, 1, 3, "tarbound:votive_rack", {"facing": "east", "lit": "false", "skull": "true"})
    t.put(1, 1, 5, "tarbound:votive_rack", {"facing": "east", "lit": "false", "skull": "false"})
    t.put(7, 1, 3, "tarbound:votive_rack", {"facing": "west", "lit": "false", "skull": "false"})
    t.put(7, 1, 5, "tarbound:votive_rack", {"facing": "west", "lit": "false", "skull": "true"})
    t.put(4, 3, 1, "tarbound:votive_wall_skull", {"facing": "south", "powered": "false", "lit": "false"})
    t.put(6, 1, 7, "chest", {"facing": "north", "type": "single", "waterlogged": "false"}, LOOT)
    t.put(2, 1, 7, "lectern", {"facing": "north", "has_book": "false", "powered": "false"})
    # bones heaped in the corners, a stub of candle on each heap, cobwebs overhead
    for (x, z) in [(1, 1), (7, 1), (1, 7), (7, 7)]:
        t.put(x, 1, z, "bone_block", {"axis": rnd.choice(["x", "z"])})
        t.put(x, 2, z, "candle", {"candles": "1", "lit": "false", "waterlogged": "false"})
        t.put(x, 4, z, "cobweb")
    for (x, z) in [(3, 2), (5, 2), (3, 6), (5, 6)]:
        t.put(x, 1, z, "candle", {"candles": str(rnd.randint(1, 4)), "lit": "false", "waterlogged": "false"})
    t.save("charnel_crypt.nbt")


if __name__ == "__main__":
    trapper_cabin()
    burned_cabin()
    wayside_shrine()
    drowned_radio()
    charnel_crypt()
