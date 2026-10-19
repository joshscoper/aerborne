package net.nerdsmp.paper.skyblock.machine;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.skyblock.IslandService;
import net.nerdsmp.paper.skyblock.IslandResourceStage;
import net.nerdsmp.paper.skyblock.SkyblockController;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Dropper;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.function.ToIntFunction;
import java.util.logging.Level;

/** Loaded-only local processing. Each pass visits at most sixteen durable machine records. */
public final class IslandMachines implements Listener, AutoCloseable {
    private final NerdSmpPlugin plugin;
    private final SkyblockController sky;
    private final ToIntFunction<AccountId> stage;
    private final MachineIndex index;
    private final NamespacedKey kindKey, schemaKey, instanceKey;
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();
    private final Map<UUID, Long> readyAt = new HashMap<>();
    private final Map<UUID, MachineView> views = new HashMap<>();
    private BukkitTask task;
    private int cursor;
    private boolean closed;

    public IslandMachines(NerdSmpPlugin plugin, SkyblockController sky, ToIntFunction<AccountId> stage) {
        this.plugin = Objects.requireNonNull(plugin); this.sky = Objects.requireNonNull(sky);
        this.stage = Objects.requireNonNull(stage);
        index = new MachineIndex(plugin.playerData(), sky.islands(), plugin.getConfig().getString("season.id", "season-1"));
        if (index.entries().stream().anyMatch(e -> !e.position().world().equals(sky.world().getUID())))
            throw new IllegalStateException("An island machine belongs to another world; refusing to adopt or replace it");
        kindKey = new NamespacedKey(plugin, "island_machine_kind");
        schemaKey = new NamespacedKey(plugin, "island_machine_schema");
        instanceKey = new NamespacedKey(plugin, "island_machine_instance");
    }
    public void start() {
        if (closed || task != null) throw new IllegalStateException("Island machines already started or closed");
        for (MachineKind kind : MachineKind.values()) {
            NamespacedKey key = new NamespacedKey(plugin, "island_machine_" + kind.id());
            if (Bukkit.getRecipe(key) != null) throw new IllegalStateException("Machine recipe ID already registered: " + key);
            ShapedRecipe recipe = new ShapedRecipe(key, item(kind));
            recipe.shape("III", "CDC", "III");
            recipe.setIngredient('I', Material.IRON_INGOT);
            recipe.setIngredient('D', Material.DROPPER);
            recipe.setIngredient('C', switch (kind) {
                case CRUSHER -> Material.COBBLESTONE;
                case COMPOSTER -> Material.WHEAT;
                case SAWMILL -> Material.OAK_LOG;
                case SIFTER -> Material.GRAVEL;
            });
            if (!Bukkit.addRecipe(recipe)) throw new IllegalStateException("Could not register machine recipe: " + key);
            recipeKeys.add(key);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::process, 20, 20);
    }
    public void discover(Player player) { if (!recipeKeys.isEmpty()) player.discoverRecipes(recipeKeys); }
    public int count(AccountId owner) { return index.count(owner); }
    public MachineIndex index() { return index; }
    public String diagnostics() {
        return "Processing machines: " + index.entries().size() + "; max 16 records per second; loaded chunks only; no tickets or offline production.";
    }
    public List<String> recipeDescriptions(MachineKind kind) {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("Craft: 6 iron ingots + 1 dropper + 2 " + switch (kind) {
            case CRUSHER -> "cobblestone"; case COMPOSTER -> "wheat";
            case SAWMILL -> "oak logs"; case SIFTER -> "gravel";
        });
        lines.add("Recipe grid: III / CDC / III (C = ingredient, D = dropper)");
        lines.add("Unlock: " + IslandResourceStage.at(kind.stage()).title());
        if (kind == MachineKind.SAWMILL) lines.add("4 matching logs or stems + 1 coal = 24 matching planks");
        else if (kind == MachineKind.COMPOSTER) {
            lines.add("8 wheat, carrots, potatoes, beetroots, melon slices or pumpkins");
            lines.add("+ 1 coal = 4 bone meal");
            lines.add("16 matching crop seeds + 1 coal = 4 bone meal");
        } else MachineRecipes.forKind(kind).stream().map(MachineRecipes.Recipe::description).forEach(lines::add);
        return List.copyOf(lines);
    }
    public ItemStack item(MachineKind kind) {
        ItemStack result = new ItemStack(Material.DROPPER);
        var meta = result.getItemMeta();
        meta.displayName(text(kind.title(), NamedTextColor.AQUA));
        meta.lore(List.of(text("Place on a co-op island. Unlock: " + IslandResourceStage.at(kind.stage()).title() + ".", NamedTextColor.GRAY),
            text("Top row: ingredients. Middle row: coal or charcoal.", NamedTextColor.GRAY),
            text("Bottom row: results. Redstone pauses production.", NamedTextColor.GRAY),
            text("Empty it, then sneak + break to pick it up.", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, kind.id());
        meta.getPersistentDataContainer().set(schemaKey, PersistentDataType.INTEGER, 1);
        result.setItemMeta(meta); return result;
    }
    public MachineKind kind(ItemStack item) {
        if (item == null || item.getType() != Material.DROPPER || !item.hasItemMeta()) return null;
        var data = item.getItemMeta().getPersistentDataContainer();
        if (!Integer.valueOf(1).equals(data.get(schemaKey, PersistentDataType.INTEGER))) return null;
        try { return MachineKind.parse(data.get(kindKey, PersistentDataType.STRING)); }
        catch (IllegalArgumentException ignored) { return null; }
    }
    private static Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }
    private AccountId account(Player player) { return plugin.sessions().account(player.getUniqueId()).orElse(null); }
    private static MachineIndex.Position position(Block block) {
        return new MachineIndex.Position(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
    private MachineIndex.Entry entry(Block block) { return sky.isIslandWorld(block.getWorld()) ? index.at(position(block)) : null; }
    private MachineIndex.Entry entry(Inventory inventory) {
        Location location = inventory.getLocation();
        return location == null || location.getWorld() == null ? null : entry(location.getBlock());
    }
    private boolean marked(Block block) {
        return block.getState() instanceof TileState tile && (tile.getPersistentDataContainer().has(kindKey)
            || tile.getPersistentDataContainer().has(instanceKey) || tile.getPersistentDataContainer().has(schemaKey));
    }
    private boolean protectedBlock(Block block) {
        return sky.isIslandWorld(block.getWorld()) && (entry(block) != null
            || block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4) && marked(block));
    }
    private boolean matches(MachineIndex.Entry entry, Block block) {
        if (!sky.isIslandWorld(block.getWorld()) || !entry.position().equals(position(block))
            || !(block.getState() instanceof Dropper dropper)) return false;
        var data = dropper.getPersistentDataContainer();
        return Integer.valueOf(1).equals(data.get(schemaKey, PersistentDataType.INTEGER))
            && entry.kind().id().equals(data.get(kindKey, PersistentDataType.STRING))
            && entry.id().toString().equals(data.get(instanceKey, PersistentDataType.STRING));
    }
    private boolean authorized(Player player, MachineIndex.Entry entry) {
        if (entry == null || !player.isOnline() || !sky.isIslandWorld(player.getWorld())) return false;
        AccountId actor = account(player);
        if (actor == null) return false;
        var p = entry.position();
        if (!player.getWorld().getUID().equals(p.world())
            || player.getLocation().distanceSquared(new Location(player.getWorld(), p.x() + .5, p.y() + .5, p.z() + .5)) > 64) return false;
        IslandService.Island island = sky.islands().owned(entry.owner()).orElse(null);
        return island != null && island.member(actor)
            && sky.islands().at(entry.position().x() >> 4, entry.position().z() >> 4)
                .filter(i -> i.owner().equals(entry.owner())).isPresent();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (entry(event.getBlock()) != null) { deny(event, "This block has a saved machine record. Ask staff to check it."); return; }
        MachineKind kind = kind(event.getItemInHand());
        // A marker-bearing malformed item must never silently become an ordinary dropper.
        boolean marker = event.getItemInHand().hasItemMeta()
            && (event.getItemInHand().getItemMeta().getPersistentDataContainer().has(kindKey)
                || event.getItemInHand().getItemMeta().getPersistentDataContainer().has(schemaKey)
                || event.getItemInHand().getItemMeta().getPersistentDataContainer().has(instanceKey));
        if (kind == null && !marker) return;
        if (kind == null || event.getBlock().getType() != Material.DROPPER) { event.setCancelled(true); return; }
        Block block = event.getBlock();
        IslandService.Island island = sky.isIslandWorld(block.getWorld())
            ? sky.islands().at(block.getX() >> 4, block.getZ() >> 4).orElse(null) : null;
        AccountId actor = account(event.getPlayer());
        if (actor == null || island == null || !island.member(actor)) { deny(event, "Place machines on an island you belong to."); return; }
        if (stage.applyAsInt(island.owner()) < kind.stage()) { deny(event, "Unlock " + IslandResourceStage.at(kind.stage()).title() + " before placing a " + kind.title() + "."); return; }
        MachineIndex.Entry saved = null;
        try {
            saved = index.add(actor, island.owner(), position(block), kind);
            Dropper tile = (Dropper) block.getState();
            var data = tile.getPersistentDataContainer();
            data.set(kindKey, PersistentDataType.STRING, kind.id());
            data.set(schemaKey, PersistentDataType.INTEGER, 1);
            data.set(instanceKey, PersistentDataType.STRING, saved.id().toString());
            tile.customName(text(kind.title() + " | Input / Coal / Results", NamedTextColor.DARK_AQUA));
            if (!tile.update(false, false)) throw new IllegalStateException("Could not mark the machine block");
            MachineIndex.Entry record = saved;
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Respect cancellation by later listeners; never remove the user's replacement block.
                if (event.isCancelled() && record.equals(index.at(record.position()))) {
                    try { index.remove(record); }
                    catch (RuntimeException error) { plugin.getLogger().log(Level.SEVERE, "Cancelled machine placement needs index review: " + record.id(), error); }
                }
            });
        } catch (RuntimeException error) {
            event.setCancelled(true);
            if (saved != null) try { index.remove(saved); }
            catch (RuntimeException rollback) { error.addSuppressed(rollback); }
            plugin.getLogger().log(Level.SEVERE, "Machine placement retained its item after a failed save", error);
            Chat.error(event.getPlayer(), "The machine could not be saved. Your item was kept.");
        }
    }
    private static void deny(BlockPlaceEvent event, String message) {
        event.setCancelled(true); Chat.error(event.getPlayer(), Chat.escape(message));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        if (!protectedBlock(block)) return;
        event.setCancelled(true);
        MachineIndex.Entry entry = entry(block);
        if (entry == null || !matches(entry, block)) { Chat.error(event.getPlayer(), "This machine needs a staff check. Its contents were left untouched."); return; }
        if (!authorized(event.getPlayer(), entry)) { Chat.error(event.getPlayer(), "Only this island's co-op can use its machines."); return; }
        Chat.info(event.getPlayer(), "Top row: ingredients | middle row: coal or charcoal | bottom row: collect results");
        event.getPlayer().openInventory(((Dropper) block.getState()).getInventory());
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        Location location = inventory.getLocation();
        if (location == null || !protectedBlock(location.getBlock())) return;
        MachineIndex.Entry entry = entry(inventory);
        if (!(event.getPlayer() instanceof Player player) || !authorized(player, entry) || !matches(entry, location.getBlock())) {
            event.setCancelled(true); return;
        }
        views.put(player.getUniqueId(), new MachineView(entry, account(player), inventory));
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        MachineIndex.Entry entry = entry(top);
        MachineView view = views.get(event.getWhoClicked().getUniqueId());
        Location location = top.getLocation();
        if ((view == null || view.inventory() != top) && entry == null && (location == null || !protectedBlock(location.getBlock()))) return;
        if (event.isCancelled()) return;
        if (!(event.getWhoClicked() instanceof Player player) || !currentView(player, top, view)) {
            event.setCancelled(true); return;
        }
        int raw = event.getRawSlot();
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR || event.getClick() == ClickType.NUMBER_KEY
            || event.getClick() == ClickType.SWAP_OFFHAND || event.getClick() == ClickType.MIDDLE
            || event.getClick() == ClickType.CREATIVE || event.getAction() == InventoryAction.UNKNOWN) {
            event.setCancelled(true); return;
        }
        if (raw < 0) return;
        if (raw >= 9) {
            if (event.isShiftClick()) {
                event.setCancelled(true); Chat.info(player, "Drag ingredients into the top row and fuel into the middle row.");
            }
            return;
        }
        if (event.isShiftClick()) return; // Top-to-player extraction cannot put items into output slots.
        if (!event.isLeftClick() && !event.isRightClick()) { event.setCancelled(true); return; }
        ItemStack held = event.getCursor();
        if (held == null || held.getType().isAir()) return;
        if (!allows(entry.kind(), raw, held)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        MachineIndex.Entry entry = entry(top);
        MachineView view = views.get(event.getWhoClicked().getUniqueId());
        Location location = top.getLocation();
        if ((view == null || view.inventory() != top) && entry == null && (location == null || !protectedBlock(location.getBlock()))) return;
        if (event.isCancelled()) return;
        if (!(event.getWhoClicked() instanceof Player player) || !currentView(player, top, view)) {
            event.setCancelled(true); return;
        }
        for (var moved : event.getNewItems().entrySet()) if (moved.getKey() < 9 && !allows(entry.kind(), moved.getKey(), moved.getValue())) {
            event.setCancelled(true); return;
        }
    }
    private static boolean allows(MachineKind kind, int slot, ItemStack item) {
        if (slot < 0 || slot >= 6 || item.hasItemMeta()) return false;
        return slot < 3 ? MachineRecipes.acceptsInput(kind, item.getType()) : MachineRecipes.fuel(item.getType());
    }
    private boolean currentView(Player player, Inventory top, MachineView view) {
        if (view == null || view.inventory() != top || player.getOpenInventory().getTopInventory() != top
            || !view.account().equals(account(player)) || !view.entry().equals(index.at(view.entry().position()))
            || !authorized(player, view.entry())) return false;
        var position = view.entry().position();
        World world = Bukkit.getWorld(position.world());
        if (world == null || !world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) return false;
        Block block = world.getBlockAt(position.x(), position.y(), position.z());
        return matches(view.entry(), block) && ((Dropper) block.getState()).getInventory().equals(top);
    }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        MachineView view = views.get(event.getPlayer().getUniqueId());
        if (view != null && view.inventory() == event.getInventory()) views.remove(event.getPlayer().getUniqueId());
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { views.remove(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        // Vanilla hopper iteration cannot distinguish this station's three rows. Fail closed.
        if (protectedInventory(event.getSource()) || protectedInventory(event.getDestination())) event.setCancelled(true);
    }
    private boolean protectedInventory(Inventory inventory) {
        Location location = inventory.getLocation(); return location != null && protectedBlock(location.getBlock());
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!protectedBlock(block)) return;
        event.setCancelled(true); event.setDropItems(false); event.setExpToDrop(0);
        Player player = event.getPlayer(); MachineIndex.Entry entry = entry(block);
        if (entry == null || !matches(entry, block)) { Chat.error(player, "This machine needs a staff check. Its contents were left untouched."); return; }
        if (!authorized(player, entry)) { Chat.error(player, "Only this island's co-op can pick up its machines."); return; }
        if (!player.isSneaking()) { Chat.notice(player, "Empty the machine, then sneak + break to pick it up."); return; }
        Inventory inventory = ((Dropper) block.getState()).getInventory();
        if (!inventory.isEmpty()) { Chat.error(player, "Take every item out before picking up this machine."); return; }
        // Stop old container views before the backing tile disappears. Otherwise another
        // teammate could put their items into a detached inventory after this pickup.
        for (var viewer : new ArrayList<>(inventory.getViewers())) viewer.closeInventory();
        // Closing returns cursor items and invokes other listeners. Recheck after it,
        // rather than overwriting a newly filled player slot or removing refilled storage.
        if (!matches(entry, block) || !((Dropper) block.getState()).getInventory().isEmpty()) {
            Chat.error(player, "The machine changed while closing. Take every item out and try again."); return;
        }
        int free = player.getInventory().firstEmpty();
        if (free < 0) { Chat.error(player, "Make room in your inventory first."); return; }
        // Commit before removing the block or granting an item. Stale orphan tiles cannot mint replacements.
        try { index.remove(entry); }
        catch (RuntimeException error) {
            plugin.getLogger().log(Level.SEVERE, "Machine recovery save failed; block retained", error);
            Chat.error(player, "The machine could not be saved. It was left in place."); return;
        }
        block.setType(Material.AIR, false);
        if (block.getType() != Material.AIR) {
            plugin.getLogger().severe("Machine recovery could not remove the block; no item granted: " + entry.id());
            Chat.error(player, "The machine could not be picked up. Ask staff to check it."); return;
        }
        readyAt.remove(entry.id());
        player.getInventory().setItem(free, item(entry.kind()));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) { if (protectedBlock(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent event) { if (event.getBlocks().stream().anyMatch(this::protectedBlock)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent event) { if (event.getBlocks().stream().anyMatch(this::protectedBlock)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosion(BlockExplodeEvent event) { event.blockList().removeIf(this::protectedBlock); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) { event.blockList().removeIf(this::protectedBlock); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) { if (protectedBlock(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepare(PrepareItemCraftEvent event) {
        if (!(event.getRecipe() instanceof Keyed keyed) || !recipeKeys.contains(keyed.getKey())) return;
        for (ItemStack item : event.getInventory().getMatrix()) if (item != null && !item.getType().isAir() && item.hasItemMeta()) {
            event.getInventory().setResult(null); return;
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getRecipe() instanceof Keyed keyed) || !recipeKeys.contains(keyed.getKey())) return;
        for (ItemStack item : event.getInventory().getMatrix()) if (item != null && !item.getType().isAir() && item.hasItemMeta()) {
            event.setCancelled(true); return;
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (event.getRecipe() instanceof Keyed keyed && recipeKeys.contains(keyed.getKey())) event.setCancelled(true);
    }
    private void process() {
        if (closed) return;
        List<MachineIndex.Entry> entries = index.entries();
        if (entries.isEmpty()) { cursor = 0; return; }
        long now = System.nanoTime();
        for (int visited = 0, max = Math.min(16, entries.size()); visited < max; visited++) {
            cursor %= entries.size(); MachineIndex.Entry entry = entries.get(cursor++);
            try {
                World world = Bukkit.getWorld(entry.position().world());
                if (world == null || !sky.isIslandWorld(world) || !world.isChunkLoaded(entry.position().x() >> 4, entry.position().z() >> 4)
                    || now < readyAt.getOrDefault(entry.id(), 0L)) continue;
                Block block = world.getBlockAt(entry.position().x(), entry.position().y(), entry.position().z());
                if (!matches(entry, block) || block.isBlockPowered() || block.isBlockIndirectlyPowered()) continue;
                int unlocked = stage.applyAsInt(entry.owner());
                if (unlocked < entry.kind().stage()) continue;
                Dropper dropper = (Dropper) block.getState();
                Inventory inventory = dropper.getInventory(); ItemStack[] current = inventory.getContents();
                List<MachineRecipes.Stack> stacks = new ArrayList<>(9);
                for (ItemStack item : current) stacks.add(item == null || item.getType().isAir() ? null
                    : new MachineRecipes.Stack(item.getType(), item.getAmount(), !item.hasItemMeta()));
                var planned = MachineRecipes.plan(entry.kind(), unlocked, stacks);
                if (planned.isEmpty()) continue;
                ItemStack[] next = new ItemStack[9];
                for (int slot = 0; slot < 9; slot++) {
                    var after = planned.get().slots().get(slot);
                    if (after == null) continue;
                    ItemStack before = current[slot];
                    next[slot] = before != null && before.getType() == after.material() ? before.clone() : new ItemStack(after.material());
                    next[slot].setAmount(after.amount());
                }
                inventory.setContents(next); // One local container mutation; no economy/XP or cross-file item grants.
                readyAt.put(entry.id(), now + sky.development().progress(entry.owner()).machineCycleSeconds(entry.kind().seconds()) * 1_000_000_000L);
            } catch (RuntimeException error) {
                readyAt.put(entry.id(), now + 60_000_000_000L);
                plugin.getLogger().log(Level.SEVERE, "Machine paused without replacing its block: " + entry.id(), error);
            }
        }
    }
    @Override public void close() {
        if (closed) return; closed = true;
        if (task != null) task.cancel();
        for (UUID playerId : List.copyOf(views.keySet())) {
            Player player = Bukkit.getPlayer(playerId); MachineView view = views.get(playerId);
            if (player != null && view != null && player.getOpenInventory().getTopInventory() == view.inventory()) player.closeInventory();
        }
        recipeKeys.forEach(Bukkit::removeRecipe); recipeKeys.clear(); readyAt.clear(); views.clear();
    }
    private record MachineView(MachineIndex.Entry entry, AccountId account, Inventory inventory) { }
}
