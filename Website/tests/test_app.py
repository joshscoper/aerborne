import io
import json
import os
import re
import shutil
import socket
import sys
import tempfile
import threading
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from unittest import mock  # noqa: E402

from aerborne import auth, create_app, gameapi, status  # noqa: E402
from aerborne.config import status_target  # noqa: E402
from aerborne.db import connect, now_str  # noqa: E402

PUBLIC_PATHS = [
    "/forum", "/forum/general", "/leaderboards", "/account/login", "/account/register", "/account/recover",
    "/", "/play", "/status", "/explore", "/explore/your-island", "/guide",
    "/guide/section/getting-started", "/guide/first-island", "/codex", "/codex?tier=signature",
    "/companions", "/companions/pufflet", "/news", "/news/welcome", "/news/feed.xml", "/store",
    "/store/supporter-ranks", "/community", "/support", "/support/rules", "/sitemap.xml", "/robots.txt",
]


class SiteTest(unittest.TestCase):
    def setUp(self):
        self.data = tempfile.mkdtemp()
        os.environ["AERBORNE_DATA_DIR"] = self.data
        self.app = create_app({"TESTING": True, "SESSION_COOKIE_SECURE": False, "SETUP_TOKEN": "letmein-token",
                               "SERVER_ADDRESS": "", "API_URL": "", "API_TOKEN": ""})
        self.client = self.app.test_client()
        status.clear_cache()

    def tearDown(self):
        os.environ.pop("AERBORNE_DATA_DIR", None)
        shutil.rmtree(self.data, ignore_errors=True)

    # helpers
    def db(self):
        return connect(self.app.config["DATABASE"])

    def add_user(self, username, role, password="correct-horse-battery"):
        conn = self.db()
        conn.execute("INSERT INTO users (username, password_hash, role, created_at) VALUES (?, ?, ?, ?)",
                     (username, auth.hash_password(password), role, now_str()))
        conn.commit()
        conn.close()

    def csrf(self, path):
        html = self.client.get(path).get_data(as_text=True)
        return re.search(r'name="_csrf" value="([^"]+)"', html).group(1)

    def login(self, username, password="correct-horse-battery"):
        token = self.csrf("/admin/login")
        return self.client.post("/admin/login", data={"_csrf": token, "username": username, "password": password})


class PublicPagesTest(SiteTest):
    def test_guide_search_only_returns_live_content(self):
        conn = self.db()
        conn.execute("UPDATE guides SET status = 'draft' WHERE slug = 'milestone-planning'")
        conn.commit()
        conn.close()
        page = self.client.get('/guide?q=manageable+session').get_data(as_text=True)
        self.assertIn('No guides match', page)
        found = self.client.get('/guide?q=trade').get_data(as_text=True)
        self.assertIn('Choose how to trade', found)
        self.assertIn('Clear search', found)

    def test_new_content_revision_preserves_staff_edits(self):
        from aerborne import seed_updates
        conn = self.db()
        conn.execute("UPDATE pages SET body = 'Owner policy', updated_by = 'Josh' WHERE slug = 'privacy'")
        conn.execute("UPDATE guides SET body = 'Owner guide' WHERE slug = 'milestone-planning'")
        conn.execute("UPDATE settings SET value = '3' WHERE key = 'seed_rev'")
        conn.commit()
        seed_updates.apply(conn)
        self.assertEqual(conn.execute("SELECT body FROM pages WHERE slug = 'privacy'").fetchone()[0], 'Owner policy')
        self.assertEqual(conn.execute("SELECT body FROM guides WHERE slug = 'milestone-planning'").fetchone()[0], 'Owner guide')
        self.assertFalse(seed_updates.apply(conn))
        conn.close()

    def test_pages_render(self):
        for path in PUBLIC_PATHS:
            response = self.client.get(path)
            self.assertEqual(response.status_code, 200, path)
            self.assertIn("Content-Security-Policy", response.headers)

    def test_fun_cursor_is_on_by_default_and_can_be_switched_off(self):
        self.assertIn(b'class="home fun-cursor"', self.client.get("/").data)
        conn = self.db()
        conn.execute("UPDATE settings SET value = '0' WHERE key = 'fun_cursor'")
        conn.commit()
        conn.close()
        self.assertNotIn(b"fun-cursor", self.client.get("/").data)
        self.assertEqual(self.client.get("/static/img/cursor/arrow.svg").status_code, 200)

    def test_drafts_and_missing_pages_are_hidden(self):
        self.assertEqual(self.client.get("/support/terms").status_code, 200)  # published legal page
        conn = self.db()
        conn.execute("UPDATE pages SET status = 'draft' WHERE slug = 'privacy'")
        conn.execute("UPDATE products SET status = 'draft' WHERE slug = 'skyfarer'")
        conn.commit()
        conn.close()
        self.assertEqual(self.client.get("/support/privacy").status_code, 404)
        self.assertEqual(self.client.get("/store/item/skyfarer").status_code, 404)
        self.assertEqual(self.client.get("/nowhere").status_code, 404)

    def test_hidden_recipes_stay_hidden(self):
        html = self.client.get("/codex").get_data(as_text=True)
        self.assertIn("Stormwake", html)
        stormwake = html[html.index('id="stormwake"'):]
        stormwake = stormwake[:stormwake.index("</li>\n")]
        self.assertIn("discover in game", stormwake)
        self.assertNotIn("aspect-celerity", stormwake)
        # An Aspect filter must not reveal which hidden recipes use it.
        filtered = self.client.get("/codex?aspect=celerity").get_data(as_text=True)
        self.assertNotIn("Stormwake", filtered)
        api = self.client.get("/api/v1/codex").get_json()["items"]
        storm = next(i for i in api if i["slug"] == "stormwake")
        self.assertIsNone(storm["aspects"])
        keen = next(i for i in api if i["slug"] == "keen-edge")
        self.assertEqual(keen["aspects"], ["might"])

    def test_scheduled_content_waits(self):
        conn = self.db()
        conn.execute("UPDATE news SET publish_at = '2999-01-01 00:00:00' WHERE slug = 'welcome'")
        conn.commit()
        conn.close()
        self.assertEqual(self.client.get("/news/welcome").status_code, 404)
        self.assertNotIn(b"welcome", self.client.get("/news/feed.xml").data)

    def test_status_without_address(self):
        data = self.client.get("/api/v1/status").get_json()
        self.assertEqual(data["state"], "unconfigured")

    def test_markdown_is_sanitised(self):
        conn = self.db()
        conn.execute("UPDATE pages SET body = ? WHERE slug = 'rules'",
                     ("Hello <script>alert(1)</script> [x](javascript:alert(1)) <img src=x onerror=alert(1)>",))
        conn.commit()
        conn.close()
        html = self.client.get("/support/rules").get_data(as_text=True)
        self.assertNotIn("<script>alert", html)
        self.assertNotIn("javascript:alert", html)
        self.assertNotIn("onerror", html)


