"""Content added after the first release. Each revision runs once, on new and existing sites.

Updates never overwrite something staff have edited: guides and pages are only inserted when their
slug is free, and drafts are only replaced while they still hold the original placeholder text.
"""
from .content_types import get_type
from .db import audit, now_str, set_setting

LATEST = 31
C = "static:img/companions/"


def apply(conn):
    conn.execute("BEGIN IMMEDIATE")
    row = conn.execute("SELECT value FROM settings WHERE key = 'seed_rev'").fetchone()
    current = int(row["value"]) if row and str(row["value"]).isdigit() else 1
    if current >= LATEST:
        conn.rollback()
        return False
    if current < 2:
        _rev2(conn)
    if current < 3:
        _rev3(conn)
    if current < 4:
        _rev4(conn)
    if current < 5:
        _rev5(conn)
    if current < 6:
        _rev6(conn)
    if current < 7:
        _rev7(conn)
    if current < 8:
        _rev8(conn)
    if current < 9:
        _rev9(conn)
    if current < 10:
        _rev10(conn)
    if current < 11:
        _rev11(conn)
    if current < 12:
        _rev12(conn)
    if current < 13:
        _rev13(conn)
    if current < 14:
        _rev14(conn)
    if current < 15:
        _rev15(conn)
    if current < 16:
        _rev16(conn)
    if current < 17:
        _rev17(conn)
    if current < 18:
        _rev18(conn)
    if current < 19:
        conn.execute("UPDATE ranks SET frame='owner' WHERE slug='owner' AND frame='admin'")
        from .policies import PAGES
        for slug in ('privacy', 'store-terms'):
            conn.execute("UPDATE pages SET body=?,updated_at=? WHERE slug=? AND updated_by='starter content'",
                         (PAGES[slug][2].strip(), now_str(), slug))
    if current < 20:
        _rev20(conn)
    if current < 21:
        from .policies import PAGES
        for slug in ('privacy', 'store-terms'):
            conn.execute("UPDATE pages SET body=?,updated_at=? WHERE slug=? AND updated_by='starter content'",
                         (PAGES[slug][2].strip(), now_str(), slug))
    if current < 22:
        _rev22(conn)
    if current < 23:
        _rev23(conn)
    if current < 24:
        _rev24(conn)
    if current < 25:
        _rev25(conn)
    if current < 26:
        _rev26(conn)
    if current < 27:
        _rev27(conn)
    if current < 28:
        _rev28(conn)
    if current < 29:
        _rev29(conn)
    if current < 30:
        _rev30(conn)
    if current < 31:
        _rev31(conn)
    set_setting(conn, "seed_rev", str(LATEST))
    audit(conn, None, "seed", "", None, "Added content revision %d" % LATEST)
    conn.commit()
    return True


LILYPIP = dict(
    name="Lilypip", slug="lilypip", species="Lily frog",
    summary="A cheerful pond frog who wears a lily pad for a hat and a clover necklace. "
            "Lilypip hops after you and leaves little water drops wherever it lands.",
    temperament="Cheerful and a little splashy",
    acquisition="From the store once it opens. Summon it with /companion lilypip.", access="purchased",
    image="static:img/companions/lilypip-720.webp",
    image_alt="Lilypip, a green block-built frog with big shiny eyes, a lily-pad hat and a clover necklace",
    concept_art=1, availability="upcoming", sort_order=6)
LILYPIP_PRODUCT = dict(
    name="Lilypip companion", slug="lilypip-companion", category="companions",
    summary="A cheerful lily frog that hops along beside you.",
    includes="Lilypip, who follows you around\nSummon it from /cosmetics or /companion",
    entitlement="permanent", platforms="Java Edition", image="static:img/companions/lilypip-360.webp",
    image_alt="Lilypip the lily frog", concept_art=1, availability="upcoming",
    game_product="cosmetic.companion.lilypip", sort_order=26)


# Bodies shipped before party expeditions and the season campaign (revision 20). A guide or region
# is refreshed only while it still holds exactly this text and nobody on staff has edited it.
PRE_EXPEDITION_BODIES = {
    'beyond-your-island': 'd9d851dee5415192a8bfec957025e1612bfaece768570b95d2cd147c2f884ca4',
    'world-overview': 'ae6a2349ab3f62818895095e139119d255f0c5154d9af22405b020f6d0121048',
    'parties': 'a9b5cc6514c6a866ec7df388013672129cd6ee6f11a5eb51ef6543bdd0d913d6',
    'commands': '98afc40e0a823dbb483113f8b633d87984568d0ca55fe218bbf1f7cffb703d1b',
}
PRE_EXPEDITION_REGION = '7300c627ef7a7e1da8b6b0df09e48d8df5dfe90ad99223a561573d4f3604bf9a'
EXPEDITION_GUIDES = ('expeditions', 'season-campaign')


def _rev20(conn):
    import hashlib
    from .reference_content import GUIDES as REVIEWED, REGIONS

    def fingerprint(text):
        return hashlib.sha256((text or '').encode('utf-8')).hexdigest()

    for guide in REVIEWED:
        if guide['slug'] in EXPEDITION_GUIDES:
            _insert(conn, 'guides', dict(guide, sort_order=90))
            continue
        old = PRE_EXPEDITION_BODIES.get(guide['slug'])
        row = conn.execute('SELECT * FROM guides WHERE slug = ?', (guide['slug'],)).fetchone() if old else None
        if row and row['updated_by'] == 'starter content' and fingerprint(row['body']) == old:
            conn.execute('UPDATE guides SET body = ?, related = ?, updated_at = ? WHERE id = ?',
                         (guide['body'], guide['related'], now_str(), row['id']))
    row = conn.execute("SELECT * FROM regions WHERE slug = 'expeditions'").fetchone()
    if row and row['updated_by'] == 'starter content' and fingerprint(row['body']) == PRE_EXPEDITION_REGION:
        changes = dict(REGIONS['expeditions'], updated_at=now_str())
        cols = list(changes)
        conn.execute('UPDATE regions SET ' + ', '.join(column + ' = ?' for column in cols) + ' WHERE id = ?',
                     [changes[column] for column in cols] + [row['id']])


# The parties guide as revision 20 shipped it, before the party menu, open parties and the finder.
REV20_PARTIES = ('ad5e0421e9a5bc858d2ec4b70f73de452341b550cfb05521a4ba96431f7db1d0',
                 'Group up without sharing your island or storage permissions.')


def _rev22(conn):
    import hashlib
    from .reference_content import GUIDES as REVIEWED
    guide = next(g for g in REVIEWED if g['slug'] == 'parties')
    row = conn.execute("SELECT * FROM guides WHERE slug = 'parties'").fetchone()
    if (row and row['updated_by'] == 'starter content'
            and hashlib.sha256((row['body'] or '').encode('utf-8')).hexdigest() == REV20_PARTIES[0]):
        summary = guide['summary'] if row['summary'] == REV20_PARTIES[1] else row['summary']
        conn.execute('UPDATE guides SET body = ?, summary = ?, updated_at = ? WHERE id = ?',
                     (guide['body'], summary, now_str(), row['id']))


# The expeditions guide and region as revision 20 shipped them, before puzzles, campfires and the
# first campaign's expeditions.
REV20_EXPEDITIONS_GUIDE = '330894d81a2f3ccddbe683e73920a60ba5d86f0e54e26fdd836f79dbc5fc875b'
REV20_EXPEDITIONS_POI = 'The Hollow Barrow, a crypt beneath the old hills\nCinder Keep, a fortress in the Nether'


def _rev23(conn):
    import hashlib
    from .reference_content import GUIDES as REVIEWED, REGIONS
    guide = next(g for g in REVIEWED if g['slug'] == 'expeditions')
    row = conn.execute("SELECT * FROM guides WHERE slug = 'expeditions'").fetchone()
    if (row and row['updated_by'] == 'starter content'
            and hashlib.sha256((row['body'] or '').encode('utf-8')).hexdigest() == REV20_EXPEDITIONS_GUIDE):
        conn.execute('UPDATE guides SET body = ?, updated_at = ? WHERE id = ?', (guide['body'], now_str(), row['id']))
    region = conn.execute("SELECT * FROM regions WHERE slug = 'expeditions'").fetchone()
    if region and region['updated_by'] == 'starter content' and (region['points_of_interest'] or '') == REV20_EXPEDITIONS_POI:
        fresh = REGIONS['expeditions']
        conn.execute('UPDATE regions SET points_of_interest = ?, activities = ?, updated_at = ? WHERE id = ?',
                     (fresh['points_of_interest'], fresh['activities'], now_str(), region['id']))


# The expeditions and parties guides and the expeditions region as revision 23 shipped them, before
# keystones and the Cinder Keep raid.
REV23_GUIDES = {
    'expeditions': ('232491f023637baecaad91c555207668d8f029f015fa4ac490f13b10a96e46cf',
                    'Form a party of up to five, clear the rooms, beat the boss and leave with loot.'),
    'parties': ('21a199419ef97e99f9bfb02de0038b85e5bf84aaf14864561681bb4571e7b89d', None),
}
REV23_EXPEDITIONS_POI = ('First Flight, the tutorial across the sky islands\nThe Hollow Barrow, a crypt beneath the old hills\n'
                         'Thornwood Canopy, treetops at night\nThe Sunken Aqueduct, flooded waterworks\n'
                         'Gloomforge Mines, deep galleries and their foremen\nThe Hall of the Three Wardens, a citadel in the sky\n'
                         'Cinder Keep, a fortress in the Nether')


