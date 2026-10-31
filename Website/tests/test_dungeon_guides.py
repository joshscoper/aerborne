import os
import shutil
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from aerborne import create_app, gameapi, reference_content, seed_updates, status  # noqa: E402
from aerborne.db import connect  # noqa: E402


class DungeonGuideTests(unittest.TestCase):
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

    def test_the_guide_explains_packs_threat_and_running_back(self):
        html = self.client.get("/guide/expeditions").get_data(as_text=True)
        for text in ("Normal and Heroic forgive", "Packs, threat and interrupts", "Taunt with a shield", "Stop casts"):
            self.assertIn(text, html)
        self.assertNotIn("10 lives", html)

    def test_revision_28_names_the_bottles_and_keeps_staff_edits(self):
        conn = connect(self.app.config["DATABASE"])
        conn.execute("UPDATE guides SET body = 'Our own words.', updated_by = 'j0sh' WHERE slug = 'skills-and-talents'")
        conn.execute("UPDATE settings SET value = '27' WHERE key = 'seed_rev'")
        conn.commit()
        self.assertTrue(seed_updates.apply(conn))
        rows = {r["slug"]: r["body"] for r in conn.execute("SELECT slug, body FROM guides")}
        self.assertEqual(rows["skills-and-talents"], "Our own words.")
        self.assertIn("Bottled Lessons", rows["expeditions"])
        conn.close()

    def test_revision_27_keeps_staff_edits(self):
        conn = connect(self.app.config["DATABASE"])
        edited = "## Our own expedition notes\n"
        conn.execute("UPDATE guides SET body = ?, updated_by = 'j0sh' WHERE slug = 'expeditions'", (edited,))
        conn.execute("UPDATE settings SET value = '26' WHERE key = 'seed_rev'")
        conn.commit()
        self.assertTrue(seed_updates.apply(conn))
        rows = {row["slug"]: row["body"] for row in conn.execute("SELECT slug, body FROM guides")}
        self.assertEqual(rows["expeditions"], edited)
        conn.close()

    def test_tiers_that_run_back_say_so(self):
        reviewed = {guide["slug"]: guide for guide in reference_content.GUIDES}
        self.assertIn("Fall and you run back", reviewed["expeditions"]["body"])


if __name__ == "__main__":
    unittest.main()
