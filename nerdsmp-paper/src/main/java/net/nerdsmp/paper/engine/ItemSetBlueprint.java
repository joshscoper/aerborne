package net.nerdsmp.paper.engine;

import java.util.Map;
import java.util.TreeMap;

public record ItemSetBlueprint(String id, String name, int pieceCount, Map<Integer, Bonus> bonuses) {
    public ItemSetBlueprint {
        id = ItemBlueprint.identifier(id);
        name = ItemBlueprint.text(name, 64, "Set name");
        if (pieceCount < 2 || pieceCount > 6) throw new IllegalArgumentException("Sets contain 2–6 pieces.");
        if (bonuses == null || bonuses.size() > pieceCount || bonuses.isEmpty())
            throw new IllegalArgumentException("Define at least one set bonus.");
        for (var entry : bonuses.entrySet()) {
            if (entry.getKey() == null || entry.getKey() < 2 || entry.getKey() > pieceCount || entry.getValue() == null)
                throw new IllegalArgumentException("Set bonus thresholds must be between 2 and the set's piece count.");
        }
        bonuses = java.util.Collections.unmodifiableMap(new TreeMap<>(bonuses));
    }
    public record Bonus(String description, Map<String, Double> stats) {
        public Bonus {
            description = ItemBlueprint.text(description, 160, "Set bonus description");
            stats = ItemStats.validate(stats, true);
            if (stats.isEmpty()) throw new IllegalArgumentException("A set bonus needs a supported stat.");
        }
    }
}