def _rev24(conn):
    import hashlib
    from .reference_content import GUIDES as REVIEWED, REGIONS
    for slug, (fingerprint, old_summary) in REV23_GUIDES.items():
        guide = next(g for g in REVIEWED if g['slug'] == slug)
        row = conn.execute('SELECT * FROM guides WHERE slug = ?', (slug,)).fetchone()
        if not (row and row['updated_by'] == 'starter content'
                and hashlib.sha256((row['body'] or '').encode('utf-8')).hexdigest() == fingerprint):
            continue
        summary = guide['summary'] if old_summary is not None and row['summary'] == old_summary else row['summary']
        conn.execute('UPDATE guides SET body = ?, summary = ?, updated_at = ? WHERE id = ?', (guide['body'], summary, now_str(), row['id']))
    region = conn.execute("SELECT * FROM regions WHERE slug = 'expeditions'").fetchone()
    if region and region['updated_by'] == 'starter content' and (region['points_of_interest'] or '') == REV23_EXPEDITIONS_POI:
        fresh = REGIONS['expeditions']
        conn.execute('UPDATE regions SET points_of_interest = ?, activities = ?, updated_at = ? WHERE id = ?',
                     (fresh['points_of_interest'], fresh['activities'], now_str(), region['id']))


# The expeditions and skills guides as revision 24 shipped them, before expedition loot and skill lamps.
REV24_GUIDES = {
    'expeditions': '81bc8e537564b7ab194bfe699aa04d29f3b9f8b4b100eead0db6ac783d4c2f63',
    'skills-and-talents': '55fdca943373ab9c1897e66e2f0cd43710c2d2823b2cb219c5194c658a3b6a10',
}


def _rev25(conn):
    import hashlib
    from .reference_content import GUIDES as REVIEWED
    for slug, fingerprint in REV24_GUIDES.items():
        guide = next(g for g in REVIEWED if g['slug'] == slug)
        row = conn.execute('SELECT * FROM guides WHERE slug = ?', (slug,)).fetchone()
        if (row and row['updated_by'] == 'starter content'
                and hashlib.sha256((row['body'] or '').encode('utf-8')).hexdigest() == fingerprint):
            conn.execute('UPDATE guides SET body = ?, updated_at = ? WHERE id = ?', (guide['body'], now_str(), row['id']))


# The gemstone ladder (Rough to Perfect) and typed sockets: untouched copies of these guides, as the
# starter seed or the reviewed Handbook last wrote them, take the new text.
REV26_GUIDES = {
    'first-gear': ('e5854642c515885317f8edaeec7ac2aa7425993a6871e98db1ea14890ebc3f8e',
                   '7d13d2c345a4f760e4f562f783b677fde60087377780c9b0fb6d1e7da906927a'),
    'gear-and-sockets': ('42861bc1b9e44f97f89ffa82ac6de7a5fcffb0b0ecdbd138f598987416a96425',
                         'fe0a8d408eda64f54a9c3190d72856efee5fb6d62bf5f603dee91b8183893399'),
    'gem-merging': ('b0aeadeceea6675f0c8fd3a838b2ec30d51ab28167e487b44af7916f0da48185',),
    'aspects-and-research': ('3a4d7c9555ff2a51bc30ef366ac49c2a7207d7de5f9d2288b9378f3adb13eadf',),
}


def _rev26(conn):
    import hashlib
    from .reference_content import GUIDES as REVIEWED
    for slug, fingerprints in REV26_GUIDES.items():
        guide = next(g for g in REVIEWED if g['slug'] == slug)
        row = conn.execute('SELECT * FROM guides WHERE slug = ?', (slug,)).fetchone()
        if (row and row['updated_by'] == 'starter content'
                and hashlib.sha256((row['body'] or '').encode('utf-8')).hexdigest() in fingerprints):
            conn.execute('UPDATE guides SET body = ?, updated_at = ? WHERE id = ?', (guide['body'], now_str(), row['id']))


# Normal and Heroic dropped lives, and expeditions gained packs and threat.
REV27_GUIDES = {'expeditions': 'f6d82a1442f8f3976bc4ddbf919e1a00364644cdc6c097cd3d47068534681718',
                'beyond-your-island': 'b61d00e4e33274910bf5cc11bfacf831bac7dacdec608957353b0d7b083eff34'}
REV27_REGION = '3a682459448551a2e6532fa62b3856d5d48ae10ba96c7f0605eee7ee61a4790d'


def _rev27(conn):
    import hashlib
    from .reference_content import GUIDES as REVIEWED, REGIONS

    def fingerprint(text):
        return hashlib.sha256((text or '').encode('utf-8')).hexdigest()

    for slug, old in REV27_GUIDES.items():
        guide = next(g for g in REVIEWED if g['slug'] == slug)
        row = conn.execute('SELECT * FROM guides WHERE slug = ?', (slug,)).fetchone()
        if row and row['updated_by'] == 'starter content' and fingerprint(row['body']) == old:
            conn.execute('UPDATE guides SET body = ?, updated_at = ? WHERE id = ?', (guide['body'], now_str(), row['id']))
    row = conn.execute("SELECT * FROM regions WHERE slug = 'expeditions'").fetchone()
    if row and row['updated_by'] == 'starter content' and fingerprint(row['body']) == REV27_REGION:
        conn.execute('UPDATE regions SET body = ?, updated_at = ? WHERE id = ?',
                     (REGIONS['expeditions']['body'], now_str(), row['id']))


REV28_GUIDES = {'expeditions': '6a0aeb5a72a257a26d5d3882a6452ded34b8160e41742d1fcde3f28e90bccdb8',
                'skills-and-talents': '45af8305e465faf974cbc81e77abc82c4250466da8ded425139b168d5a79b80d'}


def _rev28(conn):
    import hashlib
    from .reference_content import GUIDES as REVIEWED

    for slug, old in REV28_GUIDES.items():
        guide = next(g for g in REVIEWED if g['slug'] == slug)
        row = conn.execute('SELECT * FROM guides WHERE slug = ?', (slug,)).fetchone()
        if row and row['updated_by'] == 'starter content' and hashlib.sha256((row['body'] or '').encode('utf-8')).hexdigest() == old:
            conn.execute('UPDATE guides SET body = ?, updated_at = ? WHERE id = ?', (guide['body'], now_str(), row['id']))


REV29_OLD_BOTTLES = ('Right-click an **experience bottle** to choose a skill: the bottle gives that skill 10% of the XP it needs for its '
                     'next level. **Greater** bottles give 25% and **Grand** bottles 60%; they come from expedition chests.')
REV29_BOTTLES = ('Experience bottles hold what someone else already learned. Right-click a **Bottled Lesson** to uncork it and choose a '
                 'skill: that skill gains 10% of the XP it needs for its next level. A **Bottled Lifetime** gives 25% and a '
                 '**Bottled Legend** 60%; they come from expedition chests.')


def _rev29(conn):
    # Sites hold older versions of this guide, so only the sentence is swapped, and only where staff haven't edited it.
    row = conn.execute("SELECT * FROM guides WHERE slug = 'skills-and-talents'").fetchone()
    if row and row['updated_by'] == 'starter content' and REV29_OLD_BOTTLES in (row['body'] or ''):
        conn.execute('UPDATE guides SET body = ?, updated_at = ? WHERE id = ?',
                     (row['body'].replace(REV29_OLD_BOTTLES, REV29_BOTTLES), now_str(), row['id']))


REV30_LINES = (
    ('expeditions', '- **Boss doors seal.** A boss door can stay barred until the packs guarding it are dead, and it shuts behind you during the fight.\n',
     '- **Boss rooms join in.** Fire can run down the floor, chandeliers can fall, and candles and braziers can need you to right-click them mid-fight. Watch the room as well as the boss.\n'),
    ('expeditions', "- **Bosses can drop a rare charm.** Right-click it to bind a companion you can't buy, or trade it to someone who wants it more.\n",
     "- **Bosses leave a chest.** Everyone has their own share: materials, a good chance at set gear, and sometimes one of that boss's own items.\n"),
)
REV30_ABILITIES = ('## Item abilities\nRare and better gear can do more than carry stats. Its tooltip names each ability and when it works: '
                   '**On Hit** has a chance to go off when you strike, **Right Click** is yours to use on a cooldown, and **Equipped** works for '
                   "as long as you wear or hold the item. Sealed gear does nothing until it's identified.\n\n")


def _rev30(conn):
    # As with rev 29, new lines go in beside what's there, and only where staff haven't edited the guide.
    for slug, after, line in REV30_LINES:
        row = conn.execute('SELECT * FROM guides WHERE slug = ?', (slug,)).fetchone()
        if row and row['updated_by'] == 'starter content' and after in (row['body'] or '') and line not in row['body']:
            conn.execute('UPDATE guides SET body = ?, updated_at = ? WHERE id = ?', (row['body'].replace(after, after + line), now_str(), row['id']))
    row = conn.execute("SELECT * FROM guides WHERE slug = 'gear-and-sockets'").fetchone()
    if row and row['updated_by'] == 'starter content' and '## Use Forge & Sockets\n' in (row['body'] or '') and '## Item abilities' not in row['body']:
        conn.execute('UPDATE guides SET body = ?, updated_at = ? WHERE id = ?',
                     (row['body'].replace('## Use Forge & Sockets\n', REV30_ABILITIES + '## Use Forge & Sockets\n'), now_str(), row['id']))


