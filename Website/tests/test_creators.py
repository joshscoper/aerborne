import hashlib
import hmac
import io
import json
import zipfile
from unittest import mock

from test_app import SiteTest
from test_tebex import TebexTest
from aerborne import creators, tebex
from aerborne.db import now_str


class CreatorTest(SiteTest):
    def setUp(self):
        super().setUp()
        self.app.config['TEBEX_WEBHOOK_SECRET'] = 'test-webhook-secret'
        conn = self.db()
        for name in ('CreatorOne', 'CreatorTwo'):
            conn.execute('INSERT INTO members(username,password_hash,created_at) VALUES(?,?,?)', (name, '!fixture', now_str()))
        conn.execute("INSERT INTO users(username,password_hash,role,created_at) VALUES('Admin','!fixture','admin',?)", (now_str(),))
        self.admin_id = conn.execute("SELECT id FROM users WHERE username='Admin'").fetchone()[0]
        self.first = conn.execute("SELECT id FROM members WHERE username='CreatorOne'").fetchone()[0]
        self.second = conn.execute("SELECT id FROM members WHERE username='CreatorTwo'").fetchone()[0]
        conn.commit()
        conn.close()
        self.signin(self.first)

    def signin(self, member_id=None, admin=False):
        with self.client.session_transaction() as s:
            s.clear()
            if member_id:
                s['mid'], s['msv'] = member_id, 1
            if admin:
                s['uid'], s['sv'] = self.admin_id, 1

    def apply(self):
        token = self.csrf('/creator-program')
        return self.client.post('/creator-program', data={'_csrf': token, 'channels': 'https://twitch.tv/example',
             'pitch': 'I make welcoming streams and useful guides for new Aerborne adventurers.', 'contact': 'creator@example.test', 'agreement': '1'})

    def accepted(self, member_id=None):
        mid = member_id or self.first
        conn = self.db()
        conn.execute("INSERT INTO creator_applications(member_id,channels,pitch,contact,status,created_at) VALUES(?,?,?,?,'accepted',?)",
                     (mid, 'https://youtube.com/@example', 'Original videos', 'private@example.test', now_str()))
        app_id = conn.execute('SELECT id FROM creator_applications WHERE member_id=?', (mid,)).fetchone()[0]
        conn.commit()
        conn.close()
        return app_id

    def code(self, application_id, status='active', code='SKYCODE'):
        conn = self.db()
        cursor = conn.execute('INSERT INTO creator_codes(application_id,code,wallet_ref,status,created_at) VALUES(?,?,?,?,?)',
                             (application_id, code, 'private_wallet_ref', status, now_str()))
        code_id = cursor.lastrowid
        conn.commit()
        conn.close()
        return code_id

    def event(self, kind='payment.completed', event_id='event-1', date='2026-10-20T12:00:00Z', transaction='tbx-test', code='SKYCODE', amount='100.00', currency='USD', status=1, signed=True):
        event = {'id': event_id, 'type': kind, 'date': date, 'subject': {'transaction_id': transaction, 'creator_code': code,
                 'price': {'amount': amount, 'currency': currency}, 'status': {'id': status},
                 'customer': {'email': 'buyer-secret@example.test'}}}
        raw = json.dumps(event).encode()
        signature = hmac.new(b'test-webhook-secret', hashlib.sha256(raw).hexdigest().encode(), hashlib.sha256).hexdigest()
        return self.client.post('/integrations/tebex/webhook', data=raw, content_type='application/json',
                                headers={'X-Signature': signature if signed else 'forged'})

    def test_application_validation_privacy_and_duplicate_submission(self):
        self.assertEqual(self.client.post('/creator-program').status_code, 400)
        token = self.csrf('/creator-program')
        bad = self.client.post('/creator-program', data={'_csrf': token, 'channels': 'javascript:alert(1)'})
        self.assertEqual(bad.status_code, 400)
        self.assertEqual(self.apply().status_code, 303)
        with self.client.session_transaction() as s:
            token = s['_csrf']
        self.assertEqual(self.client.post('/creator-program', data={'_csrf': token}).status_code, 400)
        conn = self.db()
        self.assertEqual(conn.execute('SELECT COUNT(*) FROM creator_applications').fetchone()[0], 1)
        conn.close()
        self.signin()
        page = self.client.get('/creator-program').data
        self.assertNotIn(b'private@example.test', page)
        self.assertNotIn(b'creator@example.test', page)
        self.assertIn(b'Creator program', self.client.get('/').data)
        self.assertNotIn(b'Creator dashboard', self.client.get('/').data)

    def test_admin_acceptance_grants_media_and_suspension_removes_access(self):
        self.apply()
        conn = self.db()
        aid = conn.execute('SELECT id FROM creator_applications').fetchone()[0]
        conn.close()
        self.assertEqual(self.client.get('/creator-dashboard').status_code, 403)
        self.signin(admin=True)
        token = self.csrf('/admin/creator-program/%d' % aid)
        self.assertEqual(self.client.post('/admin/creator-program/%d' % aid, data={'_csrf': token, 'status': 'accepted'}).status_code, 303)
        conn = self.db()
        self.assertEqual(conn.execute('SELECT r.slug FROM members m JOIN ranks r ON r.id=m.rank_id WHERE m.id=?', (self.first,)).fetchone()[0], 'media')
        conn.close()
        self.signin(self.first)
        self.assertEqual(self.client.get('/creator-dashboard').status_code, 200)
        self.assertIn(b'Creator dashboard', self.client.get('/').data)
        self.code(aid)
        self.signin(admin=True)
        token = self.csrf('/admin/creator-program/%d' % aid)
        self.client.post('/admin/creator-program/%d' % aid, data={'_csrf': token, 'status': 'suspended'})
        self.signin(self.first)
        self.assertEqual(self.client.get('/creator-dashboard').status_code, 403)
        conn = self.db()
        self.assertEqual(conn.execute('SELECT status FROM creator_codes').fetchone()[0], 'disabled')
        self.assertIsNone(conn.execute('SELECT rank_id FROM members WHERE id=?', (self.first,)).fetchone()[0])
        conn.close()

    def test_code_request_requires_review_and_private_wallet(self):
        aid = self.accepted()
        token = self.csrf('/creator-dashboard')
        data = {'_csrf': token, 'code': 'FIRSTCODE', 'wallet_ref': 'my_private_wallet', 'code_agreement': '1'}
        self.assertEqual(self.client.post('/creator-dashboard', data=data).status_code, 303)
        self.assertEqual(self.client.post('/creator-dashboard', data=dict(data, code='ANOTHER')).status_code, 400)
        self.signin(self.second)
        self.assertEqual(self.client.get('/creator-dashboard').status_code, 403)
        self.assertNotIn(b'my_private_wallet', self.client.get('/members/CreatorOne').data)
        self.signin(admin=True)
        path = '/admin/creator-program/%d' % aid
        token = self.csrf(path)
        conn = self.db()
        cid = conn.execute('SELECT id FROM creator_codes').fetchone()[0]
        conn.close()
        form = {'_csrf': token, 'action': 'code', 'code_id': cid, 'status': 'active'}
        self.assertEqual(self.client.post(path, data=form).status_code, 400)
        self.assertEqual(self.client.post(path, data=dict(form, tebex_confirmed='1')).status_code, 303)

    def test_priority_ticket_access_reply_and_admin_inbox(self):
        self.accepted()
        token = self.csrf('/creator-dashboard')
        self.assertEqual(self.client.post('/creator-dashboard/tickets', data={'_csrf': token, 'title': 'Code request help', 'body': 'Please help me update my creator code.'}).status_code, 303)
        path = '/creator-dashboard/tickets/1'
        self.assertEqual(self.client.get(path).status_code, 200)
        self.accepted(self.second)
        self.signin(self.second)
        self.assertEqual(self.client.get(path).status_code, 404)
        self.assertEqual(self.client.post(path, data={'_csrf': self.csrf('/creator-dashboard'), 'body': 'Intrusion'}).status_code, 404)
        self.signin(admin=True)
        self.assertIn(b'Code request help', self.client.get('/admin/creator-program').data)
        token = self.csrf(path)
        self.assertEqual(self.client.post(path, data={'_csrf': token, 'body': 'We can help.', 'status': 'closed'}).status_code, 303)
        self.signin(self.first)
        self.assertIn(b'We can help.', self.client.get(path).data)

    def test_webhook_signature_idempotency_refunds_and_event_order(self):
        self.code(self.accepted())
        self.assertEqual(self.event(signed=False).status_code, 403)
        self.assertEqual(self.event().status_code, 204)
        self.assertEqual(self.event().status_code, 204)
        with self.app.test_request_context():
            result = creators.metrics(1)
            self.assertEqual(result[0]['orders'], 1)
            self.assertEqual(result[0]['estimate'], '25.00')
        self.event('payment.refunded', 'refund', '2026-10-20T13:00:00Z', status=2)
        self.event(event_id='delayed-completion')
        with self.app.test_request_context():
            self.assertEqual(creators.metrics(1), [])
        conn = self.db()
        self.assertEqual(conn.execute('SELECT COUNT(*) FROM creator_payments').fetchone()[0], 1)
        self.assertEqual(conn.execute('SELECT status FROM creator_payments').fetchone()[0], 'refunded')
        self.assertNotIn('buyer-secret', repr([dict(r) for r in conn.execute('SELECT * FROM creator_payments')]))
        conn.close()

    def test_webhook_validation_currency_disputes_and_invalid_amount(self):
        self.code(self.accepted())
        self.assertEqual(self.event(kind='validation.webhook').json, {'id': 'event-1'})
        self.assertEqual(self.event(amount='NaN').status_code, 400)
        self.assertEqual(self.event(currency='usd').status_code, 400)
        self.event()
        self.event(event_id='euro', transaction='tbx-euro', currency='EUR', amount='20')
        self.event(kind='payment.dispute.opened', event_id='dispute', date='2026-10-20T14:00:00+00:00')
        with self.app.test_request_context():
            self.assertEqual(creators.metrics(1)[0]['currency'], 'EUR')
        self.event(kind='payment.dispute.won', event_id='won', date='2026-10-20T16:00:00+01:00', status=1)
        with self.app.test_request_context():
            self.assertEqual(len(creators.metrics(1)), 2)

    def test_export_and_erasure_include_private_creator_data(self):
        from aerborne.members import export_zip, erase
        aid = self.accepted()
        self.code(aid)
        conn = self.db()
        member = conn.execute('SELECT * FROM members WHERE id=?', (self.first,)).fetchone()
        with zipfile.ZipFile(export_zip(conn, member)) as z:
            data = json.loads(z.read('creator_program.json'))
            self.assertEqual(data['codes'][0]['wallet_ref'], 'private_wallet_ref')
        erase(conn, member)
        conn.commit()
        self.assertEqual(conn.execute('SELECT wallet_ref FROM creator_codes').fetchone()[0], '')
        self.assertEqual(conn.execute('SELECT contact FROM creator_applications').fetchone()[0], '')
        conn.close()


