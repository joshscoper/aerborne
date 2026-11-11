# Aerborne client asset prompt

Paste everything below the line into ChatGPT. It produces the art for native menus, so they stop
looking like reskinned chests. Ask for the sheets a few at a time if it struggles. Then zip them and
hand the zip over; `tools/slice_assets.py` will be extended to cut them up.

---

You are making original pixel-art UI assets for **Aerborne**, a Minecraft sky-island RPG (floating islands,
airships, wind, warm lantern light). Match this style exactly in every sheet:

- **Palette:** deep navy panels (#0D1A2B, #17243B, #283752), gold trim (#F4C76B with #B8862E shading),
  cloud white text areas (#F4F9FF), sky-blue accents (#8CE8ED), soft violet for magic (#C59AFA).
- **Pixels:** crisp pixel art drawn on a **16-pixel art grid, shown at 4x** (every art pixel is a 4x4 block
  in the PNG). No anti-aliasing, no blur, no gradients except stepped pixel shading, no drop shadows
  outside the cell.
- **Background:** fully transparent everywhere outside the art.
- **No text or letters anywhere** unless a cell says so. Nothing copied from Minecraft, Hypixel,
  Wynncraft or any other game.
- **Layout:** each sheet is a grid of equal cells, left to right, then top to bottom, in exactly the order
  listed, with no gaps, labels or borders between cells. Leave any unused cells transparent.
- **Size:** each PNG is exactly the size given. Keep every piece inside its own cell.

## Sheet 1: `window_kit.png`, 1024x1024, a 4x4 grid of 256x256 cells

Every frame must be nine-slice friendly: corners decorated, edges a repeating pattern, centre plain.

1. Large window: navy body, gold frame, a title bar across the top quarter with small wing ornaments at its ends
2. Inner inset panel: darker navy, thin gold-brown edge, for lists and item grids
3. Tab, active: top-rounded, gold edge, joins the window below it
4. Tab, inactive: dimmer, no gold
5. Tab, hover: between the two
6. Side tab, active: a vertical tab for the left side of a window
7. Side tab, inactive
8. Button, primary: gold
9. Button, secondary: navy with gold edge
10. Button, danger: deep red with gold edge
11. Button, pressed: primary, pushed in by one art pixel
12. Button, disabled: grey-navy, flat
13. Search box: inset field with a small magnifier on the left
14. Search box, focused: same, with a sky-blue glow edge
15. Scrollbar track (draw it vertical, centred in the cell) with a separate thumb beside it
16. Divider: a horizontal gold line with a centred diamond

## Sheet 2: `controls.png`, 512x256, an 8x4 grid of 64x64 cells

1. Checkbox, off
2. Checkbox, on (gold tick)
3. Toggle switch, off
4. Toggle switch, on
5. Slider track (horizontal)
6. Slider knob
7. Dropdown arrow
8. Close button (an X)
9. Back arrow
10. Left page arrow
11. Right page arrow
12. Up chevron
13. Down chevron
14. Plus
15. Minus
16. Padlock, closed
17. Padlock, open
18. Pin (track this)
19. Star, filled (favourite)
20. Star, empty
21. Magnifier
22. Gear (settings)
23. Bell (notifications)
24. Question mark in a circle (help)
25. Filter funnel
26. Sort arrows
27. Refresh arrows
28. Eye (show)
29. Crossed eye (hide)
30. Trash can
31. Envelope (mail)
32. Hourglass

## Sheet 3: `slots.png`, 512x256, an 8x4 grid of 64x64 cells (one 18-pixel item slot drawn large)

1. Slot, normal
2. Slot, hover
3. Slot, selected
4. Slot, locked (dim, with a tiny padlock in the corner)
5. Slot, disabled
6. Rarity frame: Common (grey)
7. Rarity frame: Uncommon (green)
8. Rarity frame: Rare (blue)
9. Rarity frame: Epic (purple)
10. Rarity frame: Legendary (gold)
11. Rarity frame: Mythic (crimson)
12. Rarity frame: Relic (teal with a faint shimmer)
13. Gem socket, empty
14. Gem socket, filled glow
15. Equipment silhouette: helmet
16. Equipment silhouette: chestplate
17. Equipment silhouette: leggings
18. Equipment silhouette: boots
19. Equipment silhouette: main hand weapon
20. Equipment silhouette: off hand
21. Equipment silhouette: ring
22. Equipment silhouette: necklace
23. Equipment silhouette: trinket
24. Equipment silhouette: belt
25. Equipment silhouette: cloak
26. Equipment silhouette: tool
27. Item locked marker (small padlock badge)
28. New item marker (small sparkle badge)
29. Quest item marker (small scroll badge)
30. Soulbound marker (small chain badge)
31. Stack count badge background
32. Empty, leave transparent

## Sheet 4: `bars.png`, 1024x512, a 2x8 grid of 512x64 cells (long horizontal bars)

1. Bar frame: gold-trimmed empty bar
2. Health fill: red with lighter top row
3. Absorption fill: gold
4. Adventurer XP fill: sky blue
5. Skill XP fill: green
6. Boss health fill: violet
7. Boss bar frame: wider ornate frame with small wings
8. Cast or charge fill: white-blue
9. Party member frame: small portrait box at the left, two thin bars at the right
10. Toast frame: a short wide banner for pop-ups
11. Level up toast: banner with small rising sparkles
12. Quest complete toast: banner with a scroll motif
13. Rare drop toast: banner with a crimson burst
14. Achievement toast: banner with a laurel
15. Dialogue name tab: a small banner that sits on top of a panel
16. Tooltip frame: a slim navy box with a gold line

## Sheet 5: `icons_skills.png`, 512x256, an 8x4 grid of 64x64 icons

Mining (pickaxe), Forestry (axe and leaf), Farming (wheat sheaf), Fishing (rod and fish), Hunting (bow and
arrow), Excavation (shovel and bone), Smithing (anvil and hammer), Alchemy (round flask), Building (brick
and trowel), Cooking (pot with steam), Enchanting (open glowing tome). Then the Adventurer path (compass
rose), Health (heart), Defense (shield), Strength (fist), Critical chance (crossed swords with a spark),
Critical damage (cracked sword), Speed (winged boot), Luck (four-leaf clover), Wisdom (owl eye), Marks
(currency: a gold coin stamped with a wing), Gems (cut gemstone). Leave the rest transparent.

## Sheet 6: `icons_menus.png`, 512x512, an 8x8 grid of 64x64 icons

Party (three figures), Bank (vault door), Trade (two hands exchanging), Leaderboards (podium), Expeditions
(stone gate with light), Cosmetics (hat and feather), Fast travel (airship), Forge (anvil with sparks),
Arcane forge (anvil with violet glow), Kitchen (pan), Repair (hammer and wrench), Campaign (banner),
Votes (ballot box), Homestead (house with fence), Identification (magnifier over an item), Skill lamp
(oil lamp), Mail (envelope), Friends (two figures with a heart), Guild (crest), Daily reward (gift box),
Boosters (rising arrow), Titles (ribbon), Codex or bestiary (book with a claw), Recipes (book with a
spoon), Storage (chest stack), Map (folded map), Waypoints (pin), Settings (gear), Help (question mark),
Events (calendar), Achievements (trophy), Profile (portrait). Leave the rest transparent.

## Sheet 7: `icons_map.png`, 512x256, an 8x4 grid of 64x64 map markers (readable when shrunk to 16x16)

Player arrow (pointing up, white with navy outline), Party member dot, Waypoint pin, Death marker
(small skull), Quest objective (gold diamond), Quest giver (gold "!"; text allowed), Quest ready (gold "?";
text allowed), Merchant (coin bag), Bank (vault), Auction house (gavel), Flight point (wing), Expedition
gate (arch), Town (tower), Island home (house on an island), Dig site (shovel), Fishing spot (hook), Boss
(horned skull), Dungeon entrance (stairs down), Crafting station (anvil), Healer (cross), Spawn point
(beacon), Unknown or undiscovered (fog swirl). Leave the rest transparent.

## Sheet 8: `icons_effects.png`, 512x256, an 8x4 grid of 64x64 status icons, each in a small round badge

Buffs in a gold badge: Regeneration, Speed, Strength, Resistance, Fire resistance, Haste, Water breathing,
Night vision, Luck, Absorption, XP booster, Marks booster. Debuffs in a red badge: Poison, Wither,
Slowness, Weakness, Mining fatigue, Blindness, Hunger, Burning, Bleeding, Frozen. Leave the rest transparent.

## Sheet 9: `trees_and_quests.png`, 1024x512, an 8x4 grid of 128x128 cells

Skill tree node: locked, available, unlocked, maxed (gold burst). Large keystone node: locked, unlocked.
Tree connectors: straight horizontal, straight vertical, corner, T-junction, cross (each glows sky-blue
when unlocked: draw an unlit and a lit version of each). Quest icons: main story (crown on a scroll), side
quest (scroll), daily (sun on a scroll), ready to turn in (scroll with a gold check), locked (scroll with a
padlock), failed (torn scroll), tracked (scroll with a pin). Leave the rest transparent.

## Sheet 10: `screens.png`, 2048x1024, a 2x1 grid of 1024x1024 cells

1. World map frame: an ornate gold frame around a transparent centre, with a compass rose in one corner
   and small airship and cloud ornaments in the border
2. Quest book spread: an open leather-bound book seen flat, two parchment pages with plenty of empty
   space, gold corner fittings

## Sheet 11: `minimap.png`, 512x256, a 4x2 grid of 128x128 cells

1. Round minimap frame (transparent centre)
2. Square minimap frame (transparent centre)
3. Player cursor: an arrow pointing up, for the map centre, white body, navy outline, gold tip
4. Compass strip end cap, left
5. Compass strip end cap, right
6. North marker (a small gold "N"; text allowed)
7. Tracked waypoint marker for the compass strip
8. Leave transparent
