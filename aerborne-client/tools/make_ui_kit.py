"""Draw the Aerborne core UI kit in the style of the title plaque (tools/make_title_plaque.py).

Usage: python -P -E tools/make_ui_kit.py <assets/aerborne_client dir>

Run it after tools/slice_ui_pack.py: it redraws the frames, buttons, tabs, bars, slots, rarity frames, toast banners,
name tab, search field, scrollbar and title crest under the same names, so code using the pack picks these up. Icons
stay as the pack drew them. Every piece is original pixel art, one art pixel per image pixel, with nine-slice metadata
where it stretches.
"""
import json
import pathlib
import sys

from PIL import Image

EDGE = (18, 12, 6)
GOLD_HI, GOLD, GOLD_MID, GOLD_DARK, BRONZE = (255, 228, 150), (244, 199, 107), (184, 134, 46), (122, 84, 24), (90, 58, 16)
GEM_LIGHT, GEM, GEM_DARK, WHITE = (204, 250, 252), (140, 232, 237), (60, 150, 190), (244, 249, 255)
TROUGH = (9, 13, 24)

FILLS = {  # name: (highlight, base, shade, deep)
    "health_fill": ((255, 130, 118), (214, 62, 62), (168, 38, 44), (120, 24, 32)),
    "absorption_fill": ((255, 236, 170), (244, 199, 107), (204, 150, 56), (150, 104, 30)),
    "adventurer_xp_fill": ((190, 232, 255), (96, 176, 240), (58, 128, 204), (36, 86, 150)),
    "skill_xp_fill": ((176, 246, 186), (86, 204, 114), (52, 156, 82), (32, 108, 56)),
    "boss_health_fill": ((226, 190, 255), (164, 100, 232), (120, 66, 190), (82, 40, 140)),
    "cast_charge_fill": ((250, 254, 255), (196, 236, 250), (140, 200, 230), (96, 150, 190)),
}
RARITY = {
    "common": (154, 163, 181), "uncommon": (85, 212, 106), "rare": (74, 140, 255), "epic": (176, 97, 255),
    "legendary": (244, 177, 58), "mythic": (224, 64, 90), "relic": (63, 215, 201),
}
TOASTS = {"toast_frame": GEM, "level_up_toast": (197, 154, 250), "quest_complete_toast": (85, 229, 138),
          "rare_drop_toast": (224, 64, 90), "achievement_toast": GOLD}


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def depth(x, y, w, h, chamfer):
    """How far a pixel lies inside a box with chamfered corners; negative is outside."""
    d = min(x, y, w - 1 - x, h - 1 - y)
    for cx, cy in ((x, y), (w - 1 - x, y), (x, h - 1 - y), (w - 1 - x, h - 1 - y)):
        d = min(d, cx + cy - chamfer)
    return d


def lit(x, y, w, h):
    """True on the edges facing the light (top and left)."""
    return y < h / 2 and (y <= x and y <= w - 1 - x) or x < w / 2 and x < y and x < h - 1 - y


def frame(w, h, chamfer=2, top=(36, 52, 84), bottom=(13, 22, 40), bevel=(GOLD_HI, GOLD_MID), shade=(GOLD, GOLD_DARK),
          inner=BRONZE, layers=4):
    """A bordered box: dark edge, two bevel rows lit from the top left, a dark inner line, then a vertical gradient."""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y in range(h):
        for x in range(w):
            d = depth(x, y, w, h, chamfer)
            if d < 0:
                continue
            light = lit(x, y, w, h)
            if d == 0:
                c = EDGE
            elif d == 1 and layers >= 3:
                c = bevel[0] if light else bevel[1]
            elif d == 2 and layers >= 4:
                c = shade[0] if light else shade[1]
            elif d == layers - 1:
                c = inner
            else:
                t = (y - layers) / max(1, h - 2 * layers - 1)
                c = mix(top, bottom, max(0, min(1, t)))
            px[x, y] = c + (255,)
    return img


def gem(img, cx, cy, r=1):
    px = img.load()
    for dy in range(-r - 1, r + 2):
        for dx in range(-r - 1, r + 2):
            d = abs(dx) + abs(dy)
            if d == r + 1:
                px[cx + dx, cy + dy] = GOLD_MID + (255,)
            elif d <= r:
                px[cx + dx, cy + dy] = (GEM_LIGHT if dx <= 0 and dy <= 0 else GEM if d < r else GEM_DARK) + (255,)
    px[cx, cy - r] = WHITE + (255,)


def save(img, root, key, border=None):
    out = root / (key + ".png")
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    meta = out.with_name(out.name + ".mcmeta")
    if border:
        left, top, right, bottom = border
        meta.write_text(json.dumps({"gui": {"scaling": {"type": "nine_slice", "width": img.width, "height": img.height,
            "border": {"left": left, "top": top, "right": right, "bottom": bottom}, "stretch_inner": True}}}, indent=2) + chr(10),
            encoding="utf-8")
    elif meta.exists():
        meta.unlink()


