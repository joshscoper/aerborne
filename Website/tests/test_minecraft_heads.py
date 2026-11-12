"""Direct Mojang skin rendering, bounded upstream requests and cached delivery."""
import base64
from email.message import Message
import io
import json
from pathlib import Path
import struct
import sys
import unittest
from unittest import mock
import urllib.error

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from flask import Flask
from aerborne import minecraft
from PIL import Image

UUID = 'f7b99a3dfc504b9b87441c9cf27b8d6e'
HASH = 'a' * 64


def skin_png(size=(64, 64), opaque=False):
    image = Image.new('RGBA', size, (0, 0, 0, 255 if opaque else 0))
    image.paste((255, 0, 0, 0), (8, 8, 16, 16))
    if not opaque and size[0] >= 48:
        image.putpixel((40, 8), (0, 0, 255, 128))
        image.putpixel((41, 8), (0, 255, 0, 255))
    output = io.BytesIO()
    image.save(output, 'PNG')
    return output.getvalue()


def session_profile(url='http://textures.minecraft.net/texture/' + HASH):
    payload = {'profileId': UUID, 'textures': {'SKIN': {'url': url}}}
    value = base64.b64encode(json.dumps(payload).encode()).decode()
    return {'id': UUID, 'properties': [{'name': 'textures', 'value': value}]}


