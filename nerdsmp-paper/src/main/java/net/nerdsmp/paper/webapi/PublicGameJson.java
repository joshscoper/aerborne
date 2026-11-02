package net.nerdsmp.paper.webapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.campaign.season.SeasonDefinition;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.Reward;
import net.nerdsmp.paper.campaign.season.SeasonLedger;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import net.nerdsmp.paper.expedition.content.Keystone;
import net.nerdsmp.paper.expedition.content.Modifier;
import net.nerdsmp.paper.expedition.content.Tier;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Public, read-only expedition and season documents for the website. Only published expeditions,
 * server-wide totals and top contributors' in-game names appear; no accounts or UUIDs.
 */
final class PublicGameJson {
    private static final int TOP = 5;

    private PublicGameJson() { }

    /** How much of each expedition the document carries: everything, guides without bestiaries, or the list. */
    enum Detail { FULL, GUIDES, LIST }

    private static volatile Detail published = Detail.FULL;

    /**
     * The most detail that fits the expeditions budget: everything, then without bestiaries, then the
     * bare list. Staff are told once when the level changes, not on every refresh.
     */
    static String expeditions(NerdSmpPlugin plugin, String sampled) {
        for (Detail level : Detail.values()) {
            String json = expeditions(plugin, sampled, level);
            int bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (level != Detail.LIST && bytes > ApiSnapshot.MAX_EXPEDITIONS_BYTES - 512) continue;
            if (level != published) {
                published = level;
                if (level == Detail.GUIDES) plugin.getLogger().info("The expedition list is large (" + bytes / 1024 + " KB); the web API now leaves out the bestiaries.");
                else if (level == Detail.LIST) plugin.getLogger().warning("The expedition list is too large for the web API; publishing it without guides or bestiaries.");
            }
            return json;
        }
        throw new IllegalStateException("unreachable");
    }

