package net.nerdsmp.paper.expedition;

import net.kyori.adventure.title.Title;
import net.nerdsmp.paper.expedition.combat.Telegraph;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Box;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Campfire;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Cutscene;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Hazard;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Npc;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.SetPiece;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Shot;
import net.nerdsmp.paper.expedition.content.ScriptAction;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Everything a run shows besides fights: campfires that heal and hold the respawn point, set pieces
 * that change the map, arena hazards, cutscenes, talking characters and dialogue. Owned by one run
 * and cleaned up when it closes.
 */
final class RunStage {
    interface Host {
        World world();
        ExpeditionDefinition expedition();
        List<Player> online();
        boolean live();
        /** True when the player hit or was hit within the last few seconds. */
        boolean inCombat(Player player, long now);
        /** True while any room is running. */
        boolean fighting();
        void campfireLit(String id);
        void teleport(Player player, Location to);
        void broadcast(String markup);
        /** Runs script actions from a cutscene shot, as the active room's script would. */
        void perform(List<ScriptAction> actions);
        /** Opens this quest giver's board if it has anything for the player right now. */
        boolean openQuests(Player player, String giver);
        /** The mark this quest giver shows the player: {@code !} for a quest, {@code ?} for a hand-in, or null. */
        String questMark(Player player, String giver);
    }

    /** Players out of combat this close to a lit campfire rest. */
    static final double REST_RADIUS = 4.5;
    static NamespacedKey cutsceneKey;

    private final Plugin plugin;
    private final Host host;
    private final Map<String, Boolean> lit = new LinkedHashMap<>();
    private final Map<String, Map<Block, BlockData>> pieces = new HashMap<>();
    private final Map<String, BukkitTask> hazards = new HashMap<>();
    private final Map<Block, BlockData> temporary = new HashMap<>();
    private final Map<UUID, String> npcByEntity = new HashMap<>();
    private final Map<String, UUID> npcEntities = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> npcLines = new HashMap<>();
    private final Map<UUID, CameraState> cameras = new HashMap<>();
    private final Set<UUID> fallingRocks = new HashSet<>();
    /** Scenes already shown this run: a second attempt at a boss doesn't sit through its scene again. */
    private final Set<String> played = new HashSet<>();
    /** The blue marks over a quest-giving character, one per mark, shown only to the players they're for. */
    private final Map<String, Map<String, org.bukkit.entity.TextDisplay>> marks = new HashMap<>();
    private final Map<UUID, Map<String, String>> marksShown = new HashMap<>();
    private int restClock;

    private record CameraState(GameMode mode, Location from, UUID camera, BukkitTask task) { }

    RunStage(Plugin plugin, Host host) {
        this.plugin = plugin;
        this.host = host;
        if (cutsceneKey == null) cutsceneKey = new NamespacedKey(plugin, "expedition_cutscene_mode");
    }

    // ---------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------

    void begin() {
        var story = host.expedition().story();
        for (Campfire fire : story.campfires().values()) {
            lit.put(fire.id(), fire.lit());
            Block block = block(fire.at().x(), fire.at().y(), fire.at().z());
            if (block.getType() != Material.CAMPFIRE && block.getType() != Material.SOUL_CAMPFIRE) block.setType(Material.CAMPFIRE, false);
            if (block.getBlockData() instanceof Lightable lightable) {
                lightable.setLit(fire.lit());
                block.setBlockData(lightable, false);
            }
        }
        for (SetPiece piece : story.setpieces().values()) {
            Map<Block, BlockData> original = new LinkedHashMap<>();
            forEach(piece.box(), block -> original.put(block, block.getBlockData().clone()));
            pieces.put(piece.id(), original);
        }
        for (Npc npc : story.npcs().values()) spawnNpc(npc);
    }

