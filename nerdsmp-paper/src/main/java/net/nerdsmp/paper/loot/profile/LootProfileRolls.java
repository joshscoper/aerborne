package net.nerdsmp.paper.loot.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/** Authored percentages are exact: no hidden level, looting, or represented-stack multiplier. */
public final class LootProfileRolls {
    private LootProfileRolls() { }
    public record Roll(LootProfileDefinition.Entry entry, int amount) { }

    public static List<Roll> roll(LootProfileDefinition profile, DoubleSupplier random) {
        List<Roll> rolls = new ArrayList<>();
        for (var entry : profile.entries()) {
            if (unit(random.getAsDouble()) >= entry.chance()) continue;
            int amount = entry.minimum() + (int) (unit(random.getAsDouble())
                * (entry.maximum() - entry.minimum() + 1));
            rolls.add(new Roll(entry, amount));
        }
        return List.copyOf(rolls);
    }

    private static double unit(double value) {
        if (!Double.isFinite(value) || value < 0 || value >= 1)
            throw new IllegalArgumentException("A random roll must be in [0, 1).");
        return value;
    }
}
