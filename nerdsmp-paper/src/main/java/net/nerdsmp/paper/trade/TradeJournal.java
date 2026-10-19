package net.nerdsmp.paper.trade;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;

/** Write-ahead decision to exchange two inventories. Recovery moves forward, never guesses a refund. */
final class TradeJournal {
    record Side(UUID player, AccountId account, List<String> before, List<String> after, boolean saved) {
        Side {
            Objects.requireNonNull(player); Objects.requireNonNull(account);
            before = List.copyOf(before); after = List.copyOf(after);
            if (before.size() != 36 || after.size() != 36) throw new IllegalArgumentException("Invalid trade inventory size");
            long size = 0;
            for (String value : java.util.stream.Stream.concat(before.stream(), after.stream()).toList()) {
                size += value.length();
                if (size > 524_288) throw new IllegalArgumentException("Inventory metadata is too large to trade safely");
                if (!value.isEmpty()) Base64.getDecoder().decode(value);
            }
        }
        Side acknowledged() { return new Side(player, account, before, after, true); }
    }
    record Entry(UUID id, Instant created, Side left, Side right) {
        Entry {
            Objects.requireNonNull(id); Objects.requireNonNull(created); Objects.requireNonNull(left); Objects.requireNonNull(right);
            if (left.player.equals(right.player) || left.account.equals(right.account)) throw new IllegalArgumentException("A trade needs two different accounts");
        }
        boolean complete() { return left.saved && right.saved; }
        Side side(UUID player) { return left.player.equals(player) ? left : right.player.equals(player) ? right : null; }
        boolean involves(AccountId account) { return left.account.equals(account) || right.account.equals(account); }
    }
    private final File directory;
    private final Map<UUID, Entry> pending = new LinkedHashMap<>();
    TradeJournal(File directory) {
        this.directory = directory;
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (directory.exists() && !directory.isDirectory()) throw new IllegalStateException("Trade journal is not a directory");
        if (files == null && directory.exists()) throw new IllegalStateException("Cannot read player trade journal");
        if (files == null) return;
        for (File file : files) {
            try {
                if (file.length() > 1_500_000) throw new IllegalArgumentException("Oversized trade journal");
                var yaml = new YamlConfiguration(); yaml.load(file);
                if (yaml.getInt("schema") != 1) throw new IllegalArgumentException("Unknown player trade schema");
                Entry entry = new Entry(UUID.fromString(yaml.getString("id", "")), Instant.parse(yaml.getString("created", "")), read(yaml, "left"), read(yaml, "right"));
                if (!file.getName().equals(entry.id + ".yml")) throw new IllegalArgumentException("Trade filename mismatch");
                if (entry.complete()) continue;
                if (pending.size() >= 128 || blocked(entry.left.account) || blocked(entry.right.account)
                    || forPlayer(entry.left.player).isPresent() || forPlayer(entry.right.player).isPresent()) throw new IllegalArgumentException("Conflicting or excessive pending player trades");
                pending.put(entry.id, entry);
            } catch (Exception error) { throw new IllegalStateException("Cannot load player trade " + file.getName() + "; refusing to discard it", error); }
        }
    }
    synchronized Entry begin(Side left, Side right) {
        if (left.saved || right.saved || blocked(left.account) || blocked(right.account)
            || forPlayer(left.player).isPresent() || forPlayer(right.player).isPresent()) throw new IllegalStateException("An earlier trade is still settling");
        if (pending.size() >= 128) throw new IllegalStateException("Trading is waiting for staff to review pending exchanges");
        Entry entry = new Entry(UUID.randomUUID(), Instant.now(), left, right);
        save(entry); pending.put(entry.id, entry); return entry;
    }
    synchronized void acknowledge(UUID id, UUID player) {
        Entry entry = pending.get(id); if (entry == null) return;
        Side side = entry.side(player); if (side == null) throw new IllegalArgumentException("Player is not part of this trade");
        if (side.saved) return;
        Entry next = new Entry(entry.id, entry.created, entry.left.player.equals(player) ? entry.left.acknowledged() : entry.left,
            entry.right.player.equals(player) ? entry.right.acknowledged() : entry.right);
        save(next);
        if (next.complete()) pending.remove(id); else pending.put(id, next);
    }
    synchronized List<Entry> pending() { return List.copyOf(pending.values()); }
    synchronized Optional<Entry> forPlayer(UUID player) { return pending.values().stream().filter(t -> t.side(player) != null).findFirst(); }
    synchronized boolean blocked(AccountId account) { return pending.values().stream().anyMatch(t -> t.involves(account)); }
    private static Side read(YamlConfiguration yaml, String key) {
        if (!yaml.isBoolean(key + ".saved")) throw new IllegalArgumentException("Missing save acknowledgment");
        return new Side(UUID.fromString(yaml.getString(key + ".player", "")), AccountId.parse(yaml.getString(key + ".account", "")),
            strings(yaml, key + ".before"), strings(yaml, key + ".after"), yaml.getBoolean(key + ".saved"));
    }
    private static List<String> strings(YamlConfiguration yaml, String key) {
        List<?> raw = yaml.getList(key); if (raw == null || raw.size() != 36 || raw.stream().anyMatch(v -> !(v instanceof String))) throw new IllegalArgumentException("Invalid inventory snapshots");
        return raw.stream().map(String.class::cast).toList();
    }
    private void save(Entry entry) {
        var yaml = new YamlConfiguration(); yaml.set("schema", 1); yaml.set("id", entry.id.toString()); yaml.set("created", entry.created.toString());
        write(yaml, "left", entry.left); write(yaml, "right", entry.right);
        try { AtomicYamlFiles.save(yaml, new File(directory, entry.id + ".yml")); }
        catch (java.io.IOException error) { throw new IllegalStateException("Player trade could not be saved", error); }
        // A failed archive move is harmless: the durable complete record is skipped on restart.
        if (entry.complete()) try {
            var archive = directory.toPath().resolve("completed"); Files.createDirectories(archive);
            Files.move(directory.toPath().resolve(entry.id + ".yml"), archive.resolve(entry.id + ".yml"));
        } catch (java.io.IOException ignored) { }
    }
    private static void write(YamlConfiguration yaml, String key, Side side) {
        yaml.set(key + ".player", side.player.toString()); yaml.set(key + ".account", side.account.toString());
        yaml.set(key + ".before", side.before); yaml.set(key + ".after", side.after); yaml.set(key + ".saved", side.saved);
    }
}
