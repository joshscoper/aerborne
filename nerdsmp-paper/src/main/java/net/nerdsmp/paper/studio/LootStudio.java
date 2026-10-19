package net.nerdsmp.paper.studio;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.loot.profile.LootProfileDefinition;
import net.nerdsmp.paper.loot.profile.LootProfileStore;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

import static net.nerdsmp.paper.loot.profile.LootProfileDefinition.*;
import static net.nerdsmp.paper.studio.StudioUi.card;

/** Direct-publish, account-bound loot drafts. All previews are inert inventory illustrations. */
public final class LootStudio implements Listener, AutoCloseable {
    public static final String PERMISSION = "nerdsmp.loot.admin";
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private final NerdSmpPlugin plugin;
    private final StudioUi ui;
    private final LootProfileStore store;
    private final Map<UUID, Draft> drafts = new HashMap<>();
    private boolean closed;

    public LootStudio(NerdSmpPlugin plugin, StudioUi ui) {
        this.plugin = Objects.requireNonNull(plugin); this.ui = Objects.requireNonNull(ui);
        this.store = plugin.rewardDrops().profiles();
    }

    public void open(Player player) { catalog(player, 0); }

    public boolean command(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) { Chat.error(sender, "You do not have permission to edit loot."); return true; }
        if (!(sender instanceof Player player)) { Chat.error(sender, "Open " + Chat.command("lootstudio") + " in game. Use " + Chat.command("mobloot") + " for console editing."); return true; }
        run(player, () -> {
            if (args.length == 0 || args.length == 1 && args[0].equalsIgnoreCase("browse")) open(player);
            else if (args.length == 2 && args[0].equalsIgnoreCase("create")) begin(player, LootStudioPolicy.create(args[1]), null);
            else if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
                var profile = profile(args[1]); begin(player, profile, profile);
            } else if (args.length == 3 && args[0].equalsIgnoreCase("copy")) {
                begin(player, LootStudioPolicy.copy(args[2], profile(args[1])), null);
            } else if (args.length == 1 && args[0].equalsIgnoreCase("resume")) draft(player, requireDraft(player), 0);
            else if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) discard(player, requireDraft(player));
            else Chat.usage(player, "/lootstudio [browse|create <id>|edit <id>|copy <source> <id>|resume|cancel]");
        });
        return true;
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION) || args.length == 0) return List.of();
        List<String> choices = args.length == 1 ? List.of("browse", "create", "edit", "copy", "resume", "cancel")
            : args.length == 2 && (args[0].equalsIgnoreCase("edit") || args[0].equalsIgnoreCase("copy"))
                ? store.all().stream().map(LootProfileDefinition::id).toList() : List.of();
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(value -> value.startsWith(prefix)).limit(128).toList();
    }

    private void catalog(Player player, int requested) {
        if (!allowed(player)) return;
        List<LootProfileDefinition> all = store.all(), visible = LootStudioPolicy.entries(all, requested);
        int page = LootStudioPolicy.page(requested, all.size());
        List<StudioUi.Button> buttons = new ArrayList<>();
        for (int i = 0; i < visible.size(); i++) {
            var profile = visible.get(i);
            buttons.add(button(SLOTS[i], Material.CHEST, profile.id(), List.of(modeName(profile.mode()), profile.entries().size() + " rewards", "Click to edit a private draft."), p -> {
                var current = profile(profile.id()); begin(p, current, current);
            }));
        }
        buttons.add(button(45, Material.ARROW, "Content Studio", List.of(), ui::home));
        paginate(buttons, page, all.size(), p -> catalog(player, p));
        buttons.add(button(49, Material.WRITABLE_BOOK, "New Loot Profile", List.of("Choose an ID, then add rewards.", "Nothing changes until you publish."), p ->
            ui.ask(p, PERMISSION, "Enter a loot profile ID (lowercase, underscores allowed):", id -> begin(p, LootStudioPolicy.create(id.trim()), null))));
        Draft active = drafts.get(player.getUniqueId());
        if (active != null && valid(player, active)) buttons.add(button(50, Material.BOOK, "Resume " + active.value.id(), List.of("Continue your unpublished draft."), p -> draft(p, active, 0)));
        ui.open(player, PERMISSION, "Loot Library", buttons);
    }

    private void begin(Player player, LootProfileDefinition next, LootProfileDefinition expected) {
        if (!allowed(player)) return;
        if (expected == null && store.find(next.id()).isPresent()) throw new IllegalArgumentException("That profile ID is already published. Edit it or choose another ID.");
        Draft old = drafts.get(player.getUniqueId());
        Consumer<Player> install = p -> run(p, () -> {
            if (old != null && drafts.get(p.getUniqueId()) != old) throw new IllegalStateException("Your active draft changed. Reopen the editor.");
            if (!drafts.containsKey(p.getUniqueId()) && drafts.size() >= LootStudioPolicy.MAX_EDITORS) throw new IllegalStateException("The loot editor is busy. Try again shortly.");
            var value = new Draft(p, account(p), next, expected); drafts.put(p.getUniqueId(), value); draft(p, value, 0);
        });
        if (old != null && valid(player, old) && !Objects.equals(old.value, old.expected))
            ui.confirm(player, PERMISSION, "Replace Unpublished Draft?", List.of("Your draft for " + old.value.id() + " will be discarded.", "Published content is not affected."), install);
        else install.accept(player);
    }

    private void draft(Player player, Draft value, int requested) {
        if (!valid(player, value)) return;
        List<Entry> visible = LootStudioPolicy.entries(value.value.entries(), requested);
        int page = LootStudioPolicy.page(requested, value.value.entries().size());
        List<StudioUi.Button> buttons = new ArrayList<>();
        for (int i = 0; i < visible.size(); i++) {
            Entry entry = visible.get(i);
            buttons.add(button(SLOTS[i], icon(entry), entry.id(), describe(entry), p -> reward(p, value, entry.id())));
        }
        buttons.add(button(45, Material.ARROW, "Loot Library", List.of("Your draft is kept while you browse."), p -> catalog(p, 0)));
        paginate(buttons, page, value.value.entries().size(), p -> draft(player, value, p));
        buttons.add(button(48, Material.EMERALD, "Add Reward", List.of("Vanilla item, authored item or coin purse."), p -> newReward(p, value)));
        buttons.add(button(49, Material.SPYGLASS, "Balance Preview", List.of("Expected rewards and worst-case limits.", "This never awards items or Marks."), p -> summary(p, value)));
        buttons.add(button(50, Material.LIME_CONCRETE, "Publish", List.of("Save this draft to the live loot profile.", modeName(value.value.mode()), "Requires confirmation."), p -> publish(p, value)));
        buttons.add(button(51, Material.COMPARATOR, "Profile Settings", List.of("Mode, copy, assignments and deletion."), p -> settings(p, value)));
        ui.open(player, PERMISSION, "Loot Draft: " + value.value.id(), buttons);
    }

    private void settings(Player player, Draft value) {
        if (!valid(player, value)) return;
        List<StudioUi.Button> buttons = new ArrayList<>();
        buttons.add(button(20, Material.COMPARATOR, modeName(value.value.mode()), List.of("Additive keeps normal rewards.", "Replace suppresses normal item drops,", "gear rolls, purses and mob item rewards.", "XP and boss participation rewards remain."), p -> {
            Mode next = value.value.mode() == Mode.ADDITIVE ? Mode.REPLACE : Mode.ADDITIVE;
            long revision = value.revision;
            if (next == Mode.REPLACE) ui.confirm(p, PERMISSION, "Use Replacement Loot?", List.of("Only this profile's item rewards will roll.", "An empty profile will remove normal item loot.", "This changes the draft; publish to apply it."), actor -> {
                if (same(actor, value, revision)) { update(value, LootStudioPolicy.mode(value.value, next)); settings(actor, value); }
            });
            else { update(value, LootStudioPolicy.mode(value.value, next)); settings(p, value); }
        }));
        buttons.add(button(22, Material.WRITABLE_BOOK, "Copy Profile", List.of("Start a new draft with these rewards.", "Assignments are not copied."), p -> {
            long revision = value.revision;
            ui.ask(p, PERMISSION, "Enter the ID for the copied profile:", id -> {
                if (same(p, value, revision)) begin(p, LootStudioPolicy.copy(id.trim(), value.value), null);
            });
        }));
        buttons.add(button(24, Material.LEAD, "Assignments", List.of("Bind the published profile to zones or mobs.", "Zones take priority over mob profiles."), p -> assignments(p, value, 0)));
        buttons.add(button(30, Material.BARRIER, "Delete Published Profile", List.of("Requires confirmation and no active references.", "Your draft is not published by this action."), p -> delete(p, value)));
        buttons.add(button(32, Material.RED_CONCRETE, "Discard Draft", List.of("Published content remains unchanged."), p -> discard(p, value)));
        buttons.add(button(45, Material.ARROW, "Back to Rewards", List.of(), p -> draft(p, value, 0)));
        ui.open(player, PERMISSION, "Loot Profile Settings", buttons);
    }

    private void newReward(Player player, Draft value) {
        if (!valid(player, value)) return;
        ui.open(player, PERMISSION, "Choose Reward Type", List.of(
            button(20, Material.IRON_INGOT, "Vanilla Item", List.of("Use Minecraft items and materials."), p -> rewardId(p, value, Kind.MATERIAL)),
            button(22, Material.DIAMOND_SWORD, "Item Library", List.of("Published equipment and authored materials.", "Gear supports affixes and unidentified drops."), p -> rewardId(p, value, Kind.ITEM)),
            button(24, Material.GOLD_NUGGET, "Coin Purse", List.of("Marks paid only on player pickup.", "Hoppers cannot collect these purses."), p -> rewardId(p, value, Kind.MARKS)),
            button(45, Material.ARROW, "Back to Rewards", List.of(), p -> draft(p, value, 0))));
    }

    private void rewardId(Player player, Draft value, Kind kind) {
        long revision = value.revision;
        ui.ask(player, PERMISSION, "Enter a unique reward ID, such as iron_scraps or coins:", input -> {
            if (!same(player, value, revision)) return;
            String id = identifier(input.trim());
            if (value.value.entries().stream().anyMatch(entry -> entry.id().equals(id))) throw new IllegalArgumentException("That reward ID is already used in this draft.");
            if (kind == Kind.MARKS) {
                update(value, LootStudioPolicy.entry(value.value, new Entry(id, kind, "marks", .25, 1, 4, false, 0), false)); reward(player, value, id);
            } else sources(player, value, kind, "", 0, source -> {
                if (!same(player, value, revision)) return;
                update(value, LootStudioPolicy.entry(value.value, new Entry(id, kind, source, .1, 1, 1, false, 0), false)); reward(player, value, id);
            });
        });
    }

    private void sources(Player player, Draft value, Kind kind, String filter, int requested, Consumer<String> chosen) {
        if (!valid(player, value)) return;
        List<String> all = kind == Kind.ITEM ? store.knownItems().stream().map(ItemId::value).sorted().toList()
            : Arrays.stream(Material.values()).filter(material -> material.isItem() && !material.isAir() && !material.name().startsWith("LEGACY_"))
                .map(Material::name).sorted().toList();
        String search = filter.toLowerCase(Locale.ROOT);
        all = all.stream().filter(source -> source.toLowerCase(Locale.ROOT).contains(search)).toList();
        int page = LootStudioPolicy.page(requested, all.size());
        List<String> visible = LootStudioPolicy.entries(all, page);
        List<StudioUi.Button> buttons = new ArrayList<>();
        for (int i = 0; i < visible.size(); i++) {
            String source = visible.get(i);
            boolean gear = kind == Kind.ITEM && store.gearItems().contains(new ItemId(source));
            Material icon = kind == Kind.ITEM ? gear ? Material.IRON_SWORD : Material.AMETHYST_SHARD : Material.valueOf(source);
            buttons.add(button(SLOTS[i], icon, source, List.of(kind == Kind.ITEM ? gear ? "Equipment: affixes and identification available." : "Published item: normal rewards only." : "Vanilla item.", "Choose this reward source."), p -> chosen.accept(source)));
        }
        buttons.add(button(45, Material.ARROW, "Back to Rewards", List.of(), p -> draft(p, value, 0)));
        paginate(buttons, page, all.size(), p -> sources(player, value, kind, filter, p, chosen));
        buttons.add(button(49, Material.SPYGLASS, "Search Sources", List.of("Current search: " + (filter.isEmpty() ? "all" : filter), "Enter part of an item ID or material name.", "Enter * to show all."), p -> {
            long revision = value.revision;
            ui.ask(p, PERMISSION, "Search item sources (or * for all):", text -> {
                if (same(p, value, revision)) sources(p, value, kind, text.trim().equals("*") ? "" : text.trim(), 0, chosen);
            });
        }));
        ui.open(player, PERMISSION, kind == Kind.ITEM ? "Item Reward Sources" : "Vanilla Reward Sources", buttons);
    }

    private void reward(Player player, Draft value, String entryId) {
        if (!valid(player, value)) return;
        Entry entry = entry(value, entryId);
        List<StudioUi.Button> buttons = new ArrayList<>();
        buttons.add(button(13, icon(entry), entry.id(), describe(entry), p -> { }));
        buttons.add(button(20, Material.SPYGLASS, "Chance: " + percent(entry.chance()), List.of("One independent roll per defeated mob.", "Enter a percentage between 0 and 100."), p -> promptEntry(p, value, entry, "Enter drop chance as a percentage, such as 12.5:", text -> LootStudioPolicy.chance(entry, text))));
        buttons.add(button(22, Material.CHEST, "Quantity: " + quantity(entry), List.of("Choose one amount or an inclusive range.", "Enter 1 4 to award between one and four."), p -> promptEntry(p, value, entry, "Enter quantity, or minimum and maximum (example: 1 4):", text -> LootStudioPolicy.quantity(entry, text))));
        if (entry.kind() != Kind.MARKS) buttons.add(button(24, Material.ITEM_FRAME, "Change Source", List.of(entry.source(), "Reward kind remains unchanged."), p -> {
            long revision = value.revision;
            sources(p, value, entry.kind(), "", 0, source -> {
                if (!same(p, value, revision)) return;
                update(value, LootStudioPolicy.entry(value.value, LootStudioPolicy.source(entry, source), true)); reward(p, value, entryId);
            });
        }));
        if (entry.kind() == Kind.ITEM && store.gearItems().contains(new ItemId(entry.source()))) {
            buttons.add(button(29, Material.ENDER_EYE, "Unidentified: " + (entry.unidentified() ? "Yes" : "No"), List.of("Unidentified gear must be taken to an identifier."), p -> {
                update(value, LootStudioPolicy.entry(value.value, LootStudioPolicy.unidentified(entry), true)); reward(p, value, entryId);
            }));
            buttons.add(button(31, Material.AMETHYST_SHARD, "Affixes: " + entry.affixes(), List.of("Click to cycle through zero to three.", "Each dropped item receives its own roll."), p -> {
                update(value, LootStudioPolicy.entry(value.value, LootStudioPolicy.affixes(entry), true)); reward(p, value, entryId);
            }));
        }
        buttons.add(button(33, Material.BARRIER, "Remove Reward", List.of("Remove this entry from the draft.", "Publish to apply the change."), p -> {
            long revision = value.revision;
            ui.confirm(p, PERMISSION, "Remove " + entry.id() + "?", List.of("This removes only this draft reward."), actor -> {
                if (same(actor, value, revision)) { update(value, LootStudioPolicy.remove(value.value, entryId)); draft(actor, value, 0); }
            });
        }));
        buttons.add(button(45, Material.ARROW, "Back to Rewards", List.of(), p -> draft(p, value, 0)));
        ui.open(player, PERMISSION, "Reward: " + entry.id(), buttons);
    }

    private void promptEntry(Player player, Draft value, Entry entry, String question, java.util.function.Function<String, Entry> next) {
        long revision = value.revision;
        ui.ask(player, PERMISSION, question, text -> {
            if (!same(player, value, revision)) return;
            update(value, LootStudioPolicy.entry(value.value, next.apply(text), true)); reward(player, value, entry.id());
        });
    }

    private void summary(Player player, Draft value) {
        if (!valid(player, value)) return;
        var stats = LootStudioPolicy.summary(value.value);
        List<String> lore = List.of("Independent rewards: " + stats.rewards(), "Average item quantity per kill: " + decimal(stats.expectedItemQuantity()),
            "Average Marks in purses per kill: " + decimal(stats.expectedMarks()), "Maximum items per kill: " + stats.maximumItems() + " / 256",
            "Maximum purse Marks per kill: " + stats.maximumMarks() + " / 10,000", modeName(value.value.mode()),
            "Averages describe this profile only.", "Additive mode may award normal loot as well.", "No preview items or Marks are granted.");
        ui.open(player, PERMISSION, "Loot Balance Preview", List.of(button(22, Material.BOOK, value.value.id(), lore, p -> { }),
            button(45, Material.ARROW, "Back to Rewards", List.of(), p -> draft(p, value, 0))));
    }

    private void publish(Player player, Draft value) {
        if (!valid(player, value)) return;
        long revision = value.revision;
        LootProfileDefinition snapshot = value.value;
        var summary = LootStudioPolicy.summary(snapshot);
        ui.confirm(player, PERMISSION, "Publish " + snapshot.id() + "?", List.of(modeName(snapshot.mode()), snapshot.entries().size() + " rewards",
            "Maximum purses: " + summary.maximumMarks() + " Marks per kill", "Active assignments use this profile immediately.",
            snapshot.mode() == Mode.REPLACE ? "Normal item rewards will be suppressed." : "Normal rewards will remain."), actor -> run(actor, () -> {
            if (!same(actor, value, revision)) return;
            synchronized (store) {
                LootStudioPolicy.expected(value.expected, store.find(snapshot.id()).orElse(null));
                LootStudioPolicy.validateCatalog(snapshot, store.knownItems(), store.gearItems());
                store.put(snapshot);
            }
            value.expected = snapshot; value.revision++;
            Chat.success(actor, "Published loot profile <white>" + Chat.escape(snapshot.id()) + "</white>."); draft(actor, value, 0);
        }));
    }

    private void delete(Player player, Draft value) {
        if (!valid(player, value)) return;
        if (value.expected == null) throw new IllegalArgumentException("This profile has not been published. Use Discard Draft instead.");
        long revision = value.revision;
        ui.confirm(player, PERMISSION, "Delete " + value.value.id() + "?", List.of("This removes the published profile and your draft.", "Assigned or referenced profiles cannot be deleted.", "Player inventories are not changed."), actor -> run(actor, () -> {
            if (!same(actor, value, revision)) return;
            synchronized (store) {
                LootStudioPolicy.expected(value.expected, store.find(value.value.id()).orElse(null)); store.remove(value.value.id());
            }
            drafts.remove(actor.getUniqueId(), value); Chat.success(actor, "Deleted loot profile <white>" + Chat.escape(value.value.id()) + "</white>."); catalog(actor, 0);
        }));
    }

    private void discard(Player player, Draft value) {
        if (!valid(player, value)) return;
        long revision = value.revision;
        ui.confirm(player, PERMISSION, "Discard " + value.value.id() + "?", List.of("Published rewards and assignments remain unchanged."), actor -> {
            if (same(actor, value, revision)) { drafts.remove(actor.getUniqueId(), value); catalog(actor, 0); }
        });
    }

    private void assignments(Player player, Draft value, int requested) {
        if (!valid(player, value)) return;
        if (store.find(value.value.id()).isEmpty()) throw new IllegalArgumentException("Publish this profile before assigning it.");
        List<Binding> all = new ArrayList<>();
        store.zoneAssignments().forEach((target, id) -> { if (id.equals(value.value.id())) all.add(new Binding(true, target)); });
        store.mobAssignments().forEach((target, id) -> { if (id.equals(value.value.id())) all.add(new Binding(false, target)); });
        all.sort(java.util.Comparator.comparing(binding -> (binding.zone ? "zone:" : "mob:") + binding.target));
        int page = LootStudioPolicy.page(requested, all.size());
        List<Binding> visible = LootStudioPolicy.entries(all, page);
        List<StudioUi.Button> buttons = new ArrayList<>();
        for (int i = 0; i < visible.size(); i++) {
            Binding binding = visible.get(i);
            buttons.add(button(SLOTS[i], binding.zone ? Material.MAP : Material.ZOMBIE_HEAD, (binding.zone ? "Zone: " : "Mob: ") + binding.target,
                List.of("Click to remove this assignment.", "Requires confirmation."), p -> binding(p, value, binding, true)));
        }
        paginate(buttons, page, all.size(), p -> assignments(player, value, p));
        buttons.add(button(45, Material.ARROW, "Profile Settings", List.of(), p -> settings(p, value)));
        buttons.add(button(49, Material.MAP, "Assign to Zone", List.of("Every encounter in that zone uses this profile.", "Overrides mob profiles."), p -> targets(p, value, true, "", 0)));
        buttons.add(button(50, Material.ZOMBIE_HEAD, "Assign to Mob", List.of("Fallback when no zone profile applies.", "Vanilla or authored mob identities."), p -> targets(p, value, false, "", 0)));
        ui.open(player, PERMISSION, "Loot Assignments", buttons);
    }

    private void targets(Player player, Draft value, boolean zone, String filter, int requested) {
        if (!valid(player, value)) return;
        List<String> all;
        if (zone) all = plugin.zones() == null ? List.of() : plugin.zones().all().stream().map(net.nerdsmp.paper.zone.ZoneDefinition::id).toList();
        else {
            var ids = new ArrayList<String>(); plugin.mobs().ids().forEach(id -> ids.add("custom:" + id));
            Arrays.stream(EntityType.values()).filter(type -> type != EntityType.PLAYER && type != EntityType.ARMOR_STAND
                && type.getEntityClass() != null && LivingEntity.class.isAssignableFrom(type.getEntityClass()))
                .map(type -> "vanilla:" + type.name().toLowerCase(Locale.ROOT)).forEach(ids::add);
            all = ids.stream().sorted().toList();
        }
        String search = filter.toLowerCase(Locale.ROOT);
        all = all.stream().filter(id -> id.toLowerCase(Locale.ROOT).contains(search)).toList();
        int page = LootStudioPolicy.page(requested, all.size());
        List<String> visible = LootStudioPolicy.entries(all, page);
        List<StudioUi.Button> buttons = new ArrayList<>();
        Map<String, String> existing = zone ? store.zoneAssignments() : store.mobAssignments();
        for (int i = 0; i < visible.size(); i++) {
            String target = visible.get(i), normalized = target.startsWith("custom:") ? target.substring(7) : target;
            buttons.add(button(SLOTS[i], zone ? Material.MAP : Material.ZOMBIE_HEAD, target, List.of("Current profile: " + existing.getOrDefault(normalized, "none"),
                "Click to assign " + value.value.id() + "."), p -> binding(p, value, new Binding(zone, normalized), false)));
        }
        paginate(buttons, page, all.size(), p -> targets(player, value, zone, filter, p));
        buttons.add(button(45, Material.ARROW, "Assignments", List.of(), p -> assignments(p, value, 0)));
        buttons.add(button(49, Material.SPYGLASS, "Search Targets", List.of("Current search: " + (filter.isEmpty() ? "all" : filter)), p -> {
            long revision = value.revision;
            ui.ask(p, PERMISSION, "Search target IDs (or * for all):", text -> {
                if (same(p, value, revision)) targets(p, value, zone, text.trim().equals("*") ? "" : text.trim(), 0);
            });
        }));
        ui.open(player, PERMISSION, zone ? "Choose Zone" : "Choose Mob", buttons);
    }

    private void binding(Player player, Draft value, Binding target, boolean remove) {
        if (!valid(player, value)) return;
        LootProfileDefinition expected = profile(value.value.id());
        String old = (target.zone ? store.zoneAssignments() : store.mobAssignments()).get(target.target);
        if (remove && !expected.id().equals(old)) throw new IllegalStateException("That assignment no longer points to this profile. Reopen the assignments.");
        long revision = value.revision;
        ui.confirm(player, PERMISSION, (remove ? "Remove" : "Set") + " Loot Assignment?", List.of((target.zone ? "Zone: " : "Mob: ") + target.target,
            "Current profile: " + (old == null ? "none" : old), remove ? "Resume fallback/default loot rules." : "New profile: " + expected.id(),
            remove ? "A mob fallback may still apply." : modeName(expected.mode()), "Only published rewards are assigned."), actor -> run(actor, () -> {
            if (!same(actor, value, revision)) return;
            synchronized (store) {
                LootStudioPolicy.expected(expected, store.find(expected.id()).orElse(null));
                LootStudioPolicy.expectedBinding(old, (target.zone ? store.zoneAssignments() : store.mobAssignments()).get(target.target));
                if (!remove) validateTarget(target);
                if (target.zone) { if (remove) store.unassignZone(target.target); else store.assignZone(target.target, expected.id()); }
                else { if (remove) store.unassignMob(target.target); else store.assignMob(target.target, expected.id()); }
            }
            Chat.success(actor, (remove ? "Removed" : "Saved") + " loot assignment for <white>" + Chat.escape(target.target) + "</white>."); assignments(actor, value, 0);
        }));
    }

    private void validateTarget(Binding binding) {
        if (binding.zone) {
            if (plugin.zones() == null || plugin.zones().find(binding.target).isEmpty()) throw new IllegalArgumentException("That zone no longer exists.");
        } else if (!binding.target.startsWith("vanilla:") && !plugin.mobs().ids().contains(binding.target)) throw new IllegalArgumentException("That authored mob no longer exists.");
    }

    private void update(Draft value, LootProfileDefinition next) {
        LootStudioPolicy.validateCatalog(next, store.knownItems(), store.gearItems()); value.value = next; value.revision++;
    }

    private Entry entry(Draft draft, String id) { return draft.value.entries().stream().filter(entry -> entry.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("That reward no longer exists.")); }
    private LootProfileDefinition profile(String id) { return store.find(id).orElseThrow(() -> new IllegalArgumentException("No loot profile named " + id + ".")); }
    private Draft requireDraft(Player player) { Draft draft = drafts.get(player.getUniqueId()); if (draft == null || !valid(player, draft)) throw new IllegalArgumentException("There is no open loot draft to resume."); return draft; }
    private AccountId account(Player player) { return plugin.sessions().account(player.getUniqueId()).orElseThrow(() -> new IllegalStateException("Your profile is still loading.")); }
    private boolean allowed(Player player) { return !closed && player.hasPermission(PERMISSION) && player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player && plugin.sessions().account(player.getUniqueId()).isPresent(); }
    private boolean valid(Player player, Draft value) { return allowed(player) && value.player == player && drafts.get(player.getUniqueId()) == value && plugin.sessions().account(player.getUniqueId()).filter(value.account::equals).isPresent(); }
    private boolean same(Player player, Draft value, long revision) {
        if (!valid(player, value) || value.revision != revision) { Chat.notice(player, "This draft changed while the menu was open. Reopen it with " + Chat.command("lootstudio resume") + "."); return false; } return true;
    }

    private StudioUi.Button button(int slot, Material material, String title, List<String> lore, Consumer<Player> action) {
        return new StudioUi.Button(slot, card(material, title, lore), player -> run(player, () -> action.accept(player)));
    }

    private void paginate(List<StudioUi.Button> buttons, int page, int count, Consumer<Integer> render) {
        if (page > 0) buttons.add(button(46, Material.ARROW, "Previous Page", List.of(), p -> render.accept(page - 1)));
        buttons.add(button(47, Material.PAPER, "Page " + (page + 1) + " / " + (LootStudioPolicy.page(Integer.MAX_VALUE, count) + 1), List.of(count + " entries"), p -> { }));
        if ((page + 1) * LootStudioPolicy.PAGE_SIZE < count) buttons.add(button(52, Material.ARROW, "Next Page", List.of(), p -> render.accept(page + 1)));
    }

    @FunctionalInterface private interface Action { void run() throws IOException; }
    private void run(Player player, Action action) {
        if (!allowed(player)) { Chat.error(player, "Your editor session is unavailable or you no longer have permission."); return; }
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException error) { fail(player, error.getMessage()); }
        catch (IOException error) { plugin.getLogger().log(Level.SEVERE, "Loot Studio save failed; previous content remains active", error); Chat.error(player, "The loot edit could not be saved. Your draft is kept and the previous profile remains active; check the server log."); }
    }

    private static Material icon(Entry entry) { return switch (entry.kind()) { case MARKS -> Material.GOLD_NUGGET; case ITEM -> Material.DIAMOND_SWORD; case MATERIAL -> Material.valueOf(entry.source()); }; }
    private static List<String> describe(Entry entry) { return List.of("Source: " + entry.source(), "Chance: " + percent(entry.chance()), "Quantity: " + quantity(entry),
        "Unidentified: " + (entry.unidentified() ? "yes" : "no"), "Affixes: " + entry.affixes(), "Click to edit this reward."); }
    private static String quantity(Entry entry) { return entry.minimum() == entry.maximum() ? Integer.toString(entry.minimum()) : entry.minimum() + " - " + entry.maximum(); }
    private static String percent(double chance) { return decimal(chance * 100) + "%"; }
    private static String decimal(double value) { return String.format(Locale.ROOT, "%.2f", value); }
    private static String modeName(Mode mode) { return mode == Mode.ADDITIVE ? "Additive: normal rewards remain" : "Replace: profile item rewards only"; }
    private static void fail(CommandSender sender, String message) { Chat.error(sender, Chat.escape(message == null ? "That edit was not valid." : message)); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { drafts.remove(event.getPlayer().getUniqueId()); }
    @Override public void close() { closed = true; drafts.clear(); }
    private record Binding(boolean zone, String target) { }
    private static final class Draft {
        final Player player; final AccountId account;
        LootProfileDefinition value, expected; long revision;
        Draft(Player player, AccountId account, LootProfileDefinition value, LootProfileDefinition expected) { this.player = player; this.account = account; this.value = value; this.expected = expected; }
    }
}
