# Farming

Right-click a ripe crop with a hoe in your main hand to harvest and automatically replant. You do not need to sneak or learn a talent. The crop stays in its plot at its starting growth stage, and the harvest drops nearby.

The system reserves one ordinary planting item from the harvest. If none drops, it uses one from your inventory. Named, enchanted or otherwise modified seeds are never consumed. If neither source supplies a seed, the crop stays ripe and a message explains what you need. This matters especially for torchflowers and pitcher crops, whose mature harvest does not supply their planting seed.

| Crop | Replanting | Default base Farming XP |
| --- | --- | ---: |
| Wheat, carrots, potatoes, beetroot | Reserve one seed/carrot/potato | 15 |
| Nether wart, cocoa | Reserve one wart/bean; keep cocoa facing its log | 18 |
| Sweet berries, glow berries | Keep the bush/vine, reset its fruit state; no seed spent | 15 |
| Mature torchflowers, pitcher crops | Use one plain seed/pod; pitcher top removed and base replanted | 24 |
| Grown melons, pumpkins | Ordinary breaking; stems are not harvested | 24 |
| Grown sugar cane, cactus, bamboo | Ordinary breaking; placement/replacement cannot farm XP | 9 |

The XP figures are before equipment, talent and rhythm bonuses. Existing skill curves, saved XP, masteries and island progress are unchanged. Eligible island activity still feeds island levels with the existing per-activity and daily caps; machines do not award skill XP.

Ordinary berry harvesting also awards Farming XP after its native fruit-state change is verified. Immature field crops, stems, seeds, saplings, decorative flowers and placed fruit/stalk blocks do not provide reward loops. Naturally regrown fruits and stalks become eligible again after their growth finishes.

Right-click harvests preserve normal tool-dependent drops and Farming yield bonuses. Successful hoe harvests spend one durability through Paper's item-damage API, so Preservation and existing durability perks still apply. Ordinary left-click harvesting remains available, including the existing talent-based sneak-and-break field replant. The interaction uses Paper's separate block/item controls to suppress a second vanilla action; see the [Paper interaction API](https://jd.papermc.io/paper/26.2/org/bukkit/event/player/PlayerInteractEvent.html).

Land/island permissions and harvest/replant cancellations are checked. A refused transaction gives no items or XP, spends no seed and does not wear the hoe. Mature pitcher plants are treated as one crop even when the top half is clicked. The two crop blocks must both be intact.

## Configuration

`progression.xp.farming-crop` remains the reference base (default `5`). New `progression.xp.farming-multiplier` defaults to `3.0`, including configurations saved before this update, so old servers receive the increase without a config reset. Set it to `1.0` for the former reference pace.

Optional `progression.xp.farming-items` overrides the pre-multiplier base by material ID, for example `WHEAT: 8` becomes 24 XP at multiplier 3.0. Categories scale with the reference base; wart/cocoa have base 6, specialty crops 8, and stalks 3. Invalid/non-finite multipliers fall back safely; finite multipliers are bounded from 0.25 to 10.0.

No saved player data or world conversion is required. Keep a backup before deployment and test real-client crop interactions and protection behavior before widening access.
