package net.nerdsmp.paper.naming;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Golem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * Names of your own. Gear can be given a nickname at the forge (a Name Tag and some Marks), shown in place of its name
 * with the real name kept underneath; and the beasts on your island can be named by shift-right-clicking them with a
 * Name Tag. Either way a small screen asks for the name and a colour, from plain colours to forge gradients.
 */
public final class Nicknames implements Listener {
    /** A nickname's look: a plain colour, the item's own rarity colour, or a two-tone gradient. */
    public enum Style {
        RARITY("Its own colour", null, null),
        WHITE("White", "#ffffff", null), GOLD("Gold", "#ffaa00", null), AQUA("Aqua", "#55ffff", null), GREEN("Green", "#55ff55", null),
        YELLOW("Yellow", "#ffff55", null), PINK("Pink", "#ff77cc", null), RED("Red", "#ff5555", null),
        EMBER("Ember", "#ff9a3c", "#ff3c3c"), FROST("Frost", "#a8e6ff", "#4f7dff"), VERDANT("Verdant", "#7bed9f", "#2ed573"),
        ROYAL("Royal", "#c084fc", "#facc15"), STORM("Storm", "#94a3b8", "#38bdf8"), DUSK("Dusk", "#f472b6", "#6366f1");

        final String title, from, to;
        Style(String title, String from, String to) { this.title = title; this.from = from; this.to = to; }
        public boolean gradient() { return to != null; }
        /** The name in this style, as MiniMessage; {@code fallback} is the colour tag for RARITY (or white). */
        public String paint(String name, String fallback) {
            String safe = Chat.escape(name);
            if (gradient()) return "<gradient:" + from + ":" + to + ">" + safe + "</gradient>";
            if (from != null) return "<" + from + ">" + safe + "</" + from + ">";
            return (fallback == null ? "<white>" : fallback) + safe;
        }
        public static Style of(String id) {
            try { return valueOf(id.toUpperCase(Locale.ROOT)); } catch (RuntimeException unknown) { return RARITY; }
        }
    }

    /** The longest nickname, and the Marks a gear nickname costs (gradients cost more). */
    public static final int MAX_LENGTH = 24;
    public static final long SOLID_COST = 200, GRADIENT_COST = 750;
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9 '\\-.!?&,]+");
    /** The lore line under a nicknamed item that keeps its real name. */
    static final String REAL_NAME_MARK = "◆ ";

