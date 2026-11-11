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
        List<String> lines = new java.util.ArrayList<>(server.overlay());
        lines.addAll(server.appendix());
        if (lines.isEmpty() && server.overlayTitle().isEmpty()) return y;
        int height = 10 + (server.overlayTitle().isEmpty() ? 0 : 12) + lines.size() * 10;
        AerborneHud.box(g, x, y, 184, height, p);
        int line = y + 6;
        if (!server.overlayTitle().isEmpty()) { g.text(font, font.plainSubstrByWidth(server.overlayTitle(), 168), x + 8, line, p.theme.accent); line += 12; }
        for (String text : lines) { g.text(font, font.plainSubstrByWidth(text, 168), x + 8, line, TEXT); line += 10; }
        return y + height + 6;
    }

    static void screenWide(GuiGraphicsExtractor g, Minecraft mc, HudPayload server, ClientPreferences p, ClientPreferences.Waypoint tracked) {
        Player player = mc.player;
        int width = g.guiWidth(), height = g.guiHeight();
        AerborneExtras.vignette(g, mc, p);
        AerborneExtras.damageNumbers(g, mc);
        AerborneExtras.waypointMarker(g, mc, p, tracked);
        AerborneSocial.toasts(g, mc);
        int x = p.topRight ? 12 : width - 12 - MAP_PIXELS, y = 12;
        if (p.minimap) { minimap(g, mc, x, y, tracked); y += MAP_PIXELS + 10; }
        if (p.compass) { compass(g, mc.font, player, tracked, x + MAP_PIXELS / 2, y, MAP_PIXELS / 2 + 4); y += 22; }
        AerborneExtras.effects(g, mc, p, y);
        int bottom = height - 72;
        if (server != null && !server.notice().isEmpty()) {
            int w = mc.font.width(server.notice());
            int nx = width / 2 - w / 2;
            g.fill(nx - 6, bottom - 4, nx + w + 6, bottom + 12, 0xC017243B);
            g.text(mc.font, server.notice(), nx, bottom, TEXT, true);
            bottom -= 20;
        }
        if (server != null && !server.skill().isEmpty()) skillToast(g, mc.font, server, width / 2, bottom - 8, p);
    }

    /** Skill experience: the gain over a thin bar showing how far through the level the skill is. */
    private static void skillToast(GuiGraphicsExtractor g, Font font, HudPayload server, int centre, int top, ClientPreferences p) {
        int w = Math.max(140, font.width(server.skill()) + 16);
        g.fill(centre - w / 2, top, centre + w / 2, top + 20, 0xC017243B);
        g.text(font, server.skill(), centre - font.width(server.skill()) / 2, top + 3, TEXT, true);
        g.fill(centre - w / 2 + 4, top + 14, centre + w / 2 - 4, top + 17, 0xFF283752);
        g.fill(centre - w / 2 + 4, top + 14, centre - w / 2 + 4 + Math.round((w - 8) * server.skillProgress()), top + 17, p.theme.accent);
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
        g.fill(centre, top, centre + 1, top + 2, GOLD);
    }

    private static double wrap(double degrees) {
        return ((degrees + 180) % 360 + 360) % 360 - 180;
    }

    // ------------------------------------------------------------------ minimap

    /** Blocks across the map; each is drawn two pixels wide. North is up. */
    private static final int MAP_CELLS = 48, MAP_PIXELS = MAP_CELLS * 2;
    private static final int VOID = 0xFF1B2A44, UNLOADED = 0xFF0D1A2B;
    private static final int[] colours = new int[MAP_CELLS * MAP_CELLS];
    private static long mapAt;
    private static int mapX = Integer.MIN_VALUE, mapZ;

    private static void minimap(GuiGraphicsExtractor g, Minecraft mc, int x, int y, ClientPreferences.Waypoint tracked) {
        Player player = mc.player;
        int px = player.getBlockX(), pz = player.getBlockZ();
        long now = System.nanoTime();
        // The ground is read again twice a second, or at once after a step.
        if (now - mapAt > 500_000_000L || px != mapX || pz != mapZ) {
            sample(mc, px, pz);
            mapAt = now; mapX = px; mapZ = pz;
        }
        g.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, x - 6, y - 6, MAP_PIXELS + 12, MAP_PIXELS + 12);
        g.fill(x, y, x + MAP_PIXELS, y + MAP_PIXELS, UNLOADED);
        for (int row = 0; row < MAP_CELLS; row++) {
            // Runs of one colour are drawn as one strip.
            int start = 0;
            for (int col = 1; col <= MAP_CELLS; col++) {
                if (col < MAP_CELLS && colours[row * MAP_CELLS + col] == colours[row * MAP_CELLS + start]) continue;
                g.fill(x + start * 2, y + row * 2, x + col * 2, y + row * 2 + 2, colours[row * MAP_CELLS + start]);
                start = col;
            }
        }
        if (tracked != null) {
            int wx = x + MAP_PIXELS / 2 + (tracked.x() - px) * 2, wz = y + MAP_PIXELS / 2 + (tracked.z() - pz) * 2;
            wx = Math.max(x + 2, Math.min(x + MAP_PIXELS - 3, wx));
            wz = Math.max(y + 2, Math.min(y + MAP_PIXELS - 3, wz));
            g.fill(wx - 2, wz - 2, wx + 3, wz + 3, 0xFF0D1A2B);
            g.fill(wx - 1, wz - 1, wx + 2, wz + 2, GOLD);
        }
        arrow(g, x + MAP_PIXELS / 2 + 1, y + MAP_PIXELS / 2 + 1, player.getYRot());
        g.text(mc.font, "N", x + MAP_PIXELS / 2 - 2, y + 2, GOLD, true);
    }

    /** The player as an arrow pointing the way they face, outlined so it shows on any ground. */
    private static void arrow(GuiGraphicsExtractor g, int cx, int cy, float yaw) {
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        // Yaw 0 faces south (down the map); the arrow is drawn pointing up.
        g.pose().rotate((float) Math.toRadians(yaw + 180));
        int[][] rows = {{0, 0}, {-1, 1}, {-1, 1}, {-2, 2}, {-2, 2}, {-3, 3}, {-3, -1}, {1, 3}};
        for (int i = 0; i < rows.length; i++) g.fill(rows[i][0] - 1, i - 5, rows[i][1] + 2, i - 2, 0xFF0D1A2B);
        for (int i = 0; i < rows.length; i++) g.fill(rows[i][0], i - 4, rows[i][1] + 1, i - 3, TEXT);
        g.pose().popMatrix();
    }

    private static void sample(Minecraft mc, int px, int pz) {
        Level level = mc.level;
        if (level == null) return;
        int half = MAP_CELLS / 2;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[] north = new int[MAP_CELLS];
        // The row just north of the map, for the first row's shading.
        for (int col = 0; col < MAP_CELLS; col++) north[col] = surface(level, px - half + col, pz - half - 1);
        for (int row = 0; row < MAP_CELLS; row++) {
            for (int col = 0; col < MAP_CELLS; col++) {
                int x = px - half + col, z = pz - half + row;
                int index = row * MAP_CELLS + col;
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
