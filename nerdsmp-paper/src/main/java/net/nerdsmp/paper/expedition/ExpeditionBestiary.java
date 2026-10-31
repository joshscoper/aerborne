package net.nerdsmp.paper.expedition;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.expedition.content.Abilities;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import net.nerdsmp.paper.item.SkillLamps;
import net.nerdsmp.paper.expedition.content.ExpeditionLoot;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Room;
import net.nerdsmp.paper.expedition.content.ScriptAction;
import net.nerdsmp.paper.loot.RewardDropService;
import net.nerdsmp.paper.mob.CustomMobManager;
import org.bukkit.entity.Enemy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every mob an expedition can field, gathered from its waves, bosses and script spawns, with base
 * stats, abilities and possible drops, for the in-game guide book and the website.
 */
public final class ExpeditionBestiary {
    /** {@code role} is Boss, Elite or Enemy. Health and damage are base values (null when unknown). */
    public record Entry(String id, String name, String nameMarkup, String role, List<String> rooms, Double health, Double damage,
                        String ability, List<RewardDropService.DropLine> drops) {
        public Entry {
            rooms = List.copyOf(rooms);
            drops = List.copyOf(drops);
        }
    }

    private ExpeditionBestiary() { }

    public static List<Entry> of(NerdSmpPlugin plugin, ExpeditionDefinition expedition) {
        Map<String, Set<String>> rooms = new LinkedHashMap<>();
        Set<String> bosses = new LinkedHashSet<>();
        for (Room room : expedition.roomList()) {
            var encounter = room.encounter();
            if (encounter.boss() != null) {
                bosses.add(encounter.boss());
                rooms.computeIfAbsent(encounter.boss(), key -> new LinkedHashSet<>()).add(room.title());
            }
            for (var wave : encounter.waves())
                for (String mob : wave.mobs().keySet()) rooms.computeIfAbsent(mob, key -> new LinkedHashSet<>()).add(room.title());
            for (var actions : encounter.scripts().values())
                for (ScriptAction action : actions)
                    if (action.verb() == ScriptAction.Verb.SPAWN)
                        rooms.computeIfAbsent(action.arg(0).toLowerCase(Locale.ROOT), key -> new LinkedHashSet<>()).add(room.title());
        }
        for (var pack : expedition.story().packs().values())
            for (String mob : pack.mobs().keySet()) rooms.computeIfAbsent(mob, key -> new LinkedHashSet<>()).add("Standing guard");
        List<Entry> entries = new ArrayList<>();
        List<String> order = new ArrayList<>(bosses);
        rooms.keySet().stream().filter(id -> !bosses.contains(id)).forEach(order::add);
        for (String id : order) {
            CustomMobManager.MobSheet sheet = plugin.mobs().sheet(id).orElse(null);
            if (sheet == null) continue;
            boolean boss = bosses.contains(id) || sheet.boss();
            String profile = sheet.lootProfile() != null ? sheet.lootProfile() : plugin.rewardDrops().profiles().mobAssignments().get(id);
            boolean hostile = boss || id.startsWith("custom:") || sheet.type().getEntityClass() != null
                && Enemy.class.isAssignableFrom(sheet.type().getEntityClass());
            List<RewardDropService.DropLine> drops = new ArrayList<>();
            if (sheet.relicDrop() != null && sheet.relicChance() > 0) {
                String relic = plugin.items().itemName(new net.nerdsmp.api.model.ItemId(sheet.relicDrop()))
                    .map(ExpeditionBestiary::plain).orElse(sheet.relicDrop());
                drops.add(new RewardDropService.DropLine(relic + " (relic)", sheet.relicChance(), 1, 1));
            }
            // The expedition's own loot by the mob's role, then its coin purse.
            var role = boss ? ExpeditionDefinition.Role.BOSS : expedition.story().mob(id).role();
            if (role == ExpeditionDefinition.Role.NORMAL && sheet.elite()) role = ExpeditionDefinition.Role.ELITE;
            var source = switch (role) {
                case BOSS -> ExpeditionLoot.Source.BOSS;
                case MINIBOSS -> ExpeditionLoot.Source.MINIBOSS;
                case ELITE -> ExpeditionLoot.Source.ELITE;
                default -> ExpeditionLoot.Source.NORMAL;
            };
            var table = plugin.expeditions() == null ? ExpeditionLoot.Table.EMPTY : plugin.expeditions().lootTable(expedition.id());
            for (var line : ExpeditionLoot.lines(table, source, 1))
                drops.add(new RewardDropService.DropLine(lineName(plugin, table, line), line.chance(), line.minimum(), line.maximum()));
            drops.addAll(plugin.rewardDrops().possibleDrops(sheet.type(), hostile, boss, sheet.elite(), profile, sheet.markReward(), true));
            entries.add(new Entry(id, plain(sheet.name()), sheet.name(), boss ? "Boss" : sheet.elite() ? "Elite" : "Enemy",
                List.copyOf(rooms.get(id)), sheet.health(), sheet.attackDamage(), abilities(sheet, expedition.story().mob(id)), drops));
        }
        return List.copyOf(entries);
    }

