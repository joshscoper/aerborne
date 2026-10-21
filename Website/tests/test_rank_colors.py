import unittest

from aerborne.rank_colors import contrast, rank_palette


class RankColorsTest(unittest.TestCase):
    def test_dark_light_and_custom_rank_colors_stay_readable(self):
        for color in ("#E5484D", "#F07A7A", "#777777", "#FFFFFF", "#000000", "#5865F2", "#F4C76B"):
            with self.subTest(color=color):
                palette = rank_palette(color, "#FFFFFF")
                self.assertGreaterEqual(contrast(palette["name"], "#334869"), 4.5)
                self.assertGreaterEqual(contrast(palette["name_light"], "#E8F1FB"), 4.5)
                self.assertGreaterEqual(contrast(palette["ink"], color), 4.5)

    def test_readable_staff_choices_are_preserved(self):
        palette = rank_palette("#F4C76B", "#17243B")
        self.assertEqual(palette["ink"], "#17243B")
        self.assertEqual(palette["name"], "#F4C76B")
