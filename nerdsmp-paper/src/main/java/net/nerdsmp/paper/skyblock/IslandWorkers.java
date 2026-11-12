package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.item.FoodItems;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The Workers' Lodge, after Hypixel SkyBlock's minions and the automation islands of Hytale's skyblock servers: hired
 * hands that gather one resource for the co-op, around the clock and while everyone is away, into a store that fills
 * up and waits to be collected. Workers are kept in the lodge, not as entities in the world, so a hundred islands of
 * them cost the server nothing between collections. Hiring and upgrading take the worker's own product and Marks;
 * every new worker type and tier ever reached adds to the lodge's room, so variety pays.
 */
public final class IslandWorkers implements Listener {
    /** Each kind of worker: what it gathers, how many an hour at tier I, and the resource stage it needs. */
    public enum Type {
        QUARRY("Quarry hand", Material.COBBLESTONE, Material.STONE_PICKAXE, 0, 90),
        COAL("Coal digger", Material.COAL, Material.COAL_ORE, 0, 30),
        COPPER("Copper digger", Material.COPPER_INGOT, Material.COPPER_ORE, 0, 24),
        IRON("Iron digger", Material.IRON_INGOT, Material.IRON_ORE, 0, 12),
        WHEAT("Wheat farmer", Material.WHEAT, Material.WHEAT, 0, 60),
        CARROT("Carrot farmer", Material.CARROT, Material.CARROT, 0, 60),
        POTATO("Potato farmer", Material.POTATO, Material.POTATO, 0, 60),
        CANE("Cane cutter", Material.SUGAR_CANE, Material.SUGAR_CANE, 0, 40),
        MELON("Melon picker", Material.MELON_SLICE, Material.MELON, 0, 60),
        PUMPKIN("Pumpkin picker", Material.PUMPKIN, Material.PUMPKIN, 0, 16),
        WOOD("Woodcutter", Material.OAK_LOG, Material.IRON_AXE, 0, 30),
        FISHER("Pond fisher", Material.COD, Material.FISHING_ROD, 0, 10),
        GOLD("Gold panner", Material.GOLD_INGOT, Material.GOLD_ORE, 1, 7),
        LAPIS("Lapis cutter", Material.LAPIS_LAZULI, Material.LAPIS_ORE, 1, 14),
        REDSTONE("Redstone delver", Material.REDSTONE, Material.REDSTONE_ORE, 2, 20),
        DIAMOND("Diamond seeker", Material.DIAMOND, Material.DIAMOND_ORE, 3, 1.5),
        QUARTZ("Quartz sifter", Material.QUARTZ, Material.NETHER_QUARTZ_ORE, 4, 8),
        EMERALD("Emerald seeker", Material.EMERALD, Material.EMERALD_ORE, 5, 1);

        private final String title;
        private final Material product, icon;
        private final int stage;
        private final double perHour;
        Type(String title, Material product, Material icon, int stage, double perHour) {
            this.title = title; this.product = product; this.icon = icon; this.stage = stage; this.perHour = perHour;
        }
        public String title() { return title; }
        public Material product() { return product; }
        public Material icon() { return icon; }
        public int stage() { return stage; }
        public String id() { return name().toLowerCase(Locale.ROOT); }

        /** Items an hour at a tier. */
        public double rate(int tier) { return perHour * TIER_SPEED[Math.clamp(tier, 1, MAX_TIER) - 1]; }
        /** How much the worker holds before it stops: eight hours of work. */
        public int capacity(int tier) { return (int) Math.min(2304, Math.max(8, Math.round(rate(tier) * STORE_HOURS))); }
        /** Product it takes to hire (tier 1) or to reach {@code tier}. */
        public int cost(int tier) { return (int) Math.clamp(Math.round(perHour * 0.5 * tier * tier), 2, 512); }
        public long marks(int tier) { return 250L * tier * tier * (1 + stage); }
    }

    public static final int MAX_TIER = 5, STORE_HOURS = 8, BASE_SLOTS = 3, MAX_SLOTS = 15, UNLOCKS_PER_SLOT = 4;
    static final double[] TIER_SPEED = {1, 1.5, 2.1, 2.8, 3.6};

    /** One hired worker: its kind, tier, what it has gathered, and when it last counted. */
    public record Worker(Type type, int tier, int stored, long since) { }
    record State(List<Worker> workers, Set<String> unlocks) { }

