"""Player accounts, Minecraft linking, leaderboards, player profiles and the forum."""
import datetime
import functools
import math
import re

from flask import (Blueprint, abort, current_app, flash, g, redirect, render_template, request, session,
                   url_for)
from werkzeug.security import check_password_hash, generate_password_hash

from . import gameapi
from .community_data import (AUTHOR_COLUMNS, AVATAR_PATHS, AVATARS, BANNER_PATHS, BANNERS, DEFAULT_AVATAR, LEGACY_AVATARS, FAVOURITE_SKILLS, FORUM_ICONS, PREFIXES,
                             SECTIONS, frame_key, prefixes_for)
from .db import audit, get_db, get_setting, now_str, parse_time, utcnow
from .security import (allow, check_csrf, clear_failed_logins, client_ip, login_blocked,
                       record_failed_login)

bp = Blueprint("community", __name__)

USERNAME_RE = re.compile(r"^[A-Za-z0-9_-]{3,20}$")
HEX = re.compile(r"^#[0-9a-fA-F]{6}$")
YOUTUBE_RE = re.compile(r"^https://(www\.)?(youtube\.com/(@|c/|channel/|user/)[A-Za-z0-9_.-]{1,80}|youtu\.be/[A-Za-z0-9_-]{1,40})/?$")
TWITCH_RE = re.compile(r"^https://(www\.)?twitch\.tv/[A-Za-z0-9_]{3,25}/?$")
DISCORD_HANDLE_RE = re.compile(r"^[A-Za-z0-9_.]{2,32}$")
MIN_PASSWORD = 10
THREADS_PER_PAGE = 25
POSTS_PER_PAGE = 20
MAX_TITLE = 120
MAX_POST = 10000
MOD_ROLES = ("moderator", "staff")


# --- Session --------------------------------------------------------------------------------

def load_member():
    g.member = None
    mid = session.get("mid")
    if not mid:
        return
    conn = get_db()
    row = conn.execute(
        "SELECT m.*, r.slug AS rank_slug, r.name AS rank_name, r.staff AS rank_staff, r.frame AS rank_frame, r.can_moderate AS rank_mod, "
        "r.can_announce AS rank_announce FROM members m LEFT JOIN ranks r ON r.id = m.rank_id WHERE m.id = ?",
        (mid,)).fetchone()
    if not row or row["session_version"] != session.get("msv"):
        session.pop("mid", None)
        session.pop("msv", None)
        return
    g.member = row
    seen = parse_time(row["last_seen_at"])
    if seen is None or (utcnow() - seen).total_seconds() > 120:
        conn.execute("UPDATE members SET last_seen_at = ? WHERE id = ?", (now_str(), row["id"]))
        conn.commit()


def _start_member_session(member):
    for key in ("mid", "msv", "_csrf"):
        session.pop(key, None)
    session.permanent = True
    session["mid"] = member["id"]
    session["msv"] = member["session_version"]


def member_required(view):
    @functools.wraps(view)
    def wrapped(*args, **kwargs):
        if g.get("member") is None:
            flash("Sign in to do that.")
            return redirect(url_for("community.login", next=request.full_path))
        return view(*args, **kwargs)
    return wrapped


def _row(member, key, default=None):
    try:
        return member[key]
    except (IndexError, KeyError):
        return default


def _is_mod(member):
    if not member or member["status"] != "active":
        return False
    return member["role"] in MOD_ROLES or bool(_row(member, "rank_mod"))


def _can_announce(member):
    return bool(member) and (member["role"] == "staff" or bool(_row(member, "rank_announce")))


def _staff_mod():
    user = g.get("user")
    return bool(g.get("staff_view")) and user is not None and user["role"] in ("admin", "moderator") \
        and not user["must_change_password"]


def can_moderate():
    return _is_mod(g.get("member")) or _staff_mod()


def _mod_actor():
    """Who the audit log credits: the staff account in admin view, otherwise the forum member."""
    if _staff_mod():
        return g.user
    return {"id": None, "username": "forum:" + g.member["username"]}


def _safe_next(target, fallback):
    if target and target.startswith("/") and not target.startswith("//") and "\\" not in target:
        return target
    return fallback


def _hash(password):
    return generate_password_hash(password, method="pbkdf2:sha256:260000", salt_length=16)


@bp.before_request
def _guard():
    check_csrf()


@bp.app_template_global()
def is_moderator(member=None):
    if member is not None:
        return _is_mod(member)
    return can_moderate()


@bp.app_template_global()
def avatar_url(key):
    return url_for("static", filename=AVATAR_PATHS.get(key or "", AVATAR_PATHS[DEFAULT_AVATAR]))


@bp.app_template_global()
def forum_icon(slug):
    return FORUM_ICONS.get(slug)


@bp.app_template_global()
def frame_name(frame):
    return frame_key(frame)


@bp.app_template_global()
def banner_url(key):
    return url_for("static", filename=BANNER_PATHS.get(key or "", BANNER_PATHS["sky"]))