class AdminTest(SiteTest):
    def test_default_admin_must_choose_a_new_password(self):
        conn = self.db()
        row = conn.execute("SELECT * FROM users WHERE username = 'j0sh'").fetchone()
        self.assertEqual(row["role"], "admin")
        self.assertEqual(row["must_change_password"], 1)
        self.assertTrue(row["password_hash"].startswith("pbkdf2:sha256:"))
        # Use a known password for the test instead of the shipped default.
        conn.execute("UPDATE users SET password_hash = ? WHERE username = 'j0sh'",
                     (auth.hash_password("temporary-default-pw"),))
        conn.commit()
        conn.close()
        self.assertEqual(self.client.get("/admin/setup").status_code, 404)
        signin = self.login("j0sh", "temporary-default-pw")
        self.assertIn("/admin/account", signin.headers["Location"])
        self.assertIn("/admin/account", self.client.get("/admin/").headers["Location"])
        token = self.csrf("/admin/account")
        same = self.client.post("/admin/account", data={"_csrf": token, "current": "temporary-default-pw",
                                                       "new": "temporary-default-pw"}, follow_redirects=True)
        self.assertIn(b"haven&#39;t used", same.data)
        changed = self.client.post("/admin/account", data={"_csrf": token, "current": "temporary-default-pw",
                                                          "new": "a-much-better-password"}, follow_redirects=True)
        self.assertIn(b"Password changed", changed.data)
        self.assertEqual(self.client.get("/admin/").status_code, 200)

    def test_login_required_and_csrf(self):
        self.add_user("ed", "editor")
        self.assertEqual(self.client.get("/admin/").status_code, 302)
        no_token = self.client.post("/admin/login", data={"username": "ed", "password": "correct-horse-battery"})
        self.assertEqual(no_token.status_code, 400)
        self.assertEqual(self.login("ed").status_code, 302)
        self.assertEqual(self.client.get("/admin/").status_code, 200)

    def test_login_throttle(self):
        self.add_user("ed", "editor")
        for _ in range(5):
            self.login("ed", "wrong-password-here")
        blocked = self.login("ed")
        self.assertIn(b"Too many sign-in attempts", blocked.data)

    def test_editor_publishes_and_audit_records_it(self):
        self.add_user("ed", "editor")
        self.login("ed")
        token = self.csrf("/admin/content/news/new")
        response = self.client.post("/admin/content/news/new", data={
            "_csrf": token, "title": "Patch 0.2", "slug": "patch-0-2", "category": "patch",
            "summary": "Fixes.", "body": "## Fixed\n- Things", "author": "Ed", "status": "published",
            "sort_order": "0"})
        self.assertEqual(response.status_code, 302)
        self.assertEqual(self.client.get("/news/patch-0-2").status_code, 200)
        conn = self.db()
        row = conn.execute("SELECT * FROM audit_log WHERE entity = 'news' ORDER BY id DESC").fetchone()
        conn.close()
        self.assertEqual(row["username"], "ed")
        self.assertIn("title", json.loads(row["changes"]))

    def test_writer_cannot_publish_or_edit_live_content(self):
        self.add_user("wren", "writer")
        self.login("wren")
        token = self.csrf("/admin/content/news/new")
        response = self.client.post("/admin/content/news/new", data={
            "_csrf": token, "title": "Sneaky", "slug": "sneaky", "category": "patch", "summary": "x",
            "status": "published", "sort_order": "0"})
        self.assertIn(b"can&#39;t set that status", response.data)
        conn = self.db()
        live_id = conn.execute("SELECT id FROM news WHERE slug = 'welcome'").fetchone()[0]
        conn.close()
        self.assertEqual(self.client.get("/admin/content/news/%d" % live_id).status_code, 403)
        self.assertEqual(self.client.get("/admin/content/products").status_code, 403)
        self.assertEqual(self.client.get("/admin/users").status_code, 403)

    def test_showcase_needs_permission_to_publish(self):
        self.add_user("mod", "moderator")
        self.login("mod")
        token = self.csrf("/admin/content/showcases/new")
        response = self.client.post("/admin/content/showcases/new", data={
            "_csrf": token, "title": "Sky Garden", "slug": "sky-garden", "builder": "Ash",
            "status": "published", "sort_order": "0"})
        self.assertIn(b"Confirm the builder", response.data)

    def test_store_item_needs_price_link_and_terms(self):
        self.add_user("ed", "editor")
        self.login("ed")
        conn = self.db()
        item_id = conn.execute("SELECT id FROM products WHERE slug = 'skyfarer'").fetchone()[0]
        conn.close()
        path = "/admin/content/products/%d" % item_id
        token = self.csrf(path)
        response = self.client.post(path, data={
            "_csrf": token, "name": "Skyfarer", "slug": "skyfarer", "category": "supporter-ranks",
            "summary": "Thanks", "includes": "Badge", "availability": "released", "status": "published",
            "sort_order": "0"})
        body = response.get_data(as_text=True)
        self.assertIn("exact price", body)
        self.assertIn("Tebex link", body)
        conn = self.db()
        conn.execute("UPDATE pages SET status = 'draft' WHERE slug = 'store-terms'")
        conn.commit()
        conn.close()
        response = self.client.post(path, data={
            "_csrf": token, "name": "Skyfarer", "slug": "skyfarer", "category": "supporter-ranks",
            "summary": "Thanks", "includes": "Badge", "availability": "released", "status": "published",
            "price": "$5", "tebex_url": "https://aerborne.tebex.io/package/1", "sort_order": "0"})
        self.assertIn("store terms", response.get_data(as_text=True))

    def test_preview_shows_drafts_only_to_staff(self):
        conn = self.db()
        page_id = conn.execute("SELECT id FROM pages WHERE slug = 'terms'").fetchone()[0]
        conn.close()
        path = "/admin/content/pages/%d/preview" % page_id
        self.assertEqual(self.client.get(path).status_code, 302)
        self.add_user("ed", "editor")
        self.login("ed")
        response = self.client.get(path)
        self.assertEqual(response.status_code, 200)
        self.assertIn(b"Preview:", response.data)

    def test_media_upload_validates_type(self):
        self.add_user("ed", "editor")
        self.login("ed")
        token = self.csrf("/admin/media")
        fake = self.client.post("/admin/media", data={
            "_csrf": token, "alt": "x", "file": (io.BytesIO(b"<svg onload=alert(1)>"), "evil.png")},
            content_type="multipart/form-data", follow_redirects=True)
        self.assertIn(b"isn&#39;t the image type", fake.data)
        try:
            from PIL import Image
        except ImportError:
            return
        buf = io.BytesIO()
        Image.new("RGB", (40, 30), (119, 200, 245)).save(buf, "PNG")
        buf.seek(0)
        ok = self.client.post("/admin/media", data={"_csrf": token, "alt": "Blue", "file": (buf, "sky.png")},
                              content_type="multipart/form-data", follow_redirects=True)
        self.assertIn(b"Uploaded", ok.data)
        conn = self.db()
        row = conn.execute("SELECT * FROM media").fetchone()
        conn.close()
        self.assertTrue(row["filename"].endswith(".webp"))
        self.assertEqual(self.client.get("/media/" + row["filename"]).status_code, 200)

    def test_last_admin_cannot_be_demoted(self):
        self.add_user("boss", "admin")
        self.login("boss")
        conn = self.db()
        conn.execute("UPDATE users SET active = 0 WHERE username = 'j0sh'")
        conn.commit()
        uid = conn.execute("SELECT id FROM users WHERE username = 'boss'").fetchone()[0]
        conn.close()
        token = self.csrf("/admin/users")
        response = self.client.post("/admin/users/%d" % uid, data={"_csrf": token, "role": "editor", "active": "1"},
                                    follow_redirects=True)
        self.assertIn(b"Keep at least one active admin", response.data)


