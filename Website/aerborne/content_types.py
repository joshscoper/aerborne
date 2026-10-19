"""Every kind of editable content, described once.

The admin builds its forms, validation and lists from these definitions, and the database
adds new columns automatically when a field is added here.
"""
import re

STATUSES = [
    ("draft", "Draft"),
    ("review", "In review"),
    ("published", "Published"),
    ("archived", "Archived"),
]

AVAILABILITY = [
    ("released", "Released: playable now"),
    ("preview", "Preview: built but not switched on yet"),
    ("upcoming", "Upcoming: planned, not built yet"),
]

ASPECTS = [
    ("might", "Might"), ("precision", "Precision"), ("aegis", "Aegis"),
    ("ward", "Ward"), ("celerity", "Celerity"), ("vitality", "Vitality"),
    ("resolve", "Resolve"), ("affinity", "Affinity"), ("fortune", "Fortune"),
]

ACCESS = [
    ("tba", "Not announced yet"),
    ("earned", "Earned in game"),
    ("purchased", "Store purchase"),
    ("both", "Earned or purchased"),
]

SLUG_RE = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")


class Field(object):
    def __init__(self, name, label, kind="text", required=False, options=None, help="",
                 default=None):
        self.name = name
        self.label = label
        self.kind = kind
        self.required = required
        self.options = options or []
        self.help = help
        self.default = default

    @property
    def sql(self):
        if self.kind in ("bool", "int"):
            return "INTEGER"
        return "TEXT"

    def option_label(self, value):
        for key, label in self.options:
            if key == value:
                return label.split(":")[0]
        return value


class ContentType(object):
    def __init__(self, key, table, label, singular, fields, title_field="title",
                 order_by="sort_order, id", roles=("admin", "editor", "writer"),
                 endpoint=None, description=""):
        self.key = key
        self.table = table
        self.label = label
        self.singular = singular
        self.fields = fields
        self.title_field = title_field
        self.order_by = order_by
        self.roles = roles
        self.endpoint = endpoint
        self.description = description

    def field(self, name):
        for f in self.fields:
            if f.name == name:
                return f
        return None

    def columns(self):
        cols = [(f.name, f.sql) for f in self.fields]
        cols += [
            ("status", "TEXT NOT NULL DEFAULT 'draft'"),
            ("publish_at", "TEXT"),
            ("sort_order", "INTEGER NOT NULL DEFAULT 0"),
            ("created_at", "TEXT"),
            ("updated_at", "TEXT"),
            ("updated_by", "TEXT"),
        ]
        return cols

    def create_sql(self):
        cols = ",\n  ".join("%s %s" % c for c in self.columns())
        return "CREATE TABLE IF NOT EXISTS %s (\n  id INTEGER PRIMARY KEY,\n  %s\n)" % (self.table, cols)

    def title_of(self, item):
        return item[self.title_field] if item else ""


def _common_image(label="Image"):
    return [
        Field("image", label, "image", help="Pick from the media library or a built-in image."),
        Field("image_alt", "Image description", help="Describe the image for screen readers."),
    ]


NEWS_CATEGORIES = [
    ("major", "Major update"),
    ("patch", "Patch notes"),
    ("event", "Event"),
    ("journal", "Developer journal"),
]

GUIDE_CATEGORIES = [
    ("getting-started", "Getting started"),
    ("island", "Your island"),
    ("skills", "Skills & professions"),
    ("economy", "Marks & trading"),
    ("equipment", "Equipment & combat"),
    ("arcane", "Arcane Forge"),
    ("world", "World & exploration"),
    ("troubleshooting", "Help & troubleshooting"),
]

STORE_CATEGORIES = [
    ("supporter-ranks", "Supporter ranks"),
    ("companions", "Companions"),
    ("trails", "Trails"),
    ("cosmetic-bundles", "Cosmetic bundles"),
    ("island-decor", "Island decor"),
]

RELATED_HELP = "One link per line as Label | /path, for example Arcane Forge | /guide/arcane-forge"

