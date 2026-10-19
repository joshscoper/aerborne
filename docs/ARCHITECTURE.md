# NerdSMP architecture

## Boundaries

NerdSMP begins as one Paper gameplay process behind one Velocity edge. The proxy authenticates Java accounts, Geyser/Floodgate authenticates Bedrock identities, and the Paper backend is unreachable from the public internet. PostgreSQL is the durable source of truth. Redis and additional gameplay backends are intentionally deferred.

```text
Java TCP ─┐
          ├─ Velocity + Geyser/Floodgate + Tebex ─ Paper ─ PostgreSQL
Bedrock UDP┘                                      │
                                                  └─ Java/Bedrock asset packs
```

The repository contains three Java modules:

- `nerdsmp-api`: platform-neutral IDs, immutable value types, and asynchronous service contracts.
- `nerdsmp-paper`: the gameplay implementation, content loading, listeners, commands, and PostgreSQL adapters.
- `nerdsmp-edge`: the Velocity lifecycle and protected edge channel.

Nexo 1.25+ is the sole Java resource-pack manager on Paper. NerdSMP assigns stable `nerdsmp:` ItemModel keys and retains gameplay/PDC authority; Nexo merges `assets/nexo-pack`, generates the distributable, hosts it, and dispatches it. Scaffolding replaces the former manual Bedrock conversion path, while NexoProxy becomes relevant only after adding another Nexo backend.

## Invariants

- Display names and lore never identify a custom item; namespaced persistent data does.
- Every currency mutation carries a reason and unique idempotency key.
- A balance cannot become negative and no failure creates a ledger entry that can later debit unexpectedly.
- Paid entitlements are isolated from skills, Marks, claims, markets, travel, and combat.
- Java and Bedrock invoke the same services; UI adapters contain no game rules.
- Tab, sidebar, chat, nameplate, and inventory-menu adapters render cached service snapshots and never mutate economy or progression state directly.
- A fresh second platform identity may link to an established account. Two progressed identities are never merged automatically.
- Bukkit world/player/inventory state is touched only on the primary server thread.
- Database work is performed on the bounded database executor.

## Player presentation

`PlayerPresentationService` owns one per-player scoreboard so the sidebar and viewer-specific nameplate teams can coexist. It refreshes asynchronous economy/progression/campaign snapshots every five seconds, caches paid Supporter status for one minute, and returns to the Paper thread before touching players or scoreboards. The same progression snapshot supplies talent effects and the level used when a custom mob spawns. `MenuManager` is a guarded inventory router with symmetric skill cards, three-branch talent trees, confirmation screens, and a linear Adventurer-rank path.

All presentation surfaces use ordinary Adventure components and vanilla interaction controls, so they work through Geyser without custom keybinds. Nexo remains the sole pack manager and can later supply custom menu art without changing menu routing or service contracts.

## Persistence status

PostgreSQL persists accounts, platform identities, balances, immutable economy ledger entries, paid entitlements, seasonal skill XP, levels, talent selections, masteries, and respec cooldowns. Adventurer Level and rank are derived from combined seasonal skill XP. Daily job budgets, order caps, claims, market listings, campaigns, PvP flags, custom-mob ability timers, and grave indices still use development implementations.

Local mode is restart-safe for canonical identity links, Marks and replay keys, seasonal skill/mastery/talent progress, faction standing, claims and capacity, player preferences, and cosmetic entitlements. These namespaces share one atomically rewritten `player_data/<uuid>.yml` document per primary player UUID. Campaign totals and permanent server unlocks use separate atomic server-wide documents because they are not owned by one player.

Sockets and custom enchantments live in authenticated item PDC and survive normal inventory, grave, market, and world persistence. Display names and lore are always regenerated from definitions plus validated socket/enchantment data.

## Compatibility policy

- Pin an exact stable Paper API/build and exact Velocity API in each release branch.
- Update Paper, Velocity, Geyser, Floodgate, CoreProtect, Grim, and resource-pack formats in staging as one compatibility train.
- Do not use NMS, internal CraftBukkit classes, or ProtocolLib for beta features.
- Keep Grim alert-only until custom mob, Bedrock, riptide, block-collision, and boss-arena parity tests produce no actionable false positives.
