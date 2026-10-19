package net.nerdsmp.paper.forge.arcane;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.anticheat.ArcaneEffectScope;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** {@code /arcane} for players and {@code /forgeadmin} for staff. Each subcommand is listed once and drives usage, help and completion. */
public final class ArcaneCommands implements CommandExecutor, TabCompleter {
    public static final String ADMIN_PERMISSION = "nerdsmp.forge.admin";
    static final List<String> PLAYER_SUBCOMMANDS = List.of("codex", "research", "imbue", "mastery", "help");
    static final List<String> ADMIN_SUBCOMMANDS = List.of("help", "status", "validate", "reload", "debug", "migration",
        "anticheat", "telemetry", "grant", "rollout");
    static final String PLAYER_USAGE = "/arcane [" + String.join("|", PLAYER_SUBCOMMANDS) + "]";
    static final String ADMIN_USAGE = "/forgeadmin <" + String.join("|", ADMIN_SUBCOMMANDS) + ">";

    private final NerdSmpPlugin plugin;
    private final ArcaneForge forge;
    private final ArcaneMenus menus;
    private final ArcaneSignatureService signatures;

    public ArcaneCommands(NerdSmpPlugin plugin, ArcaneForge forge, ArcaneMenus menus, ArcaneSignatureService signatures) {
        this.plugin = plugin;
        this.forge = forge;
        this.menus = menus;
        this.signatures = signatures;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("forgeadmin")) return admin(sender, args);
        return player(sender, args);
    }

    // ---------------------------------------------------------------------------------------------
    // /arcane
    // ---------------------------------------------------------------------------------------------

    private boolean player(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "codex" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("help")) {
            Chat.send(sender, "<light_purple><bold>Arcane Codex</bold></light_purple> <gray>· enchanting without the guesswork</gray>");
            Chat.info(sender, Chat.command("arcane") + " <gray>opens the Codex: everything you have discovered, clues and help.</gray>");
            Chat.info(sender, Chat.command("arcane research") + " <gray>combines gems to discover enchantments. At an enchanting table.</gray>");
            Chat.info(sender, Chat.command("arcane imbue") + " <gray>binds a discovered enchantment at an exact level. At an enchanting table.</gray>");
            Chat.info(sender, Chat.command("arcane mastery") + " <gray>lists your mastery progress.</gray>");
            Chat.info(sender, "Right-click an enchanting table for the random offers, which are cheaper.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            Chat.playersOnly(sender);
            return true;
        }
        if (args.length > 1 || !PLAYER_SUBCOMMANDS.contains(sub)) {
            Chat.usage(sender, PLAYER_USAGE);
            return true;
        }
        switch (sub) {
            case "codex" -> menus.openCodex(player, false);
            case "research" -> menus.openResearch(player, false, List.of(), false);
            case "imbue" -> menus.openImbue(player, false);
            case "mastery" -> menus.showMastery(player);
            default -> Chat.usage(sender, PLAYER_USAGE);
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // /forgeadmin
    // ---------------------------------------------------------------------------------------------

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            Chat.noPermission(sender);
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> {
                Chat.send(sender, "<gold><bold>Arcane Forge administration</bold></gold>");
                Chat.info(sender, Chat.syntax("/forgeadmin status") + " <gray>feature switches and live state.</gray>");
                Chat.info(sender, Chat.syntax("/forgeadmin validate") + " <gray>checks definitions and the config on disk without applying it.</gray>");
                Chat.info(sender, Chat.syntax("/forgeadmin reload") + " <gray>applies the arcane-forge config only if it is fully valid.</gray>");
                Chat.info(sender, Chat.syntax("/forgeadmin debug <player>") + " <gray>shows one player's knowledge and held item.</gray>");
                Chat.info(sender, Chat.syntax("/forgeadmin migration <status|dry-run> [player]") + " <gray>read-only compatibility reports.</gray>");
                Chat.info(sender, Chat.syntax("/forgeadmin anticheat status") + " <gray>trusted-effect counters.</gray>");
                Chat.info(sender, Chat.syntax("/forgeadmin telemetry") + " <gray>operation counters since start.</gray>");
                Chat.info(sender, Chat.syntax("/forgeadmin grant <player> <id> confirm") + " <gray>grants knowledge (audited).</gray>");
                Chat.info(sender, Chat.syntax("/forgeadmin rollout core [confirm]") + " <gray>previews or enables the core enchanting workflow, with a config backup.</gray>");
            }
            case "status" -> status(sender);
            case "validate" -> validate(sender);
            case "reload" -> reload(sender);
            case "debug" -> debug(sender, args);
            case "migration" -> migration(sender, args);
            case "anticheat" -> anticheat(sender);
            case "telemetry" -> telemetry(sender);
            case "grant" -> grant(sender, args);
            case "rollout" -> rollout(sender, args);
            default -> Chat.usage(sender, ADMIN_USAGE);
        }
        return true;
    }

    private void status(CommandSender sender) {
        ArcaneSettings settings = forge.settings();
        Chat.send(sender, "<gold><bold>Arcane Forge status</bold></gold>");
        Chat.info(sender, "Master switch " + flag(settings.enabled()) + " · Codex " + flag(settings.codex())
            + " · Research " + flag(settings.research().enabled()) + " · Imbuement " + flag(settings.imbuement().enabled()));
        Chat.info(sender, "Overcharge " + flag(settings.overcharge().enabled()) + " · Signatures " + flag(settings.signatures().enabled())
            + " · Anticheat bridge " + flag(settings.anticheatBridge()));
        for (ArcaneSignature signature : ArcaneSignature.values()) {
            Chat.info(sender, "  " + signature.displayName() + " " + flag(settings.signatures().active(signature)));
        }
        Chat.info(sender, "Bloodletter " + flag(settings.effects().bloodletter()) + " · Veinseeker " + flag(settings.effects().veinseeker())
            + " · Chain Harvest " + flag(settings.effects().chainHarvest()) + " · Corruption " + flag(settings.corruption().enabled())
            + " · Attunement " + flag(settings.attunement().enabled()));
        Chat.info(sender, "Definitions <white>" + forge.registry().all().size() + "</white> · config problems <white>"
            + settings.errors().size() + "</white> · runtime " + signatures.stateSummary()
            + (plugin.arcaneEffects() == null ? "" : ", " + plugin.arcaneEffects().stateSummary()));
    }

    private void rollout(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3 || !args[1].equalsIgnoreCase("core")
            || args.length == 3 && !args[2].equalsIgnoreCase("confirm")) {
            Chat.usage(sender, "/forgeadmin rollout core [confirm]");
            return;
        }
        try {
            var config = new File(plugin.getDataFolder(), "config.yml").toPath();
            ArcaneCoreRollout.Plan plan = ArcaneCoreRollout.preview(config);
            if (args.length == 2) {
                Chat.info(sender, "Enable Research, Chosen Imbuement, Attunement, Corruption and Overcharge.");
                Chat.notice(sender, "Effect enchantments, Arcane Signatures, anticheat settings, prices and player data stay unchanged.");
                Chat.info(sender, "Config changes <white>" + plan.changes().size() + "</white>. A dated backup is kept before saving.");
                Chat.info(sender, "Apply with " + Chat.command("forgeadmin rollout core confirm") + ".");
                return;
            }
            var backup = ArcaneCoreRollout.commit(config, plan);
            forge.apply(plan.settings()); // Fully validated; disk is committed before publication.
            for (String feature : ArcaneCoreRollout.SWITCHES) plugin.getConfig().set("arcane-forge." + feature + ".enabled", true);
            audit(sender, "rollout core", plan.changes().size() + " switches changed; backup " + (backup == null ? "not needed" : backup.getFileName()));
            Chat.success(sender, "Core enchanting is enabled. Existing gear and discoveries are unchanged.");
            if (!plan.settings().enabled()) Chat.notice(sender, "The Arcane Forge master switch is still off. This rollout did not override it.");
            if (backup != null) Chat.info(sender, "Config backup <white>" + Chat.escape(backup.getFileName().toString()) + "</white>.");
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException failure) {
            Chat.error(sender, "Core enchanting was not enabled. " + Chat.escape(failure.getMessage()) + ".");
            plugin.getLogger().warning("Arcane core rollout refused: " + failure.getMessage());
        }
    }

    private void validate(CommandSender sender) {
        List<String> problems = new ArrayList<>(ArcaneRegistry.validate(forge.registry().all()));
        YamlConfiguration disk = readDiskConfig(sender);
        if (disk == null) return;
        problems.addAll(ArcaneSettings.from(disk).errors());
        if (problems.isEmpty()) {
            Chat.success(sender, "Definitions and the arcane-forge config on disk are valid.");
        } else {
            Chat.error(sender, problems.size() + " problem" + (problems.size() == 1 ? "" : "s") + " found:");
            problems.forEach(problem -> Chat.info(sender, "  " + Chat.escape(problem)));
        }
    }

    private void reload(CommandSender sender) {
        YamlConfiguration disk = readDiskConfig(sender);
        if (disk == null) return;
        ArcaneSettings candidate = ArcaneSettings.from(disk);
        if (!candidate.errors().isEmpty()) {
            Chat.error(sender, "Not reloaded: the arcane-forge config has " + candidate.errors().size()
                + " problem(s). The live settings are unchanged. Run " + Chat.command("forgeadmin validate") + ".");
            return;
        }
        forge.apply(candidate);
        audit(sender, "reload", "arcane-forge settings reloaded");
        Chat.success(sender, "Arcane Forge settings reloaded. Operations already running finish with the old settings.");
    }

    private YamlConfiguration readDiskConfig(CommandSender sender) {
        YamlConfiguration disk = new YamlConfiguration();
        if (plugin.getConfig().getDefaults() != null) disk.setDefaults(plugin.getConfig().getDefaults());
        try {
            disk.load(new File(plugin.getDataFolder(), "config.yml"));
            return disk;
        } catch (IOException | InvalidConfigurationException failure) {
            Chat.error(sender, "config.yml could not be read, so nothing changed: " + Chat.escape(String.valueOf(failure.getMessage())));
            return null;
        }
    }

    private void debug(CommandSender sender, String[] args) {
        if (args.length != 2) {
            Chat.usage(sender, "/forgeadmin debug <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        AccountId account = target == null ? null : forge.account(target).orElse(null);
        if (account == null) {
            Chat.error(sender, "That player must be online with a loaded account.");
            return;
        }
        Chat.send(sender, "<gold>Arcane knowledge of</gold> <white>" + Chat.escape(target.getName()) + "</white>");
        for (String line : forge.describe(account).split("\n")) {
            if (!line.isBlank()) Chat.info(sender, Chat.escape(line));
        }
        ArcaneForge.ItemView view = forge.view(target.getInventory().getItemInMainHand());
        Chat.info(sender, view.gear() ? "Held: load " + view.load() + "/" + view.capacity() + (view.grandfathered() ? " (grandfathered)" : "")
            + ", signature " + (view.signature() == null ? "none" : view.signature().id()) + ", instability " + view.instability()
            : "Held: not gear.");
    }

    private void migration(CommandSender sender, String[] args) {
        String mode = args.length < 2 ? "status" : args[1].toLowerCase(Locale.ROOT);
        switch (mode) {
            case "status" -> {
                long seeded = 0;
                long total = 0;
                for (AccountId account : plugin.playerData().knownAccounts()) {
                    total++;
                    if (plugin.playerData().read(account, yaml -> yaml.getBoolean(ArcaneForge.PROFILE_PATH + ".seeded", false))) seeded++;
                }
                Chat.info(sender, "Items: no rewrite needed. Forge 2.0 reads the existing item format (schema 5) as is and keeps unreadable entries.");
                Chat.info(sender, "Knowledge: " + seeded + " of " + total + " accounts have an Arcane profile. The rest are created, and seeded once from their gear, on first use.");
            }
            case "dry-run" -> {
                if (args.length != 3) {
                    Chat.usage(sender, "/forgeadmin migration dry-run <player>");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    Chat.error(sender, "That player must be online.");
                    return;
                }
                forge.dryRun(target).forEach(line -> Chat.info(sender, Chat.escape(line)));
            }
            case "apply" -> Chat.notice(sender, "There is nothing to apply: Forge 2.0 needs no item or data migration.");
            default -> Chat.usage(sender, "/forgeadmin migration <status|dry-run|apply> [player]");
        }
    }

    private void anticheat(CommandSender sender) {
        ArcaneEffectScope.Stats stats = ArcaneEffectScope.stats();
        Chat.info(sender, "Bridge " + flag(forge.settings().anticheatBridge()) + ". Trusted arcs granted <white>" + stats.granted()
            + "</white>, recognised by listeners <white>" + stats.recognised() + "</white>, refused <white>" + stats.refused() + "</white>.");
        Chat.info(sender, "Trusted chain breaks <white>" + net.nerdsmp.paper.anticheat.ArcaneBreakScope.granted()
            + "</white>, refused <white>" + net.nerdsmp.paper.anticheat.ArcaneBreakScope.refused() + "</white>.");
        Chat.info(sender, "Each scope covers one player and one target or block for one synchronous call. No movement or blanket exemptions exist.");
    }

    private void telemetry(CommandSender sender) {
        Map<ArcaneTelemetry.Metric, Long> snapshot = forge.telemetry().snapshot();
        List<String> parts = new ArrayList<>();
        snapshot.forEach((metric, value) -> {
            if (value > 0) parts.add(metric.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " " + value);
        });
        Chat.info(sender, parts.isEmpty() ? "No Arcane Forge activity since start." : String.join(", ", parts));
    }

    private void grant(CommandSender sender, String[] args) {
        if (args.length != 4 || !args[3].equalsIgnoreCase("confirm")) {
            Chat.usage(sender, "/forgeadmin grant <player> <id> confirm");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        AccountId account = target == null ? null : forge.account(target).orElse(null);
        ArcaneDefinition definition = forge.registry().byId(args[2].toLowerCase(Locale.ROOT)).orElse(null);
        if (account == null || definition == null) {
            Chat.error(sender, "Use an online player with a loaded account and a known id.");
            return;
        }
        if (!forge.grant(account, definition)) {
            Chat.error(sender, "The knowledge could not be saved.");
            return;
        }
        audit(sender, "grant", definition.id() + " to " + target.getName() + " (" + account + ")");
        Chat.success(sender, "Granted " + Chat.escape(definition.displayName()) + " to " + Chat.escape(target.getName()) + ".");
    }

    private void audit(CommandSender sender, String action, String detail) {
        String line = Instant.now() + " | " + sender.getName() + " | " + action + " | " + detail + System.lineSeparator();
        plugin.getLogger().info("[Arcane audit] " + line.strip());
        try {
            File file = new File(plugin.getDataFolder(), "arcane/admin-audit.log");
            Files.createDirectories(file.getParentFile().toPath());
            Files.writeString(file.toPath(), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            plugin.getLogger().warning("Could not append to the Arcane audit log: " + failure.getMessage());
        }
    }

    private static String flag(boolean on) {
        return on ? "<green>on</green>" : "<red>off</red>";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> choices;
        if (command.getName().equalsIgnoreCase("forgeadmin")) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) return List.of();
            choices = switch (args.length) {
                case 1 -> ADMIN_SUBCOMMANDS;
                case 2 -> switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "migration" -> List.of("status", "dry-run", "apply");
                    case "anticheat" -> List.of("status");
                    case "rollout" -> List.of("core");
                    case "debug", "grant" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                    default -> List.of();
                };
                case 3 -> switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "migration" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                    case "grant" -> forge.registry().all().stream().map(ArcaneDefinition::id).toList();
                    case "rollout" -> args[1].equalsIgnoreCase("core") ? List.of("confirm") : List.of();
                    default -> List.of();
                };
                case 4 -> args[0].equalsIgnoreCase("grant") ? List.of("confirm") : List.of();
                default -> List.of();
            };
        } else {
            choices = args.length == 1 ? PLAYER_SUBCOMMANDS : List.of();
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
