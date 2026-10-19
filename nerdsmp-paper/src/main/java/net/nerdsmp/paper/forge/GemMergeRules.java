package net.nerdsmp.paper.forge;

import java.util.Objects;

public final class GemMergeRules {
    public static final int MAXIMUM_TIER = 4;

    private GemMergeRules() {
    }

    public static Result evaluate(GemType firstType, int firstTier, GemType secondType, int secondTier) {
        Objects.requireNonNull(firstType, "firstType");
        Objects.requireNonNull(secondType, "secondType");
        int boundedFirst = GemType.clampTier(firstTier);
        int boundedSecond = GemType.clampTier(secondTier);
        if (firstType != secondType) {
            return new Result(Status.DIFFERENT_TYPE, firstType, boundedFirst);
        }
        if (boundedFirst != boundedSecond) {
            return new Result(Status.DIFFERENT_TIER, firstType, boundedFirst);
        }
        if (boundedFirst >= MAXIMUM_TIER) {
            return new Result(Status.MAXIMUM_TIER, firstType, boundedFirst);
        }
        return new Result(Status.READY, firstType, boundedFirst + 1);
    }

    public enum Status {
        READY,
        DIFFERENT_TYPE,
        DIFFERENT_TIER,
        MAXIMUM_TIER
    }

    public record Result(Status status, GemType type, int outputTier) {
        public boolean ready() {
            return status == Status.READY;
        }
    }
}
