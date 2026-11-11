"""Slices the Aerborne asset pack into game-size textures.

Pixel art is reduced by reading the centre of each art-pixel block (exact nearest-neighbour), and every block is
checked to be one flat colour first, so a sheet that isn't on its stated grid is reported instead of smeared.
Brand art is cropped to its painted area and reduced smoothly.

The output is laid out as the mod's asset folder (assets/aerborne_client): GUI sprites under textures/gui/sprites
with their nine-slice metadata, brand art under textures/gui/brand with smooth filtering, and the mod's icon.

usage: slice_assets.py <pack dir> <assets/aerborne_client dir>
"""
import json
import os
import sys
from PIL import Image

PACK, OUT = sys.argv[1], sys.argv[2]
report = []


def load(rel):
    return Image.open(os.path.join(PACK, rel)).convert('RGBA')


def save(im, rel, meta=None):
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path, optimize=True)
    if meta is not None:
        with open(path + '.mcmeta', 'w', encoding='utf-8', newline=chr(10)) as f:
            json.dump(meta, f, indent=2)
            f.write(chr(10))
    return path


def nine_slice(width, height, border):
    """Sprite metadata: fixed corners and edges of the given border, the middle stretched between them."""
    return {'gui': {'scaling': {'type': 'nine_slice', 'width': width, 'height': height, 'border': border, 'stretch_inner': True}}}


SPRITES = 'textures/gui/sprites/'
BRAND = 'textures/gui/brand/'
# Painted art is sampled smoothly and never wraps at its edges.
SMOOTH = {'texture': {'blur': True, 'clamp': True}}


