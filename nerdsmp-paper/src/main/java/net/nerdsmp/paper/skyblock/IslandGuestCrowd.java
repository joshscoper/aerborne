package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Island visitors in person. While anyone stands on an island, its waiting visitors stand around the island spawn as
 * villagers in their own dress, wander a few blocks about, turn to face whoever comes near, and open their offer when
 * right-clicked. The saved visitor list stays the only truth: bodies are never saved with the world, are made when
 * someone arrives, and go when the island empties, a visitor is served or refused, or the plugin stops.
 * They walk by being moved a step at a time (no AI), and only ever onto ground that would hold them, so they can't
 * wander off the edge of a sky island.
 */
final class IslandGuestCrowd implements Listener, AutoCloseable {
    /** How far from the island spawn a visitor wanders. */
    static final int WANDER = 5;
    private static final double STEP = 0.11;

    private final NerdSmpPlugin plugin;
    private final SkyblockController islands;
    private final IslandGuests guests;
    private final NamespacedKey key;
    /** Live bodies, by island owner, then by visitor id. */
    private final Map<AccountId, Map<String, Body>> live = new HashMap<>();
    /** The board over each occupied island's spawn: how many visitors wait and when the next comes. */
    private final Map<AccountId, org.bukkit.entity.TextDisplay> boards = new HashMap<>();
    private BukkitTask sync, walk;

    private static final class Body {
        final Villager entity; final Location home;
        Location target; long restUntil;
        Body(Villager entity, Location home) { this.entity = entity; this.home = home; }
    }

    IslandGuestCrowd(NerdSmpPlugin plugin, SkyblockController islands, IslandGuests guests) {
        this.plugin = plugin;
        this.islands = islands;
        this.guests = guests;
        this.key = new NamespacedKey(plugin, "island_guest");
    }

    void start() {
        sync = Bukkit.getScheduler().runTaskTimer(plugin, this::sync, 40L, 20L);
        walk = Bukkit.getScheduler().runTaskTimer(plugin, this::walk, 41L, 2L);
    }