    private static String expeditions(NerdSmpPlugin plugin, String sampled, Detail detail) {
        var service = plugin.expeditions();
        JsonObject root = new JsonObject();
        root.addProperty("apiVersion", 1);
        root.addProperty("ready", true);
        root.addProperty("sampledAt", sampled);
        root.addProperty("activeRuns", service.activeRuns());
        root.addProperty("maxInstances", service.maxInstances());
        root.addProperty("vanillaPortals", service.vanillaPortals());
        root.add("keystones", keystones(service));
        var keyPool = service.keyPool();
        JsonArray list = new JsonArray();
        for (ExpeditionDefinition definition : service.definitions()) {
            if (!service.playable(definition)) continue;
            JsonObject entry = new JsonObject();
            entry.addProperty("id", definition.id());
            entry.addProperty("title", definition.title());
            entry.addProperty("description", definition.description());
            entry.addProperty("dimension", definition.dimension().key());
            entry.addProperty("level", definition.level());
            entry.addProperty("minParty", definition.minParty());
            entry.addProperty("maxParty", definition.maxParty());
            entry.addProperty("raid", definition.story().raid());
            entry.addProperty("rooms", definition.rooms().size());
            entry.addProperty("bosses", definition.rooms().values().stream()
                .filter(room -> room.encounter().type() == ExpeditionDefinition.EncounterType.BOSS).count());
            entry.addProperty("timeLimitMinutes", definition.timeLimitMinutes());
            entry.addProperty("activeRuns", service.activeRuns(definition.id()));
            int rating = net.nerdsmp.paper.expedition.content.Difficulty.rating(definition);
            JsonObject difficulty = new JsonObject();
            difficulty.addProperty("rating", rating);
            difficulty.addProperty("label", net.nerdsmp.paper.expedition.content.Difficulty.label(rating));
            entry.add("difficulty", difficulty);
            if (detail != Detail.LIST) {
                entry.add("guide", guide(definition));
                if (detail == Detail.FULL) entry.add("bestiary", bestiary(plugin, definition));
                entry.add("rewards", rewards(plugin, definition));
            }
            String seal = plugin.campaign() == null ? null : plugin.campaign().expeditionLock(definition.id()).orElse(null);
            if (seal == null) entry.add("sealedBy", com.google.gson.JsonNull.INSTANCE);
            else entry.addProperty("sealedBy", seal);
            var totals = service.totals(definition.id());
            JsonArray tiers = new JsonArray();
            for (Tier tier : service.tiersFor(definition)) {
                JsonObject row = new JsonObject();
                row.addProperty("id", tier.id());
                row.addProperty("title", tier.title());
                row.addProperty("level", definition.level() + tier.levelBonus());
                row.addProperty("lives", tier.lives());
                row.addProperty("runBack", tier.runBack());
                row.addProperty("lootRolls", tier.lootRolls());
                row.addProperty("bossItemChance", net.nerdsmp.paper.expedition.content.ExpeditionLoot.rareChance(
                    net.nerdsmp.paper.expedition.content.ExpeditionLoot.BOSS_ITEM_CHANCE, net.nerdsmp.paper.expedition.content.ExpeditionLoot.luck(tier.id(), 0)));
                row.addProperty("itemLevelBonus", net.nerdsmp.paper.expedition.content.ExpeditionLoot.itemLevelBonus(tier.id(), 0, definition.level(), definition.level()));
                row.addProperty("marks", Math.round(definition.loot().marks() * tier.marks()));
                if (tier.requires() != null) row.addProperty("requires", tier.requires());
                JsonArray modifiers = new JsonArray();
                for (Modifier modifier : service.modifiers(definition, tier)) {
                    JsonObject value = new JsonObject();
                    value.addProperty("id", modifier.key());
                    value.addProperty("title", modifier.title());
                    value.addProperty("description", modifier.description());
                    modifiers.add(value);
                }
                row.add("modifiers", modifiers);
                var total = totals.get(tier.id());
                row.addProperty("clears", total == null ? 0 : total.clears());
                row.addProperty("bestSeconds", total == null ? 0 : total.bestSeconds());
                JsonArray party = new JsonArray();
                if (total != null) total.bestParty().stream().filter(name -> name.matches("[A-Za-z0-9_]{3,16}")).forEach(party::add);
                row.add("bestParty", party);
                tiers.add(row);
            }
            entry.add("tiers", tiers);
            if (keyPool.contains(definition)) {
                JsonObject keystone = new JsonObject();
                keystone.addProperty("timerSeconds", Keystone.timerSeconds(definition.timeLimitMinutes()));
                service.keystones().best(definition.id()).ifPresent(best -> {
                    JsonObject row = new JsonObject();
                    row.addProperty("level", best.level());
                    row.addProperty("seconds", best.seconds());
                    JsonArray party = new JsonArray();
                    best.party().stream().filter(name -> name.matches("[A-Za-z0-9_]{3,16}")).forEach(party::add);
                    row.add("party", party);
                    keystone.add("best", row);
                });
                entry.add("keystone", keystone);
            }
            list.add(entry);
        }
        root.add("expeditions", list);
        return root.toString();
    }

    /** This week's keystone affixes at the levels they join, and the rules a key follows. */
    private static JsonObject keystones(net.nerdsmp.paper.expedition.ExpeditionService service) {
        JsonObject json = new JsonObject();
        json.addProperty("week", Keystone.week(java.time.LocalDate.now(java.time.ZoneOffset.UTC)));
        json.addProperty("deathPenaltySeconds", Keystone.DEATH_PENALTY_SECONDS);
        json.addProperty("levelScale", 0.08);
        JsonArray affixes = new JsonArray();
        for (int level : new int[]{4, 7, 10}) {
            JsonObject row = new JsonObject();
            row.addProperty("fromLevel", level);
            JsonArray modifiers = new JsonArray();
            for (Modifier modifier : service.affixes(level)) {
                JsonObject value = new JsonObject();
                value.addProperty("id", modifier.key());
                value.addProperty("title", modifier.title());
                value.addProperty("description", modifier.description());
                modifiers.add(value);
            }
            row.add("modifiers", modifiers);
            affixes.add(row);
        }
        json.add("affixes", affixes);
        return json;
    }

