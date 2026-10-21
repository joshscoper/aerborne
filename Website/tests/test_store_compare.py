import unittest

from aerborne.store_compare import comparison


class RankComparisonTest(unittest.TestCase):
    def test_related_benefits_share_rows_with_actual_values(self):
        products = []
        for name, count in (('Skyfarer', 3), ('Cloudkeeper', 5), ('Starweaver', 7)):
            includes = '%s badge in chat, the tab list, and above your head\nName colour palette (%d colours)' % (name, count)
            if count > 3:
                includes += '\nSix emotes with /emote'
            if count == 7:
                includes += '\nThree wardrobe presets\nStarweaver Lantern keepsake (decorative)'
            products.append({'includes': includes})
        rows = comparison(products)
        self.assertEqual(len(rows), 5)
        by_label = {r['label']: r['values'] for r in rows}
        self.assertEqual(by_label['Rank badge'], ['Skyfarer', 'Cloudkeeper', 'Starweaver'])
        self.assertEqual(by_label['Name colours'], ['3 colours', '5 colours', '7 colours'])
        self.assertEqual(by_label['Emotes'], ['Not included', '6 emotes', '6 emotes'])
        self.assertEqual(by_label['Wardrobe presets'], ['Not included', 'Not included', '3 presets'])
        self.assertEqual(by_label['Decorative keepsake'], ['Not included', 'Not included', 'Starweaver Lantern'])

    def test_unrecognised_or_changed_copy_is_preserved_without_guessing(self):
        rows = comparison([{'includes': 'Name colour palette (9 colours)\nCustom cosmetic'},
                           {'includes': 'A staff-edited perk\nCustom cosmetic'}])
        by_label = {r['label']: r['values'] for r in rows}
        self.assertEqual(by_label['Name colours'], ['9 colours', 'Not included'])
        self.assertEqual(by_label['Custom cosmetic'], ['Included', 'Included'])
        self.assertEqual(by_label['A staff-edited perk'], ['Not included', 'Included'])
