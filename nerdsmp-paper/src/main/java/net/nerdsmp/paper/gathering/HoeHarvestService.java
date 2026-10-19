package net.nerdsmp.paper.gathering;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.listener.SkillListener;
import net.nerdsmp.paper.session.SessionRegistry;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** One synchronous crop/seed transaction. No player document, island or terrain migration. */
public final class HoeHarvestService implements Listener {
    private final JavaPlugin plugin;
    private final SessionRegistry sessions;
    private final SkillListener skills;
    private final Predicate<Player> currentPlayer;
    private final BiConsumer<Player, ItemStack> wearTool;
    private final Set<String> busy = new HashSet<>();
    private final Set<PlayerHarvestBlockEvent> nativeHarvests = Collections.newSetFromMap(new WeakHashMap<>());

    public HoeHarvestService(JavaPlugin plugin, SessionRegistry sessions, SkillListener skills) {
        this(plugin, sessions, skills, player -> Bukkit.getPlayer(player.getUniqueId()) == player,
            (player, tool) -> {
                ItemStack damaged = tool.damage(1, player);
                // Paper's damage method invokes native durability events (including Preservation).
                player.getInventory().setItemInMainHand(damaged);
            });
    }
    /** Package-private dependencies support disposable Paper fixtures without a fake native player. */
    HoeHarvestService(JavaPlugin plugin, SessionRegistry sessions, SkillListener skills,
                      Predicate<Player> currentPlayer, BiConsumer<Player, ItemStack> wearTool) {
        this.plugin = Objects.requireNonNull(plugin); this.sessions = Objects.requireNonNull(sessions);
        this.skills = Objects.requireNonNull(skills); this.currentPlayer = Objects.requireNonNull(currentPlayer);
        this.wearTool = Objects.requireNonNull(wearTool);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent interaction) {
        if (interaction.getAction() != Action.RIGHT_CLICK_BLOCK || interaction.getHand() != EquipmentSlot.HAND
            || interaction.getClickedBlock() == null || interaction.useInteractedBlock() == Event.Result.DENY) return;
        Player player = interaction.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!CropReplanting.hoe(tool.getType())) return;
        Block crop = lower(interaction.getClickedBlock());
        HoeHarvestRules.Spec spec = HoeHarvestRules.recipe(crop.getType());
        if (spec == null || !mature(crop)) return;
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        if (account == null || !valid(player, account, crop, tool)) return;
        // Prevent vanilla berry harvesting and off-hand use from also running on this click.
        interaction.setUseInteractedBlock(Event.Result.DENY);
        interaction.setUseItemInHand(Event.Result.DENY);
        harvest(player, account, crop, spec, tool.clone());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNativeHarvest(PlayerHarvestBlockEvent event) {
        if (event instanceof ItemsEvent || !nativeHarvests.add(event)) return;
        Block crop = event.getHarvestedBlock();
        BlockData original = crop.getBlockData();
        BlockData expected = original.clone();
        if (crop.getType() == Material.SWEET_BERRY_BUSH && original instanceof Ageable age && age.getAge() >= 2)
            ((Ageable) expected).setAge(1);
        else if (original instanceof CaveVinesPlant vine && vine.hasBerries()) ((CaveVinesPlant) expected).setBerries(false);
        else return;
        Player player = event.getPlayer();
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        ItemStack held = player.getInventory().getItemInMainHand().clone();
        if (account == null || !valid(player, account, crop, held) || copyDrops(event.getItemsHarvested()).isEmpty()) return;
        HoeHarvestBreakEvent bridge = new HoeHarvestBreakEvent(crop, player);
        // Verify vanilla actually changed the fruit state before the skill callback queued below.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!event.isCancelled() && !event.getItemsHarvested().isEmpty() && valid(player, account, crop, held)
                && crop.getBlockData().getAsString().equals(expected.getAsString())) bridge.commit();
            else {
                bridge.setCancelled(true);
                if (crop.getBlockData().getAsString().equals(original.getAsString())) skills.restoreAbortedHarvest(bridge);
            }
        });
        skills.onBreak(bridge);
    }

    private boolean harvest(Player player, AccountId account, Block crop, HoeHarvestRules.Spec spec, ItemStack tool) {
        String key = crop.getWorld().getUID() + ":" + crop.getX() + ":" + crop.getY() + ":" + crop.getZ();
        if (busy.size() >= 128 || !busy.add(key)) return false;
        List<BlockState> before = new ArrayList<>();
        List<BlockData> planted = new ArrayList<>();
        boolean mutated = false;
        HoeHarvestBreakEvent harvest = null;
        try {
            before.add(crop.getState());
            if (spec.tall()) {
                Block upper = crop.getRelative(BlockFace.UP);
                if (upper.getType() != Material.PITCHER_CROP || !(upper.getBlockData() instanceof Bisected half)
                    || half.getHalf() != Bisected.Half.TOP || !(upper.getBlockData() instanceof Ageable age)
                    || age.getAge() != spec.matureAge()) return false;
                before.add(upper.getState());
            }
            Block support = support(crop, spec);
            String supportState = support.getBlockData().getAsString();
            if (!supported(crop, spec, support)) return false;
            List<ItemStack> drops = new ArrayList<>();
            for (ItemStack item : crop.getDrops(tool)) drops.add(item.clone());
            if (drops.isEmpty()) return false;
            if (skills.canRewardFarmingHarvest(crop)) skills.addFarmingHarvestBonuses(player, spec.crop(), drops);
            ItemsEvent items = new ItemsEvent(player, crop, drops);
            Bukkit.getPluginManager().callEvent(items);
            if (items.isCancelled()) return false;
            drops = copyDrops(items.getItemsHarvested());
            if (drops.isEmpty()) return false;
            Seed seed = reserveSeed(spec.seed(), drops, player.getInventory().getStorageContents());
            if (seed == null) {
                player.sendActionBar(net.kyori.adventure.text.Component.text("Keep one " + spec.seed().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                    + " for replanting.", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                return false;
            }
            harvest = new HoeHarvestBreakEvent(crop, player);
            Bukkit.getPluginManager().callEvent(harvest);
            if (harvest.isCancelled() || !harvest.isDropItems() || !valid(player, account, crop, tool)
                || !unchanged(before) || !supportState.equals(support.getBlockData().getAsString())) return false;

            BlockData first = spec.plantingMaterial() == spec.crop() ? before.getFirst().getBlockData().clone()
                : spec.plantingMaterial().createBlockData();
            if (first instanceof CaveVinesPlant vine) vine.setBerries(false);
            else if (first instanceof Ageable age) age.setAge(spec.resetAge());
            if (first instanceof Bisected half) half.setHalf(Bisected.Half.BOTTOM);
            planted.add(first);
            if (spec.tall()) planted.add(Material.AIR.createBlockData());
            mutated = true;
            for (int index = 0; index < before.size(); index++) before.get(index).getBlock().setBlockData(planted.get(index), false);
            ItemStack plantingItem = new ItemStack(spec.seed() == null ? berryProduce(spec.crop()) : spec.seed());
            BlockPlaceEvent placement = spec.tall()
                ? new BlockMultiPlaceEvent(before, support, plantingItem, player, true, EquipmentSlot.HAND)
                : new BlockPlaceEvent(crop, before.getFirst(), support, plantingItem, player, true, EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(placement);
            if (placement.isCancelled() || !placement.canBuild() || !valid(player, account, crop, tool)
                || !matches(before, planted) || !supportState.equals(support.getBlockData().getAsString())
                || !seedAvailable(seed, spec.seed(), drops, player.getInventory().getStorageContents())) return false;
            // Seed consumption is last. No canceled crop change spends seeds or emits loot/XP.
            if (seed.source() == HoeHarvestRules.SeedSource.HARVEST) {
                ItemStack reserved = drops.get(seed.index()); reserved.setAmount(reserved.getAmount() - 1);
            } else if (seed.source() == HoeHarvestRules.SeedSource.STORAGE) {
                ItemStack reserved = player.getInventory().getItem(seed.index()).clone();
                reserved.setAmount(reserved.getAmount() - 1);
                player.getInventory().setItem(seed.index(), reserved.getAmount() == 0 ? null : reserved);
            }
            harvest.commit();
            mutated = false;
            for (ItemStack item : drops) if (item.getAmount() > 0)
                crop.getWorld().dropItemNaturally(crop.getLocation().add(.5, .5, .5), item);
            wearTool.accept(player, player.getInventory().getItemInMainHand());
            player.playSound(crop.getLocation(), Sound.BLOCK_CROP_BREAK, .45F, 1.1F);
            return true;
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Hoe harvest stopped at " + key, error);
            return false;
        } finally {
            if (mutated) for (int index = 0; index < before.size(); index++) {
                BlockState state = before.get(index);
                if (index < planted.size() && state.getBlock().getBlockData().getAsString().equals(planted.get(index).getAsString()))
                    state.update(true, false);
            }
            if (harvest != null && !harvest.committed() && harvest.rewardEligible() && unchanged(before))
                skills.restoreAbortedHarvest(harvest);
            busy.remove(key);
        }
    }

    public static boolean mature(Block block) {
        BlockData data = block.getBlockData();
        return HoeHarvestRules.eligible(block.getType().name(), data instanceof Ageable age ? age.getAge() : -1,
            data instanceof Ageable age ? age.getMaximumAge() : -1,
            data instanceof CaveVinesPlant vine && vine.hasBerries(),
            data instanceof Bisected half && half.getHalf() == Bisected.Half.TOP);
    }
    private static Block lower(Block block) {
        return block.getType() == Material.PITCHER_CROP && block.getBlockData() instanceof Bisected half
            && half.getHalf() == Bisected.Half.TOP ? block.getRelative(BlockFace.DOWN) : block;
    }
    private boolean valid(Player player, AccountId account, Block crop, ItemStack tool) {
        return plugin.isEnabled() && player.isOnline() && !player.isDead() && currentPlayer.test(player)
            && player.getGameMode() == GameMode.SURVIVAL && sessions.account(player.getUniqueId()).filter(account::equals).isPresent()
            && player.getWorld().equals(crop.getWorld()) && crop.getWorld().isChunkLoaded(crop.getX() >> 4, crop.getZ() >> 4)
            && player.getEyeLocation().distanceSquared(crop.getLocation().add(.5, .5, .5)) <= 36
            && player.getInventory().getItemInMainHand().equals(tool);
    }
    private static Block support(Block crop, HoeHarvestRules.Spec spec) {
        return crop.getRelative(spec.crop() == Material.COCOA ? ((Directional) crop.getBlockData()).getFacing()
            : spec.berryVine() ? BlockFace.UP : BlockFace.DOWN);
    }
    private static boolean supported(Block crop, HoeHarvestRules.Spec spec, Block support) {
        if (spec.crop() == Material.COCOA) return Tag.JUNGLE_LOGS.isTagged(support.getType());
        if (spec.berryVine()) return support.getType().isSolid() || support.getBlockData() instanceof CaveVinesPlant;
        if (spec.crop() == Material.SWEET_BERRY_BUSH) return support.getType().isSolid();
        return support.getType() == (spec.crop() == Material.NETHER_WART ? Material.SOUL_SAND : Material.FARMLAND);
    }
    private static boolean unchanged(List<BlockState> states) {
        return states.stream().allMatch(state -> state.getBlockData().getAsString().equals(state.getBlock().getBlockData().getAsString()));
    }
    private static boolean matches(List<BlockState> states, List<BlockData> data) {
        for (int index = 0; index < states.size(); index++) if (!states.get(index).getBlock().getBlockData().getAsString()
            .equals(data.get(index).getAsString())) return false;
        return true;
    }
    private static List<ItemStack> copyDrops(List<ItemStack> source) {
        if (source.size() > 128) throw new IllegalArgumentException("Too many crop drop stacks");
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : source) {
            if (item == null || item.getType().isAir() || item.getAmount() < 1) continue;
            if (item.getAmount() > item.getMaxStackSize()) throw new IllegalArgumentException("Invalid crop drop size");
            result.add(item.clone());
        }
        return result;
    }
    private static Seed reserveSeed(Material material, List<ItemStack> drops, ItemStack[] storage) {
        var dropValues = drops.stream().map(HoeHarvestService::seedValue).toList();
        var storedValues = Arrays.stream(storage).map(HoeHarvestService::seedValue).toList();
        var plan = HoeHarvestRules.reserveSeed(material == null ? null : material.name(), dropValues, storedValues);
        return plan.available() ? new Seed(plan.source(), plan.index()) : null;
    }
    private static HoeHarvestRules.SeedStack seedValue(ItemStack item) {
        return item == null ? new HoeHarvestRules.SeedStack("AIR", 0, false)
            : new HoeHarvestRules.SeedStack(item.getType().name(), item.getAmount(), item.hasItemMeta());
    }
    private static boolean seedAvailable(Seed seed, Material material, List<ItemStack> drops, ItemStack[] storage) {
        return seed.source() == HoeHarvestRules.SeedSource.NONE || (seed.source() == HoeHarvestRules.SeedSource.HARVEST
            ? seed.index() < drops.size() && plainSeed(drops.get(seed.index()), material)
            : seed.index() < storage.length && plainSeed(storage[seed.index()], material));
    }
    private static boolean plainSeed(ItemStack item, Material material) {
        return item != null && item.getType() == material && item.getAmount() > 0 && !item.hasItemMeta();
    }
    private static Material berryProduce(Material crop) { return crop == Material.SWEET_BERRY_BUSH ? Material.SWEET_BERRIES : Material.GLOW_BERRIES; }
    private record Seed(HoeHarvestRules.SeedSource source, int index) { }

    /** Distinguishes our explicit crop transaction from vanilla bush/vine harvesting. */
    public static final class ItemsEvent extends PlayerHarvestBlockEvent {
        ItemsEvent(Player player, Block block, List<ItemStack> drops) { super(player, block, EquipmentSlot.HAND, drops); }
    }
}
