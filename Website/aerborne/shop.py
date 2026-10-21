"""Guest-friendly cart; Tebex owns the actual order, payment and delivery."""
import re
import time
from decimal import Decimal

from flask import Blueprint, abort, current_app, g, redirect, render_template, request, session, url_for

from . import tebex
from .content import published_one
from .db import get_db
from .security import CSP, allow, check_csrf, client_ip

bp = Blueprint('shop', __name__, url_prefix='/store')


def cart_items(state=None):
    items = []
    for slug in session.get('store_cart', [])[:20]:
        row = published_one('products', slug)
        if row:
            items.append(tebex.decorate(row, state))
    return items


@bp.after_request
def no_cache(response):
    response.headers['Cache-Control'] = 'no-store'
    response.headers['Referrer-Policy'] = 'same-origin'
    # Browsers also apply form-action to the final destination of a POST redirect.
    response.headers['Content-Security-Policy'] = CSP.replace("form-action 'self'",
        "form-action 'self' https://pay.tebex.io https://checkout.tebex.io")
    return response


@bp.route('/cart')
def cart():
    return render_cart()


def render_cart(error='', code=200):
    state = tebex.catalog()
    items = cart_items(state)
    currencies = {p['tebex_package']['currency'] for p in items if p['tebex_package']}
    total = ''
    if items and len(currencies) == 1 and all(p['purchasable'] and p['tebex_package'] for p in items):
        total = tebex.money(sum(Decimal(str(p['tebex_package']['total_price'])) for p in items), next(iter(currencies)))
    username = request.form.get('username', session.get('store_username', ''))
    if not username and g.get('member'):
        username = g.member['mc_name'] or ''
    return render_template('store_cart.html', items=items, total=total, username=username, error=error,
                           creator_code=request.form.get('creator_code', session.get('store_creator_code', ''))[:32],
                           can_checkout=bool(total) and bool(current_app.config.get('TEBEX_PRIVATE_KEY'))), code


@bp.route('/cart/add/<slug>', methods=['POST'])
def add(slug):
    check_csrf()
    item = published_one('products', slug) or abort(404)
    item = tebex.decorate(item)
    if not item['tebex_package'] or not item['purchasable']:
        return render_cart('This item is not available for checkout right now.', 409)
    slugs = list(dict.fromkeys(session.get('store_cart', [])))
    if slug not in slugs and len(slugs) < 20:
        slugs.append(slug)
    session['store_cart'] = slugs
    session.pop('store_checkout', None)
    return redirect(url_for('shop.cart'), code=303)


@bp.route('/cart/remove/<slug>', methods=['POST'])
def remove(slug):
    check_csrf()
    session['store_cart'] = [s for s in session.get('store_cart', []) if s != slug]
    session.pop('store_checkout', None)
    if not session['store_cart']:
        session.pop('store_username', None)
    return redirect(url_for('shop.cart'), code=303)


@bp.route('/checkout', methods=['POST'])
def checkout():
    check_csrf()
    username = request.form.get('username', '').strip()
    if not re.fullmatch(r'[A-Za-z0-9_]{3,16}', username):
        return render_cart('Enter a Java Edition Minecraft username (3–16 letters, numbers or underscores).', 400)
    if request.form.get('confirm_recipient') != '1':
        return render_cart('Confirm the Minecraft account that should receive these items.', 400)
    conn = get_db()
    permitted = allow(conn, 'store-checkout', client_ip(), 10, 600)
    conn.commit()
    if not permitted:
        return render_cart('Too many checkout attempts. Please wait a few minutes and try again.', 429)
    from .creators import CODE_RE, active_code
    creator_code = request.form.get('creator_code', '').strip()
    if creator_code:
        creator = active_code(creator_code) if CODE_RE.fullmatch(creator_code) else None
        if not creator:
            return render_cart('That creator code is unavailable. Check it or clear the field to continue without a code.', 400)
        creator_code = creator['code']
    session['store_creator_code'] = creator_code
    state = tebex.catalog(force=True)
    items = cart_items(state)
    if not items or len(items) != len(session.get('store_cart', [])) or not all(i['purchasable'] and i['tebex_package'] for i in items):
        return render_cart('Your cart has an unavailable item. Review it before continuing.', 409)
    fingerprint = [(i['slug'], str(i['tebex_package']['id']), i['display_price']) for i in items]
    existing = session.get('store_checkout')
    if existing and existing.get('until', 0) > time.time() and existing.get('username') == username and existing.get('creator_code', '') == creator_code and existing.get('items') == [list(p) for p in fingerprint]:
        return redirect(existing['url'], code=303)
    # Use a configured canonical URL in production; never take a return URL from submitted data.
    base = current_app.config.get('SITE_URL') or request.url_root.rstrip('/')
    try:
        ident, target = tebex.checkout(items, username, client_ip(),
                       base + url_for('shop.checkout_return'), base + url_for('shop.cart'), creator_code=creator_code)
    except tebex.TebexError as exc:
        return render_cart(str(exc), 503)
    session['store_username'] = username
    session['store_checkout'] = {'ident': ident, 'url': target, 'username': username,
                                'creator_code': creator_code,
                                'items': [list(p) for p in fingerprint], 'until': time.time() + 1800}
    return redirect(target, code=303)


@bp.route('/checkout/return')
def checkout_return():
    # A visit or query parameter is never proof of payment. Check only this session's basket.
    complete = False
    pending = session.get('store_checkout')
    if pending:
        try:
            basket = tebex.api(tebex.account_path('/baskets/' + tebex.quote(pending['ident'], safe='')))
            complete = basket.get('complete') is True
        except tebex.TebexError:
            pass
    if complete:
        session.pop('store_cart', None)
        session.pop('store_checkout', None)
    return render_template('store_checkout_return.html', complete=complete)
