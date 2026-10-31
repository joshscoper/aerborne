"""Public pages. Everything shown here is published content or verified configuration."""
import json
import re
import sqlite3
import hashlib
from pathlib import Path

from flask import (Blueprint, Response, abort, current_app, g, redirect, render_template, request,
                   send_from_directory, url_for, jsonify)

from . import content, gameapi, tebex
from .content import lines, published, published_one, related_links
from .content_types import ASPECTS, GUIDE_CATEGORIES, NEWS_CATEGORIES, STORE_CATEGORIES, get_type
from .db import get_db, now_str, parse_time, utcnow

bp = Blueprint("public", __name__)

GUIDE_INFO = {
    "getting-started": "Your first island and first adventure",
    "island": "Resources, machines, spawners and co-ops",
    "economy": "Shops, trades, the bank and the auction house",
    "skills": "Levelling, gathering and crafting",
    "equipment": "Gear, rarity, sockets and combat",
    "arcane": "Enchantments, Aspects and discovery",
    "world": "Island travel, the shared hub, parties and recovery",
    "troubleshooting": "Answers, fixes and help",
}

STORE_INFO = {
    "supporter-ranks": ("Supporter ranks", "Badges, name colours and keepsakes that say thanks.",
                        "img/kit/store/supporter-ranks"),
    "companions": ("Companions", "Block-built pets that follow you around the skies.",
                   "img/kit/store/companions"),
    "trails": ("Trails", "Little effects that follow your footsteps.", "img/kit/store/trails"),
    "cosmetic-bundles": ("Cosmetic bundles", "Themed sets of cosmetics, together.",
                         "img/kit/store/cosmetic-bundles"),
    "island-decor": ("Island decor", "Decorative touches for your home in the sky.",
                     "img/kit/store/island-decor"),
}

STORE_ILLUSTRATIONS = {
    'supporter-ranks': 'category-ranks',
    'companions': 'category-companions',
    'trails': 'category-trails',
    'cosmetic-bundles': 'category-bundles',
    'island-decor': 'category-island-decor',
}

ASPECT_NAMES = dict(ASPECTS)


@bp.app_template_global()
def option_label(type_key, field, value):
    ctype = get_type(type_key)
    f = ctype.field(field) if ctype else None
    return f.option_label(value) if f else value


@bp.app_template_global()
def aspect_name(slug):
    return ASPECT_NAMES.get(slug, slug)


@bp.after_app_request
def count_view(response):
    """Aggregate page counts per day and path. No IPs, cookies or user agents are stored."""
    if (request.blueprint == "public" and request.method == "GET" and response.status_code == 200
            and response.mimetype == "text/html" and g.get("user") is None):
        try:
            conn = get_db()
            day = utcnow().strftime("%Y-%m-%d")
            path = request.path[:200]
            conn.execute("INSERT OR IGNORE INTO page_views (day, path, views) VALUES (?, ?, 0)", (day, path))
            conn.execute("UPDATE page_views SET views = views + 1 WHERE day = ? AND path = ?", (day, path))
            conn.commit()
        except sqlite3.Error:
            current_app.logger.warning("Could not record a page view", exc_info=True)
    return response


# --- Home and play ---------------------------------------------------------------------------

@bp.route("/")
def index():
    news = published("news", order_by="featured DESC, COALESCE(publish_at, created_at) DESC", limit=3)
    return render_template(
        "index.html",
        news=news,
        showcases=published("showcases", limit=3),
    )


@bp.route("/play")
def play():
    faqs = published("faqs", "category = ? AND slug NOT IN ('whitelist', 'which-version', 'bedrock')", ("connecting",))
    return render_template("play.html", faqs=faqs)


@bp.route("/status")
def status_page():
    return render_template("status.html")


# --- Explore ---------------------------------------------------------------------------------

@bp.route("/explore")
def explore():
    return render_template("explore.html", regions=published("regions"),
                           world_guides=published("guides", "category = ?", ("world",)))


@bp.route("/explore/<slug>")
def region(slug):
    item = published_one("regions", slug) or abort(404)
    return render_region(item)


def render_region(item, preview=False):
    g.edit_target = ("regions", item["id"])
    return render_template("region.html", item=item, preview=preview,
                           activities=lines(item["activities"]),
                           places=lines(item["points_of_interest"]),
                           others=published("regions", "id != ?", (item["id"],), limit=3))


# --- Expeditions and the season campaign -------------------------------------------------------

def _names(values):
    return [str(v) for v in (values or []) if gameapi.NAME_RE.match(str(v))][:5]


