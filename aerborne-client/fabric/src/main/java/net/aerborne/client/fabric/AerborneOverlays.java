package net.aerborne.client.fabric;

import java.util.List;
import net.aerborne.client.protocol.ClientPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * The HUD's other pieces: the quest tracker under the stats panel; the minimap with the compass strip under it, in
 * the top corner the stats leave free (clear of boss bars in the middle); and, above the hotbar, passing notices and
 * skill experience, which the server would otherwise put on the action bar and a boss bar.
 */
final class AerborneOverlays {
    private AerborneOverlays() { }

    private static final int TEXT = 0xFFF4F9FF, MUTED = 0xFF8E9AAF, GOLD = 0xFFF4C76B;
    private static final Identifier FRAME = Identifier.fromNamespaceAndPath("aerborne_client", "menu/panel");

    /** The server's quest or expedition overlay and any appended lines, as a tracker panel; returns the next y. */
    static int panels(GuiGraphicsExtractor g, Font font, int x, int y, HudPayload server, ClientPreferences p) {
        if (server == null) return y;
        List<String> lines = new java.util.ArrayList<>(server.overlay());
        lines.addAll(server.appendix());
        if (lines.isEmpty() && server.overlayTitle().isEmpty()) return y;
        // Grow to fit the title, then wrap every line to the panel instead of cutting it off.
        int width = AerborneHud.panelWidth(font.width(server.overlayTitle()) + 16);
        var title = font.split(net.minecraft.network.chat.Component.literal(server.overlayTitle()), width - 16);
        List<net.minecraft.util.FormattedCharSequence> body = new java.util.ArrayList<>();
        for (String text : lines) body.addAll(font.split(net.minecraft.network.chat.Component.literal(text), width - 16));
        int height = 10 + (server.overlayTitle().isEmpty() ? 0 : title.size() * 10 + 2) + body.size() * 10;
        AerborneHud.box(g, x, y, width, height, p);
        int line = y + 6;
        if (!server.overlayTitle().isEmpty()) { for (var t : title) { g.text(font, t, x + 8, line, p.theme.accent, false); line += 10; } line += 2; }
        for (var text : body) { g.text(font, text, x + 8, line, TEXT, false); line += 10; }
        return y + height + 6;
    }

    /** Effects drawn over the whole screen rather than in a placed element. */
    static void screenWide(GuiGraphicsExtractor g, Minecraft mc, ClientPreferences p, ClientPreferences.Waypoint tracked) {
        AerborneExtras.vignette(g, mc, p);
        AerborneExtras.damageNumbers(g, mc);
        AerborneExtras.waypointMarker(g, mc, p, tracked);
        AerborneTracker.worldMarker(g, mc, p);
        AerborneFlight.draw(g, mc);
    }

    static final int ALERT_WIDTH = 240, MAP_SIZE = 84, COMPASS_WIDTH = 86, COMPASS_HEIGHT = 16;

    /** The minimap at (0, 0), framed, {@value #MAP_SIZE} square. */
    static boolean minimapElement(GuiGraphicsExtractor g, Minecraft mc, ClientPreferences.Waypoint tracked) {
        if (mc.player == null) return false;
        minimap(g, mc, 4, 4, tracked);
        HudElements.track(0, 0, MAP_SIZE, MAP_SIZE);
        return true;
    }

    static boolean compassElement(GuiGraphicsExtractor g, Minecraft mc, ClientPreferences.Waypoint tracked) {
        if (mc.player == null) return false;
        compass(g, mc.font, mc.player, tracked, COMPASS_WIDTH / 2, 1, COMPASS_WIDTH / 2 - 1);
        HudElements.track(0, 0, COMPASS_WIDTH, COMPASS_HEIGHT);
        return true;
    }

    /**
     * Passing notices and skill experience, which the server would otherwise put on the action bar and a boss bar,
     * centred in {@value #ALERT_WIDTH}: the skill gain above, the notice below. A sample of both in edit mode.
     */
    static boolean alerts(GuiGraphicsExtractor g, Minecraft mc, HudPayload server, ClientPreferences p, boolean preview) {
        String notice = server != null ? server.notice() : "", skill = server != null ? server.skill() : "";
        float progress = server != null ? server.skillProgress() : 0;
        if (notice.isEmpty() && skill.isEmpty()) {
            if (!preview) return false;
            notice = "Entering the Wardens' Bridge"; skill = "+42 Mining XP"; progress = 0.6F;
        }
        HudElements.track(0, 0, ALERT_WIDTH, 42);
        int centre = ALERT_WIDTH / 2;
        if (!skill.isEmpty()) skillToast(g, mc.font, skill, progress, centre, 0, p);
        if (!notice.isEmpty()) {
            int w = mc.font.width(notice), nx = centre - w / 2;
            g.fill(nx - 6, 26, nx + w + 6, 42, 0xC017243B);
            g.text(mc.font, notice, nx, 30, TEXT, true);
        }
        return true;
    }

