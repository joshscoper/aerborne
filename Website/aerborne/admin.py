"""Staff admin: content editing, publishing workflow, media, accounts, settings and audit."""
import json
import os

from flask import (Blueprint, abort, current_app, flash, g, redirect, render_template, request,
                   url_for)

from . import auth
from .content_types import SLUG_RE, STATUSES, TYPES, get_type
from .db import audit, get_db, get_settings, now_str, parse_time, set_setting
from .media import UploadError, delete_files, save_upload
from .security import check_csrf, clear_failed_logins, login_blocked, record_failed_login

bp = Blueprint("admin", __name__, url_prefix="/admin")

LIMITS = {"text": 300, "slug": 80, "url": 500, "image": 300, "select": 60, "textarea": 3000,
          "lines": 5000, "markdown": 100000, "multiselect": 300}

SETTINGS_FIELDS = [
    ("announcement_text", "Announcement banner", "text",
     "Shown across the top of every page. Leave blank to hide."),
    ("announcement_link", "Announcement link", "text", "A path on this site, like /news/patch-1-2."),
    ("maintenance", "Maintenance mode", "bool",
     "Marks the server as under maintenance on the status card and Play page."),
    ("maintenance_message", "Maintenance message", "text", "For example: Back by 18:00 UTC."),
    ("discord_invite", "Discord invite link", "text",
     "Paste an invite like https://discord.gg/abc123. Every Discord button on the site uses it; blank shows \"coming soon\"."),
    ("community_intro", "Community page intro", "textarea", "A short paragraph above the builds."),
    ("arcade_enabled", "Arcade is open", "bool", "Untick to close the Arcade."),
    ("arcade_daily_cap", "Arcade Marks per player per day", "text", "Default 1000. Set 0 to keep the games but pay nothing."),
    ("fun_cursor", "Sparkly cursor", "bool",
     "A gold star cursor with a little sparkle trail on the public site. Never shown on touch screens or to visitors who prefer reduced motion (they get the cursor without the trail)."),
    ("forum_enabled", "Forum is open", "bool", "Untick to hide the forum while keeping everything in it."),
    ("registrations_open", "Players can create accounts", "bool", "Untick to pause new sign-ups."),
    ("messages_enabled", "Members can send direct messages", "bool",
     "Untick to pause new messages. Friends, blocks and existing inboxes keep working."),
    ("forum_require_link", "Posting and messaging need a linked Minecraft account", "bool",
     "Recommended. Linking proves who's posting and keeps spam bots out."),
    ("extra_links", "Extra footer links", "textarea",
     "One per line as Label | URL. Use this for social profiles or a status page."),
]


@bp.before_request
def _guard():
    check_csrf()


@bp.after_request
def _no_store(response):
    response.headers["Cache-Control"] = "no-store"
    response.headers["X-Robots-Tag"] = "noindex, nofollow"
    return response


@bp.app_template_global()
def admin_types():
    user = g.get("user")
    return [t for t in TYPES if auth.can_edit_type(user, t)] if user else []


def _safe_next(target):
    if target and target.startswith("/") and not target.startswith("//") and "\\" not in target:
        return target
    return url_for("admin.dashboard")


# --- Sign in ---------------------------------------------------------------------------------

@bp.route("/login", methods=["GET", "POST"])
def login():
    conn = get_db()
    if conn.execute("SELECT COUNT(*) FROM users").fetchone()[0] == 0:
        return redirect(url_for("admin.setup"))
    error = None
    if request.method == "POST":
        username = request.form.get("username", "").strip()[:64]
        password = request.form.get("password", "")
        if login_blocked(conn, username):
            error = "Too many sign-in attempts. Wait 15 minutes and try again."
        else:
            user = conn.execute("SELECT * FROM users WHERE username = ?", (username,)).fetchone()
            if user and user["active"] and auth.verify_password(user, password):
                clear_failed_logins(conn, username)
                return complete_staff_login(conn, user, password, request.args.get("next"))
            record_failed_login(conn, username)
            conn.commit()
            error = "That username and password don't match an active staff account."
    return render_template("admin/login.html", error=error)


