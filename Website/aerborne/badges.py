"""Profile badges: automatic ones earned through activity, and ones staff award by hand.

Automatic badges are worked out when a profile is shown, so they appear and disappear with the activity
behind them. Staff-awarded badges live in the badges and member_badges tables and are managed under
Admin → Forum → Badges.
"""
from .db import utcnow, parse_time

# The supplied badge pack. Legacy keys still resolve, so saved and custom awards keep working.
ARTWORK = [
    ('aerborne-team', 'Aerborne Team'), ('founding-member', 'Founding Member'),
    ('high-scorer', 'High Scorer'), ('verified-helper', 'Verified Helper'),
    ('event-winner', 'Event Winner'), ('bug-hunter', 'Bug Hunter'),
    ('guide-writer', 'Guide Writer'), ('community-helper', 'Community Helper'),
    ('lore-seeker', 'Lore Seeker'), ('master-trader', 'Master Trader'),
    ('island-builder', 'Island Builder'), ('sky-explorer', 'Sky Explorer'),
    ('firstlight-sigil', 'Firstlight Sigil'),
]
FIRSTLIGHT = {
    'slug': 'firstlight-sigil', 'name': 'Firstlight Sigil',
    'rarity': 'Mythic · Invitation Only',
    'description': "Before the skies opened to the world, a chosen few carried the first light. This sigil honors those whose presence helped shape Aerborne's story.",
    'acquisition': "Personally awarded by Aerborne's creators. Never purchasable or obtainable through ordinary progression.",
    'icon': 'badge-firstlight-sigil', 'tone': 'gold', 'creator_only': 1,
    'award_policy': 'firstlight-sigil',
}
FIRSTLIGHT_LIMIT = 5


def is_firstlight(badge):
    return (badge['award_policy'] == 'firstlight-sigil' or badge['slug'] == 'firstlight-sigil'
            or badge['icon'] == 'badge-firstlight-sigil')


def firstlight_quota(conn, admin_id):
    used = conn.execute('SELECT COUNT(*) FROM firstlight_awards WHERE admin_user_id=?', (admin_id,)).fetchone()[0]
    return {'used': used, 'remaining': max(0, FIRSTLIGHT_LIMIT - used), 'limit': FIRSTLIGHT_LIMIT}
ICONS = [('badge-' + slug, label, 'img/badges/%s-96.webp' % slug) for slug, label in ARTWORK]
LEGACY_ARTWORK = {
    'gem-might': 'aerborne-team', 'gem-precision': 'guide-writer', 'gem-aegis': 'island-builder',
    'gem-ward': 'verified-helper', 'gem-celerity': 'sky-explorer', 'gem-vitality': 'community-helper',
    'gem-resolve': 'island-builder', 'gem-affinity': 'lore-seeker', 'gem-fortune': 'master-trader',
    'forum-general-chat': 'community-helper', 'forum-help-questions': 'verified-helper',
    'forum-island-showcase': 'island-builder', 'forum-trading-post': 'master-trader',
    'forum-suggestions': 'guide-writer', 'forum-bug-reports': 'bug-hunter',
    'forum-player-guides': 'guide-writer', 'forum-off-topic': 'sky-explorer',
    'pet-pufflet': 'founding-member', 'pet-mossbun': 'community-helper', 'pet-cindlet': 'aerborne-team',
    'pet-moonmoth': 'lore-seeker', 'pet-starwhisk': 'lore-seeker', 'pet-kitekit': 'sky-explorer',
}
LEGACY_ARTWORK.update({'arcade-' + game: 'high-scorer' for game in
                      ('sky-hop', 'gem-rush', 'cloud-catch', 'codex-memory', 'forge-strike', 'rune-breaker')})
ICON_PATHS = {key: path for key, _label, path in ICONS}
ICON_PATHS.update({key: 'img/badges/%s-96.webp' % slug for key, slug in LEGACY_ARTWORK.items()})
TONES = [("sky", "Sky blue"), ("gold", "Gold"), ("mint", "Mint"), ("lavender", "Lavender"), ("peach", "Peach"), ("ruby", "Ruby")]
FOUNDERS = 100  # The first hundred accounts are founding members.

