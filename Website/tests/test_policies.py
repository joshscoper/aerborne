import datetime
import json
import re
import zipfile
from unittest import mock

from test_app import SiteTest
from aerborne import community, seed_updates
from aerborne.db import now_str
from aerborne.members import erase, export_zip


class PolicyRegistrationTest(SiteTest):
    def age_check(self, birth='2000-01-01'):
        token = self.csrf('/account/register')
        return self.client.post('/account/register', data={'_csrf': token, 'birth_date': birth})

    def form(self, **changes):
        page = self.client.get('/account/register').get_data(as_text=True)
        data = {'_csrf': re.search(r'name="_csrf" value="([^"]+)"', page).group(1),
                'terms_version': re.search(r'name="terms_version" value="([^"]+)"', page).group(1),
                'username': 'PolicyPlayer', 'password': 'a-safe-password', 'confirm': 'a-safe-password',
                'age_confirm': '1', 'accept_terms': '1'}
        data.update(changes)
        return data

    def test_age_check_before_account_details_and_no_birth_date_storage(self):
        page = self.client.get('/account/register')
        self.assertIn(b'name="birth_date"', page.data)
        self.assertNotIn(b'name="username"', page.data)
        self.assertIn('no-store', page.headers['Cache-Control'])
        self.assertEqual(self.client.post('/account/register', data={'birth_date': '2000-01-01'}).status_code, 400)
        self.assertEqual(self.age_check('not-a-date').status_code, 400)
        self.assertEqual(self.age_check().status_code, 303)
        page = self.client.get('/account/register').get_data(as_text=True)
        self.assertIn('name="username"', page)
        self.assertNotIn('2000-01-01', page)
        with self.client.session_transaction() as session:
            self.assertEqual(set(session['registration_age']), {'eligible', 'checked_at'})

    def test_under_13_cannot_back_button_or_post_registration(self):
        fixed = datetime.datetime(2026, 10, 20, 12)
        with mock.patch.object(community, 'utcnow', return_value=fixed), \
                mock.patch.object(community, 'now_str', return_value='2026-10-20 12:00:00'):
            token = self.csrf('/account/register')
            self.client.post('/account/register', data={'_csrf': token, 'birth_date': '2013-10-21'})
            self.assertEqual(self.client.get('/account/register').status_code, 403)
            bypass = self.client.post('/account/register', data={'_csrf': token, 'birth_date': '2000-01-01',
                'username': 'TooYoung', 'password': 'a-safe-password', 'confirm': 'a-safe-password',
                'age_confirm': '1', 'accept_terms': '1'})
            self.assertEqual(bypass.status_code, 403)
        conn = self.db()
        self.assertEqual(conn.execute('SELECT COUNT(*) FROM members').fetchone()[0], 0)
        self.assertNotIn('birth_date', {r[1] for r in conn.execute('PRAGMA table_info(members)')})
        conn.close()
        fresh = self.app.test_client()
        self.client = fresh
        with mock.patch.object(community, 'utcnow', return_value=fixed), \
                mock.patch.object(community, 'now_str', return_value='2026-10-20 12:00:00'):
            self.assertEqual(self.age_check('2013-10-20').status_code, 303)
            self.assertIn(b'name="username"', self.client.get('/account/register').data)

    def test_required_confirmations_terms_snapshot_and_stale_terms(self):
        self.age_check()
        for changes in ({'age_confirm': ''}, {'accept_terms': ''}, {'accept_terms': 'true'}, {'terms_version': 'forged'}):
            self.client.post('/account/register', data=self.form(**changes))
            conn = self.db()
            self.assertEqual(conn.execute('SELECT COUNT(*) FROM members').fetchone()[0], 0)
            conn.close()
        data = self.form()
        conn = self.db()
        conn.execute("UPDATE pages SET body=body || '\n\n## Updated condition\nRead this new condition.' WHERE slug='terms'")
        conn.commit(); conn.close()
        response = self.client.post('/account/register', data=data)
        self.assertIn(b'The Terms changed', response.data)
        data = self.form()
        self.assertEqual(self.client.post('/account/register', data=data).status_code, 302)
        conn = self.db()
        member = conn.execute("SELECT * FROM members WHERE username='PolicyPlayer'").fetchone()
        self.assertTrue(member['terms_accepted_at'])
        self.assertTrue(member['age_confirmed_at'])
        snapshot = conn.execute('SELECT * FROM registration_terms_versions WHERE version=?', (member['terms_version'],)).fetchone()
        self.assertIn('Updated condition', snapshot['body'])
        conn.execute("UPDATE pages SET body='Later terms' WHERE slug='terms'")
        conn.commit()
        with zipfile.ZipFile(export_zip(conn, member)) as archive:
            agreement = json.loads(archive.read('registration_agreement.json'))
            self.assertIn('Updated condition', agreement['terms']['body'])
        erase(conn, member)
        removed = conn.execute('SELECT * FROM members WHERE id=?', (member['id'],)).fetchone()
        self.assertIsNone(removed['terms_accepted_at'])
        self.assertIsNone(removed['age_confirmed_at'])
        self.assertEqual(removed['terms_version'], '')
        conn.close()

    def test_unpublished_terms_block_registration_and_expired_age_check(self):
        self.age_check()
        with self.client.session_transaction() as session:
            session['registration_age']['checked_at'] = '2000-01-01 00:00:00'
            session.modified = True
        self.assertIn(b'name="birth_date"', self.client.get('/account/register').data)
        self.age_check()
        conn = self.db()
        conn.execute("UPDATE pages SET status='draft' WHERE slug='terms'")
        conn.commit(); conn.close()
        page = self.client.get('/account/register')
        self.assertEqual(page.status_code, 503)
        self.assertNotIn(b'name="password"', page.data)

    def test_policy_reader_navigation_sanitisation_and_seed_preserves_staff_edits(self):
        for slug in ('rules', 'terms', 'privacy', 'store-terms', 'appeals', 'account-help', 'bug-reports', 'purchase-help'):
            page = self.client.get('/support/' + slug)
            self.assertEqual(page.status_code, 200, slug)
            self.assertIn(b'At a glance', page.data)
            self.assertIn(b'aria-label="Page sections"', page.data)
            source = page.get_data(as_text=True)
            ids = set(re.findall(r'id="([^"]+)"', source))
            for anchor in re.findall(r'href="#(policy-[^"]+)"', source):
                self.assertIn(anchor, ids)
        privacy = self.client.get('/support/privacy').get_data(as_text=True)
        for topic in ('Discord', 'Team applications', 'Account data', 'Children and parent requests'):
            self.assertIn(topic, privacy)
        conn = self.db()
        conn.execute("UPDATE pages SET body='## Custom heading\nOwner text <script>alert(1)</script>',updated_by='Owner' WHERE slug='privacy'")
        conn.execute("UPDATE settings SET value='17' WHERE key='seed_rev'")
        conn.execute("INSERT INTO members (username,password_hash,created_at) VALUES ('Existing','unused',?)", (now_str(),))
        conn.commit()
        seed_updates.apply(conn)
        self.assertIn('Owner text', conn.execute("SELECT body FROM pages WHERE slug='privacy'").fetchone()[0])
        self.assertIsNone(conn.execute("SELECT terms_accepted_at FROM members WHERE username='Existing'").fetchone()[0])
        conn.close()
        self.assertNotIn(b'<script>alert(1)</script>', self.client.get('/support/privacy').data)
