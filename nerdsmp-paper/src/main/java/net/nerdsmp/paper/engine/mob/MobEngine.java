package net.nerdsmp.paper.engine.mob;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.mob.CustomMobManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Template-backed authoring facade. Drafts and stat previews never create world entities. */
public final class MobEngine {
    private static final Map<String, MobBlueprint> TEMPLATES = Map.of(
        "brawler", new MobBlueprint("brawler", "brawler", "Frontier Brawler", "zombie", MobBlueprint.Rank.NORMAL, 1, 24, 4, 0.23, MobBlueprint.Ability.NONE, 3, 4, 14, 5, ""),
        "ranger", new MobBlueprint("ranger", "ranger", "Frontier Ranger", "skeleton", MobBlueprint.Rank.NORMAL, 1, 20, 3, 0.25, MobBlueprint.Ability.NONE, 4, 3, 14, 5, ""),
        "guardian", new MobBlueprint("guardian", "guardian", "Stone Guardian", "husk", MobBlueprint.Rank.ELITE, 5, 48, 5, 0.18, MobBlueprint.Ability.PULSE, 4, 5, 16, 12, ""),
        "caster", new MobBlueprint("caster", "caster", "Grove Mender", "witch", MobBlueprint.Rank.ELITE, 5, 30, 2, 0.22, MobBlueprint.Ability.HEAL_SELF, 4, 6, 18, 10, "")
    );
    private final CustomMobManager manager;
    private final MobBlueprintStore store;
    private final java.util.logging.Logger logger;
    private Runnable onChange = () -> { };

    public MobEngine(JavaPlugin plugin, CustomMobManager manager) throws IOException {
        this.manager = java.util.Objects.requireNonNull(manager);
        logger = plugin.getLogger();
        store = new MobBlueprintStore(new File(plugin.getDataFolder(), "content/engine-mobs.yml"), manager.shippedIds(),
            id -> plugin instanceof NerdSmpPlugin nerd && nerd.rewardDrops() != null && nerd.rewardDrops().profiles().find(id).isPresent());
        manager.validateAuthored(store.all()); manager.applyAuthored(store.all());
    }
    public Map<String, MobBlueprint> templates() { return TEMPLATES; }
    public List<String> allowedTypes() { return net.nerdsmp.paper.zone.ZoneMobTypes.vanillaIds().stream().map(id -> id.substring(8)).toList(); }
    public synchronized void onChange(Runnable listener) { onChange = java.util.Objects.requireNonNull(listener); }
    public synchronized long revision() { return store.revision(); }
    public synchronized Optional<MobBlueprint> find(String id) { return store.find(id); }
    public synchronized List<MobBlueprint> all() { return store.all(); }
    public synchronized Optional<MobBlueprint> blueprint(String id) { return find(id).or(() -> manager.authoringBlueprint(id)); }
    public synchronized MobBlueprint draft(String id, String template) {
        MobBlueprint.validId(id, "Mob ID");
        if (find(id).isPresent() || manager.shippedIds().contains(id)) throw new IllegalArgumentException("A mob with that ID already exists.");
        MobBlueprint base = TEMPLATES.get(template); if (base == null) throw new IllegalArgumentException("Unknown template: " + template);
        return base.renamed(id);
    }
    public synchronized MobBlueprint copy(String sourceId, String newId) {
        draft(newId, "brawler");
        return blueprint(sourceId).orElseThrow(() -> new IllegalArgumentException("That encounter needs its specialized habitat or boss lifecycle.")).renamed(newId);
    }
    public synchronized void validate(MobBlueprint blueprint) {
        store.validate(blueprint);
        java.util.ArrayList<MobBlueprint> next = new java.util.ArrayList<>(store.all());
        next.removeIf(mob -> mob.id().equals(blueprint.id())); next.add(blueprint); manager.validateAuthored(next);
    }
    public synchronized void publish(MobBlueprint blueprint) throws IOException { publish(blueprint, revision()); }
    public synchronized void publish(MobBlueprint blueprint, long expectedRevision) throws IOException {
        validate(blueprint); store.publish(blueprint, expectedRevision); manager.applyAuthored(store.all()); notifyChanged();
    }
    public synchronized void reload() throws IOException {
        manager.validateAuthored(store.readCandidate()); store.reload(); manager.applyAuthored(store.all()); notifyChanged();
    }
    private void notifyChanged() {
        try { onChange.run(); }
        catch (RuntimeException error) { logger.log(java.util.logging.Level.WARNING, "Mob catalog was published, but a content observer failed.", error); }
    }
}
