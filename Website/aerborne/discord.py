"""Validation for public Discord destinations; no bot credentials are needed."""
import re
import json
import threading
import time
import urllib.request

from flask import current_app


def invite_url(value):
    value = (value or '').strip()
    return value if re.fullmatch(r'https://(?:discord\.gg/|discord\.com/invite/)[A-Za-z0-9-]+/?', value) else ''


def server_id(value):
    value = (value or '').strip()
    return value if re.fullmatch(r'[0-9]{17,20}', value) else ''


def widget_available(widget_id):
    """Avoid embedding Discord's error page while a widget is disabled or unreachable."""
    if not server_id(widget_id):
        return False
    cache = current_app.extensions.setdefault('discord_widget', {'lock': threading.Lock()})
    with cache['lock']:
        if cache.get('id') == widget_id and cache.get('until', 0) > time.monotonic():
            return cache['available']
        available = False
        try:
            request = urllib.request.Request('https://discord.com/api/v10/guilds/' + widget_id + '/widget.json',
                                             headers={'User-Agent': 'Aerborne-Website/1.0', 'Accept': 'application/json'})
            with urllib.request.urlopen(request, timeout=3) as response:
                data = json.loads(response.read(512000).decode('utf-8'))
            available = isinstance(data, dict) and str(data.get('id')) == widget_id
        except (OSError, ValueError):
            pass
        cache.update(id=widget_id, available=available, until=time.monotonic() + 60)
        return available