class MinecraftHeadsTest(unittest.TestCase):
    def setUp(self):
        minecraft._cache.clear()
        minecraft._names.clear()
        app = Flask(__name__, static_folder=str(Path(minecraft.__file__).parent / 'static'))
        app.register_blueprint(minecraft.bp)
        self.client = app.test_client()
        self.png = minecraft._render(skin_png())

    def test_same_player_is_cached_across_letter_case(self):
        with mock.patch.object(minecraft, '_fetch', return_value=self.png) as fetch:
            first = self.client.get('/minecraft/head/SkyPlayer.png?v=mojang-1')
            second = self.client.get('/minecraft/head/skyplayer.png')
        self.assertEqual(first.data, self.png)
        self.assertEqual(second.data, self.png)
        self.assertEqual(first.mimetype, 'image/png')
        self.assertIn('public, max-age=300', first.headers['Cache-Control'])
        fetch.assert_called_once_with('skyplayer')

    def test_failed_fetch_returns_local_image_and_retries_after_short_cache(self):
        with mock.patch.object(minecraft, '_fetch', return_value=None) as fetch:
            first = self.client.get('/minecraft/head/SkyPlayer.png')
            self.client.get('/minecraft/head/SkyPlayer.png').close()
            self.assertEqual(fetch.call_count, 1)
            with mock.patch.object(minecraft.time, 'monotonic', return_value=minecraft._cache['skyplayer'][0] + 1):
                self.client.get('/minecraft/head/SkyPlayer.png').close()
            self.assertEqual(fetch.call_count, 2)
        self.assertEqual(first.status_code, 200)
        self.assertEqual(first.mimetype, 'image/webp')
        self.assertIn('max-age=60', first.headers['Cache-Control'])
        self.assertEqual(first.data, (Path(minecraft.__file__).parent / 'static/img/brand/skycrest-192.webp').read_bytes())
        first.close()

    def test_outage_retains_last_good_head_but_not_forever(self):
        with mock.patch.object(minecraft.time, 'monotonic', return_value=100), mock.patch.object(minecraft, '_fetch', return_value=self.png):
            self.client.get('/minecraft/head/SkyPlayer.png')
        with mock.patch.object(minecraft.time, 'monotonic', return_value=100 + minecraft._TTL + 1), mock.patch.object(minecraft, '_fetch', return_value=None):
            stale = self.client.get('/minecraft/head/SkyPlayer.png')
        self.assertEqual(stale.data, self.png)
        self.assertIn('max-age=60', stale.headers['Cache-Control'])
        with mock.patch.object(minecraft.time, 'monotonic', return_value=100 + minecraft._STALE_TTL + 1), mock.patch.object(minecraft, '_fetch', return_value=None):
            expired = self.client.get('/minecraft/head/SkyPlayer.png')
        self.assertEqual(expired.mimetype, 'image/webp')
        expired.close()

    def test_invalid_identifiers_never_contact_upstream(self):
        with mock.patch.object(minecraft, '_fetch') as fetch:
            for name in ['ab', 'a' * 17, 'user@example.com', '..', 'name%0A']:
                self.assertEqual(self.client.get('/minecraft/head/' + name + '.png').status_code, 404)
            fetch.assert_not_called()

    def test_modern_and_legacy_transparent_hats_use_correct_pixels(self):
        for size in [(64, 64), (64, 32)]:
            with Image.open(io.BytesIO(minecraft._render(skin_png(size)))) as image:
                self.assertEqual(image.size, (128, 128))
                self.assertEqual(image.getpixel((0, 0)), (127, 0, 128, 255))
                self.assertEqual(image.getpixel((15, 15)), (127, 0, 128, 255))
                self.assertEqual(image.getpixel((16, 0)), (0, 255, 0, 255))
                self.assertEqual(image.getpixel((32, 0)), (255, 0, 0, 255))

    def test_opaque_unused_legacy_hat_does_not_cover_the_face(self):
        with Image.open(io.BytesIO(minecraft._render(skin_png((64, 32), opaque=True)))) as image:
            self.assertEqual(image.getpixel((0, 0)), (255, 0, 0, 255))

    def test_bad_or_oversized_skin_is_rejected_before_rendering(self):
        valid = skin_png()
        invalid = [b'<svg></svg>', valid[:33], valid + b'x' * minecraft._MAX_BYTES,
                   valid[:16] + struct.pack('>II', 100000, 100000) + valid[24:], skin_png((32, 32))]
        for data in invalid:
            with self.assertRaises((ValueError, OSError)):
                minecraft._render(data)

    def test_fetch_resolves_account_and_only_downloads_mojang_texture_over_https(self):
        with mock.patch.object(minecraft, '_json', side_effect=[{'id': UUID, 'name': 'SkyPlayer'}, session_profile()]) as get_json, mock.patch.object(minecraft, '_download', return_value=skin_png()) as download:
            self.assertEqual(minecraft._fetch('skyplayer'), self.png)
        self.assertEqual(get_json.call_args_list, [mock.call('https://api.mojang.com/users/profiles/minecraft/skyplayer'), mock.call('https://sessionserver.mojang.com/session/minecraft/profile/' + UUID)])
        download.assert_called_once_with('https://textures.minecraft.net/texture/' + HASH, 'image/png', minecraft._MAX_BYTES)
        with mock.patch.object(minecraft, '_json', return_value=session_profile()) as get_json, mock.patch.object(minecraft, '_download', return_value=skin_png()):
            self.assertEqual(minecraft._fetch('skyplayer'), self.png)
        self.assertEqual(get_json.call_count, 1, 'Cached UUID skips the name lookup')

    def test_untrusted_urls_and_malformed_profiles_never_download_images(self):
        bad = [session_profile(url) for url in ['https://example.com/skin.png', 'https://textures.minecraft.net.evil.test/texture/' + HASH, 'http://127.0.0.1/texture/' + HASH, 'file:///etc/passwd', 'https://textures.minecraft.net/texture/' + HASH + '?redirect=1']]
        bad.extend([{'id': '0' * 32, 'properties': []}, {'id': UUID, 'properties': [{'name': 'textures', 'value': '!!!'}]}, {'id': UUID, 'properties': [{'name': 'textures', 'value': base64.b64encode(b'[]').decode()}]}, {'id': UUID, 'properties': []}])
        with mock.patch.object(minecraft, '_resolve', return_value=UUID), mock.patch.object(minecraft, '_download') as download:
            for profile in bad:
                with mock.patch.object(minecraft, '_json', return_value=profile):
                    self.assertIsNone(minecraft._fetch('skyplayer'))
            download.assert_not_called()

    def test_download_is_bounded_rejects_wrong_types_and_disables_redirects(self):
        response = mock.MagicMock()
        response.__enter__.return_value = response
        response.status = 200
        response.headers = Message()
        response.headers.add_header('Content-Type', 'image/png')
        response.read.return_value = skin_png()
        opener = mock.Mock()
        opener.open.return_value = response
        with mock.patch.object(minecraft.urllib.request, 'build_opener', return_value=opener) as build:
            url = 'https://textures.minecraft.net/texture/' + HASH
            self.assertEqual(minecraft._download(url, 'image/png', 1000), skin_png())
            self.assertIsInstance(build.call_args[0][0], minecraft._NoRedirect)
            response.read.assert_called_with(1001)
            request = opener.open.call_args[0][0]
            self.assertIsNone(request.get_header('Cookie'))
            self.assertIsNone(request.get_header('Authorization'))
            response.read.return_value = b'x' * 1001
            with self.assertRaises(ValueError):
                minecraft._download(url, 'image/png', 1000)
            response.read.return_value = b'{}'
            response.headers.replace_header('Content-Type', 'text/html')
            with self.assertRaises(ValueError):
                minecraft._download(url, 'application/json', 1000)
            response.status = 204
            with self.assertRaises(ValueError):
                minecraft._download(url, 'application/json', 1000)
        self.assertIsNone(minecraft._NoRedirect().redirect_request(None, None, 302, '', {}, 'https://example.com'))

    def test_missing_image_library_and_upstream_failure_use_fallback(self):
        with mock.patch.object(minecraft, 'Image', None), mock.patch.object(minecraft, '_resolve') as resolve:
            self.assertIsNone(minecraft._fetch('skyplayer'))
            resolve.assert_not_called()
        with mock.patch.object(minecraft, '_resolve', side_effect=urllib.error.URLError('offline')):
            self.assertIsNone(minecraft._fetch('skyplayer'))


if __name__ == '__main__':
    unittest.main()
