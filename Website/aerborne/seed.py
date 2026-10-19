"""Starter content, written from the plugin's own docs (../docs) so the site launches accurate.

Runs once on a fresh database. Staff edit or replace all of it in the admin afterwards.
Store items and legal pages start as drafts: prices, entitlements and legal text need an owner's
review before they go public.
"""
from .content_types import get_type
from .db import audit, now_str, set_setting

SEED_VERSION = "1"

# The owner's default staff account. Only the password hash is stored here, and the first
# sign-in must replace the password, so the shipped default can't stay in use on a live site.
DEFAULT_ADMIN_USERNAME = "j0sh"
DEFAULT_ADMIN_HASH = "pbkdf2:sha256:260000$pm9RUjLsmJzWPImE$4ffb360d043e9eaaf6acab9964babad24a6304a9d260e6b4ae0a812082de98be"

FORUM_CATEGORIES = [
    ("announcements", "Announcements", "News and notices from the Aerborne team.", 1),
    ("general", "General chat", "Talk about anything Aerborne.", 0),
    ("help", "Help & questions", "Stuck on something? Ask other players and staff.", 0),
    ("islands", "Island showcase", "Show off your island and co-op builds.", 0),
    ("trading", "Trading post", "Find buyers and sellers. Trades still happen in game with /trade.", 0),
    ("suggestions", "Suggestions", "Ideas to make Aerborne better.", 0),
    ("bugs", "Bug reports", "Something broken? Tell us here. Report exploits privately to staff instead.", 0),
]
K = "static:img/kit/"


def _insert(conn, key, rows):
    ctype = get_type(key)
    for order, row in enumerate(rows):
        data = {f.name: f.default for f in ctype.fields}
        data.update(status="published", sort_order=order, created_at=now_str(),
                    updated_at=now_str(), updated_by="starter content")
        data.update(row)
        cols = list(data.keys())
        conn.execute("INSERT INTO %s (%s) VALUES (%s)" % (ctype.table, ", ".join(cols), ", ".join("?" * len(cols))),
                     [data[c] for c in cols])


def seed_default_admin(conn):
    conn.execute("BEGIN IMMEDIATE")
    if conn.execute("SELECT COUNT(*) FROM users").fetchone()[0]:
        conn.rollback()
        return False
    cur = conn.execute(
        "INSERT INTO users (username, display_name, password_hash, role, must_change_password, created_at) "
        "VALUES (?, ?, ?, 'admin', 1, ?)",
        (DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_HASH, now_str()))
    audit(conn, None, "create", "users", cur.lastrowid,
          "Created the default admin %s (password change required at first sign-in)" % DEFAULT_ADMIN_USERNAME)
    conn.commit()
    return True


DEFAULT_SETTINGS = {"forum_enabled": "1", "registrations_open": "1", "forum_require_link": "1", "fun_cursor": "1"}


def ensure_default_settings(conn):
    for key, value in DEFAULT_SETTINGS.items():
        conn.execute("INSERT OR IGNORE INTO settings (key, value) VALUES (?, ?)", (key, value))
    conn.commit()


def seed_forum(conn):
    conn.execute("BEGIN IMMEDIATE")
    if conn.execute("SELECT COUNT(*) FROM forum_categories").fetchone()[0]:
        conn.rollback()
        return False
    for order, (slug, name, description, staff_only) in enumerate(FORUM_CATEGORIES):
        conn.execute("INSERT INTO forum_categories (slug, name, description, staff_only, sort_order) VALUES (?, ?, ?, ?, ?)",
                     (slug, name, description, staff_only, order))
    conn.commit()
    return True