LEADERBOARDS = {"apiVersion": 1, "ready": True, "updatedAt": "2026-10-18T12:00:00Z", "boards": {
    "adventurer": {"players": 2, "top": [{"rank": 1, "name": "Amy", "score": 1500}, {"rank": 2, "name": "Zed", "score": 20}]},
    "marks": {"players": 0, "top": []}}}
PROFILE = {"apiVersion": 1, "name": "Amy", "updatedAt": "2026-10-18T12:00:00Z",
           "adventurer": {"level": 12, "rank": "Scout"}, "skills": {"mining": 7, "fishing": 3},
           "boards": {"adventurer": {"rank": 1, "score": 1500}}}


def fake_api(links=None):
    links = links or {}

    def request(path, token=None):
        if path == "/api/v1/leaderboards":
            return LEADERBOARDS, None
        if path.lower() == "/api/v1/players/amy":
            return PROFILE, None
        if path.startswith("/api/v1/links/"):
            if not token:
                return None, "unavailable"
            code = path.rsplit("/", 1)[1]
            return (links[code], None) if code in links else (None, "not_found")
        return None, "not_found"
    return request


class CommunityHelpers(SiteTest):
    def setUp(self):
        SiteTest.setUp(self)
        self.app.config["API_TOKEN"] = "b" * 64
        gameapi.clear_cache()

    def register(self, username="Skyfarer", password="cloud-castle-9"):
        token = self.csrf("/account/register")
        return self.client.post("/account/register", data={"_csrf": token, "username": username,
                                                           "password": password, "confirm": password})

    def link(self, code="ABCD2345", player="11111111-2222-3333-4444-555555555555", name="Amy"):
        links = {code: {"apiVersion": 1, "accountId": "acct", "playerId": player, "name": name}}
        with mock.patch.object(gameapi, "_request", side_effect=fake_api(links)):
            token = self.csrf("/account/link")
            return self.client.post("/account/link", data={"_csrf": token, "code": code[:4] + "-" + code[4:]},
                                    follow_redirects=True)



