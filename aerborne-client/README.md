# Aerborne Client

Optional Fabric client for Aerborne, targeting **Minecraft Java 26.2** and **Java 25**.
This is an early implementation, not a public release or a standalone launcher.
Players using vanilla Minecraft retain the existing menus and gameplay.

## Client features (0.2.1 alpha)

- **B** opens the Aerborne menu on new installs (G conflicts with Minecraft 26.2 Quick Actions).
  Existing saved bindings are preserved. Rebind in Options → Controls → Key Binds → Aerborne.
  `/aerborneclient` also opens the menu; `/aerborneclient settings` opens appearance settings.
  `/aerborneclient status` reports actual bindings, local feature availability and bridge/world-data status.
- `play.aerborne.net` is added to Multiplayer on first launch. Existing entries are preserved,
  equivalent hostnames/default ports are recognized, and removing the entry later is respected.
- **Character Overview** is a native screen showing live health, food, armor, held/worn items,
  durability bars and full item tooltips. Custom accessory slots still use the server Equipment menu.
- **Personal Waypoints** saves up to 32 named locations locally. Track one to show its distance on
  your HUD; **J** opens this screen directly. Locations are isolated by server, account and Paper world UUID.
- **Appearance & HUD** offers Sky, Relic and Celestial accent themes, HUD size (75/100/125%),
  left/right anchoring, and individual coordinate/durability toggles. It is also available from the title screen.
- The HUD displays live health, food, armor, vanilla XP level, latency, equipment durability and
  optional coordinates/facing. **H** toggles it. F1 and open screens hide it; the vanilla HUD stays available.
- Existing illustrated title art and gold-framed icon menus are preserved. Fifteen destinations
  include the three native client screens and twelve existing server command destinations.
- Gameplay buttons invoke existing `nerdsmp:` commands. Skills, quests, shops and transactions
  currently remain server inventory/chat interfaces. No skill progress or RPG Marks are fabricated.
- Local menus and HUD work on `play.aerborne.net` even without the bridge. Other servers require
  a compatible bridge advertisement. Waypoint saving/tracking still requires fresh bridge world data.
  Unsupported-server shortcuts show an explanation, and H confirms whether the HUD is enabled or hidden.
- Keyboard navigation, tooltips and responsive pagination use Minecraft's standard widgets.
- Preferences and waypoints persist in `config/aerborne-client.properties` using atomic file replacement.
  Waypoints are navigation aids only; they do not teleport, automate movement or reveal undiscovered locations.

## Build

From this directory with JDK 25 selected:

```powershell
.\gradlew.bat build
```

This is an independent Gradle build so Fabric tooling does not affect the main server build.
Pinned dependencies: Gradle 9.8.0, Loom 1.19.3, Fabric Loader 0.20.1,
Fabric API 0.161.0+26.2, and Paper API 26.2.build.121-stable.

## Install for development

1. Install Fabric Loader 0.20.1 for Minecraft 26.2 in a separate game profile.
2. Put Fabric API 0.161.0+26.2 and `fabric/build/libs/aerborne-client-0.2.1-alpha.1.jar`
   into that profile's `mods` folder. Do not install the sources jar.
3. Put `paper-bridge/build/libs/aerborne-client-bridge-0.2.1-alpha.1.jar` into the
   Aerborne Paper server's `plugins` folder alongside NerdSMP, then restart that server.
4. Restart Minecraft, join Aerborne, then use your saved menu binding or `/aerborneclient`.
   A current bridge supplies waypoint world data within five seconds.

The bridge is a server plugin; do not put it in the client's mods folder. The Fabric jar is
client-only; do not install it on Paper. No files are deployed automatically by the build.
Replace the previous client/bridge jars rather than installing two versions together.
Use the normal Minecraft launcher and account login. A branded launcher is a separate future project.

## Architecture

| Module | Responsibility |
| --- | --- |
| `fabric` | Native Minecraft screens, keybind and session state |
| `protocol` | Shared capability format and session tests; no Minecraft dependency |
| `paper-bridge` | Optional capability advertisement; depends on NerdSMP |

The bridge sends `aerborne:capabilities` only to players who register that channel.
The payload is exactly eight bytes: big-endian signed 32-bit protocol version, then feature bits.
Version 1, feature bit 0 enables the menu. Unknown versions disable it; unknown feature bits
are ignored. Zero bits revoke it. Advertisements repeat every 100 server ticks, and the client
expires them after 15 seconds. Disconnect and configuration/play connection initialization clear
the state. A stalled bridge temporarily disables world-dependent waypoints; the official hostname
continues to enable presentation-only local menus and HUD. This hostname fallback grants no server permissions.

The bridge also sends `aerborne:world`: exactly 16 bytes containing the current Paper world UUID
as two big-endian longs. It refreshes with the heartbeat and on world changes. This avoids treating
different Skyblock worlds as the same place merely because both use the overworld dimension.
Older bridges still enable the basic menu/HUD, but waypoints wait for world data.

This is feature negotiation, not proof of server identity or an anti-cheat attestation.
Another server can reproduce the advertisement. "Exclusive to Aerborne" describes the product
and its server integration, not an uncopyable client. Do not use the handshake to grant privileges.
Commands still enforce permissions, costs, cooldowns, confirmations and inventory handling on
the server. The client receives no web API bearer token or administrative credentials.

## Next milestones

1. Extend the native character overview with RPG stats and a quest journal from bounded server snapshots.
2. Extend the configurable HUD with tracked quests, skill progress and server-published ability cooldowns.
3. Searchable recipe browser and item comparisons using authoritative visible item data.
4. Native talent trees and transactional screens, with validated action requests, stale-state
   rejection and the same confirmations as vanilla menus.
5. Tested distribution profile and update process; evaluate a custom launcher separately.

Every milestone retains a vanilla route to gameplay. New UI can improve presentation and
navigation without changing rewards, progression, combat rules or access to content.

## Verification before distribution

Current automated verification: all three modules build successfully with the pinned toolchain.
Twelve protocol/preferences/access tests cover packet bytes, invalid sizes, incompatible versions, lease expiry,
revocation, disconnect reset, equivalent server addresses, preference persistence, waypoint scope
and malformed saved data, plus official-host fallback, unrelated servers and disconnects.
Production server and Velocity integration still require playtesting.

The [screenshot harness](../testing/aerborne-client-preview/README.md) runs the actual client code
and assets in an isolated development profile. Its simulated capability is never included in the
distributed jar. Style-preview HUD values are explicitly labelled as samples.

- Join the test server using both vanilla and Fabric clients at the same time.
- Verify the saved menu binding, H/J, Escape, tab navigation, resize, GUI scaling and pagination.
- Exercise every destination and compare it with typing its command in vanilla.
- Verify missing/stale world data disables waypoint saving. Local menus/HUD still work on the official host;
  unrelated servers require a compatible bridge. Check `/aerborneclient status` in each case.
- Disconnect/reconnect and switch backend servers through Velocity, including reconfiguration.
- Confirm existing server inventory interactions, transactions and Watchdog behavior are unchanged.

Design follows [Aerborne's brand reference](../docs/AERBORNE_BRAND.md).
Implementation references: [Fabric custom screens](https://docs.fabricmc.net/develop/rendering/gui/custom-screens),
[Fabric networking](https://docs.fabricmc.net/develop/networking), and
[Paper plugin messaging](https://docs.papermc.io/paper/dev/plugin-messaging/).
