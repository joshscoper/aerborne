import copy
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
from tests.test_expeditions_campaign import EXPEDITIONS  # noqa: E402


class ExpeditionLootTest(unittest.TestCase):
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

    def test_the_expedition_page_lists_materials_the_set_and_signatures(self):
        data = copy.deepcopy(EXPEDITIONS)
        data["expeditions"][0]["rewards"].update(materials=["Barrow Grave Dust", "Barrow Candlewax", "Huntsman's Antler"],
                                                 gear=["Barrowguard Helm", "Sexton's Edge"], signatures=["Huntsman's Longbow"])
        with mock.patch.object(gameapi, "_request", return_value=(data, None)):
            html = self.client.get("/expeditions/hollow_barrow").get_data(as_text=True)
        for text in ("Expedition loot", "Barrow Grave Dust", "Barrowguard Helm", "Huntsman&#39;s Longbow", "/expedition forge", "D to S+"):
            self.assertIn(text, html)
        with mock.patch.object(gameapi, "_request", return_value=(EXPEDITIONS, None)):
            gameapi.clear_cache()
            self.assertNotIn("Expedition loot", self.client.get("/expeditions/hollow_barrow").get_data(as_text=True))

    def test_the_expedition_page_tells_the_story(self):
        data = copy.deepcopy(EXPEDITIONS)
        data["expeditions"][0]["guide"]["lore"] = "The Huntsman held the hill road while the last carts climbed into the sky."
        with mock.patch.object(gameapi, "_request", return_value=(data, None)):
            html = self.client.get("/expeditions/hollow_barrow").get_data(as_text=True)
        self.assertIn("The story", html)
        self.assertIn("held the hill road", html)

    def test_new_sites_explain_loot_reward_chests_the_forge_and_skill_lamps(self):
        guide = self.client.get("/guide/expeditions").get_data(as_text=True)
        for text in ("Reward chests", "bonus chest", "/expedition forge", "supply crate", "Hidden caches"):
            self.assertIn(text, guide)
        skills = self.client.get("/guide/skills-and-talents").get_data(as_text=True)
        self.assertIn("Experience bottles", skills)
        self.assertIn("Bottled Legend", skills)

    def test_revision_25_refreshes_untouched_guides_and_keeps_staff_edits(self):
        conn = connect(self.app.config["DATABASE"])
        old = {"expeditions": "## Rewards\nOld.\n", "skills-and-talents": "## Train by doing\nOld.\n"}
        for slug, body in old.items():
            conn.execute("UPDATE guides SET body = ? WHERE slug = ?", (body, slug))
        conn.execute("UPDATE settings SET value = '24' WHERE key = 'seed_rev'")
        conn.commit()
        shipped = {slug: hashlib.sha256(body.encode("utf-8")).hexdigest() for slug, body in old.items()}
        with mock.patch.object(seed_updates, "REV24_GUIDES", shipped):
            self.assertTrue(seed_updates.apply(conn))
        self.assertIn("The Expedition Forge", conn.execute("SELECT body FROM guides WHERE slug = 'expeditions'").fetchone()["body"])
        self.assertIn("Experience bottles", conn.execute("SELECT body FROM guides WHERE slug = 'skills-and-talents'").fetchone()["body"])

        conn.execute("UPDATE guides SET body = ?, updated_by = 'j0sh' WHERE slug = 'skills-and-talents'", (old["skills-and-talents"],))
        conn.execute("UPDATE settings SET value = '24' WHERE key = 'seed_rev'")
        conn.commit()
        with mock.patch.object(seed_updates, "REV24_GUIDES", shipped):
            seed_updates.apply(conn)
        self.assertEqual(conn.execute("SELECT body FROM guides WHERE slug = 'skills-and-talents'").fetchone()["body"], old["skills-and-talents"])
        conn.close()


if __name__ == "__main__":
    unittest.main()
