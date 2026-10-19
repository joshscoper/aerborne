package net.nerdsmp.paper.engine;

import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.paper.item.PaperItemService;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

/** Reusable editor templates and live, identity-compatible staff items. No resource pack is required. */
public final class ItemEngine {
    private static final List<String> TEMPLATES = List.of("sword", "axe", "spear", "bow", "crossbow", "trident", "mace", "tool",
        "shovel", "hoe", "fishing_rod", "brush", "shears", "helmet", "chestplate", "leggings", "boots", "focus", "material");
    private final JavaPlugin plugin;
    private final PaperItemService items;
    private final ItemBlueprintStore store;
    private Runnable onChange = () -> { };

    public ItemEngine(JavaPlugin plugin, PaperItemService items) {
        this.plugin = java.util.Objects.requireNonNull(plugin); this.items = java.util.Objects.requireNonNull(items);
        try {
            store = new ItemBlueprintStore(new File(plugin.getDataFolder(), "content/engine-items.yml"), items.coreItemIds(), items.coreSetIds());
            synchronizeCatalog();
        } catch (IOException error) { throw new IllegalStateException("Unable to load authored items; content was preserved", error); }
    }
    public List<String> templates() { return TEMPLATES; }
    public ItemBlueprint draft(String id, String template) {
        ItemBlueprint.identifier(id);
        Material material;
        Map<String, Double> stats;
        switch (template) {
            case "sword" -> { material = Material.IRON_SWORD; stats = Map.of("damage", 6D); }
            case "axe" -> { material = Material.IRON_AXE; stats = Map.of("damage", 7D, "gathering-power", 1D); }
            case "spear" -> { material = Material.IRON_SPEAR; stats = Map.of("damage", 6D); }
            case "bow" -> { material = Material.BOW; stats = Map.of("projectile-damage", 6D); }
            case "crossbow" -> { material = Material.CROSSBOW; stats = Map.of("projectile-damage", 9D); }
            case "trident" -> { material = Material.TRIDENT; stats = Map.of("damage", 9D, "projectile-damage", 8D); }
            case "mace" -> { material = Material.MACE; stats = Map.of("damage", 8D); }
            case "tool" -> { material = Material.IRON_PICKAXE; stats = Map.of("damage", 3D, "mining-power", 2D); }
            case "shovel" -> { material = Material.IRON_SHOVEL; stats = Map.of("damage", 3D, "excavation-power", 2D); }
            case "hoe" -> { material = Material.IRON_HOE; stats = Map.of("damage", 2D, "gathering-power", 2D); }
            case "fishing_rod" -> { material = Material.FISHING_ROD; stats = Map.of("fishing-power", 2D); }
            case "brush" -> { material = Material.BRUSH; stats = Map.of("excavation-power", 2D); }
            case "shears" -> { material = Material.SHEARS; stats = Map.of("gathering-power", 2D); }
            case "helmet" -> { material = Material.IRON_HELMET; stats = Map.of("defense", 2D); }
            case "chestplate" -> { material = Material.IRON_CHESTPLATE; stats = Map.of("defense", 6D); }
            case "leggings" -> { material = Material.IRON_LEGGINGS; stats = Map.of("defense", 5D); }
            case "boots" -> { material = Material.IRON_BOOTS; stats = Map.of("defense", 2D); }
            case "focus" -> { material = Material.SHIELD; stats = Map.of("defense", 2D); }
            case "material" -> { material = Material.AMETHYST_SHARD; stats = Map.of(); }
            default -> throw new IllegalArgumentException("Unknown item template: " + template + ".");
        }
        return new ItemBlueprint(id, title(id), material, "COMMON", template.equals("material") ? 0 : 1, 1, null, List.of(), stats);
    }
    public ItemBlueprint copy(String newId, ItemId source) { return items.copyBlueprint(newId, source); }
    public Optional<ItemBlueprint> find(String id) { return store.find(id); }
    public List<ItemBlueprint> all() { return store.all(); }
    public boolean exists(String id) { return items.itemIds().contains(new ItemId(id)); }
    public List<ItemSetBlueprint> sets() { return store.sets(); }
    public Optional<ItemSetBlueprint> findSet(String id) { return store.findSet(id); }
    public Set<String> knownSetIds() {
        Set<String> ids = new TreeSet<>(items.coreSetIds()); store.sets().forEach(set -> ids.add(set.id())); return Set.copyOf(ids);
    }
    public ItemSetBlueprint draftSet(String id) {
        return new ItemSetBlueprint(id, title(id), 4, Map.of(2, new ItemSetBlueprint.Bonus("+2 Defense", Map.of("defense", 2D)),
            4, new ItemSetBlueprint.Bonus("+5% Damage", Map.of("damage-percent", 5D))));
    }
    public ItemStack preview(ItemBlueprint blueprint) {
        store.validateDraft(blueprint); return items.preview(blueprint);
    }
    public synchronized void publish(ItemBlueprint blueprint) throws IOException { publish(blueprint, store.find(blueprint.id()).orElse(null)); }
    public synchronized void publish(ItemBlueprint blueprint, ItemBlueprint expected) throws IOException {
        store.put(blueprint, expected); synchronizeCatalog(); changed();
    }
    public synchronized void publishSet(ItemSetBlueprint blueprint) throws IOException { publishSet(blueprint, store.findSet(blueprint.id()).orElse(null)); }
    public synchronized void publishSet(ItemSetBlueprint blueprint, ItemSetBlueprint expected) throws IOException {
        store.putSet(blueprint, expected); synchronizeCatalog(); changed();
    }
    public synchronized void reload() throws IOException { store.reload(); synchronizeCatalog(); changed(); }
    public synchronized void onChange(Runnable callback) { onChange = java.util.Objects.requireNonNull(callback); }
    private void synchronizeCatalog() { items.installEngineCatalog(store.all(), store.sets()); }
    private void changed() {
        try { onChange.run(); }
        catch (RuntimeException error) { plugin.getLogger().log(Level.SEVERE, "Authored items were saved, but a catalog consumer failed to refresh. Restart before further content editing.", error); }
    }
    private static String title(String id) {
        StringBuilder title = new StringBuilder();
        for (String word : id.replace('-', ' ').replace('_', ' ').split(" ")) {
            if (word.isEmpty()) continue; if (!title.isEmpty()) title.append(' ');
            title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return title.toString();
    }
}
