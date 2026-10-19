package net.nerdsmp.paper.studio.npc;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.npc.NpcContent;
import net.nerdsmp.paper.npc.NpcContentLoader;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Publishing changes dialogue definitions, never placement anchors or terrain. */
public final class NpcEngine {
    private final NerdSmpPlugin plugin;
    private final NpcBlueprintStore store;
    private NpcContent base;
    public NpcEngine(NerdSmpPlugin plugin) {
        this.plugin = plugin; base = new NpcContentLoader(plugin, plugin.items()).loadBase();
        try { store = new NpcBlueprintStore(new File(plugin.getDataFolder(), "content/engine-npcs.yml"), base); }
        catch (IOException failure) { throw new IllegalStateException("Unable to load authored NPCs; content is preserved", failure); }
    }
    public List<NpcBlueprint> all() { return store.all(); }
    public Optional<NpcBlueprint> find(String id) { return store.find(id); }
    public NpcContent baseContent() { return base; }
    public NpcBlueprint draft(String id) { return NpcBlueprints.draft(id, base.factions().keySet().stream().map(net.nerdsmp.api.model.FactionId::value).sorted().findFirst().orElseThrow()); }
    public NpcBlueprint copy(String id, String source) { return store.find(source).map(npc -> NpcBlueprints.copy(id, npc)).orElseGet(() -> NpcBlueprints.copy(id, base, source)); }
    public void validateDraft(NpcBlueprint npc) { store.validateDraft(npc); }
    public synchronized void publish(NpcBlueprint npc, NpcBlueprint expected) throws IOException {
        NpcContent candidateBase = new NpcContentLoader(plugin, plugin.items()).loadBase();
        store.base(candidateBase);
        var candidate = new TreeMap<String, NpcBlueprint>(); store.all().forEach(value -> candidate.put(value.id(), value)); candidate.put(npc.id(), npc);
        NpcContent next = NpcBlueprints.merge(candidateBase, candidate.values().stream().toList());
        plugin.npcs().validateAuthoredContent(next);
        store.put(npc, expected); base = candidateBase;
        plugin.npcs().replaceAuthoredContent(next, Set.of(npc.id()));
    }
}
