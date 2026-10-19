package net.nerdsmp.paper.anticheat.ml;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the combat model's whole lifecycle: storing every combat window, staff labels, training off the main thread,
 * scoring live windows with reasons, and the autonomous action ladder. Main thread only, except training, which
 * works on a detached copy and publishes the result back on the main thread.
 *
 * <p>Autonomy: when enabled in config, the model kicks and then bans by itself, but only while its latest training
 * run passed {@link ModelTrainer.Requirements}: enough labelled players of both kinds, and the target precision on
 * players it was never trained on. A failed or stale validation drops it back to advisory automatically.
 */
public final class ModelService implements AutoCloseable {
    public static final String ACTOR = "Watchdog AI";

    public record Window(UUID id, UUID player, String name, long time, double[] features, int label, String labeledBy,
                         double probability, double anomaly) {
        Window withLabel(int value, String actor) {
            return new Window(id, player, name, time, features, value, actor, probability, anomaly);
        }
    }

    private record Loaded(GradientBoostedTrees model, ModelTrainer.Result metrics, long trainedAt) { }

    private static final int MAX_UNLABELLED = 20_000;
    private final NerdSmpPlugin plugin;
    private final File directory;
    private final boolean enabled;
    private final boolean autonomousAllowed;
    private final ModelTrainer.Requirements requirements;
    private final int kickAfterWindows;
    private final long banMinutes;
    private final LinkedHashMap<UUID, Window> windows = new LinkedHashMap<>();
    private final Baseline baseline = new Baseline();
    private final Map<UUID, Deque<Long>> flagged = new HashMap<>();
    private final Map<UUID, Long> lastAiKick = new HashMap<>();
    private final AtomicBoolean training = new AtomicBoolean();
    private volatile Loaded loaded;
    private volatile String lastTrainingNote = "Not trained yet.";
    private boolean dirty;
    private long labelsChangedAt;
    private BukkitTask task;

    public ModelService(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        directory = new File(plugin.getDataFolder(), "security/watchdog-ml");
        var config = plugin.getConfig();
        enabled = config.getBoolean("anti-cheat.ml.enabled", true);
        autonomousAllowed = config.getBoolean("anti-cheat.ml.autonomous", true);
        requirements = new ModelTrainer.Requirements(
            config.getInt("anti-cheat.ml.min-cheat-windows", 100), config.getInt("anti-cheat.ml.min-cheat-players", 10),
            config.getInt("anti-cheat.ml.min-legit-windows", 300), config.getInt("anti-cheat.ml.min-legit-players", 30),
            Math.max(0.95D, config.getDouble("anti-cheat.ml.target-precision", 0.995D)), config.getDouble("anti-cheat.ml.min-recall", 0.25D));
        kickAfterWindows = Math.max(1, config.getInt("anti-cheat.ml.kick-after-windows", 2));
        banMinutes = Math.max(1L, Math.min(43_200L, config.getLong("anti-cheat.ml.ban-minutes", 10_080L)));
        load();
    }

