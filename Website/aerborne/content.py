"""Read-only queries for public pages. Only published, already-live content comes out of here."""
from flask import url_for

from .content_types import get_type
from .db import get_db, now_str

LIVE = "status = 'published' AND (publish_at IS NULL OR publish_at = '' OR publish_at <= ?)"


def published(type_key, where="", params=(), order_by=None, limit=None):
    ctype = get_type(type_key)
    sql = "SELECT * FROM %s WHERE %s" % (ctype.table, LIVE)
    args = [now_str()]
    if where:
        sql += " AND (" + where + ")"
        args.extend(params)
    sql += " ORDER BY " + (order_by or ctype.order_by)
    if limit:
        sql += " LIMIT %d" % int(limit)
    return get_db().execute(sql, args).fetchall()


def published_one(type_key, slug):
    ctype = get_type(type_key)
    return get_db().execute(
        "SELECT * FROM %s WHERE slug = ? AND %s" % (ctype.table, LIVE), (slug, now_str())
    ).fetchone()


def any_by_id(type_key, item_id):
    ctype = get_type(type_key)
    return get_db().execute("SELECT * FROM %s WHERE id = ?" % ctype.table, (item_id,)).fetchone()


def lines(value):
    return [line.strip() for line in (value or "").splitlines() if line.strip()]


def related_links(value):
    """Parse 'Label | /path' lines. Only same-site paths are allowed."""
    links = []
    for line in lines(value):
        label, _, path = line.partition("|")
        label, path = label.strip(), path.strip()
        if not path:
            path, label = label, label
        if path.startswith("/") and not path.startswith("//"):
            links.append((label or path, path))
    return links


def image_url(value):
    """Images are either built-in ('static:img/...') or uploaded ('media:<file>')."""
    if not value:
        return ""
    if value.startswith("static:"):
        return url_for("static", filename=value[len("static:"):])
    if value.startswith("media:"):
        return url_for("public.media", filename=value[len("media:"):])
    return ""
