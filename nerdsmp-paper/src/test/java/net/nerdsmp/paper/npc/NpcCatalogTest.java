package net.nerdsmp.paper.npc;

import net.nerdsmp.api.model.FactionId;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class NpcCatalogTest {
    private static final FactionId FACTION = new FactionId("wardens");

    @Test
    void findsShopsInNestedDialogueAndStopsAtCycles() {
        NpcContent content = content(Map.of("merchant", npc("merchant", "Mara", "Quartermaster", "root", "supplies")),
            Map.of("root", node("root", choice("details", "branch", List.of())),
                "branch", node("branch", choice("back", "root", List.of(effect(NpcContent.EffectType.OPEN_SHOP, "supplies", null))))),
            Map.of("supplies", shop("supplies", "Field Supplies", Material.BREAD)));
        NpcCatalog.Entry entry = new NpcCatalog(content).find("merchant");
        assertTrue(entry.categories().contains(NpcCatalog.Category.SHOPKEEPERS));
        assertEquals(1, entry.shops().size());
        assertEquals("supplies", entry.shops().getFirst().id());
    }

    @Test
    void classifiesTheReachableServicesWithoutLeakingOtherNpcsActions() {
        var npcs = Map.of("banker", npc("banker", "Orin", "Banker", "bank", null),
            "quest", npc("quest", "Cato", "Contract Clerk", "quest", null));
        var dialogues = Map.of("bank", node("bank", choice("bank", null,
                List.of(effect(NpcContent.EffectType.OPEN_BANK, null, null)))),
            "quest", node("quest", choice("quest", null,
                List.of(effect(NpcContent.EffectType.OPEN_CONTRACTS, null, null)))));
        NpcCatalog catalog = new NpcCatalog(content(npcs, dialogues, Map.of()));
        assertEquals(List.of("banker"), ids(catalog.page(NpcCatalog.Category.SERVICES, "", 0, 21)));
        assertEquals(List.of("quest"), ids(catalog.page(NpcCatalog.Category.QUESTS, "", 0, 21)));
        assertFalse(catalog.find("banker").categories().contains(NpcCatalog.Category.QUESTS));
    }

    @Test
    void searchFindsStockFactionAndServicesUsingAllTerms() {
        var npc = npc("field_vendor", "Mara Venn", "Quartermaster", "root", "supplies");
        var dialogues = Map.of("root", node("root", choice("forge", null,
            List.of(effect(NpcContent.EffectType.OPEN_FORGE, null, null)))));
        NpcCatalog catalog = new NpcCatalog(content(Map.of(npc.id(), npc), dialogues,
            Map.of("supplies", shop("supplies", "Field Supplies", Material.BREAD))));
        assertEquals(List.of("field_vendor"), ids(catalog.page(NpcCatalog.Category.ALL, "Warden BREAD", 0, 21)));
        assertEquals(List.of("field_vendor"), ids(catalog.page(NpcCatalog.Category.SERVICES, "Forge gems", 0, 21)));
        assertEquals(List.of("field_vendor"), ids(catalog.page(NpcCatalog.Category.ALL, "FIELD_VENDOR", 0, 21)));
        assertTrue(catalog.page(NpcCatalog.Category.ALL, "bank bread", 0, 21).entries().isEmpty());
    }

    @Test
    void commandDrivenQuestAndServiceRolesAreDiscoverable() {
        var npc = npc("herald", "Arlen", "Herald", "root", null);
        var dialogues = Map.of("root", node("root", choice("campaign", null,
            List.of(effect(NpcContent.EffectType.OPEN_COMMAND, null, "campaign"))),
            choice("travel", null, List.of(effect(NpcContent.EffectType.OPEN_COMMAND, null, "warps")))));
        NpcCatalog.Entry entry = new NpcCatalog(content(Map.of(npc.id(), npc), dialogues, Map.of())).find("herald");
        assertTrue(entry.categories().containsAll(Set.of(NpcCatalog.Category.QUESTS, NpcCatalog.Category.SERVICES)));
        assertEquals(List.of("Campaigns", "Travel destinations"), entry.services());
    }

    @Test
    void paginationHasNoDuplicatesAndClampsStalePageNumbers() {
        Map<String, NpcContent.NpcDefinition> npcs = new LinkedHashMap<>();
        for (int index = 0; index < 44; index++) {
            String id = "vendor_" + String.format("%02d", index);
            npcs.put(id, npc(id, id, "Merchant", "root", null));
        }
        NpcCatalog catalog = new NpcCatalog(content(npcs, Map.of("root", node("root")), Map.of()));
        var first = catalog.page(NpcCatalog.Category.ALL, "", 0, 21);
        var second = catalog.page(NpcCatalog.Category.ALL, "", 1, 21);
        var last = catalog.page(NpcCatalog.Category.ALL, "", 999, 21);
        assertEquals(44, first.matches());
        assertEquals(3, first.pages());
        assertEquals(21, second.entries().size());
        assertEquals(2, last.entries().size());
        assertEquals(2, last.number());
        assertTrue(first.entries().stream().noneMatch(second.entries()::contains));
        assertEquals(0, catalog.page(NpcCatalog.Category.ALL, "", -4, 21).number());
    }

    @Test
    void emptySearchResultStillHasOnePageAndImmutableEntries() {
        var npc = npc("guide", "Guide", "Guide", "root", null);
        NpcCatalog catalog = new NpcCatalog(content(Map.of(npc.id(), npc), Map.of("root", node("root")), Map.of()));
        var empty = catalog.page(NpcCatalog.Category.ALL, "missing", 10, 21);
        assertEquals(1, empty.pages());
        assertEquals(0, empty.number());
        assertThrows(UnsupportedOperationException.class, () -> catalog.entries().clear());
        assertThrows(IllegalArgumentException.class, () -> catalog.page(NpcCatalog.Category.ALL, "", 0, 0));
        assertNull(NpcCatalog.Category.parse("invalid"));
    }

    private List<String> ids(NpcCatalog.Page page) {
        return page.entries().stream().map(entry -> entry.definition().id()).toList();
    }

    private NpcContent content(Map<String, NpcContent.NpcDefinition> npcs,
                               Map<String, NpcContent.DialogueNode> dialogues,
                               Map<String, NpcContent.ShopDefinition> shops) {
        var faction = new NpcContent.FactionDefinition(FACTION, "Warden Guild", "", List.of(
            new NpcContent.TierDefinition("neutral", "Neutral", "gray", -1000, 1, true)));
        return new NpcContent(Map.of(FACTION, faction), npcs, dialogues, shops);
    }

    private NpcContent.NpcDefinition npc(String id, String name, String title, String root, String shop) {
        return new NpcContent.NpcDefinition(id, name, title, EntityType.VILLAGER, FACTION, root, shop, 8);
    }

    private NpcContent.DialogueNode node(String id, NpcContent.DialogueChoice... choices) {
        return new NpcContent.DialogueNode(id, List.of("Welcome."), List.of(choices));
    }

    private NpcContent.DialogueChoice choice(String id, String next, List<NpcContent.DialogueEffect> effects) {
        return new NpcContent.DialogueChoice(id, id, next, false, false, Duration.ZERO,
            NpcContent.Requirements.none(), effects);
    }

    private NpcContent.DialogueEffect effect(NpcContent.EffectType type, String shop, String command) {
        return new NpcContent.DialogueEffect(type, null, 0, null, false, shop, command);
    }

    private NpcContent.ShopDefinition shop(String id, String title, Material material) {
        return new NpcContent.ShopDefinition(id, title, "Everyday field supplies.", List.of(
            new NpcContent.ShopProduct("bread", material, null, 1, 10, 0, 0, -1000, "")));
    }
}