class CommunityTest(CommunityHelpers):
    def test_register_login_and_honeypot(self):
        self.assertEqual(self.register().status_code, 302)
        self.assertIn(b"Skyfarer", self.client.get("/account").data)
        self.client.post("/account/logout", data={"_csrf": self.csrf("/account")})
        self.assertEqual(self.client.get("/account").status_code, 302)
        token = self.csrf("/account/login")
        bad = self.client.post("/account/login", data={"_csrf": token, "username": "Skyfarer", "password": "nope"})
        self.assertIn(b"don&#39;t match", bad.data)
        good = self.client.post("/account/login", data={"_csrf": token, "username": "skyfarer", "password": "cloud-castle-9"})
        self.assertEqual(good.status_code, 302)
        self.client.post("/account/logout", data={"_csrf": self.csrf("/account")})
        token = self.csrf("/account/register")
        bot = self.client.post("/account/register", data={"_csrf": token, "username": "BotBot", "password": "x" * 12,
                                                         "confirm": "x" * 12, "website": "http://spam"})
        self.assertEqual(bot.status_code, 400)

    def test_posting_needs_a_linked_account(self):
        self.register()
        cat = "/forum/general/new"
        blocked = self.client.get(cat)
        self.assertIn(b"Link your Minecraft account before posting", blocked.data)
        linked = self.link()
        self.assertIn(b"Linked to Amy", linked.data)
        token = self.csrf(cat)
        made = self.client.post(cat, data={"_csrf": token, "title": "Best starter farm?",
                                           "body": "Hello <script>alert(1)</script> **world**"})
        self.assertEqual(made.status_code, 302)
        thread = self.client.get(made.headers["Location"]).get_data(as_text=True)
        self.assertIn("<strong>world</strong>", thread)
        self.assertNotIn("<script>alert", thread)
        self.assertIn("Amy", thread)  # verified Minecraft name beside the post

    def test_link_codes_are_single_use_and_unique_per_player(self):
        self.register("First")
        self.link(code="ABCD2345")
        self.client.post("/account/logout", data={"_csrf": self.csrf("/account")})
        self.register("Second")
        reused = self.link(code="ABCD2345")
        self.assertIn(b"already been used", reused.data)
        same_player = self.link(code="WXYZ6789")
        self.assertIn(b"already linked to another website account", same_player.data)
        with mock.patch.object(gameapi, "_request", side_effect=fake_api({})):
            token = self.csrf("/account/link")
            expired = self.client.post("/account/link", data={"_csrf": token, "code": "QQQQ2222"}, follow_redirects=True)
        self.assertIn(b"expired", expired.data)

    def test_staff_only_category_and_locked_threads(self):
        self.register()
        self.link()
        self.assertIn(b"Only the Aerborne team posts here", self.client.get("/forum/announcements/new").data)
        token = self.csrf("/forum/general/new")
        made = self.client.post("/forum/general/new", data={"_csrf": token, "title": "Locked soon", "body": "First post"})
        conn = self.db()
        thread_id = conn.execute("SELECT id FROM forum_threads").fetchone()[0]
        conn.execute("UPDATE forum_threads SET locked = 1")
        conn.commit()
        conn.close()
        reply = self.client.post("/forum/thread/%d/reply" % thread_id, data={"_csrf": token, "body": "Can I still?"},
                                 follow_redirects=True)
        self.assertIn(b"This thread is locked", reply.data)
        self.assertEqual(made.status_code, 302)

    def test_reports_reach_the_admin_and_staff_can_hide(self):
        self.register("Author")
        self.link(code="ABCD2345", player="aaaaaaaa-0000-0000-0000-000000000001", name="Author")
        token = self.csrf("/forum/general/new")
        self.client.post("/forum/general/new", data={"_csrf": token, "title": "Buy cheap gold", "body": "spam spam"})
        self.client.post("/account/logout", data={"_csrf": token})
        self.register("Reporter")
        conn = self.db()
        post_id = conn.execute("SELECT id FROM forum_posts").fetchone()[0]
        conn.close()
        token = self.csrf("/account")
        self.client.post("/forum/post/%d/report" % post_id, data={"_csrf": token, "reason": "Spam"})
        self.add_user("mod", "moderator")
        self.login("mod")
        reports = self.client.get("/admin/forum/reports").get_data(as_text=True)
        self.assertIn("Buy cheap gold", reports)
        report_id = int(re.search(r"/admin/forum/reports/(\d+)", reports).group(1))
        token = self.csrf("/admin/forum/reports")
        self.client.post("/admin/forum/reports/%d" % report_id, data={"_csrf": token, "action": "hide"})
        conn = self.db()
        self.assertEqual(conn.execute("SELECT hidden FROM forum_posts WHERE id = ?", (post_id,)).fetchone()[0], 1)
        conn.close()

    def test_staff_can_use_the_main_sign_in_form(self):
        conn = self.db()
        conn.execute("UPDATE users SET password_hash = ? WHERE username = 'j0sh'", (auth.hash_password("temporary-default-pw"),))
        conn.commit()
        conn.close()
        token = self.csrf("/account/login")
        wrong = self.client.post("/account/login", data={"_csrf": token, "username": "j0sh", "password": "nope"})
        self.assertIn(b"don&#39;t match", wrong.data)
        ok = self.client.post("/account/login", data={"_csrf": token, "username": "j0sh", "password": "temporary-default-pw"})
        self.assertIn("/admin/account", ok.headers["Location"])
        self.assertIn(b"still has its default password", self.client.get("/admin/account").data)
        # Players can't take a staff username.
        self.client.post("/admin/logout", data={"_csrf": self.csrf("/admin/account")})
        taken = self.register("j0sh")
        self.assertIn(b"That username is taken", taken.data)

    def test_community_forms_need_csrf(self):
        self.assertEqual(self.client.post("/account/login", data={"username": "a", "password": "b"}).status_code, 400)

    def test_leaderboards_and_profiles(self):
        with mock.patch.object(gameapi, "_request", side_effect=fake_api()):
            board = self.client.get("/leaderboards").get_data(as_text=True)
            self.assertIn("Amy", board)
            self.assertIn("1,500", board)
            empty = self.client.get("/leaderboards?board=marks").get_data(as_text=True)
            self.assertIn("No one is ranked", empty)
            profile = self.client.get("/players/Amy").get_data(as_text=True)
            self.assertIn("Scout", profile)
            self.assertEqual(self.client.get("/players/Nobody").status_code, 404)
        self.assertEqual(self.client.get("/players/not-a-name!").status_code, 404)
        gameapi.clear_cache()
        with mock.patch.object(gameapi, "_request", return_value=(None, "unavailable")):
            down = self.client.get("/leaderboards").get_data(as_text=True)
        self.assertIn("offline right now", down)
        self.assertNotIn("Amy", down)

    def test_password_recovery_needs_the_linked_player(self):
        self.register()
        self.link(code="ABCD2345")
        self.client.post("/account/logout", data={"_csrf": self.csrf("/account")})
        other = {"WXYZ6789": {"playerId": "99999999-0000-0000-0000-000000000000", "name": "Mallory"},
                 "MNPQ2345": {"playerId": "11111111-2222-3333-4444-555555555555", "name": "Amy"}}
        with mock.patch.object(gameapi, "_request", side_effect=fake_api(other)):
            token = self.csrf("/account/recover")
            wrong = self.client.post("/account/recover", data={"_csrf": token, "username": "Skyfarer",
                                                              "code": "WXYZ6789", "new": "brand-new-pass"})
            self.assertIn(b"different Minecraft account", wrong.data)
            right = self.client.post("/account/recover", data={"_csrf": token, "username": "Skyfarer",
                                                              "code": "MNPQ2345", "new": "brand-new-pass"})
            self.assertEqual(right.status_code, 302)
        token = self.csrf("/account/login")
        ok = self.client.post("/account/login", data={"_csrf": token, "username": "Skyfarer", "password": "brand-new-pass"})
        self.assertEqual(ok.status_code, 302)


