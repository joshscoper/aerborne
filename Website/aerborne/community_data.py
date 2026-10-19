"""Fixed choices for the forum and profiles: sections, thread prefixes, avatars and banners."""
from .gameapi import SKILLS

SECTIONS = [
    ("official", "Official", "News, events and notices from the Aerborne team."),
    ("aerborne", "Aerborne", "Help, guides, island talk and trading."),
    ("feedback", "Feedback", "Ideas and bug reports. Bug reports are only visible to you and staff."),
    ("staff", "Contact staff", "Private: only you and staff can read these threads."),
    ("offtopic", "Off-topic", "Everything else."),
]
SECTION_KEYS = [s[0] for s in SECTIONS]

# slug: (label, tone). Tones map to colours in site.css.
PREFIXES = {
    "question": ("Question", "sky"),
    "guide": ("Guide", "mint"),
    "discussion": ("Discussion", "stone"),
    "showcase": ("Showcase", "gold"),
    "suggestion": ("Suggestion", "lavender"),
    "bug": ("Bug", "red"),
    "buying": ("Buying", "peach"),
    "selling": ("Selling", "peach"),
    "trading": ("Trading", "peach"),
    "appeal": ("Appeal", "lavender"),
    "event": ("Event", "gold"),
    "update": ("Update", "sky"),
}
CATEGORY_PREFIXES = {
    "announcements": ["update", "event"],
    "events": ["event"],
    "general": ["discussion", "question", "showcase"],
    "help": ["question"],
    "player-guides": ["guide"],
    "islands": ["showcase"],
    "trading": ["buying", "selling", "trading"],
    "suggestions": ["suggestion"],
    "bugs": ["bug"],
    "appeals": ["appeal"],
    "support-requests": ["question"],
    "off-topic": ["discussion", "question"],
}
DEFAULT_PREFIXES = ["discussion", "question"]


def prefixes_for(category_slug):
    return [(p, PREFIXES[p][0]) for p in CATEGORY_PREFIXES.get(category_slug, DEFAULT_PREFIXES)]


LEGACY_AVATARS = [
    ("crest", "Skycrest", "img/brand/skycrest-192.webp"),
    ("companion:pufflet", "Pufflet", "img/companions/pufflet-avatar.webp"),
    ("companion:kitekit", "Kitekit", "img/companions/kitekit-avatar.webp"),
    ("companion:mossbun", "Mossbun", "img/companions/mossbun-avatar.webp"),
    ("companion:cindlet", "Cindlet", "img/companions/cindlet-avatar.webp"),
    ("companion:moonmoth", "Moonmoth", "img/companions/moonmoth-avatar.webp"),
    ("companion:starwhisk", "Starwhisk", "img/companions/starwhisk-avatar.webp"),
] + [("gem:" + a, a.capitalize(), "img/aspects/%s-192.webp" % a) for a in
     ("might", "precision", "aegis", "ward", "celerity", "vitality", "resolve", "affinity", "fortune")]
DEFAULT_AVATAR = "sprite:sky-sentinel"
AVATARS = [("sprite:" + slug, label, "img/avatars/" + slug + "-256.webp") for slug, label in [
    ("sky-sentinel", "Sky Sentinel"), ("pufflet", "Pufflet"), ("moss-bunny", "Moss Bunny"),
    ("ember-sprite", "Ember"), ("frost-sprite", "Frost"), ("blossom-sprite", "Blossom"),
    ("shadow-sprite", "Shadow"), ("celestial-sprite", "Celestial"), ("aqua-sprite", "Aqua"),
    ("forest-sprite", "Forest"), ("stone-sprite", "Stone"), ("magma-sprite", "Magma"),
    ("amethyst-sprite", "Amethyst"), ("golden-sprite", "Golden"), ("cosmic-sprite", "Cosmic"),
    ("cloud-sprite", "Cloud"),
]]
AVATAR_PATHS = {key: path for key, _label, path in AVATARS + LEGACY_AVATARS}

# Supplied transparent forum illustrations. Custom categories retain their chosen avatar icon.
FORUM_ICONS = {
    "events": "events.png",
    "general": "general-chat", "help": "help-questions", "player-guides": "player-guides",
    "islands": "island-showcase", "trading": "trading-post", "suggestions": "suggestions",
    "bugs": "bug-reports", "appeals": "punishment-appeals",
    "support-requests": "purchase-account-help", "off-topic": "off-topic",
}

BANNERS = [
    ("skybound", "Skybound city", "img/forum/hero.png"),
    ("sky", "Sky kingdom", "img/brand/hero-960.webp"),
    ("kingdom", "Floating castles", "img/brand/sky-kingdom-800.webp"),
    ("pufflet", "Pufflet's clouds", "img/companions/pufflet-720.webp"),
    ("mossbun", "Mossbun's meadow", "img/companions/mossbun-720.webp"),
    ("cindlet", "Cindlet's sunset", "img/companions/cindlet-720.webp"),
    ("moonmoth", "Moonmoth's night", "img/companions/moonmoth-720.webp"),
    ("starwhisk", "Starwhisk's stars", "img/companions/starwhisk-720.webp"),
    ("kitekit", "Kitekit's breeze", "img/companions/kitekit-720.webp"),
]
BANNER_PATHS = {key: path for key, _label, path in BANNERS}

# Avatar frames from the frames pack, shown around avatars on posts and profiles. Ranks pick one;
# members without a rank get the plain member frame.
FRAMES = [
    ("member", "Member"),
    ("skyfarer", "Skyfarer"),
    ("cloudkeeper", "Cloudkeeper"),
    ("starweaver", "Starweaver"),
    ("staff", "Staff"),
    ("admin", "Admin"),
    ("media", "Media"),
]
FRAME_KEYS = {key for key, _label in FRAMES}


def frame_key(frame):
    return frame if frame in FRAME_KEYS else "member"


FAVOURITE_SKILLS = [(s, s.capitalize()) for s in SKILLS]

# Columns every author display needs. Use with "FROM members m LEFT JOIN ranks r ON r.id = m.rank_id".
AUTHOR_COLUMNS = ("m.username, m.avatar, m.custom_title, m.post_count, m.likes_received, m.created_at AS joined_at, "
                  "m.mc_name, m.role, m.status AS member_status, m.signature, "
                  "r.slug AS rank_slug, r.name AS rank_name, r.staff AS rank_staff, r.frame AS rank_frame")
