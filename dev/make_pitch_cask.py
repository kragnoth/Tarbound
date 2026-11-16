"""Writes the Pitch Cask's model, blockstate, item model, loot table and recipe.
Run from the repo root:  python3 dev/make_pitch_cask.py"""
import json
import os
import random
from PIL import Image

A = 'src/main/resources/assets/tarbound/'
D = 'src/main/resources/data/tarbound/'
r = random.Random(4471)

OAK = [(92, 68, 42), (104, 77, 47), (116, 86, 53), (128, 96, 60)]
PITCH = (18, 14, 12)
IRON = [(52, 52, 54), (66, 65, 66), (82, 80, 80), (100, 97, 95)]
RUST = (110, 70, 44)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def save(im, name):
    im.save(A + f'textures/block/{name}.png')


# The textures (pitch_cask_side, _top, _hoop) are drawn by make_wood_textures.py with the other wood.


def el(fr, to, tex, faces=('north', 'south', 'east', 'west', 'up', 'down')):
    return {'from': fr, 'to': to, 'faces': {f: {'texture': tex} for f in faces}}


elements = [
    el([2, 0, 2], [14, 3, 14], '#side', ('north', 'south', 'east', 'west')),
    el([2, 13, 2], [14, 16, 14], '#side', ('north', 'south', 'east', 'west')),
    {'from': [2, 0, 2], 'to': [14, 3, 14], 'faces': {'down': {'texture': '#top', 'cullface': 'down'}}},
    {'from': [2, 13, 2], 'to': [14, 16, 14], 'faces': {'up': {'texture': '#top', 'cullface': 'up'}}},
    el([1, 3, 1], [15, 13, 15], '#side', ('north', 'south', 'east', 'west')),
    el([1, 3, 1], [15, 13, 15], '#top', ('up', 'down')),
    # hoops: two on the belly, one round each end
    el([0.75, 3, 0.75], [15.25, 4, 15.25], '#hoop', ('north', 'south', 'east', 'west')),
    el([0.75, 12, 0.75], [15.25, 13, 15.25], '#hoop', ('north', 'south', 'east', 'west')),  # side faces only: no coplanar tops
    el([1.75, 1, 1.75], [14.25, 2, 14.25], '#hoop', ('north', 'south', 'east', 'west')),
    el([1.75, 14, 1.75], [14.25, 15, 14.25], '#hoop', ('north', 'south', 'east', 'west')),
]
model = {'parent': 'minecraft:block/block',
         'textures': {'particle': 'tarbound:block/pitch_cask_side', 'side': 'tarbound:block/pitch_cask_side',
                      'top': 'tarbound:block/pitch_cask_top', 'hoop': 'tarbound:block/pitch_cask_hoop'},
         'elements': elements}


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(json.dumps(obj, indent=2) + '\n')


w(A + 'models/block/pitch_cask.json', model)
w(A + 'blockstates/pitch_cask.json', {'variants': {'': {'model': 'tarbound:block/pitch_cask'}}})
w(A + 'models/item/pitch_cask.json', {'parent': 'tarbound:block/pitch_cask'})
w(D + 'loot_table/blocks/pitch_cask.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [
    {'type': 'minecraft:item', 'name': 'tarbound:pitch_cask', 'functions': [
        {'function': 'minecraft:copy_components', 'source': 'block_entity', 'include': ['tarbound:cask_fluid']}]}],
    'conditions': [{'condition': 'minecraft:survives_explosion'}]}], 'random_sequence': 'tarbound:blocks/pitch_cask'})
w(D + 'recipe/pitch_cask.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['PIP', 'P P', 'PIP'],
    'key': {'P': {'item': 'tarbound:tar_soaked_planks'}, 'I': {'item': 'minecraft:iron_ingot'}},
    'result': {'id': 'tarbound:pitch_cask', 'count': 1}})
print('pitch cask written')
