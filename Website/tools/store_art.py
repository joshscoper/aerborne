"""Renders the store's art for cosmetics added after the painted set: companions from their real in-game
models, and trails, auras, finishers, titles and boosters from their particles, colours and names.

Run from Website/: .venv/Scripts/python.exe tools/store_art.py
Writes webp files under aerborne/static/img/. Staff can replace any of them later from the admin.
"""
import math
import os
import random
import re

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STATIC = os.path.join(ROOT, 'aerborne', 'static', 'img')
MODELS = os.path.join(os.path.dirname(ROOT), 'nerdsmp-paper', 'src', 'main', 'resources', 'companion-models.yml')
FONTS = 'C:/Windows/Fonts/'
SS = 3  # supersampling for smooth edges

BLOCKS = {
    'YELLOW_WOOL': (248, 197, 39), 'BLACK_WOOL': (28, 28, 34), 'OCHRE_FROGLIGHT': (250, 226, 150), 'WHITE_CONCRETE': (226, 230, 232),
    'BLACK_CONCRETE': (16, 18, 24), 'WHITE_STAINED_GLASS': (245, 250, 255), 'BROWN_WOOL': (122, 78, 46), 'SMOOTH_SANDSTONE': (226, 214, 166),
    'SPRUCE_PLANKS': (112, 82, 48), 'OAK_LEAVES': (74, 128, 44), 'RED_CONCRETE': (166, 36, 36), 'BROWN_TERRACOTTA': (88, 58, 40),
    'ORANGE_CONCRETE': (236, 108, 18), 'GOLD_BLOCK': (250, 214, 70), 'ORANGE_STAINED_GLASS': (245, 140, 50),
}
GLASS = ('STAINED_GLASS',)
GLOWING = ('FROGLIGHT',)
COLOURS = {'aqua': (85, 255, 255), 'dark_aqua': (40, 190, 200), 'light_purple': (255, 120, 255), 'white': (250, 250, 250),
           'yellow': (255, 230, 90), 'green': (110, 230, 110), 'gold': (255, 180, 40), 'red': (240, 70, 60), 'dark_purple': (170, 60, 200)}


def font(name, size):
    return ImageFont.truetype(FONTS + name, size * SS)


def save(image, relative, sizes):
    for size in sizes:
        out = image.resize((size, size), Image.LANCZOS)
        path = os.path.join(STATIC, relative.format(size=size))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        out.save(path, 'WEBP', quality=90, method=6)
        print('wrote', os.path.relpath(path, ROOT))


def gradient(size, top, bottom):
    image = Image.new('RGB', (size, size), top)
    draw = ImageDraw.Draw(image)
    for y in range(size):
        t = y / (size - 1)
        draw.line([(0, y), (size, y)], fill=tuple(int(a + (b - a) * t) for a, b in zip(top, bottom)))
    return image


def glow(base, layer, radius):
    """Adds a light layer twice: sharp, and blurred into a glow."""
    blurred = layer.filter(ImageFilter.GaussianBlur(radius * SS))
    return ImageChops.add(ImageChops.add(base, blurred), layer)


def night(size):
    image = gradient(size, (10, 22, 58), (4, 10, 30))
    stars = Image.new('RGB', image.size)
    draw = ImageDraw.Draw(stars)
    rng = random.Random(size)
    for _ in range(60):
        x, y, r = rng.randrange(size), rng.randrange(size), rng.choice((1, 1, 2)) * SS
        draw.ellipse([x - r, y - r, x + r, y + r], fill=(70, 90, 140))
    return ImageChops.add(image, stars)


# --- Companions ----------------------------------------------------------------------------------

def models():
    text = open(MODELS, encoding='utf-8').read()
    out, current = {}, None
    for line in text.splitlines():
        head = re.match(r'^([a-z_]+):\s*$', line)
        if head:
            current = out.setdefault(head.group(1), [])
            continue
        part = re.search(r'block: (\w+), at: \[([^\]]+)\], size: \[([^\]]+)\]', line)
        if part and current is not None:
            current.append((part.group(1), [float(v) for v in part.group(2).split(',')], [float(v) for v in part.group(3).split(',')]))
    return out


