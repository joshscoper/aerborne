"""Public stream checks through official APIs. Credentials never leave the server.

SQLite caches are shared by Passenger workers. Expired live results are never presented as live
when a provider fails. Requests have fixed hosts, bounded bodies and short timeouts.
"""
import hashlib
import html
import json
import math
import re
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed

PLATFORMS = {'twitch': 'Twitch', 'youtube': 'YouTube', 'kick': 'Kick'}
REQUIRED = {'twitch': ('TWITCH_CLIENT_ID', 'TWITCH_CLIENT_SECRET'),
            'youtube': ('YOUTUBE_API_KEY',), 'kick': ('KICK_CLIENT_ID', 'KICK_CLIENT_SECRET')}
_tokens = {}
_token_lock = threading.RLock()


class ProviderError(Exception):
    def __init__(self, code=0):
        self.code = code


def normalize_channel(platform, raw):
    value = (raw or '').strip()
    if '://' in value:
        try:
            parsed = urllib.parse.urlsplit(value)
            domains = {'twitch': ('twitch.tv', 'www.twitch.tv'),
                       'kick': ('kick.com', 'www.kick.com'),
                       'youtube': ('youtube.com', 'www.youtube.com')}
            if parsed.scheme != 'https' or parsed.netloc.lower() not in domains.get(platform, ()):
                return None
            value = urllib.parse.unquote(parsed.path).strip('/')
            if platform == 'youtube' and value.startswith('channel/'):
                value = value[8:]
        except ValueError:
            return None
    if platform == 'twitch' and re.fullmatch(r'[a-zA-Z0-9_]{1,25}', value):
        return value.lower()
    if platform == 'kick' and re.fullmatch(r'[a-zA-Z0-9_-]{1,25}', value):
        return value.lower()
    if platform == 'youtube' and (re.fullmatch(r'UC[a-zA-Z0-9_-]{22}', value) or
                                  re.fullmatch(r'@[^\s/?#&]{3,30}', value)):
        return value
    return None


def channel_url(platform, channel):
    channel = normalize_channel(platform, channel)
    if not channel:
        return ''
    if platform == 'youtube':
        return 'https://www.youtube.com/' + ('' if channel.startswith('@') else 'channel/') + urllib.parse.quote(channel, safe='@')
    return 'https://%s/%s' % ('www.twitch.tv' if platform == 'twitch' else 'kick.com', channel)


def configured(platform, config):
    return platform in REQUIRED and all(config.get(key) for key in REQUIRED[platform])


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def _json_request(url, config, headers=None, form=None):
    data = urllib.parse.urlencode(form).encode('utf-8') if form is not None else None
    request_headers = {'Accept': 'application/json', 'User-Agent': 'AerborneWebsite/1.0'}
    request_headers.update(headers or {})
    if data is not None:
        request_headers['Content-Type'] = 'application/x-www-form-urlencoded'
    req = urllib.request.Request(url, data=data, headers=request_headers)
    try:
        with urllib.request.build_opener(NoRedirect()).open(req, timeout=config.get('STREAM_TIMEOUT', 4)) as response:
            raw = response.read(512 * 1024 + 1)
        if len(raw) > 512 * 1024:
            raise ProviderError()
        result = json.loads(raw.decode('utf-8'))
        if not isinstance(result, dict):
            raise ProviderError()
        return result
    except urllib.error.HTTPError as exc:
        raise ProviderError(exc.code) from None
    except (urllib.error.URLError, OSError, ValueError):
        raise ProviderError() from None


def _token_key(platform, config):
    credentials = [config.get(key, '') for key in REQUIRED[platform]]
    return platform + ':' + hashlib.sha256(json.dumps(credentials).encode()).hexdigest()


def _token(platform, config, renew=False):
    key = _token_key(platform, config)
    with _token_lock:
        cached = _tokens.get(key)
        if cached and cached[0] > time.time() and not renew:
            return cached[1]
        prefix = platform.upper()
        url = 'https://id.twitch.tv/oauth2/token' if platform == 'twitch' else 'https://id.kick.com/oauth/token'
        result = _json_request(url, config, form={'client_id': config[prefix + '_CLIENT_ID'],
                               'client_secret': config[prefix + '_CLIENT_SECRET'], 'grant_type': 'client_credentials'})
        token = result.get('access_token')
        if not isinstance(token, str) or not token:
            raise ProviderError()
        lifetime = max(1, int(result.get('expires_in', 3600)) - 60)
        if len(_tokens) > 20:
            _tokens.clear()
        _tokens[key] = (time.time() + lifetime, token)
        return token


def _oauth_get(platform, url, config):
    for attempt in range(2):
        token = _token(platform, config, renew=bool(attempt))
        headers = {'Authorization': 'Bearer ' + token}
        if platform == 'twitch':
            headers['Client-Id'] = config['TWITCH_CLIENT_ID']
        try:
            return _json_request(url, config, headers=headers)
        except ProviderError as exc:
            if exc.code != 401 or attempt:
                raise


def _items(result, key):
    items = result.get(key)
    if not isinstance(items, list) or not all(isinstance(item, dict) for item in items):
        raise ProviderError()
    return items


def _number(value):
    return max(0, int(value)) if value is not None else None