def seed_if_empty(conn):
    # Several Passenger workers can start at once; the write lock makes exactly one of them seed.
    conn.execute("BEGIN IMMEDIATE")
    row = conn.execute("SELECT value FROM settings WHERE key = 'seeded'").fetchone()
    if row:
        conn.rollback()
        return False
    _insert(conn, "aspects", ASPECTS)
    _insert(conn, "codex", CODEX)
    _insert(conn, "guides", GUIDES)
    _insert(conn, "regions", REGIONS)
    _insert(conn, "companions", COMPANIONS)
    _insert(conn, "trails", TRAILS)
    _insert(conn, "products", PRODUCTS)
    _insert(conn, "faqs", FAQS)
    _insert(conn, "pages", PAGES)
    _insert(conn, "news", NEWS)
    set_setting(conn, "seeded", SEED_VERSION)
    set_setting(conn, "community_intro",
                "Share your island on Discord. With the builder's permission, staff feature favourites here.")
    audit(conn, None, "seed", "", None, "Added starter content")
    conn.commit()
    return True


ASPECTS = [
    {"name": "Might", "slug": "might", "gems": "Ruby", "summary": "Offensive power."},
    {"name": "Precision", "slug": "precision", "gems": "Amethyst", "summary": "Aim, finishing blows and ranged strikes."},
    {"name": "Aegis", "slug": "aegis", "gems": "Sapphire", "summary": "Physical protection."},
    {"name": "Ward", "slug": "ward", "gems": "Moonstone", "summary": "Resistance to magic and affliction."},
    {"name": "Celerity", "slug": "celerity", "gems": "Aquamarine", "summary": "Speed of action and motion."},
    {"name": "Vitality", "slug": "vitality", "gems": "Jade", "summary": "Health, growth and recovery."},
    {"name": "Resolve", "slug": "resolve", "gems": "Onyx", "summary": "Endurance and lasting work."},
    {"name": "Affinity", "slug": "affinity", "gems": "Emerald", "summary": "Elemental and arcane attunement."},
    {"name": "Fortune", "slug": "fortune", "gems": "Topaz, Citrine", "summary": "Rewards, finds and discovery."},
]


def _codex(name, slug, tier, aspects, equipment, summary, public, availability="released", max_level=5):
    return {"name": name, "slug": slug, "tier": tier, "aspects": aspects, "equipment": equipment,
            "summary": summary, "recipe_public": 1 if public else 0, "availability": availability,
            "max_level": max_level}


# Effect enchantments and Signatures are switched off by default in production
# (arcane-forge effects.* and signatures.enabled), so they start as previews.
CODEX = [
    _codex("Keen Edge", "keen-edge", "fundamental", "might", "Melee weapons", "Raises weapon damage by 5% per level.", True),
    _codex("Marksman", "marksman", "fundamental", "precision", "Bows and crossbows", "Raises projectile damage by 5% per level.", True),
    _codex("Warding", "warding", "fundamental", "aegis", "Armor and shields", "Adds 3 defense per level.", True),
    _codex("Preservation", "preservation", "fundamental", "resolve", "Anything with durability", "Has a 12% chance per level to prevent durability loss.", True),
    _codex("Reaping", "reaping", "fundamental", "vitality", "Hoes and harvest tools", "Has an 8% chance per level to add another crop drop.", True),
    _codex("Scholar", "scholar", "fundamental", "affinity", "Any equipment", "Raises all skill XP by 6% per level.", True),
    _codex("Prospector", "prospector", "fundamental", "fortune", "Pickaxes and mining tools", "Raises geode discovery odds by 18% per level.", True),
    _codex("Executioner", "executioner", "specialized", "might,precision", "Weapons", "Deals 5% more damage per level to wounded enemies.", False),
    _codex("Soulsiphon", "soulsiphon", "specialized", "might,vitality", "Weapons", "Heals for 1.2% of PvE damage per level.", False),
    _codex("Venombound", "venombound", "specialized", "ward,affinity", "Weapons", "Raises poison duration and strength by 10% per level.", False),
    _codex("Angler", "angler", "specialized", "celerity,fortune", "Fishing rods", "Raises fishing rarity and bite speed by 8% per level.", False),
    _codex("Archaeologist", "archaeologist", "specialized", "fortune,resolve", "Shovels and brushes", "Raises excavation find odds by 10% per level.", False),
    _codex("Bloodletter", "bloodletter", "specialized", "precision,vitality", "Melee weapons", "Full-strength hits make the target bleed for 8% of the hit per level.", False, "preview"),
    _codex("Veinseeker", "veinseeker", "specialized", "resolve,affinity", "Pickaxes", "Mining natural ore also mines up to 2 connected ores of that kind per level.", False, "preview"),
    _codex("Chain Harvest", "chain-harvest", "specialized", "vitality,celerity", "Hoes and harvest tools", "Breaking a ripe crop also harvests up to 2 connected ripe crops per level.", False, "preview"),
    _codex("Stormwake", "stormwake", "signature", "might,celerity,affinity", "Melee weapons", "Full-strength hits build Static Charge. At full charge, your next hit arcs lightning to nearby enemies.", False, "preview", 1),
    _codex("Heart of the Mountain", "heart-of-the-mountain", "signature", "fortune,resolve,affinity", "Pickaxes", "Mining natural ore builds Resonance. At full Resonance, the next natural ore drops its loot twice.", False, "preview", 1),
    _codex("Last Bastion", "last-bastion", "signature", "aegis,vitality,resolve", "Armor", "A blow that would kill you leaves you at one health with brief protection instead. Long cooldown.", False, "preview", 1),
]


