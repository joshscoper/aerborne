"""Import the supplied supporter rank WebP exports without altering the artwork."""
import argparse
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile

from PIL import Image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    args = parser.parse_args()
    target = Path(__file__).resolve().parents[1] / 'aerborne' / 'static' / 'img' / 'store' / 'ranks'
    exports = {}
    with ZipFile(args.archive) as archive:
        for rank in ('skyfarer', 'cloudkeeper', 'starweaver'):
            for size in (128, 256, 512):
                filename = '%s-%d.webp' % (rank, size)
                matches = [name for name in archive.namelist() if name.rsplit('/', 1)[-1] == filename]
                if len(matches) != 1:
                    raise ValueError('Expected one export: ' + filename)
                data = archive.read(matches[0])
                with Image.open(BytesIO(data)) as image:
                    if image.format != 'WEBP' or image.size != (size, size) or 'A' not in image.getbands():
                        raise ValueError('Expected a transparent square WebP: ' + filename)
                    image.verify()
                exports[filename] = data
    target.mkdir(parents=True, exist_ok=True)
    for filename, data in exports.items():
        (target / filename).write_bytes(data)
        print('%s: %d bytes' % (filename, len(data)))


if __name__ == '__main__':
    main()
