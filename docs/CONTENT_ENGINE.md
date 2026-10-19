# Content Studio

Staff can create equipment and encounters in game without writing YAML. The workflow borrows RPG conventions—named items, rarity, item levels, sockets, equipment sets, encounter templates, and readable ability previews—while retaining vanilla Minecraft appearances.

## Items

1. Run `/itemengine` and choose **New from Template**.
2. Choose a weapon, tool, armor, focus, or material template.
3. Enter a stable ID in the private chat prompt, for example `frostguard_blade`.
4. Edit its name, vanilla appearance, rarity, item level, sockets, description, set, and stats.
5. Choose **Tooltip Preview** to inspect the finished item.
6. Choose **Review & Publish**, then **Confirm Publish**.

Names and descriptions are literal text, not formatting scripts. Separate description lines with `|`; enter `clear` to remove the description. Stats that end in “percent” use percentage points: `10` means 10%, not 0.10. A stat value of `0` removes that stat.

Only equipment can have stats, sockets, or set membership. A bow uses projectile damage; a sword uses melee damage. Changing appearance to a different equipment category can require removing incompatible stats first. Appearance and equipment slot are locked after the first publication, and socket capacity cannot shrink. Copy an item into a new ID if its underlying material needs to change.

Item levels describe authored content; they do not automatically grant character experience or introduce an equip requirement. No crafting recipe, shop offer, or drop chance is added merely by publishing an item. Explicitly connect published IDs to your content systems and balance those sources before making them available to players.

### Equipment sets

In the item library, choose **Equipment Sets**, then **New Equipment Set**. Set sizes range from two to six pieces. Each piece-count threshold can have a description and a selection of supported bonuses. At least one threshold with a nonzero supported stat must remain. Publish the set before assigning it to authored equipment.

Built-in sets are listed for reference and can be assigned to authored items. Their definitions cannot be overwritten by the editor.

## Encounters

1. Run `/mobengine` and choose **New from Template**.
2. Choose **Brawler**, **Ranger**, **Guardian**, or **Caster**.
3. Enter a stable encounter ID, for example `frostguard`.
4. Choose its name, vanilla silhouette, normal/elite rank, encounter level, base health, base attack damage, movement speed, defeat experience, and ability.
5. Set the ability radius, power, cooldown, and optional loot profile.
6. Inspect **Encounter Preview**, then review and confirm publication.

The preview is an inventory stat card, not a spawned entity. Health and attack entries are base values; level, rank, and combatant scaling affect live encounters. **Pulse** is a telegraphed area strike. **Heal Self** restores the encounter's health periodically. **None** keeps ordinary attacks.

Choose **Loot Profile** to select an existing staff-authored table. Build and adjust those tables with `/mobloot`; see [RPG zones](RPG_ZONES.md). The priority is the managed zone's assigned table, then an explicit `/mobloot assign mob` override, then the profile selected in this editor. Only one authored table rolls. Remove an old override if you want the editor's selection to control rewards. Profiles still referenced by published encounters cannot be deleted.

After publishing, place the encounter at your feet:

```text
/zone spawn frostwood custom:frostguard 12
```

The zone must be authored and configured separately. Its boundaries, proximity checks, cap, respawn delay, and protection rules still apply. Publishing does not create or enable a zone, edit terrain, move NPCs, or spawn mobs by itself.

Existing live authored encounters retain their saved stat snapshot; newly spawned encounters use the new definition. Creature type is locked after first publication. Bosses and specialized flying/aquatic encounters retain their existing lifecycle and are not converted into generic ground-spawn templates.

## Copying and command shortcuts

The libraries contain built-in and authored content. Authored entries open an editable draft. Built-in items can be inspected and copied into a new ID; reviewed ordinary ground encounters can also be copied. Unsupported specialized encounters remain read-only.

```text
/itemengine templates
/itemengine draft frostguard_blade sword
/itemengine edit frostguard_blade
/itemengine copy frostguard_blade frostguard_sabre
/itemengine sets
/itemengine setdraft frostguard
/itemengine setedit frostguard
/mobengine templates
/mobengine draft frostguard guardian
/mobengine edit frostguard
/mobengine copy frostguard frostguard_captain
```

`/itemengine cancel` or `/mobengine cancel` discards an open draft. **Copy Draft** copies unsaved changes into a new draft. Drafts are deliberately temporary: closing the editor, logging out, or allowing a chat prompt to expire discards unsaved changes. Navigating between editor pages and the library preserves the current draft. Private text inputs expire after 60 seconds; type `cancel` to return without changing that field.

## Permissions and safety

- `nerdsmp.content.items`: item and equipment-set editing.
- `nerdsmp.content.mobs`: encounter editing.
- Existing zone and loot permissions are still required for those separate tools.

Both editor permissions should be restricted to trusted content staff. Every input, mutation, and publication rechecks permission and the authenticated player session. Old pages and prompts cannot modify a newer draft. Concurrent publication of the same item/set, or a changed mob catalog, requires reopening the current definition rather than silently overwriting another staff member's work.

Inventories are previews and controls only: item movement, shift-clicks, number keys, creative cloning, double-clicks, and drags are blocked. Previews are not grants or loot. Definitions are validated and saved before the live catalog is updated. A save failure retains the draft and leaves the previous published snapshot active.

Authored content is stored separately from shipped definitions:

```text
plugins/NerdSMP/content/engine-items.yml
plugins/NerdSMP/content/engine-mobs.yml
```

Keep these files in backups. Existing world regions, NPC placements, account data, and player inventory socket/enchantment state are not reset by editor publication.

## Manual staff playtest checklist

- Open each library, paginate, inspect a built-in entry, and make a copy.
- Create a sword, a bow, a shovel, and armor; inspect tooltips and confirm unsupported stat/material combinations are rejected.
- Create a set, configure two thresholds, publish it, and assign it to equipment.
- Try taking preview items using shift-click, number keys, drag, double-click, and creative middle-click.
- Answer a private text prompt immediately, cancel one, allow one to expire, and disconnect during one; ensure none appears in public chat.
- Have two staff edit the same content; verify stale publication is refused and saved definitions survive restart.
- Publish a normal and elite encounter, assign a loot profile, place both in an authored zone, and check live scaling, ability telegraphs, and exactly one reward roll per defeat.
