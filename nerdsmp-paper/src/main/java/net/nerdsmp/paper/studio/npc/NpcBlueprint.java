package net.nerdsmp.paper.studio.npc;

import net.nerdsmp.api.model.FactionId;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One manually placed NPC's local, plain-text dialogue graph. No authorable reward or command execution. */
public record NpcBlueprint(String id, String name, String title, String type, String faction,
                           String root, String shop, double lookRadius, Map<String, Node> nodes) {
    public static final int MAX_NPCS = 128, MAX_NODES = 32, MAX_CHOICES = 5;
    public static final Set<String> TYPES = Set.of("VILLAGER", "WANDERING_TRADER", "ALLAY", "IRON_GOLEM", "SNOW_GOLEM",
        "PIGLIN", "PIGLIN_BRUTE", "EVOKER", "WITCH", "VINDICATOR", "ILLUSIONER", "SKELETON", "ZOMBIE", "HUSK",
        "DROWNED", "STRAY", "CAT", "WOLF", "FOX", "RABBIT", "CAMEL");
    public NpcBlueprint {
        id = identifier(id, 32); name = text(name, 64, false); title = text(title, 96, true);
        type = Objects.requireNonNull(type).toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw new IllegalArgumentException("Choose a supported NPC body type.");
        faction = new FactionId(faction).value(); root = identifier(root, 24);
        shop = shop == null ? "" : shop;
        if (!shop.isEmpty()) identifier(shop, 96);
        if (!Double.isFinite(lookRadius) || lookRadius < 2 || lookRadius > 24) throw new IllegalArgumentException("Look radius must be 2..24 blocks.");
        nodes = Map.copyOf(nodes);
        if (nodes.isEmpty() || nodes.size() > MAX_NODES) throw new IllegalArgumentException("NPC dialogue needs 1..32 nodes.");
        if (!nodes.containsKey(root)) throw new IllegalArgumentException("The root dialogue node does not exist.");
        for (var pair : nodes.entrySet()) {
            if (!pair.getKey().equals(pair.getValue().id())) throw new IllegalArgumentException("Dialogue node IDs must match their keys.");
            for (Choice choice : pair.getValue().choices()) {
                if (!choice.next().isEmpty() && !nodes.containsKey(choice.next())) throw new IllegalArgumentException("Unknown next dialogue node: " + choice.next());
                if (choice.service() == Service.SHOP && shop.isEmpty()) throw new IllegalArgumentException("Select a shop before adding a shop response.");
            }
        }
        // Unreachable draft nodes are useful while authoring, but publishing rejects them separately.
    }

    public enum Service { NONE, CLOSE, SHOP, BANK, IDENTIFIER, REPAIR, CONTRACTS, ARCHIVE, FISHERY, ALCHEMY, FORGE, ENCHANTMENTS }
    public record Node(String id, List<String> text, List<Choice> choices) {
        public Node {
            id = identifier(id, 24); text = List.copyOf(text); choices = List.copyOf(choices);
            if (text.isEmpty() || text.size() > 8) throw new IllegalArgumentException("Dialogue nodes need 1..8 text lines.");
            for (String line : text) NpcBlueprint.text(line, 320, false);
            if (choices.size() > MAX_CHOICES) throw new IllegalArgumentException("A dialogue node supports at most five responses.");
            Set<String> ids = new HashSet<>(), labels = new HashSet<>();
            for (Choice choice : choices) {
                if (!ids.add(choice.id())) throw new IllegalArgumentException("Duplicate response ID: " + choice.id());
                if (!labels.add(choice.text().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Responses on one node must have different labels.");
            }
        }
    }
    public record Choice(String id, String text, String next, Service service, int minimumLevel, int minimumReputation) {
        public Choice {
            id = identifier(id, 24); text = NpcBlueprint.text(text, 96, false); next = next == null ? "" : next;
            if (!next.isEmpty()) identifier(next, 24);
            Objects.requireNonNull(service);
            if (next.isEmpty() == (service == Service.NONE)) throw new IllegalArgumentException("A response must continue to a node OR select a terminal service.");
            if (minimumLevel < 1 || minimumLevel > 100) throw new IllegalArgumentException("Response level requirement must be 1..100.");
            if (minimumReputation < -1000 || minimumReputation > 1000) throw new IllegalArgumentException("Response reputation requirement must be -1000..1000.");
        }
    }

    public static String identifier(String id, int length) {
        if (id == null || !id.matches("[a-z0-9_-]{1," + length + "}")) throw new IllegalArgumentException("Use a lowercase ID of 1.." + length + " letters, numbers, underscores or hyphens.");
        return id;
    }
    public static String text(String input, int max, boolean empty) {
        if (input == null || input.length() > max || !empty && input.isBlank()
            || input.chars().anyMatch(c -> Character.isISOControl(c))) throw new IllegalArgumentException("Enter plain text " + (empty ? "up to " : "between 1 and ") + max + " characters without control characters.");
        return input.strip();
    }
    public static String dialogueId(String npc, String node) { return "engine_" + identifier(npc, 32) + "__" + identifier(node, 24); }
    public void validatePublish() {
        Set<String> reachable = new HashSet<>(); ArrayDeque<String> pending = new ArrayDeque<>(); pending.add(root);
        while (!pending.isEmpty()) {
            String id = pending.remove(); if (!reachable.add(id)) continue;
            nodes.get(id).choices().stream().map(Choice::next).filter(next -> !next.isEmpty()).forEach(pending::add);
        }
        if (reachable.size() != nodes.size()) throw new IllegalArgumentException("Every dialogue node must be reachable from the root. Connect or remove unused nodes.");
    }
}
