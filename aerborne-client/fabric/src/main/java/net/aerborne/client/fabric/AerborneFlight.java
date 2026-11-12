package net.aerborne.client.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The flight timer: while a player rides between roosts, a banner under the boss bars names the destination, shows how
 * far along the flight is and counts down the time left. Fed by the server's {@code aerborne:flight}
 * (ReachFlights, once a second); between updates the bar and timer run on so they move smoothly.
 */
final class AerborneFlight {
    private AerborneFlight() { }

    record Payload(boolean active, String destination, float travelled, float total, float secondsLeft) implements CustomPacketPayload {
        static final Type<Payload> TYPE = new Type<>(Identifier.parse("aerborne:flight"));
        static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = new StreamCodec<>() {
            @Override public Payload decode(RegistryFriendlyByteBuf b) {
                int version = b.readInt();
                if (version != 1) throw new IllegalArgumentException("Unsupported flight payload " + version);
                if (!b.readBoolean()) return new Payload(false, "", 0, 0, 0);
                return new Payload(true, b.readUtf(256), b.readFloat(), b.readFloat(), b.readFloat());
            }
            @Override public void encode(RegistryFriendlyByteBuf b, Payload p) { throw new UnsupportedOperationException("Server to client only"); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static volatile Payload current;
    private static volatile long receivedAt;

    static void receive(Payload payload) { current = payload.active() ? payload : null; receivedAt = System.nanoTime(); }
    static void clear() { current = null; }

    static void draw(GuiGraphicsExtractor g, Minecraft mc) {
        Payload f = current;
        if (f == null || mc.player == null) return;
        float elapsed = (System.nanoTime() - receivedAt) / 1e9F;
        // No update for a while (a lost packet, a server restart): the flight is over as far as we can tell.
        if (elapsed > 4F || !mc.player.isPassenger()) { if (elapsed > 4F) current = null; return; }
        float start = f.total() <= 0 ? 1F : Math.min(1F, f.travelled() / f.total());
        float progress = f.secondsLeft() <= 0 ? 1F : Math.min(1F, start + (1F - start) * elapsed / f.secondsLeft());
        int left = Math.max(0, Math.round(f.secondsLeft() - elapsed));
        String time = left / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", left % 60);
        var font = mc.font;
        var heading = Ui.heading("Flying to " + f.destination());
        int w = Math.max(220, font.width(heading) + 60), x = g.guiWidth() / 2 - w / 2, y = 34;
        AerborneHud.box(g, x, y, w, 36, ClientState.preferences);
        g.text(font, heading, x + 8, y + 6, Ui.GOLD, true);
        g.text(font, time, x + w - 8 - font.width(time), y + 6, Ui.TEXT, true);
        Ui.bar(g, x + 8, y + 19, w - 16, 10, Ui.CAST, progress);
        String percent = Math.round(progress * 100) + "%";
        g.text(font, percent, x + w / 2 - font.width(percent) / 2, y + 20, Ui.TEXT, true);
    }
}
