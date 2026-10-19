package net.nerdsmp.paper.gathering;

import net.nerdsmp.api.model.Skill;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.skyblock.IslandTalent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Genetics applies only to player-bred ordinary livestock in their co-op territory. */
public final class LivestockService implements Listener {
    private final NerdSmpPlugin plugin;
    private final NamespacedKey genes;
    private static final Set<String> TYPES = Set.of("COW", "SHEEP", "PIG", "CHICKEN", "RABBIT", "MOOSHROOM", "MUSHROOM_COW");
    public LivestockService(NerdSmpPlugin plugin) { this.plugin = plugin; genes = new NamespacedKey(plugin, "livestock_genes_v1"); }
    private boolean supported(Animals animal) {
        return TYPES.contains(animal.getType().name()) && animal.getPersistentDataContainer().getKeys().stream()
            .noneMatch(key -> key.getNamespace().equals("nerdsmp") && (key.getKey().contains("stack") || key.getKey().contains("mob_id")));
    }
    public LivestockGenes genes(Animals animal) {
        int[] data = animal.getPersistentDataContainer().get(genes, PersistentDataType.INTEGER_ARRAY);
        if (data == null) return LivestockGenes.ordinary();
        if (data.length != 3) throw new IllegalArgumentException("Invalid livestock genes");
        return new LivestockGenes(data[0], data[1], data[2]);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void breed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player) || !(event.getEntity() instanceof Animals child)
            || !(event.getMother() instanceof Animals mother) || !(event.getFather() instanceof Animals father)
            || !supported(child) || !supported(mother) || !supported(father) || plugin.skyblock() == null
            || !plugin.skyblock().isIslandWorld(child.getWorld()) || player.getGameMode() != GameMode.SURVIVAL
            || Bukkit.getPlayer(player.getUniqueId()) != player || child.getPersistentDataContainer().has(genes)) return;
        var account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (account == null) return;
        var island = plugin.skyblock().islands().at(child.getLocation().getBlockX() >> 4, child.getLocation().getBlockZ() >> 4).orElse(null);
        if (island == null || !island.member(account)) return;
        for (Animals parent : new Animals[]{mother, father}) {
            if (!parent.getWorld().equals(child.getWorld()) || !plugin.skyblock().islands().at(parent.getLocation().getBlockX() >> 4,
                parent.getLocation().getBlockZ() >> 4).map(area -> area.id().equals(island.id())).orElse(false)) return;
        }
        final LivestockGenes inherited;
        try {
            double quality = plugin.presentation().progression(player).talentStat(Skill.FARMING, "husbandry-quality-percent")
                + 4 * plugin.skyblock().development().progress(island.owner()).rank(IslandTalent.LINEAGE);
            inherited = LivestockGenes.inherit(genes(mother), genes(father), quality, ThreadLocalRandom.current());
        } catch (IllegalArgumentException malformed) { return; }
        // Wait until every listener and vanilla's breeding reset have finished.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.isCancelled() || !child.isValid() || child.getPersistentDataContainer().has(genes)) return;
            child.getPersistentDataContainer().set(genes, PersistentDataType.INTEGER_ARRAY,
                new int[]{inherited.vitality(), inherited.growth(), inherited.fertility()});
            var health = child.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) { health.setBaseValue(Math.min(100, health.getBaseValue() * inherited.healthMultiplier())); child.setHealth(health.getValue()); }
            child.setAge(inherited.babyTicks());
            for (Animals parent : new Animals[]{mother, father}) if (parent.isValid() && parent.getAge() > 0) {
                try { parent.setAge(Math.min(parent.getAge(), genes(parent).breedingCooldown())); }
                catch (IllegalArgumentException malformed) { /* Preserve the ordinary cooldown for malformed data. */ }
            }
        });
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void inspect(PlayerInteractEntityEvent event) {
        if (!event.getPlayer().isSneaking() || !(event.getRightClicked() instanceof Animals animal) || !supported(animal)) return;
        try {
            var value = genes(animal);
            event.getPlayer().sendMessage("Livestock | Vitality " + value.vitality() + "/10 | Growth " + value.growth() + "/10 | Fertility " + value.fertility() + "/10");
        } catch (IllegalArgumentException malformed) { event.getPlayer().sendMessage("This animal's traits need staff review."); }
    }
}
