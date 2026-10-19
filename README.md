# Aerborne

*Small beginnings. Endless skies.*

Aerborne is a **Java-only Minecraft Skyblock RPG**: private co-op islands, a
shared arcane city, and staff-built RPG zones and expeditions. Under the islands sit a full RPG
layer: Marks and a player economy, skills with talent trees, Adventurer Levels 1 to 100,
socketable gear, custom enchanting and alchemy, NPCs with dialogue and reputation, and a built-in
anti-cheat with a web staff console.

The player-facing name is **Aerborne**, set by `skyblock.display-name`. The internal
plugin name stays `NerdSMP`, and item IDs stay `nerdsmp` for compatibility. This is a private development build,
not a public release; see [release gates](docs/HEARTHSKY_RELEASE.md).

An older open-world SMP mode, with land claims called [Homesteads](docs/HOMESTEADS.md), is still
available as an opt-in (`gameplay.mode: legacy`).

## Repository

| Module | What it is |
|---|---|
| `nerdsmp-paper` | The gameplay plugin. Almost everything lives here. |
| `nerdsmp-api` | Shared domain and service interfaces. |
| `nerdsmp-edge` | The Velocity proxy plugin. |
| `deploy/` | Server configs and templates for Paper, Velocity, optional Nexo and Tebex, plus the local test setup. |
| `scripts/` | Test-server, verification and data-export scripts. |
| `docs/` | Feature guides, staff tools and operations. Start at the [docs index](docs/README.md). |

Commissioned worlds, original art, production credentials, hosted infrastructure and third-party
service accounts are deliberately not stored here.

## Quick start

You need **JDK 25**. Docker Desktop or PostgreSQL 17 is only needed to test production storage.
No resource pack is required; a licensed Nexo jar is optional.

**Build** with the Gradle wrapper:

```powershell
./gradlew.bat clean build
```

The jars land in `nerdsmp-paper/build/libs/` and `nerdsmp-edge/build/libs/`.

**Run a local test server** on Windows:

```powershell
.\run-test-server.cmd
```

The script builds the plugin, downloads and verifies the pinned Paper jar, creates a
localhost-only runtime in `runtime/skyblock-test`, asks you to accept the Minecraft EULA on first
launch, and starts Skyblock. Connect to **127.0.0.1:25566** and run `/island create`.

- `run-skyblock-test-server.cmd` does the same thing.
- `run-test-server.ps1 -LegacySmp` starts legacy SMP mode on port 25565, with its own world and data.
- `-PrepareOnly` sets up the runtime without starting it; `-RefreshConfig` restores the checked-in
  development config.
- To use Nexo textures, put your licensed `Nexo*.jar` in `local-plugins/` and pass `-Nexo`.

## Configuration

The plugin writes `plugins/NerdSMP/config.yml` on first start. The
[default config](nerdsmp-paper/src/main/resources/config.yml) is grouped into commented sections,
with the most important switches first:

- `gameplay.mode`: `skyblock` (default) or `smp` for the older open-world mode.
- `storage.mode`: `yaml` for persistent local player files, or `postgres`. The older `memory`
  value is a retained alias for the same persistent YAML backend; it does not mean saves are temporary.
  PostgreSQL connection settings use the environment variables in [.env.example](.env.example).
  Never switch an existing backend without migrating and reconciling its data.
- `skyblock.*`: display name, market radius, island displays and tips.
- `branding.*`: plain-text tagline, mode label and server-list description. The existing
  `skyblock.display-name` remains the public name, shared by the MOTD, tablist, sidebar and welcome.

Local test runs keep player data in `plugins/NerdSMP/player_data/<uuid>.yml`. The
[operations runbook](docs/OPERATIONS.md) covers the proxy, Paper, backups and deployment.

The separate [web API](docs/WEB_API.md) listens on `0.0.0.0:9028` by default. Public
status is read-only; private endpoints require its generated bearer token. Use HTTPS
or a trusted private connection for that token. The staff console remains separate on 9029.

Public presentation and fresh-deployment candidate profiles are described in
[the branding/release notes](docs/PUBLIC_BRANDING.md). They do not certify public-launch readiness.

## First steps in game

**Players**
- `/quickstart` for the first-session route, and `/menu` or `/aerborne` for everything else.
- `/island` for your island, co-op, talents (`/island talents`), machines (`/island machines`)
  and journal (`/island journal`).
- `/skills`, `/adventurer` and `/achievements` for progression.
- `/ah`, `/store` and `/trade <player>` for [player commerce](docs/PLAYER_COMMERCE.md);
  `/shops` for the NPC merchants.
- `/settings` for tips, messages and requests.

**Staff**
- `/setspawn`, `/setwarp` and `/zone` connect your builds to travel and encounters. NPCs are
  placed by hand. See [manual hubs and zones](docs/MANUAL_ZONES.md).
- `/studio` opens the in-game content editors ([staff tools](docs/README.md#staff-tools)).
- `/staff` handles reports, mutes, bans and the audit trail. `/watchdog` inspects the
  [anti-cheat](docs/WATCHDOG_ANTICHEAT.md). The same tools are in the web staff console
  ([operator guide](docs/STAFF_FISHING_STATIONS.md)).
- `/aerborneadmin releasecheck` (`/nerdadmin releasecheck` also works) lists diagnostics and open release gates.

## Documentation

- [Docs index](docs/README.md): player systems, staff tools, operations and verification logs
- [Changelog](docs/CHANGELOG.md): what changed in each pass
- [Style guide](docs/STYLE_GUIDE.md): how the plugin talks to players, for contributors
- [Brand reference](docs/AERBORNE_BRAND.md): Aerborne's palette, voice and artwork requirements

## Ownership

This project is proprietary. See [LICENSE.md](LICENSE.md).
