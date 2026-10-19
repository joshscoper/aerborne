package net.nerdsmp.paper.presentation;

import java.util.Locale;

enum DamageDisplayType {
    PHYSICAL("", 0xFF6B6B),
    PROJECTILE("RANGED", 0xFACC15),
    FIRE("FIRE", 0xFB923C),
    FROST("FROST", 0x67E8F9),
    ARCANE("ARCANE", 0xC084FC),
    POISON("VENOM", 0x86EFAC),
    BLAST("BLAST", 0xFBBF24);

    private final String label;
    private final int rgb;

    DamageDisplayType(String label, int rgb) {
        this.label = label;
        this.rgb = rgb;
    }

    String label() {
        return label;
    }

    int rgb() {
        return rgb;
    }

    static DamageDisplayType classify(
        String damageKey,
        String damageCause,
        boolean indirect
    ) {
        String key = damageKey == null ? "" : damageKey.toLowerCase(Locale.ROOT);
        String cause = damageCause == null ? "" : damageCause.toUpperCase(Locale.ROOT);
        if (containsAny(key, "freeze", "melting")) {
            return FROST;
        }
        if (containsAny(key, "fire", "lava", "hot_floor", "campfire")) {
            return FIRE;
        }
        if (containsAny(key, "wither", "poison")) {
            return POISON;
        }
        if (containsAny(key, "magic", "dragon_breath", "sonic_boom")) {
            return ARCANE;
        }
        if (containsAny(key, "explosion", "fireworks", "bad_respawn_point", "wind_charge")) {
            return BLAST;
        }
        if (containsAny(key, "arrow", "trident", "spear", "projectile", "thrown", "spit")) {
            return PROJECTILE;
        }
        return switch (cause) {
            case "FREEZE", "MELTING" -> FROST;
            case "FIRE", "FIRE_TICK", "LAVA", "HOT_FLOOR", "CAMPFIRE" -> FIRE;
            case "POISON", "WITHER" -> POISON;
            case "MAGIC", "DRAGON_BREATH", "SONIC_BOOM" -> ARCANE;
            case "BLOCK_EXPLOSION", "ENTITY_EXPLOSION" -> BLAST;
            case "PROJECTILE" -> PROJECTILE;
            default -> indirect ? PROJECTILE : PHYSICAL;
        };
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
