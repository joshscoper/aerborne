"""Turns painted category art into the store's category images.

Run from Website/:
    .venv/Scripts/python.exe tools/category_art.py titles=<file> auras=<file> finishers=<file> boosters=<file>

Art that already has a transparent background keeps it. On art painted on white, the white round the
picture becomes transparent, feathered at the edge so glows fade out instead of ending in a halo. Writes aerborne/static/img/store/category-<name>-256.webp and -512.webp, and for the
booster also the store item's picture, on the store's night background.
"""
import os
import sys
from collections import deque

from PIL import Image, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STORE = os.path.join(ROOT, 'aerborne', 'static', 'img', 'store')
NAMES = {'titles', 'auras', 'finishers', 'boosters'}


def cut_out(image):
    """Removes the white background: only near-white that reaches the edge, so white inside the art stays."""
    if image.mode in ('RGBA', 'LA') and image.getchannel('A').getextrema()[0] < 8:
        return square(image.convert('RGBA').resize((512, 512), Image.LANCZOS))
    image = image.convert('RGB')
    small = image.resize((512, 512), Image.LANCZOS)
    pixels = small.load()
    w, h = small.size

    def whiteish(x, y):
        r, g, b = pixels[x, y]
        return min(r, g, b) > 232 and max(r, g, b) - min(r, g, b) < 26

    background = Image.new('L', (w, h), 0)
    seen = background.load()
    queue = deque((x, y) for x in range(w) for y in (0, h - 1)) + deque((x, y) for y in range(h) for x in (0, w - 1))
    while queue:
        x, y = queue.popleft()
        if seen[x, y] or not whiteish(x, y):
            continue
        seen[x, y] = 255
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h and not seen[nx, ny]:
                queue.append((nx, ny))
    # Soften the edge so a glow fades to nothing rather than stopping at a line.
    alpha = background.filter(ImageFilter.GaussianBlur(2)).point(lambda v: 255 - v)
    out = small.convert('RGBA')
    out.putalpha(alpha)
    return square(out)


def square(out):
    """Crops round the art to a square, with a little room."""
    w, h = out.size
    alpha = out.getchannel('A')
    bbox = alpha.point(lambda v: 255 if v > 8 else 0).getbbox() or (0, 0, w, h)
    side = max(bbox[2] - bbox[0], bbox[3] - bbox[1]) + 24
    cx, cy = (bbox[0] + bbox[2]) // 2, (bbox[1] + bbox[3]) // 2
    canvas = Image.new('RGBA', (side, side))
    canvas.alpha_composite(out.crop((cx - side // 2, cy - side // 2, cx + side // 2, cy + side // 2)))
    return canvas


def night(size):
    image = Image.new('RGBA', (size, size))
    for y in range(size):
        t = y / (size - 1)
        colour = tuple(int(a + (b - a) * t) for a, b in zip((22, 34, 72), (8, 14, 36))) + (255,)
        image.paste(colour, (0, y, size, y + 1))
    return image


def main(args):
    jobs = dict(arg.split('=', 1) for arg in args)
    unknown = set(jobs) - NAMES
    if unknown or not jobs:
        sys.exit('usage: category_art.py ' + ' '.join(n + '=<file>' for n in sorted(NAMES)))
    for name, source in jobs.items():
        art = cut_out(Image.open(source))
        for size in (256, 512):
            path = os.path.join(STORE, 'category-%s-%d.webp' % (name, size))
            art.resize((size, size), Image.LANCZOS).save(path, 'WEBP', quality=92, method=6)
            print('wrote', os.path.relpath(path, ROOT))
        if name == 'boosters':
            card = night(320)
            card.alpha_composite(art.resize((292, 292), Image.LANCZOS), (14, 14))
            path = os.path.join(STORE, 'xp-booster-320.webp')
            card.convert('RGB').save(path, 'WEBP', quality=92, method=6)
            print('wrote', os.path.relpath(path, ROOT))


if __name__ == '__main__':
    main(sys.argv[1:])
