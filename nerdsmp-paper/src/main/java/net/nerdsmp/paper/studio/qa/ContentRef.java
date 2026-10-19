package net.nerdsmp.paper.studio.qa;

import java.util.Locale;
import java.util.Objects;

/** A stable, typed reference; player-facing names never identify content. */
public record ContentRef(Type type, String id) {
    public enum Type { ITEM, SET, MOB, LOOT, ZONE, DIGSITE, NPC, DIALOGUE, SHOP, RECIPE, WARP, QUEST, FACTION }
    public ContentRef {
        Objects.requireNonNull(type, "Reference type is required.");
        if (id == null || !id.matches("[a-z0-9_][a-z0-9_./:-]{0,95}"))
            throw new IllegalArgumentException("Use a stable lowercase content ID, up to 96 characters.");
    }
    public static ContentRef parse(String value) {
        if (value == null) throw new IllegalArgumentException("Use a reference such as ITEM:ember_sword.");
        int separator = value.indexOf(':');
        if (separator < 1 || separator == value.length() - 1)
            throw new IllegalArgumentException("Use a reference such as ITEM:ember_sword.");
        Type type;
        try { type = Type.valueOf(value.substring(0, separator).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException failure) { throw new IllegalArgumentException("Unknown content-reference type."); }
        return new ContentRef(type, value.substring(separator + 1));
    }
    @Override public String toString() { return type.name() + ":" + id; }
}