def complete_staff_login(conn, user, password, next_path=None):
    """Shared by /admin/login and the site's main sign-in form once a staff password checks out."""
    conn.execute("UPDATE users SET last_login_at = ? WHERE id = ?", (now_str(), user["id"]))
    if auth.needs_rehash(user["password_hash"]):
        # Older, weaker hashes are upgraded while we have the password in hand.
        conn.execute("UPDATE users SET password_hash = ? WHERE id = ?", (auth.hash_password(password), user["id"]))
    audit(conn, user, "login", "users", user["id"], "Signed in")
    conn.commit()
    auth.start_session(user)
    if user["must_change_password"]:
        flash("Choose a new password to finish signing in. The default password can't be kept.", "error")
        return redirect(url_for("admin.account"))
    return redirect(_safe_next(next_path))


@bp.route("/logout", methods=["POST"])
def logout():
    from flask import session
    session.clear()
    return redirect(url_for("admin.login"))


@bp.route("/setup", methods=["GET", "POST"])
def setup():
    """Create the first admin. Only works while there are no accounts and a setup token is set."""
    conn = get_db()
    if conn.execute("SELECT COUNT(*) FROM users").fetchone()[0] > 0:
        abort(404)
    token = current_app.config.get("SETUP_TOKEN")
    error = None
    if request.method == "POST" and token:
        import hmac
        username = request.form.get("username", "").strip()
        password = request.form.get("password", "")
        if not hmac.compare_digest(request.form.get("token", ""), token):
            error = "That setup token doesn't match AERBORNE_SETUP_TOKEN."
        elif not SLUG_RE.match(username.lower().replace("_", "-")) or len(username) > 32:
            error = "Use 2 to 32 letters, numbers, dashes or underscores for the username."
        elif auth.password_problem(password):
            error = auth.password_problem(password)
        else:
            cur = conn.execute(
                "INSERT INTO users (username, display_name, password_hash, role, created_at) VALUES (?, ?, ?, 'admin', ?)",
                (username, username, auth.hash_password(password), now_str()))
            user = conn.execute("SELECT * FROM users WHERE id = ?", (cur.lastrowid,)).fetchone()
            audit(conn, user, "create", "users", user["id"], "Created the first admin account")
            conn.commit()
            auth.start_session(user)
            flash("Your admin account is ready. Remove AERBORNE_SETUP_TOKEN from the app settings now.")
            return redirect(url_for("admin.dashboard"))
    return render_template("admin/setup.html", error=error, token_set=bool(token))


# --- Dashboard -------------------------------------------------------------------------------

@bp.route("/")
@auth.login_required
def dashboard():
    conn = get_db()
    counts = []
    queue = []
    for ctype in admin_types():
        row = {s: 0 for s, _ in STATUSES}
        for r in conn.execute("SELECT status, COUNT(*) AS n FROM %s GROUP BY status" % ctype.table):
            row[r["status"]] = r["n"]
        counts.append((ctype, row))
        for item in conn.execute("SELECT * FROM %s WHERE status = 'review' ORDER BY updated_at" % ctype.table):
            queue.append((ctype, item))
    scheduled = []
    for ctype in admin_types():
        for item in conn.execute(
                "SELECT * FROM %s WHERE status = 'published' AND publish_at > ? ORDER BY publish_at" % ctype.table,
                (now_str(),)):
            scheduled.append((ctype, item))
    views = conn.execute(
        "SELECT path, SUM(views) AS n FROM page_views WHERE day >= date('now', '-6 days') "
        "GROUP BY path ORDER BY n DESC LIMIT 10").fetchall()
    recent = conn.execute("SELECT * FROM audit_log ORDER BY id DESC LIMIT 12").fetchall()
    return render_template("admin/dashboard.html", counts=counts, queue=queue, scheduled=scheduled,
                           views=views, recent=recent)


# --- Content ---------------------------------------------------------------------------------

def _type_or_404(key):
    ctype = get_type(key)
    if ctype is None:
        abort(404)
    if not auth.can_edit_type(g.user, ctype):
        abort(403)
    return ctype


