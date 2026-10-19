"""Friends, blocks, direct messages and notifications.

Everything here works for any member. When both members have linked Minecraft, friendships and blocks
are also queued for the game server (see social_sync), so the in-game /friends and /ignore lists match,
and a message to a linked member also pops up in their game chat when they're online.
"""
from flask import abort, current_app, flash, g, redirect, render_template, request, url_for

from .community import _safe_next, _settings_flag, bp, member_required
from .community_data import AUTHOR_COLUMNS
from .db import get_db, now_str
from .security import allow

MAX_FRIENDS = 200  # The game server's limit, so a synced list always fits on both sides.
MAX_BLOCKS = 500
MAX_MESSAGE = 1000
REQUEST_DAYS = 30
NOTIFICATIONS_KEPT = 200
DM_POLICIES = [("everyone", "Anyone"), ("friends", "Friends only"), ("nobody", "No one")]
MEMBER_COLUMNS = AUTHOR_COLUMNS + ", m.id, m.last_seen_at, m.mc_account_id, m.dm_policy"


# --- Relationships --------------------------------------------------------------------------

def _pair(a, b):
    return (a, b) if a < b else (b, a)


def are_friends(conn, a, b):
    return conn.execute("SELECT 1 FROM friendships WHERE member_a = ? AND member_b = ?", _pair(a, b)).fetchone() is not None


def is_blocked(conn, a, b):
    """True when either member has blocked the other."""
    return conn.execute("SELECT 1 FROM member_blocks WHERE (member_id = ? AND blocked_id = ?) OR (member_id = ? AND blocked_id = ?)",
                        (a, b, b, a)).fetchone() is not None


def blocked_ids(conn, member_id):
    """Members this member has blocked; their posts are folded away on the forum."""
    return {r[0] for r in conn.execute("SELECT blocked_id FROM member_blocks WHERE member_id = ?", (member_id,))}


def friend_count(conn, member_id):
    return conn.execute("SELECT COUNT(*) FROM friendships WHERE member_a = ? OR member_b = ?", (member_id, member_id)).fetchone()[0]


def relationship(conn, me, other):
    return {
        "friends": are_friends(conn, me, other),
        "outgoing": conn.execute("SELECT 1 FROM friend_requests WHERE from_id = ? AND to_id = ?", (me, other)).fetchone() is not None,
        "incoming": conn.execute("SELECT 1 FROM friend_requests WHERE from_id = ? AND to_id = ?", (other, me)).fetchone() is not None,
        "blocked": conn.execute("SELECT 1 FROM member_blocks WHERE member_id = ? AND blocked_id = ?", (me, other)).fetchone() is not None,
        "blocked_by": conn.execute("SELECT 1 FROM member_blocks WHERE member_id = ? AND blocked_id = ?", (other, me)).fetchone() is not None,
    }


# --- Notifications --------------------------------------------------------------------------

