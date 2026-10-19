package net.nerdsmp.paper.engine;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.engine.mob.MobBlueprint;
import net.nerdsmp.paper.engine.mob.MobEngine;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.zone.ZoneMobTypes;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Staff-only, read-only inventories surrounding immutable content drafts. No inventory item is ever a grant. */
public final class ContentEditor implements Listener, AutoCloseable {
    public static final String ITEMS_PERMISSION = "nerdsmp.content.items";
    public static final String MOBS_PERMISSION = "nerdsmp.content.mobs";
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "CRAFTED");
    private final NerdSmpPlugin plugin;
    private final ItemEngine items;
    private final MobEngine mobs;
    private final Map<UUID, Draft> drafts = new HashMap<>();
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> views = new HashMap<>();
    private final BukkitTask expiry;
    private boolean closed;

    public ContentEditor(NerdSmpPlugin plugin, ItemEngine items, MobEngine mobs) {
        this.plugin = plugin; this.items = items; this.mobs = mobs;
        expiry = Bukkit.getScheduler().runTaskTimer(plugin, this::expirePrompts, 20L, 20L);
    }

    public boolean handleItems(CommandSender sender, String[] args) { return command(sender, args, false); }
    public boolean handleMobs(CommandSender sender, String[] args) { return command(sender, args, true); }
    public void openItems(Player player) { catalog(player, false, 0); }
    public void openMobs(Player player) { catalog(player, true, 0); }
    public void cancelEditing(Player player) { clear(player.getUniqueId()); }

    private boolean command(CommandSender sender, String[] args, boolean mob) {
        if (!sender.hasPermission(permission(mob))) { Chat.error(sender, "You do not have permission to edit this content."); return true; }
        if (!(sender instanceof Player player)) { Chat.error(sender, "Open this editor in game. Content is saved only after Publish is confirmed."); return true; }
        if (!session(player)) { Chat.accountLoading(player); return true; }
        prompts.remove(player.getUniqueId());
        try {
            if (args.length == 0 || args[0].equalsIgnoreCase("browse")) catalog(player, mob, 0);
            else if (args[0].equalsIgnoreCase("templates")) templates(player, mob, 0);
            else if (!mob && args[0].equalsIgnoreCase("sets")) setCatalog(player, 0);
            else if (!mob && args[0].equalsIgnoreCase("setdraft") && args.length == 2) beginSet(player, EditorPolicy.identifier(args[1]), false);
            else if (!mob && args[0].equalsIgnoreCase("setedit") && args.length == 2) beginSet(player, args[1], true);
            else if (args[0].equalsIgnoreCase("resume")) {
                Draft draft = drafts.get(player.getUniqueId());
                if (draft == null || draft.mob != mob) throw new IllegalArgumentException("There is no open draft to resume.");
                showDraft(player, draft);
            } else if (args[0].equalsIgnoreCase("draft") && args.length == 3) {
                begin(player, mob, EditorPolicy.identifier(args[1]), args[2].toLowerCase(Locale.ROOT));
            } else if (args[0].equalsIgnoreCase("edit") && args.length == 2) {
                edit(player, mob, args[1]);
            } else if (args[0].equalsIgnoreCase("copy") && args.length == 3) {
                copy(player, mob, args[1], EditorPolicy.identifier(args[2]));
            } else if (args[0].equalsIgnoreCase("cancel")) {
                clear(player.getUniqueId()); player.closeInventory(); Chat.notice(player, "Draft discarded. Published content was not changed.");
            } else Chat.usage(player, "/" + (mob ? "mobengine" : "itemengine") + " [browse|templates|draft <id> <template>|edit <id>|copy <source> <id>|resume|cancel" + (mob ? "]" : "|sets]"));
        } catch (IllegalArgumentException | IllegalStateException failure) { fail(player, failure.getMessage()); }
        return true;
    }

    public List<String> tab(boolean mob, String[] args) {
        if (args.length == 0) return List.of();
        if (args.length == 1) return (mob ? List.of("browse", "templates", "draft", "edit", "copy", "cancel", "resume")
            : List.of("browse", "templates", "draft", "edit", "copy", "cancel", "resume", "sets", "setdraft", "setedit")).stream()
            .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        List<String> choices = List.of();
        if (args.length == 3 && args[0].equalsIgnoreCase("draft")) choices = mob ? mobs.templates().keySet().stream().sorted().toList() : items.templates();
        if (args.length == 2 && (args[0].equalsIgnoreCase("edit") || args[0].equalsIgnoreCase("copy"))) {
            choices = mob ? mobs.all().stream().map(MobBlueprint::id).toList() : items.all().stream().map(ItemBlueprint::id).toList();
        }
        if (!mob && args.length == 2 && args[0].equalsIgnoreCase("setedit")) choices = items.sets().stream().map(ItemSetBlueprint::id).toList();
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(value -> value.startsWith(last)).limit(100).toList();
    }

    private void catalog(Player player, boolean mob, int requested) {
        if (!allowed(player, mob)) return;
        Page page = page(player, mob, mob ? "Encounter Library" : "Item Library", null);
        List<String> ids = mob ? plugin.mobs().ids().stream().sorted().toList() : plugin.items().itemIds().stream().map(ItemId::value).sorted().toList();
        int index = EditorPolicy.page(requested, ids.size());
        List<String> visible = EditorPolicy.entries(ids, index);
        for (int i = 0; i < visible.size(); i++) {
            String id = visible.get(i);
            boolean authored = mob ? mobs.find(id).isPresent() : items.find(id).isPresent();
            ItemStack display;
            if (mob) {
                MobBlueprint blueprint = mobs.blueprint(id).orElse(null);
                display = card(blueprint == null ? Material.ZOMBIE_HEAD : mobIcon(blueprint),
                    blueprint == null ? readable(id) : blueprint.name(), List.of(id, authored ? "Authored encounter • click to edit" : "Built-in encounter • click to inspect"));
            } else {
                display = inert(plugin.items().create(new ItemId(id)));
                append(display, List.of("", id, authored ? "Authored item • click to edit" : "Built-in item • click to inspect"));
            }
            button(page, SLOTS[i], display, ignored -> {
                if (authored) edit(player, mob, id); else inspectBuiltIn(player, mob, id);
            });
        }
        button(page, 4, Material.BOOK, mob ? "Encounter Library" : "Item Library", List.of("Templates become drafts. Publish makes them live.", "Built-in content can be copied, not overwritten."), ignored -> { });
        button(page, 48, Material.WRITABLE_BOOK, "New from Template", List.of("Choose a starting point."), ignored -> templates(player, mob, 0));
        if (!mob) button(page, 51, Material.CHAINMAIL_CHESTPLATE, "Equipment Sets", List.of("Create named sets with piece-count bonuses."), ignored -> setCatalog(player, 0));
        pagination(page, index, ids.size(), next -> catalog(player, mob, next));
        Draft existing = drafts.get(player.getUniqueId());
        if (existing != null && existing.mob == mob) button(page, 50, Material.ANVIL, "Resume Draft", List.of(existing.id()), ignored -> showDraft(player, existing));
        open(player, page);
    }

    private void inspectBuiltIn(Player player, boolean mob, String id) {
        Page page = page(player, mob, "Built-in • " + readable(id), null);
        MobBlueprint encounter = mob ? mobs.blueprint(id).orElse(null) : null;
        ItemStack preview = mob ? encounter == null ? card(Material.ZOMBIE_HEAD, readable(id), List.of(id, "Specialized built-in encounter", "Its existing habitat and boss rules remain in place."))
            : card(mobIcon(encounter), encounter.name(), mobLines(encounter))
            : inert(plugin.items().create(new ItemId(id)));
        button(page, 22, preview, ignored -> { });
        if (!mob || encounter != null) button(page, 31, Material.WRITABLE_BOOK, "Create a Copy", List.of("The original keeps its identity and behavior.", "Your copy starts with a new content ID."), ignored -> ask(player, mob, null,
            "Enter an ID for the copy (for example " + (mob ? "frostguard" : "frostguard_blade") + ").", value -> copy(player, mob, id, EditorPolicy.identifier(value))));
        else button(page, 31, Material.BOOK, "Specialized Encounter", List.of("This encounter needs its existing lifecycle.", "Place it with /zone spawn using its existing ID."), ignored -> { });
        button(page, 45, Material.ARROW, "Library", List.of(), ignored -> catalog(player, mob, 0));
        open(player, page);
    }

    private void templates(Player player, boolean mob, int requested) {
        if (!allowed(player, mob)) return;
        Page page = page(player, mob, mob ? "Encounter Templates" : "Item Templates", null);
        List<String> names = mob ? mobs.templates().keySet().stream().sorted().toList() : items.templates();
        int index = EditorPolicy.page(requested, names.size());
        List<String> visible = EditorPolicy.entries(names, index);
        for (int i = 0; i < visible.size(); i++) {
            String name = visible.get(i);
            ItemStack icon = mob ? card(mobIcon(mobs.templates().get(name)), readable(name), mobLines(mobs.templates().get(name)))
                : items.preview(items.draft("template_preview", name));
            append(icon, List.of("", "Click to create a draft."));
            button(page, SLOTS[i], icon, ignored -> ask(player, mob, null,
                "Enter a content ID, such as " + (mob ? "frostguard" : "frostguard_blade") + ".", value -> begin(player, mob, EditorPolicy.identifier(value), name)));
        }
        button(page, 4, Material.KNOWLEDGE_BOOK, "Choose a Template", List.of("Nothing is published until you confirm it."), ignored -> { });
        button(page, 45, Material.ARROW, "Library", List.of(), ignored -> catalog(player, mob, 0));
        pagination(page, index, names.size(), next -> templates(player, mob, next));
        open(player, page);
    }

    private void begin(Player player, boolean mob, String id, String template) {
        if (!allowed(player, mob)) return;
        if (mob ? plugin.mobs().ids().contains(id) : items.exists(id)) throw new IllegalArgumentException("That ID already belongs to published content.");
        Draft draft = new Draft(mob, account(player));
        if (mob) { draft.encounter = mobs.draft(id, template); draft.mobRevision = mobs.revision(); }
        else draft.item = items.draft(id, template);
        install(player, draft); showDraft(player, draft);
    }

    private void edit(Player player, boolean mob, String id) {
        if (!allowed(player, mob)) return;
        Draft draft = new Draft(mob, account(player));
        if (mob) {
            draft.encounter = mobs.find(id).orElseThrow(() -> new IllegalArgumentException("Only authored encounters can be edited. Copy built-in content first."));
            draft.originalMob = draft.encounter; draft.mobRevision = mobs.revision();
        } else {
            draft.item = items.find(id).orElseThrow(() -> new IllegalArgumentException("Only authored items can be edited. Copy built-in content first."));
            draft.originalItem = draft.item;
        }
        install(player, draft); showDraft(player, draft);
    }

    private void copy(Player player, boolean mob, String source, String id) {
        if (!allowed(player, mob)) return;
        if (mob ? plugin.mobs().ids().contains(id) : items.exists(id)) throw new IllegalArgumentException("Choose an unused content ID.");
        Draft draft = new Draft(mob, account(player));
        if (mob) { draft.encounter = mobs.copy(source, id); draft.mobRevision = mobs.revision(); }
        else draft.item = items.copy(id, new ItemId(source));
        install(player, draft); showDraft(player, draft);
    }

    private void install(Player player, Draft draft) {
        if (!drafts.containsKey(player.getUniqueId()) && drafts.size() >= EditorPolicy.MAX_EDITORS) throw new IllegalStateException("The editor is busy. Try again shortly.");
        prompts.remove(player.getUniqueId()); drafts.put(player.getUniqueId(), draft);
    }

    private void showDraft(Player player, Draft draft) {
        if (!validDraft(player, draft)) return;
        if (draft.set != null) showSet(player, draft); else if (draft.mob) showMob(player, draft); else showItem(player, draft);
    }

    private void showItem(Player player, Draft draft) {
        ItemBlueprint item = draft.item;
        Page page = page(player, false, "Item • " + item.name(), draft);
        button(page, 4, items.preview(item), ignored -> { });
        field(page, 10, Material.NAME_TAG, "Name", item.name(), player, draft, "Enter the item name.", text -> replace(draft, "name", EditorPolicy.text(text, 64)));
        button(page, 11, item.material(), "Appearance", List.of(readable(item.material().name()), draft.originalItem == null ? "Click to choose a vanilla material." : "Locked after publishing to protect existing items."), ignored -> {
            if (draft.originalItem == null) ask(player, false, draft, "Enter a vanilla item material, such as iron_sword.", value -> {
                Material material = Material.matchMaterial(value.strip());
                if (material == null || !material.isItem()) throw new IllegalArgumentException("That is not a vanilla item material.");
                replace(draft, "material", material); showDraft(player, draft);
            });
        });
        button(page, 12, Material.AMETHYST_SHARD, "Rarity", List.of(readable(item.rarity()), "Click to cycle rarity."), ignored -> {
            replace(draft, "rarity", RARITIES.get((RARITIES.indexOf(item.rarity()) + 1) % RARITIES.size())); showDraft(player, draft);
        });
        field(page, 13, Material.EXPERIENCE_BOTTLE, "Item Level", Integer.toString(item.itemLevel()), player, draft, "Enter an item level from 1 to 100.", text -> replace(draft, "level", EditorPolicy.integer(text, 1, 100)));
        field(page, 14, Material.DIAMOND, "Sockets", Integer.toString(item.sockets()), player, draft, "Enter 0–4 sockets. Published items cannot lose socket capacity.", text -> replace(draft, "sockets", EditorPolicy.integer(text, 0, 4)));
        button(page, 15, Material.CHAINMAIL_CHESTPLATE, "Equipment Set", List.of(item.setId() == null ? "No set" : readable(item.setId()), "Click to choose a set."), ignored -> setChoices(player, draft, 0));
        field(page, 16, Material.WRITABLE_BOOK, "Description", item.lore().isEmpty() ? "No description" : item.lore().getFirst(), player, draft,
            "Enter up to 8 lore lines separated by |, or 'clear'.", text -> {
                List<String> lore = text.equalsIgnoreCase("clear") ? List.of() : Arrays.stream(text.split("\\|", -1)).map(String::strip).toList();
                replace(draft, "lore", lore);
            });
        button(page, 20, Material.REDSTONE, "Stats", List.of(item.stats().size() + " active stats", "Choose the stats this item grants."), ignored -> stats(player, draft, 0));
        button(page, 22, Material.SPYGLASS, "Tooltip Preview", List.of("See the finished tooltip without creating an item."), ignored -> preview(player, draft));
        button(page, 24, Material.LIME_DYE, "Review & Publish", List.of("Save this definition and add it to the live catalog.", "Existing sockets and enchantments are preserved."), ignored -> confirm(player, draft, false));
        commonDraftButtons(page, player, draft); open(player, page);
    }

    @SuppressWarnings("unchecked")
    private void replace(Draft draft, String field, Object value) {
        ItemBlueprint old = draft.item;
        ItemBlueprint candidate = new ItemBlueprint(old.id(), field.equals("name") ? (String) value : old.name(),
            field.equals("material") ? (Material) value : old.material(), field.equals("rarity") ? (String) value : old.rarity(),
            field.equals("sockets") ? (Integer) value : old.sockets(), field.equals("level") ? (Integer) value : old.itemLevel(),
            field.equals("set") ? (String) value : old.setId(), field.equals("lore") ? (List<String>) value : old.lore(),
            field.equals("stats") ? (Map<String, Double>) value : old.stats());
        if (draft.originalItem != null && candidate.sockets() < draft.originalItem.sockets()) throw new IllegalArgumentException("Published socket capacity cannot shrink.");
        draft.item = candidate; draft.revision++;
    }

    private void stats(Player player, Draft draft, int requested) {
        Page page = page(player, false, "Item Stats", draft);
        List<String> names = ItemStats.allowed().stream().sorted().toList();
        int index = EditorPolicy.page(requested, names.size());
        List<String> visible = EditorPolicy.entries(names, index);
        for (int i = 0; i < visible.size(); i++) {
            String stat = visible.get(i); double value = draft.item.stats().getOrDefault(stat, 0.0);
            button(page, SLOTS[i], Material.REDSTONE, readable(stat), List.of("Value: " + number(value) + (stat.endsWith("percent") ? "%" : ""), "Click to enter a value. Zero removes the stat."), ignored -> ask(player, false, draft,
                "Enter " + readable(stat) + " (0–" + statMaximum(stat) + (stat.endsWith("percent") ? " percentage points" : "") + ").", input -> {
                    Map<String, Double> next = new LinkedHashMap<>(draft.item.stats());
                    double amount = EditorPolicy.number(input, 0, statMaximum(stat));
                    if (amount == 0) next.remove(stat); else next.put(stat, amount);
                    replace(draft, "stats", next); stats(player, draft, index);
                }));
        }
        button(page, 45, Material.ARROW, "Back to Item", List.of(), ignored -> showDraft(player, draft));
        pagination(page, index, names.size(), next -> stats(player, draft, next)); open(player, page);
    }

    private void setChoices(Player player, Draft draft, int requested) {
        Page page = page(player, false, "Choose Equipment Set", draft);
        List<String> sets = items.knownSetIds().stream().sorted().toList();
        int index = EditorPolicy.page(requested, sets.size()); List<String> visible = EditorPolicy.entries(sets, index);
        for (int i = 0; i < visible.size(); i++) {
            String set = visible.get(i);
            button(page, SLOTS[i], Material.CHAINMAIL_CHESTPLATE, readable(set), List.of(set, "Click to use this set."), ignored -> { replace(draft, "set", set); showDraft(player, draft); });
        }
        button(page, 49, Material.BARRIER, "No Set", List.of(), ignored -> { replace(draft, "set", null); showDraft(player, draft); });
        button(page, 45, Material.ARROW, "Back to Item", List.of(), ignored -> showDraft(player, draft));
        pagination(page, index, sets.size(), next -> setChoices(player, draft, next)); open(player, page);
    }

    private void setCatalog(Player player, int requested) {
        if (!allowed(player, false)) return;
        Page page = page(player, false, "Equipment Sets", null);
        List<String> ids = items.knownSetIds().stream().sorted().toList();
        int index = EditorPolicy.page(requested, ids.size()); List<String> visible = EditorPolicy.entries(ids, index);
        for (int i = 0; i < visible.size(); i++) {
            String id = visible.get(i); boolean authored = items.findSet(id).isPresent();
            var definition = plugin.items().setDefinition(id).orElse(null);
            List<String> lines = new ArrayList<>(); lines.add(id);
            if (definition != null) {
                lines.add(definition.pieceCount() + "-piece set");
                definition.bonuses().forEach((count, bonus) -> lines.add(count + " pieces: " + bonus.description()));
            }
            lines.add(authored ? "Click to edit this set." : "Built-in set • available for authored items.");
            button(page, SLOTS[i], Material.CHAINMAIL_CHESTPLATE, definition == null ? readable(id) : definition.name(), lines,
                ignored -> { if (authored) beginSet(player, id, true); });
        }
        button(page, 48, Material.WRITABLE_BOOK, "New Equipment Set", List.of("Start with 2-piece and 4-piece bonuses."), ignored -> ask(player, false, null,
            "Enter a set ID, such as frostguard.", input -> beginSet(player, EditorPolicy.identifier(input), false)));
        button(page, 45, Material.ARROW, "Item Library", List.of(), ignored -> catalog(player, false, 0));
        pagination(page, index, ids.size(), next -> setCatalog(player, next)); open(player, page);
    }

    private void beginSet(Player player, String id, boolean editing) {
        if (!allowed(player, false)) return;
        Draft draft = new Draft(false, account(player));
        if (editing) { draft.set = items.findSet(id).orElseThrow(() -> new IllegalArgumentException("Only authored sets can be edited.")); draft.originalSet = draft.set; }
        else {
            if (items.knownSetIds().contains(id)) throw new IllegalArgumentException("That set ID is already in use.");
            draft.set = items.draftSet(id);
        }
        install(player, draft); showDraft(player, draft);
    }

    private void showSet(Player player, Draft draft) {
        ItemSetBlueprint set = draft.set; Page page = page(player, false, "Set • " + set.name(), draft);
        button(page, 4, Material.CHAINMAIL_CHESTPLATE, set.name(), setLines(set), ignored -> { });
        button(page, 10, Material.NAME_TAG, "Set Name", List.of(set.name(), "Click to edit."), ignored -> ask(player, false, draft, "Enter the equipment set's name.", text -> {
            draft.set = new ItemSetBlueprint(set.id(), EditorPolicy.text(text, 64), set.pieceCount(), set.bonuses()); draft.revision++; showDraft(player, draft);
        }));
        button(page, 13, Material.ARMOR_STAND, "Set Size", List.of(set.pieceCount() + " pieces", "Choose 2–6 pieces."), ignored -> ask(player, false, draft, "Enter the number of pieces (2–6). Bonuses above this count will be removed.", text -> {
            int count = EditorPolicy.integer(text, 2, 6); Map<Integer, ItemSetBlueprint.Bonus> bonuses = new LinkedHashMap<>(set.bonuses());
            bonuses.keySet().removeIf(threshold -> threshold > count);
            if (bonuses.isEmpty()) bonuses.put(2, new ItemSetBlueprint.Bonus("+1 Defense", Map.of("defense", 1D)));
            draft.set = new ItemSetBlueprint(set.id(), set.name(), count, bonuses); draft.revision++; showDraft(player, draft);
        }));
        for (int threshold = 2; threshold <= set.pieceCount(); threshold++) {
            int pieces = threshold; var bonus = set.bonuses().get(pieces);
            button(page, 17 + pieces, bonus == null ? Material.GRAY_DYE : Material.EMERALD,
                pieces + "-Piece Bonus", List.of(bonus == null ? "Not configured" : bonus.description(), "Click to edit this threshold."), ignored -> {
                    if (!draft.set.bonuses().containsKey(pieces)) {
                        Map<Integer, ItemSetBlueprint.Bonus> next = new LinkedHashMap<>(draft.set.bonuses());
                        next.put(pieces, new ItemSetBlueprint.Bonus("+1 Defense", Map.of("defense", 1D)));
                        replaceBonuses(draft, next);
                    }
                    setBonus(player, draft, pieces);
                });
        }
        button(page, 31, Material.SPYGLASS, "Set Preview", List.of("Inspect all piece thresholds and bonuses."), ignored -> preview(player, draft));
        button(page, 33, Material.LIME_DYE, "Review & Publish", List.of("Publish before assigning this set to items."), ignored -> confirm(player, draft, false));
        commonDraftButtons(page, player, draft); open(player, page);
    }

    private void setBonus(Player player, Draft draft, int pieces) {
        var bonus = draft.set.bonuses().get(pieces); Page page = page(player, false, pieces + "-Piece Bonus", draft);
        List<String> summary = new ArrayList<>(); summary.add(bonus.description());
        bonus.stats().forEach((id, amount) -> summary.add(readable(id) + ": " + number(amount) + (id.endsWith("percent") ? "%" : "")));
        button(page, 4, Material.EMERALD, pieces + "-Piece Bonus", summary, ignored -> { });
        button(page, 10, Material.WRITABLE_BOOK, "Bonus Description", List.of(bonus.description(), "Click to edit."), ignored -> ask(player, false, draft, "Describe this set bonus (up to 160 characters).", input -> {
            Map<Integer, ItemSetBlueprint.Bonus> next = new LinkedHashMap<>(draft.set.bonuses());
            next.put(pieces, new ItemSetBlueprint.Bonus(EditorPolicy.text(input, 160), bonus.stats()));
            replaceBonuses(draft, next); setBonus(player, draft, pieces);
        }));
        button(page, 12, Material.REDSTONE, "Bonus Stats", List.of("Choose the bonuses granted at this threshold.", "Only stats used by the set system are offered."), ignored -> setBonusStats(player, draft, pieces, 0));
        button(page, 14, Material.BARRIER, "Remove Threshold", List.of("At least one threshold must remain."), ignored -> {
            Map<Integer, ItemSetBlueprint.Bonus> next = new LinkedHashMap<>(draft.set.bonuses()); next.remove(pieces);
            replaceBonuses(draft, next); showDraft(player, draft);
        });
        button(page, 45, Material.ARROW, "Back to Set", List.of(), ignored -> showDraft(player, draft)); open(player, page);
    }

    private void setBonusStats(Player player, Draft draft, int pieces, int requested) {
        Page page = page(player, false, pieces + "-Piece Stats", draft);
        List<String> stats = ItemStats.setBonusAllowed().stream().sorted().toList();
        int index = EditorPolicy.page(requested, stats.size()); List<String> visible = EditorPolicy.entries(stats, index);
        for (int i = 0; i < visible.size(); i++) {
            String stat = visible.get(i); double current = draft.set.bonuses().get(pieces).stats().getOrDefault(stat, 0D);
            button(page, SLOTS[i], Material.REDSTONE, readable(stat), List.of("Value: " + number(current) + (stat.endsWith("percent") ? "%" : ""), "Click to enter a value. Zero removes this stat."), ignored -> ask(player, false, draft,
                "Enter " + readable(stat) + " (0–" + statMaximum(stat) + "). At least one stat must remain.", input -> {
                    var old = draft.set.bonuses().get(pieces); Map<String, Double> values = new LinkedHashMap<>(old.stats());
                    double amount = EditorPolicy.number(input, 0, statMaximum(stat));
                    if (amount == 0) values.remove(stat); else values.put(stat, amount);
                    Map<Integer, ItemSetBlueprint.Bonus> next = new LinkedHashMap<>(draft.set.bonuses());
                    next.put(pieces, new ItemSetBlueprint.Bonus(old.description(), values));
                    replaceBonuses(draft, next); setBonusStats(player, draft, pieces, index);
                }));
        }
        button(page, 45, Material.ARROW, "Back to Bonus", List.of(), ignored -> setBonus(player, draft, pieces));
        pagination(page, index, stats.size(), next -> setBonusStats(player, draft, pieces, next)); open(player, page);
    }

    private static void replaceBonuses(Draft draft, Map<Integer, ItemSetBlueprint.Bonus> bonuses) {
        ItemSetBlueprint old = draft.set; draft.set = new ItemSetBlueprint(old.id(), old.name(), old.pieceCount(), bonuses); draft.revision++;
    }

    private void showMob(Player player, Draft draft) {
        MobBlueprint mob = draft.encounter;
        Page page = page(player, true, "Encounter • " + mob.name(), draft);
        button(page, 4, mobIcon(mob), mob.name(), mobLines(mob), ignored -> { });
        mobField(page, 10, Material.NAME_TAG, "Name", mob.name(), player, draft, "Enter the encounter name.", text -> mob.withName(EditorPolicy.text(text, 64)));
        button(page, 11, mobIcon(mob), "Creature", List.of(readable(mob.type()), draft.originalMob == null ? "Click to choose a vanilla silhouette." : "Locked after publishing to protect live encounters."), ignored -> {
            if (draft.originalMob == null) mobTypes(player, draft, 0);
        });
        button(page, 12, Material.NETHER_STAR, "Rank", List.of(readable(mob.rank().name()), "Click to switch ordinary / elite."), ignored -> { draft.encounter = mob.withRank(mob.rank() == MobBlueprint.Rank.NORMAL ? MobBlueprint.Rank.ELITE : MobBlueprint.Rank.NORMAL); draft.revision++; showDraft(player, draft); });
        mobField(page, 13, Material.EXPERIENCE_BOTTLE, "Encounter Level", "" + mob.level(), player, draft, "Enter an encounter level from 1 to 100.", text -> mob.withLevel(EditorPolicy.integer(text, 1, 100)));
        mobField(page, 14, Material.APPLE, "Base Health", number(mob.health()), player, draft, "Enter base health (1–1024). Encounter level, rank, and combatants affect live health.", text -> mob.withHealth(EditorPolicy.number(text, 1, 1024)));
        mobField(page, 15, Material.IRON_SWORD, "Base Attack Damage", number(mob.attackDamage()), player, draft, "Enter base attack damage (0–100). Encounter scaling affects live damage.", text -> mob.withAttackDamage(EditorPolicy.number(text, 0, 100)));
        mobField(page, 16, Material.FEATHER, "Movement Speed", number(mob.movementSpeed()), player, draft, "Enter movement speed (0.05–0.8).", text -> mob.withMovementSpeed(EditorPolicy.number(text, 0.05, 0.8)));
        button(page, 19, Material.BLAZE_POWDER, "Ability", List.of(readable(mob.ability().name()), abilityDescription(mob.ability()), "Click to cycle abilities."), ignored -> {
            MobBlueprint.Ability[] values = MobBlueprint.Ability.values();
            draft.encounter = mob.withAbility(values[(mob.ability().ordinal() + 1) % values.length]); draft.revision++; showDraft(player, draft);
        });
        mobField(page, 20, Material.COMPASS, "Ability Radius", number(mob.abilityRadius()), player, draft, "Enter ability radius (1–12 blocks).", text -> mob.withAbilityRadius(EditorPolicy.number(text, 1, 12)));
        mobField(page, 21, Material.FIRE_CHARGE, "Ability Power", number(mob.abilityPower()), player, draft, "Enter ability power (0–100).", text -> mob.withAbilityPower(EditorPolicy.number(text, 0, 100)));
        mobField(page, 22, Material.CLOCK, "Ability Cooldown", mob.abilityCooldownSeconds() + " seconds", player, draft, "Enter ability cooldown (5–300 seconds).", text -> mob.withAbilityCooldownSeconds(EditorPolicy.integer(text, 5, 300)));
        mobField(page, 23, Material.EXPERIENCE_BOTTLE, "Defeat Experience", "" + mob.experienceReward(), player, draft, "Enter defeat experience (0–1000).", text -> mob.withExperienceReward(EditorPolicy.integer(text, 0, 1000)));
        button(page, 24, Material.CHEST, "Loot Profile", List.of(mob.lootProfile().isEmpty() ? "Default rewards" : mob.lootProfile(), "Click to choose a staff-authored loot profile."), ignored -> lootChoices(player, draft, 0));
        button(page, 31, Material.SPYGLASS, "Encounter Preview", List.of("Inspect its silhouette, stats, ability, and rewards.", "No live mob is spawned."), ignored -> preview(player, draft));
        button(page, 33, Material.LIME_DYE, "Review & Publish", List.of("Save it to the mob catalog.", "Place it with /zone spawn <zone> custom:" + mob.id() + " <level>."), ignored -> confirm(player, draft, false));
        commonDraftButtons(page, player, draft); open(player, page);
    }

    private void mobTypes(Player player, Draft draft, int requested) {
        Page page = page(player, true, "Choose a Creature", draft);
        List<EntityType> types = Arrays.stream(EntityType.values()).filter(ZoneMobTypes::allowed).sorted(java.util.Comparator.comparing(Enum::name)).toList();
        int index = EditorPolicy.page(requested, types.size()); List<EntityType> visible = EditorPolicy.entries(types, index);
        for (int i = 0; i < visible.size(); i++) {
            EntityType type = visible.get(i);
            button(page, SLOTS[i], spawnEgg(type), readable(type.name()), List.of("Click to use this creature."), ignored -> {
                draft.encounter = draft.encounter.withType(type.name().toLowerCase(Locale.ROOT)); draft.revision++; showDraft(player, draft);
            });
        }
        button(page, 45, Material.ARROW, "Back to Encounter", List.of(), ignored -> showDraft(player, draft));
        pagination(page, index, types.size(), next -> mobTypes(player, draft, next)); open(player, page);
    }

    private void lootChoices(Player player, Draft draft, int requested) {
        Page page = page(player, true, "Choose Loot Profile", draft);
        var profiles = plugin.rewardDrops().profiles().all(); int index = EditorPolicy.page(requested, profiles.size());
        var visible = EditorPolicy.entries(profiles, index);
        for (int i = 0; i < visible.size(); i++) {
            var profile = visible.get(i);
            button(page, SLOTS[i], Material.CHEST, readable(profile.id()), List.of(profile.id(), readable(profile.mode().name()), profile.entries().size() + " reward entries", "Click to use this profile."), ignored -> {
                draft.encounter = draft.encounter.withLootProfile(profile.id()); draft.revision++; showDraft(player, draft);
            });
        }
        button(page, 4, Material.BOOK, "Loot Profiles", List.of("Create reward tables with /mobloot.", "A zone's assigned profile takes priority."), ignored -> { });
        button(page, 49, Material.BARRIER, "Default Rewards", List.of("Remove this encounter's profile override."), ignored -> { draft.encounter = draft.encounter.withLootProfile(""); draft.revision++; showDraft(player, draft); });
        button(page, 45, Material.ARROW, "Back to Encounter", List.of(), ignored -> showDraft(player, draft));
        pagination(page, index, profiles.size(), next -> lootChoices(player, draft, next)); open(player, page);
    }

    private void preview(Player player, Draft draft) {
        Page page = page(player, draft.mob, draft.mob ? "Encounter Preview" : "Tooltip Preview", draft);
        if (draft.set != null) button(page, 22, Material.CHAINMAIL_CHESTPLATE, draft.set.name(), setLines(draft.set), ignored -> { });
        else if (draft.mob) {
            button(page, 22, mobIcon(draft.encounter), draft.encounter.name(), mobLines(draft.encounter), ignored -> { });
            button(page, 31, Material.BOOK, "Spawn Placement", List.of("Publish before placing this encounter.", "/zone spawn <zone> custom:" + draft.id() + " <level>", "Zone boundaries, caps, and respawn rules still apply."), ignored -> { });
        } else button(page, 22, items.preview(draft.item), ignored -> { });
        button(page, 45, Material.ARROW, "Back to Draft", List.of(), ignored -> showDraft(player, draft));
        button(page, 49, Material.LIME_DYE, "Review & Publish", List.of("This preview cannot be taken or used."), ignored -> confirm(player, draft, false));
        open(player, page);
    }

    private void confirm(Player player, Draft draft, boolean discard) {
        Page page = page(player, draft.mob, discard ? "Discard Draft?" : "Publish Content?", draft);
        button(page, 22, draft.set != null ? card(Material.CHAINMAIL_CHESTPLATE, draft.set.name(), setLines(draft.set))
            : draft.mob ? card(mobIcon(draft.encounter), draft.encounter.name(), mobLines(draft.encounter)) : items.preview(draft.item), ignored -> { });
        button(page, 30, Material.LIME_CONCRETE, discard ? "Discard Draft" : "Confirm Publish", List.of(discard ? "Published content is not changed." : "Writes the definition before making it live.", draft.id()), ignored -> {
            if (discard) { clear(player.getUniqueId()); catalog(player, draft.mob, 0); }
            else publish(player, draft);
        });
        button(page, 32, Material.RED_CONCRETE, "Keep Editing", List.of(), ignored -> showDraft(player, draft));
        open(player, page);
    }

    private void publish(Player player, Draft draft) {
        if (!validDraft(player, draft)) return;
        try {
            if (draft.set != null) items.publishSet(draft.set, draft.originalSet);
            else if (draft.mob) mobs.publish(draft.encounter, draft.mobRevision);
            else items.publish(draft.item, draft.originalItem);
            Chat.success(player, "Published <white>" + Chat.escape(draft.id()) + "</white>.");
            if (draft.mob) Chat.info(player, "Place it with <white>" + Chat.escape("/zone spawn <zone> custom:" + draft.id() + " <level>") + "</white>.");
            clear(player.getUniqueId()); catalog(player, draft.mob, 0);
        } catch (IOException failure) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Content editor could not publish " + draft.id() + "; draft retained", failure);
            Chat.error(player, "The definition could not be saved. Your draft is still open; published content was not changed.");
            showDraft(player, draft);
        }
    }

    private void commonDraftButtons(Page page, Player player, Draft draft) {
        button(page, 45, Material.ARROW, "Library", List.of("Your draft stays open while browsing."), ignored -> catalog(player, draft.mob, 0));
        button(page, 49, Material.BARRIER, "Discard Draft", List.of("Ask for confirmation before discarding."), ignored -> confirm(player, draft, true));
        button(page, 53, Material.WRITABLE_BOOK, "Copy Draft", List.of("Start a new item or encounter from this draft."), ignored -> ask(player, draft.mob, draft,
            "Enter a new content ID for the copy.", value -> {
                String id = EditorPolicy.identifier(value);
                if (draft.mob ? plugin.mobs().ids().contains(id) : items.exists(id)) throw new IllegalArgumentException("That content ID is already in use.");
                Draft next = new Draft(draft.mob, account(player));
                if (draft.set != null) {
                    if (items.knownSetIds().contains(id)) throw new IllegalArgumentException("That equipment set ID is already in use.");
                    next.set = new ItemSetBlueprint(id, draft.set.name(), draft.set.pieceCount(), draft.set.bonuses());
                }
                else if (draft.mob) { next.encounter = draft.encounter.renamed(id); next.mobRevision = mobs.revision(); }
                else { var old = draft.item; next.item = new ItemBlueprint(id, old.name(), old.material(), old.rarity(), old.sockets(), old.itemLevel(), old.setId(), old.lore(), old.stats()); }
                install(player, next); showDraft(player, next);
            }));
    }

    private void field(Page page, int slot, Material material, String label, String value, Player player, Draft draft, String question, Consumer<String> setter) {
        button(page, slot, material, label, List.of(value, "Click to edit."), ignored -> ask(player, false, draft, question, text -> { setter.accept(text); showDraft(player, draft); }));
    }

    private void mobField(Page page, int slot, Material material, String label, String value, Player player, Draft draft, String question, java.util.function.Function<String, MobBlueprint> setter) {
        button(page, slot, material, label, List.of(value, "Click to edit."), ignored -> ask(player, true, draft, question, text -> {
            MobBlueprint candidate = setter.apply(text); draft.encounter = candidate; draft.revision++; showDraft(player, draft);
        }));
    }

    private void ask(Player player, boolean mob, Draft draft, String question, Consumer<String> accepted) {
        if (!allowed(player, mob) || draft != null && !validDraft(player, draft)) return;
        if (!prompts.containsKey(player.getUniqueId()) && prompts.size() >= EditorPolicy.MAX_EDITORS) throw new IllegalStateException("The editor is busy. Try again shortly.");
        Draft context = draft == null ? drafts.get(player.getUniqueId()) : draft;
        Prompt prompt = new Prompt(player, account(player), mob, context, context == null ? 0 : context.revision,
            System.currentTimeMillis() + EditorPolicy.PROMPT_MILLIS, accepted, new java.util.concurrent.atomic.AtomicBoolean());
        prompts.put(player.getUniqueId(), prompt); views.remove(player.getUniqueId()); player.closeInventory();
        Chat.notice(player, Chat.escape(question));
        Chat.info(player, "Type your answer in chat. Only you can see it. Type <white>cancel</white> to return; this expires in 60 seconds.");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId(); Prompt prompt = prompts.get(id);
        if (prompt == null) return;
        event.setCancelled(true);
        if (!prompt.claimed.compareAndSet(false, true)) return;
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = prompt.player;
            if (!prompts.remove(id, prompt)) return;
            if (closed || !allowed(player, prompt.mob) || !sameSession(player, prompt.account)) { clear(id); return; }
            Draft current = drafts.get(id);
            long revision = current == null ? 0 : current.revision;
            if (current != prompt.draft || !EditorPolicy.promptCurrent(prompt.expiresAt, System.currentTimeMillis(), prompt.revision, revision)) {
                Chat.notice(player, "That input expired or belongs to an older draft. Open the editor again."); return;
            }
            if (text.strip().equalsIgnoreCase("cancel")) { if (current != null) showDraft(player, current); else catalog(player, prompt.mob, 0); return; }
            try { prompt.accepted.accept(EditorPolicy.text(text, 1288)); }
            catch (IllegalArgumentException | IllegalStateException failure) {
                fail(player, failure.getMessage());
                if (current != null) showDraft(player, current); else catalog(player, prompt.mob, 0);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Page page)) return;
        boolean previouslyCancelled = event.isCancelled(); event.setCancelled(true);
        if (previouslyCancelled || !(event.getWhoClicked() instanceof Player player)) return;
        if (!current(player, page)) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= page.inventory.getSize()) return;
        Consumer<Player> action = page.actions.get(event.getRawSlot()); if (action == null || page.pending) return;
        page.pending = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!current(player, page)) return;
            page.pending = false;
            try { action.accept(player); }
            catch (IllegalArgumentException | IllegalStateException failure) { fail(player, failure.getMessage()); }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Page) event.setCancelled(true); }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (closed) return;
        if (!(event.getInventory().getHolder() instanceof Page page) || !(event.getPlayer() instanceof Player player)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (closed || !player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player || !sameSession(player, page.account)) return;
            if (!prompts.containsKey(player.getUniqueId()) && !(player.getOpenInventory().getTopInventory().getHolder() instanceof Page)) clear(player.getUniqueId());
        });
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { clear(event.getPlayer().getUniqueId()); }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void endPromptOnCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
    }

    private void expirePrompts() {
        long now = System.currentTimeMillis();
        prompts.forEach((id, prompt) -> {
            if (now >= prompt.expiresAt && prompts.remove(id, prompt)) {
                clear(id);
                if (prompt.player.isOnline() && Bukkit.getPlayer(id) == prompt.player) Chat.notice(prompt.player, "Editor input expired. Published content was not changed.");
            }
        });
    }

    private boolean current(Player player, Page page) {
        Draft draft = page.draft; long revision = draft == null ? 0 : draft.revision;
        return !closed && player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player
            && player.getOpenInventory().getTopInventory() == page.inventory && sameSession(player, page.account)
            && EditorPolicy.current(page.owner, player.getUniqueId(), page.token, views.get(player.getUniqueId()), page.revision, revision, player.hasPermission(permission(page.mob)))
            && (draft == null || drafts.get(player.getUniqueId()) == draft);
    }

    private boolean validDraft(Player player, Draft draft) {
        return allowed(player, draft.mob) && sameSession(player, draft.account) && drafts.get(player.getUniqueId()) == draft;
    }

    private boolean allowed(Player player, boolean mob) {
        if (closed || !player.hasPermission(permission(mob))) { Chat.error(player, "You do not have permission to edit this content."); return false; }
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player && session(player);
    }

    private boolean session(Player player) { return plugin.sessions().account(player.getUniqueId()).isPresent(); }
    private AccountId account(Player player) { return plugin.sessions().account(player.getUniqueId()).orElseThrow(() -> new IllegalStateException("Your profile is still loading.")); }
    private boolean sameSession(Player player, AccountId account) { return plugin.sessions().account(player.getUniqueId()).filter(account::equals).isPresent(); }
    private static String permission(boolean mob) { return mob ? MOBS_PERMISSION : ITEMS_PERMISSION; }

    private Page page(Player player, boolean mob, String title, Draft draft) {
        if (!views.containsKey(player.getUniqueId()) && views.size() >= EditorPolicy.MAX_EDITORS) throw new IllegalStateException("The editor is busy. Try again shortly.");
        Page page = new Page(player.getUniqueId(), account(player), mob, draft);
        page.inventory = Bukkit.createInventory(page, 54, Component.text(title.length() > 60 ? title.substring(0, 60) : title, NamedTextColor.DARK_PURPLE));
        ItemStack border = card(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < 54; slot++) if (slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8) page.inventory.setItem(slot, border);
        return page;
    }

    private void open(Player player, Page page) { views.put(player.getUniqueId(), page.token); player.openInventory(page.inventory); }
    private static void button(Page page, int slot, ItemStack icon, Consumer<Player> action) { page.inventory.setItem(slot, icon.clone()); page.actions.put(slot, action); }
    private static void button(Page page, int slot, Material icon, String name, List<String> lore, Consumer<Player> action) { button(page, slot, card(icon, name, lore), action); }
    private static void pagination(Page page, int index, int count, Consumer<Integer> render) {
        if (index > 0) button(page, 46, Material.ARROW, "Previous Page", List.of(), ignored -> render.accept(index - 1));
        button(page, 47, Material.PAPER, "Page " + (index + 1) + " / " + (EditorPolicy.page(Integer.MAX_VALUE, count) + 1), List.of(count + " entries"), ignored -> { });
        if ((index + 1) * EditorPolicy.PAGE_SIZE < count) button(page, 52, Material.ARROW, "Next Page", List.of(), ignored -> render.accept(index + 1));
    }

    private static ItemStack card(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.values()); item.setItemMeta(meta); return item;
    }

    private static ItemStack inert(ItemStack source) {
        ItemStack result = source.clone(); result.setAmount(1); ItemMeta meta = result.getItemMeta();
        for (var key : meta.getPersistentDataContainer().getKeys()) meta.getPersistentDataContainer().remove(key);
        meta.addItemFlags(ItemFlag.values()); result.setItemMeta(meta); return result;
    }

    private static void append(ItemStack item, List<String> lines) {
        ItemMeta meta = item.getItemMeta(); List<Component> lore = new ArrayList<>();
        if (meta.lore() != null) lore.addAll(meta.lore());
        lines.forEach(line -> lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.lore(lore); item.setItemMeta(meta);
    }

    private static double statMaximum(String stat) { return List.of("damage", "projectile-damage", "defense", "damage-flat").contains(stat) ? 1000 : 100; }
    private static Material mobIcon(MobBlueprint blueprint) { return spawnEgg(blueprint.entityType()); }
    private static Material spawnEgg(EntityType type) { Material icon = Material.matchMaterial(type.name() + "_SPAWN_EGG"); return icon == null ? Material.ZOMBIE_HEAD : icon; }
    private static String number(double value) { return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", ""); }
    private static String readable(String raw) { return Arrays.stream(raw.toLowerCase(Locale.ROOT).replace('-', '_').split("_")).filter(part -> !part.isBlank()).map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1)).collect(java.util.stream.Collectors.joining(" ")); }
    private static List<String> mobLines(MobBlueprint mob) { return List.of("Level " + mob.level() + " • " + readable(mob.rank().name()), readable(mob.type()), "Base health: " + number(mob.health()) + " • Base damage: " + number(mob.attackDamage()), "Level, rank, and combatants affect live stats.", "Speed: " + number(mob.movementSpeed()), "Ability: " + readable(mob.ability().name()), abilityDescription(mob.ability()), "Radius " + number(mob.abilityRadius()) + " • Power " + number(mob.abilityPower()), "Cooldown: " + mob.abilityCooldownSeconds() + " seconds", "Defeat experience: " + mob.experienceReward(), "Loot: " + (mob.lootProfile().isEmpty() ? "default rewards" : mob.lootProfile())); }
    private static List<String> setLines(ItemSetBlueprint set) {
        List<String> lines = new ArrayList<>(); lines.add(set.pieceCount() + "-piece set");
        set.bonuses().forEach((count, bonus) -> { lines.add(count + " pieces: " + bonus.description()); bonus.stats().forEach((id, value) -> lines.add("  " + readable(id) + ": " + number(value) + (id.endsWith("percent") ? "%" : ""))); });
        return List.copyOf(lines);
    }
    private static String abilityDescription(MobBlueprint.Ability ability) { return switch (ability) { case NONE -> "Ordinary attacks; no special ability."; case PULSE -> "Telegraphed area strike; move out of its radius."; case HEAL_SELF -> "Periodically restores its own health."; }; }
    private static void fail(CommandSender sender, String message) { Chat.error(sender, Chat.escape(message == null ? "The edit could not be completed." : message)); }
    private void clear(UUID id) { drafts.remove(id); prompts.remove(id); views.remove(id); }

    @Override public void close() {
        closed = true; expiry.cancel();
        for (Player player : Bukkit.getOnlinePlayers()) if (player.getOpenInventory().getTopInventory().getHolder() instanceof Page) player.closeInventory();
        drafts.clear(); prompts.clear(); views.clear();
    }

    private static final class Draft {
        private final boolean mob; private final AccountId account;
        private ItemBlueprint item, originalItem;
        private ItemSetBlueprint set, originalSet;
        private MobBlueprint encounter, originalMob;
        private long revision, mobRevision;
        private Draft(boolean mob, AccountId account) { this.mob = mob; this.account = account; }
        private String id() { return set != null ? set.id() : mob ? encounter.id() : item.id(); }
    }
    private record Prompt(Player player, AccountId account, boolean mob, Draft draft, long revision, long expiresAt,
                          Consumer<String> accepted, java.util.concurrent.atomic.AtomicBoolean claimed) { }
    private static final class Page implements InventoryHolder {
        private final UUID owner, token = UUID.randomUUID(); private final AccountId account; private final boolean mob;
        private final Draft draft; private final long revision;
        private final Map<Integer, Consumer<Player>> actions = new HashMap<>();
        private Inventory inventory; private boolean pending;
        private Page(UUID owner, AccountId account, boolean mob, Draft draft) { this.owner = owner; this.account = account; this.mob = mob; this.draft = draft; this.revision = draft == null ? 0 : draft.revision; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
