"""Import selected supplied artwork; the kit's example application is not executable input."""
from pathlib import Path
import zipfile
import xml.etree.ElementTree as ET

dest = Path('nerdsmp-paper/src/main/resources/panel/assets')
dest.mkdir(parents=True, exist_ok=True)
sources = {
    'brand.svg': 'brand/staff-console-lockup.svg',
    'emblem.svg': 'brand/staff-console-emblem.svg',
    'pattern.svg': 'backgrounds/starfield-tile.svg',
}
sections = ['overview', 'players', 'locations', 'watchdog', 'reports', 'sanctions', 'server', 'audit-log', 'staff']
for section in sections:
    sources['hero-' + section + '.webp'] = ('heroes/staff-console-hero-dark-1280w.webp' if section == 'overview'
                                          else 'heroes/header-' + section + '.webp')
    sources['nav-' + section + '.svg'] = 'navigation/' + section + '.svg'
    sources['art-' + section + '.webp'] = 'illustrations/' + section + '/' + section + '-256.webp'
with zipfile.ZipFile(r'C:\Users\Josh\Downloads\Aerborne_Staff_Console_Complete_Web_Kit.zip') as archive:
    for name, source in sources.items():
        data = archive.read('Aerborne_Staff_Console_Complete_Web_Kit/' + source)
        if name.endswith('.svg'):
            root = ET.fromstring(data)
            for node in root.iter():
                if node.tag.split('}')[-1] in ('script', 'foreignObject', 'image', 'use'):
                    raise ValueError('Unexpected active or external SVG content: ' + name)
                if any(k.lower().startswith('on') or 'href' in k.lower() for k in node.attrib):
                    raise ValueError('Unexpected SVG reference: ' + name)
        (dest / name).write_bytes(data)
print('Imported', len(sources), 'assets;', sum((dest / name).stat().st_size for name in sources), 'bytes')