@bp.route("/content/<key>")
@auth.login_required
def content_list(key):
    ctype = _type_or_404(key)
    status = request.args.get("status", "")
    q = request.args.get("q", "").strip()
    sql = "SELECT * FROM %s WHERE 1 = 1" % ctype.table
    args = []
    if status:
        sql += " AND status = ?"
        args.append(status)
    if q:
        sql += " AND %s LIKE ?" % ctype.title_field
        args.append("%" + q + "%")
    sql += " ORDER BY " + ctype.order_by
    items = get_db().execute(sql, args).fetchall()
    return render_template("admin/list.html", ctype=ctype, items=items, status=status, q=q,
                           statuses=STATUSES, now=now_str())


def _builtin_images():
    root = os.path.join(current_app.static_folder, "img")
    found = []
    for folder, _dirs, files in os.walk(root):
        for name in sorted(files):
            if name.endswith((".webp", ".svg", ".png", ".jpg")):
                rel = os.path.relpath(os.path.join(folder, name), current_app.static_folder)
                found.append("static:" + rel.replace(os.sep, "/"))
    return sorted(found)


def _image_choices():
    media = get_db().execute("SELECT filename, original_name, alt FROM media ORDER BY id DESC").fetchall()
    return [("media:" + m["filename"], m["alt"] or m["original_name"]) for m in media], _builtin_images()


def _valid_image(value):
    if not value:
        return True
    if value.startswith("static:"):
        return value in _builtin_images()
    if value.startswith("media:"):
        return get_db().execute("SELECT 1 FROM media WHERE filename = ?",
                                (value[len("media:"):],)).fetchone() is not None
    return False


def _read_form(ctype, item):
    """Return (values, errors) from the submitted form."""
    values, errors = {}, {}
    form = request.form
    for f in ctype.fields:
        if f.kind == "bool":
            values[f.name] = 1 if form.get(f.name) else 0
            continue
        if f.kind == "multiselect":
            chosen = [v for v in form.getlist(f.name) if v in dict(f.options)]
            values[f.name] = ",".join(chosen)
            if f.required and not chosen:
                errors[f.name] = "Pick at least one."
            continue
        raw = form.get(f.name, "").replace("\r\n", "\n").strip()
        if len(raw) > LIMITS.get(f.kind, 300):
            errors[f.name] = "That's too long. Keep it under %d characters." % LIMITS.get(f.kind, 300)
        if f.kind == "int":
            if raw == "":
                values[f.name] = None
            else:
                try:
                    values[f.name] = int(raw)
                except ValueError:
                    errors[f.name] = "Enter a whole number."
                    values[f.name] = raw
            if f.required and raw == "":
                errors[f.name] = "This is required."
            continue
        values[f.name] = raw
        if f.required and not raw:
            errors[f.name] = "This is required."
        elif f.kind == "slug" and raw and not SLUG_RE.match(raw):
            errors[f.name] = "Use lowercase letters, numbers and single dashes, like first-island."
        elif f.kind == "select" and raw and raw not in dict(f.options):
            errors[f.name] = "Pick one of the options."
        elif f.kind == "url" and raw and not raw.startswith("https://"):
            errors[f.name] = "Use a full https:// link."
        elif f.kind == "image" and not _valid_image(raw):
            errors[f.name] = "Pick an image from the list."

    slug = values.get("slug")
    if slug and "slug" not in errors:
        clash = get_db().execute("SELECT id FROM %s WHERE slug = ?" % ctype.table, (slug,)).fetchone()
        if clash and (item is None or clash["id"] != item["id"]):
            errors["slug"] = "Another %s already uses this slug." % ctype.singular

    status = form.get("status", "draft")
    if status not in auth.allowed_statuses(g.user, ctype):
        errors["status"] = "Your role can't set that status."
    values["status"] = status

    publish_at = form.get("publish_at", "").strip()
    if publish_at:
        parsed = parse_time(publish_at)
        if not parsed:
            errors["publish_at"] = "Use a date and time."
        values["publish_at"] = parsed.strftime("%Y-%m-%d %H:%M:%S") if parsed else publish_at
    else:
        values["publish_at"] = None

    try:
        values["sort_order"] = int(form.get("sort_order", "0") or 0)
    except ValueError:
        errors["sort_order"] = "Enter a whole number."
        values["sort_order"] = 0

    if status == "published":
        errors.update(_publish_checks(ctype, values))
    return values, errors


