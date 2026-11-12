"""Restyle the vanilla hotbar and container screens to match the Aerborne kit.

Usage: python -P -E tools/make_vanilla_skins.py <minecraft client jar> <fabric resources assets dir>

The hotbar and its selection frame are drawn fresh. Container backgrounds (survival inventory, chests, crafting
table, furnaces and more) are read from the game's own textures and repainted: their greys map onto the kit's navy,
slot and gold tones, so every slot stays exactly where the game expects it. The results go under assets/minecraft,
where the mod's resources override the vanilla ones.
"""
import io
import pathlib
import sys
import zipfile

from PIL import Image

EDGE, GOLD, GOLD_MID, GOLD_DARK = (18, 12, 6), (244, 199, 107), (184, 134, 46), (122, 84, 24)
CONTAINERS = ["inventory", "generic_54", "crafting_table", "furnace", "blast_furnace", "smoker", "hopper", "dispenser",
              "shulker_box", "brewing_stand", "enchanting_table", "anvil", "grindstone", "smithing", "stonecutter",
              "cartography_table", "loom", "beacon", "horse", "crafter"]
# Vanilla greys and what each becomes. Panels go navy, slot wells go deep blue, bevels go gold above and dusk below.
GREYS = {
    255: (96, 118, 158),   # light bevel
    219: (52, 72, 108),
    198: (30, 44, 72),     # panel
    170: (40, 56, 88),
    139: (20, 30, 50),     # slot well
    113: (34, 48, 76),
    85: (10, 16, 28),      # dark bevel and slot shadow
    55: (8, 12, 22),
}


def nearest_grey(value):
    return min(GREYS, key=lambda grey: abs(grey - value))


def repaint(img):
    img = img.convert("RGBA")
    px = img.load()
    w, h = img.size
    out = img.copy()
    opx = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            if abs(r - g) > 12 or abs(g - b) > 12:
                continue  # coloured details (arrows, flames, icons) keep their colour
            if r < 30:
                # Black: the panel's outline where it meets the outside becomes gold; black inside stays dark.
                outside = any(not (0 <= x + dx < w and 0 <= y + dy < h) or px[x + dx, y + dy][3] == 0
                              for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                opx[x, y] = (GOLD_MID if outside else (6, 10, 18)) + (a,)
                continue
            opx[x, y] = GREYS[nearest_grey(r)] + (a,)
    # A second gold line just inside the outline, where the light bevel runs, gives the frame its bevel.
    for y in range(h):
        for x in range(w):
            if opx[x, y][:3] == GOLD_MID:
                for dx, dy in ((1, 0), (0, 1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and opx[nx, ny][:3] == GREYS[255]:
                        opx[nx, ny] = GOLD + (opx[nx, ny][3],)
    return out


def hotbar():
    img = Image.new("RGBA", (182, 22), (0, 0, 0, 0))
    px = img.load()
    for y in range(22):
        for x in range(182):
            if x in (0, 181) or y in (0, 21):
                px[x, y] = GOLD_MID + (255,)
            elif x in (1, 180) or y in (1, 20):
                px[x, y] = EDGE + (230,)
            else:
                px[x, y] = (13, 22, 40, 190)
    for i in range(1, 9):
        x = 1 + i * 20
        for y in range(2, 20):
            px[x, y] = GOLD_DARK + (220,)
    for i in range(9):
        # A faint inner well per slot.
        for y in range(3, 19):
            for x in range(3 + i * 20, 19 + i * 20):
                if y == 3 or x == 3 + i * 20:
                    px[x, y] = (8, 12, 22, 200)
    return img


def selection():
    img = Image.new("RGBA", (24, 23), (0, 0, 0, 0))
    px = img.load()
    for y in range(23):
        for x in range(24):
            d = min(x, y, 23 - x, 22 - y)
            if d == 0:
                px[x, y] = EDGE + (255,)
            elif d == 1:
                px[x, y] = (255, 228, 150, 255) if (x < 12 and y < 12) else GOLD + (255,)
            elif d == 2:
                px[x, y] = GOLD_MID + (255,)
    # A small gem on the top edge.
    for x, y, c in ((11, 0, GOLD), (12, 0, GOLD), (11, 1, (150, 236, 240)), (12, 1, (60, 160, 200))):
        px[x, y] = c + (255,)
    return img


def main(jar, assets):
    assets = pathlib.Path(assets)
    mc = assets / "minecraft" / "textures" / "gui"
    with zipfile.ZipFile(jar) as zf:
        for name in CONTAINERS:
            path = "assets/minecraft/textures/gui/container/" + name + ".png"
            try:
                data = zf.read(path)
            except KeyError:
                print("missing", name)
                continue
            out = mc / "container" / (name + ".png")
            out.parent.mkdir(parents=True, exist_ok=True)
            repaint(Image.open(io.BytesIO(data))).save(out)
    (mc / "sprites" / "hud").mkdir(parents=True, exist_ok=True)
    hotbar().save(mc / "sprites" / "hud" / "hotbar.png")
    selection().save(mc / "sprites" / "hud" / "hotbar_selection.png")
    print("restyled", len(CONTAINERS), "containers and the hotbar")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
