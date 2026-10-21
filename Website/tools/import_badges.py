"""Import the supplied Aerborne badge artwork as transparent, web-sized medallions."""
import argparse
import json
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile
from PIL import Image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    args = parser.parse_args()
    output = Path(__file__).resolve().parents[1] / 'aerborne/static/img/badges'
    output.mkdir(parents=True, exist_ok=True)
    with ZipFile(args.archive) as archive:
        manifest = json.loads(archive.read('Aerborne_Badges_v1/manifest.json'))
        for entry in manifest:
            source = Path(entry['filename']).name
            slug = Path(source).stem.replace('_', '-')
            if not slug or not all(c.isascii() and (c.isalnum() or c == '-') for c in slug):
                raise ValueError('Unexpected badge filename')
            with Image.open(BytesIO(archive.read('Aerborne_Badges_v1/' + source))) as opened:
                art = opened.convert('RGBA')
                bounds = art.getbbox()
                if bounds is None:
                    raise ValueError('Empty badge: ' + source)
                art = art.crop(bounds)
                side = max(art.size) + 24
                canvas = Image.new('RGBA', (side, side))
                canvas.alpha_composite(art, ((side - art.width) // 2, (side - art.height) // 2))
                for size in (96, 192):
                    resized = canvas.resize((size, size), Image.Resampling.LANCZOS)
                    resized.save(output / ('%s-%d.webp' % (slug, size)), 'WEBP', quality=90, method=6)
                print(slug)


if __name__ == '__main__':
    main()
