package net.nerdsmp.paper.presentation;

import java.util.Locale;

/** Presentation tokens only; never use a brand name as an item, world, permission or storage identifier. */
public final class BrandPalette {
    public static final String NAME = "Aerborne";
    public static final String TAGLINE = "Small beginnings. Endless skies.";
    public static final String GREETING = "Your adventure begins above the clouds.";
    public static final String DESCRIPTION = "Build your island. Explore the skies. Discover magic.";
    public static final String SKY = "#77C8F5", CLOUD = "#F4F9FF", BREEZE = "#8CE8ED", MINT = "#70DBC3",
        LAVENDER = "#C59AFA", GOLD = "#F4C76B", PEACH = "#FFBBA0", TWILIGHT = "#283752",
        MIDNIGHT = "#17243B", STONE = "#8E9AAF";

    private BrandPalette() { }

    public static String title(String literal) {
        return "<gradient:" + SKY + ":" + BREEZE + "><bold>" + Chat.escape(literal) + "</bold></gradient>";
    }

    public static String headline(String name) {
        return title(NAME.equals(name) ? name.toUpperCase(Locale.ROOT) : name);
    }
}
