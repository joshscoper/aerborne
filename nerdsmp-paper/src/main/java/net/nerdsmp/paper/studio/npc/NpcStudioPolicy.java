package net.nerdsmp.paper.studio.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import static net.nerdsmp.paper.studio.npc.NpcBlueprint.*;

/** Immutable graph edits shared by commands, previews and the in-game studio. */
public final class NpcStudioPolicy {
    private NpcStudioPolicy() { }
    public static NpcBlueprint details(NpcBlueprint npc, String name, String title, String type, String faction, String shop, double radius) {
        return new NpcBlueprint(npc.id(), name, title, type, faction, npc.root(), shop, radius, npc.nodes());
    }
    public static NpcBlueprint root(NpcBlueprint npc, String root) { return new NpcBlueprint(npc.id(), npc.name(), npc.title(), npc.type(), npc.faction(), root, npc.shop(), npc.lookRadius(), npc.nodes()); }
    public static NpcBlueprint node(NpcBlueprint npc, Node node, boolean replace) {
        boolean present = npc.nodes().containsKey(node.id());
        if (present != replace) throw new IllegalArgumentException(present ? "That dialogue node already exists." : "That dialogue node no longer exists.");
        var nodes = new TreeMap<>(npc.nodes()); nodes.put(node.id(), node);
        return new NpcBlueprint(npc.id(), npc.name(), npc.title(), npc.type(), npc.faction(), npc.root(), npc.shop(), npc.lookRadius(), nodes);
    }
    public static NpcBlueprint removeNode(NpcBlueprint npc, String id) {
        if (npc.root().equals(id)) throw new IllegalArgumentException("Choose another root before removing this node.");
        if (npc.nodes().values().stream().flatMap(node -> node.choices().stream()).anyMatch(choice -> choice.next().equals(id))) throw new IllegalArgumentException("Remove or redirect responses that lead to this node first.");
        var nodes = new TreeMap<>(npc.nodes()); if (nodes.remove(id) == null) throw new IllegalArgumentException("That node no longer exists.");
        return new NpcBlueprint(npc.id(), npc.name(), npc.title(), npc.type(), npc.faction(), npc.root(), npc.shop(), npc.lookRadius(), nodes);
    }
    public static Node text(Node node, String input) { return new Node(node.id(), java.util.Arrays.stream(input.split("\\|", -1)).map(String::strip).toList(), node.choices()); }
    public static Node choice(Node node, Choice choice, boolean replace) {
        var choices = new ArrayList<>(node.choices()); int index = -1;
        for (int i = 0; i < choices.size(); i++) if (choices.get(i).id().equals(choice.id())) index = i;
        if (replace && index < 0 || !replace && index >= 0) throw new IllegalArgumentException("That response ID is missing or already used.");
        if (replace) choices.set(index, choice); else choices.add(choice);
        return new Node(node.id(), node.text(), choices);
    }
    public static Node removeChoice(Node node, String id) {
        var choices = new ArrayList<>(node.choices()); if (!choices.removeIf(choice -> choice.id().equals(id))) throw new IllegalArgumentException("That response no longer exists.");
        return new Node(node.id(), node.text(), choices);
    }
    public static Choice label(Choice choice, String label) { return new Choice(choice.id(), label, choice.next(), choice.service(), choice.minimumLevel(), choice.minimumReputation()); }
    public static Choice target(Choice choice, String next, Service service) { return new Choice(choice.id(), choice.text(), next, service, choice.minimumLevel(), choice.minimumReputation()); }
    public static Choice requirements(Choice choice, String input) {
        String[] parts = input.strip().split("\\s+");
        if (parts.length != 2) throw new IllegalArgumentException("Enter minimum level and reputation, such as 10 100.");
        try { return new Choice(choice.id(), choice.text(), choice.next(), choice.service(), Integer.parseInt(parts[0]), Integer.parseInt(parts[1])); }
        catch (NumberFormatException failure) { throw new IllegalArgumentException("Level and reputation must be whole numbers."); }
    }
    public static List<String> nodeIds(NpcBlueprint npc) { return npc.nodes().keySet().stream().sorted().toList(); }
}