    void close() {
        hazards.values().forEach(BukkitTask::cancel);
        hazards.clear();
        for (UUID member : List.copyOf(cameras.keySet())) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) endCutscene(player);
        }
        temporary.forEach((block, data) -> block.setBlockData(data, false));
        temporary.clear();
        for (UUID uuid : npcByEntity.keySet()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) entity.remove();
        }
        npcByEntity.clear();
        marks.values().forEach(displays -> displays.values().forEach(Entity::remove));
        marks.clear();
        for (UUID uuid : fallingRocks) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) entity.remove();
        }
        fallingRocks.clear();
    }

    /** Once a second: campfire rest, and expiring temporary blocks are handled by their own timers. */
    void tick(long now) {
        restClock++;
        updateMarks();
        for (Campfire fire : host.expedition().story().campfires().values()) {
            Location at = new Location(host.world(), fire.at().x() + .5, fire.at().y(), fire.at().z() + .5);
            if (!lit.getOrDefault(fire.id(), false)) {
                if (restClock % 3 == 0) host.world().spawnParticle(Particle.SMOKE, at.clone().add(0, .6, 0), 3, .15, .1, .15, .005);
                continue;
            }
            for (Player player : host.online()) {
                if (player.isDead() || player.getLocation().distanceSquared(at) > REST_RADIUS * REST_RADIUS) continue;
                if (host.fighting() || host.inCombat(player, now)) {
                    player.sendActionBar(Chat.render("<gray>You can't rest while fighting.</gray>"));
                    continue;
                }
                var max = player.getAttribute(Attribute.MAX_HEALTH);
                double cap = max == null ? 20 : max.getValue();
                if (player.getHealth() < cap) {
                    player.setHealth(Math.min(cap, player.getHealth() + 2));
                    player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 1, .3, .1, .3, 0);
                }
                if (player.getFoodLevel() < 20) player.setFoodLevel(Math.min(20, player.getFoodLevel() + 1));
                player.setSaturation(Math.min(10, player.getSaturation() + 1));
                player.setFireTicks(0);
                player.sendActionBar(Chat.render("<gold>♨</gold> <gray>Resting by the " + Chat.escape(fire.title()) + "</gray>"));
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Campfires
    // ---------------------------------------------------------------------------------------------

    /** A player right-clicked a block; lights an unlit expedition campfire. Returns true when handled. */
    boolean interact(Player player, Block block) {
        for (Campfire fire : host.expedition().story().campfires().values()) {
            if (fire.at().x() != block.getX() || fire.at().y() != block.getY() || fire.at().z() != block.getZ()) continue;
            if (lit.getOrDefault(fire.id(), false)) {
                Chat.info(player, "Rest here out of combat to heal. The party regroups at the last campfire lit.");
                return true;
            }
            if (host.fighting()) {
                Chat.notice(player, "Finish the fight before lighting a campfire.");
                return true;
            }
            light(fire.id());
            return true;
        }
        return false;
    }

    void light(String id) {
        Campfire fire = host.expedition().story().campfires().get(id);
        if (fire == null || lit.getOrDefault(id, false)) return;
        lit.put(id, true);
        Block block = block(fire.at().x(), fire.at().y(), fire.at().z());
        if (block.getBlockData() instanceof Lightable lightable) {
            lightable.setLit(true);
            block.setBlockData(lightable, false);
        }
        Location at = block.getLocation().add(.5, .5, .5);
        host.world().spawnParticle(Particle.FLAME, at, 30, .3, .3, .3, .03);
        host.world().playSound(at, Sound.ITEM_FIRECHARGE_USE, 1F, 1F);
        host.campfireLit(id);
    }

    boolean campfireLit(String id) { return lit.getOrDefault(id, false); }

    // ---------------------------------------------------------------------------------------------
    // Set pieces
    // ---------------------------------------------------------------------------------------------

    void build(String id) {
        SetPiece piece = host.expedition().story().setpieces().get(id);
        if (piece == null) return;
        Material material = Material.matchMaterial(piece.block());
        BlockData data = (material == null ? Material.STONE : material).createBlockData();
        change(piece, block -> data);
    }

    void breakPiece(String id) {
        SetPiece piece = host.expedition().story().setpieces().get(id);
        if (piece != null) change(piece, block -> Material.AIR.createBlockData());
    }

    void restore(String id) {
        SetPiece piece = host.expedition().story().setpieces().get(id);
        Map<Block, BlockData> original = pieces.get(id);
        if (piece != null && original != null) change(piece, block -> original.getOrDefault(block, Material.AIR.createBlockData()));
    }

    private void change(SetPiece piece, java.util.function.Function<Block, BlockData> to) {
        Box box = piece.box();
        Location center = new Location(host.world(), (box.minX() + box.maxX()) / 2.0 + .5, (box.minY() + box.maxY()) / 2.0, (box.minZ() + box.maxZ()) / 2.0 + .5);
        switch (piece.style()) {
            case INSTANT -> {
                forEach(box, block -> block.setBlockData(to.apply(block), false));
                host.world().spawnParticle(Particle.CLOUD, center, 30, (box.maxX() - box.minX()) / 2.0, 1, (box.maxZ() - box.minZ()) / 2.0, .02);
                host.world().playSound(center, Sound.BLOCK_STONE_PLACE, 1.2F, .8F);
            }
            case RISE -> {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    int layer = y;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!host.live() && host.world() == null) return;
                        for (int x = box.minX(); x <= box.maxX(); x++) for (int z = box.minZ(); z <= box.maxZ(); z++) {
                            Block block = block(x, layer, z);
                            block.setBlockData(to.apply(block), false);
                        }
                        host.world().playSound(center, Sound.BLOCK_DEEPSLATE_PLACE, 1F, .6F + (layer - box.minY()) * .05F);
                    }, (y - box.minY()) * 4L);
                }
            }
            case CRUMBLE -> {
                host.world().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1F, .6F);
                List<Block> blocks = new ArrayList<>();
                forEach(box, blocks::add);
                java.util.Collections.shuffle(blocks);
                int batches = 5;
                for (int batch = 0; batch < batches; batch++) {
                    int from = blocks.size() * batch / batches, until = blocks.size() * (batch + 1) / batches;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        for (Block block : blocks.subList(from, until)) block.setBlockData(to.apply(block), false);
                        host.world().spawnParticle(Particle.LARGE_SMOKE, center, 25, (box.maxX() - box.minX()) / 2.0 + .5, 1, (box.maxZ() - box.minZ()) / 2.0 + .5, .02);
                        host.world().playSound(center, Sound.BLOCK_STONE_BREAK, 1F, .6F);
                    }, batch * 4L);
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Hazards
    // ---------------------------------------------------------------------------------------------

    void hazard(String action, String id) {
        Hazard hazard = host.expedition().story().hazards().get(id);
        if (hazard == null) return;
        switch (action) {
            case "start" -> {
                if (hazards.containsKey(id)) return;
                hazards.put(id, Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (host.live()) strike(hazard);
                }, 20L, hazard.everySeconds() * 20L));
            }
            case "stop" -> {
                BukkitTask task = hazards.remove(id);
                if (task != null) task.cancel();
            }
            case "pulse" -> strike(hazard);
            default -> { }
        }
    }

    /** Stops every hazard, for wipes; the room's scripts start them again. */
    void stopHazards() {
        hazards.values().forEach(BukkitTask::cancel);
        hazards.clear();
    }

    private void strike(Hazard hazard) {
        Box area = hazard.area();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Location> spots = new ArrayList<>();
        // Aim most strikes at players inside the area so a hazard can't simply be ignored.
        List<Player> inside = host.online().stream().filter(player -> !player.isDead() && area.contains(player.getX(), player.getY(), player.getZ())).toList();
        for (int index = 0; index < hazard.count(); index++) {
            if (!inside.isEmpty() && index % 2 == 0) spots.add(inside.get(random.nextInt(inside.size())).getLocation().toBlockLocation().add(.5, 0, .5));
            else spots.add(new Location(host.world(), random.nextInt(area.minX(), area.maxX() + 1) + .5, area.minY(), random.nextInt(area.minZ(), area.maxZ() + 1) + .5));
        }
        var color = switch (hazard.type()) {
            case ROCKS -> Telegraph.SHADOW;
            case FIRE, ERUPTION -> Telegraph.FIRE;
            case GAS -> Telegraph.POISON;
        };
        double radius = hazard.type() == ExpeditionDefinition.HazardType.GAS ? 2.5 : 1.6;
        int[] tick = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!host.live()) { task.cancel(); return; }
            if (tick[0]++ < 6) {
                for (Location spot : spots) Telegraph.disc(spot, radius, color);
                return;
            }
            task.cancel();
            for (Location spot : spots) land(hazard, spot, radius);
        }, 0L, 5L);
        if (hazard.type() == ExpeditionDefinition.HazardType.ROCKS) {
            host.world().playSound(spots.getFirst(), Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1.5F, .5F);
            for (Location spot : spots) {
                FallingBlock rock = host.world().spawn(spot.clone().add(0, 9, 0), FallingBlock.class, falling -> {
                    falling.setBlockData(Material.COBBLED_DEEPSLATE.createBlockData());
                    falling.setDropItem(false);
                    falling.setCancelDrop(true);
                    falling.setHurtEntities(false);
                });
                fallingRocks.add(rock.getUniqueId());
            }
        }
    }

    private void land(Hazard hazard, Location spot, double radius) {
        World world = host.world();
        switch (hazard.type()) {
            case ROCKS -> {
                world.spawnParticle(Particle.EXPLOSION, spot, 1);
                world.playSound(spot, Sound.BLOCK_DEEPSLATE_BREAK, 1.4F, .6F);
            }
            case FIRE -> {
                world.spawnParticle(Particle.FLAME, spot.clone().add(0, .3, 0), 30, .8, .2, .8, .02);
                world.playSound(spot, Sound.ITEM_FIRECHARGE_USE, 1F, .8F);
                temporary(spot.getBlock(), Material.FIRE, 80);
            }
            case ERUPTION -> {
                world.spawnParticle(Particle.LAVA, spot, 20, .6, .2, .6, 0);
                world.spawnParticle(Particle.FLAME, spot.clone().add(0, 1, 0), 30, .3, 1.2, .3, .05);
                world.playSound(spot, Sound.BLOCK_LAVA_POP, 1.5F, .5F);
            }
            case GAS -> {
                world.spawn(spot, AreaEffectCloud.class, cloud -> {
                    cloud.setRadius((float) radius);
                    cloud.setDuration(120);
                    cloud.setRadiusPerTick(0F);
                    cloud.setWaitTime(0);
                    cloud.setColor(Telegraph.POISON);
                    cloud.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 60, 0), true);
                });
                return; // The cloud does the damage.
            }
        }
        for (Player player : host.online()) {
            if (player.isDead() || player.getLocation().distanceSquared(spot) > radius * radius + 1) continue;
            player.damage(hazard.damage());
            if (hazard.type() == ExpeditionDefinition.HazardType.ERUPTION) player.setVelocity(player.getVelocity().setY(1.1));
            if (hazard.type() == ExpeditionDefinition.HazardType.FIRE) player.setFireTicks(60);
        }
    }

    boolean ownsRock(Entity entity) { return fallingRocks.remove(entity.getUniqueId()); }

    /** Places a block that goes back to what it was after {@code ticks}. */
    void temporary(Block block, Material type, int ticks) {
        if (!block.getType().isAir() && block.getType() != Material.FIRE && block.getType() != Material.COBWEB) return;
        temporary.putIfAbsent(block, block.getBlockData().clone());
        block.setType(type, false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            BlockData original = temporary.remove(block);
            if (original != null) block.setBlockData(original, false);
        }, ticks);
    }

    // ---------------------------------------------------------------------------------------------
    // Characters and dialogue
    // ---------------------------------------------------------------------------------------------

    private void spawnNpc(Npc npc) {
        EntityType type;
        try {
            type = EntityType.valueOf(npc.entity().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            type = EntityType.VILLAGER;
        }
        Location at = new Location(host.world(), npc.at().x() + .5, npc.at().y(), npc.at().z() + .5, npc.at().yaw(), 0);
        Entity entity = host.world().spawnEntity(at, type, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, spawned -> {
            spawned.customName(Chat.render("<gold>" + Chat.escape(npc.name()) + "</gold>"));
            spawned.setCustomNameVisible(true);
            spawned.setInvulnerable(true);
            spawned.setSilent(true);
            spawned.setPersistent(true);
            if (spawned instanceof Mob mob) {
                mob.setAI(false);
                mob.setRemoveWhenFarAway(false);
            }
        });
        npcByEntity.put(entity.getUniqueId(), npc.id());
        npcEntities.put(npc.id(), entity.getUniqueId());
        if (npc.quests() != null) marks.put(npc.id(), Map.of("!", mark(entity, "!"), "?", mark(entity, "?")));
    }

    private org.bukkit.entity.TextDisplay mark(Entity body, String text) {
        return host.world().spawn(markSpot(body), org.bukkit.entity.TextDisplay.class, display -> {
            display.text(Chat.render("<blue><bold>" + text + "</bold></blue>"));
            display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            display.setVisibleByDefault(false);
            display.setPersistent(false);
            display.setShadowed(true);
            display.setDefaultBackground(false);
            display.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            display.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                new org.joml.Vector3f(2.4F, 2.4F, 2.4F), new org.joml.AxisAngle4f()));
        });
    }

    private static Location markSpot(Entity body) { return body.getLocation().add(0, body.getHeight() + 1.25, 0); }

    /** Each player sees the mark that's true for them: a daily to pick up, one to hand in, or nothing. */
    private void updateMarks() {
        for (var entry : marks.entrySet()) {
            Npc npc = host.expedition().story().npcs().get(entry.getKey());
            for (Player player : host.online()) {
                Map<String, String> shown = marksShown.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
                String mark = host.questMark(player, npc.quests()), before = shown.get(npc.id());
                if (java.util.Objects.equals(mark, before)) continue;
                if (before != null) player.hideEntity(plugin, entry.getValue().get(before));
                if (mark != null) player.showEntity(plugin, entry.getValue().get(mark));
                shown.put(npc.id(), mark);
            }
        }
    }

    /** A character walks on to somewhere new, between scenes. */
    void moveNpc(String id, ExpeditionDefinition.Point to) {
        LivingEntity body = npcEntity(id);
        if (body == null) return;
        host.world().spawnParticle(Particle.SMOKE, body.getLocation().add(0, 1, 0), 12, .3, .5, .3, .01);
        body.teleport(new Location(host.world(), to.x() + .5, to.y(), to.z() + .5, to.yaw(), 0));
        var displays = marks.get(id);
        if (displays != null) displays.values().forEach(display -> display.teleport(markSpot(body)));
    }

    boolean isNpc(Entity entity) { return entity != null && npcByEntity.containsKey(entity.getUniqueId()); }

    /** Right-clicking a character plays their next line to that player. */
    void talk(Player player, Entity entity) {
        String id = npcByEntity.get(entity.getUniqueId());
        Npc npc = id == null ? null : host.expedition().story().npcs().get(id);
        if (npc == null) return;
        if (npc.quests() != null && host.openQuests(player, npc.quests())) return;
        if (npc.lines().isEmpty()) return;
        Map<String, Integer> seen = npcLines.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        int line = seen.getOrDefault(id, 0);
        Chat.send(player, speech(npc.name(), npc.lines().get(line % npc.lines().size())));
        player.playSound(entity.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, .8F, 1F);
        seen.put(id, line + 1);
    }

    /** A scripted line from an NPC (by id) or any named speaker. */
    void say(String speakerAndLine) {
        String[] parts = speakerAndLine.split("\\|", 2);
        String speaker = parts[0].strip();
        String line = parts.length > 1 ? parts[1].strip() : "";
        Npc npc = host.expedition().story().npcs().get(speaker);
        String name = npc != null ? npc.name() : speaker;
        for (Player player : host.online()) {
            Chat.send(player, speech(name, line));
            player.playSound(player.getLocation(), npc != null ? Sound.ENTITY_VILLAGER_AMBIENT : Sound.ENTITY_EVOKER_AMBIENT, .7F, npc != null ? 1F : .7F);
        }
    }

    private static String speech(String name, String line) {
        return "<gold>" + Chat.escape(name) + "</gold><dark_gray>:</dark_gray> <white>" + line + "</white>";
    }

    // ---------------------------------------------------------------------------------------------
    // Cutscenes
    // ---------------------------------------------------------------------------------------------

    boolean inCutscene(Player player) { return cameras.containsKey(player.getUniqueId()); }

    boolean cutsceneRunning() { return !cameras.isEmpty(); }

    void cutscene(String id) {
        if (!played.add(id)) return;
        // A room can start mid-teleport (an ender pearl, a staff tp), before the player has arrived;
        // start next tick so the scene returns everyone to where they really are.
        Bukkit.getScheduler().runTask(plugin, () -> { if (host.live()) play(id); });
    }

    private void play(String id) {
        Cutscene cutscene = host.expedition().story().cutscenes().get(id);
        if (cutscene == null || cutscene.shots().isEmpty()) return;
        // Once for the party: each shot's actions as it starts, and its lines spread across it.
        int start = 0;
        for (Shot shot : cutscene.shots()) {
            int at = start, length = shot.seconds() * 20;
            if (!shot.actions().isEmpty()) Bukkit.getScheduler().runTaskLater(plugin, () -> { if (host.live()) host.perform(shot.actions()); }, at);
            for (int line = 0; line < shot.lines().size(); line++) {
                String spoken = shot.lines().get(line);
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (host.live()) narrate(spoken); },
                    at + 10L + (long) line * Math.max(20, length - 10) / shot.lines().size());
            }
            start += length;
        }
        for (Player player : host.online()) {
            if (player.isDead() || inCutscene(player)) continue;
            Shot first = cutscene.shots().getFirst();
            ArmorStand camera = host.world().spawn(location(first.frame()), ArmorStand.class, stand -> {
                stand.setInvisible(true);
                stand.setMarker(true);
                stand.setGravity(false);
                stand.setInvulnerable(true);
                stand.setPersistent(false);
                stand.setSilent(true);
            });
            GameMode mode = player.getGameMode();
            Location from = player.getLocation();
            // If the player logs off mid-scene, their game mode comes back on the next join.
            player.getPersistentDataContainer().set(cutsceneKey, PersistentDataType.STRING, mode.name());
            player.setGameMode(GameMode.SPECTATOR);
            player.setSpectatorTarget(camera);
            int total = cutscene.seconds() * 20;
            int[] tick = {0};
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!player.isOnline() || !camera.isValid() || tick[0] >= total || tick[0] > 20 && player.isSneaking()) {
                    endCutscene(player);
                    return;
                }
                if (tick[0] == 30 && total > 100) player.sendActionBar(Chat.render("<dark_gray>Sneak to skip</dark_gray>"));
                int elapsed = 0;
                for (int shot = 0; shot < cutscene.shots().size(); shot++) {
                    Shot current = cutscene.shots().get(shot);
                    int length = current.seconds() * 20;
                    if (tick[0] < elapsed + length) {
                        if (tick[0] == elapsed) beginShot(player, current, shot == 0);
                        Shot next = shot + 1 < cutscene.shots().size() ? cutscene.shots().get(shot + 1) : current;
                        if (next.fade()) next = current; // A cut through black: hold this frame, then jump.
                        double t = (tick[0] - elapsed) / (double) length;
                        camera.teleport(lerp(current.frame(), current.to() != null ? current.to() : next.frame(), t));
                        break;
                    }
                    elapsed += length;
                }
                if (player.getSpectatorTarget() != camera) player.setSpectatorTarget(camera);
                tick[0]++;
            }, 0L, 1L);
            cameras.put(player.getUniqueId(), new CameraState(mode, from, camera.getUniqueId(), task));
        }
    }

    /** A shot begins for one viewer: its cards, its sound, and a fade through black when it cuts in. */
    private void beginShot(Player player, Shot shot, boolean first) {
        if (shot.text() != null || shot.title() != null)
            player.showTitle(Title.title(shot.title() == null ? net.kyori.adventure.text.Component.empty() : Chat.render(shot.title()),
                shot.text() == null ? net.kyori.adventure.text.Component.empty() : Chat.render(shot.text()),
                Title.Times.times(Duration.ofMillis(first || shot.fade() ? 700 : 250), Duration.ofSeconds(Math.max(1, shot.seconds() - 1)), Duration.ofMillis(400))));
        if (first || shot.fade()) player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 22, 0, false, false, false));
        if (shot.sound() != null) {
            String[] parts = shot.sound().split(" ");
            float pitch = parts.length > 1 ? Float.parseFloat(parts[1]) : 1F;
            player.playSound(player.getLocation(), parts[0], 1F, pitch);
        }
    }

    /** A cutscene line: a named speaker (an NPC id or any name), or plain narration when the name is empty. */
    private void narrate(String spoken) {
        String[] parts = spoken.split("\\|", 2);
        String speaker = parts.length > 1 ? parts[0].strip() : "";
        String line = parts.length > 1 ? parts[1].strip() : spoken.strip();
        Npc npc = host.expedition().story().npcs().get(speaker);
        String name = npc != null ? npc.name() : speaker;
        for (Player player : host.online())
            Chat.send(player, name.isEmpty() ? "<gray><italic>" + line + "</italic></gray>" : speech(name, line));
    }

    void endCutscene(Player player) {
        CameraState state = cameras.remove(player.getUniqueId());
        if (state == null) return;
        state.task().cancel();
        Entity camera = Bukkit.getEntity(state.camera());
        player.setSpectatorTarget(null);
        if (camera != null) camera.remove();
        host.teleport(player, state.from());
        player.setGameMode(state.mode());
        player.getPersistentDataContainer().remove(cutsceneKey);
    }

    /** On join: a player who left mid-cutscene gets their game mode back. */
    static void restoreAfterCutscene(Player player, Plugin plugin) {
        if (cutsceneKey == null) cutsceneKey = new NamespacedKey(plugin, "expedition_cutscene_mode");
        String mode = player.getPersistentDataContainer().get(cutsceneKey, PersistentDataType.STRING);
        if (mode == null) return;
        player.getPersistentDataContainer().remove(cutsceneKey);
        try {
            player.setGameMode(GameMode.valueOf(mode));
        } catch (IllegalArgumentException ignored) {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    private Location location(ExpeditionDefinition.Frame frame) {
        return new Location(host.world(), frame.x(), frame.y(), frame.z(), frame.yaw(), frame.pitch());
    }

    private Location lerp(ExpeditionDefinition.Frame a, ExpeditionDefinition.Frame b, double t) {
        double eased = t * t * (3 - 2 * t);
        float yaw = (float) (a.yaw() + wrap(b.yaw() - a.yaw()) * eased);
        return new Location(host.world(), a.x() + (b.x() - a.x()) * eased, a.y() + (b.y() - a.y()) * eased, a.z() + (b.z() - a.z()) * eased,
            yaw, (float) (a.pitch() + (b.pitch() - a.pitch()) * eased));
    }

    private static double wrap(double degrees) {
        double wrapped = degrees % 360;
        if (wrapped > 180) wrapped -= 360;
        if (wrapped < -180) wrapped += 360;
        return wrapped;
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private Block block(int x, int y, int z) { return host.world().getBlockAt(x, y, z); }

    private void forEach(Box box, java.util.function.Consumer<Block> action) {
        for (int x = box.minX(); x <= box.maxX(); x++) for (int y = box.minY(); y <= box.maxY(); y++)
            for (int z = box.minZ(); z <= box.maxZ(); z++) action.accept(block(x, y, z));
    }

    LivingEntity npcEntity(String id) {
        UUID uuid = npcEntities.get(id);
        return uuid != null && Bukkit.getEntity(uuid) instanceof LivingEntity living ? living : null;
    }
}
