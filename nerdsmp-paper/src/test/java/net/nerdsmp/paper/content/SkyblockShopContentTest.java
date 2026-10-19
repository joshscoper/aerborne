package net.nerdsmp.paper.content;

import net.nerdsmp.paper.npc.NpcContent;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class SkyblockShopContentTest {
    private static final Set<Material> BUY_ONLY_UTILITIES = Set.of(
        Material.BRUSH, Material.LEAD, Material.SPYGLASS, Material.COMPASS, Material.BUCKET,
        Material.FIREWORK_ROCKET, Material.SCAFFOLDING, Material.CAMPFIRE, Material.LADDER,
        Material.ARROW, Material.TORCH, Material.GLASS_BOTTLE, Material.BOOKSHELF
    );

    private static final Map<String, String> MERCHANTS = Map.of(
        "mara_venn", "warden_supplies", "elian_quill", "scholar_supplies",
        "bram_tallow", "delver_supplies", "alba_meadow", "farmstead_supplies",
        "rowan_ashwood", "timber_yard", "dain_slate", "quarry_supplies",
        "petra_mason", "masons_stock", "ivo_reed", "fishery_supplies",
        "sella_mire", "apothecary_supplies", "hob_copper", "workshop_parts"
    );

    @Test
    void eachMerchantHasAReachableDistinctShopBranch() throws Exception {
        YamlConfiguration yaml = content();
        assertEquals(10, required(yaml, "shops").getKeys(false).size());
        assertEquals(31, required(yaml, "npcs").getKeys(false).size());
        for (var entry : MERCHANTS.entrySet()) {
            ConfigurationSection npc = required(yaml, "npcs." + entry.getKey());
            assertEquals(entry.getValue(), npc.getString("shop"));
            assertTrue(reachableShops(yaml, npc.getString("root-dialogue")).contains(entry.getValue()),
                entry.getKey() + " cannot open its shop through dialogue");
            assertFalse(required(yaml, "shops." + entry.getValue()).getString("description", "").isBlank());
        }
        for (String id : required(yaml, "dialogues").getKeys(false)) {
            ConfigurationSection choices = yaml.getConfigurationSection("dialogues." + id + ".choices");
            if (choices == null) continue;
            assertTrue(choices.getKeys(false).size() <= 5, id);
            Set<String> labels = new HashSet<>();
            for (String choice : choices.getKeys(false)) {
                assertTrue(labels.add(choices.getString(choice + ".text", "").strip().toLowerCase(Locale.ROOT)),
                    "Repeated dialogue response in " + id);
            }
        }
    }

    @Test
    void ordinaryStockHasValidAmountsAndSafeQuotaLimitedBuybacks() throws Exception {
        YamlConfiguration yaml = content();
        int products = 0;
        for (String shop : required(yaml, "shops").getKeys(false)) {
            ConfigurationSection stock = required(yaml, "shops." + shop + ".products");
            assertTrue(stock.getKeys(false).size() <= 126);
            for (String id : stock.getKeys(false)) {
                ConfigurationSection product = required(stock, id);
                Material material = Objects.requireNonNull(Material.matchMaterial(product.getString("material", "")), id);
                assertFalse(Set.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR).contains(material), id);
                assertFalse(product.contains("custom-item"), id);
                int amount = product.getInt("amount");
                long ask = product.getLong("buy-price"), bid = product.getLong("sell-price");
                int limit = product.getInt("daily-sell-limit");
                // Actual Paper stack limits are verified by the disposable server fixture.
                assertTrue(amount >= 1 && amount <= 64, id);
                assertTrue(ask > 0 && bid >= 0 && bid < ask, id);
                assertTrue(bid == 0 ? limit == 0 : limit > 0 && limit <= 100, id);
                assertFalse(product.getString("description", "").isBlank(), id);
                assertDoesNotThrow(() -> new NpcContent.ShopProduct(id, material, null, amount, ask, bid, limit,
                    product.getInt("minimum-reputation"), product.getString("description")), id);
                if (equipment(material)) {
                    assertEquals(1, amount, id);
                    assertEquals(0, bid, "Vendor must not buy improved equipment: " + id);
                }
                if (BUY_ONLY_UTILITIES.contains(material) || material.name().endsWith("_BED")) {
                    assertEquals(0, bid, "Utility conversion or container byproduct must not earn Marks: " + id);
                    assertEquals(0, limit, id);
                }
                products++;
            }
        }
        assertEquals(226, products);
        assertTrue(required(yaml, "shops.farmstead_supplies.products").getKeys(false).size() > 21);
        assertTrue(required(yaml, "shops.masons_stock.products").getKeys(false).size() > 21);
    }

    @Test
    void duplicateCommoditiesHaveConsistentBaseUnitPrices() throws Exception {
        YamlConfiguration yaml = content();
        Map<Material, Price> prices = new HashMap<>();
        for (String shop : required(yaml, "shops").getKeys(false)) {
            ConfigurationSection stock = required(yaml, "shops." + shop + ".products");
            for (String id : stock.getKeys(false)) {
                ConfigurationSection p = required(stock, id);
                Material material = Material.matchMaterial(p.getString("material", ""));
                Price current = new Price(p.getInt("amount"), p.getLong("buy-price"), p.getLong("sell-price"));
                Price first = prices.putIfAbsent(material, current);
                if (first != null) {
                    assertEquals(first.ask * current.amount, current.ask * first.amount, material + " ask differs");
                    assertEquals(first.bid * current.amount, current.bid * first.amount, material + " bid differs");
                }
            }
        }
    }

    @Test
    void starterRecoveryAndCraftingAreAvailableWithoutReputationGrinding() throws Exception {
        YamlConfiguration yaml = content();
        for (String path : List.of(
            "farmstead_supplies.wheat_seed", "farmstead_supplies.carrot_crate",
            "farmstead_supplies.watering_bucket", "farmstead_supplies.starter_hoe",
            "timber_yard.oak_saplings", "quarry_supplies.quarry_pick",
            "quarry_supplies.lava_supply", "quarry_supplies.lapis_stock",
            "quarry_supplies.amethyst_stock", "fishery_supplies.spare_rod",
            "warden_supplies.camp_sword", "warden_supplies.leather_tunic"
        )) {
            String[] parts = path.split("\\.");
            ConfigurationSection p = required(yaml, "shops." + parts[0] + ".products." + parts[1]);
            assertTrue(p.getInt("minimum-reputation") <= 0, path);
            assertTrue(p.getLong("buy-price") <= 250, path + " is unaffordable with starter Marks");
        }
        Set<Material> prohibited = Set.of(Material.ECHO_SHARD, Material.NETHER_STAR, Material.DRAGON_EGG,
            Material.ELYTRA, Material.NETHERITE_INGOT, Material.ENCHANTED_BOOK, Material.SPAWNER);
        for (String shop : required(yaml, "shops").getKeys(false)) {
            ConfigurationSection stock = required(yaml, "shops." + shop + ".products");
            for (String id : stock.getKeys(false)) {
                assertFalse(prohibited.contains(Material.matchMaterial(stock.getString(id + ".material", ""))), id);
            }
        }
    }

    private static Set<String> reachableShops(YamlConfiguration yaml, String root) {
        Set<String> visited = new HashSet<>(), shops = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(Objects.requireNonNull(root));
        while (!pending.isEmpty()) {
            String node = pending.removeFirst();
            if (!visited.add(node)) continue;
            ConfigurationSection choices = required(yaml, "dialogues." + node + ".choices");
            for (String choice : choices.getKeys(false)) {
                String next = choices.getString(choice + ".next");
                if (next != null) pending.addLast(next);
                for (Map<?, ?> effect : choices.getMapList(choice + ".effects")) {
                    if ("open_shop".equals(effect.get("type"))) shops.add(String.valueOf(effect.get("shop")));
                }
            }
        }
        return shops;
    }

    private static boolean equipment(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE")
            || name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.endsWith("_HELMET")
            || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
            || material == Material.BOW || material == Material.SHIELD || material == Material.FISHING_ROD;
    }

    private static ConfigurationSection required(ConfigurationSection root, String path) {
        return Objects.requireNonNull(root.getConfigurationSection(path), path);
    }

    private static YamlConfiguration content() throws Exception {
        try (var stream = Objects.requireNonNull(SkyblockShopContentTest.class.getResourceAsStream("/content/npcs.yml"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(reader);
            return yaml;
        }
    }

    private record Price(int amount, long ask, long bid) { }
}
