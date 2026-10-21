"""Server-side Tebex Headless catalog and checkout. Never accepts client prices."""
import base64
import json
import re
import threading
import time
from decimal import Decimal, InvalidOperation
from html.parser import HTMLParser
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

from flask import current_app, g

from .content import published_one
from .db import audit, now_str

BASE = 'https://headless.tebex.io/api'


class TebexError(Exception):
    pass


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def configured():
    return bool(current_app.config.get('TEBEX_PUBLIC_TOKEN'))


def api(path, data=None):
    config = current_app.config
    if not configured():
        raise TebexError('The store connection is not configured yet.')
    headers = {'Accept': 'application/json', 'User-Agent': 'Aerborne-Store/1.0'}
    if data is not None:
        if not config.get('TEBEX_PRIVATE_KEY'):
            raise TebexError('Checkout is temporarily unavailable. Please try again later.')
        pair = config['TEBEX_PUBLIC_TOKEN'] + ':' + config['TEBEX_PRIVATE_KEY']
        headers['Authorization'] = 'Basic ' + base64.b64encode(pair.encode()).decode('ascii')
        headers['Content-Type'] = 'application/json'
    request = Request(BASE + path, headers=headers,
                      data=json.dumps(data).encode('utf-8') if data is not None else None)
    try:
        with build_opener(NoRedirect()).open(request, timeout=config.get('TEBEX_TIMEOUT', 8)) as response:
            raw = response.read(2 * 1024 * 1024 + 1)
        if len(raw) > 2 * 1024 * 1024:
            raise ValueError('Response too large')
        result = json.loads(raw.decode('utf-8'))
        if isinstance(result, dict) and isinstance(result.get('success'), bool) and path.endswith('/creator-codes'):
            if not result['success']:
                raise TebexError('Tebex could not apply that creator code. Please check the code or remove it before continuing.')
            return result
        if not isinstance(result, dict) or 'data' not in result:
            raise ValueError('Invalid response')
        return result['data']
    except HTTPError as exc:
        # Upstream bodies and URLs can contain credentials, basket IDs and customer details.
        current_app.logger.warning('Tebex API HTTP %s', exc.code)
        if exc.code in (400, 422):
            raise TebexError('Tebex could not prepare this order. Check your Minecraft username and try again. If it continues, contact purchase support.')
        raise TebexError('Tebex checkout is temporarily unavailable. Please try again shortly.')
    except (URLError, OSError, ValueError, TypeError):
        raise TebexError('We cannot reach Tebex right now. Your cart is saved; please try again shortly.')


def account_path(suffix=''):
    return '/accounts/' + quote(current_app.config['TEBEX_PUBLIC_TOKEN'], safe='') + suffix


def money(amount, currency):
    try:
        value = Decimal(str(amount))
        if not value.is_finite() or value < 0 or not re.fullmatch(r'[A-Z]{3}', currency or ''):
            return ''
        return '{:.2f} {}'.format(value, currency)
    except (InvalidOperation, TypeError, ValueError):
        return ''


def catalog(force=False):
    if not force and hasattr(g, 'tebex_catalog'):
        return g.tebex_catalog
    state = {'configured': configured(), 'ready': False, 'packages': {}, 'error': '', 'store': {}}
    if not configured():
        return state
    cache = current_app.extensions.setdefault('tebex_catalog', {'lock': threading.Lock()})
    with cache['lock']:
        if not force and cache.get('until', 0) > time.monotonic():
            state = cache['state']
        else:
            try:
                store = api(account_path())
                categories = api(account_path('/categories?includePackages=1'))
                if not isinstance(store, dict) or not isinstance(categories, list):
                    raise TebexError('The Tebex catalog could not be loaded. Please try again shortly.')
                packages = {}
                for category in categories:
                    if category.get('dynamic'):
                        continue
                    for package in category.get('packages') or []:
                        if not money(package.get('total_price'), package.get('currency')):
                            continue
                        package = dict(package, category_name=category.get('name', ''))
                        packages[str(package['id'])] = package
                        if package.get('ident'):
                            packages[package['ident']] = package
                state.update(ready=True, packages=packages, store=store)
            except (TebexError, KeyError, TypeError) as exc:
                state['error'] = str(exc) if isinstance(exc, TebexError) else 'The Tebex catalog could not be loaded.'
            cache.update(state=state, until=time.monotonic() + (60 if state['ready'] else 15))
    g.tebex_catalog = state
    return state


class PackageContents(HTMLParser):
    """Extract plain list items; never render Tebex HTML or run its contents."""
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.items, self.parts, self.depth = [], [], 0

    def handle_starttag(self, tag, attrs):
        if tag == 'li':
            if self.depth == 0:
                self.parts = []
            self.depth += 1
        elif tag == 'br' and self.depth:
            self.parts.append(' ')

    def handle_endtag(self, tag):
        if tag == 'li' and self.depth:
            self.depth -= 1
            if not self.depth:
                value = ' '.join(''.join(self.parts).split())
                if value:
                    self.items.append(value[:1000])

    def handle_data(self, data):
        if self.depth:
            self.parts.append(data)


