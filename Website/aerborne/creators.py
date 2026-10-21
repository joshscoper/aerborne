"""Creator applications, private support and signed Tebex payment metrics.

Code provisioning and payout settings remain in Tebex; local approval never invents a wallet payout.
"""
import datetime
import functools
import hashlib
import hmac
import json
import re
import sqlite3
from decimal import Decimal, InvalidOperation
from urllib.parse import urlsplit

from flask import abort, current_app, flash, g, jsonify, redirect, render_template, request, url_for

from . import auth
from .admin import bp as admin_bp
from .public import bp as public_bp
from .community import member_required
from .db import audit, get_db, now_str
from .security import allow, check_csrf, client_ip

CODE_RE = re.compile(r'^[A-Za-z0-9_-]{3,32}$')
APPLICATION_STATUSES = ('pending', 'reviewing', 'accepted', 'declined', 'suspended')
CODE_STATUSES = ('requested', 'active', 'rejected', 'disabled')


def is_admin():
    user = g.get('user')
    return bool(user and user['active'] and user['role'] == 'admin' and not user['must_change_password'])


def application_for_member():
    member = g.get('member')
    if not member or member['status'] != 'active':
        return None
    return get_db().execute('SELECT * FROM creator_applications WHERE member_id=?', (member['id'],)).fetchone()


def can_dashboard():
    if is_admin():
        return True
    application = application_for_member()
    return bool(application and application['status'] == 'accepted')


def creator_required(view):
    @functools.wraps(view)
    def wrapped(*args, **kwargs):
        if not g.get('member') and not is_admin():
            return redirect(url_for('community.login', next=request.path))
        if not can_dashboard():
            abort(403)
        return view(*args, **kwargs)
    return wrapped


@public_bp.before_request
def creator_request_limit():
    if ((request.endpoint or '').startswith('public.creator') and request.method == 'POST'
            and request.content_length and request.content_length > 30000):
        abort(413)


@public_bp.after_request
def creator_headers(response):
    if (request.endpoint or '').startswith('public.creator') or request.endpoint == 'public.tebex_webhook':
        response.headers['Cache-Control'] = 'no-store'
        response.headers['X-Robots-Tag'] = 'noindex, nofollow'
        response.headers['Referrer-Policy'] = 'same-origin'
    return response


def _limited(action, limit=10):
    conn = get_db()
    permitted = allow(conn, action, str(g.member['id']) if g.get('member') else client_ip(), limit, 3600)
    conn.commit()
    if not permitted:
        abort(429)


def _links_valid(value):
    links = [line.strip() for line in value.splitlines() if line.strip()]
    if not 1 <= len(links) <= 5:
        return False
    for link in links:
        try:
            parsed = urlsplit(link)
            if (parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password
                    or any(c.isspace() for c in link)):
                return False
        except ValueError:
            return False
    return True


@public_bp.route('/creator-program', methods=['GET', 'POST'])
def creator_program():
    app = application_for_member()
    error, values = '', {}
    if request.method == 'POST':
        check_csrf()
        if not g.get('member') or g.member['status'] != 'active':
            abort(403)
        _limited('creator-application', 5)
        get_db().execute('BEGIN IMMEDIATE')
        app = application_for_member()
        values = {key: request.form.get(key, '').strip() for key in ('channels', 'pitch', 'contact')}
        if app and app['status'] not in ('declined',):
            error = 'You already have an application. Its current status is shown below.'
        elif not _links_valid(values['channels']) or len(values['channels']) > 2000:
            error = 'Add one to five complete https:// channel or portfolio links, one per line.'
        elif not 40 <= len(values['pitch']) <= 3000:
            error = 'Tell us about your content in 40 to 3,000 characters.'
        elif not 3 <= len(values['contact']) <= 254:
            error = 'Add an email address or Discord username so we can contact you.'
        elif request.form.get('agreement') != '1':
            error = 'Please confirm the program terms and use of your application details.'
        else:
            conn = get_db()
            if app:
                conn.execute("UPDATE creator_applications SET channels=?,pitch=?,contact=?,status='pending',created_at=?,reviewed_at=NULL,reviewed_by='' WHERE id=?",
                             (values['channels'], values['pitch'], values['contact'], now_str(), app['id']))
            else:
                conn.execute('INSERT OR IGNORE INTO creator_applications(member_id,channels,pitch,contact,created_at) VALUES(?,?,?,?,?)',
                             (g.member['id'], values['channels'], values['pitch'], values['contact'], now_str()))
            audit(conn, None, 'submit', 'creator_applications', None, 'Creator application received')
            conn.commit()
            return redirect(url_for('public.creator_program', _anchor='application'), 303)
    if error:
        get_db().rollback()
    return render_template('creator_program.html', application=app, error=error, values=values), 400 if error else 200


