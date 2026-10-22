package net.nerdsmp.paper.item;

import net.kyori.adventure.text.format.TextDecoration;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Right-clicking an experience bottle opens a skill picker instead of throwing it; choosing a skill
 * spends the bottle for skill XP ({@link SkillLamps}). Shift-click spends the whole stack. Dispensers
 * still throw bottles as usual.
 */
public final class SkillLampService implements Listener {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 20, 21, 22, 23, 24};
    private final NerdSmpPlugin plugin;
    private final NamespacedKey gradeKey;

    public SkillLampService(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        this.gradeKey = new NamespacedKey(plugin, "skill_lamp");
    }

    /** A Greater or Grand bottle; a plain one is any vanilla experience bottle. */
    public ItemStack bottle(SkillLamps.Grade grade, int amount) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE, Math.max(1, Math.min(64, amount)));
        if (grade == SkillLamps.Grade.PLAIN) return item;
        var meta = item.getItemMeta();
        String color = grade == SkillLamps.Grade.GRAND ? "gold" : "aqua";
        meta.itemName(Chat.render("<" + color + ">" + grade.title() + "</" + color + ">").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Chat.render("<gray>Right-click and choose a skill to gain</gray>").decoration(TextDecoration.ITALIC, false),
            Chat.render("<white>" + Math.round(grade.share() * 100) + "%</white> <gray>of the XP it needs for its next level.</gray>").decoration(TextDecoration.ITALIC, false)));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(gradeKey, PersistentDataType.STRING, grade.key());
        item.setItemMeta(meta);
        return item;
    }

    public SkillLamps.Grade grade(ItemStack item) {
        if (item == null || item.getType() != Material.EXPERIENCE_BOTTLE || !item.hasItemMeta()) return SkillLamps.Grade.PLAIN;
        String raw = item.getItemMeta().getPersistentDataContainer().get(gradeKey, PersistentDataType.STRING);
        return SkillLamps.Grade.parse(raw).orElse(SkillLamps.Grade.PLAIN);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack held = event.getItem();
        if (held == null || held.getType() != Material.EXPERIENCE_BOTTLE) return;
        event.setCancelled(true);
        open(event.getPlayer(), event.getHand() == EquipmentSlot.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND);
    }

    private void open(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        SkillLamps.Grade grade = grade(held);
        Picker picker = new Picker(player, hand, grade);
        picker.inventory = Bukkit.createInventory(picker, 36, Chat.render("<dark_green><bold>Choose a skill</bold></dark_green>"));
        MenuKit.frame(picker.inventory);
        ProgressionService.ProgressionSnapshot snapshot = plugin.presentation() == null ? null : plugin.presentation().progression(player);
        Skill[] skills = Skill.values();
        for (int index = 0; index < skills.length && index < SLOTS.length; index++) {
            Skill skill = skills[index];
            int level = level(snapshot, skill);
            long xp = SkillLamps.experience(grade, level);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Level</gray> <white>" + level + "</white>");
            if (xp > 0) {
                lore.add("<gray>One bottle:</gray> <green>+" + xp + " XP</green>");
                lore.add("");
                lore.add("<yellow>Click:</yellow> <gray>use one bottle</gray>");
                lore.add("<yellow>Shift-click:</yellow> <gray>use the whole stack</gray>");
            } else lore.add("<gold>Mastered: nothing left to learn.</gold>");
            picker.inventory.setItem(SLOTS[index], MenuKit.item(icon(skill), "<green>" + skill.displayName() + "</green>", lore));
            picker.skills.put(SLOTS[index], skill);
        }
        picker.inventory.setItem(4, MenuKit.item(Material.EXPERIENCE_BOTTLE, "<aqua>" + grade.title() + "</aqua>", List.of(
            "<gray>Pick the skill this bottle's experience goes to.</gray>")));
        player.openInventory(picker.inventory);
    }

    private static int level(ProgressionService.ProgressionSnapshot snapshot, Skill skill) {
        if (snapshot == null || snapshot.skills().get(skill) == null) return 0;
        return snapshot.skills().get(skill).level();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Picker picker)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(picker.owner)) return;
        Skill skill = picker.skills.get(event.getRawSlot());
        if (skill == null) return;
        boolean all = event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT;
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            spend(player, picker, skill, all);
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Picker) event.setCancelled(true);
    }

    private void spend(Player player, Picker picker, Skill skill, boolean all) {
        ItemStack held = player.getInventory().getItem(picker.hand);
        // The bottle must still be in hand and the same grade the menu was opened for.
        if (held == null || held.getType() != Material.EXPERIENCE_BOTTLE || grade(held) != picker.grade) {
            Chat.notice(player, "Hold the experience bottle in your hand to use it.");
            return;
        }
        ProgressionService.ProgressionSnapshot snapshot = plugin.presentation() == null ? null : plugin.presentation().progression(player);
        int level = level(snapshot, skill);
        long each = SkillLamps.experience(picker.grade, level);
        if (each <= 0) {
            Chat.notice(player, skill.displayName() + " is already mastered.");
            return;
        }
        int count = all ? held.getAmount() : 1;
        held.setAmount(held.getAmount() - count);
        player.getInventory().setItem(picker.hand, held.getAmount() <= 0 ? null : held);
        long total = each * count;
        if (plugin.skills() != null)
            plugin.skills().awardMilestone(player, skill, total, "lamp:" + player.getUniqueId() + ":" + System.nanoTime());
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .6F, 1.4F);
        Chat.success(player, "You pour " + (count == 1 ? "the bottle" : count + " bottles") + " into <white>" + skill.displayName()
            + "</white>: <green>+" + total + " XP</green>.");
    }

    private static Material icon(Skill skill) {
        return switch (skill) {
            case MINING -> Material.IRON_PICKAXE;
            case FORESTRY -> Material.OAK_SAPLING;
            case FARMING -> Material.WHEAT;
            case FISHING -> Material.FISHING_ROD;
            case HUNTING -> Material.BOW;
            case EXCAVATION -> Material.BRUSH;
            case SMITHING -> Material.ANVIL;
            case ALCHEMY -> Material.BREWING_STAND;
            case BUILDING -> Material.BRICKS;
            case COOKING -> Material.COOKED_CHICKEN;
            case ENCHANTING -> Material.ENCHANTING_TABLE;
        };
    }

    public Optional<SkillLamps.Grade> parse(String id) {
        return switch (id) {
            case "greater_experience_bottle" -> Optional.of(SkillLamps.Grade.GREATER);
            case "grand_experience_bottle" -> Optional.of(SkillLamps.Grade.GRAND);
            default -> Optional.empty();
        };
    }

    private static final class Picker implements InventoryHolder {
        private final java.util.UUID owner;
        private final EquipmentSlot hand;
        private final SkillLamps.Grade grade;
        private final java.util.Map<Integer, Skill> skills = new java.util.HashMap<>();
        private Inventory inventory;

        private Picker(Player player, EquipmentSlot hand, SkillLamps.Grade grade) {
            this.owner = player.getUniqueId();
            this.hand = hand;
            this.grade = grade;
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