def _publish_checks(ctype, values):
    """Rules that only apply once something goes public."""
    errors = {}
    if ctype.key == "showcases" and not values.get("creator_permission"):
        errors["creator_permission"] = "Confirm the builder's permission before publishing."
    if ctype.key == "products" and values.get("availability") == "released":
        if not values.get("price"):
            errors["price"] = "A released store item needs its exact price."
        if not values.get("tebex_url"):
            errors["tebex_url"] = "A released store item needs its Tebex link."
        terms = get_db().execute("SELECT status FROM pages WHERE slug = 'store-terms'").fetchone()
        if not terms or terms["status"] != "published":
            errors["availability"] = "Publish the store terms page (slug store-terms) before selling anything."
    if ctype.field("image_alt") and values.get("image") and not values.get("image_alt"):
        errors["image_alt"] = "Describe the image before publishing."
    return errors


def _changes(ctype, old, new):
    changed = {}
    for key, value in new.items():
        before = old[key] if old is not None and key in old.keys() else None
        if (before or "") != (value or "") and not (before in (0, None, "") and value in (0, None, "")):
            f = ctype.field(key)
            if f and f.kind == "markdown":
                changed[key] = ["%d chars" % len(before or ""), "%d chars" % len(value or "")]
            else:
                changed[key] = [str(before or "")[:200], str(value or "")[:200]]
    return changed


@bp.route("/content/<key>/new", methods=["GET", "POST"])
@bp.route("/content/<key>/<int:item_id>", methods=["GET", "POST"])
@auth.login_required
def content_edit(key, item_id=None):
    ctype = _type_or_404(key)
    conn = get_db()
    item = None
    if item_id is not None:
        item = conn.execute("SELECT * FROM %s WHERE id = ?" % ctype.table, (item_id,)).fetchone()
        if item is None:
            abort(404)
    if not auth.can_edit_item(g.user, ctype, item):
        abort(403)

    errors = {}
    values = dict(item) if item else {f.name: f.default for f in ctype.fields}
    if item is None:
        values.update(status="draft", sort_order=0, publish_at=None)

    if request.method == "POST":
        values, errors = _read_form(ctype, item)
        if not errors:
            values["updated_at"] = now_str()
            values["updated_by"] = g.user["username"]
            changes = _changes(ctype, item, values)
            if item is None:
                values["created_at"] = now_str()
                cols = list(values.keys())
                cur = conn.execute(
                    "INSERT INTO %s (%s) VALUES (%s)" % (ctype.table, ", ".join(cols), ", ".join("?" * len(cols))),
                    [values[c] for c in cols])
                new_id = cur.lastrowid
                audit(conn, g.user, "create", ctype.key, new_id,
                      "Created %s '%s' as %s" % (ctype.singular, values[ctype.title_field], values["status"]), changes)
            else:
                new_id = item["id"]
                cols = list(values.keys())
                conn.execute("UPDATE %s SET %s WHERE id = ?" % (ctype.table, ", ".join(c + " = ?" for c in cols)),
                             [values[c] for c in cols] + [new_id])
                action = "publish" if values["status"] == "published" and item["status"] != "published" else "update"
                audit(conn, g.user, action, ctype.key, new_id,
                      "%s %s '%s'" % ("Published" if action == "publish" else "Updated", ctype.singular,
                                      values[ctype.title_field]), changes)
            conn.commit()
            flash("Saved %s." % values[ctype.title_field])
            return redirect(url_for("admin.content_edit", key=key, item_id=new_id))
        flash("Some fields need attention.", "error")

    media_choices, builtin_choices = _image_choices()
    return render_template("admin/edit.html", ctype=ctype, item=item, values=values, errors=errors,
                           statuses=[s for s in STATUSES if s[0] in auth.allowed_statuses(g.user, ctype)],
                           media_choices=media_choices, builtin_choices=builtin_choices,
                           preview_url=_preview_url(ctype, item))


def _preview_url(ctype, item):
    from .public import PREVIEW_RENDERERS
    if item is None:
        return None
    if ctype.key in PREVIEW_RENDERERS:
        return url_for("admin.content_preview", key=ctype.key, item_id=item["id"])
    return None