@bp.app_template_global()
def prefix_info(slug):
    return PREFIXES.get(slug or "")


@bp.app_template_global()
def is_online(last_seen):
    seen = parse_time(last_seen) if last_seen else None
    return seen is not None and (utcnow() - seen).total_seconds() < 15 * 60


@bp.route("/ranks.css")
def ranks_css():
    """Rank colours as classes, so the strict CSP never needs inline styles."""
    from flask import Response
    lines = []
    for r in get_db().execute("SELECT slug, color, text_color FROM ranks"):
        if re.match(r"^[a-z0-9-]{1,40}$", r["slug"]) and HEX.match(r["color"]) and HEX.match(r["text_color"]):
            lines.append(".rank-%s{--rank:%s;--rank-ink:%s}" % (r["slug"], r["color"], r["text_color"]))
    response = Response("\n".join(lines) + "\n", mimetype="text/css")
    response.headers["Cache-Control"] = "public, max-age=300"
    return response


@bp.app_template_filter("forum")
def _forum_filter(text):
    from .markup import render_forum
    return render_forum(text)


@bp.app_template_filter("number")
def _number(value):
    try:
        return "{:,}".format(int(value))
    except (TypeError, ValueError):
        return value


def _settings_flag(key, default="1"):
    return get_setting(key, default) == "1"


# --- Accounts -------------------------------------------------------------------------------

@bp.route("/account/register", methods=["GET", "POST"])
def register():
    if g.member:
        return redirect(url_for("community.account"))
    if not _settings_flag("registrations_open"):
        return render_template("community/closed.html", title="Sign-ups are closed",
                               body="New accounts are paused for now. Check back soon.")
    error = None
    form = {"username": ""}
    if request.method == "POST":
        conn = get_db()
        username = request.form.get("username", "").strip()
        password = request.form.get("password", "")
        form["username"] = username
        if request.form.get("website"):  # honeypot: people never see this field
            abort(400)
        if not USERNAME_RE.match(username):
            error = "Use 3 to 20 letters, numbers, dashes or underscores for your username."
        elif len(password) < MIN_PASSWORD:
            error = "Use at least %d characters for your password." % MIN_PASSWORD
        elif password != request.form.get("confirm", ""):
            error = "The two passwords don't match."
        elif conn.execute("SELECT 1 FROM members WHERE username = ? UNION SELECT 1 FROM users WHERE username = ?",
                          (username, username)).fetchone():
            error = "That username is taken. Try another."
        elif not allow(conn, "register", client_ip(), 3, 3600):
            conn.commit()
            error = "Too many accounts were made from this connection. Try again in an hour."
        else:
            cur = conn.execute("INSERT INTO members (username, password_hash, created_at, last_login_at) VALUES (?, ?, ?, ?)",
                               (username, _hash(password), now_str(), now_str()))
            conn.commit()
            member = conn.execute("SELECT * FROM members WHERE id = ?", (cur.lastrowid,)).fetchone()
            _start_member_session(member)
            flash("Welcome to Aerborne, %s. Link your Minecraft account to start posting." % username)
            return redirect(url_for("community.link"))
    return render_template("community/register.html", error=error, form=form, min_password=MIN_PASSWORD)


@bp.route("/account/login", methods=["GET", "POST"])
def login():
    if g.member:
        return redirect(url_for("community.account"))
    error = None
    if request.method == "POST":
        conn = get_db()
        username = request.form.get("username", "").strip()[:20]
        password = request.form.get("password", "")
        throttle_key = "member:" + username
        if login_blocked(conn, throttle_key):
            error = "Too many sign-in attempts. Wait 15 minutes and try again."
        else:
            member = conn.execute("SELECT * FROM members WHERE username = ? AND status != 'deleted'", (username,)).fetchone()
            if member and check_password_hash(member["password_hash"], password):
                clear_failed_logins(conn, throttle_key)
                conn.execute("UPDATE members SET last_login_at = ? WHERE id = ?", (now_str(), member["id"]))
                conn.commit()
                _start_member_session(member)
                return redirect(_safe_next(request.args.get("next"), url_for("community.forum")))
            # Staff accounts live separately; let them use this form too and send them to the admin.
            staff = None if member else conn.execute("SELECT * FROM users WHERE username = ? AND active = 1",
                                                     (username,)).fetchone()
            if staff and check_password_hash(staff["password_hash"], password):
                from .admin import complete_staff_login
                clear_failed_logins(conn, throttle_key)
                flash("Signed in to the staff admin.")
                return complete_staff_login(conn, staff, password)
            record_failed_login(conn, throttle_key)
            conn.commit()
            error = "That username and password don't match."
    return render_template("community/login.html", error=error)


@bp.route("/account/logout", methods=["POST"])
def logout():
    session.pop("mid", None)
    session.pop("msv", None)
    flash("You're signed out.")
    return redirect(url_for("public.index"))


