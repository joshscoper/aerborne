"""Staff tools for the forum: reports, member roles and bans, and categories."""
from flask import abort, flash, g, redirect, render_template, request, url_for

from . import auth
from .admin import bp
from .community_data import FRAMES, frame_key
from .content_types import SLUG_RE
from .db import audit, get_db, now_str

FORUM_STAFF = ("admin", "moderator")
MEMBER_ROLES = [("member", "Member"), ("moderator", "Moderator: can hide posts and lock threads"),
                ("staff", "Aerborne team: moderator who can post announcements")]


@bp.route("/forum/reports")
@auth.role_required(*FORUM_STAFF)
def forum_reports():
    show = request.args.get("show", "open")
    where = "r.resolved_at IS NULL" if show == "open" else "r.resolved_at IS NOT NULL"
    rows = get_db().execute(
        "SELECT r.*, p.body, p.hidden, p.thread_id, t.title, a.username AS author, m.username AS reporter "
        "FROM forum_reports r JOIN forum_posts p ON p.id = r.post_id JOIN forum_threads t ON t.id = p.thread_id "
        "JOIN members a ON a.id = p.member_id JOIN members m ON m.id = r.member_id "
        "WHERE " + where + " ORDER BY r.id DESC LIMIT 100").fetchall()
    # Only reported messages are ever shown to staff; private conversations stay private otherwise.
    message_rows = get_db().execute(
        "SELECT r.*, x.body, x.created_at AS sent_at, x.sender_deleted, s.username AS sender, m.username AS reporter "
        "FROM message_reports r JOIN messages x ON x.id = r.message_id JOIN members s ON s.id = x.sender_id "
        "JOIN members m ON m.id = r.member_id WHERE " + where + " ORDER BY r.id DESC LIMIT 100").fetchall()
    return render_template("admin/forum_reports.html", rows=rows, message_rows=message_rows, show=show)


@bp.route("/forum/message-reports/<int:report_id>", methods=["POST"])
@auth.role_required(*FORUM_STAFF)
def message_report_action(report_id):
    conn = get_db()
    report = conn.execute("SELECT * FROM message_reports WHERE id = ?", (report_id,)).fetchone() or abort(404)
    action = request.form.get("action")
    if action == "remove":
        conn.execute("UPDATE messages SET sender_deleted = 1, recipient_deleted = 1, read_at = COALESCE(read_at, ?) WHERE id = ?",
                     (now_str(), report["message_id"]))
    elif action != "dismiss":
        abort(400)
    conn.execute("UPDATE message_reports SET resolved_at = ?, resolved_by = ? WHERE message_id = ? AND resolved_at IS NULL",
                 (now_str(), g.user["username"], report["message_id"]))
    audit(conn, g.user, "remove" if action == "remove" else "dismiss", "message_reports", report_id,
          "%s a reported message" % ("Removed" if action == "remove" else "Dismissed reports on"))
    conn.commit()
    flash("Message removed for both members and reports closed." if action == "remove" else "Reports dismissed.")
    return redirect(url_for("admin.forum_reports"))


@bp.route("/forum/reports/<int:report_id>", methods=["POST"])
@auth.role_required(*FORUM_STAFF)
def forum_report_action(report_id):
    conn = get_db()
    report = conn.execute("SELECT * FROM forum_reports WHERE id = ?", (report_id,)).fetchone() or abort(404)
    action = request.form.get("action")
    if action == "hide":
        conn.execute("UPDATE forum_posts SET hidden = 1, hidden_reason = 'Removed by staff' WHERE id = ?", (report["post_id"],))
    elif action != "dismiss":
        abort(400)
    conn.execute("UPDATE forum_reports SET resolved_at = ?, resolved_by = ? WHERE post_id = ? AND resolved_at IS NULL",
                 (now_str(), g.user["username"], report["post_id"]))
    audit(conn, g.user, "hide" if action == "hide" else "dismiss", "forum_reports", report_id,
          "%s a reported forum post" % ("Hid" if action == "hide" else "Dismissed reports on"))
    conn.commit()
    flash("Post hidden and reports closed." if action == "hide" else "Reports dismissed.")
    return redirect(url_for("admin.forum_reports"))