def _dungeon_map_file(folder, slug):
    """Where a dungeon's map notes live, for slugs that can only name a file in that folder."""
    return folder / (slug + ".json") if re.fullmatch(r"[a-z0-9_]+", slug or "") else None


def _dungeon_map(slug):
    """A dungeon's drawn map and what to lay over it, if it has one."""
    path = _dungeon_map_file(Path(current_app.static_folder) / "img" / "maps", slug)
    try:
        notes = json.loads(path.read_text(encoding="utf-8")) if path else None
    except (OSError, ValueError):
        return None
    return notes if isinstance(notes, dict) and notes.get("pins") and notes.get("width") else None


GEAR_SLOTS = (("head", "Head"), ("shoulders", "Shoulders"), ("chest", "Chest"), ("back", "Back"), ("wrists", "Wrists"),
              ("hands", "Hands"), ("waist", "Waist"), ("legs", "Legs"), ("feet", "Feet"), ("neck", "Neck"), ("ring", "Ring"),
              ("trinket", "Trinket"), ("main_hand", "Main hand"), ("off_hand", "Off hand"))


def _gear_list(rewards, key):
    rows = rewards.get(key) if isinstance(rewards, dict) else None
    return [row for row in rows or [] if isinstance(row, dict)]


def _gear_by_slot(rewards):
    """Every piece of a dungeon's gear by the slot it goes in, with where it comes from."""
    pieces = [dict(row, source="Set") for row in _gear_list(rewards, "setGear")]
    pieces += [dict(row, source="Found") for row in _gear_list(rewards, "finds")]
    for boss in _gear_list(rewards, "bosses"):
        pieces += [dict(row, source=boss.get("boss")) for row in boss.get("items") or [] if isinstance(row, dict)]
    return [{"title": title, "items": [row for row in pieces if row.get("slot") == slot]}
            for slot, title in GEAR_SLOTS if any(row.get("slot") == slot for row in pieces)]


@bp.route("/expeditions")
def expeditions():
    data, problem = gameapi.expeditions()
    entries = []
    for raw in (data or {}).get("expeditions") or []:
        if not isinstance(raw, dict) or not raw.get("id"):
            continue
        tiers = [dict(t, bestParty=_names(t.get("bestParty"))) for t in raw.get("tiers") or [] if isinstance(t, dict)]
        entries.append(dict(raw, tiers=tiers))
    return render_template("expeditions.html", expeditions=entries, problem=problem,
                           info=data or {}, guide=published_one("guides", "expeditions"))


@bp.route("/expeditions/<slug>")
def expedition(slug):
    data, problem = gameapi.expeditions()
    if problem:
        return render_template("expedition.html", e=None, problem=problem, slug=slug)
    for raw in (data or {}).get("expeditions") or []:
        if isinstance(raw, dict) and raw.get("id") == slug:
            tiers = [dict(t, bestParty=_names(t.get("bestParty"))) for t in raw.get("tiers") or [] if isinstance(t, dict)]
            bestiary = [m for m in raw.get("bestiary") or [] if isinstance(m, dict)]
            keystone = raw.get("keystone") if isinstance(raw.get("keystone"), dict) else None
            if keystone and isinstance(keystone.get("best"), dict):
                keystone = dict(keystone, best=dict(keystone["best"], party=_names(keystone["best"].get("party"))))
            weekly = (data.get("keystones") or {}) if isinstance(data.get("keystones"), dict) else {}
            affixes = [row for row in weekly.get("affixes") or [] if isinstance(row, dict) and row.get("modifiers")]
            return render_template("expedition.html", e=dict(raw, tiers=tiers, bestiary=bestiary, keystone=keystone), affixes=affixes,
                                   gear_slots=_gear_by_slot(raw.get("rewards")), slot_titles=dict(GEAR_SLOTS), dungeon_map=_dungeon_map(slug),
                                   problem=None, slug=slug)
    abort(404)


@bp.route("/campaign")
def campaign():
    data, problem = gameapi.campaign()
    season = data or {}
    goals = {g.get("id"): g for g in season.get("goals") or [] if isinstance(g, dict)}
    for goal in goals.values():
        goal["top"] = [r for r in goal.get("top") or [] if isinstance(r, dict) and gameapi.NAME_RE.match(str(r.get("name", "")))]
    top = [r for r in season.get("topContributors") or [] if isinstance(r, dict) and gameapi.NAME_RE.match(str(r.get("name", "")))]
    return render_template("campaign.html", season=season, goals=list(goals.values()), goal_titles={k: v.get("title", k) for k, v in goals.items()},
                           milestones=[m for m in season.get("milestones") or [] if isinstance(m, dict)],
                           buffs=[b for b in season.get("buffs") or [] if isinstance(b, dict)], top=top, problem=problem)