GUIDES = [
    {"title": "Connect and create your island", "slug": "first-island", "category": "getting-started",
     "summary": "Join the server, claim your floating island and take your first look around.",
     "related": "How to connect | /play\nIsland basics | /guide/island-basics",
     "body": """Aerborne runs on **Minecraft: Java Edition**. Open the Play page for the current address and
supported version, then add it under **Multiplayer → Add Server**.

Once you're in, type `/island create`. You land on your own small island in the sky with:

- an oak tree, water and lava
- a few crops to get farming going
- a starter block generator, so you never run out of stone

Type `/quickstart` for a short route through your first session, or `/menu` to explore on your own.

### If you fall

The void doesn't take your things. Fall off your island and you're carried home safely, items and all.
"""},
    {"title": "Island basics: resources and growth", "slug": "island-basics", "category": "getting-started",
     "summary": "How your island grows, who can build on it, and the commands you'll use every day.",
     "related": "Skills and talents | /guide/skills-and-talents",
     "body": """Your island grows through what you and your co-op do on it.

- `/island journal` shows the skill work that unlocks more territory and better generator tiers.
- `/island talents` spends shared island XP on a talent tree for the whole co-op.
- `/island machines` lists the machines you can build, like stackable spawners and filtered chunk hoppers.

### Playing together

Invite friends with `/island invite <player>`. A co-op holds up to **six people**, including the owner.
You can also join up to **three** other co-ops without losing your own island, inventory or skills.
Members can build and use storage; only the owner manages the island.

Islands are **private by default**. You can open yours for sightseeing: visitors can look around,
but they can't build, open containers or pick anything up.
"""},
    {"title": "Your first gear upgrades", "slug": "first-gear", "category": "getting-started",
     "summary": "Identify sealed gear, set gems into sockets and make your equipment your own.",
     "related": "Gear, rarity and sockets | /guide/gear-and-sockets",
     "body": """Some equipment you find is **sealed**: its bonuses are hidden until it's identified.
Visit Nyra, the identifier, to reveal held gear or everything in your backpack at once.
Identification reveals the item's existing roll; it never rerolls it.

### Sockets

Right-click an anvil to open **Forge & Sockets**. Put an identified weapon, tool or armour piece on the
left and a gem on the right; the preview shows exactly what changes before you confirm.

Two gems of the same type and tier merge into one gem of the next tier, up to tier IV. You can take a gem
back out later for a little Arcane Dust.

> Craft four Arcane Dust from one lapis lazuli and one amethyst shard.
"""},
    {"title": "Professions and your first enchantment", "slug": "first-enchantment", "category": "getting-started",
     "summary": "Train skills as you play, then visit an enchanting table for your first Arcane enchantment.",
     "related": "Skills and talents | /guide/skills-and-talents\nHow the Arcane Forge works | /guide/arcane-forge",
     "body": """Everything you do trains one of eleven skills: Mining, Forestry, Farming, Fishing, Hunting,
Excavation, Smithing, Alchemy, Building, Cooking and Enchanting. Check them with `/skills`.

### Enchanting

Right-click an enchanting table to see three Arcane Enchanting offers, paid for with Arcane Dust.
Every enchantment you bind is recorded in your **Arcane Codex** (`/arcane`), which works anywhere.
"""},
    {"title": "Preparing to leave your island", "slug": "beyond-your-island", "category": "getting-started",
     "summary": "Trade in the city, group up, and get ready for what lies past your island.",
     "related": "Explore the world | /explore\nThe auction house | /guide/trading",
     "body": """`/spawn` takes you to the city, where merchants buy and sell. City prices follow supply and
demand across the whole server.

- `/ah` opens the auction house. `/store` runs your own player shop. `/trade <player>` trades face to face,
  with both sides confirming the same offer.
- Marks, the server's currency, are **earned in play** and never sold.
- If you fall in a fight, a grave keeps your items safe.

New zones open beyond the islands over time. When one is ready to visit, it appears on the
[Explore](/explore) page.
"""},
    {"title": "Skills and talents", "slug": "skills-and-talents", "category": "skills",
     "summary": "The eleven skills, how they level, and where your talent points go.",
     "body": """| Skill | Trained by |
|---|---|
| Mining | Ores, stone and deep veins |
| Forestry | Logs and saplings |
| Farming | Crops and island farms |
| Fishing | Catches, treasure and the Angler's Journal |
| Hunting | Mobs and elite fights |
| Excavation | Dig sites and buried finds |
| Smithing | Forging and upgrading gear |
| Alchemy | Brewing |
| Building | Placing blocks |
| Cooking | Meals; quality rises with your level |
| Enchanting | Every Arcane Forge action |

Open `/skills` to see your levels and spend talent points. Your **Adventurer Level** (1 to 100)
reflects your overall progress and shows beside your name. `/achievements` tracks milestones and
`/leaderboard` shows rankings.
"""},
    {"title": "Trading and the auction house", "slug": "trading", "category": "skills",
     "summary": "Auctions, player stores and direct trades, all paid in earned Marks.",
     "body": """All three work remotely, so you don't need to leave your island.

- **Auction house:** `/ah` to browse; `/ah sell <starting-bid> [buy-now] [hours] [amount]` to list the item
  in your hand for 1 to 72 hours.
- **Player store:** `/store` to stock your own shop.
- **Trades:** `/trade <player>`. Any change clears both confirmations, so nobody can swap an item at the last second.

Account-bound items and store cosmetics can't be listed.
"""},
    {"title": "Gear, rarity and sockets", "slug": "gear-and-sockets", "category": "equipment",
     "summary": "Sealed gear, identification, gem sockets and merging.",
     "body": """Gear comes in rarities, and higher rarities hold more enchantment (see Arcane Capacity in the
Arcane Forge guides).

**Identification.** Nyra reveals sealed gear for Marks, or scraps it into Arcane Dust if you'd rather.
Each action shows a confirmation with the item count and total cost first.

**Sockets.** Use an anvil's **Forge & Sockets** screen to set gems. Each gem carries one of nine
**Aspects**, which also drive Arcane research.

**Merging.** Two gems of the same type and tier make one of the next tier, up to IV.
"""},
    {"title": "How the Arcane Forge works", "slug": "arcane-forge", "category": "arcane",
     "summary": "The Codex, research, Chosen Imbuement and mastery, in one page.",
     "related": "Open the Arcane Codex | /codex",
     "body": """| Feature | Where | What it does |
|---|---|---|
| Arcane Enchanting | Enchanting table | Three random offers for Arcane Dust. Everything you bind is recorded. |
| Arcane Codex | `/arcane` | Your discoveries, research clues and the Aspect encyclopedia. Works anywhere. |
| Arcane Research | `/arcane research` at a table | Combine socket gems to discover new enchantments. |
| Chosen Imbuement | `/arcane imbue` at a table | Bind a discovered enchantment at an exact level. The price shows first; nothing is random. |
| Mastery | `/arcane mastery` | Bind an enchantment at its top level three times to master it: imbuing it costs 25% less. |

Every Arcane action earns **Enchanting** XP.
"""},
    {"title": "Aspects and research", "slug": "aspects-and-research", "category": "arcane",
     "summary": "Nine Aspects, the gems that carry them, and how discovery works.",
     "related": "Browse the Codex | /codex",
     "body": """Every enchantment belongs to one, two or three of nine **Aspects**, and every socket gem carries one.
Research combines one to three gems with different Aspects:

- **One Aspect** discovers a Fundamental enchantment in a single experiment.
- **Two Aspects** discover a Specialized enchantment over two experiments.
- **Three Aspects** discover an Arcane Signature over four.

Better gems, or a Prismatic catalyst, speed this up. A combination that's part of a bigger recipe gives
partial progress and, eventually, a clue.

The menu never tells you what an untried combination does, and it refuses before spending anything if a
combination has nothing left to teach you. Working out the rest is part of the game.
"""},
    {"title": "Arcane Capacity, instability and corruption", "slug": "capacity-and-corruption", "category": "arcane",
     "summary": "How much an item can hold, what happens when you push past it, and how to recover.",
     "availability": "released",
     "body": """**Arcane Capacity** is how much enchantment an item holds. It comes from the item's rarity, level and
quality. Only Arcane Signatures need free capacity; existing enchantments are never removed.

**Overcharge and instability.** Some items can be pushed past their capacity. An overcharged item becomes
**unstable** and must be stabilised with Arcane Dust before you bind anything else to it.

**Corruption.** You can choose one corruption for a fixed bonus. The item wears faster, uses capacity and
takes no new Arcane work until it's cleansed. Cleansing always works.

**Attunement.** Focus an item on one of its Aspects: its enchantments of that Aspect grow 10% stronger.
Clearing attunement is free.
"""},
    {"title": "Enchantment tiers and Signatures", "slug": "enchantment-tiers", "category": "arcane",
     "summary": "Fundamental, Specialized and Arcane Signature enchantments compared.",
     "availability": "preview",
     "body": """| Tier | Aspects | What to expect |
|---|---|---|
| Fundamental | 1 | Reliable, always-on bonuses |
| Specialized | 2 | Bonuses that shine in a particular situation |
| Arcane Signature | 3 | Rare, build-defining effects. One per item. |

**Arcane Signatures** are discovered through three-Aspect research and need free Arcane Capacity.
They're built but not switched on yet on the live server; this page will update when they open.
"""},
    {"title": "The world of Aerborne", "slug": "world-overview", "category": "world",
     "summary": "Floating islands, the city above the clouds and the zones beyond.",
     "body": """Every player's island floats in its own patch of open sky. Above them all sits the city, where
islands trade, bank and meet the people who send them out on adventures.

Beyond the islands, staff are building hand-made zones with their own creatures and materials.
Each one appears on the Explore page once it's open to visit.
"""},
    {"title": "Can't connect?", "slug": "connection-help", "category": "troubleshooting",
     "summary": "The usual reasons a connection fails, and what to try.",
     "body": """- **Outdated client or server.** Use the Minecraft version listed on the Play page.
- **You are not white-listed.** Aerborne is in private testing. Ask staff to add you.
- **Can't reach the server.** Check the address for typos, then check Server Status. If it says offline,
  the server may be restarting.
- **Bedrock, console or mobile.** Aerborne is Java Edition only.

Still stuck? Ask in Discord with a screenshot of the error.
"""},
]