    private static JsonObject guide(ExpeditionDefinition definition) {
        var guide = definition.guide();
        JsonObject json = new JsonObject();
        json.addProperty("overview", guide.overview());
        if (!guide.lore().isBlank()) json.addProperty("lore", guide.lore());
        JsonArray tips = new JsonArray();
        guide.tips().stream().limit(12).forEach(tips::add);
        json.add("tips", tips);
        JsonArray rooms = new JsonArray();
        for (var room : definition.publicRooms()) {
            JsonObject row = new JsonObject();
            row.addProperty("title", room.title());
            row.addProperty("type", room.encounter().type().name().toLowerCase(Locale.ROOT));
            if (room.encounter().type() == ExpeditionDefinition.EncounterType.SURVIVE) row.addProperty("seconds", room.encounter().surviveSeconds());
            if (room.encounter().type() == ExpeditionDefinition.EncounterType.WAVES) row.addProperty("waves", room.encounter().waves().size());
            if (room.optional()) row.addProperty("optional", true);
            row.addProperty("tip", guide.rooms().getOrDefault(room.id(), ""));
            rooms.add(row);
        }
        json.add("rooms", rooms);
        json.addProperty("boss", guide.boss());
        return json;
    }

    private static JsonArray bestiary(NerdSmpPlugin plugin, ExpeditionDefinition definition) {
        JsonArray list = new JsonArray();
        for (var mob : net.nerdsmp.paper.expedition.ExpeditionBestiary.of(plugin, definition)) {
            if (list.size() >= 16) break;
            JsonObject row = new JsonObject();
            row.addProperty("name", mob.name());
            row.addProperty("role", mob.role());
            JsonArray rooms = new JsonArray();
            mob.rooms().forEach(rooms::add);
            row.add("rooms", rooms);
            if (mob.health() != null) row.addProperty("health", Math.round(mob.health()));
            if (mob.damage() != null) row.addProperty("damage", Math.round(mob.damage()));
            if (!mob.ability().isBlank()) row.addProperty("ability", mob.ability());
            JsonArray drops = new JsonArray();
            for (var drop : mob.drops()) {
                if (drops.size() >= 8) break;
                JsonObject line = new JsonObject();
                line.addProperty("item", drop.item());
                line.addProperty("chance", Math.round(drop.chance() * 10_000) / 10_000.0);
                line.addProperty("min", drop.minimum());
                line.addProperty("max", drop.maximum());
                drops.add(line);
            }
            row.add("drops", drops);
            list.add(row);
        }
        return list;
    }

