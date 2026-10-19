package net.nerdsmp.paper.anticheat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.BanEntry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Production console backend. Everything that touches Bukkit runs on the main thread; HTTP threads only read
 * the published JSON string or wait (bounded) for a main-thread task to finish.
 */
final class PanelData implements PanelBackend {
    private static final int SAMPLE_TICKS = 100;
    private static final int HISTORY = 720; // one hour of 5-second samples
    private static final int DIRECTORY_EVERY = 12; // refresh the known-player directory once a minute
    private static final Set<String> MODERATION = Set.of("note", "warn", "mute", "unmute", "freeze", "unfreeze", "kick", "ban", "unban");

    private final NerdSmpPlugin plugin;
    private final Deque<double[]> samples = new ArrayDeque<>();
    private volatile String snapshot = "{\"status\":\"starting\"}";
    private JsonArray directory = new JsonArray();
    private int publishes;
    private BukkitTask task;

    PanelData(NerdSmpPlugin plugin) {
        this.plugin = plugin;
    }

    void start() {
        publish();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::publish, SAMPLE_TICKS, SAMPLE_TICKS);
    }

    @Override
    public void close() {
        if (task != null) task.cancel();
    }

    @Override
    public String status() {
        return snapshot;
    }

    @Override
    public Reply player(UUID playerId) {
        return onMainThread(() -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerId);
            if (target.getPlayer() == null && !target.hasPlayedBefore()) return Reply.error(404, "That player has not joined this server.");
            return Reply.ok(playerDetail(target));
        });
    }

    @Override
    public Reply act(String actor, JsonObject request) {
        var raw = request.get("action");
        String action = raw != null && raw.isJsonPrimitive() ? raw.getAsString() : null;
        if (action == null || !action.matches("[a-z-]{1,32}")) return Reply.error(400, "Choose an action.");
        String attributed = "Panel (" + actor + ")";
        return onMainThread(() -> {
            try {
                String result = switch (action) {
                    case "resolve" -> plugin.staff().panelResolve(attributed, uuid(request, "report"), text(request, "reason", 180));
                    case "broadcast" -> broadcast(attributed, text(request, "message", 256));
                    case "message" -> message(attributed, uuid(request, "target"), text(request, "message", 256));
                    case "clear-watchdog" -> clearWatchdog(attributed, uuid(request, "target"));
                    case "group-add", "group-remove" -> membership(actor, uuid(request, "target"), text(request, "group", 32), action.equals("group-add"));
                    case "label-player" -> labelPlayer(attributed, uuid(request, "target"), text(request, "label", 16), text(request, "hours", 6));
                    case "label-window" -> labelWindow(attributed, uuid(request, "window"), text(request, "label", 16));
                    case "train-model" -> plugin.watchdog().model().train("requested from the console by " + actor)
                        ? "Training started. Results appear in the Watchdog view in a few seconds."
                        : "A training run is already in progress.";
                    default -> {
                        if (!MODERATION.contains(action)) throw new IllegalArgumentException("Unknown action.");
                        yield plugin.staff().panelModerate(attributed, action, uuid(request, "target"),
                            text(request, "minutes", 16), text(request, "reason", 180));
                    }
                };
                plugin.getLogger().info("Web console: " + actor + " ran " + action + " — " + result);
                publish();
                JsonObject body = new JsonObject();
                body.addProperty("message", result);
                return Reply.ok(body);
            } catch (IllegalArgumentException refused) {
                return Reply.error(422, refused.getMessage());
            } catch (java.io.IOException failure) {
                return Reply.error(500, "The change could not be saved: " + failure.getMessage());
            }
        });
    }

    // ---- Actions ----

    private String broadcast(String actor, String message) {
        requireText(message, "Write a message between 1 and 256 characters.");
        Bukkit.broadcast(Component.text("[Staff] ", NamedTextColor.GOLD).append(Component.text(message, NamedTextColor.WHITE)));
        return "Broadcast sent to " + Bukkit.getOnlinePlayers().size() + " players.";
    }

    private String message(String actor, UUID target, String message) {
        requireText(message, "Write a message between 1 and 256 characters.");
        Player online = Bukkit.getPlayer(target);
        if (online == null) throw new IllegalArgumentException("That player is offline.");
        online.sendMessage(Component.text("[Staff] ", NamedTextColor.GOLD).append(Component.text(message, NamedTextColor.WHITE)));
        return "Message delivered to " + online.getName() + ".";
    }

    private String clearWatchdog(String actor, UUID target) {
        if (!plugin.watchdog().clear(target)) throw new IllegalArgumentException("That player has no live Watchdog buffer.");
        plugin.staff().panelModerate(actor, "note", target, null, "Cleared live Watchdog score");
        return "Cleared the live Watchdog score. Saved evidence is kept.";
    }

    private static int label(String raw) {
        return switch (raw == null ? "" : raw) {
            case "cheating" -> 1;
            case "legit" -> 0;
            case "clear" -> -1;
            default -> throw new IllegalArgumentException("Choose cheating, legit or clear.");
        };
    }

    private String labelPlayer(String actor, UUID target, String raw, String hoursText) {
        int value = label(raw);
        long hours;
        try { hours = hoursText == null ? 24L : Long.parseLong(hoursText); } catch (NumberFormatException invalid) { throw new IllegalArgumentException("Hours must be a number."); }
        if (hours < 1 || hours > 24L * 90) throw new IllegalArgumentException("Choose between 1 hour and 90 days.");
        long now = System.currentTimeMillis();
        int changed = plugin.watchdog().model().labelPlayer(target, value, now - hours * 3_600_000L, now, actor);
        if (changed == 0) throw new IllegalArgumentException("No combat windows from that player in that period.");
        String name = Bukkit.getOfflinePlayer(target).getName();
        plugin.staff().panelModerate(actor, "note", target, null, "Watchdog training label: " + (value == 1 ? "cheating" : value == 0 ? "legitimate" : "cleared")
            + " for the last " + hours + " h (" + changed + " combat windows)");
        return (value < 0 ? "Cleared labels on " : "Labelled ") + changed + " combat windows for " + (name == null ? "player" : name) + ". The model retrains in about a minute.";
    }

    private String labelWindow(String actor, UUID window, String raw) {
        if (!plugin.watchdog().model().labelWindow(window, label(raw), actor)) throw new IllegalArgumentException("That combat window no longer exists.");
        return "Window labelled. The model retrains in about a minute.";
    }

    private String membership(String actor, UUID target, String group, boolean add) throws java.io.IOException {
        if (group == null) throw new IllegalArgumentException("Choose a group.");
        var registry = plugin.staffPermissions().registry();
        if (!registry.snapshot().groups().containsKey(group)) throw new IllegalArgumentException("That group does not exist.");
        registry.membership("panel:" + actor, target, group, add);
        Player online = Bukkit.getPlayer(target);
        if (online != null) plugin.staffPermissions().apply(online);
        String name = Bukkit.getOfflinePlayer(target).getName();
        return (add ? "Added " : "Removed ") + (name == null ? "player" : name) + (add ? " to " : " from ") + group + ".";
    }

    // ---- Snapshot ----

    private void publish() {
        try {
            sample();
            if (publishes++ % DIRECTORY_EVERY == 0) directory = buildDirectory();
            JsonObject root = new JsonObject();
            root.addProperty("status", "online");
            root.addProperty("updated", Instant.now().toString());
            root.add("server", server());
            root.add("history", history());
            root.add("worlds", worlds());
            root.add("players", onlinePlayers());
            root.add("directory", directory);
            root.add("bans", bans());
            root.add("geography", plugin.connections().panelGeography());
            root.add("watchdog", plugin.watchdog().panelSnapshot());
            root.add("desk", plugin.staff().panelSnapshot());
            root.add("staff", staff());
            snapshot = PanelBackend.json(root);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Web console snapshot failed", failure);
        }
    }

    private void sample() {
        Runtime runtime = Runtime.getRuntime();
        int entities = 0;
        int chunks = 0;
        for (World world : Bukkit.getWorlds()) {
            entities += world.getEntityCount();
            chunks += world.getChunkCount();
        }
        samples.addLast(new double[] {
            System.currentTimeMillis(), Math.min(20.0D, Bukkit.getTPS()[0]), Bukkit.getAverageTickTime(),
            Bukkit.getOnlinePlayers().size(), (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576.0D,
            cpu() * 100.0D, entities, chunks
        });
        while (samples.size() > HISTORY) samples.removeFirst();
    }

    private JsonObject history() {
        String[] names = {"t", "tps", "mspt", "players", "memory", "cpu", "entities", "chunks"};
        JsonObject root = new JsonObject();
        for (int column = 0; column < names.length; column++) {
            JsonArray values = new JsonArray();
            for (double[] sample : samples) values.add(column == 0 ? (long) sample[column] : round(sample[column]));
            root.add(names[column], values);
        }
        return root;
    }

    private JsonObject server() {
        Runtime runtime = Runtime.getRuntime();
        double[] tps = Bukkit.getTPS();
        long[] ticks = Bukkit.getTickTimes();
        long[] sorted = Arrays.copyOf(ticks, ticks.length);
        Arrays.sort(sorted);
        JsonObject root = new JsonObject();
        root.addProperty("name", Bukkit.getName());
        root.addProperty("version", Bukkit.getVersion());
        root.addProperty("minecraft", Bukkit.getMinecraftVersion());
        root.addProperty("plugin", plugin.getPluginMeta().getVersion());
        root.addProperty("java", System.getProperty("java.version"));
        root.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        root.addProperty("cores", runtime.availableProcessors());
        root.addProperty("uptime", ManagementFactory.getRuntimeMXBean().getUptime());
        root.addProperty("online", Bukkit.getOnlinePlayers().size());
        root.addProperty("maxPlayers", Bukkit.getMaxPlayers());
        root.addProperty("whitelist", Bukkit.hasWhitelist());
        root.addProperty("onlineMode", Bukkit.getOnlineMode());
        root.addProperty("viewDistance", Bukkit.getViewDistance());
        root.addProperty("simulationDistance", Bukkit.getSimulationDistance());
        root.addProperty("tps1", round(Math.min(20.0D, tps[0])));
        root.addProperty("tps5", round(Math.min(20.0D, tps[1])));
        root.addProperty("tps15", round(Math.min(20.0D, tps[2])));
        root.addProperty("mspt", round(Bukkit.getAverageTickTime()));
        root.addProperty("msptP95", sorted.length == 0 ? 0.0D : round(sorted[(int) Math.floor(sorted.length * 0.95D)] / 1_000_000.0D));
        root.addProperty("msptMax", sorted.length == 0 ? 0.0D : round(sorted[sorted.length - 1] / 1_000_000.0D));
        root.addProperty("memoryUsed", (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576L);
        root.addProperty("memoryAllocated", runtime.totalMemory() / 1_048_576L);
        root.addProperty("memoryMax", runtime.maxMemory() / 1_048_576L);
        root.addProperty("cpu", round(cpu() * 100.0D));
        root.addProperty("systemCpu", round(systemCpu() * 100.0D));
        root.addProperty("threads", ManagementFactory.getThreadMXBean().getThreadCount());
        long gcCount = 0;
        long gcMillis = 0;
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0, collector.getCollectionCount());
            gcMillis += Math.max(0, collector.getCollectionTime());
        }
        root.addProperty("gcCount", gcCount);
        root.addProperty("gcMillis", gcMillis);
        return root;
    }

    private JsonArray worlds() {
        JsonArray array = new JsonArray();
        for (World world : Bukkit.getWorlds()) {
            JsonObject row = new JsonObject();
            row.addProperty("name", world.getName());
            row.addProperty("environment", world.getEnvironment().name());
            row.addProperty("players", world.getPlayerCount());
            row.addProperty("entities", world.getEntityCount());
            row.addProperty("tileEntities", world.getTileEntityCount());
            row.addProperty("chunks", world.getChunkCount());
            row.addProperty("time", world.getTime());
            row.addProperty("storm", world.hasStorm());
            row.addProperty("difficulty", world.getDifficulty().name());
            array.add(row);
        }
        return array;
    }

    private JsonArray onlinePlayers() {
        JsonArray array = new JsonArray();
        var users = plugin.staffPermissions().registry().snapshot().users();
        Bukkit.getOnlinePlayers().stream().sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).forEach(player -> {
            JsonObject row = new JsonObject();
            UUID id = player.getUniqueId();
            row.addProperty("uuid", id.toString());
            row.addProperty("name", player.getName());
            row.addProperty("ping", player.getPing());
            row.addProperty("world", player.getWorld().getName());
            row.addProperty("x", player.getLocation().getBlockX());
            row.addProperty("y", player.getLocation().getBlockY());
            row.addProperty("z", player.getLocation().getBlockZ());
            row.addProperty("gamemode", player.getGameMode().name());
            row.addProperty("health", round(player.getHealth()));
            row.addProperty("maxHealth", round(maxHealth(player)));
            row.addProperty("food", player.getFoodLevel());
            row.addProperty("level", player.getLevel());
            row.addProperty("op", player.isOp());
            row.addProperty("flying", player.isFlying());
            row.addProperty("brand", player.getClientBrandName());
            row.addProperty("lastLogin", player.getLastLogin());
            row.addProperty("protocol", player.getProtocolVersion());
            row.addProperty("sharedAccounts", plugin.connections().sharedAccounts(id));
            row.addProperty("score", plugin.watchdog().liveScore(id));
            row.addProperty("muted", plugin.staff().muted(id));
            row.addProperty("frozen", plugin.staff().frozen(id));
            var user = users.get(id);
            row.addProperty("groups", user == null ? "" : String.join(", ", new TreeSet<>(user.groups())));
            array.add(row);
        });
        return array;
    }

    private JsonArray buildDirectory() {
        Set<UUID> records = plugin.staff().playersWithRecords();
        JsonArray array = new JsonArray();
        Arrays.stream(Bukkit.getOfflinePlayers())
            .sorted(Comparator.comparingLong(OfflinePlayer::getLastSeen).reversed()).limit(2_000).forEach(player -> {
                JsonObject row = new JsonObject();
                row.addProperty("uuid", player.getUniqueId().toString());
                row.addProperty("name", displayName(player));
                row.addProperty("firstPlayed", player.getFirstPlayed());
                row.addProperty("lastSeen", player.getLastSeen());
                row.addProperty("banned", player.isBanned());
                row.addProperty("records", records.contains(player.getUniqueId()));
                array.add(row);
            });
        return array;
    }

    private JsonArray bans() {
        JsonArray array = new JsonArray();
        var list = Bukkit.getBanList(io.papermc.paper.ban.BanListType.PROFILE);
        list.getEntries().stream().limit(500).forEach(entry -> array.add(ban(entry)));
        return array;
    }

    private JsonObject ban(BanEntry<?> entry) {
        JsonObject row = new JsonObject();
        if (entry.getBanTarget() instanceof com.destroystokyo.paper.profile.PlayerProfile profile) {
            row.addProperty("uuid", profile.getId() == null ? null : profile.getId().toString());
            row.addProperty("name", profile.getName());
        }
        row.addProperty("reason", entry.getReason());
        row.addProperty("source", entry.getSource());
        row.addProperty("time", entry.getCreated() == null ? null : entry.getCreated().toInstant().toString());
        row.addProperty("expires", entry.getExpiration() == null ? null : entry.getExpiration().toInstant().toString());
        return row;
    }

    private JsonObject staff() {
        var state = plugin.staffPermissions().registry().snapshot();
        JsonObject root = new JsonObject();
        JsonArray users = new JsonArray();
        state.users().forEach((id, user) -> {
            if (user.groups().isEmpty()) return;
            JsonObject row = new JsonObject();
            String name = Bukkit.getOfflinePlayer(id).getName();
            row.addProperty("uuid", id.toString());
            row.addProperty("name", name == null ? id.toString().substring(0, 8) : name);
            row.addProperty("groups", String.join(", ", new TreeSet<>(user.groups())));
            row.addProperty("online", Bukkit.getPlayer(id) != null);
            users.add(row);
        });
        root.add("users", users);
        JsonArray groups = new JsonArray();
        state.groups().forEach((id, group) -> {
            JsonObject row = new JsonObject();
            row.addProperty("id", id);
            row.addProperty("parents", String.join(", ", new TreeSet<>(group.parents())));
            row.addProperty("permissions", group.permissions().size());
            groups.add(row);
        });
        root.add("groups", groups);
        JsonArray audit = new JsonArray();
        var entries = state.audit();
        for (int index = entries.size() - 1; index >= Math.max(0, entries.size() - 100); index--) {
            var entry = entries.get(index);
            JsonObject row = new JsonObject();
            row.addProperty("time", entry.time().toString());
            row.addProperty("actor", actorName(entry.actor()));
            row.addProperty("change", entry.change());
            audit.add(row);
        }
        root.add("audit", audit);
        return root;
    }

    private JsonObject playerDetail(OfflinePlayer target) {
        UUID id = target.getUniqueId();
        JsonObject root = new JsonObject();
        root.addProperty("uuid", id.toString());
        root.addProperty("name", displayName(target));
        root.addProperty("online", target.getPlayer() != null);
        root.addProperty("op", target.isOp());
        root.addProperty("firstPlayed", target.getFirstPlayed());
        root.addProperty("lastSeen", target.getLastSeen());
        root.addProperty("banned", target.isBanned());
        if (target.isBanned()) {
            var entry = Bukkit.getBanList(io.papermc.paper.ban.BanListType.PROFILE).getBanEntry(target.getPlayerProfile());
            if (entry != null) root.add("ban", ban(entry));
        }
        var user = plugin.staffPermissions().registry().snapshot().users().get(id);
        JsonArray groups = new JsonArray();
        if (user != null) new TreeSet<>(user.groups()).forEach(groups::add);
        root.add("groups", groups);
        JsonArray allGroups = new JsonArray();
        new TreeSet<>(plugin.staffPermissions().registry().snapshot().groups().keySet()).forEach(allGroups::add);
        root.add("allGroups", allGroups);
        JsonObject stats = new JsonObject();
        statistic(stats, target, "playTicks", Statistic.PLAY_ONE_MINUTE);
        statistic(stats, target, "deaths", Statistic.DEATHS);
        statistic(stats, target, "playerKills", Statistic.PLAYER_KILLS);
        statistic(stats, target, "mobKills", Statistic.MOB_KILLS);
        statistic(stats, target, "jumps", Statistic.JUMP);
        root.add("stats", stats);
        Player online = target.getPlayer();
        if (online != null) root.add("live", live(online));
        root.add("watchdog", plugin.watchdog().panelPlayer(id));
        root.add("desk", plugin.staff().panelPlayer(id));
        root.add("connection", plugin.connections().panelPlayer(id));
        return root;
    }

    private JsonObject live(Player player) {
        JsonObject root = new JsonObject();
        root.addProperty("world", player.getWorld().getName());
        root.addProperty("x", round(player.getLocation().getX()));
        root.addProperty("y", round(player.getLocation().getY()));
        root.addProperty("z", round(player.getLocation().getZ()));
        root.addProperty("gamemode", player.getGameMode().name());
        root.addProperty("health", round(player.getHealth()));
        root.addProperty("maxHealth", round(maxHealth(player)));
        root.addProperty("food", player.getFoodLevel());
        root.addProperty("saturation", round(player.getSaturation()));
        root.addProperty("level", player.getLevel());
        root.addProperty("ping", player.getPing());
        root.addProperty("brand", player.getClientBrandName());
        root.addProperty("flying", player.isFlying());
        root.addProperty("allowFlight", player.getAllowFlight());
        root.addProperty("lastLogin", player.getLastLogin());
        JsonArray effects = new JsonArray();
        player.getActivePotionEffects().forEach(effect -> {
            JsonObject row = new JsonObject();
            row.addProperty("type", effect.getType().getKey().getKey());
            row.addProperty("amplifier", effect.getAmplifier() + 1);
            row.addProperty("seconds", effect.getDuration() < 0 ? -1 : effect.getDuration() / 20);
            effects.add(row);
        });
        root.add("effects", effects);
        root.add("inventory", items(player.getInventory().getContents()));
        root.add("enderChest", items(player.getEnderChest().getContents()));
        return root;
    }

    private JsonArray items(ItemStack[] contents) {
        JsonArray array = new JsonArray();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) continue;
            JsonObject row = new JsonObject();
            row.addProperty("slot", slot);
            row.addProperty("type", item.getType().getKey().getKey());
            row.addProperty("amount", item.getAmount());
            var meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                row.addProperty("name", PlainTextComponentSerializer.plainText().serialize(meta.displayName()));
            }
            if (meta != null && !meta.getEnchants().isEmpty()) row.addProperty("enchanted", true);
            array.add(row);
        }
        return array;
    }

    // ---- Helpers ----

    private Reply onMainThread(java.util.concurrent.Callable<Reply> work) {
        if (!plugin.isEnabled()) return Reply.error(503, "The server is shutting down.");
        try {
            return Bukkit.getScheduler().callSyncMethod(plugin, work).get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException slow) {
            return Reply.error(504, "The server did not answer within 5 seconds. Try again.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Reply.error(503, "The request was interrupted.");
        } catch (java.util.concurrent.ExecutionException failure) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Web console request failed", failure.getCause());
            return Reply.error(500, "The server could not complete that request.");
        }
    }

    private static void statistic(JsonObject stats, OfflinePlayer player, String key, Statistic statistic) {
        try {
            stats.addProperty(key, player.getStatistic(statistic));
        } catch (RuntimeException unavailable) {
            // Offline statistics are unavailable on some server builds; the console shows a dash.
        }
    }

    /** Accounts whose saved data lacks a last-known name still need a label staff can act on. */
    private static String displayName(OfflinePlayer player) {
        String name = player.getName();
        return name == null ? player.getUniqueId().toString().substring(0, 8) : name;
    }

    private static double maxHealth(Player player) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0D : attribute.getValue();
    }

    private static String actorName(String actor) {
        try {
            String name = Bukkit.getOfflinePlayer(UUID.fromString(actor)).getName();
            return name == null ? actor : name;
        } catch (IllegalArgumentException notUuid) {
            return actor;
        }
    }

    private static double cpu() {
        return ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os
            ? Math.max(0.0D, os.getProcessCpuLoad()) : 0.0D;
    }

    private static double systemCpu() {
        return ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os
            ? Math.max(0.0D, os.getCpuLoad()) : 0.0D;
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private static String text(JsonObject request, String key, int maximum) {
        var element = request.get(key);
        if (element == null || element.isJsonNull()) return null;
        if (!element.isJsonPrimitive()) throw new IllegalArgumentException("Invalid " + key + ".");
        String value = element.getAsString().trim();
        if (value.length() > maximum) throw new IllegalArgumentException("The " + key + " is too long.");
        return value.isEmpty() ? null : value;
    }

    private static UUID uuid(JsonObject request, String key) {
        String value = text(request, key, 36);
        try {
            if (value == null) throw new IllegalArgumentException();
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Choose a valid " + key + ".");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank() || value.length() > 256 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(message);
        }
    }
}