REGIONS = [
    {"name": "Your Island", "slug": "your-island", "theme": "Home in the sky",
     "summary": "A starter island floating in open sky, with a tree, a pond, lava, crops and a block generator. It grows as your co-op does.",
     "activities": "Farm, mine and build\nUnlock territory through the island journal\nAutomate with island machines\nHost friends for sightseeing",
     "requirements": "Everyone starts here",
     "points_of_interest": "Your landing\nYour starter generator",
     "image": "static:img/island.svg", "image_alt": "A small floating island with an oak tree, a pond and lavender crystals underneath",
     "availability": "released"},
    {"name": "The Arcane City", "slug": "arcane-city", "theme": "Floating capital",
     "summary": "A shared capital above the clouds, with merchants, services and the routes out to new zones.",
     "body": "The city is still being built. Merchants and services open as each district is finished.",
     "activities": "Trade with city merchants\nVisit service NPCs\nMeet other islands",
     "requirements": "Reach it with /spawn",
     "image": "static:img/brand/sky-kingdom-800.webp", "concept_art": 1,
     "image_alt": "Painted artwork of white castles on floating islands with waterfalls pouring into the clouds",
     "availability": "preview"},
    {"name": "Expedition zones", "slug": "expeditions", "theme": "Beyond the islands",
     "summary": "Hand-built zones with their own creatures and materials you can't grow at home.",
     "body": "Zones are being built by staff. Each will get its own entry here when it opens.",
     "availability": "upcoming"},
]


