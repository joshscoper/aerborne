"""Read-only, versioned JSON for the site's own scripts and community tools.

Only published content is returned, and hidden codex recipes stay hidden here too.
"""
from flask import Blueprint, current_app, jsonify, url_for

import time

from . import gameapi, status
from .config import status_target, status_uses_srv
from .content import image_url, published
from .db import get_setting

bp = Blueprint("api", __name__, url_prefix="/api/v1")


@bp.after_request
def _cors(response):
    response.headers["Access-Control-Allow-Origin"] = "*"
    return response


def _api_status():
    """Server status from the game API over HTTPS, in the same shape as a status ping.

    Many web hosts block outgoing connections to the Minecraft port, but HTTPS to the game API gets through,
    so the API is asked first. Returns None when the API isn't configured or can't be reached.
    """
    if not current_app.config.get("API_URL"):
        return None
    data, _problem = gameapi.status()
    if not data or not data.get("ready"):
        return None
    now = int(time.time())
    return {"state": "online", "online": True, "configured": True, "source": "api",
            "players": int(data.get("onlinePlayers") or 0), "max": int(data.get("maxPlayers") or 0),
            "version": str(data.get("serverVersion") or "")[:40], "latency_ms": None, "favicon": None,
            "checked_at": now, "last_online_at": now}


def current_status():
    cfg = current_app.config
    # Ask the game API first; ping the Minecraft port directly only if the API can't be reached.
    result = _api_status() or status.get_status(status_target(cfg), timeout=cfg["STATUS_TIMEOUT"],
                                                cache_seconds=cfg["STATUS_CACHE_SECONDS"], use_srv=status_uses_srv(cfg))
    if get_setting("maintenance") == "1":
        result["state"] = "maintenance"
        result["message"] = get_setting("maintenance_message")
    return result


@bp.route("/status")
def server_status():
    response = jsonify(current_status())
    response.headers["Cache-Control"] = "public, max-age=10"
    return response


@bp.route("/news")
def news():
    items = [{
        "title": row["title"],
        "slug": row["slug"],
        "category": row["category"],
        "summary": row["summary"],
        "author": row["author"],
        "version": row["version_tag"],
        "published_at": row["publish_at"] or row["created_at"],
        "url": url_for("public.news_article", slug=row["slug"], _external=True),
        "image": image_url(row["image"]) or None,
    } for row in published("news", limit=20)]
    return jsonify({"items": items})


@bp.route("/codex")
def codex():
    items = []
    for row in published("codex"):
        public = bool(row["recipe_public"])
        items.append({
            "name": row["name"],
            "slug": row["slug"],
            "tier": row["tier"],
            "aspects": [a for a in (row["aspects"] or "").split(",") if a] if public else None,
            "equipment": row["equipment"],
            "max_level": row["max_level"],
            "level_required": row["level_required"],
            "effect": row["summary"],
            "availability": row["availability"],
        })
    return jsonify({"items": items})


@bp.route("/regions")
def regions():
    return jsonify({"items": [{
        "name": row["name"],
        "slug": row["slug"],
        "theme": row["theme"],
        "summary": row["summary"],
        "availability": row["availability"],
        "url": url_for("public.region", slug=row["slug"], _external=True),
    } for row in published("regions")]})