def render_model(parts, size, yaw=-35, pitch=22, scale=None):
    """A model of boxes, seen from the front-left and a little above, flat-shaded like Minecraft."""
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def view(x, y, z):
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return x, y, z

    faces = []
    for block, at, dims in parts:
        hx, hy, hz = (d / 2 for d in dims)
        corners = {(i, j, k): view(at[0] + i * hx, at[1] + j * hy, at[2] + k * hz) for i in (-1, 1) for j in (-1, 1) for k in (-1, 1)}
        for axis, sign, shade in ((1, 1, 1.0), (1, -1, .45), (0, 1, .78), (0, -1, .78), (2, 1, .9), (2, -1, .6)):
            keys = [c for c in corners if c[axis] == sign]
            pts = [corners[k] for k in keys]
            # Order the face's four corners round its edge.
            cx, cyy = sum(p[0] for p in pts) / 4, sum(p[1] for p in pts) / 4
            pts.sort(key=lambda p: math.atan2(p[1] - cyy, p[0] - cx))
            normal = view(*[sign if a == axis else 0 for a in range(3)])
            if normal[2] < 0:  # facing away from us
                continue
            depth = sum(p[2] for p in pts) / 4
            faces.append((depth, pts, block, shade))
    xs = [p[0] for f in faces for p in f[1]]
    ys = [p[1] for f in faces for p in f[1]]
    span = max(max(xs) - min(xs), max(ys) - min(ys))
    k = scale or size * .62 / span
    ox, oy = size / 2 - (max(xs) + min(xs)) / 2 * k, size * .56 + (max(ys) + min(ys)) / 2 * k
    layer = Image.new('RGBA', (size, size))
    draw = ImageDraw.Draw(layer)
    for depth, pts, block, shade in sorted(faces, key=lambda f: -f[0]):
        base = BLOCKS.get(block, (180, 180, 180))
        lit = 1.0 if any(g in block for g in GLOWING) else shade
        colour = tuple(min(255, int(c * lit)) for c in base) + ((150,) if any(g in block for g in GLASS) else (255,))
        poly = [(ox + p[0] * k, oy - p[1] * k) for p in pts]
        draw.polygon(poly, fill=colour, outline=tuple(int(c * .55) for c in colour[:3]) + (colour[3],))
    return layer, (min(ys) * k, oy)