    /** Skill experience: the gain over a thin bar showing how far through the level the skill is. */
    private static void skillToast(GuiGraphicsExtractor g, Font font, String skill, float progress, int centre, int top, ClientPreferences p) {
        int w = Math.max(140, font.width(skill) + 16);
        g.fill(centre - w / 2, top, centre + w / 2, top + 20, 0xC017243B);
        g.text(font, skill, centre - font.width(skill) / 2, top + 3, TEXT, true);
        g.fill(centre - w / 2 + 4, top + 14, centre + w / 2 - 4, top + 17, 0xFF283752);
        g.fill(centre - w / 2 + 4, top + 14, centre - w / 2 + 4 + Math.round((w - 8) * progress), top + 17, p.theme.accent);
    }

    // ------------------------------------------------------------------ compass

    /** A strip of bearings centred on the way the player faces, with the tracked waypoint as a gold mark. */
    private static void compass(GuiGraphicsExtractor g, Font font, Player player, ClientPreferences.Waypoint tracked, int centre, int top, int half) {
        g.fill(centre - half - 1, top - 1, centre + half + 1, top + 15, 0xFF354760);
        g.fill(centre - half, top, centre + half, top + 14, 0xF00D1A2B);
        double heading = ((player.getYRot() + 180) % 360 + 360) % 360;
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        // Ninety degrees each way fits across the strip.
        for (int bearing = 0; bearing < 360; bearing += 15) {
            double offset = wrap(bearing - heading);
            if (Math.abs(offset) > 90) continue;
            int x = centre + (int) Math.round(offset * (half - 6) / 90);
            if (bearing % 45 == 0) {
                String name = names[bearing / 45];
                g.text(font, name, x - font.width(name) / 2 + 1, top + 3, name.equals("N") ? GOLD : name.length() == 1 ? TEXT : MUTED, false);
            } else g.fill(x, top + 5, x + 1, top + 9, 0xFF4A5A75);
        }
        if (tracked != null) {
            // Bearing measured like the heading: 0 north, 90 east.
            double bearing = Math.toDegrees(Math.atan2(tracked.x() + 0.5 - player.getX(), -(tracked.z() + 0.5 - player.getZ())));
            double offset = wrap(bearing - heading);
            int x = centre + (int) Math.round(Math.max(-90, Math.min(90, offset)) * (half - 6) / 90);
            g.fill(x - 1, top + 11, x + 2, top + 14, GOLD);
        }
        var quest = AerborneTracker.target();
        if (quest != null) {
            double bearing = Math.toDegrees(Math.atan2(quest.x() + 0.5 - player.getX(), -(quest.z() + 0.5 - player.getZ())));
            int x = centre + (int) Math.round(Math.max(-90, Math.min(90, wrap(bearing - heading))) * (half - 6) / 90);
            g.fill(x - 2, top + 10, x + 3, top + 14, 0xFF0D1A2B);
            g.fill(x - 1, top + 11, x + 2, top + 14, AerborneTracker.AMBER);
        }
        g.fill(centre, top, centre + 1, top + 2, GOLD);
    }

    private static double wrap(double degrees) {
        return ((degrees + 180) % 360 + 360) % 360 - 180;
    }

    // ------------------------------------------------------------------ minimap

    /**
     * The map shows {@value #MAP_BLOCKS} blocks across in a {@value #MAP_PIXELS} pixel square, north up. The ground is
     * painted into a texture, one texel per block, so it stays sharp at any GUI scale and costs one draw.
     */
    private static final int MAP_PIXELS = 76, MAP_BLOCKS = 96;
    private static final int VOID = 0xFF1B2A44, UNLOADED = 0xFF0D1A2B;
    private static final int[] colours = new int[MAP_BLOCKS * MAP_BLOCKS];
    private static final Identifier MAP_TEXTURE = Identifier.fromNamespaceAndPath("aerborne_client", "minimap");
    private static net.minecraft.client.renderer.texture.DynamicTexture mapTexture;
    private static long mapAt;
    private static int mapX = Integer.MIN_VALUE, mapZ;

    /** Where a block lies on the map, kept just inside its edge. */
    private static int[] onMap(int x, int y, int px, int pz, int bx, int bz) {
        float scale = MAP_PIXELS / (float) MAP_BLOCKS;
        int mx = Math.round(x + MAP_PIXELS / 2F + (bx - px) * scale), mz = Math.round(y + MAP_PIXELS / 2F + (bz - pz) * scale);
        return new int[] {Math.max(x + 2, Math.min(x + MAP_PIXELS - 3, mx)), Math.max(y + 2, Math.min(y + MAP_PIXELS - 3, mz))};
    }

