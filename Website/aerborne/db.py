"""SQLite storage. One file in the data folder; no database server needed on cPanel."""
import datetime
import json
import sqlite3

from flask import current_app, g

SYSTEM_SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY,
    username TEXT NOT NULL UNIQUE COLLATE NOCASE,
    display_name TEXT NOT NULL DEFAULT '',
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'writer',
    active INTEGER NOT NULL DEFAULT 1,
    session_version INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    last_login_at TEXT
);
CREATE TABLE IF NOT EXISTS audit_log (
    id INTEGER PRIMARY KEY,
    created_at TEXT NOT NULL,
    user_id INTEGER,
    username TEXT NOT NULL DEFAULT '',
    action TEXT NOT NULL,
    entity TEXT NOT NULL DEFAULT '',
    entity_id INTEGER,
    summary TEXT NOT NULL DEFAULT '',
    changes TEXT NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS audit_log_created ON audit_log (created_at);
CREATE TABLE IF NOT EXISTS login_attempts (
    id INTEGER PRIMARY KEY,
    ip TEXT NOT NULL,
    username TEXT NOT NULL,
    created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS login_attempts_ip ON login_attempts (ip, created_at);
CREATE TABLE IF NOT EXISTS media (
    id INTEGER PRIMARY KEY,
    filename TEXT NOT NULL UNIQUE,
    thumb TEXT NOT NULL DEFAULT '',
    original_name TEXT NOT NULL DEFAULT '',
    mime TEXT NOT NULL,
    bytes INTEGER NOT NULL DEFAULT 0,
    width INTEGER,
    height INTEGER,
    alt TEXT NOT NULL DEFAULT '',
    uploaded_by TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS page_views (
    day TEXT NOT NULL,
    path TEXT NOT NULL,
    views INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (day, path)
);
CREATE TABLE IF NOT EXISTS rate_events (
    id INTEGER PRIMARY KEY,
    kind TEXT NOT NULL,
    key TEXT NOT NULL,
    created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS rate_events_lookup ON rate_events (kind, key, created_at);

CREATE TABLE IF NOT EXISTS members (
    id INTEGER PRIMARY KEY,
    username TEXT NOT NULL UNIQUE COLLATE NOCASE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'member',
    status TEXT NOT NULL DEFAULT 'active',
    ban_reason TEXT NOT NULL DEFAULT '',
    session_version INTEGER NOT NULL DEFAULT 1,
    bio TEXT NOT NULL DEFAULT '',
    mc_uuid TEXT UNIQUE,
    mc_name TEXT,
    mc_account_id TEXT,
    linked_at TEXT,
    post_count INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    last_login_at TEXT
);
CREATE TABLE IF NOT EXISTS member_link_codes (
    code TEXT PRIMARY KEY,
    member_id INTEGER NOT NULL,
    purpose TEXT NOT NULL,
    used_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS forum_categories (
    id INTEGER PRIMARY KEY,
    slug TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    staff_only INTEGER NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS forum_threads (
    id INTEGER PRIMARY KEY,
    category_id INTEGER NOT NULL REFERENCES forum_categories (id),
    member_id INTEGER NOT NULL REFERENCES members (id),
    title TEXT NOT NULL,
    created_at TEXT NOT NULL,
    last_post_at TEXT NOT NULL,
    last_member_id INTEGER,
    reply_count INTEGER NOT NULL DEFAULT 0,
    pinned INTEGER NOT NULL DEFAULT 0,
    locked INTEGER NOT NULL DEFAULT 0,
    hidden INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS forum_threads_category ON forum_threads (category_id, hidden, pinned, last_post_at);
CREATE TABLE IF NOT EXISTS forum_posts (
    id INTEGER PRIMARY KEY,
    thread_id INTEGER NOT NULL REFERENCES forum_threads (id),
    member_id INTEGER NOT NULL REFERENCES members (id),
    body TEXT NOT NULL,
    created_at TEXT NOT NULL,
    edited_at TEXT,
    hidden INTEGER NOT NULL DEFAULT 0,
    hidden_reason TEXT NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS forum_posts_thread ON forum_posts (thread_id, id);
CREATE TABLE IF NOT EXISTS ranks (
    id INTEGER PRIMARY KEY,
    slug TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    color TEXT NOT NULL DEFAULT '#8E9AAF',
    text_color TEXT NOT NULL DEFAULT '#17243B',
    staff INTEGER NOT NULL DEFAULT 0,
    can_moderate INTEGER NOT NULL DEFAULT 0,
    can_announce INTEGER NOT NULL DEFAULT 0,
    priority INTEGER NOT NULL DEFAULT 0,
    description TEXT NOT NULL DEFAULT '',
    product_slug TEXT NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS post_likes (
    post_id INTEGER NOT NULL REFERENCES forum_posts (id),
    member_id INTEGER NOT NULL REFERENCES members (id),
    created_at TEXT NOT NULL,
    PRIMARY KEY (post_id, member_id)
);
CREATE TABLE IF NOT EXISTS arcade_runs (
    id INTEGER PRIMARY KEY,
    token TEXT NOT NULL UNIQUE,
    member_id INTEGER,
    game TEXT NOT NULL,
    started_at TEXT NOT NULL,
    finished_at TEXT,
    score INTEGER,
    marks INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'running'
);
CREATE INDEX IF NOT EXISTS arcade_runs_member ON arcade_runs (member_id, game, started_at);
CREATE TABLE IF NOT EXISTS arcade_payouts (
    id INTEGER PRIMARY KEY,
    member_id INTEGER NOT NULL REFERENCES members (id),
    account_id TEXT NOT NULL,
    run_id INTEGER NOT NULL REFERENCES arcade_runs (id),
    game TEXT NOT NULL,
    marks INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    delivered_at TEXT
);
CREATE INDEX IF NOT EXISTS arcade_payouts_status ON arcade_payouts (status, id);
CREATE TABLE IF NOT EXISTS forum_reports (
    id INTEGER PRIMARY KEY,
    post_id INTEGER NOT NULL REFERENCES forum_posts (id),
    member_id INTEGER NOT NULL REFERENCES members (id),
    reason TEXT NOT NULL,
    created_at TEXT NOT NULL,
    resolved_at TEXT,
    resolved_by TEXT
);
"""

# Columns added to system tables after their first release.
SYSTEM_COLUMNS = [
    ("users", "must_change_password", "INTEGER NOT NULL DEFAULT 0"),
    # Profiles and display ranks.
    ("members", "rank_id", "INTEGER"),
    ("members", "custom_title", "TEXT NOT NULL DEFAULT ''"),
    ("members", "avatar", "TEXT NOT NULL DEFAULT ''"),
    ("members", "banner", "TEXT NOT NULL DEFAULT ''"),
    ("members", "headline", "TEXT NOT NULL DEFAULT ''"),
    ("members", "signature", "TEXT NOT NULL DEFAULT ''"),
    ("members", "favorite_skill", "TEXT NOT NULL DEFAULT ''"),
    ("members", "island_name", "TEXT NOT NULL DEFAULT ''"),
    ("members", "discord_handle", "TEXT NOT NULL DEFAULT ''"),
    ("members", "youtube_url", "TEXT NOT NULL DEFAULT ''"),
    ("members", "twitch_url", "TEXT NOT NULL DEFAULT ''"),
    ("members", "show_stats", "INTEGER NOT NULL DEFAULT 1"),
    ("members", "likes_received", "INTEGER NOT NULL DEFAULT 0"),
    ("members", "last_seen_at", "TEXT"),
    ("ranks", "frame", "TEXT NOT NULL DEFAULT ''"),
    # Forum layout.
    ("forum_categories", "section", "TEXT NOT NULL DEFAULT 'community'"),
    ("forum_categories", "icon", "TEXT NOT NULL DEFAULT ''"),
    ("forum_categories", "private", "INTEGER NOT NULL DEFAULT 0"),
    ("forum_threads", "prefix", "TEXT NOT NULL DEFAULT ''"),
    ("forum_threads", "view_count", "INTEGER NOT NULL DEFAULT 0"),
    ("forum_posts", "like_count", "INTEGER NOT NULL DEFAULT 0"),
]


def utcnow():
    return datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0, tzinfo=None)


def now_str():
    return utcnow().strftime("%Y-%m-%d %H:%M:%S")


def parse_time(value):
    if not value:
        return None
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M", "%Y-%m-%dT%H:%M", "%Y-%m-%d"):
        try:
            return datetime.datetime.strptime(value, fmt)
        except ValueError:
            continue
    return None


def connect(path):
    conn = sqlite3.connect(path, timeout=10)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def get_db():
    if "db" not in g:
        g.db = connect(current_app.config["DATABASE"])
    return g.db


def close_db(_error=None):
    conn = g.pop("db", None)
    if conn is not None:
        conn.close()


def migrate(conn, content_types):
    """Create missing tables and add missing columns. Never drops anything."""
    conn.executescript(SYSTEM_SCHEMA)
    for table, column, decl in SYSTEM_COLUMNS:
        _add_column(conn, table, column, decl)
    for ctype in content_types:
        conn.execute(ctype.create_sql())
        for column, decl in ctype.columns():
            _add_column(conn, ctype.table, column, decl)
        conn.execute(
            "CREATE UNIQUE INDEX IF NOT EXISTS %s_slug ON %s (slug)" % (ctype.table, ctype.table)
        )
    conn.commit()


def _add_column(conn, table, column, decl):
    existing = {row[1] for row in conn.execute("PRAGMA table_info(%s)" % table)}
    if column in existing:
        return
    try:
        conn.execute("ALTER TABLE %s ADD COLUMN %s %s" % (table, column, decl))
    except sqlite3.OperationalError as exc:
        if "duplicate column" not in str(exc):  # another worker added it first
            raise


def get_setting(key, default=""):
    row = get_db().execute("SELECT value FROM settings WHERE key = ?", (key,)).fetchone()
    return row["value"] if row else default


def get_settings():
    return {row["key"]: row["value"] for row in get_db().execute("SELECT key, value FROM settings")}


def set_setting(conn, key, value):
    # INSERT OR REPLACE rather than UPSERT: cPanel hosts often ship SQLite older than 3.24.
    conn.execute("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)", (key, value))


def audit(conn, user, action, entity="", entity_id=None, summary="", changes=None):
    conn.execute(
        "INSERT INTO audit_log (created_at, user_id, username, action, entity, entity_id, summary, changes) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        (
            now_str(),
            user["id"] if user else None,
            user["username"] if user else "system",
            action,
            entity,
            entity_id,
            summary[:300],
            json.dumps(changes, ensure_ascii=False)[:20000] if changes else "",
        ),
    )


SECURITY_RECORD_SECONDS = 3600
_last_purge = [0.0]


def purge_security_records(conn, force=True):
    """Drop IP-bearing throttle records older than an hour (the privacy policy promises this)."""
    import time
    if not force and time.time() - _last_purge[0] < 600:
        return
    _last_purge[0] = time.time()
    cutoff = (utcnow() - datetime.timedelta(seconds=SECURITY_RECORD_SECONDS)).strftime("%Y-%m-%d %H:%M:%S")
    conn.execute("DELETE FROM login_attempts WHERE created_at < ?", (cutoff,))
    conn.execute("DELETE FROM rate_events WHERE created_at < ?", (cutoff,))
    conn.commit()
