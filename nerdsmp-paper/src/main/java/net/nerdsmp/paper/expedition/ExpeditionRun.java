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
import net.nerdsmp.paper.expedition.content.Modifier;
import net.nerdsmp.paper.expedition.content.ScriptAction;
import net.nerdsmp.paper.expedition.content.Tier;
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
    private boolean enraged;
    private long closeAt = -1;
    private boolean completed;
    private final List<AllySlot> allies = new ArrayList<>();
    private int healerClock;

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
                  Set<Modifier> modifiers, List<UUID> members, boolean test, BuildCanvas.Plan plan, List<Allies.Role> hired) {
        this.service = service;
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
        });
    }

    BuildCanvas.Plan plan() { return plan; }
    boolean completed() { return completed; }

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
        if (assisted()) broadcast("<aqua>Hired allies:</aqua> <white>" + String.join(", ", allies.stream()
            .map(slot -> slot.role.title() + " (" + slot.role.job() + ")").toList()) + "</white>. <gray>This is an assisted run: "
            + "it pays loot and Marks, but doesn't count toward the campaign or the expedition records.</gray>");
        broadcast("<gold>" + Chat.escape(expedition.title()) + "</gold> <gray>(" + Chat.escape(tier.title()) + ")</gray> <white>has begun.</white> "
            + "<gray>Your party has <white>" + progress.lives() + "</white> lives and <white>" + expedition.timeLimitMinutes()
            + "</white> minutes. Push forward into the first room.</gray>");
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
        stage.tick(now);
        puzzles.tick(now);
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
        state = State.CLOSED;
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

    double damageMultiplier(Entity entity) { return damage.getOrDefault(entity.getUniqueId(), 1.0); }

    void mobDied(LivingEntity entity) {
        UUID uuid = entity.getUniqueId();
        Integer bossSlot = bosses.remove(uuid);
        damage.remove(uuid);
        casters.remove(uuid);
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
            if (!bosses.isEmpty()) broadcast("<gold>" + plainName(entity) + " falls!</gold> <gray>" + bosses.size() + " remain.</gray>");
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
            }
            case Effect.SpawnWave wave -> spawnWave(wave.room(), wave.wave());
            case Effect.SpawnBoss spawn -> spawnBoss(spawn.room(), spawn.index());
            case Effect.Script script -> runScript(script.room(), expedition.rooms().get(script.room()).encounter().script(script.trigger()), 0);
            case Effect.OpenGate open -> gate(open.room(), true);
            case Effect.DespawnRoom despawn -> despawn(despawn.room());
            case Effect.ResetRoom reset -> {
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
                if (room.encounter().type() != ExpeditionDefinition.EncounterType.NONE) {
                    broadcast("<green>✔ " + Chat.escape(room.title()) + " cleared.</green>");
                    for (Player player : online()) player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, .5F, 1.4F);
                }
            }
            case Effect.Lives lives -> {
                Player died = Bukkit.getPlayer(lives.died());
                broadcast("<red>" + Chat.escape(died == null ? "A party member" : died.getName()) + " fell.</red> <gray>Lives left: <white>"
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
            case Effect.Campfire campfire -> {
                var fire = expedition.story().campfires().get(campfire.campfire());
                broadcast("<gold>♨ " + Chat.escape(fire == null ? "Campfire" : fire.title()) + " lit.</gold> <gray>Rest by it out of combat to heal. "
                    + "The party will regroup here.</gray>");
            }
            case Effect.Fail fail -> {
                state = State.ENDING;
                closeAt = now + 10_000;
                broadcast("<red><bold>Expedition failed.</bold></red> <gray>" + Chat.escape(fail.reason()) + " You'll be returned in 10 seconds.</gray>");
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
        for (UUID member : progress.present()) {
            Player player = Bukkit.getPlayer(member);
            Optional<AccountId> account = plugin.sessions().account(member);
            if (player == null || account.isEmpty()) continue;
            party.put(account.get().toString(), player.getName());
            List<ItemStack> rewards = new ArrayList<>();
            var loot = expedition.loot();
            for (int roll = 0; roll < rolls && loot.profile() != null; roll++) rewards.addAll(plugin.rewardDrops().rollProfile(loot.profile()));
            loot.items().forEach((item, amount) -> rewards.add(item(item, amount)));
            List<ItemStack> overflow = new ArrayList<>(player.getInventory().addItem(rewards.toArray(ItemStack[]::new)).values());
            if (!overflow.isEmpty()) {
                pendingLoot.put(member, overflow);
                Chat.notice(player, "Your bags are full. The rest of your loot waits until you leave the expedition.");
            }
            long marks = Math.round(loot.marks() * tier.marks());
            if (marks > 0 && !test) plugin.economy().credit(account.get(), marks,
                new EconomyService.TransactionReason("expedition.reward", expedition.id() + ":" + tier.id()), "expedition:" + id + ":" + account.get());
            if (!test && !assisted()) plugin.campaign().recordExpedition(player, expedition.id(), tier.id());
            player.showTitle(Title.title(Chat.render("<gold><bold>Expedition complete</bold></gold>"),
                Chat.render("<gray>" + Chat.escape(expedition.title()) + " · " + Chat.duration(Duration.ofSeconds(seconds)) + "</gray>")));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1F, 1F);
            Chat.success(player, "Loot: <white>" + rewards.stream().mapToInt(ItemStack::getAmount).sum() + " items</white>"
                + (marks > 0 ? " and " + Chat.marks(marks) : "") + (underPar ? " <yellow>(+1 timed roll)</yellow>" : "") + ".");
        }
        boolean record = !test && !assisted() && !party.isEmpty() && service.records().record(expedition.id(), tier.id(), seconds, party);
        broadcast("<gold>Cleared in " + Chat.duration(Duration.ofSeconds(seconds)) + ".</gold>"
            + (record ? " <light_purple>New server record!</light_purple>" : "")
            + " <gray>Walk into the exit light, or use " + Chat.command("expedition leave") + ". The instance closes in "
            + Chat.duration(service.lingerAfterFinish()) + ".</gray>");
        if (test) broadcast("<yellow>Test run: no Marks, records or campaign progress were awarded.</yellow>");
        else if (assisted()) broadcast("<gray>Allies helped with this run, so it doesn't count toward the campaign or the expedition records.</gray>");
    }

    List<ItemStack> takePendingLoot(UUID member) {
        List<ItemStack> loot = pendingLoot.remove(member);
        return loot == null ? List.of() : loot;
    }

    private ItemStack item(String id, int amount) {
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
        for (Player player : online()) {
            player.showTitle(Title.title(Component.empty(), Chat.render("<dark_red>" + title + "</dark_red>"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400))));
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, .5F, 1.2F);
        }
        if (!abilities.isEmpty()) broadcast("<red>" + Chat.escape(title) + ":</red> <gray>" + String.join(", ",
            abilities.stream().map(id -> Abilities.info(id).map(Abilities.Info::title).orElse(id)).toList()) + "</gray>");
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
            String mob = room.encounter().waves().isEmpty() || room.encounter().waves().getFirst().mobs().isEmpty() ? "vanilla:zombie"
                : room.encounter().waves().getFirst().mobs().keySet().iterator().next();
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
    };

    /** Once a second: each caster in a fight uses one ready ability, with a pause between casts. */
    private void tickAbilities(long now) {
        if (state != State.ACTIVE || casters.isEmpty()) return;
        var random = java.util.concurrent.ThreadLocalRandom.current();
        for (var entry : List.copyOf(casters.entrySet())) {
            Caster caster = entry.getValue();
            if (caster.abilities.isEmpty() || now < caster.nextCast) continue;
            if (!(Bukkit.getEntity(entry.getKey()) instanceof LivingEntity living) || living.isDead() || abilityCaster.busy(living)) continue;
            if (arena.targets(living, 20).isEmpty()) continue;
            List<String> ready = new ArrayList<>();
            for (String ability : caster.abilities) if (now >= caster.ready.getOrDefault(ability, 0L)) ready.add(ability);
            if (ready.isEmpty()) continue;
            String ability = ready.get(random.nextInt(ready.size()));
            if (!abilityCaster.cast(ability, living, arena, caster.power * (enraged && caster.role == ExpeditionDefinition.Role.BOSS ? 1.25 : 1))) continue;
            if (plugin.getConfig().getBoolean("expeditions.log-abilities", false))
                plugin.getLogger().info("[" + expedition.id() + "] " + caster.role + " " + living.getType().name().toLowerCase(Locale.ROOT) + " casts " + ability);
            double haste = caster.role == ExpeditionDefinition.Role.BOSS ? .8 : 1;
            caster.ready.put(ability, now + Math.round(Abilities.info(ability).map(Abilities.Info::cooldownSeconds).orElse(10) * 1000 * haste));
            caster.nextCast = now + (caster.role == ExpeditionDefinition.Role.BOSS ? 3_500 : 5_000);
        }
    }

    /** The player hit or was hit; campfires won't heal them for a few seconds. */
    void combat(Player player) { combat.put(player.getUniqueId(), System.currentTimeMillis()); }

    /** A player right-clicked a block in the run: campfires, then puzzle blocks. */
    void interact(Player player, Block block) {
        if (state != State.ACTIVE || stage.interact(player, block)) return;
        puzzles.used(player, block);
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
            var max = spawned.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                max.setBaseValue(max.getBaseValue() * health);
                spawned.setHealth(max.getValue());
            }
        });
        damage.put(entity.getUniqueId(), tier.damage() * Modifier.damageMultiplier(modifiers, boss) * (asBoss ? room.encounter().bossDamage() : 1) * roleDamage);
        if (!asBoss) {
            mobs.computeIfAbsent(room.id(), key -> new HashSet<>()).add(entity.getUniqueId());
            // Smaller groups meet fewer enemies, so each one counts for more toward the forces goal.
            double fewer = Math.max(.2, service.scaling().countFactor(effectiveSize(), expedition.maxParty()));
            forcesOf.put(entity.getUniqueId(), profile.forces() <= 0 ? 0 : (int) Math.ceil(profile.forces() / fewer - 1e-9));
            ExpeditionDefinition.Role role = profile.role() == ExpeditionDefinition.Role.NORMAL && plugin.mobs().isElite(entity)
                ? ExpeditionDefinition.Role.ELITE : profile.role();
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
        if (entity instanceof Mob creature) nearestMember(at).ifPresent(creature::setTarget);
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
        lines.add(state == State.ENDING ? completed ? "<green>✔ Complete</green>" : "<red>✘ Failed</red>"
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
