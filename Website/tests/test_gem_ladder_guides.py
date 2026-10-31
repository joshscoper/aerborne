import hashlib
import os
import shutil
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from aerborne import create_app, gameapi, reference_content, seed, seed_updates, status  # noqa: E402
from aerborne.db import connect  # noqa: E402

SLUGS = ("first-gear", "gear-and-sockets", "gem-merging", "aspects-and-research")


class GemLadderGuideTests(unittest.TestCase):
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

    def test_new_sites_describe_the_gemstone_ladder(self):
        merging = self.client.get("/guide/gem-merging").get_data(as_text=True)
        for text in ("Rough, Flawed, Fine, Flawless and Perfect", "Perfect is the highest grade", "Luminous Geode", "24 Arcane Dust"):
            self.assertIn(text, merging)
        sockets = self.client.get("/guide/gear-and-sockets").get_data(as_text=True)
        self.assertIn("Socket types", sockets)
        self.assertIn("Universal", sockets)
        conn = self.db()
        for row in conn.execute("SELECT slug, body FROM guides WHERE slug IN (%s)" % ",".join("?" * len(SLUGS)), SLUGS):
            self.assertNotIn("tier IV", row["body"], row["slug"])
            self.assertNotIn("Tier IV", row["body"], row["slug"])
        conn.close()

    def test_revision_26_refreshes_untouched_guides_from_the_old_reviewed_or_seed_text(self):
        reviewed = {g["slug"]: g for g in reference_content.GUIDES}
        seeded = {g["slug"]: g for g in seed.GUIDES}
        conn = self.db()
        # One guide still has the starter seed's text and one a staff edit.
        conn.execute("UPDATE guides SET body = ? WHERE slug = 'first-gear'", (seeded["first-gear"]["body"],))
        old_merging = "## Our merging rules\nA staff-written page.\n"
        conn.execute("UPDATE guides SET body = ?, updated_by = 'j0sh' WHERE slug = 'gem-merging'", (old_merging,))
        conn.execute("UPDATE settings SET value = '25' WHERE key = 'seed_rev'")
        conn.commit()
        fingerprint = seed_updates.REV26_GUIDES["first-gear"][0]
        self.assertEqual(hashlib.sha256(seeded["first-gear"]["body"].encode("utf-8")).hexdigest(), fingerprint)
        self.assertTrue(seed_updates.apply(conn))
        rows = {r["slug"]: r["body"] for r in conn.execute("SELECT slug, body FROM guides")}
        self.assertEqual(rows["first-gear"], reviewed["first-gear"]["body"])
        self.assertEqual(rows["gem-merging"], old_merging, "Staff edits are kept")
        self.assertEqual(conn.execute("SELECT value FROM settings WHERE key = 'seed_rev'").fetchone()["value"], str(seed_updates.LATEST))
        conn.close()


if __name__ == "__main__":
    unittest.main()