def _companion(name, species, bio, temperament, image):
    return {"name": name, "slug": name.lower(), "species": species, "summary": bio,
            "temperament": temperament, "image": K + "companions/%s-320.webp" % image,
            "image_alt": "Concept portrait of %s, a block-built %s" % (name, species.lower()),
            "concept_art": 1, "access": "tba", "acquisition": "Not announced yet",
            "availability": "upcoming"}


COMPANIONS = [
    _companion("Pufflet", "Cloud puff", "A curious little sky creature made of cloud fluff. Pufflet loves to explore and sometimes forgets how to fly, usually mid-air.", "Curious, a bit forgetful", "pufflet"),
    _companion("Kitekit", "Sky fox", "A breezy fox kit that rides the updrafts between islands, tail streaming like a kite string.", "Playful and quick", "kitekit"),
    _companion("Mossbun", "Moss bunny", "A sleepy bunny with a garden growing on its back. Flowers bloom whenever it's happy.", "Gentle and sleepy", "mossbun"),
    _companion("Cindlet", "Ember sprite", "A warm little ember that keeps close on cold nights and glows brighter when it's excited.", "Brave and warm-hearted", "cindlet"),
    _companion("Moonmoth", "Moon moth", "A soft lavender moth that follows lantern light and drifts awake when the moon is out.", "Calm and dreamy", "moonmoth"),
    _companion("Starwhisk", "Star cat", "A stardust cat that bats at falling stars and leaves a faint shimmer wherever it naps.", "Mischievous and proud", "starwhisk"),
]