# --- Game guide and codex --------------------------------------------------------------------

def _guide_sections():
    everything = published("guides")
    return [{"key": key, "label": label, "blurb": GUIDE_INFO.get(key, ""),
             "guides": [g_ for g_ in everything if g_["category"] == key]} for key, label in GUIDE_CATEGORIES]


@bp.route("/guide")
def guide():
    sections = _guide_sections()
    query = request.args.get("q", "").strip()[:100]
    results = None
    if query:
        words = query.casefold().split()
        results = [entry for section in sections for entry in section["guides"]
                   if all(word in (entry["title"] + " " + entry["summary"] + " " + entry["body"]).casefold()
                          for word in words)]
    return render_template("guide.html", sections=sections, query=query, results=results,
                           guide_count=sum(len(section["guides"]) for section in sections))


@bp.route("/guide/section/<category>")
def guide_section(category):
    labels = dict(GUIDE_CATEGORIES)
    if category not in labels:
        abort(404)
    return render_template("guide_section.html", category=category, label=labels[category],
                           blurb=GUIDE_INFO.get(category, ""), sections=_guide_sections(),
                           guides=published("guides", "category = ?", (category,)))


@bp.route("/guide/<slug>")
def guide_article(slug):
    item = published_one("guides", slug) or abort(404)
    return render_guide(item)