    public void start() {
        // Save every five minutes; retrain one minute after the last label change.
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (labelsChangedAt > 0 && System.currentTimeMillis() - labelsChangedAt > 60_000L) { labelsChangedAt = 0; train("labels changed"); }
            if (dirty) saveAsync();
        }, 1_200L, 6_000L);
    }

    @Override
    public void close() {
        if (task != null) task.cancel();
        if (dirty) writeWindows(snapshot());
    }

    public boolean autonomous() {
        Loaded current = loaded;
        if (!enabled || !autonomousAllowed || current == null || !current.metrics().autonomyReady()) return false;
        var m = current.metrics(); // re-check against today's config, so tightening it takes effect without retraining
        return m.precision() >= requirements.targetPrecision() && m.recall() >= requirements.minRecall()
            && m.cheatWindows() >= requirements.minCheatWindows() && m.cheatPlayers() >= requirements.minCheatPlayers()
            && m.legitWindows() >= requirements.minLegitWindows() && m.legitPlayers() >= requirements.minLegitPlayers();
    }

    /** Scores a finished combat window, stores it and applies the autonomous policy. Returns the probability or -1. */
    public double onWindow(Player player, double[] features) {
        if (!enabled) return -1.0D;
        Loaded current = loaded;
        double probability = current == null ? -1.0D : current.model().probability(features);
        double anomaly = baseline.anomaly(features, null);
        baseline.observe(features);
        Window window = new Window(UUID.randomUUID(), player.getUniqueId(), player.getName(), System.currentTimeMillis(), features.clone(),
            -1, null, probability, anomaly);
        windows.put(window.id(), window);
        trimUnlabelled();
        dirty = true;
        if (autonomous() && probability >= current.metrics().threshold()) act(player, probability, features, current);
        return probability;
    }

    private void act(Player player, double probability, double[] features, Loaded current) {
        long now = System.currentTimeMillis();
        Deque<Long> recent = flagged.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        recent.addLast(now);
        while (!recent.isEmpty() && recent.getFirst() < now - 600_000L) recent.removeFirst();
        String reasons = topReasons(current.model(), features, 3).asList().stream().map(r -> r.getAsJsonObject().get("feature").getAsString()).reduce((a, b) -> a + ", " + b).orElse("combat pattern");
        String detail = String.format(Locale.ROOT, "Automated: combat model %.1f%% (%s)", probability * 100, reasons);
        if (detail.length() > 180) detail = detail.substring(0, 179) + "…";
        Long kickedAt = lastAiKick.get(player.getUniqueId());
        try {
            if (kickedAt != null && now - kickedAt < 86_400_000L) {
                plugin.staff().panelModerate(ACTOR, "ban", player.getUniqueId(), Long.toString(banMinutes), detail);
                plugin.watchdog().recordModelAction(player.getUniqueId(), player.getName(), probability, "AI_BAN", detail);
                lastAiKick.remove(player.getUniqueId());
                recent.clear();
            } else if (recent.size() >= kickAfterWindows) {
                lastAiKick.put(player.getUniqueId(), now);
                plugin.watchdog().recordModelAction(player.getUniqueId(), player.getName(), probability, "AI_KICK", detail);
                plugin.staff().panelModerate(ACTOR, "kick", player.getUniqueId(), null, detail);
                recent.clear();
            } else {
                plugin.watchdog().recordModelAction(player.getUniqueId(), player.getName(), probability, "AI_FLAG", detail);
            }
        } catch (IllegalArgumentException refused) {
            plugin.getLogger().warning("Watchdog AI could not act on " + player.getName() + ": " + refused.getMessage());
        }
    }

    // ---- Labels and training ----

    /** Labels a player's windows inside [from, to]. label: 1 cheating, 0 legitimate, -1 clear. Returns the count changed. */
    public int labelPlayer(UUID player, int label, long fromMillis, long toMillis, String actor) {
        int changed = 0;
        for (var entry : windows.entrySet()) {
            Window window = entry.getValue();
            if (window.player().equals(player) && window.time() >= fromMillis && window.time() <= toMillis && window.label() != label) {
                entry.setValue(window.withLabel(label, label < 0 ? null : actor));
                changed++;
            }
        }
        if (changed > 0) { dirty = true; labelsChangedAt = System.currentTimeMillis(); }
        return changed;
    }

    public boolean labelWindow(UUID windowId, int label, String actor) {
        Window window = windows.get(windowId);
        if (window == null) return false;
        windows.put(windowId, window.withLabel(label, label < 0 ? null : actor));
        dirty = true;
        labelsChangedAt = System.currentTimeMillis();
        return true;
    }

    /** Starts a background training run. Returns false if one is already running. */
    public boolean train(String reason) {
        if (!training.compareAndSet(false, true)) return false;
        List<ModelTrainer.Sample> samples = new ArrayList<>();
        for (Window window : windows.values()) if (window.label() >= 0) samples.add(new ModelTrainer.Sample(window.player(), window.features(), window.label()));
        lastTrainingNote = "Training on " + samples.size() + " labelled windows…";
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ModelTrainer.Result result;
            try {
                result = ModelTrainer.train(samples, requirements, GradientBoostedTrees.Params.defaults());
            } catch (RuntimeException failure) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Watchdog model training failed", failure);
                lastTrainingNote = "Training failed: " + failure.getMessage();
                training.set(false);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                training.set(false);
                lastTrainingNote = result.verdict();
                if (result.model() == null) return;
                loaded = new Loaded(result.model(), result, System.currentTimeMillis());
                saveModel(loaded);
                plugin.getLogger().info("Watchdog model retrained (" + reason + "): " + result.verdict());
            });
        });
        return true;
    }

    // ---- Console ----

    public JsonObject panelSnapshot() {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", enabled);
        root.addProperty("autonomyAllowed", autonomousAllowed);
        root.addProperty("autonomous", autonomous());
        root.addProperty("training", training.get());
        root.addProperty("note", lastTrainingNote);
        int cheat = 0, legit = 0;
        java.util.Set<UUID> cheatPlayers = new java.util.HashSet<>(), legitPlayers = new java.util.HashSet<>();
        for (Window window : windows.values()) {
            if (window.label() == 1) { cheat++; cheatPlayers.add(window.player()); }
            else if (window.label() == 0) { legit++; legitPlayers.add(window.player()); }
        }
        root.addProperty("windows", windows.size());
        root.addProperty("cheatWindows", cheat);
        root.addProperty("cheatPlayers", cheatPlayers.size());
        root.addProperty("legitWindows", legit);
        root.addProperty("legitPlayers", legitPlayers.size());
        root.addProperty("baselineSamples", baseline.samples());
        JsonObject needs = new JsonObject();
        needs.addProperty("cheatWindows", requirements.minCheatWindows());
        needs.addProperty("cheatPlayers", requirements.minCheatPlayers());
        needs.addProperty("legitWindows", requirements.minLegitWindows());
        needs.addProperty("legitPlayers", requirements.minLegitPlayers());
        needs.addProperty("precision", requirements.targetPrecision());
        needs.addProperty("recall", requirements.minRecall());
        root.add("requirements", needs);
        Loaded current = loaded;
        if (current != null) {
            var m = current.metrics();
            JsonObject metrics = new JsonObject();
            metrics.addProperty("trainedAt", current.trainedAt());
            metrics.addProperty("threshold", m.threshold());
            metrics.addProperty("precision", m.precision());
            metrics.addProperty("recall", m.recall());
            metrics.addProperty("auc", m.auc());
            metrics.addProperty("trees", m.trees());
            metrics.addProperty("autonomyReady", m.autonomyReady());
            metrics.addProperty("verdict", m.verdict());
            root.add("model", metrics);
        }
        return root;
    }

    public JsonObject panelPlayer(UUID player) {
        JsonObject root = new JsonObject();
        Loaded current = loaded;
        JsonArray array = new JsonArray();
        windows.values().stream().filter(w -> w.player().equals(player)).sorted(Comparator.comparingLong(Window::time).reversed()).limit(25).forEach(window -> {
            JsonObject row = new JsonObject();
            row.addProperty("id", window.id().toString());
            row.addProperty("time", Instant.ofEpochMilli(window.time()).toString());
            double probability = current == null ? window.probability() : current.model().probability(window.features());
            row.addProperty("probability", probability);
            row.addProperty("anomaly", window.anomaly());
            row.addProperty("label", window.label());
            row.addProperty("labeledBy", window.labeledBy());
            row.add("reasons", current == null ? anomalyReasons(window.features()) : topReasons(current.model(), window.features(), 3));
            JsonObject values = new JsonObject();
            values.addProperty("cps", round(window.features()[Features.CPS]));
            values.addProperty("reach", round(window.features()[Features.REACH_MAX]));
            values.addProperty("aimOffset", round(window.features()[Features.AIM_OFFSET_MEAN]));
            row.add("values", values);
            array.add(row);
        });
        root.add("windows", array);
        if (current != null) root.addProperty("threshold", current.metrics().threshold());
        return root;
    }

    /** Latest window probability per player, for list views. */
    public double latestProbability(UUID player) {
        Loaded current = loaded;
        Window latest = null;
        for (Window window : windows.values()) if (window.player().equals(player) && (latest == null || window.time() > latest.time())) latest = window;
        if (latest == null || current == null) return -1.0D;
        return current.model().probability(latest.features());
    }

    private static JsonArray topReasons(GradientBoostedTrees model, double[] features, int count) {
        double[] contributions = model.explain(features);
        Integer[] order = new Integer[contributions.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Double.compare(contributions[b], contributions[a]));
        JsonArray reasons = new JsonArray();
        for (int i = 0; i < Math.min(count, order.length); i++) {
            if (contributions[order[i]] <= 0.0D) break;
            JsonObject reason = new JsonObject();
            reason.addProperty("feature", Features.NAMES.get(order[i]));
            reason.addProperty("weight", round(contributions[order[i]]));
            reason.addProperty("value", round(features[order[i]]));
            reasons.add(reason);
        }
        return reasons;
    }

    private JsonArray anomalyReasons(double[] features) {
        int[] top = new int[3];
        JsonArray reasons = new JsonArray();
        if (baseline.anomaly(features, top) <= 0.0D) return reasons;
        for (int feature : top) {
            JsonObject reason = new JsonObject();
            reason.addProperty("feature", Features.NAMES.get(feature));
            reason.addProperty("value", round(features[feature]));
            reasons.add(reason);
        }
        return reasons;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }

    // ---- Persistence ----

    private void trimUnlabelled() {
        int unlabelled = 0;
        for (Window window : windows.values()) if (window.label() < 0) unlabelled++;
        var iterator = windows.values().iterator();
        while (unlabelled > MAX_UNLABELLED && iterator.hasNext()) {
            if (iterator.next().label() < 0) { iterator.remove(); unlabelled--; }
        }
    }

    private List<Window> snapshot() {
        return List.copyOf(windows.values());
    }

    private void saveAsync() {
        dirty = false;
        List<Window> copy = snapshot();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeWindows(copy));
    }

    private synchronized void writeWindows(List<Window> copy) {
        try {
            Files.createDirectories(directory.toPath());
            File target = new File(directory, "windows.jsonl");
            File temporary = new File(directory, "windows.jsonl.tmp");
            try (Writer writer = Files.newBufferedWriter(temporary.toPath(), StandardCharsets.UTF_8)) {
                for (Window window : copy) {
                    JsonObject row = new JsonObject();
                    row.addProperty("id", window.id().toString());
                    row.addProperty("player", window.player().toString());
                    row.addProperty("name", window.name());
                    row.addProperty("time", window.time());
                    JsonArray features = new JsonArray();
                    for (double value : window.features()) features.add(value);
                    row.add("features", features);
                    row.addProperty("label", window.label());
                    if (window.labeledBy() != null) row.addProperty("labeledBy", window.labeledBy());
                    row.addProperty("probability", window.probability());
                    row.addProperty("anomaly", window.anomaly());
                    writer.write(row.toString());
                    writer.write('\n');
                }
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            plugin.getLogger().warning("Could not save Watchdog combat windows: " + failure.getMessage());
            dirty = true;
        }
    }

    private void saveModel(Loaded current) {
        JsonObject root = new JsonObject();
        root.add("model", current.model().toJson());
        var m = current.metrics();
        root.addProperty("trainedAt", current.trainedAt());
        root.addProperty("threshold", m.threshold());
        root.addProperty("precision", m.precision());
        root.addProperty("recall", m.recall());
        root.addProperty("auc", m.auc());
        root.addProperty("cheatWindows", m.cheatWindows());
        root.addProperty("cheatPlayers", m.cheatPlayers());
        root.addProperty("legitWindows", m.legitWindows());
        root.addProperty("legitPlayers", m.legitPlayers());
        root.addProperty("trees", m.trees());
        root.addProperty("autonomyReady", m.autonomyReady());
        root.addProperty("verdict", m.verdict());
        try {
            Files.createDirectories(directory.toPath());
            File temporary = new File(directory, "model.json.tmp");
            Files.writeString(temporary.toPath(), root.toString(), StandardCharsets.UTF_8);
            Files.move(temporary.toPath(), new File(directory, "model.json").toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            plugin.getLogger().warning("Could not save Watchdog model: " + failure.getMessage());
        }
    }

    private void load() {
        File windowFile = new File(directory, "windows.jsonl");
        if (windowFile.isFile()) {
            try (BufferedReader reader = Files.newBufferedReader(windowFile.toPath(), StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    try {
                        JsonObject row = JsonParser.parseString(line).getAsJsonObject();
                        JsonArray array = row.getAsJsonArray("features");
                        double[] features = new double[Features.COUNT];
                        for (int i = 0; i < Math.min(array.size(), Features.COUNT); i++) features[i] = array.get(i).getAsDouble();
                        Window window = new Window(UUID.fromString(row.get("id").getAsString()), UUID.fromString(row.get("player").getAsString()),
                            row.get("name").getAsString(), row.get("time").getAsLong(), features, row.get("label").getAsInt(),
                            row.has("labeledBy") ? row.get("labeledBy").getAsString() : null, row.get("probability").getAsDouble(),
                            row.get("anomaly").getAsDouble());
                        windows.put(window.id(), window);
                        baseline.observe(features);
                    } catch (RuntimeException malformed) {
                        plugin.getLogger().warning("Skipping a malformed Watchdog combat window.");
                    }
                }
            } catch (IOException failure) {
                plugin.getLogger().warning("Could not read Watchdog combat windows: " + failure.getMessage());
            }
        }
        File modelFile = new File(directory, "model.json");
        if (modelFile.isFile()) {
            try {
                JsonObject root = JsonParser.parseString(Files.readString(modelFile.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
                GradientBoostedTrees model = GradientBoostedTrees.fromJson(root.getAsJsonObject("model"));
                var metrics = new ModelTrainer.Result(model, root.get("threshold").getAsDouble(), root.get("precision").getAsDouble(),
                    root.get("recall").getAsDouble(), root.get("auc").getAsDouble(), root.get("cheatWindows").getAsInt(),
                    root.get("cheatPlayers").getAsInt(), root.get("legitWindows").getAsInt(), root.get("legitPlayers").getAsInt(),
                    root.get("trees").getAsInt(), root.get("autonomyReady").getAsBoolean(), root.get("verdict").getAsString());
                loaded = new Loaded(model, metrics, root.get("trainedAt").getAsLong());
                lastTrainingNote = metrics.verdict();
            } catch (IOException | RuntimeException failure) {
                plugin.getLogger().warning("Ignoring an unreadable Watchdog model; it will be retrained from labels: " + failure.getMessage());
            }
        }
    }
}
