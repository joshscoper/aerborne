# Loot Studio

Open `/lootstudio` in game, or choose **Loot** from the development team's Content Studio. Designers with `nerdsmp.loot.admin` can publish directly; there is no mandatory review role.

## Author a profile

1. Choose **New Loot Profile** and enter an ID such as `crypt_rewards`.
2. Choose **Add Reward**, then **Vanilla Item**, **Item Library**, or **Coin Purse**.
3. Give the reward a unique ID. Search the paged item catalog to select an actual source.
4. Click the reward to set its drop percentage, fixed quantity or inclusive minimum/maximum range. Published gear also supports zero to three affixes and unidentified drops.
5. Open **Balance Preview** to inspect average item quantities, average purse Marks and maximum per-kill budgets. This is an analytical preview: it never rolls, grants or credits rewards.
6. Choose **Publish**, read the summary and confirm. The validated file is committed before the live profile changes.
7. Open **Profile Settings → Assignments** to assign the published profile to a zone, a vanilla mob type or an authored mob. Assigning and unassigning always require confirmation.

Each reward has its own independent chance. A 12.5% entry does not compete with another entry. Each defeated mob rolls once; island stack counts do not multiply a roll. Marks are delivered as authenticated coin purses on player pickup, not credited by the editor or collected by hoppers.

## Modes and precedence

New profiles are **Additive**, so ordinary rewards remain. **Replace** suppresses normal item drops, gear rolls, coin-purse rolls and mob-specific item rewards. XP and boss participation rewards remain. Switching a draft to Replace requires confirmation, then publishing requires another confirmation. An empty replacement profile deliberately awards no normal item loot.

A zone assignment takes priority over a mob assignment. Authored mobs may also have their own loot profile selected in Mob Studio; use the established zone/mob fallback rules to decide where to bind a profile. Assignments use published rewards, never an unpublished draft. Changes to a bound profile affect subsequent kills immediately.

## Draft safety

The editor has private, account-bound drafts and read-only inventory icons. **Copy Profile** copies rewards and mode, not assignments. Browsing keeps the active draft; **Resume** reopens it. Replacing or discarding an unpublished draft requires confirmation. Logging out or shutting down discards the in-memory draft; published profiles remain on disk.

If another designer changes the published profile or its assignment while a confirmation is open, the stale change is rejected rather than overwritten. A failed save leaves the earlier live profile active and keeps the draft available. Delete requires confirmation and is refused while any zone, mob assignment or authored mob still references the profile.

Limits remain enforced in every editor path: 128 profiles, 32 rewards per profile, 256 maximum item quantity per kill and 10,000 maximum Marks per kill. Only published item IDs are selectable; affixes and unidentified state are only valid on gear.

## Commands

- `/lootstudio` or `/lootstudio browse` — open the profile library.
- `/lootstudio create <id>` — start a new profile draft.
- `/lootstudio edit <id>` — edit a published profile.
- `/lootstudio copy <source> <new-id>` — copy rewards into a new draft.
- `/lootstudio resume` — reopen your current draft.
- `/lootstudio cancel` — confirm discarding your draft.

The existing `/mobloot` command editor remains available for console and precise scripted changes. Both editors share `plugins/NerdSMP/mob-loot.yml`; neither replaces the shipped default drop tables. No profiles, mobs, spawn points or player rewards are automatically created by opening the editor.
