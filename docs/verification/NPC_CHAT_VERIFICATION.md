# NPC chat dialogue verification

Player conversations now use private Adventure chat components instead of a 54-slot dialogue inventory. NPC editing, stores, contracts and item-handling services remain inventory-based.

## Manual client checks

1. Right-click a manually placed NPC. Confirm no dialogue inventory opens and only that player receives the name, title, speech and numbered replies.
2. Open chat, hover replies and follow two branches. Check costs/rewards and locked reputation, Adventurer-level, story-flag and cooldown requirements. Use `/npc reply 1` as well as a click.
3. Re-click an old reply after choosing a branch. Try another player's token, a locked index, an invalid number, and a response while an operation is pending. None may grant rewards or open services.
4. Leave with `/npc bye`, wait two minutes, walk more than ten blocks away, hide behind a wall, die, disconnect, open another menu, and reload or publish dialogue. Verify old links are inert. An old Leave link must not end a newer conversation.
5. Open each service from its NPC: shop, bank, identifier, repair, contracts, archive, fishery, alchemy, forge and enchanting. Use the shop/contract Back control and confirm it returns to fresh chat.
6. Exercise an audited one-time/cooldown reward and a Marks charge. Check the existing progress/economy receipts; duplicate clicks must not repeat the operation. Leaving during a started operation does not undo it.
7. Send ordinary public and private chat during a conversation; confirm it still follows normal channel/settings rules. NPC speech must not be broadcast to other players.

Automated results and the staged artifact are recorded below after verification. This change does not make existing multi-service dialogue effects transactional as a group.

## Automated verification

The final combined quality gate and build passed: 999 tests, 998 passed, one skipped, and no failures or errors. Build output is recorded in `build/island-workshop-final-quality.log`.

The regression checks cover player/account-bound nonces, response replay, invalid indices, expiry, capacity limits, stale completion isolation, immutable session snapshots, clickable/locked reply presentation, literal authored text, copy-to-runtime text preservation and Unicode-safe wrapping. The exact final artifact passed the disposable Paper fixture, including Adventure chat output and authored NPC content checks. Output is recorded in `build/island-workshop-paper-smoke.log`; the fixture is `runtime/zone-smoke-20261012-140027`.

A real-client pass through the manual checklist remains necessary to verify final presentation and two-player privacy. Automated fixtures are not a substitute for those client checks.

## Installation record

The built, tested and installed plugin JARs have matching SHA-256: `DDF090112411C8A9C2D63A80925767EB558ADB4BD09D15BD47314B537CA6A9F7`.

The pre-update backup is `runtime/backups/island-workshop-update-20261012-135400`: the prior plugin JAR, all 26 existing plugin-data files and all 87 world files. Staging with `run-test-server.ps1 -Skyblock -PrepareOnly` succeeded; output is recorded in `build/island-workshop-deploy.log`.

All 26 existing plugin-data files and all 87 world files retained identical hashes and file counts after staging. Existing manually placed NPCs, dialogue receipts, player progression and shop state were not reset. The main test server remains stopped; the final process check found no Paper processes.