@bp.route("/content/<key>/<int:item_id>/preview")
@auth.login_required
def content_preview(key, item_id):
    from .public import PREVIEW_RENDERERS
    ctype = _type_or_404(key)
    renderer = PREVIEW_RENDERERS.get(key) or abort(404)
    item = get_db().execute("SELECT * FROM %s WHERE id = ?" % ctype.table, (item_id,)).fetchone() or abort(404)
    return renderer(item, preview=True)


@bp.route("/content/<key>/<int:item_id>/delete", methods=["POST"])
@auth.role_required("admin", "editor")
def content_delete(key, item_id):
    ctype = _type_or_404(key)
    conn = get_db()
    item = conn.execute("SELECT * FROM %s WHERE id = ?" % ctype.table, (item_id,)).fetchone() or abort(404)
    snapshot = {k: item[k] for k in item.keys() if ctype.field(k) is None or ctype.field(k).kind != "markdown"}
    conn.execute("DELETE FROM %s WHERE id = ?" % ctype.table, (item_id,))
    audit(conn, g.user, "delete", ctype.key, item_id,
          "Deleted %s '%s'" % (ctype.singular, item[ctype.title_field]), {"snapshot": snapshot})
    conn.commit()
    flash("Deleted %s. The audit log keeps a copy of its details." % item[ctype.title_field])
    return redirect(url_for("admin.content_list", key=key))


# --- Media -----------------------------------------------------------------------------------

@bp.route("/media", methods=["GET", "POST"])
@auth.role_required("admin", "editor", "writer", "moderator")
def media():
    conn = get_db()
    if request.method == "POST":
        upload = request.files.get("file")
        alt = request.form.get("alt", "").strip()[:300]
        if not upload or not upload.filename:
            flash("Choose an image to upload.", "error")
        elif not alt:
            flash("Describe the image so screen readers can announce it.", "error")
        else:
            try:
                info = save_upload(upload, current_app.config["UPLOAD_DIR"], current_app.config["UPLOAD_MAX_BYTES"])
            except UploadError as exc:
                flash(str(exc), "error")
            else:
                cur = conn.execute(
                    "INSERT INTO media (filename, thumb, original_name, mime, bytes, width, height, alt, uploaded_by, created_at) "
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    (info["filename"], info["thumb"], info["original_name"], info["mime"], info["bytes"],
                     info["width"], info["height"], alt, g.user["username"], now_str()))
                audit(conn, g.user, "upload", "media", cur.lastrowid, "Uploaded %s" % info["original_name"])
                conn.commit()
                flash("Uploaded. Pick it from any image field.")
        return redirect(url_for("admin.media"))
    items = conn.execute("SELECT * FROM media ORDER BY id DESC").fetchall()
    return render_template("admin/media.html", items=items)


@bp.route("/media/<int:media_id>/delete", methods=["POST"])
@auth.role_required("admin", "editor")
def media_delete(media_id):
    conn = get_db()
    row = conn.execute("SELECT * FROM media WHERE id = ?", (media_id,)).fetchone() or abort(404)
    ref = "media:" + row["filename"]
    for ctype in TYPES:
        if ctype.field("image") and conn.execute(
                "SELECT 1 FROM %s WHERE image = ? LIMIT 1" % ctype.table, (ref,)).fetchone():
            flash("That image is still used by a %s. Swap it out first." % ctype.singular, "error")
            return redirect(url_for("admin.media"))
    delete_files(current_app.config["UPLOAD_DIR"], row)
    conn.execute("DELETE FROM media WHERE id = ?", (media_id,))
    audit(conn, g.user, "delete", "media", media_id, "Deleted image %s" % row["original_name"])
    conn.commit()
    flash("Image deleted.")
    return redirect(url_for("admin.media"))


# --- Settings, staff and audit ---------------------------------------------------------------