@bp.route("/account", methods=["GET", "POST"])
@member_required
def account():
    conn = get_db()
    if request.method == "POST":
        action = request.form.get("action")
        if action == "password":
            current = request.form.get("current", "")
            new = request.form.get("new", "")
            if not check_password_hash(g.member["password_hash"], current):
                flash("Your current password is wrong.", "error")
            elif len(new) < MIN_PASSWORD:
                flash("Use at least %d characters for your new password." % MIN_PASSWORD, "error")
            else:
                conn.execute("UPDATE members SET password_hash = ?, session_version = session_version + 1 WHERE id = ?",
                             (_hash(new), g.member["id"]))
                conn.commit()
                _start_member_session(conn.execute("SELECT * FROM members WHERE id = ?", (g.member["id"],)).fetchone())
                flash("Password changed. You've been signed out everywhere else.")
        elif action == "profile":
            problem = _save_profile(conn, g.member["id"], request.form)
            if problem:
                flash(problem, "error")
            else:
                conn.commit()
                flash("Profile saved.")
        return redirect(url_for("community.account"))
    return render_template("community/account.html", avatars=AVATARS, legacy_avatars=LEGACY_AVATARS,
                           selected_avatar=g.member["avatar"] or DEFAULT_AVATAR, banners=BANNERS, skills=FAVOURITE_SKILLS,
                           dm_policies=[("everyone", "Anyone"), ("friends", "Friends only"), ("nobody", "No one")])


def _save_profile(conn, member_id, form):
    fields = {
        "headline": form.get("headline", "").strip()[:80],
        "bio": form.get("bio", "").replace("\r\n", "\n").strip()[:1000],
        "signature": form.get("signature", "").replace("\r\n", "\n").strip()[:200],
        "island_name": form.get("island_name", "").strip()[:40],
        "favorite_skill": form.get("favorite_skill", ""),
        "discord_handle": form.get("discord_handle", "").strip().lstrip("@")[:32],
        "youtube_url": form.get("youtube_url", "").strip()[:200],
        "twitch_url": form.get("twitch_url", "").strip()[:200],
        "avatar": form.get("avatar", ""),
        "banner": form.get("banner", ""),
        "show_stats": 1 if form.get("show_stats") else 0,
        "dm_policy": form.get("dm_policy", "everyone"),
        "friend_requests_open": 1 if form.get("friend_requests_open") else 0,
    }
    if fields["dm_policy"] not in ("everyone", "friends", "nobody"):
        return "Pick who can message you from the list."
    if fields["favorite_skill"] and fields["favorite_skill"] not in dict(FAVOURITE_SKILLS):
        return "Pick a skill from the list."
    if fields["avatar"] and fields["avatar"] not in AVATAR_PATHS:
        return "Pick one of the avatars."
    if fields["banner"] and fields["banner"] not in BANNER_PATHS:
        return "Pick one of the banners."
    if fields["discord_handle"] and not DISCORD_HANDLE_RE.match(fields["discord_handle"]):
        return "Discord usernames use letters, numbers, dots and underscores."
    if fields["youtube_url"] and not YOUTUBE_RE.match(fields["youtube_url"]):
        return "Use a full YouTube channel link, like https://youtube.com/@yourname."
    if fields["twitch_url"] and not TWITCH_RE.match(fields["twitch_url"]):
        return "Use a full Twitch link, like https://twitch.tv/yourname."
    cols = list(fields.keys())
    conn.execute("UPDATE members SET %s WHERE id = ?" % ", ".join(c + " = ?" for c in cols),
                 [fields[c] for c in cols] + [member_id])
    return None


def _redeem(conn, code_text, purpose):
    """Shared by linking and recovery. Returns (link data, error message)."""
    code = gameapi.normalise_code(code_text)
    if not code:
        return None, "That code doesn't look right. It's 8 letters and numbers, like ABCD-2345."
    if conn.execute("SELECT 1 FROM member_link_codes WHERE code = ?", (code,)).fetchone():
        return None, "That code has already been used. Type /weblink in game for a new one."
    data, problem = gameapi.redeem_link_code(code)
    if problem == "not_found":
        return None, "That code isn't valid or has expired. Type /weblink in game for a new one."
    if problem:
        return None, "Linking isn't available right now because the game server can't be reached. Try again later."
    return dict(data, code=code), None


