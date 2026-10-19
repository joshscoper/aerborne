package net.nerdsmp.paper.social;

import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.*;
import java.util.function.UnaryOperator;

/** Canonical-account social graph; persistence succeeds before any new state is published. */
final class SocialGraph {
    record Profile(String name, Set<UUID> friends, Set<UUID> ignored) {
        Profile { Objects.requireNonNull(name); friends = Set.copyOf(friends); ignored = Set.copyOf(ignored); }
    }
    private final File file;
    private Map<UUID, Profile> profiles = Map.of();
    SocialGraph(File file, UnaryOperator<UUID> legacyIdentity) {
        this.file = file;
        if (!file.exists()) return;
        try {
            var yaml = new YamlConfiguration(); yaml.options().codePointLimit(32 * 1024 * 1024); yaml.load(file);
            int schema = yaml.getInt("schema", 1); if (schema < 1 || schema > 2) throw new IllegalArgumentException("Unknown social schema");
            var players = yaml.getConfigurationSection("players");
            if (players == null) { if (yaml.contains("players")) throw new IllegalArgumentException("Invalid players section"); return; }
            Map<UUID, Profile> loaded = new HashMap<>();
            UnaryOperator<UUID> identity = schema == 1 ? legacyIdentity : id -> id;
            for (String raw : players.getKeys(false)) {
                UUID id = identity.apply(UUID.fromString(raw));
                String name = players.getString(raw + ".name", "");
                if (name.isBlank() || name.length() > 64) throw new IllegalArgumentException("Invalid social name");
                Set<UUID> friends = ids(players.get(raw + ".friends"), identity), ignored = ids(players.get(raw + ".ignored"), identity);
                friends.remove(id); ignored.remove(id);
                Profile previous = loaded.get(id);
                if (previous != null) { friends.addAll(previous.friends); ignored.addAll(previous.ignored); }
                loaded.put(id, new Profile(name, friends, ignored));
            }
            // Preserve blocks across legacy account links; a block always takes precedence over friendship.
            for (var entry : List.copyOf(loaded.entrySet())) {
                Set<UUID> friends = new HashSet<>(entry.getValue().friends);
                friends.removeIf(other -> entry.getValue().ignored.contains(other) || loaded.getOrDefault(other, empty(other)).ignored.contains(entry.getKey()));
                loaded.put(entry.getKey(), new Profile(entry.getValue().name, friends, entry.getValue().ignored));
            }
            for (var entry : loaded.entrySet()) for (UUID other : entry.getValue().friends)
                if (!loaded.getOrDefault(other, empty(other)).friends.contains(entry.getKey())) throw new IllegalArgumentException("Asymmetric social friendship");
            profiles = Map.copyOf(loaded);
            if (schema == 1) commit(loaded);
        } catch (Exception error) { throw new IllegalStateException("Cannot load social data; refusing to erase friendships or blocks", error); }
    }
    private static Set<UUID> ids(Object raw, UnaryOperator<UUID> identity) {
        if (raw == null) return new HashSet<>();
        if (!(raw instanceof List<?> list)) throw new IllegalArgumentException("Invalid social IDs");
        Set<UUID> result = new HashSet<>();
        for (Object value : list) { if (!(value instanceof String id)) throw new IllegalArgumentException("Invalid social ID"); result.add(identity.apply(UUID.fromString(id))); }
        return result;
    }
    private static Profile empty(UUID id) { return new Profile(id.toString().substring(0, 8), Set.of(), Set.of()); }
    synchronized Profile profile(UUID id) { return profiles.getOrDefault(id, empty(id)); }
    synchronized Map<UUID, Profile> profiles() { return profiles; }
    synchronized boolean blocked(UUID a, UUID b) { return profile(a).ignored.contains(b) || profile(b).ignored.contains(a); }
    synchronized void remember(UUID id, String name) {
        var before = profile(id); if (name.equals(before.name)) return;
        var next = new HashMap<>(profiles); next.put(id, new Profile(name, before.friends, before.ignored)); commit(next);
    }
    synchronized void friend(UUID a, UUID b) {
        if (a.equals(b) || blocked(a, b)) throw new IllegalArgumentException("That friendship is no longer available.");
        if (profile(a).friends.contains(b)) return;
        if (profile(a).friends.size() >= 200 || profile(b).friends.size() >= 200) throw new IllegalArgumentException("One of the friends lists is full (200).");
        var next = new HashMap<>(profiles); alterFriend(next, a, b, true); alterFriend(next, b, a, true); commit(next);
    }
    synchronized void unfriend(UUID a, UUID b) {
        var next = new HashMap<>(profiles); alterFriend(next, a, b, false); alterFriend(next, b, a, false); commit(next);
    }
    synchronized void ignore(UUID a, UUID b, boolean value) {
        if (a.equals(b)) throw new IllegalArgumentException("You cannot ignore yourself.");
        var next = new HashMap<>(profiles); Profile before = profile(a); Set<UUID> ignored = new HashSet<>(before.ignored);
        if (value && ignored.size() >= 500 && !ignored.contains(b)) throw new IllegalArgumentException("Your ignore list is full (500).");
        if (value) ignored.add(b); else ignored.remove(b);
        next.put(a, new Profile(before.name, before.friends, ignored));
        if (value) { alterFriend(next, a, b, false); alterFriend(next, b, a, false); }
        commit(next);
    }
    private void alterFriend(Map<UUID, Profile> next, UUID a, UUID b, boolean add) {
        Profile before = next.getOrDefault(a, empty(a)); Set<UUID> friends = new HashSet<>(before.friends);
        if (add) friends.add(b); else friends.remove(b);
        next.put(a, new Profile(before.name, friends, before.ignored));
    }
    private void commit(Map<UUID, Profile> next) {
        var yaml = new YamlConfiguration(); yaml.set("schema", 2);
        next.forEach((id, profile) -> {
            String key = "players." + id; yaml.set(key + ".name", profile.name);
            yaml.set(key + ".friends", profile.friends.stream().map(UUID::toString).sorted().toList());
            yaml.set(key + ".ignored", profile.ignored.stream().map(UUID::toString).sorted().toList());
        });
        try { AtomicYamlFiles.save(yaml, file); }
        catch (java.io.IOException error) { throw new IllegalStateException("Social changes could not be saved", error); }
        profiles = Map.copyOf(next);
    }
}
