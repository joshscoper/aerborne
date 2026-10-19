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
from html.parser import HTMLParser
import zipfile

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from unittest import mock  # noqa: E402

from aerborne import auth, create_app, gameapi, status  # noqa: E402
from aerborne.config import status_target, status_uses_srv  # noqa: E402
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
    def test_reference_catalog_and_internal_links(self):
        class Links(HTMLParser):
            def __init__(self):
                HTMLParser.__init__(self)
                self.hrefs = set()
            def handle_starttag(self, tag, attrs):
                if tag == 'a':
                    href = dict(attrs).get('href', '')
                    if href.startswith('/') and not href.startswith('//'):
                        self.hrefs.add(href.split('#')[0])
        conn = self.db()
        paths = ['/guide', '/explore', '/codex', '/companions']
        for table, prefix in [('guides','/guide/'), ('regions','/explore/'), ('companions','/companions/')]:
            paths.extend(prefix + row['slug'] for row in conn.execute("SELECT slug FROM " + table + " WHERE status = 'published'"))
        from aerborne.content_types import GUIDE_CATEGORIES
        paths.extend('/guide/section/' + key for key, label in GUIDE_CATEGORIES)
        links = Links()
        for path in paths:
            response = self.client.get(path)
            self.assertEqual(response.status_code, 200, path)
            links.feed(response.get_data(as_text=True))
        for path in links.hrefs:
            response = self.client.get(path)
            self.assertIn(response.status_code, (200, 302), path)
        self.assertGreaterEqual(len(paths), 50)
        conn.close()

    def test_reviewed_copy_and_private_preview_filters(self):
        from aerborne.reference_content import GUIDES
        conn = self.db()
        self.assertEqual(len(GUIDES), 33)
        for guide in GUIDES:
            row = conn.execute('SELECT body FROM guides WHERE slug = ?', (guide['slug'],)).fetchone()
            self.assertEqual(row['body'], guide['body'], guide['slug'])
        grave = self.client.get('/guide/graves').get_data(as_text=True)
        for fact in ('250 Marks', '27 stacks', 'restart', 'payment immediately'):
            self.assertIn(fact, grave)
        active = self.client.get('/codex?availability=released').get_data(as_text=True)
        self.assertIn('Keen Edge', active)
        self.assertNotIn('id="stormwake"', active)
        preview = self.client.get('/codex?availability=preview').get_data(as_text=True)
        self.assertIn('id="stormwake"', preview)
        self.assertNotIn('id="keen-edge"', preview)
        hidden = self.client.get('/codex?availability=preview&aspect=celerity').get_data(as_text=True)
        self.assertNotIn('id="stormwake"', hidden)
        self.assertNotIn('id="chain-harvest"', hidden)
        self.assertEqual(conn.execute("SELECT concept_art FROM companions WHERE slug='pufflet'").fetchone()[0], 1)
        conn.close()

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
        conn.execute("UPDATE guides SET summary = 'Owner summary' WHERE slug = 'fishing'")
        conn.execute("INSERT INTO faqs (slug, question, answer, category, status) VALUES (?, ?, ?, ?, ?)",
                     ('whitelist', "Why can't I join yet?", 'Old testing notice', 'connecting', 'published'))
        conn.execute("UPDATE guides SET body = body || ? WHERE slug = 'connection-help'",
                     ("\n- **You are not white-listed.** Aerborne is in private testing. Ask staff to add you.\n",))
        conn.execute("UPDATE settings SET value = '3' WHERE key = 'seed_rev'")
        conn.commit()
        seed_updates.apply(conn)
        self.assertEqual(conn.execute("SELECT body FROM pages WHERE slug = 'privacy'").fetchone()[0], 'Owner policy')
        self.assertEqual(conn.execute("SELECT body FROM guides WHERE slug = 'milestone-planning'").fetchone()[0], 'Owner guide')
        self.assertEqual(conn.execute("SELECT summary FROM guides WHERE slug = 'fishing'").fetchone()[0], 'Owner summary')
        self.assertFalse(seed_updates.apply(conn))
        self.assertEqual(conn.execute("SELECT status FROM faqs WHERE slug = 'whitelist'").fetchone()[0], 'draft')
        self.assertNotIn('private testing', conn.execute("SELECT body FROM guides WHERE slug = 'connection-help'").fetchone()[0])
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

    def test_status_reports_live_and_maintenance_states(self):
        self.app.config["SERVER_ADDRESS"] = "play.example.com"
        self.app.config["SERVER_PORT"] = 25565
        payload = {"players": {"online": 2, "max": 20}, "version": {"name": "Paper 26.2"}}
        with mock.patch.object(status, "ping", return_value=(payload, 75)) as ping:
            data = self.client.get("/api/v1/status").get_json()
        self.assertEqual((data["state"], data["players"], data["max"], data["latency_ms"]),
                         ("online", 2, 20, 75))
        ping.assert_called_once_with("play.example.com", 25565, 3, use_srv=True)
        conn = self.db()
        conn.execute("INSERT OR REPLACE INTO settings (key, value) VALUES ('maintenance', '1')")
        conn.commit()
        conn.close()
        self.assertEqual(self.client.get("/api/v1/status").get_json()["state"], "maintenance")
        self.assertIn(b'data-status-refresh', self.client.get("/").data)
        self.assertIn(b'data-field="latency"', self.client.get("/status").data)

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
        for slug in ("sky-hop", "gem-rush", "cloud-catch", "codex-memory", "forge-strike", "rune-breaker"):
            self.assertEqual(self.client.get("/arcade/" + slug).status_code, 200, slug)
        _token, data = self.start()
        self.assertTrue(data["practice"])

    def test_linked_players_earn_capped_marks_and_the_server_collects_them(self):
        self.register()
        self.link()
        csrf, data = self.start()
        result = self.finish(csrf, data["token"], 30)
        self.assertEqual(result["marks"], 75, "30 gaps at 3 Marks per gap, capped at 75")
        again = self.client.post("/arcade/run/%s/finish" % data["token"], headers={"X-CSRF-Token": csrf}, json={"score": 999999})
        self.assertEqual(again.status_code, 200, "A lost receipt can be retrieved safely")
        self.assertEqual(again.get_json()["marks"], 75)
        self.assertEqual(again.get_json()["score"], 30, "Retries return the stored score, regardless of the new payload")
        self.assertEqual(again.get_json()["pending"], 75)
        csrf, data = self.start()
        cheat = self.finish(csrf, data["token"], 300, seconds_ago=10)
        self.assertEqual(cheat["marks"], 0)
        self.assertFalse(cheat["ok"])
        self.app.config["REWARDS_TOKEN"] = self.REWARDS
        self.assertEqual(self.client.get("/admin/api/arcade-payouts").status_code, 401)
        feed = self.client.get("/admin/api/arcade-payouts", headers={"Authorization": "Bearer " + self.REWARDS}).get_json()
        self.assertEqual([(p["marks"], p["accountId"]) for p in feed["payouts"]], [(50, "acct"), (25, "acct")])
        acked = self.client.get("/admin/api/arcade-payouts?ack=%d" % feed["payouts"][-1]["id"],
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
        self.assertEqual(self.finish(csrf, data["token"], 5)["marks"], 15)
        csrf, data = self.start("forge-strike")
        self.assertEqual(self.finish(csrf, data["token"], 30)["marks"], 5, "Only 5 Marks left under the 20 cap")
        csrf, data = self.start("forge-strike")
        self.assertEqual(self.finish(csrf, data["token"], 30)["marks"], 0)

    def test_all_six_games_pay_the_new_caps_in_compatible_chunks(self):
        self.register()
        self.link()
        total = 0
        for slug, score, expected in (("sky-hop", 25, 75), ("gem-rush", 2000, 100),
                                      ("cloud-catch", 40, 75), ("codex-memory", 500, 100),
                                      ("forge-strike", 500, 75), ("rune-breaker", 1000, 100)):
            csrf, data = self.start(slug)
            result = self.finish(csrf, data["token"], score)
            self.assertTrue(result["ok"], slug)
            self.assertEqual(result["marks"], expected, slug)
            total += expected
        conn = self.db()
        payouts = [r[0] for r in conn.execute("SELECT marks FROM arcade_payouts")]
        self.assertTrue(all(0 < n <= 50 for n in payouts))
        self.assertEqual(sum(payouts), total)
        conn.close()

    def test_default_daily_allowance_stops_at_one_thousand(self):
        self.register()
        self.link()
        paid = []
        for i in range(15):
            csrf, data = self.start()
            paid.append(self.finish(csrf, data["token"], 25)["marks"])
        self.assertEqual(paid, [75] * 13 + [25, 0])

    def test_allowance_migration_preserves_staff_limits(self):
        from aerborne import seed_updates
        conn = self.db()
        for before, expected in (("200", "1000"), ("0", "0"), ("750", "750")):
            conn.execute("UPDATE settings SET value = '6' WHERE key = 'seed_rev'")
            conn.execute("UPDATE settings SET value = ? WHERE key = 'arcade_daily_cap'", (before,))
            conn.commit()
            seed_updates.apply(conn)
            self.assertEqual(conn.execute("SELECT value FROM settings WHERE key = 'arcade_daily_cap'").fetchone()[0], expected)
        conn.close()

    def test_early_finish_and_other_members_receipts_do_not_pay(self):
        self.register()
        self.link()
        csrf, data = self.start("gem-rush")
        result = self.finish(csrf, data["token"], 120, seconds_ago=5)
        self.assertTrue(result["ok"])
        self.assertEqual(result["marks"], 0)
        self.assertIn("at least 20 seconds", result["message"])
        self.client.post("/account/logout", data={"_csrf": csrf})
        self.register("AnotherPlayer")
        new_csrf, _ = self.start()
        denied = self.client.post("/arcade/run/%s/finish" % data["token"], headers={"X-CSRF-Token": new_csrf}, json={"score": 100})
        self.assertEqual(denied.status_code, 409)

    def test_paid_run_limit_still_applies_with_a_larger_allowance(self):
        self.register()
        self.link()
        conn = self.db()
        conn.execute("UPDATE settings SET value = '5000' WHERE key = 'arcade_daily_cap'")
        conn.commit()
        conn.close()
        for i in range(16):
            csrf, data = self.start()
            result = self.finish(csrf, data["token"], 1)
            self.assertEqual(result["marks"], 3 if i < 15 else 0)

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


class SocialHelpers(CommunityHelpers):
    SOCIAL = "c" * 64
    ACCOUNTS = {"Ava": "aaaaaaaa-0000-4000-8000-000000000001", "Bjorn": "bbbbbbbb-0000-4000-8000-000000000002",
                "Cyra": "cccccccc-0000-4000-8000-000000000003"}

    def setUp(self):
        CommunityHelpers.setUp(self)
        self.app.config["SOCIAL_TOKEN"] = self.SOCIAL

    def sign_out(self):
        page = self.client.get("/account")
        if page.status_code == 200:
            token = re.search(r'name="_csrf" value="([^"]+)"', page.get_data(as_text=True)).group(1)
            self.client.post("/account/logout", data={"_csrf": token})

    def member(self, name, linked=True):
        """Registers name (signing out whoever was signed in) and links it to its game account."""
        self.sign_out()
        self.register(name)
        if linked:
            conn = self.db()
            conn.execute("UPDATE members SET mc_uuid = ?, mc_name = ?, mc_account_id = ?, linked_at = '2026-01-01 00:00:00' "
                         "WHERE username = ?", (self.ACCOUNTS[name], name, self.ACCOUNTS[name], name))
            conn.commit()
            conn.close()

    def sign_in(self, name):
        self.sign_out()
        token = self.csrf("/account/login")
        self.client.post("/account/login", data={"_csrf": token, "username": name, "password": "cloud-castle-9"})

    def act(self, name, action):
        return self.client.post("/friends/" + name, data={"_csrf": self.csrf("/account"), "action": action}, follow_redirects=True)

    def send(self, name, body):
        return self.client.post("/messages/" + name, data={"_csrf": self.csrf("/account"), "body": body}, follow_redirects=True)

    def outbox(self):
        conn = self.db()
        rows = [tuple(r) for r in conn.execute("SELECT kind, actor_account, target_account FROM social_outbox ORDER BY id")]
        conn.close()
        return rows

    def sync(self, payload, token=None):
        return self.client.post("/admin/api/social-sync", json=payload,
                                headers={"Authorization": "Bearer " + (token or self.SOCIAL)})


class SocialTest(SocialHelpers):
    def test_friend_requests_notify_and_sync_to_the_server(self):
        self.member("Ava")
        self.member("Bjorn")
        self.assertIn(b"Friend request sent to Ava", self.act("Ava", "add").data)
        self.sign_in("Ava")
        self.assertIn(b'class="nav-badge">1<', self.client.get("/").data)
        notes = self.client.get("/notifications").get_data(as_text=True)
        self.assertIn("sent you a friend request", notes)
        self.assertIn(b"You and Bjorn are now friends", self.act("Bjorn", "accept").data)
        self.assertIn(b"Bjorn", self.client.get("/friends").data)
        self.assertEqual(self.outbox(), [("friend", self.ACCOUNTS["Ava"], self.ACCOUNTS["Bjorn"])])
        self.act("Bjorn", "remove")
        self.assertEqual(self.outbox()[-1], ("unfriend", self.ACCOUNTS["Ava"], self.ACCOUNTS["Bjorn"]))
        self.sign_in("Bjorn")
        self.assertIn("accepted your friend request", self.client.get("/notifications").get_data(as_text=True))

    def test_blocking_stops_messages_and_requests_and_folds_posts(self):
        self.member("Ava")
        self.member("Bjorn")
        self.act("Ava", "add")
        self.sign_in("Ava")
        self.act("Bjorn", "block")
        conn = self.db()
        self.assertEqual(conn.execute("SELECT COUNT(*) FROM friend_requests").fetchone()[0], 0)
        conn.close()
        self.assertEqual(self.outbox()[-1], ("block", self.ACCOUNTS["Ava"], self.ACCOUNTS["Bjorn"]))
        self.sign_in("Bjorn")
        self.assertIn(b"can&#39;t message this member", self.send("Ava", "hello?").data)
        self.assertIn(b"can&#39;t add Ava", self.act("Ava", "add").data)
        token = self.csrf("/forum/general/new")
        made = self.client.post("/forum/general/new", data={"_csrf": token, "title": "Bjorn's island", "body": "Look at my farm"})
        self.sign_in("Ava")
        page = self.client.get(made.headers["Location"]).get_data(as_text=True)
        self.assertIn("You blocked Bjorn. Show this post", page)

    def test_messages_inbox_privacy_and_reports(self):
        self.member("Ava")
        self.member("Bjorn")
        self.send("Ava", "Want to trade rubies?")
        self.assertEqual(self.outbox(), [("message", self.ACCOUNTS["Bjorn"], self.ACCOUNTS["Ava"])])
        self.sign_in("Ava")
        self.assertIn(b'aria-label="Messages, 1 unread"', self.client.get("/").data)
        inbox = self.client.get("/messages").get_data(as_text=True)
        self.assertIn("Want to trade rubies?", inbox)
        chat = self.client.get("/messages/Bjorn").get_data(as_text=True)
        self.assertIn("Want to trade rubies?", chat)
        self.assertNotIn('aria-label="Messages, 1 unread"', self.client.get("/").get_data(as_text=True))
        conn = self.db()
        message_id = conn.execute("SELECT id FROM messages").fetchone()[0]
        conn.close()
        self.client.post("/messages/report/%d" % message_id, data={"_csrf": self.csrf("/account"), "reason": "scam"})
        token = self.csrf("/account")
        self.client.post("/account", data={"_csrf": token, "action": "profile", "dm_policy": "friends", "friend_requests_open": "1"})
        self.member("Cyra")
        self.assertIn(b"Ava only accepts messages from friends", self.send("Ava", "hi").data)
        self.add_user("warden", "moderator")
        self.login("warden")
        reports = self.client.get("/admin/forum/reports").get_data(as_text=True)
        self.assertIn("Want to trade rubies?", reports)
        self.assertIn("scam", reports)

    def test_replies_and_likes_notify_the_author(self):
        self.member("Ava")
        token = self.csrf("/forum/general/new")
        made = self.client.post("/forum/general/new", data={"_csrf": token, "title": "Sky farms", "body": "Show yours"})
        thread = made.headers["Location"]
        self.member("Bjorn")
        thread_id = int(thread.split("/thread/")[1].split("/")[0])
        self.client.post("/forum/thread/%d/reply" % thread_id, data={"_csrf": self.csrf(thread), "body": "Here's mine!"})
        conn = self.db()
        first = conn.execute("SELECT MIN(id) FROM forum_posts").fetchone()[0]
        conn.close()
        self.client.post("/forum/post/%d/like" % first, data={"_csrf": self.csrf(thread)})
        self.sign_in("Ava")
        notes = self.client.get("/notifications").get_data(as_text=True)
        self.assertIn("replied to your thread", notes)
        self.assertIn("liked your post in", notes)
        self.assertIn("Sky farms", notes)

    def test_server_sync_applies_game_changes_once_and_returns_website_changes(self):
        self.member("Ava")
        self.member("Bjorn")
        self.send("Ava", "From the website")
        self.assertEqual(self.sync({"apiVersion": 1}, token="d" * 64).status_code, 401)
        self.assertEqual(self.sync({"apiVersion": 2}).status_code, 400)
        events = [{"seq": 1, "type": "friend", "actor": self.ACCOUNTS["Ava"], "target": self.ACCOUNTS["Bjorn"]},
                  {"seq": 2, "type": "message", "actor": self.ACCOUNTS["Ava"], "target": self.ACCOUNTS["Bjorn"], "body": "Sent with /msg"},
                  {"seq": 3, "type": "block", "actor": self.ACCOUNTS["Ava"], "target": "dddddddd-0000-4000-8000-000000000004"}]
        first = self.sync({"apiVersion": 1, "ack": 0, "events": events}).get_json()
        self.assertEqual(first["accepted"], 3)
        self.assertEqual([(e["type"], e["body"]) for e in first["events"]], [("message", "From the website")])
        linked = {row["member"]: row for row in first["linked"]}
        self.assertEqual(linked["Ava"]["unreadMessages"], 1)
        self.assertEqual(linked["Bjorn"]["account"], self.ACCOUNTS["Bjorn"])
        again = self.sync({"apiVersion": 1, "ack": first["events"][-1]["id"], "events": events}).get_json()
        self.assertEqual(again["events"], [], "Acknowledged website events are not sent again")
        conn = self.db()
        self.assertEqual(conn.execute("SELECT COUNT(*) FROM friendships").fetchone()[0], 1)
        self.assertEqual(conn.execute("SELECT COUNT(*) FROM messages WHERE source = 'game'").fetchone()[0], 1,
                         "Replaying the same events changes nothing")
        conn.close()
        self.assertEqual(self.outbox(), [], "Changes from the server are never sent back to it")
        self.sign_in("Bjorn")
        chat = self.client.get("/messages/Ava").get_data(as_text=True)
        self.assertIn("Sent with /msg", chat)
        self.assertIn("sent from in game", chat)
        self.assertIn("is now your friend (added in game)", self.client.get("/notifications").get_data(as_text=True))

    def test_linking_asks_the_server_for_existing_friends(self):
        self.member("Ava")
        self.member("Bjorn", linked=False)
        self.act("Ava", "add")
        self.sign_in("Ava")
        self.act("Bjorn", "accept")
        self.assertEqual(self.outbox(), [], "Bjorn isn't in game yet")
        self.sign_in("Bjorn")
        links = {"ABCD2345": {"apiVersion": 1, "accountId": self.ACCOUNTS["Bjorn"], "playerId": self.ACCOUNTS["Bjorn"], "name": "Bjorn"}}
        with mock.patch.object(gameapi, "_request", side_effect=fake_api(links)):
            token = self.csrf("/account/link")
            self.client.post("/account/link", data={"_csrf": token, "code": "ABCD-2345"})
        self.assertEqual(self.outbox(), [("link", self.ACCOUNTS["Bjorn"], self.ACCOUNTS["Bjorn"]),
                                         ("friend", self.ACCOUNTS["Bjorn"], self.ACCOUNTS["Ava"])])


class MemberManagementTest(SocialHelpers):
    def test_directory_lists_searches_and_hides_banned(self):
        self.member("Ava")
        self.member("Bjorn")
        conn = self.db()
        conn.execute("UPDATE members SET status = 'banned' WHERE username = 'Bjorn'")
        conn.commit()
        conn.close()
        page = self.client.get("/members").get_data(as_text=True)
        self.assertIn("Ava", page)
        self.assertNotIn("Bjorn&#39;s profile", page)
        self.assertIn("/members", self.client.get("/forum").get_data(as_text=True))
        self.assertNotIn(">Ava<", self.client.get("/members?q=zzz").get_data(as_text=True))

    def test_members_download_their_own_data_without_the_password(self):
        self.member("Ava")
        self.member("Bjorn")
        self.send("Ava", "Hello there")
        res = self.client.get("/account/data")
        self.assertEqual(res.mimetype, "application/zip")
        archive = zipfile.ZipFile(io.BytesIO(res.data))
        self.assertIn("messages.json", archive.namelist())
        self.assertIn("Hello there", archive.read("messages.json").decode("utf-8"))
        profile = archive.read("profile.json").decode("utf-8")
        self.assertIn("Bjorn", profile)
        self.assertNotIn("password_hash", profile)
        self.assertNotIn("pbkdf2", profile)

    def test_admins_export_and_delete_while_moderators_cannot(self):
        self.member("Ava")
        token = self.csrf("/forum/general/new")
        made = self.client.post("/forum/general/new", data={"_csrf": token, "title": "Ava's farm", "body": "Wheat everywhere"})
        thread = made.headers["Location"]
        self.member("Bjorn")
        self.send("Ava", "Nice farm")
        self.act("Ava", "add")
        conn = self.db()
        ava = conn.execute("SELECT id FROM members WHERE username = 'Ava'").fetchone()[0]
        conn.close()
        self.add_user("warden", "moderator")
        self.login("warden")
        self.assertEqual(self.client.get("/admin/forum/members/%d/manage" % ava).status_code, 200)
        self.assertEqual(self.client.get("/admin/forum/members/%d/export" % ava).status_code, 403)
        token = self.csrf("/admin/forum/members/%d/manage" % ava)
        denied = self.client.post("/admin/forum/members/%d/action" % ava, data={"_csrf": token, "action": "delete", "confirm": "Ava"})
        self.assertEqual(denied.status_code, 403)
        self.client.post("/admin/forum/members/%d/action" % ava, data={"_csrf": token, "action": "signout"})
        self.add_user("boss", "admin")
        self.login("boss")
        export = self.client.get("/admin/forum/members/%d/export" % ava)
        self.assertIn("posts.json", zipfile.ZipFile(io.BytesIO(export.data)).namelist())
        token = self.csrf("/admin/forum/members/%d/manage" % ava)
        wrong = self.client.post("/admin/forum/members/%d/action" % ava, data={"_csrf": token, "action": "delete", "confirm": "ava?"},
                                 follow_redirects=True)
        self.assertIn(b"Type the member", wrong.data)
        self.client.post("/admin/forum/members/%d/action" % ava, data={"_csrf": token, "action": "delete", "confirm": "Ava"})
        conn = self.db()
        self.assertEqual(conn.execute("SELECT COUNT(*) FROM messages").fetchone()[0], 0)
        self.assertEqual(conn.execute("SELECT COUNT(*) FROM friend_requests").fetchone()[0], 0)
        self.assertEqual(conn.execute("SELECT status, mc_account_id FROM members WHERE id = ?", (ava,)).fetchone()[:], ("deleted", None))
        actions = [r[0] for r in conn.execute("SELECT action FROM audit_log WHERE entity = 'members' ORDER BY id")]
        conn.close()
        self.assertEqual(actions, ["signout", "export", "delete"])
        self.assertEqual(self.client.get("/members/Ava").status_code, 404)
        page = self.client.get(thread).get_data(as_text=True)
        self.assertIn("Deleted member %d" % ava, page)
        self.assertIn("Wheat everywhere", page, "Posts stay unless staff remove them too")
        self.assertEqual(self.register("Ava").status_code, 302, "The username is free again")


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
        self.request = conn.recv(1024)
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

    def test_srv_discovery_and_explicit_overrides(self):
        cfg = {"SERVER_ADDRESS": "play.example.com", "SERVER_PORT": 25565}
        self.assertTrue(status_uses_srv(cfg))
        for overrides in ({"SERVER_ADDRESS": "play.example.com:25565"}, {"SERVER_PORT": 9025},
                          {"STATUS_HOST": "backend.example.com"}, {"STATUS_PORT": 25565}):
            self.assertFalse(status_uses_srv(dict(cfg, **overrides)))
        record = mock.Mock(priority=0, weight=5, port=9025, target="node.example.com.")
        with mock.patch.object(status.dns.resolver, "resolve", return_value=[record]) as resolve:
            self.assertEqual(status._resolve_target("play.example.com", 25565, 3), ("node.example.com", 9025))
            resolve.assert_called_once_with("_minecraft._tcp.play.example.com.", "SRV", lifetime=3, search=False)

    def test_srv_fallback_and_ip_addresses(self):
        with mock.patch.object(status.dns.resolver, "resolve", side_effect=status.dns.resolver.NXDOMAIN):
            self.assertEqual(status._resolve_target("play.example.com", 25565, 1), ("play.example.com", 25565))
        with mock.patch.object(status.dns.resolver, "resolve") as resolve:
            self.assertEqual(status._resolve_target("127.0.0.1", 25565, 1), ("127.0.0.1", 25565))
            self.assertEqual(status._resolve_target("play.example.com", 9025, 1), ("play.example.com", 9025))
            resolve.assert_not_called()

    def test_srv_ping_keeps_public_handshake_host(self):
        server = FakeServer({"version": {"name": "Paper 26.2"}, "players": {"online": 4, "max": 20}})
        server.start()
        with mock.patch.object(status, "_resolve_target", return_value=("127.0.0.1", server.port)):
            result = status.get_status(("public.example.com", 25565))
        self.assertEqual((result["state"], result["players"], result["max"]), ("online", 4, 20))
        self.assertIn(b"public.example.com", server.request)

    def test_cached_success_is_replaced_by_offline(self):
        target = ("play.example.com", 25565)
        payload = {"players": {"online": 4, "max": 20}, "version": {"name": "Paper"}}
        with mock.patch.object(status, "ping", return_value=(payload, 80)) as ping:
            result = status.get_status(target)
            self.assertEqual(status.get_status(target), result)
            self.assertEqual(ping.call_count, 1)
        with mock.patch.object(status.time, "time", return_value=result["checked_at"] + 31):
            with mock.patch.object(status, "ping", side_effect=OSError("offline")):
                offline = status.get_status(target)
        self.assertEqual(offline["state"], "offline")
        self.assertNotIn("players", offline)
        self.assertEqual(offline["last_online_at"], result["checked_at"])


if __name__ == "__main__":
    unittest.main()
