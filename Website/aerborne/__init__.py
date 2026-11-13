"""Aerborne website. Python 3.6+, Flask 2.0, SQLite."""
import datetime
import email.utils
import logging
import mimetypes
import os

from flask import Flask, g, render_template, request, session, url_for
from werkzeug.exceptions import BadRequest

from . import db
from .auth import load_user
from .config import display_address, display_bedrock, load_config
from .content import image_url
from .content_types import TYPES
from .markup import plain, render
from .security import apply_headers, csrf_token
from . import seed_updates
from .seed import ensure_default_settings, seed_default_admin, seed_forum, seed_if_empty

# Top navigation. A group opens a small menu; the first link in a group is its overview page.
NAV = [
    {"label": "Play", "id": "play", "links": [
        ("public.play", "Join the server", "The address, and three easy steps"),
        ("public.client", "Aerborne client", "The optional mod: HUD, menus and maps"),
        ("public.status_page", "Server status", "Who's online and how it's running"),
    ]},
    {"label": "The World", "id": "world", "links": [
        ("public.reach", "The Wardens' Reach", "Interactive map of the central world"),
        ("public.explore", "Your island and beyond", "Islands, visitors, the Farmstead and more"),
        ("public.expeditions", "Expeditions", "Party adventures open right now"),
    ]},
    {"label": "Live", "id": "live", "links": [
        ("public.market", "The Bazaar", "Live prices for bulk goods"),
        ("public.calendar", "Reach Calendar", "Weekends, contests, festivals and the world boss"),
        ("public.council", "Wardens' Council", "Who holds the seat, and the monthly vote"),
        ("public.campaign", "Season campaign", "Server-wide goals and rewards"),
        ("community.leaderboards", "Leaderboards", "Top adventurers and islands"),
    ]},
    {"label": "Handbook", "id": "guide", "links": [
        ("public.guide", "Adventurer's Handbook", "Guides from your first island onwards"),
        ("public.codex", "Arcane Codex", "Every public enchantment and Aspect"),
        ("public.companions", "Companions", "Meet the companion characters"),
    ]},
    {"label": "Community", "id": "community", "links": [
        ("community.forum", "Forum", "Talk with other players"),
        ("community.members", "Members", "Everyone in the community"),
        ("community.leaderboards", "Leaderboards", "Top adventurers and islands"),
        ("public.news", "News", "Updates, patch notes and events"),
        ("public.community", "Featured builds", "Islands picked by staff"),
        ("public.streamers", "Featured streamers", "Watch creators from the community"),
    ]},
    {"label": "Arcade", "endpoint": "arcade.index"},
    {"label": "Store", "endpoint": "public.store"},
]


def create_app(overrides=None):
    # Older host MIME databases (including Windows) may not recognise WebP assets.
    mimetypes.add_type('image/webp', '.webp')
    mimetypes.add_type('application/java-archive', '.jar')
    app = Flask(__name__)
    app.config.from_mapping(load_config())
    if overrides:
        app.config.update(overrides)
    app.config["JSON_SORT_KEYS"] = False

    os.makedirs(app.config["DATA_DIR"], exist_ok=True)
    os.makedirs(app.config["UPLOAD_DIR"], exist_ok=True)
    conn = db.connect(app.config["DATABASE"])
    try:
        db.migrate(conn, TYPES)
        seed_if_empty(conn)
        seed_forum(conn)
        ensure_default_settings(conn)
        seed_updates.apply(conn)
        db.purge_security_records(conn)
        seed_default_admin(conn)
    finally:
        conn.close()

    if not app.debug:
        logging.basicConfig(level=logging.INFO)

    from .admin import bp as admin_bp
    from . import admin_forum  # noqa: F401  (adds forum routes to the admin blueprint)
    from .api import bp as api_bp
    from .arcade import bp as arcade_bp
    from .community import bp as community_bp, load_member
    from . import social  # noqa: F401  (adds friends, messages and notifications to the community blueprint)
    from . import members  # noqa: F401  (adds the members directory and data export)
    from .public import bp as public_bp
    from .social_sync import bp as social_sync_bp
    from .shop import bp as shop_bp
    from .minecraft import bp as minecraft_bp
    from . import applications  # noqa: F401 (adds public application and private admin review routes)
    from . import creators  # noqa: F401 (creator program and authenticated payment events)

    app.register_blueprint(public_bp)
    app.register_blueprint(community_bp)
    app.register_blueprint(arcade_bp)
    app.register_blueprint(api_bp)
    app.register_blueprint(admin_bp)
    app.register_blueprint(social_sync_bp)
    app.register_blueprint(shop_bp)
    app.register_blueprint(minecraft_bp)

    app.teardown_appcontext(db.close_db)
    app.after_request(apply_headers)

    @app.before_request
    def _load_user():
        g.user = None
        g.member = None
        g.staff_view = False
        if request.blueprint in ("admin", "public", "community", "arcade", "shop"):
            load_user()
            db.purge_security_records(db.get_db(), force=False)
        if request.blueprint in ("public", "community", "arcade", "shop"):
            load_member()
            # Staff see the site with tools ("admin view") unless they switch to the player view.
            g.staff_view = bool(g.user) and session.get("view_as", "admin") == "admin"
            g.player_view = bool(g.user) and not g.staff_view

    @app.context_processor
    def _inject():
        from . import tebex
        from .creators import can_dashboard
        settings = {}
        try:
            settings = db.get_settings()
        except Exception:  # a broken database should still render error pages
            app.logger.exception("Could not read settings")
        return {
            "site": app.config,
            "store_connection": tebex.catalog() if request.path.startswith('/store') else {},
            "store_cart_count": len(session.get('store_cart', [])),
            "server_address": display_address(app.config),
            "bedrock": display_bedrock(app.config),
            "nav": [item for item in NAV if item.get("endpoint") != "arcade.index" or settings.get("arcade_enabled", "1") == "1"],
            "settings": settings,
            "csrf_token": csrf_token,
            "image_url": image_url,
            "year": datetime.date.today().year,
            "current_user": g.get("user"),
            "member": g.get("member"),
            "staff": g.get("user") if request.blueprint in ("public", "community", "arcade") else None,
            "staff_view": g.get("staff_view", False),
            "edit_link": _edit_link(),
            "discord_url": (settings.get("discord_invite") or app.config.get("DISCORD_URL") or ""),
            "can_creator_dashboard": can_dashboard(),
        }

    app.jinja_env.filters["markdown"] = render
    app.jinja_env.filters["plain"] = plain
    app.jinja_env.filters["date"] = _format_date
    app.jinja_env.filters["isodate"] = _iso_date
    app.jinja_env.filters["rfc822"] = _rfc822

    for code in (400, 403, 404, 500):
        app.register_error_handler(code, _error_page(code))

    return app


