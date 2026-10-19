package net.nerdsmp.paper.skyblock.machine;

import org.bukkit.Material;

import java.util.Locale;

/** Island processing stations use vanilla blocks and real container storage. */
public enum MachineKind {
    CRUSHER("Crusher", Material.STONE_PICKAXE, 0, 4),
    COMPOSTER("Compost Bin", Material.BONE_MEAL, 1, 4),
    SAWMILL("Sawmill", Material.IRON_AXE, 2, 4),
    SIFTER("Mineral Sifter", Material.IRON_NUGGET, 3, 6);

    private final String title;
    private final Material icon;
    private final int stage, seconds;

    MachineKind(String title, Material icon, int stage, int seconds) {
        this.title = title; this.icon = icon; this.stage = stage; this.seconds = seconds;
    }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String title() { return title; }
    public Material icon() { return icon; }
    public int stage() { return stage; }
    public int seconds() { return seconds; }
    public static MachineKind parse(String value) {
        if (value == null || !value.matches("[a-z_]{1,32}")) throw new IllegalArgumentException("Invalid machine type");
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
