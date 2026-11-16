"""Writes Tarbound's ponder schematics (gzip NBT structure files). Run from the repo root."""
import gzip, struct

def s(x): b = x.encode(); return struct.pack('>H', len(b)) + b
def named(tid, name, payload): return bytes([tid]) + s(name) + payload
def i(v): return struct.pack('>i', v)
def compound(items):  # items: list of (tid, name, payload)
    return b''.join(named(t, n, p) for t, n, p in items) + b'\x00'
def lst(tid, payloads): return bytes([tid]) + i(len(payloads)) + b''.join(payloads)
def ints(vals): return lst(3, [i(v) for v in vals])
def string(v): return s(v)

def structure(size, blocks):
    palette, index, out = [], {}, []
    for pos, name, props in blocks:
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            entry = [(8, 'Name', string(name))]
            if props:
                entry.append((10, 'Properties', compound([(8, k, string(v)) for k, v in sorted(props.items())])))
            palette.append(compound(entry))
        out.append(compound([(9, 'pos', ints(pos)), (3, 'state', i(index[key]))]))
    root = compound([
        (3, 'DataVersion', i(3955)),
        (9, 'size', ints(size)),
        (9, 'palette', lst(10, palette)),
        (9, 'blocks', lst(10, out)),
        (9, 'entities', lst(10, [])),
    ])
    return named(10, '', root[:-1] + b'\x00')  # root compound

def plate(n):
    return [((x, 0, z), 'minecraft:smooth_stone', {}) for x in range(n) for z in range(n)]

TAR = 'tarbound:tar'
FONT = ('tarbound:scrying_font', {'facing': 'south'})
BARREL = ('minecraft:barrel', {'facing': 'up', 'open': 'false'})
RELIQUARY = ('tarbound:reliquary', {'facing': 'south', 'book': '2', 'cover': '0'})
LECTERN = ('tarbound:scribing_lectern', {'facing': 'south'})
FURNACE = ('minecraft:furnace', {'facing': 'south', 'lit': 'false'})
BLAST = ('minecraft:blast_furnace', {'facing': 'south', 'lit': 'false'})
TABLE = ('minecraft:crafting_table', {})

def ant(stage, mode='commune'):
    return ('tarbound:tar_antenna', {'facing': 'up', 'stage': stage, 'mode': mode})

def put(blocks, pos, block):
    blocks.append((pos, block[0], block[1]))

def tar_pool():
    blocks = plate(5)
    for x in (1, 2, 3):
        blocks.append(((x, 1, 2), TAR, {}))
    for x in (1, 3):
        put(blocks, (x, 1, 3), BARREL)
    put(blocks, (2, 1, 1), FONT)
    put(blocks, (0, 1, 2), TABLE)
    return structure((5, 2, 5), blocks)

def tar_antenna():
    blocks = plate(5)
    put(blocks, (1, 1, 1), FONT)
    put(blocks, (3, 1, 3), BARREL)
    put(blocks, (3, 2, 3), ant('single'))
    return structure((5, 3, 5), blocks)

def antenna_range():
    blocks = plate(9)
    put(blocks, (1, 1, 1), FONT)
    # cell tower on the ground, closest to the radio
    put(blocks, (3, 1, 2), ant('base3'))
    put(blocks, (3, 2, 2), ant('mid3'))
    put(blocks, (3, 3, 2), ant('top3'))
    # rooftop aerial on the ground
    put(blocks, (8, 1, 6), ant('base2'))
    put(blocks, (8, 2, 6), ant('top2'))
    # rabbit ears on a barrel
    put(blocks, (5, 1, 8), BARREL)
    put(blocks, (5, 2, 8), ant('single'))
    return structure((9, 4, 9), blocks)

def antenna_modes():
    blocks = plate(5)
    put(blocks, (4, 1, 0), FONT)
    for x in (1, 2, 3):
        put(blocks, (x, 1, 2), BARREL)
        put(blocks, (x, 2, 2), ant('single'))
    return structure((5, 3, 5), blocks)

def reliquary():
    blocks = plate(5)
    put(blocks, (1, 1, 1), FONT)
    blocks.append(((1, 1, 2), TAR, {}))
    blocks.append(((2, 1, 2), TAR, {}))
    put(blocks, (1, 1, 3), BARREL)
    put(blocks, (3, 1, 2), FURNACE)
    put(blocks, (3, 2, 2), RELIQUARY)
    return structure((5, 3, 5), blocks)

def contracts():
    blocks = plate(5)
    put(blocks, (1, 1, 1), FONT)
    blocks.append(((2, 1, 1), TAR, {}))
    put(blocks, (2, 1, 0), BARREL)
    for x in (3, 4):
        put(blocks, (x, 1, 2), FURNACE)
        put(blocks, (x, 2, 2), RELIQUARY)
    put(blocks, (1, 1, 3), LECTERN)
    return structure((5, 3, 5), blocks)

def crafting_station():
    blocks = plate(5)
    put(blocks, (1, 1, 1), FONT)
    blocks.append(((1, 1, 2), TAR, {}))
    blocks.append(((2, 1, 2), TAR, {}))
    put(blocks, (1, 1, 3), BARREL)
    put(blocks, (2, 1, 3), BARREL)
    for x in (3, 4):
        put(blocks, (x, 1, 0), TABLE)
        put(blocks, (x, 2, 0), RELIQUARY)
    return structure((5, 3, 5), blocks)

def standing_contracts():
    blocks = plate(5)
    put(blocks, (1, 1, 1), FONT)
    blocks.append(((1, 1, 2), TAR, {}))
    put(blocks, (1, 1, 3), BARREL)
    put(blocks, (3, 1, 1), BLAST)
    put(blocks, (3, 2, 1), RELIQUARY)
    put(blocks, (3, 1, 3), LECTERN)
    return structure((5, 3, 5), blocks)

def spirit_board():
    blocks = plate(5)
    put(blocks, (2, 1, 2), FONT)
    blocks.append(((2, 1, 3), TAR, {}))
    put(blocks, (1, 1, 3), BARREL)
    put(blocks, (3, 1, 3), BARREL)
    return structure((5, 2, 5), blocks)

def feeding_spirits():
    blocks = plate(5)
    put(blocks, (2, 1, 2), FONT)
    put(blocks, (2, 2, 2), ('minecraft:hopper', {'facing': 'down', 'enabled': 'true'}))
    blocks.append(((2, 1, 3), TAR, {}))
    put(blocks, (1, 1, 3), BARREL)
    put(blocks, (3, 1, 3), BARREL)
    put(blocks, (3, 1, 2), ('minecraft:comparator', {'facing': 'west', 'mode': 'compare', 'powered': 'false'}))
    put(blocks, (4, 1, 2), ('minecraft:redstone_lamp', {'lit': 'false'}))
    return structure((5, 3, 5), blocks)

SCENES = [feeding_spirits, tar_pool, tar_antenna, antenna_range, antenna_modes, reliquary, contracts, crafting_station, standing_contracts, spirit_board]

for scene in SCENES:
    with gzip.open('src/main/resources/assets/tarbound/ponder/%s.nbt' % scene.__name__, 'wb') as f:
        f.write(scene())