    private static JsonObject rewards(NerdSmpPlugin plugin, ExpeditionDefinition definition) {
        JsonObject json = new JsonObject();
        json.addProperty("marks", definition.loot().marks());
        JsonArray items = new JsonArray();
        definition.loot().items().forEach((id, amount) -> {
            JsonObject row = new JsonObject();
            String name = words(id);
            try {
                name = plugin.items().itemName(new net.nerdsmp.api.model.ItemId(id))
                    .map(value -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(value))).orElse(name);
            } catch (IllegalArgumentException invalid) {
                // Vanilla material: keep its plain name.
            }
            row.addProperty("name", name);
            row.addProperty("amount", amount);
            items.add(row);
        });
        json.add("items", items);
        json.addProperty("profileRolls", definition.loot().profile() != null);
        // The expedition's own loot: materials, its set and its boss signatures.
        var table = plugin.expeditions().lootTable(definition.id());
        for (var group : new String[]{"materials", "gear", "signatures"}) {
            JsonArray names = new JsonArray();
            List<String> ids = switch (group) {
                case "materials" -> table.materials();
                case "gear" -> table.gear();
                default -> table.signatures();
            };
            for (String id : ids) names.add(itemName(plugin, id));
            json.add(group, names);
        }
        // The dungeon's gear in detail. A companion is never listed: those are for players to find.
        JsonArray bosses = new JsonArray();
        for (var room : definition.publicRooms()) {
            List<String> own = table.bossLoot().get(room.id());
            if (own == null) continue;
            JsonObject row = new JsonObject();
            // A council has no one boss to name, so it goes by its room.
            var leaders = room.encounter().bossList();
            row.addProperty("boss", leaders.size() != 1 ? room.title() : plugin.mobs().sheet(leaders.getFirst()).map(sheet -> plainText(sheet.name())).orElse(room.title()));
            row.addProperty("room", room.title());
            row.addProperty("optional", room.optional());
            row.add("items", gearRows(plugin, own));
            bosses.add(row);
        }
        json.add("bosses", bosses);
        json.add("setGear", gearRows(plugin, table.gear()));
        json.add("finds", gearRows(plugin, table.finds()));
        return json;
    }

    private static JsonArray gearRows(NerdSmpPlugin plugin, List<String> ids) {
        JsonArray rows = new JsonArray();
        for (String id : ids) plugin.items().gearSheet(new net.nerdsmp.api.model.ItemId(id)).ifPresent(gear -> {
            JsonObject row = new JsonObject();
            row.addProperty("name", plainText(gear.name()));
            row.addProperty("slot", gear.slot().name().toLowerCase(Locale.ROOT));
            row.addProperty("rarity", gear.rarity().toLowerCase(Locale.ROOT));
            row.addProperty("itemLevel", gear.itemLevel());
            JsonArray stats = new JsonArray();
            gear.stats().forEach(stats::add);
            row.add("stats", stats);
            JsonArray abilities = new JsonArray();
            for (var ability : gear.abilities()) {
                JsonObject line = new JsonObject();
                line.addProperty("trigger", ability.trigger().label());
                line.addProperty("name", ability.displayName());
                line.addProperty("description", ability.description());
                abilities.add(line);
            }
            row.add("abilities", abilities);
            rows.add(row);
        });
        return rows;
    }

    private static String plainText(String markup) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(markup));
    }

    private static String itemName(NerdSmpPlugin plugin, String id) {
        try {
            return plugin.items().itemName(new net.nerdsmp.api.model.ItemId(id))
                .map(value -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(value))).orElse(words(id));
        } catch (IllegalArgumentException invalid) {
            return words(id);
        }
    }

    static String campaign(NerdSmpPlugin plugin, String sampled) {
        var campaign = plugin.campaign();
        JsonObject root = new JsonObject();
        root.addProperty("apiVersion", 1);
        root.addProperty("sampledAt", sampled);
        SeasonDefinition season = campaign.season().orElse(null);
        if (season == null) {
            root.addProperty("ready", false);
            return root.toString();
        }
        Instant now = Instant.now();
        SeasonLedger ledger = campaign.ledger();
        root.addProperty("ready", true);
        root.addProperty("id", season.id());
        root.addProperty("title", season.title());
        root.addProperty("description", season.description());
        root.addProperty("startsAt", season.startsAt().toString());
        root.addProperty("endsAt", season.endsAt().toString());
        root.addProperty("active", season.active(now));
        root.addProperty("playerDailyCap", season.playerDailyCap());
        JsonArray goals = new JsonArray();
        for (var goal : season.goals().values()) {
            JsonObject row = new JsonObject();
            row.addProperty("id", goal.id());
            row.addProperty("title", goal.title());
            row.addProperty("description", goal.description());
            row.addProperty("points", ledger.points(goal.id()));
            row.addProperty("target", goal.target());
            row.addProperty("dailyCap", goal.dailyCap() == null ? season.playerDailyCap() : goal.dailyCap());
            JsonArray sources = new JsonArray();
            for (var source : goal.sources()) {
                JsonObject value = new JsonObject();
                value.addProperty("type", source.type().key());
                value.addProperty("points", source.points());
                JsonArray filters = new JsonArray();
                source.filters().stream().sorted().limit(12).forEach(filters::add);
                value.add("filters", filters);
                sources.add(value);
            }
            row.add("sources", sources);
            row.add("top", top(ledger, goal.id()));
            goals.add(row);
        }
        root.add("goals", goals);
        JsonArray milestones = new JsonArray();
        var reached = ledger.reachedMilestones();
        for (var milestone : season.milestones().values()) {
            JsonObject row = new JsonObject();
            row.addProperty("id", milestone.id());
            row.addProperty("title", milestone.title());
            row.addProperty("description", milestone.description());
            row.addProperty("reached", reached.containsKey(milestone.id()));
            if (reached.containsKey(milestone.id())) row.addProperty("reachedAt", reached.get(milestone.id()).toString());
            JsonArray requires = new JsonArray();
            milestone.requires().forEach((goal, amount) -> {
                JsonObject value = new JsonObject();
                value.addProperty("goal", goal);
                value.addProperty("amount", amount);
                value.addProperty("points", Math.min(amount, ledger.points(goal)));
                requires.add(value);
            });
            row.add("requires", requires);
            JsonArray rewards = new JsonArray();
            for (Reward reward : milestone.rewards()) {
                String text = rewardText(plugin, reward);
                if (text == null) continue;
                JsonObject value = new JsonObject();
                value.addProperty("type", reward.getClass().getSimpleName().toLowerCase(Locale.ROOT));
                value.addProperty("text", text);
                rewards.add(value);
            }
            row.add("rewards", rewards);
            milestones.add(row);
        }
        root.add("milestones", milestones);
        JsonArray buffs = new JsonArray();
        for (SeasonLedger.ActiveBuff buff : ledger.activeBuffs(now)) {
            JsonObject value = new JsonObject();
            value.addProperty("name", campaign.plainBuffName(buff));
            value.addProperty("from", buff.label());
            value.addProperty("endsAt", buff.endsAt().toString());
            buffs.add(value);
        }
        root.add("buffs", buffs);
        root.add("topContributors", top(ledger, null));
        return root.toString();
    }

    private static JsonArray top(SeasonLedger ledger, String goal) {
        JsonArray rows = new JsonArray();
        for (var entry : ledger.top(goal, TOP)) {
            String name = ledger.name(entry.getKey()).orElse(null);
            if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) continue;
            JsonObject row = new JsonObject();
            row.addProperty("name", name);
            row.addProperty("points", entry.getValue());
            rows.add(row);
        }
        return rows;
    }

    /** Plain-text reward lines for the website; console commands and broadcasts stay private. */
    static String rewardText(NerdSmpPlugin plugin, Reward reward) {
        return switch (reward) {
            case Reward.Buff buff -> words(buff.effect()) + (buff.amplifier() > 0 ? " " + (buff.amplifier() + 1) : "") + " for everyone, " + hours(buff.duration());
            case Reward.Boost boost -> "+" + boost.percent() + "% " + boost.kind().label() + " for everyone, " + hours(boost.duration());
            case Reward.Item item -> item.amount() + " × " + words(item.item()) + " for every player to claim";
            case Reward.Marks marks -> marks.amount() + " Marks for every player to claim";
            case Reward.Unlock unlock -> unlock.expedition() != null
                ? "Opens the " + (plugin.expeditions() == null ? words(unlock.expedition())
                    : plugin.expeditions().definition(unlock.expedition()).map(ExpeditionDefinition::title).orElse(words(unlock.expedition()))) + " expedition"
                : "Unlocks " + words(unlock.feature());
            case Reward.Broadcast broadcast -> null;
            case Reward.Command command -> null;
        };
    }

    private static String hours(java.time.Duration duration) {
        long hours = duration.toHours();
        if (hours >= 48 && hours % 24 == 0) return hours / 24 + " days";
        if (hours >= 1) return hours + (hours == 1 ? " hour" : " hours");
        return duration.toMinutes() + " minutes";
    }

    private static String words(String id) {
        String text = id.replace("custom:", "").replace("vanilla:", "").replace('_', ' ').replace('-', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
