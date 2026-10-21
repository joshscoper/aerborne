"""Private team applications with a footer entry point and administrator review."""
import hmac
import re
import secrets
from urllib.parse import quote, urlsplit

from flask import abort, current_app, g, redirect, render_template, request, session, url_for

from . import auth
from .admin import bp as admin_bp
from .public import bp as public_bp
from .db import audit, get_db, now_str
from .security import allow, check_csrf, client_ip

ROLES = [
    ('helper', 'Helper', 'Welcome players, answer questions and help people find their feet.',
     'A new player is frustrated and cannot work out their next step. How would you help?'),
    ('moderator', 'Moderator', 'Help keep the game fair, friendly and welcoming.',
     'Two players give conflicting accounts of a rule violation. How would you investigate fairly?'),
    ('forum-moderator', 'Forum moderator', 'Keep discussions organised and help resolve reports.',
     'A useful forum discussion turns into a personal argument. How would you handle it?'),
    ('builder', 'Builder', 'Create islands, landmarks and places players want to explore.',
     'How would you plan and build a location that fits Aerborne’s world and works well for players?'),
    ('developer', 'Developer', 'Build reliable features, tools and fixes for the server or website.',
     'A new feature breaks for some players after release. How would you investigate and resolve it?'),
    ('artist', 'Artist', 'Give Aerborne its visual character through art, models and assets.',
     'How would you take an art brief from a first idea to a finished asset that fits Aerborne?'),
    ('writer', 'Writer', 'Shape lore, guides, announcements and stories from the skies.',
     'How would you turn a complex game feature into a clear guide for someone trying it for the first time?'),
    ('events', 'Events organiser', 'Bring players together with activities and community events.',
     'How would you plan a community event that both new and experienced players can enjoy?'),
    ('other', 'Another way to help', 'Tell us about a skill or contribution we have not listed.',
     'What would you like to contribute, and how would you approach your first project?'),
]
ROLE_NAMES = {key: name for key, name, _description, _prompt in ROLES}
STATUSES = [('new', 'New'), ('reviewing', 'Reviewing'), ('contacted', 'Contacted'),
            ('accepted', 'Accepted'), ('declined', 'Declined')]
FIELDS = {'name': 60, 'email': 254, 'discord': 80, 'minecraft_name': 16, 'role': 30,
          'timezone': 80, 'availability': 300, 'experience': 3000, 'motivation': 2000,
          'approach': 2000, 'portfolio': 2500}


def _form(values=None, errors=None, code=200):
    if not session.get('join_us_key'):
        session['join_us_key'] = secrets.token_urlsafe(32)
    response = current_app.make_response((render_template('join_us.html', roles=ROLES, values=values or {},
             errors=errors or {}, submission_key=session['join_us_key']), code))
    response.headers['Cache-Control'] = 'no-store'
    return response


