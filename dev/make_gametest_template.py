#!/usr/bin/env python3
"""Writes the empty playground the in-dev game tests build on: 16x10x16, a two-block stone floor (y 0-1), air above.

Run from the repo root: python3 dev/make_gametest_template.py
The template ships only in dev runs (the jar task leaves data/tarbound/structure/gametest out).
"""
import nbtlib
from nbtlib.tag import Compound, Int, List, String

W, H, D = 16, 10, 16
palette = [Compound({"Name": String("minecraft:stone")}), Compound({"Name": String("minecraft:air")})]
blocks = []
for x in range(W):
    for z in range(D):
        for y in range(H):
            blocks.append(Compound({"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(0 if y < 2 else 1)}))
root = Compound({
    "DataVersion": Int(3955),
    "size": List[Int]([Int(W), Int(H), Int(D)]),
    "palette": List[Compound](palette),
    "blocks": List[Compound](blocks),
    "entities": List[Compound]([]),
})
nbtlib.File(root).save("src/main/resources/data/tarbound/structure/gametest/playground.nbt", gzipped=True)
print("playground.nbt written")