def _trail(name, summary):
    slug = name.lower().replace(" ", "-")
    return {"name": name, "slug": slug, "summary": summary, "image": K + "trails/%s-320.webp" % slug,
            "image_alt": "Concept preview of the %s trail" % name, "concept_art": 1, "access": "tba",
            "acquisition": "Not announced yet", "availability": "upcoming"}


TRAILS = [
    _trail("Cloudstep", "Small puffs of cloud with every step."),
    _trail("Petal Skip", "Petals that skip along behind you."),
    _trail("Firefly Drift", "A few fireflies that follow you at dusk."),
    _trail("Rune Ripple", "Faint arcane runes that ripple out as you walk."),
    _trail("Bubblehop", "Bubbles that bob up and pop."),
    _trail("Comet Tail", "A streak of starlight behind you."),
    _trail("Dandelion Drift", "Dandelion seeds carried on the breeze."),
    _trail("Stardust Waltz", "Stardust that swirls when you turn."),
]


def _product(name, category, summary, includes, image):
    return {"name": name, "slug": name.lower().replace(" ", "-"), "category": category, "summary": summary,
            "includes": includes, "image": image, "image_alt": "Concept art for %s" % name,
            "concept_art": 1, "availability": "upcoming", "status": "draft", "platforms": "Java Edition",
            "entitlement": "permanent"}