# key, name, description, icon, tone, test(facts)
AUTOMATIC = [
    ("team", "Aerborne team", "Part of the team that runs Aerborne.", "badge-aerborne-team", "ruby", lambda f: f["staff"]),
    ("supporter", "Supporter", "Helps keep the skies open with a supporter rank.", "gem-fortune", "gold", lambda f: f["supporter"]),
    ("creator", "Content creator", "Makes videos or streams about Aerborne.", "gem-precision", "lavender", lambda f: f["creator"]),
    ("founder", "Founding member", "One of the first %d adventurers to join the website." % FOUNDERS, "badge-founding-member", "gold",
     lambda f: f["id"] <= FOUNDERS),
    ("verified", "Verified adventurer", "Linked their Minecraft account.", "badge-verified-helper", "sky", lambda f: f["linked"]),
    ("year", "A year in the skies", "Has been part of the community for a year.", "gem-celerity", "mint", lambda f: f["days"] >= 365),
    ("first-words", "First words", "Wrote their first forum post.", "forum-general-chat", "sky", lambda f: f["posts"] >= 1),
    ("regular", "Forum regular", "Wrote 50 forum posts.", "forum-player-guides", "mint", lambda f: f["posts"] >= 50),
    ("veteran", "Forum veteran", "Wrote 250 forum posts.", "gem-resolve", "lavender", lambda f: f["posts"] >= 250),
    ("well-liked", "Well liked", "Received 25 likes on their posts.", "gem-vitality", "peach", lambda f: f["likes"] >= 25),
    ("favourite", "Community favourite", "Received 250 likes on their posts.", "gem-affinity", "gold", lambda f: f["likes"] >= 250),
    ("social", "Social butterfly", "Has 10 friends.", "pet-kitekit", "sky", lambda f: f["friends"] >= 10),
    ("arcade", "Arcade player", "Earned Marks in the Arcade.", "arcade-sky-hop", "mint", lambda f: f["marks"] > 0),
    ("high-score", "High scorer", "Holds the best score in an Arcade game.", "badge-high-scorer", "gold", lambda f: f["top_scores"] > 0),
]


def icon_path(key):
    return ICON_PATHS.get(key, ICON_PATHS["gem-ward"])


def facts(conn, member):
    mid = member["id"]
    rank = conn.execute("SELECT * FROM ranks WHERE id = ?", (member["rank_id"],)).fetchone() if member["rank_id"] else None
    joined = parse_time(member["created_at"])
    top_scores = conn.execute(
        "SELECT COUNT(*) FROM (SELECT game, MAX(score) AS best FROM arcade_runs WHERE status = 'finished' AND score > 0 GROUP BY game) b "
        "WHERE (SELECT MAX(score) FROM arcade_runs r WHERE r.game = b.game AND r.status = 'finished' AND r.member_id = ?) = b.best",
        (mid,)).fetchone()[0]
    return {
        "id": mid,
        "staff": bool(rank and rank["staff"]) or member["role"] == "staff",
        "supporter": bool(rank and rank["product_slug"]),
        "creator": bool(rank and rank["slug"] == "media"),
        "linked": bool(member["mc_uuid"]),
        "days": (utcnow() - joined).days if joined else 0,
        "posts": member["post_count"],
        "likes": member["likes_received"],
        "friends": conn.execute("SELECT COUNT(*) FROM friendships WHERE member_a = ? OR member_b = ?", (mid, mid)).fetchone()[0],
        "marks": conn.execute("SELECT COALESCE(SUM(marks), 0) FROM arcade_payouts WHERE member_id = ?", (mid,)).fetchone()[0],
        "top_scores": top_scores,
    }


def for_member(conn, member):
    """Every badge a member has: staff-awarded first (newest first), then automatic ones."""
    out = [{"name": r["name"], "description": r["description"], "icon": icon_path(r["icon"]), "tone": r["tone"],
            'rarity': r['rarity'], 'acquisition': r['acquisition'], 'creator_only': r['creator_only'],
            "awarded": True, "awarded_at": r["awarded_at"], "note": r["note"]}
           for r in conn.execute("SELECT b.*, mb.awarded_at, mb.note FROM member_badges mb JOIN badges b ON b.id = mb.badge_id "
                                 "WHERE mb.member_id = ? ORDER BY mb.awarded_at DESC", (member["id"],))]
    f = facts(conn, member)
    out += [{"name": name, "description": description, "icon": icon_path(icon), "tone": tone, "awarded": False}
            for _key, name, description, icon, tone, test in AUTOMATIC if test(f)]
    for badge in out:
        badge['icon_2x'] = badge['icon'].replace('-96.webp', '-192.webp')
    return out
