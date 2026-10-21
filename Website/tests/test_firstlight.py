from test_app import SocialHelpers
from aerborne import badges, seed_updates


class FirstlightTest(SocialHelpers):
    def test_badge_details_art_and_no_automatic_or_store_award(self):
        self.member('Ava')
        conn = self.db()
        badge = conn.execute("SELECT * FROM badges WHERE slug='firstlight-sigil'").fetchone()
        for key, value in badges.FIRSTLIGHT.items():
            self.assertEqual(badge[key], value)
        member = conn.execute("SELECT * FROM members WHERE username='Ava'").fetchone()
        self.assertNotIn('Firstlight Sigil', [b['name'] for b in badges.for_member(conn, member)])
        self.assertFalse(conn.execute("SELECT 1 FROM products WHERE slug='firstlight-sigil'").fetchone())
        self.assertEqual(conn.execute('SELECT COUNT(*) FROM member_badges WHERE badge_id=?', (badge['id'],)).fetchone()[0], 0)
        conn.close()
        for size in (96, 192):
            response = self.client.get('/static/img/badges/firstlight-sigil-%d.webp' % size)
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.mimetype, 'image/webp')

    def test_only_admins_can_award_and_revoke_creator_badges(self):
        self.member('Ava')
        conn = self.db()
        member_id = conn.execute("SELECT id FROM members WHERE username='Ava'").fetchone()[0]
        badge_id = conn.execute("SELECT id FROM badges WHERE slug='firstlight-sigil'").fetchone()[0]
        conn.close()
        self.add_user('mod', 'moderator')
        self.add_user('boss', 'admin')
        path = '/admin/forum/members/%d/manage' % member_id
        award_path = '/admin/forum/members/%d/badges' % member_id
        self.login('mod')
        token = self.csrf(path)
        page = self.client.get(path).get_data(as_text=True)
        self.assertNotIn('Firstlight Sigil', page)
        self.assertEqual(self.client.post(award_path, data={'_csrf': token, 'badge_id': badge_id, 'action': 'award'}).status_code, 403)
        self.login('boss')
        token = self.csrf(path)
        self.assertEqual(self.client.post(award_path, data={'_csrf': token, 'badge_id': badge_id, 'action': 'award', 'note': 'Personal invitation'}).status_code, 302)
        page = self.client.get('/members/Ava').get_data(as_text=True)
        for text in ('Firstlight Sigil', 'Mythic · Invitation Only', 'Never purchasable or obtainable through ordinary progression.', 'Awarded by Aerborne’s creators', 'Personal invitation', 'firstlight-sigil-192.webp'):
            self.assertIn(text, page)
        self.login('mod')
        token = self.csrf(path)
        self.assertEqual(self.client.post(award_path, data={'_csrf': token, 'badge_id': badge_id, 'action': 'revoke'}).status_code, 403)
        self.assertIn(b'Firstlight Sigil', self.client.get('/members/Ava').data)
        self.login('boss')
        token = self.csrf(path)
        self.assertEqual(self.client.post(award_path, data={'_csrf': token, 'badge_id': badge_id, 'action': 'revoke'}).status_code, 302)
        self.assertNotIn(b'badges/firstlight-sigil-', self.client.get('/members/Ava').data)
        conn = self.db()
        self.assertFalse(conn.execute('SELECT 1 FROM member_badges WHERE badge_id=? AND member_id=?', (badge_id, member_id)).fetchone())
        conn.close()

    def test_upgrade_preserves_existing_details_and_awards(self):
        self.member('Ava')
        conn = self.db()
        badge_id = conn.execute("SELECT id FROM badges WHERE slug='firstlight-sigil'").fetchone()[0]
        member_id = conn.execute("SELECT id FROM members WHERE username='Ava'").fetchone()[0]
        conn.execute("UPDATE badges SET description='Custom creator description' WHERE id=?", (badge_id,))
        conn.execute("INSERT INTO member_badges VALUES (?,?,'2026-01-01 12:00:00','Josh','Original note')", (member_id, badge_id))
        conn.execute("UPDATE settings SET value='15' WHERE key='seed_rev'")
        conn.commit()
        self.assertTrue(seed_updates.apply(conn))
        self.assertEqual(conn.execute('SELECT description FROM badges WHERE id=?', (badge_id,)).fetchone()[0], 'Custom creator description')
        award = conn.execute('SELECT * FROM member_badges WHERE badge_id=?', (badge_id,)).fetchone()
        self.assertEqual(award['note'], 'Original note')
        self.assertEqual(award['awarded_by'], 'Josh')
        self.assertFalse(seed_updates.apply(conn))
        conn.close()