# Drafts until the owner sets real prices, entitlements and Tebex links.
PRODUCTS = [
    _product("Skyfarer", "supporter-ranks", "A thank-you rank for supporting Aerborne. Cosmetic only.",
             "Skyfarer badge\nName colour palette", K + "ranks/skyfarer-320.webp"),
    _product("Cloudkeeper", "supporter-ranks", "A thank-you rank for supporting Aerborne. Cosmetic only.",
             "Cloudkeeper badge\nName colour palette\nEmotes", K + "ranks/cloudkeeper-320.webp"),
    _product("Starweaver", "supporter-ranks", "A thank-you rank for supporting Aerborne. Cosmetic only.",
             "Starweaver badge\nName colour palette\nEmotes\nWardrobe presets\nVisual keepsake", K + "ranks/starweaver-320.webp"),
    _product("Cloudberry", "cosmetic-bundles", "A cosmetic bundle. Contents to be confirmed.", "To be confirmed", ""),
    _product("Blooming Skies", "cosmetic-bundles", "A cosmetic bundle. Contents to be confirmed.", "To be confirmed", ""),
    _product("Celestial Dreams", "cosmetic-bundles", "A cosmetic bundle. Contents to be confirmed.", "To be confirmed", ""),
]


FAQS = [
    {"question": "Which Minecraft version do I need?", "slug": "which-version", "category": "connecting",
     "answer": "Minecraft: Java Edition. The Play page always lists the versions the server accepts."},
    {"question": "Can I play on Bedrock, console or mobile?", "slug": "bedrock", "category": "connecting",
     "answer": "Not right now. Aerborne is Java Edition only."},
    {"question": "Why can't I join yet?", "slug": "whitelist", "category": "connecting",
     "answer": "Aerborne is in private testing with a whitelist while content is finished. Watch News and Discord for openings."},
    {"question": "Is Aerborne free to play?", "slug": "free", "category": "playing",
     "answer": "Yes. Everything that affects gameplay is earned in game."},
    {"question": "Can I buy power, Marks or island progress?", "slug": "pay-to-win", "category": "store",
     "answer": "No. The store only offers cosmetics. Marks, gear and island progress are earned by playing."},
    {"question": "I died. Are my items gone?", "slug": "lost-items", "category": "playing",
     "answer": "No. A grave keeps your items where you fell. Falling into the void on your island carries you home with everything."},
    {"question": "How do I report a player?", "slug": "report-player", "category": "account",
     "answer": "In game, type `/report <player> <reason>`. It goes privately to staff."},
]


