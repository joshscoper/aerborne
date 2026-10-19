"""CSRF tokens, response headers and login throttling."""
import datetime
import hmac
import secrets

from flask import abort, request, session

from .db import now_str, utcnow

LOGIN_WINDOW_MINUTES = 15
LOGIN_MAX_PER_IP = 10
LOGIN_MAX_PER_USER = 5

CSP = (
    "default-src 'self'; "
    "img-src 'self' data:; "
    "style-src 'self' https://fonts.googleapis.com; "
    "font-src https://fonts.gstatic.com; "
    "script-src 'self'; "
    "connect-src 'self'; "
    "frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
)


def csrf_token():
    token = session.get("_csrf")
    if not token:
        token = secrets.token_urlsafe(32)
        session["_csrf"] = token
    return token


def check_csrf():
    if request.method in ("GET", "HEAD", "OPTIONS"):
        return
    sent = request.form.get("_csrf") or request.headers.get("X-CSRF-Token", "")
    expected = session.get("_csrf", "")
    if not expected or not sent or not hmac.compare_digest(sent, expected):
        abort(400, "This form expired. Go back, reload the page and try again.")


def apply_headers(response):
    response.headers.setdefault("X-Content-Type-Options", "nosniff")
    response.headers.setdefault("X-Frame-Options", "DENY")
    response.headers.setdefault("Referrer-Policy", "strict-origin-when-cross-origin")
    response.headers.setdefault("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
    response.headers.setdefault("Content-Security-Policy", CSP)
    if request.is_secure:
        response.headers.setdefault("Strict-Transport-Security", "max-age=31536000")
    return response


def client_ip():
    # Passenger sits behind Apache on the same host, so remote_addr is trustworthy there.
    return request.remote_addr or "unknown"


def login_blocked(conn, username):
    since = (utcnow() - datetime.timedelta(minutes=LOGIN_WINDOW_MINUTES)).strftime("%Y-%m-%d %H:%M:%S")
    conn.execute("DELETE FROM login_attempts WHERE created_at < ?", (since,))
    by_ip = conn.execute(
        "SELECT COUNT(*) FROM login_attempts WHERE ip = ? AND created_at >= ?", (client_ip(), since)
    ).fetchone()[0]
    by_user = conn.execute(
        "SELECT COUNT(*) FROM login_attempts WHERE username = ? AND created_at >= ?",
        (username.lower(), since),
    ).fetchone()[0]
    return by_ip >= LOGIN_MAX_PER_IP or by_user >= LOGIN_MAX_PER_USER


def record_failed_login(conn, username):
    conn.execute(
        "INSERT INTO login_attempts (ip, username, created_at) VALUES (?, ?, ?)",
        (client_ip(), username.lower()[:64], now_str()),
    )


def clear_failed_logins(conn, username):
    conn.execute("DELETE FROM login_attempts WHERE username = ?", (username.lower(),))


def allow(conn, kind, key, limit, window_seconds, record=True):
    """Sliding-window limit shared by sign-ups, posts, reports and link attempts."""
    since = (utcnow() - datetime.timedelta(seconds=window_seconds)).strftime("%Y-%m-%d %H:%M:%S")
    conn.execute("DELETE FROM rate_events WHERE kind = ? AND created_at < ?", (kind, since))
    used = conn.execute("SELECT COUNT(*) FROM rate_events WHERE kind = ? AND key = ? AND created_at >= ?",
                        (kind, str(key), since)).fetchone()[0]
    if used >= limit:
        return False
    if record:
        conn.execute("INSERT INTO rate_events (kind, key, created_at) VALUES (?, ?, ?)", (kind, str(key), now_str()))
    return True
