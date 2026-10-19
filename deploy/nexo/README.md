# Nexo resource-pack ownership

When optional Nexo support is enabled, Nexo 1.25+ is Aerborne's only Java resource-pack generator, merger, host, and dispatcher. The existing NerdSMP plugin continues to own item identity, persistent gameplay data, tooltips, damage, defense, and set logic. The default vanilla-texture deployment does not require a pack.

## Local test setup

1. Put your licensed Nexo 1.25+ jar in `local-plugins/`.
2. Run `run-test-server.cmd -PrepareOnly`.
3. The runner installs the jar and mirrors `assets/nexo-pack` into `plugins/Nexo/pack/external_packs/NerdSMP`.
4. Start with `run-test-server.cmd`. Nexo generates and sends the merged pack.
5. If an existing Nexo settings file is already present, merge `settings.local.fragment.yml` into it and run `/nexo reload`.

Do not configure Paper's `server-resource-pack` properties or the plugin's legacy direct URL while Nexo is selected. Two dispatchers cause duplicate prompts, hash races, and unreliable pack status. Keep `external_packs/NerdSMP` and `nerdsmp:` asset identifiers unchanged; they are integration paths, not public branding.

## Production

Merge `settings.production.fragment.yml` into Nexo's generated `settings.yml`. The checked-in baseline uses Nexo's POLYMATH host and contains no deployment secrets. If you later move to SELFHOST, keep its public address in deployment configuration rather than source control.

For the initial single Paper backend, Nexo can dispatch through Velocity without another addon. Install NexoProxy on Velocity when multiple Nexo backends share the same pack so duplicate sends are suppressed during server switches.

Aerborne supports authenticated Java Edition only. Do not install Scaffolding, Geyser or Floodgate. Review every ItemModel in-game with the merged Java pack.
