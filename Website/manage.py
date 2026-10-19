"""Small command-line helpers for the cPanel Terminal.

    python manage.py create-admin <username>   add an admin (asks for a password)
    python manage.py set-password <username>   reset a staff password
    python manage.py run                        run the site locally on http://127.0.0.1:5000
"""
import getpass
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from aerborne import auth, create_app  # noqa: E402
from aerborne.db import audit, connect, now_str  # noqa: E402


def _password():
    first = getpass.getpass("Password (at least %d characters): " % auth.MIN_PASSWORD_LENGTH)
    problem = auth.password_problem(first)
    if problem:
        sys.exit(problem)
    if getpass.getpass("Repeat it: ") != first:
        sys.exit("The passwords don't match.")
    return first


def main(argv):
    if len(argv) < 2:
        sys.exit(__doc__)
    command = argv[1]
    if command == "run":
        os.environ.setdefault("AERBORNE_SECURE_COOKIES", "0")
        create_app().run(debug=True)
        return
    app = create_app()
    conn = connect(app.config["DATABASE"])
    if command == "create-admin" and len(argv) == 3:
        username = argv[2]
        if conn.execute("SELECT 1 FROM users WHERE username = ?", (username,)).fetchone():
            sys.exit("That username already exists. Use set-password instead.")
        cur = conn.execute(
            "INSERT INTO users (username, display_name, password_hash, role, created_at) VALUES (?, ?, ?, 'admin', ?)",
            (username, username, auth.hash_password(_password()), now_str()))
        audit(conn, None, "create", "users", cur.lastrowid, "Created admin %s from the command line" % username)
        conn.commit()
        print("Admin %s created. Sign in at /admin." % username)
    elif command == "set-password" and len(argv) == 3:
        username = argv[2]
        row = conn.execute("SELECT id FROM users WHERE username = ?", (username,)).fetchone()
        if not row:
            sys.exit("No staff account is called %s." % username)
        conn.execute("UPDATE users SET password_hash = ?, session_version = session_version + 1 WHERE id = ?",
                     (auth.hash_password(_password()), row["id"]))
        audit(conn, None, "update", "users", row["id"], "Reset password for %s from the command line" % username)
        conn.commit()
        print("Password updated. Their other sessions were signed out.")
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main(sys.argv)
