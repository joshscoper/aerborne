package net.nerdsmp.paper.npc;

import org.bukkit.Material;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** A staff view derived from the actions an NPC actually offers. */
public final class NpcCatalog {
    public enum Category {
        ALL("all", "All NPCs", Material.PLAYER_HEAD),
        SHOPKEEPERS("shopkeepers", "Shopkeepers", Material.EMERALD),
        SERVICES("services", "Services", Material.ANVIL),
        QUESTS("quests", "Quest givers", Material.WRITABLE_BOOK);

        private final String key;
        private final String title;
        private final Material icon;

        Category(String key, String title, Material icon) {
            this.key = key;
            this.title = title;
            this.icon = icon;
        }

        public String key() { return key; }
        public String title() { return title; }
        public Material icon() { return icon; }

        public static Category parse(String raw) {
            for (Category category : values()) {
                if (category.key.equalsIgnoreCase(raw)) return category;
            }
            return null;
        }
    }

    public record Entry(NpcContent.NpcDefinition definition, String faction,
                        Set<Category> categories, List<String> services,
                        List<NpcContent.ShopDefinition> shops) {
        public Entry {
            categories = Set.copyOf(categories);
            services = List.copyOf(services);
            shops = List.copyOf(shops);
        }

        public String searchText() {
            StringBuilder text = new StringBuilder(definition.id()).append(' ').append(definition.name())
                .append(' ').append(definition.title()).append(' ').append(faction);
            services.forEach(service -> text.append(' ').append(service));
            shops.forEach(shop -> {
                text.append(' ').append(shop.id()).append(' ').append(shop.title()).append(' ').append(shop.description());
                shop.products().forEach(product -> text.append(' ').append(label(product.id())).append(' ')
                    .append(product.material() == null ? product.customItem().value() : product.material().name()));
            });
            return text.toString().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        }
    }

    public record Page(List<Entry> entries, int number, int pages, int matches) {
        public Page { entries = List.copyOf(entries); }
    }

    private final List<Entry> entries;

    public NpcCatalog(NpcContent content) {
        entries = content.npcs().values().stream().map(npc -> describe(content, npc))
            .sorted(Comparator.comparing((Entry entry) -> entry.definition().title(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.definition().name(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.definition().id())).toList();
    }

    public List<Entry> entries() { return entries; }

    public Entry find(String id) {
        return entries.stream().filter(entry -> entry.definition().id().equals(id)).findFirst().orElse(null);
    }

    public Page page(Category category, String query, int requested, int size) {
        if (size < 1 || size > 45) throw new IllegalArgumentException("NPC catalog page size must be 1..45");
        String normalized = query.strip().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        List<String> terms = normalized.isBlank() ? List.of() : List.of(normalized.split("\\s+"));
        List<Entry> found = entries.stream().filter(entry -> entry.categories().contains(category))
            .filter(entry -> terms.stream().allMatch(entry.searchText()::contains)).toList();
        int pages = Math.max(1, (found.size() + size - 1) / size);
        int page = Math.clamp(requested, 0, pages - 1);
        int start = page * size;
        return new Page(found.subList(start, Math.min(start + size, found.size())), page, pages, found.size());
    }

    private static Entry describe(NpcContent content, NpcContent.NpcDefinition npc) {
        Set<Category> categories = EnumSet.of(Category.ALL);
        Set<String> services = new LinkedHashSet<>();
        Set<String> shopIds = new LinkedHashSet<>();
        if (npc.shopId() != null) shopIds.add(npc.shopId());
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(npc.rootDialogue());
        Set<String> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            if (!visited.add(id)) continue;
            NpcContent.DialogueNode node = content.dialogues().get(id);
            if (node == null) continue;
            for (NpcContent.DialogueChoice choice : node.choices()) {
                if (choice.nextNode() != null) pending.addLast(choice.nextNode());
                for (NpcContent.DialogueEffect effect : choice.effects()) {
                    if (effect.type() == NpcContent.EffectType.OPEN_SHOP) {
                        if (effect.shopId() != null) shopIds.add(effect.shopId());
                    } else if (effect.type() == NpcContent.EffectType.OPEN_CONTRACTS) {
                        categories.add(Category.QUESTS);
                        services.add("Faction contracts");
                    } else if (effect.type() == NpcContent.EffectType.OPEN_COMMAND) {
                        String command = effect.command();
                        if (command == null) continue;
                        if (Set.of("daily", "jobs", "campaign").contains(command)) categories.add(Category.QUESTS);
                        else categories.add(Category.SERVICES);
                        services.add(commandLabel(command));
                    } else {
                        String service = switch (effect.type()) {
                            case OPEN_BANK -> "Bank storage";
                            case OPEN_IDENTIFIER -> "Gear identification";
                            case OPEN_REPAIR -> "Equipment repairs";
                            case OPEN_ARCHIVE -> "Excavation collections";
                            case OPEN_FISHERY -> "Fishing collections";
                            case OPEN_ALCHEMY -> "Alchemy";
                            case OPEN_FORGE -> "Forge and gems";
                            case OPEN_ENCHANTMENTS -> "Enchanting";
                            default -> null;
                        };
                        if (service != null) {
                            services.add(service);
                            categories.add(Category.SERVICES);
                        }
                    }
                }
            }
        }
        List<NpcContent.ShopDefinition> shops = new ArrayList<>();
        for (String id : shopIds) {
            NpcContent.ShopDefinition shop = content.shops().get(id);
            if (shop != null) shops.add(shop);
        }
        if (!shops.isEmpty()) categories.add(Category.SHOPKEEPERS);
        shops.sort(Comparator.comparing(NpcContent.ShopDefinition::title, String.CASE_INSENSITIVE_ORDER));
        return new Entry(npc, content.faction(npc.factionId()).name(), categories, List.copyOf(services), shops);
    }

    private static String commandLabel(String command) {
        return switch (command) {
            case "daily" -> "Daily quests";
            case "jobs" -> "Server orders";
            case "campaign" -> "Campaigns";
            case "quickstart", "menu" -> "Getting started";
            case "warps" -> "Travel destinations";
            case "grave" -> "Grave recovery";
            case "claim" -> "Land management";
            case "market" -> "Player market";
            case "adventurer", "profile" -> "Adventurer progression";
            case "mastery" -> "Skill masteries";
            case "recipes" -> "Crafting recipes";
            default -> label(command);
        };
    }

    public static String label(String raw) {
        StringBuilder result = new StringBuilder();
        for (String word : raw.split("[_\\-\\s]+")) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.toString();
    }
}
