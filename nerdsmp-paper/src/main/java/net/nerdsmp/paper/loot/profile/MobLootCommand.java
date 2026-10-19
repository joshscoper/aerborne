package net.nerdsmp.paper.loot.profile;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/** Staff-owned loot editing. Preview commands never award or consume anything. */
public final class MobLootCommand {
    public static final String PERMISSION = "nerdsmp.loot.admin";
    private static final List<String> ACTIONS = List.of("list", "create", "show", "add", "set", "remove", "mode", "assign", "unassign", "bindings", "delete", "reload");
    private static final List<String> VANILLA_MOBS = Arrays.stream(EntityType.values()).filter(type -> type != EntityType.PLAYER && type != EntityType.ARMOR_STAND
        && type.getEntityClass() != null && LivingEntity.class.isAssignableFrom(type.getEntityClass()))
        .map(type -> "vanilla:" + type.name().toLowerCase(Locale.ROOT)).sorted().toList();
    interface Context {
        boolean zoneExists(String id);
        List<String> zoneIds();
        List<String> mobIds();
        List<String> materials();
        void saveFailed(IOException failure);
    }
    private final LootProfileStore store;
    private final Context context;

    public MobLootCommand(NerdSmpPlugin plugin) {
        this(plugin.rewardDrops().profiles(), new Context() {
            private final List<String> materials = Arrays.stream(Material.values()).filter(material -> material.isItem() && !material.isAir())
                .map(material -> material.name().toLowerCase(Locale.ROOT)).sorted().toList();
            public boolean zoneExists(String id) { return plugin.zones() != null && plugin.zones().find(id).isPresent(); }
            public List<String> zoneIds() { return plugin.zones() == null ? List.of() : plugin.zones().all().stream().map(net.nerdsmp.paper.zone.ZoneDefinition::id).toList(); }
            public List<String> mobIds() { return plugin.mobs().ids(); }
            public List<String> materials() { return materials; }
            public void saveFailed(IOException error) { plugin.getLogger().log(Level.SEVERE, "Could not save staff loot edit; previous snapshot remains active", error); }
        });
    }
    MobLootCommand(LootProfileStore store, Context context) { this.store = store; this.context = context; }

