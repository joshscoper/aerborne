# Operations runbook

## Local Paper test server

Place your licensed Nexo 1.25+ jar in `local-plugins/`, then run `run-test-server.cmd` from the repository root. It requires JDK 25, binds Paper to `127.0.0.1:25565`, keeps online authentication enabled, verifies Paper build 121 against its published SHA-256 checksum, installs NerdSMP and Nexo, refreshes bundled test content, imports `assets/nexo-pack`, applies the local Nexo and recommended Paper settings, and prompts for explicit EULA acceptance before the first start.

Always type `stop` in the Paper console and wait for shutdown before building or staging another NerdSMP jar. The runner checks both the Paper process and port 25565 and refuses to proceed while the test server is live. Do not hot-replace the jar or use `/reload`; Bukkit plugin classloaders can otherwise lose access to classes that have not been loaded yet.

Use `run-test-server.cmd -PrepareOnly` in CI or when only provisioning the runtime. The generated `runtime/paper-test` directory is ignored by Git. Stop the server with `stop`; do not close the window during world saves.

After joining, use `/menu` to inspect navigation, the sidebar, tab list, nameplates, and chat. `/skills` opens each skill's talent tree and `/adventurer` shows the combined rank path. Use `/nerdadmin xp <player> <skill> <amount>` to test skill, talent-point, and Adventurer-level thresholds. Test mob levels at known distances from each world's spawn; elite and boss health should preserve its current percentage as additional players enter combat. Use `/nerdadmin damage <player>` while the player holds a NerdSMP weapon to inspect its authenticated base damage, gear and rank bonuses, attack cooldown, final hit, and defense. `/nerdadmin set <player> <set-id>` grants a collection; the geode, gem, and tome admin commands test the forge. `/forge` applies an off-hand upgrade to main-hand NerdSMP gear, or merges two matching same-tier gems held one per hand, after a confirmation screen.

Kill standard, elite, and boss hostiles while watching `docs/VANILLA_MOB_DROPS_26.2.md`. Standard purses are occasional, elite purses are guaranteed, and bosses guarantee two unidentified gear rolls plus their listed material reward. A Coin Purse must disappear only after its ledger credit succeeds; replaying a copied purse ID must not issue a second credit. Move behind solid terrain during combat and confirm the mob name, personal overhead bar, and boss bar are no longer visible. An older data-folder `content/drop-tables.yml` is accepted safely: the plugin logs a schema warning and uses its bundled current catalog until the operator merges the new file.

Successful player-caused hits create a short-lived damage number near the target. The display is hidden globally and revealed only to the responsible player. Physical, ranged, fire, frost, arcane, venom, and blast damage use distinct colors; Paper-confirmed critical hits use the gold `CRIT` treatment. Animation timing and the per-player entity cap live under `presentation.damage-displays` in `config.yml`.

Use `/recipes` to review Smithing-gated crafted sets, `/party` and `/pc` for cooperative groups, `/daily` for rotating UTC contracts, and `/playtime` for AFK-filtered seasonal milestones. Local identity links and primary player state are written atomically to `plugins/NerdSMP/player_data/<uuid>.yml`; shared engagement and starter-delivery indices remain under `plugins/NerdSMP`. Production account identities remain canonical through PostgreSQL.

NPC definitions never create world anchors automatically. An operator places each character at their current position with `/npc create <definition>`; `/npc definitions` lists the available definition ids. Right-click a character to talk, use `/npc nearby` to locate them, and use `/reputation` to inspect standing and price rates. Place the three faction quest givers explicitly with `/npc create cato_flint`, `/npc create lyra_senn`, and `/npc create garrick_mott`. Their boards rotate daily, weekly, and monthly work and expose the next eligible chapter of that faction's permanent three-part story; activity counts only after acceptance and rewards are claimed back at the same giver. Operators can use `/npc list`, `/npc movehere <instance>`, `/npc remove <instance>`, `/npc respawn`, `/npc reload`, and `/npc reputation <player> <faction> <amount>`. NPC anchors, dialogue flags, contract progress, and cooldowns are written atomically under `plugins/NerdSMP`; local faction standing lives in the player's UUID file and production reputation rewards use the PostgreSQL ledger.

Campaign contributions and fixed start/end dates persist by season in `campaign-state.yml`. Legacy totals migrate once into the current season; the original values remain as a historical snapshot. Reaching `first-ripple`, `broken-seal`, and `rift-opened` unlocks ambient world events, Nether travel, and End travel respectively. Seasonal unlock state is stored in `server-progression.yml`; staff may bypass realm gates with `nerdsmp.progression.bypass` while testing.

