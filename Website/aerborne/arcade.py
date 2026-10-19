"""The Arcade: small browser games that pay a few Marks on the server.

Scores come from the browser, so they can't be fully trusted. Rewards stay small and capped:
every run needs a server-issued token, a plausible score for the time played, and counts toward a
daily Marks limit. Payouts wait in a queue until the game server collects them (see WebsiteRewards).
"""
import datetime
import secrets

from flask import Blueprint, abort, current_app, g, jsonify, render_template, request

from .db import get_db, get_setting, now_str, parse_time, utcnow
from .security import allow, check_csrf, client_ip

bp = Blueprint("arcade", __name__, url_prefix="/arcade")

# slug: name, blurb, how to play, min seconds, max score per second, points per Mark, Marks cap per run, max score
GAMES = [
    {"slug": "sky-hop", "name": "Sky Hop", "icon": "img/companions/pufflet-avatar.webp",
     "blurb": "Flap Pufflet through the gaps between floating islands.",
     "how": "Click, tap or press Space to flap. Each gap you clear is a point.",
     "min_seconds": 4, "max_rate": 1.2, "per_mark": 2, "cap": 15, "max_score": 400},
    {"slug": "gem-rush", "name": "Gem Rush", "icon": "img/aspects/fortune-192.webp",
     "blurb": "Swap Aspect gems to make rows of three before time runs out.",
     "how": "Click a gem, then a neighbour to swap. Rows of three or more score. 60 seconds.",
     "min_seconds": 20, "max_rate": 80, "per_mark": 100, "cap": 20, "max_score": 5000},
    {"slug": "cloud-catch", "name": "Cloud Catch", "icon": "img/companions/kitekit-avatar.webp",
     "blurb": "Catch falling gems and dodge the storm clouds.",
     "how": "Move with your mouse, finger or the arrow keys. Three storm hits and you're out.",
     "min_seconds": 5, "max_rate": 3.5, "per_mark": 5, "cap": 15, "max_score": 1500},
    {"slug": "codex-memory", "name": "Codex Memory", "icon": "img/aspects/affinity-192.webp",
     "blurb": "Find every matching pair of Aspect gems in as few flips as you can.",
     "how": "Flip two cards at a time. Fewer moves and less time mean a higher score.",
     "min_seconds": 12, "max_rate": 30, "per_mark": 20, "cap": 15, "max_score": 300},
    {"slug": "forge-strike", "name": "Forge Strike", "icon": "img/aspects/might-192.webp",
     "blurb": "Strike the anvil when the spark is in the gold zone.",
     "how": "Click, tap or press Space as the spark crosses the gold. Three misses ends the run.",
     "min_seconds": 3, "max_rate": 2.5, "per_mark": 2, "cap": 15, "max_score": 500},
]
BY_SLUG = {game["slug"]: game for game in GAMES}
RUN_MINUTES = 30
REWARDED_RUNS_PER_GAME_PER_DAY = 15


@bp.before_request
def _guard():
    if get_setting("arcade_enabled", "1") != "1":
        abort(404)
    check_csrf()


def _day_start():
    now = utcnow()
    return now.replace(hour=0, minute=0, second=0).strftime("%Y-%m-%d %H:%M:%S")


def daily_cap():
    try:
        return max(0, int(get_setting("arcade_daily_cap", "200")))
    except ValueError:
        return 200


def _earned_today(conn, member_id):
    return conn.execute("SELECT COALESCE(SUM(marks), 0) FROM arcade_runs WHERE member_id = ? AND finished_at >= ?",
                        (member_id, _day_start())).fetchone()[0]


def _weekly_best(conn, slug, limit=5):
    since = (utcnow() - datetime.timedelta(days=7)).strftime("%Y-%m-%d %H:%M:%S")
    return conn.execute(
        "SELECT m.username, m.avatar, r.slug AS rank_slug, MAX(a.score) AS best FROM arcade_runs a "
        "JOIN members m ON m.id = a.member_id LEFT JOIN ranks r ON r.id = m.rank_id "
        "WHERE a.game = ? AND a.status = 'finished' AND a.finished_at >= ? AND m.status = 'active' "
        "GROUP BY a.member_id ORDER BY best DESC, MIN(a.finished_at) LIMIT ?", (slug, since, limit)).fetchall()


def _status(conn):
    member = g.get("member")
    if not member:
        return {"signed_in": False, "delivery_enabled": bool(current_app.config.get("REWARDS_TOKEN"))}
    pending = conn.execute("SELECT COALESCE(SUM(marks), 0) FROM arcade_payouts WHERE member_id = ? AND status = 'pending'",
                           (member["id"],)).fetchone()[0]
    return {"signed_in": True, "linked": bool(member["mc_account_id"]), "earned": _earned_today(conn, member["id"]),
            "cap": daily_cap(), "pending": pending,
            "delivery_enabled": bool(current_app.config.get("REWARDS_TOKEN"))}


@bp.route("/")
def index():
    conn = get_db()
    boards = {game["slug"]: _weekly_best(conn, game["slug"], 3) for game in GAMES}
    return render_template("arcade/index.html", games=GAMES, boards=boards, status=_status(conn), cap=daily_cap())


@bp.route("/<slug>")
def game(slug):
    info = BY_SLUG.get(slug) or abort(404)
    conn = get_db()
    best = None
    if g.get("member"):
        best = conn.execute("SELECT MAX(score) FROM arcade_runs WHERE member_id = ? AND game = ? AND status = 'finished'",
                            (g.member["id"], slug)).fetchone()[0]
    return render_template("arcade/game.html", game=info, board=_weekly_best(conn, slug, 10),
                           status=_status(conn), best=best)


