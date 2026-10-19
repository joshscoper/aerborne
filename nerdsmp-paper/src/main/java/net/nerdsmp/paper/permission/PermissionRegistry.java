package net.nerdsmp.paper.permission;

import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.function.UnaryOperator;

/** Global NerdSMP permissions only. Immutable snapshots publish after a successful atomic save. */
public final class PermissionRegistry {
    public record Group(Set<String> parents, Map<String, Boolean> permissions) {
        public Group { parents = Set.copyOf(parents); permissions = Map.copyOf(permissions); permissions.keySet().forEach(PermissionRegistry::node);
            if (parents.size() > 8 || permissions.size() > 256) throw new IllegalArgumentException("Group rule limits exceeded"); }
    }
    public record User(Set<String> groups, Map<String, Boolean> permissions) {
        public User { groups = Set.copyOf(groups); permissions = Map.copyOf(permissions); permissions.keySet().forEach(PermissionRegistry::node);
            if (groups.size() > 16 || permissions.size() > 128) throw new IllegalArgumentException("User rule limits exceeded"); }
    }
    public record Audit(Instant time, String actor, String change) { }
    public record State(Map<String, Group> groups, Map<UUID, User> users, List<Audit> audit) {
        public State {
            groups = Map.copyOf(groups); users = Map.copyOf(users); audit = List.copyOf(audit);
            if (groups.isEmpty() || groups.size() > 100 || users.size() > 10000 || audit.size() > 2000) throw new IllegalArgumentException("Permission registry limits exceeded");
            for (String group : groups.keySet()) { name(group); visit(group, groups, new HashSet<>()); }
            for (User user : users.values()) if (!groups.keySet().containsAll(user.groups())) throw new IllegalArgumentException("Unknown user group");
        }
    }
    private final File file;
    private State state;
    public PermissionRegistry(File file) throws Exception {
        this.file = file;
        if (!file.exists()) { state = defaults(); save(state); }
        else state = load();
    }
    public synchronized State snapshot() { return state; }
    public synchronized void group(String actor, String id, String parent) throws IOException {
        name(id); if (parent != null) name(parent);
        Map<String, Group> groups = new TreeMap<>(state.groups());
        Group before = groups.getOrDefault(id, new Group(Set.of(), Map.of()));
        groups.put(id, new Group(parent == null ? before.parents() : Set.of(parent), before.permissions()));
        commit(actor, "group " + id + (parent == null ? " create" : " parent " + parent), groups, state.users());
    }
    public synchronized void permission(String actor, String group, UUID user, String key, Boolean allowed) throws IOException {
        node(key);
        if (group != null) {
            Group before = Objects.requireNonNull(state.groups().get(group), "Unknown group");
            Map<String, Boolean> rules = new TreeMap<>(before.permissions()); if (allowed == null) rules.remove(key); else rules.put(key, allowed);
            Map<String, Group> groups = new TreeMap<>(state.groups()); groups.put(group, new Group(before.parents(), rules));
            commit(actor, "group " + group + " permission " + key + " " + allowed, groups, state.users());
        } else {
            Objects.requireNonNull(user); User before = state.users().getOrDefault(user, new User(Set.of(), Map.of()));
            Map<String, Boolean> rules = new TreeMap<>(before.permissions()); if (allowed == null) rules.remove(key); else rules.put(key, allowed);
            Map<UUID, User> users = new HashMap<>(state.users()); users.put(user, new User(before.groups(), rules));
            commit(actor, "user " + user + " permission " + key + " " + allowed, state.groups(), users);
        }
    }
    public synchronized void membership(String actor, UUID id, String group, boolean add) throws IOException {
        if (!state.groups().containsKey(group)) throw new IllegalArgumentException("Unknown group");
        User before = state.users().getOrDefault(id, new User(Set.of(), Map.of()));
        Set<String> memberships = new HashSet<>(before.groups()); if (add) memberships.add(group); else memberships.remove(group);
        Map<UUID, User> users = new HashMap<>(state.users()); users.put(id, new User(memberships, before.permissions()));
        commit(actor, "user " + id + (add ? " add " : " remove ") + group, state.groups(), users);
    }
    private void commit(String actor, String change, Map<String, Group> groups, Map<UUID, User> users) throws IOException {
        List<Audit> history = new ArrayList<>(state.audit()); history.add(new Audit(Instant.now(), actor, change));
        if (history.size() > 2000) history.removeFirst();
        State next = new State(groups, users, history); save(next); state = next;
    }
    /** Inherited explicit denies win conflicts. User-specific rules then override group rules. */
    public static Map<String, Boolean> resolve(State state, UUID player, Set<String> knownNodes, Map<String, Map<String, Boolean>> children) {
        User user = state.users().getOrDefault(player, new User(Set.of(), Map.of()));
        Map<String, Boolean> groups = new TreeMap<>();
        Set<String> included = new HashSet<>();
        for (String id : user.groups()) collect(id, state.groups(), included);
        for (String id : included) state.groups().get(id).permissions().forEach((key, value) -> groups.merge(key, value, (a, b) -> a && b));
        Map<String, Boolean> result = expand(groups, knownNodes, children);
        result.putAll(expand(user.permissions(), knownNodes, children));
        return Map.copyOf(result);
    }
    public static int tier(Map<String, Boolean> access) {
        if (access.getOrDefault("nerdsmp.permissions.manage", false)) return 100;
        if (access.getOrDefault("nerdsmp.admin", false)) return 90;
        if (access.getOrDefault("nerdsmp.market.review", false)) return 60;
        if (access.getOrDefault("nerdsmp.staff.moderate", false)) return 50;
        if (access.getOrDefault("nerdsmp.studio.builder", false) || access.getOrDefault("nerdsmp.studio.designer", false)) return 40;
        if (access.getOrDefault("nerdsmp.staff", false)) return 30;
        return 0;
    }
    private static Map<String, Boolean> expand(Map<String, Boolean> rules, Set<String> known, Map<String, Map<String, Boolean>> children) {
        Map<String, Boolean> result = new TreeMap<>();
        for (boolean allowed : new boolean[]{true, false}) for (var rule : rules.entrySet()) if (rule.getValue() == allowed) {
            if (rule.getKey().endsWith(".*")) {
                String prefix = rule.getKey().substring(0, rule.getKey().length() - 1);
                known.stream().filter(key -> key.startsWith(prefix)).forEach(key -> expandNode(key, allowed, children, result, new HashSet<>()));
            } else expandNode(rule.getKey(), allowed, children, result, new HashSet<>());
        }
        return result;
    }
    private static void expandNode(String key, boolean value, Map<String, Map<String, Boolean>> children, Map<String, Boolean> result, Set<String> visited) {
        if (!visited.add(key) || !key.startsWith("nerdsmp.")) return;
        result.merge(key, value, (a, b) -> a && b);
        children.getOrDefault(key, Map.of()).forEach((child, positive) -> expandNode(child, value == positive, children, result, visited));
    }
    private static void collect(String id, Map<String, Group> groups, Set<String> result) {
        if (!result.add(id)) return; for (String parent : groups.get(id).parents()) collect(parent, groups, result);
    }
    private static void visit(String id, Map<String, Group> groups, Set<String> path) {
        if (!path.add(id)) throw new IllegalArgumentException("Cyclic group inheritance");
        Group group = groups.get(id); if (group == null) throw new IllegalArgumentException("Unknown parent group: " + id);
        for (String parent : group.parents()) visit(parent, groups, new HashSet<>(path));
    }
    public static void name(String id) { if (id == null || !id.matches("[a-z][a-z0-9_-]{0,31}")) throw new IllegalArgumentException("Use a lowercase group name up to 32 characters"); }
    public static void node(String node) { if (node == null || !node.matches("nerdsmp\\.[a-z0-9_-]+(?:\\.[a-z0-9_-]+)*(?:\\.\\*)?") || node.length() > 128) {
        if (!"nerdsmp.*".equals(node)) throw new IllegalArgumentException("Only NerdSMP permission nodes are supported");
    } }
    private State load() throws Exception {
        if (!file.isFile() || java.nio.file.Files.size(file.toPath()) > 8_000_000) throw new IOException("Invalid permission registry file");
        String text = java.nio.file.Files.readString(file.toPath());
        var options = new org.yaml.snakeyaml.LoaderOptions(); options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(0); options.setCodePointLimit(8_000_000);
        new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(options)).load(text);
        YamlConfiguration yaml = new YamlConfiguration(); yaml.options().codePointLimit(8_000_000); yaml.loadFromString(text); exact(yaml, Set.of("schema", "groups", "users", "audit"));
        if (!yaml.isInt("schema") || yaml.getInt("schema") != 1) throw new IllegalArgumentException("Unsupported permission schema");
        Map<String, Group> groups = new TreeMap<>();
        ConfigurationSection root = required(yaml, "groups");
        for (String id : root.getKeys(false)) { var g = required(root, id); exact(g, Set.of("parents", "permissions")); groups.put(id, new Group(strings(g, "parents"), rules(required(g, "permissions")))); }
        Map<UUID, User> users = new HashMap<>(); root = required(yaml, "users");
        for (String id : root.getKeys(false)) { var u = required(root, id); exact(u, Set.of("groups", "permissions")); users.put(UUID.fromString(id), new User(strings(u, "groups"), rules(required(u, "permissions")))); }
        List<Audit> audit = new ArrayList<>(); root = required(yaml, "audit");
        for (String id : new TreeSet<>(root.getKeys(false))) { var a = required(root, id); exact(a, Set.of("time", "actor", "change"));
            if (!a.isString("actor") || !a.isString("change")) throw new IllegalArgumentException("Invalid permission audit");
            audit.add(new Audit(Instant.parse(a.getString("time")), a.getString("actor"), a.getString("change"))); }
        return new State(groups, users, audit);
    }
    private void save(State value) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration(); yaml.set("schema", 1); yaml.createSection("groups"); yaml.createSection("users"); yaml.createSection("audit");
        value.groups().forEach((id, group) -> { String p = "groups." + id; yaml.set(p + ".parents", new ArrayList<>(new TreeSet<>(group.parents()))); writeRules(yaml.createSection(p + ".permissions"), group.permissions()); });
        value.users().forEach((id, user) -> { String p = "users." + id; yaml.set(p + ".groups", new ArrayList<>(new TreeSet<>(user.groups()))); writeRules(yaml.createSection(p + ".permissions"), user.permissions()); });
        for (int i = 0; i < value.audit().size(); i++) { var a = value.audit().get(i); String p = "audit." + String.format(Locale.ROOT, "%06d", i); yaml.set(p + ".time", a.time().toString()); yaml.set(p + ".actor", a.actor()); yaml.set(p + ".change", a.change()); }
        AtomicYamlFiles.save(yaml, file);
    }
    // Store dotted nodes as numbered entries so YAML path separators cannot reinterpret them.
    private static void writeRules(ConfigurationSection root, Map<String, Boolean> rules) { int i = 0; for (var rule : new TreeMap<>(rules).entrySet()) { String p = Integer.toString(i++); root.set(p + ".node", rule.getKey()); root.set(p + ".value", rule.getValue()); } }
    private static Map<String, Boolean> rules(ConfigurationSection root) {
        Map<String, Boolean> rules = new TreeMap<>(); for (String id : root.getKeys(false)) { var entry = required(root, id); exact(entry, Set.of("node", "value"));
            if (!entry.isString("node") || !entry.isBoolean("value") || rules.putIfAbsent(entry.getString("node"), entry.getBoolean("value")) != null) throw new IllegalArgumentException("Invalid or duplicate permission rule"); }
        return rules;
    }
    private static Set<String> strings(ConfigurationSection root, String key) { Object raw = root.get(key); if (!(raw instanceof List<?> list) || list.stream().anyMatch(v -> !(v instanceof String))) throw new IllegalArgumentException("Expected group list"); Set<String> result = new HashSet<>(); for (Object v : list) if (!result.add((String)v)) throw new IllegalArgumentException("Duplicate group"); return result; }
    private static ConfigurationSection required(ConfigurationSection root, String key) { var section = root.getConfigurationSection(key); if (section == null) throw new IllegalArgumentException("Missing permission section: " + key); return section; }
    private static void exact(ConfigurationSection root, Set<String> fields) { if (!root.getKeys(false).equals(fields)) throw new IllegalArgumentException("Unknown or missing permission fields"); }
    private static State defaults() {
        Map<String, Group> groups = new TreeMap<>();
        groups.put("helper", new Group(Set.of(), Map.of("nerdsmp.staff", true)));
        groups.put("moderator", new Group(Set.of("helper"), Map.of("nerdsmp.staff.moderate", true, "nerdsmp.staff.anticheat", true)));
        groups.put("administrator", new Group(Set.of("moderator"), Map.of("nerdsmp.market.review", true, "nerdsmp.npc.admin", true, "nerdsmp.travel.admin", true)));
        groups.put("builder", new Group(Set.of(), Map.of("nerdsmp.studio.builder", true)));
        groups.put("designer", new Group(Set.of(), Map.of("nerdsmp.studio.designer", true)));
        groups.put("tester", new Group(Set.of(), Map.of("nerdsmp.studio.tester", true)));
        groups.put("owner", new Group(Set.of("administrator", "builder", "designer"), Map.of("nerdsmp.admin", true, "nerdsmp.permissions.manage", true)));
        return new State(groups, Map.of(), List.of());
    }
}