@bp.route("/account/link", methods=["GET", "POST"])
@member_required
def link():
    conn = get_db()
    available = bool(current_app.config.get("API_TOKEN"))
    if request.method == "POST":
        if g.member["mc_uuid"]:
            flash("Your account is already linked.")
            return redirect(url_for("community.account"))
        if not allow(conn, "link", g.member["id"], 10, 3600):
            conn.commit()
            flash("Too many link attempts. Try again in an hour.", "error")
            return redirect(url_for("community.link"))
        data, error = _redeem(conn, request.form.get("code"), "link")
        if not error:
            other = conn.execute("SELECT username FROM members WHERE mc_uuid = ? OR (mc_account_id IS NOT NULL AND mc_account_id = ?)",
                                 (data["playerId"], data.get("accountId"))).fetchone()
            if other:
                error = "That Minecraft account is already linked to another website account."
        if error:
            conn.commit()
            flash(error, "error")
            return redirect(url_for("community.link"))
        conn.execute("INSERT INTO member_link_codes (code, member_id, purpose, used_at) VALUES (?, ?, 'link', ?)",
                     (data["code"], g.member["id"], now_str()))
        conn.execute("UPDATE members SET mc_uuid = ?, mc_name = ?, mc_account_id = ?, linked_at = ? WHERE id = ?",
                     (data["playerId"], data["name"], data.get("accountId"), now_str(), g.member["id"]))
        from .social import on_linked
        on_linked(conn, conn.execute("SELECT * FROM members WHERE id = ?", (g.member["id"],)).fetchone())
        conn.commit()
        flash("Linked to %s. You can post on the forum now, and your friends and blocks will match in game." % data["name"])
        return redirect(url_for("community.account"))
    return render_template("community/link.html", available=available)


@bp.route("/account/recover", methods=["GET", "POST"])
def recover():
    """Reset a password by proving the linked Minecraft account with a fresh /weblink code."""
    conn = get_db()
    error = None
    if request.method == "POST":
        username = request.form.get("username", "").strip()[:20]
        new = request.form.get("new", "")
        member = conn.execute("SELECT * FROM members WHERE username = ?", (username,)).fetchone()
        if not allow(conn, "recover", client_ip(), 5, 3600):
            error = "Too many attempts. Try again in an hour."
        elif len(new) < MIN_PASSWORD:
            error = "Use at least %d characters for your new password." % MIN_PASSWORD
        elif not member or not member["mc_uuid"]:
            error = "Only accounts linked to Minecraft can be recovered this way. Ask staff on Discord for help."
        else:
            data, error = _redeem(conn, request.form.get("code"), "recover")
            if not error and data["playerId"] != member["mc_uuid"]:
                error = "That code belongs to a different Minecraft account."
            if not error:
                conn.execute("INSERT INTO member_link_codes (code, member_id, purpose, used_at) VALUES (?, ?, 'recover', ?)",
                             (data["code"], member["id"], now_str()))
                conn.execute("UPDATE members SET password_hash = ?, session_version = session_version + 1 WHERE id = ?",
                             (_hash(new), member["id"]))
                conn.commit()
                flash("Password reset. Sign in with your new password.")
                return redirect(url_for("community.login"))
        conn.commit()
    return render_template("community/recover.html", error=error, available=bool(current_app.config.get("API_TOKEN")))


@bp.route("/members/<username>")
def member_profile(username):
    conn = get_db()
    member = conn.execute(
        "SELECT m.*, r.slug AS rank_slug, r.name AS rank_name, r.staff AS rank_staff, r.frame AS rank_frame, r.description AS rank_description "
        "FROM members m LEFT JOIN ranks r ON r.id = m.rank_id WHERE m.username = ? AND m.status != 'deleted'", (username,)).fetchone() or abort(404)
    threads = conn.execute(
        "SELECT t.*, c.slug AS category_slug, c.name AS category_name FROM forum_threads t "
        "JOIN forum_categories c ON c.id = t.category_id WHERE t.member_id = ? AND t.hidden = 0 AND c.private = 0 "
        "ORDER BY t.created_at DESC LIMIT 8", (member["id"],)).fetchall()
    posts = conn.execute(
        "SELECT p.id, p.body, p.created_at, t.id AS thread_id, t.title FROM forum_posts p "
        "JOIN forum_threads t ON t.id = p.thread_id JOIN forum_categories c ON c.id = t.category_id "
        "WHERE p.member_id = ? AND p.hidden = 0 AND t.hidden = 0 AND c.private = 0 ORDER BY p.id DESC LIMIT 6",
        (member["id"],)).fetchall()
    stats, problem = (None, None)
    if member["mc_name"] and member["show_stats"]:
        stats, problem = gameapi.player(member["mc_name"])
    rel = None
    if g.member and g.member["id"] != member["id"]:
        from .social import relationship
        rel = relationship(conn, g.member["id"], member["id"])
    return render_template("community/member.html", member=member, threads=threads, posts=posts,
                           stats=stats, stats_problem=problem, skill_label=dict(FAVOURITE_SKILLS), rel=rel)


# --- Leaderboards and player profiles -------------------------------------------------------

@bp.route("/leaderboards")
def leaderboards():
    board = request.args.get("board", "adventurer")
    keys = [b[0] for b in gameapi.BOARDS]
    if board not in keys:
        board = "adventurer"
    data, problem = gameapi.leaderboards()
    rows, players = [], 0
    if data:
        entry = (data.get("boards") or {}).get(board) or {}
        rows = [r for r in entry.get("top", []) if isinstance(r, dict) and gameapi.NAME_RE.match(str(r.get("name", "")))]
        players = entry.get("players", 0)
    return render_template("community/leaderboards.html", boards=gameapi.BOARDS, board=board,
                           board_info=dict((b[0], b) for b in gameapi.BOARDS)[board], rows=rows,
                           players=players, updated=data.get("updatedAt") if data else None, problem=problem)


