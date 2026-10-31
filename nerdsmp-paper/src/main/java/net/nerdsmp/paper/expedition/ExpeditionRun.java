package net.nerdsmp.paper.expedition;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.expedition.combat.AbilityCaster;
import net.nerdsmp.paper.expedition.content.Abilities;
import net.nerdsmp.paper.expedition.content.Allies;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Point;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Room;
import net.nerdsmp.paper.expedition.content.Keystone;
import net.nerdsmp.paper.expedition.content.Modifier;
import net.nerdsmp.paper.expedition.content.ScriptAction;
import net.nerdsmp.paper.expedition.content.Tier;
import net.nerdsmp.paper.expedition.flow.RaidLockouts;
import net.nerdsmp.paper.expedition.flow.RunProgress;
import net.nerdsmp.paper.expedition.flow.RunProgress.Effect;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** The live side of one run: its world, mobs, gates, bars, scripts and rewards. Rules live in {@link RunProgress}. */
final class ExpeditionRun {
    enum State { PREPARING, ACTIVE, ENDING, CLOSED }

    record BlockPos(int x, int y, int z) { }

    /** A Mythic keystone run: whose key it is, the key, this week's affixes and the key's timer. */
    record KeyRun(UUID holder, String account, Keystone.Key key, List<Modifier> affixes, long timerSeconds) { }

    final UUID id = UUID.randomUUID();
    private final ExpeditionService service;
    private final NerdSmpPlugin plugin;
    final ExpeditionDefinition expedition;
    final Tier tier;
    final Set<Modifier> modifiers;
    final List<UUID> members;
    final boolean test;
    private final BuildCanvas.Plan plan;
    State state = State.PREPARING;
    World world;
    RunProgress progress;
    private final Map<String, Set<UUID>> mobs = new HashMap<>();
    private final Map<UUID, Double> damage = new HashMap<>();
    private final Map<String, Map<BlockPos, BlockData>> gates = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingLoot = new HashMap<>();
    private final Map<UUID, Long> away = new HashMap<>();
    private final Map<String, Integer> nextPoint = new HashMap<>();
    /** Living bosses of the active room, by their index in the encounter's boss list. */
    private final Map<UUID, Integer> bosses = new LinkedHashMap<>();
    /** The run's one boss bar: the active boss (or council), else a living mini-boss. */
    private BossBar bossBar;
    private UUID barMiniboss;
    private final Map<UUID, Caster> casters = new HashMap<>();
    private final Map<UUID, Integer> forcesOf = new HashMap<>();
    private final Map<UUID, Set<UUID>> shields = new HashMap<>();
    private final Map<UUID, Long> combat = new HashMap<>();
    private final RunPuzzles puzzles;
    private final RunStage stage;
    private final AbilityCaster abilityCaster;
    private final RunCombat fight;
    private final RunPacks packs;
    /** Whether each sealed room's door is shut now. */
    private final Map<String, Boolean> seals = new HashMap<>();
    /** When the active sealed fight last had a living member inside. */
    private long lastInside;
    private boolean enraged;
    private long closeAt = -1;
    private boolean completed;
    private final List<AllySlot> allies = new ArrayList<>();
    private int healerClock;
    /** The keystone this run is on, or null for a regular run. */
    final KeyRun key;
    /** True once the key's result (upgrade or deplete) has been written. */
    boolean keySettled;
    private final Set<UUID> raging = new HashSet<>();
    /** Each encounter mob's role, for its drops. */
    private final Map<UUID, ExpeditionDefinition.Role> roles = new HashMap<>();
    /** Mob groups held under this prefix belong to a pack rather than a room. */
    private static final String PACK = "pack:";
    /** How long a sealed fight runs with nobody alive inside before it counts as a wipe. */
    private static final long EVACUATED_MILLIS = 3_000;
    final RunLoot loot;
    /** While the room's boss is submerged: when it surfaces again (-1 when it isn't). */
    private long surfaceAt = -1;
    private long submergedAt;

    /** An encounter mob with abilities: its role, what it can cast, and when each is ready again. */
    private static final class Caster {
        final ExpeditionDefinition.Role role;
        List<String> abilities;
        final double power;
        final Map<String, Long> ready = new HashMap<>();
        long nextCast;

        Caster(ExpeditionDefinition.Role role, List<String> abilities, double power) {
            this.role = role;
            this.abilities = abilities;
            this.power = power;
        }
    }

    /** One hired ally: its role, its current body (null while it waits to rejoin) and when it rejoins. */
    private static final class AllySlot {
        final Allies.Role role;
        UUID entity;
        long respawnAt = -1;

        AllySlot(Allies.Role role) { this.role = role; }
    }

    ExpeditionRun(ExpeditionService service, NerdSmpPlugin plugin, ExpeditionDefinition expedition, Tier tier,
                  Set<Modifier> modifiers, List<UUID> members, boolean test, BuildCanvas.Plan plan, List<Allies.Role> hired, KeyRun key) {
        this.service = service;
        this.key = key;
        double luck = net.nerdsmp.paper.expedition.content.ExpeditionLoot.luck(tier.id(), key == null ? 0 : key.key().level())
            * (expedition.story().raid() ? 1.1 : 1);
        this.loot = new RunLoot(plugin, this, service.lootTable(expedition.id()), luck);
        this.plugin = plugin;
        this.expedition = expedition;
        this.tier = tier;
        this.modifiers = Set.copyOf(modifiers);
        this.members = List.copyOf(members);
        this.test = test;
        this.plan = plan;
        for (Allies.Role role : hired) allies.add(new AllySlot(role));
        this.abilityCaster = new AbilityCaster(plugin);
        ExpeditionRun run = this;
        this.puzzles = new RunPuzzles(plugin, new RunPuzzles.Host() {
            @Override public World world() { return run.world; }
            @Override public Room activeRoom() { return run.progress == null ? null : run.progress.activeRoom(); }
            @Override public List<Player> online() { return run.online(); }
            @Override public void solved(String room, String puzzle) { run.apply(run.progress.solve(room, puzzle, System.currentTimeMillis()), System.currentTimeMillis()); }
            @Override public void teleport(Player player, Location to) { run.service.teleport(player, to); }
        });
        this.stage = new RunStage(plugin, new RunStage.Host() {
            @Override public World world() { return run.world; }
            @Override public ExpeditionDefinition expedition() { return run.expedition; }
            @Override public List<Player> online() { return run.online(); }
            @Override public boolean live() { return run.state == State.ACTIVE; }
            @Override public boolean inCombat(Player player, long now) { return now - run.combat.getOrDefault(player.getUniqueId(), 0L) < 6_000; }
            @Override public boolean fighting() {
                Room room = run.progress == null ? null : run.progress.activeRoom();
                return room != null && room.encounter().type() != ExpeditionDefinition.EncounterType.NONE
                    && room.encounter().type() != ExpeditionDefinition.EncounterType.PUZZLE;
            }
            @Override public void campfireLit(String id) { run.apply(run.progress.campfire(id), System.currentTimeMillis()); }
            @Override public void teleport(Player player, Location to) { run.service.teleport(player, to); }
            @Override public void broadcast(String markup) { run.broadcast(markup); }
            @Override public void perform(List<ScriptAction> actions) {
                Room active = run.progress == null ? null : run.progress.activeRoom();
                run.runScript(active != null ? active.id() : run.expedition.roomList().getFirst().id(), actions, 0);
            }
        });
        this.fight = new RunCombat(plugin, new RunCombat.Host() {
            @Override public boolean live() { return run.state == State.ACTIVE; }
            @Override public List<Player> online() { return run.online(); }
            @Override public boolean inCutscene(Player player) { return run.stage.inCutscene(player); }
            @Override public Set<UUID> engaged() { return run.engaged(); }
            @Override public boolean ally(Entity entity) { return run.isAlly(entity); }
            @Override public void logAbility(String line) { run.logAbility(line); }
            @Override public AbilityCaster.Arena arena() { return run.arena; }
            @Override public double allyThreat(Entity ally) { return run.allyThreat(ally); }
        });
        this.packs = new RunPacks(plugin, new RunPacks.Host() {
            @Override public World world() { return run.world; }
            @Override public ExpeditionDefinition expedition() { return run.expedition; }
            @Override public List<Player> online() { return run.online(); }
            @Override public boolean inCutscene(Player player) { return run.stage.inCutscene(player); }
            @Override public int scaled(int count) { return run.service.scaling().scaledCount(count, run.effectiveSize(), run.expedition.maxParty()); }
            @Override public java.util.function.BiFunction<ExpeditionDefinition.Pack, String, java.util.function.Function<Location, LivingEntity>> spawner() {
                return (pack, mob) -> at -> run.spawnPacked(pack, mob, at);
            }
            @Override public RunCombat combat() { return run.fight; }
            @Override public void cleared(String pack) { run.apply(run.progress.packCleared(pack), System.currentTimeMillis()); }
            @Override public void logAbility(String line) { run.logAbility(line); }
        });
    }

    RunCombat fight() { return fight; }

    /** Whether this enemy is in a pack that's waiting or walking home. */
    boolean dormant(LivingEntity enemy) { return packs.dormant(enemy.getUniqueId()); }

    /** A player or ally hit an enemy: threat, interrupts, and waking its pack. */
    void enemyHit(Entity source, LivingEntity enemy, double amount, boolean charged) {
        if (source instanceof LivingEntity attacker) packs.struck(enemy, attacker);
        fight.hit(source, enemy, amount, charged);
    }

    /** Taunts everything close by, waking any pack in reach. */
    void taunt(Player player) {
        if (state != State.ACTIVE || stage.inCutscene(player)) return;
        List<LivingEntity> enemies = new ArrayList<>();
        for (UUID uuid : engaged()) if (Bukkit.getEntity(uuid) instanceof LivingEntity living) enemies.add(living);
        for (LivingEntity member : packs.near(player.getLocation(), 10)) if (!enemies.contains(member)) enemies.add(member);
        if (!fight.taunt(player, enemies)) return;
        for (LivingEntity enemy : enemies)
            if (enemy.getLocation().distanceSquared(player.getLocation()) <= 100) packs.struck(enemy, player);
    }

