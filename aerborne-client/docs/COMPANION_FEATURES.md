# Companion mod feature audit

What players expect from a server's companion mod, taken from Wynntils (Wynncraft) and the
Hypixel SkyBlock mods (SkyHanni, NotEnoughUpdates, Skytils), checked against the Aerborne client.

Status: **Have** = in the client now, **Now** = built in this pass, **Server** = needs a new
feed from NerdSMP, **Art** = waits on the asset pack (see `ASSET_PROMPT.md`), **Later** = worth doing, not urgent.

## HUD and overlays

| Feature | Reference | Status |
|---|---|---|
| Real health, Defense, Adventurer level and XP, Marks | Wynntils custom bars | Have |
| Quest or expedition tracker panel | Wynntils quest overlay, SkyHanni trackers | Have |
| Minimap with compass | Wynntils minimap | Have |
| Enemy nameplates with health | SkyHanni damage indicator | Have |
| Skill XP toast instead of a boss bar | Wynntils leveling overlay | Have |
| Status effect timers in place of vanilla icons, debuffs first, with drain bars and a blink when ending | Wynntils "hide vanilla potion indicators" | Have |
| Status effect icons on enemy nameplates (level, seconds when nearly over) | SkyHanni damage indicator, Neat | Have (`aerborne:effects` feed) |
| Target frame beside the player frame: name, rank, health, every effect with time left; holds 4 s | WoW-style unit frames, Wynntils | Have |
| HUD edit mode: drag, scale (wheel) and hide every element, snapping to edges and other elements; key U or Appearance & HUD | WoW Edit Mode | Have |
| Low health vignette | Wynntils | Now |
| Damage numbers over creatures | Wynntils damage splashes | Now |
| Session tracker: Marks and XP per hour | SkyHanni profit trackers | Now |
| Beacon beam at the tracked waypoint | Wynntils | Now |
| Party frames (members' health) | Wynntils, SkyHanni | Server |
| Boss health with DPS and time to kill | SkyHanni damage indicator | Server |
| Item cooldowns on the hotbar | SkyHanni item abilities | Server |
| Level up, quest complete and rare drop toasts | Wynntils toasts, mythic sound | Server |

## Items and inventory

| Feature | Reference | Status |
|---|---|---|
| Rarity colour behind items in every slot | Wynntils item rarity overlay | Now |
| Item lock (cannot be dropped, moved or sold) | Wynntils item locker | Now |
| Search box that highlights matches in any menu | NEU / SkyHanni inventory search | Now |
| Durability on the HUD | Wynntils | Have |
| Auction price ranges in tooltips | NEU price data | Server |
| Gem socket and identification status in tooltips | Wynntils identification status | Server |
| Recipe and item browser | NEU item list | Server, Art |
| Storage overview (bank, island chests) | NEU storage overlay | Server, Art |

## Map and travel

| Feature | Reference | Status |
|---|---|---|
| Personal waypoints, tracked with distance | Wynntils waypoints | Have |
| Death waypoint, set automatically | common QoL | Now |
| Coordinates in chat become clickable waypoints | Wynntils clickable coordinates | Now |
| Full-screen world map of explored ground | Wynntils world map | Later, Art |
| Server points of interest: towns, NPCs, flights, gates | Wynntils map POIs | Server, Art |
| Fast travel from the map | Wynntils | Server |

## Chat

| Feature | Reference | Status |
|---|---|---|
| Timestamps | Wynntils | Now |
| Mentions of your name highlighted with a sound | Wynntils | Now |
| Repeated lines folded into "(x3)" | Wynntils spam filter | Now |
| Chat tabs (All, Party, Trade, System) | Wynntils custom tabs | Later |
| Clickable party and trade invites | Wynntils | Have (server sends them clickable) |

## Menus and screens

| Feature | Reference | Status |
|---|---|---|
| Aerborne launcher menu | Wynntils menu buttons | Have |
| Plugin chest menus in the Aerborne frame | stopgap | Have |
| Native quest book with tracking | Wynntils custom questbook | Server, Art |
| Native skills and talent trees | Wynncraft ability tree | Server, Art |
| Native character and equipment screen | Wynntils gear viewer | Server, Art |
| Native island, bank, auction and shop screens | NEU storage / AH overlays | Server, Art |
| Inspect another player's gear | Wynntils "see armor by looking" | Server |
| NPC dialogue panel | | Have |

## Settings and quality of life

| Feature | Reference | Status |
|---|---|---|
| Every overlay can be switched off | all | Have (extended in this pass) |
| Discord rich presence | Wynntils | Later |
| AFK guard and stopwatch | Wynntils | Later |
| Brightness boost | Wynntils gammabright | Later |