@bp.route("/players")
def player_search():
    name = request.args.get("name", "").strip()
    if gameapi.NAME_RE.match(name):
        return redirect(url_for("community.player_profile", name=name))
    if name:
        flash("Minecraft names are 3 to 16 letters, numbers or underscores.", "error")
    return redirect(url_for("community.leaderboards"))


@bp.route("/players/<name>")
def player_profile(name):
    if not gameapi.NAME_RE.match(name):
        abort(404)
    data, problem = gameapi.player(name)
    if problem == "not_found":
        return render_template("community/player_missing.html", name=name), 404
    member = get_db().execute("SELECT username FROM members WHERE mc_name = ? COLLATE NOCASE AND status = 'active'",
                              (name,)).fetchone()
    skills = []
    if data:
        levels = data.get("skills") or {}
        skills = [(s, int(levels.get(s, 0) or 0)) for s in gameapi.SKILLS]
    return render_template("community/player.html", name=name, data=data, problem=problem, skills=skills,
                           max_level=gameapi.MAX_SKILL_LEVEL, boards=gameapi.BOARDS, member=member)


# --- Forum ----------------------------------------------------------------------------------

def _forum_on():
    if not _settings_flag("forum_enabled"):
        abort(404)


def _can_post(member, category=None, thread=None):
    """Returns None when allowed, otherwise the reason to show."""
    if member is None:
        return "Sign in to post."
    if member["status"] != "active":
        return "Your account can't post right now."
    if _settings_flag("forum_require_link") and not member["mc_uuid"]:
        return "Link your Minecraft account before posting."
    if category is not None and category["staff_only"] and not _can_announce(member):
        return "Only the Aerborne team posts here."
    if thread is not None and thread["locked"] and not (_is_mod(member) or _staff_mod()):
        return "This thread is locked."
    return None


def _slug(title):
    return re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")[:60] or "thread"


@bp.app_template_global()
def thread_url(thread, page=None, anchor=None):
    url = url_for("community.thread", thread_id=thread["id"], slug=_slug(thread["title"]), page=page)
    return url + ("#" + anchor if anchor else "")


@bp.route("/forum")
def forum():
    _forum_on()
    conn = get_db()
    categories = conn.execute(
        "SELECT c.*, "
        "(SELECT COUNT(*) FROM forum_threads t WHERE t.category_id = c.id AND t.hidden = 0) AS thread_count, "
        "(SELECT COUNT(*) FROM forum_posts p JOIN forum_threads t ON t.id = p.thread_id "
        " WHERE t.category_id = c.id AND t.hidden = 0 AND p.hidden = 0) AS post_count "
        "FROM forum_categories c ORDER BY c.sort_order, c.id").fetchall()
    latest = {}
    for cat in categories:
        if cat["private"]:
            continue
        latest[cat["id"]] = conn.execute(
            "SELECT t.*, m.username AS last_username, m.avatar AS last_avatar FROM forum_threads t "
            "LEFT JOIN members m ON m.id = t.last_member_id "
            "WHERE t.category_id = ? AND t.hidden = 0 ORDER BY t.last_post_at DESC LIMIT 1", (cat["id"],)).fetchone()
    grouped = [(key, title, blurb, [c for c in categories if c["section"] == key]) for key, title, blurb in SECTIONS]
    grouped += [("other", "More", "", [c for c in categories if c["section"] not in dict((s[0], 1) for s in SECTIONS)])]
    grouped = [g_ for g_ in grouped if g_[3]]
    since = (utcnow() - datetime.timedelta(minutes=15)).strftime("%Y-%m-%d %H:%M:%S")
    online = conn.execute(
        "SELECT m.username, r.slug AS rank_slug, r.staff AS rank_staff FROM members m LEFT JOIN ranks r ON r.id = m.rank_id "
        "WHERE m.last_seen_at >= ? AND m.status = 'active' ORDER BY COALESCE(r.priority, 0) DESC, m.username LIMIT 60",
        (since,)).fetchall()
    stats = {
        "threads": conn.execute("SELECT COUNT(*) FROM forum_threads t JOIN forum_categories c ON c.id = t.category_id "
                                "WHERE t.hidden = 0 AND c.private = 0").fetchone()[0],
        "posts": conn.execute("SELECT COUNT(*) FROM forum_posts p JOIN forum_threads t ON t.id = p.thread_id "
                              "JOIN forum_categories c ON c.id = t.category_id WHERE p.hidden = 0 AND t.hidden = 0 "
                              "AND c.private = 0").fetchone()[0],
        "members": conn.execute("SELECT COUNT(*) FROM members WHERE status = 'active'").fetchone()[0],
        "newest": conn.execute("SELECT username FROM members WHERE status = 'active' ORDER BY id DESC LIMIT 1").fetchone(),
    }
    recent = conn.execute(
        "SELECT t.*, c.slug AS category_slug, c.name AS category_name, m.username AS last_username, m.avatar AS last_avatar "
        "FROM forum_threads t JOIN forum_categories c ON c.id = t.category_id LEFT JOIN members m ON m.id = t.last_member_id "
        "WHERE t.hidden = 0 AND c.private = 0 ORDER BY t.last_post_at DESC LIMIT 6").fetchall()
    return render_template("community/forum.html", grouped=grouped, latest=latest, recent=recent, online=online,
                           stats=stats)