PAGES = [
    {"title": "Server rules", "slug": "rules", "section": "support",
     "summary": "A few rules so everyone enjoys the skies. Staff can point to them by number.",
     "body": """1. **Be kind.** No harassment, hate speech, threats or targeting other players.
2. **Play fair.** No hacked clients, x-ray, duplication or automation mods that play for you. Report bugs instead of using them.
3. **Trade honestly.** Describe items truthfully. No scams, fake listings or pressure tactics.
4. **Respect other islands.** Only build where you've been invited, and talk to your owner before tearing anything down.
5. **Keep chat readable.** No spam, caps floods or advertising other servers.
6. **Listen to staff.** If you think a decision was wrong, ask calmly and in private, or appeal.

Aerborne follows the Minecraft End User License Agreement and Usage Guidelines. Breaking these rules can lead
to a warning, mute or ban depending on how serious it is.
"""},
    {"title": "Appeals", "slug": "appeals", "section": "support",
     "summary": "How to ask staff to review a mute or ban.",
     "body": """If you think a punishment was a mistake, contact staff through the official Discord or the support
email listed on this page. Include your Minecraft username, what happened and when, and why you think
the decision should change.

A different staff member reviews each appeal where possible. Please send one appeal and wait for an answer.
"""},
    {"title": "Reporting bugs", "slug": "bug-reports", "section": "support",
     "summary": "Found something broken? Here's what helps staff fix it fast.",
     "body": """Tell staff on Discord with:

- what you were doing
- what you expected, and what happened instead
- the time it happened, and a screenshot if you can

Exploits should be reported privately to staff, not posted in public channels.
"""},
    {"title": "Purchase help", "slug": "purchase-help", "section": "support",
     "summary": "What to do if a store purchase hasn't arrived.",
     "body": """Purchases are processed by Tebex, our payment provider. If something hasn't arrived:

1. Rejoin the server; deliveries can need you online.
2. Check the email Tebex sent for your receipt.
3. Contact staff with your Minecraft username and the Tebex transaction ID. Never share card details.
"""},
    {"title": "Account help", "slug": "account-help", "section": "support",
     "summary": "Username changes, lost access and in-game account questions.",
     "body": """Your Aerborne progress is tied to your Minecraft account, so a username change keeps everything.
Staff will never ask for your Microsoft password. If someone does, report it.
"""},
    {"title": "Terms of service", "slug": "terms", "section": "legal", "status": "draft",
     "summary": "Owner review required before publishing.",
     "body": "_Draft. Write the terms with the owner (and legal advice) before publishing. Cover eligibility, conduct, account actions, the store, liability and changes to these terms._"},
    {"title": "Privacy policy", "slug": "privacy", "section": "legal", "status": "draft",
     "summary": "Owner review required before publishing.",
     "body": """_Draft. Review before publishing._

What this website does today, to describe accurately:

- It sets **no cookies for visitors**. Staff who sign in to the admin get a session cookie.
- It counts page views per day and page, without storing IP addresses, cookies or user agents.
- Fonts load from Google Fonts, which receives visitors' IP addresses.
- The server-status card connects to the Minecraft server, not to visitors' devices.
- Store checkout happens on Tebex, which has its own privacy policy.
"""},
    {"title": "Store terms and refunds", "slug": "store-terms", "section": "legal", "status": "draft",
     "summary": "Owner review required before publishing.",
     "body": "_Draft. State exactly what each purchase grants, how long it lasts, the refund policy and chargeback handling, and check them against the current Minecraft commercial usage guidelines._"},
]


NEWS = [
    {"title": "Welcome to the new Aerborne website", "slug": "welcome", "category": "journal",
     "summary": "A home for guides, news and the Arcane Codex while Aerborne is in private testing.",
     "author": "Aerborne Team", "featured": 1,
     "related": "Start the Adventurer's Handbook | /guide\nBrowse the Arcane Codex | /codex",
     "body": """Aerborne now has a proper home on the web.

- The **Game Guide** walks new players from their first island to their first enchantment.
- The **Arcane Codex** lists every enchantment that's public knowledge. Hidden recipes stay hidden;
  discovering them is part of the game.
- **Companions** shows the pets we're designing. They're concept art for now, and we'll say so
  until each one is in the game.

Aerborne is still in private testing. We'll post here when that changes.
"""},
]
