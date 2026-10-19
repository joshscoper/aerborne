# Aerborne: RPG Skyblock conversion

Direction approved September 28, 2026: **private co-op islands + shared RPG zones**.
This is a staged conversion of the existing plugin, not a cosmetic rename and not
a claim that the full game is finished. Keep the current Paper/Java/Nexo stack.

## Reference research

Reviewed official server material, not ranking sites or unofficial plugin clones:

- [Hypixel's Catacombs release](https://hypixel.net/threads/skyblock-patch-0-8-first-dungeon-the-catacombs.2851991/)
  separates a dungeon hub, party discovery, encounters and equipment rewards.
  Our takeaway: home islands should support an adventure loop, not replace it.
- [Hypixel's Greenhouse release](https://hypixel.net/threads/hypixel-skyblock-0-24-the-greenhouse.6027542/page-5)
  develops farming with its own activity progression and NPC visitors. Our takeaway:
  gathering needs distinct goals and interactions beyond selling stacks.
- [ManaCube's published Skyblock revamp](https://manacube.com/threads/skyblock-revamp-season-9.54019/)
  documents island roles, quest-led border growth and a departure from scoring
  islands by placed valuable blocks. We use earned cooperative milestones, not
  purchased island levels or paid production boosts.

These are design references from specific releases, not claims about current
player counts or exhaustive descriptions of today's servers. Hypixel's old official
wiki now redirects to its closure announcement, so release posts were used instead.
Do not copy server names, maps, item art, dialogue or monetization models.

## Product direction

The island is a workshop and home that grows with its residents. The city connects
players to markets, services and expeditions. Exploration supplies materials for
gear; gear opens tougher encounters; collections and reputation unlock new recipes
and choices. Keep earned-only Marks and cosmetic-only monetization.

| Existing system | Skyblock role | State |
| --- | --- | --- |
| Homestead protection | Private islands and co-op permissions | Integrated through a read-only territory overlay |
| Skills, talents, Adventurer ranks | Personal RPG progression plus shared island milestones | Working; island chapters added |
| Gear, sockets, enchants, alchemy | Build choices and preparation for expeditions | Retained; full loot/recipe rebalance pending |
| Collections and NPC reputation | Long-term crafting and activity goals | Retained; Skyblock supply routes pending |
| Markets, bank, trade, Marks | Player economy centered on the city | Retained; scarcity and sink rebalance pending |
| Distance-based mobs | Difficulty by expedition zone, fixed home baseline | Island baseline fixed; expedition tiers pending |
| Campaigns and factions | Reopen sky routes and unlock group encounters | Existing systems retained; new story/content pending |
| Social and parties | Co-op building plus temporary expedition groups | Existing parties retained; island membership separate |

## Implemented in this pass

Follow-up: [Island showcases and optimization](SKYBLOCK_SHOWCASES.md) adds earned
centerpieces, chapter reveals, display preferences, indexed lookups and HUD diffs.

Latest follow-up: [Island progression, automation and reset](HEARTHSKY_PROGRESSION.md)
adds island XP/talents, stackable spawners and mobs, filtered chunk hoppers,
server-wide supply/demand city vendors, template 2 and a verified full local test
reset. The following foundation notes describe the earlier pass; the linked
record has current behavior, backup details and acceptance limits.

- Dedicated void world with deterministic floating starter templates, a small
  protected bedrock landing, oak tree, water, lava, crops and a starter generator.
  Generation only writes the supplied chunk data, never reads neighboring chunks.
- One owned island per canonical account. A player may additionally join up to
  three co-ops without losing their home, inventory, skills or purchases.
- Six people per co-op including the owner. Owner-only invitations and removals;
  invitations expire after five minutes and reserve seats. Invite replay cannot
  extend expiry. Members have building/storage access, not owner administration.
- Private by default. Public sightseeing is optional and does not grant item pickup,
  build, storage or redstone access. Movement and teleport entry are checked; removed
  visitors in closed islands are moved to the hub on the periodic access check.
- Existing claim protections enforce island permissions, including open storage
  revalidation and automation boundaries. Unassigned cells and unexpanded land are
  reserved. Manual `/claim` commands cannot overwrite island territory.
- Island GUI, directory, roster, invitations, journal, contextual completion,
  quickstart and tips. Java inventory interfaces remain the current client UI.
- Void damage on islands returns players safely instead of deleting their items.
  Island portals are disabled; shared destinations will be reached through city
  routes. Regular deaths retain the grave system, with graves moved off the landing.
- Island mob scaling uses distance zero; allocation position never raises home
  difficulty. Existing party-scaled elite mechanics remain.
- No extra kit on island creation or co-op join. Existing account-bound starter
  delivery is reused. There is no reset/delete command or automatic island rebuild.
- Independent local development runtime; no production migration or world wipe.

### Island progression

Players explicitly certify their trained skills through the journal. Teammates can
cover different requirements. A proof records its contributor and remains part of
the island after that member leaves. No items, Marks or skill XP are consumed.
Unlocks and proofs commit in the same player-file mutation; replay cannot expand twice.

| Chapter | Required skills | Unlocked square area |
| --- | --- | --- |
| Starting island | None | 48 × 48 |
| A Place to Grow | Mining, Farming, Building level 5 | 80 × 80 |
| Tools of the Trade | Smithing, Forestry, Hunting level 10 | 112 × 112 |
| Beyond the Horizon | Fishing, Excavation, Alchemy level 15 | 144 × 144 |

These are first-pass tuning values. Later chapters depend on the future expedition
and resource-access pass; the current starter template is not a complete route to
every skill or recipe. Personal Adventurer XP remains separate from island chapters.

### Generator table

Only actual water/lava stone or cobblestone formation on allocated land rolls ores.
The starter uses a contained generator: mine the block at local `(9,96,4)` in the
island's center chunk. Generator renewal verifies the resulting block next tick
before clearing its consumed-resource marker. Cancelled, replaced, moved or already
harvested results cannot renew a placed ore. Existing job diminishing returns apply.

| Output | Start | Chapter 1 | Chapter 2 | Chapter 3 |
| --- | ---: | ---: | ---: | ---: |
| Cobblestone | 68% | 68% | 68% | 68% |
| Coal ore | 10% | 10% | 10% | 10% |
| Copper ore | 7% | 7% | 7% | 7% |
| Iron ore | 15% | 5% | 5% | 5% |
| Gold ore | 0% | 3% | 3% | 3% |
| Lapis ore | 0% | 7% | 3% | 3% |
| Redstone ore | 0% | 0% | 4% | 3% |
| Diamond ore | 0% | 0% | 0% | 1% |

No offline minions, paid generators or unbounded automatic currency production are
added. Generator throughput and XP still require live balance/load testing.

## Commands and test launch

Run `run-skyblock-test-server.cmd` (or append `-PrepareOnly` to build/stage without
starting). Uses Java 25, pinned Paper 26.2 build 121, licensed local Nexo and existing
content. Runtime: `runtime/skyblock-test`. Address: `127.0.0.1:25566`.
The launcher records EULA acceptance only when the user explicitly agrees.

- `/island` (`/is`, `/skyblock`): overview.
- `/island create`: create once, then travel home; repeated calls reuse it.
- `/island home [owner]`, `/island homes`: own/joined islands.
- `/island directory`, `/island visit <owner>`: public homes.
- `/island invite <player>`, `/island invitations`, `/island accept <owner>`.
- `/island cancel <player>`, `/island decline <owner>`: revoke/decline invites.
- `/island kick <player>`, `/island leave <owner>`: revoke/leave co-op access.
- `/island visitors on|off`, `/island rename <name>`: owner controls.
- `/island journal [owner]`, `/island advance [owner]`: shared milestones.
- `/island showcase [hearth|verdant|arcane|celestial]`: earned display collection.
- `/island performance`: staff-only display budget diagnostics; console supported.
- `/island hub` or `/spawn`: city travel.

Names resolve online or against saved identities; UUIDs refer to **canonical account
IDs**, not arbitrary entity/profile UUIDs. Staff bypass uses `nerdsmp.claim.bypass`.
Protect that permission. It is not a paid perk.

The fresh hub is protected normal terrain, not a generated finished city. Place NPCs
manually, build the city using staff permissions and configure `/setspawn`/warps.
No existing SMP city, player inventory, purchase or world is silently imported into
this isolated runtime. A later production cutover must explicitly choose which
profiles and entitlements to migrate.

## Persistence and compatibility

Island data is stored at `seasons.<season>.skyblock` inside the existing canonical
`player_data/<uuid>.yml`. State includes schema/layout version, stable island UUID,
slot, tier, name, visibility, member set, invitation expiry and milestone proofs.
Unrelated player fields and existing homesteads are untouched.

World metadata is in `skyblock/<season>-world.yml`. It binds layout 1 to the world
name/UUID. Existing unmanaged worlds are refused; missing or mismatched managed
worlds fail startup. A Skyblock initialization failure shuts down the server so the
loaded world cannot remain online without protection. Keep plugin data and world
snapshots consistent, and never change layout constants on a populated world.
Paper 26.2 terrain lives under the configured level directory at
`dimensions/minecraft/nerdsmp_skyblock_<season>/`, not a sibling world folder.
The loader uses the keyed WorldCreator API and verifies the actual storage path.
See [Paper's world-storage changes](https://papermc.io/news/26-1/).

Coordinates use 512-block spacing; templates exist only in center chunks, generated
on demand. Protection looks up a slot directly rather than scanning every island.
The 100,000-slot address space is not a tested concurrency/performance claim.
Local YAML writes remain synchronous. Database island storage and multi-server
distribution are outside this first conversion pass.

For an explicit in-place test, `gameplay.mode: skyblock` enables the system on a
standard deployment; the launchers override this using `-Dnerdsmp.gameplay`.
In-place activation reserves non-island worlds and hides SMP claim commands but
does not convert their builds. Back up first. Do not use it as a public migration.
Stable plugin name, PDC keys and account identities have not been renamed.

## Remaining conversion gates

1. **Playable supply chain.** Rebalance the starter kit, shops, recipes, quests and
   renewable resources; prove a fresh solo player and co-op can reach every chapter.
   Add renewable expedition nodes and make every skill/collection obtainable.
2. **City and expeditions.** Build the floating city, add level-gated zones and route
   menus, replace distance scaling there with encounter tiers. Keep NPC placement
   manual. Add anti-farm limits and explicit reset-region rules.
3. **Party dungeon loop.** Difficulty tiers, telegraphed bosses, earned loot tables,
   personal contribution rules, reconnect recovery and reward replay protection.
4. **Co-op maturity.** Owner succession/transfer, scoped member roles, island chat,
   bans, staff recovery tooling and optional decorative biome/template choices.
   No shared bank until ledger/recovery semantics are designed and tested.
5. **Economy/content audit.** Remove unreachable SMP quests and resource assumptions,
   retune XP/loot inflation and shops, review all permissions/store perks against
   cosmetic-only monetization, resolve earlier inventory/grave recovery blockers.
6. **Release QA.** Java and Bedrock journeys, loaded co-op farms, adversarial grief
   tests, restore drills and explicit production data migration approval.

## Acceptance checklist

- Fresh owner creates twice/relogs/restarts: one island, one starter kit, same slot.
- Two accounts invite/accept/leave/kick; third-account visitor cannot loot or build.
- Open storage then revoke access; test carts, hoppers, pistons, fluids, pets,
  projectiles, bucket capture, grave access and chest edges.
- Try TPA, ender pearls, chorus fruit, respawn, reconnect and portal bypasses into
  closed/unassigned islands. Kicked players must be removed without a global fallback.
- Fall into the void, die beside the landing, block destinations using staff tools,
  disconnect while loading a chunk; verify inventories and landing access.
- Harvest generated ores repeatedly: XP renews per real formation; place/break,
  Silk Touch replacement, piston movement and cancelled formations do not farm XP.
- Contribute different skills from two members, race the last milestone, restart,
  then replay the command: one expansion with no repeated grants.
- Verify generator fluid physics, starter crops/tree regrowth, island menu layout,
  Bedrock interaction parity, market stalls and mob health/damage in a real client.
- Profile loaded generators and many active islands. No performance claim is implied
  by a successful unit build. Existing release blockers remain in the code review.

## Verification - September 28, 2026

- `gradlew.bat --no-daemon qualityGate build`: **395 tests**, zero failures, errors
  or skipped tests. Includes island persistence, co-op authorization, progression
  replay, territory integration, generator provenance and modern dimension paths.
- `run-skyblock-test-server.cmd -PrepareOnly` staged the separate runtime. The final
  restart fix was then rebuilt and copied only after confirming Paper had stopped.
  Final built/staged jar SHA-256:
  `F85DBA8A4D5D00F8A1085C62B0D6712E4F2FDE9FDC5A7F305436C7DF04D8E6AB`.
- Paper 26.2 build 121 / Java 25 startup enabled NerdSMP and Nexo on
  `127.0.0.1:25566`. Nexo generated and uploaded its pack. `/island` and `/is`
  registered; console invocation returned the intended in-game-only response.
- Server-side probes verified the bedrock landing, oak tree, starter cobblestone
  and empty terrain between templates. No player joined; generator harvesting,
  combat, GUI appearance, co-op grief tests and Bedrock parity remain unverified.
- Restart testing found and fixed a legacy world-folder assumption. The safety
  check refused startup instead of adopting or overwriting another world. The
  corrected build reopened the original dimension and passed landing/void probes.
  World UUID remained `fb2867f2-d658-4216-8e2d-c4783baba7c7`; manifest SHA-256 stayed
  `2E14F69E36755C4BEFBE174E6D5488BA8BE11E20AA5CC55A6D9340AAFD3D89A4`.
- The corrected build's final stop saved all four dimensions and exited 0. Test
  force-load tickets were removed. No NerdSMP enable/disable exception or watchdog
  dump occurred in this final run; no Paper process was left running.
- The initial explicit `save-all flush` took about 23 seconds and triggered Paper
  watchdog dumps while waiting on saved-data/region-file I/O. Saving completed and
  the process exited 0. This is not a performance pass: disk latency and save stalls
  must be profiled before release. Windows OSHI/Perflib warnings also remain.
- No SMP world, save or deployed plugin was migrated or replaced. The original SMP
  jar and checked player profile hashes remained unchanged. Only licensed plugin
  files and cached dependencies were reused, plus the explicitly approved EULA
  acceptance. The test dimension is separate and contains no imported accounts.

The server is left stopped after verification. Start with
`run-skyblock-test-server.cmd`, join `127.0.0.1:25566`, then use `/island create`.
This is the island foundation, not completion of the remaining conversion gates.