def notify(conn, member_id, kind, actor_id=None, ref="", body="", collapse=False):
    """Adds a notification. With collapse, an unread one of the same kind, actor and link is bumped instead."""
    if actor_id is not None and (actor_id == member_id or is_blocked(conn, member_id, actor_id)):
        return
    if collapse:
        row = conn.execute("SELECT id FROM notifications WHERE member_id = ? AND kind = ? AND actor_id IS ? AND ref = ? "
                           "AND read_at IS NULL", (member_id, kind, actor_id, ref)).fetchone()
        if row:
            conn.execute("UPDATE notifications SET created_at = ?, body = ? WHERE id = ?", (now_str(), body, row["id"]))
            return
    conn.execute("INSERT INTO notifications (member_id, kind, actor_id, ref, body, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                 (member_id, kind, actor_id, ref, body[:160], now_str()))
    conn.execute("DELETE FROM notifications WHERE member_id = ? AND id NOT IN "
                 "(SELECT id FROM notifications WHERE member_id = ? ORDER BY id DESC LIMIT ?)",
                 (member_id, member_id, NOTIFICATIONS_KEPT))


@bp.app_template_global()
def social_counts():
    """Unread messages and notifications for the nav, worked out once per request."""
    if g.get("member") is None:
        return {"messages": 0, "notifications": 0, "requests": 0}
    if "_social_counts" not in g:
        conn = get_db()
        mid = g.member["id"]
        g._social_counts = {
            "messages": conn.execute("SELECT COUNT(*) FROM messages WHERE recipient_id = ? AND read_at IS NULL "
                                     "AND recipient_deleted = 0", (mid,)).fetchone()[0],
            "notifications": conn.execute("SELECT COUNT(*) FROM notifications WHERE member_id = ? AND read_at IS NULL",
                                          (mid,)).fetchone()[0],
            "requests": conn.execute("SELECT COUNT(*) FROM friend_requests WHERE to_id = ?", (mid,)).fetchone()[0],
        }
    return g._social_counts


# --- Changes, shared by the website and the server sync --------------------------------------

def queue(conn, kind, actor, target, body=""):
    """Queues a change for the game server. Only linked members exist in game; nothing is queued while sync is off."""
    if not current_app.config.get("SOCIAL_TOKEN") or not target["mc_account_id"]:
        return
    actor_account = actor["mc_account_id"] if actor is not None else None
    if kind != "message" and not actor_account:
        return
    conn.execute("INSERT INTO social_outbox (kind, actor_account, target_account, actor_name, body, created_at) "
                 "VALUES (?, ?, ?, ?, ?, ?)", (kind, actor_account, target["mc_account_id"],
                                               actor["username"] if actor is not None else "", body, now_str()))


def make_friends(conn, a, b, from_server=False, quiet=False):
    """Returns None when they're friends, otherwise the reason they can't be."""
    if a["id"] == b["id"] or is_blocked(conn, a["id"], b["id"]):
        return "You can't be friends with that member."
    if are_friends(conn, a["id"], b["id"]):
        return None
    if friend_count(conn, a["id"]) >= MAX_FRIENDS or friend_count(conn, b["id"]) >= MAX_FRIENDS:
        return "One of your friends lists is full (%d)." % MAX_FRIENDS
    conn.execute("INSERT INTO friendships (member_a, member_b, created_at) VALUES (?, ?, ?)", _pair(a["id"], b["id"]) + (now_str(),))
    conn.execute("DELETE FROM friend_requests WHERE (from_id = ? AND to_id = ?) OR (from_id = ? AND to_id = ?)",
                 (a["id"], b["id"], b["id"], a["id"]))
    if from_server:
        if not quiet:
            for one, other in ((a, b), (b, a)):
                notify(conn, one["id"], "friend_game", other["id"], url_for("community.member_profile", username=other["username"]))
    else:
        queue(conn, "friend", a, b)
    return None


def remove_friend(conn, a, b, from_server=False):
    conn.execute("DELETE FROM friendships WHERE member_a = ? AND member_b = ?", _pair(a["id"], b["id"]))
    if not from_server:
        queue(conn, "unfriend", a, b)


def set_block(conn, actor, target, value, from_server=False):
    if value:
        if actor["id"] == target["id"]:
            return "You can't block yourself."
        if conn.execute("SELECT COUNT(*) FROM member_blocks WHERE member_id = ?", (actor["id"],)).fetchone()[0] >= MAX_BLOCKS:
            return "Your block list is full (%d)." % MAX_BLOCKS
        conn.execute("INSERT OR IGNORE INTO member_blocks (member_id, blocked_id, created_at) VALUES (?, ?, ?)",
                     (actor["id"], target["id"], now_str()))
        conn.execute("DELETE FROM friendships WHERE member_a = ? AND member_b = ?", _pair(actor["id"], target["id"]))
        conn.execute("DELETE FROM friend_requests WHERE (from_id = ? AND to_id = ?) OR (from_id = ? AND to_id = ?)",
                     (actor["id"], target["id"], target["id"], actor["id"]))
        conn.execute("DELETE FROM notifications WHERE member_id = ? AND actor_id = ?", (actor["id"], target["id"]))
    else:
        conn.execute("DELETE FROM member_blocks WHERE member_id = ? AND blocked_id = ?", (actor["id"], target["id"]))
    if not from_server:
        queue(conn, "block" if value else "unblock", actor, target)
    return None


def message_block(conn, sender, recipient):
    """Returns None when sender may message recipient, otherwise the reason to show."""
    if sender["status"] != "active":
        return "Your account can't send messages right now."
    if not _settings_flag("messages_enabled"):
        return "Messages are switched off at the moment."
    if sender["id"] == recipient["id"]:
        return "You can't message yourself."
    if _settings_flag("forum_require_link") and not sender["mc_uuid"]:
        return "Link your Minecraft account before sending messages."
    if is_blocked(conn, sender["id"], recipient["id"]):
        return "You can't message this member."
    if recipient["status"] != "active":
        return "This member can't receive messages."
    policy = recipient["dm_policy"] or "everyone"
    if policy == "nobody" or policy == "friends" and not are_friends(conn, sender["id"], recipient["id"]):
        return "%s only accepts messages from %s." % (recipient["username"], "friends" if policy == "friends" else "no one")
    return None


def send_message(conn, sender, recipient, body, source="web"):
    """Stores a message and notifies the recipient. The caller has already checked message_block."""
    cur = conn.execute("INSERT INTO messages (sender_id, recipient_id, body, source, created_at) VALUES (?, ?, ?, ?, ?)",
                       (sender["id"], recipient["id"], body, source, now_str()))
    notify(conn, recipient["id"], "message", sender["id"], url_for("community.conversation", username=sender["username"]),
           body[:120], collapse=True)
    if source == "web":
        queue(conn, "message", sender, recipient, body[:512])
    return cur.lastrowid


def on_linked(conn, member):
    """A member just linked Minecraft: ask the server for their game friends and blocks, and send it the website's."""
    if not current_app.config.get("SOCIAL_TOKEN") or not member["mc_account_id"]:
        return
    conn.execute("INSERT INTO social_outbox (kind, actor_account, target_account, actor_name, created_at) VALUES ('link', ?, ?, ?, ?)",
                 (member["mc_account_id"], member["mc_account_id"], member["username"], now_str()))
    for other in conn.execute("SELECT m.* FROM friendships f JOIN members m ON m.id = CASE WHEN f.member_a = ? THEN f.member_b "
                              "ELSE f.member_a END WHERE (f.member_a = ? OR f.member_b = ?) AND m.mc_account_id IS NOT NULL",
                              (member["id"], member["id"], member["id"])).fetchall():
        queue(conn, "friend", member, other)
    for other in conn.execute("SELECT m.* FROM member_blocks b JOIN members m ON m.id = b.blocked_id "
                              "WHERE b.member_id = ? AND m.mc_account_id IS NOT NULL", (member["id"],)).fetchall():
        queue(conn, "block", member, other)
    for other in conn.execute("SELECT m.* FROM member_blocks b JOIN members m ON m.id = b.member_id "
                              "WHERE b.blocked_id = ? AND m.mc_account_id IS NOT NULL", (member["id"],)).fetchall():
        queue(conn, "block", other, member)


# --- Pages ----------------------------------------------------------------------------------

def _member(conn, username):
    return conn.execute("SELECT * FROM members WHERE username = ?", (username,)).fetchone() or abort(404)


def _back(default):
    return redirect(_safe_next(request.form.get("next") or request.args.get("next"), default))


def _people(conn, sql, args):
    return conn.execute("SELECT " + MEMBER_COLUMNS + " FROM members m LEFT JOIN ranks r ON r.id = m.rank_id " + sql, args).fetchall()


@bp.route("/friends", methods=["GET", "POST"])
@member_required
def friends():
    conn = get_db()
    me = g.member["id"]
    if request.method == "POST":
        username = request.form.get("username", "").strip()
        other = conn.execute("SELECT * FROM members WHERE username = ?", (username,)).fetchone()
        if not other:
            flash("No member is called %s." % username if username else "Type a member's name.", "error")
            return redirect(url_for("community.friends"))
        return _friend_action(conn, other, "add", url_for("community.friends", tab="requests"))
    tab = request.args.get("tab", "friends")
    if tab not in ("friends", "requests", "blocked"):
        tab = "friends"
    conn.execute("DELETE FROM friend_requests WHERE created_at < datetime('now', ?)", ("-%d days" % REQUEST_DAYS,))
    conn.commit()
    people = _people(conn, "JOIN friendships f ON (f.member_a = ? AND f.member_b = m.id) OR (f.member_b = ? AND f.member_a = m.id) "
                           "ORDER BY m.last_seen_at DESC", (me, me))
    incoming = _people(conn, "JOIN friend_requests q ON q.from_id = m.id WHERE q.to_id = ? ORDER BY q.created_at DESC", (me,))
    outgoing = _people(conn, "JOIN friend_requests q ON q.to_id = m.id WHERE q.from_id = ? ORDER BY q.created_at DESC", (me,))
    blocked = _people(conn, "JOIN member_blocks b ON b.blocked_id = m.id WHERE b.member_id = ? ORDER BY b.created_at DESC", (me,))
    return render_template("community/friends.html", tab=tab, people=people, incoming=incoming, outgoing=outgoing,
                           blocked=blocked, max_friends=MAX_FRIENDS)


@bp.route("/friends/<username>", methods=["POST"])
@member_required
def friend_action(username):
    conn = get_db()
    return _friend_action(conn, _member(conn, username), request.form.get("action", ""),
                          url_for("community.member_profile", username=username))


def _friend_action(conn, other, action, default_next):
    me = g.member
    if other["id"] == me["id"]:
        flash("That's you!", "error")
        return _back(default_next)
    if me["status"] != "active" and action in ("add", "accept"):
        flash("Your account can't add friends right now.", "error")
        return _back(default_next)
    rel = relationship(conn, me["id"], other["id"])
    name = other["username"]
    if action == "add":
        if rel["friends"]:
            flash("You and %s are already friends." % name)
        elif rel["blocked"] or rel["blocked_by"]:
            flash("You can't add %s as a friend." % name, "error")
        elif rel["incoming"]:
            return _friend_action(conn, other, "accept", default_next)
        elif rel["outgoing"]:
            flash("Your request to %s is still waiting." % name)
        elif not other["friend_requests_open"] or other["status"] != "active":
            flash("%s isn't accepting friend requests." % name, "error")
        elif friend_count(conn, me["id"]) >= MAX_FRIENDS:
            flash("Your friends list is full (%d)." % MAX_FRIENDS, "error")
        elif not allow(conn, "friend-request", me["id"], 20, 3600):
            conn.commit()
            flash("You've sent a lot of friend requests. Try again later.", "error")
        else:
            conn.execute("INSERT OR IGNORE INTO friend_requests (from_id, to_id, created_at) VALUES (?, ?, ?)",
                         (me["id"], other["id"], now_str()))
            notify(conn, other["id"], "friend_request", me["id"], url_for("community.friends", tab="requests"))
            conn.commit()
            flash("Friend request sent to %s." % name)
    elif action == "accept":
        if not rel["incoming"]:
            flash("That friend request is no longer available.", "error")
        else:
            problem = make_friends(conn, me, other)
            if problem:
                flash(problem, "error")
            else:
                notify(conn, other["id"], "friend_accept", me["id"], url_for("community.member_profile", username=me["username"]))
                conn.execute("DELETE FROM notifications WHERE member_id = ? AND actor_id = ? AND kind = 'friend_request'",
                             (me["id"], other["id"]))
                conn.commit()
                flash("You and %s are now friends." % name)
    elif action in ("decline", "cancel"):
        a, b = (other["id"], me["id"]) if action == "decline" else (me["id"], other["id"])
        conn.execute("DELETE FROM friend_requests WHERE from_id = ? AND to_id = ?", (a, b))
        conn.execute("DELETE FROM notifications WHERE member_id = ? AND actor_id = ? AND kind = 'friend_request'", (b, a))
        conn.commit()
        flash("Request declined." if action == "decline" else "Request cancelled.")
    elif action == "remove":
        remove_friend(conn, me, other)
        conn.commit()
        flash("Removed %s from your friends." % name)
    elif action in ("block", "unblock"):
        problem = set_block(conn, me, other, action == "block")
        if problem:
            flash(problem, "error")
        else:
            conn.commit()
            flash(("Blocked %s. They can't message you or add you, and their posts are folded away." if action == "block"
                   else "Unblocked %s.") % name)
    else:
        abort(400)
    return _back(default_next)


@bp.route("/messages")
@member_required
def messages():
    conn = get_db()
    me = g.member["id"]
    to = request.args.get("to", "").strip()
    if to:
        other = conn.execute("SELECT username FROM members WHERE username = ?", (to,)).fetchone()
        if other:
            return redirect(url_for("community.conversation", username=other["username"]))
        flash("No member is called %s." % to, "error")
    rows = conn.execute(
        "SELECT other_id, MAX(id) AS last_id, SUM(unread) AS unread FROM ("
        " SELECT id, recipient_id AS other_id, 0 AS unread FROM messages WHERE sender_id = ? AND sender_deleted = 0"
        " UNION ALL"
        " SELECT id, sender_id AS other_id, CASE WHEN read_at IS NULL THEN 1 ELSE 0 END FROM messages"
        " WHERE recipient_id = ? AND recipient_deleted = 0"
        ") GROUP BY other_id ORDER BY last_id DESC LIMIT 60", (me, me)).fetchall()
    conversations = []
    for row in rows:
        other = _people(conn, "WHERE m.id = ?", (row["other_id"],))
        last = conn.execute("SELECT * FROM messages WHERE id = ?", (row["last_id"],)).fetchone()
        if other:
            conversations.append({"other": other[0], "last": last, "unread": row["unread"]})
    return render_template("community/messages.html", conversations=conversations)


@bp.route("/messages/<username>", methods=["GET", "POST"])
@member_required
def conversation(username):
    conn = get_db()
    other = _member(conn, username)
    me = g.member
    if other["id"] == me["id"]:
        return redirect(url_for("community.messages"))
    if request.method == "POST":
        body = request.form.get("body", "").replace("\r\n", "\n").strip()
        problem = message_block(conn, me, other)
        if problem:
            flash(problem, "error")
        elif not 1 <= len(body) <= MAX_MESSAGE:
            flash("Write between 1 and %d characters." % MAX_MESSAGE, "error")
        elif not allow(conn, "message-burst", me["id"], 1, 2) or not allow(conn, "message", me["id"], 60, 600):
            conn.commit()
            flash("You're sending messages very quickly. Wait a moment and try again.", "error")
        else:
            send_message(conn, me, other, body)
            conn.commit()
        return redirect(url_for("community.conversation", username=other["username"], _anchor="latest"))
    rows = conn.execute(
        "SELECT * FROM (SELECT * FROM messages WHERE (sender_id = ? AND recipient_id = ? AND sender_deleted = 0) "
        "OR (sender_id = ? AND recipient_id = ? AND recipient_deleted = 0) ORDER BY id DESC LIMIT 200) ORDER BY id",
        (me["id"], other["id"], other["id"], me["id"])).fetchall()
    conn.execute("UPDATE messages SET read_at = ? WHERE sender_id = ? AND recipient_id = ? AND read_at IS NULL",
                 (now_str(), other["id"], me["id"]))
    conn.execute("UPDATE notifications SET read_at = ? WHERE member_id = ? AND actor_id = ? AND kind = 'message' AND read_at IS NULL",
                 (now_str(), me["id"], other["id"]))
    conn.commit()
    g.pop("_social_counts", None)
    person = _people(conn, "WHERE m.id = ?", (other["id"],))[0]
    return render_template("community/conversation.html", other=person, rows=rows, max_message=MAX_MESSAGE,
                           blocked=message_block(conn, me, other), rel=relationship(conn, me["id"], other["id"]))


@bp.route("/messages/<username>/delete", methods=["POST"])
@member_required
def delete_conversation(username):
    conn = get_db()
    other = _member(conn, username)
    me = g.member["id"]
    conn.execute("UPDATE messages SET sender_deleted = 1 WHERE sender_id = ? AND recipient_id = ?", (me, other["id"]))
    conn.execute("UPDATE messages SET recipient_deleted = 1, read_at = COALESCE(read_at, ?) WHERE sender_id = ? AND recipient_id = ?",
                 (now_str(), other["id"], me))
    conn.commit()
    flash("Conversation with %s removed from your inbox. They still have their copy." % other["username"])
    return redirect(url_for("community.messages"))


@bp.route("/messages/report/<int:message_id>", methods=["POST"])
@member_required
def report_message(message_id):
    conn = get_db()
    message = conn.execute("SELECT * FROM messages WHERE id = ? AND recipient_id = ?", (message_id, g.member["id"])).fetchone() or abort(404)
    sender = conn.execute("SELECT username FROM members WHERE id = ?", (message["sender_id"],)).fetchone()
    reason = request.form.get("reason", "").strip()[:300] or "No reason given"
    if not allow(conn, "report", g.member["id"], 10, 3600):
        conn.commit()
        flash("You've sent a lot of reports. Staff will get to them; try again later.", "error")
    elif conn.execute("SELECT 1 FROM message_reports WHERE message_id = ? AND resolved_at IS NULL", (message_id,)).fetchone():
        flash("You've already reported that message.")
    else:
        conn.execute("INSERT INTO message_reports (message_id, member_id, reason, created_at) VALUES (?, ?, ?, ?)",
                     (message_id, g.member["id"], reason, now_str()))
        conn.commit()
        flash("Thanks. Staff will review that message. You can also block the sender.")
    return redirect(url_for("community.conversation", username=sender["username"]))


NOTIFICATION_TEXT = {
    "friend_request": "sent you a friend request",
    "friend_accept": "accepted your friend request",
    "friend_game": "is now your friend (added in game)",
    "message": "sent you a message",
    "reply": "replied to your thread",
    "like": "liked your post in",
    "badge": "awarded you a badge:",
}


@bp.route("/notifications")
@member_required
def notifications():
    conn = get_db()
    show = request.args.get("show", "all")
    where = " AND n.read_at IS NULL" if show == "unread" else ""
    rows = conn.execute(
        "SELECT n.*, m.username AS actor_name, m.avatar AS actor_avatar, r.slug AS actor_rank FROM notifications n "
        "LEFT JOIN members m ON m.id = n.actor_id LEFT JOIN ranks r ON r.id = m.rank_id "
        "WHERE n.member_id = ?" + where + " ORDER BY n.created_at DESC, n.id DESC LIMIT 100", (g.member["id"],)).fetchall()
    return render_template("community/notifications.html", rows=rows, show=show, text=NOTIFICATION_TEXT)


@bp.route("/notifications/read", methods=["POST"])
@member_required
def notifications_read():
    conn = get_db()
    conn.execute("UPDATE notifications SET read_at = ? WHERE member_id = ? AND read_at IS NULL", (now_str(), g.member["id"]))
    conn.commit()
    return _back(url_for("community.notifications"))


@bp.route("/notifications/<int:notification_id>")
@member_required
def open_notification(notification_id):
    conn = get_db()
    row = conn.execute("SELECT * FROM notifications WHERE id = ? AND member_id = ?", (notification_id, g.member["id"])).fetchone() or abort(404)
    conn.execute("UPDATE notifications SET read_at = COALESCE(read_at, ?) WHERE id = ?", (now_str(), row["id"]))
    conn.commit()
    return redirect(_safe_next(row["ref"], url_for("community.notifications")))