def _category(slug):
    return get_db().execute("SELECT * FROM forum_categories WHERE slug = ?", (slug,)).fetchone() or abort(404)


@bp.route("/forum/<category_slug>")
def category(category_slug):
    _forum_on()
    cat = _category(category_slug)
    conn = get_db()
    where, args = "t.category_id = ? AND t.hidden = 0", [cat["id"]]
    if cat["private"] and not can_moderate():
        where += " AND t.member_id = ?"
        args.append(g.member["id"] if g.member else -1)
    total = conn.execute("SELECT COUNT(*) FROM forum_threads t WHERE " + where, args).fetchone()[0]
    pages = max(1, int(math.ceil(total / float(THREADS_PER_PAGE))))
    page = min(max(1, request.args.get("page", 1, type=int)), pages)
    threads = conn.execute(
        "SELECT t.*, a.username AS author, a.avatar AS author_avatar, ar.slug AS author_rank, "
        "l.username AS last_username, l.avatar AS last_avatar FROM forum_threads t "
        "JOIN members a ON a.id = t.member_id LEFT JOIN ranks ar ON ar.id = a.rank_id "
        "LEFT JOIN members l ON l.id = t.last_member_id "
        "WHERE " + where + " ORDER BY t.pinned DESC, t.last_post_at DESC LIMIT ? OFFSET ?",
        args + [THREADS_PER_PAGE, (page - 1) * THREADS_PER_PAGE]).fetchall()
    return render_template("community/category.html", cat=cat, threads=threads, page=page, pages=pages,
                           post_block=_can_post(g.member, cat))


@bp.route("/forum/<category_slug>/new", methods=["GET", "POST"])
@member_required
def new_thread(category_slug):
    _forum_on()
    cat = _category(category_slug)
    block = _can_post(g.member, cat)
    form = {"title": "", "body": "", "prefix": ""}
    error = None
    if request.method == "POST" and not block:
        conn = get_db()
        form = {"title": request.form.get("title", "").strip(), "body": request.form.get("body", "").strip(),
                "prefix": request.form.get("prefix", "")}
        if form["prefix"] and form["prefix"] not in dict(prefixes_for(cat["slug"])):
            error = "Pick a tag from the list."
        elif not 4 <= len(form["title"]) <= MAX_TITLE:
            error = "Give the thread a title between 4 and %d characters." % MAX_TITLE
        elif not 2 <= len(form["body"]) <= MAX_POST:
            error = "Write between 2 and %d characters." % MAX_POST
        elif not allow(conn, "thread", g.member["id"], 3, 600):
            conn.commit()
            error = "You've started a few threads already. Wait ten minutes before starting another."
        else:
            now = now_str()
            cur = conn.execute(
                "INSERT INTO forum_threads (category_id, member_id, title, prefix, created_at, last_post_at, last_member_id) "
                "VALUES (?, ?, ?, ?, ?, ?, ?)", (cat["id"], g.member["id"], form["title"], form["prefix"], now, now, g.member["id"]))
            thread_id = cur.lastrowid
            conn.execute("INSERT INTO forum_posts (thread_id, member_id, body, created_at) VALUES (?, ?, ?, ?)",
                         (thread_id, g.member["id"], form["body"], now))
            conn.execute("UPDATE members SET post_count = post_count + 1 WHERE id = ?", (g.member["id"],))
            conn.commit()
            thread_row = conn.execute("SELECT * FROM forum_threads WHERE id = ?", (thread_id,)).fetchone()
            return redirect(thread_url(thread_row))
    return render_template("community/new_thread.html", cat=cat, block=block, form=form, error=error,
                           max_post=MAX_POST, max_title=MAX_TITLE, prefixes=prefixes_for(cat["slug"]))


def _thread(thread_id):
    row = get_db().execute(
        "SELECT t.*, c.slug AS category_slug, c.name AS category_name, c.staff_only, c.private FROM forum_threads t "
        "JOIN forum_categories c ON c.id = t.category_id WHERE t.id = ?", (thread_id,)).fetchone()
    if row is None or (row["hidden"] and not can_moderate()):
        abort(404)
    if row["private"] and not can_moderate() and not (g.get("member") and g.member["id"] == row["member_id"]):
        abort(404)
    return row


