"""Draw the Aerborne title plaque: gold serif capitals on a dark, gold-bordered plaque with a winged gem crest.

Usage: python -P -E tools/make_title_plaque.py <out png>

Original pixel art in the UI pack's palette (docs/ASSET_PROMPT.md), drawn one art pixel per image pixel so the game
can scale it by whole steps. The letters are hand-set below; nothing is traced from another game's art or fonts.
"""
import sys

from PIL import Image

# Capitals, 11 rows tall, '#' filled. Thick main strokes, thin joins and flared serifs.
GLYPHS = {
    "A": [
        ".....#.....",
        "....###....",
        "....###....",
        "...##.##...",
        "...##.##...",
        "..##...##..",
        "..#######..",
        ".##.....##.",
        ".##.....##.",
        ".##.....##.",
        "####...####",
    ],
    "E": [
        "#########",
        ".##....##",
        ".##.....#",
        ".##..#...",
        ".######..",
        ".##..#...",
        ".##......",
        ".##.....#",
        ".##....##",
        ".##...###",
        "#########",
    ],
    "R": [
        "#######...",
        ".##...##..",
        ".##....##.",
        ".##....##.",
        ".##...##..",
        ".######...",
        ".##..##...",
        ".##...##..",
        ".##....##.",
        ".##....##.",
        "####...###",
    ],
    "B": [
        "########..",
        ".##....##.",
        ".##....##.",
        ".##....##.",
        ".##...##..",
        ".#######..",
        ".##....##.",
        ".##.....##",
        ".##.....##",
        ".##....##.",
        "########..",
    ],
    "O": [
        "...#####...",
        "..##...##..",
        ".##.....##.",
        ".##.....##.",
        "##.......##",
        "##.......##",
        "##.......##",
        ".##.....##.",
        ".##.....##.",
        "..##...##..",
        "...#####...",
    ],
    "N": [
        "###....####",
        ".###....##.",
        ".####...##.",
        ".##.##..##.",
        ".##..##.##.",
        ".##...####.",
        ".##....###.",
        ".##.....##.",
        ".##.....##.",
        ".##.....##.",
        "####....##.",
    ],
}

INK = (26, 15, 5, 255)
SHADOW = (0, 0, 0, 120)
# Stepped gold, light at the top of each letter to deep amber at its foot.
GOLD_ROWS = [(255, 244, 194), (255, 236, 160), (252, 224, 138), (247, 211, 120), (244, 199, 107), (236, 184, 88),
             (224, 168, 69), (204, 150, 56), (184, 134, 46), (160, 112, 36), (138, 90, 24)]
HIGHLIGHT = (255, 251, 224)
NAVY_TOP, NAVY_BOTTOM = (36, 52, 84), (11, 18, 34)
GOLD_LIGHT, GOLD_MID, GOLD_DARK, BRONZE, EDGE = (244, 199, 107), (184, 134, 46), (122, 84, 24), (90, 58, 16), (18, 12, 6)
GEM_LIGHT, GEM, GEM_DARK, WHITE = (204, 250, 252), (140, 232, 237), (60, 150, 190), (244, 249, 255)


def letters(word, gap=2):
    width = sum(len(GLYPHS[c][0]) for c in word) + gap * (len(word) - 1)
    mask = [[False] * width for _ in range(11)]
    x = 0
    for c in word:
        glyph = GLYPHS[c]
        for y, row in enumerate(glyph):
            for i, cell in enumerate(row):
                if cell == "#":
                    mask[y][x + i] = True
        x += len(glyph[0]) + gap
    return mask, width


def plaque_shape(w, h, inset):
    """Pointed-end plaque: a band with chamfered points at both ends, shrunk by `inset` for the inner layers."""
    point = h // 2
    def inside(x, y):
        top, bottom = inset, h - 1 - inset
        if y < top or y > bottom:
            return False
        mid = (h - 1) / 2
        reach = point - abs(y - mid)  # how far in from each end this row starts
        return inset + max(0, (point - reach)) * 0 + abs(y - mid) * 0.9 <= x + 0 and x <= w - 1 - inset - abs(y - mid) * 0.9
    return inside