@bp.route("/forum/members")
@auth.role_required(*FORUM_STAFF)
def forum_members():
    q = request.args.get("q", "").strip()
    sql = "SELECT * FROM members WHERE status != 'deleted'"
    args = []
    if q:
        sql += " AND (username LIKE ? OR mc_name LIKE ?)"
        args = ["%" + q + "%", "%" + q + "%"]
    conn = get_db()
    rows = conn.execute(sql + " ORDER BY created_at DESC LIMIT 200", args).fetchall()
    ranks = conn.execute("SELECT * FROM ranks ORDER BY priority DESC, name").fetchall()
    return render_template("admin/forum_members.html", rows=rows, q=q, roles=MEMBER_ROLES, ranks=ranks)


@bp.route("/forum/members/<int:member_id>", methods=["POST"])
@auth.role_required(*FORUM_STAFF)
def forum_member_update(member_id):
    conn = get_db()
    row = conn.execute("SELECT * FROM members WHERE id = ?", (member_id,)).fetchone() or abort(404)
    role = request.form.get("role", row["role"])
    status = "banned" if request.form.get("banned") else "active"
    rank_id = request.form.get("rank_id", type=int) or None
    title = request.form.get("custom_title", "").strip()[:40]
    if rank_id is not None:
        rank = conn.execute("SELECT * FROM ranks WHERE id = ?", (rank_id,)).fetchone()
        if rank is None:
            abort(400)
        if rank["staff"] and g.user["role"] != "admin":
            flash("Only admins can give staff ranks.", "error")
            return redirect(url_for("admin.forum_members"))
    reason = request.form.get("ban_reason", "").strip()[:200]
    if role not in dict(MEMBER_ROLES):
        abort(400)
    if role == "staff" and g.user["role"] != "admin":
        flash("Only admins can give the Aerborne team badge.", "error")
        return redirect(url_for("admin.forum_members"))
    changes = {}
    if role != row["role"]:
        changes["role"] = [row["role"], role]
    if rank_id != row["rank_id"]:
        changes["rank"] = [row["rank_id"], rank_id]
    if title != row["custom_title"]:
        changes["title"] = [row["custom_title"], title]
    if status != row["status"]:
        changes["status"] = [row["status"], status]
    unlink = bool(request.form.get("unlink")) and row["mc_uuid"]
    if unlink:
        changes["minecraft"] = [row["mc_name"], ""]
    conn.execute("UPDATE members SET role = ?, status = ?, ban_reason = ?, rank_id = ?, custom_title = ?, "
                 "session_version = session_version + ? WHERE id = ?",
                 (role, status, reason if status == "banned" else "", rank_id, title,
                  1 if status != row["status"] else 0, member_id))
    if unlink:
        conn.execute("UPDATE members SET mc_uuid = NULL, mc_name = NULL, mc_account_id = NULL, linked_at = NULL WHERE id = ?",
                     (member_id,))
    if changes:
        audit(conn, g.user, "update", "members", member_id, "Changed forum member %s" % row["username"], changes)
    conn.commit()
    flash("Updated %s." % row["username"])
    return redirect(url_for("admin.forum_members", q=request.args.get("q") or None))


@bp.route("/forum/members/<int:member_id>/manage")
@auth.role_required(*FORUM_STAFF)
def forum_member(member_id):
    from .members import get_member_or_404
    conn = get_db()
    member = get_member_or_404(conn, member_id)
    mid = member["id"]
    count = lambda sql: conn.execute(sql, (mid,) * sql.count("?")).fetchone()[0]  # noqa: E731
    stats = [
        ("Threads", count("SELECT COUNT(*) FROM forum_threads WHERE member_id = ?")),
        ("Posts", count("SELECT COUNT(*) FROM forum_posts WHERE member_id = ?")),
        ("Hidden posts", count("SELECT COUNT(*) FROM forum_posts WHERE member_id = ? AND hidden = 1")),
        ("Reports against their posts", count("SELECT COUNT(*) FROM forum_reports r JOIN forum_posts p ON p.id = r.post_id WHERE p.member_id = ?")),
        ("Reports against their messages", count("SELECT COUNT(*) FROM message_reports r JOIN messages x ON x.id = r.message_id WHERE x.sender_id = ?")),
        ("Messages sent", count("SELECT COUNT(*) FROM messages WHERE sender_id = ?")),
        ("Friends", count("SELECT COUNT(*) FROM friendships WHERE member_a = ? OR member_b = ?")),
        ("Blocked by", count("SELECT COUNT(*) FROM member_blocks WHERE blocked_id = ?")),
        ("Arcade Marks earned", count("SELECT COALESCE(SUM(marks), 0) FROM arcade_payouts WHERE member_id = ?")),
    ]
    history = conn.execute("SELECT * FROM audit_log WHERE entity = 'members' AND entity_id = ? ORDER BY id DESC LIMIT 20",
                           (mid,)).fetchall()
    return render_template("admin/forum_member.html", m=member, stats=stats, history=history)


