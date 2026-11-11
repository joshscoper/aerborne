"""Cut the Aerborne native UI pack (11 sheets, 4x scale, see docs/ASSET_PROMPT.md) into GUI sprites.

Usage: python -P -E tools/slice_ui_pack.py <pack dir> <assets/aerborne_client dir>

The pack's manifest.json names every cell. Icon cells keep their whole cell, so every icon comes out the same size
(64 px cells become 16x16 sprites, 128 px cells 32x32). Frames, bars and the two screens are cropped to their art and
given nine-slice metadata, so they stretch without blurring their corners. Every art pixel is a 4x4 block in the
sheet; it is read from the block's centre, so nothing is filtered.
"""
import json
import pathlib
import sys

from PIL import Image

SCALE = 4
GROUPS = {
    "window_kit.png": "window", "controls.png": "controls", "slots.png": "slots", "bars.png": "bars",
    "icons_skills.png": "skills", "icons_menus.png": "menus", "icons_map.png": "map", "icons_effects.png": "effects",
    "trees_and_quests.png": "tree", "screens.png": "screens", "minimap.png": "minimap",
}
# Sheets whose sprites are cropped to their art rather than kept as whole cells.
CROPPED = {"window_kit.png", "bars.png", "screens.png"}
# Nine-slice borders in art pixels: (left, top, right, bottom). Anything cropped and not listed is drawn as is.
BORDERS = {
    "window/large_window": (14, 22, 14, 9),
    "window/inner_inset_panel": (4, 4, 4, 4),
    "window/tab_active": (5, 4, 5, 2),
    "window/tab_inactive": (5, 4, 5, 2),
    "window/tab_hover": (5, 4, 5, 2),
    "window/side_tab_active": (4, 8, 4, 4),
    "window/side_tab_inactive": (4, 4, 4, 4),
    "window/button_primary": (4, 4, 4, 4),
    "window/button_secondary": (4, 4, 4, 4),
    "window/button_danger": (4, 4, 4, 4),
    "window/button_pressed": (4, 4, 4, 4),
    "window/button_disabled": (4, 4, 4, 4),
    "window/search_box": (22, 4, 4, 4),
    "window/search_box_focused": (22, 4, 4, 4),
    "window/scrollbar_track": (2, 3, 2, 3),
    "window/scrollbar_thumb": (2, 3, 2, 3),
    "bars/bar_frame": (4, 2, 4, 2),
    "bars/health_fill": (2, 1, 2, 1),
    "bars/absorption_fill": (2, 1, 2, 1),
    "bars/adventurer_xp_fill": (2, 1, 2, 1),
    "bars/skill_xp_fill": (2, 1, 2, 1),
    "bars/boss_health_fill": (2, 1, 2, 1),
    "bars/cast_charge_fill": (2, 1, 2, 1),
    "bars/boss_bar_frame": (8, 2, 8, 2),
    "bars/party_member_frame": (12, 2, 4, 2),
    "bars/toast_frame": (40, 4, 4, 4),
    "bars/level_up_toast": (40, 4, 4, 4),
    "bars/quest_complete_toast": (40, 4, 4, 4),
    "bars/rare_drop_toast": (40, 4, 4, 4),
    "bars/achievement_toast": (40, 4, 4, 4),
    "bars/dialogue_name_tab": (6, 2, 6, 2),
    "bars/tooltip_frame": (3, 3, 3, 3),
    "screens/world_map_frame": (12, 12, 12, 12),
}


def downscale(image):
    w, h = image.size
    out = Image.new("RGBA", (w // SCALE, h // SCALE))
    src, dst = image.load(), out.load()
    for y in range(h // SCALE):
        for x in range(w // SCALE):
            dst[x, y] = src[x * SCALE + SCALE // 2, y * SCALE + SCALE // 2]
    return out


def crop_to_art(cell):
    box = cell.getchannel("A").getbbox()
    if box is None:
        return None
    # Keep whole art pixels.
    left, top, right, bottom = box
    left -= left % SCALE
    top -= top % SCALE
    right += (-right) % SCALE
    bottom += (-bottom) % SCALE
    return cell.crop((left, top, right, bottom))


def main(pack, assets):
    pack, assets = pathlib.Path(pack), pathlib.Path(assets)
    manifest = json.loads((pack / "manifest.json").read_text(encoding="utf-8"))
    root = assets / "textures" / "gui" / "sprites" / "ui"
    written = 0
    for sheet_name, sheet in manifest["sheets"].items():
        group = GROUPS[sheet_name]
        image = Image.open(pack / sheet_name).convert("RGBA")
        assert list(image.size) == sheet["size_px"], (sheet_name, image.size)
        (root / group).mkdir(parents=True, exist_ok=True)
        for name, rect in sheet["sprites"].items():
            cell = image.crop((rect["x"], rect["y"], rect["x"] + rect["width"], rect["y"] + rect["height"]))
            if sheet_name in CROPPED:
                cell = crop_to_art(cell)
                if cell is None:
                    continue
            elif cell.getchannel("A").getbbox() is None:
                continue
            sprite = downscale(cell)
            key = group + "/" + name
            if key == "window/large_window":
                sprite = lift_star(sprite, root / group)
            if key == "window/scrollbar_track_and_thumb":
                for part, piece in zip(("scrollbar_track", "scrollbar_thumb"), split_columns(sprite)):
                    save(piece, root / group / (part + ".png"), group + "/" + part)
                    written += 1
                continue
            save(sprite, root / group / (name + ".png"), key)
            written += 1
            if sheet_name in CROPPED:
                print(key, sprite.size)
    print("wrote", written, "sprites")


def save(sprite, out, key):
    sprite.save(out)
    meta = out.with_name(out.name + ".mcmeta")
    if key in BORDERS:
        left, top, right, bottom = BORDERS[key]
        meta.write_text(json.dumps({"gui": {"scaling": {"type": "nine_slice", "width": sprite.width,
            "height": sprite.height, "border": {"left": left, "top": top, "right": right, "bottom": bottom},
            "stretch_inner": True}}}, indent=2) + chr(10), encoding="utf-8")
    elif meta.exists():
        meta.unlink()


def lift_star(window, folder):
    """The title bar's centre star would repeat when the frame stretches: cut it out to draw it centred instead."""
    star_box = (20, 1, 40, 16)
    window.crop(star_box).save(folder / "title_star.png")
    plain = window.crop((18, star_box[1], 19, star_box[3]))
    for x in range(star_box[0], star_box[2]):
        window.paste(plain, (x, star_box[1]))
    return window


def split_columns(sprite):
    """Splits a sprite at its first fully transparent column gap: the scrollbar track, then its thumb."""
    alpha = sprite.getchannel("A")
    columns = [alpha.crop((x, 0, x + 1, sprite.height)).getbbox() is not None for x in range(sprite.width)]
    parts, start = [], None
    for x, filled in enumerate(columns + [False]):
        if filled and start is None:
            start = x
        elif not filled and start is not None:
            piece = sprite.crop((start, 0, x, sprite.height))
            parts.append(piece.crop(piece.getchannel("A").getbbox()))
            start = None
    return parts


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
