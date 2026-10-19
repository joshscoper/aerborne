package net.nerdsmp.paper.studio.qa;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.npc.NpcContent;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.nerdsmp.paper.studio.qa.ContentRef.Type.*;
import static net.nerdsmp.paper.studio.qa.ContentValidator.*;

/** Bounded main-thread observation of live catalogs; never loads worlds/chunks or changes authored content. */
public final class DomainAuditSnapshot {
    private DomainAuditSnapshot() { }
    private static final int POINT_CHECK_BUDGET = 256;
    public static Catalog capture(NerdSmpPlugin plugin) {
        if (!org.bukkit.Bukkit.isPrimaryThread()) throw new IllegalStateException("Content snapshots must be captured on the server thread.");
        Set<ContentRef> known = new HashSet<>(); List<Dependency> dependencies = new ArrayList<>();
        List<Item> items = new ArrayList<>(); List<Mob> mobs = new ArrayList<>(); List<Zone> zones = new ArrayList<>();
        List<Finding> observations = new ArrayList<>();
        plugin.items().itemIds().forEach(id -> known.add(ref(ITEM, id.value())));
        plugin.itemEngine().knownSetIds().forEach(id -> known.add(ref(SET, id)));
        for (var item : plugin.itemEngine().all()) {
            items.add(new Item(item.id(), item.setId(), item.itemLevel(), item.stats()));
            link(dependencies, ref(ITEM, item.id()), SET, item.setId(), "Set membership");
        }
        plugin.mobs().ids().forEach(id -> known.add(ref(MOB, id)));
        // Loot assignments also support world bosses/aquatic mobs that are deliberately
        // excluded from the ordinary zone-spawner authoring catalog.
        for (var type : org.bukkit.entity.EntityType.values())
            if (type.isAlive() && type != org.bukkit.entity.EntityType.PLAYER && type != org.bukkit.entity.EntityType.ARMOR_STAND)
                known.add(ref(MOB, "vanilla:" + type.name().toLowerCase(java.util.Locale.ROOT)));
        for (String id : plugin.mobs().ids()) plugin.mobEngine().blueprint(id).ifPresent(mob -> {
            mobs.add(new Mob(mob.id(), mob.level(), mob.health(), mob.attackDamage(), mob.experienceReward(),
                mob.ability() == net.nerdsmp.paper.engine.mob.MobBlueprint.Ability.NONE ? 0 : mob.abilityPower(),
                mob.abilityCooldownSeconds(), mob.lootProfile()));
            link(dependencies, ref(MOB, mob.id()), LOOT, mob.lootProfile(), "Mob loot profile");
        });
        var profiles = plugin.rewardDrops().profiles();
        profiles.all().forEach(profile -> known.add(ref(LOOT, profile.id())));
        for (var profile : profiles.all()) for (var entry : profile.entries())
            if (entry.kind() == net.nerdsmp.paper.loot.profile.LootProfileDefinition.Kind.ITEM)
                link(dependencies, ref(LOOT, profile.id()), ITEM, entry.source(), "Loot entry " + entry.id());
        int checked = 0, omittedPoints = 0;
        if (plugin.zones() != null) {
            plugin.zones().all().forEach(zone -> known.add(ref(ZONE, zone.id())));
            for (var zone : plugin.zones().all()) {
                ContentRef source = ref(ZONE, zone.id());
                zones.add(new Zone(zone.id(), zone.enabled(), zone.safe(), zone.points().size(), zone.mobs().size(), zone.cap(), zone.respawnSeconds()));
                for (var entry : zone.mobs()) link(dependencies, source, MOB,
                    entry.mobId().startsWith("custom:") ? entry.mobId().substring(7) : entry.mobId(), "Zone roster");
                World world = plugin.getServer().getWorld(zone.worldId());
                if (world == null) {
                    observations.add(new Finding(Severity.WARNING, "zone_world_unavailable", source,
                        "Saved world is unavailable. No world was loaded by this check."));
                    continue;
                }
                if (plugin.skyblockEnabled() && plugin.skyblock().isIslandWorld(world))
                    observations.add(new Finding(Severity.ERROR, "zone_private_world", source, "Shared encounter is configured in the private island world."));
                int unloaded = 0, blocked = 0;
                for (var point : zone.points()) {
                    if (checked >= POINT_CHECK_BUDGET) { omittedPoints++; continue; }
                    checked++;
                    int x = (int) Math.floor(point.x()), y = (int) Math.floor(point.y()), z = (int) Math.floor(point.z());
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) { unloaded++; continue; }
                    if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()
                        || !world.getBlockAt(x, y - 1, z).getType().isSolid()
                        || !world.getBlockAt(x, y, z).isPassable() || world.getBlockAt(x, y, z).isLiquid()
                        || !world.getBlockAt(x, y + 1, z).isPassable() || world.getBlockAt(x, y + 1, z).isLiquid()) blocked++;
                }
                if (unloaded > 0) observations.add(new Finding(Severity.INFO, "unloaded_spawn_points", source,
                    unloaded + " spawn points are unloaded; no chunks were loaded for inspection."));
                if (blocked > 0) observations.add(new Finding(Severity.WARNING, "spawn_headroom", source,
                    blocked + " inspected spawn points lack ordinary two-block headroom or solid footing."));
            }
        }
        profiles.zoneAssignments().forEach((id, profile) -> {
            ContentRef source = ref(LOOT, profile);
            link(dependencies, source, ZONE, id, "Zone assignment target"); link(dependencies, ref(ZONE, id), LOOT, profile, "Zone loot assignment");
        });
        profiles.mobAssignments().forEach((id, profile) -> {
            String target = id.startsWith("custom:") ? id.substring(7) : id;
            link(dependencies, ref(LOOT, profile), MOB, target, "Mob assignment target");
            link(dependencies, ref(MOB, target), LOOT, profile, "Mob loot assignment");
        });
        if (plugin.digSites() != null) for (var site : plugin.digSites().store().all()) {
            ContentRef source = ref(DIGSITE, site.id()); known.add(source);
            link(dependencies, source, ZONE, site.zoneId(), "Dig-site parent");
            if (plugin.zones() != null) plugin.zones().find(site.zoneId()).ifPresent(parent -> {
                if (!parent.worldId().equals(site.worldId()) || site.nodes().stream().anyMatch(node -> !parent.bounds().contains(node.x(), node.y(), node.z())))
                    observations.add(new Finding(Severity.ERROR, "digsite_bounds", source, "Dig nodes extend outside their saved parent zone/world."));
                if (site.enabled() && !parent.enabled()) observations.add(new Finding(Severity.WARNING, "digsite_disabled_parent", source,
                    "Dig site is enabled but its parent zone is disabled."));
            });
        }
        NpcContent npcs = plugin.npcs().content();
        npcs.factions().keySet().forEach(id -> known.add(ref(FACTION, id.value())));
        npcs.npcs().keySet().forEach(id -> known.add(ref(NPC, id)));
        npcs.dialogues().keySet().forEach(id -> known.add(ref(DIALOGUE, id)));
        npcs.shops().keySet().forEach(id -> known.add(ref(SHOP, id)));
        for (var npc : npcs.npcs().values()) {
            if (npc.factionId() != null) link(dependencies, ref(NPC, npc.id()), FACTION, npc.factionId().value(), "NPC faction");
            link(dependencies, ref(NPC, npc.id()), DIALOGUE, npc.rootDialogue(), "NPC root dialogue");
            link(dependencies, ref(NPC, npc.id()), SHOP, npc.shopId(), "NPC shop");
        }
        for (var dialogue : npcs.dialogues().values()) for (var choice : dialogue.choices()) {
            link(dependencies, ref(DIALOGUE, dialogue.id()), DIALOGUE, choice.nextNode(), "Dialogue choice " + choice.id());
            for (var effect : choice.effects()) {
                if (effect.type() == NpcContent.EffectType.OPEN_SHOP)
                    link(dependencies, ref(DIALOGUE, dialogue.id()), SHOP, effect.shopId(), "Dialogue shop action");
                if (effect.type() == NpcContent.EffectType.REPUTATION && effect.factionId() != null)
                    link(dependencies, ref(DIALOGUE, dialogue.id()), FACTION, effect.factionId().value(), "Dialogue reputation action");
            }
        }
        for (var shop : npcs.shops().values()) for (var product : shop.products())
            product.customItemId().ifPresent(id -> link(dependencies, ref(SHOP, shop.id()), ITEM, id.value(), "Shop product " + product.id()));
        for (var recipe : plugin.recipes().recipes()) {
            known.add(ref(RECIPE, recipe.id())); link(dependencies, ref(RECIPE, recipe.id()), ITEM, recipe.result().value(), "Recipe result");
        }
        if (plugin.recipeEngine() != null) for (var recipe : plugin.recipeEngine().all()) {
            ContentRef source = ref(RECIPE, recipe.id()); known.add(source);
            // Enabled recipes are already included in the live crafting catalog.
            // Disabled published definitions still need valid output dependencies.
            if (!recipe.enabled()) {
                link(dependencies, source, ITEM, recipe.output(), "Disabled recipe result");
                observations.add(new Finding(Severity.INFO, "disabled_recipe", source,
                    "Recipe is published disabled and is not available at crafting tables."));
            }
        }
        for (var quest : plugin.engagement().definitions()) {
            ContentRef source = ref(QUEST, quest.id()); known.add(source);
            if (quest.action() == null || quest.target() < 1 || quest.target() > 10_000 || quest.markReward() < 1 || quest.markReward() > 10_000)
                observations.add(new Finding(Severity.ERROR, "quest_definition_bounds", source,
                    "Quest needs a valid action, target of 1–10,000 and Marks reward of 1–10,000."));
            else observations.add(new Finding(Severity.INFO, "quest_objective", source,
                quest.action().name() + " ×" + quest.target() + " rewards " + quest.markReward() + " Marks in the daily rotation."));
            if (quest.markReward() > 1_000) observations.add(new Finding(Severity.WARNING, "quest_reward_budget", source,
                "Daily reward exceeds 1,000 Marks. Test objective difficulty and repeat-account farming."));
        }
        plugin.travel().warpNames().forEach(id -> known.add(ref(WARP, id)));
        if (plugin.skyblockEnabled()) {
            if (plugin.travel().publicSpawn() == null) observations.add(new Finding(Severity.WARNING, "hub_unavailable", null,
                "Public hub is not available. Staff must set a reachable public spawn."));
            int missing = plugin.npcs().missingHubDefinitions().size();
            if (missing > 0) observations.add(new Finding(Severity.INFO, "npc_hub_placement", null,
                missing + " NPC definitions have no live hub anchor. This is a placement check, not a requirement to place every optional NPC."));
        }
        if (omittedPoints > 0) observations.add(new Finding(Severity.MANUAL, "spawn_budget", null,
            omittedPoints + " spawn points were outside the 256-point inspection budget; inspect those routes in-game."));
        observations.add(new Finding(Severity.MANUAL, "encounter_journey", null,
            "Play through spawn routes, mob sizes, line of sight, leashes, rewards, party play and progression with real clients."));
        observations.add(new Finding(Severity.MANUAL, "release_operations", null,
            "Load, backup restoration, proxy authentication, payment lifecycle and multiplayer crash tests still require recorded acceptance."));
        observations.add(new Finding(Severity.MANUAL, "persistence_boundaries", null,
            "Release review flags campaign delivery, banking, starter-kit and grave crash boundaries for hardening; a content scan cannot clear them."));
        return new Catalog(known, dependencies, items, mobs, profiles.all(), zones, observations);
    }
    private static ContentRef ref(ContentRef.Type type, String id) { return new ContentRef(type, id); }
    private static void link(List<Dependency> edges, ContentRef source, ContentRef.Type type, String id, String label) {
        if (id != null && !id.isEmpty()) edges.add(new Dependency(source, ref(type, id), label));
    }
}