def metrics(application_id):
    rows = get_db().execute('SELECT p.* FROM creator_payments p JOIN creator_codes c ON c.id=p.code_id WHERE c.application_id=?',
                            (application_id,)).fetchall()
    currencies = {}
    for row in rows:
        if row['status'] != 'completed':
            continue
        value = currencies.setdefault(row['currency'], {'orders': 0, 'sales': Decimal(0)})
        value['orders'] += 1
        value['sales'] += Decimal(row['amount'])
    return [{'currency': key, 'orders': value['orders'], 'sales': format(value['sales'], '.2f'),
             'estimate': format((value['sales'] * Decimal('.25')).quantize(Decimal('.01')), '.2f')}
            for key, value in sorted(currencies.items())]


@public_bp.route('/creator-dashboard', methods=['GET', 'POST'])
@creator_required
def creator_dashboard():
    if is_admin():
        return redirect(url_for('admin.creator_applications'))
    app = application_for_member()
    conn = get_db()
    error = ''
    if request.method == 'POST':
        check_csrf()
        _limited('creator-code-request', 5)
        conn.execute('BEGIN IMMEDIATE')
        code = request.form.get('code', '').strip()
        wallet = request.form.get('wallet_ref', '').strip()
        outstanding = conn.execute("SELECT 1 FROM creator_codes WHERE application_id=? AND status IN ('requested','active')", (app['id'],)).fetchone()
        if outstanding:
            error = 'You already have an active code or a request awaiting review. Open a priority ticket if you need a change.'
        elif not CODE_RE.fullmatch(code):
            error = 'Use 3–32 letters, numbers, underscores or hyphens for your code.'
        elif not re.fullmatch(r'[A-Za-z0-9_-]{3,128}', wallet):
            error = 'Enter the wallet reference shown in your Tebex Wallet. Do not enter bank details or a password.'
        elif request.form.get('code_agreement') != '1':
            error = 'Confirm the code terms and that this is your Tebex Wallet reference.'
        else:
            try:
                conn.execute('INSERT INTO creator_codes(application_id,code,wallet_ref,created_at) VALUES(?,?,?,?)',
                             (app['id'], code, wallet, now_str()))
                audit(conn, None, 'request', 'creator_codes', None, 'Creator code request received')
                conn.commit()
                return redirect(url_for('public.creator_dashboard', _anchor='creator-code'), 303)
            except sqlite3.IntegrityError:
                conn.rollback()
                error = 'That code is reserved. Choose another code.'
        if error:
            conn.rollback()
    codes = conn.execute('SELECT * FROM creator_codes WHERE application_id=? ORDER BY id DESC', (app['id'],)).fetchall()
    tickets = conn.execute('SELECT * FROM creator_tickets WHERE application_id=? ORDER BY updated_at DESC LIMIT 50', (app['id'],)).fetchall()
    return render_template('creator_dashboard.html', application=app, codes=codes, tickets=tickets,
                           metrics=metrics(app['id']), error=error,
                           metrics_connected=bool(current_app.config.get('TEBEX_WEBHOOK_SECRET'))), 400 if error else 200


