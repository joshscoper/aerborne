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
    return render_template("admin/forum_reports.html", rows=rows, show=show)


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
    sql = "SELECT * FROM members"
    args = []
    if q:
        sql += " WHERE username LIKE ? OR mc_name LIKE ?"
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
