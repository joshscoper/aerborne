import json
import unittest
import xml.etree.ElementTree as ET
from email.utils import parsedate_to_datetime
from unittest import mock

from test_app import SiteTest
from aerborne import streaming
from aerborne.seed_updates import _insert


class FeedTest(SiteTest):
    def test_valid_xml_canonical_links_dates_and_conditional_requests(self):
        self.app.config['SITE_URL'] = 'https://aerborne.example/'
        conn = self.db()
        conn.execute("UPDATE news SET title = ?, summary = ? WHERE slug = 'welcome'",
                     ('Sky & stone <news> ☁', 'Fish & chips <3 builds'))
        _insert(conn, 'news', {'slug': 'private-feed-post', 'title': 'Private feed post', 'status': 'draft'})
        _insert(conn, 'news', {'slug': 'future-feed-post', 'title': 'Future feed post', 'publish_at': '2099-01-01 00:00:00'})
        conn.commit()
        conn.close()
        response = self.client.get('/news/feed.xml')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.content_type, 'application/rss+xml; charset=utf-8')
        root = ET.fromstring(response.data)
        channel = root.find('channel')
        # The starter welcome post and the shipped update notes; drafts and future posts stay out.
        entries = [e for e in channel.findall('item') if e.findtext('guid', '').endswith('/news/welcome')]
        self.assertEqual(len(channel.findall('item')), 3)
        self.assertFalse([e for e in channel.findall('item') if 'feed-post' in e.findtext('guid', '')])
        self.assertEqual(entries[0].findtext('title'), 'Sky & stone <news> ☁')
        self.assertEqual(entries[0].findtext('description'), 'Fish & chips <3 builds')
        self.assertEqual(entries[0].findtext('guid'), 'https://aerborne.example/news/welcome')
        self.assertEqual(entries[0].find('guid').attrib['isPermaLink'], 'true')
        self.assertIsNotNone(parsedate_to_datetime(entries[0].findtext('pubDate')))
        self.assertIsNotNone(parsedate_to_datetime(channel.findtext('lastBuildDate')))
        self.assertIn('Last-Modified', response.headers)
        etag = response.headers['ETag']
        unchanged = self.client.get('/news/feed.xml', headers={'If-None-Match': etag})
        self.assertEqual(unchanged.status_code, 304)
        self.assertFalse(unchanged.data)
        conn = self.db()
        conn.execute("UPDATE news SET title = 'A changed title' WHERE slug = 'welcome'")
        conn.commit()
        conn.close()
        changed = self.client.get('/news/feed.xml', headers={'If-None-Match': etag})
        self.assertEqual(changed.status_code, 200)
        self.assertNotEqual(changed.headers['ETag'], etag)

    def test_category_feeds_subscription_and_empty_feed(self):
        conn = self.db()
        for number in range(25):
            _insert(conn, 'news', {'slug': 'patch-%s' % number, 'title': 'Patch %s' % number, 'category': 'patch'})
        conn.commit()
        conn.close()
        response = self.client.get('/news/feed.xml?category=patch')
        root = ET.fromstring(response.data)
        self.assertEqual(len(root.findall('./channel/item')), 20)
        self.assertTrue(all(item.findtext('category') == 'Patch notes' for item in root.findall('./channel/item')))
        self.assertEqual(len(ET.fromstring(self.client.get('/news/feed.xml?category=event').data).findall('./channel/item')), 0)
        page = self.client.get('/news/subscribe?category=patch').get_data(as_text=True)
        self.assertIn('http://localhost/news/feed.xml?category=patch', page)
        self.assertIn('Copy feed URL', page)
        self.assertIn('data-copy-target="feed-url"', page)
        self.assertEqual(self.client.get('/news/feed.xml?category=secret').status_code, 404)
        self.assertEqual(self.client.get('/news/subscribe?category=secret').status_code, 404)