@bp.route("/forum/thread/<int:thread_id>")
@bp.route("/forum/thread/<int:thread_id>/<slug>")
def thread(thread_id, slug=None):
    _forum_on()
    t = _thread(thread_id)
    conn = get_db()
    mod = can_moderate()
    visible = "" if mod else " AND p.hidden = 0"
    total = conn.execute("SELECT COUNT(*) FROM forum_posts p WHERE p.thread_id = ?" + visible, (t["id"],)).fetchone()[0]
    pages = max(1, int(math.ceil(total / float(POSTS_PER_PAGE))))
    page = request.args.get("page", 1, type=int)
    if page == -1:
        page = pages
    page = min(max(1, page), pages)
    posts = conn.execute(
        "SELECT p.*, " + AUTHOR_COLUMNS + " FROM forum_posts p JOIN members m ON m.id = p.member_id "
        "LEFT JOIN ranks r ON r.id = m.rank_id WHERE p.thread_id = ?" + visible +
        " ORDER BY p.id LIMIT ? OFFSET ?", (t["id"], POSTS_PER_PAGE, (page - 1) * POSTS_PER_PAGE)).fetchall()
    first_post_id = conn.execute("SELECT MIN(id) FROM forum_posts WHERE thread_id = ?", (t["id"],)).fetchone()[0]
    liked = set()
    if g.member and posts:
        ids = [p["id"] for p in posts]
        liked = {r[0] for r in conn.execute(
            "SELECT post_id FROM post_likes WHERE member_id = ? AND post_id IN (%s)" % ",".join("?" * len(ids)),
            [g.member["id"]] + ids)}
    blocked = set()
    if g.member:
        from .social import blocked_ids
        blocked = blocked_ids(conn, g.member["id"])
    if not (g.member and g.member["id"] == t["member_id"]):
        conn.execute("UPDATE forum_threads SET view_count = view_count + 1 WHERE id = ?", (t["id"],))
        conn.commit()
    return render_template("community/thread.html", thread=t, posts=posts, page=page, pages=pages,
                           reply_block=_can_post(g.member, None, t), max_post=MAX_POST, first_post_id=first_post_id,
                           liked=liked, number_from=(page - 1) * POSTS_PER_PAGE, blocked=blocked)


@bp.route("/forum/thread/<int:thread_id>/reply", methods=["POST"])
@member_required
def reply(thread_id):
    _forum_on()
    t = _thread(thread_id)
    block = _can_post(g.member, None, t)
    if block:
        flash(block, "error")
        return redirect(thread_url(t))
    body = request.form.get("body", "").strip()
    conn = get_db()
    if not 2 <= len(body) <= MAX_POST:
        flash("Write between 2 and %d characters." % MAX_POST, "error")
        return redirect(thread_url(t, page=-1, anchor="reply"))
    if not allow(conn, "reply-burst", g.member["id"], 1, 20) or not allow(conn, "reply", g.member["id"], 40, 3600):
        conn.commit()
        flash("You're posting very quickly. Wait a moment and try again.", "error")
        return redirect(thread_url(t, page=-1, anchor="reply"))
    now = now_str()
    cur = conn.execute("INSERT INTO forum_posts (thread_id, member_id, body, created_at) VALUES (?, ?, ?, ?)",
                       (t["id"], g.member["id"], body, now))
    conn.execute("UPDATE forum_threads SET reply_count = reply_count + 1, last_post_at = ?, last_member_id = ? WHERE id = ?",
                 (now, g.member["id"], t["id"]))
    conn.execute("UPDATE members SET post_count = post_count + 1 WHERE id = ?", (g.member["id"],))
    from .social import notify
    notify(conn, t["member_id"], "reply", g.member["id"], thread_url(t, page=-1, anchor="post-%d" % cur.lastrowid),
           t["title"], collapse=True)
    conn.commit()
    return redirect(thread_url(t, page=-1, anchor="post-%d" % cur.lastrowid))


def _post(post_id):
    row = get_db().execute("SELECT * FROM forum_posts WHERE id = ?", (post_id,)).fetchone() or abort(404)
    return row, _thread(row["thread_id"])


@bp.route("/forum/post/<int:post_id>/edit", methods=["GET", "POST"])
@member_required
def edit_post(post_id):
    _forum_on()
    post, t = _post(post_id)
    if post["member_id"] != g.member["id"] or post["hidden"] or g.member["status"] != "active":
        abort(403)
    if t["locked"] and not can_moderate():
        flash("This thread is locked.", "error")
        return redirect(thread_url(t))
    error = None
    body = post["body"]
    if request.method == "POST":
        body = request.form.get("body", "").strip()
        if not 2 <= len(body) <= MAX_POST:
            error = "Write between 2 and %d characters." % MAX_POST
        else:
            conn = get_db()
            conn.execute("UPDATE forum_posts SET body = ?, edited_at = ? WHERE id = ?", (body, now_str(), post_id))
            conn.commit()
            return redirect(thread_url(t, page=-1, anchor="post-%d" % post_id))
    return render_template("community/edit_post.html", post=post, thread=t, body=body, error=error, max_post=MAX_POST)


