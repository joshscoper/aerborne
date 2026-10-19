# Recipe workshop

`/recipestudio` opens the in-game recipe catalogue. Designers publish directly with `nerdsmp.studio.recipes`; no reviewer role is required. A loaded, authenticated player profile is still required.

## Author a recipe

1. Publish the result item through `/itemengine` first. Recipe outputs are limited to authored item IDs. Built-in gear, currencies, coin purses, spawners, and vanilla item outputs are not selectable.
2. Choose **New recipe**, enter a stable ID, and select the authored output.
3. Pick up a plain vanilla item from your inventory. Left-click an ingredient slot to sample its material, or drag across several ingredient slots. Your cursor stack is not consumed; the displayed ingredient is a ghost preview. Right-click a grid slot to clear it.
4. Choose the required skill and level, output amount, and whether the recipe will publish enabled. Equipment outputs are exactly one; material outputs cannot exceed their ordinary stack size.
5. Choose **Publish recipe** and confirm. Enabled recipes immediately register at crafting tables. Disabled recipes are saved without registering a craftable result.

The grid trims empty outer rows and columns when registered. Matching can therefore shift within a crafting grid, and Minecraft's ordinary horizontal mirroring applies. Ingredients with metadata, enchantments, names, persistent data, damage, or an RPG item identity are not accepted by these recipes. Automated crafters cannot use authored recipes. Players must be in Survival and meet the selected skill level.

The workshop rejects patterns that collide with registered ordinary-material shaped or shapeless recipes, including built-in recipes and other enabled authored recipes. Choose a distinctive pattern; this tool does not replace vanilla recipes.

## Edit and remove

Open a recipe from the catalogue or use `/recipestudio edit <id>`. Changes remain draft-only until publication. Another designer publishing the same ID invalidates your old draft instead of overwriting their work.

Removing a recipe requires confirmation, stops future crafting, and does not remove already crafted items. Built-in recipes remain read-only; **Built-in recipe guide** opens their existing browsing interface.

`/recipestudio reload` confirms a reload of `content/engine-recipes.yml`. Invalid dependencies, malformed YAML, or conflicting patterns leave the previous runtime recipes active. Keep a backup before external file edits; an invalid externally edited file still needs correction before restart.

Recipe publication swaps the owned runtime recipe keys, saves the strict versioned YAML atomically, then publishes its snapshot. A failed file write restores the previous registry. If registry restoration itself fails, stop crafting and restart before further editing; the error explicitly reports that condition. Other plugins' recipe keys are not removed.

## Safety and QA

Editor recipe previews have all persistent-data markers stripped and cannot be extracted as items. Only native recipe registration receives the authentic item output. Ingredient clicks and drags only copy a material identifier; they do not move samples into a storage inventory. Bottom-inventory ordinary clicks allow you to pick up samples, while shift-transfer, number-key, collect-all, and creative clone actions are blocked.

Unit tests cover grid normalization, gaps and repeated materials, mirrored and shapeless collision matching, strict YAML, dependency validation, stale edits, and failed-write preservation. On staging, also test cursor sampling, multi-slot drag, item-stack recovery when closing, two-designer edits, disabled recipe-book entries, skill gates, shift crafting, registry refresh after item edits, and recipe removal. Do not rely on these authoring tools to automatically balance a recipe's economic or combat value.
