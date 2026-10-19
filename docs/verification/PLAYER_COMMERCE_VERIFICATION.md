# Player commerce verification

Verified and staged on 2026-10-16 using Paper 26.2 build 121 and Java 25.0.4.1. The player server remains stopped.

## Automated results

- Final offline `qualityGate build` passed: 1,146 tests, 1,145 passed, one skipped, no failures or errors across the API and Paper modules.
- Auction tests cover bid escrow, outbid refunds, delta-only self-raises, minimum increments, revision checks, Buy Now, expiry, cancellation, payment replay, lost acknowledgements, failed writes and restart recovery. A hung payment does not prevent unrelated auctions from recovering.
- Market tests cover durable auction venue separation, legacy journal compatibility, fixed-price bypass refusal, settlement retries, preserved delivery metadata and invalid journal refusal.
- Store-profile tests cover additive persistence, canonical account ownership, season separation, stale/concurrent edits, validation and failed saves.
- Remote trade tests retain account, visibility, ignore, mode, cursor, recovery and strict-click guards without world or distance requirements.
- The disposable Paper fixture passed and shut down cleanly. It checks registered commands/aliases, actual Paper gear serialization with improvements/sockets/enchants/coatings, bid funding/refunds, Buy Now seller tax/payout, timed settlement, no-bid returns, journal reopening, interrupted-delivery quarantine, fixed-price purchasing and additive storefront branding. Existing gameplay regression checks passed in the same run.
- Auction financial commands and periodic recovery run through a bounded worker queue. Bukkit inventory work remains on the main thread. Read-only commerce menus reject shift/number/double-click movement and respect earlier event cancellation. Final sale confirmations display the exact item; stale final inventory snapshots cannot be deposited.

## Artifact and preservation

Built, disposable-tested and installed jar SHA-256:

`0DF4D19FE04E9853B89D0ACEF16FD6E0B50049E223BF6FE30A593A0744B2393D`

Installed at `runtime/skyblock-test/plugins/NerdSMP.jar` using `run-test-server.ps1 -Skyblock -PrepareOnly`, without config refresh or server startup. All 26 preexisting plugin-data files, all 87 world files and server.properties remain byte-for-byte unchanged. No reset, NPC placement or terrain generation was performed on the player server.

Backup: `runtime/backups/commerce-update-20261016-200209`. The previous jar, complete plugin data, complete world and server.properties are hash-verified with a manifest and restore notes. Restore economic journals and inventory saves as a consistent set, never as independent rollbacks.

Fixture: `runtime/zone-smoke-20261016-200211`.

Logs: `build/commerce-final-quality.log`, `build/commerce-paper-smoke.log`, `build/commerce-deploy.log`. Deliberate corrupt-data/fault-injection diagnostics and preexisting Windows performance-counter warnings are expected in the fixture log.

## Remaining client acceptance gates

1. With two authenticated accounts on separate islands/worlds, browse `/store`, create a named storefront, list exact gear, buy, collect and verify full-inventory refusal. Confirm existing listings are visible.
2. Exercise `/ah sell`, bid/outbid/self-raise, stale confirmation, Buy Now and no-bid cancellation. Compare real balances and exact item metadata after relogging, including an offline seller and delayed payment while another menu is open.
3. Complete `/trade <player>` across worlds. Change offers, accept both, travel/close/disconnect before commitment, and restart after commitment. Verify both saved inventories and no duplicate recovery.
4. Inspect all menus in the real client at different GUI scales; try shift clicks, hotbar swaps, double clicks, drags, death, teleport and account-session changes.
5. Conduct controlled process-crash/backup-restore drills and multiplayer load profiling before public release. The atomic YAML journal approach still requires scale testing; this update is not a claim of production load certification.

The fixture uses isolated accounts/economies and controlled proxies. It does not claim a real connected player's inventory was saved by a proxy, a two-client trade completed, GUI visuals were visually approved, or the public-release gates above passed.

Player guide: [PLAYER_COMMERCE.md](../PLAYER_COMMERCE.md).
