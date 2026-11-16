"""Rebuilds the tarbound.ponder.* lang keys in en_us.json from TarboundScenes.java and the plugin's tag.
Run from the repo root after editing scene text. Ponder numbers each scene's texts text_1, text_2...
in the order showText(...).text("...") is called, which is the order they appear in the source."""
import json, re, collections

SCENES = 'src/main/java/com/tarbound/compat/ponder/TarboundScenes.java'
PLUGIN = 'src/main/java/com/tarbound/compat/ponder/TarboundPonderPlugin.java'
LANG = 'src/main/resources/assets/tarbound/lang/en_us.json'

src = open(SCENES, encoding='utf-8').read()
keys = collections.OrderedDict()
token = re.compile(r'scene\.title\("([^"]+)",\s*"([^"]*)"\)|\.text\("((?:[^"\\]|\\.)*)"\)')
scene, n = None, 0
for m in token.finditer(src):
    if m.group(1):
        scene, n = m.group(1), 0
        keys['tarbound.ponder.%s.header' % scene] = m.group(2)
    else:
        n += 1
        keys['tarbound.ponder.%s.text_%d' % (scene, n)] = bytes(m.group(3), 'utf-8').decode('unicode_escape')

plugin = open(PLUGIN, encoding='utf-8').read()
title = re.search(r'\.title\("([^"]*)"\)', plugin).group(1)
desc = re.search(r'\.description\("([^"]*)"\)', plugin).group(1)
keys['tarbound.ponder.tag.tarbound'] = title
keys['tarbound.ponder.tag.tarbound.description'] = desc

lang = json.load(open(LANG, encoding='utf-8'), object_pairs_hook=collections.OrderedDict)
for k in [k for k in lang if k.startswith('tarbound.ponder.')]:
    del lang[k]
lang.update(keys)
with open(LANG, 'w', encoding='utf-8') as f:
    json.dump(lang, f, indent=2, ensure_ascii=False)
    f.write('\n')
print('%d ponder keys' % len(keys))
