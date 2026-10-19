package net.nerdsmp.paper.studio.qa;

import net.nerdsmp.paper.loot.profile.LootProfileDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Read-only graph and balance checks. Warnings are tuning guidance, not automatic release or publication gates. */
public final class ContentValidator {
    private ContentValidator() { }
    public enum Severity { ERROR, WARNING, INFO, MANUAL }
    public record Finding(Severity severity, String code, ContentRef content, String message) { }
    public record Dependency(ContentRef source, ContentRef target, String purpose) { }
    public record Item(String id, String setId, int level, Map<String, Double> stats) {
        public Item { stats = Map.copyOf(stats); }
    }
    public record Mob(String id, int level, double health, double damage, int experience,
                      double abilityPower, int abilityCooldown, String lootProfile) { }
    public record Zone(String id, boolean enabled, boolean safe, int points, int mobs, int cap, int respawnSeconds) { }
    public record Catalog(Set<ContentRef> known, List<Dependency> dependencies, List<Item> items,
                          List<Mob> mobs, List<LootProfileDefinition> loot, List<Zone> zones,
                          List<Finding> observations) {
        public Catalog {
            known = Set.copyOf(known); dependencies = List.copyOf(dependencies); items = List.copyOf(items);
            mobs = List.copyOf(mobs); loot = List.copyOf(loot); zones = List.copyOf(zones); observations = List.copyOf(observations);
        }
    }
    public record Report(Map<ContentRef.Type, Integer> counts, List<Finding> findings, int omitted) {
        public Report { counts = Map.copyOf(counts); findings = List.copyOf(findings); }
        public long count(Severity severity) { return findings.stream().filter(finding -> finding.severity() == severity).count(); }
        public boolean referencesValid() { return count(Severity.ERROR) == 0; }
        /** A content scan alone can never prove player journeys, security, backups or release readiness. */
        public boolean releaseApproved() { return false; }
    }
    public static Report validate(Catalog catalog) {
        List<Finding> findings = new ArrayList<>(catalog.observations());
        Map<ContentRef.Type, Integer> counts = new TreeMap<>();
        for (ContentRef ref : catalog.known()) counts.merge(ref.type(), 1, Integer::sum);
        for (Dependency dependency : catalog.dependencies()) {
            if (!catalog.known().contains(dependency.target())) findings.add(new Finding(Severity.ERROR, "missing_reference",
                dependency.source(), dependency.purpose() + " points to missing " + dependency.target() + "."));
        }
        for (Item item : catalog.items()) {
            ContentRef ref = new ContentRef(ContentRef.Type.ITEM, item.id());
            double damage = Math.max(item.stats().getOrDefault("damage", 0.0), item.stats().getOrDefault("projectile-damage", 0.0));
            if (damage > 10 + 1.5 * item.level()) findings.add(new Finding(Severity.WARNING, "item_damage_budget", ref,
                "Damage is high for item level " + item.level() + "; compare its time-to-kill against zone enemies."));
            if (item.stats().getOrDefault("defense", 0.0) > 20 + 2.0 * item.level()) findings.add(new Finding(Severity.WARNING,
                "item_defense_budget", ref, "Defense is high for this item level; test survivability with gems and set bonuses."));
            if (item.stats().entrySet().stream().anyMatch(stat -> stat.getKey().endsWith("-percent") && stat.getValue() > 50))
                findings.add(new Finding(Severity.WARNING, "large_percent_stat", ref,
                    "A percentage bonus exceeds 50%; test stacking and economy effects."));
            if (catalog.dependencies().stream().noneMatch(link -> link.target().equals(ref))) findings.add(new Finding(Severity.INFO,
                "item_acquisition_unverified", ref, "No authored loot, shop or recipe link was found. Check how players obtain this item."));
        }
        for (Mob mob : catalog.mobs()) {
            ContentRef ref = new ContentRef(ContentRef.Type.MOB, mob.id());
            if (mob.experience() > Math.max(20, mob.level() * 5) || mob.experience() / Math.max(1.0, mob.health()) > 2)
                findings.add(new Finding(Severity.WARNING, "mob_xp_budget", ref, "XP is generous for this level/health; test repeat-kill leveling rates."));
            if (mob.damage() > Math.max(12, mob.level() * 1.3)) findings.add(new Finding(Severity.WARNING,
                "mob_damage_budget", ref, "Basic attacks may overwhelm same-level early gear. Test a full player encounter."));
            if (mob.abilityPower() > Math.max(15, mob.level() * 1.5) && mob.abilityCooldown() <= 10)
                findings.add(new Finding(Severity.WARNING, "mob_ability_burst", ref, "A strong ability has a short cooldown; check its counterplay."));
            if (mob.lootProfile().isEmpty()) findings.add(new Finding(Severity.INFO, "default_mob_loot", ref,
                "No explicit authored loot profile; this mob uses zone assignment or default reward rules."));
        }
        for (LootProfileDefinition profile : catalog.loot()) {
            ContentRef ref = new ContentRef(ContentRef.Type.LOOT, profile.id());
            double marks = 0, items = 0;
            for (var entry : profile.entries()) {
                double expected = entry.chance() * (entry.minimum() + entry.maximum()) / 2.0;
                if (entry.kind() == LootProfileDefinition.Kind.MARKS) marks += expected; else items += expected;
                if (entry.chance() == 0) findings.add(new Finding(Severity.INFO, "zero_drop_chance", ref,
                    entry.id() + " has a zero chance and never rolls."));
            }
            if (profile.entries().isEmpty()) findings.add(new Finding(Severity.WARNING, "empty_loot", ref,
                profile.mode() == LootProfileDefinition.Mode.REPLACE ? "Replacement table is empty: normal item drops are removed."
                    : "This additional table has no rewards."));
            if (marks > 30) findings.add(new Finding(Severity.WARNING, "marks_budget", ref,
                "Expected Marks exceed 30 per kill; compare farming rates and server sinks."));
            if (items > 8) findings.add(new Finding(Severity.WARNING, "loot_volume", ref,
                "Expected item count exceeds eight per kill; inspect inventory pressure and farming loops."));
            if (profile.mode() == LootProfileDefinition.Mode.ADDITIVE) findings.add(new Finding(Severity.INFO,
                "additive_loot", ref, "Simulation covers this table only; vanilla and other allowed default rewards are additional."));
        }
        for (Zone zone : catalog.zones()) {
            ContentRef ref = new ContentRef(ContentRef.Type.ZONE, zone.id());
            if (!zone.safe() && (zone.points() == 0 || zone.mobs() == 0)) findings.add(new Finding(Severity.WARNING,
                "incomplete_zone", ref, "Encounter needs both spawn points and a mob roster before it can run."));
            if (!zone.enabled()) findings.add(new Finding(Severity.INFO, "disabled_zone", ref, "Zone is disabled; staff can finish authoring it safely."));
            if (zone.cap() >= 32 && zone.respawnSeconds() <= 10) findings.add(new Finding(Severity.WARNING,
                "dense_zone", ref, "High mob cap and short respawn delay need an occupied-zone load test."));
        }
        findings.sort(Comparator.comparing(Finding::severity).thenComparing(f -> f.content() == null ? "" : f.content().toString())
            .thenComparing(Finding::code).thenComparing(Finding::message));
        int omitted = Math.max(0, findings.size() - 512);
        return new Report(counts, findings.subList(0, Math.min(512, findings.size())), omitted);
    }
}
