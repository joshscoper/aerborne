package net.aerborne.client.protocol;

import java.nio.ByteBuffer;

/** Fixed-size, big-endian wire format shared by Fabric and Paper. */
public record Capabilities(int version, int features) {
    public static final String CHANNEL = "aerborne:capabilities";
    public static final int VERSION = 1;
    public static final int MENU = 1;
    public static final int WIRE_BYTES = 8;

    public boolean supportsMenu() {
        return version == VERSION && (features & MENU) != 0;
    }

    public byte[] encode() {
        return ByteBuffer.allocate(WIRE_BYTES).putInt(version).putInt(features).array();
    }

    public static Capabilities decode(byte[] bytes) {
        if (bytes.length != WIRE_BYTES) throw new IllegalArgumentException("Expected 8 capability bytes");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new Capabilities(buffer.getInt(), buffer.getInt());
    }
}
