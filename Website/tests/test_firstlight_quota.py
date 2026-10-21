from concurrent.futures import ThreadPoolExecutor
from threading import Barrier

from test_app import SiteTest
from aerborne import badges, seed_updates
from aerborne.db import now_str


class FirstlightQuotaTest(SiteTest):
    def setUp(self):
        super().setUp()
        conn = self.db()
        self.members = []
        for number in range(8):
            self.members.append(conn.execute("INSERT INTO members (username,password_hash,created_at) VALUES (?,'!',?)",
                                            ('Guest%d' % number, now_str())).lastrowid)
        self.badge = conn.execute("SELECT id FROM badges WHERE slug='firstlight-sigil'").fetchone()[0]
        conn.commit()
        conn.close()
        self.add_user('creator-one', 'admin')
        self.add_user('creator-two', 'admin')
        self.login('creator-one')
        self.token = self.csrf('/admin/forum/badges')

    def grant(self, member, action='award'):
        return self.client.post('/admin/forum/members/%d/badges' % member,
                                data={'_csrf': self.token, 'badge_id': self.badge, 'action': action})

    def test_five_lifetime_awards_per_admin_duplicates_and_revocation(self):
        for member in self.members[:5]:
            self.assertEqual(self.grant(member).status_code, 302)
        self.grant(self.members[0])
        self.grant(self.members[5])
        conn = self.db()
        first = conn.execute("SELECT id FROM users WHERE username='creator-one'").fetchone()[0]
        self.assertEqual(badges.firstlight_quota(conn, first)['used'], 5)
        self.assertFalse(conn.execute('SELECT 1 FROM member_badges WHERE member_id=? AND badge_id=?', (self.members[5], self.badge)).fetchone())
        self.grant(self.members[0], 'revoke')
        self.grant(self.members[5])
        self.assertEqual(conn.execute('SELECT COUNT(*) FROM member_badges WHERE badge_id=?', (self.badge,)).fetchone()[0], 4)
        self.login('creator-two')
        self.token = self.csrf('/admin/forum/badges')
        self.grant(self.members[5])
        second = conn.execute("SELECT id FROM users WHERE username='creator-two'").fetchone()[0]
        self.assertEqual(badges.firstlight_quota(conn, second)['used'], 1)
        self.assertEqual(badges.firstlight_quota(conn, first)['remaining'], 0)
        conn.close()

    def test_concurrent_requests_cannot_claim_a_sixth_invitation(self):
        for member in self.members[:4]:
            self.grant(member)
        with self.client.session_transaction() as session:
            credentials = dict(session)
        barrier = Barrier(2)
        def submit(member):
            with self.app.test_client() as client:
                with client.session_transaction() as session:
                    session.update(credentials)
                barrier.wait(timeout=5)
                return client.post('/admin/forum/members/%d/badges' % member,
                    data={'_csrf': self.token, 'badge_id': self.badge, 'action': 'award'}).status_code
        with ThreadPoolExecutor(max_workers=2) as pool:
            self.assertEqual(list(pool.map(submit, self.members[4:6])), [302, 302])
        conn = self.db()
        self.assertEqual(conn.execute('SELECT COUNT(*) FROM firstlight_awards').fetchone()[0], 5)
        self.assertEqual(conn.execute('SELECT COUNT(*) FROM member_badges WHERE badge_id=?', (self.badge,)).fetchone()[0], 5)
        conn.close()

    def test_old_awards_are_counted_and_delete_recreate_does_not_reset(self):
        for member in self.members[:5]:
            self.grant(member)
        self.grant(self.members[0], 'revoke')
        conn = self.db()
        # Simulate the upgrade from historical awards plus the audit trail.
        conn.execute('DELETE FROM firstlight_awards')
        conn.execute("UPDATE settings SET value='16' WHERE key='seed_rev'")
        conn.commit()
        self.assertTrue(seed_updates.apply(conn))
        self.assertEqual(conn.execute('SELECT COUNT(*) FROM firstlight_awards').fetchone()[0], 5)
        self.client.post('/admin/forum/badges', data={'_csrf': self.token, 'id': self.badge, 'action': 'delete'})
        self.client.post('/admin/forum/badges', data={'_csrf': self.token, 'name': 'Firstlight Sigil', 'slug': 'firstlight-sigil',
            'description': 'Creator award', 'icon': 'badge-firstlight-sigil', 'tone': 'gold'})
        self.badge = conn.execute("SELECT id FROM badges WHERE slug='firstlight-sigil'").fetchone()[0]
        self.grant(self.members[5])
        self.assertEqual(conn.execute('SELECT COUNT(*) FROM member_badges WHERE badge_id=?', (self.badge,)).fetchone()[0], 0)
        self.assertEqual(conn.execute('SELECT COUNT(*) FROM firstlight_awards').fetchone()[0], 5)
        conn.close()