@bp.route("/settings", methods=["GET", "POST"])
@auth.role_required("admin")
def settings():
    conn = get_db()
    current = get_settings()
    if request.method == "POST":
        changes = {}
        for key, _label, kind, _help in SETTINGS_FIELDS:
            value = ("1" if request.form.get(key) else "0") if kind == "bool" else \
                request.form.get(key, "").replace("\r\n", "\n").strip()[:2000]
            if key == "discord_invite" and value and not (value.startswith("https://discord.gg/") or value.startswith("https://discord.com/invite/")):
                flash("Use a Discord invite link that starts with https://discord.gg/ or https://discord.com/invite/.", "error")
                return redirect(url_for("admin.settings"))
            if key == "arcade_daily_cap" and value and not (value.isdigit() and int(value) <= 5000):
                flash("The Arcade daily limit must be a whole number up to 5000.", "error")
                return redirect(url_for("admin.settings"))
            if key == "announcement_link" and value and not (value.startswith("/") and not value.startswith("//")):
                flash("The announcement link must be a path on this site, starting with /.", "error")
                return redirect(url_for("admin.settings"))
            if current.get(key, "") != value:
                changes[key] = [current.get(key, "")[:200], value[:200]]
            set_setting(conn, key, value)
        if changes:
            audit(conn, g.user, "update", "settings", None, "Changed site settings", changes)
        conn.commit()
        flash("Settings saved.")
        return redirect(url_for("admin.settings"))
    return render_template("admin/settings.html", fields=SETTINGS_FIELDS, current=current)


@bp.route("/users", methods=["GET", "POST"])
@auth.role_required("admin")
def users():
    conn = get_db()
    if request.method == "POST":
        username = request.form.get("username", "").strip()
        role = request.form.get("role", "writer")
        password = request.form.get("password", "")
        problem = auth.password_problem(password)
        if not username or len(username) > 32 or not SLUG_RE.match(username.lower().replace("_", "-")):
            flash("Use 2 to 32 letters, numbers, dashes or underscores for the username.", "error")
        elif role not in auth.ROLE_KEYS:
            flash("Pick a role.", "error")
        elif problem:
            flash(problem, "error")
        elif conn.execute("SELECT 1 FROM users WHERE username = ?", (username,)).fetchone():
            flash("That username is taken.", "error")
        else:
            cur = conn.execute(
                "INSERT INTO users (username, display_name, password_hash, role, created_at) VALUES (?, ?, ?, ?, ?)",
                (username, request.form.get("display_name", "").strip()[:64] or username,
                 auth.hash_password(password), role, now_str()))
            audit(conn, g.user, "create", "users", cur.lastrowid, "Added %s as %s" % (username, role))
            conn.commit()
            flash("Added %s. Share the password with them privately." % username)
        return redirect(url_for("admin.users"))
    rows = conn.execute("SELECT * FROM users ORDER BY active DESC, username").fetchall()
    panel = {row["id"]: auth.panel_status(row) for row in rows}
    return render_template("admin/users.html", rows=rows, roles=auth.ROLES, panel=panel,
                           panel_sync=_panel_sync_state())


@bp.route("/users/<int:user_id>", methods=["POST"])
@auth.role_required("admin")
def user_update(user_id):
    conn = get_db()
    row = conn.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone() or abort(404)
    role = request.form.get("role", row["role"])
    active = 1 if request.form.get("active") else 0
    password = request.form.get("password", "")
    if role not in auth.ROLE_KEYS:
        abort(400)
    admins = conn.execute("SELECT COUNT(*) FROM users WHERE role = 'admin' AND active = 1 AND id != ?",
                          (user_id,)).fetchone()[0]
    if admins == 0 and (role != "admin" or not active):
        flash("Keep at least one active admin.", "error")
        return redirect(url_for("admin.users"))
    if password and auth.password_problem(password):
        flash(auth.password_problem(password), "error")
        return redirect(url_for("admin.users"))
    changes = {}
    if role != row["role"]:
        changes["role"] = [row["role"], role]
    if active != row["active"]:
        changes["active"] = [row["active"], active]
    bump = bool(password) or active != row["active"] or role != row["role"]
    if password:
        changes["password"] = ["", "reset"]
        conn.execute("UPDATE users SET password_hash = ? WHERE id = ?", (auth.hash_password(password), user_id))
    conn.execute("UPDATE users SET role = ?, active = ?, session_version = session_version + ? WHERE id = ?",
                 (role, active, 1 if bump else 0, user_id))
    if changes:
        audit(conn, g.user, "update", "users", user_id, "Changed %s" % row["username"], changes)
    conn.commit()
    flash("Updated %s." % row["username"])
    return redirect(url_for("admin.users"))


