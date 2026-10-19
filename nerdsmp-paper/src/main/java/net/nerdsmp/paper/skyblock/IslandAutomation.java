package net.nerdsmp.paper.skyblock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.attribute.Attribute;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** Island-only, loaded-chunk automation. Never keeps chunks loaded or sells collected items. */
public final class IslandAutomation implements Listener, AutoCloseable {
    private final NerdSmpPlugin plugin;
    private final SkyblockController sky;
    private final IslandCollectors collectors;
    private final NamespacedKey collectorKey, collectorFilterKey, spawnerCountKey, mobCountKey, mobSourceKey;
    private final NamespacedKey mobEquipmentKey;
    private final NamespacedKey spawnerItemTypeKey, spawnerItemSchemaKey;
    private static final List<Attribute> STACK_ATTRIBUTES = List.of(Attribute.MAX_HEALTH, Attribute.ATTACK_DAMAGE,
        Attribute.ARMOR, Attribute.ARMOR_TOUGHNESS, Attribute.MOVEMENT_SPEED, Attribute.SCALE, Attribute.KNOCKBACK_RESISTANCE);
    private static final Set<String> SCALE_DOUBLES = Set.of("mob_base_health", "mob_base_damage", "mob_base_ability_damage",
        "mob_ability_damage", "mob_damage_multiplier", "mob_reward_multiplier");
    private static final Set<String> SCALE_LONGS = Set.of("mob_base_mark_reward", "mob_mark_reward");
    private static final Set<String> SCALE_INTS = Set.of("mob_level", "mob_combat_group_size");
    private final Map<UUID, StackCandidate> pendingMobs = new LinkedHashMap<>();
    private final Set<UUID> retiringMembers = new HashSet<>();
    private final LinkedHashSet<ChunkPosition> reconciliation = new LinkedHashSet<>();
    private final Map<ChunkPosition, Integer> reconciliationCursors = new HashMap<>();
    private final Map<ChunkPosition, SpawnBudget> spawnBudgets = new HashMap<>();
    private final Map<String, Long> stackRejections = new TreeMap<>();
    private long mergedBodies, mergedMembers, markedBodies, reconciledChunks, reconciliationInspections;
    private long lastPassNanos, maxPassNanos;
    private int lastCandidateWork, lastNearbyChecks, lastChunkSnapshots, passNumber, spawnBudgetTick = -1;
    private final Map<String, NamespacedKey> stackAttributeKeys = new HashMap<>();
    private final Set<NamespacedKey> allowedMobKeys = new HashSet<>();
    private final List<NamespacedKey> recipes = new ArrayList<>();
    private final BukkitTask task;
    private int cursor;
    private final Map<IslandCollectors.Position, Integer> itemCursors = new HashMap<>();
    public IslandAutomation(NerdSmpPlugin plugin, SkyblockController sky) {
        this.plugin = plugin; this.sky = sky;
        collectors = new IslandCollectors(plugin.playerData(), sky.islands(), plugin.getConfig().getString("season.id", "season-1"));
        collectorKey = new NamespacedKey(plugin, "island_chunk_hopper"); spawnerCountKey = new NamespacedKey(plugin, "island_spawner_count");
        collectorFilterKey = new NamespacedKey(plugin, "island_collector_filter");
        mobCountKey = new NamespacedKey(plugin, "island_mob_count"); mobSourceKey = new NamespacedKey(plugin, "island_mob_source");
        mobEquipmentKey = new NamespacedKey(plugin, "island_mob_equipment");
        spawnerItemTypeKey = new NamespacedKey(plugin, "island_spawner_item_type");
        spawnerItemSchemaKey = new NamespacedKey(plugin, "island_spawner_item_schema");
        allowedMobKeys.addAll(List.of(mobCountKey, mobSourceKey, mobEquipmentKey));
        for (Attribute attribute : STACK_ATTRIBUTES) {
            String name = attribute.getKey().getKey();
            NamespacedKey key = new NamespacedKey(plugin, "island_mob_attribute_" + name);
            stackAttributeKeys.put(name, key); allowedMobKeys.add(key);
        }
        java.util.stream.Stream.of(SCALE_DOUBLES, SCALE_LONGS, SCALE_INTS, Set.of("mob_tier"))
            .flatMap(Set::stream).map(name -> new NamespacedKey(plugin, name)).forEach(allowedMobKeys::add);
        registerRecipes();
        for (Chunk chunk : sky.world().getLoadedChunks()) rememberChunk(chunk.getX(), chunk.getZ());
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::collect, 10, 10);
    }
    public String diagnostics() { return "Island automation: " + collectors.positions().size() + " collectors; " + pendingMobs.size()
        + " queued mobs; " + mergedBodies + " bodies merged (" + mergedMembers + " members); " + reconciliation.size()
        + " loaded chunks tracked; last pass " + String.format(Locale.ROOT, "%.2f", lastPassNanos / 1_000_000D)
        + " ms, " + lastNearbyChecks + "/" + IslandMobStackPolicy.NEARBY_CHECKS_PER_PASS + " nearby checks. Skips: "
        + stackRejections + ". No chunk tickets."; }
    /** Cached counters only. Call on the server thread when publishing a telemetry snapshot. */
    public StackDiagnostics stackDiagnostics() {
        return new StackDiagnostics(pendingMobs.size(), reconciliation.size(), mergedBodies, mergedMembers, markedBodies,
            reconciledChunks, reconciliationInspections, lastCandidateWork, lastNearbyChecks, lastChunkSnapshots,
            lastPassNanos, maxPassNanos, Map.copyOf(stackRejections));
    }
    public record StackDiagnostics(int queuedMobs, int trackedChunks, long mergedBodies, long mergedMembers,
        long markedBodies, long reconciledChunks, long reconciliationInspections, int lastCandidates,
        int lastNearbyChecks, int lastChunkSnapshots, long lastPassNanos, long maxPassNanos, Map<String, Long> rejections) {
        public StackDiagnostics { rejections = Map.copyOf(rejections); }
    }
    private void reject(String reason) { stackRejections.merge(reason, 1L, Long::sum); }
    private void rememberChunk(int x, int z) {
        ChunkPosition position = new ChunkPosition(x, z);
        if (!reconciliation.contains(position) && reconciliation.size() >= IslandMobStackPolicy.MAX_TRACKED_CHUNKS) {
            reject("tracked_chunk_budget"); return;
        }
        if (sky.islands().at(x, z).isPresent()) reconciliation.add(position);
    }
    public int collectorCount(AccountId owner) { return collectors.count(owner); }
    public void discover(Player player) { player.discoverRecipes(recipes); }
    private IslandService.Island island(Block block) { return sky.isIslandWorld(block.getWorld()) ? sky.islands().at(block.getX() >> 4, block.getZ() >> 4).orElse(null) : null; }
    private AccountId account(Player player) { return plugin.sessions().account(player.getUniqueId()).orElse(null); }
    private static IslandCollectors.Position position(Block block) { return new IslandCollectors.Position(block.getX(), block.getY(), block.getZ()); }
    private boolean collector(ItemStack item) { return item != null && item.getType() == Material.HOPPER && item.getItemMeta().getPersistentDataContainer().has(collectorKey, PersistentDataType.BYTE); }
    private boolean collector(Block block) { return sky.isIslandWorld(block.getWorld()) && collectors.owner(position(block)) != null; }
    private ItemStack collectorItem() {
        ItemStack item = new ItemStack(Material.HOPPER); var meta = item.getItemMeta();
        meta.displayName(Component.text("Chunk Hopper", NamedTextColor.AQUA).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(List.of("Collects unowned drops across one chunk.", "Requires Collection network. One per chunk.", "Sneak + right-click with an item to set a filter.", "Empty hand clears the filter. Redstone pauses it.", "Coin purses and player-dropped items stay put.").stream()
            .map(line -> Component.text(line, NamedTextColor.GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)).toList());
        meta.getPersistentDataContainer().set(collectorKey, PersistentDataType.BYTE, (byte) 1); item.setItemMeta(meta); return item;
    }
    private ItemStack spawnerItem(EntityType type, int amount) {
        var definition = IslandSpawnerCatalog.find(type).orElseThrow(() -> new IllegalArgumentException("Unsupported island spawner"));
        ItemStack item = new ItemStack(Material.SPAWNER, amount); BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        CreatureSpawner state = (CreatureSpawner) meta.getBlockState(); state.setSpawnedType(type); meta.setBlockState(state);
        meta.displayName(Component.text(definition.title(), NamedTextColor.GOLD).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        List<String> lore = new ArrayList<>(List.of("Place on your island.", "Right-click a matching spawner to stack.", "Sneak and break to recover every spawner."));
        if (!definition.legacy()) lore.add("Requires " + definition.unlockStage().title() + " resources.");
        meta.lore(lore.stream()
            .map(line -> Component.text(line, NamedTextColor.GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)).toList());
        meta.getPersistentDataContainer().set(spawnerItemTypeKey, PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(spawnerItemSchemaKey, PersistentDataType.INTEGER, IslandSpawnerCatalog.ITEM_SCHEMA);
        item.setItemMeta(meta); return item;
    }
    private boolean authenticSpawner(ItemStack item, EntityType type) {
        if (item == null || item.getType() != Material.SPAWNER || !(item.getItemMeta() instanceof BlockStateMeta meta)
            || !(meta.getBlockState() instanceof CreatureSpawner state)) return false;
        var data = meta.getPersistentDataContainer();
        if (data.getKeys().contains(spawnerItemTypeKey) && !data.has(spawnerItemTypeKey, PersistentDataType.STRING)
            || data.getKeys().contains(spawnerItemSchemaKey) && !data.has(spawnerItemSchemaKey, PersistentDataType.INTEGER)) return false;
        return IslandSpawnerCatalog.authentic(type, state.getSpawnedType(), data.get(spawnerItemTypeKey, PersistentDataType.STRING),
            data.get(spawnerItemSchemaKey, PersistentDataType.INTEGER));
    }
    private boolean unlockedSpawner(IslandService.Island island, EntityType type, Player player) {
        var definition = IslandSpawnerCatalog.find(type);
        if (definition.isEmpty()) { Chat.error(player, "That mob does not have an island spawner recipe."); return false; }
        if (definition.get().legacy() || IslandSpawnerCatalog.unlocked(type, sky.resources().effectiveStage(island.owner()))) return true;
        Chat.error(player, "Unlock <white>" + definition.get().unlockStage().title() + "</white> resources in "
            + Chat.command("island journal") + " to place or stack this spawner.");
        return false;
    }
    private void registerRecipes() {
        NamespacedKey hopper = new NamespacedKey(plugin, "island_chunk_hopper_recipe"); recipes.add(hopper);
        ShapedRecipe recipe = new ShapedRecipe(hopper, collectorItem()); recipe.shape("CCC", "CHC", "CCC");
        recipe.setIngredient('C', Material.COPPER_INGOT); recipe.setIngredient('H', Material.HOPPER); Bukkit.addRecipe(recipe);
        IslandSpawnerCatalog.all().forEach(definition -> {
            NamespacedKey key = new NamespacedKey(plugin, definition.recipeKey()); recipes.add(key);
            ShapedRecipe spawner = new ShapedRecipe(key, spawnerItem(definition.type(), 1)); spawner.shape(IslandSpawnerCatalog.RECIPE_SHAPE.toArray(String[]::new));
            spawner.setIngredient('I', Material.IRON_BLOCK); spawner.setIngredient('F', definition.ingredient()); spawner.setIngredient('D', Material.DIAMOND); Bukkit.addRecipe(spawner);
        });
    }
    private boolean automationRecipe(Recipe recipe) { return recipe instanceof Keyed keyed && recipes.contains(keyed.getKey()); }
    static boolean ordinaryIngredients(ItemStack[] matrix) {
        for (ItemStack item : matrix) if (item != null && !item.getType().isAir() && item.hasItemMeta()) return false;
        return true;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepare(PrepareItemCraftEvent event) {
        if (automationRecipe(event.getRecipe()) && !ordinaryIngredients(event.getInventory().getMatrix())) event.getInventory().setResult(null);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (automationRecipe(event.getRecipe()) && !ordinaryIngredients(event.getInventory().getMatrix())) {
            event.setCancelled(true); Chat.error(event.getWhoClicked(), "Use ordinary materials for island automation recipes.");
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAutomatedCraft(CrafterCraftEvent event) { if (automationRecipe(event.getRecipe())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!collector(event.getItemInHand()) && event.getBlock().getType() != Material.SPAWNER) return;
        IslandService.Island island = island(event.getBlock());
        if (island == null || !island.member(account(event.getPlayer()))) { event.setCancelled(true); Chat.error(event.getPlayer(), "Place island machines on an island you belong to."); return; }
        if (event.getBlock().getState() instanceof CreatureSpawner spawner) {
            if (!authenticSpawner(event.getItemInHand(), spawner.getSpawnedType())) {
                event.setCancelled(true); Chat.error(event.getPlayer(), "Use a crafted island spawner. " + Chat.command("island machines") + " shows the recipes."); return;
            }
            if (!unlockedSpawner(island, spawner.getSpawnedType(), event.getPlayer())) { event.setCancelled(true); return; }
        }
        if (collector(event.getItemInHand())) {
            boolean registered = false;
            try {
                collectors.add(account(event.getPlayer()), island.owner(), position(event.getBlock()), sky.development().progress(island.owner()).hopperLimit());
                registered = true;
                TileState state = (TileState) event.getBlock().getState();
                state.getPersistentDataContainer().set(collectorKey, PersistentDataType.BYTE, (byte) 1);
                if (!state.update()) throw new IllegalStateException("Could not mark the collector block");
            } catch (IllegalArgumentException refusal) { event.setCancelled(true); Chat.error(event.getPlayer(), Chat.escape(refusal.getMessage())); }
            catch (RuntimeException error) {
                event.setCancelled(true); plugin.getLogger().log(java.util.logging.Level.SEVERE, "Chunk hopper placement failed", error);
                Chat.error(event.getPlayer(), "The chunk hopper could not be placed. Try again.");
            }
            // A later plugin may cancel placement. Reconcile the index on the next server tick.
            if (registered) Bukkit.getScheduler().runTask(plugin, () -> {
                if (event.isCancelled()) { try { collectors.remove(position(event.getBlock())); } catch (RuntimeException error) { plugin.getLogger().severe("Collector rollback needs review: " + error); } }
            });
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock(); IslandService.Island island = island(block);
        if (island != null && collector(block) && event.getPlayer().isSneaking() && block.getState() instanceof Hopper hopper) {
            event.setCancelled(true);
            if (!island.member(account(event.getPlayer()))) return;
            Material held = event.getPlayer().getInventory().getItemInMainHand().getType();
            if (held.isAir()) hopper.getPersistentDataContainer().remove(collectorFilterKey);
            else hopper.getPersistentDataContainer().set(collectorFilterKey, PersistentDataType.STRING, held.name());
            if (!hopper.update()) { Chat.error(event.getPlayer(), "The filter could not be saved. Try again."); return; }
            if (held.isAir()) Chat.success(event.getPlayer(), "Hopper filter cleared. Collecting all allowed drops.");
            else Chat.success(event.getPlayer(), "Hopper filter: <white>" + net.nerdsmp.paper.presentation.VisualFormat.titleCase(held.name().toLowerCase(Locale.ROOT))
                + "</white>. <gray>Sneak + right-click with an empty hand to clear.</gray>");
            return;
        }
        if (island == null || !(block.getState() instanceof CreatureSpawner spawner)) return;
        event.setCancelled(true);
        if (!island.member(account(event.getPlayer()))) return;
        int count = count(spawner); int limit = sky.development().progress(island.owner()).stackLimit();
        ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        if (held.getType() == Material.SPAWNER && held.getItemMeta() instanceof BlockStateMeta meta
            && meta.getBlockState() instanceof CreatureSpawner source && source.getSpawnedType() == spawner.getSpawnedType()
            && authenticSpawner(held, source.getSpawnedType()) && event.getPlayer().getGameMode() == GameMode.SURVIVAL && count < limit) {
            if (!unlockedSpawner(island, source.getSpawnedType(), event.getPlayer())) return;
            spawner.getPersistentDataContainer().set(spawnerCountKey, PersistentDataType.INTEGER, count + 1);
            if (spawner.update()) { held.setAmount(held.getAmount() - 1); count++; }
        }
        Chat.info(event.getPlayer(), "Spawner stack <white>" + count + "/" + limit + "</white> • Sneak and break to recover the stack.");
    }
    private int count(CreatureSpawner spawner) {
        var data = spawner.getPersistentDataContainer();
        if (!data.has(spawnerCountKey, PersistentDataType.INTEGER)) return 1;
        Integer count = data.get(spawnerCountKey, PersistentDataType.INTEGER);
        return count != null && count >= 1 && count <= 16 ? count : 1;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock(); IslandService.Island island = island(block);
        if (island == null || !(block.getState() instanceof CreatureSpawner) && !collector(block)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!island.member(account(player)) || !player.isSneaking()) { Chat.notice(player, "Sneak and break to recover an island machine."); return; }
        ItemStack returned;
        if (block.getState() instanceof CreatureSpawner spawner) {
            if (!IslandSpawnerCatalog.supports(spawner.getSpawnedType())) return;
            returned = spawnerItem(spawner.getSpawnedType(), count(spawner));
        } else {
            if (!(block.getState() instanceof Hopper hopper) || !hopper.getInventory().isEmpty()) { Chat.error(player, "Empty the hopper before picking it up."); return; }
            try { collectors.remove(position(block)); itemCursors.remove(position(block)); } catch (RuntimeException error) { Chat.error(player, "The chunk hopper could not be saved. Nothing was removed."); return; }
            returned = collectorItem();
        }
        block.setType(Material.AIR);
        if (player.getGameMode() == GameMode.SURVIVAL) block.getWorld().dropItemNaturally(block.getLocation().add(.5, .5, .5), returned);
    }
    private boolean protectedMachine(Block block) { return sky.isIslandWorld(block.getWorld()) && (block.getType() == Material.SPAWNER || collector(block)); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onExplode(EntityExplodeEvent event) { event.blockList().removeIf(this::protectedMachine); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onBlockExplode(BlockExplodeEvent event) { event.blockList().removeIf(this::protectedMachine); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onPiston(BlockPistonExtendEvent event) { if (event.getBlocks().stream().anyMatch(this::protectedMachine)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onRetract(BlockPistonRetractEvent event) { if (event.getBlocks().stream().anyMatch(this::protectedMachine)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onChange(EntityChangeBlockEvent event) { if (protectedMachine(event.getBlock())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(SpawnerSpawnEvent event) {
        if (event.getSpawner() == null || !(event.getEntity() instanceof LivingEntity entity) || !IslandSpawnerCatalog.supports(entity.getType())) return;
        IslandService.Island island = island(event.getSpawner().getBlock()); if (island == null) return;
        if (sky.islands().at(entity.getLocation().getBlockX() >> 4, entity.getLocation().getBlockZ() >> 4)
            .filter(i -> i.id().equals(island.id())).isEmpty()) { event.setCancelled(true); return; }
        SpawnBudget budget = spawnBudget(entity);
        int added = Math.min(count(event.getSpawner()), sky.development().progress(island.owner()).stackLimit());
        // A cached per-tick chunk budget avoids a full chunk and pending-queue scan for every
        // member in a spawner burst. Queueing at MONITOR reserves the complete accepted roll.
        int represented = budget.members - budget.counted.getOrDefault(entity.getUniqueId(), 0);
        if (budget.crowded || represented > IslandMobStackPolicy.MAX_CHUNK_MEMBERS - added) {
            event.setCancelled(true); reject("spawner_population_cap"); return;
        }
        if (pendingMobs.size() >= IslandMobStackPolicy.MAX_PENDING && !pendingMobs.containsKey(entity.getUniqueId())) {
            event.setCancelled(true); reject("spawner_queue_full"); return;
        }
        entity.setCanPickupItems(false);
        if (entity instanceof Zombie zombie) zombie.setAdult();
        // Do not merge yet: another listener can cancel this spawn or turn it into an elite.
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void queueSpawner(SpawnerSpawnEvent event) {
        if (event.isCancelled() || event.getSpawner() == null || !(event.getEntity() instanceof LivingEntity entity) || !IslandSpawnerCatalog.supports(entity.getType())) return;
        IslandService.Island island = island(event.getSpawner().getBlock());
        if (island != null) queueMob(entity, island, Math.min(count(event.getSpawner()), sky.development().progress(island.owner()).stackLimit()));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void queueNatural(CreatureSpawnEvent event) {
        if (event.isCancelled() || !IslandMobStackPolicy.ambient(event.getSpawnReason().name()) || !IslandMobStackPolicy.supported(event.getEntityType().name())) return;
        LivingEntity entity = event.getEntity();
        if (!sky.isIslandWorld(entity.getWorld())) return;
        sky.islands().at(entity.getLocation().getBlockX() >> 4, entity.getLocation().getBlockZ() >> 4)
            .ifPresent(island -> queueMob(entity, island, 1));
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void queueLoaded(ChunkLoadEvent event) {
        if (!sky.isIslandWorld(event.getWorld())) return;
        IslandService.Island island = sky.islands().at(event.getChunk().getX(), event.getChunk().getZ()).orElse(null);
        if (island == null) return;
        rememberChunk(event.getChunk().getX(), event.getChunk().getZ());
        ChunkPosition position = new ChunkPosition(event.getChunk().getX(), event.getChunk().getZ());
        if (reconciliation.contains(position)) reconciliation.addFirst(position);
        // Entity restoration may finish after ChunkLoadEvent. The paced reconciliation reads it later.
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void forgetLoaded(ChunkUnloadEvent event) {
        if (!sky.isIslandWorld(event.getWorld())) return;
        ChunkPosition position = new ChunkPosition(event.getChunk().getX(), event.getChunk().getZ());
        reconciliation.remove(position); reconciliationCursors.remove(position); spawnBudgets.remove(position);
        // Persist already accepted multi-spawner rolls before the unloading chunk is saved.
        for (StackCandidate candidate : List.copyOf(pendingMobs.values()))
            if (chunkPosition(candidate.entity()).equals(position)) retainPendingMembers(candidate.entity());
        pendingMobs.values().removeIf(candidate -> chunkPosition(candidate.entity()).equals(position));
    }
    private void queueMob(LivingEntity entity, IslandService.Island island, int added) {
        // A chunk-load callback must not replace a pending multi-spawner roll with a single member.
        StackCandidate pending = pendingMobs.get(entity.getUniqueId());
        if (pending != null && pending.added() >= added) return;
        if (!pendingMobs.containsKey(entity.getUniqueId()) && pendingMobs.size() >= IslandMobStackPolicy.MAX_PENDING) {
            reject("queue_full"); return;
        }
        pendingMobs.put(entity.getUniqueId(), new StackCandidate(entity, island.id(), added, Bukkit.getCurrentTick()));
        ChunkPosition position = chunkPosition(entity); rememberChunk(position.x(), position.z());
        SpawnBudget budget = spawnBudgets.get(position);
        if (budget != null && spawnBudgetTick == Bukkit.getCurrentTick()) {
            int represented = Math.max(added, mobCount(entity));
            budget.members += represented - budget.counted.getOrDefault(entity.getUniqueId(), 0);
            budget.counted.put(entity.getUniqueId(), represented);
        }
    }
    /** Bounded loaded-only work. Spawn callbacks have finished before a candidate is inspected. */
    public void processMobStacks() {
        long started = System.nanoTime();
        StackPass pass = new StackPass(started);
        // Reconciliation also handles startup-loaded mobs, moving mobs and temporary damage/fire.
        // Spawn work stays ahead of the round-robin queue; a busy queue still receives one scan
        // every fourth pass, rather than starving old chunks indefinitely.
        if (pendingMobs.isEmpty() || (++passNumber & 3) == 0) reconcileChunk(pass);
        while (!pendingMobs.isEmpty() && pass.candidates < IslandMobStackPolicy.WORK_PER_PASS && !pass.exhausted()) {
            Iterator<StackCandidate> iterator = pendingMobs.values().iterator();
            StackCandidate candidate = iterator.next(); iterator.remove(); pass.candidates++; pass.current = candidate;
            LivingEntity entity = candidate.entity();
            if (!entity.isValid() || entity.isDead() || !sky.isIslandWorld(entity.getWorld())) { reject("gone"); continue; }
            Location location = entity.getLocation();
            if (!entity.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) continue;
            IslandService.Island island = sky.islands().at(location.getBlockX() >> 4, location.getBlockZ() >> 4).orElse(null);
            if (island == null || !island.id().equals(candidate.island())) continue;
            String source = mobSource(entity);
            if (source != null && !source.equals(island.id().toString())) { reject("different_island"); continue; }
            boolean mergeable = clean(entity);
            // A spawner roll still represents its configured members if a grinder has already hurt
            // its first body. That body may be marked, but it must not merge with another hurt mob.
            boolean freshSpawnerRoll = source == null && candidate.added() > 1 && ordinaryIdentity(entity);
            if (!mergeable && !freshSpawnerRoll) { reject(rejectionReason(entity)); continue; }
            // Another candidate may already have merged into this queued saved body this pass.
            // Always read its current saved count, not the stale chunk-load snapshot.
            int added = source == null ? candidate.added() : mobCount(entity);
            LivingEntity match = null;
            var signature = signature(entity);
            if (mergeable) {
                // Try the current chunk first. Cross-border neighbors share the same global pass
                // cache/budget; no getNearbyEntities call or chunk ticket is used.
                ChunkPosition origin = chunkPosition(entity);
                List<ChunkPosition> neighbors = new ArrayList<>(); neighbors.add(origin);
                int minX = (location.getBlockX() - 6) >> 4, maxX = (location.getBlockX() + 6) >> 4;
                int minZ = (location.getBlockZ() - 6) >> 4, maxZ = (location.getBlockZ() + 6) >> 4;
                for (int cx = minX; cx <= maxX; cx++) for (int cz = minZ; cz <= maxZ; cz++) {
                    ChunkPosition neighbor = new ChunkPosition(cx, cz);
                    if (!neighbor.equals(origin)) neighbors.add(neighbor);
                }
                search: for (ChunkPosition neighbor : neighbors) {
                    if (pass.exhausted()) break;
                    ChunkSnapshot snapshot = pass.snapshot(neighbor);
                    if (snapshot == null) continue;
                    for (LivingEntity living : snapshot.mobs) {
                        if (pass.exhausted()) break search;
                        pass.inspections++;
                        if (living.getUniqueId().equals(entity.getUniqueId()) || living.getType() != entity.getType()
                            || !living.isValid() || !island.id().toString().equals(mobSource(living))) continue;
                        Location destination = living.getLocation();
                        double distance = destination.distanceSquared(location);
                        if (distance > IslandMobStackPolicy.RANGE_SQUARED || !chunkPosition(living).equals(neighbor) || !clean(living)
                            || !IslandMobStackPolicy.mergeRoom(mobCount(living), added, snapshot.members, origin.equals(neighbor))
                            || sky.islands().at(destination.getBlockX() >> 4, destination.getBlockZ() >> 4)
                                .filter(home -> home.id().equals(island.id())).isEmpty()) continue;
                        if (IslandMobStackPolicy.equivalent(island.id(), island.id(), signature, signature(living),
                            distance)) { match = living; break search; }
                    }
                }
            }
            if (match != null) {
                rememberMember(match);
                match.getPersistentDataContainer().set(mobCountKey, PersistentDataType.INTEGER, mobCount(match) + added);
                match.setCanPickupItems(false); match.setPersistent(true); match.setRemoveWhenFarAway(false);
                entity.remove(); // No death event: this member will roll its loot when actually killed.
                mergedBodies++; mergedMembers += added;
                pass.transferred(chunkPosition(entity), chunkPosition(match), added);
            } else {
                rememberMember(entity);
                entity.getPersistentDataContainer().set(mobCountKey, PersistentDataType.INTEGER, added);
                entity.getPersistentDataContainer().set(mobSourceKey, PersistentDataType.STRING, island.id().toString());
                entity.setCanPickupItems(false);
                if (added > 1) { entity.setPersistent(true); entity.setRemoveWhenFarAway(false); }
                if (source == null) markedBodies++;
                if (mergeable) reject(pass.exhausted() ? "pass_budget" : "no_compatible_neighbor");
            }
        }
        spawnBudgets.clear();
        lastCandidateWork = pass.candidates; lastNearbyChecks = pass.inspections; lastChunkSnapshots = pass.snapshots.size();
        lastPassNanos = Math.max(0, System.nanoTime() - started); maxPassNanos = Math.max(maxPassNanos, lastPassNanos);
    }
    private static ChunkPosition chunkPosition(Entity entity) {
        Location location = entity.getLocation();
        return new ChunkPosition(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }
    private SpawnBudget spawnBudget(LivingEntity incoming) {
        int tick = Bukkit.getCurrentTick();
        if (spawnBudgetTick != tick) { spawnBudgets.clear(); spawnBudgetTick = tick; }
        ChunkPosition position = chunkPosition(incoming);
        SpawnBudget existing = spawnBudgets.get(position);
        if (existing != null) return existing;
        if (spawnBudgets.size() >= IslandMobStackPolicy.MAX_SPAWN_BUDGET_CHUNKS) {
            reject("spawner_chunk_budget"); return new SpawnBudget(true);
        }
        Entity[] entities = incoming.getChunk().getEntities();
        SpawnBudget result = new SpawnBudget(entities.length > IslandMobStackPolicy.ENTITIES_PER_CHUNK);
        for (int i = 0; i < Math.min(entities.length, IslandMobStackPolicy.ENTITIES_PER_CHUNK); i++) {
            if (!(entities[i] instanceof LivingEntity living) || !living.isValid()
                || !IslandMobStackPolicy.supported(living.getType().name())) continue;
            StackCandidate pending = pendingMobs.get(living.getUniqueId());
            int count = Math.max(mobCount(living), pending == null ? 1 : pending.added());
            result.counted.put(living.getUniqueId(), count); result.members += count;
        }
        spawnBudgets.put(position, result); return result;
    }
    private void reconcileChunk(StackPass pass) {
        if (reconciliation.isEmpty()) return;
        Iterator<ChunkPosition> iterator = reconciliation.iterator();
        ChunkPosition position = iterator.next(); iterator.remove();
        if (!sky.world().isChunkLoaded(position.x(), position.z())
            || sky.islands().at(position.x(), position.z()).isEmpty()) {
            reconciliationCursors.remove(position); return;
        }
        reconciliation.add(position);
        ChunkSnapshot snapshot = pass.snapshot(position);
        if (snapshot == null) return;
        reconciledChunks++;
        int inspected = 0;
        for (LivingEntity living : snapshot.mobs) {
            if (inspected++ >= IslandMobStackPolicy.RECONCILE_ENTITIES_PER_PASS || pass.exhausted()) break;
            reconciliationInspections++;
            if (!living.isValid() || living.isDead() || !chunkPosition(living).equals(position)
                || !(living.getPersistentDataContainer().has(mobSourceKey, PersistentDataType.STRING)
                    || IslandMobStackPolicy.ambient(living.getEntitySpawnReason().name()) || living.fromMobSpawner())) continue;
            sky.islands().at(position.x(), position.z()).ifPresent(island -> queueMob(living, island, mobCount(living)));
        }
        // The raw-array cursor advances even if every inspected entity is an item or protected mob.
        reconciliationCursors.merge(position, IslandMobStackPolicy.RECONCILE_ENTITIES_PER_PASS,
            (left, right) -> (left + right) & Integer.MAX_VALUE);
    }
    private String rejectionReason(LivingEntity entity) {
        if (entity.customName() != null) return "named";
        if (plugin.mobs().isBoss(entity) || plugin.mobs().isElite(entity) || plugin.mobs().isCustomMob(entity)) return "authored_or_elite";
        if (!allowedMobKeys.containsAll(entity.getPersistentDataContainer().getKeys())) return "foreign_metadata";
        if (!entity.getActivePotionEffects().isEmpty()) return "active_effect";
        if (entity.getAttribute(Attribute.MAX_HEALTH) != null
            && entity.getHealth() + .01 < entity.getAttribute(Attribute.MAX_HEALTH).getValue()) return "hurt";
        if (entity.getFireTicks() > 0 || entity.getFreezeTicks() > 0) return "burning_or_frozen";
        EntityEquipment gear = entity.getEquipment();
        if (gear != null && Arrays.stream(gear.getArmorContents()).anyMatch(item -> item != null && !item.getType().isAir()))
            return "equipped";
        for (Attribute attribute : STACK_ATTRIBUTES) {
            var instance = entity.getAttribute(attribute);
            if (instance != null && !instance.getModifiers().isEmpty()) return "attribute_modifiers";
        }
        return "protected_or_modified";
    }
    private record ChunkPosition(int x, int z) { }
    private static final class SpawnBudget {
        final boolean crowded;
        final Map<UUID, Integer> counted = new HashMap<>();
        int members;
        SpawnBudget(boolean crowded) { this.crowded = crowded; }
    }
    private static final class ChunkSnapshot {
        final List<LivingEntity> mobs;
        int members;
        ChunkSnapshot(List<LivingEntity> mobs, int members) { this.mobs = mobs; this.members = members; }
    }
    private final class StackPass {
        final long started;
        final Map<ChunkPosition, ChunkSnapshot> snapshots = new LinkedHashMap<>();
        int candidates, inspections;
        StackCandidate current;
        StackPass(long started) { this.started = started; }
        boolean exhausted() {
            return inspections >= IslandMobStackPolicy.NEARBY_CHECKS_PER_PASS
                || System.nanoTime() - started >= IslandMobStackPolicy.SOFT_PASS_NANOS;
        }
        ChunkSnapshot snapshot(ChunkPosition position) {
            ChunkSnapshot cached = snapshots.get(position);
            if (cached != null) return cached;
            if (snapshots.size() >= IslandMobStackPolicy.MAX_CHUNKS_PER_PASS || exhausted()
                || !sky.world().isChunkLoaded(position.x(), position.z())
                || sky.islands().at(position.x(), position.z()).isEmpty()) return null;
            Entity[] entities = sky.world().getChunkAt(position.x(), position.z()).getEntities();
            int start = reconciliationCursors.getOrDefault(position, 0);
            List<LivingEntity> bodies = new ArrayList<>(); int members = 0;
            int inspected = 0;
            for (int n = 0; n < Math.min(entities.length, IslandMobStackPolicy.ENTITIES_PER_CHUNK); n++) {
                if (exhausted()) break;
                inspections++; inspected++;
                Entity entity = entities[Math.floorMod(start + n, entities.length)];
                if (!(entity instanceof LivingEntity living) || !living.isValid()
                    || !IslandMobStackPolicy.supported(living.getType().name())) continue;
                bodies.add(living);
                StackCandidate pending = pendingMobs.get(living.getUniqueId());
                if (pending == null && current != null && current.entity().getUniqueId().equals(living.getUniqueId())) pending = current;
                members += Math.max(mobCount(living), pending == null ? 1 : pending.added());
            }
            // Unknown tail population blocks importing members, not consolidating existing bodies.
            if (inspected < entities.length)
                members = Math.max(members, IslandMobStackPolicy.MAX_CHUNK_MEMBERS);
            ChunkSnapshot result = new ChunkSnapshot(bodies, members); snapshots.put(position, result); return result;
        }
        void transferred(ChunkPosition source, ChunkPosition destination, int members) {
            if (source.equals(destination)) return;
            ChunkSnapshot from = snapshots.get(source), to = snapshots.get(destination);
            if (from != null) from.members = Math.max(0, from.members - members);
            if (to != null) to.members += members;
        }
    }
    private boolean clean(LivingEntity entity) {
        return entity.isValid() && !entity.isDead() && entity.getAttribute(Attribute.MAX_HEALTH) != null
            && entity.getHealth() + .01 >= entity.getAttribute(Attribute.MAX_HEALTH).getValue()
            && entity.getFireTicks() <= 0 && entity.getFreezeTicks() <= 0
            && !(entity instanceof Creeper creeper && (creeper.isPowered() || creeper.isIgnited() || creeper.getFuseTicks() > 0))
            && !(entity instanceof Enderman enderman && (enderman.getCarriedBlock() != null || enderman.isScreaming()))
            && !(entity instanceof PigZombie piglin && piglin.isAngry()) && ordinaryIdentity(entity);
    }
    private boolean ordinaryIdentity(LivingEntity entity) {
        if (!IslandMobStackPolicy.supported(entity.getType().name()) || entity.isInvulnerable() || !entity.hasAI()
            || entity.customName() != null || entity.isLeashed() || !entity.getPassengers().isEmpty()
            || entity.isInsideVehicle() || !entity.getActivePotionEffects().isEmpty() || plugin.mobs().isBoss(entity) || plugin.mobs().isElite(entity)
            || plugin.mobs().isCustomMob(entity) || entity.getAttribute(Attribute.MAX_HEALTH) == null
            || entity instanceof Zombie zombie && zombie.isBaby()
            || entity instanceof Mob mob && !mob.isAware()) return false;
        if (!allowedMobKeys.containsAll(entity.getPersistentDataContainer().getKeys())) return false;
        var data = entity.getPersistentDataContainer();
        if (data.getKeys().contains(mobCountKey) && (!data.has(mobCountKey, PersistentDataType.INTEGER)
            || data.get(mobCountKey, PersistentDataType.INTEGER) < 1 || data.get(mobCountKey, PersistentDataType.INTEGER) > IslandMobStackPolicy.MAX_STACK)) return false;
        if (data.getKeys().contains(mobSourceKey) && !data.has(mobSourceKey, PersistentDataType.STRING)) return false;
        if (data.getKeys().contains(mobEquipmentKey) && (!data.has(mobEquipmentKey, PersistentDataType.STRING)
            || !Set.of("AIR", "BOW", "GOLDEN_SWORD").contains(data.get(mobEquipmentKey, PersistentDataType.STRING)))) return false;
        for (NamespacedKey key : stackAttributeKeys.values()) if (data.getKeys().contains(key)
            && (!data.has(key, PersistentDataType.DOUBLE) || !Double.isFinite(data.get(key, PersistentDataType.DOUBLE))
                || data.get(key, PersistentDataType.DOUBLE) < 0)) return false;
        for (Attribute attribute : STACK_ATTRIBUTES) {
            var value = entity.getAttribute(attribute);
            if (value != null && (!value.getModifiers().isEmpty() || !Double.isFinite(value.getBaseValue()) || value.getBaseValue() < 0)) return false;
        }
        try { signature(entity); } catch (IllegalArgumentException error) { return false; }
        EntityEquipment gear = entity.getEquipment();
        if (gear == null) return true;
        if (Arrays.stream(gear.getArmorContents()).anyMatch(i -> i != null && !i.getType().isAir())) return false;
        // Ordinary skeleton bows and zombified piglin swords are their attack mechanisms.
        return gear.getItemInOffHand().getType().isAir() && (gear.getItemInMainHand().getType().isAir()
            || (entity.getType() == EntityType.SKELETON || entity.getType() == EntityType.STRAY)
                && gear.getItemInMainHand().getType() == Material.BOW && !gear.getItemInMainHand().hasItemMeta()
            || entity.getType() == EntityType.ZOMBIFIED_PIGLIN && gear.getItemInMainHand().getType() == Material.GOLDEN_SWORD
                && !gear.getItemInMainHand().hasItemMeta());
    }
    private IslandMobStackPolicy.Signature signature(LivingEntity entity) {
        Map<String, Double> attributes = new HashMap<>();
        for (Attribute attribute : STACK_ATTRIBUTES) {
            var value = entity.getAttribute(attribute);
            if (value != null) attributes.put(attribute.getKey().getKey(), value.getBaseValue());
        }
        Map<String, Object> scale = new HashMap<>();
        for (String name : SCALE_DOUBLES) readScale(entity, name, PersistentDataType.DOUBLE, scale);
        for (String name : SCALE_LONGS) readScale(entity, name, PersistentDataType.LONG, scale);
        for (String name : SCALE_INTS) readScale(entity, name, PersistentDataType.INTEGER, scale);
        readScale(entity, "mob_tier", PersistentDataType.STRING, scale);
        if (scale.containsKey("mob_tier") && !"STANDARD".equals(scale.get("mob_tier"))) throw new IllegalArgumentException("Not an ordinary mob");
        EntityEquipment equipment = entity.getEquipment();
        return new IslandMobStackPolicy.Signature(entity.getType().name(), (Integer) scale.getOrDefault("mob_level", 1), attributes,
            scale, equipment == null ? "AIR" : equipment.getItemInMainHand().getType().name());
    }
    private <P, C> void readScale(LivingEntity entity, String name, PersistentDataType<P, C> type, Map<String, Object> result) {
        NamespacedKey key = new NamespacedKey(plugin, name);
        var data = entity.getPersistentDataContainer();
        if (!data.getKeys().contains(key)) return;
        C value = data.get(key, type);
        if (value == null || value instanceof Number number && !Double.isFinite(number.doubleValue()))
            throw new IllegalArgumentException("Malformed mob scale");
        result.put(name, value);
    }
    private void rememberMember(LivingEntity entity) {
        for (Attribute attribute : STACK_ATTRIBUTES) {
            var value = entity.getAttribute(attribute);
            if (value != null) entity.getPersistentDataContainer().set(stackAttributeKeys.get(attribute.getKey().getKey()),
                PersistentDataType.DOUBLE, value.getBaseValue());
        }
        EntityEquipment gear = entity.getEquipment();
        entity.getPersistentDataContainer().set(mobEquipmentKey, PersistentDataType.STRING,
            gear == null ? "AIR" : gear.getItemInMainHand().getType().name());
    }
    public int mobCount(LivingEntity entity) {
        var data = entity.getPersistentDataContainer();
        if (!data.has(mobCountKey, PersistentDataType.INTEGER)) return 1;
        Integer count = data.get(mobCountKey, PersistentDataType.INTEGER);
        return count != null && count >= 1 && count <= IslandMobStackPolicy.MAX_STACK ? count : 1;
    }
    private String mobSource(LivingEntity entity) {
        var data = entity.getPersistentDataContainer();
        if (!data.has(mobSourceKey, PersistentDataType.STRING)) return null;
        String source = data.get(mobSourceKey, PersistentDataType.STRING);
        if (source == null) return null;
        try { return UUID.fromString(source).toString().equals(source) ? source : null; }
        catch (IllegalArgumentException malformed) { return null; }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (event.isCancelled()) return;
        spawnBudgets.clear();
        LivingEntity dead = event.getEntity();
        retainPendingMembers(dead);
        scheduleNextMember(dead, () -> !event.isCancelled() && dead.isDead());
    }
    private void retainPendingMembers(LivingEntity dead) {
        StackCandidate pending = pendingMobs.remove(dead.getUniqueId());
        if (pending != null && pending.added() > 1 && mobSource(dead) == null && sky.isIslandWorld(dead.getWorld())
            && ordinaryIdentity(dead) && sky.islands().at(dead.getLocation().getBlockX() >> 4, dead.getLocation().getBlockZ() >> 4)
                .filter(home -> home.id().equals(pending.island())).isPresent()) {
            // A rapid grinder can kill a newly spawned body before the bounded merge pass runs.
            // Retain those already reserved spawner members instead of silently losing the stack.
            rememberMember(dead);
            dead.getPersistentDataContainer().set(mobCountKey, PersistentDataType.INTEGER, pending.added());
            dead.getPersistentDataContainer().set(mobSourceKey, PersistentDataType.STRING, pending.island().toString());
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStackExplosion(EntityExplodeEvent event) {
        if (event.getEntity() instanceof Creeper creeper) {
            retainPendingMembers(creeper);
            // An explosion retires one member without rolling death loot or skill XP.
            scheduleNextMember(creeper, () -> !event.isCancelled() && !creeper.isValid());
        }
    }
    private void scheduleNextMember(LivingEntity dead, java.util.function.BooleanSupplier retired) {
        int remaining = mobCount(dead) - 1;
        String source = mobSource(dead);
        if (remaining < 1 || source == null || !sky.isIslandWorld(dead.getWorld()) || !IslandMobStackPolicy.supported(dead.getType().name())) return;
        if (dead.getLocation().getY() < sky.world().getMinHeight() || sky.islands()
            .at(dead.getLocation().getBlockX() >> 4, dead.getLocation().getBlockZ() >> 4).filter(i -> i.id().toString().equals(source)).isEmpty()) return;
        if (!retiringMembers.add(dead.getUniqueId())) return;
        // Each real death runs the existing loot/XP pipeline once. Never multiply rolled gear or purses.
        Bukkit.getScheduler().runTask(plugin, () -> {
            retiringMembers.remove(dead.getUniqueId());
            if (!plugin.isEnabled() || !retired.getAsBoolean()) return;
            Location location = dead.getLocation();
            if (!dead.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                || sky.islands().at(location.getBlockX() >> 4, location.getBlockZ() >> 4).filter(i -> i.id().toString().equals(source)).isEmpty()) return;
            LivingEntity next = (LivingEntity) dead.getWorld().spawnEntity(location, dead.getType(), CreatureSpawnEvent.SpawnReason.CUSTOM);
            if (!next.isValid()) return;
            next.setCanPickupItems(false); if (next instanceof Zombie zombie) zombie.setAdult();
            EntityEquipment gear = next.getEquipment();
            if (gear != null) {
                gear.clear();
                // Never reroll bonus armor or copy equipment placed on a stack after it was created.
                var data = dead.getPersistentDataContainer();
                String weapon = data.has(mobEquipmentKey, PersistentDataType.STRING) ? data.get(mobEquipmentKey, PersistentDataType.STRING)
                    : dead.getType() == EntityType.SKELETON || dead.getType() == EntityType.STRAY ? "BOW" : "AIR";
                if ("BOW".equals(weapon)) gear.setItemInMainHand(new ItemStack(Material.BOW));
                else if ("GOLDEN_SWORD".equals(weapon) && dead.getType() == EntityType.ZOMBIFIED_PIGLIN)
                    gear.setItemInMainHand(new ItemStack(Material.GOLDEN_SWORD));
            }
            copyMemberScale(dead, next);
            next.customName(dead.customName()); next.setCustomNameVisible(false);
            next.getPersistentDataContainer().set(mobCountKey, PersistentDataType.INTEGER, remaining);
            next.getPersistentDataContainer().set(mobSourceKey, PersistentDataType.STRING, source);
            next.setPersistent(true); next.setRemoveWhenFarAway(false);
            ChunkPosition position = chunkPosition(next); rememberChunk(position.x(), position.z());
            queueMob(next, sky.islands().at(position.x(), position.z()).orElseThrow(), remaining);
        });
    }
    private void copyMemberScale(LivingEntity dead, LivingEntity next) {
        for (String name : SCALE_DOUBLES) copyScale(dead, next, name, PersistentDataType.DOUBLE);
        for (String name : SCALE_LONGS) copyScale(dead, next, name, PersistentDataType.LONG);
        for (String name : SCALE_INTS) copyScale(dead, next, name, PersistentDataType.INTEGER);
        copyScale(dead, next, "mob_tier", PersistentDataType.STRING);
        for (Attribute attribute : STACK_ATTRIBUTES) {
            NamespacedKey key = stackAttributeKeys.get(attribute.getKey().getKey());
            Double stored = dead.getPersistentDataContainer().has(key, PersistentDataType.DOUBLE)
                ? dead.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE) : null;
            // Upgrade older saved stacks without rerolling their ordinary combat attributes.
            if (stored == null && dead.getAttribute(attribute) != null && dead.getAttribute(attribute).getModifiers().isEmpty())
                stored = dead.getAttribute(attribute).getBaseValue();
            var value = next.getAttribute(attribute);
            if (stored != null && value != null && Double.isFinite(stored) && stored >= 0) {
                value.getModifiers().forEach(value::removeModifier); value.setBaseValue(stored);
            }
        }
        if (next.getAttribute(Attribute.MAX_HEALTH) != null) next.setHealth(next.getAttribute(Attribute.MAX_HEALTH).getValue());
        rememberMember(next);
    }
    private <P, C> void copyScale(LivingEntity dead, LivingEntity next, String name, PersistentDataType<P, C> type) {
        NamespacedKey key = new NamespacedKey(plugin, name);
        C value = dead.getPersistentDataContainer().has(key, type) ? dead.getPersistentDataContainer().get(key, type) : null;
        if (value == null) next.getPersistentDataContainer().remove(key);
        else next.getPersistentDataContainer().set(key, type, value);
    }
    private record StackCandidate(LivingEntity entity, UUID island, int added, int tick) { }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) { if (event.getEntity() instanceof LivingEntity living && mobCount(living) > 1) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStackTeleport(EntityTeleportEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living) || mobCount(living) < 2 || mobSource(living) == null
            || !sky.isIslandWorld(living.getWorld())) return;
        Location destination = event.getTo();
        if (destination == null || destination.getWorld() != living.getWorld() || sky.islands()
            .at(destination.getBlockX() >> 4, destination.getBlockZ() >> 4)
            .filter(home -> home.id().toString().equals(mobSource(living))).isEmpty()) event.setCancelled(true);
    }

    private void collect() {
        processMobStacks();
        List<IslandCollectors.Position> positions = collectors.positions(); if (positions.isEmpty()) return;
        for (int n = 0, limit = Math.min(32, positions.size()); n < limit; n++) {
            IslandCollectors.Position pos = positions.get(Math.floorMod(cursor++, positions.size()));
            if (!sky.world().isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) continue;
            Block block = sky.world().getBlockAt(pos.x(), pos.y(), pos.z());
            AccountId owner = collectors.owner(pos);
            if (owner == null) continue;
            if (!(block.getState() instanceof Hopper hopper) || !hopper.getPersistentDataContainer().has(collectorKey, PersistentDataType.BYTE)) {
                try { collectors.remove(pos); itemCursors.remove(pos); }
                catch (RuntimeException error) { plugin.getLogger().severe("Stale collector index could not be removed: " + error); }
                continue; // Never reconstruct or reimburse a missing machine from its index.
            }
            if (block.getBlockData() instanceof org.bukkit.block.data.type.Hopper data && !data.isEnabled()) continue;
            var island = island(block); if (island == null || !island.owner().equals(owner)) continue;
            var progress = sky.development().progress(owner); if (progress.hopperLimit() == 0) continue;
            int budget = progress.hopperBatch();
            Entity[] entities = block.getChunk().getEntities();
            int start = itemCursors.getOrDefault(pos, 0), examined = 0;
            while (examined < Math.min(64, entities.length) && budget > 0) {
                Entity entity = entities[Math.floorMod(start + examined++, entities.length)];
                if (!(entity instanceof Item item) || !item.isValid() || item.getPickupDelay() > 0 || item.getOwner() != null || item.getThrower() != null) continue;
                if (!accepts(hopper, item.getItemStack())) continue;
                InventoryPickupItemEvent pickup = new InventoryPickupItemEvent(hopper.getInventory(), item); Bukkit.getPluginManager().callEvent(pickup);
                if (pickup.isCancelled() || !item.isValid()) continue;
                ItemStack original = item.getItemStack(); int requested = Math.min(budget, original.getAmount());
                ItemStack offered = original.clone(); offered.setAmount(requested);
                int rejected = hopper.getInventory().addItem(offered).values().stream().mapToInt(ItemStack::getAmount).sum();
                int moved = requested - rejected; if (moved == 0) continue;
                budget -= moved;
                if (moved == original.getAmount()) item.remove();
                else { ItemStack rest = original.clone(); rest.setAmount(original.getAmount() - moved); item.setItemStack(rest); }
            }
            itemCursors.put(pos, entities.length == 0 ? 0 : Math.floorMod(start + examined, entities.length));
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCollectorPickup(InventoryPickupItemEvent event) {
        // Vanilla close-range pickup must obey the same filter and ownership rules as the chunk sweep.
        if (event.getInventory().getHolder() instanceof Hopper hopper && collector(hopper.getBlock())) {
            Item item = event.getItem();
            if (item.getOwner() != null || item.getThrower() != null || !accepts(hopper, item.getItemStack())) event.setCancelled(true);
        }
    }
    private boolean accepts(Hopper hopper, ItemStack item) {
        return CollectorFilter.accepts(hopper.getPersistentDataContainer().get(collectorFilterKey, PersistentDataType.STRING), item.getType());
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVanillaMerchant(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (event.getInventory().getType() == org.bukkit.event.inventory.InventoryType.MERCHANT) {
            event.setCancelled(true); Chat.notice(event.getPlayer(), "Trade with the merchants at " + Chat.command("spawn") + ". Their prices follow the server market.");
        }
    }
    @Override public void close() { task.cancel();
        for (StackCandidate candidate : List.copyOf(pendingMobs.values())) retainPendingMembers(candidate.entity());
        pendingMobs.clear(); retiringMembers.clear(); reconciliation.clear();
        reconciliationCursors.clear(); spawnBudgets.clear(); recipes.forEach(Bukkit::removeRecipe); }
}