    /** A loot line's name for guides: the item, a set piece, a signature, or an experience bottle. */
    static String lineName(NerdSmpPlugin plugin, ExpeditionLoot.Table table, ExpeditionLoot.Line line) {
        return switch (line.kind()) {
            case LAMP -> switch (line.item()) {
                case "greater" -> SkillLamps.Grade.GREATER.title();
                case "grand" -> SkillLamps.Grade.GRAND.title();
                default -> SkillLamps.Grade.PLAIN.title();
            };
            case GEAR -> table.gear().isEmpty() ? "Set piece" : !table.finds().isEmpty() ? "A piece of gear (may be sealed)" : "A " + setName(plugin, table) + " piece (sealed)";
            case ITEM -> line.item().equals(ExpeditionLoot.SIGNATURE)
                ? (table.signatures().isEmpty() ? "Signature item" : String.join(" or ", table.signatures().stream().map(id -> name(plugin, id)).toList()))
                : name(plugin, line.item());
        };
    }

    private static String setName(NerdSmpPlugin plugin, ExpeditionLoot.Table table) {
        String first = name(plugin, table.gear().getFirst());
        int space = first.indexOf(' ');
        return space > 0 ? first.substring(0, space) : first;
    }

    static String name(NerdSmpPlugin plugin, String id) {
        try {
            return plugin.items().itemName(new net.nerdsmp.api.model.ItemId(id)).map(ExpeditionBestiary::plain).orElse(id);
        } catch (IllegalArgumentException invalid) {
            return id;
        }
    }

    /** What a mob does: its own ability, then what this expedition gives it, one sentence each. */
    private static String abilities(CustomMobManager.MobSheet sheet, ExpeditionDefinition.MobProfile profile) {
        List<String> lines = new ArrayList<>();
        if (!ability(sheet).isBlank()) lines.add(ability(sheet));
        for (String id : profile.abilities()) Abilities.info(id).ifPresent(info -> lines.add(info.title() + ": " + info.description()));
        if (profile.deathAbility() != null) Abilities.info(profile.deathAbility()).ifPresent(info -> lines.add(info.title() + " (as it falls): " + info.description()));
        return String.join(" ", lines);
    }

    static String ability(CustomMobManager.MobSheet sheet) {
        return switch (sheet.ability()) {
            case "PULSE" -> sheet.abilityDamage() <= 0 ? "" : "Shockwave: " + trim(sheet.abilityDamage()) + " damage within "
                + trim(sheet.abilityRadius()) + " blocks every " + sheet.abilityCooldownSeconds() + "s. A ring marks it first; step out.";
            case "HEAL_SELF" -> "Heals itself every " + sheet.abilityCooldownSeconds() + "s. Focus it down.";
            default -> "";
        };
    }

    static String percent(double chance) {
        double value = chance * 100;
        if (value >= 99.95) return "100%";
        if (value >= 10) return Math.round(value) + "%";
        if (value >= 1) return String.format(Locale.ROOT, "%.1f%%", value).replace(".0%", "%");
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    static String amount(RewardDropService.DropLine line) {
        return line.minimum() == line.maximum() ? (line.minimum() > 1 ? "×" + line.minimum() : "") : line.minimum() + "–" + line.maximum();
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    static String plain(String markup) {
        return PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(markup));
    }
}
