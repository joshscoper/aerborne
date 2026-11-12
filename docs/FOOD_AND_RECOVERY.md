# Food and recovery

Survival and Adventure players keep a full food bar in every world, including private islands and
expeditions. Sprinting, mining and healing consume no hunger. The vanilla client still draws its
food bar; a client mod is not required.

After eight seconds without dealing or taking damage, recover **5% of actual maximum health per
second**. Recovery scales with equipment and Adventurer vitality: 20 maximum health restores 1 HP
per pulse, and 200 maximum health restores 10 HP. From half health, this takes about 18 seconds
including the combat delay, with up to one second of timer rounding.

Recovery does not tick for dead players, spirits, loading profiles, Creative or Spectator players,
or while Poison or Wither is active. Login and respawn begin a fresh recovery delay. Missed ticks
and offline time never produce a healing catch-up burst. Vanilla satiated regeneration is disabled;
healing potions, Regeneration effects, life steal and expedition campfires remain available.

## Food is preparation

Eat ordinary food at full health and hunger using the normal right-click action. One serving grants
**Well Fed: +25% passive rest recovery for five minutes**. That is 6.25% maximum health per second
after the combat delay. The latest serving replaces the benefit; eating repeatedly never adds stacks
or immediate healing. A cheaper snack cannot extend another meal's stronger quality.

Prepared Cooking meals retain their existing listed potion effects and chef-scaled durations.
Nourishment's saved `meal-nutrition-percent` now adds percentage points to the Well Fed bonus instead
of saturation: a +15 meal grants +40% recovery, giving 7% maximum health per second while resting.
Chef quality is still fixed when the serving is crafted and survives trading. Existing meals work.
The food tooltip, Cooking tree, tutorial, feature tips and website guide explain the updated effect.

Native consumption still owns item counts, bowls, buckets, bottles, golden-apple effects and
suspicious-stew effects. Placed cake consumes one slice and grants the same ordinary-food benefit.
Cancelled consumption grants nothing. Eating never grants skill XP or currency. Well Fed expires
in real time through logout/restart and is removed on death or when drinking milk.

The existing vitality action bar shows the Well Fed countdown and a short recovery-delay indicator.
Food does not occupy a second competing action bar.

## Compatibility and persistence

Ordinary food uses a shared tooltip: a localized gold item name, dark dividers, green `PROVISIONS`
category, white Well Fed duration and gray recovery/stacking guidance. The benefit and duration
come from the actual recovery configuration. Prepared Cooking meals retain their `HARVEST MEAL`
template and chef details. Existing custom names and lore are preserved.

The item's `canAlwaysEat` food flag, ordinary-food presentation and three presentation markers are
updated. Native food values, other components, item counts and container returns are preserved.
The updater covers old inventory, held items, storage, dropped items and nested containers. A bounded
player-inventory pass catches items introduced by other plugins. Presentation is idempotent and
refreshes when configured food values change; it does not add another copy of the lore.

The exact ordinary-food presentation and food override are recognized by recipe, replanting,
quest-delivery and island-machine checks. Names, lore and their saved presentation parameters must
match before only the known presentation is stripped for comparison; all other metadata remains.
Renamed, enchanted, custom and prepared named meals retain their previous protection against
accidental use as ordinary ingredients. NPC food previews, delivery and buyback share the same
current template. Prepared recipes remove the provision template before applying their own style.

Other obtainable item families now use the shared [item tooltip templates](ITEM_TEMPLATES.md).
The generic frame leaves the richer food and prepared-meal templates intact.

Well Fed is stored in the account's atomic canonical player-data document under `food.v1`, with
an absolute expiry and recovery bonus. Linked clients use the same account. A save publishes in
memory only after its disk write succeeds; malformed state is not overwritten. Combat deadlines
are session-local and restart on login. Recovery emits a cancellable `EntityRegainHealthEvent`
with reason `CUSTOM`; other plugins can reject or adjust healing. Hunger recovery alone is cancelled.

## Configuration and deployment

```yaml
food-and-recovery:
  enabled: true
  combat-delay-seconds: 8
  health-percent-per-second: 5
  well-fed-seconds: 300
  well-fed-recovery-percent: 25
```

Missing settings in an existing `config.yml` use these defaults. Add this section only if you want
to tune it. Restart after editing. Do not replace a customized production config with the complete
default file. Existing player progress, gear, meal quality and recipes need no reset.

| Pace | Damage delay | Recovery each second | Approximate recovery from half health |
| --- | ---: | ---: | ---: |
| Deliberate | 10s | 3% maximum HP | 27s |
| Default | 8s | 5% maximum HP | 18s |
| Quick | 5s | 8% maximum HP | 12s |

Times omit food and include rounding to whole recovery pulses. Start with the default so food is
useful preparation while recovery between encounters remains free. Keep combat healing in the
existing potion, meal-effect, equipment and party systems.

The implementation uses Paper's [food data component API](https://jd.papermc.io/paper/26.2/io/papermc/paper/datacomponent/item/FoodProperties.Builder.html).