@public_bp.route('/creator-dashboard/tickets', methods=['POST'])
@creator_required
def creator_ticket_new():
    check_csrf()
    if is_admin():
        abort(403)
    _limited('creator-ticket', 10)
    app = application_for_member()
    title, body = request.form.get('title', '').strip(), request.form.get('body', '').strip()
    if not 5 <= len(title) <= 120 or not 20 <= len(body) <= 5000:
        flash('Use a subject of 5–120 characters and a message of 20–5,000 characters.', 'error')
        return redirect(url_for('public.creator_dashboard', _anchor='priority-support'), 303)
    conn = get_db()
    stamp = now_str()
    row = conn.execute('INSERT INTO creator_tickets(application_id,title,created_at,updated_at) VALUES(?,?,?,?)',
                       (app['id'], title, stamp, stamp))
    ticket_id = row.lastrowid
    conn.execute('INSERT INTO creator_ticket_messages(ticket_id,author,body,created_at) VALUES(?,?,?,?)',
                 (ticket_id, 'creator', body, stamp))
    conn.commit()
    return redirect(url_for('public.creator_ticket', ticket_id=ticket_id), 303)


@public_bp.route('/creator-dashboard/tickets/<int:ticket_id>', methods=['GET', 'POST'])
@creator_required
def creator_ticket(ticket_id):
    conn = get_db()
    ticket = conn.execute('SELECT * FROM creator_tickets WHERE id=?', (ticket_id,)).fetchone() or abort(404)
    app = application_for_member()
    if not is_admin() and (not app or ticket['application_id'] != app['id']):
        abort(404)
    error = ''
    if request.method == 'POST':
        check_csrf()
        _limited('creator-ticket-reply', 30)
        body = request.form.get('body', '').strip()
        if not 1 <= len(body) <= 5000:
            error = 'Use a message of 1–5,000 characters.'
        else:
            stamp = now_str()
            conn.execute('INSERT INTO creator_ticket_messages(ticket_id,author,body,created_at) VALUES(?,?,?,?)',
                         (ticket_id, 'Aerborne team' if is_admin() else 'creator', body, stamp))
            state = request.form.get('status', 'open') if is_admin() else 'open'
            if state not in ('open', 'waiting', 'closed'):
                abort(400)
            conn.execute('UPDATE creator_tickets SET status=?,updated_at=? WHERE id=?', (state, stamp, ticket_id))
            conn.commit()
            return redirect(url_for('public.creator_ticket', ticket_id=ticket_id), 303)
    messages = conn.execute('SELECT * FROM creator_ticket_messages WHERE ticket_id=? ORDER BY id', (ticket_id,)).fetchall()
    return render_template('creator_ticket.html', ticket=ticket, messages=messages, error=error), 400 if error else 200


@admin_bp.route('/creator-program')
@auth.role_required('admin')
def creator_applications():
    conn = get_db()
    page = max(1, request.args.get('page', 1, type=int))
    rows = conn.execute('SELECT a.*,m.username FROM creator_applications a JOIN members m ON m.id=a.member_id ORDER BY a.id DESC LIMIT 40 OFFSET ?',
                         ((page - 1) * 40,)).fetchall()
    tickets = conn.execute("SELECT t.*,m.username FROM creator_tickets t JOIN creator_applications a ON a.id=t.application_id JOIN members m ON m.id=a.member_id WHERE t.status!='closed' ORDER BY t.updated_at DESC LIMIT 50").fetchall()
    return render_template('admin/creators.html', rows=rows, tickets=tickets, page=page,
                           metrics_connected=bool(current_app.config.get('TEBEX_WEBHOOK_SECRET')))


