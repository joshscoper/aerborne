"""Draw the menu icons (16x16) by hand, in the same palette and lighting as the skill icons.

Usage: python -P -E tools/make_menu_icons.py <assets/aerborne_client dir>

Writes textures/gui/sprites/ui/menu_icons/<name>.png. Shorter drawings are centred in the 16x16 square.
"""
import pathlib
import sys

from PIL import Image

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from make_skill_icons import P  # noqa: E402  (same palette)

ICONS = {
    "island": [
        "......kk........",
        ".....kEEk.......",
        "....kEeEEk......",
        "....kEEEEk......",
        ".....kkWk.......",
        "......kWk.......",
        ".kkkkkkWkkkkkk..",
        "kEeeeeeeeeeeeEk.",
        "kEEEEEEEEEEEEEk.",
        ".ktttttttttttk..",
        "..kTTTTTTTTTk...",
        "...kTTTTTTTk....",
        "....kkTTTkk.....",
        "......kkk.......",
    ],
    "party": [
        "....kkk...kkk...",
        "...khhhk.khhhk..",
        "...khxhk.khxhk..",
        "...khhhk.khhhk..",
        "....kkk...kkk...",
        "..kkBBBkkkPPPkk.",
        ".kBBBBBkkPPPPPk.",
        ".kBbBBBkkPpPPPk.",
        ".kBBBBBkkPPPPPk.",
        ".kBBBBBkkPPPPPk.",
        ".kNNNNNkkqqqqqk.",
        "..kkkkk..kkkkk..",
    ],
    "friends": [
        ".....kk.kk......",
        "....krrkrRk.....",
        "....krrrrRk.....",
        ".....krrRk......",
        "......kRk.......",
        "....kkk...kkk...",
        "...khhhk.khhhk..",
        "...khhhk.khhhk..",
        "....kkk...kkk...",
        "..kkBBBk.kPPPkk.",
        ".kBBbBBkkPpPPPk.",
        ".kBBBBBkkPPPPPk.",
        "..kkkkk..kkkkk..",
    ],
    "settings": [
        "......kkkk......",
        "...kk.kSSk.kk...",
        "..kSSkkSSkkSSk..",
        "..kSsSSSSSSsSk..",
        "...kSSSkkSSSk...",
        ".kkkSSk..kSSkkk.",
        ".kSSSk....kSSSk.",
        ".kSSSk....kSSSk.",
        ".kkkSSk..kSSkkk.",
        "...kSSSkkSSSk...",
        "..kSdSSSSSSdSk..",
        "..kSSkkSSkkSSk..",
        "...kk.kSSk.kk...",
        "......kkkk......",
    ],
    "help": [
        ".kkkkkk.kkkkkk..",
        "kBhhhhhkhhhhhBk.",
        "kBhhGGhkhhhhhBk.",
        "kBhGhhGkhHHHhBk.",
        "kBhhhGhkhhhhhBk.",
        "kBhhGhhkhHHHhBk.",
        "kBhhhhhkhhhhhBk.",
        "kBhhGhhkhHHHhBk.",
        "kBhhhhhkhhhhhBk.",
        "kBkkkkkkkkkkkBk.",
        "kNBBBBBkBBBBBNk.",
        ".kkkkkkkkkkkkk..",
    ],
    "quests": [
        "..kkkkkkkkkkk...",
        ".khhhhhhhhhhhk..",
        ".kHkkkkkkkkkHk..",
        "..khhhhhhhhhk...",
        "..khHHHHHHhhk...",
        "..khhhhhhhhhk...",
        "..khHHHHHhhhk...",
        "..khhhhhhhhhk...",
        "..khHHHHHHhhk...",
        "..khhhhhhhRhk...",
        ".kHkkkkkkkkkHk..",
        ".khhhhhhhhhhhk..",
        "..kkkkkkkkkkk...",
    ],
    "achievements": [
        "..kkkkkkkkkkkk..",
        ".kgGGGGGGGGGGok.",
        "kGkgGGGGGGGGokGk",
        "kGkgGGGGGGGGokGk",
        ".kGkGGGGGGGGkGk.",
        "..kkoGGGGGGokk..",
        "....koGGGGok....",
        ".....koGGok.....",
        "......kGok......",
        "......kGok......",
        "....kkoGGokk....",
        "...kgGGGGGGok...",
        "...kkkkkkkkkk...",
    ],
    "leaderboards": [
        "......kgGk......",
        ".....kgGGGk.....",
        "......kGGk......",
        ".....kkkkkk.....",
        ".....kgGGok.....",
        ".....kGGGok.....",
        ".kkkkkGGGokkkkk.",
        ".ksssSkGGokttTk.",
        ".kssSSkGGokttTk.",
        ".kssSSkGGokttTk.",
        ".kssSSkGGokttTk.",
        ".kkkkkkkkkkkkkk.",
    ],
    "expeditions": [
        "....kkkkkkkk....",
        "..kkSSSSSSSSkk..",
        ".kSSdkkkkkkdSSk.",
        ".kSdkccccccckdk.",
        "kSSkcccxxccckSSk",
        "kSdkccxcccccckdk",
        "kSdkccccccccckdk",
        "kSdkcCcccccCckdk",
        "kSdkCCccccCCckdk",
        "kSdkCCCccCCCckdk",
        "kSdkkkkkkkkkkkdk",
        "kkkkkkkkkkkkkkkk",
    ],
    "trade": [
        "......kkkk......",
        ".....kwWWk......",
        "......kkk.......",
        "....kkwwwkk.....",
        "...kwwwwwwWk....",
        "..kwwwGGwwwWk...",
        "..kwwGggGwwWk...",
        ".kwwwGggGwwwWk..",
        ".kwwwwGGwwwwWk..",
        ".kwwwwwwwwwWWk..",
        "..kWWWWWWWWWk...",
        "...kkkkkkkkk....",
    ],
    "bank": [
        ".kkkkkkkkkkkkkk.",
        ".kSSSSSSSSSSSdk.",
        ".kSkkkkkkkkkSdk.",
        ".kSkdddddddkSdk.",
        ".kSkddkkkddkSdk.",
        ".kSkdkGGGkdkSdk.",
        ".kSkdkGgGkdkSdk.",
        ".kSkdkGGGkdkSdk.",
        ".kSkddkkkddkSdk.",
        ".kSkdddddddkSdk.",
        ".kSkkkkkkkkkSdk.",
        ".kSSSSSSSSSSSdk.",
        ".kkkkkkkkkkkkkk.",
    ],
    "travel": [
        "..........kkk...",
        "........kkxxk...",
        "......kkxxsxk...",
        "....kkxxsxxSk...",
        "...kxxsxxxSSk...",
        "..kxxxxxxSSk....",
        ".kxxxxxxSSdk....",
        ".kssxxxSSdk.....",
        "..kSSSSSdk......",
        "...kkkkkk.......",
    ],
    "cosmetics": [
        ".........kk.....",
        "........kpPk....",
        ".......kpPk.....",
        "......kpPk......",
        "....kkkkkkkk....",
        "...kBBBBBBBBk...",
        "...kBbBBBBBBk...",
        ".kkkGGGGGGGGkkk.",
        "kBBBBBBBBBBBBBNk",
        ".kkkkkkkkkkkkkk.",
    ],
    "mail": [
        ".kkkkkkkkkkkkkk.",
        ".khkhhhhhhhhkhk.",
        ".khhkhhhhhhkhhk.",
        ".khhhkhhhhkhhhk.",
        ".khhhhkRRkhhhhk.",
        ".khhhhhRRhhhhhk.",
        ".kHHHHHHHHHHHHk.",
        ".kkkkkkkkkkkkkk.",
    ],
    "character": [
        ".....kkkkkk.....",
        "...kksssssSkk...",
        "..ksssssssSSdk..",
        "..kssSSSSSSSdk..",
        ".kssSkkkkkkSddk.",
        ".ksSkckkkkckSdk.",
        ".ksSkkkkkkkkSdk.",
        ".kSSkkkkkkkkSdk.",
        ".kSSSSdkkdSSSdk.",
        ".kSSSSdk.kdSSdk.",
        "..kkkkk...kkkk..",
    ],
    "map": [
        ".kkkkkkkkkkkkkk.",
        ".khhhkHhhhkHhhk.",
        ".khRhkHhhhkHhhk.",
        ".khhRkHhhhkHhhk.",
        ".khhhkRhhhkHhRk.",
        ".khhhkHRhRkHRhk.",
        ".khhhkHhRhkRhhk.",
        ".khhhkHhhhkHhhk.",
        ".khhhkHhhhkHxRk.",
        ".khhhkHhhhkHRxk.",
        ".kkkkkkkkkkkkkk.",
    ],
}


def draw(rows):
    width = max(len(r) for r in rows)
    assert width <= 16 and len(rows) <= 16, (width, len(rows))
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    top = (16 - len(rows)) // 2
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y + top] = P[ch] + (255,)
    return img


def main(assets):
    root = pathlib.Path(assets) / "textures" / "gui" / "sprites" / "ui" / "menu_icons"
    root.mkdir(parents=True, exist_ok=True)
    for name, rows in ICONS.items():
        draw(rows).save(root / (name + ".png"))
    print("drew", len(ICONS), "menu icons")


if __name__ == "__main__":
    main(sys.argv[1])