def _product(name, slug, category, summary, includes, image, alt, game_product, order, entitlement="permanent"):
    return dict(name=name, slug=slug, category=category, summary=summary, includes=includes, image=image, image_alt=alt,
                entitlement=entitlement, platforms="Java Edition", concept_art=0, availability="upcoming",
                game_product=game_product, sort_order=order)


# The cosmetics suite and XP boosters. The art is rendered from the game by Website/tools/store_art.py.
REV31_PRODUCTS = [
    _product("XP Booster", "xp-booster", "xp-boosters",
             "+50% XP for everyone online for an hour, with your name on the thanks.",
             "One server-wide XP booster\n+50% skill and adventurer XP for everyone online for an hour\n"
             "Use it with /booster use whenever you like\nAnother booster used while one runs adds an hour",
             "static:img/store/xp-booster-320.webp", "A golden lightning bolt in a glowing ring, marked +50% XP",
             "booster.xp", 40, entitlement="one-time"),
] + [
    _product(name + " companion", slug + "-companion", "companions", summary,
             name + ", who follows you around\nSummon it from /cosmetics or /companion",
             C + slug + "-360.webp", alt, "cosmetic.companion." + slug, order)
    for order, (name, slug, summary, alt) in enumerate([
        ("Glimmerbee", "glimmerbee", "A lantern bee with a glowing tail that hovers at your shoulder.", "Glimmerbee, a block-built bee with a glowing tail"),
        ("Bramblehog", "bramblehog", "A hedgehog wearing a bramble, berries and all.", "Bramblehog, a block-built hedgehog with berries on its back"),
        ("Driftfin", "driftfin", "A sky koi that swims through the air beside you.", "Driftfin, a white and orange block-built koi"),
    ], start=41)
] + [
    _product(name + " trail", slug + "-trail", "trails", summary, name + " trail effect",
             "static:img/kit/trails/" + slug + "-320.webp", "Preview of the " + name + " trail", "cosmetic.trail." + slug, order)
    for order, (name, slug, summary) in enumerate([
        ("Snowdrift", "snowdrift", "A soft fall of snow wherever you go."),
        ("Ember Wake", "ember-wake", "A wake of embers off your heels."),
        ("Glowspore", "glowspore", "Glowing spores that drift up behind you."),
    ], start=44)
] + [
    _product(name + " title", slug + "-title", "titles", "[" + name + "] before your name in chat and the tab list.",
             "The " + name + " title\nWear it with /cosmetics title " + slug,
             "static:img/store/titles/" + slug + "-320.webp", "The title " + name + " on a nameplate", "cosmetic.title." + slug, order)
    for order, (name, slug) in enumerate([("Skyborne", "skyborne"), ("Stormchaser", "stormchaser"), ("Starlit", "starlit"), ("Windrider", "windrider")], start=47)
] + [
    _product(name + " aura", slug + "-aura", "auras", summary,
             "The " + name + " aura\nShows while you stand still; your trail takes over when you move\nWear it with /cosmetics aura " + slug,
             "static:img/store/auras/" + slug + "-320.webp", "Preview of the " + name + " aura round a player", "cosmetic.aura." + slug, order)
    for order, (name, slug, summary) in enumerate([
        ("Glimmer Halo", "glimmer-halo", "A faint halo of light over your head."),
        ("Petal Orbit", "petal-orbit", "Petals that circle you slowly."),
        ("Snow Globe", "snow-globe", "Your own little flurry."),
    ], start=51)
] + [
    _product(name + " finisher", slug + "-finisher", "finishers", summary,
             "The " + name + " finisher\nGoes off where your kills fall\nWear it with /cosmetics finisher " + slug,
             "static:img/store/finishers/" + slug + "-320.webp", "Preview of the " + name + " finisher", "cosmetic.finisher." + slug, order)
    for order, (name, slug, summary) in enumerate([
        ("Starburst", "starburst", "A burst of sparks where your foe falls."),
        ("Confetti Pop", "confetti-pop", "A celebration, whether they wanted one or not."),
        ("Soul Release", "soul-release", "The fallen rise as a column of soul fire."),
    ], start=54)
]


def _rev31(conn):
    # New items go in only where their slug is free, as upcoming until Tebex sync links each to its package.
    for product in REV31_PRODUCTS:
        _insert(conn, "products", product)


def _rev18(conn):
    from .policies import PAGES, save_terms_version
    # Preserve the previous public Terms before refreshing unedited starter policies.
    old_terms = conn.execute("SELECT * FROM pages WHERE slug='terms'").fetchone()
    if old_terms:
        save_terms_version(conn, old_terms)
    for slug, (title, summary, body) in PAGES.items():
        row = conn.execute('SELECT * FROM pages WHERE slug=?', (slug,)).fetchone()
        if not row:
            _insert(conn, 'pages', dict(slug=slug, title=title, summary=summary, body=body.strip(),
                    section='legal' if slug in ('terms', 'privacy', 'store-terms') else 'support'))
        elif row['updated_by'] == 'starter content':
            conn.execute('UPDATE pages SET title=?,summary=?,body=?,updated_at=? WHERE id=?',
                         (title, summary, body.strip(), now_str(), row['id']))


def _rev17(conn):
    # Count historical invitations, including revoked awards, before enforcing the allowance.
    conn.execute("UPDATE badges SET award_policy='firstlight-sigil', creator_only=1 WHERE slug='firstlight-sigil' OR icon='badge-firstlight-sigil'")
    names = {'Firstlight Sigil'} | {r['name'] for r in conn.execute("SELECT name FROM badges WHERE award_policy='firstlight-sigil'")}
    admins = {r['username'].lower(): r['id'] for r in conn.execute('SELECT id,username FROM users')}
    for row in conn.execute("SELECT * FROM audit_log WHERE action='award' AND entity='members'").fetchall():
        if any(row['summary'].startswith('Awarded the ' + name + suffix)
               for name in names for suffix in (' to ', ' badge to ')):
            admin_id = row['user_id'] or admins.get(row['username'].lower())
            if admin_id:
                conn.execute('INSERT OR IGNORE INTO firstlight_awards (source_key,admin_user_id,member_id,awarded_at) VALUES (?,?,?,?)',
                             ('audit:%d' % row['id'], admin_id, row['entity_id'], row['created_at']))
    for row in conn.execute("SELECT mb.* FROM member_badges mb JOIN badges b ON b.id=mb.badge_id WHERE b.award_policy='firstlight-sigil'").fetchall():
        admin_id = admins.get(row['awarded_by'].lower())
        if admin_id and not conn.execute('SELECT 1 FROM firstlight_awards WHERE admin_user_id=? AND member_id=? AND awarded_at=?',
                                         (admin_id, row['member_id'], row['awarded_at'])).fetchone():
            conn.execute('INSERT OR IGNORE INTO firstlight_awards (source_key,admin_user_id,member_id,awarded_at) VALUES (?,?,?,?)',
                         ('legacy:%d:%d' % (row['badge_id'], row['member_id']), admin_id, row['member_id'], row['awarded_at']))
    row = conn.execute("SELECT id,body FROM pages WHERE slug='privacy' AND updated_by='starter content'").fetchone()
    if row and '## Team applications' not in row['body']:
        body = row['body'] + '\n\n## Team applications\n\nIf you apply to join the team, we store your contact details, role interest, availability, answers and optional work links for administrator review. Applications are private and are used to assess your application and contact you about it. Contact [support@aerborne.net](mailto:support@aerborne.net) to request a copy or removal.\n'
        conn.execute('UPDATE pages SET body=?, updated_at=? WHERE id=?', (body, now_str(), row['id']))


def _rev16(conn):
    from .badges import FIRSTLIGHT
    # Register the award, never recipients; preserve any existing badge and award history.
    fields = list(FIRSTLIGHT)
    conn.execute('INSERT OR IGNORE INTO badges (' + ','.join(fields) + ',created_at) VALUES (' +
                 ','.join('?' for _ in fields) + ',?)', [FIRSTLIGHT[key] for key in fields] + [now_str()])


def _rev15(conn):
    changes = {
        'privacy': (
            '**Tebex** will handle payments when the store opens. Its privacy policy applies when you visit checkout. This website does not collect card details.',
            '**Tebex** provides store checkout. When you start checkout, we send your Minecraft username, selected packages and IP address to Tebex to prepare your order. Its privacy policy applies at checkout. You enter payment details on Tebex; this website does not collect card details.'),
        'store-terms': (
            'When sales open, checkout will take place on **Tebex**.',
            'Checkout takes place on **Tebex** for available items. Choose the Minecraft account in your cart before continuing to payment.'),
    }
    for slug, (before, after) in changes.items():
        row = conn.execute('SELECT id, body FROM pages WHERE slug=?', (slug,)).fetchone()
        if row and before in row['body']:
            conn.execute('UPDATE pages SET body=?, updated_at=? WHERE id=?',
                         (row['body'].replace(before, after), now_str(), row['id']))


def _rev14(conn):
    # Lilypip, the frog companion added to the server's wardrobe. Inserted only when the slug is free.
    _insert(conn, "companions", LILYPIP)
    _insert(conn, "products", LILYPIP_PRODUCT)