    /** Every enemy that's in a fight: room mobs and bosses, and any pack that's been pulled. */
    private Set<UUID> engaged() {
        Set<UUID> found = new HashSet<>(bosses.keySet());
        for (var entry : mobs.entrySet()) if (!entry.getKey().startsWith(PACK)) found.addAll(entry.getValue());
        found.addAll(packs.fighting());
        found.removeIf(uuid -> !damage.containsKey(uuid));
        return found;
    }

    private double allyThreat(Entity entity) {
        for (AllySlot slot : allies)
            if (entity.getUniqueId().equals(slot.entity)) return slot.role == Allies.Role.BULWARK ? 3 : 1;
        return 1;
    }

    private void logAbility(String line) {
        if (plugin.getConfig().getBoolean("expeditions.log-abilities", false)) plugin.getLogger().info("[" + expedition.id() + "] " + line);
    }

    /** A pack's member, tracked under the pack so rooms don't wait on it. */
    private LivingEntity spawnPacked(ExpeditionDefinition.Pack pack, String mob, Location at) {
        Room group = new Room(PACK + pack.id(), pack.id(), null, null, null, List.of(), ExpeditionDefinition.Encounter.NONE);
        return spawnAt(group, mob, at, false);
    }

    /** Doors follow the fight: barred until a room's packs are dead, and shut while its fight runs. */
    private void updateSeals(long now) {
        for (Room room : expedition.roomList()) {
            if (room.seal() == null) continue;
            boolean closed = progress.sealed(room.id());
            if (Boolean.valueOf(closed).equals(seals.put(room.id(), closed))) continue;
            var box = room.seal();
            BlockData bars = Material.IRON_BARS.createBlockData(box.minZ() == box.maxZ() ? "[east=true,west=true]" : "[north=true,south=true]");
            for (int x = box.minX(); x <= box.maxX(); x++) for (int y = box.minY(); y <= box.maxY(); y++)
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (closed && block.getType().isAir()) block.setBlockData(bars, false);
                    else if (!closed && block.getType() == Material.IRON_BARS) block.setType(Material.AIR, false);
                }
            if (world.getPlayerCount() > 0) world.playSound(new Location(world, box.minX(), box.minY(), box.minZ()), Sound.BLOCK_IRON_DOOR_CLOSE, 1F, .7F);
        }
        Room active = progress.activeRoom();
        if (active == null || active.seal() == null || active.region() == null || !progress.sealed(active.id())) {
            lastInside = now;
            return;
        }
        for (UUID member : progress.present()) {
            Player player = Bukkit.getPlayer(member);
            if (player == null || player.isDead() || player.getWorld() != world) continue;
            Location at = player.getLocation();
            if (active.region().contains(at.getX(), at.getY(), at.getZ())) {
                lastInside = now;
                return;
            }
        }
        if (now - lastInside >= EVACUATED_MILLIS) apply(progress.evacuated(), now);
    }

    BuildCanvas.Plan plan() { return plan; }
    boolean completed() { return completed; }

    /** The key's clock: time spent plus the death penalties. */
    long keyClock(long now) {
        long seconds = progress.phase() == RunProgress.Phase.COMPLETE ? progress.finishedSeconds() : progress.elapsedSeconds(now);
        return seconds + (long) progress.deaths() * Keystone.DEATH_PENALTY_SECONDS;
    }

    /** The key's timer, plus any time a Timed-goal expedition added for cleared rooms. */
    long keyTimer() {
        return key.timerSeconds() + Math.max(0, progress.limitSeconds() - expedition.timeLimitMinutes() * 60L);
    }

    /** A run with hired allies pays loot but never counts toward the campaign or the records. */
    boolean assisted() { return !allies.isEmpty(); }

    // ---------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------

    void begin(World created) {
        world = created;
        long now = System.currentTimeMillis();
        progress = new RunProgress(expedition, tier, modifiers, new HashSet<>(members), now);
        for (Room room : expedition.roomList()) {
            if (room.gate() == null) continue;
            Map<BlockPos, BlockData> blocks = new LinkedHashMap<>();
            var gate = room.gate();
            for (int x = gate.minX(); x <= gate.maxX(); x++) for (int y = gate.minY(); y <= gate.maxY(); y++)
                for (int z = gate.minZ(); z <= gate.maxZ(); z++) {
                    var block = world.getBlockAt(x, y, z);
                    if (!block.getType().isAir()) blocks.put(new BlockPos(x, y, z), block.getBlockData().clone());
                }
            gates.put(room.id(), blocks);
        }
        ticketChunks();
        state = State.ACTIVE;
        fight.start();
        packs.begin();
        updateSeals(now);
        if (expedition.story().time() != null && world.getEnvironment() == World.Environment.NORMAL) world.setTime(expedition.story().time());
        stage.begin();
        puzzles.begin(expedition.roomList());
        Location spawn = location(expedition.spawn());
        for (UUID member : members) {
            Player player = Bukkit.getPlayer(member);
            if (player == null) {
                away.put(member, now);
                progress.left(member);
                continue;
            }
            service.returns().remember(member, player.getLocation());
            service.teleport(player, spawn);
            player.showTitle(Title.title(Chat.render("<gold>" + Chat.escape(expedition.title()) + "</gold>"),
                Chat.render("<gray>" + Chat.escape(tier.title()) + (modifiers.isEmpty() ? "" : " · " + modifierNames()) + "</gray>"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))));
        }
        for (AllySlot slot : allies) spawnAlly(slot, spawn);
        // The opening scene, once everyone has arrived.
        if (expedition.story().intro() != null)
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (state == State.ACTIVE) stage.cutscene(expedition.story().intro()); }, 30L);
        if (assisted()) broadcast("<aqua>Hired allies:</aqua> <white>" + String.join(", ", allies.stream()
            .map(slot -> slot.role.title() + " (" + slot.role.job() + ")").toList()) + "</white>. <gray>This is an assisted run: "
            + "it pays loot and Marks, but doesn't count toward the campaign or the expedition records.</gray>");
        if (key != null) broadcast("<gold>" + Chat.escape(expedition.title()) + "</gold> <light_purple>(" + Chat.escape(tier.title())
            + " keystone)</light_purple> <white>has begun.</white> <gray>Beat <white>" + Chat.duration(Duration.ofSeconds(key.timerSeconds()))
            + "</white> to upgrade the key. There are no lives: each death adds <white>" + Keystone.DEATH_PENALTY_SECONDS
            + "s</white> to the clock. Affixes: <white>" + Chat.escape(Keystone.describe(key.affixes())) + "</white>.</gray>");
        else if (tier.runBack()) broadcast("<gold>" + Chat.escape(expedition.title()) + "</gold> <gray>(" + Chat.escape(tier.title()) + ")</gray> <white>has begun.</white> "
            + "<gray>Fall and you run back from the last campfire; what you've killed stays dead.</gray>");
        else broadcast("<gold>" + Chat.escape(expedition.title()) + "</gold> <gray>(" + Chat.escape(tier.title()) + ")</gray> <white>has begun.</white> "
            + "<gray>Your party has <white>" + progress.lives() + "</white> lives and <white>" + expedition.timeLimitMinutes()
            + "</white> minutes. Push forward into the first room.</gray>");
        for (Modifier affix : key == null ? List.<Modifier>of() : key.affixes())
            broadcast("<light_purple>" + affix.title() + ":</light_purple> <gray>" + Chat.escape(affix.description()) + "</gray>");
        if (modifiers.contains(Modifier.TIMED))
            broadcast("<yellow>Timed:</yellow> <gray>finish within " + Chat.duration(Duration.ofSeconds(Modifier.parSeconds(expedition.timeLimitMinutes())))
                + " for an extra loot roll.</gray>");
        switch (expedition.story().goal()) {
            case FORCES -> broadcast("<yellow>Goal:</yellow> <gray>slay enough enemies (enemy forces on your sidebar) to open the way to the final room.</gray>");
            case BOSSES -> broadcast("<yellow>Goal:</yellow> <gray>defeat every boss, in any order.</gray>");
            case TIMED -> broadcast("<yellow>Goal:</yellow> <gray>beat the clock. Each room you clear adds "
                + Chat.duration(Duration.ofSeconds(expedition.story().timeBonusSeconds())) + ".</gray>");
            default -> { }
        }
        updateStatus(now);
    }

    void tick(long now) {
        if (state == State.ACTIVE || state == State.ENDING) tickAllies(now);
        if (state == State.ENDING) {
            if (completed) exitBeacon();
            updateStatus(now);
            // Close at the deadline, or as soon as the last member has walked out.
            if (now >= closeAt || !service.attached(this)) service.close(this, completed ? "The expedition closes behind you." : null);
            return;
        }
        if (state != State.ACTIVE) return;
        away.entrySet().removeIf(entry -> {
            if (now - entry.getValue() < service.rejoinWindow().toMillis()) return false;
            service.detach(this, entry.getKey());
            return true;
        });
        if (progress.present().isEmpty() && away.isEmpty()) {
            apply(progress.abandon("Everyone left the expedition."), now);
            return;
        }
        if (progress.present().isEmpty()) return; // Pause the encounter while the whole party reconnects.
        Room active = progress.activeRoom();
        int alive = active == null ? 0 : alive(active.id());
        apply(progress.tick(now, alive), now);
        // A submerged boss surfaces when its time is up, or once its adds are dead.
        if (surfaceAt > 0 && (now >= surfaceAt || alive == 0 && now - submergedAt > 3_000)) surface();
        stage.tick(now);
        puzzles.tick(now);
        updateSeals(now);
        if (!bosses.isEmpty() && state == State.ACTIVE) {
            double percent = bossPercent();
            if (bossBar != null) bossBar.progress((float) Math.max(0, Math.min(1, percent / 100)));
            apply(progress.bossHealth(percent), now);
        } else updateMinibossBar();
        tickAbilities(now);
        updateStatus(now);
    }

    /** The boss bar's fill: one boss's health, or a council's average with fallen members at zero. */
    private double bossPercent() {
        Room room = progress.activeRoom();
        int total = room == null ? 1 : Math.max(1, room.encounter().bossList().size());
        double sum = 0;
        for (UUID uuid : bosses.keySet())
            if (Bukkit.getEntity(uuid) instanceof LivingEntity living && living.isValid() && !living.isDead()) sum += 100 * living.getHealth() / maxHealth(living);
        return sum / total;
    }

    private static double maxHealth(LivingEntity living) {
        var max = living.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? Math.max(1, living.getHealth()) : max.getValue();
    }

    void close() {
        // A key run that ends any way but a clear (a failure, everyone leaving, staff stopping it) depletes the key.
        if (key != null && !keySettled && !test) service.depleteKey(this);
        state = State.CLOSED;
        fight.stop();
        packs.close();
        for (AllySlot slot : allies) {
            Entity entity = slot.entity == null ? null : Bukkit.getEntity(slot.entity);
            if (entity != null) entity.remove();
            slot.entity = null;
        }
        stage.close();
        hideBar();
        for (UUID member : members) {
            Player player = Bukkit.getPlayer(member);
            if (player != null && plugin.presentation() != null) plugin.presentation().clearOverlay(player);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Events routed from the service
    // ---------------------------------------------------------------------------------------------

    void moved(Player player, Location to) {
        if (stage.inCutscene(player)) return;
        if (state == State.ACTIVE) {
            puzzles.moved(player, to);
            for (Room room : expedition.roomList())
                if (room.region() != null && room.region().contains(to.getX(), to.getY(), to.getZ()) && progress.startable(room.id())) {
                    apply(progress.enter(room.id(), System.currentTimeMillis()), System.currentTimeMillis());
                    break;
                }
        } else if (state == State.ENDING && completed && expedition.exit() != null
            && expedition.exit().distanceSquared(to.getX(), to.getY(), to.getZ()) <= 6.25) {
            service.sendHome(player, this, "<green>You leave " + Chat.escape(expedition.title()) + " with your spoils.</green>");
        }
    }

    void died(Player player) {
        if (state != State.ACTIVE) return;
        apply(progress.death(player.getUniqueId()), System.currentTimeMillis());
    }

    Location respawned(Player player) {
        if (progress != null) progress.respawned(player.getUniqueId());
        return respawnLocation();
    }

    void quit(Player player) {
        if (stage.inCutscene(player)) stage.endCutscene(player);
        if (state != State.ACTIVE) return;
        away.put(player.getUniqueId(), System.currentTimeMillis());
        apply(progress.left(player.getUniqueId()), System.currentTimeMillis());
        broadcast("<gray>" + Chat.escape(player.getName()) + " disconnected. They have "
            + Chat.duration(service.rejoinWindow()) + " to return.</gray>");
    }

    boolean rejoin(Player player) {
        if (state != State.ACTIVE || away.remove(player.getUniqueId()) == null) return false;
        progress.rejoined(player.getUniqueId());
        if (player.getWorld() != world) service.teleport(player, respawnLocation());
        if (bossBar != null) player.showBossBar(bossBar);
        broadcast("<gray>" + Chat.escape(player.getName()) + " is back.</gray>");
        return true;
    }

    /** A member left for good (leave command, teleport out, or timed out while offline). */
    void left(UUID member) {
        away.remove(member);
        if (progress != null) apply(progress.left(member), System.currentTimeMillis());
        Player player = Bukkit.getPlayer(member);
        if (player != null) {
            if (stage.inCutscene(player)) stage.endCutscene(player);
            if (bossBar != null) player.hideBossBar(bossBar);
            if (plugin.presentation() != null) plugin.presentation().clearOverlay(player);
        }
    }

    boolean tracks(LivingEntity entity) {
        return damage.containsKey(entity.getUniqueId());
    }

    double damageMultiplier(Entity entity) {
        double multiplier = damage.getOrDefault(entity.getUniqueId(), 1.0);
        // Raging: regular enemies hit 50% harder once they are low.
        if (modifiers.contains(Modifier.RAGING) && !bosses.containsKey(entity.getUniqueId()) && entity instanceof LivingEntity living
            && living.getHealth() < maxHealth(living) * .3) {
            multiplier *= 1.5;
            if (raging.add(entity.getUniqueId())) {
                world.spawnParticle(Particle.ANGRY_VILLAGER, living.getLocation().add(0, living.getHeight(), 0), 6, .4, .3, .4, 0);
                world.playSound(living.getLocation(), Sound.ENTITY_RAVAGER_ROAR, .5F, 1.6F);
            }
        }
        return multiplier;
    }

    void mobDied(LivingEntity entity) {
        UUID uuid = entity.getUniqueId();
        Integer bossSlot = bosses.remove(uuid);
        damage.remove(uuid);
        ExpeditionDefinition.Role role = roles.remove(uuid);
        if (role != null && state == State.ACTIVE) loot.enemyDied(role, entity.getLocation());
        casters.remove(uuid);
        fight.died(uuid);
        packs.died(uuid);
        mobs.values().forEach(set -> set.remove(uuid));
        shields.values().forEach(helpers -> helpers.remove(uuid));
        shields.entrySet().removeIf(entry -> {
            if (!entry.getValue().isEmpty()) return false;
            if (Bukkit.getEntity(entry.getKey()) instanceof LivingEntity shielded) {
                shielded.setInvulnerable(false);
                world.playSound(shielded.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.2F, .8F);
                broadcast("<yellow>The shield breaks!</yellow>");
            }
            return true;
        });
        if (uuid.equals(barMiniboss)) {
            hideBar();
            barMiniboss = null;
        }
        long now = System.currentTimeMillis();
        Integer worth = forcesOf.remove(uuid);
        if (worth != null) apply(progress.killed(worth), now);
        if (bossSlot != null) {
            if (bosses.isEmpty()) hideBar();
            String mobId = plugin.mobs().encounterId(entity);
            if (!assisted() && !test) for (UUID member : progress.present()) {
                Player player = Bukkit.getPlayer(member);
                if (player != null) plugin.campaign().recordBoss(player, mobId, expedition.id());
            }
            int link = progress.activeRoom() == null ? 0 : progress.activeRoom().encounter().linkSeconds();
            if (!bosses.isEmpty() && link > 0) broadcast("<gold>" + plainName(entity) + " falls!</gold> <yellow>Their bond holds: bring down the rest within "
                + link + " seconds or it rises again.</yellow>");
            else if (!bosses.isEmpty()) broadcast("<gold>" + plainName(entity) + " falls!</gold> <gray>" + bosses.size() + " remain.</gray>");
            apply(progress.bossDefeated(bossSlot, now), now);
            return;
        }
        Location at = entity.getLocation();
        if (modifiers.contains(Modifier.VOLATILE)) {
            world.spawnParticle(Particle.FLAME, at, 20, .4, .4, .4, .02);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (state != State.ACTIVE) return;
                world.spawnParticle(Particle.EXPLOSION, at, 1);
                world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, .7F, 1.4F);
                for (Player player : world.getPlayers())
                    if (player.getLocation().distanceSquared(at) <= 9 && members.contains(player.getUniqueId())) player.damage(4.0);
            }, 30L);
        }
        if (modifiers.contains(Modifier.SANGUINE)) sanguinePool(at);
        if (modifiers.contains(Modifier.BOLSTERING)) {
            for (Set<UUID> group : mobs.values()) for (UUID other : group) {
                if (!(Bukkit.getEntity(other) instanceof LivingEntity living) || bosses.containsKey(other)
                    || living.getLocation().distanceSquared(at) > 100) continue;
                var max = living.getAttribute(Attribute.MAX_HEALTH);
                if (max != null) {
                    max.setBaseValue(max.getBaseValue() * 1.15);
                    living.setHealth(Math.min(max.getValue(), living.getHealth() * 1.15));
                }
                damage.computeIfPresent(other, (key, value) -> value * 1.10);
                world.spawnParticle(Particle.ANGRY_VILLAGER, living.getLocation().add(0, 1.5, 0), 3, .3, .3, .3, 0);
            }
        }
    }

    /** Sanguine: for 8 seconds the spot an enemy fell heals other enemies and stings players. */
    private void sanguinePool(Location at) {
        int[] pulses = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (state != State.ACTIVE || pulses[0]++ >= 16) {
                task.cancel();
                return;
            }
            world.spawnParticle(Particle.CRIMSON_SPORE, at.clone().add(0, .2, 0), 30, 1.1, .05, 1.1, 0);
            if (pulses[0] % 2 != 0) return;
            for (Set<UUID> group : mobs.values()) for (UUID other : group) {
                if (bosses.containsKey(other) || !(Bukkit.getEntity(other) instanceof LivingEntity living) || !living.isValid()
                    || living.getLocation().distanceSquared(at) > 6.25) continue;
                living.setHealth(Math.min(maxHealth(living), living.getHealth() + maxHealth(living) * .08));
                world.spawnParticle(Particle.HEART, living.getLocation().add(0, living.getHeight(), 0), 1, .2, .2, .2, 0);
            }
            for (Player player : online()) if (player.getLocation().distanceSquared(at) <= 6.25 && !player.isDead()) player.damage(1.5);
        }, 10L, 10L);
    }

    // ---------------------------------------------------------------------------------------------
    // Effects
    // ---------------------------------------------------------------------------------------------

    void apply(List<Effect> effects, long now) {
        for (Effect effect : effects) {
            try {
                applyOne(effect, now);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "Expedition " + expedition.id() + " effect " + effect + " failed", failure);
            }
        }
    }

    private void applyOne(Effect effect, long now) {
        switch (effect) {
            case Effect.StartRoom start -> {
                Room room = expedition.rooms().get(start.room());
                for (Player player : online()) {
                    player.showTitle(Title.title(Component.empty(), Chat.render("<gold>" + Chat.escape(room.title()) + "</gold>"),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400))));
                    player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, .5F, 1.2F);
                }
                enraged = false;
                puzzles.started(room);
                for (var puzzle : room.puzzles().values())
                    if (puzzle.supply() != null && !RunSupplies.kit(plugin, puzzle).isEmpty())
                        broadcast("<aqua>Supplies:</aqua> <gray>right-click the supply crate for " + RunSupplies.describe(puzzle) + ".</gray>");
            }
            case Effect.SpawnWave wave -> spawnWave(wave.room(), wave.wave());
            case Effect.SpawnBoss spawn -> spawnBoss(spawn.room(), spawn.index());
            case Effect.ReviveBoss revive -> {
                spawnBoss(revive.room(), revive.index());
                broadcast("<red>Too slow!</red> <gray>A fallen foe answers its partner's call and rises again.</gray>");
            }
            case Effect.Script script -> runScript(script.room(), expedition.rooms().get(script.room()).encounter().script(script.trigger()), 0);
            case Effect.OpenGate open -> gate(open.room(), true);
            case Effect.DespawnRoom despawn -> despawn(despawn.room());
            case Effect.ResetRoom reset -> {
                surfaceAt = -1;
                despawn(reset.room());
                puzzles.reset(expedition.rooms().get(reset.room()));
                stage.stopHazards();
                regroupAllies();
                broadcast("<red>The party has fallen.</red> <gray>" + Chat.escape(expedition.rooms().get(reset.room()).title())
                    + " resets. Regroup and try again.</gray>");
            }
            case Effect.Checkpoint checkpoint -> broadcast("<aqua>Checkpoint reached.</aqua> <gray>The party will regroup here.</gray>");
            case Effect.ClearRoom clear -> {
                Room room = expedition.rooms().get(clear.room());
                surfaceAt = -1;
                if (room.encounter().type() != ExpeditionDefinition.EncounterType.NONE) {
                    broadcast("<green>✔ " + Chat.escape(room.title()) + " cleared.</green>");
                    for (Player player : online()) player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, .5F, 1.4F);
                }
                if (!test) service.roomCleared(this, room);
            }
            case Effect.Lives lives -> {
                Player died = Bukkit.getPlayer(lives.died());
                if (key != null) broadcast("<red>" + Chat.escape(died == null ? "A party member" : died.getName()) + " fell.</red> <gray>+"
                    + Keystone.DEATH_PENALTY_SECONDS + "s on the key <dark_gray>(" + progress.deaths() + " deaths)</dark_gray></gray>");
                else if (lives.remaining() < 0) broadcast("<red>" + Chat.escape(died == null ? "A party member" : died.getName()) + " fell.</red>");
                else broadcast("<red>" + Chat.escape(died == null ? "A party member" : died.getName()) + " fell.</red> <gray>Lives left: <white>"
                    + Math.max(0, lives.remaining()) + "</white></gray>");
            }
            case Effect.Complete complete -> complete(complete.seconds(), complete.underPar(), now);
            case Effect.PuzzleSolved solved -> {
                var puzzle = expedition.rooms().get(solved.room()).puzzles().get(solved.puzzle());
                broadcast("<green>✔ " + Chat.escape(puzzle.title()) + "</green>");
                for (Player player : online()) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1F, 1.6F);
            }
            case Effect.BossPhase bossPhase -> enterPhase(bossPhase.room(), bossPhase.phase());
            case Effect.Enrage enrage -> {
                enraged = true;
                for (UUID uuid : bosses.keySet()) {
                    damage.computeIfPresent(uuid, (key, value) -> value * 1.5);
                    if (Bukkit.getEntity(uuid) instanceof LivingEntity living) {
                        living.addPotionEffect(new PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 20 * 600, 0));
                        world.spawnParticle(Particle.ANGRY_VILLAGER, living.getLocation().add(0, living.getHeight(), 0), 10, .5, .4, .5, 0);
                    }
                }
                broadcast("<dark_red><bold>Enraged!</bold></dark_red> <gray>The fight dragged on: the boss hits harder and faster. Finish it now.</gray>");
                for (Player player : online()) player.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1F, .6F);
            }
            case Effect.ForcesComplete forces -> {
                Room next = progress.nextRoom();
                broadcast("<gold>Enemy forces complete!</gold> <gray>" + (next == null ? "The way ahead is open." : "The way to "
                    + Chat.escape(next.title()) + " is open.") + "</gray>");
                for (Player player : online()) player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, .6F, 1.2F);
            }
            case Effect.TimeBonus bonus -> broadcast("<aqua>+" + Chat.duration(Duration.ofSeconds(bonus.seconds())) + "</aqua> <gray>on the clock.</gray>");
            case Effect.RoomUnlocked unlocked -> broadcast("<gold>The way is clear.</gold> <gray>"
                + Chat.escape(expedition.rooms().get(unlocked.room()).title()) + " stands open.</gray>");
            case Effect.Campfire campfire -> {
                var fire = expedition.story().campfires().get(campfire.campfire());
                broadcast("<gold>♨ " + Chat.escape(fire == null ? "Campfire" : fire.title()) + " lit.</gold> <gray>Rest by it out of combat to heal. "
                    + "The party will regroup here.</gray>");
            }
            case Effect.Fail fail -> {
                state = State.ENDING;
                closeAt = now + 10_000;
                broadcast("<red><bold>Expedition failed.</bold></red> <gray>" + Chat.escape(fail.reason()) + " You'll be returned in 10 seconds.</gray>");
                if (key != null && !keySettled && !test) service.depleteKey(this);
                for (Player player : online()) player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, .4F, .8F);
            }
        }
    }

    private void complete(long seconds, boolean underPar, long now) {
        completed = true;
        state = State.ENDING;
        closeAt = now + service.lingerAfterFinish().toMillis();
        int rolls = tier.lootRolls() + (underPar ? 1 : 0);
        Map<String, String> party = new LinkedHashMap<>();
        Map<UUID, List<ItemStack>> fixed = new LinkedHashMap<>();
        Set<UUID> lockedOut = new HashSet<>();
        for (UUID member : progress.present()) {
            Player player = Bukkit.getPlayer(member);
            Optional<AccountId> account = plugin.sessions().account(member);
            if (player == null || account.isEmpty()) continue;
            party.put(account.get().toString(), player.getName());
            if (!test && !assisted()) plugin.campaign().recordExpedition(player, expedition.id(), key != null ? "mythic" : tier.id());
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1F, 1F);
            // A raid's finish pays once per tier per week, like its bosses.
            if (expedition.story().raid() && !test
                && !service.lockouts().claim(account.get().toString(), expedition.id(), tier.id(), RaidLockouts.FINISH, service.raidWeek())) {
                Chat.notice(player, "You've already claimed this raid's finishing reward on " + Chat.escape(tier.title()) + " this week. It resets on Monday.");
                lockedOut.add(member);
                continue;
            }
            long marks = payMarks(account.get(), expedition.loot(), "complete");
            fixed.put(member, fixedRewards(expedition.loot(), rolls));
            if (marks > 0) Chat.success(player, "Reward: " + Chat.marks(marks) + (underPar ? " <yellow>(+1 timed roll in your chest)</yellow>" : "") + ".");
        }
        int findable = expedition.story().secrets() + progress.optionalTotal();
        var score = net.nerdsmp.paper.expedition.content.ExpeditionLoot.Score.of(seconds, progress.limitSeconds(), progress.deaths(),
            Math.max(1, members.size()), loot.secretsFound() + progress.optionalCleared(), findable);
        var grade = score.grade();
        broadcast("<gold>Score:</gold> <white>" + score.total() + "</white> <dark_gray>·</dark_gray> " + gradeColor(grade) + "<bold>" + grade.title()
            + "</bold>" + "</" + gradeColor(grade).substring(1) + " <gray>(Speed " + score.speed() + " · Survival " + score.survival()
            + " · Exploration " + score.exploration() + ")</gray>");
        for (Player player : online()) player.showTitle(Title.title(Chat.render(gradeColor(grade) + "<bold>" + grade.title() + "</bold>"),
            Chat.render("<gray>" + Chat.escape(expedition.title()) + " · " + Chat.duration(Duration.ofSeconds(seconds)) + "</gray>")));
        loot.finish(fixed, grade, lockedOut, net.nerdsmp.paper.expedition.content.ExpeditionLoot.bonusPrice(expedition.level(), tier.marks()));
        // The closing narration, a line every few seconds.
        List<String> outro = expedition.story().outro();
        for (int line = 0; line < outro.size(); line++) {
            String spoken = outro.get(line);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (state == State.ENDING) broadcast("<gray><italic>" + spoken + "</italic></gray>");
            }, 60L + line * 70L);
        }
        boolean record = key == null && !test && !assisted() && !party.isEmpty() && service.records().record(expedition.id(), tier.id(), seconds, party);
        if (key != null && !test) service.settleKey(this, keyClock(now), party);
        else if (tier.id().equals("mythic") && !test && !assisted()) service.grantFirstKeys(party.keySet());
        broadcast("<gold>Cleared in " + Chat.duration(Duration.ofSeconds(seconds)) + ".</gold>"
            + (record ? " <light_purple>New server record!</light_purple>" : "")
            + " <gray>Walk into the exit light, or use " + Chat.command("expedition leave") + ". The instance closes in "
            + Chat.duration(service.lingerAfterFinish()) + ".</gray>");
        if (test) broadcast("<yellow>Test run: no Marks, records or campaign progress were awarded.</yellow>");
        else if (assisted()) broadcast("<gray>Allies helped with this run, so it doesn't count toward the campaign or the expedition records.</gray>");
    }

    private record Reward(int items, long marks) { }

    private static String gradeColor(net.nerdsmp.paper.expedition.content.ExpeditionLoot.Grade grade) {
        return switch (grade) {
            case S_PLUS -> "<light_purple>";
            case S -> "<gold>";
            case A -> "<green>";
            case B -> "<aqua>";
            case C -> "<yellow>";
            case D -> "<red>";
        };
    }

    /** The expedition's fixed finish rewards for one player: its loot-profile rolls and items. */
    private List<ItemStack> fixedRewards(ExpeditionDefinition.Loot rewards, int rolls) {
        List<ItemStack> items = new ArrayList<>();
        for (int roll = 0; roll < rolls && rewards.profile() != null; roll++) items.addAll(plugin.rewardDrops().rollProfile(rewards.profile()));
        rewards.items().forEach((item, amount) -> items.add(item(item, amount)));
        return items;
    }

    /** Credits a loot table's Marks (scaled by the tier); returns the amount. */
    private long payMarks(AccountId account, ExpeditionDefinition.Loot rewards, String slot) {
        long marks = Math.round(rewards.marks() * tier.marks());
        if (marks > 0 && !test) plugin.economy().credit(account, marks,
            new EconomyService.TransactionReason("expedition.reward", expedition.id() + ":" + tier.id()), "expedition:" + id + ":" + slot + ":" + account);
        return marks;
    }

    /** Gives one player a loot table: profile rolls, fixed items and Marks. Overflow waits until they leave. */
    private Reward pay(Player player, AccountId account, ExpeditionDefinition.Loot loot, int rolls, String slot) {
        List<ItemStack> rewards = new ArrayList<>();
        for (int roll = 0; roll < rolls && loot.profile() != null; roll++) rewards.addAll(plugin.rewardDrops().rollProfile(loot.profile()));
        loot.items().forEach((item, amount) -> rewards.add(item(item, amount)));
        List<ItemStack> overflow = new ArrayList<>(player.getInventory().addItem(rewards.toArray(ItemStack[]::new)).values());
        if (!overflow.isEmpty()) {
            pendingLoot.computeIfAbsent(player.getUniqueId(), uuid -> new ArrayList<>()).addAll(overflow);
            Chat.notice(player, "Your bags are full. The rest of your loot waits until you leave the expedition.");
        }
        long marks = Math.round(loot.marks() * tier.marks());
        if (marks > 0 && !test) plugin.economy().credit(account, marks,
            new EconomyService.TransactionReason("expedition.reward", expedition.id() + ":" + tier.id()), "expedition:" + id + ":" + slot + ":" + account);
        return new Reward(rewards.stream().mapToInt(ItemStack::getAmount).sum(), marks);
    }

    /** A room's own loot (a raid boss's), once per player per tier per week on raids. */
    void payRoom(Room room) {
        var loot = room.loot();
        if (loot.profile() == null && loot.items().isEmpty() && loot.marks() == 0) return;
        for (UUID member : progress.present()) {
            Player player = Bukkit.getPlayer(member);
            Optional<AccountId> account = plugin.sessions().account(member);
            if (player == null || account.isEmpty()) continue;
            if (expedition.story().raid() && !service.lockouts().claim(account.get().toString(), expedition.id(), tier.id(), room.id(), service.raidWeek())) {
                Chat.notice(player, "You've already looted " + Chat.escape(room.title()) + " on " + Chat.escape(tier.title()) + " this week.");
                continue;
            }
            Reward reward = pay(player, account.get(), loot, 1, room.id());
            Chat.success(player, "<gold>" + Chat.escape(room.title()) + "</gold> spoils: <white>" + reward.items() + " items</white>"
                + (reward.marks() > 0 ? " and " + Chat.marks(reward.marks()) : "") + ".");
        }
    }

    List<ItemStack> takePendingLoot(UUID member) {
        List<ItemStack> waiting = new ArrayList<>(pendingLoot.getOrDefault(member, List.of()));
        pendingLoot.remove(member);
        waiting.addAll(loot.unclaimed(member)); // An unopened reward chest goes home with its owner.
        return waiting;
    }

    private ItemStack item(String id, int amount) {
        var lamp = plugin.skillLamps() == null ? java.util.Optional.<net.nerdsmp.paper.item.SkillLamps.Grade>empty() : plugin.skillLamps().parse(id);
        if (lamp.isPresent()) return plugin.skillLamps().bottle(lamp.get(), amount);
        ItemStack item;
        try {
            ItemId itemId = new ItemId(id);
            item = plugin.rewardDrops().profiles().knownItems().contains(itemId) ? plugin.items().create(itemId) : null;
        } catch (IllegalArgumentException invalid) {
            item = null;
        }
        if (item == null) {
            Material material = Material.matchMaterial(id);
            item = new ItemStack(material == null ? Material.PAPER : material);
        }
        item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), amount)));
        return item;
    }

    private void spawnWave(String roomId, int index) {
        Room room = expedition.rooms().get(roomId);
        var waves = room.encounter().waves();
        if (waves.isEmpty()) return;
        double size = effectiveSize();
        waves.get(Math.floorMod(index, waves.size())).mobs().forEach((mob, count) -> {
            int scaled = service.scaling().scaledCount(count, size, expedition.maxParty());
            for (int copy = 0; copy < scaled; copy++) spawn(room, mob, null, false);
        });
    }

    private void spawnBoss(String roomId, int index) {
        Room room = expedition.rooms().get(roomId);
        List<String> list = room.encounter().bossList();
        if (list.isEmpty()) return;
        String id = list.get(Math.min(index, list.size() - 1));
        LivingEntity entity = spawn(room, id, room.points().isEmpty() ? null : index, true);
        if (entity == null) return;
        bosses.put(entity.getUniqueId(), index);
        casters.put(entity.getUniqueId(), new Caster(ExpeditionDefinition.Role.BOSS, bossAbilities(room, 0), 1.4));
        if (barMiniboss != null) {
            hideBar();
            barMiniboss = null;
        }
        if (bossBar == null) {
            Component name = room.encounter().type() == ExpeditionDefinition.EncounterType.COUNCIL
                ? Chat.render("<red>" + Chat.escape(room.title()) + "</red>") : displayName(entity);
            bossBar = BossBar.bossBar(name, 1F, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
            for (Player player : online()) player.showBossBar(bossBar);
        }
        world.playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2F, .8F);
        // A name card for the boss, unless a cutscene is introducing it.
        String epithet = expedition.story().mob(id).title();
        for (Player player : online())
            if (!stage.inCutscene(player)) player.showTitle(Title.title(displayName(entity),
                epithet == null ? Component.empty() : Chat.render("<gray>" + Chat.escape(epithet) + "</gray>"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(600))));
    }

    /** A boss's abilities for a phase: the phase's list, or the encounter's when the phase lists none. */
    private List<String> bossAbilities(Room room, int phase) {
        var encounter = room.encounter();
        for (int index = phase - 1; index >= 0; index--) {
            var listed = encounter.phases().get(index).abilities();
            if (!listed.isEmpty()) return listed;
        }
        return encounter.abilities();
    }

    private void enterPhase(String roomId, int phase) {
        Room room = expedition.rooms().get(roomId);
        var definition = room.encounter().phases().get(phase - 1);
        List<String> abilities = bossAbilities(room, phase);
        for (UUID uuid : bosses.keySet()) {
            Caster caster = casters.get(uuid);
            if (caster != null) {
                caster.abilities = abilities;
                caster.nextCast = System.currentTimeMillis() + 2_000;
            }
        }
        String title = definition.title() == null ? "Phase " + (phase + 1) : definition.title();
        if (definition.submergeSeconds() > 0) submerge(definition.submergeSeconds());
        for (Player player : online()) {
            player.showTitle(Title.title(Component.empty(), Chat.render("<dark_red>" + title + "</dark_red>"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400))));
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, .5F, 1.2F);
        }
        if (!abilities.isEmpty()) broadcast("<red>" + Chat.escape(title) + ":</red> <gray>" + String.join(", ",
            abilities.stream().map(id -> Abilities.info(id).map(Abilities.Info::title).orElse(id)).toList()) + "</gray>");
    }

    /** The boss withdraws, untouchable and unseen, while its adds fight; see {@link #surface()}. */
    private void submerge(int seconds) {
        long now = System.currentTimeMillis();
        submergedAt = now;
        surfaceAt = now + seconds * 1000L;
        for (UUID uuid : bosses.keySet()) {
            if (!(Bukkit.getEntity(uuid) instanceof LivingEntity boss)) continue;
            fight.cancelCast(uuid);
            world.spawnParticle(Particle.LAVA, boss.getLocation(), 40, 1, .5, 1, 0);
            world.playSound(boss.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1.2F, .6F);
            boss.setInvulnerable(true);
            boss.setInvisible(true);
            if (boss instanceof Mob mob) mob.setAware(false);
            Caster caster = casters.get(uuid);
            if (caster != null) caster.nextCast = surfaceAt + 2_000;
        }
        if (bossBar != null) bossBar.color(BossBar.Color.PURPLE);
        broadcast("<dark_red>The boss sinks out of reach!</dark_red> <gray>Hold out, and kill what it leaves behind to bring it back sooner.</gray>");
    }

    private void surface() {
        surfaceAt = -1;
        for (UUID uuid : bosses.keySet()) {
            if (!(Bukkit.getEntity(uuid) instanceof LivingEntity boss)) continue;
            boss.setInvulnerable(false);
            boss.setInvisible(false);
            if (boss instanceof Mob mob) mob.setAware(true);
            world.spawnParticle(Particle.LAVA, boss.getLocation(), 60, 1.2, .8, 1.2, 0);
            world.spawnParticle(Particle.EXPLOSION, boss.getLocation(), 2, .6, .4, .6, 0);
            world.playSound(boss.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.3F, .5F);
            Caster caster = casters.get(uuid);
            if (caster != null) caster.nextCast = System.currentTimeMillis() + 2_000;
        }
        if (bossBar != null) bossBar.color(BossBar.Color.RED);
        broadcast("<red><bold>It rises again!</bold></red>");
    }

    private Component displayName(LivingEntity entity) {
        return plugin.mobs().isCustomMob(entity) ? plugin.mobs().displayName(entity) : Component.text(Chat.humanize(entity.getType().name()));
    }

    private String plainName(LivingEntity entity) {
        return Chat.escape(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(displayName(entity)));
    }

    private void hideBar() {
        if (bossBar != null) for (UUID member : members) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) player.hideBossBar(bossBar);
        }
        bossBar = null;
    }

    /** With no boss up, the bar follows the first living mini-boss. */
    private void updateMinibossBar() {
        if (barMiniboss != null) {
            if (Bukkit.getEntity(barMiniboss) instanceof LivingEntity living && living.isValid() && !living.isDead()) {
                if (bossBar != null) bossBar.progress((float) Math.max(0, Math.min(1, living.getHealth() / maxHealth(living))));
                return;
            }
            hideBar();
            barMiniboss = null;
        }
        for (var entry : casters.entrySet()) {
            if (entry.getValue().role != ExpeditionDefinition.Role.MINIBOSS) continue;
            if (!(Bukkit.getEntity(entry.getKey()) instanceof LivingEntity living) || living.isDead()) continue;
            barMiniboss = entry.getKey();
            bossBar = BossBar.bossBar(displayName(living), (float) (living.getHealth() / maxHealth(living)), BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
            for (Player player : online()) player.showBossBar(bossBar);
            return;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Abilities
    // ---------------------------------------------------------------------------------------------

    private final AbilityCaster.Arena arena = new AbilityCaster.Arena() {
        @Override public boolean live() { return state == State.ACTIVE; }

        @Override public List<Player> targets(LivingEntity caster, double radius) {
            List<Player> found = new ArrayList<>();
            for (Player player : online())
                if (!player.isDead() && player.getGameMode() != org.bukkit.GameMode.SPECTATOR && !stage.inCutscene(player)
                    && player.getLocation().distanceSquared(caster.getLocation()) <= radius * radius) found.add(player);
            return found;
        }

        @Override public List<LivingEntity> friends(LivingEntity caster, double radius) {
            List<LivingEntity> found = new ArrayList<>();
            for (UUID uuid : damage.keySet())
                if (Bukkit.getEntity(uuid) instanceof LivingEntity living && !living.isDead()
                    && living.getLocation().distanceSquared(caster.getLocation()) <= radius * radius) found.add(living);
            return found;
        }

        @Override public List<LivingEntity> summon(LivingEntity caster, int count) {
            Room room = progress.activeRoom();
            List<LivingEntity> spawned = new ArrayList<>();
            if (room == null) return spawned;
            // Helpers are the room's first ordinary mob, never another elite, mini-boss or boss.
            String mob = room.encounter().waves().stream().flatMap(wave -> wave.mobs().keySet().stream())
                .filter(id -> expedition.story().mob(id).role() == ExpeditionDefinition.Role.NORMAL && !plugin.mobs().encounterBoss(id))
                .findFirst().orElse("vanilla:zombie");
            for (int copy = 0; copy < count; copy++) {
                LivingEntity helper = spawnAt(room, mob, caster.getLocation().clone().add((copy * 2 - 1) * 1.5, 0, 1), false);
                if (helper != null) spawned.add(helper);
            }
            return spawned;
        }

        @Override public void shield(LivingEntity caster, List<LivingEntity> helpers) {
            Set<UUID> ids = new HashSet<>();
            for (LivingEntity helper : helpers) ids.add(helper.getUniqueId());
            shields.put(caster.getUniqueId(), ids);
            caster.setInvulnerable(true);
            broadcast("<yellow>" + plainName(caster) + " is shielded!</yellow> <gray>Kill its helpers to break it.</gray>");
        }

        @Override public void temporary(Block block, Material type, int ticks) { stage.temporary(block, type, ticks); }

        @Override public LivingEntity target(LivingEntity caster) { return fight.target(caster); }
    };

    /** Once a second: each caster in a fight uses one ready ability, with a pause between casts. */
    private void tickAbilities(long now) {
        if (state != State.ACTIVE || casters.isEmpty()) return;
        var random = java.util.concurrent.ThreadLocalRandom.current();
        for (var entry : List.copyOf(casters.entrySet())) {
            Caster caster = entry.getValue();
            if (caster.abilities.isEmpty() || now < caster.nextCast) continue;
            if (!(Bukkit.getEntity(entry.getKey()) instanceof LivingEntity living) || living.isDead() || abilityCaster.busy(living)) continue;
            if (packs.dormant(entry.getKey()) || fight.casting(entry.getKey()) || arena.targets(living, 20).isEmpty()) continue;
            List<String> ready = new ArrayList<>();
            for (String ability : caster.abilities)
                if (now >= caster.ready.getOrDefault(ability, 0L) && abilityCaster.useful(ability, living, arena)) ready.add(ability);
            if (ready.isEmpty()) continue;
            String ability = ready.get(random.nextInt(ready.size()));
            double power = caster.power * (enraged && caster.role == ExpeditionDefinition.Role.BOSS ? 1.25 : 1);
            Abilities.Info info = Abilities.info(ability).orElse(null);
            if (info != null && info.casted()) fight.cast(living, info, stopped -> {
                if (!stopped && state == State.ACTIVE) abilityCaster.cast(ability, living, arena, power);
            });
            else if (!abilityCaster.cast(ability, living, arena, power)) continue;
            logAbility(caster.role + " " + living.getType().name().toLowerCase(Locale.ROOT) + " casts " + ability);
            double haste = caster.role == ExpeditionDefinition.Role.BOSS ? .8 : 1;
            caster.ready.put(ability, now + Math.round(Abilities.info(ability).map(Abilities.Info::cooldownSeconds).orElse(10) * 1000 * haste));
            long castMillis = info == null ? 0 : Math.round(info.castSeconds() * 1000);
            caster.nextCast = now + castMillis + (caster.role == ExpeditionDefinition.Role.BOSS ? 3_500 : castMillis > 0 ? 1_500 : 5_000);
        }
    }

    /** The player hit or was hit; campfires won't heal them for a few seconds. */
    void combat(Player player) { combat.put(player.getUniqueId(), System.currentTimeMillis()); }

    /** A player right-clicked a block in the run: campfires, then puzzle blocks. */
    /** A right-clicked block: loot and reward chests (also after the finish), then campfires, crates and puzzles. */
    void interact(Player player, Block block) {
        if (state != State.ACTIVE && state != State.ENDING) return;
        if (loot.interact(player, block) || state != State.ACTIVE || stage.interact(player, block)) return;
        if (supplies(player, block)) return;
        puzzles.used(player, block);
    }

    /** Gives items, keeping what doesn't fit until the player leaves. */
    void give(Player player, List<ItemStack> items) {
        List<ItemStack> overflow = new ArrayList<>(player.getInventory().addItem(items.toArray(ItemStack[]::new)).values());
        if (overflow.isEmpty()) return;
        pendingLoot.computeIfAbsent(player.getUniqueId(), uuid -> new ArrayList<>()).addAll(overflow);
        Chat.notice(player, "Your bags are full. The rest waits until you leave the expedition.");
    }

    /** A puzzle supply crate in the active room: hands out the puzzle's kit to anyone without one. */
    boolean supplies(Player player, Block block) {
        Room room = progress.activeRoom();
        if (room == null) return false;
        for (var puzzle : room.puzzles().values()) {
            Point at = puzzle.supply();
            if (at == null || at.x() != block.getX() || at.y() != block.getY() || at.z() != block.getZ()) continue;
            if (RunSupplies.carries(plugin, player, puzzle)) {
                Chat.notice(player, "You already have supplies for this. Use what you're carrying.");
                return true;
            }
            var kit = RunSupplies.kit(plugin, puzzle);
            player.getInventory().addItem(kit.toArray(ItemStack[]::new)).values()
                .forEach(item -> world.dropItem(player.getLocation(), item));
            player.playSound(block.getLocation(), Sound.BLOCK_BARREL_OPEN, 1F, 1.1F);
            Chat.success(player, "The supply crate hands you " + RunSupplies.describe(puzzle) + ".");
            return true;
        }
        return false;
    }

    void hitBlock(Block block) { if (state == State.ACTIVE) puzzles.hit(block, System.currentTimeMillis()); }

    boolean isNpc(Entity entity) { return stage.isNpc(entity); }

    void talk(Player player, Entity entity) { stage.talk(player, entity); }

    boolean ownsRock(Entity entity) { return stage.ownsRock(entity); }

    boolean inCutscene(Player player) { return stage.inCutscene(player); }

    /** Spawns one encounter mob at the given point (or the room's next point) with tier and modifier scaling. */
    LivingEntity spawn(Room room, String mob, Integer pointIndex, boolean asBoss) {
        if (room.points().isEmpty()) return null;
        int index = pointIndex != null ? Math.floorMod(pointIndex, room.points().size())
            : nextPoint.merge(room.id(), 1, Integer::sum) % room.points().size();
        Point point = room.points().get(index);
        Location at = new Location(world, point.x() + .5, point.y(), point.z() + .5, point.yaw(), 0);
        return spawnAt(room, mob, at, asBoss);
    }

    /** Spawns one encounter mob at a location with tier, modifier, role and party-size scaling. */
    LivingEntity spawnAt(Room room, String mob, Location at, boolean asBoss) {
        var profile = expedition.story().mob(mob);
        double roleHealth = asBoss ? 1 : switch (profile.role()) { case ELITE -> 1.6; case MINIBOSS -> 3.0; default -> 1; };
        double roleDamage = asBoss ? 1 : switch (profile.role()) { case ELITE -> 1.15; case MINIBOSS -> 1.3; default -> 1; };
        int level = Math.max(1, Math.min(100, expedition.level() + tier.levelBonus() + (asBoss ? room.encounter().bossLevelBonus() : 0)));
        boolean boss = asBoss || plugin.mobs().encounterBoss(mob);
        double health = tier.health() * Modifier.healthMultiplier(modifiers, boss)
            * service.scaling().healthFactor(effectiveSize(), expedition.maxParty())
            * (asBoss ? room.encounter().bossHealth() : 1) * roleHealth;
        LivingEntity entity = plugin.mobs().spawnEncounter(mob, at, level, spawned -> {
            spawned.setRemoveWhenFarAway(false);
            spawned.setPersistent(true);
            spawned.setCanPickupItems(false); // Counterweights thrown on plates stay where they land.
            var max = spawned.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                max.setBaseValue(max.getBaseValue() * health);
                spawned.setHealth(max.getValue());
            }
        });
        damage.put(entity.getUniqueId(), tier.damage() * Modifier.damageMultiplier(modifiers, boss) * (asBoss ? room.encounter().bossDamage() : 1) * roleDamage);
        if (asBoss) roles.put(entity.getUniqueId(), ExpeditionDefinition.Role.BOSS);
        if (!asBoss) {
            mobs.computeIfAbsent(room.id(), key -> new HashSet<>()).add(entity.getUniqueId());
            // Smaller groups meet fewer enemies, so each one counts for more toward the forces goal.
            double fewer = Math.max(.2, service.scaling().countFactor(effectiveSize(), expedition.maxParty()));
            forcesOf.put(entity.getUniqueId(), profile.forces() <= 0 ? 0 : (int) Math.ceil(profile.forces() / fewer - 1e-9));
            ExpeditionDefinition.Role role = profile.role() == ExpeditionDefinition.Role.NORMAL && plugin.mobs().isElite(entity)
                ? ExpeditionDefinition.Role.ELITE : profile.role();
            roles.put(entity.getUniqueId(), role);
            List<String> abilities = profile.abilities();
            var random = java.util.concurrent.ThreadLocalRandom.current();
            if (abilities.isEmpty() && (role == ExpeditionDefinition.Role.ELITE || role == ExpeditionDefinition.Role.MINIBOSS)) {
                List<String> pool = new ArrayList<>(Abilities.ELITE_POOL);
                java.util.Collections.shuffle(pool, random);
                abilities = pool.subList(0, role == ExpeditionDefinition.Role.MINIBOSS ? 2 : 1);
            }
            if (!abilities.isEmpty()) {
                Caster caster = new Caster(role, List.copyOf(abilities), role == ExpeditionDefinition.Role.MINIBOSS ? 1.2 : 1);
                caster.nextCast = System.currentTimeMillis() + 3_000 + random.nextInt(3_000);
                casters.put(entity.getUniqueId(), caster);
            }
        }
        if (entity instanceof Mob creature) {
            if (profile.behavior() == ExpeditionDefinition.Behavior.CASTER) fight.caster(creature);
            nearestMember(at).ifPresent(creature::setTarget);
        }
        world.spawnParticle(Particle.SMOKE, at.clone().add(0, 1, 0), 12, .3, .6, .3, .01);
        return entity;
    }

    private Optional<Player> nearestMember(Location at) {
        return world.getPlayers().stream().filter(player -> members.contains(player.getUniqueId()) && !player.isDead())
            .filter(player -> player.getLocation().distanceSquared(at) <= 48 * 48)
            .min(java.util.Comparator.comparingDouble(player -> player.getLocation().distanceSquared(at)));
    }

    private int alive(String roomId) {
        Set<UUID> set = mobs.get(roomId);
        if (set == null) return 0;
        set.removeIf(uuid -> !(Bukkit.getEntity(uuid) instanceof LivingEntity living) || living.isDead() || !living.isValid());
        return set.size();
    }

    private void despawn(String roomId) {
        Set<UUID> set = mobs.remove(roomId);
        if (set != null) for (UUID uuid : set) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) entity.remove();
            damage.remove(uuid);
        }
        Room room = expedition.rooms().get(roomId);
        if (room != null && room.bossRoom()) {
            for (UUID uuid : bosses.keySet()) {
                Entity entity = Bukkit.getEntity(uuid);
                if (entity != null) entity.remove();
                damage.remove(uuid);
                casters.remove(uuid);
            }
            bosses.clear();
            hideBar();
        }
        if (set != null) set.forEach(uuid -> { casters.remove(uuid); forcesOf.remove(uuid); shields.remove(uuid); });
    }

    private void gate(String roomId, boolean open) {
        Map<BlockPos, BlockData> blocks = gates.get(roomId);
        if (blocks == null || blocks.isEmpty()) return;
        double x = 0, y = 0, z = 0;
        for (var entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            world.getBlockAt(pos.x(), pos.y(), pos.z()).setBlockData(open ? Material.AIR.createBlockData() : entry.getValue(), false);
            x += pos.x(); y += pos.y(); z += pos.z();
        }
        Location center = new Location(world, x / blocks.size() + .5, y / blocks.size() + .5, z / blocks.size() + .5);
        world.spawnParticle(open ? Particle.CLOUD : Particle.SMOKE, center, 40, 1.2, 1.2, 1.2, .02);
        world.playSound(center, open ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_IRON_DOOR_CLOSE, 1.2F, .7F);
    }

    private void runScript(String roomId, List<ScriptAction> actions, int start) {
        for (int index = start; index < actions.size(); index++) {
            if (state != State.ACTIVE) return;
            ScriptAction action = actions.get(index);
            try {
                if (action.verb() == ScriptAction.Verb.WAIT) {
                    int resume = index + 1;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> runScript(roomId, actions, resume), action.intArg(0, 1) * 20L);
                    return;
                }
                perform(roomId, action);
            } catch (RuntimeException failure) {
                plugin.getLogger().warning("Expedition " + expedition.id() + " script '" + action.text() + "' failed: " + failure.getMessage());
            }
        }
    }

    private void perform(String roomId, ScriptAction action) {
        Room room = expedition.rooms().get(roomId);
        switch (action.verb()) {
            case MESSAGE -> broadcast(action.arg(0));
            case TITLE -> {
                String[] parts = action.arg(0).split("\\|", 2);
                for (Player player : online()) player.showTitle(Title.title(Chat.render(parts[0]),
                    parts.length > 1 ? Chat.render(parts[1]) : Component.empty()));
            }
            case SOUND -> {
                float pitch = action.args().size() > 1 ? Float.parseFloat(action.arg(1)) : 1F;
                for (Player player : online()) player.playSound(player.getLocation(), action.arg(0).toLowerCase(Locale.ROOT), 1F, pitch);
            }
            case SPAWN -> {
                int count = action.intArg(1, 1);
                Integer point = action.args().size() > 2 ? action.intArg(2, 1) - 1 : null;
                for (int copy = 0; copy < count; copy++) spawn(room, action.arg(0).toLowerCase(Locale.ROOT), point, false);
            }
            case OPEN -> gate(action.arg(0), true);
            case CLOSE -> gate(action.arg(0), false);
            case EFFECT -> {
                var type = Registry.MOB_EFFECT.get(NamespacedKey.minecraft(action.arg(0).toLowerCase(Locale.ROOT)));
                if (type != null) for (Player player : online())
                    player.addPotionEffect(new PotionEffect(type, action.intArg(1, 10) * 20, action.intArg(2, 0)));
            }
            case CHECKPOINT -> apply(progress.checkpointHere(), System.currentTimeMillis());
            case COMMAND -> {
                String command = action.arg(0).replace("{run}", id.toString());
                if (command.contains("{player}")) for (Player player : online())
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", player.getName()));
                else Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
            case SAY -> stage.say(action.arg(0));
            case CUTSCENE -> stage.cutscene(action.arg(0));
            case BUILD -> stage.build(action.arg(0));
            case BREAK -> stage.breakPiece(action.arg(0));
            case RESTORE -> stage.restore(action.arg(0));
            case HAZARD -> stage.hazard(action.arg(0).toLowerCase(Locale.ROOT), action.arg(1));
            case CAMPFIRE -> stage.light(action.arg(0));
            case WAIT -> { }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Hired allies
    // ---------------------------------------------------------------------------------------------

    /** Players present plus weighted living allies, for party-size scaling. */
    double effectiveSize() {
        int players = progress == null ? members.size() : progress.present().size();
        return service.scaling().effectiveSize(players, aliveAllies(), expedition.maxParty());
    }

    private int aliveAllies() {
        int alive = 0;
        for (AllySlot slot : allies) if (slot.entity != null && Bukkit.getEntity(slot.entity) instanceof LivingEntity living && !living.isDead()) alive++;
        return alive;
    }

    boolean isAlly(Entity entity) {
        if (entity == null || allies.isEmpty()) return false;
        UUID uuid = entity.getUniqueId();
        for (AllySlot slot : allies) if (uuid.equals(slot.entity)) return true;
        return false;
    }

    double allyDamageMultiplier() { return service.allies().damageMultiplier() * tier.health(); }

    void allyDied(LivingEntity entity) {
        for (AllySlot slot : allies) {
            if (!entity.getUniqueId().equals(slot.entity)) continue;
            slot.entity = null;
            slot.respawnAt = System.currentTimeMillis() + 15_000;
            broadcast("<gray>Your " + slot.role.title() + " is down and will rejoin in 15 seconds.</gray>");
        }
    }

    private void spawnAlly(AllySlot slot, Location at) {
        Class<? extends Mob> type = switch (slot.role) {
            case BULWARK -> org.bukkit.entity.IronGolem.class;
            case BLADE -> org.bukkit.entity.Vindicator.class;
            case ARCHER -> org.bukkit.entity.Skeleton.class;
            case HEDGE_WITCH -> org.bukkit.entity.Witch.class;
        };
        Mob spawned = world.spawn(at, type, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, false, mob -> {
            mob.customName(Chat.render("<green>[Ally]</green> <white>" + slot.role.title() + "</white>"));
            mob.setCustomNameVisible(true);
            mob.setPersistent(true);
            mob.setRemoveWhenFarAway(false);
            mob.setCanPickupItems(false);
            var max = mob.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                max.setBaseValue(slot.role.health() * tier.health());
                mob.setHealth(max.getValue());
            }
            var equipment = mob.getEquipment();
            if (equipment != null) {
                if (slot.role == Allies.Role.BLADE) equipment.setItemInMainHand(new ItemStack(Material.IRON_AXE));
                if (slot.role == Allies.Role.ARCHER) {
                    equipment.setItemInMainHand(new ItemStack(Material.BOW));
                    equipment.setHelmet(new ItemStack(Material.IRON_HELMET)); // Keeps the archer out of any sunlight.
                }
                equipment.setItemInMainHandDropChance(0);
                equipment.setHelmetDropChance(0);
            }
            if (mob instanceof org.bukkit.entity.Raider raider) {
                raider.setCanJoinRaid(false);
                raider.setPatrolLeader(false);
            }
            // Allies choose their own targets: only encounter enemies, never players.
            Bukkit.getMobGoals().removeAllGoals(mob, com.destroystokyo.paper.entity.ai.GoalType.TARGET);
        });
        slot.entity = spawned.getUniqueId();
        slot.respawnAt = -1;
        world.spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 1, 0), 12, .4, .6, .4, 0);
    }

    /** Once a second: rejoin fallen allies, aim them at the nearest enemy, keep them with the party, heal. */
    private void tickAllies(long now) {
        if (allies.isEmpty() || world == null) return;
        List<Player> party = online();
        boolean heal = ++healerClock % 3 == 0;
        for (AllySlot slot : allies) {
            if (slot.entity == null) {
                if (state == State.ACTIVE && slot.respawnAt >= 0 && now >= slot.respawnAt) spawnAlly(slot, respawnLocation());
                continue;
            }
            if (!(Bukkit.getEntity(slot.entity) instanceof Mob ally) || ally.isDead()) continue;
            Player anchor = party.stream().filter(player -> !player.isDead())
                .min(java.util.Comparator.comparingDouble(player -> player.getLocation().distanceSquared(ally.getLocation()))).orElse(null);
            LivingEntity enemy = state == State.ACTIVE ? nearestEnemy(ally.getLocation(), 24) : null;
            if (enemy != null) {
                if (ally.getTarget() == null || !ally.getTarget().equals(enemy) && !isEnemy(ally.getTarget())) ally.setTarget(enemy);
            } else {
                if (ally.getTarget() != null && !isEnemy(ally.getTarget())) ally.setTarget(null);
                if (anchor != null) {
                    double distance = anchor.getLocation().distanceSquared(ally.getLocation());
                    if (distance > 32 * 32) ally.teleport(anchor.getLocation());
                    else if (distance > 5 * 5) ally.getPathfinder().moveTo(anchor, 1.15);
                }
            }
            if (heal && slot.role == Allies.Role.HEDGE_WITCH) mend(ally, party);
        }
    }

    private void mend(Mob healer, List<Player> party) {
        Player hurt = null;
        double lowest = 1;
        for (Player player : party) {
            var max = player.getAttribute(Attribute.MAX_HEALTH);
            double ratio = player.getHealth() / (max == null ? 20 : max.getValue());
            if (!player.isDead() && ratio < lowest && player.getLocation().distanceSquared(healer.getLocation()) <= 16 * 16) {
                hurt = player;
                lowest = ratio;
            }
        }
        if (hurt == null) return;
        var max = hurt.getAttribute(Attribute.MAX_HEALTH);
        hurt.setHealth(Math.min(max == null ? 20 : max.getValue(), hurt.getHealth() + 4));
        world.spawnParticle(Particle.HEART, hurt.getLocation().add(0, 2, 0), 3, .3, .2, .3, 0);
    }

    private LivingEntity nearestEnemy(Location at, double radius) {
        LivingEntity best = null;
        double bestDistance = radius * radius;
        for (UUID uuid : damage.keySet()) {
            if (!(Bukkit.getEntity(uuid) instanceof LivingEntity living) || living.isDead() || !living.getWorld().equals(world)) continue;
            double distance = living.getLocation().distanceSquared(at);
            if (distance < bestDistance) {
                best = living;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean isEnemy(Entity entity) { return entity != null && damage.containsKey(entity.getUniqueId()); }

    private void regroupAllies() {
        Location at = respawnLocation();
        for (AllySlot slot : allies) {
            if (slot.entity != null && Bukkit.getEntity(slot.entity) instanceof Mob ally) {
                ally.setTarget(null);
                ally.teleport(at);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    Location location(Point point) {
        return new Location(world, point.x() + .5, point.y(), point.z() + .5, point.yaw(), 0);
    }

    Location respawnLocation() {
        String checkpoint = progress == null ? null : progress.checkpoint();
        if (checkpoint != null && checkpoint.startsWith(RunProgress.CAMPFIRE)) {
            var fire = expedition.story().campfires().get(checkpoint.substring(RunProgress.CAMPFIRE.length()));
            if (fire != null) return location(new Point(fire.at().x() + 1, fire.at().y(), fire.at().z(), fire.at().yaw()));
        }
        Room room = checkpoint == null ? null : expedition.rooms().get(checkpoint);
        return location(room != null && room.checkpoint() != null ? room.checkpoint() : expedition.spawn());
    }

    List<Player> online() {
        List<Player> players = new ArrayList<>();
        for (UUID member : members) {
            Player player = Bukkit.getPlayer(member);
            if (player != null && world != null && player.getWorld().equals(world)) players.add(player);
        }
        return players;
    }

    void broadcast(String markup) {
        for (Player player : online()) Chat.send(player, markup);
    }

    private String modifierNames() {
        return String.join(", ", modifiers.stream().map(Modifier::title).sorted().toList());
    }

    private void ticketChunks() {
        Set<Long> chunks = new HashSet<>();
        if (expedition.spawn() != null) chunks.add(chunkKey(expedition.spawn().x() >> 4, expedition.spawn().z() >> 4));
        for (Room room : expedition.roomList()) {
            if (room.region() == null) continue;
            for (int cx = room.region().minX() >> 4; cx <= room.region().maxX() >> 4; cx++)
                for (int cz = room.region().minZ() >> 4; cz <= room.region().maxZ() >> 4; cz++) chunks.add(chunkKey(cx, cz));
        }
        for (var pack : expedition.story().packs().values()) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                chunks.add(chunkKey((pack.at().x() >> 4) + dx, (pack.at().z() >> 4) + dz));
            for (Point stop : pack.patrol()) chunks.add(chunkKey(stop.x() >> 4, stop.z() >> 4));
        }
        int added = 0;
        for (long key : chunks) {
            if (added++ >= 400) break;
            world.addPluginChunkTicket((int) (key >> 32), (int) key, plugin);
        }
    }

    private static long chunkKey(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }

    private void exitBeacon() {
        if (expedition.exit() == null || world == null) return;
        Location exit = location(expedition.exit());
        world.spawnParticle(Particle.END_ROD, exit.clone().add(0, 1, 0), 12, .3, 1.2, .3, .01);
        world.spawnParticle(Particle.HAPPY_VILLAGER, exit, 6, .6, .2, .6, 0);
    }

    /** Pushes the run panel to every member's sidebar: clock, lives, objectives and the party. */
    private void updateStatus(long now) {
        if (progress == null || plugin.presentation() == null) return;
        long left = state == State.ENDING ? 0 : progress.secondsLeft(now);
        List<String> lines = new ArrayList<>();
        lines.add("<gray>" + Chat.escape(tier.title()) + (modifiers.isEmpty() ? "" : " <dark_gray>·</dark_gray> " + Chat.escape(modifierNames())) + "</gray>");
        String clock = String.format(Locale.ROOT, "%d:%02d", left / 60, left % 60);
        if (key != null) {
            long keyLeft = keyTimer() - keyClock(now);
            long shown = Math.abs(keyLeft);
            String keyClock = String.format(Locale.ROOT, "%d:%02d", shown / 60, shown % 60);
            lines.add(state == State.ENDING ? completed ? "<green>✔ Complete</green>" : "<red>✘ Failed</red>"
                : keyLeft >= 0 ? "<light_purple>Key</light_purple> " + (keyLeft <= 60 ? "<yellow>" : "<white>") + keyClock + "</white>"
                    + "   <gray>Deaths</gray> <red>" + progress.deaths() + "</red>"
                : "<red>Over by " + keyClock + "</red>   <gray>Deaths</gray> <red>" + progress.deaths() + "</red>");
        } else if (!progress.clockRuns()) {
            long elapsed = progress.elapsedSeconds(now);
            lines.add(state == State.ENDING ? completed ? "<green>✔ Complete</green>" : "<red>✘ Failed</red>"
                : "<gray>Time</gray> <white>" + String.format(Locale.ROOT, "%d:%02d", elapsed / 60, elapsed % 60) + "</white>"
                    + "   <gray>Deaths</gray> <red>" + progress.deaths() + "</red>");
        } else lines.add(state == State.ENDING ? completed ? "<green>✔ Complete</green>" : "<red>✘ Failed</red>"
            : "<gray>Time</gray> " + (left <= 60 ? "<red>" : left <= 300 ? "<yellow>" : "<white>") + clock + "</white>"
                + "   <gray>Lives</gray> <red>❤ " + Math.max(0, progress.lives()) + "</red>");
        lines.add("<dark_gray>────────────</dark_gray>");
        List<String> objectives = new ArrayList<>();
        Room active = progress.activeRoom();
        for (RunProgress.Objective objective : progress.objectives(now)) {
            String mark = switch (objective.status()) {
                case DONE -> "<green>✔</green> <dark_gray>";
                case ACTIVE -> "<gold>▶</gold> <white>";
                case OPEN -> "<gray>○</gray> <gray>";
                case LOCKED -> "<dark_gray>▪ ";
            };
            String text = objective.text();
            if (objective.depth() > 0 && active != null) {
                for (var puzzle : active.puzzles().values())
                    if (puzzle.title().equals(text)) {
                        String count = puzzles.progress(active, puzzle);
                        if (!count.isEmpty() && objective.status() != RunProgress.Objective.Status.DONE) text += " " + count;
                    }
            }
            objectives.add((objective.depth() > 0 ? "  " : "") + mark + Chat.escape(text));
        }
        // Keep the panel on screen: completed rooms collapse first when there are many.
        int room = 15 - lines.size() - 2;
        while (objectives.size() > room) {
            int done = -1;
            for (int index = 0; index < objectives.size(); index++) if (objectives.get(index).startsWith("<green>✔")) { done = index; break; }
            if (done < 0) break;
            objectives.remove(done);
        }
        lines.addAll(objectives.subList(0, Math.min(objectives.size(), room)));
        lines.add("<dark_gray>────────────</dark_gray>");
        StringBuilder party = new StringBuilder();
        for (UUID member : members) {
            Player player = Bukkit.getPlayer(member);
            if (player == null) continue;
            double health = player.isDead() ? 0 : player.getHealth();
            String color = health <= 6 ? "red" : health <= 12 ? "yellow" : "green";
            if (!party.isEmpty()) party.append(" ");
            party.append("<").append(color).append(">").append(Chat.escape(shortName(player.getName()))).append(" ").append((int) Math.ceil(health)).append("</").append(color).append(">");
        }
        lines.add(party.toString());
        String title = "<gold><bold>" + Chat.escape(expedition.title()) + "</bold></gold>";
        for (Player player : online()) plugin.presentation().showOverlay(player, title, lines);
    }

    private static String shortName(String name) { return name.length() <= 7 ? name : name.substring(0, 6) + "…"; }
}