    private static void minimap(GuiGraphicsExtractor g, Minecraft mc, int x, int y, ClientPreferences.Waypoint tracked) {
        Player player = mc.player;
        int px = player.getBlockX(), pz = player.getBlockZ();
        long now = System.nanoTime();
        if (mapTexture == null) {
            mapTexture = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "aerborne minimap", MAP_BLOCKS, MAP_BLOCKS, false);
            mc.getTextureManager().register(MAP_TEXTURE, mapTexture);
        }
        // The ground is read again twice a second, or at once after a step.
        if (now - mapAt > 500_000_000L || px != mapX || pz != mapZ) {
            sample(mc, px, pz);
            var pixels = mapTexture.getPixels();
            if (pixels != null) {
                for (int row = 0; row < MAP_BLOCKS; row++)
                    for (int col = 0; col < MAP_BLOCKS; col++) pixels.setPixel(col, row, colours[row * MAP_BLOCKS + col]);
                mapTexture.upload();
            }
            mapAt = now; mapX = px; mapZ = pz;
        }
        Ui.inset(g, x - 4, y - 4, MAP_PIXELS + 8, MAP_PIXELS + 8);
        g.blit(RenderPipelines.GUI_TEXTURED, MAP_TEXTURE, x, y, 0F, 0F, MAP_PIXELS, MAP_PIXELS, MAP_BLOCKS, MAP_BLOCKS, MAP_BLOCKS, MAP_BLOCKS);
        if (tracked != null) {
            int[] at = onMap(x, y, px, pz, tracked.x(), tracked.z());
            g.fill(at[0] - 2, at[1] - 2, at[0] + 3, at[1] + 3, 0xFF0D1A2B);
            g.fill(at[0] - 1, at[1] - 1, at[0] + 2, at[1] + 2, GOLD);
        }
        var quest = AerborneTracker.target();
        if (quest != null) {
            int[] at = onMap(x, y, px, pz, quest.x(), quest.z());
            g.fill(at[0] - 2, at[1] - 2, at[0] + 3, at[1] + 3, 0xFF0D1A2B);
            g.fill(at[0] - 1, at[1] - 1, at[0] + 2, at[1] + 2, AerborneTracker.AMBER);
        }
        arrow(g, x + MAP_PIXELS / 2, y + MAP_PIXELS / 2, player.getYRot());
        g.text(mc.font, "N", x + MAP_PIXELS / 2 - 2, y + 2, GOLD, true);
    }

    /** The player as an arrow pointing the way they face, outlined so it shows on any ground. */
    private static void arrow(GuiGraphicsExtractor g, int cx, int cy, float yaw) {
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        // Yaw 0 faces south (down the map); the arrow is drawn pointing up.
        g.pose().rotate((float) Math.toRadians(yaw + 180));
        g.blitSprite(RenderPipelines.GUI_TEXTURED, Ui.CURSOR, -6, -7, 12, 12);
        g.pose().popMatrix();
    }

    private static void sample(Minecraft mc, int px, int pz) {
        Level level = mc.level;
        if (level == null) return;
        int half = MAP_BLOCKS / 2;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[] north = new int[MAP_BLOCKS];
        // The row just north of the map, for the first row's shading.
        for (int col = 0; col < MAP_BLOCKS; col++) north[col] = surface(level, px - half + col, pz - half - 1);
        for (int row = 0; row < MAP_BLOCKS; row++) {
            for (int col = 0; col < MAP_BLOCKS; col++) {
                int x = px - half + col, z = pz - half + row;
                int index = row * MAP_BLOCKS + col;
                pos.set(x, 0, z);
                if (!level.hasChunkAt(pos)) { colours[index] = UNLOADED; north[col] = Integer.MIN_VALUE; continue; }
                int top = surface(level, x, z);
                int above = north[col];
                north[col] = top;
                if (top < level.getMinY()) { colours[index] = VOID; continue; }
                pos.setY(top);
                MapColor colour = level.getBlockState(pos).getMapColor(level, pos);
                if (colour == MapColor.NONE) { colours[index] = VOID; continue; }
                MapColor.Brightness shade;
                if (colour == MapColor.WATER) {
                    // Water darkens with depth, as on a paper map.
                    int depth = 0;
                    while (depth < 8 && level.getBlockState(pos.move(0, -1, 0)).getFluidState().isSource()) depth++;
                    shade = depth < 2 ? MapColor.Brightness.HIGH : depth < 5 ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOW;
                } else {
                    shade = above == Integer.MIN_VALUE || top == above ? MapColor.Brightness.NORMAL
                            : top > above ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
                }
                colours[index] = 0xFF000000 | colour.calculateARGBColor(shade);
            }
        }
    }

    /** The highest block that stops movement: grass, flowers and torches are seen through; leaves and water are not. */
    private static int surface(Level level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
    }
}