    /** Room in the lodge: three, and one more for every four different worker tiers ever reached. */
    public static int slots(int unlocks) { return Math.min(MAX_SLOTS, BASE_SLOTS + unlocks / UNLOCKS_PER_SLOT); }

    /** What the worker has gathered by {@code now}; the clock moves only by whole items, so nothing is lost. */
    static Worker work(Worker w, long now) {
        if (now <= w.since()) return w;
        int cap = w.type().capacity(w.tier());
        if (w.stored() >= cap) return new Worker(w.type(), w.tier(), cap, now);
        double perMilli = w.type().rate(w.tier()) / 3_600_000D;
        long elapsed = now - w.since();
        int made = (int) Math.floor(elapsed * perMilli);
        if (made <= 0) return w;
        int stored = Math.min(cap, w.stored() + made);
        long used = stored >= cap ? elapsed : (long) Math.ceil(made / perMilli);
        return new Worker(w.type(), w.tier(), stored, Math.min(now, w.since() + used));
    }

    static String encode(Worker w) { return w.type().name() + "|" + w.tier() + "|" + w.stored() + "|" + w.since(); }
    static Worker decode(String raw) {
        String[] p = raw.split("\\|");
        return new Worker(Type.valueOf(p[0]), Math.clamp(Integer.parseInt(p[1]), 1, MAX_TIER), Math.max(0, Integer.parseInt(p[2])), Long.parseLong(p[3]));
    }

    // ------------------------------------------------------------------ storage

    private final NerdSmpPlugin plugin;
    private final SkyblockController islands;
    private final String path;

    IslandWorkers(NerdSmpPlugin plugin, SkyblockController islands, String season) {
        this.plugin = plugin; this.islands = islands; this.path = "seasons." + season + ".island-workers";
    }

    static State read(YamlConfiguration yaml, String path) {
        List<Worker> workers = new ArrayList<>();
        for (String raw : yaml.getStringList(path + ".workers")) {
            try { workers.add(decode(raw)); } catch (RuntimeException skipped) { /* an unreadable worker is dropped, not the lodge */ }
        }
        return new State(workers, new LinkedHashSet<>(yaml.getStringList(path + ".unlocks")));
    }

    private State read(AccountId owner) { return plugin.playerData().read(owner, yaml -> read(yaml, path)); }

    private void write(AccountId owner, State state) {
        plugin.playerData().update(owner, yaml -> {
            yaml.set(path + ".workers", state.workers().stream().map(IslandWorkers::encode).toList());
            yaml.set(path + ".unlocks", List.copyOf(state.unlocks()));
        });
    }

    /** Different worker tiers this co-op has ever reached, for lodge room and showcases. */
    public int unlocks(AccountId owner) { return read(owner).unlocks().size(); }

    private State caughtUp(AccountId owner) {
        State state = read(owner);
        long now = System.currentTimeMillis();
        List<Worker> worked = state.workers().stream().map(w -> work(w, now)).toList();
        return new State(worked, state.unlocks());
    }

    // ------------------------------------------------------------------ actions

