package net.nerdsmp.paper.skyblock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.player.PlayerSettingsService.Setting;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Two non-persistent entities per nearby island; no chunk tickets or per-entity tasks. */
public final class IslandPresentation implements AutoCloseable {
    private final NerdSmpPlugin plugin;
    private final IslandService islands;
    private final World world;
    private final Map<AccountId, Anchor> anchors = new HashMap<>();
    private final Map<UUID, Reveal> reveals = new HashMap<>();
    private final BukkitTask task;
    private final int maxAnchors, range;

    public IslandPresentation(NerdSmpPlugin plugin, IslandService islands, World world) {
        this.plugin = plugin; this.islands = islands; this.world = world;
        maxAnchors = IslandVisualPolicy.anchors(plugin.getConfig().getInt("skyblock.displays.max-active-islands", 32));
        range = IslandVisualPolicy.range(plugin.getConfig().getInt("skyblock.displays.range", 24));
        task = plugin.getConfig().getBoolean("skyblock.displays.enabled", true)
            ? Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10, 10) : null;
    }
    private Location core(IslandService.Island island) {
        return new Location(world, IslandGrid.blockX(island.slot()) + .5, IslandGrid.SURFACE_Y + 4.5, IslandGrid.blockZ(island.slot()) + .5);
    }
    private AccountId account(Player p) { return plugin.sessions().account(p.getUniqueId()).orElse(null); }
    private boolean eligible(Player p, IslandService.Island island) {
        AccountId account = account(p);
        return p.isOnline() && p.getWorld().equals(world) && account != null && island.canEnter(account)
            && plugin.settings().enabled(p.getUniqueId(), Setting.ISLAND_DISPLAYS)
            && p.getLocation().distanceSquared(core(island)) <= range * range;
    }
    private void tick() {
        // Group only online viewers by O(1) cell lookup. Never scan all saved islands.
        Map<AccountId, List<Player>> nearby = new LinkedHashMap<>();
        for (Player player : world.getPlayers()) {
            Location location = player.getLocation();
            islands.at(location.getBlockX() >> 4, location.getBlockZ() >> 4).filter(i -> eligible(player, i))
                .ifPresent(i -> nearby.computeIfAbsent(i.owner(), ignored -> new ArrayList<>()).add(player));
        }
        anchors.entrySet().removeIf(entry -> {
            Anchor anchor = entry.getValue();
            if (!nearby.containsKey(entry.getKey()) || !anchor.text.isValid() || !anchor.item.isValid()) { anchor.remove(); return true; }
            return false;
        });
        for (var entry : nearby.entrySet()) {
            IslandService.Island island = islands.owned(entry.getKey()).orElseThrow();
            Anchor anchor = anchors.get(entry.getKey());
            if (anchor == null) {
                if (anchors.size() >= maxAnchors || !world.isChunkLoaded(IslandGrid.chunkX(island.slot()), IslandGrid.chunkZ(island.slot()))) continue;
                anchor = create(island); anchors.put(entry.getKey(), anchor);
            }
            int level = plugin.skyblock().development().progress(island.owner()).level();
            if (anchor.snapshot != island || anchor.level != level) update(anchor, island);
            Set<UUID> visible = new HashSet<>();
            for (Player viewer : entry.getValue()) {
                if (!viewer.hasLineOfSight(anchor.item)) continue;
                visible.add(viewer.getUniqueId());
                if (anchor.viewers.add(viewer.getUniqueId())) { viewer.showEntity(plugin, anchor.item); viewer.showEntity(plugin, anchor.text); }
            }
            Anchor current = anchor;
            anchor.viewers.removeIf(id -> {
                if (visible.contains(id)) return false;
                Player viewer = Bukkit.getPlayer(id);
                if (viewer != null) { viewer.hideEntity(plugin, current.item); viewer.hideEntity(plugin, current.text); }
                return true;
            });
        }
        reveals.entrySet().removeIf(entry -> {
            Reveal reveal = entry.getValue(); Player viewer = Bukkit.getPlayer(entry.getKey());
            IslandService.Island island = islands.owned(reveal.owner).orElse(null);
            if (viewer == null || island == null || !eligible(viewer, island)
                || !plugin.settings().enabled(viewer.getUniqueId(), Setting.ISLAND_EFFECTS)
                || !reveal.display.isValid() || reveal.age >= IslandVisualPolicy.REVEAL_TICKS
                || !viewer.hasLineOfSight(reveal.display)) { reveal.display.remove(); return true; }
            reveal.age += 10;
            reveal.display.setInterpolationDelay(0);
            reveal.display.setTextOpacity((byte) IslandVisualPolicy.opacity(reveal.age));
            transform(reveal.display, IslandVisualPolicy.scale(reveal.age), reveal.age * .006f);
            return false;
        });
    }
    private Anchor create(IslandService.Island island) {
        Location location = core(island);
        TextDisplay text = world.spawn(location.clone().add(0, .85, 0), TextDisplay.class, this::setupText);
        try {
            ItemDisplay item = world.spawn(location, ItemDisplay.class, display -> {
                setup(display); display.setBillboard(Display.Billboard.CENTER);
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI); transform(display, .75f, 0);
            });
            Anchor anchor = new Anchor(text, item); update(anchor, island); return anchor;
        } catch (RuntimeException error) { text.remove(); throw error; }
    }
    private void setup(Display display) {
        display.setVisibleByDefault(false); display.setPersistent(false); display.setInvulnerable(true);
        display.setGravity(false); display.setViewRange(range / 64f); display.setShadowRadius(0);
        display.setBrightness(new Display.Brightness(12, 12));
    }
    private void setupText(TextDisplay display) {
        setup(display); display.setBillboard(Display.Billboard.CENTER); display.setSeeThrough(false);
        display.setShadowed(true); display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(130, 14, 17, 29)); display.setLineWidth(240);
        display.setAlignment(TextDisplay.TextAlignment.CENTER); display.setInterpolationDuration(10);
    }
    private void update(Anchor anchor, IslandService.Island island) {
        var showcase = island.showcase();
        int level = plugin.skyblock().development().progress(island.owner()).level();
        anchor.text.text(Component.text(island.name(), showcase.color(), TextDecoration.BOLD)
            .append(Component.text("\nLevel " + level + "  |  " + showcase.title(), NamedTextColor.WHITE))
            .append(Component.text("\n" + IslandShowcase.progress(island), NamedTextColor.GRAY))
            .append(Component.text("\nRight-click the landing • /island", NamedTextColor.DARK_GRAY)));
        if (anchor.snapshot == null || anchor.snapshot.showcase() != showcase) anchor.item.setItemStack(new ItemStack(showcase.material()));
        anchor.snapshot = island;
        anchor.level = level;
    }
    /** Called only after a chapter's durable, non-replayed completion. Pure presentation. */
    public void celebrate(IslandService.Island island) {
        if (task == null || !world.isChunkLoaded(IslandGrid.chunkX(island.slot()), IslandGrid.chunkZ(island.slot()))) return;
        IslandShowcase unlocked = Arrays.stream(IslandShowcase.values()).filter(s -> s.chapter() == island.tier()).findFirst().orElseThrow();
        for (Player player : world.getPlayers()) {
            if (!eligible(player, island) || !island.member(account(player))
                || !plugin.settings().enabled(player.getUniqueId(), Setting.ISLAND_EFFECTS)) continue;
            Reveal previous = reveals.remove(player.getUniqueId()); if (previous != null) previous.display.remove();
            if (reveals.size() >= 64) break;
            TextDisplay display = world.spawn(core(island).add(0, 2.3, 0), TextDisplay.class, spawned -> {
                setupText(spawned); spawned.setTextOpacity((byte) 0); transform(spawned, .7f, 0);
                spawned.text(Component.text("CHAPTER COMPLETE", unlocked.color(), TextDecoration.BOLD)
                    .append(Component.text("\n" + unlocked.rarity() + " • " + unlocked.title(), NamedTextColor.WHITE))
                    .append(Component.text("\nShowcase unlocked", NamedTextColor.GRAY)));
            });
            if (!player.hasLineOfSight(display)) { display.remove(); continue; }
            reveals.put(player.getUniqueId(), new Reveal(display, island.owner())); player.showEntity(plugin, display);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, .4f, 1.2f);
            player.spawnParticle(Particle.END_ROD, core(island), 10, .4, .4, .4, .01);
        }
    }
    public void forget(Player player) {
        Reveal reveal = reveals.remove(player.getUniqueId()); if (reveal != null) reveal.display.remove();
        anchors.values().forEach(a -> { player.hideEntity(plugin, a.item); player.hideEntity(plugin, a.text); a.viewers.remove(player.getUniqueId()); });
    }
    public String diagnostics() { return "Island displays: " + anchors.size() + "/" + maxAnchors + " anchors, " + reveals.size() + "/64 reveals; 10-tick scheduler; " + range + "-block range; no chunk tickets."; }
    @Override public void close() {
        if (task != null) task.cancel(); anchors.values().forEach(Anchor::remove); anchors.clear();
        reveals.values().forEach(r -> r.display.remove()); reveals.clear();
    }
    private static void transform(Display display, float scale, float rise) {
        display.setTransformation(new Transformation(new Vector3f(0, rise, 0), new Quaternionf(), new Vector3f(scale), new Quaternionf()));
    }
    private static final class Anchor {
        final TextDisplay text; final ItemDisplay item; final Set<UUID> viewers = new HashSet<>(); IslandService.Island snapshot; int level;
        Anchor(TextDisplay text, ItemDisplay item) { this.text = text; this.item = item; }
        void remove() { text.remove(); item.remove(); }
    }
    private static final class Reveal {
        final TextDisplay display; final AccountId owner; int age;
        Reveal(TextDisplay display, AccountId owner) { this.display = display; this.owner = owner; }
    }
}
