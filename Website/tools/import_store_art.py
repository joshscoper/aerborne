"""Import the supplied illustrated store pack as transparent, responsive WebP assets."""
import argparse
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile

from PIL import Image

NAMES = ('category-ranks', 'category-companions', 'category-trails', 'category-bundles',
         'shopkeeper-mascot', 'empty-cart-illustration')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    args = parser.parse_args()
    target = Path(__file__).resolve().parents[1] / 'aerborne' / 'static' / 'img' / 'store'
    target.mkdir(parents=True, exist_ok=True)
    with ZipFile(str(args.archive)) as archive:
        for name in NAMES:
            matches = [entry for entry in archive.namelist() if entry.rsplit('/', 1)[-1] == name + '.png']
            if len(matches) != 1:
                raise ValueError('Expected one source image for ' + name)
            with Image.open(BytesIO(archive.read(matches[0]))) as source:
                source = source.convert('RGBA')
                for size in (256, 512):
                    image = source.copy()
                    image.thumbnail((size, size), Image.Resampling.LANCZOS)
                    path = target / ('%s-%d.webp' % (name, size))
                    image.save(str(path), 'WEBP', quality=90, method=6)
                    print('%s: %s bytes' % (path.name, path.stat().st_size))


if __name__ == '__main__':
    main()
