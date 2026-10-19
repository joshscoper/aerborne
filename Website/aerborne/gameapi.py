"""Client for the plugin's read-only web API (api.aerborne.net).

Public reads (leaderboards, player profiles) are cached briefly so page views never hammer the
game server. Link-code lookups use the private bearer token, which stays on the server side and
is never sent to browsers. Every call returns (data, problem): problem is None, "not_found" or
"unavailable", and pages must say so rather than invent numbers.
"""
import json
import re
import threading
import time
import urllib.error
import urllib.request

from flask import current_app

NAME_RE = re.compile(r"^[A-Za-z0-9_]{3,16}$")
CODE_RE = re.compile(r"^[A-HJ-NP-Z2-9]{8}$")

BOARDS = [
    ("adventurer", "Adventurer", "Adventurer XP"),
    ("skills", "Skills", "Total skill levels"),
    ("island", "Islands", "Island XP"),
    ("achievements", "Achievements", "Achievement points"),
    ("cooking", "Cooking", "Cooking XP"),
    ("marks", "Marks", "Marks"),
]
SKILLS = ["mining", "forestry", "farming", "fishing", "hunting", "excavation", "smithing", "alchemy",
          "building", "cooking", "enchanting"]
MAX_SKILL_LEVEL = 50

_cache = {}
_lock = threading.Lock()
_FAILURE_SECONDS = 15
_MAX_CACHE = 500
_MAX_BYTES = 64 * 1024


def _request(path, token=None):
    base = current_app.config.get("API_URL", "").rstrip("/")
    if not base:
        return None, "unavailable"
    req = urllib.request.Request(base + path, headers={
        "Accept": "application/json",
        "User-Agent": "AerborneWebsite/1.0",
    })
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=current_app.config.get("API_TIMEOUT", 4)) as resp:
            raw = resp.read(_MAX_BYTES + 1)
        if len(raw) > _MAX_BYTES:
            return None, "unavailable"
        data = json.loads(raw.decode("utf-8"))
        if not isinstance(data, dict):
            return None, "unavailable"
        return data, None
    except urllib.error.HTTPError as exc:
        return None, "not_found" if exc.code == 404 else "unavailable"
    except (urllib.error.URLError, OSError, ValueError):
        return None, "unavailable"


def _cached(key, path, ttl):
    now = time.time()
    with _lock:
        hit = _cache.get(key)
        if hit and hit[0] > now:
            return hit[1], hit[2]
    data, problem = _request(path)
    keep = ttl if problem != "unavailable" else min(ttl, _FAILURE_SECONDS)
    with _lock:
        if len(_cache) >= _MAX_CACHE:
            for stale in [k for k, v in _cache.items() if v[0] <= now] or list(_cache)[:50]:
                _cache.pop(stale, None)
        _cache[key] = (now + keep, data, problem)
    return data, problem


def leaderboards():
    data, problem = _cached("leaderboards", "/api/v1/leaderboards", 60)
    if data is not None and not data.get("ready"):
        return None, "unavailable"
    return data, problem


def player(name):
    if not NAME_RE.match(name or ""):
        return None, "not_found"
    return _cached("player:" + name.lower(), "/api/v1/players/" + name, 60)


def normalise_code(text):
    code = re.sub(r"[\s-]", "", (text or "")).upper()
    return code if CODE_RE.match(code) else None


def redeem_link_code(code):
    """Ask the game which player holds this /weblink code. Never cached."""
    token = current_app.config.get("API_TOKEN")
    if not token:
        return None, "unavailable"
    data, problem = _request("/api/v1/links/" + code, token=token)
    if data is not None and not (data.get("playerId") and NAME_RE.match(data.get("name") or "")):
        return None, "unavailable"
    return data, problem


def clear_cache():
    with _lock:
        _cache.clear()
