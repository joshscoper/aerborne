"""Presentation regressions: accurate units, missing data, capped progress and probability rounding."""
import copy
import os
import shutil
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from aerborne import create_app, gameapi
from test_expeditions_campaign import EXPEDITIONS, CAMPAIGN


class ProgressionPresentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.folder = tempfile.mkdtemp()
        with mock.patch.dict(os.environ, {"AERBORNE_DATA_DIR": cls.folder}):
            cls.app = create_app({"TESTING": True, "SESSION_COOKIE_SECURE": False,
                                  "SERVER_ADDRESS": "", "API_URL": "", "API_TOKEN": "",
                                  "DISCORD_URL": "", "DISCORD_SERVER_ID": ""})

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.folder)

    def setUp(self):
        self.client = self.app.test_client()
        gameapi.clear_cache()

    def test_probability_near_one_is_not_advertised_as_guaranteed(self):
        payload = copy.deepcopy(EXPEDITIONS)
        drops = payload["expeditions"][0]["bestiary"][0]["drops"]
        drops[0]["chance"] = 0.999999
        drops[1]["chance"] = 0.000000001
        with mock.patch.object(gameapi, "expeditions", return_value=(payload, None)):
            html = self.client.get('/expeditions/hollow_barrow').get_data(as_text=True)
        self.assertIn('&gt;99.99%', html)
        self.assertIn('&lt;0.01%', html)
        self.assertNotIn('>100%</td>', html)
        self.assertIn('75–125', html)
        self.assertNotIn('<dt>Loot-profile rolls</dt>', html, 'No loot profile means no profile rolls to promise')

    def test_partial_expedition_does_not_invent_route_or_rewards(self):
        payload = copy.deepcopy(EXPEDITIONS)
        with mock.patch.object(gameapi, "expeditions", return_value=(payload, None)):
            html = self.client.get('/expeditions/cinder_keep').get_data(as_text=True)
        self.assertIn('Sealed until The Ember Gate', html)
        self.assertIn('room-by-room route is not included', html)
        self.assertIn('Detailed rewards are not included', html)
        self.assertNotIn('0 Marks', html)

    def test_campaign_keeps_actual_totals_but_bounds_progress_and_shows_goal_cap(self):
        payload = copy.deepcopy(CAMPAIGN)
        payload['goals'][0].update(points=60000, dailyCap=500)
        with mock.patch.object(gameapi, 'campaign', return_value=(payload, None)):
            html = self.client.get('/campaign').get_data(as_text=True)
        self.assertIn('60,000', html)
        self.assertIn('value="50000" max="50000"', html)
        self.assertIn('500 points', html)
        self.assertIn('1 Dec 2026, 00:00 UTC', html)

    def test_each_leaderboard_explains_its_measure_without_changing_server_ranks(self):
        payload = {'ready': True, 'updatedAt': '2026-10-21T00:00:00Z', 'boards': {}}
        for key, _, _ in gameapi.BOARDS:
            payload['boards'][key] = {'players': 200, 'top': [{'rank': 7, 'name': 'SkyPlayer', 'score': 0}]}
        expected = {'adventurer': 'total Adventurer XP', 'skills': 'sum of skill levels',
                    'island': 'owned island', 'achievements': 'completed achievements',
                    'cooking': 'Cooking XP', 'marks': 'current Marks balance'}
        expected.update((skill, skill.capitalize() + ' XP') for skill in gameapi.SKILLS)
        with mock.patch.object(gameapi, 'leaderboards', return_value=(payload, None)):
            for key in expected:
                html = self.client.get('/leaderboards?board=' + key).get_data(as_text=True)
                self.assertIn(expected[key], html)
                self.assertIn('#7', html)
                self.assertIn('Top 1 shown', html)
                self.assertIn('200 players ranked', html)
                self.assertIn('>0</strong>', html)
                self.assertIn('/minecraft/head/SkyPlayer.png', html)
                self.assertNotIn('ranking-monogram', html)
        with mock.patch.object(gameapi, 'leaderboards', return_value=(None, 'unavailable')):
            html = self.client.get('/leaderboards').get_data(as_text=True)
        self.assertIn('Leaderboards are offline', html)
        self.assertNotIn('<table class="ranking-table">', html)

    def test_new_category_waits_for_server_data_without_inventing_zero_scores(self):
        payload = {'ready': True, 'boards': {'adventurer': {'players': 1, 'top': [{'rank': 1, 'name': 'SkyPlayer', 'score': 10}]}}}
        with mock.patch.object(gameapi, 'leaderboards', return_value=(payload, None)):
            html = self.client.get('/leaderboards?board=mining').get_data(as_text=True)
        self.assertIn('This ranking is not available yet', html)
        self.assertIn('Mining XP', html)
        self.assertNotIn('No one is ranked', html)
        self.assertNotIn('<table class="ranking-table">', html)
        self.assertIn('16 rankings', html)

    def test_profile_has_grouped_skill_standings_with_correct_xp(self):
        payload = {'name': 'SkyPlayer', 'adventurer': {'rank': 'Scout', 'level': 10}, 'skills': {'mining': 10},
                   'boards': {'adventurer': {'rank': 3, 'score': 10321}, 'mining': {'rank': 2, 'score': 10321}}}
        with mock.patch.object(gameapi, 'player', return_value=(payload, None)):
            html = self.client.get('/players/SkyPlayer').get_data(as_text=True)
        self.assertIn('Overall progress', html)
        self.assertIn('Individual skills', html)
        self.assertIn('Mining XP', html)
        self.assertIn('10,321', html)
        self.assertIn('#2', html)
        self.assertNotIn('Fishing XP', html, 'Do not imply an unpublished board is ranked')
        self.assertEqual(set(key for group in gameapi.board_groups() for key, _, _ in group['boards']), set(key for key, _, _ in gameapi.BOARDS))


if __name__ == '__main__':
    unittest.main()
