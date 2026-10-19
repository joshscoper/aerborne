"""Staff accounts and role checks. Players never sign in to this site."""
import functools

from flask import abort, g, redirect, request, session, url_for
from werkzeug.security import check_password_hash, generate_password_hash

from .db import get_db

ROLES = [
    ("admin", "Admin: everything, including staff accounts and settings"),
    ("editor", "Editor: write, review and publish all content"),
    ("writer", "Writer: draft content and send it for review"),
    ("moderator", "Moderator: approve community builds and moderate the forum"),
]
ROLE_KEYS = [r[0] for r in ROLES]
MIN_PASSWORD_LENGTH = 12
# The Watchdog panel only accepts hashes this strong, so staff hashes use it too.
STAFF_ITERATIONS = 600000


def hash_password(password):
    return generate_password_hash(password, method="pbkdf2:sha256:%d" % STAFF_ITERATIONS, salt_length=16)


def hash_iterations(encoded):
    method = (encoded or "").split("$", 1)[0]
    parts = method.split(":")
    if len(parts) != 3 or parts[0] != "pbkdf2" or parts[1] != "sha256" or not parts[2].isdigit():
        return 0
    return int(parts[2])


def needs_rehash(encoded):
    return hash_iterations(encoded) < STAFF_ITERATIONS


def panel_status(user):
    """Whether this staff account is handed to the Watchdog panel, and if not, why."""
    if user["role"] != "admin":
        return False, "Only admins use the Watchdog panel."
    if not user["active"]:
        return False, "Account is deactivated."
    if user["must_change_password"]:
        return False, "Waiting for the first password change."
    if needs_rehash(user["password_hash"]):
        return False, "Sign in to the website once to upgrade the password."
    return True, "Can sign in to the Watchdog panel."


def verify_password(user, password):
    return bool(user) and check_password_hash(user["password_hash"], password)


def password_problem(password):
    if len(password or "") < MIN_PASSWORD_LENGTH:
        return "Use at least %d characters." % MIN_PASSWORD_LENGTH
    return None


def load_user():
    g.user = None
    uid = session.get("uid")
    if not uid:
        return
    user = get_db().execute("SELECT * FROM users WHERE id = ?", (uid,)).fetchone()
    # A password change or deactivation bumps session_version and signs out old sessions.
    if not user or not user["active"] or user["session_version"] != session.get("sv"):
        for key in ("uid", "sv", "view_as"):
            session.pop(key, None)
        return
    g.user = user


def start_session(user):
    session.clear()
    session.permanent = True
    session["uid"] = user["id"]
    session["sv"] = user["session_version"]


def login_required(view):
    @functools.wraps(view)
    def wrapped(*args, **kwargs):
        if g.get("user") is None:
            return redirect(url_for("admin.login", next=request.full_path))
        if g.user["must_change_password"] and request.endpoint != "admin.account":
            return redirect(url_for("admin.account"))
        return view(*args, **kwargs)
    return wrapped


def role_required(*roles):
    def decorator(view):
        @functools.wraps(view)
        @login_required
        def wrapped(*args, **kwargs):
            if g.user["role"] not in roles:
                abort(403)
            return view(*args, **kwargs)
        return wrapped
    return decorator


def can_edit_type(user, ctype):
    return bool(user) and user["role"] in ctype.roles


def allowed_statuses(user, ctype):
    """Writers draft and submit for review; editors and admins publish."""
    if user["role"] == "writer":
        return ["draft", "review"]
    return ["draft", "review", "published", "archived"]


def can_edit_item(user, ctype, item):
    if not can_edit_type(user, ctype):
        return False
    if item is not None and user["role"] == "writer" and item["status"] not in ("draft", "review"):
        return False
    return True