TYPES = [
    ContentType("news", "news", "News", "news post", [
        Field("title", "Title", required=True),
        Field("slug", "URL slug", "slug", required=True),
        Field("category", "Category", "select", required=True, options=NEWS_CATEGORIES),
        Field("summary", "Summary", "textarea", required=True, help="One or two sentences for lists and the RSS feed."),
        Field("body", "Article", "markdown"),
        Field("image", "Banner", "image"),
        Field("image_alt", "Banner description"),
        Field("author", "Author", default="Aerborne Team"),
        Field("version_tag", "Version", help="Release or patch number, if this post has one."),
        Field("related", "Related links", "lines", help=RELATED_HELP),
        Field("featured", "Feature on the homepage", "bool"),
    ], order_by="COALESCE(publish_at, created_at) DESC, id DESC", endpoint="public.news_article",
        description="Updates, patch notes, events and developer journals."),

    ContentType("guides", "guides", "Guides", "guide", [
        Field("title", "Title", required=True),
        Field("slug", "URL slug", "slug", required=True),
        Field("category", "Section", "select", required=True, options=GUIDE_CATEGORIES),
        Field("summary", "Summary", "textarea", required=True),
        Field("body", "Guide", "markdown"),
        Field("image", "Screenshot", "image"),
        Field("image_alt", "Screenshot description"),
        Field("availability", "Availability", "select", options=AVAILABILITY, default="released"),
        Field("related", "Related links", "lines", help=RELATED_HELP),
    ], endpoint="public.guide_article",
        description="The Adventurer's Handbook. Getting-started guides show as numbered steps in sort order."),

    ContentType("codex", "codex_entries", "Codex entries", "codex entry", [
        Field("name", "Name", required=True),
        Field("slug", "URL slug", "slug", required=True),
        Field("tier", "Tier", "select", required=True, options=[
            ("fundamental", "Fundamental"), ("specialized", "Specialized"), ("signature", "Arcane Signature")]),
        Field("aspects", "Aspects", "multiselect", options=ASPECTS),
        Field("recipe_public", "Show its Aspects publicly", "bool",
              help="Leave off for recipes players should discover in game. Off hides the Aspects everywhere on the site."),
        Field("equipment", "Equipment", required=True, help="For example Melee weapons, Pickaxes, Armor."),
        Field("max_level", "Maximum level", "int", default=5),
        Field("level_required", "Enchanting level needed", "int", help="Leave blank if there is no fixed requirement."),
        Field("summary", "Effect", "textarea", required=True),
        Field("body", "Notes", "markdown"),
        Field("availability", "Availability", "select", options=AVAILABILITY, default="released"),
    ], title_field="name", endpoint="public.codex",
        description="The enchantment browser. Entries never reveal hidden recipes unless you tick the box."),

    ContentType("aspects", "aspects", "Aspects", "Aspect", [
        Field("name", "Name", required=True),
        Field("slug", "URL slug", "slug", required=True),
        Field("gems", "Socket gems", required=True),
        Field("summary", "What it governs", "textarea", required=True),
    ], title_field="name", endpoint="public.codex"),

    ContentType("regions", "regions", "Regions", "region", [
        Field("name", "Name", required=True),
        Field("slug", "URL slug", "slug", required=True),
        Field("theme", "Theme", help="A few words, for example Floating farmland."),
        Field("summary", "Lore", "textarea", required=True),
        Field("body", "Journal entry", "markdown"),
        Field("image", "Image", "image"),
        Field("image_alt", "Image description"),
        Field("concept_art", "Image is an artist's impression", "bool", default=0,
              help="Labels the picture so players don't expect it to look exactly like this in game."),
        Field("activities", "Things to do", "lines"),
        Field("requirements", "Suggested progress", help="Roughly when players should visit."),
        Field("points_of_interest", "Points of interest", "lines",
              help="Public landmarks only. No rare-spawn coordinates or secrets."),
        Field("availability", "Availability", "select", options=AVAILABILITY, default="upcoming"),
    ], title_field="name", endpoint="public.region"),

    ContentType("companions", "companions", "Companions", "companion", [
        Field("name", "Name", required=True),
        Field("slug", "URL slug", "slug", required=True),
        Field("species", "Species"),
        Field("summary", "Biography", "textarea", required=True),
        Field("temperament", "Temperament"),
        Field("variants", "Colour variants", "lines"),
        Field("animations", "Animations", "lines"),
        Field("acquisition", "How to get it"),
        Field("access", "Earned or purchased", "select", options=ACCESS, default="tba"),
        Field("image", "Portrait", "image"),
        Field("image_alt", "Portrait description"),
        Field("concept_art", "Portrait is concept art", "bool", default=1,
              help="Shows a Concept art label until the in-game model is finished."),
        Field("availability", "Availability", "select", options=AVAILABILITY, default="upcoming"),
    ], title_field="name", endpoint="public.companion"),

    ContentType("trails", "trails", "Trails", "trail", [
        Field("name", "Name", required=True),
        Field("slug", "URL slug", "slug", required=True),
        Field("summary", "Description", "textarea", required=True),
        Field("image", "Preview", "image"),
        Field("image_alt", "Preview description"),
        Field("concept_art", "Preview is concept art", "bool", default=1),
        Field("acquisition", "How to get it"),
        Field("access", "Earned or purchased", "select", options=ACCESS, default="tba"),
        Field("availability", "Availability", "select", options=AVAILABILITY, default="upcoming"),
    ], title_field="name", endpoint="public.companions"),

    ContentType("products", "products", "Store items", "store item", [
        Field("name", "Name", required=True),
        Field("slug", "URL slug", "slug", required=True),
        Field("category", "Category", "select", required=True, options=STORE_CATEGORIES),
        Field("summary", "Description", "textarea", required=True),
        Field("includes", "What the player receives", "lines", required=True,
              help="One item per line. Be exact; this is what players are buying."),
        Field("price", "Price", help="Exactly as Tebex charges it, for example $9.99 USD."),
        Field("entitlement", "Entitlement", "select", options=[
            ("permanent", "Permanent"), ("timed", "Timed"), ("one-time", "One-time use")]),
        Field("entitlement_note", "Entitlement details", help="For timed items, how long it lasts."),
        Field("platforms", "Platforms", default="Java Edition"),
        Field("tebex_url", "Tebex package link", "url", help="Checkout happens on Tebex, never on this site."),
        Field("image", "Image", "image"),
        Field("image_alt", "Image description"),
        Field("concept_art", "Image is concept art", "bool", default=1),
        Field("availability", "Availability", "select", options=AVAILABILITY, default="upcoming"),
    ], title_field="name", roles=("admin", "editor"), endpoint="public.store_item",
        description="The store catalog. Only cosmetics; payment stays on Tebex."),

    ContentType("showcases", "showcases", "Community builds", "community build", [
        Field("title", "Title", required=True),
        Field("slug", "URL slug", "slug", required=True),
        Field("builder", "Builder", required=True, help="The name the builder asked to be credited as."),
        Field("summary", "Description", "textarea"),
        Field("image", "Screenshot", "image"),
        Field("image_alt", "Screenshot description"),
        Field("creator_permission", "The builder gave permission to feature this", "bool",
              help="Required before publishing."),
    ], roles=("admin", "editor", "moderator"), endpoint="public.community",
        description="Player builds. Moderators approve them by publishing."),

    ContentType("faqs", "faqs", "FAQs", "FAQ", [
        Field("question", "Question", required=True),
        Field("slug", "URL slug", "slug", required=True),
        Field("category", "Topic", "select", options=[
            ("playing", "Playing"), ("connecting", "Connecting"), ("store", "Store"), ("account", "Account")]),
        Field("answer", "Answer", "markdown", required=True),
    ], title_field="question", endpoint="public.support"),

    ContentType("pages", "pages", "Support & legal pages", "page", [
        Field("title", "Title", required=True),
        Field("slug", "URL slug", "slug", required=True),
        Field("section", "Section", "select", required=True,
              options=[("support", "Support"), ("legal", "Legal")]),
        Field("summary", "Summary", "textarea"),
        Field("body", "Page", "markdown", required=True),
    ], endpoint="public.page", description="Rules, appeals, purchase help, terms and privacy."),
]

BY_KEY = {t.key: t for t in TYPES}


def get_type(key):
    return BY_KEY.get(key)
