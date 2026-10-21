import hashlib
import os
import shutil
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from aerborne import create_app, gameapi, reference_content, seed_updates, status  # noqa: E402
from aerborne.db import connect  # noqa: E402

EXPEDITIONS = {
    "apiVersion": 1, "ready": True, "sampledAt": "2026-10-20T12:00:00Z", "activeRuns": 1, "maxInstances": 6,
    "vanillaPortals": False,
    "expeditions": [
        {"id": "hollow_barrow", "title": "The Hollow Barrow", "description": "A barrow-king stirs.", "dimension": "overworld",
         "level": 8, "minParty": 1, "maxParty": 5, "rooms": 3, "bosses": 1, "timeLimitMinutes": 25, "activeRuns": 1,
         "sealedBy": None, "difficulty": {"rating": 2, "label": "Moderate"},
         "guide": {"overview": "A short crypt crawl.", "tips": ["Bring a shield."],
                   "rooms": [{"title": "The Crypt Gallery", "type": "waves", "waves": 2, "tip": "Take out the archers early."},
                             {"title": "The Ossuary", "type": "survive", "seconds": 45, "tip": ""}],
                   "boss": "A ring of light marks the shockwave."},
         "bestiary": [{"name": "The Hollow Huntsman", "role": "Boss", "rooms": ["The King's Rest"], "health": 380, "damage": 11,
                       "ability": "Shockwave: 9 damage within 7 blocks every 9s.",
                       "drops": [{"item": "Hollowfang Blade (relic)", "chance": 0.35, "min": 1, "max": 1},
                                 {"item": "Coin purse (Marks)", "chance": 1.0, "min": 75, "max": 125}]}],
         "rewards": {"marks": 120, "items": [{"name": "Iron ingot", "amount": 4}], "profileRolls": False},
         "tiers": [{"id": "normal", "title": "Normal", "level": 8, "lives": 10, "lootRolls": 1, "modifiers": [],
                    "clears": 12, "bestSeconds": 432, "bestParty": ["Ash", "Bryn", "<script>"]},
                   {"id": "mythic", "title": "Mythic", "level": 24, "lives": 3, "lootRolls": 3, "requires": "heroic",
                    "modifiers": [{"id": "fortified", "title": "Fortified", "description": "Tougher enemies."}],
                    "clears": 0, "bestSeconds": 0, "bestParty": []}]},
        {"id": "cinder_keep", "title": "Cinder Keep", "description": "A fortress of slag.", "dimension": "nether",
         "level": 20, "minParty": 2, "maxParty": 5, "rooms": 3, "bosses": 1, "timeLimitMinutes": 30, "activeRuns": 0,
         "sealedBy": "The Ember Gate", "tiers": []},
    ],
}
CAMPAIGN = {
    "apiVersion": 1, "ready": True, "sampledAt": "2026-10-20T12:00:00Z", "id": "ember-accord", "title": "The Ember Accord",
    "description": "Rekindle the sky-forges.", "startsAt": "2026-10-20T00:00:00Z", "endsAt": "2026-12-01T00:00:00Z",
    "active": True, "playerDailyCap": 2500,
    "goals": [{"id": "forge-fires", "title": "Stoke the Forges", "description": "Ore and fuel.", "points": 12500, "target": 50000,
               "sources": [{"type": "mine", "points": 2, "filters": ["iron_ore"]}],
               "top": [{"name": "Ash", "points": 900}, {"name": "bad name!", "points": 5}]}],
    "milestones": [{"id": "first-spark", "title": "First Spark", "description": "", "reached": True, "reachedAt": "2026-10-20T10:00:00Z",
                    "requires": [{"goal": "forge-fires", "amount": 8000, "points": 8000}],
                    "rewards": [{"type": "buff", "text": "Haste for everyone, 6 hours"}]},
                   {"id": "ember-gate", "title": "The Ember Gate", "description": "Opens Cinder Keep.", "reached": False,
                    "requires": [{"goal": "forge-fires", "amount": 20000, "points": 12500}],
                    "rewards": [{"type": "unlock", "text": "Opens the Cinder Keep expedition"}]}],
    "buffs": [{"name": "Haste", "from": "First Spark", "endsAt": "2026-10-20T16:00:00Z"}],
    "topContributors": [{"name": "Ash", "points": 900}],
}