@admin_bp.route('/creator-program/<int:application_id>', methods=['GET', 'POST'])
@auth.role_required('admin')
def creator_application(application_id):
    conn = get_db()
    if request.method == 'POST':
        conn.execute('BEGIN IMMEDIATE')
    app = conn.execute('SELECT a.*,m.username,m.rank_id,m.status AS member_status FROM creator_applications a JOIN members m ON m.id=a.member_id WHERE a.id=?',
                        (application_id,)).fetchone() or abort(404)
    error = ''
    if request.method == 'POST':
        if request.form.get('action') == 'code':
            code_id = request.form.get('code_id', type=int)
            state = request.form.get('status', '')
            code = conn.execute('SELECT * FROM creator_codes WHERE id=? AND application_id=?', (code_id, app['id'])).fetchone() or abort(404)
            if state not in CODE_STATUSES:
                abort(400)
            if state == 'active' and (app['status'] != 'accepted' or app['member_status'] != 'active'
                                      or request.form.get('tebex_confirmed') != '1'):
                error = 'Accept the active member first, then confirm the code, wallet and 25%/10% settings in Tebex.'
            else:
                if state == 'active':
                    conn.execute("UPDATE creator_codes SET status='disabled' WHERE application_id=? AND status='active' AND id!=?", (app['id'], code_id))
                conn.execute('UPDATE creator_codes SET status=?,reviewed_at=?,reviewed_by=? WHERE id=?',
                             (state, now_str(), g.user['username'], code_id))
                audit(conn, g.user, 'review', 'creator_codes', code_id, 'Creator code status: ' + state)
                conn.commit()
        else:
            state = request.form.get('status', '')
            if state not in APPLICATION_STATUSES:
                abort(400)
            if state == 'accepted' and app['member_status'] != 'active':
                error = 'Only active website accounts can join the creator program.'
            else:
                rank = conn.execute("SELECT id FROM ranks WHERE slug='media'").fetchone()
                if state == 'accepted' and not rank:
                    conn.rollback()
                    abort(409)
                previous = app['previous_rank_id']
                if state == 'accepted' and app['status'] != 'accepted':
                    previous = app['rank_id']
                    old = conn.execute('SELECT staff FROM ranks WHERE id=?', (app['rank_id'],)).fetchone()
                    # Existing staff retain their staff display rank; membership still grants the dashboard.
                    if not old or not old['staff']:
                        conn.execute('UPDATE members SET rank_id=? WHERE id=?', (rank['id'], app['member_id']))
                elif state != 'accepted':
                    if rank and app['rank_id'] == rank['id']:
                        conn.execute('UPDATE members SET rank_id=? WHERE id=?', (previous, app['member_id']))
                    conn.execute("UPDATE creator_codes SET status='disabled' WHERE application_id=? AND status IN ('active','requested')", (app['id'],))
                conn.execute('UPDATE creator_applications SET status=?,previous_rank_id=?,reviewed_at=?,reviewed_by=? WHERE id=?',
                             (state, previous, now_str(), g.user['username'], app['id']))
                audit(conn, g.user, 'review', 'creator_applications', app['id'], 'Creator application status: ' + state)
                conn.commit()
        if not error:
            return redirect(url_for('admin.creator_application', application_id=app['id']), 303)
        conn.rollback()
    codes = conn.execute('SELECT * FROM creator_codes WHERE application_id=? ORDER BY id DESC', (app['id'],)).fetchall()
    tickets = conn.execute('SELECT * FROM creator_tickets WHERE application_id=? ORDER BY id DESC', (app['id'],)).fetchall()
    return render_template('admin/creator.html', application=app, codes=codes, tickets=tickets,
                           statuses=APPLICATION_STATUSES, metrics=metrics(app['id']), error=error), 400 if error else 200


def active_code(code):
    return get_db().execute("SELECT c.* FROM creator_codes c JOIN creator_applications a ON a.id=c.application_id JOIN members m ON m.id=a.member_id WHERE c.code=? COLLATE NOCASE AND c.status='active' AND a.status='accepted' AND m.status='active'", (code,)).fetchone()


