"""Render Minecraft heads from Mojang profiles and textures; no third-party avatar cache."""
from collections import OrderedDict
import base64
import io
import json
import re
import struct
import threading
import time
import urllib.error
import urllib.request

from flask import Blueprint, Response, abort, current_app, send_file

from .gameapi import NAME_RE

try:
    from PIL import Image
except ImportError:  # The rest of the site can still run without the image library.
    Image = None

bp = Blueprint("minecraft", __name__)
_cache = OrderedDict()
_names = OrderedDict()
_lock = threading.Lock()
_fetch_slots = threading.BoundedSemaphore(8)
_name_locks = [threading.Lock() for _ in range(32)]
_MAX_BYTES = 128 * 1024
_MAX_JSON = 32 * 1024
_MAX_CACHE = 512
_TTL = 15 * 60
_NAME_TTL = 60 * 60
_BROWSER_TTL = 5 * 60
_FAILURE_TTL = 60
_STALE_TTL = 24 * 60 * 60
_UUID_RE = re.compile(r"[a-fA-F0-9]{32}")
_TEXTURE_RE = re.compile(r"https?://textures\.minecraft\.net/texture/([a-fA-F0-9]{32,64})")


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def _download(url, accept, limit):
    request = urllib.request.Request(url, headers={
        "Accept": accept, "User-Agent": "AerborneWebsite/1.0"})
    with urllib.request.build_opener(_NoRedirect()).open(request, timeout=3) as response:
        if response.status != 200:
            raise ValueError("No public texture data")
        data = response.read(limit + 1)
        mime = response.headers.get_content_type()
    if len(data) > limit or mime != accept:
        raise ValueError("Unexpected upstream response")
    return data


def _json(url):
    data = json.loads(_download(url, "application/json", _MAX_JSON).decode("utf-8"))
    if not isinstance(data, dict):
        raise ValueError("Expected a profile object")
    return data


def _remember(cache, key, value):
    # Caller holds _lock. Both caches are bounded, including unsuccessful lookups.
    cache[key] = value
    cache.move_to_end(key)
    while len(cache) > _MAX_CACHE:
        cache.popitem(last=False)


def _resolve(name):
    now = time.monotonic()
    with _lock:
        hit = _names.get(name)
        if hit and hit[0] > now:
            _names.move_to_end(name)
            return hit[1]
    profile = _json("https://api.mojang.com/users/profiles/minecraft/" + name)
    uuid = profile.get("id", "")
    if (not isinstance(uuid, str) or not _UUID_RE.fullmatch(uuid)
            or str(profile.get("name", "")).lower() != name):
        raise ValueError("Profile does not match the player")
    uuid = uuid.lower()
    with _lock:
        _remember(_names, name, (time.monotonic() + _NAME_TTL, uuid))
    return uuid


def _render(skin):
    # Check the tiny allowed skin dimensions before the image decoder allocates pixels.
    if (len(skin) < 33 or len(skin) > _MAX_BYTES
            or skin[:8] != b"\x89PNG\r\n\x1a\n" or skin[12:16] != b"IHDR"
            or struct.unpack(">II", skin[16:24]) not in ((64, 64), (64, 32))):
        raise ValueError("Not a standard Minecraft skin")
    with Image.open(io.BytesIO(skin)) as source:
        if source.format != "PNG" or source.size not in ((64, 64), (64, 32)):
            raise ValueError("Not a standard Minecraft skin")
        source.load()
        pixels = source.convert("RGBA")
    face = pixels.crop((8, 8, 16, 16))
    face.putalpha(255)  # Minecraft's base head layer is opaque.
    hat = pixels.crop((40, 8, 48, 16))
    # Old 64x32 exports often filled the unused hat area with opaque pixels.
    # Minecraft ignores that layer when the entire legacy overlay region is opaque.
    legacy_opaque = pixels.height == 32 and pixels.crop((32, 0, 64, 32)).getchannel("A").getextrema()[0] == 255
    if not legacy_opaque:
        face = Image.alpha_composite(face, hat)
    nearest = Image.Resampling.NEAREST if hasattr(Image, "Resampling") else Image.NEAREST
    output = io.BytesIO()
    face.resize((128, 128), resample=nearest).save(output, format="PNG")
    return output.getvalue()


def _fetch(name):
    if Image is None:
        return None
    try:
        uuid = _resolve(name)
        profile = _json("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid)
        if profile.get("id") != uuid:
            raise ValueError("Session profile does not match")
        properties = profile.get("properties") or []
        if not isinstance(properties, list):
            raise ValueError("Invalid profile properties")
        for prop in properties:
            if not isinstance(prop, dict) or prop.get("name") != "textures":
                continue
            encoded = prop.get("value", "")
            if not isinstance(encoded, str) or len(encoded) > _MAX_JSON:
                raise ValueError("Invalid textures property")
            texture = json.loads(base64.b64decode(encoded, validate=True).decode("utf-8"))
            if not isinstance(texture, dict) or texture.get("profileId") != uuid:
                raise ValueError("Texture profile does not match")
            textures = texture.get("textures")
            skin = textures.get("SKIN") if isinstance(textures, dict) else None
            # Accounts without a published custom skin use our local fallback.
            if not isinstance(skin, dict) or not isinstance(skin.get("url"), str):
                return None
            match = _TEXTURE_RE.fullmatch(skin["url"])
            if not match:
                raise ValueError("Unexpected texture host or path")
            # The session API can return HTTP links. Fetch only HTTPS on the fixed host.
            url = "https://textures.minecraft.net/texture/" + match.group(1).lower()
            return _render(_download(url, "image/png", _MAX_BYTES))
    except (urllib.error.URLError, OSError, ValueError, TypeError):
        pass
    return None


def _head(name):
    # A stripe also coalesces concurrent podium/table requests for the same player.
    with _name_locks[hash(name) % len(_name_locks)]:
        now = time.monotonic()
        with _lock:
            hit = _cache.get(name)
            if hit and hit[0] > now:
                _cache.move_to_end(name)
                return hit[1], hit[3]
        if not _fetch_slots.acquire(timeout=2):
            if hit and hit[1] and hit[2] is not None and now - hit[2] < _STALE_TTL:
                return hit[1], _FAILURE_TTL
            return None, _FAILURE_TTL
        try:
            data = _fetch(name)
        finally:
            _fetch_slots.release()
        now = time.monotonic()
        last_success = now if data else None
        keep = _TTL if data else _FAILURE_TTL
        browser_ttl = _BROWSER_TTL if data else _FAILURE_TTL
        if not data and hit and hit[1] and hit[2] is not None and now - hit[2] < _STALE_TTL:
            data, last_success = hit[1], hit[2]
        with _lock:
            _remember(_cache, name, (now + keep, data, last_success, browser_ttl))
        return data, browser_ttl


@bp.route("/minecraft/head/<name>.png")
def head(name):
    if not NAME_RE.fullmatch(name):
        abort(404)
    data, browser_ttl = _head(name.lower())
    if data:
        response = Response(data, mimetype="image/png")
        response.cache_control.public = True
        response.cache_control.max_age = browser_ttl
    else:
        response = send_file(current_app.static_folder + "/img/brand/skycrest-192.webp",
                             mimetype="image/webp", max_age=_FAILURE_TTL)
        response.cache_control.public = True
    return response