def window():
    w, h = 48, 48
    img = frame(w, h, chamfer=3)
    px = img.load()
    # The title bar: a lighter band, a gold rule under it, gems in the top corners.
    for y in range(4, 15):
        for x in range(4, w - 4):
            if depth(x, y, w, h, 3) >= 4:
                px[x, y] = mix((52, 74, 112), (34, 50, 82), (y - 4) / 10) + (255,)
    for x in range(4, w - 4):
        px[x, 15] = GOLD + (255,)
        px[x, 16] = GOLD_DARK + (255,)
        px[x, 17] = EDGE + (255,)
    gem(img, 5, 5)
    gem(img, w - 6, 5)
    return img


def crest():
    """The title bar's centre ornament: a gem with small gold wings."""
    img = Image.new("RGBA", (31, 13), (0, 0, 0, 0))
    px = img.load()
    cx, cy = 15, 6
    for side in (-1, 1):
        for i, (length, start) in enumerate(((10, -1), (8, 1), (6, 3))):
            for k in range(length):
                x, y = cx + side * (4 + k), cy + start - (k + 1) // 3
                if 0 <= x < 31 and 0 <= y < 13:
                    px[x, y] = (GOLD_HI if i == 0 else GOLD if i == 1 else GOLD_MID) + (255,)
                    if y + 1 < 13 and px[x, y + 1][3] == 0:
                        px[x, y + 1] = EDGE + (255,)
    for dy in range(-5, 6):
        for dx in range(-5, 6):
            if abs(dx) + abs(dy) == 4:
                px[cx + dx, cy + dy] = GOLD_MID + (255,)
            elif abs(dx) + abs(dy) == 5:
                px[cx + dx, cy + dy] = EDGE + (255,)
    gem(img, cx, cy, 2)
    return img


def button(top, bottom, bevel=(GOLD_HI, GOLD_MID), shade=(GOLD, GOLD_DARK)):
    return frame(32, 16, chamfer=2, top=top, bottom=bottom, bevel=bevel, shade=shade)


def tab(top, bottom, bevel, shade):
    full = frame(32, 20, chamfer=3, top=top, bottom=bottom, bevel=bevel, shade=shade)
    return full.crop((0, 0, 32, 16))


def search(focused):
    img = frame(48, 16, chamfer=2, top=(14, 22, 38), bottom=(10, 16, 28),
                bevel=((140, 232, 237), (60, 150, 190)) if focused else (GOLD_MID, GOLD_DARK),
                shade=((60, 150, 190), (36, 90, 130)) if focused else (BRONZE, BRONZE), inner=(8, 12, 22))
    px = img.load()
    # A magnifier: a ring and a handle.
    ring = [(5, 4), (6, 4), (7, 4), (4, 5), (8, 5), (4, 6), (8, 6), (4, 7), (8, 7), (5, 8), (6, 8), (7, 8)]
    for x, y in ring:
        px[x, y] = GEM + (255,)
    for x, y in ((9, 9), (10, 10), (11, 11)):
        px[x, y] = GOLD + (255,)
    return img


def bar_frame():
    return frame(32, 10, chamfer=1, top=TROUGH, bottom=TROUGH, bevel=(GOLD, GOLD_DARK), shade=(TROUGH, TROUGH), inner=TROUGH, layers=3)


def fill(colours):
    hi, base, shade, deep = colours
    img = Image.new("RGBA", (32, 6), (0, 0, 0, 0))
    px = img.load()
    rows = [hi, base, base, shade, shade, deep]
    for y in range(6):
        for x in range(32):
            px[x, y] = rows[y] + (255,)
    # A soft shine along the top, broken every few pixels like light on metal.
    for x in range(1, 31):
        if x % 7 not in (0, 1):
            px[x, 1] = mix(base, hi, 0.45) + (255,)
    return img


def slot(hover=False):
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    px = img.load()
    for y in range(18):
        for x in range(18):
            d = depth(x, y, 18, 18, 1)
            if d < 0:
                continue
            light = lit(x, y, 18, 18)
            if d == 0:
                c = GOLD if hover else (8, 12, 22)
            elif d == 1:
                c = (10, 15, 27) if light else (66, 86, 122)  # sunken: dark above, light below
            else:
                c = mix((24, 35, 58), (30, 44, 72), y / 17) if not hover else mix((40, 58, 92), (48, 68, 104), y / 17)
            px[x, y] = c + (255,)
    return img


