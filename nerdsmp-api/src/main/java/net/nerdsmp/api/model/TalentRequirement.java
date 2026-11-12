package net.nerdsmp.api.model;

import java.util.List;
import java.util.Set;

/** Shared fork/merge rule. IDs refer to purchased ranks, never client-provided point totals. */
public record TalentRequirement(List<String> options, int minimum) {
    public static final TalentRequirement NONE = new TalentRequirement(List.of(), 0);
    public TalentRequirement {
        options = List.copyOf(options);
        if (minimum < 0 || minimum > options.size() || Set.copyOf(options).size() != options.size())
            throw new IllegalArgumentException("Invalid talent prerequisites");
    }
    public boolean met(Set<String> purchased) { return options.stream().filter(purchased::contains).count() >= minimum; }
    public static TalentRequirement any(String... ids) { return new TalentRequirement(List.of(ids), 1); }
}