def sky(size):
    image = gradient(size, (112, 166, 245), (206, 226, 255))
    clouds = Image.new('RGBA', (size, size))
    draw = ImageDraw.Draw(clouds)
    rng = random.Random(7)
    for _ in range(14):
        x, y, w = rng.randrange(size), rng.randrange(size // 2, size), rng.randrange(size // 6, size // 3)
        draw.ellipse([x - w, y - w // 3, x + w, y + w // 3], fill=(255, 255, 255, 170))
    clouds = clouds.filter(ImageFilter.GaussianBlur(10 * SS))
    image.paste(clouds, (0, 0), clouds)
    return image


def companion(slug, parts):
    size = 720 * SS
    image = sky(size)
    model, (bottom, oy) = render_model(parts, size)
    shadow = Image.new('RGBA', (size, size))
    ImageDraw.Draw(shadow).ellipse([size * .3, oy - bottom - size * .03, size * .7, oy - bottom + size * .05], fill=(40, 60, 110, 90))
    shadow = shadow.filter(ImageFilter.GaussianBlur(6 * SS))
    image.paste(shadow, (0, 0), shadow)
    image.paste(model, (0, 0), model)
    save(image, 'companions/' + slug + '-{size}.webp', (720, 360))
    crop = image.crop((int(size * .16), int(size * .12), int(size * .84), int(size * .8)))
    crop.resize((160 * SS, 160 * SS), Image.LANCZOS).resize((160, 160), Image.LANCZOS).save(os.path.join(STATIC, 'companions', slug + '-avatar.webp'), 'WEBP', quality=90)
    print('wrote companions/' + slug + '-avatar.webp')


# --- Particles -----------------------------------------------------------------------------------

def dot(draw, x, y, r, colour):
    draw.ellipse([x - r, y - r, x + r, y + r], fill=colour)


def snowflake(draw, x, y, r, colour):
    for a in range(3):
        angle = math.pi / 3 * a
        dx, dy = math.cos(angle) * r, math.sin(angle) * r
        draw.line([(x - dx, y - dy), (x + dx, y + dy)], fill=colour, width=max(1, int(r / 4)))


def petal(draw, x, y, r, colour, angle):
    pts = []
    for t in range(16):
        a = math.tau * t / 16
        px, py = math.cos(a) * r, math.sin(a) * r * .45
        pts.append((x + px * math.cos(angle) - py * math.sin(angle), y + px * math.sin(angle) + py * math.cos(angle)))
    draw.polygon(pts, fill=colour)


def figure(draw, size, colour=(20, 30, 60)):
    """A player's silhouette, standing on the ground."""
    u = size / 78
    cx = size / 2
    draw.rectangle([cx - 4 * u, size * .78 - 12 * u, cx + 4 * u, size * .78], fill=colour)        # legs
    draw.rectangle([cx - 4 * u, size * .78 - 24 * u, cx + 4 * u, size * .78 - 12 * u], fill=colour)  # body
    draw.rectangle([cx - 8 * u, size * .78 - 24 * u, cx - 4 * u, size * .78 - 13 * u], fill=colour)  # arms
    draw.rectangle([cx + 4 * u, size * .78 - 24 * u, cx + 8 * u, size * .78 - 13 * u], fill=colour)
    draw.rectangle([cx - 4 * u, size * .78 - 32 * u, cx + 4 * u, size * .78 - 24 * u], fill=colour)  # head
    return cx, size * .78, u


def trail(slug, kind, colour):
    size = 320 * SS
    image = night(size)
    light = Image.new('RGB', (size, size))
    draw = ImageDraw.Draw(light)
    rng = random.Random(slug)
    for i in range(70):
        t = i / 70
        x = size * (.08 + .84 * t)
        y = size * (.72 - .38 * math.sin(t * math.pi * .9)) + rng.uniform(-1, 1) * size * .06
        r = size * rng.uniform(.006, .016) * (.5 + t)
        c = tuple(int(v * (.35 + .65 * t)) for v in colour)
        if kind == 'snow':
            snowflake(draw, x, y - rng.uniform(0, size * .1), r * 2.2, c)
        elif kind == 'ember':
            dot(draw, x, y, r, c)
            dot(draw, x - r * 2, y + r, r * .6, tuple(int(v * .6) for v in c))
        else:
            dot(draw, x, y - rng.uniform(0, size * .25), r * 1.2, c)
    image = glow(image, light, 6)
    save(image, 'kit/trails/' + slug + '-{size}.webp', (320,))


def aura(slug, kind, colour):
    size = 320 * SS
    image = night(size)
    ImageDraw.Draw(image).ellipse([size * .3, size * .76, size * .7, size * .81], fill=(16, 28, 66))
    figure(ImageDraw.Draw(image), size, (34, 48, 92))
    light = Image.new('RGB', (size, size))
    draw = ImageDraw.Draw(light)
    cx, ground, u = size / 2, size * .78, size / 78
    rng = random.Random(slug)
    if kind == 'halo':
        for i in range(24):
            a = math.tau * i / 24
            dot(draw, cx + math.cos(a) * 7 * u, ground - 35 * u + math.sin(a) * 2.2 * u, u * .55, colour)
    elif kind == 'orbit':
        for i in range(10):
            a = math.tau * i / 10
            petal(draw, cx + math.cos(a) * 13 * u, ground - 17 * u + math.sin(a) * 5 * u, u * 1.4, colour, a)
    else:
        for _ in range(40):
            snowflake(draw, cx + rng.uniform(-13, 13) * u, ground - rng.uniform(4, 40) * u, u * rng.uniform(.6, 1.1), colour)
    image = glow(image, light, 5)
    save(image, 'store/auras/' + slug + '-{size}.webp', (320,))


def finisher(slug, kind, colour):
    size = 320 * SS
    image = night(size)
    light = Image.new('RGB', (size, size))
    draw = ImageDraw.Draw(light)
    cx, cy = size / 2, size * .55
    rng = random.Random(slug)
    if kind == 'burst':
        for i in range(40):
            a = math.tau * i / 40 + rng.uniform(-.05, .05)
            r1, r2 = size * rng.uniform(.06, .12), size * rng.uniform(.25, .4)
            draw.line([(cx + math.cos(a) * r1, cy + math.sin(a) * r1), (cx + math.cos(a) * r2, cy + math.sin(a) * r2)], fill=colour, width=SS * 2)
            dot(draw, cx + math.cos(a) * r2, cy + math.sin(a) * r2, SS * 3, colour)
    elif kind == 'fountain':
        palette = [(255, 220, 70), (110, 230, 110), (90, 200, 255), (255, 110, 160), (255, 160, 60)]
        for _ in range(110):
            a = rng.uniform(-math.pi * .95, -math.pi * .05)
            d = size * rng.uniform(.05, .38)
            x, y = cx + math.cos(a) * d, cy + math.sin(a) * d * 1.1
            w = SS * rng.uniform(3, 6)
            draw.rectangle([x - w, y - w / 2, x + w, y + w / 2], fill=rng.choice(palette))
    else:
        for i in range(60):
            y = cy + size * .2 - size * .5 * i / 60
            dot(draw, cx + rng.uniform(-1, 1) * size * .05 * (1 - i / 60), y, SS * rng.uniform(3, 8) * (1 - i / 90), colour)
    image = glow(image, light, 7)
    save(image, 'store/finishers/' + slug + '-{size}.webp', (320,))


def title(slug, name, colour):
    size = 320 * SS
    image = night(size)
    draw = ImageDraw.Draw(image)
    plate = [size * .08, size * .38, size * .92, size * .62]
    draw.rounded_rectangle(plate, radius=SS * 14, fill=(14, 24, 52), outline=(214, 170, 72), width=SS * 3)
    big, small = font('georgiab.ttf', 30), font('segoeuib.ttf', 15)
    text = '[' + name + ']'
    w = draw.textlength(text, font=big)
    draw.text(((size - w) / 2, size * .435), text, font=big, fill=colour)
    caption = 'before your name in chat'
    w = draw.textlength(caption, font=small)
    draw.text(((size - w) / 2, size * .7), caption, font=small, fill=(150, 170, 210))
    light = Image.new('RGB', (size, size))
    ImageDraw.Draw(light).text(((size - draw.textlength(text, font=big)) / 2, size * .435), text, font=big, fill=tuple(int(c * .7) for c in colour))
    image = glow(image, light, 6)
    save(image, 'store/titles/' + slug + '-{size}.webp', (320,))


def emblem(relative, sizes, draw_icon, background=True):
    size = 512 * SS
    image = night(size) if background else Image.new('RGB', (size, size))
    light = Image.new('RGB', (size, size))
    draw_icon(ImageDraw.Draw(light), size)
    image = glow(image, light, 9)
    if not background:
        # Category icons sit on a card of their own: keep only what's lit.
        alpha = image.convert('L').point(lambda v: min(255, v * 3))
        image = image.convert('RGBA')
        image.putalpha(alpha)
    save(image, relative, sizes)


def booster_icon(draw, size):
    cx, cy, r = size / 2, size * .5, size * .3
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(250, 200, 70), width=SS * 10)
    bolt = [(cx + r * .1, cy - r * .75), (cx - r * .35, cy + r * .1), (cx - r * .02, cy + r * .1), (cx - r * .15, cy + r * .75),
            (cx + r * .38, cy - r * .12), (cx + r * .05, cy - r * .12)]
    draw.polygon(bolt, fill=(255, 220, 90))
    label = font('segoeuib.ttf', 46)
    w = draw.textlength('+50% XP', font=label)
    draw.text((cx - w / 2, cy + r * 1.15), '+50% XP', font=label, fill=(255, 230, 140))


def titles_icon(draw, size):
    cx, cy = size / 2, size * .5
    draw.polygon([(cx - size * .36, cy - size * .1), (cx + size * .36, cy - size * .1), (cx + size * .3, cy + size * .1), (cx - size * .3, cy + size * .1)], fill=(200, 160, 70))
    draw.polygon([(cx - size * .44, cy - size * .02), (cx - size * .36, cy - size * .1), (cx - size * .3, cy + size * .1), (cx - size * .4, cy + size * .14)], fill=(150, 110, 40))
    draw.polygon([(cx + size * .44, cy - size * .02), (cx + size * .36, cy - size * .1), (cx + size * .3, cy + size * .1), (cx + size * .4, cy + size * .14)], fill=(150, 110, 40))
    star(draw, cx, cy - size * .2, size * .08, (255, 236, 150))


def star(draw, x, y, r, colour):
    pts = []
    for i in range(10):
        a = -math.pi / 2 + math.pi * i / 5
        d = r if i % 2 == 0 else r * .45
        pts.append((x + math.cos(a) * d, y + math.sin(a) * d))
    draw.polygon(pts, fill=colour)


def auras_icon(draw, size):
    cx, cy = size / 2, size * .55
    for i in range(36):
        a = math.tau * i / 36
        dot(draw, cx + math.cos(a) * size * .3, cy + math.sin(a) * size * .3, SS * 6, (150, 220, 255))
    draw.ellipse([cx - size * .15, cy - size * .48, cx + size * .15, cy - size * .38], outline=(255, 236, 150), width=SS * 8)
    for i in range(7):
        star(draw, cx + math.cos(i) * size * .2, cy + math.sin(i * 2.3) * size * .2, size * .025, (255, 255, 255))


def finishers_icon(draw, size):
    cx, cy = size / 2, size / 2
    for i in range(24):
        a = math.tau * i / 24
        r2 = size * (.42 if i % 2 else .3)
        draw.line([(cx + math.cos(a) * size * .08, cy + math.sin(a) * size * .08), (cx + math.cos(a) * r2, cy + math.sin(a) * r2)], fill=(255, 200, 80), width=SS * 7)
    star(draw, cx, cy, size * .12, (255, 250, 220))


def main():
    shapes = models()
    for slug in ('glimmerbee', 'bramblehog', 'driftfin'):
        companion(slug, shapes[slug])
    trail('snowdrift', 'snow', COLOURS['white'])
    trail('ember-wake', 'ember', COLOURS['gold'])
    trail('glowspore', 'spore', COLOURS['green'])
    aura('glimmer-halo', 'halo', COLOURS['white'])
    aura('petal-orbit', 'orbit', (255, 150, 210))
    aura('snow-globe', 'flurry', COLOURS['white'])
    finisher('starburst', 'burst', COLOURS['yellow'])
    finisher('confetti-pop', 'fountain', COLOURS['green'])
    finisher('soul-release', 'column', (90, 220, 255))
    for slug, name, colour in (('skyborne', 'Skyborne', 'aqua'), ('stormchaser', 'Stormchaser', 'dark_aqua'),
                               ('starlit', 'Starlit', 'light_purple'), ('windrider', 'Windrider', 'white')):
        title(slug, name, COLOURS[colour])
    emblem('store/xp-booster-{size}.webp', (320,), booster_icon)
    emblem('store/category-boosters-{size}.webp', (256, 512), booster_icon, background=False)
    emblem('store/category-titles-{size}.webp', (256, 512), titles_icon, background=False)
    emblem('store/category-auras-{size}.webp', (256, 512), auras_icon, background=False)
    emblem('store/category-finishers-{size}.webp', (256, 512), finishers_icon, background=False)


if __name__ == '__main__':
    main()
