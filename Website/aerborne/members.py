"""The public members directory, plus the data export and account deletion staff use under Forum → Members.

Deleting a member keeps a nameless placeholder row so forum threads stay readable; everything personal
(profile, Minecraft link, messages, friends, blocks, notifications, likes, reports) is removed, and their
posts can be removed too.
"""
import io
import json
import math
import zipfile

from flask import abort, g, render_template, request, send_file

from .community import bp, member_required
from .community_data import AUTHOR_COLUMNS
from .db import get_db, now_str, utcnow

PER_PAGE = 48
SORTS = [("active", "Recently active"), ("newest", "Newest"), ("posts", "Most posts"), ("name", "Name")]
ORDER = {"active": "m.last_seen_at IS NULL, m.last_seen_at DESC", "newest": "m.created_at DESC",
         "posts": "m.post_count DESC, m.username", "name": "m.username COLLATE NOCASE"}


@bp.route("/members")
def members():
    conn = get_db()
    q = request.args.get("q", "").strip()[:40]
    sort = request.args.get("sort", "active")
    if sort not in ORDER:
        sort = "active"
    rank = request.args.get("rank", "")
    where = ["m.status = 'active'"]
    args = []
    if q:
        where.append("(m.username LIKE ? OR m.mc_name LIKE ?)")
        args += ["%" + q + "%", "%" + q + "%"]
    if rank == "staff":
        where.append("r.staff = 1")
    elif rank:
        where.append("r.slug = ?")
        args.append(rank)
    sql = " FROM members m LEFT JOIN ranks r ON r.id = m.rank_id WHERE " + " AND ".join(where)
    total = conn.execute("SELECT COUNT(*)" + sql, args).fetchone()[0]
    pages = max(1, int(math.ceil(total / float(PER_PAGE))))
    page = min(max(1, request.args.get("page", 1, type=int)), pages)
    rows = conn.execute("SELECT " + AUTHOR_COLUMNS + ", m.id, m.last_seen_at, m.headline, r.frame AS rank_frame" + sql +
                        " ORDER BY " + ORDER[sort] + " LIMIT ? OFFSET ?", args + [PER_PAGE, (page - 1) * PER_PAGE]).fetchall()
    ranks = conn.execute("SELECT slug, name FROM ranks WHERE id IN (SELECT rank_id FROM members WHERE status = 'active') "
                         "ORDER BY priority DESC").fetchall()
    return render_template("community/members.html", rows=rows, total=total, page=page, pages=pages, q=q, sort=sort,
                           sorts=SORTS, rank=rank, ranks=ranks)


# --- Data export ----------------------------------------------------------------------------

def _rows(conn, sql, args):
    return [dict(r) for r in conn.execute(sql, args).fetchall()]


def export_zip(conn, member):
    """Everything the website holds about a member, as a zip of JSON files. The password hash is never included."""
    mid = member["id"]
    profile = {k: member[k] for k in member.keys() if k not in ("password_hash", "session_version")}
    names = "SELECT username FROM members WHERE id = "
    files = {
        "profile.json": profile,
        "threads.json": _rows(conn, "SELECT t.id, t.title, c.name AS category, t.created_at, t.hidden FROM forum_threads t "
                                    "JOIN forum_categories c ON c.id = t.category_id WHERE t.member_id = ? ORDER BY t.id", (mid,)),
        "posts.json": _rows(conn, "SELECT p.id, p.thread_id, t.title AS thread, p.body, p.created_at, p.edited_at, p.hidden "
                                  "FROM forum_posts p JOIN forum_threads t ON t.id = p.thread_id WHERE p.member_id = ? ORDER BY p.id", (mid,)),
        "likes_given.json": _rows(conn, "SELECT post_id, created_at FROM post_likes WHERE member_id = ? ORDER BY created_at", (mid,)),
        "messages.json": _rows(conn, "SELECT x.id, (" + names + "x.sender_id) AS sender, (" + names + "x.recipient_id) AS recipient, "
                                     "x.body, x.source, x.created_at, x.read_at FROM messages x WHERE (x.sender_id = ? AND x.sender_deleted = 0) "
                                     "OR (x.recipient_id = ? AND x.recipient_deleted = 0) ORDER BY x.id", (mid, mid)),
        "friends.json": _rows(conn, "SELECT m.username, f.created_at FROM friendships f JOIN members m ON m.id = "
                                    "CASE WHEN f.member_a = ? THEN f.member_b ELSE f.member_a END WHERE f.member_a = ? OR f.member_b = ?",
                              (mid, mid, mid)),
        "friend_requests.json": _rows(conn, "SELECT (" + names + "from_id) AS from_member, (" + names + "to_id) AS to_member, created_at "
                                            "FROM friend_requests WHERE from_id = ? OR to_id = ?", (mid, mid)),
        "blocked.json": _rows(conn, "SELECT m.username, b.created_at FROM member_blocks b JOIN members m ON m.id = b.blocked_id "
                                    "WHERE b.member_id = ?", (mid,)),
        "notifications.json": _rows(conn, "SELECT kind, (" + names + "actor_id) AS actor, body, created_at, read_at FROM notifications "
                                          "WHERE member_id = ? ORDER BY id", (mid,)),
        "reports_filed.json": _rows(conn, "SELECT post_id, reason, created_at FROM forum_reports WHERE member_id = ?", (mid,))
                              + _rows(conn, "SELECT message_id, reason, created_at FROM message_reports WHERE member_id = ?", (mid,)),
        "arcade.json": {
            "runs": _rows(conn, "SELECT game, started_at, finished_at, score, marks, status FROM arcade_runs WHERE member_id = ? "
                                "ORDER BY id", (mid,)),
            "payouts": _rows(conn, "SELECT game, marks, created_at, status, delivered_at FROM arcade_payouts WHERE member_id = ? "
                                   "ORDER BY id", (mid,)),
        },
    }
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("README.txt", "Everything the Aerborne website holds about %s, exported %s UTC.\n"
                                       "Game progress lives on the Minecraft server and isn't included.\n" % (member["username"], now_str()))
        for name, data in files.items():
            archive.writestr(name, json.dumps(data, indent=2, ensure_ascii=False, default=str))
    buffer.seek(0)
    return buffer


