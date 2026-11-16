"""The Votive Skull item icon: a weathered skull with a stub of candle burning on its crown, wax run down its brow.
Run from the repo root:  python3 dev/make_votive_skull.py"""
from PIL import Image

OUT = 'src/main/resources/assets/tarbound/textures/item/votive_skull.png'
BONE = [(92, 86, 74), (150, 142, 122), (192, 184, 160), (222, 215, 192)]
HOLE = (30, 24, 22)
WAX = [(150, 132, 96), (196, 178, 132), (226, 212, 170)]
FLAME = [(255, 238, 160), (250, 178, 64), (212, 96, 40)]
WICK = (40, 32, 28)

im = Image.new('RGBA', (16, 16))
px = im.load()


def put(x, y, c):
    px[x, y] = c + (255,)


# skull: rows 7-15, a rounded cranium, eye sockets, nasal hole, teeth
shape = {7: (5, 10), 8: (4, 11), 9: (3, 12), 10: (3, 12), 11: (3, 12), 12: (3, 12), 13: (4, 11), 14: (5, 10), 15: (5, 10)}
for y, (x0, x1) in shape.items():
    for x in range(x0, x1 + 1):
        tone = 2
        if x == x0 or y == 15:
            tone = 0
        elif x == x1 or y >= 13:
            tone = 1
        elif y <= 8 and x <= 6:
            tone = 3  # light on the crown
        put(x, y, BONE[tone])
for (x, y) in ((5, 10), (6, 10), (5, 11), (6, 11), (9, 10), (10, 10), (9, 11), (10, 11)):  # eye sockets
    put(x, y, HOLE)
put(7, 12, HOLE)
put(8, 12, HOLE)
for x in (6, 8):  # gaps between the teeth
    put(x, 14, BONE[0])
# candle stub on the crown, wax run down the brow
for y in range(3, 7):
    put(7, y, WAX[2] if y == 3 else WAX[1])
    put(8, y, WAX[1] if y == 3 else WAX[0])
for (x, y) in ((6, 7), (9, 7), (9, 8), (6, 8)):
    put(x, y, WAX[1])
put(9, 9, WAX[0])
put(7, 2, WICK)
put(7, 1, FLAME[1])
put(7, 0, FLAME[0])
put(8, 1, FLAME[2])
im.save(OUT)
print('votive skull written')