def render_guide(item, preview=False):
    from .guide_reader import sections as guide_sections
    g.edit_target = ("guides", item["id"])
    siblings = published("guides", "category = ?", (item["category"],))
    position = next((i for i, g in enumerate(siblings) if g["id"] == item["id"]), None)
    prev_item = siblings[position - 1] if position else None
    next_item = siblings[position + 1] if position is not None and position + 1 < len(siblings) else None
    return render_template("guide_article.html", item=item, preview=preview,
                           reader_sections=guide_sections(item['body']),
                           reading_minutes=max(1, (len((item['body'] or '').split()) + 179) // 180),
                           section=dict(GUIDE_CATEGORIES).get(item["category"], ""),
                           related=related_links(item["related"]), siblings=siblings,
                           step=(position + 1) if position is not None else None,
                           prev_item=prev_item, next_item=next_item, sections=_guide_sections())


@bp.route("/codex")
def codex():
    entries = published("codex", order_by="CASE tier WHEN 'fundamental' THEN 0 WHEN 'specialized' THEN 1 ELSE 2 END, sort_order, name")
    equipment = sorted({e["equipment"] for e in entries if e["equipment"]})
    filters = {
        "tier": request.args.get("tier", ""),
        "aspect": request.args.get("aspect", ""),
        "equipment": request.args.get("equipment", ""),
        "q": request.args.get("q", "").strip()[:60],
        "availability": request.args.get("availability", ""),
    }

    def keep(entry):
        if filters["availability"] and entry["availability"] != filters["availability"]:
            return False
        if filters["tier"] and entry["tier"] != filters["tier"]:
            return False
        if filters["equipment"] and entry["equipment"] != filters["equipment"]:
            return False
        if filters["aspect"]:
            # Hidden recipes never match an Aspect filter, so filtering can't reveal them.
            if not entry["recipe_public"] or filters["aspect"] not in (entry["aspects"] or "").split(","):
                return False
        if filters["q"]:
            haystack = (entry["name"] + " " + entry["summary"]).lower()
            if filters["q"].lower() not in haystack:
                return False
        return True

    shown = [e for e in entries if keep(e)]
    return render_template("codex.html", entries=shown, total=len(entries), equipment=equipment,
                           filters=filters, aspects=published("aspects"),
                           arcane_guides=published("guides", "category = ?", ("arcane",)))


# --- Companions ------------------------------------------------------------------------------

@bp.route("/companions")
def companions():
    return render_template("companions.html", companions=published("companions"))


@bp.route("/companions/<slug>")
def companion(slug):
    item = published_one("companions", slug) or abort(404)
    return render_companion(item)


def render_companion(item, preview=False):
    g.edit_target = ("companions", item["id"])
    return render_template("companion.html", item=item, preview=preview,
                           variants=lines(item["variants"]), animations=lines(item["animations"]),
                           others=published("companions", "id != ?", (item["id"],)))


# --- News ------------------------------------------------------------------------------------

@bp.route("/news")
def news():
    category = request.args.get("category", "")
    if category and category not in dict(NEWS_CATEGORIES):
        abort(404)
    page = max(1, request.args.get("page", 1, type=int))
    per_page = 12
    where, params = ("category = ?", (category,)) if category else ("", ())
    items = published("news", where, params)
    total_pages = max(1, (len(items) + per_page - 1) // per_page)
    items = items[(page - 1) * per_page: page * per_page]
    return render_template("news.html", items=items, category=category, page=page,
                           total_pages=total_pages, categories=NEWS_CATEGORIES)


@bp.route("/news/<slug>")
def news_article(slug):
    item = published_one("news", slug) or abort(404)
    return render_news(item)


def render_news(item, preview=False):
    g.edit_target = ("news", item["id"])
    more = published("news", "id != ?", (item["id"],), limit=3)
    return render_template("news_article.html", item=item, preview=preview,
                           related=related_links(item["related"]), more=more)


@bp.route("/news/feed.xml")
def news_feed():
    category = request.args.get("category", "")
    if category and category not in dict(NEWS_CATEGORIES):
        abort(404)
    items = published("news", "category = ?" if category else "", (category,) if category else (), limit=20)
    base = (current_app.config["SITE_URL"] or request.url_root).rstrip("/")
    modified = max((parse_time(row[key]) for row in items for key in ('updated_at', 'publish_at', 'created_at')
                    if parse_time(row[key])), default=None)
    xml = render_template("feed.xml", items=items, base=base, category=category, modified=modified)
    response = Response(xml, content_type="application/rss+xml; charset=utf-8")
    response.set_etag(hashlib.sha256(response.get_data()).hexdigest())
    if modified:
        response.last_modified = modified
    response.headers['Cache-Control'] = 'public, max-age=60'
    return response.make_conditional(request)


@bp.route("/news/subscribe")
def news_subscribe():
    category = request.args.get("category", "")
    if category and category not in dict(NEWS_CATEGORIES):
        abort(404)
    base = (current_app.config['SITE_URL'] or request.url_root).rstrip('/')
    feed_url = base + url_for('public.news_feed', category=category or None)
    return render_template('news_subscribe.html', feed_url=feed_url, category=category, categories=NEWS_CATEGORIES)


# --- Store -----------------------------------------------------------------------------------

def _store_categories():
    cats = []
    for key, _label in STORE_CATEGORIES:
        title, blurb, image = STORE_INFO[key]
        items = [tebex.decorate(row) for row in published("products", "category = ?", (key,))]
        cats.append({"key": key, "title": title, "blurb": blurb, "image": image, "products": items,
                     "illustration": STORE_ILLUSTRATIONS.get(key),
                     "on_sale": [i for i in items if i['purchasable']]})
    return cats


@bp.route("/store")
def store():
    ranks = get_db().execute(
        "SELECT r.*, p.name AS product_name, p.image, p.image_alt FROM ranks r JOIN products p ON p.slug = r.product_slug "
        "WHERE " + content.LIVE.replace("status", "p.status").replace("publish_at", "p.publish_at") +
        " ORDER BY r.priority", (now_str(),)).fetchall()
    return render_template("store.html", categories=_store_categories(), supporter_ranks=ranks)


@bp.route("/store/<category>")
def store_category(category):
    if category not in STORE_INFO:
        abort(404)
    categories = _store_categories()
    cat = next(c for c in categories if c["key"] == category)
    from .store_compare import comparison
    return render_template("store_category.html", cat=cat, categories=categories,
                           comparison=comparison(cat['products']) if category == 'supporter-ranks' else [])


@bp.route("/store/item/<slug>")
def store_item(slug):
    item = published_one("products", slug) or abort(404)
    return render_product(item)


def render_product(item, preview=False):
    item = tebex.decorate(item)
    g.edit_target = ("products", item["id"])
    title, _blurb, _image = STORE_INFO.get(item["category"], ("Store", "", ""))
    return render_template("store_item.html", item=item, preview=preview, category_title=title,
                           categories=_store_categories(),
                           includes=lines(item["includes"]))


# --- Community and support -------------------------------------------------------------------

@bp.route("/community")
def community():
    return render_template("community.html", showcases=published("showcases"))


@bp.route('/community/streamers')
def streamers():
    from . import streaming
    rows = []
    for index, item in enumerate(published('streamers')):
        row = dict(item)
        row['display_order'] = index
        row['channel_url'] = streaming.channel_url(row['platform'], row['channel'])
        row['platform_label'] = streaming.PLATFORMS.get(row['platform'], row['platform'])
        row['live'] = streaming.cached_status(row, current_app.config, get_db())
        rows.append(row)
    rows.sort(key=lambda row: row['live']['state'] != 'live')
    missing = [label for key, label in streaming.PLATFORMS.items() if not streaming.configured(key, current_app.config)]
    return render_template('streamers.html', streamers=rows, platforms=streaming.PLATFORMS, missing_platforms=missing)


@bp.route('/community/streamers/status')
def streamer_status():
    from . import streaming
    rows = [dict(item) for item in published('streamers')]
    statuses = streaming.get_statuses(rows, dict(current_app.config), get_db())
    response = jsonify(channels=statuses)
    response.headers['Cache-Control'] = 'no-store'
    return response


@bp.route("/support")
def support():
    faqs = published("faqs", order_by="category, sort_order, id")
    return render_template("support.html", faqs=faqs,
                           pages=published("pages", "section = ?", ("support",)),
                           legal=published("pages", "section = ?", ("legal",)))


@bp.route("/support/<slug>")
def page(slug):
    item = published_one("pages", slug) or abort(404)
    return render_page(item)


def render_page(item, preview=False):
    g.edit_target = ("pages", item["id"])
    from .guide_reader import sections
    parts = sections(item['body'])
    for part in parts:
        part['id'] = part['id'].replace('guide-', 'policy-', 1)
    order = ['rules', 'terms', 'privacy', 'store-terms', 'account-help', 'appeals', 'bug-reports', 'purchase-help']
    pages = sorted(published('pages'), key=lambda p: (order.index(p['slug']) if p['slug'] in order else 99, p['title']))
    return render_template('page.html', item=item, preview=preview, policy_sections=parts, policy_pages=pages,
                           reading_minutes=max(1, (len(item['body'].split()) + 199) // 200))


# --- Files, redirects and crawlers -----------------------------------------------------------

@bp.route("/media/<path:filename>")
def media(filename):
    response = send_from_directory(current_app.config["UPLOAD_DIR"], filename, max_age=60 * 60 * 24 * 30)
    response.headers["Content-Security-Policy"] = "default-src 'none'"
    return response


@bp.route("/discord")
def discord():
    """The community landing page, with an optional official Discord server widget."""
    from .db import get_setting
    from .discord import invite_url, server_id, widget_available
    from .security import CSP
    invite = invite_url(get_setting('discord_invite')) or invite_url(current_app.config.get('DISCORD_URL'))
    widget_id = server_id(get_setting('discord_server_id')) or server_id(current_app.config.get('DISCORD_SERVER_ID'))
    widget_ready = widget_available(widget_id)
    response = current_app.make_response(render_template('discord.html', invite=invite, widget_id=widget_id,
                                                         widget_ready=widget_ready))
    if widget_ready:
        response.headers['Content-Security-Policy'] = CSP + '; frame-src https://discord.com'
    return response


@bp.route("/rules")
def rules():
    return redirect(url_for("public.page", slug="rules"), 301)


@bp.route("/join")
def old_join():
    return redirect(url_for("public.play"), 301)


@bp.route("/features")
def old_features():
    return redirect(url_for("public.guide"), 301)


@bp.route("/robots.txt")
def robots():
    base = current_app.config["SITE_URL"] or request.url_root.rstrip("/")
    body = "User-agent: *\nDisallow: /admin/\nSitemap: %s/sitemap.xml\n" % base
    return Response(body, mimetype="text/plain")


@bp.route("/sitemap.xml")
def sitemap():
    base = current_app.config["SITE_URL"] or request.url_root.rstrip("/")
    paths = [url_for("public." + name) for name in (
        "index", "play", "status_page", "explore", "expeditions", "campaign", "guide", "codex", "companions", "news", "store",
        "community", "streamers", "support")]
    data, _problem = gameapi.expeditions()
    paths += [url_for("public.expedition", slug=raw["id"]) for raw in (data or {}).get("expeditions") or []
              if isinstance(raw, dict) and isinstance(raw.get("id"), str)]
    for key, endpoint in (("regions", "region"), ("guides", "guide_article"), ("companions", "companion"),
                          ("news", "news_article"), ("products", "store_item"), ("pages", "page")):
        paths += [url_for("public." + endpoint, slug=row["slug"]) for row in published(key)]
    xml = render_template("sitemap.xml", base=base, paths=paths)
    return Response(xml, mimetype="application/xml")


# Admin previews reuse the public templates through these renderers.
PREVIEW_RENDERERS = {
    "regions": render_region,
    "guides": render_guide,
    "companions": render_companion,
    "news": render_news,
    "products": render_product,
    "pages": render_page,
}
