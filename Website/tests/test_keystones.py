import copy
import os
import shutil
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from aerborne import create_app, gameapi, status  # noqa: E402
from tests.test_expeditions_campaign import EXPEDITIONS  # noqa: E402

KEYED = copy.deepcopy(EXPEDITIONS)
KEYED["keystones"] = {"week": 41, "deathPenaltySeconds": 10, "levelScale": 0.08, "affixes": [
    {"fromLevel": 4, "modifiers": [{"id": "tyrannical", "title": "Tyrannical", "description": "Tougher bosses."}]},
    {"fromLevel": 7, "modifiers": [{"id": "raging", "title": "Raging", "description": "Low enemies hit harder."}]},
    {"fromLevel": 10, "modifiers": []}]}
KEYED["expeditions"][0]["keystone"] = {"timerSeconds": 900, "best": {"level": 12, "seconds": 744, "party": ["Ash", "<b>x</b>"]}}


class KeystonePageTest(unittest.TestCase):
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

    def test_expedition_page_shows_the_keystone_timer_weekly_affixes_and_best_key(self):
        with mock.patch.object(gameapi, "_request", return_value=(KEYED, None)):
            html = self.client.get("/expeditions/hollow_barrow").get_data(as_text=True)
            for text in ("Mythic keystones", "15m", "From +4", "Tyrannical", "From +7", "Raging", "Highest timed key", "+12 in 12m 24s",
                         "/expedition key start"):
                self.assertIn(text, html)
            self.assertNotIn("From +10", html, "Empty affix rows are left out")
            self.assertNotIn("<b>x</b>", html)
            self.assertNotIn("Mythic keystones", self.client.get("/expeditions/cinder_keep").get_data(as_text=True))

    def test_the_page_works_without_keystone_data(self):
        with mock.patch.object(gameapi, "_request", return_value=(EXPEDITIONS, None)):
            html = self.client.get("/expeditions/hollow_barrow").get_data(as_text=True)
        self.assertIn("The Hollow Barrow", html)
        self.assertNotIn("Mythic keystones", html)


if __name__ == "__main__":
    unittest.main()