def _youtube(channel, config, previous):
    key = config['YOUTUBE_API_KEY']
    base = 'https://www.googleapis.com/youtube/v3/'
    if previous.get('video_id') and previous.get('state') == 'live':
        result = _json_request(base + 'videos?' + urllib.parse.urlencode({
            'part': 'snippet,liveStreamingDetails', 'id': previous['video_id'], 'key': key}), config)
        items = _items(result, 'items')
        if not items:
            return {'state': 'offline'}
        video = items[0]
        details = video.get('liveStreamingDetails', {})
        if video.get('snippet', {}).get('liveBroadcastContent') != 'live' or details.get('actualEndTime'):
            return {'state': 'offline'}
        return {'state': 'live', 'title': html.unescape(video['snippet'].get('title', '')),
                'video_id': previous['video_id'], 'viewers': _number(details.get('concurrentViewers'))}
    if channel.startswith('@'):
        result = _json_request(base + 'channels?' + urllib.parse.urlencode({
            'part': 'id', 'forHandle': channel, 'key': key}), config)
        channels = _items(result, 'items')
        if not channels:
            raise ProviderError()
        channel = channels[0]['id']
    result = _json_request(base + 'search?' + urllib.parse.urlencode({
        'part': 'snippet', 'channelId': channel, 'eventType': 'live', 'type': 'video', 'maxResults': 1, 'key': key}), config)
    items = _items(result, 'items')
    if not items:
        return {'state': 'offline'}
    video = items[0]
    video_id = video.get('id', {}).get('videoId', '')
    if not re.fullmatch(r'[a-zA-Z0-9_-]{11}', video_id):
        raise ProviderError()
    return {'state': 'live', 'title': html.unescape(video.get('snippet', {}).get('title', '')), 'video_id': video_id}


def _probe(item, config, previous):
    platform, channel = item['platform'], item['channel']
    if not normalize_channel(platform, channel):
        return {'state': 'unavailable', 'checked_at': None}
    if not configured(platform, config):
        return {'state': 'unconfigured', 'checked_at': None}
    try:
        if platform == 'twitch':
            result = _oauth_get(platform, 'https://api.twitch.tv/helix/streams?' + urllib.parse.urlencode({'user_login': channel}), config)
            items = _items(result, 'data')
            status = {'state': 'offline'}
            if items:
                live = items[0]
                if live.get('type') != 'live':
                    raise ProviderError()
                status = {'state': 'live', 'title': live.get('title', ''), 'viewers': _number(live.get('viewer_count'))}
        elif platform == 'kick':
            result = _oauth_get(platform, 'https://api.kick.com/public/v1/channels?' + urllib.parse.urlencode({'slug': channel}), config)
            items = _items(result, 'data')
            if not items or not isinstance(items[0].get('stream'), dict) or not isinstance(items[0]['stream'].get('is_live'), bool):
                raise ProviderError()
            live = items[0]
            status = {'state': 'offline'}
            if live['stream']['is_live']:
                status = {'state': 'live', 'title': live.get('stream_title', ''), 'viewers': _number(live['stream'].get('viewer_count'))}
        else:
            status = _youtube(channel, config, previous)
        status['title'] = str(status.get('title') or '')[:300]
        status['checked_at'] = int(time.time())
        return status
    except (ProviderError, ValueError, TypeError, KeyError, AttributeError):
        return {'state': 'unavailable', 'checked_at': None}


def _cache_key(item, config):
    credentials = [config.get(key, '') for key in REQUIRED.get(item['platform'], ())]
    return hashlib.sha256(json.dumps([item['platform'], item['channel'], credentials]).encode()).hexdigest()


def cached_status(item, config, conn):
    if not configured(item['platform'], config):
        return {'state': 'unconfigured', 'checked_at': None}
    row = conn.execute('SELECT payload, expires_at FROM stream_status_cache WHERE cache_key = ?', (_cache_key(item, config),)).fetchone()
    if row and row['expires_at'] > time.time():
        return json.loads(row['payload'])
    return {'state': 'checking', 'checked_at': None}


def get_statuses(items, config, conn):
    """Claim expired checks atomically, then perform network IO outside the database lock."""
    statuses, pending = {}, []
    now = int(time.time())
    conn.execute('BEGIN IMMEDIATE')
    try:
        conn.execute('DELETE FROM stream_status_cache WHERE expires_at < ?', (now - 86400 * 7,))
        for item in items:
            key = _cache_key(item, config)
            row = conn.execute('SELECT payload, expires_at FROM stream_status_cache WHERE cache_key = ?', (key,)).fetchone()
            if row and row['expires_at'] > now:
                statuses[item['slug']] = json.loads(row['payload'])
                continue
            previous = json.loads(row['payload']) if row else {}
            placeholder = {'state': 'checking', 'checked_at': None}
            conn.execute('INSERT OR REPLACE INTO stream_status_cache (cache_key, payload, expires_at) VALUES (?, ?, ?)',
                         (key, json.dumps(placeholder), now + max(60, len(items) * 10)))
            pending.append((item, key, previous))
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    youtube_count = sum(item['platform'] == 'youtube' for item in items)
    discovery = max(config.get('YOUTUBE_DISCOVERY_SECONDS', 1800),
                    int(math.ceil(86400 * youtube_count / max(1, config.get('YOUTUBE_SEARCH_BUDGET', 80)))))
    if pending:
        with ThreadPoolExecutor(max_workers=4) as executor:
            jobs = {executor.submit(_probe, item, config, previous): (item, key) for item, key, previous in pending}
            for future in as_completed(jobs):
                item, key = jobs[future]
                status = future.result()
                statuses[item['slug']] = status
                ttl = config.get('STREAM_CACHE_SECONDS', 60)
                if status['state'] in ('unavailable', 'unconfigured'):
                    ttl = 300
                elif item['platform'] == 'youtube' and status['state'] == 'offline':
                    ttl = discovery
                status['watch_url'] = ('https://www.youtube.com/watch?v=' + status['video_id']
                                       if status.get('video_id') else channel_url(item['platform'], item['channel']))
                conn.execute('INSERT OR REPLACE INTO stream_status_cache (cache_key, payload, expires_at) VALUES (?, ?, ?)',
                             (key, json.dumps(status), int(time.time()) + ttl))
                conn.commit()
    return statuses