@bp.route("/<slug>/start", methods=["POST"])
def start(slug):
    info = BY_SLUG.get(slug) or abort(404)
    member = g.get("member")
    if not member or member["status"] != "active":
        return jsonify({"practice": True})
    conn = get_db()
    if not allow(conn, "arcade-start", member["id"], 120, 3600):
        conn.commit()
        return jsonify({"practice": True, "message": "You've started a lot of games. This one's for practice."})
    conn.execute("UPDATE arcade_runs SET status = 'abandoned' WHERE member_id = ? AND game = ? AND status = 'running'",
                 (member["id"], slug))
    token = secrets.token_urlsafe(18)
    conn.execute("INSERT INTO arcade_runs (token, member_id, game, started_at) VALUES (?, ?, ?, ?)",
                 (token, member["id"], slug, now_str()))
    conn.commit()
    return jsonify({"practice": False, "token": token})


@bp.route("/run/<token>/finish", methods=["POST"])
def finish(token):
    member = g.get("member")
    if not member or member["status"] != "active":
        abort(403)
    conn = get_db()
    # Serialize the run check, daily allowance and payout insert across simultaneous submissions.
    conn.execute("BEGIN IMMEDIATE")
    run = conn.execute("SELECT * FROM arcade_runs WHERE token = ?", (token,)).fetchone()
    if run is None or run["member_id"] != member["id"] or run["status"] != "running":
        return jsonify({"ok": False, "message": "That game already ended. Start a new one."}), 409
    info = BY_SLUG[run["game"]]
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        return jsonify({"ok": False, "message": "Send a score object."}), 400
    score = data.get("score", 0)
    if isinstance(score, bool) or not isinstance(score, int):
        score = -1
    started = parse_time(run["started_at"])
    elapsed = (utcnow() - started).total_seconds() if started else 0
    reason = None
    if score < 0 or score > info["max_score"]:
        reason = "That score isn't possible."
    elif elapsed > RUN_MINUTES * 60:
        reason = "That run took too long to count."
    elif score > info["max_rate"] * max(elapsed, 0) + info["per_mark"]:
        reason = "That score came in faster than the game allows."
    marks = 0
    note = ""
    if reason is None and elapsed >= info["min_seconds"] and score > 0:
        marks = min(info["cap"], score // info["per_mark"])
        rewarded_runs = conn.execute(
            "SELECT COUNT(*) FROM arcade_runs WHERE member_id = ? AND game = ? AND marks > 0 AND finished_at >= ?",
            (member["id"], run["game"], _day_start())).fetchone()[0]
        room = daily_cap() - _earned_today(conn, member["id"])
        if not member["mc_account_id"]:
            marks, note = 0, "Link your Minecraft account to earn Marks."
        elif rewarded_runs >= REWARDED_RUNS_PER_GAME_PER_DAY:
            marks, note = 0, "You've had today's paid runs for this game. Try another!"
        elif room <= 0:
            marks, note = 0, "You've reached today's Arcade limit. It resets at 00:00 UTC."
        elif marks > room:
            marks, note = room, "That reached today's Arcade limit."
    status = "finished" if reason is None else "rejected"
    conn.execute("UPDATE arcade_runs SET finished_at = ?, score = ?, marks = ?, status = ? WHERE id = ?",
                 (now_str(), max(score, 0), marks, status, run["id"]))
    if marks > 0:
        conn.execute("INSERT INTO arcade_payouts (member_id, account_id, run_id, game, marks, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                     (member["id"], member["mc_account_id"], run["id"], run["game"], marks, now_str()))
    conn.commit()
    best = conn.execute("SELECT MAX(score) FROM arcade_runs WHERE member_id = ? AND game = ? AND status = 'finished'",
                        (member["id"], run["game"])).fetchone()[0]
    return jsonify({"ok": reason is None, "message": reason or note, "marks": marks, "score": max(score, 0),
                    "best": best, "earned_today": _earned_today(conn, member["id"]), "cap": daily_cap(),
                    "delivery_enabled": bool(current_app.config.get("REWARDS_TOKEN"))})


# --- Payout queue, read by the game server ---------------------------------------------------

def payout_feed():
    """GET /admin/api/arcade-payouts?ack=<id>. Bearer token; marks everything up to ack as delivered."""
    import hmac
    expected = current_app.config.get("REWARDS_TOKEN", "")
    if not expected:
        abort(404)
    conn = get_db()
    if not allow(conn, "rewards-feed", client_ip(), 60, 60):
        conn.commit()
        abort(429)
    if not hmac.compare_digest(request.headers.get("Authorization", ""), "Bearer " + expected):
        conn.commit()
        abort(401)
    ack = request.args.get("ack", 0, type=int)
    if ack > 0:
        conn.execute("UPDATE arcade_payouts SET status = 'delivered', delivered_at = ? WHERE status = 'pending' AND id <= ?",
                     (now_str(), ack))
    rows = conn.execute("SELECT id, account_id, marks, game FROM arcade_payouts WHERE status = 'pending' ORDER BY id LIMIT 100").fetchall()
    conn.commit()
    return jsonify({"apiVersion": 1, "payouts": [
        {"id": r["id"], "accountId": r["account_id"], "marks": r["marks"], "game": r["game"]} for r in rows]})
