package net.nerdsmp.paper.item;

import org.bukkit.plugin.java.JavaPlugin;

/** Native textures are the default; pack models are an explicit server opt-in. */
public final class VanillaTextures {
    private VanillaTextures() { }
    public static boolean customModels(JavaPlugin plugin) {
        String override=System.getProperty("nerdsmp.vanillaTextures");
        return override==null ? plugin.getConfig().getBoolean("resource-pack.custom-models",false) : !Boolean.parseBoolean(override);
    }
}
