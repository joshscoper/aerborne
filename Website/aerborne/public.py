"""Public pages. Everything shown here is published content or verified configuration."""
import sqlite3

from flask import (Blueprint, Response, abort, current_app, g, redirect, render_template, request,
                   send_from_directory, url_for)

from . import content
from .content import lines, published, published_one, related_links
from .content_types import ASPECTS, GUIDE_CATEGORIES, NEWS_CATEGORIES, STORE_CATEGORIES, get_type
from .db import get_db, now_str, utcnow

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
    g.edit_target = ("guides", item["id"])
    siblings = published("guides", "category = ?", (item["category"],))
    position = next((i for i, g in enumerate(siblings) if g["id"] == item["id"]), None)
    prev_item = siblings[position - 1] if position else None
    next_item = siblings[position + 1] if position is not None and position + 1 < len(siblings) else None
    return render_template("guide_article.html", item=item, preview=preview,
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
    items = published("news", limit=20)
    base = current_app.config["SITE_URL"] or request.url_root.rstrip("/")
    xml = render_template("feed.xml", items=items, base=base)
    return Response(xml, mimetype="application/rss+xml")


# --- Store -----------------------------------------------------------------------------------

def _store_categories():
    cats = []
    for key, _label in STORE_CATEGORIES:
        title, blurb, image = STORE_INFO[key]
        items = published("products", "category = ?", (key,))
        cats.append({"key": key, "title": title, "blurb": blurb, "image": image, "products": items,
                     "on_sale": [i for i in items if i["availability"] == "released" and i["price"]]})
    return cats


@bp.route("/store")
def store():
    ranks = get_db().execute(
        "SELECT r.*, p.name AS product_name FROM ranks r JOIN products p ON p.slug = r.product_slug "
        "WHERE " + content.LIVE.replace("status", "p.status").replace("publish_at", "p.publish_at") +
        " ORDER BY r.priority", (now_str(),)).fetchall()
    return render_template("store.html", categories=_store_categories(), supporter_ranks=ranks)


@bp.route("/store/<category>")
def store_category(category):
    if category not in STORE_INFO:
        abort(404)
    cat = next(c for c in _store_categories() if c["key"] == category)
    return render_template("store_category.html", cat=cat)


@bp.route("/store/item/<slug>")
def store_item(slug):
    item = published_one("products", slug) or abort(404)
    return render_product(item)


def render_product(item, preview=False):
    g.edit_target = ("products", item["id"])
    title, _blurb, _image = STORE_INFO.get(item["category"], ("Store", "", ""))
    return render_template("store_item.html", item=item, preview=preview, category_title=title,
                           includes=lines(item["includes"]))


# --- Community and support -------------------------------------------------------------------

@bp.route("/community")
def community():
    return render_template("community.html", showcases=published("showcases"),
                           events=published("news", "category = ?", ("event",), limit=3))


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
    return render_template("page.html", item=item, preview=preview)


# --- Files, redirects and crawlers -----------------------------------------------------------

@bp.route("/media/<path:filename>")
def media(filename):
    response = send_from_directory(current_app.config["UPLOAD_DIR"], filename, max_age=60 * 60 * 24 * 30)
    response.headers["Content-Security-Policy"] = "default-src 'none'"
    return response


@bp.route("/discord")
def discord():
    """A stable link for every Discord button: the invite once it's set, a holding page until then."""
    from .db import get_setting
    invite = get_setting("discord_invite") or current_app.config.get("DISCORD_URL")
    if invite:
        return redirect(invite, 302)
    return render_template("community/closed.html", title="Discord is coming soon",
                           body="Our Discord server opens soon. Check back here for the invite.")


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
        "index", "play", "status_page", "explore", "guide", "codex", "companions", "news", "store",
        "community", "support")]
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
