package net.nerdsmp.paper.loot;

import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.block.Container;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Adds expansion gear without replacing existing loot or staff-authored replacement tables. */
public final class ExpansionLootService implements Listener {
    private final NerdSmpPlugin plugin;
    private final ExpansionLootTables tables;
    private final Map<ExpansionLootTables.Rarity,List<ItemId>> pools;
    private final Set<Object> seen=Collections.newSetFromMap(new WeakHashMap<>());

    public ExpansionLootService(NerdSmpPlugin plugin) {
        this.plugin=plugin;
        String resource="content/skyforge-loot.yml";File file=new File(plugin.getDataFolder(),resource);
        if (!file.exists()) plugin.saveResource(resource,false);
        try {var yaml=new YamlConfiguration();yaml.load(file);tables=ExpansionLootTables.read(yaml);}
        catch (Exception error) {throw new IllegalStateException("Cannot load "+resource+"; existing content preserved",error);}
        var grouped=new EnumMap<ExpansionLootTables.Rarity,List<ItemId>>(ExpansionLootTables.Rarity.class);
        plugin.items().expansionEntries().forEach((id,entry)->{
            if (entry.acquisition().equals("drop")) grouped.computeIfAbsent(ExpansionLootTables.Rarity.valueOf(entry.rarity()),ignored->new ArrayList<>()).add(id);
        });
        Map<ExpansionLootTables.Rarity,List<ItemId>> immutable=new EnumMap<>(ExpansionLootTables.Rarity.class);
        grouped.forEach((rarity,ids)->immutable.put(rarity,ids.stream().sorted(Comparator.comparing(ItemId::value)).toList()));
        pools=Map.copyOf(immutable);tables.validatePools(pools);
    }

    /** Deterministic roll API for administrative previews and integration verification; grants nothing. */
    public Optional<ItemStack> roll(ExpansionLootTables.Source source,double chance,double rarity,double item,double unidentified,long seed) {
        if (!Double.isFinite(unidentified)||unidentified<0||unidentified>=1) throw new IllegalArgumentException("Invalid identification roll");
        return tables.select(source,chance,rarity,item,pools).map(id->{
            var entry=plugin.items().expansionEntries().get(id);
            int affixes=switch (entry.rarity()) {case "MYTHIC"->3;case "LEGENDARY","EPIC"->2;default->1;};
            return plugin.items().createLoot(id,affixes,unidentified<tables.unidentifiedChance(),seed);
        });
    }
    private Optional<ItemStack> roll(ExpansionLootTables.Source source) {
        var random=ThreadLocalRandom.current();return roll(source,random.nextDouble(),random.nextDouble(),random.nextDouble(),random.nextDouble(),random.nextLong());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onDeath(EntityDeathEvent event) {
        if (!Bukkit.isPrimaryThread()||event.getEntity() instanceof Player||!seen.add(event)) return;
        var entity=event.getEntity();Player killer=entity.getKiller();
        if (killer==null||plugin.sessions().account(killer.getUniqueId()).isEmpty()
            ||(killer.getGameMode()!=GameMode.SURVIVAL&&killer.getGameMode()!=GameMode.ADVENTURE)
            ||plugin.mobs().isMechanicObjective(entity)||plugin.rewardDrops().replacesDefaultLoot(entity)
            ||!(entity instanceof Enemy||plugin.mobs().isCustomMob(entity)||plugin.mobs().isBoss(entity))) return;
        var source=plugin.mobs().isBoss(entity)?ExpansionLootTables.Source.BOSS
            :plugin.mobs().isElite(entity)?ExpansionLootTables.Source.ELITE:ExpansionLootTables.Source.STANDARD;
        roll(source).ifPresent(event.getDrops()::add);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onChest(LootGenerateEvent event) {
        if (!Bukkit.isPrimaryThread()||event.isPlugin()||!(event.getInventoryHolder() instanceof Container)||!seen.add(event)) return;
        var key=event.getLootTable().getKey();
        if (!key.getNamespace().equals("minecraft")||!key.getKey().startsWith("chests/")) return;
        roll(ExpansionLootTables.Source.CHEST).ifPresent(event.getLoot()::add);
    }
}