class PanelSyncTest(SiteTest):
    TOKEN = "d" * 64

    def fetch(self, token=None):
        headers = {"Authorization": "Bearer " + token} if token else {}
        return self.client.get("/admin/api/panel-accounts", headers=headers)

    def test_endpoint_is_off_without_a_token(self):
        self.assertEqual(self.fetch(self.TOKEN).status_code, 404)

    def test_only_ready_admins_are_sent_and_only_with_the_token(self):
        self.app.config["PANEL_SYNC_TOKEN"] = self.TOKEN
        self.add_user("boss", "admin")          # strong hash: synced
        self.add_user("writer1", "writer")      # not an admin
        self.add_user("gone", "admin")
        conn = self.db()
        conn.execute("UPDATE users SET active = 0 WHERE username = 'gone'")
        from werkzeug.security import generate_password_hash
        conn.execute("INSERT INTO users (username, password_hash, role, created_at) VALUES (?, ?, 'admin', ?)",
                     ("oldhash", generate_password_hash("an-old-password-1", method="pbkdf2:sha256:260000"), now_str()))
        conn.commit()
        conn.close()
        self.assertEqual(self.fetch().status_code, 401)
        self.assertEqual(self.fetch("e" * 64).status_code, 401)
        data = self.fetch(self.TOKEN).get_json()
        names = [a["username"] for a in data["accounts"]]
        self.assertEqual(names, ["boss"])  # j0sh still has to change the default password
        self.assertTrue(data["accounts"][0]["hash"].startswith("pbkdf2:sha256:600000$"))
        # An older, weaker hash is upgraded at the next sign-in, then synced.
        self.login("oldhash", "an-old-password-1")
        names = [a["username"] for a in self.fetch(self.TOKEN).get_json()["accounts"]]
        self.assertEqual(names, ["boss", "oldhash"])

    def test_staff_page_shows_panel_access(self):
        self.app.config["PANEL_SYNC_TOKEN"] = self.TOKEN
        self.add_user("boss", "admin")
        self.login("boss")
        page = self.client.get("/admin/users").get_data(as_text=True)
        self.assertIn("Panel access", page)
        self.assertIn("Waiting for the first password change", page)
        self.assertIn("hasn't fetched the list yet", page)
        self.fetch(self.TOKEN)
        self.assertIn("Last fetched by the panel", self.client.get("/admin/users").get_data(as_text=True))


