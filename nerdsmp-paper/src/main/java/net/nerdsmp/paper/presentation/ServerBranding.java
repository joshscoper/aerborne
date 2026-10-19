package net.nerdsmp.paper.presentation;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;

/** Public display text only. Never used as a storage key, namespace, permission or plugin ID. */
public record ServerBranding(String name, String mode, String tagline, String description) {
    public static ServerBranding read(ConfigurationSection config, String displayName, boolean skyblock) {
        String tagline = text(config, "branding.tagline", skyblock ? BrandPalette.TAGLINE : "Build a world together.", 64);
        String description = text(config, "branding.description", skyblock ? BrandPalette.DESCRIPTION : "Build • Explore • Trade", 80);
        if (skyblock && tagline.equals("Your island. A wider world.")) tagline = BrandPalette.TAGLINE;
        if (skyblock && (description.equals("Co-op islands • Quests • Player economy")
            || description.equals("Co-op islands - Quests - Player economy"))) description = BrandPalette.DESCRIPTION;
        return new ServerBranding(publicName(displayName),
            text(config, "branding.mode-label", skyblock ? "RPG Skyblock" : "Survival RPG", 32),
            tagline, description);
    }

    /** Adapt exact historical defaults in memory. Never rewrite an operator's configuration. */
    public static String publicName(String raw) {
        if (raw == null) return BrandPalette.NAME;
        String value = raw.strip();
        if (value.isEmpty() || value.length() > 32 || value.chars().anyMatch(Character::isISOControl)
            || value.matches("(?i)hearth[ _-]?sky")) return BrandPalette.NAME;
        return value;
    }

    private static String text(ConfigurationSection config, String path, String fallback, int maximum) {
        String value = config.getString(path, fallback).strip();
        return value.isEmpty() || value.length() > maximum || value.chars().anyMatch(Character::isISOControl) ? fallback : value;
    }

    public String titleMarkup() {
        return BrandPalette.title(name);
    }

    public String sidebarTitleMarkup() {
        return BrandPalette.headline(name);
    }

    public Component motd() {
        String label = mode.equals("RPG Skyblock") ? "A Skyblock RPG Adventure" : mode;
        return Chat.render(BrandPalette.headline(name) + " <" + BrandPalette.STONE + ">»</" + BrandPalette.STONE + "> <"
            + BrandPalette.CLOUD + ">" + Chat.escape(label) + "</" + BrandPalette.CLOUD + ">\n"
            + "<" + BrandPalette.STONE + ">" + Chat.escape(description) + "</" + BrandPalette.STONE + ">");
    }

    public String tabHeaderMarkup() {
        return titleMarkup() + "\n<" + BrandPalette.CLOUD + ">" + Chat.escape(tagline) + "</" + BrandPalette.CLOUD + ">";
    }

    public Component welcome() {
        return Chat.render("<" + BrandPalette.CLOUD + ">Welcome to</" + BrandPalette.CLOUD + "> "
            + titleMarkup() + "<" + BrandPalette.CLOUD + ">!</" + BrandPalette.CLOUD + ">");
    }

    public Component firstSteps() {
        return Chat.render("<" + BrandPalette.BREEZE + ">" + BrandPalette.GREETING + "</" + BrandPalette.BREEZE + ">");
    }
}
