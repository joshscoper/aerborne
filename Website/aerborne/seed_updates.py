"""Content added after the first release. Each revision runs once, on new and existing sites.

Updates never overwrite something staff have edited: guides and pages are only inserted when their
slug is free, and drafts are only replaced while they still hold the original placeholder text.
"""
from .content_types import get_type
from .db import audit, now_str, set_setting

LATEST = 8
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
    set_setting(conn, "seed_rev", str(LATEST))
    audit(conn, None, "seed", "", None, "Added content revision %d" % LATEST)
    conn.commit()
    return True


def _rev7(conn):
    # Raise the original allowance while retaining custom limits (including rewards disabled).
    conn.execute("UPDATE settings SET value = '1000' WHERE key = 'arcade_daily_cap' AND value = '200'")


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
