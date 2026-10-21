package net.nerdsmp.paper.expedition;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Box;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Point;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Room;
import net.nerdsmp.paper.expedition.content.ExpeditionLoader;
import net.nerdsmp.paper.expedition.content.ExpeditionValidator;
import net.nerdsmp.paper.expedition.content.Modifier;
import net.nerdsmp.paper.expedition.content.Tier;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.zone.AreaSelection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The guided staff flow for building an expedition in game. Standing in a template world picks the
 * expedition, so commands never need its id. Every edit saves the file, re-runs validation and names
 * the next missing step. The wand marks room corners, and shift-right-click drops mob points. Holding
 * the wand draws the rooms, points, gates, spawn and exit.
 */
public final class ExpeditionSetup implements Listener, AutoCloseable {
    private static final List<String> COMMANDS = List.of("create", "edit", "wand", "set", "room", "gate", "checkpoint", "point",
        "wave", "survive", "boss", "loot", "check", "test", "publish", "unpublish", "reload", "runs", "stop", "guide");
    private final NerdSmpPlugin plugin;
    private final ExpeditionService service;
    private final NamespacedKey wandKey;
    private final Map<UUID, String> editing = new HashMap<>();
    private final BukkitTask previews;

    public ExpeditionSetup(NerdSmpPlugin plugin, ExpeditionService service) {
        this.plugin = plugin;
        this.service = service;
        this.wandKey = new NamespacedKey(plugin, "expedition_wand");
        previews = Bukkit.getScheduler().runTaskTimer(plugin, this::preview, 40L, 15L);
    }

    boolean handles(String action) { return COMMANDS.contains(action); }
    List<String> subcommands() { return COMMANDS; }

    // ---------------------------------------------------------------------------------------------
    // Commands
    // ---------------------------------------------------------------------------------------------