class ViewAsTest(CommunityHelpers):
    def test_staff_toggle_between_admin_and_player_views(self):
        self.assertNotIn(b"Admin view", self.client.get("/news/welcome").data)
        conn = self.db()
        before = conn.execute("SELECT COALESCE(SUM(views), 0) FROM page_views").fetchone()[0]
        conn.close()
        self.add_user("boss", "admin")
        self.login("boss")
        page = self.client.get("/news/welcome").get_data(as_text=True)
        self.assertIn("Admin view", page)
        self.assertIn("Edit this news post", page)
        self.assertIn(">Admin</a>", page)
        conn = self.db()
        self.assertEqual(conn.execute("SELECT COALESCE(SUM(views), 0) FROM page_views").fetchone()[0], before,
                         "Staff visits aren't counted")
        conn.close()
        token = re.search(r'name="_csrf" value="([^"]+)"', page).group(1)
        switched = self.client.post("/admin/view-as", data={"_csrf": token, "mode": "player", "next": "/news/welcome"})
        self.assertEqual(switched.headers["Location"].rsplit("localhost", 1)[-1], "/news/welcome")
        player = self.client.get("/news/welcome").get_data(as_text=True)
        self.assertNotIn("Edit this news post", player)
        self.assertNotIn("Admin view", player)
        self.assertIn("Back to admin view", player)
        self.assertIn(">Sign in</a>", player)
        back = self.client.post("/admin/view-as", data={"_csrf": token, "mode": "admin", "next": "//evil.example"})
        self.assertTrue(back.headers["Location"].endswith("/"))
        self.assertIn("Admin view", self.client.get("/").get_data(as_text=True))

    def test_staff_moderate_the_forum_only_in_admin_view(self):
        self.register("Author")
        self.link()
        token = self.csrf("/forum/general/new")
        self.client.post("/forum/general/new", data={"_csrf": token, "title": "Hello there", "body": "First post"})
        self.client.post("/account/logout", data={"_csrf": token})
        conn = self.db()
        post_id = conn.execute("SELECT id FROM forum_posts").fetchone()[0]
        conn.close()
        self.add_user("mod", "moderator")
        self.login("mod")
        token = self.csrf("/forum")
        self.assertEqual(self.client.post("/forum/post/%d/moderate" % post_id, data={"_csrf": token, "action": "hide"}).status_code, 302)
        self.client.post("/admin/view-as", data={"_csrf": token, "mode": "player"})
        self.assertEqual(self.client.post("/forum/post/%d/moderate" % post_id, data={"_csrf": token, "action": "unhide"}).status_code, 403)
        conn = self.db()
        row = conn.execute("SELECT username, action FROM audit_log WHERE entity = 'forum_posts'").fetchone()
        conn.close()
        self.assertEqual((row["username"], row["action"]), ("mod", "hide"))

    def test_view_switch_needs_a_staff_login(self):
        token = self.csrf("/account/login")
        self.assertIn("/admin/login", self.client.post("/admin/view-as", data={"_csrf": token, "mode": "player"}).headers["Location"])


