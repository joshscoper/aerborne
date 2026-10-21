from test_app import SiteTest
from unittest import mock
from aerborne.discord import widget_available
import io
import urllib.error


class DiscordTest(SiteTest):
    def configure(self, invite='', widget=''):
        conn = self.db()
        for key, value in [('discord_invite', invite), ('discord_server_id', widget)]:
            conn.execute('INSERT OR REPLACE INTO settings (key,value) VALUES (?,?)', (key, value))
        conn.commit()
        conn.close()

    def test_landing_page_and_unconfigured_fallback(self):
        self.app.config.update(DISCORD_URL='', DISCORD_SERVER_ID='')
        page = self.client.get('/discord')
        self.assertEqual(page.status_code, 200)
        self.assertIn(b'Your patch of sky.', page.data)
        self.assertIn(b'The Discord invite isn', page.data)
        self.assertIn(b'href="/forum"', page.data)
        self.assertNotIn(b'<iframe', page.data)
        self.assertNotIn('frame-src https://discord.com', page.headers['Content-Security-Policy'])

    @mock.patch('aerborne.discord.widget_available', return_value=True)
    def test_configured_widget_stays_on_site_and_keeps_invite_fallback(self, _available):
        self.configure('https://discord.gg/ExampleInvite', '123456789012345678')
        page = self.client.get('/discord')
        self.assertEqual(page.status_code, 200)
        self.assertIn(b'href="https://discord.gg/ExampleInvite"', page.data)
        self.assertIn(b'https://discord.com/widget?id=123456789012345678&amp;theme=dark', page.data)
        self.assertIn(b'Preview not loading? Open the invite', page.data)
        self.assertIn('frame-src https://discord.com', page.headers['Content-Security-Policy'])
        self.assertNotIn('https://discord.com', self.client.get('/').headers['Content-Security-Policy'])
        self.configure('javascript:alert(1)', '123456789012345678&theme=evil')
        self.app.config.update(DISCORD_URL='https://discord.com/invite/EnvironmentInvite',
                               DISCORD_SERVER_ID='987654321098765432')
        page = self.client.get('/discord')
        self.assertIn(b'https://discord.com/invite/EnvironmentInvite', page.data)
        self.assertIn(b'widget?id=987654321098765432&amp;theme=dark', page.data)
        self.assertNotIn(b'javascript:', page.data)

    @mock.patch('aerborne.discord.widget_available', return_value=True)
    def test_admin_settings_validate_destinations(self, _available):
        self.add_user('DiscordAdmin', 'admin')
        self.login('DiscordAdmin')
        token = self.csrf('/admin/settings')
        for invite, widget in [('https://discord.gg/ExampleInvite', 'not-an-id'),
                               ('https://discord.gg.evil.test/ExampleInvite', '123456789012345678')]:
            response = self.client.post('/admin/settings', data={'_csrf': token,
                'discord_invite': invite, 'discord_server_id': widget}, follow_redirects=True)
            self.assertIn(b'class="flash flash-error"', response.data)
            conn = self.db()
            row = conn.execute("SELECT value FROM settings WHERE key='discord_server_id'").fetchone()
            self.assertFalse(row and row['value'])
            conn.close()
        self.client.post('/admin/settings', data={'_csrf': token,
            'discord_invite': 'https://discord.gg/ExampleInvite', 'discord_server_id': '123456789012345678'})
        page = self.client.get('/discord')
        self.assertIn(b'widget?id=123456789012345678', page.data)

    def test_disabled_widget_fallback_and_recovery(self):
        self.configure('https://discord.gg/ExampleInvite', '123456789012345678')
        with mock.patch('aerborne.discord.urllib.request.urlopen', side_effect=urllib.error.HTTPError(
                'https://discord.com', 403, 'Widget Disabled', {}, None)) as fetch:
            for _ in range(2):
                page = self.client.get('/discord')
                self.assertNotIn(b'<iframe', page.data)
                self.assertIn(b'Open Discord', page.data)
                self.assertIn(b'live preview is currently unavailable', page.data)
            self.assertEqual(fetch.call_count, 1)
        self.app.extensions['discord_widget']['until'] = 0
        with mock.patch('aerborne.discord.urllib.request.urlopen', return_value=io.BytesIO(
                b'{"id":"123456789012345678"}')) as fetch:
            page = self.client.get('/discord')
            self.assertIn(b'<iframe', page.data)
            self.assertIn('frame-src https://discord.com', page.headers['Content-Security-Policy'])
            self.assertEqual(fetch.call_args.kwargs['timeout'], 3)
        with self.app.app_context(), mock.patch('aerborne.discord.urllib.request.urlopen') as fetch:
            self.assertFalse(widget_available('evil.example'))
            fetch.assert_not_called()