    /** A guest's plate text for the companion client, or null when the entity isn't a visitor. */
    String label(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null) return null;
        String[] parts = raw.split("\\|");
        if (parts.length < 3) return null;
        IslandGuests.Visitor visitor = IslandGuests.VISITORS.get(Math.floorMod(Integer.parseInt(parts[2]), IslandGuests.VISITORS.size()));
        return visitor.name() + " · " + visitor.rarity().label() + " Visitor";
    }

    static Location spawnOf(World world, IslandService.Island island) {
        return new Location(world, IslandGrid.blockX(island.slot()) + .5, IslandGrid.SURFACE_Y + 1, IslandGrid.blockZ(island.slot()) + .5);
    }

    // ------------------------------------------------------------------ keeping bodies in step with the saved list

    private void sync() {
        Map<AccountId, Location> occupied = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!islands.isIslandWorld(player.getWorld())) continue;
            Location at = player.getLocation();
            islands.islands().at(at.getBlockX() >> 4, at.getBlockZ() >> 4)
                .ifPresent(island -> occupied.putIfAbsent(island.owner(), spawnOf(player.getWorld(), island)));
        }
        for (Iterator<Map.Entry<AccountId, Map<String, Body>>> it = live.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (occupied.containsKey(entry.getKey())) continue;
            entry.getValue().values().forEach(body -> body.entity.remove());
            it.remove();
        }
        boards.entrySet().removeIf(entry -> {
            boolean gone = !occupied.containsKey(entry.getKey()) || !entry.getValue().isValid();
            if (gone) entry.getValue().remove();
            return gone;
        });
        for (var entry : occupied.entrySet()) {
            try { populate(entry.getKey(), entry.getValue()); board(entry.getKey(), entry.getValue()); }
            catch (RuntimeException failure) { plugin.getLogger().warning("Island visitors could not be shown for " + entry.getKey() + ": " + failure); }
        }
    }

    private void populate(AccountId owner, Location spawn) {
        if (!spawn.isChunkLoaded()) return;
        IslandGuests.State state = guests.read(owner);
        Map<String, Body> bodies = live.computeIfAbsent(owner, ignored -> new HashMap<>());
        Set<String> waiting = new HashSet<>();
        for (IslandGuests.Guest guest : state.guests()) waiting.add(guest.id());
        bodies.entrySet().removeIf(entry -> {
            boolean gone = !waiting.contains(entry.getKey()) || !entry.getValue().entity.isValid();
            if (gone) entry.getValue().entity.remove();
            return gone;
        });
        for (IslandGuests.Guest guest : state.guests()) {
            if (bodies.containsKey(guest.id())) continue;
            Location stand = standable(spawn, spawn.clone().add(ThreadLocalRandom.current().nextInt(-2, 3), 0, ThreadLocalRandom.current().nextInt(-2, 3)));
            if (stand == null) stand = standable(spawn, spawn);
            if (stand == null) return; // no ground at the spawn: nobody to show until there is
            bodies.put(guest.id(), new Body(spawn(owner, guest, stand), spawn));
        }
    }

    private void board(AccountId owner, Location spawn) {
        if (!spawn.isChunkLoaded()) return;
        org.bukkit.entity.TextDisplay board = boards.get(owner);
        if (board == null || !board.isValid()) {
            board = spawn.getWorld().spawn(spawn.clone().add(0, 2.4, 0), org.bukkit.entity.TextDisplay.class, display -> {
                display.getPersistentDataContainer().set(key, PersistentDataType.STRING, "board");
                display.setPersistent(false);
                display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                display.setShadowed(true);
                display.setBackgroundColor(org.bukkit.Color.fromARGB(96, 0, 0, 0));
                display.setViewRange(0.5F);
            });
            boards.put(owner, board);
        }
        board.text(Chat.render(guests.board(owner)));
    }

    /** A rerolled visitor keeps their body under the new offer's id. */
    void rename(AccountId owner, String from, String to) {
        Map<String, Body> bodies = live.get(owner);
        if (bodies == null) return;
        Body body = bodies.remove(from);
        if (body == null) return;
        String raw = body.entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw != null) body.entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, raw.replace("|" + from + "|", "|" + to + "|"));
        bodies.put(to, body);
    }

    private Villager spawn(AccountId owner, IslandGuests.Guest guest, Location at) {
        IslandGuests.Visitor visitor = IslandGuests.VISITORS.get(guest.who());
        at.setYaw(ThreadLocalRandom.current().nextFloat() * 360F);
        Villager villager = at.getWorld().spawn(at, Villager.class, body -> {
            body.getPersistentDataContainer().set(key, PersistentDataType.STRING, owner.value() + "|" + guest.id() + "|" + guest.who());
            body.setAI(false);
            body.setAware(false);
            body.setInvulnerable(true);
            body.setSilent(true);
            body.setCollidable(false);
            body.setPersistent(false);
            body.setRemoveWhenFarAway(false);
            body.setCanPickupItems(false);
            body.setAdult();
            body.setAgeLock(true);
            var profession = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(visitor.profession()));
            if (profession != null) { body.setProfession(profession); body.setVillagerLevel(Math.min(5, 1 + visitor.rarity().ordinal())); }
            var type = Registry.VILLAGER_TYPE.get(NamespacedKey.minecraft(visitor.biome()));
            if (type != null) body.setVillagerType(type);
            body.customName(Chat.render(visitor.rarity().paint(Chat.escape(visitor.name())) + " <dark_gray>·</dark_gray> " + visitor.rarity().tag()));
            body.setCustomNameVisible(true);
        });
        at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 1, 0), 12, 0.4, 0.6, 0.4, 0);
        if (visitor.rarity().ordinal() >= IslandGuests.Rarity.LEGENDARY.ordinal())
            at.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 24, 0.3, 0.8, 0.3, 0.04);
        return villager;
    }

    /** Takes a served or refused visitor's body away: a happy puff when served, a plain one when refused. */
    void depart(AccountId owner, String guestId, boolean served) {
        Map<String, Body> bodies = live.get(owner);
        Body body = bodies == null ? null : bodies.remove(guestId);
        if (body == null || !body.entity.isValid()) return;
        Location at = body.entity.getLocation().add(0, 1, 0);
        at.getWorld().spawnParticle(served ? Particle.HAPPY_VILLAGER : Particle.POOF, at, served ? 16 : 10, 0.4, 0.6, 0.4, served ? 0 : 0.02);
        at.getWorld().playSound(at, served ? Sound.ENTITY_VILLAGER_CELEBRATE : Sound.ENTITY_VILLAGER_NO, 0.7F, 1F);
        body.entity.remove();
    }

    // ------------------------------------------------------------------ wandering

    private void walk() {
        long now = System.currentTimeMillis();
        for (Map<String, Body> bodies : live.values())
            for (Body body : bodies.values()) {
                if (!body.entity.isValid()) continue;
                try { step(body, now); } catch (RuntimeException ignored) { body.target = null; }
            }
    }

    private void step(Body body, long now) {
        Location at = body.entity.getLocation();
        // The ground went (someone mined it): back to the spawn rather than float.
        if (!holds(at.getBlock())) {
            Location home = standable(body.home, body.home);
            if (home != null) body.entity.teleport(home);
            body.target = null;
            return;
        }
        Player near = nearest(at, 4.5);
        if (near != null) {
            // Someone came to talk: stop and face them.
            body.target = null;
            body.restUntil = Math.max(body.restUntil, now + 1500);
            face(body.entity, near.getEyeLocation().toVector().subtract(body.entity.getEyeLocation().toVector()));
            return;
        }
        if (body.target == null) {
            if (now < body.restUntil) return;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            body.target = standable(body.home, body.home.clone().add(random.nextInt(-WANDER, WANDER + 1), 0, random.nextInt(-WANDER, WANDER + 1)));
            if (body.target == null) { body.restUntil = now + 2000; return; }
        }
        Vector toward = body.target.toVector().subtract(at.toVector()).setY(0);
        if (toward.lengthSquared() < STEP * STEP * 2) {
            body.target = null;
            body.restUntil = now + ThreadLocalRandom.current().nextLong(3000, 9000);
            return;
        }
        Vector move = toward.normalize().multiply(STEP);
        Location next = at.clone().add(move);
        Location ground = standable(body.home, next);
        if (ground == null || Math.abs(ground.getY() - at.getY()) > 1.01) {
            body.target = null;
            body.restUntil = now + 1000;
            return;
        }
        next.setY(ground.getY());
        float yaw = (float) Math.toDegrees(Math.atan2(-move.getX(), move.getZ()));
        next.setYaw(yaw);
        next.setPitch(0);
        body.entity.teleport(next);
        body.entity.setRotation(yaw, 0);
    }

    private static void face(Villager entity, Vector look) {
        if (look.lengthSquared() < 1.0E-4) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-look.getX(), look.getZ()));
        float pitch = (float) Math.toDegrees(-Math.atan2(look.getY(), Math.sqrt(look.getX() * look.getX() + look.getZ() * look.getZ())));
        entity.setRotation(yaw, Math.clamp(pitch, -40F, 40F));
    }

    private static Player nearest(Location at, double range) {
        Player best = null;
        double bestDistance = range * range;
        for (Player player : at.getWorld().getPlayers()) {
            double distance = player.getLocation().distanceSquared(at);
            if (distance < bestDistance && !player.isDead()) { best = player; bestDistance = distance; }
        }
        return best;
    }

    /**
     * The spot a visitor could stand at near {@code wanted}: same column, at most a block up or down from it, with solid
     * ground underneath and room for a body. Null when there's none, or it's too far from {@code home}.
     */
    static Location standable(Location home, Location wanted) {
        if (Math.abs(wanted.getX() - home.getX()) > WANDER + 0.6 || Math.abs(wanted.getZ() - home.getZ()) > WANDER + 0.6) return null;
        World world = wanted.getWorld();
        int x = wanted.getBlockX(), z = wanted.getBlockZ(), y = wanted.getBlockY();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        for (int dy : new int[] {0, 1, -1, 2, -2}) {
            Block feet = world.getBlockAt(x, y + dy, z);
            if (holds(feet)) {
                Location spot = wanted.clone();
                spot.setY(y + dy);
                return spot;
            }
        }
        return null;
    }

    private static boolean holds(Block feet) {
        Block below = feet.getRelative(0, -1, 0), head = feet.getRelative(0, 1, 0);
        return below.getType().isSolid() && !below.isLiquid() && feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid();
    }

    // ------------------------------------------------------------------ talking to them

    private String[] tag(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return raw == null ? null : raw.split("\\|");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        String[] tag = tag(event.getRightClicked());
        if (tag == null) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || tag.length < 3) return;
        Player player = event.getPlayer();
        AccountId owner = new AccountId(UUID.fromString(tag[0]));
        IslandGuests.Visitor visitor = IslandGuests.VISITORS.get(Math.floorMod(Integer.parseInt(tag[2]), IslandGuests.VISITORS.size()));
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        boolean member = account != null && islands.islands().owned(owner).map(island -> island.member(account)).orElse(false);
        Chat.send(player, visitor.rarity().paint(Chat.escape(visitor.name())) + "<white>:</white> <gray>" + Chat.escape(visitor.line()) + "</gray>");
        if (!member) { Chat.info(player, "This visitor is waiting for the island's co-op."); return; }
        try { guests.offer(player, owner, tag[1]); }
        catch (IllegalArgumentException refused) { Chat.error(player, Chat.escape(refused.getMessage())); }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) { if (tag(event.getRightClicked()) != null) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) { if (tag(event.getEntity()) != null) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTransform(EntityTransformEvent event) { if (tag(event.getEntity()) != null) event.setCancelled(true); }

    /** A body that somehow outlived its run (a crash before removal) is cleared when its chunk loads. */
    @EventHandler
    public void onLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (tag(entity) == null) continue;
            boolean tracked = boards.containsValue(entity)
                || live.values().stream().anyMatch(bodies -> bodies.values().stream().anyMatch(body -> body.entity.equals(entity)));
            if (!tracked) entity.remove();
        }
    }

    @Override public void close() {
        if (sync != null) sync.cancel();
        if (walk != null) walk.cancel();
        live.values().forEach(bodies -> bodies.values().forEach(body -> body.entity.remove()));
        live.clear();
        boards.values().forEach(Entity::remove);
        boards.clear();
    }
}
