package net.nerdsmp.paper.npc;

import java.util.Comparator;

/** Resolves definition-backed shops without requiring a placed NPC or changing dialogue access rules. */
final class ShopMerchantResolver {
    private ShopMerchantResolver() { }

    static NpcContent.NpcDefinition resolve(NpcContent content, String shopId) {
        if (content == null || shopId == null || !content.shops().containsKey(shopId)) return null;
        var direct = content.npcs().values().stream()
            .filter(npc -> shopId.equals(npc.shopId()) && content.factions().containsKey(npc.factionId()))
            .min(Comparator.comparing(NpcContent.NpcDefinition::id)).orElse(null);
        if (direct != null) return direct;
        try {
            return new NpcCatalog(content).entries().stream()
                .filter(entry -> entry.shops().stream().anyMatch(shop -> shopId.equals(shop.id())))
                .map(NpcCatalog.Entry::definition)
                .min(Comparator.comparing(NpcContent.NpcDefinition::id)).orElse(null);
        } catch (IllegalArgumentException invalidContent) {
            return null;
        }
    }

    static boolean availableShop(NpcContent content, NpcContent.NpcDefinition npc, NpcContent.ShopDefinition shop) {
        if (content == null || npc == null || shop == null
            || content.npcs().get(npc.id()) != npc || content.shops().get(shop.id()) != shop
            || !content.factions().containsKey(npc.factionId())) return false;
        if (shop.id().equals(npc.shopId())) return true;
        try {
            var entry = new NpcCatalog(content).find(npc.id());
            return entry != null && entry.shops().stream().anyMatch(candidate -> candidate == shop);
        } catch (IllegalArgumentException invalidContent) {
            return false;
        }
    }
}