def rarity(colour):
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    px = img.load()
    dark = mix(colour, (0, 0, 0), 0.45)
    bright = mix(colour, (255, 255, 255), 0.3)
    for y in range(18):
        for x in range(18):
            d = depth(x, y, 18, 18, 1)
            if d == 0:
                px[x, y] = dark + (255,)
            elif d == 1:
                px[x, y] = (bright if lit(x, y, 18, 18) else colour) + (255,)
    for x, y in ((2, 2), (15, 2), (2, 15), (15, 15)):
        px[x, y] = bright + (255,)
    return img


def toast(accent):
    w, h = 64, 22
    img = frame(w, h, chamfer=6, top=(30, 44, 72), bottom=(12, 19, 34))
    px = img.load()
    # The accent: a coloured line under the top bevel and a gem in the emblem at each end.
    for x in range(8, w - 8):
        px[x, 4] = mix(accent, (0, 0, 0), 0.2) + (255,)
    for cx in (7, w - 8):
        for dy in range(-3, 4):
            for dx in range(-3, 4):
                d = abs(dx) + abs(dy)
                if d == 3:
                    px[cx + dx, 10 + dy] = GOLD_MID + (255,)
                elif d < 3:
                    px[cx + dx, 10 + dy] = (mix(accent, (255, 255, 255), 0.4) if dx <= 0 and dy <= 0 else accent) + (255,)
    return img


def name_tab():
    img = frame(40, 14, chamfer=4, top=(40, 58, 92), bottom=(18, 28, 48))
    return img


def scroll(thumb):
    if thumb:
        return frame(6, 12, chamfer=1, top=(84, 108, 150), bottom=(52, 72, 110), bevel=(GOLD_HI, GOLD_MID), shade=(GOLD, GOLD_DARK), layers=3)
    return frame(6, 16, chamfer=1, top=TROUGH, bottom=TROUGH, bevel=(BRONZE, BRONZE), shade=(TROUGH, TROUGH), inner=TROUGH, layers=3)


def main(assets):
    root = pathlib.Path(assets) / "textures" / "gui" / "sprites" / "ui"
    save(window(), root, "window/large_window", (8, 19, 8, 8))
    save(crest(), root, "window/title_star")
    save(frame(24, 24, chamfer=2, top=(26, 38, 62), bottom=(12, 19, 34), bevel=(GOLD_MID, GOLD_DARK), shade=(BRONZE, (60, 40, 12)),
               inner=(8, 12, 22)), root, "window/inner_inset_panel", (5, 5, 5, 5))
    save(button((60, 84, 128), (30, 46, 76)), root, "window/button_secondary", (4, 4, 4, 4))
    save(button((92, 124, 176), (50, 74, 118), bevel=((255, 244, 200), GOLD), shade=(GOLD_HI, GOLD_MID)), root, "window/button_primary", (4, 4, 4, 4))
    save(button((26, 40, 66), (48, 68, 104)), root, "window/button_pressed", (4, 4, 4, 4))
    save(button((60, 66, 80), (40, 44, 54), bevel=((120, 110, 90), (80, 70, 54)), shade=((100, 90, 70), (60, 52, 40))),
         root, "window/button_disabled", (4, 4, 4, 4))
    save(button((168, 56, 60), (98, 26, 32)), root, "window/button_danger", (4, 4, 4, 4))
    save(tab((64, 92, 140), (40, 60, 98), (GOLD_HI, GOLD_MID), (GOLD, GOLD_DARK)), root, "window/tab_active", (5, 5, 5, 2))
    save(tab((36, 50, 78), (26, 38, 62), (GOLD_MID, GOLD_DARK), (BRONZE, BRONZE)), root, "window/tab_inactive", (5, 5, 5, 2))
    save(tab((50, 70, 108), (34, 50, 82), (GOLD, GOLD_MID), (GOLD_MID, GOLD_DARK)), root, "window/tab_hover", (5, 5, 5, 2))
    save(search(False), root, "window/search_box", (14, 4, 4, 4))
    save(search(True), root, "window/search_box_focused", (14, 4, 4, 4))
    save(scroll(False), root, "window/scrollbar_track", (2, 3, 2, 3))
    save(scroll(True), root, "window/scrollbar_thumb", (2, 3, 2, 3))
    save(bar_frame(), root, "bars/bar_frame", (3, 3, 3, 3))
    for name, colours in FILLS.items():
        save(fill(colours), root, "bars/" + name, (1, 1, 1, 1))
    for name, accent in TOASTS.items():
        save(toast(accent), root, "bars/" + name, (12, 6, 12, 6))
    save(name_tab(), root, "bars/dialogue_name_tab", (6, 5, 6, 5))
    save(slot(False), root, "slots/slot_normal")
    save(slot(True), root, "slots/slot_hover")
    for name, colour in RARITY.items():
        save(rarity(colour), root, "slots/rarity_" + name)
    print("drew the UI kit")


if __name__ == "__main__":
    main(sys.argv[1])