@bp.route("/forum/post/<int:post_id>/delete", methods=["POST"])
@member_required
def delete_post(post_id):
    post, t = _post(post_id)
    if post["member_id"] != g.member["id"]:
        abort(403)
    conn = get_db()
    conn.execute("UPDATE forum_posts SET hidden = 1, hidden_reason = 'Removed by its author' WHERE id = ?", (post_id,))
    if conn.execute("SELECT MIN(id) FROM forum_posts WHERE thread_id = ?", (t["id"],)).fetchone()[0] == post_id:
        conn.execute("UPDATE forum_threads SET hidden = 1 WHERE id = ?", (t["id"],))
    conn.commit()
    flash("Post removed.")
    return redirect(url_for("community.category", category_slug=t["category_slug"]))


@bp.route("/forum/post/<int:post_id>/report", methods=["POST"])
@member_required
def report_post(post_id):
    post, t = _post(post_id)
    reason = request.form.get("reason", "").strip()[:300] or "No reason given"
    conn = get_db()
    if not allow(conn, "report", g.member["id"], 10, 3600):
        conn.commit()
        flash("You've sent a lot of reports. Staff will get to them; try again later.", "error")
    elif conn.execute("SELECT 1 FROM forum_reports WHERE post_id = ? AND member_id = ? AND resolved_at IS NULL",
                      (post_id, g.member["id"])).fetchone():
        flash("You've already reported that post.")
    else:
        conn.execute("INSERT INTO forum_reports (post_id, member_id, reason, created_at) VALUES (?, ?, ?, ?)",
                     (post_id, g.member["id"], reason, now_str()))
        conn.commit()
        flash("Thanks. Staff will review that post.")
    return redirect(thread_url(t, anchor="post-%d" % post_id))


@bp.route("/forum/post/<int:post_id>/moderate", methods=["POST"])
def moderate_post(post_id):
    if not can_moderate():
        abort(403)
    post, t = _post(post_id)
    hide = request.form.get("action") == "hide"
    conn = get_db()
    conn.execute("UPDATE forum_posts SET hidden = ?, hidden_reason = ? WHERE id = ?",
                 (1 if hide else 0, "Hidden by a moderator" if hide else "", post_id))
    who = _mod_actor()
    audit(conn, who, "hide" if hide else "unhide",
          "forum_posts", post_id, "%s a post in '%s'" % ("Hid" if hide else "Restored", t["title"]))
    conn.commit()
    return redirect(thread_url(t, anchor="post-%d" % post_id))


@bp.route("/forum/thread/<int:thread_id>/moderate", methods=["POST"])
def moderate_thread(thread_id):
    if not can_moderate():
        abort(403)
    t = _thread(thread_id)
    action = request.form.get("action")
    changes = {"pin": ("pinned", 1), "unpin": ("pinned", 0), "lock": ("locked", 1), "unlock": ("locked", 0),
               "hide": ("hidden", 1), "unhide": ("hidden", 0)}
    if action not in changes:
        abort(400)
    column, value = changes[action]
    conn = get_db()
    conn.execute("UPDATE forum_threads SET %s = ? WHERE id = ?" % column, (value, t["id"]))
    audit(conn, _mod_actor(), action, "forum_threads", t["id"],
          "%s thread '%s'" % (action.capitalize(), t["title"]))
    conn.commit()
    return redirect(thread_url(t))



@bp.route("/forum/post/<int:post_id>/like", methods=["POST"])
@member_required
def like_post(post_id):
    post, t = _post(post_id)
    conn = get_db()
    if post["member_id"] == g.member["id"] or post["hidden"]:
        abort(403)
    if g.member["status"] != "active":
        abort(403)
    existing = conn.execute("SELECT 1 FROM post_likes WHERE post_id = ? AND member_id = ?", (post_id, g.member["id"])).fetchone()
    if existing:
        conn.execute("DELETE FROM post_likes WHERE post_id = ? AND member_id = ?", (post_id, g.member["id"]))
        conn.execute("UPDATE forum_posts SET like_count = MAX(0, like_count - 1) WHERE id = ?", (post_id,))
        conn.execute("UPDATE members SET likes_received = MAX(0, likes_received - 1) WHERE id = ?", (post["member_id"],))
    elif allow(conn, "like", g.member["id"], 120, 3600):
        conn.execute("INSERT INTO post_likes (post_id, member_id, created_at) VALUES (?, ?, ?)", (post_id, g.member["id"], now_str()))
        conn.execute("UPDATE forum_posts SET like_count = like_count + 1 WHERE id = ?", (post_id,))
        conn.execute("UPDATE members SET likes_received = likes_received + 1 WHERE id = ?", (post["member_id"],))
        from .social import notify
        notify(conn, post["member_id"], "like", g.member["id"], thread_url(t, anchor="post-%d" % post_id), t["title"], collapse=True)
    conn.commit()
    return redirect(thread_url(t, page=request.form.get("page", type=int), anchor="post-%d" % post_id))
