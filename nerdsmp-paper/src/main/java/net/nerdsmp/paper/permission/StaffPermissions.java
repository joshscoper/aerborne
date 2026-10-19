package net.nerdsmp.paper.permission;

import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.permissions.PermissionAttachment;
import java.io.File;
import java.util.*;

/** Uses ordinary Bukkit attachments, so existing command and event permission checks remain authoritative. */
public final class StaffPermissions implements Listener, AutoCloseable {
    private final NerdSmpPlugin plugin;
    private final PermissionRegistry registry;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    public StaffPermissions(NerdSmpPlugin plugin) throws Exception { this.plugin = plugin; registry = new PermissionRegistry(new File(plugin.getDataFolder(), "permissions.yml")); }
    public PermissionRegistry registry() { return registry; }
    public boolean canModerate(CommandSender actor, org.bukkit.OfflinePlayer target) {
        if (actor == Bukkit.getConsoleSender()) return true;
        if (!(actor instanceof Player player) || !player.hasPermission("nerdsmp.staff.moderate") || player.getUniqueId().equals(target.getUniqueId())) return false;
        return tier(player) > tier(target);
    }
    private int tier(org.bukkit.OfflinePlayer player) {
        if (player.isOp()) return 1000;
        if (player.getPlayer() != null) {
            Map<String, Boolean> access = new HashMap<>();
            for (String key : List.of("nerdsmp.permissions.manage", "nerdsmp.admin", "nerdsmp.market.review", "nerdsmp.staff.moderate", "nerdsmp.studio.builder", "nerdsmp.studio.designer", "nerdsmp.staff")) access.put(key, player.getPlayer().hasPermission(key));
            return PermissionRegistry.tier(access);
        }
        Set<String> known = new HashSet<>(); Map<String, Map<String, Boolean>> children = new HashMap<>();
        Bukkit.getPluginManager().getPermissions().forEach(permission -> { known.add(permission.getName()); children.put(permission.getName(), Map.copyOf(permission.getChildren())); });
        return PermissionRegistry.tier(PermissionRegistry.resolve(registry.snapshot(), player.getUniqueId(), known, children));
    }
    public void start() { Bukkit.getOnlinePlayers().forEach(this::apply); }
    public void apply(Player player) {
        PermissionAttachment previous = attachments.remove(player.getUniqueId()); if (previous != null) player.removeAttachment(previous);
        Set<String> known = new HashSet<>(); Map<String, Map<String, Boolean>> children = new HashMap<>();
        Bukkit.getPluginManager().getPermissions().forEach(permission -> { known.add(permission.getName()); children.put(permission.getName(), Map.copyOf(permission.getChildren())); });
        Map<String, Boolean> rules = PermissionRegistry.resolve(registry.snapshot(), player.getUniqueId(), known, children);
        PermissionAttachment attachment = player.addAttachment(plugin);
        // Expansions are already resolved; leaf/explicit denies must win Bukkit's recursive children too.
        rules.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).forEach(rule -> attachment.setPermission(rule.getKey(), rule.getValue()));
        attachments.put(player.getUniqueId(), attachment); player.recalculatePermissions(); player.updateCommands();
    }
    @EventHandler(priority = EventPriority.LOWEST) public void join(PlayerJoinEvent event) { apply(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) { PermissionAttachment old = attachments.remove(event.getPlayer().getUniqueId()); if (old != null) event.getPlayer().removeAttachment(old); }
    @Override public void close() { for (Player player : Bukkit.getOnlinePlayers()) { PermissionAttachment old = attachments.remove(player.getUniqueId()); if (old != null) player.removeAttachment(old); } attachments.clear(); }
    public boolean command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player) && sender != Bukkit.getConsoleSender() || !sender.hasPermission("nerdsmp.permissions.manage")) { sender.sendMessage("Only permission owners or the server console can manage staff access."); return true; }
        try {
            if (args.length == 0 || args[0].equalsIgnoreCase("list")) { registry.snapshot().groups().forEach((id, group) -> sender.sendMessage(id + " inherits " + group.parents() + " | " + group.permissions())); return true; }
            if (args[0].equalsIgnoreCase("audit")) { registry.snapshot().audit().stream().skip(Math.max(0, registry.snapshot().audit().size() - 15)).forEach(a -> sender.sendMessage(a.time() + " | " + a.actor() + " | " + a.change())); return true; }
            String actor = sender instanceof Player p ? p.getUniqueId().toString() : "CONSOLE";
            if (args[0].equalsIgnoreCase("group") && args.length >= 3) {
                String group = args[1];
                if (args[2].equalsIgnoreCase("create") && args.length == 3) registry.group(actor, group, null);
                else if (args[2].equalsIgnoreCase("parent") && args.length == 4) registry.group(actor, group, args[3]);
                else if (args[2].equalsIgnoreCase("permission") && args.length == 5) registry.permission(actor, group, null, args[3], value(args[4]));
                else throw new IllegalArgumentException("Use /permissions group <group> create|parent <group>|permission <node> <true|false|unset>.");
            } else if (args[0].equalsIgnoreCase("user") && args.length >= 3) {
                Player online = Bukkit.getPlayerExact(args[1]); UUID id = online == null ? UUID.fromString(args[1]) : online.getUniqueId();
                if (args[2].equalsIgnoreCase("info") && args.length == 3) { sender.sendMessage(id + " " + registry.snapshot().users().getOrDefault(id, new PermissionRegistry.User(Set.of(), Map.of()))); return true; }
                if (args.length == 4 && Set.of("add", "remove").contains(args[2])) registry.membership(actor, id, args[3], args[2].equals("add"));
                else if (args[2].equalsIgnoreCase("permission") && args.length == 5) registry.permission(actor, null, id, args[3], value(args[4]));
                else throw new IllegalArgumentException("Use /permissions user <online-name|UUID> add|remove <group>, info, or permission <node> <true|false|unset>.");
            } else throw new IllegalArgumentException("Use /permissions list|audit|group|user. Offline players must be addressed by UUID.");
            Bukkit.getOnlinePlayers().forEach(this::apply); sender.sendMessage("Saved staff access and refreshed online players.");
        } catch (Exception failure) { sender.sendMessage("Permission change refused: " + failure.getMessage()); }
        return true;
    }
    private static Boolean value(String raw) { return switch (raw.toLowerCase(Locale.ROOT)) { case "true" -> true; case "false" -> false; case "unset" -> null; default -> throw new IllegalArgumentException("Choose true, false or unset"); }; }
    public List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nerdsmp.permissions.manage")) return List.of();
        if (args.length == 1) return List.of("list", "audit", "group", "user");
        if (args.length == 2 && args[0].equals("group")) return registry.snapshot().groups().keySet().stream().sorted().toList();
        if (args.length == 2 && args[0].equals("user")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 3) return args[0].equals("group") ? List.of("create", "parent", "permission") : List.of("add", "remove", "permission", "info");
        if (args.length == 4 && Set.of("add", "remove", "parent").contains(args[2])) return registry.snapshot().groups().keySet().stream().toList();
        if (args.length == 5 && args[2].equals("permission")) return List.of("true", "false", "unset");
        return List.of();
    }
}
