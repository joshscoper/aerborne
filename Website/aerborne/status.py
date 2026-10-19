"""Live server status using Minecraft's Server List Ping.

Standard library only. Results are cached per process so page views never hammer the server,
and every answer carries the time it was checked so the site never passes off old data as live.
"""
import json
import socket
import struct
import threading
import time

_cache = {}
_last_success = {}
_lock = threading.Lock()

_FAILURE_CACHE_SECONDS = 15
_MAX_RESPONSE_BYTES = 1024 * 1024


class PingError(Exception):
    pass


def _pack_varint(value):
    value &= 0xFFFFFFFF
    out = bytearray()
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            return bytes(out)


def _recv_exact(sock, length):
    data = bytearray()
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            raise PingError("connection closed early")
        data.extend(chunk)
    return bytes(data)


def _read_varint(sock):
    result = 0
    for shift in range(0, 35, 7):
        byte = _recv_exact(sock, 1)[0]
        result |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return result
    raise PingError("varint too long")


def _packet(packet_id, payload=b""):
    body = _pack_varint(packet_id) + payload
    return _pack_varint(len(body)) + body


def ping(host, port, timeout=3):
    """Return the raw status JSON and round-trip time in milliseconds."""
    host_bytes = host.encode("utf-8")
    handshake = (
        _pack_varint(-1)  # protocol version "any", the convention for status pings
        + _pack_varint(len(host_bytes))
        + host_bytes
        + struct.pack(">H", port)
        + _pack_varint(1)  # next state: status
    )
    started = time.time()
    sock = socket.create_connection((host, port), timeout=timeout)
    try:
        sock.settimeout(timeout)
        sock.sendall(_packet(0x00, handshake) + _packet(0x00))
        length = _read_varint(sock)
        if length <= 0 or length > _MAX_RESPONSE_BYTES:
            raise PingError("unexpected response length")
        if _read_varint(sock) != 0x00:
            raise PingError("unexpected packet id")
        text_length = _read_varint(sock)
        if text_length > _MAX_RESPONSE_BYTES:
            raise PingError("status text too long")
        raw = _recv_exact(sock, text_length)
    finally:
        sock.close()
    latency = int((time.time() - started) * 1000)
    try:
        return json.loads(raw.decode("utf-8")), latency
    except ValueError:
        raise PingError("status was not valid JSON")


def _summarise(data, latency):
    players = data.get("players") or {}
    version = data.get("version") or {}
    favicon = data.get("favicon")
    if not isinstance(favicon, str) or not favicon.startswith("data:image/png;base64,"):
        favicon = None
    return {
        "state": "online",
        "online": True,
        "players": int(players.get("online") or 0),
        "max": int(players.get("max") or 0),
        "version": str(version.get("name") or "")[:40],
        "latency_ms": latency,
        "favicon": favicon,
    }


def get_status(target, timeout=3, cache_seconds=30):
    """Cached status for (host, port). Never raises; offline is a normal answer."""
    if target is None:
        return {"state": "unconfigured", "online": False, "configured": False}

    now = time.time()
    with _lock:
        cached = _cache.get(target)
        if cached and cached["expires"] > now:
            return dict(cached["value"])

    host, port = target
    try:
        data, latency = ping(host, port, timeout)
        value = _summarise(data, latency)
        ttl = cache_seconds
    except (OSError, PingError, ValueError, TypeError, AttributeError):
        value = {"state": "offline", "online": False}
        ttl = min(cache_seconds, _FAILURE_CACHE_SECONDS)

    value["configured"] = True
    value["checked_at"] = int(now)
    with _lock:
        if value["online"]:
            _last_success[target] = int(now)
        value["last_online_at"] = _last_success.get(target)
        _cache[target] = {"value": value, "expires": now + ttl}
    return dict(value)


def clear_cache():
    with _lock:
        _cache.clear()
        _last_success.clear()