class CreatorCartTest(TebexTest):
    def setUp(self):
        super().setUp()
        conn = self.db()
        conn.execute("INSERT INTO members(username,password_hash,created_at) VALUES('Creator','!fixture',?)", (now_str(),))
        mid = conn.execute("SELECT id FROM members WHERE username='Creator'").fetchone()[0]
        conn.execute("INSERT INTO creator_applications(member_id,channels,pitch,contact,status,created_at) VALUES(?,'https://example.test','Videos','private','accepted',?)", (mid, now_str()))
        aid = conn.execute('SELECT id FROM creator_applications').fetchone()[0]
        conn.execute("INSERT INTO creator_codes(application_id,code,wallet_ref,status,created_at) VALUES(?,'SKYCODE','wallet-private','active',?)", (aid, now_str()))
        conn.commit()
        conn.close()

    def fake_api(self, path, data=None):
        if path.endswith('/creator-codes'):
            self.api_calls.append((path, data))
            self.basket['creator_code'] = data['creator_code']
            return {'success': True}
        return super().fake_api(path, data)

    def test_creator_code_reaches_tebex_and_changes_checkout_reuse(self):
        token = self.add()
        self.assertEqual(self.buy(token, creator_code='skycode').status_code, 303)
        calls = [data for path, data in self.api_calls if path.endswith('/creator-codes')]
        self.assertEqual(calls, [{'creator_code': 'SKYCODE'}])
        self.buy(token, creator_code='SKYCODE')
        self.assertEqual(len([p for p,d in self.api_calls if p.endswith('/creator-codes')]), 1)
        self.buy(token, creator_code='')
        self.assertEqual(len([p for p,d in self.api_calls if p.endswith('/baskets')]), 2)
        self.assertNotIn(b'wallet-private', self.client.get('/store/cart').data)

    def test_inactive_code_and_upstream_rejection_fail_closed(self):
        token = self.add()
        self.assertEqual(self.buy(token, creator_code='UNKNOWN').status_code, 400)
        self.assertFalse(any(d for p,d in self.api_calls if p.endswith('/baskets')))
        original = self.fake_api
        def reject(path, data=None):
            if path.endswith('/creator-codes'):
                raise tebex.TebexError('Rejected')
            return original(path, data)
        with mock.patch('aerborne.tebex.api', side_effect=reject):
            self.assertEqual(self.buy(token, creator_code='SKYCODE').status_code, 503)
        with self.client.session_transaction() as s:
            self.assertEqual(s['store_cart'], ['skyfarer'])

    def test_disabling_a_code_blocks_a_cached_checkout(self):
        token = self.add()
        self.assertEqual(self.buy(token, creator_code='SKYCODE').status_code, 303)
        conn = self.db()
        conn.execute("UPDATE creator_codes SET status='disabled'")
        conn.commit()
        conn.close()
        self.assertEqual(self.buy(token, creator_code='SKYCODE').status_code, 400)

    def test_creator_api_success_envelope_and_attribution_verification(self):
        self.patcher.stop()
        with self.app.test_request_context(), mock.patch('aerborne.tebex.build_opener') as opener:
            opener.return_value.open.return_value = io.BytesIO(b'{"success":true,"message":"Applied"}')
            self.assertTrue(tebex.api('/accounts/test-public-token/baskets/example/creator-codes', {'creator_code':'SKYCODE'})['success'])
            opener.return_value.open.return_value = io.BytesIO(b'{"success":false,"message":"Unavailable"}')
            with self.assertRaises(tebex.TebexError):
                tebex.api('/accounts/test-public-token/baskets/example/creator-codes', {'creator_code':'SKYCODE'})
        self.patcher.start()
        token = self.add()
        original = self.fake_api
        def unconfirmed(path, data=None):
            if path.endswith('/creator-codes'):
                return {'success': True}
            return original(path, data)
        with mock.patch('aerborne.tebex.api', side_effect=unconfirmed):
            self.assertEqual(self.buy(token, creator_code='SKYCODE').status_code, 503)
