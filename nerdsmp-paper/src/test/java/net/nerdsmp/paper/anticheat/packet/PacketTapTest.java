package net.nerdsmp.paper.anticheat.packet;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Fake packet classes mirror the server's public accessors so the decoder and pipeline handler run for real. */
public class PacketTapTest {
    public abstract static class Move {
        final double x; final double y; final double z; final float yaw; final float pitch; final boolean pos; final boolean rot;
        Move(double x, double y, double z, float yaw, float pitch, boolean pos, boolean rot) { this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch; this.pos = pos; this.rot = rot; }
        public double getX(double fallback) { return pos ? x : fallback; }
        public double getY(double fallback) { return pos ? y : fallback; }
        public double getZ(double fallback) { return pos ? z : fallback; }
        public float getYRot(float fallback) { return rot ? yaw : fallback; }
        public float getXRot(float fallback) { return rot ? pitch : fallback; }
        public boolean isOnGround() { return true; }
        public boolean hasPosition() { return pos; }
        public boolean hasRotation() { return rot; }
    }
    public static final class PosRot extends Move { public PosRot(double x, double y, double z, float yaw, float pitch) { super(x, y, z, yaw, pitch, true, true); } }
    public record Attack(int entityId) { }
    public static final class Swing { }
    public static final class TickEnd { }
    public enum Action { START_DESTROY_BLOCK, STOP_DESTROY_BLOCK, DROP_ITEM }
    public static final class PlayerAction { private final Action action; public PlayerAction(Action action) { this.action = action; } public Action getAction() { return action; } }

    private static PacketDecoder decoder() throws ReflectiveOperationException {
        return new PacketDecoder(Move.class, Attack.class, Swing.class, TickEnd.class, PlayerAction.class);
    }

    @Test void decodesEveryInspectedPacketKind() throws Throwable {
        PacketDecoder decoder = decoder();
        var move = (ClientPacket.Move) decoder.decode(new PosRot(1.5, 64, -2, 90F, -10F), 7L);
        assertEquals(1.5, move.x());
        assertEquals(-10F, move.pitch());
        assertTrue(move.hasPos() && move.hasRot() && move.onGround());
        assertEquals(7L, move.nanos());
        assertEquals(42, ((ClientPacket.Attack) decoder.decode(new Attack(42), 1L)).entityId());
        assertInstanceOf(ClientPacket.Swing.class, decoder.decode(new Swing(), 1L));
        assertInstanceOf(ClientPacket.TickEnd.class, decoder.decode(new TickEnd(), 1L));
        assertEquals(ClientPacket.DigAction.START, ((ClientPacket.Dig) decoder.decode(new PlayerAction(Action.START_DESTROY_BLOCK), 1L)).action());
        assertEquals(ClientPacket.DigAction.OTHER, ((ClientPacket.Dig) decoder.decode(new PlayerAction(Action.DROP_ITEM), 1L)).action());
        assertNull(decoder.decode("chat message", 1L));
    }

    @Test void handlerForwardsEveryPacketUnchangedEvenWhenTheSinkFails() throws Throwable {
        List<ClientPacket> seen = new ArrayList<>();
        UUID id = UUID.randomUUID();
        var channel = new EmbeddedChannel(PacketTap.testHandler(decoder(), (player, packet) -> {
            assertEquals(id, player);
            seen.add(packet);
            if (packet instanceof ClientPacket.Swing) throw new IllegalStateException("sink failure must not drop packets");
        }, id));
        Object[] inbound = {new PosRot(0, 0, 0, 0, 0), "unrelated", new Swing(), new Attack(3)};
        for (Object message : inbound) channel.writeInbound(message);
        for (Object message : inbound) assertSame(message, channel.readInbound());
        assertEquals(3, seen.size());
        channel.finishAndReleaseAll();
    }
}