class ForumUpgradeTest(CommunityHelpers):
    def make_thread(self, category="general", title="Hello sky", body="First post"):
        token = self.csrf("/forum/%s/new" % category)
        return self.client.post("/forum/%s/new" % category, data={"_csrf": token, "title": title, "body": body})

    def test_ranks_titles_and_frames_show_on_posts(self):
        self.register("Flare")
        self.link()
        conn = self.db()
        conn.execute("UPDATE members SET rank_id = (SELECT id FROM ranks WHERE slug = 'admin'), custom_title = 'Game Designer'")
        conn.commit()
        conn.close()
        made = self.make_thread()
        page = self.client.get(made.headers["Location"]).get_data(as_text=True)
        self.assertIn("rank-tag rank-admin", page)
        self.assertIn("Game Designer", page)
        self.assertIn("framed-art", page)
        self.assertIn("frame-admin", page)
        css = self.client.get("/ranks.css").get_data(as_text=True)
        self.assertIn(".rank-admin{--rank:#F07A7A", css)

    def test_unranked_members_get_the_member_frame(self):
        self.register("Plain")
        profile = self.client.get("/members/Plain").get_data(as_text=True)
        self.assertIn("frame-member framed-sprite", profile)
        self.assertIn("img/avatars/sky-sentinel-256.webp", profile)
        self.assertIn("img/frames/member-256.webp", profile)
        self.assertIn("data-preview-frame", self.client.get("/account").get_data(as_text=True))
        visitor_profile = self.app.test_client().get("/members/Plain").get_data(as_text=True)
        self.assertIn(">Sign in</a>", visitor_profile)
        self.assertNotIn('class="nav-top nav-member"', visitor_profile)
        conn = self.db()
        frames = dict(conn.execute("SELECT slug, frame FROM ranks").fetchall())
        conn.close()
        self.assertEqual((frames["owner"], frames["moderator"], frames["skyfarer"], frames["media"]),
                         ("admin", "staff", "skyfarer", "media"))

    def test_admin_rank_can_post_announcements(self):
        self.register("Flare")
        self.link()
        self.assertIn(b"Only the Aerborne team posts here", self.client.get("/forum/announcements/new").data)
        conn = self.db()
        conn.execute("UPDATE members SET rank_id = (SELECT id FROM ranks WHERE slug = 'admin')")
        conn.commit()
        conn.close()
        self.assertEqual(self.make_thread("announcements", "Patch notes", "Fixed things").status_code, 302)

    def test_private_categories_hide_threads_from_other_players(self):
        self.register("Asker")
        self.link(code="ABCD2345", player="aaaaaaaa-0000-0000-0000-000000000001", name="Asker")
        made = self.make_thread("appeals", "Please unmute me", "I was muted by mistake")
        url = made.headers["Location"]
        self.assertEqual(self.client.get(url).status_code, 200)
        self.client.post("/account/logout", data={"_csrf": self.csrf("/account")})
        self.register("Snoop")
        self.assertEqual(self.client.get(url).status_code, 404)
        self.assertNotIn(b"Please unmute me", self.client.get("/forum/appeals").data)
        self.assertNotIn(b"Please unmute me", self.client.get("/forum").data)

    def test_likes_and_prefixes(self):
        self.register("Author")
        self.link(code="ABCD2345", player="aaaaaaaa-0000-0000-0000-000000000001", name="Author")
        token = self.csrf("/forum/help/new")
        self.client.post("/forum/help/new", data={"_csrf": token, "title": "How do I fish?", "body": "Help",
                                                  "prefix": "question"})
        self.assertIn(b"prefix-sky", self.client.get("/forum/help").data)
        bad = self.client.post("/forum/help/new", data={"_csrf": token, "title": "Wrong tag", "body": "x", "prefix": "selling"})
        self.assertIn(b"Pick a tag from the list", bad.data)
        conn = self.db()
        post_id = conn.execute("SELECT id FROM forum_posts").fetchone()[0]
        conn.close()
        own = self.client.post("/forum/post/%d/like" % post_id, data={"_csrf": token})
        self.assertEqual(own.status_code, 403, "No liking your own post")
        self.client.post("/account/logout", data={"_csrf": token})
        self.register("Fan")
        token = self.csrf("/account")
        self.client.post("/forum/post/%d/like" % post_id, data={"_csrf": token})
        conn = self.db()
        self.assertEqual(conn.execute("SELECT like_count FROM forum_posts WHERE id = ?", (post_id,)).fetchone()[0], 1)
        self.assertEqual(conn.execute("SELECT likes_received FROM members WHERE username = 'Author'").fetchone()[0], 1)
        conn.close()
        self.client.post("/forum/post/%d/like" % post_id, data={"_csrf": token})
        conn = self.db()
        self.assertEqual(conn.execute("SELECT like_count FROM forum_posts WHERE id = ?", (post_id,)).fetchone()[0], 0,
                         "A second click unlikes")
        conn.close()

    def test_profile_editing_validates_links_and_choices(self):
        self.register()
        token = self.csrf("/account")
        ok = self.client.post("/account", data={"_csrf": token, "action": "profile", "headline": "Sky farmer",
                                                "avatar": "companion:pufflet", "banner": "cindlet", "favorite_skill": "fishing",
                                                "youtube_url": "https://youtube.com/@skyfarer", "show_stats": "1"},
                              follow_redirects=True)
        self.assertIn(b"Profile saved", ok.data)
        profile = self.client.get("/members/Skyfarer").get_data(as_text=True)
        self.assertIn("Sky farmer", profile)
        self.assertIn("pufflet-avatar.webp", profile)
        self.assertIn("cindlet-720.webp", profile)
        bad = self.client.post("/account", data={"_csrf": token, "action": "profile", "youtube_url": "javascript:alert(1)"},
                               follow_redirects=True)
        self.assertIn(b"full YouTube channel link", bad.data)
        bad = self.client.post("/account", data={"_csrf": token, "action": "profile", "avatar": "../../etc/passwd"},
                               follow_redirects=True)
        self.assertIn(b"Pick one of the avatars", bad.data)

    def test_character_avatars_save_and_classic_choices_still_work(self):
        self.register()
        page = self.client.get('/account').get_data(as_text=True)
        self.assertIn('sprite:sky-sentinel', page)
        self.assertIn('Classic avatars', page)
        self.assertIn('sky-sentinel-256.webp', self.client.get('/members/Skyfarer').get_data(as_text=True))
        token = self.csrf('/account')
        saved = self.client.post('/account', data={'_csrf': token, 'action': 'profile',
            'avatar': 'sprite:moss-bunny', 'banner': 'skybound', 'headline': 'A home in the clouds'}, follow_redirects=True)
        self.assertIn(b'Profile saved', saved.data)
        profile = self.client.get('/members/Skyfarer').get_data(as_text=True)
        self.assertIn('moss-bunny-256.webp', profile)
        self.assertIn('img/forum/hero.png', profile)
        self.assertIn('A home in the clouds', profile)
        self.assertNotIn('class="mini-skills"', profile)
        classic = self.client.post('/account', data={'_csrf': token, 'action': 'profile',
            'avatar': 'crest', 'banner': 'sky'}, follow_redirects=True)
        self.assertIn(b'Profile saved', classic.data)
        self.assertIn('skycrest-192.webp', self.client.get('/members/Skyfarer').get_data(as_text=True))


