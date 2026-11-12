package net.aerborne.client.fabric;

import java.util.ArrayList;
import java.util.List;
import net.aerborne.client.protocol.ClientPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * The quest tracker: the tracked quest's title, each objective with its progress, how many other quests are under way,
 * and where to go next. The target gets an amber marker in the world, on the compass and on the minimap, apart from
 * personal waypoints in gold. Fed by the server's {@code aerborne:tracker} (QuestGuide.sendTracker).
 */
final class AerborneTracker {
    private AerborneTracker() { }

    static final int AMBER = 0xFFFFB347;

    record Objective(String description, int have, int count) { }
    record Target(String label, int x, int y, int z, boolean sameWorld, boolean arrived) { }

    record Payload(String id, String title, String chain, List<Objective> objectives, int others, Target target)
            implements CustomPacketPayload {
        static final Type<Payload> TYPE = new Type<>(Identifier.parse("aerborne:tracker"));
        static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = new StreamCodec<>() {
            @Override public Payload decode(RegistryFriendlyByteBuf b) {
                int version = b.readInt();
                if (version != 1) throw new IllegalArgumentException("Unsupported tracker payload " + version);
                String id = b.readUtf(64);
                if (id.isEmpty()) return new Payload("", "", "", List.of(), 0, null);
                String title = b.readUtf(), chain = b.readUtf();
                int n = b.readInt();
                if (n < 0 || n > 6) throw new IllegalArgumentException("Too many objectives");
                List<Objective> objectives = new ArrayList<>(n);
                for (int i = 0; i < n; i++) objectives.add(new Objective(b.readUtf(), b.readInt(), b.readInt()));
                int others = b.readInt();
                Target target = b.readBoolean() ? new Target(b.readUtf(), b.readInt(), b.readInt(), b.readInt(), b.readBoolean(), b.readBoolean()) : null;
                return new Payload(id, title, chain, List.copyOf(objectives), others, target);
            }
            @Override public void encode(RegistryFriendlyByteBuf b, Payload p) { throw new UnsupportedOperationException("Server to client only"); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static Payload current;

    /** The target to mark on the compass and map, or null. */
    static Target target() {
        Payload p = current;
        return p == null || p.id().isEmpty() || p.target() == null || !p.target().sameWorld() ? null : p.target();
    }

    /** The tracker panel under the HUD; returns the next y. */
    static int panel(GuiGraphicsExtractor g, Font font, int x, int y, ClientPreferences p) {
        Payload t = current;
        if (t == null || t.id().isEmpty()) return y;
        Minecraft mc = Minecraft.getInstance();
        // The panel grows with the quest's title (to 260), and the title and objectives wrap beyond that.
        var heading = Ui.heading(t.title());
        int width = AerborneHud.panelWidth(font.width(heading) + 30), right = x + width - 8;
        var titleLines = font.split(heading, width - 30);
        List<List<net.minecraft.util.FormattedCharSequence>> lines = new ArrayList<>();
        int height = 10 + titleLines.size() * 11;
        for (Objective objective : t.objectives()) {
            // Leave room at the right for the "3/10" count.
            int reserve = objective.count() > 1 ? font.width(objective.count() + "/" + objective.count()) + 6 : 0;
            var wrapped = font.split(net.minecraft.network.chat.Component.literal(objective.description()), width - 28 - reserve);
            lines.add(wrapped);
            height += wrapped.size() * 10 + (objective.count() > 1 ? 5 : 2);
        }
        Target target = t.target();
        if (target != null) height += 12;
        if (t.others() > 0) height += 10;
        AerborneHud.box(g, x, y, width, height + 4, p);
        Ui.icon(g, Ui.quest("tracked"), x + 6, y + 5, 12);
        int titleRow = y + 5;
        for (var line : titleLines) { g.text(font, line, x + 22, titleRow, Ui.GOLD, true); titleRow += 11; }
        int row = titleRow + 5;
        for (int i = 0; i < t.objectives().size(); i++) {
            Objective objective = t.objectives().get(i);
            boolean done = objective.have() >= objective.count();
            int top = row;
            // A small box, ticked when the objective is met.
            g.fill(x + 9, row + 1, x + 15, row + 7, 0xFF0D1A2B);
            g.outline(x + 8, row, 8, 8, done ? 0xFF55E58A : 0xFF8E9AAF);
            if (done) g.fill(x + 10, row + 2, x + 14, row + 6, 0xFF55E58A);
            for (var wrapped : lines.get(i)) { g.text(font, wrapped, x + 20, row, done ? 0xFF8FD9A0 : Ui.TEXT, false); row += 10; }
            if (objective.count() > 1) {
                String count = Math.min(objective.have(), objective.count()) + "/" + objective.count();
                g.text(font, count, right - font.width(count), top, done ? 0xFF8FD9A0 : Ui.MUTED, false);
                g.fill(x + 20, row, right, row + 2, 0xFF0D1A2B);
                g.fill(x + 20, row, x + 20 + Math.round((right - x - 20) * Math.min(1F, objective.have() / (float) objective.count())), row + 2, done ? 0xFF55E58A : AMBER);
                row += 5;
            } else row += 2;
        }
        if (target != null) {
            String where;
            if (target.arrived()) where = "You're there";
            else if (!target.sameWorld()) where = "In another world";
            else {
                double dx = target.x() + 0.5 - mc.player.getX(), dz = target.z() + 0.5 - mc.player.getZ();
                where = Math.round(Math.sqrt(dx * dx + dz * dz)) + "m " + direction(dx, dz);
            }
            String label = "➤ " + target.label();
            int whereWidth = font.width(where);
            g.text(font, font.plainSubstrByWidth(label, width - 16 - whereWidth - 6), x + 8, row + 1, AMBER, false);
            g.text(font, where, right - whereWidth, row + 1, Ui.TEXT, false);
            row += 12;
        }
        if (t.others() > 0) g.text(font, "+" + t.others() + " more in your quest book", x + 8, row + 1, Ui.MUTED, false);
        return y + height + 10;
    }

    private static String direction(double dx, double dz) {
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        double bearing = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
        return names[(int) Math.round(bearing / 45) % 8];
    }

    /** The amber marker over the quest target in the world, with its label and distance. */
    static void worldMarker(GuiGraphicsExtractor g, Minecraft mc, ClientPreferences p) {
        Target target = target();
        if (target == null || target.arrived() || !p.waypointMarker || mc.player == null) return;
        Vec3 point = new Vec3(target.x() + 0.5, target.y() + 2.2, target.z() + 0.5);
        double distance = point.distanceTo(mc.player.position());
        if (distance < 5) return;
        float[] at = AerborneExtras.project(mc, g, point);
        if (at == null) return;
        AerborneExtras.quietMarker(g, mc, at, AMBER, target.label(), distance);
    }
}