    /**
     * A nickname cleaned for use: tags, colour codes and odd characters stripped, spaces tidied. Empty means "remove the
     * nickname"; null means it can't be used.
     */
    public static String clean(String raw) {
        if (raw == null) return "";
        String text = raw.replaceAll("<[^>]*>", "").replaceAll("[&§][0-9a-fk-orA-FK-OR]", "").trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) return "";
        if (text.length() > MAX_LENGTH || !ALLOWED.matcher(text).matches()) return null;
        return text;
    }

    private final NerdSmpPlugin plugin;
    private final NamespacedKey nameKey, styleKey, pendingKey, guestKey, npcKey;

    public Nicknames(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        this.nameKey = new NamespacedKey(plugin, "nickname");
        this.styleKey = new NamespacedKey(plugin, "nickname_style");
        this.pendingKey = new NamespacedKey(plugin, "nickname_pending");
        this.guestKey = new NamespacedKey(plugin, "island_guest");
        this.npcKey = new NamespacedKey(plugin, "npc_instance");
    }

    // ------------------------------------------------------------------ gear

    /**
     * Shows a nicknamed item's nickname in place of its name, with the real name kept as the first lore line. Called by
     * the item renderer after every rebuild, and after a nickname is set or cleared.
     */
    public static void dress(NamespacedKey nameKey, NamespacedKey styleKey, ItemMeta meta) {
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        if (!lore.isEmpty() && PlainTextComponentSerializer.plainText().serialize(lore.getFirst()).startsWith(REAL_NAME_MARK)) lore.removeFirst();
        String nickname = meta.getPersistentDataContainer().get(nameKey, PersistentDataType.STRING);
        if (nickname == null || nickname.isBlank()) {
            meta.displayName(null);
            meta.lore(lore.isEmpty() ? null : lore);
            return;
        }
        Style style = Style.of(meta.getPersistentDataContainer().getOrDefault(styleKey, PersistentDataType.STRING, "rarity"));
        Component real = meta.hasItemName() ? meta.itemName() : Component.text("");
        String fallback = "<" + (real.color() == null ? "white" : real.color().asHexString()) + ">";
        meta.displayName(Chat.render("<!i>" + style.paint(nickname, fallback)));
        lore.addFirst(Chat.render("<!i><dark_gray>" + REAL_NAME_MARK + "</dark_gray>").append(real.colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.GRAY)));
        meta.lore(lore);
    }

    /** {@link #dress(NamespacedKey, NamespacedKey, ItemMeta)} with this plugin's keys. */
    public static void dress(org.bukkit.plugin.Plugin plugin, ItemMeta meta) {
        dress(new NamespacedKey(plugin, "nickname"), new NamespacedKey(plugin, "nickname_style"), meta);
    }

    public boolean nameable(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getAmount() == 1 && plugin.items().gear(item).isPresent();
    }

    /**
     * The forge's Nickname button: the gear in the forge's left input is marked, the forge closes (handing it back as it
     * always does), and the naming screen asks for a name and a look. The mark finds the piece again when it's saved.
     */
    public void nameForgeItem(Player player, Inventory forge, int slot) {
        ItemStack gear = forge.getItem(slot);
        if (!nameable(gear)) { Chat.error(player, "Put an identified weapon, tool or armour piece in the left input first."); return; }
        String token = UUID.randomUUID().toString();
        gear.editMeta(meta -> meta.getPersistentDataContainer().set(pendingKey, PersistentDataType.STRING, token));
        forge.setItem(slot, gear);
        String current = gear.getItemMeta().getPersistentDataContainer().getOrDefault(nameKey, PersistentDataType.STRING, "");
        Style style = Style.of(gear.getItemMeta().getPersistentDataContainer().getOrDefault(styleKey, PersistentDataType.STRING, "rarity"));
        player.closeInventory();
        ask(player, "<gold>Nickname your gear</gold>", List.of(
                "<gray>Plain colours cost</gray> <gold>" + Chat.marks(SOLID_COST) + "</gold><gray>, gradients</gray> <gold>" + Chat.marks(GRADIENT_COST) + "</gold><gray>,</gray>",
                "<gray>plus one Name Tag. Leave the name empty to remove a nickname (free).</gray>"),
            gear, current, style, true, (name, chosen) -> saveGear(player, token, name, chosen), () -> unmark(player, token));
    }

    private ItemStack findPending(Player player, String token) {
        for (ItemStack stack : player.getInventory().getContents())
            if (stack != null && stack.hasItemMeta() && token.equals(stack.getItemMeta().getPersistentDataContainer().get(pendingKey, PersistentDataType.STRING))) return stack;
        return null;
    }

    private void unmark(Player player, String token) {
        ItemStack stack = findPending(player, token);
        if (stack != null) stack.editMeta(meta -> meta.getPersistentDataContainer().remove(pendingKey));
    }

    private void saveGear(Player player, String token, String raw, Style style) {
        ItemStack gear = findPending(player, token);
        if (gear == null) { Chat.error(player, "Keep the gear in your inventory while you name it."); return; }
        String name = clean(raw);
        if (name == null) { unmark(player, token); Chat.error(player, "Names can be up to " + MAX_LENGTH + " letters, numbers, spaces and simple punctuation."); return; }
        if (name.isEmpty()) {
            gear.editMeta(meta -> {
                meta.getPersistentDataContainer().remove(pendingKey);
                meta.getPersistentDataContainer().remove(nameKey);
                meta.getPersistentDataContainer().remove(styleKey);
                dress(nameKey, styleKey, meta);
            });
            Chat.info(player, "The nickname is gone.");
            return;
        }
        if (!player.getInventory().containsAtLeast(new ItemStack(Material.NAME_TAG), 1)) { unmark(player, token); Chat.error(player, "You need a Name Tag. The Visitor's Stall sells them."); return; }
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (account == null) { unmark(player, token); Chat.accountLoading(player); return; }
        long cost = style.gradient() ? GRADIENT_COST : SOLID_COST;
        plugin.economy().debit(account, cost, new EconomyService.TransactionReason("nickname", "Gear nickname"), "nickname:" + token)
            .whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null || result == null || result.status() != EconomyService.TransactionResult.Status.APPLIED) {
                    unmark(player, token);
                    Chat.error(player, "That nickname costs " + Chat.marks(cost) + ".");
                    return;
                }
                ItemStack again = findPending(player, token);
                if (again == null || !player.getInventory().removeItem(new ItemStack(Material.NAME_TAG)).isEmpty()) {
                    plugin.economy().credit(account, cost, new EconomyService.TransactionReason("nickname-refund", "Gear nickname refunded"), "nickname-refund:" + token);
                    Chat.error(player, "The gear or the Name Tag went missing, so your Marks are back.");
                    return;
                }
                again.editMeta(meta -> {
                    meta.getPersistentDataContainer().remove(pendingKey);
                    meta.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING, name);
                    meta.getPersistentDataContainer().set(styleKey, PersistentDataType.STRING, style.name().toLowerCase(Locale.ROOT));
                    dress(nameKey, styleKey, meta);
                });
                Chat.send(player, "<gold>✦ Your gear is now called</gold> " + style.paint(name, "<white>") + "<gold>.</gold> <gray>-" + Chat.marks(cost) + ", -1 Name Tag</gray>");
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6F, 1.4F);
            }));
    }

    // ------------------------------------------------------------------ island beasts

    /** Beasts a co-op may name: the island's animals, golems, fish and the like, never monsters, NPCs or visitors. */
    public boolean nameable(Entity entity) {
        if (!(entity instanceof LivingEntity) || entity instanceof Player) return false;
        if (!(entity instanceof Animals || entity instanceof Golem || entity instanceof WaterMob || entity instanceof Allay || entity instanceof Ambient)) return false;
        var data = entity.getPersistentDataContainer();
        if (data.has(guestKey) || data.has(npcKey)) return false;
        return plugin.mobs() == null || !(entity instanceof LivingEntity living) || !plugin.mobs().isCustomMob(living);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNameBeast(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !player.isSneaking() || player.getInventory().getItemInMainHand().getType() != Material.NAME_TAG) return;
        Entity entity = event.getRightClicked();
        if (!nameable(entity) || plugin.skyblock() == null || !plugin.skyblock().isIslandWorld(entity.getWorld())) return;
        event.setCancelled(true);
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        var island = plugin.skyblock().islands().at(entity.getLocation().getBlockX() >> 4, entity.getLocation().getBlockZ() >> 4).orElse(null);
        if (account == null || island == null || !island.member(account)) { Chat.error(player, "You can only name beasts on an island you belong to."); return; }
        String current = entity.customName() == null ? "" : PlainTextComponentSerializer.plainText().serialize(entity.customName());
        UUID target = entity.getUniqueId();
        ask(player, "<green>Name this " + net.nerdsmp.paper.presentation.VisualFormat.titleCase(entity.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ')) + "</green>",
            List.of("<gray>Uses one Name Tag. Named beasts stay put: they never despawn,</gray>", "<gray>stack or get culled. Leave it empty to clear the name.</gray>"),
            null, current, Style.WHITE, false, (raw, style) -> saveBeast(player, target, raw, style), () -> { });
    }

    private void saveBeast(Player player, UUID target, String raw, Style style) {
        Entity entity = Bukkit.getEntity(target);
        if (!(entity instanceof LivingEntity living) || !living.isValid() || !living.getWorld().equals(player.getWorld())
            || living.getLocation().distanceSquared(player.getLocation()) > 64) { Chat.error(player, "Stay close to the beast while you name it."); return; }
        String name = clean(raw);
        if (name == null) { Chat.error(player, "Names can be up to " + MAX_LENGTH + " letters, numbers, spaces and simple punctuation."); return; }
        if (name.isEmpty()) {
            living.customName(null);
            living.setCustomNameVisible(false);
            Chat.info(player, "The name is gone.");
            return;
        }
        if (!player.getInventory().removeItem(new ItemStack(Material.NAME_TAG)).isEmpty()) { Chat.error(player, "You need a Name Tag."); return; }
        living.customName(Chat.render(style.paint(name, "<white>")));
        living.setCustomNameVisible(true);
        living.setPersistent(true);
        living.setRemoveWhenFarAway(false);
        living.getWorld().spawnParticle(Particle.HEART, living.getLocation().add(0, living.getHeight() + 0.3, 0), 5, 0.3, 0.2, 0.3, 0);
        player.playSound(living.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.2F);
        Chat.send(player, "<green>✦ Say hello to</green> " + style.paint(name, "<white>") + "<green>!</green>");
    }

    // ------------------------------------------------------------------ the naming screen

    /** Opens the naming screen: a text box and a style picker; {@code done} gets the raw name and chosen style. */
    private void ask(Player player, String title, List<String> lines, ItemStack shown, String current, Style selected, boolean rarityOption,
                     BiConsumer<String, Style> done, Runnable cancelled) {
        List<SingleOptionDialogInput.OptionEntry> options = new ArrayList<>();
        for (Style style : Style.values()) {
            if (style == Style.RARITY && !rarityOption) continue;
            String label = style.paint(style.title, "<gray>") + (style.gradient() && rarityOption ? " <gray>(gradient)</gray>" : "");
            options.add(SingleOptionDialogInput.OptionEntry.create(style.name().toLowerCase(Locale.ROOT), Chat.render(label), style == selected));
        }
        List<DialogBody> body = new ArrayList<>();
        if (shown != null) body.add(DialogBody.item(shown).build());
        for (String line : lines) body.add(DialogBody.plainMessage(Chat.render(line)));
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(5)).build();
        Dialog dialog = Dialog.create(factory -> factory.empty()
            .base(DialogBase.builder(Chat.render(title))
                .canCloseWithEscape(true)
                .body(body)
                .inputs(List.of(
                    DialogInput.text("name", Chat.render("<gray>Name</gray>")).maxLength(MAX_LENGTH).initial(current == null ? "" : current).width(240).build(),
                    DialogInput.singleOption("style", Chat.render("<gray>Look</gray>"), options).width(240).build()))
                .build())
            .type(DialogType.confirmation(
                ActionButton.create(Chat.render("<green>Save</green>"), Chat.render("<gray>Save this name</gray>"), 110,
                    DialogAction.customClick((view, audience) -> {
                        String name = view.getText("name"), style = view.getText("style");
                        Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) done.accept(name, Style.of(style == null ? "white" : style)); });
                    }, once)),
                ActionButton.create(Chat.render("<gray>Cancel</gray>"), null, 110,
                    DialogAction.customClick((view, audience) -> Bukkit.getScheduler().runTask(plugin, cancelled), once)))));
        player.showDialog(dialog);
    }
}
