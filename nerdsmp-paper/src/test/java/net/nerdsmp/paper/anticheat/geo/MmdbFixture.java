package net.nerdsmp.paper.anticheat.geo;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes small MaxMind DB files for tests, following the published format spec. */
final class MmdbFixture {
    record Pointer(int target) {}
    record Uint64(long value) {}

    private final int ipVersion;
    private final List<int[]> nodes = new ArrayList<>(); // -1 empty, >= 0 child node, <= -2 data entry
    private final List<Object> records = new ArrayList<>();
    private final ByteArrayOutputStream raw = new ByteArrayOutputStream();
    private Map<String, Object> metadataOverrides = Map.of();

    MmdbFixture(int ipVersion) {
        this.ipVersion = ipVersion;
        nodes.add(new int[] {-1, -1});
    }

    /** Writes a value at the front of the data section and returns its offset, for pointer tests. */
    int raw(Object value) {
        int offset = raw.size();
        encode(raw, value);
        return offset;
    }

    MmdbFixture metadata(Map<String, Object> overrides) {
        metadataOverrides = overrides;
        return this;
    }

    MmdbFixture insert(String network, int prefix, Object record) throws Exception {
        byte[] address = InetAddress.getByName(network).getAddress();
        if (address.length == 4 && ipVersion == 6) {
            byte[] mapped = new byte[16];
            System.arraycopy(address, 0, mapped, 12, 4);
            address = mapped;
            prefix += 96;
        }
        records.add(record);
        int data = -2 - (records.size() - 1);
        int node = 0;
        for (int bit = 0; bit < prefix; bit++) {
            int side = (address[bit >> 3] >> (7 - (bit & 7))) & 1;
            if (bit == prefix - 1) { nodes.get(node)[side] = data; break; }
            int next = nodes.get(node)[side];
            if (next < 0) {
                nodes.add(new int[] {-1, -1});
                next = nodes.size() - 1;
                nodes.get(node)[side] = next;
            }
            node = next;
        }
        return this;
    }

    byte[] build(int recordSize) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.writeBytes(raw.toByteArray());
        int[] offsets = new int[records.size()];
        for (int i = 0; i < records.size(); i++) {
            offsets[i] = data.size();
            encode(data, records.get(i));
        }
        int count = nodes.size();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int[] node : nodes) {
            long left = value(node[0], count, offsets);
            long right = value(node[1], count, offsets);
            switch (recordSize) {
                case 24 -> { write(out, left, 3); write(out, right, 3); }
                case 28 -> {
                    write(out, left & 0xFFFFFF, 3);
                    out.write((int) (((left >> 24) & 0xF) << 4 | ((right >> 24) & 0xF)));
                    write(out, right & 0xFFFFFF, 3);
                }
                default -> { write(out, left, 4); write(out, right, 4); }
            }
        }
        out.writeBytes(new byte[16]);
        out.writeBytes(data.toByteArray());
        out.writeBytes(new byte[] {(byte) 0xAB, (byte) 0xCD, (byte) 0xEF});
        out.writeBytes("MaxMind.com".getBytes(StandardCharsets.US_ASCII));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("binary_format_major_version", 2);
        metadata.put("binary_format_minor_version", 0);
        metadata.put("build_epoch", new Uint64(1_790_000_000L));
        metadata.put("database_type", "DBIP-City-Lite");
        metadata.put("description", Map.of("en", "Test database"));
        metadata.put("ip_version", ipVersion);
        metadata.put("languages", List.of("en"));
        metadata.put("node_count", count);
        metadata.put("record_size", recordSize);
        metadata.putAll(metadataOverrides);
        encode(out, metadata);
        return out.toByteArray();
    }

    private static long value(int entry, int count, int[] offsets) {
        if (entry == -1) return count;
        if (entry >= 0) return entry;
        return count + 16L + offsets[-2 - entry];
    }

    private static void write(ByteArrayOutputStream out, long value, int bytes) {
        for (int i = bytes - 1; i >= 0; i--) out.write((int) (value >> (8 * i)) & 0xFF);
    }

    static void encode(ByteArrayOutputStream out, Object value) {
        switch (value) {
            case String text -> { byte[] bytes = text.getBytes(StandardCharsets.UTF_8); control(out, 2, bytes.length); out.writeBytes(bytes); }
            case Pointer(int target) -> {
                if (target < 2048) { out.write(0x20 | ((target >> 8) & 0x7)); out.write(target & 0xFF); }
                else { int v = target - 2048; out.write(0x28 | ((v >> 16) & 0x7)); write(out, v & 0xFFFF, 2); }
            }
            case Double number -> { control(out, 3, 8); write(out, Double.doubleToLongBits(number), 8); }
            case Integer number -> { byte[] bytes = minimal(number); control(out, 6, bytes.length); out.writeBytes(bytes); }
            case Uint64(long number) -> { byte[] bytes = minimal(number); control(out, 9, bytes.length); out.writeBytes(bytes); }
            case Boolean flag -> control(out, 14, flag ? 1 : 0);
            case Map<?, ?> map -> {
                control(out, 7, map.size());
                map.forEach((key, item) -> { encode(out, key); encode(out, item); });
            }
            case List<?> list -> { control(out, 11, list.size()); list.forEach(item -> encode(out, item)); }
            default -> throw new IllegalArgumentException("Cannot encode " + value);
        }
    }

    private static byte[] minimal(long number) {
        int length = 0;
        for (long rest = number; rest != 0; rest >>>= 8) length++;
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) bytes[length - 1 - i] = (byte) (number >>> (8 * i));
        return bytes;
    }

    private static void control(ByteArrayOutputStream out, int type, int size) {
        int sizeBits;
        byte[] extra;
        if (size < 29) { sizeBits = size; extra = new byte[0]; }
        else if (size < 285) { sizeBits = 29; extra = new byte[] {(byte) (size - 29)}; }
        else if (size < 65_821) { sizeBits = 30; int v = size - 285; extra = new byte[] {(byte) (v >> 8), (byte) v}; }
        else { sizeBits = 31; int v = size - 65_821; extra = new byte[] {(byte) (v >> 16), (byte) (v >> 8), (byte) v}; }
        if (type <= 7) out.write((type << 5) | sizeBits);
        else { out.write(sizeBits); out.write(type - 7); }
        out.writeBytes(extra);
    }
}
