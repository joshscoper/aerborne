# Arcane enchanting rollout

This update makes the existing Arcane Forge workflow available in fresh installs: random table
offers, the Arcane Codex, gem research, chosen enchantments, attunement, deterministic corruption,
cleansing and stabilization. Existing enchantments, tomes, item identifiers, recipes, prices and
Smithing unlocks are retained. Arcane requirements use the higher of Enchanting and Smithing.

## Existing servers

Existing explicit disabled settings are not overwritten during startup. A staff member with
`nerdsmp.forge.admin` can run:

1. `/forgeadmin rollout core` to preview the rollout.
2. `/forgeadmin rollout core confirm` to enable Research, Chosen Imbuement, Attunement, Corruption
   and Overcharge. Only those five switches change. The original config is backed up beside
   `config.yml` before an atomic replacement; stale edits or invalid Arcane settings refuse the
   rollout. No restart or player-data reset is required for these five switches.
3. `/forgeadmin status` to verify live settings.

The master switch, individual runtime effects, signatures, anticheat settings, prices and unknown
operator config keys remain unchanged. If the master switch is disabled, the command reports it
and does not override it. Repeating the command when all five switches are already enabled does
not rewrite the config or create another backup.

## Player flow

Right-click an enchanting table or talk to the enchanter to open Arcane Enchanting. Normal players
do not need the remote-station permission at a real table. The same screen links to the Codex,
Research and Chosen Imbuement. `/arcane` opens the Codex anywhere; paid enchanting work still needs
the normal station. `/arcane research` and `/arcane imbue` are alternate entry points at a table.

Bind an enchantment through an offer or a tome to learn it, or discover it by researching socket
gems and Arcane Dust. Master an enchantment by binding its highest level three times. Mastery and
Enchanting talents reduce chosen-imbuement costs. No Minecraft XP levels are spent.

Corruption has a fixed displayed bonus, capacity cost and faster durability wear, not a random
failure chance. Cleanse before applying more enchantments, including table offers and tomes.
Gem sockets and repairs remain available. Attunement increases only matching enchantment bonuses;
clearing it is free. Existing instability can still be stabilized even if Overcharge is disabled.

## Deliberately gated runtime powers

Bloodletter, Veinseeker, Chain Harvest and the three Arcane Signatures remain disabled in fresh
release configuration pending native-client protection and Watchdog staging checks. They are not
offered by table or mob-tome pools. Disabled definitions cannot consume research gems or Dust,
cannot generate paid partial discoveries or clues, and cannot be bound through Chosen Imbuement.
The Codex explains that they are held for safety testing. Their existing metadata stays intact
and dormant. Each signature requires its own switch; Stormwake also requires the trusted-effect
bridge, so a global signature switch alone can never charge a player for a dormant power.

This rollout is not certification of those high-risk effects. Enable individually only after the
native-client PvP, island/claim protection, canceled damage/break events and Watchdog scenarios in
the original Forge verification checklist pass on staging.

## Persistence and limits

Malformed or future-schema knowledge is never silently interpreted as empty, seeded or rewritten.
Arcane menus report that staff review is needed; unrelated inventory and stored data are preserved.
Supported extension fields and unknown entry IDs survive normal knowledge updates. Imbuement,
signature binding, research and corruption roll back their inventory changes when their knowledge
save fails. A failed rollback reports a recovery requirement rather than claiming nothing was spent.

These are synchronous inventory-and-YAML operations, matching the existing player data service.
They are not a distributed inventory transaction or a proof of forced-crash atomicity between
Minecraft player saves and YAML. Keep backups; native-client journeys and crash-recovery staging
tests remain release gates.
