package net.nerdsmp.paper.engine.mob;

import net.nerdsmp.paper.zone.ZoneMobTypes;
import org.bukkit.entity.EntityType;

import java.util.Locale;
import java.util.Set;

/** Immutable authored encounter. Names are plain text; identifiers, never names, identify content. */
public record MobBlueprint(
    String id, String template, String name, String type, Rank rank, int level,
    double health, double attackDamage, double movementSpeed, Ability ability,
    double abilityRadius, double abilityPower, int abilityCooldownSeconds,
    int experienceReward, String lootProfile
) {
    public static final int MAX_BLUEPRINTS = 256;
    public static final Set<String> TEMPLATE_IDS = Set.of("brawler", "ranger", "guardian", "caster");
    public enum Rank { NORMAL, ELITE }
    public enum Ability { NONE, PULSE, HEAL_SELF }

    public MobBlueprint {
        validId(id, "Mob ID");
        if (!TEMPLATE_IDS.contains(template)) throw new IllegalArgumentException("Unknown mob template.");
        if (name == null || name.isBlank() || name.length() > 64 || name.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Name must contain 1–64 visible characters.");
        if (type == null || !type.equals(type.toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Entity type must be a lowercase identifier.");
        EntityType entity;
        try { entity = EntityType.valueOf(type.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Unknown entity type: " + type); }
        if (!ZoneMobTypes.allowed(entity)) throw new IllegalArgumentException("That entity needs a specialized habitat or boss lifecycle.");
        if (rank == null || ability == null) throw new IllegalArgumentException("Rank and ability are required.");
        if (level < 1 || level > 100) throw new IllegalArgumentException("Level must be 1–100.");
        bounded(health, 1, 1024, "Health"); bounded(attackDamage, 0, 100, "Damage");
        bounded(movementSpeed, 0.05, 0.8, "Movement speed");
        bounded(abilityRadius, 1, 12, "Ability radius"); bounded(abilityPower, 0, 100, "Ability power");
        if (abilityCooldownSeconds < 5 || abilityCooldownSeconds > 300)
            throw new IllegalArgumentException("Ability cooldown must be 5–300 seconds.");
        if (experienceReward < 0 || experienceReward > 1000) throw new IllegalArgumentException("Experience reward must be 0–1000.");
        lootProfile = lootProfile == null ? "" : lootProfile;
        if (!lootProfile.isEmpty()) validId(lootProfile, "Loot profile");
    }

    public static void validId(String id, String label) {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,47}"))
            throw new IllegalArgumentException(label + " must contain 1–48 lowercase letters, digits, underscores or hyphens.");
    }
    private static void bounded(double value, double min, double max, String label) {
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
    }
    public EntityType entityType() { return EntityType.valueOf(type.toUpperCase(Locale.ROOT)); }
    public String displayName() { return name; }
    public MobBlueprint renamed(String value) { return copy(value, template, name, type, rank, level, health, attackDamage, movementSpeed, ability, abilityRadius, abilityPower, abilityCooldownSeconds, experienceReward, lootProfile); }
    public MobBlueprint withName(String value) { return copy(id, template, value, type, rank, level, health, attackDamage, movementSpeed, ability, abilityRadius, abilityPower, abilityCooldownSeconds, experienceReward, lootProfile); }
    public MobBlueprint withType(String value) { return copy(id, template, name, value, rank, level, health, attackDamage, movementSpeed, ability, abilityRadius, abilityPower, abilityCooldownSeconds, experienceReward, lootProfile); }
    public MobBlueprint withRank(Rank value) { return copy(id, template, name, type, value, level, health, attackDamage, movementSpeed, ability, abilityRadius, abilityPower, abilityCooldownSeconds, experienceReward, lootProfile); }
    public MobBlueprint withLevel(int value) { return copy(id, template, name, type, rank, value, health, attackDamage, movementSpeed, ability, abilityRadius, abilityPower, abilityCooldownSeconds, experienceReward, lootProfile); }
    public MobBlueprint withHealth(double value) { return copy(id, template, name, type, rank, level, value, attackDamage, movementSpeed, ability, abilityRadius, abilityPower, abilityCooldownSeconds, experienceReward, lootProfile); }
    public MobBlueprint withAttackDamage(double value) { return copy(id, template, name, type, rank, level, health, value, movementSpeed, ability, abilityRadius, abilityPower, abilityCooldownSeconds, experienceReward, lootProfile); }
    public MobBlueprint withMovementSpeed(double value) { return copy(id, template, name, type, rank, level, health, attackDamage, value, ability, abilityRadius, abilityPower, abilityCooldownSeconds, experienceReward, lootProfile); }
    public MobBlueprint withAbility(Ability value) { return copy(id, template, name, type, rank, level, health, attackDamage, movementSpeed, value, abilityRadius, abilityPower, abilityCooldownSeconds, experienceReward, lootProfile); }
    public MobBlueprint withAbilityRadius(double value) { return copy(id, template, name, type, rank, level, health, attackDamage, movementSpeed, ability, value, abilityPower, abilityCooldownSeconds, experienceReward, lootProfile); }
    public MobBlueprint withAbilityPower(double value) { return copy(id, template, name, type, rank, level, health, attackDamage, movementSpeed, ability, abilityRadius, value, abilityCooldownSeconds, experienceReward, lootProfile); }
    public MobBlueprint withAbilityCooldownSeconds(int value) { return copy(id, template, name, type, rank, level, health, attackDamage, movementSpeed, ability, abilityRadius, abilityPower, value, experienceReward, lootProfile); }
    public MobBlueprint withExperienceReward(int value) { return copy(id, template, name, type, rank, level, health, attackDamage, movementSpeed, ability, abilityRadius, abilityPower, abilityCooldownSeconds, value, lootProfile); }
    public MobBlueprint withLootProfile(String value) { return copy(id, template, name, type, rank, level, health, attackDamage, movementSpeed, ability, abilityRadius, abilityPower, abilityCooldownSeconds, experienceReward, value); }
    private static MobBlueprint copy(String id, String template, String name, String type, Rank rank, int level, double health,
        double attack, double speed, Ability ability, double radius, double power, int cooldown, int experience, String loot) {
        return new MobBlueprint(id, template, name, type, rank, level, health, attack, speed, ability, radius, power, cooldown, experience, loot);
    }
}
