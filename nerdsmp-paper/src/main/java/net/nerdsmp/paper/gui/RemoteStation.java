package net.nerdsmp.paper.gui;

import org.bukkit.permissions.Permissible;

/**
 * Services players normally use in the world, which Premium can also open from anywhere. One
 * place for the wording, so the hub, the commands and plugin.yml's permission-message agree.
 */
public enum RemoteStation {
    FORGE("Forge", "an anvil or Torren"),
    ENCHANTING("Arcane Enchanting", "an enchanting table or Veyra"),
    ALCHEMY("Alchemy", "a brewing stand or Sella"),
    IDENTIFY("Arcane Identification", "Nyra"),
    REPAIR("Repair All", "Torren"),
    BANK("Frontier Bank", "Orin at the bank");

    public static final String PERMISSION = "nerdsmp.station.remote";

    private final String displayName;
    private final String where;

    RemoteStation(String displayName, String where) {
        this.displayName = displayName;
        this.where = where;
    }

    public String displayName() {
        return displayName;
    }

    public static boolean remote(Permissible player) {
        return player.hasPermission(PERMISSION);
    }

    /** Plain text, identical to the permission-message in plugin.yml. */
    public String refusal() {
        return "Visit " + where + " to use " + displayName + ". Premium opens it from anywhere.";
    }

    /** MiniMessage chat line for {@link #refusal()}. */
    public String refusalMarkup() {
        return "<yellow>Visit " + where + " to use " + displayName + ".</yellow> <gray>Premium opens it from anywhere.</gray>";
    }

    /** Lore for the locked hub tile. */
    public String lockedLore() {
        return "<yellow>Visit " + where + ".</yellow>";
    }
}