def send_export(conn, member):
    name = "aerborne-%s-%s.zip" % (member["username"], utcnow().strftime("%Y%m%d"))
    return send_file(export_zip(conn, member), mimetype="application/zip", as_attachment=True, download_name=name)


@bp.route("/account/data")
@member_required
def my_data():
    conn = get_db()
    return send_export(conn, conn.execute("SELECT * FROM members WHERE id = ?", (g.member["id"],)).fetchone())


# --- Deletion -------------------------------------------------------------------------------

def erase(conn, member, remove_posts=False):
    """Deletes a member's account. Returns a summary for the audit log."""
    mid = member["id"]
    liked = conn.execute("SELECT post_id FROM post_likes WHERE member_id = ?", (mid,)).fetchall()
    for row in liked:
        conn.execute("UPDATE forum_posts SET like_count = MAX(0, like_count - 1) WHERE id = ?", (row["post_id"],))
        conn.execute("UPDATE members SET likes_received = MAX(0, likes_received - 1) WHERE id = "
                     "(SELECT member_id FROM forum_posts WHERE id = ?)", (row["post_id"],))
    for sql in ("DELETE FROM post_likes WHERE member_id = ?",
                "DELETE FROM message_reports WHERE member_id = ? OR message_id IN (SELECT id FROM messages WHERE sender_id = ?1 OR recipient_id = ?1)",
                "DELETE FROM messages WHERE sender_id = ? OR recipient_id = ?1",
                "DELETE FROM friendships WHERE member_a = ? OR member_b = ?1",
                "DELETE FROM friend_requests WHERE from_id = ? OR to_id = ?1",
                "DELETE FROM member_blocks WHERE member_id = ? OR blocked_id = ?1",
                "DELETE FROM notifications WHERE member_id = ? OR actor_id = ?1",
                "DELETE FROM forum_reports WHERE member_id = ?",
                "DELETE FROM member_link_codes WHERE member_id = ?",
                "DELETE FROM arcade_payouts WHERE member_id = ? AND status = 'pending'",
                "UPDATE arcade_runs SET member_id = NULL WHERE member_id = ?"):
        conn.execute(sql, (mid,))
    posts = conn.execute("SELECT COUNT(*) FROM forum_posts WHERE member_id = ?", (mid,)).fetchone()[0]
    if remove_posts:
        conn.execute("DELETE FROM forum_reports WHERE post_id IN (SELECT id FROM forum_posts WHERE member_id = ?)", (mid,))
        conn.execute("UPDATE forum_posts SET body = '', hidden = 1, hidden_reason = 'Removed with a deleted account' WHERE member_id = ?", (mid,))
        conn.execute("UPDATE forum_threads SET hidden = 1 WHERE member_id = ?", (mid,))
    conn.execute(
        "UPDATE members SET username = ?, password_hash = ?, status = 'deleted', role = 'member', ban_reason = '', "
        "session_version = session_version + 1, bio = '', mc_uuid = NULL, mc_name = NULL, mc_account_id = NULL, linked_at = NULL, "
        "rank_id = NULL, custom_title = '', avatar = '', banner = '', headline = '', signature = '', favorite_skill = '', "
        "island_name = '', discord_handle = '', youtube_url = '', twitch_url = '', show_stats = 0, last_seen_at = NULL, "
        "last_login_at = NULL, dm_policy = 'nobody', friend_requests_open = 0 WHERE id = ?",
        ("Deleted member %d" % mid, "!deleted", mid))
    return "Deleted member %s (%d post%s %s)" % (member["username"], posts, "" if posts == 1 else "s",
                                                 "removed" if remove_posts else "kept as 'Deleted member'")


def get_member_or_404(conn, member_id):
    row = conn.execute("SELECT * FROM members WHERE id = ?", (member_id,)).fetchone()
    if row is None or row["status"] == "deleted":
        abort(404)
    return row