@bp.route("/forum/members/<int:member_id>/export")
@auth.role_required("admin")
def forum_member_export(member_id):
    from .members import get_member_or_404, send_export
    conn = get_db()
    member = get_member_or_404(conn, member_id)
    audit(conn, g.user, "export", "members", member_id, "Downloaded the data of member %s" % member["username"])
    conn.commit()
    return send_export(conn, member)


@bp.route("/forum/members/<int:member_id>/action", methods=["POST"])
@auth.role_required(*FORUM_STAFF)
def forum_member_action(member_id):
    import secrets
    from .community import _hash
    from .members import erase, get_member_or_404
    conn = get_db()
    member = get_member_or_404(conn, member_id)
    action = request.form.get("action")
    back = redirect(url_for("admin.forum_member", member_id=member_id))
    if action in ("reset_password", "delete") and g.user["role"] != "admin":
        abort(403)
    if action == "signout":
        conn.execute("UPDATE members SET session_version = session_version + 1 WHERE id = ?", (member_id,))
        audit(conn, g.user, "signout", "members", member_id, "Signed out member %s everywhere" % member["username"])
        flash("%s is signed out on every device." % member["username"])
    elif action == "clear_profile":
        conn.execute("UPDATE members SET headline = '', bio = '', signature = '', avatar = '', banner = '', island_name = '', "
                     "discord_handle = '', youtube_url = '', twitch_url = '', custom_title = '' WHERE id = ?", (member_id,))
        audit(conn, g.user, "clear", "members", member_id, "Cleared the profile of member %s" % member["username"])
        flash("Cleared %s's profile text, links and pictures." % member["username"])
    elif action == "reset_password":
        temporary = secrets.token_urlsafe(9)
        conn.execute("UPDATE members SET password_hash = ?, session_version = session_version + 1 WHERE id = ?",
                     (_hash(temporary), member_id))
        audit(conn, g.user, "reset", "members", member_id, "Reset the password of member %s" % member["username"])
        flash("Temporary password for %s: %s  Share it privately; it isn't shown again. They can change it under Account."
              % (member["username"], temporary))
    elif action == "delete":
        if request.form.get("confirm", "").strip() != member["username"]:
            flash("Type the member's username exactly to confirm.", "error")
            return back
        summary = erase(conn, member, remove_posts=bool(request.form.get("remove_posts")))
        audit(conn, g.user, "delete", "members", member_id, summary)
        conn.commit()
        flash(summary + ".")
        return redirect(url_for("admin.forum_members"))
    else:
        abort(400)
    conn.commit()
    return back