# Where the staff bar's edit button goes for list pages. Item pages set g.edit_target themselves.
LIST_EDIT = {
    "public.index": ("news", "Edit news"),
    "public.explore": ("regions", "Edit regions"),
    "public.guide": ("guides", "Edit guides"),
    "public.guide_section": ("guides", "Edit guides"),
    "public.codex": ("codex", "Edit codex entries"),
    "public.companions": ("companions", "Edit companions"),
    "public.news": ("news", "Edit news"),
    "public.store": ("products", "Edit store items"),
    "public.store_category": ("products", "Edit store items"),
    "public.community": ("showcases", "Edit community builds"),
    "public.streamers": ("streamers", "Manage featured streamers"),
    "public.support": ("faqs", "Edit FAQs"),
}


def _edit_link():
    """(url, label) for the staff bar, or None. Only in admin view, and only for types this role can edit."""
    from .auth import can_edit_type
    from .content_types import get_type
    user = g.get("user")
    if not g.get("staff_view") or user is None or user["must_change_password"]:
        return None
    target = g.get("edit_target")
    if target:
        key, item_id = target
        ctype = get_type(key)
        if ctype and can_edit_type(user, ctype):
            return url_for("admin.content_edit", key=key, item_id=item_id), "Edit this " + ctype.singular
        return None
    listed = LIST_EDIT.get(request.endpoint)
    if listed and can_edit_type(user, get_type(listed[0])):
        return url_for("admin.content_list", key=listed[0]), listed[1]
    if request.blueprint == "community" and user["role"] in ("admin", "moderator"):
        return url_for("admin.forum_reports"), "Forum reports"
    return None


def _format_date(value, with_time=False):
    parsed = db.parse_time(value) if isinstance(value, str) else value
    if not parsed:
        return ""
    text = "{} {} {}".format(parsed.day, parsed.strftime("%b"), parsed.year)
    if with_time:
        text += parsed.strftime(", %H:%M UTC")
    return text


def _iso_date(value):
    parsed = db.parse_time(value) if isinstance(value, str) else value
    return parsed.strftime("%Y-%m-%dT%H:%M:%SZ") if parsed else ""


def _rfc822(value):
    parsed = db.parse_time(value) if isinstance(value, str) else value
    if not parsed:
        return ""
    return email.utils.format_datetime(parsed.replace(tzinfo=datetime.timezone.utc))


ERROR_COPY = {
    400: ("That request didn't work", "Something in the request was missing or out of date. Go back, reload and try again."),
    403: ("You can't open this page", "Your account doesn't have access to this. Ask an admin if you need it."),
    404: ("This page fell into the void", "There's nothing at this address. Head back home and pick a page from the menu."),
    500: ("Something broke on our side", "The page hit an error. Try again in a minute; if it keeps happening, tell staff on Discord."),
}


def _error_page(code):
    def handler(error):
        title, body = ERROR_COPY[code]
        # Only show 400 text we wrote ourselves (for example the expired-form message).
        detail = getattr(error, "description", "") if code == 400 else ""
        if detail == BadRequest.description:
            detail = ""
        return render_template("error.html", code=code, title=title, body=detail or body), code
    return handler
