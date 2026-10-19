package net.nerdsmp.paper.webapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.leaderboard.LeaderboardService;
import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Main-thread publisher. Loaded-world counters are cached Paper counts, never entity or chunk scans. */
final class ApiSnapshots implements AutoCloseable {
    private static final int MAX_WORLDS = 128;
    private final NerdSmpPlugin plugin;
    private final int intervalSeconds;
    private final Consumer<ApiSnapshot> publish;
    private final long start = System.nanoTime();
    private BukkitTask task;
    private String catalog = "{}";
    private long catalogAt;
    private static final int LEADERBOARD_SIZE = 25;
    private String leaderboards = ApiSnapshot.NO_LEADERBOARDS;
    private Map<String, String> players = Map.of();
    private Instant leaderboardsFrom;

    ApiSnapshots(NerdSmpPlugin plugin, int intervalSeconds, Consumer<ApiSnapshot> publish) {
        this.plugin = plugin;
        this.intervalSeconds = intervalSeconds;
        this.publish = publish;
    }

    void start() {
        refresh();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh,
            intervalSeconds * 20L, intervalSeconds * 20L);
    }

    void refresh() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Web API snapshots require the main thread");
        long now = System.currentTimeMillis();
        String sampled = Instant.ofEpochMilli(now).toString();
        var brand = plugin.branding();
        JsonObject status = new JsonObject();
        status.addProperty("apiVersion", 1);
        status.addProperty("ready", true);
        status.addProperty("sampledAt", sampled);
        status.addProperty("name", brand.name());
        status.addProperty("mode", brand.mode());
        status.addProperty("tagline", brand.tagline());
        status.addProperty("description", brand.description());
        status.addProperty("onlinePlayers", Bukkit.getOnlinePlayers().size());
        status.addProperty("maxPlayers", Bukkit.getMaxPlayers());

        JsonObject telemetry = new JsonObject();
        telemetry.addProperty("sampledAt", sampled);
        telemetry.addProperty("uptimeSeconds", TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start));
        telemetry.addProperty("onlinePlayers", Bukkit.getOnlinePlayers().size());
        telemetry.addProperty("maxPlayers", Bukkit.getMaxPlayers());
        telemetry.addProperty("tpsOneMinute", rounded(Bukkit.getTPS()[0], 20));
        telemetry.addProperty("averageTickMs", rounded(Bukkit.getAverageTickTime(), 60_000));
        Runtime runtime = Runtime.getRuntime();
        telemetry.addProperty("heapUsedBytes", runtime.totalMemory() - runtime.freeMemory());
        telemetry.addProperty("heapMaxBytes", runtime.maxMemory());
        List<World> worlds = Bukkit.getWorlds();
        long entities = 0;
        long chunks = 0;
        for (int index = 0; index < Math.min(MAX_WORLDS, worlds.size()); index++) {
            World world = worlds.get(index);
            entities += world.getEntityCount();
            chunks += world.getChunkCount();
        }
        telemetry.addProperty("worlds", worlds.size());
        telemetry.addProperty("loadedEntities", entities);
        telemetry.addProperty("loadedChunks", chunks);
        telemetry.addProperty("worldCountersPartial", worlds.size() > MAX_WORLDS);
        JsonObject arcane = new JsonObject();
        if (plugin.arcane() != null) plugin.arcane().telemetry().snapshot().forEach((metric, value) ->
            arcane.addProperty(metric.name().toLowerCase(Locale.ROOT), value));
        telemetry.add("arcaneOperations", arcane);
        JsonObject stacks = new JsonObject();
        if (plugin.skyblock() != null && plugin.skyblock().automation() != null) {
            var counts = plugin.skyblock().automation().stackDiagnostics();
            stacks.addProperty("queuedMobs", counts.queuedMobs());
            stacks.addProperty("trackedChunks", counts.trackedChunks());
            stacks.addProperty("mergedBodies", counts.mergedBodies());
            stacks.addProperty("mergedMembers", counts.mergedMembers());
            stacks.addProperty("markedBodies", counts.markedBodies());
            stacks.addProperty("reconciledChunks", counts.reconciledChunks());
            stacks.addProperty("reconciliationInspections", counts.reconciliationInspections());
            stacks.addProperty("lastCandidates", counts.lastCandidates());
            stacks.addProperty("lastNearbyChecks", counts.lastNearbyChecks());
            stacks.addProperty("lastChunkSnapshots", counts.lastChunkSnapshots());
            stacks.addProperty("lastPassMs", rounded(counts.lastPassNanos() / 1_000_000D, 60_000));
            stacks.addProperty("maxPassMs", rounded(counts.maxPassNanos() / 1_000_000D, 60_000));
            JsonObject rejections = new JsonObject();
            counts.rejections().entrySet().stream().filter(entry -> entry.getKey().matches("[a-z_]{1,40}"))
                .limit(32).forEach(entry -> rejections.addProperty(entry.getKey(), entry.getValue()));
            stacks.add("rejections", rejections);
        }
        telemetry.add("islandStacks", stacks);

        // Catalog metadata changes infrequently. Count it once per minute, not for every request or tick.
        if (catalogAt == 0 || now - catalogAt >= 60_000) {
            JsonObject metadata = new JsonObject();
            metadata.addProperty("apiVersion", 1);
            metadata.addProperty("sampledAt", sampled);
            metadata.addProperty("items", plugin.items() == null ? 0 : plugin.items().itemIds().size());
            metadata.addProperty("gearSets", plugin.items() == null ? 0 : plugin.items().setDefinitions().size());
            metadata.addProperty("craftingRecipes", plugin.recipes() == null ? 0 : plugin.recipes().recipes().size());
            metadata.addProperty("enchantments", plugin.arcane() == null ? 0 : plugin.arcane().registry().all().stream()
                .filter(definition -> !definition.isSignature()).count());
            metadata.addProperty("arcaneSignatures", plugin.arcane() == null ? 0 : plugin.arcane().registry().all().stream()
                .filter(definition -> definition.isSignature()).count());
            metadata.addProperty("skyblockEnabled", plugin.skyblockEnabled());
            metadata.addProperty("readOnly", true);
            catalog = metadata.toString();
            catalogAt = now;
        }
        // Rankings change once a minute; rebuild their JSON only when a new complete snapshot lands.
        LeaderboardService boards = plugin.leaderboards();
        Instant boardsUpdated = boards == null ? null : boards.updated();
        if (boardsUpdated != null && !boardsUpdated.equals(leaderboardsFrom)) {
            leaderboards = leaderboardJson(boards.rankings(), boardsUpdated);
            players = profileJson(boards.rankings(), boards.profiles(), boardsUpdated);
            leaderboardsFrom = boardsUpdated;
        }
        publish.accept(new ApiSnapshot(now, status.toString(), telemetry.toString(), catalog, leaderboards, players));
    }

    static String key(LeaderboardService.Board board) { return board.name().toLowerCase(Locale.ROOT); }

    static String leaderboardJson(Map<LeaderboardService.Board, List<LeaderboardService.Entry>> rankings, Instant updated) {
        JsonObject root = new JsonObject();
        root.addProperty("apiVersion", 1);
        root.addProperty("ready", true);
        root.addProperty("updatedAt", updated.toString());
        JsonObject all = new JsonObject();
        for (LeaderboardService.Board board : LeaderboardService.Board.values()) {
            List<LeaderboardService.Entry> entries = rankings.getOrDefault(board, List.of());
            JsonArray top = new JsonArray();
            for (int i = 0; i < Math.min(LEADERBOARD_SIZE, entries.size()); i++) {
                JsonObject row = new JsonObject();
                row.addProperty("rank", i + 1);
                row.addProperty("name", entries.get(i).name());
                row.addProperty("score", entries.get(i).score(board));
                top.add(row);
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("players", entries.size());
            entry.add("top", top);
            all.add(key(board), entry);
        }
        root.add("boards", all);
        return root.toString();
    }

    /** One small JSON document per known player, keyed by lowercase name. Only leaderboard facts are included. */
    static Map<String, String> profileJson(Map<LeaderboardService.Board, List<LeaderboardService.Entry>> rankings,
                                           Map<AccountId, LeaderboardService.Profile> profiles, Instant updated) {
        Map<AccountId, Map<LeaderboardService.Board, Integer>> positions = new HashMap<>();
        Map<AccountId, Map<LeaderboardService.Board, Long>> scores = new HashMap<>();
        for (var ranking : rankings.entrySet()) {
            List<LeaderboardService.Entry> entries = ranking.getValue();
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                positions.computeIfAbsent(entry.account(), ignored -> new LinkedHashMap<>()).put(ranking.getKey(), i + 1);
                scores.computeIfAbsent(entry.account(), ignored -> new LinkedHashMap<>()).put(ranking.getKey(), entry.score(ranking.getKey()));
            }
        }
        Map<String, String> out = new HashMap<>();
        for (var profile : profiles.entrySet()) {
            if (out.size() >= ApiSnapshot.MAX_PROFILES) break;
            String name = profile.getValue().name();
            if (!name.matches("[A-Za-z0-9_]{3,16}")) continue;
            JsonObject json = new JsonObject();
            json.addProperty("apiVersion", 1);
            json.addProperty("name", name);
            json.addProperty("updatedAt", updated.toString());
            JsonObject adventurer = new JsonObject();
            adventurer.addProperty("level", profile.getValue().adventurerLevel());
            adventurer.addProperty("rank", profile.getValue().rank());
            json.add("adventurer", adventurer);
            JsonObject skills = new JsonObject();
            profile.getValue().skillLevels().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(skill -> skills.addProperty(skill.getKey().name().toLowerCase(Locale.ROOT), skill.getValue()));
            json.add("skills", skills);
            JsonObject standing = new JsonObject();
            var mine = positions.getOrDefault(profile.getKey(), Map.of());
            var myScores = scores.getOrDefault(profile.getKey(), Map.of());
            for (LeaderboardService.Board board : LeaderboardService.Board.values()) {
                if (!mine.containsKey(board)) continue;
                JsonObject row = new JsonObject();
                row.addProperty("rank", mine.get(board));
                row.addProperty("score", myScores.getOrDefault(board, 0L));
                standing.add(key(board), row);
            }
            json.add("boards", standing);
            // Two accounts can share a last-known name after a rename; the first one wins.
            out.putIfAbsent(name.toLowerCase(Locale.ROOT), json.toString());
        }
        return out;
    }

    private static double rounded(double value, double maximum) {
        return Double.isFinite(value) ? Math.round(Math.clamp(value, 0, maximum) * 100.0) / 100.0 : 0;
    }

    @Override public void close() { if (task != null) task.cancel(); }
}