def _event_time(value):
    # Normalise ISO8601 offsets before ordering events so delayed retries cannot resurrect refunds.
    if not isinstance(value, str):
        raise ValueError()
    match = re.fullmatch(r'(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(\.\d{1,6})?(Z|[+-]\d{2}:\d{2})', value)
    if not match:
        raise ValueError()
    stamp = datetime.datetime.strptime(match[1], '%Y-%m-%dT%H:%M:%S')
    if match[2]:
        stamp = stamp.replace(microsecond=int(match[2][1:].ljust(6, '0')))
    zone = match[3]
    if zone != 'Z':
        hours, minutes = int(zone[1:3]), int(zone[4:6])
        if hours > 23 or minutes > 59:
            raise ValueError()
        stamp -= datetime.timedelta(minutes=(hours * 60 + minutes) * (1 if zone[0] == '+' else -1))
    return stamp.strftime('%Y-%m-%d %H:%M:%S.%f')


@public_bp.route('/integrations/tebex/webhook', methods=['POST'])
def tebex_webhook():
    secret = current_app.config.get('TEBEX_WEBHOOK_SECRET')
    if not secret:
        abort(503)
    raw = request.stream.read(262145)
    if len(raw) > 262144:
        abort(413)
    expected = hmac.new(secret.encode(), hashlib.sha256(raw).hexdigest().encode('ascii'), hashlib.sha256).hexdigest()
    provided = request.headers.get('X-Signature', '')
    if not re.fullmatch(r'[a-fA-F0-9]{64}', provided) or not hmac.compare_digest(expected, provided.lower()):
        abort(403)
    try:
        event = json.loads(raw.decode('utf-8'))
        event_id, kind = event['id'], event['type']
        if not isinstance(event_id, str) or not 1 <= len(event_id) <= 100 or not isinstance(kind, str):
            raise ValueError()
        if kind == 'validation.webhook':
            return jsonify(id=event_id)
        if kind not in ('payment.completed', 'payment.refunded', 'payment.dispute.opened',
                        'payment.dispute.won', 'payment.dispute.lost', 'payment.dispute.closed'):
            return '', 204
        subject = event['subject']
        code = subject.get('creator_code')
        transaction = subject['transaction_id']
        if not isinstance(transaction, str) or not 1 <= len(transaction) <= 128:
            raise ValueError()
        event_at = _event_time(event['date'])
        price = subject['price']
        amount = Decimal(str(price['amount']))
        currency = price['currency']
        if not amount.is_finite() or amount < 0 or amount > Decimal('1000000000') or not re.fullmatch(r'[A-Z]{3}', currency):
            raise ValueError()
    except (KeyError, TypeError, ValueError, AttributeError, InvalidOperation, OverflowError):
        abort(400)
    conn = get_db()
    conn.execute('BEGIN IMMEDIATE')
    if conn.execute('SELECT 1 FROM tebex_webhook_events WHERE id=?', (event_id,)).fetchone():
        conn.rollback()
        return '', 204
    previous = conn.execute('SELECT * FROM creator_payments WHERE transaction_id=?', (transaction,)).fetchone()
    creator = conn.execute("SELECT id FROM creator_codes WHERE code=? COLLATE NOCASE AND (status='active' OR (status='disabled' AND reviewed_at IS NOT NULL))", (code,)).fetchone() if isinstance(code, str) else None
    code_id = previous['code_id'] if previous else creator['id'] if creator else None
    if code_id:
        status = 'completed' if kind == 'payment.completed' else 'refunded' if kind == 'payment.refunded' else 'disputed'
        if kind in ('payment.dispute.won', 'payment.dispute.closed'):
            status = 'completed' if (subject.get('status') or {}).get('id') == 1 else 'disputed'
        # Prefer a reversal at the same timestamp; only a newer event can restore a payment.
        newer = not previous or event_at > previous['event_at'] or (event_at == previous['event_at'] and previous['status'] == 'completed' and status != 'completed')
        if newer:
            conn.execute('INSERT OR REPLACE INTO creator_payments(transaction_id,code_id,currency,amount,status,event_at,received_at) VALUES(?,?,?,?,?,?,?)',
                         (transaction, code_id, currency, str(amount), status, event_at, now_str()))
    conn.execute('INSERT INTO tebex_webhook_events(id,received_at) VALUES(?,?)', (event_id, now_str()))
    conn.commit()
    return '', 204

