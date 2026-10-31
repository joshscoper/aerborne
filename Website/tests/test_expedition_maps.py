"""The drawn dungeon maps and the notes the page lays over them."""
import json
import unittest
from pathlib import Path

from aerborne.public import _dungeon_map_file

FOLDER = Path(__file__).resolve().parent.parent / "aerborne" / "static" / "img" / "maps"
DUNGEONS = ("first_flight", "hollow_barrow", "thornwood_canopy", "sunken_aqueduct", "gloomforge_mines", "hall_of_wardens", "cinder_keep")


class ExpeditionMapTests(unittest.TestCase):
    def test_every_dungeon_has_a_map_and_its_notes(self):
        for dungeon in DUNGEONS:
            self.assertTrue((FOLDER / (dungeon + ".jpg")).is_file(), dungeon)
            notes = json.loads((FOLDER / (dungeon + ".json")).read_text(encoding="utf-8"))
            self.assertGreater(notes["width"], 200)
            self.assertTrue(notes["pins"], dungeon)
            for pin in notes["pins"]:
                self.assertTrue(0 < pin["x"] < 100 and 0 < pin["y"] < 100, pin)
                self.assertRegex(pin["color"], r"^#[0-9a-f]{6}$")

    def test_a_slug_cannot_reach_outside_the_maps(self):
        self.assertEqual(_dungeon_map_file(FOLDER, "hollow_barrow"), FOLDER / "hollow_barrow.json")
        for slug in ("../secret", "a/b", "..", "A", ""):
            self.assertIsNone(_dungeon_map_file(FOLDER, slug), slug)

    def test_the_secret_companions_stay_off_the_maps(self):
        for dungeon in DUNGEONS:
            text = (FOLDER / (dungeon + ".json")).read_text(encoding="utf-8").lower()
            for name in ("rattlebones", "weepling", "hollow hound", "silkling", "thistlewing", "sproutling"):
                self.assertNotIn(name, text)


if __name__ == "__main__":
    unittest.main()