@public_bp.route('/join-us', methods=['GET', 'POST'])
def join_us():
    if request.method == 'GET':
        return _form()
    check_csrf()
    if request.content_length and request.content_length > 50000:
        abort(413)
    key = request.form.get('submission_key', '')
    if key and hmac.compare_digest(key, session.get('join_us_last_key', '')):
        return redirect(url_for('public.join_us_thanks'), 303)
    if not key or not hmac.compare_digest(key, session.get('join_us_key', '')):
        return _form(errors={'form': 'This form expired. Please review your answers and submit again.'}, code=400)
    conn = get_db()
    permitted = allow(conn, 'team-application', client_ip(), 10, 3600)
    conn.commit()
    if not permitted:
        return _form(errors={'form': 'Too many attempts. Please try again in an hour or contact support.'}, code=429)
    values = {name: request.form.get(name, '').strip() for name in FIELDS}
    errors = {}
    if request.form.get('website'):
        return _form(errors={'form': 'We could not submit this application. Please try again.'}, code=400)
    for name, maximum in FIELDS.items():
        if len(values[name]) > maximum:
            errors[name] = 'Keep this answer to %d characters or fewer.' % maximum
            values[name] = values[name][:maximum]
    for name in ('name', 'email', 'role', 'timezone', 'availability', 'experience', 'motivation', 'approach'):
        if not values[name]:
            errors[name] = 'Please complete this field.'
    if not re.fullmatch(r'[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,63}', values['email']):
        errors['email'] = 'Enter an email address we can reply to.'
    if values['role'] not in ROLE_NAMES:
        errors['role'] = 'Choose a role from the list.'
    if values['minecraft_name'] and not re.fullmatch(r'[A-Za-z0-9_]{3,16}', values['minecraft_name']):
        errors['minecraft_name'] = 'Use your Java Edition username, or leave this blank.'
    links = [line.strip() for line in values['portfolio'].splitlines() if line.strip()]
    if len(links) > 5:
        errors['portfolio'] = 'Add up to five links, one per line.'
    for link in links:
        try:
            parsed = urlsplit(link)
            if parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password or any(c.isspace() for c in link):
                errors['portfolio'] = 'Use complete https:// links, one per line.'
        except ValueError:
            errors['portfolio'] = 'Check that each link is a valid https:// address.'
    if request.form.get('privacy_ack') != '1':
        errors['privacy_ack'] = 'Confirm how your application will be used before submitting.'
    if errors:
        return _form(values, errors, 400)
    values['portfolio'] = '\n'.join(links)
    reference = secrets.token_hex(6).upper()
    columns = list(FIELDS)
    conn.execute('INSERT OR IGNORE INTO team_applications (reference,submission_key,' + ','.join(columns) +
                 ',created_at) VALUES (' + ','.join('?' for _ in range(len(columns) + 3)) + ')',
                 [reference, key] + [values[name] for name in columns] + [now_str()])
    row = conn.execute('SELECT id,reference FROM team_applications WHERE submission_key=?', (key,)).fetchone()
    audit(conn, None, 'submit', 'team_applications', row['id'], 'Team application received: ' + row['reference'])
    conn.commit()
    session['join_us_reference'] = row['reference']
    session['join_us_last_key'] = key
    session.pop('join_us_key', None)
    return redirect(url_for('public.join_us_thanks'), 303)


@public_bp.route('/join-us/thanks')
def join_us_thanks():
    reference = session.get('join_us_reference') or abort(404)
    response = current_app.make_response(render_template('join_us_thanks.html', reference=reference))
    response.headers['Cache-Control'] = 'no-store'
    response.headers['X-Robots-Tag'] = 'noindex, nofollow'
    return response


@admin_bp.route('/team-applications')
@auth.role_required('admin')
def team_applications():
    status = request.args.get('status', 'new')
    if status not in dict(STATUSES) and status != 'all':
        abort(400)
    page = max(1, request.args.get('page', 1, type=int))
    conn = get_db()
    where, params = ('', []) if status == 'all' else (' WHERE status=?', [status])
    total = conn.execute('SELECT COUNT(*) FROM team_applications' + where, params).fetchone()[0]
    rows = conn.execute('SELECT id,reference,name,role,status,created_at FROM team_applications' + where +
                        ' ORDER BY id DESC LIMIT 40 OFFSET ?', params + [(page - 1) * 40]).fetchall()
    return render_template('admin/team_applications.html', rows=rows, statuses=STATUSES, selected=status,
                           role_names=ROLE_NAMES, page=page, total=total)


@admin_bp.route('/team-applications/<int:application_id>', methods=['GET', 'POST'])
@auth.role_required('admin')
def team_application(application_id):
    conn = get_db()
    application = conn.execute('SELECT * FROM team_applications WHERE id=?', (application_id,)).fetchone() or abort(404)
    if request.method == 'POST':
        if request.form.get('action') == 'delete':
            conn.execute('DELETE FROM team_applications WHERE id=?', (application_id,))
            audit(conn, g.user, 'delete', 'team_applications', application_id, 'Removed application ' + application['reference'])
            conn.commit()
            return redirect(url_for('admin.team_applications', status='all'), 303)
        status = request.form.get('status', '')
        notes = request.form.get('notes', '').strip()
        if status not in dict(STATUSES) or len(notes) > 5000:
            abort(400)
        conn.execute('UPDATE team_applications SET status=?,notes=?,reviewed_at=?,reviewed_by=? WHERE id=?',
                     (status, notes, now_str(), g.user['username'], application_id))
        audit(conn, g.user, 'review', 'team_applications', application_id,
              'Updated application ' + application['reference'] + ': ' + status)
        conn.commit()
        return redirect(url_for('admin.team_application', application_id=application_id), 303)
    prompt = next((p for key, _name, _description, p in ROLES if key == application['role']), 'Your approach')
    return render_template('admin/team_application.html', application=application, statuses=STATUSES,
                           role_names=ROLE_NAMES, prompt=prompt,
                           contact_href='mailto:' + quote(application['email'], safe='@'))
