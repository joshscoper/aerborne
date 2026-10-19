package net.nerdsmp.paper.studio.npc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.nerdsmp.api.model.FactionId;
import net.nerdsmp.paper.npc.NpcContent;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static net.nerdsmp.paper.studio.npc.NpcBlueprint.*;
import static org.junit.jupiter.api.Assertions.*;

class NpcBlueprintTest {
    static NpcContent base() {
        FactionId faction = new FactionId("guild");
        var tiers = List.of(new NpcContent.TierDefinition("neutral", "Neutral", "gray", -1000, 1, true));
        var shop = new NpcContent.ShopDefinition("supplies", "Supplies", "Basic supplies", List.of(new NpcContent.ShopProduct("iron", Material.IRON_INGOT, null, 1, 10, 0, 0, -1000, "Iron")));
        return new NpcContent(Map.of(faction, new NpcContent.FactionDefinition(faction, "Guild", "Tradesfolk", tiers)), Map.of(), Map.of(), Map.of(shop.id(), shop));
    }
    private final NpcBlueprint npc = NpcBlueprints.draft("healer", "guild");
    @Test void freshTemplateIsBoundedSafeAndDoesNotExposeRewardGrants() {
        assertEquals("greeting", npc.root()); assertEquals("VILLAGER", npc.type()); assertEquals("", npc.shop());
        assertEquals(Service.CLOSE, npc.nodes().get("greeting").choices().getFirst().service());
        assertDoesNotThrow(npc::validatePublish);
        assertFalse(ArraysSupport.names().contains("MARKS")); assertFalse(ArraysSupport.names().contains("REPUTATION")); assertFalse(ArraysSupport.names().contains("COMMAND"));
    }
    @Test void identifiersBodiesAndTextAreValidatedWithoutPaperItemRegistry() {
        for (String id : List.of("../escape", "Upper", "a".repeat(33))) assertThrows(IllegalArgumentException.class, () -> NpcBlueprints.draft(id, "guild"));
        assertThrows(IllegalArgumentException.class, () -> NpcStudioPolicy.details(npc, "Name", "Title", "PLAYER", "guild", "", 8));
        assertThrows(IllegalArgumentException.class, () -> NpcStudioPolicy.details(npc, "Name", "Title", "WITHER", "guild", "", 8));
        assertThrows(IllegalArgumentException.class, () -> NpcStudioPolicy.details(npc, "\nsecret", "Title", "VILLAGER", "guild", "", 8));
        assertThrows(IllegalArgumentException.class, () -> NpcStudioPolicy.details(npc, " ", "Title", "VILLAGER", "guild", "", 8));
        for (double radius : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 1, 25}) assertThrows(IllegalArgumentException.class, () -> NpcStudioPolicy.details(npc, "Name", "Title", "VILLAGER", "guild", "", radius));
    }
    @Test void responsesMustHaveExactlyOneBranchOrTerminalDestination() {
        assertThrows(IllegalArgumentException.class, () -> new Choice("a", "Talk", "", Service.NONE, 1, -1000));
        assertThrows(IllegalArgumentException.class, () -> new Choice("a", "Talk", "greeting", Service.BANK, 1, -1000));
        assertDoesNotThrow(() -> new Choice("a", "Talk", "greeting", Service.NONE, 1, -1000));
        assertDoesNotThrow(() -> new Choice("a", "Talk", "", Service.CONTRACTS, 1, -1000));
    }
    @Test void responseGatesAndDuplicateLabelsAreBounded() {
        assertThrows(IllegalArgumentException.class, () -> new Choice("a", "Talk", "", Service.CLOSE, 0, -1000));
        assertThrows(IllegalArgumentException.class, () -> new Choice("a", "Talk", "", Service.CLOSE, 101, -1000));
        assertThrows(IllegalArgumentException.class, () -> new Choice("a", "Talk", "", Service.CLOSE, 1, -1001));
        var a = new Choice("a", "Talk", "", Service.CLOSE, 1, -1000);
        var b = new Choice("b", " talk ", "", Service.BANK, 1, -1000);
        assertThrows(IllegalArgumentException.class, () -> new Node("node", List.of("Welcome"), List.of(a, b)));
        assertThrows(IllegalArgumentException.class, () -> new Node("node", List.of("Welcome"), java.util.stream.IntStream.range(0, 6).mapToObj(i -> new Choice("r" + i, "Response " + i, "", Service.CLOSE, 1, -1000)).toList()));
    }
    @Test void draftsPermitDisconnectedNodesButPublishRequiresReachableGraph() {
        var other = new Node("branch", List.of("More details"), List.of(new Choice("back", "Back", "greeting", Service.NONE, 1, -1000)));
        var expanded = NpcStudioPolicy.node(npc, other, false);
        assertThrows(IllegalArgumentException.class, expanded::validatePublish);
        var greeting = expanded.nodes().get("greeting");
        var linked = NpcStudioPolicy.choice(greeting, new Choice("details", "Tell me more.", "branch", Service.NONE, 1, -1000), false);
        var complete = NpcStudioPolicy.node(expanded, linked, true);
        assertDoesNotThrow(complete::validatePublish);
        assertThrows(UnsupportedOperationException.class, () -> complete.nodes().clear());
    }
    @Test void unknownBranchesAndMissingRootFailClosed() {
        var choice = new Choice("missing", "Where?", "missing", Service.NONE, 1, -1000);
        assertThrows(IllegalArgumentException.class, () -> NpcStudioPolicy.node(npc, new Node("greeting", List.of("Welcome"), List.of(choice)), true));
        assertThrows(IllegalArgumentException.class, () -> NpcStudioPolicy.root(npc, "missing"));
        assertThrows(IllegalArgumentException.class, () -> NpcStudioPolicy.removeNode(npc, "greeting"));
    }
    @Test void shopServicesRequireRealAssignedShopAndFactionReferences() {
        var greeting = npc.nodes().get("greeting");
        var shopNode = NpcStudioPolicy.choice(greeting, new Choice("shop", "Show me your wares.", "", Service.SHOP, 1, -1000), false);
        assertThrows(IllegalArgumentException.class, () -> NpcStudioPolicy.node(npc, shopNode, true));
        var merchant = NpcStudioPolicy.details(npc, npc.name(), "Merchant", npc.type(), npc.faction(), "supplies", 8);
        assertDoesNotThrow(() -> NpcBlueprints.merge(base(), List.of(NpcStudioPolicy.node(merchant, shopNode, true))));
        assertThrows(IllegalArgumentException.class, () -> NpcBlueprints.merge(base(), List.of(NpcStudioPolicy.details(npc, npc.name(), "", npc.type(), "missing", "", 8))));
        assertThrows(IllegalArgumentException.class, () -> NpcBlueprints.merge(base(), List.of(NpcStudioPolicy.details(npc, npc.name(), "", npc.type(), "guild", "missing", 8))));
    }
    @Test void runtimeMergePreservesLiteralMarkupAndUsesOwnedNamespacedNodes() {
        var node = new Node("greeting", List.of("<red>Literal dialogue</red>"), List.of(new Choice("bank", "<bold>Bank</bold>", "", Service.BANK, 10, 100)));
        var authored = NpcStudioPolicy.node(npc, node, true);
        NpcContent runtime = NpcBlueprints.merge(base(), List.of(authored));
        assertEquals("engine_healer__greeting", runtime.npcs().get("healer").rootDialogue());
        var stored = runtime.dialogues().get("engine_healer__greeting");
        var plain = PlainTextComponentSerializer.plainText();
        assertEquals(node.text().getFirst(), stored.text().getFirst());
        assertEquals(node.choices().getFirst().text(), stored.choices().getFirst().text());
        assertEquals(node.text().getFirst(), plain.serialize(Component.text(stored.text().getFirst())));
        assertEquals(node.choices().getFirst().text(), plain.serialize(Component.text(stored.choices().getFirst().text())));
        assertEquals(NpcContent.EffectType.OPEN_BANK, stored.choices().getFirst().effects().getFirst().type());
        assertEquals(10, stored.choices().getFirst().requirements().minimumAdventurerLevel());
        assertEquals(100, stored.choices().getFirst().requirements().minimumReputation());
        assertNull(stored.choices().getFirst().effects().getFirst().command());
    }
    @Test void runtimeMergeNeverMutatesBaseOrLetsAuthoredIdsShadowBuiltins() {
        var base = base(); var runtime = NpcBlueprints.merge(base, List.of(npc));
        assertTrue(base.npcs().isEmpty()); assertEquals(1, runtime.npcs().size()); assertEquals(base.shops(), runtime.shops());
        var reserved = new NpcContent(base.factions(), runtime.npcs(), base.dialogues(), base.shops());
        assertThrows(IllegalArgumentException.class, () -> NpcBlueprints.merge(reserved, List.of(npc)));
    }
    @Test void builtinCopiesDropUnsafeEffectsAndKeepExistingServices() {
        var base = base();
        var safe = new NpcContent.DialogueChoice("bank", "Bank", null, true, false, Duration.ZERO, NpcContent.Requirements.none(), List.of(new NpcContent.DialogueEffect(NpcContent.EffectType.OPEN_BANK, null, 0, null, false, null, null)));
        var grant = new NpcContent.DialogueChoice("free", "Free money", null, true, false, Duration.ZERO, NpcContent.Requirements.none(), List.of(new NpcContent.DialogueEffect(NpcContent.EffectType.MARKS, null, 100, null, false, null, null)));
        var command = new NpcContent.DialogueChoice("command", "Open command", null, true, false, Duration.ZERO, NpcContent.Requirements.none(), List.of(new NpcContent.DialogueEffect(NpcContent.EffectType.OPEN_COMMAND, null, 0, null, false, null, "menu")));
        var definition = new NpcContent.NpcDefinition("banker", "Banker", "Treasurer", EntityType.VILLAGER, new FactionId("guild"), "bank_root", null, 8);
        var runtime = new NpcContent(base.factions(), Map.of("banker", definition), Map.of("bank_root", new NpcContent.DialogueNode("bank_root", List.of("<gold>Welcome</gold>"), List.of(safe, grant, command))), base.shops());
        var copy = NpcBlueprints.copy("new_banker", runtime, "banker");
        assertEquals(1, copy.nodes().get("greeting").choices().size());
        assertEquals(Service.BANK, copy.nodes().get("greeting").choices().getFirst().service());
        assertEquals("<gold>Welcome</gold>", copy.nodes().get("greeting").text().getFirst());
        assertDoesNotThrow(copy::validatePublish);
    }
    @Test void runtimeCopiesPreserveLiteralNamesTitlesResponsesAndDialogueWithoutAddingEscapes() {
        var node = new Node("greeting", List.of("<red>Literal words</red>", "A backslash: \\ and <3."),
            List.of(new Choice("bank", "<bold>Bank</bold>", "", Service.BANK, 10, 100)));
        var named = NpcStudioPolicy.details(npc, "<gold>Healer</gold>", "<red>Medic</red>", npc.type(), npc.faction(), "", 8);
        var authored = NpcStudioPolicy.node(named, node, true);
        var runtime = NpcBlueprints.merge(base(), List.of(authored));
        var copied = NpcBlueprints.copy("new_healer", runtime, "healer");
        assertEquals(authored.name(), copied.name());
        assertEquals(authored.title(), copied.title());
        assertEquals(node.text(), copied.nodes().get("greeting").text());
        assertEquals(node.choices().getFirst().text(), copied.nodes().get("greeting").choices().getFirst().text());
        var copiedRuntime = NpcBlueprints.merge(base(), List.of(copied));
        assertEquals(node.text(), copiedRuntime.dialogues().get("engine_new_healer__greeting").text());
        assertEquals(node.choices().getFirst().text(), copiedRuntime.dialogues().get("engine_new_healer__greeting").choices().getFirst().text());
    }
    @Test void immutableEditorControlsPreserveGraphDataAndRejectInvalidOperations() {
        Node old = npc.nodes().get("greeting");
        var changedText = NpcStudioPolicy.text(old, "Hello | How are you?");
        assertEquals(List.of("Hello", "How are you?"), changedText.text()); assertEquals(old.choices(), changedText.choices());
        assertThrows(IllegalArgumentException.class, () -> NpcStudioPolicy.text(old, "Hello||World"));
        Choice changed = NpcStudioPolicy.requirements(old.choices().getFirst(), "15 100");
        assertEquals(15, changed.minimumLevel()); assertEquals(100, changed.minimumReputation());
        assertThrows(IllegalArgumentException.class, () -> NpcStudioPolicy.requirements(changed, "one two"));
        assertThrows(IllegalArgumentException.class, () -> NpcStudioPolicy.requirements(changed, "1 2 3"));
        assertThrows(IllegalArgumentException.class, () -> NpcStudioPolicy.choice(old, changed, false));
        assertThrows(IllegalArgumentException.class, () -> NpcStudioPolicy.removeChoice(old, "missing"));
        assertTrue(NpcStudioPolicy.removeChoice(old, "goodbye").choices().isEmpty());
    }
    private static final class ArraysSupport { static Set<String> names() { return java.util.Arrays.stream(Service.values()).map(Enum::name).collect(java.util.stream.Collectors.toSet()); } }
}
