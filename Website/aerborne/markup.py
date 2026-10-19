"""Markdown for staff-written content, sanitised before it reaches the page."""
import bleach
import markdown
from markupsafe import Markup

ALLOWED_TAGS = [
    "p", "br", "hr", "h2", "h3", "h4", "strong", "em", "b", "i", "code", "pre", "blockquote",
    "ul", "ol", "li", "a", "img", "table", "thead", "tbody", "tr", "th", "td", "kbd",
]
ALLOWED_ATTRIBUTES = {
    "a": ["href", "title"],
    "img": ["src", "alt", "title", "width", "height"],
    "th": ["align"],
    "td": ["align"],
}
ALLOWED_PROTOCOLS = ["http", "https", "mailto"]


def _nofollow_external(attrs, new=False):
    href = attrs.get((None, "href"), "")
    if href.startswith("http"):
        attrs[(None, "rel")] = "noopener nofollow"
    return attrs


def render(text):
    if not text:
        return Markup("")
    html = markdown.markdown(text, extensions=["tables", "fenced_code", "sane_lists"],
                             output_format="html5")
    clean = bleach.clean(html, tags=ALLOWED_TAGS, attributes=ALLOWED_ATTRIBUTES,
                         protocols=ALLOWED_PROTOCOLS, strip=True)
    clean = bleach.linkify(clean, callbacks=[_nofollow_external], skip_tags=["pre", "code"],
                           parse_email=False)
    # h1 is not in ALLOWED_TAGS, so each page keeps its own single top heading.
    return Markup(clean)


def plain(text, limit=200):
    """Strip markup for summaries and feeds."""
    text = bleach.clean(markdown.markdown(text or ""), tags=[], strip=True)
    text = " ".join(text.split())
    if len(text) > limit:
        text = text[: limit - 1].rsplit(" ", 1)[0] + "…"
    return text


FORUM_TAGS = ["p", "br", "strong", "em", "b", "i", "code", "pre", "blockquote", "ul", "ol", "li", "a", "hr", "del"]


def _ugc_links(attrs, new=False):
    href = attrs.get((None, "href"), "")
    if href.startswith("http"):
        attrs[(None, "rel")] = "nofollow ugc noopener"
    return attrs


def render_forum(text):
    """Player posts: a smaller tag set than staff content, no images or headings."""
    if not text:
        return Markup("")
    html = markdown.markdown(text, extensions=["fenced_code", "sane_lists"], output_format="html5")
    clean = bleach.clean(html, tags=FORUM_TAGS, attributes={"a": ["href", "title"]},
                         protocols=ALLOWED_PROTOCOLS, strip=True)
    clean = bleach.linkify(clean, callbacks=[_ugc_links], skip_tags=["pre", "code"], parse_email=False)
    return Markup(clean)