    private void member(Player player, AccountId owner) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElseThrow(() -> new IllegalArgumentException("Your account is still loading."));
        if (islands.islands().owned(owner).filter(i -> i.member(account)).isEmpty()) throw new IllegalArgumentException("Only this co-op can run its lodge.");
    }

    private static int has(Player player, Material material) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents())
            if (stack != null && stack.getType() == material && FoodItems.ordinary(stack)) count += stack.getAmount();
        return count;
    }

    private static void take(Player player, Material material, int amount) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material || !FoodItems.ordinary(stack)) continue;
            int used = Math.min(amount, stack.getAmount());
            stack.setAmount(stack.getAmount() - used);
            amount -= used;
            contents[i] = stack.getAmount() <= 0 ? null : stack;
        }
        player.getInventory().setStorageContents(contents);
    }

    /** Pays a hire or upgrade: the product from the inventory and the Marks from the account, or nothing at all. */
    private void pay(Player player, Type type, int tier, Runnable then) {
        int items = type.cost(tier);
        long marks = type.marks(tier);
        if (has(player, type.product()) < items) throw new IllegalArgumentException("You need " + items + " " + IslandGuests.name(type.product()) + ".");
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElseThrow();
        plugin.economy().debit(account, marks, new EconomyService.TransactionReason("island-worker", type.title() + " tier " + tier),
                "island-worker:" + account.value() + ":" + UUID.randomUUID())
            .whenComplete((paid, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (error != null || paid == null || paid.status() != EconomyService.TransactionResult.Status.APPLIED) {
                    Chat.error(player, "You need " + Chat.marks(marks) + " for that.");
                    return;
                }
                if (has(player, type.product()) < items) {
                    // The items went elsewhere while the payment cleared: give the Marks back.
                    plugin.economy().credit(account, marks, new EconomyService.TransactionReason("island-worker", "Refund"), "island-worker-refund:" + UUID.randomUUID());
                    Chat.error(player, "You no longer have the " + IslandGuests.name(type.product()) + ".");
                    return;
                }
                take(player, type.product(), items);
                then.run();
            }));
    }

    void hire(Player player, AccountId owner, Type type) {
        member(player, owner);
        if (islands.resources().effectiveStage(owner) < type.stage())
            throw new IllegalArgumentException("Unlock the " + IslandResourceStage.at(type.stage()).title() + " stage first.");
        State state = caughtUp(owner);
        if (state.workers().size() >= slots(state.unlocks().size())) throw new IllegalArgumentException("The lodge is full. Upgrade or let a worker go.");
        pay(player, type, 1, () -> {
            State now = caughtUp(owner);
            if (now.workers().size() >= slots(now.unlocks().size())) { Chat.error(player, "The lodge filled up first."); return; }
            List<Worker> workers = new ArrayList<>(now.workers());
            workers.add(new Worker(type, 1, 0, System.currentTimeMillis()));
            Set<String> unlocks = new LinkedHashSet<>(now.unlocks());
            boolean fresh = unlocks.add(type.name() + ":1");
            write(owner, new State(workers, unlocks));
            Chat.success(player, "Hired a <white>" + Chat.escape(type.title()) + "</white>." + (fresh ? " <gray>New worker type: the lodge grows as you collect them.</gray>" : ""));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 0.7F, 1F);
            open(player, owner);
        });
    }

    void upgrade(Player player, AccountId owner, int index) {
        member(player, owner);
        State state = caughtUp(owner);
        if (index < 0 || index >= state.workers().size()) return;
        Worker w = state.workers().get(index);
        if (w.tier() >= MAX_TIER) throw new IllegalArgumentException("That worker is at the top tier.");
        pay(player, w.type(), w.tier() + 1, () -> {
            State now = caughtUp(owner);
            if (index >= now.workers().size() || now.workers().get(index).type() != w.type() || now.workers().get(index).tier() != w.tier()) { open(player, owner); return; }
            List<Worker> workers = new ArrayList<>(now.workers());
            Worker current = workers.get(index);
            workers.set(index, new Worker(current.type(), current.tier() + 1, current.stored(), current.since()));
            Set<String> unlocks = new LinkedHashSet<>(now.unlocks());
            unlocks.add(current.type().name() + ":" + (current.tier() + 1));
            write(owner, new State(workers, unlocks));
            Chat.success(player, "<white>" + Chat.escape(current.type().title()) + "</white> is now tier " + roman(current.tier() + 1) + ".");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5F, 1.4F);
            open(player, owner);
        });
    }

    /** Collects from one worker, or every worker when {@code index} is -1, as much as fits in the inventory. */
    void collect(Player player, AccountId owner, int index) {
        member(player, owner);
        State state = caughtUp(owner);
        List<Worker> workers = new ArrayList<>(state.workers());
        int total = 0;
        for (int i = 0; i < workers.size(); i++) {
            if (index >= 0 && i != index) continue;
            Worker w = workers.get(i);
            if (w.stored() <= 0) continue;
            int left = give(player, w.type().product(), w.stored());
            total += w.stored() - left;
            workers.set(i, new Worker(w.type(), w.tier(), left, w.since()));
        }
        write(owner, new State(workers, state.unlocks()));
        if (total > 0) {
            Chat.success(player, "Collected <white>" + total + "</white> items from the lodge.");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6F, 1F);
        } else Chat.info(player, "Nothing to collect yet, or no room in your inventory.");
        open(player, owner);
    }

    /** Puts items in the inventory; returns how many didn't fit. */
    private static int give(Player player, Material material, int amount) {
        int left = amount;
        while (left > 0) {
            int stack = Math.min(left, material.getMaxStackSize());
            var overflow = player.getInventory().addItem(new ItemStack(material, stack));
            int notPlaced = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
            left -= stack - notPlaced;
            if (notPlaced > 0) break;
        }
        return left;
    }

    void dismiss(Player player, AccountId owner, int index) {
        member(player, owner);
        State state = caughtUp(owner);
        if (index < 0 || index >= state.workers().size()) return;
        if (state.workers().get(index).stored() > 0) throw new IllegalArgumentException("Collect what they've gathered first.");
        List<Worker> workers = new ArrayList<>(state.workers());
        Worker gone = workers.remove(index);
        write(owner, new State(workers, state.unlocks()));
        Chat.info(player, "The " + Chat.escape(gone.type().title()) + " packs up. Their tiers still count toward the lodge's room.");
        open(player, owner);
    }

    // ------------------------------------------------------------------ the lodge

    private static final int[] WORKER_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28};

    void open(Player player, AccountId owner) {
        member(player, owner);
        State state = caughtUp(owner);
        write(owner, state);
        int room = slots(state.unlocks().size());
        Holder holder = new Holder(player.getUniqueId(), owner, Mode.LODGE);
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<gold>Workers' Lodge · " + state.workers().size() + "/" + room + "</gold>"));
        MenuKit.frame(holder.inventory);
        int waiting = state.workers().stream().mapToInt(Worker::stored).sum();
        int toNext = UNLOCKS_PER_SLOT - state.unlocks().size() % UNLOCKS_PER_SLOT;
        holder.inventory.setItem(4, MenuKit.item(Material.BARREL, "<gold>Workers' Lodge</gold>", List.of(
            "<gray>Hired hands gather for the co-op, even while you're away.</gray>",
            "<gray>Each holds " + STORE_HOURS + " hours of work, then waits for you.</gray>",
            "<gray>Room:</gray> <white>" + room + "/" + MAX_SLOTS + "</white> <dark_gray>(" + state.unlocks().size() + " worker tiers reached"
                + (room < MAX_SLOTS ? "; " + toNext + " more for another spot" : "") + ")</dark_gray>",
            "<gray>Waiting to collect:</gray> <white>" + waiting + "</white>")));
        for (int i = 0; i < WORKER_SLOTS.length; i++) {
            if (i >= state.workers().size()) {
                if (i > room) continue; // one locked spot shows what's next; more would only clutter the lodge
                holder.inventory.setItem(WORKER_SLOTS[i], i < room
                    ? MenuKit.item(Material.LIME_STAINED_GLASS_PANE, "<green>Free spot</green>", List.of("<yellow>Click to hire a worker.</yellow>"))
                    : MenuKit.item(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>Locked spot</dark_gray>", List.of("<gray>Reach new worker tiers for more room.</gray>")));
                continue;
            }
            Worker w = state.workers().get(i);
            int cap = w.type().capacity(w.tier());
            List<String> lore = new ArrayList<>(List.of(
                "<gray>Gathered:</gray> <white>" + w.stored() + "/" + cap + "</white> " + Chat.escape(IslandGuests.name(w.type().product())),
                "<gray>Rate:</gray> <white>" + format(w.type().rate(w.tier())) + "/hour</white> <dark_gray>• tier " + roman(w.tier()) + "</dark_gray>",
                w.stored() >= cap ? "<red>Full: collect to keep them working.</red>" : "<gray>Full in</gray> <white>" + hours((cap - w.stored()) / w.type().rate(w.tier())) + "</white>"));
            if (w.tier() < MAX_TIER) lore.add("<gray>Upgrade:</gray> <white>" + w.type().cost(w.tier() + 1) + " " + Chat.escape(IslandGuests.name(w.type().product()))
                + "</white> <gray>+</gray> <gold>" + Chat.marks(w.type().marks(w.tier() + 1)) + "</gold>");
            lore.add("<yellow>Click to collect.</yellow>");
            if (w.tier() < MAX_TIER) lore.add("<yellow>Right-click to upgrade.</yellow>");
            lore.add("<dark_gray>Shift-right-click to let them go.</dark_gray>");
            holder.inventory.setItem(WORKER_SLOTS[i], MenuKit.item(w.type().icon(), "<white>" + Chat.escape(w.type().title()) + " " + roman(w.tier()) + "</white>", lore));
            if (w.stored() >= cap) holder.inventory.getItem(WORKER_SLOTS[i]).editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            holder.workers[i] = true;
        }
        holder.inventory.setItem(40, MenuKit.item(Material.HOPPER, "<gold>Collect everything</gold>", List.of("<gray>As much as fits in your inventory.</gray>", "<yellow>Click to collect.</yellow>")));
        holder.inventory.setItem(45, MenuKit.item(Material.ARROW, "<yellow>Back to your island</yellow>", List.of()));
        holder.inventory.setItem(49, MenuKit.close());
        player.openInventory(holder.inventory);
    }

    void openHiring(Player player, AccountId owner) {
        member(player, owner);
        int stage = islands.resources().effectiveStage(owner);
        Holder holder = new Holder(player.getUniqueId(), owner, Mode.HIRE);
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<gold>Hire a Worker</gold>"));
        MenuKit.frame(holder.inventory);
        holder.inventory.setItem(4, MenuKit.item(Material.EMERALD, "<gold>Hire a worker</gold>", List.of("<gray>Pay in the worker's own product and Marks.</gray>",
            "<gray>Higher resource stages open rarer workers.</gray>")));
        Type[] types = Type.values();
        for (int i = 0; i < types.length && i < WORKER_SLOTS.length + 3; i++) {
            Type type = types[i];
            boolean open = stage >= type.stage();
            List<String> lore = new ArrayList<>(List.of(
                "<gray>Gathers</gray> <white>" + format(type.rate(1)) + " " + Chat.escape(IslandGuests.name(type.product())) + "/hour</white> <gray>at tier I,</gray>",
                "<gray>up to</gray> <white>" + format(type.rate(MAX_TIER)) + "/hour</white> <gray>at tier V.</gray>",
                "<gray>Hire:</gray> <white>" + type.cost(1) + " " + Chat.escape(IslandGuests.name(type.product())) + "</white> <gray>+</gray> <gold>" + Chat.marks(type.marks(1)) + "</gold>"));
            lore.add(open ? "<yellow>Click to hire.</yellow>" : "<red>Needs the " + IslandResourceStage.at(type.stage()).title() + " stage.</red>");
            holder.inventory.setItem(HIRE_SLOTS[i], MenuKit.item(open ? type.icon() : Material.GRAY_DYE, (open ? "<white>" : "<dark_gray>") + Chat.escape(type.title()) + (open ? "</white>" : "</dark_gray>"), lore));
        }
        holder.inventory.setItem(45, MenuKit.item(Material.ARROW, "<yellow>Back to the lodge</yellow>", List.of()));
        holder.inventory.setItem(49, MenuKit.close());
        player.openInventory(holder.inventory);
    }

    private static final int[] HIRE_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31};

    static String roman(int tier) { return switch (tier) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; default -> "V"; }; }
    private static String format(double rate) { return rate == Math.rint(rate) ? Long.toString((long) rate) : String.format(Locale.ROOT, "%.1f", rate); }
    private static String hours(double hours) { return hours < 1 ? Math.max(1, Math.round(hours * 60)) + "m" : String.format(Locale.ROOT, "%.1fh", hours); }

    enum Mode { LODGE, HIRE }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        boolean right = event.isRightClick(), shift = event.isShiftClick();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != holder.inventory) return;
            try {
                if (holder.mode == Mode.HIRE) {
                    for (int i = 0; i < HIRE_SLOTS.length; i++) if (HIRE_SLOTS[i] == slot && i < Type.values().length) { hire(player, holder.owner, Type.values()[i]); return; }
                    if (slot == 45) open(player, holder.owner);
                    else if (slot == 49) player.closeInventory();
                    return;
                }
                for (int i = 0; i < WORKER_SLOTS.length; i++) if (WORKER_SLOTS[i] == slot) {
                    if (!holder.workers[i]) { if (holder.inventory.getItem(slot) != null && holder.inventory.getItem(slot).getType() == Material.LIME_STAINED_GLASS_PANE) openHiring(player, holder.owner); return; }
                    if (shift && right) dismiss(player, holder.owner, i);
                    else if (right) upgrade(player, holder.owner, i);
                    else collect(player, holder.owner, i);
                    return;
                }
                if (slot == 40) collect(player, holder.owner, -1);
                else if (slot == 45) player.performCommand("island");
                else if (slot == 49) player.closeInventory();
            } catch (IllegalArgumentException refused) { Chat.error(player, Chat.escape(refused.getMessage())); }
            catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "Workers' Lodge action failed", failure);
                Chat.error(player, "That didn't work. Try again shortly.");
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true); }

    private static final class Holder implements InventoryHolder {
        final UUID viewer; final AccountId owner; final Mode mode; final boolean[] workers = new boolean[WORKER_SLOTS.length]; Inventory inventory;
        Holder(UUID viewer, AccountId owner, Mode mode) { this.viewer = viewer; this.owner = owner; this.mode = mode; }
        @Override public Inventory getInventory() { return inventory; }
    }

}