class ArcadeTest(CommunityHelpers):
    REWARDS = "f" * 64

    def start(self, slug="sky-hop"):
        page = self.client.get("/arcade/" + slug).get_data(as_text=True)
        token = re.search(r'data-csrf="([^"]+)"', page).group(1)
        res = self.client.post("/arcade/%s/start" % slug, headers={"X-CSRF-Token": token}, json={})
        return token, res.get_json()

    def finish(self, csrf, run, score, seconds_ago=60):
        conn = self.db()
        conn.execute("UPDATE arcade_runs SET started_at = datetime('now', ?) WHERE token = ?", ("-%d seconds" % seconds_ago, run))
        conn.commit()
        conn.close()
        return self.client.post("/arcade/run/%s/finish" % run, headers={"X-CSRF-Token": csrf}, json={"score": score}).get_json()

    def test_visitors_play_for_practice(self):
        self.assertEqual(self.client.get("/arcade/").status_code, 200)
        for slug in ("sky-hop", "gem-rush", "cloud-catch", "codex-memory", "forge-strike"):
            self.assertEqual(self.client.get("/arcade/" + slug).status_code, 200, slug)
        _token, data = self.start()
        self.assertTrue(data["practice"])

    def test_linked_players_earn_capped_marks_and_the_server_collects_them(self):
        self.register()
        self.link()
        csrf, data = self.start()
        result = self.finish(csrf, data["token"], 30)
        self.assertEqual(result["marks"], 15, "30 gaps at 1 Mark per 2, capped at 15")
        again = self.client.post("/arcade/run/%s/finish" % data["token"], headers={"X-CSRF-Token": csrf}, json={"score": 30})
        self.assertEqual(again.status_code, 409, "A finished run can't be submitted twice")
        csrf, data = self.start()
        cheat = self.finish(csrf, data["token"], 300, seconds_ago=10)
        self.assertEqual(cheat["marks"], 0)
        self.assertFalse(cheat["ok"])
        self.app.config["REWARDS_TOKEN"] = self.REWARDS
        self.assertEqual(self.client.get("/admin/api/arcade-payouts").status_code, 401)
        feed = self.client.get("/admin/api/arcade-payouts", headers={"Authorization": "Bearer " + self.REWARDS}).get_json()
        self.assertEqual([(p["marks"], p["accountId"]) for p in feed["payouts"]], [(15, "acct")])
        acked = self.client.get("/admin/api/arcade-payouts?ack=%d" % feed["payouts"][0]["id"],
                                headers={"Authorization": "Bearer " + self.REWARDS}).get_json()
        self.assertEqual(acked["payouts"], [])

    def test_daily_cap_and_unlinked_accounts(self):
        self.register()
        csrf, data = self.start()
        self.assertEqual(self.finish(csrf, data["token"], 20)["marks"], 0, "Not linked yet")
        self.link()
        conn = self.db()
        conn.execute("UPDATE settings SET value = '20' WHERE key = 'arcade_daily_cap'")
        conn.commit()
        conn.close()
        csrf, data = self.start()
        self.assertEqual(self.finish(csrf, data["token"], 30)["marks"], 15)
        csrf, data = self.start("forge-strike")
        self.assertEqual(self.finish(csrf, data["token"], 30)["marks"], 5, "Only 5 Marks left under the 20 cap")
        csrf, data = self.start("forge-strike")
        self.assertEqual(self.finish(csrf, data["token"], 30)["marks"], 0)

    def test_invalid_score_payload_and_banned_member_cannot_collect(self):
        self.register()
        self.link()
        csrf, data = self.start()
        endpoint = "/arcade/run/%s/finish" % data["token"]
        bad = self.client.post(endpoint, headers={"X-CSRF-Token": csrf}, json=[30])
        self.assertEqual(bad.status_code, 400)
        invalid = self.client.post(endpoint, headers={"X-CSRF-Token": csrf}, json={"score": "Infinity"})
        self.assertFalse(invalid.get_json()["ok"])
        self.assertEqual(invalid.get_json()["marks"], 0)
        csrf, data = self.start()
        endpoint = "/arcade/run/%s/finish" % data["token"]
        conn = self.db()
        conn.execute("UPDATE members SET status = 'banned'")
        conn.commit()
        conn.close()
        banned = self.client.post(endpoint, headers={"X-CSRF-Token": csrf}, json={"score": 30})
        self.assertEqual(banned.status_code, 403)

    def test_delivery_notice_matches_configuration(self):
        self.assertIn(b"delivery is being connected", self.client.get("/arcade/").data)
        self.app.config["REWARDS_TOKEN"] = self.REWARDS
        self.assertNotIn(b"delivery is being connected", self.client.get("/arcade/").data)


class FakeServer(threading.Thread):
    """Answers one Server List Ping with a canned status."""

    def __init__(self, payload):
        threading.Thread.__init__(self)
        self.daemon = True
        self.payload = json.dumps(payload).encode("utf-8")
        self.sock = socket.socket()
        self.sock.bind(("127.0.0.1", 0))
        self.sock.listen(1)
        self.port = self.sock.getsockname()[1]

    def run(self):
        conn, _ = self.sock.accept()
        conn.recv(1024)
        body = status._pack_varint(0) + status._pack_varint(len(self.payload)) + self.payload
        conn.sendall(status._pack_varint(len(body)) + body)
        conn.close()
        self.sock.close()


class StatusTest(unittest.TestCase):
    def setUp(self):
        status.clear_cache()

    def test_varint(self):
        self.assertEqual(status._pack_varint(0), b"\x00")
        self.assertEqual(status._pack_varint(300), b"\xac\x02")
        self.assertEqual(status._pack_varint(-1), b"\xff\xff\xff\xff\x0f")

    def test_ping_online(self):
        server = FakeServer({"version": {"name": "Velocity 26.2"}, "players": {"online": 3, "max": 30}})
        server.start()
        result = status.get_status(("127.0.0.1", server.port), timeout=2, cache_seconds=30)
        self.assertEqual(result["state"], "online")
        self.assertEqual((result["players"], result["max"]), (3, 30))
        self.assertEqual(result["last_online_at"], result["checked_at"])

    def test_ping_offline(self):
        probe = socket.socket()
        probe.bind(("127.0.0.1", 0))
        port = probe.getsockname()[1]
        probe.close()
        result = status.get_status(("127.0.0.1", port), timeout=1, cache_seconds=30)
        self.assertEqual(result["state"], "offline")
        self.assertIsNone(result["last_online_at"])

    def test_address_with_port(self):
        cfg = {"SERVER_ADDRESS": "play.example.com:25570", "SERVER_PORT": 25565}
        self.assertEqual(status_target(cfg), ("play.example.com", 25570))


if __name__ == "__main__":
    unittest.main()