Supply stores use left-click to buy and right-click to sell one listed bundle. Vendor sales accept only exact unmodified commodity items, use a lower payout than the corresponding purchase price, and enforce persistent per-account product quotas that reset at 00:00 UTC. Reputation discounts purchases and modestly improves payouts. Sale counters are stored in `npc-shop-sales-<season>.yml`. `/repairall` and Torren Pike's dialogue open the same confirmation screen; the quote covers damaged inventory, hotbar, armor, and off-hand items, preserves all item data, and charges the configured Marks sink only after revalidating the inventory.

Players use `/report <player> <reason>` to create a private case. Grant `nerdsmp.staff` to investigators and `nerdsmp.staff.moderate` only to staff allowed to mutate player state. `/staff` opens the dashboard; console-safe commands cover reports, audit, histories, notes, warnings, timed mutes, freezes, kicks, and resolution. Kick actions in the GUI require a second confirmation. `staff-management.yml` is atomically rewritten on each state mutation and shutdown; it keeps reports, case histories, active restrictions, and the append-only audit entries used by this local vertical slice. Preserve this file through seasonal wipes and backups.

## Production topology

Use a US Central Linux host with approximately eight high-clock performance cores, 24 GB RAM, 1 TB NVMe storage, and provider-level DDoS protection. Run authenticated Java-only Velocity, Paper, and PostgreSQL on private container networks. Expose only Java TCP 25565. Do not expose a Bedrock UDP listener.

Copy the templates in `deploy/` into the runtime volumes. Generate independent random PostgreSQL, Velocity forwarding and Tebex secrets. Never commit `.env`, `forwarding.secret`, `key.pem`, store secrets, database dumps, or player exports.

## First deployment

1. Install the exact tested Paper and Velocity artifacts; the local baseline is Paper 26.2 build 121. Validate upgrades in staging before changing production.
2. Enable online mode on Velocity. Do not install Geyser, Floodgate or Scaffolding; Java is the only supported client.
3. Install licensed Nexo 1.25+ on Paper, merge `deploy/nexo/settings.production.fragment.yml`, and use Nexo as the only Java pack dispatcher. Install NexoProxy on Velocity only when multiple Nexo backends exist.
4. Install LuckPerms, CoreProtect, Tebex, and Grim. Configure Grim for alerts with automatic punishments disabled.
5. Build NerdSMP with JDK 25 and copy the Paper and edge artifacts into their respective plugin directories.
6. Configure Velocity modern forwarding and ensure the Paper backend is firewall-inaccessible from outside the container network.
7. Start PostgreSQL, select `storage.mode: postgres`, and confirm migration version 4.
8. Start Skyblock with a complete backup. The separate manifested expedition dimension generates Hearthport and gathering zones; existing worlds and NPC anchors are not imported or rewritten. Follow docs/RELEASE_HUB.md for manual NPC placement and map QA.
9. Generate and upload the merged Java pack through Nexo and visually inspect item models.
10. Test authenticated Java login, packs, commands, disconnect/reconnect, duplicate sessions and blocked backend access. There is no account-link command.
11. Keep the whitelist enabled until the beta launch gate is signed.

## Deployment safety

- Build on CI, deploy exact artifact checksums, and retain the preceding release.
- Apply database migrations before enabling new code; migrations are forward-only in production.
- Stop player joins, wait for asynchronous writes, save all worlds, then restart. Do not hot-reload Paper plugins.
- Upgrade Paper, Velocity and Nexo on staging with cloned, anonymized data first.
- Run `/spark profiler start --timeout 600` during a realistic event test; require p95 MSPT below 40 ms at 30 players.

## Backup and recovery

- Archive PostgreSQL WAL continuously and take daily logical/physical snapshots.
- Coordinate world snapshots every six hours after a server save barrier.
- Encrypt offsite backups and retain 7 daily, 4 weekly, and 9 monthly restore points.
- Target a 15-minute database RPO, six-hour world RPO, and four-hour RTO.
- Perform and record a full isolated restore before beta, before each season, and quarterly.

## Alerts

Alert on sustained MSPT above 40, tick time above 50 ms, heap or GC pressure, crash loops, failed database health checks, pool exhaustion, proxy authentication failures, entitlement command failures, backup age, disk above 80%, and unexpected backend-port reachability.