def package_for(item, state=None):
    return (state or catalog())['packages'].get(str(item.get('tebex_package_id') or ''))


def decorate(row, state=None):
    item = dict(row)
    state = state if state is not None else catalog()
    package = package_for(item, state)
    item.update(tebex_connected=state['configured'], tebex_package=package,
                purchasable=False, display_price='', store_message='Not on sale yet')
    if state['configured']:
        if package:
            item['display_price'] = money(package['total_price'], package['currency'])
            reader = PackageContents()
            reader.feed(package.get('description') or '')
            if reader.items:
                item['includes'] = '\n'.join(reader.items)
            item['purchasable'] = (item.get('availability') == 'released' and bool(current_app.config.get('TEBEX_PRIVATE_KEY'))
                                   and published_one('pages', 'store-terms') is not None)
            item['store_message'] = 'Checkout temporarily unavailable' if item['availability'] == 'released' else 'Not on sale yet'
        elif not state['ready']:
            item['store_message'] = 'Price temporarily unavailable'
    else:
        item['purchasable'] = bool(item.get('availability') == 'released' and item.get('price') and item.get('tebex_url'))
        item['display_price'] = item.get('price') if item['purchasable'] else ''
    return item


def checkout(cart, username, ip_address, complete_url, cancel_url, creator_code=''):
    basket = api(account_path('/baskets'), {'username': username, 'ip_address': ip_address,
                 'complete_url': complete_url, 'cancel_url': cancel_url, 'complete_auto_redirect': True})
    if not isinstance(basket, dict):
        raise TebexError('Tebex did not return a checkout. Please try again.')
    ident = basket.get('ident')
    if not ident or not isinstance(ident, str) or len(ident) > 180:
        raise TebexError('Tebex did not return a checkout. Please try again.')
    for item in cart:
        package = item['tebex_package']
        basket = api('/baskets/' + quote(ident, safe='') + '/packages',
                     {'package_id': package.get('ident') or package['id'], 'quantity': 1})
        if not isinstance(basket, dict) or basket.get('ident') != ident:
            raise TebexError('Tebex could not prepare the complete order. Your cart is saved.')
    if creator_code:
        try:
            api(account_path('/baskets/' + quote(ident, safe='') + '/creator-codes'), {'creator_code': creator_code})
            basket = api(account_path('/baskets/' + quote(ident, safe='')))
        except TebexError:
            raise TebexError('Tebex could not verify your creator code. Check it or remove it and try again. Your cart is saved.')
        if (not isinstance(basket, dict) or basket.get('ident') != ident
                or str(basket.get('creator_code') or '').lower() != creator_code.lower()):
            raise TebexError('Tebex did not confirm your creator code. Your cart is saved; please try again or contact support.')
    target = (basket.get('links') or {}).get('checkout', '')
    try:
        parsed = urlsplit(target)
        valid = (parsed.scheme == 'https' and parsed.hostname in ('pay.tebex.io', 'checkout.tebex.io')
                 and not parsed.username and not parsed.password and parsed.port in (None, 443))
    except (TypeError, ValueError):
        valid = False
    if not valid:
        raise TebexError('Tebex did not return a valid checkout. Please try again.')
    return ident, target


def sync_products(conn):
    """Match the existing catalog by unique category/name; never invent IDs or prices."""
    state = catalog(force=True)
    if not state['ready']:
        raise TebexError(state['error'])
    categories = {'supporter-ranks': 'supporterranks', 'companions': 'companions',
                  'trails': 'trails', 'cosmetic-bundles': 'bundles', 'island-decor': 'islanddecor'}
    norm = lambda value: re.sub(r'[^a-z0-9]', '', value.lower())
    packages = {p['id']: p for p in state['packages'].values()}.values()
    linked, skipped = [], []
    for row in conn.execute('SELECT * FROM products').fetchall():
        name = row['name']
        candidates = {norm(name)}
        if row['category'] == 'companions' and name.lower().endswith(' companion'):
            candidates.add(norm(name[:-10]))
        if row['category'] == 'trails' and name.lower().endswith(' trail'):
            candidates.add(norm(name[:-6]))
        if row['category'] == 'cosmetic-bundles':
            candidates.add(norm(name + ' Bundle'))
        if row['slug'] == 'comet-tail-trail':
            candidates.add('comet')
        matches = [p for p in packages if norm(p['name']) in candidates and norm(p['category_name']) == categories.get(row['category'])]
        if row['tebex_package_id']:
            matches = [p for p in packages if str(p['id']) == row['tebex_package_id'] or p.get('ident') == row['tebex_package_id']]
        if len(matches) != 1:
            skipped.append(row['slug'])
            continue
        package = matches[0]
        conn.execute("UPDATE products SET tebex_package_id=?, price=?, availability='released', updated_at=? WHERE id=?",
                     (str(package['id']), money(package['total_price'], package['currency']), now_str(), row['id']))
        linked.append(row['slug'])
    audit(conn, None, 'tebex-sync', 'products', None, 'Linked %d products to verified Tebex packages' % len(linked))
    conn.commit()
    return linked, skipped
