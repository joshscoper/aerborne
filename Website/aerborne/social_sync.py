"""Keeps friends, blocks and messages in step with the game server.

The plugin's WebsiteSocial collector POSTs here on a timer with a bearer token (AERBORNE_SOCIAL_TOKEN):

    {"apiVersion": 1, "ack": <last website event applied in game>,
     "events": [{"seq": n, "type": "friend|unfriend|block|unblock|message", "actor": <account>, "target": <account>,
                 "body": "...", "quiet": false}]}

and gets back the website's own changes plus who is linked:

    {"apiVersion": 1, "accepted": <highest seq stored>, "events": [{"id": n, "type": ..., ...}],
     "linked": [{"account": ..., "member": ..., "messages": "everyone|friends|nobody", "unreadMessages": n, "unreadNotifications": n}]}

Both sides number their events and acknowledge what they applied, so a lost response only repeats work that
is safe to repeat. Changes that arrive from the server are never queued back to it.
"""
import hmac
import re

from flask import Blueprint, abort, current_app, jsonify, request

from . import social
from .db import get_db, get_setting, set_setting
from .security import allow, client_ip

bp = Blueprint("social_sync", __name__, url_prefix="/admin/api")

ACCOUNT_RE = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
TYPES = ("friend", "unfriend", "block", "unblock", "message")
MAX_EVENTS = 200
MAX_GAME_MESSAGE = 512


@bp.after_request
def _no_store(response):
    response.headers["Cache-Control"] = "no-store"
    response.headers["X-Robots-Tag"] = "noindex, nofollow"
    return response


@bp.route("/social-sync", methods=["POST"])
def sync():
    expected = current_app.config.get("SOCIAL_TOKEN", "")
    if not expected:
        abort(404)
    conn = get_db()
    if not allow(conn, "social-sync", client_ip(), 120, 60):
        conn.commit()
        abort(429)
    if not hmac.compare_digest(request.headers.get("Authorization", ""), "Bearer " + expected):
        conn.commit()
        abort(401)
    data = request.get_json(silent=True)
    events = _validate(data)
    if events is None:
        abort(400)
    ack = data.get("ack", 0)
    if ack > 0:
        conn.execute("DELETE FROM social_outbox WHERE id <= ?", (ack,))
    conn.execute("DELETE FROM social_outbox WHERE created_at < datetime('now', '-7 days')")
    stored = get_setting("social_server_seq", "0")
    accepted = int(stored) if stored.isdigit() else 0
    for event in events:
        if event["seq"] <= accepted:
            continue
        _apply(conn, event)
        accepted = event["seq"]
    set_setting(conn, "social_server_seq", str(accepted))
    outgoing = conn.execute("SELECT * FROM social_outbox ORDER BY id LIMIT ?", (MAX_EVENTS,)).fetchall()
    linked = conn.execute(
        "SELECT m.mc_account_id, m.username, m.dm_policy, "
        "(SELECT COUNT(*) FROM messages x WHERE x.recipient_id = m.id AND x.read_at IS NULL AND x.recipient_deleted = 0) AS unread_messages, "
        "(SELECT COUNT(*) FROM notifications n WHERE n.member_id = m.id AND n.read_at IS NULL) AS unread_notifications "
        "FROM members m WHERE m.mc_account_id IS NOT NULL AND m.status = 'active' ORDER BY m.id LIMIT 20000").fetchall()
    conn.commit()
    return jsonify({
        "apiVersion": 1,
        "accepted": accepted,
        "events": [{"id": r["id"], "type": r["kind"], "actor": r["actor_account"], "target": r["target_account"],
                    "actorName": r["actor_name"], "body": r["body"]} for r in outgoing],
        "linked": [{"account": r["mc_account_id"], "member": r["username"],
                    "messages": r["dm_policy"] if social_policy_ok(r["dm_policy"]) else "everyone",
                    "unreadMessages": r["unread_messages"], "unreadNotifications": r["unread_notifications"]} for r in linked],
    })


def social_policy_ok(policy):
    return policy in dict(social.DM_POLICIES)


def _validate(data):
    """Returns the events in order, or None when the request is malformed."""
    if not isinstance(data, dict) or data.get("apiVersion") != 1:
        return None
    ack = data.get("ack", 0)
    events = data.get("events", [])
    if not isinstance(ack, int) or isinstance(ack, bool) or ack < 0 or not isinstance(events, list) or len(events) > MAX_EVENTS:
        return None
    previous = 0
    out = []
    for event in events:
        if not isinstance(event, dict):
            return None
        seq, kind, actor, target = event.get("seq"), event.get("type"), event.get("actor"), event.get("target")
        body = event.get("body", "")
        if not isinstance(seq, int) or isinstance(seq, bool) or seq <= previous or kind not in TYPES:
            return None
        if not isinstance(actor, str) or not isinstance(target, str) or not ACCOUNT_RE.match(actor) or not ACCOUNT_RE.match(target):
            return None
        if not isinstance(body, str) or len(body) > MAX_GAME_MESSAGE or kind == "message" and not body.strip():
            return None
        previous = seq
        out.append({"seq": seq, "type": kind, "actor": actor, "target": target, "body": body.strip(),
                    "quiet": event.get("quiet") is True})
    return out


def _apply(conn, event):
    """Applies one change made in game. Changes involving a player without a linked website account are skipped."""
    actor = conn.execute("SELECT * FROM members WHERE mc_account_id = ?", (event["actor"],)).fetchone()
    target = conn.execute("SELECT * FROM members WHERE mc_account_id = ?", (event["target"],)).fetchone()
    if actor is None or target is None or actor["id"] == target["id"]:
        return
    kind = event["type"]
    if kind == "friend":
        social.make_friends(conn, actor, target, from_server=True, quiet=event["quiet"])
    elif kind == "unfriend":
        social.remove_friend(conn, actor, target, from_server=True)
    elif kind in ("block", "unblock"):
        social.set_block(conn, actor, target, kind == "block", from_server=True)
    elif kind == "message":
        if social.message_block(conn, actor, target) is None:
            social.send_message(conn, actor, target, event["body"], source="game")
