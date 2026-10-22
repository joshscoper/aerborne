import hashlib
import os
import shutil
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from aerborne import create_app, gameapi, seed_updates, status  # noqa: E402
from aerborne.db import connect  # noqa: E402


class RaidContentTest(unittest.TestCase):
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

    def test_new_sites_explain_raids_keystones_and_eight_player_parties(self):
        expeditions = self.client.get("/guide/expeditions").get_data(as_text=True)
        for text in ("Mythic keystones", "/expedition key start", "Cinder Keep", "up to eight players", "Cinder Shelf", "every Monday"):
            self.assertIn(text, expeditions)
        self.assertIn("up to <strong>eight</strong> players", self.client.get("/guide/parties").get_data(as_text=True))

    def test_revision_24_refreshes_untouched_guides_and_region_but_keeps_staff_edits(self):
        conn = connect(self.app.config["DATABASE"])
        old_expeditions, old_parties = "## What an expedition is\nFive players.\n", "## Run expeditions together\nFive.\n"
        conn.execute("UPDATE guides SET body = ?, summary = ? WHERE slug = 'expeditions'",
                     (old_expeditions, seed_updates.REV23_GUIDES["expeditions"][1]))
        conn.execute("UPDATE guides SET body = ? WHERE slug = 'parties'", (old_parties,))
        conn.execute("UPDATE regions SET points_of_interest = ? WHERE slug = 'expeditions'", (seed_updates.REV23_EXPEDITIONS_POI,))
        conn.execute("UPDATE settings SET value = '23' WHERE key = 'seed_rev'")
        conn.commit()
        shipped = {"expeditions": (hashlib.sha256(old_expeditions.encode("utf-8")).hexdigest(), seed_updates.REV23_GUIDES["expeditions"][1]),
                   "parties": (hashlib.sha256(old_parties.encode("utf-8")).hexdigest(), None)}
        with mock.patch.object(seed_updates, "REV23_GUIDES", shipped):
            self.assertTrue(seed_updates.apply(conn))
        row = conn.execute("SELECT body, summary FROM guides WHERE slug = 'expeditions'").fetchone()
        self.assertIn("## Raids", row["body"])
        self.assertIn("Raids take up to eight", row["summary"])
        self.assertIn("**eight**", conn.execute("SELECT body FROM guides WHERE slug = 'parties'").fetchone()["body"])
        poi = conn.execute("SELECT points_of_interest FROM regions WHERE slug = 'expeditions'").fetchone()["points_of_interest"]
        self.assertIn("a raid for up to eight", poi)

        conn.execute("UPDATE guides SET body = ?, updated_by = 'j0sh' WHERE slug = 'parties'", (old_parties,))
        conn.execute("UPDATE settings SET value = '23' WHERE key = 'seed_rev'")
        conn.commit()
        with mock.patch.object(seed_updates, "REV23_GUIDES", shipped):
            seed_updates.apply(conn)
        self.assertEqual(conn.execute("SELECT body FROM guides WHERE slug = 'parties'").fetchone()["body"], old_parties)
        conn.close()

    def test_the_web_api_raid_flag_reaches_the_expedition_page(self):
        from tests.test_keystones import KEYED
        import copy
        data = copy.deepcopy(KEYED)
        data["expeditions"][1].update(raid=True, maxParty=8)
        with mock.patch.object(gameapi, "_request", return_value=(data, None)):
            html = self.client.get("/expeditions/cinder_keep").get_data(as_text=True)
        self.assertIn("Raid", html)
        self.assertIn("once per difficulty each week", html)


if __name__ == "__main__":
    unittest.main()