@bp.route("/forum/categories", methods=["GET", "POST"])
@auth.role_required("admin")
def forum_categories():
    conn = get_db()
    if request.method == "POST":
        cat_id = request.form.get("id", type=int)
        name = request.form.get("name", "").strip()[:60]
        slug = request.form.get("slug", "").strip()[:40]
        description = request.form.get("description", "").strip()[:200]
        staff_only = 1 if request.form.get("staff_only") else 0
        order = request.form.get("sort_order", 0, type=int)
        if not name or not SLUG_RE.match(slug):
            flash("Give the category a name and a slug like island-showcase.", "error")
            return redirect(url_for("admin.forum_categories"))
        clash = conn.execute("SELECT id FROM forum_categories WHERE slug = ?", (slug,)).fetchone()
        if clash and clash["id"] != cat_id:
            flash("Another category already uses that slug.", "error")
            return redirect(url_for("admin.forum_categories"))
        if cat_id:
            conn.execute("UPDATE forum_categories SET name = ?, slug = ?, description = ?, staff_only = ?, sort_order = ? WHERE id = ?",
                         (name, slug, description, staff_only, order, cat_id))
            audit(conn, g.user, "update", "forum_categories", cat_id, "Updated forum category %s" % name)
        else:
            cur = conn.execute("INSERT INTO forum_categories (slug, name, description, staff_only, sort_order) VALUES (?, ?, ?, ?, ?)",
                               (slug, name, description, staff_only, order))
            audit(conn, g.user, "create", "forum_categories", cur.lastrowid, "Added forum category %s" % name)
        conn.commit()
        flash("Saved %s." % name)
        return redirect(url_for("admin.forum_categories"))
    rows = conn.execute(
        "SELECT c.*, (SELECT COUNT(*) FROM forum_threads t WHERE t.category_id = c.id) AS threads "
        "FROM forum_categories c ORDER BY sort_order, id").fetchall()
    return render_template("admin/forum_categories.html", rows=rows)


RANK_FIELDS = ("name", "slug", "color", "text_color", "description", "product_slug", "frame")


@bp.route("/forum/ranks", methods=["GET", "POST"])
@auth.role_required("admin")
def forum_ranks():
    """Display ranks: the coloured tags shown beside names on the forum and profiles."""
    import re
    conn = get_db()
    if request.method == "POST":
        rank_id = request.form.get("id", type=int)
        if request.form.get("action") == "delete" and rank_id:
            conn.execute("UPDATE members SET rank_id = NULL WHERE rank_id = ?", (rank_id,))
            conn.execute("DELETE FROM ranks WHERE id = ?", (rank_id,))
            audit(conn, g.user, "delete", "ranks", rank_id, "Deleted a rank")
            conn.commit()
            flash("Rank deleted. Members who had it now show no rank.")
            return redirect(url_for("admin.forum_ranks"))
        data = {k: request.form.get(k, "").strip() for k in RANK_FIELDS}
        data["name"] = data["name"].upper()[:16]
        data["description"] = data["description"][:120]
        for flag in ("staff", "can_moderate", "can_announce"):
            data[flag] = 1 if request.form.get(flag) else 0
        data["priority"] = request.form.get("priority", 0, type=int)
        data["frame"] = frame_key(data["frame"])
        hex_re = re.compile(r"^#[0-9a-fA-F]{6}$")
        if not data["name"] or not SLUG_RE.match(data["slug"]) or len(data["slug"]) > 40:
            flash("Give the rank a tag and a slug like cloudkeeper.", "error")
        elif not hex_re.match(data["color"]) or not hex_re.match(data["text_color"]):
            flash("Colours must be hex values like #77C8F5.", "error")
        else:
            clash = conn.execute("SELECT id FROM ranks WHERE slug = ?", (data["slug"],)).fetchone()
            if clash and clash["id"] != rank_id:
                flash("Another rank already uses that slug.", "error")
                return redirect(url_for("admin.forum_ranks"))
            cols = list(data.keys())
            if rank_id:
                conn.execute("UPDATE ranks SET %s WHERE id = ?" % ", ".join(c + " = ?" for c in cols),
                             [data[c] for c in cols] + [rank_id])
                audit(conn, g.user, "update", "ranks", rank_id, "Updated rank %s" % data["name"])
            else:
                cur = conn.execute("INSERT INTO ranks (%s) VALUES (%s)" % (", ".join(cols), ", ".join("?" * len(cols))),
                                   [data[c] for c in cols])
                audit(conn, g.user, "create", "ranks", cur.lastrowid, "Added rank %s" % data["name"])
            conn.commit()
            flash("Saved %s." % data["name"])
        return redirect(url_for("admin.forum_ranks"))
    rows = conn.execute(
        "SELECT r.*, (SELECT COUNT(*) FROM members m WHERE m.rank_id = r.id) AS holders FROM ranks r "
        "ORDER BY priority DESC, name").fetchall()
    return render_template("admin/forum_ranks.html", rows=rows, frames=FRAMES)