class StreamingTest(SiteTest):
    def setUp(self):
        super().setUp()
        streaming._tokens.clear()
        self.app.config.update(TWITCH_CLIENT_ID='test-client', TWITCH_CLIENT_SECRET='private-twitch-secret',
                               KICK_CLIENT_ID='test-kick', KICK_CLIENT_SECRET='private-kick-secret',
                               YOUTUBE_API_KEY='private-youtube-key')

    def creators(self):
        conn = self.db()
        for platform, channel in [('twitch', 'skybuilder'), ('youtube', 'UC' + 'a' * 22), ('kick', 'cloudcraft')]:
            _insert(conn, 'streamers', {'slug': platform, 'name': platform.title(), 'platform': platform, 'channel': channel})
        _insert(conn, 'streamers', {'slug': 'private-creator', 'name': 'Private Creator', 'platform': 'twitch',
                                   'channel': 'private', 'status': 'draft'})
        _insert(conn, 'streamers', {'slug': 'scheduled-creator', 'name': 'Scheduled Creator', 'platform': 'kick',
                                   'channel': 'scheduled', 'publish_at': '2099-01-01 00:00:00'})
        conn.commit()
        conn.close()

    def expire(self):
        conn = self.db()
        conn.execute('UPDATE stream_status_cache SET expires_at = 0')
        conn.commit()
        conn.close()

    def test_live_status_all_platforms_cache_and_private_visibility(self):
        self.creators()
        def request(url, config, headers=None, form=None):
            if 'oauth' in url:
                return {'access_token': 'private-app-token', 'expires_in': 3600}
            if 'twitch.tv/helix' in url:
                return {'data': [{'type': 'live', 'title': '<script>unsafe</script> Island day', 'viewer_count': 25}]}
            if 'api.kick.com' in url:
                return {'data': [{'stream_title': 'An island on Kick', 'stream': {'is_live': True, 'viewer_count': 12}}]}
            return {'items': [{'id': {'videoId': 'abcdefghijk'}, 'snippet': {'title': 'YouTube &amp; clouds'}}]}
        with mock.patch.object(streaming, '_json_request', side_effect=request) as network:
            response = self.client.get('/community/streamers/status')
            self.assertEqual(response.status_code, 200)
            channels = response.get_json()['channels']
            self.assertEqual(set(channels), {'twitch', 'youtube', 'kick'})
            self.assertTrue(all(item['state'] == 'live' for item in channels.values()))
            self.assertEqual(channels['youtube']['watch_url'], 'https://www.youtube.com/watch?v=abcdefghijk')
            self.assertEqual(channels['youtube']['title'], 'YouTube & clouds')
            calls = network.call_count
            self.assertEqual(self.client.get('/community/streamers/status').get_json()['channels'], channels)
            self.assertEqual(network.call_count, calls)
            page = self.client.get('/community/streamers').get_data(as_text=True)
            self.assertIn('Live now', page)
            self.assertIn('&lt;script&gt;unsafe&lt;/script&gt;', page)
            for secret in ('private-app-token', 'private-twitch-secret', 'private-kick-secret', 'private-youtube-key', 'Private Creator', 'Scheduled Creator'):
                self.assertNotIn(secret, page)
                self.assertNotIn(secret, response.get_data(as_text=True))

    def test_failure_clears_live_status_and_offline_is_explicit(self):
        self.creators()
        with mock.patch.object(streaming, '_probe', return_value={'state': 'live', 'title': 'Live test', 'checked_at': 1}):
            self.client.get('/community/streamers/status')
        self.expire()
        with mock.patch.object(streaming, '_json_request', side_effect=streaming.ProviderError(503)):
            channels = self.client.get('/community/streamers/status').get_json()['channels']
            self.assertTrue(all(item['state'] == 'unavailable' for item in channels.values()))
            self.assertTrue(all(not item.get('title') for item in channels.values()))
        self.expire()
        def offline(url, config, headers=None, form=None):
            if 'oauth' in url:
                return {'access_token': 'test-token', 'expires_in': 3600}
            if 'api.kick.com' in url:
                return {'data': [{'stream': {'is_live': False}}]}
            return {'data': [], 'items': []}
        with mock.patch.object(streaming, '_json_request', side_effect=offline):
            channels = self.client.get('/community/streamers/status').get_json()['channels']
            self.assertTrue(all(item['state'] == 'offline' for item in channels.values()))
        conn = self.db()
        cache = conn.execute('SELECT expires_at FROM stream_status_cache').fetchall()
        self.assertTrue(any(row[0] > __import__('time').time() + 1700 for row in cache))
        conn.close()

    def test_unconfigured_makes_no_network_calls(self):
        self.creators()
        self.app.config.update(TWITCH_CLIENT_SECRET='', KICK_CLIENT_SECRET='', YOUTUBE_API_KEY='')
        with mock.patch.object(streaming, '_json_request') as network:
            channels = self.client.get('/community/streamers/status').get_json()['channels']
            self.assertTrue(all(item['state'] == 'unconfigured' for item in channels.values()))
            network.assert_not_called()

    def test_tokens_refresh_once_after_unauthorized(self):
        replies = [{'access_token': 'first', 'expires_in': 3600}, streaming.ProviderError(401),
                   {'access_token': 'second', 'expires_in': 3600}, {'data': []}]
        with mock.patch.object(streaming, '_json_request', side_effect=replies) as network:
            result = streaming._oauth_get('twitch', 'https://api.twitch.tv/helix/streams?user_login=skybuilder', self.app.config)
            self.assertEqual(result, {'data': []})
            self.assertEqual(network.call_count, 4)
            self.assertEqual(network.call_args_list[-1].kwargs['headers']['Authorization'], 'Bearer second')

    def test_youtube_handle_and_already_live_video_checks(self):
        with mock.patch.object(streaming, '_json_request', side_effect=[{'items': [{'id': 'UC' + 'a' * 22}]},
                {'items': [{'id': {'videoId': 'abcdefghijk'}, 'snippet': {'title': 'Clouds'}}]}]) as network:
            result = streaming._youtube('@skybuilder', self.app.config, {})
            self.assertEqual(result['state'], 'live')
            self.assertIn('forHandle=%40skybuilder', network.call_args_list[0].args[0])
        with mock.patch.object(streaming, '_json_request', return_value={'items': [{'snippet': {'liveBroadcastContent': 'none'},
                   'liveStreamingDetails': {'actualEndTime': '2026-10-19T12:00:00Z'}}]}) as network:
            result = streaming._youtube('@skybuilder', self.app.config, {'state': 'live', 'video_id': 'abcdefghijk'})
            self.assertEqual(result['state'], 'offline')
            self.assertIn('/videos?', network.call_args.args[0])
        with mock.patch.object(streaming, '_json_request', return_value={}):
            item = {'platform': 'youtube', 'channel': '@skybuilder'}
            self.assertEqual(streaming._probe(item, self.app.config, {})['state'], 'unavailable')

    def test_channel_validation_and_staff_publishing(self):
        self.assertEqual(streaming.normalize_channel('twitch', 'https://www.twitch.tv/SkyBuilder'), 'skybuilder')
        self.assertEqual(streaming.normalize_channel('youtube', 'https://www.youtube.com/@skybuilder'), '@skybuilder')
        for raw in ('https://evil.example/channel', 'https://twitch.tv@evil.example/abc', 'https://twitch.tv/abc/def', 'http://127.0.0.1/admin'):
            self.assertIsNone(streaming.normalize_channel('twitch', raw))
        self.add_user('creator-editor', 'editor')
        self.login('creator-editor')
        token = self.csrf('/admin/content/streamers/new')
        data = {'_csrf': token, 'name': 'Sky Builder', 'slug': 'sky-builder', 'platform': 'twitch',
                'channel': 'https://www.twitch.tv/SkyBuilder', 'status': 'published'}
        response = self.client.post('/admin/content/streamers/new', data=data)
        self.assertEqual(response.status_code, 302)
        conn = self.db()
        row = conn.execute('SELECT * FROM streamers WHERE slug = ?', ('sky-builder',)).fetchone()
        self.assertEqual(row['channel'], 'skybuilder')
        creator_id = row['id']
        conn.close()
        self.assertIn('Sky Builder', self.client.get('/community/streamers').get_data(as_text=True))
        data.update(slug='duplicate-creator')
        duplicate = self.client.post('/admin/content/streamers/new', data=data)
        self.assertIn(b'already in Featured streamers', duplicate.data)
        data.update(slug='sky-builder', channel='https://evil.example/abc')
        invalid = self.client.post('/admin/content/streamers/%s' % creator_id, data=data)
        self.assertIn(b'Enter a valid channel', invalid.data)
        data.update(channel='skybuilder', status='archived')
        self.assertEqual(self.client.post('/admin/content/streamers/%s' % creator_id, data=data).status_code, 302)
        self.assertNotIn('Sky Builder', self.client.get('/community/streamers').get_data(as_text=True))
        self.add_user('writer', 'writer')
        self.login('writer')
        self.assertEqual(self.client.get('/admin/content/streamers/new').status_code, 403)


if __name__ == '__main__':
    unittest.main()