def draw(out):
    mask, text_w = letters("AERBORNE")
    pad_x, pad_y = 22, 7
    w, h = text_w + pad_x * 2, 11 + pad_y * 2 + 1
    crest = 12
    img = Image.new("RGBA", (w, h + crest + 3), (0, 0, 0, 0))
    px = img.load()
    oy = crest

    # Plaque: dark edge, two-tone gold band, bronze inner line, navy fill.
    layers = [(0, EDGE), (1, None), (3, BRONZE), (4, None)]
    for y in range(h):
        for x in range(w):
            for inset, colour in layers:
                if plaque_shape(w, h, inset)(x, y):
                    if colour is None and inset == 1:
                        colour = GOLD_LIGHT if y < h / 2 else GOLD_MID
                        if y == 1 or (x < w / 2 and plaque_shape(w, h, 2)(x, y) is False and y < h / 2):
                            colour = (255, 228, 150)
                        if y >= h - 3:
                            colour = GOLD_DARK
                    elif colour is None:
                        t = (y - 4) / max(1, h - 9)
                        colour = tuple(round(NAVY_TOP[i] + (NAVY_BOTTOM[i] - NAVY_TOP[i]) * t) for i in range(3))
                    px[x, y + oy] = colour + (255,) if len(colour) == 3 else colour
    # A faint inner glow line under the top border.
    for x in range(w):
        if plaque_shape(w, h, 5)(x, 5):
            px[x, 5 + oy] = (54, 75, 109, 255)

    # Gem studs at the points and rivets beside them.
    def gem(cx, cy, r):
        for dy in range(-r - 1, r + 2):
            for dx in range(-r - 1, r + 2):
                d = abs(dx) + abs(dy)
                if d == r + 1:
                    px[cx + dx, cy + dy] = GOLD_MID + (255,)
                elif d <= r:
                    c = GEM_LIGHT if dx <= 0 and dy <= 0 else GEM if d < r else GEM_DARK
                    px[cx + dx, cy + dy] = c + (255,)
        px[cx - 1, cy - 1] = WHITE + (255,)
    mid = oy + (h - 1) // 2
    gem(6, mid, 2)
    gem(w - 7, mid, 2)
    for rx in (13, w - 14):
        for ry in (oy + 6, oy + h - 7):
            px[rx, ry] = GOLD_LIGHT + (255,)
            px[rx + 1, ry] = GOLD_MID + (255,)
            px[rx, ry + 1] = GOLD_MID + (255,)
            px[rx + 1, ry + 1] = GOLD_DARK + (255,)

    # Crest: a large gem in a gold mount, with small gold wings sweeping out from it.
    cx, cy = w // 2, oy + 1
    for dy in range(-7, 5):
        for dx in range(-7, 8):
            d = abs(dx) + abs(dy)
            if d == 6 or d == 7 and dy > -7:
                px[cx + dx, cy + dy] = (EDGE if d == 7 else GOLD_MID) + (255,)
    gem(cx, cy, 4)
    # Each wing is three swept feathers, two pixels thick, the top one longest; they rise as they reach out.
    for side in (-1, 1):
        for i, (length, start) in enumerate(((15, -3), (12, 0), (9, 3))):
            for k in range(length):
                x = cx + side * (7 + k)
                y = cy + start - (k + 1) // 2
                for t, colour in ((0, GOLD_LIGHT if i == 0 else GOLD_MID), (1, GOLD_MID if i == 0 else GOLD_DARK)):
                    if 0 <= x < w and 0 <= y + t < img.height and (px[x, y + t][3] == 0 or t == 0):
                        px[x, y + t] = colour + (255,)
                if 0 <= x < w and y + 2 < img.height and px[x, y + 2][3] == 0:
                    px[x, y + 2] = EDGE + (255,)
            # A white tip on the top feather.
            tip_x = cx + side * (7 + length - 1)
            if 0 <= tip_x < w:
                px[tip_x, cy + start - length // 2] = WHITE + (255,)
    # A small gold point under the plaque.
    for dy in range(3):
        for dx in range(-2 + dy, 3 - dy):
            px[cx + dx, oy + h + dy] = (GOLD_MID if dx else GOLD_LIGHT) + (255,)
        px[cx - 3 + dy, oy + h + dy] = EDGE + (255,)
        px[cx + 3 - dy, oy + h + dy] = EDGE + (255,)

    # Letters: drop shadow, dark outline, stepped gold with a highlight on upper edges.
    tx, ty = pad_x, oy + pad_y + 1
    def filled(x, y):
        return 0 <= y < 11 and 0 <= x < text_w and mask[y][x]
    for y in range(-1, 12):
        for x in range(-1, text_w + 1):
            if not filled(x, y) and any(filled(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                px[tx + x + 1, ty + y + 1] = SHADOW
    for y in range(-1, 12):
        for x in range(-1, text_w + 1):
            if not filled(x, y) and any(filled(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                px[tx + x, ty + y] = INK
    for y in range(11):
        for x in range(text_w):
            if filled(x, y):
                colour = HIGHLIGHT if not filled(x, y - 1) and y < 6 else GOLD_ROWS[y]
                px[tx + x, ty + y] = colour + (255,)
    img.save(out)
    print("wrote", out, img.size)


if __name__ == "__main__":
    draw(sys.argv[1])
