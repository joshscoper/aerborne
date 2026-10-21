import base64
import copy
import io
import json
from unittest import mock
from urllib.error import HTTPError, URLError

from test_app import SiteTest
from aerborne import tebex


class TebexTest(SiteTest):
    def setUp(self):
        super().setUp()
        self.app.config.update(TEBEX_PUBLIC_TOKEN='test-public-token', TEBEX_PRIVATE_KEY='server-secret')
        self.package = {'id': 123, 'ident': 'pkg_10_123', 'name': 'Skyfarer', 'total_price': 4.99,
                        'currency': 'USD', 'type': 'single', 'description': '<ul><li>Skyfarer badge</li></ul>'}
        self.categories = [{'name': 'Supporter Ranks', 'packages': [self.package]}]
        self.basket = {'ident': 'basket-test', 'complete': False, 'packages': [],
                       'links': {'checkout': 'https://pay.tebex.io/test-checkout'}}
        self.api_calls = []
        self.patcher = mock.patch('aerborne.tebex.api', side_effect=self.fake_api)
        self.patcher.start()
        self.addCleanup(self.patcher.stop)
        conn = self.db()
        conn.execute("UPDATE products SET availability='released', tebex_package_id='123', price='$999', tebex_url='' WHERE slug='skyfarer'")
        conn.commit()
        conn.close()

    def fake_api(self, path, data=None):
        self.api_calls.append((path, data))
        if path.endswith('/categories?includePackages=1'):
            return copy.deepcopy(self.categories)
        if path == '/accounts/test-public-token':
            return {'name': 'Aerborne', 'currency': 'USD', 'disabled': True}
        if path.endswith('/baskets') or path.startswith('/baskets/') or '/baskets/' in path:
            return copy.deepcopy(self.basket)
        raise AssertionError(path)

    def add(self):
        token = self.csrf('/store/item/skyfarer')
        response = self.client.post('/store/cart/add/skyfarer', data={'_csrf': token})
        self.assertEqual(response.status_code, 303)
        return token

    def buy(self, token, **extra):
        form = {'_csrf': token, 'username': 'Test_Player', 'confirm_recipient': '1'}
        form.update(extra)
        return self.client.post('/store/checkout', data=form)

    def test_live_prices_cart_deduplication_and_remove(self):
        page = self.client.get('/store/item/skyfarer').get_data(as_text=True)
        self.assertIn('4.99 USD', page)
        self.assertNotIn('$999', page)
        self.assertIn('Add to cart', page)
        self.assertIn('Skyfarer badge', page)
        self.assertNotIn('server-secret', page)
        token = self.add()
        self.add()
        page = self.client.get('/store/cart').get_data(as_text=True)
        self.assertIn('Estimated subtotal', page)
        self.assertIn('4.99 USD', page)
        policy = self.client.get('/store/cart').headers['Content-Security-Policy']
        self.assertIn("form-action 'self' https://pay.tebex.io https://checkout.tebex.io", policy)
        self.assertIn("script-src 'self'", policy)
        with self.client.session_transaction() as session:
            self.assertEqual(session['store_cart'], ['skyfarer'])
        self.assertFalse(any(data is not None for _, data in self.api_calls))
        response = self.client.post('/store/cart/remove/skyfarer', data={'_csrf': token})
        self.assertEqual(response.status_code, 303)
        self.assertIn(b'Your cart is empty.', self.client.get('/store/cart').data)

    def test_checkout_uses_server_package_and_recipient_not_client_price(self):
        token = self.add()
        response = self.buy(token, price='0.01', package_id='evil', quantity='999')
        self.assertEqual(response.status_code, 303)
        self.assertEqual(response.location, 'https://pay.tebex.io/test-checkout')
        creates = [data for path, data in self.api_calls if path.endswith('/baskets') and data]
        self.assertEqual(creates[0]['username'], 'Test_Player')
        self.assertEqual(creates[0]['ip_address'], '127.0.0.1')
        self.assertTrue(creates[0]['complete_url'].endswith('/store/checkout/return'))
        additions = [data for path, data in self.api_calls if path.startswith('/baskets/')]
        self.assertEqual(additions, [{'package_id': 'pkg_10_123', 'quantity': 1}])
        self.buy(token)
        self.assertEqual(len([data for path, data in self.api_calls if path.endswith('/baskets') and data]), 1)

    def test_csrf_recipient_and_rate_limit(self):
        self.assertEqual(self.client.post('/store/cart/add/skyfarer').status_code, 400)
        token = self.add()
        self.assertEqual(self.buy(token, username='a<script>').status_code, 400)
        self.assertEqual(self.buy(token, confirm_recipient='').status_code, 400)
        self.assertEqual(self.client.post('/store/checkout').status_code, 400)
        for _ in range(10):
            self.assertEqual(self.buy(token).status_code, 303)
        self.assertEqual(self.buy(token).status_code, 429)

    def test_fresh_catalog_removal_and_outage_fail_closed(self):
        token = self.add()
        self.categories[0]['packages'] = []
        self.assertEqual(self.buy(token).status_code, 409)
        self.assertFalse(any(data for path, data in self.api_calls if path.endswith('/baskets')))
        with mock.patch('aerborne.tebex.api', side_effect=tebex.TebexError('Connection unavailable')):
            self.app.extensions.pop('tebex_catalog', None)
            page = self.client.get('/store/item/skyfarer').get_data(as_text=True)
            self.assertIn('Price temporarily unavailable', page)
            self.assertNotIn('Add to cart', page)
            self.assertNotIn('$999', page)

    def test_drafts_upcoming_and_unpublished_terms_cannot_be_purchased(self):
        token = self.add()
        conn = self.db()
        for sql in ("UPDATE products SET status='draft' WHERE slug='skyfarer'",
                    "UPDATE products SET status='published', availability='upcoming' WHERE slug='skyfarer'",
                    "UPDATE products SET availability='released' WHERE slug='skyfarer'"):
            conn.execute(sql)
            if 'availability=\'released\'' in sql:
                conn.execute("UPDATE pages SET publish_at='2099-01-01 00:00:00' WHERE slug='store-terms'")
            conn.commit()
            self.assertEqual(self.buy(token).status_code, 409)
        conn.close()

    def test_checkout_return_verifies_only_session_basket(self):
        page = self.client.get('/store/checkout/return?complete=1&basket=someone-else').get_data(as_text=True)
        self.assertIn('haven’t confirmed', page)
        token = self.add()
        self.buy(token)
        self.assertIn(b'Your cart is saved', self.client.get('/store/checkout/return').data)
        self.basket['complete'] = True
        page = self.client.get('/store/checkout/return').get_data(as_text=True)
        self.assertIn('checkout is complete', page)
        with self.client.session_transaction() as session:
            self.assertNotIn('store_cart', session)
            self.assertNotIn('store_checkout', session)

    def test_invalid_checkout_host_and_partial_failure_do_not_redirect(self):
        token = self.add()
        self.basket['links']['checkout'] = 'https://pay.tebex.io.evil.example/steal'
        response = self.buy(token)
        self.assertEqual(response.status_code, 503)
        self.assertNotIn('Location', response.headers)
        with self.client.session_transaction() as session:
            self.assertEqual(session['store_cart'], ['skyfarer'])

    def test_unique_sync_matches_preserves_artwork_and_skips_ambiguous(self):
        conn = self.db()
        before = dict(conn.execute("SELECT * FROM products WHERE slug='skyfarer'").fetchone())
        with self.app.test_request_context():
            linked, _ = tebex.sync_products(conn)
        self.assertIn('skyfarer', linked)
        after = dict(conn.execute("SELECT * FROM products WHERE slug='skyfarer'").fetchone())
        self.assertEqual(after['image'], before['image'])
        self.assertEqual(after['includes'], before['includes'])
        self.assertEqual(after['price'], '4.99 USD')
        conn.execute("UPDATE products SET tebex_package_id='' WHERE slug='skyfarer'")
        conn.commit()
        self.categories[0]['packages'].append(dict(self.package, id=456, ident='pkg_10_456'))
        with self.app.test_request_context():
            linked, skipped = tebex.sync_products(conn)
        self.assertNotIn('skyfarer', linked)
        self.assertIn('skyfarer', skipped)
        conn.close()

    def test_http_auth_stays_on_server_and_errors_are_redacted(self):
        self.patcher.stop()
        response = io.BytesIO(b'{"data":{"ident":"basket-test"}}')
        with self.app.test_request_context(), mock.patch('aerborne.tebex.build_opener') as opener:
            opener.return_value.open.return_value = response
            self.assertEqual(tebex.api('/accounts/test-public-token/baskets', {'username': 'Test_Player'})['ident'], 'basket-test')
            request = opener.return_value.open.call_args[0][0]
            self.assertEqual(request.get_header('Authorization'), 'Basic ' + base64.b64encode(b'test-public-token:server-secret').decode())
            opener.return_value.open.side_effect = HTTPError('https://secret-url', 422, 'secret body', {}, None)
            with self.assertRaises(tebex.TebexError) as error:
                tebex.api('/baskets/test/packages', {'package_id': 123})
            self.assertNotIn('secret', str(error.exception))
            opener.return_value.open.side_effect = URLError('server-secret')
            with self.assertRaises(tebex.TebexError) as error:
                tebex.api('/accounts/test-public-token')
            self.assertNotIn('server-secret', str(error.exception))
