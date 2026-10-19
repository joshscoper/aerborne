package net.nerdsmp.paper.anticheat.packet;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Turns the server's decoded packet objects into {@link ClientPacket} values using the packets' public accessors.
 * All lookups happen once in {@link #resolve}; if any is missing the whole packet layer stays off rather than
 * half-working, and Watchdog falls back to event-based checks.
 */
public final class PacketDecoder {
    private static final String GAME = "net.minecraft.network.protocol.game.";
    private final Class<?> move;
    private final Class<?> attack;
    private final Class<?> swing;
    private final Class<?> tickEnd;
    private final Class<?> action;
    private final MethodHandle getX, getY, getZ, getYaw, getPitch, onGround, hasPos, hasRot, attackEntity, actionType;

    PacketDecoder(Class<?> move, Class<?> attack, Class<?> swing, Class<?> tickEnd, Class<?> action) throws ReflectiveOperationException {
        this.move = move;
        this.attack = attack;
        this.swing = swing;
        this.tickEnd = tickEnd;
        this.action = action;
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        MethodType doubleOf = MethodType.methodType(double.class, double.class);
        MethodType floatOf = MethodType.methodType(float.class, float.class);
        MethodType bool = MethodType.methodType(boolean.class);
        getX = lookup.findVirtual(move, "getX", doubleOf);
        getY = lookup.findVirtual(move, "getY", doubleOf);
        getZ = lookup.findVirtual(move, "getZ", doubleOf);
        getYaw = lookup.findVirtual(move, "getYRot", floatOf);
        getPitch = lookup.findVirtual(move, "getXRot", floatOf);
        onGround = lookup.findVirtual(move, "isOnGround", bool);
        hasPos = lookup.findVirtual(move, "hasPosition", bool);
        hasRot = lookup.findVirtual(move, "hasRotation", bool);
        attackEntity = lookup.findVirtual(attack, "entityId", MethodType.methodType(int.class));
        actionType = lookup.unreflect(action.getMethod("getAction"));
    }

    /** Resolves the running server's packet classes. Throws if this server version does not match. */
    public static PacketDecoder resolve(ClassLoader loader) throws ReflectiveOperationException {
        return new PacketDecoder(Class.forName(GAME + "ServerboundMovePlayerPacket", false, loader),
            Class.forName(GAME + "ServerboundAttackPacket", false, loader), Class.forName(GAME + "ServerboundSwingPacket", false, loader),
            Class.forName(GAME + "ServerboundClientTickEndPacket", false, loader), Class.forName(GAME + "ServerboundPlayerActionPacket", false, loader));
    }

    /** Returns null for packets Watchdog does not inspect. */
    public ClientPacket decode(Object packet, long nanos) throws Throwable {
        if (move.isInstance(packet)) {
            boolean position = (boolean) hasPos.invoke(packet);
            boolean rotation = (boolean) hasRot.invoke(packet);
            return new ClientPacket.Move((double) getX.invoke(packet, 0.0D), (double) getY.invoke(packet, 0.0D),
                (double) getZ.invoke(packet, 0.0D), (float) getYaw.invoke(packet, 0.0F), (float) getPitch.invoke(packet, 0.0F),
                (boolean) onGround.invoke(packet), position, rotation, nanos);
        }
        if (tickEnd.isInstance(packet)) return new ClientPacket.TickEnd(nanos);
        if (swing.isInstance(packet)) return new ClientPacket.Swing(nanos);
        if (attack.isInstance(packet)) return new ClientPacket.Attack((int) attackEntity.invoke(packet), nanos);
        if (action.isInstance(packet)) {
            String name = String.valueOf(actionType.invoke(packet));
            ClientPacket.DigAction dig = switch (name) {
                case "START_DESTROY_BLOCK" -> ClientPacket.DigAction.START;
                case "ABORT_DESTROY_BLOCK" -> ClientPacket.DigAction.ABORT;
                case "STOP_DESTROY_BLOCK" -> ClientPacket.DigAction.STOP;
                default -> ClientPacket.DigAction.OTHER;
            };
            return new ClientPacket.Dig(dig, nanos);
        }
        return null;
    }
}