def _rev13(conn):
    # Replace the original store tiles only; keep staff artwork and all product settings.
    for rank in ('skyfarer', 'cloudkeeper', 'starweaver'):
        conn.execute("UPDATE products SET image = ?, concept_art = 0, "
                     "image_alt = CASE WHEN image_alt = ? THEN ? ELSE image_alt END "
                     "WHERE slug = ? AND image = ?",
                     ('static:img/store/ranks/%s-256.webp' % rank, 'Concept art for ' + rank.title(),
                      rank.title() + ' rank emblem', rank,
                      'static:img/kit/ranks/%s-320.webp' % rank))


def _rev12(conn):
    import hashlib
    from .guide_revision import ORIGINAL_BODIES
    from .reference_content import GUIDES as REVIEWED
    for guide in REVIEWED:
        fingerprint = ORIGINAL_BODIES.get(guide['slug'])
        if not fingerprint:
            continue
        row = conn.execute('SELECT * FROM guides WHERE slug = ?', (guide['slug'],)).fetchone()
        if not row or row['updated_by'] != 'starter content':
            continue
        if any((row[key] or '') != (guide.get(key) or '')
               for key in ('title', 'summary', 'category', 'related', 'availability')):
            continue
        if hashlib.sha256((row['body'] or '').encode('utf-8')).hexdigest() == fingerprint:
            conn.execute('UPDATE guides SET body = ?, updated_at = ? WHERE id = ?',
                         (guide['body'], now_str(), row['id']))


def _rev7(conn):
    # Raise the original allowance while retaining custom limits (including rewards disabled).
    conn.execute("UPDATE settings SET value = '1000' WHERE key = 'arcade_daily_cap' AND value = '200'")


def _rev9(conn):
    # Refresh only the original welcome copy, preserving staff and direct database edits.
    from .seed import NEWS
    row = conn.execute("SELECT id, summary, body FROM news WHERE slug = 'welcome' AND updated_by = 'starter content'").fetchone()
    if not row:
        return
    original = {
        "summary": "A home for guides, news and the Arcane Codex while Aerborne is in private testing.",
        "body": """Aerborne now has a proper home on the web.

- The **Game Guide** walks new players from their first island to their first enchantment.
- The **Arcane Codex** lists every enchantment that's public knowledge. Hidden recipes stay hidden;
  discovering them is part of the game.
- **Companions** shows the pets we're designing. They're concept art for now, and we'll say so
  until each one is in the game.

Aerborne is still in private testing. We'll post here when that changes.
""",
    }
    revised = next(item for item in NEWS if item['slug'] == 'welcome')
    for field in ('summary', 'body'):
        if row[field] == original[field]:
            conn.execute("UPDATE news SET %s = ?, updated_at = ? WHERE id = ?" % field,
                         (revised[field], now_str(), row['id']))


STAFF_BADGES = [
    ("bug-hunter", "Bug hunter", "Found and reported a bug that got fixed.", "forum-bug-reports", "mint"),
    ("event-winner", "Event winner", "Won an official Aerborne event.", "gem-fortune", "gold"),
    ("master-builder", "Master builder", "Built something the whole server talked about.", "forum-island-showcase", "sky"),
    ("helping-hand", "Helping hand", "Went out of their way to help other players.", "gem-vitality", "peach"),
    ("idea-maker", "Idea maker", "Suggested something that made it into the game.", "forum-suggestions", "lavender"),
    ("beta-tester", "Beta tester", "Tested Aerborne before it opened.", "gem-celerity", "mint"),
]


