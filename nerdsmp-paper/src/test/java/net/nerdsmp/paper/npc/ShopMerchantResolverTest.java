package net.nerdsmp.paper.npc;

import net.nerdsmp.api.model.FactionId;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class ShopMerchantResolverTest {
    private static final FactionId WARDENS = new FactionId("wardens");
    private static final FactionId SCHOLARS = new FactionId("scholars");

    @Test void shippedRosterResolvesEveryShopWithoutPlacedInstances() throws Exception {
        var content = shippedDefinitions();
        assertEquals(31, content.npcs().size());
        assertEquals(10, content.shops().size());
        for (var shop : content.shops().values()) {
            var merchant = ShopMerchantResolver.resolve(content, shop.id());
            assertNotNull(merchant, shop.id());
            assertEquals(shop.id(), merchant.shopId());
            assertSame(content.npcs().get(merchant.id()), merchant);
            assertTrue(content.factions().containsKey(merchant.factionId()));
            assertTrue(ShopMerchantResolver.availableShop(content, merchant, shop));
        }
    }

    @Test void directMerchantWinsOverAlphabeticallyEarlierDialogueMerchant() {
        var indirect = npc("a_indirect", SCHOLARS, "root", null);
        var direct = npc("z_direct", WARDENS, "plain", "supplies");
        var content = content(Map.of(indirect.id(), indirect, direct.id(), direct),
            Map.of("root", node("root", choice("wares", null, "supplies")), "plain", node("plain")),
            Map.of("supplies", shop("supplies")));
        assertSame(direct, ShopMerchantResolver.resolve(content, "supplies"));
        assertEquals(WARDENS, ShopMerchantResolver.resolve(content, "supplies").factionId());
    }

    @Test void directResolutionIsStableAcrossMapOrderAndKeepsSelectedFaction() {
        var first = npc("a_merchant", WARDENS, "root", "supplies");
        var second = npc("z_merchant", SCHOLARS, "root", "supplies");
        var npcs = new LinkedHashMap<String, NpcContent.NpcDefinition>();
        npcs.put(second.id(), second);
        npcs.put(first.id(), first);
        var content = content(npcs, Map.of("root", node("root")), Map.of("supplies", shop("supplies")));
        assertSame(first, ShopMerchantResolver.resolve(content, "supplies"));
        assertEquals(WARDENS, ShopMerchantResolver.resolve(content, "supplies").factionId());
        npcs.clear();
        npcs.put(first.id(), first);
        npcs.put(second.id(), second);
        assertSame(first, ShopMerchantResolver.resolve(content(npcs, content.dialogues(), content.shops()), "supplies"));
    }

    @Test void reachableDialogueFallbackStopsAtCyclesAndSelectsStableDefinition() {
        var first = npc("a_merchant", WARDENS, "root", null);
        var second = npc("z_merchant", SCHOLARS, "root", null);
        var content = content(Map.of(first.id(), first, second.id(), second), Map.of(
            "root", node("root", choice("details", "branch", null)),
            "branch", node("branch", choice("wares", "root", "supplies"))), Map.of("supplies", shop("supplies")));
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertSame(first, ShopMerchantResolver.resolve(content, "supplies"));
            assertTrue(ShopMerchantResolver.availableShop(content, first, content.shops().get("supplies")));
        });
    }

    @Test void unrelatedDialogueCannotGrantAnotherMerchantsStock() {
        var guide = npc("guide", WARDENS, "plain", null);
        var merchant = npc("merchant", SCHOLARS, "wares", null);
        var content = content(Map.of(guide.id(), guide, merchant.id(), merchant), Map.of(
            "plain", node("plain"), "wares", node("wares", choice("stock", null, "supplies"))),
            Map.of("supplies", shop("supplies")));
        assertSame(merchant, ShopMerchantResolver.resolve(content, "supplies"));
        assertFalse(ShopMerchantResolver.availableShop(content, guide, content.shops().get("supplies")));
    }

    @Test void unknownShopAndMissingMerchantFailClosed() {
        var guide = npc("guide", WARDENS, "root", null);
        var content = content(Map.of(guide.id(), guide), Map.of("root", node("root")),
            Map.of("supplies", shop("supplies")));
        assertNull(ShopMerchantResolver.resolve(content, "unknown"));
        assertNull(ShopMerchantResolver.resolve(content, "supplies"));
        assertNull(ShopMerchantResolver.resolve(content, null));
        assertNull(ShopMerchantResolver.resolve(null, "supplies"));
    }

    @Test void copiedOrReloadedMerchantAndShopObjectsAreRejectedEvenWhenEqual() {
        var merchant = npc("merchant", WARDENS, "root", "supplies");
        var shop = shop("supplies");
        var content = content(Map.of(merchant.id(), merchant), Map.of("root", node("root")), Map.of(shop.id(), shop));
        var copiedMerchant = npc("merchant", WARDENS, "root", "supplies");
        var copiedShop = shop("supplies");
        assertEquals(merchant, copiedMerchant);
        assertEquals(shop, copiedShop);
        assertTrue(ShopMerchantResolver.availableShop(content, merchant, shop));
        assertFalse(ShopMerchantResolver.availableShop(content, copiedMerchant, shop));
        assertFalse(ShopMerchantResolver.availableShop(content, merchant, copiedShop));
        var reloaded = content(Map.of(merchant.id(), copiedMerchant), content.dialogues(), Map.of(shop.id(), copiedShop));
        assertFalse(ShopMerchantResolver.availableShop(reloaded, merchant, shop));
        assertTrue(ShopMerchantResolver.availableShop(reloaded, copiedMerchant, copiedShop));
    }

    @Test void nullCapturedValuesAndUnknownFactionFailClosed() {
        var merchant = npc("merchant", WARDENS, "root", "supplies");
        var shop = shop("supplies");
        var content = content(Map.of(merchant.id(), merchant), Map.of("root", node("root")), Map.of(shop.id(), shop));
        assertFalse(ShopMerchantResolver.availableShop(null, merchant, shop));
        assertFalse(ShopMerchantResolver.availableShop(content, null, shop));
        assertFalse(ShopMerchantResolver.availableShop(content, merchant, null));
        var invalid = new NpcContent(Map.of(), content.npcs(), content.dialogues(), content.shops());
        assertFalse(ShopMerchantResolver.availableShop(invalid, merchant, shop));
        assertNull(ShopMerchantResolver.resolve(invalid, "supplies"));
    }

    private NpcContent shippedDefinitions() throws Exception {
        YamlConfiguration yaml;
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/content/npcs.yml"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            yaml = YamlConfiguration.loadConfiguration(reader);
        }
        Map<FactionId, NpcContent.FactionDefinition> factions = new LinkedHashMap<>();
        var factionSection = Objects.requireNonNull(yaml.getConfigurationSection("factions"));
        for (String id : factionSection.getKeys(false)) {
            var factionId = new FactionId(id);
            factions.put(factionId, faction(factionId));
        }
        Map<String, NpcContent.NpcDefinition> npcs = new LinkedHashMap<>();
        var npcSection = Objects.requireNonNull(yaml.getConfigurationSection("npcs"));
        for (String id : npcSection.getKeys(false)) {
            var section = Objects.requireNonNull(npcSection.getConfigurationSection(id));
            npcs.put(id, new NpcContent.NpcDefinition(id, section.getString("name"), section.getString("title"),
                EntityType.valueOf(section.getString("entity")), new FactionId(section.getString("faction")),
                section.getString("root-dialogue"), section.getString("shop"), section.getDouble("look-radius")));
        }
        Map<String, NpcContent.ShopDefinition> shops = new LinkedHashMap<>();
        var shopSection = Objects.requireNonNull(yaml.getConfigurationSection("shops"));
        for (String id : shopSection.getKeys(false)) shops.put(id, shop(id));
        return new NpcContent(factions, npcs, Map.of(), shops);
    }

    private NpcContent content(Map<String, NpcContent.NpcDefinition> npcs,
                               Map<String, NpcContent.DialogueNode> dialogues,
                               Map<String, NpcContent.ShopDefinition> shops) {
        return new NpcContent(Map.of(WARDENS, faction(WARDENS), SCHOLARS, faction(SCHOLARS)), npcs, dialogues, shops);
    }

    private NpcContent.FactionDefinition faction(FactionId id) {
        return new NpcContent.FactionDefinition(id, id.value(), "", List.of(
            new NpcContent.TierDefinition("neutral", "Neutral", "gray", -1000, 1, true)));
    }

    private NpcContent.NpcDefinition npc(String id, FactionId faction, String root, String shop) {
        return new NpcContent.NpcDefinition(id, id, "Merchant", EntityType.VILLAGER, faction, root, shop, 8);
    }

    private NpcContent.ShopDefinition shop(String id) {
        return new NpcContent.ShopDefinition(id, "Supplies", List.of(
            new NpcContent.ShopProduct("bread", Material.BREAD, null, 1, 10, 0, 0, -1000, "")));
    }

    private NpcContent.DialogueNode node(String id, NpcContent.DialogueChoice... choices) {
        return new NpcContent.DialogueNode(id, List.of("Welcome."), List.of(choices));
    }

    private NpcContent.DialogueChoice choice(String id, String next, String shop) {
        return new NpcContent.DialogueChoice(id, id, next, false, false, Duration.ZERO,
            NpcContent.Requirements.none(), shop == null ? List.of() : List.of(
                new NpcContent.DialogueEffect(NpcContent.EffectType.OPEN_SHOP, null, 0, null, false, shop, null)));
    }
}