def fake_api(path, token=None):
    return {"/api/v1/expeditions": (EXPEDITIONS, None), "/api/v1/campaign": (CAMPAIGN, None)}.get(path, (None, "not_found"))


class ExpeditionCampaignPagesTest(unittest.TestCase):
    def setUp(self):
        self.data = tempfile.mkdtemp()
        os.environ["AERBORNE_DATA_DIR"] = self.data
        self.app = create_app({"TESTING": True, "SESSION_COOKIE_SECURE": False, "SETUP_TOKEN": "letmein-token",
                               "SERVER_ADDRESS": "", "API_URL": "", "API_TOKEN": "", "DISCORD_URL": "", "DISCORD_SERVER_ID": ""})
        self.client = self.app.test_client()
        status.clear_cache()
        gameapi.clear_cache()

    def tearDown(self):
        os.environ.pop("AERBORNE_DATA_DIR", None)
        shutil.rmtree(self.data, ignore_errors=True)

    def db(self):
        return connect(self.app.config["DATABASE"])

    def test_expedition_board_shows_live_expeditions_tiers_and_seals(self):
        with mock.patch.object(gameapi, "_request", side_effect=fake_api):
            html = self.client.get("/expeditions").get_data(as_text=True)
        for text in ("The Hollow Barrow", "Cinder Keep", "Sealed until The Ember Gate", "The Nether", "7m 12s", "Ash, Bryn",
                     "Fortified", "/expedition queue hollow_barrow", "1 of 6 instances"):
            self.assertIn(text, html)
        self.assertNotIn("<script>", html.split("</header>", 1)[1].split("<footer", 1)[0])

    def test_expedition_guide_page_shows_route_boss_bestiary_and_drops(self):
        with mock.patch.object(gameapi, "_request", side_effect=fake_api):
            board = self.client.get("/expeditions").get_data(as_text=True)
            self.assertIn("★★☆☆☆", board)
            self.assertIn('href="/expeditions/hollow_barrow"', board)
            html = self.client.get("/expeditions/hollow_barrow").get_data(as_text=True)
            for text in ("Moderate", "A short crypt crawl.", "Bring a shield.", "Take out the archers early.", "Survive 45s",
                         "A ring of light marks the shockwave.", "The Hollow Huntsman", "Hollowfang Blade (relic)", "35%",
                         "75–125", "120 Marks", "4 × Iron ingot", "/expedition guide hollow_barrow"):
                self.assertIn(text, html)
            self.assertEqual(self.client.get("/expeditions/nowhere").status_code, 404)
            self.assertIn("Cinder Keep", self.client.get("/expeditions/cinder_keep").get_data(as_text=True), "Pages work without guide data")
        gameapi.clear_cache()
        with mock.patch.object(gameapi, "_request", return_value=(None, "unavailable")):
            self.assertIn("This guide is offline", self.client.get("/expeditions/hollow_barrow").get_data(as_text=True))

    def test_campaign_page_shows_goals_milestones_buffs_and_safe_names(self):
        with mock.patch.object(gameapi, "_request", side_effect=fake_api):
            html = self.client.get("/campaign").get_data(as_text=True)
        for text in ("The Ember Accord", "Stoke the Forges", "12,500", "50,000", "First Spark", "Reached",
                     "Opens the Cinder Keep expedition", "Haste until", "Top contributors", "/campaign claim"):
            self.assertIn(text, html)
        self.assertNotIn("bad name!", html)

    def test_pages_say_when_the_game_is_unreachable(self):
        with mock.patch.object(gameapi, "_request", return_value=(None, "unavailable")):
            self.assertIn("expedition board is offline", self.client.get("/expeditions").get_data(as_text=True))
            gameapi.clear_cache()
            self.assertIn("Campaign progress is offline", self.client.get("/campaign").get_data(as_text=True))
        with mock.patch.object(gameapi, "_request", return_value=({"ready": False}, None)):
            gameapi.clear_cache()
            self.assertEqual(gameapi.campaign(), (None, "unavailable"))

    def test_navigation_and_sitemap_include_the_new_pages(self):
        html = self.client.get("/explore").get_data(as_text=True)
        self.assertIn('href="/expeditions"', html)
        self.assertIn('href="/campaign"', html)
        sitemap = self.client.get("/sitemap.xml").get_data(as_text=True)
        self.assertIn("/expeditions</loc>", sitemap)
        self.assertIn("/guide/season-campaign</loc>", sitemap)

    def test_new_sites_get_the_expedition_guides_and_region(self):
        for path, text in (("/guide/expeditions", "Lives are shared"), ("/guide/season-campaign", "/campaign claim"),
                           ("/guide/parties", "ready check"), ("/guide/world-overview", "Vanilla portals stay sealed"),
                           ("/explore/expeditions", "Your party, your instance")):
            self.assertIn(text, self.client.get(path).get_data(as_text=True), path)

    def test_revision_20_refreshes_untouched_copy_and_keeps_staff_edits(self):
        old = {"parties": "## Travel together\nCheck `/expedition` for routes currently open.\n",
               "world-overview": "A staff-written travel page.\n"}
        conn = self.db()
        for slug, body in old.items():
            conn.execute("UPDATE guides SET body = ? WHERE slug = ?", (body, slug))
        conn.execute("UPDATE guides SET updated_by = 'j0sh' WHERE slug = 'world-overview'")
        conn.execute("DELETE FROM guides WHERE slug IN ('expeditions', 'season-campaign')")
        conn.execute("UPDATE settings SET value = '19' WHERE key = 'seed_rev'")
        conn.commit()
        patched = dict(seed_updates.PRE_EXPEDITION_BODIES,
                       parties=hashlib.sha256(old["parties"].encode("utf-8")).hexdigest(),
                       **{"world-overview": hashlib.sha256(old["world-overview"].encode("utf-8")).hexdigest()})
        with mock.patch.object(seed_updates, "PRE_EXPEDITION_BODIES", patched):
            self.assertTrue(seed_updates.apply(conn))
        rows = {r["slug"]: r for r in conn.execute("SELECT slug, body FROM guides")}
        reviewed = {g["slug"]: g for g in reference_content.GUIDES}
        self.assertEqual(rows["parties"]["body"], reviewed["parties"]["body"])
        self.assertEqual(rows["world-overview"]["body"], old["world-overview"], "Staff edits are kept")
        self.assertIn("expeditions", rows)
        self.assertIn("season-campaign", rows)
        self.assertEqual(conn.execute("SELECT value FROM settings WHERE key = 'seed_rev'").fetchone()["value"], str(seed_updates.LATEST))
        conn.close()


    def test_revision_22_refreshes_the_revision_20_parties_guide_but_not_an_edited_one(self):
        conn = self.db()
        old = "## Start a party\nOld text.\n"
        conn.execute("UPDATE guides SET body = ?, summary = ? WHERE slug = 'parties'", (old, seed_updates.REV20_PARTIES[1]))
        conn.execute("UPDATE settings SET value = '21' WHERE key = 'seed_rev'")
        conn.commit()
        shipped = (hashlib.sha256(old.encode("utf-8")).hexdigest(), seed_updates.REV20_PARTIES[1])
        with mock.patch.object(seed_updates, "REV20_PARTIES", shipped):
            self.assertTrue(seed_updates.apply(conn))
        row = conn.execute("SELECT body, summary FROM guides WHERE slug = 'parties'").fetchone()
        reviewed = {g["slug"]: g for g in reference_content.GUIDES}["parties"]
        self.assertEqual(row["body"], reviewed["body"])
        self.assertEqual(row["summary"], reviewed["summary"])
        self.assertIn("/party list", row["body"])

        conn.execute("UPDATE guides SET body = ?, updated_by = 'j0sh' WHERE slug = 'parties'", (old,))
        conn.execute("UPDATE settings SET value = '21' WHERE key = 'seed_rev'")
        conn.commit()
        with mock.patch.object(seed_updates, "REV20_PARTIES", shipped):
            seed_updates.apply(conn)
        self.assertEqual(conn.execute("SELECT body FROM guides WHERE slug = 'parties'").fetchone()["body"], old,
                         "Staff edits are kept")
        conn.close()

    def test_new_sites_get_the_reworked_parties_guide(self):
        body = self.client.get("/guide/parties").get_data(as_text=True)
        for text in ("/party list", "/party open", "offline for 15 minutes", "ready check"):
            self.assertIn(text, body)


if __name__ == "__main__":
    unittest.main()
