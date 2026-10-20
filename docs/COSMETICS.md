# Cosmetics: companions, trails and supporter ranks

Everything the website store sells is in game, and all of it is visual only: six companions, eight trails,
three supporter ranks and three bundles. Code lives in `nerdsmp-paper/src/main/java/net/nerdsmp/paper/cosmetic/`.

## For players

| Command | What it does |
|---|---|
| `/cosmetics` (`/wardrobe`) | The wardrobe: summon companions, wear trails, pick a name colour, use presets, claim the keepsake. |
| `/companion [name]` / `/companion dismiss` | Summon a companion you own / send it home. `/companion list` shows them all. |
| `/trail [name]` / `/trail off` | Wear a trail you own / take it off. `/trail list` shows them all. |
| `/emote <name>` | wave, cheer, heart, bow, dance, sparkle. Cloudkeeper and Starweaver only; players within 24 blocks see it, unless they ignore you. |

The chosen companion, trail and name colour are saved per account and come back after logging in.
`/settings` → *Other players' companions and trails* hides everyone else's cosmetics for that player.

## What each purchase gives

| Store item | Product id | In game |
|---|---|---|
| Skyfarer | `cosmetic.rank.skyfarer` | Aqua ✦ badge in chat, the tab list and above your head; 3 name colours |
| Cloudkeeper | `cosmetic.rank.cloudkeeper` | ☁ badge; 5 name colours; emotes |
| Starweaver | `cosmetic.rank.starweaver` | ✧ badge; 7 name colours; emotes; 3 wardrobe presets; Starweaver Lantern keepsake |
| Pufflet … Starwhisk companion | `cosmetic.companion.<name>` | That companion |
| Cloudstep … Stardust Waltz trail | `cosmetic.trail.<name>` | That trail (`petal-skip`, `firefly-drift`, `rune-ripple`, `comet-tail`, `dandelion-drift`, `stardust-waltz`) |
| Cloudberry bundle | `cosmetic.bundle.cloudberry` | Pufflet + Cloudstep |
| Blooming Skies bundle | `cosmetic.bundle.blooming-skies` | Mossbun + Petal Skip |
| Celestial Dreams bundle | `cosmetic.bundle.celestial-dreams` | Starwhisk + Stardust Waltz |

Higher ranks include everything below them. The older `supporter_monthly` and `supporter_annual` products
still work and carry every perk, shown with the classic gold ✦.

## Connecting Tebex

For each Tebex package, add a command that runs when the player is online or offline:

```
nerdadmin entitlement grant {username} <product id> {transaction}
```

and a refund/chargeback command:

```
nerdadmin entitlement revoke {transaction} Refunded
```

Grants are idempotent on the transaction id, so Tebex retrying a command never grants twice. Unknown product
ids are refused with a clear message instead of being stored, and `/nerdadmin entitlement products` lists every
valid id. A player who is online when the grant arrives is told straight away; everyone else sees their new
cosmetics the next time they join. Ownership is re-read once a minute, so a refund removes the item within a minute.

Staff can hand out a cosmetic for events or testing the same way, with a made-up transaction id such as
`staff-2026-10-19-ava`.

## How companions work

Until custom models are made, each companion is played by a small vanilla creature: Pufflet is a ghastling,
Kitekit a snow fox kit, Mossbun a bunny, Cindlet a red fox kit with embers, Moonmoth an allay and Starwhisk a
black kitten with stardust. Change the creature, variant, size, flying and particle under `cosmetics.companions`
in `config.yml`, or set `free: true` to give a companion or trail to everyone.

A companion has its AI off and is invulnerable, silent, not solid and never saved to the world. Nobody can
feed, tame, leash, name, ride or hurt it, mobs don't target it, and mob scaling and Arcane area effects skip it.
It follows a couple of blocks behind its owner. If its owner is dead, a spectator or invisible, it is put away
and comes back afterwards. After a long teleport it reappears beside the owner. Trails draw at most eight
particles every two ticks, only while their owner moves (Stardust Waltz swirls when you turn), and only for
players within 40 blocks who haven't hidden other players' cosmetics.

## Tests

`CosmeticCatalogTest` covers the catalog, product ids, bundles, tiers and older subscriptions.
`CompanionMotionTest` covers following, snapping, facing and trail particle limits. Run them with the
plugin's test suite.
