package net.nerdsmp.paper.studio.npc;

import net.nerdsmp.api.model.FactionId;
import net.nerdsmp.paper.npc.NpcContent;
import org.bukkit.entity.EntityType;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static net.nerdsmp.paper.studio.npc.NpcBlueprint.*;

/** Converts the bounded local graph to the existing runtime without exposing unsafe dialogue effects. */
public final class NpcBlueprints {
    private NpcBlueprints() { }
    public static NpcBlueprint draft(String id, String faction) {
        return new NpcBlueprint(id, readable(id), "", "VILLAGER", faction, "greeting", "", 8,
            Map.of("greeting", new Node("greeting", List.of("Welcome. How can I help?"), List.of(new Choice("goodbye", "I'll be going.", "", Service.CLOSE, 1, -1000)))));
    }
    public static NpcContent merge(NpcContent base, List<NpcBlueprint> blueprints) {
        Map<String, NpcContent.NpcDefinition> npcs = new LinkedHashMap<>(base.npcs());
        Map<String, NpcContent.DialogueNode> dialogues = new LinkedHashMap<>(base.dialogues());
        for (NpcBlueprint npc : blueprints) {
            validate(npc, base); npc.validatePublish();
            if (npcs.containsKey(npc.id())) throw new IllegalArgumentException("That NPC ID belongs to shipped content. Copy it to a new ID.");
            String root = dialogueId(npc.id(), npc.root());
            npcs.put(npc.id(), new NpcContent.NpcDefinition(npc.id(), npc.name(), npc.title(), EntityType.valueOf(npc.type()), new FactionId(npc.faction()), root, npc.shop().isEmpty() ? null : npc.shop(), npc.lookRadius()));
            for (Node node : npc.nodes().values()) {
                String id = dialogueId(npc.id(), node.id());
                if (dialogues.containsKey(id)) throw new IllegalArgumentException("Authored dialogue collides with an existing node: " + id);
                List<NpcContent.DialogueChoice> choices = new ArrayList<>();
                for (Choice choice : node.choices()) {
                    List<NpcContent.DialogueEffect> effects = choice.service() == Service.NONE || choice.service() == Service.CLOSE ? List.of()
                        : List.of(new NpcContent.DialogueEffect(effect(choice.service()), null, 0, null, false,
                            choice.service() == Service.SHOP ? npc.shop() : null, null));
                    choices.add(new NpcContent.DialogueChoice(choice.id(), choice.text(), choice.next().isEmpty() ? null : dialogueId(npc.id(), choice.next()),
                        choice.service() != Service.NONE, false, Duration.ZERO,
                        new NpcContent.Requirements(choice.minimumReputation(), 1000, 0, choice.minimumLevel(), java.util.Set.of(), java.util.Set.of()), effects));
                }
                dialogues.put(id, new NpcContent.DialogueNode(id, node.text(), choices));
            }
        }
        return new NpcContent(base.factions(), npcs, dialogues, base.shops());
    }
    public static void validate(NpcBlueprint npc, NpcContent base) {
        if (!base.factions().containsKey(new FactionId(npc.faction()))) throw new IllegalArgumentException("Unknown faction: " + npc.faction());
        if (!npc.shop().isEmpty() && !base.shops().containsKey(npc.shop())) throw new IllegalArgumentException("Unknown shop: " + npc.shop());
        if (base.npcs().containsKey(npc.id())) throw new IllegalArgumentException("Shipped NPC IDs are reserved. Copy to a new ID.");
    }
    private static NpcContent.EffectType effect(Service service) { return NpcContent.EffectType.valueOf("OPEN_" + service.name()); }
    public static NpcBlueprint copy(String id, NpcBlueprint source) {
        return new NpcBlueprint(id, source.name(), source.title(), source.type(), source.faction(), source.root(), source.shop(), source.lookRadius(), source.nodes());
    }
    /** Copies safe navigation/service options; reward, flag and command choices are deliberately not imported. */
    public static NpcBlueprint copy(String id, NpcContent content, String sourceId) {
        NpcContent.NpcDefinition source = content.npcs().get(sourceId);
        if (source == null) throw new IllegalArgumentException("Unknown NPC: " + sourceId);
        if (!TYPES.contains(source.entityType().name())) throw new IllegalArgumentException("This NPC body is not supported by the editor. Create a fresh template instead.");
        Map<String, String> localIds = new HashMap<>(); Map<String, Node> nodes = new LinkedHashMap<>();
        ArrayDeque<String> pending = new ArrayDeque<>(); pending.add(source.rootDialogue()); localIds.put(source.rootDialogue(), "greeting");
        while (!pending.isEmpty()) {
            String original = pending.remove(); if (nodes.containsKey(localIds.get(original))) continue;
            NpcContent.DialogueNode node = content.dialogues().get(original);
            if (node == null) throw new IllegalArgumentException("The source dialogue graph is incomplete.");
            List<Choice> choices = new ArrayList<>(); int index = 0;
            for (NpcContent.DialogueChoice choice : node.choices()) {
                Service service = safeService(choice);
                if (service == null || choice.once() || !choice.requirements().requiredFlags().isEmpty() || !choice.requirements().absentFlags().isEmpty() || choice.requirements().minimumMarks() != 0) continue;
                String next = "";
                if (choice.nextNode() != null) {
                    if (!localIds.containsKey(choice.nextNode())) { if (localIds.size() >= MAX_NODES) throw new IllegalArgumentException("The source graph exceeds 32 nodes."); localIds.put(choice.nextNode(), "node_" + localIds.size()); }
                    next = localIds.get(choice.nextNode()); pending.add(choice.nextNode());
                }
                if (service == Service.SHOP && (source.shopId() == null || !choice.effects().getFirst().shopId().equals(source.shopId()))) continue;
                choices.add(new Choice("response_" + index++, literal(choice.text(), 96), next, service, Math.clamp(choice.requirements().minimumAdventurerLevel(), 1, 100), choice.requirements().minimumReputation()));
            }
            if (choices.isEmpty()) choices.add(new Choice("goodbye", "I'll be going.", "", Service.CLOSE, 1, -1000));
            String local = localIds.get(original);
            nodes.put(local, new Node(local, node.text().stream().limit(8).map(text -> literal(text, 320)).toList(), choices));
        }
        return new NpcBlueprint(id, literal(source.name(), 64), literal(source.title(), 96), source.entityType().name(), source.factionId().value(), "greeting", source.shopId() == null ? "" : source.shopId(), source.lookRadius(), nodes);
    }
    private static Service safeService(NpcContent.DialogueChoice choice) {
        if (choice.effects().isEmpty()) return choice.nextNode() == null ? Service.CLOSE : Service.NONE;
        if (choice.effects().size() != 1 || choice.nextNode() != null) return null;
        try { return Service.valueOf(choice.effects().getFirst().type().name().replace("OPEN_", "")); }
        catch (IllegalArgumentException error) { return null; }
    }
    private static String literal(String text, int max) {
        String value = text.replace('\n', ' ').replace('\r', ' ').strip();
        if (value.length() <= max) return value;
        int end = Character.isHighSurrogate(value.charAt(max - 1)) ? max - 1 : max;
        return value.substring(0, end);
    }
    public static String readable(String id) { return java.util.Arrays.stream(id.replace('-', '_').split("_")).filter(word -> !word.isEmpty()).map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1)).collect(java.util.stream.Collectors.joining(" ")); }
}