@bp.route("/view-as", methods=["POST"])
@auth.login_required
def view_as():
    """Switch the public site between the admin view (staff tools) and what a player sees."""
    from flask import session
    mode = request.form.get("mode")
    if mode not in ("admin", "player"):
        abort(400)
    session["view_as"] = mode
    target = request.form.get("next", "")
    if not (target.startswith("/") and not target.startswith("//") and "\\" not in target) or target.startswith("/admin"):
        target = url_for("public.index")
    return redirect(target)


@bp.route("/api/arcade-payouts")
def arcade_payouts():
    from .arcade import payout_feed
    return payout_feed()


# --- Watchdog panel sync ---------------------------------------------------------------------

def _panel_sync_state():
    settings = get_settings()
    return {
        "configured": bool(current_app.config.get("PANEL_SYNC_TOKEN")),
        "last_fetch": settings.get("panel_last_fetch_at", ""),
        "last_count": settings.get("panel_last_fetch_count", ""),
        "url": current_app.config.get("PANEL_URL", ""),
    }


@bp.route("/api/panel-accounts")
def panel_accounts():
    """Read by the Watchdog panel on a timer. Bearer token only; never linked from any page."""
    import hmac
    from flask import jsonify
    from .security import allow, client_ip
    expected = current_app.config.get("PANEL_SYNC_TOKEN", "")
    if not expected:
        abort(404)
    sent = request.headers.get("Authorization", "")
    conn = get_db()
    if not allow(conn, "panel-sync", client_ip(), 30, 60):
        conn.commit()
        abort(429)
    if not hmac.compare_digest(sent, "Bearer " + expected):
        conn.commit()
        abort(401)
    accounts = []
    for row in conn.execute("SELECT * FROM users WHERE role = 'admin' AND active = 1 ORDER BY username"):
        ok, _reason = auth.panel_status(row)
        if ok and SLUG_RE.match(row["username"].lower().replace("_", "-")) and len(row["username"]) <= 32:
            accounts.append({"username": row["username"], "hash": row["password_hash"]})
    set_setting(conn, "panel_last_fetch_at", now_str())
    set_setting(conn, "panel_last_fetch_count", str(len(accounts)))
    conn.commit()
    return jsonify({"apiVersion": 1, "accounts": accounts})


@bp.route("/account", methods=["GET", "POST"])
@auth.login_required
def account():
    if request.method == "POST":
        conn = get_db()
        current_pw = request.form.get("current", "")
        new_pw = request.form.get("new", "")
        if not auth.verify_password(g.user, current_pw):
            flash("Your current password is wrong.", "error")
        elif auth.password_problem(new_pw):
            flash(auth.password_problem(new_pw), "error")
        else:
            if auth.verify_password(g.user, new_pw):
                flash("Pick a password you haven't used here before.", "error")
                return redirect(url_for("admin.account"))
            conn.execute("UPDATE users SET password_hash = ?, must_change_password = 0, "
                         "session_version = session_version + 1 WHERE id = ?",
                         (auth.hash_password(new_pw), g.user["id"]))
            audit(conn, g.user, "update", "users", g.user["id"], "Changed own password")
            conn.commit()
            user = conn.execute("SELECT * FROM users WHERE id = ?", (g.user["id"],)).fetchone()
            auth.start_session(user)
            flash("Password changed. Other sessions were signed out.")
        return redirect(url_for("admin.account"))
    return render_template("admin/account.html")


@bp.route("/audit")
@auth.role_required("admin", "editor")
def audit_log():
    page = max(1, request.args.get("page", 1, type=int))
    entity = request.args.get("entity", "")
    sql, args = "SELECT * FROM audit_log", []
    if entity:
        sql += " WHERE entity = ?"
        args.append(entity)
    sql += " ORDER BY id DESC LIMIT 50 OFFSET ?"
    args.append((page - 1) * 50)
    rows = []
    for r in get_db().execute(sql, args):
        row = dict(r)
        try:
            row["changes"] = json.loads(r["changes"]) if r["changes"] else {}
        except ValueError:
            row["changes"] = {}
        rows.append(row)
    entities = [t.key for t in TYPES] + ["media", "settings", "users"]
    return render_template("admin/audit.html", rows=rows, page=page, entity=entity, entities=entities)