def _rev8(conn):
    # Starter badges staff can hand out; staff can rename or delete them.
    for slug, name, description, icon, tone in STAFF_BADGES:
        conn.execute("INSERT OR IGNORE INTO badges (slug, name, description, icon, tone, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                     (slug, name, description, icon, tone, now_str()))


PACK_STAFF_BADGES = [
    ('verified-helper', 'Verified helper', 'Recognised by staff for reliable help and answers.', 'sky'),
    ('event-winner', 'Event winner', 'Won an official Aerborne event.', 'gold'),
    ('bug-hunter', 'Bug hunter', 'Found and reported a bug that got fixed.', 'mint'),
    ('guide-writer', 'Guide writer', 'Wrote a helpful guide for the Aerborne community.', 'lavender'),
    ('community-helper', 'Community helper', 'Went out of their way to help other players.', 'peach'),
    ('lore-seeker', 'Lore seeker', 'Recognised by staff for exploring Aerborne lore and discoveries.', 'lavender'),
    ('master-trader', 'Master trader', 'Recognised by staff for fair and helpful community trading.', 'gold'),
    ('island-builder', 'Island builder', 'Built something the whole server talked about.', 'sky'),
    ('sky-explorer', 'Sky explorer', 'Recognised by staff for exploration above the clouds.', 'mint'),
]


def _rev11(conn):
    from .badges import LEGACY_ARTWORK
    # Change artwork in place. IDs and member_badges rows (including award notes) stay intact.
    for old, artwork in LEGACY_ARTWORK.items():
        conn.execute('UPDATE badges SET icon = ? WHERE icon = ?', ('badge-' + artwork, old))
    aliases = {'community-helper': ('helping-hand', 'Helping hand'),
               'island-builder': ('master-builder', 'Master builder')}
    for slug, name, description, tone in PACK_STAFF_BADGES:
        existing = conn.execute('SELECT id FROM badges WHERE slug = ?', (slug,)).fetchone()
        if existing is None and slug in aliases:
            old_slug, old_name = aliases[slug]
            legacy = conn.execute('SELECT id, name FROM badges WHERE slug = ?', (old_slug,)).fetchone()
            if legacy:
                conn.execute('UPDATE badges SET slug = ?, name = ? WHERE id = ?',
                             (slug, name if legacy['name'] == old_name else legacy['name'], legacy['id']))
                existing = legacy
        if existing:
            conn.execute('UPDATE badges SET icon = ? WHERE id = ?', ('badge-' + slug, existing['id']))
        else:
            conn.execute('INSERT INTO badges (slug, name, description, icon, tone, created_at) VALUES (?, ?, ?, ?, ?, ?)',
                         (slug, name, description, 'badge-' + slug, tone, now_str()))


# Revision 10: the server now has companions, trails and supporter perks (nerdsmp-paper cosmetic package).
GAME_PRODUCTS = {
    "skyfarer": "cosmetic.rank.skyfarer", "cloudkeeper": "cosmetic.rank.cloudkeeper", "starweaver": "cosmetic.rank.starweaver",
    "cloudberry": "cosmetic.bundle.cloudberry", "blooming-skies": "cosmetic.bundle.blooming-skies",
    "celestial-dreams": "cosmetic.bundle.celestial-dreams",
}
COMPANION_SLUGS = ("pufflet", "kitekit", "mossbun", "cindlet", "moonmoth", "starwhisk")
TRAIL_SLUGS = ("cloudstep", "petal-skip", "firefly-drift", "rune-ripple", "bubblehop", "comet-tail", "dandelion-drift", "stardust-waltz")
STORE_INCLUDES = {
    "skyfarer": ("Skyfarer badge\nName colour palette",
                 "Skyfarer badge in chat, the tab list and above your head\nName colour palette (3 colours)"),
    "cloudkeeper": ("Cloudkeeper badge\nName colour palette\nEmotes",
                    "Cloudkeeper badge in chat, the tab list and above your head\nName colour palette (5 colours)\nSix emotes with /emote"),
    "starweaver": ("Starweaver badge\nName colour palette\nEmotes\nWardrobe presets\nVisual keepsake",
                   "Starweaver badge in chat, the tab list and above your head\nName colour palette (7 colours)\nSix emotes with /emote\n"
                   "Three wardrobe presets\nStarweaver Lantern keepsake (decorative)"),
    "cloudberry": ("To be confirmed", "Pufflet companion\nCloudstep trail"),
    "pufflet-companion": ("Pufflet companion\nCompanion menu entry", "Pufflet, who follows you around\nSummon it from /cosmetics or /companion"),
    "kitekit-companion": ("Kitekit companion\nCompanion menu entry", "Kitekit, who follows you around\nSummon it from /cosmetics or /companion"),
    "mossbun-companion": ("Mossbun companion\nCompanion menu entry", "Mossbun, who follows you around\nSummon it from /cosmetics or /companion"),
    "cindlet-companion": ("Cindlet companion\nCompanion menu entry", "Cindlet, who follows you around\nSummon it from /cosmetics or /companion"),
    "moonmoth-companion": ("Moonmoth companion\nCompanion menu entry", "Moonmoth, who follows you around\nSummon it from /cosmetics or /companion"),
    "starwhisk-companion": ("Starwhisk companion\nCompanion menu entry", "Starwhisk, who follows you around\nSummon it from /cosmetics or /companion"),
    "blooming-skies": ("To be confirmed", "Mossbun companion\nPetal Skip trail"),
    "celestial-dreams": ("To be confirmed", "Starwhisk companion\nStardust Waltz trail"),
}
STORE_SUMMARIES = {
    "cloudberry": ("A cosmetic bundle. Contents to be confirmed.", "Pufflet and the Cloudstep trail, together."),
    "blooming-skies": ("A cosmetic bundle. Contents to be confirmed.", "Mossbun and the Petal Skip trail, together."),
    "celestial-dreams": ("A cosmetic bundle. Contents to be confirmed.", "Starwhisk and the Stardust Waltz trail, together."),
}


def _rev10(conn):
    now = now_str()
    for slug in COMPANION_SLUGS:
        GAME_PRODUCTS[slug + "-companion"] = "cosmetic.companion." + slug
        conn.execute("UPDATE companions SET acquisition = ?, access = 'purchased', updated_at = ? WHERE slug = ? "
                     "AND updated_by = 'starter content' AND acquisition = 'Not announced yet'",
                     ("From the store once it opens, alone or in a bundle. Summon it with /companion " + slug + ".", now, slug))
    for slug in TRAIL_SLUGS:
        GAME_PRODUCTS[slug + "-trail"] = "cosmetic.trail." + slug
        conn.execute("UPDATE trails SET acquisition = ?, access = 'purchased', updated_at = ? WHERE slug = ? "
                     "AND updated_by = 'starter content' AND acquisition = 'Not announced yet'",
                     ("From the store once it opens. Wear it with /trail " + slug + ".", now, slug))
    for slug, product in GAME_PRODUCTS.items():
        conn.execute("UPDATE products SET game_product = ? WHERE slug = ? AND COALESCE(game_product, '') = ''", (product, slug))
    for slug, (original, current) in STORE_INCLUDES.items():
        conn.execute("UPDATE products SET includes = ?, updated_at = ? WHERE slug = ? AND updated_by = 'starter content' AND includes = ?",
                     (current, now, slug, original))
    for slug, (original, current) in STORE_SUMMARIES.items():
        conn.execute("UPDATE products SET summary = ?, updated_at = ? WHERE slug = ? AND updated_by = 'starter content' AND summary = ?",
                     (current, now, slug, original))
    row = conn.execute("SELECT id, body FROM guides WHERE slug = 'commands' AND updated_by = 'starter content'").fetchone()
    marker = "Read confirmation screens and costs before committing."
    if row and "/companion" not in (row["body"] or "") and marker in (row["body"] or ""):
        from .reference_content import GUIDES as REVIEWED
        section = next(g["body"] for g in REVIEWED if g["slug"] == "commands").split("## Wardrobe", 1)[1].split(marker, 1)[0]
        conn.execute("UPDATE guides SET body = ?, updated_at = ? WHERE id = ?",
                     (row["body"].replace(marker, "## Wardrobe" + section + marker, 1), now, row["id"]))


def _insert(conn, key, row):
    ctype = get_type(key)
    if conn.execute("SELECT 1 FROM %s WHERE slug = ?" % ctype.table, (row["slug"],)).fetchone():
        return False
    data = {f.name: f.default for f in ctype.fields}
    data.update(status="published", sort_order=0, created_at=now_str(), updated_at=now_str(), updated_by="starter content")
    data.update(row)
    cols = list(data.keys())
    conn.execute("INSERT INTO %s (%s) VALUES (%s)" % (ctype.table, ", ".join(cols), ", ".join("?" * len(cols))),
                 [data[c] for c in cols])
    return True


def _insert_ranks(conn):
    for rank in RANKS:
        if not conn.execute("SELECT 1 FROM ranks WHERE slug = ?", (rank["slug"],)).fetchone():
            cols = list(rank.keys())
            conn.execute("INSERT INTO ranks (%s) VALUES (%s)" % (", ".join(cols), ", ".join("?" * len(cols))),
                         [rank[c] for c in cols])


def _rev3(conn):
    # Avatar frames: give each starter rank its frame unless staff already picked one, and add the Media rank.
    _insert_ranks(conn)
    for rank in RANKS:
        conn.execute("UPDATE ranks SET frame = ? WHERE slug = ? AND frame = ''", (rank["frame"], rank["slug"]))


def _rev4(conn):
    for guide in FIELD_GUIDES:
        _insert(conn, "guides", guide)
    # Correct only unedited starter policies; never replace a staff-authored legal page.
    for slug, replacements in POLICY_CORRECTIONS.items():
        row = conn.execute("SELECT id, body FROM pages WHERE slug = ? AND updated_by = 'starter content'", (slug,)).fetchone()
        if row:
            body = row["body"]
            for before, after in replacements:
                body = body.replace(before, after)
            if body != row["body"]:
                conn.execute("UPDATE pages SET body = ?, updated_at = ? WHERE id = ?", (body, now_str(), row["id"]))


def _rev5(conn):
    # Public access replaces the old pre-launch FAQ; keep the entry as an editable draft.
    conn.execute("UPDATE faqs SET status = 'draft', updated_at = ? WHERE slug = 'whitelist'", (now_str(),))
    row = conn.execute("SELECT id, body FROM guides WHERE slug = 'connection-help' AND updated_by = 'starter content'").fetchone()
    if row:
        old = "- **You are not white-listed.** Aerborne is in private testing. Ask staff to add you.\n"
        body = row['body'].replace(old, '')
        if body != row['body']:
            conn.execute("UPDATE guides SET body = ?, updated_at = ? WHERE id = ?", (body, now_str(), row['id']))


def _rev6(conn):
    from . import reference_content, seed
    originals = {row['slug']: row for row in seed.GUIDES + GUIDES + FIELD_GUIDES}
    order = {}
    for reviewed in reference_content.GUIDES:
        category = reviewed['category']
        position = order.get(category, 0)
        order[category] = position + 1
        row = conn.execute("SELECT * FROM guides WHERE slug = ?", (reviewed['slug'],)).fetchone()
        original = originals.get(reviewed['slug'])
        # Compare actual copy as well as authorship, so a manual database edit is preserved.
        expected = dict(original or {})
        expected.setdefault('availability', 'released')
        if reviewed['slug'] == 'trading':
            expected['category'] = 'economy'  # Revision 2 re-filed the original guide.
        untouched = row and original and all((row[key] or '') == (expected.get(key) or '')
                                             for key in ('title', 'summary', 'body', 'category', 'related', 'availability'))
        if untouched and row['updated_by'] == 'starter content':
            changes = dict(reviewed, sort_order=position, updated_at=now_str())
            changes.pop('slug')
            cols = list(changes)
            conn.execute("UPDATE guides SET " + ', '.join(column + ' = ?' for column in cols) + " WHERE id = ?",
                         [changes[column] for column in cols] + [row['id']])
    region_originals = {row['slug']: row for row in seed.REGIONS}
    for slug, reviewed in reference_content.REGIONS.items():
        row = conn.execute("SELECT * FROM regions WHERE slug = ?", (slug,)).fetchone()
        original = region_originals.get(slug)
        if row and original and row['updated_by'] == 'starter content' and (row['body'] or '') == (original.get('body') or ''):
            changes = dict(reviewed, updated_at=now_str())
            cols = list(changes)
            conn.execute("UPDATE regions SET " + ', '.join(column + ' = ?' for column in cols) + " WHERE id = ?",
                         [changes[column] for column in cols] + [row['id']])
    for slug, note in reference_content.CODEX_NOTES.items():
        conn.execute("UPDATE codex_entries SET body = ?, updated_at = ? WHERE slug = ? AND updated_by = 'starter content' AND COALESCE(body, '') = ''",
                     (note, now_str(), slug))
    conn.execute("UPDATE companions SET concept_art = 1 WHERE updated_by = 'starter content' AND image LIKE 'static:img/companions/%'")
    conn.execute("UPDATE faqs SET answer = ?, updated_at = ? WHERE slug = 'lost-items' AND updated_by = 'starter content' AND answer LIKE 'No. A grave keeps your items%'",
                 ('A grave can hold up to 27 stacks; overflow is unprotected. Returning to the barrel is free, while remote recovery has a Marks fee. Recover promptly and read the [grave guide](/guide/graves) for expiry and restart limits. Island void rescue returns you with your items.', now_str()))


def _rev2(conn):
    _insert_ranks(conn)

    for slug, section, icon, private, order in CATEGORY_LAYOUT:
        conn.execute("UPDATE forum_categories SET section = ?, icon = ?, private = ?, sort_order = ? WHERE slug = ?",
                     (section, icon, private, order, slug))
    for slug, name, description, section, icon, private, staff_only, order in NEW_CATEGORIES:
        if not conn.execute("SELECT 1 FROM forum_categories WHERE slug = ?", (slug,)).fetchone():
            conn.execute("INSERT INTO forum_categories (slug, name, description, section, icon, private, staff_only, sort_order) "
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", (slug, name, description, section, icon, private, staff_only, order))

    # Trading moves to its own handbook chapter; only if staff haven't re-filed it.
    conn.execute("UPDATE guides SET category = 'economy' WHERE slug = 'trading' AND category = 'skills'")
    for order, guide in enumerate(GUIDES):
        guide.setdefault("sort_order", 10 + order)
        _insert(conn, "guides", guide)

    for slug, summary, body in LEGAL:
        conn.execute("UPDATE pages SET summary = ?, body = ?, status = 'published', updated_at = ?, updated_by = 'starter content' "
                     "WHERE slug = ? AND status = 'draft' AND body LIKE '\\_Draft%' ESCAPE '\\'", (summary, body, now_str(), slug))

    # Store: publish the starter catalog as "coming soon"; nothing is on sale until it has a price and Tebex link.
    conn.execute("UPDATE products SET status = 'published' WHERE status = 'draft' AND updated_by = 'starter content' "
                 "AND COALESCE(tebex_url, '') = ''")
    conn.execute("UPDATE products SET image = ?, image_alt = ?, concept_art = 0 WHERE slug = 'cloudberry' AND COALESCE(image, '') = ''",
                 (C + "pufflet-360.webp", "Pufflet on a cloud"))
    conn.execute("UPDATE products SET image = ?, image_alt = ?, concept_art = 0 WHERE slug = 'blooming-skies' AND COALESCE(image, '') = ''",
                 (C + "mossbun-360.webp", "Mossbun in a flower meadow"))
    conn.execute("UPDATE products SET image = ?, image_alt = ?, concept_art = 0 WHERE slug = 'celestial-dreams' AND COALESCE(image, '') = ''",
                 (C + "starwhisk-360.webp", "Starwhisk among the stars"))
    for order, product in enumerate(STORE_ITEMS):
        product.setdefault("sort_order", 20 + order)
        _insert(conn, "products", product)

    # New illustrated card art replaces the old concept tiles.
    for slug in ("pufflet", "kitekit", "mossbun", "cindlet", "moonmoth", "starwhisk"):
        conn.execute("UPDATE companions SET image = ?, concept_art = 0 WHERE slug = ? AND image LIKE 'static:img/kit/companions/%'",
                     (C + slug + "-720.webp", slug))

    for key, value in (("discord_invite", ""), ("arcade_enabled", "1"), ("arcade_daily_cap", "200")):
        conn.execute("INSERT OR IGNORE INTO settings (key, value) VALUES (?, ?)", (key, value))


RANKS = [
    {"slug": "owner", "frame": "admin", "name": "OWNER", "color": "#E5484D", "text_color": "#FFFFFF", "staff": 1, "can_moderate": 1, "can_announce": 1, "priority": 100,
     "description": "Runs Aerborne."},
    {"slug": "admin", "frame": "admin", "name": "ADMIN", "color": "#F07A7A", "text_color": "#2A0D10", "staff": 1, "can_moderate": 1, "can_announce": 1, "priority": 90,
     "description": "Server administrators."},
    {"slug": "developer", "frame": "staff", "name": "DEV", "color": "#C59AFA", "text_color": "#24173F", "staff": 1, "can_moderate": 0, "can_announce": 1, "priority": 80,
     "description": "Builds the plugin and the website."},
    {"slug": "moderator", "frame": "staff", "name": "MOD", "color": "#77C8F5", "text_color": "#0D2A3D", "staff": 1, "can_moderate": 1, "can_announce": 0, "priority": 70,
     "description": "Keeps the forum and server friendly."},
    {"slug": "helper", "frame": "staff", "name": "HELPER", "color": "#70DBC3", "text_color": "#0B2E26", "staff": 1, "can_moderate": 0, "can_announce": 0, "priority": 60,
     "description": "Answers questions and helps new players."},
    {"slug": "builder", "frame": "staff", "name": "BUILDER", "color": "#F4C76B", "text_color": "#2E2105", "staff": 1, "can_moderate": 0, "can_announce": 0, "priority": 50,
     "description": "Builds the city, zones and spawn."},
    {"slug": "starweaver", "frame": "starweaver", "name": "STARWEAVER", "color": "#B98CF5", "text_color": "#1E1236", "staff": 0, "can_moderate": 0, "can_announce": 0, "priority": 30,
     "description": "Supporter rank. Cosmetic only.", "product_slug": "starweaver"},
    {"slug": "cloudkeeper", "frame": "cloudkeeper", "name": "CLOUDKEEPER", "color": "#8CE8ED", "text_color": "#0B2A2D", "staff": 0, "can_moderate": 0, "can_announce": 0, "priority": 20,
     "description": "Supporter rank. Cosmetic only.", "product_slug": "cloudkeeper"},
    {"slug": "skyfarer", "frame": "skyfarer", "name": "SKYFARER", "color": "#9AD8FA", "text_color": "#0D2A3D", "staff": 0, "can_moderate": 0, "can_announce": 0, "priority": 10,
     "description": "Supporter rank. Cosmetic only.", "product_slug": "skyfarer"},
    {"slug": "media", "frame": "media", "name": "MEDIA", "color": "#A9B8FF", "text_color": "#141B45", "staff": 0, "can_moderate": 0, "can_announce": 0,
     "priority": 40, "description": "Content creators who make videos and streams on Aerborne."},
]

# slug, section, icon, private, order
CATEGORY_LAYOUT = [
    ("announcements", "official", "crest", 0, 0),
    ("general", "aerborne", "companion:pufflet", 0, 10),
    ("help", "aerborne", "gem:affinity", 0, 11),
    ("islands", "aerborne", "companion:mossbun", 0, 13),
    ("trading", "aerborne", "gem:fortune", 0, 14),
    ("suggestions", "feedback", "gem:precision", 0, 20),
    ("bugs", "feedback", "gem:might", 1, 21),
]
# slug, name, description, section, icon, private, staff_only, order
NEW_CATEGORIES = [
    ("events", "Events", "Build contests, celebrations and seasonal events.", "official", "companion:starwhisk", 0, 1, 1),
    ("player-guides", "Player guides", "Tips and guides written by players.", "aerborne", "gem:celerity", 0, 0, 12),
    ("appeals", "Punishment appeals", "Ask staff to review a mute or ban. Only you and staff can see your thread.", "staff", "gem:aegis", 1, 0, 30),
    ("support-requests", "Purchase & account help", "Store, linking and account problems. Only you and staff can see your thread.", "staff", "gem:ward", 1, 0, 31),
    ("off-topic", "Off-topic", "Anything that isn't Aerborne.", "offtopic", "companion:kitekit", 0, 0, 40),
]


def _guide(title, slug, category, summary, body, related=""):
    return {"title": title, "slug": slug, "category": category, "summary": summary, "body": body.strip() + "\n",
            "related": related}


GUIDES = [
    _guide("Island resources and milestones", "island-resources", "island",
           "How your generator, land and workshop unlock as your co-op levels skills.", """
Open `/island journal` to see your next milestone. Train skills, then click **Share your skill progress**.
Co-op members can cover different requirements, and their certifications stay with the island if they leave.
Unlocks never cost XP, Marks, items or talent points.

| Stage | Requirements | What opens up |
|---|---|---|
| Settler | Your starting island | Cobblestone, coal, copper and iron; the Crusher |
| Homestead | Mining, Farming and Building 5 | Gold and lapis; Compost Bin; first land expansion |
| Workshop | Smithing, Forestry and Hunting 10 | Redstone; Sawmill; second land expansion |
| Sanctuary | Fishing, Excavation and Alchemy 15 | Diamonds; Mineral Sifter; third land expansion |
| Foundry | Island level 15; Mining 25, Smithing 20, Excavation 20 | Quartz ore and better generator odds |
| Aetherworks | Island level 25; Mining 35, Alchemy 30, Farming 30 | Emerald ore, occasional obsidian, end-stone sifting |

Island levels come from skill work done on your island, not from machine output.
""", "Machines and automation | /guide/island-machines"),
    _guide("Machines and automation", "island-machines", "island",
           "Craft the Crusher, Compost Bin, Sawmill and Mineral Sifter, then keep them fed.", """
Every machine uses the same crafting grid, with its own ingredient in the middle row:

```
I I I
C D C
I I I
```

`I` is an iron ingot and `D` a normal dropper. `C` is cobblestone (Crusher), wheat (Compost Bin), oak logs
(Sawmill) or gravel (Mineral Sifter). `/island machines` shows the recipes and can add them to your recipe book.

Place a machine on your island and right-click it:

- **Top row:** ingredients.
- **Middle row:** coal or charcoal.
- **Bottom row:** finished goods.

A full output row pauses the machine without wasting anything. Named or enchanted items are never used up.
For example, the Crusher turns 8 cobblestone into 8 gravel, and 8 gravel into 8 sand, for one fuel each.
""", "Spawners and mob stacking | /guide/island-spawners"),
    _guide("Spawners and mob stacking", "island-spawners", "island",
           "Craft spawners for more mobs and stack them to save space.", """
Open `/island machines` → **Spawner recipes**. Every spawner uses an iron block in each corner, a diamond in the
middle and the species ingredient on the four sides.

| Spawner | Ingredient | Unlocks at |
|---|---|---|
| Zombie, Skeleton, Spider, Blaze | Rotten flesh, bone, string, blaze rod | From the start |
| Creeper, Drowned, Husk | Gunpowder, copper ingot, sand | Homestead |
| Stray, Cave Spider | Ice, fermented spider eye | Workshop |
| Enderman, Zombified Piglin, Witch | Ender pearl, gold nugget, glowstone dust | Sanctuary |
| Breeze | Breeze rod | Foundry |

Right-click a placed spawner while holding a matching one to stack it; sneak and break to take the stack back.
Every mob in a stack still drops its own loot when it dies.
""", "Island resources | /guide/island-resources"),
    _guide("Co-op, visitors and borders", "island-coop", "island",
           "Invite friends, set who can visit, and see exactly where your island ends.", """
- `/island invite <player>` invites someone to your co-op. Invitations expire after five minutes.
- `/island invitations` and `/island accept <owner>` handle invites you've received.
- A co-op holds up to **six people**, including the owner. You can be a member of up to three other co-ops too.
- Members can build and use storage. Only the owner invites, removes and manages the island.

**Visitors.** Islands are private by default. If you open yours for sightseeing, visitors can look around but
can't build, open containers, pick up items or use redstone.

**Borders.** `/island border` toggles a private outline of your protected edges, visible within 40 blocks.
It's only particles: no walls or barrier blocks.
"""),
    _guide("Fishing and the Angler's Journal", "fishing", "skills",
           "Fish anywhere, choose your pond's habitat and fill your collections.", """
You can fish in any water Minecraft lets you catch in: there's no minimum pond size or depth. Keep the bobber
inside your island (or a co-op you belong to). Visitors can't fish another island's pond for rewards.

**Habitats.** `/fishing` → **Pond Habitat** cycles through the habitats your co-op has unlocked. Fish that like
the chosen habitat are four times more likely, but every species can still appear anywhere.

| Island stage | Habitat unlocked |
|---|---|
| Settler | Freshwater |
| Workshop | River |
| Sanctuary | Wetland |
| Foundry | Ocean |
| Aetherworks | Cold Water |

Each catch gives Fishing XP: 25 for a common catch, 37 uncommon, 65 rare and 145 legendary. Turn catches in to
Ivo to record them and build your angling reputation.
""", "Skills and talents | /guide/skills-and-talents"),
    _guide("Farming and replanting", "farming", "skills",
           "Harvest ripe crops with a right-click and they replant themselves.", """
Right-click a ripe crop with a hoe to harvest and replant it in one go. You don't need to sneak or unlock a
talent. One seed from the harvest is kept back for replanting; if none dropped, one comes from your inventory.
Named or enchanted seeds are never used.

| Crop | Farming XP |
|---|---:|
| Wheat, carrots, potatoes, beetroot | 15 |
| Nether wart, cocoa | 18 |
| Sweet berries, glow berries | 15 |
| Torchflowers, pitcher crops | 24 |
| Melons, pumpkins | 24 |
| Sugar cane, cactus, bamboo | 9 |

Each right-click harvest uses one point of hoe durability, so Preservation still helps.
"""),
    _guide("Excavation and dig sites", "excavation", "skills",
           "Dig for finds and geodes at renewable dig sites.", """
Shovels and brushes train Excavation. Soil you placed yourself gives no XP or finds.

Staff set up **dig sites** in shared areas. Their soil gives normal Excavation XP, finds and geodes, then grows
back in place after a short cooldown.

- `/digsite list` shows open sites.
- `/digsite info <site>` shows where it is and how much is ready.
- `/excavation` has a Dig Sites button with travel links.

A better excavation tool raises your Excavation XP.
"""),
    _guide("Cooking and meals", "cooking", "skills",
           "38 recipes, from simple snacks to meals that get better as you level.", """
`/cooking` (or `/cookbook`) shows every meal, its ingredients and the Cooking level it needs. Craft meals at a
crafting table; level-one recipes work for beginners.

Successful crafts give Cooking XP: 12 per ordinary food recipe, and more for named meals. Meal quality rises
with your Cooking level, and island Stewardship talents make meals cooked on your island last longer.
"""),
    _guide("Daily contracts, achievements and playtime", "daily-goals", "skills",
           "Short goals that keep every session moving.", """
- `/daily` shows today's rotating contracts and your progress.
- `/achievements` is your milestone journal, with a filter for unfinished ones.
- `/playtime` shows active playtime and seasonal milestones.
- `/leaderboard` ranks every player, online or not, by Marks, Adventurer XP, skills, island XP,
  achievements or cooking.
""", "Leaderboards | /leaderboards"),
    _guide("City shops", "city-shops", "economy",
           "Buy supplies and sell surplus to the city's ten merchants, from anywhere.", """
`/shops` opens every merchant from anywhere in Survival. Search with `/shops <item>`. Left-click to review a
purchase and right-click to review a sale, then confirm.

- Prices follow supply and demand across the whole server: buying raises a price, selling lowers it.
- Reputation with each merchant also changes prices.
- Ordinary goods have a daily buyback limit, which resets at 00:00 UTC.
- Merchants sell plain supplies, not finished relics or upgraded gear.
""", "Trading and the auction house | /guide/trading"),
    _guide("Remote trades", "remote-trades", "economy",
           "Trade items with another player safely, wherever you both are.", """
`/trade <player>` sends a request; they accept with `/trade accept`. You can be on different islands.

- Left-click a stack to offer it, right-click to offer one item, or use `/trade offer <amount>`.
- Your items stay in your inventory until **both** of you confirm the same offer.
- Any change clears both confirmations and starts a two-second review, so nobody can swap at the last second.
- `/trade cancel`, closing the menu, teleporting or logging out cancels the trade. Nothing moves.
"""),
    _guide("Your bank and paying players", "bank-and-pay", "economy",
           "Store items safely and send Marks to friends.", """
- `/bank` opens your account bank. You can buy extra storage slots there.
- `/pay <player> <amount>` sends earned Marks to an online player. `/pay status` shows your last receipts.
- Marks are only ever earned in game. The store doesn't sell them.
"""),
    _guide("Gem merging and recovery", "gem-merging", "equipment",
           "Combine gems into stronger tiers, or take them back out of your gear.", """
At an anvil's **Forge & Sockets** screen:

- **Merge:** put two gems of the same type and tier in the inputs to make one gem of the next tier, up to IV.
- **Recover:** put the gear on the left and Arcane Dust on the right, then pick the socket to empty.

| Gem tier | Recovery cost |
|---|---:|
| I | 8 Arcane Dust |
| II | 12 Arcane Dust |
| III | 16 Arcane Dust |
| IV | 20 Arcane Dust |

Recovered gems keep their type and tier. You need inventory space for the result; a full inventory spends nothing.
""", "Gear, rarity and sockets | /guide/gear-and-sockets"),
    _guide("Graves and recovering items", "graves", "world",
           "What happens to your things when you fall.", """
When you die, a grave keeps your items where you fell. Only you can open it: co-op members, visitors and
hoppers can't take anything out.

- `/grave locate` points you to your latest grave.
- `/grave recover` brings its contents back remotely.

A grave holds 27 stacks. Anything beyond that drops beside it, so carry less into risky places.
Falling into the void on your own island doesn't create a grave at all: you're carried home with everything.
"""),
    _guide("Parties", "parties", "world",
           "Group up for expeditions and elite fights.", """
- `/party invite <player>` starts a party and invites someone. They join with `/party accept`.
- `/party info` shows who's in it. `/partychat <message>` talks to the party only.
- The leader can `/party kick`, `/party promote` or `/party disband`. Anyone can `/party leave`.

Parties are separate from co-ops: you can adventure with anyone without sharing an island.
"""),
    _guide("Commands quick reference", "commands", "troubleshooting",
           "The commands you'll use most, in one place.", """
| Command | What it does |
|---|---|
| `/menu` | Everything in one menu |
| `/quickstart` | First-session route |
| `/island` | Your island, co-op and settings |
| `/island journal` | Next milestone |
| `/skills` | Skill levels and talents |
| `/arcane` | Your Arcane Codex |
| `/shops` | City merchants |
| `/ah` | Auction house |
| `/trade <player>` | Trade with someone |
| `/bank` | Account bank |
| `/daily` | Daily contracts |
| `/grave recover` | Get your items back |
| `/weblink` | Link your Minecraft account to the website |
| `/report <player> <reason>` | Report someone privately to staff |
| `/settings` | Tips, messages and requests |
"""),
]

LEGAL = [
    ("terms", "The rules for using Aerborne's server, website and forum.", """
These terms cover the Aerborne Minecraft server, this website and its forum. By playing or creating an account
you agree to them.

## Who can play

You need a legitimate copy of Minecraft: Java Edition. Website accounts are for people aged 13 or older. If
you're under the age of adulthood where you live, a parent or guardian should agree to these terms with you.

## Your behaviour

Follow the [server rules](/support/rules) in game, on the forum and in Discord. Don't cheat, exploit bugs,
harass others or try to break the server or website. Report problems instead.

## Accounts

Keep your password private. You're responsible for what happens on your account. You can link one Minecraft
account to one website account; linking proves you own it, and staff may unlink accounts that were linked by
mistake or misuse.

## Staff action

Staff can warn, mute, ban or remove content to keep Aerborne safe. You can [appeal](/support/appeals) a decision.
Bans can apply to the server, the website, or both.

## Your content

You keep ownership of what you post. You let us show it on Aerborne's website, forum and social channels. Don't
post anything you don't have the right to share. Staff may remove posts that break these terms or the rules.

## Purchases

Store purchases are covered by the [store terms](/support/store-terms). Nothing in the store changes gameplay.

## Changes and availability

We may change Aerborne, reset seasons or update these terms. We'll post important changes in News. The server
and website are provided as they are, and may be unavailable at times.

## Not affiliated with Mojang

Aerborne is not an official Minecraft product and is not approved by or associated with Mojang or Microsoft.
We follow the Minecraft End User License Agreement and Usage Guidelines.

## Contact

Questions? Reach staff through the [Support](/support) page.
"""),
    ("privacy", "What this website collects, why, and how long it keeps it.", """
This policy covers the Aerborne website and forum. The Minecraft server keeps its own gameplay records.

## What we collect

- **Account details:** your website username and a securely hashed password. We never see or store your
  password itself, and we don't ask for an email address.
- **Minecraft link:** if you link your account, your Minecraft username and account ID.
- **Profile and posts:** anything you add to your profile and what you post on the forum.
- **Arcade:** the scores and Marks rewards from games you play while signed in.
- **Security records:** your IP address is kept briefly with failed sign-ins and sign-ups so we can stop
  password guessing and spam. These records are deleted after about an hour.
- **Page counts:** we count how often each page is viewed per day. These counts store no IP address, cookie or
  browser details.

## Cookies

We set one cookie when you sign in, to keep you signed in. We don't use advertising or tracking cookies.

## Other services

- **Google Fonts** supplies the site's fonts, so Google receives your IP address when pages load.
- **Tebex** handles store checkout and has its own privacy policy. We never see your card details.
- **Discord** links open Discord's own site.

## How long we keep it

Account, profile and forum data stay until you ask us to delete your account. Security records are deleted
automatically after about an hour.

## Your choices

You can edit your profile at any time. To get a copy of your data or delete your account, contact staff through
[Support](/support). Deleting an account removes your profile; staff may keep posts needed to resolve reports.
"""),
    ("store-terms", "What you get when you support Aerborne, and how refunds work.", """
## Cosmetic only

Everything in the Aerborne store is cosmetic. Purchases never give Marks, gear, island progress or any
gameplay advantage.

## Checkout

Payments are processed by **Tebex**, which sells on our behalf. Your payment details go to Tebex, never to
Aerborne. Prices are shown before checkout in your chosen currency.

## Delivery

Items are delivered to the Minecraft account you enter at checkout, usually within a few minutes of you being
online. If something hasn't arrived after an hour, see [purchase help](/support/purchase-help).

## How long it lasts

Each item page says whether it's permanent or timed. Permanent items stay with your account across seasons and
resets.

## Refunds

Because items are digital and delivered straight away, purchases are generally final. If something didn't
arrive or was charged twice, contact us and we'll fix it or refund it. This doesn't affect any rights you have
under the law where you live.

## Chargebacks

Opening a chargeback instead of contacting us removes the purchased items and may restrict your account
until it's resolved.

## Bans

Store items don't protect an account from rule enforcement. Purchases aren't refunded because of a ban.
"""),
]


def _store(name, slug, category, summary, includes, image, alt):
    return {"name": name, "slug": slug, "category": category, "summary": summary, "includes": includes,
            "image": image, "image_alt": alt, "concept_art": 0, "availability": "upcoming",
            "platforms": "Java Edition", "entitlement": "permanent"}


STORE_ITEMS = [
    _store("Pufflet companion", "pufflet-companion", "companions", "A curious cloud puff that follows you around.",
           "Pufflet companion\nCompanion menu entry", C + "pufflet-360.webp", "Pufflet, a fluffy cloud creature"),
    _store("Kitekit companion", "kitekit-companion", "companions", "A breezy sky fox that rides the updrafts.",
           "Kitekit companion\nCompanion menu entry", C + "kitekit-360.webp", "Kitekit, a sky fox"),
    _store("Mossbun companion", "mossbun-companion", "companions", "A sleepy bunny with a garden on its back.",
           "Mossbun companion\nCompanion menu entry", C + "mossbun-360.webp", "Mossbun, a mossy bunny"),
    _store("Cindlet companion", "cindlet-companion", "companions", "A warm little ember dragon.",
           "Cindlet companion\nCompanion menu entry", C + "cindlet-360.webp", "Cindlet, a small ember dragon"),
    _store("Moonmoth companion", "moonmoth-companion", "companions", "A soft lavender moth that loves lantern light.",
           "Moonmoth companion\nCompanion menu entry", C + "moonmoth-360.webp", "Moonmoth, a lavender moth"),
    _store("Starwhisk companion", "starwhisk-companion", "companions", "A stardust cat that bats at falling stars.",
           "Starwhisk companion\nCompanion menu entry", C + "starwhisk-360.webp", "Starwhisk, a starry cat"),
] + [
    _store(name + " trail", name.lower().replace(" ", "-") + "-trail", "trails", summary, name + " trail effect",
           "static:img/kit/trails/%s-320.webp" % name.lower().replace(" ", "-"), "Preview of the %s trail" % name)
    for name, summary in [
        ("Cloudstep", "Small puffs of cloud with every step."),
        ("Petal Skip", "Petals that skip along behind you."),
        ("Firefly Drift", "A few fireflies that follow you at dusk."),
        ("Rune Ripple", "Faint arcane runes that ripple out as you walk."),
        ("Bubblehop", "Bubbles that bob up and pop."),
        ("Comet Tail", "A streak of starlight behind you."),
        ("Dandelion Drift", "Dandelion seeds carried on the breeze."),
        ("Stardust Waltz", "Stardust that swirls when you turn."),
    ]
]
for _item in STORE_ITEMS:
    if _item["category"] == "trails":
        _item["concept_art"] = 1  # trail previews are still concept tiles


FIELD_GUIDES = [
    _guide("Plan your next island milestone", "milestone-planning", "island",
           "Turn a long unlock list into a manageable session with your co-op.", """
## Check the journal before you gather

Open `/island journal` and find your next unfinished milestone. Check `/skills` to see which
requirements you already meet. The [milestone table](/guide/island-resources) lists each unlock.

## Split the work

Choose one requirement for this session. Ask a co-op member to cover a different skill. Return to
the journal and click **Share your skill progress** when you meet a requirement. Certifications
stay with the island even if a member leaves.

## Put the unlock to work

Check `/island machines` after unlocking a stage. Build a machine you can supply consistently,
leave room for its output and keep spare fuel nearby. Machine output does not raise island
levels; keep doing skill work on the island alongside automation.

## Before you log out

- Share new skill progress in the journal.
- Check machine output space and fuel.
- Pick a requirement for your next session.

Unlocks do not consume XP, Marks or items. You do not need to save a payment for a milestone.
""", "Machines and automation | /guide/island-machines"),
    _guide("Choose how to trade", "trading-safely", "economy",
           "Choose a city shop, auction listing or confirmed player trade for your next deal.", """
## Need ordinary supplies?

Start with `/shops <item>`. Merchants sell plain materials and buy eligible surplus. Review the
confirmation price: supply, demand and reputation can change it. Buyback limits reset at 00:00 UTC.

## Buying from a player?

Browse `/ah`. Read the item description and price before confirming, especially for sealed gear
and gems. Compare it with the [equipment guide](/guide/gear-and-sockets).

## Swapping items directly?

Use `/trade <player>` and have them accept. Review both offers before confirming. Any change
clears both confirmations and starts a short review period. `/trade cancel` ends the exchange
without moving items. Avoid dropping valuables as a substitute for a confirmed trade.

Keep passwords and linking codes private: another player never needs them to trade with you.

## Website store or city shop?

The website store is for cosmetics. City shops and player trading use earned Marks and in-game
items. Website purchases will use Tebex when checkout opens. Never post payment details on the forum.
""", "City shops | /guide/city-shops\nRemote trades | /guide/remote-trades"),
    _guide("Your website account and Arcade rewards", "website-and-arcade", "troubleshooting",
           "Link Minecraft, personalise your profile and track your Arcade Marks.", """
## Link your account

Create a [website account](/account/register), then run `/weblink` in Aerborne. Enter the code on
the [linking page](/account/link). Codes expire after ten minutes and work once. Each Minecraft
account can link to one website account. Keep the code private.

## Make your member card yours

Open [your account](/account) to choose an avatar and banner, write a headline and bio, and add
your island, favourite skill and optional social links. These details are public. Hide game
stats using the profile setting. Staff assign ranks and their matching avatar frames.

## Play a round

Choose a game in the [Arcade](/arcade/). Visitors can practise without signing in. Signed-in scores
can appear on weekly boards; earning Marks needs a linked Minecraft account. The Arcade shows
your daily allowance and each game has a per-run limit.

Accepted rewards enter a queue. The game server collects it when its connection is configured.
The Arcade shows when delivery is still being connected. Daily allowances reset at **00:00 UTC**.
Pausing does not reset a run or its allowance.

## Get help

- **Expired code:** run `/weblink` for a new one.
- **Already linked:** sign in to the original website account.
- **Forgotten password:** use [Minecraft account recovery](/account/recover).
- **Reward waiting:** check the Arcade delivery notice first.
- **Need staff:** open a private [account help thread](/forum/support-requests).
""", "Commands quick reference | /guide/commands"),
]

POLICY_CORRECTIONS = {
    "privacy": [
        ("We never see or store your\n  password itself", "We store only a password hash, not your\n  password"),
        ("We never see or store your\npassword itself", "We store only a password hash, not your\npassword"),
        ("We set one cookie when you sign in, to keep you signed in.",
         "We use a session cookie to keep you signed in and protect forms against forged submissions. It may be set before you sign in."),
        ("**Tebex** handles store checkout and has its own privacy policy. We never see your card details.",
         "**Tebex** will handle payments when the store opens. Its privacy policy applies when you visit checkout. This website does not collect card details."),
    ],
    "store-terms": [
        ("Payments are processed by **Tebex**, which sells on our behalf. Your payment details go to Tebex, never to\nAerborne. Prices are shown before checkout in your chosen currency.",
         "The catalog is public. Items marked **Not on sale yet** cannot be purchased. When sales open, checkout will take place on **Tebex**. Review the final price, currency, taxes and [checkout terms](https://checkout.tebex.io/terms) before paying. This website does not collect payment details."),
        ("Because items are digital and delivered straight away, purchases are generally final. If something didn't\narrive or was charged twice, contact us and we'll fix it or refund it. This doesn't affect any rights you have\nunder the law where you live.",
         "For billing, cancellation or refund requests, use [Tebex buyer support](https://www.tebex.io/support-customer) and the terms shown at checkout. For missing items, contact Aerborne through [purchase help](/support/purchase-help). Keep your transaction reference private. Nothing here limits rights provided by applicable consumer law."),
        ("Opening a chargeback instead of contacting us removes the purchased items and may restrict your account\nuntil it's resolved.",
         "Tebex handles payment disputes. Contact buyer support with your transaction reference; Aerborne staff can help investigate delivery."),
        ("Purchases aren't refunded because of a ban.",
         "Refund requests remain subject to the checkout terms and applicable consumer rights."),
    ],
}
