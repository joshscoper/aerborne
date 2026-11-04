"""Compare feature values rather than treating each rank's wording as a new feature."""
import re

NUMBERS = {'one': '1', 'two': '2', 'three': '3', 'four': '4', 'five': '5', 'six': '6', 'seven': '7'}
FEATURES = [('badge', 'Rank badge'), ('colours', 'Name colours'), ('emotes', 'Emotes'),
            ('presets', 'Wardrobe presets'), ('keepsake', 'Decorative keepsake')]
# One plain line under each feature, so nobody has to guess what it means.
HINTS = {'badge': 'Shown in chat, the tab list and above your head', 'colours': 'Pick the colour of your name',
         'emotes': '/emote wave, cheer, dance and more', 'presets': 'Save whole looks and swap in one click',
         'keepsake': 'A glowing lantern for your island'}


def comparison(products):
    values, extras = [], []
    for product in products:
        features = {}
        for line in product['includes'].splitlines():
            line = line.strip()
            if not line:
                continue
            badge = re.fullmatch(r'(.+?) badge in chat,? the tab list,? and above your head', line, re.I)
            colours = re.fullmatch(r'Name colou?r palette \((\d+) colou?rs?\)', line, re.I)
            emotes = re.fullmatch(r'(\d+|one|two|three|four|five|six|seven) emotes?(?: with /emote)?', line, re.I)
            presets = re.fullmatch(r'(\d+|one|two|three|four|five|six|seven) wardrobe presets?', line, re.I)
            keepsake = re.fullmatch(r'(.+?) keepsake \(decorative\)', line, re.I)
            if badge:
                features['badge'] = badge.group(1)
            elif colours:
                features['colours'] = colours.group(1) + ' colours'
            elif emotes:
                count = NUMBERS.get(emotes.group(1).lower(), emotes.group(1))
                features['emotes'] = count + ' emotes'
            elif presets:
                features['presets'] = NUMBERS.get(presets.group(1).lower(), presets.group(1)) + ' presets'
            elif keepsake:
                features['keepsake'] = keepsake.group(1)
            else:
                if line not in extras:
                    extras.append(line)
                features[line] = 'Included'
        values.append(features)
    rows = []
    for key, label in FEATURES + [(extra, extra) for extra in extras]:
        if any(key in value for value in values):
            rows.append({'label': label, 'hint': HINTS.get(key, ''), 'values': [value.get(key, 'Not included') for value in values]})
    return rows