def reduce_pixels(im, box, scale, name):
    """The art pixels of a region: one per scale x scale block, each block verified flat."""
    x0, y0, x1, y1 = box
    w, h = (x1 - x0) // scale, (y1 - y0) // scale
    assert (x1 - x0) % scale == 0 and (y1 - y0) % scale == 0, (name, box, scale)
    src = im.load()
    out = Image.new('RGBA', (w, h))
    dst = out.load()
    bad = 0
    for ay in range(h):
        for ax in range(w):
            bx, by = x0 + ax * scale, y0 + ay * scale
            c = src[bx + scale // 2, by + scale // 2]
            for dx, dy in ((0, 0), (scale - 1, 0), (0, scale - 1), (scale - 1, scale - 1)):
                if src[bx + dx, by + dy] != c:
                    bad += 1
                    break
            dst[ax, ay] = c if c[3] else (0, 0, 0, 0)
    report.append(f'{name}: {w}x{h} art px, off-grid blocks {bad}')
    return out


def slug(title):
    return title.lower().replace(' & ', '_').replace(' ', '_')


manifest = json.load(open(os.path.join(PACK, 'manifest.json'), encoding='utf-8'))

# Icon atlases: cell rectangles straight from the manifest.
for sheet, folder in (('menu_icons.png', 'icon/menu'), ('nav_icons.png', 'icon/nav'), ('hud_icons.png', 'icon/hud')):
    spec = manifest['pixel_atlases'][sheet]
    im = load('02_Pixel_GUI/' + sheet)
    cell, scale = spec['cell_pixels'], spec['scale']
    fx, fy = spec['first_cell']
    for i, title in enumerate(spec['order']):
        col, row = i % spec['columns'], i // spec['columns']
        x, y = fx + col * (cell + spec['gap_x']), fy + row * (cell + spec['gap_y'])
        save(reduce_pixels(im, (x, y, x + cell, y + cell), scale, f'{folder}/{slug(title)}'), f'{SPRITES}{folder}/{slug(title)}.png')

# Nine-slice panel and tooltip: the whole canvas.
panel = reduce_pixels(load('02_Pixel_GUI/panel_9slice.png'), (0, 0, 1024, 1024), 16, 'menu/panel')
save(panel, SPRITES + 'menu/panel.png', nine_slice(64, 64, 8))

# The tooltip. Minecraft draws a tooltip's sprite twelve pixels out from its text on every side, so the art gets a
# three pixel clear margin: its six pixels of lines then end three short of the words. The game also draws a
# second "frame" sprite over it, which this style leaves empty.
tooltip = reduce_pixels(load('02_Pixel_GUI/tooltip_9slice.png'), (0, 0, 1024, 1024), 32, 'tooltip/menu_background')
padded = Image.new('RGBA', (38, 38))
padded.paste(tooltip, (3, 3))
save(padded, SPRITES + 'tooltip/menu_background.png', nine_slice(38, 38, 9))
save(Image.new('RGBA', (2, 2)), SPRITES + 'tooltip/menu_frame.png')

# Buttons: three states stacked, each 20 art px tall at 6x with a 60px gap; cropped to their painted width.
buttons = load('02_Pixel_GUI/buttons.png')
bx0, by0, bx1, by1 = buttons.getchannel('A').getbbox()
for i, state in enumerate(('button', 'button_highlighted', 'button_disabled')):
    y = by0 + i * (120 + 60)
    # The glint at the left end, the notch at the right, the top highlight and the bottom shadow stay fixed; only
    # the flat face between them stretches, so a button can be any width and any height from 17 up.
    save(reduce_pixels(buttons, (bx0, y, bx1, y + 120), 6, f'menu/{state}'), f'{SPRITES}menu/{state}.png',
         nine_slice(196, 20, {'left': 19, 'top': 9, 'right': 13, 'bottom': 8}))

# The XP bar's two strips, each 5 art px tall at 8x.
xp = load('02_Pixel_GUI/xp_bar.png')
xx0, xy0, xx1, xy1 = xp.getchannel('A').getbbox()
save(reduce_pixels(xp, (xx0, xy0, xx1, xy0 + 40), 8, 'hud/xp_bar_background'), SPRITES + 'hud/xp_bar_background.png')
save(reduce_pixels(xp, (xx0, xy1 - 40, xx1, xy1), 8, 'hud/xp_bar_progress'), SPRITES + 'hud/xp_bar_progress.png')

# The toast: its real scale is found from the painted box (the sheet isn't at the 8x the manifest states).
toast = load('02_Pixel_GUI/toast.png')
tx0, ty0, tx1, ty1 = toast.getchannel('A').getbbox()
for scale in (8, 9, 10, 6, 12):
    if (tx1 - tx0) % scale == 0 and (ty1 - ty0) % scale == 0:
        before = len(report)
        candidate = reduce_pixels(toast, (tx0, ty0, tx1, ty1), scale, f'hud/toast (scale {scale})')
        if report[-1].endswith('blocks 0'):
            save(candidate, SPRITES + 'hud/toast.png')
            break
        del report[before:]
else:
    report.append('hud/toast: NO CLEAN GRID FOUND')

# Brand art: cropped to what is painted, reduced smoothly. Sizes are four texture pixels to one GUI unit.
def brand(rel, out, width=None, height=None, pad=0, crop=True):
    im = load(rel)
    if crop:
        x0, y0, x1, y1 = im.getchannel('A').getbbox()
        im = im.crop((max(0, x0 - pad), max(0, y0 - pad), min(im.width, x1 + pad), min(im.height, y1 + pad)))
    if width and not height:
        height = round(im.height * width / im.width)
    if height and not width:
        width = round(im.width * height / im.height)
    im = im.resize((width, height), Image.LANCZOS)
    save(im, BRAND + out, SMOOTH)
    report.append(f'{out}: {width}x{height}')
    return im


brand('01_Brand_Art/menu_header.png', 'header.png', width=960)
brand('01_Brand_Art/logo.png', 'logo.png', width=1024)
brand('01_Brand_Art/title_background.png', 'title_background.png', width=1536, crop=False)
brand('01_Brand_Art/loading_background.png', 'loading_background.png', width=1536, crop=False)
for pose in ('point', 'wave', 'read'):
    brand(f'03_Mascot/mascot_{pose}.png', f'mascot_{pose}.png', height=384)

# The mod icon: the Skycrest centred on a square with a little air round it.
icon = load('01_Brand_Art/icon.png')
x0, y0, x1, y1 = icon.getchannel('A').getbbox()
side = max(x1 - x0, y1 - y0)
square = Image.new('RGBA', (side, side))
square.paste(icon.crop((x0, y0, x1, y1)), ((side - (x1 - x0)) // 2, (side - (y1 - y0)) // 2))
canvas = Image.new('RGBA', (128, 128))
canvas.paste(square.resize((120, 120), Image.LANCZOS), (4, 4))
save(canvas, 'icon.png')
report.append('icon.png: 128x128')

print('\n'.join(report))