    public boolean command(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) { fail(sender, "You do not have permission to edit mob loot."); return true; }
        try {
            if (args.length == 0) { help(sender); return true; }
            String action = args[0].toLowerCase(Locale.ROOT);
            switch (action) {
                case "list" -> list(sender, args);
                case "bindings" -> bindings(sender, args);
                case "create" -> {
                    length(args, 2, "/mobloot create <profile>");
                    if (store.find(args[1]).isPresent()) throw new IllegalArgumentException("That loot profile already exists.");
                    store.put(new LootProfileDefinition(args[1], LootProfileDefinition.Mode.ADDITIVE, List.of()));
                    ok(sender, "Created " + args[1] + ". Add rewards, then assign it to a zone or mob. Existing drops stay in place by default.");
                }
                case "show" -> show(sender, args);
                case "add", "set" -> entry(sender, args, action.equals("set"));
                case "remove" -> {
                    length(args, 3, "/mobloot remove <profile> <entry>");
                    LootProfileDefinition profile = profile(args[1]);
                    var entries = new ArrayList<>(profile.entries());
                    if (!entries.removeIf(entry -> entry.id().equals(args[2]))) throw new IllegalArgumentException("No entry named " + args[2] + " in this profile.");
                    store.put(new LootProfileDefinition(profile.id(), profile.mode(), entries));
                    ok(sender, "Removed " + args[2] + " from " + profile.id() + ".");
                }
                case "mode" -> mode(sender, args);
                case "assign" -> assign(sender, args);
                case "unassign" -> unassign(sender, args);
                case "delete" -> {
                    length(args, 3, "/mobloot delete <profile> confirm"); confirmation(args[2]);
                    LootProfileDefinition profile = profile(args[1]);
                    store.remove(profile.id()); ok(sender, "Deleted " + profile.id() + ". No player inventories were changed.");
                }
                case "reload" -> {
                    length(args, 2, "/mobloot reload confirm"); confirmation(args[1]);
                    store.reload(); ok(sender, "Reloaded loot profiles. Existing mobs use the current profile when defeated.");
                }
                default -> help(sender);
            }
        } catch (IllegalArgumentException | IllegalStateException error) {
            fail(sender, error.getMessage());
        } catch (IOException error) {
            context.saveFailed(error);
            fail(sender, "Loot settings could not be saved or loaded. The previous settings are still active; check the server log.");
        }
        return true;
    }

    private void entry(CommandSender sender, String[] args, boolean replace) throws IOException {
        if (args.length != 8 && args.length != 10) throw usage("/mobloot " + (replace ? "set" : "add") + " <profile> <entry> <material|item|marks> <source> <chance%> <min> <max> [unidentified affixes]");
        LootProfileDefinition profile = profile(args[1]);
        LootProfileDefinition.Kind kind;
        try { kind = LootProfileDefinition.Kind.valueOf(args[3].toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("Reward type must be material, item or marks."); }
        String source = switch (kind) {
            case MATERIAL -> args[4].toUpperCase(Locale.ROOT);
            case ITEM -> args[4].toLowerCase(Locale.ROOT);
            case MARKS -> args[4].toLowerCase(Locale.ROOT);
        };
        double chance = decimal(args[5]);
        if (chance < 0 || chance > 100) throw new IllegalArgumentException("Chance must be a percentage from 0 to 100.");
        boolean unidentified = args.length == 10 && bool(args[8]);
        int affixes = args.length == 10 ? number(args[9]) : 0;
        var entry = new LootProfileDefinition.Entry(args[2], kind, source, chance / 100.0, number(args[6]), number(args[7]), unidentified, affixes);
        var entries = new ArrayList<>(profile.entries());
        boolean exists = entries.stream().anyMatch(old -> old.id().equals(entry.id()));
        if (exists && !replace) throw new IllegalArgumentException("That entry already exists. Use /mobloot set to change it.");
        if (!exists && replace) throw new IllegalArgumentException("That entry does not exist. Use /mobloot add to create it.");
        entries.removeIf(old -> old.id().equals(entry.id())); entries.add(entry);
        store.put(new LootProfileDefinition(profile.id(), profile.mode(), entries));
        ok(sender, "Saved " + entry.id() + " in " + profile.id() + ": " + describe(entry) + ".");
    }
    private void mode(CommandSender sender, String[] args) throws IOException {
        if (args.length < 3 || args.length > 4) throw usage("/mobloot mode <profile> <additive|replace> [confirm]");
        LootProfileDefinition profile = profile(args[1]);
        LootProfileDefinition.Mode mode;
        try { mode = LootProfileDefinition.Mode.valueOf(args[2].toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("Mode must be additive or replace."); }
        if (mode == LootProfileDefinition.Mode.REPLACE && args.length != 4)
            throw new IllegalArgumentException("Replace removes normal item drops, coin purses, gear rolls and mob-specific item drops. XP and boss participation rewards stay. Use /mobloot mode " + profile.id() + " replace confirm when your profile is ready.");
        if (args.length == 4) confirmation(args[3]);
        store.put(new LootProfileDefinition(profile.id(), mode, profile.entries()));
        ok(sender, "Saved " + profile.id() + " in " + mode.name().toLowerCase(Locale.ROOT) + " mode.");
    }
    private void assign(CommandSender sender, String[] args) throws IOException {
        if (args.length < 4 || args.length > 5) throw usage("/mobloot assign <zone|mob> <target> <profile> [confirm]");
        String scope = scope(args[1]), target = target(scope, args[2]);
        LootProfileDefinition profile = profile(args[3]);
        String old = (scope.equals("zone") ? store.zoneAssignments() : store.mobAssignments()).get(target);
        boolean consequential = profile.mode() == LootProfileDefinition.Mode.REPLACE || old != null && !old.equals(profile.id());
        if (consequential && args.length != 5) throw new IllegalArgumentException("This changes the existing drop rules. Confirm with /mobloot assign " + scope + " " + target + " " + profile.id() + " confirm.");
        if (args.length == 5) confirmation(args[4]);
        if (scope.equals("zone")) store.assignZone(target, profile.id()); else store.assignMob(target, profile.id());
        ok(sender, "Assigned " + profile.id() + " to " + scope + " " + target + ". Zone profiles take priority over mob profiles.");
    }
    private void unassign(CommandSender sender, String[] args) throws IOException {
        length(args, 4, "/mobloot unassign <zone|mob> <target> confirm"); confirmation(args[3]);
        String scope = scope(args[1]);
        // Allow cleanup of assignments whose zone or content definition was deliberately removed.
        String target = scope.equals("mob") ? mobId(args[2]) : args[2];
        Map<String, String> assignments = scope.equals("zone") ? store.zoneAssignments() : store.mobAssignments();
        if (!assignments.containsKey(target)) throw new IllegalArgumentException("That " + scope + " has no loot profile assigned.");
        if (scope.equals("zone")) store.unassignZone(target); else store.unassignMob(target);
        ok(sender, "Removed the loot assignment for " + scope + " " + target + ". A mob profile may still apply; otherwise normal drops resume.");
    }
    private void list(CommandSender sender, String[] args) {
        if (args.length > 2) throw usage("/mobloot list [page]");
        var profiles = store.all(); int pages = pages(profiles.size()), page = args.length == 2 ? number(args[1]) : 1; checkPage(page, pages);
        heading(sender, "Loot profiles — " + profiles.size() + " total, page " + page + "/" + pages);
        profiles.stream().skip((long) (page - 1) * 10).limit(10).forEach(profile -> detail(sender, "  " + profile.id() + " · " + profile.mode().name().toLowerCase(Locale.ROOT) + " · " + profile.entries().size() + " rewards"));
        if (profiles.isEmpty()) detail(sender, "Start with /mobloot create <profile>.");
    }
    private void show(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) throw usage("/mobloot show <profile> [page]");
        var profile = profile(args[1]); int pages = pages(profile.entries().size()), page = args.length == 3 ? number(args[2]) : 1; checkPage(page, pages);
        heading(sender, profile.id() + " — " + profile.mode().name().toLowerCase(Locale.ROOT) + ", page " + page + "/" + pages);
        profile.entries().stream().skip((long) (page - 1) * 10).limit(10).forEach(entry -> detail(sender, "  " + entry.id() + ": " + describe(entry)));
        if (profile.entries().isEmpty()) note(sender, "No rewards. An empty replace profile removes normal item rewards.");
        detail(sender, "Each entry rolls once per defeated mob, not once per stack. This preview does not award loot.");
    }
    private void bindings(CommandSender sender, String[] args) {
        if (args.length > 2) throw usage("/mobloot bindings [page]");
        var entries = new ArrayList<String>();
        store.zoneAssignments().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> entries.add("zone " + entry.getKey() + " → " + entry.getValue()));
        store.mobAssignments().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> entries.add("mob " + entry.getKey() + " → " + entry.getValue()));
        int pages = pages(entries.size()), page = args.length == 2 ? number(args[1]) : 1; checkPage(page, pages);
        heading(sender, "Loot assignments — page " + page + "/" + pages + ". Zone rules take priority.");
        entries.stream().skip((long) (page - 1) * 10).limit(10).forEach(entry -> detail(sender, "  " + entry));
        if (entries.isEmpty()) detail(sender, "No assignments. All mobs still use normal drop tables.");
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION) || args.length == 0) return List.of();
        String action = args[0].toLowerCase(Locale.ROOT); List<String> values = List.of();
        if (args.length == 1) values = ACTIONS;
        else if (args.length == 2) values = switch (action) {
            case "assign", "unassign" -> List.of("zone", "mob");
            case "reload" -> List.of("confirm");
            case "show", "add", "set", "remove", "mode", "delete" -> store.all().stream().map(LootProfileDefinition::id).toList();
            case "list" -> pageValues(store.all().size());
            case "bindings" -> pageValues(store.zoneAssignments().size() + store.mobAssignments().size());
            default -> List.of();
        };
        else if (args.length == 3) values = switch (action) {
            case "mode" -> List.of("additive", "replace");
            case "delete" -> List.of("confirm");
            case "show" -> store.find(args[1]).map(profile -> pageValues(profile.entries().size())).orElse(List.of());
            case "set", "remove" -> store.find(args[1]).map(profile -> profile.entries().stream().map(LootProfileDefinition.Entry::id).toList()).orElse(List.of());
            case "assign" -> args[1].equalsIgnoreCase("zone") ? context.zoneIds() : args[1].equalsIgnoreCase("mob") ? mobTargets() : List.of();
            case "unassign" -> args[1].equalsIgnoreCase("zone") ? List.copyOf(store.zoneAssignments().keySet()) : args[1].equalsIgnoreCase("mob") ? List.copyOf(store.mobAssignments().keySet()) : List.of();
            default -> List.of();
        };
        else if (args.length == 4 && action.equals("assign")) values = store.all().stream().map(LootProfileDefinition::id).toList();
        else if (args.length == 4 && (action.equals("mode") || action.equals("unassign"))) values = List.of("confirm");
        else if (args.length == 5 && action.equals("assign")) values = List.of("confirm");
        else if ((action.equals("add") || action.equals("set"))) {
            values = switch (args.length) {
                case 4 -> List.of("material", "item", "marks");
                case 5 -> switch (args[3].toLowerCase(Locale.ROOT)) {
                    case "material" -> context.materials();
                    case "item" -> store.knownItems().stream().map(net.nerdsmp.api.model.ItemId::value).sorted().toList();
                    case "marks" -> List.of("marks");
                    default -> List.of();
                };
                case 6 -> List.of("1", "5", "10", "25", "50", "100");
                case 7, 8 -> args[3].equalsIgnoreCase("marks") ? List.of("1", "5", "10", "25", "50", "100") : List.of("1", "2", "4", "8", "16", "64");
                case 9 -> List.of("false", "true");
                case 10 -> List.of("0", "1", "2", "3");
                default -> List.of();
            };
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).distinct().sorted().toList();
    }
    private LootProfileDefinition profile(String id) { return store.find(id).orElseThrow(() -> new IllegalArgumentException("No loot profile named " + id + ".")); }
    private String target(String scope, String input) {
        if (scope.equals("zone")) { if (!context.zoneExists(input)) throw new IllegalArgumentException("No zone named " + input + ". Create it with /zone first."); return input; }
        String id = mobId(input);
        if (!VANILLA_MOBS.contains(id) && !context.mobIds().contains(id)) throw new IllegalArgumentException("Unknown mob " + input + ". Use vanilla:<type> or custom:<id> from the tab-completion catalog.");
        return id;
    }
    private List<String> mobTargets() {
        var ids = new ArrayList<>(VANILLA_MOBS); context.mobIds().forEach(id -> ids.add("custom:" + id)); return ids;
    }
    private static String mobId(String input) { return input.startsWith("custom:") ? input.substring(7) : input; }
    private static String scope(String input) { if (!input.equalsIgnoreCase("zone") && !input.equalsIgnoreCase("mob")) throw new IllegalArgumentException("Assignment type must be zone or mob."); return input.toLowerCase(Locale.ROOT); }
    private static String describe(LootProfileDefinition.Entry entry) {
        return String.format(Locale.ROOT, "%.2f%%", entry.chance() * 100) + " · " + entry.minimum() + (entry.minimum() == entry.maximum() ? "" : "–" + entry.maximum()) + " " + entry.source()
            + (entry.unidentified() ? " · unidentified" : "") + (entry.affixes() > 0 ? " · " + entry.affixes() + " affixes" : "");
    }
    private static int pages(int size) { return Math.max(1, (size + 9) / 10); }
    private static List<String> pageValues(int size) { return java.util.stream.IntStream.rangeClosed(1, pages(size)).mapToObj(Integer::toString).toList(); }
    private static void checkPage(int page, int pages) { if (page < 1 || page > pages) throw new IllegalArgumentException("Page must be 1–" + pages + "."); }
    private static void confirmation(String input) { if (!input.equalsIgnoreCase("confirm")) throw new IllegalArgumentException("Add confirm to acknowledge this change."); }
    private static int number(String input) { try { return Integer.parseInt(input); } catch (NumberFormatException error) { throw new IllegalArgumentException("Enter a whole number within the supported range."); } }
    private static double decimal(String input) { try { double value = Double.parseDouble(input); if (!Double.isFinite(value)) throw new NumberFormatException(); return value; } catch (NumberFormatException error) { throw new IllegalArgumentException("Enter a finite chance percentage, such as 5 or 12.5."); } }
    private static boolean bool(String input) { if (input.equalsIgnoreCase("true")) return true; if (input.equalsIgnoreCase("false")) return false; throw new IllegalArgumentException("Unidentified must be true or false."); }
    private static void length(String[] args, int expected, String usage) { if (args.length != expected) throw usage(usage); }
    private static IllegalArgumentException usage(String command) { return new IllegalArgumentException("Use " + command); }
    private static void ok(CommandSender sender, String message) { Chat.success(sender, Chat.escape(message)); }
    private static void fail(CommandSender sender, String message) { Chat.error(sender, Chat.escape(message == null ? "That loot change was not valid." : message)); }
    private static void note(CommandSender sender, String message) { Chat.notice(sender, Chat.escape(message)); }
    private static void detail(CommandSender sender, String message) { Chat.info(sender, Chat.escape(message)); }
    private static void heading(CommandSender sender, String message) { Chat.send(sender, "<white>" + Chat.escape(message) + "</white>"); }
    private static void help(CommandSender sender) {
        heading(sender, "Mob loot — staff editor");
        detail(sender, "/mobloot create <profile> · list [page] · show <profile> [page]");
        detail(sender, "/mobloot add <profile> <entry> <material|item|marks> <source> <chance%> <min> <max> [unidentified affixes]");
        detail(sender, "/mobloot set <profile> <entry> <type> <source> <chance%> <min> <max> [unidentified affixes]");
        detail(sender, "/mobloot remove <profile> <entry> · mode <profile> <additive|replace> [confirm]");
        detail(sender, "/mobloot assign <zone|mob> <target> <profile> [confirm] · bindings [page]");
        detail(sender, "/mobloot unassign <zone|mob> <target> confirm · delete <profile> confirm · reload confirm");
        detail(sender, "Example: /mobloot add crypt_rewards coins marks marks 35 2 6");
        detail(sender, "Zone profiles override mob profiles. Additive keeps normal drops; replace removes them. Marks drop as pickup-only coin purses.");
    }
}
