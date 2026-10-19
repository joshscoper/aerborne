package net.nerdsmp.paper.staff;

import com.google.common.collect.HashMultimap;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.presentation.VisualFormat;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class StaffManager implements Listener, AutoCloseable {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM d HH:mm 'UTC'")
        .withLocale(Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final int[] PLAYER_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final int[] RECORD_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private final NerdSmpPlugin plugin;
    private final File dataFile;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, MuteRecord> mutes = new ConcurrentHashMap<>();
    private final Map<UUID, FreezeRecord> freezes = new ConcurrentHashMap<>();
    private final Map<UUID, ReportRecord> reports = new LinkedHashMap<>();
    private final Map<UUID, List<HistoryRecord>> histories = new HashMap<>();
    private final List<AuditRecord> audit = new ArrayList<>();
    private final Map<UUID, Long> reportCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> freezeNotices = new ConcurrentHashMap<>();
    private final long reportCooldownMillis;
    private static final Duration MAXIMUM_DURATION = Duration.ofDays(30);
    private final int defaultMuteMinutes;

    public StaffManager(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        dataFile = new File(plugin.getDataFolder(), "staff-management.yml");
        reportCooldownMillis = Math.max(10L,
            plugin.getConfig().getLong("moderation.report-cooldown-seconds", 60L)) * 1000L;
        defaultMuteMinutes = Math.max(1,
            plugin.getConfig().getInt("moderation.default-mute-minutes", 30));
        load();
    }

    public boolean handleReportCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player reporter)) {
            Chat.playersOnly(sender);
            return true;
        }
        if (args.length < 2) {
            Chat.usage(reporter, "/report <player> <reason>");
            return true;
        }
        OfflinePlayer target = findKnown(args[0]);
        String reason = reason(args, 1);
        if (target == null || target.getUniqueId().equals(reporter.getUniqueId())) {
            reporter.sendRichMessage("<red>Choose another known player.</red>");
            return true;
        }
        if (!validReason(reason)) {
            reporter.sendRichMessage("<red>Give a clear reason between 3 and 180 characters.</red>");
            return true;
        }
        long now = System.currentTimeMillis();
        long availableAt = reportCooldowns.getOrDefault(reporter.getUniqueId(), 0L);
        if (availableAt > now) {
            reporter.sendRichMessage("<red>You can file another report in "
                + Math.max(1L, (availableAt - now + 999L) / 1000L) + " seconds.</red>");
            return true;
        }
        reportCooldowns.put(reporter.getUniqueId(), now + reportCooldownMillis);
        ReportRecord report = new ReportRecord(UUID.randomUUID(), reporter.getUniqueId(), reporter.getName(),
            target.getUniqueId(), safeName(target), reason, Instant.now(), ReportStatus.OPEN,
            null, null, null, null);
        synchronized (this) {
            reports.put(report.id(), report);
            appendHistory(report.targetId(), new HistoryRecord("REPORT", reporter.getName(), reason, report.createdAt()));
            audit.add(new AuditRecord("REPORT_FILED", reporter.getUniqueId(), reporter.getName(),
                report.targetId(), report.targetName(), reason, report.createdAt()));
            save();
        }
        reporter.sendRichMessage("<green>Report " + shortId(report.id()) + " filed. Staff have been notified.</green>");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("nerdsmp.staff")) {
                player.sendRichMessage("<red><bold>New report " + shortId(report.id()) + "</bold></red> <white>"
                    + escape(report.targetName()) + "</white> <gray>— " + escape(reason) + "</gray>");
            }
        }
        return true;
    }

    public boolean handleStaffCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nerdsmp.staff")) {
            sender.sendRichMessage("<red>You do not have access to the staff desk.</red>");
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player player) {
                openDashboard(player);
            } else {
                help(sender);
            }
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("help")) {
            help(sender);
            return true;
        }
        if (action.equals("reports")) {
            if (sender instanceof Player player) {
                openReports(player);
            } else {
                showOpenReports(sender);
            }
            return true;
        }
        if (action.equals("audit")) {
            showAudit(sender);
            return true;
        }
        if (action.equals("resolve") && args.length >= 2) {
            return resolveReport(sender, args[1], args.length >= 3 ? reason(args, 2) : "Reviewed by staff");
        }
        if (args.length < 2) {
            help(sender);
            return true;
        }
        OfflinePlayer target = findKnown(args[1]);
        if (target == null) {
            sender.sendRichMessage("<red>That player has not joined this server.</red>");
            return true;
        }
        if (action.equals("player")) {
            if (sender instanceof Player player) {
                openPlayer(player, target);
            } else {
                showHistory(sender, target);
            }
            return true;
        }
        if (action.equals("inventory")) {
            Player staff = sender instanceof Player player ? player : null;
            Player online = target.getPlayer();
            if (staff == null || online == null) {
                sender.sendRichMessage("<red>Inventory review needs an in-game staff member and an online target.</red>");
            } else {
                openInventory(staff, online);
            }
            return true;
        }
        if (action.equals("history")) {
            if (sender instanceof Player player) {
                openHistory(player, target);
            } else {
                showHistory(sender, target);
            }
            return true;
        }
        if (!sender.hasPermission("nerdsmp.staff.moderate")) {
            sender.sendRichMessage("<red>This action requires nerdsmp.staff.moderate.</red>");
            return true;
        }
        return switch (action) {
            case "note" -> addTextRecord(sender, target, "NOTE", args, 2, false);
            case "warn" -> addTextRecord(sender, target, "WARNING", args, 2, true);
            case "mute" -> muteCommand(sender, target, args);
            case "unmute" -> unmute(sender, target);
            case "freeze" -> freeze(sender, target, args.length >= 3 ? reason(args, 2) : "Staff review");
            case "unfreeze" -> unfreeze(sender, target);
            case "kick" -> kick(sender, target, args.length >= 3 ? reason(args, 2) : "Removed by staff");
            case "ban" -> banCommand(sender, target, args);
            case "unban" -> unban(sender, target);
            default -> {
                help(sender);
                yield true;
            }
        };
    }

    public List<String> tabComplete(String command, String[] args) {
        if (command.equalsIgnoreCase("report")) {
            return args.length == 1 ? onlineNames(args[0]) : List.of();
        }
        if (!command.equalsIgnoreCase("staff")) {
            return List.of();
        }
        List<String> choices = args.length == 1
            ? List.of("player", "reports", "inventory", "history", "note", "warn", "mute", "unmute",
                "freeze", "unfreeze", "kick", "ban", "unban", "resolve", "audit", "help")
            : args.length == 2 && List.of("player", "inventory", "history", "note", "warn", "mute",
                "unmute", "freeze", "unfreeze", "kick", "ban", "unban").contains(args[0].toLowerCase(Locale.ROOT))
                ? onlineNames(args[1])
                : args.length == 2 && args[0].equalsIgnoreCase("resolve")
                    ? openReports().stream().map(report -> shortId(report.id())).toList() : List.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private boolean addTextRecord(
        CommandSender sender,
        OfflinePlayer target,
        String type,
        String[] args,
        int reasonIndex,
        boolean notify
    ) {
        if (notify && !allowedTarget(sender, target)) return true;
        return reply(sender, () -> applyTextRecord(actor(sender), target, type, reason(args, reasonIndex), notify));
    }

    private boolean muteCommand(CommandSender sender, OfflinePlayer target, String[] args) {
        if (!allowedTarget(sender, target)) return true;
        if (args.length < 4) {
            Chat.usage(sender, "/staff mute <player> <duration> <reason>");
            Chat.info(sender, "Durations look like <white>30m</white>, <white>2h</white> or <white>7d</white>; a bare number is minutes.");
            return true;
        }
        return reply(sender, () -> applyMute(actor(sender), target, args[2], reason(args, 3)));
    }

    private boolean banCommand(CommandSender sender, OfflinePlayer target, String[] args) {
        if (!allowedTarget(sender, target)) return true;
        if (args.length < 4) {
            Chat.usage(sender, "/staff ban <player> <duration|perm> <reason>");
            Chat.info(sender, "Durations look like <white>30m</white>, <white>2h</white> or <white>7d</white>; a bare number is minutes.");
            return true;
        }
        return reply(sender, () -> applyBan(actor(sender), target, args[2], reason(args, 3)));
    }

    private boolean unmute(CommandSender sender, OfflinePlayer target) {
        if (!allowedTarget(sender, target)) return true;
        return reply(sender, () -> applyUnmute(actor(sender), target));
    }

    private boolean freeze(CommandSender sender, OfflinePlayer target, String detail) {
        if (!allowedTarget(sender, target)) return true;
        return reply(sender, () -> applyFreeze(actor(sender), target, detail));
    }

    private boolean unfreeze(CommandSender sender, OfflinePlayer target) {
        if (!allowedTarget(sender, target)) return true;
        return reply(sender, () -> applyUnfreeze(actor(sender), target));
    }

    private boolean kick(CommandSender sender, OfflinePlayer target, String detail) {
        if (!allowedTarget(sender, target)) return true;
        return reply(sender, () -> applyKick(actor(sender), target, detail));
    }

    private boolean unban(CommandSender sender, OfflinePlayer target) {
        if (!allowedTarget(sender, target)) return true;
        return reply(sender, () -> applyUnban(actor(sender), target));
    }

    /** Reads {@code 30m}, {@code 2h} or {@code 7d}; a bare number is minutes, as before. Capped at 30 days. */
    static Optional<Duration> moderationDuration(String raw) {
        return Chat.duration(raw, ChronoUnit.MINUTES).filter(duration -> duration.compareTo(MAXIMUM_DURATION) <= 0);
    }

    /** Sends the outcome of a moderation change; refusals arrive as IllegalArgumentException messages. */
    private boolean reply(CommandSender sender, java.util.function.Supplier<String> change) {
        try {
            sender.sendRichMessage("<green>" + escape(change.get()) + "</green>");
        } catch (IllegalArgumentException refused) {
            sender.sendRichMessage("<red>" + escape(refused.getMessage()) + "</red>");
        }
        return true;
    }

    private String applyTextRecord(Actor actor, OfflinePlayer target, String type, String detail, boolean notify) {
        requireReason(detail);
        recordMutation(actor, target, type, detail);
        if (notify && target.getPlayer() != null) {
            target.getPlayer().sendRichMessage("<red><bold>Staff warning</bold></red> <white>" + escape(detail) + "</white>");
        }
        return title(type) + " recorded for " + safeName(target) + ".";
    }

    private String applyMute(Actor actor, OfflinePlayer target, String length, String detail) {
        Optional<Duration> duration = moderationDuration(length);
        if (duration.isEmpty() || !validReason(detail)) {
            throw new IllegalArgumentException("Use a duration from 1m to 30d, such as 30m, 2h or 7d, and give a clear reason.");
        }
        Instant expires = Instant.now().plus(duration.orElseThrow());
        mutes.put(target.getUniqueId(), new MuteRecord(safeName(target), actor.name(), detail, Instant.now(), expires));
        recordMutation(actor, target, "MUTE", Chat.duration(duration.orElseThrow()) + " — " + detail);
        if (target.getPlayer() != null) {
            target.getPlayer().sendRichMessage("<red>You are muted until " + TIME.format(expires) + ".</red> "
                + "<gray>" + escape(detail) + "</gray>");
        }
        return "Muted " + safeName(target) + " until " + TIME.format(expires) + ".";
    }

    private String applyUnmute(Actor actor, OfflinePlayer target) {
        if (mutes.remove(target.getUniqueId()) == null) {
            throw new IllegalArgumentException("That player is not muted.");
        }
        recordMutation(actor, target, "UNMUTE", "Mute removed");
        if (target.getPlayer() != null) {
            target.getPlayer().sendRichMessage("<green>Your chat mute has been removed.</green>");
        }
        return "Unmuted " + safeName(target) + ".";
    }

    private String applyFreeze(Actor actor, OfflinePlayer target, String detail) {
        requireReason(detail);
        freezes.put(target.getUniqueId(), new FreezeRecord(safeName(target), actor.name(), detail, Instant.now()));
        recordMutation(actor, target, "FREEZE", detail);
        if (target.getPlayer() != null) {
            target.getPlayer().sendRichMessage("<red><bold>You have been frozen.</bold></red> "
                + "<gray>Wait for staff instructions.</gray>");
        }
        return "Froze " + safeName(target) + ".";
    }

    private String applyUnfreeze(Actor actor, OfflinePlayer target) {
        if (freezes.remove(target.getUniqueId()) == null) {
            throw new IllegalArgumentException("That player is not frozen.");
        }
        recordMutation(actor, target, "UNFREEZE", "Freeze removed");
        if (target.getPlayer() != null) {
            target.getPlayer().sendRichMessage("<green>You are no longer frozen.</green>");
        }
        return "Unfroze " + safeName(target) + ".";
    }

    private String applyKick(Actor actor, OfflinePlayer target, String detail) {
        Player online = target.getPlayer();
        if (online == null) {
            throw new IllegalArgumentException("That player is offline.");
        }
        requireReason(detail);
        recordMutation(actor, target, "KICK", detail);
        online.kick(message("<red><bold>Disconnected by staff</bold></red>\n<gray>" + escape(detail) + "</gray>"));
        return "Removed " + safeName(target) + " from the server.";
    }

    /** Uses the server's own profile ban list, so vanilla /pardon and the console stay authoritative. */
    private String applyBan(Actor actor, OfflinePlayer target, String length, String detail) {
        boolean permanent = Chat.isPermanent(length) || length.equals("0");
        Optional<Duration> duration = permanent ? Optional.empty() : moderationDuration(length);
        if ((!permanent && duration.isEmpty()) || !validReason(detail)) {
            throw new IllegalArgumentException("Use a duration from 1m to 30d, such as 30m, 2h or 7d, or perm, and give a clear reason.");
        }
        if (target.isBanned()) {
            throw new IllegalArgumentException("That player is already banned.");
        }
        Instant expires = permanent ? null : Instant.now().plus(duration.orElseThrow());
        target.ban(detail, expires, actor.name());
        recordMutation(actor, target, "BAN", (permanent ? "permanent" : Chat.duration(duration.orElseThrow())) + " — " + detail);
        Player online = target.getPlayer();
        if (online != null) {
            online.kick(message("<red><bold>You have been banned</bold></red>\n<gray>" + escape(detail) + "</gray>"
                + (expires == null ? "" : "\n<gray>Ends " + TIME.format(expires) + "</gray>")));
        }
        return "Banned " + safeName(target) + (expires == null ? " permanently." : " until " + TIME.format(expires) + ".");
    }

    private String applyUnban(Actor actor, OfflinePlayer target) {
        if (!target.isBanned()) {
            throw new IllegalArgumentException("That player is not banned.");
        }
        Bukkit.getBanList(io.papermc.paper.ban.BanListType.PROFILE).pardon(target.getPlayerProfile());
        recordMutation(actor, target, "UNBAN", "Ban removed");
        return "Unbanned " + safeName(target) + ".";
    }

    private void requireReason(String detail) {
        if (!validReason(detail)) {
            throw new IllegalArgumentException("Add a clear reason between 3 and 180 characters.");
        }
    }

    private boolean allowedTarget(CommandSender sender, OfflinePlayer target) {
        if (plugin.staffPermissions().canModerate(sender, target)) return true;
        sender.sendMessage("You cannot moderate yourself or a staff member of equal or higher tier. Use the server console for owner actions.");
        return false;
    }
    private boolean resolveReport(CommandSender sender, String prefix, String resolution) {
        if (!sender.hasPermission("nerdsmp.staff.moderate")) {
            sender.sendRichMessage("<red>This action requires nerdsmp.staff.moderate.</red>");
            return true;
        }
        List<ReportRecord> matches = openReports().stream()
            .filter(report -> normalized(report.id()).startsWith(prefix.replace("-", "").toLowerCase(Locale.ROOT)))
            .limit(2).toList();
        if (matches.size() != 1) {
            sender.sendRichMessage("<red>Use a unique open report id.</red>");
            return true;
        }
        return reply(sender, () -> applyResolve(actor(sender), matches.getFirst(), resolution));
    }

    private String applyResolve(Actor actor, ReportRecord current, String resolution) {
        ReportRecord resolved = current.resolve(actor.id(), actor.name(), Instant.now(), resolution);
        synchronized (this) {
            reports.put(resolved.id(), resolved);
            appendHistory(resolved.targetId(), new HistoryRecord("REPORT_RESOLVED", actor.name(),
                shortId(resolved.id()) + " — " + resolution, resolved.resolvedAt()));
            audit.add(new AuditRecord("REPORT_RESOLVED", actor.id(), actor.name(), resolved.targetId(),
                resolved.targetName(), shortId(resolved.id()) + " — " + resolution, resolved.resolvedAt()));
            save();
        }
        return "Report " + shortId(resolved.id()) + " resolved.";
    }

    private synchronized void recordMutation(Actor actor, OfflinePlayer target, String action, String detail) {
        Instant now = Instant.now();
        appendHistory(target.getUniqueId(), new HistoryRecord(action, actor.name(), detail, now));
        audit.add(new AuditRecord(action, actor.id(), actor.name(), target.getUniqueId(),
            safeName(target), detail, now));
        save();
    }

    private Actor actor(CommandSender sender) {
        return new Actor(sender.getName(), actorId(sender));
    }

    // ---- Web console API. Call on the main thread; results are detached JSON copies. ----

    /** Applies a web-console moderation change with console authority, attributed to the panel login. */
    public String panelModerate(String actorName, String action, UUID targetId, String minutes, String detail) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        if (target.getPlayer() == null && !target.hasPlayedBefore()) {
            throw new IllegalArgumentException("That player has not joined this server.");
        }
        Actor actor = new Actor(actorName, null);
        String text = detail == null ? "" : detail.trim();
        return switch (action) {
            case "note" -> applyTextRecord(actor, target, "NOTE", text, false);
            case "warn" -> applyTextRecord(actor, target, "WARNING", text, true);
            case "mute" -> applyMute(actor, target, minutes == null ? "" : minutes, text);
            case "unmute" -> applyUnmute(actor, target);
            case "freeze" -> applyFreeze(actor, target, text.isEmpty() ? "Staff review" : text);
            case "unfreeze" -> applyUnfreeze(actor, target);
            case "kick" -> applyKick(actor, target, text);
            case "ban" -> applyBan(actor, target, minutes == null ? "" : minutes, text);
            case "unban" -> applyUnban(actor, target);
            default -> throw new IllegalArgumentException("Unknown moderation action.");
        };
    }

    public String panelResolve(String actorName, UUID reportId, String resolution) {
        String text = resolution == null || resolution.isBlank() ? "Reviewed by staff" : resolution.trim();
        requireReason(text);
        ReportRecord current;
        synchronized (this) {
            current = reports.get(reportId);
        }
        if (current == null || current.status() != ReportStatus.OPEN) {
            throw new IllegalArgumentException("That report is no longer open.");
        }
        return applyResolve(new Actor(actorName, null), current, text);
    }

    public boolean muted(UUID playerId) {
        return activeMute(playerId) != null;
    }

    public boolean frozen(UUID playerId) {
        return freezes.containsKey(playerId);
    }

    public synchronized com.google.gson.JsonObject panelSnapshot() {
        var root = new com.google.gson.JsonObject();
        var reportArray = new com.google.gson.JsonArray();
        reports.values().stream().sorted(Comparator.comparing(ReportRecord::createdAt).reversed()).limit(250)
            .forEach(report -> reportArray.add(reportJson(report)));
        root.add("reports", reportArray);
        var auditArray = new com.google.gson.JsonArray();
        audit.stream().sorted(Comparator.comparing(AuditRecord::createdAt).reversed()).limit(400).forEach(entry -> {
            var row = new com.google.gson.JsonObject();
            row.addProperty("action", entry.action());
            row.addProperty("actor", entry.actorName());
            row.addProperty("target", entry.targetName());
            row.addProperty("targetId", entry.targetId() == null ? null : entry.targetId().toString());
            row.addProperty("detail", entry.detail());
            row.addProperty("time", entry.createdAt().toString());
            auditArray.add(row);
        });
        root.add("audit", auditArray);
        var muteArray = new com.google.gson.JsonArray();
        new ArrayList<>(mutes.keySet()).forEach(id -> {
            MuteRecord mute = activeMute(id);
            if (mute != null) muteArray.add(sanction(id, mute.targetName(), mute.actorName(), mute.reason(), mute.createdAt(), mute.expiresAt()));
        });
        root.add("mutes", muteArray);
        var freezeArray = new com.google.gson.JsonArray();
        freezes.forEach((id, freeze) -> freezeArray.add(sanction(id, freeze.targetName(), freeze.actorName(), freeze.reason(), freeze.createdAt(), null)));
        root.add("freezes", freezeArray);
        return root;
    }

    public synchronized com.google.gson.JsonObject panelPlayer(UUID playerId) {
        var root = new com.google.gson.JsonObject();
        var historyArray = new com.google.gson.JsonArray();
        history(playerId).stream().sorted(Comparator.comparing(HistoryRecord::createdAt).reversed()).limit(200).forEach(record -> {
            var row = new com.google.gson.JsonObject();
            row.addProperty("type", record.type());
            row.addProperty("actor", record.actorName());
            row.addProperty("detail", record.detail());
            row.addProperty("time", record.createdAt().toString());
            historyArray.add(row);
        });
        root.add("history", historyArray);
        MuteRecord mute = activeMute(playerId);
        if (mute != null) root.add("mute", sanction(playerId, mute.targetName(), mute.actorName(), mute.reason(), mute.createdAt(), mute.expiresAt()));
        FreezeRecord freeze = freezes.get(playerId);
        if (freeze != null) root.add("freeze", sanction(playerId, freeze.targetName(), freeze.actorName(), freeze.reason(), freeze.createdAt(), null));
        var reportArray = new com.google.gson.JsonArray();
        reports.values().stream().filter(report -> report.targetId().equals(playerId) || report.reporterId().equals(playerId))
            .sorted(Comparator.comparing(ReportRecord::createdAt).reversed()).limit(50).forEach(report -> reportArray.add(reportJson(report)));
        root.add("reports", reportArray);
        return root;
    }

    /** Players who have any staff record, so the web console can list cases for offline accounts. */
    public synchronized java.util.Set<UUID> playersWithRecords() {
        var ids = new java.util.HashSet<>(histories.keySet());
        ids.addAll(mutes.keySet());
        ids.addAll(freezes.keySet());
        return ids;
    }

    private com.google.gson.JsonObject reportJson(ReportRecord report) {
        var row = new com.google.gson.JsonObject();
        row.addProperty("id", report.id().toString());
        row.addProperty("shortId", shortId(report.id()));
        row.addProperty("reporter", report.reporterName());
        row.addProperty("reporterId", report.reporterId().toString());
        row.addProperty("target", report.targetName());
        row.addProperty("targetId", report.targetId().toString());
        row.addProperty("reason", report.reason());
        row.addProperty("time", report.createdAt().toString());
        row.addProperty("status", report.status().name());
        row.addProperty("resolver", report.resolverName());
        row.addProperty("resolvedAt", report.resolvedAt() == null ? null : report.resolvedAt().toString());
        row.addProperty("resolution", report.resolution());
        return row;
    }

    private com.google.gson.JsonObject sanction(UUID id, String name, String actor, String reason, Instant created, Instant expires) {
        var row = new com.google.gson.JsonObject();
        row.addProperty("uuid", id.toString());
        row.addProperty("name", name);
        row.addProperty("actor", actor);
        row.addProperty("reason", reason);
        row.addProperty("time", created.toString());
        row.addProperty("expires", expires == null ? null : expires.toString());
        return row;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        MuteRecord mute = activeMute(event.getPlayer().getUniqueId());
        if (mute == null) {
            return;
        }
        event.setCancelled(true);
        sync(() -> event.getPlayer().sendRichMessage("<red>You are muted until " + TIME.format(mute.expiresAt())
            + ".</red> <gray>" + escape(mute.reason()) + "</gray>"));
    }

    public boolean canCommunicate(UUID playerId) {
        return activeMute(playerId) == null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!freezes.containsKey(event.getPlayer().getUniqueId()) || (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ())) {
            return;
        }
        event.setTo(event.getFrom());
        long now = System.currentTimeMillis();
        if (freezeNotices.getOrDefault(event.getPlayer().getUniqueId(), 0L) <= now) {
            freezeNotices.put(event.getPlayer().getUniqueId(), now + 2_000L);
            event.getPlayer().sendActionBar(message("<red>You are frozen. Wait for staff.</red>"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        MuteRecord mute = activeMute(event.getPlayer().getUniqueId());
        if (mute != null) {
            event.getPlayer().sendRichMessage("<red>Your chat mute ends " + TIME.format(mute.expiresAt()) + ".</red>");
        }
        if (freezes.containsKey(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendRichMessage("<red><bold>You are frozen.</bold></red> <gray>Wait for staff instructions.</gray>");
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof StaffInventory holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewerId.equals(player.getUniqueId())
            || event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        Consumer<Player> action = holder.actions.get(event.getRawSlot());
        if (action != null) {
            action.accept(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof StaffInventory
            && event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
        }
    }

    public void openDashboard(Player staff) {
        if (!staff.hasPermission("nerdsmp.staff")) {
            return;
        }
        StaffMenu menu = menu(staff, View.DASHBOARD, 54, "<red><bold>Staff Desk</bold></red>", null, null);
        decorate(menu.inventory());
        button(menu, 4, Material.SPYGLASS, "<red><bold>Staff Desk</bold></red>", List.of(
            "<gray>Online</gray> <white>" + Bukkit.getOnlinePlayers().size() + "</white>",
            "<gray>Open reports</gray> <red>" + openReports().size() + "</red>",
            "<gray>Active mutes</gray> <yellow>" + activeMuteCount() + "</yellow>",
            "<gray>Frozen</gray> <aqua>" + freezes.size() + "</aqua>"
        ), ignored -> { });
        List<? extends Player> online = Bukkit.getOnlinePlayers().stream()
            .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).limit(PLAYER_SLOTS.length).toList();
        for (int index = 0; index < online.size(); index++) {
            Player target = online.get(index);
            ItemStack head = playerIcon(target, "<white>" + escape(target.getName()) + "</white>", List.of(
                "<gray>" + escape(target.getWorld().getName()) + " • " + Math.round(target.getHealth()) + " HP</gray>",
                mutes.containsKey(target.getUniqueId()) ? "<red>Muted</red>" : "<dark_gray>Chat clear</dark_gray>",
                freezes.containsKey(target.getUniqueId()) ? "<aqua>Frozen</aqua>" : "<dark_gray>Movement clear</dark_gray>",
                "", "<yellow>Click to inspect.</yellow>"
            ));
            rawButton(menu, PLAYER_SLOTS[index], head, viewer -> openPlayer(viewer, target));
        }
        button(menu, 46, Material.WRITABLE_BOOK, "<red>Open Reports</red>", List.of(
            "<white>" + openReports().size() + " waiting</white>", "", "<yellow>Click to review.</yellow>"
        ), this::openReports);
        button(menu, 48, Material.CLOCK, "<gold>Audit Trail</gold>", List.of(
            "<gray>Latest staff and report activity.</gray>", "", "<yellow>Click to print the latest entries.</yellow>"
        ), viewer -> {
            viewer.closeInventory();
            showAudit(viewer);
        });
        button(menu, 50, Material.SLIME_BALL, "<green>Refresh</green>", List.of(), this::openDashboard);
        button(menu, 53, Material.BARRIER, "<red>Close</red>", List.of(), Player::closeInventory);
        staff.openInventory(menu.inventory());
    }

    private void openPlayer(Player staff, OfflinePlayer target) {
        StaffMenu menu = menu(staff, View.PLAYER, 54,
            "<red><bold>Player • " + escape(safeName(target)) + "</bold></red>", target.getUniqueId(), null);
        decorate(menu.inventory());
        Player online = target.getPlayer();
        rawButton(menu, 4, playerIcon(target, "<white><bold>" + escape(safeName(target)) + "</bold></white>", List.of(
            online == null ? "<gray>Offline</gray>" : "<green>Online</green>",
            "<dark_gray>" + target.getUniqueId() + "</dark_gray>"
        )), ignored -> { });
        button(menu, 13, Material.BOOK, "<aqua>Player Snapshot</aqua>", List.of(
            online == null ? "<gray>Gameplay data loads while the player is online.</gray>"
                : "<gray>Loading account data…</gray>"
        ), ignored -> { });
        button(menu, 20, Material.WRITABLE_BOOK, "<gold>Case History</gold>", List.of(
            "<white>" + history(target.getUniqueId()).size() + " entries</white>", "", "<yellow>Click to review.</yellow>"
        ), viewer -> openHistory(viewer, target));
        button(menu, 24, Material.NAME_TAG, "<yellow>Notes & Warnings</yellow>", List.of(
            "<gray>/staff note " + escape(safeName(target)) + " [text]</gray>",
            "<gray>/staff warn " + escape(safeName(target)) + " [reason]</gray>"
        ), ignored -> { });
        button(menu, 29, Material.CHEST, "<aqua>Inventory Snapshot</aqua>", List.of(
            online == null ? "<red>Player must be online.</red>" : "<yellow>Click for a read-only view.</yellow>"
        ), viewer -> {
            Player current = Bukkit.getPlayer(target.getUniqueId());
            if (current == null) {
                viewer.sendRichMessage("<red>That player is now offline.</red>");
            } else {
                openInventory(viewer, current);
            }
        });
        button(menu, 31, Material.ENDER_PEARL, "<green>Teleport to Player</green>", List.of(
            online == null ? "<red>Player must be online.</red>" : "<yellow>Click to teleport.</yellow>"
        ), viewer -> teleportTo(viewer, target));
        boolean frozen = freezes.containsKey(target.getUniqueId());
        button(menu, 33, frozen ? Material.ICE : Material.PACKED_ICE,
            frozen ? "<aqua>Unfreeze Player</aqua>" : "<aqua>Freeze Player</aqua>", List.of(
                "<gray>Stops block-to-block movement.</gray>", "", "<yellow>Click to toggle.</yellow>"
            ), viewer -> toggleFreeze(viewer, target));
        boolean muted = activeMute(target.getUniqueId()) != null;
        button(menu, 35, muted ? Material.LIME_DYE : Material.RED_DYE,
            muted ? "<green>Remove Mute</green>" : "<red>Mute " + defaultMuteMinutes + " Minutes</red>", List.of(
                muted ? "<gray>Removes the active chat mute.</gray>"
                    : "<gray>Reason: Staff panel action</gray>", "", "<yellow>Click to toggle.</yellow>"
            ), viewer -> toggleMute(viewer, target));
        button(menu, 40, Material.OAK_DOOR, "<red>Kick Player</red>", List.of(
            online == null ? "<red>Player must be online.</red>" : "<yellow>Click to review.</yellow>"
        ), viewer -> openKickConfirmation(viewer, target));
        button(menu, 45, Material.ARROW, "<aqua>Back to Staff Desk</aqua>", List.of(), this::openDashboard);
        button(menu, 49, Material.SLIME_BALL, "<green>Refresh</green>", List.of(), viewer -> openPlayer(viewer, target));
        button(menu, 53, Material.BARRIER, "<red>Close</red>", List.of(), Player::closeInventory);
        staff.openInventory(menu.inventory());
        if (online != null) {
            populateGameplaySnapshot(staff, menu, online);
        }
    }

    private void populateGameplaySnapshot(Player staff, StaffMenu menu, Player target) {
        AccountId account = plugin.sessions().account(target.getUniqueId()).orElse(null);
        if (account == null) {
            menu.inventory().setItem(13, icon(Material.BOOK, "<aqua>Player Snapshot</aqua>", List.of(
                "<gray>The player's account is still loading.</gray>"
            )));
            return;
        }
        CompletableFuture<Long> balance = plugin.economy().balance(account).toCompletableFuture();
        var progression = plugin.progression().snapshot(account).toCompletableFuture();
        var claims = plugin.claims().claimCount(account).toCompletableFuture();
        var capacity = plugin.claims().capacity(account).toCompletableFuture();
        var party = plugin.parties().partyOf(account).toCompletableFuture();
        CompletableFuture.allOf(balance, progression, claims, capacity, party).whenComplete((ignored, failure) -> sync(() -> {
            if (!staff.isOnline() || !staff.getOpenInventory().getTopInventory().equals(menu.inventory())) {
                return;
            }
            if (failure != null) {
                menu.inventory().setItem(13, icon(Material.BARRIER, "<red>Snapshot Unavailable</red>", List.of(
                    "<gray>Gameplay services did not answer.</gray>"
                )));
                return;
            }
            var adventurer = progression.join().adventurer();
            Player current = Bukkit.getPlayer(target.getUniqueId());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Marks</gray> <gold>" + VisualFormat.compact(balance.join()) + " ✦</gold>");
            lore.add("<gray>Adventurer</gray> <aqua>" + adventurer.level() + " • "
                + adventurer.rank().displayName() + "</aqua>");
            lore.add("<gray>Claims</gray> <yellow>" + claims.join() + "/" + capacity.join() + "</yellow>");
            lore.add("<gray>Party</gray> <white>" + party.join().map(view -> view.members().size() + " members")
                .orElse("None") + "</white>");
            if (current != null) {
                double maximumHealth = current.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0D
                    : current.getAttribute(Attribute.MAX_HEALTH).getValue();
                lore.add("<gray>Location</gray> <white>" + escape(current.getWorld().getName()) + " "
                    + current.getLocation().getBlockX() + ", " + current.getLocation().getBlockY() + ", "
                    + current.getLocation().getBlockZ() + "</white>");
                lore.add("<gray>Health</gray> <white>" + Math.round(current.getHealth()) + "/"
                    + Math.round(maximumHealth) + "</white>");
            }
            menu.inventory().setItem(13, icon(Material.BOOK, "<aqua>Player Snapshot</aqua>", lore));
        }));
    }

    private void openInventory(Player staff, Player target) {
        StaffMenu menu = menu(staff, View.INVENTORY, 54,
            "<red><bold>Inventory • " + escape(target.getName()) + "</bold></red>", target.getUniqueId(), null);
        ItemStack[] storage = target.getInventory().getStorageContents();
        for (int slot = 0; slot < Math.min(36, storage.length); slot++) {
            if (storage[slot] != null) {
                menu.inventory().setItem(slot, storage[slot].clone());
            }
        }
        ItemStack[] armor = target.getInventory().getArmorContents();
        for (int index = 0; index < armor.length; index++) {
            if (armor[index] != null) {
                menu.inventory().setItem(45 + index, armor[index].clone());
            }
        }
        ItemStack offhand = target.getInventory().getItemInOffHand();
        if (!offhand.getType().isAir()) {
            menu.inventory().setItem(50, offhand.clone());
        }
        button(menu, 52, Material.SLIME_BALL, "<green>Refresh Snapshot</green>", List.of(), viewer -> {
            Player current = Bukkit.getPlayer(target.getUniqueId());
            if (current == null) {
                viewer.sendRichMessage("<red>That player is now offline.</red>");
                openPlayer(viewer, target);
            } else {
                openInventory(viewer, current);
            }
        });
        button(menu, 53, Material.ARROW, "<aqua>Back to Player</aqua>", List.of(), viewer -> openPlayer(viewer, target));
        staff.openInventory(menu.inventory());
    }

    private void openReports(Player staff) {
        StaffMenu menu = menu(staff, View.REPORTS, 54, "<red><bold>Open Reports</bold></red>", null, null);
        decorate(menu.inventory());
        List<ReportRecord> open = openReports();
        button(menu, 4, Material.WRITABLE_BOOK, "<red><bold>Open Reports</bold></red>", List.of(
            "<white>" + open.size() + " waiting for review</white>",
            "<dark_gray>Oldest cases appear first.</dark_gray>"
        ), ignored -> { });
        for (int index = 0; index < Math.min(RECORD_SLOTS.length, open.size()); index++) {
            ReportRecord report = open.get(index);
            button(menu, RECORD_SLOTS[index], Material.PAPER,
                "<red>" + shortId(report.id()) + " • " + escape(report.targetName()) + "</red>", List.of(
                    "<gray>Reporter</gray> <white>" + escape(report.reporterName()) + "</white>",
                    "<gray>Filed</gray> <white>" + TIME.format(report.createdAt()) + "</white>",
                    "<gray>" + escape(trim(report.reason(), 90)) + "</gray>", "", "<yellow>Click to review.</yellow>"
                ), viewer -> openReport(viewer, report.id()));
        }
        if (open.isEmpty()) {
            button(menu, 22, Material.LIME_DYE, "<green>No Open Reports</green>", List.of(
                "<gray>The queue is clear.</gray>"
            ), ignored -> { });
        }
        button(menu, 45, Material.ARROW, "<aqua>Back to Staff Desk</aqua>", List.of(), this::openDashboard);
        button(menu, 49, Material.SLIME_BALL, "<green>Refresh</green>", List.of(), this::openReports);
        button(menu, 53, Material.BARRIER, "<red>Close</red>", List.of(), Player::closeInventory);
        staff.openInventory(menu.inventory());
    }

    private void openReport(Player staff, UUID reportId) {
        ReportRecord report;
        synchronized (this) {
            report = reports.get(reportId);
        }
        if (report == null || report.status() != ReportStatus.OPEN) {
            staff.sendRichMessage("<gray>That report is no longer open.</gray>");
            openReports(staff);
            return;
        }
        StaffMenu menu = menu(staff, View.REPORT, 27,
            "<red><bold>Report • " + shortId(report.id()) + "</bold></red>", report.targetId(), report.id());
        decorate(menu.inventory());
        button(menu, 4, Material.PAPER, "<red>Report " + shortId(report.id()) + "</red>", List.of(
            "<gray>Target</gray> <white>" + escape(report.targetName()) + "</white>",
            "<gray>Reporter</gray> <white>" + escape(report.reporterName()) + "</white>",
            "<gray>Filed</gray> <white>" + TIME.format(report.createdAt()) + "</white>",
            "", "<white>" + escape(report.reason()) + "</white>"
        ), ignored -> { });
        button(menu, 10, Material.PLAYER_HEAD, "<aqua>Inspect Player</aqua>", List.of(
            "<yellow>Click to open the player record.</yellow>"
        ), viewer -> openPlayer(viewer, Bukkit.getOfflinePlayer(report.targetId())));
        button(menu, 13, Material.LIME_DYE, "<green>Resolve Report</green>", List.of(
            "<gray>Records this case as reviewed.</gray>", "<yellow>Click to resolve.</yellow>"
        ), viewer -> {
            resolveReport(viewer, shortId(report.id()), "Reviewed from staff panel");
            openReports(viewer);
        });
        button(menu, 16, Material.ARROW, "<aqua>Back to Reports</aqua>", List.of(), this::openReports);
        staff.openInventory(menu.inventory());
    }

    private void openHistory(Player staff, OfflinePlayer target) {
        StaffMenu menu = menu(staff, View.HISTORY, 54,
            "<gold><bold>History • " + escape(safeName(target)) + "</bold></gold>", target.getUniqueId(), null);
        decorate(menu.inventory());
        List<HistoryRecord> records = history(target.getUniqueId());
        button(menu, 4, Material.WRITABLE_BOOK, "<gold><bold>Case History</bold></gold>", List.of(
            "<white>" + records.size() + " saved entries</white>"
        ), ignored -> { });
        List<HistoryRecord> newest = records.stream().sorted(Comparator.comparing(HistoryRecord::createdAt).reversed())
            .limit(RECORD_SLOTS.length).toList();
        for (int index = 0; index < newest.size(); index++) {
            HistoryRecord record = newest.get(index);
            button(menu, RECORD_SLOTS[index], historyMaterial(record.type()),
                historyColor(record.type()) + title(record.type()) + "</" + historyColorName(record.type()) + ">", List.of(
                    "<gray>By</gray> <white>" + escape(record.actorName()) + "</white>",
                    "<gray>When</gray> <white>" + TIME.format(record.createdAt()) + "</white>",
                    "", "<gray>" + escape(trim(record.detail(), 120)) + "</gray>"
                ), ignored -> { });
        }
        if (newest.isEmpty()) {
            button(menu, 22, Material.LIME_DYE, "<green>No Staff History</green>", List.of(), ignored -> { });
        }
        button(menu, 45, Material.ARROW, "<aqua>Back to Player</aqua>", List.of(), viewer -> openPlayer(viewer, target));
        button(menu, 53, Material.BARRIER, "<red>Close</red>", List.of(), Player::closeInventory);
        staff.openInventory(menu.inventory());
    }

    private void openKickConfirmation(Player staff, OfflinePlayer target) {
        if (target.getPlayer() == null) {
            staff.sendRichMessage("<red>That player is offline.</red>");
            openPlayer(staff, target);
            return;
        }
        StaffMenu menu = menu(staff, View.CONFIRM_KICK, 27,
            "<red><bold>Kick " + escape(safeName(target)) + "?</bold></red>", target.getUniqueId(), null);
        decorate(menu.inventory());
        button(menu, 11, Material.ARROW, "<gray>Cancel</gray>", List.of(), viewer -> openPlayer(viewer, target));
        button(menu, 15, Material.RED_DYE, "<red>Confirm Kick</red>", List.of(
            "<gray>Reason: Removed from staff panel</gray>", "<yellow>Click again to confirm.</yellow>"
        ), viewer -> {
            kick(viewer, target, "Removed from staff panel");
            openDashboard(viewer);
        });
        staff.openInventory(menu.inventory());
    }

    private void teleportTo(Player staff, OfflinePlayer target) {
        Player online = target.getPlayer();
        if (online == null) {
            staff.sendRichMessage("<red>That player is offline.</red>");
            return;
        }
        staff.closeInventory();
        staff.teleportAsync(online.getLocation()).thenAccept(success -> sync(() -> {
            if (success) {
                staff.sendRichMessage("<green>Teleported to " + escape(online.getName()) + ".</green>");
            } else {
                staff.sendRichMessage("<red>Teleport failed.</red>");
            }
        }));
    }

    private void toggleFreeze(Player staff, OfflinePlayer target) {
        if (!requireModerate(staff)) {
            return;
        }
        if (freezes.containsKey(target.getUniqueId())) {
            unfreeze(staff, target);
        } else {
            freeze(staff, target, "Frozen from staff panel");
        }
        openPlayer(staff, target);
    }

    private void toggleMute(Player staff, OfflinePlayer target) {
        if (!requireModerate(staff)) {
            return;
        }
        if (activeMute(target.getUniqueId()) != null) {
            unmute(staff, target);
        } else {
            Instant expires = Instant.now().plus(Duration.ofMinutes(defaultMuteMinutes));
            mutes.put(target.getUniqueId(), new MuteRecord(safeName(target), staff.getName(),
                "Muted from staff panel", Instant.now(), expires));
            recordMutation(actor(staff), target, "MUTE", defaultMuteMinutes + "m — Muted from staff panel");
            if (target.getPlayer() != null) {
                target.getPlayer().sendRichMessage("<red>You are muted until " + TIME.format(expires) + ".</red>");
            }
        }
        openPlayer(staff, target);
    }

    private boolean requireModerate(Player staff) {
        if (staff.hasPermission("nerdsmp.staff.moderate")) {
            return true;
        }
        staff.sendRichMessage("<red>This action requires nerdsmp.staff.moderate.</red>");
        return false;
    }

    private void help(CommandSender sender) {
        sender.sendRichMessage("<red><bold>Staff desk</bold></red> <gray>/staff opens the panel</gray>");
        sender.sendRichMessage("<white>Inspect:</white> <gray>/staff player|inventory|history [player]</gray>");
        sender.sendRichMessage("<white>Records:</white> <gray>/staff note|warn [player] [text]</gray>");
        sender.sendRichMessage("<white>Actions:</white> <gray>/staff mute [player] [minutes] [reason], "
            + "unmute, freeze, unfreeze, kick, ban [player] [minutes|perm] [reason], unban</gray>");
        sender.sendRichMessage("<white>Cases:</white> <gray>/staff reports, /staff resolve [id] [note], /staff audit</gray>");
    }

    private void showOpenReports(CommandSender sender) {
        List<ReportRecord> open = openReports();
        sender.sendRichMessage("<red><bold>Open reports</bold></red> <white>" + open.size() + "</white>");
        open.stream().limit(10).forEach(report -> sender.sendRichMessage("<red>" + shortId(report.id()) + "</red> "
            + "<white>" + escape(report.targetName()) + "</white> <gray>by " + escape(report.reporterName())
            + " — " + escape(trim(report.reason(), 90)) + "</gray>"));
    }

    private void showHistory(CommandSender sender, OfflinePlayer target) {
        List<HistoryRecord> records = history(target.getUniqueId());
        sender.sendRichMessage("<gold><bold>History • " + escape(safeName(target)) + "</bold></gold> <white>"
            + records.size() + " entries</white>");
        records.stream().sorted(Comparator.comparing(HistoryRecord::createdAt).reversed()).limit(10)
            .forEach(record -> sender.sendRichMessage("<yellow>" + title(record.type()) + "</yellow> <gray>"
                + TIME.format(record.createdAt()) + " by " + escape(record.actorName()) + " — "
                + escape(trim(record.detail(), 100)) + "</gray>"));
    }

    private synchronized void showAudit(CommandSender sender) {
        sender.sendRichMessage("<gold><bold>Staff audit</bold></gold> <white>" + audit.size() + " entries</white>");
        audit.stream().sorted(Comparator.comparing(AuditRecord::createdAt).reversed()).limit(10)
            .forEach(entry -> sender.sendRichMessage("<yellow>" + entry.action() + "</yellow> <white>"
                + escape(entry.actorName()) + " → " + escape(entry.targetName()) + "</white> <gray>"
                + TIME.format(entry.createdAt()) + " — " + escape(trim(entry.detail(), 90)) + "</gray>"));
    }

    private synchronized List<ReportRecord> openReports() {
        return reports.values().stream().filter(report -> report.status() == ReportStatus.OPEN)
            .sorted(Comparator.comparing(ReportRecord::createdAt)).toList();
    }

    private synchronized List<HistoryRecord> history(UUID targetId) {
        return List.copyOf(histories.getOrDefault(targetId, List.of()));
    }

    private void appendHistory(UUID targetId, HistoryRecord record) {
        histories.computeIfAbsent(targetId, ignored -> new ArrayList<>()).add(record);
    }

    private MuteRecord activeMute(UUID playerId) {
        MuteRecord mute = mutes.get(playerId);
        if (mute == null) {
            return null;
        }
        if (mute.expiresAt().isAfter(Instant.now())) {
            return mute;
        }
        if (mutes.remove(playerId, mute)) {
            sync(this::save);
        }
        return null;
    }

    private int activeMuteCount() {
        List<UUID> ids = new ArrayList<>(mutes.keySet());
        ids.forEach(this::activeMute);
        return mutes.size();
    }

    private StaffMenu menu(
        Player viewer,
        View view,
        int size,
        String title,
        UUID targetId,
        UUID reportId
    ) {
        StaffInventory holder = new StaffInventory(viewer.getUniqueId(), view, targetId, reportId);
        Inventory inventory = Bukkit.createInventory(holder, size, message(title));
        holder.inventory = inventory;
        return new StaffMenu(inventory, holder);
    }

    private void decorate(Inventory inventory) {
        ItemStack dark = icon(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> </dark_gray>", List.of());
        ItemStack accent = icon(Material.RED_STAINED_GLASS_PANE, "<dark_gray> </dark_gray>", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int column = slot % 9;
            boolean border = slot < 9 || slot >= inventory.getSize() - 9 || column == 0 || column == 8;
            inventory.setItem(slot, border && slot < 9 && slot % 2 == 1 ? accent : dark);
        }
    }

    private void button(
        StaffMenu menu,
        int slot,
        Material material,
        String name,
        List<String> lore,
        Consumer<Player> action
    ) {
        rawButton(menu, slot, icon(material, name, lore), action);
    }

    private void rawButton(StaffMenu menu, int slot, ItemStack item, Consumer<Player> action) {
        menu.inventory().setItem(slot, item);
        menu.holder().actions.put(slot, action);
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(message(name));
        meta.displayName(null);
        meta.lore(lore.stream().map(this::message).toList());
        meta.removeEnchantments();
        meta.setAttributeModifiers(HashMultimap.create());
        meta.addItemFlags(ItemFlag.values());
        meta.setEnchantmentGlintOverride(false);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack playerIcon(OfflinePlayer player, String name, List<String> lore) {
        ItemStack item = icon(Material.PLAYER_HEAD, name, lore);
        if (item.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
            item.setItemMeta(skull);
        }
        return item;
    }

    private Material historyMaterial(String type) {
        return switch (type) {
            case "WARNING", "KICK", "BAN", "UNBAN" -> Material.RED_DYE;
            case "MUTE", "UNMUTE" -> Material.NOTE_BLOCK;
            case "FREEZE", "UNFREEZE" -> Material.ICE;
            case "REPORT", "REPORT_RESOLVED" -> Material.PAPER;
            default -> Material.WRITABLE_BOOK;
        };
    }

    private String historyColor(String type) {
        return switch (type) {
            case "WARNING", "KICK", "BAN", "UNBAN" -> "<red>";
            case "MUTE", "UNMUTE" -> "<yellow>";
            case "FREEZE", "UNFREEZE" -> "<aqua>";
            case "REPORT", "REPORT_RESOLVED" -> "<light_purple>";
            default -> "<white>";
        };
    }

    private String historyColorName(String type) {
        return switch (type) {
            case "WARNING", "KICK", "BAN", "UNBAN" -> "red";
            case "MUTE", "UNMUTE" -> "yellow";
            case "FREEZE", "UNFREEZE" -> "aqua";
            case "REPORT", "REPORT_RESOLVED" -> "light_purple";
            default -> "white";
        };
    }

    private OfflinePlayer findKnown(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Arrays.stream(Bukkit.getOfflinePlayers())
            .filter(player -> player.getName() != null && player.getName().equalsIgnoreCase(name))
            .findFirst().orElse(null);
    }

    private List<String> onlineNames(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
            .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private String reason(String[] args, int from) {
        return from >= args.length ? "" : String.join(" ", Arrays.copyOfRange(args, from, args.length)).trim();
    }

    private boolean validReason(String value) {
        return value.length() >= 3 && value.length() <= 180 && value.chars().noneMatch(Character::isISOControl);
    }

    private UUID actorId(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : null;
    }

    private String safeName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString().substring(0, 8) : player.getName();
    }

    private String escape(String value) {
        return value.replace("<", "\\<").replace(">", "\\>");
    }

    private String trim(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…";
    }

    private String title(String value) {
        StringBuilder result = new StringBuilder();
        for (String word : value.toLowerCase(Locale.ROOT).split("[_-]")) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private String normalized(UUID id) {
        return id.toString().replace("-", "").toLowerCase(Locale.ROOT);
    }

    private Component message(String markup) {
        return miniMessage.deserialize(markup).decoration(TextDecoration.ITALIC, false);
    }

    private void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        loadMutes(yaml.getConfigurationSection("mutes"));
        loadFreezes(yaml.getConfigurationSection("freezes"));
        loadReports(yaml.getConfigurationSection("reports"));
        ConfigurationSection historyRoot = yaml.getConfigurationSection("history");
        if (historyRoot != null) {
            for (String rawTarget : historyRoot.getKeys(false)) {
                UUID target = uuid(rawTarget);
                ConfigurationSection targetSection = historyRoot.getConfigurationSection(rawTarget);
                if (target == null || targetSection == null) {
                    continue;
                }
                for (String index : targetSection.getKeys(false)) {
                    ConfigurationSection section = targetSection.getConfigurationSection(index);
                    if (section != null) {
                        appendHistory(target, new HistoryRecord(section.getString("type", "NOTE"),
                            section.getString("actor", "Unknown"), section.getString("detail", ""),
                            instant(section.getString("created"))));
                    }
                }
            }
        }
        ConfigurationSection auditRoot = yaml.getConfigurationSection("audit");
        if (auditRoot != null) {
            for (String index : auditRoot.getKeys(false)) {
                ConfigurationSection section = auditRoot.getConfigurationSection(index);
                if (section != null) {
                    audit.add(new AuditRecord(section.getString("action", "UNKNOWN"),
                        uuid(section.getString("actor-id")), section.getString("actor", "Unknown"),
                        uuid(section.getString("target-id")), section.getString("target", "Unknown"),
                        section.getString("detail", ""), instant(section.getString("created"))));
                }
            }
        }
    }

    private void loadMutes(ConfigurationSection root) {
        if (root == null) {
            return;
        }
        for (String rawId : root.getKeys(false)) {
            UUID id = uuid(rawId);
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (id != null && section != null) {
                MuteRecord mute = new MuteRecord(section.getString("name", rawId),
                    section.getString("actor", "Unknown"), section.getString("reason", "No reason recorded"),
                    instant(section.getString("created")), instant(section.getString("expires")));
                if (mute.expiresAt().isAfter(Instant.now())) {
                    mutes.put(id, mute);
                }
            }
        }
    }

    private void loadFreezes(ConfigurationSection root) {
        if (root == null) {
            return;
        }
        for (String rawId : root.getKeys(false)) {
            UUID id = uuid(rawId);
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (id != null && section != null) {
                freezes.put(id, new FreezeRecord(section.getString("name", rawId),
                    section.getString("actor", "Unknown"), section.getString("reason", "Staff review"),
                    instant(section.getString("created"))));
            }
        }
    }

    private void loadReports(ConfigurationSection root) {
        if (root == null) {
            return;
        }
        for (String rawId : root.getKeys(false)) {
            UUID id = uuid(rawId);
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (id == null || section == null) {
                continue;
            }
            UUID reporterId = uuid(section.getString("reporter-id"));
            UUID targetId = uuid(section.getString("target-id"));
            if (reporterId == null || targetId == null) {
                continue;
            }
            ReportStatus status;
            try {
                status = ReportStatus.valueOf(section.getString("status", "OPEN"));
            } catch (IllegalArgumentException exception) {
                status = ReportStatus.OPEN;
            }
            reports.put(id, new ReportRecord(id, reporterId, section.getString("reporter", "Unknown"),
                targetId, section.getString("target", "Unknown"), section.getString("reason", ""),
                instant(section.getString("created")), status, uuid(section.getString("resolver-id")),
                section.getString("resolver"), nullableInstant(section.getString("resolved")),
                section.getString("resolution")));
        }
    }

    private synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        mutes.forEach((id, record) -> {
            String path = "mutes." + id;
            yaml.set(path + ".name", record.targetName());
            yaml.set(path + ".actor", record.actorName());
            yaml.set(path + ".reason", record.reason());
            yaml.set(path + ".created", record.createdAt().toString());
            yaml.set(path + ".expires", record.expiresAt().toString());
        });
        freezes.forEach((id, record) -> {
            String path = "freezes." + id;
            yaml.set(path + ".name", record.targetName());
            yaml.set(path + ".actor", record.actorName());
            yaml.set(path + ".reason", record.reason());
            yaml.set(path + ".created", record.createdAt().toString());
        });
        reports.values().forEach(record -> {
            String path = "reports." + record.id();
            yaml.set(path + ".reporter-id", record.reporterId().toString());
            yaml.set(path + ".reporter", record.reporterName());
            yaml.set(path + ".target-id", record.targetId().toString());
            yaml.set(path + ".target", record.targetName());
            yaml.set(path + ".reason", record.reason());
            yaml.set(path + ".created", record.createdAt().toString());
            yaml.set(path + ".status", record.status().name());
            if (record.resolverId() != null) {
                yaml.set(path + ".resolver-id", record.resolverId().toString());
            }
            yaml.set(path + ".resolver", record.resolverName());
            yaml.set(path + ".resolved", record.resolvedAt() == null ? null : record.resolvedAt().toString());
            yaml.set(path + ".resolution", record.resolution());
        });
        histories.forEach((target, records) -> {
            for (int index = 0; index < records.size(); index++) {
                HistoryRecord record = records.get(index);
                String path = "history." + target + "." + String.format(Locale.ROOT, "%06d", index);
                yaml.set(path + ".type", record.type());
                yaml.set(path + ".actor", record.actorName());
                yaml.set(path + ".detail", record.detail());
                yaml.set(path + ".created", record.createdAt().toString());
            }
        });
        for (int index = 0; index < audit.size(); index++) {
            AuditRecord record = audit.get(index);
            String path = "audit." + String.format(Locale.ROOT, "%06d", index);
            yaml.set(path + ".action", record.action());
            if (record.actorId() != null) {
                yaml.set(path + ".actor-id", record.actorId().toString());
            }
            yaml.set(path + ".actor", record.actorName());
            if (record.targetId() != null) {
                yaml.set(path + ".target-id", record.targetId().toString());
            }
            yaml.set(path + ".target", record.targetName());
            yaml.set(path + ".detail", record.detail());
            yaml.set(path + ".created", record.createdAt().toString());
        }
        try {
            AtomicYamlFiles.save(yaml, dataFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save staff management records", exception);
        }
    }

    private UUID uuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Instant instant(String raw) {
        Instant parsed = nullableInstant(raw);
        return parsed == null ? Instant.EPOCH : parsed;
    }

    private Instant nullableInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (java.time.format.DateTimeParseException exception) {
            return null;
        }
    }

    @Override
    public void close() {
        save();
    }

    private enum View {
        DASHBOARD,
        PLAYER,
        INVENTORY,
        REPORTS,
        REPORT,
        HISTORY,
        CONFIRM_KICK
    }

    private enum ReportStatus {
        OPEN,
        RESOLVED
    }

    private record MuteRecord(
        String targetName,
        String actorName,
        String reason,
        Instant createdAt,
        Instant expiresAt
    ) {
    }

    private record FreezeRecord(String targetName, String actorName, String reason, Instant createdAt) {
    }

    private record Actor(String name, UUID id) {
    }

    private record HistoryRecord(String type, String actorName, String detail, Instant createdAt) {
    }

    private record AuditRecord(
        String action,
        UUID actorId,
        String actorName,
        UUID targetId,
        String targetName,
        String detail,
        Instant createdAt
    ) {
    }

    private record ReportRecord(
        UUID id,
        UUID reporterId,
        String reporterName,
        UUID targetId,
        String targetName,
        String reason,
        Instant createdAt,
        ReportStatus status,
        UUID resolverId,
        String resolverName,
        Instant resolvedAt,
        String resolution
    ) {
        private ReportRecord resolve(UUID staffId, String staffName, Instant at, String note) {
            return new ReportRecord(id, reporterId, reporterName, targetId, targetName, reason, createdAt,
                ReportStatus.RESOLVED, staffId, staffName, at, note);
        }
    }

    private record StaffMenu(Inventory inventory, StaffInventory holder) {
    }

    private static final class StaffInventory implements InventoryHolder {
        private final UUID viewerId;
        private final View view;
        private final UUID targetId;
        private final UUID reportId;
        private final Map<Integer, Consumer<Player>> actions = new HashMap<>();
        private Inventory inventory;

        private StaffInventory(UUID viewerId, View view, UUID targetId, UUID reportId) {
            this.viewerId = viewerId;
            this.view = view;
            this.targetId = targetId;
            this.reportId = reportId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