    boolean command(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ExpeditionService.ADMIN)) { Chat.noPermission(sender); return true; }
        String action = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "reload" -> {
                    plugin.reloadConfig();
                    List<String> problems = service.reload();
                    Chat.success(sender, "Reloaded " + service.definitions().size() + " expeditions.");
                    problems.forEach(problem -> Chat.error(sender, Chat.escape(problem)));
                    return true;
                }
                case "runs" -> {
                    List<String> lines = service.describeRuns();
                    if (lines.isEmpty()) Chat.info(sender, "No expeditions are running.");
                    else lines.forEach(line -> Chat.info(sender, Chat.escape(line)));
                    Chat.info(sender, service.activeRuns() + "/" + service.maxInstances() + " instances in use.");
                    return true;
                }
                case "stop" -> {
                    if (args.length != 2) { Chat.usage(sender, "/expedition stop <run number from /expedition runs>"); return true; }
                    if (service.stopRun(Integer.parseInt(args[1]))) Chat.success(sender, "Run closed.");
                    else Chat.error(sender, "No run with that number.");
                    return true;
                }
                case "check" -> {
                    ExpeditionDefinition definition = args.length > 1 ? require(args[1]) : current(sender);
                    checklist(sender, definition);
                    return true;
                }
                case "guide" -> { guide(sender); return true; }
                default -> { }
            }
            if (!(sender instanceof Player player)) { Chat.playersOnly(sender); return true; }
            switch (action) {
                case "create" -> create(player, args);
                case "edit" -> {
                    if (args.length != 2) { Chat.usage(player, "/expedition edit <expedition>"); return true; }
                    edit(player, require(args[1]));
                }
                case "wand" -> giveWand(player);
                case "set" -> set(player, args);
                case "room" -> room(player, args);
                case "gate" -> gate(player, args);
                case "checkpoint" -> {
                    if (args.length != 2) { Chat.usage(player, "/expedition checkpoint <room>"); return true; }
                    editRoom(player, args[1], room -> room.set("checkpoint", here(player).serialize()), "Checkpoint set where you stand.");
                }
                case "point" -> point(player, args);
                case "wave" -> wave(player, args);
                case "survive" -> {
                    if (args.length != 3) { Chat.usage(player, "/expedition survive <room> <seconds>"); return true; }
                    int seconds = Integer.parseInt(args[2]);
                    editRoom(player, args[1], room -> {
                        room.set("encounter.type", "survive");
                        room.set("encounter.survive-seconds", seconds);
                    }, "Survive room: the party holds out for " + seconds + " seconds while its waves repeat.");
                }
                case "boss" -> {
                    if (args.length != 3) { Chat.usage(player, "/expedition boss <room> <mob>"); return true; }
                    String mob = mobId(args[2]);
                    editRoom(player, args[1], room -> {
                        room.set("encounter.type", "boss");
                        room.set("encounter.boss", mob);
                    }, "Boss set to " + mob + ". It rises at the room's first point; add adds with /expedition wave.");
                }
                case "loot" -> loot(player, args);
                case "test" -> {
                    ExpeditionDefinition definition = args.length > 1 && service.definition(args[1]).isPresent() ? require(args[1]) : current(player);
                    String tier = args.length > 2 ? args[2] : args.length == 2 && service.definition(args[1]).isEmpty() ? args[1] : null;
                    service.test(player, definition, tier);
                }
                case "publish", "unpublish" -> publish(player, args, action.equals("publish"));
                default -> Chat.error(player, "Unknown setup command.");
            }
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            Chat.error(sender, Chat.escape(invalid.getMessage()));
        } catch (IOException failure) {
            Chat.error(sender, "Could not save the expedition file: " + Chat.escape(failure.getMessage()));
        }
        return true;
    }

    private void create(Player player, String[] args) throws IOException {
        if (args.length < 2 || args.length > 4) {
            Chat.usage(player, "/expedition create <id> [overworld|nether|end]");
            Chat.usage(player, "/expedition create <id> from <loaded world>");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!id.matches(ExpeditionDefinition.ID)) throw new IllegalArgumentException("Use 1-32 lowercase letters, digits or underscores for the id.");
        if (service.definition(id).isPresent() || service.fileFor(id).exists()) throw new IllegalArgumentException("That expedition already exists. Use /expedition edit " + id + ".");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("title", ExpeditionLoader.titleCase(id));
        yaml.set("description", "");
        if (args.length == 4 && args[2].equalsIgnoreCase("from")) {
            World world = Bukkit.getWorld(args[3]);
            if (world == null) world = Bukkit.getWorld(NamespacedKey.minecraft(args[3].toLowerCase(Locale.ROOT)));
            if (world == null) throw new IllegalArgumentException("No loaded world called " + args[3] + ".");
            if (Bukkit.getWorlds().indexOf(world) >= 0 && Bukkit.getWorlds().indexOf(world) < 3)
                throw new IllegalArgumentException("The main worlds can't be expedition templates. Build in a separate world.");
            if (InstanceWorlds.isRunWorld(world)) throw new IllegalArgumentException("That is a temporary run world.");
            yaml.set("dimension", switch (world.getEnvironment()) { case NETHER -> "nether"; case THE_END -> "end"; default -> "overworld"; });
            yaml.set("template-world", world.getName());
        } else {
            yaml.set("dimension", ExpeditionDefinition.Dimension.parse(args.length >= 3 ? args[2] : "overworld").key());
        }
        yaml.set("published", false);
        yaml.set("level", 10);
        yaml.set("party.min", 1);
        yaml.set("party.max", ExpeditionDefinition.MAX_PARTY);
        yaml.set("time-limit-minutes", 30);
        yaml.set("loot.marks", 100);
        yaml.setComments("rooms", List.of("Rooms run top to bottom. Add them in game with the wand and /expedition room add <name>."));
        yaml.createSection("rooms");
        service.store(id, yaml);
        ExpeditionDefinition definition = require(id);
        Chat.success(player, "Created <white>" + Chat.escape(definition.title()) + "</white> (" + definition.dimension().key() + "). Building the template world…");
        edit(player, definition);
        guide(player);
    }

    private void edit(Player player, ExpeditionDefinition definition) {
        World world = service.worlds().openTemplate(definition, SampleTemplates.plan(definition.sample()));
        service.revalidate(definition.id());
        editing.put(player.getUniqueId(), definition.id());
        Point spawn = definition.spawn();
        Location target = spawn != null ? new Location(world, spawn.x() + .5, spawn.y(), spawn.z() + .5, spawn.yaw(), 0)
            : new Location(world, 4.5, TemplateGenerator.PLATFORM_Y, 4.5);
        service.teleport(player, target);
        if (!hasWand(player)) giveWand(player);
        Chat.info(player, "Editing <white>" + Chat.escape(definition.title()) + "</white>. Changes save as you go; runs copy this world.");
        checklist(player, definition);
    }

    private void set(Player player, String[] args) throws IOException {
        if (args.length < 2) { Chat.usage(player, "/expedition set <spawn|exit|title|description|level|party|time|icon> [value]"); return; }
        ExpeditionDefinition definition = current(player);
        YamlConfiguration yaml = yaml(definition);
        String field = args[1].toLowerCase(Locale.ROOT);
        String rest = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).strip();
        switch (field) {
            case "spawn" -> yaml.set("spawn", here(player).serialize());
            case "exit" -> yaml.set("exit", here(player).serialize());
            case "title" -> yaml.set("title", requireText(rest));
            case "description" -> yaml.set("description", requireText(rest));
            case "level" -> yaml.set("level", Integer.parseInt(rest));
            case "time" -> yaml.set("time-limit-minutes", Integer.parseInt(rest));
            case "icon" -> {
                Material material = Material.matchMaterial(rest);
                if (material == null || !material.isItem()) throw new IllegalArgumentException("Unknown item material " + rest + ".");
                yaml.set("icon", material.name().toLowerCase(Locale.ROOT));
            }
            case "party" -> {
                if (args.length != 4) { Chat.usage(player, "/expedition set party <min> <max>"); return; }
                yaml.set("party.min", Integer.parseInt(args[2]));
                yaml.set("party.max", Integer.parseInt(args[3]));
            }
            case "modifiers" -> yaml.set("modifiers", rest.isEmpty() || rest.equals("none") ? List.of() : List.of(rest.toLowerCase(Locale.ROOT).split("[ ,]+")));
            default -> throw new IllegalArgumentException("Unknown field " + field + ".");
        }
        String label = field.equals("time") ? "Time limit" : Character.toUpperCase(field.charAt(0)) + field.substring(1);
        save(player, definition.id(), yaml, label + " saved.");
    }

    private void room(Player player, String[] args) throws IOException {
        if (args.length < 2) { Chat.usage(player, "/expedition room <add|remove|title|list> [room]"); return; }
        ExpeditionDefinition definition = current(player);
        YamlConfiguration yaml = yaml(definition);
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> {
                int index = 1;
                if (definition.rooms().isEmpty()) Chat.info(player, "No rooms yet.");
                for (Room room : definition.roomList())
                    Chat.info(player, index++ + ". <white>" + Chat.escape(room.id()) + "</white> <gray>" + Chat.escape(room.title()) + " · "
                        + room.encounter().type().name().toLowerCase(Locale.ROOT) + " · " + room.points().size() + " points"
                        + (room.gate() == null ? "" : " · gate") + "</gray>");
            }
            case "add" -> {
                if (args.length != 3) { Chat.usage(player, "/expedition room add <name>"); return; }
                String id = roomId(args[2]);
                Box box = selection(player);
                if (box.volume() > ExpeditionValidator.MAX_ROOM_VOLUME) throw new IllegalArgumentException("That selection is too large for a room.");
                boolean existed = definition.rooms().containsKey(id);
                ConfigurationSection room = rooms(yaml).getConfigurationSection(id);
                if (room == null) {
                    room = rooms(yaml).createSection(id);
                    room.set("title", ExpeditionLoader.titleCase(id));
                }
                room.set("region", box.serialize());
                save(player, definition.id(), yaml, existed ? "Room " + id + " resized." : "Room " + id + " added as room " + (definition.rooms().size() + 1)
                    + ". Next: shift-right-click inside it with the wand to add mob points, then " + Chat.syntax("/expedition wave " + id + " <mob> [count]") + ".");
            }
            case "remove" -> {
                if (args.length != 3) { Chat.usage(player, "/expedition room remove <name>"); return; }
                String id = roomId(args[2]);
                if (!definition.rooms().containsKey(id)) throw new IllegalArgumentException("No room called " + id + ".");
                rooms(yaml).set(id, null);
                save(player, definition.id(), yaml, "Room " + id + " removed. Its builds stay in the template.");
            }
            case "title" -> {
                if (args.length < 4) { Chat.usage(player, "/expedition room title <room> <title>"); return; }
                String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                editRoom(player, args[2], room -> room.set("title", text), "Room title saved.");
            }
            default -> Chat.usage(player, "/expedition room <add|remove|title|list> [room]");
        }
    }

    private void gate(Player player, String[] args) throws IOException {
        if (args.length < 2 || args.length > 3) { Chat.usage(player, "/expedition gate <room> [clear]"); return; }
        if (args.length == 3 && args[2].equalsIgnoreCase("clear")) {
            editRoom(player, args[1], room -> room.set("gate", null), "Gate removed.");
            return;
        }
        Box box = selection(player);
        if (box.volume() > ExpeditionValidator.MAX_GATE_VOLUME) throw new IllegalArgumentException("Gates can be at most " + ExpeditionValidator.MAX_GATE_VOLUME + " blocks.");
        editRoom(player, args[1], room -> room.set("gate", box.serialize()),
            "Gate saved. The blocks you built there vanish when the room is cleared.");
    }

    private void point(Player player, String[] args) throws IOException {
        if (args.length < 3) { Chat.usage(player, "/expedition point <room> <add|list|remove <n>|clear>"); return; }
        ExpeditionDefinition definition = current(player);
        Room room = definition.rooms().get(roomId(args[1]));
        if (room == null) throw new IllegalArgumentException("No room called " + args[1] + ".");
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "add" -> addPoint(player, definition, room, here(player));
            case "list" -> {
                int index = 1;
                for (Point point : room.points()) Chat.info(player, index++ + ". " + point.x() + ", " + point.y() + ", " + point.z());
                if (room.points().isEmpty()) Chat.info(player, "No points yet.");
            }
            case "remove" -> {
                if (args.length != 4) { Chat.usage(player, "/expedition point <room> remove <n>"); return; }
                int index = Integer.parseInt(args[3]) - 1;
                if (index < 0 || index >= room.points().size()) throw new IllegalArgumentException("No point #" + args[3] + ".");
                List<Point> points = new ArrayList<>(room.points());
                points.remove(index);
                writePoints(player, definition, room, points, "Point removed.");
            }
            case "clear" -> writePoints(player, definition, room, List.of(), "Points cleared.");
            default -> Chat.usage(player, "/expedition point <room> <add|list|remove <n>|clear>");
        }
    }

    private void addPoint(Player player, ExpeditionDefinition definition, Room room, Point point) throws IOException {
        List<Point> points = new ArrayList<>(room.points());
        points.add(point);
        writePoints(player, definition, room, points, "Point #" + points.size() + " added to " + room.id()
            + (room.points().isEmpty() && room.encounter().type() == ExpeditionDefinition.EncounterType.BOSS ? " (the boss rises here)" : "") + ".");
    }

    private void writePoints(Player player, ExpeditionDefinition definition, Room room, List<Point> points, String message) throws IOException {
        YamlConfiguration yaml = yaml(definition);
        rooms(yaml).getConfigurationSection(room.id()).set("points", points.stream().map(Point::serialize).toList());
        save(player, definition.id(), yaml, message);
    }

    private void wave(Player player, String[] args) throws IOException {
        if (args.length < 3) {
            Chat.usage(player, "/expedition wave <room> <mob> [count] [wave#]");
            Chat.usage(player, "/expedition wave <room> clear");
            return;
        }
        ExpeditionDefinition definition = current(player);
        Room room = definition.rooms().get(roomId(args[1]));
        if (room == null) throw new IllegalArgumentException("No room called " + args[1] + ".");
        YamlConfiguration yaml = yaml(definition);
        ConfigurationSection section = rooms(yaml).getConfigurationSection(room.id());
        if (args[2].equalsIgnoreCase("clear")) {
            section.set("encounter.waves", null);
            save(player, definition.id(), yaml, "Waves cleared.");
            return;
        }
        String mob = mobId(args[2]);
        if (!service.catalog().mob(mob)) throw new IllegalArgumentException("Unknown mob " + mob + ". Use custom:<id> or vanilla:<type>.");
        int count = args.length > 3 ? Integer.parseInt(args[3]) : 1;
        List<Map<String, Object>> waves = new ArrayList<>();
        for (var wave : room.encounter().waves()) {
            Map<String, Object> map = new LinkedHashMap<>();
            Map<String, Object> mobs = new LinkedHashMap<>(wave.mobs());
            map.put("mobs", mobs);
            if (wave.delaySeconds() > 0) map.put("delay-seconds", wave.delaySeconds());
            waves.add(map);
        }
        int number = args.length > 4 ? Integer.parseInt(args[4]) : Math.max(1, waves.size());
        if (number < 1 || number > waves.size() + 1) throw new IllegalArgumentException("Wave number must be 1-" + (waves.size() + 1) + ".");
        if (number == waves.size() + 1) waves.add(new LinkedHashMap<>(Map.of("mobs", new LinkedHashMap<String, Object>())));
        @SuppressWarnings("unchecked") Map<String, Object> mobs = (Map<String, Object>) waves.get(number - 1).get("mobs");
        if (count <= 0) mobs.remove(mob);
        else mobs.put(mob, count);
        section.set("encounter.waves", waves);
        if (!section.contains("encounter.type")) section.set("encounter.type", "waves");
        save(player, definition.id(), yaml, "Wave " + number + " of " + room.id() + ": " + mobs + ". Add another wave with "
            + Chat.syntax("/expedition wave " + room.id() + " <mob> <count> " + (waves.size() + 1)) + ".");
    }

    private void loot(Player player, String[] args) throws IOException {
        if (args.length < 2) {
            Chat.usage(player, "/expedition loot <profile|none>");
            Chat.usage(player, "/expedition loot marks <amount>");
            Chat.usage(player, "/expedition loot item <item> <amount|0>");
            return;
        }
        ExpeditionDefinition definition = current(player);
        YamlConfiguration yaml = yaml(definition);
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "none" -> yaml.set("loot.profile", null);
            case "marks" -> yaml.set("loot.marks", Long.parseLong(args[2]));
            case "item" -> {
                if (args.length != 4) { Chat.usage(player, "/expedition loot item <item> <amount|0>"); return; }
                int amount = Integer.parseInt(args[3]);
                yaml.set("loot.items." + args[2].toLowerCase(Locale.ROOT), amount <= 0 ? null : amount);
            }
            default -> {
                String profile = args[1].toLowerCase(Locale.ROOT);
                if (!service.catalog().lootProfile(profile)) throw new IllegalArgumentException("No loot profile " + profile + ". Create it with /mobloot create " + profile + ".");
                yaml.set("loot.profile", profile);
            }
        }
        save(player, definition.id(), yaml, "Loot saved.");
    }

    private void publish(Player player, String[] args, boolean publish) throws IOException {
        ExpeditionDefinition definition = args.length > 1 ? require(args[1]) : current(player);
        if (publish && !ExpeditionValidator.publishable(service.issues(definition.id()))) {
            Chat.error(player, "Fix these first:");
            checklist(player, definition);
            return;
        }
        YamlConfiguration yaml = yaml(definition);
        yaml.set("published", publish);
        service.store(definition.id(), yaml);
        Chat.success(player, Chat.escape(definition.title()) + (publish ? " is now open to players." : " is hidden from players."));
    }

    // ---------------------------------------------------------------------------------------------
    // Checklist and guide
    // ---------------------------------------------------------------------------------------------

    void checklist(CommandSender sender, ExpeditionDefinition definition) {
        List<ExpeditionValidator.Issue> issues = service.issues(definition.id());
        long errors = issues.stream().filter(ExpeditionValidator.Issue::error).count();
        Chat.send(sender, "<gold><bold>" + Chat.escape(definition.title()) + "</bold></gold> <dark_gray>" + definition.id() + " · "
            + definition.dimension().key() + " · " + definition.rooms().size() + " rooms · "
            + (definition.published() ? "<green>published</green>" : "<yellow>draft</yellow>") + "</dark_gray>");
        String loadError = service.loadErrors().get(definition.id());
        if (loadError != null) Chat.send(sender, "<red>✗ The file on disk doesn't load: " + Chat.escape(loadError) + " (showing the last good version)</red>");
        check(sender, definition.spawn() != null, "Party spawn", "/expedition set spawn");
        check(sender, !definition.rooms().isEmpty(), "At least one room", "/expedition room add <name>");
        check(sender, definition.rooms().values().stream().anyMatch(room -> room.encounter().type() == ExpeditionDefinition.EncounterType.BOSS), "A boss room", "/expedition boss <room> <mob>");
        check(sender, definition.exit() != null, "Exit point", "/expedition set exit");
        for (ExpeditionValidator.Issue issue : issues) {
            String fix = issue.fix().startsWith("/") ? " <dark_gray>→</dark_gray> " + Chat.syntax(issue.fix()) : " <dark_gray>→ " + Chat.escape(issue.fix()) + "</dark_gray>";
            Chat.send(sender, (issue.error() ? "<red>✗ " : "<yellow>! ") + Chat.escape(issue.message()) + (issue.error() ? "</red>" : "</yellow>") + fix);
        }
        if (errors == 0) Chat.send(sender, "<green>✔ Ready to run.</green> " + Chat.command("expedition test " + definition.id())
            + (definition.published() ? "" : " then " + Chat.command("expedition publish " + definition.id())));
    }

    private void check(CommandSender sender, boolean done, String label, String fix) {
        Chat.send(sender, done ? "<green>✔ " + label + "</green>" : "<gray>○ " + label + "</gray> <dark_gray>→</dark_gray> " + Chat.syntax(fix));
    }

    private void guide(CommandSender sender) {
        Chat.send(sender, "<gold><bold>Building an expedition</bold></gold>");
        Chat.send(sender, "<gray>1.</gray> " + Chat.syntax("/expedition create <id> [overworld|nether|end]") + " <gray>makes a template world and gives you the wand.</gray>");
        Chat.send(sender, "<gray>2.</gray> <gray>Build your dungeon. Stand at the start:</gray> " + Chat.command("expedition set spawn"));
        Chat.send(sender, "<gray>3.</gray> <gray>Wand left/right-click two corners of a room, then</gray> " + Chat.syntax("/expedition room add <name>")
            + " <gray>. Rooms run in the order you add them.</gray>");
        Chat.send(sender, "<gray>4.</gray> <gray>Shift-right-click the floor with the wand to drop mob points, then</gray> "
            + Chat.syntax("/expedition wave <room> <mob> [count]") + " <gray>or</gray> " + Chat.syntax("/expedition boss <room> <mob>"));
        Chat.send(sender, "<gray>5.</gray> <gray>Optional: build a door, select it and</gray> " + Chat.syntax("/expedition gate <room>")
            + " <gray>; set</gray> " + Chat.syntax("/expedition checkpoint <room>"));
        Chat.send(sender, "<gray>6.</gray> <gray>Stand at the finish:</gray> " + Chat.command("expedition set exit") + " <gray>, then</gray> "
            + Chat.command("expedition check") + "<gray>,</gray> " + Chat.command("expedition test") + " <gray>and</gray> " + Chat.command("expedition publish"));
        Chat.send(sender, "<dark_gray>Scripts, tiers and modifiers live in plugins/NerdSMP/expeditions/<id>.yml; see docs/EXPEDITIONS.md. Modifiers: "
            + String.join(", ", Arrays.stream(Modifier.values()).map(Modifier::key).toList()) + ".</dark_gray>");
    }

    // ---------------------------------------------------------------------------------------------
    // Wand
    // ---------------------------------------------------------------------------------------------

    private ItemStack wand() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        var meta = item.getItemMeta();
        meta.setMaxStackSize(1);
        meta.itemName(Component.text("Expedition Wand", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(line("Left/right-click: room corners"), line("Shift-right-click floor: mob point"),
            line("Shift-left-click: checklist"), line("Then: /expedition room add <name>")));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
        return item;
    }

    private static Component line(String text) { return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false); }

    private boolean isWand(ItemStack item) {
        return item != null && item.getType() == Material.BLAZE_ROD && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.INTEGER);
    }

    private boolean hasWand(Player player) {
        return Arrays.stream(player.getInventory().getContents()).anyMatch(this::isWand);
    }

    private void giveWand(Player player) {
        if (!player.getInventory().addItem(wand()).isEmpty()) Chat.error(player, "Make room in your inventory for the wand.");
        else Chat.success(player, "Expedition Wand ready.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWand(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isWand(event.getItem()) || event.getClickedBlock() == null) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission(ExpeditionService.ADMIN)) return;
        var block = event.getClickedBlock();
        boolean left = event.getAction() == Action.LEFT_CLICK_BLOCK;
        try {
            if (player.isSneaking()) {
                ExpeditionDefinition definition = current(player);
                if (left) { checklist(player, definition); return; }
                Point point = new Point(block.getX(), block.getY() + 1, block.getZ(), Math.round(player.getLocation().getYaw() / 45F) * 45F);
                Room room = definition.roomAt(point.x() + .5, point.y(), point.z() + .5).orElseThrow(() ->
                    new IllegalArgumentException("That spot isn't inside a room. Add the room first with the wand and /expedition room add <name>."));
                addPoint(player, definition, room, point);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, .5F, 1.4F);
                return;
            }
            var selected = plugin.zoneCommands().selections().select(player.getUniqueId(), block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(), left);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, .35F, left ? 1F : 1.25F);
            String corner = (left ? "Corner 1" : "Corner 2") + " at " + block.getX() + ", " + block.getY() + ", " + block.getZ();
            if (selected.complete()) Chat.info(player, corner + " <dark_gray>(" + selected.volume() + " blocks)</dark_gray>. Save it with "
                + Chat.syntax("/expedition room add <name>") + " or " + Chat.syntax("/expedition gate <room>") + ".");
            else Chat.info(player, corner + ". Now " + (left ? "right" : "left") + "-click the opposite corner.");
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            Chat.error(player, Chat.escape(invalid.getMessage()));
        } catch (IOException failure) {
            Chat.error(player, "Could not save: " + Chat.escape(failure.getMessage()));
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { editing.remove(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void onLeaveTemplate(PlayerChangedWorldEvent event) {
        World from = event.getFrom();
        service.definitions().stream().filter(definition -> definition.templateWorld().equals(from.getName())).findFirst()
            .ifPresent(definition -> Bukkit.getScheduler().runTaskLater(plugin, () -> service.worlds().closeTemplate(definition), 20L));
    }

    /** Draws the expedition for staff holding the wand: rooms, points, gates, spawn, exit and the selection. */
    private void preview() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isWand(player.getInventory().getItemInMainHand()) || !player.hasPermission(ExpeditionService.ADMIN)) continue;
            Optional<ExpeditionDefinition> definition = templateAt(player.getWorld());
            plugin.zoneCommands().selections().selection(player.getUniqueId()).filter(AreaSelection.Selection::complete)
                .filter(selection -> selection.worldId().equals(player.getWorld().getUID()))
                .ifPresent(selection -> {
                    var b = selection.bounds();
                    outline(player, Box.of(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()), Particle.END_ROD);
                });
            if (definition.isEmpty()) continue;
            for (Room room : definition.get().roomList()) {
                if (room.region() != null) outline(player, room.region(), Particle.HAPPY_VILLAGER);
                if (room.gate() != null) outline(player, room.gate(), Particle.SMOKE);
                for (Point point : room.points()) marker(player, point, Particle.FLAME);
                if (room.checkpoint() != null) marker(player, room.checkpoint(), Particle.SOUL_FIRE_FLAME);
            }
            if (definition.get().spawn() != null) marker(player, definition.get().spawn(), Particle.TOTEM_OF_UNDYING);
            if (definition.get().exit() != null) marker(player, definition.get().exit(), Particle.PORTAL);
        }
    }

    private void marker(Player player, Point point, Particle particle) {
        Location at = new Location(player.getWorld(), point.x() + .5, point.y() + .3, point.z() + .5);
        if (at.distanceSquared(player.getLocation()) <= 64 * 64) player.spawnParticle(particle, at, 4, .1, .4, .1, 0);
    }

    private static void outline(Player player, Box b, Particle particle) {
        double[][] corners = {{b.minX(), b.minY(), b.minZ()}, {b.maxX() + 1D, b.minY(), b.minZ()}, {b.minX(), b.maxY() + 1D, b.minZ()},
            {b.maxX() + 1D, b.maxY() + 1D, b.minZ()}, {b.minX(), b.minY(), b.maxZ() + 1D}, {b.maxX() + 1D, b.minY(), b.maxZ() + 1D},
            {b.minX(), b.maxY() + 1D, b.maxZ() + 1D}, {b.maxX() + 1D, b.maxY() + 1D, b.maxZ() + 1D}};
        int[][] edges = {{0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3}, {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}};
        Location at = player.getLocation();
        for (int[] edge : edges) {
            double[] a = corners[edge[0]], c = corners[edge[1]];
            double length = Math.abs(c[0] - a[0]) + Math.abs(c[1] - a[1]) + Math.abs(c[2] - a[2]);
            int steps = (int) Math.min(48, Math.max(1, Math.ceil(length / 1.5)));
            for (int step = 0; step <= steps; step++) {
                double t = (double) step / steps, x = a[0] + (c[0] - a[0]) * t, y = a[1] + (c[1] - a[1]) * t, z = a[2] + (c[2] - a[2]) * t;
                double dx = x - at.getX(), dy = y - at.getY(), dz = z - at.getZ();
                if (dx * dx + dy * dy + dz * dz <= 48 * 48) player.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private Optional<ExpeditionDefinition> templateAt(World world) {
        return service.definitions().stream().filter(definition -> definition.templateWorld().equals(world.getName())).findFirst();
    }

    /** The expedition whose template the sender stands in, else the last one they edited. */
    private ExpeditionDefinition current(CommandSender sender) {
        if (sender instanceof Player player) {
            Optional<ExpeditionDefinition> here = templateAt(player.getWorld());
            if (here.isPresent()) {
                editing.put(player.getUniqueId(), here.get().id());
                return here.get();
            }
            String last = editing.get(player.getUniqueId());
            if (last != null && service.definition(last).isPresent()) return service.definition(last).get();
        }
        throw new IllegalArgumentException("Stand in an expedition template or run /expedition edit <id> first.");
    }

    private ExpeditionDefinition require(String id) {
        return service.definition(id).orElseThrow(() -> new IllegalArgumentException("No expedition called " + id + "."));
    }

    private YamlConfiguration yaml(ExpeditionDefinition definition) {
        YamlConfiguration yaml = service.file(definition.id());
        if (yaml == null) throw new IllegalStateException("The file for " + definition.id() + " isn't loaded.");
        return yaml;
    }

    private static ConfigurationSection rooms(YamlConfiguration yaml) {
        ConfigurationSection rooms = yaml.getConfigurationSection("rooms");
        return rooms != null ? rooms : yaml.createSection("rooms");
    }

    private interface RoomEdit { void apply(ConfigurationSection room); }

    private void editRoom(Player player, String rawRoom, RoomEdit edit, String message) throws IOException {
        ExpeditionDefinition definition = current(player);
        String id = roomId(rawRoom);
        ConfigurationSection room = rooms(yaml(definition)).getConfigurationSection(id);
        if (room == null) throw new IllegalArgumentException("No room called " + id + ". Rooms: " + String.join(", ", definition.rooms().keySet()) + ".");
        edit.apply(room);
        save(player, definition.id(), yaml(definition), message);
    }

    private void save(Player player, String id, YamlConfiguration yaml, String message) throws IOException {
        try {
            service.store(id, yaml);
        } catch (IllegalArgumentException invalid) {
            service.reload(); // Discard the in-memory edit; the file on disk is unchanged.
            throw invalid;
        }
        Chat.success(player, message);
        ExpeditionDefinition definition = require(id);
        service.issues(id).stream().filter(ExpeditionValidator.Issue::error).findFirst().ifPresentOrElse(
            next -> Chat.send(player, "<gray>Next:</gray> <white>" + Chat.escape(next.message()) + "</white> "
                + (next.fix().startsWith("/") ? Chat.syntax(next.fix()) : "<dark_gray>" + Chat.escape(next.fix()) + "</dark_gray>")),
            () -> Chat.send(player, "<green>No setup errors.</green> " + Chat.command("expedition test")
                + (definition.published() ? "" : " <gray>then</gray> " + Chat.command("expedition publish"))));
    }

    private Point here(Player player) {
        Location at = player.getLocation();
        return new Point(at.getBlockX(), at.getBlockY(), at.getBlockZ(), Math.round(at.getYaw() / 45F) * 45F);
    }

    private Box selection(Player player) {
        var selection = plugin.zoneCommands().selections().selection(player.getUniqueId())
            .orElseThrow(() -> new IllegalArgumentException("Select two corners with the Expedition Wand first."));
        if (!selection.worldId().equals(player.getWorld().getUID())) throw new IllegalArgumentException("Your selection is in another world.");
        var b = selection.bounds();
        return Box.of(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }

    private static String roomId(String raw) {
        String id = raw.toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_]{1,32}")) throw new IllegalArgumentException("Room names use lowercase letters, digits or underscores.");
        return id;
    }

    private static String mobId(String raw) {
        String id = raw.toLowerCase(Locale.ROOT);
        return id.contains(":") ? id : "vanilla:" + id;
    }

    private static String requireText(String text) {
        if (text.isBlank()) throw new IllegalArgumentException("Type the text after the field name.");
        return text;
    }

    List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ExpeditionService.ADMIN)) return List.of();
        String action = args[0].toLowerCase(Locale.ROOT);
        Optional<ExpeditionDefinition> definition = Optional.empty();
        try { definition = Optional.of(current(sender)); } catch (IllegalArgumentException none) { }
        List<String> rooms = definition.map(value -> List.copyOf(value.rooms().keySet())).orElse(List.of());
        List<String> ids = service.definitions().stream().map(ExpeditionDefinition::id).toList();
        if (args.length == 2) return switch (action) {
            case "create" -> List.of("<id>");
            case "edit", "check", "publish", "unpublish", "test" -> ids;
            case "set" -> List.of("spawn", "exit", "title", "description", "level", "party", "time", "icon", "modifiers");
            case "room" -> List.of("add", "remove", "title", "list");
            case "gate", "checkpoint", "point", "wave", "survive", "boss" -> rooms;
            case "loot" -> {
                List<String> options = new ArrayList<>(List.of("none", "marks", "item"));
                plugin.rewardDrops().profiles().all().forEach(profile -> options.add(profile.id()));
                yield options;
            }
            default -> List.of();
        };
        if (args.length == 3) return switch (action) {
            case "create" -> List.of("overworld", "nether", "end", "from");
            case "room" -> rooms;
            case "point" -> List.of("add", "list", "remove", "clear");
            case "gate" -> List.of("clear");
            case "wave", "boss" -> {
                List<String> mobs = new ArrayList<>(List.of("clear"));
                plugin.mobs().ids().forEach(id -> mobs.add("custom:" + id));
                net.nerdsmp.paper.zone.ZoneMobTypes.vanillaIds().forEach(mobs::add);
                yield mobs;
            }
            case "test" -> service.definition(args[1]).map(value -> service.tiersFor(value).stream().map(Tier::id).toList()).orElse(List.of());
            case "set" -> args[1].equalsIgnoreCase("modifiers") ? Arrays.stream(Modifier.values()).map(Modifier::key).toList() : List.of();
            default -> List.of();
        };
        if (args.length == 4 && action.equals("create") && args[2].equalsIgnoreCase("from"))
            return Bukkit.getWorlds().stream().map(World::getName).toList();
        return List.of();
    }

    @Override public void close() { previews.cancel(); }
}
