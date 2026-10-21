import re
from test_app import SiteTest


class JoinUsTest(SiteTest):
    def form(self, **changes):
        page = self.client.get('/join-us').get_data(as_text=True)
        data = {'_csrf': re.search(r'name="_csrf" value="([^"]+)"', page).group(1),
                'submission_key': re.search(r'name="submission_key" value="([^"]+)"', page).group(1),
                'name': 'Test Applicant', 'email': 'applicant@example.com', 'role': 'developer',
                'discord': 'test.applicant', 'minecraft_name': 'Test_Player', 'timezone': 'America/Denver',
                'availability': 'A few hours on weekends.', 'experience': 'I maintain small community projects.',
                'motivation': 'I enjoy helping players and building useful tools.',
                'approach': 'Reproduce the issue, inspect the logs and check a small fix before release.',
                'portfolio': 'https://example.com/portfolio', 'privacy_ack': '1'}
        data.update(changes)
        return data

    def test_footer_only_discovery_roles_and_support_email(self):
        page = self.client.get('/').get_data(as_text=True)
        self.assertNotIn('/join-us', page.split('<footer')[0])
        self.assertIn('href="/join-us"', page.split('<footer')[1])
        self.assertIn('mailto:support@aerborne.net', page)
        self.assertIn(b'mailto:support@aerborne.net', self.client.get('/support').data)
        self.assertNotIn(b'/join-us', self.client.get('/sitemap.xml').data)
        page = self.client.get('/join-us').get_data(as_text=True)
        for role in ('Helper', 'Moderator', 'Forum moderator', 'Builder', 'Developer', 'Artist', 'Writer', 'Events organiser'):
            self.assertIn(role, page)
        self.assertEqual(self.client.get('/static/css/join-us.css').status_code, 200)
        self.assertEqual(self.client.get('/static/js/join-us.js').status_code, 200)

    def test_guest_submission_saved_once_and_private_receipt(self):
        form = self.form()
        for _ in range(2):
            response = self.client.post('/join-us', data=form)
            self.assertEqual(response.status_code, 303)
        conn = self.db()
        rows = conn.execute('SELECT * FROM team_applications').fetchall()
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['status'], 'new')
        self.assertEqual(rows[0]['email'], 'applicant@example.com')
        receipt = self.client.get('/join-us/thanks')
        self.assertIn(rows[0]['reference'], receipt.get_data(as_text=True))
        self.assertNotIn(b'applicant@example.com', receipt.data)
        self.assertIn('no-store', receipt.headers['Cache-Control'])
        stranger = self.app.test_client()
        self.assertEqual(stranger.get('/join-us/thanks').status_code, 404)
        self.assertEqual(stranger.get('/join-us?application=' + rows[0]['reference']).status_code, 200)
        self.assertNotIn(b'applicant@example.com', stranger.get('/join-us').data)
        conn.close()

    def test_server_validation_csrf_links_and_answer_preservation(self):
        self.assertEqual(self.client.post('/join-us', data={}).status_code, 400)
        form = self.form(role='owner', portfolio='javascript:alert(1)', privacy_ack='', email='not-an-email')
        response = self.client.post('/join-us', data=form)
        self.assertEqual(response.status_code, 400)
        body = response.get_data(as_text=True)
        self.assertIn('Test Applicant', body)
        self.assertIn('Choose a role from the list', body)
        self.assertIn('email address', body)
        self.assertIn('https://', body)
        self.assertIn('Confirm how your application', body)
        conn = self.db()
        self.assertEqual(conn.execute('SELECT COUNT(*) FROM team_applications').fetchone()[0], 0)
        conn.close()

    def test_rate_limit_and_honeypot(self):
        for _ in range(10):
            response = self.client.post('/join-us', data=self.form(website='spam'))
            self.assertEqual(response.status_code, 400)
        self.assertEqual(self.client.post('/join-us', data=self.form()).status_code, 429)

    def test_admin_review_access_notes_and_removal(self):
        self.client.post('/join-us', data=self.form(experience='<script>alert(1)</script>'))
        conn = self.db()
        item = conn.execute('SELECT id FROM team_applications').fetchone()[0]
        conn.close()
        path = '/admin/team-applications/%d' % item
        self.assertEqual(self.client.get(path).status_code, 302)
        for role in ('moderator', 'editor', 'writer'):
            self.add_user('test-' + role, role)
            self.login('test-' + role)
            self.assertEqual(self.client.get(path).status_code, 403)
            self.assertEqual(self.client.get('/admin/team-applications').status_code, 403)
        self.add_user('boss', 'admin')
        self.login('boss')
        page = self.client.get(path)
        self.assertEqual(page.status_code, 200)
        self.assertIn(b'&lt;script&gt;', page.data)
        self.assertNotIn(b'<script>alert(1)</script>', page.data)
        self.assertIn('no-store', page.headers['Cache-Control'])
        self.assertIn(b'Test Applicant', self.client.get('/admin/team-applications').data)
        token = self.csrf(path)
        self.assertEqual(self.client.post(path, data={'status': 'accepted'}).status_code, 400)
        self.assertEqual(self.client.post(path, data={'_csrf': token, 'status': 'reviewing', 'notes': 'Private review note.'}).status_code, 303)
        conn = self.db()
        row = conn.execute('SELECT * FROM team_applications WHERE id=?', (item,)).fetchone()
        self.assertEqual(row['notes'], 'Private review note.')
        self.assertEqual(row['reviewed_by'], 'boss')
        self.assertNotIn('Private review note.', self.client.get('/join-us').get_data(as_text=True))
        conn.close()
        self.assertEqual(self.client.post(path, data={'_csrf': token, 'action': 'delete'}).status_code, 303)
        self.assertEqual(self.client.get(path).status_code, 404)
