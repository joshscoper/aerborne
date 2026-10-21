import re
import unittest
from html.parser import HTMLParser

from aerborne.guide_reader import sections
from aerborne.markup import render
from aerborne.reference_content import GUIDES
from test_app import SiteTest


class Text(HTMLParser):
    def __init__(self):
        HTMLParser.__init__(self)
        self.parts = []
    def handle_data(self, data):
        self.parts.append(data)
    @classmethod
    def plain(cls, source):
        parser = cls()
        parser.feed(str(source))
        return ' '.join(' '.join(parser.parts).split())


class GuideSectionsTest(unittest.TestCase):
    def test_all_guides_keep_their_content_and_have_unique_anchors(self):
        for guide in GUIDES:
            with self.subTest(guide=guide['slug']):
                parts = sections(guide['body'])
                self.assertTrue(parts)
                self.assertEqual(len(parts), len({part['id'] for part in parts}))
                rebuilt = ' '.join((part['title'] + ' ' + Text.plain(part['html'])).strip() for part in parts)
                original = Text.plain(render(guide['body']))
                # All shipped guides start with a heading; authored preambles get At a glance.
                self.assertIn(original, rebuilt)
                for part in parts:
                    self.assertNotIn('<h2>', part['html'])
                    if '<table>' in part['html']:
                        self.assertIn('class="guide-table"', part['html'])
                        self.assertIn('scope="col"', part['html'])

    def test_preamble_nested_headings_and_sanitisation(self):
        parts = sections('Intro\n\n## One & two\nBody <img src="/safe.png" onerror="alert(1)">\n\n> ## A quoted heading\n> Keep this in the quote.\n\n## One & two\nEnd')
        self.assertEqual([p['title'] for p in parts], ['At a glance', 'One & two', 'One & two'])
        self.assertNotEqual(parts[1]['id'], parts[2]['id'])
        self.assertIn('<blockquote>', parts[1]['html'])
        self.assertNotIn('onerror', parts[1]['html'])
        self.assertEqual(len(sections('Plain staff-authored guide')), 1)


OLD_GEM_BODY = "## Merge two gems\nAt an anvil's **Forge & Sockets**, put gems of the **same type and tier** into the two inputs. One gem from each input makes one gem of the next tier, up to **IV**. Review and confirm the result.\n\n## Recover an installed gem\nPut the equipment on the left and **Arcane Dust** on the right, then select the filled socket you want to empty.\n\n| Installed tier | Recovery cost |\n|---|---:|\n| I | 8 Arcane Dust |\n| II | 12 Arcane Dust |\n| III | 16 Arcane Dust |\n| IV | 20 Arcane Dust |\n\nRecovered gems keep their type and tier. Other installed gems remain. A review expires after 15 seconds or an input change, so check it again before confirming.\n\nRecovery and merging require inventory room for the result; a full inventory spends nothing. Closing the forge is different: returned inputs that do not fit can drop at your feet. Work somewhere safe.\n\n**Dust recipe:** 1 lapis lazuli + 1 amethyst shard → 4 Arcane Dust.\n"


class GuideReaderPagesTest(SiteTest):
    def test_reader_is_usable_without_javascript_and_uses_current_table_values(self):
        page = self.client.get('/guide/gem-merging').get_data(as_text=True)
        self.assertIn('aria-label="Guide sections"', page)
        self.assertIn('data-reader-mode="steps"', page)
        self.assertIn('Dust needed for one gem', page)
        for cost in (8, 12, 16, 20):
            self.assertIn('%d Arcane Dust' % cost, page)
        self.assertNotRegex(page, r'<section[^>]+data-guide-step[^>]*hidden')
        for anchor in re.findall(r'href="#(guide-[^"]+)" data-section-link', page):
            self.assertIn('id="%s"' % anchor, page)

    def test_reader_refresh_preserves_staff_edits_and_record_identity(self):
        from aerborne import seed_updates
        conn = self.db()
        conn.execute("UPDATE guides SET body = ? WHERE slug = 'gem-merging'", (OLD_GEM_BODY,))
        record_id = conn.execute("SELECT id FROM guides WHERE slug = 'gem-merging'").fetchone()[0]
        conn.execute("UPDATE guides SET body = 'Staff body' WHERE slug = 'parties'")
        conn.execute("UPDATE guides SET summary = 'Staff summary' WHERE slug = 'commands'")
        conn.execute("UPDATE settings SET value = '11' WHERE key = 'seed_rev'")
        conn.commit()
        self.assertTrue(seed_updates.apply(conn))
        row = conn.execute("SELECT id, body FROM guides WHERE slug = 'gem-merging'").fetchone()
        self.assertEqual(row['id'], record_id)
        self.assertIn('Dust needed for one gem', row['body'])
        self.assertEqual(conn.execute("SELECT body FROM guides WHERE slug = 'parties'").fetchone()[0], 'Staff body')
        self.assertEqual(conn.execute("SELECT summary FROM guides WHERE slug = 'commands'").fetchone()[0], 'Staff summary')
        self.assertFalse(seed_updates.apply(conn))
        conn.close()
